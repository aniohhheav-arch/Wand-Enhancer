"""16x16 pixel-art items for Phase 3: the Infinity Gauntlet, six stones, portal gun, rift blade and orbital beacon."""
import os
from generate_icons import write_png

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "infinitemultiverse", "textures", "item")


def hexrgb(v):
    return ((v >> 16) & 255, (v >> 8) & 255, v & 255)


def save(name, art, palette):
    rows = []
    for line in art:
        row = []
        for ch in line.ljust(16, "."):
            row.append(hexrgb(palette[ch]) + (255,) if ch in palette else (0, 0, 0, 0))
        rows.append(row)
    write_png(os.path.join(OUT, name + ".png"), 16, 16, rows)


GAUNTLET = [
    "................",
    "....oo.oo.oo....",
    "...oGGoGGoGGo...",
    "...oGGoGGoGGo.oo",
    "...oGGoGGoGGooGo",
    "...oGGGGGGGGoGGo",
    "...oGsGtGuGGGGo.",
    "..ooGGGGGGGGGGo.",
    "..oGGGvGGGGGGo..",
    "..oGGGGGwGxGGo..",
    "..oGGGGGGGGGo...",
    "...oGGGGGGGGo...",
    "...ohhhhhhhho...",
    "...oGGGGGGGGo...",
    "....oGGGGGGo....",
    ".....oooooo.....",
]
pal = {"o": 0x5A3A08, "G": 0xE8B830, "h": 0xFFE680, "s": 0x3D7BFF, "t": 0xFFD83D, "u": 0xE0202A, "v": 0xA040FF, "w": 0x2EE06A, "x": 0xFF8A1A}
save("infinity_gauntlet", GAUNTLET, pal)

STONE = [
    "................",
    "................",
    "................",
    "......oooo......",
    ".....oLLLLo.....",
    "....oLHLLLDo....",
    "...oLHHLLLLDo...",
    "...oLHLLLLLDo...",
    "...oLLLLLLLDo...",
    "...oLLLLLLDDo...",
    "....oLLLLDDo....",
    ".....oDDDDo.....",
    "......oooo......",
    "................",
    "................",
    "................",
]
for name, c in (("space", 0x3D7BFF), ("mind", 0xFFD83D), ("reality", 0xE0202A), ("power", 0xA040FF), ("time", 0x2EE06A), ("soul", 0xFF8A1A)):
    r, g, b = hexrgb(c)
    dark = (int(r * 0.55) << 16) | (int(g * 0.55) << 8) | int(b * 0.55)
    save(name + "_stone", STONE, {"o": 0x101018, "L": c, "D": dark, "H": 0xFFFFFF})

GUN = [
    "................",
    "................",
    "................",
    "...........oo...",
    "..........oWWo..",
    "...ooooooWWBBo..",
    "..oWWWWWWWBBBo..",
    ".oWWWWWWWWWBBo..",
    ".oKKKKKKWWWWo...",
    "..oooooKKWWo....",
    "......oKKKo.....",
    "......oKKo......",
    ".....oKKo.......",
    ".....ooo........",
    "................",
    "................",
]
save("portal_gun", GUN, {"o": 0x202428, "W": 0xE8ECF0, "B": 0x3DA0FF, "K": 0x404850})

BLADE = [
    "..............oo",
    ".............oPo",
    "............oPPo",
    "...........oPWo.",
    "..........oPWo..",
    ".........oPWo...",
    "........oPWo....",
    ".......oPWo.....",
    "......oPWo......",
    "..o..oPWo.......",
    "..oooPWo........",
    "...oKKo.........",
    "..oKKoo.........",
    ".oKKo.o.........",
    "oKKo............",
    "oo..............",
]
save("rift_blade", BLADE, {"o": 0x1A0A28, "P": 0xB040FF, "W": 0xF0D8FF, "K": 0x3A2A50})

BEACON = [
    "................",
    ".......oo.......",
    "......oWWo......",
    "......oWWo......",
    ".....oWBBWo.....",
    ".....oWBBWo.....",
    ".....oWWWWo.....",
    "....oWWWWWWo....",
    "....oRWWWWRo....",
    "...oRRoWWoRRo...",
    "...oRo.oo.oRo...",
    "...oo..FF..oo...",
    ".......FF.......",
    "......FYYF......",
    ".......YY.......",
    "................",
]
save("orbital_beacon", BEACON, {"o": 0x202428, "W": 0xE8ECF0, "B": 0x3DA0FF, "R": 0xD03030, "F": 0xFF8A1A, "Y": 0xFFE680})
print("cosmic items written")
