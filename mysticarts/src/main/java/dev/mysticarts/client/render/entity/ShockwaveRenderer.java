package dev.mysticarts.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.render.MaDraw;
import dev.mysticarts.client.render.MaRenderTypes;
import dev.mysticarts.client.render.WarpFx;
import dev.mysticarts.entity.ShockwaveEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** The visible front of a shockwave: a runic band with a refracting ripple behind it. */
public class ShockwaveRenderer extends EntityRenderer<ShockwaveEntity> {
    public ShockwaveRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(ShockwaveEntity entity) {
        return MaRenderTypes.WHITE;
    }

    @Override
    public boolean shouldRender(ShockwaveEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ShockwaveEntity w, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        float age = w.tickCount + partial;
        float r = w.radiusAt(age);
        float fade = 1f - Math.min(1f, age / w.duration());
        PoseStack.Pose pose = ps.last();
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        int col = w.color();
        int seed = w.getId() & 511;
        Vector3f c = new Vector3f(0, 0.15f, 0);
        if (w.style() == ShockwaveEntity.GROUND) {
            MaDraw.bandRing(rune, pose, c, new Vector3f(0, 1, 0), r, 0.9f, seed, col, fade, 72);
            MaDraw.bandRing(rune, pose, new Vector3f(0, 0.6f, 0), new Vector3f(0, 1, 0), r * 0.97f, 0.4f, seed + 1, col, fade * 0.5f, 72);
            MaDraw.rune(rune, pose, c, new Vector3f(0, 1, 0), r, 0, MaDraw.RIPPLE, seed + 2, col, fade * 0.5f);
        } else {
            for (int i = 0; i < 3; i++) {
                Vector3f n = i == 0 ? new Vector3f(0, 1, 0) : i == 1 ? new Vector3f(1, 0.3f, 0) : new Vector3f(0, 0.3f, 1);
                MaDraw.bandRing(rune, pose, new Vector3f(), n, r, 0.6f, seed + i, col, fade, 64);
            }
            MaDraw.runeBillboard(rune, pose, new Vector3f(), r, 0, MaDraw.RIPPLE, seed + 4, col, fade * 0.4f);
        }
        WarpFx.sphere(w.getPosition(partial), Math.max(0.5f, r), WarpFx.RIPPLE, 1.4f, Math.min(1f, age / w.duration()), col);
    }
}
