/* Soundcraft acceptance tests. Drives the real app in headless Chromium and measures real audio.
   Usage: node soundcraft/tests/e2e.cjs [screenshot-dir]
   Set PLAYWRIGHT_MODULE to a playwright install if it is not resolvable from here. */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const path = require('path'), fs = require('fs'), os = require('os');
/* Served over http, as AudioWorklets cannot load from file:// pages in a plain browser (the desktop app allows it). */
const http = require('http'), ROOT = path.resolve(__dirname, '../app'), TYPES = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.woff2': 'font/woff2', '.png': 'image/png', '.json': 'application/json' };
const server = http.createServer((q, r) => { const f = path.join(ROOT, decodeURIComponent(q.url.split('?')[0]).replace(/^\/+/, '') || 'index.html'); if (!f.startsWith(ROOT) || !fs.existsSync(f) || fs.statSync(f).isDirectory()) { r.writeHead(404); r.end(); return } r.writeHead(200, { 'content-type': TYPES[path.extname(f)] || 'application/octet-stream' }); fs.createReadStream(f).pipe(r) }).listen(0);
const URL = () => 'http://127.0.0.1:' + server.address().port + '/index.html';
const OUT = process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'soundcraft-'));
const results = []; let failed = 0;
const ok = (name, cond, info = '') => { if (!cond) failed++; const line = (cond ? 'PASS ' : 'FAIL ') + name + (info ? ' — ' + info : ''); results.push(line); console.log(line); };
(async () => {
  const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required', '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'] });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 950 }, acceptDownloads: true, permissions: ['microphone'] });
  const page = await ctx.newPage(); const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error' && !/favicon/.test(m.text())) errors.push(m.text()) });
  await page.goto(URL()); await page.waitForTimeout(1000);
  const E = (fn, arg) => page.evaluate(fn, arg);
  ok('app boots into a workspace with the demo song', await E(() => S.tracks.length === 5 && !!LIB[curId]));
  ok('engine registry and library loaded', await E(() => Object.keys(ENG).length >= 20 && FACTORY.length >= 90), await E(() => Object.keys(ENG).length + ' engines, ' + FACTORY.length + ' presets'));
  // offline measurement helpers live in the page
  await E(() => {
    window.T = {
      rms(buf, a, b) { const d = buf.getChannelData(0), s = Math.floor(a * buf.sampleRate), e = Math.min(d.length, Math.floor(b * buf.sampleRate)); let q = 0; for (let i = s; i < e; i++) q += d[i] * d[i]; return Math.sqrt(q / Math.max(1, e - s)) },
      brightness(buf, win) { const d = buf.getChannelData(0), n = Math.floor(win * buf.sampleRate), out = []; for (let s = 0; s + n < d.length; s += n) { let a = 0, b = 0; for (let i = s + 1; i < s + n; i++) { a += d[i] * d[i]; const x = d[i] - d[i - 1]; b += x * x } if (a > 1e-7) out.push(Math.sqrt(b / a)) } return out },
      std(a) { const m = a.reduce((x, y) => x + y, 0) / a.length; return Math.sqrt(a.reduce((x, y) => x + (y - m) * (y - m), 0) / a.length) },
      solo(project) { loadProject(project); },
      oneTrack(preset, notes, extra) { const t = newTrack(preset); const p = curPattern(t); p.len = 4; p.notes = notes; t.clips = [{ id: 'c1', pat: p.id, start: 0, len: 4 }]; t.vol = 1; t.rev = 0; t.dly = 0; if (extra) extra(t); return { v: 2, bpm: 120, swing: 0, quant: 1, master: 1, returns: defaultReturns(), masterFx: [], loop: { on: false, start: 0, end: 4 }, markers: [], scale: { root: 0, type: 'off' }, tracks: [t] } }
    };
  });
  // 1 create a project
  await page.click('#btnLibrary'); await page.selectOption('#libTpl', 'tpl-orch'); await page.click('#libNew'); await page.waitForTimeout(300);
  const orch = await E(() => ({ n: S.tracks.length, names: S.tracks.map(t => t.name), markers: S.markers.length, rev: S.returns.rev.params.size, notes: S.tracks.reduce((a, t) => a + t.patterns.reduce((s, p) => s + p.notes.length, 0), 0) }));
  ok('a template creates the intended tracks, returns and markers, with no fake notes', orch.n === 6 && orch.markers === 5 && orch.rev === 4.5 && orch.notes === 0, JSON.stringify(orch));
  // 2 MIDI note editing in the piano roll
  await E(() => { selectTrack(0); setView('arrange'); PREF.dock = 'roll'; showDock(); });
  await page.waitForTimeout(300);
  const wrap = await page.locator('#rollWrap').boundingBox();
  const before = await E(() => curPattern(selTrack()).notes.length);
  await page.mouse.move(wrap.x + 120, wrap.y + 120); await page.mouse.down(); await page.mouse.move(wrap.x + 220, wrap.y + 120, { steps: 5 }); await page.mouse.up();
  const drawn = await E(() => { const n = curPattern(selTrack()).notes; return n[n.length - 1] && { ...n[n.length - 1] } });
  ok('draw a note with a dragged length', (await E(() => curPattern(selTrack()).notes.length)) === before + 1 && drawn.l > 1, JSON.stringify(drawn));
  await page.mouse.move(wrap.x + 125, wrap.y + 120); await page.mouse.down(); await page.mouse.move(wrap.x + 185, wrap.y + 92, { steps: 5 }); await page.mouse.up();
  const moved = await E(() => { const n = curPattern(selTrack()).notes; return { ...n[n.length - 1] } });
  ok('move a note in time and pitch', moved.s !== drawn.s && moved.p !== drawn.p, JSON.stringify(moved));
  await page.keyboard.press('Control+z'); ok('undo restores the note', await E(d => { const n = curPattern(selTrack()).notes; const x = n[n.length - 1]; return x.s === d.s && x.p === d.p }, drawn));
  await page.keyboard.press('Control+Shift+z'); ok('redo re-applies the move', await E(m => { const n = curPattern(selTrack()).notes; return n[n.length - 1].s === m.s }, moved));
  await E(() => { const n = curPattern(selTrack()).notes; R.sel = new Set([n[n.length - 1]]) }); await page.locator('#lane').click({ position: { x: 5, y: 5 } }).catch(() => { });
  // 3 play an instrument
  await E(() => { const i = S.tracks.findIndex(t => t.engine === 'brass'); selectTrack(i) });
  await page.keyboard.down('a'); await page.waitForTimeout(450); const lv = await E(() => A.level); await page.keyboard.up('a');
  ok('playing a key produces real audio', lv > 0.003, 'rms ' + lv.toFixed(4));
  // 4 the arrangement plays clips in order (offline render, measured per bar)
  const order = await E(async () => {
    const a = newTrack('bass-sub'), b = newTrack('syn-poly'); [a, b].forEach(t => { t.rev = 0; t.dly = 0; t.vol = 1 });
    curPattern(a).len = 1; curPattern(a).notes = [{ p: 48, s: 0, l: 8, v: 1 }]; curPattern(b).len = 1; curPattern(b).notes = [{ p: 72, s: 0, l: 8, v: 1 }];
    a.clips = [{ id: 'x', pat: curPattern(a).id, start: 0, len: 1 }]; b.clips = [{ id: 'y', pat: curPattern(b).id, start: 2, len: 1 }];
    loadProject({ v: 2, bpm: 120, tracks: [a, b], master: 1, masterFx: [], loop: { on: false, start: 0, end: 4 } });
    const buf = await renderOffline({ startBar: 0, endBar: 4, sampleRate: 44100, tail: 0 }); const bar = 2;
    return [T.rms(buf, 0.05, 1), T.rms(buf, bar + 0.6, bar + 1.9), T.rms(buf, 2 * bar + 0.05, 2 * bar + 1)].map(x => +x.toFixed(4)) });
  ok('arrangement plays clips in the right bars', order[0] > 0.01 && order[1] < order[0] * 0.05 && order[2] > 0.01, 'bar1 ' + order[0] + ' / bar2 ' + order[1] + ' / bar3 ' + order[2]);
  // 5 the mixer changes the output
  const mix = await E(async () => { const p = T.oneTrack('syn-poly', [{ p: 60, s: 0, l: 16, v: 1 }]); loadProject(p); const full = T.rms(await renderOffline({ startBar: 0, endBar: 1, tail: 0 }), 0.1, 1.5);
    S.tracks[0].vol = 0.5; const half = T.rms(await renderOffline({ startBar: 0, endBar: 1, tail: 0 }), 0.1, 1.5); S.tracks[0].mute = true; const muted = T.rms(await renderOffline({ startBar: 0, endBar: 1, tail: 0 }), 0.1, 1.5); return [full, half, muted] });
  ok('fader and mute change the rendered output', mix[1] < mix[0] * 0.4 && mix[2] < 1e-5, mix.map(x => x.toFixed(4)).join(' / '));
  // modulation: LFO on cutoff is audible
  const lfo = await E(async () => { const mk = withLfo => T.oneTrack('syn-poly', [{ p: 48, s: 0, l: 64, v: 1 }], t => { t.params.cutoff = 900; if (withLfo) { const s = newSource('lfo'); s.rate = 2; t.mod.sources.push(s); t.mod.routes.push(newRoute(s.id, 'eng:cutoff', 0.6)) } });
    loadProject(mk(false)); const a = T.std(T.brightness(await renderOffline({ startBar: 0, endBar: 2, tail: 0 }), 0.05)); loadProject(mk(true)); const b = T.std(T.brightness(await renderOffline({ startBar: 0, endBar: 2, tail: 0 }), 0.05)); return [a, b] });
  ok('an LFO routed to filter cutoff audibly moves the brightness', lfo[1] > lfo[0] * 2.5, 'variation ' + lfo.map(x => x.toFixed(3)).join(' → '));
  // macro with different ranges
  const mac = await E(() => { loadProject(T.oneTrack('syn-poly', [])); const t = S.tracks[0]; const m = newMacro('Bright'); t.mod.macros.push(m); m.maps.push({ dst: 'eng:cutoff', min: 0.2, max: 0.6, inv: false, curve: 'lin' }, { dst: 'eng:reso', min: 0.9, max: 0.5, inv: false, curve: 'lin' }, { dst: 'trk:rev', min: 0, max: 0.8, inv: true, curve: 'lin' });
    applyMacro(t, m, 0.5); const c = ENG.va.params.find(p => p.k === 'cutoff'), r = ENG.va.params.find(p => p.k === 'reso');
    return { cut: +toNorm(c, t.params.cutoff).toFixed(3), reso: +toNorm(r, t.params.reso).toFixed(3), rev: +t.rev.toFixed(3) } });
  ok('one macro sets several controls within their own ranges', mac.cut === 0.4 && mac.reso === 0.7 && mac.rev === 0.4, JSON.stringify(mac));
  // modulation patch save/restore and transfer with compatibility report
  const mp = await E(() => { const t = S.tracks[0]; const s = newSource('lfo'); t.mod.sources.push(s); t.mod.routes.push(newRoute(s.id, 'eng:cutoff', 0.3), newRoute('vel', 'v:vcut', 0.2)); const patch = exportModPatch(t);
    const r = addUserItem('modpatch', 'Test patch ' + Date.now(), patch); const t2 = newTrack('keys-grand'); const missing = applyModPatch(t2, r.item.data);
    return { saved: !!r.item, routes: t2.mod.routes.length, missing, disabled: t2.mod.routes.filter(x => !x.on).length } });
  ok('a modulation patch saves, restores and reports what another instrument lacks', mp.saved && mp.routes === 2 && mp.missing.includes('eng:cutoff') && mp.disabled === 1, JSON.stringify(mp));
  // factory presets are never changed by edits
  const fac = await E(() => { const t = newTrack('keys-tine'); S.tracks.push(t); t.params.index = 9; const r = addUserItem('instrument', 'My tine ' + Date.now(), instrumentData(t)); return { factory: FACTORY_BY_ID['keys-tine'].params.index, user: r.item.data.params.index } });
  ok('editing a factory instrument leaves the original intact and saves a new preset', fac.factory === 1.4 && fac.user === 9, JSON.stringify(fac));
  // sampler: import a real WAV, map zones, save, restore, play
  const smp = await E(async () => { ensureAudio(); const sr = 44100, b = new AudioBuffer({ length: sr, numberOfChannels: 1, sampleRate: sr }), d = b.getChannelData(0); for (let i = 0; i < d.length; i++)d[i] = Math.sin(i / sr * 2 * Math.PI * 261.63) * 0.5;
    const file = new File([encodeWav(b, 16)], 'test-tone.wav', { type: 'audio/wav' }); const m = await importSampleFile(file);
    const t = newTrack('sampler-empty'); t.zones = [newZone(m.id, 'Low', { lo: 0, hi: 59, root: 60 }), newZone(m.id, 'High', { lo: 60, hi: 127, root: 60, loop: true, ls: 0.2, le: 0.8, vlo: 64 })];
    const r = addUserItem('instrument', 'Sampler test ' + Date.now(), instrumentData(t)); const back = newTrack(presetById(r.item.id));
    const p = T.oneTrack(presetById(r.item.id), [{ p: 60, s: 0, l: 8, v: 1 }]); loadProject(p); const level = T.rms(await renderOffline({ startBar: 0, endBar: 1, tail: 0 }), 0.05, 0.5);
    return { zones: back.zones.length, loop: back.zones[1].loop, vlo: back.zones[1].vlo, ls: back.zones[1].ls, level: +level.toFixed(4) } });
  ok('a sampler preset keeps its zones and layers after saving, and plays', smp.zones === 2 && smp.loop && smp.vlo === 64 && smp.ls === 0.2 && smp.level > 0.01, JSON.stringify(smp));
  // ensembles: zones route notes to the right instruments
  const ens = await E(() => { loadProject(blankProject()); const r = loadEnsemble(ENSEMBLES.find(e => e.id === 'ens-band')); selectTrack(S.tracks.indexOf(r.made[0])); ensureAudio();
    const count = n => { liveOn(n, 0.9, 'test'); const v = r.made.map(t => t._rt ? t._rt.live.size : 0); liveOff(n, 'test'); return v };
    const low = count(36), mid = count(52), high = count(67); return { made: r.made.length, low, mid, high, groups: new Set(r.made.map(t => t.input.group)).size } });
  ok('an ensemble restores its instruments and splits the keyboard between them', ens.made === 4 && ens.groups === 1 && ens.low.join() === '1,0,0,0' && ens.mid.join() === '0,1,0,0' && ens.high.join() === '0,0,1,1', JSON.stringify(ens));
  // track preset
  const tp = await E(() => { const t = S.tracks[2]; t.fx.push(newFx('chorus', { mix: 0.33 })); const pk = ENG[t.engine].params.find(p => p.type === 'num' && p.k !== 'level').k; t.params[pk] = ENG[t.engine].params.find(p => p.k === pk).min; const r = addUserItem('track', 'TP ' + Date.now(), trackData(t)); const before = S.tracks.length;
    const d = r.item.data, n = newTrack(d, { name: d.name }); n.fx = sanitizeFxList(d.fx); S.tracks.push(n); return { engine: n.engine === t.engine, param: n.params[pk] === t.params[pk], fx: n.fx.map(f => f.type + ':' + f.params.mix).join(), added: S.tracks.length === before + 1 } });
  ok('a track preset restores its instrument and effects as a new track', tp.engine && tp.param && tp.fx === 'chorus:0.33' && tp.added, JSON.stringify(tp));
  // missing dependencies and invalid files
  const imp = await E(() => { const out = {}; try { validateImport({ format: 'soundcraft', formatVersion: 99, type: 'instrument', data: {} }) } catch (e) { out.newer = e.message }
    try { validateImport({ format: 'soundcraft', formatVersion: 1, type: 'instrument', data: { engine: 'nope', params: {} } }) } catch (e) { out.engine = e.message }
    try { validateImport({ format: 'other' }) } catch (e) { out.foreign = e.message }
    const v = validateImport(envelope('instrument', 'X', { engine: 'fusion', params: { L0on: true, L0src: 'not-installed', L1on: false }, mod: { sources: [{ type: 'evil', code: 'alert(1)' }], routes: [] } }));
    out.deps = v.deps.map(d => d.id); out.warn = v.warn.length; out.src = v.obj.data.mod.sources.length; return out });
  ok('newer, foreign and broken presets are rejected with a clear reason', /newer Soundcraft/.test(imp.newer || '') && /engine/.test(imp.engine || '') && /not a Soundcraft/.test(imp.foreign || ''), JSON.stringify(imp));
  ok('missing dependencies are reported and unknown sources dropped', imp.deps.includes('not-installed') && imp.src === 0 && imp.warn >= 1);
  // undo and recovery
  const ur = await E(() => { const n0 = S.tracks.length; pushUndo(); disposeTrack(S.tracks[0]); S.tracks.splice(0, 1); undo(); const n1 = S.tracks.length; takeSnapshot('t', ''); S.bpm = 77; restoreSnapshot(LIB[curId].snapshots.find(s => s.name === 't').id); return { n0, n1, bpm: S.bpm, safety: LIB[curId].snapshots.some(s => /Before restoring/.test(s.name)) } });
  ok('undo and snapshots restore work, with an automatic safety snapshot', ur.n0 === ur.n1 && ur.bpm !== 77 && ur.safety, JSON.stringify(ur));
  // preset changes are audible and match saved params
  const pa = await E(async () => { loadProject(T.oneTrack('syn-pluck', [{ p: 60, s: 0, l: 8, v: 1 }])); const a = await renderOffline({ startBar: 0, endBar: 1, tail: 0 }); const savedA = S.tracks[0].params.aS === FACTORY_BY_ID['syn-pluck'].params.aS;
    loadProject(T.oneTrack('syn-pad', [{ p: 60, s: 0, l: 8, v: 1 }])); const b = await renderOffline({ startBar: 0, endBar: 1, tail: 0 }); const savedB = S.tracks[0].params.aA === FACTORY_BY_ID['syn-pad'].params.aA;
    return { pluckEarly: +T.rms(a, 0.01, 0.15).toFixed(4), pluckLate: +T.rms(a, 0.8, 1).toFixed(4), padEarly: +T.rms(b, 0.01, 0.15).toFixed(4), padLate: +T.rms(b, 0.8, 1).toFixed(4), saved: savedA && savedB } });
  ok('different presets render differently and use their saved settings', pa.pluckEarly > pa.pluckLate * 3 && pa.padLate > pa.padEarly * 2 && pa.saved, JSON.stringify(pa));
  // every effect processes audio
  const fxr = await E(async () => { const out = {}; for (const k of FX_ORDER) { loadProject(T.oneTrack('syn-poly', [{ p: 60, s: 0, l: 16, v: 1 }], t => { t.fx = [newFx(k)] })); const b = await renderOffline({ startBar: 0, endBar: 1, tail: 0.5 }); out[k] = +T.rms(b, 0.2, 1.5).toFixed(3) } return out });
  ok('every effect renders audio without breaking the chain', Object.values(fxr).every(v => v > 0.002), JSON.stringify(fxr));
  // every engine makes sound
  const eng = await E(async () => { const out = {}; for (const k of Object.keys(ENG)) { if (k === 'sampler' || ENG[k].hidden) continue; const pre = FACTORY.find(p => p.engine === k); loadProject(T.oneTrack(pre, [{ p: k === 'kit' ? 36 : 60, s: 0, l: 8, v: 1 }])); const b = await renderOffline({ startBar: 0, endBar: 1, tail: 0.5 }); out[k] = +T.rms(b, 0, 2).toFixed(4) } return out });
  ok('every sound engine produces audio', Object.values(eng).every(v => v > 0.001), JSON.stringify(eng));
  // instrument quality: tuning, distinct timbres, real ensembles
  const q = await E(async () => {
    const f0 = (buf, a, b, n) => { const sr = buf.sampleRate, d = buf.getChannelData(0).slice(Math.floor(a * sr), Math.floor(b * sr)), fe = 440 * Math.pow(2, (n - 69) / 12), L = Math.min(d.length, 12000), lo = Math.floor(sr / (fe * 1.2)), hi = Math.ceil(sr / (fe / 1.2));
      const C = lag => { let s = 0, e1 = 0, e2 = 0; for (let i = 0; i < L - lag; i++) { s += d[i] * d[i + lag]; e1 += d[i] * d[i]; e2 += d[i + lag] * d[i + lag] } return s / Math.sqrt(e1 * e2 + 1e-12) };
      let bl = lo, bc = -1; for (let lag = lo; lag <= hi; lag++) { const c = C(lag); if (c > bc) { bc = c; bl = lag } } if (bc < 0.5) return null; const y0 = C(bl - 1), y2 = C(bl + 1), den = y0 - 2 * bc + y2; return Math.round(1200 * Math.log2(sr / (bl + (Math.abs(den) > 1e-9 ? 0.5 * (y0 - y2) / den : 0)) / fe)) };
    const bright = buf => { const d = buf.getChannelData(0), sr = buf.sampleRate, s0 = Math.floor(0.25 * sr), e = Math.min(d.length, s0 + sr); let a = 0, q = 0; for (let i = s0 + 1; i < e; i++) { a += d[i] * d[i]; const x = d[i] - d[i - 1]; q += x * x } return Math.round(Math.sqrt(q / a) * sr / (2 * Math.PI)) };
    const att = buf => { const d = buf.getChannelData(0), sr = buf.sampleRate, s0 = Math.floor(0.004 * sr), e = Math.floor(0.08 * sr); let a = 0, q = 0; for (let i = s0 + 1; i < e; i++) { a += d[i] * d[i]; const x = d[i] - d[i - 1]; q += x * x } return Math.round(Math.sqrt(q / a) * sr / (2 * Math.PI)) };
    const ring = buf => +(T.rms(buf, 1.0, 1.4) / Math.max(1e-6, T.rms(buf, 0.02, 0.2))).toFixed(3);
    const one = async (pre, n, v = 0.8) => { loadProject(T.oneTrack(pre, [{ p: n, s: 0, l: 12, v }])); return await renderOffline({ startBar: 0, endBar: 1, sampleRate: 44100, tail: 0.2 }) };
    const out = { tune: {}, bright: {}, rms: {} };
    for (const [pre, ns] of [['keys-prism', [48, 60, 72, 84, 96]], ['keys-celesta', [60, 84]], ['brass-trumpet', [60, 74]], ['brass-horn', [48, 62]], ['brass-trombone', [43, 57]], ['brass-tuba', [38, 45]], ['str-violin', [67, 84]], ['str-cello', [40, 55]], ['ww-oboe', [65, 77]]]) {
      out.tune[pre] = []; for (const n of ns) { const b = await one(pre, n); out.tune[pre].push(f0(b, pre === 'keys-celesta' || pre === 'keys-prism' ? 0.15 : 0.4, pre === 'keys-celesta' || pre === 'keys-prism' ? 0.45 : 0.7, n)) } }
    for (const [pre, n] of [['brass-trumpet', 58], ['brass-horn', 58], ['brass-trombone', 58], ['brass-tuba', 45], ['brass-mutedtp', 58], ['keys-celesta', 84], ['keys-musicbox', 84], ['keys-organ', 60], ['keys-jazzorgan', 60], ['keys-gospel', 60], ['str-violin', 72], ['str-viola', 62], ['str-cello', 50], ['str-bass', 40]]) { const b = await one(pre, n); out.bright[pre] = bright(b); out.rms[pre] = +T.rms(b, 0, 1.5).toFixed(3); out.att = out.att || {}; out.att[pre] = att(b); out.ring = out.ring || {}; out.ring[pre] = ring(b) }
    loadProject(T.oneTrack('brass-section', [43, 55, 64, 72].map(p => ({ p, s: 0, l: 12, v: 0.8 })))); ensureAudio(); await ensureProjectOrch(); const t = S.tracks[0]; buildTrack(t);
    const now = ctx.currentTime + 0.05, hs = [43, 55, 64, 72].map(n => trigger(t, n, 0.8, now)); out.parts = Object.values(t._secLoad || {}).filter(x => x > 0).length; hs.forEach(h => h && h.off(ctx.currentTime));
    out.recordedZones = Object.values(SAMPLE_LIB).reduce((a, l) => a + Object.values(l.arts).reduce((s, x) => s + x.zones.length, 0), 0); out.loadFailures = ORCH.failed.size; return out });
  const allTune = Object.values(q.tune).flat();
  ok('bells, celesta and recorded instruments play in tune (A4 = 440 Hz, within 10 cents)', allTune.every(c => c != null && Math.abs(c) <= 10), JSON.stringify(q.tune));
  const B = q.bright, dif = (a, b) => Math.abs(B[a] - B[b]) / Math.max(B[a], B[b]);
  ok('the five brass instruments have distinct timbres', ['brass-trumpet', 'brass-horn', 'brass-trombone', 'brass-tuba', 'brass-mutedtp'].every((a, i, l) => l.slice(i + 1).every(b => dif(a, b) > 0.08)) , JSON.stringify(['brass-trumpet', 'brass-horn', 'brass-trombone', 'brass-tuba', 'brass-mutedtp'].map(k => k + ' ' + B[k])));
  const ca = q.att['keys-celesta'], ma = q.att['keys-musicbox'], cr = q.ring['keys-celesta'], mr = q.ring['keys-musicbox'];
  ok('Celesta and Music Box sound different (attack spectrum and ring time)', Math.abs(ca - ma) / Math.max(ca, ma) > 0.25 && Math.abs(cr - mr) / Math.max(cr, mr) > 0.25, 'attack brightness ' + ca + ' vs ' + ma + ' Hz, ring ' + cr + ' vs ' + mr);
  ok('the three organs are distinct (brightness or level differ clearly)', [['keys-organ', 'keys-jazzorgan'], ['keys-organ', 'keys-gospel'], ['keys-jazzorgan', 'keys-gospel']].every(([a, b]) => dif(a, b) > 0.15 || Math.abs(q.rms[a] - q.rms[b]) / Math.max(q.rms[a], q.rms[b]) > 0.3), JSON.stringify(['keys-organ', 'keys-jazzorgan', 'keys-gospel'].map(k => k + ' ' + B[k] + 'Hz rms ' + q.rms[k])));
  ok('the string family is differentiated by register and timbre', ['str-violin', 'str-viola', 'str-cello', 'str-bass'].every((a, i, l) => l.slice(i + 1).every(b => dif(a, b) > 0.05)), JSON.stringify(['str-violin', 'str-viola', 'str-cello', 'str-bass'].map(k => k + ' ' + B[k])));
  ok('Brass Section spreads a chord across different recorded players', q.parts >= 3 && q.loadFailures === 0, q.parts + ' parts playing · ' + q.recordedZones + ' recorded zones · load failures ' + q.loadFailures);
  // stability with modulation active, and timing
  await E(() => { openProject(Object.values(LIB).find(e => e.title === 'Midnight Rain').id); const t = S.tracks[3]; const s = newSource('lfo'); s.sync = '1/8'; t.mod.sources.push(s); t.mod.routes.push(newRoute(s.id, 'v:vpan', 0.6), newRoute(s.id, 'v:vcut', -0.4)); buildTrackMod(t) });
  await page.click('#btnPlay'); await page.waitForTimeout(3500);
  const st = await E(() => { const l = stepLog.slice(-40); let m = 0; for (let i = 1; i < l.length; i++)m = Math.max(m, Math.abs((l[i][1] - l[i - 1][1]) - l[i - 1][2])); return { drift: m, voices: liveVoices(), level: A.level } });
  ok('audio stays stable with modulation running (no drift, bounded voices)', st.drift < 1e-6 && st.voices < 120 && st.level > 0.01, JSON.stringify(st));
  await E(() => setView('studio')); await page.waitForTimeout(800);
  ok('studio visuals read the real analysis', await E(() => A.bass > 0.05 && lampPulse > 0.2));
  await page.screenshot({ path: OUT + '/studio.png' });
  await page.click('#btnStop');
  // export a real file
  await E(() => setView('arrange')); await page.click('#btnExport'); await page.selectOption('#expRange', 'loop'); await page.selectOption('#expRate', '48000'); await page.selectOption('#expBits', '24');
  const dl = page.waitForEvent('download', { timeout: 60000 }); await page.click('#expGo'); const d = await dl; const fp = OUT + '/' + d.suggestedFilename(); await d.saveAs(fp);
  const buf = fs.readFileSync(fp); let peak = 0; for (let i = 44; i + 2 < buf.length; i += 3) { let v = buf[i] | (buf[i + 1] << 8) | (buf[i + 2] << 16); if (v & 0x800000) v -= 0x1000000; peak = Math.max(peak, Math.abs(v)) }
  ok('export writes a playable 48 kHz / 24-bit WAV with real audio', buf.toString('ascii', 0, 4) === 'RIFF' && buf.readUInt32LE(24) === 48000 && buf.readUInt16LE(34) === 24 && peak > 100000, d.suggestedFilename() + ' ' + buf.length + ' bytes, peak ' + peak);
  await page.keyboard.press('Escape');
  // MIDI file round trip
  const mid = await E(() => { const bytes = exportMidi(); const parsed = parseMidi(bytes.buffer); return { tracks: parsed.tracks.length, notes: parsed.tracks.reduce((a, t) => a + t.notes.length, 0), tempo: Math.round(parsed.tempo) } });
  ok('MIDI file export parses back with notes and tempo', mid.tracks >= 4 && mid.notes > 50 && mid.tempo === 112, JSON.stringify(mid));
  // worklets (plucked strings, gate, crusher, transient, mic recorder)
  ok('AudioWorklets load', await E(async () => { ensureAudio(); await loadWorklets(ctx); return workletOK() }));
  // mic track: record a take from the (fake) microphone, play it back, export it, undo it
  await E(() => { setView('arrange'); S.loop = { on: true, start: 0, end: 2 }; S.cursor = 0; renderTransport() });
  await page.click('#btnAddMic'); await page.waitForTimeout(1200);
  const micOk = await E(() => ({ open: micOpen(), audio: isAudio(selTrack()), dock: !$('#dockAudio').hidden, armed: selTrack().arm }));
  ok('a mic track opens the input, is armed and shows its recording panel', micOk.open && micOk.audio && micOk.dock && micOk.armed, JSON.stringify(micOk));
  await E(() => toggleRec()); await page.waitForTimeout(3200); await E(() => stop()); await page.waitForTimeout(1500);
  const take = await E(async () => { const t = S.tracks.find(isAudio), c = t.clips.filter(c => c.rec); const b = c[0] && SAMPLE_BUF.get(c[0].rec);
    const solo = S.tracks.map(x => x.mute); S.tracks.forEach(x => x.mute = !isAudio(x)); const r = await renderOffline({ startBar: 0, endBar: 2, sampleRate: 44100, tail: 0 }); S.tracks.forEach((x, i) => x.mute = solo[i]);
    const stored = c[0] ? !!(await getSample(c[0].rec)) : false; return { clips: c.length, dur: b ? +b.duration.toFixed(2) : 0, rms: +T.rms(r, 0, 3.5).toFixed(4), stored, first: c[0] && +c[0].start.toFixed(3) } });
  ok('recording captures a take into clips that play back and render in export', take.clips >= 1 && take.dur > 2 && take.rms > 0.005 && take.stored, JSON.stringify(take));
  await page.screenshot({ path: OUT + '/mic-track.png' });
  const und = await E(() => { const t = S.tracks.find(isAudio), n0 = t.clips.length; undo(); const n1 = S.tracks.find(isAudio).clips.length; redo(); return { n0, n1, n2: S.tracks.find(isAudio).clips.length } });
  ok('undo removes a recorded take and redo restores it', und.n0 > 0 && und.n1 === 0 && und.n2 === und.n0, JSON.stringify(und));
  // record straight into the Sampler, and turn a take into a Sampler instrument
  await E(() => { addTrackFrom(presetById('sampler-empty')); selectTrack(S.tracks.length - 1); setView('lab') }); await page.waitForTimeout(400);
  await page.click('#zoneRec'); await page.waitForTimeout(1600); await page.click('#zoneRec'); await page.waitForTimeout(1500);
  const zr = await E(async () => { const t = selTrack(), z = t.zones[0]; const pt = curPattern(t); pt.notes = [{ p: 60, s: 0, l: 8, v: 1 }]; t.clips = [{ id: 'cz', pat: pt.id, start: 0, len: 1 }]; const m = S.tracks.map(x => x.mute); S.tracks.forEach(x => x.mute = x !== t);
    const b = await renderOffline({ startBar: 0, endBar: 1, sampleRate: 44100, tail: 0 }); S.tracks.forEach((x, i) => x.mute = m[i]); return { zones: t.zones.length, stored: z ? !!(await getSample(z.sample)) : false, rms: +T.rms(b, 0, 2).toFixed(4), dur: z && +SAMPLE_BUF.get(z.sample).duration.toFixed(2) } });
  ok('“Record sample” records the microphone into a Sampler zone that plays', zr.zones === 1 && zr.stored && zr.rms > 0.003, JSON.stringify(zr));
  const ts = await E(async () => { const t = S.tracks.find(isAudio), c = t.clips.find(c => c.rec); const n0 = S.tracks.length; await takeToSampler(t, c); const s = S.tracks[S.tracks.length - 1]; return { added: S.tracks.length === n0 + 1, engine: s.engine, zones: (s.zones || []).length } });
  ok('a recorded take becomes a Sampler instrument', ts.added && ts.engine === 'sampler' && ts.zones === 1, JSON.stringify(ts));
  await E(() => setView('arrange'));
  // session file round trip (whole session in one file) and the History panel
  const ses = await E(async () => { const text = await buildSessionFile(), j = JSON.parse(text); const before = { tracks: S.tracks.length, undo: undoStack.length, id: curId, title: LIB[curId].title };
    const takes = Object.keys(j.samples || {}).length; j.session.uid = 'other-session'; j.name = 'Moved Session'; await openSessionData(j, null, 'Moved Session.soundcraft');
    return { ext: SESSION_EXT, format: j.format, type: j.type, takes, newId: curId !== before.id, title: LIB[curId].title, tracks: S.tracks.length, before, undo: undoStack.length, audio: S.tracks.some(t => t.clips.some(c => c.rec && SAMPLE_BUF.has(c.rec))) } });
  ok('a .soundcraft file holds the whole session and reopens it with recordings and undo history', ses.format === 'soundcraft' && ses.type === 'project' && ses.takes >= 1 && ses.newId && ses.title === 'Moved Session' && ses.tracks === ses.before.tracks && ses.undo === Math.min(60, ses.before.undo) && ses.audio, JSON.stringify(ses));
  await E(() => { pushUndo(); S.bpm = 131; renderTransport(); markDirty(); openHistory() }); await page.waitForTimeout(300);
  const hist = await E(() => ({ sessions: $$('#histSessions li').length, steps: $$('#histSteps li.done').length, top: ($('#histSteps li.done') || {}).textContent || '' }));
  ok('History lists recent sessions and describes each change', hist.sessions >= 2 && hist.steps >= 2 && /Tempo .* 131 BPM/.test(hist.top), JSON.stringify(hist).slice(0, 200));
  await page.locator('#histSteps li.done button').first().click(); await page.waitForTimeout(200);
  const back = await E(() => ({ bpm: S.bpm, redo: redoStack.length }));
  ok('History can go back to before any change, and redo forward again', back.bpm !== 131 && back.redo >= 1, JSON.stringify(back));
  await page.keyboard.press('Escape');
  // settings
  await E(() => openSettings()); await page.waitForTimeout(400);
  const tabs = await E(async () => { const names = $$('.set-nav button').map(b => b.textContent); const vis = []; for (const b of $$('.set-nav button')) { b.click(); vis.push($$('.set-sec').filter(s => !s.hidden).length) } return { names, vis } });
  ok('Settings has tabbed sections including General, MIDI and Recording', ['General', 'MIDI', 'Recording', 'Audio', 'Display'].every(n => tabs.names.includes(n)) && tabs.vis.every(v => v === 1), JSON.stringify(tabs));
  await page.keyboard.press('Escape');
  const hsig = await E(async () => { await persistHistory(); return undoStack.length });
  // save, close, reopen
  const sig = await E(() => { saveNow(); return JSON.stringify(S.tracks.map(t => [t.engine, t.mod.routes.length, t.fx.length, t.patterns.reduce((a, p) => a + p.notes.length, 0)])) + curId });
  await page.close(); const p2 = await ctx.newPage(); p2.on('pageerror', e => errors.push(e.message)); await p2.goto(URL()); await p2.waitForTimeout(1000);
  const sig2 = await p2.evaluate(() => JSON.stringify(S.tracks.map(t => [t.engine, t.mod.routes.length, t.fx.length, t.patterns.reduce((a, p) => a + p.notes.length, 0)])) + curId);
  ok('the project reopens with notes, modulation and effects intact', sig === sig2, sig2.slice(0, 120));
  await p2.waitForTimeout(500); const h2 = await p2.evaluate(() => undoStack.length);
  ok('undo history survives closing and reopening Soundcraft', h2 === hsig && h2 > 0, hsig + ' → ' + h2);
  // click every visible button in every workspace (destructive ones excluded) and watch for errors
  const skip = /Delete|Clear|Restore|New cassette|Overwrite|Revert|Import|Load reference|Export|Download|Render|Save report|Copy report|Apply and restart|Scan again|Start the tour/i;
  let clicked = 0;
  for (const v of ['arrange', 'mix', 'sounds', 'lab']) {
    await p2.evaluate(v => setView(v), v); await p2.waitForTimeout(400);
    const n = await p2.locator('.view:visible button:visible').count();
    for (let i = 0; i < Math.min(n, 60); i++) { if (i >= await p2.locator('.view:visible button:visible').count()) break; const b = p2.locator('.view:visible button:visible').nth(i); const t = (await b.innerText({ timeout: 500 }).catch(() => '')).trim(); if (skip.test(t)) continue; if (process.env.SWEEP_LOG) console.log('  click', v, i, t.slice(0, 30)); await b.click({ timeout: 1500 }).catch(() => { }); clicked++; await p2.keyboard.press('Escape'); if (p2.isClosed()) break }
  }
  ok('clicking every visible control raises no errors', errors.length === 0, clicked + ' buttons clicked; ' + (errors.slice(0, 3).join(' | ') || 'no errors'));
  await p2.setViewportSize({ width: 390, height: 844 }); for (const v of ['studio', 'arrange', 'mix', 'sounds', 'lab']) { await p2.evaluate(v => setView(v), v); await p2.waitForTimeout(250) }
  const ov = await p2.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  ok('no horizontal page scroll at phone width', ov <= 0, 'overflow ' + ov);
  console.log((results.length - failed) + '/' + results.length + ' passed · screenshots in ' + OUT);
  await browser.close(); process.exit(failed ? 1 : 0);
})().catch(e => { console.log(results.join('\n')); console.error('TEST CRASH', e); process.exit(2) });
