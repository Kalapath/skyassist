package dev.farmprofit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/** Current mayor and perks from Hypixel's public election data. */
public final class Election {
    private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/election";
    private static final String NAME = "Mayor & election";
    public static volatile String mayor;
    /** The running election (Hypixel's "current" block): leading candidate and their vote share, if an election is on. */
    public static volatile String leader;
    public static volatile double leaderShare;
    /** The election year of the current mayor (Hypixel's numbering), -1 if unknown. */
    public static volatile int mayorYear = -1;
    public static volatile List<String> perks = new ArrayList<>();
    private static volatile long lastFetch;

    static { Health.onRetry(NAME, () -> lastFetch = 0); }

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastFetch < Health.delay(NAME, 30 * 60_000L, 2 * 60_000L)) return;
        lastFetch = now;
        Http.get(NAME, URL, 20, Election::parse);
    }

    private static String parse(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("mayor") || !root.get("mayor").isJsonObject()) throw new IllegalStateException("no mayor in the response");
        JsonObject m = root.getAsJsonObject("mayor");
        List<String> p = new ArrayList<>();
        if (m.has("perks") && m.get("perks").isJsonArray())
            for (var el : m.getAsJsonArray("perks")) if (el.isJsonObject() && el.getAsJsonObject().has("name")) p.add(el.getAsJsonObject().get("name").getAsString());
        String name = m.get("name").getAsString();
        int year = -1;
        if (m.has("election") && m.get("election").isJsonObject() && m.getAsJsonObject("election").has("year"))
            year = m.getAsJsonObject("election").get("year").getAsInt();
        String lead = null;
        double share = 0;
        if (root.has("current") && root.get("current").isJsonObject() && root.getAsJsonObject("current").has("candidates")) {
            long total = 0, best = -1;
            for (var el : root.getAsJsonObject("current").getAsJsonArray("candidates")) {
                JsonObject c = el.getAsJsonObject();
                long v = c.has("votes") ? c.get("votes").getAsLong() : 0;
                total += v;
                if (v > best) { best = v; lead = c.get("name").getAsString(); }
            }
            share = total > 0 ? 100.0 * best / total : 0;
        }
        // set at the end, so the HUD never sees a half-read answer
        perks = p;
        mayor = name;
        if (year > 0) mayorYear = year;
        leaderShare = share;
        leader = lead;
        return name + (lead != null ? ", election leader " + lead : ", no election running");
    }

    /** Which activity the current mayor helps, e.g. Finnegan -> Farming. */
    public static String boosts() {
        String m = mayor;
        if (m == null) return null;
        return switch (m) {
            case "Finnegan" -> Tracker.FARMING;
            case "Cole" -> Tracker.MINING;
            case "Marina" -> Tracker.FISHING;
            case "Aatrox" -> Tracker.COMBAT;
            case "Paul" -> Tracker.DUNGEONS;
            case "Diana" -> Tracker.DIANA;
            default -> null;
        };
    }

    /** "Mayor: Finnegan (Farming Simulator, ...)" when the mayor helps this activity. */
    public static String hudLine(String type) {
        if (!Config.get().showMayor || mayor == null) return null;
        String b = boosts();
        if (b == null || !(b.equals(type) || (Tracker.isMiningType(type) && b.equals(Tracker.MINING)))) return null;
        return "§7Mayor: §d" + mayor + " §8(" + String.join(", ", perks) + ")";
    }

    private Election() {}
}
