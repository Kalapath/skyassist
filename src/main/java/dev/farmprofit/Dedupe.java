package dev.farmprofit;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;

/** Finds features that another installed SkyBlock mod already does, so they don't show twice. */
public final class Dedupe {
    /** {setting field, what it is, which mod also does it} */
    public record Overlap(String field, String feature, String otherMod) {}

    private static boolean warned;

    private static boolean loaded(String... ids) {
        for (String id : ids) if (FabricLoader.getInstance().isModLoaded(id)) return true;
        return false;
    }

    public static List<Overlap> overlaps() {
        List<Overlap> out = new ArrayList<>();
        boolean skyblocker = loaded("skyblocker"), skyhanni = loaded("skyhanni"), odin = loaded("odin", "odinclient", "odinmod");
        boolean routes = loaded("secretroutes", "secret-routes", "secret_routes");
        if (skyblocker) out.add(new Overlap("priceTooltips", "Price tooltips", "Skyblocker"));
        if (skyblocker) out.add(new Overlap("chestProfit", "Dungeon chest profit", "Skyblocker (Croesus helper)"));
        if (skyblocker || odin || routes) out.add(new Overlap("secretFinder", "Dungeon secret finder",
                skyblocker ? "Skyblocker" : odin ? "Odin" : "Secret Routes"));
        if (skyhanni) out.add(new Overlap("contestAlert", "Jacob's contest reminder", "SkyHanni"));
        if (skyhanni) out.add(new Overlap("enchantColors", "Enchantment colors", "SkyHanni"));
        if (skyhanni) out.add(new Overlap("skillXpHud", "Skill XP/h line", "SkyHanni"));
        if (skyhanni) out.add(new Overlap("abilityAlert", "Pickaxe ability ready alert", "SkyHanni"));
        if (skyblocker || odin) {
            String who = skyblocker ? "Skyblocker" : "Odin";
            out.add(new Overlap("solveWeirdos", "Three Weirdos solver", who));
            out.add(new Overlap("solveBlaze", "Blaze puzzle helper", who));
            out.add(new Overlap("solveTerminals", "Terminal solvers", who));
            out.add(new Overlap("solveIceFill", "Ice Fill solver", who));
            out.add(new Overlap("solveCreeper", "Creeper Beams solver", who));
            out.add(new Overlap("solveTicTacToe", "Tic Tac Toe solver", who));
            out.add(new Overlap("solveQuiz", "Quiz solver", who));
            out.add(new Overlap("solveMelody", "Melody terminal helper", who));
        }
        return out;
    }

    /** Only the overlaps whose feature is still switched on here. */
    public static List<Overlap> active() {
        List<Overlap> out = new ArrayList<>();
        for (Overlap o : overlaps()) {
            try { if (Config.class.getField(o.field()).getBoolean(Config.get())) out.add(o); } catch (Exception ignored) {}
        }
        return out;
    }

    public static void turnOff() {
        for (Overlap o : active()) {
            try { Config.class.getField(o.field()).setBoolean(Config.get(), false); } catch (Exception ignored) {}
        }
        Config.save();
    }

    /** Once per launch, after joining SkyBlock. */
    public static void warnOnce() {
        if (warned || !Config.get().warnDuplicates || !Config.get().setupDone) return;
        warned = true;
        List<Overlap> act = active();
        if (act.isEmpty()) return;
        Tracker.say("§6[SkyAssist] §7These features are also in other mods you have, so they may show twice:");
        for (Overlap o : act) Tracker.say(" §f" + o.feature() + " §8(also in " + o.otherMod() + ")");
        Tracker.say(Chat.clickable("§a§l[Turn these off here]", "/profit dedupe", "Switches these off in this mod only. You can turn them back on in the settings."));
    }

    private Dedupe() {}
}
