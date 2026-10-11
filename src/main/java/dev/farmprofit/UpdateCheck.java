package dev.farmprofit;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Compares this jar's commit with the newest successful build in your GitHub repo. */
public final class UpdateCheck {
    private static final String NAME = "Update check";
    private static volatile boolean started;
    static volatile String message, link;

    static { Health.onRetry(NAME, () -> started = false); }

    public static String thisCommit() {
        try (InputStream in = UpdateCheck.class.getResourceAsStream("/skyassist-build.txt")) {
            if (in == null) return "dev";
            String s = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return s.isEmpty() || s.contains("$") ? "dev" : s;
        } catch (Exception e) { return "dev"; }
    }

    public static void start() {
        Config c = Config.get();
        if (started) return;
        if (!c.updateCheck) { Health.off(NAME, "turned off in the settings"); return; }
        if (c.updateRepo == null || !c.updateRepo.contains("/")) { Health.off(NAME, "no GitHub repository set in the settings"); return; }
        started = true;
        String mine = thisCommit();
        if (mine.equals("dev")) { Health.off(NAME, "this is a development build (nothing to compare with)"); return; }
        String repo = c.updateRepo.trim();
        String url = "https://api.github.com/repos/" + repo + "/actions/runs?status=success&per_page=1";
        Http.get(NAME, url, 20, Map.of("Accept", "application/vnd.github+json"), body -> {
            JsonArray runs = JsonParser.parseString(body).getAsJsonObject().getAsJsonArray("workflow_runs");
            if (runs == null || runs.isEmpty()) return "no successful builds found in " + repo;
            JsonObject run = runs.get(0).getAsJsonObject();
            String sha = run.get("head_sha").getAsString();
            if (sha.equals(mine)) return "up to date (" + mine.substring(0, Math.min(7, mine.length())) + ")";
            link = run.get("html_url").getAsString();
            message = "§6[SkyAssist] §7A newer build of this mod is ready on GitHub. ";
            return "a newer build is available";
        }, null);
    }

    /** Shows the result on the game thread. */
    static void tick() {
        if (message == null) return;
        Tracker.say(Chat.link(message + "§a§n[Open download page]", link));
        message = null;
    }

    private UpdateCheck() {}
}
