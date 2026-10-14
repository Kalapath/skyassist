package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * "What's new": the notes of the latest versions as a menu page (/skyassist news), plus one line in chat the first time you
 * join a world after the mod was updated. A brand-new install just records its version and says nothing.
 */
public final class News {
    private record Entry(String version, String headline, String[] notes) {}

    /** Newest first. Keep to the last handful; CHANGELOG.md has everything. */
    private static final List<Entry> ENTRIES = List.of(
            new Entry("8.14.1", "Profit fixes", new String[]{
                    "Dungeon and Kuudra reward chests are counted properly: the contents (essence, enchanted books, armour, weapons, shards, pets) and the price (coins, keys) are read from the chest window when you open it and added to the run's profit once. Kuudra's Free / Paid chests work too; Kuudra keys are priced by what they cost to make (Settings → Combat → Kuudra key cost to set your own).",
                    "Bazaar \"Order Flipped!\" no longer reads the announced profit as a price; the sell offer's price is worked out from it. A sale of more items than you bought only counts the part you bought as a flip.",
                    "Auction flips subtract the listing fee (estimated: 1% / 2% / 2.5% for BINs by price, 5% for normal auctions). Switch: Settings → Market → Count AH listing fee.",
                    "PET DROP! pets are priced at the rarity of the drop (Legendary Golden Dragon, not an unknown item).",
                    "Profit per hour counts the whole time you are inside a dungeon or Kuudra's Hollow, and Diana tolerates longer gaps between burrows.",
                    "Status page: new \"Dungeon / Kuudra chests\" line, so you can see whether chests are being picked up."}),
            new Entry("8.14.0", "Alerts, a tidier settings menu and a Crystal Hollows map", new String[]{
                    "Alerts for the things people usually set up alerts for: Scatha / worm spawn (with direction and distance), rare sea creatures, pet and rare drops, stash, Garden pests and visitors, finished commissions, Golden / Diamond goblins, Mineshaft found, Minos Inquisitor, your slayer boss spawning, cold, server restart and Dark Auction / Cult of the Fallen Star starting.",
                    "Settings → Alerts lists every alert in one place, with a Test alert button that shows the real alert over the menu. Each alert also sits in its own activity tab.",
                    "Tidier settings: 14 tabs (Market and Chat & controls replace Bazaar, Extras and Chat & sounds), settings sorted by what they do, and sub-options indented and greyed while their main switch is off.",
                    "Crystal Hollows minimap (Settings → Mining → Crystal Hollows map): your position and facing on the 621×621 cave, the quarter you are in, Magma Fields shading, and the places and treasure chests it has learned. It only shows information; Hypixel's mod rules rule out anything that plays for you."}),
            new Entry("8.13.0", "Ideas from other SkyBlock mods", new String[]{
                    "Price alerts: /alert enchanted diamond below 1500 pings you when a Bazaar item crosses your price (/alerts lists them).",
                    "Pickaxe ability: cooldown on the Mining HUD and a ready alert.",
                    "Skill XP/h on the HUD, with time to the next level.",
                    "Slayer boss kill time, with your best and average.",
                    "Big on-screen alerts with a ding for all of these, plus an optional full-inventory warning.",
                    "Keep cursor between menus: the pointer stays put when SkyBlock swaps one menu for another.",
                    "Optional quick-command buttons beside your inventory (off by default; a command runs only when you click).",
                    "Upgrade value in item tooltips: enchants, potato books, recomb and gems at Bazaar prices (a rough extra, not a full price).",
                    "Optional yaw / pitch line on the Farming HUD. Every new option is in the settings, under its activity."}),
            new Entry("8.12.0", "Status page and safety net", new String[]{
                    "Status page (/skyassist status): every online source with its state, why it failed and a Retry button; problems that were caught; what the mod can see in the game; a Copy report button for asking for help.",
                    "Safety net: an error inside one feature is caught and listed on the Status page instead of crashing the game or kicking you from the server. A feature that keeps failing pauses for a minute, then tries again.",
                    "Downloads retry by themselves, waiting longer each time, and say what went wrong (no internet, rate limit, address changed).",
                    "Settings, history and totals are saved safely: a damaged file is kept as .broken next to the original instead of being silently replaced with defaults.",
                    "The update check follows the renamed Kalapath/skyassist repository. Both Minecraft versions build with the Gradle version they need.",
                    "New icon, and this What's new page."}),
            new Entry("8.11.1", "/market fixes", new String[]{
                    "/market updates live, waits for prices, copes with history/mayor data in different shapes, retries after a rate limit and shows the error if something is wrong. New Movers tab."}),
            new Entry("8.11.0", "Best flipping settings", new String[]{
                    "Best settings button (and /flips tune): picks flip filters for the most profit per bit of work from live prices and your budget. Undo (or /flips untune) puts yours back."}),
            new Entry("8.10.0", "Auctions on the flip HUD", new String[]{
                    "The flip HUD shows your Auction House listings (sold, expired, undercut) and bids (top, outbid, won). AH buys and sales count in flip profit."}),
            new Entry("8.9.0", "Craft flips know what you can craft", new String[]{
                    "Craft flips (Bazaar and AH) respect your collections, slayer levels, Heart of the Mountain tier and skills, read from the menus and level-up messages.",
                    "New settings: Only recipes I can craft (on), Hide when level unknown (off)."})
    );

