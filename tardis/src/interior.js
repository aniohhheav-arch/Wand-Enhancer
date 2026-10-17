// The TARDIS interior: console room (platform, steps, mezzanine, windows, rotor), 16 spokes with corridors and rooms
// built lazily as the player approaches, collision/ground queries, the interior doorway (portal anchor) and secrets.
import { THREE, canvasTex, clamp, damp, lerp, rng, hashStr, smooth, pad } from './util.js';
import { G, ROOM_DEFS, CLOAK_MODES, SYSTEMS, SYSTEM_LABEL, discover, unlockRoom, log } from './state.js';
import { A, rt, say } from './ship.js';
import { WORLDS } from './worlds.js';
import { buildConsole } from './console.js';
import { makeLeaves } from './tardis.js';
import { audio } from './audio.js';
import { bus } from './util.js';

export const INTERIOR_ORIGIN = new THREE.Vector3(4000, 0, 0);
export const DOOR_Z = -24.4;               // interior door plane (local)
const RW = 26, SPOKES = 16, ROOM_D = 110, ROOM_R = 15, CORR_END = 96;
export const spokeAngle = (k) => (11.25 + 22.5 * k) * Math.PI / 180;
const HALF = (n) => Math.PI / 180 * n;

const THEMES = {
  classic: { wall: 0x2c3b3d, floor: 0x1d2324, accent: 0xc9a54b, glow: 0xffb060, panel: 0x161b20, metal: 0x3a4448, light: 0xffd0a0, ctl: 0x20262c, rough: 0.55 },
  futuristic: { wall: 0xdfe8f2, floor: 0xb8c4d0, accent: 0x66ddff, glow: 0x66ddff, panel: 0x2a3440, metal: 0x9aa8b8, light: 0xd0f0ff, ctl: 0x2a3440, rough: 0.25 },
  organic: { wall: 0x3a2a1c, floor: 0x2a1c12, accent: 0x88aa44, glow: 0x88ff66, panel: 0x24180e, metal: 0x4a3828, light: 0xd8ffb0, ctl: 0x2e2012, rough: 0.9 },
  industrial: { wall: 0x2a2a2e, floor: 0x1a1a1c, accent: 0xff7a30, glow: 0xff7a30, panel: 0x141416, metal: 0x4a4a52, light: 0xffc090, ctl: 0x1c1c20, rough: 0.7 },
  neon: { wall: 0x0a0818, floor: 0x050410, accent: 0xff40d0, glow: 0xff40d0, panel: 0x0c0820, metal: 0x20183a, light: 0xff90f0, ctl: 0x120c28, rough: 0.3 },
  crystal: { wall: 0x3a5a8a, floor: 0x20304a, accent: 0xaaeeff, glow: 0xaaeeff, panel: 0x182840, metal: 0x4a6a9a, light: 0xc8f4ff, ctl: 0x1c2c48, rough: 0.15 },
};

function mkWallTex() {
  return canvasTex(512, 256, (g, w, h) => {
    g.fillStyle = '#9a9a9a'; g.fillRect(0, 0, w, h); g.strokeStyle = '#555'; g.lineWidth = 4; g.strokeRect(2, 2, w - 4, h - 4);
    g.fillStyle = '#5a5a5a'; g.fillRect(24, 24, w - 48, h - 48);
    for (const cy of [h * 0.3, h * 0.72]) { g.beginPath(); g.arc(w / 2, cy, 54, 0, 6.28); g.fillStyle = '#d8d8d8'; g.fill(); g.lineWidth = 8; g.strokeStyle = '#444'; g.stroke(); }
  }, { repeat: true });
}
function mkWallGlowTex() {
  return canvasTex(512, 256, (g, w, h) => {
    g.fillStyle = '#000'; g.fillRect(0, 0, w, h);
    for (const cy of [h * 0.3, h * 0.72]) { const gr = g.createRadialGradient(w / 2, cy, 8, w / 2, cy, 54); gr.addColorStop(0, '#fff'); gr.addColorStop(1, '#a0a0a0'); g.beginPath(); g.arc(w / 2, cy, 50, 0, 6.28); g.fillStyle = gr; g.fill(); }
  }, { repeat: true });
}
function mkFloorTex() {
  return canvasTex(512, 512, (g, w, h) => {
    g.fillStyle = '#777'; g.fillRect(0, 0, w, h); g.translate(w / 2, h / 2);
    for (let r = 250; r > 0; r -= 40) { g.beginPath(); g.arc(0, 0, r, 0, 6.28); g.strokeStyle = r % 80 ? '#555' : '#999'; g.lineWidth = 5; g.stroke(); }
    for (let i = 0; i < 32; i++) { g.rotate(Math.PI / 16); g.fillStyle = '#666'; g.fillRect(60, -2, 190, 4); }
  });
}

function scaleUV(geo, uArc, vH) { const uv = geo.attributes.uv; for (let i = 0; i < uv.count; i++) uv.setXY(i, uv.getX(i) * uArc / 11.7, uv.getY(i) * vH / 5.8); return geo; }

export class Interior {
  constructor() {
    this.group = new THREE.Group(); this.group.position.copy(INTERIOR_ORIGIN);
    this.interactables = []; this.updaters = []; this.rooms = new Array(SPOKES).fill(null); this.windowMats = []; this.t = 0;
    this.lightScale = 1; this.pool = []; this.anchors = []; this.unknownCount = 0; this.unknownGroup = null; this.musicOn = false;
    this.initMats(); this.buildRoom(); this.buildDoorway(); this.buildSpokes(); this.buildSecrets();
    this.console = buildConsole(this.mats); this.consoleGroup = this.console.group; this.consoleGroup.position.y = 1.0; this.group.add(this.consoleGroup);
    this.console.interactables.forEach((m) => this.reg(m));
    this.applyTheme(G.custom.theme); this.console.applyLayout(G.custom.layout);
  }

  reg(mesh) { mesh.userData.wp = null; this.interactables.push(mesh); }
  refreshWP() { this.group.updateMatrixWorld(true); const v = new THREE.Vector3(); for (const m of this.interactables) { if (!m.userData.wp) m.userData.wp = new THREE.Vector3(); m.getWorldPosition(v); m.userData.wp.copy(v); } }

  // ------------------------------------------------------------------ materials & themes
  initMats() {
    const wt = mkWallTex();
    const wg = mkWallGlowTex();
    const ft = mkFloorTex();
    this.mats = {
      wall: new THREE.MeshStandardMaterial({ map: wt, emissiveMap: wg, emissive: 0xffb060, emissiveIntensity: 1.0, side: THREE.BackSide, roughness: 0.6 }),
      floor: new THREE.MeshStandardMaterial({ map: ft, roughness: 0.55, metalness: 0.08 }),
      accent: new THREE.MeshStandardMaterial({ color: 0xc9a54b, roughness: 0.35, metalness: 0.9 }),
      glow: new THREE.MeshStandardMaterial({ color: 0x111111, emissive: 0xffb060, emissiveIntensity: 1 }),
      panel: new THREE.MeshStandardMaterial({ color: 0x161b20, roughness: 0.4, metalness: 0.6 }),
      metal: new THREE.MeshStandardMaterial({ color: 0x3a4448, roughness: 0.4, metalness: 0.8 }),
      ctl: new THREE.MeshStandardMaterial({ color: 0x20262c, roughness: 0.5, metalness: 0.7 }),
      chrome: new THREE.MeshStandardMaterial({ color: 0xdde3ea, roughness: 0.15, metalness: 1 }),
      door: new THREE.MeshStandardMaterial({ color: 0xe9e2cc, roughness: 0.7 }),
      ceiling: new THREE.MeshStandardMaterial({ color: 0x1a2022, roughness: 0.8, side: THREE.BackSide }),
    };
    for (const k in this.mats) this.mats[k].userData.keep = true;
    this.visColor = new THREE.Color(0xaaddff);
  }
  applyTheme(name) {
    const T = THEMES[name] || THEMES.classic, m = this.mats;
    m.wall.color.setHex(T.wall).multiplyScalar(2.4); m.wall.emissive.setHex(T.glow); m.floor.color.setHex(T.floor).multiplyScalar(3.4); m.accent.color.setHex(T.accent); m.glow.emissive.setHex(T.glow);
    m.panel.color.setHex(T.panel); m.metal.color.setHex(T.metal); m.ctl.color.setHex(T.ctl); m.wall.roughness = T.rough; m.floor.roughness = Math.min(1, T.rough * 1.1);
    m.ceiling.color.setHex(T.wall).multiplyScalar(0.7); this.themeLight = new THREE.Color(T.light); this.themeGlow = new THREE.Color(T.glow);
    this.ringLights && this.ringLights.forEach((l) => l.color.copy(this.themeLight));
  }

