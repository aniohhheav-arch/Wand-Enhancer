'use strict';
/* Soundcraft desktop shell. The app itself is the web app in ../app; this process only owns the
   window, file dialogs, on-disk backups, logs and permissions. The renderer has no Node access. */
const { app, BrowserWindow, ipcMain, dialog, shell, session, Menu } = require('electron');
const path = require('path'), fs = require('fs');

if (!app.requestSingleInstanceLock()) { app.quit(); }
// Keep timers and MIDI handling at full speed when the window is behind another app (playing along to a video, a
// tutorial or a second screen); otherwise Chromium throttles the renderer and live notes arrive late.
app.commandLine.appendSwitch('disable-renderer-backgrounding');
app.commandLine.appendSwitch('disable-background-timer-throttling');
const ROOT = path.join(__dirname, '..');
const userDir = () => app.getPath('userData');
const logDir = () => path.join(userDir(), 'Logs');
const backupRoot = () => path.join(userDir(), 'Backups');
const exportDir = () => path.join(app.getPath('documents'), 'Soundcraft');
function log(...a) { try { fs.mkdirSync(logDir(), { recursive: true }); fs.appendFileSync(path.join(logDir(), 'main.log'), new Date().toISOString() + ' ' + a.join(' ') + '\n'); } catch (e) { } }
process.on('uncaughtException', e => { log('uncaught', e.stack || e.message); });

/* .soundcraft session files: opened by double-click (file association from the installer, or "Open with" for
   the portable exe), by dropping on the exe, from the File menu, or from the History panel. */
const SESSION_EXT = '.soundcraft';
const isSessionPath = p => typeof p === 'string' && p.toLowerCase().endsWith(SESSION_EXT);
const sessionArg = argv => (argv || []).slice(1).find(a => isSessionPath(a) && !a.startsWith('-') && fs.existsSync(a));
let pendingOpen = [], rendererReady = false;
const first = sessionArg(process.argv); if (first) pendingOpen.push(path.resolve(first));
async function readSession(p) {
  const st = await fs.promises.stat(p); if (st.size > 1024 * 1024 * 1024) throw new Error('the file is larger than 1 GB');
  return { path: p, text: await fs.promises.readFile(p, 'utf8') };
}
function deliverOpen(p) {
  if (!win || !rendererReady) { pendingOpen.push(p); return; }
  readSession(p).then(r => { app.addRecentDocument(p); win.webContents.send('open-session', r); }).catch(err => win.webContents.send('open-session', { path: p, error: err.message }));
  if (win.isMinimized()) win.restore(); win.focus();
}
app.on('open-file', (e, p) => { e.preventDefault(); if (isSessionPath(p)) deliverOpen(p); });

