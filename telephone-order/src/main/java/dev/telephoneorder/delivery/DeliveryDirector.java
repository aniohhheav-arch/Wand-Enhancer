package dev.telephoneorder.delivery;

import com.mojang.math.Transformation;
import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.Fx;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.Sched;
import dev.telephoneorder.net.Payloads;
import dev.telephoneorder.phone.Listing;
import dev.telephoneorder.weapon.GunData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntFunction;

/**
 * Plays the five cinematic delivery sequences (crate, drone, supply pod, teleport container, containment case / rift) and finally
 * drops the real, collectable items. Props are item_display entities; motion is client-interpolated; everything else is particles,
 * positional sound, camera shake and screen flashes.
 */
public final class DeliveryDirector {
    private DeliveryDirector() {}

    private static final Random RNG = new Random();
    private static final Set<UUID> LIVE = new HashSet<>();

    public static void init() {
        // props never survive a restart: clean up strays left over from a previous run
        ServerEntityEvents.ENTITY_LOAD.register((e, level) -> {
            if (e.entityTags().contains("tod_prop") && !LIVE.contains(e.getUUID())) e.discard();
        });
    }

    // ------------------------------------------------------------------------------------------------ props
    public static final class Prop {
        final Display.ItemDisplay d;
        final float scale;
        public Vector3f pos;
        public Prop(ServerLevel l, Vec3 anchor, Item item, Vector3f t, float scale, Quaternionf rot) {
            this.scale = scale;
            this.pos = new Vector3f(t);
            d = new Display.ItemDisplay(EntityTypes.ITEM_DISPLAY, l);
            d.setPos(anchor.x, anchor.y, anchor.z);
            d.setItemStack(new ItemStack(item));
            d.setItemTransform(ItemDisplayContext.NONE);
            d.setBillboardConstraints(Display.BillboardConstraints.FIXED);
            d.setTransformation(new Transformation(new Vector3f(t), new Quaternionf(rot), new Vector3f(scale), new Quaternionf()));
            d.setViewRange(5.0f);
            d.setWidth(90f);
            d.setHeight(90f);
            d.setBrightnessOverride(new net.minecraft.util.Brightness(10, 12));
            d.addTag("tod_prop");
            LIVE.add(d.getUUID());
            l.addFreshEntity(d);
        }
        public void to(Vector3f t, float s, Quaternionf rot, int ticks) {
            pos = new Vector3f(t);
            d.setTransformation(new Transformation(new Vector3f(t), new Quaternionf(rot), new Vector3f(s), new Quaternionf()));
            d.setTransformationInterpolationDuration(Math.max(1, ticks));
            d.setTransformationInterpolationDelay(0);
        }
        public void item(Item item) { d.setItemStack(new ItemStack(item)); }
        public void kill() { LIVE.remove(d.getUUID()); d.discard(); }
    }

    private static Quaternionf yaw(float deg) { return new Quaternionf().rotateY((float) Math.toRadians(deg)); }
    private static Quaternionf euler(float x, float y, float z) {
        return new Quaternionf().rotateYXZ((float) Math.toRadians(y), (float) Math.toRadians(x), (float) Math.toRadians(z));
    }

    // ------------------------------------------------------------------------------------------------ context
    static final class Ctx {
        ServerPlayer p; ServerLevel l; Vec3 g; int h; Listing li; Runnable done; int oid; boolean cave;
        boolean released;
    }

    public static void start(ServerPlayer p, Listing li, String method, Runnable done, int oid) {
        if (!(p.level() instanceof ServerLevel l)) return;
        Ctx c = new Ctx();
        c.p = p; c.l = l; c.li = li; c.done = done; c.oid = oid;
        c.g = findLanding(l, p);
        c.h = clearance(l, c.g);
        c.cave = c.h < 10;
        ServerPlayNetworking.send(p, new Payloads.Notify("Delivery inbound: " + li.name(), 0xFFC54A));
        try {
            switch (method) {
                case "drone" -> drone(c);
                case "pod" -> pod(c);
                case "teleport" -> teleport(c);
                case "case" -> caseRift(c, li.secret() != null || (li.gun() != null && li.gun().tier() >= 4));
                default -> crate(c);
            }
        } catch (RuntimeException e) {
            dev.telephoneorder.TelephoneOrder.LOGGER.error("Delivery sequence failed, releasing items directly", e);
            release(c);
        }
    }

