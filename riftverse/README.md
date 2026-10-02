# Riftverse: Portals, Rifts & the Multiverse

> *You have discovered technology capable of breaking the boundaries between universes.*

A cinematic NeoForge mod for **Minecraft 1.21.1** about portals, dimensional rifts, black holes and an endless multiverse,
including universes you create just by describing them.

---

## Features

### Portals and rifts
- **Natural rifts** tear open across the Overworld and inside other universes. There are twenty-nine rift types (Azure,
  Crimson, Verdant, Void, Prismatic, Nexus, Glitch, Stellar, Return, Solar, Abyssal, Fungal, Sanguine, Brass, Saccharine,
  Tempest, Umbral, Patina, Auroral, Molten, Primal, Chrome, Sculk, Nebular, Forge, Frost, Weald, Celestial and Runic).
  Every rift locks onto one concrete world when it opens, takes that world's colours and names it when you approach
  ("Rift → <name>"), so a rift's colour always tells you where it goes, each with its own colours, shader animation and destination family. A rift lenses the world behind it, glows onto the ground and distorts your screen as you approach.
  Walk in to cross over, or **sneak-use** an open rift with an empty hand to harvest **Rift Shards**.
- **Built portals:** ignite a rectangle of **Rift Frame** blocks with a **Rift Igniter** to open a stable gate to the Nexus.
  Hold an imprinted **Dimensional Key** in your off-hand to link the gate to that exact universe instead.
- **Dimensional Portal Projector (portal gun):** fire a linked pair of blue/orange portals and walk between them, or
  charge a rift-tearing shot that opens a passage to another universe.

### The black hole
Black holes are rendered with a real-time gravitational-lensing shader: an Einstein ring, a Doppler-boosted accretion
disk and a photon sphere. Fall into one and the mod plays a full cinematic instead of a teleport:
1. **Pull:** you lose control as the camera is dragged toward the horizon. The world warps, FOV stretches, the screen
   shakes and debris spirals into the disk.
2. **Crossing:** the camera flies through the event horizon with chromatic aberration and a flash.
3. **Wormhole:** a tunnel of stars, galaxies and dimensional distortion, scored by a Shepard-Risset glissando that rises
   forever. It shifts through several abstract "multidimensional" phases.
4. **Emergence:** you burst out into an alternate universe with an establishing camera shot and a title card naming the
   reality you arrived in.

Black holes occur naturally in some universes and sit at the heart of the Nexus. You can create permanent ones with a
**Singularity Core**, and the Event Horizon armour can collapse short-lived ones.

### The Multiverse Nexus and console
The **Nexus** is a hub dimension floating around a captive singularity. It has 62 gates (one per prime reality, in an outer and an inner ring),
satellite platforms and the **Rift Altar**. At a **Multiverse Console** you can:
- browse every prime, discovered and manifested reality,
- **travel** to any of them, roll a **random** universe, or spin up a **new variant** of an archetype,
- **open a gate** beside the console or **imprint a Dimensional Key**,
- **manifest a new universe from a description**, for example:
  - *"A dark cyberpunk city during a permanent thunderstorm"*
  - *"A massive ocean planet with floating islands and giant creatures"*
  - *"A peaceful alien forest with glowing plants and two moons"*

  The interpreter reads your words and builds a universe to match: archetype blend, terrain, megastructures, sky,
  moons, weather, time of day, scale, creatures, gravity, colours and music. It then tells you what it understood.

