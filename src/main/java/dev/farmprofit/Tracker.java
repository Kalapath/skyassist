package dev.farmprofit;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** All the live tracking logic. */
public final class Tracker {
    public static final String FARMING = "Farming";
    public static final String MINING = "Mining";
    public static final String FORAGING = "Foraging";
    public static final String FISHING = "Fishing";
    public static final String COMBAT = "Combat";
    public static final String DUNGEONS = "Dungeons";
    public static final String KUUDRA = "Kuudra";
    public static final String DIANA = "Diana";
    // Old per-island mining names (only used to read old history)
    public static final String DWARVEN = "Dwarven Mines";
    public static final String HOLLOWS = "Crystal Hollows";
    public static final String MINESHAFT = "Glacite Mineshaft";

    private static final Pattern SACK_LINE = Pattern.compile("^\\s*([+-][\\d,]+) (.+?) \\(.*\\)\\s*$");
    private static final Pattern TAB_STAT = Pattern.compile("^\\s*([^:]{2,40}?):\\s*(.+?)\\s*$");
    private static final Pattern COMMISSION = Pattern.compile("^\\s*(.+?):\\s*(\\d+(?:\\.\\d+)?%|DONE)\\s*$");
    private static final Pattern NUMBER = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern PEST_KILL = Pattern.compile("^You received (\\d+)x (.+?) for killing an? (.+?)!$");
    private static final Pattern PRISTINE = Pattern.compile("^PRISTINE! You found .*?x(\\d+)!$");
    private static final Pattern RARE_DROP = Pattern.compile("^((?:[A-Z]+ )*(?:DROP|CROP))! (.+)$");
    private static final Pattern CATCH = Pattern.compile("^(?:GOOD|GREAT|OUTSTANDING) CATCH! You (?:found|caught) (?:an? )?(.+?)[.!]?$");
    private static final Pattern COINS = Pattern.compile("^([\\d,]+) Coins$");
    private static final Pattern SCORE = Pattern.compile("^Team Score: (\\d+) \\((.+?)\\)");
    private static final Pattern FLOOR = Pattern.compile("The Catacombs \\((\\w+)\\)");
    private static long lastRunEnd;
    /** e.g. "F7" or "M5", from the sidebar */
    public static String dungeonFloor;
    private static final Pattern TROPHY = Pattern.compile("^TROPHY FISH! You caught an? (.+?) (BRONZE|SILVER|GOLD|DIAMOND)", Pattern.CASE_INSENSITIVE);
    private static final Pattern KUUDRA_DOWN = Pattern.compile("^KUUDRA DOWN!.*");
    private static final Pattern DUG = Pattern.compile("(?i)^(?:.*! )?You dug out (?:an? )?(.+?)[.!]?$");
    private static final Pattern DUG_COINS = Pattern.compile("(?i)You dug out ([\\d,]+) coins");
    private static final Pattern SLAYER_START = Pattern.compile("^SLAYER QUEST STARTED!.*", Pattern.DOTALL);
    private static final Pattern SLAYER_DONE = Pattern.compile("^(?:NICE! SLAYER BOSS SLAIN!|SLAYER QUEST COMPLETE!).*", Pattern.DOTALL);
    private static final Pattern SLAYER_TYPE = Pattern.compile("Slay [\\d,]+ Combat XP worth of (\\w+)");
    private static final Pattern AMOUNT_SUFFIX = Pattern.compile("^(.+?) x(\\d+)$");
    private static final Pattern AMOUNT_PREFIX = Pattern.compile("^(\\d+)x (.+)$");
    private static final Pattern TREE_GIFT = Pattern.compile("(?i)^\\W*tree gift\\b.*", Pattern.DOTALL);
    private static final Pattern SHARD = Pattern.compile("(?:(\\d+)x\\s+)?([A-Z][A-Za-z'\\-]*(?: [A-Z][A-Za-z'\\-]*)*) Shards?\\b(?:\\s*x(\\d+))?");
    private static final Pattern PLAYER_CHAT = Pattern.compile("^(?:[A-Za-z]+ > )?(?:\\[[^\\]]*\\]\\s*)*[A-Za-z0-9_]{3,16}(?:\\s*\\[[^\\]]*\\])?: .*", Pattern.DOTALL);
    private static final String[] SHARD_IGNORE = {"fus", "syphon", "sold", "bought", "bazaar", "sell", "buy", "convert", "transfer"};
    private static final Set<String> SHARD_STOPWORDS = Set.of("You", "Caught", "Gained", "Obtained", "Found", "Received",
            "Got", "A", "An", "The", "Your", "Added", "Sent", "Rare", "Drop", "Tree", "Gift", "Hunting", "Box");
    private static final Map<String, String> SLAYERS = Map.of(
            "Zombies", "Revenant Horror", "Spiders", "Tarantula Broodfather", "Wolves", "Sven Packmaster",
            "Endermen", "Voidgloom Seraph", "Blazes", "Inferno Demonlord", "Vampires", "Riftstalker Bloodfiend");
    private static final String[] POWDERS = {"Mithril", "Gemstone", "Glacite", "Forest Whispers", "Desert Whispers"};
    private static final long INVENTORY_WINDOW_MS = 10_000;
    private static final long SHOWN_WINDOW_MS = 60_000;

