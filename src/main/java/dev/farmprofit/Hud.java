package dev.farmprofit;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds the HUD and command text. */
public final class Hud {

    /** One HUD line; item is set for lines that can be hidden with a right-click. */
    public record HudLine(String text, String item) {}

    public static final class Lines extends java.util.ArrayList<HudLine> {
        public boolean add(String text) { return add(new HudLine(text, null)); }
        public void item(String text, String item) { add(new HudLine(text, item)); }
        public List<String> texts() { return stream().map(HudLine::text).toList(); }
    }

    public static String title(String type) {
        if (Tracker.isMiningType(type)) {
            boolean here = Tracker.MINING.equals(Tracker.area) && Tracker.areaName != null && Tracker.MINING.equals(type);
            return "§2§l⛏ Mining" + (here ? " §7(" + Tracker.areaName + ")" : "");
        }
        return switch (type) {
            case Tracker.FORAGING -> "§a§l♣ Foraging" + (Tracker.FORAGING.equals(Tracker.area) && Tracker.areaName != null ? " §7(" + Tracker.areaName + ")" : "");
            case Tracker.FISHING -> "§9§l≈ Fishing";
            case Tracker.COMBAT -> "§c§l⚔ Combat";
            case Tracker.DUNGEONS -> "§4§l☠ Catacombs" + (Tracker.dungeonFloor != null ? " §7(" + Tracker.dungeonFloor + ")" : "");
            case Tracker.KUUDRA -> "§6§l♨ Kuudra";
            case Tracker.DIANA -> "§e§l✿ Diana";
            default -> "§6§lFarming";
        };
    }

    // ================= HUD =================

    public static Lines lines() {
        boolean details = Config.get().hudDetails;
        String type = Tracker.shownType();
        Lines out = new Lines();
        if (type == null || !Config.get().hudFor(type)) return out;
        Session s = Tracker.sessions.get(type);

        if (s == null) {
            if (Config.get().hudShowTitle) out.add(title(type));
            out.add(switch (type) {
                case Tracker.MINING -> "§7Start mining to begin tracking";
                case Tracker.FORAGING -> "§7Start chopping to begin tracking";
                case Tracker.DUNGEONS, Tracker.KUUDRA -> "§7Start a run to begin tracking";
                default -> "§7Break a crop to start tracking";
            });
            boolean together = !Config.get().separatePanels;
            String tip = together ? Suggest.hudLine(null, type) : null;
            if (tip != null) out.add(tip);
            if (together && details && Tracker.FARMING.equals(type)) { String c = Contests.hudLine(); if (c != null) out.add(c); }
            if (details) { String m = Election.hudLine(type); if (m != null) out.add(m); }
            if (details) addCommissions(out, type);
            if (together && Tracker.DUNGEONS.equals(type)) Secrets.addHudLines(out);
            if (Tracker.FARMING.equals(type)) Pests.addHudLines(out);
            if (Tracker.MINING.equals(type)) { Mineshafts.addHudLines(out); PowderChests.addHudLines(out, null); }
            return out;
        }

        Config cfg = Config.get();
        long now = System.currentTimeMillis();
        String main = s.mainCrop();
        if (cfg.hudShowTitle) out.add(s.isCombat() && s.grind != null ? "§5§l✦ " + s.grind + " grind" : title(type));
        if (cfg.hudShowTime) out.add("§7Time: §f" + Fmt.duration(s.durationMs(now)));
        if (s.isCombat()) { String boss = Glow.bossLine(); if (boss != null) out.add(boss); addCombatLines(out, s, now); }
        if (s.isFishing()) { FishingAlert.addHudLines(out); FishingExtras.addHudLines(out); }
        if (s.isMining()) { Mineshafts.addHudLines(out); PowderChests.addHudLines(out, s); }
        if (details) addActivityLine(out, s, now);
        if (s.isCombat() && cfg.combatShowBosses && (s.totalBreaks() > 0 || s.slayerQuests > 0)) addBossLine(out, s, now);
        if ((s.isDungeons() && cfg.dungShowRuns) || (s.isKuudra() && cfg.kuudraShowRuns)) addRunLine(out, s, now);
        if (s.isDiana() && cfg.dianaShowBurrows) out.add("§7Burrows: §e" + Fmt.num(s.burrows) + rate(s.burrows, s, now));
        if (s.isDiana()) DianaBurrows.addHudLines(out);
        if (details) {
            if (statsOn(type)) addStats(out, type, main);
            addPowder(out, s, now);
            if (!cfg.separatePanels && s.isFarming()) { String c = Contests.hudLine(); if (c != null) out.add(c); }
            String m = Election.hudLine(type);
            if (m != null) out.add(m);
        }

        if (!Prices.loaded()) out.add("§cLoading Bazaar prices...");
        out.add("§7Profit: §6" + Fmt.coins(s.value()) + " coins");
        if (s.coins > 0) out.add("§7Coins found: §6+" + Fmt.coins(s.coins));
        if (cfg.hudShowCosts) addCostLines(out, s);
        if (cfg.hudShowRate) out.add("§7Profit/h: §6" + (s.durationMs(now) < 60_000 ? "§8wait 1 min" : Fmt.coins(s.perHour(now)) + "/h"));
        if (cfg.hudShowTotal) addTotalLine(out, type);

        String tip = cfg.separatePanels ? null : Suggest.hudLine(s, type);
        if (tip != null) out.add(tip);

        if (details) addExtras(out, s);
        addShards(out, s, cfg.hudMaxShards);
        addRareDrops(out, s, cfg.hudMaxRare);

        if (cfg.hudShowItems) addItems(out, s, type);

        if (details) addCommissions(out, type);
        if (s.isDungeons() && !cfg.separatePanels) Secrets.addHudLines(out);

        if (s.paused(now) && cfg.hudShowCountdown) out.add("§ePaused §7- resets in " + Fmt.clock(Tracker.resetMs() - Tracker.idleMs(s)));
        return out;
    }

