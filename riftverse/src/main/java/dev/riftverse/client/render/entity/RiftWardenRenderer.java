package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.SpatialFx;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.entity.boss.RiftWardenEntity;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * The Custodian of the Boundaries: a floating armoured titan with a singularity for a heart, detached gauntlets,
 * a rotating halo and a vortex in place of legs. Its phase tints the energy from gold through violet to red.
 */
public class RiftWardenRenderer extends ProceduralRenderer<RiftWardenEntity> {
    private static final int ARMOR = 0xC9A24A;
    private static final int ARMOR_DARK = 0x4A3418;
    private static final float CHEST_Y = 3.15f;
    private static final double BEAM_LENGTH = 48.0;

    public RiftWardenRenderer(EntityRendererProvider.Context context) {
        super(context, 0f);
    }

    private static float hover(float age) {
        return (float) Math.sin(age * 0.05f) * 0.15f;
    }

    private static int energy(RiftWardenEntity entity) {
        return switch (entity.phase()) {
            case 3 -> 0xFF4A3A;
            case 2 -> 0xB46BFF;
            default -> RiftWardenEntity.COLOR;
        };
    }

    @Override
    protected void drawWorldAligned(RiftWardenEntity entity, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        if (entity.deathTime > 0) return;
        // the heart: a real lensing singularity sitting in the chest cavity
        float yaw = Mth.rotLerp(partial, entity.yBodyRotO, entity.yBodyRot) * Mth.DEG_TO_RAD;
        float scale = entity.getScale();
        Vec3 chest = entity.getPosition(partial).add(-Math.sin(yaw) * 0.55 * scale, (CHEST_Y + hover(entity.tickCount + partial)) * scale, Math.cos(yaw) * 0.55 * scale);
        SpatialFx.hole(chest, 0.16f, new Vector3f((float) -Math.sin(yaw), 0.35f, (float) Math.cos(yaw)).normalize(), BlackHoleEntity.STYLE_WARDEN, 1f, 0xFFE0A0, energy(entity));

        int beam = entity.beamTicks();
        if (beam <= 0) return;
        int elapsed = RiftWardenEntity.BEAM_TOTAL - beam;
        float eyeH = entity.getEyeHeight();
        Vec3 originWorld = entity.getPosition(partial).add(0, eyeH, 0);
        Vec3 dir = entity.beamDirection();
        Vec3 endWorld = originWorld.add(dir.scale(BEAM_LENGTH));
        BlockHitResult hit = entity.level().clip(new ClipContext(originWorld, endWorld, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
        if (hit.getType() != HitResult.Type.MISS) endWorld = hit.getLocation();

        PoseStack.Pose pose = ps.last();
        Vec3 eye = cameraLocal(pose);
        Vec3 from = new Vec3(0, eyeH, 0);
        Vec3 to = endWorld.subtract(entity.getPosition(partial));
        int color = energy(entity);
        float t = entity.tickCount + partial;
        if (elapsed < RiftWardenEntity.BEAM_CHARGE) {
            // charge: converging rings at the eye and a flickering targeting thread
            float charge = (elapsed + partial) / RiftWardenEntity.BEAM_CHARGE;
            for (int i = 0; i < 4; i++) {
                float k = ((t * 0.06f) + i / 4f) % 1f;
                FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0, eyeH, 0), new Vector3f((float) dir.x, (float) dir.y, (float) dir.z),
                        (1f - k) * 3.2f + 0.2f, 0.12f, color, k * charge, 40);
            }
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, eyeH, 0, 0.6f + charge * 2.2f, color, charge);
            if (((int) t & 3) != 0) FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, from, to, eye, 0.06f, color, 0.35f + 0.4f * charge);
            return;
        }
        // firing: layered beam with a white-hot core, spiral rings along it and an impact flare
        float flicker = 0.85f + 0.15f * (float) Math.sin(t * 2.3f);
        float fade = Math.min(1f, beam / 8f);
        FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, from, to, eye, 3.2f * fade, color, 0.35f * flicker);
        FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, from, to, eye, 1.6f * fade, ColorUtil.lerp(color, 0xFFFFFF, 0.4f), 0.8f * flicker);
        FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, from, to, eye, 0.55f * fade, 0xFFFFFF, 1f);
        double len = to.subtract(from).length();
        Vector3f n = new Vector3f((float) dir.x, (float) dir.y, (float) dir.z);
        for (int i = 0; i < 8; i++) {
            double d = ((t * 0.9 + i * len / 8) % len);
            Vec3 p = from.add(dir.scale(d));
            FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f((float) p.x, (float) p.y, (float) p.z), n, 1.3f * fade, 0.18f, color, 0.6f, 24);
        }
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, eyeH, 0, 2.6f * fade, color, 1f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, (float) to.x, (float) to.y, (float) to.z, 4.5f * fade * flicker, color, 1f);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, (float) to.x, (float) to.y, (float) to.z, 1.6f * fade, 0xFFFFFF, 1f);
    }

    @Override
    protected void draw(RiftWardenEntity entity, float partial, float age, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        int color = energy(entity);
        boolean slam = entity.isSlamming();
        ps.pushPose();
        ps.translate(0, hover(age), 0);
        PoseStack.Pose pose = ps.last();
        Vec3 eye = cameraLocal(pose);

        // vortex instead of legs: a tapering loft wrapped in spiralling ribbons
        Vector3f[] spine = {new Vector3f(0, 2.15f, 0), new Vector3f(0, 1.5f, -0.05f), new Vector3f(0, 0.9f, -0.1f), new Vector3f(0, 0.35f, -0.12f)};
        FxDraw.loft(buffers.getBuffer(RvRenderTypes.solid()), pose, spine, new float[]{0.75f, 0.5f, 0.28f, 0.05f}, new float[]{0.55f, 0.4f, 0.24f, 0.05f},
                new int[]{ARMOR_DARK, 0x2A1C0C, 0x1A1008, 0x100804}, new int[]{ARMOR_DARK, 0x2A1C0C, 0x1A1008, 0x100804}, 12, 1f, light, overlay);
        for (int k = 0; k < 5; k++) {
            Vec3[] pts = new Vec3[10];
            for (int i = 0; i < pts.length; i++) {
                float t = i / (float) (pts.length - 1);
                double a = age * 0.12 + k * Math.PI * 2 / 5 + t * 5;
                double r = 0.9 * (1 - t) + 0.1;
                pts[i] = new Vec3(Math.cos(a) * r, 2.0 - t * 2.1, Math.sin(a) * r);
            }
            FxDraw.ribbon(buffers.getBuffer(RvRenderTypes.ENERGY), pose, pts, eye, 0.22f, color, 0x6A2BFF, 0.7f);
        }

        // torso: armoured loft flaring into the shoulders, with an open chest cavity framed by energy
        Vector3f[] torso = {new Vector3f(0, 2.1f, 0), new Vector3f(0, 2.7f, 0.04f), new Vector3f(0, 3.4f, 0.06f), new Vector3f(0, 3.95f, 0)};
        FxDraw.loft(buffers.getBuffer(RvRenderTypes.solid()), pose, torso, new float[]{0.6f, 0.82f, 1.05f, 0.7f}, new float[]{0.45f, 0.55f, 0.62f, 0.45f},
                new int[]{ARMOR, ARMOR, 0xE6C46A, ARMOR}, new int[]{ARMOR_DARK, ARMOR_DARK, ARMOR_DARK, ARMOR_DARK}, 14, 1f, light, overlay);
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0, CHEST_Y, 0.58f), new Vector3f(0, 0.35f, 1f), 0.42f, 0.1f, color, 0.9f, 32);
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0, CHEST_Y, 0.6f), new Vector3f(0, 0.35f, 1f), 0.62f + 0.05f * (float) Math.sin(age * 0.3f), 0.05f, 0xFFE0A0, 0.6f, 32);
        // armour seams
        for (int s = -1; s <= 1; s += 2) {
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(s * 0.45f, 2.3f, 0.5f), new Vec3(s * 0.8f, 3.6f, 0.5f), eye, 0.06f, color, 0.8f);
        }

        // head: helm with crown spikes and a burning visor
        float headY = 4.3f;
        FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, 0, headY, 0.05f, 0.36f, 0.42f, 0.4f, 14, 0xE6C46A, ARMOR_DARK, 1f, light, overlay, -2f);
        for (int i = 0; i < 7; i++) {
            float a = (i - 3) * 0.32f;
            ps.pushPose();
            ps.translate(Mth.sin(a) * 0.32f, headY + 0.28f, -Mth.cos(a) * 0.1f);
            ps.mulPose(Axis.ZP.rotation(-a * 0.8f));
            ps.mulPose(Axis.XP.rotationDegrees(-15f));
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, 0.3f, 0, 0.07f, 0.42f - Math.abs(i - 3) * 0.07f, 0xF0D27A, 1f, light, overlay);
            ps.popPose();
        }
        FxDraw.cube(buffers.getBuffer(RvRenderTypes.glow()), pose, 0, headY + 0.08f, 0.43f, 0.24f, 0.04f, 0.02f, color, 1f, FxDraw.FULL_BRIGHT, overlay);
        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, 0, headY + 0.08f, 0.5f, 0.7f, color, 0.8f);

        // halo: counter-rotating rings and orbiting rune shards
        Vector3f haloC = new Vector3f(0, headY + 0.2f, -0.55f);
        for (int i = 0; i < 3; i++) {
            float a = age * (0.02f + i * 0.013f) * (i % 2 == 0 ? 1 : -1);
            Vector3f n = new Vector3f(Mth.sin(a) * 0.35f, Mth.cos(a) * 0.35f, 1f);
            FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, haloC, n, 1.1f + i * 0.38f, 0.08f - i * 0.015f, i == 1 ? 0xFFE0A0 : color, 0.8f, 48);
        }
        for (int i = 0; i < 8; i++) {
            double a = age * 0.03 + i * Math.PI / 4;
            float x = (float) Math.cos(a) * 1.9f, y = haloC.y + (float) Math.sin(a) * 1.9f;
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), pose, x, y, haloC.z - 0.1f, 0.07f, 0.16f, 0xFFE9B0, 1f, FxDraw.FULL_BRIGHT, overlay);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, x, y, haloC.z - 0.1f, 0.25f, color, 0.6f);
        }

        // floating pauldrons and detached gauntlets
        float raise = slam ? 1f : 0f;
        float attack = entity.getAttackAnim(partial);
        for (int s = -1; s <= 1; s += 2) {
            float bob = (float) Math.sin(age * 0.07f + s) * 0.08f;
            FxDraw.ellipsoid(buffers.getBuffer(RvRenderTypes.solid()), pose, s * 1.4f, 3.85f + bob, 0, 0.55f, 0.35f, 0.5f, 12, 0xE6C46A, ARMOR_DARK, 1f, light, overlay, -0.3f);
            ps.pushPose();
            ps.translate(s * 1.55f, 3.95f + bob, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(s * -30f));
            FxDraw.crystal(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), 0, 0.35f, 0, 0.12f, 0.45f, 0xF0D27A, 1f, light, overlay);
            ps.popPose();

            float angle = Mth.lerp(raise, 0.35f + attack * 1.3f, 2.9f);
            Vector3f shoulder = new Vector3f(s * 1.45f, 3.55f + bob, 0);
            Vector3f fist = new Vector3f(shoulder).add(s * 0.35f, -2.0f * Mth.cos(angle), 2.0f * Mth.sin(angle) * 0.8f);
            FxDraw.limb(buffers.getBuffer(RvRenderTypes.solid()), pose,
                    new Vector3f(fist).lerp(shoulder, 0.55f), fist, 0.3f, 0.36f, ARMOR, 0xE6C46A, 1f, light, overlay);
            FxDraw.cube(buffers.getBuffer(RvRenderTypes.solid()), pose, fist.x, fist.y, fist.z, 0.36f, 0.36f, 0.36f, ARMOR_DARK, 1f, light, overlay);
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vec3(shoulder.x, shoulder.y, shoulder.z),
                    new Vec3(fist.x, fist.y, fist.z).lerp(new Vec3(shoulder.x, shoulder.y, shoulder.z), 0.55), eye, 0.18f, color, 0.6f);
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), pose, fist.x, fist.y, fist.z, slam ? 2.2f : 0.9f, color, slam ? 1f : 0.5f);
        }
        ps.popPose();
    }
}
