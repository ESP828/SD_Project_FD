import hashlib
import json
import os
import threading
from pathlib import Path

import numpy as np

from embedding_store import file_sha256, load_embedding_store


BASE_DIR = Path(__file__).resolve().parent
KURE_ROOT = BASE_DIR / "model" / "kure"
CURRENT_POINTER_PATH = KURE_ROOT / "current.json"
EMBEDDING_MODEL_NAME = "nlpai-lab/KURE-v1"

# 싫다고 표시한 매장과 닮은 후보를 얼마나 깎을지. 긍정/부정 매장이 같은 상권에서 나오면
# 두 프로필이 서로 닮아 상쇄되므로, 인수인계서의 0.45보다 낮은 값에서 시작한다.
NEGATIVE_PROFILE_PENALTY = 0.30


class KureServiceError(RuntimeError):
    def __init__(self, code: str, message: str, status_code: int = 503):
        super().__init__(message)
        self.code = code
        self.message = message
        self.status_code = status_code


_state_lock = threading.RLock()
_encode_lock = threading.Lock()
_state = {
    "status": "NOT_STARTED",
    "reason": None,
    "message": None,
    "modelLoaded": False,
    "indexLoaded": False,
}
_model = None
_embeddings = None
_restaurant_ids = None
_id_to_index: dict[int, int] = {}
_manifest: dict = {}


def _set_state(**values) -> None:
    with _state_lock:
        _state.update(values)


def _restaurant_id_hash(ids) -> str:
    payload = ",".join(str(int(value)) for value in ids).encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


def initialize() -> None:
    global _model, _embeddings, _restaurant_ids, _id_to_index, _manifest

    with _state_lock:
        if _state["status"] in {"LOADING", "READY"}:
            return
        _state.update(
            status="LOADING",
            reason=None,
            message=None,
            modelLoaded=False,
            indexLoaded=False,
        )

    try:
        if os.environ.get("FOODUCK_KURE_INDEX_COMPATIBLE", "true").lower() != "true":
            raise KureServiceError(
                "KURE_INDEX_MISMATCH",
                "The active KURE index does not match the current MySQL dataset.",
            )

        if not CURRENT_POINTER_PATH.exists():
            raise KureServiceError("KURE_INDEX_NOT_READY", "KURE current.json is missing.")
        pointer = json.loads(CURRENT_POINTER_PATH.read_text(encoding="utf-8"))
        index_version = pointer.get("indexVersion")
        if not index_version:
            raise KureServiceError("KURE_INDEX_NOT_READY", "KURE current.json is invalid.")

        index_directory = KURE_ROOT / str(index_version)
        manifest_filename = pointer.get("manifestFilename", "manifest.json")
        manifest_path = index_directory / str(manifest_filename)
        if not manifest_path.exists():
            raise KureServiceError(
                "KURE_INDEX_NOT_READY", f"KURE {manifest_filename} is missing."
            )
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        restaurant_ids_path = index_directory / manifest["restaurantIdsFilename"]
        if not restaurant_ids_path.exists():
            raise KureServiceError("KURE_INDEX_NOT_READY", "KURE bundle files are missing.")

        try:
            embeddings = load_embedding_store(
                index_directory, manifest, verify_checksums=True
            )
        except RuntimeError as error:
            raise KureServiceError("KURE_INDEX_MISMATCH", str(error)) from error
        restaurant_ids = np.load(restaurant_ids_path, mmap_mode="r")
        expected_shape = (int(manifest["restaurantCount"]), int(manifest["dimension"]))
        if embeddings.shape != expected_shape or restaurant_ids.shape != (expected_shape[0],):
            raise KureServiceError("KURE_INDEX_MISMATCH", "KURE bundle shape is invalid.")
        if _restaurant_id_hash(restaurant_ids) != manifest["restaurantIdHash"]:
            raise KureServiceError("KURE_INDEX_MISMATCH", "KURE restaurant ID hash is invalid.")
        if file_sha256(restaurant_ids_path) != manifest["restaurantIdsSha256"]:
            raise KureServiceError("KURE_INDEX_MISMATCH", "KURE restaurant ID checksum is invalid.")

        id_to_index = {int(restaurant_id): index for index, restaurant_id in enumerate(restaurant_ids)}
        _embeddings = embeddings
        _restaurant_ids = restaurant_ids
        _id_to_index = id_to_index
        _manifest = manifest
        _set_state(indexLoaded=True)

        from sentence_transformers import SentenceTransformer

        model = SentenceTransformer(EMBEDDING_MODEL_NAME)
        _model = model
        _set_state(status="READY", modelLoaded=True, reason=None, message=None)
    except KureServiceError as error:
        _set_state(status="FAILED", reason=error.code, message=error.message)
    except Exception as error:
        _set_state(
            status="FAILED",
            reason="KURE_MODEL_NOT_LOADED" if _state["indexLoaded"] else "KURE_INDEX_NOT_READY",
            message=str(error),
        )


