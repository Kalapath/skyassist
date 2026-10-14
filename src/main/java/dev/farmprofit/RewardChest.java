package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon and Kuudra reward chests, counted the way the other profit mods count them: from what the chest window shows.
 *
 * Before 8.14.1 only items that landed in your inventory were counted, and only in windows titled "Wood Chest" etc., so the
 * essence, enchanted books, armour and weapons never reached the profit (they are not stackable items, or not items at all),
 * Kuudra's "Free Chest" / "Paid Chest" were not recognised, and the chest's price was only charged when some stackable
 * item happened to arrive. Now:
 *  - the window is read once it has filled in (title, the contents, the price on the "Open Reward Chest" button),
 *  - the chest counts when you really opened it: coins left your purse, or something new arrived in your inventory,
 *  - then the contents are added to the Dungeons / Kuudra session (value) and the price is charged (cost), once.
 * Display only: nothing is ever clicked for you.
 */
final class RewardChest {
    /** Window titles: "Wood", "Wood Chest" (dungeons) and "Free Chest Chest" / "Paid Chest Chest" (Kuudra: Hypixel's own doubled word). */
    private static final List<String> NAMES = List.of("Wood", "Gold", "Diamond", "Emerald", "Obsidian", "Bedrock", "Free", "Paid");
    private static final Set<String> BUTTONS = Set.of("Close", "Go Back", "Open Reward Chest", "Reroll Chest", "Reroll Shard", "Chest Information");
    private static final Pattern COINS = Pattern.compile("([\\d,]+(?:\\.\\d+)?) Coins");
    private static final Pattern ESSENCE = Pattern.compile("^(\\w+) Essence(?: x([\\d,]+))?$");
    private static final Pattern SHARD = Pattern.compile("^(.+ Shard)(?: x([\\d,]+))?$");
    private static final Pattern COUNT = Pattern.compile("^(.+?) x([\\d,]+)$");
    private static final Pattern BOOK = Pattern.compile("^([A-Z][A-Za-z' -]*?) ([IVX]+)$");
    /** Biggest first, so "Hot Kuudra Key" isn't read as "Kuudra Key". */
    private static final List<String> KUUDRA_KEYS = List.of("Infernal Kuudra Key", "Fiery Kuudra Key", "Burning Kuudra Key", "Hot Kuudra Key", "Kuudra Key");
    private static final double[] KEY_COINS = {200_000, 400_000, 750_000, 1_500_000, 3_000_000};     // basic .. infernal
    private static final int[] KEY_MATS = {2, 4, 16, 40, 80};
    private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
    private static final String[] TIER_NAMES = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
    /** Dungeon drops nobody buys (no Bazaar / auction price exists for them): left out quietly, as the other profit mods do. */
    private static final Pattern WORTHLESS = Pattern.compile("^(DUNGEON_DISC_[1-5]|(?:MAXOR|STORM|GOLDOR)_THE_FISH"
            + "|ENCHANTMENT_ULTIMATE_(?:NO_PAIN_NO_GAIN|COMBO|BANK|JERRY)_[1-5]|ENCHANTMENT_(?:FEATHER_FALLING|INFINITE_QUIVER)_(?:[6-9]|10))$");
    private static final long GRACE_MS = 4000, TRAIL_MS = 3000;

    record Reward(String key, long count, double each) {}

    static final class Window {
        Object screen;
        String chest;                       // Wood, Gold ... Free, Paid
        boolean kuudra, hasButton, opened;
        final List<Reward> rewards = new ArrayList<>();
        int unpriced;
        double coins, keyValue;
        String keyName;
        Map<String, Integer> baseInv = new HashMap<>();
        long basePurse = -1, openedAt, closedAt;
        int age;

        double value() { double v = 0; for (Reward r : rewards) v += r.each() * r.count(); return v; }
        double cost() { return coins + keyValue; }
    }

    private static Window cur;
    private static Object tryScreen;
    private static int tries;

    // ---------- recognising the window ----------

    /** "Wood Chest" / "Wood" -> "Wood", "Free Chest Chest" -> "Free"; null for any other title. */
    static String chestOf(String title) {
        if (title == null) return null;
        String t = title.replace(" Chest Chest", "").replace(" Chest", "").trim();
        for (String n : NAMES) if (t.equalsIgnoreCase(n)) return n;
        return null;
    }

