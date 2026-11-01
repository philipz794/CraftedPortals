package io.github.philipz794.craftedportals;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * A configurable sound: the option may be {@code true} (default sound), {@code false} (off), a game
 * sound name such as BLOCK_BEACON_AMBIENT, or a raw key such as {@code minecraft:block.portal.ambient}
 * or {@code mypack:portal_hum} from a resource pack. Volume and pitch come from {@code <option>-volume}
 * and {@code <option>-pitch} next to it.
 *
 * @param key namespaced sound key as the client expects it, or null when disabled
 */
record SoundSpec(String key, float volume, float pitch) {

    boolean enabled() { return key != null; }

    void play(World w, Location at, float pitchFactor) {
        if (key != null) w.playSound(at, key, volume, pitch * pitchFactor);
    }

    void play(Player p, Location at) {
        if (key != null) p.playSound(at, key, volume, pitch);
    }

    void stop(Player p) {
        if (key != null) p.stopSound(key);
    }

    /**
     * @param sec         section holding the option (the root config for top-level options)
     * @param option      option name, e.g. "sound" or "travel-sound"
     * @param defaultName game sound used for {@code true} or when the option is missing
     */
    static SoundSpec load(ConfigurationSection sec, String option, String defaultName, float defaultVolume,
                          String path, Consumer<String> warn) {
        Object raw = sec == null ? null : sec.get(option);
        String key;
        if (raw == null || Boolean.TRUE.equals(raw)) key = resolve(defaultName, path, warn, null);
        else if (Boolean.FALSE.equals(raw)) key = null;
        else {
            String name = String.valueOf(raw).trim();
            key = name.isEmpty() || name.equalsIgnoreCase("none") || name.equalsIgnoreCase("off")
                    ? null : resolve(name, path, warn, defaultName);
        }
        float volume = sec == null ? defaultVolume : (float) sec.getDouble(option + "-volume", defaultVolume);
        float pitch = sec == null ? 1.0f : (float) sec.getDouble(option + "-pitch", 1.0);
        return new SoundSpec(key, Math.max(0f, volume), Math.max(0.5f, Math.min(2.0f, pitch)));
    }

    /** Game names (BLOCK_PORTAL_AMBIENT) become keys; anything with ':' or '.' is taken as a raw key. */
    private static String resolve(String name, String path, Consumer<String> warn, String fallback) {
        if (name.contains(":") || name.contains(".")) return name.toLowerCase(Locale.ROOT);
        try {
            return Sound.valueOf(name.toUpperCase(Locale.ROOT)).getKey().asString();
        } catch (IllegalArgumentException ex) {
            if (fallback == null) return null;
            warn.accept("Unknown sound '" + name + "' for " + path + ", using " + fallback);
            return resolve(fallback, path, warn, null);
        }
    }
}
