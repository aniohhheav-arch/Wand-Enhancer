package dev.infinitygauntlet.client.breach;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.breach.ObservationPanelEntity;
import dev.infinitygauntlet.breach.PanelControl;
import dev.infinitygauntlet.breach.PanelLayout;
import dev.infinitygauntlet.breach.SpecimenEntity;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** GeckoLib panel body plus its holographic button face, laid out by {@link PanelLayout}. */
public class PanelRenderer extends GeoEntityRenderer<ObservationPanelEntity> {
    private static final float TEXT = 1 / 150f;

    public PanelRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(InfinityGauntlet.id("observation_panel")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0;
    }

    @Override
    public void render(ObservationPanelEntity panel, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        super.render(panel, entityYaw, pt, pose, buffers, light);
        float deploy = Math.min(1f, (panel.tickCount + pt - 12) / 10f);
        if (deploy <= 0) {
            return;
        }
        PanelLayout layout = panel.layout(pt);
        Vec3 origin = panel.getPosition(pt);
        SpecimenEntity specimen = Minecraft.getInstance().level.getEntity(panel.specimenId()) instanceof SpecimenEntity s ? s : null;
        float t = panel.tickCount + pt;
        float glitch = RealityDistortion.spike();

        pose.pushPose();
        // basis: x = right, y = up along the tilted face, z = out of the face
        Vec3 c = layout.center.subtract(origin);
        Matrix4f basis = new Matrix4f(
                (float) layout.right.x, (float) layout.right.y, (float) layout.right.z, 0,
                (float) layout.up.x, (float) layout.up.y, (float) layout.up.z, 0,
                (float) layout.normal.x, (float) layout.normal.y, (float) layout.normal.z, 0,
                (float) c.x, (float) c.y, (float) c.z, 1);
        pose.last().pose().mul(basis);
        if (glitch > 0.3f) {
            pose.translate((Math.sin(t * 13) * 0.02 * glitch), 0, 0);
        }
        PoseStack.Pose p = pose.last();
        // fetch buffers right before each use: text and other render types end shared batches
        double gw = PanelLayout.gridWidth(), gh = PanelLayout.gridHeight();
        quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), -gw / 2 - 0.05, -gh / 2 - 0.05, gw + 0.1, gh + 0.1, 0.001, 0.02f, 0.025f, 0.05f, 0.82f * deploy);

        Component header = specimen == null ? Component.translatable("panel.infinitygauntlet.header_empty")
                : Component.translatable("panel.infinitygauntlet.header", specimen.targetName());
        text(pose, buffers, header, (float) (-gw / 2 + 0.03), (float) (gh / 2 - 0.065), 1.0f, specimen != null && specimen.has(SpecimenEntity.FLAG_LOST)
                ? 0xFF5050 : 0x9FE8FF, false);
        if (((int) (t / 10)) % 2 == 0) {
            text(pose, buffers, Component.literal("●"), (float) (gw / 2 - 0.08), (float) (gh / 2 - 0.065), 1.0f, 0xFF3060, false);
        }

        for (PanelControl control : PanelControl.values()) {
            double[] r = PanelLayout.rect(control);
            boolean hovered = PanelInteraction.hovered(panel) == control;
            boolean active = control.flag >= 0 && specimen != null && specimen.has(control.flag);
            float flash = PanelInteraction.flash(panel, control);
            boolean danger = control == PanelControl.RELEASE;
            float br = active ? 0.9f : danger ? 0.5f : 0.08f, bg = active ? 0.25f : danger ? 0.06f : 0.16f, bb = active ? 0.75f : danger ? 0.1f : 0.3f;
            quad(p, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), r[0], r[1], r[2] - r[0], r[3] - r[1], 0.003, br, bg, bb, (hovered ? 0.95f : 0.75f) * deploy);
            float edge = hovered ? 1f : 0.55f;
            quad(p, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE)), r[0], r[3] - 0.008, r[2] - r[0], 0.008, 0.004, 0.35f * edge, 0.9f * edge, edge, deploy);
            quad(p, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE)), r[0], r[1], r[2] - r[0], 0.008, 0.004, 0.35f * edge, 0.9f * edge, edge, deploy);
            if (hovered || flash > 0) {
                quad(p, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE)), r[0], r[1], r[2] - r[0], r[3] - r[1], 0.005, 0.3f, 0.8f, 1f, 0.25f + 0.6f * flash);
            }
            Component label = Component.translatable("panel.infinitygauntlet." + control.key);
            text(pose, buffers, label, (float) ((r[0] + r[2]) / 2), (float) ((r[1] + r[3]) / 2), 0.95f,
                    active ? 0xFFFFFF : hovered ? 0xFFFFFF : danger ? 0xFF8080 : 0xBFD8FF, true);
        }
        pose.popPose();
    }

    private static void quad(PoseStack.Pose p, VertexConsumer vc, double x, double y, double w, double h, double z, float r, float g, float b, float a) {
        Matrix4f m = p.pose();
        vc.addVertex(m, (float) x, (float) y, (float) z).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(m, (float) (x + w), (float) y, (float) z).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(m, (float) (x + w), (float) (y + h), (float) z).setUv(1, 0).setColor(r, g, b, a);
        vc.addVertex(m, (float) x, (float) (y + h), (float) z).setUv(0, 0).setColor(r, g, b, a);
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, Component c, float x, float y, float scale, int rgb, boolean centered) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence seq = c.getVisualOrderText();
        pose.pushPose();
        pose.translate(x, y, 0.008f);
        pose.scale(TEXT * scale, -TEXT * scale, TEXT * scale);
        font.drawInBatch(seq, centered ? -font.width(seq) / 2f : 0, -4, 0xFF000000 | rgb, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
