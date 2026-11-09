const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('mc', {
  state: () => ipcRenderer.invoke('state'),
  login: () => ipcRenderer.invoke('login'),
  silent: () => ipcRenderer.invoke('silent'),
  logout: () => ipcRenderer.invoke('logout'),
  win: (w) => ipcRenderer.invoke('win', w),
  open: (u) => ipcRenderer.invoke('open', u)
});
