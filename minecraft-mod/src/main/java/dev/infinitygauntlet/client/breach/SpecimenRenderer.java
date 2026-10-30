package dev.infinitygauntlet.client.breach;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.breach.SpecimenEntity;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;

/**
 * The containment unit (GeckoLib) with the target's real model floating inside it, driven by the synced mirror
 * data: same pose, walk cycle, size and age as the real creature. Manipulations show on it (frozen creatures carry
 * a green time-ghost, phased ones are only a pale outline, glowing ones an aura) and reality glitches split it
 * into red and cyan copies. Telemetry floats above; the survey hologram stands beside it.
 */
public class SpecimenRenderer extends GeoEntityRenderer<SpecimenEntity> {
    private static final Map<Integer, LivingEntity> DUMMIES = new HashMap<>();
    private static final float TEXT = 1 / 70f;
    private static final float PLATFORM = 1.1f;

    public SpecimenRenderer(EntityRendererProvider.Context context) {
        super(context, new DefaultedEntityGeoModel<>(InfinityGauntlet.id("containment")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0;
    }

    /** Client tick: advance each mirror's walk cycle with the real target's movement. */
    public static void tickDummies() {
        Minecraft mc = Minecraft.getInstance();
        Iterator<Map.Entry<Integer, LivingEntity>> it = DUMMIES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, LivingEntity> en = it.next();
            Entity e = mc.level == null ? null : mc.level.getEntity(en.getKey());
            if (!(e instanceof SpecimenEntity specimen)) {
                it.remove();
                continue;
            }
            LivingEntity dummy = en.getValue();
            if (!specimen.has(SpecimenEntity.FLAG_FROZEN)) {
                dummy.walkAnimation.update(Math.min(1f, specimen.speed() * 4f), 0.4f);
                dummy.tickCount++;
            }
        }
    }

    public static void clear() {
        DUMMIES.clear();
    }

    private static LivingEntity dummy(SpecimenEntity specimen) {
        EntityType<?> type = specimen.targetType();
        LivingEntity d = DUMMIES.get(specimen.getId());
        if (d == null || d.getType() != type) {
            Entity created = type.create(specimen.level());
            d = created instanceof LivingEntity l ? l : null;
            if (d == null) {
                return null;
            }
            DUMMIES.put(specimen.getId(), d);
        }
        return d;
    }

    @Override
    public void render(SpecimenEntity specimen, float entityYaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        LivingEntity dummy = dummy(specimen);
        float t = specimen.tickCount + pt;
        float height = dummy == null ? 1.8f : dummy.getType().getDimensions().height() * specimen.targetScale();
        float k = Math.max(1f, height / 1.6f);
        // the containment unit hovers above the floor so the specimen floats above the panel's line of sight
        pose.pushPose();
        pose.translate(0, PLATFORM, 0);
        pose.scale(k, k, k);
        super.render(specimen, entityYaw, pt, pose, buffers, light);
        pose.popPose();

        boolean lost = specimen.has(SpecimenEntity.FLAG_LOST);
        if (dummy != null && !lost) {
            float lift = PLATFORM + 0.45f * k + (float) Math.sin(t * 0.06) * 0.08f;
            prepare(dummy, specimen, lift);
            int rgb = 0xFFFFFF;
            if (specimen.has(SpecimenEntity.FLAG_PHASED)) {
                ghost(pose, buffers, dummy, 0, lift, 0, t, 0.35f, 0xE8F4FF, 1f);
            } else {
                Minecraft.getInstance().getEntityRenderDispatcher().render(dummy, 0, lift, 0, 0, pt, pose, buffers, LightTexture.FULL_BRIGHT);
            }
            if (specimen.has(SpecimenEntity.FLAG_FROZEN)) {
                ghost(pose, buffers, dummy, 0, lift, 0, t, 0.35f, 0x1EDC5A, 1.04f);
            }
            if (specimen.has(SpecimenEntity.FLAG_GLOWING)) {
                ghost(pose, buffers, dummy, 0, lift, 0, t, 0.25f + 0.1f * (float) Math.sin(t * 0.3), rgb, 1.07f);
            }
            float g = RealityDistortion.spike();
            if (g > 0.2f) {
                double off = 0.06 + 0.25 * g;
                ghost(pose, buffers, dummy, off, lift, 0, t, 0.45f * g, 0xFF2040, 1f);
                ghost(pose, buffers, dummy, -off, lift, 0, t, 0.45f * g, 0x20E0FF, 1f);
            }
        }

        // telemetry, facing the arrival point (north)
        pose.pushPose();
        pose.translate(0, PLATFORM + 2.4 * k + 0.5, 0);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        if (lost) {
            if (((int) (t / 6)) % 2 == 0) {
                text(pose, buffers, Component.translatable("specimen.infinitygauntlet.lost"), 0, 0, 1.6f, 0xFF4050);
            }
        } else {
            text(pose, buffers, Component.literal(specimen.targetName()), 0, 0.28f, 1.6f, 0x203040);
            float hp = specimen.maxHealth() <= 0 ? 0 : specimen.health() / specimen.maxHealth();
            bar(pose, buffers, -0.6f, 0.06f, 1.2f, hp);
            text(pose, buffers, Component.translatable("specimen.infinitygauntlet.vitals", String.format(Locale.ROOT, "%.0f/%.0f", specimen.health(),
                    specimen.maxHealth()), String.format(Locale.ROOT, "%.2f", specimen.targetScale())), 0, -0.1f, 0.9f, 0x3A4A66);
            text(pose, buffers, Component.translatable("specimen.infinitygauntlet.location", specimen.targetPos().getX(), specimen.targetPos().getY(),
                    specimen.targetPos().getZ(), specimen.targetDimension()), 0, -0.26f, 0.8f, 0x5A6A86);
            MutableComponent status = Component.empty();
            String[] keys = {"levitating", "blind", "glowing", "pacified", "frozen", "phased"};
            int[] flags = {SpecimenEntity.FLAG_LEVITATING, SpecimenEntity.FLAG_BLIND, SpecimenEntity.FLAG_GLOWING, SpecimenEntity.FLAG_PACIFIED,
                    SpecimenEntity.FLAG_FROZEN, SpecimenEntity.FLAG_PHASED};
            for (int i = 0; i < keys.length; i++) {
                if (specimen.has(flags[i])) {
                    status.append(Component.translatable("specimen.infinitygauntlet.flag." + keys[i])).append("  ");
                }
            }
            text(pose, buffers, status, 0, -0.42f, 0.85f, 0xC0207A);
        }
        pose.popPose();

        drawLiveScreen(specimen, t, pose, buffers);

        // survey hologram on its own plinth
        pose.pushPose();
        pose.translate(3.6, 0.9, 0);
        SurveyHologram.render(specimen.getId(), dummy, t, pose, buffers);
        pose.popPose();
    }

    /** The live view: a big holographic monitor behind the containment unit, facing the arrival point. */
    private static void drawLiveScreen(SpecimenEntity specimen, float t, PoseStack pose, MultiBufferSource buffers) {
        float w = 7.2f, h = 4.05f;
        pose.pushPose();
        pose.translate(0, PLATFORM + 3.1f, 4.2f);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        Matrix4f m = pose.last().pose();
        float g = RealityDistortion.spike();
        quad(m, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), -w / 2 - 0.08f, -h / 2 - 0.08f, w + 0.16f, h + 0.16f,
                0.02f, 0.02f, 0.05f, 0.92f, -0.02f);
        net.minecraft.client.renderer.RenderType feed = specimen.has(SpecimenEntity.FLAG_LOST) ? null : LiveView.screen(specimen.getId());
        if (feed != null) {
            float j = g > 0.25f ? (float) Math.sin(t * 17) * 0.08f * g : 0;
            feedQuad(m, buffers.getBuffer(feed), -w / 2 + j, -h / 2, w, h, 1, 1, 1, 1, 0);
            if (specimen.has(SpecimenEntity.FLAG_BLIND)) {
                quad(m, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.VIGNETTE)), -w / 2, -h / 2, w, h, 0, 0, 0, 1f, 0.004f);
                quad(m, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE)), -w / 2, -h / 2, w, h, 0, 0, 0, 0.75f, 0.005f);
            }
            if (g > 0.2f) {
                // red/cyan split ghosts of the picture during glitches
                feedQuad(m, buffers.getBuffer(feed), -w / 2 + 0.12f * g, -h / 2, w, h, 1f, 0.15f, 0.2f, 0.35f * g, 0.006f);
                feedQuad(m, buffers.getBuffer(feed), -w / 2 - 0.12f * g, -h / 2, w, h, 0.15f, 0.9f, 1f, 0.35f * g, 0.007f);
            }
        } else {
            float u = (t * 0.37f) % 1f;
            quad(m, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.STATIC)), -w / 2, -h / 2, w, h, 0.5f, 0.5f, 0.55f, 0.6f, 0.003f);
        }
        quad(m, buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.SCANLINES)), -w / 2, -h / 2, w, h, 0, 0.02f, 0.05f, 0.8f, 0.009f);
        var edge = buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE));
        quad(m, edge, -w / 2 - 0.08f, h / 2, w + 0.16f, 0.04f, 1f, 0.27f, 0.78f, 0.9f, 0.01f);
        quad(m, buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.WHITE)), -w / 2 - 0.08f, -h / 2 - 0.04f, w + 0.16f, 0.04f, 0.35f, 0.9f, 1f, 0.9f,
                0.01f);
        pose.translate(0, 0, 0.02f);
        boolean blink = ((int) (t / 10)) % 2 == 0;
        text(pose, buffers, Component.literal((blink ? "\u25CF " : "  ") + "LIVE // ").append(Component.translatable("specimen.infinitygauntlet.eyes",
                specimen.targetName())), 0, h / 2 + 0.22f, 2.2f, 0xC0207A);
        if (feed == null) {
            text(pose, buffers, Component.translatable(specimen.has(SpecimenEntity.FLAG_LOST) ? "specimen.infinitygauntlet.lost"
                    : "specimen.infinitygauntlet.acquiring"), 0, 0, 3f, 0xFFFFFF);
        }
        pose.popPose();
    }

    private static void quad(Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer vc, float x, float y, float w, float h, float r, float g, float b,
                             float a, float z) {
        vc.addVertex(m, x, y, z).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y, z).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y + h, z).setUv(1, 0).setColor(r, g, b, a);
        vc.addVertex(m, x, y + h, z).setUv(0, 0).setColor(r, g, b, a);
    }

    /** Framebuffer textures are stored bottom-up, so the top edge samples v = 1. */
    private static void feedQuad(Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer vc, float x, float y, float w, float h, float r, float g, float b,
                                 float a, float z) {
        vc.addVertex(m, x, y, z).setUv(0, 0).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y, z).setUv(1, 0).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y + h, z).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(m, x, y + h, z).setUv(0, 1).setColor(r, g, b, a);
    }

    private static void prepare(LivingEntity d, SpecimenEntity s, float lift) {
        d.setPos(s.getX(), s.getY() + lift, s.getZ());
        d.xo = d.getX();
        d.yo = d.getY();
        d.zo = d.getZ();
        d.yBodyRot = d.yBodyRotO = s.bodyYaw();
        d.yHeadRot = d.yHeadRotO = s.headYaw();
        d.setYRot(s.bodyYaw());
        d.yRotO = s.bodyYaw();
        d.setXRot(s.pitch());
        d.xRotO = s.pitch();
        AttributeInstance scale = d.getAttribute(Attributes.SCALE);
        if (scale != null && scale.getBaseValue() != s.targetScale()) {
            scale.setBaseValue(s.targetScale());
            d.refreshDimensions();
        }
        boolean baby = s.has(SpecimenEntity.FLAG_BABY);
        if (d instanceof AgeableMob ageable && ageable.isBaby() != baby) {
            ageable.setAge(baby ? -24000 : 0);
        } else if (d instanceof Zombie zombie && zombie.isBaby() != baby) {
            zombie.setBaby(baby);
        }
    }

    private static void ghost(PoseStack pose, MultiBufferSource buffers, LivingEntity d, double x, double y, double z, float t, float alpha, int rgb,
                              float inflate) {
        Afterimages.draw(pose, buffers, d, x, y, z, d.yBodyRot, d.yHeadRot, d.getXRot(), d.walkAnimation.position(), d.walkAnimation.speed(), t,
                alpha, rgb, inflate);
    }

    private static void bar(PoseStack pose, MultiBufferSource buffers, float x, float y, float w, float f) {
        Matrix4f m = pose.last().pose();
        var glass = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE));
        quad(m, glass, x, y, w, 0.05f, 0.75f, 0.78f, 0.84f, 0.9f);
        var fill = buffers.getBuffer(ModRenderTypes.translucent(ModRenderTypes.WHITE));
        quad(m, fill, x, y, w * Math.max(0, Math.min(1, f)), 0.05f, 0.9f, 0.15f, 0.35f, 0.95f);
    }

    private static void quad(Matrix4f m, com.mojang.blaze3d.vertex.VertexConsumer vc, float x, float y, float w, float h, float r, float g, float b,
                             float a) {
        vc.addVertex(m, x, y, 0.01f).setUv(0, 1).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y, 0.01f).setUv(1, 1).setColor(r, g, b, a);
        vc.addVertex(m, x + w, y + h, 0.01f).setUv(1, 0).setColor(r, g, b, a);
        vc.addVertex(m, x, y + h, 0.01f).setUv(0, 0).setColor(r, g, b, a);
    }

    private static void text(PoseStack pose, MultiBufferSource buffers, Component c, float x, float y, float scale, int rgb) {
        Font font = Minecraft.getInstance().font;
        FormattedCharSequence seq = c.getVisualOrderText();
        pose.pushPose();
        pose.translate(x, y, 0.02f);
        pose.scale(TEXT * scale / 3, -TEXT * scale / 3, TEXT * scale / 3);
        font.drawInBatch(seq, -font.width(seq) / 2f, -4, 0xFF000000 | rgb, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0,
                LightTexture.FULL_BRIGHT);
        pose.popPose();
    }
}
