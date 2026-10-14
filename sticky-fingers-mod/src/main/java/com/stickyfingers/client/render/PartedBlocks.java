package com.stickyfingers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stickyfingers.entity.ZipperEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.joml.Matrix4f;

/**
 * Draws the blocks a zipper separated, squeezed against their own side of the seam like parted fabric.
 * The real blocks are gone while open; this keeps the surface visibly intact, just unzipped.
 */
final class PartedBlocks {
    static void render(ZipperEntity z, PoseStack ps, MultiBufferSource buffers, Vec3 here, float open) {
        long[] positions = z.separatedPositions();
        if (positions.length == 0 || open <= 0.02f) return;
        int[] states = z.separatedStates();
        Vec3 n = z.normal(), d = z.direction();
        Vec3 s = n.cross(d).normalize();
        Vec3 c = z.position();
        float f = 1f - 0.82f * open;
        double half = 0.5 * (Math.abs(s.x) + Math.abs(s.y) + Math.abs(s.z));
        var level = Minecraft.getInstance().level;
        var blocks = Minecraft.getInstance().getBlockRenderer();
        for (int i = 0; i < positions.length; i++) {
            BlockState state = Block.stateById(states[i]);
            if (state.getRenderShape() != RenderShape.MODEL) continue;
            BlockPos pos = BlockPos.of(positions[i]);
            Vec3 bc = Vec3.atCenterOf(pos);
            double side = bc.subtract(c).dot(s) >= 0 ? 1 : -1;
            // squeeze toward the outer edge of this block's half of the opening
            Vec3 outer = bc.add(s.scale(side * half)).subtract(here);
            Matrix4f m = new Matrix4f().translation((float) outer.x, (float) outer.y, (float) outer.z)
                    .mul(scaleAlong(s, f))
                    .translate((float) -outer.x, (float) -outer.y, (float) -outer.z)
                    .translate((float) (pos.getX() - here.x), (float) (pos.getY() - here.y), (float) (pos.getZ() - here.z));
            ps.pushPose();
            ps.last().pose().mul(m);
            int light = LevelRenderer.getLightColor(level, pos.relative(z.getDirection().getOpposite()));
            light = Math.max(light, LevelRenderer.getLightColor(level, BlockPos.containing(c)));
            blocks.renderSingleBlock(state, ps, buffers, light, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            ps.popPose();
        }
    }

    /** I + (f - 1) s sᵀ: scales by f along unit vector s only. */
    private static Matrix4f scaleAlong(Vec3 s, float f) {
        float k = f - 1f;
        float x = (float) s.x, y = (float) s.y, z = (float) s.z;
        return new Matrix4f(
                1 + k * x * x, k * x * y, k * x * z, 0,
                k * y * x, 1 + k * y * y, k * y * z, 0,
                k * z * x, k * z * y, 1 + k * z * z, 0,
                0, 0, 0, 1);
    }

    private PartedBlocks() {}
}
