package com.yh.carpet;

import carpet.CarpetServer;
import carpet.api.settings.CarpetRule;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

/**
 * Reads (but never owns) rules contributed by other Carpet extensions, so this mod
 * can stay compatible without a compile-time dependency on them.
 *
 * <p>Currently this mirrors carpet-org-addition's {@code openCarpetPermission} rule.
 * That rule (see {@code boat.carpetorgaddition.mixin.rule.client.carpet.SettingsManagerMixin}
 * in carpet-org-addition) only patches the permission check of the {@code /carpet}
 * root command, and only on the integrated (single player) server. It does not touch
 * {@code CommandHelper.canUseCommand}, so other mods' commands — like our {@code /bot} —
 * have to honour the same switch themselves, which is exactly what this class is for.</p>
 */
public final class ExtensionRules {
    /** carpet-org-addition renamed the rule in 1.46.0; accept both spellings. */
    private static final String[] OPEN_CARPET_PERMISSION_NAMES = {"opencarpetpermission", "opencarpetpermissions"};

    private static final String INTEGRATED_SERVER_CLASS = "net.minecraft.client.server.IntegratedServer";

    private ExtensionRules() {
    }

    /**
     * @return {@code true} when carpet-org-addition's {@code openCarpetPermission}
     *         rule exists and is currently enabled.
     */
    public static boolean isOpenCarpetPermissionEnabled() {
        final boolean[] enabled = {false};
        CarpetServer.forEachManager(manager -> {
            if (enabled[0]) {
                return;
            }
            for (CarpetRule<?> rule : manager.getCarpetRules()) {
                if (!isOpenCarpetPermissionRule(rule.name())) {
                    continue;
                }
                if (Boolean.TRUE.equals(rule.value())) {
                    enabled[0] = true;
                }
                return;
            }
        });
        return enabled[0];
    }

    /**
     * Mirrors the scope of org-addition's own implementation: the rule only applies to
     * the integrated (single player) server, never to a dedicated server.
     */
    public static boolean canBypassCarpetPermissions(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null || !INTEGRATED_SERVER_CLASS.equals(server.getClass().getName())) {
            return false;
        }
        return isOpenCarpetPermissionEnabled();
    }

    private static boolean isOpenCarpetPermissionRule(String ruleName) {
        String name = ruleName.toLowerCase(Locale.ROOT);
        for (String candidate : OPEN_CARPET_PERMISSION_NAMES) {
            if (candidate.equals(name)) {
                return true;
            }
        }
        return false;
    }
}
