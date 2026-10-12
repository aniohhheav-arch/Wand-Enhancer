package dev.finalframe.finisher;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A cinematic finisher. Implementations describe their timeline, the deterministic target path both
 * sides replay, and the server-side beats (sounds, particles, the killing blow). The matching client
 * presentation (camera, poses, effects) is registered separately through
 * {@code dev.finalframe.client.choreo.Choreographies} under the same id.
 */
public abstract class FinisherDefinition {
    private final ResourceLocation id;

    protected FinisherDefinition(ResourceLocation id) {
        this.id = id;
    }

    public final ResourceLocation id() {
        return id;
    }

    public abstract Timeline timeline();

    /** Tick at which the target is defeated. Before this the target must stay present or the finisher aborts. */
    public abstract int fatalTick();

    /** Server plans where the target ends up after the activation push (collision-aware). */
    public abstract Vec3 planTargetEnd(ServerLevel level, Player performer, LivingEntity target, AnchorFrame frame);

    /** Deterministic target pose at fractional tick {@code t}. Evaluated by the server and every client. */
    public abstract TargetMotion targetMotion(FinisherSnapshot snapshot, float t);

    /** Called once per server tick with the session's current tick. */
    public abstract void serverTick(FinisherSession session, int tick);

    /** Situational condition (range, angle, awareness) on top of the shared eligibility rules. */
    public FinisherRules.Verdict condition(Player performer, LivingEntity target) {
        return FinisherRules.Verdict.OK;
    }

    /** A finisher-specific world point carried to every client in the snapshot (e.g. where an earlier shot landed). */
    public Vec3 auxPoint(Player performer, LivingEntity target) {
        return Vec3.ZERO;
    }

    /** Where the server holds the performer, in the anchor's local frame. Lets a finisher walk its performer. */
    public Vec3 performerLocalOffset(FinisherSnapshot snapshot, float t) {
        return Vec3.ZERO;
    }

    /** Tick range [start, end) during which world slow motion may be applied, or null for none. */
    public int[] slowMotionWindow() {
        return null;
    }
}
