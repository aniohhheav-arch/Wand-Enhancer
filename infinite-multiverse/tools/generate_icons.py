#!/usr/bin/env python3
"""Generates the original 64x64 ability icons from signed-distance glyphs. Pure standard library.

    python3 tools/generate_icons.py
"""
import math
import os

from generate_textures import write_png

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources")
ICONS = os.path.join(ROOT, "assets", "infinitemultiverse", "textures", "gui", "ability")
SIZE = 64


def hex_rgb(value):
    return ((value >> 16) & 0xFF) / 255.0, ((value >> 8) & 0xFF) / 255.0, (value & 0xFF) / 255.0


def mix(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def smooth(edge0, edge1, x):
    t = max(0.0, min(1.0, (x - edge0) / (edge1 - edge0)))
    return t * t * (3 - 2 * t)


# ---- distance primitives (pixel units, centre-origin coordinates) ----

def d_segment(px, py, ax, ay, bx, by):
    vx, vy = bx - ax, by - ay
    t = max(0.0, min(1.0, ((px - ax) * vx + (py - ay) * vy) / (vx * vx + vy * vy)))
    return math.hypot(px - (ax + vx * t), py - (ay + vy * t))


def d_polyline(px, py, points):
    return min(d_segment(px, py, *points[i], *points[i + 1]) for i in range(len(points) - 1))


def d_ring(px, py, cx, cy, r):
    return abs(math.hypot(px - cx, py - cy) - r)


def d_arc(px, py, cx, cy, r, a0, a1):
    angle = math.atan2(py - cy, px - cx)
    a = (angle - a0) % (2 * math.pi)
    span = (a1 - a0) % (2 * math.pi)
    if a <= span:
        return d_ring(px, py, cx, cy, r)
    e0 = (cx + r * math.cos(a0), cy + r * math.sin(a0))
    e1 = (cx + r * math.cos(a1), cy + r * math.sin(a1))
    return min(math.hypot(px - e0[0], py - e0[1]), math.hypot(px - e1[0], py - e1[1]))


def d_polygon_outline(px, py, points):
    closed = points + [points[0]]
    return d_polyline(px, py, closed)


def regular(cx, cy, r, n, rotation=0.0):
    return [(cx + r * math.cos(rotation + 2 * math.pi * i / n), cy + r * math.sin(rotation + 2 * math.pi * i / n)) for i in range(n)]


def d_round_rect(px, py, cx, cy, hw, hh, radius):
    qx = abs(px - cx) - hw + radius
    qy = abs(py - cy) - hh + radius
    outside = math.hypot(max(qx, 0.0), max(qy, 0.0))
    return outside + min(max(qx, qy), 0.0) - radius


def d_star(px, py, cx, cy, r_outer, r_inner, points=4, rotation=-math.pi / 2):
    verts = []
    for i in range(points * 2):
        r = r_outer if i % 2 == 0 else r_inner
        a = rotation + math.pi * i / points
        verts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return d_polygon_outline(px, py, verts), verts


def inside_polygon(px, py, verts):
    inside = False
    j = len(verts) - 1
    for i in range(len(verts)):
        xi, yi = verts[i]
        xj, yj = verts[j]
        if (yi > py) != (yj > py) and px < (xj - xi) * (py - yi) / (yj - yi) + xi:
            inside = not inside
        j = i
    return inside


# ---- canvas ----

class Icon:
    def __init__(self, accent):
        self.accent = hex_rgb(accent)
        self.rgb = [[(0.0, 0.0, 0.0)] * SIZE for _ in range(SIZE)]
        self.alpha = [[0.0] * SIZE for _ in range(SIZE)]
        self._plate()

    def _plate(self):
        dark = (0.03, 0.05, 0.1)
        for y in range(SIZE):
            for x in range(SIZE):
                px, py = x - 31.5, y - 31.5
                d = d_round_rect(px, py, 0, 0, 29, 29, 9)
                cover = 1.0 - smooth(-1.0, 1.0, d)
                if cover <= 0:
                    continue
                glow = math.exp(-(math.hypot(px, py) / 30.0) ** 2)
                col = mix(dark, self.accent, 0.28 * glow)
                rim = math.exp(-((d + 1.5) / 1.2) ** 2) * 0.55
                col = mix(col, self.accent, rim)
                self._over(x, y, col, cover * 0.92)

    def _over(self, x, y, col, a):
        if a <= 0.001:
            return
        old_a = self.alpha[y][x]
        out_a = a + old_a * (1 - a)
        old = self.rgb[y][x]
        self.rgb[y][x] = tuple((col[i] * a + old[i] * old_a * (1 - a)) / out_a for i in range(3))
        self.alpha[y][x] = out_a

    def _add(self, x, y, col, a):
        old = self.rgb[y][x]
        self.rgb[y][x] = tuple(min(1.0, old[i] + col[i] * a) for i in range(3))

    def stroke(self, dist_fn, width, color_fn, glow=5.0, glow_strength=0.55):
        for y in range(SIZE):
            for x in range(SIZE):
                px, py = x - 31.5, y - 31.5
                d = dist_fn(px, py)
                col = color_fn(px, py)
                g = math.exp(-(max(0.0, d - width) / glow) ** 2) * glow_strength
                self._add(x, y, col, g)
                cover = 1.0 - smooth(width - 0.8, width + 0.8, d)
                if cover > 0:
                    core = mix(col, (1, 1, 1), 0.35 * (1 - smooth(0, width, d)))
                    self._over(x, y, core, cover)

    def fill(self, inside_fn, color_fn, soft=0.0):
        for y in range(SIZE):
            for x in range(SIZE):
                px, py = x - 31.5, y - 31.5
                if inside_fn(px, py):
                    self._over(x, y, color_fn(px, py), 1.0)

    def save(self, name):
        rows = [[tuple(int(round(max(0, min(1, c)) * 255)) for c in self.rgb[y][x]) + (int(round(self.alpha[y][x] * 255)),)
                 for x in range(SIZE)] for y in range(SIZE)]
        write_png(os.path.join(ICONS, name + ".png"), SIZE, SIZE, rows)


def vertical(top, bottom):
    t0, t1 = hex_rgb(top), hex_rgb(bottom)
    return lambda px, py: mix(t0, t1, (py + 24) / 48.0)


def horizontal(left, right):
    l, r = hex_rgb(left), hex_rgb(right)
    return lambda px, py: mix(l, r, max(0.0, min(1.0, (px + 24) / 48.0)))


def solid(value):
    c = hex_rgb(value)
    return lambda px, py: c


# ---- glyphs ----

def phase_step():
    icon = Icon(0x9B5CFF)
    for i, offset in enumerate((-16, -4)):
        a = 0.45 + 0.55 * i
        pts = [(offset - 2, -13), (offset + 10, 0), (offset - 2, 13)]
        col = horizontal(0x9B5CFF, 0x5CE1FF)
        icon.stroke(lambda px, py, p=pts: d_polyline(px, py, p), 2.6 * a + 0.8, col, glow=4)
    for i in range(4):
        x0 = -24 + i * 4.5
        icon.stroke(lambda px, py, x=x0: d_segment(px, py, x, 0, x + 1.5, 0), 0.9, solid(0x9B5CFF), glow=2, glow_strength=0.3)
    icon.stroke(lambda px, py: d_ring(px, py, 17, 0, 5.5), 1.4, solid(0x5CE1FF))
    icon.fill(lambda px, py: math.hypot(px - 17, py) < 2.2, solid(0xFFFFFF))
    icon.save("phase_step")


def kinetic_leap():
    icon = Icon(0x7FD6FF)
    icon.stroke(lambda px, py: abs(math.hypot((px + 2) / 1.0, (py - 20) / 0.32) - 14) * 0.32, 1.4, solid(0x3A7BFF), glow=3)
    curve = [(-20 + t * 36, 18 - 36 * math.sin(math.pi * t * 0.5)) for t in [i / 24 for i in range(25)]]
    icon.stroke(lambda px, py: d_polyline(px, py, curve), 2.6, vertical(0xFFFFFF, 0x7FD6FF))
    tip = curve[-1]
    prev = curve[-4]
    ang = math.atan2(tip[1] - prev[1], tip[0] - prev[0])
    head = [(tip[0] - 9 * math.cos(ang - 0.55), tip[1] - 9 * math.sin(ang - 0.55)), tip,
            (tip[0] - 9 * math.cos(ang + 0.55), tip[1] - 9 * math.sin(ang + 0.55))]
    icon.stroke(lambda px, py: d_polyline(px, py, head), 2.2, solid(0xFFFFFF), glow=3, glow_strength=0.35)
    for dx in (-8, 0, 8):
        icon.stroke(lambda px, py, x=dx: d_segment(px, py, x - 12, 22, x - 14, 25), 0.8, solid(0x7FD6FF), glow=2, glow_strength=0.3)
    icon.save("kinetic_leap")


def shockwave():
    icon = Icon(0xFF8A3D)
    icon.stroke(lambda px, py: d_ring(px, py, 0, 0, 22), 1.5, solid(0xB0201A), glow=4)
    icon.stroke(lambda px, py: d_ring(px, py, 0, 0, 14), 2.0, solid(0xFF8A3D), glow=3, glow_strength=0.35)
    for i in range(8):
        a = math.pi * i / 4 + math.pi / 8
        icon.stroke(lambda px, py, a=a: d_segment(px, py, 17 * math.cos(a), 17 * math.sin(a), 20 * math.cos(a), 20 * math.sin(a)),
                    1.2, solid(0xFFC94D), glow=2, glow_strength=0.3)
    _, verts = d_star(0, 0, 0, 0, 9, 3.2, points=4)
    icon.fill(lambda px, py: inside_polygon(px, py, verts) or math.hypot(px, py) < 3.5, vertical(0xFFFFFF, 0xFFC94D))
    icon.stroke(lambda px, py: d_star(px, py, 0, 0, 9, 3.2, points=4)[0], 0.6, solid(0xFFF1C2), glow=3, glow_strength=0.3)
    icon.save("shockwave")


def aegis_field():
    icon = Icon(0x4DE8FF)
    outer = regular(0, 1, 23, 6, -math.pi / 2)
    inner = regular(0, 1, 15, 6, -math.pi / 2)
    icon.fill(lambda px, py: inside_polygon(px, py, outer), lambda px, py: mix(hex_rgb(0x0A2440), hex_rgb(0x123D66), (py + 23) / 46))
    icon.stroke(lambda px, py: d_polygon_outline(px, py, outer), 2.4, vertical(0xBDF7FF, 0x2058FF))
    icon.stroke(lambda px, py: d_polygon_outline(px, py, inner), 1.0, solid(0x4DE8FF), glow=3, glow_strength=0.4)
    icon.stroke(lambda px, py: d_segment(px, py, 0, -10, 0, 12), 1.2, solid(0x4DE8FF), glow=3, glow_strength=0.4)
    icon.stroke(lambda px, py: d_segment(px, py, -9, 1, 9, 1), 1.2, solid(0x4DE8FF), glow=3, glow_strength=0.4)
    icon.fill(lambda px, py: math.hypot(px, py - 1) < 3.2, solid(0xFFFFFF))
    icon.save("aegis_field")


def temporal_drag():
    icon = Icon(0xFFB020)
    icon.stroke(lambda px, py: d_ring(px, py, 0, 0, 21), 2.0, vertical(0xFFF1C2, 0xFFB020))
    for i in range(12):
        a = math.pi * i / 6
        r0 = 15.5 if i % 3 == 0 else 17.5
        icon.stroke(lambda px, py, a=a, r0=r0: d_segment(px, py, r0 * math.cos(a), r0 * math.sin(a), 19 * math.cos(a), 19 * math.sin(a)),
                    0.8, solid(0xFFC94D), glow=1.5, glow_strength=0.2)
    spiral = [((13 - 11 * t) * math.cos(-math.pi / 2 + t * 5.5), (13 - 11 * t) * math.sin(-math.pi / 2 + t * 5.5)) for t in [i / 40 for i in range(41)]]
    icon.stroke(lambda px, py: d_polyline(px, py, spiral), 1.0, solid(0xFFB020), glow=3, glow_strength=0.5)
    icon.stroke(lambda px, py: d_segment(px, py, 0, 0, 0, -12), 1.6, solid(0xFFFFFF))
    icon.stroke(lambda px, py: d_segment(px, py, 0, 0, 8, 4), 1.6, solid(0xFFFFFF))
    icon.fill(lambda px, py: math.hypot(px, py) < 2.4, solid(0xFFF1C2))
    icon.save("temporal_drag")


def fist(icon, cx, cy, scale, color_fn, alpha_glow=0.55, dark=0x3B2470, light=0x6A47C2):
    icon.fill(lambda px, py: d_round_rect(px, py, cx, cy, 6 * scale, 5 * scale, 2.2 * scale) < 0,
              lambda px, py: mix(hex_rgb(dark), hex_rgb(light), (py - cy + 5 * scale) / (10 * scale)))
    icon.stroke(lambda px, py: abs(d_round_rect(px, py, cx, cy, 6 * scale, 5 * scale, 2.2 * scale)), 1.0, color_fn, glow=3, glow_strength=alpha_glow)
    for k in (-1, 1):
        icon.stroke(lambda px, py, k=k: d_segment(px, py, cx + k * 2 * scale, cy - 4.5 * scale, cx + k * 2 * scale, cy - 1.5 * scale),
                    0.5, solid(0xFFD86B), glow=1, glow_strength=0.1)


def stand_manifest():
    icon = Icon(0xC77DFF)
    body = [(-17, 26), (-15, 10), (-8, 5), (8, 5), (15, 10), (17, 26)]
    icon.fill(lambda px, py: inside_polygon(px, py, body + [(17, 30), (-17, 30)]), vertical(0x5E3AA8, 0x2A1450))
    icon.stroke(lambda px, py: d_polyline(px, py, body), 1.3, solid(0xC77DFF), glow=4)
    icon.fill(lambda px, py: math.hypot(px / 0.9, py + 6) < 8.5, vertical(0x7D58D8, 0x4A2C94))
    icon.stroke(lambda px, py: d_ring(px / 0.9, py, 0, -6, 8.5), 1.0, solid(0xE6D0FF), glow=3)
    for ex in (-3.5, 3.5):
        icon.fill(lambda px, py, ex=ex: abs(px - ex) < 1.8 and abs(py + 6) < 1.0, solid(0xFFE27A))
    for r, a0, a1 in ((20, math.pi * 1.05, math.pi * 1.45), (20, math.pi * 1.55, math.pi * 1.95), (25, math.pi * 1.15, math.pi * 1.85)):
        icon.stroke(lambda px, py, r=r, a0=a0, a1=a1: d_arc(px, py, 0, 4, r, a0, a1), 1.0, solid(0xC77DFF), glow=3, glow_strength=0.45)
    icon.save("stand_manifest")


def ora_barrage():
    icon = Icon(0xC77DFF)
    for i in range(7):
        y = -20 + i * 6.5
        icon.stroke(lambda px, py, y=y: d_segment(px, py, -26, y, -14 + (i % 2) * 4, y), 0.6, solid(0xC77DFF), glow=2, glow_strength=0.25)
    for cx, cy, s in ((-6, -12, 0.75), (2, 10, 0.75), (10, -2, 1.05)):
        fist(icon, cx, cy, s, solid(0xE6D0FF))
    for cx, cy in ((22, -2), (17, -14), (17, 12)):
        icon.stroke(lambda px, py, cx=cx, cy=cy: d_star(px, py, cx, cy, 4.5, 1.5)[0], 0.5, solid(0xFFD86B), glow=2, glow_strength=0.6)
    icon.save("ora_barrage")


def star_finger():
    icon = Icon(0xC77DFF)
    icon.stroke(lambda px, py: d_ring(px, py, 6, 0, 18), 1.0, solid(0x7B4DFF), glow=3, glow_strength=0.4)
    for a in (0, math.pi / 2, math.pi, 3 * math.pi / 2):
        icon.stroke(lambda px, py, a=a: d_segment(px, py, 6 + 14 * math.cos(a), 14 * math.sin(a), 6 + 22 * math.cos(a), 22 * math.sin(a)),
                    1.0, solid(0xE6D0FF), glow=2)
    icon.stroke(lambda px, py: d_segment(px, py, -26, 0, -12, 0), 1.2, solid(0xC77DFF), glow=3)
    fist(icon, -4, 0, 1.2, solid(0xFFFFFF), 0.7)
    icon.fill(lambda px, py: inside_polygon(px, py, d_star(0, 0, 13, 0, 7, 2.0)[1]), vertical(0xFFFFFF, 0xFFD86B))
    icon.save("star_finger")


def star_guard():
    icon = Icon(0xC77DFF)
    icon.stroke(lambda px, py: d_arc(px, py, 0, 8, 25, math.pi * 1.15, math.pi * 1.85), 2.0, solid(0x4DE8FF), glow=4)
    for k in (-1, 1):
        icon.fill(lambda px, py, k=k: d_segment(px, py, -16 * k, 18, 14 * k, -10) < 4.2, vertical(0x6A47C2, 0x3B2470))
        icon.stroke(lambda px, py, k=k: abs(d_segment(px, py, -16 * k, 18, 14 * k, -10) - 4.2), 0.8, solid(0xE6D0FF), glow=2)
    icon.stroke(lambda px, py: d_segment(px, py, 0, -24, 0, -19), 1.6, solid(0xFFD86B), glow=3, glow_strength=0.7)
    for dx in (-5, 5):
        icon.stroke(lambda px, py, dx=dx: d_segment(px, py, dx * 0.6, -22, dx, -26), 0.5, solid(0xFFF1C2), glow=1.5)
    icon.save("star_guard")


def star_platinum_the_world():
    icon = Icon(0x5C8DFF)
    for i in range(10):
        a0 = 2 * math.pi * i / 10 + 0.08
        icon.stroke(lambda px, py, a0=a0: d_arc(px, py, 0, 0, 23, a0, a0 + 0.45), 1.6, vertical(0xBFD4FF, 0x5C8DFF), glow=3)
    icon.stroke(lambda px, py: d_ring(px, py, 0, 0, 17), 0.8, solid(0x8FB8FF), glow=2, glow_strength=0.3)
    icon.stroke(lambda px, py: d_segment(px, py, 0, 0, 0, -14), 1.3, solid(0xFFFFFF))
    icon.stroke(lambda px, py: d_segment(px, py, 0, 0, -10, 6), 1.3, solid(0xFFFFFF))
    verts = d_star(0, 0, 0, 0, 9, 3.5, points=5)[1]
    icon.fill(lambda px, py: inside_polygon(px, py, verts), vertical(0xFFF1C2, 0xFFC94D))
    icon.stroke(lambda px, py: d_star(px, py, 0, 0, 9, 3.5, points=5)[0], 0.5, solid(0xFFF1C2), glow=3, glow_strength=0.25)
    icon.save("star_platinum_the_world")


def barrage(name, accent, dark, light, outline, spark):
    icon = Icon(accent)
    for i in range(7):
        y = -20 + i * 6.5
        icon.stroke(lambda px, py, y=y, i=i: d_segment(px, py, -26, y, -14 + (i % 2) * 4, y), 0.6, solid(accent), glow=2, glow_strength=0.25)
    for cx, cy, s in ((-6, -12, 0.75), (2, 10, 0.75), (10, -2, 1.05)):
        fist(icon, cx, cy, s, solid(outline), dark=dark, light=light)
    for cx, cy in ((22, -2), (17, -14), (17, 12)):
        icon.stroke(lambda px, py, cx=cx, cy=cy: d_star(px, py, cx, cy, 4.5, 1.5)[0], 0.5, solid(spark), glow=2, glow_strength=0.6)
    icon.save(name)


def knife_throw():
    icon = Icon(0xFFD34D)
    for angle in (-0.65, 0.0, 0.65):
        ca, sa = math.cos(angle - math.pi / 2), math.sin(angle - math.pi / 2)
        def rot(x, y, ca=ca, sa=sa):
            return (x * ca - y * sa, x * sa + y * ca + 12)
        blade = [rot(-12, -2.5), rot(16, 0), rot(-12, 2.5)]
        icon.fill(lambda px, py, b=blade: inside_polygon(px, py, b), vertical(0xFFFFFF, 0xB8C4CC))
        icon.stroke(lambda px, py, b=blade: d_polygon_outline(px, py, b), 0.5, solid(0xE6F0FF), glow=2, glow_strength=0.4)
        h0, h1 = rot(-12, 0), rot(-22, 0)
        icon.stroke(lambda px, py, h0=h0, h1=h1: d_segment(px, py, *h0, *h1), 2.0, solid(0xE8B020))
    icon.save("knife_throw")


def za_warudo():
    icon = Icon(0xFFD34D)
    for i in range(12):
        a0 = 2 * math.pi * i / 12 + 0.06
        icon.stroke(lambda px, py, a0=a0: d_arc(px, py, 0, 0, 23, a0, a0 + 0.36), 1.6, vertical(0xFFF1C2, 0xE8B020), glow=3)
    icon.stroke(lambda px, py: d_segment(px, py, 0, 0, 0, -15), 1.4, solid(0xFFFFFF))
    icon.stroke(lambda px, py: d_segment(px, py, 0, 0, 11, 3), 1.4, solid(0xFFFFFF))
    heart = lambda px, py: (((px / 9) ** 2 + ((py + 2) / 9) ** 2 - 1) ** 3 - (px / 9) ** 2 * ((py + 2) / -9) ** 3) < 0
    icon.fill(heart, vertical(0x5CBF5C, 0x2F4F2F))
    icon.save("za_warudo")


def first_bomb():
    icon = Icon(0xFF8EC7)
    icon.fill(lambda px, py: math.hypot(px + 2, py - 4) < 13, vertical(0x8C5A78, 0x4A2A40))
    icon.stroke(lambda px, py: d_ring(px, py, -2, 4, 13), 1.2, solid(0xFF8EC7), glow=3)
    fuse = [(6, -6), (12, -14), (18, -16)]
    icon.stroke(lambda px, py: d_polyline(px, py, fuse), 1.0, solid(0xE6E1EA), glow=2, glow_strength=0.3)
    icon.stroke(lambda px, py: d_star(px, py, 20, -18, 6, 2)[0], 0.6, solid(0xFFD86B), glow=3, glow_strength=0.8)
    for dx, dy in ((-6, 2), (2, 2)):
        icon.fill(lambda px, py, dx=dx, dy=dy: math.hypot(px - dx, py - dy) < 2.2, solid(0xF2F2F2))
    icon.fill(lambda px, py: abs(px + 2) < 4 and abs(py - 9) < 1, solid(0xF2F2F2))
    icon.save("first_bomb")


def detonate():
    icon = Icon(0xFF8EC7)
    verts = d_star(0, 0, 0, 0, 24, 9, points=8)[1]
    icon.fill(lambda px, py: inside_polygon(px, py, verts), lambda px, py: mix(hex_rgb(0xFFF1C2), hex_rgb(0xFF5E9A), min(1.0, math.hypot(px, py) / 22)))
    icon.stroke(lambda px, py: d_star(px, py, 0, 0, 24, 9, points=8)[0], 0.8, solid(0xFF8EC7), glow=4)
    icon.fill(lambda px, py: math.hypot(px, py) < 5, solid(0xFFFFFF))
    icon.save("detonate")


def bites_the_dust():
    icon = Icon(0xFF8EC7)
    icon.stroke(lambda px, py: d_arc(px, py, 0, 0, 19, math.pi * 0.25, math.pi * 1.95), 2.0, vertical(0xFFC8E4, 0xC0608E), glow=3)
    tip = (19 * math.cos(math.pi * 0.25), 19 * math.sin(math.pi * 0.25))
    head = [(tip[0] - 8, tip[1] - 2), tip, (tip[0] + 1, tip[1] - 8)]
    icon.stroke(lambda px, py: d_polyline(px, py, head), 2.0, solid(0xFFE6F2))
    icon.fill(lambda px, py: math.hypot(px, py + 1) < 8, vertical(0xF2F2F2, 0xBFB5C6))
    for dx in (-3, 3):
        icon.fill(lambda px, py, dx=dx: math.hypot(px - dx, py + 2) < 1.8, solid(0x3A2030))
    icon.fill(lambda px, py: abs(px) < 3.5 and 4 < py < 7 and int(px + 10) % 2 == 0, solid(0xF2F2F2))
    icon.save("bites_the_dust")


def life_giver():
    icon = Icon(0x8BE36A)
    icon.stroke(lambda px, py: d_segment(px, py, 0, 24, 0, -2), 1.6, solid(0x3E9B4F), glow=2)
    for k in (-1, 1):
        leaf = [(0, -2), (k * 8, -10), (k * 16, -8), (k * 10, 2), (0, 2)]
        icon.fill(lambda px, py, l=leaf: inside_polygon(px, py, l), vertical(0xB8FF8A, 0x3E9B4F))
    for bx, by in ((-16, -18), (14, -22), (20, 4)):
        icon.fill(lambda px, py, bx=bx, by=by: math.hypot((px - bx) / 1.3, py - by) < 3.2, solid(0xFFD24A))
        icon.stroke(lambda px, py, bx=bx, by=by: d_segment(px, py, bx - 1, by - 3, bx - 1, by + 3), 0.5, solid(0x2A2010), glow=0.5, glow_strength=0.0)
    icon.save("life_giver")


def healing_field():
    icon = Icon(0x8BE36A)
    icon.stroke(lambda px, py: d_ring(px, py, 0, 0, 22), 1.2, solid(0x8BE36A), glow=4)
    icon.stroke(lambda px, py: d_ring(px, py, 0, 0, 15), 0.6, solid(0x3E9B4F), glow=2, glow_strength=0.3)
    plus = lambda px, py: (abs(px) < 3.5 and abs(py) < 11) or (abs(py) < 3.5 and abs(px) < 11)
    icon.fill(plus, vertical(0xFFFFFF, 0x8BE36A))
    icon.save("healing_field")


def king_crimson_chop():
    icon = Icon(0xE0284A)
    icon.stroke(lambda px, py: d_segment(px, py, -22, 22, 22, -22), 2.2, vertical(0xFFFFFF, 0xE0284A), glow=4)
    fist(icon, -4, 4, 1.2, solid(0xFFD0D6), 0.7, dark=0x5A0A16, light=0xC0283A)
    icon.save("king_crimson_chop")


def epitaph():
    icon = Icon(0xE0284A)
    eye = lambda px, py: (px / 22) ** 2 + (py / 11) ** 2 < 1 and abs(py) < 11 * math.cos(px / 22 * math.pi / 2)
    icon.fill(eye, vertical(0xF2EEEE, 0xC8B8B8))
    icon.stroke(lambda px, py: abs(math.hypot(px / 22, py / 11) - 1) * 10, 0.8, solid(0xE0284A), glow=3)
    icon.fill(lambda px, py: math.hypot(px, py) < 7.5, vertical(0x5CFFB0, 0x1A7A4A))
    icon.fill(lambda px, py: math.hypot(px, py) < 3, solid(0x101010))
    icon.save("epitaph")


def time_erase():
    icon = Icon(0xE0284A)
    icon.stroke(lambda px, py: d_arc(px, py, 0, 0, 20, math.pi * 0.15, math.pi * 1.35), 2.0, solid(0xF2EEEE), glow=3)
    icon.stroke(lambda px, py: d_arc(px, py, 0, 0, 20, math.pi * 1.55, math.pi * 1.95), 2.0, solid(0xF2EEEE), glow=3)
    icon.stroke(lambda px, py: d_segment(px, py, -24, 10, 24, -10), 2.4, solid(0xE0284A), glow=4, glow_strength=0.7)
    icon.stroke(lambda px, py: d_segment(px, py, 0, 0, 0, -12), 1.2, solid(0xFFFFFF))
    icon.save("time_erase")


def heaven_acceleration():
    icon = Icon(0xB8FFE0)
    for i, x in enumerate((-16, -4, 8)):
        pts = [(x - 6, -14), (x + 6, 0), (x - 6, 14)]
        icon.stroke(lambda px, py, p=pts: d_polyline(px, py, p), 1.6 + i * 0.6, horizontal(0x4CC59B, 0xFFFFFF), glow=3)
    icon.save("heaven_acceleration")


def time_acceleration():
    icon = Icon(0xFFF6C0)
    icon.fill(lambda px, py: math.hypot(px - 8, py + 6) < 8, vertical(0xFFFFFF, 0xFFC94D))
    for r in (14, 20, 26):
        icon.stroke(lambda px, py, r=r: d_arc(px, py, 8, -6, r, math.pi * 0.55, math.pi * 1.05), 1.0, solid(0x4CC59B), glow=2, glow_strength=0.4)
    icon.fill(lambda px, py: math.hypot(px + 14, py - 14) < 5 and math.hypot(px + 11, py - 16) > 4.5, solid(0xE6F0FF))
    icon.save("time_acceleration")


def heaven_blink():
    icon = Icon(0xB8FFE0)
    for i in range(5):
        x = -22 + i * 6
        icon.stroke(lambda px, py, x=x: d_segment(px, py, x, -10 + i * 0, x + 2, 10), 0.8, solid(0x4CC59B), glow=2, glow_strength=0.2 + i * 0.1)
    icon.fill(lambda px, py: math.hypot((px - 12) / 0.8, py) < 10, vertical(0xFFFFFF, 0xB9C0CC))
    icon.stroke(lambda px, py: d_ring(px, py, 12, 0, 10), 1.0, solid(0x5CFFB0), glow=3)
    icon.save("heaven_blink")


def nail_shot():
    icon = Icon(0xFF9ED0)
    spiral = [((2 + 1.4 * t) * math.cos(t * 1.4), (2 + 1.4 * t) * math.sin(t * 1.4)) for t in [i / 4 for i in range(56)]]
    icon.stroke(lambda px, py: d_polyline(px, py, spiral), 0.9, solid(0xFF9ED0), glow=3, glow_strength=0.5)
    icon.stroke(lambda px, py: d_segment(px, py, -20, 20, 14, -14), 1.8, vertical(0xFFFFFF, 0xF5D36B))
    icon.save("nail_shot")


def golden_rotation():
    icon = Icon(0xF5D36B)
    phi = (1 + 5 ** 0.5) / 2
    spiral = [(0.9 * phi ** (t / (math.pi / 2)) * math.cos(t), 0.9 * phi ** (t / (math.pi / 2)) * math.sin(t)) for t in [i / 10 for i in range(0, 95)]]
    icon.stroke(lambda px, py: d_polyline(px, py, spiral), 1.6, vertical(0xFFF1C2, 0xE8B020), glow=4)
    icon.fill(lambda px, py: math.hypot(px, py) < 3, solid(0xFFFFFF))
    icon.save("golden_rotation")


def infinite_rotation():
    icon = Icon(0xFF9ED0)
    curve = []
    for i in range(240):
        t = 2 * math.pi * i / 240
        d = 1 + math.sin(t) ** 2
        curve.append((22 * math.cos(t) / d, 22 * math.sin(t) * math.cos(t) / d))
    icon.stroke(lambda px, py: d_polyline(px, py, curve), 2.2, horizontal(0xFF9ED0, 0xF5D36B), glow=4)
    for cx, cy in ((-14, -10), (14, 10)):
        icon.stroke(lambda px, py, cx=cx, cy=cy: d_arc(px, py, cx, cy, 5, 0.3, 4.5), 0.8, solid(0xFFFFFF), glow=2)
    icon.save("infinite_rotation")


def main():
    for glyph in (phase_step, kinetic_leap, shockwave, aegis_field, temporal_drag,
                  stand_manifest, ora_barrage, star_finger, star_guard, star_platinum_the_world,
                  knife_throw, za_warudo, first_bomb, detonate, bites_the_dust, life_giver, healing_field,
                  king_crimson_chop, epitaph, time_erase, heaven_acceleration, time_acceleration, heaven_blink,
                  nail_shot, golden_rotation, infinite_rotation):
        glyph()
    barrage("muda_barrage", 0xFFD34D, 0x7A5A10, 0xF1C232, 0xFFF1C2, 0x5CBF5C)
    barrage("gold_experience_barrage", 0x8BE36A, 0x8A6A10, 0xF2C94C, 0xFFF6C0, 0xE0303A)
    print("Icons written to", os.path.normpath(ICONS))


if __name__ == "__main__":
    main()
