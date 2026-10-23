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

    def cube(self, bone, origin, size, sw=0, rot=None, pivot=None, uv=None):
        c = {"origin": [round(v, 3) for v in origin], "size": [round(v, 3) for v in size], "uv": uv if uv is not None else uvs(sw)}
        if rot is not None:
            c["rotation"] = list(rot)
            c["pivot"] = list(pivot if pivot is not None else [origin[0] + size[0] / 2, origin[1] + size[1] / 2, origin[2] + size[2] / 2])
        self.bones[bone]["cubes"].append(c)

    def box_c(self, bone, centre, size, sw=0, rot=None, uv=None):
        o = [centre[0] - size[0] / 2, centre[1] - size[1] / 2, centre[2] - size[2] / 2]
        self.cube(bone, o, size, sw, rot, list(centre), uv)

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


def swirl_uvs(front_faces=("north", "south"), flat_faces=(), sw_other=3):
    """UV map for a pane whose broad faces show the animated swirl region (x 32..63, y 0..63 of a 64x64 frame)."""
    cx, cy = (sw_other % 4) * 8, (sw_other // 4) * 8
    d = {f: {"uv": [cx, cy], "uv_size": [8, 8]} for f in ("north", "east", "south", "west", "up", "down")}
    for f in tuple(front_faces) + tuple(flat_faces):
        d[f] = {"uv": [32, 0], "uv_size": [32, 64]}
    return d

def _noise_grid(n, seed):
    import numpy as np
    rng = np.random.default_rng(seed)
    return rng.random((n, n))

def texture_anim(path, pal, alphas=(255, 255, 255, 255), frames=20, frametime=2, seed=3, core=(1.0, 0.95, 1.0)):
    """Animated 64x64 frames stacked vertically. Left 32x32: palette swatches (rim runes). Right 32x64: a swirling
    vortex whose spiral arms and turbulence rotate over the loop, with a bright core and soft circular alpha edge."""
    import numpy as np
    pal = [np.array(c, dtype=float) for c in pal]
    grid = _noise_grid(64, seed)
    def vnoise(x, y):
        x0, y0 = np.floor(x).astype(int) % 64, np.floor(y).astype(int) % 64
        x1, y1 = (x0 + 1) % 64, (y0 + 1) % 64
        fx, fy = x - np.floor(x), y - np.floor(y)
        fx, fy = fx * fx * (3 - 2 * fx), fy * fy * (3 - 2 * fy)
        a = grid[y0, x0] * (1 - fx) + grid[y0, x1] * fx
        b = grid[y1, x0] * (1 - fx) + grid[y1, x1] * fx
        return a * (1 - fy) + b * fy
    W, H = 32, 64
    ys, xs = np.mgrid[0:H, 0:W]
    u = (xs / (W - 1)) * 2 - 1
    v = (ys / (H - 1)) * 2 - 1
    r = np.sqrt(u * u + v * v)
    th = np.arctan2(v, u)
    strip = Image.new('RGBA', (64, 64 * frames))
    # rune swatches are identical on every frame except for a travelling glow
    base_sw = Image.new('RGBA', (32, 32))
    rng = np.random.default_rng(seed + 1)
    for sw in range(16):
        c = pal[sw % 4]
        al = alphas[sw % 4]
        tile = np.zeros((8, 8, 4))
        for y in range(8):
            for x in range(8):
                tile[y, x, :3] = np.clip(c + rng.integers(-12, 12), 0, 255)
                tile[y, x, 3] = al
        if sw % 4 in (0, 1):   # engraved rune strokes
            for _ in range(3):
                x, y = rng.integers(0, 8), rng.integers(0, 8)
                dx, dy = rng.choice([-1, 0, 1]), rng.choice([-1, 0, 1])
                for k in range(rng.integers(3, 6)):
                    tile[(y + dy * k) % 8, (x + dx * k) % 8, :3] = pal[2]
        base_sw.paste(Image.fromarray(tile.astype('uint8'), 'RGBA'), ((sw % 4) * 8, (sw // 4) * 8))
    for f in range(frames):
        t = f / frames
        ang = 2 * math.pi * t
        # spiral arms (three) winding inward, rotating with time
        arms = 0.5 + 0.5 * np.sin(3 * th + 7.0 * np.log(r + 0.08) - ang * 2)
        # swirling turbulence: sample noise in a rotated frame whose rotation grows toward the centre
        rot = ang + 2.2 / (r + 0.35)
        nx = (np.cos(rot) * u - np.sin(rot) * v) * 9 + 20
        ny = (np.sin(rot) * u + np.cos(rot) * v) * 9 + 20
        turb = 0.6 * vnoise(nx, ny) + 0.4 * vnoise(nx * 2.1 + 7, ny * 2.1 + 3)
        val = np.clip(0.55 * arms + 0.55 * turb, 0, 1)
        val = np.clip(val + (1 - np.clip(r, 0, 1)) ** 2.4 * 0.9, 0, 1)
        # colour ramp dark -> main -> accent -> bright
        out = np.zeros((H, W, 3))
        for i in range(3):
            lo = np.clip(val * 3, 0, 1)
            mid = np.clip(val * 3 - 1, 0, 1)
            hi = np.clip(val * 3 - 2, 0, 1)
            out[:, :, i] = pal[3][i] * (1 - lo) + (pal[0][i] * (1 - mid) + pal[1][i] * mid) * lo
            out[:, :, i] = out[:, :, i] * (1 - hi) + pal[2][i] * hi
        rimglow = np.clip(1 - np.abs(r - 0.93) * 14, 0, 1)
        out = out * (1 - rimglow[..., None]) + pal[2] * rimglow[..., None]
        alpha = np.clip((1.0 - r) / 0.10, 0, 1) * (150 + 100 * val)
        alpha = np.maximum(alpha, rimglow * 255)
        rgba = np.dstack([out, alpha]).clip(0, 255).astype('uint8')
        frame = Image.new('RGBA', (64, 64))
        frame.paste(base_sw, (0, 0))
        frame.paste(Image.fromarray(rgba, 'RGBA'), (32, 0))
        strip.paste(frame, (0, 64 * f))
    full = R + 'textures/entity/prop/' + path
    os.makedirs(os.path.dirname(full), exist_ok=True)
    strip.save(full)
    with open(full + '.mcmeta', 'w') as fh:
        json.dump({"animation": {"frametime": frametime, "interpolate": True}}, fh)
