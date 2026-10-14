package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The "things you shouldn't miss" alerts, read from Hypixel's own messages and sidebar: Scatha / worm spawn, rare sea creatures,
 * pet and rare drops, stash, pests, visitors, finished commissions, goblins, Minos Inquisitor, cold, server restarts, your slayer boss
 * appearing, and the start of Dark Auction / Cult of the Fallen Star.
 * Each has its own switch in the settings (under its activity, and all together in Settings → Alerts); the look and the sound come from
 * {@link Alerts}. They only show text and ring: nothing in here ever does anything in the game for you.
 */
public final class EventAlerts {
    // ---------- what Hypixel says (plain text, colour codes already removed; the message formats are the ones other SkyBlock mods read) ----------
    private static final String WORM_MESSAGE = "You hear the sound of something approaching";
    private static final Pattern DROP = Pattern.compile("^((?:[A-Z]+ )*(?:DROP|CROP))! (.+)$");
    private static final Pattern STASH = Pattern.compile("^You have ([\\d,]+) (items?|materials?) stashed away!.*");
    /** (Hypixel puts a small bug icon between "A" and "Pest": a private font character, so anything short is allowed there.) */
    private static final Pattern PEST_ONE = Pattern.compile("^\\w+! A\\s+\\S{0,2}\\s*Pest has appeared in (?:Plot - )?(.+?)!$");
    private static final Pattern PEST_MANY = Pattern.compile("^\\w+! (\\d+)\\s+\\S{0,2}\\s*Pests? have spawned in (?:Plot - )?(.+?)!$");
    private static final Pattern PEST_AWAY = Pattern.compile("^\\w+! While you were offline,\\s+\\S{0,2}\\s*Pests? spawned in Plots? (.+?)!$");
    private static final Pattern VISITOR = Pattern.compile("^(.+?) has arrived on your Garden!$");
    private static final Pattern COMMISSION = Pattern.compile("^(.+?) Commission Complete! Visit (?:the )?(.+?) to claim your rewards!.*");
    private static final Pattern COLD = Pattern.compile("Cold: ?-?(\\d+)");
    private static final Pattern RESTART = Pattern.compile("Server closing: ?(\\d+):(\\d+)");

    /** Legendary / mythic sea creatures: Hypixel's message when one appears -> its name. */
    private static final Map<String, String> SEA_RARE = Map.ofEntries(
            Map.entry("Is this even a fish? It's the Carrot King!", "Carrot King"),
            Map.entry("The Water Hydra has come to test your strength.", "Water Hydra"),
            Map.entry("What is this creature!?", "Yeti"),
            Map.entry("A Reindrake forms from the depths.", "Reindrake"),
            Map.entry("The spirit of a long lost Phantom Fisher has come to haunt you.", "Phantom Fisher"),
            Map.entry("This can't be! The manifestation of death himself!", "Grim Reaper"),
            Map.entry("Hide no longer, a Great White Shark has tracked your scent and thirsts for your blood!", "Great White Shark"),
            Map.entry("An Abyssal Miner breaks out of the water!", "Abyssal Miner"),
            Map.entry("You hear a massive rumble as Thunder emerges.", "Thunder"),
            Map.entry("You have angered a legendary creature... Lord Jawbus has arrived.", "Lord Jawbus"),
            Map.entry("WOAH! A Plhlegblast appeared.", "Plhlegblast"),
            Map.entry("A long snout breaks the surface of the water. It's an Alligator!", "Alligator"),
            Map.entry("A massive Titanoboa surfaces. Its body stretches as far as the eye can see.", "Titanoboa"),
            Map.entry("The sky darkens and the air thickens. The end times are upon us: Ragnarok is here.", "Ragnarok"),
            Map.entry("A Fiery Scuttler inconspicuously waddles up to you, friends in tow.", "Fiery Scuttler"),
            Map.entry("The water bubbles and froths. A massive form emerges- you have disturbed the Wiki Tiki! You shall pay the price.", "Wiki Tiki"),
            Map.entry("A garish set of tentacles arise. It's a Blue Ringed Octopus!", "Blue Ringed Octopus"),
            Map.entry("The Loch Emperor arises from the depths.", "The Loch Emperor"),
            Map.entry("You've caused a disturbance in the loch. Could it be... Nessie?", "Nessie"),
            Map.entry("A Puddle Jumper is preparing for liftoff—cast your rod into it and hold on tight!", "Puddle Jumper"),
            Map.entry("Bow down before the Frog Prince... or pay the hefty price!", "Frog Prince"),
            Map.entry("Something zips through the air - it's a Silkbreeze!", "Silkbreeze"),
            Map.entry("A Giant Isopod was dredged up from the depths!", "Giant Isopod"));

