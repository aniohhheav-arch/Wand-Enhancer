package com.wandcraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.wandcraft.Config;
import com.wandcraft.WandCraft;
import com.wandcraft.magic.Possession;
import com.wandcraft.magic.util.Fx;
import com.wandcraft.magic.util.ShadowFx;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Client visuals for a possessed mob: flicker with glowing eyes and spreading veins, a progressive top-down crumble
 * with shedding extremities, then a violent inward implosion. Everything is derived from the elapsed time the server sends.
 */
@EventBusSubscriber(modid = WandCraft.ID, value = Dist.CLIENT)
public final class PossessionFx {
    private PossessionFx() {}

    private static float elapsed(Entity e, float pt) {
        Long start = Possession.clientView().get(e.getId());
        if (start == null) return -1;
        return (e.level().getGameTime() - start) + pt;
    }

    private static float ease(float x) { return x * x * (3 - 2 * x); }

    private static boolean inner;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> ev) {
        if (inner) return;
        Entity e = ev.getEntity();
        float pt = ev.getPartialTick();
        float el = elapsed(e, pt);
        if (el < 0) return;
        PoseStack ps = ev.getPoseStack();
        float rnd = (float) Math.sin(el * 91.7) * 0.5f + 0.5f;
        if (el < Possession.FLICKER) {
            // uneasy flicker: the mob stutters out of existence for a frame now and then
            if (Math.sin(el * 7.3) + Math.sin(el * 3.1 + 1) > 1.55 + (1 - el / Possession.FLICKER) * 0.1) {
                ev.setCanceled(true);
                return;
            }
            float j = 0.04f * el / Possession.FLICKER;
            ps.pushPose();
            ps.translate((rnd - 0.5f) * j, 0, (Math.sin(el * 53) * 0.5) * j);
            pushed.add(e.getId());
            return;
        }
        float k, shrink = 1f;
        if (el < Possession.FLICKER + Possession.DISSOLVE) {
            k = (el - Possession.FLICKER) / Possession.DISSOLVE;
            k = 0.82f * ease(k);
        } else {
            float u = Mth.clamp((el - Possession.FLICKER - Possession.DISSOLVE) / Possession.IMPLODE, 0, 1);
            k = 0.82f + 0.18f * u;
            shrink = Math.max(0.02f, (1f - u) * (1f - u));
        }
        ps.pushPose();
        ps.translate((rnd - 0.5f) * 0.03f, 0, 0);
        if (shrink < 1f) {
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees((1 - shrink) * 540f));
            ps.scale(shrink, shrink, shrink);
        }
        if (Config.DISSOLVE_SHADER.get()) {
            var cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            double x = Mth.lerp(pt, e.xo, e.getX()) - cam.x, y = Mth.lerp(pt, e.yo, e.getY()) - cam.y, z = Mth.lerp(pt, e.zo, e.getZ()) - cam.z;
            var r = (net.minecraft.client.renderer.entity.LivingEntityRenderer) ev.getRenderer();
            inner = true;
            try {
                double h = e.getBbHeight() * (shrink < 1f ? shrink : 1f);
                float yaw = Mth.rotLerp(pt, e.yRotO, e.getYRot());
                // pass 1 lays down the noise-cut depth, pass 2 paints the surviving body on top of it
                r.render(e, yaw, pt, ps, new DissolveBuffer(ev.getMultiBufferSource(), true, k, x, y, z, h), ev.getPackedLight());
                r.render(e, yaw, pt, ps, new DissolveBuffer(ev.getMultiBufferSource(), false, k, x, y, z, h), ev.getPackedLight());
            } finally {
                inner = false;
            }
            ps.popPose();
            ev.setCanceled(true);
        } else {
            float s = 1f - 0.9f * k;
            ps.scale(s, s, s);
            pushed.add(e.getId());
        }
    }

    private static final java.util.Set<Integer> pushed = new java.util.HashSet<>();

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> ev) {
        if (pushed.remove(ev.getEntity().getId())) ev.getPoseStack().popPose();
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post ev) {
        ClientLevel l = Minecraft.getInstance().level;
        if (l == null || Possession.clientView().isEmpty()) return;
        for (Iterator<Map.Entry<Integer, Long>> it = Possession.clientView().entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Integer, Long> en = it.next();
            Entity e = l.getEntity(en.getKey());
            if (e == null) {
                it.remove();
                continue;
            }
            int el = (int) (l.getGameTime() - en.getValue());
            double h = e.getBbHeight(), w = Math.max(0.4, e.getBbWidth());
            Vec3 base = e.position();
            if (el < Possession.FLICKER + Possession.DISSOLVE) {
                // glowing eyes
                Vec3 eye = e.getEyePosition();
                Vec3 look = Vec3.directionFromRotation(0, e.getYRot());
                for (int s = -1; s <= 1; s += 2) {
                    Vec3 p = eye.add(look.scale(w * 0.4)).add(-look.z * 0.13 * s, 0, look.x * 0.13 * s);
                    Fx.c(l, Fx.glow(0xE9C8FF, 0.5f), p.x, p.y, p.z, 0, 0, 0);
                }
                // veins creeping across the body, spreading with time
                int veins = 1 + el / 8;
                for (int i = 0; i < veins; i++) {
                    double a = l.random.nextDouble() * Math.PI * 2, y = l.random.nextDouble() * h;
                    Fx.c(l, Fx.spark(l.random.nextInt(3) == 0 ? 0xE0B0FF : 0x8E3BFF, 0.3f), base.x + Math.cos(a) * w * 0.55, base.y + y, base.z + Math.sin(a) * w * 0.55, 0, 0.004, 0);
                }
            }
            if (el >= Possession.FLICKER && el < Possession.FLICKER + Possession.DISSOLVE) {
                float k = (el - Possession.FLICKER) / (float) Possession.DISSOLVE;
                double top = h * (1 - 0.9 * ease(k));
                int n = 2 + (int) (k * 6);
                for (int i = 0; i < n; i++) {
                    double a = l.random.nextDouble() * Math.PI * 2, r = l.random.nextDouble() * w * 0.55;
                    // crumbling edge of the body plus extremities shedding sideways
                    Fx.c(l, i % 3 == 0 ? ShadowFx.ash(0.7f) : ShadowFx.smoke(0.8f), base.x + Math.cos(a) * r, base.y + top - l.random.nextDouble() * 0.25, base.z + Math.sin(a) * r,
                            Math.cos(a) * 0.02, -0.01 + l.random.nextDouble() * 0.03, Math.sin(a) * 0.02);
                }
                if (el % 3 == 0) {
                    for (int s = -1; s <= 1; s += 2) {
                        Vec3 look = Vec3.directionFromRotation(0, e.getYRot());
                        double hy = base.y + h * (0.35 + 0.1 * l.random.nextDouble());
                        Fx.c(l, ShadowFx.ash(0.6f), base.x - look.z * w * 0.7 * s, hy, base.z + look.x * w * 0.7 * s, -look.z * s * 0.03, -0.02, look.x * s * 0.03);
                    }
                    Fx.c(l, ShadowFx.ash(0.6f), base.x, base.y + 0.1, base.z, (l.random.nextDouble() - .5) * 0.04, 0.02, (l.random.nextDouble() - .5) * 0.04);
                }
            } else if (el >= Possession.FLICKER + Possession.DISSOLVE && el < Possession.TOTAL) {
                Vec3 c = base.add(0, h * 0.4, 0);
                for (int i = 0; i < 8; i++) {
                    double u = l.random.nextDouble() * 2 - 1, a = l.random.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - u * u), r = 1.6;
                    Vec3 d = new Vec3(q * Math.cos(a), u, q * Math.sin(a));
                    Vec3 v = d.scale(-0.16);
                    Fx.c(l, i % 2 == 0 ? Fx.glow(ShadowFx.VIOLET, 0.5f) : ShadowFx.smoke(0.8f), c.x + d.x * r, c.y + d.y * r, c.z + d.z * r, v.x, v.y, v.z);
                }
            }
        }
    }

    @SubscribeEvent
    public static void leave(ClientPlayerNetworkEvent.LoggingOut ev) { Possession.clientClear(); }
}
