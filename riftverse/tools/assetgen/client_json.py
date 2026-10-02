"""Client resources: blockstates, block/item models, sounds.json, particle definitions and the English language file."""
from .common import ASSETS, rl, write_json
from . import lang
from .sounds import SOUND_EVENTS

FULL_BRIGHT = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}
DIRS = ["down", "up", "north", "south", "west", "east"]

# blocks whose model is a plain cube; value = render type (None = solid)
CUBES = {
    "nexus_stone": None, "nexus_bricks": None, "nexus_glow": "cutout", "void_stone": None, "void_crystal": "cutout",
    "rift_crystal": "cutout", "neon_panel_cyan": "cutout", "neon_panel_magenta": "cutout", "cyber_plating": None,
    "cyber_glass": "translucent", "alien_moss": "cutout", "alien_soil": None, "lumen_leaves": "cutout_mipped",
    "skystone": None, "dream_turf": "cutout", "dream_cloud": "translucent", "dead_regolith": None, "ashen_rock": None,
    "cosmic_obsidian": "cutout", "stardust_sand": "cutout", "glitch_block": "cutout", "ancient_bricks": None,
    "ancient_glyph": "cutout", "ancient_gold": None, "abyssal_glow": "cutout", "rift_frame": "cutout",
    "starmetal_block": None, "aurora_glass": "translucent", "sculk_crystal": "cutout", "magma_crust": None, "chrome_plating": None, "honey_crystal": "cutout", "lunar_dust": None, "nebula_stone": None, "starwood_planks": None, "voidglass": "translucent", "rune_tile": None, "ember_bricks": None, "frost_crystal": "cutout", "coral_stone": None,
}
HANDHELD = {"portal_gun", "rift_blade", "reality_shaper", "reality_rupture", "pruning_staff"}
ITEMS = ["rift_shard", "void_essence", "stellar_dust", "singularity_fragment", "exotic_ingot", "warden_core", "leviathan_scale",
         "portal_gun", "rift_blade", "gravity_gauntlet", "singularity_grenade", "singularity_core", "reality_shaper", "dimensional_key",
         "universe_compass", "homeward_rift", "rift_igniter", "rift_sigil", "reality_remote", "reality_rupture", "delorean", "tardis", "pruning_staff", "universe_sample", "fusion_engine"]
ARMOR = [f"{s}_{p}" for s in ["rift_walker", "voyager", "event_horizon", "astral"] for p in ["helmet", "chestplate", "leggings", "boots"]]
CREATURES = ["astral_jelly", "sky_whale", "lumen_strider", "neon_drone", "glitchling", "void_stalker", "crystal_sentinel",
             "rift_wraith", "rift_warden", "abyssal_leviathan", "cosmic_deity", "void_cultist", "crystal_spider", "star_moth", "lunar_golem", "denizen", "tsa_agent"]
PARTICLES = ["spark", "mote", "streak", "ring", "glitch", "dust", "infall"]


def tex(name):
    return rl(f"block/{name}")


def box(frm, to, faces, glow=False, uv=None, cull=True):
    """One model element. faces maps direction -> texture variable; uv optionally maps direction -> [u0, v0, u1, v1]."""
    out = {}
    for d, var in faces.items():
        face = {"texture": f"#{var}_glow" if glow else f"#{var}"}
        if uv and d in uv:
            face["uv"] = uv[d]
        if cull and _on_boundary(frm, to, d):
            face["cullface"] = d
        out[d] = face
    el = {"from": frm, "to": to, "faces": out}
    if glow:
        el["shade"] = False
        el["neoforge_data"] = dict(FULL_BRIGHT)
    return el


def _on_boundary(frm, to, d):
    return {"down": frm[1] == 0, "up": to[1] == 16, "north": frm[2] == 0, "south": to[2] == 16,
            "west": frm[0] == 0, "east": to[0] == 16}[d]


def model(name, textures, elements, render_type=None, parent="minecraft:block/block"):
    m = {"parent": parent, "textures": textures, "elements": elements}
    if render_type:
        m["render_type"] = f"minecraft:{render_type}"
    write_json(ASSETS / "models" / "block" / f"{name}.json", m)


