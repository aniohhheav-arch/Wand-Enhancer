// Ship logic: actions (shared by the physical console and the terminal UI), flight state machine,
// navigation / destinations, cloak, systems, emergencies, progression & secrets.
import { G, SYSTEMS, SYSTEM_LABEL, CLOAK_MODES, MAT_STYLES, log, discover, unlockRoom, saveGame } from './state.js';
import { bus, clamp, fmtDate, hashStr, rng, pad } from './util.js';
import { audio } from './audio.js';
import { WORLDS, DESTS, dimensionByKey, resolveDest, destKey, spawnProceduralDimension } from './worlds.js';

const S = () => G.ship;
const sandbox = () => G.mode === 'sandbox';

export const rt = {          // runtime-only state (not saved)
  events: [], alarm: null, hooks: {}, suggestions: [], msgTimer: 60, eventTimer: 30, cinematicLook: 0,
  doorOpenVortex: 0, rescue: 0, scanPulse: 0, scanResult: null, seqBusy: false, shake: 0, repairHold: 0, flicker: 0,
  stabHold: 0, kickTimer: 0, dematT: 0, matT: 0, forcedLanding: false, tardisHere: true, overrideFx: 0,
};

export function say(text) { log(text, 'ship'); audio.whisper(); }
const note = (text) => log(text, 'sys');

// ------------------------------------------------------------------ destination helpers
export function destInfo(d = G.dest) {
  const w = WORLDS[d.worldId] || WORLDS.earth_london;
  const far = Math.abs(d.year) > 2_000_000 ? 1 : 0;
  const danger = clamp(Math.round((w.danger || 0) + far + (d.extraDanger || 0)), 0, 5);
  const stability = clamp((w.stability ?? 0.9) - far * 0.12 - (100 - S().navStability) / 400, 0.05, 1);
  return { world: w, danger, stability };
}

function refreshDest() {
  const d = G.dest, r = resolveDest(d);
  if (r.worldId !== d.worldId) d.worldId = r.worldId;
  const info = destInfo(d);
  d.danger = info.danger; d.stability = info.stability;
  if (!d.preset) d.name = `${info.world.name} · ${fmtDate(d)}`;
  bus.emit('dest', d);
}