### Universes
All universes are generated analytically. There are **62 prime archetypes**, each mutated into infinite variants:
Neon Sprawl, Xenoflora Wilds, Skyshatter Isles, Thalassic Expanse, Prismatic Reach, Ashen Remnant, Astral Vastness,
Corrupted Sector, Elder Dominion, Inverted Heights, The Hollow Dark, Somnium, Cinder Forge, Rime Eternal, Sunscar Dunes,
Rust Mesa, Coral Shallows, Myco Hollows, Clockwork Reach, Sanguine Expanse, Confection, Tempest Reach, Fenrot Mire,
Obsidian Spires, Verdigris Ruins, Radiant Expanse, Ferrous Wastes, Spectral Bloom, Aurora Tundra, Magma Throne,
Primeval Jungle, Nebula Drift, Fallout Wastes, Amethyst Geode, Sculk Depths, Golden Savanna, Chrome Metropolis,
Lunar Plains, Golden Hive, Mirror Realm, Starforge Foundry, Frostglass Caverns, Echoing Abyss, Molten Sea,
Cloud Kingdom, Drowned Ruins, Toxic Bog, Crimson Weald, Warped Weald, Golden Temple, Rainbow Reach, Voidglass Expanse,
Runic Plateau, Ember Steppe, Pastel Dreamscape, Petrified Starwood, Crystal Ocean, Dusk Highlands, Neon Jungle,
Celestial Court, Lunar Colony and Coral Kingdom.
- 15 terrain modes, 13 kinds of megastructure (arcologies, rings, spires, colossal ruins and more), city street grids
  and treasure caches.
- Each universe has its own shader skybox: nebulae, galaxies, auroras, ringed planets, multiple moons, suns and
  iridescent bubbles. Each also has its own fog, lighting palette, weather (rain, thunderstorms, snow, ash, spores,
  embers, data rain, stardust, petals), gravity, music and ambience.

### Equipment
| Item | What it does |
|---|---|
| Dimensional Portal Projector | Linked portals; charged rift shots |
| Rift Blade | Reality-tearing strikes; dash through micro-rifts |
| Gravity Gauntlet | Grab, lift and hurl anything; open crushing gravity wells |
| Singularity Grenade | Thrown gravity well |
| Singularity Core | Births a permanent black hole |
| Reality Shaper | Transmute terrain into another universe's matter; Stasis bubble that freezes time |
| Dimensional Key, Universe Compass, Homeward Rift, Rift Igniter, Rift Sigil | Navigation, linking, getting home, boss summoning |

**Armour sets**, each with a passive bonus and an ability on the **V** key:

| Set | Ability |
|---|---|
| Rift Walker | Phase Shift through solid matter |
| Voyager Exploration Suit | Deep Scan; night-vision visor |
| Event Horizon | Collapse a black hole |
| Astral Regalia | Starfall; flight in other universes |

Every set has its own visible aura: orbiting rift shards, a holographic visor, a lensing mini-singularity, or orbiting
stars.

### Creatures and bosses

**The Cosmic Deity** (event `cosmic_deity`, spawn egg): a titan with a person's body whose skin is the night sky, a
turning halo and burning eyes. Between volleys of star-bolts it inhales: its mouth tears open across its face, far
wider than its head, streams of light spiral in, and creatures, items and loose terrain are dragged into it. Players
nearby see the world warp (FOV surge, inward-rushing rings, violet bleed, shaking). It also plays the Celestial
Devourer in the End Protocols. New creatures: Void Cultist (blinks to its prey), Crystal Spider (crystallising bite),
Star Moth (glittering night flyer) and Lunar Golem (guardian of the moon worlds). 14 new building blocks: Starmetal,
Aurora Glass, Sculk Crystal, Magma Crust, Chrome Plating, Honey Crystal, Lunar Dust, Nebula Stone, Petrified Starwood,
Voidglass, Rune Tile, Ember Bricks, Frost Crystal and Glowing Coral Stone.

- **Astral Jelly**, **Sky Whale** and **Lumen Strider**: peaceful drifters, sky leviathans and lantern-bearing grazers.
- **Neon Drone**, **Glitchling**, **Void Stalker**, **Crystal Sentinel** and **Rift Wraith**: hostile and dimensional.
- **The Rift Warden** is a three-phase boss summoned at the Rift Altar. It fires bolt barrages, makes aerial slams,
  summons wraiths and void singularities, and in its final phase sweeps a reality-cutting beam.
- **The Abyssal Leviathan** is a colossal sky-serpent whose body follows the path its head took.

All creatures are built from procedural, animated geometry with emissive details and their own synthesised voices.

---

### Ultimate Multiverse Expansion

