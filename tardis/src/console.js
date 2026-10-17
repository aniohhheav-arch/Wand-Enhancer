// The central console: six stations of physically animated controls (levers, buttons, switches, knobs, sliders, pulls,
// guarded switches) plus live display screens. Every control calls the same action layer the terminal UI uses.
import { THREE, canvasTex, clamp, damp, lerp, pad, fmtYear, fmtDate } from './util.js';
import { G, SYSTEMS, SYSTEM_LABEL, CLOAK_MODES, MAT_STYLES } from './state.js';
import { A, rt, destInfo, say } from './ship.js';
import { DESTS, WORLDS, destKey } from './worlds.js';
import { audio } from './audio.js';

const DISPLAY_COL = ['#46e6ff', '#ffb347', '#6bff8a', '#ff5ce1'];
const accent = () => DISPLAY_COL[G.custom.display % 4];

export const listDests = () => DESTS.filter((d) => !d.secret || G.disc.secrets.unlisted);

function metal(color, rough = 0.4, metalness = 0.85, emissive = 0x000000, ei = 0) { return new THREE.MeshStandardMaterial({ color, roughness: rough, metalness, emissive, emissiveIntensity: ei }); }

export function makeScreen(w, h, draw, hz = 5) {
  const px = 512, py = Math.round(512 * h / w);
  const tex = canvasTex(px, py, () => {});
  const mat = new THREE.MeshBasicMaterial({ map: tex, toneMapped: false });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat);
  let acc = 1;
  mesh.userData.refresh = (dt, force) => { acc += dt; if (acc < 1 / hz && !force) return; acc = 0; const g = tex.userData.ctx; draw(g, px, py); tex.needsUpdate = true; };
  return mesh;
}
const bar = (g, x, y, w, h, v, col, bg = 'rgba(255,255,255,.12)') => { g.fillStyle = bg; g.fillRect(x, y, w, h); g.fillStyle = col; g.fillRect(x, y, w * clamp(v), h); };
function frame(g, W, H, title, col) {
  g.fillStyle = '#04070c'; g.fillRect(0, 0, W, H); g.strokeStyle = col; g.lineWidth = 3; g.strokeRect(4, 4, W - 8, H - 8);
  g.fillStyle = col; g.font = 'bold 26px monospace'; g.textBaseline = 'top'; g.fillText(title, 18, 14);
  g.globalAlpha = 0.25; g.fillRect(18, 48, W - 36, 2); g.globalAlpha = 1;
  if (rt.flicker || (G.ship.consoleCondition < 30 && Math.random() < 0.15)) { g.fillStyle = 'rgba(255,255,255,.08)'; g.fillRect(0, Math.random() * H, W, 6 + Math.random() * 20); }
}
const sysCol = (v) => (v > 60 ? '#6bff8a' : v > 30 ? '#ffb347' : '#ff4a4a');

