package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menus where items you receive should count as profit (dungeon reward chests, Garden visitors),
 * plus the dungeon chest profit calculator.
 */
public final class Menus {
    private static final String[] CHESTS = {"Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest", "Bedrock Chest"};
    private static final Pattern COINS = Pattern.compile("([\\d,]+(?:\\.\\d+)?) Coins");
    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence x?([\\d,]+)$");
    private static final Pattern BOOK = Pattern.compile("^([A-Z][A-Za-z' -]+) ([IVX]+)$");
    private static final Pattern COUNT = Pattern.compile("^(.+?) x([\\d,]+)$");

    private static Object lastScreen;
    private static boolean countHere;
    private static String kind;            // "chest", "visitor" or null
    private static double chestCost;
    private static boolean chestCostCharged, analysed;
    private static int waited;

    /** The items in the open menu (not your inventory), via runtime lookups. */
    public static List<ItemStack> menuItems(Object screen) {
        List<ItemStack> out = new ArrayList<>();
        Object menu = Reflect.call(screen, new String[]{"getMenu"});
        Object slots = Reflect.field(menu, "slots");
        if (!(slots instanceof List<?> list)) return out;
        int containerSlots = Math.max(0, list.size() - 36);   // the last 36 slots are your inventory
        for (int i = 0; i < containerSlots; i++) {
            Object st = Reflect.call(list.get(i), "getItem");
            if (st instanceof ItemStack is && !is.isEmpty()) out.add(is);
        }
        return out;
    }

    private static String title(Object screen) {
        Object t = Reflect.call(screen, "getTitle");
        return t instanceof net.minecraft.network.chat.Component c ? Tracker.strip(c.getString()) : "";
    }

    /** Should inventory changes count while this menu is open? Re-checked when the menu changes. */
    public static boolean countsIn(Minecraft mc) {
        Object screen = Compat.screen(mc);
        if (screen == null || screen instanceof net.minecraft.client.gui.screens.ChatScreen) { lastScreen = null; return true; }
        if (screen == lastScreen && analysed) return countHere;
        if (screen != lastScreen) { chestCostCharged = false; waited = 0; }
        lastScreen = screen;
        countHere = false;
        kind = null;
        String title = title(screen);
        for (String w : Config.get().countInMenus) if (!w.isBlank() && title.contains(w)) countHere = true;
        List<ItemStack> items = menuItems(screen);
        for (ItemStack is : items) {
            String name = Tracker.strip(is.getHoverName().getString());
            if (name.equals("Accept Offer")) { countHere = true; kind = "visitor"; }
        }
        for (String c : CHESTS) if (title.contains(c)) kind = "chest";
        // menus fill in a tick or two after opening: look again until there's something to read
        analysed = !items.isEmpty() || ++waited > 20;
        if (!items.isEmpty()) { Accessories.scanMenu(title, items); Greenhouse.noticeItems(items, false); Shards.scanMenu(title, items); Storage.scanMenu(title, items); VisitorShop.scanMenu(title, items); }
        if (analysed) {
            if ("chest".equals(kind)) analyseChestScreen(title, items);
            else analyseCroesus(items);
        }
        return countHere;
    }

    /** Called when items were gained inside a counted menu: charges the chest's cost once. */
    public static void onGainedInMenu(Session s) {
        if ("chest".equals(kind) && !chestCostCharged && chestCost > 0) {
            s.costs += chestCost;
            chestCostCharged = true;
        }
    }

    public static boolean isVisitorMenu() { return "visitor".equals(kind); }

    // ---------------- dungeon chest profit ----------------

    /** Inside one reward chest: contents are the menu items, the cost is on the "Open Reward Chest" button. */
    private static void analyseChestScreen(String title, List<ItemStack> items) {
        double value = 0, cost = 0;
        List<String> contents = new ArrayList<>();
        for (ItemStack is : items) {
            String name = Tracker.strip(is.getHoverName().getString());
            if (name.equals("Open Reward Chest")) { cost = costFrom(ItemIds.lore(is)); continue; }
            if (name.isBlank() || name.equals("Close") || name.equals("Go Back")) continue;
            double v = itemValue(name, ItemIds.lore(is)) * Math.max(1, is.getCount());
            if (v > 0 || name.contains("Essence")) { value += v; contents.add(name); }
        }
        chestCost = cost;
        if (!Config.get().chestProfit || contents.isEmpty()) return;
        double profit = value - cost;
        Tracker.say("§6[Dungeons] §f" + title + "§7: worth §6" + Fmt.coins(value) + "§7, costs §c" + Fmt.coins(cost)
                + "§7 → " + (profit >= 0 ? "§a+" : "§c") + Fmt.coins(profit));
    }

    /** Croesus / end-of-run view: each chest is an item whose lore lists its contents and cost. */
    private static void analyseCroesus(List<ItemStack> items) {
        if (!Config.get().chestProfit) return;
        List<String> results = new ArrayList<>();
        String bestName = null;
        double best = -Double.MAX_VALUE;
        for (ItemStack is : items) {
            String name = Tracker.strip(is.getHoverName().getString());
            boolean isChest = false;
            for (String c : CHESTS) if (name.equals(c)) isChest = true;
            if (!isChest) continue;
            List<String> lore = ItemIds.lore(is);
            double value = 0;
            boolean inContents = false;
            for (String line : lore) {
                String l = line.trim();
                if (l.startsWith("Contents")) { inContents = true; continue; }
                if (l.startsWith("Cost")) inContents = false;
                if (inContents && !l.isEmpty()) value += itemValue(l, List.of());
            }
            double cost = costFrom(lore);
            double profit = value - cost;
            results.add("§f" + name + " §7" + Fmt.coins(value) + " - " + Fmt.coins(cost) + " = " + (profit >= 0 ? "§a+" : "§c") + Fmt.coins(profit));
            if (profit > best) { best = profit; bestName = name; }
        }
        if (results.isEmpty()) return;
        Tracker.say("§6§l[Dungeons] Chest profit:");
        for (String r : results) Tracker.say(" " + r);
        if (bestName != null) Tracker.say(" §7Best to open: §a" + bestName);
    }

    private static double costFrom(List<String> lore) {
        double cost = 0;
        boolean inCost = false;
        for (String line : lore) {
            String l = line.trim();
            if (l.startsWith("Cost")) { inCost = true; continue; }
            if (!inCost || l.isEmpty()) continue;
            if (l.equalsIgnoreCase("FREE")) continue;
            Matcher m = COINS.matcher(l);
            if (m.find()) cost += Double.parseDouble(m.group(1).replace(",", ""));
            else if (l.contains("Dungeon Chest Key")) cost += Prices.price("Dungeon Chest Key");
        }
        return cost;
    }

    /** Value of one contents line / item name, understanding essence, books and "x2" amounts. */
    private static double itemValue(String name, List<String> lore) {
        Matcher e = ESSENCE.matcher(name);
        if (e.matches()) {
            double each = Prices.priceOfId("ESSENCE_" + e.group(1).toUpperCase(Locale.ROOT));
            return each * Double.parseDouble(e.group(2).replace(",", ""));
        }
        if (name.equals("Enchanted Book") && !lore.isEmpty()) name = "Enchanted Book (" + lore.get(0).trim() + ")";
        else if (BOOK.matcher(name).matches()) name = "Enchanted Book (" + name + ")";
        int count = 1;
        Matcher c = COUNT.matcher(name);
        if (c.matches()) { name = c.group(1); count = Integer.parseInt(c.group(2).replace(",", "")); }
        return Prices.price(name) * count;
    }

    private Menus() {}
}
