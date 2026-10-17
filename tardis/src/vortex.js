// The time vortex: a real 3D tunnel (shader clouds + filaments + rings), instanced speed streaks,
// pooled "fragments of reality" (planets, galaxies, memory scenes) and rare impossible silhouettes.
import { THREE, rng, canvasTex, clamp, damp, lerp } from './util.js';

export const VORTEX_ORIGIN = new THREE.Vector3(-4000, 0, 0);

const PALETTES = [
  { a: [0.02, 0.01, 0.16], b: [0.05, 0.25, 0.85], c: [0.2, 0.9, 1.0], hot: [0.8, 0.9, 1.0] },   // classic
  { a: [0.01, 0.06, 0.12], b: [0.1, 0.55, 0.5], c: [0.6, 0.4, 1.0], hot: [0.8, 1.0, 0.9] },     // aurora
  { a: [0.1, 0.01, 0.08], b: [0.7, 0.2, 0.2], c: [1.0, 0.7, 0.2], hot: [1.0, 0.9, 0.7] },       // ember
  { a: [0.05, 0.01, 0.2], b: [0.6, 0.15, 0.8], c: [0.2, 1.0, 0.8], hot: [1.0, 0.8, 1.0] },      // prismatic
];
const MODES = {
  normal: { mix: null, turb: 0.3, dmg: 0, hue: 0 },
  emergency: { mix: { a: [0.15, 0.0, 0.0], b: [0.9, 0.1, 0.05], c: [1.0, 0.65, 0.1], hot: [1, 0.9, 0.8] }, turb: 1.4, dmg: 0, hue: 0 },
  dimensional: { mix: { a: [0.06, 0.0, 0.18], b: [0.5, 0.1, 0.9], c: [0.1, 1.0, 0.7], hot: [1, 0.8, 1] }, turb: 0.7, dmg: 0, hue: 1 },
  damaged: { mix: { a: [0.03, 0.03, 0.05], b: [0.18, 0.2, 0.3], c: [0.5, 0.55, 0.65], hot: [0.8, 0.8, 0.8] }, turb: 1.0, dmg: 1, hue: 0 },
};

const NOISE = `
float h(vec3 p){ p=fract(p*0.3183099+.1); p*=17.; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
float n3(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.-2.*f);
 return mix(mix(mix(h(i),h(i+vec3(1,0,0)),f.x),mix(h(i+vec3(0,1,0)),h(i+vec3(1,1,0)),f.x),f.y),
            mix(mix(h(i+vec3(0,0,1)),h(i+vec3(1,0,1)),f.x),mix(h(i+vec3(0,1,1)),h(i+vec3(1,1,1)),f.x),f.y),f.z); }
float fbm(vec3 p){ float a=.5,s=0.; for(int i=0;i<5;i++){ s+=a*n3(p); p=p*2.03+vec3(1.7,9.2,3.1); a*=.5;} return s; }
`;

