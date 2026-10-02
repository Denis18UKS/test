package dev.denis.playerbots.client.screen;

import dev.denis.playerbots.client.BotInputController;
import dev.denis.playerbots.client.BotInventoryMirror;
import dev.denis.playerbots.client.ClientBotInfo;
import dev.denis.playerbots.client.ClientBotState;
import dev.denis.playerbots.client.ControlledBotView;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;

/** Vanilla-looking inventory backed by the controlled bot's authoritative server inventory. */
public final class BotInventoryScreen extends HandledScreen<PlayerScreenHandler> {
    private final PlayerInventory botInventory;

    public BotInventoryScreen(MinecraftClient client) {
        this(client, new PlayerInventory(client.player));
    }

    private BotInventoryScreen(MinecraftClient client, PlayerInventory inventory) {
        super(new PlayerScreenHandler(inventory, false, client.player), inventory,
                Text.literal(controlledName()));
        this.botInventory = inventory;
        this.backgroundWidth = 176;
        this.backgroundHeight = 166;
        refreshFromState();
    }

    private static String controlledName() {
        ClientBotInfo bot = ClientBotState.controlled();
        return bot == null ? "Bot" : bot.name();
    }

    @Override
    protected void init() {
        super.init();
        this.titleX = 8;
        this.titleY = 6;
        this.playerInventoryTitleX = 8;
        this.playerInventoryTitleY = 72;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(BACKGROUND_TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight, 256, 256);
        var bot = ControlledBotView.controlledPlayer();
        if (bot != null) {
            InventoryScreen.drawEntity(context, x + 51, y + 75, 30,
                    (float) (x + 51 - mouseX), (float) (y + 75 - mouseY), bot);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, 0x404040, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX, playerInventoryTitleY,
                0x404040, false);
    }

    @Override
    public void handledScreenTick() {
        refreshFromState();
        super.handledScreenTick();
    }

    private void refreshFromState() {
        BotInventoryMirror.BotState state = BotInventoryMirror.state();
        if (state == null || ClientBotState.controlled() == null) {
            close();
            return;
        }
        copy(botInventory.main, state.main());
        copy(botInventory.armor, state.armor());
        copy(botInventory.offHand, state.offhand());
        botInventory.selectedSlot = Math.max(0, Math.min(8, state.selectedSlot()));
        var crafting = handler.getCraftingInput();
        for (int i = 0; i < crafting.size(); i++) {
            crafting.setStack(i, i < state.crafting().size() ? state.crafting().get(i).copy() : ItemStack.EMPTY);
        }
        handler.setCursorStack(state.cursor().copy());
    }

    private static void copy(java.util.List<ItemStack> target, java.util.List<ItemStack> source) {
        int count = Math.min(target.size(), source.size());
        for (int i = 0; i < count; i++) target.set(i, source.get(i).copy());
        for (int i = count; i < target.size(); i++) target.set(i, ItemStack.EMPTY);
    }

    @Override
    protected void onMouseClick(Slot slot, int slotId, int button, SlotActionType actionType) {
        if (slotId >= 0) BotInputController.clickInventory(slotId, button, actionType);
    }

    @Override
    public boolean shouldPause() { return false; }
}