    static boolean isKuudraChest(String chest) { return "Free".equals(chest) || "Paid".equals(chest); }

    /** A reward-chest window (or the few seconds after it) is being watched: its items are counted from the window, not from the inventory. */
    static boolean active() { return cur != null; }

    /** The session the open chest belongs to, if it exists. */
    static Session session() {
        return cur == null ? null : Tracker.sessions.get(cur.kuudra ? Tracker.KUUDRA : Tracker.DUNGEONS);
    }

    /** Keys are part of the chest's price (charged with it), so they aren't also counted as used-up items. */
    static boolean isKey(String name) { return name != null && (name.equals("Dungeon Chest Key") || name.endsWith("Kuudra Key")); }

    // ---------- reading the contents ----------

    static Window parse(String chest, List<ItemStack> items) {
        Window w = new Window();
        w.chest = chest;
        w.kuudra = isKuudraChest(chest);
        for (ItemStack is : items) {
            String name = Tracker.strip(is.getHoverName().getString()).trim();
            if (name.isEmpty()) continue;
            if (name.equals("Open Reward Chest")) { w.hasButton = true; readCost(w, ItemIds.lore(is)); continue; }
            if (BUTTONS.contains(name) || name.startsWith("Reroll")) continue;
            readReward(w, is, name);
        }
        return w;
    }

    private static void readReward(Window w, ItemStack is, String name) {
        long stack = Math.max(1, is.getCount());
        Matcher e = ESSENCE.matcher(name);
        if (e.matches()) {                                           // Wither Essence x4: not an item, never in your inventory
            String key = e.group(1) + " Essence";
            String id = "ESSENCE_" + e.group(1).toUpperCase(Locale.ROOT);
            ItemIds.LEARNED.putIfAbsent(key, id);                    // so the session can price it by name
            add(w, key, e.group(2) != null ? count(e.group(2)) : stack, Prices.priceOfId(id));
            return;
        }
        Matcher s = SHARD.matcher(name);
        if (s.matches()) {                                           // Kraken Shard x1: goes straight to the Hunting Box
            add(w, s.group(1), s.group(2) != null ? count(s.group(2)) : stack, Prices.price(s.group(1)));
            return;
        }
        long n = stack;
        Matcher c = COUNT.matcher(name);
        if (c.matches()) { name = c.group(1).trim(); n = count(c.group(2)); }
        String id = ItemIds.priceId(is);
        if (id != null && id.startsWith("ENCHANTMENT_")) {           // an enchanted book: priced by its enchantment
            var ench = ItemIds.enchantsOf(is);
            if (ench.size() == 1) {
                var en = ench.entrySet().iterator().next();
                String key = bookKey(words(en.getKey()), roman(en.getValue()));
                add(w, key, n, Prices.priceOfId(id), id);
                return;
            }
        }
        if (name.equals("Enchanted Book")) {                         // data not readable: the first lore line names the enchantment
            List<String> lore = ItemIds.lore(is);
            Matcher b = lore.isEmpty() ? null : BOOK.matcher(lore.get(0).trim());
            if (b != null && b.matches()) {
                String key = bookKey(b.group(1), b.group(2));
                add(w, key, n, Prices.price(key), Items.guessId(key));
                return;
            }
        }
        if (id != null && id.contains(";")) {                        // a pet: priced by its type and rarity, listed the way a pet drop is ("Spirit (Legendary pet)")
            int cut = id.indexOf(';');
            int tier = 0;
            try { tier = Integer.parseInt(id.substring(cut + 1)); } catch (NumberFormatException ignored) {}
            add(w, words(id.substring(0, cut)) + " (" + TIER_NAMES[Math.max(0, Math.min(TIER_NAMES.length - 1, tier))] + " pet)", n, Prices.priceOfId(id));
            return;
        }
        double each = id != null ? Prices.priceOfId(id) : 0;
        if (id != null && each > 0 && !id.equals("ATTRIBUTE_SHARD")) ItemIds.LEARNED.putIfAbsent(name, id);     // the session prices by name: make sure it finds the same id
        if (each <= 0) each = Prices.price(name);
        add(w, name, n, each, id);
    }

    private static void add(Window w, String key, long n, double each) {
        add(w, key, n, each, null);
    }

