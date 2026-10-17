// Single shared game state + persistence. Every system reads/writes G; the save system serialises it.
import { bus } from './util.js';

const KEY = 'tardis-sim.save.v1';

export const SYSTEMS = ['power', 'navStability', 'temporalIntegrity', 'dimAlignment', 'cloakIntegrity', 'hull', 'consoleCondition', 'lifeSupport', 'interiorStability'];
export const SYSTEM_LABEL = {
  power: 'Power', navStability: 'Navigation', temporalIntegrity: 'Temporal integrity', dimAlignment: 'Dimensional alignment',
  cloakIntegrity: 'Cloak', hull: 'Exterior hull', consoleCondition: 'Console', lifeSupport: 'Life support', interiorStability: 'Interior stability',
};

export const ROOM_DEFS = [
  { id: 'secondary', name: 'Secondary Control Room', color: 0xffa040, start: true },
  { id: 'library', name: 'Library', color: 0xd9a35b, start: true },
  { id: 'navroom', name: 'Navigation & Hologram Room', color: 0x55c8ff, start: true },
  { id: 'engineering', name: 'Engineering Bay', color: 0xff7a3a, start: true },
  { id: 'cloakroom', name: 'Cloaking & Stealth Control', color: 0x9a7aff, hint: 'Engage the cloak once.' },
  { id: 'armory', name: 'Armory', color: 0xff5566, hint: 'Survive an emergency.' },
  { id: 'greenhouse', name: 'Botanical Greenhouse', color: 0x66ff9a, hint: 'Visit two different worlds.' },
  { id: 'observation', name: 'Observation Deck', color: 0x6a8cff, start: true },
  { id: 'pool', name: 'Swimming Pool', color: 0x40e0ff, hint: 'Visit three different worlds.' },
  { id: 'bedroom', name: 'Quarters', color: 0xffb3d0, hint: 'Complete five journeys.' },
  { id: 'workshop', name: 'Workshop', color: 0xffd060, hint: 'Recover an artifact.' },
  { id: 'corridor', name: 'Infinite Corridor', color: 0xcfcfff, hint: 'Visit another dimension.' },
  { id: 'companion', name: 'Companion Quarters', color: 0xff9060, hint: 'Visit two dimensions.' },
  { id: 'escape', name: 'Emergency Escape Chamber', color: 0xff3030, hint: 'Make an emergency landing.' },
  { id: 'sealed', name: 'The Room That Should Not Exist', color: 0xffffff, hint: 'Three secrets open it.' },
  { id: 'turbulence', name: '???', color: 0xff40ff, hint: 'Appears only during dimensional turbulence.' },
];

export const CLOAK_MODES = [
  { id: 1, name: 'Visual camouflage', start: true },
  { id: 2, name: 'Optical invisibility', start: true },
  { id: 3, name: 'Environmental disguise', unlock: 'cloak3' },
  { id: 4, name: 'Temporal concealment', unlock: 'cloak4' },
  { id: 5, name: 'Dimensional phase cloak', unlock: 'cloak5' },
  { id: 6, name: 'Emergency concealment', start: true, auto: true },
];
export const MAT_STYLES = ['Classic fade', 'Electric blue phase-in', 'Dimensional fracture', 'Holographic assembly', 'Unstable emergency'];

