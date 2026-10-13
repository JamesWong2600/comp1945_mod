package org.auto.comp1945_project.mixins;

import net.minecraft.block.BlockState;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunkGenerator.class)
public class LavaRemover {

    // 使用 @Inject 攔截呼叫點，而非 @Overwrite 替換整個方法
    @Inject(
            method = "createFluidLevelSampler",
            at = @At("RETURN"),
            cancellable = true
    )
    private static void overrideFluidLevel(ChunkGeneratorSettings settings, CallbackInfoReturnable<AquiferSampler.FluidLevelSampler> cir) {
        // 用 Lambda 取代顯式建構 FluidLevel，相容性更高
        cir.setReturnValue((x, y, z) -> {
            // 1.21.4 和 1.21.5 都支援 getSeaLevel()，但寫法需視 Mapping 而定
            int seaLevel = settings.seaLevel();
            BlockState defaultFluid = settings.defaultFluid();
            return new AquiferSampler.FluidLevel(seaLevel, defaultFluid);
        });
    }
}

//@Mixin({NoiseChunkGenerator.class})
//public class LavaRemover {
//
//    @Overwrite
//    private static AquiferSampler.FluidLevelSampler createFluidLevelSampler(ChunkGeneratorSettings settings) {
//        return (x, y, z) -> new AquiferSampler.FluidLevel(settings.seaLevel(), settings.defaultFluid());
//    }
//}