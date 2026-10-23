"""Generates every GeckoLib prop model, animation and texture used by spells. Run from wandcraft-mod/."""
import math, sys
sys.path.insert(0, 'tools')
from geo_lib import *

def finish(g, an, name, pal, alphas=(255, 255, 255, 255)):
    g.save(name); an.save(name); texture(name + '.png', pal, alphas)

def idle_pulse(an, bones_spin, length=6.0, extra=None):
    bones = dict(extra or {})
    for b, (axis, sign, period) in bones_spin.items():
        bones.setdefault(b, {})["rotation"] = spin(period, axis, sign)
    an.add("idle", length, bones)

# ------------------------------------------------------------------------------------------------ vine guardian
def vine_guardian():
    g = Geo("vine_guardian", bounds=(5, 5))
    an = Anims("vine_guardian")
    chain = [("seg1", (0, 0, 0), (0, 6, 0), (6, 12, 6)), ("seg2", (0, 12, 0), (0, 18, 0), (5, 12, 5)),
             ("seg3", (0, 24, 0), (0, 30, 0), (4.5, 12, 4.5)), ("seg4", (0, 36, 0), (0, 42, 0), (4, 12, 4))]
    parent = "root"
    for name, piv, cen, size in chain:
        g.bone(name, piv, parent)
        g.box_c(name, cen, size, 0)
        # ridge thorns on the front of each segment
        g.box_c(name, (0, cen[1] + 2, -size[2] / 2 - 0.6), (1.4, 2.6, 1.4), 2, rot=(25, 0, 0))
        g.box_c(name, (size[0] / 2 + 0.6, cen[1] - 2, 0), (1.4, 2.6, 1.4), 3, rot=(0, 0, -25))
        parent = name
    g.bone("jaw_top", (0, 48, 0), "seg4")
    g.box_c("jaw_top", (0, 51, -4), (9, 3, 11), 0)
    for i, x in enumerate((-3.2, -1.1, 1.1, 3.2)):
        g.box_c("jaw_top", (x, 48.8, -8.4), (1.2, 3, 1.2), 2)
    g.box_c("jaw_top", (0, 54, -2), (3, 2, 5), 1)
    g.bone("jaw_bottom", (0, 48, 0), "seg4")
    g.box_c("jaw_bottom", (0, 46.6, -4), (8, 2.4, 10), 1)
    for x in (-2.6, 0, 2.6):
        g.box_c("jaw_bottom", (x, 48.4, -8.0), (1.1, 2.6, 1.1), 2)
    for side, sg in (("l", 1), ("r", -1)):
        g.bone("leaf_" + side, (sg * 3, 22, 0), "seg2")
        g.box_c("leaf_" + side, (sg * 9, 22, 0), (12, 0.8, 6), 1, rot=(0, 0, -sg * 20))
        g.box_c("leaf_" + side, (sg * 16, 21, 0), (6, 0.8, 4), 0, rot=(0, 0, -sg * 38))
    standard(an)
    an.add("idle", 3.0, {
        "seg1": {"rotation": wave(3, 5, 2)}, "seg2": {"rotation": wave(3, -6, 2)}, "seg3": {"rotation": wave(3, 8, 0)},
        "seg4": {"rotation": wave(3, 10, 2)},
        "jaw_top": {"rotation": wave(3, -8, 0, (-4, 0, 0))}, "jaw_bottom": {"rotation": wave(3, 6, 0, (3, 0, 0))},
        "leaf_l": {"rotation": wave(3, 6, 2)}, "leaf_r": {"rotation": wave(3, -6, 2)}})
    an.add("action", 0.8, {
        "seg1": {"rotation": kf({0: [0, 0, 0], 0.2: [-12, 0, 0], 0.35: [28, 0, 0], 0.8: [0, 0, 0]})},
        "seg2": {"rotation": kf({0: [0, 0, 0], 0.2: [-14, 0, 0], 0.35: [34, 0, 0], 0.8: [0, 0, 0]})},
        "seg3": {"rotation": kf({0: [0, 0, 0], 0.2: [-12, 0, 0], 0.35: [30, 0, 0], 0.8: [0, 0, 0]})},
        "seg4": {"rotation": kf({0: [0, 0, 0], 0.2: [-10, 0, 0], 0.35: [24, 0, 0], 0.8: [0, 0, 0]})},
        "jaw_top": {"rotation": kf({0: [0, 0, 0], 0.2: [-55, 0, 0], 0.35: [8, 0, 0], 0.5: [0, 0, 0]})},
        "jaw_bottom": {"rotation": kf({0: [0, 0, 0], 0.2: [45, 0, 0], 0.35: [-6, 0, 0], 0.5: [0, 0, 0]})}}, loop=False)
    finish(g, an, "vine_guardian", [(46, 120, 50), (92, 168, 62), (214, 236, 130), (30, 78, 36)])

