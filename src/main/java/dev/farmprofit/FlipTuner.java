package dev.farmprofit;

import java.util.List;
import java.util.Locale;

/**
 * "Best settings" for flipping: tries many filter combinations on today's Bazaar data and keeps the one that makes the
 * most money for the least babysitting. Effort per order grows with how contested the item is (orders to fight over),
 * how slowly it fills and how many items you'd have to move. Score = profit/h ÷ effort^0.7, so fewer, bigger, calmer
 * flips win unless more orders clearly pay more. Only changes settings; you still place every order yourself.
 */
public final class FlipTuner {
    public record Result(int orders, double profitHour, double used, double effort, String summary) {}

    /** Settings before the last tune, for Undo. */
    private static String[] backup;

    public static boolean canUndo() { return backup != null; }

    private static final String[] ITEM_PRICE = {"0", "0.01%", "0.05%", "0.1%", "0.5%"};
    private static final double[] MARGIN = {1, 2, 3, 5, 8};
    private static final double[] VOLUME = {50_000, 200_000, 1_000_000, 5_000_000};
    private static final int[] ORDERS = {3, 5, 7, 10, 14};

    private record Plan(double profit, double effort, double used, int orders, double kthProfit, double score) {}

    /** Works out and applies the best settings. Returns null if prices aren't loaded yet. */
    public static Result tune() {
        if (!Prices.loaded() || Prices.BOOK.isEmpty()) return null;
        Config c = Config.get();
        String[] before = snapshot(c);
        double budget = c.bzBudget;
        Plan best = null;
        String bestPrice = "0";
        double bestMargin = 2, bestVolume = 200_000;
        Plan many = null;                          // for comparison: the "take everything" plan
        try {
            c.craftFlipSafeOnly = true;            // risky flips cost the most attention: always off
            c.flipMinProfit = "0";
            c.bzMaxPrice = 0;
            for (String price : ITEM_PRICE) for (double margin : MARGIN) for (double volume : VOLUME) {
                c.flipMinItemPrice = price;
                c.bzMinMargin = margin;
                c.bzMinVolume = volume;
                List<Bazaar.Flip> flips = Bazaar.computeFlips();
                for (int k : ORDERS) {
                    Plan p = plan(flips, k, budget);
                    if (p == null) continue;
                    if (best == null || p.score > best.score) { best = p; bestPrice = price; bestMargin = margin; bestVolume = volume; }
                    if (k == 14 && (many == null || p.profit > many.profit)) many = p;
                }
            }
        } finally {
            restore(c, before);
        }
        if (best == null) return new Result(0, 0, 0, 0, "No flips fit your budget right now. Try a bigger budget or later.");
        backup = before;
        c.craftFlipSafeOnly = true;
        c.flipMinItemPrice = bestPrice;
        c.bzMinMargin = bestMargin;
        c.bzMinVolume = bestVolume;
        c.bzMaxPrice = 0;
        c.bzTop = best.orders;
        c.flipMinProfit = best.kthProfit > 0 ? coins(best.kthProfit * 0.8) : "0";        // a bit under the smallest picked flip
        c.craftMinProfit = coins(Math.max(1000, best.profit / Math.max(1, best.orders) * 0.5)); // a craft must be worth half a flip
        c.craftOnlyUnlocked = true;
        c.bzFlipAlert = Math.round(best.profit / Math.max(1, best.orders) * 1.5 / 1000) * 1000.0; // ping only for standouts
        Config.save();
        String summary = String.format(Locale.US, "%d orders, about %s/h using %s of your %s budget.", best.orders,
                Fmt.coins(best.profit), Fmt.coins(best.used), Fmt.coins(budget))
                + (many != null && many.orders > best.orders
                ? " (Running " + many.orders + " orders would make ~" + Fmt.coins(many.profit) + "/h, "
                  + Math.round((many.profit / Math.max(1, best.profit) - 1) * 100) + "% more for " + Math.round((many.effort / Math.max(0.1, best.effort) - 1) * 100) + "% more work.)"
                : "");
        return new Result(best.orders, best.profit, best.used, best.effort, summary);
    }

