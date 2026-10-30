#!/usr/bin/env python3
"""Generates every texture, model, lang entry, recipe, loot table, advancement and worldgen file for ARCANA.

Run from arcana-mod/:  python3 tools/gen_assets.py
Spell ids/schools/slots are parsed from Spell.java so the Java enum stays the single source of truth.
"""
import gzip, json, math, os, random, re, struct
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources')
A = os.path.join(ROOT, 'assets', 'arcana')
D = os.path.join(ROOT, 'data', 'arcana')
M = 'arcana'

def w(path, data, mode='w'):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, mode) as f:
        f.write(data)

def jw(path, obj):
    w(path, json.dumps(obj, indent=2) + '\n')

SCHOOLS = [('void', 0x7B2CBF), ('verdant', 0x3FB950), ('glacier', 0x6EC6FF), ('crimson', 0xC1121F),
           ('solar', 0xFFB703), ('storm', 0xF2E94E), ('chronos', 0x2EC4B6), ('arcane', 0xE056FD)]
COLORS = dict(SCHOOLS); COLORS['forbidden'] = 0xFF2BD6
def rgb(c): return ((c >> 16) & 255, (c >> 8) & 255, c & 255)
def shade(c, f): return tuple(max(0, min(255, int(v * f))) for v in rgb(c))

# ---------------------------------------------------------------- spells (parsed from Java)
spells = []
src = open(os.path.join(ROOT, '..', 'java', 'dev', 'arcana', 'magic', 'Spell.java')).read()
for m in re.finditer(r'^\s+([A-Z_]+)\(School\.([A-Z]+), SpellSlot\.([A-Z]+)', src, re.M):
    name, school, slot = m.groups()
    spells.append((name.lower(), 'forbidden' if school == 'NONE' else school.lower(), slot.lower()))
assert len(spells) == 43, len(spells)

