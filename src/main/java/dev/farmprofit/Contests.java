package dev.farmprofit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Upcoming Jacob's farming contests. Hypixel doesn't publish these, so they come from a community
 * source (by default the Elite Farmers API, which collects SkyHanni users' shared calendars).
 */
public final class Contests {
    private static final long CONTEST_MS = 20 * 60_000;
    private static final String NAME = "Jacob's contests";
    /** start time (ms) -> crops */
    private static final ConcurrentSkipListMap<Long, List<String>> CONTESTS = new ConcurrentSkipListMap<>();
    private static volatile long lastFetch;

    static { Health.onRetry(NAME, () -> lastFetch = 0); }

    public static boolean loaded() { return !CONTESTS.isEmpty(); }

    private static long remindedFor;

    public static void tick() {
        remind();
        String url = Config.get().jacobContestsUrl;
        if (url == null || url.isBlank()) { Health.off(NAME, "no data address in the settings"); return; }
        long now = System.currentTimeMillis();
        boolean stale = CONTESTS.isEmpty() || CONTESTS.ceilingKey(now - CONTEST_MS) == null;
        long wait = stale ? Health.delay(NAME, 10 * 60_000L, 10 * 60_000L) : Health.delay(NAME, 60 * 60_000L, 10 * 60_000L);
        if (now - lastFetch < wait) return;
        lastFetch = now;
        Http.get(NAME, url, 20, Contests::parse);
    }

    /** Accepts {"contests": {"<time>": ["Wheat", ...]}} or a plain {"<time>": [...]} map; seconds or ms. */
    private static String parse(String body) {
        JsonElement root = JsonParser.parseString(body);
        JsonObject obj = root.getAsJsonObject();
        if (obj.has("contests") && obj.get("contests").isJsonObject()) obj = obj.getAsJsonObject("contests");
        Map<Long, List<String>> found = new TreeMap<>();
        for (var e : obj.entrySet()) {
            try {
                long t;
                try { t = Long.parseLong(e.getKey()); } catch (NumberFormatException ex) { continue; }
                if (t < 100_000_000_000L) t *= 1000;
                if (!e.getValue().isJsonArray()) continue;
                JsonArray arr = e.getValue().getAsJsonArray();
                List<String> crops = new java.util.ArrayList<>();
                for (JsonElement c : arr) crops.add(c.getAsString());
                if (!crops.isEmpty()) found.put(t, crops);
            } catch (Exception skipThisOne) { /* one odd entry shouldn't lose the rest */ }
        }
        if (found.isEmpty()) throw new IllegalStateException("no contests found in the response");
        long now = System.currentTimeMillis();
        long upcoming = found.keySet().stream().filter(t -> t + CONTEST_MS > now).count();
        CONTESTS.clear();
        CONTESTS.putAll(found);
        if (upcoming == 0) throw new IllegalStateException("the source only has past contests (" + found.size() + "), nothing upcoming yet");
        return Health.count((int) upcoming, "upcoming contests");
    }

    /** "§eWheat, Carrot, Cocoa Beans §7in 12m" or "§aNOW: ... (8m left)", or null. */
    public static String hudLine() {
        if (!Config.get().showContests) return null;
        long now = System.currentTimeMillis();
        Long start = CONTESTS.ceilingKey(now - CONTEST_MS);
        if (start == null) return null;
        List<String> crops = CONTESTS.get(start);
        String list = String.join(", ", crops);
        if (start <= now) return "§7Contest: §aNOW §e" + list + " §8(" + Fmt.clock(start + CONTEST_MS - now) + " left)";
        return "§7Next contest: §e" + list + " §7in " + Fmt.clock(start - now);
    }

    private static void remind() {
        if (!Config.get().contestAlert) return;
        long now = System.currentTimeMillis();
        Long next = CONTESTS.ceilingKey(now);
        if (next == null || next == remindedFor || next - now > 60_000) return;
        remindedFor = next;
        Chat.ping();
        Tracker.say("§6[Farming] §eJacob's contest in " + Fmt.clock(next - now) + ": §f" + String.join(", ", CONTESTS.get(next)));
    }

    /** While you're in a contest: the sidebar's contest lines (crop, time left, collected, medal / bracket). */
    public static java.util.List<String> liveLines() {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!Config.get().showContestStanding) return out;
        java.util.List<String> side = Tracker.sidebarLines;
        for (int i = 0; i < side.size(); i++) {
            if (!side.get(i).contains("Jacob's Contest")) continue;
            for (int k = i + 1; k < Math.min(side.size(), i + 5); k++) {
                String l = side.get(k).trim();
                if (l.isEmpty() || l.startsWith("www.") || l.contains("hypixel.net")) break;
                String col = l.matches("(?i).*(DIAMOND|PLATINUM).*") ? "§b" : l.matches("(?i).*GOLD.*") ? "§6" : l.matches("(?i).*SILVER.*") ? "§f"
                        : l.matches("(?i).*BRONZE.*") ? "§c" : "§7";
                out.add(" " + col + l);
            }
            if (!out.isEmpty()) out.add(0, "§e§lJacob's Contest");
            break;
        }
        return out;
    }

    /** Is this crop in a contest that's running now? */
    public static boolean running(String crop) {
        long now = System.currentTimeMillis();
        Long start = CONTESTS.floorKey(now);
        return start != null && now < start + CONTEST_MS && CONTESTS.get(start).stream().anyMatch(c -> c.equalsIgnoreCase(crop));
    }

    private Contests() {}
}
