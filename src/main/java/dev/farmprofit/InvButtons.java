package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Quick-command buttons beside your inventory (Pets, Wardrobe, Ender Chest...). Each button sends its command when YOU click it,
 * exactly like pressing a macro key; nothing is sent automatically. Off by default. Set up in Settings → Items → Inventory.
 */
public final class InvButtons {
    /** null = not tried yet, true = buttons were added to a menu, false = adding failed. Shown on the Status page. */
    public static Boolean works;
    private static final int MAX = 8, GAP = 2, BUTTON_H = 14;
    private static long lastClick;

    public record Entry(String name, String command) {}

    /** "Pets=/pets; Wardrobe=/wardrobe" -> entries (up to 8; entries without a / command are skipped). */
    static List<Entry> parse(String list) {
        List<Entry> out = new ArrayList<>();
        if (list == null) return out;
        for (String part : list.split(";")) {
            int eq = part.indexOf('=');
            if (eq < 1) continue;
            String name = part.substring(0, eq).trim(), cmd = part.substring(eq + 1).trim();
            if (name.isEmpty() || !cmd.startsWith("/") || cmd.length() < 2) continue;
            out.add(new Entry(name.length() > 14 ? name.substring(0, 14) : name, cmd));
            if (out.size() >= MAX) break;
        }
        return out;
    }

    /** Called when a menu opens (screen-init event). */
    static void attach(Object screenObj, int width, int height) {
        Config c = Config.get();
        if (!c.invButtons || !(screenObj instanceof Screen screen)) return;
        String simple = screen.getClass().getSimpleName();
        boolean playerInv = simple.equals("InventoryScreen");
        if (!playerInv && !("all menus".equals(c.invButtonsWhere) && Reflect.call(screen, "getMenu") != Reflect.FAIL)) return;
        List<Entry> entries = parse(c.invButtonList);
        if (entries.isEmpty()) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            int left = InvSearch.intField(screen, "leftPos"), top = InvSearch.intField(screen, "topPos");
            int iw = InvSearch.intField(screen, "imageWidth");
            if (left == Integer.MIN_VALUE || top == Integer.MIN_VALUE || iw == Integer.MIN_VALUE) { works = false; return; }
            int w = 40;
            for (Entry e : entries) w = Math.max(w, mc.font.width(e.name()) + 10);
            w = Math.min(w, 92);
            int total = entries.size() * (BUTTON_H + GAP);
            int x;
            if (left - w - 4 >= 2) x = left - w - 4;                        // left of the menu
            else if (left + iw + 4 + w <= width - 2) x = left + iw + 4;    // else right of it
            else return;                                                    // no room on this screen size
            int y = Math.max(2, Math.min(top, height - total - 2));
            for (Entry e : entries) {
                Button b = Button.builder(Component.literal(e.name()), btn -> click(e)).bounds(x, y, w, BUTTON_H).build();
                b.setTooltip(Tooltip.create(Component.literal(e.command())));
                if (!InvSearch.addWidget(screen, b)) throw new IllegalStateException("couldn't add a button to this menu");
                y += BUTTON_H + GAP;
            }
            works = true;
        } catch (Throwable t) {
            works = false;
            Safe.report("Inventory buttons", t);
        }
    }

    /** You clicked a button: send its command (at most one every half second). */
    private static void click(Entry e) {
        long now = System.currentTimeMillis();
        if (now - lastClick < 500) return;
        lastClick = now;
        Object conn = Minecraft.getInstance().getConnection();
        if (Reflect.call(conn, "sendCommand", e.command().substring(1)) == Reflect.FAIL) Tracker.say("§6[SkyAssist] §7Type §f" + e.command());
    }

    private InvButtons() {}
}
