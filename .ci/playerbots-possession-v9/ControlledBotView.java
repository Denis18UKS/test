package dev.denis.playerbots.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;

public final class ControlledBotView {
    private ControlledBotView() {}

    public static AbstractClientPlayerEntity controlledPlayer() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientBotInfo controlled = ClientBotState.controlled();
        if (controlled == null || client.world == null) return null;
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (player.getUuid().equals(controlled.uuid())) return player;
        }
        return null;
    }

    public static AbstractClientPlayerEntity playerForFirstPersonArm(AbstractClientPlayerEntity fallback) {
        AbstractClientPlayerEntity controlled = controlledPlayer();
        return controlled == null ? fallback : controlled;
    }
}