    private static final Map<String, Long> LAST = new HashMap<>();

    /** True if this key hasn't fired within the last `ms` (and records it). */
    private static boolean cool(String key, long ms) {
        long now = System.currentTimeMillis();
        Long prev = LAST.get(key);
        if (prev != null && now - prev < ms) return false;
        LAST.put(key, now);
        return true;
    }

    // ================= chat =================

    /**
     * Every game chat line that isn't from a player. Returns true for the lines nothing else should look at (pest messages);
     * all the others carry on to the rest of the mod, which may need them too (rare drops count as profit, burrows as burrows...).
     */
    static boolean onChat(String plain) {
        Config c = Config.get();

        if (plain.startsWith(WORM_MESSAGE)) {
            Debug.saw("worm spawn");
            if (c.scathaAlert) wormAt = System.currentTimeMillis();
            return false;
        }

        String sea = SEA_RARE.get(plain);
        if (sea != null) {
            Debug.saw("rare sea creature");
            if (c.seaCreatureAlert) Alerts.notify("sea-" + sea, "§b§l≈ " + sea + "!", "§7rare sea creature", null);
            return false;
        }

        Matcher m = DROP.matcher(plain);
        if (m.matches()) {
            drop(m.group(1), m.group(2));
            return false;
        }

        m = STASH.matcher(plain);
        if (m.matches()) {
            Debug.saw("stash");
            if (c.stashAlert && cool("stash", 60_000)) {
                String what = m.group(2).startsWith("material") ? "materials" : "items";
                Alerts.notify("stash", "§e§lYour stash has " + what, "§f" + m.group(1) + " " + what + " §7waiting: /pickupstash", null);
            }
            return false;
        }

        if ((m = PEST_ONE.matcher(plain)).matches()) { pests("pest", 1, plot(m.group(1))); return true; }
        if ((m = PEST_MANY.matcher(plain)).matches()) { pests("pest", Integer.parseInt(m.group(1)), plot(m.group(2))); return true; }
        if ((m = PEST_AWAY.matcher(plain)).matches()) { pests("pest-away", -1, "Plots " + m.group(1)); return true; }

        if ((m = VISITOR.matcher(plain)).matches()) {
            Debug.saw("visitor arrived");
            if (c.visitorAlert) Alerts.notify("visitor", "§a§lVisitor arrived", "§f" + m.group(1), null);
            return false;
        }

        if ((m = COMMISSION.matcher(plain)).matches()) {
            Debug.saw("commission done");
            if (c.commissionAlert) Alerts.notify("commission", "§a§l✔ Commission done", "§f" + m.group(1) + " §7· visit the " + m.group(2), null);
            return false;
        }

        if (plain.equals("A Golden Goblin has spawned!") || plain.equals("A Diamond Goblin has spawned!")) {
            Debug.saw("goblin spawn");
            boolean gold = plain.contains("Golden");
            if (c.goblinAlert) Alerts.notify("goblin", (gold ? "§6§l★ Golden Goblin!" : "§b§l★ Diamond Goblin!"), "§7just spawned in the mines", null);
            return false;
        }

        if (plain.contains("You dug out") && plain.contains("Minos Inquisitor")) {
            Debug.saw("inquisitor");
            if (c.inquisitorAlert) Alerts.notify("inquisitor", "§c§l★ MINOS INQUISITOR!", "§7you dug one up", null);
            return false;
        }
        return false;
    }

    private static String plot(String raw) {
        String p = raw.trim();
        return p.startsWith("The ") ? p : "Plot " + p;
    }

