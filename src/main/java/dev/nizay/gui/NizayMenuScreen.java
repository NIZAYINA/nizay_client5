package dev.nizay.gui;

import dev.nizay.Config;
import dev.nizay.NizayClient;
import dev.nizay.accounts.Accounts;
import dev.nizay.auction.AuctionHelper;
import dev.nizay.fx.Fx;
import dev.nizay.opt.Optimizer;
import dev.nizay.wings.WingsFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Меню по Right Shift. Левая колонка вкладок + гладкие скруглённые карточки (см. Ui). */
public class NizayMenuScreen extends Screen {
	static final int PANEL = 0xF4101013, SIDE = 0xFF0C0C0F, CARD = 0xFF17171B, CARD_HI = 0xFF1E1E23, LINE = 0xFF2A2A31;
	static final int TXT = 0xFFF2F2F6, DIM = 0xFF8E8E99, DIMMER = 0xFF5A5A64, WHITE = 0xFFEDEDF2, DARK = 0xFF0B0B0D, ON = 0xFFEDEDF2, OFF = 0xFF2A2A30;
	static final int W = 560, H = 352, SB = 152, CX = 176, CW = 360;

	static final String[] TABS = {"Visual", "Effects", "Wings", "HUD", "Optimization", "Auction", "Accounts", "Settings"};
	static final String[] SUB = {"Sky, effects and ambience", "30 effects, all drawn from scratch", "Animated wings attached to your character",
		"Watermark and coordinates. Move them in chat (T)", "Boost FPS without touching the server", "Auto relist on /ah", "Synced with the launcher", "Client options"};

	record Sky(String id, String name, String kind) {}
	static final Sky[] SKIES = {
		new Sky("deep_space", "Deep Space", "Animated"), new Sky("nebula", "Nebula", "Animated"), new Sky("aurora", "Aurora", "Animated"),
		new Sky("eclipse", "Eclipse", "Animated"), new Sky("crystal_dusk", "Crystal Dusk", "Animated"), new Sky("sunset", "Sunset", "Tinted"),
		new Sky("dawn_haze", "Dawn Haze", "Tinted"), new Sky("midnight", "Midnight", "Plain"), new Sky("mono", "Mono", "Plain")
	};

	private static final class Hit { float x, y, w, h; Runnable click; Consumer<Float> drag; }
	private final List<Hit> hits = new ArrayList<>();
	private Hit dragging;
	private final Map<String, Float> anim = new HashMap<>();
	private long lastNs = System.nanoTime();
	private float dt;
	private float fit, ogx, ogy, mxD, myD;
	private int tab = 0, fxPage = 0, fxSel = 0;
	private int accRefresh;

	public NizayMenuScreen() { super(Component.literal("Nizay client")); }

	@Override public boolean isPauseScreen() { return false; }

