#!/usr/bin/env python3
"""Generates every Stand: GeckoLib geometry, painted box-UV texture, glow mask and shared animations, plus the
Awakening Arrowhead item texture. Pure standard library.

    python3 tools/generate_stand.py

Each Stand shares an anatomical base body (V-shaped torso, defined pecs/abs, narrow waist, slim limbs) and adds its
own signature parts and palette. Models face -Z; the model's right side is -X.
"""
import json
import math
import os
import random

from generate_textures import write_png

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "infinitemultiverse")
TEX = 128


def rgb(value):
    return ((value >> 16) & 0xFF, (value >> 8) & 0xFF, value & 0xFF)


BONES = {
    "root": (None, [0, 0, 0]),
    "waist": ("root", [0, 12, 0]),
    "chest": ("waist", [0, 18, 0]),
    "head": ("chest", [0, 28, 0]),
    "scarf": ("chest", [0, 25, 3.5]),
    "right_arm": ("chest", [-6.5, 25, 0]),
    "right_forearm": ("right_arm", [-7, 19, 0]),
    "right_fist": ("right_forearm", [-7, 13, 0]),
    "left_arm": ("chest", [6.5, 25, 0]),
    "left_forearm": ("left_arm", [7, 19, 0]),
    "left_fist": ("left_forearm", [7, 13, 0]),
    "right_leg": ("waist", [-2, 11, 0]),
    "left_leg": ("waist", [2, 11, 0]),
}

# (bone, part, role, origin, size). Right-side parts are mirrored automatically.
BASE = [
    ("waist", "hips", "skin_dark", [-3.5, 10, -2], [7, 3, 4]),
    ("waist", "abs", "skin", [-3, 13, -2], [6, 5, 4]),
    ("chest", "ribs", "skin", [-4, 18, -2.5], [8, 3, 5]),
    ("chest", "chest", "skin", [-5.5, 21, -3], [11, 5, 6]),
    ("chest", "pecs", "skin_light", [-4.5, 22, -3.5], [9, 3, 1]),
    ("chest", "neck", "skin_dark", [-1.5, 26, -1.5], [3, 2, 3]),
    ("head", "head", "skin", [-3.5, 28, -3.5], [7, 7, 7]),
    ("right_arm", "deltoid", "skin", [-8.5, 22, -2], [3, 3, 4]),
    ("right_arm", "upper_arm", "skin", [-8.5, 19, -1.5], [3, 4, 3]),
    ("right_forearm", "forearm", "skin", [-8.5, 13, -1.5], [3, 6, 3]),
    ("right_fist", "fist", "skin_light", [-9, 10, -2], [4, 3, 4]),
    ("right_leg", "thigh", "skin_dark", [-3.5, 5, -1.5], [3, 6, 3]),
    ("right_leg", "shin", "skin_dark", [-3, 0, -1], [2, 5, 2]),
]

