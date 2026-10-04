package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Graphic HOTM / HOTF guide: the tree drawn by tier (top = highest, like in game), every perk a box colored by
 * how important it is for the chosen strategy (green core, yellow later, gray skip) with its step number and
 * target level, and next to it the step-by-step plan ("Mining Speed → 10, then Mining Fortune → 10, ...").
 */
public final class TreeGuideScreen extends Screen {
    /** A perk on the tree. tier 0 = position not confirmed ("other perks" row). max = top level (0 = one level / ability). */
    public record Perk(String name, int tier, int max, String what) {}
    public record Step(String perk, String target) {}
    public record Strategy(String name, String goal, List<Step> steps, String ability, List<String> notes, String source) {}

    private final String title;
    private final List<Perk> perks;
    private final List<Strategy> strategies;
    private final Screen parent;
    private final java.util.function.Supplier<Screen> details;
    private int chosen;

    public TreeGuideScreen(String title, List<Perk> perks, List<Strategy> strategies, Screen parent, java.util.function.Supplier<Screen> details) {
        super(Component.literal(title));
        this.title = title; this.perks = perks; this.strategies = strategies; this.parent = parent; this.details = details;
    }

    @Override
    protected void init() {
        int left = 8, top = 8;
        addRenderableWidget(new StringWidget(left, top, 300, 10, Component.literal("§6§l" + title + " §7— pick a goal:"), font));
        int x = left, y = top + 14;
        for (int i = 0; i < strategies.size(); i++) {
            final int k = i;
            String n = strategies.get(i).name();
            int w = font.width(n) + 12;
            addRenderableWidget(Button.builder(Component.literal(i == chosen ? "§e§l" + n : n), b -> { chosen = k; rebuildWidgets(); }).bounds(x, y, w, 18).build());
            x += w + 3;
        }
        Strategy s = strategies.get(chosen);

        // first step number + final target per perk
        Map<String, Integer> firstStep = new LinkedHashMap<>();
        Map<String, String> finalTarget = new LinkedHashMap<>();
        for (int i = 0; i < s.steps().size(); i++) {
            Step st = s.steps().get(i);
            firstStep.putIfAbsent(st.perk(), i + 1);
            finalTarget.put(st.perk(), st.target());
        }
        int half = Math.max(1, s.steps().size() / 2);

        // ---- the tree (left) ----
        int treeW = Math.min(width / 2 + 40, 420);
        y += 26;
        addRenderableWidget(new StringWidget(left, y, treeW, 10, Component.literal("§7" + s.goal()), font));
        y += 14;
        int rowH = Math.max(16, Math.min(21, (height - y - 34) / 12));
        for (int tier = 10; tier >= 0; tier--) {
            final int t = tier;
            List<Perk> row = perks.stream().filter(p -> p.tier() == t).toList();
            if (row.isEmpty()) continue;
            addRenderableWidget(new StringWidget(left, y + 4, 34, 10, Component.literal(t == 0 ? "§8other" : "§7T" + t), font));
            int px = left + 36;
            for (Perk p : row) {
                Integer step = firstStep.get(p.name());
                String col = step == null ? "§8" : step <= half ? "§a" : "§e";
                String label = col + (step != null ? "#" + step + " " : "") + p.name() + (finalTarget.containsKey(p.name()) ? " §7" + finalTarget.get(p.name()) : "");
                int w = Math.min(treeW - 40, font.width(label) + 10);
                if (px + w > left + treeW) { y += rowH; px = left + 36; }
                Button chip = Button.builder(Component.literal(label), b -> { }).bounds(px, y, w, rowH - 2).build();
                String tip = (step == null ? "§8Not needed for this goal" : "§aStep " + step + " §7→ " + finalTarget.get(p.name()))
                        + "\n§f" + p.name() + (p.max() > 0 ? " §7(max " + p.max() + ")" : "") + "\n§7" + p.what();
                chip.setTooltip(Tooltip.create(Component.literal(tip)));
                addRenderableWidget(chip);
                px += w + 3;
            }
            y += rowH;
        }
        addRenderableWidget(new StringWidget(left, y + 2, treeW, 10, Component.literal("§a■ core §e■ later §8■ skip §7— hover a perk for details"), font));

        // ---- the plan (right) ----
        int rx = left + treeW + 12, ry = top + 48, rw = width - rx - 8;
        addRenderableWidget(new StringWidget(rx, ry, rw, 10, Component.literal("§6§lUpgrade order"), font));
        ry += 13;
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < s.steps().size(); i++) {
            Step st = s.steps().get(i);
            lines.add((i < half ? "§a" : "§e") + (i + 1) + ". §f" + st.perk() + " §7→ " + st.target());
        }
        lines.add("");
        lines.add("§bAbility: §f" + s.ability());
        for (String n : s.notes()) lines.add("§7• " + n);
        lines.add("§8" + s.source());
        for (String l : lines) {
            for (String part : wrap(l, rw)) {
                if (ry > height - 30) break;
                addRenderableWidget(new StringWidget(rx, ry, rw, 10, Component.literal(part), font));
                ry += 10;
            }
        }
        if (details != null) addRenderableWidget(Button.builder(Component.literal("Text guide"), b -> Compat.setScreen(Minecraft.getInstance(), details.get()))
                .bounds(width - 150, height - 24, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal(parent != null ? "Back" : "Done"), b -> onClose()).bounds(width - 66, height - 24, 60, 20).build());
    }

    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        if (font.width(text) <= width) { out.add(text); return out; }
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String tryLine = line.length() == 0 ? word : line + " " + word;
            if (font.width(tryLine) > width && line.length() > 0) { out.add(line.toString()); line = new StringBuilder("§7  " + word); }
            else line = new StringBuilder(tryLine);
        }
        if (line.length() > 0) out.add(line.toString());
        return out;
    }

    @Override
    public void onClose() { Compat.setScreen(Minecraft.getInstance(), parent); }

    // =====================================================================================================
    // Data
    // =====================================================================================================

    private static Step st(String perk, String target) { return new Step(perk, target); }

    /** HOTM perks by tier (wiki, 2026). Tier 0 = position not confirmed. */
    static final List<Perk> HOTM = List.of(
            new Perk("Gemstone Infusion", 10, 0, "Ability: doubles your drill's gemstone stats while active."),
            new Perk("Sheer Force", 10, 0, "Ability: +200 Mining Spread while active."),
            new Perk("Surveyor", 8, 0, "Up to +15% chance to find a Glacite Mineshaft."),
            new Perk("Powder Buff", 7, 0, "More powder from everything."),
            new Perk("Tunnel Vision", 6, 0, "Ability (was Anomalous Desire): +50% Golden Goblin / Mineshaft / worm chances for 30 s."),
            new Perk("Maniac Miner", 6, 0, "Ability: +1 Breaking Power and up to +500 Mining Fortune while active."),
            new Perk("Great Explorer", 6, 0, "More treasure chests in the Crystal Hollows, fewer lockpicks."),
            new Perk("Core of the Mountain", 5, 0, "Center column. Never refunded. Level 2 gives every ability +1 level."),
            new Perk("Luck of the Cave", 3, 45, "+6–51% Golden Goblin, Fallen Star, Powder Ghast chance (Dwarven Mines)."),
            new Perk("Efficient Miner", 3, 100, "Mining Spread: up to +300 (breaks blocks next to the one you mine)."),
            new Perk("Quick Forge", 3, 20, "Up to −30% Forge time."),
            new Perk("Sky Mall", 3, 0, "A random daily buff on Mining Islands."),
            new Perk("Mining Fortune", 2, 50, "Up to +100 Mining Fortune."),
            new Perk("Titanium Insanium", 2, 50, "Up to 7.1% of Mithril becomes Titanium."),
            new Perk("Precision Mining", 2, 0, "A target on ores: +30% Mining Speed while aiming at it."),
            new Perk("Mining Speed Boost", 2, 0, "Ability: +200–300% Mining Speed for 10–20 s."),
            new Perk("Pickobulus", 2, 0, "Ability: explosion that mines ores in a 3-block radius."),
            new Perk("Mining Speed", 1, 50, "Up to +1,000 Mining Speed."),
            new Perk("Professional", 0, 0, "Extra Mining Speed on gemstones."),
            new Perk("Daily Powder", 0, 0, "First ore each day: +500 × HOTM tier powder."),
            new Perk("Seasoned Mineman", 0, 0, "More Mining XP."),
            new Perk("Mineshaft Mayhem", 0, 0, "A random buff in each Glacite Mineshaft."),
            new Perk("Mining Speed II", 0, 0, "Second Mining Speed perk (gemstone powder)."),
            new Perk("Mining Fortune II", 0, 0, "Second Mining Fortune perk (gemstone powder).")
    );

    static final List<Strategy> HOTM_PLANS = List.of(
            new Strategy("Powder grinding", "Gemstone powder from Crystal Hollows treasure chests (mining Hard Stone).", List.of(
                    st("Mining Speed", "10"), st("Mining Fortune", "10"), st("Great Explorer", "max"), st("Efficient Miner", "30"),
                    st("Powder Buff", "max"), st("Mining Fortune", "30"), st("Efficient Miner", "60"), st("Mining Speed", "30"),
                    st("Mining Fortune", "50 (max)"), st("Efficient Miner", "100 (max)"), st("Mining Speed", "50 (max)"), st("Luck of the Cave", "20 (optional)")),
                    "Sheer Force (T10) > Maniac Miner (T6) > Mining Speed Boost",
                    List.of("Great Explorer first: more chests AND fewer lockpicks.", "Don't put powder in Quick Forge / Titanium Insanium for this.",
                            "Pets: Snail, Armadillo, Legendary Scatha."),
                    "Wiki HOTM page (2026) + forum threads after the 2024 rework. Old 'max Mole' advice is pre-rework."),
            new Strategy("Gemstone mining", "Gemstones for coins (Crystal Hollows / Mineshafts).", List.of(
                    st("Mining Speed", "20"), st("Mining Fortune", "20"), st("Professional", "max"), st("Mining Speed", "35"),
                    st("Mining Fortune", "35"), st("Efficient Miner", "50"), st("Powder Buff", "max"), st("Mining Speed", "50 (max)"),
                    st("Mining Fortune", "50 (max)"), st("Mining Speed II", "max"), st("Mining Fortune II", "max"), st("Efficient Miner", "100 (max)")),
                    "Gemstone Infusion (T10), before that Mining Speed Boost",
                    List.of("Powder to start: ~14m gemstone + 8m mithril + 6m glacite (community, 2026).",
                            "Gear: Drill DR-X655, Divan's armor (Jade), Legendary Bal / Scatha / Glacite Golem."),
                    "Wiki HOTM page + forum 'Mining help' (2026)."),
            new Strategy("Mithril", "Mithril in the Dwarven Mines / Crystal Hollows.", List.of(
                    st("Mining Speed", "20"), st("Mining Fortune", "20"), st("Titanium Insanium", "20"), st("Seasoned Mineman", "max"),
                    st("Mining Speed", "50 (max)"), st("Mining Fortune", "50 (max)"), st("Titanium Insanium", "50 (max)"),
                    st("Efficient Miner", "50"), st("Powder Buff", "max"), st("Mining Speed II", "max"), st("Mining Fortune II", "max")),
                    "Mining Speed Boost, Maniac Miner from T6",
                    List.of("Magma Fields minimum ≈ 7.7m mithril + 7.4m gemstone powder.", "Setup: Dimensional armor, Mithril Golem, T2 Mithril drill."),
                    "Wiki Tutorial: Mining Guide + HOTM page (2026)."),
            new Strategy("Glacite / Mineshafts", "Glacite powder, Glacite Tunnels and Mineshafts.", List.of(
                    st("Mining Speed", "20"), st("Mining Fortune", "20"), st("Surveyor", "max"), st("Mineshaft Mayhem", "unlock"),
                    st("Mining Speed", "50 (max)"), st("Mining Fortune", "50 (max)"), st("Powder Buff", "max"), st("Efficient Miner", "50")),
                    "Tunnel Vision (T6)",
                    List.of("Glacite Tunnels commissions give 750 HOTM XP each.", "~6m glacite for gemstone mining, 24m+ for mineshafts."),
                    "Wiki HOTM page + Mining Guide (2026)."),
            new Strategy("Starting out", "HOTM 1–6 with little powder.", List.of(
                    st("Mining Speed", "10"), st("Mining Fortune", "10"), st("Daily Powder", "unlock"), st("Quick Forge", "10 (if you forge)"),
                    st("Mining Speed", "25"), st("Mining Fortune", "25"), st("Sky Mall", "unlock"), st("Efficient Miner", "20"), st("Great Explorer", "start at HOTM 6")),
                    "Mining Speed Boost (T2)",
                    List.of("HOTM XP: commissions (Dwarven 400, Hollows 400, Glacite 750), first 4 a day +900.", "Reset freely: everything except Core of the Mountain is refunded."),
                    "Wiki HOTM page (2026).")
    );

    /** HOTF perks (wiki, 2026). Tier 0 = position not confirmed. */
    static final List<Perk> HOTF = List.of(
            new Perk("Luck of the Forest", 3, 0, "Tree Gifts give +0.5–20% more loot."),
            new Perk("250 Gifts", 3, 0, "First 250 Tree Gifts a day: +1–40% loot and +20 Forest Whispers."),
            new Perk("Center of the Forest", 2, 0, "Center column: more Sweep and whispers. Never refunded."),
            new Perk("Sweep", 1, 0, "The key stat: trees fall faster."),
            new Perk("Foraging Fortune", 1, 0, "More logs per tree."),
            new Perk("Hunter's Luck", 0, 0, "Part of the wiki's tier 4 optimal tree."),
            new Perk("Iron Lungs", 0, 0, "Part of the wiki's tier 4 optimal tree."),
            new Perk("Foraging Madness", 0, 0, "Part of the wiki's tier 4 optimal tree."),
            new Perk("Tree Whisperer", 0, 0, "Extra Forest Whispers per Tree Gift (can't be refunded)."),
            new Perk("Lottery", 0, 0, "Random buff to Fig / Mangrove / Helix Fortune or Sweep."),
            new Perk("Forest Speed", 0, 0, "Up to +50 Sweep at 500 Speed."),
            new Perk("Ricochet", 0, 0, "Homing axe: players report it often doesn't work right.")
    );

    static final List<Strategy> HOTF_PLANS = List.of(
            new Strategy("Fig / Forest Whispers", "Forest Whispers on Moonglade Marsh (tiers 1–3).", List.of(
                    st("Sweep", "10"), st("Center of the Forest", "next level"), st("Foraging Fortune", "10"), st("Sweep", "25"),
                    st("250 Gifts", "10"), st("Luck of the Forest", "10"), st("Sweep", "max"), st("Tree Whisperer", "10"),
                    st("Foraging Fortune", "max"), st("Lottery", "unlock")),
                    "the axe ability you have; Sweep matters more than abilities",
                    List.of("Small Fig trees as fast as possible; tall Fig trees at 600+ Sweep.", "Gear: Fig Armor (Groovy), Figstone Splitter, Jade Dragon pet.",
                            "Skip Ricochet."),
                    "Forum 'Best Heart of the Forest setup' (2026), wiki HOTF page (2026)."),
            new Strategy("Helix / Desert Whispers", "Helix trees on Torrhus Canyon (tier 4+ costs Desert Whispers).", List.of(
                    st("(reset the tree at tier 4)", "—"), st("Sweep", "25"), st("Foraging Fortune", "15"), st("Hunter's Luck", "10"),
                    st("Iron Lungs", "10"), st("Sweep", "max"), st("Luck of the Forest", "max"), st("250 Gifts", "max"),
                    st("Foraging Madness", "unlock"), st("Foraging Fortune", "max"), st("Forest Speed", "only with high Speed")),
                    "the axe ability you have",
                    List.of("Helix trees are by far the best on Torrhus Canyon (wiki).", "Gear: Helix Chopper (Moonglade), Helix Armor (Groovy).",
                            "Personal Best perks: up to +10% Sweep per wood."),
                    "Wiki Tutorial: Foraging Guide (after the Aug 2026 Torrhus update)."),
            new Strategy("Starting out", "HOTF 1–3.", List.of(
                    st("every perk", "1 (cheap)"), st("Sweep", "15"), st("Foraging Fortune", "10"), st("250 Gifts", "5"), st("Sweep", "30")),
                    "the first one you unlock",
                    List.of("HOTF XP: opening Tree Gifts on Galatea and Agatha's Contests."),
                    "Wiki HOTF page (2026).")
    );

    public static Screen hotm(Screen parent) { return new TreeGuideScreen("Heart of the Mountain", HOTM, HOTM_PLANS, parent, () -> Guides.hotm(null)); }

    public static Screen hotf(Screen parent) { return new TreeGuideScreen("Heart of the Forest", HOTF, HOTF_PLANS, parent, () -> Guides.hotf(null)); }
}