# ---------------------------------------------------------------- textures: wands
ORN = {
 'void': ["..###..", ".#...#.", "#.....#", "#.....#", "#.....#", ".#...#.", "..###.."],
 'verdant': [".#...#.", "##...##", ".#...#.", ".......", "#.....#", "##...##", ".#...#."],
 'glacier': ["...#...", "...#...", ".#...#.", "#.....#", ".#...#.", "...#...", "...#..."],
 'crimson': ["##...##", "#.....#", ".......", ".......", ".......", "#.....#", "##...##"],
 'solar': ["#..#..#", ".#.#.#.", "..###..", "###.###", "..###..", ".#.#.#.", "#..#..#"],
 'storm': ["....##.", "...##..", "..##...", ".#####.", "...##..", "..##...", ".##...."],
 'chronos': ["#######", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#######"],
 'arcane': ["...#...", "...#...", "..###..", "###.###", "..###..", "...#...", "...#..."],
 'forbidden': ["#.#.#.#", ".#...#.", "#..#..#", "..#.#..", "#..#..#", ".#...#.", "#.#.#.#"],
}

def wand_frame(school, stage, accent=None, phase=0):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    px = img.load()
    c = accent if accent is not None else COLORS[school]
    handle_c = {0: (92, 62, 38), 1: (70, 70, 84), 2: (196, 160, 70)}[stage]
    if school == 'forbidden': handle_c = (40, 20, 60)
    shaft_c = (140, 100, 62) if stage == 0 else (150, 150, 168) if stage == 1 else (230, 210, 150)
    if school == 'forbidden': shaft_c = (88, 44, 120)
    # diagonal shaft from (1,14) to (10,5)
    for i in range(10):
        x, y = 1 + i, 14 - i
        px[x, y] = handle_c if i < 3 else shaft_c
        if i >= 3 and i < 9: px[x + 1, y] = tuple(int(v * 0.7) for v in shaft_c) + (255,)
    if stage >= 1:
        for (x, y) in [(5, 10), (6, 9)]: px[x, y] = (255, 214, 102, 255)
    ox, oy = 9, 0
    for ry, row in enumerate(ORN[school]):
        for rx, ch in enumerate(row):
            if ch == '#':
                px[ox + rx, oy + ry] = shade(accent if accent is not None else COLORS[school], 0.75 + 0.05 * stage) + (255,)
    if stage >= 1:
        for (x, y) in [(8, 2), (15, 6), (11, 7), (14, 0)]: px[x, y] = shade(c, 1.4) + (255,)
    if stage >= 2:
        a = 0.55 + 0.45 * math.sin(phase * math.pi / 2)
        for ry in range(7):
            for rx in range(7):
                if ORN[school][ry][rx] == '#':
                    for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                        X, Y = ox + rx + dx, oy + ry + dy
                        if 0 <= X < 16 and 0 <= Y < 16 and px[X, Y][3] == 0:
                            px[X, Y] = tuple(int(v) for v in shade(c, 1.5)) + (int(120 * a),)
        px[9, 14] = (255, 214, 102, 255); px[10, 13] = (255, 214, 102, 255)
    # outline
    outline = img.copy(); op = outline.load()
    for y in range(16):
        for x in range(16):
            if px[x, y][3] == 0:
                for dx, dy in [(1, 0), (-1, 0), (0, 1), (0, -1)]:
                    X, Y = x + dx, y + dy
                    if 0 <= X < 16 and 0 <= Y < 16 and px[X, Y][3] == 255:
                        op[x, y] = (12, 8, 22, 255); break
    return outline

def crystal_overlay(stage):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = img.load()
    cx, cy = 12, 3
    shapes = {0: [(0, 0), (1, 0), (0, 1), (1, 1)],
              1: [(0, -1), (-1, 0), (0, 0), (1, 0), (0, 1), (0, 0)],
              2: [(0, -2), (0, -1), (-1, 0), (0, 0), (1, 0), (-2, 0), (2, 0), (0, 1), (0, 2), (-1, -1), (1, -1), (-1, 1), (1, 1)]}
    for dx, dy in shapes[stage]:
        v = 235 if (dx + dy) % 2 == 0 else 200
        if dx == 0 and dy == 0: v = 255
        px[cx + dx, cy + dy] = (v, v, v, 255)
    return img

def anim_strip(frames):
    strip = Image.new('RGBA', (16, 16 * len(frames)), (0, 0, 0, 0))
    for i, f in enumerate(frames): strip.paste(f, (0, 16 * i))
    return strip

def save(img, path):
    os.makedirs(os.path.dirname(path), exist_ok=True); img.save(path)

IT = os.path.join(A, 'textures', 'item')

# ---------------------------------------------------------------- textures: materials, crystals, blocks
def gem(color, shape='shard', glow=1.0):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = img.load()
    pts = {'shard': [(8, 2), (7, 3), (8, 3), (9, 3), (6, 4), (7, 4), (8, 4), (9, 4), (10, 4), (6, 5), (7, 5), (8, 5), (9, 5), (10, 5),
                     (6, 6), (7, 6), (8, 6), (9, 6), (10, 6), (6, 7), (7, 7), (8, 7), (9, 7), (10, 7), (7, 8), (8, 8), (9, 8), (7, 9), (8, 9), (9, 9),
                     (7, 10), (8, 10), (8, 11), (8, 12)],
           'core': [(x, y) for y in range(4, 12) for x in range(4, 12) if (x - 7.5) ** 2 + (y - 7.5) ** 2 < 14],
           'rune': [(x, y) for y in range(3, 13) for x in range(3, 13)],
           'frag': [(7, 2), (8, 3), (9, 4), (7, 5), (8, 6), (6, 7), (7, 8), (8, 9), (7, 10), (6, 11), (7, 12)]}[shape]
    for (x, y) in pts:
        k = 0.7 + 0.5 * ((x - 3) / 10.0) * glow - 0.25 * ((y - 3) / 10.0)
        px[x, y] = shade(color, k) + (255,)
    if shape == 'rune':
        for (x, y) in pts:
            if x in (3, 12) or y in (3, 12): px[x, y] = (30, 22, 50, 255)
            else: px[x, y] = (50, 40, 80, 255)
        for (x, y) in [(5, 5), (6, 6), (7, 7), (8, 8), (9, 9), (9, 5), (5, 9), (7, 5), (7, 9), (5, 7), (9, 7)]: px[x, y] = shade(color, 1.3) + (255,)
    if shape == 'core':
        for (x, y) in [(6, 6), (7, 6), (6, 7)]: px[x, y] = (255, 255, 255, 230)
    return img

for s, col in SCHOOLS:
    save(gem(col, 'shard'), f'{IT}/{s}_crystal.png')
MAT = {'awakening_shard': (0x7CF2FF, 'shard'), 'ascension_core': (0xFFD35A, 'core'), 'forbidden_fragment': (0xFF2BD6, 'frag'),
       'core_ember': (0xFF6A1A, 'core'), 'core_frost': (0x8FE3FF, 'core'), 'core_volt': (0xF2E94E, 'core'), 'core_spirit': (0x7DFFB0, 'core'),
       'rune_amplify': (0xFF5A5A, 'rune'), 'rune_efficient': (0x5A8CFF, 'rune'), 'rune_swift': (0x5AFFD0, 'rune'),
       'rune_wide': (0xB45AFF, 'rune'), 'rune_leech': (0xC1121F, 'rune')}
for k, (c, sh) in MAT.items(): save(gem(c, sh), f'{IT}/{k}.png')

def cluster_frame(color, phase):
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0)); px = img.load()
    spikes = [(4, 6, 2), (8, 2, 3), (11, 7, 2), (6, 9, 2), (10, 10, 1)]
    for (x, top, wd) in spikes:
        for y in range(top, 16):
            half = max(0, wd - (y - top) // 5)
            for xx in range(x - half, x + half + 1):
                if 0 <= xx < 16:
                    k = 0.6 + 0.5 * (1 - (y - top) / 14.0) + (0.25 if xx >= x else 0.0)
                    px[xx, y] = shade(color, k) + (255,)
    g = 0.5 + 0.5 * math.sin(phase * math.pi / 2)
    for (x, y) in [(8, 3), (4, 7), (11, 8)]:
        px[x, y] = (255, 255, 255, int(160 + 95 * g))
    return img

for s, col in SCHOOLS:
    save(anim_strip([cluster_frame(col, p) for p in range(4)]), f'{A}/textures/block/{s}_crystal_cluster.png')
    w(f'{A}/textures/block/{s}_crystal_cluster.png.mcmeta', json.dumps({'animation': {'frametime': 6, 'interpolate': True}}))

# ---------------------------------------------------------------- textures: spell icons (32x32)
def glyph(d, kind, c, cx=16, cy=16):
    wcol = (255, 255, 255, 255); col = c + (255,)
    if kind == 'ring':
        d.ellipse([7, 7, 25, 25], outline=wcol, width=3); d.ellipse([13, 13, 19, 19], fill=col)
    elif kind == 'spiral':
        pts = [(16 + math.cos(t / 3) * t * 0.5, 16 + math.sin(t / 3) * t * 0.5) for t in range(0, 30)]
        d.line(pts, fill=wcol, width=2)
    elif kind == 'star':
        pts = []
        for i in range(10):
            r = 11 if i % 2 == 0 else 4.5; a = -math.pi / 2 + i * math.pi / 5
            pts.append((16 + r * math.cos(a), 16 + r * math.sin(a)))
        d.polygon(pts, fill=wcol)
    elif kind == 'bolt':
        d.polygon([(18, 3), (9, 17), (15, 17), (12, 29), (23, 12), (17, 12)], fill=wcol)
    elif kind == 'drop':
        d.polygon([(16, 4), (8, 18), (24, 18)], fill=wcol); d.ellipse([8, 13, 24, 27], fill=wcol)
    elif kind == 'leaf':
        d.ellipse([7, 6, 25, 26], fill=wcol); d.line([(9, 25), (23, 7)], fill=col, width=2)
    elif kind == 'spikes':
        for i in range(-2, 3): d.polygon([(16 + i * 5 - 2, 27), (16 + i * 5 + 2, 27), (16 + i * 5, 8 + abs(i) * 4)], fill=wcol)
    elif kind == 'sun':
        d.ellipse([10, 10, 22, 22], fill=wcol)
        for i in range(8):
            a = i * math.pi / 4
            d.line([(16 + 8 * math.cos(a), 16 + 8 * math.sin(a)), (16 + 13 * math.cos(a), 16 + 13 * math.sin(a))], fill=wcol, width=2)
    elif kind == 'hourglass':
        d.polygon([(8, 5), (24, 5), (16, 16)], fill=wcol); d.polygon([(8, 27), (24, 27), (16, 16)], fill=wcol)
    elif kind == 'chain':
        for i in range(4): d.ellipse([5 + i * 6, 12 + (i % 2) * 4, 13 + i * 6, 20 + (i % 2) * 4], outline=wcol, width=2)
    elif kind == 'wave':
        for k in range(3): d.arc([4 + k * 2, 6 + k * 4, 28 - k * 2, 30 - k * 4], 200, 340, fill=wcol, width=3)
    elif kind == 'cross':
        d.rectangle([14, 5, 18, 27], fill=wcol); d.rectangle([5, 14, 27, 18], fill=wcol)
    elif kind == 'eye':
        d.ellipse([5, 10, 27, 22], outline=wcol, width=2); d.ellipse([13, 13, 19, 19], fill=wcol)
    elif kind == 'burst':
        for i in range(12):
            a = i * math.pi / 6
            d.line([(16 + 4 * math.cos(a), 16 + 4 * math.sin(a)), (16 + 13 * math.cos(a), 16 + 13 * math.sin(a))], fill=wcol, width=2)
    elif kind == 'rift':
        d.polygon([(16, 3), (20, 14), (14, 17), (19, 29), (12, 18), (17, 14)], fill=wcol)

GLYPH = {'singularity': 'spiral', 'rift_step': 'rift', 'gravity_crush': 'ring', 'event_horizon': 'eye',
         'root_snare': 'leaf', 'thorn_barrier': 'ring', 'spore_bloom': 'burst', 'grove_guardians': 'leaf',
         'ice_spikes': 'spikes', 'frost_nova': 'star', 'frozen_prison': 'cross', 'blizzard': 'wave',
         'crimson_chains': 'chain', 'crimson_wave': 'wave', 'blood_seal': 'ring', 'crimson_cataclysm': 'burst',
         'solar_beam': 'bolt', 'mini_sun': 'sun', 'dawnlight': 'star', 'supernova': 'burst',
         'thunderbolt': 'bolt', 'chain_lightning': 'chain', 'static_field': 'ring', 'tempest': 'wave',
         'temporal_slow': 'hourglass', 'rewind': 'spiral', 'chrono_surge': 'bolt', 'time_stop': 'hourglass',
         'arcane_bolt': 'star', 'echo': 'wave', 'arcane_barrage': 'burst', 'arcane_cataclysm': 'star',
         'unstable_rift': 'rift', 'terrain_warp': 'spikes', 'rift_collapse': 'eye'}
for name, school, slot in spells:
    img = Image.new('RGBA', (32, 32), (0, 0, 0, 0)); d = ImageDraw.Draw(img)
    c = rgb(COLORS[school])
    for r in range(15, 0, -1):
        k = 0.35 + 0.65 * (1 - r / 15.0)
        d.ellipse([16 - r, 16 - r, 16 + r - 1, 16 + r - 1], fill=tuple(int(v * k) for v in c) + (255,))
    d.ellipse([1, 1, 30, 30], outline=tuple(min(255, int(v * 1.3)) for v in c) + (255,), width=1)
    kind = GLYPH.get(name, 'star')
    if name.startswith('mimic_'): kind = 'eye'
    glyph(d, kind, c)
    if slot == 'ultimate': d.ellipse([0, 0, 31, 31], outline=(255, 230, 140, 255), width=2)
    save(img, f'{A}/textures/gui/spell/{name}.png')

# (boss and wand 3D models/textures are produced by tools/gen_geo.py)

# ---------------------------------------------------------------- models & blockstates
def model(name, obj): jw(f'{A}/models/item/{name}.json', obj)
WAND_DISPLAY = {
    'gui': {'rotation': [0, 0, 40], 'translation': [0, 0, 0], 'scale': [0.62, 0.62, 0.62]},
    'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.4, 0.4, 0.4]},
    'fixed': {'rotation': [0, 0, 40], 'translation': [0, 0, 0], 'scale': [0.6, 0.6, 0.6]},
    'head': {'rotation': [0, 0, 0], 'translation': [0, 8, 0], 'scale': [0.8, 0.8, 0.8]},
    'thirdperson_righthand': {'rotation': [80, 0, 0], 'translation': [0, 2.5, 1], 'scale': [0.55, 0.55, 0.55]},
    'thirdperson_lefthand': {'rotation': [80, 0, 0], 'translation': [0, 2.5, 1], 'scale': [0.55, 0.55, 0.55]},
    'firstperson_righthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.6, 0.6, 0.6]},
    'firstperson_lefthand': {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': [0.6, 0.6, 0.6]},
}
for s, _ in SCHOOLS:
    model(f'{s}_wand', {'parent': 'minecraft:builtin/entity', 'display': WAND_DISPLAY})
    model(f'{s}_crystal', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{M}:item/{s}_crystal'}})
    model(f'{s}_crystal_cluster', {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{M}:block/{s}_crystal_cluster'}})
    jw(f'{A}/models/block/{s}_crystal_cluster.json', {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'{M}:block/{s}_crystal_cluster'}})
    jw(f'{A}/blockstates/{s}_crystal_cluster.json', {'variants': {'': {'model': f'{M}:block/{s}_crystal_cluster'}}})
model('forbidden_wand', {'parent': 'minecraft:builtin/entity', 'display': WAND_DISPLAY})
for k in MAT: model(k, {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{M}:item/{k}'}})
model('hollow_archmage_spawn_egg', {'parent': 'minecraft:item/template_spawn_egg'})

# ---------------------------------------------------------------- lang
SPELL_TEXT = {
 'singularity': ('Singularity', 'Hurl a dark orb that becomes a gravity well, dragging in everything nearby. Hold to charge.'),
 'rift_step': ('Rift Step', 'Tear a rift and blink forward. Awakened wands damage everything along the trail.'),
 'gravity_crush': ('Gravity Crush', 'Lift enemies into the air, then slam them into the ground.'),
 'event_horizon': ('Event Horizon', 'Ultimate: a black hole with an accretion disc that devours the battlefield.'),
 'root_snare': ('Root Snare', 'Roots erupt along the ground, wounding and rooting enemies. Hold to charge; awakened wands send a fan.'),
 'thorn_barrier': ('Thorn Barrier', 'A thorned ring that heals allies and repels enemies.'),
 'spore_bloom': ('Spore Bloom', 'A spore cloud that heals allies, poisons enemies and makes crops grow.'),
 'grove_guardians': ('Grove Guardians', 'Ultimate: summon guardians of the grove and root every enemy nearby.'),
 'ice_spikes': ('Ice Spikes', 'Spikes erupt in a line, freezing what they pierce. Hold to charge; awakened wands send a fan.'),
 'frost_nova': ('Frost Nova', 'A ring of frost that slows enemies and freezes shallow water.'),
 'frozen_prison': ('Frozen Prison', 'Encase an enemy in ice; the prison shatters for heavy damage.'),
 'blizzard': ('Blizzard', 'Ultimate: a raging blizzard that grinds enemies down and flings them around its eye.'),
 'crimson_chains': ('Crimson Chains', 'Bind an enemy and drag them toward you. Awakened wands bind several.'),
 'crimson_wave': ('Crimson Wave', 'A crescent of crimson energy sweeps outward.'),
 'blood_seal': ('Blood Seal', 'Draw an explosive glyph that slows everything inside, then detonates.'),
 'crimson_cataclysm': ('Crimson Cataclysm', 'Ultimate: five crimson shockwaves; every hit heals you.'),
 'solar_beam': ('Solar Beam', 'A piercing beam of light. Hold to charge; ascended wands sustain and sweep it.'),
 'mini_sun': ('Miniature Sun', 'A small sun scorches enemies, then bursts in a blinding explosion.'),
 'dawnlight': ('Dawnlight', 'Heal and cleanse allies; burn the undead.'),
 'supernova': ('Supernova', 'Ultimate: a sun gathers overhead and detonates across a huge radius.'),
 'thunderbolt': ('Thunderbolt', 'Call down lightning. Hold to charge; awakened wands call a volley.'),
 'chain_lightning': ('Chain Lightning', 'Lightning leaps between enemies with fading damage.'),
 'static_field': ('Static Field', 'Overcharge yourself: move faster and zap nearby enemies.'),
 'tempest': ('Tempest', 'Ultimate: a thunderstorm that rains bolts over a wide area.'),
 'temporal_slow': ('Temporal Slow', 'A time-dilation field slows enemies and projectiles. Hold to charge.'),
 'rewind': ('Rewind', 'Return to where you stood, and how healthy you were, five seconds ago.'),
 'chrono_surge': ('Chrono Surge', 'Hasten yourself and allies while slowing enemies.'),
 'time_stop': ('Time Stop', 'Ultimate: enemies and projectiles in range freeze in time.'),
 'arcane_bolt': ('Arcane Bolt', 'A piercing bolt with an unpredictable wild effect. Hold to charge.'),
 'echo': ('Echo', 'Repeat the last spell you cast, from any wand.'),
 'arcane_barrage': ('Arcane Barrage', 'A volley of homing arcane missiles.'),
 'arcane_cataclysm': ('Arcane Cataclysm', 'Ultimate: telegraphed pillars of arcane energy crash down.'),
 'unstable_rift': ('Unstable Rift', 'Open a rift that randomly pulls, shoves, scatters and lashes enemies.'),
 'terrain_warp': ('Terrain Warp', 'Warp the ground for a few seconds and toss enemies aloft. Blocks always return.'),
 'rift_collapse': ('Rift Collapse', 'Ultimate: a collapsing rift unleashes every school before imploding.'),
}
lang = {
 'itemGroup.arcana': 'ARCANA: Wands of the Forgotten',
 'key.categories.arcana': 'ARCANA',
 'key.arcana.secondary': 'Cast Secondary Spell', 'key.arcana.special': 'Cast Special Spell',
 'key.arcana.ultimate': 'Cast Ultimate Spell', 'key.arcana.wheel': 'Spell Wheel (hold)', 'key.arcana.menu': 'Wand Menu',
 'stage.arcana.0': 'Dormant', 'stage.arcana.1': 'Awakened', 'stage.arcana.2': 'Ascended', 'stage.arcana.forbidden': 'Forbidden',
 'tooltip.arcana.forbidden': 'Bound to no school. Borrows every magic.',
 'tooltip.arcana.selected': 'Spell: %s', 'tooltip.arcana.handle': 'Handle: %s', 'tooltip.arcana.core': 'Core: %s',
 'tooltip.arcana.rune': 'Rune: %s', 'tooltip.arcana.menu_hint': 'Press G to evolve and customise',
 'hud.arcana.mastery': 'Mastery %s', 'hud.arcana.unbound': 'Unbound', 'hud.arcana.wheel': 'Spell Wheel', 'hud.arcana.ultimate': 'Ultimate \u00b7 %s',
 'hud.arcana.cost': 'Mana %s  |  Cooldown %ss',
 'cast.arcana.fail.disabled': 'This spell is disabled', 'cast.arcana.fail.stage': 'Evolve your wand to use this spell',
 'cast.arcana.fail.mimic_locked': 'Cast that school\'s magic first to copy it', 'cast.arcana.fail.cooldown': 'Spell on cooldown',
 'cast.arcana.fail.mana': 'Not enough mana', 'cast.arcana.fail.nothing': 'The spell fizzles',
 'combo.arcana.trigger': 'Combo: %s!',
 'combo.arcana.shatter': 'Shatter', 'combo.arcana.eclipse': 'Eclipse', 'combo.arcana.singular_surge': 'Singular Surge',
 'combo.arcana.photosynthesis': 'Photosynthesis', 'combo.arcana.steam_burst': 'Steam Burst', 'combo.arcana.time_echo': 'Time Echo',
 'combo.arcana.event_collapse': 'Event Collapse', 'combo.arcana.bloodbloom': 'Bloodbloom', 'combo.arcana.resonance': 'Resonance',
 'screen.arcana.wand': 'Wand', 'screen.arcana.evolve': 'Evolve', 'screen.arcana.no_evolution': 'The Forbidden Wand cannot evolve',
 'screen.arcana.max_stage': 'Fully ascended', 'screen.arcana.req.mastery': 'Mastery level %s', 'screen.arcana.req.item': '%s x %s',
 'screen.arcana.cost_hint': 'Changing a part consumes the new item and returns the old one.',
 'customize.arcana.field.0': 'Handle', 'customize.arcana.field.1': 'Crystal colour', 'customize.arcana.field.2': 'Core',
 'customize.arcana.field.3': 'Particles', 'customize.arcana.field.4': 'Rune I', 'customize.arcana.field.5': 'Rune II',
 'customize.arcana.missing': 'You need: %s', 'customize.arcana.duplicate_rune': 'That rune is already installed',
 'evolve.arcana.fail.mastery': 'Requires mastery level %s', 'evolve.arcana.fail.items': 'Missing evolution materials',
 'evolve.arcana.success.1': 'Your wand awakens!', 'evolve.arcana.success.2': 'Your wand ascends!',
 'color.arcana.school': 'School',
 'handle.arcana.oak': 'Oak', 'handle.arcana.bone': 'Bone (-10% cooldowns)', 'handle.arcana.gilded': 'Gilded (+30 mana)',
 'handle.arcana.obsidian': 'Obsidian (+12% damage)', 'handle.arcana.echo': 'Echo (-12% mana cost)',
 'core.arcana.none': 'None', 'core.arcana.ember': 'Ember (ignites)', 'core.arcana.frost': 'Frost (chills)', 'core.arcana.volt': 'Volt (arcs)', 'core.arcana.spirit': 'Spirit (heals)',
 'fx.arcana.default': 'School', 'fx.arcana.stars': 'Stars', 'fx.arcana.embers': 'Embers', 'fx.arcana.souls': 'Souls', 'fx.arcana.glyphs': 'Glyphs', 'fx.arcana.fireflies': 'Fireflies',
 'rune.arcana.none': 'Empty', 'rune.arcana.amplify': 'Amplify (+25% dmg, +15% mana)', 'rune.arcana.efficient': 'Efficient (-25% mana)',
 'rune.arcana.swift': 'Swift (-25% cooldown)', 'rune.arcana.wide': 'Wide (+30% area, -8% dmg)', 'rune.arcana.leech': 'Leech (heal 15% of damage)',
 'entity.arcana.hollow_archmage': 'The Hollow Archmage', 'entity.arcana.spell_fx': 'Spell Effect', 'entity.arcana.grove_guardian': 'Grove Guardian',
 'boss.arcana.phase.2': 'The Archmage tears open the void!', 'boss.arcana.phase.3': 'The Archmage unravels completely!',
 'item.arcana.hollow_archmage_spawn_egg': 'Hollow Archmage Spawn Egg',
 'block.arcana.dummy': '',
 'advancements.arcana.root.title': 'ARCANA', 'advancements.arcana.root.desc': 'Wands of the Forgotten',
}
del lang['block.arcana.dummy']
for s, _ in SCHOOLS:
    S = s.capitalize()
    lang[f'school.arcana.{s}'] = S
    lang[f'item.arcana.{s}_wand'] = f'{S} Wand'
    lang[f'item.arcana.{s}_crystal'] = f'{S} Crystal'
    lang[f'item.arcana.{s}_crystal.lore'] = f'A shard of {s} magic, torn from the deep places.'
    lang[f'block.arcana.{s}_crystal_cluster'] = f'{S} Crystal Cluster'
lang['item.arcana.forbidden_wand'] = 'The Forbidden Wand'
lang['school.arcana.forbidden'] = 'Forbidden'
MATNAMES = {'awakening_shard': ('Awakening Shard', 'Stirs a dormant wand to wakefulness.'),
            'ascension_core': ('Ascension Core', 'The heart of a fallen archmage. Lifts a wand beyond itself.'),
            'forbidden_fragment': ('Forbidden Fragment', 'A piece of a wand that should not exist.'),
            'core_ember': ('Ember Core', 'Spells ignite their targets.'), 'core_frost': ('Frost Core', 'Spells chill their targets.'),
            'core_volt': ('Volt Core', 'Spells arc to nearby enemies.'), 'core_spirit': ('Spirit Core', 'Spells return a little life to you.'),
            'rune_amplify': ('Amplify Rune', 'More power at a higher mana cost.'), 'rune_efficient': ('Efficient Rune', 'Spells cost less mana.'),
            'rune_swift': ('Swift Rune', 'Spells recharge faster.'), 'rune_wide': ('Wide Rune', 'Spells cover more ground.'),
            'rune_leech': ('Leech Rune', 'Heal for a share of the damage you deal.')}
for k, (n, l) in MATNAMES.items():
    lang[f'item.arcana.{k}'] = n; lang[f'item.arcana.{k}.lore'] = l
for name, school, slot in spells:
    n, d = SPELL_TEXT[name] if name in SPELL_TEXT else (None, None)
    if name.startswith('mimic_'):
        sch = name[6:].capitalize(); n, d = f'Mimic: {sch}', f'Copy the primary spell of {sch} magic. Only works on schools you cast recently.'
    lang[f'spell.arcana.{name}'] = n; lang[f'spell.arcana.{name}.desc'] = d
jw(f'{A}/lang/en_us.json', dict(sorted(lang.items())))

# ---------------------------------------------------------------- tags
jw(f'{D}/tags/item/wands.json', {'values': [f'{M}:{s}_wand' for s, _ in SCHOOLS] + [f'{M}:forbidden_wand']})
jw(f'{D}/tags/item/crystals.json', {'values': [f'{M}:{s}_crystal' for s, _ in SCHOOLS]})
jw(os.path.join(ROOT, 'data', 'minecraft', 'tags', 'block', 'mineable', 'pickaxe.json'), {'values': [f'{M}:{s}_crystal_cluster' for s, _ in SCHOOLS]})

# ---------------------------------------------------------------- recipes
FLAVOR = {'void': 'minecraft:ender_pearl', 'verdant': 'minecraft:moss_block', 'glacier': 'minecraft:blue_ice', 'crimson': 'minecraft:crimson_fungus',
          'solar': 'minecraft:glowstone', 'storm': 'minecraft:lightning_rod', 'chronos': 'minecraft:clock', 'arcane': 'minecraft:amethyst_shard'}
def R(name, obj): jw(f'{D}/recipe/{name}.json', obj)
for s, _ in SCHOOLS:
    R(f'{s}_wand', {'type': 'minecraft:crafting_shaped', 'category': 'equipment', 'pattern': ['  C', ' S ', 'X  '],
                    'key': {'C': {'item': f'{M}:{s}_crystal'}, 'S': {'item': 'minecraft:stick'}, 'X': {'item': FLAVOR[s]}},
                    'result': {'id': f'{M}:{s}_wand', 'count': 1}})
R('forbidden_wand', {'type': 'minecraft:crafting_shapeless', 'category': 'equipment',
                     'ingredients': [{'item': f'{M}:forbidden_fragment'}] * 3 + [{'item': f'{M}:ascension_core'}, {'item': 'minecraft:nether_star'}],
                     'result': {'id': f'{M}:forbidden_wand', 'count': 1}})
def shapeless(name, cat, ings, out): R(name, {'type': 'minecraft:crafting_shapeless', 'category': cat, 'ingredients': [{'item': i} for i in ings], 'result': {'id': f'{M}:{out}', 'count': 1}})
shapeless('core_ember', 'misc', [f'{M}:crimson_crystal', 'minecraft:blaze_powder', 'minecraft:blaze_powder'], 'core_ember')
shapeless('core_frost', 'misc', [f'{M}:glacier_crystal', 'minecraft:packed_ice', 'minecraft:packed_ice'], 'core_frost')
shapeless('core_volt', 'misc', [f'{M}:storm_crystal', 'minecraft:copper_ingot', 'minecraft:copper_ingot'], 'core_volt')
shapeless('core_spirit', 'misc', [f'{M}:verdant_crystal', 'minecraft:soul_sand', 'minecraft:soul_sand'], 'core_spirit')
shapeless('rune_amplify', 'misc', [f'{M}:arcane_crystal', 'minecraft:redstone', 'minecraft:redstone', 'minecraft:gold_nugget'], 'rune_amplify')
shapeless('rune_efficient', 'misc', [f'{M}:arcane_crystal', 'minecraft:lapis_lazuli', 'minecraft:lapis_lazuli', 'minecraft:iron_nugget'], 'rune_efficient')
shapeless('rune_swift', 'misc', [f'{M}:chronos_crystal', 'minecraft:sugar', 'minecraft:sugar', 'minecraft:gold_nugget'], 'rune_swift')
shapeless('rune_wide', 'misc', [f'{M}:void_crystal', 'minecraft:ender_pearl', 'minecraft:amethyst_shard', 'minecraft:iron_nugget'], 'rune_wide')
shapeless('rune_leech', 'misc', [f'{M}:crimson_crystal', 'minecraft:spider_eye', 'minecraft:sweet_berries', 'minecraft:gold_nugget'], 'rune_leech')

# ---------------------------------------------------------------- loot tables
def item_entry(item, weight=1, count=None, extra=None):
    e = {'type': 'minecraft:item', 'name': item, 'weight': weight}
    fn = []
    if count: fn.append({'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': count[0], 'max': count[1]}})
    if fn: e['functions'] = fn
    return e
def pool(rolls, entries, bonus=None):
    p = {'rolls': rolls, 'entries': entries}
    if bonus is not None: p['bonus_rolls'] = bonus
    return p
def chest(name, pools): jw(f'{D}/loot_table/chests/{name}.json', {'type': 'minecraft:chest', 'pools': pools})
crystals_all = lambda lo, hi: [item_entry(f'{M}:{s}_crystal', 1, (lo, hi)) for s, _ in SCHOOLS]
runes = [item_entry(f'{M}:{k}', 1) for k in MAT if k.startswith('rune_')]
cores = [item_entry(f'{M}:{k}', 1) for k in MAT if k.startswith('core_')]
filler = [item_entry('minecraft:gold_nugget', 3, (2, 8)), item_entry('minecraft:lapis_lazuli', 3, (3, 9)), item_entry('minecraft:amethyst_shard', 3, (2, 6)), item_entry('minecraft:book', 2, (1, 3))]
chest('ruins', [pool({'type': 'minecraft:uniform', 'min': 2, 'max': 4}, crystals_all(1, 2) + filler),
                pool({'type': 'minecraft:constant', 'value': 1}, [item_entry(f'{M}:awakening_shard', 1), {'type': 'minecraft:empty', 'weight': 3}])])
chest('library', [pool({'type': 'minecraft:uniform', 'min': 3, 'max': 5}, runes + cores + filler),
                  pool({'type': 'minecraft:constant', 'value': 1}, [item_entry(f'{M}:awakening_shard', 1), {'type': 'minecraft:empty', 'weight': 1}])])
chest('tower', [pool({'type': 'minecraft:uniform', 'min': 3, 'max': 5}, cores + crystals_all(1, 3) + filler),
                pool({'type': 'minecraft:constant', 'value': 1}, [item_entry(f'{M}:awakening_shard', 2), {'type': 'minecraft:empty', 'weight': 1}])])
chest('shrine', [pool({'type': 'minecraft:uniform', 'min': 1, 'max': 3}, crystals_all(1, 3) + runes),
                 pool({'type': 'minecraft:constant', 'value': 1}, [item_entry(f'{M}:awakening_shard', 1), item_entry(f'{M}:ascension_core', 1), {'type': 'minecraft:empty', 'weight': 14}])])
chest('dungeon', [pool({'type': 'minecraft:uniform', 'min': 3, 'max': 6}, crystals_all(2, 4) + runes + cores + filler),
                  pool({'type': 'minecraft:constant', 'value': 1}, [item_entry(f'{M}:ascension_core', 1), item_entry(f'{M}:awakening_shard', 3), item_entry(f'{M}:forbidden_fragment', 1), {'type': 'minecraft:empty', 'weight': 3}])])
for s, _ in SCHOOLS:
    jw(f'{D}/loot_table/blocks/{s}_crystal_cluster.json', {'type': 'minecraft:block', 'random_sequence': f'{M}:blocks/{s}_crystal_cluster', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{M}:{s}_crystal', 'functions': [
            {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 1, 'max': 3}},
            {'function': 'minecraft:explosion_decay'}]}]}]})
