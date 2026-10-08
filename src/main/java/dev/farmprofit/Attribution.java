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
    private static final String[] FISHING_WORDS = {"Fish", "Salmon", "Clownfish", "Pufferfish", "Sponge", "Prismarine", "Lily Pad",
            "Ink Sac", "Shark", "Squid", "Sea Lantern", "Magmafish", "Lava", "Trophy", "Bait", "Nurse Shark", "Tiger Shark", "Blue Shark"};

    /** When a menu (crafting, Bazaar, chest...) was last open: gains right after it are crafted / bought, not loot. */
    public static volatile long menuAt;

    /** When items last left the inventory without anything appearing (a compactor working, crafting...). */
    public static volatile long usedUpAt;

    /** windowMs: how long after the last action a gain still counts (a few seconds; ~35 s for sack summaries). */
    public static boolean counts(Session s, String item, long windowMs) {
        if (!Config.get().strictAttribution || s == null) return true;
        String t = Tracker.normalType(s.type);
        // dungeons / kuudra: everything in there comes from the run
        if (Tracker.DUNGEONS.equals(t) || Tracker.KUUDRA.equals(t)) return true;
        if (belongsElsewhere(t, item)) return false;           // fish on the Farming HUD, crops on Mining... never
        if (ownItem(t, item)) return true;
        long now = System.currentTimeMillis();
        // right after a menu: crafted / bought / moved items, not loot (only the activity's own items count then)
        if (now - menuAt < Math.max(windowMs, 15_000)) return false;
        // the compacted result of items that were just used up: counts (it replaces them)
        if (now - usedUpAt < 10_000 && (item.startsWith("Enchanted ") || item.contains("Block"))) return true;
        return now - s.lastActivity <= windowMs;
    }

    /** Is this clearly another activity's item? */
    private static boolean belongsElsewhere(String type, String item) {
        boolean fish = has(item, FISHING_WORDS), mine = has(item, MINING_WORDS), wood = has(item, FORAGING_WORDS),
                crop = isFarmItem(item.toLowerCase(Locale.ROOT));
        return switch (type) {
            case "Farming" -> (fish || mine || wood) && !crop;
            case "Mining" -> (fish || crop || wood) && !mine;
            case "Foraging" -> (fish || crop || mine) && !wood;
            case "Fishing" -> (crop || mine || wood) && !fish && !item.contains("Shard");
            default -> false;
        };
    }

    private static boolean has(String item, String[] words) {
        for (String w : words) if (item.contains(w)) return true;
        return false;
    }

    private static boolean ownItem(String type, String item) {
        if (Tracker.FISHING.equals(type)) return has(item, FISHING_WORDS);
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
