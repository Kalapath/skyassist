package dev.farmprofit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What every online data source is doing: loaded, loading, or failing (and why). Shown on the Status page and in
 * /profit report, so "it shows nothing" always comes with a reason. Fed by Http and by the loaders that do their own downloads.
 */
public final class Health {
    public enum State { WAITING, LOADING, OK, STALE, FAILED, OFF }

    public static final class Source {
        public final String name;
        public final String purpose;
        public volatile String url = "";
        public volatile String detail = "";
        public volatile String error = "";
        /** Why it isn't being used (a setting is off), empty if it is. */
        public volatile String off = "";
        public volatile long lastTry, lastOk;
        public volatile int fails;
        public volatile boolean loading;
        public volatile Runnable retry;

        Source(String name, String purpose) { this.name = name; this.purpose = purpose; }

        public State state() {
            if (!off.isEmpty()) return State.OFF;
            if (loading) return State.LOADING;
            if (lastTry == 0) return State.WAITING;
            if (!error.isEmpty()) return lastOk > 0 ? State.STALE : State.FAILED;
            return State.OK;
        }
    }

    private static final Map<String, Source> SOURCES = Collections.synchronizedMap(new LinkedHashMap<>());

    static {
        // listed in this order on the Status page, even before anything was tried
        declare("Bazaar prices", "Flips, profit values, tooltips");
        declare("Item list", "Item names and NPC prices");
        declare("Lowest BIN", "Auction prices (AH flips, talismans, tooltips)");
        declare("BIN averages", "Spotting inflated auction prices (Safe only)");
        declare("Recipes (NEU repo)", "Craft costs, craft flips, talismans, shards");
        declare("Enchant data", "Enchantment colors and max levels");
        declare("Mayor & election", "Mayor perks, election leader");
        declare("Jacob's contests", "Contest reminders and HUD line");
        declare("Mining events", "Dwarven Mines / Crystal Hollows events");
        declare("Quiz answers", "Dungeon quiz solver");
        declare("Market: mayor history", "/market mayor signals");
        declare("Market: price history", "/market signals, trends and movers");
        declare("Update check", "Newer build on GitHub");
    }

    private static Source declare(String name, String purpose) {
        Source s = new Source(name, purpose);
        SOURCES.put(name, s);
        return s;
    }

    private static Source source(String name) {
        synchronized (SOURCES) {
            Source s = SOURCES.get(name);
            return s != null ? s : declare(name, "");
        }
    }

    /** A request is starting. */
    public static void attempt(String name, String url) {
        Source s = source(name);
        s.url = url == null ? "" : url;
        s.lastTry = System.currentTimeMillis();
        s.off = "";
        s.loading = true;
    }

    /** The request worked and the data was read. detail = a few words ("1,512 products"). */
    public static void ok(String name, String detail) {
        Source s = source(name);
        s.lastOk = System.currentTimeMillis();
        if (s.lastTry == 0) s.lastTry = s.lastOk;
        s.detail = detail == null ? "" : detail;
        s.error = "";
        s.off = "";
        s.fails = 0;
        s.loading = false;
    }

    /** The request or the reading failed. The previous data (if any) stays in use. */
    public static void fail(String name, String error) {
        Source s = source(name);
        if (s.lastTry == 0) s.lastTry = System.currentTimeMillis();
        s.error = error == null || error.isBlank() ? "failed" : error;
        s.fails++;
        s.loading = false;
        FarmProfitClient.LOG.warn("{}: {}{}", name, s.error, s.url.isEmpty() ? "" : " (" + s.url + ")");
    }

    /** Nothing is downloaded because a setting is off ("turned off in the settings"). Cleared by the next attempt. */
    public static void off(String name, String why) {
        Source s = source(name);
        if (!why.equals(s.off)) s.off = why;
    }

    /**
     * How long to wait before the next automatic try: the normal interval, or (while it keeps failing) the shorter retry
     * interval, doubling with each failure up to 8x, so a dead service isn't hammered.
     */
    public static long delay(String name, long normalMs, long failedMs) {
        Source s = source(name);
        if (s.error.isEmpty()) return normalMs;
        return failedMs << Math.min(3, Math.max(0, s.fails - 1));
    }

    /** Lets the Status page offer a Retry button for this source. */
    public static void onRetry(String name, Runnable retry) { source(name).retry = retry; }

