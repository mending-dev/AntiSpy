# AntiSpy

Hide plugins and commands from your players. AntiSpy removes configurable commands from the tab completion and blocks their execution, on **Paper backend servers and Velocity proxies** with a single JAR.

## Features

- Remove listed commands from the tab completion
- Optionally cancel the execution of listed commands
- Hide all namespaced commands (e.g. `/antispy:antispy`)
- Namespace bypass protection: if `plugins` is listed, `/bukkit:plugins` is blocked too
- One customizable message for blocked commands that can also replace the server's *unknown command* message, so blocked and non-existing commands look identical
- Bypass permission for staff
- Reloadable at runtime
- Built for performance: only O(1) set lookups in the event handlers, config values are pre-parsed on reload

## Requirements

- Java 25
- Paper 26.3 and/or Velocity 3.4+
- PlaceholderAPI (optional, Paper only) for placeholders in the message

## Installation

1. Put `AntiSpy-1.0.0.jar` into the `plugins` folder of your Paper servers **and** your Velocity proxy. The correct variant is loaded automatically.
2. Start the server once to generate the config.
3. Edit the `commands` list and reload.

| Platform | Config location               |
|----------|-------------------------------|
| Paper    | `plugins/AntiSpy/config.yml`  |
| Velocity | `plugins/antispy/config.yml`  |

## Configuration

Options shared by both platforms:

```yaml
enabled: true                 # Master switch
hide-namespaced: true         # Hide all namespaced commands from the tab completion
block-execution: true         # Also cancel the execution, not only hide the command
bypass-permission: "antispy.bypass"

commands:                     # Without slash, case-insensitive, aliases have to be listed too!
  - plugins
  - pl
  - version
  - ver
  - about
  - help
  - "?"
```

### Messages

**Paper**

```yaml
# Message for blocked commands (MiniMessage, supports PlaceholderAPI placeholders like %player_name%)
# "default" = the server's own unknown command message (spigot.yml: messages.unknown-command)
# empty     = no message
message: "default"

# true = "message" also replaces the server's message for commands that do not exist at all,
# so blocked and non-existing commands always look identical
override-unknown-command: true
```

Players with the bypass permission and the console always see the original server messages.

**Velocity**

```yaml
# true  = forward blocked commands to the backend, which answers with its own unknown command message
#         (only works if the backend does not know the command)
# false = deny the command on the proxy and send the custom "message"
forward-to-backend: true

# Only used if forward-to-backend is false (MiniMessage, empty = no message)
message: "<red>Unknown command."
```

Commands unknown to the proxy are forwarded to the backend anyway. With `forward-to-backend: true` the Paper config therefore controls the message of the whole network.

## Commands and permissions

| Command             | Permission        | Description                |
|---------------------|-------------------|----------------------------|
| `/antispy reload`   | `antispy.reload`  | Reloads the config (Paper) |

| Permission         | Default | Description                                        |
|--------------------|---------|----------------------------------------------------|
| `antispy.reload`   | OP      | Use `/antispy reload`                              |
| `antispy.bypass`   | OP      | See and use all commands, ignoring the blocker     |

On Velocity the config is reloaded with `/velocity reload`. Permission checks on Velocity require a permission plugin such as LuckPerms.

## Building

```
./gradlew build
```

The finished JAR is located at `build/libs/AntiSpy-1.0.0.jar`.

```
AntiSpy/
├─ core/       Platform independent matching logic (CommandFilter)
├─ paper/      Paper module (listener, Brigadier command, paper-plugin.yml)
└─ velocity/   Velocity module (event handlers, config loading)
```

The root project merges all modules into one JAR using Shadow. Paper reads `paper-plugin.yml`, Velocity reads `velocity-plugin.json`, so each platform only loads its own main class.

## Limitations

- Console, command blocks and commands dispatched by other plugins (`dispatchCommand`) are not affected
- Aliases are not resolved automatically and must be listed manually
- On Velocity the tab completion filter only affects what the proxy sends, a backend with its own commands needs its own AntiSpy config