package dev.minse.interiorveil.network;

import java.util.UUID;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record VeilTargetMapPayload(
        UUID barrierId,
        int centerX,
        int centerZ,
        int cellSize,
        int resolution,
        int[] heights,
        int[] colors,
        int[] sculkPings // [x1, z1, x2, z2, ...] 스컬크 코어로 탐지된 적 좌표 쌍
) implements CustomPacketPayload {
    public VeilTargetMapPayload(UUID barrierId, int centerX, int centerZ, int cellSize, int resolution, int[] heights, int[] colors) {
        this(barrierId, centerX, centerZ, cellSize, resolution, heights, colors, new int[0]);
    }

    public static final Type<VeilTargetMapPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("interiorveil", "target_map")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, VeilTargetMapPayload> STREAM_CODEC =
            CustomPacketPayload.codec(VeilTargetMapPayload::write, VeilTargetMapPayload::read);

    private static VeilTargetMapPayload read(RegistryFriendlyByteBuf buffer) {
        UUID barrierId = buffer.readUUID();
        int centerX = buffer.readInt();
        int centerZ = buffer.readInt();
        int cellSize = buffer.readVarInt();
        int resolution = buffer.readVarInt();
        int pingCount = buffer.readVarInt();
        int[] pings = new int[pingCount];
        for (int i = 0; i < pingCount; i++) {
            pings[i] = buffer.readInt();
        }
        byte[] compressed = buffer.readByteArray();
        
        int expected = resolution * resolution;
        if (resolution < 1 || resolution > 256) {
            throw new IllegalArgumentException("Invalid veil target map dimensions");
        }
        
        int[][] decompressed = decompress(compressed, expected);
        return new VeilTargetMapPayload(barrierId, centerX, centerZ, cellSize, resolution, decompressed[0], decompressed[1], pings);
    }

    private void write(RegistryFriendlyByteBuf buffer) {
        buffer.writeUUID(barrierId);
        buffer.writeInt(centerX);
        buffer.writeInt(centerZ);
        buffer.writeVarInt(cellSize);
        buffer.writeVarInt(resolution);
        int[] pings = (sculkPings != null) ? sculkPings : new int[0];
        buffer.writeVarInt(pings.length);
        for (int p : pings) {
            buffer.writeInt(p);
        }
        buffer.writeByteArray(compress(heights, colors));
    }

    private static byte[] compress(int[] heights, int[] colors) {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate((heights.length + colors.length) * 4);
        for (int h : heights) bb.putInt(h);
        for (int c : colors) bb.putInt(c);
        
        java.util.zip.Deflater deflater = new java.util.zip.Deflater(java.util.zip.Deflater.BEST_SPEED);
        deflater.setInput(bb.array());
        deflater.finish();
        
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            baos.write(buffer, 0, count);
        }
        deflater.end();
        return baos.toByteArray();
    }

    private static int[][] decompress(byte[] data, int expectedLength) {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        inflater.setInput(data);
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                if (count == 0 && inflater.needsInput()) break;
                baos.write(buffer, 0, count);
            }
        } catch (java.util.zip.DataFormatException e) {
            throw new IllegalArgumentException("Failed to decompress veil target map", e);
        } finally {
            inflater.end();
        }
        
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(baos.toByteArray());
        int[] heights = new int[expectedLength];
        int[] colors = new int[expectedLength];
        for (int i = 0; i < expectedLength; i++) {
            heights[i] = bb.getInt();
        }
        for (int i = 0; i < expectedLength; i++) {
            colors[i] = bb.getInt();
        }
        return new int[][]{heights, colors};
    }

    @Override
    public Type<VeilTargetMapPayload> type() {
        return TYPE;
    }
}
