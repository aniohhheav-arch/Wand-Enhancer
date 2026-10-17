import { THREE, clamp, damp, lerp, smooth, hashStr, rng, bus, disposeTree, fmtYear } from './util.js';
import { G, saveGame, loadGame, hasSave, resetGame, maybeAutosave, log, discover, unlockRoom, ROOM_DEFS, CLOAK_MODES } from './state.js';
import { audio } from './audio.js';
import { A, rt, say, updateShip, checkProgress, startTurbulenceCorridor, destInfo } from './ship.js';
import { WORLDS, destKey } from './worlds.js';
import { Tardis, XU } from './tardis.js';
import { Interior, INTERIOR_ORIGIN, DOOR_Z, spokeAngle } from './interior.js';
import { Vortex, VORTEX_ORIGIN } from './vortex.js';
import { Player } from './player.js';
import { makeSky, applySky, Portals } from './render.js';
import { UI, applyMode } from './ui.js';
import { skyEnv, WorldBase, std } from './worldkit.js';

const canvas = document.getElementById('view');
const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, powerPreference: 'high-performance' });
renderer.outputColorSpace = THREE.SRGBColorSpace; renderer.toneMapping = THREE.ACESFilmicToneMapping; renderer.shadowMap.enabled = true; renderer.shadowMap.type = THREE.PCFSoftShadowMap; renderer.shadowMap.autoUpdate = false;
renderer.autoClear = true;
const scene = new THREE.Scene(); scene.fog = new THREE.FogExp2(0x000000, 0.001);
const camera = new THREE.PerspectiveCamera(75, 1, 0.05, 1500);

// ---------------------------------------------------------------- objects
const tardis = new Tardis(); scene.add(tardis.group);
const interior = new Interior(); scene.add(interior.group, interior.amb);
const vortex = new Vortex(scene);
const sky = makeSky(); scene.add(sky);
const worldGroup = new THREE.Group(); scene.add(worldGroup);
const sun = new THREE.DirectionalLight(0xffffff, 1); sun.castShadow = true; sun.shadow.bias = -0.0004; sun.shadow.normalBias = 0.03; scene.add(sun, sun.target);
const hemi = new THREE.HemisphereLight(0xffffff, 0x444444, 0.5); scene.add(hemi);
const player = new Player(camera, canvas);
const portals = new Portals(renderer, 512, 512, 4); portals.extQuad.geometry.dispose(); portals.intQuad.geometry.dispose();
const portalGeo = new THREE.PlaneGeometry(0.93, 1.99).translate(0, 1.15, 0); portals.extQuad.geometry = portalGeo; portals.intQuad.geometry = portalGeo;
tardis.doorAnchor.add(portals.extQuad); interior.doorAnchor.add(portals.intQuad); portals.extQuad.visible = portals.intQuad.visible = false;
const cubeRT = new THREE.WebGLCubeRenderTarget(256, { type: THREE.HalfFloatType, generateMipmaps: false }); const cubeCam = new THREE.CubeCamera(0.3, 1500, cubeRT);
const hazeRT = new THREE.WebGLRenderTarget(512, 512, { type: THREE.HalfFloatType }); tardis.hazeMat.uniforms.uTex.value = hazeRT.texture;
tardis.A = A;
let world = null, worldDef = null, t = 0, running = false, hudHidden = false, scannerUntil = 0, scanDone = true, cubeAcc = 0, lastMain = 'interior', styleOverride = null, saveTimer = 0;
const tmpM = new THREE.Matrix4(), tmpV = new THREE.Vector3();
const flash = (c, d) => UI.flash(c, d);

// rain (follows the camera in exterior passes)
const RAIN = 1400; const rainGeo = new THREE.BufferGeometry(); const rainPos = new Float32Array(RAIN * 6); const rainSeed = new Float32Array(RAIN * 3);
for (let i = 0; i < RAIN; i++) { rainSeed[i * 3] = (Math.random() - 0.5) * 50; rainSeed[i * 3 + 1] = Math.random() * 30; rainSeed[i * 3 + 2] = (Math.random() - 0.5) * 50; }
rainGeo.setAttribute('position', new THREE.BufferAttribute(rainPos, 3));
const rain = new THREE.LineSegments(rainGeo, new THREE.LineBasicMaterial({ color: 0xaac4dd, transparent: true, opacity: 0.35, depthWrite: false, fog: false })); rain.frustumCulled = false; rain.visible = false; worldGroup.add(rain);
const scanRing = new THREE.Mesh(new THREE.RingGeometry(0.97, 1, 64).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: 0x66ffee, transparent: true, opacity: 0.8, side: THREE.DoubleSide, depthWrite: false, fog: false })); scanRing.visible = false; scene.add(scanRing);
const sweepRing = new THREE.Mesh(new THREE.RingGeometry(0.97, 1, 64).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: 0xff4040, transparent: true, opacity: 0.6, side: THREE.DoubleSide, depthWrite: false, fog: false })); sweepRing.visible = false; scene.add(sweepRing);
let sweep = { t: -1, timer: 30 };

// ---------------------------------------------------------------- quality
function applyQuality() {
  const q = G.settings.quality, dpr = Math.min(window.devicePixelRatio || 1, q === 'high' ? 2 : q === 'medium' ? 1.5 : 1);
  renderer.setPixelRatio(q === 'low' ? Math.min(dpr, 0.85) : dpr); resize();
  const sm = { low: 512, medium: 1024, high: 2048 }[q]; if (sun.shadow.mapSize.x !== sm) { sun.shadow.mapSize.set(sm, sm); sun.shadow.map && sun.shadow.map.dispose(); sun.shadow.map = null; }
  sun.shadow.camera.left = sun.shadow.camera.bottom = -22; sun.shadow.camera.right = sun.shadow.camera.top = 22; sun.shadow.camera.near = 1; sun.shadow.camera.far = 140; sun.shadow.camera.updateProjectionMatrix();
  portals.rt.samples = q === 'high' ? 4 : q === 'medium' ? 2 : 0; cubeRT.setSize(q === 'low' ? 128 : q === 'medium' ? 256 : 384, q === 'low' ? 128 : q === 'medium' ? 256 : 384);
  rain.geometry.setDrawRange(0, (q === 'low' ? 500 : q === 'medium' ? 900 : RAIN) * 2); cfgQuality = q;
}
let cfgQuality = '';
function resize() {
  const w = window.innerWidth, h = window.innerHeight; renderer.setSize(w, h, false); camera.aspect = w / h; camera.updateProjectionMatrix();
  const pr = renderer.getPixelRatio(); portals.resize(Math.floor(w * pr), Math.floor(h * pr)); hazeRT.setSize(Math.floor(w * pr * 0.5), Math.floor(h * pr * 0.5)); tardis.hazeMat.uniforms.uRes.value.set(Math.floor(w * pr), Math.floor(h * pr));
}
window.addEventListener('resize', resize);