    // ================= separate panels =================

    /** Lines for one panel id (see Panels.ALL). Empty = nothing to show right now. */
    public static Lines panel(String id) {
        Config cfg = Config.get();
        Lines out = new Lines();
        String type = Tracker.shownType();
        switch (id) {
            case "main" -> { return lines(); }
            case "bazaar" -> { return Bazaar.hudLines(); }
            case "greenhouse" -> Greenhouse.addHudLines(out);
            case "waypoints" -> Waypoints.addHudLines(out);
            case "timers" -> Timers.addHudLines(out);
            case "secrets" -> {
                if (cfg.separatePanels && Tracker.DUNGEONS.equals(Tracker.area)) Secrets.addHudLines(out);
            }
            case "contest" -> {
                if (cfg.separatePanels && cfg.showContests && Tracker.FARMING.equals(type)) {
                    String c = Contests.hudLine();
                    if (c != null) out.add(c);
                }
            }
            case "suggest" -> {
                if (cfg.separatePanels && type != null) {
                    String t = Suggest.hudLine(Tracker.sessions.get(type), type);
                    if (t != null) out.add(t);
                }
            }
            default -> { }
        }
        return out;
    }

    // ================= /command output: profit only =================

    public static List<String> profitLines(Session s) {
        return profitLinesRaw(s).texts();
    }

    private static Lines profitLinesRaw(Session s) {
        long now = System.currentTimeMillis();
        Lines out = new Lines();
        out.add(title(s.type));
        out.add("§7Time: §f" + Fmt.duration(s.durationMs(now)));
        if (s.isCombat()) { addCombatLines(out, s, now); if (s.totalBreaks() > 0) addBossLine(out, s, now); }
        if (s.isDungeons() || s.isKuudra()) addRunLine(out, s, now);
        if (s.isDiana()) out.add("§7Burrows: §e" + Fmt.num(s.burrows));
        out.add("§7Profit: §6" + Fmt.coins(s.value()) + " coins");
        if (s.coins > 0) out.add("§7Coins found: §6+" + Fmt.coins(s.coins));
        addCostLines(out, s);
        out.add("§7Profit/h: §6" + (s.durationMs(now) < 60_000 ? "§8wait 1 min" : Fmt.coins(s.perHour(now)) + "/h"));
        addTotalLine(out, s.type);
        addShards(out, s, Integer.MAX_VALUE);
        addRareDrops(out, s, Integer.MAX_VALUE);
        if (s.isMining() && s.chestsOpened > 0) {
            out.add("§7Treasure chests opened: §6" + s.chestsOpened);
            if (s.chestLoot != null) s.chestLoot.forEach((k, v) -> out.add(" §f" + Fmt.num(v) + "x §d" + k));
        }
        var items = sortedItems(s);
        if (!items.isEmpty()) out.add("§7Items:");
        for (var e : items) out.item(itemLine(e), e.getKey());
        if (s.spent != null && !s.spent.isEmpty()) {
            out.add("§7Spent:");
            for (var e : s.spent.entrySet()) out.item(" §c-" + Fmt.num(e.getValue()) + " §f" + e.getKey() + " §8(" + Fmt.coins(e.getValue() * Prices.price(e.getKey())) + ")", e.getKey());
        }
        return out;
    }

