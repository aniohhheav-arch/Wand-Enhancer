"""3D item models built from coloured cuboids that sample cells of a shared 16x16 palette texture."""
import numpy as np
from .common import ASSETS, rl, write_json

PALETTE = [0x14101C, 0x6A2AC8, 0xB050FF, 0xFFFFFF, 0x000000, 0xE0C0FF,   # 0-5 rupture
           0x6A5A40, 0x3A3020, 0xFF8A20, 0xFFF0C0,                        # 6-9 staff
           0xB8BCC4, 0x1A2430, 0x101010, 0x80C0FF,                        # 10-13 car
           0x1A3A8A, 0x10265A]                                            # 14-15 box


def palette_png():
    from .textures import save
    img = np.zeros((16, 16, 4))
    for i, c in enumerate(PALETTE):
        x, y = (i % 4) * 4, (i // 4) * 4
        img[y:y + 4, x:x + 4, 0] = ((c >> 16) & 255) / 255
        img[y:y + 4, x:x + 4, 1] = ((c >> 8) & 255) / 255
        img[y:y + 4, x:x + 4, 2] = (c & 255) / 255
        img[y:y + 4, x:x + 4, 3] = 1.0
    save(img, "item/palette3d.png")


def el(frm, to, color, glow=False, rot=None):
    x, y = (color % 4) * 4 + 1, (color // 4) * 4 + 1
    face = {"uv": [x, y, x + 2, y + 2], "texture": "#p"}
    e = {"from": frm, "to": to, "faces": {d: dict(face) for d in ("north", "south", "east", "west", "up", "down")}}
    if glow:
        e["shade"] = False
        e["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    if rot:
        e["rotation"] = rot
    return e


HAND = {"thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4, 0.5], "scale": [0.85, 0.85, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
        "gui": {"rotation": [0, 90, -45], "scale": [0.6, 0.6, 0.6]},
        "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
        "fixed": {"rotation": [0, 90, -45], "scale": [0.6, 0.6, 0.6]}}
OBJECT = {"gui": {"rotation": [30, 225, 0], "scale": [0.55, 0.55, 0.55]},
          "ground": {"translation": [0, 3, 0], "scale": [0.3, 0.3, 0.3]},
          "fixed": {"scale": [0.5, 0.5, 0.5]},
          "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.35, 0.35, 0.35]},
          "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]},
          "firstperson_lefthand": {"rotation": [0, 225, 0], "scale": [0.4, 0.4, 0.4]}}


def rupture():
    e = [el([7, -4, 7], [9, 4, 9], 0), el([6.5, -5, 6.5], [9.5, -4, 9.5], 1),      # grip + pommel
         el([4, 4, 7], [12, 5.5, 9], 1), el([3, 4.5, 7.5], [4, 6, 8.5], 2, True), el([12, 4.5, 7.5], [13, 6, 8.5], 2, True)]
    zig = [0, 1.2, -0.8, 1.0, -1.2, 0.8, -0.6, 0.4]
    y = 5.5
    for i, dx in enumerate(zig):
        h = 3.2
        w = 1.6 - i * 0.12
        x = 8 + dx
        e.append(el([x - w, y, 7.4], [x + w, y + h, 8.6], 1 if i % 2 else 2))
        e.append(el([x - w * 0.4, y, 7.2], [x + w * 0.4, y + h, 8.8], 4))
        e.append(el([x + w, y + 0.5, 7.7], [x + w + 0.35, y + h - 0.5, 8.3], 3, True))
        y += h - 0.2
    return e


def staff():
    e = [el([7.25, -8, 7.25], [8.75, 24, 8.75], 6), el([7, -9, 7], [9, -8, 9], 7),
         el([6.75, 2, 6.75], [9.25, 3.5, 9.25], 7), el([6.5, 22, 6.5], [9.5, 23.5, 9.5], 7)]
    e += [el([5, 24, 7.5], [11, 25, 8.5], 7), el([4.5, 25, 7.5], [5.5, 29, 8.5], 7), el([10.5, 25, 7.5], [11.5, 29, 8.5], 7)]
    e += [el([6.5, 24.5, 6.5], [9.5, 29.5, 9.5], 8, True), el([7.25, 25.5, 7.25], [8.75, 28.5, 8.75], 9, True)]
    return e


def car():
    return [el([3, 2, 0], [13, 5, 16], 10), el([4, 5, 4], [12, 8, 11], 10), el([4.5, 5.5, 11], [11.5, 7.5, 11.5], 11),
            el([3.2, 5.6, 5], [3.4, 7.6, 10], 11), el([12.6, 5.6, 5], [12.8, 7.6, 10], 11),
            el([2, 0, 2], [3.2, 3, 5], 12), el([12.8, 0, 2], [14, 3, 5], 12), el([2, 0, 11], [3.2, 3, 14], 12), el([12.8, 0, 11], [14, 3, 14], 12),
            el([7, 5, 0.5], [9, 7, 2.5], 10), el([4, 3.5, 15.9], [6, 4.5, 16.2], 9, True), el([10, 3.5, 15.9], [12, 4.5, 16.2], 9, True),
            el([7.5, 6.5, 5.5], [8.5, 7.5, 6.5], 13, True)]


def box():
    e = [el([3, 0, 3], [13, 1, 13], 15), el([3.5, 1, 3.5], [12.5, 15, 12.5], 14), el([3.2, 13.5, 3.2], [12.8, 15, 12.8], 15),
         el([4, 15, 4], [12, 16, 12], 14), el([7.25, 16, 7.25], [8.75, 18, 8.75], 3, True)]
    for c in (3, 12):
        for d in (3, 12):
            e.append(el([c, 1, d], [c + 1, 15, d + 1], 15))
    for s in ((4, 3.4, 12, 3.5), (4, 12.5, 12, 12.6)):
        e.append(el([s[0], 11, s[1]], [7.5, 12.8, s[3]], 3, True))
        e.append(el([8.5, 11, s[1]], [s[2], 12.8, s[3]], 3, True))
    for s in ((3.4, 3.5), (12.5, 12.6)):
        e.append(el([s[0], 11, 4], [s[1], 12.8, 7.5], 3, True))
        e.append(el([s[0], 11, 8.5], [s[1], 12.8, 12], 3, True))
    return e


def generate():
    palette_png()
    for name, elements, display in (("reality_rupture", rupture(), HAND), ("pruning_staff", staff(), HAND),
                                    ("delorean", car(), OBJECT), ("tardis", box(), OBJECT)):
        write_json(ASSETS / "models" / "item" / f"{name}.json",
                   {"textures": {"p": rl("item/palette3d"), "particle": rl("item/palette3d")}, "elements": elements, "display": display})
