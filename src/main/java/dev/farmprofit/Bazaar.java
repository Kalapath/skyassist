package dev.farmprofit;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bazaar flip assistant (ported from the Bazaar Flip Assistant page + chat bridge).
 * Reads your own [Bazaar] chat messages to track orders, checks them against live prices,
 * and tells you when to act. It never clicks or does anything for you.
 */
public final class Bazaar {
    private static final String NUM = "([\\d,]+(?:\\.\\d+)?)";
    private static final Pattern SETUP_BUY = Pattern.compile("\\[Bazaar\\] Buy Order Setup! " + NUM + "x (.+?) for " + NUM + " coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern SETUP_SELL = Pattern.compile("\\[Bazaar\\] Sell Offer Setup! " + NUM + "x (.+?) for " + NUM + " coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILLED_BUY = Pattern.compile("\\[Bazaar\\] Your Buy Order for " + NUM + "x (.+?) was filled", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILLED_SELL = Pattern.compile("\\[Bazaar\\] Your Sell Offer for " + NUM + "x (.+?) was filled", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLAIM_BUY = Pattern.compile("\\[Bazaar\\] Claimed " + NUM + "x (.+?) worth " + NUM + " coins bought for " + NUM + " each", Pattern.CASE_INSENSITIVE);
    private static final Pattern CLAIM_SELL = Pattern.compile("\\[Bazaar\\] Claimed " + NUM + " coins from selling " + NUM + "x (.+?) at " + NUM + " each", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCEL_BUY = Pattern.compile("\\[Bazaar\\] Cancelled! Refunded " + NUM + " coins from cancelling (?:a |your )?Buy Order", Pattern.CASE_INSENSITIVE);
    private static final Pattern CANCEL_SELL = Pattern.compile("\\[Bazaar\\] Cancelled! Refunded " + NUM + "x (.+?) from cancelling (?:a |your )?Sell Offer", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTA_BUY = Pattern.compile("\\[Bazaar\\] Bought " + NUM + "x (.+?) for " + NUM + " coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern INSTA_SELL = Pattern.compile("\\[Bazaar\\] Sold " + NUM + "x (.+?) for " + NUM + " coins", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLIPPED = Pattern.compile("\\[Bazaar\\] Order Flipped! " + NUM + "x (.+?) for " + NUM + " coins", Pattern.CASE_INSENSITIVE);
    private static final double EPS = 0.05;
    private static final int MAX_ORDER = 71680;

    public static final class Order {
        public String type;      // "buy" or "sell"
        public String name;
        public int qty;
        public double price;     // per item
        public boolean filled;
        public int claimed;
        public long created;
        public transient String status = "unknown";   // top / outbid / undercut / unknown
        public transient double suggested;
        public transient String note;
    }

    public static final class Lot { public String name; public int qty; public double cost; }

    public static final class Entry {
        public long time; public String name; public int qty; public double buy, sell, profit;
        /** true = a craft flip (ingredients bought, the crafted item sold) */
        public boolean craft;
    }

    public static final class State {
        public List<Order> orders = new ArrayList<>();
        public List<Lot> lots = new ArrayList<>();
        public List<Entry> log = new ArrayList<>();
        public long tradedDay;
        public double tradedToday;
    }

    public static final class Flip {
        public String id, name;
        public double buyAt, sellAt, profitEach, margin, hourlyVolume, profitHour;
        public int qty;
        public List<String> warnings = new ArrayList<>();
    }

    private static final Path FILE = Config.DIR.resolve("bazaar.json");
    private static State state;
    private static final Map<String, Long> flipAlerted = new HashMap<>();

    public static State state() {
        if (state == null) load();
        return state;
    }

    public static boolean hasOpenOrders() { return !state().orders.isEmpty(); }

    // ================= chat =================

    /** Returns true if the message was a [Bazaar] message we handled. */
    public static boolean handle(String msg) {
        if (!msg.startsWith("[Bazaar]")) return false;
        State st = state();
        Matcher m;

        if ((m = SETUP_BUY.matcher(msg)).find()) {
            int qty = (int) num(m.group(1));
            addOrder("buy", m.group(2).trim(), qty, round1(num(m.group(3)) / Math.max(1, qty)));
            save(); return true;
        }
        if ((m = SETUP_SELL.matcher(msg)).find()) {
            int qty = (int) num(m.group(1));
            addOrder("sell", m.group(2).trim(), qty, round1(num(m.group(3)) / Math.max(1, qty)));
            save(); return true;
        }
        if ((m = FILLED_BUY.matcher(msg)).find()) {
            Order o = find("buy", m.group(2).trim(), x -> !x.filled);
            if (o != null) o.filled = true;
            if (Config.get().bzFillSound) ping();
            save(); return true;
        }
        if ((m = FILLED_SELL.matcher(msg)).find()) {
            Order o = find("sell", m.group(2).trim(), x -> !x.filled);
            if (o != null) o.filled = true;
            if (Config.get().bzFillSound) ping();
            save(); return true;
        }
        if ((m = CLAIM_BUY.matcher(msg)).find()) {
            int qty = (int) num(m.group(1));
            String name = m.group(2).trim();
            double each = num(m.group(4));
            Lot lot = new Lot(); lot.name = name; lot.qty = qty; lot.cost = each;
            st.lots.add(lot);
            Order o = find("buy", name, x -> true);
            if (o != null) { o.claimed += qty; if (o.claimed >= o.qty) st.orders.remove(o); }
            save(); return true;
        }
        if ((m = CLAIM_SELL.matcher(msg)).find()) {
            double coins = num(m.group(1));
            int qty = (int) num(m.group(2));
            String name = m.group(3).trim();
            double sellEach = num(m.group(4));
            logSale(name, qty, coins, sellEach);
            Order o = find("sell", name, x -> true);
            if (o != null) { o.claimed += qty; if (o.claimed >= o.qty) st.orders.remove(o); }
            save(); return true;
        }
        if ((m = CANCEL_SELL.matcher(msg)).find()) {
            Order o = find("sell", m.group(2).trim(), x -> true);
            if (o != null) st.orders.remove(o);
            save(); return true;
        }
        if ((m = CANCEL_BUY.matcher(msg)).find()) {
            double refund = num(m.group(1));
            Order best = null;
            for (Order o : st.orders) {   // Hypixel doesn't name the item, so match by refunded amount
                if (!o.type.equals("buy")) continue;
                if (best == null || Math.abs((o.qty - o.claimed) * o.price - refund) < Math.abs((best.qty - best.claimed) * best.price - refund)) best = o;
            }
            if (best != null) st.orders.remove(best);
            save(); return true;
        }
        if ((m = INSTA_BUY.matcher(msg)).find()) {
            int qty = (int) num(m.group(1));
            Lot lot = new Lot(); lot.name = m.group(2).trim(); lot.qty = qty; lot.cost = num(m.group(3)) / Math.max(1, qty);
            st.lots.add(lot);
            traded(num(m.group(3)));
            save(); return true;
        }
        if ((m = INSTA_SELL.matcher(msg)).find()) {
            int qty = (int) num(m.group(1));
            String name = m.group(2).trim();
            double coins = num(m.group(3));
            logSale(name, qty, coins, coins / Math.max(1, qty));
            traded(coins);
            save(); return true;
        }
        if ((m = FLIPPED.matcher(msg)).find()) {
            int qty = (int) num(m.group(1));
            String name = m.group(2).trim();
            double total = num(m.group(3));
            Order b = find("buy", name, x -> true);
            if (b != null) {
                Lot lot = new Lot(); lot.name = name; lot.qty = qty; lot.cost = b.price;
                st.lots.add(lot);
                b.claimed += qty;
                if (b.claimed >= b.qty) st.orders.remove(b);
            }
            addOrder("sell", name, qty, round1(total / Math.max(1, qty)));
            save(); return true;
        }
        return true;   // other [Bazaar] messages: nothing to do
    }

    private static void addOrder(String type, String name, int qty, double price) {
        Debug.saw("bazaar order");
        warnIfOdd(type, name, price);
        traded(qty * price);
        Order o = new Order();
        o.type = type; o.name = name; o.qty = qty; o.price = price; o.created = System.currentTimeMillis();
        state().orders.add(o);
    }

    private static Order find(String type, String name, java.util.function.Predicate<Order> pred) {
        for (Order o : state().orders) if (o.type.equals(type) && o.name.equalsIgnoreCase(name) && pred.test(o)) return o;
        return null;
    }

    /**
     * A sale: profit against what you paid. Same item bought before = normal flip. Otherwise, if it has a recipe and you
     * bought (some of) its ingredients = craft flip: cost from your bought ingredients (2 levels deep), anything you
     * didn't buy priced at today's instant-buy price.
     */
    private static void logSale(String name, int qty, double coins, double sellEach) {
        State st = state();
        double[] same = takeLots(name, qty);
        double cost;
        boolean craft = false;
        if (same[1] >= qty) cost = same[0];
        else {
            double[] c = craftCost(name, qty - (int) same[1], 2);
            if (c[1] <= 0 && same[1] == 0) return;                          // nothing you bought went into this: not a flip
            cost = same[0] + c[0];
            craft = c[1] > 0;
        }
        Entry e = new Entry();
        e.time = System.currentTimeMillis(); e.name = name; e.qty = qty; e.craft = craft;
        e.buy = cost / Math.max(1, qty); e.sell = sellEach; e.profit = coins - cost;
        st.log.add(0, e);
        while (st.log.size() > 1000) st.log.remove(st.log.size() - 1);
        Tracker.say("§6[Flips] §7Sold §f" + qty + "x " + name + (craft ? " §8(craft flip)" : "") + " §7profit " + (e.profit >= 0 ? "§a+" : "§c") + Fmt.coins(e.profit));
    }

    /** Takes up to qty items out of your bought lots: {total cost, how many were found}. */
    private static double[] takeLots(String name, int qty) {
        double total = 0;
        int got = 0;
        for (Iterator<Lot> it = state().lots.iterator(); it.hasNext() && got < qty; ) {
            Lot l = it.next();
            if (!l.name.equalsIgnoreCase(name)) continue;
            int take = Math.min(l.qty, qty - got);
            total += take * l.cost;
            got += take;
            l.qty -= take;
            if (l.qty <= 0) it.remove();
        }
        return new double[]{total, got};
    }

    /** Cost of crafting qty of an item from your bought lots: {cost, how many bought ingredients were used}. */
    private static double[] craftCost(String name, int qty, int depth) {
        String id = Prices.idFor(name);
        var recipe = id == null ? null : CraftCost.recipes().get(id);
        if (recipe == null || depth <= 0) return new double[]{0, 0};
        int makes = Math.max(1, recipe.getValue());
        int crafts = (qty + makes - 1) / makes;
        double cost = 0, used = 0;
        for (var ing : recipe.getKey().entrySet()) {
            String ingName = Prices.nameOf(ing.getKey().replace('-', ':'));
            int need = ing.getValue() * crafts;
            double[] lots = takeLots(ingName, need);
            cost += lots[0];
            used += lots[1];
            int missing = need - (int) lots[1];
            if (missing > 0) {
                double[] deeper = craftCost(ingName, missing, depth - 1);          // ingredient crafted from bought items too
                if (deeper[1] > 0) { cost += deeper[0]; used += deeper[1]; }
                else {                                                            // not bought at all: today's price (once)
                    double[] bz = Prices.bazaarRaw(ing.getKey());
                    cost += missing * (bz != null && bz[1] > 0 ? bz[1] : Prices.price(ingName));
                }
            }
        }
        return new double[]{cost * qty / (double) (crafts * makes), used};
    }

    /** Takes qty items out of your bought lots (oldest first) and returns their average cost. */
    private static Double takeCost(String name, int qty) {
        double total = 0;
        int got = 0;
        for (Iterator<Lot> it = state().lots.iterator(); it.hasNext() && got < qty; ) {
            Lot l = it.next();
            if (!l.name.equalsIgnoreCase(name)) continue;
            int take = Math.min(l.qty, qty - got);
            total += take * l.cost;
            got += take;
            l.qty -= take;
            if (l.qty <= 0) it.remove();
        }
        return got == 0 ? null : total / got;
    }

    private static Double avgCost(String name) {
        double total = 0; int n = 0;
        for (Lot l : state().lots) if (l.name.equalsIgnoreCase(name)) { total += l.qty * l.cost; n += l.qty; }
        return n == 0 ? null : total / n;
    }

    // ================= order protection =================

    /** Can't stop the order (that would be acting for you), but tells you right away if it looks like a typo. */
    private static void warnIfOdd(String type, String name, double price) {
        if (!Config.get().bzWarnMistakes) return;
        double[] b = Prices.BOOK.get(Prices.idFor(name));
        if (b == null) return;
        double topBuy = b[0], lowSell = b[1];
        String warn = null;
        if (type.equals("buy") && lowSell > 0 && price > lowSell * 1.5)
            warn = "buy order at " + Fmt1(price) + " is " + Math.round(price / lowSell * 100 - 100) + "% above the lowest sell offer (" + Fmt1(lowSell) + ")";
        if (type.equals("sell") && topBuy > 0 && price < topBuy * 0.67)
            warn = "sell offer at " + Fmt1(price) + " is " + Math.round(100 - price / topBuy * 100) + "% below the best buy order (" + Fmt1(topBuy) + ")";
        if (warn != null) {
            ping();
            Tracker.say("§c§l[Flips] Check this order! §c" + name + ": " + warn + ". §7Cancel it in the Bazaar if it's a mistake.");
        }
    }

    // ================= daily volume =================

    private static void traded(double coins) {
        State st = state();
        long today = startOfDay();
        if (st.tradedDay != today) { st.tradedDay = today; st.tradedToday = 0; }
        st.tradedToday += coins;
    }

    public static double tradedToday() {
        State st = state();
        return st.tradedDay == startOfDay() ? st.tradedToday : 0;
    }

    // ================= price checks =================

    private static double tax() { return Config.get().bzTax / 100.0; }

    /** Called on the game thread after fresh bazaar prices arrive. */
    public static void onPrices() {
        List<String> alerts = new ArrayList<>();
        for (Order o : state().orders) {
            String before = o.status;
            evaluate(o);
            boolean bad = o.status.equals("outbid") || o.status.equals("undercut");
            if (bad && !o.status.equals(before)) {
                alerts.add("§c" + (o.status.equals("outbid") ? "Outbid" : "Undercut") + "§7: §f" + o.name
                        + " §7→ re-list at §e" + Fmt1(o.suggested) + (o.note != null ? " §8(" + o.note + ")" : ""));
            }
        }
        if (!alerts.isEmpty()) {
            ping();
            for (String a : alerts) Tracker.say("§6[Flips] " + a);
        }
        checkFlipAlerts();
    }

    private static void evaluate(Order o) {
        o.note = null;
        if (o.filled) { o.status = "filled"; return; }
        String id = Prices.idFor(o.name);
        double[] b = Prices.BOOK.get(id);
        if (b == null) { o.status = "unknown"; return; }
        double topBuy = b[0], lowSell = b[1];
        if (o.type.equals("buy")) {
            if (topBuy < 0) { o.status = "top"; return; }
            o.suggested = topBuy + 0.1;
            o.status = topBuy > o.price + EPS ? "outbid" : "top";
            if (o.status.equals("outbid") && !(lowSell > 0 && (lowSell - 0.1) * (1 - tax()) > o.suggested)) o.note = "no longer profitable";
        } else {
            if (lowSell < 0) { o.status = "top"; return; }
            o.suggested = lowSell - 0.1;
            o.status = lowSell < o.price - EPS ? "undercut" : "top";
            Double cost = avgCost(o.name);
            if (o.status.equals("undercut") && cost != null && o.suggested * (1 - tax()) < cost) o.note = "below your cost";
        }
    }

    // ================= flip finder =================

    /** Minimum profit one flip order must make: fixed, or a % of your flip budget. 0 = off. */
    public static double minFlipProfit() {
        return Config.rule(Config.get().flipMinProfit);
    }

    /** Minimum buy price per item: fixed, or a % of your flip budget (0.1% of 50m = 50k). 0 = off. */
    public static double minItemPrice() {
        return Config.rule(Config.get().flipMinItemPrice);
    }

    /** How many flips "Safe flips only" hid in the last computeFlips(). */
    public static int safeHidden;

    /** Buy/sell gap as % of the instant-buy price (big = volatile / easy to manipulate). */
    static double spreadOf(double[] b) {
        return b[1] > 0 && b[0] > 0 ? (b[1] - b[0]) / b[1] * 100 : 0;
    }

    public static List<Flip> computeFlips() {
        Config c = Config.get();
        safeHidden = 0;
        double tax = tax(), share = c.bzShare / 100.0;
        List<Flip> out = new ArrayList<>();
        for (var e : Prices.BOOK.entrySet()) {
            double[] b = e.getValue();
            if (b[0] <= 0 || b[1] <= 0) continue;
            Flip f = new Flip();
            f.id = e.getKey();
            f.buyAt = b[0] + 0.1;
            f.sellAt = b[1] - 0.1;
            if (f.sellAt <= f.buyAt) continue;
            f.profitEach = f.sellAt * (1 - tax) - f.buyAt;
            if (f.profitEach <= 0) continue;
            f.margin = f.profitEach / f.buyAt * 100;
            double weekly = Math.min(b[2], b[3]);
            if (weekly < c.bzMinVolume || f.margin < c.bzMinMargin) continue;
            if (c.bzMaxPrice > 0 && f.buyAt > c.bzMaxPrice) continue;
            f.hourlyVolume = weekly / 168.0;
            f.qty = (int) Math.max(0, Math.floor(Math.min(Math.min(f.hourlyVolume * share, c.bzBudget / f.buyAt), MAX_ORDER)));
            f.profitHour = f.qty * f.profitEach;
            // with a big budget: skip flips that earn too little per order, and cheap items you'd need thousands of
            double minFlip = minFlipProfit();
            if (minFlip > 0 && f.qty * f.profitEach < minFlip) continue;
            double minPrice = minItemPrice();                       // cheap items you'd have to buy thousands of
            if (minPrice > 0 && f.buyAt < minPrice) continue;
            if (f.margin > 50) f.warnings.add("huge margin, maybe manipulated");
            if (b[4] + b[5] > 600) f.warnings.add("very competitive");
            if (f.hourlyVolume < 100) f.warnings.add("slow to fill");
            if (c.craftFlipSafeOnly && (!f.warnings.isEmpty() || f.margin < 3 || spreadOf(b) > 25)) { safeHidden++; continue; }
            f.name = Prices.nameOf(f.id);
            out.add(f);
        }
        out.sort((x, y) -> Double.compare(y.profitHour, x.profitHour));
        return out;
    }

    private static void checkFlipAlerts() {
        double threshold = Config.get().bzFlipAlert;
        if (threshold <= 0) return;
        long now = System.currentTimeMillis();
        List<Flip> hits = new ArrayList<>();
        for (Flip f : computeFlips()) {
            if (!f.warnings.isEmpty() || f.profitHour < threshold || find("buy", f.name, x -> true) != null) continue;
            if (now - flipAlerted.getOrDefault(f.id, 0L) < 15 * 60_000) continue;
            flipAlerted.put(f.id, now);
            hits.add(f);
        }
        if (hits.isEmpty()) return;
        ping();
        Tracker.say("§6[Flips] §e" + hits.size() + " hot flip" + (hits.size() > 1 ? "s" : "") + ":");
        for (int i = 0; i < Math.min(3, hits.size()); i++) Tracker.say(flipLine(i + 1, hits.get(i)));
    }

    // ================= display =================

    public static net.minecraft.network.chat.Component flipLine(int rank, Flip f) {
        String text = "§8" + rank + ". §a" + f.name + " §7" + Fmt1(f.buyAt) + " → " + Fmt1(f.sellAt)
                + " §8(" + String.format(Locale.US, "%.1f%%", f.margin) + ") §7qty §f" + Fmt.num(f.qty)
                + " §6" + Fmt.coins(f.profitHour) + "/h" + (f.warnings.isEmpty() ? "" : " §c⚠");
        String hover = "§a" + f.name + "\n§7Buy order at §f" + Fmt1(f.buyAt) + "\n§7Sell offer at §f" + Fmt1(f.sellAt)
                + "\n§7Profit each §6" + Fmt.coins(f.profitEach) + "\n§7Volume §f" + Fmt.coins(f.hourlyVolume) + "/h"
                + (f.warnings.isEmpty() ? "" : "\n§c⚠ " + String.join(", ", f.warnings)) + "\n\n§eClick to open it in the Bazaar";
        return Chat.clickable(text, "/bz " + f.name, hover);
    }

    public static Hud.Lines hudLines() {
        Hud.Lines out = new Hud.Lines();
        if (Config.get().bazaarHud) addOrderLines(out);
        Auctions.addHudLines(out);
        if (out.isEmpty()) return out;
        double today = profitSince(startOfDay());
        if (today != 0 && Config.get().bzShowToday) out.add("§7Flip profit today: " + (today >= 0 ? "§6+" : "§c") + Fmt.coins(today));
        return out;
    }

    private static void addOrderLines(Hud.Lines out) {
        List<Order> orders = state().orders;
        if (orders.isEmpty()) return;
        double tied = 0;
        for (Order o : orders) tied += (o.qty - o.claimed) * o.price;
        out.add("§6§lBazaar §7" + orders.size() + " order" + (orders.size() > 1 ? "s" : "") + " §8(" + Fmt.coins(tied) + ")");
        int shown = 0;
        for (Order o : orders) {
            int max = Config.get().bzHudMaxOrders;
            if (shown++ >= max) { out.add("§8 ...and " + (orders.size() - max) + " more (/flips orders)"); break; }
            out.add(orderLine(o));
        }
    }

    /** An Auction House buy (BIN or won bid): kept like a Bazaar buy, so selling it later logs the flip profit. */
    static void addBought(String name, int qty, double totalCoins) {
        Lot l = new Lot();
        l.name = name; l.qty = Math.max(1, qty); l.cost = totalCoins / Math.max(1, qty);
        state().lots.add(l);
        traded(totalCoins);
        save();
    }

    /** Coins collected from an Auction House sale. */
    static void logAuctionSale(String name, int qty, double coins) {
        logSale(name, qty, coins, coins / Math.max(1, qty));
        traded(coins);
        save();
    }

    public static String orderLine(Order o) {
        String side = o.type.equals("buy") ? "§bBuy " : "§dSell ";
        String status = switch (o.status) {
            case "filled" -> "§e✔ filled, claim it";
            case "outbid" -> "§c✖ outbid → " + Fmt1(o.suggested) + (o.note != null ? " §8(" + o.note + ")" : "");
            case "undercut" -> "§c✖ undercut → " + Fmt1(o.suggested) + (o.note != null ? " §8(" + o.note + ")" : "");
            case "top" -> "§a✔";
            default -> "§8…";
        };
        return " " + side + "§f" + (o.qty - o.claimed) + "x " + o.name + " §7@" + Fmt1(o.price) + " " + status;
    }

    public static double profitSince(long t) {
        double sum = 0;
        for (Entry e : state().log) if (e.time >= t) sum += e.profit;
        return sum;
    }

    public static long startOfDay() {
        return java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    // ================= helpers =================

    static void ping() {
        if (Config.get().bzSound) Chat.ping();
    }

    static String Fmt1(double v) { return String.format(Locale.US, "%,.1f", v); }

    private static double num(String s) { return Double.parseDouble(s.replace(",", "")); }

    private static double round1(double v) { return Math.round(v * 10) / 10.0; }

    public static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Files.writeString(FILE, Config.GSON.toJson(state));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save bazaar state", e);
        }
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) state = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<State>() {}.getType());
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not read bazaar state", e);
        }
        if (state == null) state = new State();
        if (state.orders == null) state.orders = new ArrayList<>();
        if (state.lots == null) state.lots = new ArrayList<>();
        if (state.log == null) state.log = new ArrayList<>();
        for (Order o : state.orders) { o.status = o.filled ? "filled" : "unknown"; }
    }

    private Bazaar() {}
}