    // ------------------------------------------------------------------------------------------------ landing
    private static Vec3 findLanding(ServerLevel l, ServerPlayer p) {
        Vec3 base = p.position();
        for (int attempt = 0; attempt < 24; attempt++) {
            double a = RNG.nextDouble() * Math.PI * 2;
            double d = attempt < 14 ? 6 + RNG.nextDouble() * 5 : 3 + RNG.nextDouble() * 3;
            int x = Mth.floor(base.x + Math.cos(a) * d), z = Mth.floor(base.z + Math.sin(a) * d);
            for (int y = Mth.floor(base.y) + 6; y >= Mth.floor(base.y) - 10; y--) {
                BlockPos bp = new BlockPos(x, y, z);
                if (!l.getBlockState(bp.below()).isFaceSturdy(l, bp.below(), Direction.UP)) continue;
                AABB box = new AABB(x + 0.1, y, z + 0.1, x + 0.9, y + 2.4, z + 0.9);
                if (l.noCollision(box) && l.getFluidState(bp).isEmpty()) return new Vec3(x + 0.5, y, z + 0.5);
            }
        }
        return base.add(p.getViewVector(1f).scale(2.5));
    }

    private static int clearance(ServerLevel l, Vec3 g) {
        BlockPos bp = BlockPos.containing(g);
        int n = 0;
        while (n < 48 && l.getBlockState(bp.above(n + 2)).getCollisionShape(l, bp.above(n + 2)).isEmpty()) n++;
        return n;
    }

    // ------------------------------------------------------------------------------------------------ helpers
    private static Vector3f v(double x, double y, double z) { return new Vector3f((float) x, (float) y, (float) z); }

    private static void warn(Ctx c, int ticks) {
        for (int t = 0; t < ticks; t += 6) {
            int tt = t;
            Sched.later(t, () -> {
                double r = 1.2 + 0.4 * Math.sin(tt * 0.5);
                Fx.ring(c.l, Fx.dust(0xFF4030, 1.2f), c.g, r, 20, 0.1);
                if (tt % 12 == 0) Fx.ring(c.l, Fx.dust(0xFFFFFF, 0.8f), c.g, r + 0.9, 28, 0.1);
            });
        }
    }

    private static void impactFx(Ctx c, float shake, double shakeRadius, int dustCount, String sound, float vol, float pitch) {
        ServerLevel l = c.l;
        Fx.sound(l, c.g, sound, SoundSource.AMBIENT, vol, pitch);
        Fx.shake(l, c.g, shakeRadius, shake, 14);
        Fx.blockImpact(l, BlockPos.containing(c.g).below(), c.g.add(0, 0.2, 0), dustCount);
        Fx.p(l, ParticleTypes.EXPLOSION, c.g.add(0, 0.6, 0), 1, 0, 0, 0, 0);
        Fx.p(l, ParticleTypes.POOF, c.g.add(0, 0.4, 0), 18, 1.1, 0.2, 1.1, 0.08);
        Fx.ring(l, ParticleTypes.CLOUD, c.g, 2.4, 28, 0.15);
        for (Entity e : l.getEntities(c.p, new AABB(c.g, c.g).inflate(4.5), x -> x.isAlive() && !x.isSpectator() && !(x instanceof Display))) {
            Vec3 away = e.position().subtract(c.g).multiply(1, 0, 1);
            if (away.lengthSqr() < 0.01) away = new Vec3(0.1, 0, 0);
            dev.telephoneorder.weapon.Ballistics.impulse(e, away.normalize().scale(0.55).add(0, 0.25, 0));
        }
    }

