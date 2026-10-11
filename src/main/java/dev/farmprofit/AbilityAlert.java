package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pickaxe ability cooldown: reads "You used your Mining Speed Boost Pickaxe Ability!" and "Mining Speed Boost is now available!"
 * from chat, shows the cooldown on the Mining HUD and rings (plus big text) when the ability is ready again.
 * It only watches Hypixel's messages; it never uses the ability for you.
 */
public final class AbilityAlert {
    private static final Pattern USED = Pattern.compile("^You used your (.+?) Pickaxe Ability!?$");
    private static final Pattern READY = Pattern.compile("^(?:Your )?(.+?) is now available!$");
    private static final Pattern COOLDOWN = Pattern.compile("^Cooldown: (\\d+)s$");
    /** Pickaxe abilities, so the ready message still counts after a restart (when "used" wasn't seen). */
    private static final Set<String> KNOWN = Set.of("Mining Speed Boost", "Pickobulus", "Maniac Miner", "Gemstone Infusion",
            "Anomalous Desire", "Sheer Force", "Vein Seeker", "Hazardous Miner", "Tunnel Vision");

    private static String name;
    private static long usedAt, cooldownMs, readyAt;

    /** Called for every chat line (plain text). True if it was one of the ability messages. */
    static boolean onChat(String plain) {
        Matcher u = USED.matcher(plain);
        if (u.matches()) {
            name = u.group(1).trim();
            usedAt = System.currentTimeMillis();
            readyAt = 0;
            cooldownMs = Safe.get("Ability cooldown", () -> readCooldown(Minecraft.getInstance(), name), 0L);
            Debug.saw("pickaxe ability used");
            return true;
        }
        Matcher r = READY.matcher(plain);
        if (!r.matches()) return false;
        String n = r.group(1).trim();
        long now = System.currentTimeMillis();
        boolean mine = name != null && n.equals(name) && usedAt > 0 && now - usedAt < 30 * 60_000L;
        if (!mine && !KNOWN.contains(n)) return false;              // some other "... is now available!" message
        Debug.saw("pickaxe ability ready");
        name = n;
        readyAt = now;
        usedAt = 0;
        if (Config.get().abilityAlert) Alerts.notify("ability", "§a§l⛏ " + n + " ready!", "§7pickaxe ability", null);
        return true;
    }

    /** The "Cooldown: 120s" under "Ability: <name>" in the lore of an item in your inventory, in ms (0 if not found). */
    private static long readCooldown(Minecraft mc, String ability) {
        if (mc.player == null || ability == null) return 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            List<String> lore = ItemIds.lore(stack);
            for (int k = 0; k < lore.size(); k++) {
                if (!lore.get(k).startsWith("Ability: " + ability)) continue;
                for (int j = k + 1; j < Math.min(lore.size(), k + 10); j++) {
                    Matcher m = COOLDOWN.matcher(lore.get(j).trim());
                    if (m.matches()) return Long.parseLong(m.group(1)) * 1000L;
                }
            }
        }
        return 0;
    }

    /** The Mining HUD line: cooldown left, or "ready" for a few seconds after the alert. */
    static void addHudLines(Hud.Lines out) {
        if (!Config.get().abilityHud || name == null) return;
        long now = System.currentTimeMillis();
        if (readyAt > 0) {
            if (now - readyAt < 20_000) out.add("§a⛏ " + name + " ready");
            return;
        }
        if (usedAt == 0) return;
        long since = now - usedAt;
        if (since > 30 * 60_000L) { usedAt = 0; return; }              // never saw the ready message: stop showing it
        if (cooldownMs > 0) {
            long left = usedAt + cooldownMs - now;
            out.add(left > 0 ? "§7⛏ " + name + ": §e" + Fmt.clock(left) + " §7cooldown" : "§7⛏ " + name + ": §8should be ready");
        } else {
            out.add("§7⛏ " + name + ": §8used " + Fmt.clock(since) + " ago");
        }
    }

    private AbilityAlert() {}
}
