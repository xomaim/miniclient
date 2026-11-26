// Mini Client Phone Play: stream the Minecraft window to a phone on the same Wi-Fi and play it with touch controls.
// The PC keeps running the game; the phone gets the picture (WebRTC) and sends input back over a data channel.
const http = require('http');
const os = require('os');
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { BrowserWindow, ipcMain, desktopCapturer } = require('electron');

const PORT = 47800;
let server = null, streamer = null, opts = null, input = null;
let viewer = null;          // { id, name, since }
let menuTimer = null, lastMenu = null;

// ---------- Windows input (SendInput through koffi) ----------
function loadInput() {
  if (input !== null) return input;
  input = false;
  if (process.platform !== 'win32') return input;
  try {
    const koffi = require('koffi');
    const u = koffi.load('user32.dll');
    const MOUSEINPUT = koffi.struct('MC_MOUSEINPUT', { dx: 'long', dy: 'long', mouseData: 'uint32', dwFlags: 'uint32', time: 'uint32', dwExtraInfo: 'uintptr' });
    const KEYBDINPUT = koffi.struct('MC_KEYBDINPUT', { wVk: 'uint16', wScan: 'uint16', dwFlags: 'uint32', time: 'uint32', dwExtraInfo: 'uintptr' });
    const HARDWAREINPUT = koffi.struct('MC_HARDWAREINPUT', { uMsg: 'uint32', wParamL: 'uint16', wParamH: 'uint16' });
    const U = koffi.union('MC_INPUT_U', { mi: MOUSEINPUT, ki: KEYBDINPUT, hi: HARDWAREINPUT });
    const INPUT = koffi.struct('MC_INPUT', { type: 'uint32', u: U });
    const POINT = koffi.struct('MC_POINT', { x: 'long', y: 'long' });
    const RECT = koffi.struct('MC_RECT', { left: 'long', top: 'long', right: 'long', bottom: 'long' });
    const CURSORINFO = koffi.struct('MC_CURSORINFO', { cbSize: 'uint32', flags: 'uint32', hCursor: 'uintptr', ptScreenPos: POINT });
    const SendInput = u.func('unsigned int __stdcall SendInput(unsigned int cInputs, MC_INPUT *pInputs, int cbSize)');
    const SetCursorPos = u.func('bool __stdcall SetCursorPos(int X, int Y)');
    const GetCursorInfo = u.func('bool __stdcall GetCursorInfo(_Inout_ MC_CURSORINFO *pci)');
    const ClientToScreen = u.func('bool __stdcall ClientToScreen(intptr_t hWnd, _Inout_ MC_POINT *lpPoint)');
    const GetClientRect = u.func('bool __stdcall GetClientRect(intptr_t hWnd, _Out_ MC_RECT *lpRect)');
    const SetForegroundWindow = u.func('bool __stdcall SetForegroundWindow(intptr_t hWnd)');
    const IsWindow = u.func('bool __stdcall IsWindow(intptr_t hWnd)');
    const size = koffi.sizeof(INPUT);
    const send = (obj) => SendInput(1, obj, size);
    input = {
      key(scan, down) { send({ type: 1, u: { ki: { wVk: 0, wScan: scan, dwFlags: 0x0008 | (down ? 0 : 0x0002), time: 0, dwExtraInfo: 0 } } }); },
      char(code) {
        send({ type: 1, u: { ki: { wVk: 0, wScan: code, dwFlags: 0x0004, time: 0, dwExtraInfo: 0 } } });
        send({ type: 1, u: { ki: { wVk: 0, wScan: code, dwFlags: 0x0004 | 0x0002, time: 0, dwExtraInfo: 0 } } });
      },
      move(dx, dy) { send({ type: 0, u: { mi: { dx: Math.round(dx), dy: Math.round(dy), mouseData: 0, dwFlags: 0x0001, time: 0, dwExtraInfo: 0 } } }); },
      button(b, down) {
        const f = b === 'right' ? (down ? 0x0008 : 0x0010) : b === 'middle' ? (down ? 0x0020 : 0x0040) : (down ? 0x0002 : 0x0004);
        send({ type: 0, u: { mi: { dx: 0, dy: 0, mouseData: 0, dwFlags: f, time: 0, dwExtraInfo: 0 } } });
      },
      wheel(n) { send({ type: 0, u: { mi: { dx: 0, dy: 0, mouseData: (Math.round(n) * 120) >>> 0, dwFlags: 0x0800, time: 0, dwExtraInfo: 0 } } }); },
      cursorShowing() { const ci = { cbSize: koffi.sizeof(CURSORINFO), flags: 0, hCursor: 0, ptScreenPos: { x: 0, y: 0 } }; return GetCursorInfo(ci) ? (ci.flags & 1) === 1 : true; },
      clientPoint(hwnd, fx, fy) {
        const r = {}; if (!GetClientRect(hwnd, r)) return null;
        const p = { x: Math.round((r.right - r.left) * fx), y: Math.round((r.bottom - r.top) * fy) };
        ClientToScreen(hwnd, p); return p;
      },
      setCursor(x, y) { SetCursorPos(x, y); },
      focus(hwnd) { if (hwnd) SetForegroundWindow(hwnd); },
      alive(hwnd) { return !!hwnd && IsWindow(hwnd); }
    };
  } catch (e) { input = false; opts && opts.log('Phone Play input unavailable: ' + e.message); }
  return input;
}