  // ------------------------------------------------------------------ console room
  buildRoom() {
    const g = this.group, M = this.mats;
    const floor = new THREE.Mesh(new THREE.CircleGeometry(RW + 0.4, 96).rotateX(-Math.PI / 2), M.floor); g.add(floor);
    for (const [r, h] of [[10.6, 0.33], [9.6, 0.67], [8.6, 1.0]]) { const p = new THREE.Mesh(new THREE.CylinderGeometry(r, r, h, 72).translate(0, h / 2, 0), M.floor); g.add(p); const e = new THREE.Mesh(new THREE.TorusGeometry(r, 0.04, 6, 90).rotateX(Math.PI / 2).translate(0, h + 0.02, 0), M.glow); g.add(e); }
    // wall: lower segments with a gap at every spoke, plus a continuous upper wall
    const seg = Math.PI * 2 / SPOKES, gap = 5.6 / RW;
    for (let k = 0; k < SPOKES; k++) {
      const a = spokeAngle(k); // world angle of the gap centre
      const start = a + gap / 2, len = seg - gap; // angles measured in the x-z plane (x=cos,z=sin)
      const geo = scaleUV(new THREE.CylinderGeometry(RW, RW, 4.6, 12, 1, true, Math.PI / 2 - start - len, len).translate(0, 2.3, 0), len * RW, 4.6);
      g.add(new THREE.Mesh(geo, M.wall));
    }
    g.add(new THREE.Mesh(scaleUV(new THREE.CylinderGeometry(RW, RW, 20, 96, 1, true).translate(0, 14.6, 0), Math.PI * 2 * RW, 20), M.wall));
    const dome = new THREE.Mesh(new THREE.SphereGeometry(RW, 64, 20, 0, Math.PI * 2, 0, Math.PI / 2).scale(1, 0.85, 1).translate(0, 24.6, 0), M.ceiling); g.add(dome);
    for (let i = 0; i < 16; i++) { const rib = new THREE.Mesh(new THREE.TorusGeometry(RW - 0.4, 0.35, 6, 32, Math.PI / 2).rotateZ(0).translate(0, 0, 0), M.accent); rib.scale.set(1, 0.85, 1); rib.rotation.y = i * Math.PI / 8; rib.position.y = 24.6; rib.rotation.x = 0; rib.rotation.z = 0; const piv = new THREE.Group(); piv.rotation.y = i * Math.PI / 8; const arc = new THREE.Mesh(new THREE.TorusGeometry(RW - 0.3, 0.3, 5, 28, Math.PI / 2), M.accent); arc.scale.set(1, 0.85, 1); arc.position.y = 24.6; arc.rotation.z = 0; piv.add(arc); g.add(piv); }
    // mezzanine ring + stairs (steps of 0.25 m, matches collision)
    const mez = [], stairs = [];
    for (let d = 70; d < 380; d += 2.5) mez.push(d);
    const mzI = new THREE.InstancedMesh(new THREE.BoxGeometry(5.6, 0.3, 1.1), M.metal, mez.length); const D = new THREE.Object3D();
    mez.forEach((d, i) => { const a = HALF(d + 1.25); D.position.set(Math.cos(a) * 21.9, 4.85, Math.sin(a) * 21.9); D.rotation.set(0, -a, 0); D.scale.set(1, 1, 1.02); D.updateMatrix(); mzI.setMatrixAt(i, D.matrix); }); g.add(mzI);
    const stI = new THREE.InstancedMesh(new THREE.BoxGeometry(5.2, 1, 1.0), M.metal, 20);
    for (let k = 1; k <= 20; k++) { const a = HALF(20 + (k - 0.5) * 2.5), top = k * 0.25; D.position.set(Math.cos(a) * 22, top / 2, Math.sin(a) * 22); D.rotation.set(0, -a, 0); D.scale.set(1, top, 1.0); D.updateMatrix(); stI.setMatrixAt(k - 1, D.matrix); }
    g.add(stI);
    for (let i = 0; i < 10; i++) { const a = HALF(i * 36 + 80), c = new THREE.Mesh(new THREE.CylinderGeometry(0.35, 0.45, 4.8, 10), M.accent); c.position.set(Math.cos(a) * 24, 2.4, Math.sin(a) * 24); g.add(c); }
    // railing on the mezzanine inner edge
    const railN = 100, rails = new THREE.InstancedMesh(new THREE.BoxGeometry(0.08, 0.08, 1.2), M.glow, railN), posts = new THREE.InstancedMesh(new THREE.CylinderGeometry(0.04, 0.04, 1.1, 5), M.chrome, railN);
    for (let i = 0; i < railN; i++) { const d = 72 + i * 3.05, a = HALF(d); D.position.set(Math.cos(a) * 19.1, 5.95, Math.sin(a) * 19.1); D.rotation.set(0, -a + Math.PI / 2, 0); D.scale.set(1, 1, 1.15); D.updateMatrix(); rails.setMatrixAt(i, D.matrix); D.position.y = 5.45; D.rotation.set(0, 0, 0); D.scale.set(1, 1, 1); D.updateMatrix(); posts.setMatrixAt(i, D.matrix); }
    g.add(rails, posts);
    // suspended lights + machinery overhead
    this.pendants = []; for (let i = 0; i < 12; i++) { const a = i * Math.PI / 6, p = new THREE.Mesh(new THREE.SphereGeometry(0.45, 12, 10), M.glow); p.position.set(Math.cos(a) * 14, 16 + (i % 2) * 2.4, Math.sin(a) * 14); g.add(p); const cord = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.03, 10, 4).translate(0, 5, 0), M.metal); cord.position.copy(p.position); g.add(cord); this.pendants.push(p); }
    this.rings = []; for (let i = 0; i < 3; i++) { const r = new THREE.Mesh(new THREE.TorusGeometry(7 + i * 4.5, 0.4, 8, 48).rotateX(Math.PI / 2), M.metal); r.position.y = 31 + i * 3.5; g.add(r); this.rings.push(r); const sp = new THREE.Mesh(new THREE.BoxGeometry(14 + i * 9, 0.18, 0.4), M.accent); sp.position.y = r.position.y; r.add(sp); }
    // glowing conduits with energy pulses
    const pulse = canvasTex(256, 16, (c, w, h) => { c.fillStyle = '#000'; c.fillRect(0, 0, w, h); const gr = c.createLinearGradient(0, 0, w, 0); gr.addColorStop(0, 'rgba(255,255,255,0)'); gr.addColorStop(0.5, 'rgba(255,255,255,1)'); gr.addColorStop(1, 'rgba(255,255,255,0)'); c.fillStyle = gr; c.fillRect(0, 0, w, h); }, { repeat: true });
    pulse.repeat.set(4, 1); this.pulseTex = pulse; this.conduitMat = new THREE.MeshBasicMaterial({ map: pulse, color: 0xffb060, transparent: true, blending: THREE.AdditiveBlending, depthWrite: false });
    for (let i = 0; i < 8; i++) { const a = i * Math.PI / 4 + 0.2; const pts = [new THREE.Vector3(Math.cos(a) * 3, 3.2, Math.sin(a) * 3), new THREE.Vector3(Math.cos(a) * 9, 5 + (i % 3), Math.sin(a) * 9), new THREE.Vector3(Math.cos(a + 0.2) * 17, 9 + (i % 4) * 2, Math.sin(a + 0.2) * 17), new THREE.Vector3(Math.cos(a + 0.3) * 25, 14 + (i % 3) * 2, Math.sin(a + 0.3) * 25)]; const tube = new THREE.Mesh(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 48, 0.09, 6, false), this.conduitMat); g.add(tube); g.add(new THREE.Mesh(new THREE.TubeGeometry(new THREE.CatmullRomCurve3(pts), 24, 0.16, 5, false), M.metal)); }
    // windows onto the outside (cube lookup: continuous 3D environment, not a flat image)
    this.windowMats = [];
    const winMat = () => { const m = new THREE.ShaderMaterial({ uniforms: { uCube: { value: null }, uRot: { value: new THREE.Matrix3() }, uT: { value: 0 }, uSpecial: { value: 0 }, uBright: { value: 1 } }, fog: false,
      vertexShader: 'varying vec3 vW; void main(){ vec4 w=modelMatrix*vec4(position,1.); vW=w.xyz; gl_Position=projectionMatrix*viewMatrix*w; }',
      fragmentShader: `uniform samplerCube uCube; uniform mat3 uRot; uniform float uT,uSpecial,uBright; varying vec3 vW;
        float hh(vec3 p){ p=fract(p*0.3183099+.1); p*=17.; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float vn(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.-2.*f); return mix(mix(mix(hh(i),hh(i+vec3(1,0,0)),f.x),mix(hh(i+vec3(0,1,0)),hh(i+vec3(1,1,0)),f.x),f.y),mix(mix(hh(i+vec3(0,0,1)),hh(i+vec3(1,0,1)),f.x),mix(hh(i+vec3(0,1,1)),hh(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){ vec3 d=normalize(vW-cameraPosition); vec3 c;
          if(uSpecial>0.5){ float a=atan(d.x,d.z); float s=vn(vec3(a*3.+uT*0.04,d.y*5.,uT*0.03)); float sw=pow(s,3.)*vec3(1.,.5,.3).r; c=vec3(.9,.35,.2)*sw*2.+vec3(.2,.1,.4)*vn(d*8.+uT*0.02)+step(0.992,hh(floor(d*160.)))*0.9; c*=0.7+0.3*sin(uT*0.4); }
          else c=textureCube(uCube,uRot*d).rgb*uBright;
          gl_FragColor=vec4(c,1.);
        #include <tonemapping_fragment>
          #include <colorspace_fragment> }` }); m.userData.keep = true; this.windowMats.push(m); return m; };
    const makeWindow = (ang, y, w, h, mat, parent = g, rr = RW - 0.12) => {
      const win = new THREE.Mesh(new THREE.PlaneGeometry(w, h), mat); win.position.set(Math.cos(ang) * rr, y, Math.sin(ang) * rr); win.rotation.y = Math.atan2(-Math.cos(ang), -Math.sin(ang)); parent.add(win);
      const fr = new THREE.Mesh(new THREE.BoxGeometry(w + 0.8, h + 0.8, 0.5), this.mats.accent); fr.position.copy(win.position); fr.rotation.copy(win.rotation); fr.translateZ(-0.3); parent.add(fr); return win;
    };
    this.makeWindow = makeWindow; this.winMat = winMat;
    [45, 135, 225, 315].forEach((deg) => makeWindow(HALF(deg), 11, 11, 7, winMat()));
    // light shafts from the windows
    this.shafts = []; [45, 135, 225, 315].forEach((deg) => { const a = HALF(deg); const s = new THREE.Mesh(new THREE.CylinderGeometry(4.5, 1.2, 20, 12, 1, true), new THREE.MeshBasicMaterial({ color: 0xaaddff, transparent: true, opacity: 0.05, blending: THREE.AdditiveBlending, depthWrite: false, side: THREE.DoubleSide })); s.position.set(Math.cos(a) * 17, 8, Math.sin(a) * 17); s.rotation.set(Math.sin(a) * 0.5, 0, -Math.cos(a) * 0.5); g.add(s); this.shafts.push(s); });
    // dust motes
    const N = 260, pg = new THREE.BufferGeometry(), pp = new Float32Array(N * 3), r = rng(11);
    for (let i = 0; i < N; i++) { const a = r() * 6.28, d = Math.sqrt(r()) * 24; pp[i * 3] = Math.cos(a) * d; pp[i * 3 + 1] = r() * 20; pp[i * 3 + 2] = Math.sin(a) * d; }
    pg.setAttribute('position', new THREE.BufferAttribute(pp, 3)); this.dust = new THREE.Points(pg, new THREE.PointsMaterial({ color: 0xffeacc, size: 0.07, transparent: true, opacity: 0.5, depthWrite: false, blending: THREE.AdditiveBlending })); g.add(this.dust);
    // lights
    this.ringLights = []; for (let i = 0; i < 2; i++) { const l = new THREE.PointLight(0xffd0a0, 140, 0, 1.2); l.position.set(i ? -12 : 12, 15, i ? -6 : 6); g.add(l); this.ringLights.push(l); }
    for (let i = 0; i < 3; i++) { const l = new THREE.PointLight(0xffffff, 0, 60, 1.4); g.add(l); this.pool.push(l); }
    this.amb = new THREE.AmbientLight(0xffffff, 0);
  }

  // ------------------------------------------------------------------ the doorway (exit)
  buildDoorway() {
    const g = new THREE.Group(); g.position.set(0, 0, DOOR_Z); this.doorway = g; this.group.add(g);
    const M = this.mats, box = (w, h, d, m, x, y, z) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), m); b.position.set(x, y, z); g.add(b); return b; };
    const frameM = new THREE.MeshStandardMaterial({ color: 0x0b2f7a, roughness: 0.5, metalness: 0.1 }); frameM.userData.keep = true; this.frameMat = frameM;
    box(0.18, 2.4, 0.5, frameM, -0.55, 1.2, -0.7); box(0.18, 2.4, 0.5, frameM, 0.55, 1.2, -0.7); box(1.28, 0.3, 0.5, frameM, 0, 2.28, -0.7);
    box(2.6, 4.6, 0.2, M.metal, 0, 2.3, -1.45); box(0.4, 4.4, 1.4, M.metal, -1.45, 2.2, -0.7); box(0.4, 4.4, 1.4, M.metal, 1.45, 2.2, -0.7); box(2.6, 1.9, 1.4, M.metal, 0, 3.75, -0.7);
    const sign = new THREE.Mesh(new THREE.PlaneGeometry(1.2, 0.2), new THREE.MeshBasicMaterial({ map: canvasTex(256, 44, (c, w, h) => { c.fillStyle = '#0b0b10'; c.fillRect(0, 0, w, h); c.fillStyle = '#eee'; c.font = 'bold 20px serif'; c.textAlign = 'center'; c.textBaseline = 'middle'; c.fillText('EXIT · POLICE BOX · EXIT', w / 2, h / 2); }) })); sign.position.set(0, 2.9, -0.44); g.add(sign);
    const lv = makeLeaves(M.door, new THREE.MeshStandardMaterial({ color: 0xffe9c0, emissive: 0xffd890, emissiveIntensity: 0.8 }), M.accent);
    this.leafL = lv.L; this.leafR = lv.R; lv.L.piv.position.z = -0.45; lv.R.piv.position.z = -0.45; g.add(lv.L.piv, lv.R.piv);
    this.doorAnchor = new THREE.Object3D(); this.doorAnchor.position.z = -0.2; g.add(this.doorAnchor);
    // containment field
    this.field = new THREE.Mesh(new THREE.PlaneGeometry(0.88, 2.0), new THREE.ShaderMaterial({ transparent: true, depthWrite: false, side: THREE.DoubleSide, blending: THREE.AdditiveBlending, uniforms: { uT: { value: 0 }, uA: { value: 0 } },
      vertexShader: 'varying vec2 vU; void main(){ vU=uv; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.); }',
      fragmentShader: 'uniform float uT,uA; varying vec2 vU; void main(){ float h=0.5+0.5*sin(vU.y*40.-uT*3.+sin(vU.x*9.+uT)*2.); float e=pow(abs(vU.x-0.5)*2.,3.); gl_FragColor=vec4(vec3(0.2,0.6,1.)*(0.25+h*0.4+e),uA*(0.18+h*0.2+e*0.5)); }' }));
    this.field.position.set(0, 1.175, -0.1); this.field.visible = false; g.add(this.field);
    const hit = (x, y, w, h, label, run) => { const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, 0.5), new THREE.MeshBasicMaterial({ visible: false })); m.position.set(x, y, -0.3); m.userData.interact = { label, run }; g.add(m); this.reg(m); return m; };
    hit(-0.23, 1.2, 0.42, 2.0, () => A.doorLabel(), (o) => A.interiorDoor('l', o)); hit(0.23, 1.2, 0.42, 2.0, () => A.doorLabel(), (o) => A.interiorDoor('r', o));
    const lever = (x, col, label, run) => { const p = new THREE.Mesh(new THREE.CylinderGeometry(0.03, 0.03, 0.4, 8).translate(0, 0.2, 0), M.chrome); const b = new THREE.Mesh(new THREE.SphereGeometry(0.07, 10, 8), new THREE.MeshStandardMaterial({ color: col })); b.position.y = 0.42; const grp = new THREE.Group(); grp.add(p, b); grp.position.set(x, 1.0, -0.4); grp.rotation.z = 0.3; g.add(grp); const h = new THREE.Mesh(new THREE.SphereGeometry(0.35, 6, 6), new THREE.MeshBasicMaterial({ visible: false })); h.position.set(x, 1.3, -0.35); h.userData.interact = { label, run }; g.add(h); this.reg(h); return grp; };
    this.lockLever = lever(-1.15, 0x2288ff, () => (G.ship.doors.locked ? 'Unlock the doors' : 'Lock the doors'), () => A.toggleLock());
    lever(1.15, 0xcc2020, 'EMERGENCY DOOR OVERRIDE', () => A.emergencyRelease());
  }

  // ------------------------------------------------------------------ spokes: door gaps, corridors, rooms
  buildSpokes() {
    this.spokes = [];
    const M = this.mats;
    const strip = new THREE.InstancedMesh(new THREE.BoxGeometry(2.4, 0.08, 0.5), M.glow, SPOKES * 9);
    const D = new THREE.Object3D(); let si = 0;
    for (let k = 0; k < SPOKES; k++) {
      const def = ROOM_DEFS[k], a = spokeAngle(k), col = new THREE.Color(def.color);
      const sp = { k, a, def, col, doorOpen: 0, built: false, roomGroup: null };
      // door at the wall
      const dg = new THREE.Group(); dg.position.set(Math.cos(a) * (RW - 0.15), 0, Math.sin(a) * (RW - 0.15)); dg.rotation.y = Math.atan2(-Math.cos(a), -Math.sin(a)); this.group.add(dg);
      const post = (x) => { const p = new THREE.Mesh(new THREE.BoxGeometry(0.5, 4.8, 0.7), M.metal); p.position.set(x, 2.4, 0); dg.add(p); };
      post(-2.85); post(2.85); const hdr = new THREE.Mesh(new THREE.BoxGeometry(6.2, 0.6, 0.7), M.accent); hdr.position.set(0, 4.5, 0); dg.add(hdr);
      const leaf = new THREE.Mesh(new THREE.BoxGeometry(5.2, 4.3, 0.25), M.metal); leaf.position.set(0, 2.15, 0); dg.add(leaf);
      const lamp = new THREE.Mesh(new THREE.BoxGeometry(1.2, 0.18, 0.2), new THREE.MeshStandardMaterial({ color: 0x111, emissive: col, emissiveIntensity: 2 })); lamp.position.set(0, 4.6, 0.38); dg.add(lamp);
      const plug = new THREE.Mesh(new THREE.BoxGeometry(5.6, 4.6, 0.6), new THREE.MeshStandardMaterial({ color: 0x4a5656, roughness: 0.7, side: THREE.DoubleSide })); plug.position.set(0, 2.3, 0); dg.add(plug);
      Object.assign(sp, { dg, leaf, lamp, plug });
      // corridor (static, cheap)
      const cg = new THREE.Group(); cg.position.copy(this.group.position).sub(this.group.position); cg.rotation.y = -a; this.group.add(cg); sp.cg = cg;
      const len = k === 11 ? 330 - RW : CORR_END - RW + 1, wd = k === 11 ? 6.4 : 5.2, cx = RW + len / 2 - 0.5;
      const mk = (w, h, d, mat, x, y, z) => { const b = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat); b.position.set(x, y, z); cg.add(b); return b; };
      mk(len, 0.2, wd, M.floor, cx, -0.1, 0); mk(len, 0.25, wd + 0.8, M.metal, cx, 4.62, 0);
      mk(len, 4.6, 0.3, M.wall, cx, 2.3, wd / 2 + 0.15).material = M.metal; mk(len, 4.6, 0.3, M.wall, cx, 2.3, -wd / 2 - 0.15).material = M.metal;
      for (let i = 0; i < 9 && k !== 11; i++) { const x = RW + 4 + i * 8; D.position.set(x, 4.45, 0); D.rotation.set(0, 0, 0); D.scale.set(1, 1, 1); D.updateMatrix(); strip.setMatrixAt(si, D.matrix); strip.setColorAt(si, col); si++; }
      cg.visible = false; this.spokes.push(sp);
      // light anchors for the pooled lights
      const dirv = new THREE.Vector3(Math.cos(a), 0, Math.sin(a));
      sp.anchors = [dirv.clone().multiplyScalar(52).setY(3), dirv.clone().multiplyScalar(80).setY(3), dirv.clone().multiplyScalar(ROOM_D).setY(4)];
      this.anchors.push(...sp.anchors.map((p) => ({ p, k, col })));
    }
    strip.count = si; strip.instanceMatrix.needsUpdate = true; if (strip.instanceColor) strip.instanceColor.needsUpdate = true; this.group.add(strip);
    // special extra anchors (sealed hall, infinite corridor)
    const a14 = spokeAngle(14), a11 = spokeAngle(11);
    [150, 200, 245, 290].forEach((d) => this.anchors.push({ p: new THREE.Vector3(Math.cos(a14) * d, 8, Math.sin(a14) * d), k: 14, col: new THREE.Color(0xfff0d0) }));
    for (let d = 140; d <= 320; d += 30) this.anchors.push({ p: new THREE.Vector3(Math.cos(a11) * d, 3, Math.sin(a11) * d), k: 11, col: new THREE.Color(ROOM_DEFS[11].color) });
    this.anchors.push({ p: new THREE.Vector3(500, 4, 500), k: -1, col: new THREE.Color(0xffffff) });
  }

  buildSecrets() {
    // hidden control dais beneath the console: opens once the override sequence has been found
    const g = new THREE.Group(); g.position.set(0, 0, 5.8); this.group.add(g); this.hatch = g;
    const lid = new THREE.Mesh(new THREE.CylinderGeometry(1.5, 1.5, 0.12, 20).translate(0, 0.06, 0), this.mats.metal); g.add(lid); this.hatchLid = lid;
    const dais = new THREE.Group(); dais.position.y = -0.5; g.add(dais); this.dais = dais;
    dais.add(new THREE.Mesh(new THREE.CylinderGeometry(1.4, 1.4, 0.2, 20), new THREE.MeshStandardMaterial({ color: 0x0a0a14, emissive: 0x6a4aff, emissiveIntensity: 0.5 })));
    const mkGlyph = (x, color, label, run) => { const m = new THREE.Mesh(new THREE.OctahedronGeometry(0.2), new THREE.MeshStandardMaterial({ color: 0x000000, emissive: color, emissiveIntensity: 2.5 })); m.position.set(x, 0.5, 0); dais.add(m); const h = new THREE.Mesh(new THREE.SphereGeometry(0.4, 6, 6), new THREE.MeshBasicMaterial({ visible: false })); h.position.set(x, 0.5, 0); h.userData.interact = { label, run }; dais.add(h); this.reg(h); this.updaters.push((dt, t) => { m.rotation.y += dt * 2; m.position.y = 0.55 + Math.sin(t * 2 + x) * 0.06; }); };
    mkGlyph(-0.7, 0xff66aa, () => `Ancient path — hidden flight mode ${G.ship.hiddenFlight ? 'ON' : 'off'}`, () => A.hiddenFlight());
    mkGlyph(0, 0xaa88ff, 'Ancient memory — read', () => { say('Before the console, there was only a question. The question was: where?'); discover('secrets', 'memory'); G.disc.unlocks.cloak5 = true; log('The ancient controls unlock a Dimensional Phase Cloak.', 'sys'); });
    mkGlyph(0.7, 0x66ffcc, 'Ancient repair — restore all systems', () => { A.repair('all'); say('Mended. Do not ask what with.'); });
    g.visible = false;
  }

  // ------------------------------------------------------------------ room construction (lazy)
  buildRoomContents(k) {
    const sp = this.spokes[k]; if (sp.built) return; sp.built = true;
    const def = sp.def, col = new THREE.Color(def.color);
    const rg = new THREE.Group(); rg.rotation.y = -sp.a; this.group.add(rg); sp.roomGroup = rg;
    const M = this.mats; const c = new THREE.Group(); c.position.set(ROOM_D, 0, 0); rg.add(c);
    const rm = (color, rough = 0.7, metal = 0) => new THREE.MeshStandardMaterial({ color, roughness: rough, metalness: metal });
    const em = (color, k2 = 2) => new THREE.MeshStandardMaterial({ color: 0x111111, emissive: color, emissiveIntensity: k2 });
    const prop = (geo, mat, x, y, z, parent = c) => { const m = new THREE.Mesh(geo, mat); m.position.set(x, y, z); parent.add(m); return m; };
    const hit = (x, y, z, label, run, o = {}) => { const m = new THREE.Mesh(new THREE.SphereGeometry(o.r || 0.8, 6, 6), new THREE.MeshBasicMaterial({ visible: false })); m.position.set(x, y, z); m.userData.interact = { label, run, hold: o.hold }; c.add(m); this.reg(m); return m; };
    const scr = (draw, w, h, x, y, z, ry = 0) => { const s = this.mkScreen(draw, w, h); s.position.set(x, y, z); s.rotation.y = ry; c.add(s); this.updaters.push((dt) => s.userData.refresh(dt)); return s; };
    const big = k === 14 ? 12 : ROOM_R, wallH = k === 14 ? 14 : 7.5;
    if (k === 11) { this.buildInfiniteCorridor(c, rg); } else {
      const gap = 0.45;
      const fl = new THREE.Mesh(new THREE.CircleGeometry(big, 48).rotateX(-Math.PI / 2), rm(new THREE.Color(def.color).multiplyScalar(0.18), 0.4, 0.4)); c.add(fl);
      if (k !== 14) {
        const wl = new THREE.Mesh(new THREE.CylinderGeometry(big, big, wallH, 48, 1, true, -Math.PI / 2 + gap / 2, Math.PI * 2 - gap).translate(0, wallH / 2, 0), new THREE.MeshStandardMaterial({ color: new THREE.Color(def.color).multiplyScalar(0.25), roughness: 0.6, side: THREE.BackSide, emissive: col, emissiveIntensity: 0.12 })); c.add(wl);
        const ce = new THREE.Mesh(new THREE.CircleGeometry(big, 48).rotateX(Math.PI / 2).translate(0, wallH, 0), rm(0x15181c)); c.add(ce);
      }
      const lamp = new THREE.Mesh(new THREE.TorusGeometry(big * 0.5, 0.15, 6, 36).rotateX(Math.PI / 2).translate(0, wallH - 0.2, 0), em(col, 2.2)); c.add(lamp);
      const trim = new THREE.Mesh(new THREE.TorusGeometry(big - 0.05, 0.08, 6, 48).rotateX(Math.PI / 2).translate(0, 0.08, 0), em(col, 1.5)); c.add(trim);
    }
    const spec = ROOM_BUILDERS[def.id]; spec && spec({ k, def, col, c, rg, prop, hit, scr, rm, em, I: this, M });
    this.group.updateMatrixWorld(true); this.refreshWP();
    bus.emit('roomBuilt', def.id);
  }
  mkScreen(draw, w, h) { // lightweight screen helper reusing console canvas screens
    const px = 512, py = Math.round(512 * h / w); const tex = canvasTex(px, py, () => {}); const mesh = new THREE.Mesh(new THREE.PlaneGeometry(w, h), new THREE.MeshBasicMaterial({ map: tex, toneMapped: false })); let acc = 1;
    mesh.userData.refresh = (dt) => { acc += dt; if (acc < 0.25) return; acc = 0; draw(tex.userData.ctx, px, py); tex.needsUpdate = true; }; return mesh;
  }
  buildInfiniteCorridor(c, rg) {
    // a long hall of doors; the player is wrapped forward so it never ends. Doors open onto freshly generated rooms.
    const M = this.mats, I = this; c.position.set(0, 0, 0);
    const dm = new THREE.InstancedMesh(new THREE.BoxGeometry(0.2, 3.3, 1.8), new THREE.MeshStandardMaterial({ color: 0x6a5a48, roughness: 0.6 }), 40); const D = new THREE.Object3D(); let n = 0;
    for (let d = 100; d < 300; d += 10) for (const side of [-1, 1]) {
      D.position.set(d + 5, 1.65, side * 3.1); D.rotation.set(0, Math.PI / 2, 0); D.updateMatrix(); dm.setMatrixAt(n++, D.matrix);
      const idx = ((d - 100) / 10) * 2 + (side > 0 ? 1 : 0);
      const h = new THREE.Mesh(new THREE.BoxGeometry(1.8, 3, 0.8), new THREE.MeshBasicMaterial({ visible: false })); h.position.set(d + 5, 1.6, side * 2.8); h.userData.interact = { label: () => 'Open the door (where does it lead?)', run: () => I.enterUnknownRoom(idx % 24, d + 5, side) }; c.add(h); this.reg(h);
      const lam = new THREE.Mesh(new THREE.BoxGeometry(0.3, 0.06, 0.3), new THREE.MeshStandardMaterial({ color: 0x111, emissive: 0xcfcfff, emissiveIntensity: 1.5 })); lam.position.set(d + 5, 3.5, side * 2.9); c.add(lam);
    }
    c.add(dm);
    this.hallLights = null;
  }
  enterUnknownRoom(idx, along, side) {
    this.unknownCount++; const seed = hashStr(`unk:${idx}:${this.unknownCount}`); const r = rng(seed);
    if (this.unknownGroup) { this.group.remove(this.unknownGroup); this.unknownGroup = null; }
    const g = new THREE.Group(); g.position.set(500, 0, 500); this.group.add(g); this.unknownGroup = g;
    const hue = r(); const colr = new THREE.Color().setHSL(hue, 0.6, 0.55);
    g.add(new THREE.Mesh(new THREE.CircleGeometry(8, 36).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ color: new THREE.Color().setHSL(hue, 0.4, 0.15), roughness: 0.4, metalness: 0.4 })));
    g.add(new THREE.Mesh(new THREE.CylinderGeometry(8, 8, 5, 36, 1, true).translate(0, 2.5, 0), new THREE.MeshStandardMaterial({ color: new THREE.Color().setHSL(hue, 0.4, 0.3), side: THREE.BackSide, emissive: colr, emissiveIntensity: 0.15 })));
    g.add(new THREE.Mesh(new THREE.CircleGeometry(8, 36).rotateX(Math.PI / 2).translate(0, 5, 0), new THREE.MeshStandardMaterial({ color: 0x15181c })));
    const kinds = ['orb', 'pillar', 'book', 'tree', 'statue']; const kind = r.pick(kinds);
    for (let i = 0; i < r.int(3, 7); i++) { const a = r() * 6.28, d = 1.5 + r() * 5.5; let m; if (kind === 'orb') m = new THREE.Mesh(new THREE.SphereGeometry(0.5 + r() * 0.5, 14, 10), new THREE.MeshStandardMaterial({ color: 0, emissive: colr, emissiveIntensity: 2 })); else if (kind === 'pillar') m = new THREE.Mesh(new THREE.CylinderGeometry(0.4, 0.5, 2 + r() * 2.5, 8), new THREE.MeshStandardMaterial({ color: colr })); else if (kind === 'book') m = new THREE.Mesh(new THREE.BoxGeometry(0.8, 0.12, 0.6), new THREE.MeshStandardMaterial({ color: colr, emissive: colr, emissiveIntensity: 0.6 })); else if (kind === 'tree') m = new THREE.Mesh(new THREE.ConeGeometry(0.8, 2.6, 6), new THREE.MeshStandardMaterial({ color: colr, flatShading: true })); else m = new THREE.Mesh(new THREE.DodecahedronGeometry(0.8), new THREE.MeshStandardMaterial({ color: colr, flatShading: true })); m.position.set(Math.cos(a) * d, kind === 'orb' ? 1.4 + r() * 1.4 : 0.6 + (m.geometry.parameters?.height || 0) / 2, Math.sin(a) * d); g.add(m); }
    // what the room offers changes each time
    const gift = r();
    const msgs = ['A room that was a library a moment ago. It has already forgotten.', 'The walls hum in a key you almost recognise.', 'Someone left a cup of tea here. It is still warm.', 'A chalk diagram on the floor shows the console room from above. One room is missing.', 'It is empty. It is the emptiest room you have ever stood in.'];
    const gem = new THREE.Mesh(new THREE.OctahedronGeometry(0.25), new THREE.MeshStandardMaterial({ color: 0, emissive: colr, emissiveIntensity: 3 })); gem.position.set(0, 1.1, 0); g.add(gem);
    const mkHit = (x, y, z, label, run) => { const h = new THREE.Mesh(new THREE.SphereGeometry(0.8, 6, 6), new THREE.MeshBasicMaterial({ visible: false })); h.position.set(x, y, z); h.userData.interact = { label, run }; g.add(h); this.reg(h); return h; };
    mkHit(0, 1.1, 0, 'Examine', () => { say(msgs[Math.floor(gift * msgs.length) % msgs.length]); if (gift > 0.88) { const id = `unk:${seed}`; if (discover('artifacts', id)) log('You pocketed something small and warm: an artifact.', 'sys'); } discover('anomalies', 'changing_room', 'A room that changes every time it is entered'); });
    mkHit(0, 1.2, 7.0, 'Back to the corridor', () => {
      const sp = this.spokes[11], a = sp.a, dir = new THREE.Vector3(Math.cos(a), 0, Math.sin(a));
      bus.emit('teleportInterior', { pos: dir.multiplyScalar(along), yaw: Math.atan2(-Math.cos(a), -Math.sin(a)) });
      this.group.remove(g); this.unknownGroup = null;
    });
    this.group.updateMatrixWorld(true); this.refreshWP();
    bus.emit('teleportInterior', { pos: new THREE.Vector3(500, 0, 505.5), yaw: 0 });
    say('The door opens onto somewhere else.');
  }

  // ------------------------------------------------------------------ queries used by the player controller
  rotorAnchorWorld() { return new THREE.Vector3(0, 5, 0).add(INTERIOR_ORIGIN); }
  roomOK(k) { return !!G.disc.rooms[ROOM_DEFS[k].id]; }
  regionOK(x, z) {
    const r = Math.hypot(x, z);
    if (r < RW - 0.45) return true;
    if (Math.hypot(x - 500, z - 500) < 7.4) return true;
    for (let k = 0; k < SPOKES; k++) {
      if (!this.roomOK(k)) continue;
      const a = spokeAngle(k), ca = Math.cos(a), sa = Math.sin(a), along = x * ca + z * sa, across = -x * sa + z * ca;
      if (along > RW - 1.5 && along < (k === 11 ? 332 : CORR_END + 1.5) && Math.abs(across) < (k === 11 ? 2.9 : 2.3)) return true;
      if (k === 14) { if (Math.hypot(along - ROOM_D, across) < 11.6) return true; if (along > 118 && along < 126 && Math.abs(across) < 2.5) return true; if (Math.hypot(along - 245, across) < 119) return true; }
      else if (k !== 11 && Math.hypot(along - ROOM_D, across) < ROOM_R - 0.45) return true;
    }
    return false;
  }
  groundY(x, z, y) {
    const r = Math.hypot(x, z); let best = 0;
    const pool = this.poolInfo; if (pool && Math.hypot(x - pool.x, z - pool.z) < pool.r) best = -0.9;
    if (r < 10.6) { const c = r < 8.6 ? 1.0 : r < 9.6 ? 0.67 : 0.33; if (c <= y + 0.62) best = Math.max(best, c); }
    if (r >= 19 && r <= 24.6) {
      let th = Math.atan2(z, x) * 180 / Math.PI; if (th < 0) th += 360;
      if (th >= 20 && th < 70) { if (r >= 19.5) { const k = Math.floor((th - 20) / 2.5) + 1; const c = k * 0.25; if (c <= y + 0.62) best = Math.max(best, c); } }
      else if (5 <= y + 0.62) best = Math.max(best, 5);
    }
    return best;
  }
  // push the player out of solid things; returns adjusted position
  collide(p) {
    const r = Math.hypot(p.x, p.z);
    if (r < 2.85 && p.y < 3) { const s = 2.85 / Math.max(r, 0.001); p.x *= s; p.z *= s; }
    if (p.y > 2.2 && r > 12) { const s = clamp(r, 19.7, 24.2) / Math.max(r, 0.001); p.x *= s; p.z *= s; }
    // exit door: solid unless open and within the doorway slot
    const contained = G.ship.phase !== 'landed' && (G.settings.doorSafe || G.ship.phase !== 'vortex');
    const open = Math.max(G.ship.doors.left, G.ship.doors.right) > 0.6 && !contained;
    if (p.z < DOOR_Z + 0.35 && p.z > DOOR_Z - 1.5 && Math.abs(p.x) < 1.8 && !(open && Math.abs(p.x) < 0.38)) p.z = Math.max(p.z, DOOR_Z + 0.35);
    // infinite corridor wrap (period = 120 m = 12 doors)
    const sp = this.spokes[11]; if (this.roomOK(11)) { const ca = Math.cos(sp.a), sa = Math.sin(sp.a), along = p.x * ca + p.z * sa; if (along > 300) { p.x -= ca * 120; p.z -= sa * 120; p.wrapped = true; } }
    return p;
  }
  roomAt(x, z) { // index of the room the player is in, for ambience, or -1 (console)
    const r = Math.hypot(x, z); if (r < RW + 1) return -1;
    let best = -2, bd = 1e9;
    for (let k = 0; k < SPOKES; k++) { const a = spokeAngle(k), ca = Math.cos(a), sa = Math.sin(a), along = x * ca + z * sa, across = -x * sa + z * ca; if (Math.abs(across) < 20 && along > 40) { const d = Math.abs(along - ROOM_D) + Math.abs(across); if (d < bd) { bd = d; best = k; } } }
    if (Math.hypot(x - 500, z - 500) < 9) return 16;
    return best >= 0 && bd < 60 ? best : -3;
  }

  // ------------------------------------------------------------------ per-frame
  setCube(tex, mat3) { for (const m of this.windowMats) { m.uniforms.uCube.value = tex; m.uniforms.uRot.value.copy(mat3); } }
  applyLights(on, bright) {
    const s = on ? bright : 0, pw = this.themeLight || new THREE.Color(0xffffff);
    const lowP = G.ship.power < 4 ? 0.25 : 1, fl = rt.flicker && Math.random() < 0.3 ? 0.2 : 1;
    this.ringLights.forEach((l) => { l.intensity = 140 * s * lowP * fl; });
    this.console.coreLight.intensity = (this.console.coreLight.userData.base || 0) * s; this.amb.intensity = 0.8 * s;
    this.pool.forEach((l) => { l.intensity = (l.userData.i || 0) * s * lowP; });
    this.mats.wall.emissiveIntensity = 1.1 * s * lowP; this.mats.glow.userData.s = s;
    void pw;
  }
  update(dt, t, pw, vortexCol, flightLevel) {
    this.t = t; const G_ = G.ship;
    // door leaves mirror the exterior doors
    const ang = Math.PI * 0.62;
    this.leafL.piv.rotation.y = (this._dl = damp(this._dl || 0, G_.doors.left, 4.5, dt)) * ang; this.leafR.piv.rotation.y = -(this._dr = damp(this._dr || 0, G_.doors.right, 4.5, dt)) * ang;
    this.lockLever.rotation.z = damp(this.lockLever.rotation.z, G_.doors.locked ? 0.3 : -0.3, 8, dt);
    const fieldOn = G_.phase === 'vortex' && G.settings.doorSafe && (G_.doors.left > 0.3 || G_.doors.right > 0.3);
    this.field.visible = fieldOn; this.field.material.uniforms.uT.value = t; this.field.material.uniforms.uA.value = fieldOn ? 1 : 0;
    // console & rotor
    this.visColor.copy(vortexCol).lerp(this.themeLight, G_.phase === 'vortex' ? 0.35 : 0.8);
    this.console.update(dt, t, { color: this.visColor });
    this.pulseTex.offset.x -= dt * (0.15 + flightLevel * 0.9); this.conduitMat.color.copy(this.themeGlow || this.visColor).lerp(vortexCol, G_.phase === 'vortex' ? 0.55 : 0);
    this.rings.forEach((r, i) => { r.rotation.y += dt * (0.05 + i * 0.03) * (1 + flightLevel * 2) * (i % 2 ? -1 : 1); });
    this.pendants.forEach((p, i) => { p.position.y += Math.sin(t * 0.6 + i) * dt * 0.1; });
    this.ringLights.forEach((l, i) => l.color.copy(this.visColor).lerp(this.themeLight || this.visColor, G_.phase === 'vortex' ? 0.1 : 0.9));
    const dp = this.dust.geometry.attributes.position; for (let i = 0; i < dp.count; i += 2) { dp.setY(i, (dp.getY(i) + dt * 0.12) % 20); } dp.needsUpdate = true;
    this.shafts.forEach((s, i) => { s.material.color.copy(vortexCol).lerp(new THREE.Color(0xaaddff), G_.phase === 'vortex' ? 0.1 : 0.9); s.material.opacity = 0.03 + 0.03 * Math.sin(t * 0.5 + i) + (G_.phase === 'vortex' ? 0.04 : 0); });
    this.windowMats.forEach((m) => { m.uniforms.uT.value = t; });
    // ancient hatch
    const open = !!G.disc.secrets.ancient; this.hatch.visible = open; this.hatchLid.position.x = damp(this.hatchLid.position.x, open ? 3 : 0, 3, dt); this.dais.position.y = damp(this.dais.position.y, open ? 0.02 : -0.5, 2, dt);
    // spoke doors, unlock reveal & room construction
    for (const sp of this.spokes) {
      const ok = this.roomOK(sp.k), dgw = sp.dg.position.clone().add(INTERIOR_ORIGIN), d = pw.distanceTo(dgw);
      sp.plug.visible = !ok; sp.leaf.visible = ok; sp.lamp.visible = ok; sp.cg.visible = ok;
      sp.doorOpen = damp(sp.doorOpen, ok && d < 8 ? 1 : 0, 3.5, dt); sp.leaf.position.y = 2.15 + sp.doorOpen * 4.0;
      if (ok && !sp.built) { const rd = pw.distanceTo(new THREE.Vector3(Math.cos(sp.a) * ROOM_D, 0, Math.sin(sp.a) * ROOM_D).add(INTERIOR_ORIGIN)); if (rd < 170 || sp.k === 14 && rd < 330) this.buildRoomContents(sp.k); }
      if (sp.roomGroup) sp.roomGroup.visible = ok;
    }
    this.updaters.forEach((f) => f(dt, t));
    // pooled lights: attach to the three nearest anchors that are reachable
    const lp = pw.clone().sub(INTERIOR_ORIGIN); const cands = this.anchors.filter((a) => a.k < 0 || a.k >= 0 && this.roomOK(a.k)).map((a) => ({ a, d: a.p.distanceToSquared(lp) })).sort((x, y) => x.d - y.d).slice(0, 3);
    this.pool.forEach((l, i) => { const c = cands[i]; if (!c) { l.userData.i = 0; return; } l.position.lerp(c.a.p, 0.15); l.color.copy(c.a.col).lerp(new THREE.Color(0xffffff), 0.35); l.userData.i = 120 * (1 + (c.a.k === 14 ? 1.5 : 0)); });
    if (this.musicOn) audio.musicTick && audio.musicTick(dt);
  }
}

