# Roadmap, Feature Checklist and Known Limitations

The full design spec is far bigger than one release, so it ships in eight phases. Each phase must be playable and honest: anything not implemented stays unchecked and shows up in game as "scheduled for Phase N" instead of as a fake button.

## Phase plan

| Phase | Scope | Builds on |
|---|---|---|
| **1. Core Foundation** (this release) | Bootstrap, config, ability registry, keybinds, energy and cooldowns, HUD, menu, networking, VFX/SFX framework, safe teleport, test commands | — |
| 2. Supernatural | Stand entity framework and first Stands, cursed energy pool, first cursed techniques plus one full Domain Expansion, mutant powers, Mystic Arts | Ability engine, toggles, damage hooks, VFX |
| 3. Cosmic | Infinity Gauntlet with six stones, combinations and a configurable Snap; portal framework; space foundation; time manipulation | Phase 2 entities, SafeTeleport, dimension registry |
| 4. Technology | Drivable time machine and time circuits, weapon framework and first categories, vehicles, experimental gear | Portal and dimension framework |
| 5. World generation | Build-in-a-block structures, arena generator and editor, Backrooms, remaining dimensions, cosmic environments | Dimension registry, timeline snapshots |
| 6. Sandbox | Civilian NPC simulation, city, fictional police narrative, fictional alchemy, social systems | Arenas' AI navigation, structure generator |
| 7. Musical reality | Instrument framework, keyboard and MIDI input, instrument abilities, the reality-tearing instrument, the Soundscape dimension | VFX geometry, dimension registry |
| 8. Polish | Custom models and animations everywhere, recorded audio, UI refinement, performance passes, multiplayer and compatibility testing | Everything |

## Feature checklist

`[x]` means implemented in code, `[ ]` means not started. Nothing partial is checked.

### Phase 1 — Core Foundation
- [x] Mod initialization (common + client entrypoints)
- [x] Server (per-world, synced) and client configuration, in-game config screen
- [x] Per-system enable/disable switches for all 15 systems
- [x] Synced custom ability registry open to addons
- [x] Instant and toggle activation types, upkeep drain, owner damage interception
- [x] Shared energy pool: max, regeneration, regen delay, creative bypass
- [x] Per-ability cooldowns, persisted; toggles start their cooldown when they end
- [x] Persistent per-player data (attachment) that survives death; transient toggle state
- [x] Five rebindable ability slots + menu key
- [x] Server-authoritative networking (4 payloads, versioned protocol)
- [x] HUD: energy, slots, icons, cooldown sweep, active glow, corner anchoring
- [x] Multiverse menu: systems, search, details, loadout binding
- [x] Custom animated particles with quality presets, distance culling and caps
- [x] Per-ability sound events with subtitles and pitch variation
- [x] Teleport safety validation and friend-or-foe targeting rules
- [x] `/multiverse` testing and admin commands
- [x] Five working core abilities: Phase Step, Kinetic Leap, Shockwave, Aegis Field, Temporal Drag
- [x] Compiles cleanly; dedicated server boots with the mod loaded
- [x] Client tested in game (HUD, icons, Stand rendering and abilities via headless screenshots)
- [x] Original procedural ability icons

### Phase 2 — Supernatural
- [x] Stand framework: GeckoLib entity, summon/dismiss key, follow logic, actions, Stand registry, Awakening Arrowhead, commands
- [x] Star Platinum: model, texture, glow mask, 5 animations, ORA barrage, Star Finger, Guard, The World time stop
- [ ] Stand progression and evolution UI
- [x] The World, Killer Queen, Gold Experience, King Crimson, Made in Heaven, Tusk (models, icons, 18 abilities)
- [x] Time stop as a toggle with ability use inside it, creative-free and scaling survival cooldown
- [x] Stands turn and look with their user
- [ ] Cursed energy pool, reinforcement, Black Flash timing
- [ ] Limitless, Shrine, Ten Shadows, Cursed Spirit Manipulation and other techniques
- [ ] Domain Expansion engine with distinct interiors, rules and collapse sequences
- [ ] Mutant powers (telekinesis, telepathy, elemental, magnetism, reality manipulation, movement powers)
- [ ] Superhero suits with models, abilities and upgrade slots
- [ ] Mystic Arts: Cloak of Levitation, Sling Ring, whips, shields, astral projection, Mirror Dimension

