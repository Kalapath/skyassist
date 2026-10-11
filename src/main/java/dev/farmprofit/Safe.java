package dev.farmprofit;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A safety net around everything that runs inside the game's own loops (drawing, ticks, packets, menus).
 * One feature hitting an unexpected situation then skips that frame / tick instead of crashing the game or kicking you from
 * the server, and the problem is remembered: /skyassist status (Problems tab) and /profit report show what failed and where.
 */
public final class Safe {
    /** A feature that failed: where, how often, when, what the error was, and the first few stack lines. */
    public record Fault(String where, int count, long first, long last, String message, String trace) {}

    private static final Map<String, Fault> FAULTS = new ConcurrentHashMap<>();
    private static final AtomicInteger announced = new AtomicInteger();
    private static final int MAX_ANNOUNCE = 4;            // chat notices per launch (the Status page has everything)

    // A feature that fails over and over (every frame, say) is switched off for a minute instead of flooding the log
    // and slowing the game; it then tries again by itself.
    private static final int STORM = 200;                 // this many failures ...
    private static final long STORM_MS = 10_000;          // ... within this long ...
    private static final long PAUSE_MS = 60_000;          // ... pause the feature for this long
    private static final Map<String, long[]> WINDOWS = new ConcurrentHashMap<>();    // where -> {window start, failures in window}
    private static final Map<String, Long> PAUSED = new ConcurrentHashMap<>();       // where -> paused until (ms)

    /** True while a feature that kept failing is switched off for a minute. */
    public static boolean paused(String where) {
        if (PAUSED.isEmpty()) return false;
        Long until = PAUSED.get(where);
        if (until == null) return false;
        if (System.currentTimeMillis() < until) return true;
        PAUSED.remove(where, until);
        return false;
    }

    /** When the pause ends (ms), or 0 if the feature isn't paused. */
    public static long pausedUntil(String where) {
        Long until = PAUSED.get(where);
        return until != null && System.currentTimeMillis() < until ? until : 0;
    }

    /** Runs something; any error is recorded and swallowed. */
    public static void run(String where, Runnable r) {
        if (paused(where)) return;
        try { r.run(); } catch (Throwable t) { report(where, t); }
    }

    /** Runs something that gives a value; on error (or while paused) the fallback is returned. */
    public static <T> T get(String where, Supplier<T> s, T fallback) {
        if (paused(where)) return fallback;
        try {
            T v = s.get();
            return v == null ? fallback : v;
        } catch (Throwable t) {
            report(where, t);
            return fallback;
        }
    }

    public static boolean test(String where, BooleanSupplier s, boolean fallback) {
        if (paused(where)) return fallback;
        try { return s.getAsBoolean(); } catch (Throwable t) { report(where, t); return fallback; }
    }

    /** Records a caught error. Safe to call from any thread. */
    public static void report(String where, Throwable t) {
        if (t instanceof OutOfMemoryError oom) throw oom;      // nothing sensible to do about that
        long now = System.currentTimeMillis();
        String message = describe(t);
        Fault f = FAULTS.compute(where, (k, old) -> old == null
                ? new Fault(where, 1, now, now, message, trace(t))
                : new Fault(where, old.count() + 1, old.first(), now, message, old.trace()));
        try {
            if (f.count() == 1) {
                FarmProfitClient.LOG.warn("{} failed and was skipped: {}", where, message, t);
                announce(where);
            } else if (f.count() % 1000 == 0) {
                FarmProfitClient.LOG.warn("{} has now failed {} times: {}", where, f.count(), message);
            }
            long[] w = WINDOWS.computeIfAbsent(where, k -> new long[2]);
            boolean storm;
            synchronized (w) {
                if (now - w[0] > STORM_MS) { w[0] = now; w[1] = 0; }
                storm = ++w[1] >= STORM;
                if (storm) { w[0] = now; w[1] = 0; }
            }
            if (storm) {
                PAUSED.put(where, now + PAUSE_MS);
                FarmProfitClient.LOG.warn("{} failed {} times within {} s and is paused for {} s: {}", where, STORM, STORM_MS / 1000, PAUSE_MS / 1000, message);
            }
        } catch (Throwable ignored) {}
    }

    public static List<Fault> faults() {
        List<Fault> out = new ArrayList<>(FAULTS.values());
        out.sort(Comparator.comparingLong(Fault::last).reversed());
        return out;
    }

    public static int faultCount() { return FAULTS.size(); }

    public static void clear() { FAULTS.clear(); PAUSED.clear(); WINDOWS.clear(); announced.set(0); }

    /** "NullPointerException: text (Bazaar.hudLines:455)" */
    static String describe(Throwable t) {
        StringBuilder b = new StringBuilder(t.getClass().getSimpleName());
        if (t.getMessage() != null && !t.getMessage().isBlank()) b.append(": ").append(t.getMessage().replace('\n', ' '));
        for (StackTraceElement e : t.getStackTrace()) {
            if (e.getClassName().startsWith("dev.farmprofit")) {
                String cls = e.getClassName().substring(e.getClassName().lastIndexOf('.') + 1);
                b.append(" (").append(cls).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber()).append(')');
                break;
            }
        }
        return b.length() > 300 ? b.substring(0, 300) : b.toString();
    }

    private static String trace(Throwable t) {
        StringBuilder b = new StringBuilder();
        StackTraceElement[] st = t.getStackTrace();
        for (int i = 0; i < Math.min(8, st.length); i++) b.append("  at ").append(st[i]).append('\n');
        return b.toString().trim();
    }

    /** One short chat notice per failing feature (a few per launch), shown on the game thread. */
    private static void announce(String where) {
        if (announced.incrementAndGet() > MAX_ANNOUNCE) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) return;
            ((java.util.concurrent.Executor) mc).execute(() -> {
                try {
                    Tracker.say("§6[SkyAssist] §7" + where + " hit a problem and was skipped. §8Details: /skyassist status");
                } catch (Throwable ignored) {}
            });
        } catch (Throwable ignored) {}
    }

    private Safe() {}
}
