package dev.mysticarts.event;

import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Cloak;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.PowerManager;
import dev.mysticarts.power.Source;
import dev.mysticarts.power.Sustained;
import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.power.service.TimeHistory;
import dev.mysticarts.power.service.TimeLoops;
import dev.mysticarts.power.spells.MysticSpells;
import dev.mysticarts.power.spells.RealitySpells;
import dev.mysticarts.power.spells.SoulSpells;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.BlockHistory;
import dev.mysticarts.world.MirrorDimension;
import dev.mysticarts.world.Scheduler;
import dev.mysticarts.world.TemporaryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Server-side game hooks for the power system. */
public final class CommonEvents {
    private CommonEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener(CommonEvents::onPlayerTick);
        bus.addListener(CommonEvents::onLevelTick);
        bus.addListener((ServerTickEvent.Post e) -> Scheduler.tick(e.getServer()));
        bus.addListener((ServerStoppingEvent e) -> {
            Scheduler.stop(e.getServer());
            EntityMarks.clearAll();
            BlockHistory.clearAll();
            TimeHistory.clear();
            TimeLoops.clear();
            Sustained.clear();
        });
        bus.addListener(EventPriority.HIGH, CommonEvents::onEntityTick);
        bus.addListener(EventPriority.HIGH, CommonEvents::onIncomingDamage);
        bus.addListener(CommonEvents::onDamaged);
        bus.addListener(CommonEvents::onProjectileImpact);
        bus.addListener(CommonEvents::onDeath);
        bus.addListener(CommonEvents::onChangeTarget);
        bus.addListener(CommonEvents::onBreak);
        bus.addListener(CommonEvents::onPlace);
        bus.addListener(CommonEvents::onExplosion);
        bus.addListener(CommonEvents::onJoin);
        bus.addListener(CommonEvents::onLogin);
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) resync(sp);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) resync(sp);
        });
        bus.addListener((PlayerEvent.StartTracking e) -> {
            if (e.getTarget() instanceof ServerPlayer tracked && e.getEntity() instanceof ServerPlayer viewer) {
                PacketDistributor.sendToPlayer(viewer, PowerManager.casterState(tracked, PowerManager.data(tracked)));
            }
        });
    }

    private static void resync(ServerPlayer sp) {
        PowerData d = PowerManager.data(sp);
        d.dirty = true;
        d.casterDirty = true;
        PowerManager.sync(sp, d);
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PowerManager.tick(sp);
        TimeHistory.record(sp);
        MirrorDimension.tick(sp, PowerManager.data(sp));
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        EntityMarks.tick(level);
        TimeLoops.tick(level);
        Sustained.worldTick(level);
        TemporaryBlocks.get(level).tick(level);
        if (level.getGameTime() % 200 == 0) BlockHistory.trim(level);
    }

    private static void onEntityTick(EntityTickEvent.Pre e) {
        Entity entity = e.getEntity();
        if (entity.level().isClientSide || entity instanceof Player) return;
        if (EntityMarks.suppressTick(entity)) e.setCanceled(true);
    }

    // ============================================================================================ combat

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource src = e.getSource();

        if (EntityMarks.has(victim, MarkKind.SEPARATED)) e.setAmount(e.getAmount() * 1.5f);

        // supercharged melee (Power Stone)
        if (src.getDirectEntity() instanceof ServerPlayer attacker && src.getEntity() == attacker) {
            PowerData ad = PowerManager.data(attacker);
            if (ad.superchargeHits > 0) {
                ad.superchargeHits--;
                ad.dirty = true;
                e.setAmount(e.getAmount() + 10f);
                Vec3 push = victim.position().subtract(attacker.position()).normalize();
                victim.setDeltaMovement(victim.getDeltaMovement().add(push.scale(1.6)).add(0, 0.5, 0));
                victim.hurtMarked = true;
                Fx.send(attacker.serverLevel(), FxKind.IMPACT, victim.getBoundingBox().getCenter(), Vec3.ZERO, Source.POWER.color, 1.6f, 0, -1);
                Fx.sound(attacker, MaSounds.BLAST_IMPACT.get(), 1f, 0.6f);
                if (ad.superchargeHits == 0) ad.casterDirty = true;
            }
        }

        if (!(victim instanceof ServerPlayer player)) return;
        PowerData d = PowerManager.data(player);
        if (d.astralFormTicks > 0 && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            return;
        }
        if (d.absorbTicks > 0 && !src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            float energy = e.getAmount() * 2f;
            d.mystic = Math.min(d.maxMystic, d.mystic + energy);
            d.dirty = true;
            Fx.send(player.serverLevel(), FxKind.ABSORB, player.position().add(0, 1, 0), Vec3.ZERO, Ability.ENERGY_ABSORPTION.color(), 1.5f, 10, player.getId());
            Fx.sound(player, MaSounds.ABSORB.get(), 0.7f, 1.4f);
            e.setCanceled(true);
            return;
        }
        if (d.kineticTicks > 0) {
            d.kineticStored += e.getAmount() * 0.8f;
            e.setAmount(e.getAmount() * 0.2f);
            Fx.send(player.serverLevel(), FxKind.ABSORB, player.position().add(0, 1, 0), Vec3.ZERO, Source.POWER.color, 1.2f, 10, player.getId());
        }
        if (d.shieldMode == 1 && blockedByShield(player, src)) {
            float absorbed = e.getAmount() * 0.75f;
            e.setAmount(e.getAmount() - absorbed);
            if (!PowerManager.unlimited(player)) d.mystic = Math.max(0, d.mystic - absorbed * 0.8f);
            d.dirty = true;
            Vec3 hit = player.getEyePosition().add(player.getLookAngle().scale(0.7)).subtract(0, 0.3, 0);
            Fx.send(player.serverLevel(), FxKind.SHIELD_HIT, hit, player.getLookAngle(), Ability.ELDRITCH_SHIELD.color(), Math.min(2f, absorbed * 0.3f + 0.6f), 0, player.getId());
            Fx.sound(player, MaSounds.SHIELD_IMPACT.get(), 1f, 1f);
        }
    }

    /** The buckler protects a 140° arc in front of the caster. */
    private static boolean blockedByShield(Player player, DamageSource src) {
        Vec3 from = src.getSourcePosition();
        if (from == null) return false;
        Vec3 to = from.subtract(player.getEyePosition());
        Vec3 look = player.getLookAngle();
        return new Vec3(to.x, 0, to.z).normalize().dot(new Vec3(look.x, 0, look.z).normalize()) > -0.17;
    }

    private static void onDamaged(LivingDamageEvent.Post e) {
        if (e.getEntity() instanceof ServerPlayer player && e.getSource().getEntity() instanceof LivingEntity attacker && attacker != player) {
            Cloak.onWearerHurt(player, attacker);
        }
        if (e.getSource().getEntity() instanceof ServerPlayer attacker && !InfinityGauntletItem.held(attacker).isEmpty()) {
            PowerManager.gainUltimate(PowerManager.data(attacker), e.getNewDamage() * 0.4f);
        }
    }

    private static void onProjectileImpact(ProjectileImpactEvent e) {
        Projectile p = e.getProjectile();
        if (p.level().isClientSide || !(e.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof ServerPlayer player)) return;
        if (p.getOwner() == player) return;
        PowerData d = PowerManager.data(player);
        boolean shielded = d.shieldMode == 1 && p.getDeltaMovement().normalize().dot(player.getLookAngle()) < -0.2;
        if (!shielded && d.astralFormTicks <= 0) return;
        e.setCanceled(true);
        Entity shooter = p.getOwner();
        Vec3 back = shooter != null ? shooter.getEyePosition().subtract(p.position()).normalize() : p.getDeltaMovement().normalize().reverse();
        p.setDeltaMovement(back.scale(Math.max(0.8, p.getDeltaMovement().length())));
        p.setOwner(player);
        p.hurtMarked = true;
        if (shielded) {
            Fx.send(player.serverLevel(), FxKind.SHIELD_HIT, p.position(), player.getLookAngle(), Ability.ELDRITCH_SHIELD.color(), 1f, 0, player.getId());
            Fx.sound(player, MaSounds.SHIELD_IMPACT.get(), 1f, 1.2f);
        }
    }

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer killer) || e.getEntity() == killer) return;
        int stones = InfinityGauntletItem.heldStones(killer);
        PowerData d = PowerManager.data(killer);
        if ((stones & Source.SOUL.bit()) != 0 && d.souls < SoulSpells.MAX_SOULS) {
            d.souls++;
            d.dirty = true;
            Fx.send(killer.serverLevel(), FxKind.SOUL_RIP, killer.position().add(0, 1, 0), Vec3.ZERO, Source.SOUL.color, 0, 0, e.getEntity().getId());
        }
        if (stones != 0) PowerManager.gainUltimate(d, 4f);
    }

    private static void onChangeTarget(LivingChangeTargetEvent e) {
        LivingEntity mob = e.getEntity();
        LivingEntity target = e.getNewAboutToBeSetTarget();
        if (target == null || mob.level().isClientSide) return;
        if (target instanceof ServerPlayer player) {
            if (EntityMarks.has(mob, MarkKind.PACIFIED) || PowerManager.data(player).astralFormTicks > 0) {
                e.setCanceled(true);
                return;
            }
            EntityMarks.Mark control = EntityMarks.get(mob, MarkKind.CONTROLLED);
            if (control != null) e.setCanceled(true);
        }
    }

    // ============================================================================================ world history

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getLevel() instanceof ServerLevel level) BlockHistory.record(level, e.getPos(), e.getState());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getLevel() instanceof ServerLevel level && !TemporaryBlocks.get(level).tracks(e.getPos())) {
            BlockHistory.record(level, e.getPos(), e.getBlockSnapshot().getState());
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        for (BlockPos p : e.getAffectedBlocks()) {
            var s = level.getBlockState(p);
            if (!s.isAir()) BlockHistory.record(level, p, s);
        }
    }

    // ============================================================================================ lifecycle

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel() instanceof ServerLevel level && e.getEntity() instanceof Mob) {
            RealitySpells.checkTransformed(level, e.getEntity());
        }
    }

    /** Recovers players who logged off (or crashed) mid-projection or inside the mirror. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PowerData d = PowerManager.data(sp);
        if (d.astralReturn != null) MysticSpells.endAstral(sp, d);
        resync(sp);
    }
}
