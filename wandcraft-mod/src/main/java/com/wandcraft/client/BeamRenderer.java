package com.wandcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.wandcraft.WandCraft;
import com.wandcraft.entity.SpellBeam;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import com.mojang.math.Axis;

/** Draws {@link SpellBeam} as nested rotating translucent tubes, like a beacon beam that follows the wand. */
public class BeamRenderer extends EntityRenderer<SpellBeam> {
    private static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(WandCraft.ID, "textures/entity/arcane_beam.png");
    private static final int FULL_BRIGHT = 15728880;

    public BeamRenderer(EntityRendererProvider.Context ctx) { super(ctx); }

    @Override public ResourceLocation getTextureLocation(SpellBeam e) { return TEX; }

    @Override public boolean shouldRender(SpellBeam e, Frustum f, double x, double y, double z) { return true; }

    @Override
    public void render(SpellBeam beam, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity owner = beam.level().getEntity(beam.ownerId());
        if (owner == null) return;
        Vec3 from = SpellBeam.start(owner, pt);
        Vec3 to = beam.tip(owner, pt);
        Vec3 d = to.subtract(from);
        double len = d.length();
        if (len < 0.2) return;
        Vec3 n = d.scale(1 / len);

        Vec3 base = new Vec3(Mth.lerp(pt, beam.xo, beam.getX()), Mth.lerp(pt, beam.yo, beam.getY()), Mth.lerp(pt, beam.zo, beam.getZ()));
        pose.pushPose();
        pose.translate(from.x - base.x, from.y - base.y, from.z - base.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0, 1, 0), new Vector3f((float) n.x, (float) n.y, (float) n.z)));

        float time = beam.tickCount + pt;
        float power = beam.power();
        int rgb = beam.color();
        float pulse = 1f + 0.12f * Mth.sin(time * 1.3f);
        VertexConsumer vc = buffers.getBuffer(RenderType.beaconBeam(TEX, true));

        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 9f));
        tube(pose, vc, 0.035f * pulse * (1f + 0.5f * power), (float) len, -time * 0.09f, 0xFFFFFFFF);
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-time * 5f));
        tube(pose, vc, 0.075f * pulse * (1f + 0.5f * power), (float) len, -time * 0.05f, 0xD0000000 | (rgb & 0xFFFFFF));
        pose.popPose();
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(time * 2.5f + 30f));
        tube(pose, vc, 0.13f * pulse * (1f + 0.6f * power), (float) len, time * 0.03f, 0x70000000 | (rgb & 0xFFFFFF));
        pose.popPose();
        // Wide soft sheath so the beam reads as glowing even against bright skies.
        tube(pose, vc, 0.2f * pulse * (1f + 0.7f * power), (float) len, time * 0.015f, 0x28000000 | (rgb & 0xFFFFFF));
        pose.popPose();
    }

    private static final int SIDES = 8;

    /** Octagonal tube along +Y from 0 to {@code len}, emitted with both windings. */
    private static void tube(PoseStack pose, VertexConsumer vc, float r, float len, float scroll, int argb) {
        PoseStack.Pose p = pose.last();
        float v0 = scroll, v1 = scroll + len * 1.2f;
        for (int i = 0; i < SIDES; i++) {
            double a0 = Math.PI * 2 * i / SIDES, a1 = Math.PI * 2 * (i + 1) / SIDES;
            float x0 = (float) Math.cos(a0) * r, z0 = (float) Math.sin(a0) * r, x1 = (float) Math.cos(a1) * r, z1 = (float) Math.sin(a1) * r;
            float u0 = i / (float) SIDES, u1 = (i + 1) / (float) SIDES;
            vertex(p, vc, x0, 0, z0, u0, v1, argb);
            vertex(p, vc, x0, len, z0, u0, v0, argb);
            vertex(p, vc, x1, len, z1, u1, v0, argb);
            vertex(p, vc, x1, 0, z1, u1, v1, argb);
            vertex(p, vc, x1, 0, z1, u1, v1, argb);
            vertex(p, vc, x1, len, z1, u1, v0, argb);
            vertex(p, vc, x0, len, z0, u0, v0, argb);
            vertex(p, vc, x0, 0, z0, u0, v1, argb);
        }
    }

    private static void vertex(PoseStack.Pose p, VertexConsumer vc, float x, float y, float z, float u, float v, int argb) {
        vc.addVertex(p, x, y, z).setColor(argb).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(FULL_BRIGHT).setNormal(p, 0, 1, 0);
    }
}
