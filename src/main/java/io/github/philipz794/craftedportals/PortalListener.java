package io.github.philipz794.craftedportals;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import com.destroystokyo.paper.event.entity.EntityTeleportEndGatewayEvent;
import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import io.papermc.paper.event.entity.EntityPortalReadyEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

final class PortalListener implements Listener {

    private final CraftedPortals plugin;
    private final PortalManager mgr;

    PortalListener(CraftedPortals plugin) {
        this.plugin = plugin;
        this.mgr = plugin.manager();
    }

    // ------------------------------------------------------------ Activation

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block clicked = e.getClickedBlock();
        ItemStack item = e.getItem();
        if (clicked == null || item == null || item.getType() != plugin.activationItem()) return;
        Player p = e.getPlayer();

        ItemMeta meta = item.getItemMeta();
        String name = null;
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            name = PlainTextComponentSerializer.plainText().serialize(meta.displayName()).trim();
            if (name.isEmpty()) name = null;
        }

        Portal existing = mgr.byAny(clicked);
        if (existing != null) {
            e.setCancelled(true);
            p.sendActionBar(plugin.portalMsg("info", existing));
            return;
        }

        FrameDetector.Result r = plugin.detector().detect(clicked);
        if (name == null) {
            // Unnamed clock: only respond if this really is a frame
            if (r.ok() || r.error() == FrameDetector.Error.MISSING_KEYS) {
                e.setCancelled(true);
                p.sendActionBar(plugin.msg("no-name", "%item%", plugin.activationItemName()));
            }
            return;
        }
        e.setCancelled(true);
        switch (r.error()) {
            case NONE -> {
                // Arrivals face the compass direction the activating player looks in now
                Portal portal = mgr.create(name, clicked.getWorld(), r,
                        Portal.throughDirection(r.axis(), p.getLocation().getYaw()));
                if (plugin.consumeItem() && p.getGameMode() != GameMode.CREATIVE) {
                    item.setAmount(item.getAmount() - 1);
                }
                Location c = portal.arrival(clicked.getWorld());
                Sound s = switch (plugin.effect()) {
                    case END -> Sound.BLOCK_END_PORTAL_SPAWN;
                    case NETHER -> Sound.BLOCK_PORTAL_TRIGGER;
                    case PARTICLE -> Sound.BLOCK_BEACON_ACTIVATE;
                };
                clicked.getWorld().playSound(c, s, 0.7f, 1.2f);
                p.sendActionBar(plugin.portalMsg("activated", portal));
                if (mgr.count(portal) == 1) {
                    Component hint = plugin.msg("no-target", "%name%", portal.displayName);
                    // (displayName has any colour suffix already stripped)
                    plugin.getServer().getScheduler().runTaskLater(plugin, () -> p.sendActionBar(hint), 50L);
                }
            }
            case TOO_SMALL -> p.sendActionBar(plugin.msg("too-small"));
            case TOO_BIG -> p.sendActionBar(plugin.msg("too-big", "%max%", plugin.maxBlocks()));
            case MISSING_KEYS -> p.sendActionBar(plugin.msg("missing-keys", "%have%", r.keysFound(),
                    "%need%", plugin.keyCount(),
                    "%block%", String.valueOf(plugin.keyBlock()).toLowerCase()));
            default -> p.sendActionBar(plugin.msg("invalid-frame"));
        }
    }

    // ------------------------------------------------------------ Destruction

    private void destroy(Portal p) {
        plugin.destroyWithMessage(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Portal p = mgr.byAny(e.getBlock());
        if (p != null && !p.isCorner(e.getBlock())) destroy(p);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent e) {
        Portal p = mgr.byFrame(e.getBlock());
        if (p != null) destroy(p);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        Portal in = mgr.byInterior(e.getBlock());
        if (in != null) { e.setCancelled(true); return; }
        Portal fr = mgr.byFrame(e.getBlock());
        if (fr != null && e.getTo() != e.getBlock().getType()) {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!FrameDetector.isFullBlock(e.getBlock()) && !fr.isCorner(e.getBlock())) destroy(fr);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        Set<Portal> hit = new HashSet<>();
        Iterator<Block> it = blocks.iterator();
        while (it.hasNext()) {
            Block b = it.next();
            Portal in = mgr.byInterior(b);
            if (in != null) { it.remove(); continue; }
            Portal fr = mgr.byFrame(b);
            if (fr != null && !fr.isCorner(b)) hit.add(fr);
        }
        hit.forEach(this::destroy);
    }

    // ------------------------------------------------------------ Protection

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent e) {
        Material t = e.getBlock().getType();
        if (t != Material.NETHER_PORTAL && t != Material.END_GATEWAY) return;
        if (mgr.byInterior(e.getBlock()) != null) e.setCancelled(true);
    }

    /** With the PARTICLE effect the interior is air, so players could build into it. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (mgr.byInterior(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        if (mgr.byInterior(e.getToBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (touches(e.getBlocks()) || mgr.byAny(e.getBlock().getRelative(e.getDirection())) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (touches(e.getBlocks())) e.setCancelled(true);
    }

    private boolean touches(List<Block> blocks) {
        for (Block b : blocks) {
            if (mgr.byAny(b) != null) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ Suppress vanilla portals

    private boolean inOurPortal(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        Block b = loc.getBlock();
        return mgr.byInterior(b) != null || mgr.byInterior(b.getRelative(0, 1, 0)) != null;
    }

    /**
     * Earliest hook: fired every tick for an entity standing in a nether portal or end gateway
     * block. Cancelling it stops vanilla from starting its portal logic at all, so no vanilla
     * teleport and no portal generation in the other dimension can happen. This also removes the
     * vanilla purple overlay (it depends on the server-side portal process); the plugin shows its
     * own travel effect instead, see TravelTask. The portal event handlers below remain as a safety net.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInsideBlock(EntityInsideBlockEvent e) {
        Material t = e.getBlock().getType();
        if (t != Material.NETHER_PORTAL && t != Material.END_GATEWAY) return;
        if (mgr.byInterior(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerPortal(PlayerPortalEvent e) {
        if (inOurPortal(e.getFrom())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onEntityPortal(EntityPortalEvent e) {
        if (inOurPortal(e.getFrom())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerTeleport(PlayerTeleportEvent e) {
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        if ((c == PlayerTeleportEvent.TeleportCause.END_GATEWAY || c == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL)
                && inOurPortal(e.getFrom())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onGateway(EntityTeleportEndGatewayEvent e) {
        if (inOurPortal(e.getFrom())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPortalReady(EntityPortalReadyEvent e) {
        if (inOurPortal(e.getEntity().getLocation())) e.setCancelled(true);
    }

    /**
     * Vanilla spawns zombified piglins from nether portal blocks
     * (NetherPortalBlock.randomTick, spawn position = lowest portal block of the column).
     * With piglin-spawn: false this is suppressed for our portals.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.NETHER_PORTAL) return;
        if (!plugin.piglinSpawn() && inOurPortal(e.getLocation())) e.setCancelled(true);
    }

    /** Admins learn about config problems when they join (a little delayed, after the join spam). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!p.hasPermission("craftedportals.admin")) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) plugin.sendConfigWarnings(p);
        }, 40L);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        mgr.reindexWorld(e.getWorld());
    }
}
