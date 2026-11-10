// Mini Client game launcher (Node port of the MiniClient.hta launch path).
// Shares .minecraft with the official launcher: versions, libraries, assets, saves, mods.
const fs = require('fs');
const fsp = fs.promises;
const path = require('path');
const { spawn } = require('child_process');

const VERSIONS_URL = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json';
const RT_INDEX = 'https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json';
const MINI_REPO = 'xomaim/miniclient';
const UA = { 'User-Agent': 'MiniClient-App/1.0 (personal launcher)' };
const CHERRY_CAPE = 'iVBORw0KGgoAAAANSUhEUgAAAEAAAAAgCAYAAACinX6EAAABNklEQVR42u2WwamDQBRFz5hPBN0MrqxBSCXCtOFaUkcKSAE2MMXYgjbgIg4Z/2ryEzDwF3GR51wQnnIR7nnPN6osyzgejwsf1DzPii9R8unwAFu8czMAoTDGrBpOpxOSpbTWm3TrWz6DZK374d4Y87ikKlkL/SxrLdZasRCS56DGGKy1q8Z3z8UAkBzy3wDWJH0HKK31cjgcAKiqir7vOZ/PAFwulxfz/X6XeQoE9X1P27Z47/He07YtAHVdy56ANE0BaJpm1XS9XgG43W7iJuAnFEVRvA04zzNlWTIMg8wlWBQF4zhircU5R9d1dF2Hc078yaC01otSf9Oa5/mLYZqmR70si+wlGMY9BA/17v4DQtedc+IBqCzL2LMSdq4IIAKIACKACCACiAAigAggAogAIoBd6hf0V3iEHt9/IQAAAABJRU5ErkJggg==';
const FPS_FLAGS = ['-XX:+UseG1GC', '-XX:+UnlockExperimentalVMOptions', '-XX:G1NewSizePercent=30', '-XX:G1MaxNewSizePercent=40', '-XX:G1HeapRegionSize=8M', '-XX:G1ReservePercent=20', '-XX:MaxGCPauseMillis=50', '-XX:+DisableExplicitGC', '-XX:+AlwaysPreTouch', '-XX:+ParallelRefProcEnabled'];
const FPS_OPTIONS = { enableVsync: 'false', maxFps: '260', entityShadows: 'false', biomeBlendRadius: '1', particles: '2', mipmapLevels: '2', graphicsMode: '0' };

const archKey = process.arch === 'arm64' ? 'arm64' : (process.arch === 'ia32' ? 'x86' : 'x64');
const rtPlatform = { arm64: 'windows-arm64', x86: 'windows-x86', x64: 'windows-x64' }[archKey];

async function getJson(url, headers) {
  const r = await fetch(url, { headers: headers || UA });
  if (!r.ok) throw new Error('HTTP ' + r.status + ' for ' + url);
  return r.json();
}
async function exists(p) { try { await fsp.access(p); return true; } catch (e) { return false; } }
async function sizeOf(p) { try { return (await fsp.stat(p)).size; } catch (e) { return -1; } }
async function needed(it) { const s = await sizeOf(it.path); return s < 0 || (it.size && s !== it.size); }

async function download(url, dest) {
  await fsp.mkdir(path.dirname(dest), { recursive: true });
  const r = await fetch(url, { headers: UA });
  if (!r.ok) throw new Error('HTTP ' + r.status + ' for ' + url);
  const buf = Buffer.from(await r.arrayBuffer());
  const tmp = dest + '.part';
  await fsp.writeFile(tmp, buf);
  await fsp.rename(tmp, dest);
}

// Mojang allow/disallow rules: Windows only, all "features" (demo, quick play flags, custom res) off.
function rulesOk(rules) {
  if (!rules) return true;
  let ok = false;
  for (const r of rules) {
    let m = true;
    if (r.os) { if (r.os.name && r.os.name !== 'windows') m = false; if (r.os.arch && r.os.arch !== archKey) m = false; }
    if (r.features) m = false;
    if (m) ok = r.action === 'allow';
  }
  return ok;
}
const subst = (s, v) => String(s).replace(/\$\{([^}]+)\}/g, (a, k) => (v[k] !== undefined ? v[k] : ''));
function collectArgs(arr, vars, out) {
  for (const a of arr || []) {
    if (typeof a === 'string') { out.push(subst(a, vars)); continue; }
    if (!rulesOk(a.rules)) continue;
    for (const v of [].concat(a.value)) out.push(subst(v, vars));
  }
}
const libKey = (n) => { const p = String(n).split(':'); return p[0] + ':' + p[1]; };
function mavenPath(n) {
  const p = String(n).split(':');
  return p[0].replace(/\./g, '/') + '/' + p[1] + '/' + p[2] + '/' + p[1] + '-' + p[2] + (p[3] ? '-' + p[3] : '') + '.jar';
}

