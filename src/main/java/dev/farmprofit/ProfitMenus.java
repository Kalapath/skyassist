package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/** All the menus: hub, current session, history, totals, best now, Bazaar flips. */
public final class ProfitMenus {
    private static final SimpleDateFormat DATE = new SimpleDateFormat("dd.MM HH:mm");
    private static final String[] ACTIVITIES = {Tracker.FARMING, Tracker.MINING, Tracker.FORAGING, Tracker.FISHING,
            Tracker.COMBAT, Tracker.DUNGEONS, Tracker.KUUDRA, Tracker.DIANA};

    private static String title(String type) { return Hud.title(type).replace("§l", "").replaceAll(" §7\\(.*\\)", ""); }

    /** Holder so row buttons can refresh the screen they're on. */
    private static final class Ref { MenuScreen screen; }

    // ======================================================================== hub

    public static Screen hub() {
        Ref ref = new Ref();
        List<Row> rows = new ArrayList<>();
        rows.add(entry("§fCurrent session", "Items, profit and costs of what you're doing now.", () -> Compat.setScreen(mc(), session(null, ref.screen))));
        rows.add(entry("§fHistory", "Every finished session, by activity.", () -> Compat.setScreen(mc(), history(null, ref.screen))));
        rows.add(entry("§fLifetime totals", "Time, profit and profit/h per activity, all time.", () -> Compat.setScreen(mc(), totals(ref.screen))));
        rows.add(entry("§fBest now", "Which crop / ore earns the most right now.", () -> Compat.setScreen(mc(), suggest(0, ref.screen))));
        rows.add(entry("§fBazaar flips", "Flip finder, plan, your orders and flip profit.", () -> Compat.setScreen(mc(), flips(0, ref.screen))));
        rows.add(entry("§fDungeon", "This run's secrets, crypts, deaths, puzzles, team; past runs.", () -> Compat.setScreen(mc(), Dungeon.screen(Dungeon.inDungeon() ? 0 : 3, ref.screen))));
        rows.add(entry("§fGreenhouse", "Plan a mutation: what to unlock first and what to plant where.", () -> Compat.setScreen(mc(), Greenhouse.screen(ref.screen))));
        rows.add(entry("§fStorage overview", "Everything in your Ender Chest and backpacks, searchable.", () -> Compat.setScreen(mc(), Storage.screen(ref.screen))));
        rows.add(entry("§fWaypoints", "Party coordinates and Crystal Hollows places.", () -> Compat.setScreen(mc(), Waypoints.screen(ref.screen))));
        rows.add(entry("§fHOTM guide", "Best Heart of the Mountain trees for powder, gemstones, mithril, glacite.", () -> Compat.setScreen(mc(), TreeGuideScreen.hotm(ref.screen))));
        rows.add(entry("§fHOTF guide", "Best Heart of the Forest trees for Fig / Helix whispers.", () -> Compat.setScreen(mc(), TreeGuideScreen.hotf(ref.screen))));
        rows.add(entry("§fAttribute shards", "Cheapest attribute levels to buy next.", () -> Compat.setScreen(mc(), Shards.screen(ref.screen))));
        rows.add(entry("§fMarket signals", "Mayor / event / trend signals from price history, with backtest.", () -> Compat.setScreen(mc(), MarketSignals.screen(ref.screen))));
        rows.add(entry("§fNext talismans", "Cheapest Magical Power you don't have yet.", () -> Compat.setScreen(mc(), TalismansScreen.screen(ref.screen))));
        rows.add(entry("§fSettings", "Every setting, with search.", () -> Compat.setScreen(mc(), new SettingsScreen(ref.screen))));
        rows.add(entry("§fCommands", "Every command with a short explanation.", () -> Compat.setScreen(mc(), Commands.screen(ref.screen))));
        rows.add(entry("§fHUD editor", "Move, resize and hide the HUD panels.", () -> { Compat.setScreen(mc(), null); GuiEditor.open(); }));
        rows.add(entry("§fSetup check", "What the mod can see, and what to turn on.", () -> Compat.setScreen(mc(), new SetupScreen())));
        Tab t = new Tab("Menu", () -> new Page(new String[]{"What", "", ""}, new int[]{150, 260, 40}, rows, List.of(),
                List.of("§8Open this menu with the P key (change it in Controls) or /profit menu.")));
        ref.screen = new MenuScreen("SkyAssist", List.of(t), 0, null);
        return ref.screen;
    }

