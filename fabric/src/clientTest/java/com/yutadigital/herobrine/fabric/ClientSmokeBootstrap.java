package com.yutadigital.herobrine.fabric;

import com.yutadigital.herobrine.client.ClientSmokeTest;
import com.yutadigital.herobrine.client.HerobrineClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Test-only Fabric adapter; no test class is packaged into a production mod. */
public final class ClientSmokeBootstrap implements ClientModInitializer {
    private static boolean opening, started, stopping;
    private static int ticks;

    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(ClientSmokeBootstrap::tick);
    }

    private static void tick(Minecraft mc) {
        if (stopping) return;
        ticks++;
        if (!opening && mc.player == null && mc.getOverlay() == null && ticks > 60) {
            opening = true;
            String world = System.getProperty("herobrineultimate.testWorld", "HerobrineUltimate-ClientTest-Fabric");
            if (new java.io.File(mc.gameDirectory, "saves/" + world + "/level.dat").isFile()) {
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
            } else {
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel(world,
                        new LevelSettings(world, GameType.SURVIVAL, false, Difficulty.NORMAL, false, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(12012026L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if (!started && mc.player != null && HerobrineClient.hasState()) {
            started = true;
            ClientSmokeTest.start();
        }
        if (started) {
            ClientSmokeTest.tick();
            if (ClientSmokeTest.finished()) {
                stopping = true;
                if (mc.level != null) mc.level.disconnect();
                mc.clearLevel();
                mc.stop();
            }
        }
    }

    public static void renderFinished() {
        if (started && !stopping) ClientSmokeTest.renderTick();
    }
}
