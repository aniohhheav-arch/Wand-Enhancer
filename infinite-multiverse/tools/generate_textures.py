#!/usr/bin/env python3
"""Generates the procedural particle sprites and mod logo. Pure standard library; rerun after tweaking.

    python3 tools/generate_textures.py
"""
import math
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
PARTICLES = os.path.join(ROOT, "assets", "infinitemultiverse", "textures", "particle")


def write_png(path, width, height, pixels):
    """pixels: list of rows, each a list of (r, g, b, a) tuples with 0-255 ints."""
    raw = bytearray()
    for row in pixels:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(kind, data):
        body = kind + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def clamp01(value):
    return max(0.0, min(1.0, value))


def to_byte(value):
    return int(round(clamp01(value) * 255))


def white_sprite(size, alpha_fn):
    """White sprite whose alpha is alpha_fn(dx, dy) with (dx, dy) measured from the centre in pixels."""
    centre = (size - 1) / 2.0
    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            alpha = alpha_fn(x - centre, y - centre)
            row.append((255, 255, 255, to_byte(alpha)))
        rows.append(row)
    return rows


def mote_frame(core_radius, glow_radius):
    def alpha(dx, dy):
        d = math.hypot(dx, dy)
        core = 1.0 if d <= core_radius else 0.0
        glow = math.exp(-((max(0.0, d - core_radius)) / glow_radius) ** 2)
        return max(core, glow * 0.95)

    return white_sprite(16, alpha)


def spark_frame(ray_length, ray_width, diagonal_strength):
    def ray(along, across):
        if abs(along) > ray_length:
            return 0.0
        taper = 1.0 - abs(along) / ray_length
        return math.exp(-(across / ray_width) ** 2) * taper ** 1.5

    def alpha(dx, dy):
        axis = max(ray(dx, dy), ray(dy, dx))
        u = (dx + dy) / math.sqrt(2.0)
        v = (dx - dy) / math.sqrt(2.0)
        diagonal = max(ray(u * 1.6, v), ray(v * 1.6, u)) * diagonal_strength
        core = math.exp(-(math.hypot(dx, dy) / 1.4) ** 2)
        return max(axis, diagonal, core)

    return white_sprite(16, alpha)


def lerp(a, b, t):
    return a + (b - a) * t


def lerp_color(c0, c1, t):
    return tuple(lerp(c0[i], c1[i], t) for i in range(3))


def logo(size=128):
    """Dark field, layered portal rings and a glowing infinity sign."""
    violet = (0.61, 0.36, 1.0)
    cyan = (0.36, 0.88, 1.0)
    gold = (1.0, 0.79, 0.3)
    centre = (size - 1) / 2.0

    curve = []
    steps = 720
    a = size * 0.36
    for i in range(steps):
        t = 2.0 * math.pi * i / steps
        denom = 1.0 + math.sin(t) ** 2
        curve.append((centre + a * math.cos(t) / denom, centre + a * math.sin(t) * math.cos(t) / denom, i / steps))

    rows = []
    for y in range(size):
        row = []
        for x in range(size):
            dx, dy = x - centre, y - centre
            d = math.hypot(dx, dy) / (size / 2.0)
            base = lerp_color((0.03, 0.04, 0.09), (0.08, 0.05, 0.16), clamp01(1.0 - d))
            r, g, b = base

            for ring_radius, ring_color, strength in ((0.92, violet, 0.55), (0.78, cyan, 0.35), (0.64, violet, 0.2)):
                ring = math.exp(-((d - ring_radius) / 0.025) ** 2) * strength
                r, g, b = r + ring_color[0] * ring, g + ring_color[1] * ring, b + ring_color[2] * ring

            best = 1e9
            best_t = 0.0
            for cx, cy, ct in curve[::3]:
                dist = (x - cx) ** 2 + (y - cy) ** 2
                if dist < best:
                    best, best_t = dist, ct
            dist = math.sqrt(best)
            stroke = math.exp(-(dist / 2.6) ** 2)
            glow = math.exp(-(dist / 9.0) ** 2) * 0.45
            sweep = 0.5 + 0.5 * math.sin(best_t * 2.0 * math.pi)
            color = lerp_color(violet, cyan, sweep)
            r += color[0] * glow + lerp(color[0], 1.0, 0.55) * stroke
            g += color[1] * glow + lerp(color[1], 1.0, 0.55) * stroke
            b += color[2] * glow + lerp(color[2], 1.0, 0.55) * stroke

            star = math.exp(-(math.hypot(dx, dy) / 3.0) ** 2) + 0.35 * max(
                math.exp(-(dy / 0.9) ** 2) * math.exp(-(dx / 14.0) ** 2),
                math.exp(-(dx / 0.9) ** 2) * math.exp(-(dy / 14.0) ** 2))
            r, g, b = r + gold[0] * star, g + gold[1] * star, b + gold[2] * star

            vignette = clamp01(1.15 - d * 0.35)
            row.append((to_byte(r * vignette), to_byte(g * vignette), to_byte(b * vignette), 255))
        rows.append(row)
    return rows


def main():
    for index, (core, glow) in enumerate(((2.6, 4.0), (2.1, 3.4), (1.5, 2.8), (0.8, 2.2))):
        write_png(os.path.join(PARTICLES, "energy_mote_%d.png" % index), 16, 16, mote_frame(core, glow))
    for index, (length, width, diagonal) in enumerate(((7.5, 0.9, 0.55), (6.5, 0.8, 0.7), (5.0, 0.75, 0.45), (3.5, 0.7, 0.3))):
        write_png(os.path.join(PARTICLES, "spark_%d.png" % index), 16, 16, spark_frame(length, width, diagonal))
    write_png(os.path.join(ROOT, "infinitemultiverse_logo.png"), 128, 128, logo())
    print("Textures written under", os.path.normpath(ROOT))


if __name__ == "__main__":
    main()
