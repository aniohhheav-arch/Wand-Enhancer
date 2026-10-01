#!/usr/bin/env python3
"""Cross-checks Java registrations against the resource tree. Exits non-zero on any missing or dangling reference."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src" / "main" / "java" / "dev" / "riftverse"
RES = ROOT / "src" / "main" / "resources"
ASSETS = RES / "assets" / "riftverse"
DATA = RES / "data" / "riftverse"

problems = []


def problem(msg):
    problems.append(msg)


def java(path):
    return (JAVA / path).read_text(encoding="utf-8")


def names(src, pattern):
    return re.findall(pattern, src)


# ------------------------------------------------------------------------------------------------ registrations
blocks_src = java("registry/RvBlocks.java")
blocks = names(blocks_src, r'(?:BLOCKS\.register|item|simple)\("([a-z_]+)"')
block_items = [b for b in blocks if b not in ("rift", "portal_field")]
items_src = java("registry/RvItems.java")
items = names(items_src, r'(?:add|armor|egg)\("([a-z_]+)"')
entities = names(java("registry/RvEntities.java"), r'ENTITIES\.register\("([a-z_]+)"')
sound_names = names(java("registry/RvSounds.java"), r'(?:reg|far)\("([a-z_.]+)"')
particles = names(java("registry/RvParticles.java"), r'register\("([a-z_]+)"')
archetypes = names(java("universe/Archetype.java"), r'^\s+[A-Z_]+\("([a-z_]+)"', ) or \
    re.findall(r'\n    [A-Z_]+\("([a-z_]+)"', java("universe/Archetype.java"))

if not blocks or not items or not entities or not sound_names or not particles or not archetypes:
    problem("could not parse registrations")


# ------------------------------------------------------------------------------------------------ models / textures
def resolve_texture(ref):
    ns, path = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    return ns, RES / "assets" / ns / "textures" / f"{path}.png"


def check_model(ref, seen):
    ns, path = ref.split(":", 1)
    if ns != "riftverse" or ref in seen:
        return
    seen.add(ref)
    f = RES / "assets" / ns / "models" / f"{path}.json"
    if not f.exists():
        problem(f"missing model {ref}")
        return
    m = json.loads(f.read_text())
    textures = m.get("textures", {})
    for key, val in textures.items():
        if val.startswith("#"):
            continue
        tns, tf = resolve_texture(val)
        if tns == "riftverse" and not tf.exists():
            problem(f"model {ref}: missing texture {val}")
    for el in m.get("elements", []):
        for d, face in el.get("faces", {}).items():
            var = face["texture"].lstrip("#")
            if var not in textures:
                problem(f"model {ref}: face {d} uses undefined #{var}")
    if "parent" in m:
        check_model(m["parent"], seen)


seen = set()
for b in blocks:
    f = ASSETS / "blockstates" / f"{b}.json"
    if not f.exists():
        problem(f"missing blockstate {b}")
        continue
    state = json.loads(f.read_text())
    for variant in state.get("variants", {}).values():
        for v in (variant if isinstance(variant, list) else [variant]):
            check_model(v["model"], seen)
for it in items + block_items:
    check_model(f"riftverse:item/{it}", seen)

# ------------------------------------------------------------------------------------------------ language
lang = json.loads((ASSETS / "lang" / "en_us.json").read_text())
for b in blocks:
    if f"block.riftverse.{b}" not in lang:
        problem(f"lang: block.riftverse.{b}")
for it in items:
    if f"item.riftverse.{it}" not in lang:
        problem(f"lang: item.riftverse.{it}")
for e in entities:
    if f"entity.riftverse.{e}" not in lang:
        problem(f"lang: entity.riftverse.{e}")
for k in ("itemGroup.riftverse", "key.riftverse.ability", "key.categories.riftverse"):
    if k not in lang:
        problem(f"lang: {k}")
all_java = "\n".join(p.read_text(encoding="utf-8") for p in JAVA.rglob("*.java"))
for key in set(re.findall(r'translatable\("([a-zA-Z0-9_.]+)"\)', all_java)) | set(re.findall(r'"((?:message|screen|item|key)\.riftverse\.[a-z0-9_.]+)"', all_java)):
    if key.endswith("."):
        continue
    if key not in lang and not any(k.startswith(key) for k in lang):
        problem(f"lang: {key} (used in code)")
for prefix in ("screen.riftverse.multiverse.tab.", "screen.riftverse.multiverse.category.", "item.riftverse.reality_shaper.mode"):
    if not any(k.startswith(prefix) for k in lang):
        problem(f"lang: nothing for prefix {prefix}")
for lore in names(items_src, r'LoreItem\([^"]*"([a-z_]+)"\)'):
    if f"item.riftverse.{lore}.lore" not in lang:
        problem(f"lang: item.riftverse.{lore}.lore")

# ------------------------------------------------------------------------------------------------ sounds / particles
sounds_json = json.loads((ASSETS / "sounds.json").read_text())
if set(sounds_json) != set(sound_names):
    problem(f"sounds.json mismatch: {set(sounds_json) ^ set(sound_names)}")
for event, spec in sounds_json.items():
    for s in spec["sounds"]:
        name = s["name"] if isinstance(s, dict) else s
        if not (ASSETS / "sounds" / f"{name.split(':', 1)[1]}.ogg").exists():
            problem(f"missing sound file for {event}")
    if spec.get("subtitle") and spec["subtitle"] not in lang:
        problem(f"lang: {spec['subtitle']}")
for p in particles:
    f = ASSETS / "particles" / f"{p}.json"
    if not f.exists():
        problem(f"missing particle json {p}")
        continue
    for t in json.loads(f.read_text())["textures"]:
        if not (ASSETS / "textures" / "particle" / f"{t.split(':', 1)[1]}.png").exists():
            problem(f"particle {p}: missing texture {t}")

# ------------------------------------------------------------------------------------------------ misc textures
for path in set(re.findall(r'Riftverse\.id\("(textures/[a-z_/]+\.png)"\)', all_java)):
    if not (ASSETS / path).exists():
        problem(f"missing {path}")
for mat in names(java("registry/RvArmorMaterials.java"), r'register\("([a-z_]+)"'):
    for layer in (1, 2):
        if not (ASSETS / "textures" / "models" / "armor" / f"{mat}_layer_{layer}.png").exists():
            problem(f"missing armor layer {mat}_layer_{layer}")

# ------------------------------------------------------------------------------------------------ data
for b in block_items:
    if not (DATA / "loot_table" / "blocks" / f"{b}.json").exists():
        problem(f"missing block loot {b}")
entities_src = java("registry/RvEntities.java")
living = [e for e in entities if f'{"".join(w.capitalize() for w in e.split("_"))}Entity.createAttributes()' in entities_src]
for e in living:
    if not (DATA / "loot_table" / "entities" / f"{e}.json").exists():
        problem(f"missing entity loot {e}")
for adv in set(re.findall(r'award\(\w+, "([a-z_]+)"\)', all_java)) - {"trigger"}:
    if not (DATA / "advancement" / f"{adv}.json").exists():
        problem(f"missing advancement {adv}")
for a in archetypes + ["nexus"]:
    if not (DATA / "worldgen" / "biome" / f"{a}.json").exists():
        problem(f"missing biome {a}")
for key in set(re.findall(r'Riftverse\.id\("(chests/[a-z_]+)"\)', all_java)):
    if not (DATA / "loot_table" / f"{key}.json").exists():
        problem(f"missing loot table {key}")
for f in DATA.rglob("recipe/*.json"):
    r = json.loads(f.read_text())
    refs = [v["item"] for v in r.get("key", {}).values()] + [i["item"] for i in r.get("ingredients", [])] + [r["result"]["id"]]
    for ref in refs:
        ns, name = ref.split(":")
        if ns == "riftverse" and name not in items and name not in block_items:
            problem(f"recipe {f.name}: unknown item {ref}")

print(f"blocks={len(blocks)} items={len(items)} entities={len(entities)} sounds={len(sound_names)} particles={len(particles)} "
      f"archetypes={len(archetypes)} living={len(living)}")
if problems:
    print(f"{len(problems)} problem(s):")
    for p in problems:
        print("  -", p)
    sys.exit(1)
print("all resources consistent")