jw(f'{D}/loot_table/entities/hollow_archmage.json', {'type': 'minecraft:entity', 'pools': [
    {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{M}:ascension_core'}]},
    {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{M}:awakening_shard', 'functions': [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': 2, 'max': 4}}]}]},
    {'rolls': {'type': 'minecraft:uniform', 'min': 6, 'max': 10}, 'entries': crystals_all(1, 3)},
    {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 3}, 'entries': runes + cores},
    {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': f'{M}:forbidden_fragment', 'weight': 1}, {'type': 'minecraft:empty', 'weight': 2}]}]})

# ---------------------------------------------------------------- advancements
def adv(name, parent, icon, title, desc, criteria, frame='task', requirements=None, hidden=False):
    d = {'display': {'icon': {'id': icon}, 'title': {'translate': f'advancements.arcana.{name}.title'}, 'description': {'translate': f'advancements.arcana.{name}.desc'},
                     'frame': frame, 'show_toast': True, 'announce_to_chat': frame != 'task', 'hidden': hidden},
         'criteria': criteria}
    if parent: d['parent'] = f'{M}:{parent}'
    if requirements: d['requirements'] = requirements
    jw(f'{D}/advancement/{name}.json', d)
    lang[f'advancements.arcana.{name}.title'] = title; lang[f'advancements.arcana.{name}.desc'] = desc
d = {'display': {'icon': {'id': f'{M}:arcane_wand'}, 'title': {'translate': 'advancements.arcana.root.title'}, 'description': {'translate': 'advancements.arcana.root.desc'},
                 'background': 'minecraft:textures/block/amethyst_block.png', 'frame': 'task', 'show_toast': False, 'announce_to_chat': False},
     'criteria': {'tick': {'trigger': 'minecraft:tick'}}}
jw(f'{D}/advancement/root.json', d)
inv = lambda *items, **kw: {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [dict(items=i, **kw) for i in items]}}
adv('first_crystal', 'root', f'{M}:void_crystal', 'Deep Glimmer', 'Find a crystal formation underground and mine a school crystal.', {'c': inv(f'#{M}:crystals')})
adv('first_wand', 'first_crystal', f'{M}:storm_wand', 'Wandwright', 'Craft your first wand.', {'w': inv(f'#{M}:wands')})
adv('awakened', 'first_wand', f'{M}:glacier_wand', 'Stirring', 'Awaken a wand.', {'a': inv(f'#{M}:wands', components={f'{M}:stage': 1})})
adv('ascended', 'awakened', f'{M}:solar_wand', 'Beyond the Veil', 'Ascend a wand.', {'a': inv(f'#{M}:wands', components={f'{M}:stage': 2})}, frame='goal')
adv('collector', 'first_wand', f'{M}:chronos_wand', 'Eight Schools', 'Own a wand of every school.',
    {s: inv(f'{M}:{s}_wand') for s, _ in SCHOOLS}, frame='challenge', requirements=[[s for s, _ in SCHOOLS]])
