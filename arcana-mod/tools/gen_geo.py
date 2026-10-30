#!/usr/bin/env python3
"""Generates the GeckoLib 3D models, palette textures and animations for ARCANA.

Run from arcana-mod/:  python3 tools/gen_geo.py

Every cube face maps to a flat colour swatch of a small palette texture, so there is no UV packing and models stay
editable. Bones named ``glow*`` / ``tint*`` are rendered fullbright by the Java renderers; bones named ``tint*`` are also
multiplied by the wand's crystal colour, and the ``handle`` bone by the handle material colour.
"""
import json, math, os, random
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, '..', 'src', 'main', 'resources', 'assets', 'arcana')
SCHOOLS = [('void', 0x7B2CBF), ('verdant', 0x3FB950), ('glacier', 0x6EC6FF), ('crimson', 0xC1121F),
           ('solar', 0xFFB703), ('storm', 0xF2E94E), ('chronos', 0x2EC4B6), ('arcane', 0xE056FD)]
FORBIDDEN_LOOKS = [0xFF2BD6] + [c for _, c in SCHOOLS] + [0x9B30FF, 0x30FFB4, 0xFFFFFF]

def rgb(c): return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
def mul(c, f): return tuple(max(0, min(255, int(v * f))) for v in c)
def mix(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))

