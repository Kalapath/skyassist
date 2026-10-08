package dev.farmprofit;

import com.google.gson.reflect.TypeToken;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Your Auction House activity on the flip HUD: listings (active / sold / expired / undercut) and bids (top / outbid / won).
 * Read from chat and from the Manage Auctions / Your Bids menus. Items bought on the AH count as flip buys,
 * so selling them later (AH or Bazaar) shows up in the flip profit. Display-only: nothing is clicked or listed for you.
 */
public final class Auctions {
    private static final String NUM = "([\\d,]+(?:\\.\\d+)?)";
    private static final Pattern BIN_BOUGHT = Pattern.compile("^You purchased (?:(\\d+)x )?(.+?) for " + NUM + " coins!");
    private static final Pattern SOLD = Pattern.compile("^\\[Auction\\] (\\S+) bought (?:(\\d+)x )?(.+?) for " + NUM + " coins");
    private static final Pattern COLLECTED = Pattern.compile("^You collected " + NUM + " coins from selling (?:(\\d+)x )?(.+?) to (\\S+) in an auction");
    private static final Pattern STARTED = Pattern.compile("^(BIN )?Auction started for (?:(\\d+)x )?(.+?)!");
    private static final Pattern BID = Pattern.compile("^Bid of " + NUM + " coins placed for (?:(\\d+)x )?(.+?)!");
    private static final Pattern OUTBID = Pattern.compile("^\\[Auction\\] (\\S+) outbid you by " + NUM + " coins for (?:(\\d+)x )?(.+?) CLICK");
    private static final Pattern CLAIMED = Pattern.compile("^You claimed (?:(\\d+)x )?(.+?) from (\\S+?)'s? auction!");
    private static final Pattern CANCELLED = Pattern.compile("(?i)^You cancelled your auction(?: for (?:(\\d+)x )?(.+?))?[!.]");
    private static final Pattern LORE_PRICE = Pattern.compile("(?i)(Buy it now|Starting bid|Top bid|Item price|Highest bid|Your bid):\\s*" + NUM + " coins");
    private static final Pattern LORE_ENDS = Pattern.compile("(?i)Ends in:\\s*(.+)");
    private static final Pattern DURATION = Pattern.compile("(\\d+)\\s*([dhms])");

    public static final class Listing {
        public String name;
        public int qty = 1;
        public double price;              // BIN price, or top/starting bid
        public boolean bin;
        public long created, endsAt;      // endsAt 0 = unknown
        public String status = "active";  // active / sold / expired
        public String buyer;
    }

    public static final class Bid {
        public String name;
        public double amount;
        public long time, endsAt;
        public String status = "top";     // top / outbid / won / ended
    }

    public static final class State {
        public List<Listing> listings = new ArrayList<>();
        public List<Bid> bids = new ArrayList<>();
    }

    private static final Path FILE = Config.DIR.resolve("auctions.json");
    private static State state;
    private static double pendingPrice;   // last price seen in the create-auction menu
    private static long pendingTime;

    public static State state() {
        if (state == null) load();
        return state;
    }

    public static boolean active() {
        State s = state();
        return !s.listings.isEmpty() || !s.bids.isEmpty();
    }

    // ---------------- chat ----------------

