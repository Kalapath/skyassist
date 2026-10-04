package dev.farmprofit;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;

/**
 * Draws on top of menus (used by the terminal solvers). Fabric's "after a screen is drawn" event got renamed
 * in 26.x, so it's looked up at runtime: if it can't be found, the overlay simply doesn't show.
 */
final class ScreenOverlay {
    static Boolean works;
    /** True if rarity colors could be drawn behind items (afterBackground event found). */
    static Boolean behindWorks;

    static void register(Object screen) {
        behindWorks = null;
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents");
            for (Method m : events.getMethods()) {
                String n = m.getName();
                if (m.getParameterCount() != 1 || !(n.equals("afterRender") || n.equals("afterExtract") || n.equals("afterExtractRenderState"))) continue;
                Object event = m.invoke(null, screen);
                Class<?> listener = listenerType(m.getGenericReturnType());
                if (listener == null) continue;
                Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (p, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "hashCode" -> System.identityHashCode(p);
                            case "equals" -> p == args[0];
                            default -> "SkyAssistOverlay";
                        };
                    }
                    if (args != null && args.length >= 2) {
                        final int mx = args.length > 2 && args[2] instanceof Integer i ? i : -999, my = args.length > 3 && args[3] instanceof Integer j ? j : -999;
                        // new drawing layer, so everything below lands on top of the menu's own items
                        Reflect.call(args[1], new String[]{"nextStratum", "createNewRootLayer"});
                        Perf.run("Rarity colors", () -> RarityBg.drawMenu(args[0], args[1], mx, my));
                        Perf.run("Item labels", () -> ItemLabels.draw(args[0], args[1]));
                        Perf.run("Terminal solvers", () -> Terminals.draw(args[0], args[1]));
                        Perf.run("Inventory search", () -> InvSearch.draw(args[0], args[1]));
                    }
                    return null;
                });
                Events.register(event, proxy);
                works = true;
                return;
            }
            works = false;
        } catch (Throwable t) {
            works = false;
        }
    }

    /** Registers a (screen, graphics, ...) listener on the first event with one of these names. */
    private static boolean hook(Object screen, String[] names, java.util.function.BiConsumer<Object, Object> run) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.client.screen.v1.ScreenEvents");
            for (Method m : events.getMethods()) {
                if (m.getParameterCount() != 1 || !java.util.Arrays.asList(names).contains(m.getName())) continue;
                Object event = m.invoke(null, screen);
                Class<?> listener = listenerType(m.getGenericReturnType());
                if (listener == null) continue;
                Object proxy = Proxy.newProxyInstance(listener.getClassLoader(), new Class<?>[]{listener}, (p, method, args) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) { case "hashCode" -> System.identityHashCode(p); case "equals" -> p == args[0]; default -> "SkyAssist"; };
                    }
                    if (args != null && args.length >= 2) try { run.accept(args[0], args[1]); } catch (Throwable ignored) {}
                    return null;
                });
                Events.register(event, proxy);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static Class<?> listenerType(Type t) {
        if (t instanceof ParameterizedType pt && pt.getActualTypeArguments().length == 1 && pt.getActualTypeArguments()[0] instanceof Class<?> c) return c;
        return null;
    }

    private ScreenOverlay() {}
}
