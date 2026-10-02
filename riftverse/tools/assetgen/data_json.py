"""Server data: recipes, loot tables, tags, advancements and worldgen (dimensions, biomes, features, biome modifiers)."""
from .common import DATA, rl, write_json
from .lang import ADVANCEMENTS

NS = DATA / "riftverse"
MC = "minecraft"

BLOCK_ITEMS = ["rift_frame", "multiverse_console", "rift_altar", "gravity_lift", "nexus_stone", "nexus_bricks", "nexus_glow",
               "void_stone", "void_crystal", "rift_crystal", "neon_panel_cyan", "neon_panel_magenta", "cyber_plating", "cyber_glass",
               "alien_moss", "alien_soil", "glowcap", "lumen_log", "lumen_leaves", "skystone", "dream_turf", "dream_cloud",
               "dead_regolith", "ashen_rock", "cosmic_obsidian", "stardust_sand", "glitch_block", "ancient_bricks", "ancient_glyph",
               "ancient_gold", "abyssal_glow", "starmetal_block", "aurora_glass", "sculk_crystal", "magma_crust", "chrome_plating", "honey_crystal", "lunar_dust", "nebula_stone", "starwood_planks", "voidglass", "rune_tile", "ember_bricks", "frost_crystal", "coral_stone"]

PICKAXE = ["starmetal_block", "aurora_glass", "sculk_crystal", "magma_crust", "chrome_plating", "honey_crystal", "nebula_stone", "voidglass", "rune_tile", "ember_bricks", "frost_crystal", "coral_stone", "rift_frame", "multiverse_console", "gravity_lift", "nexus_stone", "nexus_bricks", "nexus_glow", "void_stone", "void_crystal",
           "rift_crystal", "neon_panel_cyan", "neon_panel_magenta", "cyber_plating", "skystone", "ashen_rock", "cosmic_obsidian",
           "glitch_block", "ancient_bricks", "ancient_glyph", "ancient_gold", "abyssal_glow"]
SHOVEL = ["lunar_dust", "alien_soil", "dead_regolith", "stardust_sand", "dream_turf"]
AXE = ["starwood_planks", "lumen_log"]
HOE = ["alien_moss", "lumen_leaves", "dream_cloud"]
NEEDS_IRON = ["gravity_lift", "cyber_plating", "ancient_gold"]
NEEDS_DIAMOND = ["rift_frame", "multiverse_console", "cosmic_obsidian"]


def item(name):
    return name if ":" in name else rl(name)


# ------------------------------------------------------------------------------------------------------------ recipes

def shaped(name, pattern, key, result=None, count=1, category="misc"):
    write_json(NS / "recipe" / f"{name}.json", {
        "type": "minecraft:crafting_shaped", "category": category, "pattern": pattern,
        "key": {k: {"item": item(v)} for k, v in key.items()},
        "result": {"id": item(result or name), "count": count},
    })


def shapeless(name, ingredients, result=None, count=1, category="misc"):
    write_json(NS / "recipe" / f"{name}.json", {
        "type": "minecraft:crafting_shapeless", "category": category,
        "ingredients": [{"item": item(i)} for i in ingredients],
        "result": {"id": item(result or name), "count": count},
    })


ARMOR_PATTERNS = {
    "helmet": ["XXX", "X X"],
    "chestplate": ["X X", "XCX", "XXX"],
    "leggings": ["XXX", "X X", "X X"],
    "boots": ["X X", "X X"],
}
ARMOR_MATERIAL = {"rift_walker": ("rift_shard", None), "voyager": ("exotic_ingot", None),
                  "event_horizon": ("singularity_fragment", "warden_core"), "astral": ("stellar_dust", "leviathan_scale")}


