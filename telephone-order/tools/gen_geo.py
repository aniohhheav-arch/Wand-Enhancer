"""GeckoLib (Bedrock geo) models, keyframe animations, textures and glow masks for the delivery actors."""
import json, math, random
from PIL import Image, ImageDraw

TEX = 256
QUAD = {0: (0, 0), 1: (128, 0), 2: (0, 128), 3: (128, 128)}   # material -> uv origin of its 128x128 quadrant

class Geo:
    def __init__(self, name, palette):
        self.name = name
        self.palette = palette          # 4 base colours: hull, dark, accent, glow
        self.bones = {}                 # name -> dict
        self.order = []
        self.anims = {}
        self.bone("root", None, (0, 0, 0))

    def bone(self, name, parent, pivot):
        self.bones[name] = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent: self.bones[name]["parent"] = parent
        self.order.append(name)

    def cube(self, bone, mat, frm, to, rot=None, pivot=None):
        """frm/to in model pixel units, x/z centred on 0 (java-style: x right, y up, z south)."""
        x0, y0, z0 = frm; x1, y1, z1 = to
        c = {"origin": [-x1, y0, z0], "size": [x1 - x0, y1 - y0, z1 - z0], "uv": list(QUAD[mat])}
        if rot:
            px, py, pz = pivot if pivot else ((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2)
            c["pivot"] = [-px, py, pz]
            c["rotation"] = [rot[0], -rot[1], -rot[2]]
        self.bones[bone]["cubes"].append(c)

    def ring(self, bone, mat, radius, n, w, h, d, y=0.0):
        for i in range(n):
            a = 2 * math.pi * i / n
            cx, cz = math.cos(a) * radius, math.sin(a) * radius
            self.cube(bone, mat, (cx - w / 2, y, cz - d / 2), (cx + w / 2, y + h, cz + d / 2), rot=(0, -math.degrees(a) + 90, 0))

    def anim(self, name, length, bones, loop=True):
        self.anims[f"animation.{self.name}.{name}"] = {"loop": loop if loop != "hold" else "hold_on_last_frame", "animation_length": length, "bones": bones}

    def geo_json(self):
        bones = [self.bones[n] for n in self.order]
        for b in bones:
            if not b["cubes"]: b.pop("cubes")
        return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {"identifier": f"geometry.{self.name}", "texture_width": TEX, "texture_height": TEX,
                "visible_bounds_width": 8, "visible_bounds_height": 8, "visible_bounds_offset": [0, 1, 0]}, "bones": bones}]}

    def anim_json(self):
        return {"format_version": "1.8.0", "animations": self.anims}

    def textures(self):
        rnd = random.Random(hash(self.name) & 0xffff)
        img = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 255))
        glow = Image.new("RGBA", (TEX, TEX), (0, 0, 0, 0))
        px = img.load(); gx = glow.load()
        for m, (ox, oy) in QUAD.items():
            base = self.palette[m]
            for y in range(128):
                for x in range(128):
                    f = 1 + rnd.uniform(-0.06, 0.06)
                    if m != 3:
                        if x % 16 == 0 or y % 16 == 0: f *= 0.72           # panel seams
                        elif x % 16 == 1 or y % 16 == 1: f *= 1.15
                        if (x // 16 + y // 16) % 5 == 0 and x % 16 in (3, 12) and y % 16 in (3, 12): f *= 1.35   # rivets
                    else:
                        f *= 1.0 + 0.25 * math.sin((x + y) * 0.2)
                    c = tuple(max(0, min(255, int(v * f))) for v in base)
                    px[ox + x, oy + y] = c + (255,)
                    if m == 3: gx[ox + x, oy + y] = c + (255,)
        if self.name == "drone":   # hazard stripes on the accent plate
            d = ImageDraw.Draw(img)
            for i in range(-128, 128, 16): d.line([(i, 128 + 0), (i + 128, 256)], fill=(20, 20, 20, 255), width=5)
        return img, glow

# --------------------------------------------------------------------------------------------------------------------
def rift():
    g = Geo("rift", [(60, 30, 100), (14, 8, 30), (150, 70, 255), (200, 140, 255)])
    for b, p in (("ring_outer", (0, 0, 0)), ("ring_mid", (0, 0, 0)), ("ring_inner", (0, 0, 0)), ("core", (0, 0, 0)), ("shards", (0, 0, 0)), ("sparks", (0, 0, 0))):
        g.bone(b, "root", p)
    g.ring("ring_outer", 1, 44, 16, 14, 3, 7)
    g.ring("ring_outer", 2, 44, 16, 4, 6, 4, y=-1.5)
    for i in range(8):
        a = i * math.pi / 4
        g.cube("ring_outer", 2, (math.cos(a) * 52 - 2, -2, math.sin(a) * 52 - 2), (math.cos(a) * 52 + 2, 14, math.sin(a) * 52 + 2))
    g.ring("ring_mid", 0, 33, 12, 12, 3, 6, y=0.5)
    g.ring("ring_mid", 3, 33, 12, 3, 2, 3, y=3.5)
    g.ring("ring_inner", 1, 22, 10, 10, 3, 5)
    g.ring("ring_inner", 3, 22, 10, 3, 2, 3, y=3)
    for i in range(12):                       # glowing disc spokes
        a = i * math.pi / 6
        g.cube("core", 3, (-1.5, 0.5, 0), (1.5, 2.0, 20), rot=(0, -math.degrees(a) + 90, 0), pivot=(0, 1, 0))
    g.cube("core", 3, (-6, -0.5, -6), (6, 2.5, 6))
    for i in range(10):                       # crystal shards hanging into the opening
        a = i * math.pi / 5
        r = 14 + (i % 3) * 7
        h = 12 + (i % 4) * 5
        g.cube("shards", 2 if i % 2 else 3, (math.cos(a) * r - 1.5, -h, math.sin(a) * r - 1.5), (math.cos(a) * r + 1.5, 0, math.sin(a) * r + 1.5),
               rot=(8 if i % 2 else -8, 0, 0))
    for i in range(14):                       # orbiting sparks
        a = i * 2 * math.pi / 14
        r = 58 + (i % 3) * 5
        g.cube("sparks", 3, (math.cos(a) * r - 1, 3 + (i % 5) * 2, math.sin(a) * r - 1), (math.cos(a) * r + 1, 5 + (i % 5) * 2, math.sin(a) * r + 1))
    # animations
    g.anim("open", 1.4, {
        "root": {"scale": {"0": [0.02, 0.02, 0.02], "0.5": [0.7, 0.7, 0.7], "1.0": [1.12, 1.12, 1.12], "1.4": [1, 1, 1]}},
        "ring_outer": {"rotation": {"0": [0, 360, 0], "1.4": [0, 0, 0]}},
        "ring_mid": {"rotation": {"0": [0, -540, 0], "1.4": [0, 0, 0]}},
        "ring_inner": {"rotation": {"0": [0, 720, 0], "1.4": [0, 0, 0]}},
        "shards": {"scale": {"0": [0, 0, 0], "0.8": [0, 0, 0], "1.4": [1, 1, 1]}},
    }, loop="hold")
    g.anim("idle", 8, {
        "ring_outer": {"rotation": {"0": [0, 0, 0], "8": [0, 360, 0]}},
        "ring_mid": {"rotation": {"0": [0, 0, 0], "8": [0, -360, 0]}},
        "ring_inner": {"rotation": {"0": [0, 0, 0], "4": [0, 360, 0], "8": [0, 720, 0]}},
        "core": {"scale": {"0": [1, 1, 1], "1": [1.12, 1, 1.12], "2": [0.92, 1, 0.92], "3": [1.1, 1, 1.1], "4": [1, 1, 1]}, "rotation": {"0": [0, 0, 0], "8": [0, -720, 0]}},
        "shards": {"position": {"0": [0, 0, 0], "2": [0, 1.5, 0], "4": [0, 0, 0], "6": [0, -1.5, 0], "8": [0, 0, 0]}, "rotation": {"0": [0, 0, 0], "8": [0, 360, 0]}},
        "sparks": {"rotation": {"0": [0, 0, 0], "3": [0, 360, 0]}},
    })
    g.anim("close", 1.0, {
        "root": {"scale": {"0": [1, 1, 1], "0.3": [1.15, 1.15, 1.15], "1.0": [0.01, 0.01, 0.01]}},
        "ring_outer": {"rotation": {"0": [0, 0, 0], "1.0": [0, 540, 0]}},
        "ring_mid": {"rotation": {"0": [0, 0, 0], "1.0": [0, -720, 0]}},
        "ring_inner": {"rotation": {"0": [0, 0, 0], "1.0": [0, 900, 0]}},
    }, loop="hold")
    return g

def case():
    g = Geo("case", [(40, 42, 50), (20, 22, 26), (200, 160, 60), (110, 255, 190)])
    g.bone("body", "root", (0, 0, 0)); g.bone("lid", "root", (0, 12, 8)); g.bone("latch", "root", (0, 6, -8)); g.bone("handle", "root", (0, 14, 0))
    g.cube("body", 0, (-14, 0, -8), (14, 12, 8))
    g.cube("body", 1, (-14.5, -0.5, -8.5), (-12.5, 12.5, 8.5)); g.cube("body", 1, (12.5, -0.5, -8.5), (14.5, 12.5, 8.5))
    g.cube("body", 2, (-14.4, 2, -8.6), (14.4, 3, 8.6)); g.cube("body", 2, (-14.4, 9, -8.6), (14.4, 10, 8.6))
    g.cube("body", 3, (-10, 11.5, -5), (10, 12.2, 5))          # glowing interior
    g.cube("lid", 0, (-14, 12, -8), (14, 16, 8)); g.cube("lid", 1, (-14.5, 11.5, -8.5), (14.5, 12.4, 8.5)); g.cube("lid", 2, (-3, 16, -1.2), (3, 17, 1.2))
    g.cube("latch", 3, (-9, 6, -9), (-5, 9, -8)); g.cube("latch", 3, (5, 6, -9), (9, 9, -8)); g.cube("latch", 2, (-2, 7, -9), (2, 10, -8))
    g.cube("handle", 1, (-5, 14, -1), (5, 15.5, 1)); g.cube("handle", 1, (-5, 12, -1), (-3.5, 14, 1)); g.cube("handle", 1, (3.5, 12, -1), (5, 14, 1))
    g.anim("idle", 2, {"latch": {"scale": {"0": [1, 1, 1], "1": [1.15, 1.15, 1.15], "2": [1, 1, 1]}}})
    g.anim("open", 1.0, {"lid": {"rotation": {"0": [0, 0, 0], "0.6": [-125, 0, 0], "0.8": [-112, 0, 0], "1.0": [-118, 0, 0]}}, "latch": {"position": {"0": [0, 0, 0], "0.3": [0, 0, -2]}}}, loop="hold")
    return g

def drone():
    g = Geo("drone", [(80, 86, 96), (28, 30, 36), (230, 60, 50), (110, 220, 255)])
    g.bone("body", "root", (0, 0, 0))
    for i, (sx, sz) in enumerate(((1, 1), (-1, 1), (1, -1), (-1, -1))):
        g.bone(f"arm{i}", "root", (0, 0, 0)); g.bone(f"rotor{i}", f"arm{i}", (sx * 22, 14, sz * 22))
        g.cube(f"arm{i}", 1, (sx * 4 - 2, 7, sz * 4 - 2), (sx * 22 + 2, 9, sz * 22 + 2), rot=(0, 0, 0)) if False else None
        # diagonal arm built from short segments
        for k in range(1, 6):
            g.cube(f"arm{i}", 1, (sx * k * 4 - 1.5, 7, sz * k * 4 - 1.5), (sx * k * 4 + 1.5, 9, sz * k * 4 + 1.5))
        g.cube(f"arm{i}", 0, (sx * 22 - 3, 7, sz * 22 - 3), (sx * 22 + 3, 13, sz * 22 + 3))
        g.cube(f"arm{i}", 3, (sx * 22 - 1, 5, sz * 22 - 1), (sx * 22 + 1, 7, sz * 22 + 1))
        g.cube(f"rotor{i}", 1, (sx * 22 - 15, 14, sz * 22 - 1), (sx * 22 + 15, 14.8, sz * 22 + 1))
        g.cube(f"rotor{i}", 1, (sx * 22 - 1, 14, sz * 22 - 15), (sx * 22 + 1, 14.8, sz * 22 + 15))
        g.cube(f"rotor{i}", 2, (sx * 22 - 1.5, 13, sz * 22 - 1.5), (sx * 22 + 1.5, 15.5, sz * 22 + 1.5))
    g.cube("body", 0, (-8, 3, -8), (8, 11, 8)); g.cube("body", 1, (-9, 5, -9), (9, 7, 9)); g.cube("body", 2, (-4, 11, -4), (4, 13, 4))
    g.cube("body", 3, (-3, 4, -9.5), (3, 6, -8)); g.cube("body", 3, (-1, 11, -8.5), (1, 12, -8)); g.cube("body", 1, (-5, -2, -5), (5, 3, 5)); g.cube("body", 2, (-3, -4, -3), (3, -2, 3))
    g.cube("body", 1, (-7, -2, -7), (-5.5, 4, -5.5)); g.cube("body", 1, (5.5, -2, -7), (7, 4, -5.5)); g.cube("body", 1, (-7, -2, 5.5), (-5.5, 4, 7)); g.cube("body", 1, (5.5, -2, 5.5), (7, 4, 7))
    spin = {f"rotor{i}": {"rotation": {"0": [0, 0, 0], "0.25": [0, 360 * (1 if i % 2 else -1), 0]}} for i in range(4)}
    fly = dict(spin); fly["body"] = {"rotation": {"0": [0, 0, 0], "1": [4, 0, 2], "2": [0, 0, 0], "3": [-3, 0, -2], "4": [0, 0, 0]}}
    g.anim("fly", 4, fly)
    g.anim("idle", 4, dict(spin))
    return g

def crate():
    g = Geo("crate", [(150, 110, 62), (60, 62, 68), (240, 200, 50), (255, 150, 50)])
    g.bone("body", "root", (0, 0, 0)); g.bone("lid", "root", (0, 12, 12))
    g.cube("body", 0, (-12, 0, -12), (12, 12, 12))
    for sx in (-1, 1):
        for sz in (-1, 1):
            g.cube("body", 1, (sx * 12 - 1.5 if sx > 0 else -13.5, -0.3, sz * 12 - 1.5 if sz > 0 else -13.5), (13.5 if sx > 0 else sx * 12 + 1.5, 12.5, 13.5 if sz > 0 else sz * 12 + 1.5))
    g.cube("body", 2, (-12.2, 5, -12.2), (12.2, 7, 12.2)); g.cube("body", 3, (-6, 3, -12.5), (6, 9, -12.2))
    g.cube("lid", 0, (-12.5, 12, -12.5), (12.5, 14.5, 12.5)); g.cube("lid", 1, (-3, 14.5, -3), (3, 16.5, 3)); g.cube("lid", 3, (-1.5, 16.5, -1.5), (1.5, 17.5, 1.5))
    g.anim("idle", 2, {"lid": {"position": {"0": [0, 0, 0], "1": [0, 0.15, 0], "2": [0, 0, 0]}}})
    g.anim("open", 1.2, {"lid": {"rotation": {"0": [0, 0, 0], "0.3": [-14, 0, 0], "0.9": [-100, 0, 0], "1.2": [-92, 0, 0]}}}, loop="hold")
    return g

def pod():
    g = Geo("pod", [(96, 102, 112), (40, 44, 50), (255, 140, 40), (255, 190, 90)])
    g.bone("body", "root", (0, 0, 0)); g.bone("door", "root", (-9, 0, -9)); g.bone("fins", "root", (0, 0, 0)); g.bone("thruster", "root", (0, 0, 0))
    g.cube("body", 0, (-9, 0, -9), (9, 26, 9)); g.cube("body", 0, (-7, 26, -7), (7, 32, 7)); g.cube("body", 0, (-5, 32, -5), (5, 36, 5)); g.cube("body", 1, (-2.5, 36, -2.5), (2.5, 40, 2.5))
    g.cube("body", 1, (-9.5, 7, -9.5), (9.5, 9, 9.5)); g.cube("body", 1, (-9.5, 19, -9.5), (9.5, 21, 9.5)); g.cube("body", 2, (-9.2, 13, -9.2), (9.2, 15, 9.2))
    g.cube("body", 3, (-3, 22, -9.8), (3, 25, -9.4))
    g.cube("door", 0, (-8, 1, -10), (8, 24, -9)); g.cube("door", 3, (-6, 10, -10.4), (6, 12, -10)); g.cube("door", 2, (-7, 16, -10.4), (7, 18, -10))
    for i in range(4):
        a = i * math.pi / 2 + math.pi / 4
        g.cube("fins", 1, (math.cos(a) * 11 - 1, -1, math.sin(a) * 11 - 1), (math.cos(a) * 11 + 1, 15, math.sin(a) * 11 + 1), rot=(0, 0, 0))
        g.cube("fins", 2, (math.cos(a) * 15 - 1.5, -1, math.sin(a) * 15 - 1.5), (math.cos(a) * 15 + 1.5, 3, math.sin(a) * 15 + 1.5))
    g.cube("thruster", 3, (-5, -4, -5), (5, 0, 5)); g.cube("thruster", 2, (-3, -7, -3), (3, -4, 3))
    g.anim("idle", 2, {"thruster": {"scale": {"0": [1, 1, 1], "0.5": [1.15, 1.3, 1.15], "1.0": [0.9, 0.8, 0.9], "2": [1, 1, 1]}}})
    g.anim("open", 1.4, {"door": {"rotation": {"0": [0, 0, 0], "1.0": [0, 125, 0], "1.4": [0, 118, 0]}}}, loop="hold")
    return g

def container():
    g = Geo("container", [(46, 52, 66), (22, 26, 34), (120, 230, 255), (150, 245, 255)])
    g.bone("body", "root", (0, 0, 0)); g.bone("flap_n", "root", (0, 20, -10)); g.bone("flap_s", "root", (0, 20, 10)); g.bone("flap_e", "root", (10, 20, 0)); g.bone("flap_w", "root", (-10, 20, 0))
    g.cube("body", 0, (-10, 0, -10), (10, 20, 10)); g.cube("body", 1, (-11, -1, -11), (11, 1, 11))
    for sx in (-1, 1):
        for sz in (-1, 1):
            g.cube("body", 3, (sx * 10 - 0.8 if sx > 0 else -10.8, 0, sz * 10 - 0.8 if sz > 0 else -10.8), (10.8 if sx > 0 else sx * 10 + 0.8, 20.2, 10.8 if sz > 0 else sz * 10 + 0.8))
    g.cube("body", 3, (-10.3, 9, -10.3), (10.3, 11, 10.3)); g.cube("body", 2, (-6, 3, -10.4), (6, 7, -10.2)); g.cube("body", 3, (-8, 19.5, -8), (8, 20.4, 8))
    g.cube("flap_n", 0, (-10, 20, -10), (10, 21.5, -1)); g.cube("flap_s", 0, (-10, 20, 1), (10, 21.5, 10))
    g.cube("flap_e", 0, (1, 20, -10), (10, 21.5, 10)) if False else None
    g.anim("idle", 2, {"body": {"position": {"0": [0, 0, 0], "1": [0, 0.4, 0], "2": [0, 0, 0]}}})
    g.anim("open", 1.0, {"flap_n": {"rotation": {"0": [0, 0, 0], "0.8": [-115, 0, 0]}}, "flap_s": {"rotation": {"0": [0, 0, 0], "0.8": [115, 0, 0]}}}, loop="hold")
    return g

MODELS = {"rift": rift, "case": case, "drone": drone, "crate": crate, "pod": pod, "container": container}

def build_all():
    out = {}
    for k, fn in MODELS.items():
        g = fn()
        tex, glow = g.textures()
        out[k] = (g.geo_json(), g.anim_json(), tex, glow)
    return out
