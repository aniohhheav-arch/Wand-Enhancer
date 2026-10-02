package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.world.TemporaryBlocks;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Hollow Technique: Purple. A cutscene of Blue and Red converging (synchronised with the client scene), then an
 * imaginary mass travels along your gaze for ~1 second, erasing every enemy it passes through.
 * Mastery IV ("200%"): bigger, longer, double damage — and it erases terrain too (when mobGriefing allows), which
 * regrows after a minute via {@link TemporaryBlocks}.
 */
public final class HollowPurpleAbility extends PowerAbility {
    private static final int LAUNCH = 52;
    private static final int TRAVEL = 22;
    private static final int COLOR = 0xA040FF;

    public HollowPurpleAbility() {
        super(MultiverseSystem.CURSED_TECHNIQUES);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        ServerLevel level = ctx.level();
        boolean full = masteryLevel(player, system()) >= 4;
        double length = full ? 64 : 48;
        double radius = full ? 3.4 : 2.2;
        float damage = 30f * mastery(player, system()) * (full ? 2f : 1f);
        Vec3 eye = player.getEyePosition();
        Vec3 dir = player.getLookAngle();
        Vec3 front = eye.add(dir.scale(1.3)).subtract(0, 0.15, 0);

        Cinematics.scene(level, SceneIds.HOLLOW_PURPLE, eye, dir.scale(length), COLOR, 84, player, (float) length, full ? Cinematics.EMPOWERED : 0);
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, LAUNCH + 6, 4, false, false));
        MultiverseVfx.sound(level, player.position(), ModSounds.STAND_SUMMON, 1.2f, 0.6f);
        StandScheduler.later(46, () -> MultiverseVfx.sound(level, player.position(), ModSounds.TIME_STOP, 1.6f, 0.6f));
        StandScheduler.later(LAUNCH - 2, () -> MultiverseVfx.shout(level, player.position(), Component.translatable("message.infinitemultiverse.shout.hollow_purple")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), 48));

        Set<UUID> hit = new HashSet<>();
        boolean erase = full && level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
        String group = "hollow_purple:" + player.getUUID() + ":" + level.getGameTime();
        StandScheduler.repeat(LAUNCH, 1, TRAVEL + 1, index -> {
            if (!player.isAlive() || player.level() != level) {
                return false;
            }
            Vec3 at = front.add(dir.scale(length * index / TRAVEL));
            if (index == 0) {
                MultiverseVfx.sound(level, at, ModSounds.SHOCKWAVE, 2f, 0.5f);
            }
            for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius + 1),
                    e -> e.getBoundingBox().getCenter().distanceTo(at) <= radius + e.getBbWidth() && AbilityTargeting.isHostileTarget(player, e))) {
                if (hit.add(target.getUUID())) {
                    target.invulnerableTime = 0;
                    target.hurt(player.damageSources().indirectMagic(player, player), damage);
                }
            }
            if (erase) {
                int r = (int) Math.ceil(radius);
                BlockPos c = BlockPos.containing(at);
                for (BlockPos pos : BlockPos.betweenClosed(c.offset(-r, -r, -r), c.offset(r, r, r))) {
                    if (pos.distSqr(c) <= radius * radius) {
                        TemporaryBlocks.place(level, pos, Blocks.AIR.defaultBlockState(), 1200, group, true);
                    }
                }
            }
            return true;
        });
        return true;
    }
}
