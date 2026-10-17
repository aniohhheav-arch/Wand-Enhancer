// Toolkit shared by all destinations: sky/lighting by time of day, instancing, window-lit building shader,
// terrain, creatures, collectibles/scannables, and the WorldBase container every generator returns.
import { THREE, clamp, lerp, smooth, rng, canvasTex } from './util.js';

export function noise2(x, z) {
  const f = (a, b) => { const s = Math.sin(a * 127.1 + b * 311.7) * 43758.5453; return s - Math.floor(s); };
  const ix = Math.floor(x), iz = Math.floor(z), fx = x - ix, fz = z - iz;
  const u = fx * fx * (3 - 2 * fx), v = fz * fz * (3 - 2 * fz);
  return lerp(lerp(f(ix, iz), f(ix + 1, iz), u), lerp(f(ix, iz + 1), f(ix + 1, iz + 1), u), v);
}
export const fbm2 = (x, z, o = 4) => { let a = 0.5, s = 0, k = 1; for (let i = 0; i < o; i++) { s += a * noise2(x * k, z * k); k *= 2; a *= 0.5; } return s; };
const C = (h) => new THREE.Color(h);
const mixC = (a, b, t) => a.clone().lerp(b, clamp(t));

// ------------------------------------------------------------------ sky + lighting from the hour of day
export function skyEnv(hour, o = {}) {
  const h = ((hour % 24) + 24) % 24, el = Math.sin((h - 6) / 12 * Math.PI);
  const day = smooth(-0.12, 0.3, el), twi = clamp(1 - Math.abs(el) * 3.2);
  const oc = o.overcast || 0;
  const night = [C(o.nightTop ?? 0x02051a), C(o.nightMid ?? 0x0a1230), C(o.nightBot ?? 0x14193a)];
  const dayc = [C(o.dayTop ?? 0x2a5fc0), C(o.dayMid ?? 0x6fa0e0), C(o.dayBot ?? 0xbcd4ee)];
  const tw = [C(o.twTop ?? 0x2a2a60), C(o.twMid ?? 0xc0607a), C(o.twBot ?? 0xffa860)];
  const grey = C(o.grey ?? 0x7a8088);
  const cols = [0, 1, 2].map((i) => {
    let c = mixC(night[i], dayc[i], day);
    c = mixC(c, tw[i], twi * 0.85);
    return mixC(c, grey.clone().multiplyScalar(0.25 + day * 0.75), oc * 0.7);
  });
  const az = (h - 6) / 12 * Math.PI;
  const sun = new THREE.Vector3(Math.cos(az), Math.sin(az), 0.35).normalize();
  const dir = el > -0.05 ? sun : sun.clone().multiplyScalar(-1);
  if (dir.y < 0.08) dir.y = 0.08 + Math.abs(dir.y) * 0.1;
  const sunI = (el > 0 ? 2.0 * Math.pow(Math.max(0, el), 0.6) : 0.28) * (1 - oc * 0.65);
  const sunColor = el > 0 ? mixC(C(0xfff2dd), C(0xffa060), twi) : C(0x8fa8ff);
  const fogC = cols[2].clone();
  return {
    skyTop: cols[0], skyMid: cols[1], skyBot: cols[2], stars: clamp(1 - day * 1.1 - oc * 0.5), sunDir: dir, sunColor, sunI: sunI * (o.sunScale ?? 1),
    hemiSky: mixC(cols[1], C(0xffffff), 0.25), hemiGround: C(o.ground ?? 0x3a3a34), hemiI: (0.22 + day * 0.42) * (o.hemiScale ?? 1),
    fogColor: fogC, fogDensity: (o.fog ?? 0.004) * (1 + oc * 1.5), exposure: o.exposure ?? 1.0, sunDisc: el > -0.02 ? 1 : 0, moon: el <= -0.02 ? 1 : 0,
    aurora: o.aurora || 0, glow: o.glow || 0, day,
  };
}
// fully custom (dimension) environment
export function customEnv(o) {
  return {
    skyTop: C(o.top), skyMid: C(o.mid ?? o.top), skyBot: C(o.bot ?? o.mid ?? o.top), stars: o.stars ?? 0.5, sunDir: o.sunDir || new THREE.Vector3(0.3, 0.6, 0.2).normalize(),
    sunColor: C(o.sunColor ?? 0xffffff), sunI: o.sunI ?? 1, hemiSky: C(o.hemiSky ?? o.mid ?? o.top), hemiGround: C(o.hemiGround ?? 0x222222), hemiI: o.hemiI ?? 0.6,
    fogColor: C(o.fogColor ?? o.bot ?? o.mid ?? o.top), fogDensity: o.fog ?? 0.006, exposure: o.exposure ?? 1, sunDisc: o.sunDisc ?? 0, moon: 0, aurora: o.aurora || 0, glow: o.glow || 0, day: o.day ?? 0.5,
  };
}

