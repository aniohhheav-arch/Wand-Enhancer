'use strict';
/* The only bridge between the page and the desktop: a fixed list of narrow functions. */
const { contextBridge, ipcRenderer } = require('electron');
const version = ipcRenderer.sendSync('app-version');
contextBridge.exposeInMainWorld('native', {
  version,
  electron: process.versions.electron,
  saveFile: (name, data) => ipcRenderer.invoke('save-file', String(name), data),
  saveFiles: files => ipcRenderer.invoke('save-files', files.map(f => ({ name: String(f.name), data: f.data }))),
  writeBackup: (id, title, json) => ipcRenderer.invoke('write-backup', String(id), String(title), String(json)),
  backupDir: () => ipcRenderer.invoke('backup-dir'),
  openBackups: () => ipcRenderer.invoke('open-backups'),
  cpu: () => ipcRenderer.invoke('cpu'),
  onQuit: fn => ipcRenderer.on('app-quit', () => fn()),
  rendererReady: () => ipcRenderer.invoke('renderer-ready'),
  readAsset: rel => ipcRenderer.invoke('read-asset', String(rel)),
  openSessionDialog: () => ipcRenderer.invoke('open-session-dialog'),
  readSession: p => ipcRenderer.invoke('read-session', String(p)),
  saveSession: (p, name, text) => ipcRenderer.invoke('save-session', p ? String(p) : null, String(name), String(text)),
  fileExists: p => ipcRenderer.invoke('file-exists', String(p)),
  onOpenSession: fn => ipcRenderer.on('open-session', (e, r) => fn(r)),
  onMenu: fn => ipcRenderer.on('menu', (e, c) => fn(String(c)))
});
