package dev.farmprofit;

import java.util.Locale;

public final class Fmt {
    public static String coins(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) return "?";
        if ("full".equals(Config.get().numberFormat)) return String.format(Locale.US, "%,.0f", v);
        double a = Math.abs(v);
        // the thresholds sit just below 1k/1M/1B so that 999,999 reads "1.00M", never "1000.0k"
        if (a >= 999_995_000d) return String.format(Locale.US, "%.2fB", v / 1e9);
        if (a >= 999_950d) return String.format(Locale.US, "%.2fM", v / 1e6);
        if (a >= 999.5) return String.format(Locale.US, "%.1fk", v / 1e3);
        return String.format(Locale.US, "%.0f", v);
    }

    public static String num(long v) { return String.format(Locale.US, "%,d", v); }

    public static String duration(long ms) {
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (h > 0) return String.format(Locale.US, "%dh %02dm %02ds", h, m, sec);
        return String.format(Locale.US, "%dm %02ds", m, sec);
    }

    /** Short "12m 30s" / "1h 05m" / "2d 3h" for estimates. */
    public static String brief(long ms) {
        long s = Math.max(0, ms / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return String.format(Locale.US, "%dh %02dm", h, m);
        if (m > 0) return String.format(Locale.US, "%dm %02ds", m, sec);
        return sec + "s";
    }

    public static String clock(long ms) {
        long s = Math.max(0, ms / 1000);
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private Fmt() {}
}
