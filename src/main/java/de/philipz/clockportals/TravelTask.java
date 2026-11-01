package de.philipz.clockportals;

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

/** Prueft jeden Tick, wer in einem Portal steht, und teleportiert. */
final class TravelTask extends BukkitRunnable {

    private final ClockPortals plugin;
    /** Spieler -> Ticks im Portal. */
    private final Map<UUID, Integer> waiting = new HashMap<>();
    /** Gerade angekommen: erst wieder reisen, wenn das Portal verlassen wurde. */
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
        // "Angekommen"-Markierung loeschen, sobald das Portal verlassen wurde (min. 1 s spaeter)
        justArrived.entrySet().removeIf(en -> !inside.contains(en.getKey()) && tick - en.getValue() > 20);
    }

    /**
     * Vanilla entfernt Netherportal-Bloecke, sobald sich ein Rahmenblock aendert
     * (kein Obsidian). Deshalb: Rahmen noch gueltig -> Innenraum reparieren,
     * Rahmen kaputt (z.B. per Befehl/WorldEdit) -> Portal entfernen.
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
            if (le instanceof Mob m && m.isLeashed()) return false; // reist mit dem Spieler
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
                justArrived.put(e.getUniqueId(), tick); // nicht jeden Tick melden
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

        // Bewegung umrechnen (Richtung senkrecht zum Portal bleibt erhalten)
        Vector v = e.getVelocity();
        Vector nv = v.clone();
        if (rotate) {
            if (from.axis == Axis.X) nv = new Vector(v.getZ(), v.getY(), -v.getX());
            else nv = new Vector(-v.getZ(), v.getY(), v.getX());
        }
        final Vector outVel = nv;

        // Angeleinte Tiere des Spielers einsammeln
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
