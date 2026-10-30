package dev.infinitygauntlet.client.breach;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.network.SurveyPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A miniature (aligned with the world's compass, like the panel's movement arrows) of the blocks around the real target, refreshed every two seconds from the server,
 * with a tiny copy of the target standing where it really is. Only blocks with an exposed face are kept.
 */
public final class SurveyHologram {
    private record Cell(int x, int y, int z, BlockState state) {
    }

    private record Snapshot(List<Cell> cells, int sx, int sy, int sz, Vec3 target) {
    }

    private static final Map<Integer, Snapshot> SNAPSHOTS = new HashMap<>();
    private static final float SCALE = 0.12f;

    public static void accept(SurveyPayload p) {
        int sx = p.sizeX(), sy = p.sizeY(), sz = p.sizeZ();
        if (p.states().size() != sx * sy * sz) {
            return;
        }
        BlockState[] states = new BlockState[sx * sy * sz];
        for (int i = 0; i < states.length; i++) {
            states[i] = Block.stateById(p.states().get(i));
        }
        List<Cell> cells = new ArrayList<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    BlockState s = states[(y * sz + z) * sx + x];
                    if (s.isAir() || s.getRenderShape() != RenderShape.MODEL) {
                        continue;
                    }
                    if (exposed(states, x, y, z, sx, sy, sz)) {
                        cells.add(new Cell(x, y, z, s));
                    }
                }
            }
        }
        SNAPSHOTS.put(p.specimenId(), new Snapshot(cells, sx, sy, sz, new Vec3(p.targetX(), p.targetY(), p.targetZ())));
    }

    private static boolean exposed(BlockState[] s, int x, int y, int z, int sx, int sy, int sz) {
        int[][] d = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] o : d) {
            int nx = x + o[0], ny = y + o[1], nz = z + o[2];
            if (nx < 0 || ny < 0 || nz < 0 || nx >= sx || ny >= sy || nz >= sz) {
                if (o[1] != -1) {
                    return true;
                }
                continue;
            }
            BlockState n = s[(ny * sz + nz) * sx + nx];
            if (n.isAir() || !n.canOcclude()) {
                return true;
            }
        }
        return false;
    }

    public static void render(int specimenId, LivingEntity dummy, float t, PoseStack pose, MultiBufferSource buffers) {
        Snapshot snap = SNAPSHOTS.get(specimenId);
        // projector base
        VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                0.55f, 0.85f, 40, t * 0.01f, 0.35f, 0.9f, 1f, 0.8f);
        VfxDraw.plane(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLYPH_SPACE)), new Vec3(1, 0, 0), new Vec3(0, 0, 1),
                1.6f, 0.3f, 0.7f, 1f, 0.5f);
        if (snap == null) {
            return;
        }
        BlockRenderDispatcher blocks = Minecraft.getInstance().getBlockRenderer();
        pose.pushPose();
        pose.translate(0, 0.08, 0);
        pose.scale(SCALE, SCALE, SCALE);
        pose.translate(-snap.sx / 2.0, 0, -snap.sz / 2.0);
        for (Cell c : snap.cells) {
            pose.pushPose();
            pose.translate(c.x, c.y, c.z);
            blocks.renderSingleBlock(c.state, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        if (dummy != null) {
            Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, snap.target.x, snap.target.y, snap.target.z, 0, 0, pose, buffers,
                    LightTexture.FULL_BRIGHT);
            VfxDraw.billboard(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW)),
                    Minecraft.getInstance().gameRenderer.getMainCamera().rotation(), (float) snap.target.x, (float) snap.target.y + 1,
                    (float) snap.target.z, 3f, 0, 1f, 0.3f, 0.6f, 0.6f + 0.3f * (float) Math.sin(t * 0.3));
        }
        pose.popPose();
        // scan plane sweeping up through the miniature
        float h = snap.sy * SCALE;
        float y = 0.08f + (t * 0.02f % 1f) * h;
        pose.pushPose();
        pose.translate(0, y, 0);
        VfxDraw.ring(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.RING)), new Vec3(1, 0, 0), new Vec3(0, 0, 1), 0,
                snap.sx * SCALE * 0.72f, 32, 0, 0.3f, 0.85f, 1f, 0.25f);
        pose.popPose();
    }

    public static void clear() {
        SNAPSHOTS.clear();
    }

    private SurveyHologram() {
    }
}
