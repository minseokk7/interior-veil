package dev.minse.interiorveil.network;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.UUIDUtil;

public record VeilConfigPayload(UUID barrierId, CompoundTag data) implements CustomPacketPayload {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("interiorveil", "veil_config");
    public static final Type<VeilConfigPayload> TYPE = new Type<>(ID);

    public static final StreamCodec<RegistryFriendlyByteBuf, VeilConfigPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, VeilConfigPayload::barrierId,
                    ByteBufCodecs.COMPOUND_TAG, VeilConfigPayload::data,
                    VeilConfigPayload::new
            );

    public VeilConfigPayload(
            UUID barrierId,
            String name,
            int radius,
            int height,
            int fogMargin,
            int fogDistance,
            int fogFadeTicks,
            int navigationRange,
            boolean boundaryVisible,
            int boundaryColor,
            int navigationColor,
            boolean securityMode,
            int beaconColor,
            int accessStart,
            int accessEnd,
            int boundaryDensity,
            float boundarySize,
            int navigationDensity,
            float navigationSize,
            boolean requireBeaconPower,
            boolean forceOpen,
            boolean disableFog,
            int fogColor,
            boolean attackMode,
            boolean perimeterDefense,
            int attackTargetX,
            int attackTargetY,
            int attackTargetZ,
            int strikeRadius,
            boolean absoluteBarrier,
            boolean reflectProjectiles,
            String allowedPlayers
    ) {
        this(barrierId, createTag(
                name,
                radius,
                height,
                fogMargin,
                fogDistance,
                fogFadeTicks,
                navigationRange,
                boundaryVisible,
                boundaryColor,
                navigationColor,
                securityMode,
                beaconColor,
                accessStart,
                accessEnd,
                boundaryDensity,
                boundarySize,
                navigationDensity,
                navigationSize,
                requireBeaconPower,
                forceOpen,
                disableFog,
                fogColor,
                attackMode,
                perimeterDefense,
                attackTargetX,
                attackTargetY,
                attackTargetZ,
                strikeRadius,
                absoluteBarrier,
                reflectProjectiles,
                allowedPlayers
        ));
    }

    private static CompoundTag createTag(
            String name,
            int radius,
            int height,
            int fogMargin,
            int fogDistance,
            int fogFadeTicks,
            int navigationRange,
            boolean boundaryVisible,
            int boundaryColor,
            int navigationColor,
            boolean securityMode,
            int beaconColor,
            int accessStart,
            int accessEnd,
            int boundaryDensity,
            float boundarySize,
            int navigationDensity,
            float navigationSize,
            boolean requireBeaconPower,
            boolean forceOpen,
            boolean disableFog,
            int fogColor,
            boolean attackMode,
            boolean perimeterDefense,
            int attackTargetX,
            int attackTargetY,
            int attackTargetZ,
            int strikeRadius,
            boolean absoluteBarrier,
            boolean reflectProjectiles,
            String allowedPlayers
    ) {
        CompoundTag tag = new CompoundTag();
        if (name != null) tag.putString("name", name);
        tag.putInt("radius", radius);
        tag.putInt("height", height);
        tag.putInt("fogMargin", fogMargin);
        tag.putInt("fogDistance", fogDistance);
        tag.putInt("fogFadeTicks", fogFadeTicks);
        tag.putInt("navigationRange", navigationRange);
        tag.putBoolean("boundaryVisible", boundaryVisible);
        tag.putInt("boundaryColor", boundaryColor);
        tag.putInt("navigationColor", navigationColor);
        tag.putBoolean("securityMode", securityMode);
        tag.putInt("beaconColor", beaconColor);
        tag.putInt("accessStart", accessStart);
        tag.putInt("accessEnd", accessEnd);
        tag.putInt("boundaryDensity", boundaryDensity);
        tag.putFloat("boundarySize", boundarySize);
        tag.putInt("navigationDensity", navigationDensity);
        tag.putFloat("navigationSize", navigationSize);
        tag.putBoolean("requireBeaconPower", requireBeaconPower);
        tag.putBoolean("forceOpen", forceOpen);
        tag.putBoolean("disableFog", disableFog);
        tag.putInt("fogColor", fogColor);
        tag.putBoolean("attackMode", attackMode);
        tag.putBoolean("perimeterDefense", perimeterDefense);
        tag.putInt("attackTargetX", attackTargetX);
        tag.putInt("attackTargetY", attackTargetY);
        tag.putInt("attackTargetZ", attackTargetZ);
        tag.putInt("strikeRadius", strikeRadius);
        tag.putBoolean("absoluteBarrier", absoluteBarrier);
        tag.putBoolean("reflectProjectiles", reflectProjectiles);
        if (allowedPlayers != null) tag.putString("allowedPlayers", allowedPlayers);
        return tag;
    }

    public String name() { return data.getString("name").orElse(""); }
    public int radius() { return data.getInt("radius").orElse(0); }
    public int height() { return data.getInt("height").orElse(0); }
    public int fogMargin() { return data.getInt("fogMargin").orElse(0); }
    public int fogDistance() { return data.getInt("fogDistance").orElse(0); }
    public int fogFadeTicks() { return data.getInt("fogFadeTicks").orElse(0); }
    public int navigationRange() { return data.getInt("navigationRange").orElse(0); }
    public boolean boundaryVisible() { return data.getBoolean("boundaryVisible").orElse(false); }
    public int boundaryColor() { return data.getInt("boundaryColor").orElse(0); }
    public int navigationColor() { return data.getInt("navigationColor").orElse(0); }
    public boolean securityMode() { return data.getBoolean("securityMode").orElse(false); }
    public int beaconColor() { return data.getInt("beaconColor").orElse(0); }
    public int accessStart() { return data.getInt("accessStart").orElse(0); }
    public int accessEnd() { return data.getInt("accessEnd").orElse(0); }
    public int boundaryDensity() { return data.getInt("boundaryDensity").orElse(0); }
    public float boundarySize() { return data.getFloat("boundarySize").orElse(0f); }
    public int navigationDensity() { return data.getInt("navigationDensity").orElse(0); }
    public float navigationSize() { return data.getFloat("navigationSize").orElse(0f); }
    public boolean requireBeaconPower() { return data.getBoolean("requireBeaconPower").orElse(false); }
    public boolean forceOpen() { return data.getBoolean("forceOpen").orElse(false); }
    public boolean disableFog() { return data.getBoolean("disableFog").orElse(false); }
    public int fogColor() { return data.getInt("fogColor").orElse(0); }
    public boolean attackMode() { return data.getBoolean("attackMode").orElse(false); }
    public boolean perimeterDefense() { return data.getBoolean("perimeterDefense").orElse(false); }
    public int attackTargetX() { return data.getInt("attackTargetX").orElse(0); }
    public int attackTargetY() { return data.getInt("attackTargetY").orElse(0); }
    public int attackTargetZ() { return data.getInt("attackTargetZ").orElse(0); }
    public int strikeRadius() { return data.getInt("strikeRadius").orElse(0); }
    public boolean absoluteBarrier() { return data.getBoolean("absoluteBarrier").orElse(false); }
    public boolean reflectProjectiles() { return data.getBoolean("reflectProjectiles").orElse(false); }
    public String allowedPlayers() { return data.getString("allowedPlayers").orElse(""); }

    @Override
    public Type<VeilConfigPayload> type() {
        return TYPE;
    }
}
