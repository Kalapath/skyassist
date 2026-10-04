package dev.farmprofit;

import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Attribute shard planner (/shards): which attribute levels are cheapest to buy next on the Bazaar,
 * using the shards-per-level table from the Hypixel SkyBlock Wiki. Your levels are read from the Attribute Menu.
 */
public final class Shards {
    /** Shards needed to go from level i to i+1 (index 0 = reaching level 1), per rarity. Source: wiki Attributes page. */
    private static final Map<String, int[]> PER_LEVEL = Map.of(
            "COMMON", new int[]{1, 3, 5, 6, 7, 8, 10, 14, 18, 24},
            "UNCOMMON", new int[]{1, 2, 3, 4, 5, 6, 7, 8, 12, 16},
            "RARE", new int[]{1, 2, 3, 3, 4, 4, 5, 6, 8, 12},
            "EPIC", new int[]{1, 1, 2, 2, 3, 3, 4, 4, 5, 7},
            "LEGENDARY", new int[]{1, 1, 1, 2, 2, 2, 3, 3, 4, 5});
    private static final Pattern RARITY = Pattern.compile("(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY)");
    private static final Pattern LEVEL = Pattern.compile("(?:Level|Lvl\\.?)\\s*(\\d+|[IVX]+)");
    private static final Pattern ATTR_NAME = Pattern.compile("^(.+?)\\s+([IVX]+|\\d+)$");

    public record Info(String id, String name, String rarity, String attribute, String effect) {}
    public record Pick(Info shard, int from, int to, int shardsNeeded, double cost) {}

    private static final Map<String, Info> ALL = new ConcurrentHashMap<>();
    private static final Path FILE = Config.DIR.resolve("attributes.json");
    private static Map<String, Integer> levels;

    public static int count() { return ALL.size(); }

    /** From the item data: attribute shards only (Hypixel IDs them SHARD_…; Prismarine Shard etc. are ordinary items). */
    static void ingest(String id, JsonObject item) {
        if (!id.startsWith("SHARD_") || !item.has("lore") || !item.has("displayname")) return;
        String name = Tracker.strip(item.get("displayname").getAsString());
        if (!name.endsWith("Shard")) return;
        var lore = item.getAsJsonArray("lore");
        String rarity = null, attribute = null;
        for (int i = lore.size() - 1; i >= 0; i--) {
            String l = Tracker.strip(lore.get(i).getAsString()).trim();
            if (rarity == null) { Matcher m = RARITY.matcher(l); if (m.find()) rarity = m.group(1); }
        }
        StringBuilder effect = new StringBuilder();
        for (int i = 0; i < Math.min(10, lore.size()); i++) {
            String l = Tracker.strip(lore.get(i).getAsString()).trim();
            if (l.isEmpty() || l.startsWith("ID") || RARITY.matcher(l).matches()) continue;
            if (attribute == null && !l.contains("Shard")) { attribute = l; continue; }
            if (attribute != null && effect.length() < 220 && !l.startsWith("Click") && !l.startsWith("Right-click")) effect.append(l).append(' ');
        }
        Info api = ALL.get(id);
        if (rarity == null && api != null) rarity = api.rarity();
        if (rarity != null) ALL.put(id, new Info(id, name, rarity, attribute, effect.toString().trim()));
    }

    private static final java.util.Set<String> NOT_ATTRIBUTE = java.util.Set.of("PRISMARINE_SHARD", "ECHO_SHARD", "AMETHYST_SHARD", "ENCHANTED_PRISMARINE_SHARD");

    /** Attribute shards: "SHARD" in the ID, name ending in "Shard", and not an ordinary item like Prismarine Shard. */
    static boolean looksLikeShard(String id, String name) {
        return id.contains("SHARD") && name.endsWith("Shard") && !NOT_ATTRIBUTE.contains(id) && !name.startsWith("Enchanted ");
    }

    /** From Hypixel's item list (always has every attribute shard and its rarity). Keeps richer item-data info if present. */
    static void fromApi(String id, String name, String tier) {
        String rarity = tier.toUpperCase(Locale.ROOT);
        if (!PER_LEVEL.containsKey(rarity)) rarity = "UNKNOWN";
        Info old = ALL.get(id);
        ALL.put(id, new Info(id, name, rarity, old != null ? old.attribute() : null, old != null ? old.effect() : ""));
    }

    // ---------------- your levels ----------------

