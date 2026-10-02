"""Original icons for cursed techniques, superhero powers, suits and the Mystic Arts.

Each icon composes a few motif primitives (beam, ring, bolt, flake, flame, shield, hand, eye...) over the shared
ability plate from generate_icons.py, so the whole set reads as one family.
"""
import math

from generate_icons import (Icon, d_arc, d_polyline, d_ring, d_segment, d_star, horizontal, inside_polygon, regular,
                            solid, vertical)


def ring(i, r, c, w=1.6, cx=0, cy=0):
    i.stroke(lambda px, py: d_ring(px, py, cx, cy, r), w, solid(c), glow=4)


def orb(i, r, inner, outer, cx=0, cy=0):
    i.fill(lambda px, py: math.hypot(px - cx, py - cy) < r, lambda px, py: [a + (b - a) * min(1, math.hypot(px - cx, py - cy) / r)
                                                                         for a, b in zip(_rgb(inner), _rgb(outer))])
    i.stroke(lambda px, py: d_ring(px, py, cx, cy, r), 0.6, solid(outer), glow=5, glow_strength=0.6)


def _rgb(v):
    return ((v >> 16 & 255) / 255, (v >> 8 & 255) / 255, (v & 255) / 255)


def beam(i, c, w=3.0, a=(-22, 18), b=(22, -18)):
    i.stroke(lambda px, py: d_segment(px, py, *a, *b), w, horizontal(0xFFFFFF, c), glow=5, glow_strength=0.7)


def bolt(i, c, pts=None, w=1.8):
    pts = pts or [(-6, -24), (4, -6), (-4, -2), (8, 22)]
    i.stroke(lambda px, py: d_polyline(px, py, pts), w, vertical(0xFFFFFF, c), glow=4)


def rays(i, c, n=8, r0=14, r1=22, w=1.2, rot=0.0):
    for k in range(n):
        a = rot + 2 * math.pi * k / n
        i.stroke(lambda px, py, a=a: d_segment(px, py, r0 * math.cos(a), r0 * math.sin(a), r1 * math.cos(a), r1 * math.sin(a)),
                 w, solid(c), glow=2, glow_strength=0.35)


def flake(i, c, r=18):
    for k in range(6):
        a = math.pi * k / 3
        ex, ey = r * math.cos(a), r * math.sin(a)
        i.stroke(lambda px, py, ex=ex, ey=ey: d_segment(px, py, 0, 0, ex, ey), 1.3, solid(c), glow=3)
        for s in (-1, 1):
            mx, my = ex * 0.6, ey * 0.6
            b = a + s * 0.7
            i.stroke(lambda px, py, mx=mx, my=my, b=b: d_segment(px, py, mx, my, mx + 6 * math.cos(b), my + 6 * math.sin(b)),
                     0.9, solid(c), glow=2)


def flame(i, top, bottom, s=1.0, cx=0, cy=4):
    def inside(px, py):
        x, y = (px - cx) / s, (py - cy) / s
        if y > 14 or y < -22:
            return False
        half = 11 * math.sqrt(max(0, (14 - y) / 36)) * (1 - max(0, -y - 4) / 18) + (2 * math.sin(y * 0.5) if y < 0 else 0)
        return abs(x) < max(0, half)
    i.fill(inside, vertical(top, bottom))


def shield(i, c, cx=0, cy=0, s=1.0, fill=None):
    pts = [(cx - 16 * s, cy - 18 * s), (cx + 16 * s, cy - 18 * s), (cx + 14 * s, cy + 2 * s), (cx, cy + 20 * s), (cx - 14 * s, cy + 2 * s),
           (cx - 16 * s, cy - 18 * s)]
    if fill:
        i.fill(lambda px, py: inside_polygon(px, py, pts), vertical(fill, 0x101020))
    i.stroke(lambda px, py: d_polyline(px, py, pts), 1.8, solid(c), glow=4)


def hand(i, c, cx=0, cy=4):
    i.fill(lambda px, py: math.hypot((px - cx) / 1.1, py - cy - 4) < 9, solid(c))
    for k, fx in enumerate((-7, -2.5, 2, 6.5)):
        h = (13, 17, 16, 12)[k]
        i.stroke(lambda px, py, fx=fx, h=h: d_segment(px, py, cx + fx, cy, cx + fx, cy - h), 2.1, solid(c), glow=2, glow_strength=0.3)
    i.stroke(lambda px, py: d_segment(px, py, cx - 9, cy + 4, cx - 15, cy - 4), 2.1, solid(c), glow=2, glow_strength=0.3)


