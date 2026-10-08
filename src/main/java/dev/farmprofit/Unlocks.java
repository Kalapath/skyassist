package dev.farmprofit;

import com.google.gson.reflect.TypeToken;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What recipes you can craft: your collection, slayer and skill levels, read from the menus you open
 * (/collection categories, Maddox / slayer menu, /skills, /hotm) and from level-up messages in chat.
 * Compared with each recipe's requirement ("Requires: Diamond VIII", "Zombie Slayer 5", "HotM 5") from the recipe data.
 * Saved in config/skyassist/unlocks.json. Display-only.
 */
public final class Unlocks {
    private static final Path FILE = Config.DIR.resolve("unlocks.json");
    private static Map<String, Integer> levels;

    /** Recipe data says "Requires: Diamond VIII" (collection), "Requires: Zombie Slayer 5", "Requires: HotM 5", or "A & B". */
    private static final Pattern REQ_SLAYER = Pattern.compile("(?i)^(\\w+) Slayer ([IVXLC]+|\\d+)$");
    private static final Pattern REQ_HOTM = Pattern.compile("(?i)^(?:HotM|Heart of the Mountain)(?: Tier)? ([IVXLC]+|\\d+)$");
    private static final java.util.Set<String> SKILLS = java.util.Set.of("farming", "mining", "combat", "foraging", "fishing", "enchanting",
            "alchemy", "taming", "carpentry", "runecrafting", "social", "hunting");
    private static final Pattern REQ_SKILL = Pattern.compile("(?i)^(\\w+) Skill ([IVXLC]+|\\d+)$");
    private static final Pattern REQ_COLLECTION = Pattern.compile("^(.+?)(?: Collection)? ([IVXLC]+|\\d+)$");
    private static final Pattern HOTM_TIER = Pattern.compile("^Tier (\\d+)$");
    private static final Pattern PROGRESS_TO = Pattern.compile("Progress to (.+?) ([IVXLC]+|\\d+):");
    private static final Pattern NAME_LEVEL = Pattern.compile("^(.+?) ([IVXLC]+|\\d+)$");
    private static final Pattern SLAYER_LVL = Pattern.compile("(?i)(\\w+) Slayer:?\\s*LVL (\\d+)");
    private static final Pattern CHAT_COLLECTION = Pattern.compile("COLLECTION LEVEL UP (.+?) (?:[IVXLC]+\\s*[➜→>]\\s*)?([IVXLC]+)$");
    private static final Pattern CHAT_SKILL = Pattern.compile("SKILL LEVEL UP (\\w+) (?:[IVXLC\\d]+\\s*[➜→>]\\s*)?([IVXLC]+|\\d+)$");

    public record Req(String text, String key, int need, int have) {
        /** true = you meet it, false = you don't, null = your level isn't known yet. */
        public Boolean met() { return have < 0 ? null : have >= need; }
    }

