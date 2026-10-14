package dev.farmprofit;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What the mod can and can't see right now: the checks behind the Status page (Features tab) and /profit debug chat,
 * plus a log of Hypixel messages that looked relevant but weren't understood, so support for them can be added.
 */
public final class Debug {
    private static final Path LOG = Config.DIR.resolve("unrecognised-messages.txt");
    private static final Pattern INTERESTING = Pattern.compile(
            "(?i)drop!|catch!|slayer|shard|trophy|offer accepted|pest|tree gift|contest|pristine|reward|secrets|kuudra|burrow|visitor|treasure|lockpick|2x powder|pickaxe ability|is now available|approaching|stashed|commission|goblin|inquisitor|reboot|closing");
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

    /** One thing the mod checks. ok: true = works, false = doesn't, null = not known yet (nothing has used it so far). */
    public record Check(String group, String name, Boolean ok, String note, String fix) {}

    public static List<Check> checks() {
        List<Check> c = new ArrayList<>();
        String reading = "Reading the game";
        c.add(new Check(reading, "Tab list", !Tracker.tab.isEmpty(), Tracker.tab.size() + " lines",
                "Hypixel's tab list widgets must be on (/widget). The mod reads area, stats and events from them."));
        boolean located = Tracker.areaName != null || HypixelLocation.mode != null;
        c.add(new Check(reading, "Location", located,
                (HypixelLocation.mode != null ? "Mod API mode " + HypixelLocation.mode : "tab Area " + Tracker.areaName) + " → HUD: " + Tracker.area,
                "Turn on the Info widget ('Area: ...') in /widget, or install the Hypixel Mod API."));
        c.add(new Check(reading, "Hypixel Mod API", HypixelLocation.active ? Boolean.TRUE : null,
                HypixelLocation.active ? "connected" : "not installed (optional)",
                "Optional: install 'Hypixel Mod API' (Fabric) from Modrinth for exact location."));
        c.add(new Check(reading, "Sidebar", Tracker.purse >= 0,
                "purse " + (Tracker.purse >= 0 ? Fmt.num(Tracker.purse) : "unknown") + ", dungeon floor " + Tracker.dungeonFloor,
                "Make sure the scoreboard sidebar is visible (it gives purse and dungeon info)."));

        boolean xp = SEEN.containsKey("skill xp"), ability = SEEN.containsKey("pickaxe ability used") || SEEN.containsKey("pickaxe ability ready");
        c.add(new Check(reading, "Skill XP numbers", xp ? Boolean.TRUE : null, xp ? "seen" : "none seen yet (gain some skill XP)",
                "Hypixel shows '+12 Farming (123/456)' above your hotbar when you gain skill XP. The skill XP/h line reads it."));
        c.add(new Check(reading, "Pickaxe ability messages", ability ? Boolean.TRUE : null, ability ? "seen" : "none seen yet (use your pickaxe ability)",
                "Hypixel's 'You used your ... Pickaxe Ability!' and '... is now available!' messages. The ready alert reads them."));

        String[] alertKinds = {"worm spawn", "rare sea creature", "drop alert", "stash", "pest spawn", "visitor arrived", "commission done",
                "goblin spawn", "inquisitor", "cold alert", "server restart", "slayer boss spawn", "event alert"};
        List<String> seenAlerts = new ArrayList<>();
        for (String k : alertKinds) if (SEEN.containsKey(k)) seenAlerts.add(k);
        c.add(new Check(reading, "Alert messages", seenAlerts.isEmpty() ? null : Boolean.TRUE,
                seenAlerts.isEmpty() ? "none seen yet (they show up when the events happen)" : "seen: " + String.join(", ", seenAlerts),
                "Scatha / worm spawns, rare sea creatures, pet drops, stash, pests, commissions, goblins, cold and server restarts are read from Hypixel's messages and sidebar."));

        boolean chest = SEEN.containsKey("reward chest");
        c.add(new Check(reading, "Dungeon / Kuudra chests", chest ? Boolean.TRUE : null,
                chest ? "counted " + SEEN.get("reward chest") + " time" + (SEEN.get("reward chest") == 1 ? "" : "s") : "none opened yet (open a reward chest after a run)",
                "The contents and the price are read from the chest window when you click 'Open Reward Chest' and added to the Dungeons / Kuudra profit. If an opened chest is never counted, Hypixel changed that window."));

        int places = CrystalMap.placeCount();
        c.add(new Check(reading, "Crystal Hollows map", CrystalMap.drawn ? Boolean.TRUE : null,
                CrystalMap.drawn ? (places > 0 ? Health.count(places, "places") + " found in this lobby" : "drawn, no places found yet")
                        : (Config.get().crystalMap ? "shows when you are in the Crystal Hollows" : "off in the settings"),
                "The minimap learns places from the sidebar's place name when you walk into one, the speech of the NPC that lives there and party coordinates."));

        String hooks = "Hooks into the game";
        c.add(new Check(hooks, "Particle hook", Lockpick.hooked ? Boolean.TRUE : null, Lockpick.hooked ? "sees particles" : "no particles seen yet",
                "Needed for the lockpick helper and Diana burrows. If it never turns green in a place with particles, Minecraft renamed the method."));
        c.add(new Check(hooks, "Glow outline hook", Glow.hooked ? Boolean.TRUE : null, Glow.hooked ? "working" : "not used yet", "Pest / mob outlines."));
        c.add(new Check(hooks, "Sound hook", Sounds.hooked ? Boolean.TRUE : null, Sounds.hooked ? "sees sounds" : "no sounds seen yet", "Needed to mute sounds."));
        c.add(new Check(hooks, "Menu overlay", ScreenOverlay.works, ScreenOverlay.works == null ? "open a menu" : "",
                "Draws rarity colors, item labels, search highlights and terminal solvers over menus."));
        c.add(new Check(hooks, "Rarity colors", RarityBg.canDrawItems,
                RarityBg.canDrawItems == null ? "open a menu" : RarityBg.canDrawItems ? "behind the items" : "light tint only (can't redraw items here)",
                "Colored slot backgrounds behind items."));
        c.add(new Check(hooks, "Inventory search box", InvSearch.works, InvSearch.works == null ? "open a chest or inventory" : "",
                "The search box under inventories and chests."));
        c.add(new Check(hooks, "Search box typing", InvSearch.keysWork, InvSearch.keysWork == null ? "open a chest or inventory" : "",
                "Keeps keys like E from closing the menu while you type in the search box."));
        c.add(new Check(hooks, "Inventory buttons", InvButtons.works,
                InvButtons.works == null ? (Config.get().invButtons ? "open your inventory" : "off in the settings") : "",
                "The quick-command buttons beside your inventory (Settings → Items → Inventory)."));
        c.add(new Check(hooks, "Keep cursor between menus", CursorKeeper.works, CursorKeeper.works == null ? "not needed yet" : "",
                "Keeps the mouse pointer in place when one SkyBlock menu replaces another."));
        c.add(new Check(hooks, "HUD item icons", iconsWork, iconsWork == null ? "not drawn yet" : "", "Item pictures next to HUD lines (turn off with /profit icons)."));
        c.add(new Check(hooks, "HUD panel scaling", scaleWorks, scaleWorks == null ? "not used yet" : "", "Per-panel sizes (middle-click a panel with chat open)."));
        c.add(new Check(hooks, "Mouse over the HUD", mouseWorks, mouseWorks == null ? "open chat" : "", "Dragging panels with chat open."));
        c.add(new Check(hooks, "Shaders", Boolean.TRUE, Glow.shadersOn() ? "on (highlights use particle boxes)" : "off", ""));

        String known = "What it has learned";
        c.add(new Check(known, "Item IDs learned", !ItemIds.LEARNED.isEmpty() ? Boolean.TRUE : null, Health.count(ItemIds.LEARNED.size(), "items"),
                "Learned from the items you see in menus and inventories."));
        c.add(new Check(known, "Attribute shards", Shards.count() > 0, Health.count(Shards.count(), "shards"),
                "Comes with the recipe data (Data tab)."));
        c.add(new Check(known, "Accessories", Accessories.count() > 0, Health.count(Accessories.count(), "accessories"),
                "Comes with the recipe data (Data tab)."));
        c.add(new Check(known, "Pests right now", Boolean.TRUE, String.valueOf(Pests.count()) + (Tracker.FARMING.equals(Tracker.area) ? "" : " (only searched in the Garden)"), ""));
        return c;
    }