# ------------------------------------------------------------------------------------------------ rune sigil
def sigil(name, pal, alphas=(230, 230, 255, 200)):
    g = Geo(name, bounds=(5, 3))
    an = Anims(name)
    g.bone("ring_outer", (0, 0, 0)); g.ring("ring_outer", 14, 0.6, 1.2, 0.8, 16, 0)
    g.bone("ring_mid", (0, 0, 0)); g.ring("ring_mid", 10.5, 0.7, 1.0, 0.8, 12, 1)
    g.bone("ring_inner", (0, 0, 0)); g.ring("ring_inner", 6, 0.8, 0.9, 0.8, 8, 0)
    g.bone("glyphs", (0, 0, 0))
    for k in range(6):
        a = 2 * math.pi * k / 6
        g.box_c("glyphs", (math.cos(a) * 12.2, 2.2, math.sin(a) * 12.2), (2.4, 0.7, 2.4), 2, rot=(0, 45, 0))
    g.bone("star", (0, 0, 0))
    pts = [(math.cos(-math.pi / 2 + 2 * math.pi * k / 5) * 9.5, math.sin(-math.pi / 2 + 2 * math.pi * k / 5) * 9.5) for k in range(5)]
    for k in range(5):
        (x0, z0), (x1, z1) = pts[k], pts[(k + 2) % 5]
        ln = math.hypot(x1 - x0, z1 - z0)
        ang = math.degrees(math.atan2(z1 - z0, x1 - x0))
        g.box_c("star", ((x0 + x1) / 2, 0.5, (z0 + z1) / 2), (ln, 0.5, 0.7), 2, rot=(0, -ang, 0))
    g.bone("core", (0, 7, 0))
    g.box_c("core", (0, 7, 0), (3.2, 3.2, 3.2), 2, rot=(45, 0, 45))
    g.box_c("core", (0, 7, 0), (2.2, 2.2, 2.2), 1, rot=(45, 45, 0))
    standard(an)
    an.add("idle", 12.0, {
        "ring_outer": {"rotation": spin(20, 1, 1, 8)}, "ring_mid": {"rotation": spin(14, 1, -1, 8)}, "ring_inner": {"rotation": spin(9, 1, 1, 8)},
        "glyphs": {"rotation": spin(12, 1, -1, 8), "position": wave(12, 0.8, 1, (0, 0, 0), 8)},
        "star": {"rotation": spin(30, 1, 1, 8)},
        "core": {"rotation": spin(6, 1, 1, 8), "position": wave(6, 1.2, 1, (0, 0, 0), 4)}})
    an.add("action", 0.5, {
        "core": {"scale": kf({0: [1, 1, 1], 0.12: [2.4, 2.4, 2.4], 0.5: [1, 1, 1]})},
        "ring_outer": {"scale": kf({0: [1, 1, 1], 0.15: [1.18, 1, 1.18], 0.5: [1, 1, 1]})},
        "ring_mid": {"scale": kf({0: [1, 1, 1], 0.15: [1.25, 1, 1.25], 0.5: [1, 1, 1]})}}, loop=False)
    finish(g, an, name, pal, alphas)

# ------------------------------------------------------------------------------------------------ orbs
def orb(name, pal, core_r=6, disc=True, alphas=(255, 255, 255, 255)):
    g = Geo(name, bounds=(6, 6))
    an = Anims(name)
    g.bone("core"); g.sphere("core", core_r, rings=5, segs=10, thick=core_r * 0.5, sw=3)
    g.bone("shell"); g.sphere("shell", core_r * 1.35, rings=4, segs=9, thick=1.0, sw=0)
    g.bone("ring_a"); g.ring("ring_a", 15, 0, 1.4, 0.8, 18, 1)
    g.bone("ring_b", rot=(62, 0, 0)); g.ring("ring_b", 12, 0, 1.2, 0.8, 16, 2)
    g.bone("ring_c", rot=(-48, 0, 40)); g.ring("ring_c", 10, 0, 1.0, 0.7, 14, 1)
    if disc:
        g.bone("disc", rot=(14, 0, 0))
        for r, sw in ((18, 1), (21, 0), (24, 1), (27, 2)):
            g.ring("disc", r, 0, 2.2, 0.4, 24, sw)
    g.bone("motes")
    for k in range(8):
        a = 2 * math.pi * k / 8
        g.box_c("motes", (math.cos(a) * 20, math.sin(k * 1.7) * 6, math.sin(a) * 20), (1.6, 1.6, 1.6), 2, rot=(45, 45, 0))
    standard(an)
    an.add("idle", 8.0, {
        "core": {"rotation": spin(16, 1, 1, 8)},
        "shell": {"rotation": spin(10, 1, -1, 8), "scale": wave(8, 0.05, 0, (1, 1, 1), 4)},
        "ring_a": {"rotation": spin(6, 1, 1, 8)}, "ring_b": {"rotation": spin(5, 1, -1, 8)}, "ring_c": {"rotation": spin(7, 1, 1, 8)},
        "disc": {"rotation": spin(4, 1, 1, 8)}, "motes": {"rotation": spin(3, 1, -1, 8)}})
    an.add("action", 0.5, {"core": {"scale": kf({0: [1, 1, 1], 0.2: [1.6, 1.6, 1.6], 0.5: [1, 1, 1]})},
                            "shell": {"scale": kf({0: [1, 1, 1], 0.2: [0.7, 0.7, 0.7], 0.5: [1, 1, 1]})}}, loop=False)
    finish(g, an, name, pal, alphas)