const SCREENS = {
  nav(g, W, H) {
    const col = accent(), d = G.dest, info = destInfo(); frame(g, W, H, 'NAVIGATION', col);
    g.font = 'bold 24px monospace'; g.fillStyle = '#fff'; g.fillText((d.name || '').slice(0, 34), 18, 62);
    g.font = '20px monospace'; g.fillStyle = col;
    g.fillText(`WORLD   ${info.world.name}`, 18, 104); g.fillText(`PLANET  ${info.world.planet || 'Other reality'}`, 18, 130); g.fillText(`REGION  ${info.world.region || '—'}`, 18, 156);
    g.fillStyle = '#fff'; g.font = 'bold 34px monospace'; g.fillText(fmtYear(d.year), 18, 186);
    g.font = '22px monospace'; g.fillStyle = col; g.fillText(`${pad(d.month)}/${pad(d.day)}  ${pad(d.hour)}:00 local`, 300, 196);
    g.fillText(`DIM  ${d.dim.join(' · ')}`, 18, 232);
    g.fillText('STABILITY', 18, 262); bar(g, 150, 264, 200, 14, info.stability, sysCol(info.stability * 100));
    g.fillText('DANGER', 18, 288); for (let i = 0; i < 5; i++) { g.fillStyle = i < info.danger ? '#ff5a4a' : 'rgba(255,255,255,.15)'; g.fillRect(150 + i * 30, 288, 24, 14); }
    g.fillStyle = col; g.font = '16px monospace'; g.fillText(G.ship.destLocked ? '■ COORDINATES LOCKED' : '□ coordinates free', 340, 288);
    if (info.world.requires && !info.world.requires()) { g.fillStyle = '#ff4a4a'; g.fillText('SHIP REFUSES THESE COORDINATES', 18, 312); }
    else if (rt.events.find((e) => e.id === 'movingdest')) { g.fillStyle = '#ff4a4a'; g.fillText('! DESTINATION IS MOVING THROUGH TIME', 18, 312); }
  },
  flight(g, W, H) {
    const col = accent(), s = G.ship, f = G.flight; frame(g, W, H, 'FLIGHT', col);
    g.font = '22px monospace'; g.fillStyle = '#fff'; g.fillText(`STATE  ${s.phase.toUpperCase()}`, 18, 62);
    const chk = (ok, t, y) => { g.fillStyle = ok ? '#6bff8a' : '#ff6a5a'; g.fillText(`${ok ? '✓' : '✗'} ${t}`, 18, y); };
    g.font = '20px monospace'; chk(s.rotor, 'time rotor', 96); chk(!s.lock, 'temporal lock released', 122); chk(s.throttle >= 0.5, 'flight controls engaged', 148); chk(s.stabilizers >= 0.75, 'stabilizers high', 174);
    g.fillStyle = col; g.fillText('COURSE', 18, 212); bar(g, 130, 214, 360, 16, f.alignment / 100, col); g.fillText('DRIFT', 18, 242);
    g.fillStyle = 'rgba(255,255,255,.15)'; g.fillRect(130, 244, 360, 16); g.fillStyle = Math.abs(f.drift) > 0.45 ? '#ff5a4a' : '#6bff8a'; g.fillRect(310 + f.drift * 170 - 4, 242, 8, 20);
    g.fillStyle = col; g.fillText(`SPEED ${(f.speed || 0).toFixed(1)}`, 18, 282);
    if (s.phase === 'vortex') { g.fillText(`T+${f.t.toFixed(0)}s / ${f.duration.toFixed(0)}s`, 220, 282); g.fillStyle = f.stabilized ? '#6bff8a' : '#ffb347'; g.fillText(f.stabilized ? 'NAV LOCK — MATERIALIZE' : 'ALIGNING…', 18, 312); }
    else { const c = A.canDemat(); g.fillStyle = c.ok ? '#6bff8a' : '#ffb347'; g.font = '16px monospace'; g.fillText(c.ok ? 'READY TO DEMATERIALIZE' : c.why.slice(0, 2).join(' · ').slice(0, 52), 18, 312); }
  },
  power(g, W, H) {
    const col = accent(), s = G.ship; frame(g, W, H, 'POWER · SHIELDS · CLOAK', col);
    g.font = '20px monospace'; g.fillStyle = col; g.fillText('POWER', 18, 70); bar(g, 130, 72, 300, 16, s.power / 100, sysCol(s.power)); g.fillStyle = '#fff'; g.fillText(`${s.power.toFixed(0)}%`, 440, 70);
    g.fillStyle = col; g.fillText(`MODE   ${['ECO', 'NORMAL', 'BOOST'][s.powerMode ?? 1]}`, 18, 108);
    g.fillText(`SHIELDS  ${s.shields ? 'RAISED' : 'DOWN'}`, 18, 140);
    g.fillText(`CLOAK    ${s.cloak.on ? 'ENGAGED' : 'OFF'}`, 18, 172); g.fillStyle = '#fff'; g.fillText(CLOAK_MODES.find((m) => m.id === s.cloak.mode).name, 18, 200);
    g.fillStyle = col; g.fillText('INTEGRITY', 18, 238); bar(g, 150, 240, 280, 14, s.cloakIntegrity / 100, sysCol(s.cloakIntegrity));
    g.fillText('EFFECTIVE', 18, 266); const q = A.cloakQuality(); bar(g, 150, 268, 280, 14, q, sysCol(q * 100)); g.font = '15px monospace'; g.fillText(`environment: ${(WORLDS[G.loc.worldId]?.cloakMod ?? 1) < 0.9 ? 'hostile to cloaking' : 'neutral'}`, 18, 296);
  },
  doors(g, W, H) {
    const col = accent(), s = G.ship; frame(g, W, H, 'DOORS · SCANNER', col);
    g.font = '20px monospace'; g.fillStyle = col;
    g.fillText(`LEFT   ${s.doors.left > 0.5 ? 'OPEN' : 'closed'}`, 18, 70); g.fillText(`RIGHT  ${s.doors.right > 0.5 ? 'OPEN' : 'closed'}`, 18, 98);
    g.fillStyle = s.doors.locked ? '#ffb347' : '#6bff8a'; g.fillText(s.doors.locked ? '■ LOCKED' : '□ unlocked', 18, 126);
    g.fillStyle = col; g.fillText('SCAN', 18, 160);
    const r = rt.scanResult; g.font = '17px monospace'; g.fillStyle = '#fff';
    if (rt.scanPulse > 0) g.fillText('scanning…', 18, 188);
    else if (r) { g.fillText(`${r.found} signature(s) found`, 18, 188); r.items.slice(0, 5).forEach((it, i) => { g.fillStyle = it.hostile ? '#ff6a5a' : '#bfe6ff'; g.fillText(`· ${it.name}`.slice(0, 40), 18, 214 + i * 24); }); }
    else g.fillText('no data', 18, 188);
  },
  diag(g, W, H) {
    const col = accent(), s = G.ship; frame(g, W, H, 'DIAGNOSTICS', col); g.font = '17px monospace';
    SYSTEMS.forEach((k, i) => { const y = 62 + i * 26; g.fillStyle = col; g.fillText(SYSTEM_LABEL[k].slice(0, 18), 18, y); bar(g, 230, y + 2, 200, 14, s[k] / 100, sysCol(s[k])); g.fillStyle = '#fff'; g.fillText(`${s[k].toFixed(0)}`.padStart(3), 440, y); });
    if (rt.events.length) { g.fillStyle = Math.floor(performance.now() / 400) % 2 ? '#ff4a4a' : '#ff9a4a'; g.font = 'bold 18px monospace'; g.fillText(('! ' + rt.events[0].def.name).slice(0, 38), 18, 300); }
  },
  archive(g, W, H) {
    const col = accent(), d = G.disc; frame(g, W, H, 'ARCHIVE', col); g.font = '19px monospace'; g.fillStyle = '#fff';
    const L = [[`Destinations`, Object.keys(d.dests).length], [`Dimensions`, Object.keys(d.dims).length], [`Life forms`, Object.keys(d.lifeforms).length], [`Landmarks`, Object.keys(d.landmarks).length], [`Anomalies`, Object.keys(d.anomalies).length], [`Artifacts`, Object.keys(d.artifacts).length], [`Secrets`, Object.keys(d.secrets).length], [`Journeys`, d.journeys]];
    L.forEach(([a, b], i) => { g.fillStyle = col; g.fillText(a, 18, 64 + i * 28); g.fillStyle = '#fff'; g.fillText(String(b), 250, 64 + i * 28); });
    g.fillStyle = col; g.font = '15px monospace'; g.fillText('Press J for the full archive', 18, 306);
  },
};

