// Destination registry + generators. Worlds are data-driven: defineWorld({id, name, kind, build(ctx)}) is all it
// takes to add a destination or dimension. Time-dependent worlds (Earth, Mars, Kepler) pick an "era" from ctx.year.
import { G } from './state.js';
import { THREE, clamp, lerp, smooth, rng, hashStr, canvasTex, fmtYear } from './util.js';
import {
  WorldBase, skyEnv, customEnv, buildingMat, std, glow, instanced, terrain, flatten, noise2, fbm2,
  makeDino, makeJelly, makeBot, makePlant, signMesh,
} from './worldkit.js';

export const WORLDS = {};
export const DESTS = [];
export const defineWorld = (d) => { WORLDS[d.id] = d; return d; };
export const dimensionByKey = (k) => Object.values(WORLDS).find((w) => w.kind === 'dimension' && w.dimKey && w.dimKey.join() === k.join());
export const destKey = (d) => (WORLDS[d.worldId]?.kind === 'dimension' ? d.worldId : `${d.worldId}:${d.year}`);
export function resolveDest(d) {
  const byKey = dimensionByKey(d.dim || [0, 0, 0]);
  if (byKey) return { worldId: byKey.id };
  const w = WORLDS[d.worldId];
  if (w && w.kind === 'dimension') return w.dimKey.join() === d.dim.join() ? { worldId: w.id } : { worldId: 'earth_london' };
  return { worldId: w ? d.worldId : 'earth_london' };
}

const D = new THREE.Object3D();
const V = (x, y, z) => new THREE.Vector3(x, y, z);
const glowPts = (() => { let t; return () => t ||= canvasTex(64, 64, (g) => { const r = g.createRadialGradient(32, 32, 0, 32, 32, 32); r.addColorStop(0, 'rgba(255,255,255,1)'); r.addColorStop(0.3, 'rgba(255,255,255,.4)'); r.addColorStop(1, 'rgba(255,255,255,0)'); g.fillStyle = r; g.fillRect(0, 0, 64, 64); }); })();
function halos(w, positions, color, size = 2.2, opacity = 0.9) {
  const g = new THREE.BufferGeometry(); g.setAttribute('position', new THREE.Float32BufferAttribute(positions.flatMap((p) => [p.x, p.y, p.z]), 3));
  const p = new THREE.Points(g, new THREE.PointsMaterial({ map: glowPts(), color, size, transparent: true, opacity, depthWrite: false, blending: THREE.AdditiveBlending, sizeAttenuation: true }));
  p.frustumCulled = false; w.add(p); return p;
}
function crowd(w, n, cols, speed, laneZ, xr, scale = 1) {
  const geo = new THREE.CapsuleGeometry(0.22 * scale, 1.0 * scale, 3, 6);
  const people = Array.from({ length: n }, () => ({ x: (w.r() - 0.5) * xr * 2, z: laneZ + (w.r() - 0.5) * 6, dir: w.r() < 0.5 ? 1 : -1, sp: speed * (0.6 + w.r() * 0.8), ph: w.r() * 6 }));
  const im = instanced(geo, std(0xffffff), n, (i) => cols[i % cols.length]); im.frustumCulled = false; w.add(im);
  w.updaters.push((dt, t) => {
    people.forEach((p, i) => { p.x += p.dir * p.sp * dt; if (Math.abs(p.x) > xr) p.dir *= -1; D.position.set(p.x, 0.75 * scale + Math.abs(Math.sin(t * 5 + p.ph)) * 0.05, p.z); D.rotation.set(0, p.dir > 0 ? Math.PI / 2 : -Math.PI / 2, 0); D.scale.set(1, 1, 1); D.updateMatrix(); im.setMatrixAt(i, D.matrix); });
    im.instanceMatrix.needsUpdate = true;
  });
  w.scan('Inhabitants', 'life', 'Local residents going about their day. They do not look twice at the box.', V(8, 1, laneZ));
}
function rowOfBuildings(w, o) {
  const { era, zs, xr, hMin, hMax, mat, cols, roof, skip = 11 } = o;
  const list = [];
  for (const z0 of zs) { let x = -xr; while (x < xr) { const wd = o.wMin + w.r() * (o.wMax - o.wMin); const cx = x + wd / 2; if (Math.abs(cx) > skip || Math.abs(z0) > 20) list.push({ cx, cz: z0 + (w.r() - 0.5) * 2, wd, dp: o.dp + w.r() * 3, h: hMin + w.r() * (hMax - hMin) }); x += wd + (o.gap ?? 0.4) + w.r() * 1.2; } }
  const im = instanced(new THREE.BoxGeometry(1, 1, 1), mat, list.length, (i, d) => { const b = list[i]; d.position.set(b.cx, b.h / 2, b.cz); d.scale.set(b.wd, b.h, b.dp); return cols[Math.floor(w.r() * cols.length)]; }, { shadow: false });
  w.add(im); list.forEach((b) => w.box(b.cx, b.cz, b.wd / 2, b.dp / 2));
  if (roof) {
    const rm = instanced(new THREE.ConeGeometry(0.75, 1, 4).rotateY(Math.PI / 4), std(roof.color, { roughness: 0.9 }), list.length, (i, d) => { const b = list[i]; d.position.set(b.cx, b.h + roof.h / 2, b.cz); d.scale.set(b.wd * 1.05, roof.h, b.dp * 1.2); }); w.add(rm);
    if (roof.chimneys) { const cm = instanced(new THREE.BoxGeometry(0.6, 2, 0.6), std(0x3a2a24), list.length, (i, d) => { const b = list[i]; d.position.set(b.cx + b.wd * 0.25, b.h + roof.h * 0.55, b.cz); }); w.add(cm); }
  }
  return list;
}
function lamps(w, xr, step, zs, color = 0xffd890, h = 4.2) {
  const pos = []; for (const z of zs) for (let x = -xr; x <= xr; x += step) if (Math.abs(x) > 3 || Math.abs(z) > 4) pos.push(V(x + (w.r() - 0.5), h, z));
  w.add(instanced(new THREE.CylinderGeometry(0.06, 0.09, h, 6), std(0x1b1b1b, { metalness: 0.5 }), pos.length, (i, d) => d.position.set(pos[i].x, h / 2, pos[i].z)));
  w.add(instanced(new THREE.SphereGeometry(0.22, 8, 6), glow(color, 3), pos.length, (i, d) => d.position.copy(pos[i])));
  halos(w, pos, color, 3.6, 0.8);
}
function trees(w, n, minR, maxR, colorTrunk, colorTop, hh = 5, sc = 1, collide = true) {
  const pts = []; for (let i = 0; i < n; i++) { const a = w.r() * 6.28, r = minR + w.r() * (maxR - minR); pts.push({ x: Math.cos(a) * r, z: Math.sin(a) * r, s: (0.7 + w.r() * 0.9) * sc }); }
  w.add(instanced(new THREE.CylinderGeometry(0.18, 0.3, hh, 6), std(colorTrunk), n, (i, d) => { const p = pts[i]; d.position.set(p.x, w.groundY(p.x, p.z) + hh * p.s / 2, p.z); d.scale.setScalar(p.s); }, { shadow: true }));
  w.add(instanced(new THREE.IcosahedronGeometry(1.7, 1), std(colorTop, { flatShading: true }), n, (i, d) => { const p = pts[i]; d.position.set(p.x, w.groundY(p.x, p.z) + hh * p.s + 0.7 * p.s, p.z); d.scale.setScalar(p.s); }, { shadow: true }));
  if (collide) pts.forEach((p) => w.circle(p.x, p.z, 0.45 * p.s));
}
function skyline(w, n, minR, maxR, hMin, hMax, mat, cols) {
  const im = instanced(new THREE.BoxGeometry(1, 1, 1), mat, n, (i, d) => { const a = w.r() * 6.28, r = minR + w.r() * (maxR - minR), h = hMin + w.r() * (hMax - hMin), s = 10 + w.r() * 24; d.position.set(Math.cos(a) * r, h / 2, Math.sin(a) * r); d.scale.set(s, h, s); return cols[i % cols.length]; });
  w.add(im); return im;
}
function weatherFor(ctx, rainBias) { const r = rng(hashStr(`${ctx.year}-${ctx.month}-${ctx.day}`)); const rain = r() < rainBias ? 0.4 + r() * 0.6 : 0; return { rain, overcast: rain > 0 ? 0.6 + r() * 0.3 : r() < 0.25 ? 0.35 : 0, r }; }

// ------------------------------------------------------------------ EARTH (London / Thames), era from year
const eraOf = (y) => (y < -1_000_000 ? 'prehistoric' : y < 1100 ? 'ancient' : y < 1600 ? 'medieval' : y < 1900 ? 'victorian' : y < 2100 ? 'modern' : y < 100000 ? 'neon' : 'ruin');
const inWindow = (c, y, m, d, y2, m2, d2) => c.year >= y && c.year <= y2 && (c.year === y ? c.month * 40 + c.day >= m * 40 + d : true) && (c.year === y2 ? c.month * 40 + c.day <= m2 * 40 + d2 : true);

