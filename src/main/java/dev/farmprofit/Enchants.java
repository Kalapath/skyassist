package dev.farmprofit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Colors enchantments in item tooltips by how good the level is, like other SkyBlock mods:
 * perfect (absolute max), great (above what the enchanting table gives), good (table max), below that.
 * Max levels come from a public data file (SkyKings Bot-Data), so new enchants are covered automatically.
 */
public final class Enchants {
    /** lower-case name -> {table max, absolute max, ultimate (1/0)} */
    private static final Map<String, int[]> DATA = new ConcurrentHashMap<>();
    private static final Pattern PART = Pattern.compile("^(.+?) ([IVXLC]+|\\d+)$");
    private static final String RAINBOW = "c6eabd";
    /** Every ultimate enchantment (in case the data file doesn't flag them). */
    private static final java.util.Set<String> ULTIMATES = java.util.Set.of("bank", "bobbin' time", "chimera", "combo", "duplex",
            "fatal tempo", "flash", "flowstate", "habanero tactics", "inferno", "last stand", "legion", "no pain no gain",
            "one for all", "refrigerate", "reiterate", "rend", "soul eater", "swarm", "the one", "ultimate jerry", "ultimate wise", "wisdom");
    private static volatile long lastFetch;

    public static int count() { return DATA.size(); }

    public static void tick() {
        String url = Config.get().enchantDataUrl;
        if (url == null || url.isBlank()) return;
        long now = System.currentTimeMillis();
        if (now - lastFetch < (DATA.isEmpty() ? 10 * 60_000 : 24 * 3_600_000L)) return;
        lastFetch = now;
        HttpClient.newHttpClient().sendAsync(HttpRequest.newBuilder(URI.create(url)).header("User-Agent", "SkyAssist")
                        .timeout(Duration.ofSeconds(20)).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenAccept(r -> { if (r.statusCode() == 200) parse(r.body()); })
                .exceptionally(e -> null);
    }

    private static void parse(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            for (var e : root.entrySet()) {
                if (!e.getValue().isJsonObject()) continue;
                JsonObject o = e.getValue().getAsJsonObject();
                int table = o.has("max_table") ? o.get("max_table").getAsInt() : 0;
                int max = o.has("max") ? o.get("max").getAsInt() : table;
                int ult = o.has("ultimate") && o.get("ultimate").getAsBoolean() ? 1 : 0;
                int[] v = {table, max, ult};
                DATA.put(e.getKey().toLowerCase(Locale.ROOT).replace('_', ' '), v);
                if (o.has("name")) DATA.put(o.get("name").getAsString().toLowerCase(Locale.ROOT), v);
            }
        } catch (Exception ignored) {}
    }

    private static int level(String s) {
        if (s.chars().allMatch(Character::isDigit)) return Integer.parseInt(s);
        int total = 0, prev = 0;
        for (int i = s.length() - 1; i >= 0; i--) {
            int v = switch (s.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }

    private static String code(String color) {
        return switch (color == null ? "" : color.toLowerCase(Locale.ROOT)) {
            case "gold" -> "§6";
            case "red" -> "§c";
            case "light purple" -> "§d";
            case "dark purple" -> "§5";
            case "aqua" -> "§b";
            case "green" -> "§a";
            case "yellow" -> "§e";
            case "blue" -> "§9";
            case "gray" -> "§7";
            case "dark gray" -> "§8";
            case "white" -> "§f";
            default -> "§9";
        };
    }

    private static String paint(String text, String color, boolean bold) {
        String b = bold ? "§l" : "";
        if (!"rainbow".equalsIgnoreCase(color)) return code(color) + b + text;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        for (char ch : text.toCharArray()) {
            if (ch != ' ') sb.append('§').append(RAINBOW.charAt(i++ % RAINBOW.length())).append(b);
            sb.append(ch);
        }
        return sb.toString();
    }

    /** Recolors every tooltip line that is made up only of enchantments. */
    public static void color(List<Component> lines) {
        Config c = Config.get();
        if (!c.enchantColors || DATA.isEmpty()) return;
        for (int i = 1; i < lines.size(); i++) {           // line 0 is the item name
            String plain = Tracker.strip(lines.get(i).getString()).trim();
            if (plain.isEmpty() || plain.length() > 200) continue;
            String[] parts = plain.split(", ");
            List<String> out = new ArrayList<>();
            boolean allEnchants = true;
            for (String part : parts) {
                Matcher m = PART.matcher(part.trim());
                int[] info = m.matches() ? DATA.get(m.group(1).toLowerCase(Locale.ROOT)) : null;
                if (info == null && m.matches() && ULTIMATES.contains(m.group(1).toLowerCase(Locale.ROOT)))   // ultimate missing from the data file
                    info = new int[]{0, m.group(1).equalsIgnoreCase("One For All") ? 1 : 5, 1};
                if (info == null) { allEnchants = false; break; }
                int lvl = level(m.group(2));
                boolean ultimate = info[2] == 1 || ULTIMATES.contains(m.group(1).toLowerCase(Locale.ROOT));
                String color;
                if (lvl >= info[1]) color = c.rainbowMaxed ? "rainbow" : c.enchantPerfectColor;
                else if (info[0] > 0 && lvl > info[0]) color = c.enchantGreatColor;
                else if (info[0] > 0 && lvl == info[0]) color = c.enchantGoodColor;
                else color = c.enchantPoorColor;
                String text = part.trim();
                if (c.enchantMaxTag && lvl >= info[1]) text += " ✦";
                // ultimate enchants keep Hypixel's own bold pink (unless you turn that off)
                if (ultimate && c.ultimateKeepPink) out.add(paint(text, "light purple", true));
                else out.add(paint(text, color, ultimate));
            }
            if (!allEnchants || out.isEmpty()) continue;
            lines.set(i, Component.literal(String.join("§9, ", out)));
        }
    }

    private Enchants() {}
}