adv('library', 'first_crystal', 'minecraft:book', 'Forgotten Pages', 'Obtain a spell rune or core from a hidden library or tower.',
    {'r': {'trigger': 'minecraft:inventory_changed', 'conditions': {'items': [{'items': [f'{M}:{k}' for k in MAT if k.startswith(('rune_', 'core_'))]}]}}})
adv('archmage', 'ascended', f'{M}:ascension_core', 'Hollow No More', 'Defeat the Hollow Archmage.',
    {'k': {'trigger': 'minecraft:player_killed_entity', 'conditions': {'entity': [{'condition': 'minecraft:entity_properties', 'entity': 'this', 'predicate': {'type': f'{M}:hollow_archmage'}}]}}}, frame='challenge')
adv('forbidden', 'archmage', f'{M}:forbidden_wand', 'The Wand That Should Not Be', 'Assemble the Forbidden Wand.', {'f': inv(f'{M}:forbidden_wand')}, frame='challenge', hidden=True)
jw(f'{A}/lang/en_us.json', dict(sorted(lang.items())))

# ---------------------------------------------------------------- worldgen: crystals
RARITY = {'void': 5, 'verdant': 4, 'glacier': 4, 'crimson': 5, 'solar': 6, 'storm': 5, 'chronos': 7, 'arcane': 3}
for s, _ in SCHOOLS:
    jw(f'{D}/worldgen/configured_feature/crystal_{s}.json', {'type': 'minecraft:random_patch', 'config': {'tries': 12, 'xz_spread': 4, 'y_spread': 2, 'feature': {
        'feature': {'type': 'minecraft:simple_block', 'config': {'to_place': {'type': 'minecraft:simple_state_provider', 'state': {'Name': f'{M}:{s}_crystal_cluster'}}}},
        'placement': [{'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}}]}}})
    jw(f'{D}/worldgen/placed_feature/crystal_{s}.json', {'feature': f'{M}:crystal_{s}', 'placement': [
        {'type': 'minecraft:count', 'count': 8}, {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform', 'min_inclusive': {'absolute': -58}, 'max_inclusive': {'absolute': 40}}},
        {'type': 'minecraft:environment_scan', 'direction_of_search': 'down', 'target_condition': {'type': 'minecraft:solid'},
         'allowed_search_condition': {'type': 'minecraft:matching_blocks', 'blocks': ['minecraft:air', 'minecraft:cave_air']}, 'max_steps': 16},
        {'type': 'minecraft:random_offset', 'xz_spread': 0, 'y_spread': 1},
        {'type': 'minecraft:rarity_filter', 'chance': RARITY[s]}, {'type': 'minecraft:biome'}]})
    jw(f'{D}/neoforge/biome_modifier/crystal_{s}.json', {'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': f'{M}:crystal_{s}', 'step': 'underground_decoration'})

# ---------------------------------------------------------------- structures (NBT templates)
def nbt_str(s): b = s.encode(); return struct.pack('>H', len(b)) + b
class T:  # tags
    BYTE, INT, DOUBLE, STRING, LIST, COMPOUND = 1, 3, 6, 8, 9, 10
def p_int(v): return struct.pack('>i', v)
def payload(kind, v):
    if kind == T.INT: return p_int(v)
    if kind == T.BYTE: return struct.pack('>b', v)
    if kind == T.DOUBLE: return struct.pack('>d', v)
    if kind == T.STRING: return nbt_str(v)
    if kind == T.LIST:
        k, items = v
        return struct.pack('>b', k) + p_int(len(items)) + b''.join(payload(k, i) for i in items)
    if kind == T.COMPOUND:
        return b''.join(struct.pack('>b', k) + nbt_str(n) + payload(k, val) for n, (k, val) in v.items()) + b'\x00'
def C(**kw): return (T.COMPOUND, kw)

class Struct:
    def __init__(self, sx, sy, sz): self.size = (sx, sy, sz); self.b = {}; self.nbt = {}; self.entities = []
    def set(self, x, y, z, name, **props):
        if 0 <= x < self.size[0] and 0 <= y < self.size[1] and 0 <= z < self.size[2]: self.b[(x, y, z)] = (name, tuple(sorted(props.items())))
    def fill(self, x0, y0, z0, x1, y1, z1, name, **props):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1): self.set(x, y, z, name, **props)
    def hollow(self, x0, y0, z0, x1, y1, z1, name):
        self.fill(x0, y0, z0, x1, y1, z1, name)
        self.fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, 'minecraft:air')
    def chest(self, x, y, z, loot, facing='north'):
        self.set(x, y, z, 'minecraft:chest', facing=facing)
        self.nbt[(x, y, z)] = C(id=(T.STRING, 'minecraft:chest'), LootTable=(T.STRING, f'{M}:chests/{loot}'))
    def write(self, name, base=None):
        palette = []; idx = {}
        blocks = []
        for pos, key in sorted(self.b.items()):
            if key not in idx: idx[key] = len(palette); palette.append(key)
            e = {'pos': (T.LIST, (T.INT, list(pos))), 'state': (T.INT, idx[key])}
            if pos in self.nbt: e['nbt'] = self.nbt[pos]
            blocks.append(C(**e))
        pal = []
        for (n, props) in palette:
            e = {'Name': (T.STRING, n)}
            if props: e['Properties'] = C(**{k: (T.STRING, str(v)) for k, v in props})
            pal.append(C(**e))
        ents = []
        for (pos, ent) in self.entities:
            ents.append(C(pos=(T.LIST, (T.DOUBLE, list(pos))), blockPos=(T.LIST, (T.INT, [int(math.floor(c)) for c in pos])), nbt=ent))
        root = C(DataVersion=(T.INT, 3955), size=(T.LIST, (T.INT, list(self.size))), palette=(T.LIST, (T.COMPOUND, [p[1] for p in pal])),
                 blocks=(T.LIST, (T.COMPOUND, [b[1] for b in blocks])), entities=(T.LIST, (T.COMPOUND, [e[1] for e in ents])))
        data = struct.pack('>b', T.COMPOUND) + nbt_str('') + payload(T.COMPOUND, root[1])
        out = base or f'{D}/structure'
        os.makedirs(out, exist_ok=True)
        with gzip.GzipFile(f'{out}/{name}.nbt', 'wb', mtime=0) as f: f.write(data)

rng = random.Random(1337)
def rubble(name_a, name_b, p=0.35): return name_a if rng.random() > p else name_b

# --- ancient ruins (surface)
s = Struct(13, 8, 13)
s.fill(0, 0, 0, 12, 0, 12, 'minecraft:mossy_stone_bricks')
for x in range(13):
    for z in range(13):
        if rng.random() < 0.25: s.set(x, 0, z, 'minecraft:cracked_stone_bricks')
        if rng.random() < 0.1: s.set(x, 0, z, 'minecraft:air')
for (px_, pz_) in [(1, 1), (1, 11), (11, 1), (11, 11), (1, 6), (11, 6), (6, 1), (6, 11)]:
    h = rng.randint(2, 6)
    for y in range(1, h + 1): s.set(px_, y, pz_, rubble('minecraft:stone_bricks', 'minecraft:mossy_stone_bricks'))
    if h < 6: s.set(px_, h + 1, pz_, 'minecraft:stone_brick_slab')
s.fill(5, 1, 5, 7, 1, 7, 'minecraft:chiseled_stone_bricks')
s.set(6, 2, 6, 'minecraft:amethyst_block'); s.set(6, 3, 6, f'{M}:arcane_crystal_cluster')
s.chest(5, 2, 6, 'ruins', 'east'); s.chest(7, 2, 6, 'ruins', 'west')
for x in range(13):
    for z in range(13):
        if rng.random() < 0.08: s.set(x, 1, z, 'minecraft:moss_carpet')
s.write('ancient_ruins')

# --- wizard tower (surface)
s = Struct(11, 26, 11); cx = cz = 5
def disc(y, r, name, hollow=False, inner=None):
    for x in range(11):
        for z in range(11):
            d = math.hypot(x - cx, z - cz)
            if d <= r + 0.3 and (not hollow or d > r - 1.2): s.set(x, y, z, name)
for y in range(0, 20):
    disc(y, 4.6, 'minecraft:stone_bricks' if y % 5 else 'minecraft:chiseled_stone_bricks', hollow=True)
for y in (0, 6, 12, 18): disc(y, 3.6, 'minecraft:dark_oak_planks')
for y in range(1, 19):
    s.set(cx, y, 1, 'minecraft:ladder', facing='south') if y % 6 != 0 else None
    for fy in (6, 12, 18):
        if y == fy: s.set(cx, y, 1, 'minecraft:dark_oak_planks')
for y in (3, 9, 15):
    for (x, z) in [(cx, 0), (cx, 10), (0, cz), (10, cz)]: s.set(x, y, z, 'minecraft:glass_pane')
s.set(cx, 1, 10, 'minecraft:air'); s.set(cx, 2, 10, 'minecraft:air'); s.set(cx, 1, 0, 'minecraft:air')
for r_, y in zip([4.6, 3.8, 3.0, 2.2, 1.4, 0.6], range(20, 26)): disc(y, r_, 'minecraft:deepslate_tile_slab' if r_ < 1 else 'minecraft:deepslate_bricks')
for y in (7, 13): s.set(3, y, 4, 'minecraft:bookshelf'); s.set(3, y + 1, 4, 'minecraft:bookshelf'); s.set(7, y, 6, 'minecraft:bookshelf')
s.set(5, 13, 6, 'minecraft:enchanting_table'); s.chest(4, 7, 7, 'tower'); s.chest(6, 13, 3, 'tower'); s.chest(5, 19, 5, 'tower')
s.set(5, 19, 6, f'{M}:chronos_crystal_cluster'); s.set(4, 19, 4, f'{M}:storm_crystal_cluster')
s.write('wizard_tower')

# --- hidden library (underground)
s = Struct(15, 7, 15)
s.hollow(0, 0, 0, 14, 6, 14, 'minecraft:deepslate_bricks')
s.fill(1, 0, 1, 13, 0, 13, 'minecraft:polished_deepslate')
for z in (2, 4, 6, 8, 10, 12):
    for x in (2, 3, 11, 12):
        for y in (1, 2, 3): s.set(x, y, z, 'minecraft:bookshelf')
s.fill(6, 0, 5, 8, 0, 9, 'minecraft:chiseled_deepslate')
s.set(7, 1, 7, 'minecraft:lectern', facing='south'); s.set(6, 1, 6, 'minecraft:enchanting_table')
s.chest(7, 1, 12, 'library', 'north'); s.chest(1, 1, 7, 'library', 'east'); s.chest(13, 1, 7, 'library', 'west')
s.set(7, 5, 7, 'minecraft:amethyst_cluster', facing='down')
for (x, z) in [(1, 1), (13, 1), (1, 13), (13, 13)]: s.set(x, 1, z, 'minecraft:soul_lantern')
s.set(7, 0, 14, 'minecraft:air'); s.set(7, 1, 14, 'minecraft:air'); s.set(7, 2, 14, 'minecraft:air')
s.write('hidden_library')

# --- shrines (surface, one per school)
for sname, col in SCHOOLS:
    s = Struct(9, 7, 9)
    s.fill(0, 0, 0, 8, 0, 8, 'minecraft:polished_andesite')
    s.fill(1, 1, 1, 7, 1, 7, 'minecraft:smooth_stone')
    for (x, z) in [(1, 1), (1, 7), (7, 1), (7, 7)]:
        for y in range(2, 6): s.set(x, y, z, 'minecraft:chiseled_stone_bricks' if y == 5 else 'minecraft:stone_brick_wall')
    s.fill(1, 6, 1, 7, 6, 7, 'minecraft:stone_brick_slab', type='top')
    s.fill(3, 2, 3, 5, 2, 5, 'minecraft:chiseled_quartz_block')
    s.set(4, 3, 4, f'{M}:{sname}_crystal_cluster')
    s.set(4, 2, 4, 'minecraft:amethyst_block')
    s.chest(4, 2, 2, 'shrine', 'south')
    s.set(4, 1, 0, 'minecraft:stone_brick_stairs', facing='south'); s.set(4, 0, 0, 'minecraft:stone_brick_stairs', facing='south')
    s.write(f'shrine_{sname}')

# --- secret dungeon with boss (underground)
s = Struct(17, 9, 17)
s.hollow(0, 0, 0, 16, 8, 16, 'minecraft:deepslate_tiles')
s.fill(1, 0, 1, 15, 0, 15, 'minecraft:polished_blackstone_bricks')
for x in range(1, 16):
    for z in range(1, 16):
        if (x + z) % 2 == 0: s.set(x, 0, z, 'minecraft:deepslate_tiles')
for (x, z) in [(3, 3), (3, 13), (13, 3), (13, 13)]:
    for y in range(1, 8): s.set(x, y, z, 'minecraft:polished_deepslate')
    s.set(x, 8, z, 'minecraft:crying_obsidian'); s.set(x, 7, z, 'minecraft:soul_lantern', hanging='true')
s.fill(6, 1, 6, 10, 1, 10, 'minecraft:crying_obsidian'); s.fill(7, 2, 7, 9, 2, 9, 'minecraft:obsidian')
s.set(8, 3, 8, f'{M}:void_crystal_cluster')
s.chest(2, 1, 8, 'dungeon', 'east'); s.chest(14, 1, 8, 'dungeon', 'west'); s.chest(8, 1, 15, 'dungeon', 'north')
for (x, z) in [(8, 1), (1, 8)]: s.set(x, 1, z, 'minecraft:air')
s.set(8, 1, 0, 'minecraft:air'); s.set(8, 2, 0, 'minecraft:air')
s.entities.append(((8.5, 4.0, 8.5), C(id=(T.STRING, f'{M}:hollow_archmage'), PersistenceRequired=(T.BYTE, 1))))
s.write('archmage_sanctum')

# --- game-test arena (lives in the gametest source set, not the shipped jar)
s = Struct(32, 8, 32); s.fill(0, 0, 0, 31, 0, 31, 'minecraft:stone')
s.write('arena', os.path.join(ROOT, '..', '..', 'gametest', 'resources', 'data', 'arcana', 'structure'))

# --- structure definitions
STRUCTS = [('ancient_ruins', 'surface_structures', True, 32, 12, 8112, 'ruins'),
           ('wizard_tower', 'surface_structures', True, 48, 16, 8113, 'tower'),
           ('hidden_library', 'underground_structures', False, 40, 14, 8114, 'library'),
           ('archmage_sanctum', 'underground_structures', False, 60, 20, 8115, 'sanctum')]
for i, (sname, _) in enumerate(SCHOOLS): STRUCTS.append((f'shrine_{sname}', 'surface_structures', True, 48 + 2 * i, 18, 8120 + i, f'shrine_{sname}'))
for name, step, surface, spacing, sep, salt, tag in STRUCTS:
    jw(f'{D}/worldgen/template_pool/{name}.json', {'fallback': 'minecraft:empty', 'elements': [{'weight': 1, 'element': {
        'element_type': 'minecraft:single_pool_element', 'location': f'{M}:{name}', 'processors': 'minecraft:empty', 'projection': 'rigid'}}]})
    st = {'type': 'minecraft:jigsaw', 'biomes': f'#{M}:has_structure/{tag}', 'step': step, 'spawn_overrides': {},
          'terrain_adaptation': 'beard_thin' if surface else 'bury', 'start_pool': f'{M}:{name}', 'size': 1,
          'start_height': {'absolute': 0} if surface else {'absolute': -30 if 'sanctum' in name else -24},
          'max_distance_from_center': 30, 'use_expansion_hack': False}
    if surface: st['project_start_to_heightmap'] = 'WORLD_SURFACE_WG'
    jw(f'{D}/worldgen/structure/{name}.json', st)
    jw(f'{D}/worldgen/structure_set/{name}.json', {'structures': [{'structure': f'{M}:{name}', 'weight': 1}],
        'placement': {'type': 'minecraft:random_spread', 'spacing': spacing, 'separation': sep, 'salt': salt}})
    jw(f'{D}/tags/worldgen/biome/has_structure/{tag}.json', {'values': ['#minecraft:is_overworld']})

print('generated', len(spells), 'spells')
