#!/usr/bin/env python3
"""Generates the Star Platinum Stand: GeckoLib geometry, painted box-UV texture, glow mask and animations,
plus the Awakening Arrowhead item texture. Pure standard library.

    python3 tools/generate_stand.py
"""
import json
import math
import os
import random

from generate_textures import write_png

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "infinitemultiverse")
NAME = "star_platinum"
TEX = 128

SKIN = (0x6E, 0x62, 0xC8)
SKIN_DARK = (0x45, 0x38, 0x8E)
GOLD = (0xE8, 0xB8, 0x3A)
GOLD_DARK = (0x9A, 0x6E, 0x1C)
GAUNTLET = (0x2E, 0x22, 0x5C)
HAIR = (0x22, 0x14, 0x3A)
SCARF = (0xC2, 0x32, 0x4A)
EYE = (0x7F, 0xF6, 0xFF)
STAR = (0xFF, 0xE2, 0x7A)

# bone name -> (parent, pivot)
BONES = {
    "root": (None, [0, 0, 0]),
    "waist": ("root", [0, 12, 0]),
    "chest": ("waist", [0, 18, 0]),
    "head": ("chest", [0, 27, 0]),
    "scarf": ("chest", [0, 25, 3.5]),
    "right_arm": ("chest", [-7, 24, 0]),
    "right_forearm": ("right_arm", [-7, 18, 0]),
    "right_fist": ("right_forearm", [-7, 12, 0]),
    "left_arm": ("chest", [7, 24, 0]),
    "left_forearm": ("left_arm", [7, 18, 0]),
    "left_fist": ("left_forearm", [7, 12, 0]),
    "right_leg": ("waist", [-2.5, 12, 0]),
    "left_leg": ("waist", [2.5, 12, 0]),
}

# (bone, part, origin, size) -- model faces -Z; right side is -X.
CUBES = [
    ("head", "head", [-4, 27, -4], [8, 8, 8]),
    ("head", "hair_band", [-4.5, 32, -4.5], [9, 2, 9]),
    ("head", "hair_back", [-5, 22, 4], [10, 12, 3]),
    ("head", "hair_top", [-4.5, 34, -4], [9, 2, 9]),
    ("head", "hair_side_r", [-5, 26, -3], [1, 8, 7]),
    ("head", "hair_side_l", [4, 26, -3], [1, 8, 7]),
    ("head", "crest", [-1, 35, -2], [2, 3, 4]),
    ("chest", "chest", [-5.5, 18, -3.5], [11, 8, 7]),
    ("chest", "neck", [-1.5, 26, -1.5], [3, 1, 3]),
    ("waist", "abdomen", [-4, 12, -2.5], [8, 6, 5]),
    ("waist", "belt", [-4.5, 12, -3], [9, 2, 6]),
    ("scarf", "scarf", [-4, 15, 3.5], [8, 10, 1]),
    ("right_arm", "shoulder", [-10, 23, -3.5], [4, 3, 7]),
    ("right_arm", "upper_arm", [-9, 18, -2], [4, 7, 4]),
    ("right_forearm", "gauntlet", [-9.5, 12, -2.5], [5, 6, 5]),
    ("right_fist", "fist", [-9.5, 8, -2.5], [5, 4, 5]),
    ("right_leg", "thigh", [-4.5, 6, -2], [4, 6, 4]),
    ("right_leg", "shin", [-4, 1, -1.5], [3, 5, 3]),
]
for bone, part, (x, y, z), (w, h, d) in list(CUBES):
    if bone.startswith("right_"):
        CUBES.append((bone.replace("right_", "left_"), part, [-(x + w), y, z], [w, h, d]))


def box_uv_size(size):
    w, h, d = (int(math.ceil(v)) for v in size)
    return 2 * (w + d), d + h


def pack():
    order = sorted(range(len(CUBES)), key=lambda i: -box_uv_size(CUBES[i][3])[1])
    uvs, x, y, row_h = {}, 0, 0, 0
    for i in order:
        uw, uh = box_uv_size(CUBES[i][3])
        if x + uw > TEX:
            x, y, row_h = 0, y + row_h, 0
        uvs[i] = (x, y)
        x += uw
        row_h = max(row_h, uh)
    assert y + row_h <= TEX, "texture atlas overflow"
    return uvs


def faces(size, uv):
    """Box-UV face rectangles: name -> (x, y, w, h)."""
    w, h, d = (int(math.ceil(v)) for v in size)
    u, v = uv
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


# ---- painting ----

pixels = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
glow = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
rng = random.Random(7)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def put(x, y, c, a=255, glowing=False):
    if 0 <= x < TEX and 0 <= y < TEX:
        pixels[y][x] = (c[0], c[1], c[2], a)
        if glowing:
            glow[y][x] = (c[0], c[1], c[2], 255)


