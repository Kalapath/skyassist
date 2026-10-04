package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;

/**
 * A search box under every inventory / chest / menu: type and every item whose name or description
 * contains the text is highlighted (the rest is dimmed).
 */
public final class InvSearch {
    private static String term = "";
    private static EditBox box;
    private static Object boxScreen;
    public static Boolean works, keysWork;

    public static boolean active() { return Config.get().inventorySearch && term != null && !term.isBlank(); }

    public static boolean matches(ItemStack stack) { return matches(stack, term.toLowerCase(Locale.ROOT).trim()); }
    private static final java.util.Set<Object> KEYS_HOOKED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private static boolean isContainer(Object screen) {
        return Reflect.call(screen, "getMenu") != Reflect.FAIL;
    }

    /** Adds the box when a menu opens (called from the screen-init event). */
    @SuppressWarnings("unchecked")
    static void attach(Object screenObj, int width, int height) {
        if (!Config.get().inventorySearch || !(screenObj instanceof Screen screen) || !isContainer(screen)) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            int w = 140, h = 16;
            // place it next to the menu so the item slots never cover it: below, else above, else to the right
            int left = intField(screen, "leftPos"), top = intField(screen, "topPos");
            int iw = intField(screen, "imageWidth"), ih = intField(screen, "imageHeight");
            int bx, by;
            if (left == Integer.MIN_VALUE || iw == Integer.MIN_VALUE) { bx = width / 2 - w / 2; by = 4; }
            else if (top + ih + 4 + h <= height - 2) { bx = left + (iw - w) / 2; by = top + ih + 4; }
            else if (top - h - 4 >= 2) { bx = left + (iw - w) / 2; by = top - h - 4; }
            else { bx = Math.min(width - w - 4, left + iw + 6); by = top + 4; }
            EditBox b = new EditBox(mc.font, bx, by, w, h, Component.literal("Search"));
            b.setMaxLength(40);
            b.setHint(Component.literal("§8Search items..."));
            b.setValue(term);
            b.setResponder(t -> term = t);
            boolean added = false;
            try {
                Class<?> screens = Class.forName("net.fabricmc.fabric.api.client.screen.v1.Screens");
                Object list = screens.getMethod("getButtons", Screen.class).invoke(null, screen);
                ((List<Object>) list).add(b);
                added = true;
            } catch (Throwable ignored) {}
            if (!added) {                                              // backup: the menu's own "add widget" method
                for (Class<?> k = screen.getClass(); k != null && !added; k = k.getSuperclass()) {
                    for (var m : k.getDeclaredMethods()) {
                        if (!m.getName().equals("addRenderableWidget") || m.getParameterCount() != 1) continue;
                        m.setAccessible(true);
                        m.invoke(screen, b);
                        added = true;
                        break;
                    }
                }
            }
            if (!added) throw new IllegalStateException("couldn't add the search box");
            box = b;
            boxScreen = screen;
            blockKeysWhileTyping(screen);               // menu events are fresh after every init, so attach each time
            works = true;
        } catch (Throwable t) {
            works = false;
        }
    }

    /**
     * While you type in the box, keys like E (close inventory) or 1-9 (move items) must not do their usual thing.
     * Fabric's key event changed between versions, so it's hooked up at runtime and the key is passed to the box.
     */
    private static void blockKeysWhileTyping(Screen screen) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents");
            for (Method m : events.getMethods()) {
                if (!m.getName().equals("allowKeyPress") || m.getParameterCount() != 1) continue;
                Object event = m.invoke(null, screen);
                if (!(m.getGenericReturnType() instanceof ParameterizedType pt) || !(pt.getActualTypeArguments()[0] instanceof Class<?> listener)) continue;
                Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (p, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) { case "hashCode" -> System.identityHashCode(p); case "equals" -> p == args[0]; default -> "SearchKeys"; };
                    }
                    if (box == null || !box.isFocused() || args == null || args.length < 2) return true;   // not typing: keys work normally
                    int key = keyCode(args[1]);
                    if (key == 256) { box.setFocused(false); return false; }   // Escape: leave the search box (menu stays open)
                    Object[] rest = java.util.Arrays.copyOfRange(args, 1, args.length);
                    Reflect.call(box, "keyPressed", rest);               // backspace, arrows, ctrl+a ... go to the box
                    return false;                                          // and nothing else reacts to the key
                });
                Events.register(event, proxy);
                keysWork = true;
                return;
            }
            keysWork = false;
        } catch (Throwable ignored) {
            keysWork = false;
        }
    }

    private static int keyCode(Object o) {
        if (o instanceof Integer i) return i;
        Object k = Reflect.call(o, new String[]{"key", "getKey", "keyCode"});
        return k instanceof Integer i ? i : -1;
    }

    private static int intField(Object o, String name) {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f.getInt(o); } catch (Exception ignored) {}
        }
        return Integer.MIN_VALUE;
    }

    /** Called after a menu is drawn: highlight matches, dim the rest. */
    static void draw(Object screen, Object g) {
        if (!active() || !isContainer(screen)) return;
        String q = term.toLowerCase(Locale.ROOT).trim();
        int left = intField(screen, "leftPos"), top = intField(screen, "topPos");
        if (left == Integer.MIN_VALUE) return;
        Object menu = Reflect.call(screen, "getMenu");
        Object slots = Reflect.field(menu, "slots");
        if (!(slots instanceof List<?> list)) return;
        for (Object slot : list) {
            Object st = Reflect.call(slot, "getItem");
            int sx = intField(slot, "x"), sy = intField(slot, "y");
            if (sx == Integer.MIN_VALUE || !(st instanceof ItemStack stack) || stack.isEmpty()) continue;
            int x = left + sx, y = top + sy;
            if (matches(stack, q)) {
                Reflect.call(g, "fill", x - 1, y - 1, x + 17, y + 1, 0xFF55FF55);          // thick bright green frame
                Reflect.call(g, "fill", x - 1, y + 15, x + 17, y + 17, 0xFF55FF55);
                Reflect.call(g, "fill", x - 1, y + 1, x + 1, y + 15, 0xFF55FF55);
                Reflect.call(g, "fill", x + 15, y + 1, x + 17, y + 15, 0xFF55FF55);
            } else {
                Reflect.call(g, "fill", x, y, x + 16, y + 16, 0xC8101010);
            }
        }
    }

    private static boolean matches(ItemStack stack, String q) {
        if (Tracker.strip(stack.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(q)) return true;
        if (!Config.get().inventorySearchLore) return false;
        for (String l : ItemIds.lore(stack)) if (l.toLowerCase(Locale.ROOT).contains(q)) return true;
        return false;
    }

    private InvSearch() {}
}