// Scan codes Minecraft's default controls use.
const SCAN = { W: 0x11, A: 0x1E, S: 0x1F, D: 0x20, SPACE: 0x39, SHIFT: 0x2A, CTRL: 0x1D, E: 0x12, Q: 0x10, F: 0x21, T: 0x14, ESC: 0x01, ENTER: 0x1C,
  BACK: 0x0E, TAB: 0x0F, F1: 0x3B, F2: 0x3C, F3: 0x3D, F5: 0x3F, SLASH: 0x35, '1': 0x02, '2': 0x03, '3': 0x04, '4': 0x05, '5': 0x06, '6': 0x07, '7': 0x08, '8': 0x09, '9': 0x0A };
const held = new Set();

// ---------- game window ----------
let gameHwnd = 0, gameSource = null;
async function findGame() {
  const srcs = await desktopCapturer.getSources({ types: ['window'], thumbnailSize: { width: 0, height: 0 }, fetchWindowIcons: false });
  const s = srcs.find((x) => /^Mini Client \d/.test(x.name)) || srcs.find((x) => /^Minecraft\*? ?\d/.test(x.name));
  if (!s) { gameSource = null; gameHwnd = 0; return null; }
  gameSource = s.id;
  const m = /^window:(\d+):/.exec(s.id);
  gameHwnd = m ? Number(m[1]) : 0;
  return s;
}

// ---------- input from the phone ----------
function handleInput(msg) {
  const I = loadInput();
  if (!I || !msg) return;
  switch (msg.t) {
    case 'key': {
      const sc = SCAN[msg.k]; if (!sc) return;
      if (msg.d) { held.add(msg.k); I.key(sc, true); } else { held.delete(msg.k); I.key(sc, false); }
      break;
    }
    case 'press': { const sc = SCAN[msg.k]; if (sc) { I.key(sc, true); setTimeout(() => I.key(sc, false), 40); } break; }
    case 'look': I.move(msg.dx, msg.dy); break;
    case 'btn': I.button(msg.b, !!msg.d); break;
    case 'wheel': I.wheel(msg.n); break;
    case 'text': for (const ch of String(msg.s || '').slice(0, 256)) I.char(ch.codePointAt(0) & 0xFFFF); break;
    case 'point': {          // absolute cursor (menus): x,y are 0..1 of the game picture
      const p = I.clientPoint(gameHwnd, Math.min(1, Math.max(0, msg.x)), Math.min(1, Math.max(0, msg.y)));
      if (p) I.setCursor(p.x, p.y);
      if (msg.b) I.button(msg.b, !!msg.d);
      break;
    }
    case 'focus': I.focus(gameHwnd); break;
    case 'quality': if (streamer && !streamer.isDestroyed()) streamer.webContents.send('rp-quality', msg.q); break;
    case 'release': releaseAll(); break;
  }
}
function releaseAll() {
  const I = loadInput(); if (!I) return;
  for (const k of held) I.key(SCAN[k], false);
  held.clear(); I.button('left', false); I.button('right', false);
}

