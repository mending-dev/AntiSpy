package dev.mending.antispy.core;

import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Platform independent matching logic shared by the Paper and Velocity module.
 * Immutable and therefore thread-safe, all lookups are O(1).
 */
public final class CommandFilter {

    public static final CommandFilter EMPTY = new CommandFilter(Set.of(), false);

    private final Set<String> commands;
    private final boolean hideNamespaced;

    public CommandFilter(Collection<String> rawCommands, boolean hideNamespaced) {
        // Pre-normalize all entries once so the lookups need no further work
        Set<String> normalized = new HashSet<>();
        for (String raw : rawCommands) {
            String entry = normalize(raw);
            if (!entry.isEmpty()) {
                normalized.add(entry);
            }
        }

        this.commands = Set.copyOf(normalized);
        this.hideNamespaced = hideNamespaced;
    }

    /**
     * Checks if a (lowercase) label is on the blocked list. Namespaced labels
     * (e.g. "bukkit:plugins") are resolved to their base name so they cannot bypass the list.
     */
    public boolean isListed(String label) {
        if (commands.contains(label)) {
            return true;
        }

        int colon = label.indexOf(':');
        return colon != -1 && commands.contains(label.substring(colon + 1));
    }

    /**
     * Checks if a (lowercase) label should be removed from the tab completion.
     */
    public boolean shouldHide(String label) {
        return (hideNamespaced && label.indexOf(':') != -1) || isListed(label);
    }

    /**
     * Extracts the lowercase label from a command line with or without leading slash,
     * without splitting the whole line (no regex, no arrays).
     */
    public static String extractLabel(String commandLine) {
        int start = (!commandLine.isEmpty() && commandLine.charAt(0) == '/') ? 1 : 0;
        int end = commandLine.indexOf(' ', start);
        String label = end == -1 ? commandLine.substring(start) : commandLine.substring(start, end);
        return label.toLowerCase(Locale.ROOT);
    }

    private static String normalize(String raw) {
        if (raw == null) {
            return "";
        }

        String entry = raw.trim().toLowerCase(Locale.ROOT);
        return entry.startsWith("/") ? entry.substring(1) : entry;
    }
}