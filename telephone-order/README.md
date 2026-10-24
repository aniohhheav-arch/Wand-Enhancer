# TELEPHONE ORDER

A Fabric mod for **Minecraft 26.3** (Java 25, Fabric Loader 0.20+, Fabric API 0.165+).

A mysterious weapons corporation takes orders over the telephone. You lift a physical receiver, dial, browse a catalogue of
**40 distinct firearms**, place an order, and a cinematic delivery brings the real, usable weapon to your feet. Secret numbers,
dead lines and a certain Operator keep the catalogue interesting.

> The mod is **firearms-first**: no RPG layer, no magic, no telephone-simulator busywork. The phone is just the doorway to the arsenal.

---

## Install

1. Install Java 25, [Fabric Loader](https://fabricmc.net/use/installer/) for Minecraft **26.3**, and **Fabric API** `0.165.0+26.3` (or newer for 26.3).
2. Drop `telephone-order-<version>.jar` into your `mods/` folder (client **and** server — it is a normal client/server mod).
3. Launch. A **Telephone Order** creative tab contains every weapon, attachment, ammo type and block.

## Build from source

```bash
./gradlew build            # JAR in build/libs/telephone-order-1.0.0.jar
./gradlew runSelftest      # headless integration test on a dedicated server (see "Testing")
python3 tools/gen_assets.py  # regenerate every texture, model, sound, language file and catalog JSON (Pillow, numpy, ffmpeg)
```

Gradle needs a JDK 25 (`JAVA_HOME`). All art and audio are generated procedurally by `tools/` — nothing is borrowed from other games.

---

## Playing

### Getting started
* Craft a **Rotary Desk Phone**, **Desk Terminal** or **Field Uplink** (recipes in the recipe book), place it and **right-click it**.
* You start with 150 credits. Credits come from **depositing** resources on the *ACCOUNT* tab (copper 1 · iron 2 · gold 6 · emerald 20 · diamond 60 · netherite 400),
  killing hostile mobs, and **Credit Chips** found in old chests.
* Browse the **CATALOG** tab, pick a weapon and press **ORDER**. Watch it travel on the **ORDERS** tab, then look up.
* Craft the **Weapons Workbench** to fit attachments to the gun in your hand.

### The three telephones
| Phone | Look | Perk | Ringtone |
|---|---|---|---|
| Rotary Desk Phone | black bakelite, brass dial that turns while you dial | classic | bell |
| Desk Terminal | beige push-button with an animated CRT | −20 % delivery fees | digital |
| Field Uplink | olive rugged field set, antenna, status LED | −15 % delivery time | satellite chirps |

The 3D model is genuinely stateful: the **receiver lifts off** when you pick up, the whole phone **rattles when it rings**, and the dial **turns / keys light up**
for everybody standing nearby while you dial. You can pick a personal ringtone (classic / digital / satellite / retro) on the ACCOUNT tab.

### Controls
| Action | Key |
|---|---|
| Fire (hold for automatic weapons) | **Right mouse** (use item) |
| Reload (from inventory ammo; **off-hand special rounds** load experimental ammo) | **R** |
| Cycle fire mode (auto / semi / burst) | **B** |
| Aim down sights (zoom, tighter spread) | **Left Alt** (hold) |
| Charge weapons (Kiloton, Railgun) | hold right mouse, release to fire |
| Gravity driver push | crouch while holding fire |
All keys are rebindable under *Controls → Telephone Order*.

---

## The arsenal (40 weapons)

| Category | Weapons |
|---|---|
| **Conventional** | Vesper P9 pistol · Hawk .357 revolver · Rattler SMG · Warden AR-4 · Anvil BR-7 battle rifle · Mesa DMR · Longshot L96 sniper · Pumpjack 12 pump shotgun (loads shell by shell) · Auto-Twelve · Brickhouse LMG (100 rds) · Pocket PDW |
| **Tactical** | Whisper-9 integrally suppressed SMG · Triad burst rifle · Goliath .50 anti-materiel (pierces armour, mobs and thin cover) · Hornet MP machine pistol · Hammerhead belt-fed SAW (200 rds) · Chameleon modular carbine (every slot) |
| **Sci-Fi** | Sunder PR-1 plasma rifle · Lance L-1 laser · Meridian railgun (charge + pierce) · Pulse Carbine (4-ring bursts) · Tesla Arc (chain lightning) · Frostbite cryo projector · Graviton Driver (pull / push) · Seeker SW (lock-on microrockets) · Scatterbolt (bouncing plasma shotgun) · Kiloton Charger (chargeable particle cannon) · Aegis Emitter (deflecting energy bubble) · Rift Gun (collapsing space) |
| **Heavy** | Stormcaller rocket launcher · Obliterator heavy energy cannon · Gauss Mortar (EMP shells) · Prism Lance (sustained ramping beam) · Sentinel Turret Kit (deployable sentry) |
| **Classified** | Prototype X-13 (unstable core) · Paradox Revolver (every shot echoes) · Void Whisper (phases through walls) · Ember Judgment (hellfire shotgun) · Daybreak Lance (stronger in daylight) · The Black Ledger (marks targets: +50 % damage) |

Every gun has: its own model (with recoil / reload frames), fire & reload sounds, magazine, recoil, spread, fire modes, headshot / limb multipliers,
armour-piercing value, muzzle flash, smoke, shell casings and tracers (ballistic) or heat / cells / charge / energy trails (sci-fi). Energy weapons build **heat** and can
**overheat**. Aiming, crouching, moving and jumping all change spread.

### Ammo & special rounds
Standard ammo (9mm, .357, 5.56, 7.62, 12 ga, .50 BMG, rockets, energy cells, fusion cores) lives in your inventory and reloads automatically.
Hold **special rounds** in your **off-hand** and press R to load a magazine of: **Incendiary**, **Armor-Piercing**, **Explosive** (no block damage) or **Frost**.

### Attachments (visibly change the model **and** the stats)
Optics (red dot, holo, 4x, 8x) · muzzle (suppressor, brake, focusing lens) · magazines (extended, drum, quickdraw) · grips (foregrip, gyro stabiliser) ·
stocks · laser sight · flashlight · energy modules (amplifier, overclock, heatsink, cell extender) · cosmetic finishes (gold, arctic, crimson).
Not everything fits everything: a pistol has no stock, a sniper cannot take a drum, energy weapons take lenses and modules, and so on. Use the **Weapons Workbench**
for a rotating preview with live stat comparison.

---

## Progression & secrets

Clearance is earned by **credits spent**: *Standard* (0) → *Advanced* (400) → *Experimental* (2 000) → *Prototype* (6 000). Basic guns are never locked behind a grind.
*Classified* weapons are not for sale — they only appear after you find their line:

* **A lab that wants proof of loyalty** — a number the Receptionist can point you at, once you have spent 1 000 credits.
* **A dead number** — dial it and hang up. Some time later, a phone nearby will ring. Answer it.
* **Three area codes** — the right number only works from the right dimension (and the Overworld line needs open sky *and* daylight).
* **Seven torn slips** — each hides one digit of a lost seven-digit number. They turn up in old chests across the world.
* The directory on the DIAL tab is incomplete on purpose. Ask the Receptionist.

Special events: classified orders may arrive with **corrupted confirmations**, and the phone may **ring after you hang up** — the Operator would like a word.

### Cinematic deliveries
Crate (free-fall with smoke trail and impact shockwave) · Courier drone (hovers and winches the cargo down) · Reinforced supply pod (fiery re-entry, sonic boom, crater shockwave) ·
Teleportation container (scan lines, flicker, flash) · Containment case through a dimensional rift (lightning, collapsing portal, camera pulses).
Every delivery uses real **positional audio**, particle effects, **camera shake** and screen flashes, then drops the actual items (weapon, starter ammo and a **collectable Order Receipt**).
Items glow and are reserved for you for five minutes.

---

## Multiplayer & configuration
* All damage, ammo, reloading, projectiles, deliveries and orders are **server-authoritative**; clients send only intents (reload, mode, aim, UI actions) which are validated.
* `config/telephoneorder.json`: damage multiplier, headshot multiplier, credit multiplier, starting credits, delivery time multiplier, explosions-break-blocks, kill bounties,
  clue loot, and client options **screenShake**, **shakeIntensity**, **reduceCameraMotion** (disables shake, recoil kick and scope sway), **recoilVisual**, **particleScale**, **hud**.

## Testing
`./gradlew runSelftest` starts a dedicated server with a fake player that fires **every weapon** at a target, reloads each one, fits attachments, dials every secret number,
places orders and runs **all five delivery sequences**, printing `SELFTEST RESULT: PASS/FAIL` (see `run-selftest/logs`).

## Credits & licence
Original art, sound and code. MIT licensed.
