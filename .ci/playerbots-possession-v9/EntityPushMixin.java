package dev.denis.playerbots.mixin;

import dev.denis.playerbots.PlayerBotsMod;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityPushMixin {
    @Inject(method = "pushAwayFrom", at = @At("HEAD"), cancellable = true)
    private void playerbots$doNotPushControllerAndControlledBot(Entity other, CallbackInfo ci) {
        if (PlayerBotsMod.manager() == null) return;
        Entity self = (Entity) (Object) this;
        if (PlayerBotsMod.manager().possession().shouldIgnorePush(self, other)) {
            ci.cancel();
        }
    }
}
