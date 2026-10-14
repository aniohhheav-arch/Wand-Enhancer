package com.stickyfingers.ability;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.StandAnim;
import com.stickyfingers.entity.StandEntity;
import com.stickyfingers.network.StandSyncPayload;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Summoning, energy, hidden-state processing and client sync for every Stand user. */
public final class StandManager {
    private static final Map<UUID, StandEntity> STANDS = new HashMap<>();

    @Nullable
    public static StandEntity stand(ServerPlayer player) {
        StandEntity s = STANDS.get(player.getUUID());
        if (s != null && (s.isRemoved() || s.level() != player.level())) {
            s.discard();
            STANDS.remove(player.getUUID());
            return null;
        }
        return s;
    }

    public static void summon(ServerPlayer player) {
        StandData data = StandData.get(player);
        if (!data.hasStand() || data.isHidden()) return;
        StandEntity existing = stand(player);
        if (existing != null && !existing.isDismissing()) return;
        if (existing != null) existing.discard();
        StandEntity s = StandEntity.create(player);
        player.level().addFreshEntity(s);
        STANDS.put(player.getUUID(), s);
        data.setSummoned(true);
        ServerLevel level = player.serverLevel();
        Fx.sound(level, s.position(), ModSounds.STAND_SUMMON.get(), 1f, 1f);
        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), s.position().add(0, 1, 0), 16, 0.4, 0.08);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), s.position().add(0, 1, 0), 24, 0.5, 0.03);
    }

    public static void dismiss(ServerPlayer player) {
        StandEntity s = stand(player);
        if (s != null) {
            s.dismiss();
            Fx.sound(player.serverLevel(), s.position(), ModSounds.STAND_DISMISS.get(), 1f, 1f);
            Fx.particles(player.serverLevel(), ModParticles.VOID_MOTE.get(), s.position().add(0, 1, 0), 20, 0.5, 0.03);
        }
        STANDS.remove(player.getUUID());
        StandData.get(player).setSummoned(false);
    }

    public static void toggle(ServerPlayer player) {
        if (stand(player) != null) dismiss(player); else summon(player);
    }

    /** Summon if needed and return the Stand (abilities pose it). */
    @Nullable
    public static StandEntity ensure(ServerPlayer player) {
        if (stand(player) == null) summon(player);
        return stand(player);
    }

    public static void animate(ServerPlayer player, StandAnim anim) {
        StandEntity s = ensure(player);
        if (s != null) s.play(anim);
    }

    public static void select(ServerPlayer player, int ability) {
        StandData.get(player).setSelected(AbilityType.byId(ability));
    }

    public static void tick(ServerPlayer player) {
        StandData data = StandData.get(player);
        if (!data.isInitialized()) {
            data.setInitialized();
            if (SFConfig.GRANT_BY_DEFAULT.get()) data.setHasStand(true);
        }
        if (!data.hasStand()) {
            if (stand(player) != null) dismiss(player);
        } else {
            float regen = (float) (SFConfig.ENERGY_REGEN.get() / 20.0);
            if (player.hasEffect(ModEffects.ZIPPER_BODY)) regen *= 0.25f;
            data.setEnergy(data.energy() + regen);
            if (data.isSummoned() && stand(player) == null && player.isAlive()) summon(player);
            Hiding.tick(player, data);
        }
        Scheduler.tick(player);
        if (data.consumeDirty() || player.tickCount % 20 == 0) sync(player);
    }

    public static void sync(ServerPlayer player) {
        StandData data = StandData.get(player);
        long now = player.level().getGameTime();
        int[] cds = new int[AbilityType.VALUES.length];
        for (AbilityType t : AbilityType.VALUES) cds[t.ordinal()] = data.cooldownRemaining(t, now);
        PacketDistributor.sendToPlayer(player, new StandSyncPayload(data.hasStand(), data.isSummoned(), data.energy(),
                data.selected().ordinal(), data.isHidden(), data.exitZipper(), cds));
    }

    public static void cleanup(ServerPlayer player) {
        if (player.getPersistentData().getBoolean("sf_inside")) player.setInvisible(false);
        player.getPersistentData().remove("sf_inside");
        Scheduler.cancelAll(player);
        Hiding.forceEmerge(player);
        StandEntity s = STANDS.remove(player.getUUID());
        if (s != null) s.discard();
    }

    private StandManager() {}
}
