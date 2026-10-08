package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/** /talismans: cheapest Magical Power you don't have, plus the ones you can't buy (grouped by upgrade chain). Searchable. */
public final class TalismansScreen {
    private static int count = 10;
    private static final String[] RARITIES = {"All", "COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL"};
    private static int rarity;

    private static String color(String r) {
        return switch (r) {
            case "UNCOMMON" -> "§a";
            case "RARE" -> "§9";
            case "EPIC" -> "§5";
            case "LEGENDARY" -> "§6";
            case "MYTHIC" -> "§d";
            case "SPECIAL", "VERY SPECIAL" -> "§c";
            default -> "§f";
        };
    }

    private static boolean rarityOk(String r) {
        String want = RARITIES[rarity];
        return want.equals("All") || r.equals(want) || (want.equals("SPECIAL") && r.contains("SPECIAL"));
    }

    public static Screen screen(Screen parent) {
        final MenuScreen[] ref = new MenuScreen[1];
        ref[0] = new MenuScreen("Next talismans", List.of(
                new Tab("Buy next", () -> buyPage(ref)),
                new Tab("Other ways (quests, drops...)", () -> otherPage(ref))
        ), 0, parent).searchable();
        return ref[0];
    }

    private static List<Action> top(MenuScreen[] ref) {
        Config cfg = Config.get();
        return List.of(
                new Action("Show " + count, "How many to list: 10, 20 or 50.", () -> { count = count == 10 ? 20 : count == 20 ? 50 : 10; ref[0].refresh(); }),
                new Action("Rarity: " + (rarity == 0 ? "All" : color(RARITIES[rarity]) + RARITIES[rarity]), "Only show one rarity (click to change).",
                        () -> { rarity = (rarity + 1) % RARITIES.length; ref[0].refresh(); }),
                new Action("Crafting: " + (cfg.talismanUseCraft ? "§aON" : "§cOFF"), "Also consider crafting / upgrading when cheaper than the Auction House.",
                        () -> { cfg.talismanUseCraft = !cfg.talismanUseCraft; Config.save(); ref[0].refresh(); }),
                new Action("Max price", "Set a maximum price (Settings → Items).", () -> {
                    SettingsScreen.selectSection("Items", "Talismans");
                    Compat.setScreen(Minecraft.getInstance(), new SettingsScreen(ref[0]));
                }),
                new Action("Refresh", "Recalculate with the newest prices.", () -> ref[0].refresh()));
    }

    private static List<String> bagFooter() {
        long scanned = Accessories.scannedAt();
        return List.of(scanned == 0 ? "§eOpen every page of your Accessory Bag once so the ones you have are skipped."
                : "§8Accessory Bag read " + new java.text.SimpleDateFormat("dd.MM HH:mm").format(new java.util.Date(scanned)) + ". Reopen it after buying.");
    }

    private static Page buyPage(MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        List<String> footer = new ArrayList<>();
        if (Accessories.count() == 0 || !Prices.loaded()) {
            footer.add(Accessories.count() == 0 ? "§7Item data is still downloading (first time takes a minute). Press Refresh shortly." : "§7Prices are still loading.");
            return new Page(new String[]{""}, new int[]{400}, rows, top(ref), footer);
        }
        double total = 0;
        int mp = 0, n = 0;
        for (Accessories.Pick p : Accessories.recommend(2000)) {
            if (!rarityOk(p.item().rarity())) continue;
            if (n >= count) break;
            n++;
            total += p.cost();
            mp += p.gain();
            boolean ah = p.source().equals("AH"), npc = p.source().equals("NPC");
            String tip = color(p.item().rarity()) + p.item().name() + "\n§7" + p.item().rarity() + " accessory, +" + p.gain() + " MP"
                    + (p.replaces() != null ? "\n§7Upgrades your " + p.replaces() : "")
                    + "\n§7" + (ah ? "Lowest BIN" : npc ? "NPC shop" : "Craft cost") + ": §6" + Fmt.coins(p.cost());
            Action go = ah ? new Action("§eAH", "Search the Auction House.", () -> MenuScreen.runCommand("ahs " + p.item().name()))
                    : npc ? new Action("§dNPC", "Which NPC sells it (wiki).", () -> wiki(p.item().name()))
                    : new Action("§bRecipe", "Open the recipe.", () -> MenuScreen.runCommand("recipe " + p.item().name()));
            rows.add(new Row(new String[]{"§8" + n + ". " + color(p.item().rarity()) + p.item().name(), color(p.item().rarity()) + p.item().rarity().toLowerCase(),
                    "§a+" + p.gain() + " MP", "§6" + Fmt.coins(p.cost()), "§8" + Fmt.coins(p.cost() / p.gain()) + "/MP"}, tip, List.of(go)));
        }
        footer.add("§7Total for these " + n + ": §6" + Fmt.coins(total) + " §7for §a+" + mp + " MP");
        footer.addAll(bagFooter());
        return new Page(new String[]{"Accessory", "Rarity", "MP", "Price", ""}, new int[]{165, 70, 55, 55, 55}, rows, top(ref), footer);
    }

