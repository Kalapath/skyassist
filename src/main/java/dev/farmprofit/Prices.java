package dev.farmprofit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Bazaar prices from Hypixel's public API, plus lowest-BIN prices for auction items. */
public final class Prices {
    private static final String BAZAAR_URL = "https://api.hypixel.net/v2/skyblock/bazaar";
    private static final String ITEMS_URL = "https://api.hypixel.net/v2/resources/skyblock/items";
    private static final Map<String, Double> NPC = new ConcurrentHashMap<>();
    /** Official item name -> ID, from Hypixel's item list. */
    private static final Map<String, String> NAME_IDS = new ConcurrentHashMap<>();
    private static volatile boolean itemsLoaded;
    /** id -> {topBuyOrder, lowestSellOffer, sellMovingWeek, buyMovingWeek, buyOrders, sellOrders} */
    public static final Map<String, double[]> BOOK = new ConcurrentHashMap<>();
    public static final Map<String, String> ID_NAMES = new ConcurrentHashMap<>();
    /** Set when fresh bazaar data arrives; the bazaar tracker picks it up on the game thread. */
    public static volatile boolean bazaarUpdated;
    private static volatile long lastBazaarFetch;
    private static final long REFRESH_MS = 10 * 60 * 1000;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private static final Map<String, double[]> BAZAAR = new ConcurrentHashMap<>(); // {instasell, sellorder}
    private static final Map<String, Double> BINS = new ConcurrentHashMap<>();
    private static volatile long lastFetch;

