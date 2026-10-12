package ru.voidrp.clientinfo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * From the server: a VoidRP UI page is up, or down, and what the cursor should look like.
 * Two bytes, {@code op arg}, so a Paper plugin writes it without our codec (see {@link UiPointer}).
 */
public record UiScreenPayload(byte op, byte arg) implements CustomPacketPayload {

    public static final byte CLOSE = 0;
    public static final byte OPEN = 1;
    public static final byte CURSOR = 2;

    public static final Type<UiScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("voidrp", "ui_screen"));

    public static final StreamCodec<FriendlyByteBuf, UiScreenPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.op());
                buf.writeByte(payload.arg());
            },
            buf -> new UiScreenPayload(buf.readByte(), buf.readableBytes() > 0 ? buf.readByte() : 0));

    @Override
    public Type<UiScreenPayload> type() {
        return TYPE;
    }
}
