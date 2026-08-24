import argparse
import csv
import hashlib
import io
import json
import os
import re
import statistics
import zipfile
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import datetime, timezone
from decimal import Decimal, InvalidOperation
from pathlib import Path
from typing import Any, Iterator

from sqlalchemy import bindparam, create_engine, text
from sqlalchemy.engine import Engine

from import_public_enrichment import (
    MatchedRecord,
    RestaurantReference,
    _database_url,
    deduplicate_matches,
    match_records,
    normalize_identity,
    parse_yes_no,
)


BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent
DATA_DIR = BASE_DIR / "data" / "public_enrichment"
MANIFEST_PATH = DATA_DIR / "seoul_tourism_sources.json"
REPORT_DIR = DATA_DIR / "reports"
MIGRATION_PATH = (
    PROJECT_DIR
    / "src"
    / "main"
    / "resources"
    / "db"
    / "migration"
    / "V19__create_public_restaurant_official_evidence.sql"
)

OPERATION_HEADERS = (
    "식당(ID)", "식당명", "지점명", "지역명", "주차가능여부", "와이파이제공여부",
    "놀이방유무", "다국어메뉴판제공여부", "화장실정보내용", "휴무일정보내용",
    "영업시간내용", "배달서비스유무", "온라인예약정보내용", "홈페이지(URL)",
    "인근랜드마크명", "인근랜드마크위도", "인근랜드마크경도", "인근랜드마크와거리",
    "스마트오더유무", "대표메뉴명", "식당상태", "해시태그", "면적정보내용",
)

QUALITY_HEADERS = (
    "식당(ID)", "식당명", "지점명", "지역명", "어워드정보설명", "(RTI)지수",
    "온라인화진행여부", "수용태세지수", "인기도", "트립어드바이저평점", "씨트립평점",
    "네이버평점",
)

MENU_HEADERS = (
    "메뉴(ID)", "메뉴명", "메뉴가격", "지역특산메뉴여부", "지역특산메뉴명",
    "지역특산메뉴출처(URL)", "지역명", "식당(ID)", "식당명", "지점명",
)

SEOUL_DISTRICTS = {
    "강남구", "강동구", "강북구", "강서구", "관악구", "광진구", "구로구", "금천구",
    "노원구", "도봉구", "동대문구", "동작구", "마포구", "서대문구", "서초구", "성동구",
    "성북구", "송파구", "양천구", "영등포구", "용산구", "은평구", "종로구", "중구", "중랑구",
}

OPERATION_TEXT_COLUMNS = {
    "restroom_info": "화장실정보내용",
    "closed_days": "휴무일정보내용",
    "opening_hours": "영업시간내용",
    "reservation_info": "온라인예약정보내용",
    "homepage_url": "홈페이지(URL)",
    "nearby_landmark_name": "인근랜드마크명",
    "representative_menu": "대표메뉴명",
    "hashtags": "해시태그",
    "area_info": "면적정보내용",
}

OPERATION_BOOLEAN_COLUMNS = {
    "parking_available": "주차가능여부",
    "wifi_available": "와이파이제공여부",
    "playroom_available": "놀이방유무",
    "multilingual_menu_available": "다국어메뉴판제공여부",
    "delivery_available": "배달서비스유무",
    "smart_order_available": "스마트오더유무",
}

MIN_PLAUSIBLE_MENU_PRICE = 1_000
MAX_PLAUSIBLE_MENU_PRICE = 10_000_000
MAX_DOCUMENT_MENU_NAMES = 20
MAX_PRICE_EXAMPLES = 10


@dataclass(frozen=True)
class SourceSpec:
    kind: str
    source_code: str
    provider_name: str
    dataset_name: str
    source_page_url: str
    download_url: str
    license_name: str
    published_on: str
    retrieved_at: str
    raw_file: Path
    archive_member: str | None
    encoding: str
    row_count: int
    byte_size: int
    sha256: str


