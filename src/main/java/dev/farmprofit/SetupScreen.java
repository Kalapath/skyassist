package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** First-time check: what the mod can see, what to switch on, duplicate features. Reopen with /profit setup. */
public final class SetupScreen extends Screen {
    private static boolean shownThisLaunch, openNextTick;
    private static long onSkyBlockSince;

    private final Screen parent;

    public SetupScreen() { this(null); }

    public SetupScreen(Screen parent) {
        super(Component.literal("SkyAssist setup"));
        this.parent = parent;
    }

    private record Check(boolean ok, String text, String fix) {}

    private static List<Check> checks() {
        List<Check> out = new ArrayList<>();
        out.add(new Check(HypixelLocation.active, "Hypixel Mod API installed",
                "Optional but recommended: download 'Hypixel Mod API' (Fabric, 26.1) from Modrinth for exact location."));
        out.add(new Check(Tracker.areaName != null || HypixelLocation.mode != null, "Location detected: " + Tracker.areaName,
                "Turn on the Info widget (shows 'Area: ...') in Hypixel's tab list widgets, or install the Hypixel Mod API."));
        boolean stats = Tracker.tab.keySet().stream().anyMatch(k -> k.contains("Fortune") || k.contains("Speed") || k.contains("Sweep") || k.contains("Magic Find"));
        out.add(new Check(stats, "Stats widget (fortune, speed...)", "Add the Stats widget to your tab list and pick the stats you care about."));
        boolean powders = Tracker.tab.containsKey("Mithril") || Tracker.tab.containsKey("Gemstone") || Tracker.tab.containsKey("Mithril Powder");
        out.add(new Check(powders, "Powders widget (mining)", "Only needed for mining: add the Powders widget in the Dwarven Mines / Crystal Hollows."));
        out.add(new Check(Tracker.purse >= 0, "Sidebar readable (purse)", "Used for slayer quest costs. Make sure the scoreboard sidebar is visible."));
        out.add(new Check(Prices.loaded(), "Prices loaded (" + Prices.bazaarCount() + " Bazaar items)", "Needs internet. Wait a few seconds or run /profit prices."));
        return out;
    }

    @Override
    protected void init() {
        int left = Math.max(10, width / 2 - 170), y = 20;
        addRenderableWidget(new StringWidget(left, y, 340, 10, Component.literal("§6§lSkyAssist — setup check"), font));
        y += 18;
        for (Check c : checks()) {
            StringWidget w = new StringWidget(left, y, 340, 10, Component.literal((c.ok() ? "§a✔ " : "§c✖ ") + "§f" + c.text()), font);
            if (!c.ok()) w.setTooltip(Tooltip.create(Component.literal(c.fix())));
            addRenderableWidget(w);
            y += 14;
            if (!c.ok()) {
                addRenderableWidget(new StringWidget(left + 12, y, 330, 10, Component.literal("§7" + c.fix()), font));
                y += 14;
            }
        }
        List<Dedupe.Overlap> dup = Dedupe.active();
        if (!dup.isEmpty()) {
            y += 6;
            addRenderableWidget(new StringWidget(left, y, 340, 10, Component.literal("§eAlso in your other mods (would show twice):"), font));
            y += 14;
            for (Dedupe.Overlap o : dup) {
                addRenderableWidget(new StringWidget(left + 12, y, 330, 10, Component.literal("§f" + o.feature() + " §8— " + o.otherMod()), font));
                y += 12;
            }
        }
        // What do you play? (switches each activity's HUD and helpers together)
        y += 8;
        addRenderableWidget(new StringWidget(left, y, 340, 10, Component.literal("§eWhat do you play? §7(click to switch an activity's HUD + helpers)"), font));
        y += 14;
        int bx = left, col = 0;
        for (String group : Playstyle.GROUPS.keySet()) {
            boolean on = Playstyle.isOn(group);
            final String g = group;
            addRenderableWidget(Button.builder(Component.literal((on ? "§a✔ " : "§c✖ ") + group), b -> { Playstyle.set(g, !Playstyle.isOn(g)); rebuildWidgets(); })
                    .bounds(bx, y, 66, 18).build());
            bx += 69;
            if (++col % 5 == 0) { bx = left; y += 21; }
        }
        y += 24;
        int by = Math.max(y + 6, height - 56);
        addRenderableWidget(Button.builder(Component.literal("Open tab widgets"), b -> {
            onClose();
            Object conn = Minecraft.getInstance().getConnection();
            if (Reflect.call(conn, "sendCommand", "widget") == Reflect.FAIL) Tracker.say("§6[SkyAssist] §7Type §f/widget §7to edit your tab list.");
        }).bounds(left, by, 110, 20).build()).setTooltip(Tooltip.create(Component.literal("Runs Hypixel's /widget menu.")));
        if (!dup.isEmpty()) {
            addRenderableWidget(Button.builder(Component.literal("Turn off duplicates"), b -> { Dedupe.turnOff(); rebuildWidgets(); })
                    .bounds(left + 115, by, 110, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Settings"), b -> Compat.setScreen(Minecraft.getInstance(), new SettingsScreen(this)))
                .bounds(left + 230, by, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Check again"), b -> rebuildWidgets()).bounds(left, by + 24, 110, 20).build());
        addRenderableWidget(Button.builder(Component.literal("§aDone"), b -> onClose()).bounds(left + 230, by + 24, 110, 20).build());
    }

    @Override
    public void onClose() {
        Config.get().setupDone = true;
        Config.save();
        Compat.setScreen(Minecraft.getInstance(), parent);
    }

    public static void requestOpen() { openNextTick = true; }

    /** Opens once, about 8 seconds after you first join SkyBlock, until you've pressed Done. */
    static void tick(Minecraft mc) {
        if (openNextTick && Compat.screen(mc) == null) {
            openNextTick = false;
            Compat.setScreen(mc, new SetupScreen());
            return;
        }
        boolean onSkyBlock = mc.player != null && (Tracker.areaName != null || Tracker.purse >= 0
                || (HypixelLocation.serverType != null && HypixelLocation.serverType.toUpperCase().contains("SKYBLOCK")));
        if (!onSkyBlock) { onSkyBlockSince = 0; return; }
        if (onSkyBlockSince == 0) onSkyBlockSince = System.currentTimeMillis();
        if (System.currentTimeMillis() - onSkyBlockSince < 8000) return;
        Dedupe.warnOnce();
        UpdateCheck.start();
        if (Config.get().setupDone || shownThisLaunch || Compat.screen(mc) != null) return;
        shownThisLaunch = true;
        boolean others = net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skyhanni")
                || net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skyblocker");
        if (others && Panels.isFresh()) {
            var win = mc.getWindow();
            Panels.preset("right", win.getGuiScaledWidth(), win.getGuiScaledHeight());
        }
        Compat.setScreen(mc, new SetupScreen());
    }
}