def jw(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f: json.dump(obj, f, indent=1)

class Palette:
    def __init__(self, size=64, sw=4):
        self.size, self.sw, self.colors = size, sw, []
    def uv(self, c):
        c = tuple(c)
        if c not in self.colors: self.colors.append(c)
        i = self.colors.index(c); per = self.size // self.sw
        assert i < per * per, 'palette full'
        return [(i % per) * self.sw + 1, (i // per) * self.sw + 1]
    def save(self, path):
        img = Image.new('RGBA', (self.size, self.size), (0, 0, 0, 0)); px = img.load(); per = self.size // self.sw
        for i, c in enumerate(self.colors):
            for dx in range(self.sw):
                for dy in range(self.sw): px[(i % per) * self.sw + dx, (i // per) * self.sw + dy] = c + (255,)
        os.makedirs(os.path.dirname(path), exist_ok=True); img.save(path)

class Model:
    def __init__(self, ident, pal, bounds=(4, 4)):
        self.ident, self.pal, self.bounds, self.bones, self.order = ident, pal, bounds, {}, []
    def bone(self, name, parent=None, pivot=(0, 0, 0), rot=None):
        b = {'name': name, 'pivot': list(pivot)}
        if parent: b['parent'] = parent
        if rot: b['rotation'] = list(rot)
        self.bones[name] = b; self.order.append(name); return name
    def cube(self, bone, origin, size, color, rot=None, pivot=None):
        u, v = self.pal.uv(color)
        face = {'uv': [u, v], 'uv_size': [1, 1]}
        c = {'origin': [round(x, 3) for x in origin], 'size': [round(x, 3) for x in size],
             'uv': {k: dict(face) for k in ('north', 'east', 'south', 'west', 'up', 'down')}}
        if rot:
            c['rotation'] = list(rot)
            c['pivot'] = list(pivot) if pivot else [origin[i] + size[i] / 2 for i in range(3)]
        self.bones[bone].setdefault('cubes', []).append(c)
    def box(self, bone, center, size, color, rot=None):
        self.cube(bone, [center[i] - size[i] / 2 for i in range(3)], size, color, rot, list(center))
    def save(self, path):
        d = {'format_version': '1.12.0', 'minecraft:geometry': [{'description': {
            'identifier': self.ident, 'texture_width': self.pal.size, 'texture_height': self.pal.size,
            'visible_bounds_width': self.bounds[0], 'visible_bounds_height': self.bounds[1], 'visible_bounds_offset': [0, 0.5, 0]},
            'bones': [self.bones[n] for n in self.order]}]}
        jw(path, d)

# ------------------------------------------------------------------ helpers
def vary(c, rnd, amt=0.12):
    f = 1 + (rnd.random() - 0.5) * 2 * amt
    return tuple(int(round(min(255, max(0, v * f)) / 8) * 8) for v in c)

def chain(m, bone, start, direction, n, size0, size1, color_fn, rnd=None):
    """A tapered chain of cubes along a direction (cheap way to build spikes, rays and branches)."""
    d = [x for x in direction]; l = math.sqrt(sum(x * x for x in d)); d = [x / l for x in d]
    for i in range(n):
        t = i / max(1, n - 1); s = size0 + (size1 - size0) * t
        c = [start[k] + d[k] * i * (size0 + size1) / 2 * 0.9 for k in range(3)]
        m.box(bone, c, (s, s, s), color_fn(t))

def voxel_sphere(m, bone, center, radius, cell, color_fn, rnd):
    n = int(radius / cell) + 1
    for ix in range(-n, n + 1):
        for iy in range(-n, n + 1):
            for iz in range(-n, n + 1):
                p = (ix * cell, iy * cell, iz * cell)
                d = math.sqrt(sum(x * x for x in p))
                if d <= radius:
                    m.box(bone, (center[0] + p[0], center[1] + p[1], center[2] + p[2]), (cell, cell, cell), color_fn(d / radius, rnd))

def ring_pts(cx, cy, cz, r, n, tilt=0.0, start=0.0):
    pts = []
    for i in range(n):
        a = start + math.pi * 2 * i / n
        x, z = math.cos(a) * r, math.sin(a) * r
        y = z * math.sin(tilt); z2 = z * math.cos(tilt)
        pts.append((cx + x, cy + y, cz + z2, a))
    return pts

# ------------------------------------------------------------------ animation builders
def kf(times_vals, easing=None):
    out = {}
    for t, v in times_vals:
        out[f'{t:.2f}'] = ({'vector': list(v), 'easing': easing} if easing else list(v))
    return out

def anim(length, bones, loop=True, hold=False):
    a = {'animation_length': length, 'bones': bones}
    if loop: a['loop'] = True
    elif hold: a['loop'] = 'hold_on_last_frame'
    return a

# ================================================================== WANDS
METAL = (150, 150, 168); GOLD = (232, 190, 80); WOOD = (210, 210, 210)

def ornament(m, school, acc, rnd):
    """School-specific 3D head of the wand, built around the crystal at (0, 19.5, 0)."""
    a, dark, light = acc, mul(acc, 0.55), mix(acc, (255, 255, 255), 0.45)
    cy = 19.5
    if school == 'void':
        for (x, y, z, ang) in ring_pts(0, cy, 0, 3.9, 12, tilt=math.radians(62)):
            m.box('ornament', (x, y, z), (1.1, 1.1, 1.1), vary(dark, rnd))
        for (x, y, z, ang) in ring_pts(0, cy, 0, 3.9, 6, tilt=math.radians(62), start=0.26):
            m.box('glow_ornament', (x, y, z), (0.7, 0.7, 0.7), light)
        m.box('ornament', (0, cy - 3.2, 0), (1.0, 2.4, 1.0), dark, rot=[0, 45, 0])
    elif school == 'verdant':
        for i in range(4):
            ang = math.pi / 2 * i
            cx_, cz_ = math.cos(ang) * 2.6, math.sin(ang) * 2.6
            m.box('ornament', (cx_, cy + 0.6, cz_), (3.4, 0.5, 1.8), vary(a, rnd, 0.1), rot=[0, -math.degrees(ang), 28])
            m.box('ornament', (cx_ * 1.9, cy + 1.9, cz_ * 1.9), (2.0, 0.4, 1.0), vary(light, rnd, 0.08), rot=[0, -math.degrees(ang), 55])
        for i in range(5):
            ang = i * 1.3
            m.box('ornament', (math.cos(ang) * 1.1, 6 + i * 1.7, math.sin(ang) * 1.1), (0.9, 0.9, 0.9), vary(dark, rnd))
    elif school == 'glacier':
        for i in range(6):
            ang = math.pi * 2 * i / 6
            chain(m, 'ornament', (math.cos(ang) * 1.5, cy, math.sin(ang) * 1.5), (math.cos(ang) * 1.0, 1.5, math.sin(ang) * 1.0), 4, 1.5, 0.4, lambda t: mix(a, (235, 250, 255), t))
        chain(m, 'glow_ornament', (0, cy + 2, 0), (0, 1, 0), 3, 1.0, 0.3, lambda t: (230, 250, 255))
    elif school == 'crimson':
        for sgn in (-1, 1):
            for i in range(6):
                t = i / 5; ang = -0.4 + t * 2.2
                m.box('ornament', (sgn * (1.8 + math.sin(ang) * 2.6), cy - 2.4 + math.cos(ang) * 2.8 + t, 0), (1.0, 1.0, 1.0), vary(mix(dark, a, t), rnd))
            m.box('glow_ornament', (sgn * 4.4, cy + 1.8, 0), (0.8, 1.6, 0.8), light, rot=[0, 0, -sgn * 20])
        for i in range(3): m.box('ornament', (0, cy - 4 - i * 1.4, 0), (1.2, 0.9, 1.2), vary(dark, rnd), rot=[0, 45 * i, 0])
    elif school == 'solar':
        for i in range(8):
            ang = math.pi / 4 * i
            chain(m, 'glow_ornament', (math.cos(ang) * 2.3, cy + math.sin(ang) * 2.3, 0), (math.cos(ang), math.sin(ang), 0), 3, 1.1, 0.4, lambda t: mix(a, (255, 250, 200), t))
        m.box('ornament', (0, cy, -0.9), (4.6, 4.6, 0.6), vary(dark, rnd), rot=[0, 0, 45])
    elif school == 'storm':
        pts = [(0.6, 24.6), (-0.9, 22.6), (0.7, 20.8), (-0.8, 19), (0.6, 17.4), (-0.4, 15.9)]
        for i, (x, y) in enumerate(pts): m.box('glow_ornament', (x * 1.6 + 2.8, y + 0.3, 0), (1.1, 2.0, 1.1), mix(a, (255, 255, 255), i / 6), rot=[0, 0, 22 if i % 2 else -22])
        for i, (x, y) in enumerate(pts): m.box('ornament', (-x * 1.6 - 2.8, y + 0.3, 0), (1.0, 1.8, 1.0), vary(dark, rnd), rot=[0, 0, -22 if i % 2 else 22])
    elif school == 'chronos':
        for sgn in (-1, 1):
            m.box('ornament', (sgn * 3.2, cy, 0), (0.7, 7.0, 0.7), vary(a, rnd, 0.08))
        for i in range(4):
            w = 5.2 - i * 1.2
            m.box('ornament', (0, cy + 2.2 + i * 0.7, 0), (w, 0.6, 0.6), vary(dark, rnd))
            m.box('ornament', (0, cy - 2.2 - i * 0.7, 0), (w, 0.6, 0.6), vary(dark, rnd))
        for i in range(3): m.box('glow_ornament', (0, cy + 1.4 - i * 0.7, 0), (0.5, 0.5, 0.5), light)
    elif school == 'arcane':
        for i in range(4):
            ang = math.pi / 2 * i
            chain(m, 'ornament', (math.cos(ang) * 1.6, cy + math.sin(ang) * 1.6, 0), (math.cos(ang), math.sin(ang), 0), 4, 1.4, 0.4, lambda t: mix(a, light, t))
        for i in range(4):
            ang = math.pi / 4 + math.pi / 2 * i
            m.box('glow_ornament', (math.cos(ang) * 2.9, cy + math.sin(ang) * 2.9, 0), (0.8, 0.8, 0.8), light)
    else:  # forbidden: fractured, floating shards
        for i in range(9):
            r = 3.2 + rnd.random() * 2.2; ang = rnd.random() * math.tau; h = rnd.random() * 6 - 2
            m.box('ornament', (math.cos(ang) * r, cy + h, math.sin(ang) * r), (0.8 + rnd.random(), 1.2 + rnd.random() * 1.6, 0.8 + rnd.random()),
                  vary(mix(dark, a, rnd.random()), rnd), rot=[rnd.random() * 90, rnd.random() * 90, rnd.random() * 90])
        for i in range(4):
            ang = math.pi / 2 * i + 0.4
            m.box('glow_ornament', (math.cos(ang) * 2.2, cy + (i % 2) * 2 - 1, math.sin(ang) * 2.2), (0.6, 0.6, 0.6), light, rot=[45, 45, 0])

def build_wand(school, accent_hex, pal):
    rnd = random.Random(hash(school) & 0xFFFF)
    acc = rgb(accent_hex)
    m = Model(f'geometry.arcana.{school}_wand', pal, bounds=(2, 3))
    m.bone('root'); m.bone('wand', 'root', (0, 10, 0))
    m.bone('handle', 'wand', (0, 3, 0))
    m.cube('handle', (-1.1, 0, -1.1), (2.2, 5, 2.2), WOOD)
    m.cube('handle', (-1.5, -1.0, -1.5), (3.0, 1.0, 3.0), WOOD)
    m.bone('fittings', 'wand', (0, 3, 0))
    for y in (1.2, 3.6): m.cube('fittings', (-1.3, y, -1.3), (2.6, 0.7, 2.6), METAL)
    m.bone('shaft', 'wand', (0, 10, 0))
    m.cube('shaft', (-0.8, 5, -0.8), (1.6, 10, 1.6), mix(mul(acc, 0.35), (60, 40, 60), 0.5))
    m.bone('glow_inlay', 'wand', (0, 10, 0))
    for y in (6.4, 9.4, 12.4): m.cube('glow_inlay', (-0.88, y, -0.88), (1.76, 0.55, 1.76), mix(acc, (255, 255, 255), 0.2))
    m.bone('collar', 'wand', (0, 15, 0))
    m.cube('collar', (-1.5, 15, -1.5), (3.0, 1.0, 3.0), GOLD)
    m.bone('ornament', 'wand', (0, 19.5, 0)); m.bone('glow_ornament', 'wand', (0, 19.5, 0))
    ornament(m, school, acc, rnd)
    m.bone('tint_crystal', 'wand', (0, 19.5, 0))
    m.box('tint_crystal', (0, 19.5, 0), (2.6, 3.8, 2.6), (235, 235, 235), rot=[0, 45, 0])
    m.box('tint_crystal', (0, 19.5, 0), (2.6, 3.8, 2.6), (255, 255, 255), rot=[0, 0, 0])
    m.box('tint_crystal', (0, 19.5, 0), (1.6, 5.4, 1.6), (255, 255, 255), rot=[0, 45, 0])
    # stage 1: gilded filigree
    m.bone('stage1', 'wand', (0, 15, 0))
    m.cube('stage1', (-2.5, 15.2, -0.35), (5.0, 0.5, 0.7), GOLD); m.cube('stage1', (-0.35, 15.2, -2.5), (0.7, 0.5, 5.0), GOLD)
    for sgn in (-1, 1): m.box('stage1', (sgn * 2.6, 16.2, 0), (0.9, 1.6, 0.9), GOLD, rot=[0, 0, -sgn * 15])
    m.bone('glow_stage1', 'stage1', (0, 16, 0))
    for sgn in (-1, 1): m.box('glow_stage1', (sgn * 2.8, 17.3, 0), (0.7, 0.7, 0.7), mix(acc, (255, 255, 255), 0.5))
    # stage 2: wings and orbiting gems
    m.bone('stage2', 'wand', (0, 15, 0))
    for sgn in (-1, 1):
        for i in range(4):
            m.box('stage2', (sgn * (3.2 + i * 1.5), 14.2 - i * 0.2 + i * 0.5, 0), (2.4, 0.5, 1.2), mix(GOLD, (255, 240, 200), i / 4), rot=[0, 0, sgn * (25 + i * 6)])
    m.bone('orbit', 'stage2', (0, 19.5, 0))
    m.bone('tint_orbit', 'orbit', (0, 19.5, 0))
    for i in range(3):
        a_ = math.pi * 2 * i / 3
        m.box('tint_orbit', (math.cos(a_) * 4.4, 19.5 + math.sin(a_ * 2) * 0.8, math.sin(a_) * 4.4), (1.2, 1.8, 1.2), (255, 255, 255), rot=[0, 45, 0])
    m.bone('glow_halo', 'stage2', (0, 19.5, 0))
    for (x, y, z, ang) in ring_pts(0, 19.5, 0, 5.8, 16, tilt=math.radians(14)):
        m.box('glow_halo', (x, y, z), (0.5, 0.5, 0.5), mix(acc, (255, 255, 255), 0.65))
    return m

def wand_animations():
    idle = anim(4.0, {
        'wand': {'position': kf([(0, (0, 0, 0)), (2.0, (0, 0.7, 0)), (4.0, (0, 0, 0))], 'easeInOutSine'),
                 'rotation': kf([(0, (0, 0, 1.5)), (2.0, (0, 0, -1.5)), (4.0, (0, 0, 1.5))], 'easeInOutSine')},
        'tint_crystal': {'rotation': kf([(0, (0, 0, 0)), (4.0, (0, 360, 0))]),
                         'scale': kf([(0, (1, 1, 1)), (1.0, (1.1, 1.1, 1.1)), (2.0, (1, 1, 1)), (3.0, (1.1, 1.1, 1.1)), (4.0, (1, 1, 1))], 'easeInOutSine')},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (4.0, (0, -360, 0))])},
        'glow_halo': {'rotation': kf([(0, (0, 0, 0)), (4.0, (0, 360, 0))])},
        'ornament': {'rotation': kf([(0, (0, -3, 0)), (2.0, (0, 3, 0)), (4.0, (0, -3, 0))], 'easeInOutSine')},
    })
    charge = anim(0.5, {
        'wand': {'position': kf([(0, (0, 0, 0)), (0.125, (0.25, 0.1, 0)), (0.25, (-0.25, 0, 0.1)), (0.375, (0.2, 0.1, -0.1)), (0.5, (0, 0, 0))]),
                 'rotation': kf([(0, (0, 0, 0)), (0.25, (3, 0, -3)), (0.5, (0, 0, 0))])},
        'tint_crystal': {'rotation': kf([(0, (0, 0, 0)), (0.5, (0, 360, 0))]), 'scale': kf([(0, (1.3, 1.3, 1.3)), (0.25, (1.7, 1.7, 1.7)), (0.5, (1.3, 1.3, 1.3))])},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (0.5, (0, -720, 0))])},
        'ornament': {'scale': kf([(0, (1, 1, 1)), (0.25, (1.12, 1.12, 1.12)), (0.5, (1, 1, 1))])},
    })
    cast = anim(0.7, {
        'wand': {'position': kf([(0, (0, 0, 0)), (0.12, (0, 0.5, -2.5)), (0.7, (0, 0, 0))], 'easeOutQuad'),
                 'rotation': kf([(0, (0, 0, 0)), (0.12, (-22, 0, 0)), (0.7, (0, 0, 0))], 'easeOutQuad')},
        'tint_crystal': {'scale': kf([(0, (1, 1, 1)), (0.12, (2.6, 2.6, 2.6)), (0.7, (1, 1, 1))], 'easeOutQuad'),
                         'rotation': kf([(0, (0, 0, 0)), (0.7, (0, 720, 0))])},
        'ornament': {'scale': kf([(0, (1, 1, 1)), (0.12, (1.4, 1.4, 1.4)), (0.7, (1, 1, 1))], 'easeOutQuad')},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (0.7, (0, -1080, 0))])},
    })
    return {'format_version': '1.8.0', 'animations': {'animation.arcana.wand.idle': idle, 'animation.arcana.wand.charge': charge, 'animation.arcana.wand.cast': cast}}

