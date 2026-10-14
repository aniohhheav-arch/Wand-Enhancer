package com.stickyfingers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.stickyfingers.entity.ZipperProjectileEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** A short spinning zipper streak aligned with the projectile's flight. */
public class ZipperProjectileRenderer extends EntityRenderer<ZipperProjectileEntity> {
    public ZipperProjectileRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(ZipperProjectileEntity e, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        Vec3 v = e.getDeltaMovement();
        if (v.lengthSqr() < 1e-6) v = new Vec3(0, 0, 1);
        Vec3 d = v.normalize();
        float time = e.tickCount + partial;
        Vec3 up = Math.abs(d.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 side = up.cross(d).normalize();
        Vec3 n0 = d.cross(side).normalize();
        double spin = time * 0.6;
        Vec3 n = n0.scale(Math.cos(spin)).add(side.scale(Math.sin(spin))).normalize();
        ZipperDraw.draw(ps, buffers, d.scale(-0.4), n, d, 1.2f, 0.35f, 0.6f, 1f, 0xF000F0, 1f, time);
        ZipperDraw.draw(ps, buffers, d.scale(-0.4), n.cross(d).normalize(), d, 1.2f, 0.35f, 0.6f, 1f, 0xF000F0, 1f, time);
        super.render(e, yaw, partial, ps, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ZipperProjectileEntity entity) {
        return ZipperDraw.TEXTURE;
    }
}
