"""Deterministic atomic writes shared by repository evidence scripts."""

from __future__ import annotations

import json
from pathlib import Path


def write_text(path: Path, content: str) -> None:
    """Atomically replace one UTF-8 evidence file."""
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(path.suffix + ".tmp")
    temporary.write_text(content, encoding="utf-8")
    temporary.replace(path)


def write_json(path: Path, value: object) -> None:
    """Atomically write deterministic, human-readable JSON evidence."""
    write_text(
        path,
        json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
    )
