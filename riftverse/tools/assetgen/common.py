"""Shared paths and small helpers for the asset generator."""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RESOURCES = ROOT / "src" / "main" / "resources"
ASSETS = RESOURCES / "assets" / "riftverse"
DATA = RESOURCES / "data"
MODID = "riftverse"


def rl(path: str) -> str:
    return f"{MODID}:{path}"


def write_json(path: Path, obj) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def rgb(hex_color: int):
    return ((hex_color >> 16) & 255) / 255.0, ((hex_color >> 8) & 255) / 255.0, (hex_color & 255) / 255.0