# ================================================================== BOSS
def build_boss(pal):
    rnd = random.Random(7)
    robe, robe2, trim, skin = (46, 20, 78), (34, 14, 60), (177, 13, 255), (140, 120, 165)
    glow = (255, 43, 214)
    m = Model('geometry.arcana.hollow_archmage', pal, bounds=(4, 5))
    m.bone('root'); m.bone('body', 'root', (0, 10, 0))
    m.bone('skirt', 'body', (0, 10, 0))
    for i, (w, y, h) in enumerate([(13, 0, 4), (12, 4, 4), (11, 8, 3)]):
        m.cube('skirt', (-w / 2, y, -w / 2 * 0.75), (w, h, w * 0.75), vary(robe2 if i % 2 else robe, rnd, 0.06))
    m.cube('skirt', (-6.6, 0, -4.5), (13.2, 0.8, 9.0), trim)
    m.bone('torso', 'body', (0, 11, 0))
    m.cube('torso', (-5, 11, -3), (10, 8, 6), robe); m.cube('torso', (-4.6, 17, -2.6), (9.2, 4, 5.2), vary(robe2, rnd))
    m.cube('torso', (-5.3, 11, -3.3), (10.6, 1.0, 6.6), trim)
    m.cube('torso', (-1, 11, -3.1), (2, 9, 0.4), trim)
    m.bone('glow_chest', 'torso', (0, 16, 0)); m.cube('glow_chest', (-1.2, 15.2, -3.4), (2.4, 2.4, 0.6), glow)
    m.bone('head', 'torso', (0, 21, 0))
    m.cube('head', (-3.5, 21, -3.5), (7, 7, 7), skin)
    m.cube('head', (-4.2, 20.6, -4.2), (8.4, 8.4, 8.4), robe)             # hood
    m.cube('head', (-3.4, 21.4, -4.6), (6.8, 5.8, 0.6), (18, 8, 30))      # shadowed face opening
    m.cube('head', (-1.2, 28.6, -1.2), (2.4, 3.6, 2.4), robe, rot=[0, 45, 0])  # hood peak
    m.bone('glow_eyes', 'head', (0, 24, 0))
    for sx in (-1.9, 0.7): m.cube('glow_eyes', (sx, 23.6, -5.0), (1.2, 0.7, 0.5), glow)
    m.bone('glow_crown', 'head', (0, 31, 0))
    for i in range(5):
        ang = math.tau * i / 5
        m.box('glow_crown', (math.cos(ang) * 4.5, 31.4 + math.sin(i * 1.7) * 0.7, math.sin(ang) * 4.5), (1.0, 2.2, 1.0), trim, rot=[0, 45, 0])
    for side, sx in (('r', -1), ('l', 1)):
        m.bone(f'arm_{side}', 'torso', (sx * 6.2, 19, 0))
        m.cube(f'arm_{side}', (sx * 6.2 - 1.6 + (0 if sx > 0 else 0), 11.5, -1.6), (3.2, 8.5, 3.2), robe)
        m.cube(f'arm_{side}', (sx * 6.2 - 1.9, 11.0, -1.9), (3.8, 2.0, 3.8), trim)
        m.cube(f'arm_{side}', (sx * 6.2 - 1.2, 8.8, -1.2), (2.4, 2.4, 2.4), skin)
    m.bone('staff', 'arm_r', (-6.2, 9.5, 0))
    m.cube('staff', (-7.5, 0, -0.6), (1.2, 32, 1.2), (90, 60, 40))
    for i in range(3): m.cube('staff', (-7.9, 16 + i * 5, -1.0), (2.0, 0.8, 2.0), trim)
    m.bone('glow_staff', 'staff', (-6.9, 33, 0))
    m.box('glow_staff', (-6.9, 34.5, 0), (3.4, 3.4, 3.4), glow, rot=[45, 45, 0])
    m.box('glow_staff', (-6.9, 34.5, 0), (2.0, 5.6, 2.0), (255, 150, 240), rot=[0, 45, 0])
    m.bone('cape', 'torso', (0, 20, 3))
    m.cube('cape', (-5.2, 4, 3.0), (10.4, 15, 0.8), vary(robe2, rnd, 0.05)); m.cube('cape', (-5.2, 2, 3.0), (10.4, 2.5, 0.8), trim)
    m.bone('orbit', 'body', (0, 22, 0)); m.bone('glow_orbs', 'orbit', (0, 22, 0))
    for i in range(3):
        a_ = math.tau * i / 3
        m.box('glow_orbs', (math.cos(a_) * 9.5, 24 + math.sin(a_ * 2) * 1.5, math.sin(a_) * 9.5), (2.2, 2.2, 2.2), glow, rot=[45, 45, 0])
    return m