function buildEarthLondon(ctx, def) {
  const w = new WorldBase(ctx, def), era = eraOf(ctx.year);
  const wx = weatherFor(ctx, { prehistoric: 0.3, ancient: 0.5, medieval: 0.5, victorian: 0.6, modern: 0.4, neon: 0.7, ruin: 0.3 }[era]);
  const fire = inWindow(ctx, 1666, 9, 2, 1666, 9, 5), comet = [1066, 1301, 1531, 1607, 1682, 1759, 1835, 1910, 1986].includes(ctx.year);
  w.weather.rain = wx.rain; w.weather.wet = wx.rain; w.eraName = era;
  const pal = {
    prehistoric: { dayTop: 0x3a6fa8, dayBot: 0xe0c89a, ground: 0x2b3a1e, fog: 0.006 }, ancient: { fog: 0.005 }, medieval: { fog: 0.008, dayBot: 0xc8cfd6 },
    victorian: { fog: 0.016, dayBot: 0xa8a49a, dayMid: 0x8a93a0, dayTop: 0x5a6a85, grey: 0x6a645a }, modern: { fog: 0.004 }, neon: { fog: 0.01, nightTop: 0x14043a, nightMid: 0x3a0f5a, nightBot: 0x7a1a7a, dayTop: 0x4a4a9a, dayMid: 0x8a7ac0, dayBot: 0xc09ad0, aurora: 0.5 },
    ruin: { fog: 0.008, nightTop: 0x050a1e, nightMid: 0x2a1a4a, nightBot: 0x1a4a4a, dayTop: 0x6a3a8a, dayMid: 0x9a6aa0, dayBot: 0x5a9a8a, aurora: 0.8 },
  }[era];
  w.env = skyEnv(ctx.hour, { ...pal, overcast: wx.overcast });
  if (fire) { w.env.skyBot.set(0xff6a1a); w.env.skyMid.lerp(new THREE.Color(0x802010), 0.6); w.env.fogColor.set(0x6a2a10); w.env.fogDensity = 0.02; w.env.stars *= 0.2; w.env.sunI = Math.max(w.env.sunI, 0.5); }
  w.groundY = flatten((x, z) => (era === 'prehistoric' ? (fbm2(x * 0.03, z * 0.03) - 0.4) * 9 : era === 'ruin' ? (fbm2(x * 0.04, z * 0.04) - 0.4) * 5 : era === 'ancient' ? (fbm2(x * 0.02, z * 0.02) - 0.4) * 3 : 0), 12, 45);
  const gcol = { prehistoric: [0.18, 0.32, 0.12], ancient: [0.25, 0.34, 0.15], medieval: [0.28, 0.25, 0.18], victorian: [0.17, 0.16, 0.15], modern: [0.2, 0.2, 0.21], neon: [0.08, 0.08, 0.14], ruin: [0.16, 0.24, 0.15] }[era];
  const urban = era === 'medieval' || era === 'victorian' || era === 'modern' || era === 'neon';
  w.add(terrain(300, 150, w.groundY, (c, x, z) => { const n = noise2(x * 0.3, z * 0.3) * 0.12; const street = urban && Math.abs(z) < 5.5; const k = street ? 0.55 : 1; c.setRGB(gcol[0] * k + n, gcol[1] * k + n, gcol[2] * k + n); if (urban && Math.abs(z) > 5.5 && Math.abs(z) < 7) c.setRGB(0.32, 0.31, 0.3); }));

  // the Thames
  const riverZ = era === 'prehistoric' ? 95 : 75;
  const river = new THREE.Mesh(new THREE.PlaneGeometry(400, 30).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ color: era === 'prehistoric' ? 0x2a6a7a : 0x2a3a40, roughness: 0.15, metalness: 0.5, transparent: true, opacity: 0.9 }));
  river.position.set(0, -0.3, riverZ); w.add(river); w.box(0, riverZ, 200, 14);
  if (era === 'medieval' || (era === 'victorian' && ctx.year < 1700)) { // age of exploration: tall ships moored
    for (let i = 0; i < 3; i++) { const sh = new THREE.Group(); const hull = new THREE.Mesh(new THREE.BoxGeometry(14, 3, 4).translate(0, 1.5, 0), std(0x4a3320)); sh.add(hull); for (let m = -1; m <= 1; m++) { sh.add(new THREE.Mesh(new THREE.CylinderGeometry(0.15, 0.2, 14, 6).translate(m * 4, 9, 0), std(0x3a2a1a))); sh.add(new THREE.Mesh(new THREE.PlaneGeometry(5, 7).translate(m * 4, 9.5, 0), std(0xe8dfc8, { side: THREE.DoubleSide }))); } sh.position.set(-40 + i * 38, 0, riverZ - 4 + i * 2); sh.rotation.y = 0.1 * i; w.add(sh); }
    w.scan('Merchant carracks', 'structure', 'Ocean-going ships fitting out for distant ports.', V(-2, 2, riverZ - 6));
  }

  if (era === 'prehistoric') {
    trees(w, 90, 14, 130, 0x4a3a2a, 0x1f5a2a, 14, 1.8);
    w.add(instanced(new THREE.ConeGeometry(0.6, 1.4, 5), std(0x2f7a38), 200, (i, d) => { const a = w.r() * 6.28, r = 4 + w.r() * 100; d.position.set(Math.cos(a) * r, w.groundY(Math.cos(a) * r, Math.sin(a) * r) + 0.5, Math.sin(a) * r); d.scale.setScalar(0.6 + w.r()); }));
    const vol = new THREE.Group(); const vc = new THREE.Mesh(new THREE.ConeGeometry(60, 70, 14), std(0x2a211c)); vc.position.y = 35; vol.add(vc);
    vol.add(new THREE.Mesh(new THREE.CylinderGeometry(7, 7, 2, 10).translate(0, 70, 0), glow(0xff5a10, 3)));
    vol.position.set(-130, -2, -140); w.add(vol); halos(w, [V(-130, 74, -140)], 0xff7020, 90, 0.8);
    const smoke = new THREE.Mesh(new THREE.CylinderGeometry(10, 22, 90, 10, 1, true), new THREE.MeshBasicMaterial({ color: 0x2a2220, transparent: true, opacity: 0.55, depthWrite: false })); smoke.position.set(-130, 120, -140); w.add(smoke);
    w.dinos = []; for (let i = 0; i < 4; i++) { const d = makeDino([0x5a7a3a, 0x7a6a3a, 0x4a6a5a, 0x6a4a3a][i], 1.4 + w.r() * 1.2, 40 + i); d.userData.cx = -30 + i * 25; d.userData.cz = -35 + w.r() * 30; d.userData.rad = 14 + w.r() * 10; w.add(d); w.dinos.push(d); w.scan(['Sauropod grazer', 'Horned herbivore', 'Armoured browser', 'Crested runner'][i], 'life', 'A living dinosaur, unbothered by the box in the ferns.', V(d.userData.cx, 2, d.userData.cz), { follow: d }); }
    w.updaters.push((dt, t) => w.dinos.forEach((d) => { const u = d.userData; u.ang += dt * u.speed * 0.15; d.position.set(u.cx + Math.cos(u.ang) * u.rad, w.groundY(u.cx, u.cz), u.cz + Math.sin(u.ang) * u.rad); d.rotation.y = -u.ang - Math.PI / 2 * 0; u.legs.forEach((l, i) => { l.rotation.z = Math.sin(t * 2 + i * 1.6) * 0.3; }); }));
    w.ambience = { base: 98, ratio: 1.2, pad: 0.05, nf: 1800, ng: 0.22, wind: 0.2, rain: wx.rain }; w.atmo = { N2: 70, O2: 32, Ar: 0.8, other: 0.4, pressure: 1.2, temp: 29, hazards: ['Volcanic ash', 'Megafauna'] }; w.disguise = 'tree';
    w.artifact('amber', V(-9, 0.6, -14), 'Amber with a sealed circuit', 'A drop of amber, 66 million years old, holding something that is not an insect.', 0xffaa30);
    w.landmark('Smoking volcano', 'A distant caldera feeds an ash plume across the sky.', V(-130, 40, -140));
  } else if (era === 'ancient') {
    const n = 14; const huts = [];
    for (let i = 0; i < n; i++) { const a = i * 0.9 + w.r(), r = 16 + w.r() * 22; huts.push({ x: Math.cos(a) * r, z: Math.sin(a) * r * 0.8 - 8 }); }
    w.add(instanced(new THREE.CylinderGeometry(1.8, 2.0, 2, 8), std(0x7a6a50), n, (i, d) => d.position.set(huts[i].x, 1, huts[i].z)));
    w.add(instanced(new THREE.ConeGeometry(2.6, 2.2, 8), std(0x8a7a3a), n, (i, d) => d.position.set(huts[i].x, 3.1, huts[i].z)));
    huts.forEach((h) => w.circle(h.x, h.z, 2.2));
    const fire1 = new THREE.Mesh(new THREE.ConeGeometry(0.6, 1.4, 7), glow(0xff7020, 4)); fire1.position.set(10, 0.7, 6); w.add(fire1);
    halos(w, [V(10, 1.2, 6)], 0xff8030, 14, 0.9); w.circle(10, 6, 0.8);
    w.updaters.push((dt, t) => { fire1.scale.set(1 + Math.sin(t * 13) * 0.12, 1 + Math.sin(t * 9) * 0.2, 1); });
    trees(w, 60, 30, 130, 0x4a3a2a, 0x3a6a2a, 6, 1.2);
    if (ctx.year > 40 && ctx.year < 410) { // Roman Londinium
      const vil = new THREE.Mesh(new THREE.BoxGeometry(16, 5, 10).translate(0, 2.5, 0), std(0xd8c8a8)); vil.position.set(-22, 0, -22); w.add(vil); w.box(-22, -22, 8, 5);
      w.landmark('Roman villa', 'Londinium: stone and tile where there were only huts.', V(-22, 3, -22));
    }
    crowd(w, 14, [0x7a5a3a, 0xa08a6a, 0x5a4a3a], 0.9, 8, 30);
    w.ambience = { base: 123, ratio: 1.25, pad: 0.04, nf: 1100, ng: 0.14, wind: 0.2, rain: wx.rain }; w.atmo.hazards = ['Open fire', 'Wolves']; w.disguise = 'rock';
    if (comet) w.comet = true;
    w.artifact('torc', V(-14, 0.6, 18), 'Gold torc, humming', 'A gold torc that shouldn\'t be warm. It resonates with the rotor.', 0xffd040);
  } else if (era === 'ruin') {
    const mat = buildingMat({ lit: 0x66ffcc, lit2: 0xc060ff, prob: 0.06, glow: 2.4 });
    const im = skyline(w, 36, 40, 150, 30, 130, mat, [0x3a4a3a, 0x2a3a3a, 0x4a4a52]);
    w.add(instanced(new THREE.BoxGeometry(1, 1, 1), std(0x3a5a3a), 40, (i, d) => { const a = w.r() * 6.28, r = 20 + w.r() * 100; d.position.set(Math.cos(a) * r, w.groundY(Math.cos(a) * r, Math.sin(a) * r) + 3, Math.sin(a) * r); d.scale.set(4 + w.r() * 8, 3 + w.r() * 8, 4 + w.r() * 8); d.rotation.set((w.r() - 0.5) * 0.5, w.r() * 3, (w.r() - 0.5) * 0.5); }));
    const plants = []; for (let i = 0; i < 70; i++) plants.push(V((w.r() - 0.5) * 140, 0, (w.r() - 0.5) * 140));
    plants.forEach((p, i) => { if (Math.hypot(p.x, p.z) < 6) return; const pl = makePlant([0x66ffcc, 0xc060ff, 0xffd060][i % 3], i); pl.position.set(p.x, w.groundY(p.x, p.z), p.z); pl.scale.setScalar(1.5 + w.r() * 1.5); w.add(pl); });
    const ring = new THREE.Mesh(new THREE.TorusGeometry(260, 3, 8, 90), glow(0x88ffee, 1.2)); ring.position.set(0, 160, -200); ring.rotation.x = 1.1; ring.rotation.y = 0.4; w.add(ring);
    w.ambience = { base: 87, ratio: 1.34, pad: 0.1, nf: 700, ng: 0.12, wind: 0.2, rain: 0 }; w.atmo = { N2: 66, O2: 17, Ar: 1, other: 16, pressure: 0.9, temp: 11, hazards: ['Spore clouds', 'Unstable ruins'] }; w.disguise = 'crystal';
    w.artifact('core', V(11, 0.6, -9), 'Post-human memory core', 'A drive the size of a seed. It contains a language nobody remembers.', 0x66ffcc);
    w.landmark('Overgrown arcology', 'A city that outlived its builders, and then slowly stopped.', V(60, 40, 60));
    w.instability = 0.4;
  } else {
    const now = era === 'modern', neon = era === 'neon', vic = era === 'victorian', med = era === 'medieval';
    const decade = Math.floor(ctx.year / 10) * 10; const sixties = decade === 1960;
    const hMin = neon ? 60 : now ? 18 : vic ? 10 : 6, hMax = neon ? 190 : now ? 60 : vic ? 17 : 10;
    const mat = buildingMat({ lit: neon ? 0xff4fd8 : now ? 0xcfe6ff : 0xffc878, lit2: neon ? 0x4fe8ff : now ? 0xfff0c0 : 0xffa850, prob: neon ? 0.7 : now ? 0.55 : 0.4, glow: neon ? 3 : 1.8, winW: neon ? 3 : 2.4, winH: neon ? 4 : 3.2 });
    const cols = med ? [0x8a7a5a, 0xa09070, 0x6a5a40] : vic ? [0x6a3b2e, 0x7a4a38, 0x55372e, 0x4a4540] : now ? (sixties ? [0xb8a888, 0x8a9aa8, 0xa88a78] : [0x8a9aa8, 0xb0b6bc, 0x6a7886, 0x4a5662]) : [0x1a1a2a, 0x24243a, 0x2a1a3a, 0x142238];
    rowOfBuildings(w, { era, zs: neon ? [-22, 24, -50] : [-13, 13, -30], xr: 120, hMin, hMax, wMin: neon ? 14 : 7, wMax: neon ? 26 : 13, dp: neon ? 16 : 9, mat, cols, roof: vic ? { color: 0x2f2f38, h: 4, chimneys: true } : med ? { color: 0x6a3a2a, h: 5 } : null, gap: neon ? 3 : 0.4 });
    if (neon) {
      skyline(w, 30, 90, 180, 120, 280, mat, [0x14142a, 0x1a1030, 0x101a30]);
      const lanes = []; for (let i = 0; i < 90; i++) lanes.push({ y: 25 + w.r() * 70, z: -60 + w.r() * 140, sp: (8 + w.r() * 22) * (w.r() < 0.5 ? 1 : -1), x: (w.r() - 0.5) * 300, c: w.r() < 0.5 ? 0xff4fd8 : 0x4fe8ff });
      const cars = instanced(new THREE.BoxGeometry(2.6, 0.7, 1.2), glow(0xffffff, 2.5), lanes.length, (i) => lanes[i].c); cars.frustumCulled = false; w.add(cars);
      w.updaters.push((dt) => { lanes.forEach((l, i) => { l.x += l.sp * dt; if (Math.abs(l.x) > 160) l.x *= -0.98; D.position.set(l.x, l.y, l.z); D.rotation.set(0, l.sp < 0 ? Math.PI : 0, 0); D.scale.set(1, 1, 1); D.updateMatrix(); cars.setMatrixAt(i, D.matrix); }); cars.instanceMatrix.needsUpdate = true; });
      for (let i = 0; i < 6; i++) { const s = signMesh(['NEO-LONDON', 'SYNTH·TEA', 'CHRONO BANK', 'DREAM♥HOTEL', 'GENE·WORKS', 'HOLO-PUB'][i], 12, 5, i % 2 ? '#4fe8ff' : '#ff4fd8'); s.position.set(-50 + i * 20, 9 + (i % 3) * 3, i % 2 ? 8 : -8); s.rotation.y = i % 2 ? Math.PI : 0; w.add(s); }
      w.landmark('Spire of the Thames Authority', 'A kilometre of glass at the river\'s edge, ringed by traffic.', V(110, 100, -80));
      w.ambience = { base: 130, ratio: 1.5, pad: 0.08, nf: 1600, ng: 0.2, wind: 0.1, rain: wx.rain }; w.atmo = { N2: 77, O2: 20, Ar: 0.9, other: 2.1, pressure: 1.0, temp: 19, hazards: ['Smog', 'Heavy air traffic'] }; w.hunters = true; w.disguise = 'lamp';
    } else {
      lamps(w, 110, 14, [-6.5, 6.5], vic ? 0xffd890 : now ? 0xfff3d8 : 0xffa860, vic ? 4.2 : 6);
      if (now) { // cars on the road, 1960s with bright paint
        const nc = 14; const cs = Array.from({ length: nc }, () => ({ x: (w.r() - 0.5) * 220, z: w.r() < 0.5 ? -2 : 2, sp: 8 + w.r() * 8 }));
        const ccols = sixties ? [0xd0402a, 0x2a70d0, 0xe0c030, 0x40a060, 0xf0f0e0] : [0x202428, 0x9aa0a8, 0xb02a2a, 0xe8e8e8, 0x2a3a5a];
        const carM = instanced(new THREE.BoxGeometry(4, 1.3, 1.8).translate(0, 0.8, 0), std(0xffffff, { roughness: 0.4, metalness: 0.5 }), nc, (i) => ccols[i % ccols.length]); carM.frustumCulled = false; w.add(carM);
        w.updaters.push((dt) => { cs.forEach((c, i) => { c.x += c.sp * dt * (c.z < 0 ? -1 : 1); if (Math.abs(c.x) > 115) c.x = -Math.sign(c.x) * 114; D.position.set(c.x, 0, c.z); D.rotation.set(0, c.z < 0 ? Math.PI : 0, 0); D.updateMatrix(); carM.setMatrixAt(i, D.matrix); }); carM.instanceMatrix.needsUpdate = true; });
        if (sixties) { w.scan('Mini and Routemaster traffic', 'structure', 'Colourful cars and a red double-decker. A police box feels natural here.', V(0, 1, 0)); }
        w.ambience = { base: 110, ratio: 1.5, pad: 0.04, nf: 1500, ng: 0.25, wind: 0.1, rain: wx.rain };
      } else w.ambience = { base: 104, ratio: 1.33, pad: 0.05, nf: vic ? 700 : 1000, ng: 0.22, wind: 0.14, rain: wx.rain };
      crowd(w, vic ? 30 : now ? 40 : 26, vic ? [0x1a1a22, 0x3a2a2a, 0x2a3a4a, 0x5a4a3a] : med ? [0x7a5a3a, 0x8a3a2a, 0x3a5a7a] : sixties ? [0xe0a030, 0x30a0e0, 0xe03080, 0x30e090] : [0x2a2a30, 0x3a6aaa, 0xaa3a3a, 0xdddddd], 1.2, vic ? 9 : 7.5, 60);
      w.atmo = { N2: 78, O2: 20.8, Ar: 0.9, other: vic ? 0.6 : 0.3, pressure: 1.0, temp: vic ? 9 : 14, hazards: vic ? ['Coal smoke'] : []}; w.disguise = vic || med ? 'lamp' : 'crate';
      if (vic || now) {
        const tw = new THREE.Group(); tw.add(new THREE.Mesh(new THREE.BoxGeometry(10, 60, 10).translate(0, 30, 0), std(0xb8a888)));
        tw.add(new THREE.Mesh(new THREE.ConeGeometry(7, 26, 4).rotateY(Math.PI / 4).translate(0, 73, 0), std(0x4a4a50)));
        const face = new THREE.Mesh(new THREE.CircleGeometry(3.4, 24), new THREE.MeshBasicMaterial({ color: 0xffe9b0 })); face.position.set(0, 52, 5.1); tw.add(face);
        tw.position.set(55, 0, -42); w.add(tw); w.box(55, -42, 5, 5);
        if (ctx.year >= 1859) w.landmark('The Great Clock', 'The tower\'s bell tolls across the city. The hands are exactly on time.', V(55, 35, -42));
        w.updaters.push((dt, t) => { w.clockT = t; });
      }
    }
    // the Great Fire window: glowing roofs, smoke, rising embers
    if (fire) {
      const fl = instanced(new THREE.ConeGeometry(1.2, 3, 5), glow(0xff5a10, 3.5), 70, (i, d) => { d.position.set((w.r() - 0.5) * 200, 12 + w.r() * 3, (w.r() < 0.5 ? -1 : 1) * (10 + w.r() * 30)); d.scale.setScalar(1 + w.r() * 2.5); }); w.add(fl);
      const sm = []; for (let i = 0; i < 6; i++) { const s = new THREE.Mesh(new THREE.CylinderGeometry(5, 14, 120, 8, 1, true), new THREE.MeshBasicMaterial({ color: 0x1a1410, transparent: true, opacity: 0.6, depthWrite: false })); s.position.set(-90 + i * 36, 60, i % 2 ? -34 : 34); w.add(s); sm.push(s); }
      const emb = new THREE.Points(new THREE.BufferGeometry().setAttribute('position', new THREE.Float32BufferAttribute(Array.from({ length: 600 }, () => (w.r() - 0.5) * 120).map((v, i) => (i % 3 === 1 ? Math.abs(v) : v)), 3)), new THREE.PointsMaterial({ color: 0xff8a30, size: 0.4, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false })); emb.frustumCulled = false; w.add(emb);
      w.updaters.push((dt, t, pl) => { const p = emb.geometry.attributes.position; for (let i = 0; i < p.count; i++) { let y = p.getY(i) + dt * (3 + (i % 5)); if (y > 60) y = 0; p.setY(i, y); p.setX(i, p.getX(i) + Math.sin(t + i) * dt); } p.needsUpdate = true; emb.position.set(pl ? pl.x : 0, 0, pl ? pl.z : 0); });
      w.landmark('The Great Fire of London', 'Pudding Lane is burning. Visible only these four days in September.', V(0, 10, -30), 'fire1666'); w.temporalEvent = 'The Great Fire';
      w.instability = 0.3; w.atmo.hazards = ['Fire', 'Smoke', 'Falling timber']; w.atmo.temp = 42;
    }
    w.artifact('chrono', V(-12, 0.6, ctx.year % 2 ? 14 : -14), neon ? 'Chrono-battery prototype' : vic ? 'Pocket watch running backwards' : now ? 'Stranger\'s phone, battery full' : 'Iron key that opens nothing', 'It ticks in a rhythm that matches the time rotor.', 0x66ddff);
    w.scan('Temporal residue', 'anomaly', 'A faint chronon trail: someone else has passed through here.', V(-6, 1, -9));
    if (!vic && !neon && !fire) w.temporalNoise = 0.15;
  }
  // sky objects
  if (comet) { const c = new THREE.Mesh(new THREE.SphereGeometry(2, 8, 8), glow(0xe0ffff, 4)); c.position.set(-120, 140, -260); const tail = new THREE.Mesh(new THREE.ConeGeometry(6, 220, 10, 1, true), new THREE.MeshBasicMaterial({ color: 0xaaeeff, transparent: true, opacity: 0.25, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide })); tail.position.set(-120 - 50, 140 + 40, -260 - 70); tail.rotation.z = -1.0; tail.rotation.x = 0.4; w.add(c, tail); w.landmark('Halley\'s Comet', 'A once-in-generations visitor, burning overhead. Visible only in this year.', c.position, 'comet'); }
  w.spawn = { x: 0, z: 2, yaw: Math.PI };
  w.radius = 120; w.water = null; w.hasRiver = true;
  return w;
}

