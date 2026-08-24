import argparse
import hashlib
import json
import os
import shutil
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd
from sqlalchemy import URL, create_engine, text
from sqlalchemy.engine import make_url
from sklearn.feature_extraction.text import TfidfVectorizer


BASE_DIR = Path(__file__).resolve().parent
OUTPUT_DIR = BASE_DIR / "model"
KURE_ROOT = OUTPUT_DIR / "kure"
CURRENT_POINTER_PATH = KURE_ROOT / "current.json"
TFIDF_ROLLBACK_ROOT = OUTPUT_DIR / "tfidf"
DOCUMENT_V2_DIR = BASE_DIR / "data" / "document_v2"
SPRING_MODEL_DIR = BASE_DIR.parent / "src" / "main" / "resources" / "recommendation" / "model"
ENV_PATH = BASE_DIR.parent / ".env"

EMBEDDING_MODEL_NAME = "nlpai-lab/KURE-v1"
DOCUMENT_VERSION = 1
LEGACY_EMBEDDINGS_PATH = OUTPUT_DIR / "restaurant_embeddings.npy"
LEGACY_META_PATH = OUTPUT_DIR / "restaurants_meta.pkl"
TFIDF_FILENAMES = ("vocabulary.json", "idf.json", "model-meta.json")

TEXT_COLUMNS = (
    "name",
    "category_large_name",
    "category_medium_name",
    "category_small_name",
    "road_address",
    "lot_address",
)

V2_TEXT_COLUMNS = (
    "closed_days",
    "opening_hours",
    "reservation_info",
    "representative_menu",
    "verified_menu_names",
    "hashtags",
    "area_info",
    "award_description",
    "source_codes",
)

FOOD_SYNONYMS = {
    "전": ["파전", "해물파전", "김치전", "감자전", "녹두전", "빈대떡", "부침개", "지짐이", "민속주점", "주막", "막걸리"],
    "파전": ["해물파전", "전", "빈대떡", "부침개", "민속주점", "막걸리"],
    "비": ["비오는날", "파전", "김치전", "수제비", "칼국수", "막걸리", "전"],
    "면": ["칼국수", "라멘", "우동", "짜장면", "짬뽕", "파스타", "소바", "냉면", "국수"],
    "해장": ["국밥", "순대국", "뼈해장국", "황태해장국", "콩나물국밥", "라면", "짬뽕"],
    "고기": ["삼겹살", "돼지갈비", "소고기", "한우", "구이", "생고기", "목살"],
}


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