export const A = {
  // ---------------------------------------------------------------- destination & navigation
  setDestination(src, quiet) {
    if (S().destLocked && !sandbox()) { audio.click('deny'); note('Coordinates are locked. Unlock them first.'); return false; }
    const d = { ...src, dim: src.dim ? [...src.dim] : [0, 0, 0] };
    if (!d.month) d.month = 6; if (!d.day) d.day = 15; if (d.hour == null) d.hour = 12;
    Object.assign(G.dest, d); G.dest.preset = !!src.name; if (src.name) G.dest.name = src.name;
    refreshDest();
    if (!quiet) { audio.click('ok'); }
    return true;
  },
  setDestField(k, v) {
    if (S().destLocked && !sandbox()) { audio.click('deny'); return; }
    const d = G.dest;
    if (k === 'year') d.year = Math.round(clamp(v, -4.6e9, 5e9));
    else if (k === 'month') d.month = Math.round(clamp(v, 1, 12));
    else if (k === 'day') d.day = Math.round(clamp(v, 1, 28));
    else if (k === 'hour') d.hour = ((Math.round(v) % 24) + 24) % 24;
    else if (k === 'world') { d.worldId = v; d.dim = WORLDS[v]?.kind === 'dimension' ? [...WORLDS[v].dimKey] : [0, 0, 0]; }
    d.preset = false; refreshDest(); audio.click('knob');
  },
  shiftYear(delta) { this.setDestField('year', G.dest.year + delta); },
  setDim(i, v) {
    if (S().destLocked && !sandbox()) { audio.click('deny'); return; }
    G.dest.dim[i] = ((Math.round(v) % 10) + 10) % 10; G.dest.preset = false;
    refreshDest(); audio.click('knob');
    const dim = dimensionByKey(G.dest.dim);
    if (dim) { G.dest.worldId = dim.id; if (discover('dims', dim.id)) { say(`Coordinates accepted. A new reality opens: ${dim.name}.`); bus.emit('discoveredDim', dim.id); } refreshDest(); }
  },
  cycleDim(i, dir = 1) { this.setDim(i, G.dest.dim[i] + dir); },
  randomDestination() {
    if (S().destLocked && !sandbox()) { audio.click('deny'); return; }
    const r = rng((Date.now() ^ 0x9e3779b9) >>> 0);
    const pool = DESTS.filter((d) => !d.secret || G.disc.secrets.unlisted);
    const pick = r.pick(pool);
    const hit = r() < 0.18 && Object.keys(WORLDS).filter((k) => WORLDS[k].kind === 'dimension');
    if (hit) {
      const id = r.pick(hit); const w = WORLDS[id];
      this.setDestination({ worldId: id, year: 0, name: `Random: ${w.name}`, dim: w.dimKey || [0, 0, 0], randomHit: true }, true);
      if (discover('dims', id)) say(`This was not on any chart. ${w.name}.`);
    } else {
      const yr = pick.worldId.startsWith('earth') && r() < 0.5 ? Math.round(pick.year + r.range(-300, 300)) : pick.year;
      this.setDestination({ ...pick, year: yr, name: `Random: ${pick.name}`, month: r.int(1, 12), day: r.int(1, 28), hour: r.int(0, 23) }, true);
    }
    audio.click('big'); bus.emit('randomDest');
  },
  toggleDestLock() { S().destLocked = !S().destLocked; audio.click('switch'); note(S().destLocked ? 'Coordinates locked.' : 'Coordinates unlocked.'); this.resolveEvent('movingdest'); },
  setFavorite(key, v) { if (v) G.disc.favorites[key] = true; else delete G.disc.favorites[key]; },
  telepathic() {
    if (!sandbox() && S().power < 8) { audio.click('deny'); return; }
    G.disc.telepathicUses++; audio.chime(2);
    const r = rng(Date.now() >>> 0), out = [];
    const undisc = Object.values(WORLDS).filter((w) => w.kind === 'dimension' && !G.disc.dims[w.id]);
    if (undisc.length) { const w = r.pick(undisc); out.push({ kind: 'lead', text: `${w.name} — ${w.hint}`, worldId: w.id, dim: w.dimKey, coordsHint: w.dimKey.map((v, i) => (i === (G.disc.telepathicUses % 3) ? v : '?')).join(' · ') }); }
    const fresh = DESTS.filter((d) => !G.disc.dests[destKey(d)] && !d.secret);
    if (fresh.length) { const d = r.pick(fresh); out.push({ kind: 'suggest', text: 'Unvisited: ' + d.name, dest: d }); }
    const risky = DESTS.filter((d) => destInfo(d).danger >= 4); if (risky.length) { const d = r.pick(risky); out.push({ kind: 'warn', text: `Warning — ${d.name}: the timeline there is unstable.`, dest: d }); }
    if (G.disc.telepathicUses >= 5 && !G.disc.secrets.unlisted) out.push({ kind: 'secret', text: 'Where the ships go to die. (An uncharted signal.)', dest: DESTS.find((d) => d.secret) });
    rt.suggestions = out; bus.emit('suggestions', out);
    if (G.disc.telepathicUses === 1) say('I can show you places you have not thought to look.');
    return out;
  },

  // ---------------------------------------------------------------- flight sequence controls
  toggleRotor(v) {
    const s = S(); const nv = v ?? !s.rotor;
    if (nv && !sandbox() && s.power < 5) { audio.click('deny'); note('Insufficient power to start the time rotor.'); return; }
    s.rotor = nv; audio.click('big'); if (nv) audio.engineStart();
    note(nv ? 'Time rotor spinning up.' : 'Time rotor stopped.');
    const ev = rt.events.find((e) => e.id === 'rotor');
    if (ev) { if (!nv) ev.offAt = performance.now(); else if (ev.offAt && performance.now() - ev.offAt > 2800) this.resolveEvent('rotor'); else if (ev.offAt) note('Wait a few more seconds before restarting the rotor.'); }
  },
  releaseLock(v) { const s = S(); s.lock = v ?? !s.lock; audio.click('lever'); note(s.lock ? 'Temporal lock engaged.' : 'Temporal lock released.'); },
  setThrottle(v) { S().throttle = clamp(v); audio.click('lever'); },
  setStabilizers(v) { S().stabilizers = clamp(v); audio.click('knob'); },
  toggleShields(v) { const s = S(); if (!sandbox() && s.power < 10 && !s.shields) { audio.click('deny'); return; } s.shields = v ?? !s.shields; audio.click('switch'); note(s.shields ? 'Shields raised.' : 'Shields lowered.'); },
  toggleExperimental() {
    const s = S();
    if (!s.guard) { s.guard = true; audio.click('switch'); note('Guard lifted. This switch is not safe.'); return; }
    s.experimental = !s.experimental; audio.click('big');
    note(s.experimental ? 'EXPERIMENTAL FLIGHT ARMED. The next flight will be unpredictable.' : 'Experimental flight disarmed.');
    if (s.experimental) say('Hold on to something.');
    setTimeout(() => { if (!s.experimental) s.guard = false; }, 600);
  },
  hiddenFlight() { if (!G.disc.secrets.ancient) return; S().hiddenFlight = !S().hiddenFlight; audio.click('big'); say(S().hiddenFlight ? 'The old way. Do not look directly at it.' : 'Returning to the safe path.'); },

  canDemat() {
    const s = S(), why = [];
    if (s.phase !== 'landed') why.push('already in transit');
    if (!s.rotor) why.push('time rotor is off');
    if (s.lock) why.push('temporal lock is engaged');
    if (s.throttle < 0.5) why.push('flight controls are not engaged');
    if (!sandbox() && s.power < 12) why.push('insufficient power');
    if (!rt.playerInside) why.push('you are outside');
    const info = destInfo();
    if (!sandbox() && info.world.requires && !info.world.requires()) why.push('the ship refuses these coordinates');
    if (!sandbox() && info.danger >= 5 && !G.disc.secrets.ancient && !s.hiddenFlight) why.push('the ship refuses: the timeline is too dangerous');
    return { ok: why.length === 0, why };
  },
  dematerialize() {
    const c = this.canDemat();
    if (!c.ok) { audio.click('deny'); note('Cannot dematerialize: ' + c.why.join(', ') + '.'); return false; }
    const s = S(), info = destInfo();
    if (info.danger >= 4) say(info.danger >= 5 ? 'I do not like this.' : 'The timeline there frays. I will try.');
    if (s.doors.left > 0.1 || s.doors.right > 0.1) { note('Doors closing automatically for dematerialization.'); this.setDoors(0, 0); }
    s.doors.locked = true; s.phase = 'dematerializing'; rt.dematT = 0; audio.click('big'); audio.dematerialize();
    bus.emit('demat');
    return true;
  },
  materialize() {
    const s = S();
    if (s.phase !== 'vortex') { audio.click('deny'); return false; }
    if (!G.flight.stabilized && !G.flight.emergency && !sandbox()) { audio.click('deny'); note('Navigation not complete. Keep the stabilizers up and wait for lock.'); return false; }
    this.beginMaterialize(); return true;
  },
  beginMaterialize(forced) {
    const s = S(), f = G.flight;
    // resolve final destination, including unexpected arrivals from poor navigation
    let d = { ...G.dest, dim: [...G.dest.dim] };
    const r = rng(hashStr(JSON.stringify(d)) ^ (Date.now() & 0xffff));
    let surprise = null;
    if (!sandbox() && !f.hidden) {
      const navBad = s.navStability < 45 || forced, dimBad = s.dimAlignment < 35;
      if (dimBad && r() < 0.7) {
        const dims = Object.values(WORLDS).filter((w) => w.kind === 'dimension');
        const w = r.pick(dims); d.worldId = w.id; d.year = 0; surprise = `Dimensional drift. We have slipped into ${w.name}.`;
      } else if (navBad && r() < 0.8) {
        const dy = Math.round(r.range(-40, 40) * (1 + (100 - s.navStability) / 30)); d.year += dy; d.hour = r.int(0, 23); surprise = `Unexpected materialization — off target by ${Math.abs(dy)} years.`;
      }
    }
    if (G.settings.autoEmergencyCloak && destInfo(d).danger >= 4 && !s.cloak.on && s.power > 15) { s.cloak.on = true; s.cloak.mode = 6; rt.autoCloak = true; note('Emergency concealment engaged automatically.'); }
    s.phase = 'materializing'; rt.matT = 0; f.final = d; rt.surprise = surprise; rt.forcedLanding = !!forced;
    audio.materialize(); bus.emit('materializeStart', d);
  },
  quickTravel() {
    if (rt.seqBusy) return;
    const s = S();
    if (s.phase !== 'landed') { audio.click('deny'); return; }
    if (!rt.playerInside) { audio.click('deny'); note('Return to the TARDIS first.'); return; }
    rt.seqBusy = true; G.flight.auto = true;
    const startup = [[0, 'Rotor'], [0.7, 'Lock'], [1.4, 'Flight'], [2.2, 'Go']];
    const speed = [1, 0.5, 1.6, 0][G.custom.startup] ?? 1;
    startup.forEach(([t, k]) => setTimeout(() => {
      if (k === 'Rotor') this.toggleRotor(true); else if (k === 'Lock') this.releaseLock(false); else if (k === 'Flight') { this.setThrottle(0.8); this.setStabilizers(0.85); }
      else { rt.seqBusy = false; if (!this.dematerialize()) G.flight.auto = false; }
    }, t * 1000 * speed));
  },

  // ---------------------------------------------------------------- doors
  setDoors(l, r) { const d = S().doors; d.left = l; d.right = r; },
  doorsBusy() { const p = S().phase; return p === 'dematerializing' || p === 'materializing'; },
  doorLabel() {
    const d = S().doors;
    if (d.locked && d.left < 0.05 && d.right < 0.05) return 'Locked — needs key';
    return (d.left > 0.5 || d.right > 0.5) ? 'Close doors' : 'Open doors';
  },
  exteriorDoor(side, o = {}) {
    const d = S().doors;
    if (this.doorsBusy()) { audio.click('deny'); return; }
    if (d.locked && d.left < 0.05 && d.right < 0.05) { audio.click('deny'); audio.knock(); note('The door is locked.'); return; }
    this.toggleDoors(o.shift ? side : null);
  },
  interiorDoor(side, o = {}) {
    const d = S().doors;
    if (this.doorsBusy()) { audio.click('deny'); return; }
    if (d.locked && d.left < 0.05 && d.right < 0.05) { d.locked = false; audio.lock(false); note('You unlock the doors from the inside.'); }
    this.toggleDoors(o.shift ? side : null);
  },
  toggleDoors(side) {
    const d = S().doors;
    if (this.doorsBusy()) { audio.click('deny'); note('The doors will not move during materialization.'); return; }
    if (d.locked && d.left < 0.05 && d.right < 0.05) { audio.click('deny'); note('Doors are locked.'); return; }
    if (side === 'l') d.left = d.left > 0.5 ? 0 : 1;
    else if (side === 'r') d.right = d.right > 0.5 ? 0 : 1;
    else { const open = d.left > 0.5 || d.right > 0.5; d.left = d.right = open ? 0 : 1; }
    audio.door(d.left > 0.5 || d.right > 0.5);
    if (d.left > 0.5 || d.right > 0.5) bus.emit('doorsOpened');
  },
  toggleLock() {
    const d = S().doors;
    if ((d.left > 0.05 || d.right > 0.05) && !d.locked) { note('Close the doors before locking.'); audio.click('deny'); return; }
    d.locked = !d.locked; audio.lock(d.locked); note(d.locked ? 'TARDIS locked.' : 'TARDIS unlocked.');
  },
  emergencyRelease() {
    const d = S().doors;
    if (this.doorsBusy()) { audio.click('deny'); return; }
    d.locked = false; d.left = d.right = 1; audio.door(true); audio.click('lever'); note('Emergency override: doors forced open.');
    bus.emit('doorsOpened');
  },
  knock() { audio.knock(); note('You knock. Something inside hums back.'); if (Math.random() < 0.3) setTimeout(() => say('Not now.'), 1200); },
  inspect() {
    const h = S().hull;
    note(h > 85 ? 'The paint is flawless. It feels almost warm.' : h > 50 ? 'Scorch marks along the lower panels. The ship is hurt.' : 'The panels are scarred and the lamp stutters. It needs repairs.');
  },

  // ---------------------------------------------------------------- cloak
  cloakModeUnlocked(m) { const def = CLOAK_MODES.find((x) => x.id === m); return sandbox() || def.start || G.disc.unlocks[def.unlock]; },
  toggleCloak(v) {
    const s = S(); const nv = v ?? !s.cloak.on;
    if (nv && !sandbox() && s.power < 10) { audio.click('deny'); note('Not enough power to cloak.'); return; }
    if (nv && !sandbox() && s.cloakIntegrity < 8) { audio.click('deny'); note('Cloak emitters are too damaged.'); return; }
    s.cloak.on = nv; audio.click('switch'); audio.tone(nv ? 200 : 400, 1.2, 'sine', 0.15, nv ? 800 : 200);
    note(nv ? `Cloak engaged: ${CLOAK_MODES.find((x) => x.id === s.cloak.mode).name}.` : 'Cloak released.');
    if (nv) unlockRoom('cloakroom');
    if (!nv) { this.resolveEvent('cloakfail'); }
  },
  setCloakMode(m) {
    if (!this.cloakModeUnlocked(m)) { audio.click('deny'); note('That cloaking mode has not been unlocked yet.'); return false; }
    S().cloak.mode = m; audio.click('knob');
    if (m === 6 && !S().cloak.on) note('Emergency concealment arms itself and engages automatically on dangerous arrivals.');
    return true;
  },
  cycleCloakMode(dir = 1) { let m = S().cloak.mode; for (let i = 0; i < 6; i++) { m = ((m - 1 + dir + 6) % 6) + 1; if (this.cloakModeUnlocked(m)) break; } this.setCloakMode(m); },
  // effective cloak quality, affected by integrity and the environment
  cloakQuality() {
    const w = (WORLDS[G.loc.worldId] || {}); const env = w.cloakMod ?? 1;
    return clamp((S().cloakIntegrity / 100) * env * (S().power < 10 ? 0.4 : 1), 0, 1);
  },

  // ---------------------------------------------------------------- scanner
  scan() {
    if (rt.scanPulse > 0) return;
    rt.scanPulse = 2.2; audio.scan(); bus.emit('scan');
  },

  // ---------------------------------------------------------------- repair & systems
  repair(sys, amt = 100) {
    if (sys === 'all') { SYSTEMS.forEach((k) => { S()[k] = Math.max(S()[k], k === 'power' ? Math.min(100, S()[k] + 40) : 100); }); }
    else if (SYSTEMS.includes(sys)) S()[sys] = clamp(S()[sys] + amt, 0, 100);
    audio.click('ok'); bus.emit('repaired', sys);
    // repairing relevant systems resolves matching events
    if (sys === 'cloakIntegrity' || sys === 'all') this.resolveEvent('cloakfail');
    if (sys === 'power' || sys === 'all') this.resolveEvent('powerloss');
    if (sys === 'navStability' || sys === 'all') this.resolveEvent('navfail');
    if (sys === 'dimAlignment' || sys === 'all') { this.resolveEvent('drift'); this.resolveEvent('overlap'); }
    if (sys === 'consoleCondition') rt.flicker = 0;
  },
  restartReactor() { const s = S(); s.power = Math.max(s.power, 45); this.resolveEvent('powerloss'); audio.engineStart(); note('Reactor restarted.'); },
  recalibrateNav() { const s = S(); s.navStability = Math.max(s.navStability, 80); audio.click('ok'); note('Navigation matrix recalibrated.'); this.resolveEvent('navfail'); },
  realignDimensions() { const s = S(); s.dimAlignment = Math.max(s.dimAlignment, 85); audio.click('ok'); note('Dimensional lattice realigned.'); this.resolveEvent('drift'); this.resolveEvent('overlap'); },
  override(n) {
    audio.click('switch'); const seq = G.disc.seq; seq.push(n); if (seq.length > 3) seq.shift();
    note(`Manual override ${n} thrown.`);
    if (seq.join() === '2,1,3' && discover('secrets', 'ancient')) {
      say('You found the old controls. I had forgotten them too.'); bus.emit('secret', 'ancient'); rt.overrideFx = 4; audio.chime(-5);
    }
  },

  // ---------------------------------------------------------------- remote
  recall() { if (rt.hooks.recall) rt.hooks.recall(); },
  toggleFollow() { S().followMode = !S().followMode; note(S().followMode ? 'TARDIS will follow you.' : 'Follow mode off.'); },

  // ---------------------------------------------------------------- sandbox
  sandboxSetSystem(k, v) { if (SYSTEMS.includes(k)) S()[k] = clamp(v, 0, 100); },
  sandboxTriggerEvent(id) { this.startEvent(id, true); },
  sandboxSpawnDimension() {
    const w = spawnProceduralDimension(Date.now() & 0xffff); discover('dims', w.id);
    this.setDestination({ worldId: w.id, year: 0, name: w.name, dim: w.dimKey }, true); say(`A new reality condenses: ${w.name}.`);
  },
  sandboxGo() { if (G.mode !== 'sandbox') return; if (S().phase === 'landed') { rt.hooks.sandboxTeleport && rt.hooks.sandboxTeleport({ ...G.dest }); } },

  // ---------------------------------------------------------------- emergencies
  startEvent(id, force) {
    const def = EVENTS[id]; if (!def || rt.events.find((e) => e.id === id)) return;
    if (!force && def.phases && !def.phases.includes(S().phase)) return;
    const ev = { id, t: 0, progress: 0, def };
    rt.events.push(ev); def.start && def.start(ev);
    audio.setAlarm(def.alarm); say(def.cryptic); note(`ALERT — ${def.name}. Cause: ${def.cause}`); note(`Fix: ${def.hint}`);
    bus.emit('alert', ev);
  },
  resolveEvent(id) {
    const i = rt.events.findIndex((e) => e.id === id); if (i < 0) return;
    const [ev] = rt.events.splice(i, 1); ev.def.end && ev.def.end(ev);
    note(`Resolved — ${ev.def.name}. ${ev.def.recover}`); audio.chime(4); G.disc.emergencies++;
    if (!rt.events.length) audio.setAlarm(null);
    bus.emit('resolved', ev);
  },
  activeEvents() { return rt.events; },
};