    private static Row entry(String name, String what, Runnable open) {
        return new Row(new String[]{name, "§7" + what}, null, List.of(new Action("Open", null, open)));
    }

    private static Minecraft mc() { return Minecraft.getInstance(); }

    // ======================================================================== current session

    public static Screen session(String type, Screen parent) {
        Ref ref = new Ref();
        List<String> types = new ArrayList<>(Tracker.sessions.keySet());
        if (types.isEmpty()) types.add(type != null ? type : Tracker.FARMING);
        int start = Math.max(0, types.indexOf(type != null ? type : Tracker.shownType()));
        List<Tab> tabs = new ArrayList<>();
        for (String t : types) tabs.add(new Tab(title(t), () -> sessionPage(t, ref)));
        ref.screen = new MenuScreen("Current session", tabs, start, parent);
        return ref.screen;
    }

    private static Page sessionPage(String type, Ref ref) {
        Session s = Tracker.sessions.get(type);
        List<Row> rows = new ArrayList<>();
        List<Action> top = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        if (s == null) {
            footer.add("§7No " + type.toLowerCase(Locale.ROOT) + " session running.");
            return new Page(new String[]{"Item", "Amount", "Value"}, new int[]{230, 90, 100}, rows, top, footer);
        }
        long now = System.currentTimeMillis();
        for (var e : Hud.sortedItems(s)) {
            double v = e.getValue() * Prices.price(e.getKey());
            rows.add(new Row(new String[]{"§a" + e.getKey(), "§f" + (e.getValue() > 0 ? "+" : "") + Fmt.num(e.getValue()), "§6" + (v == 0 ? "?" : Fmt.coins(v))},
                    "§7Each: §6" + Fmt.coins(Prices.price(e.getKey())), List.of(hide(e.getKey(), ref))));
        }
        if (s.shards != null) for (var e : s.shards.entrySet()) {
            double v = e.getValue() * Prices.price(e.getKey());
            rows.add(new Row(new String[]{"§b" + e.getKey(), "§f" + e.getValue(), "§6" + (v == 0 ? "?" : Fmt.coins(v))}, "§7Attribute shard", List.of(hide(e.getKey(), ref))));
        }
        if (s.rareDrops != null) for (var e : s.rareDrops.entrySet()) {
            double v = s.rareValue(e.getKey(), e.getValue());
            rows.add(new Row(new String[]{"§d" + e.getKey(), "§f" + e.getValue() + "x", "§6" + (v == 0 ? "in items / ?" : Fmt.coins(v))}, "§7Rare drop", List.of(hide(e.getKey(), ref))));
        }
        if (s.spent != null) for (var e : s.spent.entrySet()) {
            rows.add(new Row(new String[]{"§c" + e.getKey() + " §8(spent)", "§c-" + Fmt.num(e.getValue()), "§c-" + Fmt.coins(e.getValue() * Prices.price(e.getKey()))}));
        }
        top.add(new Action("End & save session", "Ends this session now and saves it to history.", () -> { Tracker.endSession(s, true); ref.screen.refresh(); }));
        top.add(new Action("Copy summary", "Copies \"" + type + ": profit in time (per hour)\" to the clipboard.", () -> Chat.copy(
                s.type + ": " + Fmt.coins(s.value()) + " coins in " + Fmt.duration(s.durationMs(now)) + " (" + Fmt.coins(s.perHour(now)) + "/h)")));
        top.add(new Action("Chat view", "Prints this session in chat instead.", () -> { Compat.setScreen(mc(), null); for (String l : Hud.profitLines(s)) Tracker.say(l); }));
        footer.add("§7Time §f" + Fmt.duration(s.durationMs(now)) + "  §7Profit §6" + Fmt.coins(s.value()) + "  §7Profit/h §6"
                + (s.durationMs(now) < 60_000 ? "wait 1 min" : Fmt.coins(s.perHour(now))));
        String extra = (s.costs > 0 ? "§7Costs §c-" + Fmt.coins(s.costs) + "  " : "") + (s.coins > 0 ? "§7Coins found §6+" + Fmt.coins(s.coins) + "  " : "")
                + (s.note != null ? "§7Note §f" + s.note : "");
        if (!extra.isBlank()) footer.add(extra);
        return new Page(new String[]{"Item", "Amount", "Value"}, new int[]{230, 90, 100}, rows, top, footer);
    }

