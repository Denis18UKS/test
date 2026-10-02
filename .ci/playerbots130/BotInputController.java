package dev.denis.playerbots.client;

import dev.denis.playerbots.network.PlayerBotsNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public final class BotInputController {
    private static int lastSlot = -1;
    private static BlockPos breakingPos;
    private static Direction breakingSide;

    private BotInputController() {}
    public static boolean active() { return ClientBotState.controlled() != null; }

    public static void tick(MinecraftClient client) {
        if (!active() || client.player == null) {
            lastSlot = -1; breakingPos = null; breakingSide = null; return;
        }
        if (client.currentScreen != null) return;

        float forward = (client.options.forwardKey.isPressed() ? 1.0f : 0.0f)
                - (client.options.backKey.isPressed() ? 1.0f : 0.0f);
        float sideways = (client.options.leftKey.isPressed() ? 1.0f : 0.0f)
                - (client.options.rightKey.isPressed() ? 1.0f : 0.0f);
        boolean jumping = client.options.jumpKey.isPressed();
        boolean sneaking = client.options.sneakKey.isPressed();
        boolean sprinting = client.options.sprintKey.isPressed();
        Entity camera = client.getCameraEntity();
        float yaw = camera == null ? client.player.getYaw() : camera.getYaw();
        float pitch = camera == null ? client.player.getPitch() : camera.getPitch();

        PacketByteBuf input = PacketByteBufs.create();
        input.writeFloat(sideways); input.writeFloat(forward);
        input.writeBoolean(jumping); input.writeBoolean(sneaking); input.writeBoolean(sprinting);
        input.writeFloat(yaw); input.writeFloat(pitch);
        ClientPlayNetworking.send(PlayerBotsNetworking.BOT_INPUT, input);

        int selected = BotInventoryMirror.state() == null ? 0 : BotInventoryMirror.state().selectedSlot();
        if (selected != lastSlot) {
            lastSlot = selected;
            PacketByteBuf slot = PacketByteBufs.create();
            slot.writeVarInt(selected);
            ClientPlayNetworking.send(PlayerBotsNetworking.BOT_SLOT, slot);
        }

        client.player.input.movementForward = 0.0f;
        client.player.input.movementSideways = 0.0f;
        client.player.input.jumping = false;
        client.player.input.sneaking = false;
        client.player.setSprinting(false);
        client.player.setVelocity(Vec3d.ZERO);
    }

    public static boolean attack() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active() || client.crosshairTarget == null) return false;
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) {
            PacketByteBuf out = action(PlayerBotsNetworking.ACTION_ATTACK_ENTITY);
            out.writeVarInt(entityHit.getEntity().getId());
            ClientPlayNetworking.send(PlayerBotsNetworking.BOT_ACTION, out); return true;
        }
        if (hit instanceof BlockHitResult blockHit) { startBreaking(blockHit); return true; }
        return true;
    }

    public static void handleBlockBreaking(boolean breaking) {
        if (!active()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (!breaking) { abortBreaking(); return; }
        if (client.crosshairTarget instanceof BlockHitResult hit) {
            if (!hit.getBlockPos().equals(breakingPos) || hit.getSide() != breakingSide) { abortBreaking(); startBreaking(hit); }
        } else abortBreaking();
    }

    private static void startBreaking(BlockHitResult hit) {
        if (breakingPos != null && breakingPos.equals(hit.getBlockPos()) && breakingSide == hit.getSide()) return;
        breakingPos = hit.getBlockPos().toImmutable(); breakingSide = hit.getSide();
        PacketByteBuf out = action(PlayerBotsNetworking.ACTION_START_BREAK);
        out.writeBlockPos(breakingPos); out.writeEnumConstant(breakingSide);
        ClientPlayNetworking.send(PlayerBotsNetworking.BOT_ACTION, out);
    }

    private static void abortBreaking() {
        if (breakingPos == null || breakingSide == null) return;
        PacketByteBuf out = action(PlayerBotsNetworking.ACTION_ABORT_BREAK);
        out.writeBlockPos(breakingPos); out.writeEnumConstant(breakingSide);
        ClientPlayNetworking.send(PlayerBotsNetworking.BOT_ACTION, out);
        breakingPos = null; breakingSide = null;
    }

    public static boolean use() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active()) return false;
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult entityHit) { sendUseEntity(entityHit.getEntity(), Hand.MAIN_HAND); return true; }
        if (hit instanceof BlockHitResult blockHit) { sendUseBlock(blockHit, Hand.MAIN_HAND); return true; }
        sendUseItem(Hand.MAIN_HAND); return true;
    }

    private static void sendUseEntity(Entity entity, Hand hand) {
        PacketByteBuf out = action(PlayerBotsNetworking.ACTION_USE_ENTITY);
        out.writeVarInt(entity.getId()); writeHand(out, hand); ClientPlayNetworking.send(PlayerBotsNetworking.BOT_ACTION, out);
    }
    private static void sendUseBlock(BlockHitResult hit, Hand hand) {
        PacketByteBuf out = action(PlayerBotsNetworking.ACTION_USE_BLOCK);
        out.writeBlockPos(hit.getBlockPos()); out.writeEnumConstant(hit.getSide());
        out.writeDouble(hit.getPos().x); out.writeDouble(hit.getPos().y); out.writeDouble(hit.getPos().z);
        out.writeBoolean(hit.isInsideBlock()); writeHand(out, hand); ClientPlayNetworking.send(PlayerBotsNetworking.BOT_ACTION, out);
    }
    private static void sendUseItem(Hand hand) { PacketByteBuf out = action(PlayerBotsNetworking.ACTION_USE_ITEM); writeHand(out, hand); ClientPlayNetworking.send(PlayerBotsNetworking.BOT_ACTION, out); }

    public static void clickInventory(int slotId, int button, SlotActionType actionType) {
        PacketByteBuf out = PacketByteBufs.create();
        out.writeInt(slotId); out.writeInt(button); out.writeVarInt(actionType.ordinal());
        ClientPlayNetworking.send(PlayerBotsNetworking.BOT_INVENTORY_CLICK, out);
    }
    private static PacketByteBuf action(int action) { PacketByteBuf out = PacketByteBufs.create(); out.writeByte(action); return out; }
    private static void writeHand(PacketByteBuf out, Hand hand) { out.writeBoolean(hand == Hand.OFF_HAND); }
}
