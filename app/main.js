const { app, BrowserWindow, ipcMain, safeStorage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const auth = require('./auth');
const { autoUpdater } = require('electron-updater');
const { Launcher } = require('./launcher');

let win = null;
let account = null;   // { name, uuid, token } kept in memory only
let tokenTime = 0;
const store = () => path.join(app.getPath('userData'), 'login.bin');

// Saved login: { refresh, name, uuid } encrypted with Windows' own protection (safeStorage).
function saveLogin(d) {
  try {
    if (!d || !d.refresh) { fs.rmSync(store(), { force: true }); return; }
    const t = JSON.stringify({ refresh: d.refresh, name: d.name, uuid: d.uuid });
    fs.writeFileSync(store(), safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(t) : Buffer.from(t));
  } catch (e) {}
}
function loadLogin() {
  try {
    const b = fs.readFileSync(store());
    const t = safeStorage.isEncryptionAvailable() ? safeStorage.decryptString(b) : b.toString();
    if (t.charAt(0) === '{') return JSON.parse(t);
    return { refresh: t };   // older builds saved only the refresh token
  } catch (e) { return null; }
}
const saveRefresh = (t) => saveLogin(t ? { refresh: t, name: account && account.name, uuid: account && account.uuid } : null);
const loadRefresh = () => (loadLogin() || {}).refresh || '';
// Only a real "token revoked/expired" answer from Microsoft signs you out; network hiccups keep you signed in.
const isExpired = (e) => /expired|refused|invalid_grant/i.test(String(e && e.message));

function createWindow() {
  win = new BrowserWindow({
    width: 1000, height: 680, minWidth: 900, minHeight: 600, frame: false, show: false, backgroundColor: '#0b0b0d',
    icon: path.join(__dirname, 'icon.ico'), title: 'Mini Client',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false }
  });
  win.once('ready-to-show', () => win.show());
  win.loadFile(path.join(__dirname, 'ui', 'index.html'));
}

