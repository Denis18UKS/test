package dev.denis.playerbots.mixin;

import dev.denis.playerbots.PlayerBotsMod;
import dev.denis.playerbots.client.screen.BotConfiguratorScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void playerbots$openConfiguratorBeforeVanillaUse(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player == null || client.currentScreen != null) return;

        boolean holdingConfigurator = client.player.getMainHandStack().isOf(PlayerBotsMod.CONFIGURATOR)
                || client.player.getOffHandStack().isOf(PlayerBotsMod.CONFIGURATOR);
        if (!holdingConfigurator) return;

        client.setScreen(new BotConfiguratorScreen());
        ci.cancel();
    }
}
