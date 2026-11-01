package io.github.philipz794.craftedportals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;

public final class CraftedPortals extends JavaPlugin {

    private PortalManager manager;
    private FrameDetector detector;
    private TravelTask travel;

    /** Portal visuals. */
    enum Effect { NETHER, END, PARTICLE }

    /** Settings of the PARTICLE effect. */
    record ParticleSettings(ParticleSpec spec, int perBlock, int intervalTicks,
                            boolean sheet, Color sheetColor, int sheetOpacity) {}

    /** Ambient sound of portals without nether portal blocks (PARTICLE and END looks). */
    record AmbientSettings(SoundSpec sound, int seconds) {}

    /** Screen effect shown to a waiting survival player. */
    enum TravelEffect { NAUSEA, FROST, DARKNESS, NONE }

    /** Everything a waiting player sees and hears. */
    record TravelSettings(TravelEffect effect, boolean particles, ParticleSpec spec,
                          int particleAmount, SoundSpec sound, SoundSpec arrival, boolean bar) {}

    private Effect effect = Effect.NETHER;
    private ParticleSettings particles;
    private AmbientSettings ambient;
    private TravelSettings travelSettings;
    private int playerDelay;
    private boolean consumeItem, teleportMobs, teleportItems, leashedFollow, piglinSpawn;
    private int maxBlocks;
    private Material activationItem = Material.CLOCK;
    /** Problems found in config.yml, shown to admins on join and after reload. */
    private final List<String> configWarnings = new ArrayList<>();

