import argparse
import csv
import hashlib
import json
import os
import re
import unicodedata
from collections import Counter, defaultdict
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Any

from sqlalchemy import URL, bindparam, create_engine, text
from sqlalchemy.engine import Engine, make_url


BASE_DIR = Path(__file__).resolve().parent
PROJECT_DIR = BASE_DIR.parent
ENV_PATH = PROJECT_DIR / ".env"
DATA_DIR = BASE_DIR / "data" / "public_enrichment"
MANIFEST_PATH = DATA_DIR / "sources.json"
REPORT_DIR = DATA_DIR / "reports"
MIGRATION_PATH = PROJECT_DIR / "src" / "main" / "resources" / "db" / "migration" / \
    "V18__create_public_restaurant_enrichment.sql"

EXPECTED_HEADERS = (
    "식당(ID)",
    "식당명",
    "지점명",
    "지역명",
    "주차가능여부",
    "와이파이제공여부",
    "놀이방유무",
    "다국어메뉴판제공여부",
    "화장실정보내용",
    "휴무일정보내용",
    "영업시간내용",
    "배달서비스유무",
    "온라인예약정보내용",
    "홈페이지(URL)",
    "인근랜드마크명",
    "인근랜드마크위도",
    "인근랜드마크경도",
    "인근랜드마크와거리",
    "스마트오더유무",
    "대표메뉴명",
    "식당상태",
    "해시태그",
    "면적정보내용",
)

ENRICHMENT_TEXT_COLUMNS = {
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

ENRICHMENT_BOOLEAN_COLUMNS = {
    "parking_available": "주차가능여부",
    "wifi_available": "와이파이제공여부",
    "playroom_available": "놀이방유무",
    "multilingual_menu_available": "다국어메뉴판제공여부",
    "delivery_available": "배달서비스유무",
    "smart_order_available": "스마트오더유무",
}


@dataclass(frozen=True)
class SourceConfig:
    source_code: str
    provider_name: str
    dataset_name: str
    source_page_url: str
    download_url: str
    license_name: str
    region_name: str
    published_on: str
    retrieved_at: str
    raw_file: Path
    encoding: str
    row_count: int
    byte_size: int
    sha256: str


@dataclass(frozen=True)
class RestaurantReference:
    public_restaurant_id: int
    name: str
    branch_name: str | None
    region_name: str


@dataclass(frozen=True)
class MatchedRecord:
    record: dict[str, str]
    public_restaurant_id: int
    match_method: str
    match_confidence: float


def _arguments() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate and import provenance-backed public restaurant enrichment data."
    )
    parser.add_argument("--source-code", help="Source code from sources.json; defaults to its only source")
    parser.add_argument("--apply", action="store_true", help="Write verified rows to the configured MySQL DB")
    parser.add_argument(
        "--create-schema",
        action="store_true",
        help="Apply V18 before import when the enrichment tables do not exist",
    )
    parser.add_argument("--report", type=Path, help="Additional JSON report output path")
    return parser.parse_args()


def _read_env_file() -> dict[str, str]:
    values: dict[str, str] = {}
    if not ENV_PATH.exists():
        return values
    for raw_line in ENV_PATH.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        values[key.strip()] = value.strip().strip("'").strip('"')
    return values


def _database_url() -> URL:
    env_values = _read_env_file()
    raw_url = os.environ.get("DB_URL") or env_values.get("DB_URL")
    username = os.environ.get("DB_USERNAME") or env_values.get("DB_USERNAME")
    password = os.environ.get("DB_PASSWORD") or env_values.get("DB_PASSWORD")
    if not raw_url or not username or not password:
        raise RuntimeError("DB_URL, DB_USERNAME and DB_PASSWORD are required.")

    parsed = make_url(raw_url.removeprefix("jdbc:"))
    if not parsed.host or not parsed.database:
        raise RuntimeError("DB_URL must contain a host and database name.")
    return URL.create(
        "mysql+pymysql",
        username=username,
        password=password,
        host=parsed.host,
        port=parsed.port or 3306,
        database=parsed.database,
        query={"charset": "utf8mb4"},
    )