// ---------------------------------------------------------------- per-pass configuration (lights/fog/visibility)
let hideTardisPass = false;
function applyConfig(cfg, camPos, forMain) {
  const inside = cfg === 'interior';
  interior.group.visible = inside; worldGroup.visible = cfg === 'exterior'; vortex.group.visible = cfg === 'vortex' && G.ship.phase === 'vortex';
  tardis.group.visible = !inside && !hideTardisPass && (cfg === 'vortex' || (world && G.ship.phase !== 'vortex')) ; sky.visible = cfg === 'exterior' && !!world;
  if (inside) {
    sun.intensity = 0; hemi.intensity = 0; interior.applyLights(true, G.custom.light);
    scene.fog.color.copy(interior.themeGlow || new THREE.Color(0x222222)).multiplyScalar(0.05); scene.fog.density = 0.0045;
    if (forMain) renderer.toneMappingExposure = 1.15;
  } else {
    interior.applyLights(false, 0);
    if (cfg === 'vortex') {
      sun.intensity = 0; hemi.color.copy(vortex.lightColor); hemi.groundColor.set(0x101030); hemi.intensity = 0.9; scene.fog.color.set(0x000000); scene.fog.density = 0;
    } else if (world) {
      const e = world.env; sun.color.copy(e.sunColor); sun.intensity = e.sunI * (world.def.sunBoost || 1); sun.position.copy(tardis.group.position).addScaledVector(e.sunDir, 60); sun.target.position.copy(tardis.group.position); sun.target.updateMatrixWorld();
      hemi.color.copy(e.hemiSky); hemi.groundColor.copy(e.hemiGround); hemi.intensity = e.hemiI; scene.fog.color.copy(e.fogColor); scene.fog.density = e.fogDensity;
      const wy = world.water ? world.water.y : null;
      if (wy != null && camPos && camPos.y < wy) { scene.fog.color.set(0x0a5a6a); scene.fog.density = 0.05; }
      applySky(sky, e, t); sky.position.copy(camPos || camera.position); renderer.toneMappingExposure = e.exposure;
      rain.position.set((camPos || camera.position).x, 0, (camPos || camera.position).z);
      if (forMain) renderer.toneMappingExposure = e.exposure;
      renderer.shadowMap.needsUpdate = true;
    }
  }
  rain.visible = cfg === 'exterior' && !!world && world.weather.rain > 0;
}

// ---------------------------------------------------------------- worlds
function visitKey(d) { return WORLDS[d.worldId]?.kind === 'dimension' ? d.worldId : `${d.worldId}:${d.year}`; }
function unloadWorld() {
  if (world) { worldGroup.remove(world.group); disposeTree(world.group); world = null; }
  player.up = 1;
}
function fallbackWorld(d) {
  const def = { id: 'fallback', name: 'Safe harbour' }; const w = new WorldBase({ seed: 1, hour: d.hour ?? 12, visit: { collected: {} }, collect() {}, say }, def);
  const floor = new THREE.Mesh(new THREE.CircleGeometry(60, 40).rotateX(-Math.PI / 2), std(0x445544)); floor.receiveShadow = true; w.add(floor); w.radius = 58; w.env = skyEnv(d.hour ?? 12, {}); return w;
}
function loadWorld(d) {
  unloadWorld();
  const def = WORLDS[d.worldId] || WORLDS.earth_london; worldDef = def;
  const key = visitKey(d); const visit = (G.visits[key] ||= { n: 0, collected: {}, land: null });
  const ctx = {
    year: d.year, month: d.month, day: d.day, hour: d.hour, seed: hashStr(key) ^ 0x1234, visit, say, readBook: () => interior.readBook(),
    collect(k, name, lore) { visit.collected[k] = true; discover('artifacts', k, { name, desc: lore }); audio.chime(6); log(`Artifact recovered: ${name}.`, 'sys'); say(lore); checkProgress(); },
  };
  try { world = def.build(ctx, def); } catch (e) { console.error('world generation failed', d, e); log('Destination generation failed — the ship found a safe harbour instead.', 'warn'); world = fallbackWorld(d); }
  worldGroup.add(world.group); worldGroup.add(rain);
  // choose a landing site (persisted per world+year)
  let land = visit.land;
  if (!land || !isFinite(land[0])) {
    const r = rng(hashStr(key + 'land')); let best = null;
    for (let i = 0; i < 30 && !best; i++) {
      const a = r() * 6.28, dd = i === 0 ? 0 : 3 + r() * 6, x = world.spawn.x + Math.cos(a) * dd, z = world.spawn.z + Math.sin(a) * dd + 2;
      const clear = !world.colliders.some((c) => (c.r ? Math.hypot(x - c.x, z - c.z) < c.r + 3.5 : Math.abs(x - c.x) < c.hx + 3.5 && Math.abs(z - c.z) < c.hz + 3.5)) && !(world.blocked && world.blocked(x, z)) && world.groundY(x, z) > -500;
      if (clear) best = [x, z];
    }
    land = best ? [best[0], best[1], world.spawn.yaw] : [world.spawn.x, world.spawn.z, world.spawn.yaw]; visit.land = land;
  }
  const gy = world.groundY(land[0], land[1]); tardis.group.position.set(land[0], gy, land[1]); tardis.group.rotation.set(0, land[2], 0); tardis.group.updateMatrixWorld(true);
  tardis.setDisguise(world.disguise); tardis.setCondIfChanged();
  audio.setEnv(world.ambience); XUcamo();
  hudWorldDirty = true;
}
function XUcamo() { if (!world) return; XU.uCamo.value.copy(world.env.fogColor).lerp(world.env.hemiGround, 0.5); }
let hudWorldDirty = false;

