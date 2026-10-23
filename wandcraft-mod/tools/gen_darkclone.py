"""Dark Clone and Ash Pile: GeckoLib geometry, animations and textures. Run from wandcraft-mod/."""
import json, math, os, random, sys
sys.path.insert(0, 'tools')
from PIL import Image
from geo_lib import R, kf

random.seed(77)
A = R + 'textures/entity/'
os.makedirs(R + 'geo/entity', exist_ok=True)
os.makedirs(R + 'animations/entity', exist_ok=True)

# ------------------------------------------------------------------------------------------------ box-UV atlas
class Atlas:
    def __init__(self, size):
        self.size, self.x, self.y, self.row = size, 0, 0, 0
        self.rects = []  # (u, v, w, h, kind)

    def place(self, w, h, d, kind="skin"):
        fw, fh = int(math.ceil(2 * (w + d))), int(math.ceil(d + h))
        if self.x + fw > self.size:
            self.x, self.y, self.row = 0, self.y + self.row, 0
        u, v = self.x, self.y
        self.x += fw
        self.row = max(self.row, fh)
        assert self.y + fh <= self.size, "atlas full"
        self.rects.append((u, v, int(math.ceil(w)), int(math.ceil(h)), int(math.ceil(d)), kind))
        return [u, v]

class Model:
    def __init__(self, ident, atlas, bounds):
        self.ident, self.atlas, self.bounds = ident, atlas, bounds
        self.bones = {"root": {"name": "root", "pivot": [0, 0, 0], "cubes": []}}

    def bone(self, name, pivot, parent="root", rot=None):
        self.bones[name] = {"name": name, "parent": parent, "pivot": list(pivot), "cubes": []}
        if rot:
            self.bones[name]["rotation"] = list(rot)

    def box(self, bone, origin, size, kind="skin", rot=None, pivot=None, inflate=0):
        c = {"origin": list(origin), "size": list(size), "uv": self.atlas.place(*size, kind=kind)}
        if inflate:
            c["inflate"] = inflate
        if rot:
            c["rotation"] = list(rot)
            c["pivot"] = list(pivot or [origin[i] + size[i] / 2 for i in range(3)])
        self.bones[bone]["cubes"].append(c)

    def save(self, name):
        d = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": self.atlas.size, "texture_height": self.atlas.size,
                            "visible_bounds_width": self.bounds[0], "visible_bounds_height": self.bounds[1], "visible_bounds_offset": [0, self.bounds[1] / 2 - 0.5, 0]},
            "bones": list(self.bones.values())}]}
        json.dump(d, open(R + 'geo/entity/%s.geo.json' % name, 'w'))

def anim_file(name, anims):
    out = {}
    for key, (length, loop, bones) in anims.items():
        out["animation.%s.%s" % (name, key)] = {"loop": loop, "animation_length": length, "bones": bones}
    json.dump({"format_version": "1.8.0", "animations": out}, open(R + 'animations/entity/%s.animation.json' % name, 'w'))