**Reality Remote** (endgame item: singularity fragments, a Warden Core, exotic ingots, a Singularity Core and a Leviathan
Scale). Use it to open its console; sneak-use for a quick scan. The console lists every universe you have charted and
offers: **Scan** (full research readout), **Stabilize** (stability back to 100%, glitching removed, events there ended),
**Visit**, **Modify** (gravity, time, weather, storm, glitch, fog, hostility, saturation, creature scale, vacuum,
dreamlike, name; changes sync live to everyone inside), **Archive** (seal a universe with its terrain intact),
**Restore**, **Rebuild** (same definition, a brand-new reality, or a chosen archetype), **Architect** (create a universe
from a description or from Universe DNA), **Events** (trigger any of the ten) and the **End Protocols**. Functions are
gated by cosmic rank (creative players and operators are exempt; configurable). Everything is validated again on the
server.

The console has four tabs (Universe, Reality, Events, END), fits any GUI scale and explains every button in tooltips.
The END tab can end the selected universe, or **the world you are standing in, whatever it is**: any universe (dreams,
cyberpunk...), Earth (the Overworld), the Nether, the End or a modded dimension. With *Forever* on (the default), the
ending is permanent: no backup is kept, the universe can never be restored, rebuilt or visited again, and an ended
dimension refuses every portal, rift, home trip and respawn (players respawn in the Nexus instead). Everything a player
can see (the server view distance) is wiped, every block and every non-player entity, chunk by chunk in a fast
expanding wave.

**End Protocols**: six fully staged ways to unmake a universe. Each has its own camera work, particles, sounds,
environmental reactions, screen effects and a configurable duration:
1. *Orbital Annihilation*: an orbital platform is built in the sky. A targeting scan sweeps the land, then energy lances
   hammer it (explosions, flying debris, lightning) under a targeting-reticle HUD.
2. *Singularity Collapse*: a singularity grows overhead and drags terrain fragments into it. The picture caves in toward
   one point before it compresses to nothing in a flash.
3. *The Celestial Devourer*: a colossal sky leviathan descends with a spiralling galaxy maw and inhales light and land.
4. *Reality Disassembly*: blocks detach and float apart as glowing wireframe cubes while the screen turns into a grid.
5. *Black Hole Infusion*: an expanding black hole with accretion spirals swallows the land, then collapses.
6. *Timeline Erasure*: eras rewind in flickering colour washes, the world becomes a glowing wireframe, then fades out.

Every protocol (and plain erasure) first saves a definition backup and a block snapshot around each occupant. It marks
the universe ERASED, so new chunks generate as void and travel is refused. Occupants get Slow Falling and Resistance V
during the sequence. At the point of no return the erase wave unmakes the terrain and everyone inside is evacuated to
the Nexus, so no one can be trapped (a watchdog also pulls anyone found in a sealed universe back to the Nexus). Before
the point of no return, `protocol stop` / the Remote's **Abort** cancels it cleanly. *Reconstruct after* restores the
universe automatically when the wave ends. **Preview** plays any protocol for you alone without touching the world.

Every event opens with a shockwave-and-light-pillar intro and closes with an outro burst. **Universe Birth** is a
three-act cutscene: light spirals into a point in the sky, it ignites, and a little moon condenses there shell by shell
(it stays). The newborn universe is then named, a stable rift opens beneath the moon, and inside a universe the sky
gains a new moon.

**Multiverse events** happen naturally (configurable rarity per event, per-event cooldown, global minimum gap, never in
the Nexus or the Infinite Corridor) and can all be started by command or the Remote:
reality collapse, dimensional invasion (wave defence with rewards), cosmic leviathan (boss), ancient guardian (Rift Warden
boss), dimensional anomaly (gravity flips, levitation, blinking matter), void wanderer (giant void stalker that smothers
light), universe birth (a new universe condenses and leaves a stable rift into it), black hole (grows over time), rift
storm (unstable rifts everywhere) and cosmic convergence (rifts to many realities, wandering visitors). Witnessing events
earns research.

**Reality rewriting**: erase, rebuild and restore rewrite already-generated terrain in expanding waves, a budgeted
number of block columns per tick. Rebuilt columns are regenerated exactly as world generation would make them, then
re-decorated. Only chunks inside the target universe's slot are touched and everything goes through normal level APIs,
so saves stay valid. **Snapshots** save and restore a block area with vanilla's structure format.