    private static void pests(String key, int amount, String where) {
        Debug.saw("pest spawn");
        if (!Config.get().pestAlert) return;
        if (amount < 0) Alerts.notify(key, "§c§lPests spawned while you were away", "§7" + where, null);
        else Alerts.notify(key, amount == 1 ? "§c§lPest spawned!" : "§c§l" + amount + " pests spawned!", "§7" + where, null);
    }

    /** A drop message: "PET DROP! Scatha (+210)", "VERY RARE DROP! ..." and so on. */
    private static void drop(String tier, String rest) {
        Config c = Config.get();
        if (!c.rareDropAlert) return;
        int rank = tier.startsWith("PET") ? 6 : tier.startsWith("INSANE") ? 5 : tier.startsWith("CRAZY") ? 4
                : tier.startsWith("VERY RARE") ? 3 : tier.startsWith("RARE") ? 2 : 1;
        int need = "pets only".equals(c.rareDropMin) ? 6 : "all rare drops".equals(c.rareDropMin) ? 2 : 3;
        if (rank < need) return;
        String item = cleanDrop(rest);
        String col = switch (rank) { case 6 -> "§6"; case 5 -> "§c"; case 4 -> "§d"; case 3 -> "§5"; default -> "§9"; };
        Debug.saw("drop alert");
        Alerts.notify("drop-" + item, col + "§l★ " + tier + "!", "§f" + item, null);
    }

    /** The item without its trailing "(+78)" / "(+210% ✯ Magic Find)" note. */
    private static String cleanDrop(String raw) {
        String s = raw.replaceAll("^[^A-Za-z0-9\\[]+", "").trim();
        s = s.replaceAll("\\s*\\((?:\\+|[^)]*Magic Find)[^)]*\\)\\s*$", "").trim();
        return s.isEmpty() ? raw.trim() : s;
    }

    // ================= Scatha / worms =================

    /** When the "something approaching" message arrived and nothing has been announced yet (0 = nothing pending). */
    private static long wormAt;

    /**
     * Hypixel's message doesn't say whether it is a worm or Scatha. For a few seconds we look for the mob's name tag nearby:
     * once found the alert names it and shows which way it is and how far. In "Scatha only" mode a plain worm gets no alert.
     */
    private static void checkWorm(Minecraft mc) {
        Config c = Config.get();
        if (!c.scathaAlert) { wormAt = 0; return; }
        long now = System.currentTimeMillis();
        boolean scathaOnly = "Scatha only".equals(c.scathaMode);
        Entity best = null;
        boolean scatha = false;
        double bestD = 48;
        if (mc.player != null && mc.level != null && Reflect.call(mc.level, new String[]{"entitiesForRendering", "getEntities"}) instanceof Iterable<?> all) {
            for (Object o : all) {
                if (!(o instanceof Entity e) || e == mc.player || !e.hasCustomName() || e.getCustomName() == null) continue;
                String tag = Tracker.strip(e.getCustomName().getString());
                boolean s = tag.contains("Scatha");
                if (!s && !tag.contains("Worm")) continue;
                double d = e.distanceTo(mc.player);
                if (d > 48) continue;
                if (best == null || (s && !scatha) || (s == scatha && d < bestD)) { best = e; scatha = s; bestD = d; }
            }
        }
        if (best != null) {
            wormAt = 0;
            String where = "§7" + DianaBurrows.arrow(mc, best.getX(), best.getZ()) + " " + Math.round(bestD) + " blocks away";
            if (scatha) {
                Debug.saw("scatha seen");
                Alerts.notify("scatha", "§c§l★ SCATHA!", where, "§6[SkyAssist] §c§lScatha §7spawned: " + where.substring(2));
            } else if (!scathaOnly) {
                Debug.saw("worm seen");
                Alerts.notify("scatha", "§6§lWorm", where, null);
            }
            return;
        }
        if (now - wormAt > 4000) {                                       // no name tag showed up: say what we know
            wormAt = 0;
            if (!scathaOnly) Alerts.notify("scatha", "§e§lWorm or Scatha!", "§7something spawned nearby", null);
        }
    }

    // ================= your slayer boss =================

    private static long bossLast;

