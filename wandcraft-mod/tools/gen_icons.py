"""Generates one distinct 32x32 icon per spell from vector motifs (anti-aliased by supersampling).
Run from wandcraft-mod/. Output: assets/wandcraft/textures/gui/spell/<id>.png"""
import math, os, random
from PIL import Image, ImageDraw, ImageFilter

OUT = 'src/main/resources/assets/wandcraft/textures/gui/spell/'
os.makedirs(OUT, exist_ok=True)
S = 256            # working canvas
N = 32             # output size

def rgb(h): return ((h >> 16) & 255, (h >> 8) & 255, h & 255)
def mix(a, b, t): return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))
def light(c, t=0.5): return mix(c, (255, 255, 255), t)
def dark(c, t=0.55): return mix(c, (0, 0, 0), t)

CAT = {"arcane": 0x6C8BFF, "fire": 0xFF7A1A, "ice": 0x7FD8FF, "storm": 0xFFE45A, "earth": 0xB08A58, "force": 0xC9D6E8, "nature": 0x57C84D,
       "defense": 0x4FB3FF, "illusion": 0xD27BFF, "space": 0x8A3CFF, "light": 0xFFD84A, "dark": 0x7A2BD6, "time": 0x3DE0C8, "forbidden": 0xE02450}

class Ctx:
    def __init__(self, col):
        self.img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
        self.d = ImageDraw.Draw(self.img)
        self.c = rgb(col) if isinstance(col, int) else col
        self.hi, self.lo = light(self.c, 0.55), dark(self.c, 0.55)

    def P(self, x, y): return (x * S, y * S)
    def poly(self, pts, fill=None, outline=True, w=10):
        pts = [self.P(*p) for p in pts]
        if outline: self.d.polygon(pts, fill=self.lo)
        if outline:
            # inset by drawing a smaller copy toward the centroid
            cx = sum(p[0] for p in pts) / len(pts); cy = sum(p[1] for p in pts) / len(pts)
            inner = [(cx + (x - cx) * 0.86, cy + (y - cy) * 0.86) for x, y in pts]
            self.d.polygon(inner, fill=fill or self.c)
        else:
            self.d.polygon(pts, fill=fill or self.c)
    def line(self, pts, w=12, col=None, outline=True):
        pts = [self.P(*p) for p in pts]
        if outline: self.d.line(pts, fill=self.lo, width=int(w * 1.7), joint='curve')
        self.d.line(pts, fill=col or self.c, width=int(w), joint='curve')
    def circle(self, cx, cy, r, fill=None, ring=0, col=None):
        x, y = self.P(cx, cy); rr = r * S
        if ring:
            self.d.ellipse([x - rr, y - rr, x + rr, y + rr], outline=self.lo, width=int(ring * 1.7))
            self.d.ellipse([x - rr, y - rr, x + rr, y + rr], outline=col or self.c, width=int(ring))
        else:
            self.d.ellipse([x - rr - 5, y - rr - 5, x + rr + 5, y + rr + 5], fill=self.lo)
            self.d.ellipse([x - rr, y - rr, x + rr, y + rr], fill=fill or col or self.c)
    def ellipse(self, cx, cy, rx, ry, fill=None, ring=0, col=None):
        x, y = self.P(cx, cy)
        box = [x - rx * S, y - ry * S, x + rx * S, y + ry * S]
        if ring:
            self.d.ellipse(box, outline=self.lo, width=int(ring * 1.7)); self.d.ellipse(box, outline=col or self.c, width=int(ring))
        else:
            self.d.ellipse([box[0] - 5, box[1] - 5, box[2] + 5, box[3] + 5], fill=self.lo); self.d.ellipse(box, fill=fill or col or self.c)
    def arc_pts(self, cx, cy, r, a0, a1, n=24, sq=1.0):
        return [(cx + math.cos(math.radians(a0 + (a1 - a0) * i / n)) * r, cy + math.sin(math.radians(a0 + (a1 - a0) * i / n)) * r * sq) for i in range(n + 1)]
    def rect(self, a, b, fill, radius=0):
        x0, y0 = self.P(*a); x1, y1 = self.P(*b)
        if radius: self.d.rounded_rectangle([x0, y0, x1, y1], radius=radius, fill=fill)
        else: self.d.rectangle([x0, y0, x1, y1], fill=fill)
    def glow(self, cx, cy, r, col=None):
        g = Image.new('RGBA', (S, S), (0, 0, 0, 0)); gd = ImageDraw.Draw(g)
        x, y = self.P(cx, cy)
        gd.ellipse([x - r * S, y - r * S, x + r * S, y + r * S], fill=(*(col or self.hi), 110))
        g = g.filter(ImageFilter.GaussianBlur(S * 0.06))
        self.img.alpha_composite(g)
        self.d = ImageDraw.Draw(self.img)

# --- motifs: each draws into a sub-rectangle given by (ox, oy, sc): coordinates 0..1 map to the box --------------------
def T(ctx, ox, oy, sc):
    class V:  # transforms normalized coordinates
        pass
    return lambda x, y: (ox + x * sc, oy + y * sc)

def m_flame(c, f, col=None):
    cc = col or c.c
    old = c.c; c.c = cc; c.lo = dark(cc, .6)
    c.poly([f(.5, .02), f(.66, .3), f(.86, .5), f(.86, .75), f(.7, .95), f(.3, .95), f(.14, .75), f(.2, .5), f(.34, .38), f(.38, .15)])
    c.poly([f(.5, .35), f(.62, .55), f(.66, .78), f(.5, .92), f(.34, .78), f(.38, .55)], fill=light(cc, .55), outline=False)
    c.poly([f(.5, .6), f(.57, .75), f(.5, .88), f(.43, .75)], fill=light(cc, .85), outline=False)
    c.c = old; c.lo = dark(old, .55)
def m_bolt(c, f, col=None):
    cc = col or c.c; old = c.c; c.c = cc
    c.poly([f(.58, .02), f(.22, .55), f(.46, .55), f(.34, .98), f(.8, .38), f(.54, .38), f(.7, .02)])
    c.c = old