// ------------------------------------------------------------------ materials
export function buildingMat(o = {}) {
  const m = new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: o.rough ?? 0.85, metalness: o.metal ?? 0.08 });
  const u = {
    uLitCol: { value: C(o.lit ?? 0xffd9a0) }, uLit2: { value: C(o.lit2 ?? o.lit ?? 0xffd9a0) }, uLitProb: { value: o.prob ?? 0.5 },
    uWin: { value: new THREE.Vector2(o.winW ?? 2.2, o.winH ?? 3) }, uNight: { value: o.night ?? 1 }, uGlow: { value: o.glow ?? 1.8 },
  };
  m.userData.u = u;
  m.onBeforeCompile = (sh) => {
    Object.assign(sh.uniforms, u);
    sh.vertexShader = sh.vertexShader.replace('void main() {', 'varying vec3 vLoc; varying vec3 vNrm;\nvoid main() {')
      .replace('#include <begin_vertex>', `#include <begin_vertex>
        #ifdef USE_INSTANCING
        vLoc=(instanceMatrix*vec4(position,1.)).xyz;
        #else
        vLoc=position;
        #endif
        vNrm=normal;`);
    sh.fragmentShader = sh.fragmentShader.replace('void main() {', `uniform vec3 uLitCol,uLit2; uniform float uLitProb,uNight,uGlow; uniform vec2 uWin; varying vec3 vLoc; varying vec3 vNrm;
      float bh(vec2 p){ return fract(sin(dot(p,vec2(127.1,311.7)))*43758.5453); }
      void main() {`).replace('#include <emissivemap_fragment>', `#include <emissivemap_fragment>
      if(abs(vNrm.y)<0.5 && uNight>0.01){
        vec2 g=vec2((abs(vNrm.x)>0.5?vLoc.z:vLoc.x)/uWin.x, vLoc.y/uWin.y); vec2 cell=floor(g); vec2 f=fract(g);
        float w=step(0.2,f.x)*step(f.x,0.8)*step(0.25,f.y)*step(f.y,0.75);
        float seed=bh(cell+floor(vLoc.xz*0.21)); float on=step(seed,uLitProb);
        totalEmissiveRadiance+=mix(uLitCol,uLit2,bh(cell*1.7+3.))*w*on*uNight*uGlow;
      }`);
  };
  m.customProgramCacheKey = () => 'bldg';
  return m;
}
export const std = (color, o = {}) => new THREE.MeshStandardMaterial({ color, roughness: 0.85, ...o });
export const glow = (color, k = 1.5, o = {}) => new THREE.MeshStandardMaterial({ color: 0x000000, emissive: color, emissiveIntensity: k, ...o });
export const basic = (color, o = {}) => new THREE.MeshBasicMaterial({ color, fog: true, ...o });

// ------------------------------------------------------------------ instancing
const _d = new THREE.Object3D(), _c = new THREE.Color();
export function instanced(geo, mat, n, fill, o = {}) {
  const im = new THREE.InstancedMesh(geo, mat, n);
  for (let i = 0; i < n; i++) {
    _d.position.set(0, 0, 0); _d.rotation.set(0, 0, 0); _d.scale.set(1, 1, 1);
    const col = fill(i, _d);
    _d.updateMatrix(); im.setMatrixAt(i, _d.matrix);
    if (col != null) im.setColorAt(i, _c.set(col));
  }
  im.instanceMatrix.needsUpdate = true; if (im.instanceColor) im.instanceColor.needsUpdate = true;
  im.castShadow = !!o.shadow; im.receiveShadow = true; im.frustumCulled = false;
  return im;
}
export function terrain(size, seg, hFn, colorFn) {
  const g = new THREE.PlaneGeometry(size, size, seg, seg).rotateX(-Math.PI / 2);
  const p = g.attributes.position, col = new Float32Array(p.count * 3), c = new THREE.Color();
  for (let i = 0; i < p.count; i++) {
    const x = p.getX(i), z = p.getZ(i), y = hFn(x, z); p.setY(i, y);
    colorFn ? colorFn(c, x, z, y) : c.set(0x888888); col[i * 3] = c.r; col[i * 3 + 1] = c.g; col[i * 3 + 2] = c.b;
  }
  g.setAttribute('color', new THREE.BufferAttribute(col, 3)); g.computeVertexNormals();
  const m = new THREE.Mesh(g, new THREE.MeshStandardMaterial({ vertexColors: true, roughness: 0.95, metalness: 0 }));
  m.receiveShadow = true; return m;
}
export const flatten = (h, r0 = 14, r1 = 40) => (x, z) => h(x, z) * smooth(r0, r1, Math.hypot(x, z));

