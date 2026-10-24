"""Element (true 3D) models + procedural textures for telephones, workbench, sentry turret and delivery props."""
import json, math, random
from PIL import Image, ImageDraw

FACES = ["north", "south", "east", "west", "up", "down"]

def clamp(v): return max(0, min(255, int(v)))
def col(c, f): return (clamp(c[0] * f), clamp(c[1] * f), clamp(c[2] * f), 255)

class Sheet:
    """32x32 sheet, 4x4 tiles of 8px."""
    def __init__(self, seed=0):
        self.img = Image.new("RGBA", (32, 32), (0, 0, 0, 255))
        self.d = ImageDraw.Draw(self.img)
        self.r = random.Random(seed)

    def fill(self, c, r, base, noise=0.06, hi=True, lines=None):
        for y in range(8):
            for x in range(8):
                f = 1 + self.r.uniform(-noise, noise)
                if hi and y == 0: f *= 1.22
                if hi and y == 7: f *= 0.78
                self.img.putpixel((c * 8 + x, r * 8 + y), col(base, f))
        if lines:
            for (x0, y0, x1, y1, cc) in lines:
                self.d.rectangle([c * 8 + x0, r * 8 + y0, c * 8 + x1, r * 8 + y1], fill=cc)

    def px(self, c, r, x, y, cc): self.img.putpixel((c * 8 + x, r * 8 + y), cc)
    def rect(self, c, r, x0, y0, x1, y1, cc): self.d.rectangle([c * 8 + x0, r * 8 + y0, c * 8 + x1, r * 8 + y1], fill=cc)

def uv(tile):
    c, r = tile
    return [c * 4, r * 4, c * 4 + 4, r * 4 + 4]

def box(frm, to, tile, tiles=None, rot=None, emit=0, faces=None, shade=True):
    """tiles: dict face->tile overrides"""
    el = {"from": list(frm), "to": list(to), "faces": {}}
    if rot: el["rotation"] = rot
    if emit: el["light_emission"] = emit
    if not shade: el["shade"] = False
    for f in (faces or FACES):
        t = (tiles or {}).get(f, tile)
        if t is None: continue
        el["faces"][f] = {"uv": uv(t), "texture": "#t"}
    return el

def shift(el, dx=0, dy=0, dz=0):
    e = json.loads(json.dumps(el))
    e["from"] = [e["from"][0] + dx, e["from"][1] + dy, e["from"][2] + dz]
    e["to"] = [e["to"][0] + dx, e["to"][1] + dy, e["to"][2] + dz]
    if "rotation" in e:
        e["rotation"]["origin"] = [e["rotation"]["origin"][0] + dx, e["rotation"]["origin"][1] + dy, e["rotation"]["origin"][2] + dz]
    return e

def model(elements, tex, extra_tex=None, display=None):
    m = {"textures": {"t": tex, "particle": tex}, "elements": elements}
    if extra_tex: m["textures"].update(extra_tex)
    if display: m["display"] = display
    return m

# tile aliases
BODY, TRIM, DIAL, KEYS = (0, 0), (1, 0), (2, 0), (3, 0)
KEYA, KEYB, KEYC, HAND = (0, 1), (1, 1), (2, 1), (3, 1)
CORD, SCREEN, LABEL, DARK = (0, 2), (1, 2), (2, 2), (3, 2)
LAMP, METAL, RUBBER, GLASS = (0, 3), (1, 3), (2, 3), (3, 3)

# ---------------------------------------------------------------------------------------------
# telephone textures
# ---------------------------------------------------------------------------------------------
PHONES = {
    "rotary":   dict(name="Rotary Desk Phone",    body=(30, 30, 36), trim=(205, 160, 62), hand=(26, 26, 32), keys=False, ring="classic"),
    "terminal": dict(name="Desk Terminal",        body=(214, 204, 178), trim=(120, 126, 134), hand=(190, 182, 160), keys=True, ring="digital"),
    "uplink":   dict(name="Field Uplink",         body=(84, 94, 56), trim=(40, 44, 34), hand=(70, 78, 48), keys=True, ring="satellite"),
}

