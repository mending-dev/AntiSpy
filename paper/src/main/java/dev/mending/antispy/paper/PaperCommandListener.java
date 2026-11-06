package dev.mending.antispy.paper;

import dev.mending.antispy.core.CommandFilter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.command.UnknownCommandEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperCommandListener implements Listener {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // Keyword in the config that stands for the server's own unknown command message
    private static final String DEFAULT_KEYWORD = "default";

    // Fallback in case spigot.yml does not contain the key
    private static final String DEFAULT_UNKNOWN_COMMAND = "Unknown command. Type \"/help\" for help.";

    private final JavaPlugin plugin;

    // Immutable snapshot of the config, replaced atomically on reload (thread-safe, no locking)
    private volatile Settings settings = Settings.DISABLED;

    public PaperCommandListener(JavaPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /**
     * Reads the values from the config and builds a new snapshot.
     * Call this after plugin.reloadConfig().
     */
    public void reload() {
        FileConfiguration config = plugin.getConfig();

        CommandFilter filter = new CommandFilter(
                config.getStringList("commands"),
                config.getBoolean("hide-namespaced", true)
        );

        String rawMessage = config.getString("message", DEFAULT_KEYWORD);
        boolean useServerMessage = rawMessage.strip().equalsIgnoreCase(DEFAULT_KEYWORD);

        // Overriding the unknown command message is pointless if the server's own message is used anyway
        boolean overrideUnknown = !useServerMessage && config.getBoolean("override-unknown-command", true);

        // Placeholders are player specific, so such a message has to be rendered per player.
        // Messages without placeholders stay pre-parsed and cost nothing at runtime.
        boolean usePlaceholders = !useServerMessage
                && rawMessage.indexOf('%') != -1
                && Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        this.settings = new Settings(
                config.getBoolean("enabled", true),
                config.getBoolean("block-execution", true),
                overrideUnknown,
                config.getString("bypass-permission", ""),
                filter,
                resolveMessage(rawMessage, useServerMessage),
                usePlaceholders ? rawMessage : null
        );
    }

    /**
     * Builds the static message once on reload. Returns null if no message should be sent.
     */
    private Component resolveMessage(String rawMessage, boolean useServerMessage) {
        if (useServerMessage) {
            // Use the exact message the server sends for unknown commands (configurable in spigot.yml)
            String serverMessage = Bukkit.spigot().getSpigotConfig()
                    .getString("messages.unknown-command", DEFAULT_UNKNOWN_COMMAND);
            return LegacyComponentSerializer.legacyAmpersand().deserialize(serverMessage);
        }

        return rawMessage.isBlank() ? null : MINI_MESSAGE.deserialize(rawMessage);
    }

    /**
     * Returns the message for the given player, or null if nothing should be sent.
     */
    private Component render(Settings current, Player player) {
        if (current.template() != null) {
            // Resolve the placeholders first, then parse the result as MiniMessage
            return MINI_MESSAGE.deserialize(PapiHook.apply(player, current.template()));
        }
        return current.message();
    }

    /**
     * Removes listed (and optionally all namespaced) commands from the tab completion.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Settings current = this.settings;
        if (!current.enabled() || hasBypass(event.getPlayer(), current)) {
            return;
        }

        // Single pass over the command list with O(1) set lookups
        event.getCommands().removeIf(current.filter()::shouldHide);
    }

    /**
     * Cancels the execution of listed commands.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Settings current = this.settings;
        if (!current.enabled() || !current.blockExecution()) {
            return;
        }

        String label = CommandFilter.extractLabel(event.getMessage());

        // The set lookup comes first, the (more expensive) permission check only runs on a hit
        if (!current.filter().isListed(label)) {
            return;
        }

        Player player = event.getPlayer();
        if (hasBypass(player, current)) {
            return;
        }

        event.setCancelled(true);

        Component message = render(current, player);
        if (message != null) {
            player.sendMessage(message);
        }
    }

    /**
     * Replaces the server's message for commands that do not exist at all,
     * so they look identical to blocked commands.
     */
    @EventHandler
    public void onUnknownCommand(UnknownCommandEvent event) {
        Settings current = this.settings;
        if (!current.enabled() || !current.overrideUnknown()) {
            return;
        }

        // Console and players with the bypass permission keep the original message
        if (!(event.getSender() instanceof Player player) || hasBypass(player, current)) {
            return;
        }

        // A null message suppresses the output completely
        event.message(render(current, player));
    }

    private boolean hasBypass(Player player, Settings current) {
        String permission = current.bypassPermission();
        return !permission.isEmpty() && player.hasPermission(permission);
    }

    /**
     * @param message  the pre-parsed message, null if nothing should be sent
     * @param template the raw message with placeholders, only set if PlaceholderAPI is used (overrides message)
     */
    private record Settings(
            boolean enabled,
            boolean blockExecution,
            boolean overrideUnknown,
            String bypassPermission,
            CommandFilter filter,
            Component message,
            String template
    ) {
        static final Settings DISABLED = new Settings(
                false, false, false, "", CommandFilter.EMPTY, null, null
        );
    }
}