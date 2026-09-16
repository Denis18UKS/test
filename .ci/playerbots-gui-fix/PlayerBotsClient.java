package dev.denis.playerbots.client;

import dev.denis.playerbots.client.screen.BotConfiguratorScreen;
import dev.denis.playerbots.client.screen.BotRadialScreen;
import dev.denis.playerbots.network.PlayerBotsNetworking;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

import java.util.ArrayList;
import java.util.List;

public final class PlayerBotsClient implements ClientModInitializer {
    private static KeyBinding radialKey;
    private static boolean wasHeld;
    private static boolean radialConsumed;
    private static boolean pendingRadial;

    @Override
    public void onInitializeClient() {
        radialKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.playerbots.radial", InputUtil.Type.KEYSYM,
                InputUtil.GLFW_KEY_B, "category.playerbots"));

        ClientPlayNetworking.registerGlobalReceiver(PlayerBotsNetworking.SNAPSHOT, (client, handler, buf, responseSender) -> {
            int count = buf.readVarInt();
            List<ClientBotInfo> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                list.add(new ClientBotInfo(buf.readUuid(), buf.readString(16), buf.readString(), buf.readDouble(), buf.readDouble(),
                        buf.readDouble(), buf.readBoolean(), buf.readBoolean()));
            }
            client.execute(() -> {
                ClientBotState.replace(list);
                if (pendingRadial && isRadialKeyHeld() && !radialConsumed && !list.isEmpty() && client.currentScreen == null) {
                    client.setScreen(new BotRadialScreen());
                    pendingRadial = false;
                }
            });
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> ClientBotState.request());
        ClientTickEvents.END_CLIENT_TICK.register(PlayerBotsClient::tick);
    }

    private static void tick(MinecraftClient client) {
        if (client.player == null || client.getNetworkHandler() == null) return;

        boolean held = radialKey.isPressed();
        if (held && !wasHeld && client.currentScreen == null) {
            radialConsumed = false;
            ClientBotState.request();
            if (!ClientBotState.bots().isEmpty()) client.setScreen(new BotRadialScreen());
            else pendingRadial = true;
        }
        if (!held && wasHeld) {
            pendingRadial = false;
            radialConsumed = false;
            if (client.currentScreen instanceof BotRadialScreen) client.setScreen(null);
        }
        wasHeld = held;
    }

    public static boolean isRadialKeyHeld() { return radialKey != null && radialKey.isPressed(); }
    public static void markRadialConsumed() { radialConsumed = true; pendingRadial = false; }
}
