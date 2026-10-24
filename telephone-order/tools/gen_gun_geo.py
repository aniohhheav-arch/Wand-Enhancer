"""Fully modelled GeckoLib (Bedrock geo) weapons: real 3D parts, a palette atlas + glow mask per finish, and keyframe animations
(idle / fire / reload) per gun.  Orientation: barrel points toward -Z (north), +Y is up, grip near the origin.

Bones (all guns): root > gun > { body, slide, mag > { mag_base, att_*mag }, flash, glow_core, extras, att_* }
The Java renderer hides every att_* bone whose attachment is not fitted, and swaps the texture for finishes.
"""
import json, math
from PIL import Image

T = 64                                    # atlas size; 8x8 tiles of 8px, one tile per material
MATS = ["body", "dark", "accent", "poly", "wood", "glow", "lens", "metal"]
MI = {m: i for i, m in enumerate(MATS)}

FINISH_RAMPS = {
    "gold": ((60, 38, 6), (255, 224, 120)),
    "arctic": ((52, 70, 92), (245, 252, 255)),
    "crimson": ((46, 8, 12), (245, 70, 70)),
}

def sh(c, f):
    return tuple(max(0, min(255, int(v * f))) for v in c[:3])

def palette(vis):
    body = vis["body"]
    accent = vis.get("accent") or sh(body, 1.5)
    glow = vis.get("glow") or (255, 70, 50)
    return {"body": body, "dark": sh(body, 0.55), "accent": accent, "poly": (34, 35, 40), "wood": (112, 72, 40),
            "glow": glow, "lens": (110, 190, 235), "metal": (168, 172, 182)}

def remap(c, finish):
    if not finish: return c
    lo, hi = FINISH_RAMPS[finish]
    l = (0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]) / 255.0
    l = max(0.0, min(1.0, l * 1.15))
    return tuple(int(lo[i] + (hi[i] - lo[i]) * l) for i in range(3))

