"""Procedural pixel-art for TELEPHONE ORDER weapons, attachments and ammo (32x32 sprites, muzzle to the right)."""
from PIL import Image
import math, random

W = 32
OUT = (24, 24, 30, 255)

def sh(c, f):
    return (max(0, min(255, int(c[0] * f))), max(0, min(255, int(c[1] * f))), max(0, min(255, int(c[2] * f))), 255)

def rgba(c):
    return (c[0], c[1], c[2], 255)

class Layer:
    def __init__(self):
        self.img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        self.px = self.img.load()

    def p(self, x, y, c):
        if 0 <= x < W and 0 <= y < W:
            self.px[x, y] = rgba(c)

    def rect(self, x0, y0, x1, y1, c):
        for y in range(y0, y1):
            for x in range(x0, x1):
                self.p(x, y, c)

    def line(self, x0, y0, x1, y1, c):
        n = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(n + 1):
            self.p(round(x0 + (x1 - x0) * i / n), round(y0 + (y1 - y0) * i / n), c)

    def disc(self, cx, cy, r, c):
        for y in range(int(cy - r) - 1, int(cy + r) + 2):
            for x in range(int(cx - r) - 1, int(cx + r) + 2):
                if (x - cx) ** 2 + (y - cy) ** 2 <= r * r + 0.3:
                    self.p(x, y, c)

    def ring(self, cx, cy, r, c, t=0.9):
        for y in range(int(cy - r) - 2, int(cy + r) + 3):
            for x in range(int(cx - r) - 2, int(cx + r) + 3):
                d = math.hypot(x - cx, y - cy)
                if abs(d - r) <= t:
                    self.p(x, y, c)

    def shaded(self, outline=True):
        """auto highlight / shadow / outline."""
        src = self.img.copy()
        s = src.load()
        out = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        o = out.load()
        def op(x, y):
            return 0 <= x < W and 0 <= y < W and s[x, y][3] > 0
        for y in range(W):
            for x in range(W):
                if s[x, y][3] > 0:
                    c = s[x, y]
                    if not op(x, y - 1):
                        c = sh(c, 1.32)
                    elif not op(x, y + 1):
                        c = sh(c, 0.72)
                    elif not op(x - 1, y):
                        c = sh(c, 1.08)
                    o[x, y] = c
        if outline:
            for y in range(W):
                for x in range(W):
                    if s[x, y][3] == 0 and (op(x + 1, y) or op(x - 1, y) or op(x, y + 1) or op(x, y - 1)):
                        o[x, y] = OUT
        return out

def paste(dst, src, dx=0, dy=0, alpha=1.0):
    d = dst.load()
    s = src.load()
    for y in range(W):
        for x in range(W):
            if s[x, y][3] > 0:
                tx, ty = x + dx, y + dy
                if 0 <= tx < W and 0 <= ty < W:
                    c = s[x, y]
                    if alpha < 1.0:
                        c = (c[0], c[1], c[2], int(c[3] * alpha))
                    d[tx, ty] = c

# ----------------------------------------------------------------------------------------------------
# style builders: return (body, mag, glow, anchors)
# ----------------------------------------------------------------------------------------------------
def palette(v):
    b = v["body"]
    a = v.get("accent", sh(b, 1.5)[:3])
    g = v.get("glow") or (60, 200, 230)
    return b, a, g

def new3():
    return Layer(), Layer(), Layer()

def A(**k):
    return k

