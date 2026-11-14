package dev.nizay.auction;

import dev.nizay.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * AuctionHelper — автоматическое перевыставление предметов на /ah (FunTime).
 *
 * Круг работы:
 *   1) /ah  -> ждём меню аукциона (ahTitleRegex)
 *   2) клик по слоту «Ваши товары» (myLotsSlot) -> ждём меню (myLotsTitleRegex)
 *   3) shift-клик по каждому предмету, чей lore/название совпадает с expiredLoreRegex (забираем в инвентарь)
 *   4) закрываем меню; для каждого предмета из inventory, чей id есть в prices, ставим его в руку
 *      и выполняем  /ah sell <цена>
 *   5) ждём intervalSec и повторяем.
 *
 * Названия меню и номера слотов у сервера могут меняться — они вынесены в config/nizay.json (блок "ah").
 * Скрипт работает только пока включён в меню (Right Shift -> Auction) и ничего не делает вне мира.
 */
public final class AuctionHelper {
	private AuctionHelper() {}

	private enum S { IDLE, WAIT_AH, WAIT_MY, TAKE, CLOSE, SELL_PICK, SELL_CMD, SELL_CLOSE }

	private static S state = S.IDLE;
	private static int wait, timeout;
	private static long nextRun;
	private static String status = "off";
	private static final Deque<Integer> takeQueue = new ArrayDeque<>();
	private static final Deque<int[]> sellQueue = new ArrayDeque<>(); // {invIndex, ...}
	private static long cycles, relisted;

	public static String status() { return status + (cycles > 0 ? "  (cycles " + cycles + ", listed " + relisted + ")" : ""); }

	public static void reset() {
		state = S.IDLE; wait = 0; nextRun = 0; takeQueue.clear(); sellQueue.clear();
		status = Config.I.ah.enabled ? "waiting" : "off";
	}

	public static void tick(Minecraft mc) {
		Config.Auction a = Config.I.ah;
		if (!a.enabled) return;
		if (mc.player == null || mc.level == null || mc.gameMode == null) { state = S.IDLE; return; }
		if (wait > 0) { wait--; return; }

		switch (state) {
			case IDLE -> {
				if (System.currentTimeMillis() < nextRun) { status = "next cycle in " + Math.max(0, (nextRun - System.currentTimeMillis()) / 1000) + "s"; return; }
				if (mc.screen != null) return; // не мешаем игроку, пока открыт другой экран
				mc.player.connection.sendCommand("ah");
				state = S.WAIT_AH; timeout = 100; status = "opening /ah";
			}
			case WAIT_AH -> {
				AbstractContainerScreen<?> s = container(mc);
				if (s != null && matches(a.ahTitleRegex, s.getTitle().getString())) {
					click(mc, s.getMenu(), a.myLotsSlot, 0, ClickType.PICKUP);
					state = S.WAIT_MY; timeout = 100; status = "opening my lots"; delay(a);
				} else if (--timeout <= 0) abort(mc, "ah menu not found (check ahTitleRegex)");
			}
			case WAIT_MY -> {
				AbstractContainerScreen<?> s = container(mc);
				if (s != null && matches(a.myLotsTitleRegex, s.getTitle().getString())) {
					takeQueue.clear();
					AbstractContainerMenu m = s.getMenu();
					int top = Math.max(0, m.slots.size() - 36); // верхняя часть = содержимое меню
					Pattern p = Pattern.compile(a.expiredLoreRegex);
					for (int i = 0; i < top; i++) {
						ItemStack st = m.slots.get(i).getItem();
						if (!st.isEmpty() && p.matcher(textOf(st)).find()) takeQueue.add(i);
					}
					state = S.TAKE; status = "taking " + takeQueue.size() + " items"; delay(a);
				} else if (--timeout <= 0) abort(mc, "my lots menu not found (check myLotsSlot / myLotsTitleRegex)");
			}
			case TAKE -> {
				AbstractContainerScreen<?> s = container(mc);
				if (s == null) { abort(mc, "menu closed"); return; }
				Integer slot = takeQueue.poll();
				if (slot == null) { state = S.CLOSE; return; }
				click(mc, s.getMenu(), slot, 0, ClickType.QUICK_MOVE);
				delay(a);
			}
			case CLOSE -> {
				mc.player.closeContainer();
				state = S.SELL_PICK; wait = 6; status = "preparing to sell";
				buildSellQueue(mc);
			}
			case SELL_PICK -> {
				int[] job = sellQueue.peek();
				if (job == null) { finish(a); return; }
				Inventory inv = mc.player.getInventory();
				int idx = job[0];
				ItemStack st = inv.getItem(idx);
				if (st.isEmpty()) { sellQueue.poll(); return; }
				if (idx < 9) {
					inv.setSelectedSlot(idx);
				} else {
					// меняем предмет из основного инвентаря с хотбар-слотом 8 и выбираем его
					click(mc, mc.player.inventoryMenu, idx, 8, ClickType.SWAP);
					inv.setSelectedSlot(8);
				}
				state = S.SELL_CMD; wait = 3; // даём клиенту синхронизировать выбранный слот
			}
			case SELL_CMD -> {
				int[] job = sellQueue.poll();
				if (job == null) { finish(a); return; }
				ItemStack held = mc.player.getMainHandItem();
				if (!held.isEmpty()) {
					long price = priceOf(a, held);
					mc.player.connection.sendCommand(a.sellCommand + " " + price);
					relisted++; status = "listing " + held.getHoverName().getString() + " for " + price;
				}
				state = S.SELL_CLOSE; wait = 4;
			}
			case SELL_CLOSE -> {
				if (container(mc) != null) mc.player.closeContainer(); // если сервер открыл окно подтверждения
				state = S.SELL_PICK; delay(a);
			}
		}
	}

