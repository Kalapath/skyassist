package dev.farmprofit;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.farmprofit.MenuScreen.Action;
import dev.farmprofit.MenuScreen.Page;
import dev.farmprofit.MenuScreen.Row;
import dev.farmprofit.MenuScreen.Tab;

/**
 * The Status page (/skyassist status): is everything loading, and if not, why. Four tabs:
 *  Data (every online source with its state and a Retry button), Problems (features that hit an error and were skipped),
 *  Features (what the mod can see in the game), About (versions, other mods).
 * "Copy report" puts all of it on the clipboard as text, to paste when asking for help.
 */
public final class Status {
    public static Screen screen(Screen parent) { return screen(parent, 0); }

    public static Screen screen(Screen parent, int startTab) {
        MenuScreen[] ref = new MenuScreen[1];
        int failing = Health.failedCount(), problems = Safe.faultCount();
        ref[0] = new MenuScreen("SkyAssist status", List.of(
                new Tab("Data" + (failing > 0 ? " (" + failing + " failing)" : ""), () -> data(ref)),
                new Tab("Problems" + (problems > 0 ? " (" + problems + ")" : ""), () -> problems(ref)),
                new Tab("Features", Status::features),
                new Tab("About", () -> about(ref))
        ), startTab, parent).liveWhile(Status::busy);
        return ref[0];
    }

    /** Something is downloading right now (the page then updates itself every second). */
    private static boolean busy() {
        for (Health.Source s : Health.sources()) if (s.state() == Health.State.LOADING) return true;
        return false;
    }

    // ------------------------------------------------------------------ Data

    private static Page data(MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        for (Health.Source s : Health.sources()) {
            Health.State st = s.state();
            String info = switch (st) {
                case OK -> "§7" + (s.detail.isEmpty() ? "loaded" : s.detail) + " §8· " + Health.ago(s.lastOk);
                case LOADING -> "§7downloading…";
                case STALE -> "§e" + s.error + " §8· older data is still in use" + (s.detail.isEmpty() ? "" : " (" + s.detail + ")");
                case FAILED -> "§c" + s.error + " §8· tried " + s.fails + "x";
                case OFF -> "§8" + s.off;
                case WAITING -> "§8waiting to start";
            };
            StringBuilder tip = new StringBuilder("§f" + s.name);
            if (!s.purpose.isEmpty()) tip.append("\n§7Used for: ").append(s.purpose);
            if (!s.url.isEmpty()) tip.append("\n§8").append(s.url);
            tip.append("\n§7Last try: ").append(Health.ago(s.lastTry)).append("   Last success: ").append(Health.ago(s.lastOk));
            if (!s.error.isEmpty()) tip.append("\n§cProblem: ").append(s.error);
            Runnable retry = s.retry;
            List<Action> buttons = retry != null && st != Health.State.OFF && st != Health.State.LOADING
                    ? List.of(new Action("Retry", "Try this one again now.", () -> { retry.run(); ref[0].refresh(); }))
                    : List.of();
            rows.add(new Row(new String[]{"§f" + s.name, Health.stateText(st), info}, tip.toString(), buttons));
        }
        List<Action> top = List.of(
                new Action("Retry failed", "Tries every source that is failing again right now.", () -> {
                    int n = Health.retryFailed();
                    Tracker.say("§6[SkyAssist] §7" + (n == 0 ? "Nothing to retry." : "Retrying " + n + " data source" + (n == 1 ? "" : "s") + "..."));
                    ref[0].refresh();
                }),
                new Action("Refresh prices", "Downloads Bazaar, item and auction prices again.", () -> { Prices.refresh(); ref[0].refresh(); }),
                new Action("Copy report", "Copies everything on this page as text, to paste when asking for help.", Status::copyReport));
        List<String> footer = new ArrayList<>();
        footer.add("§7Prices: §f" + Prices.bazaarCount() + " §7Bazaar products, §f" + Prices.itemCount() + " §7items, §f" + Prices.binCount()
                + " §7auction prices §8(" + Prices.binSource + ")");
        footer.add("§8Downloads run in the background. A source that fails is tried again by itself, waiting longer each time. "
                + "Hover a row for what it's used for and its address.");
        return new Page(new String[]{"Source", "State", "Details"}, new int[]{125, 85, 250}, rows, top, footer);
    }

    // ------------------------------------------------------------------ Problems