def boss_animations():
    idle = anim(4.0, {
        'body': {'position': kf([(0, (0, 0, 0)), (2.0, (0, 1.6, 0)), (4.0, (0, 0, 0))], 'easeInOutSine')},
        'skirt': {'rotation': kf([(0, (2, 0, 0)), (2.0, (-2, 0, 0)), (4.0, (2, 0, 0))], 'easeInOutSine')},
        'torso': {'rotation': kf([(0, (2, 0, 0)), (2.0, (0, 0, 0)), (4.0, (2, 0, 0))], 'easeInOutSine')},
        'head': {'rotation': kf([(0, (0, -6, 0)), (2.0, (3, 6, 0)), (4.0, (0, -6, 0))], 'easeInOutSine')},
        'arm_r': {'rotation': kf([(0, (-10, 0, -4)), (2.0, (-14, 0, -8)), (4.0, (-10, 0, -4))], 'easeInOutSine')},
        'arm_l': {'rotation': kf([(0, (10, 0, 6)), (2.0, (6, 0, 12)), (4.0, (10, 0, 6))], 'easeInOutSine')},
        'cape': {'rotation': kf([(0, (4, 0, 0)), (2.0, (12, 0, 0)), (4.0, (4, 0, 0))], 'easeInOutSine')},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (4.0, (0, 360, 0))])},
        'glow_crown': {'rotation': kf([(0, (0, 0, 0)), (4.0, (0, -360, 0))])},
        'glow_staff': {'scale': kf([(0, (1, 1, 1)), (1.0, (1.18, 1.18, 1.18)), (2.0, (1, 1, 1)), (3.0, (1.18, 1.18, 1.18)), (4.0, (1, 1, 1))], 'easeInOutSine')},
    })
    walk = anim(1.2, {
        'body': {'position': kf([(0, (0, 0, 0)), (0.6, (0, 1.2, 0)), (1.2, (0, 0, 0))], 'easeInOutSine'),
                 'rotation': kf([(0, (14, 0, 0)), (1.2, (14, 0, 0))])},
        'skirt': {'rotation': kf([(0, (-12, 0, 0)), (0.6, (6, 0, 0)), (1.2, (-12, 0, 0))], 'easeInOutSine')},
        'arm_r': {'rotation': kf([(0, (-25, 0, -4)), (0.6, (10, 0, -4)), (1.2, (-25, 0, -4))], 'easeInOutSine')},
        'arm_l': {'rotation': kf([(0, (12, 0, 6)), (0.6, (-24, 0, 6)), (1.2, (12, 0, 6))], 'easeInOutSine')},
        'cape': {'rotation': kf([(0, (22, 0, 0)), (0.6, (34, 0, 0)), (1.2, (22, 0, 0))], 'easeInOutSine')},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (1.2, (0, 360, 0))])},
    })
    cast = anim(1.0, {
        'arm_r': {'rotation': kf([(0, (-10, 0, -4)), (0.25, (-150, 0, -10)), (0.45, (-170, 0, -10)), (1.0, (-10, 0, -4))], 'easeOutQuad')},
        'arm_l': {'rotation': kf([(0, (10, 0, 6)), (0.25, (-140, 0, 14)), (0.45, (-160, 0, 14)), (1.0, (10, 0, 6))], 'easeOutQuad')},
        'head': {'rotation': kf([(0, (0, 0, 0)), (0.3, (-22, 0, 0)), (1.0, (0, 0, 0))])},
        'torso': {'rotation': kf([(0, (0, 0, 0)), (0.3, (-12, 0, 0)), (1.0, (0, 0, 0))])},
        'glow_staff': {'scale': kf([(0, (1, 1, 1)), (0.3, (2.4, 2.4, 2.4)), (1.0, (1, 1, 1))], 'easeOutQuad')},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (1.0, (0, 1080, 0))])},
    }, loop=False)
    summon = anim(1.6, {
        'arm_r': {'rotation': kf([(0, (-10, 0, -4)), (0.4, (-100, 0, -80)), (1.2, (-100, 0, -80)), (1.6, (-10, 0, -4))], 'easeInOutSine')},
        'arm_l': {'rotation': kf([(0, (10, 0, 6)), (0.4, (-100, 0, 80)), (1.2, (-100, 0, 80)), (1.6, (10, 0, 6))], 'easeInOutSine')},
        'body': {'position': kf([(0, (0, 0, 0)), (0.5, (0, 4, 0)), (1.2, (0, 4, 0)), (1.6, (0, 0, 0))], 'easeInOutSine')},
        'head': {'rotation': kf([(0, (0, 0, 0)), (0.5, (-28, 0, 0)), (1.2, (-28, 0, 0)), (1.6, (0, 0, 0))])},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (1.6, (0, 1440, 0))])},
        'glow_orbs': {'scale': kf([(0, (1, 1, 1)), (0.5, (1.8, 1.8, 1.8)), (1.2, (1.8, 1.8, 1.8)), (1.6, (1, 1, 1))])},
    }, loop=False)
    phase = anim(2.0, {
        'head': {'rotation': kf([(0, (0, 0, 0)), (0.4, (-40, 0, 0)), (1.6, (-40, 0, 0)), (2.0, (0, 0, 0))], 'easeInOutSine')},
        'torso': {'rotation': kf([(0, (0, 0, 0)), (0.4, (-20, 0, 0)), (1.6, (-20, 0, 0)), (2.0, (0, 0, 0))], 'easeInOutSine')},
        'arm_r': {'rotation': kf([(0, (-10, 0, -4)), (0.4, (-20, 0, -110)), (1.6, (-20, 0, -110)), (2.0, (-10, 0, -4))], 'easeInOutSine')},
        'arm_l': {'rotation': kf([(0, (10, 0, 6)), (0.4, (-20, 0, 110)), (1.6, (-20, 0, 110)), (2.0, (10, 0, 6))], 'easeInOutSine')},
        'body': {'position': kf([(0, (0, 0, 0)), (0.5, (0, 5, 0)), (1.5, (0, 5, 0)), (2.0, (0, 0, 0))], 'easeInOutSine')},
        'glow_crown': {'scale': kf([(0, (1, 1, 1)), (0.5, (1.7, 1.7, 1.7)), (1.5, (1.7, 1.7, 1.7)), (2.0, (1, 1, 1))])},
    }, loop=False)
    death = anim(5.0, {
        'body': {'position': kf([(0, (0, 0, 0)), (1.5, (0, 3, 0)), (3.6, (0, 9, 0)), (4.4, (0, 9, 0)), (5.0, (0, 2, 0))], 'easeInOutSine'),
                 'rotation': kf([(0, (0, 0, 0)), (0.2, (0, 0, 3)), (0.4, (0, 0, -3)), (0.6, (0, 0, 3)), (0.8, (0, 0, -3)), (1.0, (0, 0, 2)), (1.5, (0, 0, 0))])},
        'arm_r': {'rotation': kf([(0, (-10, 0, -4)), (1.5, (-20, 0, -60)), (3.6, (-30, 0, -120)), (5.0, (-30, 0, -120))], 'easeInOutSine')},
        'arm_l': {'rotation': kf([(0, (10, 0, 6)), (1.5, (-20, 0, 60)), (3.6, (-30, 0, 120)), (5.0, (-30, 0, 120))], 'easeInOutSine')},
        'head': {'rotation': kf([(0, (0, 0, 0)), (3.6, (-35, 0, 0)), (5.0, (-35, 0, 0))])},
        'orbit': {'rotation': kf([(0, (0, 0, 0)), (5.0, (0, 2880, 0))]), 'scale': kf([(0, (1, 1, 1)), (3.6, (2.4, 2.4, 2.4)), (4.4, (2.4, 2.4, 2.4)), (5.0, (0, 0, 0))])},
        'glow_crown': {'scale': kf([(0, (1, 1, 1)), (3.6, (2.0, 2.0, 2.0)), (5.0, (0, 0, 0))])},
        'torso': {'scale': kf([(0, (1, 1, 1)), (4.4, (1, 1, 1)), (5.0, (0, 0, 0))])},
        'skirt': {'scale': kf([(0, (1, 1, 1)), (4.4, (1, 1, 1)), (5.0, (0, 0, 0))])},
        'cape': {'scale': kf([(0, (1, 1, 1)), (4.4, (1, 1, 1)), (5.0, (0, 0, 0))])},
    }, loop=False, hold=True)
    return {'format_version': '1.8.0', 'animations': {f'animation.arcana.archmage.{k}': v for k, v in
            dict(idle=idle, walk=walk, cast=cast, summon=summon, phase=phase, death=death).items()}}