    /** One open session per activity. */
    public static final Map<String, Session> sessions = new LinkedHashMap<>();
    public static final Map<String, String> tab = new HashMap<>();
    public static final List<String> commissions = new ArrayList<>();
    /** Every tab-list line as plain text (in order). */
    public static volatile List<String> tabLines = new ArrayList<>();
    /** Every sidebar (scoreboard) line as plain text. */
    public static volatile List<String> sidebarLines = new ArrayList<>();
    /** MINING, FORAGING, FARMING (Garden) or null, based on the tab-list Area. */
    public static String area;
    /** Raw "Area:" name from the tab list. */
    public static String areaName;

    private static Map<String, Integer> lastInventory = new HashMap<>();
    private static boolean haveSnapshot;
    private static Object lastPlayer;
    private static long lastTabCheck;
    private static final Map<String, Long> lastPowder = new HashMap<>();
    private static String lastShardKey = "";
    private static long lastShardTime;
    private static long lastBossTime;
    private static String slayerType = "Slayer";

    private static final List<Target> pending = new ArrayList<>();

    /** Item icons seen in your inventory (ItemStack copies), by name. */
    private static final Map<String, Object> ICONS = new HashMap<>();

    public static Object icon(String name) { return name == null ? null : ICONS.get(name); }

    // ---- purse (from the sidebar) for automatic slayer cost detection ----
    public static long purse = -1;
    private static final Pattern PURSE = Pattern.compile("(?:Purse|Piggy): ([\\d,]+)");
    private static final java.util.ArrayDeque<long[]> PURSE_LOG = new java.util.ArrayDeque<>();
    private static long lastScoreboardCheck;
    private static Session costSession;
    private static long costBefore = -1, costMin = Long.MAX_VALUE, costUntil;

    private static final class Target {
        final BlockPos pos; final BlockState state; final String name; int age;
        Target(BlockPos pos, BlockState state, String name) { this.pos = pos; this.state = state; this.name = name; }
    }

    /** All-time totals use one name per activity (old per-island mining names count as Mining). */
    public static String normalType(String t) {
        if (t == null) return FARMING;
        return isMiningType(t) ? MINING : t;
    }

    public static boolean isMiningType(String t) {
        return MINING.equals(t) || DWARVEN.equals(t) || HOLLOWS.equals(t) || MINESHAFT.equals(t);
    }

    // ---------- sessions ----------

    /** The HUD you're looking at: switches to whatever you just did, or to the island you just arrived on. */
    private static String showing;
    private static String lastArea = "";

    private static Session activity(String type) {
        showing = type;
        long now = System.currentTimeMillis();
        Session s = sessions.computeIfAbsent(type, t -> new Session(t, now));
        s.touch(now);
        return s;
    }

    private static boolean recent(String type, long ms) {
        Session s = sessions.get(type);
        return s != null && System.currentTimeMillis() - s.lastActivity < ms;
    }

    public static void onCropBroken(String crop) { activity(FARMING).addBreak(crop); }

    public static void onRodUse() {
        Session s = activity(FISHING);
        if (s.location == null && areaName != null) s.location = areaName;
    }

    public static void onVacuum() { activity(FARMING).pestActions++; }

    public static Session farmingSession() { return activity(FARMING); }

    public static Session miningSession() { return activity(MINING); }

    /** Things you can hit that aren't fights: NPCs, players, decorations. */
    private static final Set<String> NOT_MOBS = Set.of("player", "armor_stand", "villager", "wandering_trader",
            "item_frame", "glow_item_frame", "painting", "interaction", "text_display", "item_display", "block_display",
            "boat", "minecart", "chest_boat", "end_crystal", "leash_knot", "mannequin");
    private static final java.util.ArrayDeque<Long> combatHits = new java.util.ArrayDeque<>();

    /** A kill of a mob you hit (see Combat). target = the session the hit went to. */
    public static void onKill(String mob, String target) {
        if (!COMBAT.equals(target)) return;
        Session s = sessions.get(COMBAT);
        if (s == null) return;
        s.kills.merge(mob, 1, Integer::sum);
        Debug.saw("kill");
        updateGrind(s);
    }

    /** Becomes a grind once most kills are one farmable mob. */
    private static void updateGrind(Session s) {
        Config c = Config.get();
        if (!c.grindHuds) { s.grind = null; return; }
        Map<String, Integer> byGrind = new HashMap<>();
        for (var e : s.kills.entrySet()) {
            Combat.Grind g = Combat.grindFor(e.getKey());
            if (g != null) byGrind.merge(g.name(), e.getValue(), Integer::sum);
        }
        int total = s.totalKills();
        String best = null;
        int bestN = 0;
        for (var e : byGrind.entrySet()) if (e.getValue() > bestN) { bestN = e.getValue(); best = e.getKey(); }
        s.grind = total >= 5 && best != null && bestN * 100 >= total * c.grindShare ? best : null;
    }

    /** Called with the real entity so kills and mob names can be tracked. */
    public static void onAttackEntity(Entity mob) {
        String type = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
        if (NOT_MOBS.contains(type)) return;
        String name = Combat.nameOf(mob);
        String target = onAttack(type, mob.isInvisible(), Combat.grindFor(name) != null);
        if (target != null) Combat.hit(mob, name, target);
    }

