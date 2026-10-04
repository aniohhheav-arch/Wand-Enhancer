#!/usr/bin/env python3
"""Procedurally generates the Final Frame texture set (pure stdlib, reproducible).

Run from the final-frame directory: python3 tools/gen_textures.py
"""
import math
import os
import random
import struct
import zlib

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "finalframe", "textures")
SIZE = 32


def write_png(path, w, h, pixels):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(bytes(max(0, min(255, int(round(c)))) for c in pixels[y * w + x]))

    def chunk(tag, data):
        c = struct.pack(">I", len(data)) + tag + data
        return c + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


def value_noise(seed, scale):
    rnd = random.Random(seed)
    grid = {}

    def g(ix, iy):
        key = (ix % 64, iy % 64)
        if key not in grid:
            grid[key] = rnd.random()
        return grid[key]

    def n(x, y):
        x /= scale
        y /= scale
        ix, iy = math.floor(x), math.floor(y)
        fx, fy = x - ix, y - iy
        sx, sy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = g(ix, iy) + (g(ix + 1, iy) - g(ix, iy)) * sx
        b = g(ix, iy + 1) + (g(ix + 1, iy + 1) - g(ix, iy + 1)) * sx
        return a + (b - a) * sy

    return n


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def tex(name, fn, size=SIZE):
    px = [fn(x, y) for y in range(size) for x in range(size)]
    px = [p if len(p) == 4 else (*p, 255) for p in px]
    write_png(os.path.join(OUT, name + ".png"), size, size, px)


def blued_steel():
    n1, n2 = value_noise(1, 6), value_noise(2, 2)
    base, hi = (34, 38, 52), (86, 98, 124)

    def f(x, y):
        t = 0.35 * n1(x, y) + 0.15 * n2(x, y)
        sheen = max(0.0, math.sin((x + y * 0.35) * 0.22)) ** 6 * 0.55
        c = mix(base, hi, min(1, t + sheen))
        return c

    return f


def polished_steel():
    n1 = value_noise(3, 3)

    def f(x, y):
        brushed = 0.5 + 0.5 * math.sin(y * 1.7 + n1(x, y) * 2.0)
        c = mix((120, 126, 138), (196, 202, 212), 0.35 + 0.4 * brushed * n1(x * 0.5, y))
        return c

    return f


def engraved_steel():
    base = polished_steel()

    def f(x, y):
        c = base(x, y)
        cx, cy = x - 16, y - 16
        r = math.hypot(cx, cy)
        ang = math.atan2(cy, cx)
        # Scrollwork: a spiral band plus leaf flourishes.
        spiral = math.sin(r * 0.9 - ang * 2.0)
        leaf = math.sin(ang * 6 + r * 0.4)
        border = x in (0, 1, 30, 31) or y in (0, 1, 30, 31)
        if border:
            return mix(c, (212, 168, 72), 0.85)
        if abs(spiral) < 0.18 and r < 14 or (abs(leaf) < 0.12 and 4 < r < 11):
            return mix(c, (40, 40, 48), 0.7)
        return c

    return f


def walnut():
    n1, n2 = value_noise(4, 9), value_noise(5, 2)

    def f(x, y):
        grain = math.sin((y + n1(x, y) * 7.0) * 1.15 + x * 0.08)
        knot = math.exp(-(((x - 22) ** 2) / 18 + ((y - 9) ** 2) / 10))
        t = 0.5 + 0.35 * grain + 0.15 * n2(x, y) - 0.5 * knot
        c = mix((58, 30, 16), (138, 78, 40), max(0, min(1, t)))
        # Checkering diamonds in the middle of the panel.
        if 6 <= x <= 25 and 6 <= y <= 25 and ((x + y) % 4 == 0 or (x - y) % 4 == 0):
            c = mix(c, (34, 18, 10), 0.55)
        return c

    return f


def brass():
    n1 = value_noise(6, 4)

    def f(x, y):
        sheen = max(0.0, math.sin((x - y) * 0.3)) ** 4
        return mix((156, 108, 34), (252, 214, 120), 0.35 + 0.35 * n1(x, y) + 0.3 * sheen)

    return f


def cylinder_face():
    steel = blued_steel()

    def f(x, y):
        c = steel(x, y)
        cx, cy = x - 15.5, y - 15.5
        for k in range(6):
            a = k * math.pi / 3 + math.pi / 2
            hx, hy = 9.5 * math.cos(a), 9.5 * math.sin(a)
            d = math.hypot(cx - hx, cy - hy)
            if d < 3.0:
                return (14, 14, 18) if d < 2.2 else (196, 158, 70)  # chamber + brass case rim
        if math.hypot(cx, cy) < 2.4:
            return (170, 176, 188)
        if math.hypot(cx, cy) > 15.5:
            return mix(c, (10, 10, 14), 0.6)
        return c

    return f


def cylinder_flutes():
    steel = blued_steel()

    def f(x, y):
        c = steel(x, y)
        if (y % 8) in (3, 4):
            return mix(c, (12, 12, 18), 0.65)
        if (y % 8) == 2:
            return mix(c, (150, 160, 182), 0.4)
        return c

    return f


def leather():
    n1, n2 = value_noise(7, 5), value_noise(8, 1.5)

    def f(x, y):
        t = 0.45 * n1(x, y) + 0.25 * n2(x, y)
        c = mix((70, 38, 20), (132, 82, 46), t)
        if x in (2, 29) and y % 3 != 0:  # stitching
            c = (214, 190, 140)
        if y in (5, 26) and 4 <= x <= 27:
            c = mix(c, (40, 22, 12), 0.6)  # tooled line
        return c

    return f


def bore():
    return lambda x, y: (6, 6, 8)


def muzzle_flash():
    def f(x, y):
        cx, cy = x - 15.5, y - 15.5
        r = math.hypot(cx, cy) / 15.5
        ang = math.atan2(cy, cx)
        spikes = 0.55 + 0.45 * abs(math.cos(ang * 4)) ** 8
        a = max(0.0, 1 - r / spikes)
        a = a ** 1.4
        col = mix((255, 150, 40), (255, 250, 220), a)
        k = min(1, a * 1.6)  # premultiplied: the flash is drawn with additive blending
        return (col[0] * k, col[1] * k, col[2] * k, 255 * k)

    return f


def main():
    tex("item/revolver/steel_blued", blued_steel())
    tex("item/revolver/steel_polished", polished_steel())
    tex("item/revolver/steel_engraved", engraved_steel())
    tex("item/revolver/walnut", walnut())
    tex("item/revolver/brass", brass())
    tex("item/revolver/cylinder_face", cylinder_face())
    tex("item/revolver/cylinder_flutes", cylinder_flutes())
    tex("item/revolver/leather", leather())
    tex("item/revolver/bore", bore())
    tex("misc/muzzle_flash", muzzle_flash())


if __name__ == "__main__":
    main()