def m_orb(c, f, col=None):
    cc = col or c.c
    c.circle(*f(.5, .5), .38 * 1, fill=cc) if False else None
    x, y = f(.5, .5); r = (f(1, 0)[0] - f(0, 0)[0]) * .4
    c.circle(x, y, r, fill=cc)
    c.circle(x - r * .15, y - r * .15, r * .72, fill=light(cc, .25))
    c.circle(x - r * .3, y - r * .3, r * .3, fill=light(cc, .8))
def m_ring(c, f, col=None, double=False):
    x, y = f(.5, .5); r = (f(1, 0)[0] - f(0, 0)[0]) * .4
    c.circle(x, y, r, ring=14, col=col)
    if double: c.circle(x, y, r * .55, ring=10, col=col)
def m_star(c, f, col=None, pts=4, inner=.28):
    cc = col or c.c; old = c.c; c.c = cc
    out = []
    for i in range(pts * 2):
        a = -math.pi / 2 + math.pi * i / pts
        r = .48 if i % 2 == 0 else .48 * inner * (pts / 4 if pts > 4 else 1)
        out.append(f(.5 + math.cos(a) * r, .5 + math.sin(a) * r))
    c.poly(out); c.c = old
def m_sun(c, f, col=None):
    cc = col or c.c
    for i in range(12):
        a = math.pi * 2 * i / 12
        c.line([f(.5 + math.cos(a) * .3, .5 + math.sin(a) * .3), f(.5 + math.cos(a) * .48, .5 + math.sin(a) * .48)], w=11, col=light(cc, .2))
    x, y = f(.5, .5); r = (f(1, 0)[0] - f(0, 0)[0]) * .27
    c.circle(x, y, r, fill=cc); c.circle(x - r * .2, y - r * .2, r * .6, fill=light(cc, .6))
def m_leaf(c, f, col=None):
    cc = col or c.c; old = c.c; c.c = cc
    c.poly([f(.08, .92), f(.15, .55), f(.4, .22), f(.78, .08), f(.92, .1), f(.88, .45), f(.62, .8), f(.25, .92)])
    c.line([f(.12, .9), f(.55, .45), f(.85, .15)], w=7, col=light(cc, .6), outline=False)
    c.c = old
def m_flower(c, f, col=None):
    cc = col or c.c
    for i in range(6):
        a = math.pi * 2 * i / 6
        x, y = f(.5 + math.cos(a) * .27, .5 + math.sin(a) * .27); r = (f(1, 0)[0] - f(0, 0)[0]) * .2
        c.circle(x, y, r, fill=cc)
    x, y = f(.5, .5); r = (f(1, 0)[0] - f(0, 0)[0]) * .16
    c.circle(x, y, r, fill=(255, 225, 100))
def m_shield(c, f, col=None, mark=True):
    cc = col or c.c; old = c.c; c.c = cc
    c.poly([f(.1, .1), f(.5, .02), f(.9, .1), f(.88, .55), f(.5, .98), f(.12, .55)])
    c.poly([f(.2, .17), f(.5, .1), f(.8, .17), f(.78, .52), f(.5, .88), f(.22, .52)], fill=light(cc, .3), outline=False)
    if mark: c.line([f(.5, .22), f(.5, .78)], w=9, col=light(cc, .85), outline=False); c.line([f(.28, .42), f(.72, .42)], w=9, col=light(cc, .85), outline=False)
    c.c = old
def m_dome(c, f, col=None):
    cc = col or c.c
    c.line(c.arc_pts(*f(.5, .8), (f(1, 0)[0] - f(0, 0)[0]) * .45, 180, 360, 28), w=13, col=cc)
    c.line(c.arc_pts(*f(.5, .8), (f(1, 0)[0] - f(0, 0)[0]) * .28, 180, 360, 20), w=9, col=light(cc, .4))
    c.line([f(.04, .8), f(.96, .8)], w=11, col=cc)
def m_mirror(c, f, col=None):
    cc = col or c.c
    x, y = f(.5, .46); sc = f(1, 0)[0] - f(0, 0)[0]
    c.ellipse(x, y, sc * .3, sc * .42, fill=(190, 225, 255)); c.ellipse(x, y, sc * .3, sc * .42, ring=14, col=cc)
    c.line([f(.38, .3), f(.5, .2)], w=9, col=(255, 255, 255), outline=False); c.line([f(.34, .42), f(.42, .34)], w=7, col=(255, 255, 255), outline=False)
    c.line([f(.5, .88), f(.5, .98)], w=14, col=cc)
def m_eye(c, f, col=None, slit=False):
    cc = col or c.c
    top = [f(.04, .5)] + [f(.04 + .92 * i / 14, .5 - math.sin(math.pi * i / 14) * .3) for i in range(1, 14)] + [f(.96, .5)]
    bot = [f(.96, .5)] + [f(.96 - .92 * i / 14, .5 + math.sin(math.pi * i / 14) * .3) for i in range(1, 14)] + [f(.04, .5)]
    c.poly(top + bot, fill=(235, 235, 245))
    x, y = f(.5, .5); sc = f(1, 0)[0] - f(0, 0)[0]
    c.circle(x, y, sc * .2, fill=cc)
    if slit: c.line([f(.5, .36), f(.5, .64)], w=9, col=(10, 5, 20), outline=False)
    else: c.circle(x, y, sc * .09, fill=(15, 8, 30))
def m_skull(c, f, col=None):
    cc = col or (225, 225, 235)
    x, y = f(.5, .42); sc = f(1, 0)[0] - f(0, 0)[0]
    c.circle(x, y, sc * .36, fill=cc)
    c.rect(f(.34, .62), f(.66, .92), dark(cc, .3))
    for i in range(3): c.line([f(.4 + i * .1, .74), f(.4 + i * .1, .9)], w=6, col=(40, 30, 50), outline=False)
    c.circle(*f(.38, .44), sc * .09, fill=(25, 10, 45)); c.circle(*f(.62, .44), sc * .09, fill=(25, 10, 45))
