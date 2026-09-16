package dev.denis.playerbots.client.screen;

import dev.denis.playerbots.client.ClientBotInfo;
import dev.denis.playerbots.client.ClientBotState;
import dev.denis.playerbots.client.PlayerBotsClient;
import dev.denis.playerbots.core.RadialMath;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public final class BotRadialScreen extends Screen {
    private static final double DEAD_ZONE = 28.0;
    private final List<Entry> entries = new ArrayList<>();
    private int hovered = -1;

    public BotRadialScreen() {
        super(Text.literal("Player Bots"));
        for (ClientBotInfo bot : ClientBotState.bots()) entries.add(new Entry(bot.name(), bot, false));
        if (ClientBotState.controlled() != null) entries.add(new Entry(Text.translatable("screen.playerbots.return").getString(), null, true));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int cx = width / 2, cy = height / 2;
        double dx = mouseX - cx, dy = mouseY - cy;
        hovered = RadialMath.sectorAt(dx, dy, DEAD_ZONE, entries.size());
        int radius = Math.min(130, Math.min(width, height) / 3);
        context.fill(cx - 23, cy - 23, cx + 23, cy + 23, 0xA0000000);
        context.drawCenteredTextWithShadow(textRenderer, "LMB", cx, cy - 4, 0xFFFFFF);

        for (int i = 0; i < entries.size(); i++) {
            double angle = i * Math.PI * 2.0 / entries.size() - Math.PI / 2.0;
            int x = cx + (int) Math.round(Math.cos(angle) * radius);
            int y = cy + (int) Math.round(Math.sin(angle) * radius);
            Entry entry = entries.get(i);
            int w = Math.max(72, textRenderer.getWidth(entry.label) + 16);
            int bg = i == hovered ? 0xE0FFFFFF : 0xB0202020;
            int fg = i == hovered ? 0x101010 : (entry.release ? 0xFFD080 : 0xFFFFFF);
            context.fill(x - w / 2, y - 12, x + w / 2, y + 12, bg);
            context.drawCenteredTextWithShadow(textRenderer, entry.label, x, y - 4, fg);
        }
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("Наведи курсор и нажми ЛКМ"), cx, cy + 38, 0xDDDDDD);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && hovered >= 0 && hovered < entries.size()) {
            Entry entry = entries.get(hovered);
            if (entry.release) ClientBotState.release(); else ClientBotState.control(entry.bot.uuid());
            close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (PlayerBotsClient.matchesRadialKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() { return true; }

    @Override
    public boolean shouldPause() { return false; }

    private record Entry(String label, ClientBotInfo bot, boolean release) {}
}