    /** "§a✔", "§c✖" or "§8?" */
    public static String mark(Boolean b) { return b == null ? "§8?" : b ? "§a✔" : "§c✖"; }

    /** /profit debug chat: a short summary in chat (the full list is on the Status page). */
    public static void show() {
        List<Check> checks = checks();
        int ok = 0, bad = 0, unknown = 0;
        for (Check c : checks) { if (c.ok() == null) unknown++; else if (c.ok()) ok++; else bad++; }
        Tracker.say("§6§l[SkyAssist] Debug §7- " + ok + " working" + (bad > 0 ? ", §c" + bad + " not working§7" : "") + (unknown > 0 ? ", §8" + unknown + " not known yet§7" : ""));
        for (Check c : checks) {
            if (c.ok() != null && c.ok()) continue;
            Tracker.say(" " + mark(c.ok()) + " §7" + c.name() + (c.note().isEmpty() ? "" : " §8(" + c.note() + ")"));
        }
        for (Health.Source s : Health.sources()) {
            Health.State st = s.state();
            if (st == Health.State.FAILED || st == Health.State.STALE) Tracker.say(" §c✖ §7" + s.name + " §8(" + s.error + ")");
        }
        StringBuilder seen = new StringBuilder(" §7Messages recognised: ");
        if (SEEN.isEmpty()) seen.append("§8none yet");
        SEEN.forEach((k, v) -> seen.append("§f").append(k).append(" §8x").append(v).append("§7, "));
        Tracker.say(seen.toString());
        if (!lastActionBar.isEmpty()) Tracker.say(" §7Last action bar: §f" + lastActionBar);
        int problems = Safe.faultCount();
        if (problems > 0) Tracker.say(" §6" + problems + " feature" + (problems == 1 ? "" : "s") + " hit problems and " + (problems == 1 ? "was" : "were") + " skipped.");
        Tracker.say(" §8Everything in detail: §f/skyassist status§8. Unrecognised messages are saved to config/skyassist/unrecognised-messages.txt.");
    }

    private Debug() {}
}