// ------------------------------------------------------------------ GIZA
function buildGiza(ctx, def) {
  const w = new WorldBase(ctx, def), y = ctx.year;
  const era = y < -2600 ? 'pre' : y < 1800 ? 'ancient' : y < 2100 ? 'modern' : y < 100000 ? 'future' : 'ruin';
  const wx = weatherFor(ctx, 0.04); w.weather.rain = 0;
  w.env = skyEnv(ctx.hour, { dayTop: 0x2f6fd0, dayBot: 0xf0d9a8, fog: 0.003, overcast: 0, ground: 0x6a5a3a, nightTop: era === 'future' ? 0x1a0a3a : 0x02051a });
  w.groundY = flatten((x, z) => (fbm2(x * 0.02, z * 0.02) - 0.4) * 5, 12, 40);
  w.add(terrain(500, 160, w.groundY, (c, x, z) => { const n = noise2(x * 0.2, z * 0.2) * 0.1; c.setRGB(era === 'ruin' ? 0.5 : 0.78 + n, era === 'ruin' ? 0.62 : 0.62 + n, era === 'ruin' ? 0.7 : 0.38 + n * 0.5); }));
  const pyr = [{ x: -120, z: -230, s: 110 }, { x: 40, z: -270, s: 105 }, { x: 190, z: -290, s: 55 }];
  pyr.forEach((p, i) => {
    const h = p.s * 0.64, full = era !== 'pre';
    const m = new THREE.Mesh(new THREE.ConeGeometry(p.s * 0.72, full ? h : h * (era === 'pre' ? 0.35 : 1), 4).rotateY(Math.PI / 4), std(era === 'ruin' ? 0x88aacc : era === 'future' ? 0xc8d8e0 : 0xd8c28a, { roughness: 0.9, metalness: era === 'future' ? 0.4 : 0 }));
    m.position.set(p.x, (full ? h : h * 0.35) / 2 - 1, p.z); m.castShadow = true; w.add(m);
    if (era === 'pre' && i === 0) { const sc = new THREE.LineSegments(new THREE.EdgesGeometry(new THREE.BoxGeometry(p.s * 1.1, 60, p.s * 1.1)), new THREE.LineBasicMaterial({ color: 0x6a4a2a })); sc.position.set(p.x, 30, p.z); w.add(sc); }
    if (era === 'ancient' || era === 'modern') { const cap = new THREE.Mesh(new THREE.ConeGeometry(p.s * 0.1, h * 0.14, 4).rotateY(Math.PI / 4), glow(0xffe9a0, 0.4)); cap.position.set(p.x, h - h * 0.06, p.z); w.add(cap); }
  });
  if (era === 'future') { const dome = new THREE.Mesh(new THREE.SphereGeometry(150, 32, 16, 0, 6.28, 0, Math.PI / 2), new THREE.MeshBasicMaterial({ color: 0x66ccff, transparent: true, opacity: 0.12, side: THREE.DoubleSide, depthWrite: false })); dome.position.set(40, 0, -250); w.add(dome); w.landmark('Shield dome over the plateau', 'A hologrid keeps the desert out of the pyramids. And the pyramids in.', dome.position); }
  const sph = new THREE.Group(); sph.add(new THREE.Mesh(new THREE.BoxGeometry(10, 8, 30).translate(0, 4, 0), std(0xc8a870))); sph.add(new THREE.Mesh(new THREE.SphereGeometry(4.2, 12, 10).translate(0, 11, 14), std(0xc8a870))); sph.position.set(60, 0, -70); sph.visible = era !== 'pre'; w.add(sph); if (era !== 'pre') { w.box(60, -70, 6, 16); w.landmark('The Sphinx', era === 'ruin' ? 'Weathered into something almost abstract.' : 'Watching the horizon, as it has for millennia.', V(60, 8, -70)); }
  if (era === 'pre') { // quarry camp: ramps, workers, fires
    for (let i = 0; i < 18; i++) { const b = new THREE.Mesh(new THREE.BoxGeometry(2 + w.r() * 2, 1.6, 2 + w.r() * 2), std(0xc8b080)); b.position.set(-50 + w.r() * 100, 0.8, -100 + w.r() * 60); w.add(b); w.box(b.position.x, b.position.z, 1.5, 1.5); }
    crowd(w, 50, [0x8a6a4a, 0xb89a6a, 0xe8d8b0], 1.0, -20, 70); w.landmark('Great Pyramid, under construction', 'Fifty thousand hands, one stone at a time. Visible only before about 2560 BCE.', V(-120, 20, -230), 'building');
  } else if (era === 'ancient') crowd(w, 16, [0x8a6a4a, 0xe8d8b0, 0x6a3a2a], 0.8, -15, 60);
  else if (era === 'modern') crowd(w, 22, [0xe03030, 0x3070e0, 0xf0f0f0, 0x30c070], 0.9, -15, 50);
  if (era !== 'ruin') { const camels = 5; for (let i = 0; i < camels; i++) { const c = makeBot(300 + i); c.scale.setScalar(2); c.position.set(-30 + i * 14, 0, 18 + (i % 2) * 7); c.userData.camel = true; w.add(c); } w.scan('Camels', 'life', 'Dromedaries, resting in the shade of nothing.', V(0, 1, 20)); }
  w.ambience = { base: 98, ratio: 1.25, pad: 0.05, nf: 500, ng: 0.1, wind: 0.3, rain: 0 }; w.atmo = { N2: 78, O2: 20.9, Ar: 0.9, other: 0.2, pressure: 1.0, temp: 37, hazards: ['Heat', 'Sandstorms'] }; w.disguise = 'rock';
  w.artifact('scarab', V(14, 0.5, -10), 'Scarab with a pulse', 'A faience scarab that beats like a heart. Something inside is still counting.', 0x40e0c0);
  w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.radius = 130; w.cloakMod = 1.0;
  return w;
}

// ------------------------------------------------------------------ MARS
function buildMars(ctx, def) {
  const w = new WorldBase(ctx, def), y = ctx.year;
  const era = y < 2100 ? 'barren' : y < 3500 ? 'colony' : y < 50000 ? 'terra' : 'dead';
  const terraformed = era === 'terra';
  w.env = skyEnv(ctx.hour, terraformed ? { dayTop: 0x3a6fc0, dayBot: 0xc0d8e8, fog: 0.004 } : { dayTop: 0x8a5a40, dayMid: 0xc08a60, dayBot: 0xe0a070, twTop: 0x2a3a6a, twBot: 0x6a90c0, fog: 0.005, ground: 0x6a3a28, nightTop: 0x03040e });
  w.groundY = flatten((x, z) => (fbm2(x * 0.03, z * 0.03) - 0.4) * 10, 14, 50);
  w.add(terrain(400, 150, w.groundY, (c, x, z, yy) => { const n = noise2(x * 0.25, z * 0.25) * 0.1; if (terraformed) c.setRGB(0.18 + n, 0.38 + yy * 0.01 + n, 0.16); else c.setRGB(0.55 + n + yy * 0.01, 0.26 + n * 0.6, 0.14); }));
  w.add(instanced(new THREE.DodecahedronGeometry(1, 0), std(terraformed ? 0x5a5a50 : 0x6a3a2a, { flatShading: true }), 90, (i, d) => { const a = w.r() * 6.28, r = 8 + w.r() * 110; const x = Math.cos(a) * r, z = Math.sin(a) * r; d.position.set(x, w.groundY(x, z) + 0.3, z); d.scale.set(0.6 + w.r() * 2, 0.5 + w.r() * 1.5, 0.6 + w.r() * 2); d.rotation.set(w.r() * 3, w.r() * 3, 0); w.circle(x, z, 0.8 * d.scale.x); }, { shadow: true }));
  if (era === 'colony' || era === 'dead') {
    const live = era === 'colony';
    for (let i = 0; i < 4; i++) { const dm = new THREE.Mesh(new THREE.SphereGeometry(14 + i * 3, 24, 12, 0, 6.28, 0, Math.PI / 2), new THREE.MeshStandardMaterial({ color: live ? 0x88ccff : 0x556, transparent: true, opacity: live ? 0.22 : 0.4, roughness: 0.1, depthWrite: false, side: THREE.DoubleSide })); const a = i * 1.6 + 0.4; dm.position.set(Math.cos(a) * 50, 0, Math.sin(a) * 40 - 30); w.add(dm); w.circle(dm.position.x, dm.position.z, 14 + i * 3);
      if (live) halos(w, [V(dm.position.x, 6, dm.position.z)], 0xffe0a0, 40, 0.45);
      const inner = new THREE.Mesh(new THREE.CylinderGeometry(3, 3, 8, 8), buildingMat({ lit: 0xffe0a0, prob: live ? 0.8 : 0, glow: 2 })); inner.position.set(dm.position.x, 4, dm.position.z); w.add(inner); }
    w.landmark(live ? 'Olympus Colony domes' : 'Abandoned colony domes', live ? 'Four pressure domes of the first Martian city. Rovers ferry settlers between them.' : 'Cracked domes under dust. Someone left in a hurry.', V(50, 8, -30));
    if (live) crowd(w, 12, [0xff8a30, 0xf0f0f0], 0.9, 14, 40);
    const pad = new THREE.Mesh(new THREE.CylinderGeometry(9, 9, 0.2, 24), std(0x3a3a44)); pad.position.set(-26, 0.1, 22); w.add(pad);
  }
  if (era === 'barren') { const rv = new THREE.Group(); rv.add(new THREE.Mesh(new THREE.BoxGeometry(2.4, 0.8, 1.6).translate(0, 0.9, 0), std(0xd8d8d0, { metalness: 0.5 }))); for (const sx of [-0.9, 0.9]) for (const sz of [-0.8, 0.8]) rv.add(new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.3, 0.2, 10).rotateX(Math.PI / 2).translate(sx, 0.3, sz), std(0x222222))); rv.position.set(18, w.groundY(18, -12), -12); w.add(rv); w.circle(18, -12, 1.5); w.landmark('Dormant rover', 'A rover, dusty and silent. The last photo it sent was of a blue box.', V(18, 1, -12)); }
  if (terraformed) { trees(w, 70, 16, 120, 0x4a3a2a, 0x3a8a3a, 6, 1.2); const lake = new THREE.Mesh(new THREE.CircleGeometry(50, 36).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0x2a6a9a, roughness: 0.1, metalness: 0.5 })); lake.position.set(70, -0.2, 40); w.add(lake); w.box(70, 40, 40, 40); }
  const moons = new THREE.Mesh(new THREE.IcosahedronGeometry(8, 1), std(0x8a8070, { flatShading: true })); moons.position.set(-160, 120, -240); w.add(moons);
  w.ambience = { base: 73, ratio: 1.5, pad: 0.05, nf: 420, ng: 0.14, wind: terraformed ? 0.18 : 0.32, rain: 0 }; w.atmo = terraformed ? { N2: 76, O2: 22, Ar: 1.5, other: 0.5, pressure: 0.9, temp: 12, hazards: [] } : { N2: 2.7, O2: 0.15, Ar: 1.6, other: 95.5, pressure: 0.006, temp: -63, hazards: ['CO₂ atmosphere', 'Radiation', 'No breathable air'] };
  w.disguise = 'rock'; w.gravity = 0.38; w.cloakMod = 1.1;
  w.artifact('ore', V(-10, 0.6, -15), 'Chronal ore', 'A seam of rock that sparkles when the rotor idles nearby.', 0xff9040);
  w.scan('Dust devil', 'anomaly', 'A slow-turning column of dust, tracking the TARDIS across the plain.', V(40, 3, 30));
  w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.radius = 120; w.temporalNoise = 0.05;
  return w;
}