STANDS = {
    "star_platinum": {
        "palette": {"skin": 0x6A5FC9, "skin_light": 0x8A80E0, "skin_dark": 0x463C8F, "accent": 0xE8B83A, "accent_dark": 0x9A6E1C,
                    "hair": 0x2A1A4A, "cloth": 0xC42A3C, "eye": 0x7FF6FF, "emblem": 0xFFE27A},
        "emblem": ("shoulder", "star"),
        "parts": [
            ("head", "hair_top", "hair", [-4, 34, -4], [8, 2, 8]),
            ("head", "hair_crown", "hair", [-3, 36, -3], [6, 1, 6]),
            ("head", "headband", "accent", [-3.75, 33, -3.75], [7, 1, 7]),
            ("head", "hair_back", "hair", [-4.5, 21, 3.5], [9, 14, 3]),
            ("head", "hair_tail", "hair", [-3.5, 15, 4.5], [7, 6, 2]),
            ("head", "hair_side", "hair", [-4.5, 27, -3], [1, 7, 6]),
            ("chest", "scarf_neck", "cloth", [-4, 25, -3.5], [8, 2, 7]),
            ("scarf", "scarf_tail_a", "cloth", [-3, 13, 3.5], [2, 12, 1]),
            ("scarf", "scarf_tail_b", "cloth", [1, 15, 3.5], [2, 10, 1]),
            ("right_arm", "shoulder", "accent", [-9.5, 24, -3], [4, 2, 6]),
            ("right_forearm", "wristband", "accent", [-9, 13, -2], [4, 2, 4]),
            ("waist", "sash", "cloth", [-4, 11, -2.5], [8, 2, 5]),
            ("waist", "loin", "cloth", [-2, 5, -3], [4, 6, 1]),
            ("right_leg", "knee", "accent", [-3.5, 5, -2], [3, 2, 1]),
        ],
    },
    "the_world": {
        "palette": {"skin": 0xF1C232, "skin_light": 0xFFE07A, "skin_dark": 0xB8860B, "accent": 0x2F4F2F, "accent_dark": 0x1A2E1A,
                    "hair": 0xE8B020, "cloth": 0x2F4F2F, "eye": 0xFFFF99, "emblem": 0x2F4F2F},
        "emblem": ("chest", "heart"),
        "parts": [
            ("head", "helmet", "hair", [-4, 32, -4], [8, 3, 8]),
            ("head", "helmet_back", "hair", [-4, 26, 3.5], [8, 7, 1]),
            ("head", "mask", "accent", [-3.5, 29, -3.75], [7, 2, 1]),
            ("chest", "hose", "accent", [-3, 18, 3], [1, 8, 1]),
            ("right_arm", "shoulder", "accent", [-9.5, 24, -3], [4, 2, 6]),
            ("right_forearm", "wristband", "accent", [-9, 13, -2], [4, 2, 4]),
            ("waist", "sash", "accent", [-4, 11, -2.5], [8, 2, 5]),
            ("right_leg", "knee", "skin_light", [-3.5, 5, -2], [3, 2, 1]),
        ],
    },
    "killer_queen": {
        "palette": {"skin": 0xE8A5C8, "skin_light": 0xF6C8DF, "skin_dark": 0xB3709A, "accent": 0xE6E1EA, "accent_dark": 0x8F8696,
                    "hair": 0xD48AB2, "cloth": 0x6B4C7A, "eye": 0xC8FF6A, "emblem": 0xF2F2F2},
        "emblem": ("sash", "skull"),
        "parts": [
            ("head", "cap", "hair", [-3.75, 34, -3.75], [7, 1, 7]),
            ("head", "ear", "hair", [-3.5, 35, -1], [2, 2, 2]),
            ("chest", "collar", "accent", [-4, 25, -3], [8, 1, 6]),
            ("right_arm", "shoulder", "accent", [-9, 24, -2.5], [3, 1, 5]),
            ("right_forearm", "wristband", "accent", [-9, 13, -2], [4, 1, 4]),
            ("waist", "sash", "cloth", [-4, 11, -2.5], [8, 2, 5]),
            ("right_leg", "knee", "accent", [-3.5, 5, -2], [3, 2, 1]),
        ],
    },
    "gold_experience": {
        "palette": {"skin": 0xF2C94C, "skin_light": 0xFFE38A, "skin_dark": 0xB88A1A, "accent": 0x3E9B4F, "accent_dark": 0x24622F,
                    "hair": 0xE0A82E, "cloth": 0x3E9B4F, "eye": 0x8BFF8B, "emblem": 0xE0303A},
        "emblem": ("chest", "ladybug"),
        "parts": [
            ("head", "curls", "hair", [-4, 34, -4], [8, 2, 8]),
            ("head", "curl_front", "hair", [-3, 33, -4.5], [2, 2, 1]),
            ("head", "hair_back", "hair", [-4, 27, 3.5], [8, 7, 1]),
            ("right_arm", "shoulder", "accent", [-9.5, 24, -3], [4, 2, 6]),
            ("right_forearm", "wristband", "accent", [-9, 13, -2], [4, 1, 4]),
            ("waist", "sash", "accent", [-4, 11, -2.5], [8, 2, 5]),
            ("right_leg", "knee", "accent", [-3.5, 5, -2], [3, 2, 1]),
        ],
    },
    "king_crimson": {
        "palette": {"skin": 0xC0283A, "skin_light": 0xE04A5A, "skin_dark": 0x7A1422, "accent": 0xF2EEEE, "accent_dark": 0x9A8E8E,
                    "hair": 0x9A1A2C, "cloth": 0xF2EEEE, "eye": 0xB8FFD0, "emblem": 0xF2EEEE},
        "emblem": ("chest", "grid"),
        "parts": [
            ("head", "crest", "hair", [-3.75, 34, -3.75], [7, 2, 7]),
            ("head", "spikes", "hair", [-3, 31, 3.5], [6, 4, 2]),
            ("head", "epitaph", "skin_light", [-2, 32, -4], [4, 2, 1]),
            ("right_arm", "shoulder", "accent", [-9.5, 24, -3], [4, 2, 6]),
            ("right_forearm", "wristband", "accent", [-9, 13, -2], [4, 1, 4]),
            ("waist", "sash", "accent", [-4, 11, -2.5], [8, 2, 5]),
            ("right_leg", "knee", "accent", [-3.5, 5, -2], [3, 2, 1]),
        ],
    },
    "made_in_heaven": {
        "palette": {"skin": 0xEEF1F5, "skin_light": 0xFFFFFF, "skin_dark": 0xB9C0CC, "accent": 0x22262E, "accent_dark": 0x101216,
                    "hair": 0x4CC59B, "cloth": 0x4CC59B, "eye": 0x5CFFB0, "emblem": 0x4CC59B},
        "emblem": ("chest", "gauge"),
        "parts": [
            ("head", "mane", "hair", [-1, 31, -3], [2, 6, 9]),
            ("head", "visor", "accent", [-3.5, 30, -3.75], [7, 2, 1]),
            ("right_arm", "shoulder", "accent", [-9.5, 24, -3], [4, 2, 6]),
            ("right_forearm", "wristband", "hair", [-9, 13, -2], [4, 1, 4]),
            ("waist", "sash", "cloth", [-4, 11, -2.5], [8, 2, 5]),
            ("right_leg", "hoof", "accent", [-3.5, 0, -1.5], [3, 2, 3]),
        ],
    },
    "tusk": {
        "palette": {"skin": 0xF7A8CC, "skin_light": 0xFFD0E6, "skin_dark": 0xC26A93, "accent": 0xF5D36B, "accent_dark": 0xB8902A,
                    "hair": 0xFFFFFF, "cloth": 0xFFFFFF, "eye": 0x6AF0FF, "emblem": 0xF5D36B},
        "emblem": ("chest", "star"),
        "parts": [
            ("head", "star_cap", "hair", [-3.75, 34, -3.75], [7, 1, 7]),
            ("head", "point_top", "accent", [-1, 35, -1], [2, 3, 2]),
            ("head", "point_side", "accent", [-5, 32, -1], [2, 2, 2]),
            ("right_fist", "nail", "accent", [-8, 8, -1], [1, 2, 1]),
            ("waist", "sash", "cloth", [-4, 11, -2.5], [8, 2, 5]),
        ],
    },
}