	// ------------------------------------------------------------------ helpers
	private static void buildSellQueue(Minecraft mc) {
		sellQueue.clear();
		Inventory inv = mc.player.getInventory();
		Config.Auction a = Config.I.ah;
		for (int i = 0; i < 36; i++) {
			ItemStack st = inv.getItem(i);
			if (st.isEmpty()) continue;
			String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
			if (a.prices.containsKey(id)) sellQueue.add(new int[]{i});
		}
	}

	private static long priceOf(Config.Auction a, ItemStack st) {
		String id = BuiltInRegistries.ITEM.getKey(st.getItem()).toString();
		Long p = a.prices.get(id);
		return p != null ? p : a.defaultPrice;
	}

	private static String textOf(ItemStack st) {
		StringBuilder sb = new StringBuilder(st.getHoverName().getString());
		ItemLore lore = st.get(DataComponents.LORE);
		if (lore != null) for (Component line : lore.lines()) sb.append('\n').append(line.getString());
		return sb.toString();
	}

	private static AbstractContainerScreen<?> container(Minecraft mc) {
		return mc.screen instanceof AbstractContainerScreen<?> s ? s : null;
	}

	private static boolean matches(String regex, String s) {
		try { return Pattern.compile(regex).matcher(s).find(); } catch (Exception e) { return false; }
	}

	private static void click(Minecraft mc, AbstractContainerMenu menu, int slot, int button, ClickType type) {
		mc.gameMode.handleInventoryMouseClick(menu.containerId, slot, button, type, mc.player);
	}

	private static void delay(Config.Auction a) {
		int ms = a.actionDelayMs + (a.jitterMs > 0 ? ThreadLocalRandom.current().nextInt(a.jitterMs + 1) : 0);
		wait = Math.max(1, ms / 50);
	}

	private static void finish(Config.Auction a) {
		cycles++; state = S.IDLE; status = "cycle done";
		nextRun = System.currentTimeMillis() + a.intervalSec * 1000L;
	}

	private static void abort(Minecraft mc, String why) {
		if (mc.player != null && container(mc) != null) mc.player.closeContainer();
		state = S.IDLE; takeQueue.clear(); sellQueue.clear();
		status = "error: " + why;
		nextRun = System.currentTimeMillis() + Config.I.ah.intervalSec * 1000L;
		if (mc.player != null) mc.player.displayClientMessage(Component.literal("[AuctionHelper] " + why), true);
	}
}
