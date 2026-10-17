// HUD + menus + the keyboard-accessible terminal (every console function is reachable here without 3D interaction).
import { G, SYSTEMS, SYSTEM_LABEL, ROOM_DEFS, CLOAK_MODES, MAT_STYLES, saveGame, hasSave, resetGame, unlockRoom, log } from './state.js';
import { A, rt, destInfo, say } from './ship.js';
import { WORLDS, DESTS, destKey } from './worlds.js';
import { SKINS } from './tardis.js';
import { audio } from './audio.js';
import { bus, fmtDate, fmtYear, pad } from './util.js';
import { listDests } from './console.js';

const $ = (s) => document.querySelector(s);
function el(tag, props, ...kids) {
  const e = document.createElement(tag);
  for (const k in props || {}) { if (k === 'class') e.className = props[k]; else if (k.startsWith('on')) e.addEventListener(k.slice(2), props[k]); else if (k === 'html') e.innerHTML = props[k]; else if (props[k] !== false && props[k] != null) e.setAttribute(k, props[k] === true ? '' : props[k]); }
  for (const c of kids.flat()) if (c != null && c !== false) e.append(c.nodeType ? c : document.createTextNode(c));
  return e;
}
const sandbox = () => G.mode === 'sandbox';
const col = (v) => (v > 60 ? 'var(--ok)' : v > 30 ? 'var(--warn)' : 'var(--bad)');
const barEl = (v, c) => el('div', { class: 'bar2' }, el('i', { style: `width:${Math.max(0, Math.min(100, v))}%;background:${c || col(v)}` }));

export const MODES = {
  immersive: ['Immersive', 'Operate the console physically and fly manually.'], quick: ['Quick travel', 'Pick a destination; the ship flies itself.'],
  sandbox: ['Sandbox', 'Everything unlocked; free control of all systems.'], exploration: ['Exploration', 'Fewer emergencies, more hints and rare events.'], emergency: ['Emergency', 'Frequent failures. Diagnose and repair.'],
};
export function applyMode(m, silent) {
  G.mode = m; const s = G.settings;
  if (m === 'immersive') { s.automation = 'manual'; s.events = 'normal'; }
  if (m === 'quick') { s.automation = 'auto'; s.events = 'low'; }
  if (m === 'exploration') { s.automation = 'semi'; s.events = 'low'; }
  if (m === 'emergency') { s.automation = 'manual'; s.events = 'high'; }
  if (m === 'sandbox') { s.automation = 'semi'; s.events = 'off'; unlockAll(); G.ship.power = 100; }
  if (!silent) log(`Mode: ${MODES[m][0]}.`, 'sys');
  bus.emit('mode', m);
}
export function unlockAll() {
  ROOM_DEFS.forEach((r) => { if (r.id !== 'turbulence') unlockRoom(r.id, true); });
  ['cloak3', 'cloak4', 'cloak5', 'skin2', 'skin3', 'skin4', 'skin5'].forEach((k) => { G.disc.unlocks[k] = true; });
  Object.values(WORLDS).forEach((w) => { if (w.kind === 'dimension') G.disc.dims[w.id] = G.disc.dims[w.id] || true; });
  G.disc.secrets.unlisted = G.disc.secrets.unlisted || true;
}