    /** Returns which session the hit went to (or null). */
    public static String onAttack(String entityType, boolean invisible, boolean grindMob) {
        if (NOT_MOBS.contains(entityType)) return null;
        Config c = Config.get();
        String r = routeAttack(entityType, grindMob, c);
        return r;
    }

    private static String routeAttack(String entityType, boolean grindMob, Config c) {
        if (recent(FISHING, c.fishingActiveSeconds * 1000L)) { activity(FISHING); return FISHING; }   // sea creatures
        if (recent(DIANA, SHOWN_WINDOW_MS)) { activity(DIANA); return DIANA; }                         // mythological mobs
        if (KUUDRA.equals(area)) { activity(KUUDRA); return KUUDRA; }
        if (DUNGEONS.equals(area)) { onDungeonAction(); return DUNGEONS; }
        // grind mobs (Ghosts in the Mines, Zealots...) always go to Combat, wherever you are
        if (grindMob && (sessions.containsKey(COMBAT) || !c.combatNeedsSlayer)) { activity(COMBAT); return COMBAT; }
        if (MINING.equals(area) || FORAGING.equals(area)) {
            if (sessions.containsKey(area)) { activity(area); return area; }
            return null;
        }
        if (FARMING.equals(area)) {
            if (sessions.containsKey(FARMING)) { activity(FARMING).pestActions++; return FARMING; }
            return null;
        }
        // Combat: keep a running session going, but only start a new one after real fighting
        if (sessions.containsKey(COMBAT)) { activity(COMBAT); return COMBAT; }
        if (c.combatNeedsSlayer) return null;
        long now = System.currentTimeMillis();
        combatHits.addLast(now);
        while (!combatHits.isEmpty() && now - combatHits.peekFirst() > 30_000) combatHits.removeFirst();
        if (combatHits.size() >= Math.max(1, c.combatStartHits)) { combatHits.clear(); activity(COMBAT); return COMBAT; }
        return null;
    }

    public static void onDungeonAction() {
        Session s = activity(DUNGEONS);
        if (s.floor == null && dungeonFloor != null) s.floor = dungeonFloor;
    }

    /** Which HUD to show: what you did last; arriving somewhere new switches to that island's HUD. */
    public static String shownType() {
        if (showing != null && (sessions.containsKey(showing) || showing.equals(area))) return showing;
        if (area != null) return area;
        Session recent = mostRecent();
        return recent != null ? recent.type : null;
    }

    /** Called after the location is read: a new island switches the HUD right away. */
    private static void onAreaChecked() {
        String now = area == null ? "" : area;
        if (!now.equals(lastArea)) {
            lastArea = now;
            if (area != null && Config.get().switchHudOnArrival) showing = area;
        }
    }

    public static Session shown() {
        String t = shownType();
        return t == null ? null : sessions.get(t);
    }

    private static Session mostRecent() {
        Session best = null;
        for (Session s : sessions.values()) if (best == null || s.lastActivity > best.lastActivity) best = s;
        return best;
    }

    public static long idleMs(Session s) { return s == null ? 0 : System.currentTimeMillis() - s.lastActivity; }

    public static long resetMs() { return Config.get().resetMinutes * 60_000L; }

    public static void endSession(Session s, boolean announce) {
        if (s == null) return;
        sessions.remove(s.type);
        if (s.isEmpty()) return;
        s.fortune = statsSummary(s.type, s.mainCrop());
        s.finish();
        Totals.add(s);
        History.add(s);
        if (announce && Config.get().announceSessionEnd) {
            say("§6§l[" + s.type + "] §7Session saved: §f" + Fmt.duration(s.durationMs(0)) + " §7- §a" + s.mainCrop);
            say("  §7Profit §6" + Fmt.coins(s.profit) + " §7(§6" + Fmt.coins(s.profitPerHour) + "/h§7)"
                    + (Hud.bestItem(s) != null ? "  §7Best: " + Hud.bestItem(s) : ""));
        }
    }

    public static void endAll(boolean announce) {
        for (Session s : new ArrayList<>(sessions.values())) endSession(s, announce);
    }

    // ---------- every tick ----------

