"""Scarlet Witch, Thor and team-up icons + hammer/axe props. Run: python3 tools/make_hero_icons.py"""
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_icons import (new, layer, finish, ult_backdrop, crewmate, star, sparkles, ring,
                        OL, BLACK, WHITE, GOLD, GOLD_L, GREY, alpha, OUT_DIR, OUT, S)
from PIL import Image, ImageDraw

SCARLET = (230, 26, 56, 255)
SCARLET_L = (255, 120, 140, 255)
SCARLET_D = (120, 6, 26, 255)
BOLT = (170, 215, 255, 255)
BOLT_L = (235, 245, 255, 255)
STORM = (60, 110, 240, 255)
STEEL = (180, 186, 200, 255)
STEEL_D = (110, 116, 130, 255)
LEATHER = (110, 70, 40, 255)
PURPLE = (190, 90, 255, 255)


def wanda(d, cx, cy, s):
    # crown points + red body
    crewmate(d, cx, cy, s, SCARLET, SCARLET_D)
    pts = [(cx - 0.55 * s, cy - 0.75 * s), (cx - 0.35 * s, cy - 1.25 * s), (cx - 0.12 * s, cy - 0.85 * s),
           (cx + 0.12 * s, cy - 0.85 * s), (cx + 0.35 * s, cy - 1.25 * s), (cx + 0.55 * s, cy - 0.75 * s)]
    d.line(pts, fill=BLACK, width=OL + 16, joint="curve")
    d.line(pts, fill=SCARLET_L, width=OL - 4, joint="curve")


def thor(d, cx, cy, s):
    d.polygon([(cx - 0.9 * s, cy + 1.0 * s), (cx - 0.7 * s, cy - 0.5 * s), (cx + 0.7 * s, cy - 0.5 * s), (cx + 0.9 * s, cy + 1.0 * s)],
              fill=SCARLET, outline=BLACK, width=OL)   # red cape
    crewmate(d, cx, cy, s, STORM)
    # winged helmet
    for side in (-1, 1):
        d.polygon([(cx + side * 0.55 * s, cy - 0.7 * s), (cx + side * 1.0 * s, cy - 1.15 * s), (cx + side * 0.75 * s, cy - 0.55 * s)],
                  fill=STEEL, outline=BLACK, width=18)


def hammer(d, cx, cy, s, ang=-0.6):
    def rp(x, y):
        return (cx + x * math.cos(ang) - y * math.sin(ang), cy + x * math.sin(ang) + y * math.cos(ang))
    handle = [rp(-0.2 * s, 0), rp(0.2 * s, 0), rp(0.2 * s, 1.3 * s), rp(-0.2 * s, 1.3 * s)]
    d.polygon(handle, fill=LEATHER, outline=BLACK, width=OL)
    head = [rp(-0.7 * s, -0.55 * s), rp(0.7 * s, -0.55 * s), rp(0.7 * s, 0.15 * s), rp(-0.7 * s, 0.15 * s)]
    d.polygon(head, fill=STEEL, outline=BLACK, width=OL)
    band = [rp(-0.7 * s, -0.25 * s), rp(0.7 * s, -0.25 * s), rp(0.7 * s, -0.12 * s), rp(-0.7 * s, -0.12 * s)]
    d.polygon(band, fill=STEEL_D)


def axe(d, cx, cy, s, ang=-0.5):
    def rp(x, y):
        return (cx + x * math.cos(ang) - y * math.sin(ang), cy + x * math.sin(ang) + y * math.cos(ang))
    d.polygon([rp(-0.08 * s, -0.9 * s), rp(0.08 * s, -0.9 * s), rp(0.08 * s, 1.2 * s), rp(-0.08 * s, 1.2 * s)], fill=LEATHER, outline=BLACK, width=OL)
    d.polygon([rp(0.05 * s, -0.85 * s), rp(0.85 * s, -1.05 * s), rp(0.95 * s, -0.4 * s), rp(0.7 * s, 0.1 * s), rp(0.05 * s, -0.2 * s)],
              fill=STEEL, outline=BLACK, width=OL)
    d.polygon([rp(-0.05 * s, -0.75 * s), rp(-0.45 * s, -0.7 * s), rp(-0.35 * s, -0.35 * s), rp(-0.05 * s, -0.35 * s)], fill=STEEL_D, outline=BLACK, width=18)


