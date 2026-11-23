package com.yh.carpet;

import carpet.CarpetServer;
import com.yh.carpet.net.TrialRequestPayload;
import com.yh.carpet.net.TrialResultPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * Fabric entrypoint. Registers the Carpet extension before Carpet itself starts,
 * plus the payloads used by the update-skip trial-run preview.
 */
public class YHAdditionMod implements ModInitializer {
    @Override
    public void onInitialize() {
        CarpetServer.manageExtension(new YHAdditionExtension());

        PayloadTypeRegistry.serverboundPlay().register(TrialRequestPayload.TYPE, TrialRequestPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(TrialResultPayload.TYPE, TrialResultPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(TrialRequestPayload.TYPE,
                (payload, context) -> context.server().execute(() -> TrialManager.handleRequest(context.player())));
    }
}
