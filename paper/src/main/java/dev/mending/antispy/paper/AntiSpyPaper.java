package dev.mending.antispy.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;

public class AntiSpyPaper extends JavaPlugin {

    private PaperCommandListener listener;

    @Override
    public void onEnable() {
        copyDefaultConfig();

        // Register the listener that handles tab completion and command execution
        this.listener = new PaperCommandListener(this);
        getServer().getPluginManager().registerEvents(listener, this);

        // Paper plugins register commands via Brigadier, the literal "reload" is tab completed automatically
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event ->
                event.registrar().register(buildCommand(), "Reloads the AntiSpy config")
        );
    }

    /**
     * Builds /antispy reload, restricted to players with the reload permission.
     */
    private LiteralCommandNode<CommandSourceStack> buildCommand() {
        return Commands.literal("antispy")
                .requires(source -> source.getSender().hasPermission("antispy.reload"))
                .executes(context -> {
                    context.getSource().getSender()
                            .sendMessage(Component.text("Usage: /antispy reload", NamedTextColor.GRAY));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.literal("reload")
                        .executes(context -> {
                            reloadConfig();
                            listener.reload();
                            context.getSource().getSender()
                                    .sendMessage(Component.text("Config reloaded.", NamedTextColor.GREEN));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    /**
     * The default config lies in paper/config.yml inside the jar (to avoid a clash with the
     * Velocity config), so it has to be copied manually instead of using saveDefaultConfig().
     */
    private void copyDefaultConfig() {
        Path file = getDataFolder().toPath().resolve("config.yml");
        if (Files.exists(file)) {
            return;
        }

        try (InputStream in = getResource("paper/config.yml")) {
            if (in == null) {
                getLogger().severe("Default config paper/config.yml is missing in the jar!");
                return;
            }

            // Unlike File#mkdirs this throws a descriptive exception if the folder cannot be created
            Files.createDirectories(file.getParent());
            Files.copy(in, file);
        } catch (IOException exception) {
            // Include the target path and the exception type in the message for easier debugging
            getLogger().log(Level.SEVERE,
                    "Could not save the default config to " + file.toAbsolutePath() + ": " + exception,
                    exception);
        }
    }
}