    private static List<ItemStack> contents(Ctx c) {
        List<ItemStack> out = new ArrayList<>();
        Listing li = c.li;
        if (li.gun() != null) {
            GunDef g = li.gun();
            ItemStack gun = GunData.newStack(g, ModItems.GUNS.get(g.id()));
            out.add(gun);
            if (!g.beh().equals("turret")) out.add(new ItemStack(ModItems.AMMO.get(g.ammo()), g.energy() ? 1 : 2));
        } else if (li.att() != null) {
            out.add(new ItemStack(ModItems.ATTACHMENTS.get(li.att().id()), 1));
        } else if (li.strike() != null) {
            out.add(new ItemStack(ModItems.ALL.get(li.strike()), 1));
        } else if (li.ammo() != null) {
            out.add(new ItemStack(ModItems.AMMO.get(li.ammo().id()), li.count()));
        }
        ItemStack receipt = new ItemStack(ModItems.ORDER_RECEIPT);
        CompoundTag t = new CompoundTag();
        t.putString("name", li.name());
        t.putString("method", c.li.delivery());
        t.putInt("day", (int) (c.l.getServer().overworld().getGameTime() / 24000L));
        t.putInt("oid", c.oid);
        t.putInt("price", li.price());
        t.putString("buyer", c.p.getGameProfile().name());
        receipt.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
        out.add(receipt);
        return out;
    }

    private static void release(Ctx c) {
        if (c.released) return;
        c.released = true;
        ServerLevel l = c.l;
        Vec3 at = c.g.add(0, 0.9, 0);
        List<ItemStack> items = contents(c);
        for (int i = 0; i < items.size(); i++) {
            double a = i * 2.4;
            ItemEntity e = new ItemEntity(l, at.x, at.y, at.z, items.get(i));
            e.setDeltaMovement(Math.cos(a) * 0.12, 0.28, Math.sin(a) * 0.12);
            e.setGlowingTag(true);
            e.setTarget(c.p.getUUID());
            e.setPickUpDelay(10);
            e.setUnlimitedLifetime();
            l.addFreshEntity(e);
            UUID id = e.getUUID();
            Sched.later(6000, () -> { Entity en = l.getEntity(id); if (en instanceof ItemEntity ie) ie.setTarget(null); });
        }
        Fx.sound(l, at, "item_ready", SoundSource.AMBIENT, 1.2f, 1f);
        Fx.p(l, ParticleTypes.END_ROD, at, 24, 0.5, 0.5, 0.5, 0.12);
        ServerPlayNetworking.send(c.p, new Payloads.Notify("Delivered: " + c.li.name(), 0x7CFFB0));
        c.done.run();
    }

    private static void fade(Prop pr, int ticks) {
        pr.to(pr.pos, 0.01f, new Quaternionf(), ticks);
        Sched.later(ticks + 2, pr::kill);
    }

    /** Smoothly moves props along a path in 2 tick steps. */
    private static void animate(int delay, int duration, List<Prop> props, List<Vector3f> offsets, float scale, IntFunction<Vector3f> base, IntFunction<Quaternionf> rot, Runnable each) {
        for (int t = 0; t < duration; t += 2) {
            int tt = t;
            Sched.later(delay + t, () -> {
                int n = Math.min(duration, tt + 2);
                Vector3f b = base.apply(n);
                Quaternionf q = rot.apply(n);
                for (int i = 0; i < props.size(); i++) {
                    Vector3f o = offsets.get(i);
                    props.get(i).to(new Vector3f(b).add(o), scale, q, n - tt);
                }
                if (each != null) each.run();
            });
        }
    }

    private static Vec3 worldPos(Ctx c, Vector3f t, float s) { return c.g.add(t.x, t.y - 0.5 * s + 0.5 * s, t.z); }

