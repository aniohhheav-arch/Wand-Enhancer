package dev.finalframe.finisher;

import dev.finalframe.FinalFrame;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Server-side state of one running finisher. Definitions drive it through the helper methods below. */
public final class FinisherSession {
    public static final ResourceKey<DamageType> LAST_WORD = ResourceKey.create(Registries.DAMAGE_TYPE, FinalFrame.id("last_word"));

    final FinisherDefinition definition;
    final FinisherSnapshot snapshot;
    final ServerPlayer performer;
    final LivingEntity target;
    final ServerLevel level;
    final boolean targetHadNoAi;
    private final java.util.List<Runnable> cleanups = new java.util.ArrayList<>();
    int tick;
    boolean targetDefeated;
    boolean slowMotion;
    float savedTickRate;

    FinisherSession(FinisherDefinition definition, FinisherSnapshot snapshot, ServerPlayer performer, LivingEntity target) {
        this.definition = definition;
        this.snapshot = snapshot;
        this.performer = performer;
        this.target = target;
        this.level = performer.serverLevel();
        this.targetHadNoAi = target instanceof Mob mob && mob.isNoAi();
    }

    public FinisherSnapshot snapshot() {
        return snapshot;
    }

    public AnchorFrame frame() {
        return snapshot.frame();
    }

    public ServerPlayer performer() {
        return performer;
    }

    public LivingEntity target() {
        return target;
    }

    public ServerLevel level() {
        return level;
    }

    public int tick() {
        return tick;
    }

    public boolean targetDefeated() {
        return targetDefeated;
    }

    /** Broadcast once from the server, so every nearby client hears exactly one copy. */
    public void sound(Holder<SoundEvent> sound, Vec3 at, float volume, float pitch) {
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch);
    }

    public void sound(Holder<SoundEvent> sound, float volume, float pitch) {
        sound(sound, performer.position().add(0, 1.2, 0), volume, pitch);
    }

    public void particles(ParticleOptions type, Vec3 at, int count, double spreadX, double spreadY, double spreadZ, double speed) {
        level.sendParticles(type, at.x, at.y, at.z, count, spreadX, spreadY, spreadZ, speed);
    }

    /** Spawns one particle with an explicit velocity (count 0 makes the spread fields a velocity). */
    public void particleWithVelocity(ParticleOptions type, Vec3 at, Vec3 velocity) {
        level.sendParticles(type, at.x, at.y, at.z, 0, velocity.x, velocity.y, velocity.z, 1.0);
    }

    /** Delivers the finishing blow through the dedicated damage type. */
    public void defeatTarget() {
        if (targetDefeated) {
            return;
        }
        targetDefeated = true;
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(LAST_WORD);
        DamageSource source = new DamageSource(type, performer, performer);
        target.invulnerableTime = 0;
        target.hurt(source, Math.max(1000f, target.getMaxHealth() * 4f));
    }

    /** Registers state to restore when the session ends for any reason (completion, abort, server stop). */
    public void onStop(Runnable cleanup) {
        cleanups.add(cleanup);
    }

    void runCleanups() {
        for (int i = cleanups.size() - 1; i >= 0; i--) {
            try {
                cleanups.get(i).run();
            } catch (RuntimeException e) {
                com.mojang.logging.LogUtils.getLogger().warn("Finisher cleanup failed", e);
            }
        }
        cleanups.clear();
    }

    /** Freezes every other mob within {@code radius} until the session ends. */
    public void freezeNearby(double radius) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, performer.getBoundingBox().inflate(radius))) {
            if (mob == target || mob.isNoAi()) {
                continue;
            }
            Vec3 motion = mob.getDeltaMovement();
            mob.setNoAi(true);
            mob.setDeltaMovement(Vec3.ZERO);
            onStop(() -> {
                if (!mob.isRemoved()) {
                    mob.setNoAi(false);
                    mob.setDeltaMovement(motion);
                }
            });
        }
    }

    boolean isLastWord(DamageSource source) {
        return source.is(LAST_WORD);
    }
}
