package dev.nizay.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Рисование без «пикселей». Всё считается в физических пикселях экрана (pose масштабируется на 1/guiScale),
 * углы скругляются с антиалиасингом (частичная прозрачность граничных пикселей), текст — TrueType (Inter).
 * Координаты в «дизайн-единицах»: 1 единица = K физических пикселей (K = guiScale * fit).
 */
public final class Ui {
	private Ui() {}

	static GuiGraphics g;
	static float S = 1, K = 1, ox, oy;
	static Font font;

	private static final Identifier F_REG = Identifier.fromNamespaceAndPath("nizay", "inter");
	private static final Identifier F_BOLD = Identifier.fromNamespaceAndPath("nizay", "inter_bold");
	private static final Style REG = Style.EMPTY.withFont(new FontDescription.Resource(F_REG));
	private static final Style BOLD = Style.EMPTY.withFont(new FontDescription.Resource(F_BOLD));

	/** originGui — левый верхний угол дизайн-области в GUI-единицах, fit — коэффициент вписывания (≤1). */
	public static void begin(GuiGraphics gg, float originGuiX, float originGuiY, float fit) {
		Minecraft mc = Minecraft.getInstance();
		g = gg; font = mc.font;
		S = (float) mc.getWindow().getGuiScale();
		K = S * fit; ox = originGuiX * S; oy = originGuiY * S;
		g.pose().pushMatrix();
		g.pose().scale(1f / S, 1f / S);
	}

	public static void end() { g.pose().popMatrix(); }

	static int X(float x) { return Math.round(ox + x * K); }
	static int Y(float y) { return Math.round(oy + y * K); }
	static int L(float v) { return Math.round(v * K); }
	/** Перевод GUI-координат мыши в дизайн-единицы. */
	static float mx(double guiX, float originGuiX, float fit) { return (float) ((guiX - originGuiX) / fit); }

	// ------------------------------------------------------------------ скруглённые прямоугольники
	private static final class Corner { final int r; final int[] full; final float[][] cov;
		Corner(int r) {
			this.r = r; full = new int[r]; cov = new float[r][r];
			for (int j = 0; j < r; j++) {
				int ff = r;
				for (int i = 0; i < r; i++) {
					double dx = r - (i + .5), dy = r - (j + .5);
					double c = Math.max(0, Math.min(1, r - Math.sqrt(dx * dx + dy * dy) + .5));
					cov[j][i] = (float) c;
					if (c >= .985 && ff == r) ff = i;
				}
				full[j] = ff;
			}
		}
	}
	private static final Map<Integer, Corner> CORNERS = new HashMap<>();
	private static Corner corner(int r) { return CORNERS.computeIfAbsent(r, Corner::new); }

	private static int a(int color, float k) {
		int al = Math.round(((color >>> 24) & 255) * k);
		return (al << 24) | (color & 0xFFFFFF);
	}

