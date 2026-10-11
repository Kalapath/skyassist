package dev.farmprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Craft cost in tooltips (the NEU feature people miss): what an item costs to craft from ingredients you buy,
 * compared with buying it. Recipes come from the public NEU item repository (downloaded weekly, kept on disk).
 */
public final class CraftCost {
    private record Recipe(Map<String, Integer> ingredients, int count) {}

    private static final Map<String, Recipe> RECIPES = new ConcurrentHashMap<>();
    /** id -> {coins, then ingredient id/amount pairs as a Recipe} for things sold by NPCs */
    private static final Map<String, Object[]> NPC_SHOP = new ConcurrentHashMap<>();
    /** id -> short "how to get it" text from the item data (e.g. "Requires Farming Skill 7") */
    public static final Map<String, String> HINTS = new ConcurrentHashMap<>();
    private static final Path ZIP = Config.DIR.resolve("neu-repo.zip");
    private static final Path TMP = Config.DIR.resolve("neu-repo.tmp");
    private static final String NAME = "Recipes (NEU repo)";
    private static volatile boolean loading;
    private static volatile long lastCheck;
    /** A retry from the Status page downloads again even if the saved copy is recent. */
    private static volatile boolean forceDownload;
    /** Identifies the saved copy that was last read, so an unchanged file isn't read again every few hours. */
    private static volatile String parsedStamp = "";

    static { Health.onRetry(NAME, () -> { forceDownload = true; lastCheck = 0; }); }

    public static int count() { return RECIPES.size(); }

    public static void tick() {
        if (loading) return;                  // also needed for /talismans, so it loads even with tooltips off
        long now = System.currentTimeMillis();
        long wait = RECIPES.isEmpty() ? Health.delay(NAME, 15 * 60_000L, 15 * 60_000L) : Health.delay(NAME, 6 * 3_600_000L, 30 * 60_000L);
        if (now - lastCheck < wait) return;
        lastCheck = now;
        loading = true;
        Thread t = new Thread(CraftCost::load, "skyassist-recipes");
        t.setDaemon(true);
        t.start();
    }