const EVENTS = {
  turbulence: {
    name: 'Temporal turbulence', phases: ['vortex'], alarm: 'temporal', cause: 'The ship has entered an eddy in the vortex.', hint: 'Slide the flight stabilizers to maximum and hold for a few seconds.',
    cryptic: 'The currents argue with each other.', recover: 'The vortex smooths out.',
    start() { rt.shake = 1; }, tick(dt, ev) { rt.shake = Math.max(rt.shake, 0.7); G.flight.drift += (Math.random() - 0.5) * dt * 2; S().hull = Math.max(0, S().hull - dt * 0.15 * (S().defense ? 0.4 : 1));
      if (S().stabilizers >= 0.99) ev.progress += dt / 5; else ev.progress = Math.max(0, ev.progress - dt * 0.4); return ev.progress >= 1; },
  },
  navfail: {
    name: 'Navigation failure', phases: ['vortex'], alarm: 'nav', cause: 'The navigation matrix has desynchronised.', hint: 'Press RECALIBRATE on the navigation panel (or in the terminal).',
    cryptic: 'Which way is forward?', recover: 'Navigation restored.', start() { S().navStability = Math.min(S().navStability, 22); },
  },
  drift: {
    name: 'Dimensional drift', phases: ['vortex'], alarm: 'temporal', cause: 'The dimensional lattice is slipping.', hint: 'Press REALIGN DIMENSIONS on the console emergency panel (or terminal).',
    cryptic: 'Not this one. Another one. Another.', recover: 'The lattice holds.', start() { S().dimAlignment = Math.min(S().dimAlignment, 25); },
  },
  powerloss: {
    name: 'Power loss', alarm: 'power', cause: 'The reactor stalled under load.', hint: 'Restart the reactor in the Engineering Bay, or hold REROUTE POWER on the emergency panel.',
    cryptic: 'Dark. It is only dark.', recover: 'Power restored.', start() { S().power = Math.min(S().power, 6); },
    tick(dt) { S().power = Math.min(S().power, 6); rt.flicker = 1; return false; },
  },
  cloakfail: {
    name: 'Cloak malfunction', alarm: 'nav', cause: 'Cloak emitters are out of phase.', hint: 'Drop the cloak and repair cloak emitters (Cloaking room terminal or console).',
    cryptic: 'I am seen. I am seen.', recover: 'Cloak emitters stable.', start() { S().cloakIntegrity = Math.min(S().cloakIntegrity, 25); },
  },
  rotor: {
    name: 'Damaged time rotor', alarm: 'red', cause: 'A rotor coupling has cracked.', hint: 'Switch the time rotor OFF, wait three seconds, then switch it ON again.',
    cryptic: 'Breathe out. Wait. Breathe in.', recover: 'Rotor stable.', start(ev) { ev.offAt = 0; },
  },
  overlap: {
    name: 'Unstable dimensional overlap', alarm: 'temporal', cause: 'Another reality is bleeding into this one.', hint: 'Realign dimensions from the emergency panel.',
    cryptic: 'Two skies. Which one is yours?', recover: 'The overlap fades.', start() { S().dimAlignment = Math.min(S().dimAlignment, 40); },
  },
  movingdest: {
    name: 'Moving destination', phases: ['landed', 'vortex'], alarm: 'nav', cause: 'The target moment is sliding through time.', hint: 'LOCK the destination coordinates on the navigation panel.',
    cryptic: 'It will not hold still. Neither will we.', recover: 'Coordinates fixed.',
    tick(dt) { if (S().destLocked) return true; if (Math.random() < dt * 0.6) { G.dest.year += Math.round((Math.random() - 0.4) * 6); bus.emit('dest', G.dest); } return false; },
  },
};

