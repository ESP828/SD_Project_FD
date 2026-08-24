import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import pandas as pd


AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

import build_embeddings as build  # noqa: E402


class ModelActivationTest(unittest.TestCase):

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.active = self.root / "active"
        self.prepared = self.root / "prepared"
        self.rollback = self.root / "rollback"
        self.pointer = self.root / "current.json"
        self.active.mkdir()
        self.prepared.mkdir()
        self.frame = pd.DataFrame([{
            "id": 1,
            "name": "테스트 식당",
            "category_large_name": "음식",
            "category_medium_name": "한식",
            "category_small_name": "한식 일반",
            "road_address": "서울특별시 강남구 테스트로 1",
            "lot_address": "",
            "latitude": 37.5,
            "longitude": 127.0,
        }])
        metadata = {
            "modelVersion": "prepared",
            "vocabularySize": 1,
            "totalDocuments": 1,
            "documentVersion": 1,
            "documentCorpusHash": build.document_corpus_hash(self.frame, 1),
        }
        (self.prepared / "vocabulary.json").write_text('{"테스트":0}', encoding="utf-8")
        (self.prepared / "idf.json").write_text("[1.0]", encoding="utf-8")
        (self.prepared / "model-meta.json").write_text(
            json.dumps(metadata, ensure_ascii=False), encoding="utf-8"
        )
        for name in build.TFIDF_FILENAMES:
            (self.active / name).write_text(f"old-{name}", encoding="utf-8")

    def tearDown(self):
        self.temporary.cleanup()

    def globals_patch(self):
        return patch.multiple(
            build,
            SPRING_MODEL_DIR=self.active,
            TFIDF_ROLLBACK_ROOT=self.rollback,
            CURRENT_POINTER_PATH=self.pointer,
        )

    def test_activation_replaces_all_files_and_preserves_a_rollback(self):
        with self.globals_patch():
            rollback_directory = build.activate_prepared_models(
                self.prepared, self.frame, 1
            )

        self.assertIsNotNone(rollback_directory)
        self.assertEqual({"테스트": 0}, json.loads(
            (self.active / "vocabulary.json").read_text(encoding="utf-8")
        ))
        self.assertTrue((rollback_directory / "rollback.json").is_file())
        self.assertEqual(
            "old-vocabulary.json",
            (rollback_directory / "vocabulary.json").read_text(encoding="utf-8"),
        )

    def test_activation_restores_all_active_files_when_post_check_fails(self):
        old_payloads = {
            name: (self.active / name).read_bytes()
            for name in build.TFIDF_FILENAMES
        }
        prepared_metadata = json.loads(
            (self.prepared / "model-meta.json").read_text(encoding="utf-8")
        )
        with self.globals_patch(), patch.object(
            build,
            "verify_tfidf_artifacts",
            side_effect=[prepared_metadata, RuntimeError("post-activation failure")],
        ):
            with self.assertRaisesRegex(RuntimeError, "post-activation failure"):
                build.activate_prepared_models(self.prepared, self.frame, 1)

        for name, expected in old_payloads.items():
            self.assertEqual(expected, (self.active / name).read_bytes())


if __name__ == "__main__":
    unittest.main()