    private static void addCostLines(Lines out, Session s) {
        if (s.costs > 0) out.add("§7" + (s.isCombat() ? "Quest costs" : "Costs") + ": §c-" + Fmt.coins(s.costs));
        double spent = s.spentValue();
        if (spent > 0) out.add("§7Spent: §c-" + Fmt.coins(spent) + " §8(" + s.spent.size() + " item" + (s.spent.size() > 1 ? "s" : "") + ")");
        if (s.purseCoins > 0 && Config.get().hudShowPurse) out.add("§7Purse coins: §6+" + Fmt.coins(s.purseCoins) + " §8(Bountiful, mob coins...)");
        if (s.copper > 0) out.add("§7Copper: §c" + Fmt.num(s.copper) + (Config.get().copperValue > 0 ? " §8(" + Fmt.coins(s.copper * Config.get().copperValue) + ")" : ""));
    }

    private static void addTotalLine(Lines out, String type) {
        var t = Totals.all().get(Tracker.normalType(type));
        if (t == null || t.sessions == 0) return;
        double h = t.ms / 3_600_000.0;
        out.add("§8All-time: §6" + Fmt.coins(t.profit) + " §8(" + (h > 0 ? Fmt.coins(t.profit / h) : "0") + "/h, " + t.sessions + " sessions)");
    }

    private static String rate(int count, Session s, long now) {
        double h = s.hours(now);
        return h < 1.0 / 60 ? "" : String.format(Locale.US, " §8(%.0f/h)", count / h);
    }

    // ================= pieces =================

    /** Is the stats block (fortune, speed...) switched on for this activity? */
    private static boolean statsOn(String type) {
        Config c = Config.get();
        if (Tracker.isMiningType(type)) return c.mineShowStats;
        return switch (type) {
            case Tracker.FARMING -> c.farmShowFortune;
            case Tracker.FORAGING -> c.forShowStats;
            case Tracker.FISHING -> c.fishShowStats;
            case Tracker.COMBAT, Tracker.DUNGEONS, Tracker.KUUDRA, Tracker.DIANA -> c.combatShowMagicFind;
            default -> true;
        };
    }

    private static void addActivityLine(Lines out, Session s, long now) {
        Config c = Config.get();
        String bps = String.format(Locale.US, "%.1f", s.breaksPerSecond(now)) + " BPS)";
        if (s.isMining()) out.add("§7Mining: §a" + s.mainCrop() + " §8(" + Fmt.num(s.totalBreaks()) + " blocks, " + bps);
        else if (s.isForaging()) {
            out.add("§7Chopping: §a" + s.mainCrop() + " §8(" + Fmt.num(s.totalBreaks()) + " logs, " + bps);
            if (s.treeGifts > 0 && c.forShowTrees) {
                double min = s.durationMs(now) / 60_000.0;
                out.add("§7Trees: §a" + Fmt.num(s.treeGifts) + " gifts" + (min < 1 ? "" : String.format(Locale.US, " §8(%.1f/min)", s.treeGifts / min)));
            }
        } else if (s.isFishing()) {
            if (s.location != null && c.fishShowLocation) out.add("§7Location: §b" + s.location);
        } else if (s.isFarming() && c.farmShowBps) {
            out.add("§7Farming: §a" + s.mainCrop() + " §8(" + Fmt.num(s.totalBreaks()) + " broken, " + bps);
        }
    }

