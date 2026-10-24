#!/usr/bin/env python3
"""Generates every resource of the TELEPHONE ORDER mod into src/main/resources.  Run: python3 tools/gen_assets.py"""
import json, os, shutil, sys
from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import catalog_data as cd
import gen_art as art
import gen_blocks as gb
import gen_sounds as gs

ROOT = os.path.dirname(HERE)
RES = os.path.join(ROOT, "src", "main", "resources")
NS = "telephoneorder"
A = os.path.join(RES, "assets", NS)
D = os.path.join(RES, "data", NS)
CAT = os.path.join(RES, "telephoneorder_catalog")

# Item display transforms for gun sprites (muzzle pointing right in the texture).
GUN_DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 3.5, 1.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 3.5, 1.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.1, 3.0, 1.3], "scale": [1.0, 1.0, 1.0]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.1, 3.0, 1.3], "scale": [1.0, 1.0, 1.0]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
}

def wr(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    mode = "wb" if isinstance(data, bytes) else "w"
    with open(path, mode) as f:
        f.write(data if isinstance(data, bytes) else data)

def wj(path, obj):
    wr(path, json.dumps(obj, indent=1))

def save_png(img, rel):
    p = os.path.join(A, "textures", rel + ".png")
    os.makedirs(os.path.dirname(p), exist_ok=True)
    img.save(p)

def item_def(name, model):
    wj(os.path.join(A, "items", name + ".json"), {"model": {"type": "minecraft:model", "model": model}})

def flat_item(name, tex_rel, parent="minecraft:item/generated"):
    save_png(tex_rel[0], tex_rel[1]) if isinstance(tex_rel, tuple) else None

def simple_item(name, img, folder="misc"):
    save_png(img, f"item/{folder}/{name}")
    wj(os.path.join(A, "models", "item", folder, name + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{folder}/{name}"}})
    item_def(name, f"{NS}:item/{folder}/{name}")

# ------------------------------------------------------------------------------------------------
def gen_catalog():
    os.makedirs(CAT, exist_ok=True)
    guns = []
    for g in cd.GUNS:
        d = {k: v for k, v in g.items() if k not in ("vis",)}
        d["vis"] = {k: (list(v) if isinstance(v, tuple) else v) for k, v in g["vis"].items()}
        d["p"] = {k: (list(v) if isinstance(v, tuple) else v) for k, v in g["p"].items()}
        guns.append(d)
    wj(os.path.join(CAT, "guns.json"), guns)
    wj(os.path.join(CAT, "attachments.json"), cd.ATT)
    ammo = [dict(a, special=False) for a in cd.AMMO] + [dict(a, special=True) for a in cd.SPECIAL_AMMO]
    for a in ammo: a["color"] = list(a["color"])
    wj(os.path.join(CAT, "ammo.json"), ammo)
    wj(os.path.join(CAT, "meta.json"), {"tiers": cd.TIERS, "thresholds": cd.TIER_THRESHOLDS, "fees": cd.DELIVERY_FEE})

def gen_guns():
    att_index = {a["id"]: i for i, a in enumerate(cd.ATT)}
    # shared base model
    wj(os.path.join(A, "models", "item", "gun_base.json"), {"parent": "minecraft:item/generated", "display": GUN_DISPLAY})
    for g in cd.GUNS:
        frames, anchors = art.build_gun_frames(g)
        for name, img in frames.items():
            save_png(img, f"item/gun/{g['id']}_{name}")
            wj(os.path.join(A, "models", "item", "gun", f"{g['id']}_{name}.json"),
               {"parent": f"{NS}:item/gun_base", "textures": {"layer0": f"{NS}:item/gun/{g['id']}_{name}"}})
        overlays = []
        for a in cd.ATT:
            if a["slot"] == "finish" or a["slot"] not in g["slots"] or g["fam"] not in a["fits"]:
                continue
            ov = art.att_overlay(a["id"], anchors, g)
            save_png(ov, f"item/gun/{g['id']}_att_{a['id']}")
            wj(os.path.join(A, "models", "item", "gun", f"{g['id']}_att_{a['id']}.json"),
               {"parent": f"{NS}:item/gun_base", "textures": {"layer0": f"{NS}:item/gun/{g['id']}_att_{a['id']}"}})
            overlays.append(a["id"])

        def mdl(n): return {"type": "minecraft:model", "model": f"{NS}:item/gun/{g['id']}_{n}"}
        def comp(suffix):
            frames_model = {
                "type": "minecraft:range_dispatch", "property": "minecraft:custom_model_data", "index": 0,
                "entries": [{"threshold": 0.5, "model": mdl("reload1" + suffix)}, {"threshold": 1.5, "model": mdl("reload2" + suffix)}],
                "fallback": {
                    "type": "minecraft:range_dispatch", "property": "minecraft:cooldown",
                    "entries": [{"threshold": 0.72, "model": mdl("fire" + suffix)}],
                    "fallback": mdl("base" + suffix)}}
            models = [frames_model]
            for aid in overlays:
                models.append({"type": "minecraft:condition", "property": "minecraft:custom_model_data", "index": att_index[aid],
                               "on_true": {"type": "minecraft:model", "model": f"{NS}:item/gun/{g['id']}_att_{aid}"},
                               "on_false": {"type": "minecraft:empty"}})
            return {"type": "minecraft:composite", "models": models}
        sel = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
               "cases": [{"when": f, "model": comp("_" + f)} for f in ("gold", "arctic", "crimson")], "fallback": comp("")}
        wj(os.path.join(A, "items", g["id"] + ".json"), {"model": sel})

def gen_items():
    for a in cd.ATT:
        simple_item(a["id"], art.att_icon(a), "att")
    for a in cd.AMMO:
        simple_item(a["id"], art.ammo_icon(a), "ammo")
    for a in cd.SPECIAL_AMMO:
        simple_item(a["id"], art.ammo_icon(a, special=True), "ammo")
    for name, img in art.misc_icons().items():
        if name.startswith("proj_"):
            simple_item(name, img, "proj")
        else:
            simple_item(name, img, "misc")

def gen_blocks():
    for key, meta in gb.PHONES.items():
        sheet = gb.phone_sheet(key)
        save_png(sheet.img, f"block/phone_{key}")
        for st, m in gb.phone_models(key).items():
            wj(os.path.join(A, "models", "block", f"phone_{key}_{st}.json"), m)
        variants = {}
        for face, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
            for st in ("idle", "ring_a", "ring_b", "off_hook", "dial_a", "dial_b", "dial_c"):
                v = {"model": f"{NS}:block/phone_{key}_{st}"}
                if y: v["y"] = y
                variants[f"facing={face},phone={st}"] = v
        wj(os.path.join(A, "blockstates", f"telephone_{key}.json"), {"variants": variants})
        item_def(f"telephone_{key}", f"{NS}:block/phone_{key}_idle")
    save_png(gb.terminal_screen_strip(), "block/terminal_screen")
    wr(os.path.join(A, "textures", "block", "terminal_screen.png.mcmeta"), json.dumps({"animation": {"frametime": 2, "interpolate": True}}))
    img, m = gb.workbench()
    save_png(img, "block/weapons_workbench")
    wj(os.path.join(A, "models", "block", "weapons_workbench.json"), m)
    wj(os.path.join(A, "blockstates", "weapons_workbench.json"), {"variants": {"": {"model": f"{NS}:block/weapons_workbench"}}})
    item_def("weapons_workbench", f"{NS}:block/weapons_workbench")
    img, m = gb.sentry()
    save_png(img, "block/sentinel_turret")
    wj(os.path.join(A, "models", "block", "sentinel_turret.json"), m)
    variants = {}
    for face, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v = {"model": f"{NS}:block/sentinel_turret"}
        if y: v["y"] = y
        variants[f"facing={face}"] = v
    wj(os.path.join(A, "blockstates", "sentinel_turret.json"), {"variants": variants})
    item_def("sentinel_turret", f"{NS}:block/sentinel_turret")

def gen_props():
    def put(name, tex, mdl):
        save_png(tex, f"item/prop/{name}")
        wj(os.path.join(A, "models", "item", "prop", name + ".json"), mdl)
    def put_model(name, mdl):
        wj(os.path.join(A, "models", "item", "prop", name + ".json"), mdl)
    tex, closed, opened = gb.prop_crate()
    save_png(tex, "item/prop/delivery_crate")
    put_model("delivery_crate", closed); put_model("delivery_crate_open", opened)
    item_def("prop_crate", f"{NS}:item/prop/delivery_crate"); item_def("prop_crate_open", f"{NS}:item/prop/delivery_crate_open")
    tex, m, tex2, rot = gb.prop_drone()
    save_png(tex, "item/prop/delivery_drone"); save_png(tex2, "item/prop/delivery_rotor")
    put_model("delivery_drone", m); put_model("delivery_rotor", rot)
    item_def("prop_drone", f"{NS}:item/prop/delivery_drone"); item_def("prop_rotor", f"{NS}:item/prop/delivery_rotor")
    tex, m = gb.prop_pod(False); tex2, m2 = gb.prop_pod(True)
    save_png(tex, "item/prop/delivery_pod"); put_model("delivery_pod", m); put_model("delivery_pod_open", m2)
    item_def("prop_pod", f"{NS}:item/prop/delivery_pod"); item_def("prop_pod_open", f"{NS}:item/prop/delivery_pod_open")
    tex, m = gb.prop_container(False); tex2, m2 = gb.prop_container(True)
    save_png(tex, "item/prop/delivery_container"); put_model("delivery_container", m); put_model("delivery_container_open", m2)
    item_def("prop_container", f"{NS}:item/prop/delivery_container"); item_def("prop_container_open", f"{NS}:item/prop/delivery_container_open")
    tex, m = gb.prop_case(False); tex2, m2 = gb.prop_case(True)
    save_png(tex, "item/prop/delivery_case"); put_model("delivery_case", m); put_model("delivery_case_open", m2)
    item_def("prop_case", f"{NS}:item/prop/delivery_case"); item_def("prop_case_open", f"{NS}:item/prop/delivery_case_open")

def gen_sounds():
    odir = os.path.join(A, "sounds")
    names = gs.write_all(odir)
    sounds = {}
    subtitles = {}
    for n in names:
        sounds[n] = {"sounds": [f"{NS}:{n}"], "subtitle": f"subtitles.{NS}.{n}"}
        subtitles[f"subtitles.{NS}.{n}"] = n.replace("_", " ").capitalize()
    wj(os.path.join(A, "sounds.json"), sounds)
    wj(os.path.join(CAT, "sounds.json"), names)
    return subtitles

def gen_data():
    for n in ("bullet", "energy", "armor_piercing", "explosion_gun", "emp", "turret", "paradox_echo"):
        wj(os.path.join(D, "damage_type", n + ".json"), {"exhaustion": 0.0, "message_id": f"{NS}.{n}", "scaling": "never"})
    wj(os.path.join(RES, "data", "minecraft", "tags", "damage_type", "bypasses_armor.json"), {"values": [f"{NS}:armor_piercing", f"{NS}:emp"]})
    allk = [f"{NS}:{n}" for n in ("bullet", "energy", "armor_piercing", "explosion_gun", "emp", "turret", "paradox_echo")]
    wj(os.path.join(RES, "data", "minecraft", "tags", "damage_type", "bypasses_cooldown.json"), {"values": allk})
    wj(os.path.join(RES, "data", "minecraft", "tags", "damage_type", "is_projectile.json"), {"values": [f"{NS}:bullet", f"{NS}:turret", f"{NS}:armor_piercing"]})
    wj(os.path.join(RES, "data", "minecraft", "tags", "damage_type", "is_explosion.json"), {"values": [f"{NS}:explosion_gun"]})
    # recipes
    def shaped(name, pattern, key, result, count=1, cat="equipment"):
        wj(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shaped", "category": cat, "pattern": pattern, "key": key, "result": {"id": f"{NS}:{result}", "count": count}})
    def shapeless(name, ings, result, count=1):
        wj(os.path.join(D, "recipe", name + ".json"), {"type": "minecraft:crafting_shapeless", "category": "equipment", "ingredients": ings, "result": {"id": f"{NS}:{result}", "count": count}})
    shaped("telephone_rotary", ["ICI", "RBR", "III"], {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot", "R": "minecraft:redstone", "B": "minecraft:bell"}, "telephone_rotary", cat="misc")
    shaped("telephone_terminal", ["IGI", "RCR", "III"], {"I": "minecraft:iron_ingot", "G": "minecraft:glass_pane", "R": "minecraft:redstone", "C": "minecraft:comparator"}, "telephone_terminal", cat="misc")
    shaped("telephone_uplink", ["IRI", "LCL", "IEI"], {"I": "minecraft:iron_ingot", "R": "minecraft:lightning_rod", "L": "minecraft:lapis_lazuli", "C": "minecraft:comparator", "E": "minecraft:ender_pearl"}, "telephone_uplink", cat="misc")
    shaped("weapons_workbench", ["III", "TCT", "P P"], {"I": "minecraft:iron_ingot", "T": "minecraft:smooth_stone", "C": "minecraft:crafting_table", "P": "minecraft:oak_planks"}, "weapons_workbench", cat="misc")
    shapeless("ammo_9mm", ["minecraft:copper_ingot", "minecraft:gunpowder"], "ammo_9mm")
    shapeless("ammo_357", ["minecraft:iron_ingot", "minecraft:gunpowder", "minecraft:copper_ingot"], "ammo_357")
    shapeless("ammo_556", ["minecraft:iron_ingot", "minecraft:gunpowder", "minecraft:gunpowder"], "ammo_556")
    shapeless("ammo_762", ["minecraft:iron_ingot", "minecraft:gunpowder", "minecraft:gold_nugget"], "ammo_762")
    shapeless("ammo_12g", ["minecraft:paper", "minecraft:gunpowder", "minecraft:iron_nugget"], "ammo_12g")
    shapeless("ammo_50bmg", ["minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:gunpowder", "minecraft:gunpowder"], "ammo_50bmg")
    shapeless("ammo_rocket", ["minecraft:firework_rocket", "minecraft:gunpowder", "minecraft:iron_ingot"], "ammo_rocket")
    shapeless("ammo_cell", ["minecraft:redstone", "minecraft:copper_ingot", "minecraft:glowstone_dust"], "ammo_cell")
    shapeless("ammo_core", ["minecraft:diamond", "minecraft:glowstone_dust", "minecraft:redstone_block"], "ammo_core")

def gen_lang(subtitles):
    lang = {}
    lang["itemGroup.telephoneorder.main"] = "Telephone Order"
    lang["key.category.telephoneorder.main"] = "Telephone Order"
    for k, t in (("reload", "Reload"), ("firemode", "Cycle Fire Mode"), ("aim", "Aim Down Sights"), ("inspect", "Inspect Weapon / Attachments")):
        lang[f"key.telephoneorder.{k}"] = t
    for g in cd.GUNS: lang[f"item.{NS}.{g['id']}"] = g["name"]
    for a in cd.ATT: lang[f"item.{NS}.{a['id']}"] = a["name"]
    for a in cd.AMMO + cd.SPECIAL_AMMO: lang[f"item.{NS}.{a['id']}"] = a["name"]
    for k, v in PHONE_NAMES.items(): lang[f"block.{NS}.telephone_{k}"] = v
    lang[f"block.{NS}.weapons_workbench"] = "Weapons Workbench"
    lang[f"block.{NS}.sentinel_turret"] = "Sentinel Turret"
    lang.update({f"item.{NS}.clue_slip": "Torn Order Slip", f"item.{NS}.order_receipt": "Order Receipt", f"item.{NS}.credit_chip": "Credit Chip"})
    for n in ("crate", "crate_open", "drone", "rotor", "pod", "pod_open", "container", "container_open", "case", "case_open"):
        lang[f"item.{NS}.prop_{n}"] = "Delivery Prop"
    for n in ("plasma", "shard", "charge", "heavy", "rift", "emp", "missile", "rocket"):
        lang[f"item.{NS}.proj_{n}"] = "Projectile"
        lang[f"entity.{NS}.gun_projectile"] = "Projectile"
    for n, t in (("bullet", "%1$s was shot by %2$s"), ("energy", "%1$s was vaporized by %2$s"), ("armor_piercing", "%1$s was punched through by %2$s"),
                 ("explosion_gun", "%1$s was blown up by %2$s"), ("emp", "%1$s was fried by %2$s's EMP"), ("turret", "%1$s was gunned down by a sentry"),
                 ("paradox_echo", "%1$s was hit by an echo from %2$s")):
        lang[f"death.attack.{NS}.{n}"] = t
        lang[f"death.attack.{NS}.{n}.player"] = t
    lang.update(subtitles)
    wj(os.path.join(A, "lang", "en_us.json"), lang)

PHONE_NAMES = {"rotary": "Rotary Desk Phone", "terminal": "Desk Terminal", "uplink": "Field Uplink"}

def gen_icon():
    img = Image.new("RGBA", (128, 128), (22, 24, 30, 255))
    d = ImageDraw.Draw(img)
    d.rectangle([4, 4, 123, 123], outline=(205, 160, 62, 255), width=3)
    g = art.build_gun_frames(cd.GUNS[0])[0]["base"].resize((96, 96), Image.NEAREST)
    img.paste(g, (16, 8), g)
    ph = gb.phone_sheet("rotary").img
    d.rectangle([20, 92, 108, 118], fill=(40, 42, 50, 255)); d.text((26, 98), "TELEPHONE ORDER", fill=(205, 160, 62, 255))
    wr_path = os.path.join(A, "icon.png")
    os.makedirs(os.path.dirname(wr_path), exist_ok=True)
    img.save(wr_path)

def main():
    if os.path.isdir(A): shutil.rmtree(A)
    if os.path.isdir(D): shutil.rmtree(D)
    if os.path.isdir(CAT): shutil.rmtree(CAT)
    gen_catalog()
    gen_guns()
    gen_items()
    gen_blocks()
    gen_props()
    subs = gen_sounds()
    gen_data()
    gen_lang(subs)
    gen_icon()
    print("assets generated:", sum(len(f) for _, _, f in os.walk(RES)), "files")

if __name__ == "__main__":
    main()
