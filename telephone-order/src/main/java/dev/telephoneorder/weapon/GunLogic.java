package dev.telephoneorder.weapon;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.AmmoDef;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.Fx;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.Sched;
import dev.telephoneorder.entity.GunProjectile;
import dev.telephoneorder.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Server-side gun behaviour: triggering, ammo, reloads, heat and the per-behaviour firing code. */
public final class GunLogic {
    private GunLogic() {}

    public static final class PS {
        public long nextFire, lastFire;
        public int useTicks, charge, lockProg, beamTicks, beamTargetTicks;
        public boolean aiming, lockedOn, started;
        public UUID lock, beamTarget;
        public long lastLockSend;
    }

    private static final Map<UUID, PS> STATE = new HashMap<>();
    private static final Random RNG = new Random();

    public static PS ps(ServerPlayer p) { return STATE.computeIfAbsent(p.getUUID(), k -> new PS()); }
    public static void forget(UUID id) { STATE.remove(id); }
    public static void clearAll() { STATE.clear(); }

    // ------------------------------------------------------------------------------------------------ helpers
    public static String holdType(GunDef d) {
        return switch (d.beh()) {
            case "rail" -> "rail";
            case "charge" -> "charge";
            case "smart" -> "smart";
            case "cryo", "gravity" -> "cont";
            case "beam" -> d.has("sustained") ? "cont" : null;
            default -> null;
        };
    }

    public static String currentMode(ItemStack stack, GunDef def) {
        List<String> m = def.modes();
        int i = Math.floorMod(GunData.mode(stack), m.size());
        return m.get(i);
    }

    private static boolean ready(ServerPlayer p, PS s) { return p.level().getGameTime() >= s.nextFire; }

    private static void actionbar(ServerPlayer p, String text) { p.sendOverlayMessage(Component.literal(text)); }

    private static Vec3 muzzle(ServerPlayer p) {
        Vec3 v = p.getViewVector(1f);
        Vec3 right = v.cross(new Vec3(0, 1, 0)).normalize();
        return p.getEyePosition().add(v.scale(0.9)).add(right.scale(0.22)).add(0, -0.18, 0);
    }

    private static double spreadDeg(ServerPlayer p, PS s, GunDef def, GunStats st) {
        double sp = def.spread() * st.spread;
        if (s.aiming) sp *= 0.45;
        else if (p.isShiftKeyDown()) sp *= 0.8;
        if (!p.onGround()) sp *= 1.8;
        double h = p.getDeltaMovement().horizontalDistance();
        if (h > 0.08) sp *= 1.0 + Math.min(1.0, h * 4.0) * 0.7;
        return sp;
    }

    private static float loudness(GunDef d) {
        return switch (d.fam()) { case "pistol" -> 1.5f; case "smg" -> 1.6f; case "rifle" -> 2.2f; case "sniper" -> 3.2f; case "shotgun" -> 2.4f;
            case "lmg" -> 2.4f; case "heavy", "launcher" -> 3.0f; default -> 1.4f; };
    }

    // ------------------------------------------------------------------------------------------------ use / tick / release
    public static InteractionResult onUse(ServerPlayer p, ItemStack stack, GunItem item, InteractionHand hand) {
        GunDef def = item.def;
        PS s = ps(p);
        if (def.beh().equals("turret")) return InteractionResult.PASS;     // placed via useOn
        if (GunData.reloading(stack)) {
            if (def.perRound() && GunData.ammo(stack) > 0) cancelReload(stack);   // shotgun: interrupt the reload to shoot
            else return InteractionResult.FAIL;
        }
        s.useTicks = 0; s.charge = 0; s.started = false; s.lockProg = 0; s.lock = null; s.lockedOn = false; s.beamTicks = 0;
        String hold = holdType(def);
        if (hold != null) {
            if (GunData.ammo(stack) < (int) def.p("cost", 1)) { empty(p, stack, def); return InteractionResult.FAIL; }
            if (GunData.overheated(stack)) { Fx.sound(p, "empty_click", SoundSource.PLAYERS, 0.6f, 0.6f); return InteractionResult.FAIL; }
            p.startUsingItem(hand);
            return InteractionResult.CONSUME;
        }
        String mode = currentMode(stack, def);
        if (mode.equals("auto")) {
            p.startUsingItem(hand);
            if (ready(p, s)) trigger(p, stack, def, hand, mode);
            return InteractionResult.CONSUME;
        }
        if (ready(p, s)) trigger(p, stack, def, hand, mode);
        return InteractionResult.SUCCESS;
    }

    public static void onUseTick(ServerPlayer p, ItemStack stack, GunItem item, int remaining) {
        GunDef def = item.def;
        PS s = ps(p);
        s.useTicks++;
        if (GunData.reloading(stack) && !def.perRound()) { p.stopUsingItem(); return; }
        String hold = holdType(def);
        InteractionHand hand = p.getUsedItemHand();
        if (hold == null) {
            if (currentMode(stack, def).equals("auto") && ready(p, s)) {
                if (!trigger(p, stack, def, hand, "auto")) p.stopUsingItem();
            }
            return;
        }
        GunStats st = GunStats.of(def, stack);
        ServerLevel l = (ServerLevel) p.level();
        switch (hold) {
            case "rail" -> {
                int need = (int) def.p("charge", 14);
                if (s.charge == 0) Fx.sound(p, "charge_up", SoundSource.PLAYERS, 1.2f, 1.4f);
                s.charge++;
                Vec3 m = muzzle(p);
                double ang = s.charge * 0.9;
                Fx.p(l, ParticleTypes.ELECTRIC_SPARK, m.add(p.getViewVector(1f).scale(0.4)), 2, 0.25, 0.25, 0.25, 0.1);
                Fx.p(l, Fx.dust(def.glow(), 0.8f), m.add(Math.cos(ang) * 0.3, Math.sin(ang) * 0.3, 0), 1, 0, 0, 0, 0);
                if (s.charge >= need) {
                    if (ready(p, s)) fireOnce(p, l, stack, def, st, hand);
                    p.stopUsingItem();
                }
            }
            case "charge" -> {
                int max = (int) def.p("charge", 44);
                if (s.charge == 0) Fx.sound(p, "charge_up", SoundSource.PLAYERS, 1.4f, 1.0f);
                if (s.charge < max + 40) s.charge++;
                Vec3 m = muzzle(p).add(p.getViewVector(1f).scale(0.5));
                float f = Math.min(1f, s.charge / (float) max);
                for (int i = 0; i < 2; i++) {
                    double a = RNG.nextDouble() * Math.PI * 2, r = 0.9 * (1.1 - f);
                    Vec3 o = m.add(Math.cos(a) * r, Math.sin(a) * r, 0);
                    Fx.p(l, Fx.dust(def.glow(), 0.6f + f * 1.2f), o, 1, 0.05, 0.05, 0.05, 0);
                }
                if (s.charge % 2 == 0) { int c = s.charge; GunData.edit(stack, t -> t.putInt("chg", Math.min(max, c))); }
                if (s.charge > max + 25) {   // overcharged: backfire
                    Boom.explode(l, p.position().add(0, 1, 0), 3.0f, 8f, p, DamageKind.ENERGY, null, 0.6f, false);
                    GunData.edit(stack, t -> { t.putInt("chg", 0); t.putFloat("heat", 100f); t.putBoolean("oh", true); });
                    p.stopUsingItem();
                }
            }
            case "smart" -> smartTick(p, l, stack, def, s);
            case "cont" -> {
                int every = (int) def.p("cost_every", 1);
                if (GunData.overheated(stack)) { p.stopUsingItem(); return; }
                if (GunData.ammo(stack) <= 0) { empty(p, stack, def); p.stopUsingItem(); return; }
                continuous(p, l, stack, def, st, s);
                if (s.useTicks % Math.max(1, every) == 0) GunData.edit(stack, t -> t.putInt("ammo", Math.max(0, t.getIntOr("ammo", 0) - 1)));
                addHeat(stack, def, st, def.heat());
                s.lastFire = l.getGameTime();
            }
            default -> { }
        }
    }

