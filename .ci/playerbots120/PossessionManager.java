package dev.denis.playerbots.bot;

import dev.denis.playerbots.core.PossessionRouting;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PossessionManager {
    private final MinecraftServer server;
    private final Map<UUID, UUID> controlledByController = new HashMap<>();
    private final Map<UUID, UUID> controllerByControlled = new HashMap<>();

    public PossessionManager(MinecraftServer server) {
        this.server = server;
    }

    public ServerPlayerEntity controllerOf(ServerPlayerEntity player) {
        UUID controllerId = controllerByControlled.get(player.getUuid());
        if (controllerId == null) return player;
        ServerPlayerEntity controller = server.getPlayerManager().getPlayer(controllerId);
        return controller == null ? player : controller;
    }

    public ServerPlayerEntity controlledBy(ServerPlayerEntity controllerOrControlled) {
        ServerPlayerEntity controller = controllerOf(controllerOrControlled);
        UUID botId = controlledByController.get(controller.getUuid());
        return botId == null ? null : server.getPlayerManager().getPlayer(botId);
    }

    public ServerPlayerEntity deliveryPlayer(ServerPlayerEntity controllerOrControlled) {
        return controllerOf(controllerOrControlled);
    }

    public boolean isController(ServerPlayerEntity player) {
        return controlledByController.containsKey(player.getUuid());
    }

    public boolean isControlled(UUID botId) {
        return controllerByControlled.containsKey(botId);
    }

    public boolean isControlledBy(UUID botId, UUID controllerId) {
        return controllerId.equals(controllerByControlled.get(botId));
    }

    public boolean shouldIgnorePush(Entity first, Entity second) {
        return PossessionRouting.shouldIgnorePush(first.getUuid(), second.getUuid(), controllerByControlled);
    }

    public boolean control(ServerPlayerEntity packetPlayer, ServerPlayerEntity target) {
        ServerPlayerEntity controller = controllerOf(packetPlayer);
        if (controller.getUuid().equals(target.getUuid())) return false;
        UUID existingController = controllerByControlled.get(target.getUuid());
        if (existingController != null && !existingController.equals(controller.getUuid())) return false;
        if (controller.getServerWorld() != target.getServerWorld()) return false;

        ServerPlayerEntity previous = controlledBy(controller);
        if (previous != null && !previous.getUuid().equals(target.getUuid())) {
            if (previous.currentScreenHandler != previous.playerScreenHandler) previous.closeHandledScreen();
            controllerByControlled.remove(previous.getUuid());
        }

        controlledByController.put(controller.getUuid(), target.getUuid());
        controllerByControlled.put(target.getUuid(), controller.getUuid());
        controller.setCameraEntity(target);
        return true;
    }

    public boolean release(ServerPlayerEntity packetPlayer) {
        ServerPlayerEntity controller = controllerOf(packetPlayer);
        UUID botId = controlledByController.get(controller.getUuid());
        if (botId == null) return false;
        ServerPlayerEntity bot = server.getPlayerManager().getPlayer(botId);
        if (bot != null && bot.currentScreenHandler != bot.playerScreenHandler) bot.closeHandledScreen();
        controlledByController.remove(controller.getUuid());
        controllerByControlled.remove(botId);
        controller.setCameraEntity(controller);
        return true;
    }

    public void releaseController(UUID controllerId) {
        ServerPlayerEntity controller = server.getPlayerManager().getPlayer(controllerId);
        if (controller != null) {
            release(controller);
            return;
        }
        UUID controlled = controlledByController.remove(controllerId);
        if (controlled != null) controllerByControlled.remove(controlled);
    }

    public void releaseIfControlled(UUID botId) {
        UUID controllerId = controllerByControlled.get(botId);
        if (controllerId != null) releaseController(controllerId);
    }
}
