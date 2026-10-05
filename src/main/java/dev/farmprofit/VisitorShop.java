package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Garden visitors: when you open a visitor, lists what they want with a clickable [Bazaar] for each item:
 * it opens the item's Bazaar page and copies the amount you still need (the purchase itself is your click).
 */
public final class VisitorShop {
    private static final Pattern NEED = Pattern.compile("^\\s*(.+?)\\s+x([\\d,]+)\\s*$");
    private static String lastTitle = "";
    private static long lastAt;

    static void scanMenu(String title, List<ItemStack> items) {
        if (!Config.get().visitorBazaar || !Tracker.FARMING.equals(Tracker.area)) return;
        for (ItemStack is : items) {
            if (!Tracker.strip(is.getHoverName().getString()).contains("Accept Offer")) continue;
            Map<String, Integer> need = new LinkedHashMap<>();
            boolean in = false;
            for (String l : ItemIds.lore(is)) {
                String t = l.trim();
                if (t.startsWith("Items Required")) { in = true; continue; }
                if (!in) continue;
                if (t.isEmpty() || t.startsWith("Rewards")) break;
                Matcher m = NEED.matcher(t);
                if (m.matches()) need.merge(m.group(1).trim(), Integer.parseInt(m.group(2).replace(",", "")), Integer::sum);
                else need.merge(t, 1, Integer::sum);
            }
            if (need.isEmpty()) return;
            long now = System.currentTimeMillis();
            if (title.equals(lastTitle) && now - lastAt < 30_000) return;        // once per visitor
            lastTitle = title;
            lastAt = now;
            Minecraft mc = Minecraft.getInstance();
            Tracker.say("§a[Visitor] §f" + title + " §7wants:");
            for (var e : need.entrySet()) {
                int have = 0;
                if (mc.player != null) for (int i = 0; i < 36; i++) {
                    ItemStack st = mc.player.getInventory().getItem(i);
                    if (!st.isEmpty() && Tracker.strip(st.getHoverName().getString()).trim().equals(e.getKey())) have += st.getCount();
                }
                int missing = Math.max(0, e.getValue() - have);
                String id = Prices.idFor(e.getKey());
                double[] bz = id != null ? Prices.bazaarRaw(id) : null;
                String cost = bz != null && bz[1] > 0 ? " §8≈ " + Fmt.coins(bz[1] * missing) : "";
                String line = " §f" + e.getValue() + "x " + e.getKey() + " §7(you carry " + have + ")" + cost;
                if (missing > 0 && bz != null) {
                    String cmd = "/skyassist bzbuy " + missing + " " + e.getKey();
                    Tracker.say(Chat.clickable(line + " §e§l[Bazaar]", cmd, "Opens " + e.getKey() + " in the Bazaar and copies " + missing
                            + " (the amount you still need) so you can paste it into the amount sign.\n§8Items in your sacks aren't counted here."));
                } else {
                    Tracker.say(line + (missing == 0 ? " §a✔" : ""));
                }
            }
        }
    }

    private VisitorShop() {}
}
