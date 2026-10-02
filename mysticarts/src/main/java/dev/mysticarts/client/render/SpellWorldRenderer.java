package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mysticarts.client.CasterStates;
import dev.mysticarts.client.ClientMarks;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Source;
import dev.mysticarts.power.Sustained;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.util.ColorUtil;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** World-space drawing of everything casters are sustaining and every status mark, once per frame. */
public final class SpellWorldRenderer {
    private SpellWorldRenderer() {}

    public static void render(PoseStack ps, MultiBufferSource buffers, Vec3 cam, float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;
        float time = level.getGameTime() + partial;
        VertexConsumer rune = buffers.getBuffer(MaRenderTypes.RUNE);
        VertexConsumer energy = buffers.getBuffer(MaRenderTypes.ENERGY);
        PoseStack.Pose pose = ps.last();
        for (Player p : level.players()) {
            Payloads.CasterState s = CasterStates.get(p.getId());
            if (s == null || p.isInvisible() && (s.flags() & Payloads.CasterState.F_ASTRAL_FORM) == 0) continue;
            caster(p, s, CasterStates.anim(p.getId()), rune, energy, pose, cam, partial, time, level);
        }
        VertexConsumer xray = buffers.getBuffer(MaRenderTypes.RUNE_SEE_THROUGH);
        for (Map.Entry<Integer, EnumMap<MarkKind, int[]>> e : ClientMarks.all().entrySet()) {
            Entity ent = level.getEntity(e.getKey());
            if (ent == null) continue;
            marks(ent, e.getValue(), rune, xray, energy, pose, cam, partial, time, level);
        }
    }

    // ============================================================================================ casters

    private static Vec3 hands(Player p, float partial, boolean firstPerson) {
        Vec3 eye = p.getEyePosition(partial);
        Vec3 look = p.getViewVector(partial);
        return eye.add(look.scale(firstPerson ? 0.75 : 0.6)).add(0, firstPerson ? -0.32 : -0.4, 0);
    }

