package dev.farmprofit;

import java.util.Locale;

/**
 * "Only count what the activity actually gave you": an item counts for a session if
 *  - it's one of that activity's own items (crops, ores, logs, gemstones...), or
 *  - it arrived right after you did that activity (broke a crop, mined, caught something, hit a mob...).
 * Things that show up while you're not doing anything (trades, picking up someone's drop, menus...) don't count.
 */
public final class Attribution {
    private static final String[] MINING_WORDS = {"Gemstone", "Mithril", "Titanium", "Glacite", "Goblin Egg", "Treasurite", "Coal", "Iron",
            "Gold", "Diamond", "Lapis", "Redstone", "Emerald", "Quartz", "Obsidian", "Hard Stone", "Cobblestone", "Sludge", "Fossil", "Umber", "Tungsten", "Sulphur"};
    private static final String[] FORAGING_WORDS = {"Log", "Wood", "Fig", "Mangrove", "Helix", "Stretching Sticks", "Tree"};

    /** When items last left the inventory without anything appearing (a compactor working, crafting...). */
    public static volatile long usedUpAt;

    /** windowMs: how long after the last action a gain still counts (a few seconds; ~35 s for sack summaries). */
    public static boolean counts(Session s, String item, long windowMs) {
        if (!Config.get().strictAttribution || s == null) return true;
        String t = Tracker.normalType(s.type);
        // dungeons / kuudra: everything in there comes from the run
        if (Tracker.DUNGEONS.equals(t) || Tracker.KUUDRA.equals(t)) return true;
        if (ownItem(t, item)) return true;
        // the compacted / crafted result of items that were just used up: always counts (it replaces them)
        if (System.currentTimeMillis() - usedUpAt < 10_000 && (item.startsWith("Enchanted ") || item.contains("Block"))) return true;
        return System.currentTimeMillis() - s.lastActivity <= windowMs;
    }

    private static boolean ownItem(String type, String item) {
        String n = item.toLowerCase(Locale.ROOT);
        if (Tracker.FARMING.equals(type)) return Items.cropFor(item) != null || Items.isTracked(item) && Items.idFor(item) != null && isFarmItem(n);
        if (Tracker.MINING.equals(type)) { for (String w : MINING_WORDS) if (item.contains(w)) return true; return false; }
        if (Tracker.FORAGING.equals(type)) { for (String w : FORAGING_WORDS) if (item.contains(w)) return true; return false; }
        return false;
    }

    private static boolean isFarmItem(String n) {
        for (String w : new String[]{"wheat", "carrot", "potato", "pumpkin", "melon", "sugar", "cane", "cactus", "cocoa", "wart", "mushroom",
                "seeds", "hay", "compost", "cropie", "squash", "fermento", "sunflower", "moonflower", "rose"}) if (n.contains(w)) return true;
        return false;
    }

    private Attribution() {}
}