def phone_sheet(key):
    p = PHONES[key]
    s = Sheet(hash(key) & 0xffff)
    s.fill(0, 0, p["body"], 0.05)
    s.fill(1, 0, p["trim"], 0.04)
    # dial face
    dark = (22, 22, 26)
    s.fill(2, 0, (24, 24, 28), 0.02, hi=False)
    s.rect(2, 0, 0, 0, 7, 7, col(p["trim"], 0.55)); s.rect(2, 0, 1, 1, 6, 6, (232, 228, 214, 255))
    s.rect(2, 0, 2, 2, 5, 5, (24, 24, 28, 255))
    for i in range(10):
        a = math.radians(40 + i * 28)
        x = round(3.5 + math.cos(a) * 2.4 - 0.2); y = round(3.5 + math.sin(a) * 2.4 - 0.2)
        if 0 <= x < 8 and 0 <= y < 8: s.px(2, 0, x, y, (232, 228, 214, 255))
    s.px(2, 0, 3, 3, col(p["trim"], 1.0)); s.px(2, 0, 4, 4, col(p["trim"], 1.0))
    # keypad
    def keypad(c, r, lit=None):
        s.fill(c, r, col(p["body"], 0.5), 0.02, hi=False)
        for ky in range(4):
            for kx in range(3):
                x0, y0 = 1 + kx * 2, 0 + ky * 2
                on = lit is not None and (kx + ky * 3) in lit
                cc = (255, 220, 90, 255) if on else (232, 232, 224, 255)
                s.rect(c, r, x0, y0, x0, y0, cc)
                s.rect(c, r, x0, y0 + 1, x0, y0 + 1, col(cc, 0.8)) if ky < 3 else None
    keypad(3, 0); keypad(0, 1, {1, 5, 9}); keypad(1, 1, {3, 4, 7}); keypad(2, 1, {0, 8, 10})
    s.fill(3, 1, p["hand"], 0.04)
    s.fill(0, 2, (14, 14, 16), 0.03)
    for y in range(0, 8, 2):
        for x in range(8): s.px(0, 2, x, y, (36, 36, 40, 255))
    s.fill(1, 2, (10, 40, 30), 0.0, hi=False)
    for y in range(0, 8, 2): s.rect(1, 2, 0, y, 7, y, (24, 90, 64, 255))
    s.rect(1, 2, 1, 1, 5, 1, (110, 255, 190, 255)); s.rect(1, 2, 1, 3, 3, 3, (110, 255, 190, 255)); s.rect(1, 2, 1, 5, 6, 5, (110, 255, 190, 255))
    s.fill(2, 2, (236, 232, 220), 0.02, hi=False)
    s.rect(2, 2, 0, 3, 7, 4, (190, 40, 36, 255)); s.rect(2, 2, 1, 6, 6, 6, (60, 60, 66, 255))
    s.fill(3, 2, (8, 8, 10), 0.02, hi=False)
    s.fill(0, 3, (30, 40, 30), 0.0, hi=False)
    s.rect(0, 3, 1, 1, 3, 6, (90, 255, 120, 255)); s.rect(0, 3, 5, 1, 6, 6, (255, 70, 60, 255))
    s.fill(1, 3, (150, 154, 162), 0.05)
    s.fill(2, 3, (18, 18, 20), 0.04)
    s.fill(3, 3, (120, 200, 230), 0.02, hi=False)
    return s

def terminal_screen_strip():
    """animated 16x16x8 CRT strip: scrolling text + rolling scanline + flicker."""
    frames = 8
    img = Image.new("RGBA", (16, 16 * frames), (0, 0, 0, 255))
    r = random.Random(77)
    for f in range(frames):
        for y in range(16):
            for x in range(16):
                base = 18 + (10 if y % 2 == 0 else 0)
                g = base
                img.putpixel((x, f * 16 + y), (4, g + 6, g, 255))
        # text rows scroll
        for row in range(5):
            y = (row * 3 + f * 2) % 16
            ln = 3 + (row * 5 + 2) % 9
            for x in range(1, 1 + ln):
                if (x + row) % 4 != 0:
                    img.putpixel((x, f * 16 + y), (90, 255, 170, 255))
        # rolling bright scanline
        sy = (f * 2) % 16
        for x in range(16):
            px = img.getpixel((x, f * 16 + sy)); img.putpixel((x, f * 16 + sy), (px[0] + 30, min(255, px[1] + 60), min(255, px[2] + 40), 255))
        # cursor blink
        if f % 2 == 0:
            for y in range(12, 14):
                for x in range(10, 12): img.putpixel((x, f * 16 + y), (200, 255, 220, 255))
    return img

