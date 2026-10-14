package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Crystal Hollows treasure chests (the ones you uncover while mining Hard Stone), like SkyHanni / Skyblocker:
 *  - every uncovered chest gets a bright box, colored by how long it has left before it despawns (~1 minute),
 *  - a list with arrows, distance and a countdown,
 *  - chests opened, chests per minute, Double Powder, and what came out of them.
 */
public final class PowderChests {
    private static final Pattern REWARD = Pattern.compile("^(?:(\\d[\\d,]*)x\\s+)?(.+?)(?:\\s+x(\\d[\\d,]*))?$");
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private record Chest(BlockPos pos, long spawned) {}

    private static final List<Chest> chests = new ArrayList<>();
    private static long lookUntil;
    private static int tick;
    public static boolean doublePowder;

    /** Is this point on (or right next to) a chest you uncovered? Falls back to any chest block there. */
    public static boolean nearChest(double x, double y, double z) {
        for (Chest c : chests) {
            if (Math.abs(x - (c.pos().getX() + 0.5)) < 1.3 && Math.abs(y - (c.pos().getY() + 0.5)) < 1.3 && Math.abs(z - (c.pos().getZ() + 0.5)) < 1.3) return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return false;
        BlockPos p = BlockPos.containing(x, y, z);
        for (BlockPos q : new BlockPos[]{p, p.north(), p.south(), p.east(), p.west(), p.below()}) if (mc.level.getBlockState(q).is(Blocks.CHEST)) return true;
        return false;
    }

    /** Where the uncovered chests are, for the Crystal Hollows map: {x, z, milliseconds left}. */
    static List<double[]> points() {
        long life = Config.get().chestLifetimeSeconds * 1000L, now = System.currentTimeMillis();
        List<double[]> out = new ArrayList<>();
        for (Chest c : chests) out.add(new double[]{c.pos().getX() + 0.5, c.pos().getZ() + 0.5, life - (now - c.spawned())});
        return out;
    }

    private static boolean inHollows() {
        return Tracker.MINING.equals(Tracker.area) && Tracker.areaName != null && Tracker.areaName.contains("Hollows");
    }

    /** Returns true if the message was about a treasure chest. */
    public static boolean onChat(String plain) {
        if (plain.contains("You uncovered a treasure chest")) {
            lookUntil = System.currentTimeMillis() + 3000;                // find the new chest block in the next moments
            Debug.saw("treasure chest");
            return true;
        }
        if (plain.contains("CHEST LOCKPICKED") || plain.contains("LOOT CHEST COLLECTED")) {
            Session s = Tracker.miningSession();
            s.chestsOpened++;
            boolean rewards = false;
            for (String raw : plain.split("\n")) {
                String line = raw.replace("▬", "").trim();
                if (line.isEmpty()) continue;
                if (line.equals("REWARDS")) { rewards = true; continue; }
                if (!rewards || line.contains("LOCKPICKED") || line.contains("COLLECTED")) continue;
                Matcher m = REWARD.matcher(line);
                if (!m.matches()) continue;
                String name = m.group(2).trim();
                int n = 1;
                try {
                    if (m.group(1) != null) n = Integer.parseInt(m.group(1).replace(",", ""));
                    else if (m.group(3) != null) n = Integer.parseInt(m.group(3).replace(",", ""));
                } catch (NumberFormatException ignored) {}
                s.chestLoot.merge(name, n, Integer::sum);
            }
            Debug.saw("chest opened");
            return true;
        }
        return false;
    }

    public static void tick(Minecraft mc) {
        if (!Config.get().powderChests || mc.player == null || mc.level == null || !inHollows()) { chests.clear(); return; }
        long now = System.currentTimeMillis();
        tick++;
        // Double Powder: shown in the tab list / sidebar while the event runs
        if (tick % 40 == 0) {
            boolean dp = false;
            for (String l : Tracker.tabLines) if (l.toUpperCase(Locale.ROOT).contains("2X POWDER")) dp = true;
            for (String l : Tracker.sidebarLines) if (l.toUpperCase(Locale.ROOT).contains("2X POWDER")) dp = true;
            doublePowder = dp;
        }
        // just uncovered one: find the chest block that appeared next to you
        if (now < lookUntil && tick % 2 == 0) {
            Set<Long> known = new HashSet<>();
            for (Chest c : chests) known.add(c.pos().asLong());
            BlockPos me = mc.player.blockPosition();
            for (int dx = -6; dx <= 6; dx++) for (int dy = -4; dy <= 5; dy++) for (int dz = -6; dz <= 6; dz++) {
                BlockPos p = me.offset(dx, dy, dz);
                if (!known.contains(p.asLong()) && mc.level.getBlockState(p).is(Blocks.CHEST)) {
                    chests.add(new Chest(p.immutable(), now));
                    lookUntil = 0;
                    known.add(p.asLong());
                }
            }
        }
        long life = Config.get().chestLifetimeSeconds * 1000L;
        chests.removeIf(c -> !mc.level.getBlockState(c.pos()).is(Blocks.CHEST) || now - c.spawned() > life + 5000);
        if (tick % (4 * Perf.slow()) != 0) return;
        for (Chest c : chests) {
            long left = life - (now - c.spawned());
            int color = left > 30_000 ? 0x40FF40 : left > 10_000 ? 0xFFE040 : 0xFF3030;   // green, yellow, red
            box(c.pos(), Particles.dust(color, 1.3f));
        }
    }

    private static void box(BlockPos p, net.minecraft.core.particles.ParticleOptions c) {
        double x1 = p.getX() - 0.05, y1 = p.getY() - 0.05, z1 = p.getZ() - 0.05, x2 = p.getX() + 1.05, y2 = p.getY() + 1.0, z2 = p.getZ() + 1.05;
        double[][] k = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) Particles.dense(c, k[e[0]][0], k[e[0]][1], k[e[0]][2], k[e[1]][0], k[e[1]][1], k[e[1]][2], 0.2);
    }

