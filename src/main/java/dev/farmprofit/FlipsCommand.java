package dev.farmprofit;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.List;
import java.util.Locale;

/** /flips - bazaar flip finder, planner, order tracker and profit log. */
final class FlipsCommand {

    static LiteralArgumentBuilder<FabricClientCommandSource> build() {
        return ClientCommands.literal("flips")
                .executes(ctx -> { MenuScreen.open(() -> ProfitMenus.flips(0, null)); return 1; })
                .then(ClientCommands.literal("chat").executes(ctx -> { top(Config.get().bzTop); return 1; }))
                .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 50))
                        .executes(ctx -> { top(IntegerArgumentType.getInteger(ctx, "count")); return 1; }))
                .then(ClientCommands.literal("plan")
                        .executes(ctx -> { MenuScreen.open(() -> ProfitMenus.flips(1, null)); return 1; })
                        .then(ClientCommands.argument("items", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> { plan(IntegerArgumentType.getInteger(ctx, "items")); return 1; })))
                .then(ClientCommands.literal("orders").executes(ctx -> { MenuScreen.open(() -> ProfitMenus.flips(4, null)); return 1; }))
                .then(ClientCommands.literal("craft").executes(ctx -> { MenuScreen.open(() -> ProfitMenus.flips(2, null)); return 1; }))
                .then(ClientCommands.literal("craftah").executes(ctx -> { MenuScreen.open(() -> ProfitMenus.flips(3, null)); return 1; }))
                .then(ClientCommands.literal("log").executes(ctx -> { MenuScreen.open(() -> ProfitMenus.flips(5, null)); return 1; }))
                .then(ClientCommands.literal("remove")
                        .then(ClientCommands.argument("number", IntegerArgumentType.integer(1))
                                .executes(ctx -> { remove(IntegerArgumentType.getInteger(ctx, "number")); return 1; })))
                .then(ClientCommands.literal("clear").executes(ctx -> {
                    Bazaar.state().orders.clear();
                    Bazaar.save();
                    Tracker.say("§6[Flips] §7Order list cleared (your profit log is kept).");
                    return 1;
                }))
                .then(ClientCommands.literal("hud").executes(ctx -> {
                    Config.get().bazaarHud = !Config.get().bazaarHud;
                    Config.save();
                    Tracker.say("§6[Flips] §7Bazaar HUD " + (Config.get().bazaarHud ? "§aon" : "§coff"));
                    return 1;
                }))
                .then(ClientCommands.literal("tune").executes(ctx -> { FlipTuner.tuneAndReport(); return 1; }))
                .then(ClientCommands.literal("untune").executes(ctx -> {
                    Tracker.say(FlipTuner.undo() ? "§6[Flips] §7Settings restored." : "§6[Flips] §7Nothing to undo.");
                    return 1; }))
                .then(ClientCommands.literal("settings").executes(ctx -> { settings(); SettingsScreen.requestOpen("Bazaar"); return 1; }))
                .then(ClientCommands.literal("set")
                        .then(ClientCommands.argument("setting", StringArgumentType.word())
                                .then(ClientCommands.argument("value", StringArgumentType.word()).executes(ctx -> {
                                    set(StringArgumentType.getString(ctx, "setting"), StringArgumentType.getString(ctx, "value"));
                                    return 1;
                                }))));
    }

    private static boolean pricesReady() {
        if (Prices.BOOK.isEmpty()) {
            Tracker.say("§6[Flips] §7Bazaar prices are still loading, try again in a few seconds.");
            Prices.refresh();
            return false;
        }
        return true;
    }

    private static void top(int count) {
        if (!pricesReady()) return;
        List<Bazaar.Flip> flips = Bazaar.computeFlips();
        if (flips.isEmpty()) {
            Tracker.say("§6[Flips] §7No flips match your settings. Lower §f/flips set minvolume§7 or §fminmargin§7.");
            return;
        }
        Tracker.say("§6§l[Flips] §7Best of §f" + flips.size() + "§7 flips (budget §6" + Fmt.coins(Config.get().bzBudget)
                + "§7, tax " + Config.get().bzTax + "%). §8Hover for details, click to open.");
        for (int i = 0; i < Math.min(count, flips.size()); i++) Tracker.say(Bazaar.flipLine(i + 1, flips.get(i)));
    }

    /** Splits the budget across the best few flips, capped by how much each realistically trades in an hour. */
    /** The plan as a list: best safe flips, budget split evenly, capped by realistic hourly volume. */
    static List<Bazaar.Flip> planList(int n) {
        Config c = Config.get();
        List<Bazaar.Flip> picks = Bazaar.computeFlips().stream().filter(f -> f.warnings.isEmpty()).limit(n).toList();
        List<Bazaar.Flip> out = new java.util.ArrayList<>();
        if (picks.isEmpty()) return out;
        double per = c.bzBudget / picks.size();
        for (Bazaar.Flip f : picks) {
            int qty = (int) Math.floor(Math.min(Math.min(per / f.buyAt, f.hourlyVolume * c.bzShare / 100.0), 71680));
            if (qty <= 0) continue;
            f.qty = qty;
            f.profitHour = qty * f.profitEach;
            out.add(f);
        }
        return out;
    }

    private static void plan(int n) {
        if (!pricesReady()) return;
        Config c = Config.get();
        List<Bazaar.Flip> picks = Bazaar.computeFlips().stream().filter(f -> f.warnings.isEmpty()).limit(n).toList();
        if (picks.isEmpty()) { Tracker.say("§6[Flips] §7Nothing to plan with these settings."); return; }
        double per = c.bzBudget / picks.size(), totalCost = 0, totalProfit = 0;
        Tracker.say("§6§l[Flips] Plan §7(" + picks.size() + " items, budget §6" + Fmt.coins(c.bzBudget) + "§7):");
        int i = 1;
        for (Bazaar.Flip f : picks) {
            int qty = (int) Math.floor(Math.min(Math.min(per / f.buyAt, f.hourlyVolume * c.bzShare / 100.0), 71680));
            if (qty <= 0) continue;
            totalCost += qty * f.buyAt;
            totalProfit += qty * f.profitEach;
            Bazaar.Flip shown = f;
            shown.qty = qty;
            shown.profitHour = qty * f.profitEach;
            Tracker.say(Bazaar.flipLine(i++, shown));
        }
        Tracker.say("§7Uses §6" + Fmt.coins(totalCost) + "§7, expected profit §6" + Fmt.coins(totalProfit)
                + "§7. Place the buy orders in-game; the mod tracks them from chat.");
    }

    private static void orders() {
        var orders = Bazaar.state().orders;
        if (orders.isEmpty()) {
            Tracker.say("§6[Flips] §7No open orders. Place one in the Bazaar and it shows up here automatically.");
            return;
        }
        Tracker.say("§6§l[Flips] Your orders:");
        for (int i = 0; i < orders.size(); i++) Tracker.say("§8" + (i + 1) + "." + Bazaar.orderLine(orders.get(i)));
        Tracker.say("§8Stale entry? /flips remove <number>");
    }

    private static void log() {
        var log = Bazaar.state().log;
        double today = Bazaar.profitSince(Bazaar.startOfDay()), all = Bazaar.profitSince(0);
        Tracker.say("§6§l[Flips] Profit §7today §6" + Fmt.coins(today) + "§7, all-time §6" + Fmt.coins(all)
                + "§7 (" + log.size() + " flips), orders placed today §f" + Fmt.coins(Bazaar.tradedToday()));
        var fmt = new java.text.SimpleDateFormat("dd.MM HH:mm");
        for (int i = 0; i < Math.min(10, log.size()); i++) {
            var e = log.get(i);
            Tracker.say("§8" + fmt.format(new java.util.Date(e.time)) + " §f" + e.qty + "x " + e.name + " §7"
                    + Bazaar.Fmt1(e.buy) + " → " + Bazaar.Fmt1(e.sell) + " " + (e.profit >= 0 ? "§a+" : "§c") + Fmt.coins(e.profit));
        }
    }

    private static void remove(int number) {
        var orders = Bazaar.state().orders;
        if (number > orders.size()) { Tracker.say("§6[Flips] §7No order #" + number + "."); return; }
        var o = orders.remove(number - 1);
        Bazaar.save();
        Tracker.say("§6[Flips] §7Removed " + o.qty + "x " + o.name + ".");
    }

    private static void settings() {
        Config c = Config.get();
        Tracker.say("§6§l[Flips] Settings §8(/flips set <name> <value>)");
        Tracker.say(" §7budget §f" + Fmt.coins(c.bzBudget) + "  §7minvolume §f" + Fmt.coins(c.bzMinVolume) + "/week"
                + "  §7minmargin §f" + c.bzMinMargin + "%");
        Tracker.say(" §7maxprice §f" + (c.bzMaxPrice > 0 ? Fmt.coins(c.bzMaxPrice) : "none") + "  §7tax §f" + c.bzTax + "%"
                + "  §7share §f" + c.bzShare + "%");
        Tracker.say(" §7alert §f" + (c.bzFlipAlert > 0 ? Fmt.coins(c.bzFlipAlert) + "/h" : "off") + "  §7top §f" + c.bzTop
                + "  §7sound §f" + (c.bzSound ? "on" : "off") + "  §7safe §f" + (c.craftFlipSafeOnly ? "on" : "off"));
        Tracker.say(" §7minprice §f" + c.flipMinItemPrice + "  §7minprofit §f" + c.flipMinProfit + "  §7craftprofit §f" + c.craftMinProfit
                + " §8(coins like 50k, or % of budget like 1%)");
    }

    private static void set(String key, String value) {
        Config c = Config.get();
        double v = parseAmount(value);
        String k = key.toLowerCase(Locale.ROOT);
        if (k.equals("sound")) {
            c.bzSound = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
        } else if (k.equals("safe")) {
            c.craftFlipSafeOnly = value.equalsIgnoreCase("on") || value.equalsIgnoreCase("true");
        } else if (k.equals("minprice") || k.equals("minprofit") || k.equals("craftprofit")) {
            String rule = value.equalsIgnoreCase("off") ? "0" : value.trim();
            if (!rule.endsWith("%") && Double.isNaN(v) && !rule.equals("0")) {
                Tracker.say("§6[Flips] §c\"" + value + "\" isn't an amount. §7Examples: 50k, 1%, off");
                return;
            }
            if (k.equals("minprice")) c.flipMinItemPrice = rule;
            else if (k.equals("minprofit")) c.flipMinProfit = rule;
            else c.craftMinProfit = rule;
        } else if (Double.isNaN(v) && !(k.equals("alert") || k.equals("maxprice"))) {
            Tracker.say("§6[Flips] §c\"" + value + "\" isn't a number. §7Examples: 10m, 500k, 1.25");
            return;
        } else {
            if (Double.isNaN(v)) v = 0;   // "off" / "none"
            switch (k) {
                case "budget" -> c.bzBudget = v;
                case "minvolume" -> c.bzMinVolume = v;
                case "minmargin" -> c.bzMinMargin = v;
                case "maxprice" -> c.bzMaxPrice = v;
                case "tax" -> c.bzTax = v;
                case "share" -> c.bzShare = v;
                case "alert" -> c.bzFlipAlert = v;
                case "top" -> c.bzTop = (int) Math.max(1, Math.min(50, v));
                default -> {
                    Tracker.say("§6[Flips] §7Unknown setting. Use: budget, minvolume, minmargin, maxprice, tax, share, alert, top, sound, safe, minprice, minprofit, craftprofit");
                    return;
                }
            }
        }
        Config.save();
        settings();
    }

    /** "10m" -> 10,000,000, "500k", "1.5b", "1,250". NaN if not a number. */
    static double parseAmount(String s) {
        String t = s.trim().toLowerCase(Locale.ROOT).replace(",", "");
        double mult = 1;
        if (t.endsWith("k")) { mult = 1e3; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("m")) { mult = 1e6; t = t.substring(0, t.length() - 1); }
        else if (t.endsWith("b")) { mult = 1e9; t = t.substring(0, t.length() - 1); }
        try { return Double.parseDouble(t) * mult; } catch (Exception e) { return Double.NaN; }
    }

    private FlipsCommand() {}
}