// ------------------------------------------------------------------ per-frame logic
export function updateShip(dt) {
  const s = S(), f = G.flight;
  const sb = sandbox();
  // power economy
  if (!sb) {
    let drain = 0;
    if (s.rotor) drain += 0.04; if (s.shields) drain += 0.25; if (s.cloak.on) drain += 0.2;
    if (s.phase === 'vortex') drain += 0.12;
    const pm = [0.55, 1, 1.7][s.powerMode ?? 1];
    drain *= pm;
    const regen = (s.phase === 'landed' && !rt.events.find((e) => e.id === 'powerloss')) ? 0.5 * [1.6, 1, 0.5][s.powerMode ?? 1] : 0;
    s.power = clamp(s.power + (regen - drain) * dt, 0, 100);
    if (s.power <= 0.5) { if (s.cloak.on) { s.cloak.on = false; note('Cloak failed: no power.'); } s.shields = false; if (s.rotor && s.phase === 'landed') s.rotor = false; if (!rt.events.find((e) => e.id === 'powerloss') && s.phase !== 'vortex') A.startEvent('powerloss'); }
    if (s.power <= 0.5 && s.phase === 'vortex' && !rt.forcedLanding) { say('Too far. Too fast. We land now.'); f.emergency = true; A.beginMaterialize(true); }
    // slow passive recovery while landed
    if (s.phase === 'landed') for (const k of ['navStability', 'temporalIntegrity', 'dimAlignment', 'lifeSupport', 'interiorStability', 'hull', 'consoleCondition']) if (s[k] < 100 && !rt.events.length) s[k] = Math.min(100, s[k] + dt * 0.08);
    if (s.cloak.on) s.cloakIntegrity = Math.max(0, s.cloakIntegrity - dt * (0.05 + (1 - A.cloakQuality()) * 0.3));
    else if (s.cloakIntegrity < 100 && !rt.events.find((e) => e.id === 'cloakfail')) s.cloakIntegrity = Math.min(100, s.cloakIntegrity + dt * 0.1);
    if (s.cloak.on && s.cloakIntegrity < 20 && Math.random() < dt * 0.05) A.startEvent('cloakfail');
  }
  if (rt.scanPulse > 0) rt.scanPulse -= dt;
  rt.overrideFx = Math.max(0, rt.overrideFx - dt);
  rt.shake = Math.max(0, rt.shake - dt * 0.8);

  // phases
  if (s.phase === 'dematerializing') {
    rt.dematT += dt; if (rt.dematT >= 5.5) {
      s.phase = 'vortex';
      const from = G.loc, to = G.dest;
      const yd = Math.abs(to.year - from.year), far = to.worldId !== from.worldId ? 6 : 0;
      f.t = 0; f.alignment = 0; f.drift = 0; f.stabilized = false; f.emergency = false; f.hidden = s.hiddenFlight; f.experimental = s.experimental;
      let dur = clamp(16 + Math.log10(1 + yd) * 3.2 + far, 16, 44);
      if (f.auto) dur *= 0.7; if (sb) dur = Math.min(dur, 14);
      f.duration = dur; f.mode = G.dest.worldId && WORLDS[G.dest.worldId].kind === 'dimension' ? 'dimensional' : 'normal';
      if (s.experimental) f.mode = 'emergency';
      bus.emit('enterVortex');
      say(['Between.', 'Hold still.', 'Time is a wide road.', 'Falling upward.'][Math.floor(Math.random() * 4)]);
    }
  } else if (s.phase === 'vortex') {
    f.t += dt;
    const hasEv = rt.events.length > 0;
    f.speed = clamp(s.throttle * 1.3 * [0.8, 1, 1.3][s.powerMode ?? 1] + (f.mode === 'emergency' ? 0.8 : 0.2) + (hasEv ? 0.3 : 0), 0.3, 3);
    // course drift: random walk countered by stabilizers
    const turb = G.settings.turbulence ? 1 : 0.2;
    f.drift += (Math.random() - 0.5) * dt * 1.3 * turb * (1.4 - s.stabilizers) + (s.navStability < 40 ? (Math.random() - 0.5) * dt : 0);
    f.drift *= 1 - dt * (0.25 + s.stabilizers * 0.9);
    f.drift = clamp(f.drift, -1, 1);
    const good = Math.abs(f.drift) < 0.45 && s.navStability > 20;
    const rate = (100 / f.duration) * (s.stabilizers >= 0.75 ? 1 : 0.4) * (good ? 1 : 0.15);
    if (!f.stabilized) f.alignment = Math.min(100, f.alignment + rate * dt);
    if (f.alignment >= 100 && !f.stabilized) {
      f.stabilized = true; audio.chime(); note('Navigation complete. Ship stabilised — materialize when ready.');
      if (f.auto || G.settings.automation !== 'manual') setTimeout(() => { if (S().phase === 'vortex') A.materialize(); }, 1500);
    }
    // semi/auto automation helps keep the course
    if (G.settings.automation === 'auto' || f.auto) { s.stabilizers = Math.max(s.stabilizers, 0.85); }
    rt.dematT = 0;
    // time-based vortex hazard and random events
    if (!sb && G.settings.events !== 'off' && !f.stabilized) {
      rt.eventTimer -= dt;
      if (rt.eventTimer <= 0) {
        const base = { low: 70, normal: 38, high: 18 }[G.settings.events] || 38;
        const k = G.mode === 'emergency' ? 0.35 : G.mode === 'exploration' ? 1.6 : G.mode === 'quick' ? 1.4 : 1;
        rt.eventTimer = base * k * (0.6 + Math.random() * 0.8);
        const info = destInfo(); const pool = ['turbulence', 'turbulence', 'navfail', 'drift'];
        if (s.cloak.on) pool.push('cloakfail'); if (info.danger >= 3 || G.mode === 'emergency') pool.push('rotor', 'powerloss');
        if (f.t > 4 && (G.mode !== 'quick' || Math.random() < 0.5)) A.startEvent(pool[Math.floor(Math.random() * pool.length)]);
      }
    }
    if (f.experimental && !rt.expEvt && f.t > 5) { rt.expEvt = true; A.startEvent(['turbulence', 'drift', 'navfail'][Math.floor(Math.random() * 3)], true); }
    // rare "something is wrong with this destination" cue
    if (WORLDS[G.dest.worldId]?.rare && !rt.rareCue && f.t > 6) { rt.rareCue = true; audio.bell(311); setTimeout(() => say('Something old is listening.'), 800); }
  } else if (s.phase === 'materializing') {
    rt.matT += dt;
    if (rt.matT >= 6) {
      s.phase = 'landed'; bus.emit('landed', f.final);
    }
  } else rt.expEvt = false;
  if (s.phase !== 'vortex') { rt.rareCue = false; f.speed = 0; }

  // event ticking
  for (const ev of rt.events.slice()) {
    ev.t += dt;
    if (ev.def.tick && ev.def.tick(dt, ev)) A.resolveEvent(ev.id);
  }
  rt.alarm = rt.events.length ? rt.events[0].def.alarm : null;

  // ship personality: sparse cryptic messages
  if (!sb) {
    rt.msgTimer -= dt;
    if (rt.msgTimer <= 0) {
      rt.msgTimer = 110 + Math.random() * 160;
      const m = ['The rotor remembers every place it has been.', 'Someone left a door open, far from here.', 'Not all rooms are rooms.', 'You are the first to ask.', 'I kept your seat warm.', 'Listen to the walls.'];
      if (s.phase === 'landed' || s.phase === 'vortex') say(m[Math.floor(Math.random() * m.length)]);
    }
  }
}