# ------------------------------------------------------------------------------------------------ bloom
def bloom():
    g = Geo("bloom", bounds=(3, 3))
    an = Anims("bloom")
    g.box_c("root", (0, 5, 0), (1.4, 10, 1.4), 3)
    for sg in (1, -1):
        g.box_c("root", (sg * 3.2, 4, 0), (6, 0.7, 3), 3, rot=(0, 0, -sg * 22))
    outer = g.radial("petal_o", 8, (0, 10, 0))
    for b in outer:
        g.cube(b, [-2.4, 10, -9.5], [4.8, 0.8, 8], 0, rot=(-20, 0, 0), pivot=[0, 10, -2])
    inner = g.radial("petal_i", 8, (0, 10.4, 0), yaw_offset=22.5)
    for b in inner:
        g.cube(b, [-1.7, 10.4, -6.5], [3.4, 0.7, 5.5], 1, rot=(-48, 0, 0), pivot=[0, 10.4, -2])
    g.bone("pistil", (0, 11.5, 0)); g.box_c("pistil", (0, 11.8, 0), (2.6, 2.6, 2.6), 2, rot=(45, 0, 45)); g.box_c("pistil", (0, 12.4, 0), (1.4, 3.4, 1.4), 2)
    g.bone("pollen", (0, 15, 0))
    for k in range(6):
        a = 2 * math.pi * k / 6
        g.box_c("pollen", (math.cos(a) * 7, 15 + math.sin(k) * 2, math.sin(a) * 7), (1, 1, 1), 2, rot=(45, 45, 0))
    standard(an)
    bones = {}
    for i, b in enumerate(outer):
        bones[b] = {"rotation": kf({0: [-2, 0, 0], 2: [4, 0, 0], 4: [-2, 0, 0]})}
    for i, b in enumerate(inner):
        bones[b] = {"rotation": kf({0: [3, 0, 0], 2: [-3, 0, 0], 4: [3, 0, 0]})}
    bones["pistil"] = {"scale": kf({0: [1, 1, 1], 1: [1.25, 1.25, 1.25], 2: [1, 1, 1], 3: [1.25, 1.25, 1.25], 4: [1, 1, 1]}), "rotation": spin(8, 1, 1, 8)}
    bones["pollen"] = {"rotation": spin(5, 1, 1, 8), "position": wave(4, 1.5, 1, (0, 0, 0), 4)}
    an.add("idle", 4.0, bones)
    closed = {b: {"rotation": kf({0: [-70, 0, 0], 0.7: [4, 0, 0], 0.9: [0, 0, 0]})} for b in outer}
    closed.update({b: {"rotation": kf({0: [-80, 0, 0], 0.9: [-3, 0, 0], 1.1: [0, 0, 0]})} for b in inner})
    closed["root"] = {"scale": kf({0: [0.2, 0.05, 0.2], 0.6: [1, 1.1, 1], 0.9: [1, 1, 1]})}
    an.anims["animation.bloom.spawn"] = {"loop": "play_once", "animation_length": 1.1, "bones": closed}
    close = {b: {"rotation": kf({0: [0, 0, 0], 0.5: [-75, 0, 0]})} for b in outer + inner}
    close["root"] = {"scale": kf({0: [1, 1, 1], 0.6: [0.2, 0.1, 0.2]})}
    an.anims["animation.bloom.despawn"] = {"loop": "hold_on_the_last_frame", "animation_length": 0.6, "bones": close}
    finish(g, an, "bloom", [(255, 118, 186), (255, 196, 232), (255, 238, 150), (52, 132, 58)])

# ------------------------------------------------------------------------------------------------ mirror & shield & dome
def mirror():
    g = Geo("mirror", bounds=(3, 4))
    an = Anims("mirror")
    g.bone("pane", (0, 16, 0)); g.box_c("pane", (0, 16, 0), (20, 28, 0.8), 0)
    g.bone("frame", (0, 16, 0))
    for x in (-11, 11): g.box_c("frame", (x, 16, 0), (2.6, 32, 2), 1)
    for y in (1.2, 30.8): g.box_c("frame", (0, y, 0), (24, 2.6, 2), 1)
    g.box_c("frame", (0, 33.5, 0), (4, 4, 2), 2, rot=(0, 0, 45))
    for k, (x, y) in enumerate(((-13, 30), (13, 4), (-12, 6))):
        b = g.bone("shard%d" % k, (x, y, 0))
        g.box_c(b, (x, y, 0), (2.4, 3.6, 0.6), 2, rot=(0, 0, 30 * (k + 1)))
    g.bone("glint", (0, 16, 0.6)); g.box_c("glint", (-4, 20, 0.6), (2, 14, 0.3), 2, rot=(0, 0, -25)); g.box_c("glint", (2, 14, 0.6), (1.2, 8, 0.3), 2, rot=(0, 0, -25))
    standard(an)
    an.anims["animation.mirror.spawn"] = {"loop": "play_once", "animation_length": 0.5, "bones": {"root": {"scale": kf({0: [0.02, 0.6, 1], 0.35: [1.1, 1.05, 1], 0.5: [1, 1, 1]})}}}
    an.add("idle", 4.0, {"root": {"position": wave(4, 0.8, 1, (0, 0, 0), 4)}, "pane": {"scale": wave(4, 0.02, 0, (1, 1, 1), 4)},
                         "glint": {"position": kf({0: [-6, 0, 0], 2: [6, 0, 0], 4: [-6, 0, 0]})},
                         "shard0": {"rotation": spin(6, 2, 1, 4)}, "shard1": {"rotation": spin(5, 2, -1, 4)}, "shard2": {"rotation": spin(7, 2, 1, 4)}})
    an.add("action", 0.4, {"pane": {"scale": kf({0: [1, 1, 1], 0.1: [1.18, 1.1, 1], 0.4: [1, 1, 1]})}}, loop=False)
    an.anims["animation.mirror.despawn"] = {"loop": "hold_on_the_last_frame", "animation_length": 0.5, "bones": {
        "pane": {"scale": kf({0: [1, 1, 1], 0.12: [1.1, 1.1, 1], 0.3: [0.01, 0.01, 0.01]})},
        "frame": {"scale": kf({0: [1, 1, 1], 0.4: [0.01, 0.01, 0.01]})},
        "shard0": {"position": kf({0: [0, 0, 0], 0.5: [-24, 22, 8]}), "scale": kf({0: [1, 1, 1], 0.5: [0.01, 0.01, 0.01]})},
        "shard1": {"position": kf({0: [0, 0, 0], 0.5: [26, -14, -6]}), "scale": kf({0: [1, 1, 1], 0.5: [0.01, 0.01, 0.01]})},
        "shard2": {"position": kf({0: [0, 0, 0], 0.5: [-18, -20, 6]}), "scale": kf({0: [1, 1, 1], 0.5: [0.01, 0.01, 0.01]})}}}
    finish(g, an, "mirror", [(176, 214, 255), (236, 196, 90), (255, 255, 255), (90, 120, 170)], (120, 255, 255, 160))

