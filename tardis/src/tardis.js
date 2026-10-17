// TARDIS exterior: detailed police-box model, doors, key & lock, roof lamp, dissolve/cloak shader hooks.
import { THREE, canvasTex, clamp, damp, lerp, rng } from './util.js';
import { G } from './state.js';

export const BLUES = [0x0b2f7a, 0x123f94, 0x071d52, 0x1b4fa8, 0x0a2660];
export const SKINS = [
  { name: 'Classic blue', color: null, metal: 0 }, { name: 'Midnight', color: 0x050b1e, metal: 0.2 },
  { name: 'Crimson', color: 0x7a0f18, metal: 0.1, unlock: 'skin2' }, { name: 'Emerald', color: 0x0c5a3a, metal: 0.15, unlock: 'skin3' },
  { name: 'Gilded', color: 0xb58a22, metal: 0.9, unlock: 'skin4' }, { name: 'Ghost white', color: 0xdfe6f2, metal: 0.05, unlock: 'skin5' },
];
const SIGNS = [['POLICE', 'PUBLIC CALL BOX'], ['POLICE', 'BOX'], ['PUBLIC', 'CALL BOX'], ['TELEPHONE', 'FREE FOR USE']];

// Shared uniforms injected into every exterior material (dissolve, cloak).
export const XU = {
  uDis: { value: 0 }, uStyle: { value: 0 }, uCloak: { value: 0 }, uCloakMode: { value: 1 }, uBaseY: { value: 0 }, uTime: { value: 0 },
  uEdge: { value: new THREE.Color(0.3, 0.8, 1) }, uCamo: { value: new THREE.Color(0.2, 0.25, 0.2) },
};

export function patchExterior(mat) {
  mat.transparent = true;
  mat.onBeforeCompile = (sh) => {
    Object.assign(sh.uniforms, XU);
    sh.vertexShader = sh.vertexShader.replace('void main() {', 'varying vec3 vWP;\nvoid main() {')
      .replace('#include <project_vertex>', '#include <project_vertex>\n vWP = (modelMatrix * vec4(transformed,1.0)).xyz;');
    sh.fragmentShader = sh.fragmentShader.replace('void main() {', `
      uniform float uDis,uStyle,uCloak,uCloakMode,uBaseY,uTime; uniform vec3 uEdge,uCamo; varying vec3 vWP;
      float hh(vec3 p){ p=fract(p*0.3183099+.1); p*=17.; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
      float vn(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.-2.*f);
        return mix(mix(mix(hh(i),hh(i+vec3(1,0,0)),f.x),mix(hh(i+vec3(0,1,0)),hh(i+vec3(1,1,0)),f.x),f.y),
                   mix(mix(hh(i+vec3(0,0,1)),hh(i+vec3(1,0,1)),f.x),mix(hh(i+vec3(0,1,1)),hh(i+vec3(1,1,1)),f.x),f.y),f.z); }
      void main() {
        float xvis=1.; float xglow=0.;
        if(uDis>0.001){
          float dn=vn(vWP*7.+vec3(0.,uTime*0.3,0.));
          if(uStyle<0.5){ xvis=1.-smoothstep(0.,1.,uDis*1.35-dn*0.35); xglow=(1.-xvis)*xvis*3.; }
          else if(uStyle<1.5){ float th=uDis*1.2-0.1; if(dn<th) discard; xglow=1.-smoothstep(0.,0.1,dn-th); }
          else if(uStyle<2.5){ vec3 c=floor(vWP*vec3(4.,9.,4.)); float r=hh(c+floor(uTime*2.)); float th=uDis*1.15-0.05; if(r<th) discard; xglow=1.-smoothstep(0.,0.15,r-th); }
          else if(uStyle<3.5){ float y=(vWP.y-uBaseY)/3.2; float th=1.-uDis; if(y>th) discard; xglow=smoothstep(0.1,0.,th-y)+step(0.93,fract(vWP.y*28.))*0.5*uDis; }
          else { float fl=hh(vec3(floor(uTime*16.),floor(vWP.y*5.),floor(vWP.x*5.+vWP.z*5.))); if(fl<uDis*1.05-0.02) discard; xglow=(1.-smoothstep(0.,0.2,fl-uDis))*1.5; }
        }
        float xa=xvis;
        if(uCloak>0.001){
          if(uCloakMode<1.5) xa*=1.-uCloak*0.78;
          else if(uCloakMode<2.5) xa*=1.-uCloak*0.97;
          else if(uCloakMode<3.5) xa*=1.-uCloak;
          else if(uCloakMode<4.5) xa*=1.-uCloak*(0.55+0.4*step(0.5,hh(vec3(floor(uTime*9.),vWP.y*3.,1.))));
          else xa*=1.-uCloak*0.86;
        }
        `)
      .replace('#include <opaque_fragment>', `#include <opaque_fragment>
        if(uCloak>0.001 && uCloakMode<1.5) gl_FragColor.rgb=mix(gl_FragColor.rgb,uCamo,uCloak*0.75);
        if(uCloak>0.001 && uCloakMode>4.5){ float fr=pow(1.-abs(dot(normalize(vNormal),normalize(vViewPosition))),2.5); gl_FragColor.rgb+=vec3(0.2,0.7,1.)*fr*uCloak*2.2; xa=max(xa,fr*uCloak*0.6); }
        gl_FragColor.rgb+=uEdge*xglow*3.;
        gl_FragColor.a*=xa;
        if(gl_FragColor.a<0.004) discard;`);
  };
  mat.customProgramCacheKey = () => 'tardis-ext';
  return mat;
}