    private static boolean checked;
    private static long joinedAt;

    /** "8.12.0" from "8.12.0+mc26.1.2"; empty if it can't be read. */
    static String current() {
        String v = Status.version("skyassist");
        if (v == null) return "";
        int plus = v.indexOf('+');
        return plus > 0 ? v.substring(0, plus) : v;
    }

    public static Screen screen(Screen parent) {
        MenuScreen[] ref = new MenuScreen[1];
        ref[0] = new MenuScreen("What's new in SkyAssist " + current(), List.of(new Tab("What's new", () -> page(ref))), 0, parent);
        return ref[0];
    }

    private static Page page(MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        boolean first = true;
        for (Entry e : ENTRIES) {
            if (!first) rows.add(new Row("", ""));
            rows.add(new Row("§6§l" + e.version(), "§f§l" + e.headline()));
            for (String n : e.notes()) rows.add(new Row("", "§7• " + n));
            first = false;
        }
        List<Action> top = List.of(
                new Action("Status page", "Is everything loading? Every data source, problems that were caught and what the mod can see.",
                        () -> Compat.setScreen(Minecraft.getInstance(), Status.screen(ref[0]))),
                new Action("Settings", "Open the settings.", () -> Compat.setScreen(Minecraft.getInstance(), new SettingsScreen(ref[0]))));
        return new Page(new String[]{"", ""}, new int[]{50, 410}, rows, top,
                List.of("§8The full list of changes is CHANGELOG.md in the GitHub repo. The chat notice after an update can be turned off in the settings (General → Updates)."));
    }

    /** Every tick: once, about 10 seconds after joining a world, says so if the mod was updated since you last played. */
    static void tick(Minecraft mc) {
        if (checked) return;
        if (mc.player == null) { joinedAt = 0; return; }
        long now = System.currentTimeMillis();
        if (joinedAt == 0) joinedAt = now;
        if (now - joinedAt < 10_000) return;
        checked = true;
        String version = current();
        if (version.isEmpty()) return;
        Config c = Config.get();
        if (version.equals(c.lastSeenVersion)) return;
        boolean updated = c.setupDone || !c.lastSeenVersion.isEmpty();     // not a fresh install
        c.lastSeenVersion = version;
        Config.save();
        if (!updated || !c.showNews || ENTRIES.isEmpty()) return;
        Entry latest = ENTRIES.get(0);
        String about = latest.version().equals(version) ? "§f" + latest.headline() : "§fa new version";
        Tracker.say(Chat.clickable("§6§l[SkyAssist] §7Updated to §f" + version + "§7: " + about + " §a§n[What's new]",
                "/skyassist news", "Shows what changed in the latest versions."));
    }

    private News() {}
}
