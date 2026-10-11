package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Glowing outlines (visible through walls) for things worth seeing: pests, starred dungeon mobs, keys, bats,
 * Zealots / Special Zealots, Ghosts, commission mobs, your slayer boss, rare sea creatures, Minos Inquisitors.
 * The glow itself is switched on by two small hooks (GlowMixin / GlowColorMixin).
 */
public final class Glow {
    private static final Map<Integer, Integer> COLORS = new ConcurrentHashMap<>();
    private static final Map<Integer, Integer> PESTS = new ConcurrentHashMap<>();
    public static volatile boolean hooked;
    /** Your slayer boss: its name tag text (with health) and its timer, from the tags around "Spawned by: you". */
    public static volatile String bossTag, bossTimer;
    public static volatile long bossSeen;
    private static int tick;

    private static final List<String> SEA = List.of("Thunder", "Lord Jawbus", "Sea Emperor", "Water Hydra", "Great White Shark",
            "Grim Reaper", "Phantom Fisher", "Yeti", "Reindrake", "Plhlegblast", "Ragnarok", "Wiki Tiki", "Titanoboa", "Abyssal Miner");
    private static final List<String> COMMISSION = List.of("Goblin", "Glacite Walker", "Ice Walker", "Treasure Hoarder", "Star Sentry");

    private static long shaderCheck;
    private static boolean shaders;

