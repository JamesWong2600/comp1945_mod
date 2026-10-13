package org.auto.comp1945_project.client;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public class AutoMiner {

    public static void mineBlock(ServerPlayerEntity player, BlockPos pos) {
        ServerWorld world = player.getServerWorld();
        // 讓玩家面向該方塊
        player.lookAt(
                net.minecraft.command.argument.EntityAnchorArgumentType.EntityAnchor.EYES,
                net.minecraft.util.math.Vec3d.ofCenter(pos)
        );
        // 直接破壞
        world.breakBlock(pos, true, player);
    }
}