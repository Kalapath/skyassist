package dev.farmprofit;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Position, scale and visibility of every HUD panel (saved in panels.json).
 * Panels: main, bazaar, secrets, contest, suggest. The main panel can have its own spot per activity.
 */
public final class Panels {
    public static final List<String> ALL = List.of("main", "bazaar", "greenhouse", "waypoints", "timers", "secrets", "contest", "suggest");

    public static final class Pos {
        public int x = -1, y = -1;        // -1 = stack under the previous panel
        public double scale = 1.0;
        public boolean hidden;
    }

    private static final Path FILE = Config.DIR.resolve("panels.json");
    private static Map<String, Pos> map;

    public static String title(String id) {
        return switch (id) {
            case "bazaar" -> "Bazaar orders";
            case "greenhouse" -> "Greenhouse planter";
            case "waypoints" -> "Waypoints";
            case "timers" -> "Timers";
            case "secrets" -> "Dungeon secrets";
            case "contest" -> "Jacob's contest";
            case "suggest" -> "Best now";
            default -> "Profit HUD";
        };
    }

    /** Key for this panel: the main panel gets one per activity if that setting is on. */
    public static String key(String id, String activity) {
        if (id.equals("main") && Config.get().perActivityPositions && activity != null) return "main:" + Tracker.normalType(activity);
        return id;
    }

    public static Pos get(String key) {
        if (map == null) load();
        Pos p = map.get(key);
        if (p == null && key.startsWith("main:")) {          // first time for this activity: copy the shared spot
            Pos base = map.get("main");
            p = new Pos();
            if (base != null) { p.x = base.x; p.y = base.y; p.scale = base.scale; p.hidden = base.hidden; }
            else { p.x = Config.get().hudX; p.y = Config.get().hudY; p.scale = Config.get().hudScale; }
            map.put(key, p);
        }
        if (p == null) {
            p = new Pos();
            if (key.equals("main")) { p.x = Config.get().hudX; p.y = Config.get().hudY; p.scale = Config.get().hudScale; }
            map.put(key, p);
        }
        return p;
    }

    /** True if the HUD layout was never saved (first launch). */
    public static boolean isFresh() { return !Files.exists(FILE); }

    public static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Files.writeString(FILE, Config.GSON.toJson(map));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save panels", e);
        }
    }

    /** Ready-made layouts. left / right / split / compact. Returns false for an unknown name. */
    public static boolean preset(String name, int screenW, int screenH) {
        if (map == null) load();
        java.util.List<String> keys = new java.util.ArrayList<>(ALL);
        for (String k : map.keySet()) if (k.startsWith("main:")) keys.add(k);
        int right = Math.max(5, screenW - 190);
        for (String k : keys) {
            Pos p = get(k);
            String id = k.startsWith("main") ? "main" : k;
            p.hidden = false;
            switch (name) {
                case "left" -> { p.scale = 1.0; p.x = id.equals("main") ? 5 : -1; p.y = id.equals("main") ? 5 : -1; }
                case "compact" -> { p.scale = 0.75; p.x = id.equals("main") ? 4 : -1; p.y = id.equals("main") ? 4 : -1; }
                case "right" -> { p.scale = 1.0; p.x = id.equals("main") ? right : -1; p.y = id.equals("main") ? screenH / 3 : -1; }
                case "split" -> {
                    p.scale = 1.0;
                    switch (id) {
                        case "main" -> { p.x = 5; p.y = 5; }
                        case "bazaar" -> { p.x = right; p.y = screenH / 3; }
                        case "secrets" -> { p.x = right; p.y = 5; }
                        default -> { p.x = -1; p.y = -1; }
                    }
                }
                default -> { return false; }
            }
        }
        save();
        return true;
    }

    public static void reset() {
        map = new LinkedHashMap<>();
        save();
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) map = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Pos>>() {}.getType());
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not read panels", e);
        }
        if (map == null) map = new LinkedHashMap<>();
    }

    private Panels() {}
}