def shield_panel(name, pal, alphas):
    g = Geo(name, bounds=(3, 4))
    an = Anims(name)
    g.bone("pane", (0, 16, 0)); g.box_c("pane", (0, 16, 0), (22, 28, 0.8), 0)
    g.bone("wing_l", (-11, 16, 0)); g.box_c("wing_l", (-17, 16, -1.6), (9, 26, 0.8), 0, rot=(0, 28, 0))
    g.bone("wing_r", (11, 16, 0)); g.box_c("wing_r", (17, 16, -1.6), (9, 26, 0.8), 0, rot=(0, -28, 0))
    g.bone("cap_t", (0, 30, 0)); g.box_c("cap_t", (0, 33, -1.8), (16, 6, 0.8), 0, rot=(-28, 0, 0))
    g.bone("cap_b", (0, 2, 0)); g.box_c("cap_b", (0, -1, -1.8), (16, 6, 0.8), 0, rot=(28, 0, 0))
    g.bone("rim", (0, 16, 0))
    for x in (-12, 12): g.box_c("rim", (x, 16, 0.4), (1.2, 29, 1.4), 1)
    for y in (1.6, 30.4): g.box_c("rim", (0, y, 0.4), (23, 1.2, 1.4), 1)
    g.bone("runes", (0, 16, 0))
    for k in range(6):
        a = 2 * math.pi * k / 6
        g.box_c("runes", (math.cos(a) * 7, 16 + math.sin(a) * 9, 1.2), (2.4, 2.4, 0.5), 2, rot=(0, 0, 45))
    standard(an)
    an.add("idle", 4.0, {"pane": {"scale": wave(4, 0.025, 2, (1, 1, 1), 4)}, "runes": {"rotation": spin(8, 2, 1, 8)},
                         "rim": {"scale": wave(2, 0.02, 0, (1, 1, 1), 4)}})
    an.add("action", 0.35, {"pane": {"scale": kf({0: [1, 1, 1], 0.08: [1.15, 1.1, 1.4], 0.35: [1, 1, 1]})},
                            "runes": {"scale": kf({0: [1, 1, 1], 0.1: [1.5, 1.5, 1.5], 0.35: [1, 1, 1]})}}, loop=False)
    an.anims["animation.%s.despawn" % name] = {"loop": "hold_on_the_last_frame", "animation_length": 0.45, "bones": {
        "wing_l": {"position": kf({0: [0, 0, 0], 0.45: [-20, 6, -10]}), "scale": kf({0: [1, 1, 1], 0.45: [0.01, 0.01, 0.01]})},
        "wing_r": {"position": kf({0: [0, 0, 0], 0.45: [20, 6, -10]}), "scale": kf({0: [1, 1, 1], 0.45: [0.01, 0.01, 0.01]})},
        "cap_t": {"position": kf({0: [0, 0, 0], 0.45: [0, 18, -8]}), "scale": kf({0: [1, 1, 1], 0.45: [0.01, 0.01, 0.01]})},
        "cap_b": {"position": kf({0: [0, 0, 0], 0.45: [0, -18, -8]}), "scale": kf({0: [1, 1, 1], 0.45: [0.01, 0.01, 0.01]})},
        "pane": {"scale": kf({0: [1, 1, 1], 0.2: [0.01, 0.01, 0.01]})}, "rim": {"scale": kf({0: [1, 1, 1], 0.3: [0.01, 0.01, 0.01]})},
        "runes": {"scale": kf({0: [1, 1, 1], 0.25: [0.01, 0.01, 0.01]})}}}
    finish(g, an, name, pal, alphas)

def dome(name, pal, alphas):
    g = Geo(name, bounds=(4, 3))
    an = Anims(name)
    lats = (0, 25, 45, 62, 77)
    for i, lat in enumerate(lats):
        b = g.bone("lat%d" % i, (0, 0, 0))
        r = 16 * math.cos(math.radians(lat)); y = 16 * math.sin(math.radians(lat))
        g.ring(b, r, y, 0.8, 0.8, max(8, int(r * 1.0)), 1 if i % 2 == 0 else 0)
    g.bone("arches", (0, 0, 0))
    for m in range(8):
        a = math.radians(m * 45)
        for k in range(8):
            t = math.radians(5 + 80 * k / 7)
            r, y = 16 * math.cos(t), 16 * math.sin(t)
            cx, cz = math.cos(a) * r, math.sin(a) * r
            g.cube("arches", [cx - 0.5, y - 1.1, cz - 0.5], [1, 2.2, 1], 0,
                   rot=[0, -math.degrees(a), 0], pivot=[cx, y, cz])
    g.bone("cap", (0, 16, 0)); g.box_c("cap", (0, 16.3, 0), (3, 1.2, 3), 2, rot=(0, 45, 0))
    g.bone("runes", (0, 0, 0))
    for k in range(6):
        a = 2 * math.pi * k / 6 + 0.3
        g.box_c("runes", (math.cos(a) * 14.5, 6 + 3 * math.sin(k), math.sin(a) * 14.5), (1.8, 1.8, 1.8), 2, rot=(45, 45, 0))
    standard(an, spawn_len=0.7)
    an.add("idle", 12.0, {"lat0": {"rotation": spin(24, 1, 1, 8)}, "lat1": {"rotation": spin(20, 1, -1, 8)}, "lat2": {"rotation": spin(16, 1, 1, 8)},
                          "lat3": {"rotation": spin(12, 1, -1, 8)}, "lat4": {"rotation": spin(9, 1, 1, 8)}, "arches": {"rotation": spin(40, 1, 1, 8)},
                          "runes": {"rotation": spin(10, 1, -1, 8), "position": wave(6, 1.2, 1, (0, 0, 0), 4)},
                          "cap": {"rotation": spin(6, 1, 1, 4), "scale": wave(3, 0.2, 1, (1, 1, 1), 4)}})
    an.add("action", 0.4, {"arches": {"scale": kf({0: [1, 1, 1], 0.1: [1.05, 1.05, 1.05], 0.4: [1, 1, 1]})},
                           "lat2": {"scale": kf({0: [1, 1, 1], 0.1: [1.1, 1, 1.1], 0.4: [1, 1, 1]})}}, loop=False)
    finish(g, an, name, pal, alphas)

