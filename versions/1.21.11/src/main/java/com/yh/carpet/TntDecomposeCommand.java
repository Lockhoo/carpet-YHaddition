package com.yh.carpet;

import carpet.utils.CommandHelper;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /yhtnt} — decompose red/blue TNT equivalents into encoder stage values.
 *
 * <pre>
 * /yhtnt &lt;red&gt; &lt;blue&gt;                              use the built-in 12 stages
 * /yhtnt &lt;red&gt; &lt;blue&gt; &lt;vertical&gt;                   also report the total equivalent
 * /yhtnt &lt;red&gt; &lt;blue&gt; [vertical] values &lt;list&gt;      use a custom stage list
 * </pre>
 *
 * {@code <list>} is space/comma/plus separated (e.g. {@code 400,100,1}); add a trailing
 * {@code repeat} token to allow a stage to be used more than once. Fails, like a
 * greedy string, at the end of the command — put it last.
 */
public final class TntDecomposeCommand {
    /** Standard 12 encoder stages (sum 1360) used when no custom list is given. */
    public static final int[] DEFAULT_VALUES = {340, 680, 10, 20, 40, 80, 80, 100, 4, 3, 2, 1};
    private static final int MAX_STORED_SOLUTIONS = 2000;
    private static final int MAX_TARGET = 1_000_000;