// ------------------------------------------------------------------ KEPLER TERMINUS (dying star)
function buildKepler(ctx, def) {
  const w = new WorldBase(ctx, def), y = ctx.year;
  const era = y < 3000 ? 'founding' : y < 6500 ? 'thriving' : 'dying';
  w.env = customEnv({ top: era === 'dying' ? 0x1a0505 : 0x1a1030, mid: era === 'dying' ? 0x601008 : 0x502050, bot: era === 'dying' ? 0xc03a10 : 0xb06080, stars: 0.6, sunI: 1.4, sunColor: 0xff6a30, hemiSky: 0xff7a40, hemiGround: 0x201010, hemiI: 0.55, fog: 0.004, sunDir: new THREE.Vector3(0.2, 0.35, -0.9).normalize(), exposure: 1.1 });
  const star = new THREE.Mesh(new THREE.SphereGeometry(era === 'dying' ? 220 : 90, 32, 24), new THREE.MeshBasicMaterial({ color: era === 'dying' ? 0xff3a10 : 0xffa040, fog: false })); star.position.set(60, 150, -500); w.add(star);
  const corona = new THREE.Mesh(new THREE.SphereGeometry(era === 'dying' ? 260 : 110, 24, 16), new THREE.MeshBasicMaterial({ color: 0xff6a20, transparent: true, opacity: 0.25, blending: THREE.AdditiveBlending, depthWrite: false, fog: false })); corona.position.copy(star.position); w.add(corona);
  const R = 38; w.groundY = (x, z) => (Math.hypot(x, z) < R ? 0 : -1000);
  const plat = new THREE.Mesh(new THREE.CylinderGeometry(R, R + 2, 3, 40).translate(0, -1.5, 0), std(0x3a3036, { metalness: 0.6, roughness: 0.5 })); plat.receiveShadow = true; w.add(plat);
  const grid = new THREE.Mesh(new THREE.RingGeometry(10, R - 1, 40, 5).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: 0xff7a40, wireframe: true, transparent: true, opacity: 0.25 })); grid.position.y = 0.02; w.add(grid);
  const mat = buildingMat({ lit: 0xffb060, lit2: 0xff6a40, prob: era === 'thriving' ? 0.7 : era === 'dying' ? 0.03 : 0.3, glow: 2.4 });
  const towers = era === 'founding' ? 3 : 11;
  w.add(instanced(new THREE.BoxGeometry(1, 1, 1), mat, towers, (i, d) => { const a = i * 0.57 + 0.8, r = 18 + (i % 3) * 7; const h = era === 'founding' ? 10 : 20 + w.r() * 36; d.position.set(Math.cos(a) * r, h / 2, Math.sin(a) * r); d.scale.set(4, h, 4); w.box(d.position.x, d.position.z, 2.2, 2.2); return era === 'dying' ? 0x2a2428 : 0x3a3a48; }, { shadow: true }));
  for (let i = 0; i < 4; i++) { const p = new THREE.Mesh(new THREE.CylinderGeometry(6 + i * 2, 6 + i * 2, 1.5, 20), std(0x3a3036, { metalness: 0.6 })); const a = i * 1.7 + 1; p.position.set(Math.cos(a) * (R + 40 + i * 15), -4 + i * 6, Math.sin(a) * (R + 40 + i * 15)); w.add(p); }
  if (era !== 'dying') { const ring = new THREE.Mesh(new THREE.TorusGeometry(260, 4, 8, 120), new THREE.MeshStandardMaterial({ color: 0x555566, emissive: 0xff8844, emissiveIntensity: 0.4, metalness: 0.7 })); ring.rotation.x = 1.2; ring.position.set(0, 80, -200); w.add(ring); w.landmark('Orbital ring', 'A habitat ring wrapped around the star\'s equator of light.', ring.position); }
  const debris = instanced(new THREE.DodecahedronGeometry(1, 0), std(0x555, { flatShading: true }), 80, (i, d) => { const a = w.r() * 6.28, r = 60 + w.r() * 140; d.position.set(Math.cos(a) * r, -30 + w.r() * 70, Math.sin(a) * r); d.scale.setScalar(1 + w.r() * 5); d.rotation.set(w.r() * 3, w.r() * 3, 0); }); w.add(debris);
  w.updaters.push((dt, t) => { debris.rotation.y = t * 0.004; star.scale.setScalar(1 + Math.sin(t * 0.3) * 0.01); });
  if (era !== 'dying') crowd(w, 12, [0xff9a50, 0xdddddd], 0.7, 8, 28);
  w.ambience = { base: 65, ratio: 1.2, pad: 0.1, nf: 350, ng: 0.12, wind: 0.0, rain: 0 }; w.atmo = { N2: 0, O2: 0, Ar: 0, other: 100, pressure: 0, temp: era === 'dying' ? 240 : 60, hazards: ['Vacuum (containment field active)', 'Stellar radiation'] };
  w.gravity = 0.8; w.fallY = -35; w.disguise = 'crate'; w.cloakMod = 0.9; w.instability = 0.2;
  w.landmark(era === 'dying' ? 'The red giant, Kepler-9' : 'Kepler-9', 'A star at the end of its life, swelling to swallow its worlds. The people left long ago — or are leaving.', star.position);
  w.artifact('lens', V(-15, 0.6, 10), 'Starlight lens', 'Ground from the last light of a dying star.', 0xff6a30);
  w.scan('Atmospheric containment field', 'tech', 'A shimmering field holds a thin pocket of air over the platform.', V(0, 4, 0));
  w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.radius = R - 2; w.temporalNoise = 0.2;
  return w;
}

// ------------------------------------------------------------------ GRID FIELD (infinite-feeling repeated worlds)
class GridField {
  constructor(geo, mat, cell, rad, place, o = {}) { this.cell = cell; this.rad = rad; this.place = place; const n = (rad * 2 + 1) ** 2; this.im = new THREE.InstancedMesh(geo, mat, n); this.im.frustumCulled = false; this.im.castShadow = false; this.ci = null; this.cj = null; this.max = n; if (o.colors) this.im.setColorAt(0, new THREE.Color()); this.o = o; }
  update(px, pz) {
    const ci = Math.round(px / this.cell), cj = Math.round(pz / this.cell);
    if (ci === this.ci && cj === this.cj) return; this.ci = ci; this.cj = cj; let k = 0;
    for (let i = -this.rad; i <= this.rad; i++) for (let j = -this.rad; j <= this.rad; j++) {
      const p = this.place(ci + i, cj + j);
      if (!p) continue;
      D.position.set(p.x, p.y, p.z); D.scale.set(p.sx, p.sy, p.sz); D.rotation.set(0, p.ry || 0, 0); D.updateMatrix(); this.im.setMatrixAt(k, D.matrix);
      if (this.o.colors && p.c != null) this.im.setColorAt(k, new THREE.Color(p.c)); k++;
    }
    this.im.count = k; this.im.instanceMatrix.needsUpdate = true; if (this.im.instanceColor) this.im.instanceColor.needsUpdate = true;
  }
  blocked(x, z) { const ci = Math.round(x / this.cell), cj = Math.round(z / this.cell); const p = this.place(ci, cj); return !!p && Math.abs(x - p.x) < p.sx / 2 + 0.3 && Math.abs(z - p.z) < p.sz / 2 + 0.3; }
}
const cellRand = (i, j, s = 0) => { let h = (i * 374761393 + j * 668265263 + s * 2147483647) | 0; h = (h ^ (h >>> 13)) * 1274126177; h ^= h >>> 16; return (h >>> 0) / 4294967296; };

// ------------------------------------------------------------------ DIMENSIONS
function dimBase(ctx, def, env) { const w = new WorldBase(ctx, def); w.env = customEnv(env); w.disguise = 'crystal'; w.cloakMod = def.cloakMod ?? 0.8; return w; }
const checker = (a, b, n = 16) => canvasTex(256, 256, (g, W, H) => { for (let y = 0; y < n; y++) for (let x = 0; x < n; x++) { g.fillStyle = (x + y) % 2 ? a : b; g.fillRect(x * W / n, y * H / n, W / n, H / n); } }, { repeat: true });

