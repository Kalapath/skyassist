# SkyAssist (Hypixel SkyBlock, Fabric 26.1.2 and 26.2)

Tracks profit for **farming, mining, foraging, fishing and combat/slayers**, each with its own HUD.
The HUD automatically shows whatever you did in the last minute (or the island you're on).
Every session resets after 15 minutes of no activity and is saved to a history log.

## What counts as activity
| HUD | Starts / stays alive when you... |
|---|---|
| Farming | break crops, kill pests, use a vacuum |
| Mining | mine ores/gemstones in Dwarven Mines, Crystal Hollows, Glacite Mineshafts (one combined HUD) |
| Foraging | chop logs on Galatea / The Park, get Tree Gifts |
| Fishing | cast or reel a fishing rod, kill sea creatures |
| Combat | hit mobs anywhere else, start a slayer quest, kill a slayer boss |
| Dungeons | being inside a dungeon run (the whole time counts as run time), hitting mobs, opening reward chests |
| Kuudra | being in Kuudra's Hollow, hitting mobs, opening its chests |
| Diana | digging burrows, killing mythological mobs |

## What's counted as profit
- Items going into your inventory or sacks (anything stackable that has a Bazaar / lowest-BIN price)
- Rare drops (RARE DROP!, PET DROP!, GOOD/GREAT/OUTSTANDING CATCH! ...) that weren't already counted as items
- Attribute shards (they go to the Hunting Box)
- Dungeon and Kuudra reward chests: what the chest window shows (essence, books, gear, shards, pets) counts when you open it, and its price (coins, keys) is subtracted
- Flips (Bazaar and Auction House): sale minus what you paid, after the Bazaar tax / auction claim tax and the estimated auction listing fee
- Minus slayer quest costs, if you set `slayerQuestCost` in the config

## Commands
`/profit` shows the HUD you're looking at. `/farmprofit`, `/miningprofit`, `/foragingprofit`, `/fishingprofit`
and `/combatprofit` show that specific activity. All of them print **profit only** (time, profit, profit/h,
shards, rare drops, every item).

| Sub-command | What it does |
|---|---|
| `total` | Lifetime totals (all activities, or just that one) |
| `copy` | Copy "Mining: 12.3M coins in 2h 10m (5.6M/h)" to your clipboard |
| `ignore <item>` / `unignore <item>` | Stop / start counting an item (e.g. `/profit ignore Hay Bale`) |
| `history [count]` | Past sessions (`/profit history` = all, `/miningprofit history` = mining only) |
| `reset` | End that session now (still saved to history) |
| `scale <size>` | HUD size (0.5–3) |
| `icons` | Item icons on/off |
| `edit` | How to move the HUD / hide items |
| `details` | Toggle the HUD between full details and profit only |
| `hud` | HUD on/off |
| `move <x> <y>` | Move the HUD |
| `prices` | Refresh prices |
| `reload` | Reload the config |

## Moving & editing the HUD
Open chat (**T**). While chat is open:
- **Left-click and drag** the HUD to move it.
- **Right-click** an item, shard or rare-drop line to hide it (it stops counting toward profit). Undo with `/profit unignore <item>`.

`/profit scale <0.5-3>` resizes the HUD, `/profit icons` toggles item icons (icons appear for items that have been in your inventory).

## Slayer quest costs
Detected automatically from your purse on the sidebar when a quest starts (handles Aatrox discounts etc.).
If you'd rather use a fixed number, set `slayerQuestCost` in the config (anything above 0 overrides auto-detection).

## New in 8.14.1 — profit fixes
Several profit numbers were wrong. Every profit panel was checked against what the game really does; this is what turned up.

| Panel | What was wrong | Now |
|---|---|---|
| Dungeons / Kuudra | Reward chests were mostly missed. Only stackable items arriving in the inventory counted, so essence, enchanted books, armour, weapons, shards and pets never did; Kuudra's Free / Paid Chest windows were not recognised; the price was only charged when a stackable item happened to arrive | The chest window is read once it has loaded (title, contents, the price on Open Reward Chest). When you open it (coins leave your purse, a key is used up, or something arrives) the contents are added to the run's profit and the price is subtracted, once. Books are priced by enchantment, pets by type and rarity. A Kismet Feather used for a reroll counts as used up by the run |
| Kuudra | A key had no price | A key costs what it takes to make: its coins plus the materials plus 2 Corrupted Nether Stars, at today's prices. Settings → Combat → Kuudra key cost sets your own number |
| Dungeons / Kuudra | Profit per hour only counted the seconds you hit something | The whole time inside the dungeon / Kuudra's Hollow counts |
| Flips (Bazaar) | "Order Flipped! 5x Coal for 13.0 coins of total expected profit" - the 13.0 is the profit but was read as a price | The sell offer's price is worked out from the profit, your buy price and the Bazaar tax, and checked against the market |
| Flips (Bazaar) | Selling more items than you had bought counted all the revenue against part of the cost | Only the part you bought counts as a flip; the chat line says so |
| Flips (Auction House) | The fee for listing the item was ignored | Estimated and subtracted: BIN 1% under 10M, 2% up to 100M, 2.5% above; normal auctions 5%. The small duration fee is not included. Settings → Market → Count AH listing fee |
| Pet drops | PET DROP! pets had no price or the wrong rarity | The rarity is read from the colour of the name in the message, so a Legendary Golden Dragon is priced as one |
| Diana | A pause of 30 s between burrows stopped the clock | Gaps of up to 90 s count |

The chest windows, the "Open Reward Chest" price text and the pet BIN keys follow what other SkyBlock mods read, but could only be
checked here against made-up windows, not on Hypixel. The Status page (`/skyassist status`) has a new "Dungeon / Kuudra chests"
line: it turns green after the first chest was counted (and stays grey if none was; Copy report includes it when you ask for help).
Still not included: the Kuudra pet's extra Crimson Essence, the auction duration fee, and listings that expire
unsold (their fee is lost).

## New in 8.14.0 — alerts, a tidier settings menu and a Crystal Hollows minimap
**Display only, as always.** An alert shows big text, rings and sometimes adds a line to chat. Nothing in this version clicks,
walks, aims or buys for you (a button or key does something only when *you* press it).

### Alerts for the usual things
Big text in the middle of the screen plus a ding, read from Hypixel's own chat lines, name tags and sidebar. Every alert has its
own switch **in its activity's tab** and all of them are also collected in **Settings → Alerts** (with a **Test alert** button that
shows the real thing over the menu, so you can judge size and sound). The alert style (on/off, ding) is in General → Alerts.

