"""Builds docs/PRESENTATION.md from the source: per ability, the impact styles, sounds, residue and
environment effects its code actually uses. Run: python3 tools/presentation_sheet.py"""
import re, glob, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
src = {}
for f in glob.glob(os.path.join(ROOT, "src/Abilities/**/*.cs"), recursive=True):
    src[f] = open(f).read()

# class name -> (body, file)
classes = {}
for f, s in src.items():
    for m in re.finditer(r"public (?:abstract )?class (\w+)\s*:\s*(\w+)", s):
        start = m.start()
        nxt = re.search(r"\n(?:public|internal) (?:abstract |static )?class ", s[m.end():])
        body = s[start: m.end() + (nxt.start() if nxt else len(s))]
        classes[m.group(1)] = (body, m.group(2))

def chain(cls):
    out = []
    while cls in classes:
        out.append(classes[cls][0]); cls = classes[cls][1]
    return out

def grab(pat, text):
    return sorted(set(re.findall(pat, text)))

desc = dict(re.findall(r'\["([a-z_0-9]+)"\]\s*=\s*"((?:[^"\\]|\\.)*)"', open(os.path.join(ROOT, "src/UI/Index/AbilityDescriptions.cs")).read()))

FAMILY = {
    "Power": ("Thanos / Power", "purple", "heavy slams, flattened shock rings, cracks, flying debris", "boom, rise, thud, crackle, gong over a rumble"),
    "Space": ("Thanos / Space", "blue", "implosions, folding tears, rifts", "whoosh, warp, rise, shimmer, gong over a hum"),
    "Reality": ("Thanos / Reality", "red", "glitch bars, glass, ghost rings", "warp, glass, crackle, zap, gong over a rumble"),
    "Soul": ("Thanos / Soul", "orange", "threads, rising wisps, dark pulses", "heartbeat, hum, whoosh, creak, choir over a shimmer"),
    "Time": ("Thanos / Time", "green", "counter-rotating clock rings that freeze and rewind", "tick, warp, chime, hum, bell over ticking"),
    "Mind": ("Thanos / Mind", "yellow", "concentric pulses, hypnotic spirals", "shimmer, heartbeat, warp, chime, choir over a hum"),
    "Infinity": ("Thanos / Infinity", "all six stones", "six-colour rings, stone energy into the gauntlet", "rise, snap, gong over a choir"),
    "Illusion Magic": ("Loki / Illusions", "green + gold", "duplicates unfolding from gold seams, rolling reveals", "shimmer, chime, zap, warp, choir"),
    "Shapeshifting": ("Loki / Shapeshift", "green + gold", "smoke curtain, sigil snap, mirror-flash reveal", "warp, whoosh, creak, shimmer, gong"),
    "Trickster Mobility": ("Loki / Trickster", "green + gold", "folds away, smoke bombs, decoys", "whoosh, warp, shimmer, tick, rise"),
    "Sorcery & Combat": ("Loki / Sorcery", "green + gold", "spinning daggers, ribbons, sigil shield", "zap, creak, hum, slash, gong"),
    "God of Mischief": ("Loki / Mischief", "green + purple", "fake events only the victim sees", "thud, heartbeat, shimmer, warp, choir"),
    "Executions": ("Loki / Finishers", "green + gold", "camera-directed cinematics, no gore", "hum, slash, glass, creak, choir"),
    "Mind_Loki": ("Loki / Mind", "none (silent)", "deliberately no effects: possession must be undetectable", "none"),
    "Possessing": ("Loki / Possessing", "none (silent)", "deliberately no effects while possessing", "none"),
    "Sling Ring": ("Strange / Sling Ring", "orange-gold", "portals with depth, rim sparks, distortion ripples", "whoosh, shimmer, warp, crackle, gong"),
    "Mystic Combat": ("Strange / Combat", "crimson + gold", "bands, whip lash, orbiting blades", "crackle, slash, hum, shimmer, gong"),
    "Mirror Dimension": ("Strange / Mirror", "pale blue", "kaleidoscope cracks, shattering panes", "glass, warp, shimmer, gong"),
    "Time Stone": ("Strange / Time", "green", "clock runes, slow ticking bubbles", "tick, warp, chime, bell"),
    "Astral": ("Strange / Astral", "pale gold", "souls leaving bodies, rising light", "hum, shimmer, heartbeat, whoosh, choir"),
    "Chaos Magic": ("Scarlet Witch", "scarlet", "wavy tendrils (never straight beams), hex sigils, floating debris", "sizzle, reversed swells, glass, heartbeat, stabs"),
    "Wanda Finishers": ("Scarlet Witch / Finishers", "scarlet", "lifts, sigils, collapsing illusions, folding worlds", "reverse, choir, creak, glass, stab"),
    "Thunder": ("Thor", "blue-white", "branching bolts from the sky, hammer arcs, scorch marks", "clang, crackle, thud, whoosh, thunder"),
    "Thor Finishers": ("Thor / Finishers", "blue-white + rainbow", "storm clouds, tightening strikes, lightning columns", "clang, gong, crackle, rumble, thunder"),
    "Team-Up": ("Team-ups", "scarlet + blue-white = purple", "red threads and blue bolts colliding into purple bursts", "thunder, glass, stab over reversed swells"),
}

