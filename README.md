# ViaProxyVBUCheck

A [ViaProxy](https://github.com/RaphiMC/ViaProxy) plugin that detects whether the [ViaBedrockUtility](https://github.com/RaphiMC/ViaBedrockUtility) mod is installed on the client when connecting to Bedrock servers, and notifies players to install it if missing.

## Features

- Detects ViaBedrockUtility mod presence via custom payload channel registration
- Triggers precisely when the player enters the world (not a simple timer), by intercepting the first PLAY-state packet (JoinGame)
- Only activates for Bedrock server connections
- Two notification modes based on client version:
  - **1.21.6+**: Native Minecraft Dialog popup
  - **Older versions**: Chat message with clickable download URL
- Fully configurable message text, download URL, and dialog appearance

## Installation

1. Download the latest `VBUCheck-x.x.x.jar` from [Releases](https://github.com/EaseCation/ViaProxyVBUCheck/releases)
2. Place the JAR in ViaProxy's `plugins/` directory
3. Start ViaProxy — a default `config.yml` will be generated in `plugins/VBUCheck/`

## Building from Source

Requires [ViaProxy](https://github.com/RaphiMC/ViaProxy) and [ViaBedrock](https://github.com/RaphiMC/ViaBedrock) published to mavenLocal:

```bash
# In ViaVersion directory
./gradlew publishToMavenLocal

# In ViaBedrock directory
./gradlew publishToMavenLocal

# Then build the plugin
./gradlew build
```

The output JAR will be at `build/libs/VBUCheck-1.0.0.jar`.

## Configuration

On first run, `plugins/VBUCheck/config.yml` is generated:

```yaml
# Enable/disable the check
enabled: true

# Delay in milliseconds after entering the world before sending the notification (0 = immediate)
delay-ms: 1000

# Notification message text
message: "检测到您未安装 ViaBedrockUtility Mod，部分功能（自定义实体、皮肤等）将无法正常显示。请安装该 Mod 以获得最佳体验。"

# Download URL (clickable in chat messages for pre-1.21.6 clients)
download-url: "https://github.com/RaphiMC/ViaBedrockUtility/releases"

# Dialog title (1.21.6+ only)
dialog-title: "提示"

# Dialog close button text (1.21.6+ only)
dialog-close-button: "我知道了"
```

## How It Works

1. On `ConnectEvent`, the plugin checks if the target is a Bedrock server and registers a custom `PacketHandler`
2. The handler monitors server-to-client packets via `handleP2S()` and detects the first PLAY-state packet (the JoinGame packet), which indicates the player has entered the world
3. After an optional configurable delay, it checks `ChannelStorage` for the `viabedrockutility:confirm` channel — which ViaBedrockUtility registers during the CONFIGURATION phase
4. If the channel is not found, it sends a Dialog (1.21.6+) or a system chat message (older versions) to the player

## License

This project is open source under the [MIT License](LICENSE).