def fetch_restaurants_from_db(document_version: int = DOCUMENT_VERSION) -> pd.DataFrame:
    if document_version == 1:
        query = """
        SELECT
            public_restaurant_id AS id,
            name,
            category_large_name,
            category_medium_name,
            category_small_name,
            road_address,
            lot_address,
            latitude,
            longitude
        FROM public_restaurant
        ORDER BY public_restaurant_id
        """
    elif document_version == 2:
        query = """
        SELECT
            p.public_restaurant_id AS id,
            p.name,
            p.category_large_name,
            p.category_medium_name,
            p.category_small_name,
            p.road_address,
            p.lot_address,
            p.latitude,
            p.longitude,
            e.parking_available,
            e.wifi_available,
            e.playroom_available,
            e.multilingual_menu_available,
            e.delivery_available,
            e.smart_order_available,
            e.closed_days,
            e.opening_hours,
            e.reservation_info,
            e.representative_menu,
            e.hashtags,
            e.area_info,
            m.verified_menu_names,
            m.menu_count,
            m.priced_menu_count,
            m.minimum_menu_price,
            m.typical_menu_price,
            m.maximum_menu_price,
            m.vegan_labeled_menu_available,
            m.vegetarian_labeled_menu_available,
            m.gluten_free_labeled_menu_available,
            q.award_description,
            q.rti_score,
            q.acceptance_score,
            q.popularity_score,
            q.naver_rating,
            q.tripadvisor_rating,
            q.ctrip_rating,
            src.source_codes,
            rv.average_rating,
            COALESCE(rv.review_count, 0) AS review_count
        FROM public_restaurant p
        LEFT JOIN (
            SELECT
                public_restaurant_id,
                MAX(parking_available) AS parking_available,
                MAX(wifi_available) AS wifi_available,
                MAX(playroom_available) AS playroom_available,
                MAX(multilingual_menu_available) AS multilingual_menu_available,
                MAX(delivery_available) AS delivery_available,
                MAX(smart_order_available) AS smart_order_available,
                GROUP_CONCAT(DISTINCT closed_days ORDER BY source_code SEPARATOR ' | ') AS closed_days,
                GROUP_CONCAT(DISTINCT opening_hours ORDER BY source_code SEPARATOR ' | ') AS opening_hours,
                GROUP_CONCAT(DISTINCT reservation_info ORDER BY source_code SEPARATOR ' | ') AS reservation_info,
                GROUP_CONCAT(DISTINCT representative_menu ORDER BY source_code SEPARATOR ' | ') AS representative_menu,
                GROUP_CONCAT(DISTINCT hashtags ORDER BY source_code SEPARATOR ',') AS hashtags,
                GROUP_CONCAT(DISTINCT area_info ORDER BY source_code SEPARATOR ' | ') AS area_info
            FROM public_restaurant_enrichment
            GROUP BY public_restaurant_id
        ) e ON e.public_restaurant_id = p.public_restaurant_id
        LEFT JOIN (
            SELECT
                public_restaurant_id,
                MAX(menu_names) AS verified_menu_names,
                MAX(menu_count) AS menu_count,
                MAX(priced_menu_count) AS priced_menu_count,
                MIN(minimum_menu_price) AS minimum_menu_price,
                MIN(typical_menu_price) AS typical_menu_price,
                MAX(maximum_menu_price) AS maximum_menu_price,
                MAX(vegan_labeled_menu_available) AS vegan_labeled_menu_available,
                MAX(vegetarian_labeled_menu_available) AS vegetarian_labeled_menu_available,
                MAX(gluten_free_labeled_menu_available) AS gluten_free_labeled_menu_available
            FROM public_restaurant_menu_evidence
            GROUP BY public_restaurant_id
        ) m ON m.public_restaurant_id = p.public_restaurant_id
        LEFT JOIN (
            SELECT
                public_restaurant_id,
                GROUP_CONCAT(DISTINCT award_description ORDER BY source_code SEPARATOR ' | ')
                    AS award_description,
                MAX(rti_score) AS rti_score,
                MAX(acceptance_score) AS acceptance_score,
                MAX(popularity_score) AS popularity_score,
                MAX(naver_rating) AS naver_rating,
                MAX(tripadvisor_rating) AS tripadvisor_rating,
                MAX(ctrip_rating) AS ctrip_rating
            FROM public_restaurant_quality_evidence
            GROUP BY public_restaurant_id
        ) q ON q.public_restaurant_id = p.public_restaurant_id
        LEFT JOIN (
            SELECT
                public_restaurant_id,
                GROUP_CONCAT(DISTINCT source_code ORDER BY source_code SEPARATOR ' | ') AS source_codes
            FROM (
                SELECT public_restaurant_id, source_code FROM public_restaurant_enrichment
                UNION ALL
                SELECT public_restaurant_id, source_code FROM public_restaurant_quality_evidence
                UNION ALL
                SELECT public_restaurant_id, source_code FROM public_restaurant_menu_evidence
            ) evidence_sources
            GROUP BY public_restaurant_id
        ) src ON src.public_restaurant_id = p.public_restaurant_id
        LEFT JOIN (
            SELECT
                public_restaurant_id,
                AVG(rating) AS average_rating,
                COUNT(*) AS review_count
            FROM review
            WHERE status = 'ACTIVE'
              AND public_restaurant_id IS NOT NULL
            GROUP BY public_restaurant_id
        ) rv ON rv.public_restaurant_id = p.public_restaurant_id
        ORDER BY p.public_restaurant_id
        """
    else:
        raise ValueError(f"Unsupported document version: {document_version}")
    engine = create_engine(_database_url())
    try:
        with engine.connect() as connection:
            frame = pd.read_sql(text(query), connection)
    finally:
        engine.dispose()

    for column in TEXT_COLUMNS:
        frame[column] = frame[column].fillna("").astype(str)
    if document_version == 2:
        for column in V2_TEXT_COLUMNS:
            frame[column] = frame[column].fillna("").astype(str)
        for column in (
            "parking_available",
            "wifi_available",
            "playroom_available",
            "multilingual_menu_available",
            "delivery_available",
            "smart_order_available",
            "vegan_labeled_menu_available",
            "vegetarian_labeled_menu_available",
            "gluten_free_labeled_menu_available",
        ):
            frame[column] = pd.to_numeric(frame[column], errors="coerce")
        for column in (
            "menu_count",
            "priced_menu_count",
            "minimum_menu_price",
            "typical_menu_price",
            "maximum_menu_price",
            "rti_score",
            "acceptance_score",
            "popularity_score",
            "naver_rating",
            "tripadvisor_rating",
            "ctrip_rating",
        ):
            frame[column] = pd.to_numeric(frame[column], errors="coerce")
        frame["average_rating"] = pd.to_numeric(frame["average_rating"], errors="coerce")
        frame["review_count"] = pd.to_numeric(frame["review_count"], errors="coerce").fillna(0).astype(int)
    frame["latitude"] = pd.to_numeric(frame["latitude"], errors="coerce")
    frame["longitude"] = pd.to_numeric(frame["longitude"], errors="coerce")
    frame["id"] = pd.to_numeric(frame["id"], errors="raise").astype(np.int64)
    return frame


def _normalized_text(value) -> str:
    if value is None or (not isinstance(value, str) and pd.isna(value)):
        return ""
    return " ".join(str(value).split())


def canonical_document_v1(row) -> str:
    values = (
        row["name"],
        row["category_large_name"],
        row["category_medium_name"],
        row["category_small_name"],
        row["road_address"],
    )
    return " ".join(_normalized_text(value) for value in values if _normalized_text(value))


