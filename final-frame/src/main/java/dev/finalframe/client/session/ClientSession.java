package dev.finalframe.client.session;

import dev.finalframe.client.camera.CameraPose;
import dev.finalframe.client.camera.CameraShake;
import dev.finalframe.client.choreo.Choreography;
import dev.finalframe.finisher.FinisherDefinition;
import dev.finalframe.finisher.FinisherSnapshot;
import dev.finalframe.finisher.TargetMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

/** A finisher being replayed on this client. */
public final class ClientSession {
    /** Ticks used to hand the camera back after an interruption. */
    public static final int ABORT_BLEND = 10;

    private final FinisherSnapshot snapshot;
    private final FinisherDefinition definition;
    private final Choreography choreography;
    private final CameraShake shake = new CameraShake();
    int tick;
    int abortTick = -1;
    boolean serverEnded;
    @Nullable
    CameraPose lastCinematicPose;

    ClientSession(FinisherSnapshot snapshot, FinisherDefinition definition, Choreography choreography, int elapsed) {
        this.snapshot = snapshot;
        this.definition = definition;
        this.choreography = choreography;
        this.tick = elapsed;
    }

    public FinisherSnapshot snapshot() {
        return snapshot;
    }

    public FinisherDefinition definition() {
        return definition;
    }

    public Choreography choreography() {
        return choreography;
    }

    public CameraShake shake() {
        return shake;
    }

    public int tick() {
        return tick;
    }

    /** Fractional timeline position, frozen at the moment of an abort. */
    public float time(float partialTick) {
        if (abortTick >= 0) {
            return abortTick;
        }
        return Math.min(tick + partialTick, definition.timeline().length());
    }

    public boolean isAborting() {
        return abortTick >= 0;
    }

    /** 0..1 progress of the abort hand-back. */
    public float abortProgress(float partialTick) {
        return abortTick < 0 ? 0 : Math.min(1f, (tick - abortTick + partialTick) / ABORT_BLEND);
    }

    public boolean isPerformer(Entity entity) {
        return entity.getId() == snapshot.performerId();
    }

    public boolean isTarget(Entity entity) {
        return entity.getId() == snapshot.targetId();
    }

    public boolean isLocal() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == snapshot.performerId();
    }

    public TargetMotion targetMotion(float t) {
        return definition.targetMotion(snapshot, t);
    }

    @Nullable
    public CameraPose lastCinematicPose() {
        return lastCinematicPose;
    }

    public void rememberPose(CameraPose pose) {
        if (!isAborting()) {
            lastCinematicPose = pose;
        }
    }
}
