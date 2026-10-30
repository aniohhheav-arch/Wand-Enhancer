# ARCANA: Wands of the Forgotten

A magic RPG expansion for **Minecraft 1.21.1 / NeoForge 21.1.x (Java 21)** built around collectible, customisable and evolvable wands.

> This folder is a self-contained Gradle project. It is unrelated to the Wand-Enhancer patcher in the rest of the repository.

## Building and running

```bash
cd arcana-mod
gradle build            # jar in build/libs (or use ./gradlew once the wrapper is generated)
gradle runClient        # dev client
gradle runServer        # dev dedicated server (accept the EULA in run/eula.txt)
gradle runGameTestServer  # 4 automated game tests (see "Testing")
```

Assets, lang, recipes, loot, advancements, worldgen and structure NBT are **generated** by `tools/gen_assets.py`
(needs Python 3 + Pillow). Spell ids are parsed from `Spell.java`, so the Java enum stays the source of truth:

```bash
python3 tools/gen_assets.py
```

## Gameplay

### The eight schools and the Forbidden Wand

| School | Primary (right-click, hold to charge) | Secondary | Special (Awakened) | Ultimate (Ascended) |
|---|---|---|---|---|
| **Void** | Singularity: orb that becomes a gravity well | Rift Step: blink, damaging the trail | Gravity Crush: lift then slam | Event Horizon: black hole with accretion disc |
| **Verdant** | Root Snare: eruption of roots, fan when awakened | Thorn Barrier: healing/repelling ring | Spore Bloom: heal, poison, grow crops | Grove Guardians: summoned golems + mass root |
| **Glacier** | Ice Spikes: freezing eruption line/fan | Frost Nova: ring, freezes water | Frozen Prison: immobilise, shatter | Blizzard: swirling storm |
| **Crimson** | Crimson Chains: bind and drag | Crimson Wave: crescent of energy | Blood Seal: slowing glyph, then blast | Crimson Cataclysm: 5 shockwaves, lifesteal |
| **Solar** | Solar Beam: piercing beam, sustained when ascended | Mini Sun: hovering sun, then burst | Dawnlight: heal/cleanse, burn undead | Supernova |
| **Storm** | Thunderbolt: strike/volley | Chain Lightning | Static Field: speed + zap aura | Tempest |
| **Chronos** | Temporal Slow: field slows enemies and projectiles | Rewind: back to where/how you were 5s ago | Chrono Surge: haste allies, slow foes | Time Stop |
| **Arcane** | Arcane Bolt: random wild effect each cast | Echo: replay your last spell, from any wand | Arcane Barrage: homing missiles | Arcane Cataclysm: telegraphed pillars |
| **Forbidden** (no school) | Mimic: copy the primary spell of any school you cast recently | Unstable Rift | Terrain Warp | Rift Collapse |

Every spell is a server-side tick function (`EffectManager`) - no projectile entities - so spell spam does not spawn entities,
and there is nothing for a client to forge.

### Evolution
Dormant -> Awakened -> Ascended. Needs mastery level, school crystals and a rare material found in structures or dropped by the boss
(Awakening Shard, Ascension Core). Awakened wands unlock the Special spell, +20% power per stage, fans/volleys and combos;
Ascended wands unlock the Ultimate (with a cinematic flash/shake) and a redesigned, animated texture.

### Controls (all rebindable under *ARCANA*)
| Key | Action |
|---|---|
| Right-click (hold) | Cast the selected spell; hold to charge charge-able ones |
| R / X / Z | Secondary / Special / Ultimate |
| C (hold) | Radial spell wheel; release to choose the right-click spell |
| G | Wand menu: evolve and customise |

### Customisation (G menu, server-validated)
Handle (traits), crystal colour (dye), core (on-hit effect), particle style, two spell-modifier runes. Changing a part consumes the new item and returns the old one.