def phone_models(key):
    """returns dict state_name -> model json (north facing)."""
    p = PHONES[key]
    states = ["idle", "ring_a", "ring_b", "off_hook", "dial_a", "dial_b", "dial_c"]
    out = {}
    for st in states:
        els = []
        off = st in ("off_hook", "dial_a", "dial_b", "dial_c")
        ring = st in ("ring_a", "ring_b")
        rdx = (0.4 if st == "ring_a" else -0.4) if ring else 0
        rdy = 0.7 if ring else 0
        rrot = None
        if key == "rotary":
            els += [
                box([2.5, 0, 3], [13.5, 1.5, 13.5], TRIM, {"up": TRIM}),
                box([3.5, 1.5, 4], [12.5, 4.5, 12.5], BODY),
                box([4.5, 4.5, 6], [11.5, 6.2, 12], BODY),
                box([4.4, 1.9, 2.9], [11.6, 5.6, 3.3], TRIM),
            ]
            ang = {"dial_a": 22.5, "dial_b": 45, "dial_c": -22.5}.get(st, 0)
            dial = box([5, 2.3, 3.3], [11, 5.2, 4.0], DIAL, {"north": DIAL, "south": BODY, "east": BODY, "west": BODY, "up": BODY, "down": BODY})
            if ang: dial["rotation"] = {"origin": [8, 3.75, 3.6], "axis": "z", "angle": ang}
            els.append(dial)
            els += [box([4.6, 6, 10], [5.6, 7.8, 11.2], TRIM), box([10.4, 6, 10], [11.4, 7.8, 11.2], TRIM)]
            plunger_y = 1.2 if off else 0
            els.append(box([7.5, 6 + plunger_y, 10.4], [8.5, 7 + plunger_y, 11], TRIM))
            els.append(box([7.5, 1.5, 12.5], [8.5, 2.6, 15.8], CORD))
            if not off:
                els += [shift(box([3, 7.8, 9.9], [13, 9.4, 11.6], HAND), rdx, rdy),
                        shift(box([1.8, 7, 9.4], [4.6, 9.8, 12.1], HAND, {"north": HAND}), rdx, rdy),
                        shift(box([11.4, 7, 9.4], [14.2, 9.8, 12.1], HAND), rdx, rdy)]
            else:
                els += [box([3, 0, 0.6], [13, 1.7, 2.4], HAND), box([1.8, 0, 0.1], [4.6, 2.6, 2.9], HAND), box([11.4, 0, 0.1], [14.2, 2.6, 2.9], HAND),
                        box([7.5, 0.2, 2.4], [8.5, 1, 3.0], CORD)]
        elif key == "terminal":
            keys = {"dial_a": KEYA, "dial_b": KEYB, "dial_c": KEYC}.get(st, KEYS)
            els += [
                box([2, 0, 2.5], [14, 2, 14], BODY),
                box([3, 2, 8], [13, 10, 14], BODY),
                box([3.6, 2.4, 3.2], [12.4, 3.4, 8.4], keys, {"up": keys, "north": BODY, "south": BODY, "east": BODY, "west": BODY, "down": BODY},
                    rot={"origin": [8, 2.4, 6], "axis": "x", "angle": -22.5}),
                box([3.2, 3, 7.7], [12.8, 9.4, 8.05], TRIM),
                box([4, 3.7, 7.5], [12, 8.7, 7.8], DARK, emit=13),
                box([10.5, 2.2, 2.6], [11.8, 2.8, 3.2], LAMP, emit=15),
                box([3, 10, 9], [13, 10.6, 13], TRIM),
                box([4.6, 10.6, 9.4], [5.6, 12.2, 10.6], TRIM), box([10.4, 10.6, 9.4], [11.4, 12.2, 10.6], TRIM),
            ]
            # the screen uses a dedicated animated texture
            els[4]["faces"]["north"] = {"uv": [0, 0, 16, 16], "texture": "#screen"}
            els.append(box([7.5, 10.6 + (1.2 if off else 0), 9.6], [8.5, 11.6 + (1.2 if off else 0), 10.4], TRIM))
            if not off:
                hy = 12.2
                els += [shift(box([3, hy, 9], [13, hy + 1.5, 10.8], HAND), rdx, rdy),
                        shift(box([1.8, hy - 0.8, 8.6], [4.6, hy + 2, 11.2], HAND), rdx, rdy),
                        shift(box([11.4, hy - 0.8, 8.6], [14.2, hy + 2, 11.2], HAND), rdx, rdy)]
            else:
                els += [box([3, 0, 0.5], [13, 1.7, 2.3], HAND), box([1.8, 0, 0.0], [4.6, 2.6, 2.7], HAND), box([11.4, 0, 0.0], [14.2, 2.6, 2.7], HAND),
                        box([7.5, 0.2, 2.3], [8.5, 1, 2.6], CORD)]
        else:  # uplink
            keys = {"dial_a": KEYA, "dial_b": KEYB, "dial_c": KEYC}.get(st, KEYS)
            els += [
                box([2, 0, 3], [14, 5, 13], BODY),
                box([1.4, 0.4, 3.6], [2, 4.6, 12.4], TRIM), box([14, 0.4, 3.6], [14.6, 4.6, 12.4], TRIM),
                box([3.5, 5, 4], [12.5, 5.7, 9.5], keys, {"up": keys, "north": BODY, "south": BODY, "east": BODY, "west": BODY, "down": BODY}),
                box([3.5, 3.5, 2.8], [12.5, 4.6, 3.05], LAMP, emit=15),
                box([12.4, 5, 10.4], [13.4, 15, 11.4], TRIM), box([12.0, 14.4, 10.0], [13.8, 15.4, 11.8], LABEL),
                box([3, 1.4, 2.7], [6, 3, 3.05], LABEL),
                box([3.2, 5, 10.2], [4.2, 6.8, 11.4], TRIM), box([10.4, 5, 10.2], [11.4, 6.8, 11.4], TRIM),
            ]
            els.append(box([7.4, 5 + (1.2 if off else 0), 10.4], [8.4, 6 + (1.2 if off else 0), 11.2], TRIM))
            if not off:
                hy = 6.8
                els += [shift(box([3, hy, 9.9], [13, hy + 1.6, 11.7], HAND), rdx, rdy),
                        shift(box([1.8, hy - 0.8, 9.4], [4.6, hy + 2.2, 12.2], HAND), rdx, rdy),
                        shift(box([11.4, hy - 0.8, 9.4], [14.2, hy + 2.2, 12.2], HAND), rdx, rdy)]
            else:
                els += [box([3, 0, 0.6], [13, 1.7, 2.4], HAND), box([1.8, 0, 0.1], [4.6, 2.6, 2.9], HAND), box([11.4, 0, 0.1], [14.2, 2.6, 2.9], HAND),
                        box([7.5, 0.2, 2.4], [8.5, 1, 3.0], CORD)]
        m = model(els, f"telephoneorder:block/phone_{key}")
        if key == "terminal":
            m["textures"]["screen"] = "telephoneorder:block/terminal_screen"
        m["display"] = {"gui": {"rotation": [30, 225, 0], "scale": [0.85, 0.85, 0.85], "translation": [0, -1, 0]},
                        "ground": {"scale": [0.5, 0.5, 0.5], "translation": [0, 2, 0]},
                        "fixed": {"scale": [0.85, 0.85, 0.85]},
                        "thirdperson_righthand": {"scale": [0.55, 0.55, 0.55], "translation": [0, 3, 0]},
                        "firstperson_righthand": {"scale": [0.5, 0.5, 0.5], "rotation": [0, 45, 0]}}
        out[st] = m
    return out

