package com.stickyfingers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stickyfingers.client.ClientState;
import com.stickyfingers.entity.ZipperEntity;
import com.stickyfingers.entity.ZipperKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/** Renders every zipper kind, including chains, mob seams and the Zipper World field. */
public class ZipperRenderer extends EntityRenderer<ZipperEntity> {
    private record Seam(Vec3 pos, Vec3 normal, float angle, float length, float delay) {}

    private static final Map<ZipperEntity, List<Seam>> FIELD_CACHE = new WeakHashMap<>();

    public ZipperRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override
    public boolean shouldRender(ZipperEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.distanceToSqr(x, y, z) < 128 * 128;
    }

    @Override
    public void render(ZipperEntity z, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        float open = z.getOpenProgress(partial);
        float appear = z.getAppear(partial);
        float time = z.tickCount + partial;
        Vec3 here = new Vec3(Mth.lerp(partial, z.xo, z.getX()), Mth.lerp(partial, z.yo, z.getY()), Mth.lerp(partial, z.zo, z.getZ()));
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        ZipperKind kind = z.kind();
        PartedBlocks.render(z, ps, buffers, here, open);

        switch (kind) {
            case CHAIN -> {
                Entity target = z.attachedEntity();
                if (target == null) return;
                ClientState.markUnzipped(target.getId());
                Vec3 tp = target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0);
                Vec3 anchor = Vec3.atCenterOf(z.anchor());
                Vec3 mid = anchor.lerp(tp, 0.5);
                Vec3 d = tp.subtract(anchor);
                float len = (float) d.length();
                if (len < 0.1f) return;
                d = d.normalize();
                Vec3 n = facing(cam.subtract(mid), d);
                ZipperDraw.draw(ps, buffers, mid.subtract(here), n, d, len, 0.3f, 0.12f + 0.05f * Mth.sin(time * 0.4f), appear, light, 1f, time);
            }
            case MOB, BODY -> {
                Entity target = z.attachedEntity();
                if (target == null) return;
                if (kind == ZipperKind.MOB && open > 0.05f) ClientState.markUnzipped(target.getId());
                if (kind == ZipperKind.BODY) ClientState.markBody(target.getId());
                Vec3 tp = target.getPosition(partial);
                Vec3 toCam = cam.subtract(tp).multiply(1, 0, 1);
                Vec3 n = toCam.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : toCam.normalize();
                Vec3 side = new Vec3(0, 1, 0).cross(n).normalize();
                float w = target.getBbWidth();
                if (kind == ZipperKind.MOB) {
                    // a horizontal seam across the middle and a diagonal one across the chest
                    Vec3 c1 = tp.add(0, target.getBbHeight() * 0.55, 0).add(n.scale(w * 0.5 + 0.06));
                    ZipperDraw.draw(ps, buffers, c1.subtract(here), n, side, w + 0.7f, 0.55f, open, appear, light, 1f, time);
                    Vec3 c2 = tp.add(0, target.getBbHeight() * 0.78, 0).add(n.scale(w * 0.5 + 0.07));
                    Vec3 diag = side.add(0, 0.8, 0).normalize();
                    ZipperDraw.draw(ps, buffers, c2.subtract(here), n, diag, w + 0.3f, 0.35f, open * 0.8f, appear, light, 1f, time + 7);
                } else {
                    Vec3 c = tp.add(0, target.getBbHeight() * 0.6, 0).add(n.scale(w * 0.5 + 0.05));
                    ZipperDraw.draw(ps, buffers, c.subtract(here), n, new Vec3(0, 1, 0), target.getBbHeight() * 0.7f, 0.3f, open, appear, light, 1f, time);
                }
            }
            case FIELD -> renderField(z, ps, buffers, here, open, time, light);
            case TRAP -> {
                boolean mine = Minecraft.getInstance().player != null && z.isOwnedBy(Minecraft.getInstance().player);
                float alpha = z.isOpen() ? 1f : (mine ? 0.55f : 0.07f);
                ZipperDraw.draw(ps, buffers, Vec3.ZERO, z.normal(), z.direction(), z.length(), z.width(), open, appear, light, alpha, time);
            }
            case SURFACE, DOOR, WALL, FLOOR, SLICE ->
                    ZipperDraw.draw(ps, buffers, Vec3.ZERO, z.normal(), z.direction(), z.length(), z.width(), open, appear, light, 1f, 0.28f, time);
            default -> ZipperDraw.draw(ps, buffers, Vec3.ZERO, z.normal(), z.direction(), z.length(), z.width(), open, appear, light, 1f, time);
        }
        super.render(z, yaw, partial, ps, buffers, light);
    }

    /** Normal for a seam along {@code d} that faces the camera as much as possible. */
    private static Vec3 facing(Vec3 toCam, Vec3 d) {
        Vec3 n = toCam.subtract(d.scale(toCam.dot(d)));
        if (n.lengthSqr() < 1e-5) n = Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0).subtract(d.scale(d.y)) : new Vec3(1, 0, 0);
        return n.normalize();
    }

    private void renderField(ZipperEntity z, PoseStack ps, MultiBufferSource buffers, Vec3 here, float open, float time, int light) {
        List<Seam> seams = FIELD_CACHE.computeIfAbsent(z, ZipperRenderer::buildField);
        float radius = z.length();
        for (Seam s : seams) {
            float local = Mth.clamp((z.tickCount - s.delay) / 6f, 0f, 1f);
            if (local <= 0) continue;
            float o = Mth.clamp(open * 1.2f - s.delay / 160f, 0f, 1f);
            Vec3 d = ZipperEntity.directionOf(s.normal, s.angle);
            ZipperDraw.draw(ps, buffers, s.pos.subtract(here), s.normal, d, s.length, s.length * 0.4f, o, local, 0xF000F0, 1f, time + s.delay);
        }
    }

    /** Scatter seams over every nearby surface (and the air) by ray casting from the impact point. */
    private static List<Seam> buildField(ZipperEntity z) {
        List<Seam> out = new ArrayList<>();
        RandomSource r = RandomSource.create(z.seed());
        int count = Math.max(8, (int) z.width());
        float radius = Math.max(4f, z.length());
        Vec3 origin = z.position().add(0, 1.2, 0);
        var level = z.level();
        for (int i = 0; i < count; i++) {
            double yaw = r.nextDouble() * Math.PI * 2;
            double pitch = (r.nextDouble() * 1.6 - 0.9);
            Vec3 dir = new Vec3(Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch), Math.sin(yaw) * Math.cos(pitch));
            double dist = radius * (0.25 + r.nextDouble() * 0.75);
            BlockHitResult hit = level.clip(new ClipContext(origin, origin.add(dir.scale(dist)), ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, z));
            float len = 0.8f + r.nextFloat() * 2.4f;
            float angle = r.nextFloat() * 180f;
            if (hit.getType() == HitResult.Type.BLOCK) {
                Vec3 n = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                float delay = (float) (hit.getLocation().distanceTo(origin) / radius * 26f);
                out.add(new Seam(hit.getLocation().add(n.scale(0.015)), n, angle, len, delay));
            } else if (r.nextInt(3) == 0) {
                Vec3 p = origin.add(dir.scale(dist));
                Vec3 n = new Vec3(r.nextDouble() - 0.5, 0, r.nextDouble() - 0.5);
                if (n.lengthSqr() < 1e-3) n = new Vec3(1, 0, 0);
                out.add(new Seam(p, n.normalize(), 90f + (r.nextFloat() - 0.5f) * 40f, len, (float) (dist / radius * 26f)));
            }
        }
        return out;
    }

    @Override
    public ResourceLocation getTextureLocation(ZipperEntity entity) {
        return ZipperDraw.TEXTURE;
    }
}