def _load_source(source_code: str | None) -> SourceConfig:
    manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
    if manifest.get("schemaVersion") != 1:
        raise RuntimeError("Unsupported public enrichment manifest schema version.")
    sources = manifest.get("sources")
    if not isinstance(sources, list) or not sources:
        raise RuntimeError("sources.json contains no sources.")
    if source_code is None:
        if len(sources) != 1:
            raise RuntimeError("--source-code is required when multiple sources are configured.")
        value = sources[0]
    else:
        value = next((item for item in sources if item.get("sourceCode") == source_code), None)
        if value is None:
            raise RuntimeError(f"Unknown source code: {source_code}")
    return SourceConfig(
        source_code=value["sourceCode"],
        provider_name=value["providerName"],
        dataset_name=value["datasetName"],
        source_page_url=value["sourcePageUrl"],
        download_url=value["downloadUrl"],
        license_name=value["licenseName"],
        region_name=value["regionName"],
        published_on=value["publishedOn"],
        retrieved_at=value["retrievedAt"],
        raw_file=(DATA_DIR / value["rawFile"]).resolve(),
        encoding=value["encoding"],
        row_count=int(value["rowCount"]),
        byte_size=int(value["byteSize"]),
        sha256=value["sha256"].lower(),
    )


def _file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _clean(value: Any) -> str | None:
    if value is None:
        return None
    cleaned = " ".join(str(value).strip().split())
    return cleaned or None


def normalize_identity(value: str | None) -> str:
    normalized = unicodedata.normalize("NFKC", value or "").lower()
    return re.sub(r"[^0-9a-z가-힣]", "", normalized)


def parse_yes_no(value: str | None) -> bool | None:
    normalized = (value or "").strip().upper()
    if normalized == "Y":
        return True
    if normalized == "N":
        return False
    if not normalized:
        return None
    raise ValueError(f"Unexpected Y/N value: {value}")


def _load_and_validate_rows(config: SourceConfig) -> tuple[list[dict[str, str]], dict[str, Any]]:
    if not config.raw_file.is_file():
        raise RuntimeError(f"Raw source file is missing: {config.raw_file}")
    actual_bytes = config.raw_file.stat().st_size
    actual_sha256 = _file_sha256(config.raw_file)
    if actual_bytes != config.byte_size:
        raise RuntimeError(f"Raw file byte size mismatch: {actual_bytes} != {config.byte_size}")
    if actual_sha256 != config.sha256:
        raise RuntimeError("Raw file SHA-256 does not match sources.json.")

    with config.raw_file.open("r", encoding=config.encoding, newline="") as file_handle:
        reader = csv.DictReader(file_handle)
        if tuple(reader.fieldnames or ()) != EXPECTED_HEADERS:
            raise RuntimeError("Official CSV headers do not match the expected schema.")
        rows = [dict(row) for row in reader]

    if len(rows) != config.row_count:
        raise RuntimeError(f"Raw row count mismatch: {len(rows)} != {config.row_count}")
    invalid_regions = Counter(
        _clean(row["지역명"])
        for row in rows
        if _clean(row["지역명"]) != config.region_name
    )
    if invalid_regions:
        raise RuntimeError(f"Unexpected source regions: {dict(invalid_regions)}")
    for row in rows:
        for column_name in ENRICHMENT_BOOLEAN_COLUMNS.values():
            parse_yes_no(row[column_name])

    return rows, {
        "manifestSchemaVersion": 1,
        "rawFile": str(config.raw_file.relative_to(BASE_DIR)),
        "encoding": config.encoding,
        "byteSize": actual_bytes,
        "rowCount": len(rows),
        "sha256": actual_sha256,
        "headersValid": True,
        "regionsValid": True,
    }