**Cosmic progression**: research points from charting universes, scans, events and reality work raise your rank:
Wanderer, Riftwalker, Voyager, Cartographer, Reality Architect, Multiversal Sovereign. Universe profiles track status,
stability, visits, scans, events, erasures, rebuilds and who charted them first.

**Universe DNA**: every universe has a shareable code (`/multiverse universe dna`). `/multiverse universe create
dna:RV-...` grows a sibling of that reality.

**The Infinite Corridor**: an endless lit hallway suspended in the void. A recessed doorway every 32 blocks holds a rift
into a different family of realities, with the occasional rift back to the Nexus. It is reached from the new north gate
of the Nexus or with `/multiverse universe visit corridor`.

**Nexus expansion**: four new satellites on the cardinal axes: the Infinite Corridor gate (north), the Event Observatory
spire (east), the Convergence Spire with a ring of rifts to eight realities (south) and the Reality Architect's dais
(west). In worlds whose Nexus was generated before this update, build them with `/multiverse hub regenerate` (it only
fills empty space).

#### Known limitations (honest notes)
- Ending a vanilla dimension wipes what players can see and seals it forever, but region files further away are not
  deleted (nobody can reach them). Worlds whose Nexus already existed keep their old gate ring; gates for the 12 newest
  realities appear only where Nexus chunks are generated fresh (they are always reachable by rifts and the console).
- Erase/rebuild/restore rewrite terrain within `rewriteRadius` of the universe origin and of every player inside it.
  Chunks of that universe generated elsewhere earlier stay on disk unchanged (they are unreachable while it is erased).
  After a *mutated* rebuild, those distant old chunks keep their previous look, so seams can appear there.
- Universe "time" is a sky/lighting mode per universe (cycle/day/dusk/night). Minecraft has a single clock per
  dimension and every universe shares the Expanse dimension, so true per-universe time freezing is not possible.
- Events and protocols in progress are ended cleanly when the server stops (their rifts and spawned creatures are
  removed, and a protocol that has not reached its point of no return is aborted). They do not resume after a restart.
- Restore uses the latest definition backup plus regeneration. Player builds inside an erased area come back only via
  the automatic block snapshots (`/multiverse reality snapshot load auto_<gx>_<gz>_<n>`), which cover the configured
  snapshot box around each occupant.
- Cinematic camera work is client-side and can be disabled (`cinematicCamera`), as can expensive particles
  (`heavyEffects`).
- The Celestial Devourer is staged with a giant, AI-less sky leviathan model rather than a new bespoke entity model.

## Getting started (player guide)
1. Find a **natural rift** in the Overworld; they glow and hum. Sneak-use it to harvest **Rift Shards**, or walk in.
2. Craft a **Rift Igniter** (flint and steel + rift shard) and **Rift Frames** (obsidian + rift shards), build a ring and
   ignite it to reach the **Nexus**.
3. Use a **Multiverse Console**, either in the Nexus or crafted, to explore, travel and manifest your own universes.
4. Gather **Stellar Dust**, **Void Essence** and **Exotic Matter Ingots** from universes and their creatures. Feed items to
   a black hole to make it spit out **Singularity Fragments**.
5. Craft a **Rift Sigil**, offer it at the Rift Altar, and face the Warden.

## Commands (operators)
```
/riftverse manifest <description>   create a universe from text and report what was understood
/riftverse travel <archetype>       cinematic jump to a fresh universe of that archetype
/riftverse random | nexus | home    jump somewhere random, to the Nexus, or back home
/riftverse blackhole [radius]       spawn a black hole in front of you
/riftverse rift <type>              open a natural rift of the given type
/riftverse info                     identify the universe you are standing in
```

