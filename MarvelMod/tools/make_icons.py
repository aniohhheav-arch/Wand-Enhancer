"""Draws the MarvelMod ability button icons (flat, thick-outlined Among Us style).

Run:  python3 tools/make_icons.py      (from MarvelMod/)
Output: Resources/Icons/<ability_id>.png  (256x256, transparent)
The PNGs are embedded into MarvelMod.dll by MarvelMod.csproj.
"""
import math
import os
from PIL import Image, ImageDraw, ImageFilter

S = 1024            # supersampled canvas, downscaled to OUT for anti-aliasing
OUT = 256
OL = 30             # outline width
BLACK = (12, 14, 18, 255)
GREEN = (38, 168, 74, 255)
GREEN_D = (16, 92, 40, 255)
GREEN_L = (126, 236, 140, 255)
GOLD = (236, 186, 40, 255)
GOLD_L = (255, 228, 120, 255)
GOLD_D = (168, 112, 16, 255)
WHITE = (245, 248, 250, 255)
RED = (206, 40, 46, 255)
RED_D = (124, 18, 26, 255)
VISOR = (150, 210, 230, 255)
VISOR_L = (220, 245, 255, 255)
PURPLE = (120, 70, 190, 255)
GREY = (70, 76, 88, 255)

HERE = os.path.dirname(os.path.abspath(__file__))
OUT_DIR = os.path.join(HERE, "..", "Resources", "Icons")


def alpha(c, a):
    return (c[0], c[1], c[2], a)


def new():
    return Image.new("RGBA", (S, S), (0, 0, 0, 0))


def glow(img, color, radius=60, strength=1.0):
    """Soft colored glow behind everything already drawn."""
    mask = img.split()[3].filter(ImageFilter.GaussianBlur(radius))
    if strength != 1.0:
        mask = mask.point(lambda v: min(255, int(v * strength)))
    g = Image.new("RGBA", img.size, color)
    g.putalpha(mask)
    return Image.alpha_composite(g, img)


