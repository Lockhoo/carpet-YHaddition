package com.yh.carpet;

import carpet.patches.EntityPlayerMPFake;
import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /bot} command family.
 *
 * <ul>
 *   <li>{@code /bot spawn range <range>} — spawn fake players in one go. The range
 *       is either a single number {@code N} (bots {@code 1..N}) or {@code A-B}
 *       (bots {@code A..B}). Each fake player is created through Carpet's own
 *       {@link EntityPlayerMPFake#createFake}, so it behaves exactly like
 *       {@code /player <name> spawn}.</li>
 *   <li>{@code /bot clear} — removes only the fake players that were summoned
 *       through {@code /bot} (tracked by {@link BotManager}), leaving fake
 *       players spawned with vanilla carpet's {@code /player} untouched.</li>
 * </ul>
 *
 * <p>Both sub-commands are gated by the carpet rule {@code commandBot}
 * ({@link YHAdditionSettings#commandBot}). When carpet-org-addition's
 * {@code openCarpetPermission} switch is on, they are also usable in single player
 * without cheats — see {@link #canUse(CommandSourceStack)}.</p>
 */
public final class BotCommand {
    /** Safety cap so a typo cannot flood the server with bots. */
    public static final int MAX_BOTS_PER_CALL = 100;

    private BotCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bot")
                .requires(BotCommand::canUse)
                .then(Commands.literal("spawn")
                        .then(Commands.literal("range")
                                .then(Commands.argument("range", StringArgumentType.word())
                                        .executes(BotCommand::spawnRange))))
                .then(Commands.literal("clear")
                        .executes(BotCommand::clear)));
    }

    /**
     * Permission gate for {@code /bot}.
     *
     * <p>The regular gate is the carpet rule {@code commandBot} (default {@code ops},
     * which needs cheats in single player). As an extra escape hatch this also accepts
     * carpet-org-addition's {@code openCarpetPermission} switch — the same switch that
     * lets a single player world owner use {@code /carpet} without cheats; see
     * {@link ExtensionRules#canBypassCarpetPermissions(CommandSourceStack)}.</p>
     */
    public static boolean canUse(CommandSourceStack source) {
        if (CommandHelper.canUseCommand(source, YHAdditionSettings.commandBot)) {
            return true;
        }
        return ExtensionRules.canBypassCarpetPermissions(source);
    }

    private static int spawnRange(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        MinecraftServer server = source.getServer();
        String raw = StringArgumentType.getString(context, "range");

        int start;
        int end;
        try {
            int dash = raw.indexOf('-');
            if (dash < 0) {
                start = 1;
                end = Integer.parseInt(raw);
            } else {
                start = Integer.parseInt(raw.substring(0, dash));
                end = Integer.parseInt(raw.substring(dash + 1));
            }
        } catch (NumberFormatException e) {
            source.sendFailure(Component.literal("范围格式错误: " + raw + "，正确写法如 5 或 1-10"));
            return 0;
        }

        if (start < 1 || end < start) {
            source.sendFailure(Component.literal("范围不合法: " + raw + "（要求 1 <= start <= end）"));
            return 0;
        }
        if (end - start + 1 > MAX_BOTS_PER_CALL) {
            source.sendFailure(Component.literal("一次最多召唤 " + MAX_BOTS_PER_CALL + " 个假人，当前为 " + (end - start + 1) + " 个"));
            return 0;
        }

        Vec3 pos = source.getPosition();
        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) {
            source.sendFailure(Component.literal("当前坐标超出可生成范围"));
            return 0;
        }
        Vec2 facing = source.getRotation();
        ResourceKey<Level> dimension = source.getLevel().dimension();

        // Mirror /player <name> spawn: inherit the sender's game mode when a player
        // runs the command, otherwise default to creative; spectator flies.
        GameType gameMode = GameType.CREATIVE;
        boolean flying = false;
        if (source.getEntity() instanceof ServerPlayer sender) {
            gameMode = sender.gameMode.getGameModeForPlayer();
            flying = sender.getAbilities().flying;
        }
        if (gameMode == GameType.SPECTATOR) {
            flying = true;
        } else if (gameMode.isSurvival()) {
            flying = false;
        }

        int spawned = 0;
        List<String> skipped = new ArrayList<>();
        List<String> failed = new ArrayList<>();
        for (int i = start; i <= end; i++) {
            String name = Integer.toString(i);
            if (server.getPlayerList().getPlayerByName(name) != null
                    || EntityPlayerMPFake.isSpawningPlayer(name)) {
                skipped.add(name);
                continue;
            }
            boolean ok = EntityPlayerMPFake.createFake(name, server, pos, facing.y, facing.x, dimension, gameMode, flying);
            if (ok) {
                BotManager.onBotSpawned(name);
                spawned++;
            } else {
                failed.add(name);
            }
        }

        int result = spawned;
        String rangeLabel = start + "-" + end;
        source.sendSuccess(() -> Component.literal("已召唤 " + result + " 个假人（" + rangeLabel + "）"), true);
        if (!skipped.isEmpty()) {
            source.sendSuccess(() -> Component.literal("已跳过已存在的假人: " + String.join(", ", skipped)), false);
        }
        if (!failed.isEmpty()) {
            source.sendFailure(Component.literal("无法生成: " + String.join(", ", failed)
                    + "（这些名字不存在，请开启 allowSpawningOfflinePlayers 规则）"));
        }
        return spawned > 0 ? 1 : 0;
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        int removed = BotManager.clearBots(source.getServer());
        source.sendSuccess(() -> Component.literal("已清除 " + removed + " 个由 /bot 召唤的假人"), true);
        return removed > 0 ? 1 : 0;
    }
}