function enterVortexScene() {
  unloadWorld(); vortex.show(true); tardis.group.position.copy(VORTEX_ORIGIN); tardis.group.rotation.set(0, Math.PI / 2, 0); tardis.group.updateMatrixWorld(true);
  vortex.setStyle(G.settings.vortexStyle); vortex.setMode(G.flight.mode); audio.setEnv({ base: 55, pad: 0.04, nf: 400, ng: 0.05, wind: 0, rain: 0 });
}

// ---------------------------------------------------------------- events from ship systems
bus.on('demat', () => { UI.flash('#aaddff', 0.8); if (G.settings.cinematic && player.inside) cinematic = { t: 0 }; });
bus.on('enterVortex', () => { enterVortexScene(); UI.flash('#ffffff', 0.9); vortex.flash(); });
bus.on('materializeStart', (d) => {
  vortex.show(false); rt.rareCue = false;
  G.loc = { worldId: d.worldId, year: d.year, month: d.month, day: d.day, hour: d.hour, dim: [...d.dim] };
  loadWorld(G.loc); styleOverride = rt.forcedLanding ? 4 : null; UI.flash('#ffffff', 1.4);
  if (WORLDS[d.worldId]?.kind === 'dimension') audio.chime(-3);
  if (rt.surprise) { say(rt.surprise); log(rt.surprise, 'warn'); rt.surprise = null; }
});
bus.on('landed', (d) => {
  const key = destKey(d), prev = G.disc.dests[key]; const visit = G.visits[visitKey(d)];
  visit.n++; G.disc.dests[key] = { worldId: d.worldId, name: (d.preset ? d.name : `${WORLDS[d.worldId].name} · ${fmtYear(d.year)}`), year: d.year, month: d.month, day: d.day, hour: d.hour, dim: d.dim, n: (prev ? prev.n : 0) + 1 };
  G.disc.worldsVisited[d.worldId] = true; G.disc.journeys++;
  if (WORLDS[d.worldId].kind === 'dimension') { discover('dims', d.worldId); }
  if (d.worldId === 'graveyard') { discover('secrets', 'unlisted'); say('Here. This is where I come from, and where I go.'); }
  if (d.worldId === 'last') { G.disc.flags.lastWindow = true; }
  if (visit.n > 1) say(['You have been here before. It remembers you.', 'The same place, a different you.', 'It looks smaller each time.'][visit.n % 3]);
  if (rt.forcedLanding) { G.disc.flags.emergencyLanding = true; G.disc.emergencies++; say('That was not a landing. That was an argument.'); rt.forcedLanding = false; }
  rt.autoCloak && (rt.autoCloak = false);
  G.ship.doors.locked = false; log(`Arrived: ${fmtYear(d.year)} — ${WORLDS[d.worldId].name}. Doors unlocked.`, 'sys'); audio.chime(); checkProgress(); saveGame('landed'); G.flight.auto = false;
});
bus.on('alert', (ev) => { if (ev.id === 'turbulence' || ev.id === 'drift' || ev.id === 'overlap') startTurbulenceCorridor(); });
bus.on('discovered', ({ cat, id }) => { if (cat === 'dims') { audio.chime(8); } checkProgress(); });
bus.on('secret', () => { UI.flash('#c8a0ff', 1); checkProgress(); });
bus.on('roomUnlocked', () => { audio.chime(-2); });
bus.on('step', () => audio.tone(player.inside ? 90 : 140, 0.06, 'triangle', 0.05));
bus.on('flip', (d) => { UI.flash('#ff66ff', 0.4); say(d < 0 ? 'Down is somewhere else.' : 'Down is back.'); });
bus.on('stuck', () => recover('Position recovered.'));
bus.on('teleportInterior', ({ pos, yaw }) => { player.setPose(pos.clone().add(INTERIOR_ORIGIN), yaw); player.inside = true; player.onGround = true; });
bus.on('sleep', () => { say('Sleep.'); UI.flash('#000', 1.5); G.loc.hour = 6; if (G.ship.phase === 'landed') { const v = G.visits[visitKey(G.loc)]; loadWorld(G.loc); void v; } snapshotPlayer(); saveGame('sleep'); log('You wake at dawn. The game was saved.', 'sys'); });
bus.on('escapePod', () => {
  if (G.ship.phase !== 'landed') { say('There is nowhere to land a pod in the vortex.'); audio.click('deny'); return; }
  UI.flash('#ff6040', 1); const f = new THREE.Vector3(0, 0, 3.4).applyQuaternion(tardis.group.quaternion);
  player.setPose(tardis.group.position.clone().add(f), tardis.group.rotation.y, 0); player.inside = false; log('Escape pod launched. You are outside, clear of the ship.', 'sys');
});
bus.on('custom', applyCustom);
bus.on('mode', () => {});
function applyCustom() { tardis.applyCustom(); tardis.setSign(G.custom.sign); tardis.setCondIfChanged(); interior.applyTheme(G.custom.theme); interior.console.applyLayout(G.custom.layout); interior.refreshWP(); applyRotor(); vortex.setStyle(G.settings.vortexStyle); if (cfgQuality !== G.settings.quality) applyQuality(); audio.setVolume(G.settings.volume); }
function applyRotor() { const s = G.custom.rotor, c = interior.console; if (c.rotorStyle) c.rotorStyle(s); }
let lastSign = -1; setInterval(() => { if (lastSign !== G.custom.sign) { lastSign = G.custom.sign; tardis.setSign(G.custom.sign); } }, 400);

