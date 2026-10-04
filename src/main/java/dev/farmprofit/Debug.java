package dev.farmprofit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** /profit debug: shows which detections work, and logs unrecognised Hypixel messages for fixing. */
public final class Debug {
    private static final Path LOG = Config.DIR.resolve("unrecognised-messages.txt");
    private static final Pattern INTERESTING = Pattern.compile(
            "(?i)drop!|catch!|slayer|shard|trophy|offer accepted|pest|tree gift|contest|pristine|reward|secrets|kuudra|burrow|visitor|treasure|lockpick|2x powder");
    /** How often each kind of message has been recognised. */
    public static final Map<String, Integer> SEEN = new LinkedHashMap<>();
    private static final Set<String> logged = new HashSet<>();
    public static String lastActionBar = "";
    public static Boolean iconsWork, scaleWorks, mouseWorks;

    public static void saw(String kind) { SEEN.merge(kind, 1, Integer::sum); }

    /** A game message that looked relevant but nothing understood it: save it so it can be supported. */
    public static void unrecognised(String text) {
        if (!INTERESTING.matcher(text).find()) return;
        String line = text.replace('\n', ' ').trim();
        if (line.length() > 300 || !logged.add(line) || logged.size() > 500) return;
        try {
            Files.createDirectories(Config.DIR);
            Files.writeString(LOG, line + System.lineSeparator(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {}
    }

    private static String ok(boolean b) { return b ? "§a✔" : "§c✖"; }
    private static String maybe(Boolean b) { return b == null ? "§8?" : ok(b); }

    public static void show() {
        Tracker.say("§6§l[SkyAssist] Debug");
        Tracker.say(" " + ok(!Tracker.tab.isEmpty()) + " §7Tab list read §8(" + Tracker.tab.size() + " lines)");
        Tracker.say(" " + ok(Tracker.areaName != null || HypixelLocation.mode != null) + " §7Location: §f"
                + (HypixelLocation.mode != null ? "Mod API mode=" + HypixelLocation.mode : "tab Area=" + Tracker.areaName)
                + " §7→ HUD §f" + Tracker.area);
        Tracker.say(" " + ok(HypixelLocation.active) + " §7Hypixel Mod API" + (HypixelLocation.active ? "" : " §8(install it for reliable location)"));
        Tracker.say(" " + ok(Tracker.purse >= 0) + " §7Sidebar read §8(purse " + (Tracker.purse >= 0 ? Fmt.num(Tracker.purse) : "unknown") + ", floor "
                + Tracker.dungeonFloor + ")");
        Tracker.say(" " + ok(Prices.loaded()) + " §7Prices: §f" + Prices.bazaarCount() + "§7 bazaar, §f" + Prices.itemCount()
                + "§7 items, §f" + Prices.binCount() + "§7 auction §8(" + ItemIds.LEARNED.size() + " item IDs learned)");
        Tracker.say(" " + ok(CraftCost.count() > 0) + " §7Recipes §8(" + CraftCost.count() + ")  " + maybe(ScreenOverlay.works) + " §7Menu overlay  " + maybe(InvSearch.keysWork) + " §7Search box keys");
        Tracker.say(" " + maybe(Lockpick.hooked ? Boolean.TRUE : null) + " §7Particle hook (lockpick)  " + maybe(Glow.hooked ? Boolean.TRUE : null) + " §7Glow hook  " + maybe(Sounds.hooked ? Boolean.TRUE : null) + " §7Sound hook");
        Tracker.say(" §7Rarity colors: " + (RarityBg.canDrawItems == null ? "§8? (open a menu)" : RarityBg.canDrawItems ? "§abehind items" : "§elight tint (can't redraw items here)"));
        Tracker.say(" §7Pests seen right now: §f" + Pests.count() + (Tracker.FARMING.equals(Tracker.area) ? "" : " §8(only searched in the Garden)"));
        Tracker.say(" §7Shaders: " + (Glow.shadersOn() ? "§eon §8(highlights use particle boxes)" : "§aoff") + "  §7Highlight style: §f" + Config.get().glowStyle);
        Tracker.say(" §7Attribute shards known: §f" + Shards.count() + " §8(Bazaar products with SHARD: " + Prices.BOOK.keySet().stream().filter(k -> k.contains("SHARD")).count() + ")");
        Tracker.say(" " + ok(Shards.count() > 0) + " §7Shards known §8(" + Shards.count() + ")  " + maybe(InvSearch.works) + " §7Inventory search");
        Tracker.say(" " + ok(Accessories.count() > 0) + " §7Accessories known §8(" + Accessories.count() + ")");
        Tracker.say(" " + ok(WorldPuzzles.quizCount() > 0) + " §7Quiz answers §8(" + WorldPuzzles.quizCount() + ")");
        Tracker.say(" " + ok(Enchants.count() > 0) + " §7Enchant max levels §8(" + Enchants.count() + ")");
        Tracker.say(" " + ok(Contests.loaded()) + " §7Jacob's contests  " + ok(Election.mayor != null) + " §7Mayor: §f" + Election.mayor);
        Tracker.say(" " + maybe(iconsWork) + " §7Icons  " + maybe(scaleWorks) + " §7Scale  " + maybe(mouseWorks) + " §7Mouse (open chat)");
        StringBuilder seen = new StringBuilder(" §7Messages recognised: ");
        if (SEEN.isEmpty()) seen.append("§8none yet");
        SEEN.forEach((k, v) -> seen.append("§f").append(k).append(" §8x").append(v).append("§7, "));
        Tracker.say(seen.toString());
        if (!lastActionBar.isEmpty()) Tracker.say(" §7Last action bar: §f" + lastActionBar);
        Tracker.say(" §8Unrecognised messages are saved to config/skyassist/unrecognised-messages.txt — send me that file to add support.");
    }

    private Debug() {}
}
