package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Diana (Mythological Ritual) helper, using the particle hook:
 *  - burrows: Hypixel marks them with particles on the ground; each one gets a colored pillar and a HUD line
 *    (green = start / empty, red = mob, gold = treasure),
 *  - Ancestral Spade: after you use it, the particle trail it shows is followed into a direction beam.
 */
public final class DianaBurrows {
    private static final class Burrow {
        final BlockPos pos; String kind; long lastSeen;
        Burrow(BlockPos pos, String kind, long t) { this.pos = pos; this.kind = kind; this.lastSeen = t; }
    }

    private static final List<Burrow> burrows = new ArrayList<>();
    private static long spadeAt;
    private static double sx, sy, sz;
    private static final List<double[]> trail = new ArrayList<>();
    private static double[] guess;              // {x, z, dirX, dirZ}
    private static long guessAt;
    private static int tick;
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    static boolean onDiana() {
        if (!Config.get().dianaHelper) return false;
        String a = Tracker.areaName;
        return (a != null && a.contains("Hub")) || Tracker.sessions.containsKey(Tracker.DIANA);
    }

    /** Every particle from the server (from the hook). */
    static synchronized void onParticle(String type, double x, double y, double z) {
        if (!onDiana()) return;
        long now = System.currentTimeMillis();
        // spade trail: particles in the few seconds after using the spade, away from you
        if (now - spadeAt < 3000) {
            double d = Math.hypot(x - sx, z - sz);
            if (d > 2 && d < 40 && !type.equals("crit") && !type.equals("enchanted_hit")) trail.add(new double[]{x, z});
        }
        String kind = switch (type) {
            case "enchanted_hit", "enchant" -> "start";
            case "crit" -> "mob";
            case "dripping_lava", "falling_lava" -> "treasure";
            default -> null;
        };
        if (kind == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos ground = BlockPos.containing(x, y - 0.5, z);
        String block = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(ground).getBlock()).getPath();
        if (!(block.contains("grass") || block.contains("dirt") || block.equals("podzol") || block.equals("mycelium") || block.equals("farmland"))) return;
        for (Burrow b : burrows) if (b.pos.equals(ground)) { b.lastSeen = now; if (!"mob".equals(b.kind) || kind.equals("treasure")) b.kind = kind; return; }
        if (burrows.size() < 20) burrows.add(new Burrow(ground.immutable(), kind, now));
    }

    /** You used the Ancestral Spade. */
    public static synchronized void onSpade(double x, double y, double z) {
        spadeAt = System.currentTimeMillis();
        sx = x; sy = y; sz = z;
        trail.clear();
    }

    /** "You dug out ..." removes the burrow you're standing at. */
    public static synchronized void onDug() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        burrows.removeIf(b -> b.pos.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()) < 16);
    }

    public static synchronized void tick(Minecraft mc) {
        if (!onDiana() || mc.player == null) { burrows.clear(); guess = null; return; }
        long now = System.currentTimeMillis();
        burrows.removeIf(b -> now - b.lastSeen > 45_000);
        // spade guess: once the trail is in, fit a direction from where you stood
        if (now - spadeAt > 3000 && now - spadeAt < 3500 && trail.size() >= 3) {
            double ax = 0, az = 0;
            for (double[] p : trail) { ax += p[0] - sx; az += p[1] - sz; }
            double len = Math.hypot(ax, az);
            if (len > 0) { guess = new double[]{sx, sz, ax / len, az / len}; guessAt = now; }
            trail.clear();
        }
        if (++tick % (5 * Perf.slow()) != 0) return;
        for (Burrow b : burrows) {
            int color = switch (b.kind) { case "mob" -> 0xFF3030; case "treasure" -> 0xFFAA00; default -> 0x40FF40; };
            var p = Particles.dust(color, 1.6f);
            for (int k = 0; k < 8; k++) Particles.point(p, b.pos.getX() + 0.5, b.pos.getY() + 1.2 + k * 0.5, b.pos.getZ() + 0.5);
        }
        if (guess != null && now - guessAt < 30_000) {
            var beam = Particles.dust(0x55FFFF, 1.2f);
            for (int k = 3; k < 120; k += 3) Particles.point(beam, guess[0] + guess[2] * k, sy + 1.5, guess[1] + guess[3] * k);
        }
    }

    private static String arrow(Minecraft mc, double x, double z) {
        double target = Math.toDegrees(Math.atan2(-(x - mc.player.getX()), z - mc.player.getZ()));
        double rel = ((target - mc.player.getYRot()) % 360 + 540) % 360 - 180;
        return ARROWS[(((int) Math.round(rel / 45.0)) % 8 + 8) % 8];
    }

    public static synchronized void addHudLines(Hud.Lines out) {
        if (!onDiana()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (guess != null && System.currentTimeMillis() - guessAt < 30_000)
            out.add("§b§lSpade §7direction: §f" + arrow(mc, guess[0] + guess[2] * 50, guess[1] + guess[3] * 50) + " §8(follow the cyan beam)");
        if (burrows.isEmpty()) return;
        out.add("§e§lBurrows §7" + burrows.size());
        List<Burrow> sorted = new ArrayList<>(burrows);
        sorted.sort((a, b) -> Double.compare(a.pos.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()),
                b.pos.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ())));
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            Burrow b = sorted.get(i);
            String col = switch (b.kind) { case "mob" -> "§cMob"; case "treasure" -> "§6Treasure"; default -> "§aStart"; };
            double d = Math.sqrt(b.pos.distToCenterSqr(mc.player.getX(), mc.player.getY(), mc.player.getZ()));
            out.add(" §f" + arrow(mc, b.pos.getX() + 0.5, b.pos.getZ() + 0.5) + " " + col + " §7" + String.format(Locale.US, "%.0fm", d));
        }
    }

    private DianaBurrows() {}
}