def m_portal(c, f, col=None):
    cc = col or c.c
    x, y = f(.5, .5); sc = f(1, 0)[0] - f(0, 0)[0]
    c.ellipse(x, y, sc * .26, sc * .44, fill=dark(cc, .6)); c.ellipse(x, y, sc * .26, sc * .44, ring=15, col=cc)
    c.line(c.arc_pts(*f(.5, .5), sc * .14, 20, 300, 18, 1.4), w=7, col=light(cc, .7), outline=False)
    c.line(c.arc_pts(*f(.5, .5), sc * .07, 200, 500, 14, 1.4), w=6, col=light(cc, .9), outline=False)
def m_swirl(c, f, col=None):
    cc = col or c.c
    pts = []
    for i in range(40):
        a = i * 0.34; r = .04 + i * .0105
        pts.append(f(.5 + math.cos(a) * r, .5 + math.sin(a) * r))
    c.line(pts, w=13, col=cc)
def m_wings(c, f, col=None):
    cc = col or c.c; old = c.c; c.c = cc
    for sg in (-1, 1):
        base = [f(.5 + sg * .03, .6), f(.5 + sg * .22, .28), f(.5 + sg * .46, .12), f(.5 + sg * .42, .34), f(.5 + sg * .5, .4), f(.5 + sg * .4, .56), f(.5 + sg * .44, .66), f(.5 + sg * .26, .7), f(.5 + sg * .08, .84)]
        c.poly(base)
    c.c = old
def m_hand(c, f, col=None):
    cc = col or c.c
    c.rect(f(.2, .48), f(.82, .97), c.lo, 26)
    c.rect(f(.25, .52), f(.77, .93), cc, 22)
    for x, top in ((.26, .2), (.4, .08), (.54, .05), (.68, .16)):
        c.rect(f(x - .02, top - .02), f(x + .12, .62), c.lo, 18)
        c.rect(f(x, top), f(x + .1, .6), light(cc, .15), 14)
    c.rect(f(.04, .46), f(.28, .64), c.lo, 14)
    c.rect(f(.06, .48), f(.26, .62), light(cc, .15), 10)
def m_fist(c, f, col=None):
    cc = col or c.c
    c.rect(f(.16, .16), f(.84, .84), c.lo, 44)
    c.rect(f(.2, .2), f(.8, .8), cc, 38)
    for i in range(4): c.line([f(.3 + i * .13, .22), f(.3 + i * .13, .42)], w=7, col=dark(cc, .35), outline=False)
    c.line([f(.24, .62), f(.52, .66)], w=8, col=dark(cc, .35), outline=False)
def m_sword(c, f, col=None, flame=False):
    cc = col or c.c
    c.poly([f(.92, .06), f(.78, .06), f(.2, .64), f(.34, .8)], fill=light(cc, .35))
    c.line([f(.2, .76), f(.44, .52)], w=16, col=(190, 150, 70))
    c.line([f(.28, .62), f(.1, .9)], w=14, col=(120, 80, 40))
def m_chain(c, f, col=None):
    cc = col or c.c
    for k, (x, y, ang) in enumerate(((.3, .3, 40), (.5, .5, -50), (.7, .7, 40))):
        c.ellipse(*f(x, y), (f(1, 0)[0] - f(0, 0)[0]) * .17, (f(1, 0)[0] - f(0, 0)[0]) * .26, ring=13, col=cc) if k == 1 else c.ellipse(*f(x, y), (f(1, 0)[0] - f(0, 0)[0]) * .26, (f(1, 0)[0] - f(0, 0)[0]) * .17, ring=13, col=cc)
def m_crack(c, f, col=None):
    cc = col or c.c
    pts = [f(.55, .02), f(.4, .25), f(.58, .4), f(.38, .62), f(.52, .78), f(.4, .98)]
    c.line(pts, w=15, col=cc)
    c.line([f(.58, .4), f(.82, .5)], w=9, col=light(cc, .3)); c.line([f(.38, .62), f(.14, .7)], w=9, col=light(cc, .3))
def m_tornado(c, f, col=None):
    cc = col or c.c
    for i, (w, y) in enumerate(((.46, .12), (.38, .3), (.3, .48), (.2, .66), (.1, .84))):
        c.ellipse(*f(.5, y), (f(1, 0)[0] - f(0, 0)[0]) * w, (f(1, 0)[0] - f(0, 0)[0]) * .07, fill=mix(cc, light(cc, .4), i / 5))
def m_cloud(c, f, col=None):
    cc = col or (210, 215, 230); sc = f(1, 0)[0] - f(0, 0)[0]
    c.circle(*f(.32, .6), sc * .2, fill=cc); c.circle(*f(.52, .45), sc * .26, fill=cc); c.circle(*f(.72, .6), sc * .2, fill=cc)
    c.rect(f(.32, .5), f(.72, .8), cc)
def m_hourglass(c, f, col=None):
    cc = col or c.c
    c.poly([f(.22, .08), f(.78, .08), f(.54, .5), f(.78, .92), f(.22, .92), f(.46, .5)], fill=(220, 225, 240))
    c.poly([f(.34, .16), f(.66, .16), f(.52, .44), f(.48, .44)], fill=cc, outline=False)
    c.poly([f(.3, .88), f(.7, .88), f(.56, .68), f(.44, .68)], fill=cc, outline=False)
    c.line([f(.18, .08), f(.82, .08)], w=13, col=(150, 110, 60)); c.line([f(.18, .92), f(.82, .92)], w=13, col=(150, 110, 60))
def m_book(c, f, col=None):
    cc = col or c.c
    c.poly([f(.12, .18), f(.5, .28), f(.88, .18), f(.88, .84), f(.5, .94), f(.12, .84)], fill=dark(cc, .15))
    c.poly([f(.18, .24), f(.48, .32), f(.48, .86), f(.18, .78)], fill=(235, 225, 200), outline=False)
    c.poly([f(.52, .32), f(.82, .24), f(.82, .78), f(.52, .86)], fill=(225, 215, 190), outline=False)
    c.line([f(.5, .3), f(.5, .9)], w=7, col=dark(cc, .4), outline=False)