# ---------------------------------------------------------------------------------------------
# workbench / sentry
# ---------------------------------------------------------------------------------------------
def workbench():
    s = Sheet(21)
    s.fill(0, 0, (120, 84, 50), 0.07)               # wood
    s.fill(1, 0, (70, 74, 82), 0.05)                # steel
    s.fill(2, 0, (40, 44, 52), 0.03)                # dark
    s.fill(3, 0, (36, 78, 128), 0.02, hi=False)     # blueprint
    for i in range(0, 8, 2): s.rect(3, 0, 0, i, 7, i, (90, 150, 210, 255))
    s.rect(3, 0, 2, 2, 5, 5, (200, 230, 255, 255)); s.rect(3, 0, 3, 3, 4, 4, (36, 78, 128, 255))
    s.fill(0, 1, (190, 60, 50), 0.03)               # red tool
    s.fill(1, 1, (255, 200, 60), 0.02)              # yellow
    s.fill(2, 1, (255, 250, 200), 0.0, hi=False)    # lamp
    WOOD, STEEL, DK, BP, RED, YEL, LAMPT = (0, 0), (1, 0), (2, 0), (3, 0), (0, 1), (1, 1), (2, 1)
    els = [
        box([0, 9, 0], [16, 12, 16], WOOD, {"up": WOOD}),
        box([0.5, 12, 0.5], [15.5, 12.4, 15.5], STEEL, {"up": STEEL}),
        box([1, 0, 1], [3.5, 9, 3.5], DK), box([12.5, 0, 1], [15, 9, 1 + 2.5], DK),
        box([1, 0, 12.5], [3.5, 9, 15], DK), box([12.5, 0, 12.5], [15, 9, 15], DK),
        box([3.5, 3, 3.5], [12.5, 4, 12.5], WOOD),
        box([4, 12.4, 6], [10, 12.6, 12], BP, {"up": BP}),
        box([11, 12.4, 2.5], [14.5, 15.4, 6.5], STEEL), box([11.5, 15.4, 3], [14, 16, 6], YEL),
        box([2, 12.4, 2], [5, 14, 5], RED), box([2.3, 14, 2.3], [4.7, 15.4, 4.7], DK),
        box([1.2, 14, 1.2], [2.0, 15.8, 2.0], LAMPT, emit=15),
        box([11, 12.4, 9], [14.5, 14.4, 14], DK), box([11.5, 14.4, 9.5], [14, 14.9, 13.5], STEEL),
        box([6, 12.4, 13], [10, 13.6, 15], STEEL),
    ]
    m = model(els, "telephoneorder:block/weapons_workbench")
    m["display"] = {"gui": {"rotation": [30, 225, 0], "scale": [0.65, 0.65, 0.65]}, "ground": {"scale": [0.4, 0.4, 0.4]}, "fixed": {"scale": [0.5, 0.5, 0.5]}}
    return s.img, m

