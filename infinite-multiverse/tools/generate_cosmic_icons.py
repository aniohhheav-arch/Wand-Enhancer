"""Icons for the Infinity Stone techniques and Chronokinesis, built from the shared motif primitives."""
import math
from generate_icons import Icon, d_polyline, d_ring, d_segment, d_arc, regular, solid, vertical, inside_polygon
import generate_power_icons as g

SP, MI, RE, PO, TI, SO = 0x3D7BFF, 0xFFD83D, 0xE0202A, 0xA040FF, 0x2EE06A, 0xFF8A1A


def gem(i, c, cx=0, cy=0, s=1.0):
    pts = [(cx, cy - 12 * s), (cx + 9 * s, cy - 2 * s), (cx + 5 * s, cy + 11 * s), (cx - 5 * s, cy + 11 * s), (cx - 9 * s, cy - 2 * s), (cx, cy - 12 * s)]
    i.fill(lambda px, py: inside_polygon(px, py, pts), vertical(0xFFFFFF, c))
    i.stroke(lambda px, py: d_polyline(px, py, pts), 1.0, solid(c), glow=5, glow_strength=0.7)


def cube(i, c, s=13, rot=0.4):
    pts = []
    for z in (-1, 1):
        for y in (-1, 1):
            for x in (-1, 1):
                xr = x * math.cos(rot) - z * math.sin(rot)
                zr = x * math.sin(rot) + z * math.cos(rot)
                pts.append((xr * s + zr * s * 0.35, y * s - zr * s * 0.25))
    for a, b in ((0, 1), (2, 3), (4, 5), (6, 7), (0, 2), (1, 3), (4, 6), (5, 7), (0, 4), (1, 5), (2, 6), (3, 7)):
        i.stroke(lambda px, py, a=a, b=b: d_segment(px, py, *pts[a], *pts[b]), 1.4, solid(c), glow=4)


I = {}


def icon(name, accent):
    def w(fn):
        I[name] = (accent, fn)
        return fn
    return w


@icon("space_warp", SP)
def _(i): gem(i, SP, -10, 6, 0.6); g.arrow(i, 0xBFE0FF, (-6, 0), (20, -14)); g.ring(i, 6, 0xBFE0FF, 1.0, 18, -14)
@icon("space_pull", SP)
def _(i): g.arrow(i, SP, (20, -12), (-14, 10)); g.ring(i, 5, 0xFFFFFF, 1.2, 20, -12)
@icon("tesseract_fold", SP)
def _(i): cube(i, SP); cube(i, 0xBFE0FF, 7, 1.1)
@icon("mind_control", MI)
def _(i): g.eye(i, MI, MI); g.ring(i, 22, MI, 0.8)
@icon("psionic_lance", MI)
def _(i): g.beam(i, MI, 2.6); gem(i, MI, -14, 12, 0.5)
@icon("psionic_storm", MI)
def _(i): [g.ring(i, r, MI, 1.0) for r in (8, 14, 20)]; g.rays(i, 0xFFF0A0, 8, 21, 25, 1.0)
@icon("reality_warp", RE)
def _(i): g.spiral(i, RE, 0x200008, 2.5); gem(i, RE, 0, 0, 0.5)
@icon("reality_lance", RE)
def _(i): g.beam(i, RE, 2.2); g.spiral(i, 0xFF8090, RE, 1.0, 8)
@icon("reality_shatter", RE)
def _(i):
    for k in range(7):
        a = k * 0.9
        pts = [(18 * math.cos(a), 18 * math.sin(a)), (12 * math.cos(a + 0.3), 12 * math.sin(a + 0.3)), (16 * math.cos(a + 0.5), 16 * math.sin(a + 0.5)), (18 * math.cos(a), 18 * math.sin(a))]
        i.stroke(lambda px, py, p=pts: d_polyline(px, py, p), 1.3, solid(RE), glow=4)
    gem(i, RE, 0, 0, 0.6)