FACE_LIGHT = {"up": 1.15, "down": 0.65, "north": 1.0, "south": 0.78, "east": 0.86, "west": 0.86}


def paint_face(rect, face, base, accent, part, alpha_fn=None):
    fx, fy, fw, fh = rect
    for j in range(fh):
        for i in range(fw):
            k = FACE_LIGHT[face] * (1.06 - 0.16 * j / max(1, fh - 1)) * (0.96 + 0.08 * rng.random())
            c = shade(base, k)
            edge = i == 0 or j == 0 or i == fw - 1 or j == fh - 1
            if edge and part in ("shoulder", "gauntlet", "belt", "hair_band"):
                c = shade(accent, FACE_LIGHT[face])
            elif edge:
                c = shade(c, 0.82)
            a = 255 if alpha_fn is None else alpha_fn(j, fh)
            put(fx + i, fy + j, c, a)


def leg_alpha(j, fh, part):
    # Stands trail off into wisps below the waist.
    top = 0 if part == "thigh" else 6
    t = (top + j) / 11.0
    return max(40, int(255 * (1.0 - t * 0.85)))


def details(part, rects):
    fx, fy, fw, fh = rects["north"]
    if part == "head":
        for ex in (1, 5):
            for i in range(2):
                put(fx + ex + i, fy + 3, EYE, glowing=True)
            put(fx + ex, fy + 2, shade(SKIN_DARK, 0.8))
            put(fx + ex + 1, fy + 2, shade(SKIN_DARK, 0.8))
        for i in range(2, 6):
            put(fx + i, fy + 6, shade(SKIN_DARK, 0.75))
        for x in (0, 7):
            put(fx + x, fy + 4, GOLD)
            put(fx + x, fy + 5, GOLD_DARK)
    elif part == "chest":
        cx, cy = fx + fw // 2, fy + 3
        star = [(0, -2), (0, -1), (-1, 0), (0, 0), (1, 0), (-2, 0), (2, 0), (0, 1), (-1, 1), (1, 1), (-1, 2), (1, 2)]
        for dx, dy in star:
            put(cx + dx, cy + dy, STAR, glowing=True)
        for i in range(1, fw - 1):
            if abs(i - fw // 2) > 2:
                put(fx + i, fy + 6, shade(SKIN_DARK, 0.9))
        put(fx + 1, fy + 1, GOLD)
        put(fx + fw - 2, fy + 1, GOLD)
    elif part == "belt":
        cx = fx + fw // 2
        for dy in range(fh):
            put(cx, fy + dy, STAR, glowing=True)
    elif part == "gauntlet":
        for i in range(fw):
            put(fx + i, fy + 1, GOLD)
            put(fx + i, fy + fh - 2, GOLD_DARK)
        put(fx + fw // 2, fy + 3, EYE, glowing=True)
    elif part == "fist":
        for i in range(fw):
            put(fx + i, fy, shade(SKIN, 1.15))
        for i in range(0, fw, 2):
            put(fx + i, fy + 1, shade(SKIN_DARK, 0.9))
    elif part == "hair_band":
        put(fx + fw // 2, fy + 1, STAR, glowing=True)
    elif part == "scarf":
        sx, sy, sw, sh = rects["south"]
        for i in range(sw):
            put(sx + i, sy + sh - 1, GOLD)


PART_COLORS = {
    "head": (SKIN, GOLD), "hair_band": (GOLD, GOLD_DARK), "hair_back": (HAIR, HAIR), "hair_top": (HAIR, HAIR), "hair_side_r": (HAIR, HAIR), "hair_side_l": (HAIR, HAIR), "crest": (GOLD, GOLD_DARK),
    "chest": (SKIN, GOLD), "neck": (SKIN_DARK, SKIN_DARK), "abdomen": (SKIN_DARK, SKIN), "belt": (GAUNTLET, GOLD),
    "scarf": (SCARF, GOLD), "shoulder": (GOLD, GOLD_DARK), "upper_arm": (SKIN, SKIN), "gauntlet": (GAUNTLET, GOLD),
    "fist": (SKIN, SKIN), "thigh": (SKIN_DARK, SKIN), "shin": (SKIN_DARK, SKIN),
}


def build():
    uvs = pack()
    bones = {name: {"name": name, "pivot": pivot, "cubes": []} | ({"parent": parent} if parent else {})
             for name, (parent, pivot) in BONES.items()}
    painted = set()
    for i, (bone, part, origin, size) in enumerate(CUBES):
        uv = uvs[i]
        bones[bone]["cubes"].append({"origin": origin, "size": size, "uv": list(uv)})
        rects = faces(size, uv)
        base, accent = PART_COLORS[part]
        if part.startswith("hair") and part != "hair_band":
            accent = HAIR
        for face, rect in rects.items():
            alpha_fn = (lambda j, fh, p=part: leg_alpha(j, fh, p)) if part in ("thigh", "shin") else None
            paint_face(rect, face, base, accent, part, alpha_fn)
        if (part, bone.split("_")[0]) not in painted:
            details(part, rects)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry." + NAME,
                "texture_width": TEX, "texture_height": TEX,
                "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": [bones[name] for name in BONES],
        }],
    }
    return geo


