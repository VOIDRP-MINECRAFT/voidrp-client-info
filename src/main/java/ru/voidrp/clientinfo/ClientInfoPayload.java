package ru.voidrp.clientinfo;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client → server: the mods this client runs and what the injection check found.
 *
 * <p>The server is a plugin server, which reads the bytes itself (VoidRP Guard), so the
 * layout is kept plain: a format number, then three lists of strings and a flag, each list
 * a VarInt count followed by VarInt-length UTF-8 strings.
 */
public record ClientInfoPayload(List<String> mods, List<String> javaAgents,
                                List<String> suspiciousLibraries, boolean agentsDetected)
        implements CustomPacketPayload {

    public static final int FORMAT = 1;
    /** A serverbound custom payload may not exceed 32767 bytes; stay well under it. */
    private static final int BUDGET = 30_000;
    private static final int MAX_STRING = 200;

    public static final Type<ClientInfoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ClientInfoMod.MOD_ID, "report"));

    public static final StreamCodec<FriendlyByteBuf, ClientInfoPayload> STREAM_CODEC =
            StreamCodec.of(ClientInfoPayload::write, ClientInfoPayload::read);

    private static void write(FriendlyByteBuf buf, ClientInfoPayload payload) {
        buf.writeVarInt(FORMAT);
        int budget = BUDGET;
        budget = writeList(buf, payload.mods(), budget);
        budget = writeList(buf, payload.javaAgents(), budget);
        writeList(buf, payload.suspiciousLibraries(), budget);
        buf.writeBoolean(payload.agentsDetected());
    }

    /** Writes as many entries as fit the budget; a huge pack is cut short, not dropped. */
    private static int writeList(FriendlyByteBuf buf, List<String> values, int budget) {
        List<String> fit = new ArrayList<>();
        for (String value : values) {
            String v = value.length() > MAX_STRING ? value.substring(0, MAX_STRING) : value;
            int cost = v.getBytes(java.nio.charset.StandardCharsets.UTF_8).length + 3;
            if (cost > budget) break;
            budget -= cost;
            fit.add(v);
        }
        buf.writeVarInt(fit.size());
        for (String v : fit) buf.writeUtf(v, MAX_STRING * 4);
        return budget;
    }

    private static ClientInfoPayload read(FriendlyByteBuf buf) {
        buf.readVarInt();
        List<String> mods = readList(buf);
        List<String> agents = readList(buf);
        List<String> libs = readList(buf);
        return new ClientInfoPayload(mods, agents, libs, buf.readBoolean());
    }

    private static List<String> readList(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> out = new ArrayList<>(n);
        for (int i = 0; i < n; i++) out.add(buf.readUtf(MAX_STRING * 4));
        return out;
    }

    @Override
    public Type<ClientInfoPayload> type() {
        return TYPE;
    }
}
