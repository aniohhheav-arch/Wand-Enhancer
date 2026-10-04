#!/usr/bin/env python3
"""Generates the multi-part 3D revolver models.

The Sheriff's Last Word is assembled at render time from separately animated
parts (frame, cylinder, hammer) plus the holster. Coordinates are in model
pixels: the barrel points along +X, +Y is up, Z is the lateral axis (centre 8).
Pivots used by the Java renderer live in RevolverGeometry.java and must match
the CYLINDER_AXIS / HAMMER_PIVOT constants below.
"""
import json
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "finalframe", "models")
T = "finalframe:item/revolver/"
TEXTURES = {
    "blued": T + "steel_blued",
    "polished": T + "steel_polished",
    "engraved": T + "steel_engraved",
    "walnut": T + "walnut",
    "brass": T + "brass",
    "face": T + "cylinder_face",
    "flutes": T + "cylinder_flutes",
    "leather": T + "leather",
    "bore": T + "bore",
}
CYLINDER_AXIS = (7.85, 8.0)  # (y, z)
HAMMER_PIVOT = (5.7, 8.7, 8.0)


def box(frm, to, tex, rot=None, faces=None, name=None, skip=()):
    fs = {}
    for f in ("north", "south", "east", "west", "up", "down"):
        if f in skip:
            continue
        fs[f] = {"texture": "#" + tex}
    for f, spec in (faces or {}).items():
        fs[f] = {"texture": "#" + spec[0], **({"uv": spec[1]} if len(spec) > 1 else {})}
    e = {"from": [round(v, 3) for v in frm], "to": [round(v, 3) for v in to], "faces": fs}
    if rot:
        axis, angle, origin = rot
        e["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
    if name:
        e["name"] = name
    return e


def zc(half):
    return 8 - half, 8 + half


def model(elements, textures=None):
    tx = dict(textures or TEXTURES)
    tx["particle"] = TEXTURES["blued"]
    return {"textures": tx, "elements": elements}


def frame():
    full = [0, 0, 16, 16]
    e = []
    # Barrel, rib and crown.
    e.append(box((9.6, 8.0, zc(0.6)[0]), (15.4, 9.2, zc(0.6)[1]), "blued", name="barrel"))
    e.append(box((9.6, 9.2, zc(0.32)[0]), (15.2, 9.42, zc(0.32)[1]), "polished", name="rib"))
    e.append(box((15.2, 7.86, zc(0.74)[0]), (15.75, 9.34, zc(0.74)[1]), "brass", name="muzzle_band",
                 faces={"east": ("polished",)}))
    e.append(box((15.751, 8.3, zc(0.26)[0]), (15.76, 8.9, zc(0.26)[1]), "bore", name="bore"))
    e.append(box((14.55, 9.42, zc(0.13)[0]), (15.15, 10.05, zc(0.13)[1]), "polished", name="front_sight",
                 rot=("z", -22.5, (14.85, 9.42, 8))))
    # Ejector housing and rod under the barrel.
    e.append(box((10.4, 7.25, zc(0.42)[0]), (14.6, 8.0, zc(0.42)[1]), "blued", name="ejector_housing"))
    e.append(box((14.6, 7.36, zc(0.24)[0]), (15.05, 7.9, zc(0.24)[1]), "brass", name="ejector_head"))
    # Receiver frame around the cylinder window.
    e.append(box((5.9, 9.15, zc(0.72)[0]), (9.8, 9.75, zc(0.72)[1]), "blued", name="top_strap"))
    e.append(box((6.2, 9.75, zc(0.12)[0]), (7.0, 9.95, zc(0.12)[1]), "polished", name="rear_sight_notch"))
    e.append(box((9.0, 6.3, zc(0.82)[0]), (9.9, 9.75, zc(0.82)[1]), "blued", name="front_frame",
                 faces={"north": ("engraved", [0, 0, 6, 16]), "south": ("engraved", [0, 0, 6, 16])}))
    e.append(box((5.2, 6.1, zc(0.82)[0]), (9.9, 6.65, zc(0.82)[1]), "blued", name="frame_floor"))
    e.append(box((4.9, 6.1, zc(0.95)[0]), (6.15, 9.75, zc(0.95)[1]), "polished", name="recoil_shield",
                 faces={"north": ("engraved", full), "south": ("engraved", full)}))
    e.append(box((9.9, 7.55, zc(0.18)[0]), (10.4, 8.1, zc(0.18)[1]), "polished", name="cylinder_pin"))
    # Side plate screws.
    for x, y in ((5.45, 7.0), (5.45, 9.1), (9.45, 6.9)):
        for z0, z1 in ((zc(1.0)[0], zc(0.95)[0]), (zc(0.95)[1], zc(1.0)[1])):
            e.append(box((x - 0.2, y - 0.2, z0), (x + 0.2, y + 0.2, z1), "brass", name="screw"))
    # Loading gate.
    e.append(box((5.1, 6.9, zc(1.05)[1] - 0.12), (6.0, 8.8, zc(1.05)[1]), "brass", name="loading_gate"))
    # Grip: steel core, walnut panels, brass butt cap, lanyard ring.
    grip_rot = ("z", 22.5, (5.6, 6.4, 8))
    e.append(box((3.7, 2.4, zc(0.7)[0]), (5.9, 6.5, zc(0.7)[1]), "blued", rot=grip_rot, name="grip_frame"))
    e.append(box((3.55, 2.75, zc(0.98)[0]), (5.75, 6.25, zc(0.98)[1]), "walnut", rot=grip_rot, name="grip_panels",
                 faces={"north": ("walnut", [0, 0, 16, 16]), "south": ("walnut", [0, 0, 16, 16])}))
    e.append(box((3.45, 2.0, zc(0.9)[0]), (6.0, 2.5, zc(0.9)[1]), "brass", rot=grip_rot, name="butt_cap"))
    e.append(box((4.3, 1.4, zc(0.12)[0]), (5.1, 2.0, zc(0.12)[1]), "brass", rot=grip_rot, name="lanyard_ring"))
    e.append(box((3.4, 5.9, zc(0.6)[0]), (4.4, 7.3, zc(0.6)[1]), "blued", rot=grip_rot, name="backstrap_tang"))
    # Trigger guard and trigger.
    e.append(box((6.0, 4.55, zc(0.18)[0]), (8.7, 4.85, zc(0.18)[1]), "brass", name="guard_bottom"))
    e.append(box((8.4, 4.55, zc(0.18)[0]), (8.7, 6.1, zc(0.18)[1]), "brass", name="guard_front"))
    e.append(box((5.8, 4.7, zc(0.18)[0]), (6.15, 5.8, zc(0.18)[1]), "brass", name="guard_rear",
                 rot=("z", 22.5, (6.0, 4.7, 8))))
    e.append(box((6.9, 4.95, zc(0.14)[0]), (7.25, 6.1, zc(0.14)[1]), "polished", name="trigger",
                 rot=("z", -22.5, (7.1, 6.1, 8))))
    return model(e)


def cylinder():
    y, z = CYLINDER_AXIS
    e = []
    r = 1.18
    x0, x1 = 6.2, 9.0
    for i, ang in enumerate((0, 22.5, 45, -22.5)):
        inset = 0 if i == 0 else 0.04
        rot = None if ang == 0 else ("x", ang, (x0, y, z))
        faces = {"east": ("face", [0, 0, 16, 16]), "west": ("face", [0, 0, 16, 16])} if i == 0 else {}
        e.append(box((x0 + inset, y - r, z - r), (x1 - inset, y + r, z + r), "flutes", rot=rot, faces=faces,
                     name="cylinder_%d" % i))
    e.append(box((6.0, y - 0.55, z - 0.55), (6.2, y + 0.55, z + 0.55), "brass", name="ratchet"))
    return model(e)


def hammer():
    px, py, _ = HAMMER_PIVOT
    e = []
    e.append(box((px - 0.65, py - 0.6, zc(0.32)[0]), (px + 0.35, py + 1.15, zc(0.32)[1]), "polished", name="hammer_body"))
    e.append(box((px - 1.55, py + 0.95, zc(0.4)[0]), (px - 0.45, py + 1.5, zc(0.4)[1]), "engraved",
                 rot=("z", -22.5, (px - 0.6, py + 1.2, 8)), name="hammer_spur"))
    e.append(box((px + 0.35, py + 0.1, zc(0.08)[0]), (px + 0.55, py + 0.35, zc(0.08)[1]), "polished", name="firing_pin"))
    return model(e)


def holster():
    e = []
    e.append(box((8.7, 6.7, zc(1.05)[0]), (16.1, 9.95, zc(1.05)[1]), "leather", name="pouch",
                 faces={"north": ("leather", [0, 0, 16, 16]), "south": ("leather", [0, 0, 16, 16])}))
    e.append(box((5.9, 6.0, zc(1.15)[0]), (9.2, 7.0, zc(1.15)[1]), "leather", name="mouth"))
    e.append(box((10.5, 9.95, zc(1.12)[0]), (12.5, 11.6, zc(1.12)[1]), "leather", name="belt_loop"))
    e.append(box((11.2, 8.0, zc(1.12)[0] - 0.1), (11.8, 8.6, zc(1.12)[0]), "brass", name="concho"))
    return model(e)


def item_model():
    # Display transforms for the custom renderer. The BEWLR draws the parts in
    # the same 0..16 space, so these behave like a regular 3D item model.
    return {
        "parent": "builtin/entity",
        "gui_light": "front",
        "textures": {"particle": TEXTURES["blued"]},
        "display": {
            "gui": {"rotation": [0, 0, 0], "translation": [0, 0.5, 0], "scale": [1.1, 1.1, 1.1]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.6, 0.6, 0.6]},
            "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1.1, 1.1, 1.1]},
            "head": {"rotation": [0, 0, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
            "thirdperson_righthand": {"rotation": [0, -90, 0], "translation": [-0.5, 3.5, 1.5], "scale": [0.85, 0.85, 0.85]},
            "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [-0.5, 3.5, 1.5], "scale": [0.85, 0.85, 0.85]},
            "firstperson_righthand": {"rotation": [0, -90, 0], "translation": [2.5, 4.5, -1.0], "scale": [0.9, 0.9, 0.9]},
            "firstperson_lefthand": {"rotation": [0, 90, 0], "translation": [2.5, 4.5, -1.0], "scale": [0.9, 0.9, 0.9]},
        },
    }


def write(rel, data):
    path = os.path.join(OUT, rel + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def main():
    write("item/sheriffs_last_word", item_model())
    write("part/revolver_frame", frame())
    write("part/revolver_cylinder", cylinder())
    write("part/revolver_hammer", hammer())
    write("part/holster", holster())


if __name__ == "__main__":
    main()
