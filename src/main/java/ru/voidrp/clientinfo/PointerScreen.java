package ru.voidrp.clientinfo;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * The screen held open while a VoidRP UI page is up: it draws nothing at all — no dimming,
 * no blur — so the page on the HUD behind it shows exactly as the server drew it, and it
 * exists only so the system cursor is free and the mouse and keys come here instead of to
 * the game. Everything it receives goes to the server ({@link UiPointer}).
 */
final class PointerScreen extends Screen {

    PointerScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Nothing: the page behind is the picture.
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        UiPointer.frame();
        graphics.requestCursor(UiPointer.cursor());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        UiPointer.press(event.button(), true);
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        UiPointer.press(event.button(), false);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return true; // the frame sends where the mouse went
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        UiPointer.scroll(scrollY);
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        UiPointer.key(event.key(), event.modifiers());
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        UiPointer.character(event.codepoint());
        return true;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false; // escape is the server's to answer: back a page, or close
    }
}
