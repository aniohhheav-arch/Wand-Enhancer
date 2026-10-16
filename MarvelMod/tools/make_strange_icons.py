"""Doctor Strange icons (sling-ring orange, cloak red, time green). Run: python3 tools/make_strange_icons.py"""
import math, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_icons import (new, layer, finish, ult_backdrop, crewmate, star, sparkles, ring, dagger,
                        OL, BLACK, WHITE, GOLD, GOLD_L, GREY, alpha)

ORANGE = (255, 150, 40, 255)
ORANGE_L = (255, 215, 140, 255)
CRIMSON = (210, 30, 40, 255)
BLUE = (60, 90, 180, 255)
MIRROR = (170, 220, 255, 255)
TGREEN = (60, 220, 110, 255)
ASTRAL = (255, 235, 160, 255)


def portal(d, cx, cy, r, col=ORANGE):
    for i, rr in enumerate((r, r * 0.8, r * 0.6)):
        d.ellipse([cx - rr, cy - rr * 1.15, cx + rr, cy + rr * 1.15], outline=BLACK, width=OL + 14)
        d.ellipse([cx - rr, cy - rr * 1.15, cx + rr, cy + rr * 1.15], outline=col if i != 1 else ORANGE_L, width=OL - 6)
    for i in range(10):
        a = i * math.pi / 5
        star(d, cx + r * 1.05 * math.cos(a), cy + r * 1.2 * math.sin(a), r * 0.12, ORANGE_L)


def strange(d, cx, cy, s, tint=BLUE):
    d.polygon([(cx - 0.9 * s, cy + 1.0 * s), (cx - 0.7 * s, cy - 0.6 * s), (cx + 0.7 * s, cy - 0.6 * s), (cx + 0.9 * s, cy + 1.0 * s)],
              fill=CRIMSON, outline=BLACK, width=OL)   # cloak
    crewmate(d, cx, cy, s, tint)
    d.ellipse([cx - 0.18 * s, cy - 0.05 * s, cx + 0.18 * s, cy + 0.3 * s], fill=TGREEN, outline=BLACK, width=14)   # Eye of Agamotto


def eye(d, cx, cy, r, col=TGREEN):
    d.ellipse([cx - r * 1.4, cy - r * 0.8, cx + r * 1.4, cy + r * 0.8], fill=GOLD, outline=BLACK, width=OL)
    d.ellipse([cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7], fill=col, outline=BLACK, width=OL)
    d.ellipse([cx - r * 0.25, cy - r * 0.25, cx + r * 0.25, cy + r * 0.25], fill=WHITE)


def clock(d, cx, cy, r, col=TGREEN):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=WHITE, outline=BLACK, width=OL)
    d.ellipse([cx - r * 0.82, cy - r * 0.82, cx + r * 0.82, cy + r * 0.82], outline=col, width=22)
    d.line([(cx, cy), (cx, cy - r * 0.6)], fill=BLACK, width=26)
    d.line([(cx, cy), (cx + r * 0.45, cy + r * 0.1)], fill=BLACK, width=26)


def make(name, draw, glow=ORANGE, ult=False):
    img = ult_backdrop(new()) if ult else new()
    img = layer(img, lambda d, _: draw(d))
    finish(img, name, glow_col=glow, ult=ult)