def health() -> dict:
    with _state_lock:
        state = dict(_state)
        manifest = dict(_manifest)
    return {
        "ready": state["status"] == "READY",
        "status": state["status"],
        "reason": state["reason"],
        "modelLoaded": state["modelLoaded"],
        "indexLoaded": state["indexLoaded"],
        "modelName": manifest.get("modelName", EMBEDDING_MODEL_NAME),
        "modelRevision": manifest.get("modelRevision"),
        "dimension": manifest.get("dimension"),
        "restaurantCount": manifest.get("restaurantCount"),
        "restaurantIdHash": manifest.get("restaurantIdHash"),
        "documentCorpusHash": manifest.get("documentCorpusHash"),
        "indexVersion": manifest.get("indexVersion"),
        "documentVersion": manifest.get("documentVersion"),
    }


def _ready_snapshot():
    with _state_lock:
        status = _state["status"]
        reason = _state["reason"]
        message = _state["message"]
        model = _model
        embeddings = _embeddings
        id_to_index = _id_to_index
        manifest = dict(_manifest)
    if status != "READY" or model is None or embeddings is None:
        code = reason or "KURE_INDEX_NOT_READY"
        raise KureServiceError(code, message or "KURE is not ready.")
    return model, embeddings, id_to_index, manifest


def _candidate_indices(restaurant_ids: list[int], id_to_index: dict[int, int]) -> np.ndarray:
    unique_ids = list(dict.fromkeys(int(value) for value in restaurant_ids))
    missing_ids = [restaurant_id for restaurant_id in unique_ids if restaurant_id not in id_to_index]
    if missing_ids:
        preview = ", ".join(str(value) for value in missing_ids[:5])
        raise KureServiceError(
            "KURE_CANDIDATE_SET_MISMATCH",
            f"Candidate restaurant IDs are absent from the KURE index: {preview}",
        )
    return np.asarray([id_to_index[restaurant_id] for restaurant_id in unique_ids], dtype=np.int64)


def search(query: str, restaurant_ids: list[int], top_k: int) -> dict:
    model, embeddings, id_to_index, manifest = _ready_snapshot()
    query_text = query.strip()
    if not query_text:
        raise KureServiceError("KURE_INVALID_QUERY", "Query must not be blank.", status_code=422)
    if not restaurant_ids:
        return _result(manifest, [])

    unique_ids = list(dict.fromkeys(int(value) for value in restaurant_ids))
    indices = _candidate_indices(unique_ids, id_to_index)
    with _encode_lock:
        query_vector = model.encode([query_text], normalize_embeddings=True)
    candidate_embeddings = np.asarray(embeddings[indices])
    similarities = np.dot(candidate_embeddings, query_vector.T).flatten()
    limit = min(max(int(top_k), 1), len(unique_ids))
    order = np.argsort(similarities)[::-1][:limit]
    items = [
        {"id": unique_ids[int(position)], "score": float(similarities[int(position)])}
        for position in order
    ]
    return _result(manifest, items)