def layer(base, fn, opacity=255):
    """Draw fn onto a separate layer and composite with opacity."""
    lay = new()
    fn(ImageDraw.Draw(lay), lay)
    if opacity < 255:
        a = lay.split()[3].point(lambda v: v * opacity // 255)
        lay.putalpha(a)
    return Image.alpha_composite(base, lay)


# ── primitives ────────────────────────────────────────────────────────────────

def crewmate(d, cx, cy, s, body, shade=None, visor=VISOR, flip=False, outline=BLACK, ow=OL):
    """Among Us crewmate silhouette centered at (cx, cy), height ~ 2*s."""
    shade = shade or tuple(max(0, int(v * 0.6)) for v in body[:3]) + (body[3],)
    f = -1 if flip else 1
    # backpack
    bx0, bx1 = sorted([cx - f * 0.95 * s, cx - f * 0.55 * s])
    d.rounded_rectangle([bx0, cy - 0.35 * s, bx1, cy + 0.55 * s], radius=0.18 * s, fill=shade, outline=outline, width=ow)
    # legs
    for lx in (-0.45, 0.2):
        x0, x1 = sorted([cx + f * lx * s, cx + f * (lx + 0.32) * s])
        d.rounded_rectangle([x0, cy + 0.55 * s, x1, cy + 1.0 * s], radius=0.12 * s, fill=shade, outline=outline, width=ow)
    # body
    d.rounded_rectangle([cx - 0.6 * s, cy - 0.95 * s, cx + 0.6 * s, cy + 0.85 * s], radius=0.55 * s, fill=body, outline=outline, width=ow)
    # visor
    vx0, vx1 = sorted([cx - f * 0.05 * s, cx + f * 0.78 * s])
    d.rounded_rectangle([vx0, cy - 0.6 * s, vx1, cy - 0.1 * s], radius=0.25 * s, fill=visor, outline=outline, width=ow)
    hx0, hx1 = sorted([cx + f * 0.25 * s, cx + f * 0.6 * s])
    d.rounded_rectangle([hx0, cy - 0.5 * s, hx1, cy - 0.36 * s], radius=0.07 * s, fill=VISOR_L)


def star(d, cx, cy, r, fill, points=4, inner=0.35, rot=0.0, outline=None, ow=0):
    pts = []
    for i in range(points * 2):
        a = rot + math.pi * i / points
        rr = r if i % 2 == 0 else r * inner
        pts.append((cx + rr * math.sin(a), cy - rr * math.cos(a)))
    d.polygon(pts, fill=fill, outline=outline, width=ow)


def sparkles(d, pts, color=GOLD_L, outline=BLACK):
    for (x, y, r) in pts:
        star(d, x, y, r, color, outline=outline, ow=max(8, int(r * 0.18)))


def rot_pts(pts, cx, cy, ang):
    ca, sa = math.cos(ang), math.sin(ang)
    return [(cx + (x - cx) * ca - (y - cy) * sa, cy + (x - cx) * sa + (y - cy) * ca) for x, y in pts]


def dagger(d, cx, cy, length, ang, blade=WHITE, glow_col=None, ow=OL):
    """Dagger pointing up before rotation by ang (radians)."""
    L = length
    w = L * 0.11
    blade_pts = [(cx, cy - L * 0.55), (cx + w, cy - L * 0.3), (cx + w, cy + L * 0.12),
                 (cx - w, cy + L * 0.12), (cx - w, cy - L * 0.3)]
    guard = [(cx - L * 0.24, cy + L * 0.1), (cx + L * 0.24, cy + L * 0.1),
             (cx + L * 0.24, cy + L * 0.2), (cx - L * 0.24, cy + L * 0.2)]
    grip = [(cx - w * 0.8, cy + L * 0.2), (cx + w * 0.8, cy + L * 0.2),
            (cx + w * 0.8, cy + L * 0.42), (cx - w * 0.8, cy + L * 0.42)]
    d.polygon(rot_pts(blade_pts, cx, cy, ang), fill=blade, outline=BLACK, width=ow)
    edge = [(cx, cy - L * 0.5), (cx + w * 0.35, cy - L * 0.3), (cx + w * 0.35, cy + L * 0.08), (cx, cy + L * 0.08)]
    d.polygon(rot_pts(edge, cx, cy, ang), fill=glow_col or (200, 210, 220, 255))
    d.polygon(rot_pts(guard, cx, cy, ang), fill=GOLD, outline=BLACK, width=ow)
    d.polygon(rot_pts(grip, cx, cy, ang), fill=GREEN_D, outline=BLACK, width=ow)
    px, py = rot_pts([(cx, cy + L * 0.47)], cx, cy, ang)[0]
    d.ellipse([px - w * 1.1, py - w * 1.1, px + w * 1.1, py + w * 1.1], fill=GOLD, outline=BLACK, width=ow)


def horns(d, cx, cy, s, fill=GOLD, shade=GOLD_D):
    """Loki's horned helmet."""
    # helmet dome
    d.chord([cx - 0.62 * s, cy - 0.55 * s, cx + 0.62 * s, cy + 0.75 * s], 180, 360, fill=fill, outline=BLACK, width=OL)
    d.rectangle([cx - 0.62 * s, cy + 0.08 * s, cx + 0.62 * s, cy + 0.32 * s], fill=fill, outline=BLACK, width=OL)
    # face opening
    d.polygon([(cx - 0.36 * s, cy + 0.32 * s), (cx - 0.25 * s, cy - 0.05 * s), (cx + 0.25 * s, cy - 0.05 * s),
               (cx + 0.36 * s, cy + 0.32 * s), (cx + 0.36 * s, cy + 0.75 * s), (cx - 0.36 * s, cy + 0.75 * s)],
              fill=GREEN_D, outline=BLACK, width=OL)
    # cheek guards
    for f in (-1, 1):
        d.polygon([(cx + f * 0.62 * s, cy + 0.3 * s), (cx + f * 0.36 * s, cy + 0.3 * s),
                   (cx + f * 0.36 * s, cy + 0.85 * s), (cx + f * 0.5 * s, cy + 0.85 * s)],
                  fill=fill, outline=BLACK, width=OL)
    # horns sweeping up and back
    for f in (-1, 1):
        pts = [(cx + f * 0.3 * s, cy - 0.12 * s), (cx + f * 0.55 * s, cy - 0.5 * s),
               (cx + f * 0.72 * s, cy - 0.95 * s), (cx + f * 0.95 * s, cy - 1.25 * s),
               (cx + f * 0.82 * s, cy - 0.75 * s), (cx + f * 0.68 * s, cy - 0.2 * s),
               (cx + f * 0.55 * s, cy + 0.05 * s)]
        d.polygon(pts, fill=fill, outline=BLACK, width=OL)
        d.line([(cx + f * 0.48 * s, cy - 0.2 * s), (cx + f * 0.78 * s, cy - 0.85 * s)], fill=GOLD_L, width=OL)
    d.arc([cx - 0.5 * s, cy - 0.42 * s, cx + 0.5 * s, cy + 0.6 * s], 200, 260, fill=GOLD_L, width=OL)


def ring(d, cx, cy, r, color, w):
    d.ellipse([cx - r, cy - r, cx + r, cy + r], outline=color, width=w)


def ult_backdrop(img):
    """Gold burst behind ultimate abilities."""
    def fn(d, _):
        for i in range(12):
            a = i * math.pi / 6
            p = [(512, 512), (512 + 480 * math.cos(a - 0.12), 512 + 480 * math.sin(a - 0.12)),
                 (512 + 480 * math.cos(a + 0.12), 512 + 480 * math.sin(a + 0.12))]
            d.polygon(p, fill=alpha(GOLD, 120))
        ring(d, 512, 512, 440, alpha(GOLD_L, 200), 22)
    return layer(img, fn)


def finish(img, name, glow_col=GREEN, ult=False):
    img = glow(img, alpha(glow_col, 255), radius=46, strength=1.4)
    out = img.resize((OUT, OUT), Image.LANCZOS)
    os.makedirs(OUT_DIR, exist_ok=True)
    out.save(os.path.join(OUT_DIR, name + ".png"))


# ── icons ────────────────────────────────────────────────────────────────────

def mirror_image():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 400, 480, 260, GREEN_L, flip=True), 150)
    img = layer(img, lambda d, _: crewmate(d, 600, 540, 280, GREEN))
    img = layer(img, lambda d, _: sparkles(d, [(250, 220, 90), (820, 230, 70)]))
    finish(img, "mirror_image")


