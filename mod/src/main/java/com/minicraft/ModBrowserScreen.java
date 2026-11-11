package com.minicraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-game mod store: search Modrinth for Fabric 1.21.7 mods and install them in one click. */
public class ModBrowserScreen extends MiniPage {
	private static final String MC = "1.21.7";
	private static final String FABRIC_API = "P7dR8mSH";
	private static final int ROW = 44;

	static final class Mod {
		String id, slug, title, author, desc, icon;
		long downloads;
		String file;     // for the Installed tab
		String state = "";
	}

	private static final Map<String, ResourceLocation> ICONS = new HashMap<>();
	private static final Set<String> ICON_LOADING = new HashSet<>();
	private static boolean changed = false;

	private final List<Mod> results = new ArrayList<>();
	private final List<Mod> installed = new ArrayList<>();
	private boolean tabInstalled = false;
	private EditBox search;
	private String lastQuery = null;
	private long typedAt = 0;
	private double scroll = 0;
	private String note = "Loading popular mods...";
	private int searchId = 0;

	public ModBrowserScreen(Screen parent) {
		super(parent, "Mods");
	}

	private static Path modsDir() { return FabricLoader.getInstance().getGameDir().resolve("mods"); }
	private static Path store() { return FabricLoader.getInstance().getConfigDir().resolve("miniclient-mods.json"); }
	private int listTop() { return top + 30; }
	private int listBottom() { return this.height - 22; }

	@Override
	protected void build() {
		String q = search != null ? search.getValue() : "";
		search = new EditBox(this.font, left, top, pw - 150, 20, Component.literal("Search mods"));
		search.setHint(Component.literal("Search mods, e.g. minimap"));
		search.setMaxLength(60);
		search.setValue(q);
		search.setResponder(s -> typedAt = System.currentTimeMillis());
		addRenderableWidget(search);
		button(left + pw - 144, top, 70, 20, (tabInstalled ? "" : "● ") + "Browse", () -> { tabInstalled = false; scroll = 0; rebuildWidgets(); });
		button(left + pw - 70, top, 70, 20, (tabInstalled ? "● " : "") + "Installed", () -> { tabInstalled = true; scroll = 0; loadInstalled(); rebuildWidgets(); });
		if (lastQuery == null) { lastQuery = ""; doSearch(""); }
		if (tabInstalled) loadInstalled();
	}

	@Override
	public void tick() {
		super.tick();
		if (!tabInstalled && typedAt > 0 && System.currentTimeMillis() - typedAt > 350) {
			typedAt = 0;
			String q = search.getValue().trim();
			if (!q.equals(lastQuery)) { lastQuery = q; doSearch(q); }
		}
	}

	// ---------- data ----------
	private void doSearch(String q) {
		final int my = ++searchId;
		note = q.isEmpty() ? "Loading popular mods..." : "Searching...";
		new Thread(() -> {
			try {
				String facets = "[[\"categories:fabric\"],[\"versions:" + MC + "\"],[\"project_type:mod\"]]";
				String url = "https://api.modrinth.com/v2/search?limit=40&index=" + (q.isEmpty() ? "downloads" : "relevance")
					+ "&facets=" + MiniHttp.enc(facets) + (q.isEmpty() ? "" : "&query=" + MiniHttp.enc(q));
				JsonArray hits = MiniHttp.json(url).getAsJsonObject().getAsJsonArray("hits");
				List<Mod> list = new ArrayList<>();
				Map<String, String> inst = readStore();
				for (JsonElement e : hits) {
					JsonObject h = e.getAsJsonObject();
					Mod m = new Mod();
					m.id = str(h, "project_id"); m.slug = str(h, "slug"); m.title = str(h, "title");
					m.author = str(h, "author"); m.desc = str(h, "description"); m.icon = str(h, "icon_url");
					m.downloads = h.has("downloads") ? h.get("downloads").getAsLong() : 0;
					if (FABRIC_API.equals(m.id)) m.state = "Built in";
					else if (inst.containsKey(m.id) && Files.exists(modsDir().resolve(inst.get(m.id)))) m.state = "Installed";
					list.add(m);
				}
				this.minecraft.execute(() -> {
					if (my != searchId) return;
					results.clear(); results.addAll(list); scroll = 0;
					note = list.isEmpty() ? "No Fabric " + MC + " mods found." : "";
				});
			} catch (Exception e) {
				this.minecraft.execute(() -> { if (my == searchId) note = "Could not reach Modrinth: " + e.getMessage(); });
			}
		}, "MiniClient-ModSearch").start();
	}