// ------------------------------------------------------------------ rooms
const ROOM_BUILDERS = {
  secondary({ c, prop, hit, scr, rm, em, I, col }) {
    const g = new THREE.Group(); g.position.set(0, 0, 0); c.add(g);
    prop(new THREE.CylinderGeometry(1.2, 1.4, 1.1, 6).translate(0, 0.55, 0), I.mats.metal, 0, 0, 0, g); prop(new THREE.CylinderGeometry(1.0, 1.15, 0.12, 6).translate(0, 1.16, 0), I.mats.panel, 0, 0, 0, g);
    const ctl = (x, z, color, label, run) => { prop(new THREE.CylinderGeometry(0.1, 0.1, 0.08, 12), em(color, 1.8), x, 1.25, z, g); hit(x, 1.3, z, label, run, { r: 0.28 }); };
    ctl(-0.5, 0.3, 0x9a7aff, () => `Cloak — ${G.ship.cloak.on ? 'engaged' : 'off'} (secondary)`, () => A.toggleCloak()); ctl(0, 0.35, 0x44aaff, () => `Shields — ${G.ship.shields ? 'raised' : 'down'} (secondary)`, () => A.toggleShields());
    ctl(0.5, 0.3, 0x44ffaa, 'Scan (secondary)', () => A.scan()); ctl(-0.3, -0.4, 0xffaa44, () => `Doors (secondary) — ${A.doorLabel()}`, () => A.toggleDoors()); ctl(0.3, -0.4, 0xff4444, 'Emergency recalibration', () => { A.recalibrateNav(); });
    scr((g2, W, H) => { g2.fillStyle = '#04070c'; g2.fillRect(0, 0, W, H); g2.fillStyle = '#ffb347'; g2.font = 'bold 28px monospace'; g2.textBaseline = 'top'; g2.fillText('SECONDARY CONTROL', 18, 16); g2.font = '22px monospace'; g2.fillStyle = '#fff'; g2.fillText(`Phase  ${G.ship.phase}`, 18, 70); g2.fillText(`Power  ${G.ship.power.toFixed(0)}%`, 18, 104); g2.fillText(`Dest   ${(G.dest.name || '').slice(0, 30)}`, 18, 138); g2.fillText(`Cloak  ${G.ship.cloak.on ? 'on' : 'off'}  Shields ${G.ship.shields ? 'up' : 'down'}`, 18, 172); }, 2.2, 1.4, 0, 3.2, -4);
  },
  library({ c, prop, hit, scr, rm, em, I, col }) {
    const mat = new THREE.MeshStandardMaterial({ color: 0x4a2c1a, roughness: 0.8 });
    const books = new THREE.MeshStandardMaterial({ color: 0x222222, emissive: 0xffb050, emissiveIntensity: 0.5 });
    for (let i = 0; i < 12; i++) { const a = -2.6 + i * 0.47 + (i > 5 ? 0.1 : 0); if (Math.abs(a + Math.PI) < 0.5) continue; const x = Math.cos(a + 3.14) * 14, z = Math.sin(a + 3.14) * 14; const sh = prop(new THREE.BoxGeometry(1.1, 5.5, 2.8), mat, x, 2.75, z); sh.rotation.y = -(a + 3.14); for (let r = 0; r < 5; r++) { const b = prop(new THREE.BoxGeometry(1.0, 0.04, 2.6), books, x, 0.7 + r * 1.0, z); b.rotation.y = -(a + 3.14); } }
    prop(new THREE.CylinderGeometry(2.2, 2.4, 0.9, 20).translate(0, 0.45, 0), rm(0x5a3a22), 0, 0, 0); prop(new THREE.SphereGeometry(0.7, 20, 14), em(0xffc060, 0.6), 0, 1.9, 0);
    for (let i = 0; i < 3; i++) { const a = i * 2.09; const x = Math.cos(a) * 5, z = Math.sin(a) * 5; prop(new THREE.CylinderGeometry(0.4, 0.5, 1.1, 10).translate(0, 0.55, 0), rm(0x3a2a20), x, 0, z); prop(new THREE.BoxGeometry(0.6, 0.1, 0.45), em(0xffc060, 1.2), x, 1.2, z); hit(x, 1.2, z, 'Read: Tome of Worlds', () => I.readBook(), { r: 0.8 }); }
    hit(0, 1.9, 0, 'Spin the globe — open the Archive', () => bus.emit('ui', 'archive'), { r: 1.2 });
  },
  navroom({ c, prop, hit, scr, rm, em, I, col }) {
    prop(new THREE.CylinderGeometry(2.4, 2.8, 1.0, 24).translate(0, 0.5, 0), I.mats.metal, 0, 0, 0);
    const holo = prop(new THREE.IcosahedronGeometry(1.1, 2), new THREE.MeshBasicMaterial({ color: col, wireframe: true, transparent: true, opacity: 0.55 }), 0, 2.6, 0);
    const ring = prop(new THREE.TorusGeometry(1.7, 0.02, 4, 48), em(col, 2.5), 0, 2.6, 0); ring.rotation.x = 1.2;
    I.updaters.push((dt, t) => { if (holo.visible) { holo.rotation.y += dt * 0.5; ring.rotation.z += dt; } });
    scr((g, W, H) => { g.fillStyle = '#04070c'; g.fillRect(0, 0, W, H); g.fillStyle = '#46e6ff'; g.font = 'bold 28px monospace'; g.textBaseline = 'top'; g.fillText('HOLOGRAPHIC CHART', 18, 14); g.font = '22px monospace'; g.fillStyle = '#fff'; g.fillText(G.dest.name || '', 18, 64); g.fillText(`${G.dest.year} · ${G.dest.month}/${G.dest.day} ${G.dest.hour}:00`, 18, 98); g.fillStyle = '#46e6ff'; g.fillText(`Dimensions ${G.dest.dim.join(' · ')}`, 18, 132); g.fillText('Press E at the table to open navigation', 18, 180); }, 3.2, 2.0, 0, 3.2, 0.1);
    hit(0, 1.2, 0, 'Chart table — open navigation', () => bus.emit('ui', 'terminal:nav'), { r: 2.2 });
    hit(3.2, 1.2, 3, 'Telepathic focus — suggest destinations', () => A.telepathic(), { r: 0.9 }); prop(new THREE.SphereGeometry(0.5, 16, 12), em(0xaa66ff, 1.8), 3.2, 1.2, 3);
  },
  engineering({ c, prop, hit, scr, rm, em, I, col }) {
    const core = prop(new THREE.CylinderGeometry(1.4, 1.4, 8, 20, 1, true).translate(0, 4, 0), new THREE.MeshStandardMaterial({ color: 0xffaa66, emissive: 0xff6a20, emissiveIntensity: 1.2, transparent: true, opacity: 0.55, side: THREE.DoubleSide }), 0, 0, 0);
    const rings = []; for (let i = 0; i < 4; i++) { const r = prop(new THREE.TorusGeometry(1.8, 0.14, 6, 24).rotateX(Math.PI / 2), I.mats.metal, 0, 1.2 + i * 1.8, 0); rings.push(r); }
    I.updaters.push((dt, t) => { rings.forEach((r, i) => { r.rotation.y += dt * (0.4 + i * 0.2); }); core.material.emissiveIntensity = 0.6 + (G.ship.power / 100) * 1.2 + (rt.flicker ? Math.random() : 0); });
    const station = (a, label, color, sys, text) => { const x = Math.cos(a) * 9, z = Math.sin(a) * 9; prop(new THREE.BoxGeometry(1.6, 1.1, 1.0).translate(0, 0.55, 0), I.mats.metal, x, 0, z).rotation.y = -a; prop(new THREE.BoxGeometry(1.4, 0.06, 0.8), em(color, 1.4), x, 1.15, z).rotation.y = -a; hit(x, 1.2, z, () => `${label} (hold to repair)`, () => { sys.forEach((s) => A.repair(s, 60)); say(text); }, { hold: 2.5, r: 1.0 }); };
    station(3.8, 'Navigation & dimensional repair', 0x55ccff, ['navStability', 'dimAlignment'], 'Lines re-drawn.'); station(4.6, 'Hull & console repair', 0xffaa44, ['hull', 'consoleCondition', 'interiorStability'], 'Rivets held. Rivets always hold.'); station(5.4, 'Life support & temporal repair', 0x66ff99, ['lifeSupport', 'temporalIntegrity'], 'Breathing easier.');
    prop(new THREE.CylinderGeometry(0.35, 0.35, 1.3, 10).translate(0, 0.65, 0), rm(0x882222), -9, 0, 5); hit(-9, 1.4, 5, 'Restart the reactor (hold)', () => A.restartReactor(), { hold: 1.5, r: 0.9 });
    scr((g, W, H) => { g.fillStyle = '#04070c'; g.fillRect(0, 0, W, H); g.fillStyle = '#ff9a40'; g.font = 'bold 26px monospace'; g.textBaseline = 'top'; g.fillText('ENGINEERING', 18, 12); g.font = '18px monospace'; SYSTEMS.forEach((k, i) => { const y = 54 + i * 28, v = G.ship[k]; g.fillStyle = '#ffb347'; g.fillText(SYSTEM_LABEL[k].slice(0, 20), 18, y); g.fillStyle = 'rgba(255,255,255,.15)'; g.fillRect(260, y + 3, 200, 14); g.fillStyle = v > 60 ? '#6bff8a' : v > 30 ? '#ffb347' : '#ff4a4a'; g.fillRect(260, y + 3, 2 * v, 14); }); }, 3.4, 2.2, -9, 3.2, -4, 0.7);
  },
  cloakroom({ c, prop, hit, scr, rm, em, I, col }) {
    CLOAK_MODES.forEach((m, i) => { const a = i / 6 * 6.28; const x = Math.cos(a) * 7, z = Math.sin(a) * 7; prop(new THREE.CylinderGeometry(1.1, 1.2, 0.3, 18).translate(0, 0.15, 0), em(0x9a7aff, 0.8), x, 0, z); const orb = prop(new THREE.IcosahedronGeometry(0.4, 1), new THREE.MeshBasicMaterial({ color: 0x9a7aff, wireframe: true }), x, 1.4, z); I.updaters.push((dt, t) => { orb.rotation.y += dt; orb.material.color.setHex(A.cloakModeUnlocked(m.id) ? (G.ship.cloak.mode === m.id ? 0x66ff99 : 0x9a7aff) : 0x444455); });
      hit(x, 1.2, z, () => `${m.name}${A.cloakModeUnlocked(m.id) ? (G.ship.cloak.mode === m.id ? ' — selected' : '') : ' — LOCKED (workshop upgrade)'}`, () => A.setCloakMode(m.id), { r: 1.2 }); });
    prop(new THREE.CylinderGeometry(1, 1.2, 1.1, 14).translate(0, 0.55, 0), I.mats.metal, 0, 0, 0); hit(0, 1.2, 0, () => `Cloak ${G.ship.cloak.on ? 'OFF' : 'ON'}`, () => A.toggleCloak(), { r: 1.2 });
    prop(new THREE.BoxGeometry(1.4, 1.4, 0.8).translate(0, 0.7, 0), I.mats.metal, -10, 0, 0); hit(-10, 1.2, 0, 'Reset cloak emitters (hold)', () => { A.repair('cloakIntegrity', 70); }, { hold: 2.5, r: 1.1 });
  },
  armory({ c, prop, hit, scr, rm, em, I, col }) {
    for (let i = 0; i < 8; i++) { const a = 2.4 + i * 0.5 - 1; const x = Math.cos(a + 3.14) * 13, z = Math.sin(a + 3.14) * 13; const rack = prop(new THREE.BoxGeometry(0.4, 2.4, 2.4), I.mats.metal, x, 1.2, z); rack.rotation.y = -(a + 3.14); for (let j = 0; j < 3; j++) prop(new THREE.CylinderGeometry(0.1, 0.1, 1.4, 8), em([0x66ccff, 0xff6666, 0xffdd66][j], 1.3), x * 0.97, 0.7 + j * 0.7, z * 0.97).rotation.z = 1.4; }
    prop(new THREE.CylinderGeometry(1.1, 1.3, 1.0, 12).translate(0, 0.5, 0), I.mats.metal, 0, 0, 0); const orb = prop(new THREE.SphereGeometry(0.5, 16, 12), new THREE.MeshBasicMaterial({ color: 0xff5566, wireframe: true }), 0, 1.7, 0);
    I.updaters.push((dt) => { orb.rotation.y += dt; orb.material.color.setHex(G.ship.defense ? 0x66ffaa : 0xff5566); });
    hit(0, 1.4, 0, () => `Non-lethal defence field — ${G.ship.defense ? 'ARMED' : 'off'}`, () => { G.ship.defense = !G.ship.defense; audio.click('big'); say(G.ship.defense ? 'Nothing here kills. Everything here stops.' : 'Disarmed.'); }, { r: 1.5 });
    hit(-5, 1.2, 3, 'Perception-filter rack — read manual', () => say('Non-lethal only: stun fields, repulsion nets and a very stern voice. Armed, the field halves hull damage and repels scanners.'), { r: 1.0 }); prop(new THREE.BoxGeometry(1.2, 0.2, 0.7), em(0x66ccff, 1), -5, 1.0, 3);
  },
  greenhouse({ c, prop, hit, scr, rm, em, I, col }) {
    const dome = prop(new THREE.SphereGeometry(14.5, 24, 12, 0, 6.28, 0, Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0x88ffcc, transparent: true, opacity: 0.1, side: THREE.DoubleSide, depthWrite: false }), 0, 7, 0); dome.visible = false;
    const names = ['Lumen fern', 'Hushbloom', 'Starvine', 'Glasswort', 'Moonpetal', 'Ember lily', 'Whisperreed', 'Pale oracle'], cols = [0x66ffaa, 0xff88dd, 0xffdd66, 0x66ddff, 0xaaaaff, 0xff9966, 0x99ff66, 0xffffff];
    const r = rng(3); const plants = [];
    for (let i = 0; i < 24; i++) { const a = r() * 6.28, d = 3 + r() * 10; const p = prop(new THREE.ConeGeometry(0.4, 1.8, 6).translate(0, 0.9, 0), new THREE.MeshStandardMaterial({ color: 0x1a4a2a }), Math.cos(a) * d, 0, Math.sin(a) * d); const bulb = prop(new THREE.SphereGeometry(0.28, 10, 8), em(cols[i % 8], 2.2), Math.cos(a) * d, 1.9, Math.sin(a) * d); plants.push({ p, bulb, name: names[i % 8], i });
      const id = `plant_${i}`; hit(Math.cos(a) * d, 1.3, Math.sin(a) * d, () => (G.disc.lifeforms[id] ? `${names[i % 8]} (sample taken)` : `Take a sample of ${names[i % 8]}`), () => { if (discover('lifeforms', id)) { G.ship.lifeSupport = Math.min(100, G.ship.lifeSupport + 4); audio.chime(i); log(`Sample logged: ${names[i % 8]} (+life support).`, 'sys'); bulb.material.emissiveIntensity = 0.3; } }, { r: 0.8 }); }
    I.updaters.push((dt, t) => plants.forEach((q) => { q.bulb.position.y = 1.9 + Math.sin(t * 1.3 + q.i) * 0.08; if (G.disc.lifeforms[`plant_${q.i}`]) q.bulb.material.emissiveIntensity = 0.3; }));
  },
  observation({ c, prop, hit, scr, rm, em, I, col, rg }) {
    const mat = I.winMat(); I.obsMat = mat; const w = new THREE.Mesh(new THREE.PlaneGeometry(26, 6), mat); w.position.set(14.2, 3.6, 0); w.rotation.y = -Math.PI / 2; c.add(w);
    const f = prop(new THREE.BoxGeometry(0.5, 7, 27), I.mats.accent, 14.6, 3.5, 0); void f;
    for (let i = -1; i <= 1; i++) { prop(new THREE.BoxGeometry(3.2, 0.5, 1.0), rm(0x2a2430), 9, 0.3, i * 4.5); prop(new THREE.BoxGeometry(3.2, 0.5, 0.2), rm(0x2a2430), 9.6, 0.85, i * 4.5); }
    const orb = prop(new THREE.SphereGeometry(0.4, 14, 10), em(0x88aaff, 2.8), 6, 1.4, -3); orb.visible = false;
    const h = hit(6, 1.4, -3, 'A strange resonance — investigate', () => { if (discover('secrets', 'signal')) { say('That was me. Inside me. Always inside me.'); audio.chime(7); orb.visible = false; } }, { r: 1.2 });
    I.updaters.push((dt, t) => { const show = G.disc.journeys >= 3 && !G.disc.secrets.signal; orb.visible = show; h.visible = true; h.userData.interact.enabled = show; orb.position.y = 1.4 + Math.sin(t * 2) * 0.12; mat.uniforms.uSpecial.value = G.disc.flags.lastWindow ? 1 : 0; if (G.disc.flags.lastWindow && discover('secrets', 'lastwindow')) say('A window onto a universe that no longer exists.'); });
    hit(10, 1, 0, 'Sit and watch', () => { say(G.ship.phase === 'vortex' ? 'It is more beautiful from out here, isn\'t it?' : 'Nothing moves. Everything moves.'); }, { r: 2 });
  },
  pool({ k, c, prop, hit, scr, rm, em, I, col }) {
    I.poolInfo = { r: 5.8, x: Math.cos(I.spokes[k].a) * ROOM_D, z: Math.sin(I.spokes[k].a) * ROOM_D }; const wp = I.poolInfo;
    const water = prop(new THREE.CircleGeometry(6, 36).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0x40e0ff, transparent: true, opacity: 0.75, roughness: 0.05, metalness: 0.3, emissive: 0x2288aa, emissiveIntensity: 0.4 }), 0, -0.25, 0);
    prop(new THREE.CylinderGeometry(6.1, 6.1, 1.1, 36, 1, true).translate(0, -0.45, 0), new THREE.MeshStandardMaterial({ color: 0x88e0ff, side: THREE.BackSide, emissive: 0x2288aa, emissiveIntensity: 0.6 }), 0, 0, 0); prop(new THREE.CircleGeometry(6, 36).rotateX(-Math.PI / 2), em(0x2288aa, 1.2), 0, -0.9, 0);
    for (let i = 0; i < 4; i++) { const a = 2.6 + i * 0.5; prop(new THREE.BoxGeometry(2.2, 0.35, 0.8), rm(0xf0e6d0), Math.cos(a) * 9 - 3, 0.2, Math.sin(a) * 9); }
    prop(new THREE.BoxGeometry(1.2, 0.9, 0.7), I.mats.metal, 9, 0.45, 5); hit(9, 1.1, 5, () => `Radio — ambient music ${I.musicOn ? 'ON' : 'off'}`, () => { I.musicOn = !I.musicOn; audio.setMusic && audio.setMusic(I.musicOn); audio.click('switch'); }, { r: 0.9 });
    I.updaters.push((dt, t) => { water.position.y = -0.25 + Math.sin(t * 1.2) * 0.02; }); void wp;
  },
  bedroom({ c, prop, hit, scr, rm, em, I, col }) {
    prop(new THREE.BoxGeometry(3.2, 0.5, 2.2), rm(0x5a3a4a), 0, 0.25, 0); prop(new THREE.BoxGeometry(2.9, 0.4, 2.0), rm(0xf0e0e8), 0, 0.7, 0); prop(new THREE.BoxGeometry(0.8, 0.25, 1.4), rm(0xffffff), -1.1, 1.0, 0);
    hit(0, 1, 0, 'Rest until morning (saves the game)', () => bus.emit('sleep'), { r: 1.8 });
    prop(new THREE.BoxGeometry(1.6, 2.6, 0.7), rm(0x4a3828), 6, 1.3, -6); hit(6, 1.3, -6, 'Wardrobe — open customization', () => bus.emit('ui', 'custom'), { r: 1.2 });
    prop(new THREE.BoxGeometry(1.4, 0.15, 0.9), rm(0x3a2a20), -6, 0.95, 5); hit(-6, 1.1, 5, 'Journal — review your log', () => bus.emit('ui', 'journal'), { r: 0.9 });
  },
  workshop({ c, prop, hit, scr, rm, em, I, col }) {
    prop(new THREE.BoxGeometry(5, 1.0, 1.8), rm(0x6a5a40, 0.7, 0.1), 0, 0.5, 0); prop(new THREE.BoxGeometry(0.8, 0.4, 0.8), em(0xffd060, 1.8), -1.5, 1.2, 0); prop(new THREE.TorusGeometry(0.5, 0.1, 6, 16), I.mats.accent, 1.2, 1.3, 0);
    hit(0, 1.2, 0, 'Workbench — craft upgrades from artifacts', () => bus.emit('ui', 'workshop'), { r: 2.4 });
    for (let i = 0; i < 6; i++) { const a = 0.4 + i * 0.9; prop(new THREE.BoxGeometry(1.5, 2.2, 0.6), I.mats.metal, Math.cos(a) * 12, 1.1, Math.sin(a) * 12 - 0.5).rotation.y = -a; }
    prop(new THREE.CylinderGeometry(1.2, 1.3, 0.9, 12), I.mats.metal, 7, 0.45, 6);
    hit(7, 1.2, 6, 'Spare time rotor — repair console condition', () => { A.repair('consoleCondition', 50); }, { hold: 2, r: 1.2 });
  },
  companion({ c, prop, hit, scr, rm, em, I, col }) {
    prop(new THREE.BoxGeometry(2.4, 0.5, 1.6), rm(0x6a4a3a), 0, 0.25, -6); prop(new THREE.BoxGeometry(2.2, 0.3, 1.5), rm(0xf0d0c0), 0, 0.65, -6);
    for (let i = 0; i < 5; i++) prop(new THREE.PlaneGeometry(1.2, 1.6), new THREE.MeshBasicMaterial({ color: new THREE.Color().setHSL(i * 0.2, 0.5, 0.5), side: THREE.DoubleSide }), -8 + i * 1.7, 3.5, -13.5);
    prop(new THREE.BoxGeometry(1.2, 0.1, 0.8), em(0xffd0a0, 1), 5, 1.0, 4);
    const hints = ['"The old switches on the emergency panel were never meant to be thrown in order. Second. First. Third."', '"If you ask the circuit five times, the ship tells you where the old ships go."', '"Three secrets and the room that should not exist unlocks itself."', '"The Last Universe has a window. You can see it from the observation deck once you have been."', '"Rooms come and go when the vortex is angry. Stand still and one will appear."', '"Not every door in the corridor is a door."'];
    hit(5, 1.1, 4, 'Read the diary', () => { const i = (G.disc.flags.diary || 0); say(hints[i % hints.length]); G.disc.flags.diary = i + 1; }, { r: 1.0 });
  },
  escape({ c, prop, hit, scr, rm, em, I, col }) {
    const pod = prop(new THREE.CapsuleGeometry(1.2, 2.2, 6, 16), rm(0xcc3030, 0.4, 0.6), 0, 1.8, 0); pod.rotation.z = Math.PI / 2; prop(new THREE.CylinderGeometry(2.2, 2.4, 0.4, 16), I.mats.metal, 0, 0.2, 0);
    hit(0, 1.8, 0, 'Launch escape pod — get out of trouble', () => bus.emit('escapePod'), { r: 2.0 });
    for (let i = 0; i < 6; i++) prop(new THREE.BoxGeometry(0.5, 0.15, 0.1), em(0xff3030, 2), -8 + i * 0.4, 0.5, 6);
  },
  sealed({ c, prop, hit, scr, rm, em, I, col, rg }) {
    // anteroom is radius 12 (centre at 108); the great hall lies beyond, centred ~245 from the console
    const hall = new THREE.Group(); hall.position.set(245 - 110, 0, 0); c.add(hall);
    const floor = new THREE.Mesh(new THREE.CircleGeometry(120, 96).rotateX(-Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0x14100c, roughness: 0.3, metalness: 0.6 })); hall.add(floor);
    for (let r = 20; r <= 110; r += 15) { const t = new THREE.Mesh(new THREE.TorusGeometry(r, 0.15, 4, 128).rotateX(Math.PI / 2).translate(0, 0.05, 0), em(0xffe0a0, 1.4)); hall.add(t); }
    const dome = new THREE.Mesh(new THREE.SphereGeometry(120, 48, 16, 0, 6.28, 0, Math.PI / 2), new THREE.MeshStandardMaterial({ color: 0x0a0806, side: THREE.BackSide, emissive: 0x241810, emissiveIntensity: 0.8 })); hall.add(dome);
    const stars = new THREE.Points(new THREE.BufferGeometry().setAttribute('position', new THREE.Float32BufferAttribute(Array.from({ length: 900 }, (_, i) => { const r2 = rng(i * 13 + 5); const a = r2() * 6.28, e = r2() * 1.4, d = 100; return [Math.cos(a) * Math.cos(e) * d, Math.sin(e) * d, Math.sin(a) * Math.cos(e) * d]; }).flat(), 3)), new THREE.PointsMaterial({ color: 0xffeecc, size: 0.9, sizeAttenuation: true })); hall.add(stars);
    const col2 = new THREE.Group(); for (let i = 0; i < 24; i++) { const a = i / 24 * 6.28; const p = new THREE.Mesh(new THREE.CylinderGeometry(1.4, 1.8, 60, 10).translate(0, 30, 0), I.mats.metal); p.position.set(Math.cos(a) * 80, 0, Math.sin(a) * 80); col2.add(p); } hall.add(col2);
    const origin = new THREE.Mesh(new THREE.TorusKnotGeometry(5, 1.2, 160, 12), em(0xffe0a0, 2.4)); origin.position.set(0, 22, 0); hall.add(origin); I.updaters.push((dt, t) => { origin.rotation.x += dt * 0.15; origin.rotation.y += dt * 0.2; });
    const pedestal = new THREE.Mesh(new THREE.CylinderGeometry(3, 4, 3, 14).translate(0, 1.5, 0), I.mats.metal); hall.add(pedestal);
    const h = new THREE.Mesh(new THREE.SphereGeometry(3, 8, 8), new THREE.MeshBasicMaterial({ visible: false })); h.position.set(0, 4, 0); h.userData.interact = { label: 'Touch the Origin', run: () => { if (discover('secrets', 'origin')) { say('This is where I was grown. This room is larger than all the others because it is where I started. Thank you for coming.'); G.disc.unlocks.skin5 = true; G.disc.unlocks.skin4 = true; log('Unlocked exterior skins: Gilded, Ghost white.', 'sys'); } else say('Every room is inside this one.'); } }; hall.add(h); I.reg(h);
    const wm = new THREE.MeshStandardMaterial({ color: 0x2a2018, side: THREE.BackSide, emissive: 0x180e08, emissiveIntensity: 0.6 });
    const a1 = new THREE.Mesh(new THREE.CylinderGeometry(12, 12, 14, 36, 1, true, -Math.PI / 2 + 0.23, Math.PI - 0.48).translate(0, 7, 0), wm), a2 = new THREE.Mesh(new THREE.CylinderGeometry(12, 12, 14, 36, 1, true, Math.PI / 2 + 0.25, Math.PI - 0.48).translate(0, 7, 0), wm); c.add(a1, a2);
    prop(new THREE.CircleGeometry(12, 36).rotateX(Math.PI / 2).translate(0, 14, 0), new THREE.MeshStandardMaterial({ color: 0x15100c }), 0, 0, 0);
    prop(new THREE.BoxGeometry(5, 0.1, 5), new THREE.MeshStandardMaterial({ color: 0x1a140e, metalness: 0.5, roughness: 0.4 }), 13.5, -0.05, 0);
    log('Something vast opens beyond the sealed door.', 'ship');
  },
  turbulence({ c, prop, hit, scr, rm, em, I, col }) {
    const mat = I.winMat(); mat.uniforms.uSpecial.value = 1; const w = new THREE.Mesh(new THREE.PlaneGeometry(18, 7), mat); w.position.set(14.6, 3.8, 0); w.rotation.y = -Math.PI / 2; c.add(w);
    prop(new THREE.CylinderGeometry(1, 1.2, 1.2, 14).translate(0, 0.6, 0), I.mats.metal, 0, 0, 0); prop(new THREE.SphereGeometry(0.4, 14, 10), em(0xff40ff, 2.5), 0, 1.7, 0);
    hit(0, 1.7, 0, 'Listen', () => { say('This room is only here while the vortex is angry. Remember it.'); discover('anomalies', 'turbulence_room', 'A corridor that appears only during turbulence'); }, { r: 1.2 });
    hit(10, 1, 0, 'Look through the window', () => { discover('anomalies', 'dead_window', 'A window overlooking a universe that no longer exists'); say('That sky belongs to somewhere that is gone.'); }, { r: 2.5 });
  },
};

Interior.prototype.readBook = function () {
  const dims = Object.values(WORLDS).filter((w) => w.kind === 'dimension' && !w.generated && !G.disc.dims[w.id] && !(G.disc.flags.leads || {})[w.id]);
  G.disc.flags.leads = G.disc.flags.leads || {};
  if (!dims.length) { say('Every page is blank now. You have read all that was written.'); return; }
  const w = dims[0]; G.disc.flags.leads[w.id] = true; audio.chime(3);
  log(`The book describes ${w.name}: "${w.hint}" Coordinates ${w.dimKey.join(' · ')}.`, 'sys'); say('Remember the numbers.');
  bus.emit('lead', w.id);
};
