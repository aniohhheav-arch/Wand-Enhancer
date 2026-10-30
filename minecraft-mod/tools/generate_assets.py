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
    def kf(frames):
        return {str(k): v for k, v in frames}
    fingers4 = ("index", "middle", "ring", "pinky")
    anims["animation.infinity_gauntlet.power_charge"] = {"loop": True, "animation_length": 0.6, "bones": {
        **{f: {"rotation": [f"-95 + math.sin(query.anim_time * 3000 + {i * 60}) * 3", 0, 0]} for i, f in enumerate(fingers4)},
        "thumb": {"rotation": [-40, 0, 50]},
        "hand": {"position": ["math.sin(query.anim_time * 4000) * 0.35", "math.cos(query.anim_time * 3600) * 0.35", 0],
                 "rotation": [-12, 0, 0]},
        "stone_power": {"scale": ["1.8 + math.sin(query.anim_time * 1800) * 0.6"] * 3}}}
    anims["animation.infinity_gauntlet.power_fire"] = {"animation_length": 0.8, "bones": {
        "hand": {"rotation": kf([(0.0, [-12, 0, 0]), (0.05, [18, 0, 0]), (0.25, [-6, 0, 0]), (0.8, [0, 0, 0])]),
                 "position": kf([(0.0, [0, 0, 0]), (0.05, [0, 0.6, 1.6]), (0.3, [0, 0, 0.3]), (0.8, [0, 0, 0])])},
        **{f: {"rotation": kf([(0.0, [-95, 0, 0]), (0.06, [25, 0, 0]), (0.8, [0, 0, 0])])} for f in fingers4},
        "stone_power": {"scale": kf([(0.0, [2.6, 2.6, 2.6]), (0.3, [1.2, 1.2, 1.2]), (0.8, [1, 1, 1])])}}}
    anims["animation.infinity_gauntlet.power_punch"] = {"animation_length": 0.9, "bones": {
        "hand": {"position": kf([(0.0, [0, 0, 0]), (0.2, [0, -1.2, -3.5]), (0.32, [0, 0.4, 3.2]), (0.5, [0, 0, 2.2]), (0.9, [0, 0, 0])]),
                 "rotation": kf([(0.0, [0, 0, 0]), (0.2, [-25, 15, 0]), (0.32, [10, -5, 0]), (0.9, [0, 0, 0])])},
        **{f: {"rotation": kf([(0.0, [0, 0, 0]), (0.15, [-100, 0, 0]), (0.7, [-100, 0, 0]), (0.9, [0, 0, 0])])} for f in fingers4},
        "thumb": {"rotation": kf([(0.0, [0, 0, 0]), (0.15, [-50, 0, 60]), (0.7, [-50, 0, 60]), (0.9, [0, 0, 0])])}}}
    anims["animation.infinity_gauntlet.power_slam"] = {"animation_length": 1.0, "bones": {
        "hand": {"rotation": kf([(0.0, [0, 0, 0]), (0.3, [-60, 0, 0]), (0.45, [50, 0, 0]), (0.6, [35, 0, 0]), (1.0, [0, 0, 0])]),
                 "position": kf([(0.0, [0, 0, 0]), (0.3, [0, 2, -1]), (0.45, [0, -2.5, 1.5]), (1.0, [0, 0, 0])])},
        **{f: {"rotation": kf([(0.0, [0, 0, 0]), (0.2, [-100, 0, 0]), (0.8, [-100, 0, 0]), (1.0, [0, 0, 0])])} for f in fingers4}}}
    anims["animation.infinity_gauntlet.power_spread"] = {"animation_length": 1.6, "bones": {
        "hand": {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [-30, 0, 25]), (1.3, [-35, 0, 30]), (1.4, [30, 0, -20]), (1.6, [0, 0, 0])])},
        **{f: {"rotation": kf([(0.0, [0, 0, 0]), (0.4, [25, 0, 0]), (1.3, [30, 0, 0]), (1.4, [-90, 0, 0]), (1.6, [0, 0, 0])])} for f in fingers4},
        "stone_power": {"scale": kf([(0.0, [1, 1, 1]), (1.3, [3, 3, 3]), (1.45, [1, 1, 1])])}}}
    anims["animation.infinity_gauntlet.power_raise"] = {"animation_length": 1.0, "bones": {
        "hand": {"rotation": kf([(0.0, [0, 0, 0]), (0.25, [-55, 0, 0]), (0.75, [-60, 0, 0]), (1.0, [0, 0, 0])]),
                 "position": kf([(0.0, [0, 0, 0]), (0.25, [0, 2.5, 0]), (1.0, [0, 0, 0])])},
        **{f: {"rotation": kf([(0.0, [0, 0, 0]), (0.2, [15, 0, 0]), (0.8, [15, 0, 0]), (1.0, [0, 0, 0])])} for f in fingers4}}}
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


