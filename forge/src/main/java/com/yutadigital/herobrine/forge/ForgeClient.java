package com.yutadigital.herobrine.forge;

import com.yutadigital.herobrine.client.HerobrineClient;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.lwjgl.glfw.GLFW;

/** This class is referenced only through a DistExecutor client gate. */
public final class ForgeClient {
    private static final String CATEGORY = "key.categories.herobrineultimate";
    private static final KeyMapping OPEN = key("open", GLFW.GLFW_KEY_INSERT);
    private static final KeyMapping MASTER = key("master", GLFW.GLFW_KEY_F6);
    private static final KeyMapping LIGHTNING = key("lightning", GLFW.GLFW_KEY_F7);
    private static final KeyMapping SAVE = key("save", GLFW.GLFW_KEY_F8);
    private static final KeyMapping RETURN = key("return", GLFW.GLFW_KEY_F9);
    private static final KeyMapping TELEPORT = key("teleport", GLFW.GLFW_KEY_F10);
    private static KeyMapping key(String action, int value) {
        return new KeyMapping("key.herobrineultimate." + action, InputConstants.Type.KEYSYM, value, CATEGORY);
    }
    public static void register() {
        HerobrineClient.init(FMLPaths.CONFIGDIR.get(), tag ->
                HerobrineForge.NETWORK.sendToServer(new HerobrineForge.Request(tag)));
        FMLJavaModLoadingContext.get().getModEventBus().addListener(ForgeClient::keys);
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::tick);
        MinecraftForge.EVENT_BUS.addListener(ForgeClient::disconnect);
    }
    public static void accept(CompoundTag state, net.minecraft.network.Connection connection) {
        var current = net.minecraft.client.Minecraft.getInstance().getConnection();
        if (state != null && current != null && current.getConnection() == connection) HerobrineClient.accept(state);
    }
    private static void keys(RegisterKeyMappingsEvent event) {
        for (KeyMapping key : new KeyMapping[]{OPEN, MASTER, LIGHTNING, SAVE, RETURN, TELEPORT}) event.register(key);
    }
    private static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        HerobrineClient.tick();
        while (OPEN.consumeClick()) HerobrineClient.open();
        while (MASTER.consumeClick()) HerobrineClient.master();
        while (LIGHTNING.consumeClick()) HerobrineClient.action("lightning");
        while (SAVE.consumeClick()) HerobrineClient.action("save");
        while (RETURN.consumeClick()) HerobrineClient.action("return");
        while (TELEPORT.consumeClick()) HerobrineClient.action("teleport");
    }
    private static void disconnect(ClientPlayerNetworkEvent.LoggingOut event) { HerobrineClient.clear(); }
}