def m_target(c, f, col=None):
    cc = col or c.c; x, y = f(.5, .5); sc = f(1, 0)[0] - f(0, 0)[0]
    c.circle(x, y, sc * .4, ring=12, col=cc); c.circle(x, y, sc * .22, ring=11, col=cc); c.circle(x, y, sc * .07, fill=light(cc, .5))
    c.line([f(.5, .02), f(.5, .2)], w=9, col=cc); c.line([f(.5, .8), f(.5, .98)], w=9, col=cc)
    c.line([f(.02, .5), f(.2, .5)], w=9, col=cc); c.line([f(.8, .5), f(.98, .5)], w=9, col=cc)
def m_cross(c, f, col=None):
    cc = col or c.c
    c.line([f(.15, .15), f(.85, .85)], w=22, col=cc); c.line([f(.85, .15), f(.15, .85)], w=22, col=cc)
def m_tower(c, f, col=None):
    cc = col or c.c
    c.poly([f(.3, .22), f(.7, .22), f(.64, .9), f(.36, .9)], fill=cc)
    c.poly([f(.24, .08), f(.76, .08), f(.7, .22), f(.3, .22)], fill=light(cc, .3)); c.poly([f(.2, .9), f(.8, .9), f(.84, .98), f(.16, .98)], fill=light(cc, .3))
def m_wall(c, f, col=None):
    cc = col or c.c
    for r in range(4):
        for k in range(3):
            off = 0 if r % 2 == 0 else .15
            x = .06 + k * .32 - off + (.16 if r % 2 else 0)
            c.poly([f(max(0.02, x), .06 + r * .22), f(min(.98, x + .3), .06 + r * .22), f(min(.98, x + .3), .24 + r * .22), f(max(0.02, x), .24 + r * .22)], fill=mix(cc, light(cc, .35), (r + k) % 3 / 3.0))
def m_spike(c, f, col=None):
    cc = col or c.c
    c.poly([f(.5, .02), f(.7, .9), f(.3, .9)], fill=cc)
    c.poly([f(.28, .35), f(.4, .9), f(.12, .9)], fill=dark(cc, .15)); c.poly([f(.72, .35), f(.88, .9), f(.6, .9)], fill=dark(cc, .15))
    c.line([f(.5, .12), f(.5, .8)], w=6, col=light(cc, .6), outline=False)
def m_burst(c, f, col=None):
    cc = col or c.c; out = []
    for i in range(16):
        a = math.pi * 2 * i / 16; r = .5 if i % 2 == 0 else .26
        out.append(f(.5 + math.cos(a) * r, .5 + math.sin(a) * r))
    c.poly(out, fill=cc); c.circle(*f(.5, .5), (f(1, 0)[0] - f(0, 0)[0]) * .16, fill=light(cc, .7))
def m_wave(c, f, col=None, n=3):
    cc = col or c.c
    for k in range(n):
        pts = [f(.04 + .92 * i / 20, .28 + k * .22 + math.sin(i * .6 + k) * .07) for i in range(21)]
        c.line(pts, w=11, col=mix(cc, light(cc, .5), k / max(1, n)))
def m_feather(c, f, col=None):
    cc = col or (235, 240, 250)
    c.poly([f(.1, .94), f(.2, .55), f(.45, .24), f(.9, .04), f(.8, .5), f(.5, .8)], fill=cc)
    c.line([f(.12, .92), f(.78, .12)], w=7, col=dark(cc, .3), outline=False)
def m_beam(c, f, col=None):
    cc = col or c.c
    c.line([f(.06, .94), f(.94, .06)], w=34, col=dark(cc, .1)); c.line([f(.06, .94), f(.94, .06)], w=18, col=light(cc, .5), outline=False)
    c.line([f(.06, .94), f(.94, .06)], w=7, col=(255, 255, 255), outline=False)
def m_drop(c, f, col=None):
    cc = col or c.c
    c.poly([f(.5, .04), f(.78, .5), f(.78, .72), f(.5, .94), f(.22, .72), f(.22, .5)], fill=cc)
    c.circle(*f(.4, .62), (f(1, 0)[0] - f(0, 0)[0]) * .07, fill=light(cc, .8))
def m_moon(c, f, col=None):
    cc = col or (235, 230, 255); sc = f(1, 0)[0] - f(0, 0)[0]
    x, y = f(.5, .5)
    c.circle(x, y, sc * .42, fill=cc)
    cut = Image.new('RGBA', (S, S), (0, 0, 0, 0)); cd = ImageDraw.Draw(cut)
    cx, cy = c.P(*f(.66, .42)); rr = sc * .36 * S
    cd.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=(255, 255, 255, 255))
    base = c.img.copy(); mask = cut.split()[3]
    clear = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    c.img = Image.composite(clear, base, mask); c.d = ImageDraw.Draw(c.img)
def m_cloak(c, f, col=None):
    cc = col or c.c
    c.poly([f(.5, .04), f(.76, .24), f(.9, .96), f(.64, .88), f(.5, .98), f(.36, .88), f(.1, .96), f(.24, .24)], fill=dark(cc, .35))
    c.poly([f(.5, .1), f(.66, .26), f(.58, .5), f(.42, .5), f(.34, .26)], fill=(10, 5, 20), outline=False)
    c.circle(*f(.44, .3), (f(1, 0)[0] - f(0, 0)[0]) * .04, fill=light(cc, .8)); c.circle(*f(.56, .3), (f(1, 0)[0] - f(0, 0)[0]) * .04, fill=light(cc, .8))
def m_ghost(c, f, col=None):
    cc = col or (225, 235, 250)
    c.poly([f(.5, .06), f(.78, .22), f(.82, .6), f(.84, .92), f(.7, .82), f(.58, .94), f(.5, .82), f(.4, .94), f(.28, .82), f(.16, .92), f(.18, .6), f(.22, .22)], fill=cc)
    c.circle(*f(.4, .38), (f(1, 0)[0] - f(0, 0)[0]) * .06, fill=(40, 30, 70)); c.circle(*f(.6, .38), (f(1, 0)[0] - f(0, 0)[0]) * .06, fill=(40, 30, 70))
