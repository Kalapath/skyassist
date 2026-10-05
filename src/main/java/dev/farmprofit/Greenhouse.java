package dev.farmprofit;

import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Greenhouse helper (/greenhouse): pick a mutation, see what you need first, and get a layout to plant.
 * Requirements are from the Hypixel SkyBlock Wiki's mutation table (crops needed around an empty plot).
 */
public final class Greenhouse {
    /** A mutation: what it needs around the empty plot it grows on, and the block that plot must be. */
    public record Mutation(String name, String rarity, String surface, int size, LinkedHashMap<String, Integer> needs, String special) {}

    /** Things you plant yourself (not mutations). */
    private static final Set<String> BASE = Set.of("Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Sugar Cane", "Cactus",
            "Cocoa Beans", "Nether Wart", "Red Mushroom", "Brown Mushroom", "Moonflower", "Sunflower", "Wild Rose",
            "Fermento", "Dead Plant", "Fire");

    public static final Map<String, Mutation> ALL = new LinkedHashMap<>();

    /** Block each regular crop is planted on in the Greenhouse. */
    private static final Map<String, String> BASE_SURFACE = Map.ofEntries(
            Map.entry("Wheat", "Farmland"), Map.entry("Carrot", "Farmland"), Map.entry("Potato", "Farmland"),
            Map.entry("Pumpkin", "Farmland"), Map.entry("Melon", "Farmland"), Map.entry("Cocoa Beans", "Farmland"),
            Map.entry("Moonflower", "Farmland"), Map.entry("Sunflower", "Farmland"), Map.entry("Wild Rose", "Farmland"),
            Map.entry("Fermento", "Farmland"), Map.entry("Sugar Cane", "Sand"), Map.entry("Cactus", "Sand"),
            Map.entry("Nether Wart", "Soul Sand"), Map.entry("Dead Plant", "Soul Sand"),
            Map.entry("Red Mushroom", "Mycelium"), Map.entry("Brown Mushroom", "Mycelium"),
            Map.entry("Fire", "light it with Flint and Steel"));

    /** What a crop or mutation is planted on. */
    public static String surfaceOf(String name) {
        Mutation m = ALL.get(name);
        if (m != null) return m.surface();
        return BASE_SURFACE.getOrDefault(name, "Farmland");
    }

    private static String shortSurface(String s) {
        if (s.startsWith("Soul")) return "Soul Sand";
        if (s.startsWith("Myc")) return "Mycelium";
        if (s.startsWith("End")) return "End Stone";
        if (s.startsWith("light")) return "Fire";
        if (s.startsWith("Farmland or")) return "Farm/Dirt";
        return s;
    }