    static boolean onChat(String msg) {
        State st = state();
        Matcher m;
        long now = System.currentTimeMillis();
        if ((m = BIN_BOUGHT.matcher(msg)).find()) {
            int qty = qty(m.group(1));
            Bazaar.addBought(m.group(2).trim(), qty, num(m.group(3)));          // counts as a flip buy
            save(); return true;
        }
        if ((m = STARTED.matcher(msg)).find()) {
            Listing l = new Listing();
            l.bin = m.group(1) != null;
            l.qty = qty(m.group(2));
            l.name = m.group(3).trim();
            l.created = now;
            if (now - pendingTime < 120_000) l.price = pendingPrice;
            st.listings.add(l);
            save(); return true;
        }
        if ((m = SOLD.matcher(msg)).find()) {
            Listing l = findListing(m.group(3).trim(), "active");
            if (l != null) { l.status = "sold"; l.buyer = m.group(1); l.price = num(m.group(4)); }
            Tracker.say("§6[Flips] §a" + m.group(3).trim() + " sold for " + Fmt.coins(num(m.group(4))) + " §7- claim it in /ah.");
            Bazaar.ping();
            save(); return true;
        }
        if ((m = COLLECTED.matcher(msg)).find()) {
            double coins = num(m.group(1));
            int qty = qty(m.group(2));
            String name = m.group(3).trim();
            Listing l = findListing(name, "sold");
            if (l == null) l = findListing(name, "active");
            if (l != null) st.listings.remove(l);
            Bazaar.logAuctionSale(name, qty, coins);
            save(); return true;
        }
        if ((m = BID.matcher(msg)).find()) {
            String name = m.group(3).trim();
            Bid b = findBid(name);
            if (b == null) { b = new Bid(); b.name = name; st.bids.add(b); }
            b.amount = num(m.group(1));
            b.time = now;
            b.status = "top";
            save(); return true;
        }
        if ((m = OUTBID.matcher(msg)).find()) {
            Bid b = findBid(m.group(4).trim());
            if (b != null) b.status = "outbid";
            Bazaar.ping();
            save(); return true;
        }
        if ((m = CLAIMED.matcher(msg)).find()) {
            String name = m.group(2).trim();
            Bid b = findBid(name);
            if (b != null) {
                Bazaar.addBought(name, qty(m.group(1)), b.amount);               // won bid = flip buy
                st.bids.remove(b);
            }
            save(); return true;
        }
        if ((m = CANCELLED.matcher(msg)).find()) {
            Listing l = m.group(2) != null ? findListing(m.group(2).trim(), null) : null;
            if (l == null) {                                                     // unnamed: drop the oldest unsold one
                for (Listing x : st.listings) if (!"sold".equals(x.status)) { l = x; break; }
            }
            if (l != null) st.listings.remove(l);
            save(); return true;
        }
        return false;
    }

    // ---------------- menus ----------------

    static void scanMenu(String title, List<ItemStack> items) {
        if (title.contains("Create") && title.contains("Auction")) {          // remember the price you're setting
            for (ItemStack is : items) for (String l : ItemIds.lore(is)) {
                Matcher p = LORE_PRICE.matcher(l);
                if (p.find()) { pendingPrice = num(p.group(2)); pendingTime = System.currentTimeMillis(); }
            }
            return;
        }
        boolean manage = title.contains("Manage Auctions"), bids = title.contains("Your Bids");
        if (!manage && !bids) return;
        long now = System.currentTimeMillis();
        List<Listing> listings = new ArrayList<>();
        List<Bid> bidList = new ArrayList<>();
        for (ItemStack is : items) {
            List<String> lore = ItemIds.lore(is);
            String all = String.join("\n", lore);
            if (!all.contains("coins") || !(all.contains("Ends in") || all.contains("Status") || all.contains("Ended") || all.contains("Expired") || all.contains("Sold"))) continue;
            String name = Tracker.strip(is.getHoverName().getString()).trim();
            double price = 0, yourBid = 0, top = 0;
            boolean bin = false;
            long ends = 0;
            for (String l : lore) {
                Matcher p = LORE_PRICE.matcher(l);
                if (p.find()) {
                    String k = p.group(1).toLowerCase(Locale.ROOT);
                    double v = num(p.group(2));
                    if (k.equals("your bid")) yourBid = v;
                    else { if (k.equals("buy it now")) bin = true; if (k.startsWith("top") || k.startsWith("highest")) top = v; if (price == 0 || k.startsWith("top") || k.startsWith("highest")) price = v; }
                }
                Matcher e = LORE_ENDS.matcher(l);
                if (e.find()) { long d = duration(e.group(1)); if (d > 0) ends = now + d; }
            }
            String low = all.toLowerCase(Locale.ROOT);
            if (manage) {
                Listing l = new Listing();
                l.name = name; l.price = price; l.bin = bin; l.endsAt = ends; l.qty = Math.max(1, is.getCount());
                l.status = low.contains("sold") ? "sold" : (low.contains("expired") || low.contains("ended")) && !low.contains("ends in") ? "expired" : "active";
                Listing old = findListing(name, null);
                l.created = old != null ? old.created : now;
                listings.add(l);
            } else {
                Bid b = new Bid();
                b.name = name; b.amount = yourBid > 0 ? yourBid : price; b.endsAt = ends; b.time = now;
                boolean ended = (low.contains("ended") || low.contains("expired")) && !low.contains("ends in");
                b.status = ended ? (top > 0 && yourBid >= top ? "won" : "ended") : (top > 0 && yourBid > 0 && yourBid < top ? "outbid" : "top");
                bidList.add(b);
            }
        }
        if (manage) state().listings = listings;          // the menu is the truth
        else state().bids = bidList;
        save();
    }

    // ---------------- HUD ----------------