// ---------- streamer window (captures the game and runs WebRTC) ----------
function ensureStreamer() {
  if (streamer && !streamer.isDestroyed()) return streamer;
  streamer = new BrowserWindow({ show: false, width: 320, height: 200, webPreferences: { preload: path.join(__dirname, 'preload.js'), contextIsolation: true, backgroundThrottling: false } });
  streamer.loadFile(path.join(__dirname, 'ui', 'stream.html'));
  streamer.on('closed', () => { streamer = null; });
  return streamer;
}
function ask(channel, data) {
  return new Promise((resolve, reject) => {
    const w = ensureStreamer();
    const id = crypto.randomBytes(6).toString('hex');
    const t = setTimeout(() => { ipcMain.removeAllListeners('rp-reply-' + id); reject(new Error('The streamer did not answer.')); }, 20000);
    ipcMain.once('rp-reply-' + id, (e, r) => { clearTimeout(t); r && r.error ? reject(new Error(r.error)) : resolve(r); });
    const go = () => w.webContents.send(channel, Object.assign({ id }, data));
    if (w.webContents.isLoading()) w.webContents.once('did-finish-load', go); else go();
  });
}
ipcMain.on('rp-input', (e, msg) => handleInput(msg));
ipcMain.on('rp-closed', () => { releaseAll(); viewer = null; stopMenuWatch(); opts && opts.status(); });
ipcMain.on('rp-opened', () => { startMenuWatch(); opts && opts.status(); handleInput({ t: 'focus' }); });

function startMenuWatch() {
  stopMenuWatch();
  menuTimer = setInterval(() => {
    const I = loadInput(); if (!I || !streamer) return;
    const menu = I.cursorShowing();
    const alive = I.alive(gameHwnd);
    if (menu !== lastMenu || !alive) { lastMenu = menu; streamer.webContents.send('rp-state', { menu, game: alive }); }
  }, 250);
}
function stopMenuWatch() { if (menuTimer) clearInterval(menuTimer); menuTimer = null; lastMenu = null; }

// ---------- HTTP server for the phone ----------
function lanIp() {
  const all = [];
  for (const [name, list] of Object.entries(os.networkInterfaces())) for (const a of list || [])
    if (a.family === 'IPv4' && !a.internal && !/^169\.254/.test(a.address)) all.push({ name, ip: a.address });
  const pick = all.find((a) => /^192\.168\./.test(a.ip)) || all.find((a) => /^10\./.test(a.ip)) || all.find((a) => /^172\.(1[6-9]|2\d|3[01])\./.test(a.ip)) || all[0];
  return pick ? pick.ip : '127.0.0.1';
}
const send = (res, code, body, type) => {
  res.writeHead(code, { 'content-type': type || 'application/json', 'cache-control': 'no-store' });
  res.end(typeof body === 'string' || Buffer.isBuffer(body) ? body : JSON.stringify(body));
};
function readBody(req) {
  return new Promise((resolve) => { let b = ''; req.on('data', (c) => { b += c; if (b.length > 200000) req.destroy(); }); req.on('end', () => { try { resolve(JSON.parse(b || '{}')); } catch (e) { resolve({}); } }); });
}
const PHONE = path.join(__dirname, 'ui', 'phone');
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript', '.json': 'application/manifest+json', '.png': 'image/png', '.svg': 'image/svg+xml' };