// ------------------------------------------------------------------ creature primitives
export function makeDino(color, scale, seed) {
  const r = rng(seed), g = new THREE.Group(), m = std(color, { roughness: 0.7 });
  const body = new THREE.Mesh(new THREE.SphereGeometry(1, 12, 10).scale(1.5, 0.9, 0.8), m); body.position.y = 1.9; g.add(body);
  const neck = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.4, 2.2, 8), m); neck.position.set(1.6, 3, 0); neck.rotation.z = -0.7; g.add(neck);
  const head = new THREE.Mesh(new THREE.SphereGeometry(0.4, 10, 8).scale(1.5, 0.9, 0.9), m); head.position.set(2.6, 4, 0); g.add(head);
  const tail = new THREE.Mesh(new THREE.ConeGeometry(0.5, 3.4, 8), m); tail.position.set(-2.4, 1.7, 0); tail.rotation.z = Math.PI / 2 + 0.2; g.add(tail);
  const legs = [];
  for (const [x, z] of [[0.7, 0.5], [0.7, -0.5], [-0.7, 0.5], [-0.7, -0.5]]) { const l = new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.28, 1.5, 6), m); l.position.set(x, 0.75, z); g.add(l); legs.push(l); }
  g.scale.setScalar(scale); g.traverse((o) => { if (o.isMesh) o.castShadow = true; });
  g.userData = { legs, phase: r() * 6, speed: 0.4 + r() * 0.4, ang: r() * 6.28, cx: 0, cz: 0, rad: 10 };
  return g;
}
export function makeJelly(color, s = 1) {
  const g = new THREE.Group();
  const dome = new THREE.Mesh(new THREE.SphereGeometry(1, 16, 10, 0, 6.28, 0, 1.6).scale(1, 0.7, 1), new THREE.MeshStandardMaterial({ color: 0x000000, emissive: color, emissiveIntensity: 1.2, transparent: true, opacity: 0.7 }));
  g.add(dome);
  const pts = []; for (let i = 0; i < 10; i++) { const a = i / 10 * 6.28; pts.push(Math.cos(a) * 0.7, 0, Math.sin(a) * 0.7, Math.cos(a) * 0.5, -2.5, Math.sin(a) * 0.5); }
  const lg = new THREE.BufferGeometry(); lg.setAttribute('position', new THREE.Float32BufferAttribute(pts, 3));
  g.add(new THREE.LineSegments(lg, new THREE.LineBasicMaterial({ color, transparent: true, opacity: 0.6 })));
  g.scale.setScalar(s); return g;
}
export function makeBot(seed) {
  const r = rng(seed), g = new THREE.Group(), m = std(0x6a707a, { metalness: 0.8, roughness: 0.4 });
  g.add(new THREE.Mesh(new THREE.BoxGeometry(0.8, 0.9, 0.8), m).translateY(0.8));
  g.add(new THREE.Mesh(new THREE.BoxGeometry(0.5, 0.4, 0.5), m).translateY(1.5));
  const eye = new THREE.Mesh(new THREE.SphereGeometry(0.12, 8, 8), glow(0xff8a20, 3)); eye.position.set(0, 1.5, 0.27); g.add(eye);
  for (const x of [-0.3, 0.3]) g.add(new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.1, 0.5, 6), m).translateX(x).translateY(0.25));
  g.userData = { ang: r() * 6.28, speed: 0.6 + r(), t: r() * 10 }; g.traverse((o) => { if (o.isMesh) o.castShadow = true; });
  return g;
}
export function makePlant(color, seed) {
  const r = rng(seed), g = new THREE.Group();
  const stem = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.06, 1.4, 6), std(0x2a5a30)); stem.position.y = 0.7; g.add(stem);
  for (let i = 0; i < 5; i++) {
    const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.28, 8, 6).scale(1, 0.18, 0.5), new THREE.MeshStandardMaterial({ color: 0x1a3a22, emissive: color, emissiveIntensity: 0.9 }));
    leaf.position.set(Math.cos(i * 1.26) * 0.3, 0.9 + i * 0.12, Math.sin(i * 1.26) * 0.3); leaf.rotation.y = -i * 1.26; leaf.rotation.z = 0.4; g.add(leaf);
  }
  const bulb = new THREE.Mesh(new THREE.SphereGeometry(0.14, 10, 8), glow(color, 2.5)); bulb.position.y = 1.5; g.add(bulb);
  g.userData.phase = r() * 6; return g;
}