def canonical_document_v2(row) -> str:
    parts = [canonical_document_v1(row)]
    boolean_parts = (
        ("parking_available", "주차 가능"),
        ("wifi_available", "와이파이 제공"),
        ("playroom_available", "놀이방 제공"),
        ("multilingual_menu_available", "다국어 메뉴판 제공"),
        ("delivery_available", "배달 가능"),
        ("smart_order_available", "스마트오더 가능"),
    )
    for column, text_value in boolean_parts:
        value = row.get(column)
        if value is not None and not pd.isna(value) and int(value) == 1:
            parts.append(text_value)
    labeled_parts = (
        ("representative_menu", "대표메뉴"),
        ("verified_menu_names", "검증메뉴"),
    )
    for column, label in labeled_parts:
        value = _normalized_text(row.get(column))
        if value:
            parts.append(f"{label} {value}")
    typical_price = row.get("typical_menu_price")
    if typical_price is not None and not pd.isna(typical_price):
        price_evidence = f"공식 메뉴 대표가격 {int(typical_price)}원"
        minimum_price = row.get("minimum_menu_price")
        if minimum_price is not None and not pd.isna(minimum_price):
            price_evidence += f" 최저가격 {int(minimum_price)}원"
        priced_menu_count = row.get("priced_menu_count")
        if priced_menu_count is not None and not pd.isna(priced_menu_count) and int(priced_menu_count) > 0:
            price_evidence += f" 가격표본 {int(priced_menu_count)}개"
        parts.append(price_evidence)
    boolean_menu_parts = (
        ("vegan_labeled_menu_available", "비건 표기 메뉴 있음"),
        ("vegetarian_labeled_menu_available", "채식 표기 메뉴 있음"),
        ("gluten_free_labeled_menu_available", "글루텐프리 표기 메뉴 있음"),
    )
    for column, text_value in boolean_menu_parts:
        value = row.get(column)
        if value is not None and not pd.isna(value) and int(value) == 1:
            parts.append(text_value)
    labeled_parts = (
        ("hashtags", "해시태그"),
        ("opening_hours", "영업시간"),
        ("closed_days", "휴무일"),
        ("reservation_info", "예약정보"),
        ("area_info", "면적정보"),
    )
    for column, label in labeled_parts:
        value = _normalized_text(row.get(column))
        if value:
            parts.append(f"{label} {value}")
    award_description = _normalized_text(row.get("award_description"))
    if award_description:
        parts.append(f"공식 어워드 {award_description}")
    for column, label in (
        ("rti_score", "공식 RTI 지수"),
        ("acceptance_score", "공식 수용태세 지수"),
        ("popularity_score", "공식 인기도"),
    ):
        value = row.get(column)
        if value is not None and not pd.isna(value):
            parts.append(f"{label} {float(value):.2f}")
    official_ratings = (
        ("naver_rating", "네이버"),
        ("tripadvisor_rating", "트립어드바이저"),
        ("ctrip_rating", "씨트립"),
    )
    for column, provider in official_ratings:
        value = row.get(column)
        if value is not None and not pd.isna(value):
            parts.append(f"공식 외부평점 {provider} {float(value):.2f}")
            break
    review_count = int(row.get("review_count") or 0)
    average_rating = row.get("average_rating")
    if review_count > 0 and average_rating is not None and not pd.isna(average_rating):
        parts.append(f"FOODUCK 리뷰 평점 {float(average_rating):.2f} 리뷰 {review_count}개")
    return " ".join(part for part in parts if part)


def canonical_document(row, document_version: int = DOCUMENT_VERSION) -> str:
    if document_version == 1:
        return canonical_document_v1(row)
    if document_version == 2:
        return canonical_document_v2(row)
    raise ValueError(f"Unsupported document version: {document_version}")


def canonical_documents(
    frame: pd.DataFrame,
    document_version: int = DOCUMENT_VERSION,
) -> list[str]:
    return [canonical_document(row, document_version) for _, row in frame.iterrows()]


def restaurant_id_hash(ids) -> str:
    payload = ",".join(str(int(value)) for value in ids).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def document_corpus_hash(
    frame: pd.DataFrame,
    document_version: int = DOCUMENT_VERSION,
) -> str:
    digest = hashlib.sha256()
    for (_, row), document in zip(
        frame.iterrows(), canonical_documents(frame, document_version)
    ):
        digest.update(f"{int(row['id'])}\t{document}\n".encode("utf-8"))
    return digest.hexdigest()


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def enrich_text_with_synonyms(text_value: str) -> str:
    extra: list[str] = []
    for key, synonyms in FOOD_SYNONYMS.items():
        if key in text_value:
            extra.extend(synonyms)
        for synonym in synonyms:
            if synonym in text_value:
                extra.append(key)
                extra.extend(synonyms[:3])
                break
    return f"{text_value} {' '.join(dict.fromkeys(extra))}".strip()