async function modrinthFile(slug, mc) {
  const url = 'https://api.modrinth.com/v2/project/' + slug + '/version?loaders=' + encodeURIComponent('["fabric"]') +
    '&game_versions=' + encodeURIComponent('["' + mc + '"]');
  try {
    const list = await getJson(url);
    if (!list.length) return null;
    const v = list.find((x) => x.version_type === 'release') || list[0];
    if (!v.files || !v.files.length) return null;
    const f = v.files.find((x) => x.primary) || v.files[0];
    return { url: f.url, name: f.filename, size: f.size };
  } catch (e) { return null; }
}
async function miniModFile() {
  let url = 'https://github.com/' + MINI_REPO + '/releases/download/latest/miniclient-mod.jar', stamp = 'latest', size = 0;
  try {
    const rel = await getJson('https://api.github.com/repos/' + MINI_REPO + '/releases/tags/latest');
    const a = (rel.assets || []).find((x) => /miniclient-mod\.jar$/.test(x.name)) || (rel.assets || [])[0];
    if (a) { url = a.browser_download_url; size = a.size || 0; stamp = String(a.updated_at || '').replace(/[^0-9]/g, ''); }
  } catch (e) {}
  return { url, name: 'miniclient-mod-' + stamp + '.jar', size };
}

class Launcher {
  constructor({ cfgDir, log, progress }) {
    this.mcDir = path.join(process.env.APPDATA || path.join(require('os').homedir(), 'AppData', 'Roaming'), '.minecraft');
    this.cfgDir = cfgDir;
    this.log = log || (() => {});
    this.progress = progress || (() => {});
    this.busy = false;
    this.lastLaunch = 0;
    this.proc = null;
  }

  wantedMods(id, o) {
    const out = [{ slug: 'fabric-api' }];
    if (o.builtinOn) {
      out.push(id === '1.21.7' ? { slug: '@mini' } : { slug: 'hud-plus' });
      out.push({ slug: 'sodium' });
      if (o.fpsBoost) for (const s of ['lithium', 'ferrite-core', 'entityculling', 'immediatelyfast']) out.push({ slug: s });
    }
    if (o.capeOn) out.push({ slug: 'client-capes' });
    return out;
  }
  sig(o) { return (o.builtinOn || o.capeOn) ? 'm6' + (o.builtinOn ? 'b' : '') + (o.fpsBoost ? 'f' : '') + (o.capeOn ? 'c' : '') : 'v'; }
  fastFile(id, o) { return path.join(this.cfgDir, 'fast_' + id + '_' + this.sig(o) + '.json'); }

  async fastLoad(id, o) {
    try {
      const d = JSON.parse(await fsp.readFile(this.fastFile(id, o), 'utf8'));
      if (!d || d.id !== id || !d.cp || !d.ver) return null;
      if (!(await exists(path.join(d.rtDir, 'bin', 'java.exe')))) return null;
      for (const p of d.cp.concat(d.addMods || [])) if (!(await exists(p))) return null;
      return d;
    } catch (e) { return null; }
  }