    @Override
    public void onEnable() {
        migrateDataFolder();
        saveDefaultConfig();
        try {
            ConfigUpdater.Result upd = ConfigUpdater.update(this);
            for (String r : upd.renamed()) getLogger().info("config.yml: renamed option " + r);
            for (String r : upd.removed()) getLogger().info("config.yml: removed obsolete option " + r);
            if (!upd.added().isEmpty()) getLogger().info("config.yml: added new option(s) " + String.join(", ", upd.added()));
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not update config.yml", e);
        }
        loadSettings();
        manager = new PortalManager(this);
        manager.sheets().removeStrays();
        manager.load();
        travel = new TravelTask(this);
        travel.runTaskTimer(this, 1L, 1L);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
        // Re-place portal blocks after startup (in case the effect was changed in the config)
        Bukkit.getScheduler().runTask(this, () -> manager.all().forEach(manager::fill));
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.save();
            manager.sheets().removeAll();
        }
    }

    /** The plugin was called Clock-Portals before its first public release; move that data folder. */
    private void migrateDataFolder() {
        File old = new File(getDataFolder().getParentFile(), "Clock-Portals");
        if (!old.isDirectory() || getDataFolder().exists()) return;
        try {
            Files.move(old.toPath(), getDataFolder().toPath());
            getLogger().info("Moved data folder Clock-Portals/ to " + getDataFolder().getName() + "/");
        } catch (IOException e) {
            getLogger().log(Level.WARNING, "Could not move the old data folder Clock-Portals/", e);
        }
    }

    void loadSettings() {
        reloadConfig();
        configWarnings.clear();
        String keyName = getConfig().getString("key-block", "EMERALD_BLOCK");
        Material key = Material.matchMaterial(keyName);
        if (key == null || !key.isBlock()) {
            warn("Unknown key-block '" + keyName + "', using EMERALD_BLOCK");
            key = Material.EMERALD_BLOCK;
        }
        String itemName = getConfig().getString("activation-item", "CLOCK");
        Material item = Material.matchMaterial(itemName);
        if (item == null || !item.isItem() || item.isAir()) {
            warn("Unknown activation-item '" + itemName + "', using CLOCK");
            item = Material.CLOCK;
        }
        activationItem = item;
        int wanted;
        if (!getConfig().contains("max-blocks") && getConfig().contains("max-size")) {
            // Config from 1.0.0: outer frame size -> interior cell count of the equivalent rectangle
            int inner = Math.max(1, getConfig().getInt("max-size") - 2);
            wanted = inner * inner;
            getLogger().info("Legacy option max-size=" + getConfig().getInt("max-size")
                    + " read as max-blocks=" + wanted + ". Please rename it in config.yml.");
        } else {
            wanted = getConfig().getInt("max-blocks", 200);
        }
        if (wanted > FrameDetector.HARD_MAX_BLOCKS) {
            warn("max-blocks=" + wanted + " exceeds the hard limit, using " + FrameDetector.HARD_MAX_BLOCKS);
        } else if (wanted < 2) {
            warn("max-blocks=" + wanted + " is too small, using 2");
        }
        detector = new FrameDetector(key, Math.max(0, getConfig().getInt("key-block-count", 2)), wanted);
        maxBlocks = detector.maxBlocks();
        String effectName = getConfig().getString("effect", "PARTICLE");
        try {
            effect = Effect.valueOf(effectName.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warn("Unknown effect '" + effectName + "', using PARTICLE");
            effect = Effect.PARTICLE;
        }
        particles = loadParticles();
        ambient = new AmbientSettings(
                SoundSpec.load(getConfig(), "ambient-sound", "BLOCK_PORTAL_AMBIENT", 0.3f, "ambient-sound", this::warn),
                Math.max(1, Math.min(60, getConfig().getInt("ambient-sound-interval", 4))));
        travelSettings = loadTravelSettings();
        playerDelay = Math.max(0, getConfig().getInt("player-delay-ticks", 60));
        consumeItem = getConfig().getBoolean("consume-item", true);
        teleportMobs = getConfig().getBoolean("teleport-mobs", true);
        teleportItems = getConfig().getBoolean("teleport-items", true);
        leashedFollow = getConfig().getBoolean("leashed-follow", true);
        piglinSpawn = getConfig().getBoolean("piglin-spawn", false);
    }

    private ParticleSettings loadParticles() {
        ConfigurationSection sec = getConfig().getConfigurationSection("particles");
        ParticleSpec spec = ParticleSpec.load(sec, "particles", "TRAIL", Color.fromRGB(0x4682B4), this::warn);
        int perBlock = sec == null ? 1 : Math.max(0, Math.min(10, sec.getInt("per-block", 1)));
        int interval = sec == null ? 5 : Math.max(1, Math.min(40, sec.getInt("interval-ticks", 5)));
        ConfigurationSection sh = sec == null ? null : sec.getConfigurationSection("sheet");
        boolean sheet = sh == null || sh.getBoolean("enabled", true);
        Color sheetColor = ParticleSpec.parseColor(sh == null ? null : sh.getString("color"),
                Color.fromRGB(0x4169E1), "particles.sheet.color", this::warn);
        int opacity = sh == null ? 50 : Math.max(5, Math.min(100, sh.getInt("opacity", 50)));
        return new ParticleSettings(spec, perBlock, interval, sheet, sheetColor, opacity);
    }

    private TravelSettings loadTravelSettings() {
        String name = getConfig().getString("travel-effect", "NAUSEA");
        TravelEffect eff;
        try {
            eff = TravelEffect.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            warn("Unknown travel-effect '" + name + "', using NAUSEA");
            eff = TravelEffect.NAUSEA;
        }
        ConfigurationSection tp = getConfig().getConfigurationSection("travel-particles");
        boolean particlesOn = tp != null && tp.getBoolean("enabled", true);
        ParticleSpec spec = ParticleSpec.load(tp, "travel-particles", "ENCHANT", ParticleSpec.DEFAULT_COLOR, this::warn);
        int amount = tp == null ? 10 : Math.max(1, Math.min(30, tp.getInt("amount", 10)));
        SoundSpec sound = SoundSpec.load(getConfig(), "travel-sound", "BLOCK_PORTAL_TRIGGER", 0.6f, "travel-sound", this::warn);
        SoundSpec arrival = SoundSpec.load(getConfig(), "arrival-sound", "BLOCK_PORTAL_TRAVEL", 0.25f, "arrival-sound", this::warn);
        boolean bar = getConfig().getBoolean("travel-bar", true);
        return new TravelSettings(eff, particlesOn, spec, amount, sound, arrival, bar);
    }

    private void warn(String problem) {
        configWarnings.add(problem);
        getLogger().warning("config.yml: " + problem);
    }

    /** Sends all config problems to an admin (empty list = nothing is sent). */
    void sendConfigWarnings(CommandSender to) {
        for (String w : configWarnings) {
            to.sendMessage(Component.text("[CraftedPortals] config.yml: " + w, NamedTextColor.YELLOW));
        }
    }

    Material activationItem() { return activationItem; }
    /** Lower-case item name for messages, e.g. "clock" or "recovery compass". */
    String activationItemName() { return activationItem.name().toLowerCase(Locale.ROOT).replace('_', ' '); }

    PortalManager manager() { return manager; }
    FrameDetector detector() { return detector; }
    TravelTask travel() { return travel; }
    Effect effect() { return effect; }
    ParticleSettings particles() { return particles; }
    AmbientSettings ambient() { return ambient; }
    TravelSettings travelSettings() { return travelSettings; }
    boolean useEndEffect() { return effect == Effect.END; }
    int playerDelay() { return effect == Effect.END ? 0 : playerDelay; }
    boolean consumeItem() { return consumeItem; }
    boolean teleportMobs() { return teleportMobs; }
    boolean teleportItems() { return teleportItems; }
    boolean leashedFollow() { return leashedFollow; }
    boolean piglinSpawn() { return piglinSpawn; }
    int maxBlocks() { return maxBlocks; }
    Material keyBlock() { return Material.matchMaterial(getConfig().getString("key-block", "EMERALD_BLOCK")); }
    int keyCount() { return getConfig().getInt("key-block-count", 2); }

    /** Message from the config with placeholders (pairs: "%name%", value, ...). */
    Component msg(String key, Object... repl) {
        String s = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < repl.length; i += 2) s = s.replace(String.valueOf(repl[i]), String.valueOf(repl[i + 1]));
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    Component portalMsg(String key, Portal p) {
        return msg(key, "%name%", p.displayName, "%pos%", manager.position(p), "%count%", manager.count(p));
    }

    /** Remove a portal, play a sound and notify nearby players. */
    void destroyWithMessage(Portal p) {
        World w = p.world();
        org.bukkit.Location c = w != null ? p.arrival(w) : null;
        Component m = portalMsg("destroyed", p);
        manager.destroy(p);
        if (c != null) {
            w.playSound(c, org.bukkit.Sound.BLOCK_GLASS_BREAK, 0.8f, 0.6f);
            for (Player pl : w.getPlayers()) {
                if (pl.getLocation().distanceSquared(c) < 24 * 24) pl.sendActionBar(m);
            }
        }
    }

    // ------------------------------------------------------------ Command

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                loadSettings();
                manager.all().forEach(manager::fill);
                sender.sendMessage(Component.text("CraftedPortals: config reloaded."));
                sendConfigWarnings(sender);
            }
            case "list" -> {
                if (manager.networks().isEmpty()) {
                    sender.sendMessage(Component.text("No portals yet."));
                    return true;
                }
                for (List<Portal> net : manager.networks()) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            "&6" + net.get(0).displayName + " &7(" + net.size() + " portal(s))"));
                    int i = 1;
                    for (Portal p : net) {
                        sender.sendMessage(Component.text("  " + i++ + ". " + p.worldName + " "
                                + p.minX + " " + p.minY + " " + p.minZ));
                    }
                }
            }
            case "create" -> {
                // /craftedportals create <name> <x> <y> <z> [world]  -> activates the frame at block x y z
                if (args.length < 5) {
                    sender.sendMessage(Component.text("/craftedportals create <name> <x> <y> <z> [world]"));
                    return true;
                }
                World w = args.length >= 6 ? Bukkit.getWorld(args[5])
                        : sender instanceof Player pl ? pl.getWorld() : Bukkit.getWorlds().get(0);
                if (w == null) {
                    sender.sendMessage(Component.text("World not found."));
                    return true;
                }
                try {
                    int x = Integer.parseInt(args[2]), y = Integer.parseInt(args[3]), z = Integer.parseInt(args[4]);
                    Component res = activate(args[1], w.getBlockAt(x, y, z));
                    sender.sendMessage(res);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Invalid coordinates."));
                }
            }
            default -> sender.sendMessage(Component.text("/craftedportals <list|reload|create>"));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("list", "reload", "create"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        }
        return out;
    }

    /** Tries to activate a portal at the block. Returns the message; null-safe. */
    Component activate(String name, org.bukkit.block.Block clicked) {
        Portal existing = manager.byAny(clicked);
        if (existing != null) return portalMsg("info", existing);
        FrameDetector.Result r = detector.detect(clicked);
        return switch (r.error()) {
            case NONE -> {
                Portal p = manager.create(name, clicked.getWorld(), r);
                yield portalMsg("activated", p);
            }
            case TOO_SMALL -> msg("too-small");
            case TOO_BIG -> msg("too-big", "%max%", maxBlocks);
            case MISSING_KEYS -> msg("missing-keys", "%have%", r.keysFound(), "%need%", keyCount(),
                    "%block%", String.valueOf(keyBlock()).toLowerCase(Locale.ROOT));
            default -> msg("invalid-frame");
        };
    }
}
