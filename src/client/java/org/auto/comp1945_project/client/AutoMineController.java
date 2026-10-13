package org.auto.comp1945_project.client;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.c2s.common.SyncedClientOptions;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ConnectedClientData;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.WorldEvents;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


public class AutoMineController extends ServerPlayerEntity {

    private static AutoMineController fakePlayer;
    private static int radius = 16;
    private static int tickCounter = 0;
    private static boolean enabled = true;
    private static BlockPos currentTarget = null;


    // ==================== 建構子 ====================

    public AutoMineController(MinecraftServer server, ServerWorld world, GameProfile profile, SyncedClientOptions clientOptions) {
        super(server, world, profile, clientOptions);
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }

    // ==================== 註冊 ====================

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.getWorld() instanceof ServerWorld world) {
                    MiningRecordManager.tickAnsKey(world, player.getX(), player.getY(), player.getZ());
                }
            }
            MiningRecordManager.tickFlush();

            // 2. 假玩家邏輯
            if (!enabled) return;
            AutoMineController fake = getFakePlayer();
            if (fake == null) return;

            ServerWorld world = fake.getServerWorld();

            if (currentTarget == null || world.getBlockState(currentTarget).isAir()) {
                currentTarget = OreFinder.findNearestOre(world, fake.getPos(), radius);
            }

            if (currentTarget != null) {
                double dist = fake.getPos().distanceTo(Vec3d.ofCenter(currentTarget));
                if (dist > 4.0) {
                    fake.moveTowards(currentTarget);
                } else {
                    fake.breakBlockAt(currentTarget);
                    currentTarget = null;
                }
            }

            // 🔥 更新假玩家的座標 / biome / tick
            BlockPos pos = fake.getBlockPos();
            String coordStr = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            String biomeStr = world.getBiome(pos).getKey()
                    .map(key -> key.getValue().toString())
                    .orElse("minecraft:plains");
            long tick = world.getTime();
            BiomeGuessHUD.updateStatus(coordStr, biomeStr, tick);

            // 🔥 每 100 tick 訓練一次
            if (server.getTicks() % 100 == 0) {
                HMMBiomeTrainer.trainAsync();
            }

            // 🔥 更新預測
            Map<String, Double> probs = HMMBiomeTrainer.predict(fake.getUuid());
            BiomeGuessHUD.updatePredictions(probs);
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                    CommandManager.literal("fakemine")
                            .requires(source -> source.hasPermissionLevel(2))
                            .then(CommandManager.literal("spawn")
                                    .executes(context -> {
                                        ServerPlayerEntity sourcePlayer = context.getSource().getPlayer();
                                        if (sourcePlayer == null) {
                                            context.getSource().sendError(Text.literal("必須由玩家執行"));
                                            return 0;
                                        }
                                        ServerWorld world = sourcePlayer.getServerWorld();
                                        BlockPos pos = sourcePlayer.getBlockPos();
                                        AutoMineController.spawn(world, sourcePlayer.getUuid(), pos);
                                        context.getSource().sendFeedback(
                                                () -> Text.literal("已生成假玩家於 " + pos.toShortString() + " 並自動開始尋找礦石"),
                                                false
                                        );
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("remove")
                                    .executes(context -> {
                                        if (fakePlayer != null) {
                                            fakePlayer.discard();
                                            fakePlayer = null;
                                            context.getSource().sendFeedback(
                                                    () -> Text.literal("已移除假玩家並停止自動挖礦"),
                                                    false
                                            );
                                        } else {
                                            context.getSource().sendError(Text.literal("沒有假玩家"));
                                        }
                                        return 1;
                                    })
                            )
                            .then(CommandManager.literal("radius")
                                    .then(CommandManager.argument("value", IntegerArgumentType.integer(1, 64))
                                            .executes(context -> {
                                                radius = IntegerArgumentType.getInteger(context, "value");
                                                context.getSource().sendFeedback(
                                                        () -> Text.literal("尋礦半徑已設為: " + radius),
                                                        false
                                                );
                                                return 1;
                                            })
                                    )
                            ).then(CommandManager.literal("train")
                                    .executes(context -> {
                                        BiomePredictor.Model model = BiomeTrainer.train();   // ← 這裡呼叫
                                        if (model == null) {
                                            context.getSource().sendError(Text.literal("訓練失敗，資料不足"));
                                            return 0;
                                        }
                                        BiomePredictor.load();   // ← 這裡呼叫
                                        context.getSource().sendFeedback(
                                                () -> Text.literal("模型訓練完成"),
                                                false
                                        );
                                        return 1;
                                    })
                            ).then(CommandManager.literal("predict")
                                    .executes(context -> {
                                        if (!BiomePredictor.isLoaded()) {   // ← 這裡呼叫
                                            context.getSource().sendError(Text.literal("模型尚未載入，請先 /fakemine train"));
                                            return 0;
                                        }

                                        Map<String, Integer> oreCounts = SessionStats.get(context.getSource().getPlayer().getUuid());
                                        Map<String, Double> probs = BiomePredictor.predict(oreCounts);   // ← 這裡呼叫

                                        for (Map.Entry<String, Double> e : probs.entrySet()) {
                                            String line = e.getKey() + ": " + String.format("%.2f%%", e.getValue() * 100);
                                            context.getSource().sendFeedback(() -> Text.literal(line), false);
                                        }
                                        return 1;
                                    })
                            )
            );
        });
    }

    // ==================== 生成 ====================

    public static AutoMineController spawn(ServerWorld world, UUID ownerUuid, BlockPos pos) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            System.err.println("[AutoMine] ❌ Server 為空");
            return null;
        }

        UUID cloneUuid = UUID.nameUUIDFromBytes(("Z_CLONE_ENTITY:" + ownerUuid).getBytes());

        Optional<GameProfile> existingProfile = server.getUserCache().getByUuid(ownerUuid);
        String name = existingProfile.map(GameProfile::getName).orElse("Miner");
        GameProfile profile = new GameProfile(cloneUuid, "§z_" + name);
        existingProfile.ifPresent(p -> profile.getProperties().putAll(p.getProperties()));

        AutoMineController npc = new AutoMineController(
                server, world, profile, SyncedClientOptions.createDefault()
        );
        npc.refreshPositionAndAngles(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5,
                0.0F,
                0.0F
        );

        npc.setInvulnerable(true);
        npc.setInvisible(false);
        npc.setSilent(true);

        // 🛑 關閉重力，避免假玩家受重力影響往下掉
        npc.setNoGravity(true);

        npc.getDataTracker().set(
                net.minecraft.entity.player.PlayerEntity.PLAYER_MODEL_PARTS,
                (byte) 0x7F
        );

        ClientConnection fakeConn = new ClientConnection(NetworkSide.SERVERBOUND);
        npc.networkHandler = new ServerPlayNetworkHandler(
                server,
                fakeConn,
                npc,
                new ConnectedClientData(profile, 0, npc.getClientOptions(), false)
        );

        server.getPlayerManager().getPlayerList().add(npc);
        server.getPlayerManager().sendToAll(
                PlayerListS2CPacket.entryFromPlayer(List.of(npc))
        );

        world.spawnEntity(npc);
        npc.interactionManager.changeGameMode(GameMode.SURVIVAL);

        fakePlayer = npc;
        System.out.println("[AutoMine] ✅ NPC 已生成在 " + pos + " 並開始自動挖礦");
        return npc;
    }

    // ==================== 直線移動與轉頭控制 ====================

    public void moveTowards(BlockPos pos) {
        Vec3d target = new Vec3d(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Vec3d current = this.getPos();
        Vec3d direction = target.subtract(current).normalize().multiply(0.4); // 0.4 為移動步長

        // 瞬間轉頭面向目標
        this.lookAtOre(pos);

        // 更新位置（直接朝目標前進）
        this.setPosition(current.x + direction.x, current.y + direction.y, current.z + direction.z);
        this.refreshPositionAfterTeleport(this.getX(), this.getY(), this.getZ());
    }

    public void lookAtOre(BlockPos pos) {
        double dx = pos.getX() + 0.5 - this.getX();
        double dy = pos.getY() + 0.5 - this.getEyeY();
        double dz = pos.getZ() + 0.5 - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) (Math.atan2(dz, dx) * (180 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Math.atan2(dy, distance) * (180 / Math.PI)));

        this.setYaw(yaw);
        this.setPitch(pitch);
        this.headYaw = yaw;
    }

    // ==================== 挖礦 ====================

    public void breakBlockAt(BlockPos pos) {
        ServerWorld world = this.getServerWorld();
        BlockState state = world.getBlockState(pos);
        if (state.isAir()) return;

        String oreId = Registries.BLOCK.getId(state.getBlock()).toString();
        System.out.println("[AutoMine] 挖到方塊: " + oreId);

        String biomeId = world.getBiome(pos).getKey()
                .map(key -> key.getValue().toString())
                .orElse("minecraft:plains");

        BiomeTrainerLive.onBlockBreak(oreId, biomeId);

        HMMBiomeTrainer.onBlockBreak(this.getUuid(), oreId);


        MiningRecordManager.recordBlockBreak(world, pos, state);
        Identifier blockId = Registries.BLOCK.getId(state.getBlock());
        BiomeGuessHUD.updateLastBrokenBlock(blockId.toString());
        this.getAbilities().allowModifyWorld = true;

        SoundEvent breakSound = state.getSoundGroup().getBreakSound();
        world.playSound(null, pos, breakSound, SoundCategory.BLOCKS, 1.0f, 1.0f);

        world.setBlockState(
                pos,
                Blocks.AIR.getDefaultState(),
                net.minecraft.block.Block.NOTIFY_ALL | net.minecraft.block.Block.FORCE_STATE
        );

        world.getChunkManager().markForUpdate(pos);

        BlockUpdateS2CPacket packet = new BlockUpdateS2CPacket(world, pos);
        for (ServerPlayerEntity viewer : world.getServer().getPlayerManager().getPlayerList()) {
            viewer.networkHandler.sendPacket(packet);
        }

        world.getServer().execute(() -> {
            world.getChunkManager().markForUpdate(pos);
            BlockUpdateS2CPacket packet2 = new BlockUpdateS2CPacket(world, pos);
            for (ServerPlayerEntity viewer : world.getServer().getPlayerManager().getPlayerList()) {
                viewer.networkHandler.sendPacket(packet2);
            }
        });

        world.syncWorldEvent(
                WorldEvents.BLOCK_BROKEN,
                pos,
                net.minecraft.block.Block.getRawIdFromState(state)
        );

        System.out.println("[AutoMine] 🔨 破壞: " + pos);
    }

    // ==================== 移除 ====================

    @Override
    public void remove(RemovalReason reason) {
        if (this.getServer() != null) {
            this.getServer().getPlayerManager().getPlayerList().remove(this);
        }
        if (fakePlayer == this) {
            fakePlayer = null;
            currentTarget = null;   // 🔥 加這行
        }
        super.remove(reason);
    }

    // ==================== Getter ====================

    public static AutoMineController getFakePlayer() {
        return fakePlayer;
    }

    public static int getRadius() {
        return radius;
    }
}