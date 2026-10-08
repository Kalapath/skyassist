# Changelog

## 8.11.0
- Best settings button / `/flips tune`: picks flip filters for the most profit per work from live prices and your budget; Undo / `/flips untune`.

## 8.10.0
- Flip HUD shows your AH listings (sold / expired / undercut) and bids (top / outbid / won); AH buys and sales count in flip profit.

## 8.9.0
- Craft flips (Bazaar and AH) respect your collections, slayer levels, HotM tier and skills (read from menus and level-up chat).
- New settings: Only recipes I can craft (on), Hide when level unknown (off).

## 8.8.0
- Safe flips only applies to normal Bazaar flips too (shared switch, toggle on Best flips, `/flips set safe`).
- Settings reorganised: 14 tabs with section headings, new Advanced tab, Kuudra/Diana in Combat, macros in Extras.
- Min item price / min profit per flip / craft min profit: one box each, coins or % of budget (old values migrated).

## 8.7.3
- Bazaar flips: min item price (fixed or % of budget) instead of max items.

## 8.7.2
- Bazaar flips: min profit per flip (fixed or % of budget) and max items per flip.

## 8.7.1
- BIN source fallbacks + daily averages (fixes empty Craft → AH), skip reasons, min profit from budget, safe-only filter.

## 8.7.0
- /market: mayor, event and trend signals from price history with leave-one-out backtesting.

## 8.6.0
- Craft flips counted in flip profit; instant buy / sell messages tracked.

## 8.5.3
- Crafted/bought items after menus and other-activity items no longer counted.

## 8.5.2
- Mining events from the shared Soopy service (anywhere), tab list as backup.

## 8.5.1
- Boss timers (tab-list based, remembered) with toggles + custom tab lines.

## 8.5.0
- Timers panel with per-timer toggles.

## 8.4.3
- Greenhouse: added Devourer, Glasscorn, Phantomleaf, Timestalk, Godseed, Jerryflower.

## 8.4.2
- Compactor: no negative item lines; compacted results always counted.

## 8.4.1
- Greenhouse: found/locked from the Mutations Sack wording (Stored 0 counts as found), corrects both ways.

## 8.4.0
- Real fishing rods only, per-activity item attribution, fishing extras (bobber, sea creatures, rare alert), visitor Bazaar list, greenhouse diagnostics.

## 8.3.2
- /shards uses the Attribute Menu's Source / Attribute Level / Syphon numbers directly.

## 8.3.1
- Shards: lore-based shard matching, diagnostics file + chat count, manual Edit / set / reset.

## 8.3.0
- /shards: subtracts syphoned progress + Hunting Box shards; Bazaar button copies the amount to buy.

## 8.2.1
- Colors behind items via the background event (tooltips on top), rarity found in the whole description.

## 8.2.0
- Fixed event registration (menus overlays + search keys), pest head-only highlight, ultimate enchants by name, prominent lockpick square, sturdier /shards, graphic HOTM/HOTF trees with staged plans.

## 8.1.1
- Full check: rarity fallback that never covers items, commands list completed.

## 8.1.0
- Menu hooks attached after init (rarity/search/labels/tooltip scroll work), pests detected as Garden bats/silverfish, green lockpick square, Mining best-ore + purse line toggles, HOTM/HOTF tree + upgrade order.

## 8.0.0
- Performance monitor + mode, shader fallback, clash-free first layout, playstyle setup, macro keys, Inquisitor sharing + party waypoints, mineshaft alert, CH waypoints, boss health, contest standing, storage overview, swim/swing/rainbow extras.

## 7.0.0
- Glow outlines for many targets, Diana burrow finder, fishing bite alert, chat filter + sound muting, item labels, /hotm and /hotf guides, crop fortune fix.