    // ------------------------------------------------------------------------------------------------ 1. standard shipping crate
    private static void crate(Ctx c) {
        ServerLevel l = c.l;
        float s = 1.5f;
        int H = Math.max(6, Math.min(c.h - 1, 38));
        int fall = Mth.clamp((int) (H / 1.0), 14, 40);
        Fx.sound(l, c.g, "incoming_whistle", SoundSource.AMBIENT, 4.0f, 1.0f);
        warn(c, 20 + fall);
        Sched.later(20, () -> {
            Prop crate = new Prop(l, c.g, ModItems.PROP_CRATE, v(0, 0.5 * s + H, 0), s, euler(0, RNG.nextInt(360), 8));
            animate(1, fall, List.of(crate), List.of(new Vector3f()), s,
                    t -> v(0, 0.5 * s + H * (1.0 - t / (double) fall), 0), t -> euler(0, 20 + t * 2, 8f * (1f - t / (float) fall)), () -> {
                        Vec3 p = c.g.add(0, crate.pos.y - 0.5 * s + 0.4 * s, 0);
                        Fx.p(l, ParticleTypes.LARGE_SMOKE, p.add(0, 0.7, 0), 1, 0.2, 0.1, 0.2, 0.01);
                        Fx.p(l, ParticleTypes.FLAME, p.add(0, 0.9, 0), 1, 0.15, 0.1, 0.15, 0.01);
                    });
            Sched.later(fall + 2, () -> {
                impactFx(c, 0.7f, 28, 30, "crate_impact", 3.2f, 1f);
                crate.to(v(0, 0.5 * s + 0.12, 0), s, euler(0, 25, 0), 2);
                Sched.later(3, () -> crate.to(v(0, 0.5 * s, 0), s, euler(0, 25, 0), 3));
            });
            Sched.later(fall + 34, () -> {
                crate.item(ModItems.PROP_CRATE_OPEN);
                Fx.sound(l, c.g, "case_latch", SoundSource.AMBIENT, 1.2f, 0.7f);
                Fx.p(l, ParticleTypes.POOF, c.g.add(0, 1.2, 0), 14, 0.5, 0.2, 0.5, 0.03);
                release(c);
            });
            Sched.later(fall + 220, () -> fade(crate, 14));
        });
    }

