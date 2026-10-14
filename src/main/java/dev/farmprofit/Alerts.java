package dev.farmprofit;

import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Big on-screen alerts: large text in the middle of the screen for a few seconds (plus a ding and a chat line) for things you
 * shouldn't miss: pickaxe ability ready, a price alert, a full inventory. Everything in the mod that wants your attention goes
 * through {@link #notify}, so the look and the two switches (Settings → Alerts) are in one place.
 * Display only: an alert never does anything in the game.
 */
public final class Alerts {
    private record Shown(String title, String sub, long start) {}

    private static final long LIFE_MS = 3500, FADE_MS = 600;
    private static final List<Shown> SHOWN = new ArrayList<>();
    private static final Map<String, Long> LAST = new HashMap<>();

    // ---------- showing an alert ----------

    /**
     * @param key   same key within two seconds is ignored (so a repeated message can't flood you)
     * @param title big text, e.g. "§a§lAbility ready!"
     * @param sub   smaller text under it, or null
     * @param chat  a chat line to print as well, or null
     */
    static synchronized void notify(String key, String title, String sub, Object chat) { notify(key, title, sub, chat, true); }

    /** Same, with `ding` false when the caller already rang a sound of its own (the ding is still subject to the global switch). */
    static synchronized void notify(String key, String title, String sub, Object chat, boolean ding) {
        long now = System.currentTimeMillis();
        Long prev = LAST.get(key);
        if (prev != null && now - prev < 2000) return;
        LAST.put(key, now);
        Config c = Config.get();
        if (chat instanceof net.minecraft.network.chat.Component comp) Tracker.say(comp);
        else if (chat instanceof String s) Tracker.say(s);
        if (c.alertScreen) {
            SHOWN.add(0, new Shown(title, sub, now));
            while (SHOWN.size() > 3) SHOWN.remove(SHOWN.size() - 1);
        }
        if (ding && c.alertSound) Chat.ping();
    }

    /** Settings → Alerts → Test alert: shows what an alert looks and sounds like. */
    static void test() {
        notify("test", "§a§lTest alert", "§7this is how alerts look and sound",
                "§6[SkyAssist] §7Test alert: this is what an alert's chat line looks like.");
    }

    /** Called by the HUD every frame. */
    static synchronized void draw(Object graphics) { draw(graphics, false); }

    /** Called on top of the settings menu (which blurs whatever is behind it), so the Test alert button can be seen working. */
    static synchronized void drawOnMenu(Object graphics) { draw(graphics, true); }

    private static void draw(Object graphics, boolean onMenu) {
        if (SHOWN.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (!onMenu && ScreenOverlay.works == Boolean.TRUE && Compat.screen(mc) instanceof SettingsScreen) return;     // drawn over the menu instead
        long now = System.currentTimeMillis();
        SHOWN.removeIf(a -> now - a.start() > LIFE_MS);
        if (SHOWN.isEmpty() || (!onMenu && Compat.hudHidden(mc))) return;
        Object win = Reflect.call(mc, "getWindow");
        double w = Reflect.num(Reflect.call(win, "getGuiScaledWidth"), 0), h = Reflect.num(Reflect.call(win, "getGuiScaledHeight"), 0);
        if (w <= 0 || h <= 0) return;
        Object pose = Reflect.call(graphics, "pose");
        float y = (float) (h / 2 - 62);
        for (Shown a : SHOWN) {
            long left = LIFE_MS - (now - a.start());
            int alpha = left >= FADE_MS ? 255 : (int) (255 * Math.max(0, left) / (double) FADE_MS);
            if (alpha < 12) continue;                        // (very faint text would be drawn solid by the game)
            int color = (alpha << 24) | 0xFFFFFF;
            y += drawLine(mc, graphics, pose, a.title(), (float) (w / 2), y, 3.0f, color);
            if (a.sub() != null) y += drawLine(mc, graphics, pose, a.sub(), (float) (w / 2), y, 1.4f, color);
            y += 6;
        }
    }

    /** One centred line at a scale; returns the height it took. Unscaled if this version can't scale. */
    private static float drawLine(Minecraft mc, Object graphics, Object pose, String text, float cx, float y, float scale, int color) {
        boolean pushed = Reflect.call(pose, "pushMatrix") != Reflect.FAIL;
        boolean scaled = false;
        if (pushed) {
            scaled = Reflect.call(pose, "translate", cx, y) != Reflect.FAIL && Reflect.call(pose, "scale", scale, scale) != Reflect.FAIL;
            if (!scaled) { Reflect.call(pose, "popMatrix"); pushed = false; }     // couldn't scale: undo and draw it small
        }
        try {
            int tw = mc.font.width(text);
            if (scaled) Reflect.call(graphics, "text", mc.font, text, -tw / 2, 0, color, true);
            else Reflect.call(graphics, "text", mc.font, text, Math.round(cx - tw / 2f), Math.round(y), color, true);
        } finally {
            if (pushed) Reflect.call(pose, "popMatrix");
        }
        return 9 * (scaled ? scale : 1f) + 2;
    }

    // ---------- full inventory warning ----------

    private static int ticks;
    private static boolean full;
    private static long lastWarn;

    /** Every tick; checks about twice a second. */
    static void tick(Minecraft mc) {
        Config c = Config.get();
        if (!c.invFullWarn || mc.player == null) { full = false; return; }
        if (++ticks % 10 != 0) return;
        if (Compat.screen(mc) != null || !inSkyblock()) return;       // not while a menu is open or outside SkyBlock
        int free = freeSlots(mc);
        if (free < 0) return;
        boolean low = free <= c.invFullSlots;
        long now = System.currentTimeMillis();
        if (low && (!full || now - lastWarn > 120_000)) {
            full = true;
            lastWarn = now;
            String what = free == 0 ? "Inventory full!" : "Inventory almost full";
            notify("inv-full", "§c§l" + what, "§7" + free + " free slot" + (free == 1 ? "" : "s"), "§6[SkyAssist] §c" + what + " §7(" + free + " free slot" + (free == 1 ? "" : "s") + ")");
        } else if (!low && free > c.invFullSlots + 1) {
            full = false;
        }
    }

    /** Empty slots among the 36 main inventory slots, or -1 if they can't be read. */
    static int freeSlots(Minecraft mc) {
        try {
            var inv = mc.player.getInventory();
            int free = 0;
            for (int i = 0; i < 36; i++) if (inv.getItem(i).isEmpty()) free++;
            return free;
        } catch (Throwable t) {
            return -1;
        }
    }

    /** True while the sidebar looks like SkyBlock's (purse / piggy / location line). */
    static boolean inSkyblock() {
        for (String l : Tracker.sidebarLines) {
            if (l.startsWith("Purse:") || l.startsWith("Piggy:") || l.contains("⏣") || l.contains("Bits:")) return true;
        }
        return false;
    }

    private Alerts() {}
}
