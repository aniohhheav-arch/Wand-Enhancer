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
  onQuit: fn => ipcRenderer.on('app-quit', () => fn())
});
