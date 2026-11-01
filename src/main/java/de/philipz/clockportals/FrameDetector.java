package de.philipz.clockportals;

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

/** Detects a valid portal frame starting from the clicked block. */
final class FrameDetector {

    enum Error { NONE, NO_FRAME, TOO_BIG, MISSING_KEYS }

    record Result(Error error, Axis axis, int minX, int minY, int minZ,
                  int maxX, int maxY, int maxZ, int keysFound) {
        boolean ok() { return error == Error.NONE; }
    }

    private final Material keyBlock;
    private final int keyCount;
    private final int maxSize;

    FrameDetector(Material keyBlock, int keyCount, int maxSize) {
        this.keyBlock = keyBlock;
        this.keyCount = keyCount;
        this.maxSize = Math.max(4, maxSize);
    }

    /** Full 1x1x1 block (no stairs, slabs, fences, snow ...). */
    static boolean isFullBlock(Block b) {
        Material m = b.getType();
        if (!m.isBlock() || !m.isSolid() || b.isLiquid()) return false;
        if (m == Material.NETHER_PORTAL || m == Material.END_GATEWAY) return false;
        VoxelShape shape = b.getCollisionShape();
        Collection<BoundingBox> boxes = shape.getBoundingBoxes();
        if (boxes.size() != 1) return false;
        BoundingBox bb = boxes.iterator().next();
        return bb.getMinX() <= 0.001 && bb.getMinY() <= 0.001 && bb.getMinZ() <= 0.001
                && bb.getMaxX() >= 0.999 && bb.getMaxY() >= 0.999 && bb.getMaxZ() >= 0.999;
    }

    Result detect(Block clicked) {
        Result best = new Result(Error.NO_FRAME, null, 0, 0, 0, 0, 0, 0, 0);
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
        int maxInner = maxSize - 2;
        int limit = maxInner * maxInner;
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
            // air "leaks out" -> frame is open or far too big
            if (seen.size() > limit) return fail(Error.NO_FRAME);
            minX = Math.min(minX, c[0]); maxX = Math.max(maxX, c[0]);
            minY = Math.min(minY, c[1]); maxY = Math.max(maxY, c[1]);
            minZ = Math.min(minZ, c[2]); maxZ = Math.max(maxZ, c[2]);
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

        int width = axis == Axis.X ? maxX - minX + 1 : maxZ - minZ + 1;
        int height = maxY - minY + 1;
        if (width > maxInner || height > maxInner) return fail(Error.TOO_BIG);
        if (width < 1 || height < 2) return fail(Error.NO_FRAME);
        if (seen.size() != width * height) return fail(Error.NO_FRAME); // not a rectangle

        Portal tmp = new Portal("", "", w.getName(), axis, minX, minY, minZ, maxX, maxY, maxZ);
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
        if (keys < keyCount)
            return new Result(Error.MISSING_KEYS, axis, minX, minY, minZ, maxX, maxY, maxZ, keys);
        return new Result(Error.NONE, axis, minX, minY, minZ, maxX, maxY, maxZ, keys);
    }

    private static Result fail(Error e) {
        return new Result(e, null, 0, 0, 0, 0, 0, 0, 0);
    }

    static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) y & 0xFFFL) << 52);
    }
}
