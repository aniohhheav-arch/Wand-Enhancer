package dev.telephoneorder.strike;

import dev.telephoneorder.Fx;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.Sched;
import dev.telephoneorder.net.Payloads;
import dev.telephoneorder.weapon.Boom;
import dev.telephoneorder.weapon.DamageKind;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Cinematic support strikes launched from a Strike Beacon: orbital strike, meteor barrages, planetary lance and the strategic
 * superweapon (demo-mode by default). Every sequence = radio call, siren, darkening sky, charge, impact, shockwave, aftermath.
 */
public final class StrikeDirector {
    private StrikeDirector() {}

    private static final Random RNG = new Random();
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static boolean weatherLocked;

    public static int cooldownTicks(String id) {
        return switch (id) { case "strike_strategic" -> 20 * 120; case "strike_lance" -> 20 * 90; case "strike_orbital" -> 20 * 45; default -> 20 * 20; };
    }

    /** returns false (and tells the player) if the strike cannot start. */
    public static boolean launch(ServerPlayer p, String id, Vec3 target) {
        ServerLevel l = (ServerLevel) p.level();
        long now = l.getGameTime();
        Long until = COOLDOWN.get(p.getUUID());
        if (until != null && until > now) {
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§cStrike platform recharging: " + ((until - now) / 20) + "s"));
            return false;
        }
        if (target.distanceTo(p.position()) < 14 && !id.startsWith("strike_meteor_precision")) {
            p.sendOverlayMessage(net.minecraft.network.chat.Component.literal("§cTarget too close - danger close denied. Aim at least 14 blocks away."));
            return false;
        }
        COOLDOWN.put(p.getUUID(), now + cooldownTicks(id));
        radio(p, l, target, id);
        switch (id) {
            case "strike_orbital" -> orbital(p, l, target);
            case "strike_meteor_precision" -> meteors(p, l, target, 5, 6, 90);
            case "strike_meteor_random" -> meteors(p, l, target, 14, 24, 160);
            case "strike_meteor_wide" -> meteors(p, l, target, 28, 48, 240);
            case "strike_lance" -> lance(p, l, target);
            case "strike_strategic" -> strategic(p, l, target);
            default -> { }
        }
        return true;
    }

    // ------------------------------------------------------------------------------------------------ shared
    private static void radio(ServerPlayer p, ServerLevel l, Vec3 t, String id) {
        String name = switch (id) { case "strike_orbital" -> "ORBITAL STRIKE"; case "strike_lance" -> "PLANETARY LANCE"; case "strike_strategic" -> "STRATEGIC ASSET";
            default -> "METEOR BARRAGE"; };
        Fx.privateSound(p, p.position(), "radio_beep", SoundSource.PLAYERS, 1.0f, 1f);
        ServerPlayNetworking.send(p, new Payloads.Notify(name + ": beacon acquired. Stand clear.", 0xFF6A5A));
        for (ServerPlayer o : l.players()) {
            if (o != p && o.position().distanceTo(t) < 160) ServerPlayNetworking.send(o, new Payloads.Notify("WARNING: " + name + " inbound near " + (int) t.x + ", " + (int) t.z, 0xFF6A5A));
        }
        // marker: pulsing ring + vertical smoke column
        for (int i = 0; i < 100; i += 5) {
            int ii = i;
            Sched.later(i, () -> {
                Fx.ring(l, Fx.dust(0xFF2020, 1.5f), t, 2 + (ii % 20) * 0.1, 24, 0.2);
                Fx.p(l, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, t, 1, 0.2, 0, 0.2, 0.01);
            });
        }
    }

    private static void siren(ServerLevel l, Vec3 t, int ticks) {
        for (int i = 0; i < ticks; i += 110) Sched.later(i, () -> Fx.sound(l, t, "siren", SoundSource.AMBIENT, 6.0f, 1f));
    }

    /** darken the sky with a thunderstorm for a while; restored afterwards. */
    private static void storm(ServerLevel l, int ticks) {
        MinecraftServer s = l.getServer();
        if (!l.canHaveWeather() || weatherLocked) return;
        var w = s.getWeatherData();
        boolean r = w.isRaining(), th = w.isThundering();
        int rt = w.getRainTime(), tt = w.getThunderTime();
        weatherLocked = true;
        w.setRaining(true); w.setThundering(true); w.setRainTime(ticks + 40); w.setThunderTime(ticks + 40);
        Sched.later(ticks + 20, () -> {
            w.setRaining(r); w.setThundering(th); w.setRainTime(Math.max(rt, 200)); w.setThunderTime(Math.max(tt, 200));
            weatherLocked = false;
        });
    }

