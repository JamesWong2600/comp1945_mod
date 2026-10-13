package org.auto.comp1945_project.client;


import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public class OreFinder {

    private static final Set<Block> ORES = new HashSet<>();

    static {
        // 原版礦石（可選）
        ORES.add(Blocks.COAL_BLOCK);
        //ORES.add(Blocks.DEEPSLATE_COAL_ORE);
        ORES.add(Blocks.IRON_BLOCK);
        //ORES.add(Blocks.DEEPSLATE_IRON_ORE);
        ORES.add(Blocks.GOLD_BLOCK);
       // ORES.add(Blocks.DEEPSLATE_GOLD_ORE);
        ORES.add(Blocks.DIAMOND_BLOCK);
       // ORES.add(Blocks.DEEPSLATE_DIAMOND_ORE);
        ORES.add(Blocks.EMERALD_BLOCK);
       // ORES.add(Blocks.DEEPSLATE_EMERALD_ORE);
        ORES.add(Blocks.REDSTONE_BLOCK);
       // ORES.add(Blocks.DEEPSLATE_REDSTONE_ORE);
//
//        // 你自訂的方塊
//        addBlock("project:coal_block");
//        addBlock("project:diamond_block");
//        addBlock("project:emerald_block");
//        addBlock("project:gold_block");
//        addBlock("project:iron_block");
//        addBlock("project:redstone_block");
    }

    private static void addBlock(String id) {
        Block block = Registries.BLOCK.get(Identifier.of(id));
        if (block != Blocks.AIR) {
            ORES.add(block);
        } else {
            System.out.println("[OreFinder] 找不到方塊: " + id);
        }
    }

    public static BlockPos findNearestOre(ServerWorld world, Vec3d center, int radius) {
        BlockPos centerPos = BlockPos.ofFloored(center);
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = centerPos.add(x, y, z);
                    Block block = world.getBlockState(pos).getBlock();
                    if (ORES.contains(block)) {
                        double dist = center.squaredDistanceTo(
                                pos.getX() + 0.5,
                                pos.getY() + 0.5,
                                pos.getZ() + 0.5
                        );
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = pos;
                        }
                    }
                }
            }
        }
        return nearest;
    }
}