// recall (summon the TARDIS)
let recall = null;
rt.hooks.recall = () => {
  if (G.ship.phase !== 'landed' || player.inside || recall) { audio.click('deny'); if (player.inside) log('You are already inside.', 'sys'); return; }
  const d = tardis.group.position.distanceTo(player.pos); if (d < 6) { log('The TARDIS is right here.', 'sys'); return; }
  recall = { t: 0, phase: 0 }; G.ship.doors.left = G.ship.doors.right = 0; audio.dematerialize(); log('Recall signal sent.', 'sys');
};
rt.hooks.sandboxTeleport = (d) => { G.loc = { worldId: d.worldId, year: d.year, month: d.month, day: d.day, hour: d.hour, dim: [...d.dim] }; loadWorld(G.loc); audio.materialize(); UI.flash('#fff', 1); G.disc.worldsVisited[d.worldId] = true; if (WORLDS[d.worldId].kind === 'dimension') discover('dims', d.worldId); log(`Sandbox jump: ${WORLDS[d.worldId].name}.`, 'sys'); };

function recover(msg) {
  UI.flash('#ffffff', 0.5); player.setPose(new THREE.Vector3(INTERIOR_ORIGIN.x, 1.0, 7), 0, 0); player.inside = true; player.up = 1; log(msg, 'sys');
}

// ---------------------------------------------------------------- player area
function interiorArea() {
  const o = INTERIOR_ORIGIN;
  return {
    gravity: 1, groundY: (x, z, y) => interior.groundY(x - o.x, z - o.z, y),
    ok: (x, z, y, onGround) => { if (!interior.regionOK(x - o.x, z - o.z)) return false; if (onGround && interior.groundY(x - o.x, z - o.z, y) < y - 1.25) return false; return true; },
  };
}
const tinv = new THREE.Matrix4(); const lp = new THREE.Vector3();
function worldArea() {
  const w = world, r = 0.32;
  return {
    gravity: w.gravity, waterY: w.water ? w.water.y : null, ceilY: w.ceilY, flip: w.ceilY ? { pads: w.flipPads, ceilY: w.ceilY } : null, slow: 1,
    groundY: (x, z, y) => w.groundY(x, z, y),
    ok: (x, z, y, onGround) => {
      if (Math.hypot(x, z) > w.radius) return false;
      for (const c of w.colliders) { if (c.r) { if (Math.hypot(x - c.x, z - c.z) < c.r + r) return false; } else if (Math.abs(x - c.x) < c.hx + r && Math.abs(z - c.z) < c.hz + r) return false; }
      if (w.blocked && w.blocked(x, z)) return false;
      if (G.ship.phase === 'landed') { // solid police box (except through the open doorway)
        lp.set(x, y, z).applyMatrix4(tinv); const open = Math.max(G.ship.doors.left, G.ship.doors.right) > 0.6;
        if (Math.abs(lp.x) < 1.05 && Math.abs(lp.z) < 1.05 && !(open && Math.abs(lp.x) < 0.42 && lp.z > 0.5)) return false;
      }
      if (w.def.id === 'earth_london' && w.hasRiver) { /* river collider is part of world.colliders */ }
      if (onGround && !w.water && w.groundY(x, z, y) < y - 1.25 && w.def.id !== 'void') return false;
      if (onGround && w.def.id === 'void' && w.groundY(x, z, y) < y - 1.25) return false;
      return true;
    },
  };
}

// ---------------------------------------------------------------- interaction
const ray = new THREE.Raycaster(); ray.far = 4.2; let hold = { mesh: null, t: 0 }, target = null, lastTargetMesh = null;
function findTarget() {
  const cands = []; const eye = camera.position;
  if (player.inside) { for (const m of interior.interactables) { if (!m.parent) continue; const wp = m.userData.wp; if (!wp || wp.distanceToSquared(eye) < 36) cands.push(m); } }
  else { for (const m of tardis.hits) cands.push(m); if (world) for (const m of world.interactables) cands.push(m); }
  if (!cands.length) return null;
  ray.setFromCamera({ x: 0, y: 0 }, camera); const hits = ray.intersectObjects(cands, false);
  for (const h of hits) { const it = h.object.userData.interact; if (it && !(it.enabled === false)) return h.object; }
  return null;
}
function doInteract(dt) {
  const frozen = UI.isOpen() || !running; const m = frozen ? null : findTarget(); target = m;
  if (!m) { UI.setPrompt(null); hold.t = 0; hold.mesh = null; return; }
  const it = m.userData.interact, label = typeof it.label === 'function' ? it.label() : it.label; const down = player.keys.KeyE || player.mouseDown[0];
  const alt = player.wasPressed('KeyQ') || player.clicks.includes(2);
  if (it.hold) {
    if (hold.mesh !== m) { hold.mesh = m; hold.t = 0; }
    if (down) hold.t += dt; else hold.t = Math.max(0, hold.t - dt * 2);
    UI.setPrompt(label, hold.t / it.hold);
    if (hold.t >= it.hold) { hold.t = 0; it.run({ shift: false }); }
  } else {
    UI.setPrompt(label, 0, 'E');
    if (player.wasPressed('KeyE') || player.clicks.includes(0) || alt) { audio.start(); it.run({ shift: alt }); }
  }
}