    /** Kills, kills/h, most-killed mobs, and for grinds: drop odds and "since last drop". */
    private static void addCombatLines(Lines out, Session s, long now) {
        Config c = Config.get();
        int kills = s.totalKills();
        double h = s.hours(now);
        if (c.combatShowKills && kills > 0) {
            out.add("§7Kills: §f" + Fmt.num(kills) + (h >= 1.0 / 60 ? String.format(Locale.US, " §8(%,.0f/h)", kills / h) : ""));
            if (s.grind == null && s.kills.size() > 1) {
                var top = new ArrayList<>(s.kills.entrySet());
                top.sort((a, b) -> b.getValue() - a.getValue());
                StringBuilder sb = new StringBuilder(" §8");
                for (int i = 0; i < Math.min(3, top.size()); i++) sb.append(i > 0 ? ", " : "").append(top.get(i).getKey()).append(" ").append(Fmt.num(top.get(i).getValue()));
                out.add(sb.toString());
            }
        }
        if (s.grind != null) {
            Combat.Grind g = Combat.byName(s.grind);
            int grindKills = 0;
            for (var e : s.kills.entrySet()) if (Combat.grindFor(e.getKey()) == g) grindKills += e.getValue();
            if (g != null) {
                for (String drop : g.drops()) {
                    long n = s.dropCount(drop);
                    Integer at = s.killsAtDrop.get(drop);
                    String since = at != null ? ", " + Fmt.num(kills - at) + " since last" : "";
                    out.item("§7" + drop + ": §d" + Fmt.num(n) + " " + Combat.odds(grindKills, (int) n) + since, drop);
                }
                if (g.specialName() != null) out.add("§7" + g.specialName() + ": §d" + s.specials + " " + Combat.odds(grindKills, s.specials));
            }
        }
        if (c.combatShowPerKill && kills > 0) out.add("§7Profit/kill: §6" + Fmt.coins(s.value() / kills));
    }

    private static void addBossLine(Lines out, Session s, long now) {
        int bosses = s.totalBreaks();
        double h = s.hours(now);
        String rate = h < 1.0 / 60 ? "" : String.format(Locale.US, " §8(%.1f/h)", bosses / h);
        out.add("§7Slayer: §c" + (bosses == 0 ? "no bosses yet" : s.mainCrop() + " §7x" + bosses) + rate);
    }

    private static void addRunLine(Lines out, Session s, long now) {
        if (s.isDungeons()) { String info = Dungeon.hudLine(); if (info != null) out.add(info); }
        if (s.runs == 0) { out.add("§7Runs: §f0 §8(finish a run to see your pace)"); return; }
        double h = s.hours(now);
        long avg = s.durationMs(now) / s.runs;
        out.add("§7Runs: §f" + s.runs + " §8(avg " + Fmt.duration(avg) + (h > 0 ? String.format(Locale.US, ", %.1f/h", s.runs / h) : "") + ")");
        if (s.lastScore != null && Config.get().dungShowScore) out.add("§7Last score: §f" + s.lastScore);
        if (s.runs > 0) out.add("§7Profit/run: §6" + Fmt.coins(s.value() / s.runs));
    }

    private static void addStats(Lines out, String type, String main) {
        boolean any = false;
        if (Tracker.FARMING.equals(type)) {
            String f = Tracker.tab.get("Farming Fortune");
            String[] crop = cropFortune(main);
            String c = crop == null ? null : crop[1];
            if (crop != null) main = crop[0] + (crop[2] != null ? " §8(last seen)" : "");
            if (f != null || c != null) {
                any = true;
                out.add("§7Fortune: §6" + (f != null ? f : "") + (c != null ? (f != null ? " §7+ §6" : "") + c + " §7" + main : ""));
            }
        } else {
            for (String k : Tracker.statKeys(type)) {
                String v = Tracker.tab.get(k);
                if (v == null) continue;
                any = true;
                out.add("§7" + k + ": §6" + v);
            }
        }
        if (!any && (Tracker.FARMING.equals(type) || Tracker.isMiningType(type) || Tracker.FORAGING.equals(type) || Tracker.FISHING.equals(type))) out.add("§7Stats: §8enable the Stats tab widget");
    }