def phantom_squad():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 270, 470, 190, GREEN_L), 140)
    img = layer(img, lambda d, _: crewmate(d, 760, 470, 190, GREEN_L, flip=True), 140)
    img = layer(img, lambda d, _: crewmate(d, 512, 560, 240, GREEN))
    img = layer(img, lambda d, _: sparkles(d, [(512, 170, 90)]))
    finish(img, "phantom_squad")


def false_emergency():
    img = new()
    def fn(d, _):
        d.polygon([(512, 120), (900, 820), (124, 820)], fill=RED, outline=BLACK, width=OL + 8)
        d.polygon([(512, 230), (800, 760), (224, 760)], fill=(250, 80, 70, 255))
        d.rounded_rectangle([462, 330, 562, 620], radius=40, fill=WHITE, outline=BLACK, width=OL)
        d.ellipse([457, 655, 567, 765], fill=WHITE, outline=BLACK, width=OL)
        sparkles(d, [(840, 240, 90), (170, 330, 70)], color=GREEN_L)
    img = layer(img, fn)
    finish(img, "false_emergency", glow_col=RED)


def illusion_trap():
    img = new()
    def fn(d, _):
        for i, r in enumerate([400, 320, 240, 160]):
            col = GREEN if i % 2 == 0 else GREEN_D
            d.ellipse([512 - r, 512 - r * 0.62, 512 + r, 512 + r * 0.62], fill=col, outline=BLACK, width=OL)
        # eye in the middle
        d.ellipse([400, 450, 624, 574], fill=GOLD_L, outline=BLACK, width=OL)
        d.ellipse([472, 472, 552, 552], fill=BLACK)
        d.ellipse([490, 480, 515, 505], fill=WHITE)
        sparkles(d, [(180, 200, 80), (850, 820, 80)])
    img = layer(img, fn)
    finish(img, "illusion_trap")


