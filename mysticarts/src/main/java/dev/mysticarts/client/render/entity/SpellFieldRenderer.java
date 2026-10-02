package dev.mysticarts.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.ClientFx;
import dev.mysticarts.client.render.MaDraw;
import dev.mysticarts.client.render.MaRenderTypes;
import dev.mysticarts.client.render.WarpFx;
import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Every area spell, each with its own identity: clocks for time, lattices for space, sigils for mind, embers for soul. */
public class SpellFieldRenderer extends EntityRenderer<SpellFieldEntity> {
    private static final Vector3f UP = new Vector3f(0, 1, 0);

    public SpellFieldRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SpellFieldEntity entity) {
        return MaRenderTypes.WHITE;
    }

    @Override
    public boolean shouldRender(SpellFieldEntity entity, Frustum frustum, double x, double y, double z) {
        return entity.shouldRenderAtSqrDistance(entity.distanceToSqr(x, y, z));
    }

    @Override
    public void render(SpellFieldEntity f, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        float k = f.strength(partial);
        if (k <= 0.01f) return;
        float r = f.radius();
        float time = f.tickCount + partial;
        float prog = f.progress(partial);
        int col = f.color();
        int seed = f.getId() & 255;
        PoseStack.Pose pose = ps.last();
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        Vec3 world = f.getPosition(partial);
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double camDist = cam.distanceTo(world);
        if (camDist < r) ClientFx.reportField(f.kind(), k * (float) Math.min(1.0, (r - camDist) / Math.max(1.0, r * 0.3)));
        Vector3f c = new Vector3f();
        Vector3f ground = new Vector3f(0, -0.95f, 0);

        switch (f.kind()) {
            case FieldKind.SHIELD_DOME -> {
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 6 + time * 0.01;
                    MaDraw.bandRing(rune, pose, ground, new Vector3f((float) Math.cos(a), 0, (float) Math.sin(a)), r, 0.25f, seed + i, col, 0.7f * k, 48);
                }
                for (int i = 1; i <= 3; i++) {
                    float y = r * i / 4f;
                    float rr = (float) Math.sqrt(Math.max(0, r * r - y * y));
                    MaDraw.bandRing(rune, pose, new Vector3f(ground).add(0, y, 0), UP, rr, 0.2f, seed + 10 + i, col, 0.6f * k, 48);
                }
                MaDraw.rune(rune, pose, new Vector3f(ground).add(0, 0.05f, 0), UP, r, time * 0.01f, MaDraw.SHIELD, seed, col, 0.8f * k);
            }
            case FieldKind.TIME_STOP, FieldKind.TIME_SLOW, FieldKind.PROJECTILE_FREEZE -> {
                float strength = f.kind() == FieldKind.TIME_STOP ? 1f : 0.5f;
                MaDraw.rune(rune, pose, new Vector3f(ground).add(0, 0.06f, 0), UP, r, time * 0.002f, MaDraw.CLOCK, seed, col, 0.45f * k * strength);
                MaDraw.bandRing(rune, pose, new Vector3f(ground).add(0, 0.1f, 0), UP, r, 0.5f, seed + 1, col, 0.7f * k, 96);
                if (f.kind() == FieldKind.TIME_STOP) {
                    for (int i = 0; i < 3; i++) {
                        double a = time * 0.004 + i * Math.PI * 2 / 3;
                        Vector3f at = new Vector3f((float) Math.cos(a) * r * 0.5f, 1.5f + i * 0.6f, (float) Math.sin(a) * r * 0.5f);
                        MaDraw.rune(rune, pose, at, new Vector3f(at.x, 0, at.z), 1.4f, time * 0.003f, MaDraw.CLOCK, seed + 2 + i, col, 0.5f * k);
                    }
                }
                WarpFx.sphere(world, r, WarpFx.TEMPORAL, k * strength * 0.7f, prog, col);
            }
            case FieldKind.TIME_ACCEL -> {
                for (int i = 0; i < 4; i++) {
                    double a = time * 0.3 + i * Math.PI / 2;
                    Vector3f at = new Vector3f((float) Math.cos(a) * 1.1f, (float) Math.sin(time * 0.2 + i) * 0.4f, (float) Math.sin(a) * 1.1f);
                    MaDraw.rune(rune, pose, at, new Vector3f(at.x, 0, at.z), 0.35f, time * 0.5f, MaDraw.CLOCK, seed + i, col, 0.7f * k);
                }
                MaDraw.bandRing(rune, pose, ground, UP, r, 0.3f, seed + 5, col, 0.4f * k, 48);
            }
            case FieldKind.TEMPORAL_SINGULARITY, FieldKind.TEMPORAL_REWRITE -> {
                int layers = f.kind() == FieldKind.TEMPORAL_SINGULARITY ? 8 : 5;
                for (int i = 0; i < layers; i++) {
                    float s = r * (0.25f + i * 0.09f);
                    Vector3f n = new Vector3f((float) Math.sin(i * 1.7 + time * 0.003), 1f, (float) Math.cos(i * 2.3)).normalize();
                    int cc = f.kind() == FieldKind.TEMPORAL_REWRITE && i % 2 == 1 ? 0xE3122E : col;
                    MaDraw.rune(rune, pose, new Vector3f(0, i * 0.4f - 0.5f, 0), n, s, time * 0.01f * (i % 2 == 0 ? 1 : -1.6f), MaDraw.CLOCK, seed + i, cc, (0.35f - i * 0.02f) * k);
                }
                MaDraw.runeBillboard(rune, pose, new Vector3f(0, 1, 0), 1.5f, time * 0.05f, MaDraw.STAR, seed + 20, col, 0.7f * k);
                WarpFx.sphere(world, r * 0.8f, WarpFx.TEMPORAL, k, prog, col);
            }
            case FieldKind.GRAVITY -> {
                MaDraw.rune(rune, pose, ground, UP, r, time * 0.02f, MaDraw.LATTICE, seed, col, 0.25f * k);
                for (int i = 0; i < 4; i++) {
                    float y = ((time * 0.05f + i * 1.5f) % 6f) - 1f;
                    MaDraw.bandRing(rune, pose, new Vector3f(0, y, 0), UP, r * (1f - (y + 1) / 10f), 0.25f, seed + i, col, 0.6f * k * (1f - (y + 1) / 6f), 48);
                }
                WarpFx.sphere(world, r * 0.7f, WarpFx.PINCH, 0.4f * k, prog, col);
            }
            case FieldKind.COSMIC_RIFT, FieldKind.SPACE_RIFT_ULT -> {
                float s = r * (f.kind() == FieldKind.SPACE_RIFT_ULT ? 1f : 1.1f);
                MaDraw.runeBillboard(rune, pose, c, s * 1.15f, time * 0.04f, MaDraw.RIM, seed, col, k);
                MaDraw.runeBillboard(rune, pose, c, s, -time * 0.03f, MaDraw.VORTEX, seed + 1, ColorUtil.lerp(col, 0x9B2CFF, 0.3f), 0.8f * k);
                MaDraw.runeBillboard(rune, pose, c, s * 1.6f, time * 0.02f, MaDraw.SIGIL, seed + 2, col, 0.4f * k);
                WarpFx.sphere(world, s * 1.8f, WarpFx.PINCH, k, prog, col);
            }
            case FieldKind.REALITY_DISTORT, FieldKind.REALITY_REWRITE -> {
                for (int i = 0; i < 5; i++) {
                    Vector3f n = new Vector3f((float) Math.sin(time * 0.02 + i * 1.3), (float) Math.cos(time * 0.017 + i), (float) Math.sin(time * 0.013 + i * 2.1)).normalize();
                    MaDraw.rune(rune, pose, new Vector3f(0, i * 0.5f - 0.5f, 0), n, r * (0.4f + i * 0.12f), time * 0.03f, MaDraw.SIGIL, seed + i, col, 0.35f * k);
                }
                MaDraw.rune(rune, pose, ground, UP, r, time * 0.01f, MaDraw.SIGIL, seed + 9, col, 0.6f * k);
                WarpFx.sphere(world, r, WarpFx.HAZE, 1.2f * k, prog, col);
            }
            case FieldKind.ZERO_G -> {
                for (int i = 0; i < 5; i++) {
                    float y = ((time * 0.03f + i * 1.2f) % 6f) - 1.5f;
                    MaDraw.bandRing(rune, pose, new Vector3f(0, y, 0), UP, r * 0.9f, 0.15f, seed + i, col, 0.5f * k, 48);
                }
                MaDraw.runeBillboard(rune, pose, c, r, 0, MaDraw.HALO, seed + 8, col, 0.15f * k);
            }
            case FieldKind.MIND_LIFT, FieldKind.DIMENSIONAL_TELEKINESIS, FieldKind.PSYCHIC_FIELD -> {
                float rr = f.kind() == FieldKind.PSYCHIC_FIELD ? r * prog : r;
                MaDraw.rune(rune, pose, ground, UP, rr, time * 0.02f, MaDraw.SIGIL, seed, col, 0.8f * k);
                MaDraw.bandRing(rune, pose, ground, UP, rr, 0.6f, seed + 1, col, 0.8f * k, 96);
                for (int i = 0; i < 3; i++) {
                    float y = ((time * 0.08f + i * 1.3f) % 4f);
                    MaDraw.bandRing(rune, pose, new Vector3f(0, y - 0.5f, 0), UP, rr * (0.6f + y * 0.1f), 0.3f, seed + 2 + i, col, 0.5f * k * (1f - y / 4f), 64);
                }
                if (f.kind() == FieldKind.DIMENSIONAL_TELEKINESIS) WarpFx.sphere(world, r * 0.6f, WarpFx.PINCH, 0.5f * k, prog, 0x2F7BFF);
            }
            case FieldKind.SOUL_DOMINION, FieldKind.SPECTRAL_DIMENSION -> {
                MaDraw.rune(rune, pose, ground, UP, r, time * 0.01f, MaDraw.SIGIL, seed, col, 0.6f * k);
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4 + time * 0.01;
                    Vector3f at = new Vector3f((float) Math.cos(a) * r * 0.8f, 0, (float) Math.sin(a) * r * 0.8f);
                    MaDraw.runeBillboard(rune, pose, new Vector3f(at).add(0, (float) Math.sin(time * 0.1 + i) * 0.5f + 1f, 0), 0.6f, 0, MaDraw.HALO, seed + i, col, 0.6f * k);
                }
                MaDraw.bandRing(rune, pose, ground, UP, r, 0.5f, seed + 20, col, 0.7f * k, 96);
            }
            case FieldKind.DELAYED_EXPLOSION, FieldKind.CATACLYSM -> {
                for (int i = 0; i < 3; i++) {
                    float rr = r * (1f - ((prog + i / 3f) % 1f));
                    MaDraw.bandRing(rune, pose, c, UP, rr, 0.35f, seed + i, col, k, 64);
                    MaDraw.bandRing(rune, pose, c, new Vector3f(1, 0, 0), rr, 0.25f, seed + 4 + i, col, 0.6f * k, 64);
                }
                MaDraw.runeBillboard(rune, pose, c, 0.5f + prog * 2.5f, time * 0.2f, MaDraw.STAR, seed + 9, col, 0.6f + prog * 0.4f);
                WarpFx.sphere(world, 1f + prog * r * 0.5f, WarpFx.HAZE, prog, prog, col);
            }
            case FieldKind.POWER_CHARGE -> {
                MaDraw.runeBillboard(rune, pose, new Vector3f(0, 0.4f, 0), 0.6f + prog * 2.4f, time * 0.15f, MaDraw.STAR, seed, col, 0.5f + prog * 0.5f);
                for (int i = 0; i < 3; i++) {
                    Vector3f n = new Vector3f((float) Math.sin(time * 0.1 + i * 2), 0.6f, (float) Math.cos(time * 0.12 + i * 2)).normalize();
                    MaDraw.bandRing(rune, pose, new Vector3f(0, 0.4f, 0), n, 1.2f + prog * 1.5f, 0.2f, seed + i, col, 0.7f, 40);
                }
                MaDraw.rune(rune, pose, new Vector3f(ground).add(0, 0.06f, 0), UP, 2f + prog * 3f, time * 0.05f, MaDraw.SIGIL, seed + 5, col, 0.8f);
                WarpFx.sphere(world.add(0, 0.4, 0), 1.5f + prog * 3f, WarpFx.HAZE, 0.5f + prog, prog, col);
            }
            default -> {}
        }
    }
}
