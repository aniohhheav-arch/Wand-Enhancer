"""Thanos ability icons, stone gems and the Infinity Gauntlet (same style as make_icons.py).

Run:  python3 tools/make_thanos_icons.py   (from MarvelMod/)
"""
import math
import os
import sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_icons import (new, layer, finish, glow, ult_backdrop, crewmate, star, sparkles, ring, dagger,
                        OL, BLACK, WHITE, GOLD, GOLD_L, GOLD_D, GREY, S, OUT, OUT_DIR, Image, ImageDraw, alpha)

STONES = {
    "power":   ((150, 60, 230, 255), (215, 170, 255, 255)),
    "space":   ((40, 120, 255, 255), (170, 210, 255, 255)),
    "reality": ((225, 30, 45, 255), (255, 150, 150, 255)),
    "soul":    ((255, 140, 20, 255), (255, 210, 140, 255)),
    "time":    ((40, 200, 90, 255), (170, 255, 190, 255)),
    "mind":    ((250, 210, 30, 255), (255, 245, 170, 255)),
}
THANOS = (140, 100, 190, 255)      # purple skin
THANOS_D = (90, 60, 130, 255)


def gem(d, cx, cy, r, col, light, ow=OL):
    pts = [(cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a))) for a in range(-90, 270, 60)]
    d.polygon(pts, fill=col, outline=BLACK, width=ow)
    inner = [(cx + r * 0.55 * math.cos(math.radians(a)), cy + r * 0.55 * math.sin(math.radians(a))) for a in range(-90, 270, 60)]
    d.polygon(inner, fill=light)
    d.ellipse([cx - r * 0.5, cy - r * 0.7, cx - r * 0.1, cy - r * 0.3], fill=(255, 255, 255, 200))


def gem_backdrop(img, stone, size=300):
    col, light = STONES[stone]
    def fn(d, _):
        ring(d, 512, 512, 430, alpha(col, 140), 40)
    img = layer(img, fn)
    return img


def thanos_helmet(d, cx, cy, s):
    d.chord([cx - 0.7 * s, cy - 0.9 * s, cx + 0.7 * s, cy + 0.9 * s], 180, 360, fill=GOLD, outline=BLACK, width=OL)
    d.rectangle([cx - 0.7 * s, cy - 0.02 * s, cx + 0.7 * s, cy + 0.25 * s], fill=GOLD, outline=BLACK, width=OL)
    d.rounded_rectangle([cx - 0.5 * s, cy + 0.1 * s, cx + 0.5 * s, cy + 0.95 * s], radius=0.2 * s, fill=THANOS, outline=BLACK, width=OL)
    for f in (-1, 1):  # chin ridges
        d.line([(cx + f * 0.18 * s, cy + 0.55 * s), (cx + f * 0.18 * s, cy + 0.9 * s)], fill=THANOS_D, width=OL)
    d.polygon([(cx - 0.12 * s, cy - 0.9 * s), (cx, cy - 1.25 * s), (cx + 0.12 * s, cy - 0.9 * s)], fill=GOLD, outline=BLACK, width=OL)
    for f in (-1, 1):
        d.polygon([(cx + f * 0.7 * s, cy - 0.1 * s), (cx + f * 0.95 * s, cy - 0.5 * s), (cx + f * 0.72 * s, cy + 0.2 * s)], fill=GOLD_D, outline=BLACK, width=OL)
        d.rectangle([cx + f * 0.32 * s - 0.1 * s, cy + 0.3 * s, cx + f * 0.32 * s + 0.1 * s, cy + 0.38 * s], fill=BLACK)


