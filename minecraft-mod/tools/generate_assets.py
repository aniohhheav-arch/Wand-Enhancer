#!/usr/bin/env python3
"""Procedurally generates the mod's textures, GeckoLib geometry/animations and sounds.

All generated output is committed, so this only needs re-running when the art changes:
    python3 tools/generate_assets.py
Requires numpy + Pillow, and ffmpeg (with libvorbis) for the .ogg sounds.
"""
import json
import math
import os
import shutil
import subprocess
import sys
import tempfile
import wave

import numpy as np
from PIL import Image

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "assets", "infinitygauntlet")
RNG = np.random.default_rng(1337)

STONES = {
    "space": (40, 110, 255),
    "mind": (255, 210, 40),
    "reality": (220, 20, 40),
    "power": (160, 50, 255),
    "time": (30, 220, 90),
    "soul": (255, 130, 20),
}


def path(*parts):
    p = os.path.join(ROOT, *parts)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    return p


def save(img, *parts):
    img.save(path(*parts))


# --------------------------------------------------------------------------- noise helpers

def value_noise(size, cells, seed):
    rng = np.random.default_rng(seed)
    grid = rng.random((cells + 1, cells + 1))
    grid[-1, :] = grid[0, :]
    grid[:, -1] = grid[:, 0]
    lin = np.linspace(0, cells, size, endpoint=False)
    xi = lin.astype(int)
    xf = lin - xi
    xf = xf * xf * (3 - 2 * xf)
    a = grid[np.ix_(xi, xi)]
    b = grid[np.ix_(xi, xi + 1)]
    c = grid[np.ix_(xi + 1, xi)]
    d = grid[np.ix_(xi + 1, xi + 1)]
    fx = xf[None, :]
    fy = xf[:, None]
    return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy


def fbm(size, seed, octaves=5, base=4):
    out = np.zeros((size, size))
    amp, total = 1.0, 0.0
    for o in range(octaves):
        out += value_noise(size, base * 2 ** o, seed + o * 31) * amp
        total += amp
        amp *= 0.5
    return out / total


def radial(size):
    lin = np.linspace(-1, 1, size)
    x, y = np.meshgrid(lin, lin)
    return np.sqrt(x * x + y * y), np.arctan2(y, x)


def rgba(r, g, b, a):
    arr = np.stack([r, g, b, a], axis=-1)
    return Image.fromarray(np.clip(arr * 255, 0, 255).astype(np.uint8), "RGBA")


def gray_alpha(alpha, lum=None):
    lum = np.ones_like(alpha) if lum is None else lum
    return rgba(lum, lum, lum, alpha)


# --------------------------------------------------------------------------- VFX textures

def smoke_puff(size, seed):
    r, ang = radial(size)
    n = fbm(size, seed, 5, 3)
    warp = fbm(size, seed + 99, 3, 2)
    edge = 0.66 + 0.3 * (n - 0.5) * 2.0 + 0.12 * (warp - 0.5)
    alpha = np.clip((edge - r) / 0.55, 0, 1)
    alpha = alpha * alpha * (3 - 2 * alpha)
    alpha *= 0.6 + 0.4 * n
    lum = 0.55 + 0.45 * fbm(size, seed + 7, 4, 4)
    return gray_alpha(alpha, lum)


def soft_glow(size, falloff=2.2):
    r, _ = radial(size)
    a = np.clip(1 - r, 0, 1) ** falloff
    return gray_alpha(a)


def core_tex(size):
    r, ang = radial(size)
    n = fbm(size, 5, 4, 6)
    a = np.clip(1 - r, 0, 1) ** 1.6 * (0.75 + 0.5 * n)
    hot = np.clip(1 - r * 2.6, 0, 1)
    return gray_alpha(np.clip(a + hot, 0, 1))


def swirl_tex(size, arms=3, seed=11):
    r, ang = radial(size)
    n = fbm(size, seed, 4, 4)
    s = np.sin(arms * ang + r * 9.0 + n * 3.0) * 0.5 + 0.5
    a = s ** 2.0 * np.clip(1 - r, 0, 1) ** 0.8 * np.clip(r * 3, 0, 1)
    return gray_alpha(np.clip(a * 1.2, 0, 1))


def ring_strip(w, h):
    v = np.linspace(0, 1, h)[:, None]
    u = np.linspace(0, 1, w)[None, :]
    n = value_noise(w, 8, 3)[:h, :] if h <= w else 0
    band = np.exp(-((v - 0.5) ** 2) / 0.02) + 0.35 * np.exp(-((v - 0.5) ** 2) / 0.12)
    a = np.clip(band * (0.8 + 0.4 * (n - 0.5)), 0, 1) * np.ones_like(u)
    return gray_alpha(a)


def beam_tex(w, h):
    x = np.linspace(-1, 1, w)[None, :]
    y = np.linspace(0, 1, h)[:, None]
    n = np.tile(value_noise(h, 8, 21)[:, :1], (1, w))
    n2 = value_noise(max(w, h), 16, 22)[:h, :w]
    core = np.exp(-(x ** 2) / 0.04)
    glow = np.exp(-(x ** 2) / 0.35) * (0.5 + 0.5 * n2)
    streak = (np.sin(y * 40 + n * 6) * 0.5 + 0.5) * np.exp(-(x ** 2) / 0.2) * 0.4
    return gray_alpha(np.clip(core + glow * 0.7 + streak, 0, 1) * np.ones_like(y))


