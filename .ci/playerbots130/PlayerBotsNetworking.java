package dev.denis.playerbots.network;

import dev.denis.playerbots.PlayerBotsMod;
import dev.denis.playerbots.bot.BotManager;
import dev.denis.playerbots.bot.BotSnapshot;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.UUID;

public final class PlayerBotsNetworking {
    public static final Identifier REQUEST = id("request"), SNAPSHOT = id("snapshot"), CREATE = id("create"), RENAME = id("rename"), DELETE = id("delete"), CONTROL = id("control"), RELEASE = id("release"), TELEPORT = id("teleport"), BOT_INPUT = id("bot_input"), BOT_LOOK = id("bot_look"), BOT_ACTION = id("bot_action"), BOT_SLOT = id("bot_slot"), BOT_STATE = id("bot_state"), BOT_INVENTORY_CLICK = id("bot_inventory_click");
    public static final int ACTION_ATTACK_ENTITY = 0, ACTION_USE_ENTITY = 1, ACTION_USE_BLOCK = 2, ACTION_USE_ITEM = 3, ACTION_START_BREAK = 4, ACTION_ABORT_BREAK = 5, ACTION_SWING = 6;
    private PlayerBotsNetworking() {}
    private static Identifier id(String path) { return new Identifier(PlayerBotsMod.MOD_ID, path); }

