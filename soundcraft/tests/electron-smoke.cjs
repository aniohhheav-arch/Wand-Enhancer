/* Desktop shell smoke test: launches the real Electron app (Linux build of the same code) under a display. */
const { _electron } = require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const path = require('path'), fs = require('fs'), os = require('os');
(async () => {
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'sc-el-')), res = [];
  const app = await _electron.launch({ args: [path.join(__dirname, '..'), '--no-sandbox', '--user-data-dir=' + tmp + '/ud'], executablePath: require(path.join(__dirname, '..', 'node_modules', 'electron')) });
  const win = await app.firstWindow(); const errors = []; win.on('pageerror', e => errors.push(e.message));
  await win.waitForTimeout(2500);
  res.push(['window title', await win.title()]);
  const info = await win.evaluate(() => ({ native: typeof window.native, keys: Object.keys(window.native || {}).sort().join(','), require: typeof window.require, process: typeof window.process, tracks: S.tracks.length, ver: window.native && window.native.version }));
  res.push(['native bridge only (no Node in page)', JSON.stringify(info)]);
  await app.evaluate(({ dialog }, out) => { dialog.showSaveDialog = async () => ({ canceled: false, filePath: out }); }, tmp + '/saved.wav');
  const saved = await win.evaluate(async () => window.native.saveFile('x.wav', new Uint8Array([82, 73, 70, 70, 1, 2, 3])));
  res.push(['save dialog writes the file', JSON.stringify(saved) + ' bytes on disk: ' + (fs.existsSync(tmp + '/saved.wav') ? fs.readFileSync(tmp + '/saved.wav').length : 0)]);
  const bk = await win.evaluate(async () => { await window.native.writeBackup('p1', 'My Song', '{"v":2}'); return window.native.backupDir() });
  const files = fs.readdirSync(bk).flatMap(d => fs.readdirSync(path.join(bk, d)));
  res.push(['backup written to disk', bk + ' → ' + files.length + ' file(s)']);
  const midi = await win.evaluate(async () => { try { const a = await navigator.requestMIDIAccess({ sysex: false }); return 'granted (' + a.inputs.size + ' inputs)' } catch (e) { return e.name + ': ' + e.message } });
  res.push(['MIDI permission granted (no NotAllowedError)', midi]);
  if (/NotAllowedError|SecurityError/.test(midi)) { console.error('MIDI PERMISSION REGRESSION: ' + midi); process.exitCode = 1; }
  res.push(['cpu stat', String(await win.evaluate(() => window.native.cpu()))]);
  await win.click('#btnPlay'); await win.waitForTimeout(1500); res.push(['plays audio in the desktop shell', String(await win.evaluate(() => A.level))]);
  res.push(['page errors', errors.length ? errors.join(' | ') : 'none']);
  await win.screenshot({ path: (process.argv[2] || tmp) + '/electron.png' });
  await app.close(); res.forEach(r => console.log(r[0] + ': ' + r[1]));
})().catch(e => { console.error('ELECTRON TEST FAILED', e); process.exit(1) });
