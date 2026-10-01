# Infinite Multiverse

A modular NeoForge mod for **Minecraft 1.21.1** that aims to turn Minecraft into a multiverse sandbox: Stands, cursed techniques, mutant powers, the Mystic Arts, an Infinity Gauntlet, space travel, time travel, portals, the Backrooms, reality-bending instruments, a large weapons arsenal, instant structures, combat arenas and a living city.

**Status: Phase 1 of 8 (Core Foundation).** This release ships the engine every later system plugs into, plus five fully working "Multiverse Core" abilities that exercise it end to end. Nothing in later phases is presented as done. See [ROADMAP.md](ROADMAP.md) for the full plan, feature checklist and known limitations.

> Verified: `./gradlew build` compiles with zero errors and warnings, and a dedicated server boots with the mod loaded and generates its config. In-game client testing (HUD, menu, abilities, VFX) is still pending.

## What Phase 1 contains

| Area | Implementation |
|---|---|
| Mod bootstrap | `@Mod` entrypoints for common and client, deferred registries, config registration |
| Ability engine | Custom synced registry `infinitemultiverse:ability`, server-authoritative activation, instant and toggle abilities, per-ability cooldowns, upkeep drain, damage interception hooks |
| Energy | Shared energy pool with regeneration, a regen pause after spending, and creative bypass; persisted per player and kept on death |
| Player data | NeoForge data attachment (energy, cooldowns, 5-slot loadout, unlocks), with transient toggle state that is never persisted |
| Networking | 4 versioned payloads: activate slot, bind slot (client→server), state sync, visual effect (server→client) |
| Keybinds | Rebindable keys for the menu and five ability slots, in their own Controls category |
| HUD | Energy bar, loadout slots with item icons, cooldown sweep and timer, active-toggle glow, unaffordable tint; anchor to any corner |
| Multiverse menu | Every system with ability counts and roadmap phase, search, ability details (type, cost, upkeep, cooldown, status, binding), loadout binding by click or 1–5, right-click to clear |
| VFX | Custom animated particles (generated sprites) tinted per effect, with quality presets, distance culling, a per-effect particle cap, and vanilla's particle setting honoured |
| SFX | Dedicated sound events per ability with subtitles and pitch variation |
| World safety | `SafeTeleport` validation (collision, world border, build height, loaded chunks, lava/fire), PvP/team/pet-aware targeting, global `allowTerrainModification` switch (off) |
| Configuration | Per-world server config (balance, system toggles, safety) and client config (HUD, VFX); editable in-game via Mods → Config |
| Testing harness | `/multiverse` commands to inspect and manipulate energy, cooldowns, unlocks, bindings and activation |

## Requirements

- Java 21 (JDK)
- Minecraft 1.21.1 with NeoForge 21.1.x (built against `21.1.172`; bump `neo_version` in `gradle.properties` to the newest 21.1.x if needed)
- Install on **both client and server**

## Building and running

```bash
cd infinite-multiverse
./gradlew build          # jar in build/libs/infinitemultiverse-0.1.0-phase1.jar
./gradlew runClient      # dev client with the mod loaded
./gradlew runServer      # dev dedicated server
```

Import the folder into IntelliJ IDEA or Eclipse as a Gradle project; ModDevGradle sets up decompiled Minecraft sources and run configurations.

To regenerate the particle sprites and mod logo after editing the generator:

```bash
python3 tools/generate_textures.py
```

## Installing

1. Install NeoForge 21.1.x for Minecraft 1.21.1.
2. Copy `infinitemultiverse-<version>.jar` into the instance's `mods/` folder (client and server).
3. Launch. Press **K** in game to open the Multiverse menu.

## Controls

All bindings are under *Options → Controls → Key Binds → Infinite Multiverse* and can be rebound.

| Action | Default key |
|---|---|
| Open Multiverse menu | `K` |
| Ability slot 1 | `Z` |
| Ability slot 2 | `V` |
| Ability slot 3 | `B` |
| Ability slot 4 | `N` |
| Ability slot 5 | `G` |

Inside the menu: click a system to browse it, click an ability to inspect it, then click a loadout slot or press `1`–`5` to bind it. Right-click a slot to clear it. Arrow keys move through the list, and `K` or `Esc` closes the menu.

## Abilities (Multiverse Core)

Defaults are shown; every number is configurable per world. Costs and cooldowns are only charged when an ability actually takes effect.

| Ability | Type | Energy | Cooldown | Effect |
|---|---|---|---|---|
| Phase Step | Instant | 18 | 2 s | Blink up to 12 blocks along your gaze to the nearest validated safe spot. Grants brief fall protection. Free if no safe destination exists. |
| Kinetic Leap | Instant | 12 | 1.5 s | Launch along your look direction with guaranteed lift; the next landing within 6 s deals no fall damage. |
| Shockwave | Instant | 30 | 6 s | 5-block radial blast: up to 6 damage (falling to 40% at the edge), outward knockback and lift. Respects PvP, teams and pets. Never breaks blocks. |
| Aegis Field | Toggle | 10 + 8/s | 5 s after it ends | Absorbs 50% of incoming damage for 2 energy per point absorbed. Collapses when energy runs out. Never blocks `/kill` or void damage. |
| Temporal Drag | Instant | 25 | 10 s | Slowness IV and Mining Fatigue II on hostiles within 7 blocks for 5 s; strips 85% of the speed from projectiles you don't own. |

