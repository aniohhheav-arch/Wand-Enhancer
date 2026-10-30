# Infinity Gauntlet: The Six Stones

A NeoForge 1.21.1 mod that recreates wielding the MCU Infinity Gauntlet. Done so far: the gauntlet, the shared
ability/VFX/cinematic frameworks, the complete **Space Stone** (milestone 1) and the complete **Time Stone** (milestone 2).

| Dimensional rupture | Cosmic Gateway | Spatial Prison |
| --- | --- | --- |
| ![rupture](docs/rupture-portal.jpg) | ![gateway](docs/cosmic-gateway.jpg) | ![prison](docs/spatial-prison.jpg) |

| Time Freeze | Temporal Echo | Time Fracture |
| --- | --- | --- |
| ![freeze](docs/time-freeze.jpg) | ![echo](docs/temporal-echo.jpg) | ![fracture](docs/time-fracture.jpg) |

*Screenshots are from the headless test client (Mesa llvmpipe, default "Fancy" graphics).*

## Status

| Area | State |
| --- | --- |
| Gauntlet item: GeckoLib model, emissive stones, animations (idle, insert, charge, cast, snap, overload, per-stone activation) | done |
| Six stone items: animated GeckoLib gems | done (all six can be installed) |
| Ability framework: server-authoritative casting, per-ability configurable cooldowns, synced HUD | done |
| Cinematic VFX engine: ruptures, shockwaves, sphere pulses, implosions, beams, lightning, ground glyphs, links, dissolves, distortion lenses, particles | done |
| Camera system: shake, FOV push, flashes, full-screen dimensional transition (all configurable) | done |
| **Space Stone: all 9 abilities** | done |
| **Time Stone: all 9 abilities**, built on real tick-level time dilation | done |
| Progression: crafting, loot discovery, advancements, testing commands, GameTests | done for the Space and Time Stones |
| Mind, Reality, Power and Soul abilities | not started; the stones install and render, but have no abilities yet |
| Stone combinations, full-gauntlet ability set, the Snap sequence, Consciousness Nexus (Mind + Space) | not started |

The `snap` and `overload` gauntlet animations and the per-stone palettes are already in place for the later milestones.

## Space Stone abilities

Select with **V / C** (next/previous), switch stones with **G**, or hold **R** for the radial ability wheel. Cast by using the gauntlet.

| Ability | What it does |
| --- | --- |
| Dimensional Portal | Opens a spherical dimensional rupture where you aim. A second cast opens its twin and links the pair, in any dimension. Players, mobs, items and projectiles that enter come out of the twin with their momentum; players get the swirling transition overlay. Sneak-cast collapses all your ruptures. |
| Spatial Rift | Tears a whirlpool into the ground under the target. It drags creatures and loose items in, swallows them and drops them out of a rupture high above, then collapses with a pulse. |
| Blink | Dissolves you into spatial energy and moves you up to 12 blocks along your aim. It never places you inside blocks and ends with a shockwave. |
| Portal Barrage | Opens six orbiting portals that each lock onto a nearby enemy, fire three homing space bolts and close one by one. |
| Spatial Compression | Launches a sphere that pulls creatures, items and projectiles inward while it shrinks and brightens. It then detonates in a spatial shockwave. |
| Dimension Swap | Exchanges positions through paired ruptures. You can swap a creature with an anchored creature, with a marker (sneak-cast places one), or with yourself. |
| Cosmic Gateway | Charges, then tears an enormous ringed rupture into the sky above the battlefield, linked to a rupture in front of you. Anything sent through drops from the sky. |
| Spatial Prison | Suspends a creature inside six rotating portal rings. Its escape attempts are snapped back and show as strain on the rings. |
| Universal Teleportation | Gathers you and your allies (team players and your pets) into a great rupture and moves the group to your waypoint (sneak-cast sets it; works across dimensions) or to where you aim. |

## Time Stone abilities

Time manipulation is real, not a Slowness effect. Every affected entity gets a time scale, and its ticks are skipped
at that rate on the server and on every client. A frozen zombie keeps its pose, momentum and AI state and resumes
exactly where it stopped. An arrow in a time field flies at a quarter of its speed. Players move on their own client,
so they are slowed through movement attributes instead. Rewind, Echo and Loop use a recorded movement history.
Only position, rotation and velocity are ever restored, never health, inventories or blocks.

