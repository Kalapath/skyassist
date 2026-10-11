package dev.farmprofit;

import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Bazaar price alerts: "/alert enchanted diamond below 1500" pings you (and shows big text) when the price crosses your number.
 * Checked every time fresh Bazaar prices arrive (as often as 'Order check every' while alerts exist). Display only: it
 * never places an order; the chat line has a button that opens the item in the Bazaar when YOU click it.
 */
public final class PriceAlerts {
    public static final class Alert {
        public String id, name, side = "buy";
        public boolean below = true, fired;
        public double price;
        public long created, firedAt;
    }

    private static final int MAX = 30;
    private static final Path FILE = Config.DIR.resolve("price-alerts.json");
    private static final Pattern SYNTAX = Pattern.compile("^(.+?)\\s+(below|under|<=?|above|over|>=?)\\s*(\\S+?)(?:\\s+(buy|sell))?$", Pattern.CASE_INSENSITIVE);
    private static List<Alert> alerts;
    private static volatile long lastCheck;
    private static boolean pending;

    public static List<Alert> list() {
        if (alerts == null) load();
        return alerts;
    }

    /** Should the Bazaar be refreshed often? (Only while you're in a world and an alert is still waiting.) */
    static boolean watching() {
        if (!Config.get().priceAlerts || Minecraft.getInstance().player == null) return false;
        for (Alert a : list()) if (!a.fired) return true;
        return false;
    }

    // ---------- checking ----------

    /** Fresh Bazaar prices arrived. */
    static void onPrices() {
        lastCheck = System.currentTimeMillis();
        pending = true;
    }

    /** Every tick: once prices have arrived and you're in a world, check the alerts. */
    static void tick(Minecraft mc) {
        if (!pending || mc.player == null) return;
        pending = false;
        if (Config.get().priceAlerts) evaluate();
    }

    /** The price an alert watches, or NaN: 'buy' = what you pay to buy right now, 'sell' = what you get selling right now. */
    static double current(String id, String side) {
        int i = "sell".equals(side) ? 0 : 1;
        double[] book = Prices.BOOK.get(id);
        if (book != null && book[i] > 0) return book[i];
        double[] raw = Prices.bazaarRaw(id);
        return raw != null && raw[i] > 0 ? raw[i] : Double.NaN;
    }

    private static void evaluate() {
        boolean changed = false;
        for (Alert a : new ArrayList<>(list())) {
            if (a.fired) continue;
            double v = current(a.id, a.side);
            if (Double.isNaN(v)) continue;
            if (a.below ? v > a.price : v < a.price) continue;
            a.fired = true;
            a.firedAt = System.currentTimeMillis();
            changed = true;
            Debug.saw("price alert");
            String when = (a.below ? "below " : "above ") + Fmt.coins(a.price);
            Alerts.notify("price-" + a.id + a.side, "§e§lPrice alert", "§f" + a.name + " §7" + a.side + " price " + Fmt.coins(v),
                    Chat.clickable("§6[Alert] §f" + a.name + " §7" + a.side + " price is §a" + Fmt.coins(v) + " §7(" + when + ") §a§n[Open in Bazaar]",
                            "/bz " + a.name, "Opens this item in the Bazaar. You place the order yourself."));
        }
        if (changed) save();
    }

    // ---------- adding / removing ----------

    /** "/alert <item> below|above <price> [buy|sell]" */
    static void add(String text) {
        Matcher m = SYNTAX.matcher(text.trim());
        if (!m.matches()) { usage(); return; }
        String query = m.group(1).trim();
        String op = m.group(2).toLowerCase(Locale.ROOT);
        boolean below = op.equals("below") || op.equals("under") || op.startsWith("<");
        double price = Calc.eval(m.group(3));
        if (Double.isNaN(price) || price <= 0) { Tracker.say("§6[Alert] §c\"" + m.group(3) + "\" isn't a price. §7Examples: 1500, 25k, 1.2m"); return; }
        String side = m.group(4) == null ? "buy" : m.group(4).toLowerCase(Locale.ROOT);
        if (!Prices.loaded()) { Tracker.say("§6[Alert] §7Bazaar prices are still loading, try again in a moment."); return; }
        List<String> options = new ArrayList<>();
        String id = resolve(query, options);
        if (id == null) {
            if (options.isEmpty()) Tracker.say("§6[Alert] §cNo Bazaar item matches \"" + query + "\".");
            else {
                Tracker.say("§6[Alert] §7\"" + query + "\" could be several items, be a bit more exact:");
                for (String o : options) Tracker.say(" §f" + o);
            }
            return;
        }
        String name = Prices.nameOf(id);
        List<Alert> all = list();
        Alert a = null;
        for (Alert x : all) if (x.id.equals(id) && x.side.equals(side) && x.below == below) a = x;      // same alert: just change the price
        if (a == null) {
            if (all.size() >= MAX) { Tracker.say("§6[Alert] §cYou already have " + MAX + " alerts. Remove one with /alert remove <number>."); return; }
            a = new Alert();
            a.id = id; a.name = name; a.side = side; a.below = below; a.created = System.currentTimeMillis();
            all.add(a);
        }
        a.price = price;
        a.fired = false;
        save();
        double now = current(id, side);
        Tracker.say("§6[Alert] §7You'll be told when §f" + name + " §7" + side + " price goes " + (below ? "below" : "above") + " §a" + Fmt.coins(price)
                + (Double.isNaN(now) ? "" : " §8(now " + Fmt.coins(now) + ")"));
        if (Config.get().priceAlerts) { pending = false; evaluate(); }
        else Tracker.say("§6[Alert] §8Price alerts are switched off in the settings (Bazaar → Price alerts); nothing will ping until you turn them on.");
    }