def simple_state(name, model_name=None):
    write_json(ASSETS / "blockstates" / f"{name}.json", {"variants": {"": {"model": rl(f"block/{model_name or name}")}}})


def cube(name, render_type, glow):
    if not glow:
        m = {"parent": "minecraft:block/cube_all", "textures": {"all": tex(name)}}
        if render_type:
            m["render_type"] = f"minecraft:{render_type}"
        write_json(ASSETS / "models" / "block" / f"{name}.json", m)
        return
    faces = {d: "all" for d in DIRS}
    model(name, {"particle": tex(name), "all": tex(name), "all_glow": tex(f"{name}_glow")},
          [box([0, 0, 0], [16, 16, 16], faces), box([0, 0, 0], [16, 16, 16], faces, glow=True)], render_type or "cutout")


def generate(glow_blocks):
    # --- plain cubes
    for name, rt in CUBES.items():
        cube(name, rt, glow_blocks.get(name, False))
        simple_state(name)

    # --- invisible anomaly blocks: particle texture only
    for name, particle in [("rift", "rift_particle"), ("portal_field", "portal_particle")]:
        write_json(ASSETS / "models" / "block" / f"{name}.json", {"textures": {"particle": tex(particle)}})
        simple_state(name)

    # --- lumen log (pillar with glowing veins)
    pillar = {"down": "end", "up": "end", "north": "side", "south": "side", "west": "side", "east": "side"}
    model("lumen_log", {"particle": tex("lumen_log"), "end": tex("lumen_log_top"), "side": tex("lumen_log"),
                        "end_glow": tex("lumen_log_top_glow"), "side_glow": tex("lumen_log_glow")},
          [box([0, 0, 0], [16, 16, 16], pillar), box([0, 0, 0], [16, 16, 16], pillar, glow=True)], "cutout")
    write_json(ASSETS / "blockstates" / "lumen_log.json", {"variants": {
        "axis=y": {"model": rl("block/lumen_log")},
        "axis=z": {"model": rl("block/lumen_log"), "x": 90},
        "axis=x": {"model": rl("block/lumen_log"), "x": 90, "y": 90},
    }})

    # --- gravity lift
    lift = {"down": "bottom", "up": "top", "north": "side", "south": "side", "west": "side", "east": "side"}
    lift_glow = {k: v for k, v in lift.items() if v != "bottom"}
    model("gravity_lift", {"particle": tex("gravity_lift_side"), "top": tex("gravity_lift_top"), "side": tex("gravity_lift_side"),
                           "bottom": tex("gravity_lift_bottom"), "top_glow": tex("gravity_lift_top_glow"),
                           "side_glow": tex("gravity_lift_side_glow")},
          [box([0, 0, 0], [16, 16, 16], lift), box([0, 0, 0], [16, 16, 16], lift_glow, glow=True)], "cutout")
    simple_state("gravity_lift")

    # --- multiverse console: base, column, tilted-free holo slab (matches the block's voxel shape)
    sides = ["north", "south", "west", "east"]
    console_textures = {"particle": tex("console_side"), "base": tex("console_base"), "side": tex("console_side"),
                        "top": tex("console_top"), "column": tex("console_column"), "side_glow": tex("console_side_glow"),
                        "top_glow": tex("console_top_glow"), "column_glow": tex("console_column_glow")}
    slab_uv = {d: [1, 4, 15, 12] for d in sides}
    col_uv = {d: [5, 3, 11, 10] for d in sides}
    model("multiverse_console", console_textures, [
        box([2, 0, 2], [14, 3, 14], {"down": "base", "up": "base", **{d: "base" for d in sides}}),
        box([5, 3, 5], [11, 10, 11], {d: "column" for d in sides}, uv=col_uv),
        box([5, 3, 5], [11, 10, 11], {d: "column" for d in sides}, glow=True, uv=col_uv),
        box([1, 10, 1], [15, 14, 15], {"down": "base", "up": "top", **{d: "side" for d in sides}}, uv=slab_uv),
        box([1, 10, 1], [15, 14, 15], {"up": "top", **{d: "side" for d in sides}}, glow=True, uv=slab_uv),
    ], "cutout")
    write_json(ASSETS / "blockstates" / "multiverse_console.json", {"variants": {
        f"facing={f}": {"model": rl("block/multiverse_console"), **({"y": y} if y else {})}
        for f, y in [("north", 0), ("east", 90), ("south", 180), ("west", 270)]
    }})

    # --- rift altar
    altar_textures = {"particle": tex("altar_side"), "side": tex("altar_side"), "top": tex("altar_top"), "bottom": tex("altar_bottom"),
                      "side_glow": tex("altar_side_glow"), "top_glow": tex("altar_top_glow")}
    model("rift_altar", altar_textures, [
        box([0, 0, 0], [16, 4, 16], {"down": "bottom", "up": "bottom", **{d: "side" for d in sides}}, uv={d: [0, 12, 16, 16] for d in sides}),
        box([3, 4, 3], [13, 12, 13], {d: "side" for d in sides}, uv={d: [3, 2, 13, 10] for d in sides}),
        box([3, 4, 3], [13, 12, 13], {d: "side" for d in sides}, glow=True, uv={d: [3, 2, 13, 10] for d in sides}),
        box([1, 12, 1], [15, 15, 15], {"down": "bottom", "up": "top", **{d: "side" for d in sides}}, uv={d: [1, 0, 15, 3] for d in sides}),
        box([1, 12, 1], [15, 15, 15], {"up": "top"}, glow=True),
    ], "cutout")
    simple_state("rift_altar")

    # --- glowcap: a full-bright cross
    cross = []
    for frm, to, faces in [([0.8, 0, 8], [15.2, 16, 8], ["north", "south"]), ([8, 0, 0.8], [8, 16, 15.2], ["west", "east"])]:
        cross.append({"from": frm, "to": to, "shade": False,
                      "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": 45, "rescale": True},
                      "neoforge_data": dict(FULL_BRIGHT),
                      "faces": {d: {"uv": [0, 0, 16, 16], "texture": "#cross"} for d in faces}})
    write_json(ASSETS / "models" / "block" / "glowcap.json", {"parent": "minecraft:block/block", "ambientocclusion": False,
                                                              "textures": {"particle": tex("glowcap"), "cross": tex("glowcap")},
                                                              "elements": cross, "render_type": "minecraft:cutout"})
    simple_state("glowcap")

    # --- item models
    block_items = list(CUBES) + ["lumen_log", "gravity_lift", "multiverse_console", "rift_altar", "rift_frame"]
    for b in dict.fromkeys(block_items):
        write_json(ASSETS / "models" / "item" / f"{b}.json", {"parent": rl(f"block/{b}")})
    write_json(ASSETS / "models" / "item" / "glowcap.json", {"parent": "minecraft:item/generated", "textures": {"layer0": tex("glowcap")}})
    for it in ITEMS + ARMOR:
        parent = "minecraft:item/handheld" if it in HANDHELD else "minecraft:item/generated"
        write_json(ASSETS / "models" / "item" / f"{it}.json", {"parent": parent, "textures": {"layer0": rl(f"item/{it}")}})
    # relics: a dark body (layer0) and an energy layer (layer1) tinted with the universe's colour at runtime
    for it, parent in (("relic_blade", "minecraft:item/handheld"), ("relic_blaster", "minecraft:item/generated")):
        write_json(ASSETS / "models" / "item" / f"{it}.json",
                   {"parent": parent, "textures": {"layer0": rl(f"item/{it}"), "layer1": rl(f"item/{it}_energy")}})
    for c in CREATURES:
        write_json(ASSETS / "models" / "item" / f"{c}_spawn_egg.json", {"parent": "minecraft:item/template_spawn_egg"})

    # --- particles
    for p in PARTICLES:
        write_json(ASSETS / "particles" / f"{p}.json", {"textures": [rl(f"{p}_{i}") for i in range(4)]})

    # --- sounds.json
    sounds = {}
    for event, spec in SOUND_EVENTS.items():
        entry = {"name": rl(event.replace(".", "/"))}
        if spec.get("stream"):
            entry["stream"] = True
        sounds[event] = {"sounds": [entry], "subtitle": f"subtitles.riftverse.{event}"}
    write_json(ASSETS / "sounds.json", sounds)

    # --- language
    write_json(ASSETS / "lang" / "en_us.json", lang.english())
