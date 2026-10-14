# Changelog

## 8.14.1
Profit fixes. Every profit panel was checked against what the game really does; these were wrong:
- **Dungeon and Kuudra reward chests** were mostly missed: only stackable items that landed in the inventory counted, so essence, enchanted books, armour, weapons, shards and pets never did; Kuudra's *Free Chest* / *Paid Chest* windows were not recognised; the price was only charged when some stackable item happened to arrive. Now the chest window is read once it has loaded (title, contents, the price on *Open Reward Chest*, re-read after a Kismet reroll) and counted once when you open it (coins leave the purse, a key is used up or something arrives): essence, books priced by enchantment (`ENCHANTMENT_ULTIMATE_WISE_5`), gear, attribute shards and pets (by type and rarity) go to the run's profit, the price (coins + Dungeon Chest Key, or the Kuudra key) is subtracted. A Kismet Feather used for a reroll counts as used up by the run. Things nobody buys (dungeon discs, the three fish, Jerry / Bank / Combo / No Pain No Gain books, Feather Falling VI+ and Infinite Quiver VI+ books) are left out quietly; anything else without a price is mentioned in the chat summary. The window's items arriving in the inventory are not counted a second time.
- **Kuudra keys** are priced as what they cost to make: base coins (200k / 400k / 750k / 1.5M / 3M by tier) + 2 / 4 / 16 / 40 / 80 Enchanted Mycelium or Red Sand (the cheaper) + 2 Corrupted Nether Stars. New setting *Combat → Kuudra → Kuudra key cost* sets your own number instead.
- The **Croesus** overview (chest profit list) uses the same prices and costs.
- **Bazaar "Order Flipped!"**: Hypixel announces `5x Coal for 13.0 coins of total expected profit`; the 13.0 is the profit, but it was read as a sell price. The sell offer's price is now worked out from the profit, your buy price and the Bazaar tax, checked against the market price (a stale or mismatching one falls back to the market), and the right buy order is picked when you have several for the same item.
- A **sale of more items than you bought** counted all its revenue against only the part you had a purchase for. Only the part you bought counts as a flip now (the chat line says "only N of the M sold were bought by you").
- **Auction flips** ignored the listing fee. It is estimated from the price and subtracted: BIN 1% under 10M, 2% up to 100M, 2.5% above; normal auctions 5% (the small duration fee is not included). Switch: *Market → Your orders → Count AH listing fee*.
- **PET DROP!** pets were priced as an unknown item or at the wrong rarity. The rarity is read from the colour of the pet's name in the message and the drop is listed as e.g. `Golden Dragon (Legendary pet)`, priced at its lowest BIN.
- **Profit per hour** in dungeons and Kuudra only counted the seconds you hit something; the time inside a dungeon / Kuudra's Hollow counts now. Diana allows gaps of up to 90 s between burrows.
- Status page: new *Dungeon / Kuudra chests* line.
- Not modelled (yet): the Kuudra pet's bonus Crimson Essence, the Auction House duration fee, listings that expire unsold (their fee is lost).

## 8.14.0
- Alerts for the things players usually set up alerts for, all display-only (big text + ding, optional chat line), each with its own switch in its activity's tab:
  Scatha / worm spawn (direction + distance once its name tag is seen; *Scatha only* mode), rare sea creatures (23 legendary / mythic spawn messages), pet and rare drops (pets only / very rare and up / all), stash, Garden pests (one, several, while away) and visitors, finished commissions, Golden / Diamond goblins, Minos Inquisitor, your slayer boss spawning, Cold (default 70), server restart (first sight, 1 minute, last 15 s), Dark Auction / Cult of the Fallen Star starting (X minutes before), big alert for a filled Bazaar order, auction sold / outbid.
- Settings → **Alerts**: every alert switch in one place (grouped by activity) with a **Test alert** button; the test shows the real alert over the settings menu. Alerts in the activity tabs sit in their own *Alerts* section.
- New outline *Glow: Scatha & worms* (Mining → Glow).
- Settings tidied: 14 tabs in a better order (General, HUD, Alerts, Farming, Mining, Foraging, Fishing, Combat, Dungeons, **Market** (was Bazaar), Items, Timers, **Chat & controls** (was Chat & sounds + macro keys), Advanced); the Extras tab is gone (swimming pose / slow swing → General → Performance & look, rainbow enchants → Items → Enchants); every setting sorted by what it does inside headed sections; sub-options are indented and greyed while their main switch is off; internal "last seen version" hidden.
- **Crystal Hollows minimap** (Mining → Crystal Hollows map, on): top-down map of the 621 × 621 cave on the HUD with the four quarters, Crystal Nucleus, an arrow for your position and facing, Magma Fields tint (Y ≤ 64), places learned from the sidebar / NPC speech / party coordinates (Jungle Temple, Goblin Queen's Den, Mines of Divan, Lost Precursor City, Khazad-dûm, Fairy Grotto, Dragon's Lair), treasure chests and party waypoints; direction + distance list under the map. Own HUD panel (move / resize / hide in the HUD editor). Settings: size, opacity, places, list, chests.
- Rare sea creature alert and the older sea-creature tag alert share one alert (no double ding). Duplicate detection and Status page know the new features.

## 8.13.0
- Price alerts: `/alert <item> below|above <price> [buy|sell]` pings you (ding + big text + a chat line with an *Open in Bazaar* button) when a Bazaar item crosses your price; `/alerts` menu; saved in `price-alerts.json`; Bazaar refreshes as often as *Order check every* while alerts wait.
- Pickaxe ability: cooldown line on the Mining HUD and a ready alert, read from Hypixel's chat messages.
- Skill XP/h line (with time to the next level) from the XP numbers above the hotbar.
- Slayer boss kill time (best / average per boss and tier, `slayer-times.json`) in chat and on the Combat HUD.
- Big on-screen alerts + sound for all of the above (Settings → General → Alerts), optional full-inventory warning.
- Keep cursor between menus (on): the pointer stays where it was when SkyBlock swaps one menu for another.
- Quick-command buttons beside the inventory (off): Name=/command list in the settings; a command is sent only when you click.
- Upgrade value in item tooltips (on): enchants as books, Hot / Fuming Potato Books, Recombobulator, gems, drill parts and a few more at Bazaar prices (a rough extra).
- Optional yaw / pitch line on the Farming HUD (off).
- Settings reordered with room for new ones (each new option sits in its own activity tab); "Disable swimming pose" now warns that it also changes your hitbox and water movement (Hypixel's rules don't allow changing movement; it stays off by default); Dedupe and the Status page know Firmament, NoFrills, SkyOcean and SkyHanni's overlaps.

## 8.12.0
- Status page (`/skyassist status`): every data source with state, reason and Retry; caught problems; what the mod can see; Copy report (`/profit report`).
- Safety net: errors inside one feature are caught and listed instead of crashing the game or the connection; a feature that keeps failing pauses for a minute.
- Shared download helper: retry with growing waits, keeps last good data, shows why a source failed.
- Atomic saves for settings, history, totals and data files; unreadable files are kept as `.broken` instead of being replaced.
- What's new page (`/skyassist news`) and a one-time chat notice after an update (setting: Tell me what's new).
- Update check follows redirects and the renamed `Kalapath/skyassist` repo; Gradle 9.4.1 / 9.5.0 per Minecraft version in CI; mod icon and links; small number-format and text fixes.

## 8.11.1
- /market: live-updating menu, waits for prices, tolerant history/mayor parsing with week fallback, rate-limit retry, error shown, Movers tab.

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