def recipes():
    shapeless("exotic_ingot", ["minecraft:iron_ingot", "minecraft:gold_ingot", "rift_shard", "stellar_dust"])
    shaped("rift_frame", ["OSO", "SOS", "OSO"], {"O": "minecraft:obsidian", "S": "rift_shard"}, count=4, category="building")
    shapeless("rift_igniter", ["minecraft:flint_and_steel", "rift_shard"], category="equipment")
    shaped("multiverse_console", ["GEG", "SDS", "XXX"], {"G": "minecraft:glass", "E": "minecraft:ender_eye", "S": "rift_shard",
                                                        "D": "minecraft:diamond", "X": "exotic_ingot"}, category="redstone")
    shaped("gravity_lift", ["ISI", "SRS", "III"], {"I": "minecraft:iron_ingot", "S": "stellar_dust", "R": "rift_crystal"},
           count=2, category="redstone")
    shaped("portal_gun", [" XE", "XRX", "IX "], {"X": "exotic_ingot", "E": "minecraft:ender_eye", "R": "rift_shard",
                                                 "I": "minecraft:iron_ingot"}, category="equipment")
    shaped("rift_blade", ["S", "X", "B"], {"S": "singularity_fragment", "X": "exotic_ingot", "B": "minecraft:blaze_rod"},
           category="equipment")
    shaped("gravity_gauntlet", ["VXV", "XSX", "XXX"], {"V": "void_essence", "X": "exotic_ingot", "S": "singularity_fragment"},
           category="equipment")
    shapeless("singularity_grenade", ["singularity_fragment", "minecraft:tnt", "void_essence"], count=2, category="equipment")
    shaped("singularity_core", ["FXF", "XNX", "FXF"], {"F": "singularity_fragment", "X": "exotic_ingot", "N": "minecraft:nether_star"})
    shaped("reality_shaper", ["  C", " X ", "B  "], {"C": "rift_crystal", "X": "exotic_ingot", "B": "minecraft:blaze_rod"},
           category="equipment")
    shaped("reality_remote", ["FWF", "XCX", "FLF"], {"F": "singularity_fragment", "W": "warden_core", "X": "exotic_ingot",
                                                    "C": "singularity_core", "L": "leviathan_scale"}, category="equipment")
    shaped("dimensional_key", ["RX", "X "], {"R": "rift_shard", "X": "minecraft:gold_ingot"})
    shaped("universe_compass", [" R ", "RCR", " R "], {"R": "rift_shard", "C": "minecraft:compass"}, category="equipment")
    shapeless("homeward_rift", ["rift_shard", "minecraft:ender_pearl", "stellar_dust"], count=2)
    shaped("rift_sigil", ["VFV", "FCF", "VFV"], {"V": "void_essence", "F": "singularity_fragment", "C": "rift_crystal"})
    for set_name, (mat, core) in ARMOR_MATERIAL.items():
        for piece, pattern in ARMOR_PATTERNS.items():
            key = {"X": mat}
            pat = list(pattern)
            if "C" in "".join(pat):
                if core:
                    key["C"] = core
                else:
                    pat = [row.replace("C", "X") for row in pat]
            shaped(f"{set_name}_{piece}", pat, key, category="equipment")
    shaped("nexus_bricks", ["SS", "SS"], {"S": "nexus_stone"}, count=4, category="building")
    shapeless("neon_panel_cyan", ["cyber_plating", "minecraft:glowstone_dust", "minecraft:cyan_dye"], count=2, category="building")
    shapeless("neon_panel_magenta", ["cyber_plating", "minecraft:glowstone_dust", "minecraft:magenta_dye"], count=2, category="building")
    shaped("cyber_plating", ["II", "II"], {"I": "minecraft:iron_ingot"}, count=4, category="building")
    shaped("cyber_glass", ["GGG", "GPG", "GGG"], {"G": "minecraft:glass", "P": "minecraft:prismarine_crystals"}, count=8,
           category="building")


# -------------------------------------------------------------------------------------------------------- loot tables

def count_fn(lo, hi, looting=True):
    fns = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}, "add": False}]
    if looting:
        fns.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                    "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    return fns