| Alert | What sets it off | Where |
|---|---|---|
| Scatha / worm | Hypixel's "You hear the sound of something approaching…"; then the name tag decides: **SCATHA!** (with direction, distance and a chat line), *Worm*, or "Worm or Scatha!" if no tag shows up. *Scatha only* ignores plain worms | Mining → Alerts |
| Rare sea creature | the spawn message of the legendary / mythic ones: Carrot King, Water Hydra, Yeti, Reindrake, Phantom Fisher, Grim Reaper, Great White Shark, Abyssal Miner, Thunder, Lord Jawbus, Plhlegblast, Alligator, Titanoboa, Ragnarok, Fiery Scuttler, Wiki Tiki, Blue Ringed Octopus, The Loch Emperor, Nessie, Puddle Jumper, Frog Prince, Silkbreeze, Giant Isopod | Fishing → Alerts |
| Pet / rare drop | PET DROP!, INSANE, CRAZY RARE, VERY RARE and RARE drops. *Alert for* picks the lowest one: pets only, very rare and up (default), all rare drops | General → Alerts |
| Stash | "You have N items / materials stashed away!" (at most once a minute) | General → Alerts |
| Server restart | the "Server closing" countdown in the sidebar: when it first shows, at one minute, and for the last 15 seconds | General → Alerts |
| Pests | one pest appeared, several spawned, or pests spawned while you were away, with the plot | Farming → Alerts |
| Visitor | a visitor "has arrived on your Garden!", with their name (off by default) | Farming → Alerts |
| Commission done | a "Commission Complete! Visit …" message, naming who to visit | Mining → Alerts |
| Golden / Diamond goblin | "A Golden Goblin has spawned!" | Mining → Alerts |
| Cold | the Cold on the sidebar (Glacite Tunnels, Mineshafts) reaches *Cold alert at* (default 70; you freeze at 100) | Mining → Alerts |
| Your slayer boss | its name tag shows up after a quiet spell | Combat → Slayer |
| Minos Inquisitor | when you dig one up | Combat → Diana |
| Dark Auction / Cult of the Fallen Star | *Minutes before* it starts (default 1), and again when it opens (off by default; follows the Timers panel switches) | Timers → Event alerts |
| Order filled, auction sold / outbid | like before; the big alert for a filled order is a new switch | Market → Alerts |

Already there and now listed in the same place: Jacob's contest, Mineshaft found, pickaxe ability ready, fishing bite,
price alerts, hot flips and inventory full. The rare sea creature alert and the older "sea creature" tag alert are one alert now
(no double ding). There is also a new outline for Scatha and worms: Mining → Glow → *Glow: Scatha & worms*.

### Tidier settings
- **14 tabs** in a sensible order: General, HUD, **Alerts**, Farming, Mining, Foraging, Fishing, Combat, Dungeons,
  **Market** (was Bazaar), Items, Timers, **Chat & controls** (was Chat & sounds, now with the macro keys), Advanced. The Extras
  tab is gone: swimming pose and slow swing are in General → Performance & look, rainbow enchants in Items → Enchants.
- Inside a tab, settings sit in headed sections (*Farming HUD*, *Pests*, *Alerts*, *Values*…), main switch first.
- **Sub-options are indented** under the switch they belong to, and turn **grey** while that switch is off (you can still change
  them, they just have no effect until the switch is on).
- The search box finds a setting by its name, its description or its tab; `/flips settings` and the *Best settings* button open Market.

### Crystal Hollows minimap
A small top-down map on the HUD while you are in the Crystal Hollows (north is up). **Settings → Mining → Crystal Hollows map**
(on by default; it needs *HUD → Show HUD*; drag it with `/profit gui` like any panel, the editor shows a preview anywhere).
- The whole 621 × 621 cave with its four quarters (**J**ungle, **M**ithril Deposits, **G**oblin Holdout, **P**recursor Remnants)
  around the **Crystal Nucleus**, a white **arrow** for you that turns the way you face, and the quarter you are in on the title line.
- Down in the **Magma Fields** (Y 64 and below) the map is tinted orange.
- **Places** are different in every lobby, so the map learns them from what the game tells you: the place name on the sidebar when
  you walk in (Jungle Temple, Goblin Queen's Den, Mines of Divan, Lost Precursor City, Khazad-dûm, Fairy Grotto, Dragon's Lair),
  the speech of the NPC who lives there (Kalhuiki Door Guardian, King Yolkar, Golden Dragon, Professor Robot), and coordinates
  your party shares (*x: … y: … z: … | Jungle Temple*). A list under the map gives the direction and distance to each one.
  Forgotten when you change lobby or leave the Hollows for a couple of minutes.
- Your **treasure chests** (green → yellow → red as they run out of time) and party waypoints are dots too.
- Settings: *Map size* (60–240 px), *Map opacity*, *Show found places*, *Place list under the map*, *Show treasure chests*.
- It never reveals anything the game doesn't show you: no unseen mobs, no hidden structures. Places appear after you have been in
  them, not before.

### Not verified in game
The message and sidebar formats are the ones other SkyBlock mods read (taken from their source), I couldn't test them on Hypixel.
If an alert or a place doesn't react, *Status → Features* shows whether the message was ever seen, and
`config/skyassist/unrecognised-messages.txt` collects lines that looked relevant.

## New in 8.13.0 — ideas from other SkyBlock mods
I compared SkyAssist with what players like in SkyHanni, Skyblocker, Odin, Firmament, NoFrills and similar mods, and added the
gaps that fit this mod's rule: **display only, nothing plays for you.** Every new option is in the settings, under its activity.
- **Price alerts** (Bazaar): `/alert enchanted diamond below 1500` pings you when the price crosses your number
  (`above` works too; add `sell` to watch what you'd get selling instead of what you'd pay buying). You get a ding, big text
  in the middle of the screen and a chat line with an *Open in Bazaar* button (you place the order yourself). `/alerts` lists
  them with the current price, `/alert remove <number>` and `/alert clear` tidy up. While an alert is waiting, prices refresh
  as often as *Order check every (s)*. Settings → Market → Alerts.
- **Pickaxe ability** (Mining): Hypixel says when you use it and when it's ready again; the cooldown shows on the Mining HUD
  and a ready alert rings. It never uses the ability for you. Settings → Mining (HUD line and Alerts).
- **Skill XP/h** (HUD): the XP numbers Hypixel shows above your hotbar become *Farming XP: 1.2M/h · next level in 12m 30s*.
  Settings → HUD → Main HUD → Show skill XP/h.
- **Slayer boss kill time** (Combat): timed from when your boss appears to the kill; chat shows it with your best and average,
  the Combat HUD keeps the last one. Saved in `slayer-times.json`. Settings → Combat → Slayer.
- **Big on-screen alerts** (General → Alerts): one place for the large text + ding used by the alerts above; also an optional
  **full-inventory warning** (off by default).
- **Keep cursor between menus** (Items → Inventory, on): when SkyBlock replaces one menu with another (SkyBlock Menu → Pets…),
  Minecraft puts the mouse pointer in the middle of the screen; this puts it back where it was. It only moves the pointer.
- **Quick-command buttons** (Items → Inventory, off by default): small buttons beside your inventory (Pets, Wardrobe, Ender
  Chest, Storage, Skills, SB Menu, Bazaar, Auctions; edit the list in the settings as `Name=/command; …`). A command is sent
  only when **you click** a button, exactly like a macro key.
- **Upgrade value in tooltips** (Items → Tooltips, on): *Upgrades ≈ 5.2M (enchants 4.1M, potato books 0.9M, gems 0.2M)* from the
  item's own data at Bazaar prices. It is a rough extra, not a full price: reforge, stars, pets and the item itself aren't included.
