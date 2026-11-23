package com.yh.carpet;

import carpet.patches.EntityPlayerMPFake;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps track of the fake players that were summoned through {@code /bot},
 * so {@code /bot clear} only removes those and never touches fake players
 * spawned with vanilla carpet's {@code /player}.
 */
public final class BotManager {
    private static final Set<String> SUMMONED_BOTS = ConcurrentHashMap.newKeySet();

    private BotManager() {
    }

    public static void onBotSpawned(String name) {
        SUMMONED_BOTS.add(name);
    }

    /**
     * Kills every still-online fake player that was summoned by {@code /bot}.
     *
     * @return the number of bots that were removed
     */
    public static int clearBots(MinecraftServer server) {
        int removed = 0;
        for (String name : SUMMONED_BOTS) {
            ServerPlayer player = server.getPlayerList().getPlayerByName(name);
            if (player instanceof EntityPlayerMPFake fake) {
                fake.kill(Component.literal("[carpet-YHaddition] cleared by /bot clear"));
                removed++;
            }
        }
        SUMMONED_BOTS.clear();
        return removed;
    }
}