    public static void onRelease(ServerPlayer p, ItemStack stack, GunItem item) {
        GunDef def = item.def;
        PS s = ps(p);
        ServerLevel l = (ServerLevel) p.level();
        GunStats st = GunStats.of(def, stack);
        String hold = holdType(def);
        if ("charge".equals(hold)) {
            int max = (int) def.p("charge", 44);
            if (s.charge >= def.p("min_charge", 8) && ready(p, s) && !GunData.overheated(stack)) fireOnce(p, l, stack, def, st, InteractionHand.MAIN_HAND);
            GunData.edit(stack, t -> t.putInt("chg", 0));
        } else if ("smart".equals(hold)) {
            if (ready(p, s) && GunData.ammo(stack) > 0) fireOnce(p, l, stack, def, st, InteractionHand.MAIN_HAND);
            ServerPlayNetworking.send(p, new Payloads.LockState(-1, 0, false));
        }
        s.charge = 0; s.lockProg = 0; s.lock = null; s.lockedOn = false; s.beamTicks = 0;
    }

    private static boolean trigger(ServerPlayer p, ItemStack stack, GunDef def, InteractionHand hand, String mode) {
        PS s = ps(p);
        ServerLevel l = (ServerLevel) p.level();
        GunStats st = GunStats.of(def, stack);
        if (mode.equals("burst")) {
            int n = (int) def.p("burst", 3), gap = (int) def.p("burst_gap", 2);
            if (!fireOnce(p, l, stack, def, st, hand)) return false;
            s.nextFire = l.getGameTime() + (long) Math.max(1, Math.round(def.delay() * st.delay)) + (long) gap * (n - 1);
            for (int i = 1; i < n; i++) {
                Sched.later(i * gap, () -> {
                    if (!p.isAlive() || p.hasDisconnected()) return;
                    ItemStack cur = p.getItemInHand(hand);
                    if (!(cur.getItem() instanceof GunItem gi) || gi.def != def || GunData.reloading(cur)) return;
                    fireOnce(p, (ServerLevel) p.level(), cur, def, GunStats.of(def, cur), hand);
                });
            }
            return true;
        }
        return fireOnce(p, l, stack, def, st, hand);
    }

    // ------------------------------------------------------------------------------------------------ the shot
    private static void empty(ServerPlayer p, ItemStack stack, GunDef def) {
        PS s = ps(p);
        long now = p.level().getGameTime();
        if (now < s.nextFire) return;
        s.nextFire = now + 6;
        Fx.sound(p, "empty_click", SoundSource.PLAYERS, 0.9f, 1.0f);
        if (GunData.reserve(stack) > 0 || inventoryRounds(p, def) > 0) {
            actionbar(p, "Magazine empty - reloading");
            startReload(p, stack, def);
        } else {
            actionbar(p, "Out of ammo: " + Catalog.AMMO_BY_ID.get(def.ammo()).name());
        }
    }

    /** returns true if a shot was actually fired */
    public static boolean fireOnce(ServerPlayer p, ServerLevel l, ItemStack stack, GunDef def, GunStats st, InteractionHand hand) {
        PS s = ps(p);
        int cost = Math.max(1, (int) def.p("cost", 1));
        if (def.beh().equals("charge")) {
            float f = Math.min(1f, s.charge / (float) def.p("charge", 44));
            cost = Math.max(2, Math.round(cost * (0.4f + 0.6f * f)));
        }
        int ammo = GunData.ammo(stack);
        if (GunData.overheated(stack)) { Fx.sound(p, "empty_click", SoundSource.PLAYERS, 0.6f, 0.6f); return false; }
        if (ammo < cost) { empty(p, stack, def); return false; }
        if (GunData.reloading(stack)) return false;
        final int c = cost;
        GunData.edit(stack, t -> t.putInt("ammo", Math.max(0, t.getIntOr("ammo", 0) - c)));
        long now = l.getGameTime();
        int delay = Math.max(1, Math.round(def.delay() * st.delay));
        s.nextFire = Math.max(s.nextFire, now + delay);
        s.lastFire = now;
        Vec3 eye = p.getEyePosition();
        Vec3 view = p.getViewVector(1f);
        Vec3 mz = muzzle(p);
        boolean silent = def.has("silent");
        float noise = st.noise * (silent ? 0.25f : 1f);
        double spread = spreadDeg(p, s, def, st);
        String amod = GunData.ammoMod(stack);

        switch (def.beh()) {
            case "bullet", "shotgun" -> ballistic(p, l, stack, def, st, eye, view, mz, spread, amod, false);
            case "laser" -> beamShot(p, l, def, st, eye, view, mz, spread);
            case "beam" -> daybreak(p, l, def, st, eye, view, mz);
            case "rail" -> railShot(p, l, def, st, eye, view, mz);
            case "pulse" -> pulseShot(p, l, def, st, eye, view, mz, spread);
            case "arc" -> arcShot(p, l, def, st, eye, view, mz);
            case "plasma" -> projectile(p, l, def, st, GunProjectile.Kind.PLASMA, mz, view, spread, (float) def.dmg() * st.dmg, (float) def.p("splash", 0), (float) def.p("splash_dmg", 0), 70, 0, null);
            case "eshotgun" -> {
                for (int i = 0; i < def.pellets(); i++)
                    projectile(p, l, def, st, GunProjectile.Kind.SHARD, mz, view, spread, def.dmg() * st.dmg, 0, 0, 24, (int) def.p("bounces", 1), null);
            }
            case "charge" -> {
                float f = Math.min(1f, s.charge / (float) def.p("charge", 44));
                float dmg = def.dmg() * st.dmg * (0.2f + 0.8f * f);
                GunProjectile pr = projectile(p, l, def, st, GunProjectile.Kind.CHARGE, mz, view, 0, dmg, (float) def.p("splash", 4) * (0.35f + 0.65f * f), dmg * 0.6f, 90, 0, null);
                if (pr != null) pr.scale = 0.5f + f;
                Fx.shake(l, p.position(), 10, 0.5f * f, 8);
                GunData.edit(stack, t -> t.putInt("chg", 0));
            }
            case "heavycannon" -> projectile(p, l, def, st, GunProjectile.Kind.HEAVY, mz, view, spread, def.dmg() * st.dmg, (float) def.p("splash", 5), (float) def.p("splash_dmg", 14) * st.dmg, 100, 0, null);
            case "emp" -> {
                GunProjectile pr = projectile(p, l, def, st, GunProjectile.Kind.EMP, mz, view, spread, def.dmg() * st.dmg, (float) def.p("splash", 6), def.dmg() * st.dmg, 110, 0, null);
                if (pr != null) { pr.grav = def.p("gravity", 0.045); pr.param = (int) def.p("emp", 120); }
            }
            case "rocket" -> {
                projectile(p, l, def, st, GunProjectile.Kind.ROCKET, mz, view, spread, def.dmg() * st.dmg, (float) def.p("splash", 4), def.dmg() * st.dmg * 0.8f, 120, 0, null);
                backblast(p, l, view);
            }
            case "rift" -> {
                GunProjectile pr = projectile(p, l, def, st, GunProjectile.Kind.RIFT, mz, view, spread, (float) def.p("duration", 90), (float) def.p("radius", 5), 0, 100, 0, null);
                if (pr != null) pr.grav = def.p("pull", 0.2);
            }
            case "shield" -> {
                ShieldField.create(p, (int) def.p("duration", 140), (float) def.p("radius", 3.4), (int) def.p("absorb", 6));
                Fx.sound(p, "fire_shield", SoundSource.PLAYERS, 1.2f, 1.0f);
                Fx.p(l, ParticleTypes.GLOW, p.position().add(0, 1, 0), 30, 1.2, 0.9, 1.2, 0.1);
                Fx.ring(l, Fx.dust(def.glow(), 1.4f), p.position(), def.p("radius", 3.4), 32, 0.1);
            }
            case "unstable" -> unstable(p, l, stack, def, st, eye, view, mz, spread);
            case "smart" -> smartFire(p, l, def, st, s, mz, view);
            default -> { }
        }

        // ---- common post-shot presentation
        postShot(p, l, stack, def, st, s, mz, view, noise, silent, hand);
        return true;
    }

