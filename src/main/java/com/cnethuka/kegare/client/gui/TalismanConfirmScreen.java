package com.cnethuka.kegare.client.gui;

import com.cnethuka.kegare.innatetechnique.InnateTechnique;
import com.cnethuka.kegare.network.TalismanConfirmPayload;
import com.cnethuka.kegare.network.TalismanPromptPayload;
import com.cnethuka.kegare.registry.KegareRegistries;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * "Reroll your Innate Technique?" Confirm sends a reroll REQUEST (the server re-checks the talisman in hand, combat
 * and Severed status, then rolls and consumes it). Cancel or Esc closes it and consumes nothing.
 */
public final class TalismanConfirmScreen extends Screen {
    private final TalismanPromptPayload prompt;
    private int panelX, panelY, panelW, panelH;

    public TalismanConfirmScreen(TalismanPromptPayload prompt) {
        super(Component.translatable("gui.kegare.talisman.title"));
        this.prompt = prompt;
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 16, 280);
        panelH = Math.min(height - 16, 150);
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        int buttonW = Math.min(100, (panelW - 30) / 2);
        int buttonY = panelY + panelH - 28;
        addRenderableWidget(Button.builder(Component.translatable("gui.kegare.talisman.confirm"), b -> confirm())
                .bounds(width / 2 - buttonW - 5, buttonY, buttonW, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.kegare.talisman.cancel"), b -> onClose())
                .bounds(width / 2 + 5, buttonY, buttonW, 20).build());
    }

    private void confirm() {
        PacketDistributor.sendToServer(new TalismanConfirmPayload(prompt.hand()));
        onClose();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB0000000);
        GuiDraw.sprite(g, GuiDraw.PANEL, panelX, panelY, panelW, panelH);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int cx = width / 2;
        int textW = panelW - 20;
        int y = panelY + 8;
        GuiDraw.centered(g, font, title, cx, y, 1f, GuiDraw.GOLD);
        y += font.lineHeight + 6;
        y += GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.talisman.question"), cx, y, textW, GuiDraw.WHITE) + 3;

        InnateTechnique current = prompt.current().map(KegareRegistries.INNATE_TECHNIQUE::get).orElse(null);
        Component currentName = current != null ? current.displayName() : Component.translatable("gui.kegare.talisman.none");
        y += GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.talisman.current", currentName), cx, y, textW, GuiDraw.WHITE);
        y += GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.talisman.pity", prompt.rollsSinceLegendary(), prompt.hardPity()), cx, y, textW, GuiDraw.GOLD) + 3;
        GuiDraw.wrappedCentered(g, font, Component.translatable("gui.kegare.talisman.note"), cx, y, textW, GuiDraw.DARK_GRAY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            confirm();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers); // Esc = cancel
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
