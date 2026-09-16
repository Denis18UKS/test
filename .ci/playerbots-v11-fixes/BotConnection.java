package dev.denis.playerbots.bot;

import dev.denis.playerbots.mixin.ClientConnectionAccessor;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;

import java.net.InetSocketAddress;
import java.net.SocketAddress;

/**
 * In-process connection used only so vanilla can construct a normal
 * ServerPlayNetworkHandler for a bot. No login/authentication handshake is
 * performed and outbound packets have no remote endpoint.
 */
public final class BotConnection extends ClientConnection {
    private static final SocketAddress ADDRESS = new InetSocketAddress("127.0.0.1", 0);

    public BotConnection() {
        super(NetworkSide.SERVERBOUND);
        ((ClientConnectionAccessor) (Object) this).playerbots$setChannel(new EmbeddedChannel());
    }

    @Override
    public void send(Packet<?> packet) {
        // There is no remote client for a bot.
    }

    @Override
    public void send(Packet<?> packet, PacketCallbacks callbacks) {
        // There is no remote client for a bot.
    }

    @Override
    public SocketAddress getAddress() {
        return ADDRESS;
    }

    @Override
    public void handleDisconnection() {
        // No Netty peer exists to tear down.
    }
}
