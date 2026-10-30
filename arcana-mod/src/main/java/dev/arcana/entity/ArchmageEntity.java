package dev.arcana.entity;

import dev.arcana.ArcanaConfig;
import dev.arcana.magic.EffectManager;
import dev.arcana.magic.Magic;
import dev.arcana.magic.School;
import dev.arcana.net.Net;
import dev.arcana.registry.ModComponents;
import dev.arcana.registry.ModItems;
import java.util.List;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Hollow Archmage: a three-phase boss with telegraphed attacks, a boss bar and a drawn-out cinematic death.
 * Phase 1 (&gt;66%): missiles and eruptions. Phase 2: adds gravity pulses, blinking and vex summons.
 * Phase 3 (&lt;33%): relentless barrages and overlapping eruptions.
 */
public class ArchmageEntity extends Monster implements GeoEntity {
    private static final int PURPLE = 0xB10DFF;
    private static final DustParticleOptions DUST = School.dust(PURPLE, 1.5f);
    private static final int DEATH_TICKS = 110;

    private final ServerBossEvent bossEvent = new ServerBossEvent(Component.translatable("entity.arcana.hollow_archmage"),
        BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.arcana.archmage.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.arcana.archmage.walk");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("animation.arcana.archmage.death");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.arcana.archmage.cast");
    private static final RawAnimation SUMMON = RawAnimation.begin().thenPlay("animation.arcana.archmage.summon");
    private static final RawAnimation PHASE = RawAnimation.begin().thenPlay("animation.arcana.archmage.phase");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private int phase = 1;
    private int transition;
    private DamageSource deathSource;

