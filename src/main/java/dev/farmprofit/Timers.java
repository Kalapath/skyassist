package dev.farmprofit;

import java.util.List;
import java.util.Locale;

/**
 * Event timers, each with its own switch: Dark Auction, Dwarven Mines event (live from the tab list / sidebar),
 * Cult of the Fallen Star, Spooky Festival, Traveling Zoo, Season of Jerry, New Year, Hoppity's Hunt, Jacob's contest.
 * SkyBlock time: 1 day = 20 real minutes, 1 month = 31 days, 1 year = 12 months (124 real hours), started 11 Jun 2019.
 */
public final class Timers {
    private static final long EPOCH = 1560275700L;            // SkyBlock year 1, day 1, 00:00
    private static final long DAY = 1200, MONTH = 31 * DAY, YEAR = 12 * MONTH;
    private static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
            "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};
    private static final List<String> MINING_EVENTS = List.of("2X POWDER", "GONE WITH THE WIND", "GOBLIN RAID", "BETTER TOGETHER", "RAFFLE", "MITHRIL GOURMAND");

    private static long now() { return System.currentTimeMillis() / 1000; }

    /** Seconds since the start of the current SkyBlock year, and which year it is. */
    private static long[] yearPos() {
        long e = now() - EPOCH;
        return new long[]{e / YEAR, e % YEAR};
    }

    /** Real time (s) when SkyBlock month m (1-12), day d (1-31) starts in the given year index. */
    private static long at(long yearIndex, int month, int day) { return EPOCH + yearIndex * YEAR + (month - 1) * MONTH + (day - 1) * DAY; }

    /** {start, end} (real seconds) of the current or next run of a yearly event. */
    private static long[] window(int month, int firstDay, int days) {
        long[] y = yearPos();
        long start = at(y[0], month, firstDay), end = start + days * DAY;
        if (now() >= end) { start = at(y[0] + 1, month, firstDay); end = start + days * DAY; }
        return new long[]{start, end};
    }

    private static String describe(long[] w) {
        long n = now();
        if (n >= w[0] && n < w[1]) return "§anow §7(ends in " + Fmt.duration((w[1] - n) * 1000) + ")";
        return "§7in §f" + Fmt.duration((w[0] - n) * 1000);
    }

    /** A yearly event from (month, firstDay) lasting `days`: "now (ends in ...)" or "in ...". */
    private static String yearly(int month, int firstDay, int days) { return describe(window(month, firstDay, days)); }

    // ---------------- bosses: their state is in Hypixel's tab list on their island ----------------

    /** setting field, label, color, tab-line start(s) */
    private static final String[][] BOSSES = {
            {"bossBroodmother", "Broodmother", "§c", "Broodmother:"},
            {"bossProtector", "Endstone Protector", "§5", "Protector:"},
            {"bossDragon", "Ender Dragon", "§d", "Dragon:"},
            {"bossArachne", "Arachne", "§4", "Arachne:"},
            {"bossKuudra", "Kuudra", "§6", "Kuudra:"},
            {"bossVanquisher", "Vanquisher", "§5", "Vanquisher:"},
            {"bossGoldenGoblin", "Golden Goblin", "§6", "Golden Goblin:"}};
    /** label -> {state text, when seen (ms)} */
    private static final java.util.Map<String, Object[]> LAST = new java.util.concurrent.ConcurrentHashMap<>();

    private static boolean on(String field) {
        try { return Config.class.getField(field).getBoolean(Config.get()); } catch (Exception e) { return false; }
    }

    /** Reads boss states from the tab list (call every second). */
    public static void tick() {
        long t = System.currentTimeMillis();
        for (String l : Tracker.tabLines) {
            String line = l.trim();
            for (String[] b : BOSSES) if (line.startsWith(b[3])) LAST.put(b[1], new Object[]{line.substring(b[3].length()).trim(), t});
            for (String extra : Config.get().timerExtraTabLines) {
                if (!extra.isBlank() && line.startsWith(extra.trim())) LAST.put(extra.trim(), new Object[]{line.substring(extra.trim().length()).replaceFirst("^:", "").trim(), t});
            }
        }
    }