`/multiverse` (read-only parts open to all; anything that changes the world needs permission level 2; destructive
operations require `/multiverse confirm` within 30 s). Universes can be named `here`, `corridor`, an archetype id,
`u<number>` (designation number) or `gx.gz` (slot), all with tab completion.
```
/multiverse event list | history
/multiverse event start <event> [targets] | summon <event> [targets]
/multiverse event stop <event|#id|all> | info <event>
/multiverse event cooldown <event> [reset|default|<minutes>] | rarity <event> [default|<value>]
/multiverse cinematic list | play <name> [targets] | stop [targets]
/multiverse reality scan | inspect [universe] | stabilize [universe] | modify <trait> <value>
/multiverse reality erase [universe] | rebuild [universe] [new|<archetype>] | restore [universe]
/multiverse reality snapshot save|load|delete <name> | snapshot list
/multiverse reality protocols
/multiverse reality protocol <protocol> [universe] [reconstruct|permanent]
/multiverse reality protocol <protocol> world      (end the dimension you stand in, forever)
/multiverse reality protocol stop [universe] | protocol preview <protocol> [targets]
/multiverse universe list [page] | info [universe] | dna [universe] | create <description | dna:CODE>
/multiverse universe visit <universe> | archive <universe> | restore <universe>
/multiverse profile [player] | hub regenerate | confirm | cancel
/multiverse debug events | dimensions | portals | reality | performance
```

## Configuration (`config/riftverse-common.toml`)
`blackHolesBreakBlocks`, `blackHolesBreakBlocksInVanillaDimensions`, `naturalRiftRarity`,
`maxPromptUniversesPerPlayer`, `universeCreatureSpawning`.

`[events]`: `naturalEvents`, `checkIntervalSeconds`, `minMinutesBetweenEvents`, `eventsAlterTerrain`,
`eventsInVanillaDimensions`, plus `rarity` and `cooldownMinutes` for each event (both can also be overridden in-game,
saved with the world). `[reality]`: `rewriteRadius`, `rewriteColumnsPerTick`, `backupsPerUniverse`, `snapshotRadius`,
`snapshotHeight`, `remoteRequiresRank`, `protectPrimeUniverses`, `allowEndingVanillaDimensions`, and `[reality.protocols]` with the length of each End
Protocol. `[cinematics]`: `cinematicCamera`, `heavyEffects`.

---

## Building
Requirements: **JDK 21**. Gradle is provided by the wrapper.

```bash
cd riftverse
./gradlew build          # jar in build/libs/
./gradlew runClient      # dev client
```

The build downloads NeoForge and Minecraft from `maven.neoforged.net`, `piston-meta.mojang.com`,
`libraries.minecraft.net` and `resources.download.minecraft.net`. Make sure those hosts are reachable.

> **Status:** the mod's sources were written and checked against the NeoForge 1.21.1 API and reference mods. The shaders
> were compiled with glslang and test-rendered offline, and every resource reference was cross-checked. However, the
> environment it was written in could not reach the Minecraft and NeoForge download servers, so it has **not yet been
> compiled or run in-game**. Expect the first `./gradlew build` to possibly surface a few compile errors to fix.

## Asset pipeline
Every texture, sound effect, music track and JSON resource is generated procedurally and deterministically:

```bash
pip install -r tools/requirements.txt
python3 tools/generate_assets.py            # regenerate everything (add --no-sound to skip audio)
python3 tools/check_resources.py            # cross-check registrations vs. resources
```

- **Textures:** procedural noise, Voronoi and pixel art. Glowing details are separate overlays rendered full-bright
  through NeoForge's `neoforge_data` block-model extension.
- **Audio:** 42 mono Ogg Vorbis files synthesised from scratch (FM, Karplus-Strong, filtered noise and convolution
  reverb), including seven 64-second music tracks.

## Project layout
```
src/main/java/dev/riftverse/
  universe/   archetypes, universe specs, prompt interpreter, registry
  world/      chunk generators, terrain sampler, megastructures, decorator, nexus layout
  transit/    destinations and the server-side journey timeline
  block/ item/ entity/ player/ event/ command/ network/ registry/
  client/     shaders, lensing pass, sky, cinematic director and camera rig, HUD, renderers, console screen
src/main/resources/assets/riftverse/shaders/   GLSL core shaders (sky, black hole, rift, wormhole, screen FX, cosmos, energy)
tools/        asset generator and resource checker
```

Licensed under MIT.

## The Reality Rupture (creator weapon)