def bolt(d, x0, y0, x1, y1, col=BOLT, w=34, segs=6, seed=1):
    import random
    r = random.Random(seed)
    pts = [(x0, y0)]
    for i in range(1, segs):
        t = i / segs
        pts.append((x0 + (x1 - x0) * t + r.uniform(-60, 60), y0 + (y1 - y0) * t + r.uniform(-30, 30)))
    pts.append((x1, y1))
    d.line(pts, fill=BLACK, width=w + 22, joint="curve")
    d.line(pts, fill=col, width=w, joint="curve")
    d.line(pts, fill=BOLT_L, width=max(6, w // 3), joint="curve")


def tendril(d, x0, y0, x1, y1, col=SCARLET, w=30, waves=2.5, amp=50):
    pts = []
    for i in range(30):
        t = i / 29
        nx, ny = -(y1 - y0), (x1 - x0)
        ln = math.hypot(nx, ny) or 1
        o = math.sin(t * math.pi * waves * 2) * amp * (1 - t * 0.5)
        pts.append((x0 + (x1 - x0) * t + nx / ln * o, y0 + (y1 - y0) * t + ny / ln * o))
    d.line(pts, fill=BLACK, width=w + 20, joint="curve")
    d.line(pts, fill=col, width=w, joint="curve")


def hexagram(d, cx, cy, r, col=SCARLET):
    for rot in (0, math.pi / 3):
        pts = [(cx + r * math.cos(rot + i * 2 * math.pi / 3 - math.pi / 2), cy + r * math.sin(rot + i * 2 * math.pi / 3 - math.pi / 2)) for i in range(3)]
        d.polygon(pts, outline=BLACK, width=OL + 14)
        d.polygon(pts, outline=col, width=OL - 4)
    ring(d, cx, cy, r * 1.08, col, 26)


def make(name, draw, glow, ult=False):
    img = ult_backdrop(new()) if ult else new()
    img = layer(img, lambda d, _: draw(d))
    finish(img, name, glow_col=glow, ult=ult)


def prop(name, draw):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw(ImageDraw.Draw(img))
    img.resize((OUT, OUT), Image.LANCZOS).save(os.path.join(OUT_DIR, name + ".png"))


ICONS = {
    # Scarlet Witch
    "chaos_bolt": (lambda d: (tendril(d, 200, 800, 700, 330), d.ellipse([600, 220, 840, 460], fill=SCARLET, outline=BLACK, width=OL), hexagram(d, 720, 340, 90, SCARLET_L)), SCARLET, False),
    "telekinetic_grip": (lambda d: (crewmate(d, 512, 420, 180, GREY), [tendril(d, 300 + i * 200, 860, 470 + i * 40, 520, SCARLET, 24, 2, 30) for i in range(3)]), SCARLET, False),
    "crimson_barrier": (lambda d: (d.ellipse([140, 160, 884, 904], fill=alpha(SCARLET, 90), outline=BLACK, width=OL), hexagram(d, 512, 530, 300, SCARLET_L), wanda(d, 512, 600, 150)), SCARLET, False),
    "hex_of_deception": (lambda d: (hexagram(d, 512, 512, 330), crewmate(d, 380, 560, 120, alpha(SCARLET_L, 160)), crewmate(d, 650, 560, 120, alpha(SCARLET_L, 160)), d.arc([430, 300, 594, 440], 180, 400, fill=WHITE, width=34), d.ellipse([494, 470, 530, 506], fill=WHITE)), SCARLET, False),
    "reality_rewrite": (lambda d: ([d.line([(512, 512), (512 + 420 * math.cos(a), 512 + 420 * math.sin(a))], fill=SCARLET_L, width=24) for a in [i * 0.8 for i in range(8)]], hexagram(d, 512, 512, 240), wanda(d, 512, 560, 140)), SCARLET, True),
    "crimson_execution": (lambda d: (hexagram(d, 512, 420, 280), crewmate(d, 512, 470, 130, GREY), [tendril(d, 512 + 380 * math.cos(a), 512 + 380 * math.sin(a), 512, 470, SCARLET, 22) for a in (0.6, 2.5, 4.4)]), SCARLET, False),
    "house_of_m": (lambda d: (d.polygon([(200, 820), (200, 400), (512, 180), (824, 400), (824, 820)], fill=SCARLET_D, outline=BLACK, width=OL), d.line([(380, 560), (380, 380), (512, 480), (644, 380), (644, 560)], fill=SCARLET_L, width=40, joint="curve"), wanda(d, 512, 640, 150)), SCARLET, False),
    "puppet_of_chaos": (lambda d: (crewmate(d, 512, 600, 170, GREY), [tendril(d, 320 + i * 120, 120, 420 + i * 60, 470, SCARLET_L, 16, 1.5, 20) for i in range(4)], d.line([(260, 140), (764, 140)], fill=SCARLET, width=50)), SCARLET, False),
    "reality_fracture": (lambda d: (d.rectangle([180, 180, 844, 844], fill=alpha(SCARLET_L, 120), outline=BLACK, width=OL), [d.line([(512, 512), (512 + 380 * math.cos(a), 512 + 380 * math.sin(a))], fill=BLACK, width=22) for a in [i * 0.95 for i in range(7)]], crewmate(d, 512, 540, 110, GREY)), SCARLET, False),
    "no_more": (lambda d: (hexagram(d, 512, 512, 380), wanda(d, 512, 560, 190), sparkles(d, [(200, 220, 60), (830, 250, 60)], SCARLET_L)), SCARLET, True),
    # Thor
    "mjolnir_throw": (lambda d: (bolt(d, 160, 840, 540, 520, seed=3), hammer(d, 640, 380, 240)), BOLT, False),
    "lightning_strike": (lambda d: (bolt(d, 600, 60, 480, 760, w=50, seed=5), d.ellipse([300, 720, 720, 880], outline=BOLT, width=30), crewmate(d, 512, 700, 100, GREY)), BOLT, False),
    "thunderclap": (lambda d: (ring(d, 512, 640, 380, BOLT, 30), ring(d, 512, 640, 250, BOLT_L, 22), hammer(d, 512, 420, 200, 0)), BOLT, False),
    "stormflight": (lambda d: ([bolt(d, 140, 820 - i * 90, 560, 560 - i * 60, w=22, seed=i) for i in range(3)], thor(d, 680, 430, 170)), BOLT, False),
    "god_of_thunder": (lambda d: (d.ellipse([120, 60, 904, 360], fill=(40, 46, 70, 255), outline=BLACK, width=OL), [bolt(d, 300 + i * 220, 300, 260 + i * 240, 900, w=40, seed=i + 7) for i in range(3)], thor(d, 512, 650, 150)), BOLT, True),
    "the_worthy": (lambda d: (hammer(d, 512, 330, 260, 0), [bolt(d, 512, 380, 512 + 400 * math.cos(a), 380 + 400 * math.sin(a), w=20, segs=4, seed=i) for i, a in enumerate((0.3, 1.2, 1.9, 2.8))]), BOLT, False),
    "bifrost_arrival": (lambda d: ([d.rectangle([330 + i * 60, 0, 390 + i * 60, 760], fill=c) for i, c in enumerate([(255, 80, 80, 255), (255, 170, 60, 255), (255, 240, 80, 255), (90, 255, 110, 255), (80, 170, 255, 255), (180, 100, 255, 255)])], thor(d, 512, 700, 150)), BOLT, False),
    "stormbreaker": (lambda d: (axe(d, 512, 470, 330), bolt(d, 200, 200, 400, 520, w=24, seed=11), bolt(d, 830, 300, 640, 640, w=24, seed=12)), STORM, False),
    "wrath_of_asgard": (lambda d: (d.ellipse([100, 40, 924, 330], fill=(40, 46, 70, 255), outline=BLACK, width=OL), [bolt(d, 512 + 340 * math.cos(a), 260, 512, 780, w=26, seed=i) for i, a in enumerate((2.6, 2.0, 1.1, 0.5))], crewmate(d, 512, 760, 90, GREY)), BOLT, False),
    "king_of_thunder": (lambda d: (bolt(d, 512, 0, 512, 900, w=70, seed=21), ring(d, 512, 470, 330, BOLT, 26), thor(d, 512, 470, 170)), BOLT, True),
    # Team-ups
    "chaosstorm": (lambda d: (hexagram(d, 512, 560, 320), bolt(d, 300, 80, 560, 640, seed=31), bolt(d, 760, 120, 480, 700, seed=32)), PURPLE, False),
    "reality_thunderclap": (lambda d: (ring(d, 512, 512, 400, SCARLET, 34), ring(d, 512, 512, 300, BOLT, 30), hammer(d, 512, 470, 170, 0.4)), PURPLE, False),
    "end_of_reality": (lambda d: (d.ellipse([360, 300, 664, 720], fill=(15, 0, 30, 255), outline=PURPLE, width=40), [bolt(d, 512 + 420 * math.cos(a), 512 + 420 * math.sin(a), 512, 512, w=20, segs=4, seed=i) for i, a in enumerate((0.2, 1.8, 3.4, 5.0))], [tendril(d, 512 + 420 * math.cos(a), 512 + 420 * math.sin(a), 512, 512, SCARLET, 20) for a in (1.0, 2.6, 4.2, 5.8)]), PURPLE, True),
}

if __name__ == "__main__":
    for name, (fn, glow, ult) in ICONS.items():
        make(name, fn, glow, ult)
    prop("prop_mjolnir", lambda d: hammer(d, 512, 380, 300, 0))
    prop("prop_stormbreaker", lambda d: axe(d, 512, 480, 330, 0))
    print("hero icons:", len(ICONS), "+ 2 props")
