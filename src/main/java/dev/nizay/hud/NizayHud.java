package dev.nizay.hud;

import dev.nizay.Config;
import dev.nizay.NizayClient;
import dev.nizay.gui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * HUD: плашка-ватермарк (логотип | пинг | FPS) и плашка координат.
 * Пока открыт чат (T) — элементы можно таскать мышью, а за точку в правом нижнем углу менять размер.
 * Мышь читаем напрямую из GLFW, поэтому не зависим от API экранов.
 */
public final class NizayHud {
	private NizayHud() {}

	private static final int BG = 0xE0111115, LINE = 0xFF2A2A31, TXT = 0xFFF2F2F6, DIM = 0xFF8E8E99, WHITE = 0xFFEDEDF2;

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
			Identifier.fromNamespaceAndPath("nizay", "hud"), NizayHud::render);
	}

	// ------------------------------------------------------------------ размеры (в единицах при scale = 1)
	private static float markW() {
		String ms = pingText(), fps = Minecraft.getInstance().getFps() + " FPS";
		return 14 + 22 + Ui.width("Nizay", 10, true) + 20 + Ui.width(ms, 9.5f, false) + 20 + Ui.width(fps, 9.5f, false) + 6;
	}
	private static final float MARK_H = 28;
	private static float coordsW() { return 156; }
	private static final float COORDS_H = 50;

	private static String pingText() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null && mc.player != null) {
			PlayerInfo pi = mc.getConnection().getPlayerInfo(mc.player.getUUID());
			if (pi != null && pi.getLatency() > 0) return pi.getLatency() + " ms";
		}
		return "-- ms";
	}

	// ------------------------------------------------------------------ рисование
	private static void drawMark(float w) {
		Minecraft mc = Minecraft.getInstance();
		Ui.shadow(0, 0, w, MARK_H, 14, 0x000000, 120, 8);
		Ui.rectB(0, 0, w, MARK_H, 14, BG, LINE);
		Ui.rectB(5, 5, 18, 18, 6, 0xFF17171C, LINE);
		Ui.textC("N", 14, 7.8f, 11, WHITE, true);
		float x = 29;
		Ui.text("Nizay", x, 8.6f, 10, WHITE, true);
		x += Ui.width("Nizay", 10, true) + 9;
		Ui.text("/", x, 8.6f, 10, 0xFF4A4A55, false); x += 10;
		String ms = pingText(); Ui.text(ms, x, 9, 9.5f, DIM, false); x += Ui.width(ms, 9.5f, false) + 9;
		Ui.text("/", x, 8.6f, 10, 0xFF4A4A55, false); x += 10;
		Ui.text(mc.getFps() + " FPS", x, 9, 9.5f, DIM, false);
	}

	private static void drawCoords(float w) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer p = mc.player;
		Ui.shadow(0, 0, w, COORDS_H, 12, 0x000000, 120, 8);
		Ui.rectB(0, 0, w, COORDS_H, 12, BG, LINE);
		Ui.rectB(9, 9, 14, 14, 5, 0xFF17171C, LINE);
		Ui.circle(16, 16, 2.2f, WHITE);
		Ui.text("Coordinates", 30, 10, 10, TXT, true);
		String xyz = p == null ? "-" : String.format("%d  %d  %d", (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
		Ui.text("XYZ", 12, 30, 9, DIM, false);
		Ui.text(xyz, 38, 29.5f, 9.5f, TXT, true);
		if (p != null) {
			String d = p.getDirection().getName(); d = Character.toUpperCase(d.charAt(0)) + d.substring(1);
			Ui.textR(d, w - 12, 30, 9, DIM, false);
		}
	}

	// ------------------------------------------------------------------ состояние редактора
	private static int dragEl = -1;          // 0 = watermark, 1 = coords
	private static boolean resizing, wasDown;
	private static float grabX, grabY, startScale, startW;

	private static void render(GuiGraphics g, net.minecraft.client.DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui) return;
		boolean edit = mc.screen instanceof ChatScreen;
		int sw = mc.getWindow().getGuiScaledWidth(), sh = mc.getWindow().getGuiScaledHeight();
		Config c = Config.I;
		Config.HudEl[] els = {c.hudMark, c.hudCoords};
		float[] ws = {markW(), coordsW()}, hs = {MARK_H, COORDS_H};

		if (edit) handleMouse(mc, els, ws, hs, sw, sh); else { if (dragEl >= 0) Config.save(); dragEl = -1; wasDown = false; }

		for (int i = 0; i < 2; i++) {
			Config.HudEl e = els[i];
			if (!e.on) continue;
			float x = e.x * sw, y = e.y * sh;
			Ui.begin(g, x, y, e.scale);
			try {
				if (i == 0) drawMark(ws[0]); else drawCoords(ws[1]);
				if (edit) {
					boolean active = dragEl == i;
					Ui.rectB(-3, -3, ws[i] + 6, hs[i] + 6, 12, 0x00000000, active ? WHITE : 0x88EDEDF2);
					Ui.circle(ws[i] + 3, hs[i] + 3, 4.2f, WHITE);
				}
			} finally { Ui.end(); }
		}
		if (edit) {
			String hint = "Drag panels to move  |  drag the corner dot to resize";
			Ui.begin(g, sw / 2f - 120, sh - 64, 1f);
			try {
				Ui.rectB(0, 0, 240, 18, 9, BG, LINE);
				Ui.textC(hint, 120, 4.5f, 8.5f, DIM, false);
			} finally { Ui.end(); }
		}
	}

	private static void handleMouse(Minecraft mc, Config.HudEl[] els, float[] ws, float[] hs, int sw, int sh) {
		long win = GLFW.glfwGetCurrentContext();
		if (win == 0) return;
		boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
		float mx = (float) (mc.mouseHandler.xpos() * sw / mc.getWindow().getScreenWidth());
		float my = (float) (mc.mouseHandler.ypos() * sh / mc.getWindow().getScreenHeight());
		if (down && !wasDown) {
			for (int i = 1; i >= 0; i--) {
				Config.HudEl e = els[i];
				if (!e.on) continue;
				float x = e.x * sw, y = e.y * sh, w = ws[i] * e.scale, h = hs[i] * e.scale;
				if (Math.hypot(mx - (x + w), my - (y + h)) <= 7) { dragEl = i; resizing = true; startScale = e.scale; startW = w; grabX = mx; break; }
				if (mx >= x - 3 && mx <= x + w + 3 && my >= y - 3 && my <= y + h + 3) { dragEl = i; resizing = false; grabX = mx - x; grabY = my - y; break; }
			}
		} else if (down && dragEl >= 0) {
			Config.HudEl e = els[dragEl];
			if (resizing) {
				float nw = Math.max(24, startW + (mx - grabX));
				e.scale = Math.max(.5f, Math.min(2.5f, startScale * nw / startW));
			} else {
				float w = ws[dragEl] * e.scale, h = hs[dragEl] * e.scale;
				float nx = Math.max(0, Math.min(sw - w, mx - grabX)), ny = Math.max(0, Math.min(sh - h, my - grabY));
				e.x = nx / sw; e.y = ny / sh;
			}
		} else if (!down && wasDown && dragEl >= 0) {
			Config.save(); dragEl = -1;
		}
		wasDown = down;
	}
}
