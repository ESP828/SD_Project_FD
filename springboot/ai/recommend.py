import hashlib
import json
import os
import threading
from pathlib import Path

import numpy as np


BASE_DIR = Path(__file__).resolve().parent
KURE_ROOT = BASE_DIR / "model" / "kure"
CURRENT_POINTER_PATH = KURE_ROOT / "current.json"
EMBEDDING_MODEL_NAME = "nlpai-lab/KURE-v1"


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


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


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
        manifest_path = index_directory / "manifest.json"
        if not manifest_path.exists():
            raise KureServiceError("KURE_INDEX_NOT_READY", "KURE manifest.json is missing.")
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        embeddings_path = index_directory / manifest["embeddingFilename"]
        restaurant_ids_path = index_directory / manifest["restaurantIdsFilename"]
        if not embeddings_path.exists() or not restaurant_ids_path.exists():
            raise KureServiceError("KURE_INDEX_NOT_READY", "KURE bundle files are missing.")

        embeddings = np.load(embeddings_path, mmap_mode="r")
        restaurant_ids = np.load(restaurant_ids_path, mmap_mode="r")
        expected_shape = (int(manifest["restaurantCount"]), int(manifest["dimension"]))
        if embeddings.shape != expected_shape or restaurant_ids.shape != (expected_shape[0],):
            raise KureServiceError("KURE_INDEX_MISMATCH", "KURE bundle shape is invalid.")
        if _restaurant_id_hash(restaurant_ids) != manifest["restaurantIdHash"]:
            raise KureServiceError("KURE_INDEX_MISMATCH", "KURE restaurant ID hash is invalid.")
        if _sha256_file(restaurant_ids_path) != manifest["restaurantIdsSha256"]:
            raise KureServiceError("KURE_INDEX_MISMATCH", "KURE restaurant ID checksum is invalid.")
        if _sha256_file(embeddings_path) != manifest["embeddingSha256"]:
            raise KureServiceError("KURE_INDEX_MISMATCH", "KURE embedding checksum is invalid.")

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


def _result(manifest: dict, items: list[dict]) -> dict:
    return {
        "engine": "KURE",
        "modelName": manifest.get("modelName", EMBEDDING_MODEL_NAME),
        "indexVersion": manifest.get("indexVersion"),
        "documentVersion": manifest.get("documentVersion"),
        "items": items,
    }