    public static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastFetch > REFRESH_MS) refresh();
        // refresh the bazaar much more often while you have orders out
        else if (Bazaar.hasOpenOrders() && now - lastBazaarFetch > Config.get().bzRefreshSeconds * 1000L) {
            lastBazaarFetch = now;
            fetch(BAZAAR_URL, Prices::parseBazaar);
        }
    }

    public static void refresh() {
        lastFetch = System.currentTimeMillis();
        lastBazaarFetch = lastFetch;
        fetch(BAZAAR_URL, Prices::parseBazaar);
        if (!itemsLoaded) fetch(ITEMS_URL, Prices::parseItems);
        loadBins(0);
        fetch("https://lb.tricked.dev/averages/1day.json", Prices::parseAverages);
    }

    /** Lowest-BIN sources, tried in order until one answers (moulberry.codes alone left AH prices empty when it was down). */
    private static java.util.List<String> binSources() {
        java.util.List<String> out = new java.util.ArrayList<>();
        out.add("https://lb.tricked.dev/lowestbins");                 // used by Skytils
        String own = Config.get().lowestBinUrl;
        if (own != null && !own.isBlank() && !out.contains(own)) out.add(own);
        if (!out.contains("https://moulberry.codes/lowestbin.json")) out.add("https://moulberry.codes/lowestbin.json");
        return out;
    }

    public static volatile String binSource = "none yet";

    private static void loadBins(int i) {
        java.util.List<String> src = binSources();
        if (i >= src.size()) { binSource = "all sources failed"; return; }
        String url = src.get(i);
        HTTP.sendAsync(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyAssist").timeout(Duration.ofSeconds(30)).GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    int before = BINS.size();
                    if (res.statusCode() == 200) { try { parseBins(res.body()); } catch (Exception ignored) {} }
                    if (res.statusCode() == 200 && BINS.size() > 100) binSource = url;
                    else if (BINS.size() <= Math.max(100, before)) loadBins(i + 1);                  // try the next one
                })
                .exceptionally(err -> { loadBins(i + 1); return null; });
    }

    /** Average lowest BIN over the last day (for spotting inflated prices). */
    private static final Map<String, Double> BIN_AVG = new ConcurrentHashMap<>();

    private static void parseAverages(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            for (var e : root.entrySet()) {
                try {
                    var v = e.getValue();
                    double d = v.isJsonPrimitive() ? v.getAsDouble()
                            : v.getAsJsonObject().has("price") ? v.getAsJsonObject().get("price").getAsDouble()
                            : v.getAsJsonObject().has("clean_price") ? v.getAsJsonObject().get("clean_price").getAsDouble() : 0;
                    if (d > 0) BIN_AVG.put(e.getKey(), d);
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}
    }

    /** Average lowest BIN over a day, or 0 if unknown. */
    public static double binAverage(String id) { return BIN_AVG.getOrDefault(id, 0.0); }

    private static void fetch(String url, java.util.function.Consumer<String> parser) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "SkyAssist")
                .timeout(Duration.ofSeconds(30)).GET().build();
        HTTP.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> {
                    if (res.statusCode() == 200) parser.accept(res.body());
                    else FarmProfitClient.LOG.warn("Price fetch {} returned {}", url, res.statusCode());
                })
                .exceptionally(err -> { FarmProfitClient.LOG.warn("Price fetch failed: {}", url, err); return null; });
    }

    private static void parseBazaar(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("success") || !root.get("success").getAsBoolean()) return;
        for (var e : root.getAsJsonObject("products").entrySet()) {
            JsonObject qs = e.getValue().getAsJsonObject().getAsJsonObject("quick_status");
            if (qs == null) continue;
            BAZAAR.put(e.getKey(), new double[]{qs.get("sellPrice").getAsDouble(), qs.get("buyPrice").getAsDouble()});
            JsonObject p = e.getValue().getAsJsonObject();
            double topBuy = top(p, "sell_summary"), lowSell = top(p, "buy_summary");   // Hypixel's naming is swapped
            BOOK.put(e.getKey(), new double[]{topBuy, lowSell, num(qs, "sellMovingWeek"), num(qs, "buyMovingWeek"),
                    num(qs, "buyOrders"), num(qs, "sellOrders")});
        }
        bazaarUpdated = true;
    }

    private static double top(JsonObject product, String key) {
        try {
            var arr = product.getAsJsonArray(key);
            return arr == null || arr.isEmpty() ? -1 : arr.get(0).getAsJsonObject().get("pricePerUnit").getAsDouble();
        } catch (Exception e) { return -1; }
    }

    private static double num(JsonObject o, String key) {
        try { return o.has(key) ? o.get(key).getAsDouble() : 0; } catch (Exception e) { return 0; }
    }

    /** Readable name for a Bazaar ID, the way the game shows it. */
    public static String nameOf(String id) {
        String n = ID_NAMES.get(id);
        if (n != null) return n;
        n = Items.nameForId(id);                                   // our own list (Cocoa Beans, Raw Salmon, Mithril...)
        if (n != null) return n;
        if (id.startsWith("ESSENCE_")) return words(id.substring(8)) + " Essence";              // ESSENCE_DRAGON -> Dragon Essence
        if (id.startsWith("SHARD_")) return words(id.substring(6)) + " Shard";                  // SHARD_SPARROW -> Sparrow Shard
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^ENCHANTMENT_(.+)_(\\d+)$").matcher(id);
        if (m.matches()) return words(m.group(1)) + " " + roman(Integer.parseInt(m.group(2)));  // ENCHANTMENT_SHARPNESS_7 -> Sharpness VII
        if (id.startsWith("ENCHANTMENT_")) return words(id.substring(12));
        return words(id.replaceAll(":\\d+$", ""));
    }

    private static String words(String id) {
        StringBuilder b = new StringBuilder();
        for (String w : id.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (w.equals("of") || w.equals("the")) b.append(w).append(' ');
            else b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(' ');
        }
        String s = b.toString().trim();
        return s.isEmpty() ? id : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private static String roman(int n) {
        String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return n >= 0 && n < r.length ? r[n] : String.valueOf(n);
    }

    private static void parseItems(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("items")) return;
        for (var el : root.getAsJsonArray("items")) {
            JsonObject it = el.getAsJsonObject();
            if (!it.has("id")) continue;
            String id = it.get("id").getAsString();
            if (it.has("name")) {
                String name = Tracker.strip(it.get("name").getAsString());
                NAME_IDS.putIfAbsent(name, id);
                ID_NAMES.putIfAbsent(id, name);
            }
            if (it.has("npc_sell_price")) NPC.put(id, it.get("npc_sell_price").getAsDouble());
            if (it.has("name") && Shards.looksLikeShard(id, Tracker.strip(it.get("name").getAsString())))   // attribute shards, with rarity
                Shards.fromApi(id, Tracker.strip(it.get("name").getAsString()), it.has("tier") ? it.get("tier").getAsString() : "");
        }
        itemsLoaded = true;
    }

    public static String idFor(String itemName) {
        String id = Items.idFor(itemName);
        if (id != null) return id;
        id = NAME_IDS.get(itemName);
        return id != null ? id : Items.guessId(itemName);
    }

    private static void parseBins(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        for (var e : root.entrySet()) {
            try { BINS.put(e.getKey(), e.getValue().getAsDouble()); } catch (Exception ignored) {}
        }
    }

    public static boolean loaded() { return !BAZAAR.isEmpty(); }

    public static int bazaarCount() { return BAZAAR.size(); }
    public static int itemCount() { return NAME_IDS.size(); }
    public static int binCount() { return BINS.size(); }
    public static double npcPrice(String id) { return NPC.getOrDefault(id, 0.0); }
    public static double binPrice(String id) { return BINS.getOrDefault(id, 0.0); }
    /** {instasell, instabuy} before tax, or null. */
    public static double[] bazaarRaw(String id) { return BAZAAR.get(id); }

    public static double price(String itemName) {
        double v = priceForId(idFor(itemName));
        if (v > 0 || !itemName.endsWith(" Shard")) return v;
        for (String cand : shardIds(itemName)) {
            v = priceForId(cand);
            if (v > 0) return v;
        }
        return 0;
    }

    public static double priceOfId(String id) { return priceForId(id); }

    private static double priceForId(String id) {
        String mode = Config.get().priceMode == null ? "best" : Config.get().priceMode.toLowerCase();
        double[] p = BAZAAR.get(id);
        double npc = NPC.getOrDefault(id, 0.0);
        if (p != null) {
            double keep = 1 - Config.get().bzTax / 100.0;     // selling on the Bazaar costs tax
            double insta = p[0] * keep, offer = p[1] * keep;
            return switch (mode) {
                case "sellorder" -> offer;
                case "npc" -> npc > 0 ? npc : insta;
                case "best" -> Math.max(insta, npc);
                default -> insta;
            };
        }
        Double bin = BINS.get(id);
        if (bin != null && !"npc".equals(mode)) return Math.max(bin, "best".equals(mode) ? npc : 0);
        return npc;
    }

    /** Possible Bazaar IDs for a shard, e.g. "Sparrow Shard" -> SHARD_SPARROW. */
    private static String[] shardIds(String shardName) {
        String base = Items.guessId(shardName.substring(0, shardName.length() - " Shard".length()));
        return new String[]{"SHARD_" + base, base + "_SHARD", "ATTRIBUTE_SHARD_" + base};
    }

    /** True if Bazaar/BIN data knows this shard (used to clean up names read from chat). */
    public static boolean knowsShard(String shardName) {
        if (NAME_IDS.containsKey(shardName)) return true;
        for (String cand : shardIds(shardName)) if (BAZAAR.containsKey(cand) || BINS.containsKey(cand)) return true;
        return false;
    }

    private Prices() {}
}