    /** Downloads the NEU item repository when the saved copy is missing or a week old, then reads it (if it changed). */
    private static void load() {
        try {
            boolean force = forceDownload;
            forceDownload = false;
            String url = Config.get().neuRepoUrl;
            boolean haveZip = Files.exists(ZIP);
            boolean stale = force || !haveZip || System.currentTimeMillis() - Files.getLastModifiedTime(ZIP).toMillis() > 7 * 86_400_000L;
            String problem = null;
            if (stale) {
                if (url == null || url.isBlank()) {
                    problem = "no download address in the settings";
                } else {
                    Health.attempt(NAME, url);
                    try {
                        HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build();
                        HttpResponse<Path> r = http.send(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyAssist")
                                .timeout(Duration.ofMinutes(3)).GET().build(), HttpResponse.BodyHandlers.ofFile(TMP));
                        if (r.statusCode() == 200) Files.move(TMP, ZIP, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        else problem = Http.status(r.statusCode());
                    } catch (Exception e) {
                        problem = Http.reason(e);
                    } finally {
                        try { Files.deleteIfExists(TMP); } catch (Exception ignored) {}      // never leave a half-downloaded file behind
                    }
                }
            }
            if (Files.exists(ZIP)) {
                String stamp = Files.getLastModifiedTime(ZIP).toMillis() + ":" + Files.size(ZIP);
                String detail;
                if (stamp.equals(parsedStamp) && !RECIPES.isEmpty()) {
                    detail = Health.count(RECIPES.size(), "recipes");
                } else {
                    try {
                        detail = parse();
                        parsedStamp = stamp;
                    } catch (java.util.zip.ZipException bad) {
                        try { Files.deleteIfExists(ZIP); } catch (Exception ignored) {}       // damaged: fetch a fresh one next time
                        parsedStamp = "";
                        throw bad;
                    }
                }
                Health.ok(NAME, detail + (problem != null ? " (saved copy)" : ""));
                if (problem != null) Health.fail(NAME, problem + " - using the copy saved on disk");
            } else if (problem != null) {
                Health.fail(NAME, problem);
            } else {
                Health.fail(NAME, "nothing was downloaded");
            }
        } catch (Throwable e) {
            Health.fail(NAME, "couldn't read the recipe data: " + Safe.describe(e));
        } finally {
            loading = false;
        }
    }

    private static String parse() throws Exception {
        Map<String, Recipe> found = new HashMap<>();
        try (ZipFile zip = new ZipFile(ZIP.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                String name = e.getName();
                if (name.endsWith("constants/parents.json")) {
                    try (InputStream in = zip.getInputStream(e)) {
                        Accessories.loadParents(JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject());
                    } catch (Exception ignored) {}
                    continue;
                }
                if (!name.contains("/items/") || !name.endsWith(".json")) continue;
                try (InputStream in = zip.getInputStream(e)) {
                    JsonObject item = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    if (!item.has("internalname")) continue;
                    String id = item.get("internalname").getAsString();
                    Accessories.ingest(id, item);
                    Shards.ingest(id, item);
                    if (item.has("crafttext") && !item.get("crafttext").getAsString().isBlank())
                        HINTS.put(id, Tracker.strip(item.get("crafttext").getAsString()));
                    if (item.has("recipes") && item.get("recipes").isJsonArray()) {
                        for (JsonElement r : item.getAsJsonArray("recipes")) {
                            JsonObject ro = r.getAsJsonObject();
                            if (!ro.has("type") || !ro.get("type").getAsString().equals("npc_shop") || !ro.has("cost")) continue;
                            double coins = 0;
                            Map<String, Integer> other = new HashMap<>();
                            for (JsonElement c : ro.getAsJsonArray("cost")) {
                                String v = c.getAsString();
                                int cut = v.lastIndexOf(':');
                                String cid = cut > 0 ? v.substring(0, cut) : v;
                                double n = 1;
                                try { if (cut > 0) n = Double.parseDouble(v.substring(cut + 1)); } catch (NumberFormatException ignored) {}
                                if (cid.equals("SKYBLOCK_COIN")) coins += n; else other.merge(cid, (int) n, Integer::sum);
                            }
                            NPC_SHOP.put(id, new Object[]{coins, other});
                            break;
                        }
                    }
                    JsonObject grid = null;
                    int count = 1;
                    if (item.has("recipe") && item.get("recipe").isJsonObject()) grid = item.getAsJsonObject("recipe");
                    else if (item.has("recipes") && item.get("recipes").isJsonArray()) {
                        for (JsonElement r : item.getAsJsonArray("recipes")) {
                            JsonObject ro = r.getAsJsonObject();
                            String type = ro.has("type") ? ro.get("type").getAsString() : "crafting";
                            if (!type.equals("crafting")) continue;
                            grid = ro;
                            if (ro.has("count")) count = ro.get("count").getAsInt();
                            break;
                        }
                    }
                    if (grid == null) continue;
                    if (grid.has("count")) count = Math.max(1, grid.get("count").getAsInt());
                    Map<String, Integer> ing = new HashMap<>();
                    for (String slot : List.of("A1", "A2", "A3", "B1", "B2", "B3", "C1", "C2", "C3")) {
                        if (!grid.has(slot)) continue;
                        String v = grid.get(slot).getAsString();
                        if (v.isBlank()) continue;
                        int cut = v.lastIndexOf(':');
                        int n = 1;
                        String iid = v;
                        if (cut > 0) {
                            try { n = Integer.parseInt(v.substring(cut + 1)); iid = v.substring(0, cut); } catch (NumberFormatException ignored) {}
                        }
                        ing.merge(iid, n, Integer::sum);
                    }
                    if (!ing.isEmpty()) found.put(id, new Recipe(ing, count));
                } catch (Exception ignored) {}
            }
        }
        if (found.isEmpty()) throw new IllegalStateException("no recipes found in the file (is the address a NotEnoughUpdates-REPO zip?)");
        RECIPES.clear();
        RECIPES.putAll(found);
        return Health.count(found.size(), "recipes") + ", " + Health.count(NPC_SHOP.size(), "shop items");
    }

    /** What it costs to buy one (Bazaar instabuy or lowest BIN), 0 if unknown. */
    private static double buyPrice(String id) {
        double[] bz = Prices.bazaarRaw(id);
        if (bz == null && id.contains("-")) bz = Prices.bazaarRaw(id.replace('-', ':'));
        if (bz != null && bz[1] > 0) return bz[1];
        double bin = Prices.binPrice(id);
        return bin > 0 ? bin : 0;
    }

    /** Cheapest cost of one item: buy it, or craft it from ingredients (each bought or crafted, a few levels deep). */
    private static double cheapest(String id, int depth, Set<String> path) { return cheapest(id, depth, path, Set.of()); }

    private static double cheapest(String id, int depth, Set<String> path, Set<String> free) {
        if (free.contains(id)) return 0.000001;              // you already own it
        double buy = buyPrice(id);
        if (depth <= 0 || !path.add(id)) return buy;
        double craft = craftOnce(id, depth, path, free);
        path.remove(id);
        if (craft <= 0) return buy;
        return buy <= 0 ? craft : Math.min(buy, craft);
    }

    /** Cost of the ingredients for one item, or 0 if it has no recipe / an ingredient has no price. */
    private static double craftOnce(String id, int depth, Set<String> path) { return craftOnce(id, depth, path, Set.of()); }

    private static double craftOnce(String id, int depth, Set<String> path, Set<String> free) {
        Recipe r = RECIPES.get(id);
        if (r == null) return 0;
        double total = 0;
        for (var e : r.ingredients().entrySet()) {
            double each = cheapest(e.getKey(), depth - 1, path, free);
            if (each <= 0) return 0;
            total += each * e.getValue();
        }
        return total / Math.max(1, r.count());
    }

    /** Price at an NPC shop (coins + any items it also asks for), 0 if not sold or an item has no price. */
    @SuppressWarnings("unchecked")
    public static double npcShopCost(String id) {
        Object[] o = NPC_SHOP.get(id);
        if (o == null) return 0;
        double total = (double) o[0];
        for (var e : ((Map<String, Integer>) o[1]).entrySet()) {
            double each = cheapest(e.getKey(), 2, new HashSet<>());
            if (each <= 0) return 0;
            total += each * e.getValue();
        }
        return total;
    }

    /** Every recipe: result id -> (ingredients, how many it makes). */
    public static Map<String, Map.Entry<Map<String, Integer>, Integer>> recipes() {
        Map<String, Map.Entry<Map<String, Integer>, Integer>> out = new HashMap<>();
        RECIPES.forEach((k, v) -> out.put(k, Map.entry(v.ingredients(), v.count())));
        return out;
    }

    /** Craft cost of one item, treating the given items as already owned (free). 0 if it can't be crafted. */
    public static double costWith(String id, Set<String> free) {
        return craftOnce(id, 4, new HashSet<>(Set.of(id)), free);
    }

    /** Tooltip lines: "Craft cost 1.2M (buying: 1.5M) → craft it, saves 300k". */
    public static void add(ItemStack stack, List<Component> lines) {
        if (!Config.get().craftCost || stack == null || stack.isEmpty() || RECIPES.isEmpty() || !Prices.loaded()) return;
        String id = ItemIds.of(stack);
        if (id == null || !RECIPES.containsKey(id)) return;
        double craft = craftOnce(id, 4, new HashSet<>(Set.of(id)));
        if (craft <= 0) return;
        double buy = buyPrice(id);
        int n = Math.max(1, stack.getCount());
        String amount = n > 1 ? " §8(x" + n + ": " + Fmt.coins(craft * n) + ")" : "";
        lines.add(Component.literal("§6Craft cost §f" + Fmt.coins(craft) + amount));
        if (buy > 0) {
            double diff = buy - craft;
            if (Math.abs(diff) / buy < 0.02) lines.add(Component.literal("§8  about the same as buying (" + Fmt.coins(buy) + ")"));
            else if (diff > 0) lines.add(Component.literal("§a  craft it: saves " + Fmt.coins(diff) + " §8(buying " + Fmt.coins(buy) + ")"));
            else lines.add(Component.literal("§c  buy it: " + Fmt.coins(-diff) + " cheaper §8(" + Fmt.coins(buy) + ")"));
        }
    }

    private CraftCost() {}
}
