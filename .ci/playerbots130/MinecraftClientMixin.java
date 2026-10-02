package dev.denis.playerbots.mixin;

import dev.denis.playerbots.PlayerBotsMod;
import dev.denis.playerbots.client.BotInputController;
import dev.denis.playerbots.client.screen.BotConfiguratorScreen;
import dev.denis.playerbots.client.screen.BotInventoryScreen;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Inject(method = "doAttack", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeAttackToBot(CallbackInfoReturnable<Boolean> cir) {
        if (!BotInputController.active()) return;
        BotInputController.attack();
        cir.setReturnValue(true);
    }

    @Inject(method = "handleBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeBlockBreakingToBot(boolean breaking, CallbackInfo ci) {
        if (!BotInputController.active()) return;
        BotInputController.handleBlockBreaking(breaking);
        ci.cancel();
    }

    @Inject(method = "handleInputEvents", at = @At("HEAD"), cancellable = true)
    private void playerbots$openBotInventory(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (!BotInputController.active() || client.currentScreen != null || !client.options.inventoryKey.wasPressed()) return;
        client.setScreen(new BotInventoryScreen(client));
        ci.cancel();
    }

    @Inject(method = "doItemUse", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeUseOrOpenConfigurator(CallbackInfo ci) {
        MinecraftClient client = (MinecraftClient) (Object) this;
        if (client.player == null || client.currentScreen != null) return;

        if (BotInputController.active()) {
            BotInputController.use();
            ci.cancel();
            return;
        }

        boolean holdingConfigurator = client.player.getMainHandStack().isOf(PlayerBotsMod.CONFIGURATOR)
                || client.player.getOffHandStack().isOf(PlayerBotsMod.CONFIGURATOR);
        if (!holdingConfigurator) return;

        client.setScreen(new BotConfiguratorScreen());
        ci.cancel();
    }
}