    /** Accessories you can't buy, one row per upgrade chain (Talisman → Ring → Artifact together). */
    private static Page otherPage(MenuScreen[] ref) {
        Accessories.recommend(2000);                                  // fills the list
        Map<List<String>, List<Accessories.Pick>> groups = new LinkedHashMap<>();
        for (Accessories.Pick p : Accessories.unbuyable) {
            if (!rarityOk(p.item().rarity())) continue;
            groups.computeIfAbsent(Accessories.chainOf(p.item().id()), k -> new ArrayList<>()).add(p);
        }
        List<Row> rows = new ArrayList<>();
        int total = 0;
        for (var g : groups.entrySet()) {
            List<String> chain = g.getKey();
            StringBuilder names = new StringBuilder();
            StringBuilder tip = new StringBuilder();
            int best = 0;
            String first = null;
            for (String id : chain) {
                Accessories.Info info = Accessories.info(id);
                if (info == null) continue;
                boolean owned = Accessories.owns(id);
                boolean missing = g.getValue().stream().anyMatch(p -> p.item().id().equals(id));
                if (names.length() > 0) names.append(" §8→ ");
                names.append(owned ? "§8§m" : color(info.rarity())).append(info.name()).append(owned ? "§r" : "");
                tip.append(color(info.rarity())).append(info.name()).append(" §7(").append(info.rarity().toLowerCase()).append(", ")
                        .append(info.mp()).append(" MP)").append(owned ? " §a✔ have" : "").append("\n");
                String hint = CraftCost.HINTS.get(id);
                if (hint != null && missing) tip.append("  §7").append(hint).append("\n");
                if (missing && first == null) first = info.name();
            }
            for (Accessories.Pick p : g.getValue()) best = Math.max(best, p.gain());
            total += best;
            String hint = first == null ? "" : CraftCost.HINTS.getOrDefault(g.getValue().get(0).item().id(), "quest, drop, event or collection");
            final String wikiName = first != null ? first : g.getValue().get(0).item().name();
            rows.add(new Row(new String[]{names.toString(), "§a+" + best + " MP", "§7" + hint}, tip.toString().trim(),
                    List.of(new Action("Wiki", "How to get " + wikiName + ".", () -> wiki(wikiName)))));
        }
        rows.sort((a, b) -> Integer.compare(mpOf(b), mpOf(a)));
        List<String> footer = new ArrayList<>();
        footer.add("§7" + rows.size() + " chains / accessories you can't buy or craft, up to §a+" + total + " MP §7together. Owned tiers are struck through.");
        footer.addAll(bagFooter());
        return new Page(new String[]{"Accessory (upgrades together)", "MP", "How to get it"}, new int[]{220, 50, 200}, rows, top(ref), footer);
    }

    private static int mpOf(Row r) {
        try { return Integer.parseInt(Tracker.strip(r.cells()[1]).replaceAll("[^0-9]", "")); } catch (Exception e) { return 0; }
    }

    private static void wiki(String name) {
        Compat.setScreen(Minecraft.getInstance(), null);
        Tracker.say(Chat.link("§6[Talismans] §f" + name + " §7on the wiki: §a§n[open]", "https://hypixelskyblock.minecraft.wiki/w/" + name.replace(' ', '_')));
    }

    // ---------------- opening from a command ----------------

    private static boolean openNextTick;

    public static void requestOpen(int n) { count = n <= 10 ? 10 : n <= 20 ? 20 : 50; openNextTick = true; }

    static void tick(Minecraft mc) {
        if (openNextTick && Compat.noScreen(mc)) {
            openNextTick = false;
            Compat.setScreen(mc, screen(null));
        }
    }

    private TalismansScreen() {}
}