	private static String str(JsonObject o, String k) {
		return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
	}

	private static Map<String, String> readStore() {
		Map<String, String> m = new HashMap<>();
		try {
			JsonObject o = JsonParser.parseString(Files.readString(store())).getAsJsonObject();
			for (String k : o.keySet()) m.put(k, o.get(k).getAsString());
		} catch (Exception ignored) {
		}
		return m;
	}

	private static synchronized void writeStore(String id, String file) {
		Map<String, String> m = readStore();
		if (file == null) m.remove(id); else m.put(id, file);
		JsonObject o = new JsonObject();
		m.forEach(o::addProperty);
		try { Files.writeString(store(), o.toString()); } catch (Exception ignored) {}
	}

	private void loadInstalled() {
		installed.clear();
		Map<String, String> inst = readStore();
		Map<String, String> byFile = new HashMap<>();
		inst.forEach((k, v) -> byFile.put(v, k));
		Set<String> pending = pendingRemovals();
		try (var s = Files.list(modsDir())) {
			s.map(p -> p.getFileName().toString())
				.filter(n -> n.toLowerCase().endsWith(".jar") && !n.toLowerCase().startsWith("minicraft-"))
				.sorted(String.CASE_INSENSITIVE_ORDER)
				.forEach(n -> {
					Mod m = new Mod();
					m.file = n;
					m.title = n.replaceAll("(?i)\\.jar$", "");
					m.id = byFile.getOrDefault(n, "");
					m.state = pending.contains(n) ? "Removed" : "";
					installed.add(m);
				});
		} catch (Exception ignored) {
		}
	}

	private static Path removeList() { return FabricLoader.getInstance().getConfigDir().resolve("miniclient-remove.txt"); }

	private static Set<String> pendingRemovals() {
		Set<String> s = new HashSet<>();
		try { for (String l : Files.readAllLines(removeList())) if (!l.isBlank()) s.add(l.trim()); } catch (Exception ignored) {}
		return s;
	}

	private void install(Mod m) {
		if (!m.state.isEmpty() && !m.state.equals("Failed")) return;
		m.state = "Installing";
		new Thread(() -> {
			String res;
			try {
				int n = installTree(m.id, new HashSet<>(), 0);
				res = "Installed";
				changed = true;
				if (n > 1) res = "Installed";
			} catch (Exception e) {
				res = "Failed";
			}
			final String r = res;
			this.minecraft.execute(() -> m.state = r);
		}, "MiniClient-ModInstall").start();
	}

	/** Installs a project and its required dependencies. Returns how many files were added. */
	private static int installTree(String projectId, Set<String> seen, int depth) throws Exception {
		if (projectId == null || projectId.isEmpty() || FABRIC_API.equals(projectId) || !seen.add(projectId) || depth > 3) return 0;
		Map<String, String> inst = readStore();
		if (inst.containsKey(projectId) && Files.exists(modsDir().resolve(inst.get(projectId)))) return 0;
		String url = "https://api.modrinth.com/v2/project/" + projectId + "/version?loaders=" + MiniHttp.enc("[\"fabric\"]")
			+ "&game_versions=" + MiniHttp.enc("[\"" + MC + "\"]");
		JsonArray vers = MiniHttp.json(url).getAsJsonArray();
		if (vers.isEmpty()) throw new RuntimeException("no " + MC + " version");
		JsonObject v = vers.get(0).getAsJsonObject();
		for (JsonElement e : vers) if ("release".equals(str(e.getAsJsonObject(), "version_type"))) { v = e.getAsJsonObject(); break; }
		JsonArray files = v.getAsJsonArray("files");
		JsonObject f = files.get(0).getAsJsonObject();
		for (JsonElement e : files) if (e.getAsJsonObject().has("primary") && e.getAsJsonObject().get("primary").getAsBoolean()) { f = e.getAsJsonObject(); break; }
		String name = str(f, "filename").replaceAll("[\\\\/:*?\"<>|]", "_");
		if (name.toLowerCase().startsWith("minicraft-")) name = "m-" + name;
		Files.createDirectories(modsDir());
		MiniHttp.download(str(f, "url"), modsDir().resolve(name));
		writeStore(projectId, name);
		int count = 1;
		if (v.has("dependencies")) for (JsonElement e : v.getAsJsonArray("dependencies")) {
			JsonObject d = e.getAsJsonObject();
			if ("required".equals(str(d, "dependency_type"))) {
				try { count += installTree(str(d, "project_id"), seen, depth + 1); } catch (Exception ignored) {}
			}
		}
		return count;
	}