def m_foot(c, f, col=None):
    cc = col or c.c; sc = f(1, 0)[0] - f(0, 0)[0]
    for x, y in ((.34, .6), (.64, .34)):
        c.ellipse(*f(x, y), sc * .12, sc * .2, fill=cc)
        for i in range(4): c.circle(*f(x - .1 + i * .07, y - .26 + abs(i - 1.5) * .02), sc * .03, fill=cc)
def m_anchor(c, f, col=None):
    cc = col or c.c
    c.line([f(.5, .1), f(.5, .88)], w=14, col=cc); c.line([f(.28, .26), f(.72, .26)], w=12, col=cc)
    c.line(c.arc_pts(*f(.5, .6), (f(1, 0)[0] - f(0, 0)[0]) * .36, 10, 170, 18), w=14, col=cc); c.circle(*f(.5, .1), (f(1, 0)[0] - f(0, 0)[0]) * .07, ring=9, col=cc)
def m_arrows_ud(c, f, col=None):
    cc = col or c.c
    c.poly([f(.3, .02), f(.52, .4), f(.08, .4)], fill=cc); c.poly([f(.7, .98), f(.92, .6), f(.48, .6)], fill=light(cc, .3))
def m_arrow_up(c, f, col=None):
    cc = col or c.c
    c.poly([f(.5, .02), f(.86, .5), f(.62, .5), f(.62, .96), f(.38, .96), f(.38, .5), f(.14, .5)], fill=cc)
def m_vine(c, f, col=None):
    cc = col or c.c
    pts = [f(.1, .96), f(.2, .7), f(.46, .6), f(.62, .4), f(.6, .2), f(.8, .06)]
    c.line(pts, w=14, col=cc); m_leaf(c, lambda x, y: f(.44 + x * .34, .1 + y * .32), light(cc, .1)); m_leaf(c, lambda x, y: f(.02 + x * .36, .52 + y * .3), cc)
def m_roots(c, f, col=None):
    cc = col or (150, 100, 60)
    for x0, x1 in ((.2, .1), (.4, .38), (.6, .66), (.8, .92)):
        c.line([f(x0, .06), f((x0 + x1) / 2 + .05, .5), f(x1, .94)], w=12, col=cc)
    c.line([f(.05, .5), f(.95, .5)], w=8, col=(100, 70, 40), outline=False)
def m_spikes_fan(c, f, col=None):
    cc = col or c.c
    for a in (-50, -25, 0, 25, 50):
        r = math.radians(a - 90)
        tip = (.5 + math.cos(r) * .52, .92 + math.sin(r) * .86)
        base1 = (.5 + math.cos(r + 1.57) * .06, .92 + math.sin(r + 1.57) * .06); base2 = (.5 + math.cos(r - 1.57) * .06, .92 + math.sin(r - 1.57) * .06)
        c.poly([f(*tip), f(*base1), f(*base2)], fill=cc)
def m_pillars(c, f, col=None):
    cc = col or c.c
    for x, h in ((.12, .5), (.34, .8), (.56, .65), (.78, .9)):
        c.poly([f(x, .96 - h), f(x + .14, .96 - h), f(x + .14, .96), f(x, .96)], fill=mix(cc, light(cc, .4), h))
def m_gear_dots(c, f, col=None, n=7):
    cc = col or c.c
    random.seed(4)
    for _ in range(n): c.circle(*f(random.uniform(.1, .9), random.uniform(.1, .9)), (f(1, 0)[0] - f(0, 0)[0]) * random.uniform(.03, .06), fill=cc)
def m_slash(c, f, col=None):
    cc = col or c.c
    c.line(c.arc_pts(*f(.2, .9), (f(1, 0)[0] - f(0, 0)[0]) * .85, -85, -5, 22), w=22, col=cc)
    c.line(c.arc_pts(*f(.2, .9), (f(1, 0)[0] - f(0, 0)[0]) * .85, -80, -10, 22), w=8, col=(255, 255, 255), outline=False)
def m_bridge(c, f, col=None):
    cc = col or c.c
    c.line(c.arc_pts(*f(.5, 1.0), (f(1, 0)[0] - f(0, 0)[0]) * .46, 180, 360, 24), w=24, col=cc)
    c.line(c.arc_pts(*f(.5, 1.0), (f(1, 0)[0] - f(0, 0)[0]) * .46, 185, 355, 24), w=8, col=light(cc, .7), outline=False)
def m_bolts3(c, f, col=None):
    for k in range(3): m_bolt(c, lambda x, y, k=k: f(.04 + k * .3 + x * .34, .14 + (k % 2) * .08 + y * .7), col)
def m_dash(c, f, col=None):
    cc = col or c.c
    for y in (.3, .5, .7): c.line([f(.04, y), f(.5 + (.1 if y == .5 else 0), y)], w=9, col=light(cc, .3), outline=False)
    c.poly([f(.96, .5), f(.58, .18), f(.58, .82)], fill=cc)
def m_crossbones(c, f, col=None): m_cross(c, f, col)

MOTIFS = {k[2:]: v for k, v in list(globals().items()) if k.startswith("m_")}

def draw(name, col, items, glow_col=None, bg=True):
    """items: list of (motif, (ox, oy, scale), extra-kwargs)."""
    c = Ctx(col)
    if bg: c.glow(.5, .5, .38, glow_col)
    for it in items:
        mot, box = it[0], it[1]
        kw = it[2] if len(it) > 2 else {}
        ox, oy, sc = box
        f = lambda x, y, ox=ox, oy=oy, sc=sc: (ox + x * sc, oy + y * sc)
        MOTIFS[mot](c, f, **kw)
    img = c.img.resize((N, N), Image.LANCZOS)
    img.save(OUT + name + '.png')

FULL = (0.04, 0.04, 0.92)
BIG = (0.1, 0.1, 0.8)
SMALL_TR = (0.58, 0.0, 0.4)
SMALL_BR = (0.58, 0.58, 0.4)
SMALL_BL = (0.0, 0.58, 0.4)
SMALL_TL = (0.0, 0.0, 0.4)
LEFT = (0.0, 0.1, 0.8)
RIGHT = (0.2, 0.1, 0.8)

def C(name, rgbv): return rgb(CAT[name]) if isinstance(rgbv, str) else rgbv