    private static void caster(Player p, Payloads.CasterState s, CasterStates.Anim anim, VertexConsumer rune, VertexConsumer energy, PoseStack.Pose pose,
                               Vec3 cam, float partial, float time, ClientLevel level) {
        Minecraft mc = Minecraft.getInstance();
        boolean firstPerson = p == mc.player && mc.options.getCameraType() == CameraType.FIRST_PERSON;
        Vec3 look = p.getViewVector(partial);
        Vec3 hands = hands(p, partial, firstPerson);
        Vector3f hc = MaDraw.vec(hands.subtract(cam));

        // eldritch shield: a mandala held before the caster's palm
        float shield = anim.shield(partial);
        if (shield > 0.01f) {
            float open = shield * shield * (3 - 2 * shield);
            Vec3 at = firstPerson ? hands.add(look.scale(0.35)).add(0, -0.05, 0) : hands.add(look.scale(0.2));
            Vector3f c = MaDraw.vec(at.subtract(cam));
            Vector3f n = MaDraw.vec(look);
            int col = Ability.ELDRITCH_SHIELD.color();
            MaDraw.rune(rune, pose, c, n, 0.9f * open, time * 0.06f, MaDraw.SHIELD, p.getId() & 255, col, 0.9f * open);
            MaDraw.rune(rune, pose, new Vector3f(c).add(new Vector3f(n).mul(-0.05f)), n, 0.62f * open, -time * 0.1f, MaDraw.SIGIL, (p.getId() + 7) & 255, 0xFFD27A, 0.6f * open);
            MaDraw.rune(rune, pose, c, n, 1.0f * open, 0, MaDraw.HALO, 1, col, 0.25f * open);
        }

        // whips and telekinesis
        if (s.held() >= 0) {
            Entity target = level.getEntity(s.held());
            if (target != null) {
                Vec3 tc = target.getPosition(partial).add(0, target.getBbHeight() * 0.5, 0);
                if (s.holdKind() == Sustained.HOLD_WHIP) {
                    Vec3 right = new Vec3(-look.z, 0, look.x).normalize();
                    for (int side = -1; side <= 1; side += 2) {
                        Vec3 from = hands.add(right.scale(side * 0.25));
                        whip(energy, pose, from, tc, cam, time + side * 3, Ability.ELDRITCH_WHIP.color());
                    }
                    MaDraw.bandRing(rune, pose, MaDraw.vec(tc.subtract(cam)), new Vector3f(0, 1, 0), Math.max(0.5f, target.getBbWidth() * 0.8f), 0.25f,
                            target.getId(), Ability.ELDRITCH_WHIP.color(), 0.9f, 24);
                } else {
                    int col = s.holdKind() == Sustained.HOLD_MIND ? Source.MIND.color : Ability.TELEKINESIS.color();
                    FxDraw.beam(energy, pose, hands.subtract(cam), tc.subtract(cam), Vec3.ZERO, 0.08f, col, 0.5f);
                    float r = Math.max(0.6f, target.getBbWidth() * 0.9f);
                    Vector3f tcv = MaDraw.vec(tc.subtract(cam));
                    MaDraw.bandRing(rune, pose, tcv, new Vector3f(0, 1, 0), r, 0.22f, target.getId(), col, 0.9f, 32);
                    MaDraw.bandRing(rune, pose, tcv, new Vector3f(1, 0, 0), r * 1.1f, 0.15f, target.getId() + 1, col, 0.6f, 32);
                    MaDraw.runeBillboard(rune, pose, tcv, r * 1.6f, 0, MaDraw.HALO, 2, col, 0.35f);
                    MaDraw.rune(rune, pose, hc, MaDraw.vec(look), 0.35f, time * 0.2f, MaDraw.SIGIL, 3, col, 0.8f);
                }
            }
        }

        // sustained beams
        if (s.beam() >= 0) beam(p, Ability.byId(s.beam()), hands, rune, energy, pose, cam, partial, time, level);

        // charging: energy gathers between the palms
        float charge = anim.charge(partial);
        if (charge > 0.01f) {
            int col = chargeColor(p);
            MaDraw.runeBillboard(rune, pose, hc, 0.15f + charge * 0.45f, time * 0.2f, MaDraw.STAR, 9, col, 0.6f + charge * 0.4f);
            MaDraw.runeBillboard(rune, pose, hc, 0.3f + charge * 0.7f, 0, MaDraw.HALO, 10, col, 0.5f * charge);
            MaDraw.rune(rune, pose, hc, MaDraw.vec(look), 0.4f + charge * 0.4f, time * 0.15f, MaDraw.SIGIL, 11, col, charge);
        }

        Vec3 body = p.getPosition(partial).add(0, p.getBbHeight() * 0.55, 0);
        Vector3f bc = MaDraw.vec(body.subtract(cam));
        int flags = s.flags();
        if ((flags & Payloads.CasterState.F_DEFLECT) != 0) {
            for (int i = 0; i < 3; i++) {
                double a = time * 0.12 + i * Math.PI * 2 / 3;
                Vector3f n = new Vector3f((float) Math.cos(a), 0, (float) Math.sin(a));
                Vector3f c = new Vector3f(bc).add(new Vector3f(n).mul(1.1f));
                MaDraw.rune(rune, pose, c, n, 0.55f, time * 0.2f, MaDraw.SHIELD, 20 + i, Ability.DEFLECTION.color(), 0.7f);
            }
        }
        if ((flags & Payloads.CasterState.F_ABSORB) != 0) {
            MaDraw.runeBillboard(rune, pose, bc, 1.4f, 0, MaDraw.RIPPLE, 30, Ability.ENERGY_ABSORPTION.color(), 0.7f);
        }
        if ((flags & Payloads.CasterState.F_KINETIC) != 0) {
            float flick = 0.5f + 0.5f * (float) Math.sin(time * 1.3);
            MaDraw.runeBillboard(rune, pose, bc, 1.3f + flick * 0.2f, time * 0.1f, MaDraw.STAR, 31, Source.POWER.color, 0.4f + flick * 0.3f);
        }
        float astral = anim.astral(partial);
        if (astral > 0.01f) {
            MaDraw.runeBillboard(rune, pose, bc, 1.3f, 0, MaDraw.HALO, 32, 0x9FD4FF, 0.45f * astral);
            MaDraw.rune(rune, pose, MaDraw.vec(p.getPosition(partial).add(0, 0.05, 0).subtract(cam)), new Vector3f(0, 1, 0), 1.1f, time * 0.05f, MaDraw.SIGIL, 33, 0x9FD4FF, 0.5f * astral);
        }
        // ultimate wind-up: a sigil under the caster's feet that grows with the charge
        if ((flags & Payloads.CasterState.F_CHARGING) != 0 && s.pose() == dev.mysticarts.power.Poses.RAISE) {
            MaDraw.rune(rune, pose, MaDraw.vec(p.getPosition(partial).add(0, 0.06, 0).subtract(cam)), new Vector3f(0, 1, 0), 2.5f + (float) Math.sin(time * 0.1) * 0.2f,
                    time * 0.04f, MaDraw.SIGIL, 34, chargeColor(p), 0.9f);
        }
    }

