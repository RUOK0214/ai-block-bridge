package io.github.ruok0214.bridge;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

public final class BridgeMod implements ModInitializer {
    public void onInitialize() {
        PayloadTypeRegistry.serverboundPlay().register(BridgePacket.TYPE,BridgePacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(BridgePacket.TYPE,BridgePacket.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BridgePacket.TYPE,(p,c)->BridgeServer.receive(c.player(),p));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->BridgeServer.clear(handler.player.getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(BridgeServer::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server->BridgeServer.clearAll());
    }
}