FACE_LIGHT = {"up": 1.15, "down": 0.65, "north": 1.0, "south": 0.78, "east": 0.86, "west": 0.86}
EMBLEMS = {
    "star": [(0, -2), (0, -1), (-1, 0), (0, 0), (1, 0), (-2, 0), (2, 0), (0, 1), (-1, 1), (1, 1), (-1, 2), (1, 2)],
    "heart": [(-1, -1), (1, -1), (-2, 0), (-1, 0), (0, 0), (1, 0), (2, 0), (-1, 1), (0, 1), (1, 1), (0, 2)],
    "skull": [(-1, -1), (0, -1), (1, -1), (-1, 0), (1, 0), (-1, 1), (0, 1), (1, 1)],
    "ladybug": [(-1, -1), (0, -1), (1, -1), (-1, 0), (0, 0), (1, 0), (-1, 1), (0, 1), (1, 1)],
    "grid": [(-2, -1), (0, -1), (2, -1), (-1, 0), (1, 0), (-2, 1), (0, 1), (2, 1)],
    "gauge": [(-1, -1), (0, -1), (1, -1), (-2, 0), (2, 0), (0, 0), (-1, 1), (1, 1)],
}
MIRRORED_PARTS = ("hair_side", "ear", "point_side")


def mirrored(cubes):
    out = list(cubes)
    for bone, part, role, (x, y, z), (w, h, d) in cubes:
        if bone.startswith("right_") or part in MIRRORED_PARTS:
            out.append((bone.replace("right_", "left_"), part, role, [-(x + w), y, z], [w, h, d]))
    return out


def box_uv_size(size):
    w, h, d = (int(math.ceil(v)) for v in size)
    return 2 * (w + d), d + h


def faces(size, uv):
    w, h, d = (int(math.ceil(v)) for v in size)
    u, v = uv
    return {
        "up": (u + d, v, w, d), "down": (u + d + w, v, w, d),
        "east": (u, v + d, d, h), "north": (u + d, v + d, w, h),
        "west": (u + d + w, v + d, d, h), "south": (u + 2 * d + w, v + d, w, h),
    }