def sentry():
    s = Sheet(31)
    s.fill(0, 0, (70, 78, 62), 0.06)
    s.fill(1, 0, (40, 44, 40), 0.04)
    s.fill(2, 0, (200, 40, 36), 0.02, hi=False)
    s.fill(3, 0, (150, 156, 164), 0.04)
    s.fill(0, 1, (230, 190, 60), 0.03)
    OL, DK, EYE, MT, YL = (0, 0), (1, 0), (2, 0), (3, 0), (0, 1)
    els = [
        box([4, 0, 4], [12, 1, 12], DK),
        box([7.5, 1, 7.5], [8.5, 4, 8.5], MT),
        box([6.5, 0, 3.5], [7.5, 3.5, 4.5], MT, rot={"origin": [7, 3, 4], "axis": "x", "angle": 22.5}),
        box([8.5, 0, 3.5], [9.5, 3.5, 4.5], MT, rot={"origin": [9, 3, 4], "axis": "x", "angle": 22.5}),
        box([7.5, 0, 11], [8.5, 3.5, 12.2], MT, rot={"origin": [8, 3, 12], "axis": "x", "angle": -22.5}),
        box([5, 4, 5], [11, 8, 11], OL),
        box([5.5, 8, 5.5], [10.5, 9, 10.5], DK),
        box([7, 5, -1], [9, 7, 5], DK),
        box([6.6, 4.6, -1.4], [9.4, 7.4, -0.6], MT),
        box([6, 6.6, 4.7], [10, 7.6, 5.1], EYE, emit=15),
        box([11, 4, 6], [13, 7, 10], YL), box([4, 4, 6], [5, 6, 10], DK),
        box([7.4, 9, 7.4], [8.6, 11, 8.6], MT), box([7, 11, 7], [9, 11.8, 9], EYE, emit=15),
    ]
    m = model(els, "telephoneorder:block/sentinel_turret")
    m["display"] = {"gui": {"rotation": [30, 135, 0], "scale": [0.9, 0.9, 0.9]}, "ground": {"scale": [0.5, 0.5, 0.5]}, "fixed": {"scale": [0.8, 0.8, 0.8]}}
    return s.img, m