    private TntDecomposeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("yhtnt")
                .requires(TntDecomposeCommand::canUse)
                .then(Commands.argument("red", IntegerArgumentType.integer(0))
                        .then(Commands.argument("blue", IntegerArgumentType.integer(0))
                                .executes(ctx -> run(ctx, 0, null))
                                .then(valuesBranch(ctx -> run(ctx, 0, ctx.getArgument("stageList", String.class)),
                                        "stageList"))
                                .then(Commands.argument("vertical", IntegerArgumentType.integer(0))
                                        .executes(ctx -> run(ctx,
                                                IntegerArgumentType.getInteger(ctx, "vertical"), null))
                                        .then(valuesBranch(ctx -> run(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "vertical"),
                                                        ctx.getArgument("stageList", String.class)),
                                                "stageList"))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> valuesBranch(
            com.mojang.brigadier.Command<CommandSourceStack> command, String argName) {
        return Commands.literal("values").then(Commands.argument(argName, StringArgumentType.greedyString())
                .executes(command));
    }

    /**
     * Permission gate: the carpet rule {@code commandTntDecompose} (default {@code ops}),
     * or carpet-org-addition's {@code openCarpetPermission} on an integrated server —
     * same escape hatch as {@code /bot}.
     */
    public static boolean canUse(CommandSourceStack source) {
        if (CommandHelper.canUseCommand(source, YHAdditionSettings.commandTntDecompose)) {
            return true;
        }
        return ExtensionRules.canBypassCarpetPermissions(source);
    }

    private static int run(CommandContext<CommandSourceStack> ctx, int vertical, String stageList) {
        CommandSourceStack source = ctx.getSource();
        int red = IntegerArgumentType.getInteger(ctx, "red");
        int blue = IntegerArgumentType.getInteger(ctx, "blue");

        List<Integer> parsed = new ArrayList<>();
        boolean repeat = false;
        if (stageList != null) {
            for (String token : stageList.trim().split("[,，+\\s]+")) {
                if (token.isEmpty()) {
                    continue;
                }
                if ("repeat".equalsIgnoreCase(token) || "r".equalsIgnoreCase(token)) {
                    repeat = true;
                    continue;
                }
                try {
                    int v = Integer.parseInt(token);
                    if (v <= 0) {
                        throw new NumberFormatException(token);
                    }
                    parsed.add(v);
                } catch (NumberFormatException ignored) {
                    source.sendFailure(Component.literal("§c[YH] 无法识别的档位数: §f" + token
                            + " §c（用逗号/空格分隔，例如 400,100,1；末尾加 repeat 允许重复）"));
                    return 0;
                }
            }
            if (parsed.isEmpty()) {
                source.sendFailure(Component.literal("§c[YH] 档位列表是空的"));
                return 0;
            }
        }

        int[] values;
        if (parsed.isEmpty()) {
            values = DEFAULT_VALUES.clone();
        } else {
            values = new int[parsed.size()];
            for (int i = 0; i < parsed.size(); i++) {
                values[i] = parsed.get(i);
            }
        }
        Arrays.sort(values);
        // descending order reads nicer in the output and prunes faster
        for (int i = 0, j = values.length - 1; i < j; i++, j--) {
            int tmp = values[i];
            values[i] = values[j];
            values[j] = tmp;
        }

        int total = red + blue + vertical;
        final boolean repeatUse = repeat;
        final String verticalSuffix = vertical != 0 ? " §d+ 垂直 §f" + vertical : "";
        source.sendSuccess(() -> Component.literal("§d[YH] 当量 §f" + total
                + " §d= 红 §f" + red + " §d+ 蓝 §f" + blue + verticalSuffix), false);
        source.sendSuccess(() -> Component.literal("§8档位: §7" + formatValues(values)
                + " §8· " + (repeatUse ? "可重复使用" : "每个最多一次")), false);

        reportSide(source, "红TNT", red, values, repeatUse);
        reportSide(source, "蓝TNT", blue, values, repeatUse);
        return 1;
    }

    private static void reportSide(CommandSourceStack source, String label, int value, int[] values, boolean repeat) {
        if (value > MAX_TARGET) {
            source.sendSuccess(() -> Component.literal("§c[YH] " + label + " §f" + value
                    + " §c太大，超过工具上限 " + MAX_TARGET), false);
            return;
        }
        List<int[]> solutions = decompose(values, value, repeat);
        if (solutions.isEmpty()) {
            int sum = 0;
            for (int v : values) {
                sum += v;
            }
            String hint = value > sum
                    ? "（档位总和只有 " + sum + "）"
                    : "（换一组档位，或在末尾加 repeat 允许重复使用）";
            source.sendSuccess(() -> Component.literal("§c[YH] " + label + " §f" + value
                    + " §c无法用这些档位凑出 " + hint), false);
            return;
        }
        int[] best = solutions.get(0);
        String expression = format(best);
        Component line = Component.literal("§d[YH] " + label + " §f" + value + " §d= §f"
                        + expression + " §7(§f" + solutions.size() + "§7 种组合)")
                .withStyle(Style.EMPTY.withClickEvent(new ClickEvent.CopyToClipboard(expression)));
        source.sendSuccess(() -> line, false);
        if (solutions.size() > 1) {
            StringBuilder alternatives = new StringBuilder();
            int shown = Math.min(solutions.size(), 4);
            for (int i = 1; i < shown; i++) {
                if (alternatives.length() > 0) {
                    alternatives.append("§8 · ");
                }
                alternatives.append("§7").append(format(solutions.get(i)));
            }
            if (solutions.size() > shown) {
                alternatives.append(" §8…");
            }
            source.sendSuccess(() -> Component.literal("§8  其它: " + alternatives), false);
        }
    }

    /**
     * All distinct ways to sum {@code target} from {@code values} (each entry at most once,
     * or unlimited times when {@code repeat}). Results are deduplicated (the list may contain
     * the same stage twice, e.g. two 80s) and sorted by term count, then descending values.
     */
    static List<int[]> decompose(int[] values, int target, boolean repeat) {
        List<int[]> raw = new ArrayList<>();
        boolean[] truncated = {false};
        if (target == 0) {
            raw.add(new int[0]);
        } else if (repeat) {
            dfsRepeat(values, target, new ArrayList<>(), raw, truncated);
        } else {
            dfsOnce(values, 0, target, new ArrayList<>(), raw, truncated);
        }
        Set<String> seen = new LinkedHashSet<>();
        List<int[]> unique = new ArrayList<>();
        for (int[] solution : raw) {
            StringBuilder key = new StringBuilder();
            for (int v : solution) {
                key.append(v).append(',');
            }
            if (seen.add(key.toString())) {
                unique.add(solution);
            }
        }
        unique.sort((a, b) -> {
            if (a.length != b.length) {
                return a.length - b.length;
            }
            for (int i = 0; i < a.length; i++) {
                if (a[i] != b[i]) {
                    return b[i] - a[i];
                }
            }
            return 0;
        });
        return unique;
    }

    private static void dfsOnce(int[] values, int index, int remaining, List<Integer> path,
                                List<int[]> out, boolean[] truncated) {
        if (out.size() >= MAX_STORED_SOLUTIONS) {
            truncated[0] = true;
            return;
        }
        if (remaining == 0) {
            out.add(toArray(path));
            return;
        }
        for (int i = index; i < values.length; i++) {
            int v = values[i];
            if (v > remaining) {
                continue;
            }
            path.add(v);
            dfsOnce(values, i + 1, remaining - v, path, out, truncated);
            path.remove(path.size() - 1);
            if (truncated[0]) {
                return;
            }
        }
    }

    private static void dfsRepeat(int[] values, int remaining, List<Integer> path,
                                  List<int[]> out, boolean[] truncated) {
        if (out.size() >= MAX_STORED_SOLUTIONS) {
            truncated[0] = true;
            return;
        }
        if (remaining == 0) {
            out.add(toArray(path));
            return;
        }
        for (int v : values) {
            if (v > remaining) {
                continue;
            }
            path.add(v);
            dfsRepeat(values, remaining - v, path, out, truncated);
            path.remove(path.size() - 1);
            if (truncated[0]) {
                return;
            }
        }
    }

    private static int[] toArray(List<Integer> path) {
        int[] result = new int[path.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = path.get(i);
        }
        return result;
    }

    private static String format(int[] solution) {
        StringBuilder sb = new StringBuilder();
        for (int v : solution) {
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(v);
        }
        return sb.toString();
    }

    private static String formatValues(int[] values) {
        StringBuilder sb = new StringBuilder();
        for (int v : values) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(v);
        }
        return sb.toString();
    }
}