    /** Is an Iris shader pack in use? (Shaders often break Minecraft's glowing outline.) */
    public static boolean shadersOn() {
        long now = System.currentTimeMillis();
        if (now - shaderCheck > 5000) {
            shaderCheck = now;
            shaders = false;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object inst = api.getMethod("getInstance").invoke(null);
                shaders = Boolean.TRUE.equals(api.getMethod("isShaderPackInUse").invoke(inst));
            } catch (Throwable ignored) {}
        }
        return shaders;
    }

    /** Use particle boxes instead of the glowing outline? */
    public static boolean useParticles() {
        String m = Config.get().glowStyle;
        return "particles".equals(m) || ("auto".equals(m) && shadersOn());
    }

    /** Called by the hooks for every entity being drawn. */
    public static boolean isHighlighted(Entity e) {
        hooked = true;
        if (useParticles()) return false;
        return e != null && (COLORS.containsKey(e.getId()) || PESTS.containsKey(e.getId()));
    }

    public static int color(Entity e) {
        Integer c = PESTS.get(e.getId());
        if (c == null) c = COLORS.get(e.getId());
        return c == null ? 0xFFFFFF : c;
    }

    /** Pests report their parts here (they're found by Pests.java). */
    static void setPests(java.util.Set<Integer> ids) {
        PESTS.clear();
        if (Config.get().pestHighlight && Config.get().pestGlow) for (int id : ids) PESTS.put(id, Config.get().pestBoxColor());
    }

    /** The real mob under a floating name tag (Hypixel's tags are separate invisible armor stands). */
    static Entity mobUnder(Entity tag, Iterable<?> all) {
        Entity best = null;
        double bestD = 3.5;
        for (Object o : all) {
            if (!(o instanceof Entity e) || e == tag) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if (type.equals("armor_stand") || type.equals("player") || type.contains("display")) continue;
            if (e.getY() > tag.getY() + 0.5) continue;
            double d = Math.sqrt(Math.pow(e.getX() - tag.getX(), 2) + Math.pow(e.getY() - tag.getY(), 2) + Math.pow(e.getZ() - tag.getZ(), 2));
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    public static void tick(Minecraft mc) {
        if (++tick % (5 * Perf.slow()) != 0) return;
        if (mc.player == null || mc.level == null) { COLORS.clear(); return; }
        Config c = Config.get();
        boolean useful = !Tracker.FARMING.equals(Tracker.area) && !Tracker.FORAGING.equals(Tracker.area) || Tracker.sessions.containsKey(Tracker.COMBAT);
        if (!useful) { COLORS.clear(); return; }                      // nothing to outline here: don't scan at all
        Map<Integer, Integer> found = new HashMap<>();
        Object all = Reflect.call(mc.level, new String[]{"entitiesForRendering", "getEntities"});
        if (!(all instanceof Iterable<?> it)) return;
        String me = mc.player.getName().getString();
        boolean dungeon = Tracker.DUNGEONS.equals(Tracker.area), mining = Tracker.MINING.equals(Tracker.area);
        for (Object o : it) {
            if (!(o instanceof Entity e) || e == mc.player) continue;
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if (dungeon && c.glowBats && type.equals("bat") && !e.isInvisible()) { found.put(e.getId(), 0x55FF55); continue; }
            if (!e.hasCustomName() || e.getCustomName() == null || e.distanceTo(mc.player) > 64) continue;
            String tag = Tracker.strip(e.getCustomName().getString());
            Integer color = null;
            boolean tagItself = false;
            if (dungeon && c.glowKeys && (tag.contains("Wither Key") || tag.contains("Blood Key"))) { color = tag.contains("Blood") ? 0xFF3030 : 0x303030; tagItself = true; }
            else if (dungeon && c.glowStarred && tag.contains("✯")) color = 0xFFAA00;
            else if (tag.startsWith("Spawned by:") && tag.contains(me)) {
                readBoss(e, it);
                if (c.glowSlayer) color = 0xFF3030;
            }
            else if (c.glowZealots && tag.contains("Special Zealot")) color = 0xFF55FF;
            else if (c.glowZealots && tag.contains("Zealot")) color = 0xAA00AA;
            else if (mining && c.glowGhosts && tag.contains("Ghost")) color = 0xFFFFFF;
            else if (mining && c.glowCommission && COMMISSION.stream().anyMatch(tag::contains)) color = 0xFFFF55;
            else if (c.glowInquisitor && tag.contains("Minos Inquisitor")) color = 0xFFAA00;
            else if (c.glowSeaCreatures && SEA.stream().anyMatch(tag::contains)) color = 0x55FFFF;
            if (color == null) continue;
            if (tagItself) { found.put(e.getId(), color); continue; }
            Entity mob = mobUnder(e, it);
            if (mob != null) found.put(mob.getId(), color);
        }
        COLORS.clear();
        COLORS.putAll(found);
        if (useParticles()) drawBoxes(mc, it);
    }

    /** The boss's own name tag (health ❤) and timer tag sit just above "Spawned by". */
    private static void readBoss(Entity spawnedBy, Iterable<?> all) {
        for (Object o : all) {
            if (!(o instanceof Entity t) || t == spawnedBy || !t.hasCustomName() || t.getCustomName() == null) continue;
            if (Math.abs(t.getX() - spawnedBy.getX()) > 1.0 || Math.abs(t.getZ() - spawnedBy.getZ()) > 1.0 || Math.abs(t.getY() - spawnedBy.getY()) > 1.2) continue;
            String s = Tracker.strip(t.getCustomName().getString()).trim();
            if (s.contains("❤")) {
                bossTag = s;
                bossSeen = System.currentTimeMillis();
                Safe.run("Slayer boss timer", SlayerTimes::onBossSeen);
            }
            else if (s.matches("^\\d{1,2}:\\d{2}$")) bossTimer = s;
        }
    }

    /** "Boss: Revenant Horror 1.2M❤ (3:12)" for the Combat HUD, while your boss is alive. */
    public static String bossLine() {
        if (!Config.get().showBossHealth || bossTag == null || System.currentTimeMillis() - bossSeen > 3000) return null;
        return "§c§lBoss §f" + bossTag + (bossTimer != null ? " §7(" + bossTimer + ")" : "");
    }

    /** Shader-friendly fallback: a colored particle box around each highlighted mob (nearest 12). */
    private static void drawBoxes(Minecraft mc, Iterable<?> all) {
        int n = 0;
        for (Object o : all) {
            if (!(o instanceof Entity e) || !COLORS.containsKey(e.getId()) || n++ >= 12) continue;
            var bb = e.getBoundingBox();
            var c = Particles.dust(COLORS.get(e.getId()), 1.2f);
            double x1 = bb.minX, y1 = bb.minY, z1 = bb.minZ, x2 = bb.maxX, y2 = bb.maxY, z2 = bb.maxZ;
            double[][] k = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}, {x1, y2, z1}, {x2, y2, z1}, {x2, y2, z2}, {x1, y2, z2}};
            int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
            for (int[] ed : edges) Particles.dense(c, k[ed[0]][0], k[ed[0]][1], k[ed[0]][2], k[ed[1]][0], k[ed[1]][1], k[ed[1]][2], 0.25 * Perf.slow());
        }
    }

    private Glow() {}
}