    /** Greedy: the k best flips by profit/h that still fit the budget. */
    private static Plan plan(List<Bazaar.Flip> flips, int k, double budget) {
        double left = budget, profit = 0, effort = 0, kth = 0;
        int n = 0;
        for (Bazaar.Flip f : flips) {
            if (n >= k) break;
            if (!f.warnings.isEmpty()) continue;
            int qty = (int) Math.min(f.qty, Math.floor(left / f.buyAt));
            if (qty <= 0) continue;
            double p = qty * f.profitEach;
            if (p <= 0) continue;
            left -= qty * f.buyAt;
            profit += p;
            effort += effort(f, qty);
            kth = p;
            n++;
        }
        if (n == 0 || effort <= 0) return null;
        return new Plan(profit, effort, budget - left, n, kth, profit / Math.pow(effort, 0.7));
    }

    /** Attention one order needs: 1 to place and claim, more if it's fought over, slow, or a huge pile of items. */
    private static double effort(Bazaar.Flip f, int qty) {
        double[] b = Prices.BOOK.get(f.id);
        double orders = b != null && b.length > 5 ? b[4] + b[5] : 0;
        double e = 1;
        e += Math.min(3, orders / 200.0);                         // contested: you get outbid / undercut and relist
        e += f.hourlyVolume > 0 ? Math.min(2, qty / (f.hourlyVolume * 2)) : 1;   // slow to fill: more checking back
        e += Math.min(1, qty / 20_000.0);                          // thousands of items: more claiming / inventory juggling
        return e;
    }

    public static boolean undo() {
        if (backup == null) return false;
        restore(Config.get(), backup);
        backup = null;
        Config.save();
        return true;
    }

    private static String[] snapshot(Config c) {
        return new String[]{c.flipMinItemPrice, c.flipMinProfit, c.craftMinProfit, String.valueOf(c.bzMinMargin), String.valueOf(c.bzMinVolume),
                String.valueOf(c.bzMaxPrice), String.valueOf(c.bzTop), String.valueOf(c.craftFlipSafeOnly), String.valueOf(c.craftOnlyUnlocked),
                String.valueOf(c.bzFlipAlert)};
    }

    private static void restore(Config c, String[] s) {
        c.flipMinItemPrice = s[0]; c.flipMinProfit = s[1]; c.craftMinProfit = s[2];
        c.bzMinMargin = Double.parseDouble(s[3]); c.bzMinVolume = Double.parseDouble(s[4]); c.bzMaxPrice = Double.parseDouble(s[5]);
        c.bzTop = Integer.parseInt(s[6]); c.craftFlipSafeOnly = Boolean.parseBoolean(s[7]); c.craftOnlyUnlocked = Boolean.parseBoolean(s[8]);
        c.bzFlipAlert = Double.parseDouble(s[9]);
    }

    /** 1234567 -> "1.2m" style text that the rule boxes understand. */
    private static String coins(double v) {
        if (v >= 1e9) return trim(v / 1e9) + "b";
        if (v >= 1e6) return trim(v / 1e6) + "m";
        if (v >= 1e3) return trim(v / 1e3) + "k";
        return String.valueOf(Math.round(v));
    }

    private static String trim(double v) {
        String s = String.format(Locale.US, "%.1f", v);
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    /** Chat version: /flips tune */
    public static void tuneAndReport() {
        Result r = tune();
        if (r == null) { Tracker.say("§6[Flips] §cPrices are still loading, try again in a few seconds."); return; }
        Config c = Config.get();
        Tracker.say("§6[Flips] §aBest settings applied: §f" + r.summary());
        Tracker.say("§7 min item price §f" + c.flipMinItemPrice + "§7, min margin §f" + c.bzMinMargin + "%§7, min volume §f"
                + Fmt.coins(c.bzMinVolume) + "/week§7, min profit/flip §f" + c.flipMinProfit + "§7, safe only §aon§7. §8/flips untune to undo.");
    }

    private FlipTuner() {}
}
