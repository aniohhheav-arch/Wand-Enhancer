/* Soundcraft acceptance tests. Drives the real app in headless Chromium and measures real audio.
   Usage: node soundcraft/tests/e2e.cjs [screenshot-dir]
   Set PLAYWRIGHT_MODULE to a playwright install if it is not resolvable from here. */
const { chromium } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const path = require('path'), fs = require('fs'), os = require('os');
const URL = 'file://' + path.resolve(__dirname, '../app/index.html');
const OUT = process.argv[2] || fs.mkdtempSync(path.join(os.tmpdir(), 'soundcraft-'));
const results = []; let failed = 0;
const ok = (name, cond, info = '') => { if (!cond) failed++; const line = (cond ? 'PASS ' : 'FAIL ') + name + (info ? ' — ' + info : ''); results.push(line); console.log(line); };
(async () => {
  const browser = await chromium.launch({ args: ['--autoplay-policy=no-user-gesture-required'] });
  const ctx = await browser.newContext({ viewport: { width: 1400, height: 950 }, acceptDownloads: true });
  const page = await ctx.newPage(); const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on('console', m => { if (m.type() === 'error' && !/favicon/.test(m.text())) errors.push(m.text()) });
  await page.goto(URL); await page.waitForTimeout(1000);
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
  const eng = await E(async () => { const out = {}; for (const k of Object.keys(ENG)) { if (k === 'sampler') continue; const pre = FACTORY.find(p => p.engine === k); loadProject(T.oneTrack(pre, [{ p: k === 'kit' ? 36 : 60, s: 0, l: 8, v: 1 }])); const b = await renderOffline({ startBar: 0, endBar: 1, tail: 0.5 }); out[k] = +T.rms(b, 0, 2).toFixed(4) } return out });
  ok('every sound engine produces audio', Object.values(eng).every(v => v > 0.001), JSON.stringify(eng));
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
  // save, close, reopen
  const sig = await E(() => { saveNow(); return JSON.stringify(S.tracks.map(t => [t.engine, t.mod.routes.length, t.fx.length, t.patterns.reduce((a, p) => a + p.notes.length, 0)])) + curId });
  await page.close(); const p2 = await ctx.newPage(); p2.on('pageerror', e => errors.push(e.message)); await p2.goto(URL); await p2.waitForTimeout(1000);
  const sig2 = await p2.evaluate(() => JSON.stringify(S.tracks.map(t => [t.engine, t.mod.routes.length, t.fx.length, t.patterns.reduce((a, p) => a + p.notes.length, 0)])) + curId);
  ok('the project reopens with notes, modulation and effects intact', sig === sig2, sig2.slice(0, 120));
  // click every visible button in every workspace (destructive ones excluded) and watch for errors
  const skip = /Delete|Clear|Restore|New cassette|Overwrite|Revert|Import|Load reference|Export|Download|Render|Save report|Copy report|Apply and restart|Scan again|Start the tour/i;
  let clicked = 0;
  for (const v of ['arrange', 'mix', 'sounds', 'lab']) {
    await p2.evaluate(v => setView(v), v); await p2.waitForTimeout(400);
    const n = await p2.locator('.view:visible button:visible').count();
    for (let i = 0; i < Math.min(n, 60); i++) { const b = p2.locator('.view:visible button:visible').nth(i); const t = (await b.innerText().catch(() => '')).trim(); if (skip.test(t)) continue; await b.click({ timeout: 1500 }).catch(() => { }); clicked++; await p2.keyboard.press('Escape'); if (p2.isClosed()) break }
  }
  ok('clicking every visible control raises no errors', errors.length === 0, clicked + ' buttons clicked; ' + (errors.slice(0, 3).join(' | ') || 'no errors'));
  await p2.setViewportSize({ width: 390, height: 844 }); for (const v of ['studio', 'arrange', 'mix', 'sounds', 'lab']) { await p2.evaluate(v => setView(v), v); await p2.waitForTimeout(250) }
  const ov = await p2.evaluate(() => document.documentElement.scrollWidth - innerWidth);
  ok('no horizontal page scroll at phone width', ov <= 0, 'overflow ' + ov);
  console.log((results.length - failed) + '/' + results.length + ' passed · screenshots in ' + OUT);
  await browser.close(); process.exit(failed ? 1 : 0);
})().catch(e => { console.log(results.join('\n')); console.error('TEST CRASH', e); process.exit(2) });