    // ------------------------------------------------------------------------------------------------ 2. courier drone
    private static void drone(Ctx c) {
        ServerLevel l = c.l;
        float s = 2.3f;
        float hover = Math.max(3.5f, Math.min(7f, c.h - 1.5f));
        double ang = RNG.nextDouble() * Math.PI * 2;
        double sx = Math.cos(ang) * 30, sz = Math.sin(ang) * 30;
        float startY = hover + Math.min(14, Math.max(2, c.h - hover - 1));
        List<Prop> props = new ArrayList<>();
        List<Vector3f> offs = new ArrayList<>();
        Prop body = new Prop(l, c.g, ModItems.PROP_DRONE, v(sx, startY + 0.34 * s, sz), s, yaw((float) Math.toDegrees(ang) + 90));
        props.add(body); offs.add(new Vector3f(0, 0.34f * s, 0));
        float rx = 0.2875f * s;
        float[][] rp = {{rx, rx}, {-rx, rx}, {rx, -rx}, {-rx, -rx}};
        for (float[] r : rp) {
            props.add(new Prop(l, c.g, ModItems.PROP_ROTOR, v(sx + r[0], startY + 0.5 * s, sz + r[1]), s, new Quaternionf()));
            offs.add(new Vector3f(r[0], 0.5f * s - 0.34f * s + 0.34f * s - 0.0f, r[1]));
        }
        // fix rotor offsets relative to body centre (rotor blades sit at model-centre height, just above the motors)
        for (int i = 1; i < offs.size(); i++) offs.get(i).y = 0.62f * s - 0.0f;
        float[] spin = {0};
        int approach = 80, lower = 40, hold = 12, leave = 60;
        Vector3f hoverPos = v(0, hover, 0);
        // buzz
        for (int t = 0; t < approach + lower + hold + leave; t += 7) {
            int tt = t;
            Sched.later(t, () -> {
                Vector3f b = body.pos;
                Fx.sound(l, c.g.add(b.x, b.y, b.z), "drone_buzz", SoundSource.AMBIENT, 3.0f, 1.0f + 0.1f * (float) Math.sin(tt * 0.3));
            });
        }
        IntFunction<Quaternionf> rotor = t -> new Quaternionf().rotateY((float) Math.toRadians((t * 70) % 360));
        // approach
        animate(0, approach, props, offs, s, t -> {
            double f = 1 - Math.pow(1 - t / (double) approach, 2);
            return v(sx * (1 - f), Mth.lerp(f, startY, hover) + 0.34 * s, sz * (1 - f));
        }, t -> yaw((float) Math.toDegrees(ang) + 90 + (t < approach ? 0 : 0)), null);
        // spin rotors separately (they ignore body rotation)
        for (int t = 0; t < approach + lower + hold + leave + 6; t += 2) {
            int tt = t;
            Sched.later(t, () -> {
                for (int i = 1; i < props.size(); i++) {
                    Prop r = props.get(i);
                    r.d.setTransformation(new Transformation(new Vector3f(r.pos), new Quaternionf().rotateY((float) Math.toRadians((tt * 60) % 360)), new Vector3f(s), new Quaternionf()));
                    r.d.setTransformationInterpolationDuration(2);
                    r.d.setTransformationInterpolationDelay(0);
                }
            });
        }
        // lower the cargo on a winch
        Sched.later(approach, () -> {
            float cs = 1.0f;
            Prop cargo = new Prop(l, c.g, ModItems.PROP_CRATE, v(0, hover - 0.9 * s + 0.5 * cs, 0), cs, yaw(20));
            animate(1, lower, List.of(cargo), List.of(new Vector3f()), cs, t -> v(0, Mth.lerp(t / (double) lower, hover - 0.9 * s + 0.5 * cs, 0.5 * cs), 0), t -> yaw(20 + t), () -> {
                Vec3 top = c.g.add(0, hover, 0);
                Vec3 bot = c.g.add(0, cargo.pos.y + 0.4, 0);
                Fx.line(l, Fx.dust(0x999999, 0.5f), top, bot, 0.6);
                Fx.p(l, ParticleTypes.CLOUD, bot, 1, 0.1, 0.05, 0.1, 0.01);
            });
            Sched.later(lower + 2, () -> {
                Fx.sound(l, c.g, "crate_impact", SoundSource.AMBIENT, 1.4f, 1.3f);
                Fx.shake(l, c.g, 14, 0.25f, 8);
                Fx.p(l, ParticleTypes.POOF, c.g.add(0, 0.3, 0), 10, 0.5, 0.1, 0.5, 0.05);
                Fx.blockImpact(l, BlockPos.containing(c.g).below(), c.g.add(0, 0.2, 0), 10);
            });
            Sched.later(lower + hold, () -> {
                cargo.item(ModItems.PROP_CRATE_OPEN);
                Fx.sound(l, c.g, "case_latch", SoundSource.AMBIENT, 1.2f, 1.0f);
                release(c);
            });
            Sched.later(lower + hold + 200, () -> fade(cargo, 12));
        });
        // depart
        Sched.later(approach + lower + hold, () -> {
            double dx = Math.cos(ang + 2.4) * 40, dz = Math.sin(ang + 2.4) * 40;
            animate(0, leave, props, offs, s, t -> {
                double f = t / (double) leave; f = f * f;
                return v(dx * f, hover + 0.34 * s + 16 * f, dz * f);
            }, t -> yaw((float) Math.toDegrees(ang + 2.4) + 90), null);
            Sched.later(leave + 4, () -> { for (Prop pr : props) pr.kill(); });
        });
    }