// ---------------------------------------------------------------- scanner
function scannerData() {
  const w = world; const items = [];
  if (w) for (const s of w.scannables) { const d = s.pos.distanceTo(player.inside ? tardis.group.position : player.pos); if (d < (player.inside ? 150 : 70)) items.push({ name: G.disc.lifeforms[s.id] || G.disc.landmarks[s.id] || G.disc.anomalies[s.id] || !s.artifact ? s.name : 'Artifact', type: s.type, dist: d, hostile: !!s.hostile }); }
  items.sort((a, b) => a.dist - b.dist);
  let tard = 'inside the TARDIS';
  if (!player.inside && world) { const d = tardis.group.position.distanceTo(player.pos); const dir = tardis.group.position.clone().sub(player.pos); const bearing = Math.round((Math.atan2(dir.x, -dir.z) * 180 / Math.PI + 360) % 360); const rel = (bearing + Math.round(player.yaw * 180 / Math.PI) + 360) % 360; tard = `${d.toFixed(0)} m — bearing ${bearing}° (${rel > 20 && rel < 340 ? (rel < 180 ? 'turn right' : 'turn left') : 'ahead'})`; }
  return { world: w, atmo: w ? w.atmo : { N2: 0, O2: 0, Ar: 0, other: 100, pressure: 0, temp: -273, hazards: ['Time vortex'] }, temporal: w ? w.temporalNoise + (rt.events.length ? 0.3 : 0) : 1, instab: w ? w.instability + (G.ship.dimAlignment < 50 ? 0.3 : 0) : 0.8, items, tardis: tard };
}
function finishScan() {
  const data = scannerData(); let found = 0;
  if (world) for (const s of world.scannables) {
    const d = s.pos.distanceTo(player.inside ? tardis.group.position : player.pos); if (d > (player.inside ? 150 : 70)) continue;
    const info = { name: s.name, desc: s.desc }; let cat = s.landmark ? 'landmarks' : s.type === 'life' ? 'lifeforms' : s.type === 'structure' ? 'landmarks' : 'anomalies';
    if (discover(cat, s.id, info)) { found++; log(`Scan logged: ${s.name} — ${s.desc}`, 'sys'); }
  }
  rt.scanResult = { found: data.items.length, items: data.items }; if (found) audio.chime(9);
}

// ---------------------------------------------------------------- player/world snapshot for saving
function snapshotPlayer() {
  const p = player.pos; G.player = { inside: player.inside, pos: player.inside ? [p.x - INTERIOR_ORIGIN.x, p.y, p.z - INTERIOR_ORIGIN.z] : [p.x, p.y, p.z], yaw: player.yaw, pitch: player.pitch };
}

// ---------------------------------------------------------------- cinematic camera (optional, skippable by looking)
let cinematic = null; player.cinematicCancel = () => { cinematic = null; };

// ---------------------------------------------------------------- start / load
function startGame(kind, mode) {
  audio.start();
  if (kind === 'new') { resetGame(); G.mode = mode; applyMode(mode, true); } else { loadGame(); }
  applyQuality(); applyCustom(); UI.updateHud();
  if (G.ship.phase === 'vortex') { enterVortexScene(); G.flight.mode ||= 'normal'; } else { G.ship.phase = 'landed'; loadWorld(G.loc); }
  const sp = G.player;
  if (kind === 'continue' && sp && Array.isArray(sp.pos) && sp.pos.every(isFinite)) {
    const o = sp.inside ? INTERIOR_ORIGIN : { x: 0, z: 0 }; player.inside = sp.inside; player.setPose(new THREE.Vector3(sp.pos[0] + (sp.inside ? o.x : 0), sp.pos[1], sp.pos[2] + (sp.inside ? o.z : 0)), sp.yaw, sp.pitch);
    if (!sp.inside && !world) recover('Position recovered.');
  } else { player.inside = true; player.setPose(new THREE.Vector3(INTERIOR_ORIGIN.x, 1.0, 7), 0, -0.1); }
  if (kind === 'new') { setTimeout(() => say('Hello. I have been waiting such a long time.'), 1500); setTimeout(() => log('Walk to the console (E / click to use controls). Or press Tab for the terminal.', 'sys'), 3500); }
  document.getElementById('title').classList.remove('on'); running = true; canvas.focus(); player.requestLock();
  interior.refreshWP();
}