def hall_of_loki():
    img = ult_backdrop(new())
    img = layer(img, lambda d, _: [crewmate(d, x, 680, 120, GREEN_L) for x in (220, 805)], 170)
    img = layer(img, lambda d, _: horns(d, 512, 470, 330))
    finish(img, "hall_of_loki", glow_col=GOLD, ult=True)


def disguise():
    img = new()
    def fn(d, _):
        # theater mask
        d.chord([212, 200, 812, 880], 0, 360, fill=GREEN, outline=BLACK, width=OL + 6)
        d.rectangle([200, 180, 830, 420], fill=(0, 0, 0, 0))
        d.pieslice([212, 140, 812, 560], 180, 360, fill=GREEN, outline=BLACK, width=OL + 6)
        d.chord([212, 200, 812, 880], 0, 180, fill=GREEN, outline=BLACK, width=OL + 6)
        d.rectangle([228, 340, 796, 560], fill=GREEN)
        d.line([(212, 350), (212, 560)], fill=BLACK, width=OL + 6)
        d.line([(812, 350), (812, 560)], fill=BLACK, width=OL + 6)
        # eyes
        for x in (370, 654):
            d.chord([x - 95, 380, x + 95, 520], 180, 360, fill=BLACK)
        # smile
        d.arc([360, 560, 664, 760], 20, 160, fill=BLACK, width=OL + 10)
        # gold trim
        d.arc([240, 170, 784, 520], 200, 340, fill=GOLD, width=OL)
    img = layer(img, fn)
    finish(img, "disguise")


def perfect_impersonation():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 512, 560, 300, RED, flip=False))
    def fn(d, _):
        # green mask held over the face
        d.rounded_rectangle([510, 300, 900, 520], radius=100, fill=GREEN, outline=BLACK, width=OL)
        d.chord([590, 360, 690, 440], 180, 360, fill=BLACK)
        d.chord([740, 360, 840, 440], 180, 360, fill=BLACK)
        d.line([(520, 410), (380, 380)], fill=GOLD, width=OL)
        sparkles(d, [(200, 200, 90)])
    img = layer(img, fn)
    finish(img, "perfect_impersonation")


def creature_form():
    img = new()
    def fn(d, _):
        # coiled snake (Loki's favourite)
        d.ellipse([170, 560, 860, 900], fill=GREEN, outline=BLACK, width=OL)
        d.ellipse([290, 620, 740, 830], fill=GREEN_D, outline=BLACK, width=OL)
        d.ellipse([280, 430, 760, 720], fill=GREEN, outline=BLACK, width=OL)
        d.ellipse([400, 500, 640, 640], fill=GREEN_D, outline=BLACK, width=OL)
        # neck + head
        d.polygon([(560, 520), (650, 300), (740, 300), (680, 560)], fill=GREEN, outline=BLACK, width=OL)
        d.ellipse([560, 170, 860, 360], fill=GREEN, outline=BLACK, width=OL)
        d.ellipse([740, 220, 790, 270], fill=GOLD_L, outline=BLACK, width=14)
        d.line([(860, 300), (930, 320), (960, 290)], fill=RED, width=20)
        d.line([(930, 320), (960, 350)], fill=RED, width=20)
        # tiny horns
        d.polygon([(620, 200), (590, 110), (660, 185)], fill=GOLD, outline=BLACK, width=16)
        d.polygon([(700, 180), (700, 90), (740, 175)], fill=GOLD, outline=BLACK, width=16)
    img = layer(img, fn)
    finish(img, "creature_form")


def swap_arrows(d, cx, cy, r, c1=GREEN, c2=GOLD):
    for (start, col) in ((200, c1), (20, c2)):
        d.arc([cx - r, cy - r, cx + r, cy + r], start, start + 130, fill=BLACK, width=OL + 70)
        d.arc([cx - r, cy - r, cx + r, cy + r], start, start + 130, fill=col, width=70)
        a = math.radians(start + 130)
        tx, ty = cx + r * math.cos(a), cy + r * math.sin(a)
        da = a + math.pi / 2
        head = [(tx + 80 * math.cos(da), ty + 80 * math.sin(da)),
                (tx + 70 * math.cos(a), ty + 70 * math.sin(a)),
                (tx - 70 * math.cos(a), ty - 70 * math.sin(a))]
        d.polygon(head, fill=col, outline=BLACK, width=OL)