def _fetch_restaurants(engine: Engine, region_name: str) -> list[RestaurantReference]:
    statement = text("""
        SELECT public_restaurant_id, name, branch_name, sigungu_name
          FROM public_restaurant
         WHERE status = 'ACTIVE'
           AND sigungu_name = :region_name
         ORDER BY public_restaurant_id
    """)
    with engine.connect() as connection:
        rows = connection.execute(statement, {"region_name": region_name}).mappings().all()
    return [
        RestaurantReference(
            public_restaurant_id=int(row["public_restaurant_id"]),
            name=row["name"],
            branch_name=_clean(row["branch_name"]),
            region_name=row["sigungu_name"],
        )
        for row in rows
    ]


def match_records(
    rows: list[dict[str, str]],
    restaurants: list[RestaurantReference],
) -> tuple[list[MatchedRecord], Counter[str]]:
    source_name_counts = Counter(normalize_identity(row["식당명"]) for row in rows)
    db_name_index: dict[str, list[RestaurantReference]] = defaultdict(list)
    db_name_branch_index: dict[str, list[RestaurantReference]] = defaultdict(list)
    for restaurant in restaurants:
        db_name_index[normalize_identity(restaurant.name)].append(restaurant)
        if restaurant.branch_name:
            combined = normalize_identity(f"{restaurant.name} {restaurant.branch_name}")
            db_name_branch_index[combined].append(restaurant)

    matched: list[MatchedRecord] = []
    reasons: Counter[str] = Counter()
    for row in rows:
        if (_clean(row["식당상태"]) or "").upper() != "NORMAL":
            reasons["SOURCE_STATUS_NOT_NORMAL"] += 1
            continue
        source_name = normalize_identity(row["식당명"])
        source_branch = normalize_identity(row["지점명"])
        if not source_name:
            reasons["SOURCE_NAME_EMPTY"] += 1
            continue

        if source_branch:
            combined = normalize_identity(f"{row['식당명']} {row['지점명']}")
            candidates = {
                item.public_restaurant_id: item
                for item in db_name_index.get(combined, []) + db_name_branch_index.get(combined, [])
            }
            match_method = "EXACT_NAME_BRANCH"
            confidence = 0.995
        else:
            if source_name_counts[source_name] != 1:
                reasons["SOURCE_NAME_AMBIGUOUS"] += 1
                continue
            candidates = {
                item.public_restaurant_id: item
                for item in db_name_index.get(source_name, [])
            }
            match_method = "EXACT_UNIQUE_NAME_REGION"
            confidence = 1.0

        if not candidates:
            reasons["DB_MATCH_NOT_FOUND"] += 1
            continue
        if len(candidates) != 1:
            reasons["DB_MATCH_AMBIGUOUS"] += 1
            continue
        restaurant = next(iter(candidates.values()))
        matched.append(MatchedRecord(row, restaurant.public_restaurant_id, match_method, confidence))
        reasons[match_method] += 1
    return matched, reasons


def _record_completeness(row: dict[str, str]) -> int:
    columns = list(ENRICHMENT_TEXT_COLUMNS.values()) + list(ENRICHMENT_BOOLEAN_COLUMNS.values())
    return sum(1 for column in columns if _clean(row[column]) is not None)


def _source_record_sort_key(value: str) -> tuple[int, int | str]:
    return (0, int(value)) if value.isdigit() else (1, value)


def deduplicate_matches(matches: list[MatchedRecord]) -> tuple[list[MatchedRecord], int]:
    grouped: dict[int, list[MatchedRecord]] = defaultdict(list)
    for match in matches:
        grouped[match.public_restaurant_id].append(match)
    selected: list[MatchedRecord] = []
    duplicates_removed = 0
    for public_restaurant_id in sorted(grouped):
        candidates = sorted(
            grouped[public_restaurant_id],
            key=lambda value: (
                -_record_completeness(value.record),
                _source_record_sort_key(value.record["식당(ID)"]),
            ),
        )
        selected.append(candidates[0])
        duplicates_removed += len(candidates) - 1
    return selected, duplicates_removed


