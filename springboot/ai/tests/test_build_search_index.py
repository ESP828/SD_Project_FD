import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

import build_search_index  # noqa: E402


class BuildSearchIndexArgumentsTest(unittest.TestCase):

    def test_verify_uses_the_active_manifest_document_version(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pointer = root / "current.json"
            bundle = root / "kure" / "v2-index"
            bundle.mkdir(parents=True)
            pointer.write_text('{"indexVersion":"v2-index"}', encoding="utf-8")
            (bundle / "manifest.json").write_text(
                json.dumps({"documentVersion": 2}), encoding="utf-8"
            )

            with patch.multiple(
                build_search_index,
                CURRENT_POINTER_PATH=pointer,
                KURE_ROOT=root / "kure",
            ):
                arguments = build_search_index.with_active_document_version(["--verify"])

        self.assertEqual(["--verify", "--document-version", "2"], arguments)

    def test_explicit_document_version_is_preserved(self):
        arguments = build_search_index.with_active_document_version(
            ["--verify", "--document-version", "1"]
        )

        self.assertEqual(["--verify", "--document-version", "1"], arguments)

    def test_verify_reads_the_manifest_filename_from_the_pointer(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            pointer = root / "current.json"
            bundle = root / "kure" / "sharded-index"
            bundle.mkdir(parents=True)
            pointer.write_text(
                json.dumps({
                    "indexVersion": "sharded-index",
                    "manifestFilename": "manifest-sharded.json",
                }),
                encoding="utf-8",
            )
            (bundle / "manifest-sharded.json").write_text(
                json.dumps({"documentVersion": 2}), encoding="utf-8"
            )

            with patch.multiple(
                build_search_index,
                CURRENT_POINTER_PATH=pointer,
                KURE_ROOT=root / "kure",
            ):
                arguments = build_search_index.with_active_document_version(["--verify"])

        self.assertEqual(["--verify", "--document-version", "2"], arguments)


if __name__ == "__main__":
    unittest.main()
