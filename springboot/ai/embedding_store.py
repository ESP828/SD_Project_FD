import hashlib
from pathlib import Path

import numpy as np


DEFAULT_MAX_SHARD_BYTES = 90_000_000
NPY_HEADER_RESERVE_BYTES = 4_096
SHARDED_STORAGE_FORMAT = "sharded-npy-v1"
SHARDED_MANIFEST_FILENAME = "manifest-sharded.json"
SHARDED_RESTAURANT_IDS_FILENAME = "restaurant_ids-sharded.npy"


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as file_handle:
        for chunk in iter(lambda: file_handle.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def embedding_set_sha256(shards: list[dict]) -> str:
    digest = hashlib.sha256()
    for shard in shards:
        digest.update(
            (
                f"{shard['filename']}\t{shard['rowStart']}\t{shard['rowEnd']}\t"
                f"{shard['byteSize']}\t{shard['sha256']}\n"
            ).encode("utf-8")
        )
    return digest.hexdigest()


def write_embedding_shards(
    embeddings,
    output_directory: Path,
    storage_dtype: str = "float16",
    max_shard_bytes: int = DEFAULT_MAX_SHARD_BYTES,
) -> list[dict]:
    if embeddings.ndim != 2:
        raise RuntimeError(f"KURE embeddings must be 2D, got shape {embeddings.shape}.")

    dtype = np.dtype(storage_dtype)
    row_bytes = int(embeddings.shape[1]) * dtype.itemsize
    if max_shard_bytes <= NPY_HEADER_RESERVE_BYTES + row_bytes:
        raise RuntimeError(
            "KURE max shard size is too small for one embedding row and the NPY header."
        )

    # Reserve enough room for the NPY header, then verify the actual file size after writing.
    rows_per_shard = (max_shard_bytes - NPY_HEADER_RESERVE_BYTES - 1) // row_bytes
    if rows_per_shard < 1:
        raise RuntimeError("KURE shard calculation produced no writable rows.")

    output_directory.mkdir(parents=True, exist_ok=True)
    shards: list[dict] = []
    total_rows = int(embeddings.shape[0])
    for part, row_start in enumerate(range(0, total_rows, rows_per_shard)):
        row_end = min(row_start + rows_per_shard, total_rows)
        filename = f"embeddings-part-{part:03d}.npy"
        path = output_directory / filename
        values = np.asarray(embeddings[row_start:row_end], dtype=dtype)
        np.save(path, values)
        byte_size = path.stat().st_size
        if byte_size >= max_shard_bytes:
            raise RuntimeError(
                f"KURE shard {filename} is {byte_size:,} bytes; "
                f"the strict limit is {max_shard_bytes:,} bytes."
            )
        shards.append({
            "filename": filename,
            "rowStart": row_start,
            "rowEnd": row_end,
            "byteSize": byte_size,
            "sha256": file_sha256(path),
        })

    if not shards and total_rows > 0:
        raise RuntimeError("KURE shard writer did not create any files.")
    return shards


class ShardedEmbeddingStore:
    def __init__(self, shards: list[tuple[int, int, np.ndarray]]):
        if not shards:
            raise RuntimeError("KURE embedding shard list is empty.")
        self._shards = tuple(shards)
        self.shape = (shards[-1][1], int(shards[0][2].shape[1]))
        self.dtype = shards[0][2].dtype

    def __len__(self) -> int:
        return self.shape[0]

    def close(self) -> None:
        for _, _, shard in self._shards:
            memory_map = getattr(shard, "_mmap", None)
            if memory_map is not None:
                memory_map.close()

    def __getitem__(self, key):
        if isinstance(key, slice):
            indexes = np.arange(*key.indices(self.shape[0]), dtype=np.int64)
            return self._take(indexes)

        indexes = np.asarray(key)
        scalar = indexes.ndim == 0
        if scalar:
            indexes = indexes.reshape(1)
        if not np.issubdtype(indexes.dtype, np.integer):
            raise IndexError("KURE embedding indexes must be integers.")

        original_shape = indexes.shape
        result = self._take(indexes.astype(np.int64, copy=False).reshape(-1))
        if scalar:
            return result[0]
        return result.reshape(original_shape + (self.shape[1],))

    def _take(self, indexes: np.ndarray) -> np.ndarray:
        if indexes.size == 0:
            return np.empty((0, self.shape[1]), dtype=self.dtype)
        if np.any(indexes < 0) or np.any(indexes >= self.shape[0]):
            raise IndexError("KURE embedding index is out of bounds.")

        result = np.empty((indexes.size, self.shape[1]), dtype=self.dtype)
        filled = np.zeros(indexes.size, dtype=bool)
        for row_start, row_end, shard in self._shards:
            positions = np.flatnonzero((indexes >= row_start) & (indexes < row_end))
            if positions.size == 0:
                continue
            local_indexes = indexes[positions] - row_start
            result[positions] = shard[local_indexes]
            filled[positions] = True
        if not np.all(filled):
            raise IndexError("KURE embedding shard coverage is incomplete.")
        return result


def close_embedding_store(store) -> None:
    close = getattr(store, "close", None)
    if callable(close):
        close()
        return
    memory_map = getattr(store, "_mmap", None)
    if memory_map is not None:
        memory_map.close()


def _safe_bundle_path(directory: Path, filename: str) -> Path:
    candidate = Path(filename)
    if candidate.is_absolute() or candidate.name != filename:
        raise RuntimeError(f"Unsafe KURE bundle filename: {filename}")
    return directory / filename


def load_embedding_store(
    directory: Path,
    manifest: dict,
    verify_checksums: bool = True,
):
    shard_metadata = manifest.get("embeddingShards")
    if not shard_metadata:
        filename = manifest.get("embeddingFilename")
        if not filename:
            raise RuntimeError("KURE manifest has no embedding storage metadata.")
        path = _safe_bundle_path(directory, filename)
        if not path.is_file():
            raise RuntimeError(f"KURE embedding file is missing: {filename}")
        if verify_checksums and file_sha256(path) != manifest.get("embeddingSha256"):
            raise RuntimeError("KURE embedding checksum is invalid.")
        return np.load(path, mmap_mode="r")

    if manifest.get("embeddingStorage") != SHARDED_STORAGE_FORMAT:
        raise RuntimeError("KURE sharded embedding storage format is unsupported.")

    expected_dimension = int(manifest["dimension"])
    expected_dtype = np.dtype(manifest["embeddingDtype"])
    strict_limit = int(manifest.get("maxArtifactBytes", 0))
    loaded_shards: list[tuple[int, int, np.ndarray]] = []
    next_row = 0
    for metadata in shard_metadata:
        filename = str(metadata["filename"])
        row_start = int(metadata["rowStart"])
        row_end = int(metadata["rowEnd"])
        if row_start != next_row or row_end <= row_start:
            raise RuntimeError("KURE embedding shard row ranges are not contiguous.")

        path = _safe_bundle_path(directory, filename)
        if not path.is_file():
            raise RuntimeError(f"KURE embedding shard is missing: {filename}")
        byte_size = path.stat().st_size
        if byte_size != int(metadata["byteSize"]):
            raise RuntimeError(f"KURE embedding shard size is invalid: {filename}")
        if strict_limit > 0 and byte_size >= strict_limit:
            raise RuntimeError(f"KURE embedding shard exceeds the Git size limit: {filename}")
        if verify_checksums and file_sha256(path) != metadata["sha256"]:
            raise RuntimeError(f"KURE embedding shard checksum is invalid: {filename}")

        shard = np.load(path, mmap_mode="r")
        expected_shape = (row_end - row_start, expected_dimension)
        if shard.shape != expected_shape or shard.dtype != expected_dtype:
            raise RuntimeError(f"KURE embedding shard shape or dtype is invalid: {filename}")
        loaded_shards.append((row_start, row_end, shard))
        next_row = row_end

    if next_row != int(manifest["restaurantCount"]):
        raise RuntimeError("KURE embedding shards do not cover every restaurant row.")
    if embedding_set_sha256(shard_metadata) != manifest.get("embeddingSetSha256"):
        raise RuntimeError("KURE embedding shard set checksum is invalid.")
    return ShardedEmbeddingStore(loaded_shards)
