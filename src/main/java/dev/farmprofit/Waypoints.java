package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Waypoints: coordinates your party posts in chat (Inquisitors, mineshafts... from SkyAssist, SkyHanni or by hand),
 * plus Crystal Hollows locations you've found in this lobby. Shown as a light beam and with arrows on the HUD.
 */
public final class Waypoints {
    private static final Pattern COORDS = Pattern.compile("x:\\s*(-?\\d+)[,\\s]+y:\\s*(-?\\d+)[,\\s]+z:\\s*(-?\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BARE = Pattern.compile("(?<![\\d.])(-?\\d{1,5})\\s+(-?\\d{1,3})\\s+(-?\\d{1,5})(?![\\d.])");
    private static final Pattern FROM = Pattern.compile("^(?:Party|Guild|Co-op) > (?:\\[[^\\]]*\\]\\s*)?(\\w{3,16})[^:]*:\\s*(.*)$");
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
    private static final String[] CH_PLACES = {"Jungle Temple", "Goblin Queen's Den", "Mines of Divan", "Lost Precursor City",
            "Khazad-dûm", "Fairy Grotto", "Dragon's Lair", "Goblin Holdout", "Crystal Nucleus"};

    public record Point(String label, double x, double y, double z, long until, int color, String server) {}

    private static final List<Point> points = new ArrayList<>();
    private static int tick;
    private static String lastPlace = "";

    // ---------------- party chat ----------------

    /** Player chat (party / guild / co-op): coordinates become a waypoint. */
    public static void onPlayerChat(String plain) {
        if (!Config.get().partyWaypoints) return;
        Matcher f = FROM.matcher(plain);
        if (!f.find()) return;
        String rest = f.group(2);
        Matcher m = COORDS.matcher(rest);
        if (!m.find()) { m = BARE.matcher(rest); if (!m.find()) return; }
        String label = rest.replace(m.group(), "").replaceAll("[|]", " ").trim();
        if (label.isEmpty()) label = f.group(1);
        else label = f.group(1) + ": " + label;
        boolean inq = rest.toLowerCase(Locale.ROOT).contains("inquis");
        add(label, Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                inq ? 0xFFAA00 : 0x55FFFF, Config.get().waypointSeconds * 1000L, null);
        final String shared = rest;
        final double sx = Integer.parseInt(m.group(1)) + 0.5, sz = Integer.parseInt(m.group(3)) + 0.5;
        Safe.run("Crystal Hollows places", () -> CrystalMap.onShared(shared, sx, sz));          // "x: y: z: | Jungle Temple" fills the map
        if (inq) Chat.ping();
    }

    public static void add(String label, double x, double y, double z, int color, long lifeMs, String server) {
        points.removeIf(p -> Math.abs(p.x() - x) < 2 && Math.abs(p.z() - z) < 2);
        points.add(new Point(label, x, y, z, lifeMs <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() + lifeMs, color, server));
    }

    /** The waypoints that are active right now (the Crystal Hollows map draws the ones from party chat). */
    static List<Point> active() {
        String srv = server();
        List<Point> out = new ArrayList<>();
        for (Point p : points) if (p.server() == null || p.server().equals(srv)) out.add(p);
        return out;
    }

    /** Clickable "[Share with party]" line. */
    public static void offerShare(String what) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String cmd = String.format(Locale.US, "/pc x: %d, y: %d, z: %d | %s", (int) Math.floor(mc.player.getX()), (int) Math.floor(mc.player.getY()),
                (int) Math.floor(mc.player.getZ()), what);
        if (Config.get().autoShareInquisitor && what.contains("Inquisitor")) { Reflect.call(mc.getConnection(), "sendCommand", cmd.substring(1)); return; }
        Tracker.say(Chat.clickable("§6[SkyAssist] §e" + what + "! §a§l[Share with party]", cmd, "Posts your coordinates in party chat:\n§7" + cmd));
    }

    // ---------------- Crystal Hollows locations ----------------

    private static String server() { return Tracker.tab.getOrDefault("Server", "?"); }

