"""Tiny Bedrock-geo (GeckoLib) authoring helpers shared by gen_props.py. Units are pixels (16 = 1 block), y up."""
import json, math, os, random
from PIL import Image

R = 'src/main/resources/assets/wandcraft/'

def uvs(sw):
    """All six faces mapped to 8x8 swatch number sw of a 32x32 texture (4x4 grid)."""
    cx, cy = (sw % 4) * 8, (sw // 4) * 8
    return {f: {"uv": [cx, cy], "uv_size": [8, 8]} for f in ("north", "east", "south", "west", "up", "down")}

class Geo:
    def __init__(self, ident, tex=32, bounds=(6, 6)):
        self.ident, self.tex, self.bounds = ident, tex, bounds
        self.bones = {"root": {"name": "root", "pivot": [0, 0, 0], "cubes": []}}

    def bone(self, name, pivot=(0, 0, 0), parent="root", rot=None):
        self.bones[name] = {"name": name, "parent": parent, "pivot": list(pivot), "cubes": []}
        if rot is not None:
            self.bones[name]["rotation"] = list(rot)
        return name

    def cube(self, bone, origin, size, sw=0, rot=None, pivot=None):
        c = {"origin": [round(v, 3) for v in origin], "size": [round(v, 3) for v in size], "uv": uvs(sw)}
        if rot is not None:
            c["rotation"] = list(rot)
            c["pivot"] = list(pivot if pivot is not None else [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2])
        self.bones[bone]["cubes"].append(c)

    def box_c(self, bone, centre, size, sw=0, rot=None):
        o = [centre[0] - size[0] / 2, centre[1] - size[1] / 2, centre[2] - size[2] / 2]
        self.cube(bone, o, size, sw, rot, list(centre))

    def ring(self, bone, radius, y, width, height, segs, sw=0, tilt=(0, 0, 0)):
        """Ring of tangent boxes around the Y axis (pivot at the origin); tilt rotates the whole ring about its centre."""
        seg_len = 2 * math.pi * radius / segs * 1.08
        for k in range(segs):
            a = 2 * math.pi * k / segs
            cx, cz = math.cos(a) * radius, math.sin(a) * radius
            self.cube(bone, [cx - seg_len / 2, y - height / 2, cz - width / 2], [seg_len, height, width], sw,
                      rot=[0, -math.degrees(a) + 90, 0], pivot=[cx, y, cz])
        if any(tilt):
            for c in self.bones[bone]["cubes"][-segs:]:
                pass  # tilt is applied by rotating the owning bone in animations / bone pivot instead

    def sphere(self, bone, radius, centre=(0, 0, 0), rings=5, segs=10, thick=None, sw=0):
        thick = thick or radius * 0.35
        for i in range(rings):
            lat = -math.pi / 2 + math.pi * (i + 0.5) / rings
            r = max(radius * math.cos(lat), 0.4)
            y = centre[1] + radius * math.sin(lat)
            self.ring_at(bone, r, y, thick, radius * math.pi / rings * 1.1, segs, sw, centre)

    def ring_at(self, bone, radius, y, width, height, segs, sw, centre=(0, 0, 0)):
        seg_len = 2 * math.pi * radius / segs * 1.1
        for k in range(segs):
            a = 2 * math.pi * k / segs
            cx, cz = centre[0] + math.cos(a) * radius, centre[2] + math.sin(a) * radius
            self.cube(bone, [cx - seg_len / 2, y - height / 2, cz - width / 2], [seg_len, height, width], sw,
                      rot=[0, -math.degrees(a) + 90, 0], pivot=[cx, y, cz])

    def ellipse(self, bone, a, b, cy, width, thick, segs, sw=0, cz=0.0):
        """Ring in the XY plane (faces +/-Z): semi-axes a (x) and b (y), centred at height cy."""
        for k in range(segs):
            t = 2 * math.pi * k / segs
            cx, cyy = a * math.cos(t), cy + b * math.sin(t)
            tang = math.atan2(b * math.cos(t), -a * math.sin(t))
            ln = math.hypot(a * math.sin(t), b * math.cos(t)) * 2 * math.pi / segs * 1.12
            self.cube(bone, [cx - ln / 2, cyy - width / 2, cz - thick / 2], [ln, width, thick], sw,
                      rot=[0, 0, -math.degrees(tang)], pivot=[cx, cyy, cz])

    def arc(self, bone, radius, a0, a1, segs, width, thick, sw=0, cy=0.0, cz=0.0):
        """Arc in the XY plane from angle a0 to a1 (degrees)."""
        for k in range(segs):
            t = math.radians(a0 + (a1 - a0) * (k + 0.5) / segs)
            cx, cyy = radius * math.cos(t), cy + radius * math.sin(t)
            ln = radius * math.radians(abs(a1 - a0)) / segs * 1.15
            self.cube(bone, [cx - ln / 2, cyy - width / 2, cz - thick / 2], [ln, width, thick], sw,
                      rot=[0, 0, -(math.degrees(t) + 90)], pivot=[cx, cyy, cz])

    def radial(self, prefix, count, hinge, parent="root", yaw_offset=0.0):
        """Creates count yaw bones (static rotation about Y at the origin) each holding one child bone pivoted at hinge.
        Returns the child bone names; cubes are added to those children."""
        kids = []
        for i in range(count):
            yb = self.bone("%s_yaw%d" % (prefix, i), (0, 0, 0), parent, rot=(0, yaw_offset + 360.0 * i / count, 0))
            kids.append(self.bone("%s%d" % (prefix, i), hinge, yb))
        return kids

    def save(self, name):
        os.makedirs(R + 'geo/entity/prop', exist_ok=True)
        d = {"format_version": "1.12.0", "minecraft:geometry": [{
            "description": {"identifier": "geometry." + self.ident, "texture_width": self.tex, "texture_height": self.tex,
                            "visible_bounds_width": self.bounds[0], "visible_bounds_height": self.bounds[1], "visible_bounds_offset": [0, self.bounds[1] / 2 - 0.5, 0]},
            "bones": list(self.bones.values())}]}
        with open(R + 'geo/entity/prop/%s.geo.json' % name, 'w') as f:
            json.dump(d, f)

def kf(frames):
    return {("%.2f" % k): v for k, v in frames.items()}

def spin(period, axis=1, sign=1, steps=4):
    out = {}
    for i in range(steps + 1):
        v = [0, 0, 0]; v[axis] = sign * 360.0 * i / steps
        out[period * i / steps] = v
    return kf(out)

def wave(period, amp, axis=0, base=(0, 0, 0), steps=4):
    out = {}
    for i in range(steps + 1):
        v = list(base); v[axis] = base[axis] + amp * math.sin(2 * math.pi * i / steps)
        out[period * i / steps] = v
    return kf(out)

class Anims:
    def __init__(self, name):
        self.name, self.anims = name, {}

    def add(self, key, length, bones, loop=True):
        self.anims["animation.%s.%s" % (self.name, key)] = {"loop": True if loop is True else ("hold_on_the_last_frame" if loop == "hold" else "play_once"),
                                                             "animation_length": length, "bones": bones}

    def save(self, name=None):
        os.makedirs(R + 'animations/entity/prop', exist_ok=True)
        with open(R + 'animations/entity/prop/%s.animation.json' % (name or self.name), 'w') as f:
            json.dump({"format_version": "1.8.0", "animations": self.anims}, f)

def standard(an, root_bones=("root",), spawn_len=0.5, despawn_len=0.4):
    """spawn: grow in with a small overshoot; despawn: shrink away. Applied to the root bone."""
    r = root_bones[0]
    an.add("spawn", spawn_len, {r: {"scale": kf({0: [0.01, 0.01, 0.01], spawn_len * 0.7: [1.12, 1.12, 1.12], spawn_len: [1, 1, 1]})}}, loop=False)
    an.add("despawn", despawn_len, {r: {"scale": kf({0: [1, 1, 1], despawn_len * 0.3: [1.1, 1.1, 1.1], despawn_len: [0.01, 0.01, 0.01]})}}, loop="hold")

random.seed(11)

def texture(path, pal, alphas=(255, 255, 255, 255), noise=12):
    """32x32 atlas of 8x8 swatches. Swatch n uses palette entry n % 4 (0 main, 1 accent, 2 bright, 3 dark)."""
    im = Image.new('RGBA', (32, 32))
    px = im.load()
    for sw in range(16):
        c, al = pal[sw % 4], alphas[sw % 4]
        for y in range(8):
            for x in range(8):
                v = random.randint(-noise, noise)
                px[(sw % 4) * 8 + x, (sw // 4) * 8 + y] = tuple(max(0, min(255, k + v)) for k in c) + (al,)
    os.makedirs(os.path.dirname(R + 'textures/entity/prop/' + path), exist_ok=True)
    im.save(R + 'textures/entity/prop/' + path)