SPELLS = {
 # arcane
 "arcane_bolt": ("arcane", [("bolt", BIG)]),
 "arcane_barrage": ("arcane", [("bolts3", FULL)]),
 "arcane_burst": ("arcane", [("burst", FULL)]),
 "arcane_beam": ("arcane", [("beam", FULL)]),
 "rune_mine": ("arcane", [("ring", FULL, {"double": True}), ("star", (0.3, 0.3, 0.4), {"pts": 5, "inner": .45})]),
 "arcane_orb": ("arcane", [("orb", FULL)]),
 "spell_echo": ("arcane", [("ring", (0.0, 0.1, 0.7), {}), ("ring", (0.3, 0.2, 0.7), {}), ("bolt", (0.34, 0.3, 0.35), {"col": (255, 255, 255)})]),
 "dispel": ("arcane", [("ring", FULL, {}), ("cross", (0.28, 0.28, 0.44), {"col": (255, 255, 255)})]),
 "arcane_mark": ("arcane", [("target", FULL)]),
 "mana_pulse": ("arcane", [("wave", FULL, {"n": 3}), ("star", (0.36, 0.0, 0.3), {})]),
 # elemental
 "firebolt": ("fire", [("flame", BIG)]),
 "flame_lance": ("fire", [("sword", FULL, {}), ("flame", SMALL_BL, {})]),
 "firestorm": ("fire", [("flame", (0.0, 0.25, 0.55)), ("flame", (0.45, 0.0, 0.55)), ("flame", (0.3, 0.45, 0.6), {"col": (255, 200, 60)})]),
 "phoenix_wings": ("fire", [("wings", FULL, {"col": (255, 140, 40)}), ("flame", (0.32, 0.3, 0.38), {"col": (255, 220, 90)})]),
 "magma_grasp": ("fire", [("wall", FULL, {"col": (120, 70, 50)}), ("flame", (0.3, 0.0, 0.42), {})]),
 "frost_shard": ("ice", [("spike", BIG, {"col": (150, 225, 255)})]),
 "glacial_prison": ("ice", [("wall", FULL, {"col": (150, 225, 255)}), ("star", (0.3, 0.3, 0.4), {"pts": 6, "inner": .5, "col": (255, 255, 255)})]),
 "blizzard": ("ice", [("cloud", (0.0, 0.0, 0.8), {}), ("star", (0.5, 0.45, 0.5), {"pts": 6, "inner": .5, "col": (200, 240, 255)})]),
 "ice_bridge": ("ice", [("bridge", FULL, {"col": (150, 225, 255)}), ("star", (0.36, 0.05, 0.3), {"pts": 6, "inner": .5, "col": (255, 255, 255)})]),
 "lightning_bolt": ("storm", [("bolt", FULL)]),
 "chain_lightning": ("storm", [("bolt", (0.0, 0.0, 0.7)), ("chain", (0.4, 0.4, 0.6), {"col": (190, 200, 255)})]),
 "thunderstep": ("storm", [("wings", FULL, {"col": (255, 235, 120)}), ("bolt", (0.3, 0.2, 0.4), {"col": (255, 255, 255)})]),
 "stormcall": ("storm", [("cloud", (0.0, 0.0, 0.9), {"col": (120, 130, 160)}), ("bolt", (0.3, 0.38, 0.5), {})]),
 "stone_spike": ("earth", [("spike", BIG, {"col": (170, 160, 150)})]),
 "earth_wall": ("earth", [("wall", FULL, {"col": (160, 150, 140)})]),
 "seismic_wave": ("earth", [("crack", (0.0, 0.0, 0.7), {}), ("wave", (0.3, 0.45, 0.7), {"n": 2})]),
 "sand_vortex": ("earth", [("tornado", FULL, {"col": (225, 195, 120)})]),
 # force
 "force_push": ("force", [("hand", (0.0, 0.05, 0.85)), ("wave", (0.62, 0.15, 0.4), {"n": 2})]),
 "force_wave": ("force", [("wave", FULL, {"n": 4})]),
 "force_pull": ("force", [("hand", (0.1, 0.1, 0.8)), ("swirl", (0.5, 0.0, 0.5), {"col": (230, 240, 255)})]),
 "levitation": ("force", [("feather", FULL, {}), ("arrow_up", (0.58, 0.46, 0.42), {"col": (160, 220, 255)})]),
 "gravity_well": ("force", [("orb", FULL, {"col": (60, 70, 110)}), ("ring", FULL, {})]),
 "repulsion_field": ("force", [("ring", FULL, {"double": True}), ("burst", (0.32, 0.32, 0.36), {})]),
 "kinetic_slam": ("force", [("fist", BIG), ("crack", (0.5, 0.52, 0.5), {"col": (255, 255, 255)})]),
 "force_jump": ("force", [("arrow_up", (0.15, 0.0, 0.7)), ("cloud", (0.0, 0.55, 0.9), {"col": (230, 235, 245)})]),
 "kinetic_catch": ("force", [("shield", (0.1, 0.0, 0.8), {"mark": False}), ("orb", (0.3, 0.3, 0.4), {"col": (150, 230, 255)})]),
 # nature
 "vine_lash": ("nature", [("vine", FULL)]),
 "thorn_lash": ("nature", [("vine", FULL), ("spike", (0.62, 0.62, 0.34), {"col": (200, 240, 140)})]),
 "root_snare": ("nature", [("roots", FULL)]),
 "thorn_volley": ("nature", [("spikes_fan", FULL, {"col": (120, 200, 90)})]),
 "healing_bloom": ("nature", [("flower", (0.0, 0.0, 0.9), {"col": (255, 130, 200)}), ("cross", (0.62, 0.62, 0.3), {"col": (120, 255, 150)})]),
 "guardian_vine": ("nature", [("vine", (0.0, 0.1, 0.9)), ("eye", (0.5, 0.0, 0.5), {"slit": True, "col": (255, 230, 100)})]),
 "pollen_veil": ("nature", [("cloud", (0.0, 0.1, 0.9), {"col": (230, 215, 110)}), ("gear_dots", FULL, {"col": (255, 245, 170), "n": 9})]),
 "natures_step": ("nature", [("leaf", (0.0, 0.0, 0.7)), ("dash", (0.3, 0.4, 0.7), {"col": (160, 235, 120)})]),
 "blooming_ground": ("nature", [("flower", (0.0, 0.4, 0.55), {"col": (255, 225, 90)}), ("flower", (0.45, 0.1, 0.55), {"col": (255, 140, 210)}), ("flower", (0.45, 0.55, 0.4), {"col": (150, 200, 255)})]),
 # defense
 "magic_shield": ("defense", [("shield", FULL)]),
 "aegis_shield": ("defense", [("shield", FULL, {"col": (255, 215, 110)}), ("star", (0.34, 0.28, 0.32), {"col": (255, 255, 255)})]),
 "aegis_dome": ("defense", [("dome", FULL, {"col": (255, 215, 110)}), ("star", (0.34, 0.3, 0.32), {"col": (255, 255, 255)})]),
 "counterspell": ("defense", [("shield", (0.0, 0.0, 0.7), {"mark": False}), ("burst", (0.4, 0.4, 0.6), {"col": (255, 235, 150)})]),
 "mirror_ward": ("defense", [("mirror", FULL), ("shield", (0.6, 0.6, 0.38), {"mark": False})]),
 "phase_guard": ("defense", [("ghost", FULL, {"col": (190, 215, 255)})]),
 "cleansing_light": ("defense", [("sun", (0.0, 0.0, 0.8)), ("drop", (0.58, 0.5, 0.42), {"col": (150, 230, 255)})]),
 "rescue_blink": ("defense", [("wings", FULL, {"col": (150, 215, 255)}), ("portal", (0.32, 0.28, 0.4), {})]),
 # dark
 "shadow_bolt": ("dark", [("bolt", BIG), ("gear_dots", FULL, {"col": (40, 15, 70), "n": 6})]),
 "void_bolt": ("dark", [("orb", (0.0, 0.2, 0.7), {"col": (30, 10, 60)}), ("bolt", (0.45, 0.0, 0.55), {"col": (210, 160, 255)})]),
 "shadow_grasp": ("dark", [("hand", FULL, {"col": (60, 20, 110)}), ("vine", (0.0, 0.0, 0.5), {"col": (140, 70, 220)})]),
 "abyssal_chains": ("dark", [("chain", FULL, {"col": (120, 70, 190)}), ("skull", (0.6, 0.6, 0.34), {})]),
 "soul_drain": ("dark", [("beam", FULL, {"col": (150, 80, 255)}), ("skull", (0.0, 0.58, 0.4), {})]),
 "umbral_cloak": ("dark", [("cloak", FULL)]),
 "black_flame": ("dark", [("flame", BIG, {"col": (150, 70, 255)}), ("flame", (0.3, 0.3, 0.4), {"col": (30, 10, 60)})]),
 "nightfall": ("dark", [("moon", FULL), ("star", (0.64, 0.12, 0.26), {"col": (255, 245, 200)}), ("star", (0.7, 0.55, 0.18), {"col": (255, 245, 200)})]),
 "slow_field": ("time", [("ring", FULL, {"double": True}), ("hourglass", (0.3, 0.3, 0.4), {"col": (255, 214, 120)})]),
 "quicken": ("time", [("wings", FULL, {"col": (150, 255, 235)}), ("hourglass", (0.3, 0.2, 0.4), {"col": (255, 214, 120)})]),
 "rewind": ("time", [("swirl", FULL, {"col": (120, 240, 220)}), ("hourglass", (0.34, 0.3, 0.32), {"col": (255, 214, 120)})]),
 "stasis": ("time", [("hourglass", BIG, {"col": (255, 214, 120)}), ("ring", FULL, {"col": (61, 224, 200)})]),
 "chrono_bubble": ("time", [("dome", FULL, {"col": (61, 224, 200)}), ("hourglass", (0.3, 0.38, 0.4), {"col": (255, 255, 255)})]),
 "accelerate": ("time", [("leaf", FULL, {"col": (110, 220, 120)}), ("hourglass", (0.5, 0.45, 0.4), {"col": (255, 214, 120)})]),
 "entropy": ("time", [("hourglass", FULL, {"col": (216, 176, 112)}), ("crack", (0.2, 0.15, 0.6), {"col": (255, 255, 255)})]),
 "dark_clone": ("dark", [("ghost", (0.0, 0.0, 0.98), {"col": (36, 16, 70)}), ("eye", (0.2, 0.2, 0.6), {"col": (240, 210, 255), "slit": True}), ("vine", (0.0, 0.5, 0.5), {"col": (150, 80, 255)})]),
 "shadow_clone": ("dark", [("ghost", (0.0, 0.1, 0.72), {"col": (40, 20, 80)}), ("ghost", (0.34, 0.0, 0.66), {"col": (130, 70, 210)})]),
 "curse_mark": ("dark", [("target", FULL, {"col": (160, 80, 255)}), ("skull", (0.3, 0.3, 0.4), {})]),
 "hex": ("dark", [("skull", FULL, {}), ("star", (0.0, 0.0, 0.38), {"pts": 5, "inner": .45, "col": (180, 100, 255)})]),
 "withering_wave": ("dark", [("wave", (0.0, 0.2, 0.9), {"n": 3, "col": (140, 70, 220)}), ("skull", (0.6, 0.0, 0.4), {})]),
 "shadowstep": ("dark", [("foot", FULL, {"col": (150, 80, 255)}), ("portal", (0.5, 0.0, 0.48), {})]),
 "dark_nova": ("dark", [("burst", FULL, {"col": (150, 70, 255)}), ("orb", (0.3, 0.3, 0.4), {"col": (20, 8, 40)})]),
 "eclipse_nova": ("dark", [("burst", FULL, {"col": (210, 170, 255)}), ("moon", (0.2, 0.2, 0.6), {"col": (20, 8, 40)})]),
 "void_rift": ("dark", [("swirl", FULL, {"col": (150, 80, 255)}), ("ring", FULL, {"col": (40, 15, 80)})]),
 "corruption_orb": ("dark", [("orb", (0.0, 0.0, 0.8), {"col": (110, 50, 190)}), ("drop", (0.6, 0.58, 0.34), {"col": (60, 20, 110)})]),
 "devouring_shadow": ("dark", [("drop", (0.1, 0.0, 0.8), {"col": (50, 20, 90)}), ("eye", (0.2, 0.55, 0.6), {"slit": True, "col": (200, 120, 255)})]),
 "dark_pact": ("dark", [("book", FULL, {"col": (110, 50, 190)}), ("skull", (0.3, 0.2, 0.4), {"col": (230, 215, 240)})]),
 "eclipsing_beam": ("dark", [("moon", (0.0, 0.0, 0.6), {"col": (230, 220, 255)}), ("beam", FULL, {"col": (120, 50, 230)})]),
 # illusion
 "mirror_image": ("illusion", [("mirror", FULL)]),
 "phantom_double": ("illusion", [("ghost", (0.0, 0.1, 0.72), {"col": (230, 200, 255)}), ("ghost", (0.34, 0.0, 0.66), {"col": (180, 130, 240)})]),
 "veil_of_shadows": ("illusion", [("cloak", FULL, {"col": (200, 140, 255)}), ("gear_dots", FULL, {"col": (255, 255, 255), "n": 6})]),
 "false_footsteps": ("illusion", [("foot", FULL), ("wave", (0.0, 0.0, 0.5), {"n": 2, "col": (240, 220, 255)})]),
 "hall_of_mirrors": ("illusion", [("mirror", (0.0, 0.2, 0.6)), ("mirror", (0.2, 0.0, 0.6), {"col": (170, 230, 255)}), ("mirror", (0.4, 0.2, 0.6))]),
 "phantom_wall": ("illusion", [("wall", FULL, {"col": (170, 150, 200)}), ("ghost", (0.3, 0.3, 0.4), {"col": (255, 255, 255)})]),
 "dread_vision": ("illusion", [("eye", FULL, {"col": (255, 70, 110), "slit": True}), ("skull", (0.6, 0.6, 0.38), {"col": (230, 200, 255)})]),
 # space
 "blink": ("space", [("dash", FULL)]),
 "rift_step": ("space", [("portal", (0.0, 0.0, 0.8)), ("dash", (0.5, 0.5, 0.5), {"col": (220, 180, 255)})]),
 "portal_pair": ("space", [("portal", (0.0, 0.0, 0.7)), ("portal", (0.3, 0.3, 0.7), {"col": (255, 130, 220)})]),
 "spatial_anchor": ("space", [("anchor", FULL)]),
 "gravity_swap": ("space", [("arrows_ud", FULL)]),
 "rift_snare": ("space", [("ring", FULL, {"col": (150, 70, 255)}), ("roots", (0.18, 0.1, 0.64), {"col": (50, 20, 90)})]),
 "dimensional_slash": ("space", [("slash", FULL)]),
 "pocket_rift": ("space", [("book", FULL, {"col": (110, 50, 200)}), ("portal", (0.5, 0.0, 0.5), {"col": (230, 190, 255)})]),
 "blink_rift": ("space", [("portal", (0.0, 0.1, 0.55)), ("dash", (0.2, 0.3, 0.6)), ("portal", (0.55, 0.1, 0.45), {"col": (255, 140, 220)})]),
 "rift_gate": ("space", [("portal", FULL, {"col": (160, 120, 255)}), ("star", (0.34, 0.3, 0.32), {"col": (255, 255, 255)})]),
 # light
 "radiant_bolt": ("light", [("bolt", BIG, {"col": (255, 235, 120)}), ("star", SMALL_TL, {"col": (255, 255, 255)})]),
 "radiant_lance": ("light", [("sword", FULL, {"col": (255, 240, 170)}), ("sun", SMALL_BL, {})]),
 "sunburst": ("light", [("sun", FULL)]),
 "holy_chains": ("light", [("chain", FULL, {"col": (255, 225, 130)}), ("star", (0.6, 0.0, 0.4), {"col": (255, 255, 255)})]),
 "beacon": ("light", [("star", FULL, {"pts": 4, "inner": .22}), ("circle_dummy", (0, 0, 0), {})] if False else [("star", FULL, {"pts": 4, "inner": .22}), ("orb", (0.3, 0.3, 0.4), {"col": (255, 250, 210)})]),
 "sanctuary": ("light", [("pillars", FULL, {"col": (255, 225, 130)}), ("ring", (0.0, 0.5, 1.0), {})]),
 # forbidden
 "arcane_overload": ("forbidden", [("star", FULL, {"pts": 8, "inner": .45}), ("ring", (0.2, 0.2, 0.6), {"double": True})]),
 "black_hole": ("forbidden", [("swirl", FULL, {"col": (255, 140, 60)}), ("orb", (0.3, 0.3, 0.4), {"col": (8, 4, 16)})]),
 "time_fracture": ("forbidden", [("hourglass", FULL, {"col": (255, 200, 90)}), ("crack", (0.3, 0.1, 0.4), {"col": (255, 255, 255)})]),
 "reality_tear": ("forbidden", [("crack", FULL, {"col": (255, 255, 255)}), ("ring", FULL, {"col": (224, 36, 80)})]),
 "spectral_legion": ("forbidden", [("ghost", (0.0, 0.3, 0.5), {"col": (170, 230, 255)}), ("ghost", (0.5, 0.3, 0.5), {"col": (170, 230, 255)}), ("ghost", (0.25, 0.0, 0.5), {"col": (255, 255, 255)})]),
 "cataclysm": ("forbidden", [("burst", FULL, {"col": (255, 90, 50)}), ("flame", (0.28, 0.28, 0.44), {"col": (255, 230, 120)})]),
 "void_collapse": ("forbidden", [("orb", (0.2, 0.2, 0.6), {"col": (30, 10, 60)}), ("ring", FULL, {"col": (180, 100, 255)}), ("star", (0.0, 0.0, 0.3), {"col": (255, 255, 255)})]),
}

if __name__ == "__main__":
    for sid, (cat, items) in SPELLS.items():
        col = CAT[cat]
        items = [it for it in items if it[0] in MOTIFS]
        draw(sid, col, items)
    print(len(SPELLS), "icons")
