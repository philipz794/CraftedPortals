package io.github.philipz794.craftedportals;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Brings an existing config.yml up to date after a plugin update without touching the
 * values the server owner has set. Options missing from the file are appended together
 * with the comment block from the bundled template; missing keys inside an existing
 * section (e.g. messages) are inserted at the end of that section. Renamed options are
 * converted in place. Everything else, including the owner's comments, is left alone.
 */
final class ConfigUpdater {

    /** Old option name -> new option name. The value is converted by {@link #convert}. */
    private static final Map<String, String> RENAMED = Map.of(
            "max-size", "max-blocks",
            "consume-clock", "consume-item");

    /** Options that no longer exist; their line and comment block are deleted from the owner's file. */
    private static final java.util.Set<String> REMOVED = java.util.Set.of("travel-nausea-peak");

    private static final Pattern TOP_KEY = Pattern.compile("^([A-Za-z0-9_-]+):(.*)$");
    private static final Pattern SUB_KEY = Pattern.compile("^(\\s+)([A-Za-z0-9_-]+):(.*)$");

    private ConfigUpdater() {}

    record Result(List<String> lines, List<String> added, List<String> renamed, List<String> removed) {
        boolean changed() { return !added.isEmpty() || !renamed.isEmpty() || !removed.isEmpty(); }
    }

    /** Updates plugins/<name>/config.yml from the bundled config.yml; returns what was changed. */
    static Result update(JavaPlugin plugin) throws IOException {
        File file = new File(plugin.getDataFolder(), "config.yml");
        InputStream in = plugin.getResource("config.yml");
        if (in == null || !file.exists()) return new Result(List.of(), List.of(), List.of(), List.of());
        String template;
        try (InputStreamReader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            template = readAll(r);
        }
        String current = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        Result res = merge(template.lines().toList(), current.lines().toList());
        if (res.changed()) {
            Files.writeString(file.toPath(), String.join("\n", res.lines()) + "\n", StandardCharsets.UTF_8);
        }
        return res;
    }

    private static String readAll(InputStreamReader r) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
        return sb.toString();
    }

    /** Pure text merge, kept free of plugin state so it can be tested on its own. */
    static Result merge(List<String> template, List<String> current) {
        YamlConfiguration cur = YamlConfiguration.loadConfiguration(new StringReader(String.join("\n", current)));
        List<String> out = new ArrayList<>(current);
        List<String> added = new ArrayList<>();
        List<String> renamed = new ArrayList<>();
        List<String> removed = new ArrayList<>();

        // 0) Obsolete options: drop the key line, its comment block and a blank line that follows
        for (String oldKey : REMOVED) {
            if (!cur.contains(oldKey)) continue;
            for (int i = 0; i < out.size(); i++) {
                Matcher m = TOP_KEY.matcher(out.get(i));
                if (!m.matches() || !m.group(1).equals(oldKey)) continue;
                int start = i;
                while (start > 0 && out.get(start - 1).startsWith("#")) start--;
                int end = i + 1;
                while (end < out.size() && (out.get(end).startsWith(" ") || out.get(end).startsWith("\t"))) end++;
                if (end < out.size() && out.get(end).isBlank()) end++;
                out.subList(start, end).clear();
                removed.add(oldKey);
                break;
            }
        }

        // 1) Renamed options: replace the old key line in place, keep the owner's value converted
        for (Map.Entry<String, String> e : RENAMED.entrySet()) {
            String oldKey = e.getKey(), newKey = e.getValue();
            if (!cur.contains(oldKey) || cur.contains(newKey)) continue;
            for (int i = 0; i < out.size(); i++) {
                Matcher m = TOP_KEY.matcher(out.get(i));
                if (m.matches() && m.group(1).equals(oldKey)) {
                    Object converted = convert(oldKey, cur.get(oldKey));
                    out.set(i, newKey + ": " + converted);
                    // Replace the stale comment block directly above with the template's one
                    int start = i;
                    while (start > 0 && out.get(start - 1).startsWith("#")) start--;
                    List<String> block = templateBlock(template, newKey);
                    List<String> comments = block.subList(0, block.size() - 1);
                    for (int k = i - 1; k >= start; k--) out.remove(k);
                    out.addAll(start, comments);
                    renamed.add(oldKey + " -> " + newKey);
                    cur.set(newKey, converted);
                    break;
                }
            }
        }

        // 2) Missing top-level options and sections: append with their comment block
        List<String> pending = new ArrayList<>();
        for (int i = 0; i < template.size(); i++) {
            String line = template.get(i);
            if (line.isBlank()) { pending.clear(); continue; }
            if (line.startsWith("#")) { pending.add(line); continue; }
            Matcher m = TOP_KEY.matcher(line);
            if (!m.matches()) { pending.clear(); continue; }
            String key = m.group(1);
            boolean section = m.group(2).isBlank();
            int end = i + 1;
            while (end < template.size() && (template.get(end).startsWith(" ") || template.get(end).startsWith("\t")
                    || (section && template.get(end).startsWith("#") && end + 1 < template.size()
                        && template.get(end + 1).startsWith(" ")))) end++;
            if (!cur.contains(key)) {
                if (!out.isEmpty() && !out.get(out.size() - 1).isBlank()) out.add("");
                out.addAll(pending);
                out.addAll(template.subList(i, end));
                added.add(key);
            } else if (section) {
                addMissingSubKeys(template.subList(i + 1, end), key, cur, out, added);
            }
            pending.clear();
            i = end - 1;
        }
        return new Result(out, added, renamed, removed);
    }

    /** Inserts sub keys of a section that the owner's file lacks at the end of that section. */
    private static void addMissingSubKeys(List<String> body, String section, YamlConfiguration cur,
                                          List<String> out, List<String> added) {
        List<String> missing = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (String line : body) {
            if (line.isBlank()) { pending.clear(); continue; }
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("#")) { pending.add(line); continue; }
            Matcher m = SUB_KEY.matcher(line);
            if (!m.matches()) { pending.clear(); continue; }
            if (!cur.contains(section + "." + m.group(2))) {
                missing.addAll(pending);
                missing.add(line);
                added.add(section + "." + m.group(2));
            }
            pending.clear();
        }
        if (missing.isEmpty()) return;
        int secLine = -1;
        for (int i = 0; i < out.size(); i++) {
            Matcher m = TOP_KEY.matcher(out.get(i));
            if (m.matches() && m.group(1).equals(section)) { secLine = i; break; }
        }
        if (secLine < 0) return;
        int insert = secLine + 1;
        while (insert < out.size() && (out.get(insert).startsWith(" ") || out.get(insert).startsWith("\t"))) insert++;
        out.addAll(insert, missing);
    }

    /** The comment lines directly above a top-level key in the template plus the key line itself. */
    private static List<String> templateBlock(List<String> template, String key) {
        for (int i = 0; i < template.size(); i++) {
            Matcher m = TOP_KEY.matcher(template.get(i));
            if (m.matches() && m.group(1).equals(key)) {
                int start = i;
                while (start > 0 && template.get(start - 1).startsWith("#")) start--;
                return new ArrayList<>(template.subList(start, i + 1));
            }
        }
        return List.of(key + ":");
    }

    /** Converts the value of a renamed option into the new option's meaning. */
    static Object convert(String oldKey, Object value) {
        if (oldKey.equals("max-size") && value instanceof Number n) {
            // 1.0.0: outer frame size -> interior cells of the equivalent square
            int inner = Math.max(1, n.intValue() - 2);
            return Math.min(FrameDetector.HARD_MAX_BLOCKS, inner * inner);
        }
        return value;
    }
}
