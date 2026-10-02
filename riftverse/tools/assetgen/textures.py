"""Procedural textures: blocks, items, armour layers, particles and effect sprites.

Everything is deterministic (seeded). Block textures are generated from tileable noise / Voronoi fields; item icons are
hand-drawn pixel art (ASCII maps + palettes) so they read cleanly at 16x16. Glowing parts of blocks are written as
separate "<name>_glow" overlays which the block models render full-bright.
"""
import numpy as np
from PIL import Image

from .common import ASSETS

N = 16


# ---------------------------------------------------------------------------------------------------------- utilities

def hexc(c):
    return np.array([((c >> 16) & 255) / 255.0, ((c >> 8) & 255) / 255.0, (c & 255) / 255.0])


def value_noise(size, cells, seed):
    """Tileable value noise in [0, 1]."""
    r = np.random.default_rng(seed)
    cells = max(1, min(cells, size))
    grid = r.random((cells, cells))
    coords = np.arange(size) * cells / size
    i0 = np.floor(coords).astype(int) % cells
    i1 = (i0 + 1) % cells
    t = coords - np.floor(coords)
    t = t * t * (3 - 2 * t)
    g00 = grid[np.ix_(i0, i0)]
    g10 = grid[np.ix_(i0, i1)]
    g01 = grid[np.ix_(i1, i0)]
    g11 = grid[np.ix_(i1, i1)]
    tx = t[None, :]
    ty = t[:, None]
    a = g00 * (1 - tx) + g10 * tx
    b = g01 * (1 - tx) + g11 * tx
    return a * (1 - ty) + b * ty


def fbm(size, cells, octaves, seed, gain=0.5):
    total = np.zeros((size, size))
    amp = 1.0
    norm = 0.0
    for o in range(octaves):
        total += value_noise(size, cells * (2 ** o), seed + o * 101) * amp
        norm += amp
        amp *= gain
    total /= norm
    lo, hi = total.min(), total.max()
    return (total - lo) / (hi - lo + 1e-9)


def voronoi(size, count, seed):
    """Periodic Voronoi: returns (f1, f2, index) per pixel, distances in pixels."""
    r = np.random.default_rng(seed)
    pts = r.random((count, 2)) * size
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    best1 = np.full((size, size), 1e9)
    best2 = np.full((size, size), 1e9)
    idx = np.zeros((size, size), dtype=int)
    for k, (px, py) in enumerate(pts):
        dx = np.abs(xs + 0.5 - px)
        dy = np.abs(ys + 0.5 - py)
        dx = np.minimum(dx, size - dx)
        dy = np.minimum(dy, size - dy)
        d = np.sqrt(dx * dx + dy * dy)
        closer = d < best1
        best2 = np.where(closer, best1, np.minimum(best2, d))
        idx = np.where(closer, k, idx)
        best1 = np.where(closer, d, best1)
    return best1, best2, idx


def ramp(v, stops):
    """Map a scalar field through colour stops [(pos, 0xRRGGBB), ...]."""
    pos = np.array([s[0] for s in stops])
    cols = np.array([hexc(s[1]) for s in stops])
    out = np.zeros(v.shape + (3,))
    for c in range(3):
        out[..., c] = np.interp(v, pos, cols[:, c])
    return out


def rgba(rgb_arr, alpha=1.0):
    a = np.ones(rgb_arr.shape[:2]) * alpha if np.isscalar(alpha) else alpha
    return np.dstack([rgb_arr, a])


def empty(h=N, w=N):
    return np.zeros((h, w, 4))


def paint(img, mask, color, alpha=1.0):
    col = hexc(color) if isinstance(color, int) else color
    m = mask.astype(float) * alpha
    for c in range(3):
        img[..., c] = img[..., c] * (1 - m) + (col[c] if np.ndim(col) == 1 else col[..., c]) * m
    img[..., 3] = np.maximum(img[..., 3], m)
    return img


def bevel(img, light=0.12, dark=0.18):
    """Classic block bevel: brighten top/left edge, darken bottom/right edge."""
    out = img.copy()
    out[0, :, :3] += light
    out[:, 0, :3] += light
    out[-1, :, :3] -= dark
    out[:, -1, :3] -= dark
    return out


def glow_falloff(size, radius, power=2.0, cx=None, cy=None):
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    cx = size / 2 if cx is None else cx
    cy = size / 2 if cy is None else cy
    d = np.sqrt((xs + 0.5 - cx) ** 2 + (ys + 0.5 - cy) ** 2) / radius
    return np.clip(1 - d, 0, 1) ** power


def save(img, rel, preview=None):
    path = ASSETS / "textures" / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    arr = np.clip(img, 0, 1)
    Image.fromarray((arr * 255 + 0.5).astype(np.uint8), "RGBA").save(path)
    if preview is not None:
        preview.append((rel, arr))


def save_animated(frames, rel, frametime, preview=None, interpolate=False):
    strip = np.concatenate(frames, axis=0)
    save(strip, rel, None)
    if preview is not None:
        preview.append((rel, frames[0]))
    meta = ASSETS / "textures" / (rel + ".mcmeta")
    import json
    anim = {"frametime": frametime}
    if interpolate:
        anim["interpolate"] = True
    meta.write_text(json.dumps({"animation": anim}, indent=2) + "\n")


def mask_from(art):
    return np.array([[ch != "." for ch in row] for row in art])


# ------------------------------------------------------------------------------------------------------ block textures

def tex_nexus_stone():
    n = fbm(N, 2, 3, 11)
    base = ramp(n, [(0, 0x14151E), (0.6, 0x1F2130), (1, 0x2B2E40)])
    img = rgba(base)
    r = np.random.default_rng(12)
    for _ in range(4):
        x, y = r.integers(0, N, 2)
        img[y, x, :3] = hexc(0x3FC6D8) * 0.7
    return bevel(img, 0.04, 0.06)