def _enrichment_parameters(match: MatchedRecord, source_code: str) -> dict[str, Any]:
    row = match.record
    parameters: dict[str, Any] = {
        "public_restaurant_id": match.public_restaurant_id,
        "source_code": source_code,
        "source_record_id": _clean(row["식당(ID)"],) or "",
        "source_restaurant_name": _clean(row["식당명"]) or "",
        "source_branch_name": _clean(row["지점명"]),
        "source_region_name": _clean(row["지역명"]) or "",
        "source_status": _clean(row["식당상태"]),
        "match_method": match.match_method,
        "match_confidence": match.match_confidence,
        "raw_record": json.dumps(row, ensure_ascii=False, separators=(",", ":")),
    }
    parameters.update({key: parse_yes_no(row[column]) for key, column in ENRICHMENT_BOOLEAN_COLUMNS.items()})
    parameters.update({key: _clean(row[column]) for key, column in ENRICHMENT_TEXT_COLUMNS.items()})
    return parameters


def _table_exists(engine: Engine, table_name: str) -> bool:
    statement = text("""
        SELECT COUNT(*)
          FROM information_schema.tables
         WHERE table_schema = DATABASE()
           AND table_name = :table_name
    """)
    with engine.connect() as connection:
        return bool(connection.scalar(statement, {"table_name": table_name}))


def _apply_schema(engine: Engine) -> None:
    sql = MIGRATION_PATH.read_text(encoding="utf-8")
    statements = [statement.strip() for statement in sql.split(";") if statement.strip()]
    if len(statements) != 2 or not all(statement.upper().startswith("CREATE TABLE") for statement in statements):
        raise RuntimeError("V18 migration must contain exactly two CREATE TABLE statements.")
    with engine.begin() as connection:
        for statement in statements:
            connection.exec_driver_sql(statement)


def _existing_rows(engine: Engine, source_code: str) -> list[dict[str, Any]]:
    if not _table_exists(engine, "public_restaurant_enrichment"):
        return []
    with engine.connect() as connection:
        rows = connection.execute(
            text("""
                SELECT enrichment_id, public_restaurant_id, source_record_id
                  FROM public_restaurant_enrichment
                 WHERE source_code = :source_code
            """),
            {"source_code": source_code},
        ).mappings().all()
    return [dict(row) for row in rows]


def _impact(parameters: list[dict[str, Any]], existing: list[dict[str, Any]]) -> dict[str, Any]:
    by_record = {str(row["source_record_id"]): row for row in existing}
    by_restaurant = {int(row["public_restaurant_id"]): row for row in existing}
    inserts = 0
    updates = 0
    conflicts: list[dict[str, Any]] = []
    for value in parameters:
        record_id = value["source_record_id"]
        restaurant_id = value["public_restaurant_id"]
        record_match = by_record.get(record_id)
        restaurant_match = by_restaurant.get(restaurant_id)
        if record_match or restaurant_match:
            existing_row = record_match or restaurant_match
            if (str(existing_row["source_record_id"]) != record_id
                    or int(existing_row["public_restaurant_id"]) != restaurant_id):
                conflicts.append({
                    "sourceRecordId": record_id,
                    "publicRestaurantId": restaurant_id,
                    "existingSourceRecordId": str(existing_row["source_record_id"]),
                    "existingPublicRestaurantId": int(existing_row["public_restaurant_id"]),
                })
            else:
                value["enrichment_id"] = int(existing_row["enrichment_id"])
                updates += 1
        else:
            inserts += 1
    return {
        "plannedInserts": inserts,
        "plannedUpdates": updates,
        "untouchedExistingRows": max(0, len(existing) - updates),
        "conflicts": conflicts,
    }