The ultimate artifact. Ops get it with `/multiverse weapon give [player]` (it's also in the creative tab). There's no password or authorisation step: whoever holds it can use it.

- **Abilities:**
  - **Use — Reality Tear:** opens a tear that pulls creatures in and migrates them.
  - **Sneak-use — Existence Disassembly:** removes a creature, or a region of terrain that can be restored.
  - **Sneak-use looking straight up — THE FINAL RUPTURE.**
- **Config `[rupture]`:** `radius`; `ultimateMode` = `visual` | `area` (terrain comes back by itself after 60 s) | `universe` (normal erase with a backup).
- **Commands:** `/multiverse weapon give [player]`, `discover` (discovery cinematic + weapon), `aura`, `inspect`, `test`, `restore`, and `/multiverse reality rupture preview|activate [visual|area|universe]`.

## Time travel and the TSA

- `/multiverse time status|travel <year>|travel <prime|past|future|alternate|fractured> <year>|present|paradox <0-100>`: time travel with a cinematic. The era changes the sky's hour and the weather. Each jump builds paradox, which fades over time.
- At 60%+ paradox, **TSA agents** (Time and Space Authority) are dispatched after you.
- Flying above the temporal airspace ceiling (Y=300 by default) gets you warned and then arrested. The TSA halts you, pulls you to the ground and leaves agents to watch you.
- `/multiverse tsa status|enable|disable|ceiling <y>|dispatch <p>|arrest <p>|pardon <p>|recall`.

## Models

GeckoLib is bundled inside the jar. The TARDIS (doors swing open when you walk up, the lamp pulses), the DeLorean (wheels spin with speed, gull-wing doors open when you're near and not driving) and the Pruning Staff's **Time Door** are animated GeckoLib models with glowing parts. Every item is a 3D model: the Rupture, staff, DeLorean and TARDIS are sculpted, and all other items are extruded from their pixels.

## Time machines

- **DeLorean** (item, place on the ground):
  - Right-click to drive with WASD. Your speed shows above the hotbar.
  - Sneak-right-click to open the **time circuits**, where you set the destination year and press ARM.
  - Hit **88 MPH** and you get fire trails and a flash, then you arrive in the destination year. The car rematerialises a moment later.
  - Sneak-punch to pick it back up.
- **TARDIS** (item, place it):
  - Right-click to step inside. It's bigger on the inside: a private console room built far out in the Nexus.
  - Use the console to dematerialise. The police box fades out and lands in a random universe and a random year.
  - Sneak-use the console to step out wherever it landed.
- **TSA agents** are now custom mobs (black suits, glowing visors):
  - They blink through time toward their target, and their batons slow and weaken you.
  - At 80%+ paradox a red-trimmed **Temporal Enforcer** leads them.
  - They leave when their warrant expires.

## TSA Temporal Pruning Staff

All abilities depend on how you use it and where you look:

| How you use it | Ability | What it does |
|---|---|---|
| Use | **Prune** | Erases the target from the timeline. Bosses lose 15% of their health instead. |
| Sneak-use | **Temporal Snare** | Freezes everything within 12 blocks for 5 s. |
| Use looking up | **Time Door** | Steps you through a door 14 blocks ahead. |
| Use looking down | **Reset Charge** | Rewinds you to where you were 5 s ago. |
| Sneak-use looking down | **FULL TIMELINE PRUNING** | Ultimate: an expanding wave prunes every hostile within 24 blocks. |

TSA agents carry it as their baton (it doesn't drop).

## Wormholes

`/multiverse wormhole open [archetype]` tears open a swirling wormhole mouth in front of you. Walk into it to start the trip; anyone who walks in with you shares the journey. `/multiverse wormhole enter [archetype]` skips the mouth, and `status` shows how many tunnels are open.

The trip is a real tunnel you walk through. It has four sections:

1. **Event Horizon**: obsidian and amethyst.
2. **The Starfield**: dark walls glittering with lights.
3. **Time Echo**: weathered copper and sculk.
4. **Exit Approach**: white glass and glowstone.

The destination is only revealed when you reach the last section.

A boss bar shows the tunnel's **stability**. It drains steadily, and faster the deeper you go. Random phenomena strike along the way:

- **Gravity inversion**
- **Spacetime debris**
- **Time slips** that knock you back 14 blocks
- **Echoes** that chase you
- **Stability surges** that restore stability and give you speed
- **Shudders** that cost stability

Reach the far end and you arrive at the destination. If stability hits zero, the tunnel collapses and throws you into a random universe. Tunnels clean themselves up afterwards.
