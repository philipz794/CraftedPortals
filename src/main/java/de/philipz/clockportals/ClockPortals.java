package de.philipz.clockportals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ClockPortals extends JavaPlugin {

    private PortalManager manager;
    private FrameDetector detector;
    private TravelTask travel;

    private boolean endEffect;
    private int playerDelay;
    private boolean consumeClock, teleportMobs, teleportItems, leashedFollow;
    private int maxSize;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSettings();
        manager = new PortalManager(this);
        manager.load();
        travel = new TravelTask(this);
        travel.runTaskTimer(this, 1L, 1L);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
        // Portalbloecke nach Start erneut setzen (falls Effekt in der Config geaendert wurde)
        Bukkit.getScheduler().runTask(this, () -> manager.all().forEach(manager::fill));
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.save();
    }

    void loadSettings() {
        reloadConfig();
        Material key = Material.matchMaterial(getConfig().getString("key-block", "EMERALD_BLOCK"));
        if (key == null || !key.isBlock()) {
            getLogger().warning("Unbekannter key-block, verwende EMERALD_BLOCK");
            key = Material.EMERALD_BLOCK;
        }
        maxSize = Math.max(4, getConfig().getInt("max-size", 30));
        detector = new FrameDetector(key, Math.max(0, getConfig().getInt("key-block-count", 2)), maxSize);
        endEffect = "END".equalsIgnoreCase(getConfig().getString("effect", "NETHER"));
        playerDelay = Math.max(0, getConfig().getInt("player-delay-ticks", 80));
        consumeClock = getConfig().getBoolean("consume-clock", true);
        teleportMobs = getConfig().getBoolean("teleport-mobs", true);
        teleportItems = getConfig().getBoolean("teleport-items", true);
        leashedFollow = getConfig().getBoolean("leashed-follow", true);
    }

    PortalManager manager() { return manager; }
    FrameDetector detector() { return detector; }
    TravelTask travel() { return travel; }
    boolean useEndEffect() { return endEffect; }
    int playerDelay() { return endEffect ? 0 : playerDelay; }
    boolean consumeClock() { return consumeClock; }
    boolean teleportMobs() { return teleportMobs; }
    boolean teleportItems() { return teleportItems; }
    boolean leashedFollow() { return leashedFollow; }
    int maxSize() { return maxSize; }
    Material keyBlock() { return Material.matchMaterial(getConfig().getString("key-block", "EMERALD_BLOCK")); }
    int keyCount() { return getConfig().getInt("key-block-count", 2); }

    /** Meldung aus der Config mit Platzhaltern (Paare: "%name%", wert, ...). */
    Component msg(String key, Object... repl) {
        String s = getConfig().getString("messages." + key, key);
        for (int i = 0; i + 1 < repl.length; i += 2) s = s.replace(String.valueOf(repl[i]), String.valueOf(repl[i + 1]));
        return LegacyComponentSerializer.legacyAmpersand().deserialize(s);
    }

    Component portalMsg(String key, Portal p) {
        return msg(key, "%name%", p.displayName, "%pos%", manager.position(p), "%count%", manager.count(p));
    }

    /** Portal entfernen, Sound + Meldung an Spieler in der Naehe. */
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

    // ------------------------------------------------------------ Befehl

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                loadSettings();
                manager.all().forEach(manager::fill);
                sender.sendMessage(Component.text("Clock-Portals: Config neu geladen."));
            }
            case "list" -> {
                if (manager.networks().isEmpty()) {
                    sender.sendMessage(Component.text("Keine Portale vorhanden."));
                    return true;
                }
                for (List<Portal> net : manager.networks()) {
                    sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize(
                            "&6" + net.get(0).displayName + " &7(" + net.size() + " Portal(e))"));
                    int i = 1;
                    for (Portal p : net) {
                        sender.sendMessage(Component.text("  " + i++ + ". " + p.worldName + " "
                                + p.minX + " " + p.minY + " " + p.minZ));
                    }
                }
            }
            case "create" -> {
                // /clockportals create <name> <x> <y> <z> [welt]  -> aktiviert den Rahmen am Block x y z
                if (args.length < 5) {
                    sender.sendMessage(Component.text("/clockportals create <name> <x> <y> <z> [welt]"));
                    return true;
                }
                World w = args.length >= 6 ? Bukkit.getWorld(args[5])
                        : sender instanceof Player pl ? pl.getWorld() : Bukkit.getWorlds().get(0);
                if (w == null) {
                    sender.sendMessage(Component.text("Welt nicht gefunden."));
                    return true;
                }
                try {
                    int x = Integer.parseInt(args[2]), y = Integer.parseInt(args[3]), z = Integer.parseInt(args[4]);
                    Component res = activate(args[1], w.getBlockAt(x, y, z));
                    sender.sendMessage(res);
                } catch (NumberFormatException e) {
                    sender.sendMessage(Component.text("Ungueltige Koordinaten."));
                }
            }
            default -> sender.sendMessage(Component.text("/clockportals <list|reload|create>"));
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

    /** Versucht, am Block ein Portal zu aktivieren. Liefert die Meldung; null-sicher. */
    Component activate(String name, org.bukkit.block.Block clicked) {
        Portal existing = manager.byAny(clicked);
        if (existing != null) return portalMsg("info", existing);
        FrameDetector.Result r = detector.detect(clicked);
        return switch (r.error()) {
            case NONE -> {
                Portal p = manager.create(name, clicked.getWorld(), r);
                yield portalMsg("activated", p);
            }
            case TOO_BIG -> msg("too-big", "%max%", maxSize);
            case MISSING_KEYS -> msg("missing-keys", "%have%", r.keysFound(), "%need%", keyCount(),
                    "%block%", String.valueOf(keyBlock()).toLowerCase(Locale.ROOT));
            default -> msg("invalid-frame");
        };
    }
}