    private static void damageZone(ServerLevel l, ServerPlayer p, Vec3 c, double radius, float dmg, DamageKind kind) {
        for (Entity e : l.getEntities((Entity) null, new AABB(c, c).inflate(radius, radius * 2, radius), x -> x instanceof LivingEntity && x.isAlive())) {
            double d = e.position().distanceTo(c);
            if (d > radius) continue;
            float f = (float) (1.0 - d / radius);
            if (e instanceof net.minecraft.world.entity.player.Player tp && tp != p && !p.canHarmPlayer(tp)) continue;
            dev.telephoneorder.weapon.Ballistics.damage(l, p, e, dmg * (0.25f + 0.75f * f), 0f, kind, null, false, true);
            dev.telephoneorder.weapon.Ballistics.impulse(e, e.position().subtract(c).normalize().scale(1.3 * f).add(0, 0.5 * f, 0));
        }
    }

    /** visual crater: optional real terrain damage */
    private static void crater(ServerLevel l, Vec3 c, int radius, boolean real) {
        BlockPos o = BlockPos.containing(c);
        for (int i = 0; i < 40; i++) {
            double a = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * radius;
            Vec3 p = c.add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
            Fx.blockImpact(l, BlockPos.containing(p).below(), p, 3);
        }
        Fx.ring(l, ParticleTypes.CLOUD, c, radius * 0.9, 36, 0.2);
        Fx.p(l, ParticleTypes.LARGE_SMOKE, c.add(0, 1, 0), 20, radius * 0.4, 0.6, radius * 0.4, 0.05);
        if (!real || !ModConfig.get().strikeTerrainDamage) return;
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos bp : BlockPos.betweenClosed(o.offset(-radius, -radius / 2, -radius), o.offset(radius, radius / 3, radius))) {
            double d = Math.sqrt(bp.distSqr(o));
            if (d <= radius && !l.getBlockState(bp).is(net.minecraft.tags.BlockTags.WITHER_IMMUNE) && l.getBlockState(bp).getDestroySpeed(l, bp) >= 0) l.setBlock(bp, air, 2);
        }
    }

    // ------------------------------------------------------------------------------------------------ orbital strike
    private static void orbital(ServerPlayer p, ServerLevel l, Vec3 t) {
        siren(l, t, 160);
        storm(l, 360);
        Fx.sound(l, t, "orbital_charge", SoundSource.AMBIENT, 6.0f, 1f);
        // platform glints overhead, clouds light up
        for (int i = 0; i < 80; i += 4) {
            int ii = i;
            Sched.later(40 + i, () -> {
                Vec3 sky = t.add(0, 70, 0);
                Fx.ring(l, Fx.dust(0xFF5030, 2.2f), sky, 3 + ii * 0.05, 18, 0);
                Fx.p(l, ParticleTypes.END_ROD, sky, 4, 3, 0.3, 3, 0.02);
                Fx.p(l, ParticleTypes.ELECTRIC_SPARK, sky, 6, 5, 2, 5, 0.3);
                Fx.flash(l, t, 80, 0x330000, 6);
            });
        }
        Sched.later(130, () -> {
            Fx.sound(l, t, "orbital_beam", SoundSource.AMBIENT, 8.0f, 1f);
            Fx.flash(l, t, 120, 0xFFFFFF, 14);
            for (int k = 0; k < 50; k++) {
                int kk = k;
                Sched.later(k, () -> {
                    double top = t.y + 130;
                    double r = 0.7 + 0.5 * Math.sin(kk * 0.5);
                    for (double y = t.y; y < top; y += 2.2) {
                        l.sendParticles(Fx.dust(0xFFEEDD, 3.0f), t.x, y, t.z, 1, r * 0.3, 0, r * 0.3, 0);
                        if (((int) y) % 3 == 0) l.sendParticles(ParticleTypes.END_ROD, t.x, y, t.z, 1, r * 0.4, 0, r * 0.4, 0.01);
                    }
                    Fx.ring(l, Fx.dust(0xFF8A40, 2.0f), t, 3 + (kk % 10) * 0.4, 24, 0.1);
                    Fx.p(l, ParticleTypes.FLAME, t, 6, 1.5, 0.3, 1.5, 0.15);
                    Fx.p(l, ParticleTypes.LARGE_SMOKE, t, 3, 1.5, 0.3, 1.5, 0.1);
                    if (kk % 5 == 0) { damageZone(l, p, t, 9, 18f, DamageKind.EXPLOSION); Fx.shake(l, t, 70, 0.9f, 8); }
                });
            }
            Sched.later(8, () -> shockwave(l, t, 26, 5));
            Sched.later(8, () -> crater(l, t, 7, true));
        });
    }

    private static void shockwave(ServerLevel l, Vec3 c, int maxR, int step) {
        for (int r = step; r <= maxR; r += step) {
            int rr = r;
            Sched.later(r / 2, () -> {
                Fx.ring(l, ParticleTypes.CLOUD, c, rr, 12 + rr * 3, 0.2);
                Fx.ring(l, ParticleTypes.POOF, c, rr * 0.8, 8 + rr, 0.5);
            });
        }
        Fx.sound(l, c, "mushroom_boom", SoundSource.AMBIENT, 8.0f, 1.2f);
    }

    // ------------------------------------------------------------------------------------------------ meteors
    private static void meteors(ServerPlayer p, ServerLevel l, Vec3 t, int count, double spread, int window) {
        siren(l, t, 120);
        storm(l, window + 120);
        for (int i = 0; i < count; i++) {
            int delay = 100 + RNG.nextInt(window);
            double a = RNG.nextDouble() * Math.PI * 2, r = spread * Math.sqrt(RNG.nextDouble());
            Vec3 hit = new Vec3(t.x + Math.cos(a) * r, t.y, t.z + Math.sin(a) * r);
            hit = new Vec3(hit.x, l.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(hit.x), (int) Math.floor(hit.z)), hit.z);
            Vec3 impact = hit;
            int fall = 36;
            Sched.later(delay - fall, () -> Fx.sound(l, impact.add(0, 40, 0), "incoming_whistle", SoundSource.AMBIENT, 4.0f, 1.3f));
            Vec3 dir = new Vec3(RNG.nextDouble() - 0.5, 0, RNG.nextDouble() - 0.5).normalize().scale(10);
            for (int k = 0; k <= fall; k += 2) {
                int kk = k;
                Sched.later(delay - fall + k, () -> {
                    double f = 1.0 - kk / (double) fall;
                    Vec3 pos = impact.add(dir.scale(f)).add(0, 70 * f * f + 1, 0);
                    Fx.p(l, ParticleTypes.FLAME, pos, 8, 0.4, 0.4, 0.4, 0.05);
                    Fx.p(l, ParticleTypes.LARGE_SMOKE, pos, 4, 0.6, 0.6, 0.6, 0.02);
                    l.sendParticles(Fx.dust(0xFFA040, 3.2f), pos.x, pos.y, pos.z, 4, 0.3, 0.3, 0.3, 0);
                });
            }
            Sched.later(delay, () -> {
                Boom.explode(l, impact, 5.5f, 22f, p, DamageKind.EXPLOSION, null, 1.3f, true);
                crater(l, impact, 4, true);
                Fx.shake(l, impact, 50, 0.7f, 10);
                Fx.p(l, ParticleTypes.LAVA, impact.add(0, 0.5, 0), 10, 1.5, 0.4, 1.5, 0);
            });
        }
    }

    // ------------------------------------------------------------------------------------------------ planetary lance
    private static void lance(ServerPlayer p, ServerLevel l, Vec3 t) {
        siren(l, t, 300);
        storm(l, 520);
        Fx.sound(l, t, "orbital_charge", SoundSource.AMBIENT, 8.0f, 0.6f);
        int charge = 200;
        for (int i = 0; i < charge; i += 2) {
            int ii = i;
            Sched.later(i, () -> {
                double f = ii / (double) charge;
                Vec3 focus = t.add(0, 90, 0);
                for (int arm = 0; arm < 4; arm++) {
                    double a = ii * 0.12 + arm * Math.PI / 2;
                    double r = 40 * (1 - f) + 2;
                    Vec3 pt = focus.add(Math.cos(a) * r, -f * 10, Math.sin(a) * r);
                    Fx.p(l, ParticleTypes.CLOUD, pt, 2, 1, 0.3, 1, 0.01);
                    l.sendParticles(Fx.dust(0xB070FF, 2.0f + (float) f * 2), pt.x, pt.y, pt.z, 1, 0.2, 0.2, 0.2, 0);
                }
                Fx.p(l, ParticleTypes.END_ROD, focus, 3, 1 + f * 3, 1 + f * 3, 1 + f * 3, 0.05);
                if (ii % 20 == 0) Fx.flash(l, t, 140, 0x2A0050, 10);
                if (ii > 140 && ii % 6 == 0) Fx.ring(l, Fx.dust(0xFFFFFF, 1.5f), t, 14 * (1 - (ii - 140) / 60.0) + 2, 30, 0.1);
            });
        }
        Sched.later(charge + 10, () -> {
            Fx.sound(l, t, "orbital_beam", SoundSource.AMBIENT, 10.0f, 0.7f);
            Fx.flash(l, t, 160, 0xFFFFFF, 20);
            for (int k = 0; k < 70; k++) {
                int kk = k;
                Sched.later(k, () -> {
                    double sweep = kk * 0.09;
                    Vec3 c = t.add(Math.cos(sweep) * Math.min(kk, 40) * 0.25, 0, Math.sin(sweep) * Math.min(kk, 40) * 0.25);
                    for (double y = c.y; y < c.y + 150; y += 2.0) {
                        l.sendParticles(Fx.dust(0xE8D0FF, 4.0f), c.x, y, c.z, 2, 1.0, 0, 1.0, 0);
                        if (((int) y) % 4 == 0) l.sendParticles(ParticleTypes.END_ROD, c.x, y, c.z, 1, 1.2, 0, 1.2, 0.02);
                    }
                    Fx.ring(l, Fx.dust(0xB070FF, 2.5f), c, 5 + (kk % 12), 36, 0.1);
                    Fx.p(l, ParticleTypes.FLAME, c, 10, 3, 0.3, 3, 0.2);
                    Fx.p(l, ParticleTypes.EXPLOSION, c.add(0, 1, 0), 1, 2, 0.2, 2, 0);
                    if (kk % 4 == 0) { damageZone(l, p, c, 14, 26f, DamageKind.ENERGY); Fx.shake(l, c, 100, 1.2f, 10); if (kk % 8 == 0) crater(l, c, 8, true); }
                });
            }
            Sched.later(10, () -> shockwave(l, t, 50, 6));
        });
    }

    // ------------------------------------------------------------------------------------------------ strategic superweapon
    private static void strategic(ServerPlayer p, ServerLevel l, Vec3 t) {
        boolean demo = ModConfig.get().strategicDemoMode;
        ServerPlayNetworking.send(p, new Payloads.Notify(demo ? "DEMONSTRATION MODE: visual only, no damage" : "LIVE FIRE: STRATEGIC ASSET RELEASED", demo ? 0x7CFFB0 : 0xFF4040));
        siren(l, t, 440);
        storm(l, 800);
        for (int i = 0; i < 400; i += 20) {
            int ii = i;
            Sched.later(i, () -> { Fx.flash(l, t, 200, 0x300000, 10); if (ii % 40 == 0) Fx.p(l, ParticleTypes.ELECTRIC_SPARK, t.add(0, 60, 0), 20, 8, 4, 8, 0.4); });
        }
        Sched.later(420, () -> {
            Fx.sound(l, t, "mushroom_boom", SoundSource.AMBIENT, 12.0f, 0.8f);
            Fx.flash(l, t, 300, 0xFFFFFF, 50);
            Fx.shake(l, t, 160, 1.4f, 60);
            if (!demo) { damageZone(l, p, t, 36, 80f, DamageKind.EXPLOSION); crater(l, t, 16, true); }
            else crater(l, t, 10, false);
            // fireball
            for (int k = 0; k < 20; k++) {
                int kk = k;
                Sched.later(k * 2, () -> {
                    double r = 3 + kk * 0.8;
                    Fx.p(l, ParticleTypes.FLAME, t.add(0, r * 0.6, 0), 30, r * 0.5, r * 0.4, r * 0.5, 0.08);
                    Fx.p(l, ParticleTypes.EXPLOSION, t.add(0, r * 0.5, 0), 1, r * 0.5, r * 0.3, r * 0.5, 0);
                    l.sendParticles(Fx.dust(0xFFC060, 4.0f), t.x, t.y + r * 0.6, t.z, 10, r * 0.5, r * 0.4, r * 0.5, 0);
                });
            }
            // rising stem + spreading cap (mushroom-shaped cloud), kept cheap
            int rise = 110;
            for (int k = 0; k < rise; k += 2) {
                int kk = k;
                Sched.later(20 + k, () -> {
                    double h = 4 + kk * 0.65;
                    Fx.p(l, ParticleTypes.LARGE_SMOKE, t.add(0, h, 0), 6, 2.2, 0.8, 2.2, 0.01);
                    Fx.p(l, ParticleTypes.CAMPFIRE_COSY_SMOKE, t.add(0, h * 0.7, 0), 2, 1.5, 0.8, 1.5, 0.005);
                    l.sendParticles(Fx.dust(0x6A5040, 3.5f), t.x, t.y + h, t.z, 6, 1.8, 0.8, 1.8, 0);
                    if (kk > 60) {
                        double capR = Math.min(20, (kk - 60) * 0.5);
                        Vec3 cap = t.add(0, 4 + 60 * 0.65 + (kk - 60) * 0.55, 0);
                        Fx.ring(l, ParticleTypes.LARGE_SMOKE, cap, capR, 30, 0);
                        Fx.ring(l, Fx.dust(0xFF9040, 3.0f), cap.add(0, -1.5, 0), capR * 0.8, 24, 0);
                        Fx.p(l, ParticleTypes.CLOUD, cap, 8, capR * 0.5, 2, capR * 0.5, 0.01);
                    }
                });
            }
            Sched.later(10, () -> shockwave(l, t, 60, 6));
        });
    }
}
