package dev.denis.playerbots.client;

import dev.denis.playerbots.network.PlayerBotsNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class ClientBotState {
    private static final List<ClientBotInfo> BOTS = new ArrayList<>();
    private static long revision;

    private ClientBotState() {}

    public static List<ClientBotInfo> bots() { return Collections.unmodifiableList(BOTS); }
    public static long revision() { return revision; }
    public static ClientBotInfo controlled() { return BOTS.stream().filter(ClientBotInfo::controlledByMe).findFirst().orElse(null); }

    public static void replace(List<ClientBotInfo> bots) {
        BOTS.clear(); BOTS.addAll(bots); revision++;
        PossessedBodyVisual.syncFromSnapshot(controlled() != null);
    }

    public static void request() { ClientPlayNetworking.send(PlayerBotsNetworking.REQUEST, PacketByteBufs.empty()); }
    public static void create(String name) { PacketByteBuf b = PacketByteBufs.create(); b.writeString(name, 16); ClientPlayNetworking.send(PlayerBotsNetworking.CREATE, b); }
    public static void rename(UUID id, String name) { PacketByteBuf b = PacketByteBufs.create(); b.writeUuid(id); b.writeString(name, 16); ClientPlayNetworking.send(PlayerBotsNetworking.RENAME, b); }
    public static void delete(UUID id) { sendUuid(PlayerBotsNetworking.DELETE, id); }
    public static void control(UUID id) { PossessedBodyVisual.captureBeforePossession(); sendUuid(PlayerBotsNetworking.CONTROL, id); }
    public static void teleport(UUID id) { sendUuid(PlayerBotsNetworking.TELEPORT, id); }
    public static void release() { ClientPlayNetworking.send(PlayerBotsNetworking.RELEASE, PacketByteBufs.empty()); }

    private static void sendUuid(net.minecraft.util.Identifier channel, UUID id) {
        PacketByteBuf b = PacketByteBufs.create(); b.writeUuid(id); ClientPlayNetworking.send(channel, b);
    }
}
