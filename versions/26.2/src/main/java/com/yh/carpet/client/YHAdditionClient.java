package com.yh.carpet.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.yh.carpet.net.TrialResultPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.BlockColorRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.world.level.block.Blocks;

/**
 * 客户端入口：注册“更新跳略预览”的按键、方块着色与结果接收。
 */
public class YHAdditionClient implements ClientModInitializer {
    /** 预览键（默认 V，可在原版按键设置里改）。 */
    public static final KeyMapping UPDATE_SKIP_PREVIEW = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.carpet-yhaddition.update_skip_preview", InputConstants.KEY_V, KeyMapping.Category.MISC));

    @Override
    public void onInitializeClient() {
        BlockColorRegistry.register(SkipHighlightClient::collectTint, Blocks.NETHER_PORTAL);
        ClientPlayNetworking.registerGlobalReceiver(TrialResultPayload.TYPE,
                (payload, context) -> SkipHighlightClient.onPayload(payload));
        ClientTickEvents.END_CLIENT_TICK.register(SkipHighlightClient::tick);
    }
}
