#!/usr/bin/env python3
"""Writes the GameTest arena structure (a stone floor with open air above) as a vanilla structure NBT."""
import gzip
import os
import struct

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data", "infinitygauntlet", "structure", "arena.nbt")
DATA_VERSION = 3955  # 1.21.1
SIZE = (16, 10, 28)


def tag_string(name):
    b = name.encode()
    return struct.pack(">H", len(b)) + b


def named(tag_id, name, payload):
    return bytes([tag_id]) + tag_string(name) + payload


def compound(entries):
    return b"".join(entries) + b"\x00"


def int_(v):
    return struct.pack(">i", v)


def list_(elem_id, items):
    return bytes([elem_id]) + struct.pack(">i", len(items)) + b"".join(items)


def main():
    palette = [compound([named(8, "Name", tag_string("minecraft:smooth_stone"))]),
               compound([named(8, "Name", tag_string("minecraft:air"))])]
    blocks = []
    for x in range(SIZE[0]):
        for y in range(SIZE[1]):
            for z in range(SIZE[2]):
                state = 0 if y == 0 else 1
                pos = list_(3, [int_(x), int_(y), int_(z)])
                blocks.append(compound([named(9, "pos", pos), named(3, "state", int_(state))]))
    root = compound([
        named(3, "DataVersion", int_(DATA_VERSION)),
        named(9, "size", list_(3, [int_(v) for v in SIZE])),
        named(9, "palette", list_(10, palette)),
        named(9, "blocks", list_(10, blocks)),
        named(9, "entities", list_(10, [])),
    ])
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with gzip.open(OUT, "wb") as f:
        f.write(named(10, "", root))
    print("wrote", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