# ------------------------------------------------------------------------------------------------ dark clone model
def build_clone():
    at = Atlas(128)
    m = Model("dark_clone", at, (3.5, 3.5))
    # legs (digitigrade-ish: thigh, shin, long clawed foot)
    for side, sx in (("r", -1), ("l", 1)):
        x = sx * 2.4
        m.bone("leg_" + side, (x, 16, 0), "root")
        m.box("leg_" + side, (x - 1.4, 8, -1.6), (2.8, 8.4, 3.2))
        m.bone("shin_" + side, (x, 8.2, 0.2), "leg_" + side)
        m.box("shin_" + side, (x - 1.2, 0.8, -1.2), (2.4, 7.6, 2.4), rot=(-8, 0, 0), pivot=(x, 4, 0))
        m.bone("foot_" + side, (x, 1, -0.5), "shin_" + side)
        m.box("foot_" + side, (x - 1.4, 0, -4), (2.8, 1.6, 5.2))
        for i in (-1, 0, 1):
            m.box("foot_" + side, (x + i * 0.9 - 0.3, 0, -5.6), (0.6, 0.9, 1.8))
    m.bone("body", (0, 16, 0), "root")
    m.box("body", (-3.4, 14, -2), (6.8, 4.4, 4))
    m.bone("torso", (0, 18, 0), "body")
    m.box("torso", (-4, 18, -2.4), (8, 10.4, 4.8))
    m.box("torso", (-1.2, 20, -2.8), (2.4, 5, 0.6), kind="vein")  # glowing sternum
    for sx in (-1, 1):
        m.box("torso", (sx * 4.6 - 0.8, 27, -1), (1.6, 3.6, 1.6), rot=(0, 0, sx * -20))  # shoulder spikes
    m.bone("head", (0, 28.6, 0), "torso")
    m.box("head", (-3.3, 28.6, -3.3), (6.6, 7, 6.6))
    m.box("head", (-2.4, 27.4, -3.0), (4.8, 1.6, 4.2))   # gaunt jaw
    for sx in (-1, 1):
        m.box("head", (sx * 2.5 - 0.5, 35, -0.5), (1, 4.2, 1), rot=(-16, 0, sx * 14), pivot=(sx * 2.5, 35, 0))  # horns
        m.box("head", (sx * 1.7 - 1.1, 31.6, -3.5), (2.2, 1, 0.5), kind="eye", rot=(0, 0, sx * -9), pivot=(sx * 1.7, 32, -3.3))
    # arms: shoulder, long forearm that stretches for the reach, hand with claws
    for side, sx in (("r", -1), ("l", 1)):
        x = sx * 5.6
        m.bone("arm_" + side, (x, 27, 0), "torso")
        m.box("arm_" + side, (x - 1.4, 19, -1.4), (2.8, 8.6, 2.8))
        m.bone("fore_" + side, (x, 19.2, 0), "arm_" + side)
        m.box("fore_" + side, (x - 1.2, 9, -1.2), (2.4, 10.4, 2.4))
        m.box("fore_" + side, (x - 0.5, 14, -1.6), (1, 1, 0.5), kind="vein")
        m.bone("hand_" + side, (x, 9.2, 0), "fore_" + side)
        m.box("hand_" + side, (x - 1.5, 5.8, -1.5), (3, 3.6, 3))
        for i, fx in enumerate((-1, 0, 1)):
            m.box("hand_" + side, (x + fx * 1.0 - 0.35, 2, -0.35 + (0.4 if fx else 0)), (0.7, 3.8, 0.7), rot=(8 * (1 - abs(fx)), 0, fx * -6),
                  pivot=(x + fx, 5.8, 0))
    # ragged cloak strips that trail behind, each in two hinged sections
    strips = [(-3.2, 3.6, 17, 0), (-1.1, 3, 21, 1), (1.1, 3, 20, 2), (3.2, 3.6, 16, 3)]
    for i, (cx, w, h, k) in enumerate(strips):
        n = "cloak_" + "abcd"[i]
        m.bone(n, (cx, 27, 2.4), "torso")
        m.box(n, (cx - w / 2, 27 - h * 0.55, 2.4), (w, h * 0.55, 0.7))
        m.bone(n + "_b", (cx, 27 - h * 0.55, 2.6), n)
        m.box(n + "_b", (cx - w / 2 + 0.2, 27 - h, 2.4), (w - 0.4, h * 0.45, 0.6))
    # black tendrils writhing from the back, three chained segments each
    for i, (tx, ty) in enumerate(((-2.5, 25), (0, 23), (2.5, 25))):
        n = "tend%d" % (i + 1)
        m.bone(n, (tx, ty, 2.6), "torso")
        m.box(n, (tx - 0.7, ty - 5, 2.4), (1.4, 5.5, 1.4), rot=(30, 0, 0), pivot=(tx, ty, 2.6))
        m.bone(n + "b", (tx, ty - 4.6, 4), n)
        m.box(n + "b", (tx - 0.55, ty - 9.2, 3.6), (1.1, 5, 1.1), rot=(50, 0, 0), pivot=(tx, ty - 4.6, 4))
        m.bone(n + "c", (tx, ty - 8.8, 6.4), n + "b")
        m.box(n + "c", (tx - 0.4, ty - 12.8, 5.6), (0.8, 4.4, 0.8), rot=(70, 0, 0), pivot=(tx, ty - 8.8, 6.4))
    m.save("dark_clone")
    return at

