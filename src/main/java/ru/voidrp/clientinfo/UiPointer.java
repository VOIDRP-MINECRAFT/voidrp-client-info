package ru.voidrp.clientinfo;

import java.lang.reflect.Field;
import java.util.Map;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorType;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.BossEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * The system cursor for VoidRP UI pages.
 *
 * <p>A vanilla client says nothing about its mouse while it plays, so the plugin's pointer is
 * the player's look — at most twenty readings a second, and it shows. With this mod the server
 * says when a page is up ({@link UiScreenPayload}); the mod then holds an empty screen open
 * ({@link PointerScreen}) and reports the mouse ({@link UiInputPayload}). The page itself is
 * still drawn by the server, on the HUD behind the screen, so it looks exactly as before.
 *
 * <p>Input kinds (positions in the page's canvas units: 1024 is the window's height, x is
 * measured from the middle, y from the top):
 * 1 move (a, b), 2 button (a, b, c = button &lt;&lt; 1 | down), 3 wheel (a, b, c = ±1),
 * 4 key (c = GLFW key, a = modifiers), 5 character (c = code point), 6 the screen was taken
 * down here, 7 the window's size in pixels (a, b).
 */
public final class UiPointer {

    private static final byte MOVE = 1;
    private static final byte BUTTON = 2;
    private static final byte WHEEL = 3;
    private static final byte KEY = 4;
    private static final byte CHARACTER = 5;
    private static final byte CLOSED = 6;
    private static final byte WINDOW = 7;

    private static final int KEY_ESCAPE = 256;

    /** The canvas is this tall, whatever the window. */
    private static final double CANVAS = 1024.0;

    /** Moves go out at most this often: the server's own loop runs at sixty. */
    private static final long MOVE_EVERY_NANOS = 16_000_000L;

    /** An escape the server has not answered by then closes the screen here. */
    private static final long ESCAPE_TIMEOUT_MS = 1500;

    /**
     * A page is drawn on white, empty boss bars. With none of them on the HUD for this long
     * the page is gone, whatever the server said — its plugin unloaded (a disabled plugin can
     * send nothing, so no close ever comes), or anything else went wrong — and the screen
     * is taken down rather than leave the player inside it.
     */
    private static final long NO_PAGE_MS = 1500;

    /**
     * How far down each boss bar puts the next one's line, in canvas units: the page's shader
     * reads the line's GUI offset straight as canvas units, so a bar of another plugin above
     * ours puts the whole page this much lower.
     */
    private static final int BAR_STEP = 19;

    /** Bars take a moment to arrive after the page is announced. */
    private static final long OPEN_GRACE_MS = 3000;

    /** Whether the server has a page up for us. */
    private static boolean active;
    private static long openedAt;
    private static long pageSeenAt;
    private static Field bossEvents;
    private static boolean bossEventsLookedUp;
    private static CursorType cursor = CursorTypes.ARROW;
    private static long escapedAt;

    private static int sentX = Integer.MIN_VALUE;
    private static int sentY = Integer.MIN_VALUE;
    private static long sentAt;

    private static ClientPacketListener windowSentTo;
    private static int windowWidth;
    private static int windowHeight;

    UiPointer() {
    }

    static void onScreen(UiScreenPayload payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.execute(() -> {
            switch (payload.op()) {
                case UiScreenPayload.OPEN -> {
                    active = true;
                    escapedAt = 0;
                    openedAt = System.currentTimeMillis();
                    pageSeenAt = openedAt;
                    sentX = Integer.MIN_VALUE;
                    if (!(minecraft.gui.screen() instanceof PointerScreen)) {
                        minecraft.gui.setScreen(new PointerScreen());
                    }
                }
                case UiScreenPayload.CLOSE -> takeDown(false);
                case UiScreenPayload.CURSOR -> cursor = switch (payload.arg()) {
                    case 1 -> CursorTypes.POINTING_HAND;
                    case 2 -> CursorTypes.IBEAM;
                    default -> CursorTypes.ARROW;
                };
                default -> { }
            }
        });
    }

    static CursorType cursor() {
        return cursor;
    }

    /** Called every frame the screen is drawn: sends where the mouse is, if it moved. */
    static void frame() {
        int[] at = position();
        long now = System.nanoTime();
        if ((at[0] != sentX || at[1] != sentY) && now - sentAt >= MOVE_EVERY_NANOS) {
            sentX = at[0];
            sentY = at[1];
            sentAt = now;
            send(MOVE, at[0], at[1], 0);
        }
    }

    static void press(int button, boolean down) {
        int[] at = position();
        send(BUTTON, at[0], at[1], (button << 1) | (down ? 1 : 0));
    }

    static void scroll(double amount) {
        if (amount == 0) {
            return;
        }
        int[] at = position();
        send(WHEEL, at[0], at[1], amount > 0 ? 1 : -1);
    }