function box(w, h, d, mat, x = 0, y = 0, z = 0) {
  const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat);
  m.position.set(x, y, z); m.castShadow = true; m.receiveShadow = true; return m;
}
function glowTex() {
  return canvasTex(128, 128, (g) => {
    const gr = g.createRadialGradient(64, 64, 0, 64, 64, 64);
    gr.addColorStop(0, 'rgba(255,255,255,1)'); gr.addColorStop(0.25, 'rgba(180,220,255,.5)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
    g.fillStyle = gr; g.fillRect(0, 0, 128, 128);
  });
}

function wearTex(level) {
  const r = rng(5 + level);
  return canvasTex(256, 256, (g, w, h) => {
    g.fillStyle = '#fff'; g.fillRect(0, 0, w, h);
    const n = [0, 900, 3200][level];
    for (let i = 0; i < n; i++) { const a = 0.04 + r() * 0.16 * level; g.fillStyle = `rgba(${r() > 0.7 ? '200,190,150' : '0,0,0'},${a})`; g.fillRect(r() * w, r() * h, 1 + r() * 3 * level, 1 + r() * 14); }
    if (level) { const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, 'rgba(0,0,0,0)'); gr.addColorStop(1, `rgba(40,30,20,${0.22 * level})`); g.fillStyle = gr; g.fillRect(0, 0, w, h); }
  }, { repeat: true });
}

// Door leaf pair, reused for the outside doorway (blue) and the inside doorway.
export function makeLeaves(paintMat, glassMat, trimMat) {
  const mk = (side) => {
    const piv = new THREE.Group(); piv.position.set(side * 0.46, 0, 0);
    const dir = -side; // leaf extends toward doorway centre
    const w = 0.46;
    const lf = new THREE.Group(); lf.position.x = dir * w / 2; piv.add(lf);
    lf.add(box(w, 2.05, 0.05, paintMat, 0, 1.175, 0));
    for (const [y, h] of [[0.55, 0.62], [1.2, 0.5]]) lf.add(box(w - 0.12, h, 0.03, paintMat, 0, y, 0.035));
    for (let i = 0; i < 2; i++) for (let j = 0; j < 3; j++) {
      const p = new THREE.Mesh(new THREE.PlaneGeometry(0.15, 0.17), glassMat); p.position.set((i - 0.5) * 0.18, 1.58 + j * 0.2 - 0.2, 0.04); lf.add(p);
    }
    lf.add(box(w - 0.04, 0.03, 0.06, paintMat, 0, 1.46, 0.02));
    const handle = box(0.025, 0.2, 0.04, trimMat, dir * (w / 2 - 0.05), 1.05, 0.06); lf.add(handle);
    return { piv, lf };
  };
  return { L: mk(-1), R: mk(1) };
}