# ---------------------------------------------------------------------------------------------
# delivery props (item models rendered via item_display entities)
# ---------------------------------------------------------------------------------------------
def prop_crate():
    s = Sheet(41)
    s.fill(0, 0, (150, 110, 62), 0.08)
    for y in (2, 5): s.rect(0, 0, 0, y, 7, y, (110, 78, 42, 255))
    s.fill(1, 0, (60, 62, 68), 0.05)
    s.fill(2, 0, (240, 200, 50), 0.02, hi=False)
    for i in range(0, 8, 2): s.rect(2, 0, i, 0, i + 0, 7, (30, 30, 30, 255))
    s.fill(3, 0, (240, 236, 226), 0.0, hi=False); s.rect(3, 0, 1, 2, 6, 3, (200, 40, 36, 255)); s.rect(3, 0, 1, 5, 6, 5, (60, 60, 66, 255))
    s.fill(0, 1, (255, 255, 255), 0.0, hi=False)
    WD, ST, HZ, LB, LT = (0, 0), (1, 0), (2, 0), (3, 0), (0, 1)
    def body(open_):
        els = [box([1.5, 0, 1.5], [14.5, 12, 14.5], WD),
               box([1.2, 0, 1.2], [3, 12.4, 3], ST), box([13, 0, 1.2], [14.8, 12.4, 3], ST), box([1.2, 0, 13], [3, 12.4, 14.8], ST), box([13, 0, 13], [14.8, 12.4, 14.8], ST),
               box([1.3, 5, 1.3], [14.7, 7, 14.7], HZ, {"up": None, "down": None}),
               box([3.5, 4, 1.2], [12.5, 11, 1.3], LB, {"north": LB})]
        if not open_:
            els.append(box([1, 12, 1], [15, 14, 15], WD)); els.append(box([6.5, 14, 6.5], [9.5, 15, 9.5], ST))
            els.append(box([7.4, 14.2, 7.4], [8.6, 14.8, 8.6], LT, emit=15))
        else:
            els.append(box([2, 11.9, 2], [14, 12.2, 14], DKTILE))
            els.append(box([1, 13, 14.5], [15, 14.4, 15.5], WD, rot={"origin": [8, 12, 15], "axis": "x", "angle": -45}))
            els.append(box([1, 12, 14], [15, 15, 15], WD, rot={"origin": [8, 12, 15], "axis": "x", "angle": 22.5}))
        return els
    global DKTILE
    DKTILE = (1, 0)
    return s.img, model(body(False), "telephoneorder:item/prop/delivery_crate"), model(body(True), "telephoneorder:item/prop/delivery_crate")