  async prepare(id, o) {
    const fast = await this.fastLoad(id, o);
    if (fast) {
      this.log('Fast start: ' + id + ' already set up.');
      // The Mini Client mod changes often: always pick up the newest build, even on fast start.
      if (o.builtinOn && id === '1.21.7') {
        const mf = await miniModFile();
        const mdir = path.join(this.cfgDir, 'builtin_mods', id), mp = path.join(mdir, mf.name);
        const old = (fast.addMods || []).findIndex((x) => /miniclient-mod-/.test(path.basename(x)));
        if (old < 0 || fast.addMods[old] !== mp) {
          this.progress('Updating Mini Client mod...', 50);
          await download(mf.url, mp);
          if (old < 0) fast.addMods.push(mp); else fast.addMods[old] = mp;
          await fsp.writeFile(this.fastFile(id, o), JSON.stringify(fast));
          this.log('Mini Client mod updated to ' + mf.name);
        }
      }
      return fast;
    }

    this.progress('Reading version info...', 2);
    const manifest = await getJson(VERSIONS_URL);
    const vi = manifest.versions.find((v) => v.id === id);
    if (!vi) throw new Error('Minecraft ' + id + ' was not found in the version list.');
    const ver = await getJson(vi.url);
    if (!ver.arguments || !ver.downloads || !ver.downloads.client) throw new Error('Version ' + id + ' is too old for this launcher (1.19 or later).');

    const q = [], cp = [], vanilla = [];
    const lib = (rel) => path.join(this.mcDir, 'libraries', ...rel.split('/'));
    for (const l of ver.libraries) {
      if (!rulesOk(l.rules)) continue;
      const art = l.downloads && l.downloads.artifact;
      if (!art) continue;
      const lp = lib(art.path);
      q.push({ url: art.url, path: lp, size: art.size }); cp.push(lp); vanilla.push({ key: libKey(l.name), path: lp });
    }

    // Fabric + built-in mods (all-or-nothing, like the HTA)
    let addMods = [], modsNote = '', modsFailed = false;
    if (o.builtinOn || o.capeOn) {
      try {
        this.progress('Setting up mods...', 4);
        const fab = await getJson('https://meta.fabricmc.net/v2/versions/loader/' + id);
        const lv = (fab.find((x) => x.loader && x.loader.stable) || {}).loader;
        if (!lv) throw new Error('Fabric does not support ' + id + ' yet');
        const prof = await getJson('https://meta.fabricmc.net/v2/versions/loader/' + id + '/' + lv.version + '/profile/json');
        const files = (await Promise.all(this.wantedMods(id, o).map((m) => (m.slug === '@mini' ? miniModFile() : modrinthFile(m.slug, id))))).filter(Boolean);
        ver.mainClass = prof.mainClass;
        if (prof.arguments) {
          if (prof.arguments.jvm) ver.arguments.jvm = (ver.arguments.jvm || []).concat(prof.arguments.jvm);
          if (prof.arguments.game) ver.arguments.game = (ver.arguments.game || []).concat(prof.arguments.game);
        }
        const fabKeys = new Set(prof.libraries.map((l) => libKey(l.name)));
        for (const v of vanilla) if (fabKeys.has(v.key)) {
          for (let i = cp.length - 1; i >= 0; i--) if (cp[i] === v.path) cp.splice(i, 1);
          for (let i = q.length - 1; i >= 0; i--) if (q[i].path === v.path) q.splice(i, 1);
        }
        for (const l of prof.libraries) {
          const rel = mavenPath(l.name), lp = lib(rel);
          q.push({ url: (l.url || 'https://maven.fabricmc.net/') + rel, path: lp, size: 0 }); cp.push(lp);
        }
        const mdir = path.join(this.cfgDir, 'builtin_mods', id);
        for (const f of files) { const mp = path.join(mdir, f.name); q.push({ url: f.url, path: mp, size: f.size }); addMods.push(mp); }
        this.log('Fabric ' + lv.version + ' with ' + files.length + ' built-in mod(s).');
      } catch (e) { addMods = []; modsFailed = true; modsNote = e.message; this.log('Mods unavailable: ' + e.message); }
    }

    const jar = path.join(this.mcDir, 'versions', id, id + '.jar');
    q.push({ url: ver.downloads.client.url, path: jar, size: ver.downloads.client.size }); cp.push(jar);

    const ai = ver.assetIndex;
    const idxPath = path.join(this.mcDir, 'assets', 'indexes', ai.id + '.json');
    const idx = await getJson(ai.url);
    await fsp.mkdir(path.dirname(idxPath), { recursive: true });
    await fsp.writeFile(idxPath, JSON.stringify(idx));
    const seen = new Set();
    for (const o2 of Object.values(idx.objects)) {
      if (seen.has(o2.hash)) continue; seen.add(o2.hash);
      const pre = o2.hash.slice(0, 2);
      q.push({ url: 'https://resources.download.minecraft.net/' + pre + '/' + o2.hash, path: path.join(this.mcDir, 'assets', 'objects', pre, o2.hash), size: o2.size });
    }

    // Java: reuse the official launcher's runtime if present, else our own copy.
    const comp = ver.javaVersion ? ver.javaVersion.component : 'jre-legacy';
    const offRt = path.join(this.mcDir, 'runtime', comp, rtPlatform, comp);
    let rtDir = offRt, rtNew = false;
    if (!(await exists(path.join(offRt, 'bin', 'java.exe')))) {
      rtDir = path.join(this.cfgDir, 'runtime', comp);
      rtNew = !(await exists(path.join(rtDir, '.ready')));
    }
    if (rtNew) {
      this.progress('Reading Java runtime info...', 6);
      const all = await getJson(RT_INDEX);
      const lst = all[rtPlatform] && all[rtPlatform][comp];
      if (!lst || !lst.length) throw new Error('No Java runtime ' + comp + ' for ' + rtPlatform);
      const man = await getJson(lst[0].manifest.url);
      for (const [fp, e] of Object.entries(man.files))
        if (e.type === 'file' && e.downloads && e.downloads.raw) q.push({ url: e.downloads.raw.url, path: path.join(rtDir, ...fp.split('/')), size: e.downloads.raw.size });
    }

    await this.fetchAll(q);
    if (rtNew) await fsp.writeFile(path.join(rtDir, '.ready'), 'ok');
    const d = { id, ver, cp, rtDir, addMods, modsNote, modsFailed };
    if (!modsFailed) { await fsp.mkdir(this.cfgDir, { recursive: true }); await fsp.writeFile(this.fastFile(id, o), JSON.stringify(d)); }
    return d;
  }

