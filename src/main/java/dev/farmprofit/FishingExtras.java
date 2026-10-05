package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fishing helpers like SkyHanni's: bobber timer, your living sea creatures (count + oldest, the "barn timer"),
 * a warning at your cap, and an alert with a share button for rare sea creatures.
 * A sea creature is "yours" when its health tag appears right where you just reeled in.
 */
public final class FishingExtras {
    private static final Pattern TAG = Pattern.compile("^(?:\\[Lv[\\d,]+\\]\\s*)?(?:[^A-Za-z\\[]+\\s)?(.+?)\\s+[\\d.,]+[kKmMbB]?(?:/[\\d.,]+[kKmMbB]?)?\\s*[❤♥]");
    private static final List<String> RARE = List.of("Thunder", "Lord Jawbus", "Sea Emperor", "Water Hydra", "Great White Shark",
            "Grim Reaper", "Phantom Fisher", "Yeti", "Reindrake", "Plhlegblast", "Ragnarok", "Wiki Tiki", "Titanoboa", "Abyssal Miner");

    private record Creature(int tagId, String name, long since) {}

    private static final List<Creature> mine = new ArrayList<>();
    private static long castAt, reelAt;
    private static double reelX, reelY, reelZ;
    private static boolean capWarned;
    private static int tick;

    /** Rod used: casting starts the bobber timer; reeling in marks where a sea creature may appear. */
    public static void onCast() {
        Minecraft mc = Minecraft.getInstance();
        Object hook = mc.player == null ? null : Reflect.field(mc.player, "fishing");
        if (hook instanceof Entity bobber) {             // the bobber was out: this click reels in
            reelAt = System.currentTimeMillis();
            reelX = bobber.getX(); reelY = bobber.getY(); reelZ = bobber.getZ();
            castAt = 0;
        } else {
            castAt = System.currentTimeMillis();
        }
    }

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !Config.get().fishingExtras) return;
        if (++tick % (5 * Perf.slow()) != 0) return;
        long now = System.currentTimeMillis();
        List<Entity> tags = new ArrayList<>();
        Object all = Reflect.call(mc.level, new String[]{"entitiesForRendering", "getEntities"});
        if (all instanceof Iterable<?> it) for (Object o : it) if (o instanceof Entity e && e.hasCustomName() && e.getCustomName() != null) tags.add(e);
        // forget creatures that are gone (killed or despawned)
        mine.removeIf(c -> tags.stream().noneMatch(t -> t.getId() == c.tagId()));
        // a new health tag right where you reeled in, just now = your sea creature
        if (now - reelAt < 3000) {
            for (Entity t : tags) {
                if (mine.stream().anyMatch(c -> c.tagId() == t.getId())) continue;
                if (Math.abs(t.getX() - reelX) > 6 || Math.abs(t.getZ() - reelZ) > 6 || Math.abs(t.getY() - reelY) > 6) continue;
                Matcher m = TAG.matcher(Tracker.strip(t.getCustomName().getString()).trim());
                if (!m.find()) continue;
                String name = m.group(1).trim();
                mine.add(new Creature(t.getId(), name, now));
                if (RARE.stream().anyMatch(name::contains)) {
                    Chat.ping();
                    Tracker.say("§b§l[Fishing] RARE: §f§l" + name + "§b§l!");
                    Waypoints.offerShare(name);
                }
            }
        }
        int cap = Config.get().fishingCreatureCap;
        if (cap > 0 && mine.size() >= cap && !capWarned) { capWarned = true; Chat.ping(); Tracker.say("§c[Fishing] §f" + mine.size() + " sea creatures alive §7— time to kill them."); }
        if (mine.size() < cap) capWarned = false;
    }

    public static void addHudLines(Hud.Lines out) {
        if (!Config.get().fishingExtras) return;
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        if (castAt > 0 && mc.player != null && Reflect.field(mc.player, "fishing") instanceof Entity)
            out.add("§7Bobber: §f" + (now - castAt) / 1000 + "s");
        if (!mine.isEmpty()) {
            long oldest = mine.stream().mapToLong(Creature::since).min().orElse(now);
            int cap = Config.get().fishingCreatureCap;
            String col = cap > 0 && mine.size() >= cap ? "§c" : "§b";
            out.add("§7Sea creatures alive: " + col + mine.size() + (cap > 0 ? "§8/" + cap : "") + " §7oldest §f" + Fmt.clock(now - oldest));
        }
    }

    private FishingExtras() {}
}