    public ArchmageEntity(EntityType<? extends ArchmageEntity> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        this.setPersistenceRequired();
        ItemStack wand = new ItemStack(ModItems.WANDS.get(School.ARCANE).get());
        wand.set(ModComponents.STAGE.get(), 2);
        this.setItemSlot(EquipmentSlot.MAINHAND, wand);
        this.setDropChance(EquipmentSlot.MAINHAND, 0f);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
            .add(Attributes.MAX_HEALTH, 400)
            .add(Attributes.ARMOR, 6)
            .add(Attributes.MOVEMENT_SPEED, 0.28)
            .add(Attributes.FOLLOW_RANGE, 40)
            .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
            .add(Attributes.ATTACK_DAMAGE, 8);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new CastGoal(this));
        goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 30f));
        goalSelector.addGoal(6, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new HurtByTargetGoal(this));
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        double hp = ArcanaConfig.BOSS_HEALTH.get();
        if (getAttribute(Attributes.MAX_HEALTH).getBaseValue() != hp) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(hp);
            setHealth((float) hp);
        }
    }

    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean fireImmune() { return true; }

    // ------------------------------------------------------------------ animation

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "move", 6, state -> {
            if (isDeadOrDying()) return state.setAndContinue(DEATH);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
        controllers.add(new AnimationController<>(this, "action", 2, state -> software.bernie.geckolib.animation.PlayState.STOP)
            .triggerableAnim("cast", CAST).triggerableAnim("summon", SUMMON).triggerableAnim("phase", PHASE));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    // ------------------------------------------------------------------ boss bar & phases

    @Override
    public void startSeenByPlayer(ServerPlayer p) {
        super.startSeenByPlayer(p);
        bossEvent.addPlayer(p);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer p) {
        super.stopSeenByPlayer(p);
        bossEvent.removePlayer(p);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void setCustomName(Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        bossEvent.setProgress(getHealth() / getMaxHealth());
        float f = getHealth() / getMaxHealth();
        int p = f > 0.66f ? 1 : f > 0.33f ? 2 : 3;
        if (p > phase) enterPhase(p);
        if (transition > 0) {
            transition--;
            ((ServerLevel) level()).sendParticles(DUST, getX(), getY() + 1.2, getZ(), 10, 0.8, 1, 0.8, 0.1);
        }
        if (tickCount % 40 == 0 && getTarget() == null) heal(2f);
    }

    private void enterPhase(int p) {
        phase = p;
        transition = 50;
        triggerAnim("action", "phase");
        if (p == 3) SpellFxEntity.spawn((ServerLevel) level(), FxKind.RIFT, position(), 3.2f, 70, SpellFxEntity.EASE);
        ServerLevel sl = (ServerLevel) level();
        sl.playSound(null, blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 1.5f, 0.6f + 0.2f * p);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1, getZ(), 1, 0, 0, 0, 0);
        Net.cinematicNear(sl, position(), PURPLE, 30, 0);
        for (Player pl : playersNear(30)) {
            pl.displayClientMessage(Component.translatable("boss.arcana.phase." + p), true);
            Vec3 kb = pl.position().subtract(position()).multiply(1, 0, 1).normalize().scale(1.4);
            Magic.push(pl, new Vec3(kb.x, 0.5, kb.z));
        }
        if (p >= 2) summonVexes(3);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (transition > 0 && !source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        return super.hurt(source, amount);
    }

    // ------------------------------------------------------------------ attacks

    List<Player> playersNear(double r) {
        return level().getEntitiesOfClass(Player.class, new AABB(position(), position()).inflate(r), pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator());
    }

    private void damage(Player p, float dmg) {
        p.hurt(damageSources().indirectMagic(this, this), dmg * (level().getDifficulty().getId() >= 3 ? 1.25f : 1f));
    }

    void performAttack(LivingEntity target) {
        int roll = random.nextInt(100);
        if (phase == 1) {
            if (roll < 55) missiles(4);
            else eruptions(false);
        } else if (phase == 2) {
            if (roll < 25) missiles(6);
            else if (roll < 50) eruptions(false);
            else if (roll < 70) voidPulse();
            else if (roll < 85) blink(target);
            else summonVexes(2);
        } else {
            if (roll < 35) missiles(10);
            else if (roll < 65) eruptions(true);
            else if (roll < 80) voidPulse();
            else blink(target);
        }
    }

    private void missiles(int n) {
        ServerLevel sl = (ServerLevel) level();
        triggerAnim("action", "cast");
        sl.playSound(null, blockPosition(), SoundEvents.EVOKER_PREPARE_ATTACK, SoundSource.HOSTILE, 1.5f, 0.7f);
        EffectManager.run(sl, n * 4 + 1, age -> {
            if (!isAlive()) return false;
            if (age % 4 != 0) return true;
            List<Player> ps = playersNear(26);
            if (ps.isEmpty()) return false;
            Player p = ps.get(random.nextInt(ps.size()));
            Vec3 from = getEyePosition().add(0, 0.3, 0);
            Vec3 to = p.position().add(0, 1, 0);
            Magic.line(sl, DUST, from, to, 0.4);
            Magic.line(sl, ParticleTypes.WITCH, from, to, 1.0);
            Magic.snd(sl, from, SoundEvents.ILLUSIONER_CAST_SPELL, 1f, 1.3f);
            damage(p, 5f);
            return true;
        }, null);
    }

    private void eruptions(boolean extra) {
        ServerLevel sl = (ServerLevel) level();
        triggerAnim("action", "cast");
        List<Vec3> spots = new java.util.ArrayList<>();
        for (Player p : playersNear(26)) spots.add(p.position());
        if (extra) for (int i = 0; i < 4; i++) spots.add(position().add(random.nextInt(17) - 8, 0, random.nextInt(17) - 8));
        for (Vec3 s : spots) {
            Vec3 g = new Vec3(s.x, Magic.groundY(sl, s.x, s.y, s.z), s.z);
            EffectManager.run(sl, 26, age -> {
                Magic.ring(sl, DUST, g, 3.2 * (1 - age / 30.0) + 0.4, 28, 0.1);
                Magic.ring(sl, DUST, g, 3.2, 28, 0.1);
                return true;
            }, () -> {
                for (int h = 0; h < 12; h++) sl.sendParticles(h % 2 == 0 ? DUST : ParticleTypes.END_ROD, g.x, g.y + h * 0.7, g.z, 4, 0.5, 0.1, 0.5, 0.02);
                sl.sendParticles(ParticleTypes.EXPLOSION, g.x, g.y + 0.5, g.z, 1, 0, 0, 0, 0);
                Magic.snd(sl, g, SoundEvents.GENERIC_EXPLODE.value(), 0.8f, 1.2f);
                for (Player p : sl.getEntitiesOfClass(Player.class, new AABB(g, g).inflate(3.4), pl -> pl.isAlive() && !pl.isCreative() && !pl.isSpectator())) {
                    damage(p, 10f);
                    Magic.push(p, new Vec3(0, 0.7, 0));
                }
            });
        }
    }

    private void voidPulse() {
        ServerLevel sl = (ServerLevel) level();
        triggerAnim("action", "cast");
        Vec3 c = position().add(0, 1, 0);
        sl.playSound(null, blockPosition(), SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 1.5f, 0.6f);
        EffectManager.run(sl, 30, age -> {
            if (!isAlive()) return false;
            Magic.ring(sl, DUST, c, 9 * (1 - age / 32.0) + 0.5, 40, 0);
            sl.sendParticles(ParticleTypes.PORTAL, c.x, c.y, c.z, 12, 3, 1, 3, 0.8);
            for (Player p : playersNear(12)) Magic.pull(p, c, 0.28);
            return true;
        }, () -> {
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
            for (Player p : playersNear(9)) {
                damage(p, 9f);
                Vec3 kb = p.position().subtract(c).multiply(1, 0, 1).normalize().scale(1.6);
                Magic.push(p, new Vec3(kb.x, 0.6, kb.z));
            }
        });
    }

    private void blink(LivingEntity target) {
        if (target == null) return;
        ServerLevel sl = (ServerLevel) level();
        sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 40, 0.4, 1, 0.4, 0.6);
        Vec3 back = target.getLookAngle().multiply(1, 0, 1).normalize().scale(-4);
        Vec3 dest = target.position().add(back);
        if (Magic.canStand(sl, this, dest)) {
            teleportTo(dest.x, dest.y, dest.z);
            sl.sendParticles(ParticleTypes.PORTAL, getX(), getY() + 1, getZ(), 40, 0.4, 1, 0.4, 0.6);
            sl.playSound(null, blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.2f, 0.7f);
        }
    }

    private void summonVexes(int n) {
        ServerLevel sl = (ServerLevel) level();
        triggerAnim("action", "summon");
        long existing = sl.getEntitiesOfClass(Vex.class, getBoundingBox().inflate(30), v -> v.getOwner() == this).size();
        for (int i = 0; i < n && existing + i < 6; i++) {
            Vex v = EntityType.VEX.create(sl);
            if (v == null) continue;
            v.moveTo(getX() + random.nextInt(5) - 2, getY() + 1, getZ() + random.nextInt(5) - 2, random.nextFloat() * 360f, 0);
            v.setOwner(this);
            v.setBoundOrigin(blockPosition());
            v.setLimitedLife(20 * 25);
            v.setTarget(getTarget());
            sl.addFreshEntity(v);
        }
        sl.playSound(null, blockPosition(), SoundEvents.EVOKER_CAST_SPELL, SoundSource.HOSTILE, 1.4f, 0.8f);
    }

    /** Keeps distance, faces the target and fires attacks on an interval that shortens each phase. */
    static final class CastGoal extends Goal {
        private final ArchmageEntity boss;
        private int cooldown = 40;

        CastGoal(ArchmageEntity boss) {
            this.boss = boss;
            setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override public boolean canUse() { return boss.getTarget() != null && boss.getTarget().isAlive(); }
        @Override public boolean requiresUpdateEveryTick() { return true; }

        @Override
        public void tick() {
            LivingEntity t = boss.getTarget();
            if (t == null) return;
            boss.getLookControl().setLookAt(t, 30f, 30f);
            double d = boss.distanceTo(t);
            if (d > 16) boss.getNavigation().moveTo(t, 1.0);
            else if (d < 7) {
                Vec3 away = boss.position().subtract(t.position()).normalize().scale(6).add(boss.position());
                boss.getNavigation().moveTo(away.x, away.y, away.z, 1.1);
            } else boss.getNavigation().stop();
            if (boss.transition > 0) return;
            if (--cooldown <= 0) {
                boss.performAttack(t);
                cooldown = Math.max(22, 62 - boss.phase * 12 + boss.random.nextInt(20));
            }
        }
    }

    // ------------------------------------------------------------------ cinematic death

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        deathSource = source; // deferred until the death sequence ends
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel sl)) return;
        setDeltaMovement(0, deathTime < 80 ? 0.015 : 0, 0);
        setNoGravity(true);
        if (deathTime == 1) {
            Net.cinematicNear(sl, position(), PURPLE, DEATH_TICKS, 0);
            SpellFxEntity.spawn(sl, FxKind.SIGIL, position().add(0, 0.05, 0), 7f, DEATH_TICKS, SpellFxEntity.EASE);
            sl.playSound(null, blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 2f, 0.5f);
        }
        double a = deathTime * 0.5;
        for (int i = 0; i < 3; i++) {
            double ang = a + i * 2.094, h = (deathTime % 40) * 0.12;
            sl.sendParticles(DUST, getX() + Math.cos(ang) * (1.5 - h * 0.2), getY() + h, getZ() + Math.sin(ang) * (1.5 - h * 0.2), 2, 0.05, 0.05, 0.05, 0);
        }
        sl.sendParticles(ParticleTypes.END_ROD, getX(), getY() + 1.2, getZ(), 3, 0.5, 0.8, 0.5, 0.05 + deathTime * 0.002);
        if (deathTime % 22 == 0 && deathTime < DEATH_TICKS - 10) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(sl);
            if (bolt != null) {
                bolt.moveTo(getX() + random.nextInt(9) - 4, getY(), getZ() + random.nextInt(9) - 4);
                bolt.setVisualOnly(true);
                sl.addFreshEntity(bolt);
            }
        }
        if (deathTime >= DEATH_TICKS && !isRemoved()) {
            sl.sendParticles(ParticleTypes.FLASH, getX(), getY() + 1.2, getZ(), 2, 0, 0, 0, 0);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 1.2, getZ(), 3, 1, 1, 1, 0);
            sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getY() + 1.2, getZ(), 120, 1.2, 1.5, 1.2, 0.6);
            sl.playSound(null, blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2f, 0.5f);
            super.dropAllDeathLoot(sl, deathSource != null ? deathSource : damageSources().generic());
            remove(RemovalReason.KILLED);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }
}
