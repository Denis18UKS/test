package dev.denis.playerbots.mixin;

import dev.denis.playerbots.client.ControlledBotView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {
    @Redirect(
            method = {"renderArm", "renderArmHoldingItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderRightArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V")
    )
    private void playerbots$renderControlledRightArm(PlayerEntityRenderer originalRenderer,
                                                      MatrixStack matrices, VertexConsumerProvider vertices,
                                                      int light, AbstractClientPlayerEntity originalPlayer) {
        AbstractClientPlayerEntity armPlayer = ControlledBotView.playerForFirstPersonArm(originalPlayer);
        playerbots$rendererFor(armPlayer, originalRenderer).renderRightArm(matrices, vertices, light, armPlayer);
    }

    @Redirect(
            method = {"renderArm", "renderArmHoldingItem"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/PlayerEntityRenderer;renderLeftArm(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/network/AbstractClientPlayerEntity;)V")
    )
    private void playerbots$renderControlledLeftArm(PlayerEntityRenderer originalRenderer,
                                                     MatrixStack matrices, VertexConsumerProvider vertices,
                                                     int light, AbstractClientPlayerEntity originalPlayer) {
        AbstractClientPlayerEntity armPlayer = ControlledBotView.playerForFirstPersonArm(originalPlayer);
        playerbots$rendererFor(armPlayer, originalRenderer).renderLeftArm(matrices, vertices, light, armPlayer);
    }

    private static PlayerEntityRenderer playerbots$rendererFor(AbstractClientPlayerEntity player,
                                                                PlayerEntityRenderer fallback) {
        EntityRenderer<? super AbstractClientPlayerEntity> renderer = MinecraftClient.getInstance()
                .getEntityRenderDispatcher().getRenderer(player);
        return renderer instanceof PlayerEntityRenderer playerRenderer ? playerRenderer : fallback;
    }
}