## 6.8.0
- Pests: bright green glowing outline (mixins, skipped safely if they can't attach) + green particle box; fire markers removed.

## 6.7.0
- Treasure chest lockpick helper (particle hook via a mixin that's skipped safely if it can't attach).

## 6.6.0
- Crystal Hollows treasure chest helper: highlight + despawn timer, chests/min, Double Powder, chest loot.

## 6.5.0
- Layered menu drawing (rarity under items everywhere, visible search highlights), search box key handling, wrapped footers, pest dust box, shards from Hypixel item list, greenhouse unlock detection + planting surfaces + clearer visuals.

## 6.4.1
- Menu caching (smooth scroll/search), duplicate screen hooks after resize, rarity behind items, chat-open counting, small performance fixes.

## 6.4.0
- Sack double count fixed, rarity colors in menus and hotbar, best crop removed from the Farming HUD.

## 6.3.0
- Pest box + glow, greenhouse sack fix and grid panel, wrapping/scrolling/search in menus, talismans search + grouping, craft flips use all settings, search box placement, attribute shards only, purse coins as profit.

## 6.2.0
- Profit fix for crafting from sacks, rare drops shown once, item names, pest highlighting, /greenhouse, craft flips (Bazaar + AH), /shards, talismans other ways + NPC prices, inventory search, daily/weekly/monthly profit, /dungeon info.

## 6.0.0
- Renamed to SkyAssist (mod ID skyassist, config folder moved automatically, /skyassist command).

## 5.6.0
- Commands list with explanations (/profit help, Settings → Commands, main menu).

## 5.5.0
- Menus for the current session, history, totals, best now and Bazaar flips; main menu on P.

## 5.4.0
- /talismans opens a menu (top 10/20/50, crafting toggle, max price, AH / Recipe buttons). /talismans chat keeps the chat list.

## 5.3.1
- Ultimate enchantments keep their bold pink at every level.

## 5.3.0
- Torrhus Canyon / Moonglade Marsh, Desert Whispers, Dungeon Hub fix, reliable HUD dragging in chat.

## 5.2.0
- /talismans: cheapest Magical Power you don't have yet (AH or craft, upgrade-aware).

## 5.1.0
- Ice Fill, Creeper Beams, Teleport Maze, Tic Tac Toe, Quiz and Melody solvers (particle markers + HUD).

## 5.0.0
- Builds for 26.1 and 26.2, craft cost tooltips, sign calculator and /calc, HUD layout presets, Three Weirdos / Blaze / terminal solvers.

## 4.3.0
- Mouse-wheel scrolling for tooltips that don't fit on screen.

## 4.2.0
- Enchantment colors by level (perfect / great / good / low) with ✦ on maxed enchants.

## 4.1.0
- Foraging detection fixes, Combat kills, grind HUDs (Zealots etc.), smarter item grouping.

## 4.0.0
- First-time setup check (`/profit setup`): shows what the mod can see and how to fix what's missing.
- Settings: search box, reset button per setting and per tab, Hidden items list, Mod Menu "Configure" button.
- HUD editor (`/profit gui`): every panel can be moved, resized (middle-click) and hidden (right-click its title).
  Optional separate panels for the secret finder, Jacob's contest and "Best now"; optional position per activity.
- Duplicate-feature detection for SkyHanni, Skyblocker, Odin and Secret Routes (`/profit dedupe`).
- `/profit report` copies a diagnostics report; `/profit note <text>` tags a session; setting profiles
  (`/profit profile save|load|list|export|import`); new-build check against the GitHub repo.
- The HUD text is rebuilt 4x a second instead of every frame.

## 3.1.0
- Combat only starts after real fighting; HUD follows your current activity; ~50 new settings.

## 3.0.0
- Bazaar tax in profit, real item IDs, Spent list, menu rewards, /profit debug, Hypixel Mod API location,
  price tooltips, dungeon chest profit, Garden visitors, Jacob's contests, mayor, Kuudra, Diana, CSV export.

## 2.x
- Bazaar flip tool, best-now suggestions, dungeon secret finder, settings menu and keybind,
  mining / foraging / fishing / combat HUDs, rare drops, shards, HUD dragging, icons.

## 1.0.0
- Farming profit counter.