ICONS = {
    # sling ring
    "sling_ring": (lambda d: (portal(d, 512, 512, 300), sparkles(d, [(820, 200, 70)])), ORANGE, False),
    "portal_link": (lambda d: (portal(d, 300, 512, 170), portal(d, 724, 512, 170), d.line([(420, 512), (604, 512)], fill=ORANGE_L, width=40)), ORANGE, False),
    "portal_redirect": (lambda d: (portal(d, 650, 512, 220), crewmate(d, 300, 560, 160, GREY), d.polygon([(450, 470), (540, 512), (450, 554)], fill=ORANGE_L, outline=BLACK, width=16)), ORANGE, False),
    "portal_snare": (lambda d: (portal(d, 512, 650, 260), d.polygon([(380, 330), (512, 160), (644, 330)], fill=CRIMSON, outline=BLACK, width=OL), d.rectangle([470, 330, 554, 470], fill=CRIMSON, outline=BLACK, width=OL)), ORANGE, False),
    "multiversal_gateway": (lambda d: [portal(d, 512 + 260 * math.cos(a), 512 + 260 * math.sin(a), 110) for a in (0, 2.1, 4.2)] + [portal(d, 512, 512, 140)], ORANGE, True),
    # combat
    "crimson_bands": (lambda d: (crewmate(d, 512, 540, 220, GREY), [d.ellipse([272, y - 60, 752, y + 60], outline=CRIMSON, width=44) for y in (380, 540, 700)]), CRIMSON, False),
    "eldritch_whip": (lambda d: (d.line([(200, 800), (420, 500), (650, 420), (860, 220)], fill=BLACK, width=70), d.line([(200, 800), (420, 500), (650, 420), (860, 220)], fill=ORANGE, width=36), sparkles(d, [(860, 220, 90)])), ORANGE, False),
    "eldritch_shield": (lambda d: (d.ellipse([170, 170, 854, 854], fill=alpha(ORANGE, 120), outline=BLACK, width=OL), ring(d, 512, 512, 300, ORANGE_L, 30), ring(d, 512, 512, 200, ORANGE, 24), star(d, 512, 512, 110, ORANGE_L, outline=BLACK, ow=18)), ORANGE, False),
    "mystic_blades": (lambda d: [dagger(d, 512 + 230 * math.cos(a), 512 + 230 * math.sin(a), 300, a + math.pi / 2, blade=ORANGE_L, ow=20) for a in [i * math.pi / 2 for i in range(4)]], ORANGE, False),
    "crimson_judgment": (lambda d: (ring(d, 512, 600, 330, CRIMSON, 40), crewmate(d, 512, 470, 170, GREY), [d.ellipse([322, y - 40, 702, y + 40], outline=CRIMSON, width=30) for y in (420, 560)]), CRIMSON, True),
    # mirror
    "mirror_shard": (lambda d: [d.polygon([(512 + 280 * math.cos(a), 512 + 280 * math.sin(a)), (512 + 120 * math.cos(a + 0.3), 512 + 120 * math.sin(a + 0.3)), (512 + 140 * math.cos(a - 0.4), 512 + 140 * math.sin(a - 0.4))], fill=MIRROR, outline=BLACK, width=18) for a in [i * 1.05 for i in range(6)]], MIRROR, False),
    "fractured_reality": (lambda d: (d.rectangle([200, 200, 824, 824], fill=MIRROR, outline=BLACK, width=OL), [d.line([(512, 512), (512 + 340 * math.cos(a), 512 + 340 * math.sin(a))], fill=BLACK, width=22) for a in [i * 0.9 for i in range(7)]]), MIRROR, False),
    "mirror_clone": (lambda d: (strange(d, 360, 540, 170), strange(d, 680, 540, 170, MIRROR)), MIRROR, False),
    "dimensional_prison": (lambda d: (d.rectangle([250, 220, 774, 820], outline=BLACK, width=OL + 30), d.rectangle([250, 220, 774, 820], outline=MIRROR, width=40), crewmate(d, 512, 560, 170, GREY)), MIRROR, False),
    "mirror_world_collapse": (lambda d: ([d.polygon([(512, 512), (512 + 420 * math.cos(a), 512 + 420 * math.sin(a)), (512 + 380 * math.cos(a + 0.45), 512 + 380 * math.sin(a + 0.45))], fill=MIRROR, outline=BLACK, width=16) for a in [i * 0.9 for i in range(7)]], strange(d, 512, 560, 140)), MIRROR, True),
    # time stone
    "temporal_rewind": (lambda d: (clock(d, 512, 512, 260), d.arc([180, 180, 844, 844], 200, 340, fill=TGREEN, width=50)), TGREEN, False),
    "time_bubble": (lambda d: (d.ellipse([150, 150, 874, 874], fill=alpha(TGREEN, 110), outline=BLACK, width=OL), clock(d, 512, 512, 170), crewmate(d, 300, 700, 90, GREY)), TGREEN, False),
    "strange_echo": (lambda d: (strange(d, 330, 560, 150, alpha(TGREEN, 255)), strange(d, 680, 560, 170)), TGREEN, False),
    "time_loop": (lambda d: ([d.arc([180 + i * 70, 180 + i * 70, 844 - i * 70, 844 - i * 70], i * 90, i * 90 + 300, fill=TGREEN, width=40) for i in range(3)], crewmate(d, 512, 540, 120, GREY)), TGREEN, False),
    "eye_of_agamotto": (lambda d: (ring(d, 512, 512, 380, TGREEN, 30), eye(d, 512, 512, 200)), TGREEN, True),
    # astral
    "astral_projection": (lambda d: (strange(d, 360, 600, 170), crewmate(d, 650, 430, 200, alpha(ASTRAL, 255))), ASTRAL, False),
    "astral_sight": (lambda d: (eye(d, 512, 512, 230, ASTRAL), crewmate(d, 220, 820, 70, GREY), crewmate(d, 820, 820, 70, GREY)), ASTRAL, False),
    "astral_bind": (lambda d: (crewmate(d, 512, 540, 220, GREY), [d.ellipse([200 + i * 110 - 50, 420, 200 + i * 110 + 50, 480], outline=ASTRAL, width=24) for i in range(6)]), ASTRAL, False),
    "dimension_step": (lambda d: (portal(d, 280, 560, 130), portal(d, 740, 460, 130), strange(d, 740, 470, 110)), ORANGE, False),
    "astral_exile": (lambda d: (d.ellipse([200, 200, 824, 824], fill=(20, 15, 40, 255), outline=BLACK, width=OL), [star(d, x, y, 30, WHITE) for x, y in ((330, 330), (680, 300), (600, 700), (300, 650))], crewmate(d, 512, 520, 150, alpha(ASTRAL, 255))), ASTRAL, True),
    # finishers
    "portal_maze": (lambda d: [portal(d, 260 + i * 250, 300 + (i % 2) * 400, 130) for i in range(3)], ORANGE, False),
    "mirror_dimension_finisher": (lambda d: ([strange(d, 512 + 300 * math.cos(a), 512 + 260 * math.sin(a), 80, MIRROR) for a in [i * 1.05 for i in range(6)]], crewmate(d, 512, 540, 130, GREY)), MIRROR, False),
    "crimson_bands_finisher": (lambda d: (ring(d, 512, 620, 300, CRIMSON, 34), crewmate(d, 512, 470, 150, GREY), d.rectangle([420, 380, 604, 560], outline=GOLD, width=26)), CRIMSON, False),
    "temporal_fracture": (lambda d: ([crewmate(d, 512 + 260 * math.cos(a), 540 + 220 * math.sin(a), 90, alpha(TGREEN, 255)) for a in (0.5, 2.6, 4.7)], eye(d, 512, 512, 110)), TGREEN, False),
    "sorcerer_supreme": (lambda d: ([portal(d, 512 + 330 * math.cos(a), 420 + 250 * math.sin(a), 70) for a in [math.pi * (1.1 + i * 0.2) for i in range(5)]], strange(d, 512, 620, 200)), GOLD, True),
}

if __name__ == "__main__":
    for name, (fn, glow, ult) in ICONS.items():
        make(name, fn, glow, ult)
    print("strange icons:", len(ICONS))
