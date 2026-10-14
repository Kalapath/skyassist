package dev.farmprofit;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Crystal Hollows minimap: a small top-down map on the HUD (north is up) while you are in the Hollows.
 * It shows where you are and which way you face, the places you have found in this lobby (Jungle Temple, Goblin Queen's Den,
 * Mines of Divan, Lost Precursor City, Khazad-dûm, Fairy Grotto, Dragon's Lair: they are in different spots in every lobby),
 * your uncovered treasure chests and coordinates your party shared.
 * <p>
 * The Hollows are 621 blocks wide (x and z from 202 to 823): the Jungle is in the north-west, the Mithril Deposits north-east,
 * the Goblin Holdout south-west and the Precursor Remnants south-east, around the Crystal Nucleus in the middle. Everything at
 * Y 64 and below is the Magma Fields (the map is tinted while you are down there).
 * <p>
 * Places are learned only from what the game tells you: the place name in the sidebar when you walk into one, the speech of the
 * NPC that lives there, and coordinates from party chat. Display only: it draws, it never does anything in the game for you.
 */
public final class CrystalMap {
    private static final double MIN = 202, SPAN = 621, MID = 512.5, NUCLEUS = 52;
    /** Jungle, Mithril Deposits, Goblin Holdout, Precursor Remnants (north-west, north-east, south-west, south-east). */
    private static final int[] REGION_COLOR = {0x2E9E5B, 0x2A9FDB, 0x7B4A25, 0x70727A};
    private static final String[] REGION_LETTER = {"J", "M", "G", "P"};

    /** A kind of place: what the sidebar says (lower case letters only), its colour on the map and in the list. */
    private record Kind(String name, String key, String code, int rgb, boolean several) {}

    private static final List<Kind> KINDS = List.of(
            new Kind("Jungle Temple", "jungletemple", "§5", 0xAA00AA, false),
            new Kind("Goblin Queen's Den", "goblinqueen", "§6", 0xFFAA00, false),
            new Kind("Mines of Divan", "minesofdivan", "§a", 0x55FF55, false),
            new Kind("Lost Precursor City", "precursorcity", "§b", 0x55FFFF, false),
            new Kind("Khazad-dûm", "khazad", "§e", 0xFFFF55, false),
            new Kind("Fairy Grotto", "fairygrotto", "§d", 0xFF55FF, true),
            new Kind("Dragon's Lair", "dragonslair", "§c", 0xFF5555, false));

    /** NPCs that live in a place: when one of them speaks, you are standing in it. */
    private static final String[][] NPCS = {
            {"[NPC] Kalhuiki Door Guardian:", "jungletemple"}, {"[NPC] King Yolkar:", "goblinqueen"},
            {"[NPC] Golden Dragon:", "dragonslair"}, {"[NPC] Professor Robot:", "precursorcity"}};

    private static final class Place {
        final Kind kind;
        double minX, maxX, minZ, maxZ;
        /** Pinned by an NPC or a party message: walking around no longer moves it. */
        boolean fixed;

        Place(Kind kind, double x, double z) { this.kind = kind; minX = maxX = x; minZ = maxZ = z; }

        double x() { return (minX + maxX) / 2; }

        double z() { return (minZ + maxZ) / 2; }

        /** While the sidebar says you are inside, the middle of everywhere you have been is the best guess for the place. */
        void extend(double x, double z) {
            if (Math.abs(x - x()) > 120 || Math.abs(z - z()) > 120) return;          // a jump (warp), not walking around inside
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        void pin(double x, double z) { minX = maxX = x; minZ = maxZ = z; fixed = true; }
    }

    private static final List<Place> places = new ArrayList<>();
    private static String placesServer = "";
    private static Place inside;
    private static long lastIn;
    private static int ticks;
    /** For the status page: the map has been drawn in the Hollows. */
    static boolean drawn;

    static int placeCount() { return places.size(); }

    static boolean inHollows() { return Tracker.areaName != null && Tracker.areaName.contains("Hollows"); }

    /** Side of the map in pixels. */
    static int size() { return Math.max(40, Math.min(300, Config.get().crystalMapSize)); }

    /** The part of the Hollows these coordinates are in. */
    static String region(double x, double y, double z) {
        if (y <= 64) return "Magma Fields";
        double dx = x - MID, dz = z - MID;
        if (dx * dx + dz * dz <= 55 * 55) return "Crystal Nucleus";
        return z < MID ? (x < MID ? "Jungle" : "Mithril Deposits") : (x < MID ? "Goblin Holdout" : "Precursor Remnants");
    }

    /** Letters only, lower case: "⏣ Goblin Queen's Den" -> "goblinqueensden". */
    private static String norm(String s) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) if (Character.isLetter(s.charAt(i))) b.append(Character.toLowerCase(s.charAt(i)));
        return b.toString();
    }

    // ================= learning the places =================

    private static Place find(Kind kind, double x, double z) {
        Place best = null;
        double bestD = Double.MAX_VALUE;
        for (Place p : places) {
            if (p.kind != kind) continue;
            double d = Math.hypot(p.x() - x, p.z() - z);
            if (d < bestD) { best = p; bestD = d; }
        }
        if (best == null) return null;
        return kind.several() && bestD > 70 ? null : best;           // there are three fairy grottos, every other place exists once
    }

    private static Place remember(Kind kind, double x, double z) {
        Place p = find(kind, x, z);
        if (p == null) {
            p = new Place(kind, x, z);
            places.add(p);
            Debug.saw("crystal hollows place");
        }
        return p;
    }

    /** Every tick; looks at the sidebar about twice a second. */
    static void tick(Minecraft mc) {
        Config c = Config.get();
        if (!c.crystalMap || mc.player == null || ++ticks % 10 != 0) return;
        long now = System.currentTimeMillis();
        if (!inHollows()) {
            inside = null;
            if (!places.isEmpty() && now - lastIn > 120_000) places.clear();       // gone for a while: the next visit is a new lobby
            return;
        }
        lastIn = now;
        String server = Tracker.tab.getOrDefault("Server", "?");
        if (!server.equals("?") && !server.equals(placesServer)) {                 // another lobby has its places somewhere else
            places.clear();
            inside = null;
            placesServer = server;
        }
        if (!c.crystalMapPlaces) return;
        Kind here = null;
        for (String line : Tracker.sidebarLines) {
            String n = norm(line);
            for (Kind k : KINDS) if (n.contains(k.key())) here = k;
        }
        if (here == null) { inside = null; return; }
        if (inside == null || inside.kind != here) inside = remember(here, mc.player.getX(), mc.player.getZ());
        if (!inside.fixed) inside.extend(mc.player.getX(), mc.player.getZ());
    }

    /** Game chat: an NPC of a place speaking means you are in that place. */
    static void onChat(String plain) {
        Config c = Config.get();
        if (!c.crystalMap || !c.crystalMapPlaces || !plain.startsWith("[NPC] ") || !inHollows()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (String[] n : NPCS) {
            if (!plain.startsWith(n[0])) continue;
            for (Kind k : KINDS) {
                if (!k.key().equals(n[1])) continue;
                Place p = remember(k, mc.player.getX(), mc.player.getZ());
                if (!p.fixed) p.pin(mc.player.getX(), mc.player.getZ());
                return;
            }
        }
    }

    /** Party chat with coordinates ("x: 1 y: 2 z: 3 | Jungle Temple"): if it names a place, that is where it is. */
    static void onShared(String text, double x, double z) {
        Config c = Config.get();
        if (!c.crystalMap || !c.crystalMapPlaces || !inHollows()) return;
        if (x < MIN || x > MIN + SPAN || z < MIN || z > MIN + SPAN) return;
        String n = norm(text);
        for (Kind k : KINDS) {
            if (!n.contains(k.key())) continue;
            Place p = remember(k, x, z);
            if (!p.fixed) p.pin(x, z);
            return;
        }
    }

    // ================= the HUD panel =================

    /**
     * The text of the panel: a title line, then empty lines that leave room for the map (it is drawn over them), then the list
     * of places. While you are editing the HUD (chat open) the panel is shown even outside the Hollows, so you can place it.
     */
    static void addHudLines(Hud.Lines out) {
        Config c = Config.get();
        Minecraft mc = Minecraft.getInstance();
        if (!c.crystalMap || mc.player == null) return;
        boolean here = inHollows();
        if (!here && !HudRenderer.editMode) return;
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        out.add("§b§lCrystal Hollows §7· §f" + (here ? region(px, py, pz) + " §8Y " + (int) Math.floor(py) : "preview"));
        int lh = Math.max(8, Math.min(20, c.hudLineHeight));
        int rows = (size() + 4 + lh - 1) / lh;
        for (int i = 0; i < rows; i++) out.add("");
        if (here && c.crystalMapPlaces && c.crystalMapNames) {
            List<Place> sorted = new ArrayList<>(places);
            sorted.sort(Comparator.comparingDouble(p -> Math.hypot(p.x() - px, p.z() - pz)));
            for (Place p : sorted) {
                out.add(p.kind.code() + "■ §f" + p.kind.name() + " §7" + DianaBurrows.arrow(mc, p.x(), p.z()) + " "
                        + Math.round(Math.hypot(p.x() - px, p.z() - pz)) + "m");
            }
        }
    }

    // ================= drawing =================

    private static void fill(Object g, int x1, int y1, int x2, int y2, int color) { Reflect.call(g, "fill", x1, y1, x2, y2, color); }

    private static int mapPos(double world, int size) {
        return (int) Math.round((Math.max(MIN, Math.min(MIN + SPAN, world)) - MIN) / SPAN * (size - 1));
    }

    /** A square marker (2 * half + 1 pixels wide) with a black edge, at a world position. */
    private static void dot(Object g, int x, int y, int size, double wx, double wz, int half, int color) {
        int px = x + mapPos(wx, size), pz = y + mapPos(wz, size);
        fill(g, px - half - 1, pz - half - 1, px + half + 2, pz + half + 2, 0xFF000000);
        fill(g, px - half, pz - half, px + half + 1, pz + half + 1, color);
    }

    /** Draws the map with its top-left corner at x, y (called by the HUD for the panel's own area). */
    static void draw(Object g, Minecraft mc, int x, int y, boolean hidden) {
        Safe.run("Crystal Hollows map", () -> drawMap(g, mc, x, y, hidden));
    }

    private static void drawMap(Object g, Minecraft mc, int x, int y, boolean hidden) {
        if (mc.player == null) return;
        Config c = Config.get();
        int s = size(), h = s / 2;
        boolean here = inHollows();
        if (here) drawn = true;
        if (hidden) { fill(g, x, y, x + s, y + s, 0x40808080); return; }
        int base = Math.max(20, Math.min(100, c.crystalMapOpacity)) * 255 / 100 << 24;

        // black edge around the map, then the four regions with the Crystal Nucleus where they meet
        fill(g, x - 1, y - 1, x + s + 1, y, 0xFF000000);
        fill(g, x - 1, y + s, x + s + 1, y + s + 1, 0xFF000000);
        fill(g, x - 1, y, x, y + s, 0xFF000000);
        fill(g, x + s, y, x + s + 1, y + s, 0xFF000000);
        fill(g, x, y, x + h, y + h, base | REGION_COLOR[0]);
        fill(g, x + h, y, x + s, y + h, base | REGION_COLOR[1]);
        fill(g, x, y + h, x + h, y + s, base | REGION_COLOR[2]);
        fill(g, x + h, y + h, x + s, y + s, base | REGION_COLOR[3]);
        fill(g, x + h, y, x + h + 1, y + s, 0x60000000);
        fill(g, x, y + h, x + s, y + h + 1, 0x60000000);
        int r = Math.max(3, (int) Math.round(NUCLEUS / SPAN * s));
        for (int dy = -r; dy <= r; dy++) {
            int hw = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            fill(g, x + h - hw, y + h + dy, x + h + hw + 1, y + h + dy + 1, base | 0x6B2D9B);
        }
        if (s >= 70) {                                                  // which region is which
            int lx = h / 2 - 3, ly = h / 2 - 4;
            for (int i = 0; i < 4; i++) {
                Reflect.call(g, "text", mc.font, REGION_LETTER[i], x + (i % 2) * h + lx, y + (i / 2) * h + ly, 0x90FFFFFF, false);
            }
        }
        if (here && mc.player.getY() <= 64) fill(g, x, y, x + s, y + s, 0x38FF5A00);        // down in the Magma Fields

        // treasure chests, then coordinates from your party, then the places, and you on top
        if (c.crystalMapChests) {
            for (double[] chest : PowderChests.points()) {
                double left = chest[2];
                dot(g, x, y, s, chest[0], chest[1], 1, left > 30_000 ? 0xFF40FF40 : left > 10_000 ? 0xFFFFE040 : 0xFFFF3030);
            }
        }
        for (Waypoints.Point p : Waypoints.active()) if (p.server() == null) dot(g, x, y, s, p.x(), p.z(), 1, 0xFFFFFFFF);
        if (c.crystalMapPlaces) {
            int half = s >= 140 ? 3 : 2;
            for (Place p : places) dot(g, x, y, s, p.x(), p.z(), half, 0xFF000000 | p.kind.rgb());
        }
        double wx = here ? mc.player.getX() : MID, wz = here ? mc.player.getZ() : MID;       // (the preview puts you in the middle)
        arrow(g, x + mapPos(wx, s), y + mapPos(wz, s), Math.toRadians(mc.player.getYRot()));
    }

    /**
     * You: a white arrow with a black edge, turned the way you face (yaw 0 = south = down the map), built from short horizontal
     * runs of pixels (the edge first, then the body on top).
     */
    private static void arrow(Object g, int px, int pz, double yaw) {
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);              // forward on the map
        double rx = -fz, rz = fx;                                    // to your right
        double[][] shape = {{6.0, -4.0, 4.6}, {4.5, -2.8, 3.0}};     // {tip, back, half width at the back}: the edge, then the body
        for (int pass = 0; pass < 2; pass++) {
            double tip = shape[pass][0], back = shape[pass][1], half = shape[pass][2];
            int color = pass == 0 ? 0xFF000000 : 0xFFFFFFFF;
            for (int oy = -7; oy <= 7; oy++) {
                int start = Integer.MIN_VALUE;
                for (int ox = -7; ox <= 8; ox++) {
                    boolean in = false;
                    if (ox < 8) {
                        double u = ox * fx + oy * fz, v = ox * rx + oy * rz;       // along your heading, and sideways
                        in = u >= back && u <= tip && Math.abs(v) <= (tip - u) * half / (tip - back);
                    }
                    if (in && start == Integer.MIN_VALUE) {
                        start = ox;
                    } else if (!in && start != Integer.MIN_VALUE) {
                        fill(g, px + start, pz + oy, px + ox, pz + oy + 1, color);
                        start = Integer.MIN_VALUE;
                    }
                }
            }
        }
    }

    private CrystalMap() {}
}