    private static final String[] CROPS = {"Wheat", "Carrot", "Potato", "Pumpkin", "Melon", "Sugar Cane", "Cactus", "Cocoa Beans",
            "Nether Wart", "Mushroom", "Sunflower", "Moonflower", "Wild Rose"};
    private static final java.util.Map<String, String> LAST_CROP_FORTUNE = new java.util.HashMap<>();

    private static String fortuneKey(String crop) {
        for (String k : new String[]{crop + " Fortune", crop.replaceAll("s$", "") + " Fortune",
                crop.equals("Cocoa Beans") ? "Cocoa Fortune" : "", crop.equals("Melon") ? "Melon Slice Fortune" : "",
                crop.equals("Mushroom") ? "Red Mushroom Fortune" : ""}) {
            if (!k.isEmpty() && Tracker.tab.containsKey(k)) return k;
        }
        return null;
    }

    /** {crop, value, lastSeen?}: the main crop's fortune, else any crop fortune the tab shows, else the last one seen. */
    private static String[] cropFortune(String main) {
        for (String crop : CROPS) {                                   // remember every crop fortune we see
            String k = fortuneKey(crop);
            if (k != null) LAST_CROP_FORTUNE.put(crop, Tracker.tab.get(k));
        }
        String held = heldCrop();
        for (String crop : new String[]{main, held}) {
            if (crop == null) continue;
            String k = fortuneKey(crop);
            if (k != null) return new String[]{crop, Tracker.tab.get(k), null};
        }
        for (String crop : CROPS) { String k = fortuneKey(crop); if (k != null) return new String[]{crop, Tracker.tab.get(k), null}; }
        for (String crop : new String[]{main, held}) if (crop != null && LAST_CROP_FORTUNE.containsKey(crop)) return new String[]{crop, LAST_CROP_FORTUNE.get(crop), "old"};
        return null;
    }

