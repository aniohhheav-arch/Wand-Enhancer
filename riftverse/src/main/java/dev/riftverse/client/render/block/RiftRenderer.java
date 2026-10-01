package dev.riftverse.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.block.RiftBlockEntity;
import dev.riftverse.block.RiftType;
import dev.riftverse.client.ClientEffects;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.SpatialFx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Natural rifts: a vertical tear that always turns its face to the viewer, rendered by the lensing pass. */
public class RiftRenderer implements BlockEntityRenderer<RiftBlockEntity> {
    private static final float HALF_W = 0.6f;
    private static final float HALF_H = 1.4f;
    private static final double LIFT = 0.35;

    public RiftRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(RiftBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        RiftType type = be.type();
        Vec3 center = Vec3.atCenterOf(be.getBlockPos()).add(0, LIFT, 0);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 toCam = cam.subtract(center);
        Vec3 flat = new Vec3(toCam.x, 0, toCam.z);
        Vec3 normal = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();

        float open = Mth.lerp(partial, be.openProgressO, be.openProgress);
        open = open * open * (3f - 2f * open);
        long expire = be.expireAt();
        if (expire > 0) {
            float remaining = (float) (expire - mc.level.getGameTime()) - partial;
            open *= Mth.clamp(remaining / 40f, 0f, 1f);
        }
        if (open <= 0.001f) return;
        float breathe = 1f + 0.04f * (float) Math.sin((mc.level.getGameTime() + partial) * 0.11 + be.seed % 100);

        SpatialFx.rift(center, normal, new Vec3(0, 1, 0), HALF_W * breathe, HALF_H, type.colorA, type.colorB, type.ordinal(), open, SpatialFx.SHAPE_TEAR, 1f,
                type.colorB, type == RiftType.RETURN ? 0.6f : 0.35f);
        ClientEffects.reportRift(center, toCam.length());

        // light spilling onto the ground beneath the tear
        float t = mc.level.getGameTime() + partial;
        Vector3f ground = new Vector3f(0.5f, 0.03f, 0.5f);
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), ground, new Vector3f(0, 1, 0), 1.1f * open, 0.35f, type.colorA, 0.35f * open, 32);
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), ground, new Vector3f(0, 1, 0), (0.3f + (t * 0.02f) % 1f * 1.6f) * open, 0.08f, type.colorB,
                0.5f * (1f - (t * 0.02f) % 1f) * open, 32);
    }

    @Override
    public boolean shouldRenderOffScreen(RiftBlockEntity be) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(RiftBlockEntity be) {
        return new AABB(be.getBlockPos()).inflate(3.0, 4.0, 3.0);
    }
}