// ---------------------------------------------------------------- main loop
const clock = new THREE.Clock(); let prevDEye = { in: 1e9, out: 1e9 };
function frame() {
  requestAnimationFrame(frame);
  const dt = Math.min(clock.getDelta(), window.__dtMax || 0.05); t += dt;
  try { step(dt); } catch (e) { console.error(e); if (!frame.err) { frame.err = true; log('Internal error recovered.', 'warn'); setTimeout(() => { frame.err = false; }, 3000); } }
}
function step(dt) {
  if (!running) { renderTitle(dt); return; }
  const ui = UI.isOpen();
  if (player.wasPressed('Escape')) { if (ui) UI.close(); else { UI.open('custom'); } }
  if (!ui && player.wasPressed('Tab')) UI.open('nav'); else if (ui && player.wasPressed('Tab')) UI.close();
  if (!ui && player.wasPressed('KeyJ')) UI.open('archive'); else if (ui && player.wasPressed('KeyJ') && UI.tab === 'archive') UI.close();
  if (!ui && player.wasPressed('KeyH')) { hudHidden = !hudHidden; document.getElementById('hud').style.display = hudHidden ? 'none' : 'block'; }
  if (!ui && player.wasPressed('KeyF')) { audio.start(); scannerUntil = t + 14; A.scan(); scanDone = false; }
  if (!ui && player.wasPressed('KeyC')) A.recall();
  if (!ui && player.clicks.length && !player.locked && !hudHidden) { audio.start(); player.requestLock(); }

  rt.playerInside = player.inside;
  updateShip(dt);
  const f = G.flight, s = G.ship, phase = s.phase;
  if (s.phase !== 'vortex' && vortex.group.visible && !world) {} // safety
  if (phase === 'landed' && !world) { loadWorld(G.loc); }

  // ----- exterior animation context
  let dissolve = 0, lamp = 'idle', jitter = 0;
  if (phase === 'dematerializing') { const k = clamp(rt.dematT / 5.5); dissolve = smooth(0.05, 0.98, k); lamp = 'demat'; jitter = k; }
  else if (phase === 'materializing') { const k = clamp(rt.matT / 6); dissolve = 1 - smooth(0.08, 0.95, k); lamp = k < 0.85 ? 'demat' : 'idle'; jitter = 1 - k; }
  else if (phase === 'vortex') { lamp = 'flight'; jitter = 0.6; }
  if (recall) {
    recall.t += dt;
    if (recall.phase === 0) { dissolve = clamp(recall.t / 1.6); lamp = 'demat'; if (recall.t > 1.7) { recall.phase = 1; recall.t = 0; const fwd = new THREE.Vector3(-Math.sin(player.yaw), 0, -Math.cos(player.yaw)); const np = player.pos.clone().addScaledVector(fwd, 6); const gy = world.groundY(np.x, np.z, 50); tardis.group.position.set(np.x, gy, np.z); tardis.group.rotation.y = Math.atan2(player.pos.x - np.x, player.pos.z - np.z); tardis.group.updateMatrixWorld(true); const v = G.visits[visitKey(G.loc)]; if (v) v.land = [np.x, np.z, tardis.group.rotation.y]; audio.materialize(); } }
    else { dissolve = 1 - clamp(recall.t / 2.2); lamp = 'demat'; if (recall.t > 2.3) { recall = null; log('The TARDIS has materialized beside you.', 'sys'); } }
  }
  if (s.followMode && phase === 'landed' && !player.inside && !recall && world && tardis.group.position.distanceTo(player.pos) > 40) A.recall();
  if (styleOverride != null && phase === 'materializing') { /* handled via XU style */ }
  const fl = rt.events.length ? 'emergency' : null; if (phase === 'landed' && !recall) lamp = fl || (s.power < 10 ? 'lowpower' : worldDef && worldDef.kind === 'dimension' ? 'dimension' : 'idle');
  if (phase === 'vortex' && fl) lamp = 'emergency';

  // cloak animation + instability
  const q = A.cloakQuality(); const wantCloak = s.cloak.on ? 1 : 0; const prevC = tardis._cloakVis || 0;
  let cv = prevC + clamp(wantCloak - prevC, -dt * 0.5, dt * 0.45); tardis._cloakVis = cv;
  let cloakShown = cv; if (s.cloak.on && q < 0.35) cloakShown *= 0.35 + 0.65 * (Math.sin(t * 17) * Math.sin(t * 5.3) > -0.2 ? 1 : 0.1); if (rt.events.find((e) => e.id === 'cloakfail')) cloakShown *= (Math.sin(t * 23) > 0 ? 1 : 0.2);
  if (cv < prevC) dissolve = Math.max(dissolve, cv * 0.7); // decloak = reverse materialisation
  XU.uStyle.value = styleOverride != null && phase === 'materializing' ? styleOverride : G.custom.matStyle;
  tardis.update(dt, t, { lamp, lampOn: s.power > 1 || phase !== 'landed', jitter, wet: world ? world.weather.wet : 0, cloak: cloakShown, dissolve, visible: true, interiorLight: 1, exteriorLight: 1, interiorOn: true });
  if (phase === 'vortex') { tardis.group.rotation.set(Math.sin(t * 0.6) * 0.12 + f.drift * 0.2, Math.PI / 2 + f.drift * 0.4, Math.cos(t * 0.5) * 0.1); tardis.group.updateMatrixWorld(true); }
  tardis.group.traverse((o) => { if (o.isMesh && o.castShadow !== undefined && o.userData.cs !== (dissolve < 0.5 && cloakShown < 0.5)) { o.userData.cs = dissolve < 0.5 && cloakShown < 0.5; o.castShadow = o.userData.cs; } });

  // world + vortex updates
  if (world) { const tsc = world.timeScale || 1; for (const u of world.updaters) u(dt * tsc, t, player.pos); }
  if (rain.visible || (world && world.weather.rain > 0)) updateRain(dt);
  if (vortex.group.visible || phase === 'vortex') {
    const mode = rt.events.find((e) => e.id === 'turbulence') ? 'emergency' : f.mode === 'emergency' || f.mode === 'dimensional' ? f.mode : (s.cloakIntegrity < 20 || s.hull < 25) ? 'damaged' : 'normal';
    vortex.setMode(G.flight.experimental ? 'emergency' : mode); vortex.update(dt, f.speed || 1, G.settings.turbulence);
  }
  const vortexCol = vortex.lightColor;
  const flightLevel = phase === 'vortex' ? clamp(0.55 + (f.speed || 0) * 0.2) : phase === 'dematerializing' ? clamp(rt.dematT / 5.5) * 0.85 : phase === 'materializing' ? 0.85 * (1 - clamp(rt.matT / 6)) : 0;
  interior.update(dt, t, player.pos, vortexCol, flightLevel);

  // ----- player movement
  tinv.copy(tardis.group.matrixWorld).invert();
  const area = player.inside ? interiorArea() : (world ? worldArea() : interiorArea());
  const canMove = !ui && !cinematic;
  player.update(dt, area, canMove);
  if (player.inside) {
    const lpos = new THREE.Vector3(player.pos.x - INTERIOR_ORIGIN.x, player.pos.y, player.pos.z - INTERIOR_ORIGIN.z); interior.collide(lpos); player.pos.x = lpos.x + INTERIOR_ORIGIN.x; player.pos.z = lpos.z + INTERIOR_ORIGIN.z;
    if (player.pos.y < -30 || player.pos.y > 80) recover('Position recovered.');
  } else if (world) {
    if (player.pos.y < world.fallY) { say('Caught.'); const f2 = new THREE.Vector3(0, 0, 3.2).applyQuaternion(tardis.group.quaternion); player.setPose(tardis.group.position.clone().add(f2).setY(tardis.group.position.y + 2), tardis.group.rotation.y, 0); UI.flash('#fff', 0.6); player.up = 1; }
  }
  // ----- portal crossing (the bigger-on-the-inside doorway)
  tardis.group.updateMatrixWorld(true); interior.group.updateMatrixWorld(true); portals.frames(tardis, interior);
  crossPortal();

  // ----- camera
  const fxk = { full: 1, reduced: 0.35, off: 0 }[G.settings.camFx] ?? 1;
  const inFlight = player.inside && phase !== 'landed';
  const shake = inFlight ? fxk * (0.004 + flightLevel * 0.012 + rt.shake * 0.03 * (rt.events.length ? 1.4 : 1)) : 0;
  if (cinematic) { cinematic.t += dt; const dy = Math.atan2(player.pos.x - INTERIOR_ORIGIN.x, player.pos.z - INTERIOR_ORIGIN.z); player.yaw += (Math.atan2(Math.sin(dy - player.yaw), Math.cos(dy - player.yaw))) * dt * 1.8; player.pitch = damp(player.pitch, 0.25, 2, dt); if (cinematic.t > 3.2) cinematic = null; }
  const fov = G.settings.fov + (phase === 'dematerializing' || phase === 'materializing' ? 8 * fxk * Math.sin(clamp(phase === 'dematerializing' ? rt.dematT / 5.5 : rt.matT / 6) * Math.PI) : 0) + (phase === 'vortex' && player.inside ? 3 * fxk : 0);
  player.applyCamera(shake, fov);
  camera.updateMatrixWorld(true); camera.matrixWorldInverse.copy(camera.matrixWorld).invert();

  // ----- scanner pulse
  if (rt.scanPulse > 0) { const k = 1 - rt.scanPulse / 2.2; scanRing.visible = !player.inside && !!world; scanRing.position.copy(player.pos).setY(player.pos.y + 0.1); scanRing.scale.setScalar(2 + k * 70); scanRing.material.opacity = (1 - k) * 0.8; if (rt.scanPulse < 0.5 && !scanDone) { scanDone = true; finishScan(); } } else scanRing.visible = false;
  updateHunters(dt);

  doInteract(dt);

  // ----- audio
  const roomIdx = player.inside ? interior.roomAt(player.pos.x - INTERIOR_ORIGIN.x, player.pos.z - INTERIOR_ORIGIN.z) : -9;
  if (roomIdx !== lastRoom) { lastRoom = roomIdx; const hzs = [196, 174, 220, 130, 247, 147, 165, 262, 233, 185, 155, 330, 208, 123, 110, 277, 175]; audio.setRoom(roomIdx >= 0 ? hzs[roomIdx % hzs.length] : 0, roomIdx === 14 ? 1 : 0); }
  audio.update(dt, { inside: player.inside, doorOpen: Math.max(s.doors.left, s.doors.right), flying: phase === 'vortex', flightLevel, rotorOn: s.rotor, vortexVisible: phase === 'vortex', emergency: rt.events.length > 0, power: s.power });
  audio.setAlarm(rt.alarm);

  // ----- render
  render(dt, roomIdx);

  // ----- hud + autosave
  UI.updateHud(); if (scannerUntil > t) UI.scanner(true, scannerData()); else UI.scanner(false);
  saveTimer += dt; if (saveTimer > 12) { saveTimer = 0; if (phase === 'landed' || phase === 'vortex') { snapshotPlayer(); saveGame('auto'); } }
  player.endFrame();
}
let lastRoom = -9;

