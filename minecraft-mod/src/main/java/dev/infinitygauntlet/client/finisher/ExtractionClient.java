package dev.infinitygauntlet.client.finisher;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.power.CinematicCamera;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Camera;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

import java.util.concurrent.ThreadLocalRandom;

import static dev.infinitygauntlet.finisher.Timelines.Extraction.*;

/**
 * Soul: Soul Extraction. A clawed hand reaches out and the target's soul, a glowing orange copy of it, is torn
 * slowly out of its body while the body sags and loses its colour. The soul hangs in the air writhing, tethered by
 * burning threads, until the caster pulls it in: it stretches into a stream and pours into the Soul Stone. The empty
 * body crumbles away.
 */
final class ExtractionClient extends StationaryClient {
    private static final int ORANGE = InfinityStone.SOUL.color();
    private static final int PALE = InfinityStone.SOUL.highlight();

    ExtractionClient(ClientFinishers.Active a) {
        super(a);
    }

    private float separate(float t) {
        return smooth(window(t, REACH, SUSPEND));
    }

    private float absorb(float t) {
        float u = window(t, ABSORB, CONSUMED);
        return u * u;
    }

    /** Hand position used for the absorb (the gauntlet, pulled in to the chest). */
    private Vec3 hand(float t) {
        Vec3 out = gauntletHand(soulBase(t));
        Vec3 chest = a.casterStart.add(0, 1.2, 0).add(d.scale(0.35)).add(right.scale(side * 0.15));
        return out.lerp(chest, smooth(window(t, ABSORB, CONSUMED)));
    }

    /** The soul's feet before the absorb: lifted out of the body and drawn toward the caster. */
    private Vec3 soulBase(float t) {
        float s = separate(t);
        float hover = t > SUSPEND ? 0.15f * Mth.sin((t - SUSPEND) * 0.2f) : 0;
        return feet.add(0, 1.0 * s + hover, 0).subtract(d.scale(0.9 * s));
    }

    private Vec3 soulFeet(float t) {
        return soulBase(t).lerp(hand(t).subtract(0, h * 0.2 * 0.3, 0), absorb(t));
    }

    @Override
    public void poseCaster(HumanoidModel<?> m, LivingEntity caster, HumanoidArm arm, HumanoidArm g, float pt) {
        float t = a.time(pt);
        float w = bodyWeight(t, pt);
        float reach = smooth(window(t, 0, REACH));
        float pull = smooth(window(t, ABSORB, CONSUMED));
        float release = smooth(window(t, CONSUMED + 6, CONSUMED + 20));
        float strain = Mth.sin(t * 3.4f) * 0.04f * separate(t) * (1 - pull);
        if (arm == gauntletArm) {
            float[] aimed = aim(arm, soulFeet(t).add(0, h * 0.5, 0));
            // a clawed reach that hauls back to the chest
            float x = Mth.lerp(reach, 0, aimed[0]);
            x = Mth.lerp(pull, x, -1.3f);
            float y = Mth.lerp(pull, aimed[1] * reach, -side * 0.85f);
            blend(part(m, arm), w, Mth.lerp(release, x, 0) + strain, y * (1 - release), side * 0.1f * reach * (1 - release));
        } else {
            blend(part(m, arm), w, 0.35f * reach * (1 - release), 0, -side * 0.3f * reach * (1 - release));
        }
        if (arm == HumanoidArm.RIGHT) {
            m.body.yRot = Mth.lerp(w, m.body.yRot, side * 0.3f * pull * (1 - release));
            stance(m, w, 1.2f, soulFeet(t).add(0, h * 0.6, 0));
        }
    }

    @Override
    public void transformCaster(PoseStack pose, LivingEntity caster, float pt) {
        float t = a.time(pt);
        float lean = (5 * smooth(window(t, 0, REACH)) - 12 * smooth(window(t, ABSORB, CONSUMED)) * (1 - smooth(window(t, CONSUMED + 6, CONSUMED + 20))))
                * bodyWeight(t, pt);
        pose.mulPose(Axis.of(new org.joml.Vector3f((float) d.z, 0, (float) -d.x)).rotationDegrees(lean));
    }

