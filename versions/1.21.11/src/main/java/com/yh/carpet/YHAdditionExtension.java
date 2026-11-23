package com.yh.carpet;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.CarpetSettings;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.Map;

/**
 * Carpet extension for carpet-YHaddition.
 *
 * <p>Register it from {@link YHAdditionMod} (a regular {@code ModInitializer});
 * never by mixing into Carpet itself.</p>
 */
public class YHAdditionExtension implements CarpetExtension {
    public static final String MOD_VERSION = "1.6.0";

    @Override
    public String version() {
        return MOD_VERSION;
    }

    @Override
    public void onGameStarted() {
        CarpetSettings.LOG.info("[carpet-YHaddition] extension loaded");
        // Register our rule into carpet's main settings manager so it appears in
        // /carpet and can be found by carpet-org-addition's /ruleSearch.
        CarpetServer.settingsManager.parseSettingsClass(YHAdditionSettings.class);
    }

    @Override
    public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext commandBuildContext) {
        BotCommand.register(dispatcher);
        TntDecomposeCommand.register(dispatcher);
    }

    @Override
    public void onTick(MinecraftServer server) {
        SkipHighlight.tick(server);
    }

    @Override
    public Map<String, String> canHasTranslations(String lang) {
        if ("zh_cn".equals(lang)) {
            return Map.ofEntries(
                    Map.entry("carpet.rule.commandBot.name", "bot 假人召唤命令"),
                    Map.entry("carpet.rule.commandBot.desc", "启用 /bot 一键召唤假人命令（/bot spawn range 1-10、/bot clear）"),
                    Map.entry("carpet.rule.commandBot.extra.0", "true 所有玩家可用，ops 仅管理员，0-4 权限等级，false 关闭"),
                    Map.entry("carpet.rule.commandBot.extra.1", "安装 carpet-org-addition 并开启 openCarpetPermission 后，单人（集成服务器）无需作弊也可使用 /bot"),
                    Map.entry("carpet.rule.commandTntDecompose.name", "当量分解命令"),
                    Map.entry("carpet.rule.commandTntDecompose.desc", "启用 /yhtnt 当量分解命令：把红/蓝 TNT 当量拆成编码器档位相加（例如 /yhtnt 452 596）"),
                    Map.entry("carpet.rule.commandTntDecompose.extra.0", "默认用标准 12 档 340,680,10,20,40,80,80,100,4,3,2,1；换炮时可在末尾写 values <档位列表>"),
                    Map.entry("carpet.rule.commandTntDecompose.extra.1", "true 所有玩家可用，ops 仅管理员，0-4 权限等级，false 关闭该命令"),
                    Map.entry("carpet.rule.oldwaterblockplacement.name", "旧版放水逻辑"),
                    Map.entry("carpet.rule.oldwaterblockplacement.desc", "恢复旧版水桶放置逻辑：潜行对着可含水方块（活板门、楼梯、台阶等）使用水桶时，水会直接放进该方块（含水），而不是放到相邻方块"),
                    Map.entry("carpet.rule.oldwaterblockplacement.extra.0", "等同于 1.21.5 之前的水桶行为；对水桶/岩浆桶均生效"),
                    Map.entry("carpet.rule.showSkippedUpdates.name", "显示更新跳略"),
                    Map.entry("carpet.rule.showSkippedUpdates.desc", "显示因更新跳略（连锁更新超过 max-chained-neighbor-updates 上限）而被丢弃更新的方块：青色粒子 = 下界传送门方块，红色粒子 = 其它被跳略的方块"),
                    Map.entry("carpet.rule.showSkippedUpdates.extra.0", "用于下界传送门的雕刻：把铁轨等连锁接到传送门上时，可用它确认哪些传送门方块的更新被挡在百万上限之外"),
                    Map.entry("carpet.rule.showSkippedUpdates.extra.1", "连锁触发后显示约 5 秒；上限由 server.properties 的 max-chained-neighbor-updates（默认 1000000）决定"),
                    Map.entry("carpet.rule.updateSkipPreview.name", "更新跳略预览"),
                    Map.entry("carpet.rule.updateSkipPreview.desc", "更新跳略预览：准星对准方块后按预览键（默认 V），真实试运行一次破坏/放置并回滚，然后给下界传送门方块上色：红色 = 会被破坏，青色 = 被跳略保住"),
                    Map.entry("carpet.rule.updateSkipPreview.extra.0", "用于下界传送门的雕刻：先用铁轨等把连锁接到传送门上，再对着要破坏的黑曜石/要放置的方块按 V 预览"),
                    Map.entry("carpet.rule.updateSkipPreview.extra.1", "试运行在服务器线程内完成并立即回滚，世界不会真的改变；但百万级连锁本身会有短暂卡顿"),
                    Map.entry("carpet.rule.updateSkipPreview.extra.2", "显示约 15 秒或准星移开后消失；客户端也需要安装本 mod 才能看到颜色")
            );
        }
        return Map.ofEntries(
                Map.entry("carpet.rule.commandBot.name", "bot fake player command"),
                Map.entry("carpet.rule.commandBot.desc", "Enables /bot one-click fake player spawning (/bot spawn range 1-10, /bot clear)"),
                Map.entry("carpet.rule.commandBot.extra.0", "true for everyone, ops for admins, 0-4 permission levels, false to disable"),
                Map.entry("carpet.rule.commandBot.extra.1", "With carpet-org-addition's openCarpetPermission enabled, /bot also works in single player without cheats (integrated server)"),
                Map.entry("carpet.rule.commandTntDecompose.name", "TNT equivalent decompose command"),
                Map.entry("carpet.rule.commandTntDecompose.desc", "Enables /yhtnt: decompose red/blue TNT equivalents into encoder stage values (e.g. /yhtnt 452 596)"),
                Map.entry("carpet.rule.commandTntDecompose.extra.0", "Uses the standard 12 stages 340,680,10,20,40,80,80,100,4,3,2,1 by default; append values <list> for a different cannon"),
                Map.entry("carpet.rule.commandTntDecompose.extra.1", "true for everyone, ops for admins, 0-4 permission levels, false to disable"),
                Map.entry("carpet.rule.oldwaterblockplacement.name", "old water bucket placement"),
                Map.entry("carpet.rule.oldwaterblockplacement.desc", "Restores the pre-1.21.5 bucket behaviour: sneaking and using a water/lava bucket on a waterloggable block (trapdoor, stairs, slab, ...) fills that block instead of placing the fluid in the adjacent block"),
                Map.entry("carpet.rule.oldwaterblockplacement.extra.0", "Same as vanilla pre-1.21.5 bucket behaviour; applies to water and lava buckets"),
                Map.entry("carpet.rule.showSkippedUpdates.name", "show skipped updates"),
                Map.entry("carpet.rule.showSkippedUpdates.desc", "Highlights blocks whose neighbour updates were dropped by the chained-update cap (update skip): cyan particles = nether portal blocks, red particles = other skipped blocks"),
                Map.entry("carpet.rule.showSkippedUpdates.extra.0", "Useful for nether portal carving: check whether rails and other chains push the portal block updates past the 1,000,000 cap"),
                Map.entry("carpet.rule.showSkippedUpdates.extra.1", "Shown for about 5 seconds after a cascade; the cap comes from server.properties max-chained-neighbor-updates (default 1000000)"),
                Map.entry("carpet.rule.updateSkipPreview.name", "update skip preview"),
                Map.entry("carpet.rule.updateSkipPreview.desc", "Update skip preview: aim at a block and press the preview key (default V); a real break/place trial run is executed and rolled back, then nether portal blocks are tinted: red = will be destroyed, cyan = saved by update skipping"),
                Map.entry("carpet.rule.updateSkipPreview.extra.0", "For nether portal carving: chain rails into the portal first, then preview the obsidian to break or the block to place"),
                Map.entry("carpet.rule.updateSkipPreview.extra.1", "The trial runs on the server thread and is rolled back immediately; the world does not change, but a million-update cascade can cause a short lag spike"),
                Map.entry("carpet.rule.updateSkipPreview.extra.2", "Highlight fades after about 15 seconds or when you look away; the client also needs this mod installed to see the colours")
        );
    }
}
