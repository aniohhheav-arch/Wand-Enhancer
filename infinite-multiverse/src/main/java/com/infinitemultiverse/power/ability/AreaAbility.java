package com.infinitemultiverse.power.ability;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Configurable burst around the user, optionally restricted to a forward cone (Don't Move, Flame Wave, Pacify...). */
public final class AreaAbility extends PowerAbility {
    private final Builder b;

    private AreaAbility(Builder builder) {
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
        Vec3 look = player.getLookAngle();
        int affected = 0;
        for (LivingEntity target : AbilityTargeting.hostilesInRadius(player, b.radius)) {
            Vec3 to = target.position().subtract(player.position());
            if (b.coneDot > -1.0 && to.normalize().dot(look) < b.coneDot) {
                continue;
            }
            affected++;
            if (b.damage > 0) {
                target.invulnerableTime = 0;
                target.hurt(b.magic ? player.damageSources().indirectMagic(player, player) : player.damageSources().playerAttack(player), b.damage);
            }
            if (b.fireSeconds > 0) {
                target.igniteForSeconds(b.fireSeconds);
            }
            if (b.knockback != 0) {
                Vec3 push = to.multiply(1, 0, 1);
                if (push.lengthSqr() < 1.0E-4) {
                    push = look;
                }
                push = push.normalize();
                target.knockback(Math.abs(b.knockback), b.knockback > 0 ? -push.x : push.x, b.knockback > 0 ? -push.z : push.z);
                target.setDeltaMovement(target.getDeltaMovement().add(0, 0.3, 0));
                target.hurtMarked = true;
            }
            if (b.freezeTicks > 0) {
                target.setTicksFrozen(Math.max(target.getTicksFrozen(), b.freezeTicks));
            }
            if (b.calm && target instanceof Mob mob) {
                mob.setTarget(null);
            }
            for (Supplier<MobEffectInstance> effect : b.effects) {
                target.addEffect(effect.get(), player);
            }
            MultiverseVfx.fx(ctx.level(), b.hitVfx, target.getBoundingBox().getCenter(), Vec3.ZERO, b.color);
        }
        MultiverseVfx.fx(ctx.level(), b.vfx, b.vfx == VfxIds.SHOCKWAVE ? player.position() : player.position().add(0, 1, 0),
                b.vfx == VfxIds.FROST || b.vfx == VfxIds.DOMAIN_OPEN ? new Vec3(b.radius, 0, 0) : look, b.color);
        if (b.sound != null) {
            MultiverseVfx.sound(ctx.level(), player.position(), b.sound, 1.0f, b.pitch);
        }
        if (b.shout != null) {
            MultiverseVfx.shout(ctx.level(), player.position(), Component.translatable(b.shout).withStyle(b.shoutStyle, ChatFormatting.BOLD), b.radius * 2);
        }
        if (b.selfDamage > 0f) {
            player.hurt(player.damageSources().magic(), b.selfDamage);
        }
        if (b.selfEffect != null) {
            player.addEffect(b.selfEffect.get());
        }
        return true;
    }

    public static final class Builder {
        private final MultiverseSystem system;
        private double radius = 8;
        private double coneDot = -1.0;
        private float damage;
        private boolean magic;
        private int fireSeconds;
        private double knockback;
        private int freezeTicks;
        private boolean calm;
        private float selfDamage;
        private int color = 0xFFFFFF;
        private ResourceLocation vfx = VfxIds.BURST;
        private ResourceLocation hitVfx = VfxIds.BURST;
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
        @Nullable
        private Supplier<MobEffectInstance> selfEffect;
        private final List<Supplier<MobEffectInstance>> effects = new ArrayList<>();

        private Builder(MultiverseSystem system) {
            this.system = system;
        }

        public Builder radius(double v) { radius = v; return this; }
        /** Restrict to a forward cone with the given cosine (0.5 ≈ 120°). */
        public Builder cone(double dot) { coneDot = dot; return this; }
        public Builder damage(float v) { damage = v; return this; }
        public Builder magic() { magic = true; return this; }
        public Builder fire(int seconds) { fireSeconds = seconds; return this; }
        /** Positive pushes away, negative pulls in. */
        public Builder knockback(double v) { knockback = v; return this; }
        public Builder freeze(int ticks) { freezeTicks = ticks; return this; }
        public Builder calm() { calm = true; return this; }
        public Builder selfDamage(float v) { selfDamage = v; return this; }
        public Builder color(int v) { color = v; return this; }
        public Builder vfx(ResourceLocation v) { vfx = v; return this; }
        public Builder hitVfx(ResourceLocation v) { hitVfx = v; return this; }
        public Builder sound(Holder<SoundEvent> v, float p) { sound = v; pitch = p; return this; }
        public Builder shout(String key, ChatFormatting style) { shout = key; shoutStyle = style; return this; }
        /** Gear-gated: the ability is unlocked for everyone but needs {@code test} (e.g. a full suit) to activate. */
        public Builder requires(java.util.function.Predicate<ServerPlayer> test, String messageKey) { requirement = test; requirementMessage = messageKey; return this; }
        public Builder effect(Supplier<MobEffectInstance> v) { effects.add(v); return this; }
        public Builder selfEffect(Supplier<MobEffectInstance> v) { selfEffect = v; return this; }

        public AreaAbility build() {
            return new AreaAbility(this);
        }
    }
}