def prop_drone():
    s = Sheet(51)
    s.fill(0, 0, (60, 64, 72), 0.05); s.fill(1, 0, (28, 30, 34), 0.03); s.fill(2, 0, (255, 70, 60), 0.0, hi=False)
    s.fill(3, 0, (90, 220, 255), 0.0, hi=False); s.fill(0, 1, (220, 224, 230), 0.04)
    BD, DK, RD, CY, WH = (0, 0), (1, 0), (2, 0), (3, 0), (0, 1)
    els = [box([5, 1, 5], [11, 4, 11], BD), box([6, 4, 6], [10, 5, 10], DK), box([6.5, 0, 6.5], [9.5, 1, 9.5], DK),
           box([7.2, 0.2, 5], [8.8, 0.8, 5.2], CY, emit=15),
           box([7, 3, 4.6], [9, 4, 5], RD, emit=15)]
    for sx in (-1, 1):
        for sz in (-1, 1):
            cx, cz = 8 + sx * 4.6, 8 + sz * 4.6
            els.append(box([cx - 0.7, 2.2, cz - 0.7], [cx + 0.7, 4.4, cz + 0.7], DK))
            els.append(box([cx - 0.35, 4.4, cz - 0.35], [cx + 0.35, 5.0, cz + 0.35], WH))
    # arms
    els.append(box([1.5, 2.5, 7.4], [14.5, 3.5, 8.6], DK, rot={"origin": [8, 3, 8], "axis": "y", "angle": 45}))
    els.append(box([1.5, 2.5, 7.4], [14.5, 3.5, 8.6], DK, rot={"origin": [8, 3, 8], "axis": "y", "angle": -45}))
    # landing legs + winch
    els.append(box([5, 0, 5], [5.6, 1.2, 5.6], DK)); els.append(box([10.4, 0, 5], [11, 1.2, 5.6], DK))
    m = model(els, "telephoneorder:item/prop/delivery_drone")
    s2 = Sheet(52)
    s2.fill(0, 0, (30, 32, 36), 0.02); s2.fill(1, 0, (200, 205, 215), 0.0, hi=False)
    rot = model([box([5.2, 8, 7.65], [10.8, 8.25, 8.35], (0, 0)), box([7.65, 8, 5.2], [8.35, 8.25, 10.8], (0, 0)), box([7.5, 7.9, 7.5], [8.5, 8.4, 8.5], (1, 0))],
                "telephoneorder:item/prop/delivery_rotor")
    return s.img, m, s2.img, rot

def prop_pod(open_door=False):
    s = Sheet(61)
    s.fill(0, 0, (96, 102, 112), 0.05); s.fill(1, 0, (48, 50, 56), 0.03); s.fill(2, 0, (255, 140, 40), 0.02, hi=False)
    s.fill(3, 0, (30, 34, 40), 0.02); s.fill(0, 1, (255, 255, 255), 0.0, hi=False)
    for i in range(8): s.px(2, 0, i, i, (30, 30, 30, 255)); s.px(2, 0, (i + 4) % 8, i, (30, 30, 30, 255))
    s.fill(1, 1, (120, 255, 200), 0.0, hi=False)
    HL, DK, HZ, IN, LTW, LG = (0, 0), (1, 0), (2, 0), (3, 0), (0, 1), (1, 1)
    els = [box([4, 0, 4], [12, 11, 12], HL), box([5, 11, 5], [11, 13, 11], HL), box([6, 13, 6], [10, 15, 10], HL), box([7, 15, 7], [9, 16.5, 9], DK),
           box([3.6, 3, 3.6], [12.4, 4, 12.4], DK), box([3.6, 8, 3.6], [12.4, 9, 12.4], DK),
           box([3, 0, 7.4], [4, 6, 8.6], DK, rot={"origin": [3.5, 0, 8], "axis": "z", "angle": 22.5}),
           box([12, 0, 7.4], [13, 6, 8.6], DK, rot={"origin": [12.5, 0, 8], "axis": "z", "angle": -22.5}),
           box([7.4, 0, 3], [8.6, 6, 4], DK, rot={"origin": [8, 0, 3.5], "axis": "x", "angle": -22.5}),
           box([7.4, 0, 12], [8.6, 6, 13], DK, rot={"origin": [8, 0, 12.5], "axis": "x", "angle": 22.5}),
           box([6.5, 6, 3.9], [9.5, 9.5, 4.0], LG, {"north": LG}, emit=15),
           box([4.6, 9, 3.9], [11.4, 10, 4.1], HZ, {"north": HZ})]
    if open_door:
        els.append(box([4.2, 1.5, 3.6], [11.8, 10.5, 3.9], IN, {"north": IN}))
        els.append(box([4, 1, 3], [12, 11, 4], HL, rot={"origin": [4, 1, 3.5], "axis": "y", "angle": 45}))
    else:
        els.append(box([4.2, 1.5, 3.7], [11.8, 10.5, 4.0], DK, {"north": DK}))
    return s.img, model(els, "telephoneorder:item/prop/delivery_pod")