def kf(**frames):
    return {("%.2f" % float(t.replace("t", "").replace("_", "."))): v for t, v in frames.items()}


def animations():
    def frames(pairs):
        return {"%.2f" % t: v for t, v in pairs}

    idle = {
        "loop": True, "animation_length": 3.0,
        "bones": {
            "root": {"position": frames([(0, [0, 0, 0]), (1.5, [0, 1.2, 0]), (3, [0, 0, 0])])},
            "chest": {"rotation": frames([(0, [2, 0, 0]), (1.5, [-1, 0, 0]), (3, [2, 0, 0])])},
            "head": {"rotation": frames([(0, [0, 0, 0]), (1.5, [-3, 2, 0]), (3, [0, 0, 0])])},
            "right_arm": {"rotation": frames([(0, [-8, 0, 6]), (1.5, [-12, 0, 8]), (3, [-8, 0, 6])])},
            "left_arm": {"rotation": frames([(0, [-8, 0, -6]), (1.5, [-12, 0, -8]), (3, [-8, 0, -6])])},
            "right_forearm": {"rotation": frames([(0, [-14, 0, 0]), (1.5, [-20, 0, 0]), (3, [-14, 0, 0])])},
            "left_forearm": {"rotation": frames([(0, [-14, 0, 0]), (1.5, [-20, 0, 0]), (3, [-14, 0, 0])])},
            "scarf": {"rotation": frames([(0, [12, 0, 0]), (1.5, [22, 0, 3]), (3, [12, 0, 0])])},
            "right_leg": {"rotation": frames([(0, [8, 0, 0]), (1.5, [14, 0, 0]), (3, [8, 0, 0])])},
            "left_leg": {"rotation": frames([(0, [14, 0, 0]), (1.5, [8, 0, 0]), (3, [14, 0, 0])])},
        },
    }
    barrage = {
        "loop": True, "animation_length": 0.2,
        "bones": {
            "chest": {"rotation": frames([(0, [6, 10, 0]), (0.1, [6, -10, 0]), (0.2, [6, 10, 0])])},
            "right_arm": {"rotation": frames([(0, [-92, 0, 0]), (0.1, [-70, 10, 0]), (0.2, [-92, 0, 0])]),
                          "position": frames([(0, [0, 0, -3]), (0.1, [0, 0, 1]), (0.2, [0, 0, -3])])},
            "left_arm": {"rotation": frames([(0, [-70, -10, 0]), (0.1, [-92, 0, 0]), (0.2, [-70, -10, 0])]),
                         "position": frames([(0, [0, 0, 1]), (0.1, [0, 0, -3]), (0.2, [0, 0, 1])])},
            "right_forearm": {"rotation": frames([(0, [0, 0, 0])])},
            "left_forearm": {"rotation": frames([(0, [0, 0, 0])])},
            "scarf": {"rotation": frames([(0, [40, 0, 0]), (0.1, [46, 0, 0]), (0.2, [40, 0, 0])])},
        },
    }
    heavy = {
        "loop": False, "animation_length": 0.7,
        "bones": {
            "chest": {"rotation": frames([(0, [0, 0, 0]), (0.25, [0, 28, 0]), (0.35, [8, -22, 0]), (0.55, [8, -22, 0]), (0.7, [0, 0, 0])])},
            "right_arm": {"rotation": frames([(0, [-10, 0, 0]), (0.25, [-40, 0, 20]), (0.35, [-95, 0, 0]), (0.55, [-95, 0, 0]), (0.7, [-10, 0, 0])]),
                          "position": frames([(0, [0, 0, 0]), (0.25, [0, 0, 3]), (0.35, [0, 0, -5]), (0.55, [0, 0, -5]), (0.7, [0, 0, 0])])},
            "right_forearm": {"rotation": frames([(0, [-14, 0, 0]), (0.25, [-70, 0, 0]), (0.35, [0, 0, 0]), (0.7, [-14, 0, 0])])},
            "left_arm": {"rotation": frames([(0, [-8, 0, 0]), (0.25, [-30, 0, -10]), (0.35, [10, 0, -15]), (0.7, [-8, 0, 0])])},
            "head": {"rotation": frames([(0, [0, 0, 0]), (0.25, [5, -15, 0]), (0.35, [0, 10, 0]), (0.7, [0, 0, 0])])},
        },
    }
    guard = {
        "loop": True, "animation_length": 1.0,
        "bones": {
            "right_arm": {"rotation": frames([(0, [-95, -35, 0]), (0.5, [-98, -38, 0]), (1, [-95, -35, 0])])},
            "left_arm": {"rotation": frames([(0, [-95, 35, 0]), (0.5, [-98, 38, 0]), (1, [-95, 35, 0])])},
            "right_forearm": {"rotation": frames([(0, [-30, 0, 0])])},
            "left_forearm": {"rotation": frames([(0, [-30, 0, 0])])},
            "head": {"rotation": frames([(0, [8, 0, 0])])},
            "root": {"position": frames([(0, [0, 0, 0]), (0.5, [0, 0.5, 0]), (1, [0, 0, 0])])},
        },
    }
    time_stop = {
        "loop": "hold_on_last_frame", "animation_length": 0.6,
        "bones": {
            "right_arm": {"rotation": frames([(0, [-10, 0, 0]), (0.4, [-150, 0, -25]), (0.6, [-145, 0, -30])])},
            "left_arm": {"rotation": frames([(0, [-10, 0, 0]), (0.4, [-150, 0, 25]), (0.6, [-145, 0, 30])])},
            "right_forearm": {"rotation": frames([(0, [-14, 0, 0]), (0.6, [0, 0, 0])])},
            "left_forearm": {"rotation": frames([(0, [-14, 0, 0]), (0.6, [0, 0, 0])])},
            "head": {"rotation": frames([(0, [0, 0, 0]), (0.6, [-15, 0, 0])])},
            "root": {"position": frames([(0, [0, 0, 0]), (0.6, [0, 2, 0])])},
            "scarf": {"rotation": frames([(0, [12, 0, 0]), (0.6, [35, 0, 0])])},
        },
    }
    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.stand.idle": idle,
            "animation.stand.barrage": barrage,
            "animation.stand.heavy": heavy,
            "animation.stand.guard": guard,
            "animation.stand.time_stop": time_stop,
        },
        "geckolib_format_version": 2,
    }


