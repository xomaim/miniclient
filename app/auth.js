// Microsoft sign-in: a real Microsoft window (email + password are typed into Microsoft, never into this app),
// then Xbox -> XSTS -> Minecraft services.
const { BrowserWindow, session } = require('electron');
const crypto = require('crypto');

const CLIENT_ID = '29c987d8-cae7-4e10-a1c8-5a43e7f6651b';
const REDIRECT = 'https://login.live.com/oauth20_desktop.srf';
const SCOPE = 'XboxLive.signin offline_access';
const TOKEN_URL = 'https://login.microsoftonline.com/consumers/oauth2/v2.0/token';

const form = (o) => new URLSearchParams(o).toString();
const b64url = (b) => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

async function postForm(url, body) {
  const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' }, body: form(body) });
  return { status: r.status, json: await r.json().catch(() => ({})) };
}
async function postJson(url, body) {
  const r = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'application/json' }, body: JSON.stringify(body) });
  return { status: r.status, json: await r.json().catch(() => ({})) };
}

// Opens the Microsoft window. A fresh, non-persistent session every time, so nothing is remembered between sign-ins.
function popupLogin(parent) {
  return new Promise((resolve, reject) => {
    const verifier = b64url(crypto.randomBytes(48));
    const challenge = b64url(crypto.createHash('sha256').update(verifier).digest());
    const state = b64url(crypto.randomBytes(12));
    const url = 'https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize?' + form({
      client_id: CLIENT_ID, response_type: 'code', redirect_uri: REDIRECT, response_mode: 'query',
      scope: SCOPE, prompt: 'login', state, code_challenge: challenge, code_challenge_method: 'S256'
    });
    const win = new BrowserWindow({
      width: 480, height: 680, parent, modal: !!parent, autoHideMenuBar: true, title: 'Sign in with Microsoft',
      webPreferences: { partition: 'login-' + Date.now(), nodeIntegration: false, contextIsolation: true }
    });
    let done = false;
    const finish = (err, code) => {
      if (done) return; done = true;
      try { win.destroy(); } catch (e) {}
      err ? reject(err) : resolve({ code, verifier });
    };
    const check = (u) => {
      if (!u.startsWith(REDIRECT)) return false;
      const q = new URL(u).searchParams;
      if (q.get('error')) finish(new Error(q.get('error_description') || q.get('error')));
      else if (q.get('state') !== state) finish(new Error('Sign-in state mismatch'));
      else finish(null, q.get('code'));
      return true;
    };
    win.webContents.on('will-redirect', (e, u) => { if (check(u)) e.preventDefault(); });
    win.webContents.on('will-navigate', (e, u) => { if (check(u)) e.preventDefault(); });
    win.on('closed', () => finish(new Error('Sign-in window closed')));
    win.loadURL(url);
  });
}

async function exchange(tokenResp) {
  const t = tokenResp;
  const xbl = await postJson('https://user.auth.xboxlive.com/user/authenticate', {
    Properties: { AuthMethod: 'RPS', SiteName: 'user.auth.xboxlive.com', RpsTicket: 'd=' + t.access_token },
    RelyingParty: 'http://auth.xboxlive.com', TokenType: 'JWT' });
  if (xbl.status !== 200) throw new Error('Xbox sign-in failed (' + xbl.status + ')');
  const uhs = xbl.json.DisplayClaims.xui[0].uhs;
  const xsts = await postJson('https://xsts.auth.xboxlive.com/xsts/authorize', {
    Properties: { SandboxId: 'RETAIL', UserTokens: [xbl.json.Token] },
    RelyingParty: 'rp://api.minecraftservices.com/', TokenType: 'JWT' });
  if (xsts.status !== 200) {
    const xe = xsts.json && xsts.json.XErr;
    if (xe === 2148916233) throw new Error('This Microsoft account has no Xbox profile. Sign in once at xbox.com first.');
    if (xe === 2148916238) throw new Error('Child account: add it to a Microsoft Family first.');
    throw new Error('Xbox security check failed (' + xsts.status + ')');
  }
  const mc = await postJson('https://api.minecraftservices.com/authentication/login_with_xbox',
    { identityToken: 'XBL3.0 x=' + uhs + ';' + xsts.json.Token });
  if (mc.status !== 200) throw new Error('Minecraft sign-in failed (' + mc.status + ')');
  const auth = { Authorization: 'Bearer ' + mc.json.access_token, Accept: 'application/json' };
  const ent = await (await fetch('https://api.minecraftservices.com/entitlements/mcstore', { headers: auth })).json().catch(() => ({}));
  const owns = (ent.items || []).some((i) => i.name === 'game_minecraft' || i.name === 'product_minecraft');
  const pr = await fetch('https://api.minecraftservices.com/minecraft/profile', { headers: auth });
  const prof = pr.status === 200 ? await pr.json() : null;
  if (!owns) throw new Error('This account does not own Minecraft Java.');
  if (!prof) throw new Error('No Minecraft profile yet. Create one in the official launcher once.');
  return { name: prof.name, uuid: prof.id, token: mc.json.access_token, refresh: t.refresh_token || '' };
}

async function signIn(parent) {
  const { code, verifier } = await popupLogin(parent);
  const r = await postForm(TOKEN_URL, { grant_type: 'authorization_code', client_id: CLIENT_ID, code, redirect_uri: REDIRECT, code_verifier: verifier, scope: SCOPE });
  if (r.status !== 200) throw new Error('Microsoft refused the sign-in: ' + (r.json.error_description || r.json.error || r.status));
  return exchange(r.json);
}
async function silent(refreshToken) {
  const r = await postForm(TOKEN_URL, { grant_type: 'refresh_token', client_id: CLIENT_ID, refresh_token: refreshToken, scope: SCOPE });
  if (r.status !== 200) throw new Error('Saved login expired');
  return exchange(r.json);
}
module.exports = { signIn, silent };