// ---------------------------------------------------------------- control factories
function buildControl(spec, mats) {
  const g = new THREE.Group(); g.position.set(spec.x, 0.045, spec.z);
  const t = spec.type, steps = spec.steps || 2;
  const c = { spec, group: g, value: 0, shown: 0, pulse: 0, type: t, update: null };
  const body = mats.ctl, hot = (col, ei = 1.4) => new THREE.MeshStandardMaterial({ color: 0x111111, emissive: col, emissiveIntensity: ei });
  let mover = null;
  if (t === 'lever') {
    g.add(new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.02, 0.34), body));
    const piv = new THREE.Group(); piv.position.y = 0.02; g.add(piv);
    const rod = new THREE.Mesh(new THREE.CylinderGeometry(0.014, 0.018, spec.size || 0.3, 8).translate(0, (spec.size || 0.3) / 2, 0), mats.chrome); piv.add(rod);
    const knob = new THREE.Mesh(new THREE.SphereGeometry(0.04, 12, 10), new THREE.MeshStandardMaterial({ color: spec.color || 0xcc3322, roughness: 0.3, metalness: 0.4 })); knob.position.y = (spec.size || 0.3) + 0.01; piv.add(knob);
    mover = (v) => { piv.rotation.x = lerp(-0.75, 0.75, v); };
  } else if (t === 'button') {
    const r = spec.r || 0.06;
    g.add(new THREE.Mesh(new THREE.CylinderGeometry(r * 1.25, r * 1.35, 0.02, 14), body));
    const capMat = hot(spec.color || 0x2288ff, 0.6); const cap = new THREE.Mesh(new THREE.CylinderGeometry(r, r, 0.04, 14), capMat); cap.position.y = 0.03; g.add(cap);
    mover = (v) => { cap.position.y = 0.03 - c.pulse * 0.025; capMat.emissiveIntensity = 0.4 + (spec.lit ? (spec.lit() ? 2.2 : 0.15) : 0.6) + c.pulse * 2; };
  } else if (t === 'switch' || t === 'guarded') {
    g.add(new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.02, 0.1), body));
    const piv = new THREE.Group(); piv.position.y = 0.02; g.add(piv);
    piv.add(new THREE.Mesh(new THREE.CylinderGeometry(0.011, 0.014, 0.13, 6).translate(0, 0.065, 0), mats.chrome)); const tip = new THREE.Mesh(new THREE.SphereGeometry(0.022, 8, 6), new THREE.MeshStandardMaterial({ color: spec.color || 0xdddddd })); tip.position.y = 0.135; piv.add(tip);
    const led = new THREE.Mesh(new THREE.SphereGeometry(0.014, 8, 6), hot(0x33ff66, 2)); led.position.set(0.07, 0.01, 0); g.add(led);
    let guard = null;
    if (t === 'guarded') { guard = new THREE.Group(); guard.position.set(0, 0.025, -0.07); g.add(guard); guard.add(new THREE.Mesh(new THREE.BoxGeometry(0.15, 0.012, 0.14).translate(0, 0.006, 0.07), new THREE.MeshStandardMaterial({ color: 0xcc2020, transparent: true, opacity: 0.75 }))); }
    mover = (v) => { piv.rotation.x = lerp(-0.55, 0.55, v); led.material.emissive.setHex(v > 0.5 ? 0x33ff66 : 0xff3322); if (guard) guard.rotation.x = -c.guard * 1.7; };
    c.guard = 0;
  } else if (t === 'knob') {
    g.add(new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.11, 0.02, 20), body));
    const k = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.08, 0.06, 20), mats.chrome); k.position.y = 0.04; g.add(k);
    const ind = new THREE.Mesh(new THREE.BoxGeometry(0.012, 0.012, 0.07), hot(0xffaa33, 2)); ind.position.set(0, 0.075, -0.03); k.add(ind);
    mover = (v) => { k.rotation.y = -lerp(-2.3, 2.3, v); };
  } else if (t === 'slider') {
    const len = spec.len || 0.5;
    g.add(new THREE.Mesh(new THREE.BoxGeometry(len + 0.06, 0.016, 0.06), body)); g.add(new THREE.Mesh(new THREE.BoxGeometry(len, 0.02, 0.012), hot(0x223344, 0.5)));
    const h = new THREE.Mesh(new THREE.BoxGeometry(0.05, 0.05, 0.09), new THREE.MeshStandardMaterial({ color: spec.color || 0xddaa22, metalness: 0.6, roughness: 0.3 })); h.position.y = 0.035; g.add(h);
    mover = (v) => { h.position.x = lerp(-len / 2, len / 2, v); };
  } else if (t === 'pull') {
    g.add(new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.03, 0.1), body));
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.04, 0.009, 6, 16), new THREE.MeshStandardMaterial({ color: 0xcc2020, emissive: 0x551010 })); ring.position.y = 0.09; ring.rotation.x = 0; g.add(ring);
    const cord = new THREE.Mesh(new THREE.CylinderGeometry(0.006, 0.006, 0.08, 6).translate(0, 0.04, 0), mats.chrome); g.add(cord);
    mover = () => { ring.position.y = 0.09 - c.pulse * 0.04; };
  } else if (t === 'screen') {
    const sc = makeScreen(spec.w || 0.78, spec.h || 0.5, SCREENS[spec.screen]);
    const stand = new THREE.Group(); stand.add(new THREE.Mesh(new THREE.BoxGeometry(0.06, 0.28, 0.04).translate(0, 0.12, 0), body));
    const bezel = new THREE.Mesh(new THREE.BoxGeometry((spec.w || 0.78) + 0.04, (spec.h || 0.5) + 0.04, 0.03), body); bezel.position.set(0, 0.42, -0.01); stand.add(bezel);
    sc.position.set(0, 0.42, 0.01); stand.add(sc); stand.rotation.x = -0.5; g.add(stand); g.position.y = 0.04;
    c.screen = sc; c.update = (dt, t2, force) => sc.userData.refresh(dt, force);
    return c;
  }
  // hit proxy so interaction stays comfortable regardless of how small the visible part is
  const hit = new THREE.Mesh(new THREE.SphereGeometry(spec.hit || 0.12, 6, 5), new THREE.MeshBasicMaterial({ visible: false })); hit.position.y = 0.05; g.add(hit);
  c.hit = hit;
  const label = () => (typeof spec.label === 'function' ? spec.label() : spec.label);
  hit.userData.interact = { label, hold: spec.hold, run: (o) => { c.pulse = 1; spec.press(o && o.shift ? -1 : 1, o); } };
  c.update = (dt) => {
    c.pulse = Math.max(0, c.pulse - dt * 3.5);
    const target = spec.get ? spec.get() : 0;
    c.shown = damp(c.shown, target, t === 'lever' ? 9 : 14, dt);
    if (t === 'guarded') c.guard = damp(c.guard, G.ship.guard ? 1 : 0, 8, dt);
    mover && mover(c.shown);
  };
  void steps;
  return c;
}

