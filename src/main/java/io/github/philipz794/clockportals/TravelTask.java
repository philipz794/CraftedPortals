package io.github.philipz794.clockportals;

import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Boss;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Checks every tick who is standing in a portal and teleports them. */
final class TravelTask extends BukkitRunnable {

    private final ClockPortals plugin;
    /** Player -> ticks spent in the portal. */
    private final Map<UUID, Integer> waiting = new HashMap<>();
    /** Just arrived: may travel again only after leaving the portal. */
    private final Map<UUID, Long> justArrived = new HashMap<>();
    private long tick;

    TravelTask(ClockPortals plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        tick++;
        PortalManager mgr = plugin.manager();
        if (tick % 10 == 0) maintain(mgr);
        Set<UUID> inside = new HashSet<>();

        for (Portal portal : mgr.all()) {
            if (!portal.isLoaded()) continue;
            World w = portal.world();
            for (Entity e : w.getNearbyEntities(portal.box())) {
                if (!e.isValid()) continue;
                UUID id = e.getUniqueId();
                inside.add(id);
                if (justArrived.containsKey(id)) continue;

                if (e instanceof Player p) {
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;
                    int t = waiting.merge(id, 1, Integer::sum);
                    int delay = p.getGameMode() == GameMode.CREATIVE ? 0 : plugin.playerDelay();
                    if (t >= delay) {
                        waiting.remove(id);
                        travel(e, portal);
                    }
                } else if (canTravel(e)) {
                    travel(e, portal);
                }
            }
        }

        waiting.keySet().retainAll(inside);
        // Clear the "arrived" marker once the portal has been left (at least 1 s later)
        justArrived.entrySet().removeIf(en -> !inside.contains(en.getKey()) && tick - en.getValue() > 20);
    }

    /**
     * Vanilla removes nether portal blocks as soon as a frame block changes
     * (frame is not obsidian). Therefore: frame still valid -> repair the interior,
     * frame broken (e.g. via command/WorldEdit) -> remove the portal.
     */
    private void maintain(PortalManager mgr) {
        Material want = plugin.useEndEffect() ? Material.END_GATEWAY : Material.NETHER_PORTAL;
        for (Portal p : mgr.all()) {
            if (!p.isLoaded()) continue;
            World w = p.world();
            boolean broken = false;
            for (Block f : p.frame(w)) {
                if (!p.isCorner(f) && !FrameDetector.isFullBlock(f)) { broken = true; break; }
            }
            if (broken) {
                plugin.destroyWithMessage(p);
                continue;
            }
            for (Block b : p.interior(w)) {
                if (b.getType() != want) { mgr.fill(p); break; }
            }
        }
    }

    private boolean canTravel(Entity e) {
        if (e.isInsideVehicle() || !e.getPassengers().isEmpty()) return false;
        if (e instanceof Item) return plugin.teleportItems();
        if (e instanceof Boss || e instanceof EnderDragon) return false;
        if (e instanceof LivingEntity le) {
            if (le instanceof Mob m && m.isLeashed()) return false; // travels along with the player
            return plugin.teleportMobs();
        }
        return false;
    }

    private void travel(Entity e, Portal from) {
        PortalManager mgr = plugin.manager();
        Portal to = mgr.next(from);
        if (to == null) {
            if (e instanceof Player p) {
                p.sendActionBar(plugin.msg("no-target", "%name%", from.displayName));
                justArrived.put(e.getUniqueId(), tick); // do not notify every tick
            }
            return;
        }
        World tw = to.world();
        if (tw == null) return;

        Location dest = to.arrival(tw);
        Location cur = e.getLocation();
        boolean rotate = from.axis != to.axis;
        float yaw = cur.getYaw();
        if (rotate) yaw += from.axis == Axis.X ? -90f : 90f;
        dest.setYaw(yaw);
        dest.setPitch(cur.getPitch());

        // Convert velocity (the component perpendicular to the portal is preserved)
        Vector v = e.getVelocity();
        Vector nv = v.clone();
        if (rotate) {
            if (from.axis == Axis.X) nv = new Vector(v.getZ(), v.getY(), -v.getX());
            else nv = new Vector(-v.getZ(), v.getY(), v.getX());
        }
        final Vector outVel = nv;

        // Collect the player's leashed animals
        List<Mob> leashed = new ArrayList<>();
        if (e instanceof Player p && plugin.leashedFollow()) {
            for (Entity n : p.getNearbyEntities(12, 12, 12)) {
                if (n instanceof Mob m && m.isLeashed() && p.equals(safeHolder(m))) leashed.add(m);
            }
        }

        justArrived.put(e.getUniqueId(), tick);
        for (Mob m : leashed) {
            justArrived.put(m.getUniqueId(), tick);
            m.setLeashHolder(null);
        }

        e.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok -> {
            if (!ok) return;
            if (!(e instanceof Player)) e.setVelocity(outVel);
            if (e instanceof Player p) {
                p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.0f);
                for (Mob m : leashed) {
                    m.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok2 ->
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                if (m.isValid() && p.isOnline()) m.setLeashHolder(p);
                            }, 2L));
                }
            }
        });
    }

    private static Entity safeHolder(Mob m) {
        try {
            return m.getLeashHolder();
        } catch (IllegalStateException ex) {
            return null;
        }
    }
}
