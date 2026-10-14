package dev.farmprofit;

/** Glacite Mineshaft alert: ding, a big HUD line for a few seconds, and a share button. */
public final class Mineshafts {
    private static long foundAt;

    public static boolean onChat(String plain) {
        if (!Config.get().mineshaftAlert) return false;
        if (plain.contains("You found a Glacite Mineshaft portal") || plain.contains("Glacite Mineshaft portal")) {
            foundAt = System.currentTimeMillis();
            Alerts.notify("mineshaft", "§b§l⛏ Mineshaft found!", "§7go in before it closes", null);
            Waypoints.offerShare("Glacite Mineshaft");
            Debug.saw("mineshaft");
            return false;                                 // keep Hypixel's own message
        }
        return false;
    }

    public static void addHudLines(Hud.Lines out) {
        if (System.currentTimeMillis() - foundAt < 6000) out.add("§b§l⛏ MINESHAFT FOUND! §7go in before it closes");
    }

    private Mineshafts() {}
}