function tunnelMaterial(layer) {
  return new THREE.ShaderMaterial({
    side: THREE.BackSide, transparent: !!layer, depthWrite: !layer, blending: layer ? THREE.AdditiveBlending : THREE.NormalBlending,
    fog: false,
    uniforms: {
      uT: { value: 0 }, uSpeed: { value: 1 }, uFlash: { value: 0 }, uBoltA: { value: 0 }, uTurb: { value: 0.3 }, uDmg: { value: 0 }, uHue: { value: 0 },
      uA: { value: new THREE.Color() }, uB: { value: new THREE.Color() }, uC: { value: new THREE.Color() }, uHot: { value: new THREE.Color() },
      uLayer: { value: layer }, uShift: { value: 0 },
    },
    vertexShader: `varying vec3 vP; varying vec3 vW; void main(){ vP=position; vec4 w=modelMatrix*vec4(position,1.); vW=w.xyz; gl_Position=projectionMatrix*viewMatrix*w; }`,
    fragmentShader: `
      uniform float uT,uSpeed,uFlash,uBoltA,uTurb,uDmg,uHue,uLayer,uShift; uniform vec3 uA,uB,uC,uHot;
      varying vec3 vP; varying vec3 vW;
      ${NOISE}
      vec3 hueRot(vec3 c,float a){ const vec3 k=vec3(.57735); float ca=cos(a),sa=sin(a); return c*ca+cross(k,c)*sa+k*dot(k,c)*(1.-ca); }
      void main(){
        float ang=atan(vP.y,vP.x); float z=vP.z; float t=uT;
        float rip=sin(z*0.05-t*2.)*0.06*uTurb; ang+=rip;
        float zz=z*0.012-t*0.55*uSpeed;
        vec3 q=vec3(cos(ang)*2.6,sin(ang)*2.6,zz);
        float w=fbm(q*0.7+vec3(0.,0.,t*0.04));
        float cl=fbm(q*1.1+w*2.2+uShift);
        float f1=pow(1.-abs(sin(ang*7.+w*6.28+z*0.004-t*0.2)),14.);
        float f2=pow(1.-abs(sin(ang*13.-w*9.+z*0.006+t*0.3)),18.);
        float ring=pow(.5+.5*sin(z*0.07-t*uSpeed*2.6+w*3.),26.);
        float arc=pow(1.-abs(sin(ang*3.+cl*4.+t*0.1)),30.);
        vec3 col=mix(uA,uB,smoothstep(.25,.85,cl));
        col+=uC*(f1*1.5+f2*0.9)*(0.4+cl);
        col+=uHot*ring*(.3+cl*.6);
        col+=uHot*pow(cl,5.)*0.9+uC*arc*0.3;
        if(uHue>0.5) col=hueRot(col,t*0.4+cl*3.);
        // temporal lightning bolt
        float da=abs(mod(ang-uBoltA+3.14159,6.28318)-3.14159+(fbm(vec3(z*0.04,t*4.,2.))-.5)*1.4);
        float bolt=smoothstep(.07,0.,da)*uFlash*5.;
        col+=vec3(0.7,0.85,1.)*(bolt+uFlash*0.35*(.4+f1+cl));
        // damaged: patchy flicker and dropouts
        float fl=mix(1.,h(vec3(floor(t*14.),floor(ang*3.),floor(z*0.02))),uDmg*0.55);
        col*=fl;
        float dist=length(cameraPosition-vW);
        col*=(uLayer>0.5?0.75:0.5);
        col=mix(col,uA*0.35,1.-exp(-dist*0.0035));
        float a=1.;
        if(uLayer>0.5){ a=clamp((f1+f2+arc*.5)*(.3+cl)+ring*.4,0.,1.)*.8; col*=1.2; }
        gl_FragColor=vec4(col,a);
        #include <tonemapping_fragment>
        #include <colorspace_fragment>
      }`,
  });
}

const streakVS = `
attribute vec3 aOff; attribute vec2 aSL;
uniform float uT,uSpeed,uRange; varying float vA; varying float vM;
void main(){
  float z=mod(aOff.z+uT*uSpeed*38.*aSL.x,uRange)-uRange*.5;
  float len=aSL.y*(1.+uSpeed*2.2);
  vec3 tang=vec3(-sin(aOff.x),cos(aOff.x),0.);
  vec3 c=vec3(cos(aOff.x)*aOff.y,sin(aOff.x)*aOff.y,z);
  vec3 p=c+vec3(0.,0.,1.)*position.y*len+tang*position.x*(0.07+aSL.y*0.004);
  vA=(1.-position.y)*smoothstep(0.,40.,uRange*.5-abs(z)); vM=fract(aOff.x*3.1+aSL.x*7.);
  gl_Position=projectionMatrix*viewMatrix*modelMatrix*vec4(p,1.);
}`;
const streakFS = `
uniform vec3 uC,uHot; varying float vA; varying float vM;
void main(){ vec3 col=mix(uC,uHot,vM); gl_FragColor=vec4(col*0.55,vA*.5); }`;

function planetTexture(kind, seed) {
  const r = rng(seed);
  return canvasTex(256, 256, (g, w, h) => {
    if (kind === 'galaxy') {
      g.fillStyle = '#000'; g.fillRect(0, 0, w, h);
      for (let i = 0; i < 1800; i++) {
        const a = r() * 12, d = Math.pow(r(), 0.7) * 120; const arm = a + d * 0.05;
        const x = 128 + Math.cos(arm) * d + (r() - 0.5) * 14, y = 128 + Math.sin(arm) * d * 0.6 + (r() - 0.5) * 10;
        g.fillStyle = `hsla(${200 + r() * 60},90%,${60 + r() * 30}%,${0.2 + r() * 0.5})`; g.fillRect(x, y, 2, 2);
      }
      const gr = g.createRadialGradient(128, 128, 0, 128, 128, 40); gr.addColorStop(0, 'rgba(255,240,200,.9)'); gr.addColorStop(1, 'rgba(0,0,0,0)');
      g.fillStyle = gr; g.fillRect(0, 0, w, h);
    } else if (kind === 'scene') {
      const hue = r() * 360; const gr = g.createLinearGradient(0, 0, 0, h); gr.addColorStop(0, `hsl(${hue},60%,20%)`); gr.addColorStop(1, `hsl(${hue + 40},80%,60%)`);
      g.fillStyle = gr; g.fillRect(0, 0, w, h); g.fillStyle = '#05050a';
      for (let x = 0; x < w; x += 14) { const bh = 30 + r() * 110; g.fillRect(x, h - bh, 12, bh); }
      g.fillStyle = 'rgba(255,230,160,.8)'; for (let i = 0; i < 60; i++) g.fillRect(r() * w, 120 + r() * 130, 2, 3);
      g.strokeStyle = 'rgba(255,255,255,.6)'; g.lineWidth = 4; g.strokeRect(2, 2, w - 4, h - 4);
    } else {
      const hue = r() * 360;
      for (let y = 0; y < h; y++) { g.fillStyle = `hsl(${hue + Math.sin(y * 0.05 + r()) * 25 + (kind === 'earth' ? 120 : 0)},${55 + r() * 20}%,${25 + Math.sin(y * 0.11 + seed) * 12 + r() * 6}%)`; g.fillRect(0, y, w, 1); }
      if (kind === 'earth') { g.fillStyle = 'rgba(40,160,70,.8)'; for (let i = 0; i < 14; i++) { g.beginPath(); g.ellipse(r() * w, r() * h, 20 + r() * 40, 10 + r() * 30, r() * 3, 0, 6.3); g.fill(); } g.fillStyle = 'rgba(255,255,255,.45)'; for (let i = 0; i < 20; i++) { g.beginPath(); g.ellipse(r() * w, r() * h, 20 + r() * 30, 4 + r() * 6, 0, 0, 6.3); g.fill(); } }
    }
  });
}

