package dev.farmprofit;

import com.google.gson.reflect.TypeToken;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Storage overview (/itemsearch): remembers the contents of every Ender Chest page and backpack you open,
 * and lists all of it in one searchable menu with where each item is.
 */
public final class Storage {
    private static final Path FILE = Config.DIR.resolve("storage.json");
    /** container name ("Ender Chest (2/9)", "Jumbo Backpack (Slot #3)") -> item name -> count */
    private static Map<String, Map<String, Integer>> data;

    private static Map<String, Map<String, Integer>> data() {
        if (data == null) {
            try {
                if (Files.exists(FILE)) data = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<LinkedHashMap<String, Map<String, Integer>>>() {}.getType());
            } catch (Exception ignored) {}
            if (data == null) data = new LinkedHashMap<>();
        }
        return data;
    }

    private static boolean isStorage(String title) {
        return title.startsWith("Ender Chest") || title.contains("Backpack") || title.startsWith("Personal Vault");
    }

    /** Called when a menu opens. */
    static void scanMenu(String title, List<ItemStack> items) {
        if (!Config.get().storageOverview || !isStorage(title)) return;
        Map<String, Integer> contents = new TreeMap<>();
        for (ItemStack is : items) {
            String n = Tracker.strip(is.getHoverName().getString()).trim();
            if (n.isEmpty() || n.equals("Go Back") || n.equals("Close") || n.startsWith("Next Page") || n.startsWith("Previous Page")) continue;
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(is.getItem()).getPath();
            if (path.endsWith("glass_pane")) continue;                               // menu decoration
            contents.merge(n, is.getCount(), Integer::sum);
        }
        Map<String, Integer> old = data().put(title, contents);
        if (!contents.equals(old)) {
            try { Files.createDirectories(Config.DIR); Config.write(FILE, Config.GSON.toJson(data)); } catch (Exception ignored) {}
        }
    }

    public static Screen screen(Screen parent) {
        return new MenuScreen("Storage overview", List.of(
                new Tab("All items", () -> {
                    Map<String, Integer> total = new TreeMap<>();
                    Map<String, List<String>> where = new LinkedHashMap<>();
                    data().forEach((box, items) -> items.forEach((name, n) -> {
                        total.merge(name, n, Integer::sum);
                        where.computeIfAbsent(name, k -> new ArrayList<>()).add(box + " ×" + n);
                    }));
                    List<Row> rows = new ArrayList<>();
                    for (var e : total.entrySet()) {
                        List<String> w = where.get(e.getKey());
                        rows.add(new Row(new String[]{"§f" + e.getKey(), "§7" + Fmt.num(e.getValue()), "§8" + String.join(", ", w)},
                                "§f" + e.getKey() + "\n§7" + String.join("\n§7", w), List.of()));
                    }
                    return new Page(new String[]{"Item", "Total", "Where"}, new int[]{170, 60, 220}, rows, List.of(),
                            List.of("§8Open your Ender Chest pages and backpacks once and they're remembered here. Search above."));
                }),
                new Tab("By container", () -> {
                    List<Row> rows = new ArrayList<>();
                    data().forEach((box, items) -> {
                        int count = items.values().stream().mapToInt(Integer::intValue).sum();
                        rows.add(new Row(new String[]{"§e" + box, "§7" + items.size() + " kinds, " + Fmt.num(count) + " items"},
                                "§e" + box + "\n§7" + String.join("\n§7", items.entrySet().stream().map(x -> x.getValue() + "x " + x.getKey()).toList()), List.of()));
                    });
                    return new Page(new String[]{"Container", ""}, new int[]{200, 200}, rows, List.of(), List.of("§8Hover a container to see what's in it."));
                })
        ), 0, parent).searchable();
    }

    private Storage() {}
}
