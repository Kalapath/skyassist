package dev.farmprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.screens.Screen;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Market signals (/market): statistics, not guesses.
 *  - Mayor effect: how each item's price changed in past terms of a mayor (vs. the week before the term).
 *  - Event effect: how it changed around past Spooky Festivals, Seasons of Jerry, New Years.
 *  - Trends: 24 h / 7 d change and how unusual today's price is (z-score vs. the last week).
 *  - Backtest: every signal is checked against the past (leave-one-out); only signals that would have been right
 *    most of the time are shown, with their hit rate.
 * Data: Coflnet's public price history + mayor history (cached on disk, refreshed daily).
 */
public final class MarketSignals {
    public record Point(long t, double price) {}
    public record Term(String mayor, long start, long end) {}
    public record Signal(String item, String kind, String trigger, double expectedPct, int hits, int samples, String note) {}

    private static final Path CACHE = Config.DIR.resolve("market");
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(15)).build();
    private static final Map<String, List<Point>> HISTORY = new LinkedHashMap<>();
    private static volatile List<Term> terms = List.of();
    private static volatile List<Signal> signals = List.of();
    private static volatile String status = "not started (open /market)";
    private static volatile boolean running;
    private static volatile long builtAt;

    // ---------------------------------------------------------------- data

    private static String get(String url) throws Exception {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyAssist")
                .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode());
        return r.body();
    }

    /** Cached for a day so Coflnet isn't asked again and again. */
    private static String cached(String key, String url) throws Exception {
        Path f = CACHE.resolve(key.replaceAll("[^A-Za-z0-9_.-]", "_") + ".json");
        if (Files.exists(f) && System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() < 24 * 3_600_000L) return Files.readString(f);
        String body = get(url);
        Files.createDirectories(CACHE);
        Files.writeString(f, body);
        Thread.sleep(1200);                                    // be gentle with the free API
        return body;
    }

    private static long time(JsonElement e) {
        try {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) { long v = e.getAsLong(); return v < 10_000_000_000L ? v * 1000 : v; }
            return Instant.parse(e.getAsString().endsWith("Z") || e.getAsString().contains("+") ? e.getAsString() : e.getAsString() + "Z").toEpochMilli();
        } catch (Exception ex) { return -1; }
    }

    /** Price points from Coflnet's history: uses "buy" (or the average of buy/sell, or "price") and "timestamp". */
    private static List<Point> parseHistory(String body) {
        List<Point> out = new ArrayList<>();
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) return out;
        for (JsonElement el : root.getAsJsonArray()) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            long t = o.has("timestamp") ? time(o.get("timestamp")) : o.has("time") ? time(o.get("time")) : -1;
            double p = o.has("buy") && o.has("sell") ? (o.get("buy").getAsDouble() + o.get("sell").getAsDouble()) / 2
                    : o.has("buy") ? o.get("buy").getAsDouble() : o.has("price") ? o.get("price").getAsDouble() : -1;
            if (t > 0 && p > 0) out.add(new Point(t, p));
        }
        out.sort(Comparator.comparingLong(Point::t));
        return out;
    }

    /** Mayor terms from Coflnet's mayor history (tolerant: finds a name + a start date in each entry). */
    private static List<Term> parseMayors(String body) {
        List<long[]> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        JsonElement root = JsonParser.parseString(body);
        if (!root.isJsonArray()) return List.of();
        for (JsonElement el : root.getAsJsonArray()) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String name = null;
            if (o.has("winner") && o.get("winner").isJsonObject() && o.getAsJsonObject("winner").has("name")) name = o.getAsJsonObject("winner").get("name").getAsString();
            else if (o.has("name")) name = o.get("name").getAsString();
            long start = -1;
            for (String k : new String[]{"start", "startDate", "from", "timestamp", "date"}) if (start < 0 && o.has(k)) start = time(o.get(k));
            if (name != null && start > 0) { names.add(name); starts.add(new long[]{start}); }
        }
        List<Term> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            long end = i + 1 < names.size() ? starts.get(i + 1)[0] : System.currentTimeMillis();
            out.add(new Term(names.get(i), starts.get(i)[0], end));
        }
        out.sort(Comparator.comparingLong(Term::start));
        return out;
    }

    // ---------------------------------------------------------------- statistics

    private static double median(List<Point> pts, long from, long to) {
        List<Double> v = new ArrayList<>();
        for (Point p : pts) if (p.t() >= from && p.t() < to) v.add(p.price());
        if (v.size() < 3) return -1;
        v.sort(Double::compare);
        return v.get(v.size() / 2);
    }

    /** % change of the median in [start, start+len) vs the week before start. */
    private static double change(List<Point> pts, long start, long len) {
        double before = median(pts, start - 7 * 86_400_000L, start), during = median(pts, start, start + len);
        return before > 0 && during > 0 ? (during / before - 1) * 100 : Double.NaN;
    }

    /**
     * From several past occurrences: average % change, and a leave-one-out backtest — for each occurrence, predict its
     * direction from the average of the others, and count how often that was right.
     */
    private static double[] stats(List<Double> changes) {
        int n = changes.size(), hits = 0;
        double sum = 0;
        for (double c : changes) sum += c;
        for (int i = 0; i < n; i++) {
            double others = (sum - changes.get(i)) / Math.max(1, n - 1);
            if (n > 1 && Math.signum(others) == Math.signum(changes.get(i)) && Math.abs(changes.get(i)) > 1) hits++;
        }
        return new double[]{n == 0 ? 0 : sum / n, hits, n};
    }

    // ---------------------------------------------------------------- build

    public static void refresh() {
        if (running) return;
        running = true;
        Thread t = new Thread(() -> {
            try { build(); } catch (Exception e) { status = "§cfailed: " + e.getMessage(); }
            running = false;
        }, "skyassist-market");
        t.setDaemon(true);
        t.start();
    }

    private static void build() throws Exception {
        Config c = Config.get();
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(Math.max(30, c.marketHistoryDays)));
        status = "loading mayor history…";
        try {
            terms = parseMayors(cached("mayors", "https://sky.coflnet.com/api/mayor?from=" + from.toString().substring(0, 10) + "&to=" + now.toString().substring(0, 10)));
        } catch (Exception e) { terms = List.of(); }

        // the most traded Bazaar items (enough volume to mean something)
        List<Map.Entry<String, double[]>> items = new ArrayList<>(Prices.BOOK.entrySet());
        items.removeIf(e -> e.getValue()[1] <= 0 || Math.min(e.getValue()[2], e.getValue()[3]) < c.bzMinVolume);
        items.sort((a, b) -> Double.compare(Math.min(b.getValue()[2], b.getValue()[3]) * b.getValue()[1], Math.min(a.getValue()[2], a.getValue()[3]) * a.getValue()[1]));
        int n = Math.min(c.marketItems, items.size());
        for (int i = 0; i < n; i++) {
            String id = items.get(i).getKey();
            status = "loading price history " + (i + 1) + "/" + n + " (" + Prices.nameOf(id) + ")…";
            try {
                List<Point> pts = parseHistory(cached("h_" + id, "https://sky.coflnet.com/api/bazaar/" + id + "/history?start=" + from + "&end=" + now));
                if (pts.size() > 20) synchronized (HISTORY) { HISTORY.put(id, pts); }
            } catch (Exception ignored) {}
        }
        status = "calculating…";
        signals = compute();
        builtAt = System.currentTimeMillis();
        status = "§aready §7(" + HISTORY.size() + " items, " + terms.size() + " mayor terms)";
    }

    /** Real-time start of the given SkyBlock event in every SkyBlock year covered by the data. */
    private static List<Long> eventStarts(int month, int day, long from, long to) {
        long epoch = 1560275700L * 1000, year = 446_400_000L, monthMs = 37_200_000L, dayMs = 1_200_000L;
        List<Long> out = new ArrayList<>();
        for (long y = (from - epoch) / year; epoch + y * year < to; y++) {
            long s = epoch + y * year + (month - 1) * monthMs + (day - 1) * dayMs;
            if (s >= from && s < to) out.add(s);
        }
        return out;
    }

    private static List<Signal> compute() {
        Config c = Config.get();
        List<Signal> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        String next = Election.leader;
        Object[][] events = {{"Spooky Festival", 8, 29, 3}, {"Season of Jerry", 12, 24, 3}, {"New Year", 12, 29, 3}};
        Map<String, List<Point>> hist;
        synchronized (HISTORY) { hist = new LinkedHashMap<>(HISTORY); }
        for (var e : hist.entrySet()) {
            String id = e.getKey(), name = Prices.nameOf(id);
            List<Point> pts = e.getValue();
            long first = pts.get(0).t();
            // --- mayor effect for the likely next mayor (and the current one, for "what happens when they leave")
            for (String mayor : new String[]{next, Election.mayor}) {
                if (mayor == null) continue;
                List<Double> ch = new ArrayList<>();
                for (Term t : terms) {
                    if (!t.mayor().equalsIgnoreCase(mayor) || t.start() < first + 7 * 86_400_000L || t.end() > now) continue;
                    double d = change(pts, t.start(), Math.min(t.end() - t.start(), 3 * 86_400_000L));   // first 3 days of the term
                    if (!Double.isNaN(d)) ch.add(d);
                }
                double[] s = stats(ch);
                boolean isNext = mayor.equals(next) && !mayor.equalsIgnoreCase(Election.mayor);
                if (s[2] >= c.marketMinSamples && Math.abs(s[0]) >= c.marketMinMove && s[1] / s[2] >= c.marketMinHitRate / 100.0)
                    out.add(new Signal(name, "Mayor", (isNext ? "if " + mayor + " wins" : mayor + " term start"), s[0], (int) s[1], (int) s[2],
                            isNext ? "leading with " + String.format(Locale.US, "%.0f%%", Election.leaderShare) : "current mayor's past terms"));
            }
            // --- calendar events
            for (Object[] ev : events) {
                List<Double> ch = new ArrayList<>();
                for (long s0 : eventStarts((int) ev[1], (int) ev[2], first + 7 * 86_400_000L, now))
                    { double d = change(pts, s0, (int) ev[3] * 1_200_000L + 3_600_000L); if (!Double.isNaN(d)) ch.add(d); }
                double[] s = stats(ch);
                if (s[2] >= c.marketMinSamples && Math.abs(s[0]) >= c.marketMinMove && s[1] / s[2] >= c.marketMinHitRate / 100.0)
                    out.add(new Signal(name, "Event", (String) ev[0], s[0], (int) s[1], (int) s[2], "around past " + ev[0] + "s"));
            }
            // --- trend: last 24 h, last 7 d, and how unusual today is (z-score vs the past week)
            double nowP = pts.get(pts.size() - 1).price();
            double d1 = median(pts, now - 86_400_000L * 2, now - 86_400_000L), d7 = median(pts, now - 8 * 86_400_000L, now - 7 * 86_400_000L);
            List<Double> wk = new ArrayList<>();
            for (Point p : pts) if (p.t() > now - 7 * 86_400_000L) wk.add(p.price());
            if (wk.size() > 10) {
                double mean = wk.stream().mapToDouble(Double::doubleValue).average().orElse(nowP);
                double sd = Math.sqrt(wk.stream().mapToDouble(v -> (v - mean) * (v - mean)).average().orElse(0));
                double z = sd > 0 ? (nowP - mean) / sd : 0;
                if (Math.abs(z) >= 2)
                    out.add(new Signal(name, "Trend", z < 0 ? "unusually LOW" : "unusually HIGH", (mean / nowP - 1) * 100, 0, 0,
                            String.format(Locale.US, "z=%.1f, 24h %s, 7d %s", z, pct(d1 > 0 ? (nowP / d1 - 1) * 100 : 0), pct(d7 > 0 ? (nowP / d7 - 1) * 100 : 0))));
            }
        }
        out.sort((a, b) -> Double.compare(Math.abs(b.expectedPct()), Math.abs(a.expectedPct())));
        return out;
    }

    private static String pct(double v) { return String.format(Locale.US, "%+.1f%%", v); }

    // ---------------------------------------------------------------- menu

    public static Screen screen(Screen parent) {
        if (signals.isEmpty() && !running && System.currentTimeMillis() - builtAt > 60_000) refresh();
        final MenuScreen[] ref = new MenuScreen[1];
        ref[0] = new MenuScreen("Market signals", List.of(
                new Tab("Mayor", () -> page("Mayor", ref)),
                new Tab("Events", () -> page("Event", ref)),
                new Tab("Trends", () -> page("Trend", ref)),
                new Tab("How this works", MarketSignals::help)
        ), 0, parent).searchable();
        return ref[0];
    }

    private static Page page(String kind, MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        for (Signal s : signals) {
            if (!s.kind().equals(kind)) continue;
            String exp = (s.expectedPct() >= 0 ? "§a+" : "§c") + String.format(Locale.US, "%.1f%%", s.expectedPct());
            String hit = s.samples() > 0 ? "§f" + s.hits() + "/" + s.samples() + " §8right" : "";
            String tip = "§f" + s.item() + "\n§7" + s.trigger() + ": " + exp + "\n§7" + s.note()
                    + (s.samples() > 0 ? "\n§7Backtest: the average of the other cases called the direction right " + s.hits() + " of " + s.samples() + " times." : "")
                    + "\n§8Past patterns, not a promise: updates and manipulation can break them.";
            rows.add(new Row(new String[]{"§f" + s.item(), "§7" + s.trigger(), exp, hit}, tip,
                    List.of(new Action("§eBazaar", "Open " + s.item() + " in the Bazaar.", () -> MenuScreen.runCommand("bz " + s.item())))));
        }
        List<Action> top = List.of(new Action("Refresh data", "Download fresh history (cached for a day otherwise).", () -> {
            try { java.nio.file.Files.walk(CACHE).filter(java.nio.file.Files::isRegularFile).forEach(f -> f.toFile().delete()); } catch (Exception ignored) {}
            refresh(); ref[0].refresh();
        }), new Action("Recalculate", "Recompute from the downloaded data (e.g. after the election leader changed).", () -> { signals = compute(); ref[0].refresh(); }));
        List<String> footer = new ArrayList<>();
        footer.add("§7Status: " + status + (running ? " §8(keeps loading in the background — reopen in a moment)" : ""));
        if (kind.equals("Mayor")) footer.add("§7Current mayor: §f" + (Election.mayor != null ? Election.mayor : "?") + "§7, election leader: §f"
                + (Election.leader != null ? Election.leader + String.format(Locale.US, " (%.0f%%)", Election.leaderShare) : "no election running"));
        footer.add("§8Only signals that held up in the backtest are shown. These are statistics from the past, not guarantees.");
        return new Page(new String[]{"Item", "When", "Expected", "Backtest"}, new int[]{150, 120, 60, 70}, rows, top, footer);
    }

    private static Page help() {
        String[][] lines = {
                {"§fMayor", "For the election leader (and the current mayor): the item's median price in the first 3 days of each past term vs. the week before. Average = Expected."},
                {"§fEvents", "Same idea around past Spooky Festivals, Seasons of Jerry and New Years (from the SkyBlock calendar)."},
                {"§fTrends", "Today's price vs. the last 7 days. 'Unusually LOW' (z ≤ −2) often bounces back; 'HIGH' often drops. Expected = back to the weekly average."},
                {"§fBacktest", "For every past case, the average of the OTHER cases predicts its direction. 4/5 = right 4 times out of 5. Below your minimum hit rate, a signal isn't shown."},
                {"§fData", "Coflnet's public price and mayor history, cached for a day in config/skyassist/market."},
                {"§cLimits", "New updates, nerfs, dupes and manipulators move prices in ways no history can see. Use it as a hint, not a promise."},
        };
        List<Row> rows = new ArrayList<>();
        for (String[] l : lines) rows.add(new Row(l[0], "§7" + l[1]));
        return new Page(new String[]{"", ""}, new int[]{90, 360}, rows, List.of(), List.of("§8Settings → Bazaar → Market signals: items analysed, history length, minimum samples / move / hit rate."));
    }

    public static String status() { return status; }

    private MarketSignals() {}
}
