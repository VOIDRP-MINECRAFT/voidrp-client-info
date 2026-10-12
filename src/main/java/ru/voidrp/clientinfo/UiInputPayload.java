package ru.voidrp.clientinfo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * To the server: the mouse and keyboard while a VoidRP UI page is up. Nine big-endian bytes,
 * {@code kind:byte a:short b:short c:int}, the same for every kind so the plugin reads it with
 * a plain stream. The kinds are listed in {@link UiPointer}.
 */
public record UiInputPayload(byte kind, short a, short b, int c) implements CustomPacketPayload {

    public static final Type<UiInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("voidrp", "ui_input"));

    public static final StreamCodec<FriendlyByteBuf, UiInputPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.kind());
                buf.writeShort(payload.a());
                buf.writeShort(payload.b());
                buf.writeInt(payload.c());
            },
            buf -> new UiInputPayload(buf.readByte(), buf.readShort(), buf.readShort(), buf.readInt()));

    @Override
    public Type<UiInputPayload> type() {
        return TYPE;
    }
}
