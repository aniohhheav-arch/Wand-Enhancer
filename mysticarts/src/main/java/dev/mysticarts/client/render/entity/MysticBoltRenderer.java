package dev.mysticarts.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.render.FxDraw;
import dev.mysticarts.client.render.MaDraw;
import dev.mysticarts.client.render.MaRenderTypes;
import dev.mysticarts.client.render.WarpFx;
import dev.mysticarts.entity.MysticBoltEntity;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Spell projectiles: a white-hot core, a coloured corona and a streaming tail; orbs carry spinning sigils. */
public class MysticBoltRenderer extends EntityRenderer<MysticBoltEntity> {
    public MysticBoltRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(MysticBoltEntity entity) {
        return MaRenderTypes.WHITE;
    }

    @Override
    public void render(MysticBoltEntity b, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        PoseStack.Pose pose = ps.last();
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        VertexConsumer energy = buffers.getBuffer(MaRenderTypes.ENERGY);
        float time = b.tickCount + partial;
        float size = b.size();
        int col = b.color();
        int seed = b.getId() & 511;
        Vector3f c = new Vector3f(0, 0, 0);
        Vec3 v = b.getDeltaMovement();
        Vec3 eye = ProceduralRenderer.cameraLocal(pose);
        if (b.kind() == MysticBoltEntity.SOUL_PROJECTION) {
            // a spectral silhouette of the caster striding forward
            MaDraw.runeBillboard(rune, pose, new Vector3f(0, 0.9f, 0), 1.1f, 0, MaDraw.HALO, seed, col, 0.55f);
            FxDraw.beam(energy, pose, new Vec3(0, 0.1, 0), new Vec3(0, 1.8, 0), eye, 0.55f, col, 0.6f);
            MaDraw.runeBillboard(rune, pose, new Vector3f(0, 1.6f, 0), 0.35f, 0, MaDraw.STAR, seed + 1, ColorUtil.lerp(col, 0xFFFFFF, 0.5f), 0.9f);
            FxDraw.beam(energy, pose, new Vec3(0, 0.9, 0), v.scale(-5).add(0, 0.9, 0), eye, 0.8f, col, 0.3f);
            return;
        }
        float pulse = 1f + 0.15f * (float) Math.sin(time * 1.3);
        MaDraw.runeBillboard(rune, pose, c, size * 2.2f * pulse, 0, MaDraw.HALO, seed, col, 0.8f);
        MaDraw.runeBillboard(rune, pose, c, size * 1.1f, time * 0.3f, MaDraw.STAR, seed + 1, ColorUtil.lerp(col, 0xFFFFFF, 0.4f), 1f);
        if (v.lengthSqr() > 1e-4) {
            Vec3 tail = v.normalize().scale(-(1.5 + size * 3));
            FxDraw.beam(energy, pose, Vec3.ZERO, tail, eye, size * 0.9f, col, 0.9f);
            FxDraw.beam(energy, pose, Vec3.ZERO, tail.scale(0.6), eye, size * 0.35f, 0xFFFFFF, 0.8f);
        }
        if (b.kind() == MysticBoltEntity.POWER_ORB || b.kind() == MysticBoltEntity.MYSTIC_CHARGED || b.kind() == MysticBoltEntity.POWER) {
            Vector3f n = v.lengthSqr() > 1e-4 ? MaDraw.vec(v.normalize()) : new Vector3f(0, 1, 0);
            MaDraw.rune(rune, pose, c, n, size * 1.8f, time * 0.25f, MaDraw.SIGIL, seed + 2, col, 0.8f);
            MaDraw.bandRing(rune, pose, c, new Vector3f(n).add(0.5f, 0.3f, 0), size * 1.4f, size * 0.25f, seed + 3, col, 0.7f, 20);
            if (b.kind() == MysticBoltEntity.POWER_ORB) WarpFx.sphere(b.getPosition(partial), size * 2.2f, WarpFx.HAZE, 1f, 0.5f, col);
        }
    }
}