- **Yaw / pitch line** (Farming → Farming HUD, off): your exact look direction for lining up with crop rows. Display only.
- **Works next to other mods**: duplicate detection now also knows SkyHanni's skill XP and ability alerts, and the Status page
  lists Firmament, NoFrills and SkyOcean when they are installed.
- **Left out on purpose**: anything that plays for you or changes how you move or what the game does (auto-requeue, auto-tip,
  keybinds that click menu slots, macros, automatic buying / selling / flipping, hiding invisible mobs). Hypixel's mod rules
  forbid these and SkyAssist stays display-only. Tell me about any other feature you miss from a mod you like.
- **Not verified in game**: the chat and action-bar formats the new features read (ability messages, XP numbers, boss tags) and
  the item data behind the upgrade value come from how Hypixel behaved when this was written. If one doesn't react, the
  *Status → Features* page shows whether the message was ever seen, and `config/skyassist/unrecognised-messages.txt` collects
  lines that looked relevant.

## New in 8.12.0 — status page, safety net, tidy-up
A full pass over the whole mod, aimed at "it should never crash, and when something doesn't load you should see why".
- **Status page** (`/skyassist status`, or *Status* in the main menu; `/profit debug` opens the same page):
  - **Data**: every online source (Bazaar, item list, lowest BIN, averages, recipes, enchants, mayor, contests, mining events,
    quiz answers, market history, update check) with its state, how long ago it loaded, the reason if it failed
    (no internet, rate limit, address changed, unreadable data) and a **Retry** button. *Retry failed* tries them all again.
  - **Problems**: features that hit an error and were skipped, with how often, when, and the technical details on hover.
  - **Features**: what the mod can see in the game (tab list, location, sidebar, particle / sound / glow hooks, menu overlay,
    search box...) with a hint for each ✖.
  - **About**: versions of the mod, Minecraft, Fabric and related mods. **Copy report** (also `/profit report`) puts all of it
    on the clipboard to paste when asking for help. `/skyassist status chat` is the short version in chat.
- **Safety net**: an error inside one feature no longer crashes the game or drops you from the server. Every HUD panel, tick
  step, tooltip, menu, chat handler and game hook is guarded; the problem is listed on the Status page (and once in chat),
  and everything else keeps working. A feature that fails over and over is paused for a minute, then tries again.
- **Downloads** share one reliable helper: they retry by themselves with growing waits, keep using the last good data while a
  source is down, and tell you which one failed and why instead of silently showing nothing.