function buildMirror(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x9aa0c8, mid: 0xd8d4f0, bot: 0xf0ecff, stars: 0.1, sunI: 1.1, hemiI: 0.9, fog: 0.006, sunDisc: 1, glow: 0.3, day: 0.8, exposure: 1.1 });
  const tex = checker('#b8bad8', '#e8e6f8', 24); tex.repeat.set(30, 30);
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(600, 600).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ map: tex, roughness: 0.15, metalness: 0.4, transparent: true, opacity: 0.8 })); w.add(floor);
  const ceil = new THREE.Mesh(new THREE.PlaneGeometry(600, 600).rotateX(Math.PI / 2), new THREE.MeshStandardMaterial({ map: tex, roughness: 0.15, metalness: 0.4, side: THREE.DoubleSide })); ceil.position.y = 70; w.add(ceil);
  const city = new THREE.Group(); const mat = buildingMat({ lit: 0xffffff, lit2: 0x88ccff, prob: 0.35, glow: 1.2, rough: 0.25, metal: 0.6 });
  const n = 55; const geo = new THREE.BoxGeometry(1, 1, 1);
  const im = instanced(geo, mat, n, (i, d) => { const a = w.r() * 6.28, r = 18 + w.r() * 130; const h = 10 + w.r() * 45, s = 5 + w.r() * 10; d.position.set(Math.cos(a) * r, h / 2, Math.sin(a) * r); d.scale.set(s, h, s);
    if (i % 3 === 0) { d.rotation.z = (w.r() - 0.5) * 1.2; d.position.y = h * 0.4; } if (i % 3 === 1) d.rotation.y = w.r() * 3; w.box(d.position.x, d.position.z, s * 0.7, s * 0.7); return [0xdcdcf4, 0xc4c8ee, 0xe8d0f0, 0xcce8f4][i % 4]; });
  city.add(im); w.add(city);
  const refl = city.clone(); refl.scale.y = -1; w.add(refl); // reflection under the glass
  const hang = city.clone(); hang.scale.y = -1; hang.position.y = 70; w.add(hang); // the inverted city on the ceiling
  w.ceilY = 70; w.flipPads = [{ x: 7, z: -7, r: 1.8 }, { x: -9, z: 15, r: 1.8 }, { x: 28, z: 10, r: 1.8 }];
  w.flipPads.forEach((p) => { const m = new THREE.Mesh(new THREE.CircleGeometry(p.r, 24).rotateX(-Math.PI / 2), glow(0xff66ff, 2)); m.position.set(p.x, 0.04, p.z); w.add(m); const m2 = m.clone(); m2.position.y = 69.96; m2.rotation.x = Math.PI / 2; w.add(m2); });
  w.updaters.push((dt, t) => { for (let i = 0; i < 3; i++) { /* mirrored city sways slightly */ } hang.rotation.y = Math.sin(t * 0.05) * 0.02; });
  w.scan('Gravity inversion pads', 'anomaly', 'Standing on a pad flips gravity: step on the glowing circles to walk on the ceiling.', V(7, 0.5, -7));
  w.scan('Folded tower', 'structure', 'A tower that bends back through itself. Its reflection has a different number of floors.', V(25, 10, 25));
  w.landmark('Mirror horizon', 'The floor and the sky are the same place, seen from different sides.', V(0, 35, -120));
  w.artifact('shard', V(-8, 0.6, -12), 'Shard of a looking-glass', 'It shows you five seconds from now.', 0xff66ff);
  w.ambience = { base: 392, ratio: 1.5, pad: 0.06, nf: 3000, ng: 0.04, wind: 0.04, rain: 0 }; w.atmo = { N2: 60, O2: 15, Ar: 0, other: 25, pressure: 0.8, temp: 5, hazards: ['Gravity inversion', 'Reflection desync'] }; w.gravity = 0.8; w.instability = 0.5;
  w.radius = 135; w.spawn = { x: 0, z: 2, yaw: Math.PI }; return w;
}
function buildVoid(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x000003, mid: 0x02020a, bot: 0x05050f, stars: 1, sunI: 0.15, hemiI: 0.25, fog: 0.0025, glow: 0.2, day: 0, sunColor: 0x6a7aff, exposure: 1.0 });
  const isl = [{ x: 0, z: 0, r: 28, y: 0 }, { x: 44, z: 14, r: 7, y: 2 }, { x: 62, z: 38, r: 6, y: 5 }, { x: 78, z: 70, r: 9, y: 8 }, { x: 95, z: 100, r: 22, y: 10 }, { x: -50, z: -26, r: 12, y: -3 }, { x: -70, z: -60, r: 10, y: -6 }];
  const noisy = (x, z) => noise2(x * 0.3, z * 0.3) * 1.6;
  w.groundY = (x, z, py = 1e9) => { let best = -1000; for (const s of isl) if (Math.hypot(x - s.x, z - s.z) < s.r + noisy(x, z) && s.y <= py + 0.9) best = Math.max(best, s.y + (s.r > 15 ? (fbm2(x * 0.1, z * 0.1) - 0.4) * 1.5 : 0)); return best; };
  const rock = std(0x1a1a24, { flatShading: true, roughness: 1 });
  isl.forEach((s, i) => { const top = new THREE.Mesh(new THREE.CylinderGeometry(s.r + 1, s.r * 0.6, 3, 24), rock); top.position.set(s.x, s.y - 1.55, s.z); w.add(top); const under = new THREE.Mesh(new THREE.ConeGeometry(s.r * 0.6, s.r * 1.6, 8).rotateX(Math.PI), rock); under.position.set(s.x, s.y - 3 - s.r * 0.8, s.z); w.add(under);
    const eg = new THREE.Mesh(new THREE.RingGeometry(s.r - 0.2, s.r + 0.2, 48).rotateX(-Math.PI / 2), glow(0x4a5aff, 0.8)); eg.position.set(s.x, s.y + 0.02, s.z); w.add(eg); });
  const cols = 16; const col = instanced(new THREE.CylinderGeometry(0.7, 0.9, 1, 8), rock, cols, (i, d) => { const a = i * 0.9, r = 8 + (i % 5) * 4; const h = 2 + w.r() * 7; d.position.set(Math.cos(a) * r, h / 2, Math.sin(a) * r); d.scale.set(1, h, 1); d.rotation.set((w.r() - 0.5) * 0.25, 0, (w.r() - 0.5) * 0.25); w.circle(d.position.x, d.position.z, 0.9); }, { shadow: true }); w.add(col);
  const arch = new THREE.Mesh(new THREE.TorusGeometry(6, 0.9, 8, 14, Math.PI), rock); arch.position.set(-12, 0, 8); arch.rotation.y = 0.8; w.add(arch);
  const floaters = instanced(new THREE.IcosahedronGeometry(1, 0), rock, 90, (i, d) => { const a = w.r() * 6.28, r = 40 + w.r() * 200; d.position.set(Math.cos(a) * r, -40 + w.r() * 120, Math.sin(a) * r); d.scale.setScalar(1 + w.r() * 9); d.rotation.set(w.r() * 3, w.r() * 3, 0); }); w.add(floaters);
  const lights = []; for (let i = 0; i < 70; i++) { const a = w.r() * 6.28, r = 90 + w.r() * 320; lights.push(V(Math.cos(a) * r, -30 + w.r() * 140, Math.sin(a) * r)); } halos(w, lights, 0x6a8aff, 7, 0.9);
  const beacon = new THREE.Mesh(new THREE.CylinderGeometry(0.25, 0.25, 90, 8), glow(0x88aaff, 2.2, { transparent: true, opacity: 0.6 })); beacon.position.set(95, 55, 100); w.add(beacon);
  const pulse = new THREE.Mesh(new THREE.RingGeometry(0.9, 1, 48).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ color: 0x88aaff, transparent: true, opacity: 0.6, side: THREE.DoubleSide, depthWrite: false })); pulse.position.set(95, 10.1, 100); w.add(pulse);
  w.updaters.push((dt, t) => { const k = (t * 0.2) % 1; pulse.scale.setScalar(2 + k * 40); pulse.material.opacity = (1 - k) * 0.6; beacon.material.opacity = 0.35 + Math.sin(t * 2) * 0.25; floaters.rotation.y = t * 0.002; });
  w.scan('Repeating signal', 'anomaly', 'A beacon pulses from the far island. 4 long, 2 short — it is not a language, but it is not random.', V(95, 12, 100));
  w.landmark('Floating ruins', 'Columns and arches that stood in a city that was never here.', V(0, 3, 0));
  w.artifact('lantern', V(97, 10.6, 103), 'Lantern of the Void', 'It burns without fuel. Its light travels slower than it should.', 0x88aaff);
  w.ambience = { base: 55, ratio: 1.1, pad: 0.12, nf: 250, ng: 0.05, wind: 0.0, rain: 0 }; w.atmo = { N2: 0, O2: 0, Ar: 0, other: 0, pressure: 0, temp: -270, hazards: ['No ground beyond the islands', 'Low gravity (0.25 g)'] };
  w.gravity = 0.25; w.fallY = -70; w.radius = 400; w.temporalNoise = 0.6; w.instability = 0.2; w.spawn = { x: 0, z: 2, yaw: Math.PI };
  return w;
}
function buildLibrary(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x120a05, mid: 0x2a1a0c, bot: 0x4a2c14, stars: 0, sunI: 0.15, hemiI: 0.5, hemiSky: 0xffc880, hemiGround: 0x3a2410, fog: 0.012, exposure: 1.15, glow: 0.4 });
  const mat = buildingMat({ lit: 0xffc060, lit2: 0xff7050, prob: 0.9, glow: 1.5, winW: 0.45, winH: 0.9, rough: 0.9, metal: 0 });
  const place = (i, j) => { if (Math.abs(i) < 1 && Math.abs(j) < 1) return null; const r = cellRand(i, j); if (r < 0.18) return null; const alongX = cellRand(i, j, 3) < 0.5; return { x: i * 8, y: 8, z: j * 8, sx: alongX ? 7 : 1.4, sy: 16, sz: alongX ? 1.4 : 7, c: [0x5a3a22, 0x4a2c1a, 0x6a4a30][Math.floor(r * 30) % 3] }; };
  const gf = new GridField(new THREE.BoxGeometry(1, 1, 1), mat, 8, 9, place, { colors: true }); w.add(gf.im); w.field = gf; w.blocked = (x, z) => gf.blocked(x, z);
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(400, 400).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ map: checker('#2a1a10', '#3a2416', 8), roughness: 0.6 })); floor.material.map.repeat.set(50, 50); w.add(floor);
  const ceil = new THREE.Mesh(new THREE.PlaneGeometry(400, 400).rotateX(Math.PI / 2), std(0x120a05)); ceil.position.y = 18; w.add(ceil);
  const orbs = new THREE.Group(); for (let i = 0; i < 60; i++) { const o = new THREE.Mesh(new THREE.SphereGeometry(0.28, 8, 8), glow(0xffd890, 3)); o.userData = { bx: (w.r() - 0.5) * 120, bz: (w.r() - 0.5) * 120, ph: w.r() * 6 }; o.position.set(o.userData.bx, 6 + w.r() * 8, o.userData.bz); orbs.add(o); } w.add(orbs);
  w.updaters.push((dt, t, pl) => { orbs.children.forEach((o) => { o.position.y += Math.sin(t + o.userData.ph) * dt * 0.4; }); gf.update(pl.x, pl.z); floor.position.set(Math.round(pl.x / 8) * 8, 0, Math.round(pl.z / 8) * 8); ceil.position.set(Math.round(pl.x / 8) * 8, 18, Math.round(pl.z / 8) * 8); orbs.position.set(Math.round(pl.x / 60) * 60, 0, Math.round(pl.z / 60) * 60); });
  gf.update(0, 0);
  const pedestals = [[5, -9], [-6, 11], [13, 3]];
  pedestals.forEach(([x, z], i) => { const g = new THREE.Group(); g.position.set(x, 0, z); g.add(new THREE.Mesh(new THREE.CylinderGeometry(0.5, 0.6, 1.1, 10).translate(0, 0.55, 0), std(0x3a2a20))); const book = new THREE.Mesh(new THREE.BoxGeometry(0.7, 0.12, 0.5).translate(0, 1.2, 0), glow([0xffc060, 0xff7050, 0x80d0ff][i], 1.8)); g.add(book);
    const hit = new THREE.Mesh(new THREE.SphereGeometry(0.9, 6, 6).translate(0, 1.1, 0), new THREE.MeshBasicMaterial({ visible: false })); hit.userData.interact = { label: () => 'Read the book', run: () => ctx.readBook(i) }; g.add(hit); w.interactables.push(hit); w.add(g); w.circle(x, z, 0.7); w.updaters.push((dt, t) => { book.position.y = Math.sin(t * 1.5 + i) * 0.05; }); });
  w.scan('Book of unwritten histories', 'tech', 'The pages rewrite themselves while you watch. Some of them describe doors in the TARDIS.', V(5, 1.2, -9));
  w.landmark('The endless stacks', 'The shelves repeat, but the books do not.', V(0, 8, 30));
  w.artifact('index', V(-3, 0.6, 5), 'Index of Universes', 'A card catalogue entry for a universe that has not happened yet.', 0xffc060);
  w.ambience = { base: 174, ratio: 1.5, pad: 0.07, nf: 600, ng: 0.05, wind: 0.04, rain: 0 }; w.atmo = { N2: 78, O2: 21, Ar: 0.9, other: 0.1, pressure: 1.0, temp: 19, hazards: ['Paper dust'] };
  w.radius = 1e5; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.cloakMod = 1.1; w.disguise = 'crate'; return w;
}
function buildNeonUniverse(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x14003a, mid: 0xa01aa0, bot: 0x00c8ff, stars: 0.3, sunI: 0.7, sunColor: 0xff40ff, hemiSky: 0xff40ff, hemiGround: 0x103060, hemiI: 0.8, fog: 0.006, aurora: 1, glow: 0.8, exposure: 1.1, cloakMod: 0.5 });
  const mat = buildingMat({ lit: 0xff40ff, lit2: 0x20e8ff, prob: 0.8, glow: 3.2, winW: 3.2, winH: 4 });
  const place = (i, j) => { if (Math.abs(i) < 1 && Math.abs(j) < 1) return null; const r = cellRand(i, j, 5); if (r < 0.25) return null; const h = 30 + cellRand(i, j, 9) * 170; return { x: i * 30, y: h / 2, z: j * 30, sx: 14 + r * 6, sy: h, sz: 14 + r * 6, c: [0x1a1030, 0x101a38, 0x20103a][Math.floor(r * 30) % 3] }; };
  const gf = new GridField(new THREE.BoxGeometry(1, 1, 1), mat, 30, 7, place, { colors: true }); w.add(gf.im); w.blocked = (x, z) => gf.blocked(x, z); gf.update(0, 0);
  const road = new THREE.Mesh(new THREE.PlaneGeometry(600, 600).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ map: checker('#070312', '#0c0620', 2), roughness: 0.3, metalness: 0.6 })); road.material.map.repeat.set(40, 40); w.add(road);
  const veh = instanced(new THREE.BoxGeometry(3, 0.7, 1.4), glow(0xffffff, 3), 120, (i) => (i % 2 ? 0xff40ff : 0x20e8ff)); veh.frustumCulled = false; w.add(veh);
  const vs = Array.from({ length: 120 }, (_, i) => ({ lane: (i % 12) * 30 - 150 + 15, y: 14 + (i % 5) * 9, x: (w.r() - 0.5) * 300, sp: (12 + w.r() * 25) * (i % 2 ? 1 : -1), dim: i % 3 === 0 }));
  w.updaters.push((dt, t, pl) => { vs.forEach((v, i) => { v.x += v.sp * dt; if (Math.abs(v.x) > 160) v.x = -Math.sign(v.x) * 159; const wx = Math.round(pl.x / 300) * 300 + (v.dim ? 0 : 0); D.position.set(wx + v.x, v.y, Math.round(pl.z / 300) * 300 + v.lane); D.rotation.set(0, v.sp < 0 ? Math.PI : 0, 0); D.scale.set(1, 1, 1); D.updateMatrix(); veh.setMatrixAt(i, D.matrix); }); veh.instanceMatrix.needsUpdate = true; gf.update(pl.x, pl.z); road.position.set(Math.round(pl.x / 30) * 30, 0, Math.round(pl.z / 30) * 30); });
  for (let i = 0; i < 8; i++) { const s = signMesh(['SYNTH·TEA', 'DREAMSCAPE™', 'GHOST·NET', 'CHRONOS', 'OVERDRIVE', 'LUMEN', 'N3ON', 'VOID·BAR'][i], 14, 6, i % 2 ? '#20e8ff' : '#ff40ff'); s.position.set(-30 + i * 9, 10 + (i % 3) * 4, -14 + (i % 2) * 28); s.rotation.y = i % 2 ? Math.PI : 0; w.add(s); }
  w.scan('Sky-lane traffic', 'structure', 'Thousands of vehicles stacked in lanes of light. They never seem to arrive.', V(0, 20, 0)); w.scan('Neon patrol drones', 'tech', 'Scanner drones sweep the street. They are looking for something that is not neon.', V(10, 6, 12), { hostile: true });
  w.hunters = true; w.landmark('Cathedral of Light', 'A tower made entirely of advertising.', V(120, 100, -120));
  w.artifact('chip', V(-8, 0.6, 9), 'Neon codex chip', 'A data-chip with a city\'s dreams in it.', 0xff40ff);
  w.ambience = { base: 147, ratio: 1.5, pad: 0.09, nf: 2200, ng: 0.17, wind: 0.05, rain: 0 }; w.atmo = { N2: 70, O2: 19, Ar: 0, other: 11, pressure: 1.1, temp: 24, hazards: ['Patrol drones', 'Photic overload'] };
  w.radius = 1e5; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.disguise = 'lamp'; return w;
}
function buildCrystal(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x1a3a6a, mid: 0x6aa8e8, bot: 0xd8f0ff, stars: 0.25, sunI: 2.2, sunColor: 0xfff4e0, hemiSky: 0xaaddff, hemiGround: 0x4a6a8a, hemiI: 0.9, fog: 0.0035, sunDisc: 1, day: 0.9, exposure: 1.1, cloakMod: 1.2 });
  w.groundY = flatten((x, z) => (fbm2(x * 0.04, z * 0.04) - 0.4) * 6, 12, 40);
  w.add(terrain(500, 140, w.groundY, (c, x, z, y) => { const n = noise2(x * 0.2, z * 0.2) * 0.1; c.setRGB(0.5 + n + y * 0.02, 0.7 + n, 0.85); }));
  const cm = new THREE.MeshStandardMaterial({ color: 0x9adfff, emissive: 0x3a7aaa, emissiveIntensity: 0.55, transparent: true, opacity: 0.62, roughness: 0.05, metalness: 0.2, flatShading: true });
  const n = 90; const list = []; const cs = instanced(new THREE.CylinderGeometry(0.0, 1, 1, 6), cm, n, (i, d) => { const a = w.r() * 6.28, r = 12 + Math.pow(w.r(), 0.7) * 150, h = 4 + Math.pow(w.r(), 2) * 60; const x = Math.cos(a) * r, z = Math.sin(a) * r; d.position.set(x, w.groundY(x, z) + h / 2 - 1, z); d.scale.set(2 + h * 0.18, h, 2 + h * 0.18); d.rotation.set((w.r() - 0.5) * 0.4, w.r() * 3, (w.r() - 0.5) * 0.4); list.push({ x, z, h }); w.circle(x, z, (2 + h * 0.18) * 0.5); return [0x9adfff, 0xffa8f0, 0xa8ffd8, 0xc8b8ff][i % 4]; }, { shadow: true }); w.add(cs);
  const shafts = []; for (let i = 0; i < 10; i++) { const s = new THREE.Mesh(new THREE.CylinderGeometry(1.2, 4, 70, 12, 1, true), new THREE.MeshBasicMaterial({ color: 0xaaeeff, transparent: true, opacity: 0.1, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide })); const a = i * 0.8 + 0.3; s.position.set(Math.cos(a) * (20 + i * 6), 35, Math.sin(a) * (20 + i * 6)); s.rotation.z = 0.25; w.add(s); shafts.push(s); }
  const orgs = []; for (let i = 0; i < 24; i++) { const o = new THREE.Mesh(new THREE.OctahedronGeometry(0.35), glow([0xaaeeff, 0xffaaf0][i % 2], 2.5)); o.userData = { a: w.r() * 6.28, r: 4 + w.r() * 16, h: 1 + w.r() * 4, s: 0.2 + w.r() * 0.5, cx: (w.r() - 0.5) * 60, cz: (w.r() - 0.5) * 60 }; w.add(o); orgs.push(o); }
  w.updaters.push((dt, t) => { orgs.forEach((o) => { const u = o.userData; u.a += dt * u.s; o.position.set(u.cx + Math.cos(u.a) * u.r, u.h + Math.sin(t * 2 + u.a) * 0.5, u.cz + Math.sin(u.a) * u.r); o.rotation.y += dt * 2; }); cm.emissiveIntensity = 0.5 + Math.sin(t * 1.3) * 0.25; shafts.forEach((s, i) => { s.material.opacity = 0.07 + Math.sin(t * 0.6 + i) * 0.04; }); });
  w.scan('Resonant crystal organisms', 'life', 'Crystalline lifeforms drifting between the spires. They ring faintly when the rotor idles.', V(10, 2, 8), { follow: orgs[0] }); w.scan('Singing spire', 'structure', 'A fifty-metre crystal that rings at exactly the rotor\'s frequency.', V(list[0].x, 10, list[0].z));
  w.landmark('Prism canyon', 'Sunlight enters as white and leaves as every colour.', V(0, 20, -60));
  w.artifact('heart', V(8, 0.7, -11), 'Crystal heart', 'A resonating crystal. The time rotor pulses back at it.', 0xaaeeff);
  w.ambience = { base: 261, ratio: 1.5, pad: 0.1, nf: 4000, ng: 0.03, wind: 0.06, rain: 0 }; w.atmo = { N2: 80, O2: 19, Ar: 0.5, other: 0.5, pressure: 0.8, temp: 3, hazards: ['Resonance shatter'] };
  w.gravity = 0.7; w.radius = 140; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.disguise = 'crystal'; return w;
}
function buildOcean(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x1a3a6a, mid: 0x3a8aaa, bot: 0xa0e0d8, stars: 0.35, sunI: 1.6, sunColor: 0xfff0d0, hemiSky: 0x88ddff, hemiGround: 0x205a6a, hemiI: 0.9, fog: 0.004, sunDisc: 1, day: 0.8, cloakMod: 1.0 });
  const WY = -9; w.water = { y: WY }; w.floorY = -50;
  const isl = [{ x: 0, z: 0, r: 24, y: 0 }, { x: 70, z: -30, r: 14, y: 3 }, { x: -60, z: 50, r: 16, y: -2 }, { x: 40, z: 90, r: 10, y: 6 }, { x: -90, z: -40, r: 12, y: 4 }];
  w.groundY = (x, z, py = 1e9) => { let b = w.floorY + Math.max(0, noise2(x * 0.1, z * 0.1)) * 6; for (const s of isl) if (Math.hypot(x - s.x, z - s.z) < s.r && s.y <= py + 1) b = Math.max(b, s.y + (fbm2(x * 0.1, z * 0.1) - 0.45) * 1.2); return b; };
  const rock = std(0x6a6a5a, { flatShading: true }), grass = std(0x3a9a5a, { flatShading: true });
  isl.forEach((s) => { const g = new THREE.Group(); g.position.set(s.x, s.y, s.z); g.add(new THREE.Mesh(new THREE.CylinderGeometry(s.r, s.r * 0.9, 1.4, 20).translate(0, -0.7, 0), grass)); g.add(new THREE.Mesh(new THREE.ConeGeometry(s.r * 0.9, s.r * 1.3, 10).rotateX(Math.PI).translate(0, -1.4 - s.r * 0.65, 0), rock)); w.add(g); s.g = g; });
  const ocean = new THREE.Mesh(new THREE.PlaneGeometry(900, 900, 90, 90).rotateX(-Math.PI / 2), new THREE.ShaderMaterial({ transparent: true, fog: false, uniforms: { uT: { value: 0 }, uCol: { value: new THREE.Color(0x1a8aa0) }, uSky: { value: new THREE.Color(0x9ae0e0) } },
    vertexShader: 'uniform float uT; varying vec3 vP; varying float vH; void main(){ vec3 p=position; float h=sin(p.x*0.09+uT*0.8)*0.5+sin(p.z*0.07+uT*0.6)*0.5+sin((p.x+p.z)*0.2+uT*1.4)*0.15; p.y+=h; vH=h; vec4 w=modelMatrix*vec4(p,1.); vP=w.xyz; gl_Position=projectionMatrix*viewMatrix*w; }',
    fragmentShader: 'uniform vec3 uCol,uSky; varying vec3 vP; varying float vH; void main(){ float f=clamp(length(cameraPosition-vP)*0.004,0.,1.); vec3 c=mix(uCol*(0.6+vH*0.3),uSky,f); gl_FragColor=vec4(c,0.88);\n #include <tonemapping_fragment>\n #include <colorspace_fragment>\n }' })); ocean.position.y = WY; w.add(ocean);
  const floor = new THREE.Mesh(new THREE.PlaneGeometry(900, 900, 60, 60).rotateX(-Math.PI / 2), std(0x2a5a5a)); const p = floor.geometry.attributes.position; for (let i = 0; i < p.count; i++) p.setY(i, w.groundY(p.getX(i), p.getZ(i)) - 0.1 < w.floorY ? w.floorY + Math.max(0, noise2(p.getX(i) * 0.1, p.getZ(i) * 0.1)) * 6 : w.floorY + 6); floor.geometry.computeVertexNormals(); w.add(floor);
  trees(w, 25, 6, 20, 0x5a4a3a, 0x3aaa5a, 5, 1.1, true);
  const jf = []; for (let i = 0; i < 18; i++) { const j = makeJelly([0x66e8ff, 0xff88e8, 0x88ffaa][i % 3], 1.5 + w.r() * 2.5); j.userData = { bx: (w.r() - 0.5) * 160, bz: (w.r() - 0.5) * 160, by: -14 - w.r() * 25, ph: w.r() * 6 }; j.position.set(j.userData.bx, j.userData.by, j.userData.bz); w.add(j); jf.push(j); }
  w.updaters.push((dt, t) => { ocean.material.uniforms.uT.value = t; isl.forEach((s, i) => { s.g.position.y = s.y + Math.sin(t * 0.5 + i) * 0.35; }); jf.forEach((j) => { j.position.y = j.userData.by + Math.sin(t * 0.8 + j.userData.ph) * 2; j.position.x = j.userData.bx + Math.sin(t * 0.1 + j.userData.ph) * 6; j.scale.y = 1 + Math.sin(t * 2 + j.userData.ph) * 0.1; }); });
  const arch = new THREE.Mesh(new THREE.TorusGeometry(12, 3, 6, 12, Math.PI), rock); arch.position.set(40, WY - 18, 0); w.add(arch);
  w.scan('Glowing jellyfish', 'life', 'Slow bioluminescent drifters, some larger than the TARDIS.', V(jf[0].position.x, -12, jf[0].position.z), { follow: jf[0] }); w.scan('Undersea caverns', 'structure', 'Dive from the island edge. The caverns beneath are lit from within.', V(40, WY - 10, 0));
  w.landmark('Floating archipelago', 'Islands of grass bob above an ocean that has no far shore.', V(0, 5, 0));
  w.artifact('pearl', V(40, WY - 28, 6), 'Abyssal pearl', 'A pearl in an arch deep underwater. It hums at the rotor\'s frequency.', 0xaaffff);
  w.ambience = { base: 130, ratio: 1.25, pad: 0.08, nf: 1100, ng: 0.2, wind: 0.2, rain: 0 }; w.atmo = { N2: 70, O2: 25, Ar: 0, other: 5, pressure: 1.2, temp: 24, hazards: ['Deep water'] };
  w.radius = 1e5; w.fallY = -100; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.disguise = 'rock'; w.gravity = 0.9; return w;
}
function buildFractured(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x280a3a, mid: 0x9a4a7a, bot: 0xf0b070, stars: 0.4, sunI: 1.2, hemiI: 0.8, fog: 0.005, glow: 0.4, day: 0.6, cloakMod: 0.7 });
  w.groundY = () => 0;
  w.add(new THREE.Mesh(new THREE.PlaneGeometry(500, 500).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ map: checker('#2a2030', '#3a2a48', 12), roughness: 0.4 })));
  const patch = (name, cx, cz, fn) => { const g = new THREE.Group(); g.position.set(cx, 0, cz); fn(g); w.add(g); return g; };
  const vm = buildingMat({ lit: 0xffc878, prob: 0.5 }), nm = buildingMat({ lit: 0xff40ff, lit2: 0x20e8ff, prob: 0.7, glow: 3 });
  patch('victorian', -42, -10, (g) => { for (let i = 0; i < 4; i++) { const b = new THREE.Mesh(new THREE.BoxGeometry(8, 12, 9).translate(0, 6, 0), vm); b.position.set(i * 9 - 14, 0, 0); g.add(b); w.box(-42 + b.position.x, -10, 4, 4.5); const r = new THREE.Mesh(new THREE.ConeGeometry(6.4, 4, 4).rotateY(Math.PI / 4).translate(0, 14, 0), std(0x2f2f38)); r.position.copy(b.position); g.add(r); } const lp = new THREE.Mesh(new THREE.SphereGeometry(0.3), glow(0xffd890, 3)); lp.position.set(0, 4.2, 6); g.add(lp); halos(g, [lp.position], 0xffd890, 4); });
  patch('pyramid', 0, -62, (g) => { const m = new THREE.Mesh(new THREE.ConeGeometry(26, 34, 4).rotateY(Math.PI / 4).translate(0, 17, 0), std(0xd8c28a)); g.add(m); w.box(0, -62, 18, 18); });
  patch('neon', 46, -6, (g) => { for (let i = 0; i < 5; i++) { const h = 40 + i * 14; const b = new THREE.Mesh(new THREE.BoxGeometry(8, h, 8).translate(0, h / 2, 0), nm); b.position.set(i * 10 - 20, 0, (i % 2) * 6); g.add(b); w.box(46 + b.position.x, -6 + b.position.z, 4, 4); } });
  patch('prehistoric', 0, 58, (g) => { for (let i = 0; i < 12; i++) { const t = new THREE.Mesh(new THREE.CylinderGeometry(0.3, 0.5, 10, 6).translate(0, 5, 0), std(0x4a3a2a)); t.position.set((i - 6) * 4, 0, (w.r() - 0.5) * 18); g.add(t); const c = new THREE.Mesh(new THREE.IcosahedronGeometry(3, 1).translate(0, 11, 0), std(0x1f6a2a, { flatShading: true })); c.position.copy(t.position); g.add(c); } const d = makeDino(0x5a7a3a, 1.6, 7); d.position.set(0, 0, 10); g.add(d); w.updaters.push((dt, t) => { d.position.x = Math.sin(t * 0.2) * 16; d.rotation.y = Math.cos(t * 0.2) > 0 ? 0 : Math.PI; }); });
  patch('mars', -34, 52, (g) => { const dm = new THREE.Mesh(new THREE.SphereGeometry(10, 20, 10, 0, 6.28, 0, Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0xcc8866, transparent: true, opacity: 0.35 })); g.add(dm); g.add(new THREE.Mesh(new THREE.CylinderGeometry(22, 22, 0.3, 24), std(0x8a3a20))); });
  const shards = instanced(new THREE.PlaneGeometry(1, 1), new THREE.MeshStandardMaterial({ color: 0xaaddff, emissive: 0x335577, emissiveIntensity: 0.6, transparent: true, opacity: 0.35, side: THREE.DoubleSide, roughness: 0.05 }), 160, (i, d) => { const a = w.r() * 6.28, r = 20 + w.r() * 90; d.position.set(Math.cos(a) * r, 2 + w.r() * 50, Math.sin(a) * r); d.scale.set(2 + w.r() * 12, 2 + w.r() * 12, 1); d.rotation.set(w.r() * 3, w.r() * 3, w.r() * 3); }); w.add(shards);
  w.updaters.push((dt, t) => { shards.rotation.y = t * 0.01; });
  w.scan('Overlapping epochs', 'anomaly', 'Five periods of history occupy the same ground. Walk between them: the weather changes at the seams.', V(-30, 5, 0)); w.landmark('The seam', 'Where a Victorian street meets the Cretaceous with no transition at all.', V(-20, 4, 30));
  w.artifact('seam', V(11, 0.6, -10), 'Splinter of fractured time', 'A shard that exists in several moments at once.', 0xffd080);
  w.ambience = { base: 207, ratio: 1.41, pad: 0.1, nf: 1200, ng: 0.1, wind: 0.1, rain: 0 }; w.atmo = { N2: 77, O2: 21, Ar: 0.9, other: 1.1, pressure: 1.0, temp: 14, hazards: ['Temporal overlap', 'Chronal shear'] };
  w.radius = 130; w.instability = 0.6; w.temporalNoise = 0.9; w.spawn = { x: 0, z: 2, yaw: Math.PI }; return w;
}
function buildDream(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x3a1a6a, mid: 0xd86aa0, bot: 0xffd0a0, stars: 0.6, sunI: 1.0, hemiI: 1.0, fog: 0.007, glow: 0.7, day: 0.6, cloakMod: 0.9, exposure: 1.1 });
  w.groundY = flatten((x, z) => Math.sin(x * 0.07) * Math.cos(z * 0.06) * 3, 10, 30);
  const gt = terrain(400, 120, (x, z) => w.groundY(x, z), (c, x, z) => c.setHSL((0.8 + noise2(x * 0.02, z * 0.02) * 0.3) % 1, 0.5, 0.65)); w.add(gt);
  const n = 140; const geo = new THREE.BoxGeometry(1, 1, 1); const mat = std(0xffffff, { roughness: 0.4, emissive: 0x201030, emissiveIntensity: 0.6 });
  const items = Array.from({ length: n }, (_, i) => { const a = w.r() * 6.28, r = 10 + w.r() * 100; return { x: Math.cos(a) * r, z: Math.sin(a) * r, ph: w.r() * 6, kind: i % 4, s: 3 + w.r() * 6 }; });
  const im = instanced(geo, mat, n, (i) => new THREE.Color().setHSL(w.r(), 0.5, 0.65).getHex()); w.add(im);
  w.dreamShift = () => { w.gravity = [0.3, 0.6, 1, 1.4, 0.15][Math.floor(Math.random() * 5)]; ctx.say(w.gravity < 0.5 ? 'The floor remembers it can float.' : w.gravity > 1.1 ? 'Everything is heavier than it should be.' : 'The rules shift.'); };
  let shiftT = 25; const base = { top: w.env.skyTop.clone(), mid: w.env.skyMid.clone(), bot: w.env.skyBot.clone() };
  w.updaters.push((dt, t) => {
    items.forEach((it, i) => { const k = it.kind, s = it.s * (1 + Math.sin(t * 0.3 + it.ph) * 0.5); const h = 4 + Math.abs(Math.sin(t * 0.2 + it.ph)) * 20 * (k === 0 ? 1.8 : 1);
      D.position.set(it.x, w.groundY(it.x, it.z) + (k === 3 ? 10 + Math.sin(t * 0.5 + it.ph) * 6 : h / 2), it.z); D.rotation.set(k === 1 ? Math.sin(t * 0.2 + it.ph) * 0.6 : 0, t * 0.1 * (k === 2 ? 1 : 0.2) + it.ph, k === 3 ? t * 0.2 : 0);
      D.scale.set(k === 1 ? s * 0.4 : s, k === 3 ? s : h, k === 1 ? s * 0.4 : s); D.updateMatrix(); im.setMatrixAt(i, D.matrix); });
    im.instanceMatrix.needsUpdate = true;
    w.env.skyTop.copy(base.top).offsetHSL(Math.sin(t * 0.05) * 0.1, 0, 0); w.env.skyMid.copy(base.mid).offsetHSL(Math.sin(t * 0.04 + 1) * 0.15, 0, 0); w.env.skyBot.copy(base.bot).offsetHSL(Math.sin(t * 0.03 + 2) * 0.12, 0, 0); w.env.fogColor.copy(w.env.skyBot);
    shiftT -= dt; if (shiftT < 0) { shiftT = 35 + Math.random() * 30; w.dreamShift(); }
  });
  w.dynamicEnv = true; w.scan('Shifting architecture', 'anomaly', 'The buildings rearrange when you are not looking at them — and sometimes when you are.', V(15, 8, 15)); w.scan('Gravity weather', 'anomaly', 'The local gravity changes every half-minute. A rule of this place is that rules change.', V(0, 3, 0));
  w.landmark('The unfinished staircase', 'It leads somewhere different each time you climb it.', V(-30, 8, -20)); w.artifact('dream', V(-9, 0.6, 12), 'Dreamglass', 'It shows what you are about to forget.', 0xffaaee);
  w.ambience = { base: 196, ratio: 1.33, pad: 0.1, nf: 1500, ng: 0.06, wind: 0.07, rain: 0 }; w.atmo = { N2: 75, O2: 20, Ar: 0, other: 5, pressure: 1, temp: 21, hazards: ['Shifting geometry', 'Variable gravity'] };
  w.radius = 140; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.instability = 0.7; return w;
}
function gearGeo(teeth, r, h) { const s = new THREE.Shape(); for (let i = 0; i < teeth * 2; i++) { const a = i / (teeth * 2) * 6.2832, rr = i % 2 ? r : r * 1.12; s.lineTo(Math.cos(a) * rr, Math.sin(a) * rr); } s.closePath(); const hole = new THREE.Path(); hole.absarc(0, 0, r * 0.3, 0, 6.28, true); s.holes.push(hole); return new THREE.ExtrudeGeometry(s, { depth: h, bevelEnabled: false }); }
function buildMachine(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x1a0c06, mid: 0x6a3410, bot: 0xd06a20, stars: 0, sunI: 0.9, sunColor: 0xffa860, hemiSky: 0xff9040, hemiGround: 0x201008, hemiI: 0.7, fog: 0.011, glow: 0.5, exposure: 1.15, cloakMod: 0.7, aurora: 0 });
  const metal = std(0x6a6a70, { metalness: 0.85, roughness: 0.35 }), dark = std(0x2a2a30, { metalness: 0.7, roughness: 0.5 });
  w.add(new THREE.Mesh(new THREE.PlaneGeometry(500, 500).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ map: checker('#2a2a30', '#34343c', 6), roughness: 0.5, metalness: 0.6 })));
  const gears = [];
  for (let i = 0; i < 16; i++) { const r = 8 + w.r() * 18, g = new THREE.Mesh(gearGeo(14 + Math.floor(w.r() * 10), r, 2.5), i % 2 ? metal : dark); const a = w.r() * 6.28, d = 28 + w.r() * 120; g.position.set(Math.cos(a) * d, 4 + r + w.r() * 6, Math.sin(a) * d); g.rotation.y = w.r() * 3; if (i % 3 === 0) g.rotation.x = Math.PI / 2; g.castShadow = true; g.userData = { sp: (w.r() - 0.5) * 0.8, axis: i % 3 === 0 ? 'y' : 'z' }; w.add(g); gears.push(g); w.circle(g.position.x, g.position.z, r * 0.5); }
  const pistons = []; for (let i = 0; i < 14; i++) { const p = new THREE.Group(); const rod = new THREE.Mesh(new THREE.CylinderGeometry(1.2, 1.2, 14, 10).translate(0, 7, 0), metal); const cyl = new THREE.Mesh(new THREE.CylinderGeometry(2.4, 2.4, 14, 12).translate(0, 7, 0), dark); p.add(cyl); p.add(rod); const a = i * 0.45, d = 16 + (i % 4) * 10; p.position.set(Math.cos(a) * d, 0, Math.sin(a) * d); p.userData = { rod, ph: w.r() * 6 }; w.add(p); pistons.push(p); w.circle(p.position.x, p.position.z, 2.5); }
  const slabs = []; for (let i = 0; i < 6; i++) { const s = new THREE.Mesh(new THREE.BoxGeometry(24, 18, 24).translate(0, 9, 0), dark); s.userData = { ax: (w.r() - 0.5) * 2, bx: (w.r() - 0.5) * 140, bz: (w.r() - 0.5) * 140, ph: w.r() * 6 }; w.add(s); slabs.push(s); }
  const belts = []; for (let i = 0; i < 4; i++) { const b = new THREE.Mesh(new THREE.BoxGeometry(60, 0.4, 3), glow(0xffa040, 0.5)); b.position.set(-30 + i * 22, 0.3, 18 + i * 5); b.rotation.y = i * 0.2; w.add(b); belts.push(b); }
  const bots = []; for (let i = 0; i < 10; i++) { const b = makeBot(i * 11); b.position.set((w.r() - 0.5) * 60, 0, (w.r() - 0.5) * 60); w.add(b); bots.push(b); }
  w.updaters.push((dt, t) => {
    gears.forEach((g) => { if (g.userData.axis === 'y') g.rotation.z += g.userData.sp * dt; else g.rotation.z += g.userData.sp * dt; });
    pistons.forEach((p) => { p.userData.rod.position.y = (Math.sin(t * 1.4 + p.userData.ph) * 0.5 + 0.5) * 8; });
    slabs.forEach((s) => { s.position.x = s.userData.bx + Math.sin(t * 0.05 + s.userData.ph) * 30; s.position.z = s.userData.bz + Math.cos(t * 0.04 + s.userData.ph) * 30; });
    belts.forEach((b, i) => { b.material.emissiveIntensity = 0.4 + Math.sin(t * 3 + i) * 0.2; });
    bots.forEach((b) => { const u = b.userData; u.t += dt; u.ang += Math.sin(u.t * 0.4) * dt; b.position.x += Math.cos(u.ang) * u.speed * dt; b.position.z += Math.sin(u.ang) * u.speed * dt; b.rotation.y = -u.ang + Math.PI / 2; if (b.position.length() > 70) u.ang += Math.PI; });
  });
  w.scan('Maintenance automata', 'life', 'Machine citizens tend the machine. They do not acknowledge you, but they step around the TARDIS.', V(bots[0].position.x, 1, bots[0].position.z), { follow: bots[0] }); w.scan('Prime escapement', 'structure', 'A gear the size of a stadium, advancing the universe one tooth at a time.', V(gears[0].position.x, gears[0].position.y, gears[0].position.z));
  w.landmark('The slow engine', 'Walls that sliding apart are only the size of rooms.', V(0, 9, -40)); w.artifact('cog', V(8, 0.6, 10), 'Governor cog', 'A cog from the machine that governs the machines.', 0xffa040);
  w.ambience = { base: 82, ratio: 1.5, pad: 0.06, nf: 900, ng: 0.3, wind: 0.05, rain: 0 }; w.atmo = { N2: 75, O2: 10, Ar: 0, other: 15, pressure: 1.4, temp: 48, hazards: ['Crushing machinery', 'Heat'] };
  w.radius = 125; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.disguise = 'crate'; w.hunters = true; return w;
}
function buildLast(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x000000, mid: 0x0a0408, bot: 0x241014, stars: 0.35, sunI: 0.35, sunColor: 0xff5030, hemiSky: 0x401a18, hemiGround: 0x100808, hemiI: 0.5, fog: 0.006, exposure: 1.0, glow: 0.2, cloakMod: 1.2, day: 0.1 });
  w.groundY = flatten((x, z) => (fbm2(x * 0.03, z * 0.03) - 0.4) * 7, 12, 40);
  w.add(terrain(500, 140, w.groundY, (c, x, z) => { const n = noise2(x * 0.15, z * 0.15) * 0.08; const crack = Math.abs(noise2(x * 0.08, z * 0.08) - 0.5) < 0.02; c.setRGB(0.1 + n + (crack ? 0.5 : 0), 0.07 + n + (crack ? 0.15 : 0), 0.07 + n); }));
  const mat = buildingMat({ lit: 0xff6040, prob: 0.04, glow: 2 });
  w.add(instanced(new THREE.BoxGeometry(1, 1, 1), mat, 24, (i, d) => { const a = i * 0.9, r = 30 + (i % 6) * 18; const h = 20 + w.r() * 60; const x = Math.cos(a) * r, z = Math.sin(a) * r; d.position.set(x, h / 2 + w.groundY(x, z) - 2, z); d.scale.set(6 + w.r() * 10, h, 6 + w.r() * 10); d.rotation.set((w.r() - 0.5) * 0.12, w.r(), (w.r() - 0.5) * 0.12); w.circle(x, z, 5); return 0x1a1214; }, { shadow: true }));
  const statues = []; for (let i = 0; i < 12; i++) { const g = new THREE.Group(); g.add(new THREE.Mesh(new THREE.CylinderGeometry(0.8, 1, 3, 6).translate(0, 1.5, 0), std(0x2a2226))); g.add(new THREE.Mesh(new THREE.SphereGeometry(1.1, 8, 6).translate(0, 4, 0), std(0x2a2226))); const a = i * 0.55 + 0.4, r = 10 + (i % 3) * 8; g.position.set(Math.cos(a) * r, w.groundY(Math.cos(a) * r, Math.sin(a) * r), Math.sin(a) * r); g.rotation.z = (w.r() - 0.5) * 0.4; g.rotation.y = w.r() * 6; w.add(g); statues.push(g); w.circle(g.position.x, g.position.z, 1.2); }
  const star = new THREE.Mesh(new THREE.SphereGeometry(40, 24, 16), new THREE.MeshBasicMaterial({ color: 0xff3a1a, fog: false })); star.position.set(-180, 90, -330); w.add(star);
  const stars = []; for (let i = 0; i < 40; i++) { const a = w.r() * 6.28, e = 0.2 + w.r() * 1.2; stars.push(V(Math.cos(a) * Math.cos(e) * 480, Math.sin(e) * 480, Math.sin(a) * Math.cos(e) * 480)); } halos(w, stars, 0xff9a70, 6, 0.9);
  w.updaters.push((dt, t) => { star.material.color.setHSL(0.02, 1, 0.35 + Math.sin(t * 0.2) * 0.04); });
  w.scan('Last-light monument', 'structure', 'A monument to species that no longer exist. The inscriptions have gone but the weight of them remains.', V(30, 8, 20)); w.scan('Fading star', 'anomaly', 'The final star in this universe, almost cold. Time moves oddly near it.', star.position); w.landmark('Remnant city', 'The skeletons of towers lean in the dark, gently, towards the last star.', V(0, 30, -80));
  w.artifact('ember', V(-11, 0.6, 9), 'Last ember', 'A coal that has been burning since the beginning.', 0xff6040);
  w.timeScale = 0.35; w.ambience = { base: 49, ratio: 1.06, pad: 0.14, nf: 200, ng: 0.05, wind: 0.08, rain: 0 }; w.atmo = { N2: 80, O2: 0, Ar: 0, other: 20, pressure: 0.3, temp: -120, hazards: ['Slowed time (0.35×)', 'No breathable air'] };
  w.radius = 140; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.instability = 0.3; w.temporalNoise = 0.9; w.rare = true; return w;
}
function buildGraveyard(ctx, def) {
  const w = dimBase(ctx, def, { top: 0x000104, mid: 0x04060c, bot: 0x0a1020, stars: 0.9, sunI: 0.25, sunColor: 0x6a8aff, hemiSky: 0x203a6a, hemiGround: 0x080a14, hemiI: 0.5, fog: 0.008, glow: 0.15, day: 0.1, cloakMod: 0.6 });
  w.groundY = flatten((x, z) => (fbm2(x * 0.02, z * 0.02) - 0.4) * 4, 8, 40);
  w.add(terrain(500, 120, w.groundY, (c, x, z) => { const n = noise2(x * 0.2, z * 0.2) * 0.06; c.setRGB(0.05 + n, 0.06 + n, 0.09 + n); }));
  const cols = [0x103a8a, 0x5a1a1a, 0x1a4a2a, 0x3a3a3a, 0x4a2a6a];
  for (let i = 0; i < 26; i++) { const t = new THREE.Group(); const a = w.r() * 6.28, r = 14 + w.r() * 110; const m = std(cols[i % cols.length], { roughness: 0.8 }); t.add(new THREE.Mesh(new THREE.BoxGeometry(1.3, 2.4, 1.3).translate(0, 1.2, 0), m)); t.add(new THREE.Mesh(new THREE.BoxGeometry(1.5, 0.2, 1.5).translate(0, 2.5, 0), m)); const lamp = new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.14, 0.3, 8).translate(0, 2.85, 0), glow(0x4a7aff, i % 9 === 0 ? 2.5 : 0)); t.add(lamp); t.position.set(Math.cos(a) * r, w.groundY(Math.cos(a) * r, Math.sin(a) * r) - w.r() * 0.7, Math.sin(a) * r); t.rotation.set((w.r() - 0.5) * 0.7, w.r() * 6, (w.r() - 0.5) * 0.7); w.add(t); w.circle(t.position.x, t.position.z, 1.2); if (i % 9 === 0) w.updaters.push((dt, tt) => { lamp.material.emissiveIntensity = 1.2 + Math.sin(tt * (3 + i)) * 1.1; }); }
  w.scan('Derelict vessels', 'structure', 'Dozens of ships like yours, every one of them dead. One lamp flickers.', V(20, 2, 30)); w.landmark('The graveyard', 'Where the ships come when they stop being ships.', V(0, 5, -60)); w.scan('Faint homing signal', 'anomaly', 'Something in this field answers the TARDIS in a language older than both of you.', V(-30, 2, -20));
  w.artifact('heartbeat', V(10, 0.6, -14), 'A ship\'s dying heartbeat', 'A time rotor that stopped, remembered.', 0x4a7aff);
  w.ambience = { base: 61, ratio: 1.2, pad: 0.13, nf: 300, ng: 0.06, wind: 0.14, rain: 0 }; w.atmo = { N2: 78, O2: 4, Ar: 0, other: 18, pressure: 0.7, temp: -30, hazards: ['Residual temporal radiation'] };
  w.radius = 130; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.rare = true; w.instability = 0.2; return w;
}

