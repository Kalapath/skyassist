package dev.farmprofit;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** "What do you play?": each activity switches its HUD and all of its helpers on or off together. */
public final class Playstyle {
    public static final Map<String, List<String>> GROUPS = new LinkedHashMap<>();
    static {
        GROUPS.put("Farming", List.of("showFarmingHud", "pestHighlight", "greenhouseGuide", "contestAlert", "showContests"));
        GROUPS.put("Mining", List.of("showMiningHud", "powderChests", "lockpickHelper", "glowGhosts", "glowCommission"));
        GROUPS.put("Foraging", List.of("showForagingHud"));
        GROUPS.put("Fishing", List.of("showFishingHud", "fishingAlert", "glowSeaCreatures"));
        GROUPS.put("Combat", List.of("showCombatHud", "glowSlayer", "glowZealots"));
        GROUPS.put("Dungeons", List.of("showDungeonsHud", "secretFinder", "chestProfit", "glowStarred", "glowKeys", "glowBats",
                "solveWeirdos", "solveBlaze", "solveTerminals", "solveIceFill", "solveCreeper", "solveTeleport", "solveTicTacToe", "solveQuiz", "solveMelody"));
        GROUPS.put("Kuudra", List.of("showKuudraHud"));
        GROUPS.put("Diana", List.of("showDianaHud", "dianaHelper", "glowInquisitor"));
        GROUPS.put("Bazaar", List.of("bazaarHud", "auctionHud"));
    }

    /** On if the activity's main switch (first in its list) is on. */
    public static boolean isOn(String group) {
        try { return Config.class.getField(GROUPS.get(group).get(0)).getBoolean(Config.get()); } catch (Exception e) { return true; }
    }

    public static void set(String group, boolean on) {
        for (String f : GROUPS.get(group)) {
            try { Config.class.getField(f).setBoolean(Config.get(), on); } catch (Exception ignored) {}
        }
        Config.save();
    }

    private Playstyle() {}
}
