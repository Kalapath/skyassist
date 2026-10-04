package dev.farmprofit;

import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * Heart of the Mountain / Heart of the Forest guides. Each strategy has: the goal, the tree (which tiers matter),
 * and a numbered upgrade order with target levels. From the Hypixel SkyBlock Wiki (Heart of the Mountain, Mining Guide,
 * Foraging Guide, all updated 2026) and recent forum threads; advice from before the 2024 HOTM rework is left out.
 */
public final class Guides {

    /** rows: {"left", "right"}; a left starting with "#" is a section header. */
    private static Page page(String[][] rows, String... footer) {
        List<Row> out = new ArrayList<>();
        for (String[] r : rows) {
            if (r[0].startsWith("#")) out.add(new Row("§6§l" + r[0].substring(1), "§8" + r[1]));
            else out.add(new Row(r[0], r[1]));
        }
        return new Page(new String[]{"", ""}, new int[]{170, 290}, out, List.of(), List.of(footer));
    }

    // ---- shared HOTM facts (wiki) ----
    private static final String[][] HOTM_TREE = {
            {"#Tree", "how HOTM works"},
            {"§fTier 1", "Mining Speed (max 50: +1,000 Mining Speed)."},
            {"§fTier 2", "Pickaxe abilities: Mining Speed Boost or Pickobulus. Mining Fortune (max 50: +100 Mining Fortune), Titanium Insanium."},
            {"§fTier 3", "Luck of the Cave (max 45), the Mining Spread perk (max 100: +300 Spread), Quick Forge (max 20: −30% forge time), Sky Mall."},
            {"§fTier 6", "Pickaxe abilities: Tunnel Vision (was 'Anomalous Desire') or Maniac Miner."},
            {"§fTier 10", "Pickaxe abilities: Gemstone Infusion or Sheer Force (+200 Mining Spread while active)."},
            {"§fCore of the Mountain", "The center column. Powder in it is never refunded on reset; level 2 gives every ability +1 level."},
            {"§fTokens", "You get 20 Tokens of the Mountain from tiers (plus a few from Core of the Mountain). Every perk costs one token to unlock."},
    };