def pack(cubes):
    order = sorted(range(len(cubes)), key=lambda i: -box_uv_size(cubes[i][4])[1])
    uvs, x, y, row_h = {}, 0, 0, 0
    for i in order:
        uw, uh = box_uv_size(cubes[i][4])
        if x + uw > TEX:
            x, y, row_h = 0, y + row_h, 0
        uvs[i] = (x, y)
        x += uw
        row_h = max(row_h, uh)
    assert y + row_h <= TEX, "texture atlas overflow"
    return uvs


class Painter:
    def __init__(self, palette, seed):
        self.p = {k: rgb(v) for k, v in palette.items()}
        self.px = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
        self.glow = [[(0, 0, 0, 0)] * TEX for _ in range(TEX)]
        self.rng = random.Random(seed)

    @staticmethod
    def shade(c, k):
        return tuple(max(0, min(255, int(v * k))) for v in c)

    def put(self, x, y, c, a=255, glowing=False):
        if 0 <= x < TEX and 0 <= y < TEX:
            self.px[y][x] = (c[0], c[1], c[2], a)
            if glowing:
                self.glow[y][x] = (c[0], c[1], c[2], 255)

    def face(self, rect, face, base, part, alpha_fn=None):
        fx, fy, fw, fh = rect
        trim = part in ("shoulder", "wristband", "headband", "knee", "sash", "collar", "helmet", "visor", "hoof")
        hairy = part.startswith("hair") or part in ("curls", "mane", "spikes")
        for j in range(fh):
            for i in range(fw):
                k = FACE_LIGHT[face] * (1.07 - 0.18 * j / max(1, fh - 1)) * (0.95 + 0.08 * self.rng.random())
                c = self.shade(base, k)
                if i in (0, fw - 1) or j in (0, fh - 1):
                    c = self.shade(c, 1.2 if trim else 0.8)
                if hairy and (i + j * 2) % 4 == 0:
                    c = self.shade(c, 1.3)
                self.put(fx + i, fy + j, c, 255 if alpha_fn is None else alpha_fn(j, fh))

    def emblem(self, rect, kind, color):
        fx, fy, fw, fh = rect
        cx, cy = fx + fw // 2, fy + fh // 2
        for dx, dy in EMBLEMS[kind]:
            c = (20, 20, 20) if kind == "ladybug" and (dx, dy) in ((-1, -1), (1, 1), (0, 0)) else color
            self.put(cx + dx, cy + dy, c, glowing=kind != "ladybug")


def leg_alpha(part):
    def fn(j, fh):
        top = 0 if part == "thigh" else 6
        return max(50, int(255 * (1.0 - (top + j) / 11.0 * 0.8)))
    return fn


def detail(painter, part, rects, p):
    put, shade = painter.put, painter.shade
    fx, fy, fw, fh = rects["north"]
    if part == "head":
        for ex in (1, 4):
            for i in (0, 1):
                put(fx + ex + i, fy + 3, p["eye"], glowing=True)
                put(fx + ex + i, fy + 2, shade(p["skin_dark"], 0.6))
        put(fx + 3, fy + 4, shade(p["skin_dark"], 0.9))
        for i in (2, 3, 4):
            put(fx + i, fy + 5, shade(p["skin_dark"], 0.6))
    elif part == "pecs":
        put(fx + fw // 2, fy, shade(p["skin_dark"], 0.8))
        put(fx + fw // 2, fy + 1, shade(p["skin_dark"], 0.8))
        for i in range(fw):
            put(fx + i, fy + fh - 1, shade(p["skin_dark"], 0.9))
    elif part == "abs":
        for j in range(fh):
            put(fx + fw // 2, fy + j, shade(p["skin_dark"], 0.85))
        for j in (1, 3):
            for i in range(1, fw - 1):
                put(fx + i, fy + j, shade(p["skin_dark"], 0.9))
    elif part == "fist":
        for i in range(0, fw, 2):
            put(fx + i, fy, shade(p["skin_light"], 1.2))
            put(fx + i, fy + 1, shade(p["skin_dark"], 0.85))
    elif part == "epitaph":
        for i in (1, 2):
            put(fx + i, fy, p["eye"], glowing=True)
            put(fx + i, fy + 1, shade(p["skin_dark"], 0.6))
    elif part in ("scarf_tail_a", "scarf_tail_b"):
        sx, sy, sw, sh = rects["south"]
        for i in range(sw):
            put(sx + i, sy + sh - 1, shade(p["cloth"], 0.6))
    elif part == "knee":
        put(fx + 1, fy, p["emblem"])


def build(name, spec):
    cubes = mirrored(BASE + spec["parts"])
    uvs = pack(cubes)
    painter = Painter(spec["palette"], sum(map(ord, name)))
    p = painter.p
    bones = {b: dict({"name": b, "pivot": piv, "cubes": []}, **({"parent": par} if par else {})) for b, (par, piv) in BONES.items()}
    rects_by_part = {}
    for i, (bone, part, role, origin, size) in enumerate(cubes):
        uv = uvs[i]
        bones[bone]["cubes"].append({"origin": origin, "size": size, "uv": list(uv)})
        rects = faces(size, uv)
        rects_by_part.setdefault(part, rects)
        alpha = leg_alpha(part) if part in ("thigh", "shin") else None
        for face, rect in rects.items():
            painter.face(rect, face, p[role], part, alpha)
        detail(painter, part, rects, p)
    emblem_part, kind = spec["emblem"]
    if emblem_part in rects_by_part:
        painter.emblem(rects_by_part[emblem_part]["north"], kind, p["emblem"])
        if emblem_part == "shoulder":
            painter.emblem(rects_by_part[emblem_part]["up"], kind, p["emblem"])
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry." + name, "texture_width": TEX, "texture_height": TEX,
                            "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1.25, 0]},
            "bones": [bones[b] for b in BONES],
        }],
    }
    return geo, painter


