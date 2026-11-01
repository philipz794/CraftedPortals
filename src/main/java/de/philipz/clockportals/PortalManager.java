package de.philipz.clockportals;

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

/** Verwaltet alle Portale, Netzwerke und die Speicherung in portals.yml. */
final class PortalManager {

    private final ClockPortals plugin;
    private final File file;

    /** Netzwerk-Schluessel -> Portale in Reihenfolge. */
    private final Map<String, List<Portal>> networks = new LinkedHashMap<>();
    /** Weltname -> Blockschluessel -> Portal (Innenraum). */
    private final Map<String, Map<Long, Portal>> interiorIndex = new HashMap<>();
    /** Weltname -> Blockschluessel -> Portal (Rahmen). */
    private final Map<String, Map<Long, Portal>> frameIndex = new HashMap<>();

    PortalManager(ClockPortals plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "portals.yml");
    }

    // ------------------------------------------------------------ Abfragen

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

    /** Naechstes Portal im Netzwerk (im Kreis) oder null. */
    Portal next(Portal p) {
        List<Portal> list = networks.get(p.networkKey);
        if (list == null || list.size() < 2) return null;
        int i = list.indexOf(p);
        return list.get((i + 1) % list.size());
    }

    // ------------------------------------------------------------ Aendern

    Portal create(String name, World w, FrameDetector.Result r) {
        String key = name.toLowerCase();
        Portal p = new Portal(key, name, w.getName(), r.axis(),
                r.minX(), r.minY(), r.minZ(), r.maxX(), r.maxY(), r.maxZ());
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

    /** Fuellt den Innenraum mit Portalbloecken (ohne Physik-Updates). */
    void fill(Portal p) {
        World w = p.world();
        if (w == null) return;
        boolean end = plugin.useEndEffect();
        for (Block b : p.interior(w)) {
            if (end) {
                if (b.getType() != Material.END_GATEWAY) b.setType(Material.END_GATEWAY, false);
                BlockState st = b.getState();
                if (st instanceof EndGateway gw) {
                    // Ausgang auf sich selbst -> Vanilla teleportiert nirgendwohin
                    // und generiert im End keine Inseln/Gateways.
                    gw.setExitLocation(b.getLocation());
                    gw.setExactTeleport(true);
                    gw.setAge(Long.MAX_VALUE / 2); // kein Lichtstrahl
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

    // ------------------------------------------------------------ Speichern

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
                    Portal p = new Portal(key, name, (String) m.get("world"),
                            Axis.valueOf((String) m.get("axis")),
                            ((Number) m.get("minX")).intValue(), ((Number) m.get("minY")).intValue(),
                            ((Number) m.get("minZ")).intValue(), ((Number) m.get("maxX")).intValue(),
                            ((Number) m.get("maxY")).intValue(), ((Number) m.get("maxZ")).intValue());
                    list.add(p);
                    n++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Fehlerhafter Portaleintrag in Netzwerk " + key, e);
                }
            }
            if (!list.isEmpty()) networks.put(key, list);
        }
        for (Portal p : all()) index(p);
        plugin.getLogger().info(n + " Portal(e) in " + networks.size() + " Netzwerk(en) geladen.");
    }

    /** Index fuer Portale nachholen, deren Welt erst spaeter geladen wurde. */
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
                list.add(m);
            }
            yml.set(base + ".name", e.getValue().get(0).displayName);
            yml.set(base + ".portals", list);
        }
        try {
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "portals.yml konnte nicht gespeichert werden", ex);
        }
    }

    /** Ist die Location (Fuesse) in irgendeinem Portal-Innenraum? */
    Portal portalAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return byInterior(loc.getBlock());
    }
}