let win = null;
function createWindow() {
  win = new BrowserWindow({
    width: 1440, height: 920, minWidth: 900, minHeight: 600, show: false, backgroundColor: '#141330', title: 'Soundcraft',
    icon: path.join(ROOT, 'build', 'icon.png'),
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, sandbox: true, nodeIntegration: false, autoplayPolicy: 'no-user-gesture-required', spellcheck: false, backgroundThrottling: false }
  });
  win.loadFile(path.join(ROOT, 'app', 'index.html'));
  win.webContents.on('did-start-loading', () => { rendererReady = false; });
  win.once('ready-to-show', () => win.show());
  win.webContents.setWindowOpenHandler(({ url }) => { if (/^https:\/\//.test(url)) shell.openExternal(url); return { action: 'deny' }; });
  win.webContents.on('will-navigate', (e, url) => { if (!url.startsWith('file:')) { e.preventDefault(); if (/^https:\/\//.test(url)) shell.openExternal(url); } });
  win.webContents.on('render-process-gone', (e, d) => {
    log('renderer gone', d.reason, d.exitCode);
    if (d.reason === 'clean-exit') return;
    dialog.showMessageBox(win, { type: 'error', title: 'Soundcraft', message: 'The Soundcraft window stopped unexpectedly (' + d.reason + ').', detail: 'Your project was autosaved up to the last change. Soundcraft will reload now. Older versions are in Snapshots → Automatic backups.' }).then(() => { if (!win.isDestroyed()) win.reload(); });
  });
  win.webContents.on('console-message', (e, level, message) => { if (level >= 3) log('renderer error', message); });
  win.on('close', () => { try { win.webContents.send('app-quit'); } catch (e) { } });
}

function menuCmd(c) { if (win) win.webContents.send('menu', c); }
function buildMenu() {
  const tpl = [
    { label: 'File', submenu: [
      { label: 'Open session…', accelerator: 'CmdOrCtrl+O', registerAccelerator: false, click: () => menuCmd('open') },
      { label: 'Save', accelerator: 'CmdOrCtrl+S', registerAccelerator: false, click: () => menuCmd('save') },
      { label: 'Save session as…', accelerator: 'CmdOrCtrl+Shift+S', registerAccelerator: false, click: () => menuCmd('saveAs') },
      { type: 'separator' },
      { label: 'History and sessions…', accelerator: 'CmdOrCtrl+H', registerAccelerator: false, click: () => menuCmd('history') },
      { label: 'Settings…', accelerator: 'CmdOrCtrl+,', registerAccelerator: false, click: () => menuCmd('settings') },
      { type: 'separator' }, { role: 'quit', label: 'Quit Soundcraft' }] },
    { label: 'View', submenu: [{ role: 'reload' }, { role: 'togglefullscreen' }, { role: 'resetZoom' }, { role: 'zoomIn' }, { role: 'zoomOut' }].concat(app.isPackaged ? [] : [{ role: 'toggleDevTools' }]) },
    { label: 'Help', submenu: [
      { label: 'Open backups folder', click: () => { fs.mkdirSync(backupRoot(), { recursive: true }); shell.openPath(backupRoot()); } },
      { label: 'Open logs folder', click: () => { fs.mkdirSync(logDir(), { recursive: true }); shell.openPath(logDir()); } },
      { label: 'About Soundcraft', click: () => dialog.showMessageBox(win, { title: 'Soundcraft', message: 'Soundcraft ' + app.getVersion(), detail: 'Electron ' + process.versions.electron + ' · Chromium ' + process.versions.chrome + '\nData: ' + userDir() }) }
    ] }
  ];
  Menu.setApplicationMenu(Menu.buildFromTemplate(tpl));
}

const safeName = s => String(s || 'file').replace(/[^\w\-. ()]+/g, '_').slice(0, 120);
const FILTERS = { wav: ['WAV audio', ['wav']], zip: ['Zip archive', ['zip']], mid: ['MIDI file', ['mid']], json: ['Soundcraft file', ['json']], txt: ['Text', ['txt']] };
ipcMain.on('app-version', e => { e.returnValue = app.getVersion(); });
ipcMain.handle('save-file', async (e, name, data) => {
  try {
    const ext = path.extname(name).slice(1).toLowerCase(), f = FILTERS[ext];
    fs.mkdirSync(exportDir(), { recursive: true });
    const r = await dialog.showSaveDialog(win, { defaultPath: path.join(exportDir(), safeName(name)), filters: f ? [{ name: f[0], extensions: f[1] }] : [] });
    if (r.canceled || !r.filePath) return { canceled: true };
    await fs.promises.writeFile(r.filePath, Buffer.from(data));
    return { path: r.filePath };
  } catch (err) { log('save-file', err.message); return { error: err.message }; }
});
ipcMain.handle('save-files', async (e, files) => {
  try {
    fs.mkdirSync(exportDir(), { recursive: true });
    const r = await dialog.showOpenDialog(win, { title: 'Choose a folder for the exported files', defaultPath: exportDir(), properties: ['openDirectory', 'createDirectory'] });
    if (r.canceled || !r.filePaths[0]) return { canceled: true };
    const dir = r.filePaths[0], written = [];
    for (const f of files) {
      let p = path.join(dir, safeName(f.name)), i = 2; const ext = path.extname(p), base = p.slice(0, -ext.length);
      while (fs.existsSync(p)) p = base + ' (' + (i++) + ')' + ext; // never overwrite an existing file
      await fs.promises.writeFile(p, Buffer.from(f.data)); written.push(p);
    }
    return { dir, files: written };
  } catch (err) { log('save-files', err.message); return { error: err.message }; }
});
ipcMain.handle('write-backup', async (e, id, title, json) => {
  if (typeof json !== 'string' || json.length > 50 * 1024 * 1024) return false;
  const dir = path.join(backupRoot(), safeName(title) + '-' + safeName(id));
  await fs.promises.mkdir(dir, { recursive: true });
  await fs.promises.writeFile(path.join(dir, new Date().toISOString().replace(/[:.]/g, '-') + '.json'), json);
  const all = (await fs.promises.readdir(dir)).filter(f => f.endsWith('.json')).sort();
  for (const f of all.slice(0, Math.max(0, all.length - 20))) await fs.promises.unlink(path.join(dir, f)).catch(() => { });
  return true;
});
ipcMain.handle('backup-dir', () => backupRoot());
// Bundled recordings (app/samples/*.ogg) only; anything else is refused.
const SAMPLE_DIR = path.join(ROOT, 'app', 'samples');
ipcMain.handle('read-asset', async (e, rel) => {
  const p = path.normalize(path.join(ROOT, 'app', String(rel)));
  if (!p.startsWith(SAMPLE_DIR + path.sep) || !p.endsWith('.ogg')) throw new Error('not a bundled sample');
  const b = await fs.promises.readFile(p); return b.buffer.slice(b.byteOffset, b.byteOffset + b.byteLength);
});
ipcMain.handle('renderer-ready', () => { rendererReady = true; const l = pendingOpen; pendingOpen = []; return Promise.all(l.map(p => readSession(p).then(r => { app.addRecentDocument(p); return r; }).catch(err => ({ path: p, error: err.message })))); });
ipcMain.handle('open-session-dialog', async () => {
  try {
    fs.mkdirSync(exportDir(), { recursive: true });
    const r = await dialog.showOpenDialog(win, { title: 'Open a Soundcraft session', defaultPath: exportDir(), properties: ['openFile'], filters: [{ name: 'Soundcraft session', extensions: ['soundcraft'] }, { name: 'Older project files', extensions: ['json'] }] });
    if (r.canceled || !r.filePaths[0]) return { canceled: true };
    const res = await readSession(r.filePaths[0]); app.addRecentDocument(res.path); return res;
  } catch (err) { log('open-session', err.message); return { error: err.message }; }
});
ipcMain.handle('read-session', async (e, p) => { if (!isSessionPath(p)) return { error: 'not a .soundcraft file' }; try { return await readSession(p); } catch (err) { return { path: p, error: err.message }; } });
// Writes go to a temporary file first and are renamed into place, so a crash mid-save never leaves half a session.
ipcMain.handle('save-session', async (e, p, name, text) => {
  try {
    if (typeof text !== 'string') return { error: 'nothing to save' };
    if (!isSessionPath(p)) {
      fs.mkdirSync(exportDir(), { recursive: true });
      const r = await dialog.showSaveDialog(win, { title: 'Save session', defaultPath: path.join(exportDir(), safeName(String(name || 'Session').replace(/\.soundcraft$/i, '')) + SESSION_EXT), filters: [{ name: 'Soundcraft session', extensions: ['soundcraft'] }] });
      if (r.canceled || !r.filePath) return { canceled: true };
      p = isSessionPath(r.filePath) ? r.filePath : r.filePath + SESSION_EXT;
    }
    const tmp = p + '.saving'; await fs.promises.writeFile(tmp, text, 'utf8'); await fs.promises.rename(tmp, p); app.addRecentDocument(p);
    return { path: p };
  } catch (err) { log('save-session', err.message); return { error: err.message }; }
});
ipcMain.handle('file-exists', (e, p) => isSessionPath(p) && fs.existsSync(p));
ipcMain.handle('open-backups', () => { fs.mkdirSync(backupRoot(), { recursive: true }); return shell.openPath(backupRoot()); });
ipcMain.handle('cpu', () => app.getAppMetrics().reduce((a, m) => a + (m.cpu ? m.cpu.percentCPUUsage : 0), 0));

app.on('second-instance', (e, argv) => { const f = sessionArg(argv); if (f) deliverOpen(path.resolve(f)); else if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.whenReady().then(() => {
  // Chromium in Electron 33 asks for 'midiSysex' even for plain requestMIDIAccess({sysex:false});
  // Soundcraft never sends or reads SysEx messages, but the permission must be granted for MIDI to work at all.
  // 'media' is granted for audio input only (mic tracks); camera requests are refused.
  const allowed = new Set(['midi', 'midiSysex', 'clipboard-sanitized-write', 'speaker-selection']);
  session.defaultSession.setPermissionRequestHandler((wc, perm, cb, details) => {
    if (perm === 'media') return cb(!!details && Array.isArray(details.mediaTypes) && details.mediaTypes.length > 0 && details.mediaTypes.every(t => t === 'audio'));
    cb(allowed.has(perm));
  });
  session.defaultSession.setPermissionCheckHandler((wc, perm, origin, details) => perm === 'media' ? !!details && details.mediaType !== 'video' : allowed.has(perm));
  buildMenu(); createWindow(); log('started', app.getVersion(), process.platform, process.versions.electron);
  app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
});
app.on('window-all-closed', () => { if (process.platform !== 'darwin') app.quit(); });