def make_nexus():
    """Consciousness Nexus: plinth, console pillar, floating crystal, gold/blue rings, rune plates, cables, screen frame.
    Model space: block footprint is x/z -8..8, y 0..16; the holographic screen (drawn by the renderer) is 64x36 px,
    centred 54 px up and 6 px behind the block centre."""
    p = BoxUvPainter(256, 256)
    DARK = (46, 48, 62)
    STEEL = (96, 104, 128)
    BLUE = (60, 150, 255)
    GOLD_E = (255, 205, 80)
    bones = [bone("root", [0, 0, 0])]
    bones.append(bone("base", [0, 0, 0], [
        p.cube([-12, 0, -12], [24, 3, 24], DARK, seed=1),
        p.cube([-10, 3, -10], [20, 2, 20], STEEL, seed=2),
        p.cube([-12.5, 2.2, -12.5], [25, 0.6, 25], GOLD_DARK, seed=3),
    ], parent="root"))
    for i, (x, z) in enumerate(((-10, -10), (10, -10), (-10, 10), (10, 10))):
        bones.append(bone(f"rune_{i}", [x, 3, z], [p.cube([x - 1.5, 3, z - 1.5], [3, 0.6, 3], GOLD_E, glow=True, seed=10 + i)], parent="base"))
    bones.append(bone("pillar", [0, 5, 0], [
        p.cube([-3, 5, -3], [6, 16, 6], STEEL, seed=20),
        p.cube([-3.5, 9, -3.5], [7, 1.2, 7], GOLD, seed=21),
        p.cube([-3.5, 15, -3.5], [7, 1.2, 7], GOLD, seed=22),
        p.cube([-1, 6, -3.3], [2, 14, 0.5], BLUE, glow=True, seed=23),
        p.cube([-1, 6, 2.8], [2, 14, 0.5], BLUE, glow=True, seed=24),
    ], parent="root"))
    # angled console desk on the front (+z faces the operator after block rotation)
    bones.append(bone("console", [0, 18, 4], [
        p.cube([-8, 17, 3], [16, 1.5, 7], DARK, seed=30),
        p.cube([-7, 18.5, 4], [14, 0.4, 5], BLUE, glow=True, seed=31),
        p.cube([-2, 18.9, 5], [4, 0.6, 3], GOLD_E, glow=True, seed=32),
    ], parent="pillar", rotation=[-25, 0, 0]))
    bones.append(bone("crystal_anchor", [0, 28, 0], parent="root"))
    bones.append(bone("crystal", [0, 28, 0], [
        p.cube([-2.5, 25.5, -2.5], [5, 5, 5], BLUE, glow=True, seed=40),
        p.cube([-1.6, 24.5, -1.6], [3.2, 7, 3.2], (150, 210, 255), glow=True, seed=41),
    ], parent="crystal_anchor", rotation=[45, 0, 45]))
    def ring(name, radius, count, color, size, parent_rot):
        cubes = []
        for k in range(count):
            a = 2 * math.pi * k / count
            cx, cz = math.cos(a) * radius, math.sin(a) * radius
            c = p.cube([cx - size[0] / 2, 28 - size[1] / 2, cz - size[2] / 2], list(size), color, glow=True, seed=50 + k)
            c["pivot"] = [cx, 28, cz]
            c["rotation"] = [0, -math.degrees(a), 0]
            cubes.append(c)
        bones.append(bone(name, [0, 28, 0], cubes, parent="crystal_anchor", rotation=parent_rot))
    ring("ring_gold", 8, 14, GOLD_E, (1.2, 0.8, 3.4), [12, 0, 0])
    ring("ring_blue", 11, 18, BLUE, (1.0, 0.7, 3.6), [-20, 0, 15])
    # neural cables from the base corners up to the screen frame corners
    cables = []
    for i, (x0, z0, x1, y1) in enumerate(((-9, -9, -30, 38), (9, -9, 30, 38))):
        length = math.dist((x0, 3, z0), (x1, y1, -6))
        c = p.cube([x0 - 0.6, 3, z0 - 0.6], [1.2, length, 1.2], BLUE, glow=True, seed=60 + i)
        dx, dy, dz = x1 - x0, y1 - 3, -6 - z0
        c["pivot"] = [x0, 3, z0]
        c["rotation"] = [math.degrees(math.atan2(dz, dy)), 0, -math.degrees(math.atan2(dx, math.hypot(dy, dz)))]
        cables.append(c)
    bones.append(bone("cables", [0, 3, 0], cables, parent="root"))
    # screen frame: 64x36 opening centred at (0, 46, -6)
    W, H, T, Y, Z = 64, 36, 2, 54, -6
    bones.append(bone("screen_frame", [0, Y, Z], [
        p.cube([-W / 2 - T, Y + H / 2, Z - 1], [W + 2 * T, T, 2], GOLD, seed=70),
        p.cube([-W / 2 - T, Y - H / 2 - T, Z - 1], [W + 2 * T, T, 2], GOLD, seed=71),
        p.cube([-W / 2 - T, Y - H / 2, Z - 1], [T, H, 2], GOLD, seed=72),
        p.cube([W / 2, Y - H / 2, Z - 1], [T, H, 2], GOLD, seed=73),
        p.cube([-W / 2 - T - 1, Y - H / 2 - T - 1, Z - 1.6], [4, 4, 1], BLUE, glow=True, seed=74),
        p.cube([W / 2 - T + 1, Y - H / 2 - T - 1, Z - 1.6], [4, 4, 1], BLUE, glow=True, seed=75),
        p.cube([-6, Y + H / 2 + T, Z - 0.8], [12, 2, 1.6], DARK, seed=76),
        p.cube([-4, Y + H / 2 + T + 0.5, Z + 0.9], [8, 1, 0.3], GOLD_E, glow=True, seed=77),
    ], parent="root"))
    # authored with the console on +z; GeckoLib's unrotated (north-facing) block model faces -z, so mirror along z
    def mirror_z(obj):
        if "pivot" in obj:
            obj["pivot"] = [obj["pivot"][0], obj["pivot"][1], -obj["pivot"][2]]
        if "rotation" in obj:
            r = obj["rotation"]
            obj["rotation"] = [-r[0], -r[1], r[2]]
        for c in obj.get("cubes", []):
            c["origin"] = [c["origin"][0], c["origin"][1], -(c["origin"][2] + c["size"][2])]
            mirror_z(c)
    for b in bones:
        mirror_z(b)
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.consciousness_nexus", "texture_width": p.w, "texture_height": p.h,
                        "visible_bounds_width": 6, "visible_bounds_height": 5, "visible_bounds_offset": [0, 2, 0]},
        "bones": bones}]}
    with open(path("geo", "block", "consciousness_nexus.geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
    tex, glow = p.images()
    save(tex, "textures", "block", "consciousness_nexus.png")
    save(glow, "textures", "block", "consciousness_nexus_glowmask.png")
    anims = {
        "animation.consciousness_nexus.standby": {"loop": True, "animation_length": 8.0, "bones": {
            "crystal_anchor": {"position": [0, "math.sin(query.anim_time * 45) * 0.6", 0]},
            "crystal": {"rotation": [45, "query.anim_time * 20", 45]},
            "ring_gold": {"rotation": [12, "query.anim_time * 15", 0]},
            "ring_blue": {"rotation": [-20, "-query.anim_time * 10", 15]}}},
        "animation.consciousness_nexus.active": {"loop": True, "animation_length": 4.0, "bones": {
            "crystal_anchor": {"position": [0, "1.5 + math.sin(query.anim_time * 180) * 0.8", 0]},
            "crystal": {"rotation": [45, "query.anim_time * 120", 45],
                        "scale": ["1.15 + math.sin(query.anim_time * 540) * 0.08"] * 3},
            "ring_gold": {"rotation": ["12 + math.sin(query.anim_time * 90) * 10", "query.anim_time * 140", 0]},
            "ring_blue": {"rotation": ["-20 + math.cos(query.anim_time * 90) * 12", "-query.anim_time * 100", 15]},
            "rune_0": {"scale": [1, "1 + math.sin(query.anim_time * 720) * 0.6", 1]},
            "rune_3": {"scale": [1, "1 + math.cos(query.anim_time * 720) * 0.6", 1]}}},
        "animation.consciousness_nexus.unstable": {"loop": True, "animation_length": 1.0, "bones": {
            "crystal_anchor": {"position": ["math.sin(query.anim_time * 4000) * 0.6", "1.5 + math.cos(query.anim_time * 3300) * 0.6", 0]},
            "crystal": {"rotation": [45, "query.anim_time * 400", 45]},
            "ring_gold": {"rotation": ["12 + math.sin(query.anim_time * 2000) * 20", "query.anim_time * 300", 0]},
            "ring_blue": {"rotation": ["-20 + math.cos(query.anim_time * 1700) * 25", "-query.anim_time * 260", 15]},
            "screen_frame": {"position": ["math.sin(query.anim_time * 5000) * 0.3", 0, 0]}}},
        "animation.consciousness_nexus.power_on": {"animation_length": 1.5, "bones": {
            "crystal": {"scale": {"0.0": [0.4, 0.4, 0.4], "0.5": [1.6, 1.6, 1.6], "1.0": [1.0, 1.0, 1.0]},
                        "rotation": {"0.0": [45, 0, 45], "1.5": [45, 720, 45]}},
            "ring_gold": {"rotation": {"0.0": [12, 0, 0], "1.5": [12, 540, 0]}},
            "ring_blue": {"rotation": {"0.0": [-20, 0, 15], "1.5": [-20, -540, 15]}},
            "screen_frame": {"scale": {"0.0": [1, 0.9, 1], "0.3": [1.03, 1.05, 1], "0.6": [1, 1, 1]}}}},
    }
    with open(path("animations", "block", "consciousness_nexus.animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)
    # item icon
    icon = np.zeros((16, 16, 4))
    icon[12:15, 2:14] = [0.25, 0.26, 0.32, 1]
    icon[5:12, 6:10] = [0.45, 0.48, 0.58, 1]
    icon[1:5, 1:15] = [0.9, 0.7, 0.2, 1]
    icon[2:4, 2:14] = [0.25, 0.6, 1.0, 1]
    icon[6:9, 7:9] = [1.0, 0.85, 0.3, 1]
    save(Image.fromarray((icon * 255).astype(np.uint8), "RGBA"), "textures", "item", "consciousness_nexus.png")
    # screen overlays: scanlines and static noise
    sl = np.zeros((64, 64, 4))
    sl[::2, :, 3] = 0.22
    save(Image.fromarray((sl * 255).astype(np.uint8), "RGBA"), "textures", "vfx", "scanlines.png")
    n = RNG.random((128, 128))
    st = np.stack([n, n, n, np.ones_like(n)], -1)
    save(Image.fromarray((st * 255).astype(np.uint8), "RGBA"), "textures", "vfx", "static.png")
    v = radial(128)[0]
    vig = np.clip((v - 0.55) / 0.6, 0, 1) ** 1.5
    save(gray_alpha(vig, np.zeros_like(vig)), "textures", "vfx", "vignette.png")
    save(glyph_circle(128, 3, 5), "textures", "vfx", "glyph_mind.png")


def write_geo(name, folder, p, bones, bounds=(4, 4)):
    geo = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": f"geometry.{name}", "texture_width": p.w, "texture_height": p.h,
                        "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1], "visible_bounds_offset": [0, 0, 0]},
        "bones": bones}]}
    with open(path("geo", folder, f"{name}.geo.json"), "w") as f:
        json.dump(geo, f, indent=1)
    tex, glow = p.images()
    save(tex, "textures", folder, f"{name}.png")
    save(glow, "textures", folder, f"{name}_glowmask.png")


def write_anims(name, folder, anims):
    with open(path("animations", folder, f"{name}.animation.json"), "w") as f:
        json.dump({"format_version": "1.8.0", "animations": anims}, f, indent=1)


def make_breach():
    """Reality Breach: the rift's shard ring, the observation panel and the specimen containment unit."""
    WHITE = (240, 244, 250)
    INK = (20, 18, 30)
    CYAN = (90, 230, 255)
    MAGENTA = (255, 70, 200)
    GOLD_E = (255, 210, 90)

    # -- dimensional rift: 14 jagged shards around a torn aperture, centred on the origin (16 px = 1 block radius)
    p = BoxUvPainter(128, 128)
    bones = [bone("root", [0, 0, 0])]
    for k in range(14):
        a = 2 * math.pi * k / 14 + (RNG.random() - 0.5) * 0.25
        r = 15 + RNG.random() * 3
        cx, cy = math.cos(a) * r, math.sin(a) * r
        length = 5 + RNG.random() * 6
        col = [INK, CYAN, MAGENTA, INK][k % 4]
        c = p.cube([cx - 0.8, cy - length / 2, -0.8], [1.6, length, 1.6], col, glow=col != INK, seed=200 + k)
        c["pivot"] = [cx, cy, 0]
        c["rotation"] = [0, 0, math.degrees(a) + RNG.uniform(-25, 25)]
        bones.append(bone(f"shard_{k}", [cx, cy, 0], [c], parent="root"))
    ring = []
    for k in range(20):
        a = 2 * math.pi * k / 20
        cx, cy = math.cos(a) * 13, math.sin(a) * 13
        c = p.cube([cx - 0.4, cy - 2.1, -0.4], [0.8, 4.2, 0.8], WHITE, glow=True, seed=260 + k)
        c["pivot"] = [cx, cy, 0]
        c["rotation"] = [0, 0, math.degrees(a)]
        ring.append(c)
    bones.append(bone("inner_ring", [0, 0, 0], ring, parent="root"))
    bones.append(bone("eye", [0, 0, 0], [p.cube([-1.2, -1.2, -1.2], [2.4, 2.4, 2.4], WHITE, glow=True, seed=290)], parent="root",
                      rotation=[45, 45, 0]))
    write_geo("dimensional_rift", "entity", p, bones, (3, 3))
    shard_names = [f"shard_{k}" for k in range(14)]
    write_anims("dimensional_rift", "entity", {
        "animation.dimensional_rift.open": {"animation_length": 1.6, "bones": {
            **{n: {"position": {"0.0": [RNG.uniform(-30, 30), RNG.uniform(-30, 30), RNG.uniform(-20, 20)], "1.1": [0, 0, 0]},
                   "scale": {"0.0": [0, 0, 0], "0.6": [1.4, 1.4, 1.4], "1.1": [1, 1, 1]}} for n in shard_names},
            "inner_ring": {"scale": {"0.0": [0, 0, 0], "0.9": [0.2, 0.2, 0.2], "1.4": [1.15, 1.15, 1.15], "1.6": [1, 1, 1]},
                           "rotation": {"0.0": [0, 0, 0], "1.6": [0, 0, 540]}},
            "eye": {"scale": {"0.0": [0, 0, 0], "0.3": [3, 3, 3], "0.8": [1, 1, 1]}}}},
        "animation.dimensional_rift.idle": {"loop": True, "animation_length": 6.0, "bones": {
            **{n: {"position": [f"math.sin(query.anim_time * {170 + i * 23} + {i * 40}) * 0.9",
                                f"math.cos(query.anim_time * {150 + i * 17} + {i * 25}) * 0.9", 0],
                   "rotation": [0, 0, f"math.sin(query.anim_time * {90 + i * 11}) * 9"]} for i, n in enumerate(shard_names)},
            "inner_ring": {"rotation": [0, 0, "query.anim_time * 45"], "scale": ["1 + math.sin(query.anim_time * 140) * 0.05"] * 3},
            "eye": {"rotation": ["query.anim_time * 160", "query.anim_time * 230", 0]},
            "root": {"rotation": [0, 0, "math.sin(query.anim_time * 30) * 4"]}}},
        "animation.dimensional_rift.close": {"animation_length": 1.0, "bones": {
            **{n: {"position": {"0.0": [0, 0, 0], "0.8": [0, 0, 0]}, "scale": {"0.0": [1, 1, 1], "0.9": [0, 0, 0]}} for n in shard_names},
            "inner_ring": {"scale": {"0.0": [1, 1, 1], "0.5": [1.3, 1.3, 1.3], "0.9": [0, 0, 0]}, "rotation": {"0.0": [0, 0, 0], "1.0": [0, 0, -720]}},
            "eye": {"scale": {"0.0": [1, 1, 1], "0.7": [2.5, 2.5, 2.5], "1.0": [0, 0, 0]}}}},
    })

    # -- observation panel: tilted holo slab (the button plane is drawn by the renderer), side wings, rear halo, emitters
    p = BoxUvPainter(256, 128)
    STEEL = (205, 212, 226)
    bones = [bone("root", [0, 0, 0])]
    bones.append(bone("slab", [0, 0, 0], [
        p.cube([-19, -9, -0.6], [38, 18, 1.2], STEEL, seed=300),
        p.cube([-20, -10, 0.4], [40, 20, 0.6], INK, seed=301),
        p.cube([-20, 9.2, -1.2], [40, 0.8, 1.2], CYAN, glow=True, seed=302),
        p.cube([-20, -10, -1.2], [40, 0.8, 1.2], CYAN, glow=True, seed=303),
    ], parent="root", rotation=[38, 0, 0]))
    for side, sx in (("left", -1), ("right", 1)):
        bones.append(bone(f"wing_{side}", [sx * 20, 0, 0], [
            p.cube([sx * 20 - (0 if sx > 0 else 3), -7, -0.5], [3, 14, 1], STEEL, seed=310 + sx),
            p.cube([sx * 20 + (2.2 if sx > 0 else -3.2), -5, -0.8], [1, 10, 1.6], MAGENTA, glow=True, seed=312 + sx),
        ], parent="slab"))
    halo = []
    for k in range(24):
        a = 2 * math.pi * k / 24
        cx, cy = math.cos(a) * 15, math.sin(a) * 9
        c = p.cube([cx - 0.5, cy - 1.2, -4.4], [1, 2.4, 0.6], WHITE, glow=True, seed=320 + k)
        c["pivot"] = [cx, cy, -4.1]
        c["rotation"] = [0, 0, math.degrees(math.atan2(cy / 9, cx / 15))]
        halo.append(c)
    # GeckoLib entity models face +z toward the viewer: the halo sits behind the slab at -z
    bones.append(bone("halo", [0, 0, -4.1], halo, parent="slab"))
    for i, x in enumerate((-12, 12)):
        bones.append(bone(f"emitter_{i}", [x, -14, 0], [p.cube([x - 1.5, -15.5, -1.5], [3, 3, 3], CYAN, glow=True, seed=350 + i)],
                          parent="root", rotation=[45, 45, 0]))
    bones.append(bone("stem", [0, -10, 2], [p.cube([-0.8, -22, 1.2], [1.6, 12, 1.6], STEEL, seed=360),
                                             p.cube([-3, -23, -1], [6, 1, 6], INK, seed=361)], parent="root"))
    write_geo("observation_panel", "entity", p, bones, (4, 3))
    write_anims("observation_panel", "entity", {
        "animation.observation_panel.deploy": {"animation_length": 1.2, "bones": {
            "slab": {"scale": {"0.0": [0.05, 0.05, 1], "0.5": [1.1, 0.1, 1], "0.9": [1.05, 1.08, 1], "1.2": [1, 1, 1]}},
            "wing_left": {"rotation": {"0.0": [0, 90, 0], "1.0": [0, 0, 0]}},
            "wing_right": {"rotation": {"0.0": [0, -90, 0], "1.0": [0, 0, 0]}},
            "halo": {"scale": {"0.0": [0, 0, 0], "0.8": [0, 0, 0], "1.2": [1, 1, 1]}}}},
        "animation.observation_panel.idle": {"loop": True, "animation_length": 4.0, "bones": {
            "root": {"position": [0, "math.sin(query.anim_time * 90) * 0.5", 0]},
            "halo": {"rotation": [0, 0, "query.anim_time * 30"]},
            "emitter_0": {"rotation": [45, "query.anim_time * 180", 0]},
            "emitter_1": {"rotation": [45, "-query.anim_time * 180", 0]},
            "wing_left": {"rotation": [0, "math.sin(query.anim_time * 90) * 4", 0]},
            "wing_right": {"rotation": [0, "-math.sin(query.anim_time * 90) * 4", 0]}}},
        "animation.observation_panel.press": {"animation_length": 0.35, "bones": {
            "slab": {"position": {"0.0": [0, 0, 0], "0.08": [0, 0, 0.8], "0.35": [0, 0, 0]}},
            "halo": {"scale": {"0.0": [1, 1, 1], "0.1": [1.15, 1.15, 1.15], "0.35": [1, 1, 1]}}}},
    })

    # -- containment unit: floor disc, three gimballed rings, four emitter pylons
    p = BoxUvPainter(128, 128)
    bones = [bone("root", [0, 0, 0])]
    disc = [p.cube([-14, 0, -14], [28, 1, 28], STEEL, seed=400), p.cube([-11, 1, -11], [22, 0.4, 22], CYAN, glow=True, seed=401)]
    bones.append(bone("base", [0, 0, 0], disc, parent="root"))
    for i, (x, z) in enumerate(((-13, -13), (13, -13), (-13, 13), (13, 13))):
        bones.append(bone(f"pylon_{i}", [x, 1, z], [p.cube([x - 1, 1, z - 1], [2, 14, 2], STEEL, seed=410 + i),
                                                    p.cube([x - 0.6, 15, z - 0.6], [1.2, 3, 1.2], MAGENTA, glow=True, seed=415 + i)],
                          parent="base"))
    for j, (rad, col) in enumerate(((16, WHITE), (19, CYAN), (22, GOLD_E))):
        segs = []
        for k in range(22):
            a = 2 * math.pi * k / 22
            cx, cz = math.cos(a) * rad, math.sin(a) * rad
            c = p.cube([cx - 0.4, 24 - 0.4, cz - 2.4], [0.8, 0.8, 4.8], col, glow=True, seed=430 + j * 30 + k)
            c["pivot"] = [cx, 24, cz]
            c["rotation"] = [0, -math.degrees(a), 0]
            segs.append(c)
        bones.append(bone(f"ring_{j}", [0, 24, 0], segs, parent="root"))
    write_geo("containment", "entity", p, bones, (4, 4))
    write_anims("containment", "entity", {
        "animation.containment.idle": {"loop": True, "animation_length": 8.0, "bones": {
            "ring_0": {"rotation": ["query.anim_time * 40", "query.anim_time * 25", 0]},
            "ring_1": {"rotation": [0, "-query.anim_time * 35", "query.anim_time * 30"]},
            "ring_2": {"rotation": ["math.sin(query.anim_time * 45) * 25", "query.anim_time * 15", "math.cos(query.anim_time * 45) * 25"]},
            **{f"pylon_{i}": {"scale": [1, f"1 + math.sin(query.anim_time * 180 + {i * 90}) * 0.04", 1]} for i in range(4)}}},
        "animation.containment.lost": {"animation_length": 1.5, "bones": {
            **{f"ring_{j}": {"scale": {"0.0": [1, 1, 1], "0.4": [1.2, 1.2, 1.2], "1.5": [0, 0, 0]},
                             "rotation": {"0.0": [0, 0, 0], "1.5": [0, 900 * (1 if j % 2 else -1), 0]}} for j in range(3)}}},
    })

    # lab floor: white with a faint grid seam
    f = np.ones((16, 16, 4)) * np.array([0.97, 0.975, 0.985, 1.0])
    f[0, :, :3] = f[:, 0, :3] = [0.9, 0.915, 0.94]
    save(Image.fromarray((f * 255).astype(np.uint8), "RGBA"), "textures", "block", "chamber_floor.png")


def make_power():
    """Power Stone: orb, gravity singularity and planetary core models; impact particle sprites."""
    PURPLE = (170, 60, 255)
    VIOLET = (110, 30, 200)
    PINK = (240, 140, 255)
    DARK = (24, 10, 40)
    WHITE = (250, 240, 255)

    # power orb: hot core, three counter-rotating shells, six spikes
    p = BoxUvPainter(128, 64)
    bones = [bone("root", [0, 0, 0]), bone("core", [0, 0, 0], [p.cube([-2.5, -2.5, -2.5], [5, 5, 5], WHITE, glow=True, seed=500)], parent="root")]
    for j, (rad, col) in enumerate(((4.5, PURPLE), (5.5, PINK), (6.5, VIOLET))):
        segs = []
        for k in range(12):
            a = 2 * math.pi * k / 12
            cx, cy = math.cos(a) * rad, math.sin(a) * rad
            c = p.cube([cx - 0.4, cy - 1.0, -0.4], [0.8, 2.0, 0.8], col, glow=True, seed=510 + j * 20 + k)
            c["pivot"] = [cx, cy, 0]
            c["rotation"] = [0, 0, math.degrees(a)]
            segs.append(c)
        bones.append(bone(f"shell_{j}", [0, 0, 0], segs, parent="root", rotation=[j * 60, j * 35, 0]))
    spikes = []
    for k, (dx, dy, dz) in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
        size = [0.8 + abs(dx) * 3, 0.8 + abs(dy) * 3, 0.8 + abs(dz) * 3]
        spikes.append(p.cube([dx * 3.5 - size[0] / 2, dy * 3.5 - size[1] / 2, dz * 3.5 - size[2] / 2], size, PINK, glow=True, seed=580 + k))
    bones.append(bone("spikes", [0, 0, 0], spikes, parent="root"))
    write_geo("power_orb", "entity", p, bones, (1.5, 1.5))
    write_anims("power_orb", "entity", {
        "animation.power_orb.idle": {"loop": True, "animation_length": 2.0, "bones": {
            "shell_0": {"rotation": [0, "query.anim_time * 360", "query.anim_time * 180"]},
            "shell_1": {"rotation": ["query.anim_time * -270", 0, "query.anim_time * 200"]},
            "shell_2": {"rotation": ["query.anim_time * 150", "query.anim_time * -300", 0]},
            "spikes": {"rotation": ["query.anim_time * 90", "query.anim_time * 140", 0],
                       "scale": ["1 + math.sin(query.anim_time * 720) * 0.25"] * 3},
            "core": {"scale": ["1 + math.sin(query.anim_time * 1080) * 0.15"] * 3}}},
        "animation.power_orb.dive": {"loop": True, "animation_length": 0.5, "bones": {
            "shell_0": {"rotation": [0, "query.anim_time * 1440", 0], "scale": [0.6, 0.6, 1.8]},
            "shell_1": {"rotation": [0, 0, "query.anim_time * -1440"], "scale": [0.6, 0.6, 1.8]},
            "spikes": {"scale": [0.5, 0.5, 2.5]}}},
    })

    # gravity singularity: black core, glowing accretion disk, orbiting rubble
    p = BoxUvPainter(256, 128)
    bones = [bone("root", [0, 0, 0]), bone("core", [0, 0, 0], [p.cube([-6, -6, -6], [12, 12, 12], DARK, seed=600),
                                                              p.cube([-6.6, -6.6, -6.6], [13.2, 13.2, 13.2], PURPLE, glow=True, seed=601)],
                                      parent="root")]
    disk = []
    for k in range(40):
        a = 2 * math.pi * k / 40
        for r, col in ((11, PINK), (15, PURPLE), (19, VIOLET)):
            cx, cz = math.cos(a) * r, math.sin(a) * r
            c = p.cube([cx - 1.4, -0.4, cz - 0.6], [2.8, 0.8, 1.2], col, glow=True, seed=610 + k * 3 + r)
            c["pivot"] = [cx, 0, cz]
            c["rotation"] = [0, -math.degrees(a), 0]
            disk.append(c)
    bones.append(bone("disk", [0, 0, 0], disk, parent="root", rotation=[18, 0, 8]))
    rubble = []
    for k in range(10):
        a = 2 * math.pi * k / 10
        cx, cy, cz = math.cos(a) * 24, (RNG.random() - 0.5) * 12, math.sin(a) * 24
        rubble.append(p.cube([cx - 1.5, cy - 1.5, cz - 1.5], [3, 3, 3], (80, 70, 90), seed=700 + k))
    bones.append(bone("rubble", [0, 0, 0], rubble, parent="root"))
    write_geo("gravity_core", "entity", p, bones, (5, 5))
    write_anims("gravity_core", "entity", {
        "animation.gravity_core.idle": {"loop": True, "animation_length": 4.0, "bones": {
            "disk": {"rotation": [18, "query.anim_time * 180", 8]},
            "rubble": {"rotation": [0, "query.anim_time * -90", "math.sin(query.anim_time * 90) * 15"]},
            "core": {"scale": ["1 + math.sin(query.anim_time * 540) * 0.06"] * 3, "rotation": ["query.anim_time * 45", "query.anim_time * 70", 0]}}},
    })

    # planetary core: blazing sphere-ish block cluster wrapped in three great rings
    p = BoxUvPainter(256, 128)
    core = [p.cube([-8, -8, -8], [16, 16, 16], WHITE, glow=True, seed=800),
            p.cube([-10, -5, -5], [20, 10, 10], PINK, glow=True, seed=801),
            p.cube([-5, -10, -5], [10, 20, 10], PINK, glow=True, seed=802),
            p.cube([-5, -5, -10], [10, 10, 20], PINK, glow=True, seed=803)]
    bones = [bone("root", [0, 0, 0]), bone("core", [0, 0, 0], core, parent="root")]
    for j, rad in enumerate((16, 21, 26)):
        segs = []
        for k in range(28):
            a = 2 * math.pi * k / 28
            cx, cy = math.cos(a) * rad, math.sin(a) * rad
            c = p.cube([cx - 0.7, cy - 2.4, -0.7], [1.4, 4.8, 1.4], PURPLE if j != 1 else WHITE, glow=True, seed=820 + j * 40 + k)
            c["pivot"] = [cx, cy, 0]
            c["rotation"] = [0, 0, math.degrees(a)]
            segs.append(c)
        bones.append(bone(f"ring_{j}", [0, 0, 0], segs, parent="root", rotation=[j * 55, j * 30, 0]))
    write_geo("planetary_core", "entity", p, bones, (6, 6))
    write_anims("planetary_core", "entity", {
        "animation.planetary_core.idle": {"loop": True, "animation_length": 2.0, "bones": {
            "ring_0": {"rotation": [0, 0, "query.anim_time * 360"]},
            "ring_1": {"rotation": [55, "query.anim_time * -540", 0]},
            "ring_2": {"rotation": ["query.anim_time * 270", 30, 0]},
            "core": {"rotation": ["query.anim_time * 200", "query.anim_time * 260", 0],
                     "scale": ["1 + math.sin(query.anim_time * 1440) * 0.08"] * 3}}},
    })

    # impact sprites
    r, ang = radial(16)
    drop = np.clip(1 - (r * 1.3 + np.maximum(0, np.linspace(-1, 1, 16)[:, None]) * 0.4), 0, 1) ** 0.6
    save(gray_alpha(drop), "textures", "particle", "droplet.png")
    n = fbm(16, 900, 3, 2)
    splat = np.clip((0.75 + 0.3 * (n - 0.5) * 2 - r) / 0.25, 0, 1)
    save(gray_alpha(splat, 0.8 + 0.2 * n), "textures", "particle", "splat.png")
    lin = np.linspace(-1, 1, 16)
    x, y = np.meshgrid(lin, lin)
    chunk = ((np.abs(x) < 0.6) & (np.abs(y) < 0.6)).astype(float)
    save(gray_alpha(chunk, 0.85 + 0.15 * fbm(16, 901, 2, 2)), "textures", "particle", "chunk.png")
    for name, sprites in {"impact_droplet": ["droplet"], "impact_splat": ["splat"], "impact_chunk": ["chunk"]}.items():
        with open(path("particles", f"{name}.json"), "w") as f:
            json.dump({"textures": [f"infinitygauntlet:{s}" for s in sprites]}, f, indent=2)


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

    # Consciousness Nexus ----------------------------------------------------
    d = 2.0
    add("nexus_power_on", sweep(d, 60, 900, 1.5) * env(d, 0.05, 0.4) * 0.4 + sweep(d, 90, 1350, 1.5) * env(d, 0.1, 0.4) * 0.2
        + fit(np.concatenate([np.zeros(int(SR * 1.2)), np.sin(2 * np.pi * 1760 * t_axis(0.8)) * np.exp(-t_axis(0.8) * 5) * 0.5]), d)
        + bandpass(noise(d), 2000, 8000) * env(d, 0.01, 0.15, 3) * 0.4, "Consciousness Nexus powers on")
    d = 4.0
    hum = (np.sin(2 * np.pi * 60 * t_axis(d)) * 0.35 + np.sin(2 * np.pi * 120 * t_axis(d)) * 0.2 + np.sin(2 * np.pi * 330 * t_axis(d)) * 0.08
           * (0.6 + 0.4 * np.sin(2 * np.pi * 0.7 * t_axis(d))) + bandpass(noise(d), 1500, 5000) * 0.05)
    fade = np.minimum(1, np.minimum(t_axis(d), d - t_axis(d)) / 0.05)
    add("nexus_hum", hum * fade, "Nexus hums")
    d = 1.6
    add("nexus_link", sweep(d, 1800, 300, 0.6) * env(d, 0.02, 1.4) * 0.3 + sweep(d, 300, 2400, 1.8) * env(d, 0.6, 0.6) * 0.3
        + crackle(d, 90, seed=31) * 0.5, "Neural link forms")
    d = 2.2
    add("nexus_disconnect", bandpass(noise(d), 300, 7000) * env(d, 0.05, 1.6, 1.2) * 0.6 + sweep(d, 900, 40, 0.5) * env(d, 0.01, 2.0) * 0.4
        + fit(np.concatenate([np.zeros(int(SR * 1.5)), boom(0.7, 120, 50)]), d), "Neural link severed")
    d = 1.0
    add("nexus_static", bandpass(noise(d), 800, 9000) * env(d, 0.01, 0.9, 1) * 0.5, "Signal static")
    d = 0.5
    add("nexus_warning", np.sign(np.sin(2 * np.pi * 880 * t_axis(d))) * ((t_axis(d) % 0.25) < 0.12) * env(d, 0.005, 0.1, 1) * 0.25, "Connection unstable")

    # Reality Breach -----------------------------------------------------
    d = 2.6
    tear = (crackle(d, 220, 0.0012, seed=41) * env(d, 0.05, 2.2, 1.2) * 1.1 + sweep(d, 30, 160, 0.4) * env(d, 0.3, 2.0) * 0.6
            + np.sign(np.sin(2 * np.pi * np.cumsum(70 + 400 * t_axis(d) ** 2) / SR)) * env(d, 0.5, 1.4) * 0.12
            + fit(np.concatenate([np.zeros(int(SR * 1.4)), boom(1.2, 50, 22) * 1.3]), d))
    add("breach_open", tear, "Reality tears open")
    d = 2.0
    add("breach_enter", bandpass(noise(d), 200, 9000)[::-1] * env(d, 1.6, 0.4, 1) * 0.7 + sweep(d, 2000, 60, 0.4) * env(d, 0.4, 1.4) * 0.4
        + fit(np.concatenate([np.zeros(int(SR * 1.6)), boom(0.4, 140, 60)]), d), "You pass through the breach")
    d = 6.0
    tt = t_axis(d)
    amb = (np.sin(2 * np.pi * 47 * tt) * 0.25 + np.sin(2 * np.pi * 47.6 * tt) * 0.25 + np.sin(2 * np.pi * 3520 * tt) * 0.012
           * (0.5 + 0.5 * np.sin(2 * np.pi * 0.21 * tt)) + bandpass(noise(d), 4000, 9000) * 0.015)
    fade = np.minimum(1, np.minimum(tt, d - tt) / 0.08)
    add("chamber_ambient", amb * fade, "The white room hums")
    d = 0.6
    gl = np.zeros(int(SR * d))
    rng2 = np.random.default_rng(55)
    i = 0
    while i < len(gl):
        n = int(SR * rng2.uniform(0.01, 0.05))
        f0 = rng2.uniform(80, 3000)
        seg = np.sign(np.sin(2 * np.pi * f0 * np.arange(n) / SR)) * rng2.uniform(0.2, 0.8)
        if rng2.random() < 0.3:
            seg = rng2.standard_normal(n) * 0.5
        gl[i:i + n] = seg[:len(gl[i:i + n])]
        i += n + int(SR * rng2.uniform(0, 0.03))
    add("reality_glitch", gl * env(d, 0.002, 0.2, 1), "Reality glitches")
    d = 0.18
    add("panel_press", np.sin(2 * np.pi * 1320 * t_axis(d)) * np.exp(-t_axis(d) * 30) * 0.5
        + np.sin(2 * np.pi * 1980 * t_axis(d)) * np.exp(-t_axis(d) * 40) * 0.3, "Control panel beeps")
    d = 1.2
    add("specimen_lost", sweep(d, 900, 90, 0.5) * env(d, 0.01, 1.1) * 0.4 + crackle(d, 160, 0.002, seed=57) * env(d, 0.01, 0.8, 2) * 0.6,
        "Specimen signal lost")

    # Power Stone -----------------------------------------------------------
    def rumble(sec, lo=40, hi=220, seed=0):
        return lowpass(noise(sec), hi) * 4

    d = 1.0
    add("power_charge", sum(sweep(d, f, f * 4, 2.2) * (0.3 / (i + 1)) for i, f in enumerate((55, 110, 165))) * env(d, 0.9, 0.1, 1)
        + crackle(d, 120, 0.002, seed=61) * env(d, 0.8, 0.1, 1) * 0.6 + rumble(d) * env(d, 0.9, 0.1, 1) * 0.5, "Power gathers")
    d = 1.6
    add("power_beam", (sweep(d, 70, 60) * 0.5 + np.sign(np.sin(2 * np.pi * np.cumsum(140 + 30 * np.sin(2 * np.pi * 9 * t_axis(d))) / SR)) * 0.15
                       + bandpass(noise(d), 300, 5000) * 0.5 + crackle(d, 300, 0.0015, seed=62) * 0.7) * env(d, 0.01, 0.6, 1.2), "Power beam roars")
    d = 1.4
    add("power_explosion", boom(d, 70, 22) * 1.6 + crackle(d, 500, 0.001, seed=63) * env(d, 0.001, 0.5, 2) + bandpass(noise(d), 200, 3000) * env(d, 0.005, 1.0, 2) * 0.8,
        "Power explosion")
    d = 0.7
    add("power_punch_impact", boom(d, 110, 30) * 1.8 + bandpass(noise(d), 100, 2500) * env(d, 0.001, 0.25, 3) * 1.2 + crackle(d, 600, 0.0008, seed=64) * env(d, 0.001, 0.2, 2),
        "Cosmic punch lands")
    d = 0.5
    add("power_whoosh", bandpass(noise(d), 400, 5000) * env(d, 0.25, 0.2, 1) * 0.8 + sweep(d, 200, 900, 1.5) * env(d, 0.3, 0.2) * 0.2, "Rushing air")
    d = 2.2
    add("power_ground_crack", (crackle(d, 160, 0.004, seed=65) * 1.2 + rumble(d, hi=160) * 0.8) * env(d, 0.01, 1.8, 1.3) + boom(d, 55, 25), "Ground cracks apart")
    d = 3.0
    add("power_nova_charge", sum(sweep(d, f, f * 5, 2.5) * (0.25 / (i + 1)) for i, f in enumerate((40, 80, 120, 160))) * env(d, 2.8, 0.1, 1)
        + rumble(d) * env(d, 2.8, 0.1, 1) * 0.7 + crackle(d, 80, seed=66) * env(d, 2.5, 0.2, 1) * 0.6, "Energy builds to breaking point")
    d = 3.0
    add("power_nova_release", boom(d, 60, 18) * 2.0 + bandpass(noise(d), 100, 8000) * env(d, 0.001, 2.0, 2) * 1.2 + crackle(d, 300, 0.002, seed=67) * env(d, 0.01, 1.5, 2),
        "Energy nova detonates")
    d = 1.2
    add("power_launch", sweep(d, 300, 1400, 0.6) * env(d, 0.01, 1.0) * 0.4 + bandpass(noise(d), 1000, 7000) * env(d, 0.02, 0.9) * 0.4, "Energy launches")
    d = 2.0
    whistle = sweep(d, 2400, 500, 1.0) * env(d, 0.2, 0.3) * 0.35 + bandpass(noise(d), 800, 4000) * env(d, 1.0, 0.2, 1) * 0.4
    add("power_meteor", whistle, "Meteor screams down")
    d = 1.0
    add("power_overload", sweep(d, 80, 300, 1.5) * env(d, 0.05, 0.8) * 0.5 + crackle(d, 250, 0.0015, seed=68) * 0.8 + boom(d, 90, 50) * 0.4, "Power overloads")
    d = 0.4
    add("power_hit", boom(d, 140, 60) * 1.2 + crackle(d, 400, 0.001, seed=69) * env(d, 0.001, 0.2, 2), "Overloaded hit")
    d = 4.0
    tt = t_axis(d)
    add("power_gravity", (np.sin(2 * np.pi * np.cumsum(32 + 14 * tt / d) / SR) * 0.6 + rumble(d, hi=120) * 0.8 + crackle(d, 30, 0.004, seed=70) * 0.4) * env(d, 1.0, 0.4, 1),
        "A singularity descends")
    d = 0.8
    add("power_orb", sweep(d, 600, 1800, 1.0) * env(d, 0.02, 0.6) * 0.3 + crackle(d, 100, seed=71) * 0.5 + np.sin(2 * np.pi * 220 * t_axis(d)) * env(d, 0.01, 0.7) * 0.3,
        "Power orb forms")
    d = 5.0
    add("power_planetary_charge", sum(sweep(d, f, f * 6, 3.0) * (0.22 / (i + 1)) for i, f in enumerate((30, 45, 60, 90, 120))) * env(d, 4.8, 0.15, 1)
        + rumble(d, hi=140) * env(d, 4.5, 0.2, 1) + crackle(d, 150, 0.002, seed=72) * env(d, 4.0, 0.3, 1) * 0.7, "A planet's worth of force gathers")
    d = 5.0
    add("power_planetary_impact", boom(d, 45, 14) * 2.4 + bandpass(noise(d), 60, 6000) * env(d, 0.002, 4.0, 2) * 1.4
        + fit(np.concatenate([np.zeros(int(SR * 0.7)), boom(2.0, 60, 25) * 1.5]), d) + fit(np.concatenate([np.zeros(int(SR * 1.5)), boom(2.0, 50, 22) * 1.3]), d),
        "Planetary impact")
    d = 0.35
    add("impact_flesh", lowpass(noise(d), 900) * env(d, 0.001, 0.25, 3) * 2 + boom(d, 160, 70) * 0.6, "Visceral impact")
    d = 0.3
    add("impact_bone", crackle(d, 900, 0.0006, seed=73) * env(d, 0.001, 0.2, 3) * 1.5 + bandpass(noise(d), 2000, 7000) * env(d, 0.001, 0.08, 3), "Bones crack")
    d = 0.6
    add("head_pop", np.sin(2 * np.pi * np.cumsum(np.linspace(300, 120, int(SR * d))) / SR) * env(d, 0.001, 0.5) * 0.5 + lowpass(noise(d), 1500) * env(d, 0.001, 0.15, 3) * 1.5,
        "Head pops off")
    d = 2.0
    add("power_finisher", sum(sweep(d, f, f * 0.25, 0.4) * (0.3 / (i + 1)) for i, f in enumerate((400, 600, 900))) * env(d, 0.01, 1.9)
        + fit(np.concatenate([np.zeros(int(SR * 0.05)), boom(1.9, 60, 18) * 2]), d), "Finishing blow")

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
    make_nexus()
    make_breach()
    make_power()
    make_sounds()
    with open(os.path.join(os.path.dirname(os.path.abspath(__file__)), "generated_subtitles.json"), "w") as f:
        json.dump(SUBTITLES, f, indent=2, sort_keys=True)
    print("assets generated in", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