    // ------------------------------------------------------------------------------------------------ 3. reinforced supply pod
    private static void pod(Ctx c) {
        ServerLevel l = c.l;
        float s = 1.9f;
        int H = Math.max(8, Math.min(c.h - 1, 56));
        int fall = Mth.clamp((int) (H / 1.8), 10, 28);
        Fx.sound(l, c.g, "incoming_whistle", SoundSource.AMBIENT, 5.0f, 0.85f);
        warn(c, 24 + fall);
        Sched.later(24, () -> {
            Prop pod = new Prop(l, c.g, ModItems.PROP_POD, v(0, 0.5 * s + H, 0), s, euler(0, 0, 0));
            Fx.p(l, ParticleTypes.SONIC_BOOM, c.g.add(0, H, 0), 1, 0, 0, 0, 0);
            Fx.sound(l, c.g.add(0, H, 0), "flash_whoosh", SoundSource.AMBIENT, 5.0f, 0.5f);
            animate(1, fall, List.of(pod), List.of(new Vector3f()), s, t -> v(0, 0.5 * s + H * Math.pow(1.0 - t / (double) fall, 1.4), 0), t -> euler(0, t * 14, 0), () -> {
                Vec3 p = c.g.add(0, pod.pos.y + 0.9 * s, 0);
                Fx.p(l, ParticleTypes.FLAME, p, 5, 0.25, 0.4, 0.25, 0.05);
                Fx.p(l, ParticleTypes.LARGE_SMOKE, p.add(0, 1.2, 0), 3, 0.25, 0.5, 0.25, 0.04);
                Fx.p(l, ParticleTypes.LAVA, p.add(0, 0.5, 0), 1, 0.4, 0.4, 0.4, 0);
            });
            Sched.later(fall + 2, () -> {
                impactFx(c, 1.4f, 48, 50, "pod_impact", 5.0f, 0.95f);
                Fx.flash(l, c.g, 22, 0xFFEEDD, 8);
                for (int r = 1; r <= 8; r++) {
                    int rr = r;
                    Sched.later(r * 2, () -> {
                        Fx.ring(l, ParticleTypes.CLOUD, c.g, rr * 1.2, 14 + rr * 5, 0.15);
                        if (rr % 2 == 0) Fx.ring(l, ParticleTypes.FLAME, c.g, rr * 0.8, 10 + rr * 3, 0.1);
                    });
                }
                pod.to(v(0, 0.5 * s - 0.15, 0), s, euler(0, 10, 0), 2);
                Sched.later(4, () -> pod.to(v(0, 0.5 * s, 0), s, euler(0, 10, 0), 4));
            });
            Sched.later(fall + 40, () -> {
                pod.item(ModItems.PROP_POD_OPEN);
                Fx.sound(l, c.g, "pod_hiss", SoundSource.AMBIENT, 2.0f, 1f);
                for (int t = 0; t < 40; t += 3) Sched.later(t, () -> Fx.p(l, ParticleTypes.CLOUD, c.g.add(0, 1.8, 0), 4, 0.35, 0.3, 0.35, 0.05));
                release(c);
            });
            Sched.later(fall + 240, () -> fade(pod, 16));
        });
    }

