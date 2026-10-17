package com.yutadigital.herobrine.fabric;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;

import java.util.Arrays;

/** Actual vanilla NBT/Netty framing checks; this class is never shipped. */
public final class FabricPacketDecodeTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void rejected(byte[] payload, String label) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(payload));
        try {
            check(HerobrineFabric.readPacket(buffer) == null, label);
            check(buffer.refCnt() == 1, label + " preserves caller ownership");
        } finally { buffer.release(); }
    }

    public static void main(String[] args) {
        CompoundTag request = new CompoundTag();
        request.putString("op", "toggle");
        request.putString("key", "FLIGHT");
        request.putBoolean("flag", true);
        FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
        byte[] bytes;
        try {
            encoded.writeNbt(request);
            bytes = new byte[encoded.readableBytes()];
            encoded.getBytes(encoded.readerIndex(), bytes);
            check(request.equals(HerobrineFabric.readPacket(encoded)), "A real compound request round trips");
            check(encoded.refCnt() == 1, "Valid decode preserves caller ownership");
        } finally { encoded.release(); }

        rejected(new byte[0], "An empty payload is rejected");
        rejected(new byte[]{0}, "A null NBT root is rejected");
        rejected(new byte[]{8, 0, 0, 0, 0}, "A string NBT root is rejected");
        rejected(new byte[HerobrineFabric.MAX_PACKET_BYTES + 1], "An oversized frame is rejected before NBT decode");
        rejected(Arrays.copyOf(bytes, bytes.length + 1), "Trailing bytes are rejected");
        for (int end = 1; end < bytes.length; end++) {
            rejected(Arrays.copyOf(bytes, end), "A truncated frame at byte " + end + " is rejected");
        }
        FriendlyByteBuf prefixed = new FriendlyByteBuf(Unpooled.buffer());
        try {
            prefixed.writeInt(0x12345678);
            prefixed.writeBytes(bytes);
            prefixed.readerIndex(4);
            check(request.equals(HerobrineFabric.readPacket(prefixed)), "Framing starts at the actual reader index");
        } finally { prefixed.release(); }
        for (int players : new int[]{283, 284, 4096}) {
            CompoundTag state = new CompoundTag();
            ListTag visuals = new ListTag();
            for (int i = 0; i < players; i++) {
                CompoundTag visual = new CompoundTag();
                visual.putUUID("id", new UUID(0, i));
                visuals.add(visual);
            }
            state.put("visuals", visuals);
            FriendlyByteBuf frame = new FriendlyByteBuf(Unpooled.buffer());
            try {
                frame.writeNbt(state);
                check(frame.readableBytes() <= HerobrineFabric.MAX_STATE_BYTES, "State fits bounded frame: " + players);
                check(state.equals(HerobrineFabric.readState(frame)), "Large state round trips: " + players);
                check(frame.refCnt() == 1, "State decoder preserves ownership");
                frame.clear();
                frame.writeNbt(state);
                if (frame.readableBytes() > HerobrineFabric.MAX_PACKET_BYTES) {
                    check(HerobrineFabric.readPacket(frame) == null, "Request limit is unchanged");
                }
            } finally { frame.release(); }
        }
        FriendlyByteBuf oversizedState = new FriendlyByteBuf(Unpooled.buffer());
        try {
            oversizedState.writeZero(HerobrineFabric.MAX_STATE_BYTES + 1);
            check(HerobrineFabric.readState(oversizedState) == null, "Oversized state is rejected");
        } finally { oversizedState.release(); }
        System.out.println("Fabric packet framing passed: " + assertions + " assertions (actual vanilla NBT + Netty)");
    }
}