    private static Map<String, Integer> levels() {
        if (levels == null) {
            try {
                if (Files.exists(FILE)) levels = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<HashMap<String, Integer>>() {}.getType());
            } catch (Exception ignored) {}
            if (levels == null) levels = new HashMap<>();
        }
        return levels;
    }

    private static int roman(String s) {
        if (s.chars().allMatch(Character::isDigit)) return Integer.parseInt(s);
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }

    /** Called for menus: in the Attribute Menu / Hunting Box, reads each shard's attribute level from its lore. */
    static void scanMenu(String title, List<ItemStack> items) {
        if (!(title.contains("Attribute") || title.contains("Hunting Box"))) return;
        boolean changed = false;
        for (ItemStack is : items) {
            String id = ItemIds.of(is);
            String name = Tracker.strip(is.getHoverName().getString());
            Info info = id != null ? ALL.get(id) : null;
            if (info == null) for (Info i : ALL.values()) if (i.name().equals(name) || (i.attribute() != null && name.startsWith(i.attribute()))) { info = i; break; }
            if (info == null) continue;
            int level = -1;
            Matcher n = ATTR_NAME.matcher(name);
            if (n.matches() && !name.endsWith("Shard")) level = roman(n.group(2));
            for (String l : ItemIds.lore(is)) {
                Matcher m = LEVEL.matcher(l);
                if (m.find()) { level = roman(m.group(1)); break; }
            }
            if (level >= 0 && level <= 10 && !Integer.valueOf(level).equals(levels().get(info.id()))) { levels().put(info.id(), level); changed = true; }
        }
        if (changed) {
            try { Files.createDirectories(Config.DIR); Files.writeString(FILE, Config.GSON.toJson(levels)); } catch (Exception ignored) {}
        }
    }

    // ---------------- planning ----------------

    private static double buyPrice(String id) {
        double[] bz = Prices.bazaarRaw(id);
        if (bz != null && bz[1] > 0) return bz[1];
        double bin = Prices.binPrice(id);
        return bin > 0 ? bin : 0;
    }

    /** toMax=false: cheapest next level of every attribute. true: cheapest to reach level 10. */
    public static List<Pick> plan(boolean toMax) {
        List<Pick> out = new ArrayList<>();
        // shards on the Bazaar that no item list told us about: still list them
        for (String id : Prices.BOOK.keySet()) {
            if (!ALL.containsKey(id) && looksLikeShard(id, Prices.nameOf(id))) ALL.put(id, new Info(id, Prices.nameOf(id), "UNKNOWN", null, ""));
        }
        for (Info s : ALL.values()) {
            int[] table = PER_LEVEL.getOrDefault(s.rarity(), PER_LEVEL.get("COMMON"));   // unknown rarity: assume common (shown as "?")
            int cur = levels().getOrDefault(s.id(), 0);
            if (cur >= 10) continue;
            int to = toMax ? 10 : cur + 1;
            int need = 0;
            for (int l = cur; l < to; l++) need += table[l];
            double each = buyPrice(s.id());
            out.add(new Pick(s, cur, to, need, each <= 0 ? -1 : need * each));
        }
        // priced ones first (cheapest per level), then shards nobody sells on the Bazaar
        out.sort((a, b) -> {
            if ((a.cost() < 0) != (b.cost() < 0)) return a.cost() < 0 ? 1 : -1;
            return Double.compare(a.cost() / (a.to() - a.from()), b.cost() / (b.to() - b.from()));
        });
        return out;
    }

    private static String color(String r) {
        return switch (r) { case "UNCOMMON" -> "§a"; case "RARE" -> "§9"; case "EPIC" -> "§5"; case "LEGENDARY" -> "§6"; default -> "§f"; };
    }

    public static Screen screen(Screen parent) {
        return new MenuScreen("Attribute shards", List.of(
                new Tab("Next level", () -> page(false)),
                new Tab("To max", () -> page(true))
        ), 0, parent).searchable();
    }

    private static Page page(boolean toMax) {
        List<Row> rows = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        if (ALL.isEmpty()) footer.add(Prices.loaded() ? "§cNo attribute shards found in Hypixel's item list or the Bazaar. Run /profit report and send it."
                : "§7Prices are still loading (a few seconds after joining). Reopen the menu.");
        int i = 1;
        double total = 0;
        for (Pick p : plan(toMax)) {
            if (i > 300) break;
            if (p.cost() > 0) total += p.cost();
            boolean unknown = p.shard().rarity().equals("UNKNOWN");
            String hunting = switch (p.shard().rarity()) { case "UNCOMMON" -> "5"; case "RARE" -> "10"; case "EPIC" -> "15"; case "LEGENDARY" -> "20"; default -> "0"; };
            String tip = color(p.shard().rarity()) + p.shard().name() + " §7(" + p.shard().rarity().toLowerCase() + ")"
                    + (p.shard().attribute() != null ? "\n§f" + p.shard().attribute() : "")
                    + (!p.shard().effect().isEmpty() ? "\n§7" + p.shard().effect() : "")
                    + "\n§7Level " + p.from() + " → " + p.to() + ": " + p.shardsNeeded() + " shards"
                    + "\n§8Needs Hunting " + hunting + " to syphon";
            rows.add(new Row(new String[]{"§8" + i++ + ". " + color(p.shard().rarity()) + p.shard().name() + (unknown ? " §8(rarity ?)" : ""),
                    "§f" + (p.shard().attribute() != null ? p.shard().attribute() : ""), "§7Lv " + p.from() + "→" + p.to(),
                    "§f" + p.shardsNeeded(), p.cost() < 0 ? "§8not on Bazaar" : "§6" + Fmt.coins(p.cost()),
                    p.cost() < 0 ? "" : "§8" + Fmt.coins(p.cost() / (p.to() - p.from())) + "/lvl"}, tip,
                    List.of(new Action("§eBazaar", "Opens " + p.shard().name() + " in the Bazaar.", () -> MenuScreen.runCommand("bz " + p.shard().name())))));
        }
        footer.add("§7Cheapest first (coins per attribute level, Bazaar buy price).");
        footer.add(levels().isEmpty() ? "§eOpen your Attribute Menu (/am) or Hunting Box once so your current levels are known."
                : "§8Your levels are from the last time you opened the Attribute Menu / Hunting Box.");
        footer.add("§8Search the box above by attribute or effect, e.g. \"Farming Fortune\" or \"Sweep\".");
        return new Page(new String[]{"Shard", "Attribute", "Level", "Shards", "Cost", ""}, new int[]{130, 110, 50, 45, 55, 50}, rows, List.of(), footer);
    }

    private Shards() {}
}