const fresh = () => ({
  mode: 'immersive',
  ship: {
    power: 100, navStability: 100, temporalIntegrity: 100, dimAlignment: 100, cloakIntegrity: 100,
    hull: 100, consoleCondition: 100, lifeSupport: 100, interiorStability: 100,
    phase: 'landed', rotor: false, lock: true, throttle: 0, stabilizers: 0.5, shields: false,
    doors: { left: 0, right: 0, locked: true }, tardisKeyHeld: false,
    cloak: { on: false, mode: 1 },
    followMode: false, scanner: false, telepathic: 0,
    experimental: false, guard: false, destLocked: false, hiddenFlight: false, powerMode: 1, defense: false,
  },
  dest: { worldId: 'earth_london', year: 1888, month: 10, day: 12, hour: 21, dim: [0, 0, 0], name: 'Victorian London', danger: 1, stability: 0.9 },
  loc: { worldId: 'earth_london', year: 1888, month: 10, day: 12, hour: 21, dim: [0, 0, 0] },
  flight: { t: 0, duration: 0, alignment: 0, drift: 0, speed: 0, mode: 'normal', stabilized: false, auto: false, emergency: false },
  player: { inside: true, pos: [0, 1.0, 6], yaw: 0, pitch: 0 },
  disc: {
    dests: {}, dims: {}, rooms: {}, secrets: {}, unlocks: {}, favorites: {}, lifeforms: {}, landmarks: {}, anomalies: {}, artifacts: {},
    journeys: 0, worldsVisited: {}, emergencies: 0, telepathicUses: 0, logs: [], seq: [], flags: {},
  },
  visits: {},
  custom: {
    paint: 0, condition: 0, lamp: 0, sign: 0, door: 0, matStyle: 0, cloakFx: 0, skin: 0,
    theme: 'classic', layout: 0, display: 0, rotor: 0, light: 1, startup: 0,
  },
  settings: {
    quality: 'medium', camFx: 'full', cinematic: false, turbulence: true, doorSafe: true, vortexStyle: 0, engine: 0,
    automation: 'manual', events: 'normal', volume: 0.7, music: false, autoEmergencyCloak: true, fov: 75, invertY: false, sens: 1,
  },
  clockDay: 0,
});

export const G = fresh();
for (const r of ROOM_DEFS) if (r.start) G.disc.rooms[r.id] = true;

let lastSave = 0;
export function saveGame(reason = '') {
  try {
    const snap = JSON.parse(JSON.stringify(G));
    // Saving during a transition is made safe by storing the state that the transition resolves to.
    if (snap.ship.phase === 'dematerializing') snap.ship.phase = 'vortex';
    if (snap.ship.phase === 'materializing') snap.ship.phase = 'landed';
    snap.savedAt = Date.now();
    localStorage.setItem(KEY, JSON.stringify(snap));
    lastSave = performance.now();
    bus.emit('saved', reason);
    return true;
  } catch (e) { console.warn('save failed', e); return false; }
}
export function maybeAutosave() { if (performance.now() - lastSave > 12000) saveGame('auto'); }

function merge(dst, src) {
  for (const k in src) {
    if (src[k] && typeof src[k] === 'object' && !Array.isArray(src[k]) && dst[k] && typeof dst[k] === 'object' && !Array.isArray(dst[k])) merge(dst[k], src[k]);
    else dst[k] = src[k];
  }
}
export function loadGame() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return false;
    const snap = JSON.parse(raw);
    merge(G, snap);
    if (!['landed', 'vortex'].includes(G.ship.phase)) G.ship.phase = 'landed';
    return true;
  } catch (e) { console.warn('load failed, starting fresh', e); return false; }
}
export function hasSave() { try { return !!localStorage.getItem(KEY); } catch { return false; } }
export function resetGame() {
  try { localStorage.removeItem(KEY); } catch { /* ignore */ }
  const f = fresh();
  for (const r of ROOM_DEFS) if (r.start) f.disc.rooms[r.id] = true;
  for (const k of Object.keys(G)) delete G[k];
  Object.assign(G, f);
}

export function log(msg, kind = 'ship') {
  bus.emit('msg', { msg, kind });
  G.disc.logs.push({ t: Date.now(), msg, kind });
  if (G.disc.logs.length > 80) G.disc.logs.shift();
}

export function discover(cat, id, extra = true) {
  if (G.disc[cat][id]) return false;
  G.disc[cat][id] = extra;
  bus.emit('discovered', { cat, id });
  return true;
}
export function unlockRoom(id, silent) {
  if (G.disc.rooms[id]) return;
  G.disc.rooms[id] = true;
  const r = ROOM_DEFS.find((x) => x.id === id);
  bus.emit('roomUnlocked', id);
  if (!silent) log(`A door has opened somewhere in the ship: ${r ? r.name : id}.`, 'ship');
}
