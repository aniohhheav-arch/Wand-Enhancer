package dev.riftverse.util;

import dev.riftverse.Riftverse;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

public final class Advancements {
    private Advancements() {}

    public static void award(ServerPlayer player, String id) {
        AdvancementHolder holder = player.server.getAdvancements().get(Riftverse.id(id));
        if (holder != null) player.getAdvancements().award(holder, "trigger");
    }
}
