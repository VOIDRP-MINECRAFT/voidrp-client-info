package ru.voidrp.clientinfo;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * A channel that only says "this client hides passwords" ({@link PasswordMask}).
 *
 * <p>Registered as an optional server → client payload of the configuration phase, so the
 * client lists it in the {@code minecraft:register} it sends a plugin server while the login
 * window is shown; VoidRpAuth then leaves out its "the password is visible" warning.
 * Nothing is ever sent on it.
 */
public record MaskPayload() implements CustomPacketPayload {

    public static final Type<MaskPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ClientInfoMod.MOD_ID, "password_mask"));

    public static final StreamCodec<FriendlyByteBuf, MaskPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> { }, buf -> new MaskPayload());

    @Override
    public Type<MaskPayload> type() {
        return TYPE;
    }
}