def _source_parameters(config: SourceConfig) -> dict[str, Any]:
    return {
        "source_code": config.source_code,
        "provider_name": config.provider_name,
        "dataset_name": config.dataset_name,
        "source_page_url": config.source_page_url,
        "download_url": config.download_url,
        "license_name": config.license_name,
        "source_published_on": config.published_on,
        "retrieved_at": datetime.fromisoformat(config.retrieved_at),
        "raw_file_sha256": config.sha256,
        "raw_row_count": config.row_count,
    }


def _apply_rows(
    engine: Engine,
    config: SourceConfig,
    parameters: list[dict[str, Any]],
    impact: dict[str, Any],
) -> None:
    if impact["conflicts"]:
        raise RuntimeError("Existing enrichment keys conflict with the verified match plan.")
    source_statement = text("""
        INSERT INTO public_data_source (
            source_code, provider_name, dataset_name, source_page_url, download_url,
            license_name, source_published_on, retrieved_at, raw_file_sha256, raw_row_count
        ) VALUES (
            :source_code, :provider_name, :dataset_name, :source_page_url, :download_url,
            :license_name, :source_published_on, :retrieved_at, :raw_file_sha256, :raw_row_count
        )
        ON DUPLICATE KEY UPDATE
            provider_name = VALUES(provider_name),
            dataset_name = VALUES(dataset_name),
            source_page_url = VALUES(source_page_url),
            download_url = VALUES(download_url),
            license_name = VALUES(license_name),
            source_published_on = VALUES(source_published_on),
            retrieved_at = VALUES(retrieved_at),
            raw_file_sha256 = VALUES(raw_file_sha256),
            raw_row_count = VALUES(raw_row_count)
    """)
    insert_statement = text("""
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
    """)
    update_statement = text("""
        UPDATE public_restaurant_enrichment
           SET public_restaurant_id = :public_restaurant_id,
               source_restaurant_name = :source_restaurant_name,
               source_branch_name = :source_branch_name,
               source_region_name = :source_region_name,
               source_status = :source_status,
               match_method = :match_method,
               match_confidence = :match_confidence,
               parking_available = :parking_available,
               wifi_available = :wifi_available,
               playroom_available = :playroom_available,
               multilingual_menu_available = :multilingual_menu_available,
               delivery_available = :delivery_available,
               smart_order_available = :smart_order_available,
               restroom_info = :restroom_info,
               closed_days = :closed_days,
               opening_hours = :opening_hours,
               reservation_info = :reservation_info,
               homepage_url = :homepage_url,
               nearby_landmark_name = :nearby_landmark_name,
               representative_menu = :representative_menu,
               hashtags = :hashtags,
               area_info = :area_info,
               raw_record = :raw_record
         WHERE enrichment_id = :enrichment_id
           AND source_code = :source_code
           AND source_record_id = :source_record_id
    """)
    inserts = [value for value in parameters if "enrichment_id" not in value]
    updates = [value for value in parameters if "enrichment_id" in value]
    with engine.begin() as connection:
        connection.execute(source_statement, _source_parameters(config))
        if inserts:
            connection.execute(insert_statement, inserts)
        if updates:
            connection.execute(update_statement, updates)


