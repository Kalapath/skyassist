package dev.farmprofit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Current mayor and perks from Hypixel's public election data. */
public final class Election {
    private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/election";
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    public static volatile String mayor;
    /** The running election (Hypixel's "current" block): leading candidate and their vote share, if an election is on. */
    public static volatile String leader;
    public static volatile double leaderShare;
    public static volatile List<String> perks = new ArrayList<>();
    private static volatile long lastFetch;

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastFetch < 30 * 60_000) return;
        lastFetch = now;
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(URL)).header("User-Agent", "SkyAssist")
                        .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> { if (r.statusCode() == 200) parse(r.body()); })
                .exceptionally(e -> null);
    }

    private static void parse(String body) {
        try {
            JsonObject m = JsonParser.parseString(body).getAsJsonObject().getAsJsonObject("mayor");
            List<String> p = new ArrayList<>();
            if (m.has("perks")) for (var el : m.getAsJsonArray("perks")) p.add(el.getAsJsonObject().get("name").getAsString());
            perks = p;
            mayor = m.get("name").getAsString();
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            leader = null;
            if (root.has("current") && root.get("current").isJsonObject() && root.getAsJsonObject("current").has("candidates")) {
                long total = 0, best = -1;
                for (var el : root.getAsJsonObject("current").getAsJsonArray("candidates")) {
                    JsonObject c = el.getAsJsonObject();
                    long v = c.has("votes") ? c.get("votes").getAsLong() : 0;
                    total += v;
                    if (v > best) { best = v; leader = c.get("name").getAsString(); }
                }
                leaderShare = total > 0 ? 100.0 * best / total : 0;
            }
        } catch (Exception ignored) {}
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
