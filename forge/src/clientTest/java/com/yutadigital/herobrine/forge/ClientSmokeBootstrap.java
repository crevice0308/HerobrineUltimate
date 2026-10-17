package com.yutadigital.herobrine.forge;

import com.yutadigital.herobrine.client.HerobrineClient;
import com.yutadigital.herobrine.client.ClientSmokeTest;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Test-only source: excluded from production compilation and artifacts. */
@Mod.EventBusSubscriber(modid="herobrineultimate", value=Dist.CLIENT)
public final class ClientSmokeBootstrap {
    private static boolean opening, started, stopping;
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stopping) return;
        Minecraft mc=Minecraft.getInstance();
        ticks++;
        if (!opening && mc.player==null && mc.getOverlay()==null && ticks>60) {
            opening=true;
            String world=System.getProperty("herobrineultimate.testWorld", "HerobrineUltimate-ClientTest");
            if (new java.io.File(mc.gameDirectory,"saves/"+world+"/level.dat").isFile()) {
                mc.createWorldOpenFlows().loadLevel(new TitleScreen(),world);
            } else {
                GameRules rules=new GameRules();
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false,null);
                mc.createWorldOpenFlows().createFreshLevel(world,
                    new LevelSettings(world,GameType.SURVIVAL,false,Difficulty.NORMAL,false,rules,WorldDataConfiguration.DEFAULT),
                    new WorldOptions(12012026L,false,false),
                    access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
        }
        if (!started && mc.player!=null && HerobrineClient.hasState()) {
            started=true;
            ClientSmokeTest.start();
        }
        if (started) {
            ClientSmokeTest.tick();
            if (ClientSmokeTest.finished()) {
                stopping=true;
                if (mc.level != null) mc.level.disconnect();
                mc.clearLevel();
                mc.stop();
            }
        }
    }
    @SubscribeEvent public static void render(TickEvent.RenderTickEvent event) {
        if (started && !stopping && event.phase==TickEvent.Phase.END) ClientSmokeTest.renderTick();
    }
}