def build_tfidf_model(
    frame: pd.DataFrame,
    document_version: int = DOCUMENT_VERSION,
    output_directory: Path = SPRING_MODEL_DIR,
) -> dict:
    if frame.empty:
        raise RuntimeError("No public restaurants were returned from MySQL.")

    corpus = [
        enrich_text_with_synonyms(document)
        for document in canonical_documents(frame, document_version)
    ]
    vectorizer = TfidfVectorizer(
        token_pattern=r"(?u)\b\w+\b",
        ngram_range=(1, 1),
        max_features=50000,
        sublinear_tf=False,
    )
    vectorizer.fit(corpus)

    output_directory.mkdir(parents=True, exist_ok=True)
    vocabulary = {word: int(index) for word, index in vectorizer.vocabulary_.items()}
    idf = [0.0] * len(vocabulary)
    for word, index in vocabulary.items():
        idf[index] = float(vectorizer.idf_[index])

    metadata = {
        "modelVersion": (
            "fooduck-tfidf-v3-canonical-unigram"
            if document_version == 1
            else "fooduck-tfidf-v4-document-v2-unigram"
        ),
        "vocabularySize": len(vocabulary),
        "totalDocuments": len(frame),
        "documentVersion": document_version,
        "documentCorpusHash": document_corpus_hash(frame, document_version),
        "builtAt": datetime.now(timezone.utc).isoformat(),
    }
    _write_json_atomic(output_directory / "vocabulary.json", vocabulary)
    _write_json_atomic(output_directory / "idf.json", idf)
    _write_json_atomic(output_directory / "model-meta.json", metadata)
    print(
        f"TF-IDF model prepared: vocabulary={len(vocabulary):,}, "
        f"documents={len(frame):,}, path={output_directory}"
    )
    return metadata


def _model_revision() -> str | None:
    reference = Path.home() / ".cache" / "huggingface" / "hub" / "models--nlpai-lab--KURE-v1" / "refs" / "main"
    if reference.exists():
        return reference.read_text(encoding="utf-8").strip() or None
    return None


