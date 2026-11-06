package dev.mending.antispy.paper;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.entity.Player;

/**
 * Isolates all PlaceholderAPI calls in one class. As long as PlaceholderAPI is not installed,
 * this class is never loaded, so the plugin works without it.
 */
final class PapiHook {

    private PapiHook() {
    }

    static String apply(Player player, String text) {
        return PlaceholderAPI.setPlaceholders(player, text);
    }
}