    /** The crop your held tool is for, from its name (e.g. "Euclid's Wheat Hoe"). */
    private static String heldCrop() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) return null;
        String n = Tracker.strip(mc.player.getMainHandItem().getHoverName().getString());
        for (String crop : CROPS) {
            String word = crop.equals("Cocoa Beans") ? "Cocoa" : crop.equals("Nether Wart") ? "Wart" : crop.equals("Sugar Cane") ? "Cane" : crop;
            if (n.contains(word)) return crop;
        }
        if (n.contains("Fungi") || n.contains("Mushroom")) return "Mushroom";
        return null;
    }

    private static void addPowder(Lines out, Session s, long now) {
        if (s.powder == null || (!s.isMining() && !s.isForaging())) return;
        if ((s.isMining() && !Config.get().mineShowPowder) || (s.isForaging() && !Config.get().forShowWhispers)) return;
        double h = s.hours(now);
        List<String> kinds = new ArrayList<>();
        if (s.isForaging()) {
            for (String w : new String[]{"Forest Whispers", "Desert Whispers"}) if (s.powder.getOrDefault(w, 0L) > 0) kinds.add(w);
            if (kinds.isEmpty()) kinds.add(Tracker.areaName != null && Tracker.areaName.contains("Torrhus") ? "Desert Whispers" : "Forest Whispers");
        }
        else {
            for (String p : new String[]{"Mithril", "Gemstone", "Glacite"}) if (s.powder.getOrDefault(p, 0L) > 0) kinds.add(p);
            if (kinds.isEmpty()) {
                String an = Tracker.areaName == null ? "" : Tracker.areaName;
                kinds.add(an.contains("Hollows") ? "Gemstone" : (an.contains("Mineshaft") || an.contains("Glacite")) ? "Glacite" : "Mithril");
            }
        }
        for (String p : kinds) {
            long gained = s.powder.getOrDefault(p, 0L);
            String color = switch (p) { case "Gemstone" -> "§d"; case "Glacite" -> "§b"; case "Forest Whispers" -> "§3"; case "Desert Whispers" -> "§e"; default -> "§2"; };
            String rate = h < 1.0 / 60 ? "" : " §8(" + Fmt.coins(gained / h) + "/h)";
            out.add("§7" + (p.endsWith("Whispers") ? p : p + " Powder") + ": " + color + "+" + Fmt.num(gained) + rate);
        }
    }

    private static void addExtras(Lines out, Session s) {
        Config c = Config.get();
        if (s.isFarming()) { for (String l : Contests.liveLines()) out.add(l); Pests.addHudLines(out); }
        if (s.isFarming() && s.totalPests() > 0 && c.farmShowPests) {
            var pests = new ArrayList<>(s.pests.entrySet());
            pests.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder sb = new StringBuilder("§7Pests: §c" + s.totalPests() + " killed §8(");
            for (int i = 0; i < Math.min(3, pests.size()); i++) {
                if (i > 0) sb.append(", ");
                sb.append(pests.get(i).getKey()).append(" ").append(pests.get(i).getValue());
            }
            out.add(sb.append(pests.size() > 3 ? ", ...)" : ")").toString());
        }
        if (s.isMining()) PowderChests.addLootLines(out, s);
        if (s.isMining() && s.pristine > 0 && c.mineShowPristine) out.add("§7Pristine procs: §d" + s.pristine);
        if (s.isFishing() && s.trophyFish > 0 && c.fishShowTrophies) {
            var tr = new ArrayList<>(s.trophies.entrySet());
            tr.sort((a, b) -> b.getValue() - a.getValue());
            StringBuilder sb = new StringBuilder("§7Trophy fish: §6" + s.trophyFish + " §8(");
            for (int i = 0; i < Math.min(3, tr.size()); i++) sb.append(i > 0 ? ", " : "").append(tr.get(i).getKey()).append(" ").append(tr.get(i).getValue());
            out.add(sb.append(")").toString());
        }
        if (s.isFarming() && s.visitors > 0 && c.farmShowVisitors) out.add("§7Visitors: §a" + s.visitors + " accepted");
        if (s.isMining() && s.breaks.size() > 1 && c.mineShowBlocks) {
            out.add("§7Blocks:");
            var breaks = new ArrayList<>(s.breaks.entrySet());
            breaks.sort((a, b) -> b.getValue() - a.getValue());
            for (int i = 0; i < Math.min(c.mineBlocksShown, breaks.size()); i++) {
                out.add(" §f" + Fmt.num(breaks.get(i).getValue()) + " §d" + breaks.get(i).getKey());
            }
        }
    }

    private static void addShards(Lines out, Session s, int max) {
        if (!Config.get().showShards) return;
        if (s.totalShards() == 0) {
            if (s.isForaging()) out.add("§7Shards: §b0");
            return;
        }
        var shards = new ArrayList<>(s.shards.entrySet());
        shards.removeIf(e -> Session.ignored(e.getKey()));
        shards.sort((a, b) -> Double.compare(b.getValue() * Prices.price(b.getKey()), a.getValue() * Prices.price(a.getKey())));
        if (shards.isEmpty() || max <= 0) return;
        double value = s.shardValue();
        out.add("§7Shards: §b" + s.totalShards() + " §8(" + (value == 0 ? "?" : Fmt.coins(value)) + ")");
        for (int i = 0; i < Math.min(max, shards.size()); i++) out.item(shardLine(shards.get(i)), shards.get(i).getKey());
        if (shards.size() > max) out.add("§8 ...and " + (shards.size() - max) + " more");
    }

    public static String shardLine(Map.Entry<String, Integer> e) {
        double v = e.getValue() * Prices.price(e.getKey());
        return " §b" + e.getValue() + "x §f" + e.getKey() + " §8(" + (v == 0 ? "?" : Fmt.coins(v)) + ")";
    }

    private static void addRareDrops(Lines out, Session s, int max) {
        if (!Config.get().showRareDrops || s.rareDrops == null || s.rareDrops.isEmpty()) return;
        var drops = new ArrayList<>(s.rareDrops.entrySet());
        drops.removeIf(e -> Session.ignored(e.getKey()));
        if (drops.isEmpty() || max <= 0) return;
        drops.sort((a, b) -> Double.compare(b.getValue() * Prices.price(b.getKey()), a.getValue() * Prices.price(a.getKey())));
        int total = 0;
        for (var d : drops) total += d.getValue();
        out.add("§7Rare drops: §d" + total);
        for (int i = 0; i < Math.min(max, drops.size()); i++) out.item(rareLine(s, drops.get(i)), drops.get(i).getKey());
        if (drops.size() > max) out.add("§8 ...and " + (drops.size() - max) + " more");
    }

    public static String rareLine(Session s, Map.Entry<String, Integer> d) {
        double each = Prices.price(d.getKey());
        long amount = s.rareAlreadyCounted(d.getKey()) ? Math.max(d.getValue(), s.items.get(d.getKey())) : d.getValue();
        String value = each == 0 ? "?" : Fmt.coins(each * amount);
        return " §d" + d.getValue() + "x §f" + d.getKey() + " §8(" + value + ")";
    }

    private static void addCommissions(Lines out, String type) {
        if (!Config.get().showCommissions || Tracker.commissions.isEmpty() || !Tracker.MINING.equals(type)) return;
        out.add("§7Commissions:");
        for (String c : Tracker.commissions) {
            out.add(" §f" + c.replace("DONE", "§aDONE").replaceAll("(\\d+(\\.\\d+)?%)", "§e$1"));
        }
    }

    /** Item list for the HUD: top items, then one line for everything cheap. */
    private static void addItems(Lines out, Session s, String type) {
        var items = sortedItems(s);
        if (items.isEmpty()) return;
        int max = Math.max(1, Config.get().hudMaxItems);
        out.add("§7Items:");
        // everything fits: list it all. Too many: list the most valuable and sum up the rest in one line.
        int show = items.size() <= max ? items.size() : max - 1;
        for (int i = 0; i < show; i++) out.item(itemLine(items.get(i)), items.get(i).getKey());
        if (show < items.size()) {
            double rest = 0;
            for (int i = show; i < items.size(); i++) rest += items.get(i).getValue() * Prices.price(items.get(i).getKey());
            int n = items.size() - show;
            out.add("§8 + " + n + " other item" + (n > 1 ? "s" : "") + " (" + Fmt.coins(rest) + ") §8/" + command(type));
        }
    }

    /** The item that made the most money, e.g. "Enchanted Wheat (1.2M)". */
    public static String bestItem(Session s) {
        String best = null;
        double bestV = 0;
        for (var e : s.items.entrySet()) {
            double v = e.getValue() * Prices.price(e.getKey());
            if (v > bestV && !Session.ignored(e.getKey())) { bestV = v; best = e.getKey(); }
        }
        if (s.rareDrops != null) for (var e : s.rareDrops.entrySet()) {
            double v = s.rareValue(e.getKey(), e.getValue());
            if (v > bestV) { bestV = v; best = e.getKey(); }
        }
        return best == null ? null : "§a" + best + " §8(" + Fmt.coins(bestV) + ")";
    }

    public static List<Map.Entry<String, Long>> sortedItems(Session s) {
        var items = new ArrayList<>(s.items.entrySet());
        items.removeIf(e -> Session.ignored(e.getKey()));
        if (s.rareDrops != null) items.removeIf(e -> s.rareDrops.containsKey(e.getKey()));   // shown under Rare drops
        // a negative amount is raw material a compactor / crafting turned into something else: the result is listed,
        // the raw part only stays in the profit math (so the total is right) instead of showing as "-160 Wheat"
        if (Config.get().hideNegativeItems) items.removeIf(e -> e.getValue() < 0);
        items.sort((a, b) -> Double.compare(b.getValue() * Prices.price(b.getKey()), a.getValue() * Prices.price(a.getKey())));
        return items;
    }

    public static String itemLine(Map.Entry<String, Long> e) {
        double v = e.getValue() * Prices.price(e.getKey());
        String sign = e.getValue() > 0 ? "+" : "";
        return " §f" + sign + Fmt.num(e.getValue()) + " §a" + e.getKey() + " §8(" + (v == 0 ? "?" : Fmt.coins(v)) + ")";
    }

    public static String command(String type) {
        if (Tracker.isMiningType(type)) return "miningprofit";
        return switch (type) {
            case Tracker.FORAGING -> "foragingprofit";
            case Tracker.FISHING -> "fishingprofit";
            case Tracker.COMBAT -> "combatprofit";
            case Tracker.DUNGEONS -> "dungeonprofit";
            case Tracker.KUUDRA -> "kuudraprofit";
            case Tracker.DIANA -> "dianaprofit";
            default -> "farmprofit";
        };
    }

    private Hud() {}
}
