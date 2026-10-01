package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.creature.NeonDroneEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Quad-rotor security drone with a single scanning eye that flares magenta when it charges a shot. */
public class NeonDroneRenderer extends ProceduralRenderer<NeonDroneEntity> {
    private static final float CY = 0.45f;

    public NeonDroneRenderer(EntityRendererProvider.Context context) {
        super(context, 0.4f);
    }

    @Override
    protected boolean pitches() {
        return true;
    }

    @Override
    protected void draw(NeonDroneEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        float hover = (float) Math.sin(age * 0.2f + entity.getId()) * 0.05f;
        ps.pushPose();
        ps.translate(0, hover, 0);
        PoseStack.Pose pose = ps.last();
        boolean charging = entity.isCharging();
        int neon = charging ? 0xFF2BD6 : 0x2BF3FF;

        // chassis: armoured core with plates
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, CY, 0, 0.27f, 0.22f, 0.3f, 12, 0x3A4058, 0x15171F, 1f, light, overlay, -2f);
        FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, CY + 0.2f, -0.05f, 0.16f, 0.04f, 0.2f, 0x262A3A, 1f, light, overlay);
        FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, CY - 0.2f, 0, 0.12f, 0.04f, 0.15f, 0x262A3A, 1f, light, overlay);
        for (int s = -1; s <= 1; s += 2) {
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), pose, s * 0.26f, CY, 0, 0.04f, 0.12f, 0.18f, 0x2E3346, 1f, light, overlay);
        }

        // rotor arms with spinning blades
        float spin = age * 1.6f;
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            float ax = (float) Math.cos(a) * 0.55f, az = (float) Math.sin(a) * 0.55f;
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose, new Vector3f(0, CY + 0.05f, 0), new Vector3f(ax, CY + 0.1f, az), 0.04f, 0.035f, 0x2E3346, 0x434A66, 1f, light, overlay);
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), pose, ax, CY + 0.1f, az, 0.06f, 0.05f, 0.06f, 0x1A1D28, 1f, light, overlay);
            ps.pushPose();
            ps.translate(ax, CY + 0.17f, az);
            ps.mulPose(Axis.YP.rotation(spin * (i % 2 == 0 ? 1 : -1)));
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, 0, 0, 0.22f, 0.006f, 0.025f, 0x55607A, 1f, light, overlay);
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, 0, 0, 0.025f, 0.006f, 0.22f, 0x55607A, 1f, light, overlay);
            ps.popPose();
            FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(ax, CY + 0.17f, az), new Vector3f(0, 1, 0), 0.23f, 0.03f, neon, 0.45f, 20);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, ax, CY + 0.04f, az, 0.07f, neon, 0.9f);
        }

        // light strips along the hull
        Vec3 eye = cameraLocal(pose);
        for (int s = -1; s <= 1; s += 2) {
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(s * 0.31f, CY + 0.06f, 0.18f), new Vec3(s * 0.31f, CY + 0.06f, -0.18f), eye, 0.035f, neon, 0.9f);
        }

        // the eye: lens, glow and (while charging) converging rings
        float flare = charging ? 0.7f + 0.3f * (float) Math.sin(age * 1.3f) : 0.55f + 0.15f * (float) Math.sin(age * 0.3f);
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.glow()), pose, 0, CY, 0.27f, 0.1f, 0.1f, 0.05f, 10, neon, neon, 1f, FxDraw.FULL_BRIGHT, overlay, -2f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, CY, 0.33f, (charging ? 0.55f : 0.3f) * flare, neon, 0.9f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, CY, 0.34f, 0.08f, 0xFFFFFF, 1f);
        if (charging) {
            for (int i = 0; i < 3; i++) {
                float t = ((age * 0.08f) + i / 3f) % 1f;
                FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0, CY, 0.32f + t * 0.4f), new Vector3f(0, 0, 1), 0.4f * (1f - t) + 0.05f, 0.04f, neon, t, 24);
            }
        } else {
            // idle scan line sweeping below the drone
            float sweep = (float) Math.sin(age * 0.09f) * 0.6f;
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(0, CY, 0.3f), new Vec3(sweep, -1.2f, 1.6f), eye, 0.05f, neon, 0.25f);
        }
        ps.popPose();
    }
}
