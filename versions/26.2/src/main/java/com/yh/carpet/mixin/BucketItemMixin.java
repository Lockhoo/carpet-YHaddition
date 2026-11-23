package com.yh.carpet.mixin;

import com.yh.carpet.YHAdditionSettings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BucketItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Restores the pre-1.21.5 bucket placement behaviour when the carpet rule
 * {@code oldwaterblockplacement} is enabled.
 *
 * <p>Since 1.21.5, {@link BucketItem#emptyContents} treats a sneaking player
 * specially: clicking a waterloggable block (trapdoor, stairs, slab, ...)
 * with a bucket of water while crouching no longer fills that block, it places
 * the fluid in the block adjacent to the clicked face instead. The old logic
 * ignored the sneaking state, so the clicked block was simply waterlogged.</p>
 *
 * <p>Forcing {@code isShiftKeyDown()} to {@code false} inside
 * {@code emptyContents} reproduces exactly the old behaviour: the fluid goes
 * into the clicked block, while every other case (air, replaceable blocks,
 * normal blocks) stays untouched.</p>
 */
@Mixin(BucketItem.class)
public class BucketItemMixin {
    @Redirect(
            method = "emptyContents(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/BlockHitResult;)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;isShiftKeyDown()Z")
    )
    private boolean yh$ignoreShiftWhenPlacingFluid(LivingEntity user) {
        if (YHAdditionSettings.oldwaterblockplacement) {
            return false;
        }
        return user.isShiftKeyDown();
    }
}
