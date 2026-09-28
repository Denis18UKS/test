package dev.denis.playerbots.bot;

import dev.denis.playerbots.PlayerBotsMod;
import dev.denis.playerbots.mixin.ClientConnectionAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenHorseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerPropertyUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.SetTradeOffersS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.UUID;

public final class BotConnection extends ClientConnection {
    private static final SocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 0);
    private final MinecraftServer server;
    private final UUID botId;

    public BotConnection(MinecraftServer server, UUID botId) {
        super(NetworkSide.SERVERBOUND);
        this.server = server;
        this.botId = botId;
        ((ClientConnectionAccessor) (Object) this).playerbots$setChannel(new EmbeddedChannel());
    }

    @Override
    public void send(Packet<?> packet) {
        forwardScreenPacket(packet);
    }

    @Override
    public void send(Packet<?> packet, PacketCallbacks callbacks) {
        forwardScreenPacket(packet);
    }

    private void forwardScreenPacket(Packet<?> packet) {
        if (!isScreenPacket(packet)) return;
        BotManager manager = PlayerBotsMod.manager();
        if (manager == null || !manager.possession().isControlled(botId)) return;
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botId);
        if (bot == null) return;
        ServerPlayerEntity controller = manager.possession().controllerOf(bot);
        if (controller == bot || controller.networkHandler == null) return;
        controller.networkHandler.sendPacket(packet);
    }

    private static boolean isScreenPacket(Packet<?> packet) {
        return packet instanceof OpenScreenS2CPacket
                || packet instanceof OpenHorseScreenS2CPacket
                || packet instanceof CloseScreenS2CPacket
                || packet instanceof InventoryS2CPacket
                || packet instanceof ScreenHandlerSlotUpdateS2CPacket
                || packet instanceof ScreenHandlerPropertyUpdateS2CPacket
                || packet instanceof SetTradeOffersS2CPacket;
    }

    @Override
    public SocketAddress getAddress() {
        return ADDRESS;
    }

    @Override
    public void handleDisconnection() {
    }
}
