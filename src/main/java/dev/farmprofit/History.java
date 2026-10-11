package dev.farmprofit;

import com.google.gson.reflect.TypeToken;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Finished sessions, saved in .minecraft/config/skyassist/history.json */
public final class History {
    private static final Path FILE = Config.DIR.resolve("history.json");
    private static final int MAX = 200;
    private static List<Session> sessions;

    public static List<Session> all() {
        if (sessions == null) load();
        return sessions;
    }

    public static void add(Session s) {
        all().add(s);
        while (sessions.size() > MAX) sessions.remove(0);
        save();
    }

    private static void load() {
        try {
            if (Files.exists(FILE)) {
                sessions = Config.GSON.fromJson(Files.readString(FILE), new TypeToken<List<Session>>() {}.getType());
            }
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not read history (the old file is kept as .broken)", e);
            Config.backupBroken(FILE);
        }
        if (sessions == null) sessions = new ArrayList<>();
    }

    private static void save() {
        try {
            Files.createDirectories(Config.DIR);
            Config.write(FILE, Config.GSON.toJson(sessions));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save history", e);
        }
    }
}
