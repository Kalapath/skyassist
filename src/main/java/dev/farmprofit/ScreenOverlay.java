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

    /** Things that go BEHIND the items (colored slot backgrounds): rarity, search match / dim, terminal highlights. */
    private static void drawBehind(Object screen, Object g) {
        Perf.run("Rarity colors", () -> RarityBg.drawBehind(screen, g));
        Perf.run("Inventory search", () -> InvSearch.drawBehind(screen, g));
        Perf.run("Terminal solvers", () -> Terminals.draw(screen, g));
    }

    static void register(Object screen) {
        // after the menu background, before items and tooltips: colors land under the items, tooltips stay on top
        behindWorks = hook(screen, new String[]{"afterBackground", "afterExtractBackground", "afterRenderBackground"}, ScreenOverlay::drawBehind);
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
                        // no new layer here: anything in a new layer would cover the tooltip
                        if (behindWorks != Boolean.TRUE) drawBehind(args[0], args[1]);       // fallback if the background event is missing
                        Perf.run("Inventory search", () -> InvSearch.drawFront(args[0], args[1]));   // green frames around matches
                        Perf.run("Item labels", () -> ItemLabels.draw(args[0], args[1]));            // text always draws above items
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
