package dev.denis.playerbots.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Authoritative client-side snapshot of the currently possessed server fake-player. */
public final class BotInventoryMirror {
    private static BotState state;

    private BotInventoryMirror() {}

    public static BotState read(PacketByteBuf buf) {
        boolean active = buf.readBoolean();
        if (!active) return null;
        UUID botId = buf.readUuid();
        float health = buf.readFloat();
        int food = buf.readVarInt();
        float saturation = buf.readFloat();
        int selectedSlot = buf.readVarInt();
        List<ItemStack> crafting = readStacks(buf);
        List<ItemStack> main = readStacks(buf);
        List<ItemStack> armor = readStacks(buf);
        List<ItemStack> offhand = readStacks(buf);
        ItemStack cursor = buf.readItemStack();
        return new BotState(botId, health, food, saturation, selectedSlot, crafting, main, armor, offhand, cursor);
    }

    private static List<ItemStack> readStacks(PacketByteBuf buf) {
        int size = buf.readVarInt();
        List<ItemStack> result = new ArrayList<>(size);
        for (int i = 0; i < size; i++) result.add(buf.readItemStack());
        return result;
    }

    public static void apply(BotState next) {
        state = next;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        if (next == null) {
            client.setCameraEntity(client.player);
            return;
        }

        AbstractClientPlayerEntity controlled = ControlledBotView.controlledPlayer();
        if (controlled != null) {
            controlled.setHealth(next.health());
            controlled.getHungerManager().setFoodLevel(next.food());
            controlled.getHungerManager().setSaturationLevel(next.saturation());
            controlled.getInventory().selectedSlot = next.selectedSlot();
            client.setCameraEntity(controlled);
        }
    }

    public static BotState state() { return state; }

    public static void clear() {
        state = null;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) client.setCameraEntity(client.player);
    }

    public record BotState(UUID botId, float health, int food, float saturation, int selectedSlot,
                           List<ItemStack> crafting, List<ItemStack> main, List<ItemStack> armor,
                           List<ItemStack> offhand, ItemStack cursor) {}
}