def gauntlet(d, cx, cy, s, stones_lit=True):
    """Infinity Gauntlet: fist with five knuckle stones + palm stone."""
    d.rounded_rectangle([cx - 0.55 * s, cy + 0.35 * s, cx + 0.55 * s, cy + 1.15 * s], radius=0.15 * s, fill=GOLD_D, outline=BLACK, width=OL)  # cuff
    d.rounded_rectangle([cx - 0.62 * s, cy - 0.45 * s, cx + 0.62 * s, cy + 0.5 * s], radius=0.3 * s, fill=GOLD, outline=BLACK, width=OL)   # hand
    for i in range(4):  # fingers
        x = cx - 0.48 * s + i * 0.32 * s
        d.rounded_rectangle([x - 0.14 * s, cy - 0.95 * s, x + 0.14 * s, cy - 0.3 * s], radius=0.12 * s, fill=GOLD, outline=BLACK, width=OL)
    d.rounded_rectangle([cx + 0.48 * s, cy - 0.35 * s, cx + 0.85 * s, cy + 0.05 * s], radius=0.14 * s, fill=GOLD, outline=BLACK, width=OL)  # thumb
    if stones_lit:
        order = ["mind", "soul", "reality", "space", "power"]
        for i, name in enumerate(order[:4]):
            col, light = STONES[name]
            x = cx - 0.48 * s + i * 0.32 * s
            gem(d, x, cy - 0.62 * s, 0.1 * s, col, light, ow=12)
        col, light = STONES["power"]
        gem(d, cx + 0.66 * s, cy - 0.15 * s, 0.1 * s, col, light, ow=12)
        col, light = STONES["time"]
        gem(d, cx, cy + 0.05 * s, 0.2 * s, col, light, ow=16)


def burst_lines(d, cx, cy, r0, r1, col, n=10, w=30):
    for i in range(n):
        a = i * 2 * math.pi / n
        d.line([(cx + r0 * math.cos(a), cy + r0 * math.sin(a)), (cx + r1 * math.cos(a), cy + r1 * math.sin(a))], fill=col, width=w)


def orb(d, cx, cy, r, col, light):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=col, outline=BLACK, width=OL)
    d.ellipse([cx - r * 0.6, cy - r * 0.6, cx + r * 0.4, cy + r * 0.4], fill=light)


def make(name, stone, draw, ult=False):
    col, light = STONES[stone] if stone in STONES else (GOLD, GOLD_L)
    img = new()
    if ult:
        img = ult_backdrop(img)
    img = layer(img, lambda d, i: draw(d, col, light))
    finish(img, name, glow_col=col, ult=ult)


# ── stone tab icons + gauntlet ───────────────────────────────────────────────

def stone_tabs():
    for name, (col, light) in STONES.items():
        img = new()
        img = layer(img, lambda d, _: gem(d, 512, 512, 330, col, light))
        img = layer(img, lambda d, _: sparkles(d, [(800, 220, 80), (230, 800, 60)]))
        finish(img, f"stone_{name}", glow_col=col)
    img = new()
    img = layer(img, lambda d, _: gauntlet(d, 512, 470, 380))
    finish(img, "gauntlet", glow_col=GOLD)
    img = new()
    img = layer(img, lambda d, _: gauntlet(d, 512, 470, 380, stones_lit=False))
    finish(img, "gauntlet_empty", glow_col=GOLD)


# ── abilities ────────────────────────────────────────────────────────────────

def power_icons():
    make("power_blast", "power", lambda d, c, l: (orb(d, 600, 430, 230, c, l), [d.line([(150 + i * 60, 600 + i * 40), (380 + i * 40, 520 + i * 20)], fill=l, width=40) for i in range(3)]))
    def beam(d, c, l):
        d.polygon([(120, 560), (880, 380), (900, 470), (140, 650)], fill=c, outline=BLACK, width=OL)
        d.polygon([(160, 590), (880, 420), (885, 440), (165, 615)], fill=WHITE)
        orb(d, 200, 600, 120, c, l)
    make("cosmic_beam", "power", beam)
    def shock(d, c, l):
        for r in (420, 320, 220):
            d.arc([512 - r, 600 - r * 0.45, 512 + r, 600 + r * 0.45], 180, 360, fill=c, width=50)
        d.rounded_rectangle([400, 200, 624, 560], radius=60, fill=(140, 100, 190, 255), outline=BLACK, width=OL)  # fist
        for i in range(4):
            d.line([(420 + i * 55, 230), (420 + i * 55, 330)], fill=BLACK, width=16)
    make("shockwave", "power", shock)
    def overload(d, c, l):
        crewmate(d, 512, 560, 250, (140, 100, 190, 255))
        burst_lines(d, 512, 520, 330, 460, c, 12, 36)
        d.polygon([(760, 160), (680, 330), (740, 330), (690, 470), (830, 290), (770, 290), (820, 160)], fill=l, outline=BLACK, width=20)
    make("power_overload", "power", overload)
    def planet(d, c, l):
        d.ellipse([260, 260, 764, 764], fill=(90, 60, 160, 255), outline=BLACK, width=OL)
        for (x0, y0, x1, y1) in ((300, 400, 600, 520), (500, 300, 640, 700), (380, 600, 740, 560)):
            d.line([(x0, y0), (x1, y1)], fill=l, width=34)
        burst_lines(d, 512, 512, 300, 480, c, 14, 40)
    make("planetbreaker", "power", planet, ult=True)


