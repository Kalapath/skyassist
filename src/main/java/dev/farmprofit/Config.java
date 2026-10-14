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

    @Setting(category = "General", section = "Sessions", order = 10, label = "Switch HUD on arrival", desc = "Arriving on an island (Garden, Galatea, Mines...) switches the HUD to it right away.")
    public boolean switchHudOnArrival = true;
    @Setting(category = "General", section = "Sessions", order = 20, label = "Session end message", desc = "Chat summary when a session ends and is saved.")
    public boolean announceSessionEnd = true;
    @Setting(category = "General", section = "Performance & look", order = 180, label = "Performance mode", desc = "Scans and particle markers run half as often and less dense. Use it if SkyAssist costs you FPS (check with /profit perf).")
    public boolean performanceMode = false;
    @Setting(category = "General", section = "Performance & look", order = 190, label = "Highlight style", desc = "auto = glowing outline, but particle boxes when a shader pack (Iris) is on. outline / particles = always that.", options = {"auto", "outline", "particles"})
    public String glowStyle = "auto";
    @Setting(category = "General", section = "Sessions", order = 50, label = "Count purse coins", desc = "Coins that go straight into your purse while you're active (Bountiful reforge, mob coins, Midas, coin catches...) count as profit. Read from the sidebar; menus are ignored so selling / bank don't count.")
    public boolean trackPurse = true;
    @Setting(category = "Advanced", section = "What counts as profit", order = 10, label = "Only count what the activity gave", desc = "Items count for a profit HUD only if they're that activity's own items or arrived right after you did it (not trades, other people's drops, etc.).")
    public boolean strictAttribution = true;
    @Setting(category = "Advanced", section = "What counts as profit", order = 20, label = "Activity window (s)", desc = "How long after your last action (break, catch, hit...) a new item still counts.", min = 1, max = 30)
    public int attributionSeconds = 4;
    @Setting(category = "HUD", section = "Main HUD", order = 100, label = "Show purse coins line", desc = "The 'Purse coins: +X (Bountiful, mob coins...)' line. Off = still counted in profit, just not listed.", parent = "hudEnabled")
    public boolean hudShowPurse = false;
    @Setting(category = "HUD", section = "Main HUD", order = 80, label = "Hide used-up items", desc = "Don't list items with a negative amount (raw items a compactor turned into enchanted ones). They still count in profit.", parent = "hudShowItems")
    public boolean hideNegativeItems = true;
    @Setting(category = "General", section = "Prices & numbers", order = 70, label = "Number format", desc = "compact = 1.2M, full = 1,234,567.", options = {"compact", "full"})
    public String numberFormat = "compact";
    @Setting(category = "HUD", section = "Look & layout", order = 210, label = "Background opacity", desc = "0 = invisible, 255 = solid black.", min = 0, max = 255, parent = "hudEnabled")
    public int hudOpacity = 144;
    @Setting(category = "HUD", section = "Look & layout", order = 220, label = "Line height", desc = "Pixels per line (10 = default).", min = 8, max = 20, parent = "hudEnabled")
    public int hudLineHeight = 10;
    @Setting(category = "HUD", section = "Look & layout", order = 230, label = "Text shadow", desc = "Drop shadow behind HUD text.", parent = "hudEnabled")
    public boolean hudShadow = true;
    @Setting(category = "HUD", section = "Main HUD", order = 20, label = "Show title", desc = "The activity name at the top of the HUD.", parent = "hudEnabled")
    public boolean hudShowTitle = true;
    @Setting(category = "HUD", section = "Main HUD", order = 30, label = "Show session time", desc = "The Time line.", parent = "hudEnabled")
    public boolean hudShowTime = true;
    @Setting(category = "HUD", section = "Main HUD", order = 40, label = "Show profit/h", desc = "The Profit/h line.", parent = "hudEnabled")
    public boolean hudShowRate = true;
    @Setting(category = "HUD", section = "Main HUD", order = 50, label = "Show item list", desc = "The Items section.", parent = "hudEnabled")
    public boolean hudShowItems = true;
    @Setting(category = "HUD", section = "Main HUD", order = 90, label = "Show spent / costs", desc = "Spent, costs and copper lines.", parent = "hudEnabled")
    public boolean hudShowCosts = true;
    @Setting(category = "HUD", section = "Main HUD", order = 110, label = "Show reset countdown", desc = "The 'Paused - resets in' line when you're idle.", parent = "hudEnabled")
    public boolean hudShowCountdown = true;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 180, label = "Rare drops shown", desc = "How many rare drops the HUD lists.", min = 0, max = 20, parent = "showRareDrops")
    public int hudMaxRare = 4;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 200, label = "Shards shown", desc = "How many shards the HUD lists.", min = 0, max = 20, parent = "showShards")
    public int hudMaxShards = 4;
    @Setting(category = "Farming", section = "Farming HUD", order = 20, label = "Show fortune", desc = "Farming Fortune line (needs the Stats tab widget).", parent = "showFarmingHud")
    public boolean farmShowFortune = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 30, label = "Show crop and blocks/s", desc = "The 'Farming: Wheat (1,234 broken, 19.5 BPS)' line.", parent = "showFarmingHud")
    public boolean farmShowBps = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 60, label = "Show pests", desc = "Pests killed by type.", parent = "showFarmingHud")
    public boolean farmShowPests = true;
    @Setting(category = "Farming", section = "Pests", order = 90, label = "Highlight pests", desc = "Fire particles on every pest you can see, plus a list with arrows, distances and pest plots.")
    public boolean pestHighlight = true;
    @Setting(category = "Farming", section = "Pests", order = 130, label = "Trail to pests", desc = "A short particle trail from you toward each pest.", parent = "pestHighlight")
    public boolean pestTrail = false;
    @Setting(category = "Farming", section = "Pests", order = 110, label = "Pest particle box", desc = "A box of colored particles around each pest (on top of the glowing outline).", parent = "pestHighlight")
    public boolean pestBox = true;
    @Setting(category = "Farming", section = "Pests", order = 100, label = "Pest glow outline", desc = "Bright outline around pests in the box color, visible through walls (Minecraft's glowing effect).", parent = "pestHighlight")
    public boolean pestGlow = true;
    @Setting(category = "Farming", section = "Pests", order = 120, label = "Pest box color", desc = "Color of the box around pests, as a hex number, e.g. FF3030 (red), FFFF00 (yellow), 00FFFF (cyan).", options = {"00FF00", "FFFF00", "00FFFF", "FF00FF", "FF3030", "FFFFFF"}, parent = "pestHighlight")
    public String pestBoxColorHex = "00FF00";
    @Setting(category = "Other", label = "pest color migrated", desc = "internal", hidden = true)
    public boolean pestColorMigrated = false;
    @Setting(category = "Farming", section = "Garden helpers", order = 140, label = "Greenhouse guide", desc = "When a mutation is planned (/greenhouse): markers on the plot you look at and a layout grid on the HUD.")
    public boolean greenhouseGuide = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 70, label = "Show visitors", desc = "Garden visitors accepted.", parent = "showFarmingHud")
    public boolean farmShowVisitors = true;
    @Setting(category = "Farming", section = "Alerts", order = 170, label = "Jacob's contest alert", desc = "Big alert (and a chat line) 1 minute before a Jacob's contest starts, naming the crops.", alert = true)
    public boolean contestAlert = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 80, label = "Contest standing on HUD", desc = "During a Jacob's contest: your collected amount and medal bracket (from the sidebar) on the Farming HUD.", parent = "showFarmingHud")
    public boolean showContestStanding = true;
    @Setting(category = "Farming", section = "Garden helpers", order = 150, label = "Visitor shopping list", desc = "Opening a visitor lists what they want with a [Bazaar] link per item (opens it and copies the amount you still need).")
    public boolean visitorBazaar = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 20, label = "Show mining stats", desc = "Mining Speed / Fortune lines (needs the Stats tab widget).", parent = "showMiningHud")
    public boolean mineShowStats = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 30, label = "Show powder", desc = "Powder gained and per hour.", parent = "showMiningHud")
    public boolean mineShowPowder = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 40, label = "Show pristine", desc = "Pristine procs (Crystal Hollows, Mineshafts).", parent = "showMiningHud")
    public boolean mineShowPristine = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 50, label = "Show block breakdown", desc = "Which blocks you've mined, by type.", parent = "showMiningHud")
    public boolean mineShowBlocks = true;
    @Setting(category = "Mining", section = "Glow", order = 290, label = "Glow: Ghosts", desc = "Ghosts in the Mist get a white outline (they're almost invisible).")
    public boolean glowGhosts = true;
    @Setting(category = "Mining", section = "Glow", order = 300, label = "Glow: commission mobs", desc = "Goblins, Glacite / Ice Walkers, Treasure Hoarders, Star Sentries get a yellow outline.")
    public boolean glowCommission = true;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 110, label = "Treasure chest helper", desc = "Crystal Hollows: box + despawn countdown on chests you uncover, chests opened per minute, Double Powder, and chest loot.")
    public boolean powderChests = true;
    @Setting(category = "Mining", section = "Alerts", order = 240, label = "Mineshaft alert", desc = "Big alert when you find a Glacite Mineshaft, with a [Share with party] button.", alert = true)
    public boolean mineshaftAlert = true;
    @Setting(category = "Mining", section = "Alerts", order = 260, label = "Pickaxe ability ready alert", desc = "Pings (and shows big text) when your pickaxe ability (Mining Speed Boost, Pickobulus, Maniac Miner...) is ready again. Read from Hypixel's own chat messages; it never uses the ability for you.", alert = true)
    public boolean abilityAlert = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 90, label = "Pickaxe ability on the HUD", desc = "A line on the Mining HUD showing that the ability was used and how long ago, until it's ready again.", parent = "showMiningHud")
    public boolean abilityHud = true;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 100, label = "Crystal Hollows waypoints", desc = "Saves a waypoint when you reach Jungle Temple, Goblin Queen's Den, Mines of Divan, Precursor City... (per lobby), with a share button.")
    public boolean chWaypoints = true;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 120, label = "Treasure chest lifetime (s)", desc = "How long an uncovered chest stays before it disappears (about 60 s).", min = 10, max = 300, parent = "powderChests")
    public int chestLifetimeSeconds = 60;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 130, label = "Lockpick helper", desc = "Marks the lockpick spot on treasure chests with a green square on your screen and shows which way to move your aim. Display only: you aim yourself.")
    public boolean lockpickHelper = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 80, label = "Best ore on Mining HUD", desc = "Show the 'Best now' ore line on the Mining HUD. The full list is always in /miningprofit suggest.", parent = "showMiningHud")
    public boolean mineShowSuggestion = false;
    @Setting(category = "Mining", section = "Crystal Hollows & helpers", order = 140, label = "Lockpick aim offset (pixels)", desc = "How far above the particles to mark the spot (the wiki says 1-2 pixels above). 0 = exactly on the particles.", min = -4, max = 6, parent = "lockpickHelper")
    public int lockpickOffset = 1;
    @Setting(category = "Mining", section = "Mining HUD", order = 60, label = "Blocks listed", desc = "How many block types the breakdown shows.", min = 1, max = 20, parent = "mineShowBlocks")
    public int mineBlocksShown = 4;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 20, label = "Show foraging stats", desc = "Sweep / Foraging Fortune lines (needs the Stats tab widget).", parent = "showForagingHud")
    public boolean forShowStats = true;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 30, label = "Show tree gifts", desc = "Tree Gifts and trees per minute.", parent = "showForagingHud")
    public boolean forShowTrees = true;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 40, label = "Show Forest Whispers", desc = "Forest Whispers gained and per hour.", parent = "showForagingHud")
    public boolean forShowWhispers = true;
    @Setting(category = "Foraging", section = "Counting", order = 50, label = "Count any log", desc = "Count log blocks the mod doesn't know by name (new woods).")
    public boolean countAnyLog = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 20, label = "Show fishing stats", desc = "Fishing Speed, Sea Creature Chance... (needs the Stats tab widget).", parent = "showFishingHud")
    public boolean fishShowStats = true;
    @Setting(category = "Fishing", section = "Helpers", order = 90, label = "Glow: rare sea creatures", desc = "Thunder, Lord Jawbus, Sea Emperor, Water Hydra, Yeti... get a cyan outline.")
    public boolean glowSeaCreatures = true;
    @Setting(category = "Fishing", section = "Alerts", order = 100, label = "Bite alert", desc = "A ding and a big \"REEL IN!\" on the HUD when Hypixel shows !!! over your bobber.", alert = true)
    public boolean fishingAlert = true;
    @Setting(category = "Fishing", section = "Helpers", order = 70, label = "Bobber timer & sea creatures", desc = "Bobber timer, your living sea creatures (count + oldest) and a share button for rare ones. The alert for rare ones is \"Rare sea creature alert\".")
    public boolean fishingExtras = true;
    @Setting(category = "Fishing", section = "Helpers", order = 80, label = "Sea creature warning at", desc = "Ding when this many of your sea creatures are alive (0 = off).", min = 0, max = 60, parent = "fishingExtras")
    public int fishingCreatureCap = 10;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 30, label = "Show location", desc = "Where you're fishing.", parent = "showFishingHud")
    public boolean fishShowLocation = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 40, label = "Show trophy fish", desc = "Trophy fish by type.", parent = "showFishingHud")
    public boolean fishShowTrophies = true;
    @Setting(category = "Fishing", section = "Tracking", order = 50, label = "Count coin catches", desc = "Coins from GOOD/GREAT CATCH count as profit.")
    public boolean fishCountCoins = true;
    @Setting(category = "Fishing", section = "Tracking", order = 60, label = "Keep fishing active (s)", desc = "Hits within this many seconds of using your rod count as fishing (sea creatures).", min = 5, max = 600)
    public int fishingActiveSeconds = 60;
    @Setting(category = "Combat", section = "When a session starts", order = 90, label = "Hits to start combat", desc = "How many mob hits (within 30 s) start a Combat session. Players, NPCs and armor stands never count.", min = 1, max = 100)
    public int combatStartHits = 5;
    @Setting(category = "Combat", section = "When a session starts", order = 100, label = "Only start on slayer quests", desc = "Combat sessions only start when you start a slayer quest.")
    public boolean combatNeedsSlayer = false;
    @Setting(category = "Combat", section = "Combat HUD", order = 20, label = "Show slayer bosses", desc = "Bosses killed and per hour.", parent = "showCombatHud")
    public boolean combatShowBosses = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 30, label = "Show kills", desc = "Kills, kills per hour and the mobs you killed most.", parent = "showCombatHud")
    public boolean combatShowKills = true;
    @Setting(category = "Combat", section = "Glow", order = 150, label = "Glow: your slayer boss", desc = "Your own slayer boss (not other players') gets a red outline.")
    public boolean glowSlayer = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 60, label = "Boss health on HUD", desc = "Your slayer boss's health and time left on the Combat HUD.", parent = "showCombatHud")
    public boolean showBossHealth = true;
    @Setting(category = "Combat", section = "Glow", order = 160, label = "Glow: Zealots", desc = "Zealots purple, Special Zealots pink.")
    public boolean glowZealots = true;
    @Setting(category = "Combat", section = "Slayer", order = 120, label = "Slayer boss kill time", desc = "Times how long the boss fight took (from when the boss appears to the kill) and keeps your best and average per slayer. Shown on the Combat HUD and in chat.")
    public boolean slayerTime = true;
    @Setting(category = "Combat", section = "Slayer", order = 130, label = "Kill time in chat", desc = "Prints the boss kill time in chat when you kill a slayer boss (with your best and average).", parent = "slayerTime")
    public boolean slayerTimeChat = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 70, label = "Grind HUDs", desc = "When most of your kills are one farmable mob (Zealots, Ghosts, Endermen...), the Combat HUD turns into a grind HUD with drop odds and 'since last drop'.", parent = "showCombatHud")
    public boolean grindHuds = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 80, label = "Grind kills share %", desc = "How much of your kills must be one grind mob before the grind HUD appears.", min = 30, max = 100, parent = "grindHuds")
    public int grindShare = 60;
    @Setting(category = "Combat", section = "Combat HUD", order = 40, label = "Show profit per kill", desc = "Average coins per kill.", parent = "showCombatHud")
    public boolean combatShowPerKill = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 50, label = "Show Magic Find", desc = "Magic Find from the Stats tab widget.", parent = "showCombatHud")
    public boolean combatShowMagicFind = false;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 20, label = "Show runs", desc = "Runs, average time and runs per hour.", parent = "showDungeonsHud")
    public boolean dungShowRuns = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 30, label = "Show last score", desc = "Your last run's score.", parent = "showDungeonsHud")
    public boolean dungShowScore = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 70, label = "Secrets listed", desc = "How many nearby secrets the finder lists.", min = 1, max = 15, parent = "secretFinder")
    public int secretsShown = 5;
    @Setting(category = "Dungeons", section = "Secrets", order = 80, label = "Secret scan radius", desc = "How far (blocks) the finder looks around you.", min = 4, max = 32, parent = "secretFinder")
    public int secretRadius = 14;
    @Setting(category = "Dungeons", section = "Secrets", order = 90, label = "Find chests", desc = "Include chests.", parent = "secretFinder")
    public boolean secretChests = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 100, label = "Find levers", desc = "Include levers.", parent = "secretFinder")
    public boolean secretLevers = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 110, label = "Find essence", desc = "Include Wither Essence skulls.", parent = "secretFinder")
    public boolean secretEssence = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 120, label = "Find items", desc = "Include secret items on the floor.", parent = "secretFinder")
    public boolean secretItems = true;
    @Setting(category = "Dungeons", section = "Secrets", order = 130, label = "Find bats", desc = "Include bats.", parent = "secretFinder")
    public boolean secretBats = true;
    @Setting(category = "Combat", section = "Kuudra", order = 180, label = "Show runs", desc = "Runs and runs per hour.", parent = "showKuudraHud")
    public boolean kuudraShowRuns = true;
    @Setting(category = "Combat", section = "Kuudra", order = 185, label = "Kuudra key cost", desc = "What one Kuudra key costs you, charged when you open a Paid Chest. 0 = work it out from the key's recipe (its coins, the materials and 2 Corrupted Nether Stars at today's prices).", min = 0, max = 10000000000.0, parent = "showKuudraHud")
    public double kuudraKeyCost = 0;
    @Setting(category = "Combat", section = "Diana", order = 200, label = "Show burrows", desc = "Burrows dug and per hour.", parent = "showDianaHud")
    public boolean dianaShowBurrows = true;
    @Setting(category = "Market", section = "Your orders", order = 20, label = "Orders on HUD", desc = "How many orders the orders panel lists.", min = 1, max = 30, parent = "bazaarHud")
    public int bzHudMaxOrders = 6;
    @Setting(category = "Market", section = "Your orders", order = 30, label = "Show auctions on HUD", desc = "Your Auction House listings (sold / expired / undercut by a cheaper BIN) and bids (top / outbid / won) on the flip panel. Open /ah → Manage Auctions and Your Bids once to read existing ones.")
    public boolean auctionHud = true;
    @Setting(category = "Market", section = "Your orders", order = 40, label = "Auctions on HUD", desc = "How many listings and bids the panel lists.", min = 1, max = 20, parent = "auctionHud")
    public int ahHudMax = 5;
    @Setting(category = "Market", section = "Your orders", order = 50, label = "Show today's flip profit", desc = "Flip profit today on the orders panel.")
    public boolean bzShowToday = true;
    @Setting(category = "Market", section = "Your orders", order = 55, label = "Count AH listing fee", desc = "Auction flips subtract the fee Hypixel charged when you listed the item (BIN: 1% under 10M, 2% up to 100M, 2.5% above; normal auctions 5%). An estimate from the price: the small duration fee isn't included. Off = only the claim tax is taken off.")
    public boolean ahFee = true;
    @Setting(category = "Market", section = "Alerts", order = 290, label = "Ding when an order fills", desc = "Sound when a buy order or sell offer is filled.", alert = true)
    public boolean bzFillSound = true;
    @Setting(category = "HUD", section = "Look & layout", order = 240, label = "Separate small panels", desc = "Show the secret finder, Jacob's contest and 'Best now' as their own panels you can place anywhere (/profit gui).", parent = "hudEnabled")
    public boolean separatePanels = false;
    @Setting(category = "HUD", section = "Look & layout", order = 250, label = "Position per activity", desc = "The main HUD remembers a different spot for each activity (Farming, Mining...).", parent = "hudEnabled")
    public boolean perActivityPositions = false;
    @Setting(category = "Advanced", section = "Setup", order = 180, label = "First-time setup done", desc = "Turn off to see the setup check again next time you join SkyBlock (or use /profit setup).")
    public boolean setupDone = false;
    @Setting(category = "Advanced", section = "Setup", order = 170, label = "Warn about duplicate features", desc = "Tells you when SkyHanni, Skyblocker or Odin already do something this mod does, so you can turn one off.")
    public boolean warnDuplicates = true;
    @Setting(category = "General", section = "Updates", order = 230, label = "Check for new builds", desc = "Tells you when your GitHub repo has a newer successful build than the one you're running.")
    public boolean updateCheck = true;
    @Setting(category = "General", section = "Updates", order = 240, label = "Tell me what's new", desc = "One line in chat the first time you join after the mod was updated. /skyassist news shows the notes any time.")
    public boolean showNews = true;
    /** The version whose notes were last announced (not a setting). */
    @Setting(category = "Other", label = "Last seen version", desc = "internal", hidden = true)
    public String lastSeenVersion = "";
    @Setting(category = "Advanced", section = "Data sources", order = 160, label = "GitHub repo", desc = "owner/name of the repo that builds this mod, e.g. Kalapath/skyassist.")
    public String updateRepo = "Kalapath/skyassist";
    @Setting(category = "Items", section = "Enchants", order = 180, label = "Color enchantments", desc = "Colors enchantments in tooltips by level: perfect (max), great (above table max), good (table max), lower.")
    public boolean enchantColors = true;
    @Setting(category = "Items", section = "Enchants", order = 190, label = "Perfect enchant color", desc = "Enchantments at their absolute maximum level.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"}, parent = "enchantColors")
    public String enchantPerfectColor = "gold";
    @Setting(category = "Items", section = "Enchants", order = 200, label = "Great enchant color", desc = "Above what the enchanting table gives, but not max yet.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"}, parent = "enchantColors")
    public String enchantGreatColor = "light purple";
    @Setting(category = "Items", section = "Enchants", order = 210, label = "Good enchant color", desc = "Exactly the enchanting table maximum.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"}, parent = "enchantColors")
    public String enchantGoodColor = "blue";
    @Setting(category = "Items", section = "Enchants", order = 220, label = "Low enchant color", desc = "Below the enchanting table maximum.", options = {"gold", "rainbow", "red", "light purple", "dark purple", "aqua", "green", "yellow", "blue", "gray", "dark gray", "white"}, parent = "enchantColors")
    public String enchantPoorColor = "gray";
    @Setting(category = "Items", section = "Enchants", order = 230, label = "Ultimate enchants stay pink", desc = "Ultimate enchantments keep Hypixel's bold pink at every level (the ✦ still shows when maxed). Off = colored by level like the others.", parent = "enchantColors")
    public boolean ultimateKeepPink = true;
    @Setting(category = "Items", section = "Enchants", order = 240, label = "Mark maxed with ✦", desc = "Adds a ✦ after enchantments at their maximum level.", parent = "enchantColors")
    public boolean enchantMaxTag = true;
    @Setting(category = "Advanced", section = "Data sources", order = 120, label = "Enchant data URL", desc = "Where max levels come from (public SkyKings data). Empty = off.")
    public String enchantDataUrl = "https://raw.githubusercontent.com/SkyKings-Guild/Bot-Data/main/skyblock/enchants.json";
    @Setting(category = "Items", section = "Tooltips", order = 160, label = "Scroll long tooltips", desc = "Item descriptions taller than your screen can be scrolled with the mouse wheel.")
    public boolean tooltipScroll = true;
    @Setting(category = "Items", section = "Inventory", order = 50, label = "Inventory search box", desc = "A search box under every inventory and menu: matching items are highlighted, the rest dimmed.")
    public boolean inventorySearch = true;
    @Setting(category = "Items", section = "Inventory", order = 70, label = "Storage overview", desc = "Remembers your Ender Chest pages and backpacks when you open them; /itemsearch lists everything with where it is.")
    public boolean storageOverview = true;
    @Setting(category = "Items", section = "Inventory", order = 10, label = "Rarity colors on items", desc = "Colors each item's slot by its rarity (white, green, blue, purple, gold, pink...) in menus.")
    public boolean rarityBackground = true;
    @Setting(category = "Items", section = "Inventory", order = 40, label = "Labels on items", desc = "Pet level, minion tier and enchanted book level shown on the item in menus.")
    public boolean itemLabels = true;
    @Setting(category = "Items", section = "Inventory", order = 30, label = "Rarity colors on hotbar", desc = "The same rarity colors on your hotbar.", parent = "rarityBackground")
    public boolean rarityHotbar = true;
    @Setting(category = "Items", section = "Inventory", order = 20, label = "Rarity color strength", desc = "0 = invisible, 255 = solid.", min = 0, max = 255, parent = "rarityBackground")
    public int rarityOpacity = 170;
    @Setting(category = "Items", section = "Inventory", order = 60, label = "Search descriptions too", desc = "The search box also matches text in item descriptions (e.g. \"Farming Fortune\").", parent = "inventorySearch")
    public boolean inventorySearchLore = true;
    @Setting(category = "Items", section = "Tooltips", order = 170, label = "Tooltip scroll speed", desc = "Lines moved per mouse-wheel notch.", min = 1, max = 20, parent = "tooltipScroll")
    public int tooltipScrollSpeed = 3;
    @Setting(category = "Items", section = "Tooltips", order = 150, label = "Upgrade value in tooltips", desc = "Adds a line with what the upgrades on an item are worth at Bazaar prices: enchantments, Hot Potato Books, Fuming Potato Books, Recombobulator and more. A rough extra, not a full price (reforge, stars and gems aren't included).")
    public boolean upgradeValue = true;
    @Setting(category = "Items", section = "Tooltips", order = 140, label = "Craft cost in tooltips", desc = "Shows what an item costs to craft from bought ingredients, and whether crafting or buying is cheaper.")
    public boolean craftCost = true;
    @Setting(category = "Advanced", section = "Data sources", order = 110, label = "Recipe data URL", desc = "Where recipes come from (the public NEU item repository, downloaded weekly). Empty = off.")
    public String neuRepoUrl = "https://github.com/NotEnoughUpdates/NotEnoughUpdates-REPO/archive/refs/heads/master.zip";
    @Setting(category = "Items", section = "Inventory", order = 80, label = "Calculator in signs", desc = "In Bazaar / Auction amount signs, type a sum ending in = (e.g. 64x8= or 10m/3=) and it becomes the result.")
    public boolean signCalculator = true;
    @Setting(category = "Items", section = "Inventory", order = 90, label = "Keep cursor between menus", desc = "When Hypixel closes one menu and opens the next (SkyBlock Menu → Pets and so on), your mouse pointer stays where it was instead of jumping to the middle of the screen.")
    public boolean keepCursor = true;
    @Setting(category = "Items", section = "Inventory", order = 100, label = "Quick-command buttons", desc = "A column of small buttons beside your inventory; each runs a command when YOU click it (Pets, Wardrobe, Ender Chest...). Nothing is sent until you click.")
    public boolean invButtons = false;
    @Setting(category = "Items", section = "Inventory", order = 110, label = "Buttons shown in", desc = "player inventory = only the plain inventory (E). all menus = every chest-style menu too.", options = {"player inventory", "all menus"}, parent = "invButtons")
    public String invButtonsWhere = "player inventory";
    @Setting(category = "Items", section = "Inventory", order = 120, label = "Button list", desc = "Name=/command pairs separated by ; for example Pets=/pets; Wardrobe=/wardrobe. Up to 8 are shown.", parent = "invButtons")
    public String invButtonList = "Pets=/pets; Wardrobe=/wardrobe; Ender Chest=/ec; Storage=/storage; Skills=/skills; SB Menu=/sbmenu; Bazaar=/bz; Auctions=/ah";
    @Setting(category = "Dungeons", section = "Puzzles", order = 150, label = "Three Weirdos solver", desc = "Tells you which chest to open.")
    public boolean solveWeirdos = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 160, label = "Blaze puzzle helper", desc = "Shows the lowest and highest health blaze with arrows.")
    public boolean solveBlaze = true;
    @Setting(category = "Dungeons", section = "Terminals", order = 220, label = "Terminal solvers", desc = "Floor 7 / Master 7 terminals: highlights what to click (you still click).")
    public boolean solveTerminals = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 140, label = "Puzzle markers", desc = "Show puzzle answers in the world with particles only you can see.")
    public boolean puzzleParticles = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 170, label = "Ice Fill solver", desc = "Green path over every ice tile, from where you stand.")
    public boolean solveIceFill = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 180, label = "Creeper Beams solver", desc = "Marks which sea lanterns to connect.")
    public boolean solveCreeper = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 190, label = "Teleport Maze helper", desc = "After each teleport, marks the pad most in the direction you're facing (toward the exit).")
    public boolean solveTeleport = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 200, label = "Tic Tac Toe solver", desc = "Marks the best button to press.")
    public boolean solveTicTacToe = true;
    @Setting(category = "Dungeons", section = "Puzzles", order = 210, label = "Quiz solver", desc = "Tells you the right answer for Ouro the Omniscient.")
    public boolean solveQuiz = true;
    @Setting(category = "Advanced", section = "Data sources", order = 130, label = "Quiz answers URL", desc = "Maintained list of quiz answers (Skytils data). Empty = off.")
    public String quizDataUrl = "https://raw.githubusercontent.com/Skytils/SkytilsMod-Data/main/solvers/oruotrivia.json";
    @Setting(category = "Dungeons", section = "Terminals", order = 230, label = "Melody terminal helper", desc = "Floor 7: turns the button green when it's time to click.")
    public boolean solveMelody = true;
    @Setting(category = "Items", section = "Talismans", order = 260, label = "Talismans listed", desc = "How many accessories /talismans shows.", min = 1, max = 50)
    public int talismanCount = 10;
    @Setting(category = "Items", section = "Talismans", order = 270, label = "Include crafting", desc = "Also consider crafting an accessory (and upgrading ones you own) when it's cheaper than the Auction House.")
    public boolean talismanUseCraft = true;
    @Setting(category = "Items", section = "Talismans", order = 280, label = "Max price", desc = "Skip accessories costing more than this. 0 = no limit.", min = 0, max = 1e13)
    public double talismanMaxPrice = 0;
    @Setting(category = "Chat & controls", section = "Chat filter", order = 10, label = "Hide sack messages", desc = "Hides the \"[Sacks] +X items\" lines (they're still counted).")
    public boolean chatHideSacks = false;
    @Setting(category = "Chat & controls", section = "Chat filter", order = 20, label = "Hide ability cooldown", desc = "Hides \"This ability is on cooldown\".")
    public boolean chatHideCooldown = true;
    @Setting(category = "Chat & controls", section = "Chat filter", order = 30, label = "Hide \"blocks in the way\"", desc = "Hides \"There are blocks in the way!\" (teleport items).")
    public boolean chatHideBlocksInWay = true;
    @Setting(category = "Chat & controls", section = "Chat filter", order = 40, label = "Hide visitor chatter", desc = "Hides [NPC] lines while in the Garden (visitor offers are still read).")
    public boolean chatHideVisitorChat = false;
    @Setting(category = "Chat & controls", section = "Chat filter", order = 50, label = "Hide Watchdog announcements", desc = "Hides Watchdog / staff ban announcements.")
    public boolean chatHideWatchdog = true;
    @Setting(category = "Chat & controls", section = "Chat filter", order = 60, label = "Hide messages containing", desc = "Comma separated words or phrases; any chat line containing one is hidden.")
    public java.util.List<String> chatHideContaining = new java.util.ArrayList<>();
    @Setting(category = "Chat & controls", section = "Sounds", order = 70, label = "Mute explosions", desc = "Mutes explosion sounds sent by the server.")
    public boolean muteExplosions = false;
    @Setting(category = "Chat & controls", section = "Sounds", order = 80, label = "Muted sounds", desc = "Comma separated sound names (or parts of names) to mute. Turn on 'Show sound names' to find them.")
    public java.util.List<String> mutedSounds = new java.util.ArrayList<>();
    @Setting(category = "Chat & controls", section = "Sounds", order = 90, label = "Show sound names", desc = "Prints each new sound's name in chat once, so you can add it to Muted sounds. Turn off after.")
    public boolean logSounds = false;
    @Setting(category = "General", section = "Party", order = 80, label = "Waypoints from party chat", desc = "Coordinates your party posts in chat (x: y: z: or three numbers) become a light beam + HUD arrow.")
    public boolean partyWaypoints = true;
    @Setting(category = "General", section = "Party", order = 90, label = "Party waypoint time (s)", desc = "How long a waypoint from party chat stays.", min = 10, max = 1800, parent = "partyWaypoints")
    public int waypointSeconds = 120;
    @Setting(category = "General", section = "Alerts", order = 100, label = "Big on-screen alerts", desc = "Alerts (Scatha, rare drops, pests, price alerts, pickaxe ability, full inventory...) also show as large text in the middle of the screen for a few seconds, besides the chat line and ding.", alert = true)
    public boolean alertScreen = true;
    @Setting(category = "General", section = "Alerts", order = 110, label = "Ding with big alerts", desc = "A short ding together with the big alerts. Off = silent big text.", alert = true)
    public boolean alertSound = true;
    @Setting(category = "General", section = "Alerts", order = 120, label = "Inventory full alert", desc = "Big alert once when your inventory runs out of free slots (so loot doesn't get lost). Only while you're in SkyBlock and no menu is open.", alert = true)
    public boolean invFullWarn = false;
    @Setting(category = "General", section = "Alerts", order = 130, label = "Free slots to warn at", desc = "The inventory warning shows when you have this many free slots or fewer (0 = only when completely full).", min = 0, max = 20, parent = "invFullWarn", alert = true)
    public int invFullSlots = 2;
    @Setting(category = "General", section = "Performance & look", order = 200, label = "Disable swimming pose", desc = "Stay upright in water instead of the swimming / crawling pose (your player only). Careful: this switches off your client's swimming state, which also changes your hitbox and how you move in water, and Hypixel's mod rules don't allow changing player movement. Leave it off if you want to be safe.")
    public boolean disableSwimming = false;
    @Setting(category = "General", section = "Performance & look", order = 210, label = "Slow swing", desc = "Your arm swing animation is slower, like the old 1.8 swing (visual only).")
    public boolean slowSwing = false;
    @Setting(category = "General", section = "Performance & look", order = 220, label = "Swing length (ticks)", desc = "How long one swing takes with Slow swing on. Vanilla is 6; 10-14 looks like 1.8.", min = 2, max = 40, parent = "slowSwing")
    public int swingDuration = 12;
    @Setting(category = "Items", section = "Enchants", order = 250, label = "Rainbow maxed enchants", desc = "Maxed enchantments in tooltips are rainbow-colored (sets the Perfect enchant color).", parent = "enchantColors")
    public boolean rainbowMaxed = false;
    @Setting(category = "Timers", section = "Panel", order = 10, label = "Timers panel", desc = "A separate HUD panel with event timers (move it with /profit gui).")
    public boolean timersPanel = true;
    @Setting(category = "Timers", section = "Events", order = 20, label = "Dark Auction", desc = "Every hour at :55.")
    public boolean timerDarkAuction = true;
    @Setting(category = "Timers", section = "Events", order = 30, label = "Mining events", desc = "Current Dwarven Mines and Crystal Hollows events with time left, from anywhere (shared service), or your tab list as a backup.")
    public boolean timerMiningEvent = true;
    @Setting(category = "Advanced", section = "Data sources", order = 150, label = "Mining events source", desc = "Shared mining-event service (Soopy's, also used by SkyHanni's Mining Event Tracker): shows Dwarven Mines / Crystal Hollows events from anywhere. Empty = only your own tab list.")
    public String miningEventsUrl = "https://api.soopy.dev/skyblock/chevents/get";
    @Setting(category = "Timers", section = "Events", order = 40, label = "Cult of the Fallen Star", desc = "Days 7, 14, 21 and 28 of every SkyBlock month, 00:00-06:00.")
    public boolean timerCult = true;
    @Setting(category = "Timers", section = "Events", order = 50, label = "Spooky Festival", desc = "Autumn 29-31.")
    public boolean timerSpooky = true;
    @Setting(category = "Timers", section = "Events", order = 60, label = "Traveling Zoo", desc = "Early Summer 1-3 and Early Winter 1-3.")
    public boolean timerZoo = false;
    @Setting(category = "Timers", section = "Events", order = 70, label = "Season of Jerry", desc = "Late Winter 24-26.")
    public boolean timerJerry = false;
    @Setting(category = "Timers", section = "Events", order = 80, label = "New Year", desc = "Late Winter 29-31.")
    public boolean timerNewYear = false;
    @Setting(category = "Timers", section = "Events", order = 90, label = "Hoppity's Hunt", desc = "All of Spring.")
    public boolean timerHoppity = false;
    @Setting(category = "Timers", section = "Events", order = 100, label = "Jacob's contest", desc = "Next contest and its crops.")
    public boolean timerJacob = true;
    @Setting(category = "Timers", section = "World bosses", order = 130, label = "Boss: Broodmother", desc = "Spider's Den: Dormant / Soon / Imminent / Alive (from the tab list), remembered when you leave.")
    public boolean bossBroodmother = true;
    @Setting(category = "Timers", section = "World bosses", order = 140, label = "Boss: Endstone Protector", desc = "The End: the Protector's stage (from the tab list).")
    public boolean bossProtector = true;
    @Setting(category = "Timers", section = "World bosses", order = 150, label = "Boss: Ender Dragon", desc = "Dragon's Nest: dragon / eyes status (from the tab list).")
    public boolean bossDragon = false;
    @Setting(category = "Timers", section = "World bosses", order = 160, label = "Boss: Arachne", desc = "Spider's Den: Arachne status, if Hypixel shows it in the tab list.")
    public boolean bossArachne = false;
    @Setting(category = "Timers", section = "World bosses", order = 170, label = "Boss: Kuudra", desc = "Kuudra status, if shown in the tab list.")
    public boolean bossKuudra = false;
    @Setting(category = "Timers", section = "World bosses", order = 180, label = "Boss: Vanquisher", desc = "Crimson Isle: Vanquisher status, if shown in the tab list.")
    public boolean bossVanquisher = false;
    @Setting(category = "Timers", section = "World bosses", order = 190, label = "Boss: Golden Goblin", desc = "Dwarven Mines / Crystal Hollows: Golden Goblin status, if shown in the tab list.")
    public boolean bossGoldenGoblin = false;
    @Setting(category = "Timers", section = "World bosses", order = 200, label = "Extra tab lines to watch", desc = "Comma separated starts of tab-list lines to show and remember, e.g. \"Matriarch\" or \"Bal\". Shows the rest of that line.")
    public java.util.List<String> timerExtraTabLines = new java.util.ArrayList<>();
    @Setting(category = "Chat & controls", section = "Macro keys (set keys in Controls)", order = 100, label = "Macro 1 command", desc = "What Macro 1 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro1 = "/warp garden";
    @Setting(category = "Chat & controls", section = "Macro keys (set keys in Controls)", order = 110, label = "Macro 2 command", desc = "What Macro 2 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro2 = "/bz";
    @Setting(category = "Chat & controls", section = "Macro keys (set keys in Controls)", order = 120, label = "Macro 3 command", desc = "What Macro 3 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro3 = "/pets";
    @Setting(category = "Chat & controls", section = "Macro keys (set keys in Controls)", order = 130, label = "Macro 4 command", desc = "What Macro 4 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro4 = "/wardrobe";
    @Setting(category = "Chat & controls", section = "Macro keys (set keys in Controls)", order = 140, label = "Macro 5 command", desc = "What Macro 5 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro5 = "/storage";
    @Setting(category = "Chat & controls", section = "Macro keys (set keys in Controls)", order = 150, label = "Macro 6 command", desc = "What Macro 6 runs. Set its key in Options → Controls → Key Binds → SkyAssist. Commands start with /, anything else is sent as chat.")
    public String macro6 = "/warp hub";
    /** Minutes without activity before a session resets. */
    @Setting(category = "General", section = "Sessions", order = 30, label = "Reset after (minutes)", desc = "Minutes without activity before a session ends and is saved to history.", min = 1, max = 600)
    public int resetMinutes = 15;
    /**
     * "instasell" = Bazaar instant-sell, "sellorder" = Bazaar sell offer,
     * "npc" = NPC sell price, "best" = whichever of instasell / NPC pays more.
     */
    @Setting(category = "General", section = "Prices & numbers", order = 60, label = "Price source", desc = "best = Bazaar instasell or NPC, whichever pays more. instasell / sellorder = Bazaar. npc = NPC sell price.", options = {"best", "instasell", "sellorder", "npc"})
    public String priceMode = "best";
    /** The session timer pauses after this many seconds without activity (AFK doesn't ruin your /h). */
    @Setting(category = "General", section = "Sessions", order = 40, label = "Pause timer after (seconds)", desc = "The session clock pauses after this long without activity, so AFK time doesn't lower profit/h.", min = 5, max = 600)
    public int pauseSeconds = 30;
    /** On the HUD, items worth less than this (in total) are grouped into one "cheap items" line. */
    /** Items you never want counted (e.g. "Hay Bale"). Add with /profit ignore <item> */
    @Setting(category = "Items", section = "Not counted", order = 290, label = "Ignored items", desc = "Comma separated. These never count toward profit. Right-click an item on the HUD (chat open) to add it.")
    public java.util.List<String> ignoredItems = new java.util.ArrayList<>();
    @Setting(category = "HUD", section = "Main HUD", order = 10, label = "Show HUD", desc = "Turn the whole HUD on or off.")
    public boolean hudEnabled = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 10, label = "Farming HUD", desc = "Show the Farming HUD (tracking keeps running when hidden).")
    public boolean showFarmingHud = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 10, label = "Mining HUD", desc = "Show the Mining HUD (tracking keeps running when hidden).")
    public boolean showMiningHud = true;
    @Setting(category = "Foraging", section = "Foraging HUD", order = 10, label = "Foraging HUD", desc = "Show the Foraging HUD (tracking keeps running when hidden).")
    public boolean showForagingHud = true;
    @Setting(category = "Fishing", section = "Fishing HUD", order = 10, label = "Fishing HUD", desc = "Show the Fishing HUD (tracking keeps running when hidden).")
    public boolean showFishingHud = true;
    @Setting(category = "Combat", section = "Combat HUD", order = 10, label = "Combat HUD", desc = "Show the Combat / slayer HUD (tracking keeps running when hidden).")
    public boolean showCombatHud = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 10, label = "Catacombs HUD", desc = "Show the Catacombs HUD (tracking keeps running when hidden).")
    public boolean showDungeonsHud = true;
    @Setting(category = "Combat", section = "Kuudra", order = 170, label = "Kuudra HUD", desc = "Show the Kuudra HUD (tracking keeps running when hidden).")
    public boolean showKuudraHud = true;
    @Setting(category = "Combat", section = "Diana", order = 190, label = "Diana HUD", desc = "Show the Diana (Mythological Ritual) HUD (tracking keeps running when hidden).")
    public boolean showDianaHud = true;
    @Setting(category = "Combat", section = "Diana", order = 220, label = "Glow: Minos Inquisitor", desc = "Minos Inquisitors get a gold outline.")
    public boolean glowInquisitor = true;
    @Setting(category = "Combat", section = "Diana", order = 210, label = "Burrow finder", desc = "Marks burrows from their particles (green start, red mob, gold treasure) and turns the Ancestral Spade's particle trail into a cyan direction beam.")
    public boolean dianaHelper = true;
    @Setting(category = "Combat", section = "Diana", order = 230, label = "Auto-share Inquisitor", desc = "Post your coordinates in party chat automatically when you dig up a Minos Inquisitor (off = a [Share] button instead).")
    public boolean autoShareInquisitor = false;
    @Setting(category = "HUD", section = "Main HUD", order = 120, label = "Show all-time line", desc = "Adds your all-time profit and profit/h for the activity under the session numbers.", parent = "hudEnabled")
    public boolean hudShowTotal = false;
    @Setting(category = "HUD", section = "Main HUD", order = 150, label = "Show mayor", desc = "Shows the mayor on the HUD of the activity they boost.", parent = "hudEnabled")
    public boolean showMayor = true;
    @Setting(category = "HUD", section = "Main HUD", order = 160, label = "Show skill XP/h", desc = "A line with the skill XP you earn per hour and how long until your next level. Read from the XP numbers Hypixel shows above your hotbar when you gain skill XP.", parent = "hudEnabled")
    public boolean skillXpHud = true;
    @Setting(category = "Items", section = "Tooltips", order = 130, label = "Price tooltips", desc = "Adds Bazaar, lowest BIN and NPC prices to item tooltips.")
    public boolean priceTooltips = true;
    @Setting(category = "Farming", section = "Garden helpers", order = 160, label = "Show Jacob's contests", desc = "Next contest crops and countdown on the Farming HUD.")
    public boolean showContests = true;
    @Setting(category = "Advanced", section = "Data sources", order = 140, label = "Contest data URL", desc = "Where upcoming contests come from (community data; Hypixel doesn't publish them). Empty = off.")
    public String jacobContestsUrl = "https://api.elitebot.dev/contests/at/now";
    @Setting(category = "Farming", section = "Values", order = 200, label = "Copper value (coins)", desc = "Coins each copper from Garden visitors is worth to you. 0 = don't count copper.", min = 0, max = 1e9)
    public double copperValue = 0;
    @Setting(category = "Market", section = "Your orders", order = 70, label = "Warn about odd orders", desc = "Chat warning when an order you place is far from the market price (possible typo).")
    public boolean bzWarnMistakes = true;
    @Setting(category = "Other", label = "HUD X position", desc = "Or drag the HUD with chat open.", min = 0, max = 10000, hidden = true)
    public int hudX = 5;
    @Setting(category = "Other", label = "HUD Y position", desc = "Or drag the HUD with chat open.", min = 0, max = 10000, hidden = true)
    public int hudY = 5;
    @Setting(category = "HUD", section = "Main HUD", order = 60, label = "Items shown", desc = "Up to this many items are listed one by one. If you have more, the cheapest are added up into one line.", min = 1, max = 50, parent = "hudShowItems")
    public int hudMaxItems = 6;
    @Setting(category = "Other", label = "HUD scale", desc = "0.5 to 3.", min = 0.5, max = 3, hidden = true)
    public double hudScale = 1.0;
    @Setting(category = "HUD", section = "Main HUD", order = 70, label = "Item icons", desc = "Small icons next to items you've had in your inventory.", parent = "hudShowItems")
    public boolean hudIcons = true;
    @Setting(category = "Mining", section = "Mining HUD", order = 70, label = "Show commissions", desc = "Commissions on the Mining HUD.", parent = "showMiningHud")
    public boolean showCommissions = true;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 170, label = "Show rare drops", desc = "Rare drops section on the HUD.", parent = "hudEnabled")
    public boolean showRareDrops = true;
    @Setting(category = "HUD", section = "Rare drops & shards", order = 190, label = "Show shards", desc = "Attribute shards line on the HUD.", parent = "hudEnabled")
    public boolean showShards = true;
    /** false = HUD only shows profit lines (no stats, powder, commissions...). Toggle with /profit details */
    @Setting(category = "HUD", section = "Main HUD", order = 130, label = "Show details", desc = "Stats, powder, commissions, BPS... Off = profit lines only.", parent = "hudEnabled")
    public boolean hudDetails = true;
    /** "Best now" line on the farming and mining HUD. */
    @Setting(category = "HUD", section = "Main HUD", order = 140, label = "Show 'Best now' tip", desc = "Best crop / ore to farm right now on the Farming and Mining HUDs.", parent = "hudEnabled")
    public boolean showSuggestion = true;
    @Setting(category = "Farming", section = "Farming HUD", order = 40, label = "Best crop on Farming HUD", desc = "Show the 'Best now' crop line on the Farming HUD. The full list is always in /profit suggest.", parent = "showFarmingHud")
    public boolean farmShowSuggestion = false;
    @Setting(category = "Farming", section = "Farming HUD", order = 50, label = "Show yaw / pitch", desc = "Your exact look direction (yaw and pitch) on the Farming HUD, to line up with crop rows. Display only; it changes nothing.", parent = "showFarmingHud")
    public boolean farmShowAim = false;
    /** Dungeon secret finder on the Catacombs HUD. */
    @Setting(category = "Dungeons", section = "Secrets", order = 60, label = "Dungeon secret finder", desc = "Room counter and nearby secret list on the Catacombs HUD.")
    public boolean secretFinder = true;
    @Setting(category = "Dungeons", section = "Glow", order = 240, label = "Glow: starred mobs", desc = "Starred mobs (the ones that count for clearing a room) get a gold outline through walls.")
    public boolean glowStarred = true;
    @Setting(category = "Dungeons", section = "Glow", order = 250, label = "Glow: Wither / Blood key", desc = "The dropped Wither Key (dark) and Blood Key (red) get an outline.")
    public boolean glowKeys = true;
    @Setting(category = "Dungeons", section = "Glow", order = 260, label = "Glow: bats", desc = "Secret bats get a green outline.")
    public boolean glowBats = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 40, label = "Show run info", desc = "Your secrets, team secrets %, crypts and deaths on the Catacombs HUD.", parent = "showDungeonsHud")
    public boolean dungShowRunInfo = true;
    @Setting(category = "Dungeons", section = "Catacombs HUD", order = 50, label = "Chest profit in chat", desc = "When you open a reward chest or Croesus, show each chest's value, cost and profit.")
    public boolean chestProfit = true;
    @Setting(category = "Advanced", section = "What counts as profit", order = 30, label = "Count items in menus", desc = "Extra menu titles (comma separated) where items you receive count as profit. Dungeon and Kuudra reward chests are recognised on their own, whatever Hypixel calls their windows.")
    public java.util.List<String> countInMenus = new java.util.ArrayList<>(java.util.List.of(
            "Wood Chest", "Gold Chest", "Diamond Chest", "Emerald Chest", "Obsidian Chest", "Bedrock Chest"));
    /** Blocks per second used for farming suggestions until you've farmed for a minute. */
    @Setting(category = "Farming", section = "Values", order = 210, label = "Default blocks/s", desc = "Used for farming suggestions until you've farmed for a minute.", min = 1, max = 40)
    public double defaultBps = 19;
    /** Share of the theoretical mining rate you really get (walking, aiming, abilities...). */
    @Setting(category = "Mining", section = "Values", order = 320, label = "Mining efficiency", desc = "Share of the theoretical mining rate you really get (walking, aiming). 0.6 = 60%.", min = 0.05, max = 1)
    public double miningEfficiency = 0.6;

    // ----- Bazaar flipping (/flips) -----
    @Setting(category = "Market", section = "Flip finder", order = 80, label = "Budget", desc = "Coins you want to put into flips.", min = 0, max = 10000000000000.0)
    public double bzBudget = 10_000_000;
    @Setting(category = "Market", section = "Flip filters", order = 130, label = "Min weekly volume", desc = "Higher = flips fill faster.", min = 0, max = 1000000000000.0)
    public double bzMinVolume = 20_000;      // weekly
    @Setting(category = "Market", section = "Flip filters", order = 140, label = "Min margin %", desc = "After tax.", min = 0, max = 1000)
    public double bzMinMargin = 1;           // % after tax
    @Setting(category = "Market", section = "Flip filters", order = 150, label = "Max item price", desc = "0 = no limit.", min = 0, max = 1000000000000.0)
    public double bzMaxPrice = 0;            // 0 = no limit
    @Setting(category = "Market", section = "Flip finder", order = 100, label = "Bazaar tax %", desc = "Lower with the Bazaar Flipper upgrade.", min = 0, max = 10)
    public double bzTax = 1.25;              // % (lower with the Bazaar Flipper upgrade)
    @Setting(category = "Market", section = "Flip finder", order = 90, label = "Your volume share %", desc = "How much of an item's trading you expect to get (competition estimate).", min = 0, max = 100)
    public double bzShare = 10;              // % of the item's volume you expect to get
    @Setting(category = "Market", section = "Alerts", order = 270, label = "Hot flip alert (coins/h)", desc = "Ping when a new flip beats this. 0 = off.", min = 0, max = 1000000000000.0, alert = true)
    public double bzFlipAlert = 0;           // ping when a flip beats this many coins/h (0 = off)
    @Setting(category = "Market", section = "Flip finder", order = 110, label = "Flips listed", desc = "How many flips /flips shows.", min = 1, max = 50)
    public int bzTop = 10;
    @Setting(category = "Market", section = "Your orders", order = 60, label = "Order check every (s)", desc = "How often prices are checked while you have orders out or price alerts waiting.", min = 10, max = 600)
    public int bzRefreshSeconds = 20;        // price checks while you have orders out
    @Setting(category = "Market", section = "Your orders", order = 10, label = "Show orders on HUD", desc = "Your open bazaar orders under the HUD.")
    public boolean bazaarHud = true;
    @Setting(category = "Market", section = "Alerts", order = 310, label = "Market alert sound", desc = "Ding when you're outbid or undercut, when an order fills (with 'Ding when an order fills') and for auction alerts. Off = the market alerts are silent.", alert = true)
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
    @Setting(category = "Market", section = "Flip finder", order = 120, label = "Safe flips only", desc = "For normal and craft flips: hide volatile (buy/sell gap over 25%), competitive (under 3% margin, lots of orders), slow (under 100 sold/h) and possibly manipulated or inflated ones.")
    public boolean craftFlipSafeOnly = false;
    @Setting(category = "Market", section = "Flip filters", order = 160, label = "Min item price", desc = "Hide flips on items cheaper than this, so you never buy thousands of something. A price (50k) or a % of your budget (0.1% of 50m = 50k). 0 = off.")
    public String flipMinItemPrice = "0";
    @Setting(category = "Market", section = "Flip filters", order = 170, label = "Min profit per flip", desc = "Hide flips whose whole order (the amount you'd buy) makes less than this. Coins (500k) or a % of your budget (1%). 0 = off.")
    public String flipMinProfit = "0";
    @Setting(category = "Market", section = "Craft flips", order = 180, label = "Min profit", desc = "Craft flips (Craft → Bazaar / AH) must make at least this per craft. Coins (10k) or a % of your budget (1%).")
    public String craftMinProfit = "1000";
    @Setting(category = "Market", section = "Craft flips", order = 190, label = "Only recipes I can craft", desc = "Hide craft flips you haven't unlocked yet (collection, slayer or skill level). Your levels are read when you open /collection categories, the slayer menu and /skills, and from level-up messages.")
    public boolean craftOnlyUnlocked = true;
    @Setting(category = "Market", section = "Craft flips", order = 200, label = "Hide when level unknown", desc = "Also hide recipes whose requirement you haven't been seen to meet (e.g. that collection's menu wasn't opened yet). Off = they show with a note.", parent = "craftOnlyUnlocked")
    public boolean craftHideUnknown = false;
    @Setting(category = "Other", label = "flip rules migrated", desc = "internal", hidden = true)
    public boolean flipRulesMigrated = false;
    @Setting(category = "Market", section = "Market signals (/market)", order = 210, label = "Items analysed", desc = "How many of the most traded Bazaar items /market downloads history for (more = slower first load).", min = 10, max = 300)
    public int marketItems = 60;
    @Setting(category = "Market", section = "Market signals (/market)", order = 220, label = "History (days)", desc = "How far back /market looks for mayor terms and events.", min = 30, max = 730)
    public int marketHistoryDays = 365;
    @Setting(category = "Market", section = "Market signals (/market)", order = 230, label = "Min past cases", desc = "A mayor / event signal needs at least this many past cases.", min = 2, max = 20)
    public int marketMinSamples = 3;
    @Setting(category = "Market", section = "Market signals (/market)", order = 240, label = "Min move %", desc = "Only show signals that expect at least this % change.", min = 1, max = 100)
    public double marketMinMove = 5;
    @Setting(category = "Market", section = "Market signals (/market)", order = 250, label = "Min hit rate %", desc = "Only show signals the backtest called right at least this often.", min = 50, max = 100)
    public double marketMinHitRate = 60;
    @Setting(category = "Market", section = "Alerts", order = 260, label = "Price alerts", desc = "/alert <item> below|above <price> pings you (and shows big text) when a Bazaar item crosses your price. Manage them with /alerts. While alerts exist, prices refresh as often as 'Order check every (s)'. Off = your list is kept but nothing pings.", alert = true)
    public boolean priceAlerts = true;
    /** Coins subtracted from profit every time a slayer quest starts (set to what your tier costs). */
    @Setting(category = "Combat", section = "When a session starts", order = 110, label = "Slayer quest cost", desc = "Coins subtracted per slayer quest. 0 = detect automatically from your purse.", min = 0, max = 1000000000.0)
    public double slayerQuestCost = 0;
    /** Tab-list "Area:" names that count as mining. */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 50, label = "Extra mining areas", desc = "Comma separated. Dwarven Mines, Crystal Hollows, Mineshafts, Glacite Tunnels, Deep Caverns and Gold Mine are built in; add others here.")
    public java.util.List<String> miningAreas = new java.util.ArrayList<>(java.util.List.of(
            "Dwarven Mines", "Crystal Hollows", "Mineshaft", "Glacite", "Deep Caverns", "Gold Mine"));
    /** Lowest-BIN prices for auction-house items (rare drops). Set to "" to disable. */
    @Setting(category = "Advanced", section = "Data sources", order = 100, label = "Lowest BIN price URL", desc = "Where auction-house prices come from. Leave empty to turn auction prices off.")
    public String lowestBinUrl = "https://lb.tricked.dev/lowestbins";
    /** "Item Name": "BAZAAR_ID" */
    @Setting(category = "Advanced", section = "What counts as profit", order = 40, label = "Extra item prices", desc = "name=BAZAAR_ID, separated by commas. For items the mod can't price.")
    public Map<String, String> extraItems = new HashMap<>();
    /** "block_id": "Crop Name" */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 70, label = "Extra crop blocks", desc = "block_id=Crop Name, separated by commas.")
    public Map<String, String> extraCropBlocks = new HashMap<>();
    /** "block_id": "Ore Name" */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 80, label = "Extra ore blocks", desc = "block_id=Ore Name, separated by commas.")
    public Map<String, String> extraOreBlocks = new HashMap<>();
    /** "block_id": "Wood Name" */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 90, label = "Extra log blocks", desc = "block_id=Wood Name, separated by commas.")
    public Map<String, String> extraLogBlocks = new HashMap<>();
    /** Tab-list "Area:" names that count as foraging islands. */
    @Setting(category = "Advanced", section = "Areas & blocks", order = 60, label = "Extra foraging areas", desc = "Comma separated. The Park, Galatea, Moonglade Marsh and Torrhus Canyon are built in; add others here.")
    public java.util.List<String> foragingAreas = new java.util.ArrayList<>(java.util.List.of("Galatea", "The Park"));

    // ----- alerts (8.14): each one only shows text and rings, it never does anything in the game for you -----
    @Setting(category = "General", section = "Alerts", order = 140, label = "Stash alert", desc = "Big alert when Hypixel tells you items are waiting in your stash (your inventory was full). Pick them up with /pickupstash.", alert = true)
    public boolean stashAlert = true;
    @Setting(category = "General", section = "Alerts", order = 150, label = "Rare drop alert", desc = "Big alert for Hypixel's drop messages: PET DROP!, INSANE DROP!, CRAZY RARE DROP!, VERY RARE DROP! and RARE DROP!. Pick which ones with 'Alert for'.", alert = true)
    public boolean rareDropAlert = true;
    @Setting(category = "General", section = "Alerts", order = 160, label = "Alert for", desc = "pets only = PET DROP! only. very rare and up = also VERY RARE, CRAZY RARE and INSANE drops. all rare drops = also plain RARE drops.", options = {"pets only", "very rare and up", "all rare drops"}, parent = "rareDropAlert", alert = true)
    public String rareDropMin = "very rare and up";
    @Setting(category = "General", section = "Alerts", order = 170, label = "Server restart alert", desc = "Big alert when the server announces a restart (the countdown in the sidebar): when it first shows, at 1 minute and at 15 seconds. Finish what you're doing.", alert = true)
    public boolean restartAlert = true;
    @Setting(category = "Farming", section = "Alerts", order = 180, label = "Pest spawned alert", desc = "Big alert when pests spawn on your Garden, with the plot (also for pests that spawned while you were away).", alert = true)
    public boolean pestAlert = true;
    @Setting(category = "Farming", section = "Alerts", order = 190, label = "Visitor arrived alert", desc = "Big alert when a visitor arrives on your Garden, with their name. Off by default: visitors come every few minutes.", alert = true)
    public boolean visitorAlert = false;
    @Setting(category = "Mining", section = "Alerts", order = 210, label = "Scatha / worm alert", desc = "Big alert when a worm or Scatha spawns near you in the Crystal Hollows. Once its name tag is in range the alert names it and shows which way it is and how far. Read from Hypixel's own message.", alert = true)
    public boolean scathaAlert = true;
    @Setting(category = "Mining", section = "Alerts", order = 220, label = "Alert for", desc = "Scatha and worms = every worm and Scatha. Scatha only = just the rare Scatha (nothing for plain worms).", options = {"Scatha and worms", "Scatha only"}, parent = "scathaAlert", alert = true)
    public String scathaMode = "Scatha and worms";
    @Setting(category = "Mining", section = "Alerts", order = 230, label = "Golden / Diamond goblin alert", desc = "Big alert when a Golden Goblin or a Diamond Goblin spawns in the mines.", alert = true)
    public boolean goblinAlert = true;
    @Setting(category = "Mining", section = "Alerts", order = 250, label = "Commission done alert", desc = "Big alert when a commission is finished, naming the NPC to visit for the reward.", alert = true)
    public boolean commissionAlert = true;
    @Setting(category = "Mining", section = "Alerts", order = 270, label = "Cold alert", desc = "Big alert when your Cold (shown in the sidebar in the Glacite Tunnels and Mineshafts) reaches the number below. Warm up before it hits 100.", alert = true)
    public boolean coldAlert = true;
    @Setting(category = "Mining", section = "Alerts", order = 280, label = "Cold alert at", desc = "The Cold alert shows when your Cold reaches this number (you freeze at 100).", min = 10, max = 100, parent = "coldAlert", alert = true)
    public int coldAt = 70;
    @Setting(category = "Mining", section = "Glow", order = 310, label = "Glow: Scatha & worms", desc = "Worms get an orange outline and Scatha a red one, so you can find them.")
    public boolean glowScatha = true;
    @Setting(category = "Fishing", section = "Alerts", order = 110, label = "Rare sea creature alert", desc = "Big alert when you fish up a legendary or mythic sea creature (Thunder, Lord Jawbus, Water Hydra, Yeti, Reindrake...). Read from Hypixel's own message.", alert = true)
    public boolean seaCreatureAlert = true;
    @Setting(category = "Combat", section = "Slayer", order = 140, label = "Boss spawned alert", desc = "Big alert when your slayer boss appears, so you don't miss the spawn.", alert = true)
    public boolean slayerSpawnAlert = true;
    @Setting(category = "Combat", section = "Diana", order = 240, label = "Minos Inquisitor alert", desc = "Big alert when you dig up a Minos Inquisitor.", alert = true)
    public boolean inquisitorAlert = true;
    @Setting(category = "Market", section = "Alerts", order = 280, label = "Big alert when an order fills", desc = "Shows the big on-screen alert (not just a ding) when a buy order or sell offer is filled.", alert = true)
    public boolean bzFillAlert = false;
    @Setting(category = "Market", section = "Alerts", order = 300, label = "Auction sold / outbid alert", desc = "Big alert when one of your auctions sells or someone outbids you on an auction.", alert = true)
    public boolean auctionAlert = true;
    @Setting(category = "Timers", section = "Event alerts", order = 110, label = "Event start alert", desc = "Big alert shortly before the Dark Auction and the Cult of the Fallen Star start (the ones switched on under Events). Works even with the panel hidden.", alert = true)
    public boolean eventAlert = false;
    @Setting(category = "Timers", section = "Event alerts", order = 120, label = "Minutes before", desc = "How early the event alert shows. 0 = only when the event starts.", min = 0, max = 5, parent = "eventAlert", alert = true)
    public int eventAlertMinutes = 1;

    // ----- Crystal Hollows minimap (8.14): display only -----
    @Setting(category = "Mining", section = "Crystal Hollows map", order = 150, label = "Crystal Hollows minimap", desc = "A small map of the Crystal Hollows on the HUD, shown only while you are there: you and the way you face, the places you've found in this lobby and your treasure chests. Move or resize it like the other panels (open chat and drag it, or /profit gui). Needs Show HUD.")
    public boolean crystalMap = true;
    @Setting(category = "Mining", section = "Crystal Hollows map", order = 160, label = "Map size", desc = "Width and height of the map in pixels. You can also scale the whole panel with a middle-click while chat is open.", min = 60, max = 240, parent = "crystalMap")
    public int crystalMapSize = 100;
    @Setting(category = "Mining", section = "Crystal Hollows map", order = 170, label = "Map opacity %", desc = "How solid the map's colors are (20 to 100).", min = 20, max = 100, parent = "crystalMap")
    public int crystalMapOpacity = 85;
    @Setting(category = "Mining", section = "Crystal Hollows map", order = 180, label = "Show found places", desc = "Marks Jungle Temple, Goblin Queen's Den, Mines of Divan, Lost Precursor City, Khazad-dûm, Fairy Grotto and Dragon's Lair once you've walked into them, talked to their NPC or your party shared them (needs 'Waypoints from party chat'). They are somewhere else in every lobby.", parent = "crystalMap")
    public boolean crystalMapPlaces = true;
    @Setting(category = "Mining", section = "Crystal Hollows map", order = 190, label = "Place list under the map", desc = "Lists the places you've found under the map, each with an arrow and its distance.", parent = "crystalMap")
    public boolean crystalMapNames = true;
    @Setting(category = "Mining", section = "Crystal Hollows map", order = 200, label = "Show treasure chests", desc = "Your uncovered treasure chests as small dots (green, then yellow, then red as they run out of time). Needs the Treasure chest helper.", parent = "crystalMap")
    public boolean crystalMapChests = true;

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
            FarmProfitClient.LOG.warn("Could not read config, using defaults (the old file is kept as config.json.broken)", e);
            backupBroken(FILE);
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
        if ("Kalapath/farmprofit".equals(instance.updateRepo)) instance.updateRepo = "Kalapath/skyassist";     // the repository was renamed
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
            write(FILE, GSON.toJson(instance));
        } catch (Exception e) {
            FarmProfitClient.LOG.warn("Could not save config", e);
        }
    }

    /** Writes a file so that a crash halfway can't leave half a file: written next to it first, then swapped in. */
    public static void write(Path file, String text) throws java.io.IOException {
        Path dir = file.getParent();
        if (dir != null) Files.createDirectories(dir);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.writeString(tmp, text);
        try {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** A file that couldn't be read is copied to "<name>.broken" before it gets overwritten with fresh data, so nothing is lost for good. */
    public static void backupBroken(Path file) {
        try {
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".broken"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {}
    }
}
