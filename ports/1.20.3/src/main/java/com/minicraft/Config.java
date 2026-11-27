package com.minicraft;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
	public boolean showFps = true;
	public boolean showCps = true;
	public boolean showPing = false;
	public boolean showCoords = false;
	public boolean showTime = false;
	public int bg = 0;        // 0 = transparent, 1 = dark
	public int scale = 100;   // percent
	public int x = 6;
	public int y = 6;
	public boolean titleStyle = true;

	public static Config INSTANCE = new Config();

	private static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("miniclient.json");
	}

	public static void load() {
		try {
			Path p = path();
			if (Files.exists(p)) {
				Config c = new Gson().fromJson(Files.readString(p), Config.class);
				if (c != null) INSTANCE = c;
			}
		} catch (Exception e) {
			INSTANCE = new Config();
		}
	}

	public static void save() {
		try {
			Files.writeString(path(), new GsonBuilder().setPrettyPrinting().create().toJson(INSTANCE));
		} catch (Exception ignored) {
		}
	}
}
