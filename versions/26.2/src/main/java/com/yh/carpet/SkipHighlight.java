package com.yh.carpet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Set;

/**
 * 更新跳略（update skip）可视化。
 *
 * <p>原版 {@code CollectingNeighborUpdater} 在一次连锁更新中累计提交次数，
 * 一旦达到 {@code max-chained-neighbor-updates}（server.properties，默认 1,000,000），
 * 之后的方块更新会被直接丢弃（更新跳略）。{@code CollectingNeighborUpdaterMixin}
 * 会在跳略发生时把“本来要收到更新的方块坐标”记录到这里，本类负责在一段时间内
 * 用彩色粒子把这些方块标出来：青色 = 下界传送门方块，红色 = 其它被跳略的方块。</p>
 */
public final class SkipHighlight {
    /** 每次连锁最多记录 / 显示的传送门方块数。 */
    private static final int MAX_PORTAL_POSITIONS = 512;
    /** 每次连锁最多记录 / 显示的其它方块数（避免链路很长时内存和粒子数量失控）。 */
    private static final int MAX_OTHER_POSITIONS = 512;
    /** 最后一次跳略之后继续显示多久（毫秒）。 */
    private static final long DISPLAY_MILLIS = 5000L;
    /** 只给附近的玩家发粒子 / 提示。 */
    private static final double SHOW_RADIUS = 64.0D;
    private static final double SHOW_RADIUS_SQR = SHOW_RADIUS * SHOW_RADIUS;

    private static final DustParticleOptions PORTAL_MARK = new DustParticleOptions(0x00FFFF, 1.4F);
    private static final DustParticleOptions OTHER_MARK = new DustParticleOptions(0xFF3030, 1.0F);

    private static ResourceKey<Level> dimension;
    private static BlockPos anchor;
    private static final Set<BlockPos> portalBlocks = new HashSet<>();
    private static final Set<BlockPos> otherBlocks = new HashSet<>();
    private static long skippedUpdates;
    private static long expireAt;
    private static boolean announced;

    private SkipHighlight() {
    }

    /**
     * 记录一个“更新被跳略”的方块。由 mixin 在跳略发生时调用（只在规则开启时）。
     */
    public static void record(Level level, BlockPos pos) {
        long now = System.currentTimeMillis();
        if (now > expireAt || !level.dimension().equals(dimension)) {
            reset();
            dimension = level.dimension();
            anchor = pos.immutable();
        }
        skippedUpdates++;
        BlockState state = level.getBlockState(pos);
        if (state.is(Blocks.NETHER_PORTAL)) {
            if (portalBlocks.size() < MAX_PORTAL_POSITIONS) {
                portalBlocks.add(pos.immutable());
            }
        } else if (otherBlocks.size() < MAX_OTHER_POSITIONS) {
            otherBlocks.add(pos.immutable());
        }
        if (!announced) {
            announced = true;
            announce(level, pos);
        }
        expireAt = now + DISPLAY_MILLIS;
    }

    /**
     * 每个服务器 tick 调用：给附近玩家发送高亮粒子，并在显示结束时发一条汇总。
     */
    public static void tick(MinecraftServer server) {
        if (expireAt == 0L) {
            return;
        }
        ServerLevel level = dimension == null ? null : server.getLevel(dimension);
        if (level == null) {
            reset();
            return;
        }
        if (System.currentTimeMillis() > expireAt) {
            if (announced) {
                sendSummary(level);
            }
            reset();
            return;
        }
        if (portalBlocks.isEmpty() && otherBlocks.isEmpty()) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.level().dimension().equals(dimension)) {
                continue;
            }
            for (BlockPos pos : portalBlocks) {
                if (inRange(player, pos)) {
                    send(level, player, pos, PORTAL_MARK, 4, 0.35D);
                }
            }
            for (BlockPos pos : otherBlocks) {
                if (inRange(player, pos)) {
                    send(level, player, pos, OTHER_MARK, 1, 0.2D);
                }
            }
        }
    }

    private static void send(ServerLevel level, ServerPlayer player, BlockPos pos, DustParticleOptions particle, int count, double spread) {
        level.sendParticles(player, particle, true, true,
                pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                count, spread, spread, spread, 0.0D);
    }

    private static boolean inRange(ServerPlayer player, BlockPos pos) {
        double dx = player.getX() - (pos.getX() + 0.5D);
        double dy = player.getY() - (pos.getY() + 0.5D);
        double dz = player.getZ() - (pos.getZ() + 0.5D);
        return dx * dx + dy * dy + dz * dz <= SHOW_RADIUS_SQR;
    }

    private static void announce(Level level, BlockPos firstPos) {
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        Component message = Component.literal("§d[YH] 检测到更新跳略：本次连锁更新已达上限，后续更新将被丢弃");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension()) && inRange(player, firstPos)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static void sendSummary(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null || anchor == null) {
            return;
        }
        Component message = Component.literal("§d[YH] 更新跳略：本次连锁共丢弃 §f" + skippedUpdates
                + " §d个方块更新，其中传送门方块 §f" + portalBlocks.size() + " §d个");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level().dimension().equals(level.dimension()) && inRange(player, anchor)) {
                player.sendSystemMessage(message);
            }
        }
    }

    private static void reset() {
        dimension = null;
        anchor = null;
        portalBlocks.clear();
        otherBlocks.clear();
        skippedUpdates = 0L;
        expireAt = 0L;
        announced = false;
    }
}