    @Override
    public void transformTarget(PoseStack pose, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < REACH) {
            return;
        }
        // the emptied body sags forward and down
        float sag = separate(t);
        pose.translate(0, h * 0.4f, 0);
        pose.mulPose(Axis.of(new org.joml.Vector3f((float) d.z, 0, (float) -d.x)).rotationDegrees(-14 * sag));
        pose.translate(0, -h * 0.4f, 0);
        pose.scale(1, 1 - 0.06f * sag, 1);
    }

    @Override
    public void overlayTarget(PoseStack pose, MultiBufferSource buffers, LivingEntity e, float pt) {
        float t = a.time(pt);
        if (t < REACH || t >= CONSUMED) {
            return;
        }
        // drained of colour: a grey shell over the body
        ghost(pose, buffers, e, pt, 0.7f * separate(t), 0x383838, 1.01f);
        // the soul itself, drawn here so it shares the body's model and pose
        Vec3 off = soulFeet(t).subtract(e.getPosition(pt));
        float writhe = t - REACH;
        float shrink = 1 - 0.8f * absorb(t);
        float alpha = (0.45f + 0.45f * separate(t)) * (1 - absorb(t) * 0.5f);
        pose.pushPose();
        pose.translate(off.x, off.y, off.z);
        pose.translate(0, h * 0.5f, 0);
        pose.mulPose(Axis.YP.rotationDegrees(Mth.sin(writhe * 0.13f) * 25));
        pose.scale(shrink, shrink * (1 + absorb(t)), shrink);
        pose.translate(0, -h * 0.5f, 0);
        float body = Mth.lerp(pt, e.yBodyRotO, e.yBodyRot), head = Mth.lerp(pt, e.yHeadRotO, e.yHeadRot);
        Afterimages.draw(pose, buffers, e, 0, 0, 0, body, head + Mth.sin(writhe * 0.4f) * 20, e.getXRot(), writhe * 0.5f, 0.9f, e.tickCount + writhe * 2,
                alpha, ORANGE, 1.02f);
        Afterimages.draw(pose, buffers, e, 0, 0, 0, body, head, e.getXRot(), writhe * 0.5f, 0.9f, e.tickCount + writhe * 2, alpha * 0.4f, PALE, 1.12f);
        pose.popPose();
    }

    @Override
    public void tick() {
        float t = a.age;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        if (t >= REACH && t < CONSUMED) {
            Vec3 s = soulFeet(t).add(0, h * 0.5, 0);
            for (int i = 0; i < Particles.scaled(4); i++) {
                Vec3 at = s.add(r.nextGaussian() * hw, r.nextGaussian() * h * 0.4, r.nextGaussian() * hw);
                Particles.mote(at, new Vec3(0, 0.04, 0), i % 2 == 0 ? ORANGE : PALE, 0.08f, 22);
            }
        }
        if ((int) t == CONSUMED) {
            for (int i = 0; i < Particles.scaled(30); i++) {
                Vec3 at = feet.add(r.nextGaussian() * hw, r.nextDouble() * h, r.nextGaussian() * hw);
                Particles.smoke(at, new Vec3(0, -0.02, 0), 0x404040, 0.4f, 40);
                Particles.chunk(at, new Vec3(r.nextGaussian() * 0.05, 0.05, r.nextGaussian() * 0.05), 0x505050, 0.07f);
            }
        }
    }

    @Override
    public void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        float t = a.time(pt);
        if (t < REACH || t > CONSUMED + 16) {
            return;
        }
        Vec3 camPos = camera.getPosition();
        Vec3 soul = soulFeet(t).add(0, h * 0.5, 0);
        Vec3 body = core;
        Vec3 hand = hand(t);
        pose.pushPose();
        pose.translate(soul.x - camPos.x, soul.y - camPos.y, soul.z - camPos.z);
        Vec3 cam = camPos.subtract(soul);
        Quaternionf camRot = camera.rotation();
        if (t < CONSUMED) {
            // burning threads still binding it to the body
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.ENERGY_LINES);
            float bind = (1 - absorb(t)) * separate(t);
            for (int i = 0; i < 4; i++) {
                Vec3 from = body.add(VfxDraw.unit(a.targetId * 7L, i).multiply(hw, h * 0.3, hw)).subtract(soul);
                VfxDraw.lightning(pose.last(), lines, from, Vec3.ZERO, cam, a.targetId * 9L + i + (long) (t / 3), 0.4f, 0.03f, 1f, 0.55f, 0.1f, 0.7f * bind);
            }
            // the stream into the stone
            if (t >= ABSORB) {
                VfxDraw.beam(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.BEAM)), Vec3.ZERO, hand.subtract(soul), cam,
                        0.5f * (1 - absorb(t) * 0.5f), -t * 0.6f, 1f, 0.55f, 0.1f, 0.9f);
            }
            VfxDraw.billboard(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW)), camRot, 0, 0, 0, h * 2.2f * (1 - 0.6f * absorb(t)),
                    0, 1f, 0.55f, 0.1f, 0.5f * separate(t));
        }
        pose.popPose();
        // the Soul Stone flaring as it feeds
        float flare = t < ABSORB ? 0 : t < CONSUMED ? absorb(t) : 1 - window(t, CONSUMED, CONSUMED + 16);
        if (flare > 0) {
            pose.pushPose();
            pose.translate(hand.x - camPos.x, hand.y - camPos.y, hand.z - camPos.z);
            VfxDraw.billboard(pose.last(), buffers.getBuffer(ModRenderTypes.additive(ModRenderTypes.GLOW)), camRot, 0, 0, 0, 0.6f + 1.8f * flare, 0, 1f, 0.6f, 0.15f,
                    flare);
            pose.popPose();
        }
    }

    @Override
    public CinematicCamera.Shot shot(float pt) {
        float t = a.time(pt);
        Vec3 soul = soulFeet(t).add(0, h * 0.5, 0);
        if (t < REACH) {
            return shot(eye().subtract(d.scale(2.2)).add(right.scale(side)).add(0, 0.2, 0), core, 0, 1f);
        }
        if (t < SUSPEND) {
            // side on: body and soul pulling apart
            Vec3 mid = core.lerp(soul, 0.5);
            return shot(mid.add(right.scale(side * 4.2)).add(0, 0.2, 0), mid, 0, 0.95f);
        }
        if (t < ABSORB) {
            // reverse shot from behind the soul, the caster beyond it
            float u = window(t, SUSPEND, ABSORB);
            return shot(soul.add(d.scale(2.6 - 0.5 * u)).add(right.scale(-side * 0.9)).add(0, 0.5, 0), soul.lerp(eye(), 0.35), 4 * u, 0.9f);
        }
        if (t < CONSUMED + 4) {
            // close on the gauntlet as the stone drinks it in
            Vec3 hand = hand(t);
            return shot(hand.add(right.scale(side * 1.4)).add(d.scale(1.2)).add(0, 0.25, 0), hand.lerp(soul, 0.3), -5, 0.8f);
        }
        float u = window(t, CONSUMED + 4, a.type.duration);
        return shot(core.lerp(eye(), 0.5).add(right.scale(side * (4.5 + u))).add(0, 1 + u, 0), core.lerp(eye(), 0.5), 0, 1f);
    }

    @Override
    public int grade(float pt) {
        float t = a.time(pt);
        float alpha = t < CONSUMED ? 0.06f + 0.24f * separate(t) : 0.3f * (1 - window(t, CONSUMED, CONSUMED + 22));
        return grade(0xA04A08, alpha);
    }

    @Override
    public int impactFrame(float pt) {
        float t = a.time(pt);
        if (t >= CONSUMED && t < CONSUMED + 1.5f) {
            return 0xE8FFF0D8;
        }
        return 0;
    }
}