	// ------------------------------------------------------------------ кадр
	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
		super.render(g, mouseX, mouseY, partial);
		long now = System.nanoTime(); dt = Math.min(.1f, (now - lastNs) / 1e9f); lastNs = now;
		fit = Math.min(1f, Math.min((width - 16f) / W, (height - 16f) / H));
		ogx = (width - W * fit) / 2f; ogy = (height - H * fit) / 2f;
		mxD = Ui.mx(mouseX, ogx, fit); myD = Ui.mx(mouseY, ogy, fit);
		hits.clear();
		Ui.begin(g, ogx, ogy, fit);
		try { draw(); } finally { Ui.end(); }
	}

	private void draw() {
		Ui.shadow(0, 0, W, H, 16, 0x000000, 150, 26);
		Ui.rectB(0, 0, W, H, 16, PANEL, LINE);
		// боковая колонка
		Ui.rect(6, 6, SB - 6, H - 12, 12, SIDE);
		Ui.rectB(22, 22, 32, 32, 9, 0xFF121216, LINE);
		Ui.textC("N", 38, 29, 17, WHITE, true);
		Ui.text("Nizay", 64, 25, 13, WHITE, true);
		Ui.text("client", 64 + Ui.width("Nizay ", 13, true), 25, 13, DIM, false);
		Ui.text("MODULES", 22, 70, 8, DIMMER, true);
		for (int i = 0; i < TABS.length; i++) {
			float ty = 88 + i * 31;
			boolean sel = i == tab, hov = inside(12, ty, SB - 18, 28);
			float a = anim("tab" + i, sel ? 1 : hov ? .45f : 0);
			if (a > .01f) Ui.rect(12, ty, SB - 18, 28, 9, Ui.lerp(0x00000000, 0xFF1C1C21, a));
			if (sel) Ui.rect(12, ty + 7, 2.2f, 14, 1.1f, WHITE);
			icon(i, 28, ty + 14, sel ? WHITE : (hov ? 0xFFC8C8D0 : DIM));
			Ui.text(TABS[i], 46, ty + 8, 10.5f, sel ? WHITE : (hov ? 0xFFD0D0D8 : DIM), sel);
			final int ti = i; hit(12, ty, SB - 18, 28, () -> tab = ti, null);
		}
		Ui.rectB(18, H - 44, SB - 30, 28, 9, 0xFF101014, LINE);
		Ui.rectB(24, H - 39, 54, 18, 6, 0xFF1B1B20, LINE);
		Ui.textC("R Shift", 51, H - 35, 8.5f, TXT, true);
		Ui.text("toggle", 88, H - 35, 8.5f, DIM, false);

		// шапка
		Ui.text(TABS[tab], CX, 22, 20, WHITE, true);
		Ui.text(SUB[tab], CX, 48, 9.5f, DIM, false);
		Ui.textR("v" + NizayClient.VERSION, W - 24, 28, 9, DIMMER, false);

		float y = 76;
		switch (tab) {
			case 0 -> visual(CX, y, CW);
			case 1 -> effects(CX, y, CW);
			case 2 -> wings(CX, y, CW);
			case 3 -> hud(CX, y, CW);
			case 4 -> optimization(CX, y, CW);
			case 5 -> auction(CX, y, CW);
			case 6 -> accounts(CX, y, CW);
			default -> settings(CX, y, CW);
		}
	}

	/** Простые иконки вкладок, нарисованные скруглёнными прямоугольниками. */
	private void icon(int i, float cx, float cy, int c) {
		switch (i) {
			case 0 -> { Ui.rect(cx - 6, cy - 3.5f, 12, 7, 3.5f, c); Ui.circle(cx, cy, 1.8f, 0xFF0C0C0F); }
			case 1 -> { Ui.rect(cx - 1, cy - 6, 2, 12, 1, c); Ui.rect(cx - 6, cy - 1, 12, 2, 1, c); Ui.circle(cx, cy, 1.5f, c); }
			case 2 -> { Ui.rect(cx - 5, cy - 5, 2, 11, 1, c); Ui.rect(cx - 1, cy - 6, 2, 11, 1, c); Ui.rect(cx + 3, cy - 5, 2, 11, 1, c); }
			case 3 -> { Ui.rectB(cx - 6, cy - 4.5f, 12, 9, 2.5f, 0x00000000, c); Ui.rect(cx - 3.5f, cy - 1.5f, 7, 1.5f, .7f, c); }
			case 4 -> { Ui.rect(cx - 6, cy + 1, 3, 5, 1, c); Ui.rect(cx - 1.5f, cy - 3, 3, 9, 1, c); Ui.rect(cx + 3, cy - 6, 3, 12, 1, c); }
			case 5 -> { Ui.rect(cx - 6, cy - 1, 12, 7, 2, c); Ui.rect(cx - 3, cy - 6, 6, 4, 2, c); }
			case 6 -> { Ui.circle(cx, cy - 2.5f, 3, c); Ui.rect(cx - 6, cy + 1.5f, 12, 5, 2.5f, c); }
			default -> { Ui.rectB(cx - 6, cy - 6, 12, 12, 6, 0x00000000, c); Ui.circle(cx, cy, 2, c); }
		}
	}

	// ------------------------------------------------------------------ вкладки
	private void visual(float x, float y, float w) {
		Config c = Config.I;
		Ui.text("Sky", x, y, 12, TXT, true);
		Ui.text("Replaces the vanilla sky completely", x + 30, y + 2, 9, DIM, false);
		toggle("sky", x + w - 32, y - 2, c.skyOn, () -> { c.skyOn = !c.skyOn; });
		hitRow(x, y - 4, w - 40, 20, () -> c.skyOn = !c.skyOn);
		y += 26;
		float cw = (w - 16) / 3f, ih = 44;
		for (int i = 0; i < SKIES.length; i++) {
			Sky s = SKIES[i];
			float cx = x + (i % 3) * (cw + 8), cy = y + (i / 3) * (ih + 24);
			boolean sel = s.id().equals(c.sky), hov = inside(cx, cy, cw, ih + 20);
			float a = anim("sky" + i, sel ? 1 : hov ? .5f : 0);
			if (a > .02f) Ui.rect(cx - 2, cy - 2, cw + 4, ih + 4, 9, Ui.lerp(0x00FFFFFF, 0xFFEDEDF2, a));
			Ui.image(Identifier.fromNamespaceAndPath("nizay", "textures/gui/sky_" + s.id() + ".png"), 256, 128, cx, cy, cw, ih, 7, a > .02f ? Ui.lerp(0xFF101013, 0xFFEDEDF2, a) : PANEL);
			if (sel) { Ui.circle(cx + cw - 10, cy + 10, 6, 0xFFF2F2F6); Ui.rect(cx + cw - 12.5f, cy + 9.5f, 2.2f, 2.2f, .5f, DARK); Ui.rect(cx + cw - 10.3f, cy + 6.8f, 1.8f, 5.6f, .5f, DARK); }
			Ui.text(s.name(), cx + 2, cy + ih + 5, 9.5f, sel ? WHITE : TXT, sel);
			Ui.textR(s.kind(), cx + cw - 1, cy + ih + 6, 8, DIM, false);
			hit(cx, cy, cw, ih + 20, () -> c.sky = s.id(), null);
		}
		y += 3 * (ih + 24) + 2;
		float hw = (w - 24) / 2f;
		Ui.text("Animation speed", x, y, 9.5f, DIM, false); Ui.textR(String.format("%.1fx", c.animSpeed), x + hw, y, 9.5f, TXT, true);
		slider("spd", x, y + 17, hw, (c.animSpeed - .2f) / 2.8f, v -> c.animSpeed = .2f + v * 2.8f);
		Ui.text("Brightness", x + hw + 24, y, 9.5f, DIM, false); Ui.textR(Math.round(c.brightness * 100) + "%", x + w, y, 9.5f, TXT, true);
		slider("brt", x + hw + 24, y + 17, hw, c.brightness, v -> c.brightness = Math.max(.1f, v));
	}

	private void effects(float x, float y, float w) {
		Config c = Config.I;
		int per = 15, pages = Fx.IDS.length / per;
		float tw = (w - 32) / 5f, th = 56;
		for (int k = 0; k < per; k++) {
			int idx = fxPage * per + k; String id = Fx.IDS[idx];
			Config.Fx f = c.fx(id);
			float tx = x + (k % 5) * (tw + 8), ty = y + (k / 5) * (th + 8);
			boolean sel = idx == fxSel, hov = inside(tx, ty, tw, th);
			float a = anim("fx" + idx, sel ? 1 : hov ? .4f : 0);
			Ui.rectB(tx, ty, tw, th, 9, hov ? CARD_HI : CARD, Ui.lerp(f.on ? 0xFF3C3C45 : LINE, 0xFFEDEDF2, a));
			Ui.sprite(Identifier.fromNamespaceAndPath("nizay", "textures/particle/" + id + ".png"), 64, tx + tw / 2 - 13, ty + 8, 26);
			Ui.textC(Ui.fit(Fx.NAMES[idx], tw - 6, 8, false), tx + tw / 2, ty + th - 14, 8, f.on ? TXT : DIM, false);
			final int fi = idx; hit(tx, ty, tw, th, () -> fxSel = fi, null);
			mini(tx + tw - 26, ty + 5, f.on, "m" + idx);
			hit(tx + tw - 34, ty, 34, 22, () -> f.on = !f.on, null);   // большая область клика на тумблер
		}
		float by = y + 3 * (th + 8) + 2;
		Ui.rectB(x, by, w, 62, 11, CARD, LINE);
		Config.Fx f = c.fx(Fx.IDS[fxSel]);
		Ui.text(Fx.NAMES[fxSel], x + 14, by + 12, 11.5f, TXT, true);
		Ui.text("Selected effect", x + 14, by + 29, 8.5f, DIM, false);
		toggle("fxsel", x + 14, by + 42, f.on, () -> f.on = !f.on);
		float sx = x + 126, sw = 92;
		Ui.text("Size", sx, by + 9, 9, DIM, false); Ui.textR(f.size + "%", sx + sw, by + 9, 9, TXT, true);
		slider("fsz", sx, by + 27, sw, f.size / 100f, v -> f.size = Math.round(v * 100));
		float dx = sx + sw + 18;
		Ui.text("Density", dx, by + 9, 9, DIM, false); Ui.textR(f.density + "%", dx + sw, by + 9, 9, TXT, true);
		slider("fdn", dx, by + 27, sw, f.density / 100f, v -> f.density = Math.round(v * 100));
		for (int i = 0; i < Fx.PALETTE_RGB.length; i++) {
			final int ci = i; swatch(sx + 7 + i * 20, by + 50, 7, 0xFF000000 | Fx.PALETTE_RGB[i], f.color == i, () -> f.color = ci);
		}
		Ui.textC((fxPage + 1) + "/" + pages, x + w - 50, by + 44, 9, DIM, false);
		arrow(x + w - 84, by + 40, "<", () -> fxPage = (fxPage + pages - 1) % pages);
		arrow(x + w - 36, by + 40, ">", () -> fxPage = (fxPage + 1) % pages);
	}

	private void wings(float x, float y, float w) {
		Config.Wings c = Config.I.wings;
		Ui.rectB(x, y, w, 246, 11, CARD, LINE);
		Ui.text("Wings", x + 16, y + 14, 12.5f, TXT, true); Ui.text("Aether Wings", x + 16, y + 31, 9, DIM, false);
		toggle("wg", x + w - 48, y + 16, c.on, () -> c.on = !c.on);
		Ui.text("Color", x + 16, y + 56, 9, DIM, false);
		for (int i = 0; i < WingsFx.COLORS_RGB.length; i++) { final int ci = i; swatch(x + 24 + i * 24, y + 80, 8.5f, 0xFF000000 | WingsFx.COLORS_RGB[i], c.color == i, () -> c.color = ci); }
		float sw = w - 32, sx = x + 16, yy = y + 100;
		Ui.text("Size", sx, yy, 9.5f, DIM, false); Ui.textR(c.size + "%", sx + sw, yy, 9.5f, TXT, true);
		slider("wsz", sx, yy + 17, sw, c.size / 100f, v -> c.size = Math.round(v * 100));
		yy += 34;
		Ui.text("Flap speed", sx, yy, 9.5f, DIM, false); Ui.textR(String.format("%.1fx", c.flap), sx + sw, yy, 9.5f, TXT, true);
		slider("wfl", sx, yy + 17, sw, (c.flap - .3f) / 2.2f, v -> c.flap = .3f + v * 2.2f);
		yy += 34;
		Ui.text("Glow", sx, yy, 9.5f, DIM, false); Ui.textR(c.glow + "%", sx + sw, yy, 9.5f, TXT, true);
		slider("wgl", sx, yy + 17, sw, c.glow / 100f, v -> c.glow = Math.round(v * 100));
		row(x + 16, y + 190, w - 32, "Fold when idle", "Wings rest while you stand still", "wfi", c.foldIdle, () -> c.foldIdle = !c.foldIdle);
		row(x + 16, y + 215, w - 32, "Show in first person", "Off by default", "wfp", c.firstPerson, () -> c.firstPerson = !c.firstPerson);
	}

	private void hud(float x, float y, float w) {
		Config c = Config.I;
		Ui.rectB(x, y, w, 196, 11, CARD, LINE);
		row(x + 16, y + 14, w - 32, "Watermark", "Logo, ping and FPS", "hw", c.hudMark.on, () -> c.hudMark.on = !c.hudMark.on);
		Ui.text("Scale", x + 16, y + 48, 9.5f, DIM, false); Ui.textR(Math.round(c.hudMark.scale * 100) + "%", x + w - 16, y + 48, 9.5f, TXT, true);
		slider("hws", x + 16, y + 65, w - 32, (c.hudMark.scale - .6f) / 1.4f, v -> c.hudMark.scale = .6f + v * 1.4f);
		row(x + 16, y + 92, w - 32, "Coordinates", "XYZ and facing direction", "hc", c.hudCoords.on, () -> c.hudCoords.on = !c.hudCoords.on);
		Ui.text("Scale", x + 16, y + 126, 9.5f, DIM, false); Ui.textR(Math.round(c.hudCoords.scale * 100) + "%", x + w - 16, y + 126, 9.5f, TXT, true);
		slider("hcs", x + 16, y + 143, w - 32, (c.hudCoords.scale - .6f) / 1.4f, v -> c.hudCoords.scale = .6f + v * 1.4f);
		button(x + 16, y + 164, 130, 22, "Reset positions", false, () -> {
			c.hudMark.x = .012f; c.hudMark.y = .018f; c.hudMark.scale = 1f;
			c.hudCoords.x = .012f; c.hudCoords.y = .085f; c.hudCoords.scale = 1f;
		});
		Ui.text("Open chat (T) and drag any panel. Drag the corner dot to resize.", x, y + 206, 9, DIMMER, false);
	}

	private void optimization(float x, float y, float w) {
		Config.Opt o = Config.I.opt;
		Ui.rectB(x, y, w, 258, 11, CARD, LINE);
		row(x + 16, y + 12, w - 32, "Optimization", "Master switch. Old settings are restored when off", "o_on", o.on, () -> { o.on = !o.on; Optimizer.apply(); });
		Ui.rect(x + 16, y + 41, w - 32, 1, 0, LINE);
		row(x + 16, y + 48, w - 32, "Adaptive effects", "Lowers Nizay particle density when FPS drops", "o_lim", o.limiter, () -> o.limiter = !o.limiter);
		row(x + 16, y + 72, w - 32, "Minimal vanilla particles", "Fewer particles from the game", "o_mp", o.minParticles, () -> { o.minParticles = !o.minParticles; Optimizer.apply(); });
		row(x + 16, y + 96, w - 32, "Clouds off", "Removes cloud rendering", "o_cl", o.noClouds, () -> { o.noClouds = !o.noClouds; Optimizer.apply(); });
		row(x + 16, y + 120, w - 32, "Entity shadows off", "Cheaper crowds on spawn", "o_sh", o.noShadows, () -> { o.noShadows = !o.noShadows; Optimizer.apply(); });
		row(x + 16, y + 144, w - 32, "Flat lighting", "Smooth lighting off", "o_ao", o.noAo, () -> { o.noAo = !o.noAo; Optimizer.apply(); });
		float sw = (w - 48) / 2f, sx = x + 16, yy = y + 176;
		Ui.text("Entity distance", sx, yy, 9, DIM, false); Ui.textR(o.entityDist + "%", sx + sw, yy, 9, TXT, true);
		slider("o_ed", sx, yy + 16, sw, (o.entityDist - 50) / 50f, v -> { o.entityDist = 50 + Math.round(v * 50); Optimizer.apply(); });
		Ui.text("Effects cap", sx + sw + 16, yy, 9, DIM, false); Ui.textR(o.fxCap + "", sx + 2 * sw + 16, yy, 9, TXT, true);
		slider("o_fc", sx + sw + 16, yy + 16, sw, (o.fxCap - 100) / 1400f, v -> o.fxCap = 100 + Math.round(v * 1400));
		yy += 38;
		Ui.text("Render distance cap", sx, yy, 9, DIM, false); Ui.textR(o.renderCap == 0 ? "off" : o.renderCap + "", sx + sw, yy, 9, TXT, true);
		slider("o_rd", sx, yy + 16, sw, o.renderCap == 0 ? 0 : (o.renderCap - 4) / 28f, v -> { o.renderCap = v < .02f ? 0 : 4 + Math.round(v * 28); Optimizer.apply(); });
		Ui.text("Simulation cap", sx + sw + 16, yy, 9, DIM, false); Ui.textR(o.simCap == 0 ? "off" : o.simCap + "", sx + 2 * sw + 16, yy, 9, TXT, true);
		slider("o_sd", sx + sw + 16, yy + 16, sw, o.simCap == 0 ? 0 : (o.simCap - 5) / 27f, v -> { o.simCap = v < .02f ? 0 : 5 + Math.round(v * 27); Optimizer.apply(); });
	}

	private void auction(float x, float y, float w) {
		Config.Auction a = Config.I.ah;
		Ui.rectB(x, y, w, 200, 11, CARD, LINE);
		Ui.text("AuctionHelper", x + 16, y + 14, 12.5f, TXT, true); Ui.text("Auto relist unsold items on /ah", x + 16, y + 31, 9, DIM, false);
		toggle("ah", x + w - 48, y + 16, a.enabled, () -> { a.enabled = !a.enabled; AuctionHelper.reset(); });
		float sw = w - 32, sx = x + 16;
		Ui.text("Cycle every", sx, y + 56, 9.5f, DIM, false); Ui.textR(a.intervalSec + " s", sx + sw, y + 56, 9.5f, TXT, true);
		slider("ahi", sx, y + 73, sw, (a.intervalSec - 10) / 290f, v -> a.intervalSec = Math.round(10 + v * 290));
		Ui.text("Click delay", sx, y + 92, 9.5f, DIM, false); Ui.textR(a.actionDelayMs + " ms", sx + sw, y + 92, 9.5f, TXT, true);
		slider("ahd", sx, y + 109, sw, (a.actionDelayMs - 100) / 900f, v -> a.actionDelayMs = Math.round(100 + v * 900));
		Ui.text("Default price", sx, y + 132, 9.5f, DIM, false); Ui.textR(String.format("%,d", a.defaultPrice), sx + sw - 92, y + 132, 9.5f, TXT, true);
		button(sx + sw - 86, y + 128, 26, 20, "-", false, () -> a.defaultPrice = Math.max(1, a.defaultPrice / 2));
		button(sx + sw - 56, y + 128, 26, 20, "+", false, () -> a.defaultPrice = a.defaultPrice * 2);
		button(sx + sw - 26, y + 128, 26, 20, "0", false, () -> a.defaultPrice = 1000);
		Ui.text("Status", sx, y + 164, 9.5f, DIM, false); Ui.text(AuctionHelper.status(), sx + 44, y + 164, 9.5f, TXT, false);
		Ui.text("Titles, slots and prices: config/nizay.json", x, y + 212, 9, DIMMER, false);
	}

	private void accounts(float x, float y, float w) {
		if (accRefresh-- <= 0) accRefresh = 40;
		List<Accounts.Acc> list = Accounts.list();
		String cur = Accounts.currentName();
		Ui.rectB(x, y, w, 62, 11, CARD, LINE);
		Ui.text("Current account", x + 16, y + 12, 9, DIM, false);
		Ui.text(cur, x + 16, y + 27, 14, TXT, true);
		Ui.text("Switching applies on the next server join", x + 16, y + 46, 8.5f, DIMMER, false);
		float yy = y + 74;
		if (list.isEmpty()) {
			Ui.text("No launcher accounts found.", x, yy + 6, 10, DIM, false);
			Ui.text("Start the game from the Nizay launcher and add accounts there.", x, yy + 22, 9, DIMMER, false);
			return;
		}
		String active = Accounts.activeId();
		for (int i = 0; i < Math.min(list.size(), 6); i++) {
			Accounts.Acc a = list.get(i);
			boolean isCur = a.name().equals(cur);
			float ry = yy + i * 32;
			Ui.rectB(x, ry, w, 28, 9, CARD, isCur ? 0xFF4A4A55 : LINE);
			Ui.circle(x + 18, ry + 14, 8, 0xFF24242B);
			Ui.textC(a.name().isEmpty() ? "?" : a.name().substring(0, 1).toUpperCase(), x + 18, ry + 8, 10, TXT, true);
			Ui.text(a.name(), x + 34, ry + 6, 10.5f, TXT, true);
			Ui.text(a.type(), x + 34 + Ui.width(a.name() + "  ", 10.5f, true), ry + 8, 8.5f, DIM, false);
			if (isCur) Ui.textR("In use", x + w - 14, ry + 9, 9, DIM, false);
			else button(x + w - 70, ry + 4, 58, 20, "Use", true, () -> Accounts.select(a));
		}
	}

	private void settings(float x, float y, float w) {
		Ui.rectB(x, y, w, 110, 11, CARD, LINE);
		Ui.text("Nizay client v" + NizayClient.VERSION, x + 16, y + 14, 12, TXT, true);
		Ui.text("Config: .minecraft/config/nizay.json", x + 16, y + 32, 9, DIM, false);
		button(x + 16, y + 56, 96, 24, "Save", true, Config::save);
		button(x + 122, y + 56, 96, 24, "Reset all", false, () -> { Config.I = new Config(); Config.save(); Optimizer.apply(); });
		Ui.text("Open this menu: Right Shift", x + 16, y + 90, 9, DIMMER, false);
	}

	// ------------------------------------------------------------------ виджеты
	private boolean inside(float x, float y, float w, float h) { return mxD >= x && mxD < x + w && myD >= y && myD < y + h; }

	private void hit(float x, float y, float w, float h, Runnable click, Consumer<Float> drag) {
		Hit hh = new Hit(); hh.x = x; hh.y = y; hh.w = w; hh.h = h; hh.click = click; hh.drag = drag; hits.add(hh);
	}
	private void hitRow(float x, float y, float w, float h, Runnable r) { hit(x, y, w, h, r, null); }

	private float anim(String id, float target) {
		float v = anim.getOrDefault(id, target);
		v += (target - v) * Math.min(1f, dt * 14f);
		anim.put(id, v); return v;
	}

	private void toggle(String id, float x, float y, boolean on, Runnable click) {
		float a = anim(id, on ? 1 : 0);
		Ui.rect(x, y, 32, 18, 9, Ui.lerp(OFF, ON, a));
		Ui.circle(x + 9 + 14 * a, y + 9, 6.2f, Ui.lerp(0xFF8A8A94, DARK, a));
		hit(x - 4, y - 4, 40, 26, click, null);
	}

	private void mini(float x, float y, boolean on, String id) {
		float a = anim(id, on ? 1 : 0);
		Ui.rect(x, y, 22, 12, 6, Ui.lerp(OFF, ON, a));
		Ui.circle(x + 6 + 10 * a, y + 6, 4, Ui.lerp(0xFF8A8A94, DARK, a));
	}

	private void row(float x, float y, float w, String title, String desc, String id, boolean on, Runnable click) {
		boolean hov = inside(x - 6, y - 2, w + 12, 24);
		if (hov) Ui.rect(x - 6, y - 3, w + 12, 24, 8, 0x14FFFFFF);
		Ui.text(title, x, y, 10.5f, TXT, true);
		Ui.text(desc, x + Ui.width(title, 10.5f, true) + 10, y + 1.5f, 8.5f, DIMMER, false);
		toggle(id, x + w - 32, y - 2, on, click);
		hit(x - 6, y - 3, w - 30, 24, click, null);
	}

	private void slider(String id, float x, float y, float w, float v, Consumer<Float> set) {
		v = Math.max(0, Math.min(1, v));
		boolean active = dragging != null && dragging.drag != null && dragging.x == x - 8 && dragging.y == y - 9;
		float a = anim("s" + id, active || inside(x - 8, y - 9, w + 16, 20) ? 1 : 0);
		Ui.rect(x, y - 2, w, 4, 2, OFF);
		Ui.rect(x, y - 2, Math.max(4, w * v), 4, 2, WHITE);
		Ui.circle(x + w * v, y, 5.5f + a * 1.2f, 0x40000000);
		Ui.circle(x + w * v, y, 5f + a * 1.2f, WHITE);
		final float fx = x, fw = w;
		hit(x - 8, y - 9, w + 16, 20, null, mx -> set.accept(Math.max(0, Math.min(1, (mx - fx) / fw))));
	}

	private void swatch(float cx, float cy, float r, int color, boolean sel, Runnable click) {
		if (sel) Ui.circle(cx, cy, r + 3.2f, WHITE);
		if (sel) Ui.circle(cx, cy, r + 1.6f, PANEL);
		Ui.circle(cx, cy, r, color);
		hit(cx - r - 3, cy - r - 3, 2 * r + 6, 2 * r + 6, click, null);
	}

	private void button(float x, float y, float w, float h, String label, boolean primary, Runnable click) {
		boolean hov = inside(x, y, w, h);
		Ui.rectB(x, y, w, h, 7, primary ? (hov ? 0xFFFFFFFF : WHITE) : (hov ? CARD_HI : 0xFF1B1B20), primary ? WHITE : LINE);
		Ui.textC(label, x + w / 2, y + h / 2 - 5, 9.5f, primary ? DARK : TXT, true);
		hit(x, y, w, h, click, null);
	}

	private void arrow(float x, float y, String s, Runnable click) {
		boolean hov = inside(x, y, 24, 20);
		Ui.rectB(x, y, 24, 20, 7, hov ? CARD_HI : 0xFF1B1B20, LINE);
		Ui.textC(s, x + 12, y + 4.5f, 10, TXT, true);
		hit(x, y, 24, 20, click, null);
	}

	// ------------------------------------------------------------------ ввод
	@Override
	public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
		if (e.button() != 0) return false;
		float mx = Ui.mx(e.x(), ogx, fit), my = Ui.mx(e.y(), ogy, fit);
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (mx >= h.x && mx < h.x + h.w && my >= h.y && my < h.y + h.h) {
				if (h.click != null) h.click.run();
				if (h.drag != null) { dragging = h; h.drag.accept(mx); }
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
		if (dragging != null && dragging.drag != null) { dragging.drag.accept(Ui.mx(e.x(), ogx, fit)); return true; }
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent e) {
		if (dragging != null) { dragging = null; Config.save(); return true; }
		return false;
	}

	@Override public void removed() { Config.save(); }
}
