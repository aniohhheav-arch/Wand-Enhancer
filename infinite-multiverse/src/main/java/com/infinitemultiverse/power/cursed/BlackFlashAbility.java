package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Timing mechanic: arm the technique, then land a melee hit inside the window (0.35–0.65 s later). A hit inside the
 * window becomes a Black Flash (2.5x damage, black lightning); outside it is an ordinary hit and the chance is lost.
 */
public final class BlackFlashAbility extends PowerAbility {
    private static final int WINDOW_START = 7;
    private static final int WINDOW_END = 13;
    private static final int EXPIRE = 30;
    private static final float MULTIPLIER = 2.5f;
    private static final Map<UUID, Long> ARMED = new HashMap<>();

    public BlackFlashAbility() {
        super(MultiverseSystem.CURSED_TECHNIQUES);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ARMED.put(ctx.player().getUUID(), ctx.level().getGameTime());
        ctx.player().displayClientMessage(Component.translatable("message.infinitemultiverse.black_flash_armed").withStyle(ChatFormatting.DARK_RED), true);
        MultiverseVfx.fx(ctx.level(), VfxIds.AURA, ctx.player().position(), Vec3.ZERO, 0x2A0010);
        return true;
    }

    /** Called for every melee hit landed by a player. */
    public static void onMeleeHit(ServerPlayer attacker, LivingEntity target, LivingIncomingDamageEvent event) {
        Long armedAt = ARMED.remove(attacker.getUUID());
        if (armedAt == null) {
            return;
        }
        long elapsed = attacker.level().getGameTime() - armedAt;
        if (elapsed > EXPIRE) {
            return;
        }
        if (elapsed < WINDOW_START || elapsed > WINDOW_END) {
            attacker.displayClientMessage(Component.translatable("message.infinitemultiverse.black_flash_missed").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        event.setAmount(event.getAmount() * MULTIPLIER);
        Vec3 at = target.getBoundingBox().getCenter();
        MultiverseVfx.fx(attacker.serverLevel(), VfxIds.BOLT, at.add(0, 2.5, 0), new Vec3(0, -3, 0), 0xD0102A);
        MultiverseVfx.fx(attacker.serverLevel(), VfxIds.BURST, at, Vec3.ZERO, 0x100008);
        MultiverseVfx.sound(attacker.serverLevel(), at, ModSounds.STAND_HEAVY, 1.2f, 0.7f);
        MultiverseVfx.shout(attacker.serverLevel(), attacker.position(), Component.translatable("message.infinitemultiverse.shout.black_flash")
                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), 24.0);
    }
}
