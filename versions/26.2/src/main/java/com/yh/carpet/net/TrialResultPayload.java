package com.yh.carpet.net;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * 服务端 -> 客户端：一次“更新跳略预览”试运行的结果。
 *
 * <p>{@code destroyed} 是试运行中会被破坏的下界传送门方块（红色），
 * {@code skipped} 是因更新跳略而保住的传送门方块（青色）。
 * 服务端已经回滚了试运行改动，客户端只负责按准星显示这些位置。</p>
 */
public record TrialResultPayload(
        String dimension,
        int aimX, int aimY, int aimZ,
        List<BlockPos> destroyed,
        List<BlockPos> skipped,
        int destroyedCount,
        int skippedCount,
        String message
) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TrialResultPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("carpet-yhaddition", "trial_result"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TrialResultPayload> CODEC =
            StreamCodec.of((buf, payload) -> payload.write(buf), TrialResultPayload::new);

    private TrialResultPayload(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(),
                buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                readPositions(buf), readPositions(buf),
                buf.readVarInt(), buf.readVarInt(),
                buf.readUtf());
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(this.dimension);
        buf.writeVarInt(this.aimX);
        buf.writeVarInt(this.aimY);
        buf.writeVarInt(this.aimZ);
        writePositions(buf, this.destroyed);
        writePositions(buf, this.skipped);
        buf.writeVarInt(this.destroyedCount);
        buf.writeVarInt(this.skippedCount);
        buf.writeUtf(this.message);
    }

    private static void writePositions(RegistryFriendlyByteBuf buf, List<BlockPos> positions) {
        buf.writeVarInt(positions.size());
        for (BlockPos pos : positions) {
            buf.writeVarInt(pos.getX());
            buf.writeVarInt(pos.getY());
            buf.writeVarInt(pos.getZ());
        }
    }

    private static List<BlockPos> readPositions(RegistryFriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<BlockPos> positions = new ArrayList<>(Math.min(size, 1024));
        for (int i = 0; i < size; i++) {
            positions.add(new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return positions;
    }

    @Override
    public CustomPacketPayload.Type<TrialResultPayload> type() {
        return TYPE;
    }
}