- **Files are saved safely**: settings, history, totals and the other data files are written in one step (a crash mid-save
  can't leave half a file), and a file that can't be read is kept as a copy ending in `.broken` next to it (e.g. `config.json.broken`) instead of being quietly replaced by defaults.
- **What's new** (`/skyassist news`): the notes of the latest versions, and one line in chat the first time you join after an
  update (turn off in Settings → General → Updates).
- **Fixes**: the update check follows the renamed `Kalapath/skyassist` repo (the old redirect was never followed, so it could
  not find new builds), 26.2 builds with the Gradle version Loom 1.17 needs, mod icon and links in the mod list, number
  formatting for NaN / rounding edge cases, a few settings and menu texts that had gone stale.

## New in 8.11.1
- **/market fixes**: the menu updates by itself while data loads (no more empty screen until you reopen it), waits for
  Bazaar prices instead of giving up, no longer depends on your flip min-volume, reads more history formats (and falls back
  to the last week if long history isn't available), works out mayor terms from election years, retries when rate-limited,
  and says exactly what went wrong (e.g. "HTTP 400 from …") instead of showing nothing.
- New **Movers** tab: biggest 24 h / 7 d price moves, so there's always something to see even when no signal passes the backtest.

## New in 8.11.0
- **★ Best settings** (every /flips tab, Settings → Market, or `/flips tune`): tries ~500 combinations of min item price,
  min margin, min volume and number of orders on today's Bazaar prices with your budget, and keeps the one with the most
  profit per bit of work. Work per order goes up when an item is fought over (lots of orders → you get outbid and relist),
  fills slowly, or means moving thousands of items. Score = profit/h ÷ work^0.7, so a few big calm flips beat many small busy
  ones unless the extra orders clearly pay. It also turns Safe only on, sets min profit per flip / per craft and the hot-flip
  alert to match, and tells you what running more orders would earn and cost. **↺ Undo** / `/flips untune` restores your settings.
  Budget and volume share are left as you set them.

## New in 8.10.0
- **Auctions on the flip HUD** (the panel is now "Bazaar & AH flips"): your listings with price and time left, marked
  **sold, claim it**, **expired**, or **undercut → lowest BIN**; your bids marked **top**, **outbid** or **won, claim it**.
  A ding when something sells or you're outbid.
- Read from chat (auction started, sold, collected, bid placed, outbid, claimed, BIN purchase) and from **/ah → Manage
  Auctions / Your Bids** (open them once to pick up auctions made before updating; the menus always win).
- AH buys (BIN or won bids) count as flip buys, so selling them later (AH or Bazaar) shows the profit in the flip log and
  "Flip profit today". Settings → Market → Your orders: Show auctions on HUD, how many to list.

## New in 8.9.0
- **Craft flips only show what you can craft.** Each recipe's requirement (collection tier, slayer level, HotM tier or
  skill, e.g. "Diamond VIII", "Zombie Slayer 5", "HotM 5") is checked against your own levels. SkyAssist reads them when
  you open your **/collection** categories, the **slayer menu**, **/skills** and **/hotm**, and keeps them up to date from
  level-up messages in chat (saved in `config/skyassist/unlocks.json`).
- Locked recipes are hidden (both Craft → Bazaar and Craft → AH). Recipes whose requirement SkyAssist hasn't seen yet are
  shown with a note, or hidden too with *Hide when level unknown*. Toggle with "Only what I can craft" on the craft tabs or
  in Settings → Market → Craft flips. The footer shows how many levels are known and how many recipes were hidden.

## New in 8.8.0
- **Safe flips only** now works for normal Bazaar flips too (one switch for every flip tab, also on the Best flips tab and
  `/flips set safe on`). It hides flips with a ⚠ warning, under 3% margin, or a buy/sell gap over 25%; the footer says how many it hid.
- **Tidier settings**: 14 tabs instead of 16 (Kuudra and Diana moved into Combat, macro keys into Extras, which became *Chat & controls* in 8.14.0), every tab split into
  headed sections, main on/off switches first, and URLs / counting rules / extra areas moved to a new **Advanced** tab.
- Flip filters are one box each now: type coins (`50k`) or a % of your budget (`0.1%`) into *Min item price*, *Min profit per
  flip* and *Craft flips: min profit*. Your old settings are carried over. In the flips menu the buttons cycle through presets.

## New in 8.7.3
- Bazaar flips: **min item price** (fixed, or ☐ a % of your budget — 0.1% of 50m = 50k each) so cheap items you'd need
  thousands of don't show. Replaces "max items per flip".

## New in 8.7.2
- Bazaar flips: **min profit per flip** (fixed, or ☐ a % of your budget) and **max items per flip** (hides cheap items
  you'd need thousands of to use your budget). Quick tick boxes on the Best flips tab.

## New in 8.7.1
- **Craft → AH fixed**: lowest-BIN prices now come from several sources in turn (lb.tricked.dev — used by Skytils —,
  your configured one, moulberry.codes). AH flips were empty because moulberry.codes alone returned nothing.
  `/profit debug` shows how many BIN prices loaded and from where.
- Craft flip tabs say why recipes were left out ("810 not sold on the AH, 95 ingredient without price...").
- ☐ **Min profit = % of budget**: minimum profit per craft from your flip budget (default 1%).
- ☐ **Safe only**: hides volatile (25%+ buy/sell gap), competitive (under 3% margin) and possibly inflated
  (BIN 30%+ over its daily average, or 100%+ margin) flips.

## New in 8.7 — market signals
`/market` (also in the main menu): statistics from Coflnet's public price + mayor history, not guesses.
- **Mayor**: what the election leader's (and current mayor's) past terms did to each item (first 3 days vs. the week before).
- **Events**: the same around past Spooky Festivals, Seasons of Jerry and New Years.
- **Trends**: prices unusually low / high vs. the last 7 days (z-score), with 24 h and 7 d change.
- **Backtest**: each signal is checked against every past case (leave-one-out); only signals that were right often enough
  are shown, with their hit rate (e.g. 4/5). Thresholds in Settings → Market → Market signals.
- Data is cached for a day in `config/skyassist/market`; the first load takes a minute or two in the background.

## New in 8.6
- **Craft flips count in flip profit**: selling an item you crafted from Bazaar-bought ingredients logs a craft flip
  (cost = what you paid for the ingredients, 2 levels deep, the rest at today's price). Instant buys and instant sells
  are now read too. Craft flips are marked "(craft)" in the profit log.

## New in 8.5.3
- Crafting / Bazaar flipping no longer leaks into profit HUDs: right after a menu (crafting, Bazaar, chests) only the
  activity's own items count, and items clearly from another activity (fish on Farming, crops on Mining...) never count.
  Visitor rewards and dungeon / Croesus chests still count as before.

## New in 8.5.2
- Mining events from anywhere: the Timers panel shows the current Dwarven Mines and Crystal Hollows events with time
  left, read from Soopy's shared mining-event service (the one SkyHanni's Mining Event Tracker uses). Falls back to your
  own tab list if the service is down. `/profit debug` shows whether it's reachable.

## New in 8.5.1
- Boss timers in the Timers panel, each with a switch: Broodmother, Endstone Protector, Ender Dragon, Arachne, Kuudra,
  Vanquisher, Golden Goblin. Their state is read from Hypixel's tab list on their island and remembered with
  "x min ago" when you leave. "Extra tab lines to watch" adds any other tab-list line you want.

## New in 8.5
- **Timers panel** (its own HUD panel, move it with `/profit gui`), each timer switchable in Settings → Timers:
  Dark Auction (:55 every hour), current Dwarven Mines event (from your tab list / sidebar), Cult of the Fallen Star,
  Spooky Festival, Traveling Zoo, Season of Jerry, New Year, Hoppity's Hunt, Jacob's contest, plus today's SkyBlock date.

## New in 8.4.3
- Greenhouse: all 40 mutations — added the missing Legendaries Devourer, Glasscorn, Phantomleaf, Timestalk, Godseed
  (special: every positive effect) and Jerryflower (from a Fertilized Jerryseed).

## New in 8.4.2
- Personal Compactor: raw items it uses up no longer show as negative numbers on the HUD (they still count in profit, so
  the total stays exact), and the compacted item always counts even if it arrives a few seconds later or in your sacks.

## New in 8.4.1
- Greenhouse unlocks read the Mutations Sack exactly as Hypixel words it: "LOCKED" / "Discover this mutation…" =
  not found; a "Stored: N/64" line = found (even with 0 stored). Opening the sack corrects wrong marks both ways.

## New in 8.4
- **Fishing HUD only for real fishing rods**: rods whose rarity line says "FISHING ROD" (a Grappling Hook, Soul Whip,
  Flaming Flay... no longer start a fishing session).
- **Only what the activity gave counts**: items count for a profit HUD if they're that activity's own items or arrived
  within a few seconds of your last action (break, catch, hit...). Trades, other people's drops etc. don't count.
  Settings → Advanced → What counts as profit: on/off and the window.
- **Fishing extras** (like SkyHanni): bobber timer, your living sea creatures (count + oldest), a ding at your cap,
  and a rare sea creature alert with [Share with party].
- **Visitor shopping list**: opening a Garden visitor lists what they want; [Bazaar] opens the item and copies the
  amount you still need (you place the order).
- **Greenhouse**: every menu showing mutations is saved to `config/skyassist/greenhouse-menus.txt` so the unlock
  reading can be matched to Hypixel's real wording.

## New in 8.3.2
- /shards reads the real Attribute Menu format: "Source: Mist Shard (C2)" links the attribute to its shard,
  "Attribute Level: 8", and Hypixel's own "Syphon 8 shards to level up!" / "Syphon 32 shards to max!" numbers
  (progress already included) are used directly; unused Hunting Box shards are subtracted.

## New in 8.3.1
- /shards: attribute items are also matched to their shard when the description names it; opening the Attribute Menu /
  Hunting Box says in chat how many were recognised and saves what was seen to `config/skyassist/shard-menus.txt`.
- **Edit** button per shard (or `/shards set <shard> <level> <have>`) to fix numbers by hand; hand-set values (✎) win
  over menu reading until `/shards reset`.

## New in 8.3
- **/shards** subtracts what you already have: shards syphoned into the current level (read from the Attribute Menu,
  as "12/24" progress or a "Syphoned: N" total) and unused shards in your Hunting Box. The list shows **To buy**.
- Clicking **Bazaar** opens that shard's Bazaar page and **copies the amount to buy** to your clipboard (paste it into the
  amount sign). The order itself stays your click (automatic Bazaar orders break Hypixel's rules).

## New in 8.2.1
- Menu colors (rarity, search) are drawn **after the menu background and before the items**, so items sit on the color
  and **tooltips stay on top** (before, redrawn items poked through tooltips).
- Rarity is found anywhere in the item's description (shop menus put prices after the rarity line).
- Search: matches get a green background + frame, everything else a dark background.

## New in 8.2
- **Root cause found for menus**: overlays (rarity colors, search highlights, item labels, terminal solvers) and the
  search box's key handling were registered through a Fabric-internal class that Java refuses to call; registration
  failed silently every time. They now register through Fabric's public Event type.
- **Pests**: only the visible head is highlighted (outline + a tight particle box around it).
- **Ultimate enchants** are recognised by name too, so they always keep Hypixel's bold pink.
- **Lockpick**: a bigger neon-green square with black/white edges and crosshair arms.
- **/shards**: finds attribute shards by "SHARD" in the ID + name ending in "Shard", with the Bazaar list as backup;
  `/profit debug` shows how many it found.
- **/hotm and /hotf** open a **graphic tree**: perks by tier as boxes colored green (core) / yellow (later) / gray
  (skip) with step numbers and target levels, next to the step-by-step plan (e.g. Mining Speed → 10, Mining Fortune → 10,
  Great Explorer → max, Efficient Miner → 30 ...). "Text guide" opens the detailed text version.

## New in 8.1.1 — full check
- Rarity colors can never hide an item: if this Minecraft version can't redraw the item on top, a light see-through
  tint is used instead (`/profit debug` shows which).
- `/profit menu` added to the Commands list; every command, menu and setting checked to be reachable.

## New in 8.1 — fixes
- **Rarity colors, search highlights, item labels, terminal solvers and tooltip scrolling now actually work in menus.**
  They were attached to each menu before Fabric set the menu up, and Fabric throws those away during setup; they're
  now attached right after.
- **Pests are found reliably**: a pest is a Bat (Fly, Mosquito, Moth) or a Silverfish (all others) in the Garden
  (wiki). Their name tag is only used for the name. Green glow outline + green box + HUD arrows follow from that;
  `/profit debug` shows how many pests are seen.
- **Lockpick**: a plain green square drawn exactly over the spot to aim at (no particles).
- "Best now" on the Mining HUD and the purse coins line are **off by default** (switches in Mining / HUD settings).
- **/hotm and /hotf**: every strategy now has a tree overview (what's on each tier) and a numbered upgrade order
  with target levels, from the 2026 wiki pages (incl. the Anomalous Desire → Tunnel Vision rename).

## New in 8.0
1. **Performance**: `/profit perf` shows how much time each feature uses; **Performance mode** (General) makes scans
   and particle markers run half as often; outlines don't scan at all where nothing can glow.
2. **Shaders**: with an Iris shader pack on, highlights switch to particle boxes automatically (Highlight style setting).
3. **Clash-free start**: with SkyHanni / Skyblocker installed, SkyAssist's HUD starts on the right under the scoreboard.
4. **What do you play?** in the setup screen (`/profit setup`): one click switches an activity's HUD + helpers.
5. **Macro keys**: 6 keys that run commands (Controls → SkyAssist; commands in Settings → Chat & controls).
6. **Inquisitor sharing**: [Share with party] (or auto-share) when you dig one up; party coordinates from anyone
   (SkyAssist, SkyHanni or typed) become a light beam + HUD arrow. `/waypoints` to share / remove.
7. **Mineshaft alert**: ding + big HUD line + [Share with party] when you find a Glacite Mineshaft.
8. **Crystal Hollows waypoints**: Jungle Temple, Goblin Queen's Den, Mines of Divan, Precursor City... saved per lobby.
9. **Boss health**: your slayer boss's health and timer on the Combat HUD.
10. **Jacob's contest standing**: collected amount and medal bracket on the Farming HUD during a contest.
11. **Storage overview**: `/itemsearch` lists everything in your Ender Chest pages and backpacks with where it is.
12. **Extras** tab: disable swimming pose, slow swing (with swing length), rainbow maxed enchants (the tab was split up in 8.14.0).

## New in 7.0
- **Glowing outlines** (through walls), each with its own switch: starred dungeon mobs (gold), Wither / Blood key,
  secret bats (green), your slayer boss (red), Zealots (purple) / Special Zealots (pink), Ghosts (white),
  commission mobs (yellow), rare sea creatures (cyan), Minos Inquisitor (gold), pests (green).
- **Diana burrow finder**: burrows marked from their particles (green start / red mob / gold treasure) with a HUD list,
  and the Ancestral Spade's particle trail turned into a cyan direction beam.
- **Fishing bite alert**: ding + "REEL IN!" when !!! appears over your bobber.
- **Chat & sounds** settings tab (now *Chat & controls*): hide sack messages, ability cooldown, "blocks in the way", Garden visitor chatter,
  Watchdog announcements, anything containing your own words; mute explosions or any server sound by name
  ("Show sound names" helps you find them).
- **Labels on items**: pet level, minion tier, enchanted book level.
- **/hotm** and **/hotf**: tree guides per goal (powder grinding, gemstones, mithril, glacite, starting out;
  Fig / Forest Whispers, Helix / Desert Whispers), from the 2026 wiki guides and recent forum threads.
- **Farming HUD crop fortune**: now found even when the main crop isn't decided yet or the name differs
  (Cocoa Bean, Melon Slice...), falls back to the crop of your held tool, and shows the last value seen.

## New in 6.8 — green pest outline
Pests now get Minecraft's **glowing outline in bright green** (visible through walls), made to work on Hypixel's pests
with two small hooks, plus a dense bright-green particle box. The fire markers and trail are gone (trail can be turned
back on). Color, outline and box can each be changed in Farming settings.

## New in 6.7 — lockpick helper
Opening a treasure chest: the spot to aim at gets a **pink dot**, and the Mining HUD says which way to move your aim
(↑ ↓ ← →) or **✔ ON TARGET**. It follows the chest's lockpick particles live (the mod reads them with a small, safe
hook into Minecraft's particle handling). Display only: you still aim yourself. Settings → Mining: on/off and the aim
offset (the wiki's tip: aim 1-2 pixels above the particles; default 1).

## New in 6.6 — Crystal Hollows treasure chests
Like SkyHanni's Powder Chest Timer / Powder Grinding Tracker and Skyblocker's Treasure Chest Highlighter:
- Every chest you uncover while mining gets a bright box, colored by time left before it despawns
  (green > 30 s, yellow > 10 s, red), and the Mining HUD lists them with an arrow, distance and countdown
  (the one about to disappear first on top). Opened / despawned chests drop off.
- Chests opened and chests per minute, a "2x Powder active" line during Double Powder, and the chest loot
  (Goblin Eggs, essence, gemstones, powder...) on the HUD and in /miningprofit. Powder itself is counted by the
  powder tracker as before. Settings → Mining: on/off and chest lifetime.

## New in 6.5
- Menus are drawn in layers in 26.x; the mod now draws on a **new layer** on top of each menu. Result: rarity colors in
  **every** menu with the **item drawn over the color**, and the inventory search highlights are actually visible.
- Inventory search box: while you type, keys like E / Q / 1-9 don't act on the inventory; **Escape or clicking elsewhere**
  leaves the box and everything works normally again.
- Menu footers wrap instead of being cut off.
- Pests: a dense box of colored dust particles (color in Farming settings).
- /shards: the list now comes from Hypixel's own item list, so every attribute shard shows up (with its rarity).
- Greenhouse: better unlock detection (sack "Stored", sack messages, Unlocked/Analyzed lore) with a chat line telling you
  what was read, a "Reset unlocks" button, "on <block>" next to every crop and mutation, bigger grid squares with the
  name and the block, and in-world colored squares matching the HUD colors.

## New in 6.4.1 (fixes & polish)
- Menus no longer recalculate everything on every mouse-wheel notch or search letter (talismans and craft flips were slow to scroll/search).
- After resizing the window, tooltip scrolling and backspace in the inventory search no longer act several times at once.
- Rarity colors are drawn **behind** the items in menus (Fabric's background event); the hotbar tint is lighter since it sits on top.
- Items picked up while chat is open are counted again.
- The sign calculator no longer inspects every open menu each tick; HUD text refreshes 10x a second while editing instead of every frame.

## New in 6.4
- **Sack items were counted about twice** (Hypixel repeats the same list on several parts of the "[Sacks]" message);
  each list is now read once, so the HUD matches your sacks.
- **Rarity colors**: every item's slot is tinted in its rarity color in menus and on the hotbar
  (Settings → Items: on/off, hotbar on/off, strength).
- **"Best now" is off on the Farming HUD** (Farming → Best crop on Farming HUD to bring it back; `/profit suggest` still has it).

## New in 6.3
- **Pests**: a bright particle box around each pest plus Minecraft's glowing outline (visible through walls).
- **Greenhouse**: the Mutations Sack no longer marks everything as unlocked (only mutations with Stored 1+ or in your
  inventory count; the wrong marks from 6.2 are cleared once). The planter is now its **own HUD panel** with a real grid:
  a colored square per block with the crop's name on it, turned so the top is the way you face, and a legend.
- **Menus**: long text wraps onto more lines instead of being cut off, lists scroll with the mouse wheel (▲ ▼ too),
  and every big menu has a **search box**.
- **Talismans menu**: search by name or rarity, a rarity filter, and "Other ways" groups upgrade chains together
  (owned tiers struck through, how to get each one on hover).
- **Craft flips** now use all flip settings: budget, max item price, min margin, min profit, tax, min volume and share
  (the last two only for Bazaar, the AH has no volume data).
- **Inventory search box** is placed next to the menu (below, above or beside it) so item slots don't cover it.
- **/shards** only lists attribute shards (no Prismarine Shard etc.), with the attribute, its effect and the Hunting
  level needed; search by effect (e.g. "Farming Fortune").
- **Purse coins** count as profit: Bountiful, mob coins, Midas, coin catches... (read from the sidebar while you're
  active; menus are ignored so selling / bank don't count).

## New in 6.2
- **Profit fix**: taking items out of your sacks (e.g. crafting Enchanted Pumpkins from sacks) no longer counts as a loss,
  and crops / ores leaving your inventory (compacting, moving to sacks) aren't listed as "Spent". Only Garden visitor
  requests count as spent from sacks.
- **Rare drops are shown once** (under Rare drops), not again in the item list.
- **Item names** in the flipper fixed: "Dragon Essence", "Sharpness VII", "Kada Knight Shard", "Jar of Sand"...
- **Pests**: fire particles on every pest you can see, a short trail toward them, and a list with arrows, distance and
  the plots with pests (Farming settings).
- **/greenhouse**: pick a mutation (all of them, from the wiki's table), see what you need to unlock first and in what
  order, then plant it: look at the empty plot in your greenhouse and markers show what goes where, plus a grid on the HUD
  that turns with you. Mutations you hold or see in a menu are marked as unlocked automatically (or press "I have it").
- **/flips**: new tabs **Craft → Bazaar** (e.g. Enchanted Diamonds → Enchanted Diamond Block, with volume and profit/h)
  and **Craft → AH** (armor, weapons... sold at lowest BIN). Shortcuts: `/flips craft`, `/flips craftah`.
- **/shards**: cheapest attribute levels to buy next (or to max), using the wiki's shards-per-level table and your levels
  from the Attribute Menu.
- **/talismans**: NPC-shop accessories now have a price; **Other ways** lists the ones you can't buy (quests, drops,
  events) with how to get them and a wiki link.
- **Inventory search**: a box under every inventory / chest / menu. Type, and matching items (name or description) are
  highlighted, everything else is dimmed.
- **/profit history**: new first tab with profit **today, yesterday, this week, last 7 days, this month, last 30 days and
  all time** (Bazaar flips included; hover a row for the split by activity).
- **/dungeon**: this run's secrets (yours and the team's), crypts, deaths, rooms, time, milestone, puzzles and team, plus
  past runs with averages. `/dungeon secrets` for a quick line in chat.

## New in 6.0 — SkyAssist
The mod is now called **SkyAssist** (it does a lot more than profit now). The jar is `skyassist-…jar`, settings live in
`config/skyassist` (your old `config/farmprofit` folder is moved over automatically the first time, so nothing is lost),
and the chat prefix is `[SkyAssist]`. `/skyassist` opens the main menu; all the `/profit…` and `/flips` commands still work.

## New in 5.6
- **Commands list**: `/profit help` (or the Commands button in Settings, or the main menu) shows every command with a
  short explanation, grouped into tabs. **Run** runs it; **Type** opens chat with the command filled in so you can add the rest.

## New in 5.5 — menus
Press **P** (or `/profit menu`) for the main menu, which opens everything below. The commands open the same menus;
add `chat` to get the old chat output (e.g. `/profit history chat`, `/flips chat`).
- **Current session** (`/profit`, `/miningprofit`...): one tab per running activity, every item / shard / rare drop /
  spent item with its value and a **Hide** button, plus End & save, Copy summary.
- **History** (`/profit history`): tabs All + each activity, hover a session for fortune, notes, shards and rare drops,
  profit/h trend, Export CSV.
- **Lifetime totals** (`/profit total`), **Best now** (`/profit suggest`, Farming and Mining tabs).
- **Bazaar flips** (`/flips`): Best flips, Plan, My orders (with Remove), Profit log; every flip / order has a
  **Bazaar** button that opens the item; Refresh prices and Flip settings at the top.
- **Next talismans** (`/talismans`) and **Settings** (O) as before.

## New in 5.4
- `/talismans` opens a **menu**: rank, name in rarity color, +MP, price, coins per MP, and an **AH** or **Recipe** button
  per accessory (closes the menu and runs the search / recipe command). Top 10 / 20 / 50, crafting on/off and a max price
  at the top, total cost at the bottom. `/talismans chat` still prints the list in chat.

## New in 5.3
- **Torrhus Canyon** and **Moonglade Marsh** are recognised as foraging islands (built in, so they work even with an
  older config). **Desert Whispers** are tracked next to Forest Whispers, and Helix Fortune shows in the stats.
- Where HUDs appear was checked for every activity: the **Dungeon Hub no longer counts as a dungeon**, and newer
  Hypixel Mod API names for mining and foraging islands are recognised.
- **Moving HUDs with chat open (T)** now reads the mouse directly, so it works on every version; every visible panel gets
  an outline while chat is open (orange = under your mouse). Drag to move, middle-click to resize, right-click a title to hide.

## New in 5.2
- **`/talismans [count]`**: the next accessories to get, ranked by **coins per Magical Power**, using the cheaper of the
  Auction House (lowest BIN) and crafting. Upgrade chains are understood (Talisman → Ring → Artifact): if you own a lower
  tier, only the extra MP counts and the part you own is free in the craft cost. One entry per chain (the best value tier).
  Click an entry to search the AH or open its recipe. What you own is read when you open your **Accessory Bag** (open
  every page once, and again after buying). Settings → Items: how many to list, include crafting, max price.
  Accessories only sold by NPCs or not tradeable have no price here and are skipped.

## New in 5.1
More dungeon puzzle solvers. Answers appear as **particles in the world** that only you see, plus a line on the HUD:
- **Ice Fill**: a green path over every ice tile from where you stand (recalculated as you walk).
- **Creeper Beams**: each lantern pair to connect gets its own particle colour and a line through the creeper.
- **Teleport Maze**: after each teleport, the pad most in the direction you face (toward the exit) is marked; used pads are skipped.
- **Tic Tac Toe**: reads the X / O maps on the wall and marks the best button (unbeatable play).
- **Quiz (Ouro)**: tells you the right answer letter, using the maintained Skytils answer list (and works out "What SkyBlock year is it?" itself).
- **Melody terminal** (F7): the button turns green when it's time to click.
Each one can be turned off in Settings → Dungeons. Still not included: Boulder and Water Board (see below).

## New in 5.0
- **Two Minecraft versions**: every build now makes a jar for **26.1.x** and one for **26.2** (Actions → Artifacts:
  `skyassist-mc26.1.2` and `skyassist-mc26.2`). If one version fails to build, the other still does.
- **Craft cost** in tooltips: what an item costs to craft from bought ingredients (each ingredient bought or crafted,
  whichever is cheaper), and whether crafting or buying is cheaper. Recipes: NEU item repository, downloaded weekly.
- **Calculator**: in Bazaar / Auction amount signs type a sum ending in `=` (`64x8=`, `10m/3=`, `(2.5k+500)*4=`) and it
  becomes the number; while typing, the result is previewed above your hotbar. Also `/calc <sum>` (copies the result).
- **HUD layout presets**: `/profit gui preset left | right | split | compact`.
- **Puzzle solvers** (Dungeons settings): Three Weirdos (tells you which chest), Blaze puzzle (lowest / highest blaze with
  arrows), and Floor 7 / Master 7 terminals (Correct all the panes, Click in order, What starts with, Select all the
  [color] items, Change all to same color — highlights what to click; you still click).
- `MODRINTH.md`: ready-made project description if you publish the mod.

## New in 4.3
- Scrollable tooltips: item descriptions taller than the screen can be scrolled with the mouse wheel while you hover
  the item in a menu. The name stays at the top and "▲ / ▼ N more" shows what's hidden. Settings → Items.

## New in 4.2
- Enchantment colors in tooltips: **perfect** (absolute max, gold + ✦), **great** (above the enchanting-table max, purple),
  **good** (table max, blue), **low** (gray). Ultimate enchants stay bold. All colors (including rainbow) are in
  Settings → Items. Max levels come from the public SkyKings Bot-Data file, so new enchants work automatically.
  If you also run SkyHanni, the duplicate check offers to turn one of the two off.

## New in 4.1
- Foraging starts reliably: instant log breaks (Sweep) are counted, breaks are detected from the first hit, and chopping
  5 logs anywhere starts a Foraging session even if the island isn't recognised.
- Combat HUD: kills, kills/h, most-killed mobs, profit per kill (mob names read from Hypixel's nametags).
- Grind HUDs: when most kills are one farmable mob (Zealot, Enderman/Voidling, Ghost, Blaze, Wither Skeleton,
  Magma Cube, Ice/Glacite Walker, Arachne's spiders) the HUD becomes e.g. "✦ Zealot grind" with each key drop's
  count, odds (1 per X kills) and kills since the last one, plus Special Zealots.
- The "cheap items" option is gone: items are listed one by one, and only when there are more than "Items shown"
  are the cheapest added up into one line.

## New in 4.0
- **Setup check** the first time you join SkyBlock (reopen with `/profit setup`): ✔/✖ for location, Stats and Powders
  widgets, sidebar, prices and the Hypixel Mod API, with a button that opens Hypixel's `/widget` menu.
- **Settings menu**: search box, ↺ reset next to every setting, "Reset tab", a **Hidden items** tab to count items again,
  and a Configure button in **Mod Menu** if you have it.
- **HUD editor** (`/profit gui`, or the button in Settings → HUD): every panel gets an outline; drag to move,
  middle-click to change size, right-click the title to hide or show it. `/profit gui reset` resets the layout.
  HUD settings → *Separate small panels* splits off the secret finder, Jacob's contest and "Best now";
  *Position per activity* gives the main HUD its own spot per activity. Clicking the main title toggles the all-time line.
- **Duplicates**: if SkyHanni, Skyblocker, Odin or Secret Routes already do something (price tooltips, chest profit,
  secret finder, contest reminder), you get a one-click `[Turn these off here]`.
- `/profit report` copies a diagnostics report to paste to Claude; `/profit note <text>` adds a note to the current session
  (shown in history and the CSV).
- **Profiles**: `/profit profile save <name>`, `load <name>`, `list`, `export` (copies a code), `import <code>`.
- **New-build check**: tells you when your GitHub repo has a newer successful build (Settings → Advanced → Data sources → GitHub repo).

## New in 3.1
- Combat sessions only start after real fighting: 5 mob hits within 30 s (players, NPCs and armor stands never count),
  or a slayer quest. Both are adjustable in Settings → Combat.
- The HUD follows what you do: it switches as soon as you do something else (farm, chop, fish...) and when you arrive
  on a new island. Unknown log blocks on foraging islands are counted too, so Galatea woods always start a session.
- About 50 new settings: HUD look (opacity, line height, shadow, which lines show, number format),
  and per-activity options in every tab (Farming, Mining, Foraging, Fishing, Combat, Dungeons, Kuudra, Diana, Bazaar).

## New in 3.0
**Accuracy**
- Bazaar sale values now include Bazaar tax (the `Bazaar tax %` setting), so profit isn't overstated.
- Items are identified by their real SkyBlock ID once you've held them (pets, books and reforged items price correctly).
- Items you use up (potions, arrows, visitor requests) go into a separate **Spent** list instead of quietly lowering totals.
- Items from dungeon reward chests and Garden visitor menus are now counted (other menus are still ignored, so buying isn't "profit").
- **`/profit debug`** shows what's working (tab list, location, sidebar, prices, icons, scale, mouse, recognised messages).
  Hypixel messages the mod doesn't understand yet are saved to `config/skyassist/unrecognised-messages.txt`.

**Location: install the Hypixel Mod API (recommended)**
Download *Hypixel Mod API* for Fabric 26.1 from Modrinth and put it in `mods`. The mod then gets your exact location from
Hypixel and no longer depends on the tab list's Area line. Without it, the tab list is used like before.

**New features**
- Price tooltips: hover any item for Bazaar sell/buy, lowest BIN and NPC price (toggle in HUD settings).
- Dungeon chest profit: opening a reward chest or the Croesus run view prints each chest's value, cost and profit, plus the best chest.
- Garden visitors: visitors accepted, copper (valued with `Copper value`), requested items in Spent, rewards in profit.
- Jacob's contests on the Farming HUD (next crops + countdown; "Best now" marks a crop that's in a running contest).
  Contest data is community data from the Elite Farmers API (elitebot.dev), collected from SkyHanni users.
- Mayor on the HUD of the activity they boost (from Hypixel's election data), and in `/profit suggest`.
- Bazaar: warning when an order you place is far from the market (possible typo), and orders placed today in `/flips log`.
- New activities: **Kuudra** (`/kuudraprofit`, runs per hour) and **Diana** (`/dianaprofit`, burrows per hour, coins dug).
- Trophy fish broken down by type on the Fishing HUD.
- Optional all-time line on every HUD (HUD settings → Show all-time line).
- The Bazaar orders panel is its own box: drag it separately with chat open.
- `/profit export` writes `history.csv` (open in Excel/Sheets for graphs); `/profit history` shows a profit/h trend bar.

## Settings menu
Press **O** (rebind it in Options → Controls → Key Binds → "SkyAssist"), or use `/profit settings`,
to open a menu with **every** setting, grouped into tabs:
General, HUD, **Alerts** (every alert in one place, with a Test alert button), Farming, Mining (incl. the Crystal Hollows map), Foraging, Fishing, Combat (incl. Kuudra and Diana), Dungeons, Market (flipping, orders and /market), Items, Timers, Chat & controls (chat filter, sounds, macro keys) and Advanced (data URLs, counting rules, extra areas/blocks). Each tab is split into headed sections; a sub-option is indented under its switch and greyed while that switch is off.
Each activity tab also has a switch to hide that activity's HUD (tracking keeps running).
Hover a setting for an explanation. On/off settings are buttons, choices cycle when clicked, numbers accept
`10m` / `500k`, lists are comma separated and item/block maps use `name=ID, name2=ID2`.
Changes save immediately (text boxes save when you switch tab or press Done). `/flips settings` opens it too.

**For future additions:** the menu is built automatically from `Config.java`. Every new option is added there with
`@Setting(category = ..., label = ..., desc = ...)` and appears in the menu on its own; an option without the
annotation still shows up under an "Other" tab.

## Dungeons (☠ Catacombs HUD)
- Runs, average run time, runs/hour, last score, profit, profit/h and profit per run (drops, rare drops, shards).
  `/dungeonprofit` for the profit-only view, `/dungeonprofit history` for past sessions.
- **Secret finder** (no route database, so it works in every room):
  - Hypixel's own room counter from the action bar: `room 3/7 (4 left)` or `✔ room done`.
  - The 5 nearest likely secrets with an arrow (relative to where you look), distance and ▲/▼ for above/below:
    chests, levers, Wither Essence skulls, secret items lying on the floor, and bats.
  - Chests/levers/skulls you right-click are crossed off.
  - It shows *candidates* (decorative skulls and some levers aren't secrets). For full routes use Skyblocker / Secret Routes alongside this mod.
  - `/profit secrets` turns it on/off.

## "Best now" suggestions
The farming and mining HUDs show which crop / ore makes the most coins per hour right now, e.g.
`Best now: Nether Wart ~8.1M/h (you: 6.2M/h)` (a green ✔ means you're already on it).
`/farmprofit suggest` and `/miningprofit suggest` show the full ranking.
- Farming: your blocks/s (or `defaultBps` until you've farmed a minute) × base drops × (Farming Fortune + crop fortune) × best price (raw, enchanted or NPC).
- Mining: your Mining Speed vs. each block's strength (`miningEfficiency` = 0.6 for walking/aiming) × fortune × best price, only for blocks on the island you're on.
These are estimates from Bazaar trends; pests, rare drops and powder aren't included. Turn off with `showSuggestion: false`.

## Bazaar flipping (`/flips`)
Built in from the Bazaar Flip Assistant. It only reads public bazaar data and your own chat; you do all the clicking.
- `/flips [count]` – best flips right now (buy order → sell offer, margin after tax, qty, coins/h). Hover for details, **click to open the item in the Bazaar**.
- `/flips plan [items]` – splits your budget across the best few safe flips.
- **Order tracking is automatic**: place, flip, claim or cancel orders in-game and the mod reads the `[Bazaar]` chat messages.
  Open orders appear under the HUD with ✔ (best price), ✖ outbid/undercut + the price to re-list at, or "filled, claim it".
  You get a ding + chat alert the moment you're outbid or undercut (prices are checked every 20 s while you have orders).
- Profit is logged when you claim the coins from a sell offer (cost = what you paid, oldest first). `/flips log` shows today / all-time.
- `/flips orders`, `/flips remove <n>`, `/flips clear`, `/flips hud`
- `/flips settings` and `/flips set <budget|minvolume|minmargin|maxprice|tax|share|alert|top|sound> <value>`
  e.g. `/flips set budget 25m`, `/flips set tax 1`, `/flips set alert 500k` (ping for hot flips).

## Tab list widgets
The mod reads Hypixel's tab list: the **Area** line (to know where you are), **Stats** (fortune, speed,
Sweep, Magic Find...), **Powders** and **Commissions** (mining), and Forest Whispers (foraging).

## Setup (no programming tools needed)

### 1. Get the mod file built by GitHub (free, ~5 minutes)
1. Make a free account at github.com.
2. Click **+** (top right) → **New repository**, name it `skyassist`, click **Create repository**.
3. On the next page click **uploading an existing file**. Unzip this project and drag
   **everything inside the `skyassist` folder** into the browser. Click **Commit changes**.
   - On Mac, the `.github` folder is hidden. If it didn't upload: in your repo click
     **Add file → Create new file**, type `.github/workflows/build.yml` as the name,
     paste in the contents of that file, and commit.
4. Open the **Actions** tab. A "Build mod" run starts by itself (takes ~3-5 min).
   If nothing is running, click **Build mod → Run workflow**.
5. When it shows a green check, click the run, scroll to **Artifacts**, download
   `skyassist-mc26.1.2` (or `skyassist-mc26.2` for Minecraft 26.2), and unzip it. Inside is the `skyassist-….jar` file.
   - In the mod's settings, *GitHub repo* must be your `owner/skyassist` so the update check looks at your builds.

### 2. Install
1. Install Fabric Loader for Minecraft **26.1.2** or **26.2** (fabricmc.net/use/installer) — or use
   Prism Launcher / Modrinth App and create a Fabric instance for that version.
2. Put **Fabric API** (for your Minecraft version, from Modrinth) and the `skyassist-….jar` in your `mods` folder.
3. Launch and join Hypixel.

### 3. In game
- For the fortune line, enable the **Stats** widget in Hypixel's tab list settings
  and make sure Farming Fortune is one of the shown stats.
- Start farming. The HUD appears as soon as you break a crop.

## Settings
`.minecraft/config/skyassist/config.json`
- `resetMinutes` – idle time before a session ends (default 15)
- `priceMode` – `"best"` (default: Bazaar instasell or NPC, whichever pays more), `"instasell"`, `"sellorder"`, `"npc"`
- `pauseSeconds` – the timer pauses after this long without activity (default 30), so AFK time doesn't lower profit/h
- `minItemValue` – items worth less than this are grouped into one "cheap items" HUD line (default 1000)
- `ignoredItems` – items that never count
- `hudX`, `hudY`, `hudMaxItems`, `hudEnabled`, `hudDetails`
- `slayerQuestCost` – coins subtracted from profit per slayer quest started (default 0)
- `miningAreas`, `foragingAreas` – tab-list Area names for those HUDs
- `extraItems`, `extraCropBlocks`, `extraOreBlocks`, `extraLogBlocks` – add things the mod doesn't know
- `showShards`, `showRareDrops`, `showCommissions`, `lowestBinUrl`

History is saved to `.minecraft/config/skyassist/history.json` (last 200 sessions).
