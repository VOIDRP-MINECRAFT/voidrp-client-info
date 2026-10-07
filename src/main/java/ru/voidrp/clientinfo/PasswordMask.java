package ru.voidrp.clientinfo;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.dialog.DialogScreen;
import net.minecraft.network.chat.Style;
import net.minecraft.server.dialog.Dialog;
import net.minecraft.server.dialog.Input;
import net.minecraft.server.dialog.input.TextInput;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * Stars instead of the password in a server's dialog window (VoidRpAuth's login and sign-up).
 *
 * <p>A dialog's text field has no password mode in the protocol, so the plugin cannot ask
 * for one: the client draws it. When a dialog screen is built, every single-line text input
 * whose key contains {@code password} gets a formatter that draws one {@code *} per
 * character. Only the drawing changes: the value sent to the server is the typed text.
 *
 * <p>The edit boxes are found in the screen's widgets in the order of the dialog's inputs
 * (a multi-line input is another widget, so it is skipped on both sides).
 */
public final class PasswordMask {

    private static final EditBox.TextFormatter STARS =
            (text, offset) -> FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY);

    private static Field dialogField;
    private static boolean lookedUp;

    @SubscribeEvent
    public void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof DialogScreen<?> screen)) {
            return;
        }
        Dialog dialog = dialogOf(screen);
        if (dialog == null) {
            return;
        }
        List<Boolean> secret = new ArrayList<>();
        for (Input input : dialog.common().inputs()) {
            if (input.control() instanceof TextInput text && text.multiline().isEmpty()) {
                secret.add(input.key().toLowerCase(Locale.ROOT).contains("password"));
            }
        }
        if (!secret.contains(Boolean.TRUE)) {
            return;
        }
        List<EditBox> boxes = new ArrayList<>();
        collect(screen.children(), boxes);
        if (boxes.size() != secret.size()) {
            return; // not the layout we know: better plain text than stars on the wrong field
        }
        for (int i = 0; i < boxes.size(); i++) {
            if (secret.get(i)) {
                boxes.get(i).addFormatter(STARS);
            }
        }
    }

    private static void collect(List<? extends GuiEventListener> children, List<EditBox> out) {
        for (GuiEventListener child : children) {
            if (child instanceof EditBox box) {
                out.add(box);
            } else if (child instanceof ContainerEventHandler container) {
                collect(container.children(), out);
            }
        }
    }

    private static Dialog dialogOf(DialogScreen<?> screen) {
        if (!lookedUp) {
            lookedUp = true;
            for (Field f : DialogScreen.class.getDeclaredFields()) {
                if (Dialog.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    dialogField = f;
                    break;
                }
            }
        }
        try {
            return dialogField == null ? null : (Dialog) dialogField.get(screen);
        } catch (IllegalAccessException e) {
            return null;
        }
    }
}
