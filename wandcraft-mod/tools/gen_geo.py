"""Generates GeckoLib geometry and animations (Blockbench / Bedrock geo format) for the wand and summoned clones.
Run from wandcraft-mod/ after gen_textures.py and gen_wand_model.py. The output files can be opened and edited in Blockbench."""
import json, os, sys
sys.path.insert(0, 'tools')
import gen_wand_model as wm
from PIL import Image

R = 'src/main/resources/assets/wandcraft/'
for d in ('geo/item', 'geo/entity', 'animations/item', 'animations/entity'):
    os.makedirs(R + d, exist_ok=True)

def dump(obj, path):
    with open(R + path, 'w') as f:
        json.dump(obj, f, indent=1)

def uv_box(tex_idx):
    cx, cy = (tex_idx % 4) * 8, (tex_idx // 4) * 8
    return {f: {"uv": [cx, cy], "uv_size": [8, 8]} for f in ("north", "east", "south", "west", "up", "down")}

# ---------------------------------------------------------------------------------------------------------------- wand
def tex_index_of(e):
    u = e['faces']['north']['uv']
    return (u[1] // 4) * 4 + (u[0] // 4)

bones = {"root": {"name": "root", "pivot": [0, 0, 0], "cubes": []}}
GEM_PARTS = {"gem_a", "gem_b", "gem_c", "gem_core", "spark_tip", "spike"}
SHARDS = {"shard_r": 3.0, "shard_l": 4.0, "shard_f": 2.5}
bones["gem"] = {"name": "gem", "parent": "root", "pivot": [0, wm.gy - 8, 0], "cubes": []}
for n in SHARDS:
    bones[n] = {"name": n, "parent": "root", "pivot": [0, wm.gy - 8, 0], "cubes": []}

glow_tex = set()
for e in wm.elements:
    name = e.get('name', 'part')
    f, t = e['from'], e['to']
    cube = {
        "origin": [-(t[0] - 8), f[1] - 8, f[2] - 8],
        "size": [t[0] - f[0], t[1] - f[1], t[2] - f[2]],
        "uv": uv_box(tex_index_of(e)),
    }
    if 'rotation' in e:
        r = e['rotation']; o = r['origin']
        cube["pivot"] = [-(o[0] - 8), o[1] - 8, o[2] - 8]
        ax = {"x": 0, "y": 1, "z": 2}[r['axis']]
        rot = [0, 0, 0]; rot[ax] = r['angle']
        cube["rotation"] = rot
    target = name if name in SHARDS else "gem" if name in GEM_PARTS else "root"
    bones[target]["cubes"].append(cube)
    if 'neoforge_data' in e: glow_tex.add(tex_index_of(e))

geo = {"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.wand", "texture_width": 32, "texture_height": 32,
                    "visible_bounds_width": 4, "visible_bounds_height": 4, "visible_bounds_offset": [0, 0, 0]},
    "bones": list(bones.values())}]}
dump(geo, 'geo/item/wand.geo.json')