function crossPortal() {
  const E = portals.E, N = portals.N, s = G.ship; const open = Math.max(s.doors.left, s.doors.right);
  const eye = camera.position;
  if (!player.inside) {
    lp.copy(eye).applyMatrix4(portals.Ei);
    const feet = tmpV.copy(player.pos).applyMatrix4(portals.Ei);
    if (lp.z < 0.14 && prevDEye.out >= 0.14 && Math.abs(lp.x) < 0.44 && feet.y > -0.3 && feet.y < 1.2 && open > 0.6 && s.phase === 'landed') {
      player.applyMatrix(portals.Me2i); player.inside = true; player.onGround = true; player.pos.y = Math.max(player.pos.y, 0); prevDEye.in = 1e9; audio.click('switch');
      if (!G.disc.flags.firstEntry) { G.disc.flags.firstEntry = true; say('Bigger on the inside. It is always bigger on the inside.'); }
    }
    prevDEye.out = lp.z;
  } else {
    lp.copy(eye).applyMatrix4(portals.Ni);
    const feet = tmpV.copy(player.pos).applyMatrix4(portals.Ni);
    if (lp.z < 0.14 && prevDEye.in >= 0.14 && Math.abs(lp.x) < 0.44 && feet.y > -0.3 && feet.y < 1.2 && open > 0.6) {
      if (s.phase === 'landed' && world) { player.applyMatrix(portals.Mi2e); player.inside = false; player.onGround = true; player.pos.y = Math.max(player.pos.y, tardis.group.position.y); prevDEye.out = 1e9; }
      else if (s.phase === 'vortex' && !G.settings.doorSafe) { experimentalCross(); prevDEye.in = lp.z; }
    }
    prevDEye.in = lp.z; if (window.__dbg) window.__dbg.push([lp.z, lp.x, feet.y, open, s.phase, !!world]);
  }
}
let rescueCool = 0;
function experimentalCross() {
  if (performance.now() < rescueCool) return; rescueCool = performance.now() + 6000;
  UI.flash('#ffffff', 1.4); audio.zap(); const roll = Math.random();
  if (roll < 0.35) { say('Gotcha. Never do that again.'); log('Cinematic rescue: the ship caught you at the threshold.', 'warn'); }
  else if (roll < 0.7) { say('The vortex let you go. It does not usually.'); log('Temporal anomaly: you stepped through a moment that never happened and returned.', 'warn'); discover('anomalies', 'threshold', { name: 'Threshold anomaly', desc: 'You crossed into the vortex and were returned.' }); G.ship.temporalIntegrity = Math.max(0, G.ship.temporalIntegrity - 12); }
  else { say('Emergency recovery. Please stop.'); log('Emergency recovery: the ship pulled you back and strained itself.', 'warn'); G.ship.hull = Math.max(0, G.ship.hull - 8); }
  const dir = new THREE.Vector3(0, 0, 1).transformDirection(portals.N); player.pos.addScaledVector(dir, 2.2); player.vel.set(0, 0, 0);
}