    public static void tick(Minecraft mc) {
        SettingsScreen.tick(mc);
        TalismansScreen.tick(mc);
        MenuScreen.tick(mc);
        GuiEditor.tick(mc);
        SetupScreen.tick(mc);
        UpdateCheck.tick();
        Prices.tick();
        if (Prices.bazaarUpdated) {
            Prices.bazaarUpdated = false;
            Bazaar.onPrices();
        }
        long now = System.currentTimeMillis();
        for (Session s : new ArrayList<>(sessions.values())) {
            if (idleMs(s) > resetMs()) endSession(s, true);
        }

        if (mc.player == null || mc.level == null) {
            haveSnapshot = false; lastPlayer = null; pending.clear(); return;
        }
        if (mc.player != lastPlayer) { haveSnapshot = false; lastPlayer = mc.player; pending.clear(); }

        if (now - lastTabCheck > 1000) {
            lastTabCheck = now;
            readTab(mc);
        }

        Perf.run("Mining/foraging blocks", () -> trackBlocks(mc));
        Combat.tick(mc);
        Perf.run("Dungeon secret finder", () -> Secrets.tick(mc));
        Perf.run("Jacob's contests", () -> Contests.tick());
        Perf.run("Mayor", () -> Election.tick());
        Perf.run("Enchant data", () -> Enchants.tick());
        Perf.run("Recipe data", () -> CraftCost.tick());
        Perf.run("Dungeon puzzles", () -> WorldPuzzles.tick(mc));
        Perf.run("Pests", () -> Pests.tick(mc));
        Perf.run("Glow outlines", () -> Glow.tick(mc));
        Perf.run("Greenhouse guide", () -> Greenhouse.tick(mc));
        Perf.run("Treasure chests", () -> PowderChests.tick(mc));
        Perf.run("Lockpick helper", () -> Lockpick.tick(mc));
        Perf.run("Diana burrows", () -> DianaBurrows.tick(mc));
        Perf.run("Fishing bite alert", () -> FishingAlert.tick(mc));
        Perf.run("Fishing extras", () -> FishingExtras.tick(mc));
        Perf.run("Waypoints", () -> Waypoints.tick(mc));
        Perf.run("Greenhouse unlocks", () -> Greenhouse.noticeInventory(mc));
        Perf.run("Quiz data", () -> WorldPuzzles.quizTick());
        Calc.tick(mc);

        if (now - lastScoreboardCheck > 500) {
            lastScoreboardCheck = now;
            readPurse(mc);
            finishCostCheck(now);
        }

        if (Compat.screen(mc) != null && !(Compat.screen(mc) instanceof net.minecraft.client.gui.screens.ChatScreen)) lastMenuTime = System.currentTimeMillis();
        Map<String, Integer> inv = scanInventory(mc);   // (measured as part of "Inventory tracking")
        Session target = mostRecent();
        boolean inMenu = Compat.screen(mc) != null;
        boolean menuOk = Menus.countsIn(mc);
        if (inMenu && Menus.isVisitorMenu()) target = sessions.get(FARMING);
        boolean counting = haveSnapshot && target != null && menuOk
                && (inMenu || idleMs(target) < INVENTORY_WINDOW_MS);
        if (counting) {
            Set<String> keys = new HashSet<>(inv.keySet());
            keys.addAll(lastInventory.keySet());
            Map<String, Integer> deltas = new HashMap<>();
            boolean anyGain = false;
            for (String k : keys) {
                int delta = inv.getOrDefault(k, 0) - lastInventory.getOrDefault(k, 0);
                if (delta != 0) deltas.put(k, delta);
                if (delta > 0) anyGain = true;
            }
            // Something gained in the same tick (compactor, crafting): treat as a conversion, keep it net.
            // Only losses: you used it up (potion, arrows, a visitor's request) -> "Spent".
            for (var d : deltas.entrySet()) {
                if (d.getValue() > 0 && !anyLoss(deltas) && !Attribution.counts(target, d.getKey(), Config.get().attributionSeconds * 1000L)) continue;  // not from this activity
                if (anyGain || d.getValue() > 0) target.addItem(d.getKey(), d.getValue());
                // the crops / ores themselves (and their enchanted forms) leaving the inventory are moved, crafted
                // or put in sacks, not used up: keep them as a negative amount instead of "Spent"
                else if (Items.isTracked(d.getKey())) { target.addItem(d.getKey(), d.getValue()); Attribution.usedUpAt = System.currentTimeMillis(); }
                else target.addSpent(d.getKey(), -d.getValue());
            }
            if (anyGain && inMenu) Menus.onGainedInMenu(target);
        }
        lastInventory = inv;
        haveSnapshot = true;
    }

