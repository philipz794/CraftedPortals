package de.philipz.clockportals;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Ein aktives Portal. Gespeichert wird nur der Innenraum (min/max inklusive),
 * der Rahmen liegt jeweils einen Block drumherum.
 *
 * axis = X: Portal liegt in der X-Y-Ebene (z konstant), man laeuft in Z-Richtung hindurch.
 * axis = Z: Portal liegt in der Z-Y-Ebene (x konstant), man laeuft in X-Richtung hindurch.
 */
public final class Portal {

    final String networkKey;   // Kleinbuchstaben, zum Vergleichen
    String displayName;        // Name wie auf der Uhr
    final String worldName;
    final Axis axis;
    final int minX, minY, minZ, maxX, maxY, maxZ; // Innenraum, inklusive

    Portal(String networkKey, String displayName, String worldName, Axis axis,
           int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.networkKey = networkKey;
        this.displayName = displayName;
        this.worldName = worldName;
        this.axis = axis;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
    }

    World world() {
        return Bukkit.getWorld(worldName);
    }

    boolean isLoaded() {
        World w = world();
        if (w == null) return false;
        int x0 = (minX - 1) >> 4, x1 = (maxX + 1) >> 4, z0 = (minZ - 1) >> 4, z1 = (maxZ + 1) >> 4;
        for (int cx = x0; cx <= x1; cx++)
            for (int cz = z0; cz <= z1; cz++)
                if (!w.isChunkLoaded(cx, cz)) return false;
        return true;
    }

    /** Alle Bloecke des Innenraums. */
    List<Block> interior(World w) {
        List<Block> list = new ArrayList<>();
        for (int x = minX; x <= maxX; x++)
            for (int y = minY; y <= maxY; y++)
                for (int z = minZ; z <= maxZ; z++)
                    list.add(w.getBlockAt(x, y, z));
        return list;
    }

    /** Alle Rahmenpositionen inkl. Ecken (Ecken koennen leer sein). */
    List<Block> frame(World w) {
        List<Block> list = new ArrayList<>();
        if (axis == Axis.X) {
            int z = minZ;
            for (int x = minX - 1; x <= maxX + 1; x++) {
                list.add(w.getBlockAt(x, minY - 1, z));
                list.add(w.getBlockAt(x, maxY + 1, z));
            }
            for (int y = minY; y <= maxY; y++) {
                list.add(w.getBlockAt(minX - 1, y, z));
                list.add(w.getBlockAt(maxX + 1, y, z));
            }
        } else {
            int x = minX;
            for (int z = minZ - 1; z <= maxZ + 1; z++) {
                list.add(w.getBlockAt(x, minY - 1, z));
                list.add(w.getBlockAt(x, maxY + 1, z));
            }
            for (int y = minY; y <= maxY; y++) {
                list.add(w.getBlockAt(x, y, minZ - 1));
                list.add(w.getBlockAt(x, y, maxZ + 1));
            }
        }
        return list;
    }

    boolean isCorner(Block b) {
        boolean yEdge = b.getY() == minY - 1 || b.getY() == maxY + 1;
        if (axis == Axis.X) return yEdge && (b.getX() == minX - 1 || b.getX() == maxX + 1);
        return yEdge && (b.getZ() == minZ - 1 || b.getZ() == maxZ + 1);
    }

    /** Bereich, in dem Entities als "im Portal" gelten. */
    BoundingBox box() {
        return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** Ankunftspunkt: unten mittig im Innenraum. */
    Location arrival(World w) {
        double cx = (minX + maxX + 1) / 2.0;
        double cz = (minZ + maxZ + 1) / 2.0;
        return new Location(w, cx, minY, cz);
    }
}
