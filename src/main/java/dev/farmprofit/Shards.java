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
    /** shardsNeeded = for the levels; have = already syphoned into this level + owned in the Hunting Box; toBuy = what's left. */
    public record Pick(Info shard, int from, int to, int shardsNeeded, int have, int toBuy, double cost) {}

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
        int matched = 0;
        StringBuilder dump = new StringBuilder("=== " + title + " (" + new java.util.Date() + ") ===\n");
        for (ItemStack is : items) {
            String id = ItemIds.of(is);
            String name = Tracker.strip(is.getHoverName().getString());
            List<String> lore = ItemIds.lore(is);
            dump.append("\n[").append(name).append("] id=").append(id).append('\n');
            for (String l : lore) dump.append("   ").append(l).append('\n');
            Info info = id != null ? ALL.get(id) : null;
            // "Source: Mist Shard (C2)" names the shard the attribute comes from
            for (String l : lore) {
                Matcher src = SOURCE.matcher(l);
                if (info == null && src.find()) info = shardNamed(src.group(1).trim(), rarityIn(lore), attributeName(name));
            }
            if (info == null) for (Info i : ALL.values()) if (i.name().equals(name) || (i.attribute() != null && name.startsWith(i.attribute()))) { info = i; break; }
            if (info == null) {                       // the description names the shard ("Grove Shard", "Source: ...")
                String all = String.join(" ", lore);
                Info best = null;
                for (Info i : ALL.values()) if (all.contains(i.name()) && (best == null || i.name().length() > best.name().length())) best = i;
                info = best;
            }
            if (info == null) continue;
            matched++;
            if (title.contains("Hunting Box")) {
                // shards you own but haven't used yet: the stack size, or an "Owned / Amount / Stored: N" line
                int owned = is.getCount();
                for (String l : lore) {
                    Matcher m = OWNED.matcher(l);
                    if (m.find()) { owned = Integer.parseInt(m.group(1).replace(",", "")); break; }
                }
                changed |= put(info.id() + "#owned", owned);
                continue;
            }
            // Hypixel's own numbers, progress already included: use them as they are
            for (String l : lore) {
                Matcher a = ATTR_LEVEL.matcher(l);
                if (a.find()) changed |= put(info.id(), Integer.parseInt(a.group(1)));
                Matcher up = TO_LEVEL.matcher(l);
                if (up.find()) changed |= put(info.id() + "#toNext", Integer.parseInt(up.group(1).replace(",", "")));
                Matcher mx = TO_MAX.matcher(l);
                if (mx.find()) changed |= put(info.id() + "#toMax", Integer.parseInt(mx.group(1).replace(",", "")));
                if (l.toUpperCase(Locale.ROOT).contains("MAXED") || l.contains("Max Level")) { changed |= put(info.id(), 10); changed |= put(info.id() + "#toNext", 0); changed |= put(info.id() + "#toMax", 0); }
            }
            if (levels().containsKey(info.id() + "#toNext")) continue;      // got the exact numbers, no need to guess below
            int level = -1, used = -1;
            Matcher n = ATTR_NAME.matcher(name);
            if (n.matches() && !name.endsWith("Shard")) level = roman(n.group(2));
            for (String l : lore) {
                Matcher m = LEVEL.matcher(l);
                if (level < 0 && m.find()) level = roman(m.group(1));
                // progress toward the next level: "12/24" on a line about shards / syphoning / progress
                String low = l.toLowerCase(Locale.ROOT);
                Matcher pr = PROGRESS.matcher(l);
                if (used < 0 && (low.contains("shard") || low.contains("syphon") || low.contains("progress")) && pr.find())
                    used = Integer.parseInt(pr.group(1).replace(",", ""));
                // or a running total ("Syphoned: 37"): work out the level and what's left over from the table
                Matcher tot = TOTAL.matcher(l);
                if (used < 0 && tot.find()) {
                    int total = Integer.parseInt(tot.group(1).replace(",", ""));
                    int[] table = PER_LEVEL.getOrDefault(info.rarity(), PER_LEVEL.get("COMMON"));
                    int lv = 0;
                    while (lv < 10 && total >= table[lv]) { total -= table[lv]; lv++; }
                    if (level < 0) level = lv;
                    used = lv < 10 ? total : 0;
                }
            }
            if (level >= 0 && level <= 10) changed |= put(info.id(), level);
            if (used >= 0) changed |= put(info.id() + "#used", used);
        }
        if (changed) save();
        // 2) what the mod saw, for fixing the reading if Hypixel words things differently
        try {
            Files.createDirectories(Config.DIR);
            Files.writeString(Config.DIR.resolve("shard-menus.txt"), dump.toString(),
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
        if (!title.equals(lastReported)) {
            lastReported = title;
            Tracker.say("§6[Shards] §7Read §f" + title + "§7: recognised §f" + matched + "§7 of " + items.size()
                    + " items. §8(Wrong numbers? Use Edit in /shards, and send config/skyassist/shard-menus.txt)");
        }
    }

    private static final Pattern OWNED = Pattern.compile("(?:Owned|Amount|Stored|You have):?\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROGRESS = Pattern.compile("([\\d,]+)\\s*/\\s*([\\d,]+)");
    private static final Pattern TOTAL = Pattern.compile("(?:Syphoned|Shards used|Total shards):?\\s*([\\d,]+)", Pattern.CASE_INSENSITIVE);

    private static final Pattern SOURCE = Pattern.compile("Source:\\s*(.+? Shard)");
    private static final Pattern RARITY_LINE = Pattern.compile("Rarity:\\s*(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC)");
    private static final Pattern ATTR_LEVEL = Pattern.compile("Attribute Level:\\s*(\\d+)");
    private static final Pattern TO_LEVEL = Pattern.compile("Syphon\\s+([\\d,]+)\\s+shards?\\s+to level up", Pattern.CASE_INSENSITIVE);
    private static final Pattern TO_MAX = Pattern.compile("Syphon\\s+([\\d,]+)\\s+shards?\\s+to max", Pattern.CASE_INSENSITIVE);

    private static String rarityIn(List<String> lore) {
        for (String l : lore) { Matcher m = RARITY_LINE.matcher(l); if (m.find()) return m.group(1); }
        return null;
    }

    /** "Fog Elemental VIII" -> "Fog Elemental". */
    private static String attributeName(String itemName) {
        Matcher n = ATTR_NAME.matcher(itemName);
        return n.matches() ? n.group(1) : itemName;
    }

    /** The shard with this name; if no list knows it yet, it's added (its Bazaar ID found by name). */
    private static Info shardNamed(String shardName, String rarity, String attribute) {
        for (Info i : ALL.values()) {
            if (i.name().equalsIgnoreCase(shardName)) {
                if (i.attribute() == null || "UNKNOWN".equals(i.rarity()))       // fill in what the menu told us
                    ALL.put(i.id(), new Info(i.id(), i.name(), rarity != null ? rarity : i.rarity(), attribute, i.effect()));
                return ALL.get(i.id());
            }
        }
        String id = Prices.idFor(shardName);
        String guess = "SHARD_" + shardName.replace(" Shard", "").toUpperCase(Locale.ROOT).replace(' ', '_').replace("'", "");
        if ((id == null || Prices.bazaarRaw(id) == null) && Prices.bazaarRaw(guess) != null) id = guess;
        if (id == null) id = guess;
        Info info = new Info(id, shardName, rarity != null ? rarity : "UNKNOWN", attribute, "");
        ALL.put(id, info);
        return info;
    }

    private static String lastReported = "";

    private static void save() {
        try { Files.createDirectories(Config.DIR); Files.writeString(FILE, Config.GSON.toJson(levels)); } catch (Exception ignored) {}
    }

    /** Forget all hand-set values (go back to reading the menus). */
    public static void resetManual() {
        levels().keySet().removeIf(k -> k.endsWith("#manual"));
        save();
    }

    /** Manual correction from /shards set: your level and how many you already have toward it. */
    public static String set(String shardName, int level, int have) {
        Info hit = null;
        for (Info i : ALL.values()) {
            if (i.name().equalsIgnoreCase(shardName) || i.name().equalsIgnoreCase(shardName + " Shard")) { hit = i; break; }
            if (hit == null && i.name().toLowerCase(Locale.ROOT).contains(shardName.toLowerCase(Locale.ROOT))) hit = i;
        }
        if (hit == null) return "§cNo shard called \"" + shardName + "\".";
        levels().put(hit.id(), Math.max(0, Math.min(10, level)));
        levels().put(hit.id() + "#used", 0);
        levels().put(hit.id() + "#owned", Math.max(0, have));
        levels().put(hit.id() + "#manual", 1);
        save();
        return "§a" + hit.name() + "§7: level §f" + level + "§7, have §f" + have + "§7 saved.";
    }

    private static boolean put(String key, int value) {
        if (levels().getOrDefault(key.replaceAll("#.*", "") + "#manual", 0) == 1 && !key.endsWith("#manual")) return false;   // your manual value wins
        if (Integer.valueOf(value).equals(levels().get(key))) return false;
        levels().put(key, value);
        return true;
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
            int have;
            Integer exact = levels().get(s.id() + (toMax ? "#toMax" : "#toNext"));
            if (exact != null && levels().getOrDefault(s.id() + "#manual", 0) != 1) {
                need = exact;                                                   // straight from the Attribute Menu
                have = levels().getOrDefault(s.id() + "#owned", 0);             // + unused shards in the Hunting Box
            } else {
                for (int l = cur; l < to; l++) need += table[l];
                // already syphoned into the current level + unused shards in the Hunting Box
                have = levels().getOrDefault(s.id() + "#used", 0) + levels().getOrDefault(s.id() + "#owned", 0);
            }
            int toBuy = Math.max(0, need - have);
            double each = buyPrice(s.id());
            out.add(new Pick(s, cur, to, need, have, toBuy, each <= 0 ? -1 : toBuy * each));
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
                    + (levels().containsKey(p.shard().id() + "#toNext") ? "\n§8Needed: Hypixel's own number from your Attribute Menu (progress included)" : "\n§8Needed: from the wiki table (open /am for exact numbers)")
                    + (p.have() > 0 ? "\n§7Already in your Hunting Box / syphoned: §f" + p.have() : "")
                    + "\n§aTo buy: " + p.toBuy()
                    + "\n§8Needs Hunting " + hunting + " to syphon";
            boolean manual = levels().getOrDefault(p.shard().id() + "#manual", 0) == 1;
            rows.add(new Row(new String[]{"§8" + i++ + ". " + color(p.shard().rarity()) + p.shard().name() + (unknown ? " §8(rarity ?)" : "") + (manual ? " §b✎" : ""),
                    "§f" + (p.shard().attribute() != null ? p.shard().attribute() : ""), "§7Lv " + p.from() + "→" + p.to(),
                    p.have() > 0 ? "§f" + p.toBuy() + " §8(" + p.shardsNeeded() + "−" + p.have() + ")" : "§f" + p.toBuy(),
                    p.toBuy() == 0 ? "§aenough!" : p.cost() < 0 ? "§8not on Bazaar" : "§6" + Fmt.coins(p.cost()),
                    p.cost() < 0 ? "" : "§8" + Fmt.coins(p.cost() / (p.to() - p.from())) + "/lvl"}, tip,
                    List.of(new Action("Edit", "Set your level and how many you already have (if the menu reading got it wrong).",
                            () -> Commands.typeInChat("/shards set " + p.shard().name().replace(" Shard", "") + " " + p.from() + " " + p.have())),
                            new Action("§eBazaar", "Opens " + p.shard().name() + " in the Bazaar and copies the amount to buy (" + p.toBuy()
                            + ") so you can paste it into the amount sign.", () -> {
                        Chat.copy(String.valueOf(p.toBuy()));
                        MenuScreen.runCommand("bz " + p.shard().name());
                        Tracker.say("§6[Shards] §fBuy §a" + p.toBuy() + "x §f" + p.shard().name() + " §7— amount copied, paste it with Ctrl+V in the amount sign.");
                    }))));
        }
        footer.add("§7Cheapest first (coins per attribute level, Bazaar buy price).");
        footer.add(levels().keySet().stream().noneMatch(k -> !k.contains("#")) ? "§eOpen your Attribute Menu (/am) or Hunting Box once so your current levels are known."
                : "§8Your levels are from the last time you opened the Attribute Menu / Hunting Box.");
        footer.add("§8Search the box above by attribute or effect, e.g. \"Farming Fortune\" or \"Sweep\".");
        footer.add("§8Shards already syphoned into a level and unused shards in your Hunting Box are subtracted (open both once).");
        return new Page(new String[]{"Shard", "Attribute", "Level", "To buy", "Cost", ""}, new int[]{125, 105, 45, 65, 55, 45}, rows, List.of(), footer);
    }

    private Shards() {}
}
