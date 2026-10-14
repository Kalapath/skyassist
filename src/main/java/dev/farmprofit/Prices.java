package dev.farmprofit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

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
    private static final Map<String, double[]> BAZAAR = new ConcurrentHashMap<>(); // {instasell, sellorder}
    private static final Map<String, Double> BINS = new ConcurrentHashMap<>();
    private static volatile long lastFetch, lastBinTry;
    private static volatile boolean binsLoading;
    private static volatile int retryStreak;

    static {
        Health.onRetry("Bazaar prices", () -> { retryStreak = 0; refresh(); });
        Health.onRetry("Item list", () -> fetchItems());
        Health.onRetry("Lowest BIN", () -> loadBins(0));
        Health.onRetry("BIN averages", () -> fetchAverages());
    }

    public static void tick() {
        long now = System.currentTimeMillis();
        // until the first load has worked, try again soon (30 s, 1 min, 2 min ... up to the normal 10 min)
        long wait = BAZAAR.isEmpty() ? Math.min(REFRESH_MS, 30_000L << Math.min(5, retryStreak)) : REFRESH_MS;
        if (now - lastFetch > wait) {
            if (BAZAAR.isEmpty()) retryStreak++;
            refresh();
        }
        // refresh the bazaar much more often while you have orders out or price alerts waiting
        else if ((Bazaar.hasOpenOrders() || PriceAlerts.watching()) && now - lastBazaarFetch > Config.get().bzRefreshSeconds * 1000L) {
            lastBazaarFetch = now;
            fetchBazaar();
        }
        // Bazaar is fine but no auction prices came through: try those again every two minutes
        else if (!BAZAAR.isEmpty() && BINS.size() < 100 && !binsLoading && now - lastBinTry > 120_000) loadBins(0);
    }

    public static void refresh() {
        lastFetch = System.currentTimeMillis();
        lastBazaarFetch = lastFetch;
        fetchBazaar();
        if (!itemsLoaded) fetchItems();
        loadBins(0);
        fetchAverages();
    }

    private static void fetchBazaar() { Http.get("Bazaar prices", BAZAAR_URL, 30, Prices::parseBazaar); }

    private static void fetchItems() { Http.get("Item list", ITEMS_URL, 60, Prices::parseItems); }

    private static void fetchAverages() { Http.get("BIN averages", "https://lb.tricked.dev/averages/1day.json", 30, Prices::parseAverages); }

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
        if (i == 0) { if (binsLoading && System.currentTimeMillis() - lastBinTry < 60_000) return; binsLoading = true; }
        lastBinTry = System.currentTimeMillis();
        if (i >= src.size()) {
            binSource = "all sources failed";
            binsLoading = false;
            Health.fail("Lowest BIN", "all " + src.size() + " sources failed (see the log for each reason)");
            return;
        }
        String url = src.get(i);
        Http.get("Lowest BIN", url, 30, java.util.Map.of(), body -> {
            int n = parseBins(body);
            if (n < 100) throw new IllegalStateException("only " + n + " prices - this source looks wrong");
            binSource = url;
            binsLoading = false;
            return Health.count(BINS.size(), "auction prices") + " from " + url.replaceFirst("^https?://", "").replaceFirst("/.*$", "");
        }, () -> loadBins(i + 1));                                    // anything wrong: try the next source
    }

    /** Average lowest BIN over the last day (for spotting inflated prices). */
    private static final Map<String, Double> BIN_AVG = new ConcurrentHashMap<>();

    private static String parseAverages(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        int n = 0;
        for (var e : root.entrySet()) {
            try {
                var v = e.getValue();
                double d = v.isJsonPrimitive() ? v.getAsDouble()
                        : v.getAsJsonObject().has("price") ? v.getAsJsonObject().get("price").getAsDouble()
                        : v.getAsJsonObject().has("clean_price") ? v.getAsJsonObject().get("clean_price").getAsDouble() : 0;
                if (d > 0) { BIN_AVG.put(e.getKey(), d); n++; }
            } catch (Exception ignored) {}
        }
        if (n == 0) throw new IllegalStateException("no prices in the file");
        return Health.count(n, "daily averages");
    }

    /** Average lowest BIN over a day, or 0 if unknown. */
    public static double binAverage(String id) { return BIN_AVG.getOrDefault(id, 0.0); }

    private static String parseBazaar(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("success") || !root.get("success").getAsBoolean()) throw new IllegalStateException("the API said success=false");
        int n = 0;
        for (var e : root.getAsJsonObject("products").entrySet()) {
            try {                                                     // one odd product must not drop the rest
                JsonObject p = e.getValue().getAsJsonObject();
                JsonObject qs = p.getAsJsonObject("quick_status");
                if (qs == null) continue;
                BAZAAR.put(e.getKey(), new double[]{qs.get("sellPrice").getAsDouble(), qs.get("buyPrice").getAsDouble()});
                double topBuy = top(p, "sell_summary"), lowSell = top(p, "buy_summary");   // Hypixel's naming is swapped
                BOOK.put(e.getKey(), new double[]{topBuy, lowSell, num(qs, "sellMovingWeek"), num(qs, "buyMovingWeek"),
                        num(qs, "buyOrders"), num(qs, "sellOrders")});
                n++;
            } catch (Exception ignored) {}
        }
        if (n == 0) throw new IllegalStateException("no products in the answer");
        retryStreak = 0;
        bazaarUpdated = true;
        return Health.count(n, "products");
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

    private static String parseItems(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        if (!root.has("items")) throw new IllegalStateException("no item list in the answer");
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
        return Health.count(NAME_IDS.size(), "items");
    }

    public static String idFor(String itemName) {
        String id = Items.idFor(itemName);
        if (id != null) return id;
        id = NAME_IDS.get(itemName);
        return id != null ? id : Items.guessId(itemName);
    }

    private static int parseBins(String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        int n = 0;
        for (var e : root.entrySet()) {
            try { BINS.put(e.getKey(), e.getValue().getAsDouble()); n++; } catch (Exception ignored) {}
        }
        return n;
    }

    public static boolean loaded() { return !BAZAAR.isEmpty(); }

    public static int bazaarCount() { return BAZAAR.size(); }
    public static int itemCount() { return NAME_IDS.size(); }
    public static int binCount() { return BINS.size(); }
    public static double npcPrice(String id) { return NPC.getOrDefault(id, 0.0); }
    public static double binPrice(String id) { return BINS.getOrDefault(id, 0.0); }
    /** {instasell, instabuy} before tax, or null. */
    public static double[] bazaarRaw(String id) { return BAZAAR.get(id); }

    /** "Golden Dragon (Legendary pet)": how a pet drop is listed. */
    private static final java.util.regex.Pattern PET_DROP = java.util.regex.Pattern.compile("^(.+) \\((Common|Uncommon|Rare|Epic|Legendary|Mythic) pet\\)$");
    private static final String[] PET_TIERS = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};

    /** The lowest-BIN id of a pet drop: "Golden Dragon (Legendary pet)" -> GOLDEN_DRAGON;4. Null if this isn't one. */
    static String petDropId(String itemName) {
        java.util.regex.Matcher m = PET_DROP.matcher(itemName);
        if (!m.matches()) return null;
        for (int i = 0; i < PET_TIERS.length; i++)
            if (PET_TIERS[i].equals(m.group(2))) return m.group(1).toUpperCase(java.util.Locale.ROOT).replace("'", "").replace(' ', '_') + ";" + i;
        return null;
    }

    public static double price(String itemName) {
        String pet = petDropId(itemName);
        if (pet != null) return priceForId(pet);
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
