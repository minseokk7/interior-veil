package dev.minse.interiorveil;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import dev.minse.interiorveil.network.VeilConfigPayload;
import dev.minse.interiorveil.network.VeilAdminActionPayload;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.network.chat.Component;

public final class InteriorVeil implements ModInitializer {
    public static final String MOD_ID = "interiorveil";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final ResourceKey<Level> POCKET_LEVEL = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            id("pocket")
    );

    public static VeilManager manager;

    @Override
    public void onInitialize() {
        VeilItems.initialize();
        VeilNetworking.registerPayloads();

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            manager = new VeilManager(server);
            VeilSecurity.register(manager);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (manager != null) {
                manager.save();
            }
            manager = null;
        });
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (manager != null) {
                manager.tick();
            }
        });
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (manager != null) {
                manager.onPlayerJoin(handler.getPlayer());
            }
        });
        net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(net.minecraft.commands.Commands.literal("veil")
                    .then(net.minecraft.commands.Commands.literal("recover")
                            .executes(context -> {
                                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                    return 0;
                                }
                                if (manager != null) {
                                    java.util.List<VeilBarrier> owned = manager.barriers().stream()
                                            .filter(b -> b.owner().equals(player.getUUID()) || player.hasPermissions(2))
                                            .toList();
                                    if (!owned.isEmpty()) {
                                        for (VeilBarrier barrier : owned) {
                                            net.minecraft.world.item.ItemStack key = new net.minecraft.world.item.ItemStack(VeilItems.VEIL_KEY);
                                            VeilKeyBinding.bind(key, barrier);
                                            if (!player.getInventory().add(key)) {
                                                player.drop(key, false);
                                            }
                                        }
                                        player.sendSystemMessage(Component.literal("§a보유하신 결계들의 키가 인벤토리(또는 바닥)에 복구되었습니다."));
                                        return 1;
                                    }
                                }
                                player.sendSystemMessage(Component.literal("§c소유하고 있는 결계가 없습니다."));
                                return 0;
                            }))
                    .then(net.minecraft.commands.Commands.literal("strike")
                            .then(net.minecraft.commands.Commands.literal("emp")
                                    .then(net.minecraft.commands.Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(net.minecraft.commands.Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                    .executes(context -> {
                                                        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                                                        int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                                                        int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");
                                                        if (manager != null) {
                                                            net.minecraft.server.level.ServerLevel sLevel = (net.minecraft.server.level.ServerLevel) player.level();
                                                            int y = sLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                                                            manager.fireStrikeFromCommand(player, x, y, z, 1);
                                                            return 1;
                                                        }
                                                        return 0;
                                                    }))))
                            .then(net.minecraft.commands.Commands.literal("supply")
                                    .then(net.minecraft.commands.Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .then(net.minecraft.commands.Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                    .executes(context -> {
                                                        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) return 0;
                                                        int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                                                        int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");
                                                        if (manager != null) {
                                                            net.minecraft.server.level.ServerLevel sLevel = (net.minecraft.server.level.ServerLevel) player.level();
                                                            int y = sLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                                                            manager.fireStrikeFromCommand(player, x, y, z, 2);
                                                            return 1;
                                                        }
                                                        return 0;
                                                    }))))
                            .then(net.minecraft.commands.Commands.argument("x", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                    .then(net.minecraft.commands.Commands.argument("z", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                            .executes(context -> {
                                                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                                    return 0;
                                                }
                                                int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                                                int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");
                                                if (manager != null) {
                                                    net.minecraft.server.level.ServerLevel sLevel = (net.minecraft.server.level.ServerLevel) player.level();
                                                    int y = sLevel.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);
                                                    manager.fireStrikeFromCommand(player, x, y, z, 0);
                                                    return 1;
                                                }
                                                return 0;
                                            })
                                            .then(net.minecraft.commands.Commands.argument("y", com.mojang.brigadier.arguments.IntegerArgumentType.integer())
                                                    .executes(context -> {
                                                        if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                                            return 0;
                                                        }
                                                        int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                                                        int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "y");
                                                        int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");
                                                        if (manager != null) {
                                                            manager.fireStrikeFromCommand(player, x, y, z, 0);
                                                            return 1;
                                                        }
                                                        return 0;
                                                    })
                                                    .then(net.minecraft.commands.Commands.argument("type", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 2))
                                                            .executes(context -> {
                                                                if (!(context.getSource().getEntity() instanceof ServerPlayer player)) {
                                                                    return 0;
                                                                }
                                                                int x = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "x");
                                                                int y = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "y");
                                                                int z = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "z");
                                                                int type = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(context, "type");
                                                                if (manager != null) {
                                                                    manager.fireStrikeFromCommand(player, x, y, z, type);
                                                                    return 1;
                                                                }
                                                                return 0;
                                                            })
                                                    )
                                            )
                                    )
                            )
                    )
            );
        });
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || manager == null) {
                return InteractionResult.PASS;
            }
            return manager.useBeacon(serverPlayer, hand, hit);
        });
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, level, hand) -> {
            net.minecraft.world.item.ItemStack stack = player.getItemInHand(hand);
            if (stack.is(VeilItems.VEIL_KEY) && (player.isShiftKeyDown() || player.isCrouching())) {
                if (VeilKeyBinding.barrierId(stack).isPresent()) {
                    if (level.isClientSide()) {
                        return net.minecraft.world.InteractionResult.SUCCESS;
                    }
                    if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer && manager != null) {
                        java.util.UUID barrierId = VeilKeyBinding.barrierId(stack).get();
                        VeilBarrier barrier = manager.barriers().stream()
                                .filter(b -> b.id().equals(barrierId))
                                .findFirst()
                                .orElse(null);
                        if (barrier != null) {
                            manager.openConfig(serverPlayer, barrier, true);
                        } else {
                            serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.interiorveil.barrier_not_found"), true);
                        }
                    }
                    return net.minecraft.world.InteractionResult.SUCCESS;
                }
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (world.isClientSide() || !(player instanceof ServerPlayer serverPlayer) || manager == null) {
                return true;
            }
            if (state.getBlock() instanceof net.minecraft.world.level.block.BeaconBlock) {
                VeilBarrier barrier = manager.barriers().stream()
                        .filter(b -> {
                            if (world.dimension().equals(POCKET_LEVEL)) {
                                // 포켓 차원 신호기: 포켓 좌표로 매칭
                                return b.getPocketX() == pos.getX() && b.centerY() == pos.getY() && b.getPocketZ() == pos.getZ();
                            } else if (world.dimension().equals(b.sourceKey())) {
                                // 오버월드: 신호기가 있거나(이미 이동됐더라도) barrier center 좌표와 일치하면 처리
                                return b.centerX() == pos.getX() && b.centerY() == pos.getY() && b.centerZ() == pos.getZ();
                            }
                            return false;
                        }).findFirst().orElse(null);

                if (barrier != null) {
                    // 절대방어막 신호기: 오너나 키 소지자, 관리자만 파괴 가능
                    if (barrier.advanced().absoluteBarrier() && world.dimension().equals(barrier.sourceKey())) {
                        boolean isOwner = serverPlayer.getUUID().equals(barrier.owner());
                        boolean isKeyHolder = manager.holdsKeyForBarrier(serverPlayer, barrier);
                        boolean isAdmin = serverPlayer.hasPermissions(2);
                        if (!isOwner && !isKeyHolder && !isAdmin) {
                            serverPlayer.displayClientMessage(
                                    net.minecraft.network.chat.Component.translatable("message.interiorveil.barrier_protected"),
                                    true
                            );
                            return false;
                        }
                    }

                    // 1. 신호기 아이템 지급
                    giveItemToPlayer(serverPlayer, new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.BEACON));

                    // 2. 포켓 차원의 신호기 & 피라미드 제거 및 아이템 수거
                    net.minecraft.server.level.ServerLevel pocket = world.getServer().getLevel(POCKET_LEVEL);
                    if (pocket != null) {
                        net.minecraft.core.BlockPos pocketBeaconPos = new net.minecraft.core.BlockPos(barrier.getPocketX(), barrier.centerY(), barrier.getPocketZ());
                        pocket.setBlock(pocketBeaconPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        // 포켓 피라미드 (신호기 아래 최대 4레이어)
                        for (int tier = 1; tier <= 4; tier++) {
                            int py = barrier.centerY() - tier;
                            for (int x = barrier.getPocketX() - tier; x <= barrier.getPocketX() + tier; x++) {
                                for (int z = barrier.getPocketZ() - tier; z <= barrier.getPocketZ() + tier; z++) {
                                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, py, z);
                                    net.minecraft.world.level.block.state.BlockState tState = pocket.getBlockState(p);
                                    if (isBeaconBase(tState)) {
                                        giveItemToPlayer(serverPlayer, new net.minecraft.world.item.ItemStack(tState.getBlock().asItem()));
                                        pocket.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                                    }
                                }
                            }
                        }
                    }

                    // 3. 오버월드 소스의 신호기 & 피라미드 제거 및 아이템 수거
                    net.minecraft.server.level.ServerLevel source = world.getServer().getLevel(barrier.sourceKey());
                    if (source != null) {
                        net.minecraft.core.BlockPos sourceBeaconPos = new net.minecraft.core.BlockPos(barrier.centerX(), barrier.centerY(), barrier.centerZ());
                        if (source.getBlockState(sourceBeaconPos).is(net.minecraft.world.level.block.Blocks.BEACON)) {
                            source.setBlock(sourceBeaconPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                        }
                        // 오버월드 피라미드 제거 (신호기 Y 바로 아래부터 최대 4레이어)
                        for (int tier = 1; tier <= 4; tier++) {
                            int py = barrier.centerY() - tier;
                            for (int x = barrier.centerX() - tier; x <= barrier.centerX() + tier; x++) {
                                for (int z = barrier.centerZ() - tier; z <= barrier.centerZ() + tier; z++) {
                                    net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, py, z);
                                    net.minecraft.world.level.block.state.BlockState pState = source.getBlockState(p);
                                    if (isBeaconBase(pState)) {
                                        giveItemToPlayer(serverPlayer, new net.minecraft.world.item.ItemStack(pState.getBlock().asItem()));
                                        source.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                                    }
                                }
                            }
                        }
                    }

                    // 4. 결계 데이터 삭제 및 저장
                    manager.removeBarrierAndSave(barrier);
                    world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    return false; // 이미 처리했으므로 바닐라 기본 파괴 중복 방지
                } else {
                    // 일반 신호기 파괴 시에도 피라미드 전체를 함께 수거
                    giveItemToPlayer(serverPlayer, new net.minecraft.world.item.ItemStack(net.minecraft.world.level.block.Blocks.BEACON));
                    for (int tier = 1; tier <= 4; tier++) {
                        int py = pos.getY() - tier;
                        for (int x = pos.getX() - tier; x <= pos.getX() + tier; x++) {
                            for (int z = pos.getZ() - tier; z <= pos.getZ() + tier; z++) {
                                net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(x, py, z);
                                net.minecraft.world.level.block.state.BlockState pState = world.getBlockState(p);
                                if (isBeaconBase(pState)) {
                                    giveItemToPlayer(serverPlayer, new net.minecraft.world.item.ItemStack(pState.getBlock().asItem()));
                                    world.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                                }
                            }
                        }
                    }
                    world.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                    return false;
                }
            }
            return true;
        });
        LOGGER.info("Interior Veil initialized");
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    static void applyConfigUpdate(ServerPlayer player, VeilConfigPayload payload) {
        if (manager != null) {
            manager.applyConfig(player, payload);
        }
    }

    static void applyAdminAction(ServerPlayer player, VeilAdminActionPayload payload) {
        if (manager != null) {
            manager.applyAdminAction(player, payload);
        }
    }

    public static void giveItemToPlayer(ServerPlayer player, net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty() || player.isCreative()) return;
        player.getInventory().add(stack);
        if (!stack.isEmpty()) {
            net.minecraft.server.level.ServerLevel sLevel = (net.minecraft.server.level.ServerLevel) player.level();
            net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
                    sLevel,
                    player.getX(),
                    player.getY() + 0.1,
                    player.getZ(),
                    stack.copy()
            );
            itemEntity.setDefaultPickUpDelay();
            sLevel.addFreshEntity(itemEntity);
            stack.setCount(0);
        }
    }

    private static boolean isBeaconBase(net.minecraft.world.level.block.state.BlockState state) {
        if (state.is(net.minecraft.tags.BlockTags.BEACON_BASE_BLOCKS)) {
            return true;
        }
        return state.is(net.minecraft.world.level.block.Blocks.IRON_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.GOLD_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.DIAMOND_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.EMERALD_BLOCK)
                || state.is(net.minecraft.world.level.block.Blocks.NETHERITE_BLOCK);
    }
}