    private static void m(String name, String rarity, String surface, int size, Object... needs) {
        LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i + 1 < needs.length; i += 2) map.put((String) needs[i], (Integer) needs[i + 1]);
        ALL.put(name, new Mutation(name, rarity, surface, size, map, null));
    }

    static {
        // COMMON
        m("Ashwreath", "COMMON", "Soul Sand", 1, "Nether Wart", 2, "Fire", 2);
        m("Choconut", "COMMON", "Farmland", 1, "Cocoa Beans", 2);
        m("Dustgrain", "COMMON", "Farmland", 1, "Wheat", 2);
        m("Gloomgourd", "COMMON", "Farmland", 1, "Pumpkin", 1, "Melon", 1);
        m("Lonelily", "COMMON", "Farmland or Dirt", 1);                                  // needs NO crops around it
        m("Scourroot", "COMMON", "Farmland", 1, "Potato", 1, "Carrot", 1);
        m("Shadevine", "COMMON", "Farmland", 1, "Cactus", 1, "Sugar Cane", 1);
        m("Veilshroom", "COMMON", "Mycelium", 1, "Red Mushroom", 1, "Brown Mushroom", 1);
        m("Witherbloom", "COMMON", "Soul Sand", 1, "Dead Plant", 4);
        // UNCOMMON
        m("Chocoberry", "UNCOMMON", "Farmland", 1, "Choconut", 6, "Gloomgourd", 2);
        m("Cindershade", "UNCOMMON", "Soul Sand", 1, "Ashwreath", 4, "Witherbloom", 4);
        m("Coalroot", "UNCOMMON", "Farmland", 1, "Ashwreath", 5, "Scourroot", 3);
        m("Creambloom", "UNCOMMON", "Farmland", 1, "Choconut", 8);
        m("Duskbloom", "UNCOMMON", "Farmland", 1, "Moonflower", 2, "Shadevine", 2, "Sunflower", 2, "Dustgrain", 2);
        m("Thornshade", "UNCOMMON", "Farmland", 1, "Wild Rose", 4, "Veilshroom", 4);
        // RARE
        m("Blastberry", "RARE", "Sand", 1, "Chocoberry", 5, "Ashwreath", 3);
        m("Cheesebite", "RARE", "Farmland", 1, "Creambloom", 4, "Fermento", 4);
        m("Chloronite", "RARE", "Farmland", 1, "Coalroot", 6, "Thornshade", 2);
        m("Do-not-eat-shroom", "RARE", "Farmland", 1, "Veilshroom", 4, "Scourroot", 4);
        m("Fleshtrap", "RARE", "Farmland", 1, "Cindershade", 4, "Lonelily", 4);
        m("Magic Jellybean", "RARE", "Sand", 1, "Sugar Cane", 5, "Duskbloom", 3);
        m("Noctilume", "RARE", "Farmland", 2, "Duskbloom", 6, "Lonelily", 6);
        m("Snoozling", "RARE", "Farmland", 3, "Creambloom", 4, "Dustgrain", 3, "Witherbloom", 3, "Duskbloom", 3, "Thornshade", 3);
        m("Soggybud", "RARE", "Farmland", 1, "Melon", 2, "Gloomgourd", 2);
        m("Turtlellini", "RARE", "Farmland", 1, "Soggybud", 4, "Choconut", 4);
        // EPIC
        m("Chorus Fruit", "EPIC", "End Stone", 1, "Chloronite", 5, "Magic Jellybean", 3);
        m("PlantBoy Advance", "EPIC", "Farmland", 2, "Snoozling", 6, "Thunderling", 6);
        m("Puffercloud", "EPIC", "Farmland", 1, "Snoozling", 2, "Do-not-eat-shroom", 6);
        ALL.put("Shellfruit", new Mutation("Shellfruit", "EPIC", "Farmland", 1, new LinkedHashMap<>(Map.of("Turtlellini", 1, "Blastberry", 1)),
                "Explode a Turtlellini with a Blastberry."));
        m("Startlevine", "EPIC", "Farmland", 1, "Blastberry", 4, "Cheesebite", 4);
        m("Stoplight Petal", "EPIC", "Farmland", 1, "Snoozling", 4, "Noctilume", 4);
        m("Thunderling", "EPIC", "Farmland", 1, "Soggybud", 5, "Noctilume", 3);
        m("Zombud", "EPIC", "Soul Sand", 1, "Dead Plant", 4, "Cindershade", 2, "Fleshtrap", 2);
        // LEGENDARY
        m("All-in Aloe", "LEGENDARY", "Sand", 1, "Magic Jellybean", 6, "PlantBoy Advance", 2);
        m("Devourer", "LEGENDARY", "Farmland", 1, "Puffercloud", 4, "Zombud", 4);
        m("Glasscorn", "LEGENDARY", "Sand", 2, "Chloronite", 6, "Startlevine", 6);
        m("Phantomleaf", "LEGENDARY", "Soul Sand", 1, "Chorus Fruit", 4, "Shellfruit", 4);
        m("Timestalk", "LEGENDARY", "End Stone", 1, "Stoplight Petal", 4, "Chorus Fruit", 2, "Shellfruit", 2);
        // Godseed: no fixed recipe. The listed crops are the wiki's no-watering way to get every positive effect.
        ALL.put("Godseed", new Mutation("Godseed", "LEGENDARY", "Farmland", 3, new LinkedHashMap<>(Map.of(
                "Shadevine", 1, "Thornshade", 1, "Gloomgourd", 1, "Cocoa Beans", 1, "Red Mushroom", 1)),
                "Needs EVERY positive crop effect at its highest tier (yield and XP +30%, water +100%) around an empty 3x3. "
                        + "Wiki tip: Shadevine, Thornshade, Gloomgourd, Cocoa Beans and Red or Brown Mushroom do it without watering. "
                        + "A Snoozling placed there shows if it's met."));
        // Jerryflower: not grown from crops at all
        ALL.put("Jerryflower", new Mutation("Jerryflower", "LEGENDARY", "Farmland", 1, new LinkedHashMap<>(),
                "Plant a Fertilized Jerryseed: Jerryseed from a Jerry visitor → Xalx (Crystal Hollows) → reforge it Dirty → Dirt Guy. "
                        + "At growth stage 5 feed it 10 Move Jerry. (It can't go in the Mutations Sack, so mark it with \"I have it\".)"));
    }

    // ---------------- which ones you have ----------------

    private static final Path FILE = Config.DIR.resolve("greenhouse.json");
    private static Set<String> have;
    private static String target;

    public static Set<String> have() {
        if (have == null) {
            try {
                if (Files.exists(FILE)) {
                    Map<String, Object> m = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<Map<String, Object>>() {}.getType());
                    have = new LinkedHashSet<>();
                    boolean fixed = m.get("v") instanceof Number v && v.intValue() >= 2;
                    if (fixed && m.get("have") instanceof List<?> l) for (Object o : l) have.add(String.valueOf(o));   // 6.2.0 marked too many: start over once
                    if (m.get("target") instanceof String t) target = t;
                }
            } catch (Exception ignored) {}
            if (have == null) have = new LinkedHashSet<>();
        }
        return have;
    }

    private static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("v", 2);
            m.put("have", new ArrayList<>(have()));
            m.put("target", target);
            Files.writeString(FILE, Config.GSON.toJson(m));
        } catch (Exception ignored) {}
    }

    /** Mutations seen in your inventory or in any menu (Mutations Sack, Crop Analyzer...) count as unlocked. */
    private static final java.util.regex.Pattern STORED = java.util.regex.Pattern.compile("Stored:\\s*([\\d,]+)");

    /**
     * A mutation counts as unlocked only if you actually have one: in your inventory, or with "Stored: 1+" in a sack.
     * (The Mutations Sack lists every mutation, including ones you've never had, with Stored: 0.)
     */
    static void noticeItems(List<ItemStack> items, boolean inventory) {
        int seen = 0, added = 0, removed = 0;
        StringBuilder dump = new StringBuilder();
        for (ItemStack is : items) {
            String n = Tracker.strip(is.getHoverName().getString()).replaceAll("^\\d+x ", "").replaceAll(" x\\d+$", "").trim();
            if (!ALL.containsKey(n)) continue;
            seen++;
            if (!inventory) {
                Boolean found = foundFromLore(ItemIds.lore(is));
                if (found != null) {
                    if (found && have().add(n)) added++;
                    if (!found && have().remove(n)) removed++;
                    if (!found || !inventory) { dumpItem(dump, n, is); continue; }
                }
            }
            if (!inventory) {
                dump.append("[").append(n).append("]\n");
                for (String l : ItemIds.lore(is)) dump.append("   ").append(l).append('\n');
            }
            if (have().contains(n)) continue;
            if (inventory || ownedFromLore(ItemIds.lore(is))) { have().add(n); added++; }
        }
        if (added > 0 || removed > 0) save();
        if (!inventory && seen > 0) {
            try {
                java.nio.file.Files.createDirectories(Config.DIR);
                java.nio.file.Files.writeString(Config.DIR.resolve("greenhouse-menus.txt"), "=== " + new java.util.Date() + " ===\n" + dump,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception ignored) {}
        }
        if (!inventory && seen >= 3 && Config.get().greenhouseGuide) {
            Tracker.say("§a[Greenhouse] §7Read " + seen + " mutations here" + (added > 0 ? ", §f" + added + " newly unlocked" : "")
                    + (removed > 0 ? ", §c" + removed + " corrected to locked" : "")
                    + "§7. You have §f" + have().size() + "§7/" + ALL.size() + ". §8(Wrong? /greenhouse → I have it / Unmark)");
        }
    }

    /**
     * Mutations Sack (Hypixel's wording): a mutation you haven't found says "LOCKED" and "Discover this mutation in the
     * Greenhouse to unlock it here."; one you have found shows "Stored: 0/64" (any number, even 0).
     * Returns true / false, or null if this item doesn't say either way.
     */
    private static Boolean foundFromLore(List<String> lore) {
        boolean stored = false;
        for (String l : lore) {
            String t = l.trim();
            if (t.equals("LOCKED") || t.startsWith("Discover this mutation") || t.contains("to unlock it here")) return false;
            if (STORED.matcher(t).find()) stored = true;
        }
        return stored ? Boolean.TRUE : null;
    }

    private static void dumpItem(StringBuilder dump, String n, ItemStack is) {
        dump.append("[").append(n).append("]\n");
        for (String l : ItemIds.lore(is)) dump.append("   ").append(l).append('\n');
    }

    /** Lore decides: "Stored: 5" = have it, "Stored: 0" / locked / ??? = don't, "Unlocked" / "Analyzed" = have it. */
    private static boolean ownedFromLore(List<String> lore) {
        for (String l : lore) {
            java.util.regex.Matcher m = STORED.matcher(l);
            if (m.find()) return Long.parseLong(m.group(1).replace(",", "")) > 0;
        }
        for (String l : lore) {
            String low = l.toLowerCase(Locale.ROOT);
            if (low.contains("locked") && !low.contains("unlocked")) return false;
            if (low.contains("???") || low.contains("not discovered") || low.contains("undiscovered") || low.contains("not analyzed")) return false;
        }
        for (String l : lore) {
            String low = l.toLowerCase(Locale.ROOT);
            if (low.contains("unlocked") || low.contains("analyzed") || low.contains("discovered")) return true;
        }
        return false;
    }

    /** "[Sacks] +5 Choconut": you clearly have it. */
    static void noticeName(String name) {
        if (ALL.containsKey(name) && have().add(name)) save();
    }

    private static int invTick;

    /** Mutations you're carrying count as unlocked (checked every 2 seconds). */
    static void noticeInventory(Minecraft mc) {
        if (mc.player == null || ++invTick % 40 != 0) return;
        List<ItemStack> items = new ArrayList<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) { var st = inv.getItem(i); if (!st.isEmpty()) items.add(st); }
        noticeItems(items, true);
    }

    public static void resetMarks() { have().clear(); save(); }

    public static boolean has(String name) { return BASE.contains(name) || have().contains(name); }

    /** Everything you still need to unlock first (deepest first), for one mutation. */
    public static List<String> missingChain(String name) {
        List<String> out = new ArrayList<>();
        collect(name, out, new HashSet<>());
        out.remove(name);
        return out;
    }

    private static void collect(String name, List<String> out, Set<String> seen) {
        if (!seen.add(name)) return;
        Mutation m = ALL.get(name);
        if (m == null) return;
        for (String need : m.needs().keySet()) if (!has(need)) collect(need, out, seen);
        if (!has(name) && !out.contains(name)) out.add(name);
    }

    // ---------------- the layout ----------------

    /** 1x1: the 8 blocks around the center, filled N, S, W, E first, then corners. */
    private static final int[][] RING_1 = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    /** Cells around the center (in x/z offsets) and what to plant on each. */
    public static List<Object[]> layout(Mutation m) {
        List<Object[]> out = new ArrayList<>();
        List<int[]> ring = new ArrayList<>();
        if (m.size() == 1) for (int[] r : RING_1) ring.add(r);
        else {
            int s = m.size();                                   // a 2x2 / 3x3 mutation: the ring of blocks around it
            for (int x = -1; x <= s; x++) for (int z = -1; z <= s; z++) {
                boolean inside = x >= 0 && x < s && z >= 0 && z < s;
                if (!inside) ring.add(new int[]{x, z});
            }
        }
        int i = 0;
        for (var e : m.needs().entrySet()) {
            for (int k = 0; k < e.getValue() && i < ring.size(); k++, i++) out.add(new Object[]{ring.get(i)[0], ring.get(i)[1], e.getKey()});
        }
        return out;
    }

    private static String shortName(String s) {
        if (s.length() <= 8) return s;
        String[] w = s.replace("-", " ").split(" ");
        if (w.length == 1) return s.substring(0, 7) + ".";
        return (w[0].substring(0, Math.min(4, w[0].length())) + " " + w[1]).substring(0, Math.min(8, w[0].length() + 1 + w[1].length()));
    }

    // ---------------- in-world guide ----------------

    private static int tick;

    /** While a target is set and you're in the Garden: the block you look at is the empty plot; marks what goes where. */
    public static void tick(Minecraft mc) {
        if (target == null || !Config.get().greenhouseGuide || mc.player == null || mc.level == null || !Tracker.FARMING.equals(Tracker.area)) return;
        if (++tick % (8 * Perf.slow()) != 0) return;
        Mutation m = ALL.get(target);
        if (m == null) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        BlockPos c = hit.getBlockPos();
        double y = c.getY() + 1.05;
        var white = Particles.dust(0xFFFFFF, 1.6f);
        for (int dx = 0; dx < m.size(); dx++) for (int dz = 0; dz < m.size(); dz++) {
            for (int k = 0; k < 6; k++) Particles.point(white, c.getX() + dx + 0.5, y + k * 0.35, c.getZ() + dz + 0.5);   // leave empty
        }
        var colors = new LinkedHashMap<String, net.minecraft.core.particles.ParticleOptions>();
        int ci = 0;
        for (String need : m.needs().keySet()) colors.put(need, Particles.dust(FILL[ci++ % FILL.length] & 0xFFFFFF, 1.5f));
        for (Object[] cell : layout(m)) {
            double bx = c.getX() + (int) cell[0], bz = c.getZ() + (int) cell[1];
            var col = colors.get((String) cell[2]);
            // a colored square on the block (same color as on the HUD)
            Particles.dense(col, bx + 0.15, y, bz + 0.15, bx + 0.85, y, bz + 0.15, 0.23);
            Particles.dense(col, bx + 0.15, y, bz + 0.85, bx + 0.85, y, bz + 0.85, 0.23);
            Particles.dense(col, bx + 0.15, y, bz + 0.15, bx + 0.15, y, bz + 0.85, 0.23);
            Particles.dense(col, bx + 0.85, y, bz + 0.15, bx + 0.85, y, bz + 0.85, 0.23);
        }
    }

    /** Box colors (ARGB) and matching text colors for each different thing to plant. */
    private static final int[] FILL = {0xD02E8B3A, 0xD0C06010, 0xD02A5DB0, 0xD08A2BB0, 0xD0208A8A, 0xD0A08020};
    private static final String[] TEXT = {"§a", "§6", "§9", "§d", "§3", "§e"};

    /** One cell of the planting grid as drawn on screen. */
    public record Cell(int col, int row, String label, String sub, int fill) {}
    public record Grid(int size, List<Cell> cells) {}

    public static boolean active() {
        return target != null && ALL.containsKey(target) && Config.get().greenhouseGuide && Tracker.FARMING.equals(Tracker.area);
    }

    /** Text part of the Greenhouse panel (title, surface, legend). */
    public static void addHudLines(Hud.Lines out) {
        if (!active()) return;
        Mutation m = ALL.get(target);
        out.add("§a§lGreenhouse §7→ §f" + m.name());
        out.add("§7Plant on: §f" + m.surface() + (m.size() > 1 ? " §8(" + m.size() + "x" + m.size() + ")" : ""));
        if (m.special() != null) { out.add("§7" + m.special()); return; }
        if (m.needs().isEmpty()) { out.add("§7Keep every block around it empty."); return; }
        int i = 0;
        for (var e : m.needs().entrySet()) {
            out.add(TEXT[i % TEXT.length] + "■ §f" + e.getValue() + "x " + e.getKey() + " §7on §f" + shortSurface(surfaceOf(e.getKey()))
                    + (has(e.getKey()) ? "" : " §c(not unlocked)"));
            i++;
        }
        out.add("§f✦ §7= the empty " + shortSurface(m.surface()) + " where it grows  §8(top = the way you face)");
    }

    /** The planting grid, turned so the top is the direction you're facing. */
    public static Grid grid() {
        if (!active()) return null;
        Mutation m = ALL.get(target);
        if (m.special() != null) return null;
        int s = m.size(), n = s + 2;
        Minecraft mc = Minecraft.getInstance();
        float yaw = mc.player != null ? Math.round(mc.player.getYRot() / 90f) * 90f : 180f;
        double fx = -Math.sin(Math.toRadians(yaw)), fz = Math.cos(Math.toRadians(yaw));   // facing
        double rx = -fz, rz = fx;                                                            // to your right
        double c = (s - 1) / 2.0;
        Map<String, Integer> colorOf = new LinkedHashMap<>();
        for (String need : m.needs().keySet()) colorOf.put(need, colorOf.size());
        List<Cell> cells = new ArrayList<>();
        java.util.function.BiFunction<Double, Double, int[]> place = (dx, dz) -> {
            double vx = dx - c, vz = dz - c;
            double sx = vx * rx + vz * rz, sy = -(vx * fx + vz * fz);
            return new int[]{(int) Math.round(sx + (n - 1) / 2.0), (int) Math.round(sy + (n - 1) / 2.0)};
        };
        for (int dx = 0; dx < s; dx++) for (int dz = 0; dz < s; dz++) {
            int[] p = place.apply((double) dx, (double) dz);
            cells.add(new Cell(p[0], p[1], "✦ " + shortName(m.name()), "empty", 0xE0E8E8E8));
        }
        for (Object[] cell : layout(m)) {
            int[] p = place.apply((double) (int) cell[0], (double) (int) cell[1]);
            String need = (String) cell[2];
            cells.add(new Cell(p[0], p[1], shortName(need), shortSurface(surfaceOf(need)), FILL[colorOf.get(need) % FILL.length]));
        }
        return new Grid(n, cells);
    }

    // ---------------- menu ----------------

    public static Screen screen(Screen parent) {
        final MenuScreen[] ref = new MenuScreen[1];
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("Plan", () -> planPage(ref)));
        for (String r : new String[]{"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY"}) tabs.add(new Tab(cap(r), () -> listPage(r, ref)));
        ref[0] = new MenuScreen("Greenhouse helper", tabs, target == null ? 1 : 0, parent).searchable();
        return ref[0];
    }

    private static String cap(String s) { return s.charAt(0) + s.substring(1).toLowerCase(Locale.ROOT); }

    private static Page listPage(String rarity, MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        for (Mutation m : ALL.values()) {
            if (!m.rarity().equals(rarity)) continue;
            List<String> missing = missingChain(m.name());
            String state = has(m.name()) ? "§a✔ have" : missing.isEmpty() ? "§e can make" : "§c needs " + missing.size() + " first";
            StringBuilder needs = new StringBuilder();
            for (var e : m.needs().entrySet()) needs.append(has(e.getKey()) ? "§7" : "§c").append(e.getValue()).append(" ").append(e.getKey())
                    .append(" §8(on ").append(shortSurface(surfaceOf(e.getKey()))).append(")§8, ");
            String req = m.special() != null ? "§7" + m.special() : m.needs().isEmpty() ? "§7nothing around it" : needs.substring(0, needs.length() - 4);
            List<Action> buttons = new ArrayList<>();
            buttons.add(new Action(m.name().equals(target) ? "§a§lPlanned" : "Plan", "Show the layout for " + m.name() + " (in the Plan tab, the HUD and the world).", () -> {
                target = m.name();
                save();
                ref[0].refresh();
            }));
            buttons.add(new Action(has(m.name()) ? "§8Unmark" : "§8I have it", "Mark whether you've unlocked " + m.name()
                    + ". (Ones you hold or see in a menu are marked automatically.)", () -> {
                if (!have().remove(m.name())) have().add(m.name());
                save();
                ref[0].refresh();
            }));
            rows.add(new Row(new String[]{"§f" + m.name() + " §8(on " + shortSurface(m.surface()) + ")", state, req}, "§7Grows on: §f" + m.surface() + (m.size() > 1 ? "\n§7Size: " + m.size() + "x" + m.size() : ""), buttons));
        }
        return new Page(new String[]{"Mutation", "Status", "Needs around it"}, new int[]{110, 90, 230}, rows, List.of(),
                List.of("§8Red = you don't have that one yet. Requirements: Hypixel SkyBlock Wiki."));
    }

    private static Page planPage(MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        List<Action> top = new ArrayList<>();
        if (target == null || !ALL.containsKey(target)) {
            footer.add("§7Pick a mutation in one of the rarity tabs (Plan button).");
            return new Page(new String[]{""}, new int[]{400}, rows, top, footer);
        }
        Mutation m = ALL.get(target);
        top.add(new Action("Stop guide", "Turns off the layout markers and HUD panel.", () -> { target = null; save(); ref[0].refresh(); }));
        top.add(new Action("Reset unlocks", "Forget which mutations you have; open your Mutations Sack again to re-read them.", () -> { resetMarks(); ref[0].refresh(); }));
        List<String> chain = missingChain(m.name());
        if (has(m.name())) rows.add(new Row("§a✔ You already have " + m.name() + "."));
        if (!chain.isEmpty()) {
            rows.add(new Row("§eGet these first, in this order:"));
            int i = 1;
            for (String step : chain) {
                if (step.equals(m.name())) continue;
                Mutation sm = ALL.get(step);
                rows.add(new Row(new String[]{"§f" + i++ + ". " + step, sm == null ? "" : "§7on " + sm.surface() + ", needs " + needsText(sm)}, null,
                        List.of(new Action("Plan this", "Switch the guide to " + step, () -> { target = step; save(); ref[0].refresh(); }))));
            }
        }
        rows.add(new Row("§a§l" + m.name() + " §7(" + cap(m.rarity()) + ", grows on §f" + m.surface() + "§7)"));
        rows.add(new Row("§7Around the empty plot: " + (m.special() != null ? m.special() : needsText(m))));
        footer.add("§7In the Garden, look at the empty " + m.surface() + " block: white = leave empty, colored = what to plant there.");
        footer.add("§8The HUD shows the same layout as a grid that turns with you.");
        return new Page(new String[]{"", ""}, new int[]{170, 260}, rows, top, footer);
    }

    private static String needsText(Mutation m) {
        if (m.needs().isEmpty()) return "nothing (keep it alone)";
        List<String> parts = new ArrayList<>();
        m.needs().forEach((k, v) -> parts.add(v + " " + k + " (on " + shortSurface(surfaceOf(k)) + ")"));
        return String.join(", ", parts);
    }

    private Greenhouse() {}
}