# ------------------------------------------------------------------------------------------------ fire pillar, tornado, roots
def fire_pillar():
    g = Geo("fire_pillar", bounds=(2, 4))
    an = Anims("fire_pillar")
    for bname, rot, sws in (("col_a", 0, (0, 1, 2)), ("col_b", 45, (1, 0, 2))):
        g.bone(bname)
        y = 0
        for w, h, sw in ((9, 12, sws[0]), (7.5, 12, sws[1]), (6, 12, sws[0]), (4.2, 12, sws[1]), (2.4, 10, sws[2])):
            g.box_c(bname, (0, y + h / 2, 0), (w, h, w), sw, rot=(0, rot, 0))
            y += h
    g.bone("embers")
    for k in range(8):
        a = 2 * math.pi * k / 8
        g.box_c("embers", (math.cos(a) * 8, 6 + k * 6, math.sin(a) * 8), (1.4, 1.4, 1.4), 2, rot=(45, 45, 0))
    an.add("spawn", 0.35, {"root": {"scale": kf({0: [1.3, 0.05, 1.3], 0.18: [1.1, 1.45, 1.1], 0.35: [1, 1.25, 1]})}}, loop=False)
    an.add("idle", 0.6, {"root": {"scale": kf({0: [1, 1.25, 1], 0.15: [0.92, 1.35, 0.92], 0.3: [1.05, 1.2, 1.05], 0.45: [0.94, 1.32, 0.94], 0.6: [1, 1.25, 1]})},
                         "col_a": {"rotation": spin(1.2, 1, 1, 4)}, "col_b": {"rotation": spin(1.0, 1, -1, 4)}, "embers": {"rotation": spin(0.8, 1, 1, 4)}})
    an.add("despawn", 0.35, {"root": {"scale": kf({0: [1, 1.25, 1], 0.35: [0.2, 0.02, 0.2]})}}, loop="hold")
    finish(g, an, "fire_pillar", [(255, 120, 24), (255, 190, 60), (255, 245, 190), (170, 40, 10)], (120, 105, 150, 90))

def tornado(name, pal, alphas):
    g = Geo(name, bounds=(6, 6))
    an = Anims(name)
    layers = 10
    for i in range(layers):
        b = g.bone("layer%d" % i, (0, 0, 0))
        r = 4 + i * 2.0 + (i * i) * 0.12
        g.ring(b, r, i * 7 + 3.5, 1.5, 6.5, max(8, int(r * 1.2)), 0 if i % 2 == 0 else 1)
    g.bone("debris")
    for k in range(12):
        a = 2 * math.pi * k / 12
        r = 7 + (k % 4) * 5
        g.box_c("debris", (math.cos(a) * r, 6 + k * 5.5, math.sin(a) * r), (2, 2, 2), 3 if k % 3 else 2, rot=(30, 45, 10))
    standard(an, spawn_len=0.6)
    bones = {"layer%d" % i: {"rotation": spin(0.9 + i * 0.18, 1, 1 if i % 3 else -1, 4)} for i in range(layers)}
    bones["debris"] = {"rotation": spin(1.6, 1, 1, 4), "position": wave(1.6, 3, 1, (0, 0, 0), 4)}
    an.add("idle", 3.0, bones)
    finish(g, an, name, pal, alphas)

def roots():
    g = Geo("roots", bounds=(4, 3))
    an = Anims("roots")
    heads = g.radial("rt", 7, (0, 0, 0), yaw_offset=10)
    bones = {}
    for i, h in enumerate(heads):
        # three chained, progressively curling segments rising from the ground ring
        base = 7 + (i % 3) * 2.5
        g.bone(h + "_a", (0, 0, -base), h); g.box_c(h + "_a", (0, 5, -base), (3.2, 11, 3.2), 0, rot=(0, 0, 0))
        g.bone(h + "_b", (0, 10, -base), h + "_a", rot=(-18, 0, 0)); g.box_c(h + "_b", (0, 15, -base), (2.6, 10, 2.6), 3)
        g.bone(h + "_c", (0, 20, -base), h + "_b", rot=(-30, 0, 0)); g.box_c(h + "_c", (0, 24, -base), (1.8, 8, 1.8), 0)
        g.box_c(h + "_c", (0, 28.4, -base - 0.6), (1.2, 2.2, 1.2), 2, rot=(-25, 0, 0))
        ph = i * 0.7
        bones[h + "_a"] = {"rotation": kf({0: [0, 0, 4 * math.sin(ph)], 1.5: [3, 0, -4 * math.sin(ph)], 3: [0, 0, 4 * math.sin(ph)]})}
        bones[h + "_b"] = {"rotation": kf({0: [-3, 0, 0], 1.5: [8, 0, 5], 3: [-3, 0, 0]})}
        bones[h + "_c"] = {"rotation": kf({0: [-4, 0, 0], 1.5: [10, 0, -6], 3: [-4, 0, 0]})}
    an.add("spawn", 0.5, {"root": {"scale": kf({0: [1, 0.02, 1], 0.3: [1, 1.2, 1], 0.5: [1, 1, 1]})}}, loop=False)
    an.add("idle", 3.0, bones)
    an.add("despawn", 0.45, {"root": {"scale": kf({0: [1, 1, 1], 0.45: [1, 0.02, 1]})}}, loop="hold")
    finish(g, an, "roots", [(86, 58, 34), (118, 84, 50), (160, 220, 120), (52, 34, 22)])

