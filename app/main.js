const { app, BrowserWindow, ipcMain, safeStorage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const auth = require('./auth');
const { autoUpdater } = require('electron-updater');

let win = null;
let account = null;   // { name, uuid, token } kept in memory only
const store = () => path.join(app.getPath('userData'), 'login.bin');

function saveRefresh(t) {
  try {
    if (!t) { fs.rmSync(store(), { force: true }); return; }
    fs.writeFileSync(store(), safeStorage.isEncryptionAvailable() ? safeStorage.encryptString(t) : Buffer.from(t));
  } catch (e) {}
}
function loadRefresh() {
  try {
    const b = fs.readFileSync(store());
    return safeStorage.isEncryptionAvailable() ? safeStorage.decryptString(b) : b.toString();
  } catch (e) { return ''; }
}

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
  try { account = await auth.signIn(win); saveRefresh(account.refresh); return { ok: true, account: { name: account.name, uuid: account.uuid } }; }
  catch (e) { return { ok: false, error: e.message }; }
});
ipcMain.handle('silent', async () => {
  const t = loadRefresh();
  if (!t) return { ok: false };
  try { account = await auth.silent(t); saveRefresh(account.refresh || t); return { ok: true, account: { name: account.name, uuid: account.uuid } }; }
  catch (e) { saveRefresh(''); return { ok: false }; }
});
ipcMain.handle('logout', () => { account = null; saveRefresh(''); return { ok: true }; });
ipcMain.handle('win', (e, what) => {
  if (!win) return;
  if (what === 'close') win.close(); else if (what === 'min') win.minimize();
  else if (what === 'max') win.isMaximized() ? win.unmaximize() : win.maximize();
});
ipcMain.handle('open', (e, url) => { if (/^https:\/\//.test(url)) shell.openExternal(url); });


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
