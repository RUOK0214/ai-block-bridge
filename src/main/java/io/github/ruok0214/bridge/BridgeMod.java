package io.github.ruok0214.bridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

public final class BridgeMod implements ModInitializer {
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(BridgePacket.TYPE,BridgePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BridgePacket.TYPE,BridgePacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BridgePacket.TYPE,(p,c)->BridgeServer.receive(c.player(),p));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->BridgeServer.clear(handler.player.getUUID()));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->BridgeServer.clearAll());
    }
}