# ================================================================== CINEMATIC FX MODELS
def spin(bone, length=6.0, dy=360):
    return {bone: {'rotation': kf([(0, (0, 0, 0)), (length, (0, dy, 0))])}}

def fx_black_hole(pal):
    rnd = random.Random(1)
    m = Model('geometry.arcana.fx_black_hole', pal, bounds=(6, 6))
    m.bone('root'); m.bone('core', 'root', (0, 0, 0)); m.bone('glow_rim', 'root', (0, 0, 0)); m.bone('disc', 'root', (0, 0, 0)); m.bone('glow_disc', 'disc', (0, 0, 0)); m.bone('glow_jets', 'root', (0, 0, 0))
    voxel_sphere(m, 'core', (0, 0, 0), 5.0, 1.8, lambda t, r: vary((10, 6, 18), r, 0.25), rnd)
    voxel_sphere(m, 'glow_rim', (0, 0, 0), 6.2, 1.2, lambda t, r: (150, 70, 255) if t > 0.9 else None, rnd) if False else None
    for (x, y, z, ang) in ring_pts(0, 0, 0, 6.3, 24, start=0.0): m.box('glow_rim', (x, y * 0.3, z), (1.0, 1.0, 1.0), (170, 110, 255))
    for ring, (r0, r1, col0, col1) in enumerate([(7.5, 9.5, (255, 255, 255), (255, 170, 80)), (9.5, 12, (255, 150, 70), (170, 50, 200)), (12, 14.5, (150, 50, 190), (70, 20, 120))]):
        n = int(r1 * 5)
        for (x, y, z, ang) in ring_pts(0, 0, 0, (r0 + r1) / 2, n, tilt=0.0, start=rnd.random()):
            t = rnd.random(); m.box('glow_disc', (x, rnd.random() * 0.8 - 0.4, z), (1.5, 0.5 + rnd.random() * 0.4, 1.5), mix(col0, col1, t))
    for sgn in (-1, 1): chain(m, 'glow_jets', (0, sgn * 6.5, 0), (0, sgn, 0), 8, 1.8, 0.4, lambda t: mix((200, 150, 255), (255, 255, 255), t))
    m.bones['disc']['rotation'] = [18, 0, 8]
    anims = {'animation.arcana.fx.idle': anim(6.0, {**spin('disc', 6.0, 360), 'glow_jets': {'scale': kf([(0, (1, 1, 1)), (1.5, (1, 1.2, 1)), (3, (1, 1, 1)), (4.5, (1, 1.2, 1)), (6, (1, 1, 1))], 'easeInOutSine')}})}
    return m, anims