def eye(i, c, iris):
    i.stroke(lambda px, py: abs(math.hypot(px, py * 2.0) - 20) / 2.0, 1.4, solid(c), glow=4)
    orb(i, 7, 0xFFFFFF, iris)
    i.fill(lambda px, py: math.hypot(px, py) < 2.5, solid(0x000000))


def arrow(i, c, a=(-18, 18), b=(18, -18)):
    i.stroke(lambda px, py: d_segment(px, py, *a, *b), 2.4, solid(c), glow=4)
    ang = math.atan2(b[1] - a[1], b[0] - a[0])
    head = [(b[0] - 10 * math.cos(ang - 0.5), b[1] - 10 * math.sin(ang - 0.5)), b, (b[0] - 10 * math.cos(ang + 0.5), b[1] - 10 * math.sin(ang + 0.5))]
    i.stroke(lambda px, py: d_polyline(px, py, head), 2.2, solid(0xFFFFFF), glow=3)


def spiral(i, c1, c2, turns=2.5, r=22):
    pts = [(r * t / (turns * 2 * math.pi) * math.cos(t), r * t / (turns * 2 * math.pi) * math.sin(t))
           for t in [k * turns * 2 * math.pi / 160 for k in range(161)]]
    i.stroke(lambda px, py: d_polyline(px, py, pts), 1.8, horizontal(c1, c2), glow=4)


def slash(i, c, offset=0, ang=-0.8, w=1.6):
    dx, dy = math.cos(ang) * 22, math.sin(ang) * 22
    nx, ny = -math.sin(ang) * offset, math.cos(ang) * offset
    i.stroke(lambda px, py: d_segment(px, py, -dx + nx, -dy + ny, dx + nx, dy + ny), w, solid(c), glow=3)


def wave(i, c, y=0, amp=5, w=1.6):
    pts = [(x, y + amp * math.sin(x * 0.3)) for x in range(-22, 23)]
    i.stroke(lambda px, py: d_polyline(px, py, pts), w, solid(c), glow=3)


def mandala(i, c, r=20):
    ring(i, r, c, 1.2)
    ring(i, r * 0.7, c, 1.0)
    for rot in (0, math.pi / 4):
        pts = regular(0, 0, r * 0.95, 4, rot)
        i.stroke(lambda px, py, p=pts: d_polyline(px, py, p + [p[0]]), 0.9, solid(c), glow=3)


ICONS = {}


def icon(name, accent):
    def wrap(fn):
        ICONS[name] = (accent, fn)
        return fn
    return wrap


# ---------- cursed ----------
@icon("cursed_reinforcement", 0x4A6BFF)
def _(i): hand(i, 0x4A6BFF); ring(i, 24, 0x8FA8FF, 1.0)
@icon("black_flash", 0xFF1030)
def _(i): orb(i, 8, 0x000000, 0x200008); bolt(i, 0xFF1030, [(-22, -8), (-6, -2), (-10, 6), (22, 10)], 2.2); bolt(i, 0x101010, [(-14, 20), (0, 4), (16, -20)], 1.6)
@icon("reversed_cursed_technique", 0xE8F4FF)
def _(i): ring(i, 16, 0xE8F4FF, 1.6); i.stroke(lambda px, py: d_segment(px, py, 0, -10, 0, 10), 3, solid(0xFFFFFF)); i.stroke(lambda px, py: d_segment(px, py, -10, 0, 10, 0), 3, solid(0xFFFFFF))
@icon("infinity", 0xBFD4FF)
def _(i):
    pts = [(20 * math.cos(t) / (1 + math.sin(t) ** 2), 20 * math.sin(t) * math.cos(t) / (1 + math.sin(t) ** 2)) for t in [k * 2 * math.pi / 200 for k in range(201)]]
    i.stroke(lambda px, py: d_polyline(px, py, pts), 2.2, horizontal(0xFFFFFF, 0x7FB0FF), glow=5)
