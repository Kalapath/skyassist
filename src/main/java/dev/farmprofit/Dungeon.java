package dev.farmprofit;

import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Dungeon information read from the tab list and sidebar during a run (secrets, crypts, deaths, rooms,
 * puzzles, team, time), plus a log of finished runs. /dungeon opens it as a menu.
 */
public final class Dungeon {
    private static final Pattern SECRETS_PCT = Pattern.compile("^Secrets Found:\\s*([\\d.]+)%");
    private static final Pattern SECRETS_NUM = Pattern.compile("^Secrets Found:\\s*(\\d+)$");
    private static final Pattern NUM = Pattern.compile("^(Crypts|Deaths|Completed Rooms|Opened Rooms|Team Deaths):\\s*(\\d+)");
    private static final Pattern PUZZLE = Pattern.compile("^(.+?):\\s*\\[([✔✖✦])\\]");
    private static final Pattern MEMBER = Pattern.compile("^\\[\\d+\\]\\s*(?:\\S+\\s+)?(\\w{3,16})\\s.*?\\((\\w+)\\s*([IVXL0-9]*|DEAD|EMPTY)\\)");
    private static final Pattern TIME = Pattern.compile("^(?:Time Elapsed|Time):\\s*(.+)$");
    private static final Pattern CLEARED = Pattern.compile("^Cleared:\\s*(\\d+)%");
    private static final Pattern MILESTONE = Pattern.compile("^Milestone:\\s*(.+)$");

    /** What the tab list / sidebar show right now. */
    public record Live(String floor, String time, Integer cleared, Double teamSecretsPct, Integer mySecrets,
                       Integer crypts, Integer deaths, Integer completedRooms, Integer openedRooms, String milestone,
                       List<String[]> puzzles, List<String[]> team) {}

    public static final class Run {
        public long when;
        public String floor, score, rank, time;
        public Integer mySecrets, crypts, deaths;
        public Double teamSecretsPct;
    }

    private static final Path FILE = Config.DIR.resolve("dungeon-runs.json");
    private static List<Run> runs;

    public static boolean inDungeon() { return Tracker.DUNGEONS.equals(Tracker.area); }

    public static Live live() {
        String time = null, milestone = null;
        Integer cleared = null, mySecrets = null, crypts = null, deaths = null, completed = null, opened = null;
        Double pct = null;
        List<String[]> puzzles = new ArrayList<>(), team = new ArrayList<>();
        for (String l : Tracker.tabLines) {
            Matcher m;
            if ((m = SECRETS_PCT.matcher(l)).find()) pct = Double.parseDouble(m.group(1));
            else if ((m = SECRETS_NUM.matcher(l)).find()) mySecrets = Integer.parseInt(m.group(1));
            else if ((m = NUM.matcher(l)).find()) {
                int v = Integer.parseInt(m.group(2));
                switch (m.group(1)) {
                    case "Crypts" -> crypts = v;
                    case "Deaths", "Team Deaths" -> deaths = v;
                    case "Completed Rooms" -> completed = v;
                    default -> opened = v;
                }
            } else if ((m = MILESTONE.matcher(l)).find()) milestone = m.group(1);
            else if ((m = PUZZLE.matcher(l)).find() && !l.startsWith("Puzzles")) {
                String state = switch (m.group(2)) { case "✔" -> "§a✔ done"; case "✖" -> "§c✖ failed"; default -> "§e✦ to do"; };
                puzzles.add(new String[]{m.group(1).trim(), state});
            } else if ((m = MEMBER.matcher(l)).find()) {
                team.add(new String[]{m.group(1), m.group(2) + (m.group(3).isEmpty() ? "" : " " + m.group(3)), l.contains("DEAD") ? "§cdead" : "§aalive"});
            }
        }
        for (String l : Tracker.sidebarLines) {
            Matcher m;
            if ((m = TIME.matcher(l)).find()) time = m.group(1).trim();
            else if ((m = CLEARED.matcher(l)).find()) cleared = Integer.parseInt(m.group(1));
        }
        return new Live(Tracker.dungeonFloor, time, cleared, pct, mySecrets, crypts, deaths, completed, opened, milestone, puzzles, team);
    }

