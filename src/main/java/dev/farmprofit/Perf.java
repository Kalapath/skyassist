package dev.farmprofit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Measures how much time each feature takes, for /profit perf, and the Performance mode multiplier. */
public final class Perf {
    private static final Map<String, long[]> TIMES = new ConcurrentHashMap<>();   // name -> {total ns, calls}
    private static long since = System.nanoTime();

    /** Runs a feature and records its time. Errors in one feature never stop the others (they show up in /skyassist status). */
    public static void run(String name, Runnable r) {
        if (Safe.paused(name)) return;
        long t = System.nanoTime();
        try { r.run(); } catch (Throwable e) { Safe.report(name, e); }
        long[] a = TIMES.computeIfAbsent(name, k -> new long[2]);
        a[0] += System.nanoTime() - t;
        a[1]++;
    }

    /** Adds time measured elsewhere (e.g. HUD drawing). */
    public static void add(String name, long nanos) {
        long[] a = TIMES.computeIfAbsent(name, k -> new long[2]);
        a[0] += nanos;
        a[1]++;
    }

    /** 1 normally, 2 in Performance mode (everything that repeats runs half as often / half as dense). */
    public static int slow() { return Config.get().performanceMode ? 2 : 1; }

    public static void show() {
        double secs = Math.max(1, (System.nanoTime() - since) / 1e9);
        Map<String, Double> perSec = new LinkedHashMap<>();
        TIMES.entrySet().stream().sorted((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]))
                .forEach(e -> perSec.put(e.getKey(), e.getValue()[0] / 1e6 / secs));
        double total = perSec.values().stream().mapToDouble(Double::doubleValue).sum();
        Tracker.say("§6§l[SkyAssist] Performance §7(last " + Math.round(secs) + " s, ms of work per second)");
        Tracker.say(" §7Total: " + color(total) + String.format(java.util.Locale.US, "%.2f ms/s", total)
                + " §8(1000 ms/s = one CPU core fully busy; under ~5 is unnoticeable)");
        int i = 0;
        for (var e : perSec.entrySet()) {
            if (i++ >= 12) break;
            Tracker.say(" " + color(e.getValue()) + String.format(java.util.Locale.US, "%6.2f", e.getValue()) + " §7" + e.getKey());
        }
        Tracker.say(" §8Performance mode: " + (Config.get().performanceMode ? "§aON" : "§cOFF") + " §8(Settings → General). /profit perf reset to start over.");
    }

    private static String color(double ms) { return ms < 2 ? "§a" : ms < 8 ? "§e" : "§c"; }

    public static void reset() { TIMES.clear(); since = System.nanoTime(); }

    private Perf() {}
}
