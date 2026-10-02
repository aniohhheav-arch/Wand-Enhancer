package com.infinitemultiverse.power.ability;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.Beams;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import com.infinitemultiverse.stand.StandScheduler;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Configurable line attack used by many powers (Red, Hollow Purple, Dismantle, heat vision, repulsors, the
 * eldritch whip...). Stops at solid blocks; never modifies terrain.
 */
public final class BeamAbility extends PowerAbility {
    private final Builder b;

    private BeamAbility(Builder builder) {
        super(builder.system, com.infinitemultiverse.core.ability.ActivationType.INSTANT, 0f, builder.requirement != null);
        this.b = builder;
    }

    public static Builder builder(MultiverseSystem system) {
        return new Builder(system);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        if (b.requirement != null && !b.requirement.test(player)) {
            com.infinitemultiverse.core.ability.AbilityManager.deny(player, Component.translatable(b.requirementMessage));
            return false;
        }
        if (b.selfDamage > 0f && player.getHealth() <= b.selfDamage + 1f) {
            com.infinitemultiverse.core.ability.AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.too_hurt"));
            return false;
        }
        if (b.shout != null) {
            MultiverseVfx.shout(ctx.level(), player.position(), Component.translatable(b.shout).withStyle(b.shoutStyle, ChatFormatting.BOLD), 32.0);
        }
        if (b.cinematic != null) {
            int flags = com.infinitemultiverse.power.mastery.Mastery.sceneFlags(player, system());
            com.infinitemultiverse.core.cinematic.Cinematics.scene(ctx.level(), b.cinematic, player.getEyePosition(), player.getLookAngle().scale(b.length), b.color,
                    b.cinematicDuration, player, (float) b.length, flags);
            player.addEffect(new MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, b.cinematicDelay + 10, 3, false, false));
            StandScheduler.later(b.cinematicDelay, () -> {
                if (player.isAlive() && player.level() == ctx.level()) {
                    fire(player, true);
                }
            });
        } else if (b.chargeTicks > 0) {
            com.infinitemultiverse.core.cinematic.Cinematics.attached(ctx.level(), com.infinitemultiverse.core.cinematic.SceneIds.CHARGE, player, b.color, b.chargeTicks, (float) b.radius * 0.6f);
            StandScheduler.later(b.chargeTicks, () -> {
                if (player.isAlive() && player.level() == ctx.level()) {
                    fire(player, false);
                }
            });
        } else {
            fire(player, false);
        }
        if (b.selfDamage > 0f) {
            player.hurt(player.damageSources().magic(), b.selfDamage);
        }
        return true;
    }

    private void fire(ServerPlayer player, boolean quiet) {
        ServerLevel level = player.serverLevel();
        Beams.Result result = Beams.trace(player, b.length, b.radius, b.maxHits);
        Vec3 muzzle = result.start().add(player.getLookAngle().scale(0.8)).subtract(0, 0.2, 0);
        Vec3 span = result.vector().subtract(player.getLookAngle().scale(0.8));
        int flags = com.infinitemultiverse.power.mastery.Mastery.sceneFlags(player, system());
        float width = (float) b.radius * ((flags & com.infinitemultiverse.core.cinematic.Cinematics.EMPOWERED) != 0 ? 0.75f : 0.5f);
        if (!quiet) {
            com.infinitemultiverse.core.cinematic.Cinematics.scene(level, b.scene, muzzle, span, b.color, b.heavy ? 22 : 14, player, width, flags);
        }
        if (b.heavy) {
            MultiverseVfx.fx(level, VfxIds.BEAM_HEAVY, muzzle, span, b.color);
        }
        if (b.sound != null) {
            MultiverseVfx.sound(level, player.position(), b.sound, 1.0f, b.pitch);
        }
        Vec3 dir = player.getLookAngle();
        for (LivingEntity target : result.hits()) {
            DamageSource source = b.magic ? player.damageSources().indirectMagic(player, player) : player.damageSources().playerAttack(player);
            target.invulnerableTime = 0;
            float damage = (b.damage + (float) (b.maxHealthFraction * target.getMaxHealth())) * mastery(player, system());
            target.hurt(source, damage);
            if (b.fireSeconds > 0) {
                target.igniteForSeconds(b.fireSeconds);
            }
            if (b.knockback > 0) {
                target.knockback(b.knockback, -dir.x, -dir.z);
                target.setDeltaMovement(target.getDeltaMovement().add(0, 0.25, 0));
                target.hurtMarked = true;
            }
            if (b.pull > 0) {
                Vec3 toOwner = player.position().subtract(target.position()).normalize().scale(b.pull);
                target.setDeltaMovement(toOwner.add(0, 0.3, 0));
                target.hurtMarked = true;
            }
            for (Supplier<MobEffectInstance> effect : b.effects) {
                target.addEffect(effect.get(), player);
            }
            if (b.lightning) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(target.position());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
            }
            MultiverseVfx.fx(level, b.hitVfx, target.getBoundingBox().getCenter(), dir, b.color);
            com.infinitemultiverse.core.cinematic.Cinematics.scene(level, b.hitVfx == VfxIds.SLASH ? com.infinitemultiverse.core.cinematic.SceneIds.IMPACT_SLASH
                    : com.infinitemultiverse.core.cinematic.SceneIds.IMPACT, target.getBoundingBox().getCenter(), dir, b.color, 8, null, target.getBbWidth() + 0.3f);
        }
        if (b.endBurst) {
            MultiverseVfx.fx(level, VfxIds.BURST, result.end(), Vec3.ZERO, b.color);
        }
    }

    public static final class Builder {
        private final MultiverseSystem system;
        private double length = 24;
        private double radius = 0.8;
        private int maxHits = 1;
        private float damage = 6f;
        private double maxHealthFraction;
        private boolean magic;
        private int fireSeconds;
        private double knockback;
        private double pull;
        private boolean heavy;
        private boolean lightning;
        private boolean endBurst;
        private float selfDamage;
        private int chargeTicks;
        private int color = 0xFFFFFF;
        private ResourceLocation hitVfx = VfxIds.BURST;
        private ResourceLocation scene = com.infinitemultiverse.core.cinematic.SceneIds.BEAM;
        @Nullable
        private ResourceLocation cinematic;
        private int cinematicDelay;
        private int cinematicDuration;
        @Nullable
        private Holder<SoundEvent> sound;
        private float pitch = 1f;
        @Nullable
        private String shout;
        private ChatFormatting shoutStyle = ChatFormatting.WHITE;
        @Nullable
        private java.util.function.Predicate<ServerPlayer> requirement;
        @Nullable
        private String requirementMessage;
        private final List<Supplier<MobEffectInstance>> effects = new ArrayList<>();

        private Builder(MultiverseSystem system) {
            this.system = system;
        }

        public Builder length(double v) { length = v; return this; }
        public Builder radius(double v) { radius = v; return this; }
        public Builder hits(int v) { maxHits = v; return this; }
        public Builder damage(float v) { damage = v; return this; }
        public Builder maxHealthFraction(double v) { maxHealthFraction = v; return this; }
        public Builder magic() { magic = true; return this; }
        public Builder fire(int seconds) { fireSeconds = seconds; return this; }
        public Builder knockback(double v) { knockback = v; return this; }
        public Builder pull(double v) { pull = v; return this; }
        public Builder heavy() { heavy = true; return this; }
        public Builder lightning() { lightning = true; return this; }
        public Builder endBurst() { endBurst = true; return this; }
        public Builder selfDamage(float v) { selfDamage = v; return this; }
        public Builder charge(int ticks) { chargeTicks = ticks; return this; }
        public Builder color(int v) { color = v; return this; }
        public Builder hitVfx(ResourceLocation v) { hitVfx = v; return this; }
        /** Rendered scene for the beam (see SceneIds.BEAM_*). */
        public Builder scene(ResourceLocation v) { scene = v; return this; }
        /** Plays a cutscene scene first and fires {@code fireDelay} ticks into it (the caster is shielded meanwhile). */
        public Builder cinematic(ResourceLocation v, int fireDelay, int duration) { cinematic = v; cinematicDelay = fireDelay; cinematicDuration = duration; return this; }
        public Builder sound(Holder<SoundEvent> v, float p) { sound = v; pitch = p; return this; }
        public Builder shout(String key, ChatFormatting style) { shout = key; shoutStyle = style; return this; }
        /** Gear-gated: the ability is unlocked for everyone but needs {@code test} (e.g. a full suit) to activate. */
        public Builder requires(java.util.function.Predicate<ServerPlayer> test, String messageKey) { requirement = test; requirementMessage = messageKey; return this; }
        public Builder effect(Supplier<MobEffectInstance> v) { effects.add(v); return this; }

        public BeamAbility build() {
            return new BeamAbility(this);
        }
    }
}
