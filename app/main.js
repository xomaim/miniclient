const { app, BrowserWindow, ipcMain, safeStorage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const auth = require('./auth');

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
    width: 1000, height: 680, minWidth: 900, minHeight: 600, frame: false, backgroundColor: '#0b0b0d',
    icon: path.join(__dirname, 'icon.ico'), title: 'Mini Client',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false }
  });
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

app.whenReady().then(createWindow);
app.on('window-all-closed', () => app.quit());
