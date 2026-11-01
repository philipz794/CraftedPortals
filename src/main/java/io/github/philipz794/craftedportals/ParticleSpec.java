package io.github.philipz794.craftedportals;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Vibration;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A particle type from the config together with whatever extra data that type needs, so that every
 * particle of the game can be used. Options per section: type, color, color-to, block, item, value.
 */
record ParticleSpec(Particle type, Color color, Color colorTo, BlockData block, ItemStack item, float value) {

    static final Color DEFAULT_COLOR = Color.fromRGB(0x9B30FF);

    /** Particle types whose look is driven by the configured colour (or the network colour). */
    static final Set<Particle> COLORABLE = Set.of(Particle.DUST, Particle.DUST_COLOR_TRANSITION, Particle.ENTITY_EFFECT,
            Particle.FLASH, Particle.TINTED_LEAVES, Particle.TRAIL, Particle.EFFECT, Particle.INSTANT_EFFECT);

    boolean colorable() { return COLORABLE.contains(type); }

    /** Same spec with another colour (network colour); only matters for colourable types. */
    ParticleSpec withColor(Color c) {
        return c == null ? this : new ParticleSpec(type, c, colorTo, block, item, value);
    }

    /**
     * The data object for spawnParticle. {@code target} is used by particles that travel somewhere
     * (TRAIL, VIBRATION): the portal centre or the waiting player.
     */
    Object data(Location target) {
        Class<?> t = type.getDataType();
        if (t == Void.class) return null;
        if (t == Particle.DustOptions.class) return new Particle.DustOptions(color, value);
        if (t == Particle.DustTransition.class) return new Particle.DustTransition(color, colorTo, value);
        if (t == Color.class) return color;
        if (t == Particle.Spell.class) return new Particle.Spell(color, value);
        if (t == BlockData.class) return block;
        if (t == ItemStack.class) return item;
        if (t == Float.class) return value;
        if (t == Integer.class) return Math.round(value);
        if (t == Particle.Trail.class) return new Particle.Trail(target, color, Math.max(1, Math.round(value * 20)));
        if (t == Vibration.class) return new Vibration(new Vibration.Destination.BlockDestination(target), Math.max(1, Math.round(value * 20)));
        if (t == Particle.Geyser.class) return new Particle.Geyser(Math.max(1, Math.round(value)));
        if (t == Particle.GeyserBase.class) return new Particle.GeyserBase(Math.max(1, Math.round(value)), 1.0f);
        return null;
    }

    /** True if this plugin knows how to build the data for the type. */
    static boolean supported(Particle p) {
        Class<?> t = p.getDataType();
        return t == Void.class || t == Particle.DustOptions.class || t == Particle.DustTransition.class
                || t == Color.class || t == Particle.Spell.class || t == BlockData.class || t == ItemStack.class
                || t == Float.class || t == Integer.class || t == Particle.Trail.class || t == Vibration.class
                || t == Particle.Geyser.class || t == Particle.GeyserBase.class;
    }

    /** Reads a spec from a config section; problems go to {@code warn} and fall back to safe values. */
    static ParticleSpec load(ConfigurationSection sec, String path, String defaultType, Consumer<String> warn) {
        String typeName = sec == null ? defaultType : sec.getString("type", defaultType);
        Particle type;
        try {
            type = Particle.valueOf(typeName.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ex) {
            warn.accept("Unknown " + path + ".type '" + typeName + "', using PORTAL");
            type = Particle.PORTAL;
        }
        if (!supported(type)) {
            warn.accept(path + ".type " + type + " needs data this plugin cannot provide, using PORTAL");
            type = Particle.PORTAL;
        }
        Color color = parseColor(sec == null ? null : sec.getString("color"), DEFAULT_COLOR, path + ".color", warn);
        Color colorTo = parseColor(sec == null ? null : sec.getString("color-to"), Color.WHITE, path + ".color-to", warn);
        float value = sec == null ? 1.0f : (float) sec.getDouble("value", 1.0);

        BlockData block = null;
        if (type.getDataType() == BlockData.class) {
            String name = sec == null ? "EMERALD_BLOCK" : sec.getString("block", "EMERALD_BLOCK");
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isBlock()) {
                warn.accept("Unknown " + path + ".block '" + name + "', using EMERALD_BLOCK");
                m = Material.EMERALD_BLOCK;
            }
            block = m.createBlockData();
        }
        ItemStack item = null;
        if (type.getDataType() == ItemStack.class) {
            String name = sec == null ? "EMERALD" : sec.getString("item", "EMERALD");
            Material m = Material.matchMaterial(name);
            if (m == null || !m.isItem() || m.isAir()) {
                warn.accept("Unknown " + path + ".item '" + name + "', using EMERALD");
                m = Material.EMERALD;
            }
            item = new ItemStack(m);
        }
        return new ParticleSpec(type, color, colorTo, block, item, value);
    }

    static Color parseColor(String hex, Color fallback, String path, Consumer<String> warn) {
        if (hex == null) return fallback;
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", "").trim(), 16));
        } catch (NumberFormatException ex) {
            warn.accept("Invalid " + path + " '" + hex + "', using default");
            return fallback;
        }
    }
}