    private static Page problems(MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        for (Safe.Fault f : Safe.faults()) {
            long paused = Safe.pausedUntil(f.where());
            String where = "§f" + f.where() + (paused > 0 ? " §6(paused " + Math.max(1, (paused - System.currentTimeMillis()) / 1000) + "s)" : "");
            String tip = "§c" + f.message() + "\n§7First: " + Health.ago(f.first()) + "   Last: " + Health.ago(f.last()) + "   Times: " + f.count()
                    + (f.trace().isEmpty() ? "" : "\n§8" + f.trace().replace("\n", "\n§8"));
            rows.add(new Row(new String[]{where, "§e" + f.count() + "x", "§8" + Health.ago(f.last()), "§7" + f.message()}, tip, List.of()));
        }
        if (rows.isEmpty()) rows.add(new Row(new String[]{"§aNothing has failed this session. §7An error inside one feature is caught and listed here instead of crashing the game."}, null, List.of()));
        List<Action> top = List.of(
                new Action("Clear list", "Forget the problems listed here (and un-pause paused features).", () -> { Safe.clear(); ref[0].refresh(); }),
                new Action("Copy report", "Copies the problems, data sources and checks as text, to paste when asking for help.", Status::copyReport));
        return new Page(new String[]{"Feature", "Times", "Last", "What happened"}, new int[]{140, 40, 55, 215}, rows, top,
                List.of("§8A feature that fails over and over is switched off for a minute, then tries again. Hover a row for the technical details.",
                        "§8Everything else keeps working. If one keeps coming back, copy the report and send it."));
    }

    // ------------------------------------------------------------------ Features

    private static Page features() {
        List<Row> rows = new ArrayList<>();
        String group = "";
        for (Debug.Check c : Debug.checks()) {
            if (!c.group().equals(group)) {
                group = c.group();
                rows.add(new Row("§6§l" + group));
            }
            rows.add(new Row(new String[]{"§f" + c.name(), Debug.mark(c.ok()), "§7" + c.note()},
                    c.ok() != null && c.ok() || c.fix().isEmpty() ? null : "§e" + c.fix(), List.of()));
        }
        StringBuilder seen = new StringBuilder();
        Debug.SEEN.forEach((k, v) -> seen.append(k).append(" x").append(v).append(", "));
        rows.add(new Row("§6§lMessages"));
        rows.add(new Row(new String[]{"§fRecognised", "", "§7" + (seen.length() == 0 ? "none yet" : seen.substring(0, seen.length() - 2))}, null, List.of()));
        if (!Debug.lastActionBar.isEmpty()) rows.add(new Row(new String[]{"§fLast action bar", "", "§7" + Debug.lastActionBar}, null, List.of()));
        return new Page(new String[]{"Check", "", "Details"}, new int[]{150, 25, 285}, rows, List.of(),
                List.of("§8§a✔ §8works, §c✖ §8doesn't, §8? §8not known yet (nothing has used it so far). Hover a ✖ for what to do.",
                        "§8Messages the mod saw but didn't understand are saved in config/skyassist/unrecognised-messages.txt."));
    }

    // ------------------------------------------------------------------ About

    private static final String[][] RELATED = {
            {"hypixel-mod-api", "Hypixel Mod API"}, {"skyhanni", "SkyHanni"}, {"skyblocker", "Skyblocker"}, {"odin", "Odin"},
            {"firmament", "Firmament"}, {"nofrills", "NoFrills"}, {"skyocean", "SkyOcean"},
            {"iris", "Iris Shaders"}, {"sodium", "Sodium"}, {"modmenu", "Mod Menu"}};

    private static Page about(MenuScreen[] ref) {
        List<Row> rows = new ArrayList<>();
        rows.add(about("SkyAssist", "§f" + version("skyassist") + " §8(build " + UpdateCheck.thisCommit() + ")"));
        rows.add(about("Minecraft", "§f" + version("minecraft")));
        rows.add(about("Fabric Loader", "§f" + version("fabricloader") + (version("fabric-api") != null ? " §8· Fabric API §f" + version("fabric-api") : " §c· Fabric API missing")));
        rows.add(about("Java", "§f" + System.getProperty("java.version")));
        for (String[] m : RELATED) {
            String v = version(m[0]);
            rows.add(about(m[1], v == null ? "§8not installed" : "§a" + v));
        }
        rows.add(about("Settings folder", "§7config/skyassist"));
        rows.add(about("Update check", Health.sources().stream().filter(s -> s.name.equals("Update check")).findFirst()
                .map(s -> s.state() == Health.State.OFF ? "§8" + s.off : s.error.isEmpty() ? "§7" + s.detail : "§e" + s.error).orElse("§8-")));
        List<Action> top = List.of(
                new Action("Copy report", "Copies versions, data sources, problems and checks as text, to paste when asking for help.", Status::copyReport),
                new Action("What's new", "What changed in the latest versions.", () -> Compat.setScreen(net.minecraft.client.Minecraft.getInstance(), News.screen(ref[0]))),
                new Action("Settings", "Open the settings.", () -> Compat.setScreen(net.minecraft.client.Minecraft.getInstance(), new SettingsScreen(ref[0]))));
        return new Page(new String[]{"", ""}, new int[]{120, 340}, rows, top,
                List.of("§8Prices come from Hypixel's public API. The other sources are on the Data tab (hover a row for the address).",
                        "§8Display only: the mod shows information and opens menus when you press something; it never clicks or plays for you."));
    }