// ------------------------------------------------------------------ procedural dimension framework
const PROPS = {
  spires: (w, c) => w.add(instanced(new THREE.ConeGeometry(1, 1, 5), new THREE.MeshStandardMaterial({ color: c.a, emissive: c.b, emissiveIntensity: 0.4, flatShading: true }), 60, (i, d) => { const a = w.r() * 6.28, r = 12 + w.r() * 110, h = 6 + w.r() * 50; d.position.set(Math.cos(a) * r, h / 2, Math.sin(a) * r); d.scale.set(2 + h * 0.1, h, 2 + h * 0.1); w.circle(d.position.x, d.position.z, 1 + h * 0.05); })),
  orbs: (w, c) => { const o = instanced(new THREE.SphereGeometry(1, 14, 10), glow(c.b, 1.4), 40, (i, d) => { const a = w.r() * 6.28, r = 10 + w.r() * 100; d.position.set(Math.cos(a) * r, 3 + w.r() * 30, Math.sin(a) * r); d.scale.setScalar(1 + w.r() * 5); }); w.add(o); w.updaters.push((dt, t) => { o.rotation.y = t * 0.01; }); },
  rings: (w, c) => { for (let i = 0; i < 8; i++) { const r = new THREE.Mesh(new THREE.TorusGeometry(8 + w.r() * 18, 0.6, 8, 40), glow(c.b, 1.2)); const a = w.r() * 6.28, d = 25 + w.r() * 80; r.position.set(Math.cos(a) * d, 12 + w.r() * 30, Math.sin(a) * d); r.rotation.set(w.r() * 3, w.r() * 3, 0); w.add(r); w.updaters.push((dt) => { r.rotation.z += dt * 0.2; }); } },
  monoliths: (w, c) => w.add(instanced(new THREE.BoxGeometry(1, 1, 1), std(c.a, { metalness: 0.7, roughness: 0.2 }), 30, (i, d) => { const a = w.r() * 6.28, r = 14 + w.r() * 100, h = 10 + w.r() * 40; d.position.set(Math.cos(a) * r, h / 2, Math.sin(a) * r); d.scale.set(3, h, 1.2); d.rotation.y = w.r() * 3; w.box(d.position.x, d.position.z, 2.3, 2.3); })),
};
export function spawnProceduralDimension(seed) {
  const id = `proc_${seed}`; if (WORLDS[id]) return WORLDS[id];
  const r = rng(seed), hue = r(); const col = (h, s, l) => new THREE.Color().setHSL(h % 1, s, l).getHex();
  const names = ['Veil', 'Hollow', 'Cascade', 'Reverie', 'Lattice', 'Meridian', 'Echo', 'Gloam'], nn = r.pick(names) + ' ' + r.pick(['Reach', 'Expanse', 'Fold', 'Verge']);
  const kinds = Object.keys(PROPS).sort(() => r() - 0.5).slice(0, 2);
  const dimKey = [r.int(1, 9), r.int(1, 9), r.int(1, 9)];
  return defineWorld({
    id, name: nn, kind: 'dimension', dimKey, danger: r.int(1, 3), stability: 0.5 + r() * 0.4, hint: 'A procedurally condensed reality.', generated: true, gravity: 0.5 + r() * 0.9,
    build(ctx, def) {
      const w = dimBase(ctx, def, { top: col(hue, 0.6, 0.12), mid: col(hue + 0.08, 0.5, 0.35), bot: col(hue + 0.15, 0.5, 0.6), stars: 0.5, sunI: 1.2, hemiI: 0.8, fog: 0.007, glow: 0.4, day: 0.5, exposure: 1.1 });
      const pal = { a: col(hue + 0.5, 0.4, 0.4), b: col(hue + 0.3, 0.9, 0.6) };
      w.groundY = flatten((x, z) => (fbm2(x * 0.03, z * 0.03) - 0.4) * 8, 10, 35);
      w.add(terrain(400, 120, w.groundY, (c, x, z) => c.setHSL((hue + 0.05 + noise2(x * 0.03, z * 0.03) * 0.1) % 1, 0.4, 0.3 + noise2(x * 0.2, z * 0.2) * 0.1)));
      kinds.forEach((k) => PROPS[k](w, pal));
      w.scan('Condensed anomaly', 'anomaly', 'This reality was assembled a moment ago from the shape of your intent.', V(5, 3, 8)); w.artifact('seed', V(7, 0.6, -9), 'Seed of a reality', 'A seed that, planted, would become another world.', pal.b);
      w.ambience = { base: 80 + r() * 200, ratio: 1.2 + r() * 0.3, pad: 0.09, nf: 800, ng: 0.1, wind: 0.1, rain: 0 }; w.atmo = { N2: 70, O2: 20, Ar: 1, other: 9, pressure: 1, temp: 10 + r() * 20, hazards: ['Unmapped'] };
      w.radius = 130; w.spawn = { x: 0, z: 2, yaw: Math.PI }; w.instability = 0.5; return w;
    },
  });
}