    private static void postShot(ServerPlayer p, ServerLevel l, ItemStack stack, GunDef def, GunStats st, PS s, Vec3 mz, Vec3 view, float noise, boolean silent, InteractionHand hand) {
        String beh = def.beh();
        boolean hitscanNoise = !beh.equals("shield") && !beh.equals("smart") && !beh.equals("charge");
        float vol = loudness(def) * Math.max(0.12f, noise);
        float pitch = 0.94f + RNG.nextFloat() * 0.12f;
        String snd = "fire_" + def.sound();
        if (!beh.equals("shield")) Fx.sound(l, mz, snd, SoundSource.PLAYERS, vol, pitch);
        if (!def.energy() && !def.fam().equals("launcher") && !st.flashHidden && !silent && !beh.equals("emp")) {
            Fx.p(l, ParticleTypes.FLAME, mz.add(view.scale(0.2)), 1, 0.02, 0.02, 0.02, 0.02);
            Fx.p(l, ParticleTypes.SMOKE, mz.add(view.scale(0.3)), def.fam().equals("shotgun") ? 5 : 2, 0.04, 0.04, 0.04, 0.02);
            // shell casing, thrown out of the ejection port
            Vec3 right = view.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 ej = p.getEyePosition().add(view.scale(0.5)).add(right.scale(0.35)).add(0, -0.15, 0);
            Item casing = def.fam().equals("shotgun") ? Items.REDSTONE : Items.GOLD_NUGGET;
            l.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, casing), ej.x, ej.y, ej.z, 1, right.x * 0.05, 0.05, right.z * 0.05, 0.12);
        } else if (def.energy() && !beh.equals("shield")) {
            Fx.p(l, Fx.dust(def.glow(), 1.0f), mz.add(view.scale(0.25)), 3, 0.05, 0.05, 0.05, 0);
        }
        // recoil
        float rm = s.aiming ? 0.7f : 1f;
        float pitchKick = def.rp() * st.recoil * rm * (0.85f + RNG.nextFloat() * 0.3f);
        float yawKick = def.ry() * st.recoil * rm * (RNG.nextFloat() * 2 - 1);
        if (pitchKick > 0.01f || Math.abs(yawKick) > 0.01f) ServerPlayNetworking.send(p, new Payloads.Recoil(pitchKick, yawKick));
        // heat
        if (def.usesHeat() && !beh.equals("beam") || beh.equals("beam") && !def.has("sustained")) addHeat(stack, def, st, def.heat());
        // slow while shooting heavy support weapons
        int slow = (int) def.p("slow", 0);
        if (slow > 0) p.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 12, slow - 1, true, false, false));
        // mob alert
        if (hitscanNoise) Ballistics.alertMobs(l, p, silent ? 4 : Math.min(40, vol * 11 * st.noise));
        // visual cooldown (drives the muzzle-flash model frame and prevents click-spam)
        int cd = Math.max(1, Math.round(def.delay() * st.delay));
        p.getCooldowns().addCooldown(stack, cd);
        p.swing(hand, net.minecraft.world.item.component.SwingAnimation.DEFAULT, true);
        GunData.syncModel(stack, GunData.stage(stack));
        if (stack.getItem() instanceof GunItem gi) gi.playAnim(p, stack, "fire");
    }

    private static void addHeat(ItemStack stack, GunDef def, GunStats st, float amount) {
        if (amount <= 0 || !def.usesHeat()) return;
        float add = amount * st.heat;
        GunData.edit(stack, t -> {
            float h = t.getFloatOr("heat", 0f) + add;
            if (h >= 100f) { h = 100f; t.putBoolean("oh", true); }
            t.putFloat("heat", h);
        });
    }

    private static void backblast(ServerPlayer p, ServerLevel l, Vec3 view) {
        Vec3 back = p.position().subtract(view.scale(1.0)).add(0, 1, 0);
        Fx.p(l, ParticleTypes.LARGE_SMOKE, back, 8, 0.3, 0.2, 0.3, 0.08);
        Fx.p(l, ParticleTypes.FLAME, back, 8, 0.2, 0.1, 0.2, 0.15);
        Vec3 flat = new Vec3(view.x, 0, view.z).normalize();
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(back, back).inflate(3.2), x -> x != p)) {
            Vec3 rel = e.position().subtract(p.position());
            if (rel.dot(flat) < -0.3 && rel.length() < 3.2) {
                Ballistics.damage(l, p, e, 4f, 0f, DamageKind.EXPLOSION, flat.scale(-1), false, false);
                Ballistics.impulse(e, flat.scale(-0.7));
            }
        }
    }

    // ------------------------------------------------------------------------------------------------ ballistic
    private static void hitEffects(ServerLevel l, ServerPlayer p, GunDef def, GunStats st, String amod, Entity e, Vec3 at, boolean head) {
        if (!(e instanceof LivingEntity le)) return;
        Fx.p(l, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at, head ? 8 : 4, 0.1, 0.1, 0.1, 0.1);
        Fx.sound(l, at, "impact_flesh", SoundSource.PLAYERS, 0.5f, 0.9f + RNG.nextFloat() * 0.2f);
        switch (amod) {
            case "incendiary" -> le.igniteForSeconds(4);
            case "frost" -> { le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 80, 2)); le.setTicksFrozen(Math.min(140, le.getTicksFrozen() + 60)); }
            case "explosive" -> Boom.explode(l, at, 1.8f, 3.5f, p, DamageKind.EXPLOSION, e, 0.2f, false);
            default -> { }
        }
        if (def.p("burn", 0) > 0) le.igniteForSeconds((float) def.p("burn", 4));
        if (st.light) le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, true, false));
        if (def.has("mark")) {
            Ballistics.MARKS.put(e.getUUID(), l.getGameTime() + 240);
            le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, true, false));
        }
    }

    private static void ballistic(ServerPlayer p, ServerLevel l, ItemStack stack, GunDef def, GunStats st, Vec3 eye, Vec3 view, Vec3 mz, double spread, String amod, boolean echo) {
        float dmgMul = switch (amod) { case "incendiary" -> 0.9f; case "ap" -> 0.9f; case "explosive" -> 0.8f; case "frost" -> 0.9f; default -> 1f; };
        float ap = def.ap() + st.apAdd + (amod.equals("ap") ? 0.55f : 0f);
        int pierceEnt = def.pierce();
        int pierceBlocks = def.pierce() > 0 ? 1 : 0;
        boolean phase = def.has("phase");
        boolean shotgun = def.fam().equals("shotgun");
        double range = def.range() * st.range;
        float per = def.dmg() * st.dmg * dmgMul * (echo ? 0.6f : 1f);
        int pellets = Math.max(1, def.pellets());
        int glow = def.has("glow") || def.p("glow", 0) > 0 ? def.glow() : (amod.equals("incendiary") ? 0xFF8A2A : amod.equals("frost") ? 0x9AD8FF : 0xFFEFAA);
        for (int i = 0; i < pellets; i++) {
            Vec3 dir = Ballistics.spreadDir(view, spread, RNG);
            Ballistics.Trace tr = Ballistics.trace(l, p, eye, dir, range, pierceEnt, pierceBlocks, phase);
            for (Ballistics.EntityHit h : tr.hits()) {
                float f = Ballistics.falloff(h.dist(), range, shotgun ? 0.25 : 0.55, shotgun ? 0.2 : 0.6);
                boolean head = Ballistics.isHead(h.entity(), h.pos());
                float mult = head ? 1f + (def.hs() - 1f) * ModConfig.get().headshotMultiplier : (Ballistics.isLegs(h.entity(), h.pos()) ? 0.85f : 1f);
                boolean hurt = Ballistics.damage(l, p, h.entity(), per * f * mult, ap, echo ? DamageKind.ECHO : DamageKind.BULLET, dir, head, i > 0 && pellets > 3);
                if (hurt) hitEffects(l, p, def, st, amod, h.entity(), h.pos(), head);
            }
            if (tr.block() != null && !phase) {
                Fx.blockImpact(l, tr.block().getBlockPos(), tr.end(), 5);
                Fx.p(l, ParticleTypes.CRIT, tr.end(), 2, 0.05, 0.05, 0.05, 0.2);
                if (i == 0) Fx.sound(l, tr.end(), "impact_bullet", SoundSource.BLOCKS, 0.7f, 0.9f + RNG.nextFloat() * 0.2f);
                if (amod.equals("explosive") && i == 0) Boom.explode(l, tr.end(), 1.8f, 3.5f, p, DamageKind.EXPLOSION, null, 0.2f, false);
                if (amod.equals("incendiary") && i == 0) Fx.p(l, ParticleTypes.FLAME, tr.end(), 6, 0.1, 0.1, 0.1, 0.03);
            }
            if (i < Math.min(pellets, 4) && !(echo && i > 0)) {
                boolean fancy = phase || echo || def.has("glow");
                Vec3 from = i == 0 ? mz : mz;
                Fx.line(l, Fx.dust(glow, fancy ? 0.7f : 0.45f), from, tr.end(), fancy ? 1.4 : (pellets > 1 ? 3.5 : 2.6));
            }
        }
        if (def.has("echo") && !echo) {
            int ticks = (int) def.p("echo", 10);
            Sched.later(ticks, () -> {
                if (!p.isAlive() || p.hasDisconnected()) return;
                ServerLevel lv = (ServerLevel) p.level();
                Fx.sound(lv, mz, "fire_paradox", SoundSource.PLAYERS, 0.8f, 0.6f);
                Fx.p(lv, ParticleTypes.REVERSE_PORTAL, mz, 10, 0.1, 0.1, 0.1, 0.2);
                ballistic(p, lv, stack, def, st, eye, view, mz, 0.2, amod, true);
            });
        }
    }

    // ------------------------------------------------------------------------------------------------ energy beams
    private static void beamShot(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, Vec3 eye, Vec3 view, Vec3 mz, double spread) {
        Vec3 dir = Ballistics.spreadDir(view, spread, RNG);
        double range = def.range() * st.range;
        Ballistics.Trace tr = Ballistics.trace(l, p, eye, dir, range, 0, 0, false);
        for (Ballistics.EntityHit h : tr.hits()) {
            boolean head = Ballistics.isHead(h.entity(), h.pos());
            float f = Ballistics.falloff(h.dist(), range, 0.7, 0.8);
            boolean hurt = Ballistics.damage(l, p, h.entity(), def.dmg() * st.dmg * f * (head ? def.hs() : 1f), def.ap(), DamageKind.ENERGY, dir, head, false);
            if (hurt) { Fx.p(l, ParticleTypes.ELECTRIC_SPARK, h.pos(), 5, 0.1, 0.1, 0.1, 0.25); Fx.p(l, Fx.dust(def.glow(), 1.3f), h.pos(), 3, 0.1, 0.1, 0.1, 0); }
        }
        Fx.line(l, Fx.dust(def.glow(), 0.9f), mz, tr.end(), 0.7);
        Fx.p(l, ParticleTypes.END_ROD, tr.end(), 2, 0.05, 0.05, 0.05, 0.05);
        if (tr.block() != null) {
            Fx.p(l, ParticleTypes.ELECTRIC_SPARK, tr.end(), 4, 0.05, 0.05, 0.05, 0.2);
            Fx.p(l, ParticleTypes.SMOKE, tr.end(), 1, 0.02, 0.02, 0.02, 0.01);
            Fx.sound(l, tr.end(), "impact_energy", SoundSource.BLOCKS, 0.4f, 1.3f);
        }
    }

    /** Daybreak Lance: heavy single solar beam. */
    private static void daybreak(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, Vec3 eye, Vec3 view, Vec3 mz) {
        double range = def.range() * st.range;
        Ballistics.Trace tr = Ballistics.trace(l, p, eye, view, range, 2, 0, false);
        boolean day = l.isBrightOutside() && l.canSeeSky(p.blockPosition());
        float mul = day ? 1.5f : 1f;
        for (Ballistics.EntityHit h : tr.hits()) {
            boolean head = Ballistics.isHead(h.entity(), h.pos());
            boolean hurt = Ballistics.damage(l, p, h.entity(), def.dmg() * st.dmg * mul * (head ? def.hs() : 1f), def.ap(), DamageKind.ENERGY, view, head, false);
            if (hurt && h.entity() instanceof LivingEntity le) {
                le.igniteForSeconds(day || le.isInvertedHealAndHarm() ? 6 : 2);
                Fx.p(l, ParticleTypes.FLAME, h.pos(), 8, 0.2, 0.3, 0.2, 0.05);
            }
        }
        Fx.line(l, Fx.dust(0xFFE890, 1.6f), mz, tr.end(), 0.5);
        Fx.line(l, ParticleTypes.END_ROD, mz, tr.end(), 3.0);
        Fx.ring(l, Fx.dust(0xFFD060, 1.2f), tr.end(), 0.8, 14, 0.0);
        if (tr.block() != null) Fx.blockImpact(l, tr.block().getBlockPos(), tr.end(), 6);
        Fx.shake(l, p.position(), 12, 0.3f, 6);
    }

    private static void railShot(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, Vec3 eye, Vec3 view, Vec3 mz) {
        double range = def.range() * st.range;
        Ballistics.Trace tr = Ballistics.trace(l, p, eye, view, range, def.pierce(), (int) def.p("blocks", 2), false);
        for (Ballistics.EntityHit h : tr.hits()) {
            boolean head = Ballistics.isHead(h.entity(), h.pos());
            boolean hurt = Ballistics.damage(l, p, h.entity(), def.dmg() * st.dmg * (head ? def.hs() : 1f), def.ap() + st.apAdd, DamageKind.ARMOR_PIERCING, view, head, false);
            if (hurt) {
                Ballistics.impulse(h.entity(), view.scale(0.9).add(0, 0.2, 0));
                Fx.p(l, ParticleTypes.ELECTRIC_SPARK, h.pos(), 10, 0.2, 0.2, 0.2, 0.4);
            }
        }
        Fx.line(l, Fx.dust(def.glow(), 1.3f), mz, tr.end(), 0.45);
        Fx.line(l, ParticleTypes.ELECTRIC_SPARK, mz, tr.end(), 1.5);
        Fx.ring(l, ParticleTypes.END_ROD, mz.add(view.scale(0.8)), 0.5, 12, 0);
        Fx.p(l, ParticleTypes.SONIC_BOOM, mz.add(view.scale(1.5)), 1, 0, 0, 0, 0);
        if (tr.block() != null) { Fx.blockImpact(l, tr.block().getBlockPos(), tr.end(), 10); Fx.p(l, ParticleTypes.ELECTRIC_SPARK, tr.end(), 12, 0.1, 0.1, 0.1, 0.4); }
        Fx.shake(l, p.position(), 14, 0.5f, 8);
    }

    private static void pulseShot(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, Vec3 eye, Vec3 view, Vec3 mz, double spread) {
        Vec3 dir = Ballistics.spreadDir(view, spread, RNG);
        double range = def.range() * st.range;
        Ballistics.Trace tr = Ballistics.trace(l, p, eye, dir, range, 0, 0, false);
        for (Ballistics.EntityHit h : tr.hits()) {
            boolean head = Ballistics.isHead(h.entity(), h.pos());
            float f = Ballistics.falloff(h.dist(), range, 0.5, 0.6);
            if (Ballistics.damage(l, p, h.entity(), def.dmg() * st.dmg * f * (head ? def.hs() : 1f), def.ap(), DamageKind.ENERGY, dir, head, false)) {
                Ballistics.impulse(h.entity(), dir.scale(0.25));
                Fx.ring(l, Fx.dust(def.glow(), 1.2f), h.pos(), 0.5, 10, 0);
            }
        }
        // travelling concussive rings
        Vec3 up = Math.abs(dir.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 r1 = dir.cross(up).normalize(), r2 = r1.cross(dir).normalize();
        double len = tr.end().distanceTo(mz);
        for (double d = 1.5; d < len; d += 4.0) {
            Vec3 c = mz.add(dir.scale(d));
            for (int k = 0; k < 8; k++) {
                double a = Math.PI * 2 * k / 8;
                Vec3 pt = c.add(r1.scale(Math.cos(a) * 0.35)).add(r2.scale(Math.sin(a) * 0.35));
                l.sendParticles(Fx.dust(def.glow(), 0.8f), pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            }
        }
        Fx.p(l, ParticleTypes.ELECTRIC_SPARK, tr.end(), 4, 0.1, 0.1, 0.1, 0.2);
        if (tr.block() != null) Fx.blockImpact(l, tr.block().getBlockPos(), tr.end(), 3);
    }

    private static void arcShot(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, Vec3 eye, Vec3 view, Vec3 mz) {
        double range = def.range() * st.range;
        Ballistics.Trace tr = Ballistics.trace(l, p, eye, view, range, 0, 0, false);
        Vec3 from = mz;
        Entity first = tr.hits().isEmpty() ? null : tr.hits().get(0).entity();
        if (first == null) {
            arcLine(l, from, tr.end());
            Fx.p(l, ParticleTypes.ELECTRIC_SPARK, tr.end(), 8, 0.2, 0.2, 0.2, 0.3);
            Fx.sound(l, tr.end(), "impact_energy", SoundSource.PLAYERS, 0.5f, 1.2f);
            return;
        }
        int chains = (int) def.p("chains", 5);
        double cr = def.p("chain_range", 6);
        float falloff = (float) def.p("falloff", 0.82);
        float dmg = def.dmg() * st.dmg;
        java.util.Set<UUID> done = new java.util.HashSet<>();
        Entity cur = first;
        Vec3 curPos = tr.hits().get(0).pos();
        arcLine(l, from, curPos);
        for (int i = 0; i <= chains && cur != null; i++) {
            done.add(cur.getUUID());
            boolean wet = cur.isInWaterOrRain();
            Ballistics.damage(l, p, cur, dmg * (wet ? 1.25f : 1f), def.ap(), DamageKind.ENERGY, null, false, false);
            if (cur instanceof LivingEntity le) le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 25, 1, true, false, false));
            Fx.p(l, ParticleTypes.ELECTRIC_SPARK, cur.getBoundingBox().getCenter(), 8, 0.25, 0.4, 0.25, 0.3);
            dmg *= falloff;
            Entity nextE = null;
            double best = cr * cr;
            Vec3 cc = cur.getBoundingBox().getCenter();
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, cur.getBoundingBox().inflate(cr))) {
                if (done.contains(e.getUUID()) || !Ballistics.targetable(p, e)) continue;
                double d = e.getBoundingBox().getCenter().distanceToSqr(cc);
                if (d < best) { best = d; nextE = e; }
            }
            if (nextE != null) arcLine(l, cc, nextE.getBoundingBox().getCenter());
            cur = nextE;
        }
        Fx.sound(l, curPos, "impact_energy", SoundSource.PLAYERS, 0.8f, 1.6f);
    }

    private static void arcLine(ServerLevel l, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 n = d.scale(1 / len);
        Vec3 up = Math.abs(n.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 r1 = n.cross(up).normalize(), r2 = r1.cross(n).normalize();
        int steps = (int) Math.max(3, len * 2.2);
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double jitter = Math.sin(t * Math.PI) * 0.35;
            Vec3 pt = a.add(d.scale(t)).add(r1.scale((RNG.nextDouble() - 0.5) * jitter * 2)).add(r2.scale((RNG.nextDouble() - 0.5) * jitter * 2));
            l.sendParticles(ParticleTypes.ELECTRIC_SPARK, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            if (i % 2 == 0) l.sendParticles(Fx.dust(0xA0C8FF, 0.8f), pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
        }
    }

    // ------------------------------------------------------------------------------------------------ continuous weapons
    private static void continuous(ServerPlayer p, ServerLevel l, ItemStack stack, GunDef def, GunStats st, PS s) {
        Vec3 eye = p.getEyePosition(), view = p.getViewVector(1f), mz = muzzle(p);
        double range = def.range() * st.range;
        switch (def.beh()) {
            case "cryo" -> {
                double cone = Math.cos(Math.toRadians(def.p("cone", 22)));
                for (int i = 0; i < 6; i++) {
                    Vec3 d = Ballistics.spreadDir(view, def.p("cone", 22) * 0.8, RNG);
                    Vec3 pt = mz.add(d.scale(1.0 + RNG.nextDouble() * range * 0.9));
                    l.sendParticles(ParticleTypes.SNOWFLAKE, pt.x, pt.y, pt.z, 1, 0.1, 0.1, 0.1, 0.02);
                    if (i % 2 == 0) l.sendParticles(Fx.dust(0xAAE6FF, 1.1f), pt.x, pt.y, pt.z, 1, 0.1, 0.1, 0.1, 0);
                }
                for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range))) {
                    if (!Ballistics.targetable(p, e)) continue;
                    Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                    double d = to.length();
                    if (d > range || d < 0.01 || to.normalize().dot(view) < cone) continue;
                    if (!Ballistics.trace(l, p, eye, to.normalize(), d, 0, 0, false).hits().isEmpty() && Ballistics.trace(l, p, eye, to.normalize(), d, 0, 0, false).block() != null) continue;
                    e.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 40, (int) def.p("slow", 3) - 1, true, false, false));
                    e.setTicksFrozen(Math.min(e.getTicksRequiredToFreeze() + 40, e.getTicksFrozen() + 6));
                    e.clearFire();
                    if (s.useTicks % 5 == 0) Ballistics.damage(l, p, e, def.dmg() * st.dmg * 5, 0f, DamageKind.ENERGY, null, false, true);
                }
                if (s.useTicks % 8 == 1) Fx.sound(l, mz, "fire_cryo", SoundSource.PLAYERS, 1.0f, 1.0f);
            }
            case "gravity" -> {
                boolean push = p.isShiftKeyDown();
                double cone = Math.cos(Math.toRadians(def.p("cone", 30)));
                double force = def.p("force", 0.17);
                for (Entity e : l.getEntities(p, new AABB(eye, eye).inflate(range), x -> x.isAlive() && !x.isSpectator() && !(x instanceof net.minecraft.world.entity.projectile.Projectile && false))) {
                    Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                    double d = to.length();
                    if (d > range || d < 0.5 || to.normalize().dot(view) < cone) continue;
                    Vec3 dir = to.normalize();
                    double f = force * (1.0 - d / range * 0.5);
                    if (e instanceof net.minecraft.world.entity.player.Player tp && !p.canHarmPlayer(tp)) continue;
                    Ballistics.impulse(e, dir.scale(push ? f : -f).add(0, push ? 0.02 : 0.03, 0));
                    e.fallDistance = 0;
                }
                for (int i = 0; i < 5; i++) {
                    Vec3 d = Ballistics.spreadDir(view, 25, RNG);
                    double t = RNG.nextDouble();
                    Vec3 pt = push ? mz.add(d.scale(1 + t * range * 0.8)) : mz.add(d.scale(range * (1 - t) * 0.8 + 1));
                    l.sendParticles(Fx.dust(def.glow(), 0.9f), pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0);
                    if (i == 0) l.sendParticles(ParticleTypes.REVERSE_PORTAL, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.1);
                }
                if (s.useTicks % 10 == 1) Fx.sound(l, mz, "fire_gravity", SoundSource.PLAYERS, 0.9f, push ? 1.5f : 1.0f);
            }
            case "beam" -> prismBeam(p, l, def, st, s, eye, view, mz, range);
            default -> { }
        }
    }

    private static void prismBeam(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, PS s, Vec3 eye, Vec3 view, Vec3 mz, double range) {
        Ballistics.Trace tr = Ballistics.trace(l, p, eye, view, range, 0, 0, false);
        Entity tgt = tr.hits().isEmpty() ? null : tr.hits().get(0).entity();
        if (tgt != null && tgt.getUUID().equals(s.beamTarget)) s.beamTargetTicks++;
        else { s.beamTarget = tgt == null ? null : tgt.getUUID(); s.beamTargetTicks = 0; }
        float ramp = 1f + Math.min(1.5f, s.beamTargetTicks / 30f);
        if (tgt != null) Ballistics.damage(l, p, tgt, def.dmg() * st.dmg * ramp, 0.3f, DamageKind.ENERGY, null, false, true);
        float hue = (s.useTicks % 40) / 40f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.35f + Math.min(0.5f, s.beamTargetTicks / 60f), 1f) & 0xFFFFFF;
        Fx.line(l, Fx.dust(rgb, 1.0f + ramp * 0.3f), mz, tr.end(), 0.8);
        Fx.line(l, ParticleTypes.END_ROD, mz, tr.end(), 4.0);
        if (tr.block() != null || tgt != null) {
            Fx.p(l, ParticleTypes.ELECTRIC_SPARK, tr.end(), 2, 0.1, 0.1, 0.1, 0.2);
            if (tr.block() != null && s.useTicks % 4 == 0) Fx.blockImpact(l, tr.block().getBlockPos(), tr.end(), 2);
        }
        if (s.useTicks % 8 == 1) Fx.sound(l, mz, "fire_beam", SoundSource.PLAYERS, 1.0f, 0.9f + ramp * 0.15f);
    }

    // ------------------------------------------------------------------------------------------------ smart / lock-on
    private static void smartTick(ServerPlayer p, ServerLevel l, ItemStack stack, GunDef def, PS s) {
        Vec3 eye = p.getEyePosition(), view = p.getViewVector(1f);
        double range = def.range();
        double cone = Math.cos(Math.toRadians(def.p("cone", 11)));
        LivingEntity best = null; double bestDot = cone;
        for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(eye, eye).inflate(range))) {
            if (!Ballistics.targetable(p, e)) continue;
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double d = to.length();
            if (d > range || d < 2) continue;
            double dot = to.normalize().dot(view);
            if (dot <= bestDot) continue;
            if (Ballistics.trace(l, p, eye, to.normalize(), d, 0, 0, false).block() != null) continue;
            bestDot = dot; best = e;
        }
        int need = (int) def.p("lock_ticks", 14);
        if (best != null && best.getUUID().equals(s.lock)) {
            s.lockProg = Math.min(100, s.lockProg + Math.max(1, 100 / need));
            if (s.lockProg >= 100 && !s.lockedOn) { s.lockedOn = true; Fx.soundTo(p, "lock_done", SoundSource.PLAYERS, 1f, 1f); }
            else if (!s.lockedOn && s.useTicks % 3 == 0) Fx.soundTo(p, "lock_beep", SoundSource.PLAYERS, 0.6f, 0.8f + s.lockProg / 120f);
        } else {
            s.lock = best == null ? null : best.getUUID();
            s.lockProg = 0; s.lockedOn = false;
        }
        if (s.useTicks % 2 == 0) ServerPlayNetworking.send(p, new Payloads.LockState(best == null ? -1 : best.getId(), s.lockProg, s.lockedOn));
    }

    private static void smartFire(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, PS s, Vec3 mz, Vec3 view) {
        int n = (int) def.p("missiles", 3);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Sched.later(i * 3, () -> {
                if (!p.isAlive()) return;
                Vec3 v = p.getViewVector(1f);
                Vec3 spreadV = Ballistics.spreadDir(v, 3 + idx * 1.5, RNG);
                GunProjectile pr = projectile(p, (ServerLevel) p.level(), def, st, GunProjectile.Kind.MISSILE, muzzle(p), spreadV, 0, def.dmg() * st.dmg,
                        (float) def.p("splash", 1.8), (float) def.p("splash_dmg", 2.0) * st.dmg, 110, 0, null);
                if (pr != null) {
                    pr.speed = def.p("speed", 1.0);
                    pr.setDeltaMovement(spreadV.scale(0.6));
                    if (s.lockedOn && s.lock != null) pr.target = s.lock;
                    Fx.sound((ServerLevel) p.level(), muzzle(p), "fire_smart", SoundSource.PLAYERS, 1.0f, 1.0f + idx * 0.08f);
                }
            });
        }
        ServerPlayNetworking.send(p, new Payloads.LockState(-1, 0, false));
    }

    // ------------------------------------------------------------------------------------------------ projectiles
    public static GunProjectile projectile(ServerPlayer p, ServerLevel l, GunDef def, GunStats st, GunProjectile.Kind kind, Vec3 at, Vec3 dir, double spreadDeg,
                                           float dmg, float splash, float splashDmg, int life, int bounces, UUID target) {
        GunProjectile pr = new GunProjectile(dev.telephoneorder.ModEntities.GUN_PROJECTILE, l);
        pr.setup(kind, dmg, splash, splashDmg, life, def.glow());
        pr.bounces = bounces;
        pr.target = target;
        pr.setOwner(p);
        Vec3 d = Ballistics.spreadDir(dir, spreadDeg, RNG);
        double speed = def.p("speed", 1.5);
        pr.speed = speed;
        pr.setPos(at);
        pr.setDeltaMovement(d.scale(speed).add(p.getDeltaMovement().multiply(1, 0.2, 1)));
        l.addFreshEntity(pr);
        return pr;
    }

    // ------------------------------------------------------------------------------------------------ prototype X-13
    private static void unstable(ServerPlayer p, ServerLevel l, ItemStack stack, GunDef def, GunStats st, Vec3 eye, Vec3 view, Vec3 mz, double spread) {
        float add = (float) (def.p("instability", 0.14) * 100);
        float[] inst = new float[1];
        GunData.edit(stack, t -> { float u = t.getFloatOr("unst", 0f) + add; inst[0] = u; t.putFloat("unst", u >= 100f ? 35f : u); });
        if (inst[0] >= 100f) {
            Fx.p(l, ParticleTypes.EXPLOSION, p.position().add(0, 1, 0), 1, 0, 0, 0, 0);
            Boom.explode(l, p.position().add(0, 1, 0), 3.5f, 9f, p, DamageKind.ENERGY, null, 1.0f, true);
            p.sendOverlayMessage(Component.literal("§cCORE BREACH"));
            return;
        }
        int roll = RNG.nextInt(100);
        float risk = inst[0] / 100f;
        if (roll < 28) {
            projectile(p, l, def, st, GunProjectile.Kind.PLASMA, mz, view, spread, def.dmg() * st.dmg, 1.8f, 3f, 60, 0, null);
        } else if (roll < 44) {   // lightning strike on the crosshair
            Ballistics.Trace tr = Ballistics.trace(l, p, eye, view, 60, 0, 0, false);
            Vec3 e = tr.end();
            for (int i = 0; i < 12; i++) l.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.x, e.y + i * 0.8, e.z, 3, 0.1, 0.1, 0.1, 0.1);
            Fx.p(l, ParticleTypes.EXPLOSION, e, 1, 0, 0, 0, 0);
            Fx.sound(l, e, "explosion_small", SoundSource.PLAYERS, 1.0f, 1.4f);
            Boom.explode(l, e, 2.6f, def.dmg() * st.dmg * 1.4f, p, DamageKind.ENERGY, null, 0.4f, false);
        } else if (roll < 58) {   // shockwave
            Fx.ring(l, Fx.dust(def.glow(), 1.6f), p.position(), 4.0, 32, 0.2);
            Fx.sound(l, p.position(), "emp_burst", SoundSource.PLAYERS, 1f, 1.1f);
            for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(5), x -> x != p && Ballistics.targetable(p, x))) {
                Vec3 away = e.position().subtract(p.position()).normalize();
                Ballistics.impulse(e, away.scale(1.1).add(0, 0.4, 0));
                Ballistics.damage(l, p, e, 3f * st.dmg, 0f, DamageKind.ENERGY, away, false, false);
            }
        } else if (roll < 70) {   // frost burst at crosshair
            Ballistics.Trace tr = Ballistics.trace(l, p, eye, view, 40, 0, 0, false);
            Vec3 e = tr.end();
            Fx.p(l, ParticleTypes.SNOWFLAKE, e, 40, 1.5, 1.0, 1.5, 0.05);
            for (LivingEntity le : l.getEntitiesOfClass(LivingEntity.class, new AABB(e, e).inflate(4), x -> x != p && Ballistics.targetable(p, x))) {
                le.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 100, 3)); le.setTicksFrozen(120);
            }
        } else if (roll < 80) {   // short blink
            Ballistics.Trace tr = Ballistics.trace(l, p, eye, view, 6, 0, 0, false);
            Vec3 dest = tr.end().subtract(view.scale(0.6));
            Fx.p(l, ParticleTypes.REVERSE_PORTAL, p.position().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.3);
            if (l.noCollision(p, p.getBoundingBox().move(dest.subtract(eye)))) p.teleportTo(dest.x, dest.y - 1.62, dest.z);
            Fx.sound(l, p.position(), "teleport_zap", SoundSource.PLAYERS, 0.5f, 1.8f);
        } else if (roll < 90) {   // repair
            p.heal(4f);
            Fx.p(l, ParticleTypes.HEART, p.position().add(0, 1.6, 0), 4, 0.3, 0.2, 0.3, 0.02);
        } else {                  // backfire
            Ballistics.damage(l, null, p, 2.0f + 3f * risk, 0f, DamageKind.ENERGY, null, false, true);
            Fx.p(l, ParticleTypes.ELECTRIC_SPARK, p.position().add(0, 1, 0), 14, 0.3, 0.5, 0.3, 0.3);
            p.sendOverlayMessage(Component.literal("§eCore feedback"));
        }
    }

    // ------------------------------------------------------------------------------------------------ reload
    private static boolean ballisticAmmo(GunDef d) {
        return switch (d.ammo()) { case "ammo_9mm", "ammo_357", "ammo_556", "ammo_762", "ammo_12g", "ammo_50bmg" -> true; default -> false; };
    }

    private static int inventoryRounds(ServerPlayer p, GunDef def) {
        Item it = ModItems.AMMO.get(def.ammo());
        AmmoDef ad = Catalog.AMMO_BY_ID.get(def.ammo());
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() == it) n += s.getCount() * ad.per();
        }
        return n;
    }

    private static ItemStack offhandSpecial(ServerPlayer p, GunDef def) {
        if (!ballisticAmmo(def)) return ItemStack.EMPTY;
        ItemStack o = p.getOffhandItem();
        if (o.getItem() instanceof AmmoItem ai && ai.def.special()) return o;
        return ItemStack.EMPTY;
    }

    public static int totalReserve(ServerPlayer p, ItemStack stack, GunDef def) {
        return GunData.reserve(stack) + inventoryRounds(p, def);
    }

    public static void requestReload(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        if (s.getItem() instanceof GunItem gi) startReload(p, s, gi.def);
    }

    public static void startReload(ServerPlayer p, ItemStack stack, GunDef def) {
        if (def.beh().equals("turret") || def.beh().equals("shield") && false) return;
        if (GunData.reloading(stack)) return;
        GunStats st = GunStats.of(def, stack);
        int ammo = GunData.ammo(stack);
        ItemStack special = offhandSpecial(p, def);
        boolean specialLoad = !special.isEmpty();
        if (ammo >= st.mag && !(specialLoad && !GunData.ammoMod(stack).equals(((AmmoItem) special.getItem()).def.mod()))) { actionbar(p, "Magazine full"); return; }
        if (!specialLoad && GunData.reserve(stack) + inventoryRounds(p, def) <= 0) {
            Fx.sound(p, "empty_click", SoundSource.PLAYERS, 0.8f, 1f);
            actionbar(p, "No ammo: " + Catalog.AMMO_BY_ID.get(def.ammo()).name());
            return;
        }
        long dur = Math.max(6L, Math.round(def.reload() * st.reload));
        long now = p.level().getGameTime();
        GunData.edit(stack, t -> { t.putLong("rend", now + dur); t.putLong("rstart", now); t.putLong("rtot", dur); });
        Fx.sound(p, "reload_" + def.rsound(), SoundSource.PLAYERS, 1.0f, 1.0f);
        GunData.syncModel(stack, 1);
        if (stack.getItem() instanceof GunItem gi) gi.playAnim(p, stack, "reload");
        PS s = ps(p);
        s.nextFire = Math.max(s.nextFire, now + dur);
    }

    public static void cancelReload(ItemStack stack) {
        GunData.edit(stack, t -> { t.putLong("rend", 0L); });
        GunData.syncModel(stack, 0);
    }

    private static void finishReload(ServerPlayer p, ItemStack stack, GunDef def, GunStats st) {
        int mag = st.mag;
        ItemStack special = offhandSpecial(p, def);
        CompoundTag t = GunData.read(stack);
        int ammo = t.getIntOr("ammo", 0);
        int reserve = t.getIntOr("reserve", 0);
        String amod = t.getStringOr("amod", "");
        if (!special.isEmpty()) {
            AmmoDef sd = ((AmmoItem) special.getItem()).def;
            int loaded = 0;
            int need = mag;
            while (need > 0 && special.getCount() > 0) {
                int take = Math.min(need, sd.per());
                loaded += take; need -= take; special.shrink(1);
            }
            if (!amod.isEmpty() && amod.equals(sd.mod())) loaded = Math.min(mag, ammo + loaded);
            else if (amod.isEmpty() && ammo > 0) reserve += ammo;     // standard rounds go back to reserve
            ammo = loaded; amod = sd.mod();
        } else {
            if (!amod.isEmpty() && ammo > 0) ammo = 0;                 // dumped special rounds when swapping to standard
            if (ammo == 0) amod = "";
            int need = mag - ammo;
            if (def.perRound()) need = Math.min(1, need);
            AmmoDef ad = Catalog.AMMO_BY_ID.get(def.ammo());
            Item it = ModItems.AMMO.get(def.ammo());
            while (need > 0) {
                if (reserve <= 0) {
                    boolean found = false;
                    for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
                        ItemStack s2 = p.getInventory().getItem(i);
                        if (s2.getItem() == it) { s2.shrink(1); reserve += ad.per(); found = true; break; }
                    }
                    if (!found) break;
                }
                int take = Math.min(need, reserve);
                reserve -= take; ammo += take; need -= take;
            }
        }
        int fAmmo = ammo, fRes = reserve; String fMod = amod;
        GunData.edit(stack, tt -> { tt.putInt("ammo", fAmmo); tt.putInt("reserve", fRes); tt.putString("amod", fMod); tt.putLong("rend", 0L); });
        GunData.syncModel(stack, 0);
        if (!def.perRound()) Fx.sound(p, "empty_click", SoundSource.PLAYERS, 0.4f, 1.8f);
    }

    /** Called every tick for each gun in a player's inventory. */
    public static void tickInventory(ServerPlayer p, ItemStack stack, GunItem item, boolean selected) {
        GunDef def = item.def;
        long now = p.level().getGameTime();
        CompoundTag t = GunData.read(stack);
        GunStats st = null;
        long rend = t.getLongOr("rend", 0L);
        if (rend > 0) {
            if (!selected) { cancelReload(stack); }
            else {
                st = GunStats.of(def, stack);
                long start = t.getLongOr("rstart", now), tot = Math.max(1, t.getLongOr("rtot", 1));
                double f = Mth.clamp((now - start) / (double) tot, 0, 1);
                int stage = f < 0.3 ? 1 : (f < 0.72 ? 2 : 1);
                if (stage != GunData.stage(stack)) GunData.syncModel(stack, stage);
                if (now >= rend) {
                    finishReload(p, stack, def, st);
                    if (def.perRound()) {
                        int a = GunData.ammo(stack);
                        if (a < st.mag && (GunData.reserve(stack) + inventoryRounds(p, def)) > 0) {
                            long dur = Math.max(6L, Math.round(def.reload() * st.reload));
                            GunData.edit(stack, tt -> { tt.putLong("rend", now + dur); tt.putLong("rstart", now); tt.putLong("rtot", dur); });
                            Fx.sound(p, "reload_" + def.rsound(), SoundSource.PLAYERS, 0.8f, 1.0f);
                            GunData.syncModel(stack, 1);
                        } else if (def.rsound().equals("pump")) {
                            Fx.sound(p, "reload_pump", SoundSource.PLAYERS, 0.8f, 1.0f);
                        }
                    }
                }
            }
        } else if (GunData.stage(stack) != 0) {
            GunData.syncModel(stack, 0);
        }
        // heat dissipation
        float heat = t.getFloatOr("heat", 0f);
        if (heat > 0 && def.usesHeat()) {
            PS s = STATE.get(p.getUUID());
            long last = s == null ? 0 : s.lastFire;
            if (now - last > 8 || !selected) {
                if (st == null) st = GunStats.of(def, stack);
                float nh = Math.max(0f, heat - def.cool() * st.cool);
                boolean oh = t.getBooleanOr("oh", false);
                boolean nowOh = oh && nh > 40f;
                if (oh && !nowOh) Fx.sound(p, "reload_cell", SoundSource.PLAYERS, 0.5f, 1.5f);
                if ((int) nh != (int) heat || oh != nowOh) GunData.edit(stack, tt -> { tt.putFloat("heat", nh); tt.putBoolean("oh", nowOh); });
                if (oh && selected && now % 4 == 0 && p.level() instanceof ServerLevel sl) {
                    Fx.p(sl, ParticleTypes.CLOUD, muzzle(p), 2, 0.05, 0.05, 0.05, 0.03);
                }
            }
        }
        if (def.beh().equals("unstable")) {
            float u = t.getFloatOr("unst", 0f);
            if (u > 0 && now % 4 == 0) GunData.edit(stack, tt -> tt.putFloat("unst", Math.max(0f, u - 1.5f)));
        }
    }

    public static void cycleMode(ServerPlayer p) {
        ItemStack s = p.getMainHandItem();
        if (!(s.getItem() instanceof GunItem gi)) return;
        List<String> modes = gi.def.modes();
        if (modes.size() <= 1) { actionbar(p, "This weapon has a single fire mode"); return; }
        int next = (GunData.mode(s) + 1) % modes.size();
        GunData.edit(s, t -> t.putInt("mode", next));
        Fx.sound(p, "empty_click", SoundSource.PLAYERS, 0.5f, 1.5f);
        actionbar(p, "Fire mode: " + modes.get(next).toUpperCase());
    }

    public static void setAim(ServerPlayer p, boolean aim) { ps(p).aiming = aim; }
}
