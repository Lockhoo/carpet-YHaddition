package com.yh.carpet;

import carpet.settings.Rule;

import static carpet.api.settings.RuleCategory.COMMAND;
import static carpet.api.settings.RuleCategory.FEATURE;

/**
 * Carpet rules registered into carpet's own settings manager by
 * {@link YHAdditionExtension#onGameStarted()}, so they show up in
 * {@code /carpet} (and can be found via carpet-org-addition's {@code /ruleSearch}).
 */
public final class YHAdditionSettings {
    private YHAdditionSettings() {
    }

    @Rule(
            desc = "启用 /bot 一键召唤假人命令（/bot spawn range 1-10、/bot clear）",
            extra = {
                    "true 所有玩家可用，ops 仅管理员，0-4 权限等级，false 关闭该命令",
                    "安装 carpet-org-addition 并开启 openCarpetPermission 后，单人（集成服务器）无需作弊也可使用 /bot"
            },
            category = COMMAND
    )
    public static String commandBot = "ops";

    @Rule(
            desc = "启用 /yhtnt 当量分解命令：把红/蓝 TNT 当量拆成编码器档位相加（例如 /yhtnt 452 596）",
            extra = {
                    "默认用标准 12 档 340,680,10,20,40,80,80,100,4,3,2,1；换炮时可在末尾写 values <档位列表>",
                    "true 所有玩家可用，ops 仅管理员，0-4 权限等级，false 关闭该命令"
            },
            category = COMMAND
    )
    public static String commandTntDecompose = "ops";

    @Rule(
            desc = "恢复旧版水桶放置逻辑：潜行对着可含水方块（活板门、楼梯、台阶等）使用水桶时，水会直接放进该方块（含水），而不是放到相邻方块",
            extra = {"等同于 1.21.5 之前的水桶行为；对水桶/岩浆桶均生效"},
            category = FEATURE
    )
    public static boolean oldwaterblockplacement = false;

    @Rule(
            desc = "更新跳略预览：准星对准方块后按预览键（默认 V），真实试运行一次破坏/放置并回滚，然后给下界传送门方块上色：红色 = 会被破坏，青色 = 被跳略保住",
            extra = {
                    "用于下界传送门的雕刻：先用铁轨等把连锁接到传送门上，再对着要破坏的黑曜石/要放置的方块按 V 预览",
                    "试运行在服务器线程内完成并立即回滚，世界不会真的改变；但百万级连锁本身会有短暂卡顿",
                    "显示约 15 秒或准星移开后消失；客户端也需要安装本 mod 才能看到颜色"
            },
            category = FEATURE
    )
    public static boolean updateSkipPreview = false;

    @Rule(
            desc = "显示因更新跳略（连锁更新超过 max-chained-neighbor-updates 上限）而被丢弃更新的方块：青色粒子 = 下界传送门方块，红色粒子 = 其它被跳略的方块",
            extra = {
                    "用于下界传送门的雕刻：把铁轨等连锁接到传送门上时，可用它确认哪些传送门方块的更新被挡在百万上限之外",
                    "连锁触发后显示约 5 秒，移开视线/超时后消失；上限由 server.properties 的 max-chained-neighbor-updates 决定"
            },
            category = FEATURE
    )
    public static boolean showSkippedUpdates = false;
}