  async fetchAll(q) {
    this.log('Checking ' + q.length + ' files.');
    let done = 0, i = 0, lastPct = -1;
    const worker = async () => {
      while (i < q.length) {
        const it = q[i++];
        if (await needed(it)) {
          try { await download(it.url, it.path); } catch (e) { await download(it.url, it.path); }
        }
        done++;
        const pct = Math.floor(8 + done * 90 / q.length);
        if (pct !== lastPct) { lastPct = pct; this.progress('Downloading game files ' + done + ' / ' + q.length, pct); }
      }
    };
    await Promise.all(Array.from({ length: 16 }, worker));
  }

  async ensureCape() {
    const dest = path.join(this.mcDir, 'Capes', 'Mini Client.png');
    if (await exists(dest)) return;
    await fsp.mkdir(path.dirname(dest), { recursive: true });
    await fsp.writeFile(dest, Buffer.from(CHERRY_CAPE, 'base64'));
  }
  async applyFpsOptions() {
    const of = path.join(this.mcDir, 'options.txt');
    let lines = [];
    const seen = new Set();
    try {
      for (const l of (await fsp.readFile(of, 'utf8')).split(/\r?\n/)) {
        if (!l) continue;
        const k = l.split(':')[0];
        if (FPS_OPTIONS[k] !== undefined) { lines.push(k + ':' + FPS_OPTIONS[k]); seen.add(k); } else lines.push(l);
      }
    } catch (e) {}
    for (const k of Object.keys(FPS_OPTIONS)) if (!seen.has(k)) lines.push(k + ':' + FPS_OPTIONS[k]);
    await fsp.writeFile(of, lines.join('\n') + '\n');
  }
  // The old HTA launcher enabled a pink "Minicraft" resource pack; Mini Client is black and white now.
  async dropOldPack() {
    const of = path.join(this.mcDir, 'options.txt');
    let t; try { t = await fsp.readFile(of, 'utf8'); } catch (e) { return; }
    let changed = false;
    t = t.replace(/^(resourcePacks|incompatibleResourcePacks):(.*)$/mg, (line, k, v) => {
      try { const a = JSON.parse(v), b = a.filter((x) => !/minicraft/i.test(x)); if (b.length !== a.length) { changed = true; return k + ':' + JSON.stringify(b); } } catch (e) {}
      return line;
    });
    if (changed) { await fsp.writeFile(of, t); this.log('Turned off the old Minicraft resource pack.'); }
  }
  async userMods() {
    try { return (await fsp.readdir(path.join(this.mcDir, 'mods'))).filter((n) => /\.jar$/i.test(n) && !/^minicraft-/i.test(n)); } catch (e) { return []; }
  }
  async syncMods(list) {
    const md = path.join(this.mcDir, 'mods');
    await fsp.mkdir(md, { recursive: true });
    const want = new Map(list.map((p) => [('minicraft-' + path.basename(p)).toLowerCase(), p]));
    for (const n of await fsp.readdir(md)) if (/^minicraft-.*\.jar$/i.test(n) && !want.has(n.toLowerCase())) await fsp.rm(path.join(md, n), { force: true }).catch(() => {});
    for (const p of list) {
      const dest = path.join(md, 'minicraft-' + path.basename(p));
      if ((await sizeOf(dest)) === (await sizeOf(p))) continue;
      await fsp.copyFile(p, dest);
    }
  }