    private static Map<String, Integer> levels() {
        if (levels == null) {
            try {
                if (Files.exists(FILE)) levels = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<HashMap<String, Integer>>() {}.getType());
            } catch (Exception ignored) {}
            if (levels == null) levels = new HashMap<>();
        }
        return levels;
    }

    private static void save() {
        try { Files.createDirectories(Config.DIR); Files.writeString(FILE, Config.GSON.toJson(levels())); } catch (Exception ignored) {}
    }

    private static String key(String type, String name) {
        return type.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT).replace("'", "").trim();
    }

    private static boolean put(String key, int level) {
        Integer old = levels().put(key, level);
        return old == null || old != level;
    }

    static int roman(String s) {
        if (s.chars().allMatch(Character::isDigit)) return Integer.parseInt(s);
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }

    /** The recipe's requirement, or null if it has none (or none we understand). */
    public static Req requirement(String id) {
        String hint = CraftCost.HINTS.get(id);
        if (hint == null || !hint.toLowerCase(Locale.ROOT).startsWith("requires")) return null;
        String body = hint.replaceFirst("(?i)^requires:?\\s*", "").trim();
        Req worst = null;          // the one you're furthest from: not met > unknown > met
        for (String part : body.split("&")) {
            Req r = parse(part.trim());
            if (r == null) continue;
            if (worst == null || rank(r) < rank(worst)) worst = r;
        }
        return worst;
    }

    private static int rank(Req r) {
        Boolean m = r.met();
        return m == null ? 1 : m ? 2 : 0;
    }

    private static Req parse(String part) {
        Matcher m;
        String k, text;
        int need;
        part = part.replaceFirst("(?i)^requires:?\\s*", "").trim();
        if (part.contains("Milestone") || part.contains("Museum")) return null;      // garden milestones, museum: not tracked
        if ((m = REQ_SLAYER.matcher(part)).matches()) { k = key("slayer", m.group(1)); need = roman(m.group(2)); text = m.group(1) + " Slayer " + need; }
        else if ((m = REQ_HOTM.matcher(part)).matches()) { k = "hotm"; need = roman(m.group(1)); text = "HotM " + need; }
        else if ((m = REQ_SKILL.matcher(part)).matches()) { k = key("skill", m.group(1)); need = roman(m.group(2)); text = m.group(1) + " " + need; }
        else if ((m = REQ_COLLECTION.matcher(part)).matches() && SKILLS.contains(m.group(1).toLowerCase(Locale.ROOT))) {
            k = key("skill", m.group(1)); need = roman(m.group(2)); text = m.group(1) + " " + need;
        }
        else if ((m = REQ_COLLECTION.matcher(part)).matches()) { k = key("collection", m.group(1)); need = roman(m.group(2)); text = m.group(1) + " Collection " + m.group(2); }
        else return null;
        Integer have = levels().get(k);
        return new Req(text, k, need, have == null ? -1 : have);
    }

    public static int known(String type) {
        int n = 0;
        for (String k : levels().keySet()) if (k.equals(type) || k.startsWith(type + ":")) n++;
        return n;
    }

    // ---------------- reading menus ----------------

    static void scanMenu(String title, List<ItemStack> items) {
        boolean collections = title.contains("Collection"), slayer = title.contains("Slayer"), skills = title.contains("Skill");
        boolean hotm = title.contains("Heart of the Mountain");
        if (!collections && !slayer && !skills && !hotm) return;
        boolean changed = false;
        if (hotm) {                 // tier items: "Tier 7", lore says UNLOCKED once you have it
            int best = -1;
            for (ItemStack is : items) {
                Matcher t = HOTM_TIER.matcher(Tracker.strip(is.getHoverName().getString()).trim());
                if (!t.matches()) continue;
                boolean unlocked = false;
                for (String l : ItemIds.lore(is)) if (l.toUpperCase(Locale.ROOT).contains("UNLOCKED")) unlocked = true;
                if (unlocked) best = Math.max(best, Integer.parseInt(t.group(1)));
            }
            if (best > 0 && put("hotm", best)) save();
            return;
        }
        for (ItemStack is : items) {
            String name = Tracker.strip(is.getHoverName().getString()).trim();
            List<String> lore = ItemIds.lore(is);
            if (slayer) {
                for (String l : lore) {
                    Matcher m = SLAYER_LVL.matcher(l);
                    if (m.find()) changed |= put(key("slayer", m.group(1)), Integer.parseInt(m.group(2)));
                }
                continue;
            }
            String all = String.join("\n", lore);
            String type = collections ? "collection" : "skill";
            // "Progress to Wheat X: 45%" -> Wheat is level IX
            boolean done = false;
            for (String l : lore) {
                Matcher p = PROGRESS_TO.matcher(l);
                if (p.find()) {
                    String n = p.group(1).startsWith("Level") ? NAME_LEVEL.matcher(name).matches() ? name.replaceAll(" \\S+$", "") : name : p.group(1);
                    changed |= put(key(type, n), Math.max(0, roman(p.group(2)) - 1));
                    done = true;
                    break;
                }
            }
            if (done) continue;
            boolean isEntry = collections ? (all.contains("Total Collected") || all.contains("Collected:") || all.contains("Find this item") || all.toLowerCase(Locale.ROOT).contains("maxed"))
                    : all.toLowerCase(Locale.ROOT).contains("max skill level") || all.toLowerCase(Locale.ROOT).contains("maxed");
            if (!isEntry) continue;
            if (collections && all.contains("Find this item")) { changed |= put(key(type, name), 0); continue; }
            Matcher nl = NAME_LEVEL.matcher(name);
            if (nl.matches()) changed |= put(key(type, nl.group(1)), roman(nl.group(2)));
            else if (collections) changed |= put(key(type, name), 0);
        }
        if (changed) save();
    }

    static void onChat(String plain) {
        Matcher c = CHAT_COLLECTION.matcher(plain);
        if (c.find()) { if (put(key("collection", c.group(1)), roman(c.group(2)))) save(); return; }
        Matcher s = CHAT_SKILL.matcher(plain);
        if (s.find()) { if (put(key("skill", s.group(1)), roman(s.group(2)))) save(); return; }
        if (plain.contains("LVL UP") || plain.contains("LEVEL UP")) {
            Matcher sl = SLAYER_LVL.matcher(plain);
            if (sl.find() && put(key("slayer", sl.group(1)), Integer.parseInt(sl.group(2)))) save();
        }
    }

    public static void reset() {
        levels().clear();
        save();
    }

    private Unlocks() {}
}
