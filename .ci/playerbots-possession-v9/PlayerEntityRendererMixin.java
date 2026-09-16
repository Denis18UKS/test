package dev.denis.playerbots.mixin;

import dev.denis.playerbots.client.ClientBotInfo;
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
    private void playerbots$hideDuplicatePossessionBodies(AbstractClientPlayerEntity player, float yaw, float tickDelta,
                                                           MatrixStack matrices, VertexConsumerProvider vertices,
                                                           int light, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientBotInfo controlled = ClientBotState.controlled();
        if (controlled == null) return;

        if (player == client.player) {
            ci.cancel();
            return;
        }

        if (client.options.getPerspective().isFirstPerson()
                && player.getUuid().equals(controlled.uuid())) {
            ci.cancel();
        }
    }
}
