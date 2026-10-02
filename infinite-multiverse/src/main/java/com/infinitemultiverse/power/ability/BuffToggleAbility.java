package com.infinitemultiverse.power.ability;

import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.ActivationType;
import com.infinitemultiverse.core.ability.DeactivationReason;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Toggled self-buff: keeps a set of effects refreshed, shows an aura, can grant flight, and can run a per-second
 * extra (heal, ignite nearby, static damage). Everything it grants is removed when it ends.
 */
public final class BuffToggleAbility extends PowerAbility {
    private record Buff(Holder<MobEffect> effect, int amplifier) {
    }

    private final List<Buff> buffs;
    private final int auraColor;
    private final boolean flight;
    @Nullable
    private final BiConsumer<AbilityContext, Integer> perSecond;
    @Nullable
    private final Predicate<ServerPlayer> requirement;
    private final net.minecraft.resources.ResourceLocation scene;
    @Nullable
    private final String requirementMessage;

    private BuffToggleAbility(Builder b) {
        super(b.system, ActivationType.TOGGLE, b.upkeep, b.requirement != null);
        this.buffs = List.copyOf(b.buffs);
        this.auraColor = b.auraColor;
        this.flight = b.flight;
        this.perSecond = b.perSecond;
        this.requirement = b.requirement;
        this.scene = b.scene;
        this.requirementMessage = b.requirementMessage;
    }

    public static Builder builder(MultiverseSystem system, float upkeepPerSecond) {
        return new Builder(system, upkeepPerSecond);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        if (requirement != null && !requirement.test(ctx.player())) {
            AbilityManager.deny(ctx.player(), Component.translatable(requirementMessage));
            return false;
        }
        if (flight) {
            Flight.grant(ctx.player(), id());
        }
        MultiverseVfx.fx(ctx.level(), VfxIds.AURA, ctx.player().position(), Vec3.ZERO, auraColor);
        com.infinitemultiverse.core.cinematic.Cinematics.attached(ctx.level(), scene, ctx.player(), auraColor, 24, 1f);
        return true;
    }

    @Override
    public void tickActive(AbilityContext ctx, int activeTicks) {
        ServerPlayer player = ctx.player();
        if (requirement != null && !requirement.test(player)) {
            AbilityManager.deactivate(player, ctx.data(), this, DeactivationReason.MANUAL);
            return;
        }
        if (activeTicks % 10 == 1) {
            for (Buff buff : buffs) {
                player.addEffect(new MobEffectInstance(buff.effect, 40, buff.amplifier, false, false, true));
            }
        }
        if (activeTicks % 20 == 0) {
            com.infinitemultiverse.core.cinematic.Cinematics.attached(ctx.level(), scene, player, auraColor, 24, 1f);
        }
        if (perSecond != null && activeTicks % 20 == 0) {
            perSecond.accept(ctx, activeTicks / 20);
        }
    }

    @Override
    public void onDeactivate(AbilityContext ctx, DeactivationReason reason) {
        ServerPlayer player = ctx.player();
        for (Buff buff : buffs) {
            player.removeEffect(buff.effect);
        }
        if (flight) {
            Flight.revoke(player, id());
            ctx.data().grantFallProtection(100);
        }
    }

    public static final class Builder {
        private final MultiverseSystem system;
        private final float upkeep;
        private final List<Buff> buffs = new ArrayList<>();
        private int auraColor = 0xFFFFFF;
        private net.minecraft.resources.ResourceLocation scene = com.infinitemultiverse.core.cinematic.SceneIds.AURA;
        private boolean flight;
        @Nullable
        private BiConsumer<AbilityContext, Integer> perSecond;
        @Nullable
        private Predicate<ServerPlayer> requirement;
        @Nullable
        private String requirementMessage;

        private Builder(MultiverseSystem system, float upkeep) {
            this.system = system;
            this.upkeep = upkeep;
        }

        public Builder buff(Holder<MobEffect> effect, int amplifier) { buffs.add(new Buff(effect, amplifier)); return this; }
        public Builder aura(int color) { auraColor = color; return this; }
        /** Rendered aura scene (see SceneIds.AURA_*). */
        public Builder scene(net.minecraft.resources.ResourceLocation v) { scene = v; return this; }
        public Builder flight() { flight = true; return this; }
        public Builder perSecond(BiConsumer<AbilityContext, Integer> action) { perSecond = action; return this; }
        public Builder requires(Predicate<ServerPlayer> test, String messageKey) { requirement = test; requirementMessage = messageKey; return this; }

        public BuffToggleAbility build() {
            return new BuffToggleAbility(this);
        }
    }
}