// ------------------------------------------------------------------ HUD
export const UI = {
  panel: null, tab: 'nav', tabs: [], search: '', onClose: null, isOpen() { return !!this.panel; },
  init() {
    this.hud = { prompt: $('#prompt'), cross: $('#cross'), hold: $('#hold'), status: $('#status'), alert: $('#alert'), scanner: $('#scanner'), log: $('#log'), flash: $('#flash') };
    bus.on('msg', ({ msg, kind }) => this.toast(msg, kind));
    for (const e of ['dest', 'repaired', 'resolved', 'alert', 'discovered', 'suggestions', 'mode', 'lead']) bus.on(e, () => this.refresh());
    setInterval(() => this.refresh(), 1000);
    bus.on('ui', (p) => { if (p === 'terminal:nav') this.open('nav'); else this.open(p === 'status' ? 'ship' : p); });
  },
  toast(msg, kind = 'sys') {
    const t = el('div', { class: 'toast ' + (kind === 'ship' ? 'ship' : kind === 'warn' ? 'warn' : 'sys') }, msg); this.hud.log.append(t);
    while (this.hud.log.children.length > 5) this.hud.log.firstChild.remove(); setTimeout(() => t.remove(), 9500);
  },
  flash(color = '#fff', dur = 0.5) { const f = this.hud.flash; f.style.transition = 'none'; f.style.background = color; f.style.opacity = 0.9; requestAnimationFrame(() => { f.style.transition = `opacity ${dur}s`; f.style.opacity = 0; }); },
  setPrompt(text, hold, keyHint = 'E') {
    const p = this.hud.prompt;
    if (!text) { p.style.display = 'none'; this.hud.cross.classList.remove('hot'); this.hud.hold.style.display = 'none'; return; }
    p.style.display = 'block'; p.innerHTML = `<kbd>${keyHint}</kbd> ${text}`; this.hud.cross.classList.add('hot');
    this.hud.hold.style.display = hold > 0 ? 'block' : 'none'; this.hud.hold.firstChild.style.width = `${hold * 100}%`;
  },
  updateHud(extra) {
    const s = G.ship, d = G.dest, info = destInfo();
    const phase = { landed: 'LANDED', dematerializing: 'DEMATERIALIZING', vortex: 'IN THE VORTEX', materializing: 'MATERIALIZING' }[s.phase];
    const h = `<div class="t">${phase}</div><div class="row"><span>${(G.loc && WORLDS[G.loc.worldId]?.name) || ''}</span><span>${s.phase === 'landed' ? fmtYear(G.loc.year) : '—'}</span></div>
      <div class="row"><span>Power</span><span>${s.power.toFixed(0)}%</span></div><div class="bar"><i style="width:${s.power}%;background:${col(s.power)}"></i></div>
      <div class="row"><span>Dest</span><span>${(d.name || '').slice(0, 24)}</span></div><div class="row"><span>${fmtDate(d)}</span><span>danger ${info.danger}/5</span></div>
      <div class="row"><span>Doors ${s.doors.left > 0.5 || s.doors.right > 0.5 ? 'OPEN' : 'closed'}${s.doors.locked ? ' 🔒' : ''}</span><span>Cloak ${s.cloak.on ? 'ON' : 'off'}</span></div>
      ${extra || ''}<div class="row"><span style="color:var(--dim)">${MODES[G.mode][0]}</span><span style="color:var(--dim)">${s.shields ? 'shields up' : ''}</span></div>`;
    if (this._h !== h) { this.hud.status.innerHTML = h; this._h = h; }
    const a = rt.events[0];
    if (a) { this.hud.alert.style.display = 'block'; const k = a.def.name + a.id; if (this._a !== k) { this.hud.alert.innerHTML = `<b>⚠ ${a.def.name}</b><div>${a.def.cause}</div><div class="fix">Fix: ${a.def.hint}</div>`; this._a = k; } } else { this.hud.alert.style.display = 'none'; this._a = null; }
  },
  // ------------------------------------------------------------------ scanner overlay
  scanner(on, data) {
    const s = this.hud.scanner; s.style.display = on ? 'block' : 'none'; if (!on || !data) return;
    const w = data.world; const at = data.atmo;
    s.innerHTML = `<h4>SCANNER ${rt.scanPulse > 0 ? '— SCANNING…' : ''}</h4>
      <div><span class="s">Location</span> ${w ? w.def.name : 'Vortex'}</div>
      <div><span class="s">Atmosphere</span> N₂ ${at.N2}% O₂ ${at.O2}% Ar ${at.Ar}% other ${at.other}% · ${at.pressure} atm · ${at.temp}°C</div>
      <div class="hz"><span class="s">Hazards</span> ${at.hazards.length ? at.hazards.join(', ') : 'none detected'}</div>
      <div><span class="s">Temporal distortion</span> ${(data.temporal * 100).toFixed(0)}% · <span class="s">Dimensional instability</span> ${(data.instab * 100).toFixed(0)}%</div>
      <div><span class="s">TARDIS</span> ${data.tardis}</div>
      <div><span class="s">Energy signatures</span></div>${data.items.slice(0, 7).map((i) => `<div>· <span style="color:${i.hostile ? 'var(--bad)' : 'var(--ok)'}">${i.name}</span> <span class="s">${i.type} · ${i.dist.toFixed(0)}m</span></div>`).join('') || '<div class="s">none in range — press F to pulse</div>'}
      <div class="s" style="margin-top:4px">F: pulse scan · Fully analysed entries are saved to the Archive</div>`;
  },

  // ------------------------------------------------------------------ title
  title(onStart) {
    const c = $('#titleCard'); c.innerHTML = '';
    let mode = G.mode;
    const mb = () => Object.entries(MODES).map(([k, [n, d]]) => el('button', { class: k === mode ? 'on' : '', onclick: () => { mode = k; render(); } }, el('b', {}, n), el('small', {}, d)));
    const render = () => { modes.replaceChildren(...mb()); };
    const modes = el('div', { class: 'modes' });
    c.append(el('h1', {}, 'TARDIS'), el('p', { class: 'sub' }, 'A time-and-space machine you own. It is larger on the inside. It is not quite tame.'), el('h2', {}, 'Choose how you play'), modes, el('div', { class: 'row' },
      hasSave() ? el('button', { class: 'p', onclick: () => onStart('continue', mode) }, 'Continue') : null,
      el('button', { class: hasSave() ? '' : 'p', onclick: () => onStart('new', mode) }, hasSave() ? 'New game' : 'Begin'),
      el('span', { style: 'color:var(--dim)' }, 'Click the view to capture the mouse. Esc releases it.')),
    el('h2', {}, 'Controls'), this.helpTable());
    render();
  },
  helpTable() {
    return el('table', { class: 'help' }, ...[['WASD / arrows', 'Move'], ['Shift', 'Sprint'], ['Space', 'Jump / swim up'], ['E or left-click', 'Use what you are looking at (hold for repairs)'], ['Q or right-click', 'Alternate use (reverse a dial, single door leaf)'], ['Tab', 'Terminal — keyboard-accessible console'], ['J', 'Archive'], ['F', 'Scanner pulse'], ['C', 'Recall signal (summon the TARDIS to you)'], ['Esc', 'Menu / release mouse'], ['H', 'Hide HUD']].map(([a, b]) => el('tr', {}, el('td', {}, a), el('td', {}, b))));
  },

  // ------------------------------------------------------------------ panels
  open(tab) {
    this.tab = tab || this.tab; this.panel = $('#panel'); this.panel.classList.add('on'); document.exitPointerLock && document.exitPointerLock(); this.render(); bus.emit('uiopen');
  },
  close() { if (!this.panel) return; this.panel.classList.remove('on'); this.panel = null; bus.emit('uiclose'); },
  refresh() { if (!this.panel) return; const a = document.activeElement; if (a && this.panel.contains(a) && ['INPUT', 'SELECT'].includes(a.tagName)) return; this.render(); },
  render() {
    const card = $('#panelCard'); const st = card.scrollTop; card.innerHTML = '';
    const tabs = [['nav', 'Navigate'], ['ship', 'Ship'], ['archive', 'Archive'], ['custom', 'Customize'], ['workshop', 'Workshop'], ['journal', 'Journal'], ['help', 'Help']];
    if (sandbox() || G.mode === 'emergency') tabs.splice(4, 0, ['sandbox', sandbox() ? 'Sandbox' : 'Emergency']);
    card.append(el('div', { class: 'row' }, el('h1', { style: 'font-size:20px' }, 'TERMINAL'), el('span', { class: 'sub right' }, `${MODES[G.mode][0]} · ${G.ship.phase}`), el('button', { onclick: () => { saveGame('manual'); this.toast('Game saved.'); } }, 'Save'), el('button', { onclick: () => this.close() }, 'Close (Esc)')),
      el('div', { class: 'tabs', role: 'tablist' }, tabs.map(([k, n]) => el('button', { class: this.tab === k ? 'on' : '', role: 'tab', onclick: () => { this.tab = k; this.render(); } }, n))));
    const fn = { nav: this.navTab, ship: this.shipTab, archive: this.archiveTab, custom: this.customTab, workshop: this.workshopTab, journal: this.journalTab, help: () => this.helpTable(), sandbox: this.sandboxTab }[this.tab];
    card.append(fn ? fn.call(this) : el('div'));
    card.scrollTop = st;
  },

  // ---- Navigate
  navTab() {
    const d = G.dest, info = destInfo(), root = el('div', { class: 'col' });
    const flightOK = A.canDemat();
    root.append(
      el('div', { class: 'item' }, el('div', { class: 'n' }, d.name || 'Custom coordinates'), el('div', { class: 'd' }, `${info.world.name} · ${fmtDate(d)} · dim ${d.dim.join('·')}`),
        el('div', { class: 'row' }, el('span', { class: 'tag ' + (info.danger >= 4 ? 'bad' : '') }, `danger ${info.danger}/5`), el('span', { class: 'tag' }, `stability ${(info.stability * 100).toFixed(0)}%`), el('span', { class: 'tag' }, G.ship.destLocked ? 'locked' : 'unlocked')),
        el('div', { class: 'row', style: 'margin-top:6px' },
          el('button', { class: 'p', disabled: G.ship.phase !== 'landed', onclick: () => A.quickTravel() }, 'Quick travel (automatic)'),
          el('button', { onclick: () => A.randomDestination() }, 'Random destination'), el('button', { onclick: () => A.toggleDestLock() }, G.ship.destLocked ? 'Unlock coordinates' : 'Lock coordinates'), el('button', { onclick: () => A.telepathic() }, 'Telepathic circuit'))),
      el('h2', {}, 'Manual flight sequence'),
      el('div', { class: 'row' },
        el('button', { class: G.ship.rotor ? 'p' : '', onclick: () => A.toggleRotor() }, `1 · Time rotor ${G.ship.rotor ? 'ON' : 'off'}`),
        el('button', { class: !G.ship.lock ? 'p' : '', onclick: () => A.releaseLock() }, `2 · Temporal lock ${G.ship.lock ? 'engaged' : 'released'}`),
        el('button', { class: G.ship.throttle >= 0.5 ? 'p' : '', onclick: () => A.setThrottle(G.ship.throttle >= 0.5 ? 0 : 0.8) }, `3 · Flight controls ${G.ship.throttle >= 0.5 ? 'engaged' : 'idle'}`),
        el('label', {}, 'Stabilizers ', el('input', { type: 'range', min: 0, max: 4, value: Math.round(G.ship.stabilizers * 4), oninput: (e) => A.setStabilizers(e.target.value / 4) })),
        el('button', { class: 'p', disabled: !flightOK.ok, title: flightOK.ok ? '' : flightOK.why.join(', '), onclick: () => A.dematerialize() }, '4 · Dematerialize'),
        el('button', { class: 'p', disabled: !(G.ship.phase === 'vortex'), onclick: () => A.materialize() }, '5 · Materialize')),
      el('div', { class: 'sub' }, flightOK.ok ? 'Ready to dematerialize.' : 'Not ready: ' + flightOK.why.join(', ') + '.'),
      this.coordForm(), this.suggestions(),
      el('h2', {}, 'Destinations'), this.destGrid(DESTS.filter((x) => !x.secret || G.disc.secrets.unlisted)),
    );
    return root;
  },
  coordForm() {
    const d = G.dest; const num = (k, min, max) => el('input', { type: 'number', value: d[k], min, max, style: 'width:90px', onchange: (e) => A.setDestField(k, +e.target.value) });
    const worlds = Object.values(WORLDS).filter((w) => w.kind !== 'dimension' && !w.secret);
    return el('div', { class: 'item' }, el('div', { class: 'n' }, 'Manual coordinates'), el('div', { class: 'row', style: 'margin-top:6px' },
      el('label', {}, 'World ', el('select', { onchange: (e) => A.setDestField('world', e.target.value) }, worlds.map((w) => el('option', { value: w.id, selected: w.id === d.worldId }, w.name)))),
      el('label', {}, 'Year ', num('year', -4600000000, 5000000000)), el('label', {}, 'Month ', num('month', 1, 12)), el('label', {}, 'Day ', num('day', 1, 28)), el('label', {}, 'Hour ', num('hour', 0, 23))),
    el('div', { class: 'row', style: 'margin-top:6px' }, el('label', {}, 'Dimensional coordinates'), [0, 1, 2].map((i) => el('input', { type: 'number', min: 0, max: 9, value: d.dim[i], style: 'width:60px', onchange: (e) => A.setDim(i, +e.target.value) })), el('span', { class: 'sub' }, 'Some realities answer only to specific coordinates — read the Library, ask the ship.')),
    el('div', { class: 'row', style: 'margin-top:6px' }, [[-1000000, '−1M'], [-1000, '−1000'], [-100, '−100'], [-10, '−10'], [10, '+10'], [100, '+100'], [1000, '+1000'], [1000000, '+1M']].map(([v, n]) => el('button', { onclick: () => A.shiftYear(v) }, n))));
  },
  suggestions() {
    if (!rt.suggestions.length) return el('div');
    return el('div', { class: 'item' }, el('div', { class: 'n' }, 'The ship suggests…'), rt.suggestions.map((s) => el('div', { class: 'row' }, el('span', { class: 'tag ' + (s.kind === 'warn' ? 'bad' : '') }, s.kind), el('span', {}, s.text + (s.coordsHint ? ` [coordinates ${s.coordsHint}]` : '')), s.dest ? el('button', { onclick: () => A.setDestination(s.dest) }, 'Set destination') : null)));
  },
  destGrid(list) {
    const cats = {}; list.forEach((d) => (cats[d.category] ||= []).push(d));
    return el('div', { class: 'col' }, Object.entries(cats).map(([c, arr]) => el('div', {}, el('h2', {}, c), el('div', { class: 'grid' }, arr.map((d) => {
      const key = destKey(d), visited = !!G.disc.dests[key], info = destInfo(d), fav = !!G.disc.favorites[key];
      return el('div', { class: 'item' }, el('div', { class: 'n' }, d.name), el('div', { class: 'd' }, `${fmtYear(d.year)} · ${d.desc}`), el('div', { class: 'row' },
        visited ? el('span', { class: 'tag ok' }, 'visited') : el('span', { class: 'tag' }, 'unvisited'), el('span', { class: 'tag ' + (info.danger >= 4 ? 'bad' : '') }, `danger ${info.danger}`), d.rare ? el('span', { class: 'tag' }, 'rare window') : null,
        el('button', { class: 'p right', onclick: () => A.setDestination(d) }, 'Set'), el('button', { title: 'Favorite', onclick: () => { A.setFavorite(key, !fav); this.render(); } }, fav ? '★' : '☆')));
    })))));
  },

  // ---- Ship
  shipTab() {
    const s = G.ship, root = el('div', { class: 'col' });
    root.append(el('h2', {}, 'Systems'), el('div', { class: 'grid' }, SYSTEMS.map((k) => el('div', { class: 'item' }, el('div', { class: 'row' }, el('span', { class: 'n' }, SYSTEM_LABEL[k]), el('span', { class: 'right' }, `${s[k].toFixed(0)}%`)), barEl(s[k]),
      sandbox() ? el('button', { onclick: () => A.repair(k, 100) }, 'Repair') : el('div', { class: 'd' }, k === 'power' ? 'Regenerates when landed' : 'Repair at Engineering Bay or the console emergency panel')))));
    if (rt.events.length) root.append(el('h2', {}, 'Active alerts'), rt.events.map((e) => el('div', { class: 'item' }, el('div', { class: 'n', style: 'color:var(--bad)' }, e.def.name), el('div', { class: 'd' }, `Cause: ${e.def.cause}`), el('div', { class: 'd', style: 'color:var(--warn)' }, `Fix: ${e.def.hint}`),
      el('div', { class: 'row' }, el('button', { onclick: () => A.recalibrateNav() }, 'Recalibrate nav'), el('button', { onclick: () => A.realignDimensions() }, 'Realign dimensions'), el('button', { onclick: () => A.restartReactor() }, 'Restart reactor'), el('button', { onclick: () => A.setStabilizers(1) }, 'Stabilizers max'), el('button', { onclick: () => A.toggleDestLock() }, 'Lock destination'), el('button', { onclick: () => A.toggleRotor() }, 'Toggle rotor')))));
    root.append(el('h2', {}, 'Controls'), el('div', { class: 'row' },
      el('button', { onclick: () => A.toggleShields() }, `Shields: ${s.shields ? 'raised' : 'down'}`), el('button', { onclick: () => A.toggleCloak() }, `Cloak: ${s.cloak.on ? 'ON' : 'off'}`),
      el('label', {}, 'Cloak mode ', el('select', { onchange: (e) => { A.setCloakMode(+e.target.value); this.render(); } }, CLOAK_MODES.map((m) => el('option', { value: m.id, selected: m.id === s.cloak.mode }, m.name + (A.cloakModeUnlocked(m.id) ? '' : ' 🔒'))))),
      el('label', {}, 'Power ', el('select', { onchange: (e) => { G.ship.powerMode = +e.target.value; } }, ['Eco', 'Normal', 'Boost'].map((n, i) => el('option', { value: i, selected: (s.powerMode ?? 1) === i }, n))))),
    el('div', { class: 'row' }, el('button', { onclick: () => A.toggleDoors() }, 'Doors: open/close both'), el('button', { onclick: () => A.toggleDoors('l') }, 'Left door'), el('button', { onclick: () => A.toggleDoors('r') }, 'Right door'), el('button', { onclick: () => A.toggleLock() }, s.doors.locked ? 'Unlock' : 'Lock'), el('button', { class: 'd', onclick: () => A.emergencyRelease() }, 'Emergency override'),
      el('label', {}, el('input', { type: 'checkbox', checked: G.settings.doorSafe, onchange: (e) => { G.settings.doorSafe = e.target.checked; } }), ' Containment field at doors (safe)')),
    el('div', { class: 'row' }, el('button', { onclick: () => A.scan() }, 'Scan'), el('button', { onclick: () => A.recall() }, 'Recall TARDIS to me'), el('button', { onclick: () => A.toggleFollow() }, `Follow mode: ${s.followMode ? 'on' : 'off'}`), el('button', { onclick: () => A.repair('cloakIntegrity', 0) , style: 'display:none' }, ''),
      el('button', { onclick: () => A.toggleExperimental() }, !s.guard ? 'Lift guard on experimental flight' : s.experimental ? 'Disarm experimental flight' : 'ARM experimental flight')),
    el('h2', {}, 'Rooms'), el('div', { class: 'grid' }, ROOM_DEFS.filter((r) => r.id !== 'turbulence').map((r) => el('div', { class: 'item' }, el('div', { class: 'n' }, G.disc.rooms[r.id] ? r.name : '▒▒▒▒▒▒▒▒'), el('div', { class: 'd' }, G.disc.rooms[r.id] ? 'Explored' : (r.hint ? `Lead: ${r.hint}` : 'Undiscovered'))))));
    return root;
  },

  // ---- Archive
  archiveTab() {
    const d = G.disc, root = el('div', { class: 'col' }); const q = this.search.toLowerCase();
    const match = (t) => !q || t.toLowerCase().includes(q);
    root.append(el('div', { class: 'row' }, el('input', { placeholder: 'Search archive…', value: this.search, style: 'min-width:260px', oninput: (e) => { this.search = e.target.value; this.render(); const i = $('#panelCard input[placeholder]'); i && i.focus(); i && i.setSelectionRange(this.search.length, this.search.length); } }), el('span', { class: 'sub' }, `${Object.keys(d.dests).length} destinations · ${Object.keys(d.dims).length} dimensions · ${d.journeys} journeys`)));
    const vis = Object.entries(d.dests).filter(([, v]) => match(`${v.name} ${v.year}`));
    const section = (title, nodes) => el('div', {}, el('h2', {}, title), nodes.length ? el('div', { class: 'grid' }, nodes) : el('div', { class: 'sub' }, 'Nothing yet.'));
    root.append(section('Favorites', DESTS.filter((x) => d.favorites[destKey(x)] && match(x.name)).map((x) => this.archiveItem(x))));
    root.append(section('Visited destinations & landing sites', vis.map(([k, v]) => el('div', { class: 'item' }, el('div', { class: 'n' }, v.name), el('div', { class: 'd' }, `${fmtDate(v)} · visited ${v.n}×`), el('div', { class: 'row' }, el('button', { class: 'p', onclick: () => A.setDestination({ ...v, dim: v.dim || [0, 0, 0] }) }, 'Set destination'), el('button', { onclick: () => { A.setFavorite(k, !d.favorites[k]); this.render(); } }, d.favorites[k] ? '★' : '☆'))))));
    const dims = Object.values(WORLDS).filter((w) => w.kind === 'dimension' && !w.generated);
    root.append(section('Dimensions (mapped)', dims.filter((w) => d.dims[w.id] && match(w.name)).map((w) => el('div', { class: 'item' }, el('div', { class: 'n' }, w.name), el('div', { class: 'd' }, `${w.hint} · coordinates ${w.dimKey.join(' · ')}`), el('div', { class: 'row' }, el('span', { class: 'tag ' + (d.worldsVisited[w.id] ? 'ok' : '') }, d.worldsVisited[w.id] ? 'visited' : 'discovered'), el('button', { class: 'p right', onclick: () => A.setDestination({ worldId: w.id, year: 0, name: w.name, dim: [...w.dimKey] }) }, 'Set destination'))))
      .concat(Object.values(WORLDS).filter((w) => w.generated && d.dims[w.id]).map((w) => el('div', { class: 'item' }, el('div', { class: 'n' }, w.name), el('div', { class: 'd' }, `Generated · coordinates ${w.dimKey.join(' · ')}`), el('button', { class: 'p', onclick: () => A.setDestination({ worldId: w.id, year: 0, name: w.name, dim: [...w.dimKey] }) }, 'Set destination'))))));
    const leads = [...dims.filter((w) => !d.dims[w.id]).map((w) => el('div', { class: 'item' }, el('div', { class: 'n' }, (d.flags.leads || {})[w.id] ? w.name : 'Unknown reality'), el('div', { class: 'd' }, (d.flags.leads || {})[w.id] ? `${w.hint} Coordinates ${w.dimKey.join(' · ')} — enter them on the console.` : `"${w.hint}" — find its coordinates (Library, telepathic circuit).`))), ...DESTS.filter((x) => !d.dests[destKey(x)] && !x.secret && match(x.name)).map((x) => this.archiveItem(x, 'unexplored'))];
    root.append(section('Unexplored leads', leads));
    const lists = [['Life forms', d.lifeforms], ['Landmarks', d.landmarks], ['Anomalies', d.anomalies], ['Artifacts', d.artifacts]];
    lists.forEach(([t, o]) => root.append(section(t, Object.entries(o).filter(([k, v]) => match(`${k} ${typeof v === 'string' ? v : v.name || ''}`)).map(([k, v]) => el('div', { class: 'item' }, el('div', { class: 'n' }, (typeof v === 'object' ? v.name : null) || k.split(':').pop().replace(/_/g, ' ')), el('div', { class: 'd' }, typeof v === 'object' ? v.desc || '' : typeof v === 'string' ? v : ''))))));
    root.append(el('div', {}, el('h2', {}, 'Secrets found'), el('div', { class: 'sub' }, Object.keys(d.secrets).length ? Object.keys(d.secrets).join(', ') : 'None yet. Not everything is on the archive.')));
    return root;
  },
  archiveItem(x, tag) { return el('div', { class: 'item' }, el('div', { class: 'n' }, x.name), el('div', { class: 'd' }, `${fmtYear(x.year)} · ${x.desc}`), el('div', { class: 'row' }, tag ? el('span', { class: 'tag' }, tag) : null, el('button', { class: 'p right', onclick: () => A.setDestination(x) }, 'Set destination'))); },

  // ---- Customize
  customTab() {
    const c = G.custom, s = G.settings, root = el('div', { class: 'col' });
    const sel = (label, cur, opts, on) => el('div', { class: 'row' }, el('label', {}, label), el('select', { onchange: (e) => { on(e.target.value); bus.emit('custom'); } }, opts.map(([v, n, dis]) => el('option', { value: v, selected: String(v) === String(cur), disabled: !!dis }, n))));
    const cb = (label, cur, on) => el('div', { class: 'row' }, el('label', {}, label), el('input', { type: 'checkbox', checked: cur, onchange: (e) => { on(e.target.checked); } }));
    root.append(el('h2', {}, 'Exterior'),
      sel('Paint shade', c.paint, ['Classic', 'Bright', 'Midnight', 'Cobalt', 'Deep'].map((n, i) => [i, n]), (v) => { c.paint = +v; }),
      sel('Skin', c.skin, SKINS.map((k, i) => [i, k.name + (k.unlock && !G.disc.unlocks[k.unlock] && !sandbox() ? ' 🔒' : ''), k.unlock && !G.disc.unlocks[k.unlock] && !sandbox()]), (v) => { c.skin = +v; }),
      sel('Condition', c.condition, ['Pristine', 'Weathered', 'Battered'].map((n, i) => [i, n]), (v) => { c.condition = +v; }),
      sel('Roof lamp', c.lamp, ['Classic bulb', 'Fresnel', 'Crystal', 'Neon ring'].map((n, i) => [i, n]), (v) => { c.lamp = +v; }),
      sel('Signage', c.sign, ['POLICE PUBLIC CALL BOX', 'POLICE BOX', 'PUBLIC CALL BOX', 'TELEPHONE'].map((n, i) => [i, n]), (v) => { c.sign = +v; }),
      sel('Door fittings', c.door, ['Brass', 'Timber', 'Steel', 'Blue enamel'].map((n, i) => [i, n]), (v) => { c.door = +v; }),
      sel('Materialization style', c.matStyle, MAT_STYLES.map((n, i) => [i, n]), (v) => { c.matStyle = +v; }),
      sel('Cloak visual effect', c.cloakFx, ['Heat haze', 'Glitch slices', 'Ripple'].map((n, i) => [i, n]), (v) => { c.cloakFx = +v; }),
      el('h2', {}, 'Interior & console'),
      sel('Interior theme', c.theme, [['classic', 'Classic roundels'], ['futuristic', 'Futuristic control room'], ['organic', 'Organic living'], ['industrial', 'Dark industrial'], ['neon', 'Neon sci-fi'], ['crystal', 'Crystal-powered']], (v) => { c.theme = v; }),
      sel('Console arrangement', c.layout, [0, 1, 2, 3, 4, 5].map((i) => [i, `Layout ${i + 1}`]), (v) => { c.layout = +v; }),
      sel('Display style', c.display, ['Cyan', 'Amber', 'Green', 'Magenta'].map((n, i) => [i, n]), (v) => { c.display = +v; }),
      sel('Time rotor', c.rotor, ['Glass column', 'Crystal prism', 'Organic pulse'].map((n, i) => [i, n]), (v) => { c.rotor = +v; }),
      el('div', { class: 'row' }, el('label', {}, 'Interior lighting'), el('input', { type: 'range', min: 0.4, max: 1.8, step: 0.05, value: c.light, oninput: (e) => { c.light = +e.target.value; } })),
      sel('Startup sequence', c.startup, ['Standard', 'Rapid', 'Ceremonial', 'Instant'].map((n, i) => [i, n]), (v) => { c.startup = +v; }),
      el('h2', {}, 'Flight'),
      sel('Vortex appearance', s.vortexStyle, ['Classic blue', 'Aurora', 'Ember', 'Prismatic'].map((n, i) => [i, n]), (v) => { s.vortexStyle = +v; }),
      sel('Camera effects', s.camFx, [['full', 'Full'], ['reduced', 'Reduced'], ['off', 'Off']], (v) => { s.camFx = v; }),
      sel('Flight automation', s.automation, [['manual', 'Manual'], ['semi', 'Semi-automatic'], ['auto', 'Automatic']], (v) => { s.automation = v; }),
      sel('Engine ambience', s.engine, ['Classic wheeze', 'Deep drone', 'Choral'].map((n, i) => [i, n]), (v) => { s.engine = +v; }),
      sel('Random events', s.events, [['off', 'Off'], ['low', 'Low'], ['normal', 'Normal'], ['high', 'High']], (v) => { s.events = v; }),
      cb('Turbulence', s.turbulence, (v) => { s.turbulence = v; }), cb('Door behaviour: safe (containment field)', s.doorSafe, (v) => { s.doorSafe = v; }), cb('Cinematic camera on flights', s.cinematic, (v) => { s.cinematic = v; }), cb('Auto emergency cloak on dangerous arrivals', s.autoEmergencyCloak, (v) => { s.autoEmergencyCloak = v; }),
      el('h2', {}, 'Graphics, audio & input'),
      sel('Quality', s.quality, [['low', 'Low'], ['medium', 'Medium'], ['high', 'High']], (v) => { s.quality = v; }),
      el('div', { class: 'row' }, el('label', {}, 'Volume'), el('input', { type: 'range', min: 0, max: 1, step: 0.05, value: s.volume, oninput: (e) => { s.volume = +e.target.value; audio.setVolume(s.volume); } })),
      el('div', { class: 'row' }, el('label', {}, 'Mouse sensitivity'), el('input', { type: 'range', min: 0.3, max: 2.5, step: 0.05, value: s.sens, oninput: (e) => { s.sens = +e.target.value; } })),
      el('div', { class: 'row' }, el('label', {}, 'Field of view'), el('input', { type: 'range', min: 55, max: 100, step: 1, value: s.fov, oninput: (e) => { s.fov = +e.target.value; } })),
      cb('Invert mouse Y', s.invertY, (v) => { s.invertY = v; }),
      el('h2', {}, 'Game'), el('div', { class: 'modes' }, Object.entries(MODES).map(([k, [n, d]]) => el('button', { class: G.mode === k ? 'on' : '', onclick: () => { applyMode(k); this.render(); } }, el('b', {}, n), el('small', {}, d)))),
      el('div', { class: 'row' }, el('button', { class: 'd', onclick: () => { if (confirm('Erase the saved game and restart?')) { resetGame(); location.reload(); } } }, 'New game (erase save)')));
    return root;
  },

  // ---- Workshop
  workshopTab() {
    const d = G.disc, have = Object.keys(d.artifacts).length - (d.flags.spent || 0), root = el('div', { class: 'col' });
    const R = [['cloak3', 'Cloak mode: Environmental disguise', 2], ['cloak4', 'Cloak mode: Temporal concealment', 4], ['cloak5', 'Cloak mode: Dimensional phase cloak', 6], ['skin2', 'Skin: Crimson', 1], ['skin3', 'Skin: Emerald', 2], ['skin4', 'Skin: Gilded', 4], ['skin5', 'Skin: Ghost white', 6]];
    root.append(el('div', { class: 'item' }, el('div', { class: 'n' }, `Artifacts available: ${have}`), el('div', { class: 'd' }, 'Artifacts are recovered from the worlds you visit. Spend them at the workbench to unlock ship upgrades.')),
      el('div', { class: 'grid' }, R.map(([k, n, cost]) => { const done = !!d.unlocks[k]; return el('div', { class: 'item' }, el('div', { class: 'n' }, n), el('div', { class: 'd' }, done ? 'Installed' : `Cost: ${cost} artifact${cost > 1 ? 's' : ''}`), el('button', { class: 'p', disabled: done || have < cost, onclick: () => { d.flags.spent = (d.flags.spent || 0) + cost; d.unlocks[k] = true; audio.chime(5); log(`Upgrade installed: ${n}.`, 'sys'); this.render(); } }, done ? 'Owned' : 'Craft')); })));
    return root;
  },

  journalTab() { return el('div', { class: 'log' }, [...G.disc.logs].reverse().map((l) => el('div', { class: l.kind }, `${new Date(l.t).toLocaleTimeString()} — ${l.msg}`))); },

  // ---- Sandbox / emergency
  sandboxTab() {
    const root = el('div', { class: 'col' });
    root.append(el('h2', {}, 'Trigger an emergency'), el('div', { class: 'row' }, ['turbulence', 'navfail', 'drift', 'powerloss', 'cloakfail', 'rotor', 'overlap', 'movingdest'].map((id) => el('button', { onclick: () => A.sandboxTriggerEvent(id) }, id))),
      el('div', { class: 'sub' }, 'Each emergency states its cause and fix in the alert banner. Resolve them from the console or the Ship tab.'));
    if (sandbox()) {
      root.append(el('h2', {}, 'System values'), el('div', { class: 'grid' }, SYSTEMS.map((k) => el('div', { class: 'item' }, el('div', { class: 'n' }, SYSTEM_LABEL[k]), el('input', { type: 'range', min: 0, max: 100, value: G.ship[k], oninput: (e) => A.sandboxSetSystem(k, +e.target.value) })))),
        el('h2', {}, 'World'), el('div', { class: 'row' }, el('button', { class: 'p', onclick: () => A.sandboxGo() }, 'Teleport to selected destination now'), el('button', { onclick: () => A.sandboxSpawnDimension() }, 'Spawn new dimension'), el('button', { onclick: () => { unlockAll(); this.toast('Everything unlocked.'); this.render(); } }, 'Unlock everything'), el('button', { onclick: () => { G.ship.power = 100; } }, 'Full power')));
    }
    return root;
  },
};