def arrowhead():
    size = 16
    rows = [[(0, 0, 0, 0)] * size for _ in range(size)]
    blade = {(7, 1), (8, 1), (6, 2), (7, 2), (8, 2), (9, 2), (6, 3), (7, 3), (8, 3), (9, 3), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
             (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (4, 6), (5, 6), (6, 6), (9, 6), (10, 6), (11, 6), (4, 7), (5, 7), (10, 7), (11, 7)}
    for x, y in blade:
        light = 1.15 if x < 8 else 0.85
        rows[y][x] = shade(GOLD, light * (1.05 - y * 0.03)) + (255,)
    for x, y in ((7, 3), (8, 4), (7, 5), (8, 5)):
        rows[y][x] = (0xC7, 0x7D, 0xFF, 255)
    rows[2][7] = (0xFF, 0xF4, 0xC2, 255)
    for y in range(7, 15):
        for x in (7, 8):
            c = (0x5A, 0x3B, 0x22) if (x + y) % 3 else (0x7A, 0x52, 0x30)
            rows[y][x] = c + (255,)
    for x in (6, 9):
        rows[9][x] = shade(GOLD_DARK, 1.0) + (255,)
        rows[10][x] = shade(GOLD_DARK, 0.85) + (255,)
    rows[6][7] = rows[6][8] = shade(GOLD_DARK, 1.0) + (255,)
    return rows


def main():
    geo = build()
    paths = {
        "geo": os.path.join(ROOT, "geo", "entity", "stand", NAME + ".geo.json"),
        "anim": os.path.join(ROOT, "animations", "entity", "stand", NAME + ".animation.json"),
        "tex": os.path.join(ROOT, "textures", "entity", "stand", NAME + ".png"),
        "glow": os.path.join(ROOT, "textures", "entity", "stand", NAME + "_glowmask.png"),
        "item": os.path.join(ROOT, "textures", "item", "awakening_arrowhead.png"),
    }
    for path in paths.values():
        os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(paths["geo"], "w") as handle:
        json.dump(geo, handle, indent=1)
    with open(paths["anim"], "w") as handle:
        json.dump(animations(), handle, indent=1)
    write_png(paths["tex"], TEX, TEX, pixels)
    write_png(paths["glow"], TEX, TEX, glow)
    write_png(paths["item"], 16, 16, arrowhead())
    print("Stand assets written for", NAME)


if __name__ == "__main__":
    main()