def entity_loot(name, drops):
    pools = []
    for drop in drops:
        it, lo, hi = drop[:3]
        chance = drop[3] if len(drop) > 3 else None
        pool = {"rolls": 1, "entries": [{"type": "minecraft:item", "name": item(it), "functions": count_fn(lo, hi)}]}
        if chance is not None:
            pool["conditions"] = [{"condition": "minecraft:random_chance_with_enchanted_bonus", "enchantment": "minecraft:looting",
                                   "unenchanted_chance": chance, "enchanted_chance": {"type": "minecraft:linear", "base": chance + 0.05,
                                                                                      "per_level_above_first": 0.05}}]
        pools.append(pool)
    write_json(NS / "loot_table" / "entities" / f"{name}.json",
               {"type": "minecraft:entity", "pools": pools, "random_sequence": rl(f"entities/{name}")})


def loot():
    for b in BLOCK_ITEMS:
        write_json(NS / "loot_table" / "blocks" / f"{b}.json", {
            "type": "minecraft:block",
            "pools": [{"rolls": 1, "bonus_rolls": 0, "entries": [{"type": "minecraft:item", "name": rl(b)}],
                       "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": rl(f"blocks/{b}"),
        })
    entity_loot("astral_jelly", [("stellar_dust", 0, 2)])
    entity_loot("sky_whale", [("stellar_dust", 1, 3), ("minecraft:prismarine_crystals", 0, 2)])
    entity_loot("lumen_strider", [("stellar_dust", 0, 1), ("minecraft:glow_berries", 0, 2)])
    entity_loot("neon_drone", [("minecraft:iron_nugget", 1, 3), ("minecraft:redstone", 0, 2), ("exotic_ingot", 1, 1, 0.2)])
    entity_loot("glitchling", [("rift_shard", 0, 1), ("void_essence", 0, 1)])
    entity_loot("void_stalker", [("void_essence", 1, 2), ("minecraft:ender_pearl", 0, 1)])
    entity_loot("crystal_sentinel", [("rift_shard", 1, 2), ("minecraft:amethyst_shard", 1, 3)])
    entity_loot("rift_wraith", [("void_essence", 0, 2), ("rift_shard", 0, 1), ("minecraft:phantom_membrane", 0, 1)])
    entity_loot("rift_warden", [("warden_core", 1, 1), ("singularity_fragment", 3, 5), ("exotic_ingot", 4, 8), ("rift_shard", 6, 10)])
    entity_loot("void_cultist", [("void_essence", 0, 2), ("rift_shard", 0, 1)])
    entity_loot("crystal_spider", [("minecraft:amethyst_shard", 1, 3), ("minecraft:string", 0, 2)])
    entity_loot("star_moth", [("stellar_dust", 0, 1)])
    entity_loot("lunar_golem", [("minecraft:iron_ingot", 2, 5), ("stellar_dust", 1, 3)])
    entity_loot("denizen", [("minecraft:emerald", 0, 2), ("rift_shard", 0, 1)])
    entity_loot("cosmic_deity", [("singularity_core", 1, 1), ("singularity_fragment", 6, 10), ("warden_core", 1, 2), ("stellar_dust", 16, 32)])
    entity_loot("abyssal_leviathan", [("leviathan_scale", 4, 6), ("singularity_fragment", 1, 3), ("stellar_dust", 8, 12)])

    def e(name, weight, lo=1, hi=1):
        entry = {"type": "minecraft:item", "name": item(name), "weight": weight}
        if hi > 1 or lo != 1:
            entry["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
        return entry

    write_json(NS / "loot_table" / "chests" / "rift_cache.json", {
        "type": "minecraft:chest",
        "pools": [
            {"rolls": {"type": "minecraft:uniform", "min": 3, "max": 6}, "entries": [
                e("rift_shard", 20, 1, 3), e("stellar_dust", 15, 2, 6), e("void_essence", 12, 1, 3), e("exotic_ingot", 8, 1, 2),
                e("singularity_fragment", 4), e("homeward_rift", 6), e("dimensional_key", 4), e("minecraft:ender_pearl", 10, 1, 3),
                e("minecraft:diamond", 5, 1, 2), e("minecraft:emerald", 8, 2, 5), e("minecraft:gold_ingot", 10, 2, 5),
                e("minecraft:experience_bottle", 8, 1, 4)]},
            {"rolls": {"type": "minecraft:uniform", "min": 0, "max": 1}, "entries": [
                e("rift_igniter", 6), e("universe_compass", 4), e("singularity_grenade", 4, 1, 2), e("rift_sigil", 1),
                e("portal_gun", 1), {"type": "minecraft:empty", "weight": 6}]},
        ],
        "random_sequence": rl("chests/rift_cache"),
    })


# --------------------------------------------------------------------------------------------------------------- tags

def tags():
    t = DATA / MC / "tags" / "block"
    write_json(t / "mineable" / "pickaxe.json", {"replace": False, "values": [rl(b) for b in PICKAXE]})
    write_json(t / "mineable" / "shovel.json", {"replace": False, "values": [rl(b) for b in SHOVEL]})
    write_json(t / "mineable" / "axe.json", {"replace": False, "values": [rl(b) for b in AXE]})
    write_json(t / "mineable" / "hoe.json", {"replace": False, "values": [rl(b) for b in HOE]})
    write_json(t / "needs_iron_tool.json", {"replace": False, "values": [rl(b) for b in NEEDS_IRON]})
    write_json(t / "needs_diamond_tool.json", {"replace": False, "values": [rl(b) for b in NEEDS_DIAMOND]})
    write_json(t / "dragon_immune.json", {"replace": False, "values": [rl("rift_altar"), rl("rift"), rl("portal_field")]})
    write_json(t / "wither_immune.json", {"replace": False, "values": [rl("rift_altar"), rl("rift"), rl("portal_field")]})


# ------------------------------------------------------------------------------------------------------- advancements

ADV_TREE = {
    # id: (parent, icon, frame)
    "root": (None, "rift_shard", "task"),
    "first_rift": ("root", "rift_igniter", "task"),
    "nexus": ("first_rift", "multiverse_console", "goal"),
    "manifest": ("nexus", "dimensional_key", "goal"),
    "dreamwalker": ("manifest", "dream_turf", "task"),
    "cartographer": ("first_rift", "universe_compass", "challenge"),
    "event_horizon": ("first_rift", "singularity_core", "challenge"),
    "warden_slayer": ("nexus", "warden_core", "challenge"),
    "leviathan_slayer": ("first_rift", "leviathan_scale", "challenge"),
}


def advancements():
    for adv, (parent, icon, frame) in ADV_TREE.items():
        display = {
            "icon": {"id": rl(icon)},
            "title": {"translate": f"advancements.riftverse.{adv}.title"},
            "description": {"translate": f"advancements.riftverse.{adv}.description"},
            "frame": frame,
            "show_toast": parent is not None,
            "announce_to_chat": parent is not None,
            "hidden": adv in ("dreamwalker",),
        }
        body = {"display": display}
        if parent is None:
            display["background"] = rl("textures/block/cosmic_obsidian.png")
            body["criteria"] = {"tick": {"trigger": "minecraft:tick"}}
        else:
            body["parent"] = rl(parent)
            body["criteria"] = {"trigger": {"trigger": "minecraft:impossible"}}
        write_json(NS / "advancement" / f"{adv}.json", body)
    assert set(ADV_TREE) == set(ADVANCEMENTS)


# ----------------------------------------------------------------------------------------------------------- worldgen

BIOMES = {
    # id: (temperature, sky, fog, water, water_fog, grass, foliage)
    "neon_sprawl": (0.7, 0x1A0F3A, 0x2A1450, 0x00C8D8, 0x08202A, 0x2E8A6A, 0x2E8A6A),
    "xenoflora": (0.9, 0x6AD8B0, 0x9FE8C8, 0x2ADFA0, 0x0A3A30, 0x5CFF9D, 0x3ADF80),
    "skyshatter": (0.6, 0x8AC8FF, 0xC8E8FF, 0x4AA8FF, 0x103050, 0x7ED87A, 0x6AC86A),
    "thalassic": (0.7, 0x5AA0FF, 0x9AC8FF, 0x1E64FF, 0x041A40, 0x4AC8A0, 0x3AB890),
    "prismatic": (0.5, 0xD8B8FF, 0xE8D8FF, 0xB070FF, 0x2A1050, 0xC890FF, 0xB070FF),
    "ashen": (1.2, 0x8A6A5A, 0x9A7A6A, 0x6A5A4A, 0x2A2018, 0x8A7A5A, 0x7A6A4A),
    "astral": (0.5, 0x1A1040, 0x2A1A5A, 0x6A4AFF, 0x100830, 0x8F6BFF, 0x7A5AE8),
    "corrupted": (0.6, 0x0A1A0A, 0x1A3A1A, 0x39FF14, 0x0A2A0A, 0x39FF14, 0x2AD810),
    "elder": (1.0, 0xFFD890, 0xFFE8B0, 0x3AB8C8, 0x0A3040, 0xC8B060, 0xA89040),
    "inverted": (0.6, 0x7AF0D0, 0xB0FFF0, 0x40E0D0, 0x0A3A38, 0x7AF0D0, 0x5AD8B8),
    "hollow": (0.4, 0x05030A, 0x0A0614, 0x3A1A6A, 0x050210, 0x3A2A5A, 0x2A1A4A),
    "somnium": (0.8, 0xFFC8F0, 0xFFE0F8, 0xFF9AE0, 0x4A1A40, 0xFFB3E6, 0xF0A0E0),
    "cinder": (2.0, 0x3A0A04, 0x6A1A08, 0xFF5A1F, 0x3A0A00, 0x6A3A1A, 0x5A2A10),
    "rime": (-0.5, 0xC8F0FF, 0xE0F8FF, 0x7AC8FF, 0x1A3A5A, 0xA8E8FF, 0x98D8F0),
    "sunscar": (2.0, 0x8AC8FF, 0xFFE0A0, 0x3AB8C8, 0x0A3040, 0xD8C070, 0xC8B060),
    "mesa": (1.8, 0x6A9AD8, 0xE0925A, 0x4A90B0, 0x0A2A3A, 0xA87040, 0x986030),
    "coral": (0.9, 0x6AC0FF, 0xB0F0FF, 0x30D8E0, 0x0A3A48, 0x5ADFA0, 0x4AD090),
    "mycelia": (0.7, 0x2A1A4A, 0x3A1A5A, 0x6A4AB0, 0x1A0A30, 0x9A70D0, 0x8A60C0),
    "clockwork": (0.8, 0x6A5A30, 0x8A6A30, 0x6A8A70, 0x1A2A20, 0xA89050, 0x988040),
    "sanguine": (1.4, 0x3A0A10, 0x5A0A14, 0x8A1020, 0x2A0408, 0x8A2A2A, 0x7A1A1A),
    "confection": (0.8, 0xFFC8E8, 0xFFE8F4, 0x9AD8FF, 0x2A3A5A, 0xFFB0E0, 0xFFA0D8),
    "tempest": (0.6, 0x4A6A95, 0x6A88B0, 0x3A70C0, 0x0A1A3A, 0x6AA880, 0x5A9870),
    "mire": (0.8, 0x5A6A3A, 0x6A7A44, 0x5A7A3A, 0x1A2A10, 0x6A8A30, 0x5A7A28),
    "obsidian": (0.5, 0x2A164A, 0x1A0E33, 0x4A2A8A, 0x0A0418, 0x4A3A6A, 0x3A2A5A),
    "verdigris": (0.8, 0x8AC8B0, 0xA8D8C4, 0x3AB8A0, 0x0A3A30, 0x6AC090, 0x5AB080),
    "radiance": (0.7, 0xB0D8FF, 0xFFF4D8, 0x7AD0FF, 0x1A3A5A, 0xC8E890, 0xB8D880),
    "ferrous": (1.0, 0x3A424A, 0x2A3036, 0x5A6A7A, 0x10181E, 0x7A8078, 0x6A7068),
    "bloom": (0.8, 0xFF9AE0, 0xE080C8, 0xC070FF, 0x2A1040, 0xFF90D0, 0xF080C0),
    "aurora": (0.0, 0x1A4A6A, 0x2A5A7A, 0x40FFB0, 0x0A2A3A, 0x60C0A0, 0x50A890),
    "magma": (1.0, 0x8A2000, 0x601800, 0xFF4A10, 0x3A0800, 0x8A5020, 0x704010),
    "primeval": (0.9, 0xC0E8A0, 0x80B070, 0x3ABF3A, 0x1A4A2A, 0x30C030, 0x28A828),
    "nebula": (0.5, 0x5A1A8A, 0x3A105A, 0xC050FF, 0x1A0A3A, 0xC070F0, 0xA060D0),
    "wasteland": (0.9, 0xA09060, 0x807850, 0x9A9A40, 0x3A3A20, 0x9A9A50, 0x808040),
    "geode": (0.5, 0x3A1A5A, 0x2A1040, 0xA060FF, 0x1A0A30, 0xA080D0, 0x9070C0),
    "deepdark": (0.5, 0x02141A, 0x020E12, 0x0A8A9A, 0x02101A, 0x205A5A, 0x1A4A4A),
    "savanna": (1.0, 0xFFE0A0, 0xE0C080, 0xFFB040, 0x3A6A8A, 0xC0B040, 0xA8A030),
    "chrome": (0.6, 0xF0F8FF, 0xD0E0F0, 0x80E0FF, 0x5A8AB0, 0xB0D0E0, 0xA0C0D0),
    "lunar": (0.0, 0x0A0A12, 0x08080C, 0xD8D8E8, 0x101018, 0x808088, 0x707078),
    "hive": (0.9, 0xFFE070, 0xF0C040, 0xFFC020, 0x8A6A10, 0xE0C040, 0xD0B030),
    "mirror": (0.6, 0xFFFFFF, 0xE0F0FF, 0xB0E0FF, 0x80B0E0, 0xC0E0F0, 0xB0D0E8),
    "starforge": (0.5, 0x3A2A5A, 0x2A2040, 0x9AC8FF, 0x1A1A3A, 0x6A6A8A, 0x5A5A7A),
    "frostglass": (0.0, 0x6AA0D0, 0x80B0E0, 0xE0FFFF, 0x3A6A9A, 0xA0D0E0, 0x90C0D0),
    "echo": (0.5, 0x02101A, 0x020A10, 0x30E0F0, 0x02101A, 0x205A5A, 0x1A4A4A),
    "moltensea": (1.0, 0x8A2A00, 0x6A1A00, 0xFF6A10, 0x3A0800, 0x8A5020, 0x704010),
    "cloudkingdom": (0.7, 0xE0F0FF, 0xF0F8FF, 0xFFFFFF, 0x80B0E0, 0xC0E0C0, 0xB0D0B0),
    "drowned": (0.7, 0x2A6A9A, 0x1A4A6A, 0x2A8AC0, 0x0A2A4A, 0x4A9A9A, 0x3A8A8A),
    "toxic": (0.9, 0x4A6A10, 0x3A5A10, 0x80FF20, 0x2A3A00, 0x6A8A20, 0x5A7A10),
    "crimsonweald": (1.0, 0x7A1020, 0x5A0A18, 0xD02040, 0x3A0010, 0x8A2030, 0x7A1828),
    "warpedweald": (0.5, 0x0A5A5A, 0x0A4040, 0x20D0B0, 0x002A2A, 0x20A080, 0x109070),
    "goldentemple": (1.0, 0xFFE0A0, 0xE0C080, 0xFFC14D, 0x3A6A8A, 0xC0B040, 0xA8A030),
    "rainbow": (0.8, 0xFFD0F0, 0xF0E0FF, 0xFF70C0, 0x6AB0FF, 0x80F080, 0x70E070),
    "voidglass": (0.5, 0x0A0418, 0x05020C, 0x5A30A0, 0x05020C, 0x302050, 0x281840),
    "runic": (0.5, 0x6A6AA0, 0x4A4A70, 0xFFC14D, 0x1A2A4A, 0x6A8A50, 0x5A7A40),
    "embersteppe": (1.0, 0xA04010, 0x7A3010, 0xFF6020, 0x3A0800, 0x8A5020, 0x704010),
    "pastel": (0.7, 0xFFE0F0, 0xFFF0F8, 0xFFC0E0, 0xC0E0FF, 0xF0C0E0, 0xE0B0D0),
    "petrified": (0.5, 0x4A2A6A, 0x3A2050, 0x9A7AC0, 0x10061A, 0x6A5A8A, 0x5A4A7A),
    "crystalocean": (0.8, 0xD0F8FF, 0xC0F0FF, 0xA0F0FF, 0x40B0E0, 0x80D0C0, 0x70C0B0),
    "duskhighlands": (0.6, 0xFF9060, 0xC07080, 0xFF9060, 0x2A1A4A, 0x7A9A50, 0x6A8A40),
    "neonjungle": (0.9, 0x0A1A3A, 0x08102A, 0x00F0FF, 0x02101A, 0x20C080, 0x10B070),
    "celestial": (0.5, 0x6A5AC0, 0x4A3A90, 0xFFF0C8, 0x0A0A2A, 0xC0C0A0, 0xB0B090),
    "lunarcolony": (0.0, 0x0A0A14, 0x08080C, 0xD0E8FF, 0x101018, 0x808088, 0x707078),
    "coralking": (0.8, 0xB0F0FF, 0x90E0FF, 0xFF70A0, 0x2AA0C0, 0x60D0A0, 0x50C090),
    "sketchbook": (0.6, 0xFFFFFF, 0xF4F0E8, 0xE8E0D0, 0xF0EAE0, 0xFFFFFF, 0xF4F0E8),
    "noir": (0.6, 0x404040, 0x303030, 0x9A9A9A, 0x101010, 0x404040, 0x303030),
    "psychedelia": (0.6, 0x00FFC0, 0xC000FF, 0xFF40FF, 0xFF00C0, 0x00FFC0, 0xC000FF),
    "comicverse": (0.6, 0xFFF0A0, 0xFFE0C0, 0xFFD020, 0x40A0FF, 0xFFF0A0, 0xFFE0C0),
    "pixelworld": (0.6, 0xA0D0FF, 0x90C0F0, 0x40E040, 0x5090FF, 0xA0D0FF, 0x90C0F0),
    "canvas": (0.6, 0xFFB060, 0xE09060, 0xE0A040, 0x3060A0, 0xFFB060, 0xE09060),
    "papercraft": (0.6, 0xFFFFFF, 0xFFF8F0, 0xF0F0E0, 0xC0E0FF, 0xFFFFFF, 0xFFF8F0),
    "neongrid": (0.6, 0x10002A, 0x080018, 0x00F0FF, 0x000008, 0x10002A, 0x080018),
    "reverie": (0.6, 0xFFC0E0, 0xE0B0D0, 0xFFC0E0, 0x6050A0, 0xFFC0E0, 0xE0B0D0),
    "corruptdata": (0.6, 0x003000, 0x001800, 0x39FF14, 0x000000, 0x003000, 0x001800),
    "blueprint": (0.6, 0x3060B0, 0x2050A0, 0x2050A0, 0x1A3A7A, 0x3060B0, 0x2050A0),
    "claymation": (0.6, 0xFFE0B0, 0xF0D0A0, 0xE08060, 0x70B0FF, 0xFFE0B0, 0xF0D0A0),
    "wireframe": (0.6, 0x001010, 0x000808, 0x40FFE0, 0x000000, 0x001010, 0x000808),
    "inkwash": (0.6, 0xF8F4EC, 0xF0ECE0, 0x202020, 0xE0E0D8, 0xF8F4EC, 0xF0ECE0),
    "glassworld": (0.6, 0xE0F8FF, 0xD0F0FF, 0xC0F0FF, 0x80C0FF, 0xE0F8FF, 0xD0F0FF),
    "watercolor": (0.6, 0xFFE8D0, 0xF0E0D0, 0x80C0E0, 0x80C0E0, 0xFFE8D0, 0xF0E0D0),
    "negative": (0.6, 0xC0C0C0, 0xE0E0E0, 0x00FFFF, 0xFFFFFF, 0xC0C0C0, 0xE0E0E0),
    "silhouette": (0.6, 0xFF8040, 0xD06040, 0xFF8040, 0x602060, 0xFF8040, 0xD06040),
    "vhstape": (0.6, 0x302040, 0x201830, 0xC0A0FF, 0x10081A, 0x302040, 0x201830),
    "astralplane": (0.6, 0x20104A, 0x100828, 0x8060FF, 0x02000A, 0x20104A, 0x100828),
    "origami": (0.6, 0xFFF0F0, 0xFFF8F8, 0xFF6060, 0xA0D0FF, 0xFFF0F0, 0xFFF8F8),
    "xray": (0.6, 0x001040, 0x000820, 0x4080FF, 0x000010, 0x001040, 0x000820),
    "fractal": (0.6, 0xFF9020, 0xA04080, 0xFF9020, 0x200820, 0xFF9020, 0xA04080),
    "comicpanels": (0.6, 0xFFF0A0, 0xFFE0C0, 0xFF3030, 0x40A0FF, 0xFFF0A0, 0xFFE0C0),
    "graphite": (0.6, 0xF0F0F0, 0xE0E0E0, 0x808080, 0xD0D0D0, 0xF0F0F0, 0xE0E0E0),
    "cyberrot": (0.6, 0x200030, 0x100018, 0xFF0080, 0x05000A, 0x200030, 0x100018),
    "dreamwash": (0.6, 0xFFD0F0, 0xF0D0F0, 0xC0A0FF, 0x8070C0, 0xFFD0F0, 0xF0D0F0),
    "poptrip": (0.6, 0xFFFF60, 0xFFC0FF, 0xFF60FF, 0xFF60FF, 0xFFFF60, 0xFFC0FF),
    "tapehorror": (0.6, 0x101810, 0x080C08, 0x608060, 0x020202, 0x101810, 0x080C08),
    "prismpixel": (0.6, 0x30106A, 0x180840, 0x40FFFF, 0x08001A, 0x30106A, 0x180840),
    "nexus": (0.5, 0x05030A, 0x100A20, 0x7DF9FF, 0x0A2A30, 0x7DF9FF, 0x7DF9FF),
}


def dimension_type(name, fixed_time):
    write_json(NS / "dimension_type" / f"{name}.json", {
        "ultrawarm": False, "natural": False, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
        "ambient_light": 0.0, "fixed_time": fixed_time, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": False,
        "logical_height": 384, "min_y": -64, "height": 384, "infiniburn": "#minecraft:infiniburn_overworld",
        "effects": rl(name),
    })


def worldgen():
    dimension_type("expanse", 6000)
    dimension_type("nexus", 6000)
    write_json(NS / "dimension" / "expanse.json", {
        "type": rl("expanse"),
        "generator": {"type": rl("universe"), "biome_source": {"type": rl("universe")}},
    })
    write_json(NS / "dimension" / "nexus.json", {
        "type": rl("nexus"),
        "generator": {"type": rl("nexus"), "biome_source": {"type": "minecraft:fixed", "biome": rl("nexus")}},
    })
    for name, (temp, sky, fog, water, water_fog, grass, foliage) in BIOMES.items():
        write_json(NS / "worldgen" / "biome" / f"{name}.json", {
            "has_precipitation": False, "temperature": temp, "downfall": 0.0,
            "effects": {
                "sky_color": sky, "fog_color": fog, "water_color": water, "water_fog_color": water_fog,
                "grass_color": grass, "foliage_color": foliage,
                "mood_sound": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
            },
            "spawners": {}, "spawn_costs": {}, "carvers": {}, "features": [],
        })
    write_json(NS / "worldgen" / "configured_feature" / "natural_rift.json", {"type": rl("natural_rift"), "config": {}})
    write_json(NS / "worldgen" / "placed_feature" / "natural_rift.json", {
        "feature": rl("natural_rift"),
        "placement": [{"type": "minecraft:in_square"}, {"type": "minecraft:biome"}],
    })
    write_json(NS / "neoforge" / "biome_modifier" / "overworld_rifts.json", {
        "type": "neoforge:add_features", "biomes": "#minecraft:is_overworld",
        "features": rl("natural_rift"), "step": "surface_structures",
    })


def generate():
    recipes()
    loot()
    tags()
    advancements()
    worldgen()