@icon("blue", 0x2A6BFF)
def _(i): spiral(i, 0x9FD0FF, 0x2A6BFF); orb(i, 6, 0xFFFFFF, 0x2A6BFF)
@icon("red", 0xFF2A3A)
def _(i): orb(i, 10, 0xFFFFFF, 0xFF2A3A); rays(i, 0xFF6A6A, 10, 14, 23)
@icon("hollow_purple", 0xA040FF)
def _(i): orb(i, 13, 0xF0D8FF, 0xA040FF); orb(i, 4, 0x6AA0FF, 0x2A3AFF, -16, 14); orb(i, 4, 0xFF8080, 0xFF2A3A, 16, -14)
@icon("domain_unlimited_void", 0x7FB0FF)
def _(i): ring(i, 22, 0xFFFFFF, 1.0); eye(i, 0x7FB0FF, 0x2A6BFF); rays(i, 0xBFD4FF, 16, 23, 26, 0.6)
@icon("dismantle", 0xFF3040)
def _(i): [slash(i, 0xFF3040, o) for o in (-9, 0, 9)]
@icon("cleave", 0xB0101A)
def _(i): slash(i, 0xFF6060, 0, -0.8, 2.6); slash(i, 0xB0101A, 0, 0.8, 1.4)
@icon("fuga", 0xFF7A1A)
def _(i): flame(i, 0xFFF0A0, 0xFF5A10); arrow(i, 0xFFB040, (-20, 20), (20, -20))
@icon("domain_malevolent_shrine", 0xD0102A)
def _(i):
    i.stroke(lambda px, py: d_polyline(px, py, [(-22, -10), (0, -22), (22, -10)]), 2.0, solid(0xD0102A), glow=4)
    for x in (-14, 14):
        i.stroke(lambda px, py, x=x: d_segment(px, py, x, -12, x, 20), 2.2, solid(0xD0102A), glow=3)
    i.stroke(lambda px, py: d_segment(px, py, -22, 2, 22, 2), 1.4, solid(0xFF6070))
    eye(i, 0x00000000 | 0x601020, 0xFF2030) if False else orb(i, 5, 0xFF8080, 0xD0102A, 0, 10)
@icon("divine_dogs", 0x3A3A5A)
def _(i):
    for s in (-1, 1):
        i.stroke(lambda px, py, s=s: d_polyline(px, py, [(s * 4, -4), (s * 16, -20), (s * 18, 2)]), 2.0, solid(0xE0E0FF), glow=3)
    eye(i, 0x8080C0, 0x3A3A5A)
@icon("nue", 0xFFF07A)
def _(i):
    i.stroke(lambda px, py: d_polyline(px, py, [(-24, -2), (-10, -12), (0, -2), (10, -12), (24, -2)]), 2.2, solid(0xC0C0E0), glow=3)
    bolt(i, 0xFFF07A, [(0, -2), (-5, 8), (3, 10), (-2, 22)])
@icon("shadow_storage", 0x3A3A5A)
def _(i): i.fill(lambda px, py: math.hypot(px, (py - 10) * 2.5) < 20, solid(0x101018)); arrow(i, 0x8080C0, (0, -20), (0, 8))
@icon("domain_chimera_shadow_garden", 0x3A3A5A)
def _(i): i.fill(lambda px, py: py > 4 + 3 * math.sin(px * 0.4) and abs(px) < 25 and py < 25, vertical(0x303050, 0x08080C)); [eye(i, 0x8080C0, 0x3A3A5A) for _ in (0,)]
@icon("absorb_spirit", 0x6A3FA0)
def _(i): spiral(i, 0xD0A0FF, 0x6A3FA0, 2.0); orb(i, 5, 0x101010, 0x6A3FA0)
@icon("release_spirit", 0x6A3FA0)
def _(i): orb(i, 6, 0xD0A0FF, 0x6A3FA0); rays(i, 0xA070E0, 6, 10, 22, 1.8)
@icon("maximum_uzumaki", 0x6A3FA0)
def _(i): spiral(i, 0x6A3FA0, 0xFFFFFF, 3.5); ring(i, 24, 0xA070E0, 0.8)
@icon("piercing_blood", 0xB00018)
def _(i): beam(i, 0xB00018, 2.0); orb(i, 4, 0xFF5060, 0xB00018, -16, 13)
@icon("supernova", 0xB00018)
def _(i): orb(i, 7, 0xFF8090, 0xB00018); [orb(i, 3, 0xFF5060, 0xB00018, 16 * math.cos(a), 16 * math.sin(a)) for a in [k * math.pi / 3 for k in range(6)]]
@icon("flowing_red_scale", 0xB00018)
def _(i): hand(i, 0xB00018); wave(i, 0xFF5060, 18, 2)
@icon("dont_move", 0xE6E6FF)
def _(i): i.stroke(lambda px, py: abs(math.hypot(px, py * 2.2) - 16) / 2, 2, solid(0xE6E6FF)); [i.stroke(lambda px, py, x=x: d_segment(px, py, x, -2, x, 2), 1.2, solid(0xFFFFFF)) for x in (-8, 0, 8)]; i.stroke(lambda px, py: d_segment(px, py, -20, 18, 20, -18), 1.6, solid(0xFF4040))
@icon("blast_away", 0xE6E6FF)
def _(i): [i.stroke(lambda px, py, r=r: d_arc(px, py, -14, 0, r, -0.8, 0.8), 1.4, solid(0xE6E6FF), glow=3) for r in (10, 18, 26)]
@icon("twist", 0xE6E6FF)
def _(i): spiral(i, 0xFFFFFF, 0xA0A0C0, 1.5, 18); slash(i, 0xFF4040, 0, 0.8, 1.0)