export class Tardis {
  constructor() {
    this.group = new THREE.Group();
    this.body = new THREE.Group(); this.group.add(this.body);
    this.hits = []; this.doors = { l: 0, r: 0 };
    this.lampMode = 'idle'; this.lampPhase = 0; this.wet = 0; this.jitter = 0; this.baseRough = 0.55;
    this.paintMat = patchExterior(new THREE.MeshStandardMaterial({ color: BLUES[0], roughness: 0.55, metalness: 0.05 }));
    this.trimMat = patchExterior(new THREE.MeshStandardMaterial({ color: 0xc9a54b, roughness: 0.35, metalness: 0.9 }));
    this.glassMat = patchExterior(new THREE.MeshStandardMaterial({ color: 0xfff0c0, emissive: 0xffd890, emissiveIntensity: 1.1, roughness: 0.2 }));
    this.darkMat = patchExterior(new THREE.MeshStandardMaterial({ color: 0x0a0c12, roughness: 0.8 }));
    this.lampMat = patchExterior(new THREE.MeshStandardMaterial({ color: 0xffffff, emissive: 0x9fd8ff, emissiveIntensity: 2, roughness: 0.2 }));
    this.signMat = patchExterior(new THREE.MeshStandardMaterial({ color: 0xffffff, roughness: 0.6, map: null }));
    this.signTex = null;
    this.build();
    this.setSign(0); this.setCondition(0);
    this.applyCustom();
  }