async function route(req, res) {
  const u = new URL(req.url, 'http://x');
  const key = u.searchParams.get('k') || req.headers['x-mini-key'] || '';
  const okKey = key && crypto.timingSafeEqual(Buffer.from(String(key).padEnd(64).slice(0, 64)), Buffer.from(opts.key().padEnd(64).slice(0, 64)));
  // The home-screen app keeps its key in the start link, so it still works when opened from the icon.
  if (u.pathname === '/manifest.json') {
    const k = okKey ? key : '';
    return send(res, 200, { name: 'Mini Client Play', short_name: 'Mini Client', start_url: '/?k=' + encodeURIComponent(k), display: 'fullscreen', orientation: 'landscape',
      background_color: '#000000', theme_color: '#000000', icons: [{ src: '/icon.png', sizes: '512x512', type: 'image/png' }] }, 'application/manifest+json');
  }
  // static phone app (no key needed for the files themselves; every action needs the key)
  if (req.method === 'GET' && !u.pathname.startsWith('/api/')) {
    const f = u.pathname === '/' ? 'index.html' : u.pathname.replace(/^\/+/, '');
    const full = path.join(PHONE, path.normalize(f).replace(/^(\.\.[/\\])+/, ''));
    if (!full.startsWith(PHONE)) return send(res, 404, 'Not found', 'text/plain');
    try { return send(res, 200, fs.readFileSync(full), TYPES[path.extname(full)] || 'application/octet-stream'); }
    catch (e) { return send(res, 404, 'Not found', 'text/plain'); }
  }
  if (!okKey) return send(res, 403, { ok: false, error: 'This link is old. Scan the QR code in Mini Client again.' });
  if (u.pathname === '/api/hello') {
    await findGame().catch(() => null);
    return send(res, 200, { ok: true, game: !!gameSource, name: opts.account(), version: opts.version(), pc: os.hostname(), busy: !!viewer });
  }
  if (u.pathname === '/api/launch' && req.method === 'POST') { opts.launch(); return send(res, 200, { ok: true }); }
  if (u.pathname === '/api/call' && req.method === 'POST') {          // the phone app uses the same actions as the launcher
    const body = await readBody(req);
    try { return send(res, 200, { ok: true, result: await opts.call(String(body.fn || ''), body.args || []) }); }
    catch (e) { return send(res, 200, { ok: false, error: e.message }); }
  }
  if (u.pathname === '/api/offer' && req.method === 'POST') {
    const body = await readBody(req);
    const s = await findGame().catch(() => null);
    if (!s) return send(res, 200, { ok: false, error: 'Minecraft is not open on your PC yet.' });
    try {
      const r = await ask('rp-offer', { source: gameSource, quality: body.quality || 'auto', audio: body.audio !== false, ip: lanIp() });
      viewer = { id: r.session, name: String(body.device || 'Phone').slice(0, 40), since: Date.now() };
      opts.status();
      return send(res, 200, { ok: true, session: r.session, sdp: r.sdp });
    } catch (e) { return send(res, 200, { ok: false, error: e.message }); }
  }
  if (u.pathname === '/api/answer' && req.method === 'POST') {
    const body = await readBody(req);
    try { await ask('rp-answer', { session: body.session, sdp: body.sdp }); return send(res, 200, { ok: true }); }
    catch (e) { return send(res, 200, { ok: false, error: e.message }); }
  }
  return send(res, 404, { ok: false });
}

function start(o) {
  opts = o;
  if (server) return info();
  server = http.createServer((req, res) => route(req, res).catch((e) => send(res, 500, { ok: false, error: e.message })));
  server.on('error', (e) => { opts.log('Phone Play server error: ' + e.message); server = null; opts.status(); });
  server.listen(PORT, '0.0.0.0');
  loadInput();
  return info();
}
function stop() {
  if (streamer && !streamer.isDestroyed()) streamer.close();
  if (server) server.close();
  server = null; viewer = null; stopMenuWatch(); releaseAll();
}
function info() {
  const ip = lanIp();
  return { on: !!server, url: 'http://' + ip + ':' + PORT + '/?k=' + (opts ? opts.key() : ''), ip, port: PORT, viewer, input: !!loadInput() };
}
function kick() { if (streamer && !streamer.isDestroyed()) streamer.webContents.send('rp-kick'); viewer = null; releaseAll(); }

module.exports = { start, stop, info, kick };
