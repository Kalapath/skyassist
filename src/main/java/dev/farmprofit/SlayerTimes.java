package dev.farmprofit;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How long your slayer bosses take: timed from the moment your boss appears (its "Spawned by: you" tag is seen) to the kill message.
 * Keeps best / average / count per boss (and tier, when the sidebar shows it) in slayer-times.json. Display only.
 */
public final class SlayerTimes {
    public static final class Rec {
        public long best, total, last, lastAt;
        public int count;
    }

    private static final Path FILE = Config.DIR.resolve("slayer-times.json");
    private static final Pattern SIDEBAR_BOSS = Pattern.compile(
            "^(?:Revenant Horror|Tarantula Broodfather|Sven Packmaster|Voidgloom Seraph|Inferno Demonlord|Riftstalker Bloodfiend) ([IVX]+)\\b.*");
    private static Map<String, Rec> data;
    private static long start, lastSeen;
    private static String tier = "";
    private static String lastKey;

    /** Called whenever your boss's name tag is read. The first sighting after a quiet spell starts the clock. */
    static void onBossSeen() {
        if (!Config.get().slayerTime) return;
        long now = System.currentTimeMillis();
        if (start == 0 || now - lastSeen > 15_000) {
            start = now;
            tier = readTier();
        }
        lastSeen = now;
    }

    private static String readTier() {
        for (String l : Tracker.sidebarLines) {
            Matcher m = SIDEBAR_BOSS.matcher(l.trim());
            if (m.matches()) return m.group(1);
        }
        return "";
    }

    /** The kill message arrived. */
    static void onKill(String type) {
        if (!Config.get().slayerTime) { start = 0; return; }
        long now = System.currentTimeMillis();
        if (start == 0 || now - lastSeen > 20_000) { start = 0; return; }       // never saw the boss: can't time it
        long ms = now - start;
        start = 0;
        if (ms < 1500 || ms > 60 * 60_000L) return;
        String key = tier.isEmpty() ? type : type + " " + tier;
        Rec r = all().computeIfAbsent(key, k -> new Rec());
        r.best = r.best == 0 ? ms : Math.min(r.best, ms);
        r.total += ms;
        r.count++;
        r.last = ms;
        r.lastAt = now;
        lastKey = key;
        save();
        Debug.saw("slayer time");
        if (Config.get().slayerTimeChat) {
            Tracker.say("§c[Slayer] §7" + key + " killed in §f" + time(ms)
                    + (r.count > 1 ? " §8(best " + time(r.best) + " · avg " + time(r.total / r.count) + " · " + r.count + " kills)" : " §8(first one timed)"));
        }
    }

    /** "23.4s" / "1m 05.3s" */
    static String time(long ms) {
        if (ms < 60_000) return String.format(Locale.US, "%.1fs", ms / 1000.0);
        return String.format(Locale.US, "%dm %04.1fs", ms / 60_000, (ms % 60_000) / 1000.0);
    }

    /** Combat HUD line for the boss you killed last (for 20 minutes). */
    static void addHudLines(Hud.Lines out) {
        if (!Config.get().slayerTime || lastKey == null) return;
        Rec r = all().get(lastKey);
        if (r == null || System.currentTimeMillis() - r.lastAt > 20 * 60_000L) return;
        out.add("§7Boss time: §f" + time(r.last) + (r.count > 1 ? " §8(avg " + time(r.total / r.count) + ", best " + time(r.best) + ")" : ""));
    }

    public static Map<String, Rec> all() {
        if (data == null) load();
        return data;
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) data = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Rec>>() {}.getType());
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not read slayer-times.json (the old file is kept as .broken)", e);
            Config.backupBroken(FILE);
        }
        if (data == null) data = new LinkedHashMap<>();
    }

    private static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Config.write(FILE, Config.GSON.toJson(data));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save slayer times", e);
        }
    }

    private SlayerTimes() {}
}
