"""Generates the procedural textures used by Wandcraft (particles, beam, wand atlas). Run from wandcraft-mod/."""
import math, random
from PIL import Image, ImageDraw, ImageFilter

A = 'src/main/resources/assets/wandcraft/textures/'
random.seed(7)

def save(im, p): im.save(A + p)

# --- particles (white, tinted at runtime) ---------------------------------------------------------------------------
def glow(n=32):
    im = Image.new('RGBA', (n, n)); px = im.load(); c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / c
            a = max(0.0, 1 - d) ** 2.2
            core = max(0.0, 1 - d * 2.2) ** 1.5
            px[x, y] = (255, 255, 255, int(255 * min(1, a * 0.85 + core)))
    return im

def spark(n=32):
    im = Image.new('RGBA', (n, n)); px = im.load(); c = (n - 1) / 2
    for y in range(n):
        for x in range(n):
            dx, dy = abs(x - c) / c, abs(y - c) / c
            arm = max(0.0, 1 - min(dx, dy) * 7) * max(0.0, 1 - max(dx, dy)) ** 1.3
            d = math.hypot(dx, dy)
            core = max(0.0, 1 - d * 2.5) ** 1.4
            diag = max(0.0, 1 - abs(dx - dy) * 9) * max(0.0, 1 - d) ** 1.8 * 0.5
            px[x, y] = (255, 255, 255, int(255 * min(1, arm + core + diag)))
    return im

def wisp(n=32):
    im = Image.new('RGBA', (n, n)); px = im.load(); c = (n - 1) / 2
    noise = [[random.random() for _ in range(n)] for _ in range(n)]
    for y in range(n):
        for x in range(n):
            d = math.hypot(x - c, y - c) / c
            base = max(0.0, 1 - d) ** 1.4
            nv = 0.55 + 0.45 * noise[y][x]
            px[x, y] = (255, 255, 255, int(255 * base * nv * 0.8))
    return im.filter(ImageFilter.GaussianBlur(0.8))

def rune(n=128):
    im = Image.new('RGBA', (n, n), (0, 0, 0, 0)); d = ImageDraw.Draw(im)
    c = n / 2; W = (255, 255, 255, 255); S = (255, 255, 255, 150)
    d.ellipse([2, 2, n - 3, n - 3], outline=W, width=3)
    d.ellipse([9, 9, n - 10, n - 10], outline=S, width=1)
    d.ellipse([20, 20, n - 21, n - 21], outline=W, width=2)
    pts = [(c + math.cos(-math.pi / 2 + i * 4 * math.pi / 5) * (c - 22), c + math.sin(-math.pi / 2 + i * 4 * math.pi / 5) * (c - 22)) for i in range(6)]
    d.line(pts, fill=W, width=2)
    for i in range(24):
        a = i * math.tau / 24
        r1, r2 = c - 10, c - 3 - (3 if i % 2 == 0 else 0)
        d.line([c + math.cos(a) * r1, c + math.sin(a) * r1, c + math.cos(a) * r2, c + math.sin(a) * r2], fill=S, width=1)
    for i in range(8):  # small glyph ticks between the rings
        a = i * math.tau / 8 + math.pi / 8
        gx, gy = c + math.cos(a) * (c - 15), c + math.sin(a) * (c - 15)
        d.line([gx - 2, gy - 2, gx + 2, gy + 2], fill=W, width=1)
        d.line([gx - 2, gy + 2, gx + 1, gy - 1], fill=S, width=1)
    d.ellipse([c - 5, c - 5, c + 5, c + 5], outline=W, width=2)
    return im.filter(ImageFilter.GaussianBlur(0.4))

save(glow(), 'particle/glow.png'); save(spark(), 'particle/spark.png')
save(wisp(), 'particle/wisp.png'); save(rune(), 'particle/rune.png')

