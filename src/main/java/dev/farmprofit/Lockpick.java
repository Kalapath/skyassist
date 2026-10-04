package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Locale;

/**
 * Treasure chest lockpick helper (display only, you still aim): Hypixel shows "crit" particles on the chest;
 * this marks the spot with a green square on your screen and tells you on the HUD which way to move your aim.
 * Wiki tip built in: the hit spot is a pixel or two above the particles.
 */
public final class Lockpick {
    public static volatile boolean hooked;            // the particle hook is working
    private static volatile double tx, ty, tz;
    private static volatile long seenAt;
    private static int tick;

    /** Called from the mixin for every particle packet (on the game thread). */
    public static void onPacket(Object packet) {
        hooked = true;
        try {
            Object particle = Reflect.call(packet, new String[]{"getParticle", "particle"});
            Object type = Reflect.call(particle, "getType");
            if (!(type instanceof net.minecraft.core.particles.ParticleType<?> pt)) return;
            String key = BuiltInRegistries.PARTICLE_TYPE.getKey(pt).getPath();
            double x = Reflect.num(Reflect.call(packet, new String[]{"getX", "x"}), Double.NaN);
            double y = Reflect.num(Reflect.call(packet, new String[]{"getY", "y"}), Double.NaN);
            double z = Reflect.num(Reflect.call(packet, new String[]{"getZ", "z"}), Double.NaN);
            if (Double.isNaN(x)) return;
            DianaBurrows.onParticle(key, x, y, z);
            if (!Config.get().lockpickHelper || !Tracker.MINING.equals(Tracker.area)) return;
            if (!key.equals("crit")) return;
            if (!PowderChests.nearChest(x, y, z)) return;
            tx = x; ty = y + Config.get().lockpickOffset / 16.0; tz = z;            // a pixel or two above the particles
            seenAt = System.currentTimeMillis();
        } catch (Throwable ignored) {}
    }

    private static boolean active() { return System.currentTimeMillis() - seenAt < 1500; }

    public static void tick(Minecraft mc) { }

    /**
     * Where the lockpick spot is on your screen (GUI pixels), or null if not active / behind you.
     * Projects the 3D point through your camera (yaw, pitch, field of view).
     */
    public static int[] screenPos(Minecraft mc) {
        if (!Config.get().lockpickHelper || !active() || mc.player == null) return null;
        double dx = tx - mc.player.getX(), dy = ty - mc.player.getEyeY(), dz = tz - mc.player.getZ();
        double yaw = Math.toRadians(mc.player.getYRot()), pitch = Math.toRadians(mc.player.getXRot());
        double fx = -Math.sin(yaw) * Math.cos(pitch), fy = -Math.sin(pitch), fz = Math.cos(yaw) * Math.cos(pitch);   // forward
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);                                                       // right
        double ux = ry * fz - rz * fy, uy = rz * fx - rx * fz, uz = rx * fy - ry * fx;                                  // up = right × forward
        double cz = dx * fx + dy * fy + dz * fz;
        if (cz < 0.1) return null;                                                                                       // behind you
        double cx = dx * rx + dy * ry + dz * rz, cy = dx * ux + dy * uy + dz * uz;
        double fov = 70;
        Object opt = Reflect.call(Reflect.field(mc, "options"), "fov");
        Object v = Reflect.call(opt, "get");
        if (v instanceof Number n) fov = n.doubleValue();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        double focal = (h / 2.0) / Math.tan(Math.toRadians(fov) / 2);
        return new int[]{(int) Math.round(w / 2.0 + cx / cz * focal), (int) Math.round(h / 2.0 - cy / cz * focal)};
    }

    /** HUD: where to move your aim (or "on target"). */
    public static void addHudLines(Hud.Lines out) {
        if (!Config.get().lockpickHelper) return;
        Minecraft mc = Minecraft.getInstance();
        if (!active() || mc.player == null) return;
        double dx = tx - mc.player.getX(), dy = ty - mc.player.getEyeY(), dz = tz - mc.player.getZ();
        double yawTo = Math.toDegrees(Math.atan2(-dx, dz));
        double pitchTo = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        double dYaw = ((yawTo - mc.player.getYRot()) % 360 + 540) % 360 - 180;
        double dPitch = pitchTo - mc.player.getXRot();
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double tol = Math.toDegrees(Math.atan2(0.07, Math.max(0.5, dist)));         // ~1 pixel of the chest
        if (Math.abs(dYaw) <= tol && Math.abs(dPitch) <= tol) { out.add("§a§lLockpick ✔ ON TARGET"); return; }
        StringBuilder dir = new StringBuilder();
        if (dPitch < -tol) dir.append("↑ ");
        if (dPitch > tol) dir.append("↓ ");
        if (dYaw < -tol) dir.append("← ");
        if (dYaw > tol) dir.append("→ ");
        out.add("§a§lLockpick §faim at the green square §e" + dir.toString().trim()
                + String.format(Locale.US, " §8(%.1f°)", Math.max(Math.abs(dYaw), Math.abs(dPitch))));
    }

    private Lockpick() {}
}