    private static int chargeColor(Player p) {
        Payloads.CasterState s = CasterStates.get(p.getId());
        if (s == null) return Source.MYSTIC.color;
        return dev.mysticarts.item.InfinityGauntletItem.held(p).isEmpty() ? Source.MYSTIC.color : Source.POWER.color;
    }

    /** An eldritch whip: a sagging, writhing ribbon of energy from the hand to the target. */
    private static void whip(VertexConsumer energy, PoseStack.Pose pose, Vec3 from, Vec3 to, Vec3 cam, float time, int color) {
        int n = 14;
        Vec3 prev = from.subtract(cam);
        Vec3 dir = to.subtract(from);
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
        for (int i = 1; i <= n; i++) {
            double t = i / (double) n;
            double sag = Math.sin(t * Math.PI) * (0.4 + 0.1 * Math.sin(time * 0.3));
            double wiggle = Math.sin(t * 9 - time * 0.9) * 0.18 * Math.sin(t * Math.PI);
            Vec3 at = from.add(dir.scale(t)).add(0, -sag, 0).add(side.scale(wiggle)).subtract(cam);
            float w = (float) (0.12 * (1 - t * 0.5));
            FxDraw.beam(energy, pose, prev, at, Vec3.ZERO, w, color, 0.95f);
            FxDraw.beam(energy, pose, prev, at, Vec3.ZERO, w * 3f, ColorUtil.scale(color, 0.5f), 0.35f);
            prev = at;
        }
    }

