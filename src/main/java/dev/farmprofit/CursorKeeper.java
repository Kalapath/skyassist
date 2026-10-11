package dev.farmprofit;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;

/**
 * Keeps the mouse pointer where it was when one SkyBlock menu is replaced by the next (SkyBlock Menu → Pets and so on).
 * Hypixel closes a menu and opens another one, and Minecraft puts the pointer back in the middle of the screen in between;
 * this puts it back where you had it. Only moves the pointer: it never clicks or presses anything.
 */
public final class CursorKeeper {
    /** null = never needed, true = moved the pointer, false = couldn't. Shown on the Status page. */
    public static Boolean works;
    private static Object lastScreen;
    private static double x = -1, y;
    private static long lastTick, closedAt;
    private static boolean wasMenu;

    static void tick(Minecraft mc) {
        if (!Config.get().keepCursor) { lastScreen = null; wasMenu = false; return; }
        Object screen = Compat.screen(mc);
        boolean menu = screen != null && Reflect.call(screen, "getMenu") != Reflect.FAIL;
        long now = System.currentTimeMillis();
        long handle = handle(mc);
        if (menu && handle != 0L) {
            boolean replaced = lastScreen != null && screen != lastScreen && now - lastTick < 400;          // menu → menu in one go
            boolean reopened = !wasMenu && closedAt > 0 && now - closedAt < 600 && lastScreen != screen;     // closed, then another opened
            if ((replaced || reopened) && x >= 0) restore(mc, handle);
            else remember(handle);
            lastScreen = screen;
            lastTick = now;
        } else if (wasMenu) {
            closedAt = now;                                      // the pointer position stays as it was last remembered
        }
        wasMenu = menu;
    }

    private static long handle(Minecraft mc) {
        Object win = Reflect.call(mc, "getWindow");
        Object h = Reflect.call(win, new String[]{"handle", "getWindow", "getHandle"});
        return h instanceof Long l ? l : 0L;
    }

    private static void remember(long handle) {
        try {
            double[] cx = new double[1], cy = new double[1];
            GLFW.glfwGetCursorPos(handle, cx, cy);
            x = cx[0];
            y = cy[0];
        } catch (Throwable ignored) {}
    }

    private static void restore(Minecraft mc, long handle) {
        try {
            GLFW.glfwSetCursorPos(handle, x, y);
            Object mh = Reflect.field(mc, "mouseHandler");                  // Minecraft's own copy of the position, so the menu sees it at once
            if (mh != null && mh != Reflect.FAIL) {
                setDouble(mh, "xpos", x);
                setDouble(mh, "ypos", y);
            }
            works = true;
        } catch (Throwable t) {
            works = false;
        }
    }

    private static void setDouble(Object target, String name, double value) {
        try {
            Field f = target.getClass().getDeclaredField(name);
            f.setAccessible(true);
            f.setDouble(target, value);
        } catch (Throwable ignored) {}
    }

    private CursorKeeper() {}
}