	/** Прямоугольник в физических пикселях [x0,x1)×[y0,y1) со скруглением R. */
	static void rectPx(int x0, int y0, int x1, int y1, int R, int color) {
		if (x1 <= x0 || y1 <= y0 || (color >>> 24) == 0) return;
		R = Math.min(R, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
		if (R <= 0) { g.fill(x0, y0, x1, y1, color); return; }
		g.fill(x0, y0 + R, x1, y1 - R, color);
		Corner c = corner(R);
		for (int j = 0; j < R; j++) {
			int ff = c.full[j];
			if (x1 - ff > x0 + ff) {
				g.fill(x0 + ff, y0 + j, x1 - ff, y0 + j + 1, color);
				g.fill(x0 + ff, y1 - 1 - j, x1 - ff, y1 - j, color);
			}
			for (int i = 0; i < ff; i++) {
				float cv = c.cov[j][i];
				if (cv < .04f) continue;
				int col = a(color, cv);
				g.fill(x0 + i, y0 + j, x0 + i + 1, y0 + j + 1, col);
				g.fill(x1 - 1 - i, y0 + j, x1 - i, y0 + j + 1, col);
				g.fill(x0 + i, y1 - 1 - j, x0 + i + 1, y1 - j, col);
				g.fill(x1 - 1 - i, y1 - 1 - j, x1 - i, y1 - j, col);
			}
		}
	}

	/** Закрашивает «внешнюю» часть углов цветом фона — чтобы скруглить картинку, нарисованную прямоугольником. */
	static void maskCornersPx(int x0, int y0, int x1, int y1, int R, int bg) {
		R = Math.min(R, Math.min((x1 - x0) / 2, (y1 - y0) / 2));
		if (R <= 0) return;
		Corner c = corner(R);
		for (int j = 0; j < R; j++) for (int i = 0; i < R; i++) {
			float cv = 1f - c.cov[j][i];
			if (cv < .04f) continue;
			int col = a(bg, cv);
			g.fill(x0 + i, y0 + j, x0 + i + 1, y0 + j + 1, col);
			g.fill(x1 - 1 - i, y0 + j, x1 - i, y0 + j + 1, col);
			g.fill(x0 + i, y1 - 1 - j, x0 + i + 1, y1 - j, col);
			g.fill(x1 - 1 - i, y1 - 1 - j, x1 - i, y1 - j, col);
		}
	}

	public static void rect(float x, float y, float w, float h, float r, int color) {
		rectPx(X(x), Y(y), X(x + w), Y(y + h), L(r), color);
	}

	/** Прямоугольник с тонкой обводкой (≈1 физ. пиксель, не меньше). */
	public static void rectB(float x, float y, float w, float h, float r, int fill, int border) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h), R = L(r), b = Math.max(1, Math.round(K * .7f));
		rectPx(x0, y0, x1, y1, R, border);
		rectPx(x0 + b, y0 + b, x1 - b, y1 - b, Math.max(0, R - b), fill);
	}

	/** Мягкая тень: несколько расширяющихся слоёв с убывающей прозрачностью. */
	public static void shadow(float x, float y, float w, float h, float r, int rgb, int maxAlpha, float spread) {
		int n = 7;
		for (int i = n; i >= 1; i--) {
			float t = i / (float) n;
			int al = Math.round(maxAlpha * (1 - t) * (1 - t) * .55f);
			if (al <= 0) continue;
			int e = L(spread * t);
			rectPx(X(x) - e, Y(y) - e + L(spread * .25f), X(x + w) + e, Y(y + h) + e + L(spread * .25f), L(r) + e, (al << 24) | rgb);
		}
	}

	/** Вертикальный градиент внутри скруглённого прямоугольника (по строкам физических пикселей). */
	public static void gradient(float x, float y, float w, float h, float r, int top, int bot) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h), R = Math.min(L(r), Math.min((x1 - x0) / 2, (y1 - y0) / 2));
		int hh = y1 - y0; if (hh <= 0) return;
		Corner c = R > 0 ? corner(R) : null;
		for (int row = 0; row < hh; row++) {
			float t = hh <= 1 ? 0 : row / (float) (hh - 1);
			int col = lerp(top, bot, t);
			int j = row < R ? row : (row >= hh - R ? hh - 1 - row : -1);
			if (j < 0 || c == null) { g.fill(x0, y0 + row, x1, y0 + row + 1, col); continue; }
			int ff = c.full[j];
			g.fill(x0 + ff, y0 + row, x1 - ff, y0 + row + 1, col);
			for (int i = 0; i < ff; i++) {
				float cv = c.cov[j][i]; if (cv < .04f) continue;
				int cc = a(col, cv);
				g.fill(x0 + i, y0 + row, x0 + i + 1, y0 + row + 1, cc);
				g.fill(x1 - 1 - i, y0 + row, x1 - i, y0 + row + 1, cc);
			}
		}
	}

	public static int lerp(int c0, int c1, float t) {
		int a0 = c0 >>> 24, r0 = c0 >> 16 & 255, g0 = c0 >> 8 & 255, b0 = c0 & 255;
		int a1 = c1 >>> 24, r1 = c1 >> 16 & 255, g1 = c1 >> 8 & 255, b1 = c1 & 255;
		return (Math.round(a0 + (a1 - a0) * t) << 24) | (Math.round(r0 + (r1 - r0) * t) << 16) | (Math.round(g0 + (g1 - g0) * t) << 8) | Math.round(b0 + (b1 - b0) * t);
	}

	/** Картинка (полная текстура texW×texH) в прямоугольник со скруглёнными углами; bg — цвет фона под ней. */
	public static void image(Identifier tex, int texW, int texH, float x, float y, float w, float h, float r, int bg) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + w), y1 = Y(y + h);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y0, 0f, 0f, x1 - x0, y1 - y0, texW, texH, texW, texH);
		maskCornersPx(x0, y0, x1, y1, L(r), bg);
	}

	/** Картинка без маски, вписанная в квадрат. */
	public static void sprite(Identifier tex, int texSize, float x, float y, float size) {
		int x0 = X(x), y0 = Y(y), x1 = X(x + size), y1 = Y(y + size);
		g.blit(RenderPipelines.GUI_TEXTURED, tex, x0, y0, 0f, 0f, x1 - x0, y1 - y0, texSize, texSize, texSize, texSize);
	}

	public static void circle(float cx, float cy, float r, int color) { rect(cx - r, cy - r, r * 2, r * 2, r, color); }

	// ------------------------------------------------------------------ текст
	private static Component comp(String s, boolean bold) { return Component.literal(s).withStyle(bold ? BOLD : REG); }

	public static float width(String s, float size, boolean bold) { return font.width(comp(s, bold)) * size / 9f; }

	public static void text(String s, float x, float y, float size, int color, boolean bold) {
		g.pose().pushMatrix();
		g.pose().translate(ox + x * K, oy + y * K);
		float sc = K * size / 9f;
		g.pose().scale(sc, sc);
		g.drawString(font, comp(s, bold), 0, 0, color, false);
		g.pose().popMatrix();
	}

	public static void textR(String s, float xRight, float y, float size, int color, boolean bold) {
		text(s, xRight - width(s, size, bold), y, size, color, bold);
	}

	public static void textC(String s, float xCenter, float y, float size, int color, boolean bold) {
		text(s, xCenter - width(s, size, bold) / 2f, y, size, color, bold);
	}

	public static String fit(String s, float maxW, float size, boolean bold) {
		if (width(s, size, bold) <= maxW) return s;
		while (s.length() > 1 && width(s + "…", size, bold) > maxW) s = s.substring(0, s.length() - 1);
		return s + "…";
	}
}
