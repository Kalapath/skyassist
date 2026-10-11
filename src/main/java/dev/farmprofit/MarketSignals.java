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
    private static volatile int tried, loaded;
    private static volatile String lastError;
    private static final String MAYORS = "Market: mayor history", PRICES = "Market: price history";

    static {
        Health.onRetry(MAYORS, MarketSignals::refresh);
        Health.onRetry(PRICES, MarketSignals::refresh);
    }

    /** The reason for the Status page: our own messages as they are, network errors in plain words. */
    private static String why(Throwable e) {
        return e instanceof IllegalStateException && e.getMessage() != null ? e.getMessage() : Http.reason(e);
    }

    // ---------------------------------------------------------------- data

    private static String get(String url) throws Exception {
        for (int attempt = 0; ; attempt++) {
            HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyAssist")
                    .timeout(Duration.ofSeconds(30)).GET().build(), HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 429 && attempt < 2) {                 // rate limited: wait as asked, then retry
                long wait = r.headers().firstValue("Retry-After").map(v -> { try { return Long.parseLong(v.trim()); } catch (Exception e) { return 10L; } }).orElse(10L);
                Thread.sleep(Math.min(60, Math.max(2, wait)) * 1000);
                continue;
            }
            if (r.statusCode() != 200) throw new IllegalStateException("HTTP " + r.statusCode() + " from " + url.replaceAll("\\?.*", ""));
            return r.body();
        }
    }

    /** Cached for a day so Coflnet isn't asked again and again. Only usable answers are kept (a broken one is asked for again). */
    private static String cached(String key, String url, java.util.function.Predicate<String> usable) throws Exception {
        Path f = CACHE.resolve(key.replaceAll("[^A-Za-z0-9_.-]", "_") + ".json");
        if (Files.exists(f) && System.currentTimeMillis() - Files.getLastModifiedTime(f).toMillis() < 24 * 3_600_000L) {
            String saved = Files.readString(f);
            if (isUsable(usable, saved)) return saved;
        }
        String body = get(url);
        if (isUsable(usable, body)) {
            Files.createDirectories(CACHE);
            Files.writeString(f, body);
        }
        Thread.sleep(1200);                                    // be gentle with the free API
        return body;
    }

    private static boolean isUsable(java.util.function.Predicate<String> usable, String body) {
        try { return usable.test(body); } catch (Exception e) { return false; }
    }

    private static long time(JsonElement e) {
        try {
            if (e.isJsonPrimitive() && e.getAsJsonPrimitive().isNumber()) { long v = e.getAsLong(); return v < 10_000_000_000L ? v * 1000 : v; }
            return Instant.parse(e.getAsString().endsWith("Z") || e.getAsString().contains("+") ? e.getAsString() : e.getAsString() + "Z").toEpochMilli();
        } catch (Exception ex) { return -1; }
    }

    /** The list inside a response: the response itself, or its first array field ("data", "history", ...). */
    private static com.google.gson.JsonArray arrayIn(JsonElement root) {
        if (root.isJsonArray()) return root.getAsJsonArray();
        if (root.isJsonObject()) for (var e : root.getAsJsonObject().entrySet()) if (e.getValue().isJsonArray()) return e.getValue().getAsJsonArray();
        return new com.google.gson.JsonArray();
    }

    private static double num(JsonObject o, String... keys) {
        for (String k : keys) {
            if (!o.has(k) || o.get(k).isJsonNull()) continue;
            try { double v = o.get(k).getAsDouble(); if (v > 0) return v; } catch (Exception ignored) {}
        }
        return -1;
    }

    /** Price points from a history response, whatever the field names (buy/sell, maxBuy/minSell, price, avg...). */
    private static List<Point> parseHistory(String body) {
        List<Point> out = new ArrayList<>();
        for (JsonElement el : arrayIn(JsonParser.parseString(body))) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            long t = -1;
            for (String k : new String[]{"timestamp", "time", "date", "t"}) if (t < 0 && o.has(k)) t = time(o.get(k));
            double buy = num(o, "buy", "buyPrice", "maxBuy", "minBuy"), sell = num(o, "sell", "sellPrice", "minSell", "maxSell");
            double p = buy > 0 && sell > 0 ? (buy + sell) / 2 : buy > 0 ? buy : sell > 0 ? sell : num(o, "price", "avg", "average", "median");
            if (t > 0 && p > 0) out.add(new Point(t, p));
        }
        out.sort(Comparator.comparingLong(Point::t));
        return out;
    }

    /** Real-time start of the mayor term that began in SkyBlock year y (Late Spring 27). */
    private static long termStart(long y) {
        long epoch = 1560275700L * 1000, year = 446_400_000L, monthMs = 37_200_000L, dayMs = 1_200_000L;
        return epoch + (y - 1) * year + 2 * monthMs + 26 * dayMs;
    }

    /** How the "year" in mayor data maps to the term start year: worked out from the current mayor when known. */
    private static long yearOffset() {
        if (Election.mayorYear > 0) {
            long now = System.currentTimeMillis();
            for (long off = -1; off <= 2; off++) {
                long s = termStart(Election.mayorYear + off);
                if (now >= s && now < s + 446_400_000L) return off;
            }
        }
        return 1;      // an election held in year Y gives a mayor from Late Spring of Y+1
    }

    /** Mayor terms from Coflnet's mayor history (tolerant: finds a name + a start date in each entry). */
    private static List<Term> parseMayors(String body) {
        List<long[]> starts = new ArrayList<>();
        List<String> names = new ArrayList<>();
        long off = yearOffset();
        for (JsonElement el : arrayIn(JsonParser.parseString(body))) {
            if (!el.isJsonObject()) continue;
            JsonObject o = el.getAsJsonObject();
            String name = null;
            if (o.has("winner") && o.get("winner").isJsonObject() && o.getAsJsonObject("winner").has("name")) name = o.getAsJsonObject("winner").get("name").getAsString();
            else if (o.has("name")) name = o.get("name").getAsString();
            long start = -1;
            for (String k : new String[]{"start", "startDate", "from", "timestamp", "date"}) if (start < 0 && o.has(k)) start = time(o.get(k));
            if (start < 0 && o.has("year")) try { start = termStart(o.get("year").getAsLong() + off); } catch (Exception ignored) {}
            if (name != null && start > 0) { names.add(name); starts.add(new long[]{start}); }
        }
        List<Term> out = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            long end = i + 1 < names.size() ? starts.get(i + 1)[0] : System.currentTimeMillis();
            out.add(new Term(names.get(i), starts.get(i)[0], end));
        }
        out.sort(Comparator.comparingLong(Term::start));
        // ends from the next term (the input order may differ from time order)
        List<Term> fixed = new ArrayList<>();
        for (int i = 0; i < out.size(); i++)
            fixed.add(new Term(out.get(i).mayor(), out.get(i).start(), i + 1 < out.size() ? out.get(i + 1).start() : out.get(i).start() + 446_400_000L));
        return fixed;
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
            try {
                build();
            } catch (Throwable e) {
                status = "§cfailed: " + why(e);
                Health.fail(PRICES, why(e));
                Safe.report("Market signals", e);
            } finally {
                running = false;
            }
        }, "skyassist-market");
        t.setDaemon(true);
        t.start();
    }

    private static void build() throws Exception {
        Config c = Config.get();
        Instant now = Instant.now();
        Instant from = now.minus(Duration.ofDays(Math.max(30, c.marketHistoryDays)));
        status = "loading mayor history…";
        lastError = null;
        tried = 0;
        loaded = 0;
        String mayorUrl = "https://sky.coflnet.com/api/mayor?from=" + from.toString().substring(0, 10) + "&to=" + now.toString().substring(0, 10);
        Health.attempt(MAYORS, mayorUrl);
        try {
            terms = parseMayors(cached("mayors", mayorUrl, b -> !parseMayors(b).isEmpty()));
            if (terms.isEmpty()) throw new IllegalStateException("no mayor terms found in the answer");
            Health.ok(MAYORS, Health.count(terms.size(), "terms"));
        } catch (Exception e) {
            terms = List.of();
            lastError = "mayor history: " + why(e);
            Health.fail(MAYORS, why(e));
        }

        Health.attempt(PRICES, "https://sky.coflnet.com/api/bazaar/<item>/history");
        for (int i = 0; i < 60 && Prices.BOOK.isEmpty(); i++) { status = "waiting for Bazaar prices…"; Thread.sleep(1000); }
        if (Prices.BOOK.isEmpty()) {
            status = "§cBazaar prices didn't load (see /skyassist status)";
            Health.fail(PRICES, "waited a minute, but the Bazaar prices (needed to pick the items) didn't load");
            return;
        }

        // the most traded Bazaar items (by coins traded per week)
        List<Map.Entry<String, double[]>> items = new ArrayList<>(Prices.BOOK.entrySet());
        items.removeIf(e -> e.getValue()[1] <= 0 || Math.min(e.getValue()[2], e.getValue()[3]) < 10_000);
        items.sort((a, b) -> Double.compare(Math.min(b.getValue()[2], b.getValue()[3]) * b.getValue()[1], Math.min(a.getValue()[2], a.getValue()[3]) * a.getValue()[1]));
        int n = Math.min(c.marketItems, items.size());
        for (int i = 0; i < n; i++) {
            String id = items.get(i).getKey();
            status = "loading price history " + (i + 1) + "/" + n + " (" + Prices.nameOf(id) + ")…";
            tried++;
            List<Point> pts = history(id, from, now);
            if (pts.size() > 20) { synchronized (HISTORY) { HISTORY.put(id, pts); } loaded++; }
            if (i == 4 && loaded == 0) {           // first five all failed: the source is down or changed, stop hammering it
                status = "§cno price history could be downloaded" + (lastError != null ? " (" + lastError + ")" : "");
                Health.fail(PRICES, "none of the first 5 items could be downloaded" + (lastError != null ? " - " + lastError : ""));
                builtAt = System.currentTimeMillis();
                return;
            }
        }
        if (loaded == 0) {
            status = "§cno price history could be downloaded" + (lastError != null ? " (" + lastError + ")" : "");
            Health.fail(PRICES, "no item had enough history" + (lastError != null ? " - " + lastError : ""));
            builtAt = System.currentTimeMillis();
            return;
        }
        status = "calculating…";
        signals = compute();
        builtAt = System.currentTimeMillis();
        Health.ok(PRICES, loaded + " of " + tried + " items" + (loaded < tried && lastError != null ? " (some failed: " + lastError + ")" : ""));
        status = "§aready §7(" + HISTORY.size() + " items with history, " + terms.size() + " mayor terms"
                + (lastError != null ? ", §e" + lastError + "§7" : "") + ")";
    }

    /** Full range first; if that gives nothing, the last week only (enough for trends and movers). */
    private static List<Point> history(String id, Instant from, Instant now) {
        String base = "https://sky.coflnet.com/api/bazaar/" + id + "/history";
        try {
            List<Point> pts = parseHistory(cached("h_" + id, base + "?start=" + from + "&end=" + now, MarketSignals::enoughPoints));
            if (pts.size() > 20) return pts;
        } catch (Exception e) { lastError = e.getMessage(); }
        try {
            List<Point> pts = parseHistory(cached("w_" + id, base + "/week", MarketSignals::enoughPoints));
            if (pts.size() > 20) return pts;
        } catch (Exception e) { lastError = e.getMessage(); }
        try {
            Instant weekAgo = now.minus(Duration.ofDays(7));
            return parseHistory(cached("w2_" + id, base + "?start=" + weekAgo + "&end=" + now, MarketSignals::enoughPoints));
        } catch (Exception e) { lastError = e.getMessage(); }
        return List.of();
    }

    private static boolean enoughPoints(String body) { return parseHistory(body).size() > 20; }

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
        if (HISTORY.isEmpty() && !running && System.currentTimeMillis() - builtAt > 30_000) refresh();
        final MenuScreen[] ref = new MenuScreen[1];
        ref[0] = new MenuScreen("Market signals", List.of(
                new Tab("Mayor", () -> page("Mayor", ref)),
                new Tab("Events", () -> page("Event", ref)),
                new Tab("Trends", () -> page("Trend", ref)),
                new Tab("Movers", () -> movers(ref)),
                new Tab("How this works", MarketSignals::help)
        ), 0, parent).searchable().liveWhile(() -> running);
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
        if (rows.isEmpty()) rows.add(new Row(running ? "§eLoading… §7" + status : HISTORY.isEmpty()
                ? "§cNo price history yet. §7" + status
                : "§7No " + kind.toLowerCase(Locale.ROOT) + " signal passed the backtest right now. §8See the Movers tab, or lower the minimums in Settings → Bazaar → Market signals."));
        List<String> footer = new ArrayList<>();
        footer.add("§7Status: " + status + (running ? " §8(updates by itself)" : ""));
        if (status.startsWith("§c")) footer.add("§8Why it failed, and a Retry button: /skyassist status (Data tab).");
        if (kind.equals("Mayor")) footer.add("§7Current mayor: §f" + (Election.mayor != null ? Election.mayor : "?") + "§7, election leader: §f"
                + (Election.leader != null ? Election.leader + String.format(Locale.US, " (%.0f%%)", Election.leaderShare) : "no election running"));
        footer.add("§8Only signals that held up in the backtest are shown. These are statistics from the past, not guarantees.");
        return new Page(new String[]{"Item", "When", "Expected", "Backtest"}, new int[]{150, 120, 60, 70}, rows, top, footer);
    }

    /** Biggest 24 h / 7 d moves among the items with history: always something to look at. */
    private static Page movers(MenuScreen[] ref) {
        Map<String, List<Point>> hist;
        synchronized (HISTORY) { hist = new LinkedHashMap<>(HISTORY); }
        long now = System.currentTimeMillis();
        record Move(String id, double now, double d1, double d7) {}
        List<Move> moves = new ArrayList<>();
        for (var e : hist.entrySet()) {
            List<Point> pts = e.getValue();
            double p = pts.get(pts.size() - 1).price();
            double a = median(pts, now - 2 * 86_400_000L, now - 86_400_000L), b = median(pts, now - 8 * 86_400_000L, now - 6 * 86_400_000L);
            if (a <= 0 && b <= 0) { a = pts.get(0).price(); }
            moves.add(new Move(e.getKey(), p, a > 0 ? (p / a - 1) * 100 : Double.NaN, b > 0 ? (p / b - 1) * 100 : Double.NaN));
        }
        moves.sort((x, y) -> Double.compare(Math.abs(Double.isNaN(y.d7) ? y.d1 : y.d7), Math.abs(Double.isNaN(x.d7) ? x.d1 : x.d7)));
        List<Row> rows = new ArrayList<>();
        for (Move m : moves) {
            String name = Prices.nameOf(m.id());
            rows.add(new Row(new String[]{"§f" + name, "§f" + Bazaar.Fmt1(m.now()), color(m.d1()), color(m.d7())},
                    "§f" + name + "\n§7Now " + Bazaar.Fmt1(m.now()) + "\n§724 h " + color(m.d1()) + "  §77 d " + color(m.d7()),
                    List.of(new Action("§eBazaar", "Open " + name + " in the Bazaar.", () -> MenuScreen.runCommand("bz " + name)))));
        }
        if (rows.isEmpty()) rows.add(new Row(running ? "§eLoading… §7" + status : "§cNo price history yet. §7" + status));
        List<String> footer = new ArrayList<>();
        footer.add("§7Status: " + status);
        if (status.startsWith("§c")) footer.add("§8Why it failed, and a Retry button: /skyassist status (Data tab).");
        footer.add("§8Sorted by the biggest 7-day move. Data: SkyCofl (sky.coflnet.com).");
        return new Page(new String[]{"Item", "Price", "24 h", "7 d"}, new int[]{150, 80, 60, 60}, rows, List.of(), footer);
    }

    private static String color(double v) {
        return Double.isNaN(v) ? "§8-" : (v >= 0 ? "§a+" : "§c") + String.format(Locale.US, "%.1f%%", v);
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
