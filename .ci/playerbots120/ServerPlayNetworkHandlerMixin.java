package dev.denis.playerbots.mixin;

import dev.denis.playerbots.PlayerBotsMod;
import net.minecraft.network.packet.c2s.play.ButtonClickC2SPacket;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.CloseHandledScreenC2SPacket;
import net.minecraft.network.packet.c2s.play.CraftRequestC2SPacket;
import net.minecraft.network.packet.c2s.play.CreativeInventoryActionC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PickFromInventoryC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInputC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractItemC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.SelectMerchantTradeC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateBeaconC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdatePlayerAbilitiesC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayNetworkHandler.class)
public abstract class ServerPlayNetworkHandlerMixin {
    private ServerPlayerEntity playerbots$human() {
        return ((ServerPlayNetworkHandler) (Object) this).player;
    }

    private ServerPlayerEntity playerbots$controlled() {
        if (PlayerBotsMod.manager() == null) return null;
        ServerPlayerEntity human = playerbots$human();
        if (!PlayerBotsMod.manager().possession().isController(human)) return null;
        return PlayerBotsMod.manager().possession().controlledBy(human);
    }

    private boolean playerbots$isPossessing() {
        return playerbots$controlled() != null;
    }

    @Inject(method = "onPlayerMove", at = @At("HEAD"), cancellable = true)
    private void playerbots$freezeBodyMove(PlayerMoveC2SPacket packet, CallbackInfo ci) {
        if (playerbots$isPossessing()) ci.cancel();
    }

    @Inject(method = "onPlayerInput", at = @At("HEAD"), cancellable = true)
    private void playerbots$freezeBodyInput(PlayerInputC2SPacket packet, CallbackInfo ci) {
        if (playerbots$isPossessing()) ci.cancel();
    }

    @Inject(method = "onCreativeInventoryAction", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeCreativeInventory(CreativeInventoryActionC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onPickFromInventory", at = @At("HEAD"), cancellable = true)
    private void playerbots$routePick(PickFromInventoryC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onPlayerAction", at = @At("HEAD"), cancellable = true)
    private void playerbots$routePlayerAction(PlayerActionC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onPlayerInteractBlock", at = @At("HEAD"), cancellable = true)
    private void playerbots$freezeBodyBlockUse(PlayerInteractBlockC2SPacket packet, CallbackInfo ci) {
        if (playerbots$isPossessing()) ci.cancel();
    }

    @Inject(method = "onPlayerInteractItem", at = @At("HEAD"), cancellable = true)
    private void playerbots$freezeBodyItemUse(PlayerInteractItemC2SPacket packet, CallbackInfo ci) {
        if (playerbots$isPossessing()) ci.cancel();
    }

    @Inject(method = "onPlayerInteractEntity", at = @At("HEAD"), cancellable = true)
    private void playerbots$freezeBodyEntityUse(PlayerInteractEntityC2SPacket packet, CallbackInfo ci) {
        if (playerbots$isPossessing()) ci.cancel();
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeHotbar(UpdateSelectedSlotC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onHandSwing", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeSwing(HandSwingC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onClientCommand", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeClientCommand(ClientCommandC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onUpdatePlayerAbilities", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeAbilities(UpdatePlayerAbilitiesC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onClickSlot", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeInventory(ClickSlotC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onCloseHandledScreen", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeCloseScreen(CloseHandledScreenC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onButtonClick", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeButton(ButtonClickC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onCraftRequest", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeCraft(CraftRequestC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onSelectMerchantTrade", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeTrade(SelectMerchantTradeC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }

    @Inject(method = "onUpdateBeacon", at = @At("HEAD"), cancellable = true)
    private void playerbots$routeBeacon(UpdateBeaconC2SPacket packet, CallbackInfo ci) {
        ServerPlayerEntity bot = playerbots$controlled();
        if (bot != null) { packet.apply(bot.networkHandler); ci.cancel(); }
    }
}