    static void addHudLines(Hud.Lines out) {
        Config c = Config.get();
        if (!c.auctionHud) return;
        State st = state();
        long now = System.currentTimeMillis();
        expire(now);
        if (st.listings.isEmpty() && st.bids.isEmpty()) return;
        double listed = 0;
        for (Listing l : st.listings) if (!"sold".equals(l.status)) listed += l.price;
        out.add("§6§lAuctions §7" + st.listings.size() + " listed" + (listed > 0 ? " §8(" + Fmt.coins(listed) + ")" : "")
                + (st.bids.isEmpty() ? "" : " §7" + st.bids.size() + " bid" + (st.bids.size() > 1 ? "s" : "")));
        int shown = 0, max = c.ahHudMax;
        List<String> rows = new ArrayList<>();
        for (Listing l : st.listings) rows.add(listingLine(l, now));
        for (Bid b : st.bids) rows.add(bidLine(b, now));
        for (String r : rows) {
            if (shown++ >= max) { out.add("§8 ...and " + (rows.size() - max) + " more (/ah)"); break; }
            out.add(r);
        }
    }

    private static String listingLine(Listing l, long now) {
        String status = switch (l.status) {
            case "sold" -> "§e✔ sold, claim it";
            case "expired" -> "§c✖ expired";
            default -> {
                if (l.bin && l.price > 0) {
                    String id = Prices.idFor(l.name);
                    double lowest = id != null ? Prices.binPrice(id) : 0;
                    if (lowest > 0 && lowest < l.price - 0.5) yield "§c✖ undercut → " + Fmt.coins(lowest);
                }
                yield l.endsAt > 0 ? "§a✔ §8" + Fmt.clock(Math.max(0, l.endsAt - now)) : "§a✔";
            }
        };
        return " " + (l.bin ? "§eBIN " : "§eAuc ") + "§f" + (l.qty > 1 ? l.qty + "x " : "") + l.name
                + (l.price > 0 ? " §7@" + Fmt.coins(l.price) : "") + " " + status;
    }

    private static String bidLine(Bid b, long now) {
        String status = switch (b.status) {
            case "outbid" -> "§c✖ outbid";
            case "won" -> "§e✔ won, claim it";
            case "ended" -> "§8ended";
            default -> b.endsAt > 0 ? "§a✔ top §8" + Fmt.clock(Math.max(0, b.endsAt - now)) : "§a✔ top";
        };
        return " §bBid §f" + b.name + " §7@" + Fmt.coins(b.amount) + " " + status;
    }

    private static void expire(long now) {
        for (Listing l : state().listings) if ("active".equals(l.status) && l.endsAt > 0 && now > l.endsAt) l.status = "expired";
        for (Iterator<Bid> it = state().bids.iterator(); it.hasNext(); ) {
            Bid b = it.next();
            if (b.endsAt > 0 && now > b.endsAt && !"won".equals(b.status)) {
                if ("top".equals(b.status)) b.status = "won";                  // you were on top when it ended
                else if (now > b.endsAt + 3_600_000) it.remove();               // lost: drop it after an hour
            }
            else if (b.endsAt == 0 && now - b.time > 14L * 24 * 3_600_000) it.remove();
        }
    }

    // ---------------- helpers ----------------

    private static Listing findListing(String name, String status) {
        for (Listing l : state().listings) if (l.name.equalsIgnoreCase(name) && (status == null || status.equals(l.status))) return l;
        return null;
    }

    private static Bid findBid(String name) {
        for (Bid b : state().bids) if (b.name.equalsIgnoreCase(name)) return b;
        return null;
    }

    private static int qty(String s) { return s == null ? 1 : Math.max(1, Integer.parseInt(s)); }

    private static double num(String s) { return Double.parseDouble(s.replace(",", "")); }

    /** "1d 2h 3m", "5h", "30s" -> milliseconds. */
    static long duration(String s) {
        long ms = 0;
        Matcher m = DURATION.matcher(s);
        while (m.find()) {
            long n = Long.parseLong(m.group(1));
            ms += n * switch (m.group(2)) { case "d" -> 86_400_000L; case "h" -> 3_600_000L; case "m" -> 60_000L; default -> 1000L; };
        }
        return ms;
    }

    public static void clear() {
        state = new State();
        save();
    }

    static void save() {
        try { Files.createDirectories(Config.DIR); Files.writeString(FILE, Config.GSON.toJson(state())); } catch (Exception ignored) {}
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) state = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<State>() {}.getType());
        } catch (Exception ignored) {}
        if (state == null) state = new State();
        if (state.listings == null) state.listings = new ArrayList<>();
        if (state.bids == null) state.bids = new ArrayList<>();
    }

    private Auctions() {}
}
