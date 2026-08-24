import json
import sys
from pathlib import Path

from build_embeddings import CURRENT_POINTER_PATH, KURE_ROOT, main


def with_active_document_version(arguments: list[str]) -> list[str]:
    values = list(arguments)
    has_explicit_version = any(
        value == "--document-version" or value.startswith("--document-version=")
        for value in values
    )
    if "--verify" not in values or has_explicit_version:
        return values

    pointer = json.loads(CURRENT_POINTER_PATH.read_text(encoding="utf-8"))
    index_version = pointer.get("indexVersion")
    if not index_version:
        raise RuntimeError("KURE current.json has no indexVersion.")
    manifest_path = Path(KURE_ROOT) / str(index_version) / "manifest.json"
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    document_version = int(manifest.get("documentVersion", -1))
    if document_version not in (1, 2):
        raise RuntimeError(f"Unsupported active KURE document version: {document_version}")
    return values + ["--document-version", str(document_version)]


if __name__ == "__main__":
    try:
        raise SystemExit(main(with_active_document_version(sys.argv[1:])))
    except Exception as error:
        print(f"[ERROR] {error}")
        raise SystemExit(1)
