package net.raphimc.viaproxy.plugins.vbucheck;

import com.viaversion.nbt.tag.ByteTag;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.viaversion.api.connection.UserConnection;
import com.viaversion.viaversion.api.protocol.packet.PacketWrapper;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.libs.mcstructs.converter.impl.v1_21_5.NbtConverter_v1_21_5;
import com.viaversion.viaversion.libs.mcstructs.core.Identifier;
import com.viaversion.viaversion.libs.mcstructs.dialog.ActionButton;
import com.viaversion.viaversion.libs.mcstructs.dialog.AfterAction;
import com.viaversion.viaversion.libs.mcstructs.dialog.action.CustomAllAction;
import com.viaversion.viaversion.libs.mcstructs.dialog.action.StaticAction;
import com.viaversion.viaversion.libs.mcstructs.text.events.click.ClickEvent;
import com.viaversion.viaversion.libs.mcstructs.dialog.body.DialogBody;
import com.viaversion.viaversion.libs.mcstructs.dialog.Input;
import com.viaversion.viaversion.libs.mcstructs.dialog.body.PlainMessageBody;
import com.viaversion.viaversion.libs.mcstructs.dialog.impl.MultiActionDialog;
import com.viaversion.viaversion.libs.mcstructs.dialog.serializer.DialogSerializer;
import com.viaversion.viaversion.libs.mcstructs.text.components.StringComponent;
import com.viaversion.viaversion.protocols.v1_21_9to1_21_11.packet.ClientboundPackets1_21_11;
import io.netty.channel.ChannelFutureListener;
import net.raphimc.netminecraft.constants.ConnectionState;
import net.raphimc.netminecraft.packet.Packet;
import net.raphimc.viabedrock.protocol.BedrockProtocol;
import net.raphimc.viabedrock.protocol.storage.ChannelStorage;
import net.raphimc.viaproxy.proxy.packethandler.PacketHandler;
import net.raphimc.viaproxy.proxy.session.ProxyConnection;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class VBUCheckPacketHandler extends PacketHandler {

    private static final Logger LOGGER = LogManager.getLogger("VBUCheck");
    private static final String VBU_CONFIRM_CHANNEL = "viabedrockutility:confirm";
    private static final int DIALOG_BUTTON_WIDTH = 200;

    private final int delayMs;
    private final String message;
    private final String downloadUrl;
    private final String dialogTitle;
    private final String dialogCloseButton;
    private final String dialogDownloadButton;

    private boolean checked = false;

    public VBUCheckPacketHandler(ProxyConnection proxyConnection, int delayMs, String message,
                                 String downloadUrl, String dialogTitle, String dialogCloseButton,
                                 String dialogDownloadButton) {
        super(proxyConnection);
        this.delayMs = delayMs;
        this.message = message;
        this.downloadUrl = downloadUrl;
        this.dialogTitle = dialogTitle;
        this.dialogCloseButton = dialogCloseButton;
        this.dialogDownloadButton = dialogDownloadButton;
    }

    @Override
    public boolean handleP2S(Packet packet, List<ChannelFutureListener> listeners) {
        if (!checked && proxyConnection.getC2pConnectionState() == ConnectionState.PLAY) {
            checked = true;
            if (delayMs > 0) {
                proxyConnection.getC2P().eventLoop().schedule(this::checkAndNotify, delayMs, TimeUnit.MILLISECONDS);
            } else {
                proxyConnection.getC2P().eventLoop().execute(this::checkAndNotify);
            }
        }
        return true;
    }

    private void checkAndNotify() {
        if (proxyConnection.isClosed()) return;

        final UserConnection userConnection = proxyConnection.getUserConnection();
        if (userConnection == null) return;

        final ChannelStorage channelStorage = userConnection.get(ChannelStorage.class);
        if (channelStorage == null) return;

        if (channelStorage.hasChannel(VBU_CONFIRM_CHANNEL)) {
            LOGGER.info("Player " + getPlayerName() + " has ViaBedrockUtility installed");
            return;
        }

        LOGGER.info("Player " + getPlayerName() + " does NOT have ViaBedrockUtility, sending notification");

        try {
            if (proxyConnection.getClientVersion().newerThanOrEqualTo(ProtocolVersion.v1_21_6)) {
                sendDialog(userConnection);
            }
            sendChatMessage(userConnection);
        } catch (Exception e) {
            LOGGER.error("Failed to send VBU notification", e);
        }
    }

    private void sendDialog(UserConnection userConnection) throws Exception {
        final ActionButton closeButton = new ActionButton(
                new StringComponent(dialogCloseButton),
                DIALOG_BUTTON_WIDTH,
                new CustomAllAction(Identifier.of("vbucheck", "dismiss"), null));

        final List<DialogBody> body = new ArrayList<>();
        for (String line : message.split("\n")) {
            body.add(new PlainMessageBody(new StringComponent(line)));
        }

        final ActionButton downloadButton = new ActionButton(
                new StringComponent(dialogDownloadButton),
                DIALOG_BUTTON_WIDTH,
                new StaticAction(ClickEvent.openUrl(new URI(downloadUrl))));

        final List<ActionButton> actions = new ArrayList<>();
        actions.add(downloadButton);
        actions.add(closeButton);

        final MultiActionDialog dialog = new MultiActionDialog(
                new StringComponent(dialogTitle),
                true,   // closeAfterAction
                false,  // hideExitButton
                AfterAction.CLOSE,
                body,                                   // body
                new ArrayList<Input>(),                  // inputs
                actions,                                 // actions
                closeButton,                             // exitButton
                1                                        // columns
        );

        final PacketWrapper showDialog = PacketWrapper.create(ClientboundPackets1_21_11.SHOW_DIALOG, userConnection);
        showDialog.write(Types.VAR_INT, 0); // registry id
        showDialog.write(Types.TAG, DialogSerializer.V1_21_6.getDirectCodec()
                .serialize(NbtConverter_v1_21_5.INSTANCE, dialog).get());
        showDialog.send(BedrockProtocol.class);
    }

    private void sendChatMessage(UserConnection userConnection) throws Exception {
        final CompoundTag root = new CompoundTag();
        root.putString("text", "");

        final ListTag<CompoundTag> extra = new ListTag<>(CompoundTag.class);

        // 前缀
        final CompoundTag prefix = new CompoundTag();
        prefix.putString("text", "[VBUCheck] ");
        prefix.putString("color", "gold");
        extra.add(prefix);

        // 消息文本
        final CompoundTag msgPart = new CompoundTag();
        msgPart.putString("text", message + " ");
        msgPart.putString("color", "yellow");
        extra.add(msgPart);

        // 可点击下载链接
        final CompoundTag link = new CompoundTag();
        link.putString("text", "[Download]");
        link.putString("color", "green");
        link.put("underlined", new ByteTag((byte) 1));

        final CompoundTag clickEvent = new CompoundTag();
        clickEvent.putString("action", "open_url");
        clickEvent.putString("value", downloadUrl);
        link.put("clickEvent", clickEvent);

        final CompoundTag hoverEvent = new CompoundTag();
        hoverEvent.putString("action", "show_text");
        final CompoundTag hoverContents = new CompoundTag();
        hoverContents.putString("text", downloadUrl);
        hoverEvent.put("contents", hoverContents);
        link.put("hoverEvent", hoverEvent);

        extra.add(link);
        root.put("extra", extra);

        final PacketWrapper systemChat = PacketWrapper.create(ClientboundPackets1_21_11.SYSTEM_CHAT, userConnection);
        systemChat.write(Types.TAG, root);       // message
        systemChat.write(Types.BOOLEAN, false);   // overlay
        systemChat.send(BedrockProtocol.class);
    }

    private String getPlayerName() {
        return proxyConnection.getGameProfile() != null ? proxyConnection.getGameProfile().getName() : "unknown";
    }

}
