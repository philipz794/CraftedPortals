package io.github.philipz794.craftedportals;

import org.bukkit.Axis;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * An active portal. Stored are the bounding box of the interior (min/max inclusive)
 * and a mask that says which cells inside that box are actually interior, so the
 * portal may have any closed shape. The frame is derived: every in-plane neighbour
 * of an interior cell that is not interior itself. Cells touching the interior only
 * diagonally are "corners" and may be empty.
 *
 * axis = X: portal lies in the X-Y plane (constant z), you walk through along Z.
 * axis = Z: portal lies in the Z-Y plane (constant x), you walk through along X.
 */
public final class Portal {

    final String networkKey;   // lower-case, used for lookups
    String displayName;        // name exactly as written on the clock
    final String worldName;
    final Axis axis;
    final int minX, minY, minZ, maxX, maxY, maxZ; // interior bounding box, inclusive
    /** Interior mask, indexed by (u - minU) * height + (y - minY); u = x for axis X, z for axis Z. */
    private final boolean[] mask;

    Portal(String networkKey, String displayName, String worldName, Axis axis,
           int minX, int minY, int minZ, int maxX, int maxY, int maxZ, boolean[] mask) {
        this.networkKey = networkKey;
        this.displayName = displayName;
        this.worldName = worldName;
        this.axis = axis;
        this.minX = minX; this.minY = minY; this.minZ = minZ;
        this.maxX = maxX; this.maxY = maxY; this.maxZ = maxZ;
        int size = width() * height();
        if (mask == null || mask.length != size) {
            // Legacy entry or plain rectangle: every cell is interior
            this.mask = new boolean[size];
            Arrays.fill(this.mask, true);
        } else {
            this.mask = mask.clone();
        }
    }

    World world() {
        return Bukkit.getWorld(worldName);
    }

    // ------------------------------------------------------------ Geometry

    int minU() { return axis == Axis.X ? minX : minZ; }
    int maxU() { return axis == Axis.X ? maxX : maxZ; }
    int width() { return maxU() - minU() + 1; }
    int height() { return maxY - minY + 1; }

    /** Number of interior cells. */
    int cells() {
        int n = 0;
        for (boolean b : mask) if (b) n++;
        return n;
    }

    private int u(int x, int z) { return axis == Axis.X ? x : z; }

    /** Is this block an interior cell? */
    boolean contains(int x, int y, int z) {
        if (axis == Axis.X ? z != minZ : x != minX) return false;
        return containsUY(u(x, z), y);
    }

    private boolean containsUY(int u, int y) {
        if (u < minU() || u > maxU() || y < minY || y > maxY) return false;
        return mask[(u - minU()) * height() + (y - minY)];
    }

    private Block blockAt(World w, int u, int y) {
        return axis == Axis.X ? w.getBlockAt(u, y, minZ) : w.getBlockAt(minX, y, u);
    }

    /** Edge-neighbour of at least one interior cell (not interior itself)? */
    private boolean isEdge(int u, int y) {
        return !containsUY(u, y) && (containsUY(u + 1, y) || containsUY(u - 1, y)
                || containsUY(u, y + 1) || containsUY(u, y - 1));
    }

