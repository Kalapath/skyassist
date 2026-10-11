package dev.farmprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Which talismans should I get next?" Ranks accessories you don't have by coins per Magical Power,
 * using the cheaper of lowest BIN and craft cost. Knows upgrade chains (Talisman -> Ring -> Artifact),
 * so upgrading only counts the extra MP and the part you already own is free.
 * What you own is read when you open your Accessory Bag.
 */
public final class Accessories {
    public record Info(String id, String name, String rarity, int mp) {}
    public record Pick(Info item, int gain, double cost, String source, String replaces) {}

    private static final Pattern RARITY = Pattern.compile("(VERY SPECIAL|SPECIAL|COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE) (?:DUNGEON )?(?:ACCESSORY|HATCESSORY)");
    private static final Map<String, Info> ALL = new ConcurrentHashMap<>();
    /** id -> its upgrade chain, lowest tier first */
    private static final Map<String, List<String>> FAMILY = new ConcurrentHashMap<>();
    private static final Path FILE = Config.DIR.resolve("accessories.json");
    private static Set<String> owned;
    private static long scannedAt;

    public static int count() { return ALL.size(); }

    public static long scannedAt() { owned(); return scannedAt; }

    /** The upgrade chain an accessory belongs to (lowest tier first), or just itself. */
    public static List<String> chainOf(String id) { return FAMILY.getOrDefault(id, List.of(id)); }

    public static Info info(String id) { return ALL.get(id); }

    public static boolean owns(String id) { return owned().contains(id); }

    static int mpFor(String rarity) {
        return switch (rarity) {
            case "COMMON", "SPECIAL" -> 3;
            case "UNCOMMON", "VERY SPECIAL" -> 5;
            case "RARE" -> 8;
            case "EPIC" -> 12;
            case "LEGENDARY" -> 16;
            case "MYTHIC" -> 22;
            default -> 0;
        };
    }

    // ---------------- data from the NEU repository (called while recipes load) ----------------

    static void ingest(String id, JsonObject item) {
        if (!item.has("lore") || !item.get("lore").isJsonArray()) return;
        var lore = item.getAsJsonArray("lore");
        for (int i = lore.size() - 1; i >= 0 && i >= lore.size() - 6; i--) {
            String line = Tracker.strip(lore.get(i).getAsString());
            Matcher m = RARITY.matcher(line);
            if (m.find()) {
                String name = item.has("displayname") ? Tracker.strip(item.get("displayname").getAsString()) : id;
                ALL.put(id, new Info(id, name, m.group(1), mpFor(m.group(1))));
                return;
            }
        }
    }

    /** parents.json: { "LOWEST_TIER": ["NEXT", "NEXT", ...] } */
    static void loadParents(JsonObject parents) {
        for (var e : parents.entrySet()) {
            if (!e.getValue().isJsonArray()) continue;
            List<String> chain = new ArrayList<>();
            chain.add(e.getKey());
            for (JsonElement c : e.getValue().getAsJsonArray()) chain.add(c.getAsString());
            for (String id : chain) FAMILY.put(id, chain);
        }
    }

    // ---------------- what you own ----------------

    private static Set<String> owned() {
        if (owned == null) load();
        return owned;
    }

