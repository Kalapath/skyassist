package dev.farmprofit;

import java.util.ArrayList;
import java.util.List;

/**
 * "What should I farm / mine right now?" Estimates coins per hour for each crop or ore
 * from live Bazaar (or NPC) prices, your fortune and your speed.
 */
public final class Suggest {

    public record Option(String name, double perHour, double perItem, String sellAs) {}

    // ---------------- farming ----------------
    // crop, base drops per block, then sellable forms as id:itemsNeeded
    private static final Object[][] CROPS = {
            {"Wheat", 1.0, "WHEAT:1", "ENCHANTED_WHEAT:160", "ENCHANTED_HAY_BLOCK:1296"},
            {"Carrot", 3.0, "CARROT_ITEM:1", "ENCHANTED_CARROT:160"},
            {"Potato", 3.0, "POTATO_ITEM:1", "ENCHANTED_POTATO:160", "ENCHANTED_BAKED_POTATO:25600"},
            {"Pumpkin", 1.0, "PUMPKIN:1", "ENCHANTED_PUMPKIN:160", "POLISHED_PUMPKIN:25600"},
            {"Melon", 5.0, "MELON:1", "ENCHANTED_MELON:160", "ENCHANTED_MELON_BLOCK:25600"},
            {"Sugar Cane", 2.0, "SUGAR_CANE:1", "ENCHANTED_SUGAR:160", "ENCHANTED_SUGAR_CANE:25600"},
            {"Cactus", 2.0, "CACTUS:1", "ENCHANTED_CACTUS_GREEN:160", "ENCHANTED_CACTUS:25600"},
            {"Cocoa Beans", 3.0, "INK_SACK:3:1", "ENCHANTED_COCOA:160"},
            {"Nether Wart", 2.5, "NETHER_STALK:1", "ENCHANTED_NETHER_STALK:160", "MUTANT_NETHER_STALK:25600"},
            {"Mushroom", 1.0, "RED_MUSHROOM:1", "ENCHANTED_RED_MUSHROOM:160"},
    };

    public static List<Option> farming() {
        Session s = Tracker.sessions.get(Tracker.FARMING);
        double bps = Config.get().defaultBps;
        if (s != null && s.durationMs(System.currentTimeMillis()) > 60_000 && s.breaksPerSecond(System.currentTimeMillis()) > 3) {
            bps = s.breaksPerSecond(System.currentTimeMillis());
        }
        double ff = Math.max(0, Tracker.number(Tracker.tab.get("Farming Fortune")));
        List<Option> out = new ArrayList<>();
        for (Object[] c : CROPS) {
            String crop = (String) c[0];
            double base = (double) c[1];
            double cropFF = Math.max(0, Tracker.number(Tracker.tab.get(crop + " Fortune")));
            double itemsPerHour = bps * 3600 * base * (1 + (ff + cropFF) / 100.0);
            Best best = bestForm(c, 2);
            if (best.price <= 0) continue;
            out.add(new Option(crop, itemsPerHour * best.price, best.price, best.name));
        }
        out.sort((a, b) -> Double.compare(b.perHour, a.perHour));
        return out;
    }

    // ---------------- mining ----------------
    // name, block strength, fortune stat, island tags (D = Dwarven, H = Hollows, M = Mineshaft), sellable forms
    private static final Object[][] ORES = {
            {"Mithril", 800.0, "Dwarven Metal Fortune", "D", "MITHRIL_ORE:1", "ENCHANTED_MITHRIL:160"},
            {"Titanium", 2000.0, "Dwarven Metal Fortune", "D", "TITANIUM_ORE:1", "ENCHANTED_TITANIUM:160"},
            {"Glacite", 6000.0, "Block Fortune", "DM", "GLACITE:1", "ENCHANTED_GLACITE:160"},
            {"Umber", 5600.0, "Dwarven Metal Fortune", "DM", "UMBER:1", "ENCHANTED_UMBER:160"},
            {"Tungsten", 5600.0, "Dwarven Metal Fortune", "DM", "TUNGSTEN:1", "ENCHANTED_TUNGSTEN:160"},
            {"Hard Stone", 50.0, "Block Fortune", "H", "HARD_STONE:1", "ENCHANTED_HARD_STONE:576"},
            {"Ruby", 2300.0, "Gemstone Fortune", "H", "ROUGH_RUBY_GEM:1", "FLAWED_RUBY_GEM:80", "FINE_RUBY_GEM:6400"},
            {"Amber", 3000.0, "Gemstone Fortune", "H", "ROUGH_AMBER_GEM:1", "FLAWED_AMBER_GEM:80", "FINE_AMBER_GEM:6400"},
            {"Sapphire", 3000.0, "Gemstone Fortune", "H", "ROUGH_SAPPHIRE_GEM:1", "FLAWED_SAPPHIRE_GEM:80", "FINE_SAPPHIRE_GEM:6400"},
            {"Jade", 3000.0, "Gemstone Fortune", "H", "ROUGH_JADE_GEM:1", "FLAWED_JADE_GEM:80", "FINE_JADE_GEM:6400"},
            {"Amethyst", 3000.0, "Gemstone Fortune", "H", "ROUGH_AMETHYST_GEM:1", "FLAWED_AMETHYST_GEM:80", "FINE_AMETHYST_GEM:6400"},
            {"Topaz", 3800.0, "Gemstone Fortune", "H", "ROUGH_TOPAZ_GEM:1", "FLAWED_TOPAZ_GEM:80", "FINE_TOPAZ_GEM:6400"},
            {"Jasper", 4800.0, "Gemstone Fortune", "H", "ROUGH_JASPER_GEM:1", "FLAWED_JASPER_GEM:80", "FINE_JASPER_GEM:6400"},
            {"Aquamarine", 5200.0, "Gemstone Fortune", "M", "ROUGH_AQUAMARINE_GEM:1", "FLAWED_AQUAMARINE_GEM:80", "FINE_AQUAMARINE_GEM:6400"},
            {"Citrine", 5200.0, "Gemstone Fortune", "M", "ROUGH_CITRINE_GEM:1", "FLAWED_CITRINE_GEM:80", "FINE_CITRINE_GEM:6400"},
            {"Peridot", 5200.0, "Gemstone Fortune", "M", "ROUGH_PERIDOT_GEM:1", "FLAWED_PERIDOT_GEM:80", "FINE_PERIDOT_GEM:6400"},
            {"Onyx", 5200.0, "Gemstone Fortune", "M", "ROUGH_ONYX_GEM:1", "FLAWED_ONYX_GEM:80", "FINE_ONYX_GEM:6400"},
    };

