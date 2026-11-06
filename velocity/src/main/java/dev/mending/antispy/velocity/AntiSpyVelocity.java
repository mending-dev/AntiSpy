package dev.mending.antispy.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.command.PlayerAvailableCommandsEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyReloadEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import dev.mending.antispy.core.CommandFilter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.slf4j.Logger;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Plugin(id = "antispy", name = "AntiSpy", authors = {"MrMending"})
public class AntiSpyVelocity {

    private final Logger logger;
    private final Path dataDirectory;

    // Immutable snapshot of the config, replaced atomically on reload (thread-safe, no locking)
    private volatile Settings settings = Settings.DISABLED;

    @Inject
    public AntiSpyVelocity(Logger logger, @DataDirectory Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        loadConfig();
    }

    @Subscribe
    public void onProxyReload(ProxyReloadEvent event) {
        loadConfig();
    }

    /**
     * Removes listed (and optionally all namespaced) commands from the tab completion.
     */
    @Subscribe(order = PostOrder.LAST)
    public void onAvailableCommands(PlayerAvailableCommandsEvent event) {
        Settings current = this.settings;
        if (!current.enabled() || hasBypass(event.getPlayer(), current)) {
            return;
        }

        // Single pass over the command tree with O(1) set lookups
        event.getRootNode().getChildren().removeIf(node ->
                current.filter().shouldHide(node.getName().toLowerCase(Locale.ROOT))
        );
    }

    /**
     * Blocks the execution of listed commands.
     */
    @Subscribe(order = PostOrder.FIRST)
    public void onCommandExecute(CommandExecuteEvent event) {
        Settings current = this.settings;
        if (!current.enabled() || !current.blockExecution()) {
            return;
        }

        // Only players are affected, the console is never blocked
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }

        String label = CommandFilter.extractLabel(event.getCommand());

        // The set lookup comes first, the permission check only runs on a hit
        if (!current.filter().isListed(label) || hasBypass(player, current)) {
            return;
        }

        if (current.forwardToBackend()) {
            // The backend answers with its own "unknown command" message
            event.setResult(CommandExecuteEvent.CommandResult.forwardToServer());
            return;
        }

        event.setResult(CommandExecuteEvent.CommandResult.denied());
        if (current.hasMessage()) {
            player.sendMessage(current.message());
        }
    }

    private boolean hasBypass(Player player, Settings current) {
        String permission = current.bypassPermission();
        return !permission.isEmpty() && player.hasPermission(permission);
    }

    /**
     * Copies the default config if missing, then builds a new snapshot from the file.
     */
    private void loadConfig() {
        try {
            Files.createDirectories(dataDirectory);
            Path file = dataDirectory.resolve("config.yml");

            // The default config lies in velocity/config.yml inside the jar (avoids a clash with the Paper config)
            if (Files.notExists(file)) {
                try (InputStream in = getClass().getResourceAsStream("/velocity/config.yml")) {
                    if (in != null) {
                        Files.copy(in, file);
                    }
                }
            }

            Map<String, Object> root;
            try (Reader reader = Files.newBufferedReader(file)) {
                root = new Yaml().load(reader);
            }
            if (root == null) {
                root = Map.of();
            }

            List<String> commands = new ArrayList<>();
            if (root.get("commands") instanceof List<?> list) {
                for (Object entry : list) {
                    commands.add(String.valueOf(entry));
                }
            }

            CommandFilter filter = new CommandFilter(commands, getBoolean(root, "hide-namespaced", true));

            // Parse the MiniMessage text once instead of on every blocked command
            String rawMessage = getString(root, "message", "");
            boolean hasMessage = !rawMessage.isBlank();
            Component message = hasMessage
                    ? MiniMessage.miniMessage().deserialize(rawMessage)
                    : Component.empty();

            this.settings = new Settings(
                    getBoolean(root, "enabled", true),
                    getBoolean(root, "block-execution", true),
                    getBoolean(root, "forward-to-backend", true),
                    getString(root, "bypass-permission", ""),
                    filter,
                    message,
                    hasMessage
            );
        } catch (IOException | RuntimeException exception) {
            logger.error("Could not load config.yml, AntiSpy stays disabled", exception);
            this.settings = Settings.DISABLED;
        }
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean fallback) {
        return map.get(key) instanceof Boolean value ? value : fallback;
    }

    private static String getString(Map<String, Object> map, String key, String fallback) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : fallback;
    }

    private record Settings(
            boolean enabled,
            boolean blockExecution,
            boolean forwardToBackend,
            String bypassPermission,
            CommandFilter filter,
            Component message,
            boolean hasMessage
    ) {
        static final Settings DISABLED = new Settings(
                false, false, false, "", CommandFilter.EMPTY, Component.empty(), false
        );
    }
}