@dataclass
class MenuAggregate:
    menu_count: int = 0
    names: list[str] = field(default_factory=list)
    name_keys: set[str] = field(default_factory=set)
    priced_entries: list[tuple[int, str, str]] = field(default_factory=list)
    invalid_price_count: int = 0
    implausible_price_count: int = 0
    vegan_labeled: bool = False
    vegetarian_labeled: bool = False
    gluten_free_labeled: bool = False

    def add(self, row: dict[str, str]) -> None:
        self.menu_count += 1
        menu_name = clean(row.get("메뉴명")) or ""
        menu_key = normalize_identity(menu_name)
        if menu_name and menu_key not in self.name_keys and len(self.names) < MAX_DOCUMENT_MENU_NAMES:
            self.name_keys.add(menu_key)
            self.names.append(menu_name)

        compact_name = re.sub(r"[\s_-]+", "", menu_name.lower())
        vegan = "비건" in compact_name or "vegan" in compact_name
        vegetarian = vegan or "채식" in compact_name or "vegetarian" in compact_name
        gluten_free = "글루텐프리" in compact_name or "glutenfree" in compact_name
        self.vegan_labeled = self.vegan_labeled or vegan
        self.vegetarian_labeled = self.vegetarian_labeled or vegetarian
        self.gluten_free_labeled = self.gluten_free_labeled or gluten_free

        raw_price = clean(row.get("메뉴가격"))
        try:
            price = int(raw_price) if raw_price is not None else 0
        except ValueError:
            self.invalid_price_count += 1
            return
        if price < MIN_PLAUSIBLE_MENU_PRICE or price > MAX_PLAUSIBLE_MENU_PRICE:
            self.implausible_price_count += 1
            return
        self.priced_entries.append((price, clean(row.get("메뉴(ID)")) or "", menu_name))

    def price_summary(self) -> tuple[int | None, int | None, int | None]:
        if not self.priced_entries:
            return None, None, None
        prices = sorted(value[0] for value in self.priced_entries)
        typical = int(statistics.median_low(prices))
        return prices[0], typical, prices[-1]

    def price_examples(self) -> list[dict[str, Any]]:
        if not self.priced_entries:
            return []
        _, typical, _ = self.price_summary()
        ordered = sorted(
            self.priced_entries,
            key=lambda value: (abs(value[0] - int(typical or 0)), value[0], value[1]),
        )
        return [
            {"menuId": menu_id, "menuName": menu_name, "price": price}
            for price, menu_id, menu_name in ordered[:MAX_PRICE_EXAMPLES]
        ]


def arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Strictly match and import Seoul Tourism restaurant evidence into MySQL."
    )
    parser.add_argument("--apply", action="store_true", help="Apply the verified upsert plan.")
    parser.add_argument(
        "--create-schema",
        action="store_true",
        help="Create missing V19 evidence tables before applying rows.",
    )
    parser.add_argument("--report", type=Path, help="Additional JSON report output path.")
    return parser.parse_args()


def clean(value: Any) -> str | None:
    if value is None:
        return None
    normalized = " ".join(str(value).strip().split())
    return normalized or None


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def load_sources() -> dict[str, SourceSpec]:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1:
        raise RuntimeError("Unsupported Seoul Tourism manifest schema version.")
    specs: dict[str, SourceSpec] = {}
    for kind, value in manifest["sources"].items():
        specs[kind] = SourceSpec(
            kind=kind,
            source_code=value["sourceCode"],
            provider_name=manifest["providerName"],
            dataset_name=value["datasetName"],
            source_page_url=value["sourcePageUrl"],
            download_url=value["downloadUrl"],
            license_name=manifest["licenseName"],
            published_on=value["publishedOn"],
            retrieved_at=manifest["retrievedAt"],
            raw_file=(DATA_DIR / value["rawFile"]).resolve(),
            archive_member=value.get("archiveMember"),
            encoding=value["encoding"],
            row_count=int(value["rowCount"]),
            byte_size=int(value["byteSize"]),
            sha256=value["sha256"].lower(),
        )
    if set(specs) != {"operation", "quality", "menu"}:
        raise RuntimeError("The Seoul Tourism manifest must define operation, quality, and menu sources.")
    return specs


def validate_file(spec: SourceSpec) -> dict[str, Any]:
    if not spec.raw_file.is_file():
        raise RuntimeError(f"Raw source file is missing: {spec.raw_file}")
    actual_size = spec.raw_file.stat().st_size
    actual_sha256 = file_sha256(spec.raw_file)
    if actual_size != spec.byte_size:
        raise RuntimeError(f"{spec.kind} byte size mismatch: {actual_size} != {spec.byte_size}")
    if actual_sha256 != spec.sha256:
        raise RuntimeError(f"{spec.kind} SHA-256 mismatch.")
    return {
        "rawFile": str(spec.raw_file.relative_to(BASE_DIR)),
        "byteSize": actual_size,
        "sha256": actual_sha256,
        "encoding": spec.encoding,
        "archiveMember": spec.archive_member,
    }


def row_reader(spec: SourceSpec) -> Iterator[dict[str, str]]:
    if spec.archive_member is None:
        with spec.raw_file.open("r", encoding=spec.encoding, newline="") as file_handle:
            yield from csv.DictReader(file_handle)
        return
    with zipfile.ZipFile(spec.raw_file) as archive:
        try:
            member = archive.open(spec.archive_member)
        except KeyError as error:
            raise RuntimeError(f"Archive member is missing: {spec.archive_member}") from error
        with member, io.TextIOWrapper(member, encoding=spec.encoding, newline="") as text_handle:
            yield from csv.DictReader(text_handle)


