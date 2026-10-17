package com.yutadigital.herobrine.server;

import com.mojang.authlib.GameProfile;
import com.yutadigital.herobrine.core.Ability;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

/** Real Minecraft/Forge world tests, excluded from normal distribution jars. */
@GameTestHolder("herobrineultimate")
@PrefixGameTestTemplate(false)
public final class HerobrineServerGameTests {
    @BeforeBatch(batch = "herobrine_server")
    public static void setup(ServerLevel level) {
        try {
            // This batch runs only in the disposable GameTest server. No network
            // peer is needed to exercise the actual server/event paths.
            var directory = Files.createTempDirectory("herobrine-gametest-restricted-");
            Files.writeString(directory.resolve("herobrine-ultimate-server.properties"), "require-permission=true\n");
            HerobrineServer.init(directory, (player, packet) -> {});
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    @BeforeBatch(batch = "herobrine_default")
    public static void setupDefault(ServerLevel level) {
        try {
            HerobrineServer.init(Files.createTempDirectory("herobrine-gametest-default-"), (player, packet) -> {});
        } catch (IOException error) { throw new IllegalStateException(error); }
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_default")
    public static void defaultConfigurationNeedsNoOpOrCheats(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, false);
        try {
            helper.assertFalse(player.hasPermissions(2), "Fixture has no OP permission");
            helper.assertTrue(HerobrineServer.allowed(player), "Fresh default configuration permits a non-OP player");
            HerobrineServer.handle(player, toggle("FLIGHT", true));
            HerobrineServer.handle(player, toggle("HUNGER", true));
            HerobrineServer.handle(player, toggle("WORLD", true));
            player.getFoodData().setFoodLevel(1);
            HerobrineServer.tick(player);
            helper.assertTrue(player.getAbilities().mayfly && player.getFoodData().getFoodLevel() == 20,
                    "Default non-OP player can use Flight and Hunger");
            HerobrineServer.handle(player, request("night"));
            helper.assertTrue(Math.floorMod(helper.getLevel().getDayTime(), 24000) == 13000,
                    "Default non-OP player can execute server-authoritative world actions");
            helper.assertFalse(player.hasPermissions(2), "Abilities do not promote the player to OP");
        } finally { cleanup(player); }
        helper.succeed();
    }

    private static ServerPlayer player(GameTestHelper helper, boolean op) {
        var server = helper.getLevel().getServer();
        // Vanilla's helper hardcodes creative mode. Build a real survival
        // player with a packet listener whose unsent packets stay queued.
        // Avoid the login handshake: Forge requires a live Netty pipeline for
        // placeNewPlayer, which is unrelated to these world-logic tests.
        ServerPlayer player = new ServerPlayer(server, helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "hb-test-" + UUID.randomUUID().toString().substring(0, 6)));
        new ServerGamePacketListenerImpl(server, new Connection(PacketFlow.SERVERBOUND), player);
        helper.getLevel().addNewPlayer(player);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 1, 8.5));
        player.setPos(at);
        player.setYRot(0);
        player.setXRot(0);
        player.invulnerableTime = 0;
        if (op) server.getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 4, false));
        HerobrineServer.join(player);
        if (op) helper.assertTrue(player.hasPermissions(2) && HerobrineServer.allowed(player), "Restricted fixture has an explicit level-4 OP entry");
        return player;
    }
    private static void cleanup(ServerPlayer player) {
        HerobrineServer.logout(player);
        player.getServer().getPlayerList().deop(player.getGameProfile());
        player.serverLevel().removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
    }
    private static HerobrineWorldData.Record record(ServerPlayer player) {
        return HerobrineWorldData.get(player.getServer()).player(player.getUUID());
    }
    private static void enable(ServerPlayer player, Ability... abilities) {
        for (Ability ability : abilities) record(player).settings.set(ability, true);
        HerobrineServer.tick(player);
    }
    private static CompoundTag request(String op) {
        CompoundTag tag = new CompoundTag(); tag.putString("op", op); return tag;
    }
    private static CompoundTag toggle(String name, boolean flag) {
        CompoundTag tag = request("toggle"); tag.putString("key", name); tag.putBoolean("flag", flag); return tag;
    }
    private static void floor(GameTestHelper helper) {
        for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) helper.setBlock(x, 0, z, Blocks.STONE);
    }
    private static CompoundTag waypoint(GameTestHelper helper, double x, double y, double z) {
        Vec3 at = helper.absoluteVec(new Vec3(x, y, z));
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", helper.getLevel().dimension().location().toString());
        tag.putDouble("x", at.x); tag.putDouble("y", at.y); tag.putDouble("z", at.z);
        tag.putFloat("yaw", 42); tag.putFloat("pitch", 12);
        return tag;
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server", timeoutTicks = 120)
    public static void damagePolicies(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        helper.runAfterDelay(65, () -> {
        try {
            float original = player.getHealth();
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().generic(), 1);
            helper.assertTrue(player.getHealth() < original, "Vanilla control damage confirms spawn protection has expired");
            player.setHealth(original); player.invulnerableTime = 0;
            enable(player, Ability.GOD);
            player.hurt(player.damageSources().generic(), 3);
            player.hurt(player.damageSources().inFire(), 3);
            helper.assertTrue(player.getHealth() == original, "Forge LivingAttackEvent blocks real generic/fire damage under God mode");
            helper.assertFalse(HerobrineServer.preventDamage(player, player.damageSources().fellOutOfWorld()), "Void remains a God mode exception");
            helper.assertFalse(HerobrineServer.preventDamage(player, player.damageSources().genericKill()), "/kill remains a God mode exception");
            record(player).settings.set(Ability.GOD, false);
            enable(player, Ability.FLIGHT);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().fall(), 5);
            helper.assertTrue(player.getHealth() == original, "Flight alone cancels actual fall damage through Forge's damage hook");
            record(player).settings.set(Ability.FLIGHT, false);
            HerobrineServer.tick(player);
            player.invulnerableTime = 0;
            player.hurt(player.damageSources().fall(), 3);
            helper.assertTrue(player.getHealth() < original, "Fall damage resumes when Flight and God are both disabled");
            player.setHealth(original); player.invulnerableTime = 0;
            enable(player, Ability.GOD);
            player.hurt(player.damageSources().fellOutOfWorld(), 2);
            helper.assertTrue(player.getHealth() < original, "Actual void damage passes through enabled God mode");
            helper.succeed();
        } finally { cleanup(player); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server")
    public static void hungerAndAttributeLifecycle(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        try {
            double move = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double attack = player.getAttributeValue(Attributes.ATTACK_SPEED);
            double damage = player.getAttributeValue(Attributes.ATTACK_DAMAGE);
            var settings = record(player).settings;
            settings.multiplier("move", 3); settings.multiplier("mine", 25);
            settings.multiplier("attack", 4); settings.multiplier("damage", 50);
            player.getFoodData().setFoodLevel(2); player.getFoodData().setSaturation(0);
            player.getFoodData().setExhaustion(3);
            enable(player, Ability.HUNGER, Ability.MOTION, Ability.COMBAT);
            helper.assertTrue(player.getFoodData().getFoodLevel() == 20 && player.getFoodData().getSaturationLevel() == 20,
                    "Actual FoodData is replenished to 20/20");
            helper.assertTrue(player.getFoodData().getExhaustionLevel() == 0, "Hunger removes exhaustion");
            helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - move * 3) < .00001,
                    "Motion updates the real movement attribute");
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_SPEED) == attack * 4, "Attack speed multiplier is applied");
            helper.assertTrue(player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage * 50, "Combat damage multiplier is applied");
            helper.assertTrue(HerobrineServer.mining(player, 2) == 50, "Mining uses configured multiplier");
            settings.master = false; HerobrineServer.tick(player);
            helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == move
                    && player.getAttributeValue(Attributes.ATTACK_SPEED) == attack
                    && player.getAttributeValue(Attributes.ATTACK_DAMAGE) == damage, "Master OFF restores the three original attributes");
            player.getFoodData().setFoodLevel(9); HerobrineServer.tick(player);
            helper.assertTrue(player.getFoodData().getFoodLevel() == 9, "Master OFF leaves food consumption alone");
            helper.assertTrue(settings.selected(Ability.HUNGER) && settings.selected(Ability.MOTION), "Master preserves individual selections");
            settings.master = true; HerobrineServer.tick(player);
            helper.assertTrue(player.getFoodData().getFoodLevel() == 20
                    && Math.abs(player.getAttributeValue(Attributes.MOVEMENT_SPEED) - move * 3) < .00001,
                    "Master ON resumes stored selections and multipliers");
        } finally { cleanup(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server")
    public static void flightAndInvisibilityRestore(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        try {
            enable(player, Ability.FLIGHT, Ability.INVISIBILITY);
            helper.assertTrue(player.getAbilities().mayfly && player.isInvisible(), "Survival flight and invisibility are applied");
            record(player).settings.master = false; HerobrineServer.tick(player);
            helper.assertFalse(player.getAbilities().mayfly || player.isInvisible(), "Master OFF removes only mod-owned flight/invisibility");
            player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.setInvisible(true);
            record(player).settings.master = true; HerobrineServer.tick(player);
            record(player).settings.master = false; HerobrineServer.tick(player);
            helper.assertTrue(player.getAbilities().mayfly && player.getAbilities().flying && player.isInvisible(),
                    "Pre-existing external flight and invisibility are restored: mayfly=" + player.getAbilities().mayfly
                            + ", flying=" + player.getAbilities().flying + ", invisible=" + player.isInvisible());
            player.setInvisible(false); player.getAbilities().mayfly = false; player.getAbilities().flying = false;
            record(player).settings.master = true; HerobrineServer.tick(player);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 200));
            record(player).settings.master = false; HerobrineServer.tick(player);
            helper.assertTrue(player.hasEffect(MobEffects.INVISIBILITY) && player.isInvisible(),
                    "Disabling mod invisibility preserves an independently active vanilla potion");
            player.setGameMode(GameType.CREATIVE);
            record(player).settings.master = true; HerobrineServer.tick(player);
            record(player).settings.master = false; HerobrineServer.tick(player);
            helper.assertTrue(player.getAbilities().mayfly, "Disabling Flight preserves native creative flight");
        } finally { cleanup(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server")
    public static void permissionsAndMalformedPackets(GameTestHelper helper) {
        ServerPlayer player = player(helper, false);
        try {
            HerobrineServer.handle(player, toggle("FLIGHT", true));
            helper.assertFalse(record(player).settings.selected(Ability.FLIGHT) || player.getAbilities().mayfly,
                    "An unauthorized real player cannot enable Flight by packet");
            player.getServer().getPlayerList().getOps().add(new ServerOpListEntry(player.getGameProfile(), 4, false));
            helper.assertTrue(player.hasPermissions(2), "Explicit permission grant is visible to the real player");
            HerobrineServer.handle(player, toggle("FLIGHT", true));
            helper.assertTrue(player.getAbilities().mayfly, "Authorized toggle packet takes effect on real abilities");
            CompoundTag number = request("number"); number.putString("key", "move"); number.putDouble("value", Double.NaN);
            HerobrineServer.handle(player, number);
            helper.assertTrue(record(player).settings.multiplier("move") == 1, "NaN cannot alter the stored multiplier");
            number.putDouble("value", 1000000); HerobrineServer.handle(player, number);
            helper.assertTrue(record(player).settings.multiplier("move") == 10, "Excessive packet multiplier is clamped on the server");
            HerobrineServer.handle(player, toggle("UNKNOWN", true));
            CompoundTag nested = toggle("GOD", true); nested.put("nested", new CompoundTag());
            HerobrineServer.handle(player, nested);
            helper.assertFalse(record(player).settings.selected(Ability.GOD), "Unknown enum and nested packets cannot mutate selections");
            player.getServer().getPlayerList().deop(player.getGameProfile());
            HerobrineServer.tick(player);
            helper.assertFalse(player.getAbilities().mayfly, "Permission revocation removes an active ability immediately on tick");
            helper.assertTrue(record(player).settings.selected(Ability.FLIGHT), "Permission loss preserves the player's selection for a later grant");
        } finally { cleanup(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server")
    public static void staleRecoveryMetadataCannotGrantAbilities(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        try {
            HerobrineServer.logout(player);
            var saved = record(player);
            saved.flightOwned = true; saved.priorMayFly = true; saved.priorFlying = true;
            saved.invisibilityOwned = true; saved.priorInvisible = true;
            HerobrineServer.join(player);
            helper.assertFalse(player.getAbilities().mayfly || player.getAbilities().flying || player.isInvisible(),
                    "Stale world SavedData cannot grant flight or invisibility to an untagged clean player");
            helper.assertFalse(saved.flightOwned || saved.invisibilityOwned, "Stale ownership metadata is cleared");
            HerobrineServer.logout(player);
            player.getAbilities().mayfly = true; player.getAbilities().flying = true; player.setInvisible(true);
            player.addTag("herobrineultimate:flight_owned"); player.addTag("herobrineultimate:invisibility_owned");
            HerobrineServer.join(player);
            helper.assertFalse(player.getAbilities().mayfly || player.getAbilities().flying || player.isInvisible(),
                    "Atomic player-file ownership tags recover interrupted mod grants");
            helper.assertFalse(player.getTags().contains("herobrineultimate:flight_owned")
                    || player.getTags().contains("herobrineultimate:invisibility_owned"), "Recovery removes ownership tags");
        } finally { cleanup(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server", timeoutTicks = 100)
    public static void safeWaypointReturn(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        enable(player, Ability.TELEPORT);
        var saved = record(player);
        saved.waypoint = waypoint(helper, 3.5, 1, 3.5);
        HerobrineServer.handle(player, request("return"));
        helper.assertTrue(player.position().distanceToSqr(helper.absoluteVec(new Vec3(3.5, 1, 3.5))) < .00001,
                "Waypoint return teleports the actual player onto supported safe terrain");
        helper.assertTrue(player.getYRot() == 42 && player.getXRot() == 12, "Safe return restores saved rotation");
        helper.runAfterDelay(11, () -> {
            try {
                Vec3 previous = player.position();
                helper.setBlock(5, 0, 5, Blocks.MAGMA_BLOCK);
                saved.waypoint = waypoint(helper, 5.5, 1, 5.5);
                HerobrineServer.handle(player, request("return"));
                helper.assertTrue(player.position().distanceToSqr(previous) < .00001, "Return refuses a magma-block support surface");
                saved.waypoint = waypoint(helper, 6.5, 1, 6.5);
                saved.waypoint.putFloat("yaw", Float.NaN);
            } catch (Throwable failure) { cleanup(player); throw failure; }
        });
        helper.runAfterDelay(22, () -> {
            try {
                Vec3 previous = player.position();
                HerobrineServer.handle(player, request("return"));
                helper.assertTrue(player.position().distanceToSqr(previous) < .00001 && Float.isFinite(player.getYRot()),
                        "Corrupt nonfinite waypoint rotation cannot reach player teleport state");
                helper.setBlock(6, 0, 6, Blocks.STONE_SLAB);
                saved.waypoint = waypoint(helper, 6.5, .5, 6.5);
            } catch (Throwable failure) { cleanup(player); throw failure; }
        });
        helper.runAfterDelay(33, () -> {
            try {
                HerobrineServer.handle(player, request("return"));
                helper.assertTrue(player.position().distanceToSqr(helper.absoluteVec(new Vec3(6.5, .5, 6.5))) < .00001,
                        "Safe return accepts fractional-height slab support");
                helper.succeed();
            } finally { cleanup(player); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server")
    public static void registeredMobLifecycle(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        Wolf wolf = EntityType.WOLF.create(helper.getLevel());
        Wolf unregistered = EntityType.WOLF.create(helper.getLevel());
        helper.assertTrue(wolf != null && unregistered != null, "Vanilla wolf type is available");
        try {
            var saved = record(player);
            wolf.moveTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
            wolf.addTag("herobrine_owner:" + player.getUUID());
            saved.mobs.put(wolf.getUUID(), "minecraft:the_nether");
            helper.getLevel().addFreshEntity(wolf);
            helper.assertTrue(helper.getLevel().dimension().location().toString().equals(saved.mobs.get(wolf.getUUID())),
                    "Forge entity-load event repairs a registered bound mob's changed dimension");
            unregistered.moveTo(helper.absoluteVec(new Vec3(5.5, 1, 4.5)));
            unregistered.addTag("herobrine_owner:" + player.getUUID());
            helper.getLevel().addFreshEntity(unregistered);
            helper.assertFalse(saved.mobs.containsKey(unregistered.getUUID()), "An unregistered ownership tag cannot adopt an arbitrary mob");
            wolf.hurt(wolf.damageSources().genericKill(), 10000);
            helper.assertFalse(wolf.isAlive() || saved.mobs.containsKey(wolf.getUUID()),
                    "Actual LivingDeathEvent removes the dead mob's ownership registration");
        } finally { wolf.discard(); unregistered.discard(); cleanup(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server", timeoutTicks = 160)
    public static void summonRallyAndDismiss(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        enable(player, Ability.MOB);
        String[] names = {"wolf", "cat", "parrot", "iron_golem"};
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            helper.runAfterDelay(i * 21L + 1, () -> {
                try {
                    CompoundTag summon = request("summon"); summon.putString("key", names[index]);
                    HerobrineServer.handle(player, summon);
                    helper.assertTrue(record(player).mobs.size() == index + 1, "Summoning creates and registers " + names[index]);
                } catch (Throwable failure) { cleanup(player); throw failure; }
            });
        }
        helper.runAfterDelay(90, () -> {
            try {
                for (UUID id : record(player).mobs.keySet()) {
                    Entity entity = helper.getLevel().getEntity(id);
                    helper.assertTrue(entity instanceof Mob && entity.getTags().contains("herobrine_owner:" + player.getUUID()),
                            "A summoned real mob carries the matching owner tag");
                }
                HerobrineServer.handle(player, request("rally"));
                HerobrineServer.handle(player, request("dismiss"));
                helper.assertTrue(record(player).mobs.isEmpty(), "Dismiss removes all four loaded bound mobs and registrations");
                helper.succeed();
            } finally { cleanup(player); }
        });
    }
    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server", timeoutTicks = 80)
    public static void combatRaycastAndInstantConfirmation(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        player.setPos(helper.absoluteVec(new Vec3(8.5, 1, 4.5)));
        Zombie target = helper.spawnWithNoFreeWill(EntityType.HUSK, new Vec3(8.5, 1, 8.5));
        float original = target.getHealth();
        record(player).settings.multiplier("damage", 5);
        enable(player, Ability.COMBAT);
        CompoundTag attack = request("attack"); attack.putBoolean("flag", true);
        HerobrineServer.handle(player, attack);
        helper.assertTrue(target.getHealth() == original, "Instant attack requires an explicit confirmation");
        helper.runAfterDelay(6, () -> {
            try {
                helper.setBlock(8, 2, 6, Blocks.STONE);
                HerobrineServer.handle(player, request("attack"));
                helper.assertTrue(target.getHealth() == original, "Combat raycast cannot attack through a solid wall");
            } catch (Throwable failure) { target.discard(); cleanup(player); throw failure; }
        });
        helper.runAfterDelay(12, () -> {
            try {
                helper.setBlock(8, 2, 6, Blocks.AIR);
                target.invulnerableTime = 0;
                HerobrineServer.handle(player, request("attack"));
                helper.assertTrue(target.getHealth() < original && target.isAlive(), "Configured normal attack damages the actual visible target");
            } catch (Throwable failure) { target.discard(); cleanup(player); throw failure; }
        });
        helper.runAfterDelay(18, () -> {
            try {
                target.invulnerableTime = 0;
                attack.putBoolean("confirm", true);
                HerobrineServer.handle(player, attack);
                helper.assertFalse(target.isAlive(), "Confirmed instant attack kills the target through ordinary damage processing");
                helper.succeed();
            } finally { target.discard(); cleanup(player); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server", timeoutTicks = 80)
    public static void visualLightningAndCooldown(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        player.setPos(helper.absoluteVec(new Vec3(8.5, 1, 4.5)));
        helper.setBlock(8, 2, 8, Blocks.STONE);
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new Vec3(8.5, 1, 7.5));
        float health = pig.getHealth();
        enable(player, Ability.LIGHTNING);
        AABB box = new AABB(helper.absoluteVec(new Vec3(0, 0, 0)), helper.absoluteVec(new Vec3(16, 8, 16)));
        int before = helper.getLevel().getEntitiesOfClass(LightningBolt.class, box).size();
        HerobrineServer.handle(player, request("lightning"));
        int after = helper.getLevel().getEntitiesOfClass(LightningBolt.class, box).size();
        helper.assertTrue(after == before + 1, "Lightning action spawns one real lightning-bolt entity at the aim point");
        HerobrineServer.handle(player, request("lightning"));
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(LightningBolt.class, box).size() == after,
                "A repeated same-tick action is rejected by the lightning cooldown");
        helper.runAfterDelay(4, () -> {
            try {
                helper.assertTrue(pig.isAlive() && pig.getHealth() == health && !pig.isOnFire(),
                        "Default visual lightning neither damages nor ignites a nearby real mob");
                helper.succeed();
            } finally { pig.discard(); cleanup(player); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server", timeoutTicks = 80)
    public static void terrainConfirmationAndProtectedBlocks(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        player.setPos(helper.absoluteVec(new Vec3(8.5, 1, 4.5)));
        helper.setBlock(8, 2, 8, Blocks.STONE);
        helper.setBlock(7, 2, 8, Blocks.BEDROCK);
        helper.setBlock(9, 2, 8, Blocks.CHEST);
        enable(player, Ability.WORLD);
        HerobrineServer.handle(player, request("terrain"));
        helper.assertBlockPresent(Blocks.STONE, 8, 2, 8);
        helper.runAfterDelay(21, () -> {
            try {
                CompoundTag terrain = request("terrain"); terrain.putBoolean("confirm", true);
                HerobrineServer.handle(player, terrain);
                helper.assertBlockPresent(Blocks.AIR, 8, 2, 8);
                helper.assertBlockPresent(Blocks.BEDROCK, 7, 2, 8);
                helper.assertBlockPresent(Blocks.CHEST, 9, 2, 8);
                helper.succeed();
            } finally { cleanup(player); }
        });
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server")
    public static void transformationDisplayAndEffects(GameTestHelper helper) {
        floor(helper);
        ServerPlayer player = player(helper, true);
        try {
            String name = player.getGameProfile().getName(); UUID id = player.getGameProfile().getId();
            AABB box = new AABB(helper.absoluteVec(new Vec3(0, 0, 0)), helper.absoluteVec(new Vec3(16, 8, 16)));
            int before = helper.getLevel().getEntitiesOfClass(LightningBolt.class, box).size();
            enable(player, Ability.EFFECTS, Ability.HEROBRINE);
            helper.assertTrue(HerobrineServer.transformed(player.getUUID()), "Server transformation state is active");
            helper.assertTrue(player.getDisplayName().getString().equals("Herobrine"), "Real player display-name mixin uses Herobrine");
            helper.assertTrue(player.getGameProfile().getName().equals(name) && player.getGameProfile().getId().equals(id),
                    "Transformation leaves account name and authenticated UUID intact");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(LightningBolt.class, box).size() == before + 1,
                    "Enabled transformation effects create the visual lightning entity");
            record(player).settings.master = false; HerobrineServer.tick(player);
            helper.assertFalse(HerobrineServer.transformed(player.getUUID()), "Master OFF clears active transformation state");
            helper.assertTrue(player.getDisplayName().getString().equals(name), "Master OFF restores the player's original display name");
        } finally { cleanup(player); }
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "herobrineultimate", batch = "herobrine_server", timeoutTicks = 100)
    public static void worldTimeAndWeather(GameTestHelper helper) {
        ServerPlayer player = player(helper, true);
        enable(player, Ability.WORLD);
        HerobrineServer.handle(player, request("day"));
        helper.assertTrue(Math.floorMod(helper.getLevel().getDayTime(), 24000) == 1000, "Day action updates actual world time");
        helper.runAfterDelay(11, () -> {
            try {
                HerobrineServer.handle(player, request("rain"));
                helper.assertTrue(helper.getLevel().getLevelData().isRaining(), "Rain action updates actual world weather state");
            } catch (Throwable failure) { cleanup(player); throw failure; }
        });
        helper.runAfterDelay(22, () -> {
            try {
                HerobrineServer.handle(player, request("thunder"));
                helper.assertTrue(helper.getLevel().getLevelData().isRaining() && helper.getLevel().getLevelData().isThundering(),
                        "Thunder action enables both rain and thunder flags");
            } catch (Throwable failure) { cleanup(player); throw failure; }
        });
        helper.runAfterDelay(33, () -> {
            try {
                HerobrineServer.handle(player, request("clear"));
                helper.assertFalse(helper.getLevel().getLevelData().isRaining() || helper.getLevel().getLevelData().isThundering(),
                        "Clear action resets actual world weather flags");
            } catch (Throwable failure) { cleanup(player); throw failure; }
        });
        helper.runAfterDelay(44, () -> {
            try {
                HerobrineServer.handle(player, request("night"));
                helper.assertTrue(Math.floorMod(helper.getLevel().getDayTime(), 24000) == 13000, "Night action updates actual world time");
                helper.succeed();
            } finally { cleanup(player); }
        });
    }

}