def animations():
    def frames(pairs):
        return {"%.2f" % t: v for t, v in pairs}

    return {
        "format_version": "1.8.0",
        "animations": {
            "animation.stand.idle": {
                "loop": True, "animation_length": 3.0,
                "bones": {
                    "root": {"position": frames([(0, [0, 0, 0]), (1.5, [0, 1.2, 0]), (3, [0, 0, 0])])},
                    "chest": {"rotation": frames([(0, [2, 0, 0]), (1.5, [-1, 0, 0]), (3, [2, 0, 0])])},
                    "right_arm": {"rotation": frames([(0, [-8, 0, 6]), (1.5, [-12, 0, 8]), (3, [-8, 0, 6])])},
                    "left_arm": {"rotation": frames([(0, [-8, 0, -6]), (1.5, [-12, 0, -8]), (3, [-8, 0, -6])])},
                    "right_forearm": {"rotation": frames([(0, [-14, 0, 0]), (1.5, [-20, 0, 0]), (3, [-14, 0, 0])])},
                    "left_forearm": {"rotation": frames([(0, [-14, 0, 0]), (1.5, [-20, 0, 0]), (3, [-14, 0, 0])])},
                    "scarf": {"rotation": frames([(0, [12, 0, 0]), (1.5, [22, 0, 3]), (3, [12, 0, 0])])},
                    "right_leg": {"rotation": frames([(0, [8, 0, 0]), (1.5, [14, 0, 0]), (3, [8, 0, 0])])},
                    "left_leg": {"rotation": frames([(0, [14, 0, 0]), (1.5, [8, 0, 0]), (3, [14, 0, 0])])},
                },
            },
            "animation.stand.barrage": {
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
            },
            "animation.stand.heavy": {
                "loop": False, "animation_length": 0.7,
                "bones": {
                    "chest": {"rotation": frames([(0, [0, 0, 0]), (0.25, [0, 28, 0]), (0.35, [8, -22, 0]), (0.55, [8, -22, 0]), (0.7, [0, 0, 0])])},
                    "right_arm": {"rotation": frames([(0, [-10, 0, 0]), (0.25, [-40, 0, 20]), (0.35, [-95, 0, 0]), (0.55, [-95, 0, 0]), (0.7, [-10, 0, 0])]),
                                  "position": frames([(0, [0, 0, 0]), (0.25, [0, 0, 3]), (0.35, [0, 0, -5]), (0.55, [0, 0, -5]), (0.7, [0, 0, 0])])},
                    "right_forearm": {"rotation": frames([(0, [-14, 0, 0]), (0.25, [-70, 0, 0]), (0.35, [0, 0, 0]), (0.7, [-14, 0, 0])])},
                    "left_arm": {"rotation": frames([(0, [-8, 0, 0]), (0.25, [-30, 0, -10]), (0.35, [10, 0, -15]), (0.7, [-8, 0, 0])])},
                },
            },
            "animation.stand.guard": {
                "loop": True, "animation_length": 1.0,
                "bones": {
                    "right_arm": {"rotation": frames([(0, [-95, -35, 0]), (0.5, [-98, -38, 0]), (1, [-95, -35, 0])])},
                    "left_arm": {"rotation": frames([(0, [-95, 35, 0]), (0.5, [-98, 38, 0]), (1, [-95, 35, 0])])},
                    "right_forearm": {"rotation": frames([(0, [-30, 0, 0])])},
                    "left_forearm": {"rotation": frames([(0, [-30, 0, 0])])},
                    "root": {"position": frames([(0, [0, 0, 0]), (0.5, [0, 0.5, 0]), (1, [0, 0, 0])])},
                },
            },
            "animation.stand.time_stop": {
                "loop": "hold_on_last_frame", "animation_length": 0.6,
                "bones": {
                    "right_arm": {"rotation": frames([(0, [-10, 0, 0]), (0.4, [-150, 0, -25]), (0.6, [-145, 0, -30])])},
                    "left_arm": {"rotation": frames([(0, [-10, 0, 0]), (0.4, [-150, 0, 25]), (0.6, [-145, 0, 30])])},
                    "right_forearm": {"rotation": frames([(0, [-14, 0, 0]), (0.6, [0, 0, 0])])},
                    "left_forearm": {"rotation": frames([(0, [-14, 0, 0]), (0.6, [0, 0, 0])])},
                    "root": {"position": frames([(0, [0, 0, 0]), (0.6, [0, 2, 0])])},
                    "scarf": {"rotation": frames([(0, [12, 0, 0]), (0.6, [35, 0, 0])])},
                },
            },
        },
        "geckolib_format_version": 2,
    }