  // account: { name, uuid, token }; o: { version, ram, fpsBoost, builtinOn, capeOn, fullscreen, server }
  async launch(account, o) {
    if (this.busy) throw new Error('Already preparing a launch.');
    if (this.proc && this.proc.exitCode === null) throw new Error('Minecraft is already running.');
    this.busy = true;
    try {
      const id = o.version || '1.21.7';
      const c = await this.prepare(id, o);
      const ver = c.ver, java = path.join(c.rtDir, 'bin', 'java.exe');
      if (!(await exists(java))) throw new Error('Java was not found at ' + java);
      this.progress('Starting Minecraft...', 99);
      const fwd = (p) => String(p).replace(/\\/g, '/');
      const natives = path.join(this.mcDir, 'versions', id, 'natives');
      await fsp.mkdir(natives, { recursive: true });
      const vars = {
        auth_player_name: account.name, version_name: id, game_directory: fwd(this.mcDir),
        assets_root: fwd(path.join(this.mcDir, 'assets')), assets_index_name: ver.assetIndex.id,
        auth_uuid: account.uuid, auth_access_token: account.token, clientid: '0', auth_xuid: '0',
        user_type: 'msa', version_type: ver.type || 'release', natives_directory: fwd(natives),
        launcher_name: 'MiniClient', launcher_version: '1.0', classpath: c.cp.map(fwd).join(';')
      };
      await this.dropOldPack().catch(() => {});
      if (o.capeOn) await this.ensureCape().catch((e) => this.log('Cape: ' + e.message));
      if (o.fpsBoost) await this.applyFpsOptions().catch((e) => this.log('FPS options: ' + e.message));
      await this.syncMods(c.addMods || []).catch((e) => this.log('Mods copy: ' + e.message));

      const ram = Math.min(32, Math.max(1, parseInt(o.ram, 10) || 2));
      const jvm = [];
      collectArgs(ver.arguments.jvm, vars, jvm);
      if (!jvm.includes('-cp') && !jvm.includes('-classpath')) jvm.push('-cp', vars.classpath);
      const args = ['-Xmx' + ram + 'G', '-Xms512M', ...jvm, ...(o.fpsBoost ? FPS_FLAGS : []), ver.mainClass];
      collectArgs(ver.arguments.game, vars, args);
      if (o.server) args.push('--quickPlayMultiplayer', o.server);
      if (o.fullscreen) args.push('--fullscreen');

      // Args file keeps the token off the visible command line and avoids Windows' length limit.
      const af = path.join(this.cfgDir, 'launch_args.txt');
      await fsp.writeFile(af, args.map((a) => '"' + String(a).replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '"').join('\n'));
      const logFd = fs.openSync(path.join(this.cfgDir, 'game.log'), 'w');
      const p = spawn(java, ['@' + af], { cwd: this.mcDir, detached: true, stdio: ['ignore', logFd, logFd], windowsHide: true });
      p.on('error', (e) => this.log('Java failed to start: ' + e.message));
      p.unref();
      this.proc = p;
      this.lastLaunch = Date.now();
      setTimeout(() => fsp.rm(af, { force: true }).catch(() => {}), 60000);
      return { ok: true, id, mods: (c.addMods || []).length, modsNote: c.modsFailed ? c.modsNote : '' };
    } finally { this.busy = false; }
  }
  running() { return !!(this.proc && this.proc.exitCode === null); }
}

module.exports = { Launcher };
