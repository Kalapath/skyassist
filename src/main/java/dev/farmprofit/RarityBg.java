package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Colors each item's slot by its SkyBlock rarity, in menus and on the hotbar. */
public final class RarityBg {
    /** A rarity line: the rarity word, then optionally the item type in capitals ("LEGENDARY SWORD", "a RARE ACCESSORY a"). */
    private static final Pattern RARITY = Pattern.compile("^(?:a )?(VERY SPECIAL|SPECIAL|ULTIMATE|DIVINE|MYTHIC|LEGENDARY|EPIC|RARE|UNCOMMON|COMMON|ADMIN)(?: [A-Z][A-Z ]*)?(?: a)?$");
    private static final Map<ItemStack, Integer> CACHE = new WeakHashMap<>();

    /** RGB of the item's rarity (Hypixel's colors), or -1 if it has none. */
    public static int color(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        Integer c = CACHE.get(stack);
        if (c != null) return c;
        int found = -1;
        List<String> lore = ItemIds.lore(stack);
        for (int i = lore.size() - 1; i >= 0 && found < 0; i--) {                 // whole description, bottom up
            Matcher m = RARITY.matcher(lore.get(i).trim());
            if (m.find()) found = switch (m.group(1)) {
                case "COMMON" -> 0xFFFFFF;
                case "UNCOMMON" -> 0x55FF55;
                case "RARE" -> 0x5555FF;
                case "EPIC" -> 0xAA00AA;
                case "LEGENDARY" -> 0xFFAA00;
                case "MYTHIC" -> 0xFF55FF;
                case "DIVINE" -> 0x55FFFF;
                case "SPECIAL", "VERY SPECIAL" -> 0xFF5555;
                case "ULTIMATE" -> 0xAA0000;
                default -> 0xAA0000;
            };
        }
        CACHE.put(stack, found);
        return found;
    }

    private static int argb(int rgb, boolean overItem) {
        int a = Math.max(0, Math.min(255, Config.get().rarityOpacity));
        if (overItem) a = a * 2 / 5;                      // on top of the item: lighter so it stays readable
        return (a << 24) | rgb;
    }

    private static int intField(Object o, String name) {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass()) {
            try { Field f = k.getDeclaredField(name); f.setAccessible(true); return f.getInt(o); } catch (Exception ignored) {}
        }
        return Integer.MIN_VALUE;
    }

    /** Draws an item (and its stack count) on top of a color. False if this version can't. */
    /** null = not tried yet, false = this Minecraft version can't redraw items from here (then we only tint lightly). */
    static Boolean canDrawItems;

    static boolean drawItem(Object g, ItemStack stack, int x, int y) {
        if (canDrawItems == Boolean.FALSE) return false;
        Object r = Reflect.call(g, new String[]{"item", "renderItem", "fakeItem", "renderFakeItem"}, stack, x, y);
        if (r == Reflect.FAIL) { canDrawItems = false; return false; }
        canDrawItems = true;
        Object font = Minecraft.getInstance().font;
        Reflect.call(g, new String[]{"itemDecorations", "renderItemDecorations"}, font, stack, x, y);
        return true;
    }

    /** Menus: a colored square under every item with a rarity (drawn before the items, so items and tooltips stay on top). */
    static void drawBehind(Object screen, Object g) {
        if (!Config.get().rarityBackground) return;
        int left = intField(screen, "leftPos"), top = intField(screen, "topPos");
        if (left == Integer.MIN_VALUE) return;
        Object slots = Reflect.field(Reflect.call(screen, "getMenu"), "slots");
        if (!(slots instanceof List<?> list)) return;
        boolean searching = InvSearch.active();
        for (Object slot : list) {
            Object st = Reflect.call(slot, "getItem");
            if (!(st instanceof ItemStack stack)) continue;
            int c = color(stack);
            if (c < 0) continue;
            if (searching && !InvSearch.matches(stack)) continue;                 // search darkens these instead
            int sx = intField(slot, "x"), sy = intField(slot, "y");
            if (sx == Integer.MIN_VALUE) continue;
            Reflect.call(g, "fill", left + sx, top + sy, left + sx + 16, top + sy + 16, argb(c, false));
        }
    }

    /** Hotbar: colored square, then the item again on top. */
    static void drawHotbar(Minecraft mc, Object g) {
        for (int[] b : hotbarBoxes(mc)) {
            if (b == null) continue;
            if (canDrawItems == Boolean.FALSE) { Reflect.call(g, "fill", b[0], b[1], b[2], b[3], (b[4] & 0xFFFFFF) | 0x40000000); continue; }
            Reflect.call(g, "fill", b[0], b[1], b[2], b[3], b[4]);
            drawItem(g, mc.player.getInventory().getItem(b[5]), b[0], b[1]);
        }
    }

    static int[][] hotbarBoxes(Minecraft mc) {
        if (!Config.get().rarityBackground || !Config.get().rarityHotbar || mc.player == null) return new int[0][];
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        int[][] out = new int[9][];
        for (int i = 0; i < 9; i++) {
            int c = color(mc.player.getInventory().getItem(i));
            if (c < 0) continue;
            int x = w / 2 - 90 + i * 20 + 2, y = h - 19;
            out[i] = new int[]{x, y, x + 16, y + 16, argb(c, false), i};
        }
        return out;
    }

    private RarityBg() {}
}
