package dev.denis.playerbots.mixin;

import dev.denis.playerbots.client.ClientBotState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(
            method = "render(Lnet/minecraft/client/network/AbstractClientPlayerEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void playerbots$hideVanillaSelfWhilePossessing(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                                            MatrixStack matrices, VertexConsumerProvider vertices,
                                                            int light, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (player == client.player && ClientBotState.controlled() != null) {
            ci.cancel();
        }
    }
}