# ---------- mutants ----------
@icon("telekinetic_grip", 0xD070FF)
def _(i): hand(i, 0xD070FF, 0, 8); orb(i, 5, 0xFFFFFF, 0xD070FF, 0, -16)
@icon("telekinetic_push", 0xD070FF)
def _(i): hand(i, 0xD070FF, -8, 6); [i.stroke(lambda px, py, r=r: d_arc(px, py, 0, 0, r, -0.9, 0.9), 1.2, solid(0xF0C0FF), glow=3) for r in (14, 20)]
@icon("kinetic_barrier", 0xD070FF)
def _(i): ring(i, 20, 0xD070FF, 2.0); ring(i, 15, 0xF0C0FF, 0.8)
@icon("mind_scan", 0xFF5AD0)
def _(i): eye(i, 0xFF5AD0, 0xFF5AD0); ring(i, 24, 0xFFA0E6, 0.7)
@icon("pacify", 0xFF9AE6)
def _(i): wave(i, 0xFF9AE6, -6, 3); wave(i, 0xFF9AE6, 6, 3); orb(i, 3, 0xFFFFFF, 0xFF9AE6)
@icon("psychic_blast", 0xFF5AD0)
def _(i): beam(i, 0xFF5AD0, 3.4); ring(i, 8, 0xFFFFFF, 1.0, -14, 12)
@icon("chain_lightning", 0x9FD8FF)
def _(i): bolt(i, 0x9FD8FF, [(-22, -16), (-8, -4), (-12, 6), (6, 2), (2, 12), (22, 18)]); [orb(i, 3, 0xFFFFFF, 0x9FD8FF, x, y) for x, y in ((-22, -16), (6, 2), (22, 18))]
@icon("thunder_strike", 0x9FD8FF)
def _(i): bolt(i, 0x9FD8FF, [(-4, -24), (6, -6), (-6, -2), (4, 22)], 3.0); rays(i, 0x9FD8FF, 6, 18, 24, 1.0)
@icon("static_field", 0x9FD8FF)
def _(i): ring(i, 18, 0x9FD8FF, 1.2); [bolt(i, 0xFFFFFF, [(18 * math.cos(a), 18 * math.sin(a)), (10 * math.cos(a + 0.3), 10 * math.sin(a + 0.3)), (4 * math.cos(a), 4 * math.sin(a))], 1.0) for a in [k * math.pi / 2 for k in range(4)]]
@icon("ice_shard", 0xA8E8FF)
def _(i):
    pts = [(-20, 20), (-4, 2), (16, -18), (2, -6), (-20, 20)]
    i.fill(lambda px, py: inside_polygon(px, py, pts), vertical(0xFFFFFF, 0x6AB0E0)); i.stroke(lambda px, py: d_polyline(px, py, pts), 1, solid(0xE0F8FF))
@icon("flash_freeze", 0xA8E8FF)
def _(i): flake(i, 0xA8E8FF)
@icon("ice_path", 0xA8E8FF)
def _(i):
    pts = [(-24, 20), (-8, 4), (8, 4), (24, 20)]
    i.fill(lambda px, py: inside_polygon(px, py, pts), vertical(0xE0F8FF, 0x6AB0E0)); flake(i, 0xFFFFFF, 8)
