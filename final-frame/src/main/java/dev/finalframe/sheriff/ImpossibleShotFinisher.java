package dev.finalframe.sheriff;

import static dev.finalframe.sheriff.ImpossibleScript.*;

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
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The Impossible Shot. The sheriff just missed. The finisher never shows a second shot landing, yet something hits.
 */
public final class ImpossibleShotFinisher extends FinisherDefinition {
    public static final ImpossibleShotFinisher INSTANCE = new ImpossibleShotFinisher();

    /** How far the target may be for the missed shot to still matter. */
    private static final double RANGE = 14.0;

    private static final Holder<SoundEvent> STEP = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.GRAVEL_STEP);
    private static final Holder<SoundEvent> PING = BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.AMETHYST_BLOCK_RESONATE);

    private static final Timeline TIMELINE = Timeline.builder()
        .phase(FinisherPhase.ACTIVATION, 0)
        .phase(FinisherPhase.AIM, EARLY)
        .phase(FinisherPhase.FIRE, HEARD)
        .phase(FinisherPhase.FINISH, HOLSTER_SCENE)
        .phase(FinisherPhase.RESTORE, RETURN)
        .end(END);

    private ImpossibleShotFinisher() {
        super(FinalFrame.id("impossible_shot"));
    }

    @Override
    public FinisherRules.Verdict condition(Player performer, LivingEntity target) {
        if (!FinisherRules.withinRange(performer, target, RANGE)) {
            return FinisherRules.Verdict.TOO_FAR;
        }
        return ShotMemory.recent(performer, target) != null ? FinisherRules.Verdict.OK : FinisherRules.Verdict.INELIGIBLE;
    }

    @Override
    public Vec3 auxPoint(Player performer, LivingEntity target) {
        ShotMemory.Miss miss = ShotMemory.recent(performer, target);
        ShotMemory.forget(performer);
        Vec3 eye = performer.getEyePosition();
        if (miss == null) {
            return target.position().add(target.position().subtract(eye).normalize().scale(3)).add(0, 1, 0);
        }
        // A shot into open sky has nothing to stick in; it stops a little way past them.
        Vec3 impact = miss.impact();
        double past = impact.distanceTo(eye) - target.position().distanceTo(eye);
        if (past > 10) {
            impact = eye.add(impact.subtract(eye).normalize().scale(target.position().distanceTo(eye) + 6));
        }
        return impact;
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
        return new int[] {EARLY, HUNG + 4};
    }

    @Override
    public Vec3 performerLocalOffset(FinisherSnapshot snapshot, float t) {
        return ImpossibleScript.performer(snapshot, t);
    }

    @Override
    public Vec3 planTargetEnd(ServerLevel level, Player performer, LivingEntity target, AnchorFrame frame) {
        return target.position();
    }

    @Override
    public TargetMotion targetMotion(FinisherSnapshot s, float t) {
        Vec3 eyes = new Vec3(0, s.targetHeight() * 0.86, tz(s));
        float toImpact = yawTo(s, eyes, impact(s));
        float toSheriff = s.anchorYaw() + 180f;
        float body = s.targetStartYaw() + Mth.wrapDegrees(toSheriff - s.targetStartYaw()) * Ease.inOutSine(Ease.range(t, LOOK_BACK, LOOK_BACK + 8));
        float glance = Ease.inOutSine(Ease.range(t, LOOK, LOOK + 6)) * (1 - Ease.inOutSine(Ease.range(t, LOOK_BACK, LOOK_BACK + 8)));
        float head = body + Mth.wrapDegrees(toImpact - body) * glance;
        // Flinches at shots no one fired, and turns toward places the sheriff should not be.
        head += 14f * (Ease.bump(t, HEARD_1, HEARD_1 + 5) - Ease.bump(t, HEARD_2, HEARD_2 + 5) + Ease.bump(t, HEARD_3, HEARD_3 + 5));
        head += 150f * Ease.bump(t, SHIFT + 1, SHIFT_BACK + 4) - 70f * Ease.bump(t, SHIFT_SIDE + 1, SHIFT_HOME + 2);
        float lean = -5f * (Ease.bump(t, HEARD_1, HEARD_1 + 6) + Ease.bump(t, HEARD_2, HEARD_2 + 6) + Ease.bump(t, HEARD_3, HEARD_3 + 6));
        return new TargetMotion(s.targetStart(), body, head, lean, 0);
    }

    @Override
    public void serverTick(FinisherSession s, int t) {
        AnchorFrame f = s.frame();
        FinisherSnapshot snap = s.snapshot();
        if (t >= WALK && t < WALK_END && (t - WALK) % 7 == 3) {
            s.sound(STEP, f.toWorld(ImpossibleScript.performer(snap, t)), 0.8f, 0.7f);
        }
        switch (t) {
            case LOWERED - 6 -> s.sound(FFSounds.CLOTH, 0.5f, 0.85f);
            case EARLY -> s.sound(FFSounds.SLOWMO_IN, 0.6f, 0.8f);
            case BACK_OUT -> s.sound(FFSounds.DRY_FIRE, snap.aux(), 0.6f, 0.45f);
            case HUNG -> s.sound(PING, snap.aux(), 0.7f, 1.6f);
            case GHOST_1, GHOST_2, GHOST_3 -> s.sound(FFSounds.WHOOSH, s.target().position().add(0, 1.5, 0), 0.35f, 0.55f);
            case CYLINDER -> s.sound(FFSounds.CYLINDER_SPIN, 0.45f, 0.8f);
            // Shots heard before anything fires.
            case HEARD_1, HEARD_2, HEARD_3 -> s.sound(FFSounds.SHOT, f.toWorld(0, 1.4, 0), 2.2f, 0.9f + 0.05f * (t % 3));
            case UNTOUCHED -> s.particles(ParticleTypes.SMOKE, f.toWorld(0.36, 0.72, 0.6), 6, 0.04, 0.04, 0.04, 0.01);
            case SHIFT, SHIFT_BACK, SHIFT_SIDE, SHIFT_HOME -> s.sound(STEP, s.target().position(), 0.7f, 0.6f);
            case SPIN -> s.sound(FFSounds.REVOLVER_SPIN, 0.8f, 1f);
            case HOLSTER -> s.sound(FFSounds.HOLSTER, 0.8f, 1f);
            case FATAL -> {
                Vec3 head = s.target().position().add(0, s.snapshot().targetHeight() * 0.86, 0);
                s.sound(FFSounds.IMPACT, head, 0.7f, 0.7f);
                s.defeatTarget();
            }
            case FATAL + 4 -> s.sound(FFSounds.EAR_RING, 0.4f, 1.1f);
            case WALK_END - 4 -> s.sound(FFSounds.STINGER, 0.55f, 0.84f);
            default -> {
            }
        }
    }
}
