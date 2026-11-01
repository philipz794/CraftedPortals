package io.github.philipz794.craftedportals;

import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.VoxelShape;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Detects a valid portal frame starting from the clicked block. The interior may have
 * any closed shape: the air in the portal plane is flood-filled from a neighbour of the
 * clicked block, and every in-plane neighbour of that air must be a full block.
 */
final class FrameDetector {

    /** Absolute upper bound for the configurable interior size (cells of air). */
    static final int HARD_MAX_BLOCKS = 1000;
    /** Absolute upper bound for width and height of the interior, not configurable. */
    static final int MAX_SPAN = 64;

    /** Ordered by priority: when several seeds fail, the highest error wins. */
    enum Error { NONE, NO_FRAME, TOO_SMALL, TOO_BIG, MISSING_KEYS }

    record Result(Error error, Axis axis, int minX, int minY, int minZ,
                  int maxX, int maxY, int maxZ, boolean[] mask, int cells, int keysFound) {
        boolean ok() { return error == Error.NONE; }
    }

    private final Material keyBlock;
    private final int keyCount;
    private final int maxBlocks;

    FrameDetector(Material keyBlock, int keyCount, int maxBlocks) {
        this.keyBlock = keyBlock;
        this.keyCount = keyCount;
        this.maxBlocks = Math.min(HARD_MAX_BLOCKS, Math.max(2, maxBlocks));
    }

    int maxBlocks() { return maxBlocks; }

    /** Blocks at least this high (in blocks) count as frame blocks: soul sand and mud are 14/16, end portal frames 13/16. */
    private static final double MIN_FRAME_HEIGHT = 0.8;

    /**
     * A block that reads as a solid cube: one collision box covering the whole footprint from the
     * ground up to at least MIN_FRAME_HEIGHT. Accepts full blocks plus soul sand, mud, farmland, dirt
     * paths and end portal frames; rejects slabs, stairs, fences, walls, panes, snow layers, chests,
     * enchanting tables and everything with several boxes.
     */
    static boolean isFullBlock(Block b) {
        Material m = b.getType();
        if (!m.isBlock() || !m.isSolid() || b.isLiquid()) return false;
        if (m == Material.NETHER_PORTAL || m == Material.END_GATEWAY) return false;
        VoxelShape shape = b.getCollisionShape();
        Collection<BoundingBox> boxes = shape.getBoundingBoxes();
        if (boxes.size() != 1) return false;
        BoundingBox bb = boxes.iterator().next();
        return bb.getMinX() <= 0.001 && bb.getMinY() <= 0.001 && bb.getMinZ() <= 0.001
                && bb.getMaxX() >= 0.999 && bb.getMaxY() >= MIN_FRAME_HEIGHT && bb.getMaxZ() >= 0.999;
    }

    Result detect(Block clicked) {
        Result best = fail(Error.NO_FRAME);
        for (Axis axis : new Axis[]{Axis.X, Axis.Z}) {
            int dx = axis == Axis.X ? 1 : 0;
            int dz = axis == Axis.Z ? 1 : 0;
            Block[] seeds = {
                    clicked.getRelative(0, 1, 0), clicked.getRelative(0, -1, 0),
                    clicked.getRelative(dx, 0, dz), clicked.getRelative(-dx, 0, -dz),
                    clicked.getRelative(dx, 1, dz), clicked.getRelative(-dx, 1, -dz),
                    clicked.getRelative(dx, -1, dz), clicked.getRelative(-dx, -1, -dz)
            };
            for (Block seed : seeds) {
                if (!seed.getType().isAir()) continue;
                Result r = tryFrom(clicked, seed, axis);
                if (r.ok()) return r;
                if (r.error.ordinal() > best.error.ordinal()) best = r;
            }
        }
        return best;
    }

    private Result tryFrom(Block clicked, Block seed, Axis axis) {
        World w = seed.getWorld();
        int dx = axis == Axis.X ? 1 : 0;
        int dz = axis == Axis.Z ? 1 : 0;

        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        queue.add(new int[]{seed.getX(), seed.getY(), seed.getZ()});
        seen.add(key(seed.getX(), seed.getY(), seed.getZ()));
        int minX = seed.getX(), maxX = seed.getX(), minY = seed.getY(), maxY = seed.getY(),
                minZ = seed.getZ(), maxZ = seed.getZ();
        int minWorldY = w.getMinHeight(), maxWorldY = w.getMaxHeight() - 1;

        while (!queue.isEmpty()) {
            int[] c = queue.poll();
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
            minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
            // Air "leaks out": frame is open or absurdly large -> no frame at all
            if (seen.size() > HARD_MAX_BLOCKS || maxX - minX >= MAX_SPAN || maxZ - minZ >= MAX_SPAN
                    || maxY - minY >= MAX_SPAN) return fail(Error.NO_FRAME);
            int[][] next = {
                    {c[0], c[1] + 1, c[2]}, {c[0], c[1] - 1, c[2]},
                    {c[0] + dx, c[1], c[2] + dz}, {c[0] - dx, c[1], c[2] - dz}
            };
            for (int[] n : next) {
                if (n[1] < minWorldY || n[1] > maxWorldY) return fail(Error.NO_FRAME);
                long k = key(n[0], n[1], n[2]);
                if (seen.contains(k)) continue;
                if (!w.getBlockAt(n[0], n[1], n[2]).getType().isAir()) continue;
                seen.add(k);
                queue.add(n);
            }
        }

        // Closed region found. Build the mask over its bounding box.
        int minU = axis == Axis.X ? minX : minZ;
        int width = (axis == Axis.X ? maxX - minX : maxZ - minZ) + 1;
        int height = maxY - minY + 1;
        boolean[] mask = new boolean[width * height];
        boolean column2 = false;
        for (int u = 0; u < width; u++) {
            for (int y = 0; y < height; y++) {
                int wx = axis == Axis.X ? minU + u : minX, wz = axis == Axis.X ? minZ : minU + u;
                boolean in = seen.contains(key(wx, minY + y, wz));
                mask[u * height + y] = in;
                if (in && y > 0 && mask[u * height + y - 1]) column2 = true;
            }
        }
        int cells = seen.size();
        if (!column2) return fail(Error.TOO_SMALL);
        if (cells > maxBlocks) return fail(Error.TOO_BIG);

        Portal tmp = new Portal("", "", w.getName(), axis, minX, minY, minZ, maxX, maxY, maxZ, mask, null);
        boolean clickedIsFrame = false;
        int keys = 0;
        for (Block f : tmp.frame(w)) {
            boolean corner = tmp.isCorner(f);
            boolean full = isFullBlock(f);
            if (!corner && !full) return fail(Error.NO_FRAME);
            if (full && f.getType() == keyBlock) keys++;
            if (f.equals(clicked) && (full || !corner)) clickedIsFrame = true;
        }
        if (!clickedIsFrame) return fail(Error.NO_FRAME);
        Error err = keys < keyCount ? Error.MISSING_KEYS : Error.NONE;
        return new Result(err, axis, minX, minY, minZ, maxX, maxY, maxZ, mask, cells, keys);
    }

    private static Result fail(Error e) {
        return new Result(e, null, 0, 0, 0, 0, 0, 0, null, 0, 0);
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) y & 0xFFFL) << 52);
    }
}
