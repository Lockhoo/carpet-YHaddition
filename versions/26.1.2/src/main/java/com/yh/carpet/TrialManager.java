package com.yh.carpet;

import com.yh.carpet.net.TrialResultPayload;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * “更新跳略预览”的试运行（trial-run）管理器。
 *
 * <p>思路：玩家按键时，服务端按准星目标真实执行一次“破坏”或“放置”，
 * 让整条连锁更新真的跑完（因此跳略会真的发生），把过程中每个方块的变化
 * 记录下来，然后立即回滚世界，最后把“哪些传送门方块会被破坏 / 哪些被跳略保住”
 * 发给客户端着色。这样不需要模拟连锁，结果与真实操作完全一致。</p>
 *
 * <p>回滚采用“安静写回”：{@code setBlock(pos, oldState, 2, 0)} 只同步给客户端，
 * 不触发邻居更新、不触发 shape update，因此不会再次引发连锁；方块实体则用
 * 试运行前保存的 NBT 重新载入。</p>
 */
public final class TrialManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("carpet-YHaddition");

    /** 试运行中最多记录多少个方块变化（超过则中止并回滚，防止内存爆炸）。 */
    private static final int MAX_CAPTURES = 1_000_000;
    /** 一次预览最多记录多少个被跳略的坐标。 */
    private static final int MAX_SKIPPED_RECORDS = 200_000;
    /** 结果里最多返回多少个坐标（客户端只显示这些）。 */
    private static final int MAX_RESULT_POSITIONS = 512;

    /** 内部异常：连锁变化过多，提前中止试运行（外层会回滚已经记录的部分）。 */
    private static final class AbortTrial extends RuntimeException {
        private AbortTrial() {
            super(null, null, false, false);
        }
    }

    private static boolean recording;
    private static ServerLevel trialLevel;
    private static Long2ObjectMap<BlockState> oldStates = new Long2ObjectOpenHashMap<>();
    private static Long2ObjectMap<CompoundTag> oldBlockEntityData = new Long2ObjectOpenHashMap<>();
    private static LongOpenHashSet skippedPositions = new LongOpenHashSet();

    private TrialManager() {
    }

    public static boolean isRecording() {
        return recording;
    }

    /**
     * 由 {@code LevelMixin} 在每次 {@code Level.setBlock(pos, state, flags, updateLimit)} 开头调用。
     * 试运行期间记录该坐标的原始方块状态与方块实体 NBT（同一坐标只记第一次）。
     */
    public static void beforeBlockChange(Level level, BlockPos pos) {
        if (!recording || level != trialLevel) {
            return;
        }
        long key = pos.asLong();
        if (oldStates.containsKey(key)) {
            return;
        }
        BlockState oldState = level.getBlockState(pos);
        oldStates.put(key, oldState);
        if (oldState.hasBlockEntity()) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                try {
                    oldBlockEntityData.put(key, blockEntity.saveWithFullMetadata(level.registryAccess()));
                } catch (Exception e) {
                    LOGGER.warn("[carpet-YHaddition] could not save block entity at {}", pos, e);
                }
            }
        }
        if (oldStates.size() > MAX_CAPTURES) {
            throw new AbortTrial();
        }
    }

    /**
     * 由 {@code CollectingNeighborUpdaterMixin} 在一条更新会被丢弃时调用。
     * 试运行期间收集到本次预览里；否则交给 {@link SkipHighlight} 走旧的粒子显示。
     */
    public static void onSkipped(Level level, BlockPos pos) {
        if (recording && level == trialLevel) {
            if (skippedPositions.size() < MAX_SKIPPED_RECORDS) {
                skippedPositions.add(pos.asLong());
            }
            return;
        }
        if (YHAdditionSettings.showSkippedUpdates) {
            SkipHighlight.record(level, pos);
        }
    }

    /**
     * 处理客户端发来的试运行请求（必须在服务器线程调用）。
     */
    public static void handleRequest(ServerPlayer player) {
        if (!YHAdditionSettings.updateSkipPreview) {
            // 规则关闭时完全静默：按键不产生任何提示或副作用。
            return;
        }
        if (recording) {
            sendChat(player, "§c[YH] 上一次预览还没有结束");
            return;
        }
        ServerLevel level = player.level();
        BlockHitResult hit = raycast(player, level);
        if (hit == null) {
            sendChat(player, "§c[YH] 请先用准星对准要破坏或放置的方块");
            return;
        }

        BlockPos target = hit.getBlockPos();
        ItemStack stack = player.getMainHandItem();
        boolean placing = stack.getItem() instanceof BlockItem;

        oldStates = new Long2ObjectOpenHashMap<>();
        oldBlockEntityData = new Long2ObjectOpenHashMap<>();
        skippedPositions = new LongOpenHashSet();
        trialLevel = level;
        String error = null;
        int heldCount = stack.getCount();
        recording = true;
        try {
            if (placing) {
                // 与右键放置走同一条逻辑（消耗的手持物品稍后补回）。
                InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                if (!result.consumesAction()) {
                    error = "无法在该位置放置这个方块";
                }
            } else {
                BlockState state = level.getBlockState(target);
                if (state.isAir()) {
                    error = "准星没有对准方块";
                } else {
                    // 与 Level.destroyBlock 的落块方式一致（液体则放回流体），但 flags 只有 1：
                    // 连锁更新照常发生，只是先不把方块变化发给客户端，减少闪烁。
                    level.setBlock(target, level.getFluidState(target).createLegacyBlock(), 1, 512);
                }
            }
        } catch (AbortTrial abort) {
            error = "连锁更新太多，预览已中止并回滚";
        } catch (Throwable t) {
            error = "预览时出错：" + t.getClass().getSimpleName() + "（已尝试回滚）";
            LOGGER.error("[carpet-YHaddition] trial run failed", t);
        } finally {
            recording = false;
            if (placing) {
                stack.setCount(heldCount);
            }
        }

        // 回滚之前先分类：传送门的旧状态 vs 当前状态。
        List<BlockPos> destroyed = new ArrayList<>();
        List<BlockPos> skipped = new ArrayList<>();
        int destroyedCount = 0;
        int skippedCount = 0;
        for (Long2ObjectMap.Entry<BlockState> entry : oldStates.long2ObjectEntrySet()) {
            if (!entry.getValue().is(Blocks.NETHER_PORTAL)) {
                continue;
            }
            BlockPos pos = BlockPos.of(entry.getLongKey());
            if (!level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                destroyedCount++;
                if (destroyed.size() < MAX_RESULT_POSITIONS) {
                    destroyed.add(pos);
                }
            }
        }
        for (long key : skippedPositions) {
            BlockPos pos = BlockPos.of(key);
            if (!level.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                continue;
            }
            skippedCount++;
            if (skipped.size() < MAX_RESULT_POSITIONS) {
                skipped.add(pos);
            }
        }

        rollback(level);

        if (error != null) {
            sendChat(player, "§c[YH] " + error);
        } else {
            player.sendSystemMessage(Component.literal("§d[YH] 预览：§f" + destroyedCount
                    + " §d个传送门方块会被破坏，§f" + skippedCount + " §d个被跳略保住"), true);
        }

        ServerPlayNetworking.send(player, new TrialResultPayload(
                level.dimension().identifier().toString(),
                target.getX(), target.getY(), target.getZ(),
                destroyed, skipped, destroyedCount, skippedCount,
                error == null ? "" : error));
    }

    /** 把试运行记录下来的改动安静地写回原状态。 */
    private static void rollback(ServerLevel level) {
        recording = false;
        for (Long2ObjectMap.Entry<BlockState> entry : oldStates.long2ObjectEntrySet()) {
            BlockPos pos = BlockPos.of(entry.getLongKey());
            BlockState target = entry.getValue();
            if (level.getBlockState(pos) == target) {
                continue;
            }
            level.setBlock(pos, target, 2, 0);
            CompoundTag tag = oldBlockEntityData.get(entry.getLongKey());
            if (tag == null) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity == null) {
                continue;
            }
            try {
                blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
                blockEntity.setChanged();
                ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(blockEntity);
                for (ServerPlayer other : level.players()) {
                    other.connection.send(packet);
                }
            } catch (Exception e) {
                LOGGER.warn("[carpet-YHaddition] could not restore block entity at {}", pos, e);
            }
        }
        oldStates = new Long2ObjectOpenHashMap<>();
        oldBlockEntityData = new Long2ObjectOpenHashMap<>();
        skippedPositions = new LongOpenHashSet();
        trialLevel = null;
    }

    private static BlockHitResult raycast(ServerPlayer player, ServerLevel level) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        double reach = player.blockInteractionRange();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static void sendChat(ServerPlayer player, String message) {
        player.sendSystemMessage(Component.literal(message));
    }
}