// ------------------------------------------------------------------ registry
defineWorld({ id: 'earth_london', name: 'Earth · London', kind: 'earth', planet: 'Earth', region: 'Thames Valley', danger: 1, stability: 0.92, build: buildEarthLondon });
defineWorld({ id: 'earth_giza', name: 'Earth · Giza', kind: 'earth', planet: 'Earth', region: 'Nile / Giza plateau', danger: 1, stability: 0.9, build: buildGiza });
defineWorld({ id: 'mars', name: 'Mars · Olympus Plain', kind: 'planet', planet: 'Mars', region: 'Amazonis Planitia', danger: 2, stability: 0.85, build: buildMars });
defineWorld({ id: 'kepler', name: 'Kepler Terminus', kind: 'planet', planet: 'Kepler-9 Terminus', region: 'Kepler-9 system', danger: 3, stability: 0.75, build: buildKepler });
const dim = (id, name, key, hint, extra, build) => defineWorld({ id, name, kind: 'dimension', dimKey: key, hint, build, ...extra });
dim('mirror', 'The Mirror Dimension', [7, 3, 1], 'Where every surface remembers the other side.', { danger: 2, stability: 0.5, gravity: 0.8, cloakMod: 0.8 }, buildMirror);
dim('void', 'The Void', [0, 0, 9], 'Beyond the last lamp.', { danger: 3, stability: 0.55, gravity: 0.25 }, buildVoid);
dim('library', 'The Infinite Library', [4, 2, 0], 'Every ending is shelved here.', { danger: 1, stability: 0.8 }, buildLibrary);
dim('neon', 'The Neon Universe', [3, 3, 3], 'Three ones, three times, but louder.', { danger: 3, stability: 0.6, cloakMod: 0.5 }, buildNeonUniverse);
dim('crystal', 'The Crystal World', [5, 1, 8], 'A universe that refracts.', { danger: 2, stability: 0.7, gravity: 0.7 }, buildCrystal);
dim('ocean', 'The Ocean Dimension', [2, 6, 4], 'There is no shore.', { danger: 2, stability: 0.7, gravity: 0.9 }, buildOcean);
dim('fractured', 'The Fractured Timeline', [9, 9, 1], 'Nine and nine and one: the rest are missing.', { danger: 4, stability: 0.35, cloakMod: 0.7 }, buildFractured);
dim('dream', 'The Dream Dimension', [1, 4, 7], 'Count the way you fall asleep.', { danger: 2, stability: 0.45 }, buildDream);
dim('machine', 'The Machine Universe', [6, 0, 6], 'A reality built by something that preferred gears.', { danger: 3, stability: 0.65, cloakMod: 0.7 }, buildMachine);
dim('last', 'The Last Universe', [0, 9, 0], 'Where the stars go out.', { danger: 4, stability: 0.4, rare: true, requires: () => G.mode === 'sandbox' || Object.keys(G.disc.dims).length >= 3 }, buildLast);
defineWorld({ id: 'graveyard', name: 'The Graveyard of Ships', kind: 'planet', planet: 'Unknown', region: 'Uncharted', danger: 3, stability: 0.5, rare: true, secret: true, build: buildGraveyard });
WORLDS.graveyard.hint = 'Answers to something older than the archive.';

