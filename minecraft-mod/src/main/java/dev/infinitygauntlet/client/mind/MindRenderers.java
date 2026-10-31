package dev.infinitygauntlet.client.mind;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.render.EffectEntityRenderer;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.mind.AstralBodyEntity;
import dev.infinitygauntlet.mind.LevitationFieldEntity;
import dev.infinitygauntlet.mind.MentalShieldEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Renderers for the Mind Stone entities. */
public final class MindRenderers {
    /** The abandoned body: the player's own model, still and breathing, tethered to the projection by a golden cord. */
    public static final class Body extends EffectEntityRenderer<AstralBodyEntity> {
        public Body(EntityRendererProvider.Context context) {
            super(context);
            this.shadowRadius = 0.5f;
        }

        @Override
        protected void draw(AstralBodyEntity body, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
            Minecraft mc = Minecraft.getInstance();
            Player owner = body.ownerUuid() == null || mc.level == null ? null : mc.level.getPlayerByUUID(body.ownerUuid());
            if (!(owner instanceof AbstractClientPlayer player)) {
                return;
            }
            PlayerRenderer renderer = (PlayerRenderer) mc.getEntityRenderDispatcher().getRenderer(player);
            PlayerModel<AbstractClientPlayer> model = renderer.getModel();
            float breath = Mth.sin((body.tickCount + pt) * 0.08f) * 0.012f;
            int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(body.level(), body.blockPosition().above());
            pose.pushPose();
            pose.mulPose(Axis.YP.rotationDegrees(180f - body.bodyYaw()));
            pose.scale(-0.9375f, -0.9375f * (1 + breath), 0.9375f);
            pose.translate(0, -1.501f, 0);
            model.attackTime = 0;
            model.riding = false;
            model.young = false;
            model.crouching = false;
            model.setupAnim(player, 0, 0, body.tickCount + pt, Mth.wrapDegrees(body.headYaw() - body.bodyYaw()), 8f);
            model.head.xRot = 0.25f; // the head droops a little: nobody is home
            model.hat.copyFrom(model.head);
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.entityTranslucent(player.getSkin().texture())), light, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF);
            pose.popPose();
            // a faint golden aura over the empty vessel
            Afterimages.draw(pose, buffers, player, 0, 0, 0, body.bodyYaw(), body.headYaw(), 8f, 0, 0, body.tickCount + pt, 0.25f, 0xFFD22A, 1.06f);
            // the cord between body and projection
            Vec3 chest = new Vec3(0, 1.1, 0);
            Vec3 to = player.getPosition(pt).add(0, 1.0, 0).subtract(body.getPosition(pt));
            if (to.length() > 1.2) {
                VertexConsumer beam = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM));
                Vec3 prev = chest;
                for (int i = 1; i <= 16; i++) {
                    float f = i / 16f;
                    Vec3 p = chest.lerp(to, f).add(0, -Math.sin(f * Math.PI) * Math.min(2, to.length() * 0.15), 0);
                    VfxDraw.beam(pose.last(), beam, prev, p, camLocal, 0.08f, -(body.tickCount + pt) * 0.2f, 1f, 0.85f, 0.35f, 0.7f);
                    prev = p;
                }
            }
        }
    }

    /** The levitation field: the golden sigil hanging above, a column of light, and chunks of the ground in orbit. */
    public static final class Field extends GeoEntityRenderer<LevitationFieldEntity> {
        public Field(EntityRendererProvider.Context context) {
            super(context, new DefaultedEntityGeoModel<>(InfinityGauntlet.id("mind_sigil")));
            addRenderLayer(new AutoGlowingGeoLayer<>(this));
            this.shadowRadius = 0;
        }

        @Override
        public void render(LevitationFieldEntity field, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
            float t = field.tickCount + pt;
            float life = field.life(pt);
            float alpha = Math.min(1f, t / 10f) * Math.min(1f, (1 - life) * field.maxLife() / 12f);
            float r = field.visualRadius();
            Vec3 camLocal = entityRenderDispatcher.camera.getPosition().subtract(field.getPosition(pt));
            Quaternionf camRot = entityRenderDispatcher.cameraOrientation();
            // a soft cylinder of light rising off the ground
            VfxDraw.cylinder(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM)), r, 6, 48, t * 0.01f, 1f, 0.82f, 0.25f, 0.18f * alpha, 0);
            // ground chunks in slow orbit
            int[] states = field.floating();
            var dispatcher = Minecraft.getInstance().getBlockRenderer();
            for (int i = 0; i < states.length; i++) {
                BlockState s = Block.stateById(states[i]);
                double a = i * Math.PI * 2 / Math.max(1, states.length) + t * 0.012;
                double dist = r * (0.45 + 0.35 * VfxDraw.hash(field.getId(), i, 1));
                double h = 1.5 + 2.5 * VfxDraw.hash(field.getId(), i, 2) + Math.sin(t * 0.05 + i) * 0.3;
                float size = (0.35f + 0.3f * VfxDraw.hash(field.getId(), i, 3)) * Math.min(1, t / 20f);
                pose.pushPose();
                pose.translate(Math.cos(a) * dist, h, Math.sin(a) * dist);
                pose.mulPose(Axis.of(new org.joml.Vector3f(1, 0.6f, 0.3f).normalize()).rotationDegrees(t * 1.5f + i * 50));
                pose.scale(size, size, size);
                pose.translate(-0.5, -0.5, -0.5);
                dispatcher.renderSingleBlock(s, pose, buffers, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.pushPose();
            pose.translate(0, 4.5, 0);
            VfxDraw.billboard(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW)), camRot, 0, 0, 0, 4f, 0, 1f, 0.82f, 0.25f, 0.6f * alpha);
            pose.scale(alpha, alpha, alpha);
            super.render(field, yaw, pt, pose, buffers, light);
            pose.popPose();
        }
    }

    /** The shield: a translucent golden dome of hexagons, brighter at its rim. */
    public static final class Shield extends EffectEntityRenderer<MentalShieldEntity> {
        public Shield(EntityRendererProvider.Context context) {
            super(context);
        }

        @Override
        protected void draw(MentalShieldEntity shield, float pt, PoseStack pose, MultiBufferSource buffers, Vec3 camLocal) {
            float f = shield.formed(pt);
            if (f <= 0) {
                return;
            }
            float t = shield.tickCount + pt;
            float r = shield.visualRadius() * (0.6f + 0.4f * f);
            pose.pushPose();
            pose.translate(0, 0.9, 0);
            Vec3 cam = camLocal.subtract(0, 0.9, 0);
            VfxDraw.sphere(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.HEXGRID)), cam, r, 16, 28, t * 0.004f, 1f, 0.8f, 0.25f,
                    0.85f * f, 0.12f * f);
            VfxDraw.sphere(pose.last(), buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), cam, r * 0.99f, 14, 24, 0, 1f, 0.85f, 0.4f,
                    0.12f * f, 0.03f * f);
            pose.popPose();
        }
    }

    private MindRenderers() {
    }
}
