package com.infinitemultiverse.client.cinematic;

import com.infinitemultiverse.client.vfx.VfxSpawner;
import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.network.ScenePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** One running effect: world geometry, particles, and optionally camera direction and screen overlays. */
public abstract class Scene {
    protected final ScenePayload p;
    protected final Vec3 origin;
    protected final Vec3 dir;
    protected final int color;
    protected final int duration;
    protected final float param;
    protected final long seed;
    protected int age;

    protected Scene(ScenePayload payload) {
        this.p = payload;
        this.origin = payload.origin();
        this.dir = payload.dir().lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : payload.dir();
        this.color = payload.color();
        this.duration = Math.max(1, payload.duration());
        this.param = payload.param();
        this.seed = Double.doubleToLongBits(origin.x * 31 + origin.z) ^ payload.scene().hashCode();
    }

    @Nullable
    public Entity entity() {
        Minecraft mc = Minecraft.getInstance();
        return p.entityId() < 0 || mc.level == null ? null : mc.level.getEntity(p.entityId());
    }

    public boolean empowered() {
        return (p.flags() & Cinematics.EMPOWERED) != 0;
    }

    /** Where the scene is anchored this frame (follows its entity when flagged). */
    public Vec3 anchor(float pt) {
        Entity e = entity();
        if ((p.flags() & Cinematics.FOLLOW) != 0 && e != null) {
            return e.getPosition(pt);
        }
        return origin;
    }

    /** 0..1 progress with partial ticks. */
    public float t(float pt) {
        return Mth.clamp((age + pt) / duration, 0f, 1f);
    }

    public float time(float pt) {
        return age + pt;
    }

    /** Fade in over {@code in} ticks and out over the last {@code out} ticks. */
    public float envelope(float pt, float in, float out) {
        float a = age + pt;
        return Mth.clamp(Math.min(in <= 0 ? 1 : a / in, out <= 0 ? 1 : (duration - a) / out), 0f, 1f);
    }

    public boolean done() {
        return age >= duration;
    }

    public void tick(VfxSpawner s) {
    }

    public abstract void render(FxDraw d, float pt);

    /** Non-null while this scene directs the camera. */
    @Nullable
    public CinematicCamera.Shot shot(float pt) {
        return null;
    }

    /** Is this a cutscene that the local player should watch? */
    public boolean wantsCamera() {
        return false;
    }

    public void overlay(GuiGraphics g, float pt, boolean watching) {
    }

    /** White flash strength 0..1. */
    public float flash(float pt) {
        return 0f;
    }

    /** Camera shake amplitude in degrees at the local player. */
    public float shake(float pt) {
        return 0f;
    }

    /** FOV multiplier (1 = none). */
    public float fov(float pt) {
        return 1f;
    }

    protected static float falloff(Vec3 at, double radius) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 0;
        }
        double d = mc.player.position().distanceTo(at);
        return (float) Mth.clamp(1 - d / radius, 0, 1);
    }

    protected boolean localIsCaster() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.getId() == p.entityId();
    }
}
