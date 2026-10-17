package com.yutadigital.herobrine.server;

import com.yutadigital.herobrine.core.Ability;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

/** Real Minecraft NBT/SavedData checks. Does not boot a game or substitute Minecraft stubs. */
public final class HerobrineWorldDataTest {
    private static int assertions;
    private static final UUID ALICE = UUID.fromString("c6ebc731-0e31-4f58-b7a5-f7667995ca55");
    private static final UUID BOB = UUID.fromString("d87b6901-dbe0-4586-ae05-f92656c0a998");

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static CompoundTag mob(UUID uuid, String dimension) {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("uuid", uuid);
        tag.putString("dimension", dimension);
        return tag;
    }
    private static CompoundTag playerEntry(CompoundTag root, UUID uuid) {
        ListTag players = root.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) if (players.getCompound(i).getUUID("uuid").equals(uuid)) return players.getCompound(i);
        throw new AssertionError("Missing saved player " + uuid);
    }

    public static void main(String[] args) throws Exception {
        HerobrineWorldData data = new HerobrineWorldData();
        check(!data.isDirty(), "A new untouched SavedData is clean");
        HerobrineWorldData.Record alice = data.player(ALICE);
        check(data.isDirty(), "Creating a player marks the actual SavedData dirty");
        check(data.player(ALICE) == alice, "Repeated UUID lookups return the same record");
        HerobrineWorldData.Record bob = data.player(BOB);
        check(alice != bob && alice.settings != bob.settings, "Players own independent records and settings");
        alice.settings.master = false;
        alice.settings.set(Ability.HEROBRINE, true);
        alice.settings.set(Ability.FLIGHT, true);
        alice.settings.set(Ability.HUNGER, true);
        alice.settings.multiplier("move", 3.5);
        alice.settings.multiplier("mine", 25);
        alice.settings.multiplier("attack", 4);
        alice.settings.multiplier("damage", 750);
        alice.waypoint = new CompoundTag();
        alice.waypoint.putString("dimension", "minecraft:the_nether");
        alice.waypoint.putDouble("x", 12.5);
        alice.waypoint.putDouble("y", 64);
        alice.waypoint.putDouble("z", -19.25);
        alice.waypoint.putFloat("yaw", 123.5F);
        alice.waypoint.putFloat("pitch", -23.25F);
        UUID aliceMob = new UUID(1, 1);
        UUID bobMob = new UUID(2, 1);
        alice.mobs.put(aliceMob, "minecraft:the_nether");
        bob.mobs.put(bobMob, "minecraft:overworld");
        alice.flightOwned = true;
        alice.priorMayFly = true;
        alice.priorFlying = false;
        alice.invisibilityOwned = true;
        alice.priorInvisible = false;
        bob.priorInvisible = true;
        bob.settings.set(Ability.GOD, true);

        CompoundTag root = data.save(new CompoundTag());
        check(root.getInt("version") == 1, "Save writes a schema version");
        check(root.getList("players", Tag.TAG_COMPOUND).size() == 2, "Both UUIDs are saved");
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        NbtIo.writeCompressed(root, stream);
        byte[] bytes = stream.toByteArray();
        check(bytes.length > 0 && (bytes[0] & 255) == 0x1f && (bytes[1] & 255) == 0x8b, "Actual NbtIo writes compressed gzip NBT");
        CompoundTag disk = NbtIo.readCompressed(new ByteArrayInputStream(bytes));
        HerobrineWorldData loaded = HerobrineWorldData.load(disk);
        HerobrineWorldData.Record a = loaded.player(ALICE);
        HerobrineWorldData.Record b = loaded.player(BOB);
        check(loaded.players.size() == 2, "Compressed save/load keeps both UUIDs");
        check(!a.settings.master && !a.settings.enabled(Ability.FLIGHT), "F6 suspension remains effective after disk reload");
        for (Ability ability : Ability.values()) check(a.settings.selected(ability) == alice.settings.selected(ability), "Selected ability survives disk reload: " + ability);
        for (String key : new String[]{"move", "mine", "attack", "damage"}) check(a.settings.multiplier(key) == alice.settings.multiplier(key), "Multiplier survives disk reload: " + key);
        a.settings.master = true;
        check(a.settings.enabled(Ability.HEROBRINE) && a.settings.enabled(Ability.FLIGHT) && a.settings.enabled(Ability.HUNGER), "F6 resumes selected powers after reload");
        check(b.settings.enabled(Ability.GOD) && !b.settings.selected(Ability.FLIGHT), "Another UUID retains its own ability selection");
        check(a.waypoint.getString("dimension").equals("minecraft:the_nether"), "Waypoint retains dimension");
        check(a.waypoint.getDouble("x") == 12.5 && a.waypoint.getDouble("y") == 64 && a.waypoint.getDouble("z") == -19.25, "Waypoint retains exact coordinates");
        check(a.waypoint.getFloat("yaw") == 123.5F && a.waypoint.getFloat("pitch") == -23.25F, "Waypoint retains view rotation");
        check(b.waypoint == null, "Waypoint does not leak to another UUID");
        check(a.mobs.size() == 1 && a.mobs.get(aliceMob).equals("minecraft:the_nether"), "Mob ownership and dimension survive reload");
        check(b.mobs.containsKey(bobMob) && !b.mobs.containsKey(aliceMob), "Mob ownership remains isolated per UUID");
        check(a.flightOwned && a.priorMayFly && !a.priorFlying, "Crash recovery retains flight ownership and both original flags");
        check(a.invisibilityOwned && !a.priorInvisible, "Crash recovery retains invisibility ownership and original flag");
        check(!b.flightOwned && !b.invisibilityOwned && b.priorInvisible, "Inactive recovery metadata remains isolated per UUID");
        a.waypoint.putDouble("x", 999);
        check(playerEntry(disk, ALICE).getCompound("waypoint").getDouble("x") == 12.5, "Loading copies the waypoint instead of aliasing serialized NBT");
        check(alice.waypoint.getDouble("x") == 12.5, "Loaded waypoint mutation does not affect pre-save data");

        CompoundTag corrupt = new CompoundTag();
        ListTag corruptPlayers = new ListTag();
        CompoundTag noUuid = new CompoundTag();
        noUuid.putString("uuid", "malformed UUID");
        corruptPlayers.add(noUuid);
        CompoundTag wrongLengthUuid = new CompoundTag();
        wrongLengthUuid.putIntArray("uuid", new int[]{1, 2});
        corruptPlayers.add(wrongLengthUuid);
        CompoundTag recoverable = new CompoundTag();
        recoverable.putUUID("uuid", ALICE);
        CompoundTag badSettings = new CompoundTag();
        badSettings.putDouble("move", Double.NaN);
        badSettings.putDouble("mine", Double.POSITIVE_INFINITY);
        badSettings.putString("attack", "broken type");
        badSettings.putDouble("damage", -100);
        badSettings.putBoolean("FLIGHT", true);
        recoverable.put("settings", badSettings);
        ListTag mobs = new ListTag();
        mobs.add(new CompoundTag());
        mobs.add(mob(new UUID(3, 1), ""));
        mobs.add(mob(new UUID(3, 2), "INVALID:UPPERCASE"));
        mobs.add(mob(new UUID(3, 3), "minecraft:" + "x".repeat(257)));
        for (int i = 0; i < 20; i++) mobs.add(mob(new UUID(4, i), "minecraft:overworld"));
        recoverable.put("mobs", mobs);
        corruptPlayers.add(recoverable);
        corrupt.put("players", corruptPlayers);
        HerobrineWorldData repaired = HerobrineWorldData.load(corrupt);
        check(repaired.players.size() == 1, "Malformed and wrong-length player UUIDs are skipped");
        HerobrineWorldData.Record repairedAlice = repaired.player(ALICE);
        check(repairedAlice.settings.master && repairedAlice.settings.enabled(Ability.FLIGHT), "Legacy save without master flag keeps default and valid ability");
        for (String key : new String[]{"move", "mine", "attack", "damage"}) check(repairedAlice.settings.multiplier(key) == 1, "Nonfinite, mistyped and negative saved values recover safely: " + key);
        check(repairedAlice.mobs.size() == 16, "Loader retains at most 16 valid mobs without malformed entries crowding them out");
        for (var entry : repairedAlice.mobs.entrySet()) {
            check(entry.getKey().getMostSignificantBits() == 4 && entry.getValue().equals("minecraft:overworld"), "Malformed mob UUID/dimension entries are not retained");
        }
        check(!repairedAlice.flightOwned && !repairedAlice.invisibilityOwned, "Missing old recovery data never invents owned overrides");
        CompoundTag oversized = new CompoundTag();
        oversized.putDouble("move", 99999);
        oversized.putDouble("mine", 99999);
        oversized.putDouble("attack", 99999);
        oversized.putDouble("damage", 99999);
        var clamped = HerobrineWorldData.readSettings(oversized);
        check(clamped.multiplier("move") == 10 && clamped.multiplier("mine") == 100 && clamped.multiplier("attack") == 10 && clamped.multiplier("damage") == 1000, "Disk values cannot bypass server multiplier caps");
        CompoundTag wrongTypeRoot = new CompoundTag();
        wrongTypeRoot.putString("players", "broken list type");
        check(HerobrineWorldData.load(wrongTypeRoot).players.isEmpty(), "Wrong root list type loads a safe empty world record");
        System.out.println("PASS: " + assertions + " real Minecraft NBT persistence assertions (gzip roundtrip, F6, UUID isolation, waypoint, mobs, corrupt data, recovery metadata)");
    }
}