    /** Called whenever your boss's name tag is read; the first sighting after a quiet spell is its spawn. */
    static void onSlayerBoss() {
        long now = System.currentTimeMillis();
        boolean fresh = now - bossLast > 30_000;
        bossLast = now;
        if (!fresh || !Config.get().slayerSpawnAlert) return;
        Debug.saw("slayer boss spawn");
        Alerts.notify("slayer-boss", "§c§l☠ Your boss spawned!", "§7kill it before the timer runs out", null);
    }

    // ================= sidebar: cold, server restart =================

    private static boolean coldSent;
    private static long coldSeen;
    private static int restartStage;
    private static long restartSeen;

    private static void cold(Config c) {
        int cold = -1;
        for (String l : Tracker.sidebarLines) {
            Matcher m = COLD.matcher(l);
            if (m.find()) { cold = Integer.parseInt(m.group(1)); break; }
        }
        long now = System.currentTimeMillis();
        if (cold >= 0) coldSeen = now;
        if (!c.coldAlert) { coldSent = false; return; }
        if (cold < 0) { if (now - coldSeen > 10_000) coldSent = false; return; }
        if (cold >= c.coldAt && !coldSent) {
            coldSent = true;
            Debug.saw("cold alert");
            Alerts.notify("cold", "§b§l❄ Cold " + cold + "!", "§7warm up before it reaches 100", null);
        } else if (cold < c.coldAt - 5) {
            coldSent = false;                                              // warmed up: ready to warn again
        }
    }

    private static void restart(Config c) {
        int secs = -1;
        for (String l : Tracker.sidebarLines) {
            Matcher m = RESTART.matcher(l);
            if (m.find()) { secs = Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)); break; }
        }
        long now = System.currentTimeMillis();
        if (secs < 0) { if (now - restartSeen > 30_000) restartStage = 0; return; }
        restartSeen = now;
        if (!c.restartAlert || !Alerts.inSkyblock()) return;
        int stage = secs <= 15 ? 3 : secs <= 60 ? 2 : 1;                   // first sight, one minute left, last seconds
        if (stage <= restartStage) return;
        restartStage = stage;
        Debug.saw("server restart");
        Alerts.notify("restart", "§c§l⚠ Server restart in " + secs / 60 + ":" + String.format("%02d", secs % 60), "§7finish what you're doing", null);
    }

    // ================= events starting =================

    private static long darkHour = -1, cultStart = -1;

    private static void events(Config c) {
        if (!c.eventAlert || !Alerts.inSkyblock()) return;
        long nowS = System.currentTimeMillis() / 1000;
        long lead = Math.max(0, c.eventAlertMinutes) * 60L;
        if (c.timerDarkAuction) {
            long hour = nowS / 3600, sec = nowS % 3600;                    // the Dark Auction opens at :55 of every hour for 3 minutes
            if (sec >= 3300 - lead && sec < 3300 + 150 && hour != darkHour) {
                darkHour = hour;
                Debug.saw("event alert");
                if (sec < 3300) Alerts.notify("event-da", "§5§lDark Auction soon", "§7opens in " + Fmt.clock((3300 - sec) * 1000), null);
                else Alerts.notify("event-da", "§5§lDark Auction is open!", "§7it closes in " + Fmt.clock((3480 - sec) * 1000), null);
            }
        }
        if (c.timerCult) {
            long[] w = Timers.cultWindow();
            if (w != null && nowS >= w[0] - lead && nowS < w[1] && w[0] != cultStart) {
                cultStart = w[0];
                Debug.saw("event alert");
                if (nowS < w[0]) Alerts.notify("event-cult", "§d§lCult of the Fallen Star soon", "§7starts in " + Fmt.clock((w[0] - nowS) * 1000), null);
                else Alerts.notify("event-cult", "§d§lCult of the Fallen Star is on!", "§7ends in " + Fmt.clock((w[1] - nowS) * 1000), null);
            }
        }
    }

    // ================= every tick =================

    private static int ticks;

    static void tick(Minecraft mc) {
        ticks++;
        if (wormAt != 0 && ticks % 3 == 0) checkWorm(mc);
        if (ticks % 10 != 0) return;
        Config c = Config.get();
        cold(c);
        restart(c);
        if (ticks % 40 == 0) events(c);
    }

    private EventAlerts() {}
}
