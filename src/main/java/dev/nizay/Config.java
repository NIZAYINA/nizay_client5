package dev.nizay;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Весь конфиг клиента. Лежит в .minecraft/config/nizay.json */
public class Config {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("nizay.json");
	public static Config I = new Config();

	/** Настройки одного эффекта. size/density 0..100, color: 0 = родной цвет, 1..4 = палитра. */
	public static class Fx { public boolean on = false; public int size = 55, density = 70, color = 0; }

	public static class Wings {
		public boolean on = false;
		public int color = 0, size = 70, glow = 40;
		public float flap = 1.1f;
		public boolean foldIdle = true, firstPerson = false;
	}

	/** Элемент HUD: позиция левого верхнего угла в долях экрана (0..1) и масштаб. Двигается мышью в чате (T). */
	public static class HudEl {
		public boolean on; public float x, y, scale = 1f;
		public HudEl() {}
		public HudEl(boolean on, float x, float y) { this.on = on; this.x = x; this.y = y; }
	}

	/** Оптимизация FPS. orig* хранят прежние значения настроек игры, чтобы вернуть их при выключении. */
	public static class Opt {
		public boolean on = false;
		public boolean limiter = true;       // автоматически снижает плотность эффектов Nizay при низком FPS
		public int fxCap = 700;              // максимум живых частиц Nizay
		public boolean minParticles = true;  // ванильные частицы -> минимум
		public boolean noClouds = true;
		public boolean noShadows = true;
		public boolean noAo = false;
		public int entityDist = 70;          // 50..100 % дальности отрисовки сущностей
		public int renderCap = 0;            // 0 = не трогать, иначе максимум чанков
		public int simCap = 0;               // 0 = не трогать, иначе симуляция чанков
		public int biomeBlend = 1;           // 0..7
		public boolean saved;
		public String origParticles, origClouds;
		public boolean origShadows, origAo;
		public double origEntDist = 1.0;
		public int origRender, origSim, origBiome;
	}

	/** Настройки AuctionHelper (FunTime). Заголовки меню и слоты подгоняются под сервер. */
	public static class Auction {
		public boolean enabled = false;
		public int intervalSec = 60;          // как часто делать круг
		public int actionDelayMs = 350;       // базовая задержка между кликами
		public int jitterMs = 150;            // случайная добавка к задержке
		public String ahTitleRegex = "(?i)аукцион|auction";
		public String myLotsTitleRegex = "(?i)(ваш|мои|твои).*(лот|товар|предмет)|(?i)your.*(items|lots)";
		public int myLotsSlot = 46;           // слот кнопки «ваши товары» в главном меню /ah (ПРОВЕРЬТЕ на сервере)
		public String expiredLoreRegex = "(?i)(истек|истёк|просрочен|не продан|expired|снят)";
		public String sellCommand = "ah sell";  // итоговая команда: /ah sell <цена>
		public long defaultPrice = 1000;
		/** id предмета -> цена за выставление. Если предмета нет в списке — он не трогается. */
		public Map<String, Long> prices = new LinkedHashMap<>();
		public Auction() { prices.put("minecraft:netherite_ingot", 100000L); }
	}

	public Map<String, Fx> fx = new LinkedHashMap<>();
	public boolean skyOn = true;
	public String sky = "deep_space";
	public float animSpeed = 1.2f, brightness = 0.8f;
	public Wings wings = new Wings();
	public HudEl hudMark = new HudEl(true, 0.012f, 0.018f);
	public HudEl hudCoords = new HudEl(true, 0.012f, 0.085f);
	public Opt opt = new Opt();
	public Auction ah = new Auction();

	public Fx fx(String id) { return fx.computeIfAbsent(id, k -> new Fx()); }

	public static void load() {
		try {
			if (Files.exists(FILE)) {
				try (Reader r = Files.newBufferedReader(FILE)) {
					Config c = GSON.fromJson(r, Config.class);
					if (c != null) { I = c; if (I.hudMark == null) I.hudMark = new HudEl(true, 0.012f, 0.018f); if (I.hudCoords == null) I.hudCoords = new HudEl(true, 0.012f, 0.085f); if (I.opt == null) I.opt = new Opt(); }
				}
			} else {
				// по умолчанию как на референсе: Stardust Trail, Aether Orbs и Halo включены
				I.fx("stardust").on = true;
				I.fx("aether").on = true;
				I.fx("halo").on = true;
				save();
			}
		} catch (Exception e) {
			System.err.println("[Nizay] config load failed: " + e);
		}
	}

	public static void save() {
		try (Writer w = Files.newBufferedWriter(FILE)) {
			GSON.toJson(I, w);
		} catch (Exception e) {
			System.err.println("[Nizay] config save failed: " + e);
		}
	}
}
