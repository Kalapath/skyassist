package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Makes pests easy to see in the Garden: a particle column and a trail toward each pest (only you see them),
 * and a list with arrows, distance and the plot numbers from the tab list.
 */
public final class Pests {
    private static final String[] NAMES = {"Beetle", "Cricket", "Fly", "Locust", "Mite", "Mosquito", "Moth", "Rat", "Slug",
            "Earthworm", "Mouse", "Field Mouse", "Dragonfly", "Firefly", "Mantis", "Praying Mantis"};
    private static final Pattern PEST_TAG = Pattern.compile("ൠ\\s*([A-Za-z ]+?)(?:\\s+[\\d.,]+[kKmM]?(?:/[\\d.,]+[kKmM]?)?\\s*❤)?\\s*$");
    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    public record Pest(String name, double x, double y, double z, Entity mob) {}

    private static final List<Pest> pests = new ArrayList<>();
    private static final java.util.Set<Integer> GLOWING = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static volatile boolean glowHooked;

    /** Called by the glow hooks: should this entity get the green outline? */
    public static boolean isHighlighted(Entity e) {
        glowHooked = true;
        return e != null && Config.get().pestHighlight && Config.get().pestGlow && GLOWING.contains(e.getId());
    }

    public static int glowColor() { return Config.get().pestBoxColor(); }
    private static int tick;

