package dev.farmprofit;

import java.util.LinkedHashMap;
import java.util.Map;

/** One farming or mining session. Public fields so it saves straight to JSON. */
public final class Session {
    /** "Farming", "Dwarven Mines", "Crystal Hollows" or "Glacite Mineshaft" */
    public String type = Tracker.FARMING;
    public long start;
    public long lastActivity;
    public long end;                                   // 0 while running
    public Map<String, Integer> breaks = new LinkedHashMap<>();
    public Map<String, Long> items = new LinkedHashMap<>();
    public Map<String, Long> powder = new LinkedHashMap<>();
    /** Items used up (potions, arrows, visitor requests...). Subtracted from profit. */
    public Map<String, Long> spent = new LinkedHashMap<>();
    /** Items announced in chat (RARE DROP!, PET DROP!, RARE CROP! ...) */
    public Map<String, Integer> rareDrops = new LinkedHashMap<>();
    /** Pest kills by pest type */
    public Map<String, Integer> pests = new LinkedHashMap<>();
    public int pristine;
    public int treeGifts;
    /** Crystal Hollows treasure chests opened, and what was in them (shown, not added to profit: the items are counted when they arrive). */
    public int chestsOpened;
    public Map<String, Integer> chestLoot = new LinkedHashMap<>();
    public int trophyFish;
    public int slayerQuests;
    /** Kills by mob name (combat). */
    public Map<String, Integer> kills = new LinkedHashMap<>();
    /** The mob being farmed if this is a grind (e.g. "Zealot"), else null. */
    public String grind;
    /** e.g. Special Zealots seen. */
    public int specials;
    /** Kill count when each tracked drop last dropped (for "since last drop"). */
    public Map<String, Integer> killsAtDrop = new LinkedHashMap<>();
    public int runs;
    public int visitors;
    public long copper;
    public int burrows;
    /** Trophy fish by name, e.g. "Gusher (BRONZE)" */
    public Map<String, Integer> trophies = new LinkedHashMap<>();
    public String lastScore;
    public String floor;
    /** Slayer quest costs etc. (subtracted from profit) */
    public double costs;
    /** Where you were fishing */
    public String location;
    /** Your own note, e.g. "new hoe + Finnegan" (/profit note). */
    public String note;
    /** Coins found directly (fishing treasure etc.) */
    public double coins;
    /** Coins that went straight into your purse while active (Bountiful, mob coins, Midas...). */
    public double purseCoins;
    /** Time actually spent active; -1 for sessions saved by older versions. */
    public long activeMs = -1;
    /** Attribute shards sent to the Hunting Box */
    public Map<String, Integer> shards = new LinkedHashMap<>();
    public int pestActions;
    public String fortune;
    // Filled in when the session ends (prices frozen at that moment)
    public double profit;
    public double profitPerHour;
    public String mainCrop;                            // main crop / main ore

    public Session() {}

    public Session(String type, long now) {
        this.type = type;
        this.start = now;
        this.lastActivity = now;
        this.activeMs = 0;
    }

    /** Called on every action; gaps longer than the pause time don't count as farming time. */
    public void touch(long now) {
        long gap = now - lastActivity;
        if (gap > 0 && gap <= pauseMs()) activeMs += gap;
        lastActivity = now;
    }

    public boolean paused(long now) { return now - lastActivity > pauseMs(); }

    /** How long a gap still counts as time spent. Burrows are further apart than crops, so Diana allows a longer one. */
    private long pauseMs() {
        long ms = Config.get().pauseSeconds * 1000L;
        return isDiana() ? Math.max(ms, 90_000L) : ms;
    }

    static boolean ignored(String name) { return Config.get().ignoredItems.contains(name); }

    public boolean isMining() { return Tracker.isMiningType(type); }
    public boolean isForaging() { return Tracker.FORAGING.equals(type); }
    public boolean isFishing() { return Tracker.FISHING.equals(type); }
    public boolean isCombat() { return Tracker.COMBAT.equals(type); }
    public boolean isFarming() { return type == null || Tracker.FARMING.equals(type); }
    public boolean isDungeons() { return Tracker.DUNGEONS.equals(type); }
    public boolean isKuudra() { return Tracker.KUUDRA.equals(type); }
    public boolean isDiana() { return Tracker.DIANA.equals(type); }

