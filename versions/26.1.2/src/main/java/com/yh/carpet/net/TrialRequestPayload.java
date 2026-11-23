package com.yh.carpet.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 客户端 -> 服务端：请求执行一次“更新跳略预览”的试运行。
 *
 * <p>客户端按键时发送；服务端用玩家当前准星目标判断是破坏还是放置，
 * 真实跑一次连锁更新后回滚，再把结果（{@link TrialResultPayload}）发回客户端。</p>
 */
public record TrialRequestPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TrialRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("carpet-yhaddition", "trial_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrialRequestPayload> CODEC =
            StreamCodec.unit(new TrialRequestPayload());

    @Override
    public CustomPacketPayload.Type<TrialRequestPayload> type() {
        return TYPE;
    }
}
