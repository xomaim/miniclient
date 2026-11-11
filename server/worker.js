// Mini Client friends server: friend requests, friends list and online status.
// Identity = the player's own Minecraft login (checked with Mojang), so nobody can pretend to be someone else.
import { DurableObject } from 'cloudflare:workers';

const ONLINE_MS = 90 * 1000;
const json = (o, status = 200) => new Response(JSON.stringify(o), { status, headers: { 'content-type': 'application/json' } });
const dash = (u) => String(u).replace(/-/g, '').toLowerCase();

export default {
  async fetch(req, env) {
    const url = new URL(req.url);
    if (url.pathname === '/' || url.pathname === '/health') return json({ ok: true, name: 'Mini Client friends' });
    const hub = env.HUB.get(env.HUB.idFromName('main'));
    return hub.fetch(req);
  }
};

export class Hub extends DurableObject {
  constructor(ctx, env) {
    super(ctx, env);
    this.sql = ctx.storage.sql;
    this.tokens = new Map();   // token -> { uuid, name, exp }
    this.sql.exec(`CREATE TABLE IF NOT EXISTS users (uuid TEXT PRIMARY KEY, name TEXT, seen INTEGER DEFAULT 0, status TEXT DEFAULT 'offline')`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS requests (src TEXT, dst TEXT, at INTEGER, PRIMARY KEY (src, dst))`);
    this.sql.exec(`CREATE TABLE IF NOT EXISTS friends (a TEXT, b TEXT, since INTEGER, PRIMARY KEY (a, b))`);
  }

  async who(req) {
    const tok = (req.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
    if (!tok) return null;
    const c = this.tokens.get(tok);
    if (c && c.exp > Date.now()) return c;
    const r = await fetch('https://api.minecraftservices.com/minecraft/profile', { headers: { authorization: 'Bearer ' + tok } });
    if (!r.ok) return null;
    const p = await r.json();
    const me = { uuid: dash(p.id), name: p.name, exp: Date.now() + 30 * 60 * 1000 };
    if (this.tokens.size > 5000) this.tokens.clear();
    this.tokens.set(tok, me);
    return me;
  }

  upsert(uuid, name) {
    this.sql.exec(`INSERT INTO users (uuid, name) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET name = excluded.name`, uuid, name);
  }

  user(uuid) {
    const r = this.sql.exec(`SELECT uuid, name, seen, status FROM users WHERE uuid = ?`, uuid).toArray()[0];
    const online = r && r.status !== 'offline' && Date.now() - r.seen < ONLINE_MS;
    return { uuid, name: r ? r.name : '', online: !!online, status: online ? r.status : 'offline', seen: r ? r.seen : 0 };
  }

  async lookup(name) {
    const r = await fetch('https://api.mojang.com/users/profiles/minecraft/' + encodeURIComponent(name));
    if (!r.ok) return null;
    const p = await r.json();
    return { uuid: dash(p.id), name: p.name };
  }

  befriend(a, b) {
    const t = Date.now();
    this.sql.exec(`INSERT OR IGNORE INTO friends VALUES (?, ?, ?)`, a, b, t);
    this.sql.exec(`INSERT OR IGNORE INTO friends VALUES (?, ?, ?)`, b, a, t);
    this.sql.exec(`DELETE FROM requests WHERE (src = ? AND dst = ?) OR (src = ? AND dst = ?)`, a, b, b, a);
  }

  list(me) {
    const friends = this.sql.exec(`SELECT b, since FROM friends WHERE a = ?`, me).toArray().map((r) => Object.assign(this.user(r.b), { since: r.since }));
    const incoming = this.sql.exec(`SELECT src, at FROM requests WHERE dst = ? ORDER BY at DESC`, me).toArray().map((r) => Object.assign(this.user(r.src), { at: r.at }));
    const outgoing = this.sql.exec(`SELECT dst, at FROM requests WHERE src = ? ORDER BY at DESC`, me).toArray().map((r) => Object.assign(this.user(r.dst), { at: r.at }));
    friends.sort((x, y) => (y.online - x.online) || x.name.localeCompare(y.name));
    return { friends, incoming, outgoing };
  }

  async fetch(req) {
    const me = await this.who(req);
    if (!me) return json({ ok: false, error: 'Sign in again.' }, 401);
    this.upsert(me.uuid, me.name);
    const path = new URL(req.url).pathname;
    const body = req.method === 'POST' ? await req.json().catch(() => ({})) : {};
    const other = body.uuid ? dash(body.uuid) : '';

    if (path === '/friends') return json(Object.assign({ ok: true, me: { uuid: me.uuid, name: me.name } }, this.list(me.uuid)));

    if (path === '/presence') {
      const st = ['online', 'playing', 'offline'].includes(body.status) ? body.status : 'online';
      this.sql.exec(`UPDATE users SET seen = ?, status = ? WHERE uuid = ?`, Date.now(), st, me.uuid);
      return json({ ok: true });
    }

    if (path === '/request') {
      const t = await this.lookup(String(body.name || '').trim());
      if (!t) return json({ ok: false, error: 'No Minecraft player called ' + body.name + '.' });
      if (t.uuid === me.uuid) return json({ ok: false, error: "That's you!" });
      this.upsert(t.uuid, t.name);
      if (this.sql.exec(`SELECT 1 FROM friends WHERE a = ? AND b = ?`, me.uuid, t.uuid).toArray().length) return json({ ok: false, error: t.name + ' is already your friend.' });
      // They already asked you: accept straight away.
      if (this.sql.exec(`SELECT 1 FROM requests WHERE src = ? AND dst = ?`, t.uuid, me.uuid).toArray().length) {
        this.befriend(me.uuid, t.uuid);
        return json({ ok: true, accepted: true, name: t.name });
      }
      const pending = this.sql.exec(`SELECT COUNT(*) AS n FROM requests WHERE src = ?`, me.uuid).toArray()[0].n;
      if (pending >= 100) return json({ ok: false, error: 'Too many pending requests.' });
      this.sql.exec(`INSERT OR REPLACE INTO requests VALUES (?, ?, ?)`, me.uuid, t.uuid, Date.now());
      return json({ ok: true, name: t.name });
    }

    if (path === '/accept') {
      if (!this.sql.exec(`SELECT 1 FROM requests WHERE src = ? AND dst = ?`, other, me.uuid).toArray().length) return json({ ok: false, error: 'That request is gone.' });
      this.befriend(me.uuid, other);
      return json({ ok: true });
    }
    if (path === '/decline') { this.sql.exec(`DELETE FROM requests WHERE src = ? AND dst = ?`, other, me.uuid); return json({ ok: true }); }
    if (path === '/cancel') { this.sql.exec(`DELETE FROM requests WHERE src = ? AND dst = ?`, me.uuid, other); return json({ ok: true }); }
    if (path === '/remove') {
      this.sql.exec(`DELETE FROM friends WHERE (a = ? AND b = ?) OR (a = ? AND b = ?)`, me.uuid, other, other, me.uuid);
      return json({ ok: true });
    }
    return json({ ok: false, error: 'Unknown request.' }, 404);
  }
}