    public boolean isEmpty() {
        return totalBreaks() == 0 && totalKills() == 0 && purseCoins == 0 && runs == 0 && burrows == 0 && visitors == 0 && items.isEmpty() && coins == 0 && (rareDrops == null || rareDrops.isEmpty())
                && (shards == null || shards.isEmpty());
    }

    public void addBreak(String what) { breaks.merge(what, 1, Integer::sum); }

    /** For grinds: remember at which kill a tracked drop came. */
    public void noteDrop(String name) {
        if (grind == null) return;
        Combat.Grind g = Combat.byName(grind);
        if (g != null && g.drops().contains(name)) killsAtDrop.put(name, totalKills());
    }

    public void addItem(String name, long delta) {
        if (delta > 0) noteDrop(name);
        long v = items.merge(name, delta, Long::sum);
        if (v == 0) items.remove(name);
    }

    public void addSpent(String name, long amount) { spent.merge(name, amount, Long::sum); }

    public double spentValue() {
        double t = 0;
        if (spent != null) for (var e : spent.entrySet()) if (!ignored(e.getKey())) t += e.getValue() * Prices.price(e.getKey());
        return t;
    }

    public void addPowder(String kind, long amount) { powder.merge(kind, amount, Long::sum); }

    public long durationMs(long now) {
        if (activeMs < 0) return Math.max(0, (end > 0 ? end : now) - start);   // old history entries
        if (end > 0 || paused(now)) return activeMs;
        return activeMs + Math.max(0, now - lastActivity);
    }

    public double hours(long now) { return durationMs(now) / 3_600_000.0; }

    public int totalBreaks() {
        int t = 0;
        for (int v : breaks.values()) t += v;
        return t;
    }

    public String mainCrop() {
        if (mainCrop != null) return mainCrop;
        String best = null;
        int max = -1;
        for (var e : breaks.entrySet()) if (e.getValue() > max) { max = e.getValue(); best = e.getKey(); }
        if (best == null && location != null) return location;
        if (best == null && isDungeons()) return floor != null ? floor : "Catacombs";
        if (best == null && isKuudra()) return floor != null ? floor : "Kuudra";
        if (best == null && isDiana()) return "Mythological Ritual";
        if (best == null) return isCombat() ? "No bosses yet" : "Nothing yet";
        return best;
    }

    public double value() {
        double total = 0;
        for (var e : items.entrySet()) if (!ignored(e.getKey())) total += e.getValue() * Prices.price(e.getKey());
        total += coins;
        total += purseCoins;
        total += copper * Config.get().copperValue;
        if (rareDrops != null) for (var e : rareDrops.entrySet()) total += rareValue(e.getKey(), e.getValue());
        total += shardValue();
        total -= costs;
        total -= spentValue();
        return total;
    }

    /** True if this rare drop was already counted through the inventory or sacks. */
    public boolean rareAlreadyCounted(String name) { return items.containsKey(name); }

    /** Value a rare drop adds to profit (0 if it was already counted as a normal item). */
    public double rareValue(String name, int count) {
        return rareAlreadyCounted(name) || ignored(name) ? 0 : count * Prices.price(name);
    }

    public double shardValue() {
        double total = 0;
        if (shards != null) for (var e : shards.entrySet()) if (!ignored(e.getKey())) total += e.getValue() * Prices.price(e.getKey());
        return total;
    }

    public int totalShards() {
        int t = 0;
        if (shards != null) for (int v : shards.values()) t += v;
        return t;
    }

    public int totalKills() {
        int t = 0;
        if (kills != null) for (int v : kills.values()) t += v;
        return t;
    }

    /** How many of a drop this session got (counted as an item or a rare drop). */
    public long dropCount(String name) {
        long n = items.getOrDefault(name, 0L);
        if (rareDrops != null) n = Math.max(n, rareDrops.getOrDefault(name, 0));
        return Math.max(0, n);
    }

    public int totalPests() {
        int t = 0;
        if (pests != null) for (int v : pests.values()) t += v;
        return t;
    }

    public double perHour(long now) {
        double h = hours(now);
        return h < 1.0 / 60 ? 0 : value() / h;
    }

    public double breaksPerSecond(long now) {
        double sec = durationMs(now) / 1000.0;
        return sec < 1 ? 0 : totalBreaks() / sec;
    }

    /** Freeze values and close the session. Duration excludes the idle time at the end. */
    public void finish() {
        end = lastActivity;
        mainCrop = mainCrop();
        profit = value();
        profitPerHour = perHour(end);
    }
}