    /** Touches the interior only diagonally (not interior, not an edge neighbour)? */
    private boolean isCornerUY(int u, int y) {
        if (containsUY(u, y) || isEdge(u, y)) return false;
        return containsUY(u + 1, y + 1) || containsUY(u - 1, y + 1)
                || containsUY(u + 1, y - 1) || containsUY(u - 1, y - 1);
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

    /** All interior blocks. */
    List<Block> interior(World w) {
        List<Block> list = new ArrayList<>();
        for (int u = minU(); u <= maxU(); u++)
            for (int y = minY; y <= maxY; y++)
                if (containsUY(u, y)) list.add(blockAt(w, u, y));
        return list;
    }

    /** All frame positions including corners (corners may be empty). */
    List<Block> frame(World w) {
        List<Block> list = new ArrayList<>();
        for (int u = minU() - 1; u <= maxU() + 1; u++)
            for (int y = minY - 1; y <= maxY + 1; y++)
                if (isEdge(u, y) || isCornerUY(u, y)) list.add(blockAt(w, u, y));
        return list;
    }

    boolean isCorner(Block b) {
        if (axis == Axis.X ? b.getZ() != minZ : b.getX() != minX) return false;
        return isCornerUY(u(b.getX(), b.getZ()), b.getY());
    }

    /** Region in which entities may count as "inside the portal" (coarse check). */
    BoundingBox box() {
        return new BoundingBox(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1);
    }

    /** Does the entity bounding box overlap at least one interior cell? */
    boolean intersects(BoundingBox bb) {
        double minUd = axis == Axis.X ? bb.getMinX() : bb.getMinZ();
        double maxUd = axis == Axis.X ? bb.getMaxX() : bb.getMaxZ();
        int u0 = (int) Math.floor(minUd), u1 = (int) Math.floor(maxUd - 1e-6);
        int y0 = (int) Math.floor(bb.getMinY()), y1 = (int) Math.floor(bb.getMaxY() - 1e-6);
        for (int u = Math.max(u0, minU()); u <= Math.min(u1, maxU()); u++)
            for (int y = Math.max(y0, minY); y <= Math.min(y1, maxY); y++)
                if (containsUY(u, y)) return true;
        return false;
    }

    /**
     * Arrival point: the lowest two-block-high column closest to the centre of the shape,
     * standing in the middle of that block. For a rectangle this is the bottom centre.
     */
    Location arrival(World w) {
        double centre = (minU() + maxU()) / 2.0;
        int bestU = Integer.MIN_VALUE, bestY = Integer.MIN_VALUE;
        double bestDist = Double.MAX_VALUE;
        for (int u = minU(); u <= maxU(); u++) {
            for (int y = minY; y < maxY; y++) {
                if (containsUY(u, y) && containsUY(u, y + 1)) {
                    double d = Math.abs(u - centre);
                    if (d < bestDist - 1e-9) { bestDist = d; bestU = u; bestY = y; }
                    break; // lowest column of this u found
                }
            }
        }
        if (bestU == Integer.MIN_VALUE) { // should not happen: detector requires a 2-high column
            bestU = (int) Math.floor(centre);
            bestY = minY;
        }
        double x = axis == Axis.X ? bestU + 0.5 : minX + 0.5;
        double z = axis == Axis.X ? minZ + 0.5 : bestU + 0.5;
        return new Location(w, x, bestY, z);
    }

    /**
     * Greedy decomposition of the interior into rectangles {u0, u1, y0, y1} (inclusive, u = x or z
     * depending on the axis). Rows are split into runs; runs with identical u-range in consecutive rows
     * are merged. A plain rectangle yields one entry.
     */
    List<int[]> rectangles() {
        List<int[]> open = new ArrayList<>();   // rectangles that may still grow upwards
        List<int[]> done = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            List<int[]> runs = new ArrayList<>();
            int start = -1;
            for (int u = minU(); u <= maxU() + 1; u++) {
                boolean in = u <= maxU() && containsUY(u, y);
                if (in && start < 0) start = u;
                if (!in && start >= 0) { runs.add(new int[]{start, u - 1}); start = -1; }
            }
            List<int[]> stillOpen = new ArrayList<>();
            for (int[] run : runs) {
                int[] grown = null;
                for (int[] r : open) {
                    if (r[0] == run[0] && r[1] == run[1] && r[3] == y - 1) { grown = r; break; }
                }
                if (grown != null) { grown[3] = y; open.remove(grown); stillOpen.add(grown); }
                else stillOpen.add(new int[]{run[0], run[1], y, y});
            }
            done.addAll(open); // rectangles that did not continue into this row
            open = stillOpen;
        }
        done.addAll(open);
        return done;
    }

    // ------------------------------------------------------------ Serialisation

    /** Mask as rows from top (maxY) to bottom (minY), '#' = interior, '.' = not. */
    List<String> maskRows() {
        List<String> rows = new ArrayList<>();
        for (int y = maxY; y >= minY; y--) {
            StringBuilder sb = new StringBuilder();
            for (int u = minU(); u <= maxU(); u++) sb.append(containsUY(u, y) ? '#' : '.');
            rows.add(sb.toString());
        }
        return rows;
    }

    /** Inverse of {@link #maskRows()}; returns null if the rows do not fit the box. */
    static boolean[] parseMask(List<String> rows, int width, int height) {
        if (rows == null || rows.size() != height) return null;
        boolean[] m = new boolean[width * height];
        for (int r = 0; r < height; r++) {
            String row = rows.get(r);
            if (row == null || row.length() != width) return null;
            int y = height - 1 - r;
            for (int u = 0; u < width; u++) m[u * height + y] = row.charAt(u) == '#';
        }
        return m;
    }

    /** Is the mask a full rectangle? Then it need not be stored. */
    boolean isRectangle() {
        for (boolean b : mask) if (!b) return false;
        return true;
    }
}
