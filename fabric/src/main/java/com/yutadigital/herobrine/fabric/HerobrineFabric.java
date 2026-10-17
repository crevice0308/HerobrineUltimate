package com.yutadigital.herobrine.fabric;

import com.yutadigital.herobrine.server.HerobrineServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Fabric's server bridge. It deliberately has no dependency on client classes. */
public final class HerobrineFabric implements ModInitializer {
    public static final ResourceLocation STATE = new ResourceLocation("herobrineultimate", "state");
    public static final ResourceLocation REQUEST = new ResourceLocation("herobrineultimate", "request");
    public static final int MAX_PACKET_BYTES = 8192;

    @Override
    public void onInitialize() {
        ServerLifecycleEvents.SERVER_STARTING.register(server ->
            HerobrineServer.init(FabricLoader.getInstance().getConfigDir(), HerobrineFabric::sendState));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HerobrineServer.stop());
        ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> HerobrineServer.entityLoaded(entity));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                HerobrineServer.tick(player);
            }
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            HerobrineServer.join(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            HerobrineServer.logout(handler.player));

        ServerPlayNetworking.registerGlobalReceiver(REQUEST, (server, player, handler, buffer, sender) -> {
            CompoundTag request = readPacket(buffer);
            if (request == null) return;
            server.execute(() -> {
                // The connection can close while a packet is waiting for the server thread.
                if (server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    HerobrineServer.handle(player, request);
                }
            });
        });
    }

    private static void sendState(ServerPlayer player, CompoundTag state) {
        if (!ServerPlayNetworking.canSend(player, STATE)) return;
        FriendlyByteBuf buffer = PacketByteBufs.create();
        boolean handedOff = false;
        try {
            buffer.writeNbt(state);
            if (buffer.readableBytes() <= MAX_PACKET_BYTES) {
                ServerPlayNetworking.send(player, STATE, buffer);
                handedOff = true;
            }
        } finally {
            // The packet retains this buffer until its asynchronous write completes.
            if (!handedOff) buffer.release();
        }
    }

    /** Reject empty, oversized, non-compound and trailing-byte packets before dispatch. */
    static CompoundTag readPacket(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() < 1 || buffer.readableBytes() > MAX_PACKET_BYTES) return null;
        try {
            CompoundTag tag = buffer.readNbt();
            return tag != null && !buffer.isReadable() ? tag : null;
        } catch (RuntimeException malformedPacket) {
            return null;
        }
    }
}
