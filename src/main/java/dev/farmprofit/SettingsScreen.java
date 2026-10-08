package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Settings menu (O key, /profit settings, or Mod Menu). Built automatically from Config:
 * anything marked @Setting appears in its tab, anything else under "Other", so new features show up by themselves.
 * Search box, per-setting and per-tab reset, and a Hidden items list.
 */
public final class SettingsScreen extends Screen {
    private static final String OTHER = "Other", HIDDEN = "Hidden items", RESULTS = "Search";
    private static final List<String> ORDER = List.of("General", "HUD", "Farming", "Mining", "Foraging", "Fishing",
            "Combat & Slayers", "Dungeons", "Kuudra", "Diana", "Bazaar flipping", "Items & areas", "Chat & sounds", "Keybinds & macros", "Timers", "Extras");
    private static String category;
    private static String query = "";
    private final Screen parent;
    private int page;
    private final List<Runnable> pending = new ArrayList<>();
    private static final Config DEFAULTS = new Config();

    public SettingsScreen() { this(null); }

    public SettingsScreen(Screen parent) {
        super(Component.literal("SkyAssist Settings"));
        this.parent = parent;
    }

    // ---------------- which fields go where ----------------

    private static Map<String, List<Field>> categories() {
        Map<String, List<Field>> map = new LinkedHashMap<>();
        List<Field> other = new ArrayList<>();
        for (Field f : Config.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isTransient(mod) || !Modifier.isPublic(mod)) continue;
            Setting s = f.getAnnotation(Setting.class);
            if (s != null && s.hidden()) continue;
            if (s == null) other.add(f);
            else map.computeIfAbsent(s.category(), k -> new ArrayList<>()).add(f);
        }
        Map<String, List<Field>> sorted = new LinkedHashMap<>();
        for (String c : ORDER) if (map.containsKey(c)) sorted.put(c, map.get(c));
        map.forEach(sorted::putIfAbsent);
        if (!other.isEmpty()) sorted.put(OTHER, other);
        sorted.put(HIDDEN, List.of());
        return sorted;
    }

    private static String label(Field f) {
        Setting s = f.getAnnotation(Setting.class);
        if (s != null) return s.label();
        String n = f.getName().replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(n.charAt(0)) + n.substring(1).toLowerCase(Locale.ROOT);
    }

    private static String desc(Field f) {
        Setting s = f.getAnnotation(Setting.class);
        return s != null && !s.desc().isEmpty() ? s.desc() : "Setting \"" + f.getName() + "\" in config.json";
    }

    private static String tabOf(Field f) {
        Setting s = f.getAnnotation(Setting.class);
        return s == null ? OTHER : s.category();
    }

    // ---------------- layout ----------------

    @Override
    protected void init() {
        pending.clear();
        Map<String, List<Field>> cats = categories();
        if (category == null || !(cats.containsKey(category) || category.equals(RESULTS))) category = cats.keySet().iterator().next();

        // search box
        EditBox search = new EditBox(font, 10, 8, 160, 18, Component.literal("Search"));
        search.setMaxLength(50);
        search.setValue(query);
        search.setHint(Component.literal("§8Search settings..."));
        search.setResponder(text -> {
            if (text.equals(query)) return;
            applyPending();
            query = text;
            category = text.isBlank() ? (category.equals(RESULTS) ? ORDER.get(0) : category) : RESULTS;
            page = 0;
            rebuildWidgets();
        });
        addRenderableWidget(search);
        setFocused(search);

        // tabs
        int x = 178, y = 8;
        for (String cat : cats.keySet()) {
            int w = font.width(cat) + 12;
            if (x + w > width - 10) { x = 10; y += 22; }
            final String c = cat;
            addRenderableWidget(Button.builder(Component.literal(cat.equals(category) ? "§e§l" + cat : cat), b -> {
                applyPending();
                category = c;
                query = "";
                page = 0;
                rebuildWidgets();
            }).bounds(x, y, w, 18).build());
            x += w + 3;
        }

        // which rows to show
        List<Field> fields;
        if (category.equals(RESULTS)) {
            fields = new ArrayList<>();
            String q = query.toLowerCase(Locale.ROOT);
            for (List<Field> list : cats.values()) for (Field f : list) {
                if (label(f).toLowerCase(Locale.ROOT).contains(q) || desc(f).toLowerCase(Locale.ROOT).contains(q)) fields.add(f);
            }
        } else {
            fields = cats.getOrDefault(category, List.of());
        }

        int top = y + 28, rowH = 24, bottom = height - 34;
        int perPage = Math.max(1, (bottom - top) / rowH);
        int labelW = 170, controlW = 170;
        int left = Math.max(10, width / 2 - (labelW + controlW + 34) / 2);

        if (category.equals(HIDDEN)) {
            List<String> hidden = Config.get().ignoredItems;
            int pages = Math.max(1, (hidden.size() + perPage - 1) / perPage);
            page = Math.max(0, Math.min(page, pages - 1));
            if (hidden.isEmpty()) addRenderableWidget(new StringWidget(left, top + 6, 340, 10,
                    Component.literal("§7Nothing hidden. Right-click an item on the HUD (chat open) to stop counting it."), font));
            int row = 0;
            for (int i = page * perPage; i < Math.min(hidden.size(), (page + 1) * perPage); i++, row++) {
                String item = hidden.get(i);
                int ry = top + row * rowH;
                addRenderableWidget(new StringWidget(left, ry + 6, labelW, 10, Component.literal(item), font));
                addRenderableWidget(Button.builder(Component.literal("§aCount again"), b -> {
                    Config.get().ignoredItems.remove(item);
                    Config.save();
                    rebuildWidgets();
                }).bounds(left + labelW + 10, ry, controlW, 20).build());
            }
            bottomBar(pages);
            return;
        }

        int pages = Math.max(1, (fields.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(page, pages - 1));
        if (fields.isEmpty()) addRenderableWidget(new StringWidget(left, top + 6, 300, 10, Component.literal("§7No settings match."), font));
        int row = 0;
        for (int i = page * perPage; i < Math.min(fields.size(), (page + 1) * perPage); i++, row++) {
            Field f = fields.get(i);
            int ry = top + row * rowH;
            String text = label(f) + (category.equals(RESULTS) ? " §8(" + tabOf(f) + ")" : "");
            StringWidget lbl = new StringWidget(left, ry + 6, labelW, 10, Component.literal(text), font);
            lbl.setTooltip(Tooltip.create(Component.literal(desc(f))));
            addRenderableWidget(lbl);
            addControl(f, left + labelW + 10, ry, controlW);
            Button reset = Button.builder(Component.literal("↺"), b -> { applyPending(); resetField(f); rebuildWidgets(); })
                    .bounds(left + labelW + controlW + 14, ry, 20, 20).build();
            reset.setTooltip(Tooltip.create(Component.literal("Reset to default: " + toText(get(f, DEFAULTS)))));
            addRenderableWidget(reset);
        }
        bottomBar(pages);
    }

    private void bottomBar(int pages) {
        int by = height - 28;
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("<"), b -> { applyPending(); page--; rebuildWidgets(); })
                    .bounds(width / 2 - 150, by, 24, 20).build()).active = page > 0;
            addRenderableWidget(new StringWidget(width / 2 - 122, by + 6, 40, 10, Component.literal("§7" + (page + 1) + "/" + pages), font));
            addRenderableWidget(Button.builder(Component.literal(">"), b -> { applyPending(); page++; rebuildWidgets(); })
                    .bounds(width / 2 - 80, by, 24, 20).build()).active = page < pages - 1;
        }
        if (!category.equals(RESULTS) && !category.equals(HIDDEN)) {
            Button resetTab = Button.builder(Component.literal("Reset tab"), b -> {
                applyPending();
                for (Field f : categories().getOrDefault(category, List.of())) resetField(f);
                rebuildWidgets();
            }).bounds(width / 2 - 50, by, 70, 20).build();
            resetTab.setTooltip(Tooltip.create(Component.literal("Puts every setting in this tab back to its default.")));
            addRenderableWidget(resetTab);
        }
        if (category.equals("HUD")) {
            addRenderableWidget(Button.builder(Component.literal("Edit HUD layout"), b -> {
                onClose();
                GuiEditor.open();
            }).bounds(10, by, 100, 20).build()).setTooltip(Tooltip.create(Component.literal(
                    "Move, resize and hide each panel. Same as /profit gui.")));
        }
        addRenderableWidget(Button.builder(Component.literal("Commands"), b -> {
            applyPending();
            Compat.setScreen(Minecraft.getInstance(), Commands.screen(this));
        }).bounds(width - 90, by, 80, 20).build()).setTooltip(Tooltip.create(Component.literal("Every command with a short explanation.")));
        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose()).bounds(width / 2 + 30, by, 80, 20).build());
    }

    /** The right control for the field's type. */
    private void addControl(Field f, int x, int y, int w) {
        Setting s = f.getAnnotation(Setting.class);
        Tooltip tip = Tooltip.create(Component.literal(desc(f)));
        Config cfg = Config.get();
        try {
            Class<?> t = f.getType();
            if (t == boolean.class) {
                Button b = Button.builder(onOff(f.getBoolean(cfg)), btn -> {
                    try {
                        f.setBoolean(cfg, !f.getBoolean(cfg));
                        btn.setMessage(onOff(f.getBoolean(cfg)));
                        Config.save();
                    } catch (Exception ignored) {}
                }).bounds(x, y, w, 20).build();
                b.setTooltip(tip);
                addRenderableWidget(b);
                return;
            }
            if (t == String.class && s != null && s.options().length > 0) {
                Button b = Button.builder(Component.literal(String.valueOf(f.get(cfg))), btn -> {
                    try {
                        List<String> opts = Arrays.asList(s.options());
                        int i = opts.indexOf(String.valueOf(f.get(cfg)));
                        String next = opts.get((i + 1) % opts.size());
                        f.set(cfg, next);
                        btn.setMessage(Component.literal(next));
                        Config.save();
                    } catch (Exception ignored) {}
                }).bounds(x, y, w, 20).build();
                b.setTooltip(tip);
                addRenderableWidget(b);
                return;
            }
            EditBox box = new EditBox(font, x, y, w, 20, Component.literal(label(f)));
            box.setMaxLength(2000);
            box.setValue(toText(f.get(cfg)));
            box.setTooltip(tip);
            addRenderableWidget(box);
            pending.add(() -> fromText(f, box.getValue()));
        } catch (Exception e) {
            addRenderableWidget(new StringWidget(x, y + 6, w, 10, Component.literal("§8(edit in config.json)"), font));
        }
    }

    private static Component onOff(boolean v) { return Component.literal(v ? "§aON" : "§cOFF"); }

    // ---------------- reset ----------------

    private static Object get(Field f, Config c) {
        try { return f.get(c); } catch (Exception e) { return null; }
    }

    private static void resetField(Field f) {
        try {
            Object def = f.get(DEFAULTS);
            if (def instanceof List<?> || def instanceof Map<?, ?>) {   // copy so the defaults stay untouched
                def = Config.GSON.fromJson(Config.GSON.toJson(def), f.getGenericType());
            }
            f.set(Config.get(), def);
            Config.save();
        } catch (Exception ignored) {}
    }

    // ---------------- value <-> text ----------------

    private static String toText(Object v) {
        if (v == null) return "";
        if (v instanceof Double d) return d == Math.rint(d) && Math.abs(d) < 1e15 ? String.valueOf(d.longValue()) : String.valueOf(d);
        if (v instanceof List<?> l) return String.join(", ", l.stream().map(String::valueOf).toList());
        if (v instanceof Map<?, ?> m) {
            List<String> parts = new ArrayList<>();
            m.forEach((k, val) -> parts.add(k + "=" + val));
            return String.join(", ", parts);
        }
        if (v instanceof Boolean b) return b ? "ON" : "OFF";
        return String.valueOf(v);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void fromText(Field f, String text) {
        Config cfg = Config.get();
        Setting s = f.getAnnotation(Setting.class);
        try {
            Class<?> t = f.getType();
            if (t == int.class || t == long.class || t == double.class || t == float.class) {
                double v = FlipsCommand.parseAmount(text);
                if (Double.isNaN(v)) return;
                if (s != null) v = Math.max(s.min(), Math.min(s.max(), v));
                if (t == int.class) f.setInt(cfg, (int) Math.round(v));
                else if (t == long.class) f.setLong(cfg, Math.round(v));
                else if (t == float.class) f.setFloat(cfg, (float) v);
                else f.setDouble(cfg, v);
            } else if (t == String.class) {
                f.set(cfg, text.trim());
            } else if (List.class.isAssignableFrom(t)) {
                List<String> list = new ArrayList<>();
                for (String part : text.split(",")) if (!part.isBlank()) list.add(part.trim());
                f.set(cfg, list);
            } else if (Map.class.isAssignableFrom(t)) {
                Map map = new java.util.HashMap<String, String>();
                for (String part : text.split(",")) {
                    int eq = part.indexOf('=');
                    if (eq > 0) map.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
                }
                f.set(cfg, map);
            }
        } catch (Exception ignored) {}
    }

    private void applyPending() {
        for (Runnable r : pending) r.run();
        pending.clear();
        Config.save();
    }

    @Override
    public void onClose() {
        applyPending();
        Compat.setScreen(Minecraft.getInstance(), parent);
    }

    // ---------------- opening ----------------

    private static boolean openNextTick;

    public static void requestOpen() { openNextTick = true; }

    public static void selectTab(String tab) { category = tab; query = ""; }

    public static void requestOpen(String tab) { category = tab; query = ""; openNextTick = true; }

    static void tick(Minecraft mc) {
        if (openNextTick && Compat.screen(mc) == null) {
            openNextTick = false;
            Compat.setScreen(mc, new SettingsScreen());
        }
    }
}
