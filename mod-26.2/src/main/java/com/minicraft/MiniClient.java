package com.minicraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class MiniClient implements ClientModInitializer {
	public static Button capeBtn = null;
	private int tick = 0;
	private static boolean iconSet = false;
	private static boolean darkDone = false;

	/** Windows draws its own title bar for the game window. Make it black with only the logo (no white bar, no title text). */
	public interface Dwm extends com.sun.jna.Library {
		int DwmSetWindowAttribute(com.sun.jna.Pointer hwnd, int attr, com.sun.jna.ptr.IntByReference value, int size);
	}

	private static void darkTitle(Minecraft mc) {
		if (darkDone) return;
		darkDone = true;
		if (!System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")) return;
		try {
			long hwnd = org.lwjgl.glfw.GLFWNativeWin32.glfwGetWin32Window(mc.getWindow().getWindow());
			if (hwnd == 0) return;
			com.sun.jna.Pointer h = new com.sun.jna.Pointer(hwnd);
			Dwm d = com.sun.jna.Native.load("dwmapi", Dwm.class);
			dwm(d, h, 20, 1);          // dark mode: white minimise / maximise / close on a dark bar
			dwm(d, h, 35, 0x000000);   // caption colour: black (Windows 11)
			dwm(d, h, 36, 0x000000);   // title text colour: black = hidden, only the logo shows
			dwm(d, h, 34, 0x1A1A1A);   // thin dark border
		} catch (Throwable ignored) {
		}
	}

	private static void dwm(Dwm d, com.sun.jna.Pointer h, int attr, int value) {
		try { d.DwmSetWindowAttribute(h, attr, new com.sun.jna.ptr.IntByReference(value), 4); } catch (Throwable ignored) {}
	}

	/** Swap the grass-block window icon for the Mini Client logo. */
	private static void setIcon(Minecraft mc) {
		if (iconSet) return;
		iconSet = true;
		try (java.io.InputStream in = MiniClient.class.getResourceAsStream("/assets/miniclient/icon.png");
		     org.lwjgl.system.MemoryStack st = org.lwjgl.system.MemoryStack.stackPush()) {
			byte[] bytes = in.readAllBytes();
			java.nio.ByteBuffer file = org.lwjgl.system.MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
			java.nio.IntBuffer w = st.mallocInt(1), h = st.mallocInt(1), c = st.mallocInt(1);
			java.nio.ByteBuffer px = org.lwjgl.stb.STBImage.stbi_load_from_memory(file, w, h, c, 4);
			org.lwjgl.system.MemoryUtil.memFree(file);
			if (px == null) return;
			org.lwjgl.glfw.GLFWImage.Buffer imgs = org.lwjgl.glfw.GLFWImage.malloc(1, st);
			imgs.position(0).width(w.get(0)).height(h.get(0)).pixels(px);
			GLFW.glfwSetWindowIcon(mc.getWindow().getWindow(), imgs);
			org.lwjgl.stb.STBImage.stbi_image_free(px);
		} catch (Throwable ignored) {
		}
	}

	@Override
	public void onInitializeClient() {
		Config.load();

		HudRenderCallback.EVENT.register((g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			HudRenderer.poll(mc);
			if (mc.options.hideGui) return;
			if (mc.screen instanceof HudEditorScreen) return;
			HudRenderer.draw(g, mc);
		});

		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			tick++;
			HostWorldScreen.tick(mc);
			if (Config.INSTANCE.titleStyle && mc.screen instanceof TitleScreen) {
				capeBtn = null;
				for (net.minecraft.client.gui.components.AbstractWidget wd : Screens.getButtons(mc.screen)) {
					if (wd instanceof Button && wd.getMessage().getString().toLowerCase().contains("cape")) { capeBtn = (Button) wd; break; }
				}
				mc.setScreen(new MiniTitleScreen());
			}
			if (tick % 20 == 0) {
				GLFW.glfwSetWindowTitle(mc.getWindow().getWindow(), "Mini Client 1.21.7");
				setIcon(mc);
				darkTitle(mc);
			}
		});

		ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
			if (screen instanceof PauseScreen) {
				ScreenEvents.beforeRender(screen).register((sc, g, mx, my, dt) -> {
					g.fillGradient(0, 0, sc.width, sc.height, 0xE0070707, 0xE01C1C1C);
					g.fill(0, sc.height / 2 - 70, 3, sc.height / 2 + 70, 0xFFEBEBEB);
					g.drawCenteredString(client.font, "MINICRAFT", sc.width / 2, 14, 0xFFEBEBEB);
				});
				Screens.getButtons(screen).add(
					Button.builder(Component.literal("Mini Client"), b -> client.setScreen(new MiniSettingsScreen(screen)))
						.bounds(6, 6, 90, 20).build());
			}
		});
	}
}