@icon("fire_blast", 0xFF7A1A)
def _(i): beam(i, 0xFF7A1A, 3.0); flame(i, 0xFFF0A0, 0xFF5A10, 0.5, 14, -8)
@icon("flame_wave", 0xFF5A1A)
def _(i): [flame(i, 0xFFE080, 0xFF3A10, 0.55, x, 6) for x in (-14, 0, 14)]
@icon("heat_aura", 0xFF7A1A)
def _(i): ring(i, 20, 0xFF7A1A, 1.4); flame(i, 0xFFF0A0, 0xFF5A10, 0.7)
@icon("magnetic_pull", 0xC02040)
def _(i):
    i.stroke(lambda px, py: d_arc(px, py, 0, 2, 12, math.pi, 2 * math.pi) if py <= 2 else min(abs(px - 12), abs(px + 12)) + max(0, py - 18), 4, solid(0xC02040), glow=3)
    [i.stroke(lambda px, py, x=x: d_segment(px, py, x - 3, 18, x + 3, 18), 2, solid(0xE0E0E0)) for x in (-12, 12)]
@icon("magnetic_repulse", 0xC02040)
def _(i): orb(i, 6, 0xFF8080, 0xC02040); [arrow(i, 0xC02040, (8 * math.cos(a), 8 * math.sin(a)), (22 * math.cos(a), 22 * math.sin(a))) for a in [k * math.pi / 2 + 0.78 for k in range(4)]]
@icon("magnetic_shield", 0xC02040)
def _(i): ring(i, 20, 0xC02040, 2.0); ring(i, 14, 0x8090A0, 1.2)
@icon("kryptonian_flight", 0x3A6BFF)
def _(i): arrow(i, 0xFFFFFF, (0, 22), (0, -18)); i.stroke(lambda px, py: d_polyline(px, py, [(-14, 4), (-4, 12), (-18, 22)]), 2.0, solid(0xD02020), glow=3)
@icon("heat_vision", 0xFF2020)
def _(i): beam(i, 0xFF2020, 1.6, (-12, -4), (22, 10)); beam(i, 0xFF2020, 1.6, (-12, 6), (22, 20)); orb(i, 4, 0xFFFFFF, 0xFF2020, -14, -4); orb(i, 4, 0xFFFFFF, 0xFF2020, -14, 6)
@icon("super_strength", 0x3A6BFF)
def _(i):
    pts = [(-14, -14), (14, -14), (20, -4), (0, 20), (-20, -4), (-14, -14)]
    i.fill(lambda px, py: inside_polygon(px, py, pts), vertical(0xFFD040, 0xD02020)); i.stroke(lambda px, py: d_polyline(px, py, pts), 1.2, solid(0xFFFFFF))
@icon("super_speed", 0xFFD84A)
def _(i): [i.stroke(lambda px, py, y=y: d_segment(px, py, -22 + abs(y), y, 10, y), 1.4, solid(0xFFD84A), glow=3) for y in (-10, -3, 4, 11)]; bolt(i, 0xFFD84A, [(14, -16), (8, 0), (16, 0), (10, 16)], 2.0)
@icon("speed_force_dash", 0xFFD84A)
def _(i): arrow(i, 0xFFD84A, (-22, 0), (22, 0)); bolt(i, 0xFFFFFF, [(-18, -12), (-8, -4), (-14, 4), (-4, 14)], 1.0)
@icon("lightning_throw", 0xFFD84A)
def _(i): bolt(i, 0xFFD84A, [(-20, 20), (-4, 6), (-10, 0), (20, -20)], 2.4)

# ---------- suits ----------
@icon("repulsor_blast", 0xA8F0FF)
def _(i): hand(i, 0xB02020, 0, 6); orb(i, 6, 0xFFFFFF, 0xA8F0FF, 0, 8)
@icon("unibeam", 0xC8F8FF)
def _(i):
    tri = regular(0, 0, 14, 3, -math.pi / 2)
    i.fill(lambda px, py: inside_polygon(px, py, tri), vertical(0xFFFFFF, 0x80E0FF)); ring(i, 20, 0xFFD040, 1.6)
@icon("thrusters", 0xFFB040)
def _(i):
    for x in (-8, 8):
        i.stroke(lambda px, py, x=x: d_segment(px, py, x, -18, x, 0), 4, solid(0xB02020)); flame(i, 0xFFFFFF, 0xFFB040, 0.45, x, 14)
@icon("grapple", 0x2A2A2A)
def _(i):
    i.stroke(lambda px, py: d_segment(px, py, -20, 20, 12, -12), 1.2, solid(0xC0C0C0), glow=2)
    i.stroke(lambda px, py: d_polyline(px, py, [(6, -18), (14, -14), (18, -6)]), 2.4, solid(0xFFFFFF), glow=3)