### Mastery and combos
Each school has its own mastery (levels 0-10): more damage, lower mana cost and cooldown, higher max mana. Hitting an enemy with two *different*
schools within five seconds triggers a **combo** (Shatter, Eclipse, Singular Surge, Photosynthesis, Steam Burst, Time Echo, Event Collapse, Bloodbloom, otherwise Resonance).

### The Forbidden Wand
No school, cannot evolve. Craft: 3 Forbidden Fragments + Ascension Core + Nether Star. Fragments only come from the Archmage's sanctum and its chests.
Its model re-skins itself to the selected ability and crystal colour.

### World
* Crystal formations in caves (8 school variants, biome-independent in the overworld).
* Ancient ruins, wizard towers, hidden libraries, 8 school shrines, and the **Archmage Sanctum** (underground, with the boss).
* Boss: **The Hollow Archmage** - boss bar, 3 phases, telegraphed attacks, vex summons, ~2s cinematic death, drops Ascension Core, Awakening Shards, crystals, runes/cores and (rarely) Forbidden Fragments.

## Admin commands (op level 2)
```
/arcana mastery <players> <school|all> set|add <level>      /arcana mastery <players> <school|all> setxp|addxp <xp>
/arcana mastery <players> reset
/arcana mana <players> fill | set <amount>                 /arcana cooldowns <players> reset
/arcana stage <players> <0-2>   (held wand)                /arcana info <player>
```

## 3D and cinematics (GeckoLib 4.8, bundled via jar-in-jar)
* Wands are animated 3D models: per-school geometry, extra filigree/wings/orbiting gems per stage, crystal tinted by the chosen dye,
  handle colour by material, idle/charge/cast animations. The Forbidden Wand re-skins itself to the selected ability.
* The Hollow Archmage is an animated 3D model (idle, walk, cast, summon, phase roar, 5-second death).
* Every ultimate spawns a 3D set-piece (black hole, sun, ice spire, crimson blades, storm cloud, giant clock, arcane sigil, world tree, rift),
  shows a title card, slides in letterbox bars, tilts to a third-person hero camera with an FOV push (config `cinematicCamera`).

## Configuration
`serverconfig/arcana-server.toml` (world) - global multipliers, particle density, effect cap, block-change toggles, mimic window,
evolution requirements, boss health, and **per-spell** `enabled` / `damageMultiplier` / `cooldownMultiplier` / `manaMultiplier`.
`config/arcana-client.toml` - screen shake, cinematics, HUD scale.

## Design notes
* **Server authoritative**: clients only send requests (`cast_slot`, `select_spell`, `customize`, `evolve`); costs, cooldowns, stage and mimic eligibility are checked in `CastService`/`Customize`.
* **State**: `PlayerMagic` player attachment (mana, mastery, cooldowns, recent schools), synced by `arcana:sync` only when something changed.
* **Safety**: temporary world/entity state (Time Stop, Terrain Warp, Grove Guardians) is restored on shutdown (`EffectManager.shutdown`) and repaired on entity load.
* **Items** use data components (`stage`, `style`, `selected`).

## Testing
`gradle runGameTestServer` runs `src/gametest`: every one of the 43 spells is cast through the full pipeline against live mobs and the
test fails on any exception in an effect callback; plus evolution/customisation rules and mimic rules.
`gradle runClient -PquickPlay=<world>` additionally runs the dev-only screenshot harness (`ClientShots`, not shipped) that writes images to `run/screenshots`.

## Known limitations (honest list)
* The 3D models are procedurally generated low-poly GeckoLib models (`tools/gen_geo.py`), not hand-sculpted Blockbench art; they are meant to be replaceable with the same file names.
* Sounds are vanilla sounds pitched per school; there are no custom `.ogg` files.
* Dynamic light is not used (vanilla has none); luminous crystals and particles carry the effect.
* Numeric balance (damage, costs, cooldowns) has not been playtested by humans.