    public static void registerServer() {
        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buf, sender) -> server.execute(() -> { sendSnapshot(player); sendControlledState(player); }));
        ServerPlayNetworking.registerGlobalReceiver(CREATE, (server, player, handler, buf, sender) -> { String name = buf.readString(16); server.execute(() -> { manager().create(player, name); sendSnapshot(player); }); });
        ServerPlayNetworking.registerGlobalReceiver(RENAME, (server, player, handler, buf, sender) -> { UUID id = buf.readUuid(); String name = buf.readString(16); server.execute(() -> { manager().rename(player, id, name); sendSnapshot(player); }); });
        ServerPlayNetworking.registerGlobalReceiver(DELETE, (server, player, handler, buf, sender) -> { UUID id = buf.readUuid(); server.execute(() -> { manager().remove(player, id); sendSnapshot(player); }); });
        ServerPlayNetworking.registerGlobalReceiver(CONTROL, (server, player, handler, buf, sender) -> { UUID id = buf.readUuid(); server.execute(() -> { ServerPlayerEntity bot = manager().get(id); if (bot != null) manager().possession().control(player, bot); sendSnapshot(player); sendControlledState(player); }); });
        ServerPlayNetworking.registerGlobalReceiver(RELEASE, (server, player, handler, buf, sender) -> server.execute(() -> { manager().possession().release(player); sendSnapshot(player); sendControlledState(player); }));
        ServerPlayNetworking.registerGlobalReceiver(TELEPORT, (server, player, handler, buf, sender) -> { UUID id = buf.readUuid(); server.execute(() -> { manager().teleportControllerTo(player, id); sendSnapshot(player); }); });

        ServerPlayNetworking.registerGlobalReceiver(BOT_INPUT, (server, player, handler, buf, sender) -> {
            float sideways = buf.readFloat(), forward = buf.readFloat();
            boolean jumping = buf.readBoolean(), sneaking = buf.readBoolean(), sprinting = buf.readBoolean();
            float yaw = buf.readFloat(), pitch = buf.readFloat();
            server.execute(() -> {
                ServerPlayerEntity bot = manager().possession().controlledBy(player);
                if (bot == null) return;
                // Run the exact vanilla PlayerInput handler against the bot's own listener.
                // The possession mixin deliberately does not recurse because the bot is not a controller.
                new PlayerInputC2SPacket(sideways, forward, jumping, sneaking).apply(bot.networkHandler);
                bot.setSprinting(sprinting);
                bot.setYaw(yaw); bot.setPitch(pitch); bot.setHeadYaw(yaw); bot.bodyYaw = yaw;
                sendControlledState(player);
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(BOT_LOOK, (server, player, handler, buf, sender) -> { float yaw = buf.readFloat(), pitch = buf.readFloat(); server.execute(() -> { ServerPlayerEntity bot = manager().possession().controlledBy(player); if (bot == null) return; bot.setYaw(yaw); bot.setPitch(pitch); bot.setHeadYaw(yaw); bot.bodyYaw = yaw; }); });
        ServerPlayNetworking.registerGlobalReceiver(BOT_SLOT, (server, player, handler, buf, sender) -> { int slot = buf.readVarInt(); server.execute(() -> { ServerPlayerEntity bot = manager().possession().controlledBy(player); if (bot == null || slot < 0 || slot >= 9) return; bot.getInventory().selectedSlot = slot; bot.playerScreenHandler.sendContentUpdates(); sendControlledState(player); }); });
        ServerPlayNetworking.registerGlobalReceiver(BOT_ACTION, (server, player, handler, buf, sender) -> { int action = buf.readUnsignedByte(); PacketByteBuf copy = PacketByteBufs.create(); copy.writeBytes(buf); server.execute(() -> handleBotAction(player, action, copy)); });
        ServerPlayNetworking.registerGlobalReceiver(BOT_INVENTORY_CLICK, (server, player, handler, buf, sender) -> { int slot = buf.readInt(), button = buf.readInt(), actionTypeOrdinal = buf.readVarInt(); server.execute(() -> { ServerPlayerEntity bot = manager().possession().controlledBy(player); if (bot == null) return; var values = net.minecraft.screen.slot.SlotActionType.values(); if (actionTypeOrdinal < 0 || actionTypeOrdinal >= values.length) return; bot.playerScreenHandler.onSlotClick(slot, button, values[actionTypeOrdinal], bot); bot.playerScreenHandler.sendContentUpdates(); sendControlledState(player); }); });
    }

    private static void handleBotAction(ServerPlayerEntity controller, int action, PacketByteBuf buf) {
        ServerPlayerEntity bot = manager().possession().controlledBy(controller); if (bot == null) return;
        switch (action) {
            case ACTION_ATTACK_ENTITY -> { Entity target = bot.getServerWorld().getEntityById(buf.readVarInt()); if (target != null && bot.squaredDistanceTo(target) <= 36.0) { bot.attack(target); bot.swingHand(Hand.MAIN_HAND, true); } }
            case ACTION_USE_ENTITY -> { Entity target = bot.getServerWorld().getEntityById(buf.readVarInt()); Hand hand = readHand(buf); if (target != null && bot.squaredDistanceTo(target) <= 36.0) { target.interact(bot, hand); bot.swingHand(hand, true); } }
            case ACTION_USE_BLOCK -> { BlockPos pos = buf.readBlockPos(); Direction side = buf.readEnumConstant(Direction.class); Vec3d hit = new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()); boolean inside = buf.readBoolean(); Hand hand = readHand(buf); if (bot.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64.0) { BlockHitResult bhr = new BlockHitResult(hit, side, pos, inside); bot.interactionManager.interactBlock(bot, bot.getServerWorld(), bot.getStackInHand(hand), hand, bhr); bot.swingHand(hand, true); } }
            case ACTION_USE_ITEM -> { Hand hand = readHand(buf); bot.interactionManager.interactItem(bot, bot.getServerWorld(), bot.getStackInHand(hand), hand); bot.swingHand(hand, true); }
            case ACTION_START_BREAK, ACTION_ABORT_BREAK -> { BlockPos pos = buf.readBlockPos(); Direction side = buf.readEnumConstant(Direction.class); if (bot.squaredDistanceTo(Vec3d.ofCenter(pos)) <= 64.0) { PlayerActionC2SPacket.Action vanillaAction = action == ACTION_START_BREAK ? PlayerActionC2SPacket.Action.START_DESTROY_BLOCK : PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK; bot.interactionManager.processBlockBreakingAction(pos, vanillaAction, side, bot.getServerWorld().getTopY(), 0); } }
            case ACTION_SWING -> bot.swingHand(readHand(buf), true);
            default -> { }
        }
        sendControlledState(controller);
    }
    private static Hand readHand(PacketByteBuf buf) { return buf.readBoolean() ? Hand.OFF_HAND : Hand.MAIN_HAND; }

    public static void sendSnapshot(ServerPlayerEntity controllerOrBot) {
        BotManager manager = manager(); ServerPlayerEntity controller = manager.possession().controllerOf(controllerOrBot); List<BotSnapshot> list = manager.snapshotsFor(controller); PacketByteBuf out = PacketByteBufs.create(); out.writeVarInt(list.size());
        for (BotSnapshot bot : list) { out.writeUuid(bot.uuid()); out.writeString(bot.name(), 16); out.writeString(bot.dimension()); out.writeDouble(bot.x()); out.writeDouble(bot.y()); out.writeDouble(bot.z()); out.writeBoolean(bot.controlledByMe()); out.writeBoolean(bot.controlledByAnyone()); }
        ServerPlayNetworking.send(controller, SNAPSHOT, out);
    }
    public static void sendControlledState(ServerPlayerEntity controllerOrBot) {
        BotManager manager = manager(); ServerPlayerEntity controller = manager.possession().controllerOf(controllerOrBot); ServerPlayerEntity bot = manager.possession().controlledBy(controller); PacketByteBuf out = PacketByteBufs.create(); out.writeBoolean(bot != null);
        if (bot != null) {
            out.writeUuid(bot.getUuid()); out.writeFloat(bot.getHealth()); out.writeVarInt(bot.getHungerManager().getFoodLevel()); out.writeFloat(bot.getHungerManager().getSaturationLevel()); out.writeVarInt(bot.getInventory().selectedSlot);
            out.writeVarInt(4); for (int slot = 1; slot <= 4; slot++) out.writeItemStack(bot.playerScreenHandler.getSlot(slot).getStack());
            out.writeVarInt(bot.getInventory().main.size()); for (ItemStack stack : bot.getInventory().main) out.writeItemStack(stack);
            out.writeVarInt(bot.getInventory().armor.size()); for (ItemStack stack : bot.getInventory().armor) out.writeItemStack(stack);
            out.writeVarInt(bot.getInventory().offHand.size()); for (ItemStack stack : bot.getInventory().offHand) out.writeItemStack(stack);
            out.writeItemStack(bot.playerScreenHandler.getCursorStack());
        }
        ServerPlayNetworking.send(controller, BOT_STATE, out);
    }
    private static BotManager manager() { BotManager manager = PlayerBotsMod.manager(); if (manager == null) throw new IllegalStateException("Player Bots manager is not active"); return manager; }
}