    // ------------------------------------------------------------------------------------------------ 4. teleportation container
    private static void teleport(Ctx c) {
        ServerLevel l = c.l;
        float s = 1.7f;
        Fx.sound(l, c.g, "teleport_zap", SoundSource.AMBIENT, 3.0f, 1.0f);
        warn(c, 24);
        Prop box = new Prop(l, c.g, ModItems.PROP_CONTAINER, v(0, 0.5 * s, 0), 0.01f, new Quaternionf());
        for (int t = 0; t < 36; t++) {
            int tt = t;
            Sched.later(t, () -> {
                double y = (tt % 18) / 18.0 * 2.6;
                Fx.ring(l, ParticleTypes.END_ROD, c.g, 1.1, 16, y);
                Fx.p(l, ParticleTypes.REVERSE_PORTAL, c.g.add(Math.cos(tt * 0.6) * 1.3, y, Math.sin(tt * 0.6) * 1.3), 2, 0.05, 0.05, 0.05, 0.02);
                if (tt % 4 == 0) Fx.p(l, ParticleTypes.ELECTRIC_SPARK, c.g.add(0, 1.0, 0), 6, 0.9, 0.9, 0.9, 0.3);
            });
        }
        float[] flick = {0.35f, 0f, 1.0f, 0.2f, 1.0f, 0.5f};
        for (int i = 0; i < flick.length; i++) {
            float f = flick[i];
            Sched.later(12 + i * 3, () -> box.to(v(0, 0.5 * s, 0), Math.max(0.01f, f * s), new Quaternionf(), 1));
        }
        Sched.later(32, () -> {
            box.to(v(0, 0.5 * s, 0), s, new Quaternionf(), 2);
            Fx.sound(l, c.g, "flash_whoosh", SoundSource.AMBIENT, 2.5f, 1.0f);
            l.sendParticles(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFFFFF), c.g.x, c.g.y + 1.2, c.g.z, 1, 0, 0, 0, 0);
            Fx.flash(l, c.g, 18, 0x9AF0FF, 8);
            Fx.shake(l, c.g, 20, 0.45f, 10);
            Fx.p(l, ParticleTypes.EXPLOSION, c.g.add(0, 1, 0), 1, 0, 0, 0, 0);
            Fx.ring(l, ParticleTypes.END_ROD, c.g, 1.8, 30, 0.2);
        });
        Sched.later(78, () -> {
            box.item(ModItems.PROP_CONTAINER_OPEN);
            Fx.sound(l, c.g, "case_latch", SoundSource.AMBIENT, 1.4f, 1.4f);
            Fx.p(l, ParticleTypes.GLOW, c.g.add(0, 1.8, 0), 20, 0.4, 0.2, 0.4, 0.08);
            release(c);
        });
        Sched.later(250, () -> {
            for (int i = 0; i < 4; i++) {
                int ii = i;
                Sched.later(i * 3, () -> box.to(v(0, 0.5 * s, 0), (ii % 2 == 0 ? 0.3f : 1.0f) * s, new Quaternionf(), 1));
            }
            Sched.later(12, () -> {
                Fx.p(l, ParticleTypes.REVERSE_PORTAL, c.g.add(0, 1, 0), 30, 0.6, 0.8, 0.6, 0.3);
                Fx.sound(l, c.g, "teleport_zap", SoundSource.AMBIENT, 1.4f, 1.6f);
                box.kill();
            });
        });
    }

    // ------------------------------------------------------------------------------------------------ 5. containment case through a dimensional rift
    private static void caseRift(Ctx c, boolean dramatic) {
        ServerLevel l = c.l;
        float s = 1.8f;
        float riftY = Math.max(2.5f, Math.min(5.5f, c.h - 1.2f));
        Vec3 rc = c.g.add(0, riftY, 0);
        Fx.sound(l, rc, "rift_open", SoundSource.AMBIENT, 4.0f, dramatic ? 0.8f : 1.0f);
        warn(c, 40);
        if (dramatic) {
            Fx.flash(l, c.g, 30, 0x4A1070, 24);
            Fx.shake(l, c.g, 40, 0.55f, 30);
            Sched.later(4, () -> bolt(l, c.g.add(3, 0, 1)));
            Sched.later(10, () -> bolt(l, c.g.add(-2, 0, -3)));
        }
        double maxR = dramatic ? 3.2 : 2.2;
        for (int t = 0; t < 60; t++) {
            int tt = t;
            Sched.later(t, () -> {
                double r = maxR * Math.min(1.0, tt / 36.0);
                for (int k = 0; k < 3; k++) {
                    double a = tt * 0.35 + k * 2.09;
                    Vec3 p = rc.add(Math.cos(a) * r, Math.sin(a) * r * 0.4, Math.sin(a) * r * 0.6);
                    l.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0, 0, 0, 0.0);
                    l.sendParticles(Fx.dust(0xB050FF, 1.4f), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
                Fx.p(l, ParticleTypes.PORTAL, rc, 6, r * 0.5, r * 0.4, r * 0.5, 0.8);
                if (tt % 10 == 0 && tt > 10) Fx.p(l, ParticleTypes.ELECTRIC_SPARK, rc, 10, r * 0.6, r * 0.5, r * 0.6, 0.2);
            });
        }
        int fall = 46;
        Prop box = new Prop(l, c.g, ModItems.PROP_CASE, v(0, 0.5 * s + riftY, 0), 0.01f, new Quaternionf());
        Sched.later(46, () -> {
            box.to(v(0, 0.5 * s + riftY, 0), s, euler(20, 0, 40), 3);
            Fx.sound(l, rc, "flash_whoosh", SoundSource.AMBIENT, 2.0f, 0.7f);
            Sched.later(4, () -> animate(0, fall, List.of(box), List.of(new Vector3f()), s,
                    t -> v(Math.sin(t * 0.18) * 0.35, 0.5 * s + riftY * (1.0 - Math.pow(t / (double) fall, 1.5)), Math.cos(t * 0.18) * 0.35),
                    t -> euler(20f * (1f - t / (float) fall), t * 9f, 40f * (1f - t / (float) fall)), () -> {
                        Vec3 p = c.g.add(box.pos.x, box.pos.y, box.pos.z);
                        Fx.p(l, ParticleTypes.END_ROD, p, 2, 0.1, 0.1, 0.1, 0.02);
                        Fx.p(l, ParticleTypes.REVERSE_PORTAL, p, 2, 0.2, 0.2, 0.2, 0.05);
                    }));
        });
        Sched.later(76, () -> {   // rift collapses
            Fx.sound(l, rc, "emp_burst", SoundSource.AMBIENT, 2.5f, 0.6f);
            Fx.p(l, ParticleTypes.SONIC_BOOM, rc, 1, 0, 0, 0, 0);
            Fx.p(l, ParticleTypes.REVERSE_PORTAL, rc, 50, 0.4, 0.4, 0.4, 0.6);
            Fx.ring(l, Fx.dust(0xC070FF, 1.8f), rc, maxR, 40, 0);
            Fx.shake(l, c.g, 24, dramatic ? 0.7f : 0.4f, 14);
            if (dramatic) Fx.flash(l, c.g, 30, 0xE0C0FF, 10);
        });
        Sched.later(46 + 4 + fall + 2, () -> {
            impactFx(c, 0.35f, 16, 12, "crate_impact", 1.3f, 1.4f);
            box.to(v(0, 0.5 * s, 0), s, euler(0, 30, 0), 2);
        });
        Sched.later(46 + 4 + fall + 22, () -> {
            box.item(ModItems.PROP_CASE_OPEN);
            Fx.sound(l, c.g, "case_latch", SoundSource.AMBIENT, 1.6f, 1.0f);
            Fx.p(l, ParticleTypes.GLOW, c.g.add(0, 0.9, 0), 16, 0.3, 0.2, 0.3, 0.06);
            release(c);
        });
        Sched.later(46 + 4 + fall + 240, () -> {
            Fx.p(l, ParticleTypes.END_ROD, c.g.add(0, 0.8, 0), 20, 0.4, 0.3, 0.4, 0.1);
            fade(box, 12);
        });
    }

    private static void bolt(ServerLevel l, Vec3 at) {
        LightningBolt b = EntityTypes.LIGHTNING_BOLT.create(l, EntitySpawnReason.TRIGGERED);
        if (b == null) return;
        b.setPos(at.x, at.y, at.z);
        b.setVisualOnly(true);
        l.addFreshEntity(b);
    }
}
