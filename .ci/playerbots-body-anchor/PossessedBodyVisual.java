package dev.denis.playerbots.client;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.EntityPose;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;

import java.util.EnumMap;
import java.util.Map;

/**
 * Client-side visual proxy for the controller's real body while the vanilla
 * client player object is temporarily used to drive a possessed bot.
 *
 * The authoritative real ServerPlayerEntity is never moved by this class.
 */
public final class PossessedBodyVisual {
    private static final int SHELL_ENTITY_ID = Integer.MIN_VALUE + 47047;

    private static Anchor anchor;
    private static OtherClientPlayerEntity shell;

    private PossessedBodyVisual() {}

    public static void captureBeforePossession() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (anchor != null || client.player == null || client.world == null) return;

        Map<EquipmentSlot, ItemStack> equipment = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            equipment.put(slot, client.player.getEquippedStack(slot).copy());
        }

        anchor = new Anchor(
                client.world.getRegistryKey().getValue().toString(),
                client.player.getX(), client.player.getY(), client.player.getZ(),
                client.player.getYaw(), client.player.getPitch(), client.player.getHeadYaw(), client.player.bodyYaw,
                client.player.getPose(), client.player.isSneaking(), client.player.getGameProfile(), equipment
        );
    }

    public static void syncFromSnapshot(boolean possessed) {
        if (!possessed) {
            clear();
            return;
        }
        renderAnchoredBody();
    }

    public static void tick() {
        if (shell == null || anchor == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || shell.getWorld() != client.world) {
            removeShellOnly();
            renderAnchoredBody();
            return;
        }
        pinShellToAnchor();
    }

    public static void clear() {
        removeShellOnly();
        anchor = null;
    }

    private static void renderAnchoredBody() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (anchor == null || client.world == null || shell != null) return;
        String currentDimension = client.world.getRegistryKey().getValue().toString();
        if (!anchor.dimension().equals(currentDimension)) return;

        Entity existing = client.world.getEntityById(SHELL_ENTITY_ID);
        if (existing != null) client.world.removeEntity(SHELL_ENTITY_ID, Entity.RemovalReason.DISCARDED);

        shell = new OtherClientPlayerEntity(client.world, copyProfile(anchor.profile()));
        shell.setNoGravity(true);
        shell.noClip = true;
        shell.setPose(anchor.pose());
        shell.setSneaking(anchor.sneaking());
        for (Map.Entry<EquipmentSlot, ItemStack> entry : anchor.equipment().entrySet()) {
            shell.equipStack(entry.getKey(), entry.getValue().copy());
        }
        pinShellToAnchor();
        client.world.addEntity(SHELL_ENTITY_ID, shell);
    }

    private static void pinShellToAnchor() {
        if (shell == null || anchor == null) return;
        shell.refreshPositionAndAngles(anchor.x(), anchor.y(), anchor.z(), anchor.yaw(), anchor.pitch());
        shell.setHeadYaw(anchor.headYaw());
        shell.bodyYaw = anchor.bodyYaw();
        shell.prevBodyYaw = anchor.bodyYaw();
        shell.prevHeadYaw = anchor.headYaw();
        shell.setVelocity(Vec3d.ZERO);
    }

    private static void removeShellOnly() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (shell != null && client.world != null && client.world.getEntityById(SHELL_ENTITY_ID) == shell) {
            client.world.removeEntity(SHELL_ENTITY_ID, Entity.RemovalReason.DISCARDED);
        }
        shell = null;
    }

    private static GameProfile copyProfile(GameProfile source) {
        GameProfile copy = new GameProfile(source.getId(), source.getName());
        copy.getProperties().putAll(source.getProperties());
        return copy;
    }

    private record Anchor(
            String dimension,
            double x, double y, double z,
            float yaw, float pitch, float headYaw, float bodyYaw,
            EntityPose pose,
            boolean sneaking,
            GameProfile profile,
            Map<EquipmentSlot, ItemStack> equipment
    ) {}
}