	private void remove(Mod m) {
		if ("Removed".equals(m.state)) return;
		Path p = modsDir().resolve(m.file);
		try {
			Files.delete(p);
		} catch (Exception e) {
			// Windows keeps loaded mods locked: the launcher deletes it before the next start.
			try {
				Files.writeString(removeList(), m.file + System.lineSeparator(), StandardCharsets.UTF_8,
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
			} catch (Exception ignored) {
			}
		}
		if (!m.id.isEmpty()) writeStore(m.id, null);
		for (Mod r : results) if (r.id != null && r.id.equals(m.id)) r.state = "";
		m.state = "Removed";
		changed = true;
	}

	// ---------- icons ----------
	private ResourceLocation icon(Mod m) {
		if (m.icon == null || m.icon.isEmpty() || m.slug == null) return null;
		if (ICONS.containsKey(m.slug)) return ICONS.get(m.slug);
		if (ICON_LOADING.add(m.slug)) {
			final String slug = m.slug, url = m.icon;
			new Thread(() -> {
				try {
					byte[] b = MiniHttp.bytes(url);
					this.minecraft.execute(() -> {
						try {
							NativeImage img = NativeImage.read(new ByteArrayInputStream(b));
							DynamicTexture dt = new DynamicTexture(() -> "miniclient_modicon", img);
							try { dt.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(dt, true, false); } catch (Throwable ignored) {}
							ResourceLocation id = ResourceLocation.fromNamespaceAndPath("miniclient", "modicon/" + slug.toLowerCase().replaceAll("[^a-z0-9_.-]", "_"));
							this.minecraft.getTextureManager().register(id, dt);
							ICONS.put(slug, id);
						} catch (Throwable t) {
							ICONS.put(slug, null);   // webp/gif icons: show the letter instead
						}
					});
				} catch (Exception e) {
					this.minecraft.execute(() -> ICONS.put(slug, null));
				}
			}, "MiniClient-ModIcon").start();
		}
		return null;
	}

	// ---------- drawing ----------
	private List<Mod> list() { return tabInstalled ? installed : results; }

	private int maxScroll() {
		return Math.max(0, list().size() * ROW - (listBottom() - listTop()));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);
		g.drawString(this.font, tabInstalled ? "Mods in your mods folder." : "Fabric mods for Minecraft " + MC + " from Modrinth.", left + 70, 20, 0xFF8A8A8A, false);
		int lt = listTop(), lb = listBottom();
		List<Mod> l = list();
		String empty = tabInstalled ? (installed.isEmpty() ? "No extra mods yet. Install some from Browse." : "") : note;
		if (!empty.isEmpty()) g.drawCenteredString(this.font, empty, this.width / 2, lt + 30, 0xFF9A9A9A);
		g.enableScissor(left - 2, lt, left + pw + 2, lb);
		for (int i = 0; i < l.size(); i++) {
			int y = lt + i * ROW - (int) scroll;
			if (y + ROW < lt || y > lb) continue;
			Mod m = l.get(i);
			boolean hot = mouseX >= left && mouseX < left + pw && mouseY >= y && mouseY < y + ROW - 4 && mouseY >= lt && mouseY < lb;
			card(g, left, y, pw, ROW - 4, hot);
			// icon
			ResourceLocation ic = tabInstalled ? null : icon(m);
			if (ic != null) g.blit(RenderPipelines.GUI_TEXTURED, ic, left + 6, y + 6, 0f, 0f, 28, 28, 28, 28, 0xFFFFFFFF);
			else {
				GlassButton.roundRect(g, left + 6, y + 6, 28, 28, 5, 0xFF222226);
				String letter = m.title.isEmpty() ? "?" : m.title.substring(0, 1).toUpperCase();
				g.drawCenteredString(this.font, letter, left + 20, y + 16, 0xFFFFFFFF);
			}
			int tx = left + 42, tw = pw - 42 - 90;
			if (tabInstalled) {
				g.drawString(this.font, fit(m.title, tw), tx, y + 9, 0xFFFFFFFF, false);
				g.drawString(this.font, fit(m.file, tw), tx, y + 22, 0xFF8A8A8A, false);
			} else {
				String head = m.title;
				g.drawString(this.font, fit(head, tw), tx, y + 6, 0xFFFFFFFF, false);
				String by = "by " + m.author + "  ·  " + fmt(m.downloads) + " downloads";
				g.drawString(this.font, fit(by, tw), tx, y + 17, 0xFF8A8A8A, false);
				g.drawString(this.font, fit(m.desc, tw), tx, y + 28, 0xFFBDBDBD, false);
			}
			// action pill
			String label = actionLabel(m);
			int bx = left + pw - 82, by = y + (ROW - 4) / 2 - 9;
			boolean clickable = label.equals("Install") || label.equals("Retry") || label.equals("Remove");
			boolean bh = clickable && mouseX >= bx && mouseX < bx + 74 && mouseY >= by && mouseY < by + 18 && mouseY >= lt && mouseY < lb;
			int fill = !clickable ? 0xFF1E1E22 : (bh ? 0xFFFFFFFF : 0xFFE8E8E8);
			if (label.equals("Remove")) fill = bh ? 0xFF3A3A40 : 0xFF26262B;
			GlassButton.roundRect(g, bx, by, 74, 18, 4, fill);
			int tc = !clickable || label.equals("Remove") ? 0xFFDADADA : 0xFF000000;
			g.drawCenteredString(this.font, label, bx + 37, by + 5, tc);
		}
		g.disableScissor();
		if (maxScroll() > 0) {
			int track = lb - lt, bar = Math.max(20, track * track / (track + maxScroll()));
			int by = lt + (int) ((track - bar) * (scroll / maxScroll()));
			GlassButton.roundRect(g, left + pw + 5, by, 3, bar, 1, 0x55FFFFFF);
		}
		if (changed) g.drawCenteredString(this.font, "Restart Minecraft to load your mod changes.", this.width / 2, this.height - 14, 0xFFFFFFFF);
	}

