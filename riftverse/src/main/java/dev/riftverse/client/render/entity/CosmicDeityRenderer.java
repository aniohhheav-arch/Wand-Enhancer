package dev.riftverse.client.render.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.riftverse.Riftverse;
import dev.riftverse.client.render.FxDraw;
import dev.riftverse.client.render.RvRenderTypes;
import dev.riftverse.entity.boss.CosmicDeityEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * The Cosmic Deity: a person-shaped titan wearing the night sky as skin, rendered full-bright. When it inhales, a void
 * mouth tears open across its face — wider and taller than the head itself at full breath — ringed with burning
 * light, while streams of light spiral into it from every direction and a halo turns behind its head.
 */
public class CosmicDeityRenderer extends MobRenderer<CosmicDeityEntity, PlayerModel<CosmicDeityEntity>> {
    private static final ResourceLocation TEXTURE = Riftverse.id("textures/entity/cosmic_deity.png");

    public CosmicDeityRenderer(EntityRendererProvider.Context context) {
        super(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), false), 2.5f);
        addLayer(new Mouth(this));
    }

    @Override
    public ResourceLocation getTextureLocation(CosmicDeityEntity entity) {
        return TEXTURE;
    }

    @Nullable
    @Override
    protected RenderType getRenderType(CosmicDeityEntity entity, boolean visible, boolean translucent, boolean glowing) {
        return RenderType.entityTranslucentEmissive(TEXTURE);
    }

    @Override
    protected void setupRotations(CosmicDeityEntity entity, PoseStack ps, float bob, float yaw, float partial, float scale) {
        super.setupRotations(entity, ps, bob, yaw, partial, scale);
        // a slow, weightless drift; at full breath it rears back
        ps.translate(0, Math.sin((entity.tickCount + partial) * 0.04) * 0.08, 0);
        ps.mulPose(Axis.XP.rotationDegrees(-14f * entity.inhale(partial)));
    }

    @Override
    public void render(CosmicDeityEntity entity, float yaw, float partial, PoseStack ps, MultiBufferSource buffers, int light) {
        float k = entity.inhale(partial);
        super.render(entity, yaw, partial, ps, buffers, light);
        float age = entity.tickCount + partial;
        float h = entity.getBbHeight();
        ps.pushPose();
        Vec3 eye = ProceduralRenderer.cameraLocal(ps.last());
        // halo behind the head
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(-entity.yBodyRot));
        Vector3f haloC = new Vector3f(0, h * 0.9f, -h * 0.08f);
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), haloC, new Vector3f(0, 0, 1), h * 0.16f, h * 0.012f, 0xFFC14D, 0.8f, 48);
        FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), haloC, new Vector3f((float) Math.sin(age * 0.02), 0, 1).normalize(),
                h * 0.2f, h * 0.006f, CosmicDeityEntity.COLOR, 0.5f, 48);
        ps.popPose();
        if (k > 0.02f) {
            // streams of light spiralling into the mouth from every direction
            Vec3 mouthW = entity.mouth();
            Vec3 mouth = mouthW.subtract(entity.position());
            int streams = 18;
            for (int i = 0; i < streams; i++) {
                double a = i * Math.PI * 2 / streams + age * 0.05;
                double b = Math.sin(i * 1.7) * 0.9;
                double r = h * (1.2 + 0.6 * Math.sin(age * 0.07 + i));
                double phase = ((age * 0.05 + i * 0.37) % 1.0);
                double rr = r * (1 - phase);
                Vec3 from = mouth.add(Math.cos(a + phase * 2) * Math.cos(b) * rr, Math.sin(b) * rr * 0.6, Math.sin(a + phase * 2) * Math.cos(b) * rr);
                Vec3 to = mouth.add(from.subtract(mouth).scale(0.6));
                int col = i % 3 == 0 ? 0xFFF0C8 : CosmicDeityEntity.COLOR;
                FxDraw.beam(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), from, to, eye, 0.12f * k * (float) (h / 10), col, 0.8f * k);
            }
            FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), ps.last(), (float) mouth.x, (float) mouth.y, (float) mouth.z,
                    h * 0.25f * k, CosmicDeityEntity.COLOR, 0.7f * k);
        }
        ps.popPose();
    }

    /** The void mouth, drawn in head space so it opens on the face itself and stretches far past it. */
    static final class Mouth extends RenderLayer<CosmicDeityEntity, PlayerModel<CosmicDeityEntity>> {
        Mouth(RenderLayerParent<CosmicDeityEntity, PlayerModel<CosmicDeityEntity>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buffers, int light, CosmicDeityEntity entity, float limbSwing, float limbSwingAmount,
                           float partial, float age, float netHeadYaw, float headPitch) {
            float k = entity.inhale(partial);
            ps.pushPose();
            getParentModel().head.translateAndRotate(ps);
            // head space: 1/16 per pixel, y down, face plane at z = -4/16, mouth just below the eyes
            float open = 0.15f + 0.85f * k;
            float w = (0.12f + 0.32f * k) * (1f + 0.05f * (float) Math.sin(age * 0.5));
            float hgt = 0.02f + 0.42f * k * open;
            float cy = -0.12f + 0.05f * k;
            float z = -0.255f;
            int segs = 24;
            for (int i = 0; i < segs; i++) {
                double a0 = i * Math.PI * 2 / segs;
                double a1 = (i + 1) * Math.PI * 2 / segs;
                Vector3f c = new Vector3f(0, cy, z - 0.002f);
                Vector3f p0 = new Vector3f((float) Math.cos(a0) * w, cy + (float) Math.sin(a0) * hgt, z - 0.002f);
                Vector3f p1 = new Vector3f((float) Math.cos(a1) * w, cy + (float) Math.sin(a1) * hgt, z - 0.002f);
                FxDraw.quad(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), c, p0, p1, c, 0x020005, 1f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                FxDraw.quad(buffers.getBuffer(RvRenderTypes.solid()), ps.last(), c, p1, p0, c, 0x020005, 1f, FxDraw.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            if (k > 0.05f) {
                // a burning rim and, deeper inside, a swirling galaxy
                for (int ring = 0; ring < 3; ring++) {
                    float s = 1f - ring * 0.28f;
                    FxDraw.ring(buffers.getBuffer(RvRenderTypes.ENERGY), ps.last(), new Vector3f(0, cy, z - 0.004f - ring * 0.002f), new Vector3f(0, 0, 1),
                            w * s, 0.02f + 0.015f * k, ring == 0 ? 0xFFC14D : CosmicDeityEntity.COLOR, k * (1f - ring * 0.25f), 32);
                }
                for (int arm = 0; arm < 3; arm++) {
                    for (int j = 0; j < 8; j++) {
                        double ang = age * 0.15 + arm * Math.PI * 2 / 3 + j * 0.5;
                        float rr = w * 0.85f * (j / 8f);
                        FxDraw.billboard(buffers.getBuffer(RvRenderTypes.softGlow()), ps.last(), (float) Math.cos(ang) * rr,
                                cy + (float) Math.sin(ang) * rr * (hgt / Math.max(w, 1e-3f)), z - 0.01f, 0.03f + 0.02f * k,
                                j % 2 == 0 ? 0xFFF0C8 : CosmicDeityEntity.COLOR, 0.8f * k);
                    }
                }
            }
            ps.popPose();
        }
    }
}