@icon("power_punch", PO)
def _(i): g.hand(i, PO, -6, 4); [i.stroke(lambda px, py, r=r: d_arc(px, py, 0, 0, r, -0.9, 0.9), 1.6, solid(0xE0C0FF), glow=4) for r in (14, 21)]
@icon("power_blast", PO)
def _(i): g.beam(i, PO, 3.6); g.orb(i, 6, 0xFFFFFF, PO, -16, 13)
@icon("power_nova", PO)
def _(i): g.orb(i, 9, 0xFFFFFF, PO); g.rays(i, 0xE0C0FF, 12, 13, 24, 1.6)
@icon("time_rewind", TI)
def _(i): g.ring(i, 17, TI, 1.6); i.stroke(lambda px, py: d_segment(px, py, 0, 0, 0, -11), 2, solid(0xFFFFFF)); i.stroke(lambda px, py: d_segment(px, py, 0, 0, -8, 4), 2, solid(0xFFFFFF)); g.arrow(i, TI, (14, 14), (22, 4))
@icon("time_age", TI)
def _(i): g.beam(i, TI, 2.0); [i.stroke(lambda px, py, x=x: d_segment(px, py, x, 14, x + 3, 22), 1.2, solid(0x406040)) for x in (-12, -4, 4)]
@icon("time_freeze", TI)
def _(i): g.ring(i, 20, TI, 1.4); g.flake(i, 0xC0FFD0, 13)
@icon("soul_siphon", SO)
def _(i): g.helix_icon(i, SO) if hasattr(g, "helix_icon") else g.spiral(i, 0xFFE0A0, SO, 1.5, 20); gem(i, SO, 0, 0, 0.45)
@icon("soul_guard", SO)
def _(i): g.shield(i, SO, 0, 0, 0.9); g.flame(i, 0xFFE0A0, SO, 0.4, 0, 2)
@icon("soul_harvest", SO)
def _(i): [g.flame(i, 0xFFE0A0, SO, 0.4, x, 6) for x in (-14, 0, 14)]; g.ring(i, 22, SO, 1.0)
@icon("annihilation", 0xC0206A)
def _(i): g.beam(i, 0xC0206A, 3.4); gem(i, RE, -14, 12, 0.4); gem(i, PO, 14, -12, 0.4)
@icon("chrono_jump", TI)
def _(i): g.arrow(i, SP, (-18, 12), (18, -12)); g.ring(i, 9, TI, 1.2, -12, 8)
@icon("snap", 0xFFE27A)
def _(i):
    for k, c in enumerate((SP, MI, RE, PO, TI, SO)):
        a = -math.pi / 2 + k * math.pi / 3
        gem(i, c, 15 * math.cos(a), 15 * math.sin(a), 0.32)
    g.orb(i, 6, 0xFFFFFF, 0xFFE27A)
@icon("time_dilation", 0x2EE0C0)
def _(i): g.ring(i, 20, 0x2EE0C0, 1.2); [i.stroke(lambda px, py, x=x: d_segment(px, py, x, -4, x + 6, -4), 1.2, solid(0xFFFFFF)) for x in (-16, -6, 4)]
@icon("overclock", 0x2EE0C0)
def _(i): g.ring(i, 16, 0x2EE0C0, 1.6); i.stroke(lambda px, py: d_segment(px, py, 0, 0, 10, -8), 2.2, solid(0xFFFFFF)); g.rays(i, 0x2EE0C0, 8, 18, 23, 1.0)
@icon("rewind_step", 0x2EE0C0)
def _(i): g.arrow(i, 0x2EE0C0, (18, 0), (-18, 0)); [i.stroke(lambda px, py, x=x: d_segment(px, py, x, -12, x, 12), 1.0, solid(0x80FFE0), glow=2) for x in (6, 14)]
@icon("stasis", 0x2EE0C0)
def _(i): i.stroke(lambda px, py: d_polyline(px, py, [(-12, -18), (12, -18), (-12, 18), (12, 18), (-12, -18)]), 1.8, solid(0x2EE0C0), glow=4); g.orb(i, 4, 0xFFFFFF, 0x2EE0C0)

for name, (accent, fn) in I.items():
    ic = Icon(accent)
    fn(ic)
    ic.save(name)
print(len(I), "cosmic icons")
