package com.yh.carpet.client;

import com.yh.carpet.net.TrialRequestPayload;
import com.yh.carpet.net.TrialResultPayload;
import it.unimi.dsi.fastutil.ints.IntList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 客户端高亮状态：记录服务端试运行返回的“会被破坏 / 被跳略保住”的传送门方块，
 * 通过方块颜色（tint）把它们短暂染成红色/青色，准星移开或超时后恢复。
 */
public final class SkipHighlightClient {
    private static final long DISPLAY_MILLIS = 15_000L;
    private static final long REQUEST_COOLDOWN_MILLIS = 300L;
    // 注意：tint 颜色按 ARGB 使用（alpha 0 会让方块完全透明），所以这里必须带上 0xFF alpha。
    private static final int TINT_DEFAULT = 0xFFFFFFFF;
    private static final int TINT_DESTROYED = 0xFFFF3030;
    private static final int TINT_SKIPPED = 0xFF30FFFF;

    private static final Map<BlockPos, Integer> highlights = new HashMap<>();
    private static Identifier dimension;
    private static BlockPos aim;
    private static long expireAt;
    private static long lastRequest;

    private SkipHighlightClient() {
    }

    /** 方块颜色回调：下界传送门默认白色，命中的坐标按类别染色。 */
    public static void collectTint(BlockState state, BlockAndTintGetter world, BlockPos pos, IntList colors) {
        Integer tint = highlights.isEmpty() ? null : highlights.get(pos);
        colors.add(tint == null ? TINT_DEFAULT : tint);
    }

    public static void onPayload(TrialResultPayload payload) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        Identifier dim = Identifier.tryParse(payload.dimension());
        if (dim == null || !client.level.dimension().identifier().equals(dim)) {
            return;
        }
        clear();
        for (BlockPos pos : payload.destroyed()) {
            highlights.put(pos, TINT_DESTROYED);
        }
        for (BlockPos pos : payload.skipped()) {
            highlights.put(pos, TINT_SKIPPED);
        }
        dimension = dim;
        aim = new BlockPos(payload.aimX(), payload.aimY(), payload.aimZ());
        expireAt = System.currentTimeMillis() + DISPLAY_MILLIS;
        if (!payload.message().isEmpty()) {
            client.player.sendSystemMessage(Component.literal("§c[YH] " + payload.message()));
        }
        rebuild();
    }

    /** 客户端能看到 carpet 规则是否开启时，功能关闭就不发请求；看不到则交给服务端静默处理。 */
    private static boolean previewRuleEnabled() {
        try {
            carpet.settings.SettingsManager manager = carpet.CarpetServer.settingsManager;
            if (manager == null) {
                return true;
            }
            carpet.api.settings.CarpetRule<?> rule = manager.getCarpetRule("updateSkipPreview");
            if (rule == null) {
                return true;
            }
            Object value = rule.value();
            return !(value instanceof Boolean enabled) || enabled;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null) {
            if (!highlights.isEmpty()) {
                clear();
            }
            return;
        }

        if (YHAdditionClient.UPDATE_SKIP_PREVIEW.consumeClick()) {
            long now = System.currentTimeMillis();
            // 功能未开启时按键不发送任何请求（避免服务端回一条提示）。
            if (now - lastRequest >= REQUEST_COOLDOWN_MILLIS && previewRuleEnabled()) {
                lastRequest = now;
                ClientPlayNetworking.send(new TrialRequestPayload());
            }
        }

        if (highlights.isEmpty()) {
            return;
        }
        if (System.currentTimeMillis() > expireAt) {
            clear();
            return;
        }
        BlockPos looked = aimedPos(client);
        if (looked == null || !looked.equals(aim)) {
            clear();
        }
    }

    private static BlockPos aimedPos(Minecraft client) {
        if (client.hitResult instanceof BlockHitResult hit) {
            return hit.getBlockPos();
        }
        return null;
    }

    private static void clear() {
        if (highlights.isEmpty()) {
            dimension = null;
            aim = null;
            return;
        }
        // 必须先记下“被染过色的”坐标，清空之后再让这些区块重新生成网格，
        // 否则地图里已经没有这些坐标，渲染层不会刷新，颜色就会一直留在屏幕上。
        List<BlockPos> wasHighlighted = new ArrayList<>(highlights.keySet());
        highlights.clear();
        dimension = null;
        aim = null;
        rebuild(wasHighlighted);
    }

    private static void rebuild() {
        rebuild(highlights.keySet());
    }

    private static void rebuild(Collection<BlockPos> positions) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }
        for (BlockPos pos : positions) {
            client.level.setSectionDirtyWithNeighbors(pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }
    }
}
