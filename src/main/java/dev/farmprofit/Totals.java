package dev.farmprofit;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Lifetime totals per activity (not limited like history). Saved in totals.json */
public final class Totals {
    public static final class Total {
        public long ms;
        public double profit;
        public int sessions;
    }

    private static final Path FILE = Config.DIR.resolve("totals.json");
    private static Map<String, Total> totals;

    public static Map<String, Total> all() {
        if (totals == null) load();
        return totals;
    }

    public static void add(Session s) {
        String type = Tracker.isMiningType(s.type) ? Tracker.MINING : (s.type == null ? Tracker.FARMING : s.type);
        Total t = all().computeIfAbsent(type, k -> new Total());
        t.ms += s.durationMs(0);
        t.profit += s.profit;
        t.sessions++;
        save();
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) {
                totals = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Total>>() {}.getType());
            }
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not read totals (the old file is kept as .broken)", e);
            Config.backupBroken(FILE);
        }
        if (totals == null) {
            // first run: build totals from existing history
            totals = new LinkedHashMap<>();
            for (Session s : History.all()) {
                String type = Tracker.isMiningType(s.type) ? Tracker.MINING : (s.type == null ? Tracker.FARMING : s.type);
                Total t = totals.computeIfAbsent(type, k -> new Total());
                t.ms += s.durationMs(0);
                t.profit += s.profit;
                t.sessions++;
            }
            save();
        }
    }

    private static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Config.write(FILE, Config.GSON.toJson(totals));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save totals", e);
        }
    }

    private Totals() {}
}
