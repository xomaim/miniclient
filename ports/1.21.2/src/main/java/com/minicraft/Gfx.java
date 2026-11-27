package com.minicraft;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/** Version glue for Minecraft 1.21.2: textured quads, the pose stack, ids and dynamic textures. */
public final class Gfx {
	public static void blit(GuiGraphics g, ResourceLocation t, int x, int y, int w, int h, int argb) {
		g.blit(net.minecraft.client.renderer.RenderType::guiTextured, t, x, y, 0f, 0f, w, h, w, h, argb);
	}
	public static void push(GuiGraphics g) { g.pose().pushPose(); }
	public static void pop(GuiGraphics g) { g.pose().popPose(); }
	public static void translate(GuiGraphics g, float x, float y) { g.pose().translate(x, y, 0f); }
	public static void scale(GuiGraphics g, float x, float y) { g.pose().scale(x, y, 1f); }
	public static ResourceLocation id(String ns, String path) { return ResourceLocation.fromNamespaceAndPath(ns, path); }
	public static DynamicTexture dyn(NativeImage img) { return new DynamicTexture(img); }
}
