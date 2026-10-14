package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Menus where items you receive should count as profit (dungeon / Kuudra reward chests, Garden visitors),
 * plus the Croesus chest overview. The reward chest window itself is handled by {@link RewardChest}.
 */
public final class Menus {
    private static final Pattern COINS = Pattern.compile("([\\d,]+(?:\\.\\d+)?) Coins");
    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence(?: x([\\d,]+))?$");
    private static final Pattern BOOK = Pattern.compile("^([A-Z][A-Za-z' -]*?) ([IVX]+)$");
    private static final Pattern COUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
    /** "Catacombs - Floor VII", "Master Catacombs - Floor II", "Kuudra - Infernal": the list of a run's chests. */
    private static final Pattern OVERVIEW = Pattern.compile(".*Catacombs - Flo.*|Kuudra - .*");

    private static Object lastScreen;
    private static boolean countHere;
    private static String kind;            // "chest", "visitor" or null
    private static boolean analysed;
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

    static String title(Object screen) {
        Object t = Reflect.call(screen, "getTitle");
        return t instanceof net.minecraft.network.chat.Component c ? Tracker.strip(c.getString()) : "";
    }

    /** Should inventory changes count while this menu is open? Re-checked when the menu changes. */
    public static boolean countsIn(Minecraft mc) {
        Object screen = Compat.screen(mc);
        if (screen == null || screen instanceof net.minecraft.client.gui.screens.ChatScreen) { lastScreen = null; return true; }
        if (screen == lastScreen && analysed) return countHere;
        if (screen != lastScreen) waited = 0;
        lastScreen = screen;
        countHere = false;
        kind = null;
        String title = title(screen);
        for (String w : Config.get().countInMenus) if (!w.isBlank() && title.contains(w)) countHere = true;
        if (RewardChest.chestOf(title) != null) { countHere = true; kind = "chest"; }     // (its contents are counted by RewardChest)
        List<ItemStack> items = menuItems(screen);
        for (ItemStack is : items) {
            String name = Tracker.strip(is.getHoverName().getString());
            if (name.equals("Accept Offer")) { countHere = true; kind = "visitor"; }
        }
        // menus fill in a tick or two after opening: look again until there's something to read
        analysed = !items.isEmpty() || ++waited > 20;
        if (!items.isEmpty()) { Accessories.scanMenu(title, items); Greenhouse.noticeItems(items, false); Shards.scanMenu(title, items); Unlocks.scanMenu(title, items); Auctions.scanMenu(title, items); Storage.scanMenu(title, items); VisitorShop.scanMenu(title, items); }
        if (analysed && !"chest".equals(kind)) analyseCroesus(title, items);
        return countHere;
    }

    public static boolean isVisitorMenu() { return "visitor".equals(kind); }

    // ---------------- Croesus chest overview ----------------

    /** The end-of-run view: each chest is an item whose lore lists its contents and cost. */
    private static void analyseCroesus(String title, List<ItemStack> items) {
        if (!Config.get().chestProfit) return;
        boolean overview = OVERVIEW.matcher(title).matches();
        List<String> results = new ArrayList<>();
        String bestName = null;
        double best = -Double.MAX_VALUE;
        for (ItemStack is : items) {
            String chest = RewardChest.chestOf(Tracker.strip(is.getHoverName().getString()).trim());
            if (chest == null) continue;
            List<String> lore = ItemIds.lore(is);
            if (!overview && lore.stream().noneMatch(l -> l.trim().startsWith("Contents"))) continue;      // some other menu with an item called "Gold"
            double value = 0, cost = 0;
            boolean inCost = false, opened = false;
            for (String line : lore) {
                String l = line.trim();
                if (l.isEmpty() || l.equalsIgnoreCase("FREE") || l.startsWith("Requires")) continue;
                if (l.startsWith("Already opened")) { opened = true; break; }
                if (l.startsWith("Contents")) { inCost = false; continue; }
                if (l.startsWith("Cost")) { inCost = true; continue; }
                Matcher cm = COINS.matcher(l);
                if (cm.find() && (inCost || cm.matches())) { cost += Double.parseDouble(cm.group(1).replace(",", "")); continue; }
                if (l.equals("Dungeon Chest Key")) { cost += Prices.price("Dungeon Chest Key"); continue; }
                String key = RewardChest.kuudraKey(l);
                if (key != null) { cost += RewardChest.kuudraKeyValue(key); continue; }
                if (!inCost) value += itemValue(l, List.of());
            }
            if (opened) continue;
            String name = chest + " Chest";
            double profit = value - cost;
            results.add("§f" + name + " §7" + Fmt.coins(value) + " - " + Fmt.coins(cost) + " = " + (profit >= 0 ? "§a+" : "§c") + Fmt.coins(profit));
            if (profit > best) { best = profit; bestName = name; }
        }
        if (results.isEmpty()) return;
        Tracker.say("§6§l[Dungeons] Chest profit:");
        for (String r : results) Tracker.say(" " + r);
        if (bestName != null) Tracker.say(" §7Best to open: §a" + bestName);
    }

    /** Value of one contents line / item name, understanding essence, books and "x2" amounts. */
    private static double itemValue(String name, List<String> lore) {
        Matcher e = ESSENCE.matcher(name);
        if (e.matches()) {
            double each = Prices.priceOfId("ESSENCE_" + e.group(1).toUpperCase(Locale.ROOT));
            return each * (e.group(2) != null ? Double.parseDouble(e.group(2).replace(",", "")) : 1);
        }
        if (name.equals("Enchanted Book") && !lore.isEmpty()) name = lore.get(0).trim();
        Matcher b = BOOK.matcher(name);
        if (b.matches()) name = RewardChest.bookKey(b.group(1), b.group(2));
        int count = 1;
        Matcher c = COUNT.matcher(name);
        if (c.matches()) { name = c.group(1); count = Integer.parseInt(c.group(2).replace(",", "")); }
        return Prices.price(name) * count;
    }

    private Menus() {}
}
