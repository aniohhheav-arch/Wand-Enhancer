# TARDIS — time & dimensional travel simulator

A standalone browser game (Three.js, no build step). It lives beside the Wand patcher and shares no code with it.

Run: `cd tardis && python3 -m http.server 8000` then open http://localhost:8000 (ES modules need http, not file://).

## What works
- Exterior police box (dissolve/materialize shader, 5 styles, cloak haze, roof lamp, key/lock/knock/emergency release).
- Bigger-on-the-inside: a real portal doorway (oblique-clipped render target), seamless in both directions.
- 16-room interior (rooms unlock through play), physical console (≈70 animated controls), time rotor, windows onto the outside.
- Real-time 3D vortex; open the doors during flight (containment field, or experimental mode with rescue events).
- Manual, semi and automatic flight; navigation by date/place/dimension coordinates; emergencies with causes and fixes.
- 4 era-aware worlds (London, Giza, Mars, Kepler) plus 10 dimensions and a procedural-dimension framework (`defineWorld`).
- Cloaking (6 modes), scanner, archive, workshop, customization, sandbox/emergency modes, localStorage saves.
- Terminal (Tab) exposes every console action by keyboard. Audio is fully synthesized.

## Layout
`src/ship.js` actions/flight/emergencies · `console.js` controls · `interior.js` rooms · `worlds.js` destinations · `vortex.js` · `tardis.js` · `render.js` portals/sky · `main.js` loop.

## Known limits
Tested headlessly (software GL) only: no real-GPU performance pass. Ring worlds, many hidden-secret variants and full spatial audio are simplified or absent.
