# TARDIS — Minecraft (Fabric) mod — scaffold

Target: Minecraft **26.3** (current release), Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Loom 1.19.1 — all confirmed to exist.
**Not yet compiled.** Blockers found: (1) Minecraft 26.3 needs **Java 25**; this sandbox only has Java 21 and could not download
one. (2) No GeckoLib 26.x artifact was found at its Maven path (1.21.11 builds exist), so animation is deferred.
To build locally with JDK 25: `gradle wrapper && ./gradlew build`.

Plan: GeckoLib-animated police box/rotor/doors; datapack interior dimension; flight/destination logic ported from
`../tardis/src/ship.js` and `worlds.js`; the vortex as a client shader. The web prototype in `../tardis/` is the design reference.
