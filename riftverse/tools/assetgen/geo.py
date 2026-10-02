"""GeckoLib geometry/animation/texture generator. Models are built from coloured cubes; every face samples a cell of a
64x64 palette texture. Glowing colours are also written to a _glowmask texture for GeckoLib's AutoGlowingGeoLayer."""
import numpy as np
from .common import ASSETS, write_json

# (color, glow)
COLORS = [
    (0x1A3A8A, False), (0x10265A, False), (0xE8F0FF, True), (0x101010, False), (0xFFFFFF, True), (0x2A4A9A, False),   # 0-5 tardis
    (0xB8BCC4, False), (0x8A8E96, False), (0x1A2430, False), (0x2A2C30, False), (0x0C0C0C, False), (0xC02020, True),  # 6-11 car
    (0xFFFFE0, True), (0x80C0FF, True), (0xD0D0D0, False), (0x5A5E66, False),                                        # 12-15
    (0xFF8A20, True), (0xFFF0C0, True), (0x3A2A1A, False), (0xC05010, True),                                         # 16-19 door
]


def cell(i):
    return (i % 8) * 8, (i // 8) * 8


def texture(name):
    from .textures import save, shaded_cells
    img = shaded_cells([c for c, _ in COLORS], seed=hash(name) % 1000)
    glow = img.copy()
    for i, (c, g) in enumerate(COLORS):
        x, y = cell(i)
        if g:
            img[y:y + 8, x:x + 8, :3] = np.clip(img[y:y + 8, x:x + 8, :3] * 1.15, 0, 1)
        else:
            glow[y:y + 8, x:x + 8, 3] = 0
    for i in range(len(COLORS), 64):
        x, y = cell(i)
        glow[y:y + 8, x:x + 8, 3] = 0
    save(img, f"entity/{name}.png")
    save(glow, f"entity/{name}_glowmask.png")


def cube(origin, size, color, pivot=None, rotation=None, inflate=0):
    x, y = cell(color)
    uv = {"uv": [x, y], "uv_size": [8, 8]}
    c = {"origin": origin, "size": size, "uv": {d: dict(uv) for d in ("north", "south", "east", "west", "up", "down")}}
    if inflate:
        c["inflate"] = inflate
    if rotation:
        c["pivot"] = pivot
        c["rotation"] = rotation
    return c


def bone(name, pivot, cubes, parent=None):
    b = {"name": name, "pivot": pivot, "cubes": cubes}
    if parent:
        b["parent"] = parent
    return b


def write(name, bones, animations, bounds=(4, 4)):
    write_json(ASSETS / "geo" / "entity" / f"{name}.geo.json", {
        "format_version": "1.12.0",
        "minecraft:geometry": [{"description": {"identifier": f"geometry.{name}", "texture_width": 64, "texture_height": 64,
                                                "visible_bounds_width": bounds[0], "visible_bounds_height": bounds[1],
                                                "visible_bounds_offset": [0, bounds[1] / 2, 0]}, "bones": bones}]})
    write_json(ASSETS / "animations" / "entity" / f"{name}.animation.json",
               {"format_version": "1.8.0", "animations": {f"animation.{name}.{k}": v for k, v in animations.items()}})
    texture(name)


def swing(bonename, frm, to, length):
    return {"loop": "hold_on_last_frame", "animation_length": length,
            "bones": {b: {"rotation": {"0.0": [0, f, 0], str(length): [0, t, 0]}} for b, f, t in zip(bonename, frm, to)}}


def tardis():
    root = [cube([-11, 0, -11], [22, 2, 22], 1),                      # plinth
            cube([-9.5, 2, -9.5], [19, 36, 19], 0),                   # shell
            cube([-10.5, 34, -10.5], [21, 4, 21], 1),                 # roof sign band
            cube([-9, 38, -9], [18, 2, 18], 0), cube([-7, 40, -7], [14, 1.5, 14], 5)]
    for cx in (-11, 9):
        for cz in (-11, 9):
            root.append(cube([cx, 2, cz], [2, 36, 2], 1))            # corner posts
    # sign lettering strips (glow) on all four sides
    root += [cube([-8, 35, -10.6], [16, 2, 0.2], 4), cube([-8, 35, 10.4], [16, 2, 0.2], 4),
             cube([-10.6, 35, -8], [0.2, 2, 16], 4), cube([10.4, 35, -8], [0.2, 2, 16], 4)]
    # side and back panels + windows
    for side in ((-9.7, None), (9.5, None)):
        for row in range(4):
            y = 4 + row * 7.5
            col = 2 if row == 3 else 1
            root.append(cube([side[0], y, -8], [0.2, 6, 7.5], col))
            root.append(cube([side[0], y, 0.5], [0.2, 6, 7.5], col))
    for row in range(4):
        y = 4 + row * 7.5
        col = 2 if row == 3 else 1
        root.append(cube([-8, y, -9.7], [7.5, 6, 0.2], col))
        root.append(cube([0.5, y, -9.7], [7.5, 6, 0.2], col))
    lamp = [cube([-1.5, 41.5, -1.5], [3, 1, 3], 1), cube([-1, 42.5, -1], [2, 3, 2], 2), cube([-1.5, 45.5, -1.5], [3, 0.7, 3], 1)]
    door_l, door_r = [], []
    for row in range(4):
        y = 4 + row * 7.5
        col = 2 if row == 3 else 1
        door_l.append(cube([-8.5, y, 9.5], [8, 6, 0.4], col))
        door_r.append(cube([0.5, y, 9.5], [8, 6, 0.4], col))
    door_l.append(cube([-9.5, 2, 9.3], [9.5, 32, 0.4], 0))
    door_r.append(cube([0, 2, 9.3], [9.5, 32, 0.4], 0))
    door_r.append(cube([1, 18, 9.8], [0.6, 2, 0.8], 3))
    bones = [bone("root", [0, 0, 0], root), bone("lamp", [0, 42, 0], lamp, "root"),
             bone("door_left", [-9.5, 0, 9.5], door_l, "root"), bone("door_right", [9.5, 0, 9.5], door_r, "root")]
    anims = {"doors_open": swing(["door_left", "door_right"], [0, 0], [100, -100], 0.6),
             "doors_close": swing(["door_left", "door_right"], [100, -100], [0, 0], 0.6),
             "lamp": {"loop": True, "animation_length": 1.5, "bones": {"lamp": {"scale": {"0.0": [1, 1, 1], "0.75": [1.25, 1.1, 1.25], "1.5": [1, 1, 1]}}}},
             "spin": {"loop": True, "animation_length": 2.0, "bones": {"root": {"rotation": {"0.0": [0, 0, 0], "2.0": [0, 360, 0]}}}}}
    write("tardis", bones, anims, (3, 4))


def delorean():
    body = [cube([-14.5, 4, -33], [29, 8, 66], 6),                      # lower body
            cube([-13.5, 12, 16], [27, 2, 17], 6),                      # bonnet
            cube([-13, 12, -19], [26, 8, 35], 6),                       # cabin base
            cube([-12, 12.5, 15], [24, 6.5, 2], 8),                     # windscreen
            cube([-12.5, 20, -17], [25, 1, 30], 7),                     # roof
            cube([-14.6, 7, -33], [29.2, 1, 66], 15),                   # side stripe
            cube([-15, 3, 31], [30, 3, 2.5], 9), cube([-15, 3, -33.5], [30, 3, 2.5], 9),   # bumpers
            cube([-13, 9, 32.6], [5, 2, 0.6], 12), cube([8, 9, 32.6], [5, 2, 0.6], 12),     # headlights
            cube([-13.5, 8, -33.4], [7, 2, 0.6], 11), cube([6.5, 8, -33.4], [7, 2, 0.6], 11),  # tail lights
            cube([-4, 12, -31], [8, 6, 8], 14), cube([-3, 18, -30], [6, 1.5, 6], 7),          # reactor
            cube([-1, 14.5, -14], [2, 3, 1], 13),                       # flux capacitor
            cube([-9, 13.5, 13.5], [18, 1, 0.5], 11)]                   # time circuit strip
    for i in range(5):
        body.append(cube([-12, 12 + i * 0.8, -31 + i * 2.5], [24, 0.6, 1.5], 9))  # louvres
    wheels = []
    for name, x, z in (("wheel_fl", -16, 21), ("wheel_fr", 12, 21), ("wheel_bl", -16, -22), ("wheel_br", 12, -22)):
        wheels.append(bone(name, [x + 2, 4.5, z], [cube([x, 0, z - 4.5], [4, 9, 9], 10), cube([x - 0.2, 2.5, z - 2], [4.4, 4, 4], 15)], "root"))
    door_l = [cube([-14, 12, -15], [1, 8, 28], 6), cube([-14.2, 13, -12], [0.4, 6, 20], 8)]
    door_r = [cube([13, 12, -15], [1, 8, 28], 6), cube([13.8, 13, -12], [0.4, 6, 20], 8)]
    bones = [bone("root", [0, 0, 0], body)] + wheels + [
        bone("door_left", [-12.5, 21, 0], door_l, "root"), bone("door_right", [12.5, 21, 0], door_r, "root")]
    spin = {b: {"rotation": {"0.0": [0, 0, 0], "0.5": [-180, 0, 0], "1.0": [-360, 0, 0]}} for b in ("wheel_fl", "wheel_fr", "wheel_bl", "wheel_br")}
    anims = {"drive": {"loop": True, "animation_length": 1.0, "bones": spin},
             "doors_open": {"loop": "hold_on_last_frame", "animation_length": 0.8,
                            "bones": {"door_left": {"rotation": {"0.0": [0, 0, 0], "0.8": [0, 0, -95]}},
                                      "door_right": {"rotation": {"0.0": [0, 0, 0], "0.8": [0, 0, 95]}}}},
             "doors_close": {"loop": "hold_on_last_frame", "animation_length": 0.6,
                             "bones": {"door_left": {"rotation": {"0.0": [0, 0, -95], "0.6": [0, 0, 0]}},
                                       "door_right": {"rotation": {"0.0": [0, 0, 95], "0.6": [0, 0, 0]}}}}}
    write("delorean", bones, anims, (5, 3))


def time_door():
    frame = [cube([-9, 0, -1], [2, 34, 2], 16), cube([7, 0, -1], [2, 34, 2], 16), cube([-9, 32, -1], [18, 2, 2], 16),
             cube([-7, 0, -0.4], [14, 32, 0.8], 19)]
    door = [cube([-7, 0, -0.6], [14, 32, 1.2], 18), cube([-6, 2, -0.8], [12, 13, 1.6], 18), cube([-6, 17, -0.8], [12, 13, 1.6], 18),
            cube([4, 15, -1.2], [1.5, 2, 2.4], 17)]
    bones = [bone("root", [0, 0, 0], frame), bone("door", [-7, 0, 0], door, "root")]
    anims = {"open": {"loop": "hold_on_last_frame", "animation_length": 0.5,
                      "bones": {"door": {"rotation": {"0.0": [0, 0, 0], "0.5": [0, -105, 0]}},
                                "root": {"scale": {"0.0": [0.2, 0.0, 0.2], "0.25": [1, 1, 1]}}}},
             "close": {"loop": "hold_on_last_frame", "animation_length": 0.5,
                       "bones": {"door": {"rotation": {"0.0": [0, -105, 0], "0.3": [0, 0, 0]}},
                                 "root": {"scale": {"0.3": [1, 1, 1], "0.5": [0.1, 0, 0.1]}}}}}
    write("time_door", bones, anims, (3, 3))


def generate():
    tardis()
    delorean()
    time_door()