    private static Row about(String label, String value) { return new Row(new String[]{"§7" + label, value}, null, List.of()); }

    // ------------------------------------------------------------------ report

    /** Version of a loaded mod ("26.1.2", "0.17.3"), or null if it isn't loaded. */
    static String version(String id) {
        try {
            return FabricLoader.getInstance().getModContainer(id).map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    /** The mods you installed (not the libraries bundled inside them), as "id version". */
    private static String mods() {
        List<String> out = new ArrayList<>();
        try {
            for (ModContainer m : FabricLoader.getInstance().getAllMods()) {
                if (m.getContainingMod().isPresent()) continue;
                out.add(m.getMetadata().getId() + " " + m.getMetadata().getVersion().getFriendlyString());
            }
        } catch (Throwable t) {
            out.add("(couldn't list: " + t.getClass().getSimpleName() + ")");
        }
        out.sort(String.CASE_INSENSITIVE_ORDER);
        return String.join(", ", out);
    }

    /** Everything needed to understand a problem from the outside, as plain text. */
    public static String report() {
        StringBuilder r = new StringBuilder("SkyAssist report\n");
        r.append("SkyAssist ").append(version("skyassist")).append(" (build ").append(UpdateCheck.thisCommit()).append("), Minecraft ")
                .append(version("minecraft")).append(", Fabric Loader ").append(version("fabricloader")).append(", Fabric API ")
                .append(version("fabric-api")).append(", Java ").append(System.getProperty("java.version")).append('\n');
        r.append("mods: ").append(mods()).append('\n');
        r.append("area=").append(Tracker.areaName).append(", hud=").append(Tracker.area).append(", modapi=").append(HypixelLocation.active)
                .append(" mode=").append(HypixelLocation.mode).append(" map=").append(HypixelLocation.map).append('\n');
        r.append("tab keys: ").append(String.join(", ", Tracker.tab.keySet())).append('\n');
        r.append("purse=").append(Tracker.purse).append(" floor=").append(Tracker.dungeonFloor)
                .append(" prices=").append(Prices.bazaarCount()).append('/').append(Prices.itemCount()).append('/').append(Prices.binCount()).append('\n');
        r.append("icons=").append(Debug.iconsWork).append(" scale=").append(Debug.scaleWorks).append(" mouse=").append(Debug.mouseWorks).append('\n');
        r.append('\n').append(Health.report()).append('\n');
        r.append("Checks:\n");
        for (Debug.Check c : Debug.checks()) {
            r.append("  ").append(c.ok() == null ? "?     " : c.ok() ? "OK    " : "FAIL  ").append(c.name());
            if (!c.note().isEmpty()) r.append(" - ").append(c.note());
            r.append('\n');
        }
        r.append("recognised: ").append(Debug.SEEN).append('\n');
        r.append("action bar: ").append(Debug.lastActionBar).append('\n');
        r.append("unrecognised messages:\n");
        try {
            java.nio.file.Path f = Config.DIR.resolve("unrecognised-messages.txt");
            if (java.nio.file.Files.exists(f)) {
                List<String> lines = java.nio.file.Files.readAllLines(f);
                for (String l : lines.subList(Math.max(0, lines.size() - 40), lines.size())) r.append("  ").append(l).append('\n');
            }
        } catch (Exception ignored) {}
        return r.toString();
    }

    static void copyReport() {
        String text = Safe.get("Status report", Status::report, "SkyAssist report: could not be built (see latest.log)");
        if (Chat.copy(text)) Tracker.say("§6[SkyAssist] §7Report copied to your clipboard. Paste it where you're asking for help.");
        else Tracker.say("§6[SkyAssist] §7Couldn't copy. Send config/skyassist/unrecognised-messages.txt and a screenshot of /skyassist status instead.");
    }

    private Status() {}
}