def paint_clone(at):
    img = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    glow = Image.new("RGBA", (128, 128), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    for (u, v, w, h, d, kind) in at.rects:
        fw, fh = 2 * (w + d), d + h
        for y in range(fh):
            for x in range(fw):
                X, Y = u + x, v + y
                if X >= 128 or Y >= 128:
                    continue
                n = random.randint(-5, 5)
                base = (13 + n, 9 + n, 22 + n)
                if (x // 3 + y // 3) % 7 == 0:
                    base = (24 + n, 15 + n, 38 + n)
                px[X, Y] = base + (246,)
                if kind == "eye":
                    px[X, Y] = (255, 240, 255, 255)
                    gx[X, Y] = (255, 240, 255, 255)
                elif kind == "vein":
                    px[X, Y] = (170, 90, 255, 255)
                    gx[X, Y] = (170, 90, 255, 255)
        # purple veins: short random walks across each cube's faces
        for _ in range(max(1, (fw * fh) // 60)):
            x, y = random.randrange(fw), random.randrange(fh)
            for _s in range(random.randint(5, 14)):
                X, Y = u + x, v + y
                if 0 <= X < 128 and 0 <= Y < 128 and kind == "skin":
                    px[X, Y] = (120, 52, 214, 255)
                    gx[X, Y] = (120, 52, 214, 255)
                x = max(0, min(fw - 1, x + random.choice((-1, 0, 1))))
                y = max(0, min(fh - 1, y + random.choice((0, 1))))
    img.save(A + 'dark_clone.png')
    glow.save(A + 'dark_clone_glowing.png')

def build_clone_anims():
    def tend(spd, amp, phase=0.0):
        out = {}
        for i in range(1, 4):
            n = "tend%d" % i
            for suffix, mul in (("", 1.0), ("b", 1.5), ("c", 2.0)):
                length = spd
                out[n + suffix] = {"rotation": kf({
                    0: [amp * mul * math.sin(phase + i), 0, amp * 0.6 * mul * math.cos(phase + i)],
                    length / 4: [amp * mul * math.sin(phase + i + 1.6), 0, -amp * 0.6 * mul],
                    length / 2: [-amp * mul * math.sin(phase + i), 0, amp * 0.6 * mul * math.cos(phase + i + 2)],
                    length * 3 / 4: [amp * mul * math.cos(phase + i), 0, amp * 0.4 * mul],
                    length: [amp * mul * math.sin(phase + i), 0, amp * 0.6 * mul * math.cos(phase + i)]})}
        return out

    def cloak(spd, base, amp):
        out = {}
        for i, n in enumerate(("cloak_a", "cloak_b", "cloak_c", "cloak_d")):
            out[n] = {"rotation": kf({0: [base, 0, 2 * (i - 1.5)], spd / 2: [base + amp, 0, -2 * (i - 1.5)], spd: [base, 0, 2 * (i - 1.5)]})}
            out[n + "_b"] = {"rotation": kf({0: [base * 0.6, 0, 0], spd / 2: [base * 0.6 + amp * 1.3, 0, 3], spd: [base * 0.6, 0, 0]})}
        return out

    def merge(*ds):
        o = {}
        for d in ds:
            for k, v in d.items():
                o.setdefault(k, {}).update(v)
        return o

    zero = [0, 0, 0]
    A_ = {}
    # --- 1. materialize: rises out of the pool, limbs unfurl, head lifts last
    A_["materialize"] = (2.0, "hold_on_the_last_frame", merge({
        "root": {"scale": kf({0: [1.5, 0.02, 1.5], 0.7: [0.7, 1.35, 0.7], 1.3: [1.08, 0.95, 1.08], 2.0: [1, 1, 1]}),
                 "position": kf({0: [0, -14, 0], 0.7: [0, -2, 0], 1.3: [0, 0.5, 0], 2.0: zero})},
        "body": {"rotation": kf({0: [-25, 0, 0], 1.0: [-8, 0, 0], 2.0: zero})},
        "head": {"rotation": kf({0: [-50, 0, 0], 1.0: [-30, 0, 8], 1.6: [10, 0, -3], 2.0: zero})},
        "arm_r": {"rotation": kf({0: [0, 0, 30], 1.0: [0, 0, 18], 2.0: [0, 0, 5]})},
        "arm_l": {"rotation": kf({0: [0, 0, -30], 1.0: [0, 0, -18], 2.0: [0, 0, -5]})},
        "hand_r": {"scale": kf({0: [0.01, 0.01, 0.01], 1.2: [0.01, 0.01, 0.01], 1.7: [1.3, 1.3, 1.3], 2.0: [1, 1, 1]})},
        "hand_l": {"scale": kf({0: [0.01, 0.01, 0.01], 1.2: [0.01, 0.01, 0.01], 1.7: [1.3, 1.3, 1.3], 2.0: [1, 1, 1]})},
    }, tend(2.0, 18), cloak(2.0, 6, 6)))
    # --- 2. observe: still, gaunt, head twitches toward the prey
    A_["observe"] = (4.0, True, merge({
        "body": {"position": kf({0: zero, 2: [0, 0.35, 0], 4: zero}), "rotation": kf({0: [2, 0, 0], 2: [4, 0, 0], 4: [2, 0, 0]})},
        "head": {"rotation": kf({0: zero, 1.0: [0, 0, 0], 1.2: [-3, 18, 7], 2.3: [-3, 18, 7], 2.5: [3, -14, -6], 3.6: [3, -14, -6], 4.0: zero})},
        "hand_r": {"rotation": kf({0: [0, 0, 0], 0.5: [6, 0, 8], 1.0: [0, 0, 0], 2.0: [0, 0, 0], 2.3: [8, 0, -6], 2.6: [0, 0, 0], 4: zero})},
        "hand_l": {"rotation": kf({0: [0, 0, 0], 1.5: [8, 0, -6], 1.8: [0, 0, 0], 4: zero})},
        "arm_r": {"rotation": kf({0: [0, 0, 4], 2: [0, 0, 8], 4: [0, 0, 4]})},
        "arm_l": {"rotation": kf({0: [0, 0, -4], 2: [0, 0, -8], 4: [0, 0, -4]})},
    }, tend(3.0, 8), cloak(2.0, 4, 4)))
    # --- 3. hunt: low predatory lope
    A_["hunt"] = (0.8, True, merge({
        "root": {"position": kf({0: zero, 0.2: [0, 0.6, 0], 0.4: zero, 0.6: [0, 0.6, 0], 0.8: zero})},
        "body": {"rotation": kf({0: [22, 0, 3], 0.4: [22, 0, -3], 0.8: [22, 0, 3]})},
        "torso": {"rotation": kf({0: [10, 6, 0], 0.4: [10, -6, 0], 0.8: [10, 6, 0]})},
        "head": {"rotation": kf({0: [-30, -3, 0], 0.4: [-30, 3, 0], 0.8: [-30, -3, 0]})},
        "leg_r": {"rotation": kf({0: [-38, 0, 0], 0.4: [38, 0, 0], 0.8: [-38, 0, 0]})},
        "leg_l": {"rotation": kf({0: [38, 0, 0], 0.4: [-38, 0, 0], 0.8: [38, 0, 0]})},
        "shin_r": {"rotation": kf({0: [10, 0, 0], 0.2: [40, 0, 0], 0.4: [4, 0, 0], 0.6: [10, 0, 0], 0.8: [10, 0, 0]})},
        "shin_l": {"rotation": kf({0: [4, 0, 0], 0.4: [10, 0, 0], 0.6: [40, 0, 0], 0.8: [4, 0, 0]})},
        "arm_r": {"rotation": kf({0: [32, 0, 6], 0.4: [-30, 0, 6], 0.8: [32, 0, 6]})},
        "arm_l": {"rotation": kf({0: [-30, 0, -6], 0.4: [32, 0, -6], 0.8: [-30, 0, -6]})},
        "fore_r": {"rotation": kf({0: [-25, 0, 0], 0.4: [-5, 0, 0], 0.8: [-25, 0, 0]})},
        "fore_l": {"rotation": kf({0: [-5, 0, 0], 0.4: [-25, 0, 0], 0.8: [-5, 0, 0]})},
    }, tend(0.8, 22), cloak(0.8, 32, 12)))
    # --- 4. approach: slow stalking creep, claws rising
    A_["approach"] = (1.8, True, merge({
        "root": {"position": kf({0: zero, 0.45: [0, 0.25, 0], 0.9: zero, 1.35: [0, 0.25, 0], 1.8: zero})},
        "body": {"rotation": kf({0: [12, 0, 2], 0.9: [12, 0, -2], 1.8: [12, 0, 2]})},
        "head": {"rotation": kf({0: [-16, 0, 0], 0.9: [-12, 0, 2], 1.8: [-16, 0, 0]})},
        "leg_r": {"rotation": kf({0: [-22, 0, 0], 0.9: [22, 0, 0], 1.8: [-22, 0, 0]})},
        "leg_l": {"rotation": kf({0: [22, 0, 0], 0.9: [-22, 0, 0], 1.8: [22, 0, 0]})},
        "shin_r": {"rotation": kf({0: [8, 0, 0], 0.45: [30, 0, 0], 0.9: [6, 0, 0], 1.8: [8, 0, 0]})},
        "shin_l": {"rotation": kf({0: [6, 0, 0], 0.9: [8, 0, 0], 1.35: [30, 0, 0], 1.8: [6, 0, 0]})},
        "arm_r": {"rotation": kf({0: [-58, 0, 12], 0.9: [-66, 0, 16], 1.8: [-58, 0, 12]})},
        "arm_l": {"rotation": kf({0: [-50, 0, -12], 0.9: [-44, 0, -16], 1.8: [-50, 0, -12]})},
        "hand_r": {"rotation": kf({0: [0, 0, 0], 0.3: [14, 0, 10], 0.6: [0, 0, -8], 0.9: [12, 0, 6], 1.8: [0, 0, 0]})},
        "hand_l": {"rotation": kf({0: [10, 0, -10], 0.5: [0, 0, 8], 1.0: [12, 0, -6], 1.8: [10, 0, -10]})},
    }, tend(1.8, 14), cloak(1.8, 18, 8)))
    # --- 5. reach: right arm lengthens toward the prey
    A_["reach"] = (1.1, "hold_on_the_last_frame", merge({
        "body": {"rotation": kf({0: [12, 0, 0], 0.5: [26, 0, 0], 1.1: [30, 0, 0]})},
        "head": {"rotation": kf({0: [-16, 0, 0], 0.5: [-34, 0, 0], 1.1: [-38, 0, 0]})},
        "arm_r": {"rotation": kf({0: [-58, 0, 12], 0.45: [-85, 0, 2], 1.1: [-90, 0, 0]})},
        "fore_r": {"rotation": kf({0: [0, 0, 0], 1.1: [-6, 0, 0]}), "scale": kf({0: [1, 1, 1], 0.4: [1, 1, 1], 0.8: [0.8, 2.2, 0.8], 1.1: [0.7, 2.8, 0.7]})},
        "hand_r": {"rotation": kf({0: zero, 0.6: [-4, 0, 12], 0.8: [6, 0, -14], 1.1: [-4, 0, 14]}), "scale": kf({0: [1, 1, 1], 0.8: [1.1, 0.5, 1.1], 1.1: [1.2, 0.4, 1.2]})},
        "arm_l": {"rotation": kf({0: [-50, 0, -12], 1.1: [34, 0, -26]})},
        "leg_r": {"rotation": kf({0: [-22, 0, 0], 0.5: [24, 0, 0], 1.1: [24, 0, 0]})},
        "leg_l": {"rotation": kf({0: [22, 0, 0], 0.5: [-26, 0, 0], 1.1: [-26, 0, 0]})},
    }, tend(0.7, 30), cloak(0.7, 26, 16)))
    # --- 6. merge: the arm stays locked on the prey while the body is consumed bottom-up and drawn along the arm into it
    def gone(t0, t1):
        return {"scale": kf({0: [1, 1, 1], t0: [1, 1, 1], t1: [0.01, 0.01, 0.01]})}
    mg = {
        "body": {"rotation": kf({0: [30, 0, 0], 1.2: [38, 0, 0]})},
        "head": {"rotation": kf({0: [-38, 0, 0], 1.2: [-46, 0, 0]}), "scale": kf({0: [1, 1, 1], 0.55: [1, 1, 1], 0.85: [0.01, 0.01, 0.01]})},
        "arm_r": {"rotation": kf({0: [-90, 0, 0], 1.2: [-90, 0, 0]})},
        "fore_r": {"rotation": kf({0: [-6, 0, 0]}), "scale": kf({0: [0.7, 2.8, 0.7], 0.5: [0.95, 2.8, 0.95], 1.0: [1.1, 2.8, 1.1], 1.2: [0.3, 2.8, 0.3]})},
        "hand_r": {"scale": kf({0: [1.2, 0.4, 1.2], 0.9: [1.5, 0.5, 1.5], 1.2: [0.01, 0.01, 0.01]})},
        "arm_l": {"rotation": kf({0: [34, 0, -26], 1.2: [60, 0, -40]}), "scale": kf({0: [1, 1, 1], 0.5: [1, 1, 1], 0.8: [0.01, 0.01, 0.01]})},
        "leg_r": {"rotation": kf({0: [24, 0, 0], 1.2: [24, 0, 0]}), "scale": kf({0: [1, 1, 1], 0.1: [1, 1, 1], 0.45: [0.01, 0.01, 0.01]})},
        "leg_l": {"rotation": kf({0: [-26, 0, 0], 1.2: [-26, 0, 0]}), "scale": kf({0: [1, 1, 1], 0.15: [1, 1, 1], 0.5: [0.01, 0.01, 0.01]})},
        "root": {"position": kf({0: zero, 0.8: zero, 1.2: [0, 0.5, -26]}), "scale": kf({0: [1, 1, 1], 0.8: [1, 1, 1], 1.2: [0.02, 0.02, 0.02]})},
    }
    for n in ("cloak_a", "cloak_b", "cloak_c", "cloak_d"):
        mg[n + "_b"] = gone(0.05, 0.35)
        mg[n] = gone(0.2, 0.55)
    for i in (1, 2, 3):
        mg["tend%dc" % i] = gone(0.0, 0.25); mg["tend%db" % i] = gone(0.15, 0.4); mg["tend%d" % i] = gone(0.3, 0.6)
    A_["merge"] = (1.2, "hold_on_the_last_frame", mg)
    # --- 7. dissolve: extremities (claws, head tip, feet, tendrils) crumble first, then limbs, then the core
    def vanish(t0, t1):
        return {"scale": kf({0: [1, 1, 1], t0: [1, 1, 1], t1: [0.01, 0.01, 0.01]})}
    dis = {
        "hand_r": vanish(0.1, 0.5), "hand_l": vanish(0.2, 0.6),
        "tend1c": vanish(0.0, 0.3), "tend2c": vanish(0.05, 0.35), "tend3c": vanish(0.1, 0.4),
        "tend1b": vanish(0.25, 0.6), "tend2b": vanish(0.3, 0.65), "tend3b": vanish(0.35, 0.7),
        "tend1": vanish(0.5, 0.85), "tend2": vanish(0.55, 0.9), "tend3": vanish(0.6, 0.95),
        "cloak_a_b": vanish(0.2, 0.6), "cloak_b_b": vanish(0.25, 0.65), "cloak_c_b": vanish(0.3, 0.7), "cloak_d_b": vanish(0.2, 0.6),
        "cloak_a": vanish(0.6, 1.0), "cloak_b": vanish(0.65, 1.05), "cloak_c": vanish(0.7, 1.1), "cloak_d": vanish(0.6, 1.0),
        "fore_r": vanish(0.5, 0.95), "fore_l": vanish(0.6, 1.05),
        "arm_r": vanish(0.8, 1.2), "arm_l": vanish(0.9, 1.3),
        "foot_r": vanish(0.7, 1.1), "foot_l": vanish(0.75, 1.15),
        "shin_r": vanish(1.0, 1.4), "shin_l": vanish(1.05, 1.45),
        "head": {"scale": kf({0: [1, 1, 1], 0.8: [1, 1, 1], 1.4: [0.01, 0.01, 0.01]}), "rotation": kf({0: zero, 0.8: zero, 1.4: [-40, 20, 0]})},
        "leg_r": vanish(1.3, 1.7), "leg_l": vanish(1.35, 1.75),
        "torso": vanish(1.4, 1.9), "body": {"scale": kf({0: [1, 1, 1], 1.5: [1, 1, 1], 2.0: [0.01, 0.01, 0.01]})},
    }
    dis["body"]["rotation"] = kf({0: [4, 0, 0], 1.0: [-6, 0, 4], 2.0: [-14, 0, -4]})
    A_["dissolve"] = (2.0, "hold_on_the_last_frame", dis)
    # --- 8. implode: violent inward collapse
    A_["implode"] = (0.55, "hold_on_the_last_frame", {
        "root": {"scale": kf({0: [1, 1, 1], 0.25: [1.25, 0.6, 1.25], 0.55: [0.01, 0.01, 0.01]}),
                 "rotation": kf({0: zero, 0.55: [0, 360, 0]})}})
    # --- 9. phase: one-shot flicker used for phase steps
    A_["phase"] = (0.4, "play_once", {
        "root": {"scale": kf({0: [1, 1, 1], 0.08: [1.6, 0.4, 0.2], 0.16: [0.3, 1.5, 1.6], 0.24: [1.3, 0.7, 0.7], 0.4: [1, 1, 1]}),
                 "position": kf({0: zero, 0.1: [3, 0, 0], 0.2: [-2, 0, 0], 0.4: zero})}})
    anim_file("dark_clone", A_)

# ------------------------------------------------------------------------------------------------ ash pile
def build_ash():
    at = Atlas(64)  # not used for UV: swatches instead
    sw = lambda i: {f: {"uv": [(i % 4) * 8, (i // 4) * 8], "uv_size": [8, 8]} for f in ("north", "east", "south", "west", "up", "down")}
    bones = {"root": {"name": "root", "pivot": [0, 0, 0], "cubes": []}}
    def bone(n, parent="root", pivot=(0, 0, 0)):
        bones[n] = {"name": n, "parent": parent, "pivot": list(pivot), "cubes": []}
    def cube(b, centre, size, swatch, yaw=0.0):
        o = [centre[i] - size[i] / 2 for i in range(3)]
        c = {"origin": [round(v, 3) for v in o], "size": [round(v, 3) for v in size], "uv": sw(swatch)}
        if yaw:
            c["rotation"] = [0, yaw, 0]
            c["pivot"] = list(centre)
        bones[b]["cubes"].append(c)

    bone("mound")
    # a broad flat skirt of ash, then a few stacked, offset layers forming a low heap
    cube("mound", (0, 0.5, 0), (21, 1.0, 18), 0, 12)
    cube("mound", (-3, 0.7, 4), (13, 1.4, 11), 1, -20)
    cube("mound", (4, 0.8, -3), (11, 1.6, 12), 0, 33)
    cube("mound", (0, 1.7, 0), (10, 1.8, 9), 3, 8)
    cube("mound", (-1, 3.0, 1), (6.5, 1.8, 6), 1, -25)
    cube("mound", (1.2, 4.1, -0.5), (3.6, 1.6, 3.4), 0, 40)
    cube("mound", (0.4, 5.0, 0), (1.8, 1.0, 1.8), 2, 20)
    bone("flecks")
    rnd = random.Random(5)
    for i in range(46):
        a, r = rnd.uniform(0, math.tau), rnd.uniform(5, 15)
        s = rnd.uniform(0.7, 2.4)
        cube("flecks", (math.cos(a) * r, s * 0.25, math.sin(a) * r), (s, s * 0.5, s * rnd.uniform(0.7, 1.4)), rnd.choice((0, 1, 2, 3)), rnd.uniform(0, 90))
    for i in range(4):
        a = rnd.uniform(0, math.tau)
        cube("flecks", (math.cos(a) * 4.5, 2.2, math.sin(a) * 4.5), (1.4, 2.4, 1.2), 3, rnd.uniform(0, 90))
    bone("embers_a")
    bone("embers_b")
    for i in range(14):
        a, r = rnd.uniform(0, math.tau), rnd.uniform(0.5, 9)
        h = 1.2 + max(0.0, 4.6 - r * 0.6)
        cube("embers_a" if i % 2 else "embers_b", (math.cos(a) * r, h, math.sin(a) * r), (0.8, 0.6, 0.8), 4 if i % 3 else 5, rnd.uniform(0, 90))
    d = {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": "geometry.ash_pile", "texture_width": 32, "texture_height": 32,
                        "visible_bounds_width": 3, "visible_bounds_height": 1.5, "visible_bounds_offset": [0, 0.3, 0]},
        "bones": list(bones.values())}]}
    json.dump(d, open(R + 'geo/entity/ash_pile.geo.json', 'w'))

    # swatches 0..3 ash, 4 ember orange, 5 ember violet (the last two form the glow mask)
    cols = [(30, 28, 33), (52, 50, 58), (82, 79, 90), (14, 13, 16), (255, 140, 54), (190, 100, 255)]
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0)); glow = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    for s, c in enumerate(cols):
        for y in range(8):
            for x in range(8):
                v = random.randint(-9, 9) if s < 4 else random.randint(-20, 0)
                col = tuple(max(0, min(255, k + v)) for k in c) + (255,)
                px[(s % 4) * 8 + x, (s // 4) * 8 + y] = col
                if s >= 4:
                    gx[(s % 4) * 8 + x, (s // 4) * 8 + y] = col
    img.save(A + 'ash_pile.png'); glow.save(A + 'ash_pile_glowing.png')

    def sc(a, b, c=None):
        return [a, b, a if c is None else c]
    anims = {
        # the heap rains down and settles with a soft puff of motion
        "settle": (2.2, "play_once", {
            "mound": {"scale": kf({0: [0.35, 0.05, 0.35], 0.5: [1.05, 1.5, 1.05], 1.2: [1.0, 0.85, 1.0], 2.2: [1, 1, 1]})},
            "flecks": {"scale": kf({0: [0.01, 0.01, 0.01], 0.3: [1.3, 1.3, 1.3], 2.0: [1, 1, 1]}),
                       "position": kf({0: [0, 14, 0], 0.5: [0, 7, 0], 1.4: [0, 0.5, 0], 2.2: [0, 0, 0]})},
            "embers_a": {"scale": kf({0: [0.01, 0.01, 0.01], 1.4: [0.01, 0.01, 0.01], 2.2: [1, 1, 1]})},
            "embers_b": {"scale": kf({0: [0.01, 0.01, 0.01], 1.6: [0.01, 0.01, 0.01], 2.2: [1, 1, 1]})}}),
        "idle": (5.0, True, {
            "embers_a": {"scale": kf({0: [1, 1, 1], 1.2: [0.3, 0.3, 0.3], 2.5: [1.15, 1.15, 1.15], 3.8: [0.4, 0.4, 0.4], 5: [1, 1, 1]})},
            "embers_b": {"scale": kf({0: [0.5, 0.5, 0.5], 1.5: [1.1, 1.1, 1.1], 3.0: [0.3, 0.3, 0.3], 4.2: [1.2, 1.2, 1.2], 5: [0.5, 0.5, 0.5]})},
            "flecks": {"position": kf({0: [0, 0, 0], 2.5: [0, 0.12, 0], 5: [0, 0, 0]})}}),
        "dissipate": (1.4, "hold_on_the_last_frame", {
            "flecks": {"scale": kf({0: [1, 1, 1], 0.6: [0.01, 0.01, 0.01]}), "position": kf({0: [0, 0, 0], 0.6: [0, 3, 0]})},
            "embers_a": {"scale": kf({0: [1, 1, 1], 0.3: [0.01, 0.01, 0.01]})},
            "embers_b": {"scale": kf({0: [1, 1, 1], 0.3: [0.01, 0.01, 0.01]})},
            "mound": {"scale": kf({0: [1, 1, 1], 1.4: [1.1, 0.02, 1.1]})}}),
    }
    anim_file("ash_pile", anims)

if __name__ == "__main__":
    at = build_clone()
    paint_clone(at)
    build_clone_anims()
    build_ash()
    print("dark clone ok", len(at.rects), "boxes")
