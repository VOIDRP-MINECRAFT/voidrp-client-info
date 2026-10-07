package ru.voidrp.clientinfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/**
 * Tells a VoidRP server which mods this client runs, for its anticheat, and draws the
 * password of a server's login window as stars ({@link PasswordMask}).
 *
 * <p>Made for our 26.2 servers, which run plugins (Paper) while players come in on a
 * NeoForge client. The payload is optional, so the client still joins any server; it is
 * sent only once the server has said it listens on the channel (a Paper plugin announces
 * its channels a moment after the join), and only once per connection.
 */
@Mod(value = ClientInfoMod.MOD_ID, dist = Dist.CLIENT)
public final class ClientInfoMod {

    public static final String MOD_ID = "voidrp_client_info";
    private static final Logger LOG = LogUtils.getLogger();

    /** How long to wait for the server to announce the channel before giving up. */
    private static final int WAIT_TICKS = 20 * 60;

    private ClientPacketListener connection;
    private int waited;
    private boolean sent;

    public ClientInfoMod(IEventBus modBus) {
        modBus.addListener(this::registerPayloads);
        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new PasswordMask());
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        // Server-bound only; the handler never runs on a client.
        event.registrar("1").optional()
                .playToServer(ClientInfoPayload.TYPE, ClientInfoPayload.STREAM_CODEC, (payload, context) -> { })
                // Announces the password stars to the server while the login window is up
                // (configuration phase, see MaskPayload); never sent.
                .configurationToClient(MaskPayload.TYPE, MaskPayload.STREAM_CODEC, (payload, context) -> { });
    }

    @SubscribeEvent
    public void onLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        connection = null;
        waited = 0;
        sent = false;
    }

    @SubscribeEvent
    public void onTick(ClientTickEvent.Post event) {
        ClientPacketListener current = Minecraft.getInstance().getConnection();
        if (current == null || sent) {
            return;
        }
        if (current != connection) {
            connection = current;
            waited = 0;
        }
        if (++waited > WAIT_TICKS || waited % 10 != 0) {
            return;
        }
        if (!current.hasChannel(ClientInfoPayload.TYPE)) {
            return;
        }
        sent = true;
        List<String> mods = ModList.get().getMods().stream()
                .map(info -> info.getModId() + ":" + info.getVersion())
                .collect(Collectors.toList());
        CompletableFuture.supplyAsync(InjectionDetector::detect).thenAccept(result ->
                Minecraft.getInstance().execute(() -> {
                    if (Minecraft.getInstance().getConnection() != current) {
                        return;
                    }
                    ClientPacketDistributor.sendToServer(new ClientInfoPayload(
                            mods, result.javaAgents(), result.suspiciousLibraries(), result.agentsDetected()));
                    LOG.info("Sent the client's mod list to the server ({} mods)", mods.size());
                }));
    }
}