    /** Called when a menu opens: reads Accessory Bag pages. */
    static void scanMenu(String title, List<ItemStack> items) {
        if (!title.startsWith("Accessory Bag")) return;
        boolean firstPage = title.contains("(1/") || !title.contains("(");
        if (firstPage && System.currentTimeMillis() - scannedAt > 60_000) owned().clear();   // fresh scan
        int before = owned().size();
        for (ItemStack is : items) {
            String id = ItemIds.of(is);
            if (id != null && ALL.containsKey(id)) owned().add(id);
        }
        scannedAt = System.currentTimeMillis();
        if (owned().size() != before) save();
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) {
                Map<String, Object> m = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, Object>>() {}.getType());
                owned = new HashSet<>();
                if (m.get("owned") instanceof List<?> l) for (Object o : l) owned.add(String.valueOf(o));
                if (m.get("scannedAt") instanceof Number n) scannedAt = n.longValue();
            }
        } catch (Exception ignored) {}
        if (owned == null) owned = new HashSet<>();
    }

    private static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Map<String, Object> m = new HashMap<>();
            m.put("owned", new ArrayList<>(owned));
            m.put("scannedAt", scannedAt);
            Config.write(FILE, Config.GSON.toJson(m));
        } catch (Exception ignored) {}
    }

    // ---------------- recommendations ----------------

    /** Accessories you don't have that can't be bought or crafted (quests, drops, events...), filled by recommend(). */
    public static final List<Pick> unbuyable = new ArrayList<>();

    public static List<Pick> recommend(int count) {
        unbuyable.clear();
        Config c = Config.get();
        Set<String> have = owned();
        Map<List<String>, Pick> bestPerFamily = new HashMap<>();
        List<Pick> singles = new ArrayList<>();
        for (Info info : ALL.values()) {
            if (info.mp() <= 0 || have.contains(info.id())) continue;
            List<String> chain = FAMILY.getOrDefault(info.id(), List.of(info.id()));
            int myTier = chain.indexOf(info.id());
            // what you already have in this chain
            int ownedTier = -1;
            for (int i = 0; i < chain.size(); i++) if (have.contains(chain.get(i))) ownedTier = i;
            if (ownedTier >= myTier && ownedTier >= 0) continue;                 // you have this or better
            Info ownedInfo = ownedTier >= 0 ? ALL.get(chain.get(ownedTier)) : null;
            int gain = info.mp() - (ownedInfo != null ? ownedInfo.mp() : 0);
            if (gain <= 0) continue;

            Set<String> free = new HashSet<>();
            if (ownedTier >= 0) free.addAll(chain.subList(0, ownedTier + 1));   // the part you own costs nothing
            double bin = Prices.binPrice(info.id());
            double craft = c.talismanUseCraft ? CraftCost.costWith(info.id(), free) : 0;
            double npc = CraftCost.npcShopCost(info.id());
            double cost = Double.MAX_VALUE;
            String source = null;
            if (bin > 0 && bin < cost) { cost = bin; source = "AH"; }
            if (craft > 0 && craft < cost) { cost = craft; source = "craft"; }
            if (npc > 0 && npc < cost) { cost = npc; source = "NPC"; }
            if (source == null) { unbuyable.add(new Pick(info, gain, 0, "other", ownedInfo != null ? ownedInfo.name() : null)); continue; }
            if (c.talismanMaxPrice > 0 && cost > c.talismanMaxPrice) continue;
            // buying a higher tier on the AH replaces what you own (you could sell it), crafting uses it up
            Pick p = new Pick(info, gain, cost, source, ownedInfo != null ? ownedInfo.name() : null);
            if (chain.size() > 1) {
                Pick old = bestPerFamily.get(chain);
                if (old == null || p.cost() / p.gain() < old.cost() / old.gain()) bestPerFamily.put(chain, p);
            } else singles.add(p);
        }
        List<Pick> all = new ArrayList<>(singles);
        all.addAll(bestPerFamily.values());
        all.sort((a, b) -> Double.compare(a.cost() / a.gain(), b.cost() / b.gain()));
        unbuyable.sort((a, b) -> b.gain() - a.gain());
        return all.subList(0, Math.min(count, all.size()));
    }

    private static String color(String rarity) {
        return switch (rarity) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "SPECIAL", "VERY SPECIAL" -> "§c";
            default -> "§f";
        };
    }

    public static void show(int count) {
        if (ALL.isEmpty()) {
            Tracker.say("§6[Talismans] §7Item data is still downloading (first time takes a minute). Try again shortly.");
            return;
        }
        if (!Prices.loaded()) { Tracker.say("§6[Talismans] §7Prices are still loading, try again in a moment."); return; }
        List<Pick> picks = recommend(count);
        if (picks.isEmpty()) { Tracker.say("§6[Talismans] §7Nothing found with a price. Check the max price in settings."); return; }
        Tracker.say("§6§l[Talismans] §7Cheapest Magical Power you don't have yet:");
        double total = 0;
        int mp = 0;
        for (int i = 0; i < picks.size(); i++) {
            Pick p = picks.get(i);
            total += p.cost();
            mp += p.gain();
            String line = "§8" + (i + 1) + ". " + color(p.item().rarity()) + p.item().name() + " §7+" + p.gain() + " MP §6"
                    + Fmt.coins(p.cost()) + " §8(" + p.source() + ", " + Fmt.coins(p.cost() / p.gain()) + "/MP)"
                    + (p.replaces() != null ? " §8upgrades " + p.replaces() : "");
            String cmd = p.source().equals("AH") ? "/ahs " + p.item().name() : "/recipe " + p.item().name();
            String hover = color(p.item().rarity()) + p.item().name() + "\n§7" + p.item().rarity() + " accessory, +" + p.gain() + " MP"
                    + "\n§7" + (p.source().equals("AH") ? "Lowest BIN" : "Craft cost") + ": §6" + Fmt.coins(p.cost())
                    + "\n\n§eClick to " + (p.source().equals("AH") ? "search the Auction House" : "see the recipe");
            Tracker.say(Chat.clickable(line, cmd, hover));
        }
        Tracker.say("§7Total: §6" + Fmt.coins(total) + " §7for §a+" + mp + " MP");
        if (scannedAt == 0) Tracker.say("§eOpen every page of your Accessory Bag once so I can skip what you already have.");
        else Tracker.say("§8Based on your Accessory Bag as of " + new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date(scannedAt))
                + ". Reopen it after buying to update.");
    }

    private Accessories() {}
}