def fx_sun(pal):
    rnd = random.Random(2)
    m = Model('geometry.arcana.fx_sun', pal, bounds=(5, 5))
    m.bone('root'); m.bone('glow_core', 'root'); m.bone('glow_corona', 'root')
    voxel_sphere(m, 'glow_core', (0, 0, 0), 5.0, 1.7, lambda t, r: mix((255, 255, 235), (255, 160, 30), t ** 1.5), rnd)
    dirs = [(x, y, z) for x in (-1, 0, 1) for y in (-1, 0, 1) for z in (-1, 0, 1) if (x, y, z) != (0, 0, 0)]
    for d in dirs:
        l = math.sqrt(sum(c * c for c in d)); chain(m, 'glow_corona', [c / l * 6.0 for c in d], d, 4, 1.8, 0.5, lambda t: mix((255, 200, 60), (255, 120, 20), t))
    return m, {'animation.arcana.fx.idle': anim(4.0, {'glow_corona': {'rotation': kf([(0, (0, 0, 0)), (4, (0, 360, 0))])},
                                                       'glow_core': {'scale': kf([(0, (1, 1, 1)), (1, (1.06, 1.06, 1.06)), (2, (1, 1, 1)), (3, (1.06, 1.06, 1.06)), (4, (1, 1, 1))], 'easeInOutSine')}})}

def fx_ice_spire(pal):
    rnd = random.Random(3)
    m = Model('geometry.arcana.fx_ice_spire', pal, bounds=(6, 8))
    m.bone('root'); m.bone('spire', 'root'); m.bone('glow_tip', 'root'); m.bone('shards', 'root')
    h = 0
    for i in range(10):
        w = 8.0 - i * 0.7
        m.box('spire', (0, h + w * 0.5, 0), (w, w * 1.2, w), vary(mix((120, 200, 255), (225, 248, 255), i / 10), rnd, 0.06), rot=[0, 45 * (i % 2) + i * 9, 0])
        h += w * 0.8
    m.box('glow_tip', (0, h + 1.5, 0), (2.2, 5.0, 2.2), (240, 255, 255), rot=[0, 45, 0])
    for i in range(7):
        ang = math.tau * i / 7
        d = (math.cos(ang) * 0.55, 1, math.sin(ang) * 0.55)
        chain(m, 'shards', (math.cos(ang) * 7, 0, math.sin(ang) * 7), d, 6, 3.4 - (i % 2) * 0.8, 0.8, lambda t: mix((90, 170, 240), (220, 245, 255), t))
    return m, {'animation.arcana.fx.idle': anim(8.0, {**spin('shards', 8.0, 360), 'glow_tip': {'scale': kf([(0, (1, 1, 1)), (2, (1.3, 1.3, 1.3)), (4, (1, 1, 1))], 'easeInOutSine')}})}

def fx_crimson_spears(pal):
    rnd = random.Random(4)
    m = Model('geometry.arcana.fx_crimson_spears', pal, bounds=(6, 3))
    m.bone('root'); m.bone('spears', 'root'); m.bone('glow_tips', 'root')
    for i in range(16):
        ang = math.tau * i / 16; r = 14.0
        lean = 0.35
        d = (math.cos(ang) * lean, 1, math.sin(ang) * lean)
        chain(m, 'spears', (math.cos(ang) * r, 0, math.sin(ang) * r), d, 5, 2.0, 0.6, lambda t: mix((110, 6, 20), (200, 20, 40), t), rnd)
        tip = (math.cos(ang) * (r + lean * 6.5), 7.0, math.sin(ang) * (r + lean * 6.5))
        m.box('glow_tips', tip, (1.0, 1.6, 1.0), (255, 90, 90), rot=[0, 45, 0])
    return m, {'animation.arcana.fx.idle': anim(4.0, spin('spears', 4.0, 30))}

