package dev.farmprofit;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.stream.Stream;

/** Named setting profiles, plus a text code to share settings. */
public final class Profiles {
    private static final Path DIR = Config.DIR.resolve("profiles");

    private static Path file(String name) { return DIR.resolve(name.replaceAll("[^A-Za-z0-9_-]", "_") + ".json"); }

    public static void save(String name) {
        try {
            Files.createDirectories(DIR);
            Config.write(file(name), Config.GSON.toJson(Config.get()));
            Tracker.say("§6[SkyAssist] §7Saved settings profile §f" + name);
        } catch (Exception e) { Tracker.say("§6[SkyAssist] §cCouldn't save: " + e.getMessage()); }
    }

    public static void load(String name) {
        try {
            Path f = file(name);
            if (!Files.exists(f)) { Tracker.say("§6[SkyAssist] §7No profile called §f" + name + "§7. /profit profile list"); return; }
            Config.replace(Config.GSON.fromJson(Files.readString(f), Config.class));
            Tracker.say("§6[SkyAssist] §7Loaded settings profile §f" + name);
        } catch (Exception e) { Tracker.say("§6[SkyAssist] §cCouldn't load: " + e.getMessage()); }
    }

    public static void list() {
        try {
            if (!Files.exists(DIR)) { Tracker.say("§6[SkyAssist] §7No profiles yet. /profit profile save <name>"); return; }
            try (Stream<Path> s = Files.list(DIR)) {
                List<String> names = s.map(p -> p.getFileName().toString().replace(".json", "")).sorted().toList();
                Tracker.say("§6[SkyAssist] §7Profiles: §f" + (names.isEmpty() ? "none" : String.join(", ", names)));
            }
        } catch (Exception e) { Tracker.say("§6[SkyAssist] §cCouldn't list profiles."); }
    }

    public static void export() {
        String code = Base64.getEncoder().encodeToString(Config.GSON.toJson(Config.get()).getBytes(StandardCharsets.UTF_8));
        Chat.copy(code);
        Tracker.say("§6[SkyAssist] §7Settings code copied to your clipboard (" + code.length() + " characters). Import with §f/profit profile import <code>");
    }

    public static void importCode(String code) {
        try {
            String json = new String(Base64.getDecoder().decode(code.trim()), StandardCharsets.UTF_8);
            Config.replace(Config.GSON.fromJson(json, Config.class));
            Tracker.say("§6[SkyAssist] §7Settings imported.");
        } catch (Exception e) { Tracker.say("§6[SkyAssist] §cThat code didn't work."); }
    }

    private Profiles() {}
}