    public static List<Source> sources() {
        synchronized (SOURCES) { return new ArrayList<>(SOURCES.values()); }
    }

    public static int failedCount() {
        int n = 0;
        for (Source s : sources()) if (s.state() == State.FAILED || s.state() == State.STALE) n++;
        return n;
    }

    /** Runs every Retry button of the sources that are failing. Returns how many were started. */
    public static int retryFailed() {
        int n = 0;
        for (Source s : sources()) {
            if ((s.state() == State.FAILED || s.state() == State.STALE) && s.retry != null) {
                try { s.retry.run(); n++; } catch (Throwable t) { Safe.report("Retry " + s.name, t); }
            }
        }
        return n;
    }

    private static final java.util.Set<String> told = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static long lastAnnounceCheck;
    private static int announcedTotal;

    /**
     * Says it in chat (a few times per launch) when a data source keeps failing, so "it shows nothing" always comes with a
     * reason; /skyassist status has the details. Call every tick on the game thread.
     */
    public static void announce() {
        long now = System.currentTimeMillis();
        if (now - lastAnnounceCheck < 5_000) return;
        lastAnnounceCheck = now;
        List<Source> bad = new ArrayList<>();
        for (Source s : sources()) {
            State st = s.state();
            if (st == State.OK) told.remove(s.name);
            else if ((st == State.FAILED || st == State.STALE) && s.fails >= 3 && !told.contains(s.name)) bad.add(s);
        }
        if (bad.isEmpty() || announcedTotal >= 3) return;
        for (Source s : bad) told.add(s.name);
        announcedTotal++;
        if (bad.size() == 1) {
            Source s = bad.get(0);
            Tracker.say("§6[SkyAssist] §7" + s.name + " can't be loaded: §c" + s.error + "§7. §8(/skyassist status)");
        } else {
            Tracker.say("§6[SkyAssist] §7" + bad.size() + " data sources can't be loaded (§c" + bad.get(0).error + "§7). Are you offline? §8(/skyassist status)");
        }
    }

    public static String ago(long t) {
        if (t <= 0) return "never";
        long s = Math.max(0, (System.currentTimeMillis() - t) / 1000);
        if (s < 90) return s + "s ago";
        if (s < 5400) return (s / 60) + "m ago";
        if (s < 172800) return (s / 3600) + "h ago";
        return (s / 86400) + "d ago";
    }

    public static String stateText(State st) {
        return switch (st) {
            case OK -> "§a✔ ok";
            case LOADING -> "§e… loading";
            case STALE -> "§e⚠ old data";
            case FAILED -> "§c✖ failed";
            case WAITING -> "§8not started";
            case OFF -> "§8– off";
        };
    }

    /** Plain-text version for the clipboard (no color codes). */
    public static String report() {
        StringBuilder r = new StringBuilder();
        r.append("Data sources:\n");
        for (Source s : sources()) {
            String st = switch (s.state()) { case OK -> "OK    "; case LOADING -> "LOAD  "; case STALE -> "STALE "; case FAILED -> "FAIL  "; case WAITING -> "wait  "; case OFF -> "off   "; };
            r.append("  ").append(st).append(s.name);
            if (s.state() == State.OFF) r.append(" - ").append(s.off);
            else if (!s.detail.isEmpty()) r.append(" - ").append(s.detail);
            if (s.lastOk > 0 && s.state() != State.OFF) r.append(" (loaded ").append(ago(s.lastOk)).append(')');
            if (!s.error.isEmpty()) r.append(" - ").append(s.error).append(" [").append(s.fails).append("x, last try ").append(ago(s.lastTry)).append(']');
            if (!s.error.isEmpty() && !s.url.isEmpty()) r.append("\n        ").append(s.url);
            r.append('\n');
        }
        List<Safe.Fault> faults = Safe.faults();
        r.append("Problems caught: ").append(faults.isEmpty() ? "none" : faults.size()).append('\n');
        for (Safe.Fault f : faults) {
            r.append("  ").append(f.where()).append(" x").append(f.count()).append(" (last ").append(ago(f.last())).append("): ").append(f.message()).append('\n');
            if (!f.trace().isEmpty()) r.append(f.trace().replace("  at ", "      at ")).append('\n');
        }
        return r.toString();
    }

    private Health() {}

    /** Convenience for "x items" style details. */
    static String count(int n, String noun) { return String.format(Locale.US, "%,d %s", n, noun); }
}
