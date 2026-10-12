package dev.finalframe.sheriff;

import static dev.finalframe.sheriff.DuelScript.*;

import dev.finalframe.FFConfig;
import dev.finalframe.FinalFrame;
import dev.finalframe.finisher.AnchorFrame;
import dev.finalframe.finisher.Ease;
import dev.finalframe.finisher.FinisherDefinition;
import dev.finalframe.finisher.FinisherPhase;
import dev.finalframe.finisher.FinisherRules;
import dev.finalframe.finisher.FinisherSession;
import dev.finalframe.finisher.FinisherSnapshot;
import dev.finalframe.finisher.TargetMotion;
import dev.finalframe.finisher.Timeline;
import dev.finalframe.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Server half of The Last Duel: the target is staring the sheriff down, so the sheriff gives it a fair fight. */
public final class DuelFinisher extends FinisherDefinition {
    public static final DuelFinisher INSTANCE = new DuelFinisher();

    private static final Holder<SoundEvent> STEP = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.GRAVEL_STEP);
    private static final Holder<SoundEvent> BELL = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.BELL_BLOCK);
    private static final Holder<SoundEvent> HEART = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.WARDEN_HEARTBEAT);

    private static final Timeline TIMELINE = Timeline.builder()
        .phase(FinisherPhase.ACTIVATION, 0)
        .phase(FinisherPhase.SHOVE, APPROACH_END)
        .phase(FinisherPhase.DRAW, SECOND_DRAW)
        .phase(FinisherPhase.THROW, THROW)
        .phase(FinisherPhase.CATCH, CATCH)
        .phase(FinisherPhase.AIM, DRAW)
        .phase(FinisherPhase.FIRE, SHOT_HEARD)
        .phase(FinisherPhase.FINISH, BLACK)
        .phase(FinisherPhase.RESTORE, RETURN)
        .end(END);

    private DuelFinisher() {
        super(FinalFrame.id("last_duel"));
    }

    @Override
    public FinisherRules.Verdict condition(Player performer, LivingEntity target) {
        if (!FinisherRules.withinRange(performer, target, FFConfig.get(FFConfig.DUEL_RANGE))) {
            return FinisherRules.Verdict.TOO_FAR;
        }
        Vec3 d = target.position().subtract(performer.position());
        if (d.x * d.x + d.z * d.z < 1.5 * 1.5) {
            return FinisherRules.Verdict.TOO_FAR;
        }
        return FinisherRules.isFacing(target, performer, FFConfig.get(FFConfig.DUEL_FACING_ANGLE))
            ? FinisherRules.Verdict.OK : FinisherRules.Verdict.INELIGIBLE;
    }

    @Override
    public Timeline timeline() {
        return TIMELINE;
    }

    @Override
    public int fatalTick() {
        return FATAL;
    }

    @Override
    public int[] slowMotionWindow() {
        return new int[] {DRAW - 2, BLACK};
    }

    @Override
    public Vec3 performerLocalOffset(FinisherSnapshot snapshot, float t) {
        return DuelScript.performer(snapshot, t);
    }

    @Override
    public Vec3 planTargetEnd(ServerLevel level, Player performer, LivingEntity target, AnchorFrame frame) {
        Vec3 dir = frame.forward();
        AABB box = target.getBoundingBox();
        for (double d = PUSH; d > 0.05; d -= 0.1) {
            AABB moved = box.move(dir.scale(d));
            if (level.noCollision(target, moved) && !level.noCollision(target, moved.move(0, -0.6, 0))) {
                return target.position().add(dir.scale(d));
            }
        }
        return target.position();
    }

    @Override
    public TargetMotion targetMotion(FinisherSnapshot s, float t) {
        float p = Ease.outCubic(Ease.range(t, CONTACT, STUMBLE_END));
        Vec3 pos = SheriffScript.lerp(s.targetStart(), s.targetEnd(), p);
        float facing = s.anchorYaw() + 180f;
        float body = s.targetStartYaw() + Mth.wrapDegrees(facing - s.targetStartYaw()) * Ease.inOutSine(Ease.range(t, 0, 8));
        float head = body - 25f * Ease.bump(t, CATCH - 2, SHERIFF_HOLSTER + 2) + 4f * Mth.sin(t * 0.05f) * Ease.bump(t, STANDOFF, DRAW);
        float lean = -16f * Ease.bump(t, CONTACT, CONTACT + 10) - 6f * Ease.bump(t, CONTACT + 8, STUMBLE_END)
            - 85f * Ease.inCubic(Ease.range(t, FALL, FATAL));
        float roll = 5f * Mth.sin(p * (float) Math.PI * 2f) * (1 - p) + 10f * Ease.range(t, FALL, FATAL);
        return new TargetMotion(pos, body, head, lean, roll);
    }

    @Override
    public void serverTick(FinisherSession s, int t) {
        AnchorFrame f = s.frame();
        FinisherSnapshot snap = s.snapshot();
        boolean walking = (t > 2 && t < APPROACH_END) || (t >= WALK && t < WALK_END);
        if (walking && t % 7 == 3) {
            Vec3 feet = world(snap, DuelScript.performer(snap, t));
            s.sound(STEP, feet, 0.9f, 0.62f);
            dust(s, feet, 3);
        }
        switch (t) {
            case CONTACT - 2 -> s.sound(FFSounds.CLOTH, 0.7f, 0.85f);
            case CONTACT -> {
                Vec3 chest = s.target().position().add(0, s.snapshot().targetHeight() * 0.6, 0);
                s.sound(FFSounds.SHOVE, chest, 1f, 0.8f);
                s.particles(ParticleTypes.POOF, chest, 6, 0.2, 0.2, 0.2, 0.02);
            }
            case CONTACT + 3, CONTACT + 8, CONTACT + 13 -> {
                Vec3 feet = s.target().position();
                s.sound(STEP, feet, 0.8f, 0.75f);
                dust(s, feet, 5);
            }
            case STUMBLE_END - 2 -> s.sound(FFSounds.STUMBLE, s.target().position(), 0.9f, 0.9f);
            case SECOND_DRAW + 1 -> s.sound(FFSounds.DRAW, 0.8f, 1.1f);
            case SECOND_DRAW + 4 -> s.sound(FFSounds.REVOLVER_SPIN, 0.7f, 1.15f);
            case THROW -> s.sound(FFSounds.WHOOSH, 0.8f, 1.2f);
            case CATCH -> s.sound(FFSounds.CATCH, world(snap, targetHand(snap)), 0.9f, 0.9f);
            case SHERIFF_SPIN -> s.sound(FFSounds.REVOLVER_SPIN, 0.8f, 1f);
            case SHERIFF_HOLSTER -> {
                s.sound(FFSounds.HOLSTER, 0.9f, 0.95f);
                s.sound(FFSounds.CLOTH, world(snap, targetHand(snap)), 0.6f, 0.8f);
            }
            case STANDOFF -> {
                s.freezeNearby(16);
                s.sound(BELL, f.toWorld(0, 4, DuelScript.endDistance(snap) * 0.5), 0.9f, 0.5f);
            }
            case STANDOFF + 30 -> s.sound(BELL, f.toWorld(0, 4, DuelScript.endDistance(snap) * 0.5), 0.6f, 0.5f);
            // The revolver in the holster turns over by itself. Nobody touched it.
            case SELF_CYLINDER -> s.sound(FFSounds.CYLINDER_SPIN, 0.35f, 0.7f);
            case POV_T + 4, RAPID, RAPID + 5, RAPID + 9, RAPID + 12, RAPID + 14 ->
                s.sound(HEART, s.performer().position().add(0, 1, 0), 0.9f, 0.9f);
            case DRAW -> {
                s.sound(FFSounds.DRAW, 0.9f, 0.85f);
                s.sound(FFSounds.DRAW, world(snap, targetHand(snap)), 0.9f, 0.8f);
            }
            case DRAW + 2 -> s.sound(FFSounds.HAMMER_COCK, 0.6f, 0.9f);
            // One shot for two guns, and it arrives before either muzzle flashes.
            case SHOT_HEARD -> s.sound(FFSounds.LAST_WORD_SHOT, f.toWorld(0, 1.4, DuelScript.endDistance(snap) * 0.5), 2.2f, 0.92f);
            case FIRE -> {
                s.particles(ParticleTypes.SMOKE, world(snap, performerMuzzle(snap)), 6, 0.05, 0.05, 0.05, 0.01);
                s.particles(ParticleTypes.SMOKE, world(snap, targetAimMuzzle(snap)), 6, 0.05, 0.05, 0.05, 0.01);
            }
            case BLACK + 4 -> s.sound(FFSounds.EAR_RING, 0.45f, 1f);
            case SPIN -> s.sound(FFSounds.REVOLVER_SPIN, 0.8f, 1f);
            case HOLSTER -> s.sound(FFSounds.HOLSTER, 0.8f, 1f);
            case FATAL -> {
                s.sound(FFSounds.IMPACT, s.target().position(), 0.6f, 0.6f);
                dust(s, s.target().position(), 14);
                s.defeatTarget();
            }
            case FATAL + 6 -> s.sound(FFSounds.STINGER, 0.6f, 0.94f);
            default -> {
            }
        }
    }

    private static void dust(FinisherSession s, Vec3 feet, int count) {
        BlockState below = s.level().getBlockState(BlockPos.containing(feet.x, feet.y - 0.2, feet.z));
        if (!below.isAir()) {
            s.particles(new BlockParticleOption(ParticleTypes.BLOCK, below), feet.add(0, 0.05, 0), count, 0.18, 0.02, 0.18, 0.05);
        }
    }
}