def prop_container(open_=False):
    s = Sheet(71)
    s.fill(0, 0, (46, 52, 66), 0.04); s.fill(1, 0, (120, 230, 255), 0.0, hi=False); s.fill(2, 0, (22, 26, 34), 0.02)
    s.fill(3, 0, (200, 210, 230), 0.04); s.rect(0, 0, 0, 3, 7, 4, (120, 230, 255, 255))
    for i in range(0, 8, 2): s.rect(2, 0, 0, i, 7, i, (60, 70, 90, 255))
    SK, GL, DK, LT = (0, 0), (1, 0), (2, 0), (3, 0)
    els = [box([3, 0, 3], [13, 10, 13], SK), box([2.6, 0, 2.6], [13.4, 1, 13.4], DK),
           box([2.8, 4.5, 2.8], [13.2, 5.5, 13.2], GL, {"up": None, "down": None}, emit=15),
           box([2.8, 9.5, 2.8], [13.2, 10.2, 13.2], GL, {"up": None, "down": None}, emit=15),
           box([2.8, 0, 2.8], [3.4, 10, 3.4], LT), box([12.6, 0, 2.8], [13.2, 10, 3.4], LT), box([2.8, 0, 12.6], [3.4, 10, 13.2], LT), box([12.6, 0, 12.6], [13.2, 10, 13.2], LT)]
    if open_:
        els.append(box([3.4, 10, 3.4], [12.6, 10.4, 12.6], DK))
        els.append(box([3, 10, 12.6], [13, 11, 13.2], SK, rot={"origin": [8, 10, 13], "axis": "x", "angle": -45}))
        els.append(box([3, 10, 2.8], [13, 11, 3.4], SK, rot={"origin": [8, 10, 3], "axis": "x", "angle": 45}))
        els.append(box([3.4, 9.6, 3.4], [12.6, 9.8, 12.6], GL, {"up": GL}, emit=15))
    else:
        els.append(box([3, 10, 3], [13, 11, 13], SK)); els.append(box([6, 11, 6], [10, 11.6, 10], GL, emit=15))
    return s.img, model(els, "telephoneorder:item/prop/delivery_container")

def prop_case(open_=False):
    s = Sheet(81)
    s.fill(0, 0, (36, 38, 44), 0.04); s.fill(1, 0, (190, 150, 60), 0.04); s.fill(2, 0, (255, 60, 60), 0.0, hi=False)
    s.fill(3, 0, (90, 90, 98), 0.05); s.fill(0, 1, (18, 18, 22), 0.02); s.fill(1, 1, (120, 255, 190), 0.0, hi=False)
    for i in range(8): s.px(3, 0, i, 3, (60, 60, 66, 255))
    SH, GD, RD, MT, FOAM, GN = (0, 0), (1, 0), (2, 0), (3, 0), (0, 1), (1, 1)
    els = [box([2, 0, 5], [14, 4, 11], SH), box([1.6, 0, 4.6], [2.4, 4.4, 11.4], GD), box([13.6, 0, 4.6], [14.4, 4.4, 11.4], GD),
           box([6, 4, 7.4], [10, 5.5, 8.6], MT), box([6.5, 5.5, 7.2], [9.5, 5.9, 8.8], GD)]
    els.append(box([3, 3, 4.8], [4.4, 4.2, 5.2], GD)); els.append(box([11.6, 3, 4.8], [13, 4.2, 5.2], GD))
    els.append(box([3.4, 2.4, 4.7], [4, 3.2, 4.9], GN, emit=15)); els.append(box([12, 2.4, 4.7], [12.6, 3.2, 4.9], RD, emit=15))
    if open_:
        els = [box([2, 0, 5], [14, 3, 11], SH), box([3, 3, 5.4], [13, 3.4, 10.6], FOAM), box([4, 3.1, 6], [12, 3.5, 10], GN, {"up": GN}, emit=15),
               box([2, 3, 10.4], [14, 7.5, 11], SH, rot={"origin": [8, 3, 11], "axis": "x", "angle": 22.5}),
               box([1.6, 0, 4.6], [2.4, 3.4, 11.4], GD), box([13.6, 0, 4.6], [14.4, 3.4, 11.4], GD)]
    return s.img, model(els, "telephoneorder:item/prop/delivery_case")