def space_icons():
    make("space_blink", "space", lambda d, c, l: (crewmate(d, 300, 560, 150, alpha(l, 255)), crewmate(d, 720, 520, 170, c), [d.ellipse([x - 25, 530, x + 25, 580], fill=l) for x in (440, 520, 600)]))
    def rift(d, c, l):
        for x in (300, 724):
            for i, r in enumerate((180, 130, 80)):
                d.ellipse([x - r * 0.6, 512 - r, x + r * 0.6, 512 + r], fill=c if i % 2 == 0 else l, outline=BLACK, width=OL)
        d.line([(400, 512), (624, 512)], fill=l, width=40)
        d.polygon([(624, 450), (700, 512), (624, 574)], fill=l, outline=BLACK, width=16)
    make("space_rift", "space", rift)
    def well(d, c, l):
        for i in range(4):
            d.arc([200 + i * 60, 200 + i * 60, 824 - i * 60, 824 - i * 60], i * 60, i * 60 + 250, fill=c if i % 2 == 0 else l, width=44)
        d.ellipse([440, 440, 584, 584], fill=BLACK, outline=l, width=20)
    make("gravity_well", "space", well)
    def prison(d, c, l):
        crewmate(d, 512, 560, 190, GREY)
        d.rectangle([250, 220, 774, 820], outline=BLACK, width=OL + 30)
        d.rectangle([250, 220, 774, 820], outline=c, width=40)
        d.line([(250, 220), (380, 120)], fill=l, width=30); d.line([(774, 220), (900, 120)], fill=l, width=30)
        d.line([(380, 120), (900, 120)], fill=l, width=30); d.line([(900, 120), (900, 700)], fill=l, width=30)
        d.line([(774, 820), (900, 700)], fill=l, width=30)
    make("spatial_prison", "space", prison)
    def exile(d, c, l):
        d.ellipse([200, 200, 824, 824], fill=(10, 20, 60, 255), outline=BLACK, width=OL)
        for (x, y, r) in ((330, 330, 40), (680, 300, 30), (600, 700, 45), (300, 650, 25), (750, 560, 30)):
            star(d, x, y, r, WHITE)
        crewmate(d, 512, 520, 150, GREY)
        ring(d, 512, 512, 330, l, 30)
    make("cosmic_exile", "space", exile, ult=True)


