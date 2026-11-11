const { app, BrowserWindow, ipcMain, safeStorage, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const auth = require('./auth');
const { autoUpdater } = require('electron-updater');
const { Launcher, releases } = require('./launcher');

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
  const w = BrowserWindow.fromWebContents(e.sender) || win;
  if (!w) return;
  if (what === 'close') w.close(); else if (what === 'min') w.minimize();
  else if (what === 'max') w.isMaximized() ? w.unmaximize() : w.maximize();
});
ipcMain.handle('friend-lookup', async (e, name) => {
  if (!/^[A-Za-z0-9_]{2,16}$/.test(String(name))) return { ok: false, error: 'That is not a valid Minecraft name.' };
  try {
    const r = await fetch('https://api.mojang.com/users/profiles/minecraft/' + encodeURIComponent(name));
    if (r.status === 404 || r.status === 204) return { ok: false, error: 'No player called ' + name + '.' };
    if (!r.ok) return { ok: false, error: 'Could not reach Mojang (' + r.status + ').' };
    const j = await r.json();
    return { ok: true, name: j.name, uuid: j.id };
  } catch (err) { return { ok: false, error: 'Could not reach Mojang. Check your internet.' }; }
});
ipcMain.handle('open', (e, url) => { if (/^https:\/\//.test(url)) shell.openExternal(url); });


// ---------- friends server ----------
let serverUrl = null;
async function getServer() {
  if (serverUrl) return serverUrl;
  try {
    const r = await fetch('https://github.com/xomaim/miniclient/releases/download/server/server.json');
    if (r.ok) { const j = await r.json(); if (/^https:\/\//.test(j.url)) serverUrl = j.url.replace(/\/$/, ''); }
  } catch (e) {}
  return serverUrl;
}
async function freshAccount() {
  if (account && Date.now() - tokenTime < 20 * 3600 * 1000) return account;
  const d = loadLogin();
  if (!d || !d.refresh) return null;
  account = await auth.silent(d.refresh); tokenTime = Date.now();
  saveLogin({ refresh: account.refresh || d.refresh, name: account.name, uuid: account.uuid });
  return account;
}
async function api(pathname, body) {
  const base = await getServer();
  if (!base) return { ok: false, offline: true, error: 'The friends server is not set up yet.' };
  const a = await freshAccount().catch(() => null);
  if (!a) return { ok: false, error: 'Sign in first.' };
  try {
    const r = await fetch(base + pathname, {
      method: body ? 'POST' : 'GET',
      headers: Object.assign({ authorization: 'Bearer ' + a.token }, body ? { 'content-type': 'application/json' } : {}),
      body: body ? JSON.stringify(body) : undefined
    });
    return await r.json();
  } catch (e) { return { ok: false, error: 'Could not reach the friends server.' }; }
}
ipcMain.handle('friends', (e, action, data) => {
  const ok = ['request', 'accept', 'decline', 'cancel', 'remove'];
  if (action === 'list') return api('/friends');
  if (ok.includes(action)) return api('/' + action, data || {});
  return { ok: false };
});
function presence(status) { if (account) api('/presence', { status }).catch(() => {}); }
setInterval(() => presence(launcher && launcher.running() ? 'playing' : 'online'), 45000);
app.on('before-quit', () => presence('offline'));

// ---------- game log window ----------
let logWin = null, logBuf = [], logState = 'idle';
function classify(t) {
  if (/\b(ERROR|FATAL|SEVERE)\b|Exception|^\s*Caused by|^\s+at [\w.$]+\(|crash/i.test(t)) return 'err';
  if (/Loading \d+ mods|^\s*(-|\|--|\\--)\s+\S+\s+\S+|Mini ?Client|Fabric .* with \d+|Fast start|Sound engine started|Sodium|ready/i.test(t)) return 'ok';
  if (/\bWARN\b/.test(t)) return 'warn';
  return 'info';
}
function logLine(text, kind) {
  const l = { t: Date.now(), text: String(text).replace(/\s+$/, ''), kind: kind || classify(String(text)) };
  logBuf.push(l); if (logBuf.length > 5000) logBuf.shift();
  if (logWin && !logWin.isDestroyed()) logWin.webContents.send('log', l);
}
function setLogState(s) { logState = s; if (logWin && !logWin.isDestroyed()) logWin.webContents.send('log-state', s); }
function openLog(reset) {
  if (logWin && !logWin.isDestroyed()) { if (reset) logWin.webContents.send('log-reset'); logWin.show(); return; }
  logWin = new BrowserWindow({
    width: 780, height: 470, minWidth: 480, minHeight: 260, frame: false, show: false, backgroundColor: '#000000',
    icon: path.join(__dirname, 'icon.ico'), title: 'Mini Client - Game log',
    webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, nodeIntegration: false }
  });
  logWin.once('ready-to-show', () => { if (logWin) logWin.show(); });
  logWin.loadFile(path.join(__dirname, 'ui', 'log.html'));
  logWin.on('closed', () => { logWin = null; });
}
ipcMain.handle('log-init', () => ({ lines: logBuf, state: logState }));
const READY = /Sound engine started|Created: \d+x\d+x\d+ minecraft:textures\/atlas\/blocks/;
function showLauncher() { if (win && !win.isDestroyed()) { win.show(); if (win.isMinimized()) win.restore(); win.focus(); } }
function tailGame(file, proc) {
  let pos = 0, rest = '', ready = false;
  const seen = (p) => {
    logLine(p);
    if (!ready && READY.test(p)) {
      ready = true;
      logLine('Minecraft is open. Have fun!', 'ok');
      setTimeout(() => { if (logWin && !logWin.isDestroyed()) logWin.close(); }, 1200);
    }
  };
  const tick = () => {
    try {
      const st = fs.statSync(file);
      if (st.size <= pos) return;
      const fd = fs.openSync(file, 'r'), buf = Buffer.alloc(st.size - pos);
      fs.readSync(fd, buf, 0, buf.length, pos); fs.closeSync(fd); pos = st.size;
      const parts = (rest + buf.toString('utf8')).split(/\r?\n/); rest = parts.pop();
      for (const p of parts) if (p.trim()) seen(p);
    } catch (e) {}
  };
  const iv = setInterval(tick, 250);
  proc.once('exit', (code) => setTimeout(() => {
    tick(); if (rest.trim()) logLine(rest); clearInterval(iv);
    const crashed = !!code || !ready;
    logLine(crashed ? 'Minecraft closed unexpectedly' + (code ? ' (error code ' + code + ')' : '') + '. Check the red lines above.' : 'Minecraft closed.', crashed ? 'err' : 'info');
    setLogState(crashed ? 'failed' : 'closed');
    if (crashed) openLog(false);      // bring the log back so you can see what went wrong
    showLauncher();
  }, 400));
}

// ---------- game launch ----------
const GAME_DEFAULTS = { version: '1.21.7', ram: 4, fpsBoost: true, builtinOn: true, capeOn: true, fullscreen: false, keepOpen: false, showLog: true };
let launcher = null, progKey = '', progPct = 0;
function gameOpts() { return Object.assign({}, GAME_DEFAULTS, readCfg().game || {}); }
const sendGame = (s) => { if (win && !win.isDestroyed()) win.webContents.send('game', s); };
function getLauncher() {
  if (!launcher) launcher = new Launcher({
    cfgDir: app.getPath('userData'),
    log: (t) => { try { fs.appendFileSync(path.join(app.getPath('userData'), 'launcher.log'), new Date().toISOString() + ' ' + t + '\n'); } catch (e) {}
      logLine(t, /unavailable|failed|error|not found/i.test(t) ? 'err' : /Fabric .* with|Fast start|updated|Turned off/i.test(t) ? 'ok' : 'info'); },
    progress: (text, pct) => {
      sendGame({ state: 'progress', text, pct });
      const key = text.replace(/[\d\/ ]+/g, '');
      if (key !== progKey || pct - progPct >= 10 || pct >= 98) { progKey = key; progPct = pct; logLine(text + (pct != null ? '   ' + pct + '%' : ''), 'info'); }
    }
  });
  return launcher;
}
ipcMain.handle('game-opts', () => gameOpts());
ipcMain.handle('versions', async () => { try { return await releases(); } catch (e) { return []; } });
ipcMain.handle('user-mods', () => getLauncher().userMods());
ipcMain.handle('open-folder', (e, which) => {
  const mcDir = path.join(process.env.APPDATA || app.getPath('appData'), '.minecraft');
  const p = which === 'logs' ? app.getPath('userData') : which === 'shots' ? path.join(mcDir, 'screenshots') : which === 'mods' ? path.join(mcDir, 'mods') : mcDir;
  try { fs.mkdirSync(p, { recursive: true }); } catch (err) {}
  return require('electron').shell.openPath(p);
});
ipcMain.handle('game-opts-set', (e, o) => {
  const c = readCfg(), g = Object.assign(gameOpts(), o || {});
  g.ram = Math.min(16, Math.max(1, parseInt(g.ram, 10) || 4));
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
    const opts = gameOpts();
    logBuf = []; progKey = ''; progPct = 0; setLogState('starting');
    if (opts.showLog !== false) openLog(true);
    if (!opts.keepOpen && win) { if (opts.showLog !== false) win.hide(); else win.minimize(); }
    logLine('Mini Client ' + app.getVersion() + '  \u00b7  starting Minecraft ' + opts.version + ' as ' + account.name, 'info');
    const r = await L.launch(account, Object.assign(gameOpts(), { server: typeof server === 'string' ? server : '' }));
    sendGame({ state: 'running', id: r.id });
    if (r.modsNote) logLine('Mods could not be set up: ' + r.modsNote, 'err');
    else if (r.mods) logLine(r.mods + ' Mini Client mods ready.', 'ok');
    logLine('Java started. Booting Minecraft...', 'ok'); setLogState('running'); presence('playing');
    if (L.proc) tailGame(path.join(app.getPath('userData'), 'game.log'), L.proc);
    const watch = setInterval(() => { if (!L.running()) { clearInterval(watch); sendGame({ state: 'idle' }); showLauncher(); } }, 2000);
    return r;
  } catch (err) { sendGame({ state: 'idle' }); logLine(err.message, 'err'); setLogState('failed'); showLauncher(); return { ok: false, error: err.message }; }
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
app.on('second-instance', () => showLauncher());
app.commandLine.appendSwitch('disable-features', 'CalculateNativeWinOcclusion');
app.whenReady().then(() => { createWindow(); setupUpdater(); });
app.on('window-all-closed', () => app.quit());
