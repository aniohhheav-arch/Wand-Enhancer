package com.stickyfingers.client.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Client-side expanding ring VFX: spawn a ring and it expands outward, fading.
 * Used for portal activations, time-stop begin/end, and finisher moves.
 */
public final class VfxRings {
    private static final int SEGMENTS = 48;

    private record Ring(Vec3 pos, float maxRadius, float speed, float r, float g, float b,
                        float lifeTicks, float[] tick) {}

    private static final List<Ring> RINGS = new ArrayList<>();

    /** Spawn a ring at world position. r,g,b are 0-1 color. */
    public static void spawn(Vec3 pos, float maxRadius, float speed, float r, float g, float b, int lifeTicks) {
        RINGS.add(new Ring(pos, maxRadius, speed, r, g, b, lifeTicks, new float[]{0}));
    }

    public static void tick() {
        Iterator<Ring> it = RINGS.iterator();
        while (it.hasNext()) {
            Ring ring = it.next();
            ring.tick()[0]++;
            if (ring.tick()[0] >= ring.lifeTicks()) it.remove();
        }
    }

    public static void render(PoseStack ps, MultiBufferSource buf, Vec3 camPos, float partial) {
        if (RINGS.isEmpty()) return;
        for (Ring ring : RINGS) {
            float t = ring.tick()[0] + partial;
            float progress = t / ring.lifeTicks();
            float currentRadius = ring.maxRadius() * Mth.clamp(progress * ring.speed(), 0, 1);
            float alpha = (1f - progress) * 0.85f;
            if (alpha <= 0.01f) continue;

            ps.pushPose();
            Vec3 offset = ring.pos().subtract(camPos);
            ps.translate(offset.x, offset.y, offset.z);

            VertexConsumer vc = buf.getBuffer(RenderType.lines());
            Matrix4f m = ps.last().pose();
            for (int i = 0; i < SEGMENTS; i++) {
                float a0 = (float) (i * 2 * Math.PI / SEGMENTS);
                float a1 = (float) ((i + 1) * 2 * Math.PI / SEGMENTS);
                vc.addVertex(m, Mth.cos(a0) * currentRadius, 0, Mth.sin(a0) * currentRadius)
                        .setColor(ring.r(), ring.g(), ring.b(), alpha).setNormal(0, 1, 0);
                vc.addVertex(m, Mth.cos(a1) * currentRadius, 0, Mth.sin(a1) * currentRadius)
                        .setColor(ring.r(), ring.g(), ring.b(), alpha * 0.7f).setNormal(0, 1, 0);
            }
            ps.popPose();
        }
    }

    private VfxRings() {}
}
