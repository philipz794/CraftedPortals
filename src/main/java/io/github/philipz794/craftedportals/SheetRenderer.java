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
 * Geometry (vanilla TextDisplayRenderer): 40 text pixels per block; the text is bottom-anchored at
 * the entity position and centred horizontally. The background quad spans from x = -1 to the text
 * width k and from y = -1 to lines * 10, i.e. (k + BG_PAD) x BG_HEIGHT pixels for one line. A text of
 * SPACES spaces is 4 * SPACES pixels wide. If the sheet shows gaps or overlaps in game, BG_PAD /
 * BG_HEIGHT are the knobs to tune.
 */
final class SheetRenderer {

    /** Scoreboard tag so stray displays from a crash can be found and removed. */
    static final String TAG = "craftedportals_sheet";

    private static final int SPACES = 10;
    private static final float PX_PER_BLOCK = 40f;
    private static final float BG_PAD = 1f;      // quad from x = -1 to x = k
    private static final float BG_HEIGHT = 11f;  // quad from y = -1 to y = 10 for one line

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
        for (int[] r : p.rectangles()) list.add(spawn(w, p, r, argb));
        sheets.put(p, list);
    }

    private TextDisplay spawn(World w, Portal p, int[] r, Color argb) {
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
            // A text display faces -Z and lies in the X-Y plane; turn it for Z-axis portals.
            Quaternionf left = axisX ? new Quaternionf() : new Quaternionf(new AxisAngle4f((float) Math.toRadians(90), 0, 1, 0));
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), left, new Vector3f(sx, sy, 1f), new Quaternionf()));
        });
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