export class Vortex {
  constructor(scene) {
    this.group = new THREE.Group(); this.group.position.copy(VORTEX_ORIGIN); this.group.visible = false;
    scene.add(this.group);
    this.t = 0; this.speed = 1; this.mode = 'normal'; this.style = 0;
    this.cur = { a: new THREE.Color(), b: new THREE.Color(), c: new THREE.Color(), hot: new THREE.Color() };
    this.turb = 0.3; this.dmg = 0; this.hue = 0; this.flashV = 0; this.nextBolt = 2; this.shake = 0;

    const geo = new THREE.CylinderGeometry(42, 42, 900, 72, 1, true).rotateX(Math.PI / 2);
    this.m0 = tunnelMaterial(0); this.m1 = tunnelMaterial(1);
    this.group.add(new THREE.Mesh(geo, this.m0));
    const m = new THREE.Mesh(new THREE.CylinderGeometry(30, 30, 900, 48, 1, true).rotateX(Math.PI / 2), this.m1);
    this.group.add(m);

    // streaks
    const N = 1500, base = new THREE.PlaneGeometry(1, 1).translate(0, 0.5, 0);
    const ig = new THREE.InstancedBufferGeometry(); ig.index = base.index;
    ig.setAttribute('position', base.attributes.position);
    const off = new Float32Array(N * 3), sl = new Float32Array(N * 2), r = rng(77);
    for (let i = 0; i < N; i++) {
      off[i * 3] = r() * 6.283; off[i * 3 + 1] = 3 + Math.pow(r(), 0.6) * 36; off[i * 3 + 2] = r() * 800;
      sl[i * 2] = 0.5 + r() * 1.5; sl[i * 2 + 1] = 2 + Math.pow(r(), 2) * 22;
    }
    ig.setAttribute('aOff', new THREE.InstancedBufferAttribute(off, 3));
    ig.setAttribute('aSL', new THREE.InstancedBufferAttribute(sl, 2));
    ig.instanceCount = N;
    this.streakMat = new THREE.ShaderMaterial({
      vertexShader: streakVS, fragmentShader: streakFS, transparent: true, depthWrite: false, blending: THREE.AdditiveBlending, side: THREE.DoubleSide, fog: false,
      uniforms: { uT: { value: 0 }, uSpeed: { value: 1 }, uRange: { value: 800 }, uC: { value: new THREE.Color() }, uHot: { value: new THREE.Color() } },
    });
    const streaks = new THREE.Mesh(ig, this.streakMat); streaks.frustumCulled = false; this.group.add(streaks);

    // fragments of reality
    this.frags = [];
    const kinds = ['planet', 'earth', 'galaxy', 'scene', 'planet', 'galaxy', 'scene', 'earth'];
    kinds.forEach((k, i) => {
      const tex = planetTexture(k, 100 + i * 17);
      const mat = new THREE.MeshBasicMaterial({ map: tex, fog: false, transparent: k === 'galaxy', blending: k === 'galaxy' ? THREE.AdditiveBlending : THREE.NormalBlending, depthWrite: k !== 'galaxy', side: THREE.DoubleSide });
      const mesh = k === 'galaxy' || k === 'scene' ? new THREE.Mesh(new THREE.PlaneGeometry(1, 1), mat) : new THREE.Mesh(new THREE.SphereGeometry(0.5, 24, 16), mat);
      mesh.userData = { kind: k, active: false, spin: (r() - 0.5) };
      mesh.visible = false; this.group.add(mesh); this.frags.push(mesh);
    });
    this.fragTimer = 1;
    // impossible silhouettes
    this.sils = [];
    for (let i = 0; i < 3; i++) {
      const gm = [new THREE.TorusKnotGeometry(0.5, 0.12, 120, 8, 2 + i, 3), new THREE.TorusGeometry(0.5, 0.05, 6, 5, 4.2), new THREE.CylinderGeometry(0.02, 0.2, 2.5, 5)][i];
      const grp = new THREE.Group();
      grp.add(new THREE.Mesh(gm, new THREE.MeshBasicMaterial({ color: 0x03000a, fog: false })));
      grp.add(new THREE.LineSegments(new THREE.EdgesGeometry(gm, 30), new THREE.LineBasicMaterial({ color: 0x66ccff, fog: false })));
      grp.visible = false; grp.userData = { active: false }; this.group.add(grp); this.sils.push(grp);
    }
    this.silTimer = 12;
    this.setStyle(0);
  }

