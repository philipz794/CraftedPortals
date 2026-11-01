package io.github.philipz794.craftedportals;

import org.bukkit.Axis;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Checks every tick who is standing in a portal and teleports them. */
final class TravelTask extends BukkitRunnable {

    private final CraftedPortals plugin;
    /** Player -> ticks spent in the portal. */
    private final Map<UUID, Integer> waiting = new HashMap<>();
    /** Just arrived: may travel again only after leaving the portal. */
    private final Map<UUID, Long> justArrived = new HashMap<>();
    private long tick;

    TravelTask(CraftedPortals plugin) {
        this.plugin = plugin;
    }

    /** Arrival portal plus the interior cells currently sent as air to that player. */
    private record Hidden(Portal portal, Set<Long> cells) {}
    /** Players who see the portal blocks they stand in as air until they step out of the arrival portal. */
    private final Map<UUID, Hidden> hiddenInterior = new HashMap<>();

    @Override
    public void run() {
        tick++;
        PortalManager mgr = plugin.manager();
        if (tick % 10 == 0) maintain(mgr);
        if (plugin.effect() == CraftedPortals.Effect.PARTICLE) emitParticles(mgr);
        updateHiddenCells();
        Set<UUID> inside = new HashSet<>();

        for (Portal portal : mgr.all()) {
            if (!portal.isLoaded()) continue;
            World w = portal.world();
            for (Entity e : w.getNearbyEntities(portal.box())) {
                if (!e.isValid()) continue;
                // The bounding box is only a coarse filter; the shape may have notches
                if (!portal.intersects(e.getBoundingBox())) continue;
                UUID id = e.getUniqueId();
                inside.add(id);
                if (justArrived.containsKey(id)) continue;

                if (e instanceof Player p) {
                    if (p.getGameMode() == GameMode.SPECTATOR) continue;
                    if (mgr.next(portal) == null) {
                        travel(e, portal); // no destination: say so right away, no waiting
                        continue;
                    }
                    int t = waiting.merge(id, 1, Integer::sum);
                    int delay = p.getGameMode() == GameMode.CREATIVE ? 0 : plugin.playerDelay();
                    if (t >= delay) {
                        waiting.remove(id);
                        endWaiting(p);
                        travel(e, portal);
                    } else {
                        showWaiting(p, portal, t, delay);
                    }
                } else if (canTravel(e)) {
                    travel(e, portal);
                }
            }
        }

        // Players who left the portal before the delay was over: stop their effects
        for (Iterator<UUID> it = waiting.keySet().iterator(); it.hasNext(); ) {
            UUID id = it.next();
            if (inside.contains(id)) continue;
            it.remove();
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) endWaiting(p);
        }
        // Clear the "arrived" marker once the portal has been left (at least 1 s later)
        justArrived.entrySet().removeIf(en -> {
            if (inside.contains(en.getKey()) || tick - en.getValue() <= 20) return false;
            Hidden hidden = hiddenInterior.remove(en.getKey());
            if (hidden != null) {
                Player p = plugin.getServer().getPlayer(en.getKey());
                if (p != null) revealAll(p, hidden);
            }
            return true;
        });
    }

    /**
     * Vanilla removes nether portal blocks as soon as a frame block changes
     * (frame is not obsidian). Therefore: frame still valid -> repair the interior,
     * frame broken (e.g. via command/WorldEdit) -> remove the portal.
     */
    private void maintain(PortalManager mgr) {
        Material want = switch (plugin.effect()) {
            case END -> Material.END_GATEWAY;
            case NETHER -> Material.NETHER_PORTAL;
            case PARTICLE -> null; // interior is air, nothing to repair
        };
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
            if (want == null) {
                // PARTICLE: the sheet's display entities vanish with unloaded chunks; recreate them
                mgr.fill(p);
                continue;
            }
            for (Block b : p.interior(w)) {
                if (b.getType() != want) { mgr.fill(p); break; }
            }
        }
    }

    /** Visual range in which particles and the ambient sound are worth sending. */
    private static final double PARTICLE_RANGE = 48;

    /**
     * PARTICLE effect: fills the interior with particles every interval, only for portals that
     * have players nearby. Particles are spread over the whole block in the portal plane and
     * kept thin perpendicular to it, so the portal reads as a flat sheet.
     */
    private void emitParticles(PortalManager mgr) {
        CraftedPortals.ParticleSettings ps = plugin.particles();
        if (tick % ps.intervalTicks() != 0 || ps.perBlock() == 0) return;
        boolean ambient = ps.sound() && tick % 100 == 0;
        for (Portal p : mgr.all()) {
            if (!p.isLoaded()) continue;
            World w = p.world();
            Location centre = p.box().getCenter().toLocation(w);
            if (w.getNearbyPlayers(centre, PARTICLE_RANGE).isEmpty()) continue;
            double dx = p.axis == Axis.X ? 0.3 : 0.05, dz = p.axis == Axis.X ? 0.05 : 0.3;
            Object data = ps.data();
            if (data instanceof Particle.DustOptions dust && mgr.colorOf(p) != null) {
                data = new Particle.DustOptions(mgr.colorOf(p), dust.getSize()); // network colour wins
            }
            for (Block b : p.interior(w)) {
                Location c = b.getLocation().add(0.5, 0.5, 0.5);
                w.spawnParticle(ps.type(), c, ps.perBlock(), dx, 0.3, dz, 0.0, data);
            }
            if (ambient) w.playSound(centre, Sound.BLOCK_PORTAL_AMBIENT, 0.3f, 1.0f);
        }
    }

    // ------------------------------------------------------------ Waiting effects

    /** Full-screen frost without damage: vanilla hurts at 140, so stay below. */
    private static final int MAX_FROST = 130;

    /** Called every tick while a survival player waits inside a portal (t of delay ticks done). */
    private void showWaiting(Player p, Portal portal, int t, int delay) {
        CraftedPortals.TravelSettings ts = plugin.travelSettings();
        double progress = delay <= 0 ? 1.0 : Math.min(1.0, t / (double) delay);
        // The client draws vanilla's purple overlay on its own while touching nether portal blocks;
        // hide the touched blocks from the waiting player so only the configured effect is visible.
        if (plugin.effect() == CraftedPortals.Effect.NETHER) {
            hiddenInterior.computeIfAbsent(p.getUniqueId(), k -> new Hidden(portal, new HashSet<>()));
        }
        switch (ts.effect()) {
            case NAUSEA -> p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0, true, false, false));
            case DARKNESS -> p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, true, false, false));
            case FROST -> p.setFreezeTicks((int) (MAX_FROST * progress) + 2); // +2: vanilla thaws 2 per tick
            case NONE -> { }
        }
        if (ts.particles()) {
            int count = 1 + (int) (ts.particleAmount() * progress);
            Location c = p.getLocation().add(0, 1, 0);
            p.getWorld().spawnParticle(ts.particleType(), c, count, 0.4, 0.6, 0.4, 0.0, ts.particleData());
        }
        if (ts.sound() && t == 1) {
            // The vanilla trigger sound swells over about four seconds, matching the default delay
            p.playSound(p.getLocation(), Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.0f);
        }
        if (ts.bar()) {
            int filled = (int) Math.round(progress * 10);
            String bar = "■".repeat(filled) + "□".repeat(10 - filled);
            p.sendActionBar(plugin.msg("travel-bar", "%bar%", bar));
        }
    }

    /** Removes everything showWaiting() applied, whether the player travels or walks out. */
    private void endWaiting(Player p) {
        CraftedPortals.TravelSettings ts = plugin.travelSettings();
        Hidden h = hiddenInterior.remove(p.getUniqueId());
        if (h != null) revealAll(p, h);
        switch (ts.effect()) {
            case NAUSEA -> p.removePotionEffect(PotionEffectType.NAUSEA);
            case DARKNESS -> p.removePotionEffect(PotionEffectType.DARKNESS);
            case FROST -> p.setFreezeTicks(0);
            case NONE -> { }
        }
        if (ts.sound()) p.stopSound(Sound.BLOCK_PORTAL_TRIGGER);
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
                p.sendActionBar(plugin.portalMsg("arrived", to));
                hideArrivalPortal(p, to);
                for (Mob m : leashed) {
                    m.teleportAsync(dest, PlayerTeleportEvent.TeleportCause.PLUGIN).thenAccept(ok2 ->
                            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                                if (m.isValid() && p.isOnline()) m.setLeashHolder(p);
                            }, 2L));
                }
            }
        });
    }

    // ------------------------------------------------------------ Arrival portal overlay

    /**
     * The client renders the purple portal overlay on its own as soon as its bounding box touches
     * nether portal blocks; vanilla avoids that after a portal trip with a client-side cooldown we
     * cannot set. So, until the arriving player steps out (justArrived cleared), the one or two
     * portal blocks they are standing in are sent to them as air, every tick following their
     * position. The rest of the portal keeps its texture, and the blocks they left behind pop back.
     * The same mechanism hides the source portal's blocks while a player waits inside it.
     */
    private void hideArrivalPortal(Player p, Portal to) {
        if (plugin.effect() != CraftedPortals.Effect.NETHER) return;
        Hidden old = hiddenInterior.put(p.getUniqueId(), new Hidden(to, new HashSet<>()));
        if (old != null) revealAll(p, old);
    }

    /** Hidden cells follow the player; every second all hidden cells are re-sent in case chunk data overwrote them. */
    private void updateHiddenCells() {
        if (hiddenInterior.isEmpty()) return;
        boolean refresh = tick % 20 == 0;
        for (Map.Entry<UUID, Hidden> en : hiddenInterior.entrySet()) {
            Player p = plugin.getServer().getPlayer(en.getKey());
            Hidden h = en.getValue();
            World w = h.portal().world();
            if (p == null || w == null || !p.getWorld().equals(w)) continue;
            // Small margin so a cell is hidden slightly before the client would touch it
            BoundingBox bb = p.getBoundingBox().expand(0.15);
            for (Block b : h.portal().interior(w)) {
                long key = FrameDetector.key(b.getX(), b.getY(), b.getZ());
                boolean touch = bb.overlaps(b.getBoundingBox());
                boolean hidden = h.cells().contains(key);
                if (touch && (!hidden || refresh)) {
                    p.sendBlockChange(b.getLocation(), Material.AIR.createBlockData());
                    h.cells().add(key);
                } else if (!touch && hidden) {
                    p.sendBlockChange(b.getLocation(), b.getBlockData());
                    h.cells().remove(key);
                }
            }
        }
    }

    private void revealAll(Player p, Hidden h) {
        World w = h.portal().world();
        if (w == null || !p.getWorld().equals(w)) return;
        for (Block b : h.portal().interior(w)) {
            if (h.cells().contains(FrameDetector.key(b.getX(), b.getY(), b.getZ()))) {
                p.sendBlockChange(b.getLocation(), b.getBlockData());
            }
        }
    }

    private static Entity safeHolder(Mob m) {
        try {
            return m.getLeashHolder();
        } catch (IllegalStateException ex) {
            return null;
        }
    }
}
