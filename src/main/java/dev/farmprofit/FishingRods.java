package dev.farmprofit;

import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;

/** Is this a rod you actually fish with? (Grappling Hook, Soul Whip, Flaming Flay... are rods too but aren't fishing.) */
public final class FishingRods {
    private static final Set<String> NOT_FISHING = Set.of("Grappling Hook", "Soul Whip", "Flaming Flay", "Spirit Sceptre");

    public static boolean isRealRod(ItemStack stack) {
        String name = Tracker.strip(stack.getHoverName().getString()).trim();
        for (String n : NOT_FISHING) if (name.contains(n)) return false;
        // SkyBlock fishing rods say "... FISHING ROD" in their rarity line; a Grappling Hook etc. doesn't
        List<String> lore = ItemIds.lore(stack);
        boolean hasRarity = false;
        for (int i = lore.size() - 1; i >= 0; i--) {
            String l = lore.get(i).trim();
            if (l.contains("FISHING ROD") || l.contains("FISHING WEAPON")) return true;
            if (l.matches("^(?:a )?(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL)( .*)?$")) hasRarity = true;
        }
        // a SkyBlock item with a rarity line but no "FISHING ROD": not a fishing rod. A plain vanilla rod: yes.
        return !hasRarity;
    }

    private FishingRods() {}
}
