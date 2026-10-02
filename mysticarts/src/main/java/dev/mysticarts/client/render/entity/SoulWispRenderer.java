package dev.mysticarts.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.render.MaDraw;
import dev.mysticarts.client.render.MaRenderTypes;
import dev.mysticarts.entity.SoulWispEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

public class SoulWispRenderer extends EntityRenderer<SoulWispEntity> {
    public SoulWispRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SoulWispEntity entity) {
        return MaRenderTypes.WHITE;
    }

    @Override
    public void render(SoulWispEntity w, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        float t = w.tickCount + partial;
        float pulse = 1f + 0.2f * (float) Math.sin(t * 0.4f + w.slot());
        MaDraw.runeBillboard(rune, ps.last(), new Vector3f(), 0.45f * pulse, 0, MaDraw.HALO, w.getId() & 255, 0xFF7A12, 0.9f);
        MaDraw.runeBillboard(rune, ps.last(), new Vector3f(), 0.22f, t * 0.2f, MaDraw.STAR, (w.getId() + 1) & 255, 0xFFE0B0, 1f);
    }
}
