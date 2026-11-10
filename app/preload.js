const { contextBridge, ipcRenderer } = require('electron');
contextBridge.exposeInMainWorld('mc', {
  state: () => ipcRenderer.invoke('state'),
  login: () => ipcRenderer.invoke('login'),
  silent: () => ipcRenderer.invoke('silent'),
  saved: () => ipcRenderer.invoke('saved'),
  logout: () => ipcRenderer.invoke('logout'),
  win: (w) => ipcRenderer.invoke('win', w),
  open: (u) => ipcRenderer.invoke('open', u),
  updCheck: () => ipcRenderer.invoke('upd-check'),
  updInstall: () => ipcRenderer.invoke('upd-install'),
  onUpd: (cb) => ipcRenderer.on('upd', (e, s) => cb(s)),
  channel: () => ipcRenderer.invoke('channel-get'),
  setChannel: (c) => ipcRenderer.invoke('channel-set', c),
  launch: (server) => ipcRenderer.invoke('launch', server),
  openFolder: (w) => ipcRenderer.invoke('open-folder', w),
  userMods: () => ipcRenderer.invoke('user-mods'),
  versions: () => ipcRenderer.invoke('versions'),
  gameOpts: () => ipcRenderer.invoke('game-opts'),
  setGameOpts: (o) => ipcRenderer.invoke('game-opts-set', o),
  onGame: (cb) => ipcRenderer.on('game', (e, s) => cb(s))
});