def _write_json_atomic(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(json.dumps(value, ensure_ascii=False, indent=2), encoding="utf-8")
    os.replace(temporary, path)


def verify_tfidf_artifacts(
    directory: Path,
    frame: pd.DataFrame,
    document_version: int,
) -> dict:
    paths = {name: directory / name for name in TFIDF_FILENAMES}
    missing = [name for name, path in paths.items() if not path.is_file()]
    if missing:
        raise RuntimeError(f"TF-IDF files are missing: {', '.join(missing)}")
    vocabulary = json.loads(paths["vocabulary.json"].read_text(encoding="utf-8"))
    idf = json.loads(paths["idf.json"].read_text(encoding="utf-8"))
    metadata = json.loads(paths["model-meta.json"].read_text(encoding="utf-8"))
    if not isinstance(vocabulary, dict) or not isinstance(idf, list):
        raise RuntimeError("TF-IDF vocabulary or IDF has an invalid JSON structure.")
    expected_indexes = set(range(len(vocabulary)))
    if set(int(value) for value in vocabulary.values()) != expected_indexes:
        raise RuntimeError("TF-IDF vocabulary indexes are not contiguous.")
    if len(idf) != len(vocabulary):
        raise RuntimeError("TF-IDF IDF length does not match the vocabulary.")
    if int(metadata.get("vocabularySize", -1)) != len(vocabulary):
        raise RuntimeError("TF-IDF metadata vocabulary size mismatch.")
    if int(metadata.get("totalDocuments", -1)) != len(frame):
        raise RuntimeError("TF-IDF metadata restaurant count mismatch.")
    if int(metadata.get("documentVersion", -1)) != document_version:
        raise RuntimeError("TF-IDF metadata document version mismatch.")
    expected_corpus_hash = document_corpus_hash(frame, document_version)
    if metadata.get("documentCorpusHash") != expected_corpus_hash:
        raise RuntimeError("TF-IDF document corpus hash does not match MySQL.")
    print(
        "TF-IDF artifacts verified: "
        f"version={metadata.get('modelVersion')}, vocabulary={len(vocabulary):,}, "
        f"documents={len(frame):,}"
    )
    return metadata


def _backup_active_tfidf() -> Path | None:
    active_paths = [SPRING_MODEL_DIR / name for name in TFIDF_FILENAMES]
    existing_paths = [path for path in active_paths if path.exists()]
    if not existing_paths:
        return None
    now = datetime.now(timezone.utc)
    base_name = now.strftime("%Y%m%dT%H%M%SZ")
    backup_directory = TFIDF_ROLLBACK_ROOT / base_name
    suffix = 1
    while backup_directory.exists():
        backup_directory = TFIDF_ROLLBACK_ROOT / f"{base_name}_{suffix:02d}"
        suffix += 1
    backup_directory.mkdir(parents=True, exist_ok=False)
    for path in existing_paths:
        shutil.copy2(path, backup_directory / path.name)
    _write_json_atomic(backup_directory / "rollback.json", {
        "createdAt": now.isoformat(),
        "sourceDirectory": str(SPRING_MODEL_DIR),
        "files": {
            path.name: {"sha256": file_sha256(path), "byteSize": path.stat().st_size}
            for path in existing_paths
        },
    })
    return backup_directory


def _replace_tfidf_files(prepared_directory: Path) -> None:
    SPRING_MODEL_DIR.mkdir(parents=True, exist_ok=True)
    for name in TFIDF_FILENAMES:
        source = prepared_directory / name
        temporary = SPRING_MODEL_DIR / f".{name}.activate.tmp"
        shutil.copy2(source, temporary)
        os.replace(temporary, SPRING_MODEL_DIR / name)


def _restore_files(previous_files: dict[str, bytes | None]) -> None:
    for name, payload in previous_files.items():
        target = SPRING_MODEL_DIR / name
        if payload is None:
            target.unlink(missing_ok=True)
            continue
        temporary = SPRING_MODEL_DIR / f".{name}.rollback.tmp"
        temporary.write_bytes(payload)
        os.replace(temporary, target)


def activate_prepared_models(
    prepared_tfidf_directory: Path,
    frame: pd.DataFrame,
    document_version: int,
    kure_manifest: dict | None = None,
) -> Path | None:
    verify_tfidf_artifacts(prepared_tfidf_directory, frame, document_version)
    kure_directory = None
    if kure_manifest is not None:
        kure_directory = KURE_ROOT / kure_manifest["indexVersion"]
        verify_kure_bundle(kure_directory, kure_manifest, frame, document_version)

    previous_files = {
        name: (SPRING_MODEL_DIR / name).read_bytes()
        if (SPRING_MODEL_DIR / name).exists() else None
        for name in TFIDF_FILENAMES
    }
    previous_pointer = CURRENT_POINTER_PATH.read_bytes() if CURRENT_POINTER_PATH.exists() else None
    rollback_directory = _backup_active_tfidf()
    try:
        _replace_tfidf_files(prepared_tfidf_directory)
        if kure_manifest is not None:
            _write_json_atomic(CURRENT_POINTER_PATH, {"indexVersion": kure_manifest["indexVersion"]})
        verify_tfidf_artifacts(SPRING_MODEL_DIR, frame, document_version)
        if kure_manifest is not None:
            active_directory, active_manifest = _load_current_bundle()
            verify_kure_bundle(active_directory, active_manifest, frame, document_version)
    except Exception:
        _restore_files(previous_files)
        if kure_manifest is not None:
            if previous_pointer is None:
                CURRENT_POINTER_PATH.unlink(missing_ok=True)
            else:
                temporary_pointer = CURRENT_POINTER_PATH.with_suffix(".json.rollback.tmp")
                temporary_pointer.write_bytes(previous_pointer)
                os.replace(temporary_pointer, CURRENT_POINTER_PATH)
        raise
    print(
        "Recommendation models activated with rollback protection: "
        f"tfidf={SPRING_MODEL_DIR}, kure={kure_directory}, rollback={rollback_directory}"
    )
    return rollback_directory


def _write_bundle(
    frame: pd.DataFrame,
    embeddings_source: Path,
    provenance: str,
    document_version: int = DOCUMENT_VERSION,
) -> dict:
    embeddings = np.load(embeddings_source, mmap_mode="r")
    if embeddings.ndim != 2 or embeddings.shape[0] != len(frame):
        raise RuntimeError(
            f"Embedding shape {embeddings.shape} does not match restaurant count {len(frame)}."
        )

    now = datetime.now(timezone.utc)
    index_version = now.strftime("%Y%m%dT%H%M%SZ")
    final_directory = KURE_ROOT / index_version
    temporary_directory = KURE_ROOT / f".{index_version}.tmp"
    if final_directory.exists() or temporary_directory.exists():
        raise RuntimeError(f"KURE index version already exists: {index_version}")

    temporary_directory.mkdir(parents=True, exist_ok=False)
    try:
        embeddings_target = temporary_directory / "embeddings.npy"
        ids_target = temporary_directory / "restaurant_ids.npy"
        shutil.copy2(embeddings_source, embeddings_target)
        np.save(ids_target, frame["id"].to_numpy(dtype=np.int64))

        manifest = {
            "indexVersion": index_version,
            "modelName": EMBEDDING_MODEL_NAME,
            "modelRevision": _model_revision(),
            "dimension": int(embeddings.shape[1]),
            "embeddingDtype": str(embeddings.dtype),
            "restaurantCount": int(len(frame)),
            "restaurantIdHash": restaurant_id_hash(frame["id"].tolist()),
            "documentCorpusHash": document_corpus_hash(frame, document_version),
            "embeddingSha256": file_sha256(embeddings_target),
            "restaurantIdsSha256": file_sha256(ids_target),
            "documentVersion": document_version,
            "normalized": True,
            "builtAt": now.isoformat(),
            "provenance": provenance,
            "embeddingFilename": embeddings_target.name,
            "restaurantIdsFilename": ids_target.name,
        }
        _write_json_atomic(temporary_directory / "manifest.json", manifest)
        temporary_directory.rename(final_directory)
        print(f"KURE index bundle prepared: {final_directory}")
        return manifest
    except Exception:
        shutil.rmtree(temporary_directory, ignore_errors=True)
        raise


def migrate_legacy_index(frame: pd.DataFrame, document_version: int = DOCUMENT_VERSION) -> None:
    if document_version != 1:
        raise RuntimeError("The legacy embedding index can only be migrated as Document V1.")
    if not LEGACY_EMBEDDINGS_PATH.exists() or not LEGACY_META_PATH.exists():
        raise RuntimeError("Legacy restaurant_embeddings.npy/restaurants_meta.pkl files are required.")

    legacy_meta = pd.read_pickle(LEGACY_META_PATH)
    for column in TEXT_COLUMNS:
        legacy_meta[column] = legacy_meta[column].fillna("").astype(str)
    legacy_meta["id"] = pd.to_numeric(legacy_meta["id"], errors="raise").astype(np.int64)

    if not np.array_equal(legacy_meta["id"].to_numpy(), frame["id"].to_numpy()):
        raise RuntimeError("Legacy metadata restaurant IDs do not match the current MySQL dataset.")
    if document_corpus_hash(legacy_meta) != document_corpus_hash(frame):
        raise RuntimeError("Legacy metadata documents do not match the current MySQL dataset.")

    manifest = _write_bundle(
        frame,
        LEGACY_EMBEDDINGS_PATH,
        "legacy-migration-verified-metadata",
        document_version,
    )
    directory = KURE_ROOT / manifest["indexVersion"]
    verify_kure_bundle(directory, manifest, frame, document_version)
    activate_kure_bundle(directory, manifest, frame, document_version)


def build_kure_index(
    frame: pd.DataFrame,
    document_version: int = DOCUMENT_VERSION,
    activate: bool = True,
    batch_size: int = 32,
    device: str | None = None,
    inference_dtype: str = "auto",
) -> dict:
    from sentence_transformers import SentenceTransformer

    if frame.empty:
        raise RuntimeError("No public restaurants were returned from MySQL.")

    documents = canonical_documents(frame, document_version)
    print(f"Encoding {len(documents):,} restaurant documents with {EMBEDDING_MODEL_NAME}...")
    model_kwargs = {} if device in (None, "auto") else {"device": device}
    model = SentenceTransformer(EMBEDDING_MODEL_NAME, **model_kwargs)
    resolved_dtype = inference_dtype
    if resolved_dtype == "auto":
        resolved_dtype = "float16" if model.device.type == "cuda" else "float32"
    if resolved_dtype == "float16":
        if model.device.type != "cuda":
            raise RuntimeError("KURE float16 inference requires a CUDA device.")
        model.half()
    embeddings = model.encode(
        documents,
        batch_size=batch_size,
        show_progress_bar=True,
        normalize_embeddings=True,
    )

    temporary_embeddings = OUTPUT_DIR / ".kure-embeddings.tmp.npy"
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    np.save(temporary_embeddings, embeddings)
    try:
        manifest = _write_bundle(frame, temporary_embeddings, "full-rebuild", document_version)
    finally:
        temporary_embeddings.unlink(missing_ok=True)
    directory = KURE_ROOT / manifest["indexVersion"]
    verify_kure_bundle(directory, manifest, frame, document_version)
    if activate:
        activate_kure_bundle(directory, manifest, frame, document_version)
    return manifest


def _load_current_bundle() -> tuple[Path, dict]:
    if not CURRENT_POINTER_PATH.exists():
        raise RuntimeError("KURE current.json is missing.")
    pointer = json.loads(CURRENT_POINTER_PATH.read_text(encoding="utf-8"))
    index_version = pointer.get("indexVersion")
    if not index_version:
        raise RuntimeError("KURE current.json has no indexVersion.")
    directory = KURE_ROOT / str(index_version)
    manifest_path = directory / "manifest.json"
    if not manifest_path.exists():
        raise RuntimeError("KURE manifest.json is missing.")
    return directory, json.loads(manifest_path.read_text(encoding="utf-8"))


def verify_kure_index(
    frame: pd.DataFrame,
    document_version: int = DOCUMENT_VERSION,
) -> None:
    directory, manifest = _load_current_bundle()
    verify_kure_bundle(directory, manifest, frame, document_version)


def verify_kure_bundle(
    directory: Path,
    manifest: dict,
    frame: pd.DataFrame,
    document_version: int = DOCUMENT_VERSION,
) -> None:
    embeddings_path = directory / manifest["embeddingFilename"]
    ids_path = directory / manifest["restaurantIdsFilename"]
    if not embeddings_path.exists() or not ids_path.exists():
        raise RuntimeError("KURE bundle files are missing.")
    if int(manifest.get("documentVersion", -1)) != document_version:
        raise RuntimeError(
            f"KURE document version mismatch: {manifest.get('documentVersion')} != {document_version}"
        )

    ids = np.load(ids_path, mmap_mode="r")
    embeddings = np.load(embeddings_path, mmap_mode="r")
    expected_shape = (int(manifest["restaurantCount"]), int(manifest["dimension"]))
    if embeddings.shape != expected_shape:
        raise RuntimeError(f"KURE embedding shape mismatch: {embeddings.shape} != {expected_shape}")
    expected_dtype = manifest.get("embeddingDtype")
    if expected_dtype is not None and str(embeddings.dtype) != expected_dtype:
        raise RuntimeError(
            f"KURE embedding dtype mismatch: {embeddings.dtype} != {expected_dtype}"
        )
    if ids.shape != (expected_shape[0],):
        raise RuntimeError("KURE restaurant ID array shape mismatch.")
    if restaurant_id_hash(ids) != manifest["restaurantIdHash"]:
        raise RuntimeError("KURE restaurant ID manifest hash mismatch.")
    if file_sha256(ids_path) != manifest["restaurantIdsSha256"]:
        raise RuntimeError("KURE restaurant ID file checksum mismatch.")
    if file_sha256(embeddings_path) != manifest["embeddingSha256"]:
        raise RuntimeError("KURE embedding file checksum mismatch.")
    if restaurant_id_hash(frame["id"].tolist()) != manifest["restaurantIdHash"]:
        raise RuntimeError("KURE restaurant IDs do not match the current MySQL dataset.")
    if document_corpus_hash(frame, document_version) != manifest["documentCorpusHash"]:
        raise RuntimeError("KURE documents do not match the current MySQL dataset.")

    sample_positions = np.unique(np.linspace(0, len(ids) - 1, num=min(32, len(ids)), dtype=int))
    sample_norms = np.linalg.norm(np.asarray(embeddings[sample_positions]), axis=1)
    if not np.allclose(sample_norms, 1.0, atol=1e-3):
        raise RuntimeError("KURE embeddings are not L2-normalized.")
    print(
        "KURE index verified: "
        f"version={manifest['indexVersion']}, restaurants={manifest['restaurantCount']:,}, "
        f"dimension={manifest['dimension']}"
    )


def activate_kure_bundle(
    directory: Path,
    manifest: dict,
    frame: pd.DataFrame,
    document_version: int,
) -> None:
    verify_kure_bundle(directory, manifest, frame, document_version)
    previous_pointer = CURRENT_POINTER_PATH.read_bytes() if CURRENT_POINTER_PATH.exists() else None
    try:
        _write_json_atomic(CURRENT_POINTER_PATH, {"indexVersion": manifest["indexVersion"]})
        active_directory, active_manifest = _load_current_bundle()
        verify_kure_bundle(active_directory, active_manifest, frame, document_version)
    except Exception:
        if previous_pointer is None:
            CURRENT_POINTER_PATH.unlink(missing_ok=True)
        else:
            temporary = CURRENT_POINTER_PATH.with_suffix(".json.rollback.tmp")
            temporary.write_bytes(previous_pointer)
            os.replace(temporary, CURRENT_POINTER_PATH)
        raise
    print(f"KURE index bundle activated: {directory}")


def export_document_corpus(
    frame: pd.DataFrame,
    document_version: int,
    output_directory: Path = DOCUMENT_V2_DIR,
) -> dict:
    if document_version != 2:
        raise RuntimeError("Corpus export is reserved for the prepared Document V2 dataset.")
    output_directory.mkdir(parents=True, exist_ok=True)
    corpus_path = output_directory / "corpus.jsonl"
    temporary_corpus = corpus_path.with_suffix(corpus_path.suffix + ".tmp")
    documents = canonical_documents(frame, document_version)
    enriched_count = 0
    reviewed_count = 0
    official_menu_count = 0
    official_priced_menu_count = 0
    official_quality_count = 0
    source_codes: set[str] = set()
    with temporary_corpus.open("w", encoding="utf-8", newline="\n") as file_handle:
        for (_, row), document in zip(frame.iterrows(), documents):
            row_source_codes = [
                value.strip()
                for value in _normalized_text(row.get("source_codes")).split("|")
                if value.strip()
            ]
            if row_source_codes:
                enriched_count += 1
                source_codes.update(row_source_codes)
            review_count = int(row.get("review_count") or 0)
            if review_count > 0:
                reviewed_count += 1
            menu_count = row.get("menu_count")
            if menu_count is not None and not pd.isna(menu_count) and int(menu_count) > 0:
                official_menu_count += 1
            typical_menu_price = row.get("typical_menu_price")
            if typical_menu_price is not None and not pd.isna(typical_menu_price):
                official_priced_menu_count += 1
            has_official_score = any(
                row.get(column) is not None and not pd.isna(row.get(column))
                for column in ("rti_score", "naver_rating", "tripadvisor_rating", "ctrip_rating")
            )
            if _normalized_text(row.get("award_description")) or has_official_score:
                official_quality_count += 1
            file_handle.write(json.dumps({
                "publicRestaurantId": int(row["id"]),
                "documentVersion": document_version,
                "document": document,
                "evidenceSourceCodes": row_source_codes,
                "fooduckReviewCount": review_count,
            }, ensure_ascii=False, separators=(",", ":")) + "\n")
    os.replace(temporary_corpus, corpus_path)

    manifest = {
        "corpusVersion": 1,
        "documentVersion": document_version,
        "restaurantCount": int(len(frame)),
        "restaurantIdHash": restaurant_id_hash(frame["id"].tolist()),
        "documentCorpusHash": document_corpus_hash(frame, document_version),
        "corpusSha256": file_sha256(corpus_path),
        "enrichedRestaurantCount": enriched_count,
        "fooduckReviewedRestaurantCount": reviewed_count,
        "officialMenuRestaurantCount": official_menu_count,
        "officialPricedMenuRestaurantCount": official_priced_menu_count,
        "officialQualityRestaurantCount": official_quality_count,
        "evidenceSourceCodes": sorted(source_codes),
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "trainingPerformed": False,
        "activeKureIndexModified": False,
        "tfidfModelModified": False,
        "corpusFilename": corpus_path.name,
    }
    _write_json_atomic(output_directory / "manifest.json", manifest)
    print(
        "Document V2 corpus exported: "
        f"restaurants={len(frame):,}, enriched={enriched_count:,}, path={corpus_path}"
    )
    return manifest


def unique_model_staging_directory() -> Path:
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    staging_directory = OUTPUT_DIR / f".model-set-{stamp}.tmp"
    suffix = 1
    while staging_directory.exists():
        staging_directory = OUTPUT_DIR / f".model-set-{stamp}_{suffix:02d}.tmp"
        suffix += 1
    return staging_directory


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Build or verify FOODUCK recommendation indexes.")
    parser.add_argument("--verify", action="store_true", help="Verify the active KURE bundle against MySQL.")
    parser.add_argument("--migrate-existing", action="store_true", help="Migrate the current legacy NPY index.")
    parser.add_argument("--tfidf", action="store_true", help="Rebuild the Java TF-IDF fallback model.")
    parser.add_argument("--kure", action="store_true", help="Rebuild the complete KURE embedding index.")
    parser.add_argument("--all", action="store_true", help="Rebuild both TF-IDF and KURE indexes.")
    parser.add_argument(
        "--document-version",
        type=int,
        choices=(1, 2),
        default=DOCUMENT_VERSION,
        help="Canonical document version; defaults to the active V1 index.",
    )
    parser.add_argument(
        "--export-corpus",
        action="store_true",
        help="Export the prepared Document V2 JSONL corpus without model training.",
    )
    parser.add_argument(
        "--corpus-output",
        type=Path,
        default=DOCUMENT_V2_DIR,
        help="Output directory used with --export-corpus.",
    )
    parser.add_argument("--rebuild-embeddings", action="store_true", help=argparse.SUPPRESS)
    parser.add_argument(
        "--batch-size",
        type=int,
        default=32,
        help="KURE encoding batch size; lower this when GPU memory is limited.",
    )
    parser.add_argument(
        "--device",
        default="auto",
        help="SentenceTransformer device such as auto, cpu, cuda, or cuda:0.",
    )
    parser.add_argument(
        "--inference-dtype",
        choices=("auto", "float32", "float16"),
        default="auto",
        help="KURE inference dtype; auto uses float16 on CUDA and float32 otherwise.",
    )
    arguments = parser.parse_args(argv)

    if arguments.batch_size < 1:
        parser.error("--batch-size must be at least 1.")

    if arguments.rebuild_embeddings:
        arguments.all = True
    if not any((
        arguments.verify,
        arguments.migrate_existing,
        arguments.tfidf,
        arguments.kure,
        arguments.all,
        arguments.export_corpus,
    )):
        parser.print_help()
        return 2

    frame = fetch_restaurants_from_db(arguments.document_version)
    print(
        f"Loaded {len(frame):,} public restaurants from MySQL "
        f"for Document V{arguments.document_version}."
    )
    if arguments.migrate_existing:
        migrate_legacy_index(frame, arguments.document_version)
    if arguments.all:
        staging_directory = unique_model_staging_directory()
        tfidf_staging = staging_directory / "tfidf"
        staging_directory.mkdir(parents=True, exist_ok=False)
        try:
            build_tfidf_model(frame, arguments.document_version, tfidf_staging)
            kure_manifest = build_kure_index(
                frame,
                arguments.document_version,
                activate=False,
                batch_size=arguments.batch_size,
                device=arguments.device,
                inference_dtype=arguments.inference_dtype,
            )
            activate_prepared_models(
                tfidf_staging,
                frame,
                arguments.document_version,
                kure_manifest,
            )
        finally:
            shutil.rmtree(staging_directory, ignore_errors=True)
    elif arguments.tfidf:
        staging_directory = unique_model_staging_directory()
        staging_directory.mkdir(parents=True, exist_ok=False)
        try:
            tfidf_staging = staging_directory / "tfidf"
            build_tfidf_model(frame, arguments.document_version, tfidf_staging)
            activate_prepared_models(tfidf_staging, frame, arguments.document_version)
        finally:
            shutil.rmtree(staging_directory, ignore_errors=True)
    elif arguments.kure:
        build_kure_index(
            frame,
            arguments.document_version,
            activate=True,
            batch_size=arguments.batch_size,
            device=arguments.device,
            inference_dtype=arguments.inference_dtype,
        )
    if arguments.export_corpus:
        export_document_corpus(
            frame,
            arguments.document_version,
            arguments.corpus_output.resolve(),
        )
    if arguments.verify:
        verify_kure_index(frame, arguments.document_version)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"[ERROR] {error}")
        raise SystemExit(1)
