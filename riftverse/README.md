# Riftverse: Portals, Rifts & the Multiverse

> *You have discovered technology capable of breaking the boundaries between universes.*

A cinematic NeoForge mod for **Minecraft 1.21.1** about portals, dimensional rifts, black holes and an endless multiverse,
including universes you create just by describing them.

---

## Features

### Portals and rifts
- **Natural rifts** tear open across the Overworld and inside other universes. There are eighteen rift types (Azure,
  Crimson, Verdant, Void, Prismatic, Nexus, Glitch, Stellar, Return, Solar, Abyssal, Fungal, Sanguine, Brass, Saccharine,
  Tempest, Umbral and Patina), each with its own colours, shader animation and destination family. A rift lenses the world behind it, glows onto the ground and distorts your screen as you approach.
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
The **Nexus** is a hub dimension floating around a captive singularity. It has 28 gates (one per prime reality),
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
All universes are generated analytically. There are **28 prime archetypes**, each mutated into infinite variants:
Neon Sprawl, Xenoflora Wilds, Skyshatter Isles, Thalassic Expanse, Prismatic Reach, Ashen Remnant, Astral Vastness,
Corrupted Sector, Elder Dominion, Inverted Heights, The Hollow Dark, Somnium, Cinder Forge, Rime Eternal, Sunscar Dunes,
Rust Mesa, Coral Shallows, Myco Hollows, Clockwork Reach, Sanguine Expanse, Confection, Tempest Reach, Fenrot Mire,
Obsidian Spires, Verdigris Ruins, Radiant Expanse, Ferrous Wastes and Spectral Bloom.
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
- **Astral Jelly**, **Sky Whale** and **Lumen Strider**: peaceful drifters, sky leviathans and lantern-bearing grazers.
- **Neon Drone**, **Glitchling**, **Void Stalker**, **Crystal Sentinel** and **Rift Wraith**: hostile and dimensional.
- **The Rift Warden** is a three-phase boss summoned at the Rift Altar. It fires bolt barrages, makes aerial slams,
  summons wraiths and void singularities, and in its final phase sweeps a reality-cutting beam.
- **The Abyssal Leviathan** is a colossal sky-serpent whose body follows the path its head took.

All creatures are built from procedural, animated geometry with emissive details and their own synthesised voices.

---

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

## Configuration (`config/riftverse-common.toml`)
`blackHolesBreakBlocks`, `blackHolesBreakBlocksInVanillaDimensions`, `naturalRiftRarity`,
`maxPromptUniversesPerPlayer`, `universeCreatureSpawning`.

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