ipcMain.handle('state', () => ({ account: account ? { name: account.name, uuid: account.uuid } : null, version: app.getVersion() }));
ipcMain.handle('login', async () => {
  try { account = await auth.signIn(win); tokenTime = Date.now(); saveLogin(account); return { ok: true, account: { name: account.name, uuid: account.uuid } }; }
  catch (e) { return { ok: false, error: e.message }; }
});
ipcMain.handle('saved', () => { const d = loadLogin(); return d && d.name ? { name: d.name, uuid: d.uuid } : null; });
ipcMain.handle('silent', async () => {
  const d = loadLogin();
  if (!d || !d.refresh) return { ok: false };
  try {
    account = await auth.silent(d.refresh); tokenTime = Date.now();
    saveLogin({ refresh: account.refresh || d.refresh, name: account.name, uuid: account.uuid });
    return { ok: true, account: { name: account.name, uuid: account.uuid } };
  } catch (e) {
    if (isExpired(e)) { saveLogin(null); return { ok: false }; }
    return { ok: d.name ? true : false, offline: true, account: d.name ? { name: d.name, uuid: d.uuid } : null };
  }
});
ipcMain.handle('logout', () => { account = null; saveRefresh(''); return { ok: true }; });
ipcMain.handle('win', (e, what) => {
  if (!win) return;
  if (what === 'close') win.close(); else if (what === 'min') win.minimize();
  else if (what === 'max') win.isMaximized() ? win.unmaximize() : win.maximize();
});
ipcMain.handle('open', (e, url) => { if (/^https:\/\//.test(url)) shell.openExternal(url); });


// ---------- game launch ----------
const GAME_DEFAULTS = { version: '1.21.7', ram: 4, fpsBoost: true, builtinOn: true, capeOn: true, fullscreen: false };
let launcher = null;
function gameOpts() { return Object.assign({}, GAME_DEFAULTS, readCfg().game || {}); }
const sendGame = (s) => { if (win && !win.isDestroyed()) win.webContents.send('game', s); };
function getLauncher() {
  if (!launcher) launcher = new Launcher({
    cfgDir: app.getPath('userData'),
    log: (t) => { try { fs.appendFileSync(path.join(app.getPath('userData'), 'launcher.log'), new Date().toISOString() + ' ' + t + '\n'); } catch (e) {} },
    progress: (text, pct) => sendGame({ state: 'progress', text, pct })
  });
  return launcher;
}
ipcMain.handle('game-opts', () => gameOpts());
ipcMain.handle('game-opts-set', (e, o) => {
  const c = readCfg(), g = Object.assign(gameOpts(), o || {});
  g.ram = Math.min(32, Math.max(1, parseInt(g.ram, 10) || 4));
  c.game = g; writeCfg(c); return g;
});
ipcMain.handle('launch', async (e, server) => {
  try {
    if (!account || Date.now() - tokenTime > 20 * 3600 * 1000) {
      const d = loadLogin();
      if (!d || !d.refresh) return { ok: false, error: 'Sign in with Microsoft first.' };   // Minecraft tokens last ~24h
      try { account = await auth.silent(d.refresh); }
      catch (e) { if (isExpired(e)) saveLogin(null); throw new Error(isExpired(e) ? 'Your saved login expired. Sign in again.' : 'Could not reach Microsoft. Check your internet.'); }
      tokenTime = Date.now();
      saveLogin({ refresh: account.refresh || d.refresh, name: account.name, uuid: account.uuid });
    }
    const L = getLauncher();
    const r = await L.launch(account, Object.assign(gameOpts(), { server: typeof server === 'string' ? server : '' }));
    sendGame({ state: 'running', id: r.id });
    const watch = setInterval(() => { if (!L.running()) { clearInterval(watch); sendGame({ state: 'idle' }); if (win && win.isMinimized()) win.restore(); } }, 2000);
    if (win) win.minimize();
    return r;
  } catch (err) { sendGame({ state: 'idle' }); return { ok: false, error: err.message }; }
});

// ---------- updater (stable and beta channels, both published on GitHub) ----------
const cfgFile = () => path.join(app.getPath('userData'), 'config.json');
function readCfg() { try { return JSON.parse(fs.readFileSync(cfgFile(), 'utf8')); } catch (e) { return {}; } }
function writeCfg(c) { try { fs.writeFileSync(cfgFile(), JSON.stringify(c)); } catch (e) {} }
const feed = (ch) => 'https://github.com/xomaim/miniclient/releases/download/' + (ch === 'beta' ? 'app-beta' : 'app-latest');
let updState = { state: 'idle' };
function sendUpd(s) { updState = s; if (win && !win.isDestroyed()) win.webContents.send('upd', s); }
function setupUpdater() {
  if (!app.isPackaged) return;
  autoUpdater.autoDownload = true;
  autoUpdater.autoInstallOnAppQuit = true;
  autoUpdater.allowDowngrade = true;
  autoUpdater.setFeedURL({ provider: 'generic', url: feed(readCfg().channel) });
  autoUpdater.on('checking-for-update', () => sendUpd({ state: 'checking' }));
  autoUpdater.on('update-available', (i) => sendUpd({ state: 'downloading', version: i.version, pct: 0 }));
  autoUpdater.on('update-not-available', () => sendUpd({ state: 'none' }));
  autoUpdater.on('download-progress', (p) => sendUpd({ state: 'downloading', version: updState.version, pct: Math.round(p.percent) }));
  autoUpdater.on('update-downloaded', (i) => sendUpd({ state: 'ready', version: i.version }));
  autoUpdater.on('error', (e) => sendUpd({ state: 'error', error: String(e && e.message || e).slice(0, 160) }));
  const check = () => autoUpdater.checkForUpdates().catch(() => {});
  setTimeout(check, 4000);
  setInterval(check, 5 * 60 * 1000);
}
ipcMain.handle('upd-check', () => { if (!app.isPackaged) { sendUpd({ state: 'none' }); return; } autoUpdater.checkForUpdates().catch(() => {}); });
ipcMain.handle('upd-install', () => { if (app.isPackaged) autoUpdater.quitAndInstall(false, true); });
ipcMain.handle('channel-get', () => readCfg().channel || 'stable');
ipcMain.handle('channel-set', (e, ch) => {
  const c = readCfg(); c.channel = ch === 'beta' ? 'beta' : 'stable'; writeCfg(c);
  if (app.isPackaged) { autoUpdater.setFeedURL({ provider: 'generic', url: feed(c.channel) }); autoUpdater.checkForUpdates().catch(() => {}); }
  return c.channel;
});

if (!app.requestSingleInstanceLock()) app.quit();
app.on('second-instance', () => { if (win) { if (win.isMinimized()) win.restore(); win.focus(); } });
app.commandLine.appendSwitch('disable-features', 'CalculateNativeWinOcclusion');
app.whenReady().then(() => { createWindow(); setupUpdater(); });
app.on('window-all-closed', () => app.quit());