def identity_swap():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 330, 560, 190, GREEN))
    img = layer(img, lambda d, _: crewmate(d, 700, 560, 190, RED, flip=True))
    img = layer(img, lambda d, _: swap_arrows(d, 512, 520, 400))
    finish(img, "identity_swap")


def the_other_you():
    img = ult_backdrop(new())
    img = layer(img, lambda d, _: crewmate(d, 360, 560, 230, RED))
    img = layer(img, lambda d, _: crewmate(d, 680, 560, 230, GREEN, flip=True), 190)
    img = layer(img, lambda d, _: horns(d, 680, 290, 140))
    finish(img, "the_other_you", glow_col=GOLD, ult=True)


def mystic_blink():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 270, 560, 170, GREEN_L), 90)
    def fn(d, _):
        for i, x in enumerate((420, 520, 620)):
            d.ellipse([x - 30 + i * 6, 520, x + 30 + i * 6, 580], fill=GREEN_L, outline=BLACK, width=12)
    img = layer(img, fn)
    img = layer(img, lambda d, _: crewmate(d, 770, 540, 190, GREEN))
    img = layer(img, lambda d, _: sparkles(d, [(760, 220, 90), (900, 380, 60), (620, 300, 55)]))
    finish(img, "mystic_blink")


def swap_trick():
    img = new()
    def fn(d, _):
        swap_arrows(d, 512, 512, 330)
        star(d, 512, 512, 150, GOLD_L, outline=BLACK, ow=OL)
    img = layer(img, fn)
    finish(img, "swap_trick")


def decoy_escape():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 330, 560, 220, GREEN_L), 120)
    def fn(d, _):
        for y in (430, 540, 650):
            d.line([(520, y), (640, y)], fill=BLACK, width=40)
            d.line([(530, y), (630, y)], fill=GREEN_L, width=16)
    img = layer(img, fn)
    img = layer(img, lambda d, _: crewmate(d, 770, 520, 200, GREEN))
    finish(img, "decoy_escape")


def footprint(d, cx, cy, s, ang, col):
    pts = [(cx - 0.35 * s, cy - 0.2 * s), (cx + 0.35 * s, cy - 0.2 * s), (cx + 0.3 * s, cy + 0.55 * s), (cx - 0.3 * s, cy + 0.55 * s)]
    d.ellipse([cx - 0.38 * s, cy - 0.75 * s, cx + 0.38 * s, cy + 0.15 * s], fill=col, outline=BLACK, width=OL)
    d.ellipse([cx - 0.28 * s, cy + 0.3 * s, cx + 0.28 * s, cy + 0.8 * s], fill=col, outline=BLACK, width=OL)


def shadow_step():
    img = new()
    img = layer(img, lambda d, _: footprint(d, 300, 760, 190, 0, GREEN_L), 110)
    img = layer(img, lambda d, _: footprint(d, 520, 540, 190, 0, GREEN_L), 190)
    img = layer(img, lambda d, _: footprint(d, 740, 320, 190, 0, GREEN))
    img = layer(img, lambda d, _: sparkles(d, [(880, 140, 70)]))
    finish(img, "shadow_step")


def master_of_misdirection():
    img = ult_backdrop(new())
    def fn(d, _):
        for (x, y, r) in ((300, 330, 150), (720, 330, 150), (512, 700, 190)):
            for i, rr in enumerate((r, r * 0.68, r * 0.36)):
                d.ellipse([x - rr, y - rr * 0.7, x + rr, y + rr * 0.7], fill=GREEN if i % 2 == 0 else GREEN_D, outline=BLACK, width=OL)
        sparkles(d, [(512, 420, 100)])
    img = layer(img, fn)
    finish(img, "master_of_misdirection", glow_col=GOLD, ult=True)


