package io.github.philipz794.craftedportals;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Translucent coloured sheet for the PARTICLE effect, built from text display entities with an
 * empty text and a coloured background. One display per rectangle of the interior, so a plain
 * rectangular portal is a single quad without seams. Java clients only; Geyser does not render
 * scaled display backgrounds, Bedrock players see the particles.
 *
 * Geometry (measured in game): 40 text pixels per block; the text is bottom-anchored at the entity
 * position and centred horizontally. The background of a one-line text of width k is (k + BG_PAD) px
 * wide and BG_HEIGHT px high. A text of SPACES spaces is 4 * SPACES px wide. The quad is back-face
 * culled, so every rectangle gets two displays facing opposite directions. If the sheet shows gaps or
 * overlaps in game, BG_PAD / BG_HEIGHT are the knobs to tune.
 */
final class SheetRenderer {

    /** Scoreboard tag so stray displays from a crash can be found and removed. */
    static final String TAG = "craftedportals_sheet";

    private static final int SPACES = 10;
    private static final float PX_PER_BLOCK = 40f;
    private static final float BG_PAD = 1f;
    private static final float BG_HEIGHT = 10f;  // measured: an 11 left the top 1/11 of the sheet empty

    private final CraftedPortals plugin;
    private final Map<Portal, List<TextDisplay>> sheets = new HashMap<>();

    SheetRenderer(CraftedPortals plugin) {
        this.plugin = plugin;
    }

    /** Creates the sheet for a portal (or refreshes its colour) if the effect asks for one; removes it otherwise. */
    void update(Portal p, Color color, int opacityPercent, boolean wanted) {
        if (!wanted) { remove(p); return; }
        World w = p.world();
        if (w == null || !p.isLoaded()) return;
        List<TextDisplay> list = sheets.get(p);
        Color argb = Color.fromARGB(Math.round(255 * opacityPercent / 100f), color.getRed(), color.getGreen(), color.getBlue());
        if (list != null && !list.isEmpty() && list.stream().allMatch(Entity::isValid)) {
            for (TextDisplay d : list) if (!argb.equals(d.getBackgroundColor())) d.setBackgroundColor(argb);
            return;
        }
        remove(p);
        list = new ArrayList<>();
        List<int[]> rects = p.rectangles();
        try {
            for (int[] r : rects) {
                list.add(spawn(w, p, r, argb, false));
                list.add(spawn(w, p, r, argb, true)); // back side
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not spawn the colour sheet for portal "
                    + p.displayName + " at " + p.minX + " " + p.minY + " " + p.minZ, ex);
            sheets.put(p, list); // keep what we have; retried on the next maintain run
            return;
        }
        long invalid = list.stream().filter(d -> !d.isValid()).count();
        if (rects.isEmpty()) {
            if (!logged.contains(p)) {
                plugin.getLogger().warning("Colour sheet for portal " + p.displayName + ": interior has " + p.cells()
                        + " cells but no rectangles could be derived; please report this");
                logged.add(p);
            }
        } else if (invalid > 0) {
            plugin.getLogger().warning("Colour sheet for portal " + p.displayName + ": " + invalid + " of " + list.size()
                    + " display entities were not added to the world (spawn cancelled by the server or another plugin?)");
        } else if (!logged.contains(p)) {
            plugin.getLogger().info("Colour sheet for portal " + p.displayName + ": " + list.size() + " display entit"
                    + (list.size() == 1 ? "y" : "ies") + " for " + rects.size() + " rectangle(s) at "
                    + p.minX + " " + p.minY + " " + p.minZ + " (" + p.cells() + " cells)");
            logged.add(p);
        }
        sheets.put(p, list);
    }

    /** Portals whose sheet creation has been logged once, to keep the console quiet. */
    private final java.util.Set<Portal> logged = new java.util.HashSet<>();

    private TextDisplay spawn(World w, Portal p, int[] r, Color argb, boolean back) {
        int u0 = r[0], u1 = r[1], y0 = r[2], y1 = r[3];
        float width = u1 - u0 + 1, height = y1 - y0 + 1;
        double uCentre = (u0 + u1 + 1) / 2.0;
        boolean axisX = p.axis == org.bukkit.Axis.X;
        Location loc = axisX ? new Location(w, uCentre, y0, p.minZ + 0.5) : new Location(w, p.minX + 0.5, y0, uCentre);
        return w.spawn(loc, TextDisplay.class, d -> {
            d.text(Component.text(" ".repeat(SPACES)));
            d.setBackgroundColor(argb);
            d.setDefaultBackground(false);
            d.setShadowed(false);
            d.setSeeThrough(false);
            d.setTextOpacity((byte) 4); // text itself invisible
            d.setBillboard(Display.Billboard.FIXED);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(1.0f);
            d.setPersistent(false);
            d.addScoreboardTag(TAG);
            float sx = PX_PER_BLOCK * width / (4 * SPACES + BG_PAD);
            float sy = PX_PER_BLOCK * height / BG_HEIGHT;
            // A text display lies in the X-Y plane and is visible from one side only; turn it for
            // Z-axis portals and add 180 degrees for the back-side copy.
            float deg = (axisX ? 0f : 90f) + (back ? 180f : 0f);
            Quaternionf left = new Quaternionf(new AxisAngle4f((float) Math.toRadians(deg), 0, 1, 0));
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), left, new Vector3f(sx, sy, 1f), new Quaternionf()));
        });
    }

    /** Forget a portal entirely (destroyed); its sheet creation may be logged again if it comes back. */
    void forget(Portal p) {
        remove(p);
        logged.remove(p);
    }

    void remove(Portal p) {
        List<TextDisplay> list = sheets.remove(p);
        if (list != null) for (TextDisplay d : list) if (d.isValid()) d.remove();
    }

    void removeAll() {
        for (Portal p : new ArrayList<>(sheets.keySet())) remove(p);
    }

    /** Removes displays left behind by a crash or a previous plugin version in all loaded worlds. */
    void removeStrays() {
        for (World w : plugin.getServer().getWorlds()) {
            for (TextDisplay d : w.getEntitiesByClass(TextDisplay.class)) {
                if (d.getScoreboardTags().contains(TAG)) d.remove();
            }
        }
    }
}