def glyph_circle(size, seed, spokes):
    r, ang = radial(size)
    a = np.zeros_like(r)
    for rad, wid in ((0.95, 0.018), (0.86, 0.01), (0.62, 0.014), (0.40, 0.01)):
        a += np.exp(-((r - rad) ** 2) / (wid ** 2))
    # rune ticks between the two outer rings
    ticks = (np.sin(ang * 48) > 0.85) & (r > 0.87) & (r < 0.94)
    a += ticks * 0.9
    # polygon / star chords
    for k in range(spokes):
        t0 = k * 2 * math.pi / spokes
        t1 = (k + 2) * 2 * math.pi / spokes
        p0 = np.array([math.cos(t0), math.sin(t0)]) * 0.62
        p1 = np.array([math.cos(t1), math.sin(t1)]) * 0.62
        lin = np.linspace(-1, 1, size)
        x, y = np.meshgrid(lin, lin)
        d = p1 - p0
        t = np.clip(((x - p0[0]) * d[0] + (y - p0[1]) * d[1]) / d.dot(d), 0, 1)
        dist = np.hypot(x - (p0[0] + t * d[0]), y - (p0[1] + t * d[1]))
        a += np.exp(-(dist ** 2) / 0.00012)
    # glyph dots on middle ring
    for k in range(spokes * 2):
        t0 = k * math.pi / spokes
        lin = np.linspace(-1, 1, size)
        x, y = np.meshgrid(lin, lin)
        a += np.exp(-((x - math.cos(t0) * 0.74) ** 2 + (y - math.sin(t0) * 0.74) ** 2) / 0.0006)
    return gray_alpha(np.clip(a, 0, 1))


def clock_face(size):
    """Ornate clock dial: concentric rings, 60 minute ticks, 12 hour blades, rotating inner gear teeth and two hands."""
    r, ang = radial(size)
    lin = np.linspace(-1, 1, size)
    x, y = np.meshgrid(lin, lin)
    a = np.zeros_like(r)
    for rad, wid in ((0.97, 0.012), (0.90, 0.008), (0.70, 0.010), (0.52, 0.008), (0.30, 0.012), (0.12, 0.02)):
        a += np.exp(-((r - rad) ** 2) / (wid ** 2))
    minute = (np.abs(((ang / (2 * np.pi) * 60) + 0.5) % 1 - 0.5) < 0.06) & (r > 0.91) & (r < 0.96)
    hour = (np.abs(((ang / (2 * np.pi) * 12) + 0.5) % 1 - 0.5) < 0.035) & (r > 0.72) & (r < 0.89)
    a += minute * 0.9 + hour * 1.0
    # roman-numeral-like blocks between 0.74 and 0.86, offset half an hour
    blocks = (np.abs(((ang / (2 * np.pi) * 12)) % 1 - 0.5) < 0.12) & (r > 0.76) & (r < 0.84)
    a += blocks * 0.45
    gear = (np.sin(ang * 24) > 0.2) & (r > 0.52) & (r < 0.58)
    a += gear * 0.8
    # spokes of the inner wheel
    spokes = (np.abs(np.sin(ang * 3)) < 0.05) & (r > 0.12) & (r < 0.52)
    a += spokes * 0.7
    # hands
    for theta, length, width in ((-1.2, 0.62, 0.025), (0.55, 0.45, 0.035)):
        ux, uy = math.cos(theta), math.sin(theta)
        t = np.clip(x * ux + y * uy, 0, length)
        d = np.hypot(x - t * ux, y - t * uy)
        a += np.exp(-(d ** 2) / (width ** 2)) * (x * ux + y * uy > -0.05)
    return gray_alpha(np.clip(a, 0, 1))


def gear_strip(w, h):
    v = np.linspace(0, 1, h)[:, None]
    u = np.linspace(0, 1, w)[None, :]
    band = np.exp(-((v - 0.5) ** 2) / 0.02)
    teeth = ((np.sin(u * 2 * np.pi * 32) > 0.3) & (v > 0.12) & (v < 0.32)) * 1.0
    ticks = ((np.sin(u * 2 * np.pi * 96) > 0.8) & (v > 0.68) & (v < 0.86)) * 0.8
    edge = np.exp(-((v - 0.1) ** 2) / 0.001) + np.exp(-((v - 0.9) ** 2) / 0.001)
    return gray_alpha(np.clip(band * 0.6 + teeth + ticks + edge * 0.8, 0, 1) * np.ones_like(u))


def lightning_noise(size):
    n = fbm(size, 77, 5, 4)
    a = np.clip(1 - np.abs(n - 0.5) * 18, 0, 1)
    return gray_alpha(a)


def transition_tex(size):
    r, ang = radial(size)
    n = fbm(size, 404, 5, 3)
    s = np.sin(4 * ang + r * 14 + n * 5) * 0.5 + 0.5
    smoke = fbm(size, 405, 5, 4)
    lum = 0.25 * smoke + 0.75 * s * np.clip(1.2 - r, 0, 1) * 0.6
    red = lum * 0.25
    green = lum * 0.55
    blue = np.clip(lum * 1.3 + 0.05, 0, 1)
    alpha = np.clip(0.7 + 0.3 * smoke + (r - 0.6) * 0.6, 0, 1)
    hot = np.clip(1 - r * 3.5, 0, 1)
    return rgba(np.clip(red + hot, 0, 1), np.clip(green + hot, 0, 1), np.clip(blue + hot, 0, 1), np.clip(alpha, 0, 1))