    /** Lines for the Mining HUD (Crystal Hollows only). */
    public static void addHudLines(Hud.Lines out, Session s) {
        if (!Config.get().powderChests || !inHollows()) return;
        Minecraft mc = Minecraft.getInstance();
        Lockpick.addHudLines(out);
        if (doublePowder) out.add("§b§l2x Powder §aactive");
        if (s != null && s.chestsOpened > 0) {
            double min = s.durationMs(System.currentTimeMillis()) / 60_000.0;
            out.add("§7Chests opened: §6" + s.chestsOpened + (min >= 1 ? String.format(Locale.US, " §8(%.1f/min)", s.chestsOpened / min) : ""));
        }
        if (chests.isEmpty() || mc.player == null) return;
        long now = System.currentTimeMillis(), life = Config.get().chestLifetimeSeconds * 1000L;
        out.add("§6§lTreasure chests §7" + chests.size());
        List<Chest> sorted = new ArrayList<>(chests);
        sorted.sort((a, b) -> Long.compare(a.spawned(), b.spawned()));                   // the one about to vanish first
        for (int i = 0; i < Math.min(6, sorted.size()); i++) {
            Chest c = sorted.get(i);
            long left = Math.max(0, life - (now - c.spawned()));
            String col = left > 30_000 ? "§a" : left > 10_000 ? "§e" : "§c";
            double dx = c.pos().getX() + 0.5 - mc.player.getX(), dz = c.pos().getZ() + 0.5 - mc.player.getZ();
            double dy = c.pos().getY() - mc.player.getY();
            double target = Math.toDegrees(Math.atan2(-dx, dz));
            double rel = ((target - mc.player.getYRot()) % 360 + 540) % 360 - 180;
            String arrow = ARROWS[(((int) Math.round(rel / 45.0)) % 8 + 8) % 8];
            out.add(" §f" + arrow + " §7" + String.format(Locale.US, "%.0fm", Math.sqrt(dx * dx + dy * dy + dz * dz))
                    + (dy > 2 ? " ▲" : dy < -2 ? " ▼" : "") + "  " + col + (left / 1000) + "s");
        }
    }

    /** What came out of the chests this session (shown under the Mining HUD details). */
    public static void addLootLines(Hud.Lines out, Session s) {
        if (!Config.get().powderChests || s == null || s.chestLoot == null || s.chestLoot.isEmpty()) return;
        var loot = new ArrayList<>(s.chestLoot.entrySet());
        loot.sort((a, b) -> b.getValue() - a.getValue());
        out.add("§7Chest loot:");
        for (int i = 0; i < Math.min(4, loot.size()); i++) out.add(" §f" + Fmt.num(loot.get(i).getValue()) + "x §d" + loot.get(i).getKey());
        if (loot.size() > 4) out.add("§8 + " + (loot.size() - 4) + " more (/miningprofit)");
    }

    private PowderChests() {}
}