### Phase 3 — Cosmic
- [ ] Infinity Gauntlet item, six stones with individual abilities and ultimates
- [ ] Stone combinations and the Snap (visual-only and safe modes)
- [ ] Portal guns, linked portals, momentum, projectile redirection, loop prevention
- [ ] Dimensional rifts
- [ ] Space foundation: suits, oxygen, gravity
- [ ] Time manipulation abilities

### Phase 4 — Technology
- [ ] Drivable time machine (doors, wheels, steering, dashboard, lights, audio)
- [ ] Time circuits UI and temporal energy
- [ ] Snapshot-based isolated timelines and controlled rewind
- [ ] Time devices (beacon, anchor, lab, generator, scanner, navigation station)
- [ ] Weapon framework: projectile entities or validated raycasts, ammo/energy, recoil, reload, attachments
- [ ] Weapon categories A–G
- [ ] Spacecraft and other vehicles

### Phase 5 — World generation
- [ ] Structure generator block: hologram preview, rotate/mirror, conflict check, staged animated build, undo
- [ ] Custom blueprints and schematic import
- [ ] Arena block, isolated match instances, teams, rounds, modes, AI combatants, map editor
- [ ] Backrooms procedural levels, atmosphere, entities and exploration tools
- [ ] Mirror, Astral, Cosmic Expanse, Void, Temporal Archive, Riftlands, Infinite City, Soundscape dimensions
- [ ] Black hole and wormhole presentation

### Phase 6 — Sandbox
- [ ] Civilian NPCs: identities, routines, navigation, dialogue, memory, emotional state
- [ ] City simulation: traffic, shops, services, events
- [ ] Fictional police narrative with integrity, trust and branching endings
- [ ] Fictional alchemy lab, invented ingredients, discovery log, visual effects
- [ ] Consequence engine (fear, trust, reputation, emergency response)

### Phase 7 — Musical reality
- [ ] Instrument framework and keyboard playing
- [ ] MIDI input (note on/off, velocity, sustain, channel, mapping)
- [ ] Six instruments with abilities, compositions and presets
- [ ] Reality-tearing instrument and the Soundscape dimension

### Phase 8 — Polish
- [ ] Custom 3D models and first/third-person animations for all content
- [ ] Recorded, original audio replacing layered vanilla sounds
- [ ] Graphics presets beyond particles, optional shader effects
- [ ] Multiplayer load testing and mod compatibility testing

## Known limitations

- **Client not yet play-tested.** The build compiles and a dedicated server loads the mod, but the HUD, menu, abilities and effects haven't been exercised in a running client.
- **Placeholder-free, not asset-complete.** Phase 1 has no custom 3D models: abilities use vanilla item icons, and the HUD and menu are drawn procedurally. Particle sprites and the logo are generated by `tools/generate_textures.py`.
- **Sounds reuse vanilla sources.** Each ability has its own sound events, which pitch-shift and layer vanilla sounds until original audio is recorded. A resource pack can replace them now.
- **One energy pool.** Every Phase 1 ability shares the multiverse energy pool. Cursed energy, Stand stamina and similar resources arrive as additional pools in Phase 2.
- **Toggles don't persist.** By design, toggles end on logout and death. They are not restored on login.
- **Protection mods.** Phase Step teleports with vanilla `teleportTo` and doesn't yet fire a teleport event that claim/protection mods could cancel.
- **MIDI (Phase 7)** will use `javax.sound.midi`, which needs the `java.desktop` module. It is present in standard launcher runtimes, but some custom or server-only runtimes lack it, so MIDI will degrade to keyboard play.
- **Time travel (Phase 4)** will use region snapshots loaded into isolated dimensions, not true whole-world rollback. A Minecraft world can't safely be rewound in place without risking corruption, so the original world is never overwritten.
- **Fan content.** Stands carry their canonical JoJo names (by request), but every model, texture and sound is original and generated in `tools/`; no official assets are redistributed. Public release under these names may be taken down. Substances are invented, with no real-world recipes. Social systems contain no sexual content, and violence stays non-graphic.