    private static void crystalHollows(Minecraft mc) {
        if (!Config.get().chWaypoints || Tracker.areaName == null || !Tracker.areaName.contains("Hollows") || mc.player == null) return;
        String here = null;
        for (String l : Tracker.sidebarLines) for (String p : CH_PLACES) if (l.contains(p)) here = p;
        if (here == null || here.equals(lastPlace)) { if (here == null) lastPlace = ""; return; }
        lastPlace = here;
        final String place = here;
        if (points.stream().anyMatch(p -> p.label().equals(place) && server().equals(p.server()))) return;
        add(place, mc.player.getX(), mc.player.getY(), mc.player.getZ(), 0xFF55FF, 0, server());
        Tracker.say(Chat.clickable("§d[Crystal Hollows] §fFound §d" + place + "§f, waypoint saved. §a§l[Share]",
                String.format(Locale.US, "/pc x: %d, y: %d, z: %d | %s", (int) mc.player.getX(), (int) mc.player.getY(), (int) mc.player.getZ(), place),
                "Posts it in party chat"));
    }

    // ---------------- drawing ----------------

    public static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        String srv = server();
        points.removeIf(p -> p.until() < now || (p.server() != null && !p.server().equals(srv) && Tracker.areaName != null && Tracker.areaName.contains("Hollows")));
        if (++tick % 20 == 0) crystalHollows(mc);
        if (mc.player == null || tick % (6 * Perf.slow()) != 0) return;
        for (Point p : points) {
            if (p.server() != null && !p.server().equals(srv)) continue;
            var c = Particles.dust(p.color(), 2.0f);
            for (int k = 0; k < 14; k++) Particles.point(c, p.x() + 0.5, p.y() + 1 + k * 1.2, p.z() + 0.5);   // tall beam
        }
    }

    public static void addHudLines(Hud.Lines out) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || points.isEmpty()) return;
        String srv = server();
        List<Point> vis = points.stream().filter(p -> p.server() == null || p.server().equals(srv)).toList();
        if (vis.isEmpty()) return;
        out.add("§b§lWaypoints §7" + vis.size());
        long now = System.currentTimeMillis();
        for (int i = 0; i < Math.min(6, vis.size()); i++) {
            Point p = vis.get(i);
            double dx = p.x() + 0.5 - mc.player.getX(), dz = p.z() + 0.5 - mc.player.getZ(), dy = p.y() - mc.player.getY();
            double target = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = ((target - mc.player.getYRot()) % 360 + 540) % 360 - 180;
            String arrow = ARROWS[(((int) Math.round(rel / 45.0)) % 8 + 8) % 8];
            String left = p.until() == Long.MAX_VALUE ? "" : " §8" + Fmt.clock(p.until() - now);
            out.add(" §f" + arrow + " §7" + String.format(Locale.US, "%.0fm", Math.sqrt(dx * dx + dy * dy + dz * dz)) + " §f" + p.label() + left);
        }
    }

    public static Screen screen(Screen parent) {
        final MenuScreen[] ref = new MenuScreen[1];
        ref[0] = new MenuScreen("Waypoints", List.of(new Tab("Waypoints", () -> {
            List<Row> rows = new ArrayList<>();
            for (Point p : new ArrayList<>(points)) {
                String cmd = String.format(Locale.US, "pc x: %d, y: %d, z: %d | %s", (int) p.x(), (int) p.y(), (int) p.z(), p.label());
                rows.add(new Row(new String[]{"§f" + p.label(), String.format(Locale.US, "§7%d %d %d", (int) p.x(), (int) p.y(), (int) p.z())}, null, List.of(
                        new Action("§aShare", "Post in party chat.", () -> MenuScreen.runCommand(cmd)),
                        new Action("§cRemove", "Delete this waypoint.", () -> { points.remove(p); ref[0].refresh(); }))));
            }
            return new Page(new String[]{"Waypoint", "Coordinates"}, new int[]{220, 150}, rows, List.of(
                    new Action("Clear all", "Remove every waypoint.", () -> { points.clear(); ref[0].refresh(); })),
                    List.of("§8Party coordinates (x: y: z: or three numbers) become waypoints automatically. Crystal Hollows places are saved per lobby."));
        })), 0, parent);
        return ref[0];
    }

    private Waypoints() {}
}
