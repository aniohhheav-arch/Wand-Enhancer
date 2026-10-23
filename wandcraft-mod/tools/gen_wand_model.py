"""Builds assets/wandcraft/models/item/wand.json: a detailed 3D wand along +Y. Run from wandcraft-mod/."""
import json, math

BARK, WOOD, GOLD, DGOLD, WRAP, GEM, CORE, SILVER, ROOT, LEAF, WHITE, SGEM = range(12)
elements = []

def cell(i):
    cx, cy = (i % 4) * 4, (i // 4) * 4
    return [cx, cy, cx + 4, cy + 4]

def box(a, b, tex, rot=None, glow=False, name=None):
    e = {"from": list(a), "to": list(b), "faces": {f: {"uv": cell(tex), "texture": "#0"} for f in ("north", "east", "south", "west", "up", "down")}}
    if rot: e["rotation"] = {"angle": rot[0], "axis": rot[1], "origin": list(rot[2])}
    if glow: e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    if name: e["name"] = name
    elements.append(e)

c = 8
def centred(y0, y1, w, tex, **kw):
    box((c - w / 2, y0, c - w / 2), (c + w / 2, y1, c + w / 2), tex, **kw)

# pommel: gold diamond made of two crossed cubes
centred(-2.2, -0.2, 1.8, GOLD, rot=(45, "y", (c, -1.2, c)), name="pommel_a")
centred(-2.0, 0.0, 1.5, DGOLD, name="pommel_b")
# handle with violet wrap bands over bark
centred(0, 6.2, 1.4, BARK, name="handle")
for y in (0.6, 2.2, 3.8, 5.4):
    centred(y, y + 0.8, 1.9, WRAP, name="wrap")
# gold collar with fins
centred(6.2, 7.2, 2.4, GOLD, name="collar")
centred(6.5, 7.0, 2.9, DGOLD, rot=(45, "y", (c, 6.75, c)), name="collar_ring")
# main shaft, slightly twisting by alternating offset slices
y = 7.2
i = 0
while y < 14.6:
    off = 0.12 * math.sin(i * 1.3)
    box((c - 0.65 + off, y, c - 0.65 - off), (c + 0.65 + off, y + 1.4, c + 0.65 - off), WOOD if i % 2 == 0 else BARK, name="shaft")
    y += 1.4
    i += 1
# thorn nubs spiralling up the shaft
for k, (dx, dz, yy) in enumerate([(0.8, 0.0, 8.0), (0.0, 0.8, 9.6), (-0.8, 0.0, 11.2), (0.0, -0.8, 12.8)]):
    box((c + dx - 0.25, yy, c + dz - 0.25), (c + dx + 0.25, yy + 0.7, c + dz + 0.25), ROOT, name="nub")
# silver taper under the cradle
centred(14.6, 16.0, 1.1, SILVER, name="taper")
centred(15.7, 16.4, 2.0, GOLD, name="cradle_base")
# four gold prongs curling around the gem
for dx, dz in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
    box((c + dx * 0.95 - 0.2, 16.4, c + dz * 0.95 - 0.2), (c + dx * 0.95 + 0.2, 19.6, c + dz * 0.95 + 0.2), GOLD, name="prong")
    box((c + dx * 0.55 - 0.2, 19.4, c + dz * 0.55 - 0.2), (c + dx * 0.55 + 0.2, 19.9, c + dz * 0.55 + 0.2), GOLD, name="prong_tip")
# the gem: three interlocking rotated cubes read as a faceted crystal
gy = 18.0
centred(gy - 1.3, gy + 1.3, 1.9, GEM, rot=(45, "y", (c, gy, c)), glow=True, name="gem_a")
centred(gy - 1.3, gy + 1.3, 1.9, SGEM, rot=(45, "x", (c, gy, c)), glow=True, name="gem_b")
centred(gy - 1.1, gy + 1.1, 1.5, GEM, rot=(45, "z", (c, gy, c)), glow=True, name="gem_c")
centred(gy - 0.55, gy + 0.55, 1.1, CORE, glow=True, name="gem_core")
# crown spike and floating shards
centred(19.9, 21.6, 0.5, SILVER, name="spike")
centred(21.4, 22.4, 0.7, CORE, rot=(45, "y", (c, 21.9, c)), glow=True, name="spark_tip")
box((10.6, 17.2, c - 0.3), (11.3, 18.4, c + 0.3), GEM, rot=(45, "z", (10.95, 17.8, c)), glow=True, name="shard_r")
box((4.7, 19.0, c - 0.3), (5.4, 20.0, c + 0.3), SGEM, rot=(-45, "z", (5.05, 19.5, c)), glow=True, name="shard_l")
box((c - 0.3, 15.0, 10.4), (c + 0.3, 16.0, 11.0), CORE, rot=(45, "x", (c, 15.5, 10.7)), glow=True, name="shard_f")

# The item itself is rendered by GeckoLib (geo/item/wand.geo.json); this JSON only supplies display transforms.
model = {
    "parent": "minecraft:builtin/entity",
    "textures": {"particle": "wandcraft:item/wand_geo"},
    "display": {
        "gui": {"rotation": [30, 45, -45], "translation": [0.5, -1.5, 0], "scale": [0.62, 0.62, 0.62]},
        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 0, -45], "translation": [0, -2, 0], "scale": [0.8, 0.8, 0.8]},
        "head": {"rotation": [0, 0, 0], "translation": [0, 4, 0], "scale": [0.6, 0.6, 0.6]},
        "thirdperson_righthand": {"rotation": [-10, 0, 0], "translation": [0, 5, 0], "scale": [0.55, 0.55, 0.55]},
        "thirdperson_lefthand": {"rotation": [-10, 0, 0], "translation": [0, 5, 0], "scale": [0.55, 0.55, 0.55]},
        "firstperson_righthand": {"rotation": [-35, 12, 8], "translation": [2, 1.5, 1], "scale": [0.65, 0.65, 0.65]},
        "firstperson_lefthand": {"rotation": [-35, -12, -8], "translation": [-2, 1.5, 1], "scale": [0.65, 0.65, 0.65]},
    },
}
def write():
    with open("src/main/resources/assets/wandcraft/models/item/wand.json", "w") as f:
        json.dump(model, f, indent=1)


if __name__ == "__main__":
    write()
    print(len(elements), "elements")
