package dev.farmprofit;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reads Hypixel's SkyBlock item ID (e.g. ENCHANTED_WHEAT) and lore from an item. */
public final class ItemIds {
    /** Display name -> real SkyBlock ID, learned from items you've held. Used to price sack items etc. */
    public static final Map<String, String> LEARNED = new ConcurrentHashMap<>();

    /** The SkyBlock ID stored on the item, or null. */
    public static String of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return null;
            Object tag = Reflect.call(data, new String[]{"copyTag", "getUnsafe", "tag"});
            if (tag == Reflect.FAIL || tag == null) return null;
            Object id = Reflect.call(tag, "getStringOr", "id", "");
            if (id == Reflect.FAIL) id = Reflect.call(tag, "getString", "id");
            if (id instanceof java.util.Optional<?> opt) id = opt.orElse(null);
            return id instanceof String s && !s.isEmpty() ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Remembers name -> ID (skips generic IDs shared by many different items). */
    public static void learn(String name, ItemStack stack) {
        if (LEARNED.containsKey(name)) return;
        String id = of(stack);
        if (id == null || id.equals("PET") || id.equals("ENCHANTED_BOOK") || id.equals("POTION") || id.equals("RUNE")) return;
        LEARNED.put(name, id);
    }

    // ---------- what an item is really worth by (books, pets) ----------

    /** The item's custom-data compound (Hypixel's "ExtraAttributes"), or null. */
    private static Object tagOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        try {
            var data = stack.get(DataComponents.CUSTOM_DATA);
            if (data == null) return null;
            Object tag = Reflect.call(data, new String[]{"copyTag", "getUnsafe", "tag"});
            return tag == Reflect.FAIL ? null : tag;
        } catch (Throwable t) {
            return null;
        }
    }

    /** Newer Minecraft versions return Optional from the tag getters, older ones the value itself. */
    private static Object unwrap(Object o) {
        if (o == Reflect.FAIL) return null;
        if (o instanceof java.util.Optional<?> opt) return opt.orElse(null);
        return o;
    }

    private static String text(Object tag, String key) {
        if (tag == null) return null;
        Object v = Reflect.call(tag, "getStringOr", key, "");
        if (v == Reflect.FAIL) v = Reflect.call(tag, "getString", key);
        v = unwrap(v);
        return v instanceof String s && !s.isEmpty() ? s : null;
    }

    /** The enchantments on a book, e.g. {ultimate_wise=5}. Empty if they can't be read. */
    static Map<String, Integer> enchantsOf(ItemStack stack) {
        Map<String, Integer> out = new LinkedHashMap<>();
        try {
            Object tag = tagOf(stack);
            if (tag == null) return out;
            Object ench = unwrap(Reflect.call(tag, new String[]{"getCompoundOrEmpty", "getCompound"}, "enchantments"));
            if (ench == null) return out;
            Object keys = Reflect.call(ench, new String[]{"keySet", "getAllKeys"});
            if (!(keys instanceof Iterable<?> it)) return out;
            for (Object k : it) {
                if (!(k instanceof String key)) continue;
                Object lv = unwrap(Reflect.call(ench, "getIntOr", key, 0));
                if (lv == null) lv = unwrap(Reflect.call(ench, "getInt", key));
                if (lv instanceof Number n && n.intValue() > 0) out.put(key, n.intValue());
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};

    /** "TYPE;tier" (e.g. GOLDEN_DRAGON;4), the key the lowest-BIN list uses for a pet, or null. */
    static String petKey(ItemStack stack) {
        try {
            String json = text(tagOf(stack), "petInfo");
            if (json == null) return null;
            var o = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String type = o.get("type").getAsString(), tier = o.get("tier").getAsString();
            for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equals(tier)) return type + ";" + i;
        } catch (Throwable ignored) {}
        return null;
    }

    /**
     * The id to price an item by: its SkyBlock id, except that an enchanted book is priced by its enchantment
     * (ENCHANTMENT_ULTIMATE_WISE_5) and a pet by its type and rarity (GOLDEN_DRAGON;4). Null if it has none.
     */
    static String priceId(ItemStack stack) {
        String id = of(stack);
        if (id == null) return null;
        if (id.equals("ENCHANTED_BOOK")) {
            var e = enchantsOf(stack);
            if (e.size() != 1) return null;
            var en = e.entrySet().iterator().next();
            return "ENCHANTMENT_" + en.getKey().toUpperCase(Locale.ROOT) + "_" + en.getValue();
        }
        if (id.equals("PET")) return petKey(stack);
        return id;
    }

    /** Plain-text lore lines of an item. */
    public static List<String> lore(ItemStack stack) {
        List<String> out = new ArrayList<>();
        try {
            var lore = stack.get(DataComponents.LORE);
            if (lore == null) return out;
            for (Component c : lore.lines()) out.add(Tracker.strip(c.getString()));
        } catch (Throwable ignored) {}
        return out;
    }

    private ItemIds() {}
}