function updateRain(dt) {
  const p = rainPos, H = 30; for (let i = 0; i < RAIN; i++) { rainSeed[i * 3 + 1] -= dt * 26; if (rainSeed[i * 3 + 1] < 0) rainSeed[i * 3 + 1] += H; const x = rainSeed[i * 3], y = rainSeed[i * 3 + 1], z = rainSeed[i * 3 + 2]; p[i * 6] = x; p[i * 6 + 1] = y; p[i * 6 + 2] = z; p[i * 6 + 3] = x - 0.03; p[i * 6 + 4] = y + 0.5; p[i * 6 + 5] = z; }
  rain.geometry.attributes.position.needsUpdate = true;
}
function updateHunters(dt) {
  if (!world || !world.hunters || G.ship.phase !== 'landed') { sweepRing.visible = false; return; }
  sweep.timer -= dt;
  if (sweep.timer <= 0 && sweep.t < 0) { sweep.t = 0; sweep.timer = 50 + Math.random() * 30; audio.tone(180, 1.8, 'sine', 0.12, 700); log('A scanner sweep is crossing the area.', 'warn'); }
  if (sweep.t >= 0) {
    sweep.t += dt; sweepRing.visible = true; sweepRing.position.copy(tardis.group.position).setY(tardis.group.position.y + 0.3); sweepRing.scale.setScalar(150 - sweep.t * 40 + 2); sweepRing.material.opacity = 0.5 * (1 - sweep.t / 3.5);
    if (sweep.t > 3.5) {
      sweep.t = -1; sweepRing.visible = false; const s = G.ship;
      if (s.cloak.on) { const m = s.cloak.mode; if (s.defense) log('The sweep hit the defence field and was repelled.', 'sys'); else if ((m === 4 || m === 5) && A.cloakQuality() > 0.3) log('The sweep passed through the TARDIS without registering.', 'sys'); else if (A.cloakQuality() > 0.75 && m !== 1) log('The sweep brushed the cloak. Undetected.', 'sys'); else { s.cloakIntegrity = Math.max(0, s.cloakIntegrity - 22); say('They found us.'); log('Advanced scan detected the ship! Cloak integrity damaged.', 'warn'); if (s.cloakIntegrity < 20) A.startEvent('cloakfail'); } }
    }
  }
}

// ---------------------------------------------------------------- rendering
function render(dt, roomIdx) {
  const s = G.ship, inside = player.inside, open = Math.max(s.doors.left, s.doors.right), phase = s.phase;
  // outside config for passes that look out of / around the TARDIS
  const outCfg = phase === 'vortex' ? 'vortex' : 'exterior';
  const frame = (cam) => { // virtual-camera pass configuration (called by portals.render before drawing)
    applyConfig(inside ? outCfg : 'interior', new THREE.Vector3().setFromMatrixPosition(cam.matrixWorld), false);
  };
  // 1) doorway portal render target
  const quad = inside ? portals.intQuad : portals.extQuad, other = inside ? portals.extQuad : portals.intQuad;
  other.visible = false; let usePortal = false;
  if (open > 0.02 && (inside ? true : (phase === 'landed' && !!world))) { quad.updateMatrixWorld(true); usePortal = portals.inView(camera, quad); }
  quad.visible = usePortal;
  if (usePortal) { hideTardisPass = false; portals.render(camera, scene, inside, frame); }
  // 2) cube map for the windows (console room, observation deck, turbulence room)
  cubeAcc += dt;
  const needCube = inside && (roomIdx === -1 || roomIdx === 7 || roomIdx === 15) && cubeAcc > (cfgQuality === 'low' ? 0.3 : 0.14);
  if (needCube) {
    cubeAcc = 0; hideTardisPass = true; cubeCam.position.copy(tardis.group.position).add(new THREE.Vector3(0, 2.2, 0)); cubeCam.updateMatrixWorld(true);
    applyConfig(outCfg, cubeCam.position, false); renderer.setRenderTarget(null); cubeCam.update(renderer, scene); hideTardisPass = false;
    interior.setCube(cubeRT.texture, new THREE.Matrix3().setFromMatrix4(portals.Mi2e));
  }
  // 3) cloak haze capture (only when the exterior is visible and cloaking/dissolving)
  const hz = !inside && tardis.haze.visible; if (hz) {
    hideTardisPass = true; tardis.haze.visible = false; applyConfig('exterior', camera.position, false); renderer.setRenderTarget(hazeRT); renderer.clear(); renderer.render(scene, camera); renderer.setRenderTarget(null); tardis.haze.visible = true; hideTardisPass = false;
  }
  // 4) final pass
  hideTardisPass = false; applyConfig(inside ? 'interior' : outCfg, camera.position, true); lastMain = inside ? 'interior' : outCfg;
  if (!inside && !world && phase !== 'vortex') { renderer.setClearColor(0x000000); }
  renderer.setRenderTarget(null); renderer.render(scene, camera);
}
function renderTitle(dt) {
  t += dt; // slowly orbit the interior behind the title card
  if (!interior._titleInit) { interior._titleInit = true; applyQuality(); applyCustom(); }
  camera.position.set(INTERIOR_ORIGIN.x + Math.cos(t * 0.1) * 12, 5.5, Math.sin(t * 0.1) * 12); camera.lookAt(INTERIOR_ORIGIN.x, 6, 0); camera.updateMatrixWorld(true); camera.matrixWorldInverse.copy(camera.matrixWorld).invert();
  interior.update(dt, t, camera.position, new THREE.Color(0x9fd0ff), 0.1); applyConfig('interior', camera.position, true); renderer.setRenderTarget(null); renderer.render(scene, camera);
}

// ---------------------------------------------------------------- boot
UI.init();
UI.title((kind, mode) => startGame(kind, mode));
canvas.addEventListener('click', () => { if (running && !UI.isOpen() && !player.locked) { audio.start(); player.requestLock(); } });
window.addEventListener('pointerdown', () => audio.start(), { once: true }); window.addEventListener('keydown', () => audio.start(), { once: true });
bus.on('pointerlock', (l) => { if (!l && running && !UI.isOpen()) setTimeout(() => { if (!player.locked && !UI.isOpen() && running && !document.hidden && performance.now() - lastUI > 400) UI.open('custom'); }, 120); });
let lastUI = 0; bus.on('uiclose', () => { lastUI = performance.now(); setTimeout(() => { if (running) player.requestLock(); }, 30); });
window.addEventListener('beforeunload', () => { if (running) { snapshotPlayer(); saveGame('unload'); } });
document.addEventListener('visibilitychange', () => { if (document.hidden && running) { snapshotPlayer(); saveGame('hidden'); } });
applyQuality(); frame();

// debugging / test hooks
window.TARDIS = { G, A, rt, player, tardis, interior, vortex, UI, THREE, camera, scene, renderer, portals, applyMode, startGame, get world() { return world; }, loadWorld, recover, interiorArea, crossPortal, enterVortexScene, finishScan, saveGame, snapshotPlayer, applyCustom };