# Frame 0 of the animated atlas, plus a glow mask containing only the crystal swatches.
atlas = Image.open(R + 'textures/item/wand.png').crop((0, 0, 32, 32))
atlas.save(R + 'textures/item/wand_geo.png')
mask = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
for i in glow_tex:
    cx, cy = (i % 4) * 8, (i // 4) * 8
    mask.paste(atlas.crop((cx, cy, cx + 8, cy + 8)), (cx, cy))
mask.save(R + 'textures/item/wand_geo_glowing.png')

def kf(frames):  # {time: [x,y,z]}
    return {("%.2f" % k): v for k, v in frames.items()}

def spin(period, sign=1):
    return kf({0: [0, 0, 0], period / 2: [0, sign * 180, 0], period: [0, sign * 360, 0]})

wand_anim = {"format_version": "1.8.0", "animations": {
    "animation.wand.idle": {
        "loop": True, "animation_length": 12,
        "bones": {
            "root": {"rotation": kf({0: [0, 0, 0], 3: [1.5, 0, 1], 6: [0, 0, 0], 9: [-1.5, 0, -1], 12: [0, 0, 0]}),
                     "position": kf({0: [0, 0, 0], 3: [0, 0.35, 0], 6: [0, 0, 0], 9: [0, -0.2, 0], 12: [0, 0, 0]})},
            "gem": {"rotation": spin(12), "scale": kf({0: [1, 1, 1], 1.5: [1.1, 1.1, 1.1], 3: [1, 1, 1], 4.5: [1.1, 1.1, 1.1], 6: [1, 1, 1], 7.5: [1.1, 1.1, 1.1], 9: [1, 1, 1], 10.5: [1.1, 1.1, 1.1], 12: [1, 1, 1]})},
            "shard_r": {"rotation": spin(12, -1), "position": kf({0: [0, 0, 0], 3: [0, 0.8, 0], 6: [0, 0, 0], 9: [0, -0.8, 0], 12: [0, 0, 0]})},
            "shard_l": {"rotation": spin(12, 1), "position": kf({0: [0, 0.4, 0], 6: [0, -0.4, 0], 12: [0, 0.4, 0]})},
            "shard_f": {"rotation": spin(12, -1), "position": kf({0: [0, -0.5, 0], 4: [0, 0.6, 0], 8: [0, -0.5, 0], 12: [0, -0.5, 0]})}
        }},
    "animation.wand.cast": {
        "loop": "play_once", "animation_length": 0.6,
        "bones": {
            "root": {"rotation": kf({0: [0, 0, 0], 0.1: [-9, 0, 0], 0.3: [5, 0, 0], 0.6: [0, 0, 0]}),
                     "position": kf({0: [0, 0, 0], 0.1: [0, 0, 1.2], 0.6: [0, 0, 0]})},
            "gem": {"scale": kf({0: [1, 1, 1], 0.1: [1.6, 1.6, 1.6], 0.6: [1, 1, 1]})},
            "shard_r": {"rotation": kf({0: [0, 0, 0], 0.6: [0, -720, 0]})},
            "shard_l": {"rotation": kf({0: [0, 0, 0], 0.6: [0, 720, 0]})},
            "shard_f": {"rotation": kf({0: [0, 0, 0], 0.6: [0, -720, 0]})}
        }}}}
dump(wand_anim, 'animations/item/wand.animation.json')

# ---------------------------------------------------------------------------------------------------------------- clone
def box(origin, size, uv, **kw):
    c = {"origin": origin, "size": size, "uv": uv}
    c.update(kw)
    return c

clone_bones = [
    {"name": "root", "pivot": [0, 0, 0]},
    {"name": "body", "parent": "root", "pivot": [0, 12, 0], "cubes": [box([-4, 12, -2], [8, 12, 4], [16, 16])]},
    {"name": "head", "parent": "body", "pivot": [0, 24, 0], "cubes": [box([-4, 24, -4], [8, 8, 8], [0, 0])]},
    {"name": "right_arm", "parent": "body", "pivot": [-5, 22, 0], "cubes": [box([-8, 12, -2], [4, 12, 4], [40, 16])]},
    {"name": "left_arm", "parent": "body", "pivot": [5, 22, 0], "cubes": [box([4, 12, -2], [4, 12, 4], [32, 48])]},
    {"name": "right_leg", "parent": "root", "pivot": [-1.9, 12, 0], "cubes": [box([-3.9, 0, -2], [4, 12, 4], [0, 16])]},
    {"name": "left_leg", "parent": "root", "pivot": [1.9, 12, 0], "cubes": [box([-0.1, 0, -2], [4, 12, 4], [16, 48])]},
    # tattered cloak strips trailing behind the body
    {"name": "cloak_a", "parent": "body", "pivot": [-2.5, 23, 2], "cubes": [box([-4, 6, 2], [3.5, 17, 0.6], [0, 32])]},
    {"name": "cloak_b", "parent": "body", "pivot": [0, 23, 2], "cubes": [box([-1.5, 3, 2.4], [3, 20, 0.6], [4, 32])]},
    {"name": "cloak_c", "parent": "body", "pivot": [2.5, 23, 2], "cubes": [box([0.5, 7, 2], [3.5, 16, 0.6], [8, 32])]},
]
dump({"format_version": "1.12.0", "minecraft:geometry": [{
    "description": {"identifier": "geometry.shadow_clone", "texture_width": 64, "texture_height": 64,
                    "visible_bounds_width": 3, "visible_bounds_height": 3.5, "visible_bounds_offset": [0, 1, 0]},
    "bones": clone_bones}]}, 'geo/entity/shadow_clone.geo.json')

def loop_anim(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}

clone_anim = {"format_version": "1.8.0", "animations": {
    "animation.shadow_clone.idle": loop_anim(3, {
        "body": {"position": kf({0: [0, 0, 0], 1.5: [0, 0.4, 0], 3: [0, 0, 0]})},
        "head": {"rotation": kf({0: [0, 0, 0], 1.5: [2, 0, 0], 3: [0, 0, 0]})},
        "right_arm": {"rotation": kf({0: [0, 0, 3], 1.5: [0, 0, 6], 3: [0, 0, 3]})},
        "left_arm": {"rotation": kf({0: [0, 0, -3], 1.5: [0, 0, -6], 3: [0, 0, -3]})},
        "cloak_a": {"rotation": kf({0: [4, 0, 0], 1.5: [9, 0, 2], 3: [4, 0, 0]})},
        "cloak_b": {"rotation": kf({0: [6, 0, 0], 1.2: [12, 0, 0], 3: [6, 0, 0]})},
        "cloak_c": {"rotation": kf({0: [4, 0, 0], 1.5: [10, 0, -2], 3: [4, 0, 0]})}}),
    "animation.shadow_clone.walk": loop_anim(0.8, {
        "root": {"position": kf({0: [0, 0, 0], 0.2: [0, 0.6, 0], 0.4: [0, 0, 0], 0.6: [0, 0.6, 0], 0.8: [0, 0, 0]})},
        "body": {"rotation": kf({0: [8, 0, 0], 0.8: [8, 0, 0]})},
        "right_leg": {"rotation": kf({0: [35, 0, 0], 0.4: [-35, 0, 0], 0.8: [35, 0, 0]})},
        "left_leg": {"rotation": kf({0: [-35, 0, 0], 0.4: [35, 0, 0], 0.8: [-35, 0, 0]})},
        "right_arm": {"rotation": kf({0: [-30, 0, 0], 0.4: [30, 0, 0], 0.8: [-30, 0, 0]})},
        "left_arm": {"rotation": kf({0: [30, 0, 0], 0.4: [-30, 0, 0], 0.8: [30, 0, 0]})},
        "cloak_a": {"rotation": kf({0: [24, 0, 4], 0.4: [34, 0, -4], 0.8: [24, 0, 4]})},
        "cloak_b": {"rotation": kf({0: [30, 0, 0], 0.4: [40, 0, 0], 0.8: [30, 0, 0]})},
        "cloak_c": {"rotation": kf({0: [26, 0, -4], 0.4: [36, 0, 4], 0.8: [26, 0, -4]})}}),
    "animation.shadow_clone.attack": {"loop": "play_once", "animation_length": 0.5, "bones": {
        "body": {"rotation": kf({0: [0, 0, 0], 0.15: [-4, -18, 0], 0.3: [10, 14, 0], 0.5: [0, 0, 0]})},
        "right_arm": {"rotation": kf({0: [0, 0, 0], 0.15: [-110, 0, 0], 0.3: [40, 0, 0], 0.5: [0, 0, 0]})},
        "head": {"rotation": kf({0: [0, 0, 0], 0.15: [-6, 10, 0], 0.3: [6, -8, 0], 0.5: [0, 0, 0]})}}},
    "animation.shadow_clone.spawn": {"loop": "play_once", "animation_length": 0.8, "bones": {
        "root": {"scale": kf({0: [1, 0.05, 1], 0.5: [1, 1.12, 1], 0.8: [1, 1, 1]}),
                 "position": kf({0: [0, -6, 0], 0.5: [0, 0.5, 0], 0.8: [0, 0, 0]})}}}}}
dump(clone_anim, 'animations/entity/shadow_clone.animation.json')
print("geo ok")