def st_pistol(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(8, 11, 24, 15, b)          # slide
    body.rect(24, 12, 27, 14, sh(b, 0.8))  # barrel tip
    body.rect(8, 15, 22, 17, sh(b, 0.75))  # frame
    for r in range(8):                   # grip with lean
        body.rect(8 - r // 4, 15 + r, 13 - r // 4, 16 + r, sh(b, 0.6) if r % 2 else sh(b, 0.65))
    body.line(14, 17, 14, 19, sh(b, 0.7)); body.line(14, 19, 18, 19, sh(b, 0.7)); body.line(18, 17, 18, 19, sh(b, 0.7))
    body.p(9, 10, a); body.p(23, 10, a); body.p(8, 10, a)
    body.rect(10, 12, 20, 13, sh(b, 1.25))
    mag.rect(6, 19, 11, 26, sh(b, 0.5))
    return body, mag, glow, A(muzzle=(27, 13), optic=(16, 10), under=(19, 17), mag=(8, 25), module=(16, 13), stock=(7, 13), grip=(19, 17))

def st_machinepistol(v):
    body, mag, glow, an = st_pistol(v)
    b, a, g = palette(v)
    body.rect(24, 11, 29, 14, sh(b, 0.9))
    for x in (25, 27):
        body.p(x, 11, a)
    mag.rect(6, 19, 11, 28, sh(b, 0.5))
    mag.line(7, 21, 10, 21, a)
    an["muzzle"] = (29, 12); an["mag"] = (8, 27)
    return body, mag, glow, an

def st_revolver(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(7, 11, 19, 16, b)
    body.rect(19, 12, 28, 14, sh(b, 0.85))
    body.rect(19, 14, 25, 16, sh(b, 0.7))          # under-lug
    for r in range(9):
        body.rect(7 - r // 3, 16 + r, 13 - r // 3, 17 + r, a if r > 1 else sh(b, 0.6))
    body.p(6, 10, sh(b, 0.6)); body.p(7, 9, sh(b, 0.6))
    body.p(26, 11, a)
    mag.rect(12, 10, 18, 17, sh(b, 1.12))          # cylinder
    for y in (11, 13, 15):
        mag.p(14, y, OUT); mag.p(16, y, OUT)
    return body, mag, glow, A(muzzle=(28, 13), optic=(15, 9), under=(22, 16), mag=(15, 17), module=(14, 13), stock=(6, 13), grip=(22, 16))

def st_smg(v, supp=False):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(6, 11, 20, 16, b)
    body.rect(20, 12, 27, 15, sh(b, 0.8))
    if supp:
        body.rect(20, 11, 31, 16, sh(b, 0.65))
        for x in range(22, 31, 3): body.line(x, 11, x, 15, sh(b, 0.45))
    body.line(1, 12, 6, 12, sh(b, 0.7)); body.line(1, 12, 1, 16, sh(b, 0.7)); body.line(1, 16, 6, 16, sh(b, 0.7))
    body.rect(12, 16, 15, 22, sh(b, 0.6))
    body.rect(8, 10, 18, 11, sh(b, 1.2))
    body.p(24, 10, a); body.p(8, 9, a)
    body.rect(7, 12, 12, 13, a)
    mag.rect(16, 16, 19, 25, sh(b, 0.5)); mag.line(16, 21, 18, 21, a)
    m = (31, 13) if supp else (27, 13)
    return body, mag, glow, A(muzzle=m, optic=(13, 10), under=(23, 15), mag=(17, 24), module=(14, 13), stock=(2, 13), grip=(23, 15))

def st_pdw(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(4, 11, 24, 17, b)
    body.rect(24, 12, 28, 15, sh(b, 0.8))
    body.rect(5, 10, 14, 11, sh(b, 1.2))
    body.rect(16, 17, 19, 23, sh(b, 0.6))
    body.rect(5, 13, 10, 14, a)
    body.p(20, 10, a)
    mag.rect(10, 17, 13, 24, sh(b, 0.5)); mag.line(10, 20, 12, 20, a)
    return body, mag, glow, A(muzzle=(28, 13), optic=(12, 10), under=(22, 17), mag=(11, 23), module=(14, 14), stock=(4, 13), grip=(22, 17))

def st_rifle(v, wood=False, longb=False, dmr=False, stripe=False, modular=False):
    b, a, g = palette(v); body, mag, glow = new3()
    wc = a if wood else b
    body.rect(1, 12, 9, 17, wc if wood else sh(b, 0.85))
    body.rect(2, 11, 6, 12, wc if wood else sh(b, 0.85))
    body.rect(9, 11, 19, 16, b)
    body.rect(19, 11, 25, 16, wc if wood else sh(b, 0.9))
    bx = 31 if longb else 29
    body.rect(25, 12, bx, 14, sh(b, 0.65))
    body.rect(bx - 2, 11, bx, 15, sh(b, 0.55))
    body.rect(10, 16, 12, 21, sh(b, 0.6))
    body.rect(10, 9, 14, 11, a); body.p(26, 10, a); body.p(26, 9, a)
    if dmr:
        body.rect(11, 8, 20, 10, sh(b, 0.5)); body.p(10, 8, (180, 220, 255)); body.p(21, 8, (180, 220, 255))
    if stripe:
        for i in range(3): body.p(14 + i * 2, 12, (240, 210, 60))
    if modular:
        for x in range(10, 24, 2): body.p(x, 10, sh(b, 1.4)); body.p(x + (0 if x < 20 else 0), 16, sh(b, 1.4))
        glow.rect(12, 13, 17, 15, g)
    mag.rect(14, 16, 18, 24, sh(b, 0.5)); mag.line(14, 20, 17, 20, a)
    return body, mag, glow, A(muzzle=(bx, 13), optic=(15, 10), under=(23, 16), mag=(16, 23), module=(13, 13), stock=(2, 14), grip=(23, 16))

def st_sniper(v, heavy=False):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(0, 12, 9, 18, sh(b, 0.85)); body.rect(3, 14, 6, 16, OUT)
    body.rect(9, 11, 17, 16, b)
    body.rect(17, 12, 31, 14 if not heavy else 15, sh(b, 0.7))
    if heavy:
        body.rect(27, 11, 31, 16, sh(b, 0.55))
        for x in (28, 30): body.line(x, 11, x, 15, OUT)
    body.rect(10, 7, 22, 10, sh(b, 0.45)); body.rect(10, 8, 22, 9, sh(b, 0.9))
    body.rect(9, 6, 11, 11, sh(b, 0.45)); body.rect(21, 6, 23, 11, sh(b, 0.45))
    body.p(10, 7, (150, 220, 255)); body.p(22, 7, (150, 220, 255))
    body.rect(14, 10, 15, 11, sh(b, 0.5)); body.p(13, 10, a); body.p(12, 10, a)  # bolt
    body.line(21, 15, 25, 19, sh(b, 0.55)); body.line(23, 15, 27, 19, sh(b, 0.55))   # bipod
    mag.rect(11, 16, 14, 20, sh(b, 0.5))
    return body, mag, glow, A(muzzle=(31, 13), optic=(16, 7), under=(24, 15), mag=(12, 19), module=(13, 13), stock=(1, 14), grip=(24, 15))

def st_pump(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(1, 12, 9, 19, a); body.rect(2, 11, 6, 12, a)
    body.rect(9, 11, 15, 16, sh(b, 0.8))
    body.rect(15, 11, 30, 13, b)
    body.rect(15, 14, 29, 16, sh(b, 0.85))
    body.rect(19, 13, 26, 17, a)
    body.p(30, 10, sh(b, 1.3)); body.p(14, 10, sh(b, 1.3))
    mag.rect(15, 16, 23, 17, sh(b, 0.6))
    return body, mag, glow, A(muzzle=(30, 12), optic=(12, 10), under=(24, 16), mag=(20, 17), module=(12, 13), stock=(2, 14), grip=(26, 16))

def st_autoshot(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(1, 12, 8, 18, sh(b, 0.85))
    body.rect(7, 10, 19, 17, b)
    body.rect(19, 11, 28, 14, sh(b, 0.75)); body.rect(19, 14, 26, 16, sh(b, 0.9))
    body.rect(9, 17, 12, 21, sh(b, 0.6))
    for x in range(20, 27, 2): body.p(x, 11, a)
    body.rect(8, 11, 18, 12, a)
    body.p(28, 11, sh(b, 1.3))
    mag.rect(13, 17, 18, 25, sh(b, 0.5)); mag.line(13, 20, 17, 20, a)
    return body, mag, glow, A(muzzle=(28, 12), optic=(12, 9), under=(23, 16), mag=(15, 24), module=(12, 13), stock=(2, 14), grip=(23, 16))

def st_lmg(v, saw=False):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(0, 12, 6, 18, sh(b, 0.85))
    body.rect(6, 10, 19, 16, b)
    body.rect(19, 11, 31, 14, sh(b, 0.75))
    for x in range(21, 30, 2): body.p(x, 12, OUT)
    body.rect(7, 8, 14, 10, sh(b, 0.6)); body.rect(8, 9, 13, 10, a)   # carry handle
    body.rect(9, 16, 11, 21, sh(b, 0.6))
    body.line(26, 14, 24, 20, sh(b, 0.55)); body.line(28, 14, 30, 20, sh(b, 0.55))   # bipod
    if saw:
        body.rect(17, 12, 19, 15, a)
    mag.rect(13, 16, 20, 24, sh(a, 0.55) if saw else sh(b, 0.5))
    mag.line(13, 19, 19, 19, a); mag.line(13, 22, 19, 22, a)
    if saw:
        mag.line(19, 14, 17, 17, (230, 200, 90))
    return body, mag, glow, A(muzzle=(31, 12), optic=(11, 8), under=(24, 15), mag=(16, 23), module=(13, 12), stock=(1, 14), grip=(23, 14))

def st_launcher(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(2, 9, 26, 15, b)
    body.rect(26, 8, 31, 16, sh(b, 0.7))      # flared end
    body.rect(0, 9, 3, 15, sh(b, 0.6))
    body.rect(10, 5, 18, 8, sh(b, 0.55)); body.p(17, 6, (150, 220, 255))   # sight
    body.rect(12, 15, 15, 21, sh(b, 0.6)); body.rect(19, 15, 21, 18, sh(b, 0.6))
    body.rect(5, 11, 9, 13, a)
    mag.rect(24, 10, 27, 14, a); mag.p(28, 12, (255, 220, 120))
    return body, mag, glow, A(muzzle=(31, 12), optic=(14, 5), under=(20, 15), mag=(25, 12), module=(10, 12), stock=(1, 12), grip=(20, 16))

# ----- sci-fi -----
def st_plasma(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(5, 11, 22, 17, b)
    body.rect(22, 12, 26, 16, sh(b, 0.7))
    body.rect(8, 17, 11, 23, sh(b, 0.6))
    body.rect(3, 12, 5, 16, sh(b, 0.7))
    for x in (26, 28):
        body.rect(x, 10, x + 1, 18, sh(b, 1.15))
        glow.p(x, 14, g)
    glow.rect(10, 13, 17, 15, g); glow.rect(26, 13, 31, 14, g)
    mag.rect(14, 17, 19, 22, sh(b, 0.55)); mag.rect(15, 18, 18, 21, g)
    return body, mag, glow, A(muzzle=(31, 13), optic=(13, 10), under=(22, 17), mag=(16, 21), module=(12, 12), stock=(3, 13), grip=(22, 17))

def st_laser(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(4, 11, 24, 16, b)
    body.rect(24, 12, 29, 15, sh(b, 0.85))
    body.rect(9, 16, 12, 22, sh(b, 0.7))
    body.rect(8, 8, 15, 11, sh(b, 0.8))
    glow.p(11, 9, g); glow.p(12, 9, g)
    glow.rect(12, 13, 22, 14, g); glow.rect(29, 13, 31, 14, g); glow.p(30, 12, g); glow.p(30, 15, g)
    mag.rect(15, 16, 19, 21, sh(b, 0.6)); mag.rect(16, 17, 18, 20, g)
    return body, mag, glow, A(muzzle=(31, 13), optic=(17, 10), under=(23, 16), mag=(17, 20), module=(10, 13), stock=(4, 13), grip=(23, 16))

def st_rail(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(2, 11, 12, 18, b)
    body.rect(12, 10, 31, 12, sh(b, 1.1)); body.rect(12, 15, 31, 17, sh(b, 1.1))
    for x in range(14, 30, 4): body.rect(x, 9, x + 2, 18, sh(b, 0.7))
    glow.rect(12, 13, 31, 14, g)
    glow.rect(4, 13, 10, 16, g)
    body.rect(5, 18, 8, 24, sh(b, 0.6))
    mag.rect(9, 18, 12, 22, sh(b, 0.55)); mag.rect(10, 19, 11, 21, g)
    return body, mag, glow, A(muzzle=(31, 13), optic=(7, 10), under=(18, 17), mag=(10, 21), module=(8, 13), stock=(2, 14), grip=(18, 17))

def st_pulse(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(3, 11, 24, 18, b)
    body.rect(24, 12, 29, 16, sh(b, 0.75))
    body.rect(4, 9, 12, 11, sh(b, 0.9))
    body.rect(16, 18, 19, 23, sh(b, 0.6))
    glow.rect(7, 13, 21, 14, g); glow.rect(7, 16, 21, 17, g); glow.rect(29, 13, 31, 15, g)
    for x in (26, 28): body.line(x, 11, x, 17, sh(b, 1.2))
    mag.rect(10, 18, 13, 24, sh(b, 0.55)); mag.rect(11, 19, 12, 23, g)
    return body, mag, glow, A(muzzle=(31, 14), optic=(8, 9), under=(23, 18), mag=(11, 23), module=(14, 12), stock=(3, 14), grip=(23, 18))

def st_arc(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(4, 12, 18, 18, b)
    body.rect(18, 13, 23, 17, sh(b, 0.75))
    body.rect(8, 18, 11, 24, sh(b, 0.6))
    for i, y in enumerate((10, 14, 18)):
        body.rect(23, y, 29, y + 2, sh(b, 1.2)); glow.disc(30, y + 1, 1.3, g)
    body.rect(6, 8, 14, 12, sh(b, 0.8)); glow.disc(10, 9, 2, g)
    glow.line(24, 11, 29, 14, g); glow.line(24, 19, 29, 15, g)
    mag.rect(14, 18, 18, 23, sh(b, 0.55)); mag.rect(15, 19, 17, 22, g)
    return body, mag, glow, A(muzzle=(31, 14), optic=(14, 8), under=(20, 18), mag=(16, 22), module=(11, 14), stock=(4, 14), grip=(20, 18))

def st_cryo(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(5, 13, 20, 19, b)
    body.rect(20, 12, 24, 20, sh(b, 0.75))
    for i in range(4):
        body.rect(24 + i, 13 - i, 25 + i, 19 + i, sh(b, 1.1 - i * 0.08))
    glow.rect(24, 14, 28, 18, (190, 240, 255)); glow.rect(27, 13, 29, 19, (220, 250, 255))
    tank = Layer()
    mag.rect(7, 6, 18, 13, sh(g, 0.6)); mag.rect(8, 7, 17, 12, g)
    mag.rect(7, 6, 8, 13, sh(g, 0.4)); mag.rect(17, 6, 18, 13, sh(g, 0.4))
    body.rect(8, 19, 11, 25, sh(b, 0.6))
    return body, mag, glow, A(muzzle=(30, 16), optic=(12, 5), under=(19, 20), mag=(12, 10), module=(12, 16), stock=(5, 15), grip=(19, 20))

def st_gravity(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(4, 12, 20, 19, b)
    body.rect(8, 19, 11, 25, sh(b, 0.6))
    glow.ring(24, 15, 6, g, 0.8); glow.ring(24, 15, 3.2, g, 0.7); glow.disc(24, 15, 1.2, (255, 255, 255))
    body.rect(18, 14, 21, 17, sh(b, 0.8))
    mag.rect(11, 8, 17, 12, sh(b, 0.6)); mag.rect(12, 9, 16, 11, g)
    return body, mag, glow, A(muzzle=(30, 15), optic=(12, 8), under=(19, 19), mag=(14, 11), module=(10, 15), stock=(4, 15), grip=(19, 19))

def st_smart(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(3, 12, 24, 19, b)
    body.rect(24, 11, 30, 20, sh(b, 0.75))
    for y in (12, 15, 18): body.rect(24, y, 30, y + 1, OUT)
    body.rect(9, 19, 12, 25, sh(b, 0.6))
    body.disc(13, 9, 4, sh(b, 0.9)); glow.disc(13, 9, 2, g); glow.p(13, 9, (255, 255, 255))
    body.rect(10, 11, 16, 13, sh(b, 0.7))
    glow.rect(20, 14, 24, 16, g)
    mag.rect(15, 19, 20, 24, sh(b, 0.55)); mag.rect(16, 20, 19, 23, g)
    return body, mag, glow, A(muzzle=(30, 15), optic=(13, 5), under=(21, 19), mag=(17, 23), module=(16, 14), stock=(3, 15), grip=(21, 19))

def st_eshotgun(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(2, 12, 9, 18, sh(b, 0.85))
    body.rect(9, 10, 20, 17, b)
    body.rect(20, 9, 30, 18, sh(b, 0.8))
    for x in (22, 25, 28): glow.rect(x, 11, x + 1, 16, g)
    glow.rect(10, 12, 17, 14, g)
    body.rect(11, 17, 14, 22, sh(b, 0.6))
    mag.rect(15, 17, 19, 22, sh(b, 0.55)); mag.rect(16, 18, 18, 21, g)
    return body, mag, glow, A(muzzle=(30, 13), optic=(14, 9), under=(24, 18), mag=(17, 21), module=(13, 14), stock=(2, 14), grip=(24, 18))

def st_charger(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(2, 12, 12, 19, b)
    body.rect(12, 13, 31, 17, sh(b, 0.8))
    body.disc(20, 15, 5, sh(b, 1.1)); glow.disc(20, 15, 3.2, g); glow.disc(20, 15, 1.2, (255, 255, 255))
    for x in (26, 28, 30): body.line(x, 12, x, 18, sh(b, 1.3))
    glow.rect(13, 15, 17, 16, g)
    body.rect(7, 19, 10, 25, sh(b, 0.6))
    mag.rect(3, 9, 9, 12, sh(b, 0.6)); mag.rect(4, 10, 8, 11, g)
    return body, mag, glow, A(muzzle=(31, 15), optic=(8, 9), under=(14, 18), mag=(6, 11), module=(11, 14), stock=(2, 15), grip=(14, 18))

def st_shield(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(4, 13, 16, 19, b)
    body.rect(7, 19, 10, 25, sh(b, 0.6))
    body.disc(21, 15, 7, sh(b, 0.8))
    glow.ring(21, 15, 5.5, g, 0.8); glow.ring(21, 15, 3.2, g, 0.7); glow.disc(21, 15, 1.4, (255, 255, 255))
    body.rect(14, 14, 17, 18, sh(b, 1.1))
    mag.rect(9, 9, 15, 13, sh(b, 0.6)); mag.rect(10, 10, 14, 12, g)
    return body, mag, glow, A(muzzle=(28, 15), optic=(10, 9), under=(18, 19), mag=(12, 11), module=(10, 15), stock=(4, 15), grip=(18, 19))

def st_rift(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(3, 12, 19, 19, b)
    body.rect(8, 19, 11, 25, sh(b, 0.6))
    for r, t in ((7, 0.8), (4.5, 0.7)): glow.ring(24, 15, r, g, t)
    for i in range(10):
        ang = i * 0.7; rad = 1 + i * 0.6
        glow.p(round(24 + math.cos(ang) * rad), round(15 + math.sin(ang) * rad), (255, 230, 255))
    body.rect(18, 14, 20, 17, sh(b, 0.8))
    mag.rect(10, 8, 17, 12, sh(b, 0.6)); mag.rect(11, 9, 16, 11, g)
    return body, mag, glow, A(muzzle=(30, 15), optic=(13, 8), under=(19, 19), mag=(13, 11), module=(9, 15), stock=(3, 15), grip=(19, 19))

def st_cannon(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(0, 11, 10, 20, sh(b, 0.85))
    body.rect(10, 9, 24, 21, b)
    body.rect(24, 11, 31, 19, sh(b, 0.8))
    glow.rect(12, 12, 22, 14, g); glow.rect(12, 16, 22, 18, g); glow.rect(29, 13, 31, 17, g)
    for x in (13, 17, 21): body.line(x, 9, x, 20, sh(b, 0.65))
    body.rect(14, 21, 18, 26, sh(b, 0.6))
    mag.rect(2, 6, 9, 10, sh(b, 0.6)); mag.rect(3, 7, 8, 9, g)
    return body, mag, glow, A(muzzle=(31, 15), optic=(16, 8), under=(21, 21), mag=(5, 8), module=(16, 15), stock=(1, 15), grip=(16, 21))

def st_mortar(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(5, 11, 22, 21, b)
    body.rect(22, 9, 28, 23, sh(b, 0.8)); body.rect(28, 10, 31, 22, sh(b, 0.65))
    glow.rect(7, 13, 20, 14, g); glow.rect(7, 18, 20, 19, g); glow.ring(30, 16, 3, g, 0.6)
    body.rect(1, 20, 8, 24, sh(b, 0.6)); body.rect(14, 21, 20, 26, sh(b, 0.6))
    mag.rect(10, 7, 17, 11, sh(b, 0.6)); mag.rect(11, 8, 16, 10, g)
    return body, mag, glow, A(muzzle=(31, 16), optic=(13, 7), under=(17, 22), mag=(13, 9), module=(10, 15), stock=(2, 16), grip=(17, 22))

def st_prism(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(3, 12, 18, 19, b)
    body.rect(18, 13, 22, 18, sh(b, 0.8))
    # prism triangle
    for i in range(7):
        body.rect(22 + i, 9 + i // 2, 23 + i, 22 - i // 2, (200, 220, 240) if i % 2 else (160, 190, 220))
    glow.line(22, 15, 31, 12, (255, 80, 80)); glow.line(22, 16, 31, 15, (80, 255, 120)); glow.line(22, 17, 31, 18, (90, 120, 255))
    body.rect(8, 19, 11, 25, sh(b, 0.6))
    mag.rect(12, 8, 18, 12, sh(b, 0.6)); mag.rect(13, 9, 17, 11, g)
    return body, mag, glow, A(muzzle=(31, 15), optic=(10, 11), under=(15, 19), mag=(15, 10), module=(8, 15), stock=(3, 15), grip=(15, 19))

def st_turret(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(8, 10, 22, 17, b)
    body.rect(22, 12, 30, 15, sh(b, 0.7))
    body.rect(10, 17, 20, 19, sh(b, 0.6))
    body.line(14, 19, 8, 27, sh(b, 0.55)); body.line(16, 19, 16, 28, sh(b, 0.55)); body.line(18, 19, 25, 27, sh(b, 0.55))
    glow.p(26, 13, g); glow.p(11, 12, g); glow.p(12, 12, g)
    mag.rect(11, 6, 19, 10, sh(b, 0.5))
    return body, mag, glow, A(muzzle=(30, 13), optic=(15, 6), under=(21, 18), mag=(15, 8), module=(14, 13), stock=(8, 13), grip=(21, 18))

def st_unstable(v):
    b, a, g = palette(v); body, mag, glow = new3()
    body.rect(3, 12, 21, 18, b)
    body.rect(21, 11, 25, 17, sh(b, 0.8))
    body.rect(25, 13, 30, 16, sh(b, 0.65))
    body.rect(8, 8, 15, 12, sh(b, 0.7))
    body.rect(9, 18, 12, 24, sh(b, 0.6))
    glow.rect(10, 14, 20, 15, g)
    for (x, y) in ((6, 13), (13, 17), (17, 12), (23, 13), (27, 14)): glow.p(x, y, g)
    glow.line(11, 9, 14, 11, g); glow.line(16, 16, 19, 18, g)
    glow.disc(13, 10, 1, (255, 255, 255))
    mag.rect(14, 18, 19, 23, sh(b, 0.55)); mag.rect(15, 19, 18, 22, g)
    return body, mag, glow, A(muzzle=(30, 14), optic=(18, 11), under=(23, 17), mag=(16, 22), module=(12, 14), stock=(3, 14), grip=(23, 17))

STYLES = {
    "pistol": st_pistol, "machinepistol": st_machinepistol, "revolver": st_revolver,
    "smg": st_smg, "suppsmg": lambda v: st_smg(v, supp=True), "pdw": st_pdw,
    "rifle": st_rifle, "battle": lambda v: st_rifle(v, wood=True, longb=True),
    "dmr": lambda v: st_rifle(v, longb=True, dmr=True), "burst": lambda v: st_rifle(v, stripe=True),
    "modular": lambda v: st_rifle(v, modular=True),
    "sniper": st_sniper, "antimat": lambda v: st_sniper(v, heavy=True),
    "pump": st_pump, "autoshot": st_autoshot, "lmg": st_lmg, "saw": lambda v: st_lmg(v, saw=True),
    "launcher": st_launcher,
    "plasma": st_plasma, "laser": st_laser, "rail": st_rail, "pulse": st_pulse, "arc": st_arc, "cryo": st_cryo,
    "gravity": st_gravity, "smart": st_smart, "eshotgun": st_eshotgun, "charger": st_charger, "shield": st_shield,
    "rift": st_rift, "cannon": st_cannon, "mortar": st_mortar, "prism": st_prism, "turret": st_turret,
    "unstable": st_unstable,
}

FINISH_RAMPS = {
    "gold": ((60, 38, 6), (255, 224, 120)),
    "arctic": ((52, 70, 92), (245, 252, 255)),
    "crimson": ((46, 8, 12), (245, 70, 70)),
}

def remap_finish(layer_img, finish):
    lo, hi = FINISH_RAMPS[finish]
    out = layer_img.copy()
    px = out.load()
    for y in range(W):
        for x in range(W):
            c = px[x, y]
            if c[3] == 0 or (c[0], c[1], c[2]) == OUT[:3]:
                continue
            l = (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255.0
            l = min(1.0, l * 1.35)
            px[x, y] = (int(lo[0] + (hi[0] - lo[0]) * l), int(lo[1] + (hi[1] - lo[1]) * l), int(lo[2] + (hi[2] - lo[2]) * l), 255)
    return out

def glow_bloom(layer):
    """soft emissive look: bright core + faint halo."""
    src = layer.img
    s = src.load()
    out = src.copy()
    o = out.load()
    for y in range(W):
        for x in range(W):
            if s[x, y][3] == 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < W and 0 <= ny < W and s[nx, ny][3] == 0 and o[nx, ny][3] == 0:
                    c = s[x, y]
                    o[nx, ny] = (c[0], c[1], c[2], 90)
    for y in range(W):
        for x in range(W):
            c = s[x, y]
            if c[3] > 0:
                o[x, y] = (min(255, c[0] + 60), min(255, c[1] + 60), min(255, c[2] + 60), 255)
    return out

def flash_layer(muzzle, color, big):
    l = Layer()
    mx, my = muzzle
    r = 3 if big else 2
    for i in range(-r, r + 1):
        l.p(mx + 1 + abs(i) * 0 + (r - abs(i)), my + i, (255, 240, 170))
    l.rect(mx + 1, my - 1, mx + 1 + r + 2, my + 2, color if color else (255, 230, 140))
    l.p(mx + 1 + r + 2, my, (255, 255, 255)); l.p(mx + 1, my - r, (255, 210, 90)); l.p(mx + 1, my + r, (255, 210, 90))
    l.p(mx + 2, my - r - 1, (255, 190, 60)); l.p(mx + 2, my + r + 1, (255, 190, 60))
    return l.img

def build_gun_frames(gun):
    v = gun["vis"]
    body, mag, glow, an = STYLES[v["style"]](v)
    bimg = body.shaded(); mimg = mag.shaded()
    gimg = glow_bloom(glow) if v.get("glow") else glow.img
    is_energy = gun["fam"] in ("energy", "heavy")
    big = gun["cat"] in ("heavy",) or gun["fam"] in ("sniper", "launcher") or gun["id"] in ("pumpjack_12", "autotwelve")
    silent = gun["p"].get("silent", 0) == 1
    fcol = tuple(gun["p"]["glow"]) if "glow" in gun["p"] else None
    frames = {}
    def compose(mag_dx, mag_dy, show_mag, glow_alpha, flash, finish=None):
        img = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        mi, bi = mimg, bimg
        if finish:
            mi, bi = remap_finish(mimg, finish), remap_finish(bimg, finish)
        if show_mag:
            paste(img, mi, mag_dx, mag_dy)
        paste(img, bi)
        paste(img, gimg, 0, 0, glow_alpha)
        if flash and not silent:
            paste(img, flash_layer(an["muzzle"], fcol if is_energy else None, big))
        return img
    for finish in (None, "gold", "arctic", "crimson"):
        suffix = "" if finish is None else "_" + finish
        frames["base" + suffix] = compose(0, 0, True, 1.0, False, finish)
        frames["fire" + suffix] = compose(0, 0, True, 1.0, True, finish)
        frames["reload1" + suffix] = compose(0, 5, True, 0.6, False, finish)
        frames["reload2" + suffix] = compose(0, 9, False, 0.3, False, finish)
    return frames, an

# ----------------------------------------------------------------------------------------------------
# attachment overlays (drawn at the gun's anchors) and standalone icons
# ----------------------------------------------------------------------------------------------------
def att_overlay(att_id, an, gun=None):
    l = Layer()
    mx, my = an["muzzle"]; ox, oy = an["optic"]; ux, uy = an["under"]
    gx, gy = an.get("grip", an["under"]); sx, sy = an["stock"]; kx, ky = an["mag"]; dx, dy = an["module"]
    if att_id == "red_dot":
        l.rect(ox - 2, oy - 3, ox + 3, oy - 1, (50, 52, 58)); l.rect(ox - 2, oy - 5, ox + 3, oy - 3, (60, 64, 72)); l.p(ox, oy - 4, (255, 60, 60))
        l.rect(ox - 1, oy - 1, ox + 2, oy, (40, 40, 44))
    elif att_id == "holo_sight":
        l.rect(ox - 3, oy - 1, ox + 4, oy, (44, 46, 52)); l.rect(ox - 3, oy - 6, ox - 2, oy - 1, (60, 64, 72)); l.rect(ox + 3, oy - 6, ox + 4, oy - 1, (60, 64, 72))
        l.rect(ox - 2, oy - 6, ox + 3, oy - 5, (60, 64, 72)); l.p(ox, oy - 3, (255, 90, 90)); l.p(ox, oy - 2, (255, 90, 90)); l.p(ox - 1, oy - 3, (255, 90, 90)); l.p(ox + 1, oy - 3, (255, 90, 90))
    elif att_id == "scope_4x":
        l.rect(ox - 5, oy - 5, ox + 6, oy - 2, (38, 40, 46)); l.rect(ox - 6, oy - 6, ox - 4, oy - 1, (50, 52, 60)); l.rect(ox + 5, oy - 6, ox + 7, oy - 1, (50, 52, 60))
        l.p(ox - 6, oy - 4, (140, 210, 255)); l.p(ox + 7, oy - 4, (140, 210, 255)); l.rect(ox - 1, oy - 2, ox + 1, oy, (38, 40, 46))
    elif att_id == "scope_8x":
        l.rect(ox - 7, oy - 6, ox + 8, oy - 3, (30, 32, 38)); l.rect(ox - 8, oy - 7, ox - 6, oy - 2, (46, 48, 56)); l.rect(ox + 7, oy - 7, ox + 10, oy - 2, (46, 48, 56))
        l.p(ox - 8, oy - 5, (150, 230, 255)); l.p(ox + 10, oy - 5, (150, 230, 255)); l.rect(ox - 4, oy - 3, ox - 2, oy, (30, 32, 38)); l.rect(ox + 4, oy - 3, ox + 6, oy, (30, 32, 38))
        l.rect(ox - 1, oy - 8, ox + 2, oy - 7, (60, 60, 66))
    elif att_id == "suppressor":
        l.rect(mx - 1, my - 2, mx + 7, my + 2, (46, 48, 54))
        for x in range(mx, mx + 7, 2): l.line(x, my - 2, x, my + 1, (30, 30, 34))
    elif att_id == "muzzle_brake":
        l.rect(mx - 2, my - 2, mx + 2, my + 2, (70, 72, 80)); l.p(mx - 1, my - 2, OUT[:3]); l.p(mx + 1, my - 2, OUT[:3]); l.p(mx - 1, my + 1, OUT[:3]); l.p(mx + 1, my + 1, OUT[:3])
    elif att_id == "focus_lens":
        l.rect(mx - 2, my - 3, mx + 1, my + 3, (60, 70, 90)); l.rect(mx + 1, my - 2, mx + 3, my + 2, (140, 220, 255)); l.p(mx + 3, my, (255, 255, 255))
    elif att_id == "extended_mag":
        l.rect(kx - 1, ky - 3, kx + 3, ky + 5, (60, 62, 68)); l.line(kx - 1, ky + 1, kx + 2, ky + 1, (200, 180, 60))
    elif att_id == "drum_mag":
        l.disc(kx + 1, ky + 3, 5, (62, 64, 70)); l.ring(kx + 1, ky + 3, 3, (110, 112, 120), 0.6); l.p(kx + 1, ky + 3, (200, 180, 60))
    elif att_id == "quickdraw_mag":
        l.rect(kx - 2, ky - 3, kx + 3, ky + 2, (60, 62, 68)); l.rect(kx - 3, ky + 1, kx + 4, ky + 3, (200, 80, 60))
    elif att_id == "foregrip":
        l.rect(ux - 1, uy, ux + 2, uy + 6, (60, 60, 64)); l.line(ux - 1, uy + 2, ux + 1, uy + 2, (30, 30, 34)); l.line(ux - 1, uy + 4, ux + 1, uy + 4, (30, 30, 34))
    elif att_id == "stabilizer":
        l.rect(ux - 2, uy, ux + 3, uy + 3, (70, 80, 100)); l.rect(ux - 1, uy + 3, ux + 2, uy + 7, (60, 60, 66)); l.p(ux, uy + 1, (80, 220, 255))
    elif att_id == "heavy_stock":
        l.rect(sx - 2, sy - 1, sx + 3, sy + 6, (70, 70, 78)); l.rect(sx - 2, sy - 3, sx + 1, sy - 1, (70, 70, 78)); l.line(sx - 2, sy + 2, sx + 2, sy + 2, (40, 40, 44))
    elif att_id == "skeleton_stock":
        l.line(sx - 3, sy, sx + 2, sy, (70, 70, 78)); l.line(sx - 3, sy + 4, sx + 2, sy + 4, (70, 70, 78)); l.line(sx - 3, sy, sx - 3, sy + 4, (70, 70, 78))
    elif att_id == "laser_sight":
        l.rect(ux - 1, uy, ux + 4, uy + 3, (50, 52, 58)); l.p(ux + 4, uy + 1, (255, 40, 40)); l.line(ux + 5, uy + 1, ux + 7, uy + 1, (255, 80, 80))
    elif att_id == "flashlight":
        l.rect(ux - 1, uy, ux + 4, uy + 3, (60, 62, 68)); l.rect(ux + 4, uy, ux + 5, uy + 3, (255, 240, 150)); l.p(ux + 6, uy + 1, (255, 250, 200))
    elif att_id == "amp_module":
        l.rect(dx - 2, dy - 2, dx + 3, dy + 3, (60, 64, 80)); l.rect(dx - 1, dy - 1, dx + 2, dy + 2, (255, 120, 60)); l.p(dx, dy, (255, 240, 190))
    elif att_id == "overclock":
        l.rect(dx - 2, dy - 2, dx + 3, dy + 3, (60, 64, 80)); l.line(dx - 1, dy - 1, dx + 1, dy + 1, (255, 230, 80)); l.line(dx + 1, dy - 1, dx - 1, dy + 1, (255, 230, 80))
    elif att_id == "heatsink":
        for i in range(4): l.rect(dx - 3 + i * 2, dy - 4, dx - 2 + i * 2, dy + 1, (150, 156, 170))
        l.rect(dx - 3, dy + 1, dx + 4, dy + 2, (110, 116, 130))
    elif att_id == "cell_extender":
        l.rect(dx - 3, dy - 2, dx + 4, dy + 3, (50, 56, 70)); l.rect(dx - 2, dy - 1, dx + 3, dy + 2, (110, 255, 200)); l.p(dx, dy, (255, 255, 255))
    return l.img

def att_icon(att, size=32):
    an = dict(muzzle=(12, 16), optic=(16, 20), under=(13, 12), grip=(13, 12), stock=(18, 14), mag=(14, 10), module=(16, 16))
    base = att_overlay(att["id"], an)
    if att["slot"] == "finish":
        fin = att["finish"]
        lo, hi = FINISH_RAMPS[fin]
        l = Layer()
        l.rect(6, 6, 26, 26, rgba(lo)); 
        for i in range(8):
            l.rect(7 + i, 7, 8 + i, 25, rgba((int(lo[0] + (hi[0] - lo[0]) * i / 7), int(lo[1] + (hi[1] - lo[1]) * i / 7), int(lo[2] + (hi[2] - lo[2]) * i / 7))))
        l.rect(16, 7, 25, 25, rgba(hi)); l.disc(20, 12, 3, rgba(sh(hi, 1.2)))
        return l.shaded()
    lay = Layer(); lay.img = base; lay.px = base.load()
    # enlarge 2x for icon readability
    big = base.resize((W * 2, W * 2), Image.NEAREST)
    bb = big.getbbox()
    if bb:
        crop = big.crop(bb)
        cw, ch = crop.size
        s = min(26 / cw, 26 / ch, 2.0)
        crop = crop.resize((max(1, int(cw * s)), max(1, int(ch * s))), Image.NEAREST)
        icon = Image.new("RGBA", (W, W), (0, 0, 0, 0))
        icon.paste(crop, ((W - crop.width) // 2, (W - crop.height) // 2), crop)
        l2 = Layer(); l2.img = icon; l2.px = icon.load()
        return l2.shaded()
    return base

def ammo_icon(a, special=False):
    c = a["color"]
    l = Layer()
    if a["id"] == "ammo_cell":
        l.rect(11, 7, 21, 27, (60, 66, 80)); l.rect(12, 9, 20, 25, rgba(c)); l.rect(13, 10, 19, 24, rgba(sh(c, 1.15)))
        l.rect(13, 5, 19, 7, (140, 146, 160)); l.rect(14, 4, 18, 5, (200, 206, 220))
        sp = l.shaded(); return sp
    if a["id"] == "ammo_core":
        l.disc(16, 16, 9, (60, 50, 70)); l.disc(16, 16, 6.5, rgba(c)); l.disc(16, 16, 3, (255, 245, 255)); l.ring(16, 16, 9, (120, 110, 140), 0.6)
        return l.shaded()
    if a["id"] == "ammo_rocket":
        for dy in (0, 9):
            l.rect(5, 8 + dy, 24, 13 + dy, (90, 96, 70)); l.rect(24, 9 + dy, 28, 12 + dy, rgba(c)); l.rect(3, 7 + dy, 6, 14 + dy, (60, 64, 52)); l.p(29, 10 + dy, (255, 220, 120))
        return l.shaded()
    # ammo box with bullets
    l.rect(4, 14, 28, 27, (86, 92, 60) if not special else (60, 64, 76)); l.rect(4, 14, 28, 17, (110, 118, 80) if not special else (84, 90, 104))
    l.rect(9, 19, 23, 24, rgba(c))
    for i in range(5):
        l.rect(6 + i * 4, 6, 8 + i * 4, 14, rgba(sh(c, 1.1))); l.rect(6 + i * 4, 4, 8 + i * 4, 6, (190, 120, 70))
    if special:
        l.rect(4, 22, 28, 24, rgba(c)); l.p(15, 20, (255, 255, 255))
    return l.shaded()

def misc_icons():
    out = {}
    # credit chip
    l = Layer(); l.disc(16, 16, 11, (210, 175, 60)); l.disc(16, 16, 9, (240, 205, 80)); l.ring(16, 16, 7, (170, 135, 40), 0.7)
    l.rect(14, 11, 18, 21, (170, 135, 40)); l.rect(12, 13, 20, 15, (170, 135, 40)); l.rect(12, 17, 20, 19, (170, 135, 40))
    out["credit_chip"] = l.shaded()
    # clue slip
    l = Layer(); l.rect(7, 5, 25, 27, (226, 214, 180)); l.rect(7, 5, 9, 27, (206, 192, 156))
    for y in (9, 12, 15, 18): l.rect(11, y, 22, y + 1, (120, 100, 70))
    l.rect(11, 21, 16, 22, (180, 40, 40)); l.p(24, 6, (0, 0, 0)); l.p(23, 7, (0, 0, 0)); l.p(7, 27, (0, 0, 0)); l.p(8, 26, (0, 0, 0))
    out["clue_slip"] = l.shaded(outline=True)
    # pocket handset
    l = Layer(); l.rect(10, 8, 22, 28, (46, 50, 58)); l.rect(11, 9, 21, 27, (60, 66, 76)); l.rect(12, 11, 20, 17, (20, 60, 44)); l.rect(13, 12, 19, 13, (110, 255, 190)); l.rect(13, 14, 17, 15, (110, 255, 190))
    for ky in range(3):
        for kx in range(3): l.rect(12 + kx * 3, 19 + ky * 3, 14 + kx * 3, 21 + ky * 3, (200, 204, 212))
    l.rect(19, 3, 21, 8, (46, 50, 58)); l.p(20, 2, (255, 70, 60))
    out["handheld_phone"] = l.shaded()
    # receipt
    l = Layer(); l.rect(8, 4, 24, 28, (240, 238, 228)); 
    for i in range(0, 16, 2): l.p(8 + i, 28, (240, 238, 228)); l.p(9 + i, 29, (0, 0, 0, 0)[:3] if False else (240, 238, 228))
    for y in (8, 11, 14, 17, 20): l.rect(10, y, 22, y + 1, (110, 110, 120))
    l.rect(10, 23, 16, 25, (30, 30, 36)); l.rect(18, 22, 23, 26, (200, 40, 40))
    out["order_receipt"] = l.shaded()
    # drone / crate / pod / container / case icons are 3d models - no sprite needed
    # projectiles
    def orb(col, name, r=6):
        l = Layer(); l.disc(16, 16, r + 3, rgba(sh(col, 0.5))); l.disc(16, 16, r, rgba(col)); l.disc(16, 16, r - 3, (255, 255, 255)); out[name] = l.img
    orb((80, 255, 200), "proj_plasma"); orb((255, 150, 60), "proj_shard", 4); orb((255, 255, 120), "proj_charge", 8); orb((255, 110, 60), "proj_heavy", 9)
    orb((190, 90, 255), "proj_rift", 7); orb((110, 210, 255), "proj_emp", 7); orb((255, 90, 200), "proj_missile", 4)
    l = Layer(); l.rect(6, 14, 22, 18, (96, 100, 80)); l.rect(22, 13, 26, 19, (220, 80, 40)); l.rect(4, 12, 7, 20, (60, 64, 52)); l.p(27, 16, (255, 230, 150))
    l.rect(1, 15, 4, 17, (255, 200, 90)); out["proj_rocket"] = l.shaded()
    # sentinel turret item inventory image reuses gun art
    return out

if __name__ == "__main__":
    import sys, os
    sys.path.insert(0, os.path.dirname(__file__))
    import catalog_data as cd
    sheet = Image.new("RGBA", (W * 8, W * 5), (90, 100, 110, 255))
    for i, g in enumerate(cd.GUNS):
        fr, an = build_gun_frames(g)
        sheet.paste(fr["base"], ((i % 8) * W, (i // 8) * W), fr["base"])
    sheet = sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST)
    sheet.save("/tmp/gun_sheet.png")
    print("ok")
