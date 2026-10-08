package dev.farmprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Current Dwarven Mines / Crystal Hollows events from anywhere, like SkyHanni's Mining Event Tracker:
 * read from Soopy's shared mining-event service (players' mods report the events they see in their lobbies).
 * If it's down, the Timers panel falls back to your own tab list.
 */
public final class MiningEvents {
    public record Event(String island, String name, long endsAt) {}

    private static volatile List<Event> events = List.of();
    private static volatile long fetchedAt, lastTry;
    public static volatile boolean ok;

    public static void tick() {
        Config c = Config.get();
        if (!c.timersPanel || !c.timerMiningEvent || c.miningEventsUrl == null || c.miningEventsUrl.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now - lastTry < 60_000) return;                       // once a minute
        lastTry = now;
        HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(URI.create(c.miningEventsUrl))
                        .header("User-Agent", "SkyAssist").timeout(Duration.ofSeconds(15)).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> {
                    if (r.statusCode() != 200) { ok = false; return; }
                    List<Event> found = new ArrayList<>();
                    collect(JsonParser.parseString(r.body()), null, found);
                    events = found;
                    fetchedAt = System.currentTimeMillis();
                    ok = true;
                })
                .exceptionally(e -> { ok = false; return null; });
    }

    /** Finds every {"event": ..., "ends_at": ...} entry, remembering which island list it was under. */
    private static void collect(JsonElement el, String island, List<Event> out) {
        if (el == null) return;
        if (el.isJsonArray()) { for (JsonElement e : el.getAsJsonArray()) collect(e, island, out); return; }
        if (!el.isJsonObject()) return;
        JsonObject o = el.getAsJsonObject();
        if (o.has("event") && o.has("ends_at")) {
            try { out.add(new Event(island, o.get("event").getAsString(), o.get("ends_at").getAsLong())); } catch (Exception ignored) {}
            return;
        }
        for (var e : o.entrySet()) {
            String k = e.getKey().toUpperCase(Locale.ROOT);
            String next = k.contains("DWARVEN") ? "Dwarven Mines" : k.contains("CRYSTAL") ? "Crystal Hollows" : k.contains("MINESHAFT") ? "Mineshaft" : island;
            collect(e.getValue(), next, out);
        }
    }

    private static String pretty(String code) {
        return switch (code.toUpperCase(Locale.ROOT)) {
            case "DOUBLE_POWDER", "2X_POWDER" -> "2x Powder";
            case "GONE_WITH_THE_WIND" -> "Gone with the Wind";
            case "BETTER_TOGETHER" -> "Better Together";
            case "GOBLIN_RAID" -> "Goblin Raid";
            case "RAFFLE" -> "Raffle";
            case "MITHRIL_GOURMAND" -> "Mithril Gourmand";
            default -> {
                StringBuilder b = new StringBuilder();
                for (String w : code.toLowerCase(Locale.ROOT).split("_")) if (!w.isEmpty()) b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
                yield b.toString().trim();
            }
        };
    }

    /** Lines for the Timers panel, or empty if the service gave nothing usable. */
    public static List<String> lines() {
        List<String> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (!ok || now - fetchedAt > 5 * 60_000) return out;
        for (String island : new String[]{"Dwarven Mines", "Crystal Hollows"}) {
            StringBuilder sb = new StringBuilder();
            for (Event e : events) {
                if (!island.equals(e.island()) || e.endsAt() < now) continue;
                if (sb.length() > 0) sb.append("§7, ");
                String n = pretty(e.name());
                sb.append(n.equals("2x Powder") ? "§b§l" : "§f").append(n).append(" §8").append(Fmt.clock(e.endsAt() - now));
            }
            out.add(" §b" + (island.startsWith("Dwarven") ? "Dwarven" : "Hollows") + ": " + (sb.length() == 0 ? "§8no event right now" : sb));
        }
        return out;
    }

    private MiningEvents() {}
}