    static void key(int key, int modifiers) {
        if (key == KEY_ESCAPE && escapedAt == 0) {
            escapedAt = System.currentTimeMillis();
        }
        send(KEY, modifiers, 0, key);
    }

    static void character(int codePoint) {
        send(CHARACTER, 0, 0, codePoint);
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientPacketListener connection = minecraft.getConnection();
        if (connection == null) {
            active = false;
            return;
        }
        if (connection.hasChannel(UiInputPayload.TYPE)) {
            // The page is laid out for the shape of the window, which the game never tells a
            // server: told once the server is heard from, and again on every resize.
            Window window = minecraft.getWindow();
            int width = window.getScreenWidth();
            int height = window.getScreenHeight();
            if (connection != windowSentTo || width != windowWidth || height != windowHeight) {
                windowSentTo = connection;
                windowWidth = width;
                windowHeight = height;
                if (width > 0 && height > 0) {
                    send(WINDOW, width, height, 0);
                }
            }
        }
        if (active) {
            long now = System.currentTimeMillis();
            Boolean shown = pageShown(minecraft);
            if (shown == null || shown) {
                pageSeenAt = now;
            } else if (now - openedAt > OPEN_GRACE_MS && now - pageSeenAt > NO_PAGE_MS) {
                takeDown(true);
                return;
            }
        }
        // Something else took the screen for a moment — a page's own prompt dialog, a chat
        // line typed with a key the page does not use: back once it is gone.
        if (active && minecraft.gui.screen() == null) {
            minecraft.gui.setScreen(new PointerScreen());
        }
        // An escape nobody answered — the plugin gone, the server stuck: never leave a player
        // inside an invisible screen.
        if (escapedAt != 0 && System.currentTimeMillis() - escapedAt > ESCAPE_TIMEOUT_MS) {
            takeDown(true);
        }
    }

    @SubscribeEvent
    public void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        escapedAt = 0;
        cursor = CursorTypes.ARROW;
        windowSentTo = null;
    }

    private static void takeDown(boolean tellServer) {
        active = false;
        escapedAt = 0;
        cursor = CursorTypes.ARROW;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui.screen() instanceof PointerScreen) {
            minecraft.gui.setScreen(null);
        }
        if (tellServer) {
            send(CLOSED, 0, 0, 0);
        }
    }

    /**
     * Whether one of the page's bars is on the HUD: white and empty of progress, the way the
     * plugin makes them. Null when the bars cannot be read, which counts as shown.
     */
    private static Boolean pageShown(Minecraft minecraft) {
        int above = barsAbove(minecraft);
        return above == -2 ? null : above >= 0;
    }

    /**
     * How many bars of other plugins are drawn above the page's first one: -1 when the page
     * has no bar on the HUD, -2 when the bars cannot be read. The overlay draws them in the
     * order they arrived, which is the order of its map.
     */
    @SuppressWarnings("unchecked")
    private static int barsAbove(Minecraft minecraft) {
        try {
            if (!bossEventsLookedUp) {
                bossEventsLookedUp = true;
                bossEvents = BossHealthOverlay.class.getDeclaredField("events");
                bossEvents.setAccessible(true);
            }
            if (bossEvents == null) {
                return -2;
            }
            BossHealthOverlay overlay = minecraft.gui.hud.getBossOverlay();
            Map<?, LerpingBossEvent> events = (Map<?, LerpingBossEvent>) bossEvents.get(overlay);
            int index = 0;
            for (LerpingBossEvent event : events.values()) {
                if (event.getColor() == BossEvent.BossBarColor.WHITE && event.getProgress() == 0f) {
                    return index;
                }
                index++;
            }
            return -1;
        } catch (ReflectiveOperationException | RuntimeException e) {
            bossEvents = null;
            return -2;
        }
    }

    /** The mouse in canvas units: x from the middle of the window, y from the top. */
    private static int[] position() {
        Minecraft minecraft = Minecraft.getInstance();
        Window window = minecraft.getWindow();
        double width = Math.max(1, window.getScreenWidth());
        double height = Math.max(1, window.getScreenHeight());
        double x = minecraft.mouseHandler.xpos();
        double y = minecraft.mouseHandler.ypos();
        // Where the page is: a bar of another plugin above ours has pushed it down.
        int shift = Math.max(0, barsAbove(minecraft)) * BAR_STEP;
        return new int[] {
                (int) Math.round((x - width / 2) / height * CANVAS),
                (int) Math.round(y / height * CANVAS) - shift,
        };
    }

    private static void send(byte kind, int a, int b, int c) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null || !connection.hasChannel(UiInputPayload.TYPE)) {
            return;
        }
        ClientPacketDistributor.sendToServer(new UiInputPayload(kind, (short) a, (short) b, c));
    }
}
