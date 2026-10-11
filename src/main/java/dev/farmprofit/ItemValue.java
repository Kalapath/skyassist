package dev.farmprofit;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Upgrades ≈ 5.2M" in item tooltips: what the upgrades on an item would cost at Bazaar prices. Counts enchantments (as books),
 * Hot / Fuming Potato Books, Recombobulator, gemstones, drill parts and a few other books that the item records. It is a rough
 * extra, NOT a full item price: reforges, stars, pets, runes and the item itself aren't included. Read from the item's own data.
 */
public final class ItemValue {
    /** NBT counter -> the item it came from. */
    private static final String[][] COUNTERS = {
            {"art_of_war_count", "THE_ART_OF_WAR"}, {"wood_singularity_count", "WOOD_SINGULARITY"}, {"tuned_transmission", "TRANSMISSION_TUNER"},
            {"farming_for_dummies_count", "FARMING_FOR_DUMMIES"}, {"jalapeno_count", "JALAPENO_BOOK"},
            {"mana_disintegrator_count", "MANA_DISINTEGRATOR"}, {"artOfPeaceApplied", "THE_ART_OF_PEACE"}};
    private static final String[] DRILL_PARTS = {"drill_part_upgrade_module", "drill_part_fuel_tank", "drill_part_engine"};
    private static final Set<String> FLEX_SLOTS = Set.of("UNIVERSAL", "COMBAT", "OFFENSIVE", "DEFENSIVE", "MINING");
    private static final Set<String> SKIP = Set.of("ENCHANTED_BOOK", "PET", "RUNE", "POTION");

    /** Tooltips are rebuilt every frame while you hover: keep the last result for a second. */
    private static Object lastStack;
    private static long lastAt;
    private static List<String> lastLines = List.of();

    public static void add(ItemStack stack, List<Component> lines) {
        if (!Config.get().upgradeValue || stack == null || stack.isEmpty() || !Prices.loaded()) return;
        long now = System.currentTimeMillis();
        if (stack != lastStack || now - lastAt > 1000) {
            lastLines = compute(stack);
            lastStack = stack;
            lastAt = now;
        }
        for (String s : lastLines) lines.add(Component.literal(s));
    }

    private static List<String> compute(ItemStack stack) {
        Object tag = tag(stack);
        if (tag == null) return List.of();
        String id = str(tag, "id");
        if (id.isEmpty() || SKIP.contains(id)) return List.of();
        Map<String, Double> parts = new LinkedHashMap<>();

        Object ench = compound(tag, "enchantments");
        if (ench != null) {
            double sum = 0;
            for (String key : keys(ench)) {
                int level = intOf(ench, key);
                if (level > 0) sum += enchantValue(key, level);
            }
            put(parts, "enchants", sum);
        }
        int potatoes = intOf(tag, "hot_potato_count");
        if (potatoes > 0) put(parts, "potato books", Math.min(potatoes, 10) * price("HOT_POTATO_BOOK") + Math.max(0, potatoes - 10) * price("FUMING_POTATO_BOOK"));
        if (intOf(tag, "rarity_upgrades") > 0) put(parts, "recomb", price("RECOMBOBULATOR_3000"));

        Object gems = compound(tag, "gems");
        if (gems != null) {
            double sum = 0;
            for (String slot : keys(gems)) {
                if (slot.endsWith("_gem") || slot.equals("unlocked_slots")) continue;
                String quality = quality(gems, slot);
                int us = slot.lastIndexOf('_');
                String kind = us > 0 ? slot.substring(0, us).toUpperCase(Locale.ROOT) : slot.toUpperCase(Locale.ROOT);
                String type = FLEX_SLOTS.contains(kind) ? str(gems, slot + "_gem").toUpperCase(Locale.ROOT) : kind;
                if (!quality.isEmpty() && !type.isEmpty()) sum += price(quality + "_" + type + "_GEM");
            }
            put(parts, "gems", sum);
        }

        double other = 0;
        for (String[] c : COUNTERS) {
            int n = intOf(tag, c[0]);
            if (n > 0) other += n * price(c[1]);
        }
        for (String part : DRILL_PARTS) {
            String pid = str(tag, part);
            if (!pid.isEmpty()) other += price(pid.toUpperCase(Locale.ROOT));
        }
        put(parts, "other", other);

        double total = 0;
        for (double v : parts.values()) total += v;
        if (total < 100) return List.of();
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(parts.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        StringBuilder detail = new StringBuilder();
        for (int i = 0; i < Math.min(3, sorted.size()); i++) {
            if (i > 0) detail.append(", ");
            detail.append(sorted.get(i).getKey()).append(' ').append(Fmt.coins(sorted.get(i).getValue()));
        }
        return List.of("§6Upgrades ≈ §f" + Fmt.coins(total) + " §8(" + detail + ")");
    }

    private static void put(Map<String, Double> parts, String name, double value) {
        if (value > 0) parts.put(name, value);
    }

    private static double price(String id) {
        double p = Prices.priceOfId(id);
        return p > 0 ? p : 0;
    }

    /** An enchantment book of that level; if none is sold, built from the level below (two books make the next level). */
    private static double enchantValue(String key, int level) {
        String base = "ENCHANTMENT_" + key.toUpperCase(Locale.ROOT) + "_";
        double p = price(base + level);
        if (p > 0) return p;
        for (int l = level - 1, mult = 2; l >= 1 && mult <= 8; l--, mult *= 2) {
            double q = price(base + l);
            if (q > 0) return q * mult;
        }
        return 0;
    }

    /** "FLAWLESS" from a gem slot, which is either the text itself or an object with a "quality". */
    private static String quality(Object gems, String slot) {
        String q = str(gems, slot);
        if (q.isEmpty()) {
            Object sub = compound(gems, slot);
            if (sub != null) q = str(sub, "quality");
        }
        return q.toUpperCase(Locale.ROOT);
    }

    // ---------- reading the item's data (looked up by name because Mojang keeps renaming these) ----------

    private static Object tag(ItemStack stack) {
        var data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) return null;
        Object tag = Reflect.call(data, new String[]{"copyTag", "getUnsafe", "tag"});
        return tag == Reflect.FAIL ? null : tag;
    }

    private static Object compound(Object tag, String key) {
        Object r = Reflect.call(tag, "getCompound", key);
        if (r instanceof java.util.Optional<?> o) r = o.orElse(null);
        return r == Reflect.FAIL ? null : r;
    }

    private static int intOf(Object tag, String key) {
        Object r = Reflect.call(tag, "getIntOr", key, 0);
        if (r instanceof Number n) return n.intValue();
        r = Reflect.call(tag, "getInt", key);
        if (r instanceof java.util.Optional<?> o) r = o.isPresent() ? o.get() : Integer.valueOf(0);
        return r instanceof Number n ? n.intValue() : 0;
    }

    private static String str(Object tag, String key) {
        Object r = Reflect.call(tag, "getStringOr", key, "");
        if (r instanceof String s) return s;
        r = Reflect.call(tag, "getString", key);
        if (r instanceof java.util.Optional<?> o) r = o.isPresent() ? o.get() : "";
        return r instanceof String s ? s : "";
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> keys(Object tag) {
        Object r = Reflect.call(tag, new String[]{"keySet", "getAllKeys"});
        return r instanceof Collection<?> c ? (Collection<String>) c : List.of();
    }

    private ItemValue() {}
}