    private static String[][] join(String[][] a, String[][] b) {
        String[][] r = new String[a.length + b.length][];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    public static Screen hotm(Screen parent) {
        return new MenuScreen("Heart of the Mountain guide", List.of(
                new Tab("Powder grinding", () -> page(join(new String[][]{
                        {"#Goal", "Gemstone powder from Crystal Hollows treasure chests"},
                        {"§fWhat you do", "Mine Hard Stone in the Crystal Hollows; treasure chests appear and give powder."},
                        {"#Upgrade order", "do them top to bottom"},
                        {"§a1. Great Explorer → max", "More treasure chests and fewer lockpicks. Everyone's #1 for powder."},
                        {"§a2. Powder Buff → max", "More powder from everything (HOTM 7)."},
                        {"§a3. Mining Spread perk → 50, later 100", "Breaks the Hard Stone around you too = more blocks = more chests."},
                        {"§a4. Mining Fortune → 50", "With Powder Buff, the 'must have' pair."},
                        {"§a5. Mining Speed → 50", "Faster Hard Stone = more chests."},
                        {"§a6. Luck of the Cave", "Only if tokens and powder are left over."},
                        {"§bAbility", "Sheer Force (tier 10) if you have it (+200 Spread), else Maniac Miner (tier 6)."},
                        {"§bSkip", "Quick Forge and Titanium Insanium don't help powder."},
                        {"§bPets", "Snail, Armadillo or Legendary Scatha; Mithril / Glacite Golem boost powder."},
                }, HOTM_TREE), "§8Sources: Hypixel forum threads after the HOTM rework (2024–2026), wiki HOTM page (2026). 'Max Mole' advice is from before the rework.")),
                new Tab("Gemstone mining", () -> page(join(new String[][]{
                        {"#Goal", "Mining gemstones for coins"},
                        {"#Upgrade order", "do them top to bottom"},
                        {"§a1. Mining Speed → 50", "Gemstones are slow to break; speed is everything."},
                        {"§a2. Mining Fortune → 50", "More gemstones per block."},
                        {"§a3. Professional → max", "Extra Mining Speed on gemstones only."},
                        {"§a4. Gemstone Spread / Mining Spread → max", "Break neighboring gemstone blocks too."},
                        {"§a5. Powder Buff → max", "Gemstone mining still gives powder."},
                        {"§a6. Second-row Mining Speed / Fortune perks", "Once the above are maxed."},
                        {"§bAbility", "Gemstone Infusion (tier 10): doubles your drill's gemstone stats while active. Before tier 10: Mining Speed Boost."},
                        {"§bPowder to start", "About 14m gemstone + 8m mithril, plus ~6m glacite powder (community advice, 2026)."},
                        {"§bGear", "Titanium Drill DR-X655, Divan's armor (Jade), Legendary Bal / Scatha / Glacite Golem, perfect gemstones."},
                }, HOTM_TREE), "§8Sources: Hypixel forum 'Mining help' (2026), wiki HOTM page (2026).")),
                new Tab("Mithril mining", () -> page(join(new String[][]{
                        {"#Goal", "Mithril in the Dwarven Mines / Crystal Hollows"},
                        {"#Upgrade order", "mithril powder first, then gemstone powder"},
                        {"§a1. Mining Speed → 50", "Mithril powder."},
                        {"§a2. Mining Fortune → 50", "Mithril powder."},
                        {"§a3. Efficient Miner → max", "More blocks per break."},
                        {"§a4. Seasoned Mineman → max", "More Mining XP."},
                        {"§a5. Titanium Insanium → 50", "Up to 7.1% of mithril becomes Titanium."},
                        {"§a6. Second-row Speed / Fortune, Powder Buff", "With gemstone powder (about 11–17m total)."},
                        {"§bAbility", "Mining Speed Boost; Maniac Miner from tier 6."},
                        {"§bMagma Fields", "Minimum ≈ 7.7m mithril + 7.4m gemstone: Efficient Miner, Mining Speed, Seasoned Mineman, Powder Buff, second-row Speed."},
                        {"§bSetup", "Dimensional armor, royal equipment, Mithril Golem, T2 Mithril drill."},
                }, HOTM_TREE), "§8Source: Hypixel SkyBlock Wiki, Tutorial: Mining Guide + Heart of the Mountain (2026).")),
                new Tab("Glacite & Mineshafts", () -> page(join(new String[][]{
                        {"#Goal", "Glacite powder, Glacite Tunnels, Glacite Mineshafts"},
                        {"#Upgrade order", "do them top to bottom"},
                        {"§a1. Surveyor (HOTM 8) → max", "Up to +15% chance to find a Mineshaft."},
                        {"§a2. Mining Speed + Fortune → 50", "Base stats."},
                        {"§a3. Mineshaft Mayhem", "A random buff in each Mineshaft (e.g. −25% ability cooldown)."},
                        {"§a4. Powder Buff → max", "More glacite powder."},
                        {"§bAbility", "Tunnel Vision (tier 6): +50% chance for Mineshafts, Golden Goblins and more for 30 s."},
                        {"§bGetting glacite powder", "Glacite Tunnels commissions (750 HOTM XP each), fossils, corpses (Glacite Golem pet)."},
                        {"§bPowder", "About 6m glacite for general gemstone mining, 24m+ for mineshaft mining."},
                }, HOTM_TREE), "§8Sources: wiki HOTM page + Mining Guide (2026), Hypixel forum 'Mining help' (2026).")),
                new Tab("Getting started", () -> page(join(new String[][]{
                        {"#Goal", "HOTM 1–6, little powder"},
                        {"#Upgrade order", "do them top to bottom"},
                        {"§a1. Mining Speed → 10–20", "Cheap and immediately noticeable."},
                        {"§a2. Mining Fortune → 10–20", "More drops."},
                        {"§a3. Daily Powder", "First ore each day = +500 × your HOTM tier powder."},
                        {"§a4. Quick Forge", "If you use the Forge (refinements, drills)."},
                        {"§a5. Great Explorer (HOTM 6)", "Then switch to the Powder grinding plan."},
                        {"§bHOTM XP", "Commissions: Dwarven Mines 400, Crystal Hollows 400, Glacite Tunnels 750 XP each; first 4 a day give +900 extra."},
                        {"§bReset freely", "Resetting refunds everything except Core of the Mountain: reset for each plan."},
                }, HOTM_TREE), "§8Source: Hypixel SkyBlock Wiki, Heart of the Mountain (2026)."))
        ), 0, parent).searchable();
    }

    private static final String[][] HOTF_TREE = {
            {"#Tree", "how HOTF works"},
            {"§fTiers 1–3", "Perks paid with Forest Whispers: Sweep, Foraging Fortune; tier 3: Luck of the Forest (Tree Gifts +0.5–20% loot), 250 Gifts (first 250 Gifts a day: +1–40% loot, +20 whispers)."},
            {"§fTier 4+", "Paid with Desert Whispers (Torrhus Canyon): Hunter's Luck, Iron Lungs, Foraging Madness and more."},
            {"§fCenter of the Forest", "The center column: more Sweep and whispers. Never refunded on reset."},
            {"§fReset", "Refunds all whispers spent on perks (not Center of the Forest)."},
    };

    public static Screen hotf(Screen parent) {
        return new MenuScreen("Heart of the Forest guide", List.of(
                new Tab("Forest Whispers (Fig)", () -> page(join(new String[][]{
                        {"#Goal", "Forest Whispers on Moonglade Marsh (Galatea)"},
                        {"#Upgrade order", "do them top to bottom"},
                        {"§a1. Sweep → max", "The most important stat: trees fall faster = more whispers."},
                        {"§a2. Center of the Forest", "More Sweep (%) and more whispers per Tree Gift / log."},
                        {"§a3. 250 Gifts", "+20 whispers per Gift for your first 250 a day."},
                        {"§a4. Foraging Fortune", "More logs."},
                        {"§a5. Tree Whisperer", "Extra whispers per Gift (can't be refunded)."},
                        {"§a6. Lottery", "Random buff to Fig / Mangrove / Helix Fortune or Sweep."},
                        {"§bMethod", "Small Fig trees as fast as possible; tall Fig trees at 600+ Sweep."},
                        {"§bGear", "Fig Armor (Groovy), David's Cloak + Mangrove equipment, Figstone Splitter, Jade Dragon pet."},
                        {"§cSkip", "Ricochet / homing axe perks (players report they still often don't work)."},
                }, HOTF_TREE), "§8Sources: Hypixel forum 'Best Heart of the Forest setup' (2026), wiki HOTF page (2026).")),
                new Tab("Desert Whispers (Helix)", () -> page(join(new String[][]{
                        {"#Goal", "Helix trees on Torrhus Canyon, Desert Whispers"},
                        {"#Upgrade order", "reset at tier 4, then top to bottom"},
                        {"§a1. Sweep → max", "Helix trees are tough; Sweep matters even more."},
                        {"§a2. Foraging Fortune", "More Helix logs."},
                        {"§a3. Luck of the Forest + 250 Gifts", "Better and more Tree Gifts."},
                        {"§a4. Hunter's Luck", "Part of the wiki's tier 4 tree."},
                        {"§a5. Iron Lungs", "Part of the wiki's tier 4 tree."},
                        {"§a6. Foraging Madness", "Part of the wiki's tier 4 tree."},
                        {"§a7. Forest Speed", "Only if you build Speed (up to +50 Sweep at 500 Speed)."},
                        {"§bMethod", "On Torrhus Canyon, Helix trees are by far the best (wiki)."},
                        {"§bGear", "Helix Chopper with Moonglade reforge, Helix Armor with Groovy reforge."},
                        {"§bPersonal Best", "Each wood's Personal Best perk: up to +10% Sweep (all three +30%)."},
                }, HOTF_TREE), "§8Source: Hypixel SkyBlock Wiki, Tutorial: Foraging Guide (after the Aug 2026 Torrhus update).")),
                new Tab("Getting started", () -> page(join(new String[][]{
                        {"#Goal", "HOTF 1–3"},
                        {"#Upgrade order", "do them top to bottom"},
                        {"§a1. A level in every perk", "First levels are cheap; unlock them all."},
                        {"§a2. Sweep → max", "Then everything else gets faster."},
                        {"§a3. Foraging Fortune", "More logs per tree."},
                        {"§a4. 250 Gifts, Luck of the Forest", "When you open many Tree Gifts a day (tier 3)."},
                        {"§bHOTF XP", "Opening Tree Gifts on Galatea and Agatha's Contests."},
                }, HOTF_TREE), "§8Source: Hypixel SkyBlock Wiki, Heart of the Forest (2026)."))
        ), 0, parent).searchable();
    }

    private Guides() {}
}