export function checkProgress() {
  const d = G.disc, s = S();
  const nWorlds = Object.keys(d.worldsVisited).length, nDims = Object.keys(d.dims).filter((k) => d.worldsVisited[k]).length;
  if (nWorlds >= 2) unlockRoom('greenhouse');
  if (nWorlds >= 3) unlockRoom('pool');
  if (d.journeys >= 5) unlockRoom('bedroom');
  if (Object.keys(d.artifacts).length >= 1) unlockRoom('workshop');
  if (nDims >= 1) unlockRoom('corridor');
  if (nDims >= 2) unlockRoom('companion');
  if (d.emergencies >= 1) unlockRoom('armory');
  if (d.flags.emergencyLanding) unlockRoom('escape');
  if (s.cloak.on) unlockRoom('cloakroom');
  if (Object.keys(d.secrets).length >= 3) { unlockRoom('sealed'); }
  if (d.journeys >= 3 && !d.flags.signalHinted) { d.flags.signalHinted = true; setTimeout(() => say('Something hums in the observation deck. It was not there before.'), 4000); }
}

export function startTurbulenceCorridor() {
  if (G.disc.rooms.turbulence) return;
  unlockRoom('turbulence', true); say('A corridor that was never built has opened. It will not stay.');
  setTimeout(() => { delete G.disc.rooms.turbulence; bus.emit('roomLocked', 'turbulence'); }, 70000);
}