// ---------------------------------------------------------------- the console builder
export function buildConsole(mats) {
  const root = new THREE.Group(); const controls = []; const interactables = [];
  const base = new THREE.Mesh(new THREE.CylinderGeometry(2.35, 2.6, 1.55, 6).translate(0, 0.775, 0), mats.metal); root.add(base);
  const skirt = new THREE.Mesh(new THREE.CylinderGeometry(2.7, 2.8, 0.18, 6).translate(0, 0.09, 0), mats.accent); root.add(skirt);
  const lowGlow = new THREE.Mesh(new THREE.CylinderGeometry(2.38, 2.38, 0.08, 6, 1, true).translate(0, 0.3, 0), mats.glow); root.add(lowGlow);
  const top = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 1.2, 0.7, 6).translate(0, 1.9, 0), mats.metal); root.add(top);
  const stations = [];
  const trap = new THREE.Shape(); trap.moveTo(-0.46, -0.7); trap.lineTo(0.46, -0.7); trap.lineTo(1.0, 0.7); trap.lineTo(-1.0, 0.7); trap.closePath();
  const panelGeo = new THREE.ExtrudeGeometry(trap, { depth: 0.06, bevelEnabled: false }).rotateX(Math.PI / 2);
  const names = ['NAVIGATION', 'FLIGHT', 'POWER · CLOAK', 'DOORS · SCANNER', 'EMERGENCY', 'SYSTEMS'];
  for (let k = 0; k < 6; k++) {
    const st = new THREE.Group(); st.rotation.y = k * Math.PI / 3; root.add(st);
    const slope = new THREE.Group(); slope.position.set(0, 2.3, 1.55); slope.rotation.x = 0.26; st.add(slope);
    slope.add(new THREE.Mesh(panelGeo, mats.panel));
    const lp = new THREE.Mesh(new THREE.PlaneGeometry(0.9, 0.09), new THREE.MeshBasicMaterial({ map: canvasTex(256, 26, (g, w, h) => { g.fillStyle = '#10141a'; g.fillRect(0, 0, w, h); g.fillStyle = '#c9a54b'; g.font = 'bold 17px sans-serif'; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(names[k], w / 2, h / 2 + 1); }) }));
    lp.rotation.x = -Math.PI / 2; lp.position.set(0, 0.075, 0.74); slope.add(lp);
    stations.push({ st, slope });
  }
  const add = (k, spec) => { const c = buildControl(spec, mats); stations[k].slope.add(c.group); controls.push(c); if (c.hit) interactables.push(c.hit); return c; };
  const sy = (s) => G.ship[s];
  const dl = () => listDests();
  const idxOf = () => { const l = dl(); const i = l.findIndex((d) => destKey(d) === destKey(G.dest) && d.name === G.dest.name); return i; };

  // ---- 0 NAVIGATION
  add(0, { type: 'screen', screen: 'nav', x: 0, z: -0.42, w: 0.82, h: 0.52 });
  add(0, { type: 'knob', x: -0.6, z: 0.3, steps: 20, label: () => `Destination selector — ${G.dest.name || 'custom'}`, get: () => { const l = dl(); const i = idxOf(); return i < 0 ? 0 : i / Math.max(1, l.length - 1); }, press: (d) => { const l = dl(); let i = idxOf(); i = (i + d + l.length) % l.length; A.setDestination(l[i]); audio.click('knob'); } });
  const yearBtns = [[-1000, 0xaa3030], [-100, 0xaa3030], [-10, 0xaa3030], [-1, 0xaa3030], [1, 0x2288ff], [10, 0x2288ff], [100, 0x2288ff], [1000, 0x2288ff]];
  yearBtns.forEach(([dy, col], i) => add(0, { type: 'button', x: [-0.78, -0.58, -0.38, -0.18, 0.18, 0.38, 0.58, 0.78][i], z: 0.62, r: 0.055, color: col, hit: 0.09, label: `Year ${dy > 0 ? '+' : '−'}${Math.abs(dy)}`, press: () => { A.shiftYear(dy); audio.click('button'); } }));
  add(0, { type: 'knob', x: -0.28, z: 0.3, label: () => `Time of day — ${pad(G.dest.hour)}:00`, get: () => G.dest.hour / 23, press: (d) => A.setDestField('hour', G.dest.hour + 3 * d) });
  add(0, { type: 'knob', x: 0.02, z: 0.3, label: () => `Month — ${G.dest.month}`, get: () => (G.dest.month - 1) / 11, press: (d) => A.setDestField('month', ((G.dest.month - 1 + d + 12) % 12) + 1) });
  [0, 1, 2].forEach((i) => add(0, { type: 'knob', x: 0.3 + i * 0.22, z: 0.3, steps: 10, label: () => `Dimensional coordinate ${'ABC'[i]} — ${G.dest.dim[i]}`, get: () => G.dest.dim[i] / 9, press: (d) => A.cycleDim(i, d) }));
  add(0, { type: 'button', x: -0.5, z: 0.0, r: 0.085, color: 0xff7a1a, hit: 0.14, label: 'Random destination', press: () => A.randomDestination() });
  add(0, { type: 'switch', x: -0.15, z: 0.0, label: () => (sy('destLocked') ? 'Coordinates: locked (click to unlock)' : 'Lock coordinates'), get: () => (sy('destLocked') ? 1 : 0), press: () => A.toggleDestLock() });
  add(0, { type: 'button', x: 0.2, z: 0.0, r: 0.06, color: 0xaa66ff, label: 'Telepathic circuit — focus on a destination', press: () => { A.telepathic(); } });
  add(0, { type: 'button', x: 0.5, z: 0.0, r: 0.06, color: 0x33ddaa, label: 'Recalibrate navigation', press: () => A.recalibrateNav() });

  // ---- 1 FLIGHT
  add(1, { type: 'screen', screen: 'flight', x: 0, z: -0.42, w: 0.82, h: 0.52 });
  add(1, { type: 'lever', x: -0.6, z: 0.5, size: 0.38, color: 0xff3322, hit: 0.15, label: () => (G.ship.phase === 'landed' ? 'DEMATERIALIZE (pull lever)' : 'Dematerialization lever'), get: () => (['dematerializing', 'vortex'].includes(sy('phase')) ? 1 : 0), press: () => { A.dematerialize(); audio.click('lever'); } });
  add(1, { type: 'button', x: 0.55, z: 0.52, r: 0.1, color: 0x22ee66, hit: 0.15, label: () => (G.flight.stabilized && sy('phase') === 'vortex' ? 'MATERIALIZE (ready)' : 'Materialize'), lit: () => G.flight.stabilized && sy('phase') === 'vortex', press: () => A.materialize() });
  add(1, { type: 'lever', x: -0.6, z: 0.14, label: () => `Time rotor — ${sy('rotor') ? 'ON' : 'off'}`, get: () => (sy('rotor') ? 1 : 0), press: () => A.toggleRotor() });
  add(1, { type: 'lever', x: -0.2, z: 0.14, label: () => `Temporal lock — ${sy('lock') ? 'engaged' : 'released'}`, get: () => (sy('lock') ? 0 : 1), press: () => A.releaseLock() });
  add(1, { type: 'lever', x: 0.2, z: 0.14, label: () => `Flight controls — ${sy('throttle') >= 0.5 ? 'engaged' : 'idle'}`, get: () => sy('throttle'), press: () => A.setThrottle(sy('throttle') >= 0.5 ? 0 : 0.8) });
  add(1, { type: 'slider', x: 0.45, z: 0.14, len: 0.5, label: () => `Flight stabilizers — ${Math.round(sy('stabilizers') * 100)}%`, get: () => sy('stabilizers'), press: (d) => A.setStabilizers(((Math.round(sy('stabilizers') * 4) + d + 5) % 5) / 4), hit: 0.18 });
  add(1, { type: 'knob', x: -0.4, z: -0.13, label: () => `Automation — ${G.settings.automation}`, get: () => ['manual', 'semi', 'auto'].indexOf(G.settings.automation) / 2, press: (d) => { const l = ['manual', 'semi', 'auto']; G.settings.automation = l[(l.indexOf(G.settings.automation) + d + 3) % 3]; audio.click('knob'); } });
  add(1, { type: 'button', x: 0.05, z: -0.1, r: 0.05, color: 0xffee44, label: 'QUICK TRAVEL (automatic sequence)', press: () => A.quickTravel() });

  // ---- 2 POWER / CLOAK
  add(2, { type: 'screen', screen: 'power', x: 0, z: -0.42, w: 0.82, h: 0.52 });
  add(2, { type: 'slider', x: -0.4, z: 0.5, len: 0.5, label: () => `Power regulation — ${['ECO', 'NORMAL', 'BOOST'][sy('powerMode') ?? 1]}`, get: () => (sy('powerMode') ?? 1) / 2, press: (d) => { G.ship.powerMode = (((G.ship.powerMode ?? 1) + d + 3) % 3); audio.click('knob'); }, hit: 0.18 });
  add(2, { type: 'switch', x: 0.15, z: 0.5, label: () => `Shields — ${sy('shields') ? 'raised' : 'down'}`, get: () => (sy('shields') ? 1 : 0), press: () => A.toggleShields() });
  add(2, { type: 'lever', x: 0.6, z: 0.46, size: 0.3, color: 0x9a7aff, label: () => `Cloak — ${sy('cloak').on ? 'ENGAGED' : 'off'}`, get: () => (sy('cloak').on ? 1 : 0), press: () => A.toggleCloak() });
  add(2, { type: 'knob', x: -0.5, z: 0.14, steps: 6, label: () => `Cloak mode — ${CLOAK_MODES.find((m) => m.id === sy('cloak').mode).name}`, get: () => (sy('cloak').mode - 1) / 5, press: (d) => A.cycleCloakMode(d) });
  add(2, { type: 'switch', x: 0.0, z: 0.14, label: () => `Auto emergency cloak — ${G.settings.autoEmergencyCloak ? 'on' : 'off'}`, get: () => (G.settings.autoEmergencyCloak ? 1 : 0), press: () => { G.settings.autoEmergencyCloak = !G.settings.autoEmergencyCloak; audio.click('switch'); } });
  add(2, { type: 'switch', x: 0.35, z: 0.14, label: () => `Cloak: ${A.cloakQuality() > 0.5 ? 'stable' : 'unstable'} — environment & damage affect it`, get: () => (A.cloakQuality() > 0.5 ? 1 : 0), press: () => A.toggleCloak() });

  // ---- 3 DOORS / SCANNER
  add(3, { type: 'screen', screen: 'doors', x: 0, z: -0.42, w: 0.82, h: 0.52 });
  add(3, { type: 'lever', x: -0.6, z: 0.5, size: 0.34, color: 0x3388ff, label: () => ((G.ship.doors.left > 0.5 || G.ship.doors.right > 0.5) ? 'Close both doors' : 'Open both doors'), get: () => ((G.ship.doors.left > 0.5 || G.ship.doors.right > 0.5) ? 1 : 0), press: () => A.toggleDoors() });
  add(3, { type: 'switch', x: -0.2, z: 0.5, label: () => `Left door — ${sy('doors').left > 0.5 ? 'open' : 'closed'}`, get: () => (sy('doors').left > 0.5 ? 1 : 0), press: () => A.toggleDoors('l') });
  add(3, { type: 'switch', x: 0.12, z: 0.5, label: () => `Right door — ${sy('doors').right > 0.5 ? 'open' : 'closed'}`, get: () => (sy('doors').right > 0.5 ? 1 : 0), press: () => A.toggleDoors('r') });
  add(3, { type: 'switch', x: 0.5, z: 0.5, label: () => `Door lock — ${sy('doors').locked ? 'locked' : 'unlocked'}`, get: () => (sy('doors').locked ? 1 : 0), press: () => A.toggleLock() });
  add(3, { type: 'pull', x: 0.0, z: 0.14, label: 'EMERGENCY DOOR OVERRIDE', press: () => A.emergencyRelease() });
  add(3, { type: 'button', x: -0.5, z: 0.14, r: 0.07, color: 0x66ffcc, label: 'Scanner — scan surroundings', press: () => A.scan() });
  add(3, { type: 'switch', x: 0.5, z: 0.14, label: () => `Door behaviour — ${G.settings.doorSafe ? 'SAFE (containment field)' : 'EXPERIMENTAL (no field)'}`, get: () => (G.settings.doorSafe ? 1 : 0), press: () => { G.settings.doorSafe = !G.settings.doorSafe; audio.click('switch'); say(G.settings.doorSafe ? 'The field hums reassuringly.' : 'Do not stand in the doorway.'); } });

  // ---- 4 EMERGENCY
  add(4, { type: 'screen', screen: 'diag', x: 0, z: -0.42, w: 0.82, h: 0.52 });
  add(4, { type: 'guarded', x: -0.65, z: 0.52, color: 0xff2222, hit: 0.14, label: () => (!sy('guard') ? 'Lift guard (DANGEROUS EXPERIMENTAL FLIGHT)' : sy('experimental') ? 'EXPERIMENTAL FLIGHT — armed (click to disarm)' : 'Throw switch: experimental flight'), get: () => (sy('experimental') ? 1 : 0), press: () => A.toggleExperimental() });
  [1, 2, 3].forEach((n, i) => add(4, { type: 'switch', x: -0.12 + i * 0.26, z: 0.52, label: () => `Manual override ${n}`, get: () => (G.disc.seq[G.disc.seq.length - 1] === n ? 1 : 0), press: () => A.override(n) }));
  add(4, { type: 'button', x: -0.55, z: 0.18, r: 0.06, color: 0x33ddaa, label: 'Recalibrate navigation', press: () => A.recalibrateNav() });
  add(4, { type: 'button', x: -0.2, z: 0.18, r: 0.06, color: 0xaa66ff, label: 'Realign dimensions', press: () => A.realignDimensions() });
  add(4, { type: 'button', x: 0.15, z: 0.18, r: 0.06, color: 0xffaa22, hold: 1.5, label: 'Reroute power (hold)', press: () => { A.restartReactor(); } });
  add(4, { type: 'button', x: 0.5, z: 0.18, r: 0.06, color: 0xff5555, label: 'Auto-repair worst system (−8 power)', press: () => { const s = G.ship; if (s.power < 8 && G.mode !== 'sandbox') { audio.click('deny'); return; } let w = 'hull', v = 101; for (const k of SYSTEMS) if (k !== 'power' && s[k] < v) { v = s[k]; w = k; } if (G.mode !== 'sandbox') s.power -= 8; A.repair(w, 25); } });

  // ---- 5 SYSTEMS
  add(5, { type: 'screen', screen: 'archive', x: 0, z: -0.42, w: 0.82, h: 0.52 });
  add(5, { type: 'switch', x: -0.6, z: 0.5, label: () => `Follow mode — ${sy('followMode') ? 'on' : 'off'}`, get: () => (sy('followMode') ? 1 : 0), press: () => A.toggleFollow() });
  add(5, { type: 'button', x: -0.2, z: 0.5, r: 0.07, color: 0xff55aa, label: 'Recall signal (summon TARDIS to you)', press: () => A.recall() });
  add(5, { type: 'switch', x: 0.2, z: 0.5, label: () => `Turbulence — ${G.settings.turbulence ? 'on' : 'off'}`, get: () => (G.settings.turbulence ? 1 : 0), press: () => { G.settings.turbulence = !G.settings.turbulence; audio.click('switch'); } });
  add(5, { type: 'switch', x: 0.55, z: 0.5, label: () => `Cinematic camera — ${G.settings.cinematic ? 'on' : 'off'}`, get: () => (G.settings.cinematic ? 1 : 0), press: () => { G.settings.cinematic = !G.settings.cinematic; audio.click('switch'); } });
  add(5, { type: 'knob', x: -0.45, z: 0.15, steps: 4, label: () => `Display style — ${['cyan', 'amber', 'green', 'magenta'][G.custom.display % 4]}`, get: () => (G.custom.display % 4) / 3, press: (d) => { G.custom.display = (G.custom.display + d + 4) % 4; audio.click('knob'); } });
  add(5, { type: 'knob', x: 0.0, z: 0.15, steps: 3, label: () => `Engine ambience — ${['Classic wheeze', 'Deep drone', 'Choral'][G.settings.engine]}`, get: () => G.settings.engine / 2, press: (d) => { G.settings.engine = (G.settings.engine + d + 3) % 3; audio.click('knob'); } });
  add(5, { type: 'button', x: 0.45, z: 0.15, r: 0.06, color: 0x66ccff, label: 'Telepathic circuit', press: () => A.telepathic() });

  // time rotor column (rotates faster in flight; its discs travel up and down)
  const rot = new THREE.Group(); root.add(rot);
  const glass = new THREE.Mesh(new THREE.CylinderGeometry(0.62, 0.62, 8.5, 24, 1, true).translate(0, 7.4, 0), new THREE.MeshStandardMaterial({ color: 0xaaddff, transparent: true, opacity: 0.2, roughness: 0.05, metalness: 0.1, side: THREE.DoubleSide, depthWrite: false })); rot.add(glass);
  const core = new THREE.Mesh(new THREE.CylinderGeometry(0.16, 0.16, 8.5, 12).translate(0, 7.4, 0), mats.glow); rot.add(core);
  const discs = []; for (let i = 0; i < 5; i++) { const d = new THREE.Mesh(new THREE.TorusGeometry(0.5, 0.06, 8, 24).rotateX(Math.PI / 2), mats.glow); d.userData.ph = i / 5; rot.add(d); discs.push(d); }
  for (const y of [3.0, 11.8]) { const cap = new THREE.Mesh(new THREE.CylinderGeometry(0.85, 1.0, 0.45, 12).translate(0, y, 0), mats.metal); rot.add(cap); }
  const struts = new THREE.Group(); for (let i = 0; i < 6; i++) { const s = new THREE.Mesh(new THREE.CylinderGeometry(0.05, 0.05, 8.8, 6).translate(0.95 * Math.cos(i * 1.047), 7.4, 0.95 * Math.sin(i * 1.047)), mats.chrome); struts.add(s); } rot.add(struts);
  const hatchTop = new THREE.Mesh(new THREE.CylinderGeometry(1.3, 2.2, 3.5, 12).translate(0, 14, 0), mats.metal); rot.add(hatchTop);
  const coreLight = new THREE.PointLight(0xaaddff, 80, 0, 1.4); coreLight.position.y = 7; root.add(coreLight);
  let rotSpeed = 0, rotPhase = 0, rotLevel = 0;
  const update = (dt, t, vis) => {
    for (const c of controls) c.update(dt, t, false);
    rotLevel = damp(rotLevel, G.ship.rotor ? (G.ship.phase === 'vortex' ? 2.2 : G.ship.phase === 'landed' ? 0.5 : 1.6) : 0, 1.5, dt);
    rotPhase += dt * rotLevel * 0.45;
    discs.forEach((d, i) => { const k = ((rotPhase + d.userData.ph) % 1); d.position.y = 3.4 + k * 8; d.rotation.y += dt * (2 + rotLevel * 5); d.scale.setScalar((0.9 + 0.2 * Math.sin(k * 6.28)) * (d.userData.sz || 1)); });
    struts.rotation.y += dt * rotLevel * 0.3;
    mats.glow.emissiveIntensity = (0.6 + rotLevel * 0.9) * (rt.flicker ? (Math.random() < 0.5 ? 0.15 : 1) : 1) * (G.ship.power > 3 ? 1 : 0.2);
    coreLight.userData.base = (30 + rotLevel * 55) * (G.ship.power > 3 ? 1 : 0.3) * (rt.flicker && Math.random() < 0.5 ? 0.2 : 1);
    coreLight.color.copy(vis.color);
    void rotSpeed;
  };
  // layout customisation: rotate which station sits in which slot
  const applyLayout = (off) => stations.forEach((s, k) => { s.st.rotation.y = ((k + off) % 6) * Math.PI / 3; });
  const forceScreens = () => controls.forEach((c) => c.screen && c.screen.userData.refresh(0, true));
  const rotorStyle = (i) => {
    const cfg = [{ glass: 0xaaddff, op: 0.2, disc: 1, core: 0.16 }, { glass: 0xff88ff, op: 0.42, disc: 1.35, core: 0.1 }, { glass: 0x88ff99, op: 0.3, disc: 0.8, core: 0.26 }][i % 3];
    glass.material.color.setHex(cfg.glass); glass.material.opacity = cfg.op; core.scale.set(cfg.core / 0.16, 1, cfg.core / 0.16); discs.forEach((d) => { d.userData.sz = cfg.disc; }); coreLight.userData.tint = cfg.glass;
  };
  discs.forEach((d) => { d.userData.sz = 1; });
  return { group: root, controls, interactables, update, applyLayout, forceScreens, coreLight, stations, rotorStyle, press: (c) => c.hit.userData.interact.run({}) };
}