    private static Action hide(String item, Ref ref) {
        return new Action("§8Hide", "Stop counting " + item + " (undo in Settings → Hidden items).", () -> {
            if (!Config.get().ignoredItems.contains(item)) Config.get().ignoredItems.add(item);
            Config.save();
            ref.screen.refresh();
        });
    }

    // ======================================================================== history

    public static Screen history(String type, Screen parent) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab("Day / week / month", ProfitMenus::periodsPage));
        tabs.add(new Tab("All", () -> historyPage(null)));
        int start = type == null ? 0 : 1;
        for (String t : ACTIVITIES) {
            if (t.equals(type)) start = tabs.size();
            tabs.add(new Tab(title(t), () -> historyPage(t)));
        }
        return new MenuScreen("History", tabs, start, parent).searchable();
    }

    private static Page historyPage(String type) {
        List<Session> all = new ArrayList<>();
        for (Session s : History.all()) if (type == null || Tracker.normalType(s.type).equals(type)) all.add(s);
        List<Row> rows = new ArrayList<>();
        for (int i = all.size() - 1; i >= 0; i--) {
            Session s = all.get(i);
            String t = Tracker.normalType(s.type);
            StringBuilder tip = new StringBuilder(title(t) + "\n§7" + s.mainCrop);
            if (s.fortune != null) tip.append("\n§8").append(s.fortune);
            if (s.note != null) tip.append("\n§7Note: §f").append(s.note);
            if (s.shards != null && !s.shards.isEmpty()) tip.append("\n§bShards: ").append(joinCounts(s.shards));
            if (s.rareDrops != null && !s.rareDrops.isEmpty()) tip.append("\n§dRare: ").append(joinCounts(s.rareDrops));
            rows.add(new Row(new String[]{"§8" + DATE.format(new Date(s.start)), title(t), "§a" + s.mainCrop, "§f" + Fmt.duration(s.durationMs(0)),
                    "§6" + Fmt.coins(s.profit), "§6" + Fmt.coins(s.profitPerHour) + "/h"}, tip.toString(), List.of()));
        }
        List<String> footer = new ArrayList<>();
        String spark = FarmProfitClient.sparkline(all.subList(Math.max(0, all.size() - 40), all.size()));
        if (!spark.isEmpty()) footer.add("§7Profit/h trend (oldest → newest): §e" + spark);
        footer.add("§8Hover a session for details.");
        List<Action> top = List.of(new Action("Export CSV", "Saves all history to config/skyassist/history.csv for Excel / Google Sheets.", FarmProfitClient::exportCsv));
        return new Page(new String[]{"When", "Activity", "What", "Time", "Profit", "Per hour"}, new int[]{70, 90, 100, 70, 60, 60}, rows, top, footer);
    }

    /** Profit today / this week / this month..., with flips, and a per-activity breakdown on hover. */
    private static Page periodsPage() {
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.LocalDate today = java.time.LocalDate.now(zone);
        long now = System.currentTimeMillis();
        Object[][] periods = {
                {"Today", today.atStartOfDay(zone).toInstant().toEpochMilli(), now},
                {"Yesterday", today.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), today.atStartOfDay(zone).toInstant().toEpochMilli()},
                {"This week", today.with(java.time.DayOfWeek.MONDAY).atStartOfDay(zone).toInstant().toEpochMilli(), now},
                {"Last 7 days", now - 7L * 86_400_000, now},
                {"This month", today.withDayOfMonth(1).atStartOfDay(zone).toInstant().toEpochMilli(), now},
                {"Last 30 days", now - 30L * 86_400_000, now},
                {"All time", 0L, now}};
        List<Row> rows = new ArrayList<>();
        for (Object[] p : periods) {
            long from = (long) p[1], to = (long) p[2];
            double profit = 0;
            long ms = 0;
            int sessions = 0;
            java.util.Map<String, Double> byType = new java.util.LinkedHashMap<>();
            List<Session> list = new ArrayList<>(History.all());
            list.addAll(Tracker.sessions.values());                         // running sessions count too
            for (Session s : list) {
                long when = s.end > 0 ? s.end : s.lastActivity;
                if (when < from || when > to) continue;
                double v = s.end > 0 ? s.profit : s.value();
                profit += v;
                ms += s.durationMs(now);
                sessions++;
                byType.merge(Tracker.normalType(s.type), v, Double::sum);
            }
            double flips = 0;
            for (var e : Bazaar.state().log) if (e.time >= from && e.time <= to) flips += e.profit;
            if (flips != 0) byType.merge("Bazaar flips", flips, Double::sum);
            double h = ms / 3_600_000.0;
            StringBuilder tip = new StringBuilder("§7" + p[0]);
            byType.forEach((k, v) -> tip.append("\n§f").append(k).append(": §6").append(Fmt.coins(v)));
            rows.add(new Row(new String[]{"§f" + p[0], "§6" + Fmt.coins(profit + flips), "§7" + Fmt.duration(ms), "§7" + sessions,
                    "§6" + (h > 0 ? Fmt.coins(profit / h) : "0") + "/h", flips != 0 ? "§e" + Fmt.coins(flips) : "§8-"}, tip.toString(), List.of()));
        }
        return new Page(new String[]{"Period", "Profit", "Time", "Sessions", "Per hour", "Flips"}, new int[]{90, 70, 90, 55, 70, 60}, rows, List.of(),
                List.of("§8Profit includes Bazaar flips. Hover a row to see it split by activity.", "§8Per hour is from activity time (flips not included)."));
    }

    private static String joinCounts(Map<String, Integer> m) {
        List<String> parts = new ArrayList<>();
        m.forEach((k, v) -> parts.add(v + "x " + k));
        return String.join(", ", parts);
    }

    // ======================================================================== totals

    public static Screen totals(Screen parent) {
        Tab t = new Tab("Totals", () -> {
            List<Row> rows = new ArrayList<>();
            double profit = 0;
            long ms = 0;
            for (var e : Totals.all().entrySet()) {
                var tt = e.getValue();
                double h = tt.ms / 3_600_000.0;
                profit += tt.profit;
                ms += tt.ms;
                rows.add(new Row(title(e.getKey()), "§f" + Fmt.duration(tt.ms), "§f" + tt.sessions, "§6" + Fmt.coins(tt.profit), "§6" + (h > 0 ? Fmt.coins(tt.profit / h) : "0") + "/h"));
            }
            double h = ms / 3_600_000.0;
            return new Page(new String[]{"Activity", "Time", "Sessions", "Profit", "Per hour"}, new int[]{110, 100, 70, 80, 80}, rows, List.of(),
                    List.of("§7All activities: §f" + Fmt.duration(ms) + " §7for §6" + Fmt.coins(profit) + " §7(§6" + (h > 0 ? Fmt.coins(profit / h) : "0") + "/h§7)"));
        });
        return new MenuScreen("Lifetime totals", List.of(t), 0, parent);
    }

    // ======================================================================== best now

    public static Screen suggest(int startTab, Screen parent) {
        return new MenuScreen("Best now", List.of(
                new Tab("Farming", () -> suggestPage(Suggest.farming(), "crop", "Based on your blocks/s and Farming Fortune.")),
                new Tab("Mining", () -> suggestPage(Suggest.mining(), "ore", Suggest.miningSpeed() > 0
                        ? "Based on your Mining Speed and fortune, for the island you're on." : "Add Mining Speed to the Stats tab widget to see this."))
        ), startTab, parent);
    }

    private static Page suggestPage(List<Suggest.Option> opts, String what, String note) {
        List<Row> rows = new ArrayList<>();
        int i = 1;
        for (Suggest.Option o : opts) {
            rows.add(new Row("§8" + i++ + ". §a" + o.name(), "§6~" + Fmt.coins(o.perHour()) + "/h", "§f" + o.sellAs(),
                    "§7" + String.format(Locale.US, "%.2f", o.perItem()) + " each"));
        }
        List<String> footer = new ArrayList<>();
        if (Election.mayor != null) footer.add("§7Mayor: §d" + Election.mayor + " §8(" + String.join(", ", Election.perks) + ")");
        String c = Contests.hudLine();
        if (c != null) footer.add(c);
        footer.add("§8" + note + " Pests, rare drops and powder aren't included.");
        return new Page(new String[]{"Best " + what, "Estimate", "Sell as", "Price"}, new int[]{130, 100, 150, 80}, rows, List.of(), footer);
    }

    // ======================================================================== Bazaar flips

    public static Screen flips(int startTab, Screen parent) {
        Ref ref = new Ref();
        ref.screen = new MenuScreen("Bazaar flips", List.of(
                new Tab("Best flips", () -> flipsPage(Bazaar.computeFlips(), ref, false)),
                new Tab("Plan", () -> flipsPage(FlipsCommand.planList(5), ref, true)),
                new Tab("Craft → Bazaar", () -> craftPage(false, ref)),
                new Tab("Craft → AH", () -> craftPage(true, ref)),
                new Tab("My orders", () -> ordersPage(ref)),
                new Tab("Profit log", ProfitMenus::logPage)
        ), startTab, parent).searchable();
        return ref.screen;
    }

    private static List<Action> flipTop(Ref ref) {
        List<Action> out = new ArrayList<>(List.of(
                new Action("Refresh prices", "Gets the newest Bazaar prices.", () -> { Prices.refresh(); ref.screen.refresh(); }),
                new Action("Flip settings", "Budget, minimum volume, tax and more.", () -> {
                    SettingsScreen.selectSection("Bazaar", "Flip finder");
                    Compat.setScreen(mc(), new SettingsScreen(ref.screen));
                })));
        out.add(new Action("§6★ Best settings", "Tries ~500 filter combinations on today's Bazaar prices and keeps the one with the most profit for the least work "
                + "(fewer orders, less fought-over items, faster fills). Safe only goes on. Uses your budget: " + Fmt.coins(Config.get().bzBudget) + ".",
                () -> { FlipTuner.tuneAndReport(); ref.screen.refresh(); }));
        if (FlipTuner.canUndo()) out.add(new Action("§7↺ Undo", "Puts your flip settings back to how they were before Best settings.",
                () -> { FlipTuner.undo(); Tracker.say("§6[Flips] §7Settings restored."); ref.screen.refresh(); }));
        return out;
    }

    /** One switch for normal and craft flips. */
    private static Action safeToggle(Ref ref) {
        Config c = Config.get();
        return new Action((c.craftFlipSafeOnly ? "§a☑" : "§7☐") + " Safe only",
                "Hide volatile, competitive, slow and possibly manipulated / inflated flips. Applies to every flip tab.",
                () -> { c.craftFlipSafeOnly = !c.craftFlipSafeOnly; Config.save(); ref.screen.refresh(); });
    }

    /** Button showing a flip rule ("1%" or "50k") and its coin value; click cycles through presets. */
    private static Action ruleButton(String name, String current, String[] presets, String tip,
                                     java.util.function.Consumer<String> set, Ref ref) {
        double coins = Config.rule(current);
        String shown = coins <= 0 ? "§7off" : current.trim().endsWith("%") ? "§f" + current.trim() + " §8(" + Fmt.coins(coins) + ")" : "§f" + Fmt.coins(coins);
        return new Action("§7" + name + ": " + shown, tip + "\n§8Budget: " + Fmt.coins(Config.get().bzBudget), () -> {
            int i = java.util.Arrays.asList(presets).indexOf(current.trim());
            set.accept(presets[(i + 1) % presets.length]);
            Config.save();
            ref.screen.refresh();
        });
    }

    private static Page flipsPage(List<Bazaar.Flip> flips, Ref ref, boolean plan) {
        Config c = Config.get();
        List<Row> rows = new ArrayList<>();
        double cost = 0, profit = 0;
        int i = 1;
        for (Bazaar.Flip f : flips) {
            if (!plan && i > 50) break;
            cost += f.qty * f.buyAt;
            profit += f.profitHour;
            String tip = "§a" + f.name + "\n§7Buy order at §f" + Bazaar.Fmt1(f.buyAt) + "\n§7Sell offer at §f" + Bazaar.Fmt1(f.sellAt)
                    + "\n§7Profit each §6" + Fmt.coins(f.profitEach) + "\n§7Volume §f" + Fmt.coins(f.hourlyVolume) + "/h"
                    + (f.warnings.isEmpty() ? "" : "\n§c⚠ " + String.join(", ", f.warnings));
            rows.add(new Row(new String[]{"§8" + i++ + ". §a" + f.name + (f.warnings.isEmpty() ? "" : " §c⚠"),
                    "§f" + Bazaar.Fmt1(f.buyAt) + " → " + Bazaar.Fmt1(f.sellAt), String.format(Locale.US, "§7%.1f%%", f.margin),
                    "§f" + Fmt.num(f.qty), "§6" + Fmt.coins(f.profitHour) + "/h"}, tip,
                    List.of(new Action("§eBazaar", "Opens " + f.name + " in the Bazaar.", () -> MenuScreen.runCommand("bz " + f.name)))));
        }
        List<String> footer = new ArrayList<>();
        footer.add("§7Budget §6" + Fmt.coins(c.bzBudget) + "  §7tax " + c.bzTax + "%  §7min volume " + Fmt.coins(c.bzMinVolume) + "/week");
        if (plan) footer.add("§7Plan uses §6" + Fmt.coins(cost) + "§7, expected profit §6" + Fmt.coins(profit) + "§7. Place the orders yourself; they're tracked from chat.");
        if (!Prices.loaded()) footer.add("§cPrices are still loading — press Refresh prices.");
        if (Bazaar.minItemPrice() > 0) footer.add("§8Hidden: items cheaper than " + Fmt.coins(Bazaar.minItemPrice()) + " each (your budget buys at most ~"
                + Fmt.num(Math.round(c.bzBudget / Bazaar.minItemPrice())) + " of any item).");
        if (Bazaar.minFlipProfit() > 0) footer.add("§8Hidden: flips making under " + Fmt.coins(Bazaar.minFlipProfit()) + " per order.");
        if (c.craftFlipSafeOnly && Bazaar.safeHidden > 0) footer.add("§8Safe only hid " + Bazaar.safeHidden + " risky flips (⚠, under 3% margin or 25%+ buy/sell gap).");
        List<Action> top = new ArrayList<>(flipTop(ref));
        top.add(safeToggle(ref));
        top.add(ruleButton("Min item price", c.flipMinItemPrice, new String[]{"0", "0.05%", "0.1%", "0.5%", "1%"},
                "Hide items cheaper than this, so you never buy thousands of something. Click to cycle: off, 0.05%, 0.1%, 0.5%, 1% of your budget. Exact value in Settings → Bazaar.",
                v -> c.flipMinItemPrice = v, ref));
        top.add(ruleButton("Min profit/flip", c.flipMinProfit, new String[]{"0", "0.5%", "1%", "2%", "5%"},
                "Hide flips whose whole order makes less than this. Click to cycle: off, 0.5%, 1%, 2%, 5% of your budget.",
                v -> c.flipMinProfit = v, ref));
        return new Page(new String[]{"Item", "Buy → sell", "Margin", "Qty", "Profit"}, new int[]{150, 120, 50, 60, 70}, rows, top, footer);
    }

    private static Page craftPage(boolean auction, Ref ref) {
        List<Row> rows = new ArrayList<>();
        int i = 1;
        for (CraftFlips.Flip f : CraftFlips.compute(auction)) {
            if (i > 60) break;
            String tip = "§a" + f.name() + (f.makes() > 1 ? " §7(recipe makes " + f.makes() + ")" : "") + "\n§7Ingredients: §f" + f.ingredients()
                    + "\n§7Buy them for §6" + Fmt.coins(f.cost()) + "§7, sell for §6" + Fmt.coins(f.sell()) + " §8(after tax)"
                    + (f.note() != null ? "\n§e" + f.note() : "");
            List<Action> buttons = new ArrayList<>();
            buttons.add(new Action("§bRecipe", "Opens the recipe for " + f.name() + ".", () -> MenuScreen.runCommand("recipe " + f.name())));
            if (auction) buttons.add(new Action("§eAH", "Searches the Auction House for " + f.name() + " (check recent prices).", () -> MenuScreen.runCommand("ahs " + f.name())));
            else buttons.add(new Action("§eBazaar", "Opens " + f.name() + " in the Bazaar.", () -> MenuScreen.runCommand("bz " + f.name())));
            rows.add(new Row(new String[]{"§8" + i++ + ". §a" + f.name(), "§6" + Fmt.coins(f.cost()), "§6" + Fmt.coins(f.sell()),
                    "§a+" + Fmt.coins(f.profit()), String.format(java.util.Locale.US, "§7%.0f%%", f.margin()),
                    auction ? "" : "§6" + Fmt.coins(f.perHour()) + "/h"}, tip, buttons));
        }
        List<String> footer = new ArrayList<>();
        StringBuilder why = new StringBuilder("§8" + CraftFlips.checked + " recipes checked");
        CraftFlips.SKIPPED.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(5)
                .forEach(x -> why.append(", ").append(x.getValue()).append(" ").append(x.getKey()));
        footer.add(why.toString());
        if (auction && Prices.binCount() < 100) footer.add("§cNo lowest-BIN prices loaded (source: " + Prices.binSource + "). AH flips need them; try /profit prices.");
        footer.add(auction ? "§7Ingredients bought now, result sold at lowest BIN (minus AH fees). Check recent sales before crafting a lot."
                : "§7Ingredients bought instantly, result sold with a sell offer (after tax). Per hour uses your volume share and budget.");
        Config cc = Config.get();
        int cols = Unlocks.known("collection"), slayers = Unlocks.known("slayer"), skills = Unlocks.known("skill");
        if (cc.craftOnlyUnlocked) footer.add((cols == 0 ? "§e" : "§8") + "Your unlocks: " + cols + " collections, " + slayers + " slayers, " + skills
                + " skills read. Open each /collection category, the slayer menu and /skills to update."
                + (CraftFlips.locked > 0 ? " Hidden as locked: " + CraftFlips.locked + "." : "")
                + (CraftFlips.unknown > 0 ? (cc.craftHideUnknown ? " Hidden as unknown: " : " Unknown (shown with a note): ") + CraftFlips.unknown + "." : ""));
        footer.add("§8Hover a row for the ingredients. Settings → Bazaar: min profit, min margin, budget.");
        List<Action> top = new ArrayList<>(flipTop(ref));
        top.add(safeToggle(ref));
        top.add(new Action((cc.craftOnlyUnlocked ? "§a☑" : "§7☐") + " Only what I can craft",
                "Hide recipes you haven't unlocked (collection / slayer / skill level). Levels come from the menus you open and level-up messages.",
                () -> { cc.craftOnlyUnlocked = !cc.craftOnlyUnlocked; Config.save(); ref.screen.refresh(); }));
        top.add(ruleButton("Min profit/craft", cc.craftMinProfit, new String[]{"1000", "0.5%", "1%", "2%", "5%"},
                "Each craft must make at least this. Click to cycle: 1k, 0.5%, 1%, 2%, 5% of your budget. Exact value in Settings → Bazaar.",
                v -> cc.craftMinProfit = v, ref));
        return new Page(new String[]{"Craft", "Cost", "Sells for", "Profit", "Margin", auction ? "" : "Per hour"},
                new int[]{140, 60, 60, 60, 45, 60}, rows, top, footer);
    }

    private static Page ordersPage(Ref ref) {
        List<Row> rows = new ArrayList<>();
        var orders = Bazaar.state().orders;
        for (int i = 0; i < orders.size(); i++) {
            var o = orders.get(i);
            final int idx = i;
            rows.add(new Row(new String[]{Bazaar.orderLine(o).trim()}, null, List.of(
                    new Action("§eBazaar", "Opens " + o.name + " in the Bazaar.", () -> MenuScreen.runCommand("bz " + o.name)),
                    new Action("§cRemove", "Removes this entry from the list (if it's stale). Doesn't touch the real order.", () -> {
                        if (idx < Bazaar.state().orders.size()) Bazaar.state().orders.remove(idx);
                        Bazaar.save();
                        ref.screen.refresh();
                    }))));
        }
        return new Page(new String[]{"Order"}, new int[]{360}, rows, flipTop(ref),
                List.of("§8Orders appear automatically when you place them in the Bazaar.", "§7Coins in orders today: §6" + Fmt.coins(Bazaar.tradedToday())));
    }

    private static Page logPage() {
        List<Row> rows = new ArrayList<>();
        for (var e : Bazaar.state().log) {
            rows.add(new Row("§8" + DATE.format(new Date(e.time)), "§f" + e.qty + "x " + e.name + (e.craft ? " §8(craft)" : ""), "§7" + Bazaar.Fmt1(e.buy) + " → " + Bazaar.Fmt1(e.sell),
                    (e.profit >= 0 ? "§a+" : "§c") + Fmt.coins(e.profit)));
        }
        return new Page(new String[]{"When", "Item", "Bought → sold", "Profit"}, new int[]{70, 170, 120, 80}, rows, List.of(),
                List.of("§7Today §6" + Fmt.coins(Bazaar.profitSince(Bazaar.startOfDay())) + "  §7All time §6" + Fmt.coins(Bazaar.profitSince(0))));
    }

    private ProfitMenus() {}
}