    private static void usage() {
        Tracker.say("§6[Alert] §7Use: §f/alert <item> below|above <price> [buy|sell]");
        Tracker.say(" §8e.g. §7/alert enchanted diamond below 1500  §8·  §7/alert booster cookie above 5m sell");
        Tracker.say(" §8buy = what you pay to buy now (default), sell = what you get selling now. §7/alerts §8lists them.");
    }

    static void remove(int number) {
        List<Alert> all = list();
        if (number < 1 || number > all.size()) { Tracker.say("§6[Alert] §7There is no alert number " + number + ". §f/alerts §7shows them."); return; }
        Alert a = all.remove(number - 1);
        save();
        Tracker.say("§6[Alert] §7Removed: §f" + a.name + " " + (a.below ? "below " : "above ") + Fmt.coins(a.price));
    }

    static void clear() {
        int n = list().size();
        list().clear();
        save();
        Tracker.say("§6[Alert] §7" + (n == 0 ? "No alerts to clear." : "Removed all " + n + " alerts."));
    }

    /** A Bazaar item ID for what you typed (an ID, an exact name, or a part of a name that fits only one item). Else null, with the likely ones in 'options'. */
    static String resolve(String query, List<String> options) {
        String asId = query.toUpperCase(Locale.ROOT).replace(' ', '_');
        if (Prices.BOOK.containsKey(asId)) return asId;
        String known = Prices.idFor(query);
        if (known != null && Prices.BOOK.containsKey(known)) return known;
        String q = query.toLowerCase(Locale.ROOT).trim();
        List<String> exact = new ArrayList<>(), starts = new ArrayList<>(), part = new ArrayList<>();
        for (String id : Prices.BOOK.keySet()) {
            String n = Prices.nameOf(id).toLowerCase(Locale.ROOT);
            if (n.equals(q)) exact.add(id);
            else if (n.startsWith(q)) starts.add(id);
            else if (n.contains(q)) part.add(id);
        }
        for (List<String> tier : List.of(exact, starts, part)) {
            if (tier.size() == 1) return tier.get(0);
            if (tier.size() > 1) {
                tier.sort(Comparator.comparingInt((String id) -> Prices.nameOf(id).length()).thenComparing(id -> id));
                for (int i = 0; i < Math.min(6, tier.size()); i++) options.add(Prices.nameOf(tier.get(i)));
                return null;
            }
        }
        return null;
    }

    // ---------- the menu ----------

    public static Screen screen(Screen parent) {
        MenuScreen[] ref = new MenuScreen[1];
        ref[0] = new MenuScreen("Price alerts", List.of(new Tab("Alerts", () -> page(ref))), 0, parent)
                .liveWhile(() -> System.currentTimeMillis() - lastCheck < 1500);
        return ref[0];
    }

    private static Page page(MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        List<Alert> all = list();
        for (int i = 0; i < all.size(); i++) {
            Alert a = all.get(i);
            double now = Prices.loaded() ? current(a.id, a.side) : Double.NaN;
            String when = "§7" + a.side + " price " + (a.below ? "below " : "above ") + "§f" + Fmt.coins(a.price);
            List<Action> buttons = new ArrayList<>();
            if (a.fired) buttons.add(new Action("§aWatch again", "Re-arm this alert so it pings again.", () -> { a.fired = false; save(); ref[0].refresh(); }));
            buttons.add(new Action("§cRemove", "Delete this alert.", () -> { list().remove(a); save(); ref[0].refresh(); }));
            rows.add(new Row(new String[]{(i + 1) + ". §f" + a.name, (a.fired ? "§a✔ alerted  " : "") + when, Double.isNaN(now) ? "§8-" : "§f" + Fmt.coins(now)},
                    "buy = what you pay to buy right now; sell = what you get selling right now.", buttons));
        }
        List<Action> top = List.of(
                new Action("Add alert", "Opens chat with /alert ready to finish.", () -> Commands.typeInChat("/alert ")),
                new Action("Clear all", "Remove every alert.", () -> { list().clear(); save(); ref[0].refresh(); }));
        List<String> footer = new ArrayList<>();
        footer.add("§8/alert <item> below|above <price> [buy|sell]  e.g. /alert enchanted diamond below 1500");
        if (!Config.get().priceAlerts) footer.add("§eAlerts are switched off: Settings → Bazaar → Price alerts.");
        return new Page(new String[]{"Item", "Alert when", "Now"}, new int[]{170, 170, 60}, rows, top, footer);
    }

    // ---------- saving ----------

    private static void load() {
        try {
            if (Files.exists(FILE)) alerts = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<ArrayList<Alert>>() {}.getType());
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not read price-alerts.json (the old file is kept as .broken)", e);
            Config.backupBroken(FILE);
        }
        if (alerts == null) alerts = new ArrayList<>();
        alerts.removeIf(a -> a == null || a.id == null || a.name == null);
    }

    private static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Config.write(FILE, Config.GSON.toJson(list()));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save price alerts", e);
        }
    }

    private PriceAlerts() {}
}