    /** Counts known items plus any stackable item that has a price (mob drops, fish, ...). */
    private static Map<String, Integer> scanInventory(Minecraft mc) {
        Map<String, Integer> out = new HashMap<>();
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            var stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String name = strip(stack.getHoverName().getString());
            ItemIds.learn(name, stack);
            if (Items.isTracked(name) || (stack.getMaxStackSize() > 1 && Prices.price(name) > 0)) {
                out.merge(name, stack.getCount(), Integer::sum);
                if (!ICONS.containsKey(name)) ICONS.put(name, stack.copy());
            }
        }
        return out;
    }

    // ---------- mining / foraging block detection ----------

    private static final java.util.ArrayDeque<Long> logBreaks = new java.util.ArrayDeque<>();

    /** A block you broke on your side (instant breaks, like logs with Sweep, never show up as a slow break). */
    public static void onClientBreak(BlockPos pos, BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (MINING.equals(area) || FORAGING.equals(area)) {
            String name = Items.blockFor(path, area);
            if (name == null) return;
            pending.removeIf(t -> t.pos.equals(pos));          // don't count it twice
            activity(area).addBreak(name);
            return;
        }
        String crop = Items.cropFor(path);
        if (crop != null) { onCropBroken(crop); return; }
        // logs anywhere else (hub forest, unknown islands): foraging after a few in a row
        String log = Items.blockFor(path, FORAGING);
        if (log == null) return;
        if (sessions.containsKey(FORAGING)) { activity(FORAGING).addBreak(log); return; }
        long now = System.currentTimeMillis();
        logBreaks.addLast(now);
        while (!logBreaks.isEmpty() && now - logBreaks.peekFirst() > 30_000) logBreaks.removeFirst();
        if (logBreaks.size() >= 5) { logBreaks.clear(); activity(FORAGING).addBreak(log); }
    }

    /** You started hitting a block: remember it in case the server breaks it a moment later. */
    public static void onStartBreak(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || (!MINING.equals(area) && !FORAGING.equals(area))) return;
        for (Target t : pending) if (t.pos.equals(pos)) return;
        BlockState state = mc.level.getBlockState(pos);
        String name = Items.blockFor(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), area);
        if (name != null && pending.size() < 32) pending.add(new Target(pos.immutable(), state, name));
    }

    private static void trackBlocks(Minecraft mc) {
        if (!MINING.equals(area) && !FORAGING.equals(area)) { pending.clear(); return; }

        for (Iterator<Target> it = pending.iterator(); it.hasNext(); ) {
            Target t = it.next();
            if (mc.level.getBlockState(t.pos) != t.state) {
                activity(area).addBreak(t.name);
                it.remove();
            } else if (++t.age > 40) {
                it.remove();
            }
        }

        if (Compat.screen(mc) != null || !mc.options.keyAttack.isDown()) return;
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        for (Target t : pending) {
            if (t.pos.equals(pos)) { t.age = 0; return; }
        }
        BlockState state = mc.level.getBlockState(pos);
        String name = Items.blockFor(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), area);
        if (name != null && pending.size() < 32) pending.add(new Target(pos.immutable(), state, name));
    }

    // ---------- sidebar purse ----------

    private static void readPurse(Minecraft mc) {
        if (mc.level == null) return;
        try {
            Object sb = Reflect.call(mc.level, "getScoreboard");
            Object sidebar = null;
            Class<?> slotCls = Class.forName("net.minecraft.world.scores.DisplaySlot");
            for (Object c : slotCls.getEnumConstants()) if (c.toString().equalsIgnoreCase("SIDEBAR")
                    || ((Enum<?>) c).name().equals("SIDEBAR")) sidebar = c;
            Object objective = Reflect.call(sb, "getDisplayObjective", sidebar);
            Object entries = Reflect.call(sb, new String[]{"listPlayerScores"}, objective);
            if (!(entries instanceof java.util.Collection<?> list)) return;
            List<String> side = new ArrayList<>();
            sidebarLines = side;
            for (Object e : list) {
                Object owner = Reflect.call(e, new String[]{"owner", "getOwner"});
                if (!(owner instanceof String name)) continue;
                Object team = Reflect.call(sb, "getPlayersTeam", name);
                String prefix = team == Reflect.FAIL || team == null ? "" : text(Reflect.call(team, "getPlayerPrefix"));
                String suffix = team == Reflect.FAIL || team == null ? "" : text(Reflect.call(team, "getPlayerSuffix"));
                String line = strip(prefix + name + suffix);
                side.add(line.trim());
                Matcher fl = FLOOR.matcher(line);
                if (fl.find()) dungeonFloor = fl.group(1);
                Matcher m = PURSE.matcher(line);
                if (m.find()) {
                    long newPurse = Long.parseLong(m.group(1).replace(",", ""));
                    onPurse(newPurse);
                    purse = newPurse;
                    long now = System.currentTimeMillis();
                    PURSE_LOG.addLast(new long[]{now, purse});
                    while (!PURSE_LOG.isEmpty() && now - PURSE_LOG.peekFirst()[0] > 10_000) PURSE_LOG.removeFirst();
                    if (costSession != null) costMin = Math.min(costMin, purse);
                }
            }
        } catch (Throwable ignored) {
            // sidebar not readable on this version: automatic slayer costs just stay off
        }
    }

    private static long lastPurseSeen = -1;
    private static long lastMenuTime;

    /** Purse went up while you're active and no menu was open recently: Bountiful, mob coins, coin catches... */
    private static void onPurse(long newPurse) {
        long before = lastPurseSeen;
        lastPurseSeen = newPurse;
        if (!Config.get().trackPurse || before < 0 || newPurse <= before) return;
        long gain = newPurse - before;
        long now = System.currentTimeMillis();
        if (now - lastMenuTime < 3000 || gain > 20_000_000) return;          // selling, bank, trades... or a big transfer
        Session s = mostRecent();
        if (s == null || now - s.lastActivity > 15_000) return;
        s.purseCoins += gain;
    }

    /** True when purse coins are being tracked (then coin catches / dug coins aren't added again). */
    public static boolean purseTracked() { return Config.get().trackPurse && purse >= 0; }

    private static String text(Object component) {
        return component instanceof Component c ? c.getString() : "";
    }

    private static void startCostCheck(Session s) {
        long now = System.currentTimeMillis();
        long before = -1;
        for (long[] p : PURSE_LOG) if (now - p[0] <= 4000) before = Math.max(before, p[1]);
        if (before < 0) return;
        costSession = s;
        costBefore = before;
        costMin = purse >= 0 ? purse : Long.MAX_VALUE;
        costUntil = now + 6000;
    }

    private static void finishCostCheck(long now) {
        if (costSession == null || now < costUntil) return;
        long cost = costBefore - costMin;
        if (costMin != Long.MAX_VALUE && cost > 0 && cost < 5_000_000) costSession.costs += cost;
        costSession = null;
    }

    // ---------- tab list ----------

    private static void readTab(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn == null) return;
        tab.clear();
        commissions.clear();
        List<String> lines = new ArrayList<>();
        for (var info : conn.getListedOnlinePlayers()) {
            Component dn = info.getTabListDisplayName();
            if (dn == null) continue;
            String line = strip(dn.getString());
            lines.add(line.trim());
            Matcher c = COMMISSION.matcher(line);
            if (c.matches()) commissions.add(c.group(1).trim() + ": " + c.group(2));
            Matcher m = TAB_STAT.matcher(line);
            if (m.matches()) tab.putIfAbsent(m.group(1).trim(), m.group(2).trim());
        }

        tabLines = lines;
        String a = tab.get("Area");
        areaName = a != null ? a : HypixelLocation.map;
        area = null;
        String fromApi = HypixelLocation.activity();
        String apiMap = HypixelLocation.map;
        if (apiMap != null && areaFor(apiMap) != null) fromApi = areaFor(apiMap);
        if (fromApi != null) {
            area = fromApi.isEmpty() ? null : fromApi;
            if (!fromApi.isEmpty() || a == null) { onAreaChecked(); readPowder(); return; }
        }
        if (a != null) {
            area = areaFor(a);
        }
        if (tab.containsKey("Crypts") || tab.containsKey("Secrets Found")) area = DUNGEONS;
        onAreaChecked();
        readPowder();
    }

    private static void readPowder() {
        for (String p : POWDERS) {
            String v = tab.containsKey(p + " Powder") ? tab.get(p + " Powder") : tab.get(p);
            long val = number(v);
            if (val < 0) continue;
            Long last = lastPowder.put(p, val);
            String owner = p.endsWith("Whispers") ? FORAGING : MINING;
            Session s = owner.equals(area) ? sessions.get(owner) : null;
            if (s != null && last != null && val > last) s.addPowder(p, val - last);
        }
    }

    /** Islands / areas that have their own HUD. Your own extra names from the settings are added on top. */
    private static final String[] MINING_PLACES = {"Dwarven Mines", "Crystal Hollows", "Mineshaft", "Glacite", "Deep Caverns", "Gold Mine"};
    private static final String[] FORAGING_PLACES = {"The Park", "Galatea", "Moonglade", "Torrhus"};

    /** Which HUD an area name belongs to (tab-list "Area:" or the Mod API map name), or null. */
    public static String areaFor(String name) {
        if (name == null) return null;
        if (name.contains("Dungeon Hub")) return null;                         // the hub isn't a dungeon
        if (name.contains("Catacombs") || name.contains("Dungeon")) return DUNGEONS;
        if (name.contains("Kuudra")) return KUUDRA;
        if (name.contains("Garden")) return FARMING;
        for (String f : FORAGING_PLACES) if (name.contains(f)) return FORAGING;
        for (String f : MINING_PLACES) if (name.contains(f)) return MINING;
        for (String f : Config.get().foragingAreas) if (!f.isBlank() && name.contains(f)) return FORAGING;
        for (String f : Config.get().miningAreas) if (!f.isBlank() && name.contains(f)) return MINING;
        return null;
    }

    /** A loss in the same tick means a conversion (compactor / crafting): keep it net, don't filter it. */
    private static boolean anyLoss(java.util.Map<String, Integer> deltas) {
        for (int v : deltas.values()) if (v < 0) return true;
        return false;
    }

    public static long number(String s) {
        if (s == null) return -1;
        Matcher m = NUMBER.matcher(s);
        if (!m.find()) return -1;
        try { return (long) Double.parseDouble(m.group().replace(",", "")); } catch (Exception e) { return -1; }
    }

    public static String[] statKeys(String type) {
        if (isMiningType(type)) return new String[]{"Mining Speed", "Mining Fortune", "Gemstone Fortune", "Ore Fortune", "Block Fortune"};
        return switch (type) {
            case FORAGING -> new String[]{"Sweep", "Foraging Fortune", "Fig Fortune", "Mangrove Fortune", "Helix Fortune"};
            case FISHING -> new String[]{"Fishing Speed", "Sea Creature Chance", "Double Hook Chance", "Treasure Chance"};
            case COMBAT, DUNGEONS, KUUDRA, DIANA -> new String[]{"Magic Find"};
            default -> new String[]{"Farming Fortune"};
        };
    }

    public static String statsSummary(String type, String main) {
        List<String> parts = new ArrayList<>();
        for (String k : statKeys(type)) if (tab.containsKey(k)) parts.add(k + " " + tab.get(k));
        if (FARMING.equals(type) && main != null && tab.containsKey(main + " Fortune"))
            parts.add(main + " Fortune " + tab.get(main + " Fortune"));
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    // ---------- chat ----------

    public static void onChat(Component message) {
        String plain = strip(message.getString()).trim();
        if (PLAYER_CHAT.matcher(plain).matches() || plain.startsWith("Party >") || plain.startsWith("Guild >") || plain.startsWith("Co-op >")) {
            Waypoints.onPlayerChat(plain);
            return;
        }
        Mineshafts.onChat(plain);
        if (Bazaar.handle(plain)) return;
        if (Puzzles.onChat(plain)) return;
        if (PowderChests.onChat(plain)) return;
        if (WorldPuzzles.onChat(plain)) return;

        scanShards(plain);

        Matcher pk = PEST_KILL.matcher(plain);
        if (pk.matches()) {
            Session s = activity(FARMING);
            Debug.saw("pest kill");
            s.pests.merge(pk.group(3).trim(), 1, Integer::sum);
            s.pestActions++;
            return;
        }

        if (TREE_GIFT.matcher(plain).matches()) {
            Debug.saw("tree gift");
            if (sessions.containsKey(FORAGING) || FORAGING.equals(area)) activity(FORAGING).treeGifts++;
            return;
        }

        if (PRISTINE.matcher(plain).matches()) {
            Session s = sessions.get(MINING);
            Debug.saw("pristine");
            if (s != null) s.pristine++;
            return;
        }

        // ----- slayers -----
        Matcher st = SLAYER_TYPE.matcher(plain);
        if (st.find()) slayerType = SLAYERS.getOrDefault(st.group(1), "Slayer");
        if (SLAYER_START.matcher(plain).matches()) {
            Session s = activity(COMBAT);
            Debug.saw("slayer start");
            s.slayerQuests++;
            if (Config.get().slayerQuestCost > 0) {
                s.costs += Config.get().slayerQuestCost;          // fixed cost from the config
            } else {
                startCostCheck(s);                                // measure it from your purse
            }
            return;
        }
        if (SLAYER_DONE.matcher(plain).matches()) {
            long now = System.currentTimeMillis();
            if (now - lastBossTime > 5000) activity(COMBAT).addBreak(slayerType);
            lastBossTime = now;
            return;
        }

        // ----- grind specials (e.g. "A special Zealot has spawned nearby!") -----
        for (Combat.Grind g : Combat.GRINDS) {
            if (g.specialMessage() != null && plain.contains(g.specialMessage())) {
                Session s = sessions.get(COMBAT);
                if (s != null) s.specials++;
                Debug.saw("special " + g.name());
                return;
            }
        }

        // ----- dungeons: run finished -----
        Matcher sc = SCORE.matcher(plain);
        if (sc.find()) {
            Session s = activity(DUNGEONS);
            Debug.saw("dungeon score");
            s.runs++;
            s.lastScore = sc.group(1) + " (" + sc.group(2) + ")";
            Dungeon.recordRun(sc.group(1), sc.group(2));
            if (dungeonFloor != null) s.floor = dungeonFloor;
            lastRunEnd = System.currentTimeMillis();
            return;
        }

        // ----- fishing -----
        Matcher tr = TROPHY.matcher(plain);
        if (tr.find()) {
            if (sessions.containsKey(FISHING)) {
                Session s = activity(FISHING);
                s.trophyFish++;
                s.trophies.merge(tr.group(1).trim() + " (" + tr.group(2).toUpperCase() + ")", 1, Integer::sum);
            }
            Debug.saw("trophy fish");
            return;
        }

        // ----- Kuudra -----
        if (KUUDRA_DOWN.matcher(plain).matches()) {
            Session s = activity(KUUDRA);
            s.runs++;
            Debug.saw("kuudra run");
            return;
        }

        // ----- Diana -----
        Matcher dug = DUG.matcher(plain);
        if (dug.find()) {
            Session s = activity(DIANA);
            s.burrows++;
            DianaBurrows.onDug();
            if (dug.group(1).contains("Minos Inquisitor")) Waypoints.offerShare("Minos Inquisitor");
            Matcher dc = DUG_COINS.matcher(plain);
            if (dc.find() && !purseTracked()) s.coins += Long.parseLong(dc.group(1).replace(",", ""));
            Debug.saw("burrow");
            if (!plain.contains("DROP!")) return;
        }

        // ----- Garden visitors -----
        if (Visitors.onChat(plain)) return;
        Matcher fc = CATCH.matcher(plain);
        if (fc.matches()) {
            Session s = sessions.get(FISHING);
            Debug.saw("catch");
            if (s == null) return;
            Matcher coins = COINS.matcher(fc.group(1).trim());
            if (coins.matches()) { if (Config.get().fishCountCoins && !purseTracked()) s.coins += Long.parseLong(coins.group(1).replace(",", "")); }
            else addRare(s, fc.group(1));
            return;
        }

        // ----- RARE DROP! / PET DROP! / RARE CROP! ... -----
        Matcher rd = RARE_DROP.matcher(plain);
        if (rd.matches()) {
            Debug.saw("rare drop");
            Session s = shown();
            if (s != null) addRare(s, rd.group(2));
            return;
        }

        // ----- sacks -----
        Session target = mostRecent();
        if (!plain.startsWith("[Sacks]")) { Debug.unrecognised(plain); return; }
        Debug.saw("sacks");
        if (target == null) return;
        List<Component> hovers = new ArrayList<>();
        collectHovers(message, hovers);
        // Hypixel puts the same hover list on several pieces of the message: read each different list once
        java.util.Set<String> seenHovers = new java.util.HashSet<>();
        hovers.removeIf(h -> !seenHovers.add(h.getString()));
        for (Component hover : hovers) {
            for (String line : strip(hover.getString()).split("\n")) {
                Matcher m = SACK_LINE.matcher(line);
                if (!m.matches()) continue;
                long amount = Long.parseLong(m.group(1).replace(",", "").replace("+", ""));
                if (amount < 0) {
                    // Taking items out of sacks (crafting enchanted versions, using them) isn't a loss:
                    // the crafted result appears in a menu, which isn't counted. Only visitor requests cost you.
                    if (System.currentTimeMillis() - Visitors.lastAccepted < 10_000 && sessions.containsKey(FARMING)) {
                        sessions.get(FARMING).addSpent(m.group(2).trim(), -amount);
                    }
                    continue;
                }
                Greenhouse.noticeName(m.group(2).trim());
                if (!Attribution.counts(target, m.group(2).trim(), 35_000)) continue;     // sack lines cover the last 30 s
                target.addItem(m.group(2).trim(), amount);
            }
        }
    }

    private static void addRare(Session s, String raw) {
        String item = raw.replaceAll("^[^A-Za-z0-9\\[]+", "").trim();
        while (item.matches(".*\\([^()]*\\)\\s*$") && !item.startsWith("Enchanted Book (")) {
            item = item.replaceAll("\\s*\\([^()]*\\)\\s*$", "");
        }
        if (item.startsWith("Enchanted Book (") && item.indexOf(')') < item.length() - 1) {
            item = item.substring(0, item.indexOf(')') + 1);
        }
        int count = 1;
        Matcher a = AMOUNT_SUFFIX.matcher(item);
        if (a.matches()) { item = a.group(1).trim(); count = Integer.parseInt(a.group(2)); }
        Matcher b = AMOUNT_PREFIX.matcher(item);
        if (b.matches()) { item = b.group(2).trim(); count = Integer.parseInt(b.group(1)); }
        if (!item.isEmpty() && !item.endsWith(" Shard")) { s.rareDrops.merge(item, count, Integer::sum); s.noteDrop(item); }
    }

    private static void scanShards(String text) {
        if (!text.contains("Shard")) return;
        String lower = text.toLowerCase();
        for (String bad : SHARD_IGNORE) if (lower.contains(bad)) return;

        Session s = FORAGING.equals(area) ? activity(FORAGING) : shown();
        if (s == null) return;

        for (String line : text.split("\n")) {
            Matcher m = SHARD.matcher(line);
            while (m.find()) {
                String name = cleanShardName(m.group(2));
                if (name == null) continue;
                int count = m.group(1) != null ? Integer.parseInt(m.group(1))
                        : m.group(3) != null ? Integer.parseInt(m.group(3)) : 1;
                long now = System.currentTimeMillis();
                String key = name + "#" + count;
                if (key.equals(lastShardKey) && now - lastShardTime < 1500) continue;
                lastShardKey = key;
                lastShardTime = now;
                s.shards.merge(name + " Shard", count, Integer::sum);
            }
        }
    }

    private static String cleanShardName(String raw) {
        String[] words = raw.trim().split(" ");
        for (int i = 0; i < words.length; i++) {
            String candidate = String.join(" ", Arrays.copyOfRange(words, i, words.length));
            if (Prices.knowsShard(candidate + " Shard")) return candidate;
        }
        int start = 0;
        while (start < words.length - 1 && SHARD_STOPWORDS.contains(words[start])) start++;
        if (SHARD_STOPWORDS.contains(words[start])) return null;
        return String.join(" ", Arrays.copyOfRange(words, start, words.length));
    }

    private static void collectHovers(Component c, List<Component> out) {
        if (c.getStyle().getHoverEvent() instanceof HoverEvent.ShowText st) out.add(st.value());
        for (Component sibling : c.getSiblings()) collectHovers(sibling, out);
    }

    // ---------- helpers ----------

    public static String strip(String s) {
        String r = ChatFormatting.stripFormatting(s);
        return r == null ? "" : r;
    }

    public static void say(String msg) {
        say(Component.literal(msg));
    }

    public static void say(Component c) {
        Minecraft mc = Minecraft.getInstance();
        String msg = c.getString();
        if (mc.player == null) return;
        // Minecraft keeps renaming its chat methods, so find one at runtime.
        if (call(mc.player, "sendSystemMessage", c)) return;
        if (call(mc.player, "displayClientMessage", c, false)) return;
        try {
            Object listener = mc.getClass().getMethod("getChatListener").invoke(mc);
            if (call(listener, "handleSystemMessage", c, false)) return;
        } catch (Exception ignored) {}
        FarmProfitClient.LOG.info(msg);
    }

    private static boolean call(Object target, String name, Object... args) {
        for (java.lang.reflect.Method m : target.getClass().getMethods()) {
            if (!m.getName().equals(name) || m.getParameterCount() != args.length) continue;
            try { m.invoke(target, args); return true; } catch (Exception ignored) {}
        }
        return false;
    }

    private Tracker() {}
}