    private static void bossLine(Hud.Lines out, String label, String color) {
        Object[] s = LAST.get(label);
        if (s == null) { out.add(" " + color + label + " §8not seen yet (visit its island)"); return; }
        long age = System.currentTimeMillis() - (long) s[1];
        String state = (String) s[0];
        boolean alive = state.toUpperCase(Locale.ROOT).contains("ALIVE") || state.toUpperCase(Locale.ROOT).contains("SPAWNED") || state.contains("!");
        out.add(" " + color + label + " " + (alive ? "§a§l" : "§f") + state + (age > 5000 ? " §8(" + Fmt.duration(age) + " ago)" : ""));
    }

    public static String skyblockDate() {
        long[] y = yearPos();
        int month = (int) (y[1] / MONTH), day = (int) (y[1] % MONTH / DAY) + 1;
        return MONTHS[month] + " " + day + ", Year " + (y[0] + 1);
    }

    public static void addHudLines(Hud.Lines out) {
        Config c = Config.get();
        if (!c.timersPanel) return;
        out.add("§e§lTimers §8" + skyblockDate());
        long n = now();
        if (c.timerDarkAuction) {
            // every hour at :55
            long secOfHour = n % 3600, until = secOfHour < 55 * 60 ? 55 * 60 - secOfHour : 3600 - secOfHour + 55 * 60;
            out.add(" §5Dark Auction " + (secOfHour >= 55 * 60 && secOfHour < 58 * 60 ? "§anow" : "§7in §f" + Fmt.clock(until * 1000)));
        }
        if (c.timerMiningEvent) {
            String ev = null;
            for (List<String> src : List.of(Tracker.tabLines, Tracker.sidebarLines)) for (String l : src) {
                String up = l.toUpperCase(Locale.ROOT);
                for (String e : MINING_EVENTS) if (up.contains(e)) ev = l.trim();
            }
            out.add(" §bMining event " + (ev != null ? "§f" + ev : "§8none seen §7(visit Dwarven Mines)"));
        }
        if (c.timerCult) {
            // days 7, 14, 21, 28 of every month, 00:00-06:00 SkyBlock time (5 real minutes)
            long[] y = yearPos();
            long monthStart = EPOCH + y[0] * YEAR + y[1] / MONTH * MONTH;
            String s = null;
            for (int k = 0; k < 6 && s == null; k++) {
                long ms = monthStart + (long) k * MONTH;
                for (int d : new int[]{7, 14, 21, 28}) {
                    long start = ms + (d - 1) * DAY, end = start + DAY / 4;
                    if (n >= start && n < end) { s = "§anow §7(" + Fmt.clock((end - n) * 1000) + " left)"; break; }
                    if (start > n) { s = "§7in §f" + Fmt.duration((start - n) * 1000); break; }
                }
            }
            out.add(" §dCult of the Fallen Star " + s);
        }
        if (c.timerSpooky) out.add(" §6Spooky Festival " + yearly(8, 29, 3));
        if (c.timerZoo) {
            long[] a = window(4, 1, 3), b = window(10, 1, 3);      // whichever comes (or is running) first
            out.add(" §aTraveling Zoo " + describe(a[1] <= b[1] ? a : b));
        }
        if (c.timerJerry) out.add(" §cSeason of Jerry " + yearly(12, 24, 3));
        if (c.timerNewYear) out.add(" §bNew Year " + yearly(12, 29, 3));
        if (c.timerHoppity) out.add(" §eHoppity's Hunt " + yearly(1, 1, 93));
        if (c.timerJacob) {
            String l = Contests.hudLine();
            if (l != null) out.add(" " + l);
        }
        for (String[] b : BOSSES) if (on(b[0])) bossLine(out, b[1], b[2]);
        for (String extra : c.timerExtraTabLines) if (!extra.isBlank()) bossLine(out, extra.trim(), "§b");
    }

    private Timers() {}
}