| Ability | What it does |
| --- | --- |
| Time Freeze | A green wave stops time around you (12 blocks by default). Creatures, projectiles and falling items hang motionless with clock rings and flickering afterimages, among suspended emerald motes, then resume with a pulse. |
| Time Slow | A ground field with a turning clock dial; everything inside except you and your allies runs at 25% speed. Stepping out restores normal time at once. |
| Time Acceleration | Speeds up your own time (movement, attack speed, mining, step height) with a trail of green afterimages of your own model. |
| Temporal Bubble | A great sphere, wrapped in rotating gear rings and orbiting clock dials, where time runs at 12%. It is placed so you stay outside it. |
| Temporal Echo | A green ghost of you, drawn with your actual player model, re-walks your last four seconds and pulls hostile mobs onto itself. |
| Rewind | Plays the aimed target's movement backwards (or everything hostile nearby) while its afterimages trail behind. Sneak-cast rewinds yourself. |
| Temporal Loop | A time anomaly on the ground. Creatures that enter it get snapped back to the moment they arrived every 2.5 seconds until they escape or the loop ends. |
| Chronological Barrier | A disc of clockwork rings in front of you. Incoming projectiles decelerate and hang suspended in front of it; cast again to send them all back. |
| Time Fracture | Charges, then shatters the air over the battlefield into a turning pane of cracked time. It pulses nearby creatures into near-stillness, shows afterimages of where they were, and implodes in a green flash. |

## Getting the gauntlet (survival)

* **Gauntlet:** crafted from 6 gold blocks, a netherite ingot and a nether star (shaped: ` N ` / `GUG` / `GGG`).
* **Space Stone (Tesseract):** found in End City treasure chests (20%), stronghold libraries (8%) and ancient cities (4%).
* **Time Stone (Eye of Agamotto):** found in jungle temples (25%), woodland mansions (15%) and desert pyramids (6%).
* **Installing a stone:** right-click the stone onto the gauntlet in your inventory (bundle-style), or sneak-use the gauntlet with the stone in your off hand. Right-click the gauntlet with an empty cursor to take the selected stone back out.
* **Advancements:** the "Infinity" tab includes Infinity, The Tesseract, A Hole in Reality, Folded Space, Between Worlds, Master of Space, The Eye of Agamotto, Za Warudo, Dormammu I've Come to Bargain, Master of Time and I Am Inevitable.

## Testing commands (permission level 2)

```
/gauntlet give full|empty|stone <stone>
/gauntlet cast <ability id>          # ignores cooldowns; uses the held gauntlet or a temporary full one
/gauntlet cooldowns reset [targets]
/gauntlet portals list|clear
/gauntlet vfx <type>                 # preview any VFX primitive in front of you
/gauntlet abilities
```

## Configuration

* **Server** (`serverconfig/infinitygauntlet-server.toml`, per world): portal size and duration, pair limit, cross-dimension linking, what portals may transport, all damage values, gateway height/size, group-teleport radius/range, every Time Stone strength, radius and duration (freeze, slow and bubble time scales, rewind history length, loop period...), PvP (`offensiveAbilitiesAffectPlayers`, off by default), a global cooldown multiplier and every ability's cooldown.
* **Client** (`config/infinitygauntlet-client.toml`): VFX quality (`LOW`…`ULTRA`, scales particle and smoke density), effect draw distance, camera effects on/off, shake intensity, screen flashes, portal transition overlay and the HUD.

No ability in either stone edits terrain.

## Architecture

```
ability/        Ability base, CastResult, AbilityCaster (the single cast path), per-player data, target rules
ability/space/  the nine Space Stone abilities
ability/time/   the nine Time Stone abilities
time/           TimeManager (tick dilation), EntityHistory (movement recording), RewindController
entity/         RupturePortalEntity + PortalNetwork (SavedData links, survive unloads/restarts/dimensions),
                TimedEffectEntity base, rift/compression/prison/barrage-portal/bolt entities
vfx/            server API (Vfx) and the VfxType primitives; EnergyParticleOptions
network/        payloads; all sends skip connections without the mod's channels (fake/test players)
client/time/    ClientTime (client tick dilation), ClientHistory, Afterimages (ghost models), TimeOverlay
client/vfx/     render types, geometry builders (VfxDraw), RuptureDraw, ClientVfxManager, CameraEffects
client/render/  entity renderers and the GeckoLib item renderers
client/gui/     ability HUD, screen effects layer, ability wheel
gametest/       server GameTests (arena structure generated by tools/generate_test_structures.py)
```

Gameplay is decided on the server; clients receive only what they need to draw (`VfxPayload`, `CameraPayload`, `TransitionPayload`). The rupture's volumetric look comes from layered geometry: a refraction haze, a rear shell of smoke puffs, an additive core with counter-rotating swirls, a front shell with outward-billowing tendrils, lightning arcs, and a rim light. The smoke puffs come from one 2×2 atlas, so each rupture's smoke is a single depth-sorted batch. Effect cost scales with the client quality setting. The mod uses no custom shaders; every effect is built from geometry and particles, so it works with or without shader packs.

All textures, GeckoLib geometry/animations and sounds are generated procedurally by `tools/generate_assets.py` (numpy, Pillow and ffmpeg with libvorbis), and the output is committed.

## Building and running

Requires JDK 21.

```bash
./gradlew build                 # jar in build/libs
./gradlew runClient             # dev client
./gradlew runClient -PquickPlayWorld=<save>   # boot straight into a singleplayer world
./gradlew runGameTestServer     # server GameTests (16 tests)
```
