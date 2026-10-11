package dev.farmprofit;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Skill XP per hour and time to the next level. Hypixel shows "+11.5 Mining (12,345/50,000)" above your hotbar whenever you gain
 * skill XP; this reads those numbers (nothing else) and keeps a running rate per skill. Display only.
 */
public final class SkillXp {
    private static final String NUM = "[\\d,]+(?:\\.\\d+)?";
    private static final Pattern FULL = Pattern.compile("\\+(" + NUM + ") ([A-Za-z]+) \\((" + NUM + "[kKmMbB]?)/(" + NUM + "[kKmMbB]?)\\)");
    private static final Pattern PCT = Pattern.compile("\\+(" + NUM + ") ([A-Za-z]+) \\(([\\d.]+)%\\)");
    private static final Pattern TOTAL = Pattern.compile("\\+(" + NUM + ") ([A-Za-z]+) \\((" + NUM + ")\\)");

    /** XP gained in a stretch of activity in one skill. A pause of more than 90 s starts a new stretch. */
    private static final class Streak {
        final String skill;
        /** XP gained after the first message of the stretch (the first one only marks the start). */
        double later, cur = -1, need = -1;
        long first, last;
        int events;
        Streak(String skill) { this.skill = skill; }
    }

    private static final Map<String, Streak> STREAKS = new HashMap<>();
    private static String lastKey = "", lastSkill;
    private static long lastSeen;

    /** Called with the plain text of every action bar message. */
    static void onActionBar(String plain) {
        if (plain.indexOf('+') < 0) return;
        String skill, key;
        double gained, cur = -1, need = -1;
        Matcher m = FULL.matcher(plain);
        if (m.find()) {
            skill = m.group(2); gained = parse(m.group(1)); cur = parse(m.group(3)); need = parse(m.group(4));
            key = skill + "|" + m.group(3) + "|" + m.group(1);
        } else if ((m = PCT.matcher(plain)).find()) {
            skill = m.group(2); gained = parse(m.group(1));
            key = skill + "|" + m.group(3) + "%|" + m.group(1);
        } else if ((m = TOTAL.matcher(plain)).find()) {
            skill = m.group(2); gained = parse(m.group(1)); cur = -1;
            key = skill + "|" + m.group(3) + "|" + m.group(1);
        } else return;
        if (!(gained > 0) || key.equals(lastKey)) return;          // the same message is sent again and again while it's shown
        lastKey = key;
        long now = System.currentTimeMillis();
        Streak s = STREAKS.get(skill);
        if (s == null || now - s.last > 90_000) {
            s = new Streak(skill);
            s.first = now;
            STREAKS.put(skill, s);
        } else {
            double add = gained;
            // the XP bar shows only the latest gain, so messages can be missed; the change in progress counts them all
            if (cur >= 0 && s.cur >= 0 && need == s.need && cur > s.cur) add = cur - s.cur;
            s.later += add;
        }
        s.events++;
        s.last = now;
        if (cur >= 0 && need > 0) { s.cur = cur; s.need = need; }
        lastSkill = skill;
        lastSeen = now;
        Debug.saw("skill xp");
    }

    private static double parse(String t) {
        String x = t.replace(",", "").trim();
        double mult = 1;
        if (!x.isEmpty() && Character.isLetter(x.charAt(x.length() - 1))) {
            mult = switch (Character.toLowerCase(x.charAt(x.length() - 1))) { case 'k' -> 1e3; case 'm' -> 1e6; case 'b' -> 1e9; default -> 1; };
            x = x.substring(0, x.length() - 1);
        }
        try { return Double.parseDouble(x) * mult; } catch (NumberFormatException e) { return Double.NaN; }
    }

    /** XP per hour for a stretch, or NaN while there is too little to go on. */
    private static double perHour(Streak s) {
        long span = s.last - s.first;
        if (s.events < 3 || span < 10_000) return Double.NaN;
        return s.later / span * 3_600_000.0;
    }

    /** The skill the activity trains, or null. */
    private static String skillFor(String type) {
        if (type == null) return null;
        if (Tracker.isMiningType(type)) return "Mining";
        return switch (type) {
            case Tracker.FARMING -> "Farming";
            case Tracker.FORAGING -> "Foraging";
            case Tracker.FISHING -> "Fishing";
            case Tracker.COMBAT, Tracker.KUUDRA, Tracker.DIANA -> "Combat";
            default -> null;
        };
    }

    /** HUD line for this activity's skill (or the one you gained XP in last), or null. */
    static String hudLine(String type) {
        if (!Config.get().skillXpHud || STREAKS.isEmpty()) return null;
        long now = System.currentTimeMillis();
        STREAKS.values().removeIf(s -> now - s.last > 90_000);
        if (STREAKS.isEmpty()) return null;
        Streak s = STREAKS.get(skillFor(type));
        if (s == null && lastSkill != null) s = STREAKS.get(lastSkill);
        if (s == null) return null;
        String idle = now - s.last > 20_000 ? " §8(paused)" : "";
        double rate = perHour(s);
        if (Double.isNaN(rate)) return "§7" + s.skill + " XP: §8measuring..." + idle;
        String eta = "";
        if (s.need > 0 && s.cur >= 0 && s.need > s.cur && rate > 0) eta = " §8· next level in " + Fmt.brief((long) ((s.need - s.cur) / rate * 3_600_000.0));
        return "§7" + s.skill + " XP: §b" + Fmt.coins(rate) + "/h" + eta + idle;
    }

    private SkillXp() {}
}