# ------------------------------------------------------------------------------------------------ light
def light_orb():
    g = Geo("light_orb", bounds=(4, 4))
    an = Anims("light_orb")
    g.bone("core"); g.box_c("core", (0, 0, 0), (4, 4, 4), 2, rot=(45, 0, 45)); g.box_c("core", (0, 0, 0), (3, 3, 3), 1, rot=(45, 45, 0)); g.box_c("core", (0, 0, 0), (2, 2, 2), 2)
    g.bone("halo_a", rot=(90, 0, 0)); g.ring("halo_a", 8, 0, 0.9, 0.6, 14, 1)
    g.bone("halo_b", rot=(0, 0, 60)); g.ring("halo_b", 10, 0, 0.8, 0.5, 16, 0)
    rays = g.radial("ray", 8, (0, 0, 0))
    for k, r in enumerate(rays):
        g.box_c(r, (0, 0, -11), (0.9, 0.9, 14), 2 if k % 2 == 0 else 1)
    diag = g.radial("dray", 4, (0, 0, 0), yaw_offset=45)
    for k, r in enumerate(diag):
        g.box_c(r, (0, 5.5, -5.5), (0.7, 0.7, 11), 1, rot=(45, 0, 0))
        g.box_c(r, (0, -5.5, -5.5), (0.7, 0.7, 11), 1, rot=(-45, 0, 0))
    standard(an)
    bones = {"core": {"rotation": spin(6, 1, 1, 4), "scale": wave(2, 0.12, 0, (1, 1, 1), 4)}, "halo_a": {"rotation": spin(5, 1, 1, 4)},
             "halo_b": {"rotation": spin(7, 1, -1, 4)}, "root": {"position": wave(3, 1.5, 1, (0, 0, 0), 4)}}
    for k, r in enumerate(rays + diag):
        bones[r] = {"scale": kf({0: [1, 1, 0.7 + 0.3 * (k % 2)], 0.75: [1, 1, 1.15], 1.5: [1, 1, 0.7 + 0.3 * (k % 2)]})}
    an.add("idle", 1.5, bones)
    an.add("action", 0.4, {"core": {"scale": kf({0: [1, 1, 1], 0.1: [2.4, 2.4, 2.4], 0.4: [1, 1, 1]})}}, loop=False)
    finish(g, an, "light_orb", [(255, 214, 90), (255, 240, 170), (255, 255, 240), (200, 140, 30)], (240, 220, 255, 190))

def sanctuary():
    g = Geo("sanctuary", bounds=(10, 4))
    an = Anims("sanctuary")
    g.bone("ring_a"); g.ring("ring_a", 62, 0.6, 2.2, 0.8, 36, 0)
    g.bone("ring_b"); g.ring("ring_b", 54, 0.7, 1.4, 0.8, 32, 1)
    g.bone("ring_c"); g.ring("ring_c", 30, 0.8, 1.2, 0.8, 22, 0)
    pillars = g.radial("pil", 8, (0, 0, 0))
    for k, p in enumerate(pillars):
        g.bone(p + "_top", (0, 0, -58), p)
        g.box_c(p + "_top", (0, 6, -58), (4, 12, 4), 1); g.box_c(p + "_top", (0, 13, -58), (5.6, 2, 5.6), 0)
        g.box_c(p + "_top", (0, 40, -58), (2.2, 56, 2.2), 2)   # soft shaft of light
        g.box_c(p + "_top", (0, 70, -58), (1.2, 16, 1.2), 2)
    standard(an, spawn_len=0.8)
    bones = {"ring_a": {"rotation": spin(40, 1, 1, 8)}, "ring_b": {"rotation": spin(30, 1, -1, 8)}, "ring_c": {"rotation": spin(18, 1, 1, 8)}}
    for k, p in enumerate(pillars):
        bones[p + "_top"] = {"scale": kf({0: [1, 0.8 + 0.1 * (k % 3), 1], 1.5: [1, 1.1, 1], 3: [1, 0.8 + 0.1 * (k % 3), 1]})}
    an.add("idle", 3.0, bones)
    an.anims["animation.sanctuary.spawn"] = {"loop": "play_once", "animation_length": 0.9, "bones": dict(
        {"root": {"scale": kf({0: [0.2, 0.2, 0.2], 0.6: [1.05, 1.05, 1.05], 0.9: [1, 1, 1]})}},
        **{p + "_top": {"scale": kf({0: [1, 0.02, 1], 0.5: [1, 1.2, 1], 0.9: [1, 1, 1]})} for p in pillars})}
    finish(g, an, "sanctuary", [(255, 214, 90), (255, 240, 170), (255, 255, 235), (200, 150, 40)], (110, 100, 150, 70))

