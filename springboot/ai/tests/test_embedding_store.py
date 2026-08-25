import tempfile
import unittest
from pathlib import Path
import sys

import numpy as np


AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

from embedding_store import (
    SHARDED_STORAGE_FORMAT,
    close_embedding_store,
    embedding_set_sha256,
    file_sha256,
    load_embedding_store,
    write_embedding_shards,
)


class EmbeddingStoreTest(unittest.TestCase):

    def test_writes_strictly_limited_shards_and_reads_across_boundaries(self):
        values = np.arange(30 * 64, dtype=np.float32).reshape(30, 64)
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            shards = write_embedding_shards(
                values,
                directory,
                storage_dtype="float16",
                max_shard_bytes=5_000,
            )
            manifest = {
                "embeddingStorage": SHARDED_STORAGE_FORMAT,
                "embeddingShards": shards,
                "embeddingSetSha256": embedding_set_sha256(shards),
                "embeddingDtype": "float16",
                "dimension": 64,
                "restaurantCount": 30,
                "maxArtifactBytes": 5_000,
            }

            store = load_embedding_store(directory, manifest)
            try:
                selected = np.asarray(store[np.asarray([29, 0, 8, 7, 29])])

                self.assertGreater(len(shards), 1)
                self.assertTrue(all(shard["byteSize"] < 5_000 for shard in shards))
                self.assertEqual((30, 64), store.shape)
                self.assertEqual(np.dtype("float16"), store.dtype)
                np.testing.assert_array_equal(
                    values[[29, 0, 8, 7, 29]].astype(np.float16), selected
                )
                np.testing.assert_array_equal(values[7].astype(np.float16), store[7])
            finally:
                close_embedding_store(store)

    def test_rejects_a_tampered_shard(self):
        values = np.ones((12, 64), dtype=np.float32)
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            shards = write_embedding_shards(
                values, directory, storage_dtype="float16", max_shard_bytes=5_000
            )
            manifest = {
                "embeddingStorage": SHARDED_STORAGE_FORMAT,
                "embeddingShards": shards,
                "embeddingSetSha256": embedding_set_sha256(shards),
                "embeddingDtype": "float16",
                "dimension": 64,
                "restaurantCount": 12,
                "maxArtifactBytes": 5_000,
            }
            with (directory / shards[0]["filename"]).open("ab") as file_handle:
                file_handle.write(b"tampered")

            with self.assertRaisesRegex(RuntimeError, "size is invalid"):
                load_embedding_store(directory, manifest)

    def test_loads_the_legacy_single_file_format(self):
        values = np.eye(4, dtype=np.float16)
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            path = directory / "embeddings.npy"
            np.save(path, values)
            manifest = {
                "embeddingFilename": path.name,
                "embeddingSha256": file_sha256(path),
            }

            loaded = load_embedding_store(directory, manifest)
            try:
                np.testing.assert_array_equal(values, np.asarray(loaded))
            finally:
                close_embedding_store(loaded)


if __name__ == "__main__":
    unittest.main()
