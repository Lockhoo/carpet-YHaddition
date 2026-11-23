package com.yh.carpet.mixin;

import com.yh.carpet.TrialManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 试运行期间拦截“方块事件”和“实体生成”这两个无法回滚的副作用。
 *
 * <p>方块事件（活塞推动、箱子开合动画等）会进入 {@code ServerLevel.blockEvents} 队列，
 * 在之后的游戏刻执行；如果试运行过程中登记了活塞事件，回滚后活塞仍会被事件推动方块，
 * 把已经恢复好的装置再破坏一次，导致装置需要重置。实体生成（沙砾/沙子下落、TNT 等）
 * 同样会在回滚后留在世界里，所以一并丢弃。</p>
 *
 * <p>这两者都只在“之后的游戏刻”发生，丢弃它们不会改变同步连锁更新的结果。</p>
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {
    @Inject(method = "blockEvent(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/Block;II)V",
            at = @At("HEAD"), cancellable = true)
    private void yh$suppressBlockEventsDuringTrial(BlockPos pos, Block block, int eventId, int eventParam, CallbackInfo ci) {
        if (TrialManager.isRecording()) {
            ci.cancel();
        }
    }

    @Inject(method = "addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void yh$suppressEntitySpawnDuringTrial(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (TrialManager.isRecording()) {
            cir.setReturnValue(false);
        }
    }
}