All abilities are unlocked by default. Creative-mode players can use every ability without spending energy (configurable).

## Commands

`/multiverse info` is available to everyone for themselves; everything else needs permission level 2.

| Command | Purpose |
|---|---|
| `/multiverse info [player]` | Energy, loadout, cooldowns, active toggles, unlock count |
| `/multiverse abilities` | List registered abilities by system |
| `/multiverse energy set <players> <amount>` / `energy fill <players>` | Set or refill energy |
| `/multiverse cooldowns reset <players>` | Clear every cooldown |
| `/multiverse unlock <players> <ability\|all>` / `lock <players> <ability>` | Manage unlocks |
| `/multiverse bind <players> <slot 1-5> <ability>` | Bind (and unlock) an ability |
| `/multiverse activate <ability>` | Activate as yourself through the normal pipeline |
| `/multiverse deactivate <players>` | End all active toggles without starting their cooldowns |

## Configuration

| File | Scope | Contents |
|---|---|---|
| `config/infinitemultiverse-server.toml` (a copy in `<world>/serverconfig/` overrides it per world) | Server-wide, synced to clients | Energy pool, per-system enable switches, terrain-modification switch, VFX broadcast range, per-ability cost/cooldown/parameters |
| `config/infinitemultiverse-client.toml` | Per player | HUD on/off, anchor corner and offsets, VFX quality (LOW/MEDIUM/HIGH), max effect distance, particle cap per effect |

Both files can be edited in-game from *Mods → Infinite Multiverse → Config*.

## Architecture

```
com.infinitemultiverse
├── InfiniteMultiverse                 common entrypoint: registries, payloads, configs
├── core
│   ├── MultiverseSystem               the 15 pillars, their colour and roadmap phase
│   ├── ability                        Ability base class, AbilityManager pipeline, targeting, toggle hooks
│   ├── data/PlayerMultiverseData      per-player attachment (persisted + transient state)
│   ├── config/MultiverseConfig        server + client specs
│   ├── network                        payload records and registration
│   ├── registry                       custom ability registry, attachments, sounds, particles
│   ├── vfx                            server-side effect broadcast + sound helper, effect ids
│   ├── world/SafeTeleport             destination validation shared by every teleporting system
│   ├── event/ServerEvents             tick, login/logout, death, damage and fall hooks
│   └── command/MultiverseCommand      test and admin harness
├── abilities.core                     the five Phase 1 abilities
└── client                             keybinds, synced state, HUD layer, menu screen, particles and effect choreography
```

Design rules every later phase follows:

- **The server decides everything.** Clients send only "slot pressed" or "bind slot"; costs, cooldowns, validation and effects run on the server.
- **Effects are named, not streamed.** The server broadcasts an effect id and parameters; each client chooses particle density from its own settings.
- **Nothing persists that can't be cleaned up.** Toggle state is transient, so a crash can never leave a barrier or transformation stuck on.
- **Terrain is never modified by default.** Destructive mechanics must check `safety.allowTerrainModification`.

### Adding an ability

```java
public final class MyAbility extends Ability {
    public MyAbility() {
        super(MultiverseSystem.STANDS, ActivationType.INSTANT, Items.NETHER_STAR, true);
    }

    @Override public float energyCost() { return 20f; }
    @Override public int cooldownTicks() { return 60; }

    @Override
    public boolean activate(AbilityContext ctx) {
        // Validate first and return false (charging nothing) if the ability can't take effect.
        MultiverseVfx.broadcast(ctx.level(), MY_EFFECT_ID, ctx.player().position());
        return true;
    }
}

// Register it from any mod:
DeferredRegister<Ability> ABILITIES = DeferredRegister.create(MultiverseRegistries.ABILITY_KEY, "yourmod");
ABILITIES.register("my_ability", MyAbility::new);
```

Then add `ability.<namespace>.<path>` and `.desc` translations and, for a new look, register a client effect in `ClientVfx`.

## Manual test checklist (Phase 1)

- [ ] Menu opens with `K`, lists all 15 systems, search finds abilities by name, id or system
- [ ] Binding by click, by `1`–`5`, and clearing by right-click all update the HUD immediately
- [ ] Each ability works from its slot key; failure messages appear for empty slot, cooldown, low energy and locked ability
- [ ] Energy regenerates after the regen delay; HUD bar and numbers match `/multiverse info`
- [ ] Phase Step refuses destinations inside walls, lava or beyond the world border and charges nothing when refused
- [ ] Kinetic Leap and Phase Step prevent the next fall's damage
- [ ] Shockwave and Temporal Drag skip teammates, tamed pets, creative players and players when PvP is off
- [ ] Aegis Field drains upkeep, absorbs damage, collapses at zero energy, and is not saved across relog
- [ ] Disabling `systems.core` in the server config blocks activation and shuts off an active Aegis Field
- [ ] VFX quality LOW/MEDIUM/HIGH and vanilla "Particles: Minimal" visibly change particle density
- [ ] Two clients on a dedicated server see each other's effects; state stays correct after death, respawn and dimension change
