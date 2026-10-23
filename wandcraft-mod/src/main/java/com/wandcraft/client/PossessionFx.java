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

    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> ev) {
        float el = elapsed(ev.getEntity(), ev.getPartialTick());
        if (el < 0) return;
        PoseStack ps = ev.getPoseStack();
        ps.pushPose();
        float sx = 1, sy = 1;
        float rnd = (float) Math.sin(el * 91.7) * 0.5f + 0.5f;
        if (el < Possession.FLICKER) {
            // uneasy flicker: the mob stutters out of existence for a frame now and then
            if (Math.sin(el * 7.3) + Math.sin(el * 3.1 + 1) > 1.55 + (1 - el / Possession.FLICKER) * 0.1) {
                ps.popPose();
                ev.setCanceled(true);
                return;
            }
            float j = 0.04f * el / Possession.FLICKER;
            ps.translate((rnd - 0.5f) * j, 0, (Math.sin(el * 53) * 0.5) * j);
        } else if (el < Possession.FLICKER + Possession.DISSOLVE) {
            float k = (el - Possession.FLICKER) / Possession.DISSOLVE;
            if (Config.DISSOLVE_SHADER.get()) {
                sy = 1f - 0.9f * ease(k);
                float sway = 1f - 0.45f * k * k;
                sx = sway;
                ps.translate((rnd - 0.5f) * 0.05f * (1 - k), 0, 0);
            } else {
                sx = sy = 1f - 0.9f * k;
            }
        } else {
            float u = Mth.clamp((el - Possession.FLICKER - Possession.DISSOLVE) / Possession.IMPLODE, 0, 1);
            float f = (1f - u) * (1f - u);
            sx = Math.max(0.01f, 0.1f * f);
            sy = Math.max(0.01f, 0.1f * f + 0.02f);
            ps.mulPose(com.mojang.math.Axis.YP.rotationDegrees(u * 540f));
        }
        ps.scale(sx, sy, sx);
    }

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> ev) {
        if (elapsed(ev.getEntity(), ev.getPartialTick()) >= 0) ev.getPoseStack().popPose();
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
