#!/usr/bin/env python3
"""Regenerates every Riftverse texture, sound and JSON resource.

    pip install -r tools/requirements.txt
    python3 tools/generate_assets.py            # everything
    python3 tools/generate_assets.py --no-sound # skip the (slow) audio synthesis

Output is deterministic, so re-running only changes files whose generator changed.
"""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from assetgen import client_json, data_json, textures  # noqa: E402


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--no-sound", action="store_true", help="skip audio synthesis")
    parser.add_argument("--sheet", metavar="PNG", help="also write a contact sheet of all textures for review")
    args = parser.parse_args()

    print("textures...")
    preview = textures.generate()
    if args.sheet:
        textures.contact_sheet(preview, args.sheet)
    print(f"  {len(preview)} textures")

    print("client json...")
    client_json.generate(textures.GLOW_BLOCKS)
    print("data json...")
    data_json.generate()

    if not args.no_sound:
        from assetgen import sounds
        print("sounds...")
        sounds.generate()
    print("done")


if __name__ == "__main__":
    main()