lines = ["# MarvelMod ability presentation sheet", "",
         "Generated from the source by `tools/presentation_sheet.py`. Each row lists what the ability's code actually uses:",
         "impact styles (`Fx.Release`), extra shapes, residue (`Fx.Aftermath`) and sounds. The activation sound comes from",
         "`SoundBook` (a different main sound per slot on each page, plus the page's texture layer); the activation motif",
         "comes from `Fx.Motif` (one shape per family).", ""]

chars = [("Loki", "src/Abilities/Loki/LokiCharacter.cs"), ("Thanos", "src/Characters/ThanosCharacter.cs"),
         ("Doctor Strange", "src/Abilities/Strange/StrangeCharacter.cs"), ("Scarlet Witch", "src/Characters/WandaCharacter.cs"),
         ("Thor", "src/Characters/ThorCharacter.cs")]

seen = set()
sigs = {}
lines.append("## Families")
lines.append("")
lines.append("| Family | Palette | Visual language | Sound identity |")
lines.append("|---|---|---|---|")
for k, (name, pal, vis, snd) in FAMILY.items():
    lines.append(f"| {name} | {pal} | {vis} | {snd} |")
lines.append("")
lines.append("## Abilities")
lines.append("")
lines.append("| Ability | Family | Impact styles | Shapes and environment | Residue | Sounds in code | Description |")
lines.append("|---|---|---|---|---|---|---|")
for cls, (body, base) in sorted(classes.items()):
    m = re.search(r'Id\s*=>\s*"([a-z_0-9]+)"', body)
    if not m or not re.search(r"Name\s*=>", body): continue
    aid = m.group(1)
    if aid in seen or aid in ("loki", "thanos", "strange"): continue
    seen.add(aid)
    full = "\n".join(chain(cls))
    cat = re.search(r'Category\s*=>\s*"([^"]+)"', full)
    stone = re.search(r'Stone\s*=>\s*Stone\.(\w+)', body)
    family = stone.group(1) if stone else (cat.group(1) if cat else "?")
    if family == "Mind" and not stone: family = "Mind_Loki"
    if "CharacterVisuals.Set" in body: pass
    if family in ("Hidden", "Meeting"): continue
    styles = grab(r"ImpactStyle\.(\w+)", body)
    residue = grab(r"Residue\.(\w+)", body)
    sounds = grab(r"SfxKind\.(\w+)", body)
    shapes = [s for s in ("Lightning", "StrikeFromSky", "Tendril", "Bind", "Sigil", "Levitate", "Darken", "Cracks", "Debris",
                          "Wave", "ShockRing", "Streaks", "Comic", "Trail", "StoneToGauntlet", "Charge", "Fragments", "Clouds", "Beam", "Projectile", "Scorch",
                          "Rising", "Aura", "Sparkles", "Smoke", "Flare", "WorldText", "Toast", "ScreenTint", "Flash")
              if re.search(r"\b" + s + r"\(", body)]
    if "CharacterVisuals.Set" in body: shapes.append("body pose (CharacterVisuals)")
    name = re.search(r'Name\s*=>\s*"([^"]+)"', body).group(1)
    shapes += ["preset " + v for v in grab(r"VfxType\.(\w+)", body)]
    d = desc.get(aid, "").replace("|", "/")
    sigs.setdefault((tuple(styles), tuple(shapes), tuple(residue), tuple(sounds)), []).append(f"{name} ({FAMILY.get(family, (family,))[0]})")
    lines.append(f"| **{name}** `{aid}` | {FAMILY.get(family, (family,))[0]} | {', '.join(styles) or '-'} | {', '.join(shapes) or '-'} | {', '.join(residue) or '-'} | {', '.join(sounds) or '(activation only)'} | {d} |")

lines += ["", "## Repetition audit", "",
          "Abilities whose code uses exactly the same set of impact styles, shapes, residue and extra sounds. Before the",
          "overhaul every ability shared one cast flash and each Thanos page shared one sound; now each one also has its own",
          "activation sound (SoundBook) and family motif, so these pairs differ at least there. Silent possession actions",
          "are identical on purpose (nobody may notice a possession).", ""]
dups = [v for v in sigs.values() if len(v) > 1]
if not dups: lines.append("None.")
for v in dups: lines.append("- " + ", ".join(v))
os.makedirs(os.path.join(ROOT, "docs"), exist_ok=True)
open(os.path.join(ROOT, "docs/PRESENTATION.md"), "w").write("\n".join(lines) + "\n")
print("rows:", len(seen))
