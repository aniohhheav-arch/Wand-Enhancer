# TARDIS — Minecraft (Fabric) mod — scaffold

Target: Minecraft **26.3** (current release), Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Loom 1.19.1 — all confirmed to exist.
**Builds successfully** (JDK 25, Gradle 9.8.1 wrapper, Loom plugin `net.fabricmc.fabric-loom` — the non-remapping one, since 26.x is unobfuscated; GeckoLib `com.geckolib:geckolib-fabric-26.3:5.5.7`). Run: `./gradlew build` with JDK 25.

Plan: GeckoLib-animated police box/rotor/doors; datapack interior dimension; flight/destination logic ported from
`../tardis/src/ship.js` and `worlds.js`; the vortex as a client shader. The web prototype in `../tardis/` is the design reference.
