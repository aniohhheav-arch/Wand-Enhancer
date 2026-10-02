package dev.mysticarts.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.render.MaDraw;
import dev.mysticarts.client.render.MaRenderTypes;
import dev.mysticarts.client.render.WarpFx;
import dev.mysticarts.entity.SlingPortalEntity;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/**
 * A sling-ring portal: a ring of churning sparks around a refracting window that glimpses the destination. Space
 * Stone portals are blue; redirect portals carry a warding mandala; rifts are huge and layered.
 */
public class SlingPortalRenderer extends EntityRenderer<SlingPortalEntity> {
    public SlingPortalRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SlingPortalEntity entity) {
        return MaRenderTypes.WHITE;
    }

    @Override
    public boolean shouldRender(SlingPortalEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(x, y, z));
    }

    @Override
    public void render(SlingPortalEntity p, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = p.openness(partial);
        if (open <= 0.01f) return;
        float time = p.tickCount + partial;
        float r = p.radius() * open;
        Vec3 n = p.normal(), up = p.up(), right = p.right();
        Vector3f c = new Vector3f();
        Vector3f nv = MaDraw.vec(n);
        PoseStack.Pose pose = ps.last();
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        int col = p.color();
        int seed = p.getId() & 511;
        // the burning rim, doubled for density, spinning opposite ways
        MaDraw.rune(rune, pose, c, nv, r * 1.12f, time * 0.08f, MaDraw.RIM, seed, col, 1f);
        MaDraw.rune(rune, pose, new Vector3f(nv).mul(0.02f), nv, r * 1.06f, -time * 0.05f, MaDraw.RIM, seed + 1, ColorUtil.lerp(col, 0xFFFFFF, 0.3f), 0.8f);
        switch (p.kind()) {
            case SlingPortalEntity.KIND_REDIRECT -> {
                MaDraw.rune(rune, pose, new Vector3f(nv).mul(0.03f), nv, r * 0.9f, time * 0.04f, MaDraw.SHIELD, seed + 2, col, 0.8f);
                return;
            }
            case SlingPortalEntity.KIND_RIFT -> {
                for (int i = 0; i < 3; i++) {
                    MaDraw.rune(rune, pose, new Vector3f(nv).mul(-0.1f * i), nv, r * (1.3f + i * 0.25f), time * (0.02f + i * 0.015f) * (i % 2 == 0 ? 1 : -1),
                            MaDraw.SIGIL, seed + 3 + i, ColorUtil.lerp(col, 0x9B2CFF, i * 0.3f), 0.5f - i * 0.12f);
                }
            }
            default -> {}
        }
        // the interior: a faint vortex over the refracted view of the destination
        MaDraw.rune(rune, pose, new Vector3f(nv).mul(-0.01f), nv, r * 0.98f, time * 0.03f, MaDraw.VORTEX, seed + 9, ColorUtil.lerp(col, p.viewColor(), 0.6f), 0.35f);
        Vec3 world = p.getPosition(partial);
        WarpFx.disc(world, right.scale(r * 0.98), up.scale(r * 0.98), WarpFx.PORTAL, 1f, open, p.viewColor());
    }
}
