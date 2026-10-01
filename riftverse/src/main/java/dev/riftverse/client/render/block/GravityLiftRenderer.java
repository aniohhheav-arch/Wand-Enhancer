package dev.riftverse.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.block.GravityLiftBlockEntity;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.entity.ProceduralRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** A column of inverted gravity: a soft light shaft with rings drifting upward through it. */
public class GravityLiftRenderer implements BlockEntityRenderer<GravityLiftBlockEntity> {
    private static final int COLOR = 0x7DF9FF;
    private static final int ACCENT = 0x8F6BFF;

    public GravityLiftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(GravityLiftBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (Minecraft.getInstance().level == null) return;
        float t = Minecraft.getInstance().level.getGameTime() + partial;
        int range = GravityLiftBlockEntity.RANGE;
        PoseStack.Pose pose = ps.last();
        Vec3 eye = ProceduralRenderer.cameraLocal(pose);
        Vec3 bottom = new Vec3(0.5, 1.0, 0.5);
        Vec3 top = new Vec3(0.5, 1.0 + range, 0.5);
        FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, bottom, top, eye, 0.9f, COLOR, 0.12f);
        FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), pose, bottom, bottom.add(0, range * 0.4, 0), eye, 0.35f, 0xE0FFFF, 0.25f);
        Vector3f up = new Vector3f(0, 1, 0);
        for (int i = 0; i < 6; i++) {
            float k = ((t * 0.012f) + i / 6f) % 1f;
            float y = 1.0f + k * range;
            float fade = (float) Math.sin(k * Math.PI);
            FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0.5f, y, 0.5f), up, 0.42f - k * 0.12f, 0.05f, i % 2 == 0 ? COLOR : ACCENT, 0.7f * fade, 24);
        }
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), pose, new Vector3f(0.5f, 1.02f, 0.5f), up, 0.44f, 0.08f, COLOR, 0.6f + 0.2f * (float) Math.sin(t * 0.2f), 24);
    }

    @Override
    public boolean shouldRenderOffScreen(GravityLiftBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(GravityLiftBlockEntity be) {
        return new AABB(be.getBlockPos()).expandTowards(0, GravityLiftBlockEntity.RANGE + 1, 0);
    }
}
