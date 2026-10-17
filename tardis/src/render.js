// Rendering helpers: sky dome, the seamless doorway portal (bigger-on-the-inside), the window cube-camera,
// the cloak haze capture and per-pass lighting configuration.
import { THREE } from './util.js';

export function makeSky() {
  const mat = new THREE.ShaderMaterial({
    side: THREE.BackSide, depthWrite: false, fog: false,
    uniforms: { uTop: { value: new THREE.Color() }, uMid: { value: new THREE.Color() }, uBot: { value: new THREE.Color() }, uSunDir: { value: new THREE.Vector3(0, 1, 0) }, uSunCol: { value: new THREE.Color() }, uStars: { value: 0 }, uAurora: { value: 0 }, uGlow: { value: 0 }, uSun: { value: 1 }, uMoon: { value: 0 }, uT: { value: 0 } },
    vertexShader: 'varying vec3 vD; void main(){ vD=position; gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.); gl_Position.z=gl_Position.w*0.9999; }',
    fragmentShader: `uniform vec3 uTop,uMid,uBot,uSunDir,uSunCol; uniform float uStars,uAurora,uGlow,uSun,uMoon,uT; varying vec3 vD;
      float hh(vec3 p){ p=fract(p*0.3183099+.1); p*=17.; return fract(p.x*p.y*p.z*(p.x+p.y+p.z)); }
      float vn(vec3 x){ vec3 i=floor(x), f=fract(x); f=f*f*(3.-2.*f); return mix(mix(mix(hh(i),hh(i+vec3(1,0,0)),f.x),mix(hh(i+vec3(0,1,0)),hh(i+vec3(1,1,0)),f.x),f.y),mix(mix(hh(i+vec3(0,0,1)),hh(i+vec3(1,0,1)),f.x),mix(hh(i+vec3(0,1,1)),hh(i+vec3(1,1,1)),f.x),f.y),f.z); }
      void main(){
        vec3 d=normalize(vD); float h=d.y;
        vec3 col=mix(uBot,uMid,smoothstep(0.,0.3,h)); col=mix(col,uTop,smoothstep(0.25,0.95,h));
        if(h<0.) col=mix(uBot,uBot*0.45,clamp(-h*2.,0.,1.));
        float s=max(dot(d,uSunDir),0.);
        col+=uSunCol*(pow(s,900.)*4.*uSun+pow(s,10.)*0.22*uSun+pow(s,4.)*0.06*uSun);
        if(uMoon>0.5){ col+=vec3(.75,.8,.95)*smoothstep(0.9993,0.9997,dot(d,uSunDir))*1.5; col+=vec3(.2,.25,.4)*pow(s,40.)*0.5; }
        float star=step(0.9972,hh(floor(d*260.)))*(0.5+0.5*sin(uT*3.+hh(floor(d*260.))*40.));
        star+=step(0.9995,hh(floor(d*90.+3.)))*1.4;
        col+=vec3(star)*uStars*smoothstep(-0.05,0.25,h);
        if(uAurora>0.01){ float a=sin(d.x*7.+uT*0.15+sin(d.z*4.+uT*0.1)*2.)*0.5+0.5; col+=mix(vec3(.2,1.,.6),vec3(.7,.3,1.),a)*pow(a,3.)*smoothstep(0.15,0.55,h)*(1.-smoothstep(0.6,1.,h))*uAurora*0.9; }
        col+=uBot*uGlow*pow(1.-abs(h),6.);
        gl_FragColor=vec4(col,1.);
        #include <tonemapping_fragment>
        #include <colorspace_fragment>
      }`,
  });
  const m = new THREE.Mesh(new THREE.SphereGeometry(900, 32, 16), mat); m.frustumCulled = false; m.renderOrder = -100; return m;
}
export function applySky(sky, env, t) {
  const u = sky.material.uniforms;
  u.uTop.value.copy(env.skyTop); u.uMid.value.copy(env.skyMid); u.uBot.value.copy(env.skyBot); u.uSunDir.value.copy(env.sunDir); u.uSunCol.value.copy(env.sunColor);
  u.uStars.value = env.stars; u.uAurora.value = env.aurora; u.uGlow.value = env.glow; u.uSun.value = env.sunDisc; u.uMoon.value = env.moon; u.uT.value = t;
}