# ------------------------------------------------------------------------------------------------ space: portal, slash, time, crack
def portal(name, pal, alphas):
    g = Geo(name, bounds=(4, 4))
    an = Anims(name)
    g.bone("frame", (0, 24, 0)); g.ellipse("frame", 15, 25, 25, 2.4, 2.0, 22, 0)
    g.bone("frame2", (0, 24, 0)); g.ellipse("frame2", 13, 22.5, 25, 1.2, 1.2, 20, 1, cz=0.4)
    g.bone("surface", (0, 25, 0)); g.box_c("surface", (0, 25, 0), (26, 46, 0.4), 3)
    g.bone("swirl_a", (0, 25, 0.5)); g.ring_at("swirl_a", 9, 25, 1.4, 0.6, 12, 2, centre=(0, 0, 0))
    # ring_at builds around Y; rotate it to face Z via static bone rotation
    g.bones["swirl_a"]["rotation"] = [90, 0, 0]; g.bones["swirl_a"]["pivot"] = [0, 25, 0.5]
    g.bone("swirl_b", (0, 25, 0.5), rot=(90, 0, 0)); g.ring_at("swirl_b", 5, 25, 1.2, 0.6, 9, 1)
    g.bone("sparks", (0, 25, 0))
    for k in range(10):
        a = 2 * math.pi * k / 10
        g.box_c("sparks", (math.cos(a) * 12, 25 + math.sin(a) * 21, 1.2), (1.4, 1.4, 1.4), 2, rot=(45, 45, 0))
    standard(an, spawn_len=0.45)
    an.add("idle", 6.0, {"frame": {"scale": wave(3, 0.02, 0, (1, 1, 1), 4)}, "frame2": {"scale": wave(2, 0.02, 1, (1, 1, 1), 4)},
                         "swirl_a": {"rotation": kf({0: [90, 0, 0], 3: [90, 0, 180], 6: [90, 0, 360]})},
                         "swirl_b": {"rotation": kf({0: [90, 0, 0], 3: [90, 0, -180], 6: [90, 0, -360]})},
                         "sparks": {"rotation": spin(4, 2, 1, 4)}, "surface": {"scale": wave(2, 0.03, 1, (1, 1, 1), 4)}})
    an.add("action", 0.4, {"surface": {"scale": kf({0: [1, 1, 1], 0.1: [1.1, 1.05, 3], 0.4: [1, 1, 1]})}}, loop=False)
    finish(g, an, name, pal, alphas)

def slash_wave():
    g = Geo("slash_wave", bounds=(4, 4))
    an = Anims("slash_wave")
    g.bone("crescent", (0, 0, 0))
    g.arc("crescent", 22, 20, 160, 14, 3.0, 1.2, 2)
    g.arc("crescent", 22, 28, 152, 12, 1.4, 1.6, 0, cz=-0.4)
    g.arc("crescent", 18, 40, 140, 10, 1.0, 1.0, 1, cz=-3)
    g.arc("crescent", 14, 55, 125, 8, 0.8, 0.8, 1, cz=-6)
    g.bone("sparks", (0, 10, 0))
    for k in range(6):
        g.box_c("sparks", (-16 + k * 6.4, 20 + 4 * math.sin(k), -4 - k), (1.2, 1.2, 1.2), 2, rot=(45, 45, 0))
    an.add("spawn", 0.12, {"root": {"scale": kf({0: [0.3, 0.3, 0.3], 0.12: [1, 1, 1]})}}, loop=False)
    an.add("idle", 0.3, {"crescent": {"scale": kf({0: [1, 1, 1], 0.15: [1.05, 1.1, 1.3], 0.3: [1, 1, 1]})}, "sparks": {"rotation": spin(0.6, 2, 1, 4)}})
    an.add("despawn", 0.2, {"root": {"scale": kf({0: [1, 1, 1], 0.2: [1.2, 0.05, 0.4]})}}, loop="hold")
    finish(g, an, "slash_wave", [(200, 170, 255), (140, 110, 255), (255, 255, 255), (80, 50, 180)], (230, 230, 255, 200))

def time_ring():
    g = Geo("time_ring", bounds=(4, 3))
    an = Anims("time_ring")
    g.bone("ring_o"); g.ring("ring_o", 26, 0.6, 1.6, 0.8, 28, 0)
    g.bone("ring_i"); g.ring("ring_i", 21, 0.7, 1.0, 0.8, 24, 1)
    g.bone("ticks")
    for k in range(12):
        a = 2 * math.pi * k / 12
        g.box_c("ticks", (math.cos(a) * 23.5, 0.8, math.sin(a) * 23.5), (4.2 if k % 3 == 0 else 2.4, 0.8, 1.2), 2, rot=(0, -math.degrees(a), 0))
    g.bone("hour", (0, 1.2, 0)); g.box_c("hour", (0, 1.2, -7), (1.8, 0.8, 14), 0)
    g.bone("minute", (0, 1.6, 0)); g.box_c("minute", (0, 1.6, -10), (1.2, 0.8, 20), 2)
    g.bone("hub", (0, 2, 0)); g.box_c("hub", (0, 2, 0), (3, 2, 3), 1, rot=(0, 45, 0))
    g.bone("gears")
    for k in range(5):
        a = 2 * math.pi * k / 5
        g.box_c("gears", (math.cos(a) * 14, 5 + 2 * math.sin(k), math.sin(a) * 14), (3, 1, 3), 3, rot=(0, 45, 0))
        g.box_c("gears", (math.cos(a) * 14, 5 + 2 * math.sin(k), math.sin(a) * 14), (3, 1, 3), 1)
    standard(an, spawn_len=0.6)
    an.add("idle", 12.0, {"ring_o": {"rotation": spin(30, 1, -1, 8)}, "ring_i": {"rotation": spin(20, 1, 1, 8)},
                          "minute": {"rotation": spin(6, 1, 1, 8)}, "hour": {"rotation": spin(36, 1, 1, 8)},
                          "gears": {"rotation": spin(8, 1, -1, 8), "position": wave(4, 1.5, 1, (0, 0, 0), 4)}, "hub": {"rotation": spin(3, 1, 1, 4)}})
    an.anims["animation.time_ring.despawn"] = {"loop": "hold_on_the_last_frame", "animation_length": 0.8, "bones": {
        "minute": {"rotation": kf({0: [0, 0, 0], 0.8: [0, 1440, 0]})}, "hour": {"rotation": kf({0: [0, 0, 0], 0.8: [0, 360, 0]})},
        "root": {"scale": kf({0: [1, 1, 1], 0.6: [1.1, 1, 1.1], 0.8: [0.01, 0.01, 0.01]})}}}
    finish(g, an, "time_ring", [(240, 176, 60), (255, 220, 120), (255, 250, 220), (140, 90, 20)], (220, 220, 255, 190))

