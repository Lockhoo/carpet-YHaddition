package com.yh.carpet.mixin;

import com.yh.carpet.TrialManager;
import com.yh.carpet.YHAdditionSettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.redstone.CollectingNeighborUpdater;
import net.minecraft.world.level.redstone.NeighborUpdater;
import net.minecraft.world.level.redstone.Orientation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 更新跳略探针。
 *
 * <p>每个方块更新都会先进到 {@code CollectingNeighborUpdater} 的这几个入口方法，
 * 再交给 {@code addAndRun} 计数。当 {@code count >= maxChainedNeighborUpdates}
 * 时，{@code addAndRun} 会把这条更新直接丢弃——也就是“更新跳略”。我们在这几个
 * 入口的 HEAD 处用同样的条件判断“这条更新是否会被丢弃”，是则把目标方块坐标
 * 交给 {@link TrialManager}（试运行期间收集，否则转给旧版粒子显示）。</p>
 *
 * <p>为什么不在 {@code addAndRun} 里注入：它的参数类型是私有嵌套接口
 * {@code NeighborUpdates}，mixin 处理器无法引用；而这几个人口方法的参数都是
 * 公开类型，且从入口到 {@code addAndRun} 之间不会再修改 {@code count}，
 * 因此在 HEAD 处的判断与 {@code addAndRun} 内的判断等价。</p>
 */
@Mixin(CollectingNeighborUpdater.class)
public abstract class CollectingNeighborUpdaterMixin {
    @Shadow
    private int count;

    @Shadow
    @Final
    private int maxChainedNeighborUpdates;

    @Shadow
    @Final
    private Level level;

    private boolean yh$willSkip() {
        return (TrialManager.isRecording() || YHAdditionSettings.showSkippedUpdates)
                && this.maxChainedNeighborUpdates >= 0
                && this.count >= this.maxChainedNeighborUpdates;
    }

    @Inject(
            method = "shapeUpdate(Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/BlockPos;II)V",
            at = @At("HEAD")
    )
    private void yh$onShapeUpdate(Direction direction, BlockState neighborState, BlockPos pos, BlockPos neighborPos,
                                  int updateFlags, int updateLimit, CallbackInfo ci) {
        if (this.yh$willSkip()) {
            TrialManager.onSkipped(this.level, pos);
        }
    }

    @Inject(
            method = "neighborChanged(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("HEAD")
    )
    private void yh$onNeighborChanged(BlockPos pos, Block block, Orientation orientation, CallbackInfo ci) {
        if (this.yh$willSkip()) {
            TrialManager.onSkipped(this.level, pos);
        }
    }

    @Inject(
            method = "neighborChanged(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/world/level/redstone/Orientation;Z)V",
            at = @At("HEAD")
    )
    private void yh$onFullNeighborChanged(BlockState state, BlockPos pos, Block block, Orientation orientation,
                                          boolean movedByPiston, CallbackInfo ci) {
        if (this.yh$willSkip()) {
            TrialManager.onSkipped(this.level, pos);
        }
    }

    @Inject(
            method = "updateNeighborsAtExceptFromFacing(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/redstone/Orientation;)V",
            at = @At("HEAD")
    )
    private void yh$onUpdateNeighbors(BlockPos pos, Block block, Direction skipDirection, Orientation orientation,
                                      CallbackInfo ci) {
        if (!this.yh$willSkip()) {
            return;
        }
        for (Direction direction : NeighborUpdater.UPDATE_ORDER) {
            if (direction != skipDirection) {
                TrialManager.onSkipped(this.level, pos.relative(direction));
            }
        }
    }
}