def _field_coverage(parameters: list[dict[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key in ENRICHMENT_BOOLEAN_COLUMNS:
        result[key] = {
            "known": sum(value[key] is not None for value in parameters),
            "true": sum(value[key] is True for value in parameters),
        }
    for key in ENRICHMENT_TEXT_COLUMNS:
        result[key] = sum(_clean(value[key]) is not None for value in parameters)
    return result


def _write_report(report: dict[str, Any], explicit_path: Path | None) -> list[Path]:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().astimezone().strftime("%Y%m%d_%H%M%S")
    paths = [REPORT_DIR / f"public_enrichment_{timestamp}.json", REPORT_DIR / "latest.json"]
    if explicit_path is not None:
        paths.append(explicit_path.resolve())
    serialized = json.dumps(report, ensure_ascii=False, indent=2)
    for path in dict.fromkeys(paths):
        path.parent.mkdir(parents=True, exist_ok=True)
        temporary = path.with_suffix(path.suffix + ".tmp")
        temporary.write_text(serialized, encoding="utf-8")
        os.replace(temporary, path)
    return paths


def main() -> int:
    arguments = _arguments()
    if arguments.create_schema and not arguments.apply:
        raise RuntimeError("--create-schema requires --apply.")
    config = _load_source(arguments.source_code)
    rows, validation = _load_and_validate_rows(config)
    database_url = _database_url()
    engine = create_engine(database_url, pool_pre_ping=True)
    try:
        restaurants = _fetch_restaurants(engine, config.region_name)
        matches, match_reasons = match_records(rows, restaurants)
        selected, duplicates_removed = deduplicate_matches(matches)
        parameters = [_enrichment_parameters(match, config.source_code) for match in selected]
        existing = _existing_rows(engine, config.source_code)
        impact = _impact(parameters, existing)
        report: dict[str, Any] = {
            "reportVersion": 1,
            "generatedAt": datetime.now().astimezone().isoformat(),
            "mode": "APPLY" if arguments.apply else "DRY_RUN",
            "status": "VALIDATED",
            "source": {
                "sourceCode": config.source_code,
                "providerName": config.provider_name,
                "datasetName": config.dataset_name,
                "sourcePageUrl": config.source_page_url,
                "licenseName": config.license_name,
                "publishedOn": config.published_on,
            },
            "sourceValidation": validation,
            "database": {
                "databaseName": database_url.database,
                "region": config.region_name,
                "eligibleRestaurantRows": len(restaurants),
            },
            "matching": {
                "sourceRows": len(rows),
                "matchedRowsBeforeDeduplication": len(matches),
                "uniqueMatchedRestaurants": len(selected),
                "duplicatesRemoved": duplicates_removed,
                "reasonCounts": dict(sorted(match_reasons.items())),
            },
            "fieldCoverage": _field_coverage(parameters),
            "impact": impact,
            "safety": {
                "baseRestaurantWrites": 0,
                "menuWrites": 0,
                "deleteStatements": 0,
                "targetTables": ["public_data_source", "public_restaurant_enrichment"],
            },
        }
        if arguments.apply:
            tables_exist = _table_exists(engine, "public_data_source") and _table_exists(
                engine, "public_restaurant_enrichment"
            )
            if not tables_exist:
                if not arguments.create_schema:
                    raise RuntimeError("Enrichment tables are missing; rerun with --apply --create-schema.")
                _apply_schema(engine)
            existing = _existing_rows(engine, config.source_code)
            impact = _impact(parameters, existing)
            report["impact"] = impact
            _apply_rows(engine, config, parameters, impact)
            verified_rows = _existing_rows(engine, config.source_code)
            verified_pairs = {
                (str(row["source_record_id"]), int(row["public_restaurant_id"]))
                for row in verified_rows
            }
            expected_pairs = {
                (value["source_record_id"], value["public_restaurant_id"])
                for value in parameters
            }
            if not expected_pairs.issubset(verified_pairs):
                raise RuntimeError("Post-import verification found missing enrichment rows.")
            report["status"] = "APPLIED_AND_VERIFIED"
            report["applied"] = {
                "inserted": impact["plannedInserts"],
                "updated": impact["plannedUpdates"],
                "verifiedSourceRows": len(verified_rows),
            }
        paths = _write_report(report, arguments.report)
        print(json.dumps({
            "status": report["status"],
            "uniqueMatchedRestaurants": len(selected),
            "plannedInserts": report["impact"]["plannedInserts"],
            "plannedUpdates": report["impact"]["plannedUpdates"],
            "report": str(paths[1]),
        }, ensure_ascii=False))
        return 0
    finally:
        engine.dispose()


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(json.dumps({"status": "ERROR", "error": str(error)}, ensure_ascii=False))
        raise SystemExit(1)
