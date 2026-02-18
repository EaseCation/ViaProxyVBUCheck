package net.raphimc.viaproxy.plugins.vbucheck;

import net.lenni0451.lambdaevents.EventHandler;
import net.raphimc.viabedrock.api.BedrockProtocolVersion;
import net.raphimc.viaproxy.ViaProxy;
import net.raphimc.viaproxy.plugins.ViaProxyPlugin;
import net.raphimc.viaproxy.plugins.events.ConnectEvent;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

public class VBUCheckPlugin extends ViaProxyPlugin {

    private static final Logger LOGGER = Logger.getLogger("VBUCheck");

    private boolean enabled = true;
    private int delayMs = 1000;
    private String message = "检测到您未安装 ViaBedrockUtility Mod，部分功能（自定义实体、皮肤等）将无法正常显示。请安装该 Mod 以获得最佳体验。";
    private String downloadUrl = "https://github.com/RaphiMC/ViaBedrockUtility/releases";
    private String dialogTitle = "提示";
    private String dialogCloseButton = "我知道了";

    @Override
    public void onEnable() {
        loadConfig();
        ViaProxy.EVENT_MANAGER.register(this);
        LOGGER.info("VBUCheck plugin enabled (enabled=" + enabled + ")");
    }

    @EventHandler
    private void onConnect(ConnectEvent event) {
        if (!enabled) return;

        final ProxyConnection proxyConnection = event.getProxyConnection();

        if (!proxyConnection.getServerVersion().equals(BedrockProtocolVersion.bedrockLatest)) {
            return;
        }

        proxyConnection.getPacketHandlers().add(new VBUCheckPacketHandler(
                proxyConnection, delayMs, message, downloadUrl, dialogTitle, dialogCloseButton));
    }

    private void loadConfig() {
        final File configFile = new File(getDataFolder(), "config.yml");

        if (!configFile.exists()) {
            saveDefaultConfig(configFile);
        }

        try (InputStream is = new FileInputStream(configFile);
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            final Yaml yaml = new Yaml();
            final Map<String, Object> config = yaml.load(reader);
            if (config == null) return;

            if (config.containsKey("enabled")) {
                enabled = (Boolean) config.get("enabled");
            }
            if (config.containsKey("delay-ms")) {
                delayMs = ((Number) config.get("delay-ms")).intValue();
            }
            if (config.containsKey("message")) {
                message = (String) config.get("message");
            }
            if (config.containsKey("download-url")) {
                downloadUrl = (String) config.get("download-url");
            }
            if (config.containsKey("dialog-title")) {
                dialogTitle = (String) config.get("dialog-title");
            }
            if (config.containsKey("dialog-close-button")) {
                dialogCloseButton = (String) config.get("dialog-close-button");
            }
        } catch (Exception e) {
            LOGGER.warning("Failed to load config, using defaults: " + e.getMessage());
        }
    }

    private void saveDefaultConfig(File configFile) {
        final Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("enabled", enabled);
        defaults.put("delay-ms", delayMs);
        defaults.put("message", message);
        defaults.put("download-url", downloadUrl);
        defaults.put("dialog-title", dialogTitle);
        defaults.put("dialog-close-button", dialogCloseButton);

        try (OutputStream os = new FileOutputStream(configFile);
             OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            writer.write("# VBUCheck 配置文件\n\n");
            writer.write("# 是否启用检测\n");
            writer.write("enabled: " + enabled + "\n\n");
            writer.write("# 进入世界后延迟多少毫秒再发送提示（0 = 立即）\n");
            writer.write("delay-ms: " + delayMs + "\n\n");
            writer.write("# 提示文案\n");
            writer.write("message: \"" + escapeYaml(message) + "\"\n\n");
            writer.write("# 下载链接（仅低版本聊天消息可点击）\n");
            writer.write("download-url: \"" + escapeYaml(downloadUrl) + "\"\n\n");
            writer.write("# Dialog 标题（仅 1.21.6+）\n");
            writer.write("dialog-title: \"" + escapeYaml(dialogTitle) + "\"\n\n");
            writer.write("# Dialog 关闭按钮文案（仅 1.21.6+）\n");
            writer.write("dialog-close-button: \"" + escapeYaml(dialogCloseButton) + "\"\n");
        } catch (Exception e) {
            LOGGER.warning("Failed to save default config: " + e.getMessage());
        }
    }

    private static String escapeYaml(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