def score_favorites(favorite_restaurant_ids: list[int], candidate_restaurant_ids: list[int]) -> dict:
    _, embeddings, id_to_index, manifest = _ready_snapshot()
    favorite_ids = list(dict.fromkeys(int(value) for value in favorite_restaurant_ids))
    candidate_ids = list(dict.fromkeys(int(value) for value in candidate_restaurant_ids))
    if not favorite_ids or not candidate_ids:
        return _result(manifest, [])

    favorite_indices = _candidate_indices(favorite_ids, id_to_index)
    candidate_indices = _candidate_indices(candidate_ids, id_to_index)
    profile_vector = np.asarray(embeddings[favorite_indices], dtype=np.float32).mean(axis=0)
    norm = float(np.linalg.norm(profile_vector))
    if norm <= 1e-12:
        raise KureServiceError("KURE_PROFILE_NOT_READY", "Favorite profile vector is empty.")
    profile_vector /= norm
    similarities = np.dot(np.asarray(embeddings[candidate_indices]), profile_vector).flatten()
    items = [
        {"id": restaurant_id, "score": float(score)}
        for restaurant_id, score in zip(candidate_ids, similarities)
    ]
    return _result(manifest, items)


def _weighted_profile(embeddings, id_to_index: dict[int, int], signals) -> np.ndarray | None:
    """가중치를 준 매장 임베딩의 평균. 신호 매장이 인덱스에 없으면 조용히 건너뛴다.

    후보 ID와 달리 신호 ID는 엄격하게 다루지 않는다. 사용자가 찜한 매장 하나가
    인덱스 재빌드 전이라는 이유로 개인화 전체를 TF-IDF로 떨어뜨릴 이유가 없다.
    """
    indices: list[int] = []
    weights: list[float] = []
    for signal in signals or []:
        restaurant_id = int(signal["restaurantId"])
        weight = float(signal["weight"])
        index = id_to_index.get(restaurant_id)
        if index is None or weight <= 0.0:
            continue
        indices.append(index)
        weights.append(weight)
    if not indices:
        return None

    vectors = np.asarray(embeddings[np.asarray(indices, dtype=np.int64)], dtype=np.float32)
    weight_column = np.asarray(weights, dtype=np.float32).reshape(-1, 1)
    profile_vector = (vectors * weight_column).sum(axis=0) / float(weight_column.sum())
    norm = float(np.linalg.norm(profile_vector))
    if norm <= 1e-12:
        return None
    return profile_vector / norm


def score_profile(
    positive_signals: list[dict],
    negative_signals: list[dict],
    candidate_restaurant_ids: list[int],
) -> dict:
    """찜과 평점을 가중 평균한 개인 취향 프로필로 후보 점수를 매긴다.

    긍정 프로필과의 유사도에서 부정 프로필과의 유사도를 일정 비율만큼 뺀다.
    좋아한 것과 닮았더라도 싫어한 것과 더 닮았다면 위로 올라오지 않는다.
    """
    _, embeddings, id_to_index, manifest = _ready_snapshot()
    candidate_ids = list(dict.fromkeys(int(value) for value in candidate_restaurant_ids))
    if not candidate_ids:
        return _result(manifest, [])

    positive_profile = _weighted_profile(embeddings, id_to_index, positive_signals)
    if positive_profile is None:
        raise KureServiceError(
            "KURE_PROFILE_NOT_READY",
            "Positive preference profile is empty.",
        )
    negative_profile = _weighted_profile(embeddings, id_to_index, negative_signals)

    candidate_indices = _candidate_indices(candidate_ids, id_to_index)
    candidate_embeddings = np.asarray(embeddings[candidate_indices])
    similarities = np.dot(candidate_embeddings, positive_profile).flatten()
    if negative_profile is not None:
        penalties = np.dot(candidate_embeddings, negative_profile).flatten()
        similarities = similarities - NEGATIVE_PROFILE_PENALTY * penalties

    items = [
        {"id": restaurant_id, "score": float(score)}
        for restaurant_id, score in zip(candidate_ids, similarities)
    ]
    return _result(manifest, items)


def _result(manifest: dict, items: list[dict]) -> dict:
    return {
        "engine": "KURE",
        "modelName": manifest.get("modelName", EMBEDDING_MODEL_NAME),
        "indexVersion": manifest.get("indexVersion"),
        "documentVersion": manifest.get("documentVersion"),
        "items": items,
    }
