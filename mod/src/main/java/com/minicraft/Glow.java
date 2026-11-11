package com.minicraft;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

/** A soft round glow drawn from a smooth texture, so background lights are not pixel squares. */
public final class Glow {
	private static ResourceLocation tex;
	private static boolean tried;

	private static ResourceLocation tex() {
		if (tried) return tex;
		tried = true;
		try {
			int n = 64;
			BufferedImage bi = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB);
			for (int y = 0; y < n; y++)
				for (int x = 0; x < n; x++) {
					double dx = (x + 0.5 - n / 2.0) / (n / 2.0), dy = (y + 0.5 - n / 2.0) / (n / 2.0);
					double d = Math.sqrt(dx * dx + dy * dy);
					double core = Math.max(0, 1 - d / 0.28);                 // bright centre
					double halo = Math.pow(Math.max(0, 1 - d), 2.4) * 0.55;  // soft falloff
					int a = (int) Math.round(Math.min(1, core + halo) * 255);
					bi.setRGB(x, y, (a << 24) | 0xFFFFFF);
				}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			javax.imageio.ImageIO.write(bi, "png", out);
			NativeImage img = NativeImage.read(new ByteArrayInputStream(out.toByteArray()));
			DynamicTexture dt = new DynamicTexture(() -> "miniclient_glow", img);
			try { dt.getClass().getMethod("setFilter", boolean.class, boolean.class).invoke(dt, true, false); } catch (Throwable ignored) {}
			ResourceLocation id = ResourceLocation.fromNamespaceAndPath("miniclient", "glow_dyn");
			Minecraft.getInstance().getTextureManager().register(id, dt);
			tex = id;
		} catch (Throwable e) {
			tex = null;
		}
		return tex;
	}

	/** Draws a glow centred on (cx, cy). size = full diameter in GUI units, alpha 0..255. */
	public static void draw(GuiGraphics g, float cx, float cy, float size, int alpha) {
		ResourceLocation t = tex();
		int s = Math.max(2, Math.round(size));
		int x = Math.round(cx - s / 2f), y = Math.round(cy - s / 2f);
		if (t != null) {
			g.blit(RenderPipelines.GUI_TEXTURED, t, x, y, 0f, 0f, s, s, s, s, (Math.min(255, alpha) << 24) | 0xFFFFFF);
		} else {
			int r = Math.max(1, s / 4);
			g.fill(Math.round(cx) - r, Math.round(cy) - r, Math.round(cx) + r, Math.round(cy) + r, (alpha / 2) << 24 | 0xFFFFFF);
		}
	}
}