def green_bolt():
    img = new()
    def fn(d, _):
        d.ellipse([250, 250, 774, 774], fill=GREEN, outline=BLACK, width=OL + 6)
        d.ellipse([330, 310, 700, 680], fill=GREEN_L)
        bolt = [(560, 170), (380, 540), (500, 540), (440, 860), (680, 440), (550, 440), (640, 170)]
        d.polygon(bolt, fill=GOLD_L, outline=BLACK, width=OL)
    img = layer(img, fn)
    finish(img, "green_bolt", glow_col=GREEN_L)


def binding_ribbons():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 512, 540, 250, GREY))
    def fn(d, _):
        for y, tilt in ((360, -60), (520, 50), (680, -40)):
            d.line([(170, y - tilt), (854, y + tilt)], fill=BLACK, width=110)
            d.line([(170, y - tilt), (854, y + tilt)], fill=GOLD, width=66)
            d.line([(180, y - tilt - 12), (844, y + tilt - 12)], fill=GOLD_L, width=16)
    img = layer(img, fn)
    finish(img, "binding_ribbons", glow_col=GOLD)


def mystic_shield():
    img = new()
    def fn(d, _):
        pts = [(512, 130), (850, 240), (820, 560), (512, 900), (204, 560), (174, 240)]
        d.polygon(pts, fill=GREEN, outline=BLACK, width=OL + 8)
        inner = [(512, 220), (760, 300), (738, 540), (512, 800), (286, 540), (264, 300)]
        d.polygon(inner, fill=GREEN_D)
        d.line([(512, 260), (512, 760)], fill=GOLD, width=50)
        d.line([(340, 430), (684, 430)], fill=GOLD, width=50)
        star(d, 512, 430, 110, GOLD_L, outline=BLACK, ow=18)
    img = layer(img, fn)
    finish(img, "mystic_shield", glow_col=GREEN_L)


def enchanted_dagger():
    img = new()
    img = layer(img, lambda d, _: dagger(d, 512, 512, 820, math.radians(35), blade=GREEN_L, glow_col=WHITE))
    img = layer(img, lambda d, _: sparkles(d, [(250, 260, 80), (800, 760, 70)]))
    finish(img, "enchanted_dagger", glow_col=GREEN_L)


def asgardian_wrath():
    img = ult_backdrop(new())
    def fn(d, _):
        for i in range(6):
            a = i * math.pi / 3 + math.pi / 6
            x, y = 512 + 250 * math.cos(a), 512 + 250 * math.sin(a)
            dagger(d, x, y, 360, a + math.pi / 2, blade=GREEN_L, glow_col=WHITE, ow=22)
        star(d, 512, 512, 140, GOLD_L, outline=BLACK, ow=OL)
    img = layer(img, fn)
    finish(img, "asgardian_wrath", glow_col=GOLD, ult=True)


def fake_body():
    img = new()
    def fn(d, _):
        # Among Us dead body: bottom half lying on the floor, bone sticking out
        d.ellipse([140, 700, 900, 860], fill=(150, 20, 30, 200))                                     # blood pool
        d.rounded_rectangle([170, 520, 360, 800], radius=60, fill=GREEN_D, outline=BLACK, width=OL)  # backpack
        for lx in (380, 600):                                                                       # legs
            d.rounded_rectangle([lx, 700, lx + 170, 860], radius=50, fill=GREEN_D, outline=BLACK, width=OL)
        d.rounded_rectangle([280, 470, 820, 800], radius=150, fill=GREEN, outline=BLACK, width=OL)
        d.rectangle([280 + OL, 470 - 4, 820 - OL, 560], fill=GREEN)
        d.ellipse([290, 400, 810, 540], fill=RED_D, outline=BLACK, width=OL)                         # cut
        d.ellipse([350, 425, 750, 515], fill=RED)
        d.rounded_rectangle([505, 200, 595, 470], radius=26, fill=WHITE, outline=BLACK, width=OL)    # bone
        d.ellipse([430, 120, 560, 250], fill=WHITE, outline=BLACK, width=OL)
        d.ellipse([540, 120, 670, 250], fill=WHITE, outline=BLACK, width=OL)
        d.rectangle([480, 190, 620, 250], fill=WHITE)
        sparkles(d, [(840, 220, 110), (170, 300, 70)])
    img = layer(img, fn)
    finish(img, "fake_body")