const P = (worldId, name, year, month, day, hour, category, desc, extra = {}) => DESTS.push({ worldId, name, year, month, day, hour, category, desc, dim: [0, 0, 0], ...extra });
P('earth_giza', 'Giza · The Great Pyramid rises', -2560, 4, 12, 9, 'Ancient civilizations', 'The plateau at dawn. Fifty thousand people and a pyramid half built.');
P('earth_giza', 'Giza · Napoleon\'s campaign', 1798, 7, 21, 8, 'Age of exploration', 'Soldiers, scholars and camels at the foot of the pyramids.');
P('earth_giza', 'Giza · Shield-domed monuments', 2430, 3, 3, 17, 'Futures', 'The pyramids sit beneath a hologrid dome; tourists arrive by sky-lift.');
P('earth_london', 'London · Anglo-Saxon settlement (Halley\'s Comet)', 1066, 4, 24, 22, 'Medieval cities', 'Huts on the Thames and a comet in the sky.');
P('earth_london', 'London · Roman Londinium', 120, 5, 3, 11, 'Ancient civilizations', 'Stone and tile at the edge of the empire.');
P('earth_london', 'London · Plague year', 1348, 9, 6, 15, 'Medieval cities', 'Timber houses, market stalls and silence.');
P('earth_london', 'London · Armada summer', 1588, 7, 29, 14, 'Renaissance & exploration', 'The river is full of masts. Everything smells of tar.');
P('earth_london', 'London · The Great Fire', 1666, 9, 3, 23, 'Renaissance & exploration', 'Pudding Lane is burning. A rare temporal window.', { rare: true });
P('earth_london', 'London · Victorian fog', 1888, 10, 12, 21, 'Victorian London', 'Gaslight, brick, cobbles and fog. The clock tower tolls.');
P('earth_london', 'London · Swinging 1966', 1966, 7, 30, 15, 'The 1960s', 'Bright cars, red buses, and a very natural place for a police box.');
P('earth_london', 'London · Near-future 2085', 2085, 5, 10, 18, 'A futuristic present', 'The skyline is greener, taller, and quiet.');
P('earth_london', 'New London · Neon megacity', 2487, 11, 2, 23, 'Neon megacities', 'A kilometre-high city, lit like a circuit board.');
P('earth_london', 'Post-human London', 190000, 6, 2, 16, 'Post-human worlds', 'Overgrown arcologies and glowing flora. Nobody has been here for ages.');
P('earth_london', 'Thames, 66 million years ago', -66000000, 6, 15, 12, 'Earth millions of years ago', 'Ferns, volcanoes, and a very large neighbour.');
P('earth_london', 'Earth, 80 million years from now', 80000000, 3, 8, 19, 'Earth millions of years hence', 'Continents drifted. Something else has built here.');
P('mars', 'Mars · Before anyone', 1995, 6, 1, 13, 'Colonies', 'A rover nobody has visited yet and a very quiet sky.');
P('mars', 'Mars · Olympus Colony', 2210, 4, 20, 10, 'Distant human colonies', 'Pressure domes, rovers, and a landing day parade.');
P('mars', 'Mars · Terraformed', 5400, 8, 9, 9, 'Terraforming colonies', 'Lakes and forests under a blue sky.');
P('kepler', 'Kepler Terminus · City above the star', 4200, 2, 14, 12, 'Cities suspended above planets', 'A bright floating platform city above a swelling star.');
P('kepler', 'Kepler Terminus · The dying star', 8421, 9, 30, 1, 'Dying stars', 'The red giant fills the sky. The lights are going out.');
P('graveyard', 'Signal source (unlisted)', 0, 1, 1, 0, 'Unlisted', 'A destination that was never in the archive.', { secret: true, dim: [0, 0, 0] });
