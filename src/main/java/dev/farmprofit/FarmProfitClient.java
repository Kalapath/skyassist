package dev.farmprofit;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.event.client.player.ClientPlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FishingRodItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public final class FarmProfitClient implements ClientModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("skyassist");

    private static KeyMapping settingsKey, menuKey;
    private static final KeyMapping[] macroKeys = new KeyMapping[6];

    @Override
    public void onInitializeClient() {
        KeyMapping.Category category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("skyassist", "main"));
        settingsKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyassist.settings", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_O, category));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.skyassist.menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_P, category));
        for (int i = 0; i < macroKeys.length; i++) {                         // unbound until you pick keys in Controls
            macroKeys[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                    "key.skyassist.macro" + (i + 1), InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, category));
        }
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            for (int i = 0; i < macroKeys.length; i++) {
                while (macroKeys[i].consumeClick()) if (Compat.noScreen(client)) Macros.run(i + 1);
            }
            while (menuKey.consumeClick()) {
                if (Compat.noScreen(client)) Compat.setScreen(client, ProfitMenus.hub());
            }
            while (settingsKey.consumeClick()) {
                if (Compat.noScreen(client)) Compat.setScreen(client, new SettingsScreen());
            }
        });

        Config.load();
        Prices.refresh();
        if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("hypixel-mod-api")) HypixelLocation.init();
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            Enchants.color(lines);
            PriceTooltip.add(stack, lines);
            CraftCost.add(stack, lines);
            TooltipScroll.apply(lines);
        });
        // mouse wheel inside menus scrolls long tooltips (and only then; otherwise the menu scrolls as usual)
        // Per-menu hooks MUST be attached after the menu is set up: Fabric recreates a menu's events every time it's
        // (re)initialised, so anything attached earlier is thrown away. Re-attaching on every init is therefore correct.
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            ScreenMouseEvents.allowMouseScroll(screen).register((s, mouseX, mouseY, horizontal, vertical) ->
                    TooltipScroll.onScroll(vertical));
            ScreenOverlay.register(screen);             // rarity colors, item labels, search highlights, terminal solvers
            InvSearch.attach(screen, w, h);             // search box (+ its key handling)
        });

        // Crops (farming breaks happen on the client)
        ClientPlayerBlockBreakEvents.AFTER.register((level, player, pos, state) -> Tracker.onClientBreak(pos, state));
        AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) -> {
            if (player == Minecraft.getInstance().player) Tracker.onStartBreak(pos);
            return InteractionResult.PASS;
        });

        // Hitting mobs: combat / sea creatures / pests / keeps mining & foraging alive
        AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player == Minecraft.getInstance().player) Tracker.onAttackEntity(entity);
            return InteractionResult.PASS;
        });

        // Fishing rod casts/reels, and pest vacuums
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (player == Minecraft.getInstance().player) {
                var stack = player.getItemInHand(hand);
                if (Tracker.strip(stack.getHoverName().getString()).contains("Ancestral Spade")) {
                    DianaBurrows.onSpade(player.getX(), player.getY(), player.getZ());
                } else if (stack.getItem() instanceof FishingRodItem) {
                    Tracker.onRodUse();
                } else if (!Tracker.MINING.equals(Tracker.area) && !Tracker.FORAGING.equals(Tracker.area)) {
                    String name = Tracker.strip(stack.getHoverName().getString());
                    if (name.contains("Vacuum")) Tracker.onVacuum();
                }
            }
            return InteractionResult.PASS;
        });

        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay) { Secrets.onActionBar(Tracker.strip(message.getString())); return true; }
            try { Tracker.onChat(message); } catch (Throwable t) { LOG.warn("chat handling failed", t); }   // tracking first
            return ChatFilter.allow(Tracker.strip(message.getString()).trim());                            // then the spam filter
        });

        // Right-clicking blocks: marks dungeon secrets as done, keeps the dungeon session alive
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (player == Minecraft.getInstance().player && Tracker.DUNGEONS.equals(Tracker.area)) {
                Secrets.onUse(hit.getBlockPos());
                Tracker.onDungeonAction();
            }
            return InteractionResult.PASS;
        });

        ClientTickEvents.END_CLIENT_TICK.register(Tracker::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> Tracker.endAll(false));

        // lockpick: a green square exactly over the spot to aim at
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("skyassist", "lockpick"), (graphics, delta) -> {
            int[] p = Lockpick.screenPos(Minecraft.getInstance());
            if (p == null) return;
            int x = p[0], y = p[1];
            // crosshair arms (black under lime) so you can find the spot from the side
            graphics.fill(x - 16, y - 2, x - 8, y + 2, 0xFF000000); graphics.fill(x + 8, y - 2, x + 16, y + 2, 0xFF000000);
            graphics.fill(x - 2, y - 16, x + 2, y - 8, 0xFF000000); graphics.fill(x - 2, y + 8, x + 2, y + 16, 0xFF000000);
            graphics.fill(x - 15, y - 1, x - 9, y + 1, 0xFF39FF14); graphics.fill(x + 9, y - 1, x + 15, y + 1, 0xFF39FF14);
            graphics.fill(x - 1, y - 15, x + 1, y - 9, 0xFF39FF14); graphics.fill(x - 1, y + 9, x + 1, y + 15, 0xFF39FF14);
            // the square: black edge, white ring, solid neon green
            graphics.fill(x - 7, y - 7, x + 7, y + 7, 0xFF000000);
            graphics.fill(x - 6, y - 6, x + 6, y + 6, 0xFFFFFFFF);
            graphics.fill(x - 5, y - 5, x + 5, y + 5, 0xFF39FF14);
        });
        // rarity colors on the hotbar
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, Identifier.fromNamespaceAndPath("skyassist", "rarity"), (graphics, delta) -> {
            Minecraft mc = Minecraft.getInstance();
            if (Compat.hudHidden(mc)) return;
            RarityBg.drawHotbar(mc, graphics);
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
                Identifier.fromNamespaceAndPath("skyassist", "hud"), (graphics, delta) -> {
                    Minecraft mc = Minecraft.getInstance();
                    Config cfg = Config.get();
                    boolean chat = Compat.screen(mc) instanceof ChatScreen;
                    if (!chat) HudEditor.reset();
                    if (!cfg.hudEnabled || Compat.hudHidden(mc) || mc.player == null) return;

                    long perfStart = System.nanoTime();
                    List<HudEditor.Box> boxes = HudRenderer.layout(mc);
                    if (boxes.isEmpty()) return;
                    int[] hovered = chat ? HudEditor.update(mc, boxes) : null;
                    Object pose = Reflect.call(graphics, "pose");

                    for (int b = 0; b < boxes.size(); b++) {
                        HudEditor.Box box = boxes.get(b);
                        // each panel has its own size; quietly unscaled if this version can't scale
                        float sc = box.scale();
                        boolean scaled = false;
                        if (Math.abs(sc - 1f) > 0.01f && Reflect.call(pose, "pushMatrix") != Reflect.FAIL) {
                            scaled = Reflect.call(pose, "scale", sc, sc) != Reflect.FAIL;
                            if (!scaled) Reflect.call(pose, "popMatrix");
                            Debug.scaleWorks = scaled;
                        }
                        float eff = scaled ? sc : 1f;
                        int x = Math.round(box.x() / eff), y = Math.round(box.y() / eff), lh = box.lineH();

                        int alpha = Math.max(0, Math.min(255, chat ? Math.max(cfg.hudOpacity, 176) : cfg.hudOpacity));
                        if (box.hidden()) alpha = 60;
                        if (alpha > 0) graphics.fill(x - 3, y - 3, x + box.w() + 3, y + box.h() + 1, alpha << 24);
                        if (HudRenderer.editMode || chat) {
                            boolean over = hovered != null && hovered[0] == b;
                            int c = box.hidden() ? 0x80FF5555 : over ? 0xFFFFAA00 : 0x60FFFFFF;   // outline while chat is open
                            graphics.fill(x - 4, y - 4, x + box.w() + 4, y - 3, c);
                            graphics.fill(x - 4, y + box.h() + 1, x + box.w() + 4, y + box.h() + 2, c);
                            graphics.fill(x - 4, y - 3, x - 3, y + box.h() + 1, c);
                            graphics.fill(x + box.w() + 3, y - 3, x + box.w() + 4, y + box.h() + 1, c);
                        }
                        if (hovered != null && hovered[0] == b && box.lines().get(hovered[1]).item() != null) {
                            int hy = y + hovered[1] * lh;
                            graphics.fill(x - 3, hy - 1, x + box.w() + 3, hy + lh - 1, 0x40FFFFFF);
                        }
                        for (int i = 0; i < box.lines().size(); i++) {
                            Hud.HudLine l = box.lines().get(i);
                            int ly = y + i * lh, tx = x;
                            Object icon = cfg.hudIcons ? Tracker.icon(l.item()) : null;
                            if (icon != null) {
                                drawIcon(graphics, pose, icon, x, ly);
                                tx += 11;
                            }
                            graphics.text(mc.font, (box.hidden() ? "§8" : "") + l.text(), tx, ly, 0xFFFFFFFF, cfg.hudShadow);
                        }
                        if (box.id().equals("greenhouse")) {
                            Greenhouse.Grid g = Greenhouse.grid();
                            if (g != null) {
                                int gx = x, gy = y + box.lines().size() * lh + 4, cw = HudRenderer.CELL_W, ch = HudRenderer.CELL_H;
                                for (int r = 0; r < g.size(); r++) for (int cc = 0; cc < g.size(); cc++)      // empty grid
                                    graphics.fill(gx + cc * cw, gy + r * ch, gx + cc * cw + cw - 2, gy + r * ch + ch - 2, 0x40FFFFFF);
                                for (Greenhouse.Cell cell : g.cells()) {
                                    int cx = gx + cell.col() * cw, cy = gy + cell.row() * ch;
                                    graphics.fill(cx, cy, cx + cw - 2, cy + ch - 2, cell.fill());
                                    boolean empty = cell.label().startsWith("✦");
                                    int tw = mc.font.width(cell.label()), sw = mc.font.width(cell.sub());
                                    graphics.text(mc.font, cell.label(), cx + (cw - 2 - tw) / 2, cy + 3, empty ? 0xFF202020 : 0xFFFFFFFF, !empty);
                                    graphics.text(mc.font, cell.sub(), cx + (cw - 2 - sw) / 2, cy + 12, empty ? 0xFF505050 : 0xFFE0E0E0, false);
                                }
                            }
                        }
                        if (scaled) Reflect.call(pose, "popMatrix");
                    }
                    Perf.add("HUD drawing", System.nanoTime() - perfStart);
                    if (chat) {
                        HudEditor.Box last = boxes.get(boxes.size() - 1);
                        graphics.text(mc.font, "§7Drag: move §8| §7Middle-click: size §8| §7Right-click title: hide §8| §7Right-click item: don't count",
                                boxes.get(0).x(), last.bottom() + 4, 0xFFFFFFFF, true);
                    }
                });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(buildCommand("profit", null));
            dispatcher.register(buildCommand("farmprofit", Tracker.FARMING));
            dispatcher.register(buildCommand("miningprofit", Tracker.MINING));
            dispatcher.register(buildCommand("foragingprofit", Tracker.FORAGING));
            dispatcher.register(buildCommand("fishingprofit", Tracker.FISHING));
            dispatcher.register(buildCommand("combatprofit", Tracker.COMBAT));
            dispatcher.register(buildCommand("dungeonprofit", Tracker.DUNGEONS));
            dispatcher.register(buildCommand("kuudraprofit", Tracker.KUUDRA));
            dispatcher.register(buildCommand("dianaprofit", Tracker.DIANA));
            dispatcher.register(FlipsCommand.build());
            dispatcher.register(ClientCommands.literal("talismans")
                    .executes(ctx -> { TalismansScreen.requestOpen(Config.get().talismanCount); return 1; })
                    .then(ClientCommands.literal("chat").executes(ctx -> { Accessories.show(Config.get().talismanCount); return 1; }))
                    .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 50))
                            .executes(ctx -> { TalismansScreen.requestOpen(IntegerArgumentType.getInteger(ctx, "count")); return 1; })));
            dispatcher.register(ClientCommands.literal("itemsearch").executes(ctx -> { MenuScreen.open(() -> Storage.screen(null)); return 1; }));
            dispatcher.register(ClientCommands.literal("waypoints").executes(ctx -> { MenuScreen.open(() -> Waypoints.screen(null)); return 1; }));
            dispatcher.register(ClientCommands.literal("hotm").executes(ctx -> { MenuScreen.open(() -> TreeGuideScreen.hotm(null)); return 1; }));
            dispatcher.register(ClientCommands.literal("hotf").executes(ctx -> { MenuScreen.open(() -> TreeGuideScreen.hotf(null)); return 1; }));
            dispatcher.register(ClientCommands.literal("greenhouse").executes(ctx -> { MenuScreen.open(() -> Greenhouse.screen(null)); return 1; }));
            dispatcher.register(ClientCommands.literal("shards").executes(ctx -> { MenuScreen.open(() -> Shards.screen(null)); return 1; })
                    .then(ClientCommands.literal("reset").executes(ctx -> {
                        Shards.resetManual();
                        Tracker.say("§6[Shards] §7Hand-set values cleared; levels come from the Attribute Menu again.");
                        return 1;
                    }))
                    .then(ClientCommands.literal("set").then(ClientCommands.argument("args", StringArgumentType.greedyString()).executes(ctx -> {
                        String[] w = StringArgumentType.getString(ctx, "args").trim().split("\\s+");
                        try {
                            if (w.length < 3) throw new NumberFormatException();
                            int have = Integer.parseInt(w[w.length - 1]), level = Integer.parseInt(w[w.length - 2]);
                            String name = String.join(" ", java.util.Arrays.copyOf(w, w.length - 2));
                            Tracker.say("§6[Shards] " + Shards.set(name, level, have));
                        } catch (NumberFormatException e) {
                            Tracker.say("§6[Shards] §7Use: §f/shards set <shard> <level> <have> §7e.g. §f/shards set Grove 3 5");
                        }
                        return 1;
                    }))));
            dispatcher.register(ClientCommands.literal("dungeon")
                    .executes(ctx -> { MenuScreen.open(() -> Dungeon.screen(Dungeon.inDungeon() ? 0 : 3, null)); return 1; })
                    .then(ClientCommands.literal("chat").executes(ctx -> { Dungeon.sayLive(); return 1; }))
                    .then(ClientCommands.literal("puzzles").executes(ctx -> { MenuScreen.open(() -> Dungeon.screen(1, null)); return 1; }))
                    .then(ClientCommands.literal("team").executes(ctx -> { MenuScreen.open(() -> Dungeon.screen(2, null)); return 1; }))
                    .then(ClientCommands.literal("runs").executes(ctx -> { MenuScreen.open(() -> Dungeon.screen(3, null)); return 1; }))
                    .then(ClientCommands.literal("secrets").executes(ctx -> {
                        Dungeon.Live l = Dungeon.live();
                        Tracker.say("§4[Dungeon] §7Secrets: you §f" + (l.mySecrets() == null ? "?" : l.mySecrets()) + "§7, team §f"
                                + (l.teamSecretsPct() == null ? "?" : l.teamSecretsPct() + "%")
                                + (Secrets.roomTotal >= 0 ? "§7, this room §f" + Secrets.roomFound + "/" + Secrets.roomTotal : ""));
                        return 1;
                    })));
            dispatcher.register(ClientCommands.literal("calc")
                    .then(ClientCommands.argument("sum", StringArgumentType.greedyString()).executes(ctx -> {
                        String sum = StringArgumentType.getString(ctx, "sum");
                        double v = Calc.eval(sum);
                        if (Double.isNaN(v)) { Tracker.say("§6[Calc] §c\"" + sum + "\" isn't a sum. §7Examples: 64x8, 10m/3, (2.5k+500)*4"); return 1; }
                        String r = Calc.format(v);
                        Chat.copy(r);
                        Tracker.say("§6[Calc] §7" + sum + " §f= §a" + r + " §8(" + Fmt.coins(v) + ", copied)");
                        return 1;
                    })));
            dispatcher.register(ClientCommands.literal("skyassist")
                    .executes(ctx -> { MenuScreen.open(ProfitMenus::hub); return 1; })
                    .then(ClientCommands.literal("help").executes(ctx -> { MenuScreen.open(() -> Commands.screen(null)); return 1; }))
                    .then(ClientCommands.literal("settings").executes(ctx -> { SettingsScreen.requestOpen(); return 1; }))
                    .then(ClientCommands.literal("gui").executes(ctx -> { GuiEditor.open(); return 1; }))
                    .then(ClientCommands.literal("setup").executes(ctx -> { SetupScreen.requestOpen(); return 1; })));
            dispatcher.register(ClientCommands.literal("profitsettings").executes(ctx -> { SettingsScreen.requestOpen(); return 1; }));
        });
        LOG.info("SkyAssist loaded");
    }

    /** Draws a 16px item icon shrunk to fit a 10px line. Silently skipped if not possible. */
    private static void drawIcon(Object graphics, Object pose, Object stack, int x, int y) {
        if (Reflect.call(pose, "pushMatrix") == Reflect.FAIL) return;
        Reflect.call(pose, "translate", (float) x, (float) (y - 1));
        Reflect.call(pose, "scale", 0.625f, 0.625f);
        Object r = Reflect.call(graphics, new String[]{"item", "renderItem", "fakeItem", "renderFakeItem"}, stack, 0, 0);
        Debug.iconsWork = r != Reflect.FAIL;
        Reflect.call(pose, "popMatrix");
    }

    // ---------- commands ----------

    private static String typeFor(String fixed) { return fixed != null ? fixed : Tracker.shownType(); }

    private static LiteralArgumentBuilder<FabricClientCommandSource> buildCommand(String name, String fixed) {
        return ClientCommands.literal(name)
                .executes(ctx -> { String t = typeFor(fixed); MenuScreen.open(() -> ProfitMenus.session(t, null)); return 1; })
                .then(ClientCommands.literal("chat").executes(ctx -> { showCurrent(typeFor(fixed)); return 1; }))
                .then(ClientCommands.literal("menu").executes(ctx -> { MenuScreen.open(ProfitMenus::hub); return 1; }))
                .then(ClientCommands.literal("help").executes(ctx -> { MenuScreen.open(() -> Commands.screen(null)); return 1; }))
                .then(ClientCommands.literal("commands").executes(ctx -> { MenuScreen.open(() -> Commands.screen(null)); return 1; }))
                .then(ClientCommands.literal("reset").executes(ctx -> {
                    String type = typeFor(fixed);
                    Session s = type == null ? null : Tracker.sessions.get(type);
                    if (s == null) Tracker.say("§6[SkyAssist] §7No " + (type == null ? "" : type + " ") + "session running.");
                    else Tracker.endSession(s, true);
                    return 1;
                }))
                .then(ClientCommands.literal("history")
                        .executes(ctx -> { MenuScreen.open(() -> ProfitMenus.history(fixed, null)); return 1; })
                        .then(ClientCommands.literal("chat").executes(ctx -> { showHistory(fixed, 10); return 1; }))
                        .then(ClientCommands.argument("count", IntegerArgumentType.integer(1, 200))
                                .executes(ctx -> { showHistory(fixed, IntegerArgumentType.getInteger(ctx, "count")); return 1; })))
                .then(ClientCommands.literal("suggest")
                        .executes(ctx -> {
                            int tab = Tracker.isMiningType(typeFor(fixed)) ? 1 : 0;
                            MenuScreen.open(() -> ProfitMenus.suggest(tab, null));
                            return 1;
                        })
                        .then(ClientCommands.literal("chat").executes(ctx -> { suggest(typeFor(fixed)); return 1; })))
                .then(ClientCommands.literal("total")
                        .executes(ctx -> { MenuScreen.open(() -> ProfitMenus.totals(null)); return 1; })
                        .then(ClientCommands.literal("chat").executes(ctx -> { showTotals(fixed); return 1; })))
                .then(ClientCommands.literal("copy").executes(ctx -> { copy(typeFor(fixed)); return 1; }))
                .then(ClientCommands.literal("ignore")
                        .then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(ctx -> {
                            String item = StringArgumentType.getString(ctx, "item").trim();
                            if (!Config.get().ignoredItems.contains(item)) Config.get().ignoredItems.add(item);
                            Config.save();
                            Tracker.say("§6[SkyAssist] §7Ignoring §f" + item + "§7. Undo with /profit unignore " + item);
                            return 1;
                        })))
                .then(ClientCommands.literal("unignore")
                        .then(ClientCommands.argument("item", StringArgumentType.greedyString()).executes(ctx -> {
                            String item = StringArgumentType.getString(ctx, "item").trim();
                            boolean removed = Config.get().ignoredItems.remove(item);
                            Config.save();
                            Tracker.say("§6[SkyAssist] §7" + (removed ? "Counting §f" + item + " §7again." : "§f" + item + " §7wasn't ignored."));
                            return 1;
                        })))
                .then(ClientCommands.literal("scale")
                        .then(ClientCommands.argument("size", DoubleArgumentType.doubleArg(0.5, 3.0)).executes(ctx -> {
                            Panels.Pos pos = Panels.get(Panels.key("main", Tracker.shownType()));
                            pos.scale = DoubleArgumentType.getDouble(ctx, "size");
                            Panels.save();
                            Tracker.say("§6[SkyAssist] §7HUD scale set to §f" + pos.scale + " §8(middle-click a panel with chat open to size each one)");
                            return 1;
                        })))
                .then(ClientCommands.literal("settings").executes(ctx -> { SettingsScreen.requestOpen(); return 1; }))
                .then(ClientCommands.literal("debug").executes(ctx -> { Debug.show(); return 1; }))
                .then(ClientCommands.literal("report").executes(ctx -> { report(); return 1; }))
                .then(ClientCommands.literal("perf").executes(ctx -> { Perf.show(); return 1; })
                        .then(ClientCommands.literal("reset").executes(ctx -> { Perf.reset(); Tracker.say("§6[SkyAssist] §7Performance numbers reset."); return 1; })))
                .then(ClientCommands.literal("setup").executes(ctx -> { SetupScreen.requestOpen(); return 1; }))
                .then(ClientCommands.literal("dedupe").executes(ctx -> {
                    Dedupe.turnOff();
                    Tracker.say("§6[SkyAssist] §7Done. Turn them back on any time in the settings.");
                    return 1;
                }))
                .then(ClientCommands.literal("gui")
                        .executes(ctx -> { GuiEditor.open(); return 1; })
                        .then(ClientCommands.literal("reset").executes(ctx -> {
                            Panels.reset();
                            Tracker.say("§6[SkyAssist] §7HUD layout reset.");
                            return 1;
                        }))
                        .then(ClientCommands.literal("preset").then(ClientCommands.argument("layout", StringArgumentType.word())
                                .suggests((c, b) -> { for (String n : new String[]{"left", "right", "split", "compact"}) b.suggest(n); return b.buildFuture(); })
                                .executes(ctx -> {
                                    var win = Minecraft.getInstance().getWindow();
                                    String n = StringArgumentType.getString(ctx, "layout").toLowerCase();
                                    if (Panels.preset(n, win.getGuiScaledWidth(), win.getGuiScaledHeight()))
                                        Tracker.say("§6[SkyAssist] §7Layout §f" + n + " §7applied. Fine-tune with /profit gui.");
                                    else Tracker.say("§6[SkyAssist] §7Layouts: §fleft, right, split, compact");
                                    return 1;
                                }))))
                .then(ClientCommands.literal("note")
                        .then(ClientCommands.argument("text", StringArgumentType.greedyString()).executes(ctx -> {
                            String type = typeFor(fixed);
                            Session s = type == null ? null : Tracker.sessions.get(type);
                            if (s == null) { Tracker.say("§6[SkyAssist] §7No session running to add a note to."); return 1; }
                            s.note = StringArgumentType.getString(ctx, "text");
                            Tracker.say("§6[SkyAssist] §7Note added to this " + s.type + " session: §f" + s.note);
                            return 1;
                        })))
                .then(ClientCommands.literal("profile")
                        .then(ClientCommands.literal("list").executes(ctx -> { Profiles.list(); return 1; }))
                        .then(ClientCommands.literal("export").executes(ctx -> { Profiles.export(); return 1; }))
                        .then(ClientCommands.literal("save").then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(ctx -> { Profiles.save(StringArgumentType.getString(ctx, "name")); return 1; })))
                        .then(ClientCommands.literal("load").then(ClientCommands.argument("name", StringArgumentType.word())
                                .executes(ctx -> { Profiles.load(StringArgumentType.getString(ctx, "name")); return 1; })))
                        .then(ClientCommands.literal("import").then(ClientCommands.argument("code", StringArgumentType.greedyString())
                                .executes(ctx -> { Profiles.importCode(StringArgumentType.getString(ctx, "code")); return 1; }))))
                .then(ClientCommands.literal("export").executes(ctx -> { exportCsv(); return 1; }))
                .then(ClientCommands.literal("secrets").executes(ctx -> {
                    Config.get().secretFinder = !Config.get().secretFinder;
                    Config.save();
                    Tracker.say("§6[SkyAssist] §7Dungeon secret finder " + (Config.get().secretFinder ? "§aon" : "§coff"));
                    return 1;
                }))
                .then(ClientCommands.literal("icons").executes(ctx -> {
                    Config.get().hudIcons = !Config.get().hudIcons;
                    Config.save();
                    Tracker.say("§6[SkyAssist] §7Item icons " + (Config.get().hudIcons ? "§aon" : "§coff"));
                    return 1;
                }))
                .then(ClientCommands.literal("edit").executes(ctx -> { GuiEditor.open(); return 1; }))
                .then(ClientCommands.literal("hud").executes(ctx -> {
                    Config.get().hudEnabled = !Config.get().hudEnabled;
                    Config.save();
                    Tracker.say("§6[SkyAssist] §7HUD " + (Config.get().hudEnabled ? "§aon" : "§coff"));
                    return 1;
                }))
                .then(ClientCommands.literal("details").executes(ctx -> {
                    Config.get().hudDetails = !Config.get().hudDetails;
                    Config.save();
                    Tracker.say("§6[SkyAssist] §7HUD " + (Config.get().hudDetails
                            ? "now shows §adetails §7(stats, powder, commissions...)" : "now shows §aprofit only"));
                    return 1;
                }))
                .then(ClientCommands.literal("move")
                        .then(ClientCommands.argument("x", IntegerArgumentType.integer(0))
                                .then(ClientCommands.argument("y", IntegerArgumentType.integer(0)).executes(ctx -> {
                                    Panels.Pos pos = Panels.get(Panels.key("main", Tracker.shownType()));
                                    pos.x = IntegerArgumentType.getInteger(ctx, "x");
                                    pos.y = IntegerArgumentType.getInteger(ctx, "y");
                                    Panels.save();
                                    Tracker.say("§6[SkyAssist] §7HUD moved.");
                                    return 1;
                                }))))
                .then(ClientCommands.literal("prices").executes(ctx -> {
                    Prices.refresh();
                    Tracker.say("§6[SkyAssist] §7Refreshing prices...");
                    return 1;
                }))
                .then(ClientCommands.literal("reload").executes(ctx -> {
                    Config.load();
                    Tracker.say("§6[SkyAssist] §7Config reloaded.");
                    return 1;
                }));
    }

    private static void showCurrent(String type) {
        Session s = type == null ? null : Tracker.sessions.get(type);
        if (s == null) {
            Tracker.say("§6[SkyAssist] §7No " + (type == null ? "" : type + " ") + "session running. §8(try /profit history)");
            return;
        }
        for (String line : Hud.profitLines(s)) Tracker.say(line);
    }

    private static void suggest(String type) {
        boolean farming = Tracker.FARMING.equals(type);
        if (!farming && !Tracker.isMiningType(type)) {
            Tracker.say("§6[SkyAssist] §7Suggestions are for farming and mining: §f/farmprofit suggest §7or §f/miningprofit suggest");
            return;
        }
        if (!Prices.loaded()) { Tracker.say("§6[SkyAssist] §7Prices are still loading, try again in a moment."); return; }
        var opts = farming ? Suggest.farming() : Suggest.mining();
        if (opts.isEmpty()) {
            Tracker.say("§6[SkyAssist] §7Can't estimate yet" + (farming ? "." : ": add Mining Speed to the Stats tab widget."));
            return;
        }
        Tracker.say("§6§l[SkyAssist] Best " + (farming ? "crops" : "ores here") + " right now §8(estimates from live prices + your stats)");
        for (int i = 0; i < Math.min(10, opts.size()); i++) {
            var o = opts.get(i);
            Tracker.say("§8" + (i + 1) + ". §a" + o.name() + " §6~" + Fmt.coins(o.perHour()) + "/h §8(sell as "
                    + o.sellAs() + ", " + String.format(java.util.Locale.US, "%.2f", o.perItem()) + " each)");
        }
        String m = Election.mayor;
        if (m != null) Tracker.say("§7Mayor: §d" + m + " §8(" + String.join(", ", Election.perks) + ")");
        if (farming) { String c = Contests.hudLine(); if (c != null) Tracker.say(c); }
        Tracker.say("§8Based on " + (farming ? "your blocks/s and Farming Fortune" : "your Mining Speed and fortune")
                + "; pests, rare drops and powder aren't included.");
    }

    /** Everything needed to fix detection problems, copied to the clipboard to paste into a chat with Claude. */
    private static void report() {
        StringBuilder r = new StringBuilder("SkyAssist report\n");
        r.append("build ").append(UpdateCheck.thisCommit()).append(", area=").append(Tracker.areaName)
                .append(", hud=").append(Tracker.area).append(", modapi=").append(HypixelLocation.active)
                .append(" mode=").append(HypixelLocation.mode).append(" map=").append(HypixelLocation.map).append('\n');
        r.append("tab keys: ").append(String.join(", ", Tracker.tab.keySet())).append('\n');
        r.append("purse=").append(Tracker.purse).append(" floor=").append(Tracker.dungeonFloor)
                .append(" prices=").append(Prices.bazaarCount()).append('/').append(Prices.itemCount()).append('/').append(Prices.binCount()).append('\n');
        r.append("icons=").append(Debug.iconsWork).append(" scale=").append(Debug.scaleWorks).append(" mouse=").append(Debug.mouseWorks).append('\n');
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
        if (Chat.copy(r.toString())) Tracker.say("§6[SkyAssist] §7Report copied to your clipboard. Paste it into your chat with Claude.");
        else Tracker.say("§6[SkyAssist] §7Couldn't copy. Send config/skyassist/unrecognised-messages.txt and a /profit debug screenshot instead.");
    }

    static void exportCsv() {
        try {
            StringBuilder csv = new StringBuilder("date,activity,main,active_minutes,profit,profit_per_hour,runs_or_bosses,note\n");
            var fmt = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
            for (Session s : History.all()) {
                String type = Tracker.normalType(s.type);
                int count = s.runs > 0 ? s.runs : (Tracker.COMBAT.equals(type) ? s.totalBreaks() : 0);
                csv.append(fmt.format(new java.util.Date(s.start))).append(',').append(type).append(",\"")
                        .append(String.valueOf(s.mainCrop).replace("\"", "'")).append("\",")
                        .append(s.durationMs(0) / 60000).append(',').append(Math.round(s.profit)).append(',')
                        .append(Math.round(s.profitPerHour)).append(',').append(count).append(",\"")
                        .append(s.note == null ? "" : s.note.replace("\"", "'")).append("\"\n");
            }
            java.nio.file.Path file = Config.DIR.resolve("history.csv");
            java.nio.file.Files.writeString(file, csv.toString());
            Tracker.say("§6[SkyAssist] §7Exported " + History.all().size() + " sessions to §f" + file.toAbsolutePath()
                    + " §7(open it in Excel / Google Sheets for graphs).");
        } catch (Exception e) {
            Tracker.say("§6[SkyAssist] §cExport failed: " + e.getMessage());
        }
    }

    /** "▂▃▅▇▆" style bar of profit/h over sessions, oldest to newest. */
    static String sparkline(List<Session> sessions) {
        String bars = "▁▂▃▄▅▆▇█";
        double max = 0;
        for (Session s : sessions) max = Math.max(max, s.profitPerHour);
        if (max <= 0) return "";
        StringBuilder sb = new StringBuilder();
        for (Session s : sessions) sb.append(bars.charAt((int) Math.min(7, Math.max(0, Math.round(s.profitPerHour / max * 7)))));
        return sb.toString();
    }

    private static void showTotals(String fixed) {
        var all = Totals.all();
        if (all.isEmpty()) {
            Tracker.say("§6[SkyAssist] §7No finished sessions yet.");
            return;
        }
        Tracker.say("§6§l[SkyAssist] Lifetime totals:");
        for (var e : all.entrySet()) {
            if (fixed != null && !fixed.equals(e.getKey())) continue;
            var t = e.getValue();
            double h = t.ms / 3_600_000.0;
            Tracker.say(" " + Hud.title(e.getKey()).replace("§l", "").replaceAll(" §7\\(.*\\)", "")
                    + " §f" + Fmt.duration(t.ms) + " §7in " + t.sessions + " sessions, §6" + Fmt.coins(t.profit)
                    + " §7(§6" + (h > 0 ? Fmt.coins(t.profit / h) : "0") + "/h§7)");
        }
    }

    private static void copy(String type) {
        Session s = type == null ? null : Tracker.sessions.get(type);
        if (s == null) { Tracker.say("§6[SkyAssist] §7Nothing to copy."); return; }
        long now = System.currentTimeMillis();
        String text = s.type + ": " + Fmt.coins(s.value()) + " coins in " + Fmt.duration(s.durationMs(now))
                + " (" + Fmt.coins(s.perHour(now)) + "/h)";
        if (Chat.copy(text)) Tracker.say("§6[SkyAssist] §7Copied: §f" + text);
        else Tracker.say("§6[SkyAssist] §7Couldn't copy, here it is: §f" + text);
    }

    private static boolean matches(Session s, String fixed) {
        if (fixed == null) return true;
        String t = s.type == null ? Tracker.FARMING : s.type;
        if (Tracker.MINING.equals(fixed)) return Tracker.isMiningType(t);
        return fixed.equals(t);
    }

    private static void showHistory(String fixed, int count) {
        List<Session> all = History.all().stream().filter(s -> matches(s, fixed)).toList();
        if (all.isEmpty()) {
            Tracker.say("§6[SkyAssist] §7No finished " + (fixed == null ? "" : fixed.toLowerCase() + " ") + "sessions yet.");
            return;
        }
        Tracker.say("§6§l[SkyAssist] Last " + Math.min(count, all.size()) + (fixed == null ? "" : " " + fixed.toLowerCase()) + " sessions:");
        var fmt = new java.text.SimpleDateFormat("dd.MM HH:mm");
        for (int i = all.size() - 1; i >= Math.max(0, all.size() - count); i--) {
            Session s = all.get(i);
            String type = s.type == null ? Tracker.FARMING : s.type;
            Tracker.say("§8" + fmt.format(new java.util.Date(s.start)) + " " + Hud.title(type).replace("§l", "")
                    + " §a" + s.mainCrop + " §7" + Fmt.duration(s.durationMs(0)) + " §6" + Fmt.coins(s.profit)
                    + " §7(§6" + Fmt.coins(s.profitPerHour) + "/h§7)");
            if (s.note != null) Tracker.say("   §7Note: §f" + s.note);
            if (s.shards != null && !s.shards.isEmpty()) Tracker.say(list("   §bShards: ", s.shards));
            if (s.rareDrops != null && !s.rareDrops.isEmpty()) Tracker.say(list("   §dRare: ", s.rareDrops));
        }
        List<Session> recent = all.subList(Math.max(0, all.size() - 30), all.size());
        String spark = sparkline(recent);
        if (!spark.isEmpty()) Tracker.say("§7Profit/h trend (last " + recent.size() + "): §e" + spark);
        Tracker.say("§8Full details: /profit export (CSV) or .minecraft/config/skyassist/history.json");
    }

    private static String list(String prefix, java.util.Map<String, Integer> map) {
        StringBuilder r = new StringBuilder(prefix);
        int n = 0;
        for (var e : map.entrySet()) {
            if (n++ > 0) r.append("§7, ").append(prefix.contains("§b") ? "§b" : "§d");
            r.append(e.getValue()).append("x ").append(e.getKey());
        }
        return r.toString();
    }
}