def arrowhead():
    gold, gold_dark = (0xE8, 0xB8, 0x3A), (0x9A, 0x6E, 0x1C)

    def shade(c, k):
        return tuple(max(0, min(255, int(v * k))) for v in c)

    rows = [[(0, 0, 0, 0)] * 16 for _ in range(16)]
    blade = {(7, 1), (8, 1), (6, 2), (7, 2), (8, 2), (9, 2), (6, 3), (7, 3), (8, 3), (9, 3), (5, 4), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4),
             (5, 5), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5), (4, 6), (5, 6), (6, 6), (9, 6), (10, 6), (11, 6), (4, 7), (5, 7), (10, 7), (11, 7)}
    for x, y in blade:
        rows[y][x] = shade(gold, (1.15 if x < 8 else 0.85) * (1.05 - y * 0.03)) + (255,)
    for x, y in ((7, 3), (8, 4), (7, 5), (8, 5)):
        rows[y][x] = (0xC7, 0x7D, 0xFF, 255)
    rows[2][7] = (0xFF, 0xF4, 0xC2, 255)
    for y in range(7, 15):
        for x in (7, 8):
            rows[y][x] = ((0x5A, 0x3B, 0x22) if (x + y) % 3 else (0x7A, 0x52, 0x30)) + (255,)
    for x in (6, 9):
        rows[9][x] = gold_dark + (255,)
        rows[10][x] = shade(gold_dark, 0.85) + (255,)
    rows[6][7] = rows[6][8] = gold_dark + (255,)
    return rows


def main():
    anim = animations()
    for name, spec in STANDS.items():
        geo, painter = build(name, spec)
        base = {
            "geo": os.path.join(ROOT, "geo", "entity", "stand", name + ".geo.json"),
            "anim": os.path.join(ROOT, "animations", "entity", "stand", name + ".animation.json"),
            "tex": os.path.join(ROOT, "textures", "entity", "stand", name + ".png"),
            "glow": os.path.join(ROOT, "textures", "entity", "stand", name + "_glowmask.png"),
        }
        for path in base.values():
            os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(base["geo"], "w") as handle:
            json.dump(geo, handle, indent=1)
        with open(base["anim"], "w") as handle:
            json.dump(anim, handle, indent=1)
        write_png(base["tex"], TEX, TEX, painter.px)
        write_png(base["glow"], TEX, TEX, painter.glow)
    write_png(os.path.join(ROOT, "textures", "item", "awakening_arrowhead.png"), 16, 16, arrowhead())
    print("Stand assets written:", ", ".join(STANDS))


if __name__ == "__main__":
    main()
