import * as THREE from 'three';
export { THREE };

export const clamp = (v, a = 0, b = 1) => Math.min(b, Math.max(a, v));
export const lerp = (a, b, t) => a + (b - a) * t;
export const damp = (cur, target, lambda, dt) => lerp(cur, target, 1 - Math.exp(-lambda * dt));
export const smooth = (a, b, x) => { const t = clamp((x - a) / (b - a)); return t * t * (3 - 2 * t); };

export function hashStr(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return h >>> 0;
}
export function rng(seed) {
  let a = seed >>> 0;
  const f = () => {
    a = (a + 0x6D2B79F5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  f.range = (lo, hi) => lo + (hi - lo) * f();
  f.int = (lo, hi) => Math.floor(lo + (hi - lo + 1) * f());
  f.pick = (arr) => arr[Math.floor(f() * arr.length)];
  return f;
}

export function canvasTex(w, h, draw, opts = {}) {
  const c = document.createElement('canvas');
  c.width = w; c.height = h;
  const g = c.getContext('2d');
  draw(g, w, h);
  const t = new THREE.CanvasTexture(c);
  t.colorSpace = THREE.SRGBColorSpace;
  t.anisotropy = 4;
  if (opts.repeat) { t.wrapS = t.wrapT = THREE.RepeatWrapping; }
  t.userData.canvas = c; t.userData.ctx = g;
  return t;
}

export const pad = (n, l = 2) => String(Math.abs(Math.trunc(n))).padStart(l, '0');
export function fmtYear(y) {
  if (y < 0) return Math.abs(y).toLocaleString('en-US') + ' BCE';
  return y.toLocaleString('en-US').replace(/,/g, y >= 10000 ? ',' : '') + ' CE';
}
export function fmtDate(d) {
  return `${fmtYear(d.year)} · ${pad(d.month)}/${pad(d.day)} · ${pad(d.hour)}:00`;
}

export function disposeTree(o) {
  o.traverse((n) => {
    if (n.geometry) n.geometry.dispose();
    if (n.material) {
      const ms = Array.isArray(n.material) ? n.material : [n.material];
      ms.forEach((m) => { if (!m.userData.keep) { for (const k in m) if (m[k] && m[k].isTexture && !m[k].userData.keep) m[k].dispose(); m.dispose(); } });
    }
  });
}

// Tiny event bus
const handlers = {};
export const bus = {
  on(e, f) { (handlers[e] ||= []).push(f); return () => { handlers[e] = handlers[e].filter((x) => x !== f); }; },
  emit(e, d) { (handlers[e] || []).slice().forEach((f) => { try { f(d); } catch (err) { console.error('bus', e, err); } }); },
};
