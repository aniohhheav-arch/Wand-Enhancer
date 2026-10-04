package dev.finalframe.sheriff;

import static dev.finalframe.sheriff.SheriffScript.*;

import dev.finalframe.FinalFrame;
import dev.finalframe.finisher.AnchorFrame;
import dev.finalframe.finisher.Ease;
import dev.finalframe.finisher.FinisherDefinition;
import dev.finalframe.finisher.FinisherPhase;
import dev.finalframe.finisher.FinisherSession;
import dev.finalframe.finisher.FinisherSnapshot;
import dev.finalframe.finisher.TargetMotion;
import dev.finalframe.finisher.Timeline;
import dev.finalframe.registry.FFSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server half of The Sheriff's Last Word: timeline, target choreography and every synchronized beat. */
public final class SheriffFinisher extends FinisherDefinition {
    public static final SheriffFinisher INSTANCE = new SheriffFinisher();

    public static final DustParticleOptions GOLD_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.78f, 0.32f), 1.1f);
    public static final DustParticleOptions EMBER_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.55f, 0.18f), 0.7f);
    public static final DustParticleOptions WHITE_DUST = new DustParticleOptions(new Vector3f(1.0f, 0.97f, 0.88f), 1.4f);

    private static final Timeline TIMELINE = Timeline.builder()
        .phase(FinisherPhase.ACTIVATION, ACTIVATION)
        .phase(FinisherPhase.SHOVE, SHOVE)
        .phase(FinisherPhase.DRAW, DRAW)
        .phase(FinisherPhase.FLOURISH, FLOURISH)
        .phase(FinisherPhase.THROW, THROW)
        .phase(FinisherPhase.CATCH, CATCH)
        .phase(FinisherPhase.AIM, AIM)
        .phase(FinisherPhase.FIRE, FIRE_PHASE)
        .phase(FinisherPhase.FINISH, FINISH)
        .phase(FinisherPhase.RESTORE, RESTORE)
        .end(END);

    private SheriffFinisher() {
        super(FinalFrame.id("sheriffs_last_word"));
    }

    @Override
    public Timeline timeline() {
        return TIMELINE;
    }

    @Override
    public int fatalTick() {
        return IMPACT;
    }

    @Override
    public int[] slowMotionWindow() {
        return new int[] {TOSS + 2, CATCH};
    }

    /** Pushes the target forward as far as the terrain allows (up to {@link SheriffScript#PUSH_DISTANCE}), never off a ledge. */
    @Override
    public Vec3 planTargetEnd(ServerLevel level, Player performer, LivingEntity target, AnchorFrame frame) {
        Vec3 dir = frame.forward();
        AABB box = target.getBoundingBox();
        for (double d = PUSH_DISTANCE; d > 0.05; d -= 0.1) {
            AABB moved = box.move(dir.scale(d));
            boolean clear = level.noCollision(target, moved);
            boolean grounded = !level.noCollision(target, moved.move(0, -0.6, 0));
            if (clear && grounded) {
                return target.position().add(dir.scale(d));
            }
        }
        return target.position();
    }

    @Override
    public TargetMotion targetMotion(FinisherSnapshot s, float t) {
        AnchorFrame f = s.frame();
        Vec3 pos;
        if (t < CONTACT) {
            pos = s.targetStart();
        } else {
            float p = Ease.outCubic(Ease.range(t, CONTACT, STUMBLE_END));
            Vec3 base = lerp(s.targetStart(), s.targetEnd(), p);
            double wobble = 0.14 * Mth.sin(p * (float) Math.PI * 2f) * (1 - p);
            pos = base.add(f.right().scale(wobble));
        }

        float away = s.anchorYaw();
        float body;
        float head;
        if (t < CONTACT) {
            body = s.targetStartYaw();
            head = body;
        } else {
            float aligned = s.targetStartYaw() + Mth.wrapDegrees(away - s.targetStartYaw()) * Ease.outCubic(Ease.range(t, CONTACT, CONTACT + 4));
            float turn = 112f * Ease.inOutCubic(Ease.range(t, 14, 30));
            float face = (180f - 112f) * Ease.inOutCubic(Ease.range(t, AIM, AIM + 8));
            body = aligned + turn + face;
            head = body + 38f * Ease.inOutSine(Ease.range(t, 16, 30)) * (1 - Ease.range(t, AIM, AIM + 8));
        }

        float lean = 0;
        float roll = 0;
        if (t >= CONTACT) {
            lean = 30f * Ease.bump(t, CONTACT, CONTACT + 13) - 8f * Ease.bump(t, CONTACT + 10, STUMBLE_END + 4);
            float dazed = Ease.range(t, STUMBLE_END, STUMBLE_END + 6) * (1 - Ease.range(t, AIM, AIM + 6));
            roll = 4.5f * Mth.sin(t * 0.21f) * dazed;
            lean += 3f * Mth.sin(t * 0.13f + 1f) * dazed;
            lean -= 7f * Ease.bump(t, AIM + 2, FIRE);
        }
        return new TargetMotion(pos, body, head, lean, roll);
    }

    @Override
    public void serverTick(FinisherSession s, int t) {
        FinisherSnapshot snap = s.snapshot();
        AnchorFrame f = snap.frame();
        switch (t) {
            case ACTIVATION -> s.sound(FFSounds.CLOTH, 0.7f, 0.9f);
            case CONTACT -> {
                s.sound(FFSounds.SHOVE, 1.0f, 1.0f);
                Vec3 feet = snap.targetStart();
                s.particles(ParticleTypes.POOF, feet.add(0, 0.2, 0), 6, 0.3, 0.05, 0.3, 0.02);
                groundDust(s, feet, 24);
            }
            case CONTACT + 3 -> s.sound(FFSounds.STUMBLE, s.target().position(), 0.9f, 1.0f);
            case DRAW -> s.sound(FFSounds.CLOTH, 0.6f, 1.15f);
            case DRAW_PULL -> s.sound(FFSounds.DRAW, 1.0f, 1.0f);
            case DRAW_SPIN -> s.sound(FFSounds.REVOLVER_SPIN, 0.8f, 1.05f);
            case DRAW_CATCH -> s.sound(FFSounds.CATCH, 0.55f, 1.35f);
            case FLOURISH + 1 -> s.sound(FFSounds.REVOLVER_SPIN, 0.75f, 1.2f);
            case WRIST_ROLL -> s.sound(FFSounds.CLOTH, 0.45f, 1.4f);
            case BACK_FLIP -> s.sound(FFSounds.WHOOSH, 0.35f, 1.8f);
            case GRIP_CATCH -> s.sound(FFSounds.CATCH, 0.6f, 1.25f);
            case COCK -> s.sound(FFSounds.HAMMER_COCK, 1.0f, 1.0f);
            case CYLINDER_ROLL -> s.sound(FFSounds.CYLINDER_SPIN, 0.9f, 1.0f);
            case THROW -> s.sound(FFSounds.CLOTH, 0.6f, 0.95f);
            case TOSS -> s.sound(FFSounds.WHOOSH, 1.0f, 1.0f);
            case TOSS + 2 -> s.sound(FFSounds.SLOWMO_IN, 1.0f, 1.0f);
            case PEAK -> s.sound(FFSounds.REVOLVER_SPIN, 0.5f, 0.55f);
            case CATCH -> {
                s.sound(FFSounds.CATCH, 1.0f, 1.0f);
                s.sound(FFSounds.SLOWMO_OUT, 0.8f, 1.0f);
                s.particles(ParticleTypes.CRIT, f.toWorld(CATCH_POINT), 6, 0.08, 0.08, 0.08, 0.15);
            }
            case AIM + 2 -> s.sound(FFSounds.HAMMER_COCK, 0.9f, 0.85f);
            case FIRE -> fire(s);
            case IMPACT -> impact(s);
            case FINISH -> {
                s.sound(FFSounds.EAR_RING, 0.45f, 1.0f);
                s.sound(FFSounds.SMOKE_HISS, 0.5f, 1.0f);
            }
            case FINISH + 8 -> s.sound(FFSounds.STINGER, 0.9f, 1.0f);
            case FINAL_SPIN -> s.sound(FFSounds.REVOLVER_SPIN, 0.7f, 1.0f);
            case HOLSTER -> s.sound(FFSounds.HOLSTER, 1.0f, 1.0f);
            default -> {
            }
        }
        if (t > CONTACT && t <= STUMBLE_END && t % 4 == 0) {
            groundDust(s, s.target().position(), 6);
        }
        if (t > FIRE && t < LOWER && t % 4 == 0) {
            Vec3 muzzle = muzzleWorld(snap);
            s.particleWithVelocity(ParticleTypes.SMOKE, muzzle, new Vec3(0, 0.025, 0));
        }
    }

    private static void fire(FinisherSession s) {
        FinisherSnapshot snap = s.snapshot();
        Vec3 muzzle = muzzleWorld(snap);
        Vec3 hit = impactWorld(snap);
        s.sound(FFSounds.LAST_WORD_SHOT, muzzle, 4.0f, 1.0f);
        Vec3 dir = hit.subtract(muzzle).normalize();
        s.particles(ParticleTypes.FLASH, muzzle.add(dir.scale(0.2)), 1, 0, 0, 0, 0);
        for (int i = 0; i < 10; i++) {
            Vec3 v = dir.scale(0.06 + 0.02 * i).add(0, 0.01, 0);
            s.particleWithVelocity(ParticleTypes.SMOKE, muzzle, v);
        }
        s.particles(ParticleTypes.ELECTRIC_SPARK, muzzle, 8, 0.05, 0.05, 0.05, 0.25);
        double len = hit.distanceTo(muzzle);
        for (double d = 0.3; d < len; d += 0.22) {
            s.particles(GOLD_DUST, muzzle.add(dir.scale(d)), 1, 0, 0, 0, 0);
        }
    }

    private static void impact(FinisherSession s) {
        FinisherSnapshot snap = s.snapshot();
        Vec3 hit = impactWorld(snap);
        s.sound(FFSounds.IMPACT, hit, 1.6f, 1.0f);
        s.particles(WHITE_DUST, hit, 14, 0.12, 0.12, 0.12, 0);
        s.particles(ParticleTypes.END_ROD, hit, 18, 0.05, 0.05, 0.05, 0.18);
        s.particles(ParticleTypes.POOF, hit, 10, 0.2, 0.3, 0.2, 0.03);
        Vec3 ring = snap.targetEnd().add(0, 0.12, 0);
        for (int i = 0; i < 40; i++) {
            float a = i / 40f * Mth.TWO_PI;
            Vec3 v = new Vec3(Mth.cos(a) * 0.42, 0.015, Mth.sin(a) * 0.42);
            s.particleWithVelocity(i % 2 == 0 ? GOLD_DUST : EMBER_DUST, ring, v);
            s.particleWithVelocity(ParticleTypes.CLOUD, ring, v.scale(0.35));
        }
        s.defeatTarget();
    }

    private static void groundDust(FinisherSession s, Vec3 feet, int count) {
        BlockPos below = BlockPos.containing(feet.x, feet.y - 0.2, feet.z);
        BlockState ground = s.level().getBlockState(below);
        if (!ground.isAir()) {
            s.particles(new BlockParticleOption(ParticleTypes.BLOCK, ground), feet.add(0, 0.1, 0), count, 0.35, 0.05, 0.35, 0.12);
        }
    }
}