	private String actionLabel(Mod m) {
		if (tabInstalled) return "Removed".equals(m.state) ? "Removed" : "Remove";
		switch (m.state) {
			case "Installing": return "Installing...";
			case "Installed": return "✔ Installed";
			case "Built in": return "Built in";
			case "Failed": return "Retry";
			default: return "Install";
		}
	}

	private static String fmt(long n) {
		if (n >= 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fM", n / 1e6);
		if (n >= 1_000) return String.format(java.util.Locale.ROOT, "%.1fK", n / 1e3);
		return Long.toString(n);
	}

	@Override
	public boolean mouseClicked(double mx, double my, int button) {
		if (super.mouseClicked(mx, my, button)) return true;
		int lt = listTop(), lb = listBottom();
		if (button != 0 || my < lt || my >= lb || mx < left || mx >= left + pw) return false;
		int i = (int) ((my - lt + scroll) / ROW);
		List<Mod> l = list();
		if (i < 0 || i >= l.size()) return false;
		int y = lt + i * ROW - (int) scroll;
		int bx = left + pw - 82, by = y + (ROW - 4) / 2 - 9;
		if (mx >= bx && mx < bx + 74 && my >= by && my < by + 18) {
			Mod m = l.get(i);
			if (tabInstalled) remove(m); else install(m);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		scroll = Math.max(0, Math.min(maxScroll(), scroll - sy * 24));
		return true;
	}
}
