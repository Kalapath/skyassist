package dev.farmprofit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Settings saved in .minecraft/config/skyassist/config.json */
public final class Config {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final Path DIR = FabricLoader.getInstance().getConfigDir().resolve("skyassist");

    static {
        // the mod used to be called "farmprofit": move its folder (settings, history, flips...) over once
        Path old = FabricLoader.getInstance().getConfigDir().resolve("farmprofit");
        try {
            if (java.nio.file.Files.isDirectory(old) && !java.nio.file.Files.exists(DIR)) java.nio.file.Files.move(old, DIR);
        } catch (Exception ignored) {}
    }
    private static final Path FILE = DIR.resolve("config.json");
    private static Config instance;

    /** Minutes without activity before a session resets. */
    @Setting(category = "General", section = "Sessions", order = 1, label = "Switch HUD on arrival", desc = "Arriving on an island (Garden, Galatea, Mines...) switches the HUD to it right away.")
    public boolean switchHudOnArrival = true;
    @Setting(category = "General", section = "Sessions", order = 2, label = "Session end message", desc = "Chat summary when a session ends and is saved.")
    public boolean announceSessionEnd = true;
    @Setting(category = "General", section = "Performance & look", order = 10, label = "Performance mode", desc = "Scans and particle markers run half as often and less dense. Use it if SkyAssist costs you FPS (check with /profit perf).")
    public boolean performanceMode = false;
    @Setting(category = "General", section = "Performance & look", order = 11, label = "Highlight style", desc = "auto = glowing outline, but particle boxes when a shader pack (Iris) is on. outline / particles = always that.", options = {"auto", "outline", "particles"})
    public String glowStyle = "auto";
    @Setting(category = "General", section = "Sessions", order = 5, label = "Count purse coins", desc = "Coins that go straight into your purse while you're active (Bountiful reforge, mob coins, Midas, coin catches...) count as profit. Read from the sidebar; menus are ignored so selling / bank don't count.")
    public boolean trackPurse = true;
    @Setting(category = "Advanced", section = "What counts as profit", order = 217, label = "Only count what the activity gave", desc = "Items count for a profit HUD only if they're that activity's own items or arrived right after you did it (not trades, other people's drops, etc.).")
    public boolean strictAttribution = true;
    @Setting(category = "Advanced", section = "What counts as profit", order = 218, label = "Activity window (s)", desc = "How long after your last action (break, catch, hit...) a new item still counts.", min = 1, max = 30)
    public int attributionSeconds = 4;
    @Setting(category = "HUD", section = "Main HUD", order = 21, label = "Show purse coins line", desc = "The 'Purse coins: +X (Bountiful, mob coins...)' line. Off = still counted in profit, just not listed.")
    public boolean hudShowPurse = false;
    @Setting(category = "HUD", section = "Main HUD", order = 22, label = "Hide used-up items", desc = "Don't list items with a negative amount (raw items a compactor turned into enchanted ones). They still count in profit.")
    public boolean hideNegativeItems = true;
    @Setting(category = "General", section = "Prices & numbers", order = 7, label = "Number format", desc = "compact = 1.2M, full = 1,234,567.", options = {"compact", "full"})
    public String numberFormat = "compact";
    @Setting(category = "HUD", section = "Look & layout", order = 32, label = "Background opacity", desc = "0 = invisible, 255 = solid black.", min = 0, max = 255)
    public int hudOpacity = 144;
    @Setting(category = "HUD", section = "Look & layout", order = 33, label = "Line height", desc = "Pixels per line (10 = default).", min = 8, max = 20)
    public int hudLineHeight = 10;
    @Setting(category = "HUD", section = "Look & layout", order = 34, label = "Text shadow", desc = "Drop shadow behind HUD text.")
    public boolean hudShadow = true;
    @Setting(category = "HUD", section = "Main HUD", order = 14, label = "Show title", desc = "The activity name at the top of the HUD.")
    public boolean hudShowTitle = true;
    @Setting(category = "HUD", section = "Main HUD", order = 15, label = "Show session time", desc = "The Time line.")
    public boolean hudShowTime = true;
    @Setting(category = "HUD", section = "Main HUD", order = 16, label = "Show profit/h", desc = "The Profit/h line.")
    public boolean hudShowRate = true;
    @Setting(category = "HUD", section = "Main HUD", order = 17, label = "Show item list", desc = "The Items section.")
    public boolean hudShowItems = true;
    @Setting(category = "HUD", section = "Main HUD", order = 20, label = "Show spent / costs", desc = "Spent, costs and copper lines.")
    public boolean hudShowCosts = true;
    @Setting(category = "HUD", section = "Main HUD", order = 23, label = "Show reset countdown", desc = "The 'Paused - resets in' line when you're idle.")
    public boolean hudShowCountdown = true;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 29, label = "Rare drops shown", desc = "How many rare drops the HUD lists.", min = 0, max = 20)
    public int hudMaxRare = 4;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 31, label = "Shards shown", desc = "How many shards the HUD lists.", min = 0, max = 20)
    public int hudMaxShards = 4;
    @Setting(category = "Farming", section = "Farming HUD", order = 38, label = "Show fortune", desc = "Farming Fortune line (needs the Stats tab widget).")
    public boolean farmShowFortune = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 39, label = "Show crop and blocks/s", desc = "The 'Farming: Wheat (1,234 broken, 19.5 BPS)' line.")
    public boolean farmShowBps = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 40, label = "Show pests", desc = "Pests killed by type.")
    public boolean farmShowPests = true;
    @Setting(category = "Farming", section = "Pests", order = 44, label = "Highlight pests", desc = "Fire particles on every pest you can see, plus a list with arrows, distances and pest plots.")
    public boolean pestHighlight = true;
    @Setting(category = "Farming", section = "Pests", order = 48, label = "Trail to pests", desc = "A short particle trail from you toward each pest.")
    public boolean pestTrail = false;
    @Setting(category = "Farming", section = "Pests", order = 46, label = "Pest particle box", desc = "A box of colored particles around each pest (on top of the glowing outline).")
    public boolean pestBox = true;
    @Setting(category = "Farming", section = "Pests", order = 45, label = "Pest glow outline", desc = "Bright outline around pests in the box color, visible through walls (Minecraft's glowing effect).")
    public boolean pestGlow = true;
    @Setting(category = "Farming", section = "Pests", order = 47, label = "Pest box color", desc = "Color of the box around pests, as a hex number, e.g. FF3030 (red), FFFF00 (yellow), 00FFFF (cyan).", options = {"00FF00", "FFFF00", "00FFFF", "FF00FF", "FF3030", "FFFFFF"})
    public String pestBoxColorHex = "00FF00";
    @Setting(category = "Other", label = "pest color migrated", desc = "internal", hidden = true)
    public boolean pestColorMigrated = false;
    @Setting(category = "Farming", section = "Garden helpers", order = 49, label = "Greenhouse guide", desc = "When a mutation is planned (/greenhouse): markers on the plot you look at and a layout grid on the HUD.")
    public boolean greenhouseGuide = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 41, label = "Show visitors", desc = "Garden visitors accepted.")
    public boolean farmShowVisitors = true;
    @Setting(category = "Farming", section = "Garden helpers", order = 51, label = "Contest reminder", desc = "Chat ping 1 minute before a Jacob's contest starts.")
    public boolean contestAlert = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 42, label = "Contest standing on HUD", desc = "During a Jacob's contest: your collected amount and medal bracket (from the sidebar) on the Farming HUD.")
    public boolean showContestStanding = true;
    @Setting(category = "Farming", section = "Garden helpers", order = 50, label = "Visitor shopping list", desc = "Opening a visitor lists what they want with a [Bazaar] link per item (opens it and copies the amount you still need).")
    public boolean visitorBazaar = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 56, label = "Show mining stats", desc = "Mining Speed / Fortune lines (needs the Stats tab widget).")
    public boolean mineShowStats = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 57, label = "Show powder", desc = "Powder gained and per hour.")
    public boolean mineShowPowder = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 58, label = "Show pristine", desc = "Pristine procs (Crystal Hollows, Mineshafts).")
    public boolean mineShowPristine = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 59, label = "Show block breakdown", desc = "Which blocks you've mined, by type.")
    public boolean mineShowBlocks = true;
    @Setting(category = "Mining", section = "Glow", order = 69, label = "Glow: Ghosts", desc = "Ghosts in the Mist get a white outline (they're almost invisible).")
    public boolean glowGhosts = true;
    @Setting(category = "Mining", section = "Glow", order = 70, label = "Glow: commission mobs", desc = "Goblins, Glacite / Ice Walkers, Treasure Hoarders, Star Sentries get a yellow outline.")
    public boolean glowCommission = true;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 63, label = "Treasure chest helper", desc = "Crystal Hollows: box + despawn countdown on chests you uncover, chests opened per minute, Double Powder, and chest loot.")
    public boolean powderChests = true;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 68, label = "Mineshaft alert", desc = "Ding + big HUD line when you find a Glacite Mineshaft, with a [Share with party] button.")
    public boolean mineshaftAlert = true;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 67, label = "Crystal Hollows waypoints", desc = "Saves a waypoint when you reach Jungle Temple, Goblin Queen's Den, Mines of Divan, Precursor City... (per lobby), with a share button.")
    public boolean chWaypoints = true;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 64, label = "Treasure chest lifetime (s)", desc = "How long an uncovered chest stays before it disappears (about 60 s).", min = 10, max = 300)
    public int chestLifetimeSeconds = 60;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 65, label = "Lockpick helper", desc = "Marks the lockpick spot on treasure chests with a green square on your screen and shows which way to move your aim. Display only: you aim yourself.")
    public boolean lockpickHelper = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 62, label = "Best ore on Mining HUD", desc = "Show the 'Best now' ore line on the Mining HUD. The full list is always in /miningprofit suggest.")
    public boolean mineShowSuggestion = false;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 66, label = "Lockpick aim offset (pixels)", desc = "How far above the particles to mark the spot (the wiki says 1-2 pixels above). 0 = exactly on the particles.", min = -4, max = 6)
    public int lockpickOffset = 1;
    @Setting(category = "Mining", section = "Mining HUD", order = 60, label = "Blocks listed", desc = "How many block types the breakdown shows.", min = 1, max = 20)
    public int mineBlocksShown = 4;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 73, label = "Show foraging stats", desc = "Sweep / Foraging Fortune lines (needs the Stats tab widget).")
    public boolean forShowStats = true;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 74, label = "Show tree gifts", desc = "Tree Gifts and trees per minute.")
    public boolean forShowTrees = true;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 75, label = "Show Forest Whispers", desc = "Forest Whispers gained and per hour.")
    public boolean forShowWhispers = true;
    @Setting(category = "Foraging", section = "Counting", order = 76, label = "Count any log", desc = "Count log blocks the mod doesn't know by name (new woods).")
    public boolean countAnyLog = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 78, label = "Show fishing stats", desc = "Fishing Speed, Sea Creature Chance... (needs the Stats tab widget).")
    public boolean fishShowStats = true;
    @Setting(category = "Fishing", section = "Helpers", order = 86, label = "Glow: rare sea creatures", desc = "Thunder, Lord Jawbus, Sea Emperor, Water Hydra, Yeti... get a cyan outline.")
    public boolean glowSeaCreatures = true;
    @Setting(category = "Fishing", section = "Helpers", order = 83, label = "Bite alert", desc = "A ding and a big \"REEL IN!\" on the HUD when Hypixel shows !!! over your bobber.")
    public boolean fishingAlert = true;
    @Setting(category = "Fishing", section = "Helpers", order = 84, label = "Bobber timer & sea creatures", desc = "Bobber timer, your living sea creatures (count + oldest), rare sea creature alert with a share button.")
    public boolean fishingExtras = true;
    @Setting(category = "Fishing", section = "Helpers", order = 85, label = "Sea creature warning at", desc = "Ding when this many of your sea creatures are alive (0 = off).", min = 0, max = 60)
    public int fishingCreatureCap = 10;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 79, label = "Show location", desc = "Where you're fishing.")
    public boolean fishShowLocation = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 80, label = "Show trophy fish", desc = "Trophy fish by type.")
    public boolean fishShowTrophies = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 81, label = "Count coin catches", desc = "Coins from GOOD/GREAT CATCH count as profit.")
    public boolean fishCountCoins = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 82, label = "Keep fishing active (s)", desc = "Hits within this many seconds of using your rod count as fishing (sea creatures).", min = 5, max = 600)
    public int fishingActiveSeconds = 60;
    @Setting(category = "Combat", section = "When a session starts", order = 95, label = "Hits to start combat", desc = "How many mob hits (within 30 s) start a Combat session. Players, NPCs and armor stands never count.", min = 1, max = 100)
    public int combatStartHits = 5;
    @Setting(category = "Combat", section = "When a session starts", order = 96, label = "Only start on slayer quests", desc = "Combat sessions only start when you start a slayer quest.")
    public boolean combatNeedsSlayer = false;
    @Setting(category = "Combat", section = "Combat HUD", order = 88, label = "Show slayer bosses", desc = "Bosses killed and per hour.")
    public boolean combatShowBosses = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 89, label = "Show kills", desc = "Kills, kills per hour and the mobs you killed most.")
    public boolean combatShowKills = true;
    @Setting(category = "Combat", section = "Glow", order = 98, label = "Glow: your slayer boss", desc = "Your own slayer boss (not other players') gets a red outline.")
    public boolean glowSlayer = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 92, label = "Boss health on HUD", desc = "Your slayer boss's health and time left on the Combat HUD.")
    public boolean showBossHealth = true;
    @Setting(category = "Combat", section = "Glow", order = 99, label = "Glow: Zealots", desc = "Zealots purple, Special Zealots pink.")
    public boolean glowZealots = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 93, label = "Grind HUDs", desc = "When most of your kills are one farmable mob (Zealots, Ghosts, Endermen...), the Combat HUD turns into a grind HUD with drop odds and 'since last drop'.")
    public boolean grindHuds = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 94, label = "Grind kills share %", desc = "How much of your kills must be one grind mob before the grind HUD appears.", min = 30, max = 100)
    public int grindShare = 60;
    @Setting(category = "Combat", section = "Combat HUD", order = 90, label = "Show profit per kill", desc = "Average coins per kill.")
    public boolean combatShowPerKill = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 91, label = "Show Magic Find", desc = "Magic Find from the Stats tab widget.")
    public boolean combatShowMagicFind = false;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 108, label = "Show runs", desc = "Runs, average time and runs per hour.")
    public boolean dungShowRuns = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 109, label = "Show last score", desc = "Your last run's score.")
    public boolean dungShowScore = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 113, label = "Secrets listed", desc = "How many nearby secrets the finder lists.", min = 1, max = 15)
    public int secretsShown = 5;
    @Setting(category = "Dungeons", section = "Secrets", order = 114, label = "Secret scan radius", desc = "How far (blocks) the finder looks around you.", min = 4, max = 32)
    public int secretRadius = 14;
    @Setting(category = "Dungeons", section = "Secrets", order = 115, label = "Find chests", desc = "Include chests.")
    public boolean secretChests = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 116, label = "Find levers", desc = "Include levers.")
    public boolean secretLevers = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 117, label = "Find essence", desc = "Include Wither Essence skulls.")
    public boolean secretEssence = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 118, label = "Find items", desc = "Include secret items on the floor.")
    public boolean secretItems = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 119, label = "Find bats", desc = "Include bats.")
    public boolean secretBats = true;
    @Setting(category = "Combat", section = "Kuudra", order = 101, label = "Show runs", desc = "Runs and runs per hour.")
    public boolean kuudraShowRuns = true;
    @Setting(category = "Combat", section = "Diana", order = 103, label = "Show burrows", desc = "Burrows dug and per hour.")
    public boolean dianaShowBurrows = true;
    @Setting(category = "Bazaar", section = "Your orders", order = 134, label = "Orders on HUD", desc = "How many orders the orders panel lists.", min = 1, max = 30)
    public int bzHudMaxOrders = 6;
    @Setting(category = "Bazaar", section = "Your orders", order = 134, label = "Show auctions on HUD", desc = "Your Auction House listings (sold / expired / undercut by a cheaper BIN) and bids (top / outbid / won) on the flip panel. Open /ah → Manage Auctions and Your Bids once to read existing ones.")
    public boolean auctionHud = true;
    @Setting(category = "Bazaar", section = "Your orders", order = 134, label = "Auctions on HUD", desc = "How many listings and bids the panel lists.", min = 1, max = 20)
    public int ahHudMax = 5;
    @Setting(category = "Bazaar", section = "Your orders", order = 135, label = "Show today's flip profit", desc = "Flip profit today on the orders panel.")
    public boolean bzShowToday = true;
    @Setting(category = "Bazaar", section = "Your orders", order = 137, label = "Ding when an order fills", desc = "Sound when a buy order or sell offer is filled.")
    public boolean bzFillSound = true;
    @Setting(category = "HUD", section = "Look & layout", order = 35, label = "Separate small panels", desc = "Show the secret finder, Jacob's contest and 'Best now' as their own panels you can place anywhere (/profit gui).")
    public boolean separatePanels = false;
    @Setting(category = "HUD", section = "Look & layout", order = 36, label = "Position per activity", desc = "The main HUD remembers a different spot for each activity (Farming, Mining...).")
    public boolean perActivityPositions = false;
    @Setting(category = "Advanced", section = "Setup", order = 234, label = "First-time setup done", desc = "Turn off to see the setup check again next time you join SkyBlock (or use /profit setup).")
    public boolean setupDone = false;
    @Setting(category = "Advanced", section = "Setup", order = 233, label = "Warn about duplicate features", desc = "Tells you when SkyHanni, Skyblocker or Odin already do something this mod does, so you can turn one off.")
    public boolean warnDuplicates = true;
    @Setting(category = "General", section = "Updates", order = 12, label = "Check for new builds", desc = "Tells you when your GitHub repo has a newer successful build than the one you're running.")
    public boolean updateCheck = true;
    @Setting(category = "Advanced", section = "Data sources", order = 232, label = "GitHub repo", desc = "owner/name of the repo that builds this mod, e.g. Kalapath/farmprofit.")
    public String updateRepo = "Kalapath/farmprofit";
    @Setting(category = "Items", section = "Enchants", order = 169, label = "Color enchantments", desc = "Colors enchantments in tooltips by level: perfect (max), great (above table max), good (table max), lower.")
    public boolean enchantColors = true;
    @Setting(category = "Items", section = "Enchants", order = 170, label = "Perfect enchant color", desc = "Enchantments at their absolute maximum level.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"})
    public String enchantPerfectColor = "gold";
    @Setting(category = "Items", section = "Enchants", order = 171, label = "Great enchant color", desc = "Above what the enchanting table gives, but not max yet.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"})
    public String enchantGreatColor = "light purple";
    @Setting(category = "Items", section = "Enchants", order = 172, label = "Good enchant color", desc = "Exactly the enchanting table maximum.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"})
    public String enchantGoodColor = "blue";
    @Setting(category = "Items", section = "Enchants", order = 173, label = "Low enchant color", desc = "Below the enchanting table maximum.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"})
    public String enchantPoorColor = "gray";
    @Setting(category = "Items", section = "Enchants", order = 174, label = "Ultimate enchants stay pink", desc = "Ultimate enchantments keep Hypixel's bold pink at every level (the ✦ still shows when maxed). Off = colored by level like the others.")
    public boolean ultimateKeepPink = true;
    @Setting(category = "Items", section = "Enchants", order = 175, label = "Mark maxed with ✦", desc = "Adds a ✦ after enchantments at their maximum level.")
    public boolean enchantMaxTag = true;
    @Setting(category = "Advanced", section = "Data sources", order = 228, label = "Enchant data URL", desc = "Where max levels come from (public SkyKings data). Empty = off.")
    public String enchantDataUrl = "https://raw.githubusercontent.com/SkyKings-Guild/Bot-Data/main/skyblock/enchants.json";
    @Setting(category = "Items", section = "Tooltips", order = 167, label = "Scroll long tooltips", desc = "Item descriptions taller than your screen can be scrolled with the mouse wheel.")
    public boolean tooltipScroll = true;
    @Setting(category = "Items", section = "Inventory", order = 161, label = "Inventory search box", desc = "A search box under every inventory and menu: matching items are highlighted, the rest dimmed.")
    public boolean inventorySearch = true;
    @Setting(category = "Items", section = "Inventory", order = 163, label = "Storage overview", desc = "Remembers your Ender Chest pages and backpacks when you open them; /itemsearch lists everything with where it is.")
    public boolean storageOverview = true;
    @Setting(category = "Items", section = "Inventory", order = 157, label = "Rarity colors on items", desc = "Colors each item's slot by its rarity (white, green, blue, purple, gold, pink...) in menus.")
    public boolean rarityBackground = true;
    @Setting(category = "Items", section = "Inventory", order = 160, label = "Labels on items", desc = "Pet level, minion tier and enchanted book level shown on the item in menus.")
    public boolean itemLabels = true;
    @Setting(category = "Items", section = "Inventory", order = 159, label = "Rarity colors on hotbar", desc = "The same rarity colors on your hotbar.")
    public boolean rarityHotbar = true;
    @Setting(category = "Items", section = "Inventory", order = 158, label = "Rarity color strength", desc = "0 = invisible, 255 = solid.", min = 0, max = 255)
    public int rarityOpacity = 170;
    @Setting(category = "Items", section = "Inventory", order = 162, label = "Search descriptions too", desc = "The search box also matches text in item descriptions (e.g. \"Farming Fortune\").")
    public boolean inventorySearchLore = true;
    @Setting(category = "Items", section = "Tooltips", order = 168, label = "Tooltip scroll speed", desc = "Lines moved per mouse-wheel notch.", min = 1, max = 20)
    public int tooltipScrollSpeed = 3;
    @Setting(category = "Items", section = "Tooltips", order = 166, label = "Craft cost in tooltips", desc = "Shows what an item costs to craft from bought ingredients, and whether crafting or buying is cheaper.")
    public boolean craftCost = true;
    @Setting(category = "Advanced", section = "Data sources", order = 227, label = "Recipe data URL", desc = "Where recipes come from (the public NEU item repository, downloaded weekly). Empty = off.")
    public String neuRepoUrl = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
    @Setting(category = "Items", section = "Inventory", order = 164, label = "Calculator in signs", desc = "In Bazaar / Auction amount signs, type a sum ending in = (e.g. 64x8= or 10m/3=) and it becomes the result.")
    public boolean signCalculator = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 121, label = "Three Weirdos solver", desc = "Tells you which chest to open.")
    public boolean solveWeirdos = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 122, label = "Blaze puzzle helper", desc = "Shows the lowest and highest health blaze with arrows.")
    public boolean solveBlaze = true;
    @Setting(category = "Dungeons", section = "Terminals", order = 128, label = "Terminal solvers", desc = "Floor 7 / Master 7 terminals: highlights what to click (you still click).")
    public boolean solveTerminals = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 120, label = "Puzzle markers", desc = "Show puzzle answers in the world with particles only you can see.")
    public boolean puzzleParticles = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 123, label = "Ice Fill solver", desc = "Green path over every ice tile, from where you stand.")
    public boolean solveIceFill = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 124, label = "Creeper Beams solver", desc = "Marks which sea lanterns to connect.")
    public boolean solveCreeper = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 125, label = "Teleport Maze helper", desc = "After each teleport, marks the pad most in the direction you're facing (toward the exit).")
    public boolean solveTeleport = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 126, label = "Tic Tac Toe solver", desc = "Marks the best button to press.")
    public boolean solveTicTacToe = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 127, label = "Quiz solver", desc = "Tells you the right answer for Ouro the Omniscient.")
    public boolean solveQuiz = true;
    @Setting(category = "Advanced", section = "Data sources", order = 229, label = "Quiz answers URL", desc = "Maintained list of quiz answers (Skytils data). Empty = off.")
    public String quizDataUrl = "https://raw.githubusercontent.com/Skytils/SkytilsMod-Data/main/solvers/oruotrivia.json";
    @Setting(category = "Dungeons", section = "Terminals", order = 129, label = "Melody terminal helper", desc = "Floor 7: turns the button green when it's time to click.")
    public boolean solveMelody = true;
    @Setting(category = "Items", section = "Talismans", order = 177, label = "Talismans listed", desc = "How many accessories /talismans shows.", min = 1, max = 50)
    public int talismanCount = 10;
    @Setting(category = "Items", section = "Talismans", order = 178, label = "include crafting", desc = "Also consider crafting an accessory (and upgrading ones you own) when it's cheaper than the Auction House.")
    public boolean talismanUseCraft = true;
    @Setting(category = "Items", section = "Talismans", order = 179, label = "max price", desc = "Skip accessories costing more than this. 0 = no limit.", min = 0, max = 1e13)
    public double talismanMaxPrice = 0;
    @Setting(category = "Chat & sounds", section = "Chat filter", order = 181, label = "Hide sack messages", desc = "Hides the \"[Sacks] +X items\" lines (they're still counted).")
    public boolean chatHideSacks = false;
    @Setting(category = "Chat & sounds", section = "Chat filter", order = 182, label = "Hide ability cooldown", desc = "Hides \"This ability is on cooldown\".")
    public boolean chatHideCooldown = true;
    @Setting(category = "Chat & sounds", section = "Chat filter", order = 183, label = "Hide \"blocks in the way\"", desc = "Hides \"There are blocks in the way!\" (teleport items).")
    public boolean chatHideBlocksInWay = true;
    @Setting(category = "Chat & sounds", section = "Chat filter", order = 184, label = "Hide visitor chatter", desc = "Hides [NPC] lines while in the Garden (visitor offers are still read).")
    public boolean chatHideVisitorChat = false;
    @Setting(category = "Chat & sounds", section = "Chat filter", order = 185, label = "Hide Watchdog announcements", desc = "Hides Watchdog / staff ban announcements.")
    public boolean chatHideWatchdog = true;
    @Setting(category = "Chat & sounds", section = "Chat filter", order = 186, label = "Hide messages containing", desc = "Comma separated words or phrases; any chat line containing one is hidden.")
    public java.util.List<String> chatHideContaining = new java.util.ArrayList<>();
    @Setting(category = "Chat & sounds", section = "Sounds", order = 187, label = "Mute explosions", desc = "Mutes explosion sounds sent by the server.")
    public boolean muteExplosions = false;
    @Setting(category = "Chat & sounds", section = "Sounds", order = 188, label = "Muted sounds", desc = "Comma separated sound names (or parts of names) to mute. Turn on 'Show sound names' to find them.")
    public java.util.List<String> mutedSounds = new java.util.ArrayList<>();
    @Setting(category = "Chat & sounds", section = "Sounds", order = 189, label = "Show sound names", desc = "Prints each new sound's name in chat once, so you can add it to Muted sounds. Turn off after.")
    public boolean logSounds = false;
    @Setting(category = "General", section = "Party", order = 8, label = "Waypoints from party chat", desc = "Coordinates your party posts in chat (x: y: z: or three numbers) become a light beam + HUD arrow.")
    public boolean partyWaypoints = true;
    @Setting(category = "General", section = "Party", order = 9, label = "Party waypoint time (s)", desc = "How long a waypoint from party chat stays.", min = 10, max = 1800)
    public int waypointSeconds = 120;
    @Setting(category = "Extras", section = "Animations", order = 208, label = "Disable swimming pose", desc = "Stay upright in water instead of swimming / crawling (your player only, visual).")
    public boolean disableSwimming = false;
    @Setting(category = "Extras", section = "Animations", order = 209, label = "Slow swing", desc = "Your arm swing animation is slower, like the old 1.8 swing (visual only).")
    public boolean slowSwing = false;
    @Setting(category = "Extras", section = "Animations", order = 210, label = "Swing length (ticks)", desc = "How long one swing takes with Slow swing on. Vanilla is 6; 10-14 looks like 1.8.", min = 2, max = 40)
    public int swingDuration = 12;
    @Setting(category = "Items", section = "Enchants", order = 176, label = "Rainbow maxed enchants", desc = "Maxed enchantments in tooltips are rainbow-colored (sets the Perfect enchant color).")
    public boolean rainbowMaxed = false;
    @Setting(category = "Timers", section = "Panel", order = 190, label = "Timers panel", desc = "A separate HUD panel with event timers (move it with /profit gui).")
    public boolean timersPanel = true;
    @Setting(category = "Timers", section = "Events", order = 191, label = "Dark Auction", desc = "Every hour at :55.")
    public boolean timerDarkAuction = true;
    @Setting(category = "Timers", section = "Events", order = 192, label = "Mining events", desc = "Current Dwarven Mines and Crystal Hollows events with time left, from anywhere (shared service), or your tab list as a backup.")
    public boolean timerMiningEvent = true;
    @Setting(category = "Advanced", section = "Data sources", order = 231, label = "Mining events source", desc = "Shared mining-event service (Soopy's, also used by SkyHanni's Mining Event Tracker): shows Dwarven Mines / Crystal Hollows events from anywhere. Empty = only your own tab list.")
    public String miningEventsUrl = "https://api.soopy.dev/skyblock/chevents/get";
    @Setting(category = "Timers", section = "Events", order = 193, label = "Cult of the Fallen Star", desc = "Days 7, 14, 21 and 28 of every SkyBlock month, 00:00-06:00.")
    public boolean timerCult = true;
    @Setting(category = "Timers", section = "Events", order = 194, label = "Spooky Festival", desc = "Autumn 29-31.")
    public boolean timerSpooky = true;
    @Setting(category = "Timers", section = "Events", order = 195, label = "Traveling Zoo", desc = "Early Summer 1-3 and Early Winter 1-3.")
    public boolean timerZoo = false;
    @Setting(category = "Timers", section = "Events", order = 196, label = "Season of Jerry", desc = "Late Winter 24-26.")
    public boolean timerJerry = false;
    @Setting(category = "Timers", section = "Events", order = 197, label = "New Year", desc = "Late Winter 29-31.")
    public boolean timerNewYear = false;
    @Setting(category = "Timers", section = "Events", order = 198, label = "Hoppity's Hunt", desc = "All of Spring.")
    public boolean timerHoppity = false;
    @Setting(category = "Timers", section = "Events", order = 199, label = "Jacob's contest", desc = "Next contest and its crops.")
    public boolean timerJacob = true;
    @Setting(category = "Timers", section = "World bosses", order = 200, label = "Boss: Broodmother", desc = "Spider's Den: Dormant / Soon / Imminent / Alive (from the tab list), remembered when you leave.")
    public boolean bossBroodmother = true;
    @Setting(category = "Timers", section = "World bosses", order = 201, label = "Boss: Endstone Protector", desc = "The End: the Protector's stage (from the tab list).")
    public boolean bossProtector = true;
    @Setting(category = "Timers", section = "World bosses", order = 202, label = "Boss: Ender Dragon", desc = "Dragon's Nest: dragon / eyes status (from the tab list).")
    public boolean bossDragon = false;
    @Setting(category = "Timers", section = "World bosses", order = 203, label = "Boss: Arachne", desc = "Spider's Den: Arachne status, if Hypixel shows it in the tab list.")
    public boolean bossArachne = false;
    @Setting(category = "Timers", section = "World bosses", order = 204, label = "Boss: Kuudra", desc = "Kuudra status, if shown in the tab list.")
    public boolean bossKuudra = false;
    @Setting(category = "Timers", section = "World bosses", order = 205, label = "Boss: Vanquisher", desc = "Crimson Isle: Vanquisher status, if shown in the tab list.")
    public boolean bossVanquisher = false;
    @Setting(category = "Timers", section = "World bosses", order = 206, label = "Boss: Golden Goblin", desc = "Dwarven Mines / Crystal Hollows: Golden Goblin status, if shown in the tab list.")
    public boolean bossGoldenGoblin = false;
    @Setting(category = "Timers", section = "World bosses", order = 207, label = "Extra tab lines to watch", desc = "Comma separated starts of tab-list lines to show and remember, e.g. \"Matriarch\" or \"Bal\". Shows the rest of that line.")
    public java.util.List<String> timerExtraTabLines = new java.util.ArrayList<>();
    @Setting(category = "Extras", section = "Macro keys (set keys in Controls)", order = 211, label = "Macro 1 command", desc = "What Macro 1 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro1 = "/warp garden";
    @Setting(category = "Extras", section = "Macro keys (set keys in Controls)", order = 212, label = "Macro 2 command", desc = "What Macro 2 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro2 = "/bz";
    @Setting(category = "Extras", section = "Macro keys (set keys in Controls)", order = 213, label = "Macro 3 command", desc = "What Macro 3 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro3 = "/pets";
    @Setting(category = "Extras", section = "Macro keys (set keys in Controls)", order = 214, label = "Macro 4 command", desc = "What Macro 4 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro4 = "/wardrobe";
    @Setting(category = "Extras", section = "Macro keys (set keys in Controls)", order = 215, label = "Macro 5 command", desc = "What Macro 5 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro5 = "/storage";
    @Setting(category = "Extras", section = "Macro keys (set keys in Controls)", order = 216, label = "Macro 6 command", desc = "What Macro 6 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro6 = "/warp hub";
    @Setting(category = "General", section = "Sessions", order = 3, label = "Reset after (minutes)", desc = "Minutes without activity before a session ends and is saved to history.", min = 1, max = 600)
    public int resetMinutes = 15;
    /**
     * "instasell" = Bazaar instant-sell, "sellorder" = Bazaar sell offer,
     * "npc" = NPC sell price, "best" = whichever of instasell / NPC pays more.
     */
    @Setting(category = "General", section = "Prices & numbers", order = 6, label = "Price source", desc = "best = Bazaar instasell or NPC, whichever pays more. instasell / sellorder = Bazaar. npc = NPC sell price.", options = {"best", "instasell", "sellorder", "npc"})
    public String priceMode = "best";
    /** The session timer pauses after this many seconds without activity (AFK doesn't ruin your /h). */
    @Setting(category = "General", section = "Sessions", order = 4, label = "Pause timer after (seconds)", desc = "The session clock pauses after this long without activity, so AFK time doesn't lower profit/h.", min = 5, max = 600)
    public int pauseSeconds = 30;
    /** On the HUD, items worth less than this (in total) are grouped into one "cheap items" line. */
    /** Items you never want counted (e.g. "Hay Bale"). Add with /profit ignore <item> */
    @Setting(category = "Items", section = "Not counted", order = 180, label = "Ignored items", desc = "Comma separated. These never count toward profit. Right-click an item on the HUD (chat open) to add it.")
    public java.util.List<String> ignoredItems = new java.util.ArrayList<>();
    @Setting(category = "HUD", section = "Main HUD", order = 13, label = "Show HUD", desc = "Turn the whole HUD on or off.")
    public boolean hudEnabled = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 37, label = "Farming HUD", desc = "Show the Farming HUD (tracking keeps running when hidden).")
    public boolean showFarmingHud = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 55, label = "Mining HUD", desc = "Show the Mining HUD (tracking keeps running when hidden).")
    public boolean showMiningHud = true;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 72, label = "Foraging HUD", desc = "Show the Foraging HUD (tracking keeps running when hidden).")
    public boolean showForagingHud = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 77, label = "Fishing HUD", desc = "Show the Fishing HUD (tracking keeps running when hidden).")
    public boolean showFishingHud = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 87, label = "Combat HUD", desc = "Show the Combat / slayer HUD (tracking keeps running when hidden).")
    public boolean showCombatHud = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 107, label = "Catacombs HUD", desc = "Show the Catacombs HUD (tracking keeps running when hidden).")
    public boolean showDungeonsHud = true;
    @Setting(category = "Combat", section = "Kuudra", order = 100, label = "Kuudra HUD", desc = "Show the Kuudra HUD (tracking keeps running when hidden).")
    public boolean showKuudraHud = true;
    @Setting(category = "Combat", section = "Diana", order = 102, label = "Diana HUD", desc = "Show the Diana (Mythological Ritual) HUD (tracking keeps running when hidden).")
    public boolean showDianaHud = true;
    @Setting(category = "Combat", section = "Diana", order = 105, label = "Glow: Minos Inquisitor", desc = "Minos Inquisitors get a gold outline.")
    public boolean glowInquisitor = true;
    @Setting(category = "Combat", section = "Diana", order = 104, label = "Burrow finder", desc = "Marks burrows from their particles (green start, red mob, gold treasure) and turns the Ancestral Spade's particle trail into a cyan direction beam.")
    public boolean dianaHelper = true;
    @Setting(category = "Combat", section = "Diana", order = 106, label = "Auto-share Inquisitor", desc = "Post your coordinates in party chat automatically when you dig up a Minos Inquisitor (off = a [Share] button instead).")
    public boolean autoShareInquisitor = false;
    @Setting(category = "HUD", section = "Main HUD", order = 24, label = "Show all-time line", desc = "Adds your all-time profit and profit/h for the activity under the session numbers.")
    public boolean hudShowTotal = false;
    @Setting(category = "HUD", section = "Main HUD", order = 27, label = "Show mayor", desc = "Shows the mayor on the HUD of the activity they boost.")
    public boolean showMayor = true;
    @Setting(category = "Items", section = "Tooltips", order = 165, label = "Price tooltips", desc = "Adds Bazaar, lowest BIN and NPC prices to item tooltips.")
    public boolean priceTooltips = true;
    @Setting(category = "Farming", section = "Garden helpers", order = 52, label = "Show Jacob's contests", desc = "Next contest crops and countdown on the Farming HUD.")
    public boolean showContests = true;
    @Setting(category = "Advanced", section = "Data sources", order = 230, label = "Contest data URL", desc = "Where upcoming contests come from (community data; Hypixel doesn't publish them). Empty = off.")
    public String jacobContestsUrl = "https://api.elitebot.dev/contests/at/now";
    @Setting(category = "Farming", section = "Values", order = 53, label = "Copper value (coins)", desc = "Coins each copper from Garden visitors is worth to you. 0 = don't count copper.", min = 0, max = 1e9)
    public double copperValue = 0;
    @Setting(category = "Other", label = "Orders panel X", desc = "-1 = right under the main HUD. Or drag it with chat open.", min = -1, max = 10000, hidden = true)
    public int bazaarHudX = -1;
    @Setting(category = "Other", label = "Orders panel Y", desc = "-1 = right under the main HUD. Or drag it with chat open.", min = -1, max = 10000, hidden = true)
    public int bazaarHudY = -1;
    @Setting(category = "Bazaar", section = "Your orders", order = 139, label = "Warn about odd orders", desc = "Chat warning when an order you place is far from the market price (possible typo).")
    public boolean bzWarnMistakes = true;
    @Setting(category = "Other", label = "HUD X position", desc = "Or drag the HUD with chat open.", min = 0, max = 10000, hidden = true)
    public int hudX = 5;
    @Setting(category = "Other", label = "HUD Y position", desc = "Or drag the HUD with chat open.", min = 0, max = 10000, hidden = true)
    public int hudY = 5;
    @Setting(category = "HUD", section = "Main HUD", order = 18, label = "Items shown", desc = "Up to this many items are listed one by one. If you have more, the cheapest are added up into one line.", min = 1, max = 50)
    public int hudMaxItems = 6;
    @Setting(category = "Other", label = "HUD scale", desc = "0.5 to 3.", min = 0.5, max = 3, hidden = true)
    public double hudScale = 1.0;
    @Setting(category = "HUD", section = "Main HUD", order = 19, label = "Item icons", desc = "Small icons next to items you've had in your inventory.")
    public boolean hudIcons = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 61, label = "Show commissions", desc = "Commissions on the Mining HUD.")
    public boolean showCommissions = true;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 28, label = "Show rare drops", desc = "Rare drops section on the HUD.")
    public boolean showRareDrops = true;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 30, label = "Show shards", desc = "Attribute shards line on the HUD.")
    public boolean showShards = true;
    /** false = HUD only shows profit lines (no stats, powder, commissions...). Toggle with /profit details */
    @Setting(category = "HUD", section = "Main HUD", order = 25, label = "Show details", desc = "Stats, powder, commissions, BPS... Off = profit lines only.")
    public boolean hudDetails = true;
    /** "Best now" line on the farming and mining HUD. */
    @Setting(category = "HUD", section = "Main HUD", order = 26, label = "Show 'Best now' tip", desc = "Best crop / ore to farm right now on the Farming and Mining HUDs.")
    public boolean showSuggestion = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 43, label = "Best crop on Farming HUD", desc = "Show the 'Best now' crop line on the Farming HUD. The full list is always in /profit suggest.")
    public boolean farmShowSuggestion = false;
    /** Dungeon secret finder on the Catacombs HUD. */
    @Setting(category = "Dungeons", section = "Secrets", order = 112, label = "Dungeon secret finder", desc = "Room counter and nearby secret list on the Catacombs HUD.")
    public boolean secretFinder = true;
    @Setting(category = "Dungeons", section = "Glow", order = 130, label = "Glow: starred mobs", desc = "Starred mobs (the ones that count for clearing a room) get a gold outline through walls.")
    public boolean glowStarred = true;
    @Setting(category = "Dungeons", section = "Glow", order = 131, label = "Glow: Wither / Blood key", desc = "The dropped Wither Key (dark) and Blood Key (red) get an outline.")
    public boolean glowKeys = true;
    @Setting(category = "Dungeons", section = "Glow", order = 132, label = "Glow: bats", desc = "Secret bats get a green outline.")
    public boolean glowBats = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 110, label = "Show run info", desc = "Your secrets, team secrets %, crypts and deaths on the Catacombs HUD.")
    public boolean dungShowRunInfo = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 111, label = "Chest profit in chat", desc = "When you open a reward chest or Croesus, show each chest's value, cost and profit.")
    public boolean chestProfit = true;
    @Setting(category = "Advanced", section = "What counts as profit", order = 219, label = "Count items in menus", desc = "Comma separated menu titles where items you receive count as profit (reward chests).")
    public java.util.List<String> countInMenus = new java.util.ArrayList<>(java.util.List.of(
            "Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest", "Bedrock Chest"));
    /** Blocks per second used for farming suggestions until you've farmed for a minute. */
    @Setting(category = "Farming", section = "Values", order = 54, label = "Default blocks/s", desc = "Used for farming suggestions until you've farmed for a minute.", min = 1, max = 40)
    public double defaultBps = 19;
    /** Share of the theoretical mining rate you really get (walking, aiming, abilities...). */
    @Setting(category = "Mining", section = "Values", order = 71, label = "Mining efficiency", desc = "Share of the theoretical mining rate you really get (walking, aiming). 0.6 = 60%.", min = 0.05, max = 1)
    public double miningEfficiency = 0.6;

    // ----- Bazaar flipping (/flips) -----
    @Setting(category = "Bazaar", section = "Flip finder", order = 140, label = "Budget", desc = "Coins you want to put into flips.", min = 0, max = 10000000000000.0)
    public double bzBudget = 10_000_000;
    @Setting(category = "Bazaar", section = "Flip filters", order = 146, label = "Min weekly volume", desc = "Higher = flips fill faster.", min = 0, max = 1000000000000.0)
    public double bzMinVolume = 20_000;      // weekly
    @Setting(category = "Bazaar", section = "Flip filters", order = 147, label = "Min margin %", desc = "After tax.", min = 0, max = 1000)
    public double bzMinMargin = 1;           // % after tax
    @Setting(category = "Bazaar", section = "Flip filters", order = 148, label = "Max item price", desc = "0 = no limit.", min = 0, max = 1000000000000.0)
    public double bzMaxPrice = 0;            // 0 = no limit
    @Setting(category = "Bazaar", section = "Flip finder", order = 142, label = "Bazaar tax %", desc = "Lower with the Bazaar Flipper upgrade.", min = 0, max = 10)
    public double bzTax = 1.25;              // % (lower with the Bazaar Flipper upgrade)
    @Setting(category = "Bazaar", section = "Flip finder", order = 141, label = "Your volume share %", desc = "How much of an item's trading you expect to get (competition estimate).", min = 0, max = 100)
    public double bzShare = 10;              // % of the item's volume you expect to get
    @Setting(category = "Bazaar", section = "Flip finder", order = 145, label = "Hot flip alert (coins/h)", desc = "Ping when a new flip beats this. 0 = off.", min = 0, max = 1000000000000.0)
    public double bzFlipAlert = 0;           // ping when a flip beats this many coins/h (0 = off)
    @Setting(category = "Bazaar", section = "Flip finder", order = 143, label = "Flips listed", desc = "How many flips /flips shows.", min = 1, max = 50)
    public int bzTop = 10;
    @Setting(category = "Bazaar", section = "Your orders", order = 136, label = "Order check every (s)", desc = "How often prices are checked while you have orders out.", min = 10, max = 600)
    public int bzRefreshSeconds = 20;        // price checks while you have orders out
    @Setting(category = "Bazaar", section = "Your orders", order = 133, label = "Show orders on HUD", desc = "Your open bazaar orders under the HUD.")
    public boolean bazaarHud = true;
    @Setting(category = "Bazaar", section = "Your orders", order = 138, label = "Alert sound", desc = "Ding when you're outbid/undercut or an order fills.")
    public boolean bzSound = true;
    @Setting(category = "Other", label = "min profit", desc = "Craft flips (Craft → Bazaar / AH tabs) must make at least this much per craft.", min = 0, max = 1e12, hidden = true)
    public double craftFlipMinProfit = 1000;
    @Setting(category = "Other", label = "Flips: min profit per flip", desc = "Hide Bazaar flips whose full order (the amount you'd buy) makes less than this. 0 = off.", min = 0, max = 1e12, hidden = true)
    public double bzMinFlipProfit = 0;
    @Setting(category = "Other", label = "Flips: min profit from budget", desc = "Instead of the fixed number above, a flip order must make at least a % of your flip budget (e.g. 1% of 50m = 500k).", hidden = true)
    public boolean bzMinFlipAuto = false;
    @Setting(category = "Other", label = "Flips: % of budget", desc = "With 'min profit from budget' on: profit per flip order must be at least this % of your budget.", min = 0.01, max = 50, hidden = true)
    public double bzMinFlipPercent = 1;
    @Setting(category = "Other", label = "Flips: min item price", desc = "Hide flips on items cheaper than this (so you never have to buy huge amounts). 0 = off.", min = 0, max = 1e12, hidden = true)
    public double bzMinItemPrice = 0;
    @Setting(category = "Other", label = "Flips: min item price from budget", desc = "Instead of the fixed price above, items must cost at least a % of your flip budget (0.1% of 50m = 50k each, so at most ~1,000 items).", hidden = true)
    public boolean bzMinItemPriceAuto = false;
    @Setting(category = "Other", label = "Flips: item price % of budget", desc = "With 'min item price from budget' on: each item must cost at least this % of your budget.", min = 0.001, max = 50, hidden = true)
    public double bzMinItemPricePercent = 0.1;
    @Setting(category = "Other", label = "min profit from budget", desc = "Instead of the fixed min profit above, require a % of your flip budget per craft (e.g. 1% of 10m = 100k).", hidden = true)
    public boolean craftFlipAutoMinProfit = false;
    @Setting(category = "Other", label = "% of budget", desc = "With 'min profit from budget' on: profit per craft must be at least this % of your flip budget.", min = 0.01, max = 50, hidden = true)
    public double craftFlipAutoPercent = 1;
    @Setting(category = "Bazaar", section = "Flip finder", order = 144, label = "Safe flips only", desc = "For normal and craft flips: hide volatile (buy/sell gap over 25%), competitive (under 3% margin, lots of orders), slow (under 100 sold/h) and possibly manipulated or inflated ones.")
    public boolean craftFlipSafeOnly = false;
    @Setting(category = "Bazaar", section = "Flip filters", order = 149, label = "Min item price", desc = "Hide flips on items cheaper than this, so you never buy thousands of something. A price (50k) or a % of your budget (0.1% of 50m = 50k). 0 = off.")
    public String flipMinItemPrice = "0";
    @Setting(category = "Bazaar", section = "Flip filters", order = 150, label = "Min profit per flip", desc = "Hide flips whose whole order (the amount you'd buy) makes less than this. Coins (500k) or a % of your budget (1%). 0 = off.")
    public String flipMinProfit = "0";
    @Setting(category = "Bazaar", section = "Craft flips", order = 151, label = "min profit", desc = "Craft flips (Craft → Bazaar / AH) must make at least this per craft. Coins (10k) or a % of your budget (1%).")
    public String craftMinProfit = "1000";
    @Setting(category = "Bazaar", section = "Craft flips", order = 151, label = "Only recipes I can craft", desc = "Hide craft flips you haven't unlocked yet (collection, slayer or skill level). Your levels are read when you open /collection categories, the slayer menu and /skills, and from level-up messages.")
    public boolean craftOnlyUnlocked = true;
    @Setting(category = "Bazaar", section = "Craft flips", order = 151, label = "Hide when level unknown", desc = "Also hide recipes whose requirement you haven't been seen to meet (e.g. that collection's menu wasn't opened yet). Off = they show with a note.")
    public boolean craftHideUnknown = false;
    @Setting(category = "Other", label = "flip rules migrated", desc = "internal", hidden = true)
    public boolean flipRulesMigrated = false;
    @Setting(category = "Bazaar", section = "Market signals (/market)", order = 152, label = "items analysed", desc = "How many of the most traded Bazaar items /market downloads history for (more = slower first load).", min = 10, max = 300)
    public int marketItems = 60;
    @Setting(category = "Bazaar", section = "Market signals (/market)", order = 153, label = "history (days)", desc = "How far back /market looks for mayor terms and events.", min = 30, max = 730)
    public int marketHistoryDays = 365;
    @Setting(category = "Bazaar", section = "Market signals (/market)", order = 154, label = "min past cases", desc = "A mayor / event signal needs at least this many past cases.", min = 2, max = 20)
    public int marketMinSamples = 3;
    @Setting(category = "Bazaar", section = "Market signals (/market)", order = 155, label = "min move %", desc = "Only show signals that expect at least this % change.", min = 1, max = 100)
    public double marketMinMove = 5;
    @Setting(category = "Bazaar", section = "Market signals (/market)", order = 156, label = "min hit rate %", desc = "Only show signals the backtest called right at least this often.", min = 50, max = 100)
    public double marketMinHitRate = 60;
    /** Coins subtracted from profit every time a slayer quest starts (set to what your tier costs). */
    @Setting(category = "Combat", section = "When a session starts", order = 97, label = "Slayer quest cost", desc = "Coins subtracted per slayer quest. 0 = detect automatically from your purse.", min = 0, max = 1000000000.0)
    public double slayerQuestCost = 0;
    /** Tab-list "Area:" names that count as mining. */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 221, label = "Extra mining areas", desc = "Comma separated. Dwarven Mines, Crystal Hollows, Mineshafts, Glacite Tunnels, Deep Caverns and Gold Mine are built in; add others here.")
    public java.util.List<String> miningAreas = new java.util.ArrayList<>(java.util.List.of(
            "Dwarven Mines", "Crystal Hollows", "Mineshaft", "Glacite", "Deep Caverns", "Gold Mine"));
    /** Lowest-BIN prices for auction-house items (rare drops). Set to "" to disable. */
    @Setting(category = "Advanced", section = "Data sources", order = 226, label = "Lowest BIN price URL", desc = "Where auction-house prices come from. Leave empty to turn auction prices off.")
    public String lowestBinUrl = "https://lb.tricked.dev/lowestbins";
    /** "Item Name": "BAZAAR_ID" */
    @Setting(category = "Advanced", section = "What counts as profit", order = 220, label = "Extra item prices", desc = "name=BAZAAR_ID, separated by commas. For items the mod can't price.")
    public Map<String, String> extraItems = new HashMap<>();
    /** "block_id": "Crop Name" */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 223, label = "Extra crop blocks", desc = "block_id=Crop Name, separated by commas.")
    public Map<String, String> extraCropBlocks = new HashMap<>();
    /** "block_id": "Ore Name" */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 224, label = "Extra ore blocks", desc = "block_id=Ore Name, separated by commas.")
    public Map<String, String> extraOreBlocks = new HashMap<>();
    /** "block_id": "Wood Name" */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 225, label = "Extra log blocks", desc = "block_id=Wood Name, separated by commas.")
    public Map<String, String> extraLogBlocks = new HashMap<>();
    /** Tab-list "Area:" names that count as foraging islands. */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 222, label = "Extra foraging areas", desc = "Comma separated. The Park, Galatea, Moonglade Marsh and Torrhus Canyon are built in; add others here.")
    public java.util.List<String> foragingAreas = new java.util.ArrayList<>(java.util.List.of("Galatea", "The Park"));

    /** Whether the HUD for this activity is switched on. */
    public boolean hudFor(String type) {
        if (Tracker.isMiningType(type)) return showMiningHud;
        return switch (type) {
            case Tracker.FORAGING -> showForagingHud;
            case Tracker.FISHING -> showFishingHud;
            case Tracker.COMBAT -> showCombatHud;
            case Tracker.DUNGEONS -> showDungeonsHud;
            case Tracker.KUUDRA -> showKuudraHud;
            case Tracker.DIANA -> showDianaHud;
            default -> showFarmingHud;
        };
    }

    /** The pest box color as a number. */
    public int pestBoxColor() {
        try { return Integer.parseInt(pestBoxColorHex.replace("#", ""), 16); } catch (Exception e) { return 0xFF3030; }
    }

    private static String num(double v) {
        return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
    }

    /** A flip rule: "50k" = 50,000 coins, "1%" = 1% of the flip budget, blank / 0 = off. */
    public static double rule(String text) {
        if (text == null || text.isBlank()) return 0;
        String t = text.trim().replace(",", "");
        try {
            if (t.endsWith("%")) return get().bzBudget * Double.parseDouble(t.substring(0, t.length() - 1).trim()) / 100.0;
            double v = FlipsCommand.parseAmount(t);
            return Double.isNaN(v) ? 0 : Math.max(0, v);
        } catch (Exception e) { return 0; }
    }

    public static Config get() {
        if (instance == null) load();
        return instance;
    }

    public static void load() {
        try {
            if (Files.exists(FILE)) instance = GSON.fromJson(Files.readString(FILE), Config.class);
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not read config, using defaults", e);
        }
        if (instance == null) instance = new Config();
        if (instance.extraItems == null) instance.extraItems = new HashMap<>();
        if (instance.extraCropBlocks == null) instance.extraCropBlocks = new HashMap<>();
        if (instance.extraOreBlocks == null) instance.extraOreBlocks = new HashMap<>();
        if (instance.countInMenus == null) instance.countInMenus = new java.util.ArrayList<>(java.util.List.of(
                "Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest", "Bedrock Chest"));
        if ("FF3030".equals(instance.pestBoxColorHex) && !instance.pestColorMigrated) { instance.pestBoxColorHex = "00FF00"; instance.pestTrail = false; }
        instance.pestColorMigrated = true;
        if (!instance.flipRulesMigrated) {     // 8.8: the fixed / "% of budget" pairs became one box each
            Config c = instance;
            c.flipMinProfit = c.bzMinFlipAuto ? num(c.bzMinFlipPercent) + "%" : num(c.bzMinFlipProfit);
            c.flipMinItemPrice = c.bzMinItemPriceAuto ? num(c.bzMinItemPricePercent) + "%" : num(c.bzMinItemPrice);
            c.craftMinProfit = c.craftFlipAutoMinProfit ? num(c.craftFlipAutoPercent) + "%" : num(c.craftFlipMinProfit);
            c.flipRulesMigrated = true;
        }
        if (instance.flipMinProfit == null) instance.flipMinProfit = "0";
        if (instance.flipMinItemPrice == null) instance.flipMinItemPrice = "0";
        if (instance.craftMinProfit == null) instance.craftMinProfit = "1000";
        if (instance.timerExtraTabLines == null) instance.timerExtraTabLines = new java.util.ArrayList<>();
        if (instance.chatHideContaining == null) instance.chatHideContaining = new java.util.ArrayList<>();
        if (instance.mutedSounds == null) instance.mutedSounds = new java.util.ArrayList<>();
        if (instance.ignoredItems == null) instance.ignoredItems = new java.util.ArrayList<>();
        if (instance.miningAreas == null) instance.miningAreas = new java.util.ArrayList<>(java.util.List.of(
                "Dwarven Mines", "Crystal Hollows", "Mineshaft", "Glacite", "Deep Caverns", "Gold Mine"));
        if (instance.extraLogBlocks == null) instance.extraLogBlocks = new HashMap<>();
        if (instance.foragingAreas == null) instance.foragingAreas = new java.util.ArrayList<>(java.util.List.of("Galatea", "The Park"));
        save();
    }

    /** Swap in a whole new set of settings (profiles / import). */
    public static void replace(Config c) {
        if (c == null) return;
        instance = c;
        save();
        load();     // fills in anything missing
    }

    public static void save() {
        try {
            Files.createDirectories(DIR);
            Files.writeString(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save config", e);
        }
    }
}