    /** id: the item's price id, so that things nobody buys (discs, Jerry books...) aren't reported as "without a price". */
    private static void add(Window w, String key, long n, double each, String id) {
        if (n <= 0) return;
        if (each > 0) w.rewards.add(new Reward(key, n, each));
        else if (id == null || !WORTHLESS.matcher(id).matches()) w.unpriced++;
    }

    /** "Enchanted Book (Ultimate Wise V)": the ultimate ones are written without "Ultimate" in some menus, so try both names. */
    static String bookKey(String enchant, String level) {
        String plain = "Enchanted Book (" + enchant + " " + level + ")";
        if (enchant.startsWith("Ultimate ") || Prices.bazaarRaw(Items.guessId(plain)) != null) return plain;
        String ultimate = "Enchanted Book (Ultimate " + enchant + " " + level + ")";
        return Prices.bazaarRaw(Items.guessId(ultimate)) != null ? ultimate : plain;
    }

    private static String roman(int n) { return n > 0 && n < ROMAN.length ? ROMAN[n] : String.valueOf(n); }

    /** "ultimate_wise" -> "Ultimate Wise". */
    private static String words(String id) {
        StringBuilder b = new StringBuilder();
        for (String p : id.toLowerCase(Locale.ROOT).split("_")) {
            if (p.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1));
        }
        return b.toString();
    }

    private static long count(String s) {
        try { return Long.parseLong(s.replace(",", "")); } catch (Exception e) { return 0; }
    }

    // ---------- the price of the chest ----------

    /** Coins and keys named on the "Open Reward Chest" button. Free chests name nothing. */
    private static void readCost(Window w, List<String> lore) {
        boolean inCost = false;
        for (String raw : lore) {
            String l = raw.trim();
            if (l.isEmpty() || l.equalsIgnoreCase("FREE") || l.startsWith("Requires")) continue;
            if (l.startsWith("Cost")) { inCost = true; continue; }
            Matcher m = COINS.matcher(l);
            if (m.find() && (inCost || m.matches())) {
                try { w.coins += Double.parseDouble(m.group(1).replace(",", "")); } catch (NumberFormatException ignored) {}
                continue;
            }
            if (l.equals("Dungeon Chest Key") || (inCost && l.contains("Dungeon Chest Key"))) {
                w.keyName = "Dungeon Chest Key";
                w.keyValue += Prices.price("Dungeon Chest Key");
                continue;
            }
            String key = kuudraKey(l);
            if (key != null) { w.keyName = key; w.keyValue += kuudraKeyValue(key); }
        }
    }

    /** "Burning Kuudra Key" -> "Burning Kuudra Key" (the line names one of the keys), else null. */
    static String kuudraKey(String line) {
        for (String k : KUUDRA_KEYS) if (line.equals(k) || line.startsWith(k + " ")) return k;
        return null;
    }

    /** What a Kuudra key cost you to make: its coins plus the materials (the cheaper of Enchanted Mycelium / Red Sand) and 2 Corrupted Nether Stars. */
    static double kuudraKeyValue(String key) {
        double fixed = Config.get().kuudraKeyCost;
        if (fixed > 0) return fixed;
        int tier = KUUDRA_KEYS.size() - 1 - Math.max(0, KUUDRA_KEYS.indexOf(key));          // 0 = basic key .. 4 = infernal
        double myc = Prices.priceOfId("ENCHANTED_MYCELIUM"), sand = Prices.priceOfId("ENCHANTED_RED_SAND");
        double mat = myc > 0 && sand > 0 ? Math.min(myc, sand) : Math.max(myc, sand);
        return KEY_COINS[tier] + KEY_MATS[tier] * mat + 2 * Prices.priceOfId("CORRUPTED_NETHER_STAR");
    }

    // ---------- counting it when it is opened ----------

    /** Every tick, before the inventory is looked at. */
    static void tick(Minecraft mc) {
        Object screen = Compat.screen(mc);
        long now = System.currentTimeMillis();
        if (cur != null) {
            boolean here = screen == cur.screen;
            if (here) cur.closedAt = 0;
            else if (cur.closedAt == 0) cur.closedAt = now;
            if (!cur.opened) {
                if (openedNow(mc, cur)) open(cur, now);
                else if (cur.closedAt > 0 && now - cur.closedAt > GRACE_MS) cur = null;                      // closed again without opening it
                else if (here && ++cur.age % 5 == 0) refresh(cur);                                           // a reroll (Kismet) changes what is in it
            } else if (cur.closedAt > 0 && now - Math.max(cur.openedAt, cur.closedAt) > TRAIL_MS) {
                cur = null;                                                                                  // everything has arrived
            }
            if (cur != null && here) return;
        }
        if (screen == null) return;
        if (screen != tryScreen) { tryScreen = screen; tries = 0; }
        if (tries > 80) return;                                                                              // (about 4 seconds of waiting for it to fill in)
        String chest = chestOf(Menus.title(screen));
        if (chest == null) { tries = 99; return; }
        tries++;
        Window w = parse(chest, Menus.menuItems(screen));
        if (!w.hasButton || w.rewards.isEmpty() && w.unpriced == 0) return;     // still filling in, or already opened: nothing to count
        w.screen = screen;
        w.baseInv = allItems(mc);
        w.basePurse = Tracker.purse;
        cur = w;
        tries = 99;
        summary(w);
    }

    /** The window may change before it is opened (a Kismet reroll): read it again and tell you if the chest is a different one now. */
    private static void refresh(Window w) {
        Window n = parse(w.chest, Menus.menuItems(w.screen));
        if (!n.hasButton || n.rewards.isEmpty()) return;               // (the button is gone while it is being opened, or it is refilling: keep what we read before)
        if (n.rewards.size() == w.rewards.size() && Math.abs(n.value() - w.value()) < 1 && Math.abs(n.cost() - w.cost()) < 1) return;
        w.rewards.clear();
        w.rewards.addAll(n.rewards);
        w.unpriced = n.unpriced;
        w.coins = n.coins;
        w.keyValue = n.keyValue;
        w.keyName = n.keyName;
        summary(w);
    }

    /** Coins left your purse, or something new arrived in your inventory: the chest was opened. */
    private static boolean openedNow(Minecraft mc, Window w) {
        if (w.coins >= 1000 && w.basePurse >= 0 && Tracker.purse >= 0 && w.basePurse - Tracker.purse >= w.coins * 0.9) return true;
        Map<String, Integer> now = allItems(mc);
        if (w.keyName != null) {                                   // a chest that costs a key (Kuudra's paid chests): the key is gone
            int had = w.baseInv.getOrDefault(w.keyName, 0);
            if (had > 0 && now.getOrDefault(w.keyName, 0) < had) return true;
        }
        for (var e : now.entrySet()) if (e.getValue() > w.baseInv.getOrDefault(e.getKey(), 0)) return true;
        return false;
    }

    /** All 36 inventory slots by item name (not only the priced ones). */
    private static Map<String, Integer> allItems(Minecraft mc) {
        Map<String, Integer> out = new HashMap<>();
        try {
            var inv = mc.player.getInventory();
            for (int i = 0; i < 36; i++) {
                var st = inv.getItem(i);
                if (!st.isEmpty()) out.merge(Tracker.strip(st.getHoverName().getString()), st.getCount(), Integer::sum);
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static void open(Window w, long now) {
        w.opened = true;
        w.openedAt = now;
        Session s = Tracker.chestSession(w.kuudra ? Tracker.KUUDRA : Tracker.DUNGEONS);
        if (s == null) return;
        for (Reward r : w.rewards) s.addItem(r.key(), r.count());
        s.costs += w.cost();
        Debug.saw("reward chest");
        if (Config.get().chestProfit)
            Tracker.say("§6[" + (w.kuudra ? "Kuudra" : "Dungeons") + "] §7Counted in your profit: §6+" + Fmt.coins(w.value()) + " §7contents, §c-" + Fmt.coins(w.cost()) + " §7price.");
    }

    private static void summary(Window w) {
        if (!Config.get().chestProfit) return;
        double value = w.value(), cost = w.cost(), profit = value - cost;
        Tracker.say("§6[" + (w.kuudra ? "Kuudra" : "Dungeons") + "] §f" + w.chest + " Chest§7: worth §6" + Fmt.coins(value) + "§7, costs §c" + Fmt.coins(cost)
                + (w.keyName != null ? " §8(" + w.keyName + ")" : "") + "§7 → " + (profit >= 0 ? "§a+" : "§c") + Fmt.coins(profit)
                + (w.unpriced > 0 ? " §8(" + w.unpriced + " item" + (w.unpriced > 1 ? "s" : "") + " without a price)" : ""));
    }

    private RewardChest() {}
}