def fx_storm_cloud(pal):
    rnd = random.Random(5)
    m = Model('geometry.arcana.fx_storm_cloud', pal, bounds=(6, 3))
    m.bone('root'); m.bone('cloud', 'root'); m.bone('glow_bolts', 'root'); m.bone('glow_flash', 'root')
    for i in range(70):
        ang = rnd.random() * math.tau; r = math.sqrt(rnd.random()) * 13
        s = 4 + rnd.random() * 4
        m.box('cloud', (math.cos(ang) * r, rnd.random() * 3 - (r / 13) * 2, math.sin(ang) * r), (s, 2.0 + rnd.random() * 2.5, s), vary(mix((38, 40, 52), (90, 94, 110), rnd.random()), rnd, 0.1))
    for k in range(4):
        ang = math.tau * k / 4 + 0.5; r = 5.5
        x0, z0 = math.cos(ang) * r, math.sin(ang) * r
        for i in range(8):
            m.box('glow_bolts', (x0 + (0.9 if i % 2 else -0.9), -1.2 - i * 1.7, z0), (0.8, 1.9, 0.8), (255, 250, 150) if i % 2 else (255, 255, 255), rot=[0, 0, 24 if i % 2 else -24])
    for i in range(8):
        ang = math.tau * i / 8
        m.box('glow_flash', (math.cos(ang) * 9.5, 1.0, math.sin(ang) * 9.5), (2.4, 1.0, 2.4), (200, 210, 255))
    return m, {'animation.arcana.fx.idle': anim(6.0, {**spin('cloud', 6.0, 360), 'glow_bolts': {'scale': kf([(0, (1, 1, 1)), (0.3, (1, 0.3, 1)), (0.6, (1, 1, 1)), (1.5, (1, 1, 1)), (1.7, (1, 0.2, 1)), (2.0, (1, 1, 1)), (6.0, (1, 1, 1))])},
                                                       'glow_flash': {'scale': kf([(0, (1, 1, 1)), (0.3, (1.8, 2, 1.8)), (0.6, (1, 1, 1)), (3.0, (1, 1, 1)), (3.2, (1.8, 2, 1.8)), (3.5, (1, 1, 1)), (6.0, (1, 1, 1))])}})}

def fx_clock(pal):
    rnd = random.Random(6)
    m = Model('geometry.arcana.fx_clock', pal, bounds=(6, 2))
    m.bone('root'); m.bone('glow_face', 'root'); m.bone('glow_ticks', 'root'); m.bone('hour', 'root', (0, 0.6, 0)); m.bone('glow_hour', 'hour', (0, 0.6, 0)); m.bone('minute', 'root', (0, 0.6, 0)); m.bone('glow_minute', 'minute', (0, 0.6, 0))
    for (x, y, z, ang) in ring_pts(0, 0, 0, 12.0, 72): m.box('glow_face', (x, 0, z), (1.2, 0.5, 1.2), (60, 220, 200))
    for (x, y, z, ang) in ring_pts(0, 0, 0, 10.0, 48): m.box('glow_face', (x, 0, z), (0.8, 0.4, 0.8), (30, 140, 150))
    for i, (x, y, z, ang) in enumerate(ring_pts(0, 0, 0, 8.2, 12)):
        big = i % 3 == 0
        m.box('glow_ticks', (x, 0.2, z), (2.2 if big else 1.2, 0.8, 2.2 if big else 1.2), (230, 255, 250) if big else (130, 240, 225), rot=[0, -math.degrees(ang), 0])
    m.box('glow_hour', (0, 0.6, -3.2), (1.4, 0.8, 6.4), (255, 255, 255)); m.box('glow_minute', (0, 0.9, -4.8), (0.9, 0.8, 9.6), (200, 255, 245))
    m.box('glow_face', (0, 0.6, 0), (2.4, 1.2, 2.4), (255, 255, 255))
    return m, {'animation.arcana.fx.idle': anim(6.0, {'minute': {'rotation': kf([(0, (0, 0, 0)), (6.0, (0, -1080, 0))])}, 'hour': {'rotation': kf([(0, (0, 0, 0)), (6.0, (0, -90, 0))])},
                                                       'glow_ticks': {'rotation': kf([(0, (0, 0, 0)), (6.0, (0, 30, 0))])}})}

def fx_sigil(pal):
    rnd = random.Random(8)
    m = Model('geometry.arcana.fx_sigil', pal, bounds=(6, 2))
    m.bone('root'); m.bone('glow_outer', 'root'); m.bone('glow_mid', 'root'); m.bone('glow_star', 'root'); m.bone('glow_runes', 'root')
    for (x, y, z, ang) in ring_pts(0, 0, 0, 12.0, 120): m.box('glow_outer', (x, 0, z), (0.8, 0.3, 0.8), (224, 86, 253))
    for (x, y, z, ang) in ring_pts(0, 0, 0, 9.5, 56): m.box('glow_mid', (x, 0.1, z), (1.0, 0.5, 1.0), (180, 120, 255))
    pts = ring_pts(0, 0, 0, 9.5, 8)
    for i in range(8):
        a, b = pts[i], pts[(i + 3) % 8]
        steps = 10
        for s in range(steps + 1):
            t = s / steps; m.box('glow_star', (a[0] + (b[0] - a[0]) * t, 0.2, a[2] + (b[2] - a[2]) * t), (0.8, 0.4, 0.8), (255, 190, 255))
    for (x, y, z, ang) in ring_pts(0, 0, 0, 11.0, 12): m.box('glow_runes', (x, 0.6, z), (1.6, 1.6, 1.6), (255, 255, 255), rot=[0, math.degrees(-ang) + 45, 0])
    return m, {'animation.arcana.fx.idle': anim(8.0, {'glow_outer': {'rotation': kf([(0, (0, 0, 0)), (8, (0, 360, 0))])}, 'glow_mid': {'rotation': kf([(0, (0, 0, 0)), (8, (0, -360, 0))])},
                                                       'glow_star': {'rotation': kf([(0, (0, 0, 0)), (8, (0, 180, 0))])}, 'glow_runes': {'rotation': kf([(0, (0, 0, 0)), (8, (0, -720, 0))]),
                                                       'scale': kf([(0, (1, 1, 1)), (1, (1.3, 1.3, 1.3)), (2, (1, 1, 1))])}})}