    public static void tick(Minecraft mc) {
        if (!Config.get().pestHighlight || mc.player == null || mc.level == null || !Tracker.FARMING.equals(Tracker.area)) {
            pests.clear();
            return;
        }
        if (++tick % (4 * Perf.slow()) == 0) scan(mc);     // 5x a second (half in Performance mode)
        if (tick % (3 * Perf.slow()) != 0) return;          // redraw often so the box stays bright
        for (Pest p : pests) {
            if (Config.get().pestBox) box(p);
            if (Config.get().pestTrail) {
                // a short dotted trail from you toward the pest, so you can follow it
                double dx = p.x() - mc.player.getX(), dy = p.y() - mc.player.getY(), dz = p.z() - mc.player.getZ();
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 2) {
                    double len = Math.min(6, d - 1);
                    Particles.line(Particles.dust(Config.get().pestBoxColor(), 0.8f), mc.player.getX(), mc.player.getY() + 1, mc.player.getZ(),
                            mc.player.getX() + dx / d * len, mc.player.getY() + 1 + dy / d * len, mc.player.getZ() + dz / d * len);
                }
            }
        }
    }

    /**
     * Pests are a Bat (Fly, Mosquito, Moth) or a Silverfish (all the others) in the Garden, where no other bats or
     * silverfish exist (wiki). That's what's searched for; the floating name tag only supplies the pest's name.
     */
    private static void scan(Minecraft mc) {
        pests.clear();
        Object all = Reflect.call(mc.level, new String[]{"entitiesForRendering", "getEntities"});
        if (!(all instanceof Iterable<?> it)) return;
        List<Entity> list = new ArrayList<>();
        for (Object o : it) if (o instanceof Entity e) list.add(e);
        java.util.Set<Integer> glow = new java.util.HashSet<>();
        for (Entity mob : list) {
            String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).getPath();
            if (!type.equals("bat") && !type.equals("silverfish")) continue;
            String name = type.equals("bat") ? "Flying pest" : "Pest";
            Entity tag = null;
            for (Entity t : list) {                         // its name tag floats just above it
                if (t == mob || !t.hasCustomName() || t.getCustomName() == null) continue;
                if (Math.abs(t.getX() - mob.getX()) > 1.5 || Math.abs(t.getZ() - mob.getZ()) > 1.5 || t.getY() < mob.getY() - 0.5 || t.getY() > mob.getY() + 3) continue;
                String s = Tracker.strip(t.getCustomName().getString()).trim();
                for (String n : NAMES) if (s.contains(n)) { name = n; tag = t; break; }
                if (tag != null) break;
            }
            pests.add(new Pest(name, mob.getX(), mob.getY() + 1, mob.getZ(), mob));
            glow.add(mob.getId());
            if (tag != null) glow.add(tag.getId());
            // the pest's head model is an armor stand right at it: outline that too
            for (Entity part : list) {
                if (part == mob) continue;
                String t = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(part.getType()).getPath();
                if (t.equals("armor_stand") && Math.abs(part.getX() - mob.getX()) < 1.0 && Math.abs(part.getZ() - mob.getZ()) < 1.0
                        && Math.abs(part.getY() - mob.getY()) < 2.0) glow.add(part.getId());
            }
            // also set Minecraft's own glow flag (works even if the glow hook couldn't attach)
            if (Config.get().pestGlow && !Glow.useParticles()) Reflect.call(mob, "setGlowingTag", true);
        }
        GLOWING.clear();
        GLOWING.addAll(glow);
        Glow.setPests(glow);
        double px = mc.player.getX(), pz = mc.player.getZ();
        pests.sort((a, b) -> Double.compare(Math.hypot(a.x() - px, a.z() - pz), Math.hypot(b.x() - px, b.z() - pz)));
    }

    public static int count() { return pests.size(); }


    /** The actual pest mob under its floating name tag (the tag itself is an invisible armor stand). */
    private static Entity mobUnder(Entity tag, Iterable<?> all) {
        Entity best = null;
        double bestD = 3.5;
        for (Object o : all) {
            if (!(o instanceof Entity e) || e == tag) continue;
            String type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if (type.equals("armor_stand") || type.equals("player") || type.contains("display")) continue;
            double d = Math.sqrt(Math.pow(e.getX() - tag.getX(), 2) + Math.pow(e.getY() - tag.getY(), 2) + Math.pow(e.getZ() - tag.getZ(), 2));
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /** A box of bright particles along the 12 edges of the pest's hitbox (or a 1-block box at its tag). */
    private static void box(Pest p) {
        double x1, y1, z1, x2, y2, z2;
        if (p.mob() != null) {
            var bb = p.mob().getBoundingBox();
            x1 = bb.minX - 0.15; y1 = bb.minY - 0.1; z1 = bb.minZ - 0.15; x2 = bb.maxX + 0.15; y2 = bb.maxY + 0.15; z2 = bb.maxZ + 0.15;
        } else {
            x1 = p.x() - 0.6; y1 = p.y() - 1.2; z1 = p.z() - 0.6; x2 = p.x() + 0.6; y2 = p.y() + 0.2; z2 = p.z() + 0.6;
        }
        var c = Particles.dust(Config.get().pestBoxColor(), 1.6f);
        double[][] corners = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            double[] a = corners[e[0]], b = corners[e[1]];
            Particles.dense(c, a[0], a[1], a[2], b[0], b[1], b[2], 0.12 * Perf.slow());
        }
    }

    private static String arrow(Pest p, Minecraft mc) {
        double target = Math.toDegrees(Math.atan2(-(p.x() - mc.player.getX()), p.z() - mc.player.getZ()));
        double rel = ((target - mc.player.getYRot()) % 360 + 540) % 360 - 180;
        int idx = (int) Math.round(rel / 45.0);
        return ARROWS[((idx % 8) + 8) % 8];
    }

    /** HUD lines for the Farming HUD. */
    public static void addHudLines(Hud.Lines out) {
        if (!Config.get().pestHighlight || !Tracker.FARMING.equals(Tracker.area)) return;
        Minecraft mc = Minecraft.getInstance();
        String alive = Tracker.tab.get("Alive");
        String plots = Tracker.tab.get("Plots");
        if (pests.isEmpty() && (alive == null || alive.startsWith("0"))) return;
        out.add("§c§lൠ Pests" + (alive != null ? " §7" + alive + " alive" : "") + (plots != null ? " §8(plots " + plots + ")" : ""));
        for (int i = 0; i < Math.min(5, pests.size()); i++) {
            Pest p = pests.get(i);
            double dy = p.y() - mc.player.getY();
            double d = Math.sqrt(Math.pow(p.x() - mc.player.getX(), 2) + dy * dy + Math.pow(p.z() - mc.player.getZ(), 2));
            out.add(" §f" + arrow(p, mc) + " §c" + p.name() + " §7" + String.format(Locale.US, "%.0fm", d) + (dy > 3 ? " §7▲" : dy < -3 ? " §7▼" : ""));
        }
        if (pests.isEmpty()) out.add(" §8none nearby — check the plots above");
        if (pests.isEmpty()) { GLOWING.clear(); Glow.setPests(java.util.Set.of()); }
    }

    public static boolean isPestName(String n) {
        for (String s : NAMES) if (s.equalsIgnoreCase(n)) return true;
        return false;
    }

    private Pests() {}
}
