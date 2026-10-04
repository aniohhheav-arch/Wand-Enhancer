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

    /** Tick range [start, end) during which world slow motion may be applied, or null for none. */
    public int[] slowMotionWindow() {
        return null;
    }
}