def read_operation_rows(spec: SourceSpec) -> tuple[list[dict[str, str]], dict[str, Any]]:
    rows: list[dict[str, str]] = []
    regions: Counter[str] = Counter()
    ids: set[str] = set()
    with spec.raw_file.open("r", encoding=spec.encoding, newline="") as file_handle:
        reader = csv.DictReader(file_handle)
        if tuple(reader.fieldnames or ()) != OPERATION_HEADERS:
            raise RuntimeError("Official operation CSV headers do not match the expected schema.")
        for row in reader:
            source_id = clean(row["식당(ID)"])
            if not source_id or source_id in ids:
                raise RuntimeError(f"Missing or duplicate operation restaurant ID: {source_id}")
            ids.add(source_id)
            region = clean(row["지역명"]) or ""
            if region not in SEOUL_DISTRICTS:
                raise RuntimeError(f"Unexpected operation region: {region}")
            if (clean(row["식당상태"]) or "").upper() != "NORMAL":
                raise RuntimeError(f"Unexpected operation status for source ID {source_id}.")
            for column in OPERATION_BOOLEAN_COLUMNS.values():
                parse_yes_no(row[column])
            regions[region] += 1
            rows.append(dict(row))
    if len(rows) != spec.row_count:
        raise RuntimeError(f"Operation row count mismatch: {len(rows)} != {spec.row_count}")
    return rows, {"rowCount": len(rows), "regionCounts": dict(sorted(regions.items()))}


def fetch_restaurants(engine: Engine) -> list[RestaurantReference]:
    statement = text("""
        SELECT public_restaurant_id, name, branch_name, sigungu_name
          FROM public_restaurant
         WHERE status = 'ACTIVE'
           AND sigungu_name IN :regions
         ORDER BY public_restaurant_id
    """).bindparams(bindparam("regions", expanding=True))
    with engine.connect() as connection:
        rows = connection.execute(statement, {"regions": sorted(SEOUL_DISTRICTS)}).mappings().all()
    return [
        RestaurantReference(
            public_restaurant_id=int(row["public_restaurant_id"]),
            name=row["name"],
            branch_name=clean(row["branch_name"]),
            region_name=row["sigungu_name"],
        )
        for row in rows
    ]


def strict_matches(
    rows: list[dict[str, str]],
    restaurants: list[RestaurantReference],
) -> tuple[list[MatchedRecord], dict[str, Any]]:
    rows_by_region: dict[str, list[dict[str, str]]] = defaultdict(list)
    restaurants_by_region: dict[str, list[RestaurantReference]] = defaultdict(list)
    for row in rows:
        rows_by_region[row["지역명"]].append(row)
    for restaurant in restaurants:
        restaurants_by_region[restaurant.region_name].append(restaurant)

    all_matches: list[MatchedRecord] = []
    totals: Counter[str] = Counter()
    region_reports: dict[str, Any] = {}
    for region in sorted(SEOUL_DISTRICTS):
        matches, reasons = match_records(rows_by_region[region], restaurants_by_region[region])
        all_matches.extend(matches)
        totals.update(reasons)
        region_reports[region] = {
            "sourceRows": len(rows_by_region[region]),
            "databaseRestaurants": len(restaurants_by_region[region]),
            "matchedRowsBeforeDeduplication": len(matches),
            "reasons": dict(sorted(reasons.items())),
        }
    selected, duplicates_removed = deduplicate_matches(all_matches)
    return selected, {
        "matchedRowsBeforeDeduplication": len(all_matches),
        "matchedRestaurants": len(selected),
        "duplicatesRemoved": duplicates_removed,
        "reasonTotals": dict(sorted(totals.items())),
        "regions": region_reports,
    }


def operation_parameters(match: MatchedRecord, source_code: str) -> dict[str, Any]:
    row = match.record
    parameters: dict[str, Any] = {
        "public_restaurant_id": match.public_restaurant_id,
        "source_code": source_code,
        "source_record_id": clean(row["식당(ID)"]) or "",
        "source_restaurant_name": clean(row["식당명"]) or "",
        "source_branch_name": clean(row["지점명"]),
        "source_region_name": clean(row["지역명"]) or "",
        "source_status": clean(row["식당상태"]),
        "match_method": match.match_method,
        "match_confidence": match.match_confidence,
        "raw_record": json.dumps(row, ensure_ascii=False, separators=(",", ":")),
    }
    parameters.update({key: parse_yes_no(row[column]) for key, column in OPERATION_BOOLEAN_COLUMNS.items()})
    parameters.update({key: clean(row[column]) for key, column in OPERATION_TEXT_COLUMNS.items()})
    return parameters