// A vertical "portal" quad that samples the screen-space render target of the other side.
const portalMat = (rt) => new THREE.ShaderMaterial({
  uniforms: { uTex: { value: rt.texture }, uRes: { value: new THREE.Vector2(1, 1) } }, fog: false,
  vertexShader: 'void main(){ gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.); }',
  fragmentShader: `uniform sampler2D uTex; uniform vec2 uRes; void main(){ gl_FragColor=vec4(texture2D(uTex,gl_FragCoord.xy/uRes).rgb,1.);
        #include <tonemapping_fragment>
    #include <colorspace_fragment> }`,
});

export class Portals {
  constructor(renderer, w, h, samples) {
    this.renderer = renderer;
    this.rt = new THREE.WebGLRenderTarget(w, h, { type: THREE.HalfFloatType, samples, depthBuffer: true });
    this.mat = portalMat(this.rt);
    const geo = new THREE.PlaneGeometry(0.9, 2.05).translate(0, 1.175, 0);
    this.extQuad = new THREE.Mesh(geo, this.mat); this.intQuad = new THREE.Mesh(geo, this.mat);
    this.extQuad.frustumCulled = this.intQuad.frustumCulled = false; this.extQuad.renderOrder = this.intQuad.renderOrder = 2;
    this.cam = new THREE.PerspectiveCamera(); this.cam.matrixAutoUpdate = false; this.cam.matrixWorldAutoUpdate = false;
    this.R180 = new THREE.Matrix4().makeRotationY(Math.PI);
    this.E = new THREE.Matrix4(); this.N = new THREE.Matrix4(); this.Ei = new THREE.Matrix4(); this.Ni = new THREE.Matrix4(); this.Me2i = new THREE.Matrix4(); this.Mi2e = new THREE.Matrix4();
    this.frustum = new THREE.Frustum(); this.pv = new THREE.Matrix4(); this.clip = new THREE.Vector4(); this.q = new THREE.Vector4(); this.pl = new THREE.Plane();
    this.size = new THREE.Vector2();
  }
  resize(w, h) { this.rt.setSize(w, h); this.mat.uniforms.uRes.value.set(w, h); }
  frames(tardis, interior) {
    this.E.copy(tardis.doorAnchor.matrixWorld); this.N.copy(interior.doorAnchor.matrixWorld);
    this.Ei.copy(this.E).invert(); this.Ni.copy(this.N).invert();
    this.Me2i.copy(this.N).multiply(this.R180).multiply(this.Ei); this.Mi2e.copy(this.E).multiply(this.R180).multiply(this.Ni);
  }
  inView(camera, quad) { this.pv.multiplyMatrices(camera.projectionMatrix, camera.matrixWorldInverse); this.frustum.setFromProjectionMatrix(this.pv); quad.updateMatrixWorld(true); const s = new THREE.Sphere(); new THREE.Box3().setFromObject(quad).getBoundingSphere(s); return this.frustum.intersectsSphere(s); }
  // render the other side of the doorway into this.rt. fromInside: player is in the TARDIS (so we render the outside).
  render(camera, scene, fromInside, passCfg) {
    const M = fromInside ? this.Mi2e : this.Me2i, T = fromInside ? this.E : this.N;
    const cam = this.cam; cam.matrixWorld.copy(M).multiply(camera.matrixWorld); cam.matrixWorldInverse.copy(cam.matrixWorld).invert();
    cam.projectionMatrix.copy(camera.projectionMatrix); cam.projectionMatrixInverse.copy(camera.projectionMatrixInverse); cam.near = camera.near; cam.far = camera.far;
    // oblique near-plane: clip everything between the virtual camera and the destination doorway plane
    const origin = new THREE.Vector3().setFromMatrixPosition(T), normal = new THREE.Vector3(0, 0, 1).transformDirection(T);
    this.pl.setFromNormalAndCoplanarPoint(normal, origin).applyMatrix4(cam.matrixWorldInverse);
    this.clip.set(this.pl.normal.x, this.pl.normal.y, this.pl.normal.z, this.pl.constant);
    const p = cam.projectionMatrix.elements;
    this.q.x = (Math.sign(this.clip.x) + p[8]) / p[0]; this.q.y = (Math.sign(this.clip.y) + p[9]) / p[5]; this.q.z = -1; this.q.w = (1 + p[10]) / p[14];
    this.clip.multiplyScalar(2 / this.clip.dot(this.q));
    p[2] = this.clip.x; p[6] = this.clip.y; p[10] = this.clip.z + 1 - 0.003; p[14] = this.clip.w;
    const r = this.renderer, prev = r.getRenderTarget();
    passCfg(cam); r.setRenderTarget(this.rt); r.clear(); r.render(scene, cam); r.setRenderTarget(prev);
  }
}
