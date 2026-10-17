package com.yutadigital.herobrine.fabric;

import com.mojang.blaze3d.platform.InputConstants;
import com.yutadigital.herobrine.client.HerobrineClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.lwjgl.glfw.GLFW;

/** Loaded exclusively through Fabric's client entrypoint. */
public final class HerobrineFabricClient implements ClientModInitializer {
    private static final String KEY_CATEGORY = "key.categories.herobrineultimate";

    @Override
    public void onInitializeClient() {
        HerobrineClient.init(FabricLoader.getInstance().getConfigDir(), HerobrineFabricClient::sendRequest);
        KeyMapping open = key("open", GLFW.GLFW_KEY_INSERT);
        KeyMapping master = key("master", GLFW.GLFW_KEY_F6);
        KeyMapping lightning = key("lightning", GLFW.GLFW_KEY_F7);
        KeyMapping save = key("save", GLFW.GLFW_KEY_F8);
        KeyMapping returnToSaved = key("return", GLFW.GLFW_KEY_F9);
        KeyMapping teleport = key("teleport", GLFW.GLFW_KEY_F10);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            HerobrineClient.tick();
            if (client.player == null) return;
            while (open.consumeClick()) HerobrineClient.open();
            while (master.consumeClick()) HerobrineClient.master();
            while (lightning.consumeClick()) HerobrineClient.action("lightning");
            while (save.consumeClick()) HerobrineClient.action("save");
            while (returnToSaved.consumeClick()) HerobrineClient.action("return");
            while (teleport.consumeClick()) HerobrineClient.action("teleport");
        });
        ClientPlayNetworking.registerGlobalReceiver(HerobrineFabric.STATE,
            (client, handler, buffer, sender) -> {
                CompoundTag state = HerobrineFabric.readPacket(buffer);
                if (state != null) client.execute(() -> {
                    if (client.getConnection() == handler) HerobrineClient.accept(state);
                });
            });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> HerobrineClient.clear());
    }

    private static KeyMapping key(String name, int defaultKey) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.herobrineultimate." + name, InputConstants.Type.KEYSYM, defaultKey, KEY_CATEGORY));
    }

    private static void sendRequest(CompoundTag request) {
        if (!ClientPlayNetworking.canSend(HerobrineFabric.REQUEST)) return;
        FriendlyByteBuf buffer = PacketByteBufs.create();
        boolean handedOff = false;
        try {
            buffer.writeNbt(request);
            if (buffer.readableBytes() <= HerobrineFabric.MAX_PACKET_BYTES) {
                ClientPlayNetworking.send(HerobrineFabric.REQUEST, buffer);
                handedOff = true;
            }
        } finally {
            if (!handedOff) buffer.release();
        }
    }
}