def decimal_or_none(value: str | None) -> Decimal | None:
    normalized = clean(value)
    if normalized is None:
        return None
    try:
        return Decimal(normalized)
    except InvalidOperation as error:
        raise ValueError(f"Invalid decimal value: {value}") from error


def identity_matches(row: dict[str, str], operation_row: dict[str, str]) -> bool:
    return (
        normalize_identity(row.get("식당명")) == normalize_identity(operation_row.get("식당명"))
        and normalize_identity(row.get("지점명")) == normalize_identity(operation_row.get("지점명"))
    )


def quality_parameters(
    spec: SourceSpec,
    matches_by_source_id: dict[str, MatchedRecord],
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    parameters: list[dict[str, Any]] = []
    row_count = 0
    identity_mismatches = 0
    matched_source_ids: set[str] = set()
    with spec.raw_file.open("r", encoding=spec.encoding, newline="") as file_handle:
        reader = csv.DictReader(file_handle)
        if tuple(reader.fieldnames or ()) != QUALITY_HEADERS:
            raise RuntimeError("Official quality CSV headers do not match the expected schema.")
        for row in reader:
            row_count += 1
            source_id = clean(row["식당(ID)"]) or ""
            match = matches_by_source_id.get(source_id)
            if match is None:
                continue
            if not identity_matches(row, match.record) or clean(row["지역명"]) != clean(match.record["지역명"]):
                identity_mismatches += 1
                continue
            if source_id in matched_source_ids:
                raise RuntimeError(f"Duplicate quality source restaurant ID: {source_id}")
            matched_source_ids.add(source_id)
            parameters.append({
                "public_restaurant_id": match.public_restaurant_id,
                "source_code": spec.source_code,
                "source_record_id": source_id,
                "source_restaurant_name": clean(row["식당명"]) or "",
                "source_branch_name": clean(row["지점명"]),
                "source_region_name": clean(row["지역명"]) or "",
                "match_method": match.match_method,
                "match_confidence": match.match_confidence,
                "award_description": clean(row["어워드정보설명"]),
                "rti_score": decimal_or_none(row["(RTI)지수"]),
                "online_progress": parse_yes_no(row["온라인화진행여부"]),
                "acceptance_score": decimal_or_none(row["수용태세지수"]),
                "popularity_score": decimal_or_none(row["인기도"]),
                "tripadvisor_rating": decimal_or_none(row["트립어드바이저평점"]),
                "ctrip_rating": decimal_or_none(row["씨트립평점"]),
                "naver_rating": decimal_or_none(row["네이버평점"]),
                "raw_record": json.dumps(row, ensure_ascii=False, separators=(",", ":")),
            })
    if row_count != spec.row_count:
        raise RuntimeError(f"Quality row count mismatch: {row_count} != {spec.row_count}")
    return parameters, {
        "rowCount": row_count,
        "matchedRows": len(parameters),
        "identityMismatchesRejected": identity_mismatches,
        "withAward": sum(1 for value in parameters if value["award_description"]),
        "withExternalRating": sum(1 for value in parameters if any(
            value[key] is not None
            for key in ("naver_rating", "tripadvisor_rating", "ctrip_rating")
        )),
    }


def menu_parameters(
    spec: SourceSpec,
    matches_by_source_id: dict[str, MatchedRecord],
) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    aggregates: dict[str, MenuAggregate] = defaultdict(MenuAggregate)
    row_count = 0
    matched_rows = 0
    identity_mismatches = 0
    with zipfile.ZipFile(spec.raw_file) as archive:
        try:
            member = archive.open(spec.archive_member or "")
        except KeyError as error:
            raise RuntimeError(f"Archive member is missing: {spec.archive_member}") from error
        with member, io.TextIOWrapper(member, encoding=spec.encoding, newline="") as text_handle:
            reader = csv.DictReader(text_handle)
            if tuple(reader.fieldnames or ()) != MENU_HEADERS:
                raise RuntimeError("Official menu CSV headers do not match the expected schema.")
            for row in reader:
                row_count += 1
                source_id = clean(row["식당(ID)"]) or ""
                match = matches_by_source_id.get(source_id)
                if match is None:
                    continue
                if not identity_matches(row, match.record):
                    identity_mismatches += 1
                    continue
                matched_rows += 1
                aggregates[source_id].add(row)
    if row_count != spec.row_count:
        raise RuntimeError(f"Menu row count mismatch: {row_count} != {spec.row_count}")

    parameters: list[dict[str, Any]] = []
    for source_id in sorted(
        aggregates,
        key=lambda value: (0, int(value)) if value.isdigit() else (1, value),
    ):
        aggregate = aggregates[source_id]
        match = matches_by_source_id[source_id]
        minimum, typical, maximum = aggregate.price_summary()
        examples = aggregate.price_examples()
        parameters.append({
            "public_restaurant_id": match.public_restaurant_id,
            "source_code": spec.source_code,
            "source_restaurant_id": source_id,
            "source_restaurant_name": clean(match.record["식당명"]) or "",
            "source_branch_name": clean(match.record["지점명"]),
            "match_method": match.match_method,
            "match_confidence": match.match_confidence,
            "menu_count": aggregate.menu_count,
            "priced_menu_count": len(aggregate.priced_entries),
            "minimum_menu_price": minimum,
            "typical_menu_price": typical,
            "maximum_menu_price": maximum,
            "menu_names": ", ".join(aggregate.names) or None,
            "vegan_labeled_menu_available": aggregate.vegan_labeled,
            "vegetarian_labeled_menu_available": aggregate.vegetarian_labeled,
            "gluten_free_labeled_menu_available": aggregate.gluten_free_labeled,
            "price_examples": json.dumps(examples, ensure_ascii=False, separators=(",", ":")),
            "raw_summary": json.dumps({
                "menuRowCount": aggregate.menu_count,
                "pricedMenuCount": len(aggregate.priced_entries),
                "invalidPriceCount": aggregate.invalid_price_count,
                "implausiblePriceCount": aggregate.implausible_price_count,
                "pricePolicy": {
                    "minimumIncluded": MIN_PLAUSIBLE_MENU_PRICE,
                    "maximumIncluded": MAX_PLAUSIBLE_MENU_PRICE,
                    "typicalMethod": "median_low",
                },
            }, ensure_ascii=False, separators=(",", ":")),
        })
    return parameters, {
        "rowCount": row_count,
        "matchedMenuRows": matched_rows,
        "matchedRestaurants": len(parameters),
        "identityMismatchesRejected": identity_mismatches,
        "withPlausiblePrice": sum(1 for value in parameters if value["typical_menu_price"] is not None),
        "veganLabeledRestaurants": sum(1 for value in parameters if value["vegan_labeled_menu_available"]),
        "vegetarianLabeledRestaurants": sum(
            1 for value in parameters if value["vegetarian_labeled_menu_available"]
        ),
        "glutenFreeLabeledRestaurants": sum(
            1 for value in parameters if value["gluten_free_labeled_menu_available"]
        ),
    }


def table_exists(engine: Engine, table_name: str) -> bool:
    with engine.connect() as connection:
        return bool(connection.scalar(text("""
            SELECT COUNT(*)
              FROM information_schema.tables
             WHERE table_schema = DATABASE()
               AND table_name = :table_name
        """), {"table_name": table_name}))


def apply_schema(engine: Engine) -> list[str]:
    sql = MIGRATION_PATH.read_text(encoding="utf-8")
    statements = [statement.strip() for statement in sql.split(";") if statement.strip()]
    table_statements: list[tuple[str, str]] = []
    for statement in statements:
        match = re.match(r"CREATE TABLE\s+([a-z0-9_]+)", statement, re.IGNORECASE)
        if match is None:
            raise RuntimeError("V19 migration may only contain CREATE TABLE statements.")
        table_statements.append((match.group(1), statement))
    if {value[0] for value in table_statements} != {
        "public_restaurant_quality_evidence", "public_restaurant_menu_evidence"
    }:
        raise RuntimeError("V19 migration has an unexpected table set.")
    created: list[str] = []
    with engine.begin() as connection:
        for table_name, statement in table_statements:
            if not table_exists(engine, table_name):
                connection.exec_driver_sql(statement)
                created.append(table_name)
    return created


def existing_rows(
    engine: Engine,
    table_name: str,
    key_column: str,
    source_code: str,
) -> list[dict[str, Any]]:
    if not table_exists(engine, table_name):
        return []
    allowed_tables = {
        "public_restaurant_enrichment", "public_restaurant_quality_evidence",
        "public_restaurant_menu_evidence",
    }
    allowed_keys = {"source_record_id", "source_restaurant_id"}
    if table_name not in allowed_tables or key_column not in allowed_keys:
        raise ValueError("Unexpected evidence table or key column.")
    with engine.connect() as connection:
        rows = connection.execute(text(f"""
            SELECT public_restaurant_id, {key_column} AS source_key
              FROM {table_name}
             WHERE source_code = :source_code
        """), {"source_code": source_code}).mappings().all()
    return [dict(row) for row in rows]


def impact(
    parameters: list[dict[str, Any]],
    existing: list[dict[str, Any]],
    key_name: str,
) -> dict[str, Any]:
    by_key = {str(row["source_key"]): row for row in existing}
    by_restaurant = {int(row["public_restaurant_id"]): row for row in existing}
    inserts = 0
    updates = 0
    conflicts: list[dict[str, Any]] = []
    for value in parameters:
        source_key = str(value[key_name])
        restaurant_id = int(value["public_restaurant_id"])
        prior = by_key.get(source_key) or by_restaurant.get(restaurant_id)
        if prior is None:
            inserts += 1
            continue
        if str(prior["source_key"]) == source_key and int(prior["public_restaurant_id"]) == restaurant_id:
            updates += 1
            continue
        conflicts.append({
            "sourceKey": source_key,
            "publicRestaurantId": restaurant_id,
            "existingSourceKey": str(prior["source_key"]),
            "existingPublicRestaurantId": int(prior["public_restaurant_id"]),
        })
    return {
        "plannedInserts": inserts,
        "plannedUpdates": updates,
        "untouchedExistingRows": max(0, len(existing) - updates),
        "conflicts": conflicts,
    }


def source_parameters(spec: SourceSpec) -> dict[str, Any]:
    return {
        "source_code": spec.source_code,
        "provider_name": spec.provider_name,
        "dataset_name": spec.dataset_name,
        "source_page_url": spec.source_page_url,
        "download_url": spec.download_url,
        "license_name": spec.license_name,
        "source_published_on": spec.published_on,
        "retrieved_at": datetime.fromisoformat(spec.retrieved_at),
        "raw_file_sha256": spec.sha256,
        "raw_row_count": spec.row_count,
    }


SOURCE_UPSERT = text("""
    INSERT INTO public_data_source (
        source_code, provider_name, dataset_name, source_page_url, download_url,
        license_name, source_published_on, retrieved_at, raw_file_sha256, raw_row_count
    ) VALUES (
        :source_code, :provider_name, :dataset_name, :source_page_url, :download_url,
        :license_name, :source_published_on, :retrieved_at, :raw_file_sha256, :raw_row_count
    )
    ON DUPLICATE KEY UPDATE
        provider_name = VALUES(provider_name), dataset_name = VALUES(dataset_name),
        source_page_url = VALUES(source_page_url), download_url = VALUES(download_url),
        license_name = VALUES(license_name), source_published_on = VALUES(source_published_on),
        retrieved_at = VALUES(retrieved_at), raw_file_sha256 = VALUES(raw_file_sha256),
        raw_row_count = VALUES(raw_row_count)
""")

OPERATION_UPSERT = text("""
    INSERT INTO public_restaurant_enrichment (
        public_restaurant_id, source_code, source_record_id, source_restaurant_name,
        source_branch_name, source_region_name, source_status, match_method, match_confidence,
        parking_available, wifi_available, playroom_available, multilingual_menu_available,
        delivery_available, smart_order_available, restroom_info, closed_days, opening_hours,
        reservation_info, homepage_url, nearby_landmark_name, representative_menu, hashtags,
        area_info, raw_record
    ) VALUES (
        :public_restaurant_id, :source_code, :source_record_id, :source_restaurant_name,
        :source_branch_name, :source_region_name, :source_status, :match_method, :match_confidence,
        :parking_available, :wifi_available, :playroom_available, :multilingual_menu_available,
        :delivery_available, :smart_order_available, :restroom_info, :closed_days, :opening_hours,
        :reservation_info, :homepage_url, :nearby_landmark_name, :representative_menu, :hashtags,
        :area_info, :raw_record
    )
    ON DUPLICATE KEY UPDATE
        source_restaurant_name = VALUES(source_restaurant_name),
        source_branch_name = VALUES(source_branch_name), source_region_name = VALUES(source_region_name),
        source_status = VALUES(source_status), match_method = VALUES(match_method),
        match_confidence = VALUES(match_confidence), parking_available = VALUES(parking_available),
        wifi_available = VALUES(wifi_available), playroom_available = VALUES(playroom_available),
        multilingual_menu_available = VALUES(multilingual_menu_available),
        delivery_available = VALUES(delivery_available), smart_order_available = VALUES(smart_order_available),
        restroom_info = VALUES(restroom_info), closed_days = VALUES(closed_days),
        opening_hours = VALUES(opening_hours), reservation_info = VALUES(reservation_info),
        homepage_url = VALUES(homepage_url), nearby_landmark_name = VALUES(nearby_landmark_name),
        representative_menu = VALUES(representative_menu), hashtags = VALUES(hashtags),
        area_info = VALUES(area_info), raw_record = VALUES(raw_record)
""")

QUALITY_UPSERT = text("""
    INSERT INTO public_restaurant_quality_evidence (
        public_restaurant_id, source_code, source_record_id, source_restaurant_name,
        source_branch_name, source_region_name, match_method, match_confidence,
        award_description, rti_score, online_progress, acceptance_score, popularity_score,
        tripadvisor_rating, ctrip_rating, naver_rating, raw_record
    ) VALUES (
        :public_restaurant_id, :source_code, :source_record_id, :source_restaurant_name,
        :source_branch_name, :source_region_name, :match_method, :match_confidence,
        :award_description, :rti_score, :online_progress, :acceptance_score, :popularity_score,
        :tripadvisor_rating, :ctrip_rating, :naver_rating, :raw_record
    )
    ON DUPLICATE KEY UPDATE
        source_restaurant_name = VALUES(source_restaurant_name),
        source_branch_name = VALUES(source_branch_name), source_region_name = VALUES(source_region_name),
        match_method = VALUES(match_method), match_confidence = VALUES(match_confidence),
        award_description = VALUES(award_description), rti_score = VALUES(rti_score),
        online_progress = VALUES(online_progress), acceptance_score = VALUES(acceptance_score),
        popularity_score = VALUES(popularity_score), tripadvisor_rating = VALUES(tripadvisor_rating),
        ctrip_rating = VALUES(ctrip_rating), naver_rating = VALUES(naver_rating),
        raw_record = VALUES(raw_record)
""")

MENU_UPSERT = text("""
    INSERT INTO public_restaurant_menu_evidence (
        public_restaurant_id, source_code, source_restaurant_id, source_restaurant_name,
        source_branch_name, match_method, match_confidence, menu_count, priced_menu_count,
        minimum_menu_price, typical_menu_price, maximum_menu_price, menu_names,
        vegan_labeled_menu_available, vegetarian_labeled_menu_available,
        gluten_free_labeled_menu_available, price_examples, raw_summary
    ) VALUES (
        :public_restaurant_id, :source_code, :source_restaurant_id, :source_restaurant_name,
        :source_branch_name, :match_method, :match_confidence, :menu_count, :priced_menu_count,
        :minimum_menu_price, :typical_menu_price, :maximum_menu_price, :menu_names,
        :vegan_labeled_menu_available, :vegetarian_labeled_menu_available,
        :gluten_free_labeled_menu_available, :price_examples, :raw_summary
    )
    ON DUPLICATE KEY UPDATE
        source_restaurant_name = VALUES(source_restaurant_name),
        source_branch_name = VALUES(source_branch_name), match_method = VALUES(match_method),
        match_confidence = VALUES(match_confidence), menu_count = VALUES(menu_count),
        priced_menu_count = VALUES(priced_menu_count), minimum_menu_price = VALUES(minimum_menu_price),
        typical_menu_price = VALUES(typical_menu_price), maximum_menu_price = VALUES(maximum_menu_price),
        menu_names = VALUES(menu_names),
        vegan_labeled_menu_available = VALUES(vegan_labeled_menu_available),
        vegetarian_labeled_menu_available = VALUES(vegetarian_labeled_menu_available),
        gluten_free_labeled_menu_available = VALUES(gluten_free_labeled_menu_available),
        price_examples = VALUES(price_examples), raw_summary = VALUES(raw_summary)
""")


def execute_batches(connection, statement, values: list[dict[str, Any]], size: int = 1_000) -> None:
    for offset in range(0, len(values), size):
        connection.execute(statement, values[offset:offset + size])


def apply_rows(
    engine: Engine,
    specs: dict[str, SourceSpec],
    operation_values: list[dict[str, Any]],
    quality_values: list[dict[str, Any]],
    menu_values: list[dict[str, Any]],
) -> None:
    with engine.begin() as connection:
        connection.execute(SOURCE_UPSERT, [source_parameters(spec) for spec in specs.values()])
        execute_batches(connection, OPERATION_UPSERT, operation_values)
        execute_batches(connection, QUALITY_UPSERT, quality_values)
        execute_batches(connection, MENU_UPSERT, menu_values)


def field_coverage(values: list[dict[str, Any]], columns: tuple[str, ...]) -> dict[str, int]:
    return {column: sum(1 for value in values if value.get(column) not in (None, "", False)) for column in columns}


def write_report(report: dict[str, Any], explicit_path: Path | None) -> list[Path]:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report_id = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    paths = [
        REPORT_DIR / "seoul_tourism_import_latest.json",
        REPORT_DIR / f"seoul_tourism_import_{report_id}.json",
    ]
    if explicit_path is not None:
        paths.append(explicit_path.resolve())
    payload = json.dumps(report, ensure_ascii=False, indent=2, default=str) + "\n"
    for path in dict.fromkeys(paths):
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(payload, encoding="utf-8")
        os.replace(temporary, path)
    return list(dict.fromkeys(paths))


def main() -> int:
    args = arguments()
    specs = load_sources()
    file_reports = {kind: validate_file(spec) for kind, spec in specs.items()}
    operation_rows, operation_validation = read_operation_rows(specs["operation"])

    engine = create_engine(_database_url())
    try:
        restaurants = fetch_restaurants(engine)
        matches, match_report = strict_matches(operation_rows, restaurants)
        matches_by_source_id = {
            clean(match.record["식당(ID)"]) or "": match
            for match in matches
        }
        operation_values = [
            operation_parameters(match, specs["operation"].source_code)
            for match in matches
        ]
        quality_values, quality_report = quality_parameters(specs["quality"], matches_by_source_id)
        menu_values, menu_report = menu_parameters(specs["menu"], matches_by_source_id)

        impacts = {
            "operation": impact(
                operation_values,
                existing_rows(
                    engine, "public_restaurant_enrichment", "source_record_id",
                    specs["operation"].source_code,
                ),
                "source_record_id",
            ),
            "quality": impact(
                quality_values,
                existing_rows(
                    engine, "public_restaurant_quality_evidence", "source_record_id",
                    specs["quality"].source_code,
                ),
                "source_record_id",
            ),
            "menu": impact(
                menu_values,
                existing_rows(
                    engine, "public_restaurant_menu_evidence", "source_restaurant_id",
                    specs["menu"].source_code,
                ),
                "source_restaurant_id",
            ),
        }
        conflicts = sum(len(value["conflicts"]) for value in impacts.values())
        if conflicts:
            raise RuntimeError(f"Evidence import has {conflicts} existing key conflicts.")

        created_tables: list[str] = []
        if args.apply:
            required_tables = {
                "public_data_source", "public_restaurant_enrichment",
                "public_restaurant_quality_evidence", "public_restaurant_menu_evidence",
            }
            missing = sorted(table for table in required_tables if not table_exists(engine, table))
            v18_missing = [
                table for table in missing
                if table in {"public_data_source", "public_restaurant_enrichment"}
            ]
            if v18_missing:
                raise RuntimeError(f"V18 tables are missing: {', '.join(v18_missing)}")
            if missing:
                if not args.create_schema:
                    raise RuntimeError(
                        "V19 evidence tables are missing; rerun with --create-schema after reviewing the plan."
                    )
                created_tables = apply_schema(engine)
            apply_rows(engine, specs, operation_values, quality_values, menu_values)

        report = {
            "reportVersion": 1,
            "generatedAt": datetime.now(timezone.utc).isoformat(),
            "mode": "APPLY" if args.apply else "DRY_RUN",
            "manifest": str(MANIFEST_PATH.relative_to(BASE_DIR)),
            "files": file_reports,
            "operationValidation": operation_validation,
            "databaseRestaurantCount": len(restaurants),
            "matching": match_report,
            "quality": quality_report,
            "menu": menu_report,
            "impact": impacts,
            "coverage": {
                "operation": field_coverage(operation_values, tuple(sorted(
                    OPERATION_BOOLEAN_COLUMNS.keys() | OPERATION_TEXT_COLUMNS.keys()
                ))),
                "quality": field_coverage(quality_values, (
                    "award_description", "rti_score", "acceptance_score", "popularity_score",
                    "tripadvisor_rating", "ctrip_rating", "naver_rating",
                )),
                "menu": field_coverage(menu_values, (
                    "typical_menu_price", "menu_names", "vegan_labeled_menu_available",
                    "vegetarian_labeled_menu_available", "gluten_free_labeled_menu_available",
                )),
            },
            "createdTables": created_tables,
            "baseRestaurantRowsModified": 0,
            "existingMenuRowsModified": 0,
            "trainingPerformed": False,
        }
        paths = write_report(report, args.report)
        print(
            f"Seoul Tourism evidence {'applied' if args.apply else 'planned'}: "
            f"operations={len(operation_values):,}, quality={len(quality_values):,}, "
            f"menu={len(menu_values):,}, conflicts={conflicts}"
        )
        print(f"JSON report: {paths[0]}")
        return 0
    finally:
        engine.dispose()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"[ERROR] {error}")
        raise SystemExit(1)