def make_vfx_textures():
    atlas = Image.new("RGBA", (128, 128))
    for i in range(4):
        atlas.paste(smoke_puff(64, 100 + i * 13), ((i % 2) * 64, (i // 2) * 64))
        save(smoke_puff(32, 500 + i * 7), "textures", "particle", f"smoke_{i}.png")
    save(atlas, "textures", "vfx", "smoke_atlas.png")
    save(core_tex(64), "textures", "vfx", "core.png")
    save(Image.new("RGBA", (4, 4), (255, 255, 255, 255)), "textures", "vfx", "white.png")
    save(soft_glow(64, 2.2), "textures", "vfx", "glow.png")
    save(swirl_tex(128), "textures", "vfx", "swirl.png")
    save(ring_strip(128, 32), "textures", "vfx", "ring.png")
    save(beam_tex(32, 128), "textures", "vfx", "beam.png")
    save(glyph_circle(128, 1, 6), "textures", "vfx", "glyph_space.png")
    save(lightning_noise(64), "textures", "vfx", "crackle.png")
    save(clock_face(256), "textures", "vfx", "glyph_time.png")
    save(gear_strip(256, 32), "textures", "vfx", "gear_ring.png")
    save(transition_tex(256), "textures", "gui", "dimensional_transition.png")

    save(soft_glow(16, 1.8), "textures", "particle", "glow.png")
    # spark: a hot centre with a 4 point star
    r, ang = radial(16)
    star = np.clip(1 - r * 1.1, 0, 1) ** 3 + np.clip(1 - r, 0, 1) * (np.abs(np.cos(2 * ang)) ** 16)
    save(gray_alpha(np.clip(star, 0, 1)), "textures", "particle", "spark.png")
    # mote: tiny round dot
    save(gray_alpha(np.clip(1 - r * 1.6, 0, 1) ** 1.2), "textures", "particle", "mote.png")
    # shard: a thin diamond (used for fractures / debris)
    lin = np.linspace(-1, 1, 16)
    x, y = np.meshgrid(lin, lin)
    save(gray_alpha(np.clip(1 - (np.abs(x) * 3 + np.abs(y)), 0, 1) ** 0.6), "textures", "particle", "shard.png")
    for name, sprites in {
        "energy_glow": ["glow"],
        "energy_spark": ["spark"],
        "energy_mote": ["mote"],
        "dimensional_smoke": [f"smoke_{i}" for i in range(4)],
        "energy_shard": ["shard"],
    }.items():
        with open(path("particles", f"{name}.json"), "w") as f:
            json.dump({"textures": [f"infinitygauntlet:{s}" for s in sprites]}, f, indent=2)


# --------------------------------------------------------------------------- item / GeckoLib art

def gem_pixels(rgb, size=16):
    lin = np.linspace(-1, 1, size)
    x, y = np.meshgrid(lin, lin)
    d = np.abs(x) * 0.9 + np.abs(y) * 1.1
    inside = d < 0.95
    facet = np.where(x + y < 0, 1.15, 0.8) * np.where(y < -0.2, 1.1, 1.0)
    hi = np.exp(-((x + 0.3) ** 2 + (y + 0.35) ** 2) / 0.03)
    base = np.array(rgb) / 255.0
    col = np.clip(base[None, None, :] * facet[..., None] * (1.1 - d[..., None] * 0.5) + hi[..., None] * 0.9, 0, 1)
    alpha = inside.astype(float)
    outline = (d > 0.8) & inside
    col[outline] *= 0.55
    return rgba(col[..., 0], col[..., 1], col[..., 2], alpha)


class BoxUvPainter:
    """Builds bedrock box-UV cubes and paints a matching texture/glowmask."""

    def __init__(self, w, h):
        self.w, self.h = w, h
        self.tex = np.zeros((h, w, 4))
        self.glow = np.zeros((h, w, 4))
        self.cursor = [0, 0]
        self.row_h = 0

    def alloc(self, size):
        sx, sy, sz = (int(math.ceil(v)) for v in size)
        need_w, need_h = 2 * (sx + sz), sy + sz
        if self.cursor[0] + need_w > self.w:
            self.cursor = [0, self.cursor[1] + self.row_h]
            self.row_h = 0
        u, v = self.cursor
        if v + need_h > self.h:
            raise RuntimeError("texture atlas overflow")
        self.cursor[0] += need_w
        self.row_h = max(self.row_h, need_h)
        return u, v, need_w, need_h

    def paint(self, u, v, w, h, rgb, glow=False, seed=0):
        n = value_noise(max(w, h, 2) * 2, 4, seed)[:h, :w]
        base = np.array(rgb) / 255.0
        shade = 0.82 + 0.3 * (n - 0.5)
        y = np.linspace(1.08, 0.86, h)[:, None]
        col = np.clip(base[None, None, :] * shade[..., None] * y[..., None], 0, 1)
        self.tex[v:v + h, u:u + w, :3] = col
        self.tex[v:v + h, u:u + w, 3] = 1
        if glow:
            self.glow[v:v + h, u:u + w, :3] = np.clip(col * 1.3 + 0.15, 0, 1)
            self.glow[v:v + h, u:u + w, 3] = 1

    def cube(self, origin, size, rgb, glow=False, inflate=0.0, seed=0):
        u, v, w, h = self.alloc(size)
        self.paint(u, v, w, h, rgb, glow, seed)
        c = {"origin": origin, "size": size, "uv": [u, v]}
        if inflate:
            c["inflate"] = inflate
        return c

    def images(self):
        def img(a):
            return Image.fromarray(np.clip(a * 255, 0, 255).astype(np.uint8), "RGBA")
        return img(self.tex), img(self.glow)


GOLD = (232, 176, 52)
GOLD_DARK = (176, 116, 30)
GOLD_LIGHT = (255, 214, 110)


def bone(name, pivot, cubes=None, parent=None, rotation=None):
    b = {"name": name, "pivot": pivot}
    if parent:
        b["parent"] = parent
    if rotation:
        b["rotation"] = rotation
    if cubes:
        b["cubes"] = cubes
    return b


def make_gauntlet():
    p = BoxUvPainter(128, 128)
    bones = [bone("root", [0, 0, 0])]
    bones.append(bone("cuff", [0, 0, 0], [
        p.cube([-4, 0, -3], [8, 6, 6], GOLD, seed=1),
        p.cube([-4.5, 4.5, -3.5], [9, 1.5, 7], GOLD_DARK, seed=2),
        p.cube([-4.5, 0, -3.5], [9, 1, 7], GOLD_DARK, seed=3),
    ], parent="root"))
    bones.append(bone("hand", [0, 6, 0], [
        p.cube([-4, 6, -2.5], [8, 8, 5], GOLD, seed=4),
        p.cube([-3, 7, -3], [6, 6, 1], GOLD_LIGHT, seed=5),  # raised back plate
    ], parent="root"))
    bones.append(bone("stone_mind", [0, 10, -3], [
        p.cube([-1, 9, -4], [2, 2, 1], STONES["mind"], glow=True, inflate=0.1, seed=6),
    ], parent="hand"))
    for i, (finger, stone) in enumerate([("index", "power"), ("middle", "space"), ("ring", "reality"), ("pinky", "soul")]):
        x = -4 + i * 2
        length = 4 if finger in ("middle", "ring") else 3
        if finger == "pinky":
            length = 3
        bones.append(bone(finger, [x + 1, 14, 0], [
            p.cube([x + 0.1, 14, -1], [1.8, length, 2], GOLD, seed=10 + i),
        ], parent="hand"))
        bones.append(bone(f"{finger}_tip", [x + 1, 14 + length, 0], [
            p.cube([x + 0.2, 14 + length, -0.9], [1.6, 3, 1.8], GOLD_DARK, seed=20 + i),
        ], parent=finger))
        bones.append(bone(f"stone_{stone}", [x + 1, 15.5, -1], [
            p.cube([x + 0.5, 15, -1.8], [1, 1, 1], STONES[stone], glow=True, inflate=0.15, seed=30 + i),
        ], parent=finger))
    bones.append(bone("thumb", [-4, 8, 0], [
        p.cube([-6, 8, -1], [2, 4, 2], GOLD, seed=40),
    ], parent="hand", rotation=[0, 0, 25]))
    bones.append(bone("thumb_tip", [-5, 12, 0], [
        p.cube([-5.9, 12, -0.9], [1.8, 3, 1.8], GOLD_DARK, seed=41),
    ], parent="thumb"))
    bones.append(bone("stone_time", [-5, 10, -1], [
        p.cube([-5.5, 9.5, -1.8], [1, 1, 1], STONES["time"], glow=True, inflate=0.15, seed=42),
    ], parent="thumb"))
    # the aura shell is an invisible-by-default bone the renderer scales for charge states
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.infinity_gauntlet",
                "texture_width": p.w, "texture_height": p.h,
                "visible_bounds_width": 3, "visible_bounds_height": 3, "visible_bounds_offset": [0, 1, 0],
            },
            "bones": bones,
        }],
    }
    with open(path("geo", "item", "infinity_gauntlet.geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
    tex, glow = p.images()
    save(tex, "textures", "item", "infinity_gauntlet.png")
    save(glow, "textures", "item", "infinity_gauntlet_glowmask.png")


def make_stone_model():
    p = BoxUvPainter(32, 32)
    white = (235, 235, 235)
    geo = {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {"identifier": "geometry.infinity_stone", "texture_width": 32, "texture_height": 32,
                            "visible_bounds_width": 2, "visible_bounds_height": 2, "visible_bounds_offset": [0, 0.5, 0]},
            "bones": [
                bone("gem", [0, 8, 0], [
                    p.cube([-2, 6, -2], [4, 4, 4], white, glow=True, seed=1),
                ]),
                bone("facet_a", [0, 8, 0], [p.cube([-1.5, 5.5, -1.5], [3, 5, 3], white, glow=True, seed=2)], parent="gem", rotation=[45, 0, 45]),
                bone("facet_b", [0, 8, 0], [p.cube([-1.5, 6.5, -1.5], [3, 3, 3], white, glow=True, seed=3)], parent="gem", rotation=[0, 45, 0]),
            ],
        }],
    }
    with open(path("geo", "item", "infinity_stone.geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
    tex, _ = p.images()
    base = np.asarray(tex).astype(float) / 255.0
    for stone, rgb in STONES.items():
        col = base.copy()
        col[..., :3] = np.clip(col[..., :3] * (np.array(rgb) / 255.0) * 1.15 + 0.05 * col[..., 3:4], 0, 1)
        img = Image.fromarray((col * 255).astype(np.uint8), "RGBA")
        save(img, "textures", "item", f"{stone}_stone_model.png")
        glow = col.copy()
        glow[..., :3] = np.clip(glow[..., :3] * 1.25 + 0.1, 0, 1)
        save(Image.fromarray((glow * 255).astype(np.uint8), "RGBA"), "textures", "item", f"{stone}_stone_model_glowmask.png")
        save(gem_pixels(rgb), "textures", "item", f"{stone}_stone.png")


def anim_finger_curl(degrees, t):
    return {f: {"rotation": {str(t): [degrees, 0, 0]}} for f in ("index", "middle", "ring", "pinky")}


def make_gauntlet_animations():
    fingers = ("index", "middle", "ring", "pinky")
    anims = {}
    anims["animation.infinity_gauntlet.idle"] = {
        "loop": True, "animation_length": 4.0,
        "bones": {
            **{f: {"rotation": [f"math.sin(query.anim_time * 90 + {i * 25}) * 4 - 6", 0, 0]} for i, f in enumerate(fingers)},
            "thumb": {"rotation": ["math.sin(query.anim_time * 90) * 3", 0, 0]},
            "hand": {"position": [0, "math.sin(query.anim_time * 90) * 0.15", 0]},
        },
    }
    anims["animation.infinity_gauntlet.insert"] = {
        "animation_length": 0.8,
        "bones": {
            "hand": {"rotation": {"0.0": [0, 0, 0], "0.2": [-12, 8, 0], "0.5": [4, -4, 0], "0.8": [0, 0, 0]}},
            **{f: {"rotation": {"0.0": [0, 0, 0], "0.25": [-55, 0, 0], "0.55": [-10, 0, 0], "0.8": [0, 0, 0]}} for f in fingers},
        },
    }
    anims["animation.infinity_gauntlet.charge"] = {
        "loop": True, "animation_length": 1.0,
        "bones": {
            **{f: {"rotation": [f"-80 + math.sin(query.anim_time * 2400 + {i * 40}) * 2", 0, 0]} for i, f in enumerate(fingers)},
            "thumb": {"rotation": [-30, 0, 40]},
            "hand": {"position": ["math.sin(query.anim_time * 3000) * 0.2", "math.cos(query.anim_time * 2700) * 0.2", 0]},
        },
    }
    anims["animation.infinity_gauntlet.cast"] = {
        "animation_length": 0.7,
        "bones": {
            "hand": {"rotation": {"0.0": [0, 0, 0], "0.12": [20, 0, 0], "0.3": [-25, 0, 0], "0.7": [0, 0, 0]},
                     "position": {"0.0": [0, 0, 0], "0.12": [0, -0.5, 0.5], "0.3": [0, 0.6, -1.2], "0.7": [0, 0, 0]}},
            **{f: {"rotation": {"0.0": [-10, 0, 0], "0.12": [-85, 0, 0], "0.3": [15, 0, 0], "0.7": [0, 0, 0]}} for f in fingers},
            "thumb": {"rotation": {"0.0": [0, 0, 0], "0.12": [-30, 0, 30], "0.3": [10, 0, -10], "0.7": [0, 0, 0]}},
        },
    }
    anims["animation.infinity_gauntlet.snap"] = {
        "animation_length": 2.0,
        "bones": {
            "hand": {"rotation": {"0.0": [0, 0, 0], "0.6": [-10, 15, 0], "1.2": [-12, 18, 0], "1.3": [-4, 10, 0], "2.0": [0, 0, 0]}},
            "middle": {"rotation": {"0.0": [0, 0, 0], "0.6": [-40, 0, 0], "1.2": [-45, 0, 0], "1.25": [-95, 0, 0], "2.0": [0, 0, 0]}},
            "thumb": {"rotation": {"0.0": [0, 0, 0], "0.6": [-40, 0, 55], "1.2": [-45, 0, 60], "1.25": [-10, 0, 10], "2.0": [0, 0, 0]}},
            "index": {"rotation": {"0.0": [0, 0, 0], "0.6": [-20, 0, 0], "1.3": [-10, 0, 0], "2.0": [0, 0, 0]}},
            "ring": {"rotation": {"0.0": [0, 0, 0], "0.6": [-70, 0, 0], "2.0": [0, 0, 0]}},
            "pinky": {"rotation": {"0.0": [0, 0, 0], "0.6": [-75, 0, 0], "2.0": [0, 0, 0]}},
        },
    }
    anims["animation.infinity_gauntlet.overload"] = {
        "animation_length": 0.6,
        "bones": {"hand": {"position": {"0.0": [0, 0, 0], "0.1": [0.4, 0, 0], "0.2": [-0.4, 0.2, 0], "0.3": [0.3, -0.2, 0], "0.6": [0, 0, 0]}}},
    }
    for stone in STONES:
        anims[f"animation.infinity_gauntlet.activate_{stone}"] = {
            "animation_length": 0.9,
            "bones": {
                f"stone_{stone}": {"scale": {"0.0": [1, 1, 1], "0.15": [2.1, 2.1, 2.1], "0.45": [1.5, 1.5, 1.5], "0.9": [1, 1, 1]},
                                   "rotation": {"0.0": [0, 0, 0], "0.9": [0, 360, 0]}},
                "hand": {"rotation": {"0.0": [0, 0, 0], "0.15": [-8, 0, 0], "0.9": [0, 0, 0]}},
            },
        }
    with open(path("animations", "item", "infinity_gauntlet.animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)

    stone_anims = {
        "animation.infinity_stone.idle": {
            "loop": True, "animation_length": 6.0,
            "bones": {"gem": {"rotation": [0, "query.anim_time * 60", 0],
                              "position": [0, "math.sin(query.anim_time * 60) * 0.6", 0],
                              "scale": ["1 + math.sin(query.anim_time * 240) * 0.04"] * 3}},
        }
    }
    with open(path("animations", "item", "infinity_stone.animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": stone_anims}, f, indent=1)


# --------------------------------------------------------------------------- sounds

SR = 44100


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def noise(sec):
    return RNG.standard_normal(int(SR * sec))


def lowpass(x, cutoff):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= 1 / (1 + (f / cutoff) ** 4)
    return np.fft.irfft(spec, len(x))


def bandpass(x, lo, hi):
    spec = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    spec *= (1 / (1 + (lo / np.maximum(f, 1)) ** 4)) * (1 / (1 + (f / hi) ** 4))
    return np.fft.irfft(spec, len(x))


def sweep(sec, f0, f1, curve=1.0, wave_fn=np.sin):
    t = t_axis(sec)
    f = f0 + (f1 - f0) * (t / sec) ** curve
    return wave_fn(2 * np.pi * np.cumsum(f) / SR)


def env(sec, attack, release, shape=2.0):
    t = t_axis(sec)
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    r = np.clip((sec - t) / max(release, 1e-4), 0, 1) ** shape
    return a * r


def crackle(sec, density, decay=0.004, seed=0):
    rng = np.random.default_rng(seed)
    n = int(SR * sec)
    out = np.zeros(n)
    hits = rng.random(n) < density / SR
    burst_len = int(SR * decay * 6)
    burst = rng.standard_normal(burst_len) * np.exp(-np.arange(burst_len) / (SR * decay))
    for i in np.nonzero(hits)[0]:
        seg = out[i:i + burst_len]
        seg += burst[:len(seg)] * rng.uniform(0.3, 1.0)
    return bandpass(out, 1500, 9000)


def boom(sec, f0=60, f1=28):
    return sweep(sec, f0, f1, 0.6) * env(sec, 0.005, sec * 0.95, 1.6) + lowpass(noise(sec), 180) * env(sec, 0.002, sec * 0.6, 3) * 3


def fit(x, sec):
    n = int(SR * sec)
    return np.pad(x, (0, max(0, n - len(x))))[:n]


def norm(x, peak=0.9):
    m = np.max(np.abs(x))
    return x if m == 0 else x / m * peak


def write_ogg(name, samples):
    samples = norm(samples)
    tmp = tempfile.mkdtemp()
    try:
        wav_path = os.path.join(tmp, "s.wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes((samples * 32767).astype(np.int16).tobytes())
        out = path("sounds", f"{name}.ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", out], check=True)
    finally:
        shutil.rmtree(tmp)


def make_sounds():
    sounds = {}

    def add(name, samples, subtitle):
        write_ogg(name, samples)
        sounds[name] = {"subtitle": f"subtitles.infinitygauntlet.{name}", "sounds": [{"name": f"infinitygauntlet:{name}"}]}
        SUBTITLES[f"subtitles.infinitygauntlet.{name}"] = subtitle

    # Space Stone ----------------------------------------------------------
    d = 3.2
    open_ = (bandpass(noise(d), 200, 2500) * env(d, 1.6, 1.4) * sweep(d, 0.3, 1.0) ** 2 * 0.8
             + sweep(d, 40, 140, 2.0) * env(d, 1.2, 1.8) * 0.9
             + fit(np.zeros(int(SR * 1.3)), d) + fit(np.concatenate([np.zeros(int(SR * 1.3)), boom(1.9, 70, 25) * 1.4]), d)
             + fit(np.concatenate([np.zeros(int(SR * 1.0)), crackle(2.2, 60, seed=1)]), d) * 0.6)
    add("space_portal_open", open_, "Dimensional rupture tears open")

    d = 2.4
    swell = bandpass(noise(d), 150, 3000)[::-1] * env(d, 1.9, 0.3, 1) * 0.9
    suck = sweep(d, 300, 30, 0.5) * env(d, 0.4, 0.5) * 0.6
    thump = fit(np.concatenate([np.zeros(int(SR * 1.95)), boom(0.45, 90, 30) * 1.8]), d)
    add("space_portal_close", swell + suck + thump, "Dimensional rupture implodes")

    d = 4.0
    hum = (sweep(d, 55, 55) * 0.5 + sweep(d, 82.5, 82.5) * 0.25 + bandpass(noise(d), 80, 600) * 0.6) * (0.85 + 0.15 * np.sin(2 * np.pi * 0.5 * t_axis(d)))
    hum += crackle(d, 12, seed=4) * 0.35
    fade = np.minimum(1, np.minimum(t_axis(d), d - t_axis(d)) / 0.05)
    add("space_portal_ambient", hum * fade, "Dimensional rupture rumbles")

    d = 1.1
    add("space_teleport", bandpass(noise(d), 400, 6000) * env(d, 0.35, 0.7) + sweep(d, 900, 120, 0.7) * env(d, 0.05, 1.0) * 0.4 + crackle(d, 40, seed=5) * 0.4, "Space folds")
    d = 0.6
    add("space_blink", sweep(d, 1800, 300, 0.4) * env(d, 0.01, 0.55) * 0.6 + bandpass(noise(d), 2000, 9000) * env(d, 0.005, 0.3) * 0.6 + boom(d, 120, 50) * 0.4, "Blink")
    d = 0.5
    add("space_bolt_fire", sweep(d, 1400, 400, 0.6, lambda p: np.sign(np.sin(p))) * env(d, 0.005, 0.45) * 0.3 + crackle(d, 120, seed=6) * 0.7, "Space bolt fires")
    d = 0.9
    add("space_bolt_impact", boom(d, 140, 40) + crackle(d, 80, seed=7) * env(d, 0.001, 0.5), "Space bolt impacts")
    d = 3.0
    add("space_rift", lowpass(noise(d), 260) * 3 * env(d, 0.6, 1.2) + sweep(d, 35, 22) * env(d, 0.4, 1.5) + crackle(d, 25, seed=8) * 0.5, "Spatial rift grinds")
    d = 1.8
    add("space_shockwave", boom(d, 80, 22) * 1.3 + bandpass(noise(d), 300, 4000) * env(d, 0.01, 1.2, 3) * 0.7, "Spatial shockwave")
    d = 2.6
    add("space_compression", sweep(d, 60, 400, 2.0) * env(d, 0.4, 0.2, 1) * 0.6 + bandpass(noise(d), 300, 3000) * env(d, 2.2, 0.4, 1) * 0.6 + crackle(d, 50, seed=9) * 0.5, "Space compresses")
    d = 0.7
    add("space_arc", crackle(d, 300, 0.002, seed=10) * env(d, 0.01, 0.5) + bandpass(noise(d), 3000, 10000) * env(d, 0.005, 0.2) * 0.3, "Energy arc crackles")
    d = 1.6
    add("space_prison", sweep(d, 220, 440, 1.0) * env(d, 0.1, 1.2) * 0.4 + sweep(d, 330, 660, 1.0) * env(d, 0.2, 1.2) * 0.3 + crackle(d, 40, seed=11) * 0.4, "Spatial prison locks")
    d = 1.3
    add("space_swap", sweep(d, 300, 1200, 0.8) * env(d, 0.3, 0.6) * 0.4 + sweep(d, 1200, 300, 0.8) * env(d, 0.3, 0.6) * 0.4 + crackle(d, 60, seed=12) * 0.5, "Positions swap")

    # Time Stone -----------------------------------------------------------
    def tick_click(sec, period, pitch=2400, seed=0):
        out = np.zeros(int(SR * sec))
        click = np.sin(2 * np.pi * pitch * t_axis(0.03)) * np.exp(-t_axis(0.03) * 180)
        tock = np.sin(2 * np.pi * pitch * 0.7 * t_axis(0.03)) * np.exp(-t_axis(0.03) * 160)
        i, k = 0, 0
        while i < len(out):
            c = click if k % 2 == 0 else tock
            seg = out[i:i + len(c)]
            seg += c[:len(seg)]
            i += int(SR * period)
            k += 1
        return out

    d = 2.4
    stop = (sweep(d, 900, 40, 0.35) * env(d, 0.01, 2.0) * 0.5 + bandpass(noise(d), 300, 6000)[::-1] * env(d, 0.6, 0.2, 1) * 0.5
            + fit(np.concatenate([np.zeros(int(SR * 0.6)), boom(1.8, 70, 30)]), d) + fit(tick_click(0.7, 0.16), d) * 0.5)
    add("time_freeze", stop, "Time stops")
    d = 1.4
    add("time_resume", sweep(d, 40, 600, 2.0) * env(d, 0.6, 0.6) * 0.5 + bandpass(noise(d), 500, 8000) * env(d, 0.9, 0.4, 1) * 0.5
        + fit(np.concatenate([np.zeros(int(SR * 0.9)), boom(0.5, 120, 60)]), d), "Time resumes")
    d = 4.0
    wob = 1 + 0.25 * np.sin(2 * np.pi * 0.35 * t_axis(d))
    slow = (np.sin(2 * np.pi * np.cumsum(110 * wob) / SR) * 0.4 + np.sin(2 * np.pi * np.cumsum(165 * wob) / SR) * 0.2
            + tick_click(d, 0.5, 1400) * 0.6)
    fade = np.minimum(1, np.minimum(t_axis(d), d - t_axis(d)) / 0.05)
    add("time_field_loop", slow * fade, "Time crawls")
    d = 1.6
    add("time_field_open", sweep(d, 300, 80, 0.5) * env(d, 0.05, 1.4) * 0.6 + fit(tick_click(1.2, 0.09, 2000), d) * env(d, 0.01, 1.2)
        + boom(d, 60, 35) * 0.5, "Temporal field forms")
    d = 1.0
    add("time_accelerate", sweep(d, 200, 2400, 1.4) * env(d, 0.05, 0.3) * 0.4 + fit(tick_click(1.0, 0.04, 2600), d) * 0.6
        + bandpass(noise(d), 1500, 9000) * env(d, 0.6, 0.3, 1) * 0.3, "Time accelerates")
    d = 2.2
    rev = (bandpass(noise(d), 200, 5000) * env(d, 0.2, 1.9, 1) * 0.6 + sweep(d, 1200, 120, 0.6) * env(d, 0.1, 1.8) * 0.4
           + tick_click(d, 0.07, 1800) * 0.4)[::-1]
    add("time_rewind", rev, "Time rewinds")
    d = 1.5
    add("time_echo", sweep(d, 700, 350, 1.0) * env(d, 0.02, 1.3) * 0.3 + sweep(d, 1050, 525, 1.0) * env(d, 0.15, 1.2) * 0.2
        + fit(tick_click(0.6, 0.15, 1600), d) * 0.4, "Temporal echo")
    d = 1.2
    add("time_loop", sweep(d, 900, 300, 0.5) * env(d, 0.01, 0.5) * 0.4 + sweep(d, 300, 900, 2.0) * env(d, 0.5, 0.6) * 0.4
        + fit(tick_click(1.2, 0.1, 2200), d) * 0.4, "Time loops")
    d = 1.8
    add("time_barrier", sweep(d, 180, 260, 1.0) * env(d, 0.1, 1.2) * 0.5 + fit(tick_click(1.8, 0.12, 1700), d) * 0.5
        + bandpass(noise(d), 800, 6000) * env(d, 0.05, 0.6, 2) * 0.3, "Chronological barrier rises")
    d = 0.5
    add("time_suspend", sweep(d, 1800, 400, 0.4) * env(d, 0.005, 0.45) * 0.5 + crackle(d, 60, seed=21) * 0.3, "Projectile suspended")
    d = 3.0
    glass = crackle(d, 140, 0.0015, seed=22) * env(d, 0.01, 2.6, 1.5)
    add("time_fracture", glass * 1.2 + sweep(d, 50, 30) * env(d, 0.3, 2.5) * 0.6 + bandpass(noise(d), 2000, 10000) * env(d, 0.01, 1.0, 3) * 0.5,
        "Time fractures")
    d = 1.6
    add("time_shatter", boom(d, 90, 25) * 1.2 + crackle(d, 400, 0.001, seed=23) * env(d, 0.001, 0.7, 2) * 1.2, "Time shatters")

    # Gauntlet ---------------------------------------------------------------
    d = 2.2
    charge = sum(sweep(d, f, f * 3, 2.0) * (0.35 / (i + 1)) for i, f in enumerate((70, 140, 210, 280)))
    charge = charge * env(d, 2.0, 0.15, 1) + bandpass(noise(d), 500, 5000) * env(d, 2.0, 0.15, 1) * 0.3
    add("gauntlet_charge", charge, "Gauntlet charges")
    d = 1.2
    chime = sum(np.sin(2 * np.pi * f * t_axis(d)) * np.exp(-t_axis(d) * (3 + i)) * (0.5 / (i + 1)) for i, f in enumerate((1318, 1975, 2637, 3951)))
    click = fit(boom(0.15, 200, 90) * 1.2, d)
    add("stone_insert", chime + click, "Infinity Stone locks into place")
    d = 1.0
    add("gauntlet_cast", sweep(d, 200, 60, 0.5) * env(d, 0.01, 0.9) * 0.6 + bandpass(noise(d), 300, 5000) * env(d, 0.02, 0.5, 2.5) * 0.7 + crackle(d, 70, seed=13) * 0.3, "Gauntlet unleashes power")
    d = 0.5
    add("gauntlet_fail", sweep(d, 300, 120) * env(d, 0.01, 0.4) * 0.6 + crackle(d, 30, seed=14) * 0.3, "Gauntlet fizzles")
    d = 0.25
    add("gauntlet_select", np.sin(2 * np.pi * 1760 * t_axis(d)) * np.exp(-t_axis(d) * 18) * 0.6 + np.sin(2 * np.pi * 2637 * t_axis(d)) * np.exp(-t_axis(d) * 24) * 0.3, "Ability selected")

    with open(path("sounds.json"), "w") as f:
        json.dump(sounds, f, indent=2)


SUBTITLES = {}


def main():
    if shutil.which("ffmpeg") is None:
        print("ffmpeg is required for sound generation", file=sys.stderr)
        sys.exit(1)
    make_vfx_textures()
    make_gauntlet()
    make_stone_model()
    make_gauntlet_animations()
    make_sounds()
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "generated_subtitles.json"), "w") as f:
        json.dump(SUBTITLES, f, indent=2, sort_keys=True)
    print("assets generated in", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