  setStyle(i) { this.style = i; }
  setMode(m) { this.mode = MODES[m] ? m : 'normal'; }
  flash() { this.flashV = 1; this.boltA = Math.random() * 6.28; this.m0.uniforms.uBoltA.value = this.m1.uniforms.uBoltA.value = this.boltA; }
  show(v) { this.group.visible = v; }

  update(dt, speed, turbOn = true) {
    if (!this.group.visible) return;
    this.t += dt;
    this.speed = damp(this.speed, speed, 1.2, dt);
    const M = MODES[this.mode], P = PALETTES[this.style] || PALETTES[0], tgt = M.mix || P;
    for (const k of ['a', 'b', 'c', 'hot']) {
      this.cur[k].r = damp(this.cur[k].r, tgt[k][0], 2, dt); this.cur[k].g = damp(this.cur[k].g, tgt[k][1], 2, dt); this.cur[k].b = damp(this.cur[k].b, tgt[k][2], 2, dt);
    }
    this.turb = damp(this.turb, turbOn ? M.turb : 0.1, 1.5, dt); this.dmg = damp(this.dmg, M.dmg, 2, dt); this.hue = damp(this.hue, M.hue, 1, dt);
    this.flashV = Math.max(0, this.flashV - dt * 2.6);
    this.nextBolt -= dt; if (this.nextBolt < 0) { this.flash(); this.nextBolt = (this.mode === 'emergency' ? 1 : 4) + Math.random() * 5; this.onBolt && this.onBolt(); }
    for (const m of [this.m0, this.m1]) {
      const u = m.uniforms; u.uT.value = this.t; u.uSpeed.value = this.speed; u.uFlash.value = this.flashV; u.uTurb.value = this.turb; u.uDmg.value = this.dmg; u.uHue.value = this.hue;
      u.uA.value.copy(this.cur.a); u.uB.value.copy(this.cur.b); u.uC.value.copy(this.cur.c); u.uHot.value.copy(this.cur.hot);
    }
    const su = this.streakMat.uniforms; su.uT.value = this.t; su.uSpeed.value = this.speed; su.uC.value.copy(this.cur.c); su.uHot.value.copy(this.cur.hot);

    // fragments
    this.fragTimer -= dt;
    if (this.fragTimer < 0) {
      const f = this.frags.find((x) => !x.userData.active);
      if (f) {
        const a = Math.random() * 6.283, rad = 20 + Math.random() * 14, s = 4 + Math.random() * 14;
        f.position.set(Math.cos(a) * rad, Math.sin(a) * rad, -420); f.scale.setScalar(s); f.userData.active = true; f.visible = true;
        f.lookAt(0, 0, f.position.z);
      }
      this.fragTimer = (1.2 + Math.random() * 3) / Math.max(0.4, this.speed);
    }
    for (const f of this.frags) if (f.userData.active) {
      f.position.z += dt * (60 + this.speed * 90); if (f.userData.kind !== 'galaxy' && f.userData.kind !== 'scene') f.rotation.y += dt * f.userData.spin;
      if (f.position.z > 430) { f.userData.active = false; f.visible = false; }
    }
    this.silTimer -= dt;
    if (this.silTimer < 0) {
      const s = this.sils.find((x) => !x.userData.active);
      if (s) { const a = Math.random() * 6.283; s.position.set(Math.cos(a) * 26, Math.sin(a) * 26, -430); s.scale.setScalar(14 + Math.random() * 18); s.userData.active = true; s.visible = true; s.userData.spin = Math.random() - 0.5; }
      this.silTimer = 14 + Math.random() * 16;
    }
    for (const s of this.sils) if (s.userData.active) {
      s.position.z += dt * (45 + this.speed * 70); s.rotation.x += dt * s.userData.spin; s.rotation.y += dt * 0.3;
      if (s.position.z > 440) { s.userData.active = false; s.visible = false; }
    }
  }
  // brightness the interior should mimic
  get lightColor() { return this.cur.c; }
}