def reality_icons():
    def warp(d, c, l):
        for i in range(5):
            y = 260 + i * 120
            pts = [(150 + x * 10, y + 40 * math.sin(x * 0.45 + i)) for x in range(73)]
            d.line(pts, fill=c if i % 2 == 0 else l, width=36)
    make("reality_warp", "reality", warp)
    def trans(d, c, l):
        crewmate(d, 300, 560, 170, GREY)
        d.polygon([(470, 512), (560, 440), (560, 480), (640, 480), (640, 545), (560, 545), (560, 585)], fill=l, outline=BLACK, width=16)
        d.rectangle([680, 430, 880, 680], fill=(170, 120, 70, 255), outline=BLACK, width=OL)  # crate
        d.line([(680, 430), (880, 680)], fill=BLACK, width=20); d.line([(880, 430), (680, 680)], fill=BLACK, width=20)
    make("transmutation", "reality", trans)
    def shatter(d, c, l):
        pts = [(512, 512)]
        for i in range(7):
            a = i * 2 * math.pi / 7 + 0.3
            d.line([(512, 512), (512 + 200 * math.cos(a), 512 + 200 * math.sin(a)), (512 + 380 * math.cos(a + 0.2), 512 + 380 * math.sin(a + 0.2))], fill=BLACK, width=50)
            d.line([(512, 512), (512 + 200 * math.cos(a), 512 + 200 * math.sin(a)), (512 + 380 * math.cos(a + 0.2), 512 + 380 * math.sin(a + 0.2))], fill=c, width=24)
        orb(d, 512, 512, 90, c, l)
    make("reality_shatter", "reality", shatter)
    def false_world(d, c, l):
        d.rounded_rectangle([170, 220, 854, 800], radius=40, fill=(60, 20, 30, 255), outline=BLACK, width=OL)
        for (x0, y0, x1, y1) in ((240, 300, 480, 480), (540, 300, 780, 420), (540, 480, 780, 720), (240, 540, 480, 720)):
            d.rectangle([x0, y0, x1, y1], outline=c, width=26)
        d.ellipse([360, 380, 660, 640], fill=WHITE, outline=BLACK, width=OL)
        d.ellipse([450, 450, 570, 570], fill=c)
    make("false_world", "reality", false_world)
    def collapse(d, c, l):
        for i in range(10):
            a = i * 2 * math.pi / 10
            d.polygon([(512, 512), (512 + 420 * math.cos(a), 512 + 420 * math.sin(a)), (512 + 380 * math.cos(a + 0.4), 512 + 380 * math.sin(a + 0.4))],
                      fill=c if i % 2 == 0 else l, outline=BLACK, width=16)
        orb(d, 512, 512, 110, c, WHITE)
    make("reality_collapse", "reality", collapse, ult=True)


def soul_icons():
    def sight(d, c, l):
        d.ellipse([120, 330, 904, 694], fill=WHITE, outline=BLACK, width=OL)
        d.ellipse([370, 370, 654, 654], fill=c, outline=BLACK, width=OL)
        d.ellipse([460, 460, 564, 564], fill=BLACK)
        for x in (200, 820):
            crewmate(d, x, 820, 80, alpha(l, 255))
    make("soul_sight", "soul", sight)
    def drain(d, c, l):
        crewmate(d, 280, 560, 170, GREY)
        for i in range(4):
            d.ellipse([430 + i * 90, 470 - i * 20, 490 + i * 90, 530 - i * 20], fill=l, outline=BLACK, width=12)
        orb(d, 800, 400, 110, c, l)
    make("soul_drain", "soul", drain)
    def spirit(d, c, l):
        crewmate(d, 360, 600, 170, GREY)
        crewmate(d, 650, 420, 200, alpha(l, 255))
        burst_lines(d, 650, 420, 260, 330, c, 10, 24)
    make("spirit_projection", "soul", spirit)
    def chains(d, c, l):
        crewmate(d, 512, 540, 220, GREY)
        for y, t in ((380, -40), (560, 40)):
            for i in range(7):
                x = 180 + i * 110
                d.ellipse([x - 55, y + t * (i - 3) / 3 - 30, x + 55, y + t * (i - 3) / 3 + 30], outline=BLACK, width=40)
                d.ellipse([x - 55, y + t * (i - 3) / 3 - 30, x + 55, y + t * (i - 3) / 3 + 30], outline=c, width=20)
    make("soul_chains", "soul", chains)
    def world(d, c, l):
        d.ellipse([170, 170, 854, 854], fill=(120, 50, 10, 255), outline=BLACK, width=OL)
        for x in (300, 512, 724):
            d.rectangle([x - 40, 380, x + 40, 760], fill=l, outline=BLACK, width=16)
        d.ellipse([440, 220, 584, 330], fill=c, outline=BLACK, width=16)
        for (x, y) in ((260, 300), (760, 300), (512, 820)):
            d.ellipse([x - 40, y - 50, x + 40, y + 50], fill=alpha(WHITE, 200))
    make("soul_world", "soul", world, ult=True)