@icon("smoke_bomb", 0x505050)
def _(i): [orb(i, r, 0x909090, 0x404040, x, y) for x, y, r in ((-8, 4, 10), (8, 2, 11), (0, -8, 9))]
@icon("glide", 0x2A2A2A)
def _(i):
    pts = [(-24, -6), (-12, -2), (-8, 6), (0, 0), (8, 6), (12, -2), (24, -6), (12, 10), (0, 4), (-12, 10), (-24, -6)]
    i.fill(lambda px, py: inside_polygon(px, py, pts), vertical(0x606070, 0x101018)); i.stroke(lambda px, py: d_polyline(px, py, pts), 1.0, solid(0xFFD040))
@icon("lasso", 0xFFD84A)
def _(i): i.stroke(lambda px, py: abs(math.hypot(px - 6, (py + 6) * 1.6) - 12), 1.8, solid(0xFFD84A), glow=4); i.stroke(lambda px, py: d_polyline(px, py, [(-4, 0), (-12, 10), (-22, 20)]), 1.8, solid(0xFFD84A), glow=4)
@icon("bracer_deflect", 0xFFE07A)
def _(i):
    i.fill(lambda px, py: abs(px) < 8 and abs(py) < 16, vertical(0xE0E0E0, 0x909090)); [i.stroke(lambda px, py, y=y: d_segment(px, py, -8, y, 8, y), 1.4, solid(0xFFD040)) for y in (-10, 10)]
    rays(i, 0xFFFFFF, 6, 18, 24, 1.0, 0.3)
@icon("divine_leap", 0xFFE07A)
def _(i): arrow(i, 0xFFE07A, (-16, 22), (14, -20)); _ = d_star; i.stroke(lambda px, py: d_star(px, py, 14, -20, 6, 2, 5)[0], 0.8, solid(0xFFFFFF))

# ---------- mystic ----------
@icon("eldritch_whip", 0xFF9A2A)
def _(i): pts = [(-20 + t * 42, 18 - t * 36 + 6 * math.sin(t * 12)) for t in [k / 60 for k in range(61)]]; i.stroke(lambda px, py: d_polyline(px, py, pts), 1.8, horizontal(0xFFE0A0, 0xFF6A10), glow=5)
@icon("seraphim_shield", 0xFFB347)
def _(i): mandala(i, 0xFFB347); shield(i, 0xFFE0A0, 0, 0, 0.6)
@icon("sling_ring_portal", 0xFF9A2A)
def _(i):
    for k in range(48):
        a = 2 * math.pi * k / 48
        i.fill(lambda px, py, a=a: math.hypot(px - 18 * math.cos(a), py - 18 * math.sin(a)) < 1.6, solid(0xFFD080 if k % 2 else 0xFF6A10))
    ring(i, 15, 0xFF9A2A, 0.8)
@icon("astral_projection", 0xFFE6A0)
def _(i):
    for cx, a in ((-6, 0x404040), (6, 0xFFE6A0)):
        i.fill(lambda px, py, cx=cx: math.hypot(px - cx, py + 12) < 5 or (abs(px - cx) < 6 and -6 < py < 16), solid(a))
@icon("time_reversal", 0x5CFF8A)
def _(i): eye(i, 0x5CFF8A, 0x2AC060); i.stroke(lambda px, py: d_arc(px, py, 0, 0, 24, 0.4, 5.6), 1.2, solid(0x5CFF8A), glow=3)
@icon("mirror_dimension", 0xB070FF)
def _(i):
    for k in range(6):
        a = math.pi * k / 3
        i.stroke(lambda px, py, a=a: d_segment(px, py, 4 * math.cos(a), 4 * math.sin(a), 22 * math.cos(a + 0.2), 22 * math.sin(a + 0.2)), 1.0, solid(0xE0C0FF), glow=3)
    ring(i, 12, 0xB070FF, 1.0)
@icon("cloak_levitation", 0xC0182A)
def _(i):
    pts = [(-10, -18), (10, -18), (18, 18), (6, 12), (0, 20), (-6, 12), (-18, 18), (-10, -18)]
    i.fill(lambda px, py: inside_polygon(px, py, pts), vertical(0xE03040, 0x600810)); i.stroke(lambda px, py: d_polyline(px, py, pts), 1.0, solid(0xFFD040))
@icon("mystic_grip", 0xFF9A2A)
def _(i): mandala(i, 0xFF9A2A, 22); hand(i, 0xFFE0A0, 0, 6)


def main():
    for name, (accent, fn) in ICONS.items():
        i = Icon(accent)
        fn(i)
        i.save(name)
    print(len(ICONS), "power icons written")


if __name__ == "__main__":
    main()