def mind_whisper():
    img = new()
    def fn(d, _):
        d.rounded_rectangle([150, 200, 874, 700], radius=180, fill=WHITE, outline=BLACK, width=OL + 6)
        d.polygon([(330, 660), (250, 880), (480, 690)], fill=WHITE, outline=BLACK, width=OL + 6)
        d.rectangle([310, 640, 500, 690], fill=WHITE)
        for x in (340, 512, 684):
            d.ellipse([x - 55, 395, x + 55, 505], fill=GREEN, outline=BLACK, width=OL - 6)
        horns_small = [(820, 230), (900, 90), (880, 260)]
        d.polygon(horns_small, fill=GOLD, outline=BLACK, width=20)
    img = layer(img, fn)
    finish(img, "mind_whisper")


def enchanted_witness():
    img = new()
    def fn(d, _):
        d.ellipse([120, 300, 904, 724], fill=WHITE, outline=BLACK, width=OL + 8)
        d.ellipse([340, 340, 684, 684], fill=GREEN, outline=BLACK, width=OL)
        d.ellipse([430, 430, 594, 594], fill=BLACK)
        d.ellipse([460, 440, 520, 500], fill=WHITE)
        for i in range(5):
            a = math.radians(200 + i * 35)
            d.line([(512 + 440 * math.cos(a), 512 + 300 * math.sin(a)), (512 + 520 * math.cos(a), 512 + 380 * math.sin(a))], fill=BLACK, width=OL)
        sparkles(d, [(840, 780, 90)])
    img = layer(img, fn)
    finish(img, "enchanted_witness")


def chaos_contract():
    img = new()
    def fn(d, _):
        d.rounded_rectangle([240, 140, 784, 860], radius=40, fill=(240, 222, 170, 255), outline=BLACK, width=OL)
        d.ellipse([190, 100, 330, 200], fill=(220, 196, 140, 255), outline=BLACK, width=OL)
        d.ellipse([694, 800, 834, 900], fill=(220, 196, 140, 255), outline=BLACK, width=OL)
        for y in (260, 340, 420, 500):
            d.line([(320, y), (700, y)], fill=(120, 100, 70, 255), width=22)
        d.ellipse([480, 560, 720, 800], fill=GREEN, outline=BLACK, width=OL)
        horns(d, 600, 690, 80)
    img = layer(img, fn)
    finish(img, "chaos_contract")


def the_grand_deception():
    img = ult_backdrop(new())
    img = layer(img, lambda d, _: horns(d, 512, 560, 380))
    img = layer(img, lambda d, _: sparkles(d, [(200, 200, 80), (830, 220, 80)]))
    finish(img, "the_grand_deception", glow_col=GOLD, ult=True)


def switch_tab():
    img = new()
    img = layer(img, lambda d, _: swap_arrows(d, 512, 512, 350, GREEN, GREEN))
    img = layer(img, lambda d, _: horns(d, 512, 560, 210))
    finish(img, "switch_tab")


BLOOD = (170, 10, 20, 255)
BLUE = (80, 170, 255, 255)
BLUE_L = (190, 230, 255, 255)


def blood_drops(d, pts):
    for (x, y, r) in pts:
        d.ellipse([x - r, y - r, x + r, y + r], fill=BLOOD, outline=BLACK, width=max(8, int(r * 0.3)))
        d.polygon([(x - r * 0.75, y - r * 0.4), (x, y - r * 2.1), (x + r * 0.75, y - r * 0.4)], fill=BLOOD)


def backstab():
    img = new()
    img = layer(img, lambda d, _: crewmate(d, 420, 560, 260, GREY, flip=True))
    img = layer(img, lambda d, _: dagger(d, 660, 430, 640, math.radians(-40), blade=GREEN_L, glow_col=WHITE))
    img = layer(img, lambda d, _: blood_drops(d, [(560, 600, 44), (640, 760, 34), (480, 820, 28)]))
    finish(img, "backstab", glow_col=RED)