    private static void beam(Player p, Ability a, Vec3 from, VertexConsumer rune, VertexConsumer energy, PoseStack.Pose pose, Vec3 cam, float partial, float time,
                             ClientLevel level) {
        Vec3 look = p.getViewVector(partial);
        Vec3 to = from.add(look.scale(Sustained.BEAM_RANGE));
        BlockHitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, p));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(level, p, from, end, new AABB(from, end).inflate(1),
                e -> e instanceof LivingEntity && e != p && !e.isSpectator(), 0.5f);
        if (ent != null) end = ent.getLocation();
        int col = a.color();
        float width = a == Ability.POWER_BEAM ? 0.55f : 0.35f;
        float pulse = 1f + 0.12f * (float) Math.sin(time * 1.7);
        Vec3 f = from.subtract(cam), e = end.subtract(cam);
        FxDraw.beam(energy, pose, f, e, Vec3.ZERO, width * 2.6f * pulse, ColorUtil.scale(col, 0.6f), 0.5f);
        FxDraw.beam(energy, pose, f, e, Vec3.ZERO, width * pulse, col, 1f);
        FxDraw.beam(energy, pose, f, e, Vec3.ZERO, width * 0.35f, 0xFFFFFF, 0.9f);
        Vector3f n = MaDraw.vec(look);
        MaDraw.rune(rune, pose, MaDraw.vec(f), n, 0.6f, time * 0.25f, MaDraw.SIGIL, 40, col, 0.9f);
        // rings travelling down the beam
        double len = end.distanceTo(from);
        for (int i = 0; i < 4; i++) {
            double d = ((time * 0.6 + i * len / 4) % Math.max(1, len));
            Vec3 at = from.add(look.scale(d)).subtract(cam);
            MaDraw.rune(rune, pose, MaDraw.vec(at), n, width * 1.8f, time * 0.2f, MaDraw.RIPPLE, 41 + i, col, 0.6f);
        }
        MaDraw.runeBillboard(rune, pose, MaDraw.vec(e), width * 4f * pulse, time * 0.3f, MaDraw.STAR, 45, col, 0.9f);
        if (a == Ability.POWER_BEAM) WarpFx.sphere(end, 1.4f, WarpFx.HAZE, 1f, 0.5f, col);
    }

    // ============================================================================================ marks

    private static void marks(Entity ent, EnumMap<MarkKind, int[]> marks, VertexConsumer rune, VertexConsumer xray, VertexConsumer energy, PoseStack.Pose pose,
                              Vec3 cam, float partial, float time, ClientLevel level) {
        Vec3 feet = ent.getPosition(partial);
        float w = Math.max(0.5f, ent.getBbWidth()), h = ent.getBbHeight();
        Vector3f base = MaDraw.vec(feet.subtract(cam));
        Vector3f mid = new Vector3f(base).add(0, h * 0.5f, 0);
        Vector3f top = new Vector3f(base).add(0, h + 0.45f, 0);
        int seed = ent.getId() & 511;
        for (Map.Entry<MarkKind, int[]> e : marks.entrySet()) {
            MarkKind kind = e.getKey();
            int ticks = e.getValue()[0];
            int col = kind.color;
            switch (kind) {
                case BOUND -> {
                    for (int i = 0; i < 3; i++) {
                        float y = h * (0.25f + i * 0.3f);
                        Vector3f n = new Vector3f((float) Math.sin(time * 0.05 + i) * 0.25f, 1, (float) Math.cos(time * 0.04 + i) * 0.25f);
                        MaDraw.bandRing(rune, pose, new Vector3f(base).add(0, y, 0), n, w * 0.75f, 0.22f, seed + i, col, 0.95f, 28);
                    }
                    MaDraw.bandArc(rune, pose, top, new Vector3f(0, 0, 1).rotateY((float) Math.toRadians(-Minecraft.getInstance().gameRenderer.getMainCamera().getYRot())),
                            0.35f, 0.08f, ticks / 120f, seed, col, 0.9f);
                }
                case PRISON -> MaDraw.latticeCube(rune, pose, mid, Math.max(w, h) * 0.65f, seed, col, 0.85f);
                case FROZEN -> {
                    MaDraw.rune(rune, pose, new Vector3f(base).add(0, 0.05f, 0), new Vector3f(0, 1, 0), w * 1.2f, 0, MaDraw.CLOCK, seed, col, 0.5f);
                    MaDraw.runeBillboard(rune, pose, mid, Math.max(w, h) * 0.8f, 0, MaDraw.HALO, seed, col, 0.18f);
                }
                case SLOWED -> MaDraw.rune(rune, pose, new Vector3f(base).add(0, 0.05f, 0), new Vector3f(0, 1, 0), w, time * 0.01f, MaDraw.CLOCK, seed, col, 0.35f);
                case LOOPING -> MaDraw.bandRing(rune, pose, mid, new Vector3f(0, 0, 1).rotateY(time * 0.05f), Math.max(w, h) * 0.7f, 0.18f, seed, col, 0.8f, 32);
                case CONTROLLED -> MaDraw.rune(rune, pose, top, new Vector3f(0, 1, 0), 0.4f, time * 0.1f, MaDraw.SIGIL, seed, col, 0.9f);
                case PACIFIED -> MaDraw.rune(rune, pose, top, new Vector3f(0, 1, 0), 0.3f, 0, MaDraw.HALO, seed, col, 0.6f);
                case LIFTED -> MaDraw.rune(rune, pose, new Vector3f(base).add(0, -0.3f, 0), new Vector3f(0, 1, 0), w * 0.9f, time * 0.15f, MaDraw.RIPPLE, seed, col, 0.7f);
                case SEPARATED -> {
                    Vector3f soul = new Vector3f(mid).add((float) Math.sin(time * 0.07) * 0.6f, h * 0.6f, (float) Math.cos(time * 0.07) * 0.6f);
                    MaDraw.runeBillboard(rune, pose, soul, 0.5f, 0, MaDraw.HALO, seed, col, 0.8f);
                    FxDraw.beam(energy, pose, new Vec3(mid.x, mid.y, mid.z), new Vec3(soul.x, soul.y, soul.z), Vec3.ZERO, 0.06f, col, 0.6f);
                }
                case TETHERED -> {
                    Entity caster = level.getEntity(e.getValue()[1]);
                    if (caster != null) {
                        Vec3 to = caster.getPosition(partial).add(0, caster.getBbHeight() * 0.6, 0).subtract(cam);
                        MaDraw.bandLine(rune, pose, new Vec3(mid.x, mid.y, mid.z), to, Vec3.ZERO, 0.2f, seed, col, 0.8f);
                    }
                    MaDraw.bandRing(rune, pose, mid, new Vector3f(0, 1, 0), w * 0.7f, 0.15f, seed, col, 0.8f, 24);
                }
                case REVEALED -> {
                    int c = e.getValue()[1] == 1 ? 0xFF4A3A : e.getValue()[1] == 2 ? Source.SOUL.color : col;
                    MaDraw.runeBillboard(xray, pose, top, 0.28f, time * 0.05f, MaDraw.STAR, seed, c, 0.8f);
                }
                default -> {}
            }
        }
    }
}