  build() {
    const P = this.paintMat, T = this.trimMat, B = this.body;
    B.add(box(1.54, 0.15, 1.54, P, 0, 0.075, 0));
    for (const sx of [-1, 1]) for (const sz of [-1, 1]) B.add(box(0.1, 2.32, 0.1, P, sx * 0.62, 1.31, sz * 0.62));
    // three plain sides (back, left, right), each with lower panels, windows, and a header sign
    const sides = [[0, Math.PI], [-1, -Math.PI / 2], [1, Math.PI / 2]];
    this.signs = [];
    for (const [, rot] of sides) {
      const s = new THREE.Group(); s.rotation.y = rot; B.add(s);
      s.add(box(1.14, 1.2, 0.05, P, 0, 0.78, 0.6));
      for (let i = 0; i < 2; i++) for (let j = 0; j < 2; j++) s.add(box(0.46, 0.5, 0.03, P, (i - 0.5) * 0.54, 0.5 + j * 0.56, 0.635));
      s.add(box(1.14, 0.06, 0.07, P, 0, 1.43, 0.6));
      for (let i = 0; i < 3; i++) for (let j = 0; j < 2; j++) {
        s.add(box(0.34, 0.015, 0.07, P, (i - 1) * 0.37, 1.58 + j * 0.34 - 0.0, 0.6));
        const gl = new THREE.Mesh(new THREE.PlaneGeometry(0.31, 0.28), this.glassMat); gl.position.set((i - 1) * 0.37, 1.58 + j * 0.34 + 0.16 - 0.2, 0.605); s.add(gl);
        if (j === 0) s.add(box(0.012, 0.3, 0.07, T, (i - 1) * 0.37 + 0.0, 1.5, 0.61));
      }
      this.addSign(s, 0.6);
    }
    // front (door) face
    const f = new THREE.Group(); B.add(f);
    f.add(box(0.1, 2.2, 0.12, P, -0.52, 1.25, 0.6)); f.add(box(0.1, 2.2, 0.12, P, 0.52, 1.25, 0.6));
    f.add(box(1.14, 0.08, 0.1, P, 0, 2.2, 0.6));
    this.addSign(f, 0.6);
    const lv = makeLeaves(this.paintMat, this.glassMat, T);
    this.leafL = lv.L; this.leafR = lv.R;
    lv.L.piv.position.z = 0.6; lv.R.piv.position.z = 0.6; f.add(lv.L.piv, lv.R.piv);
    // notice board + phone flap on door leaves
    const nb = new THREE.Mesh(new THREE.PlaneGeometry(0.16, 0.2), new THREE.MeshStandardMaterial({
      map: canvasTex(64, 80, (g, w, h) => { g.fillStyle = '#e8e2cf'; g.fillRect(0, 0, w, h); g.fillStyle = '#222'; g.font = 'bold 8px sans-serif'; ['PUBLIC', 'TELEPHONE', 'for use of', 'POLICE', 'PULL TO', 'OPEN'].forEach((t, i) => g.fillText(t, 6, 14 + i * 11)); }), roughness: 0.9,
    }));
    nb.position.set(0.03, 0.95, 0.065); lv.R.lf.add(nb);
    // lock plate + keyhole + key
    this.lockPlate = box(0.07, 0.12, 0.02, T, 0.4, 1.02, 0.66); B.add(this.lockPlate);
    const hole = new THREE.Mesh(new THREE.CylinderGeometry(0.012, 0.012, 0.03, 8).rotateX(Math.PI / 2), this.darkMat); hole.position.set(0.4, 1.02, 0.672); B.add(hole);
    this.key = new THREE.Group(); this.key.position.set(0.4, 1.02, 0.69);
    const kb = new THREE.Mesh(new THREE.CylinderGeometry(0.008, 0.008, 0.07, 8).rotateX(Math.PI / 2), T); kb.position.z = 0.02; this.key.add(kb);
    const kh = new THREE.Mesh(new THREE.TorusGeometry(0.03, 0.007, 6, 16), T); kh.position.z = 0.06; this.key.add(kh);
    B.add(this.key);
    // roof
    B.add(box(1.52, 0.1, 1.52, P, 0, 2.5, 0)); B.add(box(1.34, 0.12, 1.34, P, 0, 2.61, 0));
    const roof = new THREE.Mesh(new THREE.CylinderGeometry(0.34, 0.62, 0.3, 4).rotateY(Math.PI / 4), P); roof.position.y = 2.82; roof.castShadow = true; B.add(roof);
    B.add(box(0.5, 0.06, 0.5, T, 0, 2.99, 0));
    this.lampStyles = [];
    const ls = [
      new THREE.Mesh(new THREE.CylinderGeometry(0.12, 0.14, 0.3, 14), this.lampMat),
      (() => { const g = new THREE.Group(); for (let i = 0; i < 5; i++) { const r = new THREE.Mesh(new THREE.CylinderGeometry(0.1 + i * 0.012, 0.12 + i * 0.012, 0.045, 14), this.lampMat); r.position.y = (i - 2) * 0.06; g.add(r); } return g; })(),
      new THREE.Mesh(new THREE.OctahedronGeometry(0.17).scale(0.8, 1.5, 0.8), this.lampMat),
      new THREE.Mesh(new THREE.TorusGeometry(0.13, 0.04, 8, 24).rotateX(Math.PI / 2), this.lampMat),
    ];
    ls.forEach((m) => { m.position.y = 3.16; B.add(m); this.lampStyles.push(m); });
    B.add(box(0.12, 0.08, 0.12, T, 0, 3.35, 0));
    this.lampAnchor = new THREE.Object3D(); this.lampAnchor.position.y = 3.16; B.add(this.lampAnchor);
    this.glow = new THREE.Sprite(new THREE.SpriteMaterial({ map: glowTex(), color: 0x9fd8ff, blending: THREE.AdditiveBlending, depthWrite: false, transparent: true, fog: false }));
    this.glow.position.y = 3.16; this.glow.scale.setScalar(1.6); B.add(this.glow);
    this.lampLight = new THREE.PointLight(0x9fd8ff, 4, 0, 1.2); this.lampLight.position.set(0, 3.3, 0); this.group.add(this.lampLight);
    // light spilling through the open doors
    this.spill = new THREE.PointLight(0xffd8a0, 0, 14, 1.4); this.spill.position.set(0, 1.4, 1.4); this.group.add(this.spill);
    const spTex = canvasTex(128, 128, (g) => { const gr = g.createRadialGradient(64, 0, 0, 64, 0, 128); gr.addColorStop(0, 'rgba(255,220,160,.9)'); gr.addColorStop(1, 'rgba(255,200,120,0)'); g.fillStyle = gr; g.fillRect(0, 0, 128, 128); });
    this.spillMesh = new THREE.Mesh(new THREE.PlaneGeometry(2.4, 4.5).rotateX(-Math.PI / 2), new THREE.MeshBasicMaterial({ map: spTex, transparent: true, opacity: 0, blending: THREE.AdditiveBlending, depthWrite: false, fog: false }));
    this.spillMesh.position.set(0, 0.03, 0.68 + 2.2); this.group.add(this.spillMesh);

    // door-plane anchor: the portal quad lives here (door plane at z = 0.6)
    this.doorAnchor = new THREE.Object3D(); this.doorAnchor.position.set(0, 0, 0.6); this.group.add(this.doorAnchor);

    // disguise (cloak mode 3)
    this.disguise = new THREE.Group(); this.disguise.visible = false; this.group.add(this.disguise);

    // cloak haze shell (refraction of the background captured by the renderer)
    this.hazeMat = new THREE.ShaderMaterial({
      transparent: true, depthWrite: false, fog: false,
      uniforms: { uTex: { value: null }, uRes: { value: new THREE.Vector2(1, 1) }, uAmt: { value: 0 }, uTime: { value: 0 }, uFx: { value: 0 } },
      vertexShader: 'varying vec3 vN; varying vec3 vW; void main(){ vN=normalize(normalMatrix*normal); vec4 w=modelMatrix*vec4(position,1.); vW=w.xyz; gl_Position=projectionMatrix*viewMatrix*w; }',
      fragmentShader: `uniform sampler2D uTex; uniform vec2 uRes; uniform float uAmt,uTime,uFx; varying vec3 vN; varying vec3 vW;
        float hh(vec3 p){ p=fract(p*0.3183099+.1); p*=17.; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
        float vn(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.-2.*f); return mix(mix(mix(hh(i),hh(i+vec3(1,0,0)),f.x),mix(hh(i+vec3(0,1,0)),hh(i+vec3(1,1,0)),f.x),f.y),mix(mix(hh(i+vec3(0,0,1)),hh(i+vec3(1,0,1)),f.x),mix(hh(i+vec3(0,1,1)),hh(i+vec3(1,1,1)),f.x),f.y),f.z); }
        void main(){
          vec2 uv=gl_FragCoord.xy/uRes;
          float up=vW.y*3.-uTime*(uFx<0.5?2.2:5.);
          vec2 d=vec2(vn(vec3(vW.xz*6.,up))-.5,vn(vec3(vW.zx*6.+9.,up))-.5)*0.045*uAmt;
          if(uFx>0.5&&uFx<1.5) d.x+=(step(0.85,hh(vec3(floor(vW.y*14.),floor(uTime*10.),1.)))-0.0)*0.03*uAmt; // glitch slices
          if(uFx>1.5) d+=vec2(sin(vW.y*30.-uTime*8.),cos(vW.x*30.+uTime*6.))*0.006*uAmt; // ripple
          d+=vN.xy*0.03*uAmt;
          vec3 c=texture2D(uTex,uv+d).rgb;
          float edge=pow(1.-abs(vN.z),3.);
          c+=vec3(.15,.4,.6)*edge*0.25*uAmt;
          gl_FragColor=vec4(c,clamp(uAmt*1.4,0.,1.));
          #include <tonemapping_fragment>
          #include <colorspace_fragment>
        }`,
    });
    this.haze = new THREE.Mesh(new THREE.BoxGeometry(1.5, 3.3, 1.5).translate(0, 1.65, 0), this.hazeMat); this.haze.visible = false; this.haze.renderOrder = 5; this.group.add(this.haze);

    // interaction proxies (invisible but ray-castable, so interaction stays consistent while cloaked)
    const hitMat = new THREE.MeshBasicMaterial({ visible: false });
    const mkHit = (x, y, z, w, h, d, label, run, parent = this.group) => {
      const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), hitMat); m.position.set(x, y, z); parent.add(m);
      m.userData.interact = { label, run }; this.hits.push(m); return m;
    };
    this.hitL = mkHit(-0.23, 1.17, 0.66, 0.4, 2.0, 0.2, () => this.A.doorLabel(), (o) => this.A.exteriorDoor('l', o));
    this.hitR = mkHit(0.23, 1.17, 0.66, 0.4, 2.0, 0.2, () => this.A.doorLabel(), (o) => this.A.exteriorDoor('r', o));
    this.hitKey = mkHit(0.4, 1.02, 0.7, 0.14, 0.18, 0.12, () => (G.ship.doors.locked ? 'Unlock with key' : 'Lock with key'), () => this.A.toggleLock());
    this.hitKnock = mkHit(-0.9, 1.0, 0.45, 0.5, 0.6, 0.6, () => 'Knock', () => this.A.knock());
    this.hitEmerg = mkHit(0.4, 0.75, 0.7, 0.12, 0.1, 0.1, () => 'Emergency release', () => this.A.emergencyRelease());
    this.hitInspect = mkHit(0, 1.7, -0.7, 1.4, 1.6, 0.2, () => 'Inspect', () => this.A.inspect());
    this.group.traverse((o) => { if (o.isMesh && o.material && o.material !== hitMat) o.frustumCulled = true; });
  }

  addSign(parent, z) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(1.2, 0.2, 0.06), this.signMat); m.position.set(0, 2.33, z + 0.01);
    m.castShadow = true; parent.add(m); this.signs.push(m);
  }
  setSign(i) {
    const [a, b] = SIGNS[i % SIGNS.length];
    const tex = canvasTex(512, 88, (g, w, h) => {
      g.fillStyle = '#0b0b10'; g.fillRect(0, 0, w, h); g.fillStyle = '#f2f2ee'; g.font = 'bold 30px Georgia, serif'; g.textAlign = 'center'; g.textBaseline = 'middle';
      g.fillText(`${a}  ·  ${b}`, w / 2, h / 2 + 2); g.strokeStyle = '#444'; g.strokeRect(3, 3, w - 6, h - 6);
    });
    if (this.signTex) this.signTex.dispose();
    this.signTex = tex; this.signMat.map = tex; this.signMat.needsUpdate = true;
  }
  setCondition(l) {
    this.wearMap = wearTex(l);
    this.paintMat.map = this.wearMap; this.paintMat.needsUpdate = true;
    this.baseRough = [0.5, 0.72, 0.9][l]; this.applyWet();
  }
  setLamp(i) { this.lampStyles.forEach((m, k) => { m.visible = k === i; }); }
  applyWet() { this.paintMat.roughness = this.baseRough * (1 - 0.55 * this.wet); }

  applyCustom() {
    const c = G.custom, skin = SKINS[c.skin] || SKINS[0];
    this.paintMat.color.setHex(skin.color ?? BLUES[c.paint % BLUES.length]);
    this.paintMat.metalness = skin.metal;
    this.setLamp(c.lamp);
    const doorMats = [[0xc9a54b, 0.9], [0x6d4a2a, 0.1], [0x7c8591, 0.9], [0x1a2a4a, 0.3]][c.door % 4];
    this.trimMat.color.setHex(doorMats[0]); this.trimMat.metalness = doorMats[1];
    XU.uStyle.value = c.matStyle;
  }
  setCondIfChanged() { if (this._cond !== G.custom.condition) { this._cond = G.custom.condition; this.setCondition(G.custom.condition); } }

  setDisguise(kind) {
    while (this.disguise.children.length) this.disguise.remove(this.disguise.children[0]);
    const m = new THREE.MeshStandardMaterial({ color: 0x555a50, roughness: 1 });
    let o;
    switch (kind) {
      case 'lamp': o = new THREE.Group(); o.add(box(0.2, 3.2, 0.2, new THREE.MeshStandardMaterial({ color: 0x1a1a1a, metalness: 0.6 }), 0, 1.6, 0)); o.add(box(0.5, 0.5, 0.5, new THREE.MeshStandardMaterial({ color: 0xffe0a0, emissive: 0xffc060, emissiveIntensity: 1 }), 0, 3.3, 0)); break;
      case 'tree': o = new THREE.Group(); o.add(new THREE.Mesh(new THREE.CylinderGeometry(0.2, 0.3, 2, 8), new THREE.MeshStandardMaterial({ color: 0x4a3322 })).translateY(1)); o.add(new THREE.Mesh(new THREE.IcosahedronGeometry(1.1, 1), new THREE.MeshStandardMaterial({ color: 0x2c5a2e, flatShading: true })).translateY(2.8)); break;
      case 'crystal': o = new THREE.Mesh(new THREE.ConeGeometry(0.7, 3.2, 6), new THREE.MeshStandardMaterial({ color: 0x66ddff, emissive: 0x2288aa, transparent: true, opacity: 0.8 })); o.position.y = 1.6; break;
      case 'crate': o = box(1.4, 1.4, 1.4, new THREE.MeshStandardMaterial({ color: 0x6b5a3a, roughness: 1 }), 0, 0.7, 0); break;
      default: o = new THREE.Mesh(new THREE.DodecahedronGeometry(1.1, 0).scale(1, 1.4, 1), m); o.position.y = 1.2; o.castShadow = true;
    }
    o.traverse((x) => { if (x.isMesh) { x.castShadow = true; } });
    this.disguise.add(o);
  }

  // called every frame by main with the shared ship/flight context
  update(dt, t, c) {
    XU.uTime.value = t;
    // doors
    this.doors.l = damp(this.doors.l, G.ship.doors.left, 4.5, dt); this.doors.r = damp(this.doors.r, G.ship.doors.right, 4.5, dt);
    const ang = Math.PI * 0.62;
    this.leafL.piv.rotation.y = -this.doors.l * ang; this.leafR.piv.rotation.y = this.doors.r * ang;
    // key
    const unlocked = !G.ship.doors.locked;
    this.key.rotation.z = damp(this.key.rotation.z, unlocked ? Math.PI / 2 : 0, 8, dt);
    this.key.position.z = damp(this.key.position.z, unlocked ? 0.74 : 0.69, 8, dt);
    const open = Math.max(this.doors.l, this.doors.r);
    this.spill.intensity = open * 14 * c.interiorLight; this.spillMesh.material.opacity = open * 0.55 * (c.visible ? 1 : 0.2);
    this.spillMesh.visible = open > 0.02;

    // lamp
    this.lampPhase += dt;
    let col = new THREE.Color(0x9fd8ff), inten = 1.2 + 0.6 * Math.sin(this.lampPhase * 1.4), rate = 1;
    switch (c.lamp) {
      case 'flight': col.set(0xbfe6ff); inten = 3 + 2.5 * Math.sin(this.lampPhase * 9); break;
      case 'demat': col.set(0xffffff); inten = 4 + 4 * Math.abs(Math.sin(this.lampPhase * 6)); break;
      case 'emergency': col.set(0xff2a20); inten = 3 * (Math.sin(this.lampPhase * 7) > 0 ? 1 : 0.1); break;
      case 'lowpower': col.set(0xffa030); inten = 0.5 + 0.5 * (Math.sin(this.lampPhase * 17) > 0.4 ? 1 : 0.1); break;
      case 'dimension': col.setHSL((this.lampPhase * 0.2) % 1, 0.8, 0.7); inten = 2 + Math.sin(this.lampPhase * 3); break;
      case 'off': inten = 0; break;
    }
    const lampOn = c.lampOn ? 1 : 0;
    this.lampMat.emissive.copy(col); this.lampMat.emissiveIntensity = inten * lampOn;
    this.glow.material.color.copy(col); this.glow.material.opacity = clamp(inten * 0.35) * lampOn * (c.visible ? 1 : 0.15);
    this.glow.scale.setScalar(1.2 + inten * 0.25);
    this.lampLight.color.copy(col); this.lampLight.intensity = inten * 2.2 * lampOn * c.exteriorLight;
    this.glassMat.emissiveIntensity = 0.6 + open * 0.8 + (c.interiorOn ? 0.5 : 0);

    // vibration while the ship is dematerializing / flying
    this.jitter = damp(this.jitter, c.jitter, 3, dt);
    if (this.jitter > 0.001) this.body.position.set((Math.random() - 0.5) * 0.02 * this.jitter, (Math.random() - 0.5) * 0.015 * this.jitter, (Math.random() - 0.5) * 0.02 * this.jitter);
    else this.body.position.set(0, 0, 0);

    // wet surface
    if (Math.abs(this.wet - c.wet) > 0.01) { this.wet = damp(this.wet, c.wet, 0.8, dt); this.applyWet(); }

    // disguise for cloak mode 3
    const dis = G.ship.cloak.mode === 3 && c.cloak > 0.4;
    this.disguise.visible = dis;
    this.disguise.scale.setScalar(0.4 + 0.6 * clamp((c.cloak - 0.3) * 1.6));
    // haze shell
    const hz = c.cloak > 0.02 || c.dissolve > 0.02;
    this.haze.visible = hz && c.visible;
    this.hazeMat.uniforms.uAmt.value = clamp(Math.max(c.cloak * (G.ship.cloak.mode === 2 ? 1 : 0.5), c.dissolve * 0.6));
    this.hazeMat.uniforms.uTime.value = t; this.hazeMat.uniforms.uFx.value = G.custom.cloakFx;

    XU.uDis.value = c.dissolve; XU.uCloak.value = c.cloak; XU.uCloakMode.value = G.ship.cloak.mode; XU.uBaseY.value = this.group.position.y;
    const e = [new THREE.Color(0.7, 0.8, 1), new THREE.Color(0.2, 0.7, 1), new THREE.Color(0.7, 0.3, 1), new THREE.Color(0.3, 1, 0.8), new THREE.Color(1, 0.4, 0.15)][G.custom.matStyle % 5];
    XU.uEdge.value.copy(e);
  }
}