def time_icons():
    def clock(d, cx, cy, r, c, l):
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE, outline=BLACK, width=OL)
        d.ellipse([cx - r * 0.8, cy - r * 0.8, cx + r * 0.8, cy + r * 0.8], outline=c, width=20)
        d.line([(cx, cy), (cx, cy - r * 0.6)], fill=BLACK, width=26)
        d.line([(cx, cy), (cx + r * 0.45, cy + r * 0.1)], fill=BLACK, width=26)
    def freeze(d, c, l):
        crewmate(d, 512, 560, 230, (180, 230, 255, 255))
        for (x, y) in ((220, 260), (820, 300), (780, 760)):
            star(d, x, y, 90, l, points=6, inner=0.3, outline=BLACK, ow=12)
    make("time_freeze", "time", freeze)
    def rewind(d, c, l):
        clock(d, 512, 512, 260, c, l)
        d.arc([180, 180, 844, 844], 200, 340, fill=c, width=50)
        d.polygon([(170, 470), (230, 330), (310, 450)], fill=c, outline=BLACK, width=16)
    make("rewind", "time", rewind)
    make("temporal_echo", "time", lambda d, c, l: (crewmate(d, 300, 560, 170, alpha(l, 255)), crewmate(d, 480, 560, 170, alpha(c, 255)), crewmate(d, 680, 560, 170, (140, 100, 190, 255))))
    def age(d, c, l):
        d.polygon([(330, 200), (694, 200), (520, 512), (694, 824), (330, 824), (504, 512)], fill=(230, 210, 160, 255), outline=BLACK, width=OL)
        d.polygon([(380, 250), (644, 250), (512, 470)], fill=c)
        d.polygon([(400, 790), (624, 790), (512, 640)], fill=c)
    make("age_manipulation", "time", age)
    def loop(d, c, l):
        for i in range(3):
            d.arc([180 + i * 70, 180 + i * 70, 844 - i * 70, 844 - i * 70], i * 90, i * 90 + 300, fill=c if i % 2 == 0 else l, width=46)
        clock(d, 512, 512, 140, c, l)
    make("eternal_loop", "time", loop, ult=True)


def mind_icons():
    def control(d, c, l):
        crewmate(d, 512, 640, 190, GREY)
        for x in (380, 512, 640):
            d.line([(x, 160), (x, 470)], fill=c, width=16)
        d.rounded_rectangle([280, 120, 744, 190], radius=30, fill=GOLD_D, outline=BLACK, width=20)
    make("thanos_mind_control", "mind", control)
    def pulse(d, c, l):
        for r in (420, 320, 220):
            ring(d, 512, 512, r, c, 36)
        gem(d, 512, 512, 120, c, l)
    make("psychic_pulse", "mind", pulse)
    def erase(d, c, l):
        crewmate(d, 512, 560, 230, GREY)
        d.rounded_rectangle([260, 300, 764, 420], radius=40, fill=c, outline=BLACK, width=OL)
        d.line([(320, 360), (700, 360)], fill=WHITE, width=20)
    make("memory_erasure", "mind", erase)
    make("illusion_army", "mind", lambda d, c, l: [thanos_helmet(d, x, y, s) for x, y, s in ((260, 560, 140), (764, 560, 140), (512, 520, 200))])
    def domination(d, c, l):
        thanos_helmet(d, 512, 520, 300)
        burst_lines(d, 512, 512, 380, 470, c, 16, 30)
    make("absolute_domination", "mind", domination, ult=True)


def infinity_icons():
    def surge(d, c, l):
        for i, name in enumerate(STONES):
            col, light = STONES[name]
            ring(d, 512, 512, 420 - i * 55, col, 34)
        gauntlet(d, 512, 470, 180)
    make("infinity_surge", "none", surge, ult=True)
    def snap(d, c, l):
        gauntlet(d, 512, 470, 330)
        for i in range(8):
            a = i * math.pi / 4
            d.line([(800 + 70 * math.cos(a), 220 + 70 * math.sin(a)), (800 + 150 * math.cos(a), 220 + 150 * math.sin(a))], fill=WHITE, width=24)
    make("the_snap", "none", snap, ult=True)
    def judgment(d, c, l):
        cols = [v[0] for v in STONES.values()]
        for i, col in enumerate(cols):
            d.polygon([(420 + i * 30, 330), (450 + i * 30, 330), (380 + i * 50, 900), (330 + i * 50, 900)], fill=col)
        thanos_helmet(d, 512, 240, 150)
    make("titans_judgment", "none", judgment, ult=True)


if __name__ == "__main__":
    stone_tabs()
    power_icons(); space_icons(); reality_icons(); soul_icons(); time_icons(); mind_icons(); infinity_icons()
    print("thanos icons written")