def textures(vis, finish=None):
    pal = palette(vis)
    img = Image.new("RGBA", (T, T), (0, 0, 0, 255))
    glow = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    px, gx = img.load(), glow.load()
    for m, i in MI.items():
        ox, oy = (i % 8) * 8, (i // 8) * 8
        base = pal[m]
        if m not in ("glow", "lens", "wood", "poly") or finish:
            base = remap(base, finish) if m in ("body", "dark", "accent", "metal") else base
        for y in range(8):
            for x in range(8):
                f = 1.0
                if m in ("body", "dark", "metal", "accent"):
                    if x in (0, 7) or y in (0, 7): f = 0.78            # bevel
                    elif x in (1,) or y in (1,): f = 1.12
                if m == "wood": f = 0.92 + 0.12 * math.sin(y * 1.6 + x * 0.3)
                if m == "poly": f = 1.0 + 0.06 * ((x + y) % 2)
                c = tuple(max(0, min(255, int(v * f))) for v in base)
                px[ox + x, oy + y] = c + (255,)
                if m in ("glow", "lens"):
                    gx[ox + x, oy + y] = (base[0], base[1], base[2], 255 if m == "glow" else 150)
    return img, glow

class Gun:
    def __init__(self, gid):
        self.id = gid
        self.bones = {}
        self.order = []
        self.anims = {}
        self.bone("root", None, (0, 0, 0))
        self.bone("gun", "root", (0, 0, 0))
        for b in ("body", "slide", "mag", "flash", "glow_core", "extra"):
            self.bone(b, "gun", (0, 0, 0))
        self.bone("mag_base", "mag", (0, 0, 0))
        self.anchor = dict(muzzle=-14.0, top=3.0, rail0=-8.0, rail1=2.0, under=-2.5, under_z=-8.0, stock=5.0, mag=(-2.0, -2.0),
                           mid=-4.0, side=2.0)

    def bone(self, name, parent, pivot):
        self.bones[name] = {"name": name, "pivot": list(pivot), "cubes": []}
        if parent: self.bones[name]["parent"] = parent
        self.order.append(name)

    def box(self, bone, mat, cx, cy, cz, sx, sy, sz, rot=None, pivot=None, inflate=0.0):
        x0, y0, z0 = cx - sx / 2, cy - sy / 2, cz - sz / 2
        x1, y1, z1 = cx + sx / 2, cy + sy / 2, cz + sz / 2
        i = MI[mat]
        u, v = (i % 8) * 8 + 1, (i // 8) * 8 + 1
        face = {"uv": [u, v], "uv_size": [6, 6]}
        c = {"origin": [-x1, y0, z0], "size": [sx, sy, sz], "inflate": inflate,
             "uv": {k: dict(face) for k in ("north", "south", "east", "west", "up", "down")}}
        if rot:
            px, py, pz = pivot if pivot else (cx, cy, cz)
            c["pivot"] = [-px, py, pz]
            c["rotation"] = [rot[0], -rot[1], -rot[2]]
        self.bones[bone]["cubes"].append(c)

    def tube(self, bone, mat, cy, cx, z0, z1, r, sides=2, rot_extra=None):
        """Cylinder-ish bar along Z: `sides`=2 gives an octagon (box + box rotated 45 deg)."""
        L = abs(z1 - z0); cz = (z0 + z1) / 2
        d = r * 2
        self.box(bone, mat, cx, cy, cz, d, d, L)
        if sides >= 2:
            self.box(bone, mat, cx, cy, cz, d, d, L, rot=(0, 0, 45))

    def disc(self, bone, mat, cx, cy, cz, r, thick, axis="x"):
        """Round plate facing along `axis` (x or y); uses octagon trick."""
        d = r * 2
        if axis == "x":
            self.box(bone, mat, cx, cy, cz, thick, d, d); self.box(bone, mat, cx, cy, cz, thick, d, d, rot=(45, 0, 0))
        else:
            self.box(bone, mat, cx, cy, cz, d, thick, d); self.box(bone, mat, cx, cy, cz, d, thick, d, rot=(0, 45, 0))

    def anim(self, name, length, bones, loop=False):
        self.anims[f"animation.{self.id}.{name}"] = {"loop": True if loop else "play_once", "animation_length": length, "bones": bones}

    def geo_json(self):
        bones = [self.bones[n] for n in self.order]
        for b in bones:
            if not b["cubes"]: b.pop("cubes")
        return {"format_version": "1.12.0", "minecraft:geometry": [{"description": {"identifier": f"geometry.{self.id}",
                "texture_width": T, "texture_height": T, "visible_bounds_width": 4, "visible_bounds_height": 3,
                "visible_bounds_offset": [0, 0.5, 0]}, "bones": bones}]}

    def anim_json(self):
        return {"format_version": "1.8.0", "animations": self.anims}

# ====================================================================================================================
# receivers / archetypes
# ====================================================================================================================
def grip(g, z=1.0, h=7.0, w=2.6, lean=12, mat="poly"):
    g.box("body", mat, 0, -2 - h / 2 + 1, z, w, h, 3.2, rot=(lean, 0, 0), pivot=(0, -1.5, z - 1))
    g.box("body", "dark", 0, -2.2, z - 3.2, 1.0, 0.8, 4.4)                                # trigger guard bottom
    g.box("body", "dark", 0, -1.1, z - 5.1, 1.0, 2.6, 0.8)                                 # guard front
    g.box("body", "metal", 0, -1.0, z - 3.0, 0.7, 1.9, 0.7, rot=(-18, 0, 0))               # trigger

def sights(g, z0, z1, top, mat="dark"):
    g.box("slide", mat, 0, top + 0.5, z0 + 0.6, 1.0, 1.0, 0.8)
    g.box("slide", mat, 0, top + 0.6, z1 - 0.6, 1.4, 1.2, 0.8)

def mag_box(g, y_top, z, w, h, d, lean=0, mat="dark", bone="mag_base", curve=0.0):
    g.box(bone, mat, 0, y_top - h / 2, z, w, h, d, rot=(lean, 0, 0), pivot=(0, y_top, z))
    g.box(bone, "metal", 0, y_top - h, z, w + 0.2, 0.5, d + 0.2, rot=(lean, 0, 0), pivot=(0, y_top, z))   # base plate

def stock_full(g, z, length=9.0, drop=2.5, mat="poly"):
    g.box("body", mat, 0, -0.4, z + length / 2, 2.4, 3.6, length, rot=(-6, 0, 0), pivot=(0, 0, z))
    g.box("body", "dark", 0, -1.4, z + length + 0.2, 2.6, 5.4, 0.9, rot=(-6, 0, 0), pivot=(0, 0, z))

def stock_skel(g, z, length=8.0):
    g.box("body", "dark", 0, 0.9, z + length / 2, 0.9, 0.9, length)
    g.box("body", "dark", 0, -1.8, z + length / 2, 0.9, 0.9, length, rot=(8, 0, 0), pivot=(0, 0, z))
    g.box("body", "dark", 0, -0.4, z + length, 1.0, 4.6, 0.9)

def pistol(g, vis, kind="pistol"):
    g.anchor.update(muzzle=-9.5, top=2.6, rail0=-6.0, rail1=-0.5, under=-1.6, under_z=-5.0, stock=3.0, mag=(-3.5, 1.4), mid=-3.0)
    g.box("body", "dark", 0, -0.2, -2.6, 2.4, 2.6, 9.6)                                        # frame
    g.box("slide", "body", 0, 1.1, -3.0, 2.6, 2.4, 10.6)                                       # slide
    for i in range(5): g.box("slide", "dark", 0, 1.2, 1.2 - i * 0.7, 2.8, 2.2, 0.28)          # serrations
    g.tube("body", "metal", 0.8, 0, -9.2, -8.0, 0.55)                                          # barrel tip
    sights(g, -8.0, 1.8, 2.3, "dark")
    grip(g, z=1.2, h=7.5, w=2.6)
    mag_box(g, -2.5, 1.0, 2.0, 7.0, 2.9, lean=12)
    if kind == "machine":
        g.box("slide", "accent", 0, 2.4, -4.6, 2.0, 0.6, 5.0)
        g.tube("body", "dark", 0.9, 0, -12.0, -9.0, 0.7)
        g.anchor["muzzle"] = -12.2

def revolver(g, vis):
    g.anchor.update(muzzle=-14.5, top=3.0, rail0=-8.0, rail1=-1.0, under=-1.0, under_z=-7.0, stock=3.0, mag=(-3.5, 1.5), mid=-4.0)
    g.box("body", "body", 0, 0.2, -1.0, 2.6, 3.8, 6.5)                                         # frame
    g.tube("body", "metal", 1.0, 0, -13.5, -3.5, 0.95)                                        # barrel
    g.box("body", "dark", 0, -0.4, -9.0, 1.6, 1.4, 6.5)                                        # underlug
    g.box("body", "dark", 0, 2.4, -2.4, 0.9, 1.0, 0.9)                                         # rear sight
    g.box("body", "accent", 0, 2.4, -13.1, 0.6, 0.9, 0.8)                                      # front sight
    g.box("slide", "dark", 0, 2.2, 3.0, 1.3, 1.6, 2.0, rot=(-35, 0, 0))                         # hammer
    # cylinder (animated: swings on reload)
    g.bone("cyl", "slide", (0, 0.4, -2.0))
    g.box("cyl", "metal", 0, 0.4, -2.6, 3.6, 3.6, 3.8)
    g.box("cyl", "metal", 0, 0.4, -2.6, 3.6, 3.6, 3.8, rot=(0, 0, 45))
    for i in range(6):
        a = math.radians(i * 60)
        g.box("cyl", "dark", math.cos(a) * 1.2, 0.4 + math.sin(a) * 1.2, -4.55, 0.7, 0.7, 0.3)
    grip(g, z=2.8, h=6.5, w=2.6, lean=14, mat="wood")
    g.box("body", "accent", 0, -2.8, 3.9, 2.8, 1.0, 1.6)
    g.bones["mag_base"]["cubes"] = []
    g.box("mag_base", "dark", 0, 0.4, -2.6, 0.2, 0.2, 0.2)       # dummy so mag bone exists in the cache

def smg_core(g, vis, supp=False, pdw=False, tall=False):
    g.anchor.update(muzzle=-17.0, top=3.4, rail0=-12.0, rail1=2.0, under=-2.2, under_z=-10.0, stock=7.0, mag=(-2.5, -1.0), mid=-5.0)
    g.box("body", "body", 0, 0.3, -3.0, 2.8, 4.0, 13.0)
    g.box("slide", "dark", 0, 2.6, -4.0, 2.3, 1.2, 10.0)
    g.box("body", "dark", 0, 0.8, -12.0, 2.2, 2.6, 4.0)                                        # handguard
    g.tube("body", "metal", 0.8, 0, -17.0, -13.5, 0.55)
    if supp: g.tube("body", "dark", 0.8, 0, -19.0, -9.0, 1.2); g.anchor["muzzle"] = -19.5
    sights(g, -10.0, 1.5, 3.2)
    grip(g, z=2.5, h=7.0)
    mag_box(g, -2.0, -2.5, 2.0, 9.0, 2.8, lean=-2)
    g.box("slide", "accent", 0, 2.4, 1.5, 0.6, 0.6, 1.2)                                       # charging handle
    stock_skel(g, 5.5, 6.5) if not pdw else None
    if pdw: g.box("body", "poly", 0, -0.5, 6.0, 2.6, 3.8, 3.0)

def rifle_core(g, vis, wood=False, longb=False, dmr=False, stripe=False, modular=False):
    ln = 7.0 if longb else 0.0
    g.anchor.update(muzzle=-21.0 - ln, top=3.6, rail0=-14.0, rail1=3.0, under=-2.0, under_z=-14.0, stock=7.5, mag=(-2.8, -2.0), mid=-6.0)
    hm = "wood" if wood else "poly"
    g.box("body", "body", 0, 0.5, -1.5, 2.8, 4.4, 11.0)                                        # receiver
    g.box("slide", "dark", 0, 3.0, -2.0, 2.2, 1.0, 9.0)                                        # top cover / bolt
    g.box("body", hm, 0, 0.3, -13.5, 3.2, 3.6, 10.0)                                           # handguard
    for i in range(4): g.box("body", "dark", 0, 2.2, -10.2 - i * 2.2, 3.4, 0.35, 0.4)          # heat vents
    g.tube("body", "metal", 0.8, 0, -21.0 - ln, -18.0, 0.6)
    g.box("body", "dark", 0, 0.8, -19.6 - ln / 2, 1.8, 1.8, 3.0 + ln)                          # gas block / barrel shroud
    g.box("body", "accent", 0, 0.8, -21.5 - ln, 2.0, 2.0, 1.4)                                 # muzzle device
    sights(g, -17.0, 1.5, 3.4)
    grip(g, z=2.8, h=7.0)
    mag_box(g, -1.8, -2.2, 2.2, 8.5, 3.6, lean=-8)
    stock_full(g, 5.0, 9.0) if not modular else stock_skel(g, 5.0, 8.5)
    if stripe: g.box("body", "accent", 0, 2.1, -2.0, 2.9, 0.5, 8.0)
    if dmr:
        g.box("body", "dark", 0, 3.8, -6.0, 1.8, 1.2, 9.0)                                      # integral rail
    if modular:
        for i in range(3): g.box("body", "accent", 1.7, 0.2, -8.5 - i * 2.4, 0.5, 1.4, 1.2)
    g.anchor["stock"] = 5.0

def sniper_core(g, vis, heavy=False):
    L = 30.0 if not heavy else 34.0
    g.anchor.update(muzzle=-L - 3.0, top=4.2, rail0=-6.0, rail1=3.0, under=-2.0, under_z=-14.0, stock=6.5, mag=(-2.5, 0.0), mid=-6.0)
    g.box("body", "body", 0, 0.8, 0.0, 3.0, 4.4, 12.0)
    g.box("slide", "dark", 0, 3.4, -1.0, 1.6, 1.0, 7.0)                                         # bolt
    g.box("slide", "metal", 3.0, 3.6, 3.0, 0.9, 0.9, 3.0, rot=(0, 0, 0))                        # bolt handle
    g.box("slide", "metal", 3.7, 3.6, 4.4, 1.6, 1.0, 1.0)                                       # knob
    g.tube("body", "metal", 1.2, 0, -L, -5.0, 0.75 if not heavy else 1.0)
    g.box("body", "poly", 0, -0.8, -9.5, 3.0, 2.4, 10.5)                                        # fore-end
    g.box("body", "accent", 0, 1.2, -L - 1.0, 1.9, 1.9, 2.0)                                    # brake
    if heavy:
        g.box("body", "dark", 0, 1.2, -L + 1.2, 2.6, 2.6, 3.0)
    # bipod
    g.box("extra", "dark", 1.8, -2.2, -8.5, 0.5, 4.5, 0.5, rot=(0, 0, -12)); g.box("extra", "dark", -1.8, -2.2, -8.5, 0.5, 4.5, 0.5, rot=(0, 0, 12))
    grip(g, z=3.5, h=6.5, mat="poly")
    mag_box(g, -2.2, -0.5, 2.0, 4.5, 3.0)
    stock_full(g, 6.0, 10.0)
    g.box("body", "poly", 0, 3.0, 9.0, 2.6, 1.6, 6.0)                                           # cheek rest
    g.anchor["stock"] = 6.0

def shotgun_core(g, vis, kind="pump"):
    g.anchor.update(muzzle=-26.0, top=3.4, rail0=-10.0, rail1=2.0, under=-2.0, under_z=-10.0, stock=6.0, mag=(-2.0, -8.0), mid=-6.0)
    g.box("body", "body", 0, 0.6, -0.5, 2.8, 4.0, 10.0)
    g.tube("body", "metal", 1.4, 0, -26.0, -4.0, 0.9)
    g.tube("body", "dark", -0.9, 0, -22.0, -5.0, 0.8)                                           # tube magazine
    g.box("body", "accent", 0, 1.4, -26.2, 1.6, 1.6, 0.8)                                       # bead
    if kind == "pump":
        g.box("slide", "wood", 0, -0.9, -14.0, 3.4, 2.8, 8.0)                                   # pump forearm
        for i in range(4): g.box("slide", "dark", 0, -2.3, -11.0 - i * 1.6, 3.5, 0.3, 0.4)
        stock_full(g, 5.0, 9.5, mat="wood")
        grip(g, z=3.0, h=6.2, mat="wood")
        g.bones["mag_base"]["cubes"] = []
        g.box("mag_base", "dark", 0, -0.9, -14.0, 0.2, 0.2, 0.2)
    else:
        g.box("body", "poly", 0, 0.0, -14.0, 3.4, 3.4, 9.0)
        g.box("slide", "dark", 0, 3.0, -2.0, 2.0, 1.0, 7.5)
        grip(g, z=3.0, h=6.8)
        mag_box(g, -2.0, -4.0, 2.4, 7.5, 4.5, lean=-6)
        stock_skel(g, 5.5, 8.0)
        g.box("body", "accent", 0, 2.4, -14.0, 3.5, 0.5, 8.0)

def lmg_core(g, vis, saw=False):
    g.anchor.update(muzzle=-26.0, top=4.8, rail0=-14.0, rail1=3.0, under=-2.5, under_z=-14.0, stock=7.0, mag=(-3.5, -1.0), mid=-6.0)
    g.box("body", "body", 0, 0.8, -1.0, 3.4, 5.0, 13.0)
    g.box("slide", "dark", 0, 3.8, -1.5, 2.6, 1.4, 10.0)
    g.box("body", "poly", 0, 0.6, -14.0, 3.4, 3.6, 9.0)                                          # shroud
    for i in range(5): g.box("body", "dark", 1.8, 0.8, -10.6 - i * 1.7, 0.4, 2.4, 0.7)         # cooling slots
    g.tube("body", "metal", 0.8, 0, -26.0, -18.0, 0.7)
    g.box("body", "dark", 0, 0.8, -19.5, 2.0, 2.0, 3.0)
    g.box("body", "accent", 0, 4.4, -12.0, 1.0, 1.6, 1.2)                                         # carry handle post front
    g.box("body", "dark", 0, 5.6, -7.0, 1.0, 0.8, 14.0)                                           # carry handle
    grip(g, z=3.0, h=7.0)
    # box magazine (belt feed)
    g.box("mag_base", "dark", 0, -4.0, -1.0, 4.8, 6.4, 6.0)
    g.box("mag_base", "accent", 0, -4.0, -1.0, 4.9, 0.8, 6.1)
    g.box("mag_base", "metal", 2.6, -1.2, -1.0, 1.0, 1.0, 5.0)
    stock_full(g, 5.0, 10.0)
    # bipod
    g.box("extra", "dark", 2.2, -2.0, -15.0, 0.5, 6.0, 0.5, rot=(0, 0, -14)); g.box("extra", "dark", -2.2, -2.0, -15.0, 0.5, 6.0, 0.5, rot=(0, 0, 14))
    if saw: g.box("body", "accent", 0, 2.0, -6.0, 3.6, 0.6, 3.0)

def launcher_core(g, vis):
    g.anchor.update(muzzle=-26.0, top=4.8, rail0=-14.0, rail1=-2.0, under=-3.0, under_z=-10.0, stock=10.0, mag=(-2.0, 0.0), mid=-6.0)
    g.tube("body", "body", 1.5, 0, -26.0, 14.0, 3.6, sides=2)
    g.tube("body", "dark", 1.5, 0, -27.0, -24.0, 4.0)                                           # muzzle collar
    g.tube("body", "dark", 1.5, 0, 12.0, 15.0, 4.2)                                             # rear flare
    g.box("body", "accent", 0, 1.5, 0, 7.6, 0.6, 3.0)                                           # warning band
    g.box("body", "dark", 0, 5.8, -4.0, 2.2, 2.0, 7.0)                                          # sight unit
    g.box("body", "lens", 0, 5.9, -7.6, 1.2, 1.0, 0.3)
    grip(g, z=1.0, h=6.5, mat="dark")
    g.box("body", "dark", 0, -3.0, -10.0, 2.0, 6.0, 2.0)                                        # foregrip
    g.box("body", "poly", 0, 2.0, 11.0, 5.0, 4.0, 2.0)                                          # shoulder pad
    g.box("body", "dark", 0, 0.0, 6.0, 4.0, 1.0, 6.0)
    g.box("mag_base", "accent", 0, 1.5, -24.6, 3.2, 3.2, 1.4)                                   # visible warhead tip (reload swap)

# ---- energy / heavy / experimental ---------------------------------------------------------------------------------
def energy_base(g, vis, L=22.0, h=4.4, w=3.2):
    g.anchor.update(muzzle=-L - 2.0, top=h / 2 + 2.2, rail0=-12.0, rail1=0.0, under=-2.0, under_z=-8.0, stock=5.5, mag=(-2.8, -0.5), mid=-5.0)
    g.box("body", "body", 0, 0.4, -3.0, w, h, 14.0)
    g.box("body", "dark", 0, 0.0, -L / 2 - 3.0, w - 0.6, h - 1.4, L - 4.0)
    g.box("glow_core", "glow", 0, 0.6, -2.0, w + 0.2, 1.2, 6.0)                                 # energy window
    grip(g, z=2.2, h=6.5, mat="poly")
    g.box("mag_base", "dark", 0, -2.2, 0.0, 2.2, 3.0, 5.4)                                      # cell housing
    g.box("mag_base", "glow", 0, -2.2, 0.0, 2.4, 1.4, 4.8)                                      # visible charge
    g.box("body", "poly", 0, 0.0, 4.5, 2.6, 3.4, 4.0)
    g.box("body", "accent", 0, 1.6, -L + 0.5, w - 0.2, 0.8, 3.0)

def coils(g, vis, n=4, z0=-10.0, step=3.0, r=2.6, thick=1.0, mat="metal"):
    g.bone("coils", "gun", (0, 0.5, z0 - step * n / 2))
    for i in range(n):
        z = z0 - i * step
        g.box("coils", mat, 0, 0.5, z, r * 2, r * 2, thick)
        g.box("coils", mat, 0, 0.5, z, r * 2, r * 2, thick, rot=(0, 0, 45))
        g.box("coils", "glow", 0, 0.5, z, r * 2 - 1.0, r * 2 - 1.0, thick + 0.2)

def emitter(g, z, r=1.6, mat="metal", glow=True):
    g.tube("body", mat, 0.4, 0, z - 1.5, z, r)
    if glow: g.box("flash", "glow", 0, 0.4, z - 0.3, r * 1.2, r * 1.2, 0.5)

def plasma(g, vis):
    energy_base(g, vis, 20.0)
    coils(g, vis, 4, -9.0, 3.0, 2.4)
    emitter(g, -22.0, 1.4)
    g.box("body", "glow", 0, 0.4, -20.6, 3.4, 3.4, 1.0, rot=(0, 0, 45))

def laser(g, vis):
    energy_base(g, vis, 26.0, 3.6, 2.8)
    g.tube("body", "metal", 0.4, 0, -29.0, -20.0, 0.7)
    g.box("body", "lens", 0, 0.4, -29.2, 1.8, 1.8, 0.5)
    g.box("flash", "glow", 0, 0.4, -29.8, 1.4, 1.4, 0.6)
    for i in range(3): g.box("body", "dark", 0, 2.4, -6.0 - i * 3.4, 1.2, 0.8, 1.8)
    g.box("extra", "glow", 0, 2.4, -9.0, 0.5, 0.5, 8.0)

def rail(g, vis):
    energy_base(g, vis, 30.0, 4.0, 3.4)
    for sx in (-1.6, 1.6):
        g.box("body", "metal", sx, 0.4, -20.0, 0.9, 1.6, 22.0)
        g.box("extra", "glow", sx * 0.82, 0.4, -20.0, 0.3, 0.5, 20.0)
    for i in range(5): g.box("body", "dark", 0, 0.4, -12.0 - i * 4.0, 3.8, 3.2, 0.8)
    g.box("flash", "glow", 0, 0.4, -33.0, 2.6, 2.6, 0.6)

def pulse(g, vis):
    energy_base(g, vis, 18.0, 4.8, 3.6)
    for i in range(5): g.box("body", "accent", 0, 0.4, -9.0 - i * 2.4, 3.9, 3.6, 0.5)
    g.tube("body", "metal", 0.4, 0, -22.0, -18.0, 1.1)
    g.box("flash", "glow", 0, 0.4, -22.5, 1.8, 1.8, 0.6)
    g.box("body", "dark", 0, 3.6, -4.0, 2.0, 1.2, 8.0)

def arc(g, vis):
    energy_base(g, vis, 18.0)
    for sx in (-1, 1):
        g.box("body", "metal", sx * 2.2, 0.4, -22.0, 0.9, 1.0, 9.0, rot=(0, -sx * 6, 0))
        g.box("extra", "glow", sx * 2.0, 0.4, -24.4, 0.4, 0.4, 3.0)
    coils(g, vis, 3, -9.0, 3.4, 2.2)
    g.box("flash", "glow", 0, 0.4, -27.0, 1.6, 1.6, 1.2)

def cryo(g, vis):
    energy_base(g, vis, 20.0)
    g.tube("extra", "lens", -1.6, 0, -18.0, -4.0, 1.9)                                           # coolant tank under barrel
    g.tube("extra", "metal", -1.6, 0, -19.0, -17.6, 2.1); g.tube("extra", "metal", -1.6, 0, -4.4, -4.0, 2.1)
    g.tube("body", "metal", 0.5, 0, -24.0, -19.0, 1.3)
    g.box("flash", "glow", 0, 0.5, -24.4, 2.0, 2.0, 0.6)

def gravity(g, vis):
    energy_base(g, vis, 16.0, 4.4, 3.6)
    g.bone("orbit", "gun", (0, 0.8, -17.0))
    for ang in range(0, 360, 60):
        a = math.radians(ang)
        g.box("orbit", "metal", math.cos(a) * 3.4, 0.8 + math.sin(a) * 3.4, -17.0, 1.2, 1.2, 1.2, rot=(0, 0, ang))
    g.box("glow_core", "glow", 0, 0.8, -17.0, 2.0, 2.0, 2.0)
    g.box("flash", "glow", 0, 0.8, -19.0, 2.8, 2.8, 0.6)
    g.box("body", "dark", 0, 0.8, -12.5, 2.4, 2.4, 5.0)

def smart(g, vis):
    energy_base(g, vis, 20.0)
    g.box("body", "dark", 0, 3.3, -4.0, 2.4, 2.4, 6.0)
    g.box("body", "lens", 0, 3.8, -7.1, 1.6, 1.4, 0.3)
    g.box("body", "lens", 2.1, 0.8, -1.0, 0.3, 2.4, 3.0)                                          # side screen
    g.tube("body", "metal", -0.6, 1.6, -22.0, -18.0, 0.6); g.tube("body", "metal", -0.6, -1.6, -22.0, -18.0, 0.6)
    g.box("flash", "glow", 0, 0.0, -22.4, 3.6, 0.8, 0.5)

def eshotgun(g, vis):
    energy_base(g, vis, 18.0, 5.0, 4.6)
    for sx in (-1.5, 1.5): g.tube("body", "metal", 0.6, sx, -22.0, -16.0, 1.1)
    g.box("extra", "glow", 0, 2.8, -10.0, 3.0, 0.6, 7.0)
    g.box("flash", "glow", 0, 0.6, -22.6, 5.0, 2.4, 0.5)

def charger(g, vis):
    energy_base(g, vis, 26.0, 5.4, 4.4)
    g.box("extra", "dark", 0, 3.6, -3.0, 4.6, 3.4, 10.0)                                          # capacitor
    for i in range(4): g.box("extra", "glow", 0, 3.8, -0.5 - i * 2.4, 4.8, 2.0, 0.8)
    g.tube("body", "metal", 0.4, 0, -30.0, -22.0, 1.6)
    g.box("flash", "glow", 0, 0.4, -30.6, 2.8, 2.8, 0.6)

def shield(g, vis):
    energy_base(g, vis, 16.0)
    g.disc("extra", "metal", 0, 0.4, -19.0, 4.6, 0.8, axis="x")
    g.disc("extra", "glow", 0, 0.4, -19.5, 3.4, 0.5, axis="x")
    g.box("flash", "glow", 0, 0.4, -20.4, 6.0, 6.0, 0.4)

def rift(g, vis):
    energy_base(g, vis, 16.0)
    g.bone("orbit", "gun", (0, 0.5, -17.5))
    for ang in range(0, 360, 45):
        a = math.radians(ang)
        g.box("orbit", "glow" if ang % 90 == 0 else "metal", math.cos(a) * 3.8, 0.5 + math.sin(a) * 3.8, -17.5, 1.1, 1.1, 1.0, rot=(0, 0, ang))
    for i, (y, z) in enumerate(((2.4, -14), (-1.6, -15), (3.0, -11))):
        g.box("extra", "glow", 0, y, z, 0.7, 2.6, 0.7, rot=(0, 0, 20 + i * 30))
    g.box("flash", "glow", 0, 0.5, -18.0, 3.0, 3.0, 0.6)

def cannon(g, vis):
    g.anchor.update(muzzle=-30.0, top=6.6, rail0=-8.0, rail1=2.0, under=-3.0, under_z=-6.0, stock=8.0, mag=(-2.0, 0.0), mid=-6.0)
    g.tube("body", "body", 1.5, 0, -30.0, 6.0, 4.2)
    g.tube("body", "dark", 1.5, 0, -32.0, -27.0, 4.8)
    for i in range(4): g.tube("body", "metal", 1.5, 0, -24.0 + i * 5, -23.2 + i * 5, 4.7)
    g.box("body", "dark", 0, 7.4, -2.0, 3.0, 2.0, 12.0)
    g.box("extra", "glow", 0, 1.5, -14.0, 4.8, 1.0, 8.0)
    g.box("flash", "glow", 0, 1.5, -33.0, 4.4, 4.4, 0.8)
    grip(g, z=1.5, h=6.5, mat="dark")
    g.box("body", "dark", 0, -3.2, -12.0, 2.2, 6.6, 2.4)
    g.box("body", "poly", 0, 1.5, 8.0, 6.0, 5.6, 3.0)
    g.box("mag_base", "accent", 0, -3.6, -4.0, 5.0, 3.4, 5.0)
    g.box("mag_base", "glow", 0, -3.6, -4.0, 5.2, 1.0, 5.2)

def mortar(g, vis):
    g.anchor.update(muzzle=-14.0, top=6.0, rail0=-8.0, rail1=2.0, under=-3.0, under_z=-6.0, stock=8.0, mag=(-2.0, 0.0), mid=-6.0)
    g.tube("body", "body", 2.0, 0, -14.0, 9.0, 4.0)
    g.tube("body", "dark", 2.0, 0, -15.0, -11.0, 4.6)
    g.box("extra", "glow", 0, 2.0, -3.0, 4.4, 1.2, 10.0)
    g.box("body", "dark", 0, 5.6, -2.0, 3.0, 2.0, 7.0)
    g.box("body", "metal", 0, -2.2, 3.0, 0.8, 4.0, 8.0)
    grip(g, z=0.8, h=6.0, mat="dark")
    g.box("body", "dark", 0, -3.0, -8.0, 2.2, 6.0, 2.2)
    g.box("body", "poly", 0, 2.0, 10.0, 5.0, 5.0, 2.0)
    g.box("flash", "glow", 0, 2.0, -15.4, 4.0, 4.0, 0.8)
    g.box("mag_base", "glow", 0, 2.0, -13.0, 3.0, 3.0, 1.2)

def prism(g, vis):
    energy_base(g, vis, 16.0)
    cx = -20.0
    for i, (dy, ang) in enumerate(((0, 0), (0, 45), (0, 90))):
        g.box("extra", "glow" if i != 1 else "lens", 0, 0.5, cx, 2.2, 7.5, 2.2, rot=(0, 0, 45 * i + 20))
    g.box("extra", "metal", 0, 0.5, -16.8, 5.4, 5.4, 0.8)
    g.box("flash", "glow", 0, 0.5, -24.0, 3.0, 3.0, 0.6)

def turret(g, vis):
    g.anchor.update(muzzle=-14.0, top=6.0, rail0=-6.0, rail1=2.0, under=-3.0, under_z=-6.0, stock=6.0, mag=(-2.0, 0.0), mid=-4.0)
    g.box("body", "body", 0, 3.0, 0.0, 7.0, 5.0, 9.0)                                            # turret head
    g.tube("body", "metal", 3.4, 0, -14.0, -4.0, 1.0)
    g.tube("body", "dark", 3.4, 2.0, -12.0, -4.0, 0.7); g.tube("body", "dark", 3.4, -2.0, -12.0, -4.0, 0.7)
    g.box("body", "lens", 0, 3.4, -4.6, 2.4, 1.6, 0.4)
    g.box("extra", "glow", 0, 5.8, 0.0, 3.0, 0.6, 3.0)
    g.box("flash", "glow", 0, 3.4, -14.4, 2.4, 2.4, 0.6)
    for a in (0, 120, 240):                                                                       # folding tripod
        r = math.radians(a + 90)
        g.box("body", "dark", math.cos(r) * 3.0, -2.5, math.sin(r) * 3.0, 0.8, 7.0, 0.8, rot=(math.sin(r) * 25, 0, -math.cos(r) * 25))
    g.box("body", "poly", 0, 0.0, 0.0, 3.0, 1.2, 3.0)
    g.box("mag_base", "dark", 0, 0.5, 5.0, 4.0, 3.0, 4.0)
    grip(g, z=2.0, h=3.5, mat="dark")

def unstable(g, vis):
    energy_base(g, vis, 18.0, 4.8, 4.0)
    g.box("extra", "glow", 1.8, 2.0, -9.0, 2.4, 2.4, 3.0, rot=(18, 25, 12))
    g.box("extra", "glow", -1.8, -0.5, -14.0, 1.8, 2.0, 2.4, rot=(-12, 20, 30))
    g.box("extra", "metal", -1.9, 2.4, -4.0, 1.0, 1.0, 7.0, rot=(0, 10, 0))
    g.box("extra", "dark", 1.6, 2.6, -16.0, 1.4, 1.4, 5.0, rot=(0, -14, 8))
    g.tube("body", "metal", 0.4, 0, -22.0, -17.0, 1.0)
    g.box("flash", "glow", 0, 0.4, -22.6, 2.4, 2.4, 0.6, rot=(0, 0, 30))

STYLE_MAP = {
    "pistol": lambda g, v: pistol(g, v), "machinepistol": lambda g, v: pistol(g, v, "machine"), "revolver": revolver,
    "smg": smg_core, "suppsmg": lambda g, v: smg_core(g, v, supp=True), "pdw": lambda g, v: smg_core(g, v, pdw=True),
    "rifle": rifle_core, "battle": lambda g, v: rifle_core(g, v, wood=True, longb=True),
    "dmr": lambda g, v: rifle_core(g, v, longb=True, dmr=True), "burst": lambda g, v: rifle_core(g, v, stripe=True),
    "modular": lambda g, v: rifle_core(g, v, modular=True),
    "sniper": sniper_core, "antimat": lambda g, v: sniper_core(g, v, heavy=True),
    "pump": lambda g, v: shotgun_core(g, v, "pump"), "autoshot": lambda g, v: shotgun_core(g, v, "auto"),
    "lmg": lmg_core, "saw": lambda g, v: lmg_core(g, v, saw=True), "launcher": launcher_core,
    "plasma": plasma, "laser": laser, "rail": rail, "pulse": pulse, "arc": arc, "cryo": cryo, "gravity": gravity,
    "smart": smart, "eshotgun": eshotgun, "charger": charger, "shield": shield, "rift": rift,
    "cannon": cannon, "mortar": mortar, "prism": prism, "turret": turret, "unstable": unstable,
}

# ====================================================================================================================
# attachments (bones att_<id>, hidden by the renderer unless fitted)
# ====================================================================================================================
def attachments(g, gun, atts):
    A = g.anchor
    for a in atts:
        if a["slot"] == "finish" or a["slot"] not in gun["slots"] or gun["fam"] not in a["fits"]: continue
        aid = a["id"]; name = "att_" + aid
        parent = "mag" if a["slot"] == "mag" else "gun"
        g.bone(name, parent, (0, 0, 0))
        top, r0, r1, mz = A["top"], A["rail0"], A["rail1"], A["muzzle"]
        zc = (r0 + r1) / 2
        if aid == "red_dot":
            g.box(name, "dark", 0, top + 0.6, zc, 1.8, 0.7, 4.0); g.box(name, "dark", 0, top + 2.1, zc, 2.0, 2.4, 3.6)
            g.box(name, "lens", 0, top + 2.1, zc - 1.9, 1.5, 1.8, 0.3); g.box(name, "glow", 0, top + 2.1, zc - 2.1, 0.5, 0.5, 0.3)
        elif aid == "holo_sight":
            g.box(name, "dark", 0, top + 0.6, zc, 2.4, 0.7, 5.0)
            for sx in (-1.3, 1.3): g.box(name, "dark", sx, top + 2.6, zc, 0.5, 3.6, 4.4)
            g.box(name, "dark", 0, top + 4.5, zc, 3.0, 0.5, 4.4)
            g.box(name, "lens", 0, top + 2.6, zc - 2.0, 2.0, 3.0, 0.2); g.box(name, "glow", 0, top + 2.8, zc - 2.15, 0.5, 0.5, 0.2)
        elif aid in ("scope_4x", "scope_8x"):
            big = aid == "scope_8x"; r = 1.5 if not big else 1.9; ln = 11.0 if not big else 15.0
            g.box(name, "dark", 0, top + 0.6, zc, 1.6, 1.0, 4.0)
            g.tube(name, "dark", top + 0.6 + r + 0.4, 0, zc - ln / 2, zc + ln / 2, r)
            g.tube(name, "metal", top + 0.6 + r + 0.4, 0, zc - ln / 2 - 1.2, zc - ln / 2, r * 1.3)
            g.tube(name, "metal", top + 0.6 + r + 0.4, 0, zc + ln / 2, zc + ln / 2 + 0.8, r * 1.15)
            g.box(name, "lens", 0, top + 0.6 + r + 0.4, zc - ln / 2 - 1.3, r * 1.6, r * 1.6, 0.2)
            g.box(name, "dark", 0, top + 0.6 + r + 0.4, zc, r * 2.6, r * 0.6, 1.2)
        elif aid == "suppressor":
            g.tube(name, "dark", 0.8, 0, mz - 9.0, mz + 0.5, 1.35)
            g.tube(name, "metal", 0.8, 0, mz - 9.2, mz - 8.7, 1.4)
            for i in range(4): g.box(name, "poly", 0, 0.8, mz - 2.0 - i * 1.8, 3.0, 3.0, 0.3, rot=(0, 0, 45))
        elif aid == "muzzle_brake":
            g.box(name, "dark", 0, 0.8, mz - 2.0, 2.6, 2.6, 3.8)
            for i in range(3): g.box(name, "metal", 0, 0.8, mz - 0.8 - i * 1.2, 2.9, 2.0, 0.5)
        elif aid == "focus_lens":
            g.tube(name, "metal", 0.4, 0, mz - 2.4, mz + 0.4, 1.6)
            g.box(name, "lens", 0, 0.4, mz - 2.6, 2.4, 2.4, 0.3); g.box(name, "glow", 0, 0.4, mz - 2.9, 1.2, 1.2, 0.3)
        elif aid == "extended_mag":
            g.box(name, "dark", 0, -9.0, A["mag"][1], 2.1, 5.5, 3.0); g.box(name, "metal", 0, -11.9, A["mag"][1], 2.3, 0.5, 3.2)
        elif aid == "drum_mag":
            mz2 = A["mag"][1]
            g.disc(name, "dark", 0, -7.0, mz2, 4.2, 4.0, axis="x"); g.disc(name, "accent", 0, -7.0, mz2, 3.0, 4.4, axis="x")
            g.box(name, "dark", 0, -3.4, mz2, 2.0, 3.0, 2.8)
        elif aid == "quickdraw_mag":
            g.box(name, "dark", 0, -6.5, A["mag"][1], 2.1, 6.0, 3.0); g.box(name, "accent", 0, -6.5, A["mag"][1] + 0.0, 2.3, 1.0, 3.2)
            g.box(name, "dark", 0, -6.0, A["mag"][1] - 2.4, 2.1, 6.0, 1.8)
        elif aid == "foregrip":
            g.box(name, "poly", 0, A["under"] - 2.8, A["under_z"], 1.6, 5.6, 1.8, rot=(8, 0, 0), pivot=(0, A["under"], A["under_z"]))
            g.box(name, "dark", 0, A["under"] - 5.4, A["under_z"] + 0.4, 2.0, 0.5, 2.2)
        elif aid == "stabilizer":
            g.box(name, "dark", 0, A["under"] - 1.0, A["under_z"], 3.0, 1.0, 5.0)
            g.box(name, "accent", 0, A["under"] - 3.2, A["under_z"], 0.7, 4.0, 3.0)
        elif aid == "heavy_stock":
            s = A["stock"]
            g.box(name, "poly", 0, -0.6, s + 5.0, 3.2, 5.2, 10.0); g.box(name, "dark", 0, -1.0, s + 10.3, 3.4, 7.0, 1.2)
        elif aid == "skeleton_stock":
            s = A["stock"]
            g.box(name, "metal", 0, 0.9, s + 4.5, 0.7, 0.7, 9.0); g.box(name, "metal", 0, -2.4, s + 4.5, 0.7, 0.7, 9.0, rot=(10, 0, 0), pivot=(0, 0, s))
            g.box(name, "dark", 0, -0.8, s + 9.2, 1.0, 5.4, 0.8)
        elif aid == "laser_sight":
            g.box(name, "dark", 0, A["under"] - 0.6, A["under_z"], 1.8, 1.6, 3.0)
            g.box(name, "lens", 0, A["under"] - 0.6, A["under_z"] - 1.6, 1.0, 1.0, 0.2); g.box(name, "glow", 0, A["under"] - 0.6, A["under_z"] - 1.8, 0.6, 0.6, 0.3)
            g.box(name, "glow", 0, A["under"] - 0.6, mz - 6.0, 0.25, 0.25, 8.0)
        elif aid == "flashlight":
            g.tube(name, "dark", A["under"] - 0.8, 0, A["under_z"] - 2.0, A["under_z"] + 2.0, 1.0)
            g.box(name, "lens", 0, A["under"] - 0.8, A["under_z"] - 2.2, 1.7, 1.7, 0.2); g.box(name, "glow", 0, A["under"] - 0.8, A["under_z"] - 2.4, 1.1, 1.1, 0.2)
        elif aid == "amp_module":
            g.box(name, "dark", 2.4, 0.8, A["mid"], 1.6, 2.4, 5.0); g.box(name, "glow", 3.3, 0.8, A["mid"], 0.2, 1.4, 3.6)
        elif aid == "overclock":
            g.box(name, "dark", -2.4, 0.8, A["mid"], 1.6, 3.2, 5.0)
            for i in range(3): g.box(name, "accent", -3.3, 0.8, A["mid"] - 1.6 + i * 1.6, 0.2, 2.6, 0.9)
            g.box(name, "glow", -2.4, 2.8, A["mid"], 1.2, 0.4, 3.4)
        elif aid == "heatsink":
            for i in range(5): g.box(name, "metal", 0, top + 1.2, A["mid"] - 2.4 + i * 1.2, 3.4, 1.6, 0.35)
            g.box(name, "dark", 0, top + 0.5, A["mid"], 3.0, 0.5, 6.0)
        elif aid == "cell_extender":
            g.box(name, "dark", 0, -6.2, A["mag"][1], 2.4, 3.6, 5.6); g.box(name, "glow", 0, -6.2, A["mag"][1], 2.6, 2.4, 4.8)

# ====================================================================================================================
# animations
# ====================================================================================================================
def animate(g, gun, vis):
    fam, beh = gun["fam"], gun["beh"]
    delay = gun["delay"]; reload_ticks = gun["reload"]
    big = fam in ("sniper", "launcher", "heavy") or gun["id"] in ("pumpjack_12", "autotwelve")
    kick = 2.6 if big else (1.2 if fam in ("pistol",) else 1.5)
    rot = 7.0 if big else 3.2
    energy = fam in ("energy", "heavy") and gun["id"] != "sentinel_kit"
    spin = [b for b in ("orbit", "coils") if b in g.bones]
    # idle: breathing + glow pulse + flash hidden
    idle = {"gun": {"position": {"0": [0, 0, 0], "2": [0, 0.18, 0], "4": [0, 0, 0]}, "rotation": {"0": [0, 0, 0], "2": [0.6, 0, 0.3], "4": [0, 0, 0]}},
            "flash": {"scale": {"0": [0, 0, 0]}}}
    if "glow_core" in g.bones: idle["glow_core"] = {"scale": {"0": [1, 1, 1], "1": [1.0, 1.0, 1.08], "2": [1, 1, 1], "3": [1.0, 1.0, 0.94], "4": [1, 1, 1]}}
    for b in spin:
        idle[b] = {"rotation": {"0": [0, 0, 0], "4": [0, 0, 360]}} if b == "orbit" else {"scale": {"0": [1, 1, 1], "2": [1, 1, 1.08], "4": [1, 1, 1]}}
    g.anim("idle", 4.0, idle, loop=True)

    # fire
    ft = max(0.12, min(0.4, delay / 20.0 * 0.9))
    fire = {"gun": {"position": {"0": [0, 0, 0], str(round(ft * 0.2, 3)): [0, 0.15, kick], str(ft): [0, 0, 0]},
                    "rotation": {"0": [0, 0, 0], str(round(ft * 0.2, 3)): [-rot, 0, 0], str(ft): [0, 0, 0]}},
            "flash": {"scale": {"0": [0.01, 0.01, 0.01], str(round(ft * 0.12, 3)): [1.3, 1.3, 1.3], str(round(ft * 0.45, 3)): [0.01, 0.01, 0.01]}},
            "slide": {"position": {"0": [0, 0, 0], str(round(ft * 0.25, 3)): [0, 0, 3.2 if fam not in ("shotgun",) else 4.5], str(ft): [0, 0, 0]}}}
    if gun["id"] == "pumpjack_12":
        fire["slide"] = {"position": {"0": [0, 0, 0], str(round(ft * 0.4, 3)): [0, 0, 0], str(round(ft * 0.7, 3)): [0, 0, 5.5], str(ft): [0, 0, 0]}}
    if "glow_core" in g.bones:
        fire["glow_core"] = {"scale": {"0": [1, 1, 1], str(round(ft * 0.2, 3)): [1.2, 1.5, 1.3], str(ft): [1, 1, 1]}}
    for b in spin:
        fire[b] = {"rotation": {"0": [0, 0, 0], str(ft): [0, 0, 120]}} if b == "orbit" else {"scale": {"0": [1, 1, 1], str(round(ft * 0.2, 3)): [1.25, 1.25, 1.25], str(ft): [1, 1, 1]}}
    if "cyl" in g.bones:
        fire["cyl"] = {"rotation": {"0": [0, 0, 0], str(ft): [60, 0, 0]}}
    g.anim("fire", ft, fire)

    # reload (duration follows the gun's real reload time)
    rl = max(0.6, reload_ticks / 20.0)
    t = lambda f: str(round(rl * f, 3))
    rel = {"gun": {"rotation": {"0": [0, 0, 0], t(0.15): [-16, 0, 14], t(0.5): [-18, 0, 16], t(0.8): [-10, 0, 8], t(1.0): [0, 0, 0]},
                   "position": {"0": [0, 0, 0], t(0.15): [0, -1.2, 1.0], t(0.8): [0, -1.0, 0.6], t(1.0): [0, 0, 0]}},
           "mag": {"position": {"0": [0, 0, 0], t(0.2): [0, 0, 0], t(0.38): [0, -11, 1.5], t(0.5): [0, -14, 2], t(0.62): [0, -9, 0.6], t(0.78): [0, 0.6, 0], t(0.84): [0, 0, 0]},
                   "rotation": {"0": [0, 0, 0], t(0.38): [8, 0, 10], t(0.5): [8, 0, 10], t(0.62): [4, 0, 4], t(0.78): [0, 0, 0]}},
           "slide": {"position": {"0": [0, 0, 0], t(0.84): [0, 0, 0], t(0.9): [0, 0, 3.6], t(1.0): [0, 0, 0]}},
           "flash": {"scale": {"0": [0, 0, 0]}}}
    if "glow_core" in g.bones:
        rel["glow_core"] = {"scale": {"0": [1, 1, 1], t(0.3): [0.3, 0.3, 0.3], t(0.7): [0.3, 0.3, 0.3], t(0.85): [1.2, 1.2, 1.2], t(1.0): [1, 1, 1]}}
    if "cyl" in g.bones:
        rel["cyl"] = {"rotation": {"0": [0, 0, 0], t(0.2): [0, 0, 80], t(0.75): [0, 0, 80], t(0.9): [0, 0, 0]},
                      "position": {"0": [0, 0, 0], t(0.2): [-2.6, -1.0, 0], t(0.75): [-2.6, -1.0, 0], t(0.9): [0, 0, 0]}}
        rel["mag"] = {"position": {"0": [0, 0, 0]}}
    if gun["id"] == "pumpjack_12":
        rel["slide"] = {"position": {"0": [0, 0, 0]}}
        rel["mag"] = {"position": {"0": [0, 0, 0]}}
        for i in range(6):                                      # shell by shell
            s0 = 0.15 + i * 0.12
            rel["gun"]["rotation"][t(s0)] = [-14, 0, 12]; rel["gun"]["rotation"][t(s0 + 0.06)] = [-18, 0, 14]
        rel["gun"]["rotation"] = dict(sorted(rel["gun"]["rotation"].items(), key=lambda kv: float(kv[0])))
    if "orbit" in g.bones:
        rel["orbit"] = {"rotation": {"0": [0, 0, 0], t(1.0): [0, 0, 540]}}
    g.anim("reload", rl, rel)

def build(gun, atts, finish_unused=None):
    g = Gun(gun["id"])
    STYLE_MAP[gun["vis"]["style"]](g, gun["vis"])
    attachments(g, gun, atts)
    animate(g, gun, gun["vis"])
    # keep glow_core/flash/extra pivots on the muzzle line for sensible scaling
    if "flash" in g.bones: g.bones["flash"]["pivot"] = [0, 0.8, g.anchor["muzzle"]]
    if "glow_core" in g.bones: g.bones["glow_core"]["pivot"] = [0, 0.5, -2.0]
    if "mag" in g.bones: g.bones["mag"]["pivot"] = [0, -2.0, g.anchor["mag"][1]]
    if "slide" in g.bones: g.bones["slide"]["pivot"] = [0, 1.0, -2.0]
    g.bones["gun"]["pivot"] = [0, -3.0, 2.0]
    return g
