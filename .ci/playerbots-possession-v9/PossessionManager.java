package dev.denis.playerbots.bot;

import dev.denis.playerbots.core.PossessionRouting;
import dev.denis.playerbots.mixin.ServerPlayNetworkHandlerAccessor;
import net.minecraft.entity.Entity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;

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

    public ServerPlayerEntity controllerOf(ServerPlayerEntity packetPlayer) {
        UUID controllerId = controllerByControlled.get(packetPlayer.getUuid());
        if (controllerId == null) return packetPlayer;
        ServerPlayerEntity controller = server.getPlayerManager().getPlayer(controllerId);
        return controller == null ? packetPlayer : controller;
    }

    public ServerPlayerEntity controlledBy(ServerPlayerEntity controller) {
        UUID id = controlledByController.get(controller.getUuid());
        return id == null ? null : server.getPlayerManager().getPlayer(id);
    }

    public ServerPlayerEntity deliveryPlayer(ServerPlayerEntity controller) {
        ServerPlayerEntity controlled = controlledBy(controller);
        return controlled == null ? controller : controlled;
    }

    public boolean isControlled(UUID botId) {
        return controllerByControlled.containsKey(botId);
    }

    public boolean isControlledBy(UUID botId, UUID controllerId) {
        return controllerId.equals(controllerByControlled.get(botId));
    }

    public boolean shouldSuppressTracking(Entity trackedEntity, ServerPlayerEntity recipient) {
        return PossessionRouting.shouldSuppressTracking(trackedEntity.getUuid(), recipient.getUuid(), controllerByControlled);
    }

    public boolean shouldIgnorePush(Entity first, Entity second) {
        return PossessionRouting.shouldIgnorePush(first.getUuid(), second.getUuid(), controllerByControlled);
    }

    public boolean control(ServerPlayerEntity packetPlayer, ServerPlayerEntity target) {
        ServerPlayerEntity controller = controllerOf(packetPlayer);
        if (controller.getUuid().equals(target.getUuid())) return false;
        UUID owner = controllerByControlled.get(target.getUuid());
        if (owner != null && !owner.equals(controller.getUuid())) return false;

        ServerPlayerEntity current = controlledBy(controller);
        if (current == null) current = controller;
        if (current.getUuid().equals(target.getUuid())) return true;

        ReturnPoint returnPoint = bridgeTargetIntoCurrentWorld(current, target);
        swapHandlers(current, target);
        if (!current.getUuid().equals(controller.getUuid())) {
            controllerByControlled.remove(current.getUuid());
        }
        controlledByController.put(controller.getUuid(), target.getUuid());
        controllerByControlled.put(target.getUuid(), controller.getUuid());
        restoreTargetWorld(target, returnPoint);
        syncClient(target);
        return true;
    }

    public boolean release(ServerPlayerEntity packetPlayer) {
        ServerPlayerEntity controller = controllerOf(packetPlayer);
        ServerPlayerEntity current = controlledBy(controller);
        if (current == null) return false;

        ReturnPoint returnPoint = bridgeTargetIntoCurrentWorld(current, controller);
        swapHandlers(current, controller);
        controlledByController.remove(controller.getUuid());
        controllerByControlled.remove(current.getUuid());
        restoreTargetWorld(controller, returnPoint);
        syncClient(controller);
        return true;
    }

    public void releaseController(UUID controllerId) {
        ServerPlayerEntity controller = server.getPlayerManager().getPlayer(controllerId);
        if (controller != null) release(deliveryPlayer(controller));
        else {
            UUID controlled = controlledByController.remove(controllerId);
            if (controlled != null) controllerByControlled.remove(controlled);
        }
    }

    public void releaseIfControlled(UUID botId) {
        UUID controllerId = controllerByControlled.get(botId);
        if (controllerId != null) releaseController(controllerId);
    }

    private ReturnPoint bridgeTargetIntoCurrentWorld(ServerPlayerEntity current, ServerPlayerEntity target) {
        String currentDimension = current.getServerWorld().getRegistryKey().getValue().toString();
        String targetDimension = target.getServerWorld().getRegistryKey().getValue().toString();
        if (!PossessionRouting.needsWorldBridge(currentDimension, targetDimension)) return null;

        ReturnPoint original = new ReturnPoint(target.getServerWorld(), target.getX(), target.getY(), target.getZ(),
                target.getYaw(), target.getPitch());
        target.teleport(current.getServerWorld(), target.getX(), target.getY(), target.getZ(), target.getYaw(), target.getPitch());
        return original;
    }

    private static void restoreTargetWorld(ServerPlayerEntity target, ReturnPoint point) {
        if (point == null) return;
        target.teleport(point.world(), point.x(), point.y(), point.z(), point.yaw(), point.pitch());
    }

    private static void swapHandlers(ServerPlayerEntity a, ServerPlayerEntity b) {
        ServerPlayNetworkHandler aHandler = a.networkHandler;
        ServerPlayNetworkHandler bHandler = b.networkHandler;
        if (aHandler == null || bHandler == null) throw new IllegalStateException("Cannot swap missing network handler");

        ((ServerPlayNetworkHandlerAccessor) (Object) aHandler).playerbots$setPlayer(b);
        ((ServerPlayNetworkHandlerAccessor) (Object) bHandler).playerbots$setPlayer(a);
        a.networkHandler = bHandler;
        b.networkHandler = aHandler;
    }

    private void syncClient(ServerPlayerEntity player) {
        player.networkHandler.syncWithPlayerPosition();
        player.networkHandler.requestTeleport(player.getX(), player.getY(), player.getZ(), player.getYaw(), player.getPitch());
        player.sendAbilitiesUpdate();
        player.playerScreenHandler.syncState();
        player.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(player.getInventory().selectedSlot));
        server.getPlayerManager().sendPlayerStatus(player);
    }

    private record ReturnPoint(ServerWorld world, double x, double y, double z, float yaw, float pitch) {}
}