    /** Mining speed from the tab list, or -1 if the Stats widget doesn't show it. */
    public static double miningSpeed() { return Tracker.number(Tracker.tab.get("Mining Speed")); }

    public static List<Option> mining() {
        double speed = miningSpeed();
        List<Option> out = new ArrayList<>();
        if (speed <= 0) return out;
        String island = islandTag();
        double mf = Math.max(0, Tracker.number(Tracker.tab.get("Mining Fortune")));
        for (Object[] o : ORES) {
            if (island != null && !((String) o[3]).contains(island)) continue;
            double strength = (double) o[1];
            double extra = Math.max(0, Tracker.number(Tracker.tab.get((String) o[2])));
            // Hypixel: ticks to break = strength * 30 / mining speed, never faster than 4 ticks
            double ticks = Math.max(4, Math.round(strength * 30 / speed));
            double blocksPerHour = 20.0 / ticks * 3600 * Config.get().miningEfficiency;
            double itemsPerHour = blocksPerHour * (1 + (mf + extra) / 100.0);
            Best best = bestForm(o, 4);
            if (best.price <= 0) continue;
            out.add(new Option((String) o[0], itemsPerHour * best.price, best.price, best.name));
        }
        out.sort((a, b) -> Double.compare(b.perHour, a.perHour));
        return out;
    }

    private static String islandTag() {
        String a = Tracker.areaName;
        if (a == null) return null;
        if (a.contains("Hollows")) return "H";
        if (a.contains("Mineshaft")) return "M";
        if (a.contains("Dwarven") || a.contains("Glacite")) return "D";
        return null;
    }

    // ---------------- shared ----------------

    private record Best(double price, String name) {}

    /** Best price per raw item across the forms you could sell it as (raw, enchanted, ...). */
    private static Best bestForm(Object[] row, int firstForm) {
        double bestPrice = 0;
        String bestName = null;
        for (int i = firstForm; i < row.length; i++) {
            String spec = (String) row[i];
            int cut = spec.lastIndexOf(':');
            String id = spec.substring(0, cut);
            double need = Double.parseDouble(spec.substring(cut + 1));
            double each = Prices.priceOfId(id) / need;
            if (each > bestPrice) { bestPrice = each; bestName = Prices.nameOf(id); }
        }
        return new Best(bestPrice, bestName);
    }

    /** One HUD line, e.g. "Best now: Nether Wart ~8.1M/h (you: 6.2M/h)". */
    public static String hudLine(Session s, String type) {
        if (!Config.get().showSuggestion || !Prices.loaded()) return null;
        boolean farming = Tracker.FARMING.equals(type);
        if (!farming && !Tracker.isMiningType(type)) return null;
        if (farming && !Config.get().farmShowSuggestion) return null;
        if (!farming && !Config.get().mineShowSuggestion) return null;
        List<Option> opts = farming ? farming() : mining();
        if (opts.isEmpty()) return farming ? null : "§7Best now: §8needs Mining Speed in the Stats tab widget";
        Option best = opts.get(0);
        String current = s == null ? null : s.mainCrop();
        long now = System.currentTimeMillis();
        String you = s != null && s.durationMs(now) >= 60_000 ? " §8(you: " + Fmt.coins(s.perHour(now)) + "/h)" : "";
        String contest = farming && Contests.running(best.name) ? " §e★ contest" : "";
        if (best.name.equals(current)) return "§7Best now: §a" + best.name + " §2✔ §6~" + Fmt.coins(best.perHour) + "/h" + contest + you;
        return "§7Best now: §a" + best.name + " §6~" + Fmt.coins(best.perHour) + "/h" + contest + you;
    }

    private Suggest() {}
}