def reality_crack():
    g = Geo("reality_crack", bounds=(2, 6))
    an = Anims("reality_crack")
    g.bone("crack")
    z = 0.0
    x = 0.0
    random.seed(5)
    segs = 18
    for k in range(segs):
        w = 6.0 * (1 - abs(k - segs / 2) / (segs / 2)) + 1.4
        ang = random.choice((-1, 1)) * random.uniform(14, 30)
        g.box_c("crack", (x, 0.5, -z - 8), (w, 0.8, 17), 3, rot=(0, ang, 0))
        g.box_c("crack", (x, 0.9, -z - 8), (max(0.6, w * 0.45), 0.8, 17.5), 2, rot=(0, ang, 0))
        x += math.sin(math.radians(ang)) * 5
        z += 16
    g.bone("shards")
    for k in range(26):
        zz = random.uniform(0, segs * 16)
        xx = random.uniform(-6, 6) + math.sin(zz / 40) * 4
        h = random.uniform(7, 22)
        g.box_c("shards", (xx, h / 2, -zz), (1.2, h, 1.2), 2 if k % 2 else 1, rot=(random.uniform(-18, 18), random.uniform(0, 90), random.uniform(-18, 18)))
    an.add("spawn", 0.35, {"root": {"scale": kf({0: [0.05, 0.3, 0.02], 0.3: [1.1, 1.1, 1.05], 0.35: [1, 1, 1]})}}, loop=False)
    an.add("idle", 0.8, {"shards": {"scale": kf({0: [1, 1, 1], 0.2: [1, 1.15, 1], 0.4: [1, 0.85, 1], 0.6: [1, 1.1, 1], 0.8: [1, 1, 1]})},
                         "crack": {"scale": kf({0: [1, 1, 1], 0.4: [1.06, 1, 1], 0.8: [1, 1, 1]})}})
    an.add("despawn", 0.5, {"root": {"scale": kf({0: [1, 1, 1], 0.5: [0.01, 0.1, 1]})}, "shards": {"position": kf({0: [0, 0, 0], 0.5: [0, -14, 0]})}}, loop="hold")
    finish(g, an, "reality_crack", [(150, 110, 255), (210, 190, 255), (255, 255, 255), (30, 10, 60)], (230, 255, 255, 220))

def phantom_wall():
    g = Geo("phantom_wall", bounds=(5, 4))
    an = Anims("phantom_wall")
    rows = 4
    for r in range(rows):
        g.bone("row%d" % r, (0, 0, 0))
        off = 0 if r % 2 == 0 else 9
        for c in range(-3, 4):
            x = c * 18 + off - 9
            if abs(x) > 28: continue
            g.box_c("row%d" % r, (x, r * 12 + 5.5, 0), (17.2, 11, 5), (r + c) % 3)
    standard(an, spawn_len=0.5)
    an.add("idle", 2.0, {("row%d" % r): {"position": kf({0: [0, 0, 0], 0.5 + r * 0.1: [0.5 * (-1) ** r, 0.15, 0], 1.0: [0, 0, 0], 1.5: [-0.4 * (-1) ** r, -0.1, 0], 2: [0, 0, 0]})} for r in range(rows)})
    an.add("action", 0.4, {("row%d" % r): {"scale": kf({0: [1, 1, 1], 0.1: [1.04, 1.03, 1.3], 0.4: [1, 1, 1]})} for r in range(rows)}, loop=False)
    finish(g, an, "phantom_wall", [(128, 128, 132), (150, 150, 156), (190, 190, 196), (96, 96, 100)], (140, 160, 180, 150))

if __name__ == "__main__":
    vine_guardian()
    sigil("rune_sigil", [(60, 96, 230), (130, 170, 255), (235, 242, 255), (30, 50, 140)])
    sigil("rune_sigil_red", [(210, 60, 36), (255, 150, 60), (255, 236, 170), (120, 24, 12)])
    orb("gravity_orb", [(120, 150, 200), (180, 215, 255), (255, 255, 255), (22, 30, 64)], 6, True, (210, 220, 255, 255))
    orb("void_orb", [(86, 36, 170), (160, 90, 255), (235, 205, 255), (10, 4, 20)], 6, True, (220, 230, 255, 255))
    orb("black_hole", [(14, 8, 22), (255, 150, 60), (255, 232, 190), (2, 1, 5)], 8, True, (240, 255, 255, 255))
    bloom()
    mirror()
    shield_panel("shield_panel", [(110, 190, 255), (210, 235, 255), (255, 255, 255), (60, 110, 190)], (110, 255, 255, 150))
    shield_panel("shield_panel_gold", [(255, 214, 110), (255, 238, 170), (255, 255, 235), (200, 150, 50)], (110, 255, 255, 150))
    dome("dome_blue", [(110, 190, 255), (200, 230, 255), (255, 255, 255), (60, 110, 190)], (70, 160, 255, 120))
    dome("dome_gold", [(255, 214, 110), (255, 238, 170), (255, 255, 235), (200, 150, 50)], (70, 160, 255, 120))
    fire_pillar()
    tornado("tornado_sand", [(214, 190, 120), (236, 216, 150), (255, 240, 200), (150, 125, 70)], (150, 190, 255, 120))
    roots()
    light_orb()
    sanctuary()
    portal("portal_blue", [(60, 130, 255), (150, 200, 255), (240, 250, 255), (10, 30, 90)], (255, 255, 255, 190))
    portal("portal_orange", [(255, 140, 40), (255, 200, 120), (255, 245, 220), (110, 40, 6)], (255, 255, 255, 190))
    portal("portal_violet", [(150, 70, 255), (210, 160, 255), (250, 235, 255), (40, 10, 90)], (255, 255, 255, 190))
    slash_wave()
    time_ring()
    reality_crack()
    phantom_wall()
    print("props ok")
