package dev.farmprofit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dungeon puzzles solved by looking at the room: Ice Fill, Creeper Beams, Teleport Maze, Tic Tac Toe, plus the Quiz.
 * Answers are shown as particles in the world (only you see them) and as lines on the HUD. You do the solving.
 */
public final class WorldPuzzles {
    private static final List<String> hud = new ArrayList<>();
    private static int tick;

    public static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null || !Tracker.DUNGEONS.equals(Tracker.area)) {
            hud.clear();
            teleports.clear();
            usedPads.clear();
            return;
        }
        trackTeleports(mc);
        if (++tick % (5 * Perf.slow()) != 0) return;   // 4x a second
        hud.clear();
        Config c = Config.get();
        try { if (c.solveIceFill) iceFill(mc); } catch (Throwable ignored) {}
        try { if (c.solveCreeper) creeperBeams(mc); } catch (Throwable ignored) {}
        try { if (c.solveTeleport) teleportMaze(mc); } catch (Throwable ignored) {}
        try { if (c.solveTicTacToe) ticTacToe(mc); } catch (Throwable ignored) {}
        if (quizAnswer != null && System.currentTimeMillis() - quizTime < 60_000) hud.add("§d§lQuiz §7answer: §a" + quizAnswer);
    }

    public static void addHudLines(Hud.Lines out) {
        for (String l : hud) out.add(l);
    }

    // =====================================================================
    // Ice Fill: walk over every ice tile exactly once. Finds a path from where you stand.
    // =====================================================================

    private static List<BlockPos> icePath = new ArrayList<>();
    private static long iceKey;

    private static void iceFill(Minecraft mc) {
        BlockPos feet = mc.player.blockPosition();
        int floorY = feet.getY() - 1;
        // all ice / packed ice tiles connected to you on this floor level
        Set<Long> ice = new HashSet<>(), floor = new HashSet<>();
        List<BlockPos> queue = new ArrayList<>();
        BlockPos start = new BlockPos(feet.getX(), floorY, feet.getZ());
        queue.add(start);
        Set<Long> seen = new HashSet<>();
        seen.add(start.asLong());
        while (!queue.isEmpty() && seen.size() < 200) {
            BlockPos p = queue.remove(queue.size() - 1);
            BlockState s = mc.level.getBlockState(p);
            boolean isIce = s.is(Blocks.ICE), isPacked = s.is(Blocks.PACKED_ICE);
            if (!isIce && !isPacked && !p.equals(start)) continue;
            if (!mc.level.getBlockState(p.above()).isAir()) continue;      // a wall above: not walkable
            floor.add(p.asLong());
            if (isIce) ice.add(p.asLong());
            for (BlockPos n : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
                if (Math.abs(n.getX() - feet.getX()) > 10 || Math.abs(n.getZ() - feet.getZ()) > 10) continue;
                if (seen.add(n.asLong())) queue.add(n);
            }
        }
        if (ice.size() < 4) { icePath.clear(); return; }                     // not the puzzle
        // checkerboard rule: each step swaps tile colour, so the two colours can differ by at most one
        int even = 0;
        for (long l : ice) { BlockPos p = BlockPos.of(l); if (((p.getX() + p.getZ()) & 1) == 0) even++; }
        if (Math.abs(even - (ice.size() - even)) > 1) {
            hud.add("§b§lIce Fill §7" + ice.size() + " tiles left §8(can't be finished from here)");
            return;
        }
        // same room state as last time: reuse the path instead of searching again
        long key = start.asLong() * 31 + ice.hashCode();
        if (key != iceKey) {
            iceKey = key;
            List<BlockPos> path = new ArrayList<>();
            int[] budget = {200_000};
            icePath = dfs(start, ice, new HashSet<>(), path, ice.size(), budget, mc) ? path : null;
        }
        if (icePath == null) {
            hud.add("§b§lIce Fill §7" + ice.size() + " tiles left §8(no path from here, step back)");
            return;
        }
        List<BlockPos> path = icePath;
        hud.add("§b§lIce Fill §7follow the green path §8(" + ice.size() + " tiles)");
        BlockPos prev = start;
        for (int i = 0; i < path.size(); i++) {
            BlockPos p = path.get(i);
            Particles.line(i == 0 ? Particles.WHITE : Particles.GREEN, prev.getX() + 0.5, floorY + 1.1, prev.getZ() + 0.5,
                    p.getX() + 0.5, floorY + 1.1, p.getZ() + 0.5);
            prev = p;
        }
    }

    private static boolean dfs(BlockPos at, Set<Long> ice, Set<Long> visited, List<BlockPos> path, int total, int[] budget, Minecraft mc) {
        if (path.size() == total) return true;
        if (--budget[0] < 0) return false;
        List<BlockPos> next = new ArrayList<>();
        for (BlockPos n : new BlockPos[]{at.north(), at.south(), at.east(), at.west()}) {
            if (ice.contains(n.asLong()) && !visited.contains(n.asLong())) next.add(n);
        }
        // Warnsdorff: try the tile with the fewest onward options first
        next.sort((a, b) -> Integer.compare(free(a, ice, visited), free(b, ice, visited)));
        for (BlockPos n : next) {
            visited.add(n.asLong());
            path.add(n);
            if (dfs(n, ice, visited, path, total, budget, mc)) return true;
            path.remove(path.size() - 1);
            visited.remove(n.asLong());
        }
        return false;
    }

    private static int free(BlockPos p, Set<Long> ice, Set<Long> visited) {
        int n = 0;
        for (BlockPos q : new BlockPos[]{p.north(), p.south(), p.east(), p.west()}) {
            if (ice.contains(q.asLong()) && !visited.contains(q.asLong())) n++;
        }
        return n;
    }

    // =====================================================================
    // Creeper Beams: connect sea lantern pairs whose line passes through the creeper.
    // =====================================================================

    private static void creeperBeams(Minecraft mc) {
        Entity creeper = null;
        for (Entity e : entities(mc)) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath().equals("creeper") && e.distanceTo(mc.player) < 25) { creeper = e; break; }
        }
        if (creeper == null) return;
        double cx = creeper.getX(), cy = creeper.getY() + 0.9, cz = creeper.getZ();
        BlockPos c = creeper.blockPosition();
        List<double[]> lanterns = new ArrayList<>();
        for (int dx = -15; dx <= 15; dx++) for (int dy = -8; dy <= 12; dy++) for (int dz = -15; dz <= 15; dz++) {
            BlockPos p = c.offset(dx, dy, dz);
            if (mc.level.getBlockState(p).is(Blocks.SEA_LANTERN)) lanterns.add(new double[]{p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5});
        }
        if (lanterns.size() < 2) return;
        record Pair(double[] a, double[] b, double miss) {}
        List<Pair> pairs = new ArrayList<>();
        for (int i = 0; i < lanterns.size(); i++) for (int j = i + 1; j < lanterns.size(); j++) {
            double miss = segmentDistance(lanterns.get(i), lanterns.get(j), cx, cy, cz);
            if (miss < 1.0) pairs.add(new Pair(lanterns.get(i), lanterns.get(j), miss));
        }
        pairs.sort((a, b) -> Double.compare(a.miss(), b.miss()));
        Set<double[]> used = new HashSet<>();
        int shown = 0;
        for (Pair p : pairs) {
            if (used.contains(p.a()) || used.contains(p.b()) || shown >= 4) continue;
            used.add(p.a());
            used.add(p.b());
            var type = Particles.PAIRS[shown % Particles.PAIRS.length];
            Particles.line(type, p.a()[0], p.a()[1], p.a()[2], p.b()[0], p.b()[1], p.b()[2]);
            Particles.pillar(type, p.a()[0], p.a()[1] + 0.6, p.a()[2]);
            Particles.pillar(type, p.b()[0], p.b()[1] + 0.6, p.b()[2]);
            shown++;
        }
        if (shown > 0) hud.add("§a§lCreeper Beams §7connect the " + shown + " marked lantern pair" + (shown > 1 ? "s" : ""));
    }

    private static double segmentDistance(double[] a, double[] b, double px, double py, double pz) {
        double vx = b[0] - a[0], vy = b[1] - a[1], vz = b[2] - a[2];
        double wx = px - a[0], wy = py - a[1], wz = pz - a[2];
        double t = Math.max(0, Math.min(1, (wx * vx + wy * vy + wz * vz) / (vx * vx + vy * vy + vz * vz)));
        double dx = a[0] + vx * t - px, dy = a[1] + vy * t - py, dz = a[2] + vz * t - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    // =====================================================================
    // Teleport Maze: after each teleport you face toward the exit; mark the pad that's most in front of you.
    // =====================================================================

    private record Ray(double x, double z, float yaw) {}
    private static final List<Ray> teleports = new ArrayList<>();
    private static final Set<Long> usedPads = new HashSet<>();
    private static double lastX = Double.NaN, lastZ;

    private static void trackTeleports(Minecraft mc) {
        double x = mc.player.getX(), z = mc.player.getZ();
        if (!Double.isNaN(lastX)) {
            double jump = Math.hypot(x - lastX, z - lastZ);
            if (jump > 3 && jump < 40 && nearPad(mc, mc.player.blockPosition(), 3) != null) {
                BlockPos from = nearPad(mc, BlockPos.containing(lastX, mc.player.getY(), lastZ), 2);
                if (from != null) usedPads.add(from.asLong());
                BlockPos to = nearPad(mc, mc.player.blockPosition(), 2);
                if (to != null) usedPads.add(to.asLong());
                teleports.add(new Ray(x, z, mc.player.getYRot()));
                if (teleports.size() > 20) teleports.remove(0);
            }
        }
        lastX = x;
        lastZ = z;
    }

    private static BlockPos nearPad(Minecraft mc, BlockPos around, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -2; dy <= 0; dy++) {
            BlockPos p = around.offset(dx, dy, dz);
            if (mc.level.getBlockState(p).is(Blocks.END_PORTAL_FRAME)) return p;
        }
        return null;
    }

    private static void teleportMaze(Minecraft mc) {
        if (teleports.isEmpty()) return;
        Ray last = teleports.get(teleports.size() - 1);
        if (Math.hypot(mc.player.getX() - last.x(), mc.player.getZ() - last.z()) > 8) return;   // walked away
        BlockPos me = mc.player.blockPosition();
        BlockPos best = null;
        double bestAngle = 999;
        for (int dx = -6; dx <= 6; dx++) for (int dz = -6; dz <= 6; dz++) for (int dy = -2; dy <= 0; dy++) {
            BlockPos p = me.offset(dx, dy, dz);
            if (!mc.level.getBlockState(p).is(Blocks.END_PORTAL_FRAME) || usedPads.contains(p.asLong())) continue;
            double yawTo = Math.toDegrees(Math.atan2(-(p.getX() + 0.5 - last.x()), p.getZ() + 0.5 - last.z()));
            double diff = Math.abs(((yawTo - last.yaw()) % 360 + 540) % 360 - 180);
            if (diff < bestAngle) { bestAngle = diff; best = p; }
        }
        if (best == null) return;
        Particles.pillar(Particles.GREEN, best.getX() + 0.5, best.getY() + 1, best.getZ() + 0.5);
        hud.add("§5§lTeleport Maze §7take the green pad §8(" + usedPads.size() + " used)");
    }

    // =====================================================================
    // Tic Tac Toe: read the maps on the wall, best move with minimax.
    // =====================================================================

    private static void ticTacToe(Minecraft mc) {
        Map<Long, Character> marks = new HashMap<>();   // position -> 'X' / 'O'
        List<BlockPos> cells = new ArrayList<>();
        for (Entity e : entities(mc)) {
            String type = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getPath();
            if (!type.contains("item_frame") || e.distanceTo(mc.player) > 12) continue;
            Object st = Reflect.call(e, "getItem");
            if (!(st instanceof ItemStack stack) || stack.isEmpty()) continue;
            Character mark = readMark(mc, stack);
            if (mark == null) continue;
            BlockPos p = e.blockPosition();
            marks.put(p.asLong(), mark);
            cells.add(p);
        }
        if (marks.isEmpty()) return;
        // empty cells are the buttons on the same wall
        BlockPos any = cells.get(0);
        for (int dx = -4; dx <= 4; dx++) for (int dy = -4; dy <= 4; dy++) for (int dz = -4; dz <= 4; dz++) {
            BlockPos p = any.offset(dx, dy, dz);
            String path = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock()).getPath();
            if (path.endsWith("_button") && !marks.containsKey(p.asLong())) cells.add(p);
        }
        // lay the cells out on a 3x3 grid (wall runs along x or z)
        boolean alongX = cells.stream().mapToInt(BlockPos::getX).distinct().count() >= cells.stream().mapToInt(BlockPos::getZ).distinct().count();
        List<Integer> hs = new ArrayList<>(), vs = new ArrayList<>();
        for (BlockPos p : cells) {
            int h = alongX ? p.getX() : p.getZ();
            if (!hs.contains(h)) hs.add(h);
            if (!vs.contains(p.getY())) vs.add(p.getY());
        }
        hs.sort(Integer::compare);
        vs.sort((a, b) -> Integer.compare(b, a));        // top row first
        if (hs.size() != 3 || vs.size() != 3) return;
        char[] board = new char[9];
        BlockPos[] at = new BlockPos[9];
        for (BlockPos p : cells) {
            int i = vs.indexOf(p.getY()) * 3 + hs.indexOf(alongX ? p.getX() : p.getZ());
            at[i] = p;
            board[i] = marks.getOrDefault(p.asLong(), ' ');
        }
        int xs = 0, os = 0;
        for (char ch : board) { if (ch == 'X') xs++; if (ch == 'O') os++; }
        char me = xs > os ? 'O' : 'X';
        if (winner(board) != ' ' || xs + os == 9) return;
        int best = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < 9; i++) {
            if (board[i] != ' ' || at[i] == null) continue;
            board[i] = me;
            int score = -minimax(board, me == 'X' ? 'O' : 'X', 1);
            board[i] = ' ';
            if (score > bestScore) { bestScore = score; best = i; }
        }
        if (best < 0) return;
        BlockPos b = at[best];
        Particles.pillar(Particles.GREEN, b.getX() + 0.5, b.getY(), b.getZ() + 0.5);
        hud.add("§e§lTic Tac Toe §7press the green button §8(you are " + me + ")");
    }

    /** X has lines through the middle of the map, O is a ring with an empty middle. */
    private static Character readMark(Minecraft mc, ItemStack stack) {
        try {
            Object id = stack.get(DataComponents.MAP_ID);
            if (id == null) return null;
            Class<?> mapItem = Class.forName("net.minecraft.world.item.MapItem");
            Object data = null;
            for (var m : mapItem.getMethods()) {
                if (m.getName().equals("getSavedData") && m.getParameterCount() == 2) {
                    try { data = m.invoke(null, id, mc.level); break; } catch (Exception ignored) {}
                }
            }
            Object colors = Reflect.field(data, "colors");
            if (!(colors instanceof byte[] px) || px.length < 128 * 128) return null;
            byte background = px[0], middle = px[64 * 128 + 64];
            return middle == background ? 'O' : 'X';
        } catch (Throwable t) {
            return null;
        }
    }

    private static final int[][] LINES = {{0, 1, 2}, {3, 4, 5}, {6, 7, 8}, {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}};

    private static char winner(char[] b) {
        for (int[] l : LINES) if (b[l[0]] != ' ' && b[l[0]] == b[l[1]] && b[l[1]] == b[l[2]]) return b[l[0]];
        return ' ';
    }

    /** Score from the view of whoever is to move; quicker wins (and slower losses) score higher. */
    private static int minimax(char[] b, char turn, int depth) {
        char w = winner(b);
        if (w != ' ') return w == turn ? 10 - depth : depth - 10;
        int best = Integer.MIN_VALUE;
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            if (b[i] != ' ') continue;
            any = true;
            b[i] = turn;
            best = Math.max(best, -minimax(b, turn == 'X' ? 'O' : 'X', depth + 1));
            b[i] = ' ';
        }
        return any ? best : 0;
    }

    // =====================================================================
    // Quiz (Ouro the Omniscient): known answers from a maintained list.
    // =====================================================================

    private static final String QUIZ_NAME = "Quiz answers";
    /** Replaced as a whole when new data arrives (the download runs on another thread). */
    private static volatile Map<String, List<String>> QUIZ = Map.of();

    static { Health.onRetry(QUIZ_NAME, () -> quizFetch = 0); }
    private static final Pattern OPTION = Pattern.compile("^\\s*([ⓐⓑⓒ])\\s*(.+)$");
    private static volatile long quizFetch;
    private static List<String> currentAnswers;
    private static String quizAnswer;
    private static long quizTime;

    public static void quizTick() {
        Config c = Config.get();
        if (!c.solveQuiz) { Health.off(QUIZ_NAME, "the quiz solver is turned off"); return; }
        String url = c.quizDataUrl;
        if (url == null || url.isBlank()) { Health.off(QUIZ_NAME, "no data address in the settings"); return; }
        long now = System.currentTimeMillis();
        long wait = QUIZ.isEmpty() ? Health.delay(QUIZ_NAME, 10 * 60_000L, 10 * 60_000L) : Health.delay(QUIZ_NAME, 12 * 3_600_000L, 30 * 60_000L);
        if (now - quizFetch < wait) return;
        quizFetch = now;
        Http.get(QUIZ_NAME, url, 20, body -> {
            JsonObject o = JsonParser.parseString(body).getAsJsonObject();
            Map<String, List<String>> found = new HashMap<>();
            for (var e : o.entrySet()) {
                try {
                    List<String> answers = new ArrayList<>();
                    if (e.getValue().isJsonArray()) for (JsonElement a : e.getValue().getAsJsonArray()) answers.add(a.getAsString());
                    else answers.add(e.getValue().getAsString());
                    found.put(norm(e.getKey()), answers);
                } catch (Exception skipThisOne) { /* one odd entry shouldn't lose the rest */ }
            }
            if (found.isEmpty()) throw new IllegalStateException("no questions found in the file");
            QUIZ = found;
            return Health.count(found.size(), "questions");
        });
    }

    public static int quizCount() { return QUIZ.size(); }

    private static String norm(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim(); }

    /** Returns true if the line belonged to the quiz. */
    public static boolean onChat(String plain) {
        if (!Config.get().solveQuiz || !Tracker.DUNGEONS.equals(Tracker.area)) return false;
        String n = norm(plain);
        if (n.contains("what skyblock year is it")) {
            long year = (System.currentTimeMillis() / 1000 - 1560275700L) / 446400L + 1;
            currentAnswers = List.of("Year " + year);
            return true;
        }
        for (var e : QUIZ.entrySet()) {
            if (!e.getKey().isEmpty() && n.contains(e.getKey())) { currentAnswers = e.getValue(); return true; }
        }
        Matcher m = OPTION.matcher(plain);
        if (m.matches() && currentAnswers != null) {
            String option = m.group(2).trim();
            for (String a : currentAnswers) {
                if (norm(option).equals(norm(a))) {
                    quizAnswer = m.group(1) + " " + option;
                    quizTime = System.currentTimeMillis();
                    Chat.ping();
                    Tracker.say("§d§l[Quiz] §fAnswer: §a§l" + quizAnswer);
                    Debug.saw("quiz");
                    return false;     // let the line show normally too
                }
            }
        }
        return false;
    }

    // ---------------- helpers ----------------

    private static List<Entity> entities(Minecraft mc) {
        List<Entity> out = new ArrayList<>();
        Object all = Reflect.call(mc.level, new String[]{"entitiesForRendering", "getEntities"});
        if (all instanceof Iterable<?> it) for (Object o : it) if (o instanceof Entity e) out.add(e);
        return out;
    }

    private WorldPuzzles() {}
}