// ------------------------------------------------------------------ the container each world generator returns
export class WorldBase {
  constructor(ctx, def) {
    this.ctx = ctx; this.def = def; this.group = new THREE.Group(); this.r = rng(ctx.seed);
    this.colliders = []; this.interactables = []; this.scannables = []; this.updaters = []; this.collectibles = [];
    this.groundY = () => 0; this.radius = 140; this.gravity = def.gravity ?? 1; this.fallY = -60; this.flipPads = [];
    this.env = skyEnv(ctx.hour); this.weather = { rain: 0, wet: 0 };
    this.ambience = { base: 110, ratio: 1.5, pad: 0.07, nf: 800, ng: 0.15, wind: 0.12, rain: 0 };
    this.atmo = { N2: 78, O2: 21, Ar: 0.9, other: 0.1, pressure: 1.0, temp: 15, hazards: [] };
    this.spawn = { x: 0, z: 0, yaw: 0 }; this.disguise = 'rock'; this.hunters = false; this.temporalNoise = 0.1; this.instability = 0.05;
    this.water = null; this.dispose = null; this.overlay = null;
  }
  add(o) { this.group.add(o); return o; }
  box(cx, cz, hx, hz) { this.colliders.push({ x: cx, z: cz, hx, hz }); }
  circle(cx, cz, r) { this.colliders.push({ x: cx, z: cz, r }); }
  scan(name, type, desc, pos, extra = {}) { this.scannables.push({ id: `${this.def.id}:${name}`, name, type, desc, pos: pos.clone ? pos.clone() : new THREE.Vector3(...pos), ...extra }); }
  artifact(id, pos, name, lore, color = 0x66ddff) {
    const key = `${this.def.id}:${id}`;
    if (this.ctx.visit.collected[key]) return null;
    const g = new THREE.Group(); g.position.copy(pos);
    const core = new THREE.Mesh(new THREE.OctahedronGeometry(0.28), glow(color, 3)); g.add(core);
    const ring = new THREE.Mesh(new THREE.TorusGeometry(0.45, 0.02, 6, 24), glow(0xffffff, 2)); g.add(ring);
    const hit = new THREE.Mesh(new THREE.SphereGeometry(0.9, 6, 6), new THREE.MeshBasicMaterial({ visible: false })); g.add(hit);
    hit.userData.interact = { label: () => `Take artifact: ${name}`, run: () => { this.ctx.collect(key, name, lore); g.removeFromParent(); this.interactables = this.interactables.filter((x) => x !== hit); } };
    this.interactables.push(hit); this.group.add(g);
    this.updaters.push((dt, t) => { core.rotation.y += dt * 1.5; core.position.y = Math.sin(t * 2 + pos.x) * 0.08; ring.rotation.x += dt; });
    this.scan(name, 'tech', lore, pos, { artifact: true });
    return g;
  }
  landmark(name, desc, pos, discoverId) { this.scan(name, 'structure', desc, pos, { landmark: true, id: `${this.def.id}:${discoverId || name}` }); }
}

// A generic billboard-style sign (neon ads, signposts)
export function signMesh(text, w, h, color = '#ff4fd8', bg = 'rgba(0,0,0,0.6)') {
  const t = canvasTex(256, Math.round(256 * h / w), (g, W, H) => {
    g.fillStyle = bg; g.fillRect(0, 0, W, H); g.strokeStyle = color; g.lineWidth = 6; g.strokeRect(5, 5, W - 10, H - 10);
    g.fillStyle = color; g.font = `bold ${Math.round(H * 0.4)}px sans-serif`; g.textAlign = 'center'; g.textBaseline = 'middle'; g.fillText(text, W / 2, H / 2 + 3);
  });
  return new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: t, transparent: true, side: THREE.DoubleSide }));
}