# --- beam: soft-edged vertical energy streaks (tinted at runtime) --------------------------------------------------------
def beam(n=32):
    im = Image.new('RGBA', (n, n)); px = im.load()
    for y in range(n):
        for x in range(n):
            u = x / (n - 1)
            edge = math.sin(u * math.pi) ** 0.35
            streak = 0.82 + 0.18 * math.sin(y / n * math.tau * 2 + math.sin(u * 9) * 1.5)
            px[x, y] = (255, 255, 255, int(255 * edge * streak))
    return im
save(beam(), 'entity/arcane_beam.png')

# --- wand atlas: 32x32, 4x4 grid of 8x8 swatches, 4 animation frames stacked vertically ----------------------------------
def swatch(base, var=14, pattern=None):
    im = Image.new('RGBA', (8, 8)); px = im.load()
    for y in range(8):
        for x in range(8):
            v = random.randint(-var, var)
            if pattern == 'grain': v += (-18 if (y % 3 == 0) else 6)
            if pattern == 'wrap': v += (-22 if ((x + y) % 4 == 0) else 10)
            if pattern == 'metal': v += int(26 * (1 - y / 7)) - 10
            if pattern == 'facet': v += (24 if x + y < 6 else -20 if x + y > 9 else 0)
            px[x, y] = tuple(max(0, min(255, c + v)) for c in base) + (255,)
    return im

def frame(f):
    pulse = 0.5 + 0.5 * math.sin(f * math.pi / 2)
    atlas = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    gem = (int(120 + 90 * pulse), int(110 + 70 * pulse), 255)
    core = (int(200 + 55 * pulse), int(215 + 40 * pulse), 255)
    sw = [
        swatch((92, 58, 34), 10, 'grain'),   # 0 bark
        swatch((132, 90, 52), 10, 'grain'),  # 1 light wood
        swatch((226, 178, 72), 8, 'metal'),  # 2 gold
        swatch((150, 108, 40), 8, 'metal'),  # 3 dark gold
        swatch((74, 44, 120), 8, 'wrap'),    # 4 violet wrap
        swatch(gem, 10, 'facet'),            # 5 gem
        swatch(core, 6, None),               # 6 gem core
        swatch((205, 210, 225), 8, 'metal'), # 7 silver
        swatch((60, 36, 24), 8, 'grain'),    # 8 dark root
        swatch((140, 255, 200), 6, None),    # 9 green leaf glow (unused accent)
        swatch((255, 255, 255), 0, None),    # 10 white
        swatch(tuple(int(c * 0.55) for c in gem), 8, 'facet'),  # 11 shadowed gem
    ]
    for i, s in enumerate(sw): atlas.paste(s, ((i % 4) * 8, (i // 4) * 8))
    return atlas
strip = Image.new('RGBA', (32, 128))
for f in range(4): strip.paste(frame(f), (0, 32 * f))
save(strip, 'item/wand.png')
# flat inventory-style icon is not needed: the wand is a 3D model in every context

# --- summoned fighters: 64x64 player-layout skins ---------------------------------------------------------------------------
def skin(base, accent, eye):
    im = Image.new('RGBA', (64, 64), base + (255,)); px = im.load()
    for y in range(64):
        for x in range(64):
            v = random.randint(-8, 8) + (10 if (x // 4 + y // 4) % 2 else 0)
            px[x, y] = tuple(max(0, min(255, c + v)) for c in base) + (255,)
    for (x, y) in [(10, 12), (11, 12), (13, 12), (14, 12), (10, 13), (14, 13)]:
        px[x, y] = eye + (255,)
    for y in range(20, 32):
        px[22, y] = accent + (255,); px[23, y] = accent + (255,)
    return im
save(skin((14, 8, 24), (90, 40, 160), (205, 130, 255)), 'entity/shadow_clone.png')
save(skin((170, 215, 235), (230, 250, 255), (255, 255, 255)), 'entity/spectral_warrior.png')
print('ok')
