# Wandcraft: The Art of Magic

A NeoForge 1.21.1 magic mod: a wand, a radial spell wheel, mana, mastery ranks and evolving spells.
This directory is self-contained and unrelated to the Wand Enhancer patcher in the repository root.

## Status: Phase 3

The framework is complete and 73 spells across six schools are implemented and smoke-tested. Remaining schools are added group by group on the same framework.

| School | Spells | State |
| --- | --- | --- |
| Arcane | Arcane Bolt, Arcane Barrage, Arcane Burst, Arcane Beam, Rune Mine, Arcane Orb, Spell Echo, Dispel, Arcane Mark, Mana Pulse | done |
| Elemental | Firebolt, Flame Lance, Firestorm, Phoenix Wings, Magma Grasp, Frost Shard, Glacial Prison, Blizzard, Ice Bridge, Lightning Bolt, Chain Lightning, Thunderstep, Stormcall, Stone Spike, Earth Wall, Seismic Wave, Sand Vortex | done |
| Force | Force Push, Force Wave, Force Pull, Levitation, Gravity Well, Repulsion Field, Kinetic Slam, Force Jump, Kinetic Catch | done |
| Nature | Vine Lash, Thorn Lash, Root Snare, Thorn Volley, Healing Bloom, Guardian Vine, Pollen Veil, Nature's Step, Blooming Ground | done |
| Defense | Magic Shield, Aegis Shield, Aegis Dome, Counterspell, Mirror Ward, Phase Guard, Cleansing Light, Rescue Blink | done |
| Dark Arts | Shadow Bolt, Void Bolt, Shadow Grasp, Abyssal Chains, Soul Drain, Umbral Cloak, Black Flame, Nightfall, Shadow Clone, Curse Mark, Hex, Withering Wave, Shadowstep, Dark Nova, Eclipse Nova, Void Rift, Corruption Orb, Devouring Shadow, Dark Pact, Eclipsing Beam | done (strong spells cost life force) |
| Illusion, Space, Light, Forbidden | | not started; the wheel only lists schools that contain spells |

## Playing

* Craft the **Wand**: amethyst shard, gold ingot and stick on a diagonal (`  A` / ` G ` / `S  `).
* Hold **R** (rebindable) with the wand in your main hand to open the wheel; point at a spell and release. Scroll or use the arrow keys to change school; click a school tab as well. **B** opens the searchable Spellbook.
* Right-click casts. Instant spells fire immediately, charge spells fire when you release (a tap is a minimum-charge cast), channel spells run while you hold and drain mana per second. Switching items or losing the wand stops a channel and cancels a charge.
* Movement is not slowed while casting.

### Progression

* Every successful use (the spell actually affected something, not empty air) counts toward that spell's mastery. Ranks: Novice, Adept (25 uses), Master (100 uses). Adept and Master lower mana cost and cooldown and raise power.
* Total successful uses raise your **wizard level** (`floor(sqrt(uses / 3))`, max 30). Level raises max mana and regen and unlocks spells.
* Evolutions unlock at Master rank of the parent: Firebolt to Flame Lance, Arcane Bolt to Arcane Barrage, Lightning Bolt to Chain Lightning, Stone Spike to Seismic Wave.
* Creative mode has every spell, unlimited mana and shortened cooldowns.

### Commands (op)

`/wandcraft level <0-30>`, `/wandcraft master <spell_id|all>`, `/wandcraft mana`, `/wandcraft reset`.

## Visuals

* **Custom particles** (`ModParticles`, `client/particle/MagicParticle`): tinted fullbright `glow`, `spark`, `wisp` and a flat spinning `rune` decal. `Fx.glow/spark/wisp/runeDecal` are the entry points; colour and size travel in the ARGB value.
* **Beam shaft** (`SpellBeam` + `client/BeamRenderer`): Arcane Beam, Soul Drain and Eclipsing Beam draw rotating, layered octagonal tubes that follow the wand tip, with a bright core, tinted sheath and end-point sparks. Width and brightness follow beam intensity.
* **Seismic Wave** throws visual block copies (`DebrisEntity`) into the air along the wave; the real terrain is never changed.
* **3D wand** (`tools/gen_wand_model.py`): a 38-element model with a wrapped handle, gold collar, twisting shaft, prong cradle, faceted fullbright crystal and orbiting shards on an animated atlas. Regenerate textures with `python3 tools/gen_textures.py` and the model with `python3 tools/gen_wand_model.py`.
* **Summons** (`ShadowClone`) use the player model with a generated shadow skin.
* `gradle runClient -Pdev` runs a scripted session (`client/DevAutomation`) that joins `run/saves/devworld`, casts several spells and writes screenshots to `run/screenshots`.

## Design notes

* **Server authority.** Clients only send spell *selection*; the server validates unlocks, mana, cooldowns, targets and damage. Casting runs through `Casting` (validate, spend, run, cooldown, credit).
* **Data-driven spells.** A spell is a `Spell` definition with lambdas (`magic/spells/*`). Projectile spells share one `SpellProjectile` entity and area spells share one `SpellZone` entity; each spell supplies its behaviour. Neither entity is saved, so nothing lingers across restarts.
* **No permanent terrain damage.** Every block a spell places goes through `TempBlocks`, which records the original state, restores it on expiry (persisted, and flushed on server stop), never replaces anything but air, replaceable plants and water source blocks, honours spawn protection, and turns mining into durability damage with no drops. Conjured ice is a custom block that cannot melt into water.
* **Limits.** `maxEntitiesPerCaster` caps projectiles and zones per player, `fxIntensity` (server and client configs) thins particles, and several spells cap their instances (for example three Rune Mines).
* **Boss and heavy resistance.** `Combat.isBoss`/`isHeavy` gate displacement and crowd control.

## Building and testing

```
gradle build                  # build/libs/wandcraft-0.1.0.jar
gradle runClient / runServer  # dev runs
gradle runGameTestServer      # casts every spell against a field of enemies
```

## Known gaps in Phase 1

* Sounds are vanilla; there is no bespoke audio yet.
* Screen effects (config flag exists) are reserved for Forbidden magic.
* Multiplayer was exercised with a mock player only; a real two-client session has not been run.