def bricks(seed, stops, mortar, rows=4, offset=8, wide=16):
    img = empty()
    n = fbm(N, 4, 2, seed)
    base = ramp(n, stops)
    img[..., :3] = base
    img[..., 3] = 1
    for y in range(N):
        row = y // rows
        if y % rows == rows - 1:
            img[y, :, :3] = hexc(mortar)
            continue
        shift = (row % 2) * offset
        for x in range(N):
            if (x + shift) % wide == wide - 1 or (wide < 16 and (x + shift) % (wide // 1) == wide - 1):
                img[y, x, :3] = hexc(mortar)
            elif y % rows == 0:
                img[y, x, :3] += 0.05
    return img


def tex_nexus_bricks():
    img = bricks(21, [(0, 0x1E2032), (1, 0x343852)], 0x0B0B14, rows=4, offset=4, wide=8)
    return img


def tex_nexus_glow(frame=0, frames=1):
    phase = 0.5 + 0.5 * np.cos(2 * np.pi * frame / frames)
    img = rgba(ramp(fbm(N, 2, 2, 31), [(0, 0x10121C), (1, 0x1C2030)]))
    glow = empty()
    ys, xs = np.mgrid[0:N, 0:N]
    lattice = ((xs % 8 == 3) | (xs % 8 == 4) | (ys % 8 == 3) | (ys % 8 == 4))
    diamond = (np.abs(xs - 7.5) + np.abs(ys - 7.5)) < 4.6
    inner = lattice | diamond
    edge = (xs == 0) | (ys == 0) | (xs == N - 1) | (ys == N - 1)
    img[edge, :3] = hexc(0x2A3048)
    col = ramp(glow_falloff(N, 11, 1.0), [(0, 0x1FA8C8), (1, 0xB8FFFF)]) * (0.75 + 0.25 * phase)
    glow[..., :3] = col
    glow[..., 3] = inner & ~edge
    return img, glow


def tex_void_stone():
    n = fbm(N, 2, 3, 41)
    ridge = 1 - np.abs(fbm(N, 2, 2, 42) * 2 - 1)
    base = ramp(n, [(0, 0x08060E), (0.7, 0x150F22), (1, 0x22183A)])
    base += hexc(0x5A2AA8) * (np.clip(ridge - 0.86, 0, 1) * 4)[..., None]
    return bevel(rgba(base), 0.03, 0.05)


def crystal(seed, dark, mid, light, glow_col, count=7):
    f1, f2, idx = voronoi(N, count, seed)
    shade = (idx * 0.37 % 1.0) * 0.6 + 0.2
    edge = (f2 - f1) < 0.9
    base = ramp(shade, [(0, dark), (0.5, mid), (1, light)])
    base[edge] = hexc(dark) * 0.7
    img = rgba(base)
    glow = empty()
    core = f1 < 1.3
    glow[..., :3] = hexc(glow_col)
    glow[..., 3] = core * 0.85
    sparkle = np.random.default_rng(seed + 1).random((N, N)) > 0.95
    glow[sparkle, :3] = 1.0
    glow[sparkle, 3] = 1.0
    return img, glow


def tex_neon_panel(color):
    img = rgba(np.ones((N, N, 3)) * hexc(0x0E0E16))
    ys, xs = np.mgrid[0:N, 0:N]
    inner = (xs >= 2) & (xs <= 13) & (ys >= 2) & (ys <= 13)
    img[inner, :3] = hexc(0x15151F)
    frame = ((xs == 1) | (xs == 14) | (ys == 1) | (ys == 14)) & (xs >= 1) & (xs <= 14) & (ys >= 1) & (ys <= 14)
    img[frame, :3] = hexc(0x2A2A3A)
    glow = empty()
    tubes = inner & ((ys == 5) | (ys == 10)) & (xs >= 3) & (xs <= 12)
    tubes |= inner & ((xs == 3) | (xs == 12)) & ((ys == 3) | (ys == 12))
    glow[..., :3] = hexc(color)
    glow[tubes, :3] = np.clip(hexc(color) * 0.6 + 0.45, 0, 1)
    glow[..., 3] = tubes
    halo = inner & ~tubes & ((np.abs(ys - 5) <= 1) | (np.abs(ys - 10) <= 1))
    img[halo & (xs >= 3) & (xs <= 12), :3] = hexc(color) * 0.25
    return img, glow


def tex_cyber_plating():
    n = fbm(N, 4, 2, 61)
    img = rgba(ramp(n, [(0, 0x3A4252), (1, 0x58627A)]))
    for y in range(N):
        for x in range(N):
            if x % 8 == 7 or y % 8 == 7:
                img[y, x, :3] = hexc(0x1C2029)
            elif x % 8 == 0 or y % 8 == 0:
                img[y, x, :3] += 0.08
    for (x, y) in [(1, 1), (5, 1), (1, 5), (5, 5), (9, 9), (13, 9), (9, 13), (13, 13), (9, 1), (13, 5), (1, 9), (5, 13)]:
        img[y, x, :3] = hexc(0x8C96AC)
    scratch = np.random.default_rng(62).random((N, N)) > 0.93
    img[scratch, :3] += 0.07
    return img


def tex_cyber_glass():
    img = empty()
    ys, xs = np.mgrid[0:N, 0:N]
    img[..., :3] = hexc(0x7FE8FF)
    img[..., 3] = 0.18
    edge = (xs == 0) | (ys == 0) | (xs == N - 1) | (ys == N - 1)
    img[edge, :3] = hexc(0x2E4A60)
    img[edge, 3] = 0.95
    corner = ((xs <= 2) & (ys <= 2)) | ((xs >= 13) & (ys >= 13))
    img[corner & ~edge, :3] = hexc(0x9FFFFF)
    img[corner & ~edge, 3] = 0.6
    glint = (xs + ys == 9) | (xs + ys == 11)
    img[glint & ~edge, :3] = 1
    img[glint & ~edge, 3] = 0.45
    return img


def tex_alien_moss():
    n = fbm(N, 3, 3, 71)
    img = rgba(ramp(n, [(0, 0x0F3B3A), (0.5, 0x1E6B5A), (1, 0x3FB28A)]))
    glow = empty()
    spots = np.random.default_rng(72).random((N, N)) > 0.94
    glow[..., :3] = hexc(0x8FFFD8)
    glow[..., 3] = spots
    return img, glow


def tex_alien_soil():
    n = fbm(N, 3, 3, 81)
    img = rgba(ramp(n, [(0, 0x24152A), (0.6, 0x3A2440), (1, 0x553560)]))
    f1, f2, idx = voronoi(N, 9, 82)
    pebble = f1 < 1.0
    img[pebble, :3] = hexc(0x6A4A78)
    return img


def tex_glowcap():
    art = [
        "................",
        "................",
        "....oooooooo....",
        "...o33444333o...",
        "..o3344444433o..",
        "..o2233333322o..",
        "...oo222222oo...",
        ".....oo11oo.....",
        "......o11o......",
        "......o11o......",
        "......o11o......",
        ".......11o..o...",
        "..o...o11o.o3o..",
        ".o3o..o11o..oo..",
        "..o...o11o......",
        "................",
    ]
    pal = {"o": 0x0E3A2E, "1": 0xC8E8D8, "2": 0x29C78F, "3": 0x5CFFB0, "4": 0xD8FFEE}
    return from_art(art, pal)


def tex_lumen_log_side():
    ys, xs = np.mgrid[0:N, 0:N]
    n = fbm(N, 2, 2, 91)
    stripes = value_noise(N, 8, 92)
    v = 0.6 * stripes[0][xs] + 0.4 * n
    img = rgba(ramp(v, [(0, 0x1E1030), (0.6, 0x3A2058), (1, 0x5A3480)]))
    glow = empty()
    veins = (xs % 5 == 2) & (value_noise(N, 4, 93) > 0.45)
    glow[..., :3] = hexc(0x6FF0FF)
    glow[..., 3] = veins * 0.9
    return img, glow


def tex_lumen_log_top():
    ys, xs = np.mgrid[0:N, 0:N]
    d = np.sqrt((xs - 7.5) ** 2 + (ys - 7.5) ** 2)
    rings = (np.sin(d * 1.9) * 0.5 + 0.5)
    img = rgba(ramp(rings * 0.7 + fbm(N, 4, 2, 94) * 0.3, [(0, 0x2A1844), (1, 0x6A4898)]))
    edge = (xs == 0) | (ys == 0) | (xs == N - 1) | (ys == N - 1)
    img[edge, :3] = hexc(0x1E1030)
    glow = empty()
    glow[..., :3] = hexc(0x9FFFFF)
    glow[..., 3] = (d < 2.2) * 1.0
    return img, glow


def tex_lumen_leaves():
    n = fbm(N, 4, 2, 101)
    holes = value_noise(N, 8, 102)
    img = rgba(ramp(n, [(0, 0x0F4A4A), (0.6, 0x1F8A7A), (1, 0x5CE8B0)]))
    img[..., 3] = (holes > 0.28).astype(float)
    glow = empty()
    tips = (np.random.default_rng(103).random((N, N)) > 0.9) & (img[..., 3] > 0)
    glow[..., :3] = hexc(0xC8FFE8)
    glow[..., 3] = tips
    return img, glow


def tex_skystone():
    n = fbm(N, 2, 3, 111)
    vein = 1 - np.abs(fbm(N, 2, 2, 112) * 2 - 1)
    base = ramp(n, [(0, 0xB8C8DA), (0.6, 0xD8E4F0), (1, 0xF4F8FF)])
    base -= (np.clip(vein - 0.88, 0, 1) * 3)[..., None] * 0.4
    return bevel(rgba(base), 0.05, 0.08)


def tex_dream_turf():
    n = fbm(N, 4, 2, 121)
    img = rgba(ramp(n, [(0, 0xC890D8), (0.5, 0xE8A8E0), (1, 0xFFD0F0)]))
    flowers = np.random.default_rng(122).random((N, N)) > 0.93
    img[flowers, :3] = hexc(0xFFFFFF)
    glow = empty()
    glow[..., :3] = hexc(0xFFF0FF)
    glow[..., 3] = flowers * 0.8
    return img, glow


def tex_dream_cloud():
    n = fbm(N, 2, 3, 131)
    img = rgba(ramp(n, [(0, 0xF0E0F8), (1, 0xFFFFFF)]))
    img[..., 3] = 0.78 + 0.18 * n
    return img


def tex_dead_regolith():
    f1, f2, idx = voronoi(N, 14, 141)
    shade = (idx * 0.61 % 1.0)
    img = rgba(ramp(shade * 0.7 + fbm(N, 4, 2, 142) * 0.3, [(0, 0x3E3530), (0.6, 0x5E5248), (1, 0x7E7064)]))
    img[(f2 - f1) < 0.7, :3] *= 0.7
    return img


def tex_ashen_rock():
    n = fbm(N, 2, 3, 151)
    crack = 1 - np.abs(fbm(N, 3, 2, 152) * 2 - 1)
    base = ramp(n, [(0, 0x3A3A3E), (0.6, 0x55555A), (1, 0x6E6E72)])
    base[crack > 0.9] *= 0.45
    return bevel(rgba(base), 0.04, 0.06)


def tex_cosmic_obsidian(frame=0, frames=1):
    n = fbm(N, 2, 3, 161)
    neb = fbm(N, 2, 3, 162)
    base = ramp(n, [(0, 0x05030A), (1, 0x120A22)])
    base += ramp(neb, [(0, 0x000000), (0.6, 0x000000), (1, 0x3A1060)]) * 0.8
    img = rgba(base)
    glow = empty()
    r = np.random.default_rng(163)
    stars = r.random((N, N))
    phases = r.random((N, N)) * 2 * np.pi
    tw = 0.5 + 0.5 * np.sin(phases + 2 * np.pi * frame / frames)
    on = stars > 0.92
    glow[..., :3] = 1.0
    glow[..., 3] = on * (0.35 + 0.65 * tw)
    big = stars > 0.985
    glow[big, :3] = hexc(0xD8C0FF)
    return img, glow


def tex_stardust_sand():
    n = fbm(N, 4, 2, 171)
    img = rgba(ramp(n, [(0, 0x10184A), (0.6, 0x203070), (1, 0x3A4CA0)]))
    glow = empty()
    sp = np.random.default_rng(172).random((N, N))
    glow[..., :3] = np.where((sp > 0.97)[..., None], hexc(0xFFE6A0), hexc(0xA8D8FF))
    glow[..., 3] = (sp > 0.9) * 0.9
    return img, glow


def tex_glitch(frame, frames):
    r = np.random.default_rng(181 + frame * 7)
    img = rgba(np.ones((N, N, 3)) * hexc(0x06080A))
    glow = empty()
    palette = [0x39FF14, 0xFF0055, 0x00E5FF, 0xF6FF3B, 0xFFFFFF]
    for _ in range(7):
        x, y = r.integers(0, N, 2)
        w, h = r.integers(1, 7), r.integers(1, 4)
        col = palette[r.integers(0, len(palette))]
        ys, xs = np.mgrid[0:N, 0:N]
        m = ((xs - x) % N < w) & ((ys - y) % N < h)
        glow[m, :3] = hexc(col)
        glow[m, 3] = 0.9
    scan = (np.arange(N) + frame * 3) % 4 == 0
    img[scan, :, :3] += 0.06
    shift = r.integers(-3, 4)
    row = r.integers(0, N)
    glow[row] = np.roll(glow[row], shift, axis=0)
    return img, glow


def tex_ancient_bricks():
    return bricks(191, [(0, 0xB8955A), (0.6, 0xCDAE72), (1, 0xE2C890)], 0x7A5E34, rows=4, offset=4, wide=8)


def tex_ancient_glyph():
    img = bricks(201, [(0, 0xA88650), (1, 0xC8A870)], 0x6A5030, rows=16, offset=0, wide=16)
    ys, xs = np.mgrid[0:N, 0:N]
    frame = (xs <= 1) | (ys <= 1) | (xs >= 14) | (ys >= 14)
    img[frame, :3] = hexc(0x8A6A3A)
    glyph = [
        "................",
        "................",
        "................",
        "......####......",
        ".....#....#.....",
        ".....#.##.#.....",
        "......#..#......",
        "....########....",
        ".......##.......",
        "....#..##..#....",
        ".....#.##.#.....",
        "......####......",
        ".......##.......",
        "................",
        "................",
        "................",
    ]
    m = mask_from(glyph)
    img[m, :3] = hexc(0x5A3A1A)
    glow = empty()
    glow[..., :3] = hexc(0xFFD27A)
    glow[..., 3] = m
    return img, glow


def tex_ancient_gold():
    n = fbm(N, 4, 2, 211)
    img = rgba(ramp(n, [(0, 0xB8860B), (0.6, 0xE0B040), (1, 0xFFE07A)]))
    ys, xs = np.mgrid[0:N, 0:N]
    border = (xs == 1) | (ys == 1) | (xs == 14) | (ys == 14)
    img[border & (xs >= 1) & (xs <= 14) & (ys >= 1) & (ys <= 14), :3] = hexc(0x8A5A10)
    pattern = ((xs + ys) % 4 == 0) & (xs > 3) & (xs < 12) & (ys > 3) & (ys < 12)
    img[pattern, :3] = hexc(0xFFF2B0)
    return bevel(img, 0.08, 0.15)


def tex_abyssal_glow():
    f1, f2, idx = voronoi(N, 6, 221)
    groove = (f2 - f1) < 1.1
    img = rgba(ramp(f1 / 6.0, [(0, 0xA8FFF4), (0.5, 0x3FD8C8), (1, 0x1A7A80)]))
    img[groove, :3] = hexc(0x0A3A44)
    glow = empty()
    glow[..., :3] = ramp(np.clip(f1 / 5.0, 0, 1), [(0, 0xE0FFFF), (1, 0x2FC8C0)])
    glow[..., 3] = (~groove) * 1.0
    return img, glow


def tex_rift_frame():
    n = fbm(N, 2, 3, 231)
    img = rgba(ramp(n, [(0, 0x0C0614), (0.6, 0x1A0E2A), (1, 0x2A1844)]))
    ys, xs = np.mgrid[0:N, 0:N]
    outer = (xs == 0) | (ys == 0) | (xs == N - 1) | (ys == N - 1)
    rim = ((xs == 1) | (ys == 1) | (xs == N - 2) | (ys == N - 2)) & ~outer
    img[outer, :3] = hexc(0x05030A)
    img[rim, :3] = hexc(0x3A2458)
    glow = empty()
    circuit = [
        "................",
        "................",
        "..#####..#####..",
        "..#...#..#......",
        "..#...####......",
        "..#.......##....",
        "..##.......#....",
        "...#...##..#....",
        "...#...##..###..",
        "...####......#..",
        "......#......#..",
        "......#..#####..",
        "..#####..#......",
        "..#......#......",
        "................",
        "................",
    ]
    m = mask_from(circuit)
    col = ramp((xs + ys) / 30.0, [(0, 0xFF4FD8), (1, 0x40E0FF)])
    glow[..., :3] = col
    glow[..., 3] = m
    img[m, :3] = hexc(0x3A1A50)
    return img, glow


def tex_gravity_lift_top():
    img = rgba(ramp(fbm(N, 4, 2, 241), [(0, 0x1A2030), (1, 0x2A3248)]))
    ys, xs = np.mgrid[0:N, 0:N]
    d = np.sqrt((xs - 7.5) ** 2 + (ys - 7.5) ** 2)
    edge = (xs == 0) | (ys == 0) | (xs == N - 1) | (ys == N - 1)
    img[edge, :3] = hexc(0x3A4458)
    glow = empty()
    ring = (np.abs(d - 5.0) < 0.75) | (np.abs(d - 2.3) < 0.7)
    glow[..., :3] = ramp(np.clip(d / 6, 0, 1), [(0, 0xE0FFFF), (1, 0x5FD8FF)])
    glow[..., 3] = ring
    return img, glow


def tex_gravity_lift_side():
    img = tex_cyber_plating()
    glow = empty()
    chevrons = [
        "................",
        "................",
        ".......##.......",
        "......#..#......",
        ".....#....#.....",
        "................",
        ".......##.......",
        "......#..#......",
        ".....#....#.....",
        "................",
        ".......##.......",
        "......#..#......",
        ".....#....#.....",
        "................",
        "................",
        "................",
    ]
    m = mask_from(chevrons)
    glow[..., :3] = hexc(0x7DF9FF)
    glow[..., 3] = m
    return img, glow


def tex_console_base():
    return tex_cyber_plating()


def tex_console_side():
    img = rgba(ramp(fbm(N, 4, 2, 251), [(0, 0x1C2230), (1, 0x2C3448)]))
    ys, xs = np.mgrid[0:N, 0:N]
    img[(ys == 0) | (ys == N - 1), :3] = hexc(0x46506A)
    glow = empty()
    leds = (ys == 8) & (xs % 3 == 1)
    strip = (ys == 12) & (xs >= 2) & (xs <= 13)
    glow[..., :3] = hexc(0x7DF9FF)
    glow[leds, :3] = np.array([hexc(0xFF4FD8), hexc(0x7DF9FF), hexc(0xFFC14D)])[np.arange(int(leds.sum())) % 3]
    glow[..., 3] = leds | strip
    return img, glow


def tex_console_top():
    ys, xs = np.mgrid[0:N, 0:N]
    img = rgba(np.ones((N, N, 3)) * hexc(0x0A0E18))
    edge = (xs == 0) | (ys == 0) | (xs == N - 1) | (ys == N - 1)
    img[edge, :3] = hexc(0x46506A)
    glow = empty()
    swirl_n = fbm(N, 2, 3, 252)
    d = np.sqrt((xs - 7.5) ** 2 + (ys - 7.5) ** 2)
    a = np.arctan2(ys - 7.5, xs - 7.5)
    spiral = 0.5 + 0.5 * np.sin(a * 2 + d * 0.9)
    nebula = ramp(spiral * 0.6 + swirl_n * 0.4, [(0, 0x1A0A40), (0.5, 0x6A3AFF), (1, 0x7DF9FF)])
    grid = ((xs % 4 == 0) | (ys % 4 == 0)) & ~edge
    glow[..., :3] = nebula
    glow[..., 3] = (~edge) * np.clip(0.5 + 0.5 * spiral, 0, 1) * (d < 7.2)
    glow[grid, :3] = np.clip(glow[grid, :3] + 0.15, 0, 1)
    glow[(d < 1.5), :3] = 1
    return img, glow


def tex_console_column():
    img = tex_cyber_plating()
    ys, xs = np.mgrid[0:N, 0:N]
    glow = empty()
    glow[..., :3] = hexc(0x7DF9FF)
    glow[..., 3] = ((xs == 7) | (xs == 8)) * 1.0
    return img, glow


def tex_altar_side():
    n = fbm(N, 2, 3, 261)
    img = rgba(ramp(n, [(0, 0x0C0814), (1, 0x22183A)]))
    ys, xs = np.mgrid[0:N, 0:N]
    trim = (ys <= 1) | (ys >= 14)
    img[trim, :3] = ramp(fbm(N, 4, 1, 262)[trim], [(0, 0x9A7020), (1, 0xE0B040)])
    glow = empty()
    runes = mask_from([
        "................",
        "................",
        "................",
        "..#.#..##..#.#..",
        "..###.#..#.###..",
        "...#...##...#...",
        "..#.#..##..#.#..",
        "................",
        "...##.#..#.##...",
        "..#....##....#..",
        "...##.#..#.##...",
        "................",
        "................",
        "................",
        "................",
        "................",
    ])
    glow[..., :3] = hexc(0xB070FF)
    glow[..., 3] = runes
    return img, glow


def tex_altar_top():
    ys, xs = np.mgrid[0:N, 0:N]
    d = np.sqrt((xs - 7.5) ** 2 + (ys - 7.5) ** 2)
    img = rgba(ramp(fbm(N, 2, 2, 271), [(0, 0x0C0814), (1, 0x1E1430)]))
    ring = np.abs(d - 6.3) < 0.7
    img[ring, :3] = hexc(0xC89A30)
    glow = empty()
    a = np.arctan2(ys - 7.5, xs - 7.5)
    spokes = (np.abs(np.sin(a * 4)) < 0.25) & (d > 2.5) & (d < 5.5)
    core = d < 2.0
    glow[..., :3] = ramp(np.clip(d / 6, 0, 1), [(0, 0xFFFFFF), (0.4, 0xFFC14D), (1, 0x8F6BFF)])
    glow[..., 3] = spokes | core
    return img, glow


def tex_particle_sprite(color_a, color_b, seed):
    ys, xs = np.mgrid[0:N, 0:N]
    d = np.sqrt((xs - 7.5) ** 2 + (ys - 7.5) ** 2)
    img = empty()
    img[..., :3] = ramp(np.clip(d / 7, 0, 1), [(0, 0xFFFFFF), (0.5, color_a), (1, color_b)])
    img[..., 3] = (np.random.default_rng(seed).random((N, N)) > 0.6) & (d < 7)
    return img


# -------------------------------------------------------------------------------------------------- item pixel art

def from_art(art, palette):
    assert len(art) == N, f"art has {len(art)} rows"
    img = empty()
    for y, row in enumerate(art):
        assert len(row) == N, f"row {y} has length {len(row)}: {row!r}"
        for x, ch in enumerate(row):
            if ch == ".":
                continue
            img[y, x, :3] = hexc(palette[ch])
            img[y, x, 3] = 1
    return img


ITEM_ART = {
    "rift_shard": ([
        "................",
        "............o...",
        "...........o4o..",
        "..........o43o..",
        ".........o432o..",
        "........o4321o..",
        ".......o4321o...",
        "......o4321o....",
        ".....o3321o.....",
        "....o3221o......",
        "...o2221o.......",
        "...o221o........",
        "..o211o.........",
        "..o11o..........",
        "..ooo...........",
        "................",
    ], {"o": 0x1A0830, "1": 0x5A1FA8, "2": 0x8A3AFF, "3": 0x6FD6FF, "4": 0xE8FFFF}),
    "void_essence": ([
        "................",
        "......oooo......",
        "....oo1111oo....",
        "...o11222211o...",
        "..o1122332211o..",
        "..o1223443221o..",
        ".o112344g43211o.",
        ".o12234gg43221o.",
        ".o12234gg43221o.",
        ".o112344g43211o.",
        "..o1223443221o..",
        "..o1122332211o..",
        "...o11222211o...",
        "....oo1111oo....",
        "......oooo......",
        "................",
    ], {"o": 0x0A0214, "1": 0x1E0838, "2": 0x3A1068, "3": 0x6A2BCF, "4": 0x9B5CFF, "g": 0xF0D8FF}),
    "stellar_dust": ([
        "................",
        ".......4........",
        "......424.......",
        ".......4....4...",
        "...4.......424..",
        "..424.......4...",
        "...4....4.......",
        "........3.......",
        "......o333o.....",
        "....oo31213oo...",
        "...o3121112213o.",
        "..o312111111213o",
        "..o221111111122o",
        "...oo22222222oo.",
        ".....oooooooo...",
        "................",
    ], {"o": 0x0A1030, "1": 0x203070, "2": 0x3A50B0, "3": 0x8FB8FF, "4": 0xFFE6A0}),
    "singularity_fragment": ([
        "................",
        "................",
        ".........gg.....",
        "........gk1g....",
        ".......gkk1g....",
        "......gkkk12g...",
        ".....gkkkk12g...",
        "....gkkkkkk2g...",
        "...gkkkkkk12g...",
        "...g1kkkkk2g....",
        "..g21kkkk12g....",
        "..g2111k12g.....",
        "...gg2222gg.....",
        ".....gggg.......",
        "................",
        "................",
    ], {"k": 0x05040A, "1": 0x1A1424, "2": 0xFF8A3A, "g": 0xFFD27A}),
    "exotic_ingot": ([
        "................",
        "................",
        "................",
        "................",
        "......ooooooooo.",
        ".....o44444443o.",
        "....o43333332o..",
        "...oooooooooo2o.",
        "..o4333333332o2o",
        ".o43222222221oo.",
        "o433222222211o..",
        "o3222221111111o.",
        "oooooooooooooo..",
        "................",
        "................",
        "................",
    ], {"o": 0x102030, "1": 0x5A3A9A, "2": 0x3A9AB0, "3": 0x5FE0D0, "4": 0xD8FFF8}),
    "warden_core": ([
        "................",
        ".....oooooo.....",
        "...oo222222oo...",
        "..o2233333322o..",
        "..o2311111132o..",
        ".o231gggggg132o.",
        ".o23ggwwwwgg32o.",
        ".o31gwwwwwwg13o.",
        ".o31gwwwwwwg13o.",
        ".o23ggwwwwgg32o.",
        ".o231gggggg132o.",
        "..o2311111132o..",
        "..o2233333322o..",
        "...oo222222oo...",
        ".....oooooo.....",
        "................",
    ], {"o": 0x3A2408, "1": 0x5A2AA8, "2": 0xB8862A, "3": 0xFFD27A, "g": 0x8F6BFF, "w": 0xF4EAFF}),
    "leviathan_scale": ([
        "................",
        "................",
        "....oooooooo....",
        "...o44333333o...",
        "..o4332222223o..",
        "..o3322111122o..",
        "..o3221111112o..",
        "..o3221111112o..",
        "..o3221111112o..",
        "...o32111112o...",
        "...o32211122o...",
        "....o322222o....",
        ".....o3222o.....",
        "......o33o......",
        ".......oo.......",
        "................",
    ], {"o": 0x062228, "1": 0x0E4A52, "2": 0x1F8A8A, "3": 0x40FFE0, "4": 0xD8FFF8}),
    "portal_gun": ([
        "................",
        "...........bBb..",
        "..........bwwBo.",
        ".........owwwBo.",
        "........ow3wwo..",
        ".......o3332o...",
        "......o33r2o....",
        ".....o3rR2o.....",
        "...oor322o......",
        "..o22o22o.......",
        ".o2ooo2o........",
        ".o1o.o1o........",
        ".o11oo1o........",
        "..o11o2o........",
        "...ooooo........",
        "................",
    ], {"o": 0x1A1E28, "1": 0x3A4050, "2": 0x9AA4B4, "3": 0xE6ECF2, "w": 0xC8F0FF, "b": 0x3DA5FF, "B": 0x9FD8FF, "r": 0xFF8A3A, "R": 0xFFE0B0}),
    "rift_blade": ([
        "................",
        "............oo..",
        "...........o4wo.",
        "..........o43wo.",
        ".........o432o..",
        "........o432o...",
        ".......o432o....",
        "......o432o.....",
        "..o..o432o......",
        "..oggo32o.......",
        "...oggoo........",
        "...ogkgo........",
        "..okko.go.......",
        ".okko...o.......",
        ".occo...........",
        "..oo............",
    ], {"o": 0x14081E, "2": 0x8A3AFF, "3": 0xD07CFF, "4": 0xFF8AE8, "w": 0xFFF0FF, "g": 0xC9A24A, "k": 0x3A2050, "c": 0x40E0FF}),
    "gravity_gauntlet": ([
        "................",
        "....oooooo......",
        "...o222222oo....",
        "..o2ccc2c22o....",
        "..o2222222222o..",
        "..o2c22c22c222o.",
        "..o22222222222o.",
        "..o3cc3cc3cc22o.",
        "..o2222222222o..",
        "..o1222222221o..",
        "...o11111111o...",
        "...o2gggggg2o...",
        "...o22222222o...",
        "...o11111111o...",
        "....oooooooo....",
        "................",
    ], {"o": 0x141822, "1": 0x3A4254, "2": 0x6A7488, "3": 0x9AA4B8, "c": 0x7DF9FF, "g": 0x3DA5FF}),
    "singularity_grenade": ([
        "................",
        "......oo........",
        ".....o33o.......",
        "......oo........",
        ".....oooooo.....",
        "...oo111111oo...",
        "..o1111111111o..",
        "..o1122222211o..",
        ".orrrrrRRrrrrro.",
        ".orrrRRwwRRrrro.",
        ".o111122221111o.",
        "..o1111111111o..",
        "..o1111111111o..",
        "...oo111111oo...",
        ".....oooooo.....",
        "................",
    ], {"o": 0x05040A, "1": 0x1A1424, "2": 0x2E2440, "3": 0x9AA4B4, "r": 0xFF5A1F, "R": 0xFFAA4A, "w": 0xFFF0C0}),
    "singularity_core": ([
        "................",
        "................",
        "......oooo......",
        "....oo1111oo....",
        "...o11111111o...",
        "rrro11111111orrr",
        "RRrRrrrrrrrrRrRR",
        ".rRRRwwwwwwRRRr.",
        "..rrrRRRRRRrrr..",
        "...o11111111o...",
        "...o11111111o...",
        "....oo1111oo....",
        "......oooo......",
        "................",
        "................",
        "................",
    ], {"o": 0x2A1408, "1": 0x020104, "r": 0xC0441A, "R": 0xFF8A3A, "w": 0xFFF0C0}),
    "relic_blade": ([
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "....o...........",
        ".....o.o........",
        "......gg........",
        ".....ogo........",
        "....o2o.o.......",
        "...o2o..........",
        "...oo...........",
    ], {"o": 0x1A1420, "2": 0x5A4A6A, "g": 0xC8A040}),
    "relic_blade_energy": ([
        "..............ww",
        ".............wWw",
        "............wWw.",
        "...........wWw..",
        "..........wWw...",
        ".........wWw....",
        "........wWw.....",
        ".......wWw......",
        "......wWw.......",
        ".....wWw........",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"w": 0xB0B0B0, "W": 0xFFFFFF}),
    "relic_blaster": ([
        "................",
        "................",
        "................",
        "................",
        "...oooooooooo...",
        "..o22222222o....",
        "..o2oooooooo....",
        "..o2o...........",
        "..ooo.og........",
        "....o2og........",
        "....o22o........",
        "....o22o........",
        ".....oo.........",
        "................",
        "................",
        "................",
    ], {"o": 0x1A1420, "2": 0x4A3E5A, "g": 0xC8A040}),
    "relic_blaster_energy": ([
        "................",
        "................",
        "................",
        "................",
        "................",
        "....wWWWWWWw.www",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
    ], {"w": 0xB0B0B0, "W": 0xFFFFFF}),
    "delorean": ([
        "................",
        "................",
        "................",
        "................",
        "......ssssss....",
        ".....sggggggs...",
        "....sgcFFcggss..",
        "..sssssssssssss.",
        ".sssssssssssssss",
        ".sddddddddddddds",
        ".sRsssssssssssYs",
        "..ooo.......ooo.",
        "..ooo.......ooo.",
        "................",
        "................",
        "................",
    ], {"s": 0xB8BCC4, "g": 0x1A2430, "c": 0x80C0FF, "F": 0xFFFFFF, "d": 0x2A2C30, "R": 0xC02020, "Y": 0xFFFFE0, "o": 0x101010}),
    "tardis": ([
        ".......ww.......",
        "......dddd......",
        "....dddddddd....",
        "....dkkkkkkd....",
        "....bbbbbbbb....",
        "....bwwbbwwb....",
        "....bwwbbwwb....",
        "....bbbbbbbb....",
        "....bddbbddb....",
        "....bddbbddb....",
        "....bbbbbbbb....",
        "....bddbbddb....",
        "....bddbbddb....",
        "....bbbbbbbb....",
        "...dddddddddd...",
        "................",
    ], {"b": 0x1A3A8A, "d": 0x10265A, "w": 0xE8F0FF, "k": 0x101010}),
    "reality_rupture": ([
        "............wW..",
        "...........wPPW.",
        "..........wPkPw.",
        ".........wPkPw..",
        "........wPkPw...",
        ".......wPkPw....",
        "......wPkPw.....",
        ".....wPkPw......",
        "..o.wPkPw.......",
        "..oowPkw........",
        "...oVoW.........",
        "...oVVo.........",
        "..oVo.oo........",
        ".oVo............",
        "oVo.............",
        "oo..............",
    ], {"o": 0x120A1C, "V": 0x6A2AC8, "P": 0xB050FF, "k": 0x000000, "w": 0xE0C0FF, "W": 0xFFFFFF}),
    "reality_remote": ([
        "................",
        ".....oooooo.....",
        "....o333333o....",
        "....o3cccc3o....",
        "....o3cRRc3o....",
        "....o3cRRc3o....",
        "....o3cccc3o....",
        "....o333333o....",
        "....o3Pw3Go3....",
        "....o333333o....",
        "....o3G3Pw3o....",
        "....o333333o....",
        "....o3wP3R3o....",
        "....o333333o....",
        ".....oooooo.....",
        "................",
    ], {"o": 0x120A1C, "3": 0x3A2A5A, "c": 0x7DF9FF, "R": 0xFF3A5A, "P": 0x8F6BFF, "w": 0xFFFFFF, "G": 0xFFC14D}),
    "reality_shaper": ([
        "................",
        "...........PP...",
        "..........PwBP..",
        ".........PwBBGP.",
        ".........PBBGGP.",
        "..........PGGP..",
        ".........ogPP...",
        "........ogo.....",
        ".......o2o......",
        "......o2o.......",
        ".....o2o........",
        "....ogo.........",
        "...o2o..........",
        "..o2o...........",
        "..oo............",
        "................",
    ], {"o": 0x2A1A0A, "2": 0x8A5A2A, "g": 0xE0B040, "P": 0x8F6BFF, "w": 0xFFFFFF, "B": 0x7DF9FF, "G": 0xFF7AF0}),
    "dimensional_key": ([
        "................",
        "...oooo.........",
        "..o2332o........",
        ".o23oo32o.......",
        ".o3occo3o.......",
        ".o3occo3o.......",
        ".o23oo32o.......",
        "..o2332oo.......",
        "...oooo23o......",
        "........o23o....",
        ".........o23o...",
        "..........o23o..",
        ".........oo223o.",
        "........o2oo22o.",
        ".........o..oo..",
        "................",
    ], {"o": 0x3A2408, "2": 0xB8862A, "3": 0xFFD27A, "c": 0x40E0FF}),
    "universe_compass": ([
        "................",
        ".....oooooo.....",
        "...oo333333oo...",
        "..o33nnnnnn33o..",
        "..o3nnNnnnnn3o..",
        ".o3nnnnnnmnnn3o.",
        ".o3nnnnnmmnNn3o.",
        ".o3nnnnwmnnnn3o.",
        ".o3nnnnmwnnnn3o.",
        ".o3nNnmmnnnnn3o.",
        ".o3nnnmnnnnnn3o.",
        "..o3nnnnnnNn3o..",
        "..o33nnnnnn33o..",
        "...oo333333oo...",
        ".....oooooo.....",
        "................",
    ], {"o": 0x1A1E28, "3": 0xB8C2D0, "n": 0x2A1460, "N": 0xFFFFFF, "m": 0xFF4FD8, "w": 0xFFFFFF}),
    "homeward_rift": ([
        "................",
        ".......oo.......",
        "......o33o......",
        ".....o3223o.....",
        "....o32ww23o....",
        "...o32wBBw23o...",
        "..o32wBwwBw23o..",
        ".o32wBwWWwBw23o.",
        ".o32wBwWWwBw23o.",
        "..o32wBwwBw23o..",
        "...o32wBBw23o...",
        "....o32ww23o....",
        ".....o3223o.....",
        "......o33o......",
        ".......oo.......",
        "................",
    ], {"o": 0x1A2840, "2": 0x4A78B0, "3": 0xA8C8F0, "w": 0xE8F4FF, "B": 0x7FB8FF, "W": 0xFFFFFF}),
    "rift_igniter": ([
        "................",
        "................",
        "..oooo..........",
        ".o3333o.........",
        "o3o..o3o........",
        "o3o..o3o........",
        "o3o..o3o........",
        ".o3oo3o..o......",
        "..o33o..o4o.....",
        "...oo..o432o....",
        ".......o4321o...",
        "........o3221o..",
        ".........o221o..",
        "..........o11o..",
        "...........oo...",
        "................",
    ], {"o": 0x14141C, "3": 0xA8B0BC, "1": 0x5A1FA8, "2": 0x8A3AFF, "4": 0xE8FFFF}),
    "rift_sigil": ([
        "................",
        ".....oooooo.....",
        "...oo222222oo...",
        "..o2233333322o..",
        "..o23kkkkkk32o..",
        ".o23kkkggkkk32o.",
        ".o23kkgkkgkk32o.",
        ".o23kggggggk32o.",
        ".o23kkkggkkk32o.",
        ".o23kkgkkgkk32o.",
        ".o23kgkkkkgk32o.",
        "..o23kkkkkk32o..",
        "..o2233333322o..",
        "...oo222222oo...",
        ".....oooooo.....",
        "................",
    ], {"o": 0x3A2408, "2": 0xB8862A, "3": 0xFFD27A, "k": 0x1A0A2A, "g": 0xB070FF}),
}

ARMOR_ART = {
    "helmet": [
        "................",
        "................",
        "................",
        "....oooooooo....",
        "...o33322222o...",
        "..o3322222221o..",
        "..o2222cc22221o.",
        "..o2ooooooooo1o.",
        "..o2ogggggggo1o.",
        "..o2oooooooo21o.",
        "..o21o....o211o.",
        "..oooo....ooooo.",
        "................",
        "................",
        "................",
        "................",
    ],
    "chestplate": [
        "................",
        "..oooo....oooo..",
        ".o3322oooo2221o.",
        ".o3222cggc22221o",
        ".o22222gg222221o",
        ".oo2222222222oo.",
        "..oo32222221oo..",
        "...o3222c221o...",
        "...o2222g221o...",
        "...o2222c221o...",
        "...o22222221o...",
        "...o22c22c21o...",
        "...o22222221o...",
        "...oooooooooo...",
        "................",
        "................",
    ],
    "leggings": [
        "................",
        "...oooooooooo...",
        "...o3gggggg2o...",
        "...o32222221o...",
        "...o32oooo21o...",
        "...o32o..o21o...",
        "...o3co..oc1o...",
        "...o32o..o21o...",
        "...o32o..o21o...",
        "...o3go..og1o...",
        "...o32o..o21o...",
        "...o32o..o21o...",
        "...oooo..oooo...",
        "................",
        "................",
        "................",
    ],
    "boots": [
        "................",
        "................",
        "................",
        "................",
        "................",
        "................",
        "...oooo..oooo...",
        "...o32o..o21o...",
        "...o3go..og1o...",
        "...o32o..o21o...",
        "..o322o..o221o..",
        ".o3222o..o2221o.",
        ".occcco..occcco.",
        ".oooooo..oooooo.",
        "................",
        "................",
    ],
}

ARMOR_SETS = {
    # outline, dark, mid, light, glow, accent
    "rift_walker": (0x14081E, 0x3A2A5A, 0x6A4AA0, 0x9A7AD0, 0xB070FF, 0x40E0FF),
    "voyager": (0x1A222C, 0x7A8C9C, 0xC0CED8, 0xF0F6FA, 0x7DF9FF, 0xFFC14D),
    "event_horizon": (0x050308, 0x141018, 0x2A2030, 0x4A3A58, 0xFF8A3A, 0x5A2AFF),
    "astral": (0x2A2050, 0xA898C8, 0xE0D4C0, 0xFFF6E0, 0xFFE6A0, 0x8F6BFF),
}


def armor_icon(piece, palette):
    o, d, m, l, g, a = palette
    pal = {"o": o, "1": d, "2": m, "3": l, "g": g, "c": a}
    return from_art(ARMOR_ART[piece], pal)


# ---------------------------------------------------------------------------------------------- armour model layers

def armor_layers(palette, seed):
    """Paints the vanilla 64x32 humanoid armour layout: layer 1 (helmet, chestplate, boots), layer 2 (leggings)."""
    o, d, m, l, g, a = [hexc(c) for c in palette]
    H, W = 32, 64
    noise = np.random.default_rng(seed).random((H, W))

    def plate(img, x0, y0, x1, y1, glow_rows=(), accent_cols=()):
        for y in range(y0, y1):
            for x in range(x0, x1):
                edge = x in (x0, x1 - 1) or y in (y0, y1 - 1)
                col = d if edge else (m * (0.92 + 0.12 * noise[y, x]))
                if not edge and (y - y0) == 1:
                    col = l
                img[y, x, :3] = col
                img[y, x, 3] = 1
        for gy in glow_rows:
            if y0 <= gy < y1:
                img[gy, x0 + 1:x1 - 1, :3] = g
        for ax in accent_cols:
            if x0 <= ax < x1:
                img[y0 + 1:y1 - 1, ax, :3] = a

    def box(img, u, v, w, h, dp, rows=None, **kw):
        """Faces of a w*h*dp box whose texture origin is (u, v). rows limits the painted side rows (relative)."""
        faces = [
            (u + dp, v, u + dp + w, v + dp),            # top
            (u + dp + w, v, u + dp + 2 * w, v + dp),    # bottom
            (u, v + dp, u + dp, v + dp + h),            # right
            (u + dp, v + dp, u + dp + w, v + dp + h),   # front
            (u + dp + w, v + dp, u + 2 * dp + w, v + dp + h),  # left
            (u + 2 * dp + w, v + dp, u + 2 * dp + 2 * w, v + dp + h),  # back
        ]
        for i, (x0, y0, x1, y1) in enumerate(faces):
            if rows is not None and i >= 2:
                y0, y1 = v + dp + rows[0], v + dp + rows[1]
            elif rows is not None and i < 2 and rows[0] > 0 and i == 0:
                continue
            plate(img, x0, y0, x1, y1, **kw)

    layer1 = np.zeros((H, W, 4))
    # helmet (head 8x8x8 at 0,0) with a glowing visor across the front
    box(layer1, 0, 0, 8, 8, 8)
    layer1[8 + 3:8 + 5, 9:15, :3] = g
    layer1[8 + 3:8 + 5, 9:15, 3] = 1
    # chestplate: body (8x12x4 at 16,16) and arms (4x12x4 at 40,16)
    box(layer1, 16, 16, 8, 12, 4, glow_rows=(24, 25), accent_cols=(23, 24))
    layer1[22:24, 22:26, :3] = g
    box(layer1, 40, 16, 4, 12, 4, rows=(0, 9), glow_rows=(24,))
    # boots: lower part of the legs (4x12x4 at 0,16)
    box(layer1, 0, 16, 4, 12, 4, rows=(7, 12), glow_rows=(29,))

    layer2 = np.zeros((H, W, 4))
    # leggings: belt on the body and full legs
    box(layer2, 16, 16, 8, 12, 4, rows=(7, 12), glow_rows=(27,))
    box(layer2, 0, 16, 4, 12, 4, rows=(0, 10), glow_rows=(25,), accent_cols=(5, 6))
    return layer1, layer2


# ---------------------------------------------------------------------------------------------- particles / misc

def particle_frames(kind, frames=4):
    out = []
    S = 16
    ys, xs = np.mgrid[0:S, 0:S].astype(float)
    d = np.sqrt((xs + 0.5 - 8) ** 2 + (ys + 0.5 - 8) ** 2)
    for f in range(frames):
        t = f / max(1, frames - 1)
        img = np.zeros((S, S, 4))
        if kind == "spark":
            arm = np.exp(-np.abs(xs + 0.5 - 8) * 0.9) * np.exp(-np.abs(ys + 0.5 - 8) * 0.25) + \
                  np.exp(-np.abs(ys + 0.5 - 8) * 0.9) * np.exp(-np.abs(xs + 0.5 - 8) * 0.25)
            core = np.exp(-d * 0.7)
            v = np.clip((arm * 0.8 + core) * (1 - 0.55 * t), 0, 1)
        elif kind == "mote":
            v = np.clip(1 - d / (6.5 - 2 * t), 0, 1) ** 1.6
        elif kind == "streak":
            v = np.exp(-np.abs(ys + 0.5 - 8) * 0.9) * np.clip(1 - np.abs(xs + 0.5 - 8) / 8, 0, 1)
            v = np.clip(v * (1 - 0.5 * t), 0, 1)
        elif kind == "ring":
            r = 3 + 4 * t
            v = np.exp(-((d - r) ** 2) / (1.2 - 0.6 * t)) * (1 - 0.4 * t)
        elif kind == "glitch":
            r = np.random.default_rng(300 + f)
            v = np.zeros((S, S))
            for _ in range(4):
                x, y = r.integers(2, 12, 2)
                w, h = r.integers(1, 5), r.integers(1, 3)
                v[y:y + h, x:x + w] = 1
        elif kind == "dust":
            v = np.clip(1 - d / (3.5 - t), 0, 1) ** 1.2
        else:  # infall
            v = np.clip(1 - d / 5.0, 0, 1) ** 2 * (1 - 0.3 * t)
        img[..., :3] = np.clip(v, 0, 1)[..., None]
        img[..., 3] = np.clip(v, 0, 1)
        out.append(img)
    return out


def glow_sprite(size=64):
    ys, xs = np.mgrid[0:size, 0:size].astype(float)
    d = np.sqrt((xs + 0.5 - size / 2) ** 2 + (ys + 0.5 - size / 2) ** 2) / (size / 2)
    v = np.clip(1 - d, 0, 1) ** 2.2
    img = np.zeros((size, size, 4))
    img[..., :3] = v[..., None]
    img[..., 3] = v
    return img


# ------------------------------------------------------------------------------------------------------------ driver

GLOW_BLOCKS = {}  # block texture name -> True when a "_glow" overlay exists (read by the model generator)


def block(name, result, preview):
    if isinstance(result, tuple):
        base, glow = result
        save(base, f"block/{name}.png", preview)
        save(glow, f"block/{name}_glow.png", preview)
        GLOW_BLOCKS[name] = True
    else:
        save(result, f"block/{name}.png", preview)


def animated_block(name, fn, frames, frametime, preview, interpolate=False):
    pairs = [fn(f, frames) for f in range(frames)]
    if isinstance(pairs[0], tuple):
        save_animated([p[0] for p in pairs], f"block/{name}.png", frametime, preview, interpolate)
        save_animated([p[1] for p in pairs], f"block/{name}_glow.png", frametime, preview, interpolate)
        GLOW_BLOCKS[name] = True
    else:
        save_animated(pairs, f"block/{name}.png", frametime, preview, interpolate)


def tex_denizen(robe, trim, skin, seed):
    """64x64 player-layout skin: a robed native with trimmed hems, a face and a hood."""
    img = rgba(ramp(fbm(64, 4, 3, seed), [(0, robe), (0.6, robe), (1, trim)]), 1.0)
    img[32:48, 0:56, 3] = 0.0
    img[48:64, 0:16, 3] = 0.0
    img[48:64, 48:64, 3] = 0.0
    img[0:16, 32:64, 3] = 0.0
    img[8:16, 8:16, :3] = hexc(skin)
    img[11, 9:11, :3] = hexc(0x101018)
    img[11, 13:15, :3] = hexc(0x101018)
    img[14, 10:14, :3] = hexc(skin) * 0.7
    img[0:8, 8:16, :3] = hexc(robe) * 0.8
    for y in (19, 31):
        img[y, 16:40, :3] = hexc(trim)
    img[30:32, 20:28, :3] = hexc(trim)
    return img


def tex_cosmic_deity():
    """64x64 player-layout skin: a body made of night sky — nebula skin, star points, gold sigil veins, burning eyes."""
    size = 64
    neb = fbm(size, 4, 4, 9001)
    rgb = ramp(neb, [(0.0, 0x05010F), (0.35, 0x1A0A40), (0.6, 0x4A1A8A), (0.8, 0x2A60C0), (1.0, 0xC070FF)])
    rng = np.random.default_rng(77)
    stars = rng.random((size, size)) > 0.93
    rgb[stars] = rgb[stars] * 0.3 + hexc(0xFFF4E0) * 0.7
    veins = fbm(size, 6, 2, 31337)
    vein = np.abs(veins - 0.5) < 0.035
    rgb[vein] = hexc(0xFFC14D)
    img = rgba(rgb, 1.0)
    # second skin layer (rows/areas used by hat & jacket) left transparent
    img[0:16, 32:64, 3] = 0.0
    img[32:48, 0:56, 3] = 0.0
    img[48:64, 0:16, 3] = 0.0
    img[48:64, 48:64, 3] = 0.0
    # face (front of head is x 8..15, y 8..15): darker brow, two burning eyes
    img[8:16, 8:16, :3] *= 0.55
    for x in (9, 10, 13, 14):
        img[11, x, :3] = hexc(0xFFF0C0)
    img[12, 9:11, :3] = hexc(0xFFC14D)
    img[12, 13:15, :3] = hexc(0xFFC14D)
    # a glowing core in the chest
    img[22:26, 22:26, :3] = hexc(0xFFE0FF)
    img[23:25, 23:25, :3] = hexc(0xFFFFFF)
    return img


def tex_x_starmetal_block():
    f1, f2, idx = voronoi(N, 10, 3100)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3200) * 0.5
    img = rgba(ramp(v, [(0, 0x2A3A5A), (0.6, 0x6A8AC0), (1, 0xD0E8FF)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    g = empty()
    m = fbm(N, 6, 2, 3300) > 0.72
    g[m, :3] = hexc(0xBFE8FF)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0xBFE8FF)
    return img, g

def tex_x_aurora_glass():
    f1, f2, idx = voronoi(N, 11, 3101)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3201) * 0.5
    img = rgba(ramp(v, [(0, 0x40FFB0), (0.5, 0x60C0FF), (1, 0xB060FF)]), 0.55)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    return img

def tex_x_sculk_crystal():
    f1, f2, idx = voronoi(N, 12, 3102)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3202) * 0.5
    img = rgba(ramp(v, [(0, 0x021014), (0.6, 0x0A5A6A), (1, 0x30E0F0)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    img[(f2 - f1) < 0.35, 3] = 0.0
    g = empty()
    m = fbm(N, 6, 2, 3302) > 0.72
    g[m, :3] = hexc(0x60F8FF)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0x60F8FF)
    return img, g

def tex_x_magma_crust():
    f1, f2, idx = voronoi(N, 13, 3103)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3203) * 0.5
    img = rgba(ramp(v, [(0, 0x1A0400), (0.5, 0x4A1000), (1, 0x8A2A00)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    g = empty()
    m = fbm(N, 6, 2, 3303) > 0.72
    g[m, :3] = hexc(0xFF7020)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0xFF7020)
    return img, g

def tex_x_chrome_plating():
    f1, f2, idx = voronoi(N, 14, 3104)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3204) * 0.5
    img = rgba(ramp(v, [(0, 0x8A9AAA), (0.6, 0xD8E4F0), (1, 0xFFFFFF)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    return img

def tex_x_honey_crystal():
    f1, f2, idx = voronoi(N, 15, 3105)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3205) * 0.5
    img = rgba(ramp(v, [(0, 0x8A4A00), (0.5, 0xE0A020), (1, 0xFFE080)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    img[(f2 - f1) < 0.35, 3] = 0.0
    g = empty()
    m = fbm(N, 6, 2, 3305) > 0.72
    g[m, :3] = hexc(0xFFF0A0)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0xFFF0A0)
    return img, g

def tex_x_lunar_dust():
    f1, f2, idx = voronoi(N, 16, 3106)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3206) * 0.5
    img = rgba(ramp(v, [(0, 0x8A8A92), (0.6, 0xB8B8C0), (1, 0xE0E0E8)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    return img

def tex_x_nebula_stone():
    f1, f2, idx = voronoi(N, 10, 3107)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3207) * 0.5
    img = rgba(ramp(v, [(0, 0x10021A), (0.5, 0x4A1A7A), (1, 0xC050FF)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    g = empty()
    m = fbm(N, 6, 2, 3307) > 0.72
    g[m, :3] = hexc(0xFF80F0)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0xFF80F0)
    return img, g

def tex_x_starwood_planks():
    f1, f2, idx = voronoi(N, 11, 3108)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3208) * 0.5
    img = rgba(ramp(v, [(0, 0x2A1A3A), (0.6, 0x5A3A7A), (1, 0x9A7AC0)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    return img

def tex_x_voidglass():
    f1, f2, idx = voronoi(N, 12, 3109)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3209) * 0.5
    img = rgba(ramp(v, [(0, 0x05010A), (0.6, 0x20103A), (1, 0x5A30A0)]), 0.55)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    return img

def tex_x_rune_tile():
    f1, f2, idx = voronoi(N, 13, 3110)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3210) * 0.5
    img = rgba(ramp(v, [(0, 0x2A1A0A), (0.5, 0x5A4020), (1, 0x8A6A3A)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    g = empty()
    m = fbm(N, 6, 2, 3310) > 0.72
    g[m, :3] = hexc(0xFFC14D)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0xFFC14D)
    return img, g

def tex_x_ember_bricks():
    f1, f2, idx = voronoi(N, 14, 3111)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3211) * 0.5
    img = rgba(ramp(v, [(0, 0x2A0804), (0.6, 0x6A1A10), (1, 0xA03018)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    g = empty()
    m = fbm(N, 6, 2, 3311) > 0.72
    g[m, :3] = hexc(0xFF6020)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0xFF6020)
    return img, g

def tex_x_frost_crystal():
    f1, f2, idx = voronoi(N, 15, 3112)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3212) * 0.5
    img = rgba(ramp(v, [(0, 0x3A6A9A), (0.5, 0x80C0F0), (1, 0xF0FFFF)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    img[(f2 - f1) < 0.35, 3] = 0.0
    g = empty()
    m = fbm(N, 6, 2, 3312) > 0.72
    g[m, :3] = hexc(0xE0FFFF)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0xE0FFFF)
    return img, g

def tex_x_coral_stone():
    f1, f2, idx = voronoi(N, 16, 3113)
    v = (idx * 0.37 % 1.0) * 0.5 + fbm(N, 4, 3, 3213) * 0.5
    img = rgba(ramp(v, [(0, 0x5A1A3A), (0.5, 0xC04080), (1, 0xFF90C0)]), 1.0)
    img[(f2 - f1) < 0.6, :3] *= 0.75
    g = empty()
    m = fbm(N, 6, 2, 3313) > 0.72
    g[m, :3] = hexc(0x80FFE0)
    g[m, 3] = 1.0
    img[m, :3] = hexc(0x80FFE0)
    return img, g


def generate():
    preview = []
    block("starmetal_block", tex_x_starmetal_block(), preview)
    block("aurora_glass", tex_x_aurora_glass(), preview)
    block("sculk_crystal", tex_x_sculk_crystal(), preview)
    block("magma_crust", tex_x_magma_crust(), preview)
    block("chrome_plating", tex_x_chrome_plating(), preview)
    block("honey_crystal", tex_x_honey_crystal(), preview)
    block("lunar_dust", tex_x_lunar_dust(), preview)
    block("nebula_stone", tex_x_nebula_stone(), preview)
    block("starwood_planks", tex_x_starwood_planks(), preview)
    block("voidglass", tex_x_voidglass(), preview)
    block("rune_tile", tex_x_rune_tile(), preview)
    block("ember_bricks", tex_x_ember_bricks(), preview)
    block("frost_crystal", tex_x_frost_crystal(), preview)
    block("coral_stone", tex_x_coral_stone(), preview)
    save(tex_cosmic_deity(), "entity/cosmic_deity.png", preview)
    for i, (robe, trim, skin) in enumerate([(0x2A3A8A, 0xFFC14D, 0xC08A6A), (0x1A6A3A, 0xE0FF80, 0x8A5A3A), (0x8A1A2A, 0xFFB040, 0xE0B090),
                                            (0x101018, 0x00F0FF, 0xD0A080), (0xE0E0F0, 0x8F6BFF, 0xF0D0B0), (0x6A3A1A, 0x40E0C0, 0x6A4028),
                                            (0x5A1A6A, 0xFF7AF0, 0xB08070), (0x3A3A3A, 0xFF4A10, 0x9A6A50)]):
        save(tex_denizen(robe, trim, skin, 700 + i), f"entity/denizen_{i}.png", preview)
    for name, trim, visor, seed in [("tsa_agent", 0x60A0FF, 0x80E0FF, 801), ("tsa_enforcer", 0xFF4050, 0xFF8080, 802)]:
        img = tex_denizen(0x14161C, trim, 0xC8A080, seed)
        img[10:12, 8:16, :3] = hexc(visor)
        img[20:32, 20:22, :3] = hexc(0xE0E0E0)
        img[22:24, 26:28, :3] = hexc(trim)
        save(img, f"entity/{name}.png", preview)
    for name, stops, seed in [("void_cultist", [(0, 0x05010A), (0.6, 0x2A0A4A), (1, 0x9B30FF)], 501),
                              ("crystal_spider", [(0, 0x1A0A2A), (0.5, 0x6A3AAA), (1, 0xE0B0FF)], 502),
                              ("star_moth", [(0, 0x1A1030), (0.6, 0x8A70C0), (1, 0xFFE8A0)], 503),
                              ("lunar_golem", [(0, 0x6A6A72), (0.6, 0xB8B8C0), (1, 0xEFEFF8)], 504)]:
        save(rgba(ramp(fbm(64, 4, 4, seed), stops)), f"entity/{name}.png", preview)
    block("nexus_stone", tex_nexus_stone(), preview)
    block("nexus_bricks", tex_nexus_bricks(), preview)
    animated_block("nexus_glow", tex_nexus_glow, 16, 3, preview, interpolate=True)
    block("void_stone", tex_void_stone(), preview)
    block("void_crystal", crystal(51, 0x1A0A3A, 0x5A2AA8, 0x9B6BFF, 0xD0A8FF), preview)
    block("rift_crystal", crystal(52, 0x3A0A40, 0xC03AD0, 0x7DF9FF, 0xFFD8FF, count=8), preview)
    block("neon_panel_cyan", tex_neon_panel(0x2BF3FF), preview)
    block("neon_panel_magenta", tex_neon_panel(0xFF2BD6), preview)
    block("cyber_plating", tex_cyber_plating(), preview)
    block("cyber_glass", tex_cyber_glass(), preview)
    block("alien_moss", tex_alien_moss(), preview)
    block("alien_soil", tex_alien_soil(), preview)
    block("glowcap", tex_glowcap(), preview)
    block("lumen_log", tex_lumen_log_side(), preview)
    block("lumen_log_top", tex_lumen_log_top(), preview)
    block("lumen_leaves", tex_lumen_leaves(), preview)
    block("skystone", tex_skystone(), preview)
    block("dream_turf", tex_dream_turf(), preview)
    block("dream_cloud", tex_dream_cloud(), preview)
    block("dead_regolith", tex_dead_regolith(), preview)
    block("ashen_rock", tex_ashen_rock(), preview)
    animated_block("cosmic_obsidian", tex_cosmic_obsidian, 8, 4, preview, interpolate=True)
    block("stardust_sand", tex_stardust_sand(), preview)
    animated_block("glitch_block", tex_glitch, 8, 2, preview)
    block("ancient_bricks", tex_ancient_bricks(), preview)
    block("ancient_glyph", tex_ancient_glyph(), preview)
    block("ancient_gold", tex_ancient_gold(), preview)
    block("abyssal_glow", tex_abyssal_glow(), preview)
    block("rift_frame", tex_rift_frame(), preview)
    block("gravity_lift_top", tex_gravity_lift_top(), preview)
    block("gravity_lift_side", tex_gravity_lift_side(), preview)
    block("gravity_lift_bottom", tex_cyber_plating(), preview)
    block("console_base", tex_console_base(), preview)
    block("console_side", tex_console_side(), preview)
    block("console_top", tex_console_top(), preview)
    block("console_column", tex_console_column(), preview)
    block("altar_side", tex_altar_side(), preview)
    block("altar_top", tex_altar_top(), preview)
    block("altar_bottom", tex_void_stone(), preview)
    block("rift_particle", tex_particle_sprite(0xB070FF, 0x40E0FF, 401), preview)
    block("portal_particle", tex_particle_sprite(0x7DF9FF, 0xFFC14D, 402), preview)

    for name, (art, pal) in ITEM_ART.items():
        save(from_art(art, pal), f"item/{name}.png", preview)
    for set_name, palette in ARMOR_SETS.items():
        for piece in ARMOR_ART:
            save(armor_icon(piece, palette), f"item/{set_name}_{piece}.png", preview)
        l1, l2 = armor_layers(palette, sum(map(ord, set_name)))
        save(l1, f"models/armor/{set_name}_layer_1.png", preview)
        save(l2, f"models/armor/{set_name}_layer_2.png", preview)

    for kind in ["spark", "mote", "streak", "ring", "glitch", "dust", "infall"]:
        for i, frame in enumerate(particle_frames(kind)):
            save(frame, f"particle/{kind}_{i}.png", preview)

    white = np.ones((16, 16, 4))
    save(white, "misc/white.png", preview)
    save(glow_sprite(), "misc/glow.png", preview)
    return preview


def contact_sheet(preview, path, scale=4):
    """Writes a labelled overview of every generated texture (for review, not shipped)."""
    from PIL import ImageDraw
    cols = 10
    cell = 16 * scale + 4
    rows = (len(preview) + cols - 1) // cols
    sheet = Image.new("RGBA", (cols * cell, rows * (cell + 10)), (40, 40, 48, 255))
    draw = ImageDraw.Draw(sheet)
    for i, (rel, arr) in enumerate(preview):
        im = Image.fromarray((np.clip(arr, 0, 1) * 255).astype(np.uint8), "RGBA")
        h, w = arr.shape[:2]
        fit = min(16 * scale / w, 16 * scale / h)
        im = im.resize((max(1, int(w * fit)), max(1, int(h * fit))), Image.NEAREST)
        x = (i % cols) * cell + 2
        y = (i // cols) * (cell + 10) + 2
        checker = Image.new("RGBA", im.size, (70, 70, 80, 255))
        sheet.paste(checker, (x, y))
        sheet.alpha_composite(im, (x, y))
        draw.text((x, y + 16 * scale), rel.split("/")[-1][:12], fill=(220, 220, 230, 255))
    sheet.save(path)
