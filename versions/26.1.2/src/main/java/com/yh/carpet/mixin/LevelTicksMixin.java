package com.yh.carpet.mixin;

import com.yh.carpet.TrialManager;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 试运行期间拦截“计划刻”的登记。
 *
 * <p>试运行是在服务器线程里同步跑完的，同步过程中登记的计划刻（中继器/侦测器的
 * 延迟动作、流体刻、方块刻等）都不会在本次调用栈里执行，而是等到之后的游戏刻才执行。
 * 由于世界随后会被回滚，这些计划刻就变成了“幽灵任务”，会在回滚之后继续影响世界，
 * 进而把玩家的更新跳略装置意外触发或卡住（表现为需要重置装置）。
 *
 * <p>丢弃它们不会改变试运行本身的连锁结果——计划刻永远不可能在本次同步的连锁更新里
 * 被执行，它只影响“之后”。因此这里直接取消登记即可，既保持预览准确，又不留下副作用。</p>
 */
@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin {
    @Inject(method = "schedule(Lnet/minecraft/world/ticks/ScheduledTick;)V", at = @At("HEAD"), cancellable = true)
    private void yh$suppressScheduledTicksDuringTrial(ScheduledTick<?> tick, CallbackInfo ci) {
        if (TrialManager.isRecording()) {
            ci.cancel();
        }
    }
}
