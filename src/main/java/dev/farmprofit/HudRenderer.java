package dev.farmprofit;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds the panel layout each frame (drawing itself happens in FarmProfitClient, which has the graphics object). */
final class HudRenderer {
    /** Edit mode (/profit gui): every panel is shown with an outline, even empty or hidden ones. */
    static boolean editMode;
    static final int CELL_W = 52, CELL_H = 24;
    private static final Map<String, Hud.Lines> cache = new HashMap<>();
    private static long cacheTime;

    static List<HudEditor.Box> layout(Minecraft mc) {
        Config cfg = Config.get();
        boolean chat = Compat.screen(mc) instanceof ChatScreen;
        if (!chat) editMode = false;
        long now = System.currentTimeMillis();
        if (now - cacheTime > (chat ? 100 : 250)) {         // rebuild text 4x a second (10x while editing)
            cache.clear();
            for (String id : Panels.ALL) cache.put(id, Hud.panel(id));
            cacheTime = now;
        }
        List<HudEditor.Box> boxes = new ArrayList<>();
        String activity = Tracker.shownType();
        int lh = Math.max(8, Math.min(20, cfg.hudLineHeight));
        int nextY = -1, nextX = -1;                           // where an un-placed panel goes (under the previous one)
        for (String id : Panels.ALL) {
            if (id.equals("bazaar") && !cfg.bazaarHud && !cfg.auctionHud) continue;
            if (!cfg.separatePanels && (id.equals("secrets") || id.equals("contest") || id.equals("suggest"))) continue;
            String key = Panels.key(id, activity);
            Panels.Pos pos = Panels.get(key);
            Hud.Lines lines = cache.getOrDefault(id, new Hud.Lines());
            if (lines.isEmpty() && editMode) { lines = new Hud.Lines(); lines.add("§8" + Panels.title(id) + " (empty now)"); }
            if (lines.isEmpty() || (pos.hidden && !editMode)) continue;
            float scale = (float) Math.max(0.5, Math.min(3.0, pos.scale));
            int x = pos.x >= 0 ? pos.x : (nextX >= 0 ? nextX : cfg.hudX);
            int y = pos.y >= 0 ? pos.y : (nextY >= 0 ? nextY : cfg.hudY);
            int w = 0;
            for (Hud.HudLine l : lines) {
                boolean icon = cfg.hudIcons && Tracker.icon(l.item()) != null;
                w = Math.max(w, mc.font.width(l.text()) + (icon ? 11 : 0));
            }
            int h = lines.size() * lh;
            if (id.equals("greenhouse")) {
                Greenhouse.Grid g = Greenhouse.grid();
                if (g != null) { w = Math.max(w, g.size() * CELL_W); h += g.size() * CELL_H + 4; }
            }
            HudEditor.Box box = new HudEditor.Box(id, key, lines, x, y, w, h, lh, scale, pos.hidden);
            boxes.add(box);
            nextX = x;
            nextY = y + Math.round((box.h() + 10) * scale);
        }
        return boxes;
    }

    private HudRenderer() {}
}