    /** Called when "Team Score" appears at the end of a run. */
    static void recordRun(String score, String rank) {
        Live l = live();
        Run r = new Run();
        r.when = System.currentTimeMillis();
        r.floor = l.floor();
        r.score = score;
        r.rank = rank;
        r.time = l.time();
        r.mySecrets = l.mySecrets();
        r.crypts = l.crypts();
        r.deaths = l.deaths();
        r.teamSecretsPct = l.teamSecretsPct();
        all().add(r);
        while (all().size() > 300) all().remove(0);
        save();
    }

    public static List<Run> all() {
        if (runs == null) {
            try {
                if (Files.exists(FILE)) runs = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<List<Run>>() {}.getType());
            } catch (Exception ignored) {}
            if (runs == null) runs = new ArrayList<>();
        }
        return runs;
    }

    private static void save() {
        try { Files.createDirectories(Config.DIR); Config.write(FILE, Config.GSON.toJson(runs)); } catch (Exception ignored) {}
    }

    private static String v(Object o) { return o == null ? "§8?" : "§f" + o; }

    // ---------------- HUD line ----------------

    /** e.g. "Secrets 12 (45%)  Crypts 3  Deaths 0" on the Catacombs HUD. */
    public static String hudLine() {
        if (!inDungeon() || !Config.get().dungShowRunInfo) return null;
        Live l = live();
        if (l.mySecrets() == null && l.teamSecretsPct() == null && l.crypts() == null) return null;
        return "§7Secrets " + v(l.mySecrets()) + (l.teamSecretsPct() != null ? " §8(" + fmtPct(l.teamSecretsPct()) + " team)" : "")
                + "  §7Crypts " + v(l.crypts()) + "  §7Deaths " + (l.deaths() != null && l.deaths() > 0 ? "§c" + l.deaths() : v(l.deaths()));
    }

    private static String fmtPct(double d) { return String.format(Locale.US, "%.1f%%", d); }

    // ---------------- chat ----------------

    public static void sayLive() {
        if (!inDungeon()) { Tracker.say("§6[Dungeon] §7You're not in a dungeon. §8(/dungeon runs for past runs)"); return; }
        Live l = live();
        Tracker.say("§4§l[Dungeon] §f" + (l.floor() != null ? l.floor() : "Catacombs") + (l.time() != null ? " §7" + l.time() : "")
                + (l.cleared() != null ? " §7cleared §f" + l.cleared() + "%" : ""));
        Tracker.say(" §7Secrets: you " + v(l.mySecrets()) + "§7, team " + (l.teamSecretsPct() != null ? "§f" + fmtPct(l.teamSecretsPct()) : "§8?")
                + "  §7Crypts " + v(l.crypts()) + "  §7Deaths " + v(l.deaths()));
        Tracker.say(" §7Rooms: completed " + v(l.completedRooms()) + "§7, opened " + v(l.openedRooms())
                + (l.milestone() != null ? "  §7Milestone §f" + l.milestone() : ""));
        if (Secrets.roomTotal >= 0) Tracker.say(" §7This room: §f" + Secrets.roomFound + "/" + Secrets.roomTotal + " §7secrets");
        if (!l.puzzles().isEmpty()) {
            StringBuilder sb = new StringBuilder(" §7Puzzles: ");
            for (String[] p : l.puzzles()) sb.append("§f").append(p[0]).append(" ").append(p[1]).append("§7, ");
            Tracker.say(sb.substring(0, sb.length() - 2));
        }
    }

    // ---------------- menu ----------------

    public static Screen screen(int startTab, Screen parent) {
        return new MenuScreen("Dungeon", List.of(
                new Tab("This run", Dungeon::runPage),
                new Tab("Puzzles", Dungeon::puzzlePage),
                new Tab("Team", Dungeon::teamPage),
                new Tab("Past runs", Dungeon::historyPage)
        ), startTab, parent);
    }

    private static Page runPage() {
        List<Row> rows = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        if (!inDungeon()) {
            footer.add("§7You're not in a dungeon right now. Past runs are in the last tab.");
            return new Page(new String[]{"", ""}, new int[]{170, 250}, rows, List.of(), footer);
        }
        Live l = live();
        rows.add(new Row("§7Floor", v(l.floor())));
        rows.add(new Row("§7Time", v(l.time())));
        rows.add(new Row("§7Cleared", l.cleared() != null ? "§f" + l.cleared() + "%" : "§8?"));
        rows.add(new Row("§7Your secrets", v(l.mySecrets())));
        rows.add(new Row("§7Team secrets", l.teamSecretsPct() != null ? "§f" + fmtPct(l.teamSecretsPct()) : "§8?"));
        if (Secrets.roomTotal >= 0) rows.add(new Row("§7This room", "§f" + Secrets.roomFound + "/" + Secrets.roomTotal + " secrets"));
        rows.add(new Row("§7Crypts", v(l.crypts()) + (l.crypts() != null && l.crypts() < 5 ? " §8(5 for full bonus)" : "")));
        rows.add(new Row("§7Deaths", l.deaths() != null && l.deaths() > 0 ? "§c" + l.deaths() : v(l.deaths())));
        rows.add(new Row("§7Rooms completed / opened", v(l.completedRooms()) + " §7/ " + v(l.openedRooms())));
        if (l.milestone() != null) rows.add(new Row("§7Milestone", "§f" + l.milestone()));
        Session s = Tracker.sessions.get(Tracker.DUNGEONS);
        if (s != null) rows.add(new Row("§7This session", "§f" + s.runs + " runs, §6" + Fmt.coins(s.value()) + " §7profit"));
        footer.add("§8Read from Hypixel's tab list and sidebar. \"?\" = that widget isn't shown in your tab list.");
        return new Page(new String[]{"", ""}, new int[]{170, 250}, rows, List.of(), footer);
    }

    private static Page puzzlePage() {
        List<Row> rows = new ArrayList<>();
        for (String[] p : live().puzzles()) rows.add(new Row("§f" + p[0], p[1]));
        return new Page(new String[]{"Puzzle", "State"}, new int[]{200, 150}, rows, List.of(),
                List.of(inDungeon() ? "§8Solvers: Settings → Dungeons." : "§7You're not in a dungeon right now."));
    }

    private static Page teamPage() {
        List<Row> rows = new ArrayList<>();
        for (String[] t : live().team()) rows.add(new Row("§f" + t[0], "§7" + t[1], t[2]));
        return new Page(new String[]{"Player", "Class", ""}, new int[]{160, 140, 80}, rows, List.of(),
                List.of(inDungeon() ? "§8From the tab list." : "§7You're not in a dungeon right now."));
    }

    private static Page historyPage() {
        List<Run> all = all();
        List<Row> rows = new ArrayList<>();
        int n = 0, secrets = 0, secretsN = 0, score = 0;
        for (int i = all.size() - 1; i >= 0; i--) {
            Run r = all.get(i);
            rows.add(new Row("§8" + new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date(r.when)), "§f" + (r.floor != null ? r.floor : "?"),
                    "§e" + r.score + " §7" + r.rank, v(r.time), v(r.mySecrets), v(r.crypts), r.deaths != null && r.deaths > 0 ? "§c" + r.deaths : v(r.deaths)));
            n++;
            try { score += Integer.parseInt(r.score); } catch (Exception ignored) {}
            if (r.mySecrets != null) { secrets += r.mySecrets; secretsN++; }
        }
        List<String> footer = new ArrayList<>();
        if (n > 0) footer.add("§7" + n + " runs, average score §e" + score / n + (secretsN > 0 ? "§7, average secrets §f" + String.format(Locale.US, "%.1f", (double) secrets / secretsN) : ""));
        return new Page(new String[]{"When", "Floor", "Score", "Time", "Secrets", "Crypts", "Deaths"}, new int[]{70, 45, 85, 75, 55, 50, 50}, rows, List.of(), footer);
    }

    private Dungeon() {}
}