def fx_world_tree(pal):
    rnd = random.Random(9)
    m = Model('geometry.arcana.fx_world_tree', pal, bounds=(6, 8))
    m.bone('root'); m.bone('trunk', 'root'); m.bone('roots', 'root'); m.bone('canopy', 'root'); m.bone('glow_motes', 'root')
    y = 0
    for i in range(14):
        w = 7.0 - i * 0.35
        m.box('trunk', (math.sin(i * 0.7) * 1.2, y + w * 0.5, math.cos(i * 0.5) * 1.2), (w, w * 1.05, w), vary((86, 58, 40), rnd, 0.1), rot=[0, i * 14, 0])
        y += w * 0.9
    top = y
    for i in range(8):
        ang = math.tau * i / 8
        chain(m, 'roots', (math.cos(ang) * 3, 1, math.sin(ang) * 3), (math.cos(ang), -0.05, math.sin(ang)), 6, 3.0, 1.0, lambda t: vary((70, 48, 34), rnd, 0.1))
    for i in range(6):
        ang = math.tau * i / 6 + 0.3
        chain(m, 'trunk', (0, top * 0.62, 0), (math.cos(ang), 0.7, math.sin(ang)), 6, 3.0, 1.0, lambda t: vary((86, 58, 40), rnd, 0.1))
    voxel_sphere(m, 'canopy', (0, top + 4, 0), 13, 3.6, lambda t, r: vary(mix((38, 150, 60), (120, 210, 90), r.random()), r, 0.1), rnd)
    for i in range(24):
        a, r2 = rnd.random() * math.tau, rnd.random() * 12
        m.box('glow_motes', (math.cos(a) * r2, top + rnd.random() * 14, math.sin(a) * r2), (0.8, 0.8, 0.8), (210, 255, 150))
    return m, {'animation.arcana.fx.idle': anim(6.0, {'canopy': {'rotation': kf([(0, (0, -2, 0)), (3, (0, 2, 0)), (6, (0, -2, 0))], 'easeInOutSine')},
                                                       'glow_motes': {'rotation': kf([(0, (0, 0, 0)), (6, (0, 360, 0))]), 'position': kf([(0, (0, 0, 0)), (3, (0, 3, 0)), (6, (0, 0, 0))], 'easeInOutSine')}})}

def fx_rift(pal):
    rnd = random.Random(10)
    m = Model('geometry.arcana.fx_rift', pal, bounds=(6, 8))
    m.bone('root'); m.bone('void', 'root'); m.bone('glow_edge', 'root'); m.bone('shards', 'root'); m.bone('glow_shards', 'shards')
    x = 0
    for i in range(22):
        y = i * 2.2 - 2
        x += (rnd.random() - 0.5) * 2.2
        h = 6.5 * math.sin(math.pi * (i + 0.5) / 22) + 1.0
        m.box('void', (x, y + 1.1, 0), (h * 0.55, 2.4, 1.6), (12, 4, 20))
        m.box('glow_edge', (x - h * 0.3, y + 1.1, 0), (0.8, 2.4, 1.8), (255, 43, 214) if i % 2 else (255, 150, 240))
        m.box('glow_edge', (x + h * 0.3, y + 1.1, 0), (0.8, 2.4, 1.8), (255, 43, 214) if i % 2 else (255, 150, 240))
    for i in range(14):
        ang = rnd.random() * math.tau; r = 6 + rnd.random() * 6
        m.box('glow_shards', (math.cos(ang) * r, rnd.random() * 44, math.sin(ang) * r * 0.5), (1 + rnd.random(), 2 + rnd.random() * 2, 1 + rnd.random()), mix((255, 43, 214), (255, 255, 255), rnd.random()), rot=[rnd.random() * 90, rnd.random() * 90, 0])
    return m, {'animation.arcana.fx.idle': anim(4.0, {'shards': {'rotation': kf([(0, (0, 0, 0)), (4, (0, 360, 0))])}, 'glow_edge': {'scale': kf([(0, (1, 1, 1)), (0.5, (1.4, 1, 1)), (1.0, (1, 1, 1)), (1.7, (1.5, 1, 1)), (2.0, (1, 1, 1)), (4, (1, 1, 1))])}})}

FX = {'black_hole': fx_black_hole, 'sun': fx_sun, 'ice_spire': fx_ice_spire, 'crimson_spears': fx_crimson_spears, 'storm_cloud': fx_storm_cloud,
      'clock': fx_clock, 'sigil': fx_sigil, 'world_tree': fx_world_tree, 'rift': fx_rift}

# ================================================================== write everything
def main():
    # wands
    for school, col in SCHOOLS + [('forbidden', 0xFF2BD6)]:
        pal = Palette(); m = build_wand(school, col, pal)
        m.save(f'{A}/geo/item/{school}_wand.geo.json'); pal.save(f'{A}/textures/item/geo/{school}_wand.png')
    for i, c in enumerate(FORBIDDEN_LOOKS):   # forbidden wand adapts: same geometry, recoloured per selected ability
        pal = Palette(); build_wand('forbidden', c, pal); pal.save(f'{A}/textures/item/geo/forbidden_look_{i}.png')
    jw(f'{A}/animations/item/wand.animation.json', wand_animations())
    # boss
    pal = Palette(); build_boss(pal).save(f'{A}/geo/entity/hollow_archmage.geo.json'); pal.save(f'{A}/textures/entity/hollow_archmage.png')
    jw(f'{A}/animations/entity/hollow_archmage.animation.json', boss_animations())
    for stale in ('hollow_archmage_glow.png',):
        p = f'{A}/textures/entity/{stale}'
        if os.path.exists(p): os.remove(p)
    # cinematic fx
    for name, fn in FX.items():
        pal = Palette(128, 4); m, anims = fn(pal)
        m.save(f'{A}/geo/entity/fx_{name}.geo.json'); pal.save(f'{A}/textures/entity/fx_{name}.png')
        jw(f'{A}/animations/entity/fx_{name}.animation.json', {'format_version': '1.8.0', 'animations': anims})
        print('fx', name, sum(len(b.get('cubes', [])) for b in m.bones.values()), 'cubes,', len(pal.colors), 'colours')
    print('wand/boss/fx models written')

if __name__ == '__main__':
    main()