def scepter_blast():
    img = new()
    def fn(d, _):
        d.line([(250, 860), (600, 380)], fill=BLACK, width=110)
        d.line([(250, 860), (600, 380)], fill=GOLD, width=66)
        d.polygon([(600, 380), (520, 250), (640, 130), (760, 250), (680, 380)], fill=GOLD, outline=BLACK, width=OL)
        d.ellipse([560, 200, 720, 360], fill=BLUE, outline=BLACK, width=OL)
        d.ellipse([600, 230, 660, 290], fill=BLUE_L)
        for i in range(5):
            a = math.radians(-60 + i * 25)
            d.line([(640 + 180 * math.cos(a), 280 + 180 * math.sin(a)), (640 + 330 * math.cos(a), 280 + 330 * math.sin(a))], fill=BLUE_L, width=26)
    img = layer(img, fn)
    finish(img, "scepter_blast", glow_col=BLUE)


def serpent_strike():
    img = new()
    def fn(d, _):
        d.ellipse([170, 300, 830, 760], fill=GREEN, outline=BLACK, width=OL + 6)       # head
        d.chord([210, 520, 790, 900], 0, 180, fill=GREEN_D, outline=BLACK, width=OL)   # jaw
        d.chord([250, 560, 750, 760], 0, 180, fill=RED_D)                              # mouth
        for x in (350, 650):                                                           # fangs
            d.polygon([(x - 45, 580), (x + 45, 580), (x, 780)], fill=WHITE, outline=BLACK, width=18)
        for x in (330, 690):                                                           # eyes
            d.ellipse([x - 70, 380, x + 70, 480], fill=GOLD_L, outline=BLACK, width=18)
            d.ellipse([x - 14, 390, x + 14, 470], fill=BLACK)
        blood_drops(d, [(650, 870, 34)])
    img = layer(img, fn)
    finish(img, "serpent_strike", glow_col=GREEN_L)


def illusion_ambush():
    img = new()
    img = layer(img, lambda d, _: [crewmate(d, x, y, 140, GREEN_L, flip=f) for x, y, f in ((230, 360, False), (800, 360, True), (512, 830, False))], 170)
    img = layer(img, lambda d, _: crewmate(d, 512, 470, 170, GREY))
    img = layer(img, lambda d, _: [dagger(d, x, y, 300, a, blade=GREEN_L, ow=20) for x, y, a in ((360, 420, math.radians(70)), (670, 420, math.radians(-70)), (512, 700, 0))])
    img = layer(img, lambda d, _: blood_drops(d, [(512, 470, 40)]))
    finish(img, "illusion_ambush", glow_col=RED)


def royal_execution():
    img = ult_backdrop(new())
    def fn(d, _):
        # crown
        d.polygon([(250, 520), (250, 300), (380, 420), (512, 240), (644, 420), (774, 300), (774, 520)], fill=GOLD, outline=BLACK, width=OL)
        d.rectangle([250, 500, 774, 600], fill=GOLD, outline=BLACK, width=OL)
        for x in (360, 512, 664):
            d.ellipse([x - 36, 515, x + 36, 585], fill=GREEN, outline=BLACK, width=14)
        blood_drops(d, [(360, 760, 50), (560, 820, 40), (700, 720, 34)])
    img = layer(img, fn)
    finish(img, "royal_execution", glow_col=RED, ult=True)


if __name__ == "__main__":
    for fn in (mirror_image, phantom_squad, false_emergency, illusion_trap, hall_of_loki,
               disguise, perfect_impersonation, creature_form, identity_swap, the_other_you,
               mystic_blink, swap_trick, decoy_escape, shadow_step, master_of_misdirection,
               green_bolt, binding_ribbons, mystic_shield, enchanted_dagger, asgardian_wrath,
               fake_body, mind_whisper, enchanted_witness, chaos_contract, the_grand_deception,
               switch_tab, backstab, scepter_blast, serpent_strike, illusion_ambush, royal_execution):
        fn()
    print("icons written to", os.path.normpath(OUT_DIR))
