package io.github.philipz794.clockportals;

import org.bukkit.Axis;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.EndGateway;
import org.bukkit.block.data.Orientable;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Manages all portals, networks and persistence in portals.yml. */
final class PortalManager {

    private final ClockPortals plugin;
    private final File file;

    /** Network key -> portals in order. */
    private final Map<String, List<Portal>> networks = new LinkedHashMap<>();
    /** World name -> block key -> portal (interior). */
    private final Map<String, Map<Long, Portal>> interiorIndex = new HashMap<>();
    /** World name -> block key -> portal (frame). */
    private final Map<String, Map<Long, Portal>> frameIndex = new HashMap<>();

    PortalManager(ClockPortals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    // ------------------------------------------------------------ Queries

    Portal byInterior(Block b) {
        Map<Long, Portal> m = interiorIndex.get(b.getWorld().getName());
        return m == null ? null : m.get(FrameDetector.key(b.getX(), b.getY(), b.getZ()));
    }

    Portal byFrame(Block b) {
        Map<Long, Portal> m = frameIndex.get(b.getWorld().getName());
        return m == null ? null : m.get(FrameDetector.key(b.getX(), b.getY(), b.getZ()));
    }

    Portal byAny(Block b) {
        Portal p = byInterior(b);
        return p != null ? p : byFrame(b);
    }

    Collection<List<Portal>> networks() {
        return networks.values();
    }

    List<Portal> all() {
        List<Portal> list = new ArrayList<>();
        networks.values().forEach(list::addAll);
        return list;
    }

    int position(Portal p) {
        return networks.get(p.networkKey).indexOf(p) + 1;
    }

    int count(Portal p) {
        return networks.get(p.networkKey).size();
    }

    /** Next portal in the network (cyclic) or null. */
    Portal next(Portal p) {
        List<Portal> list = networks.get(p.networkKey);
        if (list == null || list.size() < 2) return null;
        int i = list.indexOf(p);
        return list.get((i + 1) % list.size());
    }

    // ------------------------------------------------------------ Mutation

    Portal create(String name, World w, FrameDetector.Result r) {
        String key = name.toLowerCase();
        Portal p = new Portal(key, name, w.getName(), r.axis(),
                r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ(), r.mask());
        networks.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        index(p);
        fill(p);
        save();
        return p;
    }

    void destroy(Portal p) {
        List<Portal> list = networks.get(p.networkKey);
        if (list != null) {
            list.remove(p);
            if (list.isEmpty()) networks.remove(p.networkKey);
        }
        unindex(p);
        World w = p.world();
        if (w != null) {
            for (Block b : p.interior(w)) {
                Material t = b.getType();
                if (t == Material.NETHER_PORTAL || t == Material.END_GATEWAY) {
                    b.setType(Material.AIR, false);
                }
            }
        }
        save();
    }

    /** Fills the interior with portal blocks (without physics updates). */
    void fill(Portal p) {
        World w = p.world();
        if (w == null) return;
        boolean end = plugin.useEndEffect();
        for (Block b : p.interior(w)) {
            if (end) {
                if (b.getType() != Material.END_GATEWAY) b.setType(Material.END_GATEWAY, false);
                BlockState st = b.getState();
                if (st instanceof EndGateway gw) {
                    // Exit points to itself -> vanilla teleports nowhere
                    // and does not generate islands/gateways in the End.
                    gw.setExitLocation(b.getLocation());
                    gw.setExactTeleport(true);
                    gw.setAge(Long.MAX_VALUE / 2); // no beam
                    gw.update(true, false);
                }
            } else {
                Orientable data = (Orientable) Material.NETHER_PORTAL.createBlockData();
                data.setAxis(p.axis == Axis.X ? Axis.X : Axis.Z);
                b.setBlockData(data, false);
            }
        }
    }

    private void index(Portal p) {
        World w = p.world();
        if (w == null) return;
        Map<Long, Portal> in = interiorIndex.computeIfAbsent(p.worldName, k -> new HashMap<>());
        for (Block b : p.interior(w)) in.put(FrameDetector.key(b.getX(), b.getY(), b.getZ()), p);
        Map<Long, Portal> fr = frameIndex.computeIfAbsent(p.worldName, k -> new HashMap<>());
        for (Block b : p.frame(w)) fr.put(FrameDetector.key(b.getX(), b.getY(), b.getZ()), p);
    }

    private void unindex(Portal p) {
        Map<Long, Portal> in = interiorIndex.get(p.worldName);
        if (in != null) in.values().removeIf(x -> x == p);
        Map<Long, Portal> fr = frameIndex.get(p.worldName);
        if (fr != null) fr.values().removeIf(x -> x == p);
    }

    // ------------------------------------------------------------ Persistence

    void load() {
        networks.clear();
        interiorIndex.clear();
        frameIndex.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection nets = yml.getConfigurationSection("networks");
        if (nets == null) return;
        int n = 0;
        for (String key : nets.getKeys(false)) {
            ConfigurationSection net = nets.getConfigurationSection(key);
            if (net == null) continue;
            String name = net.getString("name", key);
            List<Portal> list = new ArrayList<>();
            for (Map<?, ?> m : net.getMapList("portals")) {
                try {
                    Axis axis = Axis.valueOf((String) m.get("axis"));
                    int minX = ((Number) m.get("minX")).intValue(), minY = ((Number) m.get("minY")).intValue(),
                            minZ = ((Number) m.get("minZ")).intValue(), maxX = ((Number) m.get("maxX")).intValue(),
                            maxY = ((Number) m.get("maxY")).intValue(), maxZ = ((Number) m.get("maxZ")).intValue();
                    int width = (axis == Axis.X ? maxX - minX : maxZ - minZ) + 1;
                    // "cells" is absent for rectangles and for entries written by older versions
                    boolean[] mask = null;
                    if (m.get("cells") instanceof List<?> rows) {
                        List<String> list2 = new ArrayList<>();
                        for (Object o : rows) list2.add(String.valueOf(o));
                        mask = Portal.parseMask(list2, width, maxY - minY + 1);
                        if (mask == null) plugin.getLogger().warning("Ignoring unreadable cell mask of a portal in network " + key);
                    }
                    Portal p = new Portal(key, name, (String) m.get("world"), axis,
                            minX, minY, minZ, maxX, maxY, maxZ, mask);
                    list.add(p);
                    n++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid portal entry in network " + key, e);
                }
            }
            if (!list.isEmpty()) networks.put(key, list);
        }
        for (Portal p : all()) index(p);
        plugin.getLogger().info(n + " portal(s) in " + networks.size() + " network(s) loaded.");
    }

    /** Build the index for portals whose world was loaded later. */
    void reindexWorld(World w) {
        for (Portal p : all()) if (p.worldName.equals(w.getName())) index(p);
    }

    void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Map.Entry<String, List<Portal>> e : networks.entrySet()) {
            String base = "networks." + e.getKey();
            List<Map<String, Object>> list = new ArrayList<>();
            for (Portal p : e.getValue()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("world", p.worldName);
                m.put("axis", p.axis.name());
                m.put("minX", p.minX); m.put("minY", p.minY); m.put("minZ", p.minZ);
                m.put("maxX", p.maxX); m.put("maxY", p.maxY); m.put("maxZ", p.maxZ);
                if (!p.isRectangle()) m.put("cells", p.maskRows());
                list.add(m);
            }
            yml.set(base + ".name", e.getValue().get(0).displayName);
            yml.set(base + ".portals", list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save portals.yml", ex);
        }
    }

    /** Is the location (feet) inside any portal interior? */
    Portal portalAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return byInterior(loc.getBlock());
    }
}
