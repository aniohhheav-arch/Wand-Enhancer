package dev.riftverse.client.render.block;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.riftverse.block.PortalFieldBlockEntity;
import dev.riftverse.client.ClientEffects;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.client.render.SpatialFx;
import dev.riftverse.client.render.entity.ProceduralRenderer;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Built gates and Nexus gateways. Only the controller (lowest corner) renders: one window spanning the whole frame,
 * showing a glimpse of the destination's sky, with a floating label naming where it leads.
 */
public class PortalFieldRenderer implements BlockEntityRenderer<PortalFieldBlockEntity> {
    private final Font font;

    public PortalFieldRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(PortalFieldBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (!be.isController()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        int w = be.width(), h = be.height();
        boolean alongX = be.axis() == Direction.Axis.X;
        BlockPos origin = be.getBlockPos();
        // local (relative to the controller block's corner) and world centre of the membrane
        double lx = alongX ? w * 0.5 : 0.5;
        double lz = alongX ? 0.5 : w * 0.5;
        double ly = h * 0.5;
        Vec3 center = new Vec3(origin.getX() + lx, origin.getY() + ly, origin.getZ() + lz);
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        Vec3 normal = alongX ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        if (cam.subtract(center).dot(normal) < 0) normal = normal.scale(-1);

        int color = be.color();
        SpatialFx.rift(center, normal, new Vec3(0, 1, 0), w * 0.5f, h * 0.5f, color, ColorUtil.lerp(color, 0xFFFFFF, 0.6f), SpatialFx.STYLE_GATE, 1f,
                SpatialFx.SHAPE_RECT, 1f, color, 0.85f);
        double dist = cam.distanceTo(center);
        ClientEffects.reportRift(center, Math.max(0, dist - Math.max(w, h) * 0.3));

        // inner edge glow tracing the frame
        float t = mc.level.getGameTime() + partial;
        Vec3 eye = ProceduralRenderer.cameraLocal(ps.last());
        Vec3 right = alongX ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        Vec3 base = new Vec3(alongX ? 0 : 0.5, 0, alongX ? 0.5 : 0);
        Vec3[] corners = {base, base.add(right.scale(w)), base.add(right.scale(w)).add(0, h, 0), base.add(0, h, 0)};
        float pulse = 0.55f + 0.25f * (float) Math.sin(t * 0.12f);
        for (int i = 0; i < 4; i++) {
            FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), corners[i], corners[(i + 1) % 4], eye, 0.22f, color, pulse);
        }

        String label = be.label();
        if (!label.isEmpty() && dist < 32) {
            ps.pushPose();
            ps.translate(lx, h + 0.7, lz);
            ps.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
            float s = 0.03f;
            ps.scale(s, -s, s);
            float x = -font.width(label) / 2f;
            int alpha = (int) (255 * Math.min(1.0, (32 - dist) / 8.0));
            int text = (Math.max(alpha, 8) << 24) | (ColorUtil.lerp(color, 0xFFFFFF, 0.55f) & 0xFFFFFF);
            int background = (Math.max(alpha / 3, 0) << 24);
            font.drawInBatch(label, x, 0, text, false, ps.last().pose(), buffers, Font.DisplayMode.NORMAL, background, LightTexture.FULL_BRIGHT);
            ps.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(PortalFieldBlockEntity be) {
        return be.isController();
    }

    @Override
    public int getViewDistance() {
        return 128;
    }

    @Override
    public AABB getRenderBoundingBox(PortalFieldBlockEntity be) {
        BlockPos p = be.getBlockPos();
        if (!be.isController()) return new AABB(p);
        boolean alongX = be.axis() == Direction.Axis.X;
        int w = be.width(), h = be.height();
        return new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + (alongX ? w : 1), p.getY() + h + 1.5, p.getZ() + (alongX ? 1 : w)).inflate(2.0);
    }
}
