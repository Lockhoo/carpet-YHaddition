package com.yh.carpet.mixin;

import com.yh.carpet.TrialManager;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 试运行记录探针。
 *
 * <p>“更新跳略预览”需要把一次操作引起的所有方块变化记录下来，才能在跑完后回滚。
 * 所有方块修改最终都会走 {@code Level.setBlock(pos, state, flags, updateLimit)}
 * （{@code ServerLevel} 没有覆盖它），所以在这里的 HEAD 处记录“变化前”的状态。</p>
 */
@Mixin(Level.class)
public abstract class LevelMixin {
    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD")
    )
    private void yh$beforeBlockChange(BlockPos pos, BlockState state, int flags, int updateLimit,
                                      CallbackInfoReturnable<Boolean> cir) {
        TrialManager.beforeBlockChange((Level) (Object) this, pos);
    }
}
