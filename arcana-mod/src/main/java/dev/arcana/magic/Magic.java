package dev.arcana.magic;

import dev.arcana.ArcanaConfig;
import dev.arcana.item.Core;
import dev.arcana.item.Fx;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Shared spell toolbox: aiming, targeting, damage pipeline (mastery, cores, combos) and particle helpers. */
public final class Magic {
    private static final String TAG_MARK = "arcana_mark";
    private static final String TAG_MARK_T = "arcana_mark_t";
    private static final String TAG_COMBO_T = "arcana_combo_t";

    // ------------------------------------------------------------------ aiming

    public static Vec3 eye(Player p) {
        return p.getEyePosition();
    }

    public static Vec3 look(Player p) {
        return p.getLookAngle();
    }

    public static Vec3 flatLook(Player p) {
        Vec3 l = p.getLookAngle();
        Vec3 f = new Vec3(l.x, 0, l.z);
        return f.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : f.normalize();
    }

    /** First block hit along the look vector, or the far end point. */
    public static BlockHitResult rayBlocks(SpellContext c, double range) {
        Vec3 a = eye(c.player);
        Vec3 b = a.add(look(c.player).scale(range));
        return c.level.clip(new ClipContext(a, b, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, c.player));
    }

    public static Vec3 aimPoint(SpellContext c, double range) {
        return rayBlocks(c, range).getLocation();
    }

    /** First hostile/enemy living entity along the look vector (blocked by terrain). */
    public static LivingEntity aimEntity(SpellContext c, double range) {
        Vec3 a = eye(c.player);
        Vec3 dir = look(c.player);
        double limit = a.distanceTo(rayBlocks(c, range).getLocation());
        Vec3 b = a.add(dir.scale(limit));
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(a, b).inflate(1.5), x -> isEnemy(c.player, x))) {
            var hit = e.getBoundingBox().inflate(0.35).clip(a, b);
            if (hit.isPresent()) {
                double d = a.distanceToSqr(hit.get());
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
        }
        return best;
    }

    public static boolean isEnemy(Player caster, Entity e) {
        if (!(e instanceof LivingEntity le) || e == caster || !le.isAlive() || e instanceof ArmorStand) return false;
        if (e.isSpectator() || e.isInvulnerable()) return false;
        if (e instanceof Player p) {
            if (p.isCreative() || p.isSpectator()) return false;
            if (ArcanaConfig.RESPECT_PVP.get() && !caster.getServer().isPvpAllowed()) return false;
            return !caster.isAlliedTo(p);
        }
        if (e instanceof TamableAnimal t && t.isOwnedBy(caster)) return false;
        if (e.getPersistentData().getBoolean("arcana_summon")) return false;
        return !caster.isAlliedTo(e);
    }

    public static List<LivingEntity> enemies(SpellContext c, Vec3 center, double r) {
        return c.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r),
            e -> isEnemy(c.player, e) && e.position().add(0, e.getBbHeight() / 2, 0).distanceToSqr(center) <= r * r);
    }

    public static List<LivingEntity> allies(SpellContext c, Vec3 center, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : c.level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(r))) {
            if (!e.isAlive() || e.position().distanceToSqr(center) > r * r) continue;
            if (e == c.player || (e instanceof Player p && !isEnemy(c.player, p) && p.isAlliedTo(c.player))
                || (e instanceof TamableAnimal t && t.isOwnedBy(c.player))
                || e.getPersistentData().getBoolean("arcana_summon")) out.add(e);
        }
        return out;
    }

    // ------------------------------------------------------------------ damage pipeline

    /** Damage {@code target} with base damage scaled by mastery/stage/runes/config, applying cores, leech and combos. */
    public static boolean hurt(SpellContext c, LivingEntity target, float base) {
        if (!isEnemy(c.player, target)) return false;
        float dmg = base * c.damage;
        int prevMark = target.getPersistentData().contains(TAG_MARK) ? target.getPersistentData().getInt(TAG_MARK) : -1;
        long markAge = c.level.getGameTime() - target.getPersistentData().getLong(TAG_MARK_T);
        School prev = prevMark >= 0 && markAge < 100 ? School.values()[prevMark] : null;
        School cur = c.school;
        Combos.Combo combo = prev != null && cur != School.NONE ? Combos.find(prev, cur) : null;
        if (combo != null && c.level.getGameTime() - target.getPersistentData().getLong(TAG_COMBO_T) < 40) combo = null;
        if (combo != null) dmg *= 1.25f;

        if (!rawHurt(c, target, dmg)) return false;

        target.getPersistentData().putInt(TAG_MARK, cur.ordinal());
        target.getPersistentData().putLong(TAG_MARK_T, c.level.getGameTime());
        applyCore(c, target, dmg);
        double leech = c.leech();
        if (leech > 0) c.player.heal((float) (dmg * leech));
        if (c.level.random.nextInt(2) == 0) c.player.getData(dev.arcana.registry.ModAttachments.MAGIC).addXp(c.school, 1);

        if (combo != null) {
            target.getPersistentData().putLong(TAG_COMBO_T, c.level.getGameTime());
            c.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("combo.arcana.trigger",
                net.minecraft.network.chat.Component.translatable("combo.arcana." + combo.id())), true);
            combo.effect().accept(c, target);
        }
        return true;
    }

    /** Applies damage without marks/combos (used by combo effects and hazards to prevent recursion). */
    public static boolean rawHurt(SpellContext c, LivingEntity target, float dmg) {
        target.invulnerableTime = 0;
        return target.hurt(c.level.damageSources().indirectMagic(c.player, c.player), dmg);
    }

    private static void applyCore(SpellContext c, LivingEntity t, float dmg) {
        Core core = c.style.coreType();
        switch (core) {
            case EMBER -> t.igniteForSeconds(4);
            case FROST -> {
                t.setTicksFrozen(Math.min(t.getTicksRequiredToFreeze() + 40, t.getTicksFrozen() + 60));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
            case VOLT -> {
                for (LivingEntity o : enemies(c, t.position(), 4)) {
                    if (o != t) {
                        rawHurt(c, o, dmg * 0.25f);
                        line(c.level, ParticleTypes.ELECTRIC_SPARK, t.position().add(0, 1, 0), o.position().add(0, 1, 0), 0.5);
                        break;
                    }
                }
            }
            case SPIRIT -> c.player.heal(dmg * 0.1f);
            default -> {}
        }
    }

    public static void root(LivingEntity e, int ticks) {
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 6, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, ticks, 0, false, false));
    }

    public static void effect(LivingEntity e, net.minecraft.core.Holder<MobEffect> eff, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(eff, ticks, amp, false, true));
    }

    // ------------------------------------------------------------------ movement

    public static void push(Entity e, Vec3 vel) {
        e.setDeltaMovement(vel);
        e.hurtMarked = true;
    }

    public static void pull(Entity e, Vec3 center, double strength) {
        Vec3 d = center.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
        double dist = d.length();
        if (dist < 0.2) return;
        Vec3 v = d.scale(1.0 / dist).scale(Math.min(strength, dist * 0.6));
        e.setDeltaMovement(e.getDeltaMovement().scale(0.55).add(v));
        e.hurtMarked = true;
        e.fallDistance = 0;
    }

    // ------------------------------------------------------------------ particles & sound

    public static int count(int n) {
        return Math.max(1, Math.round(n * ArcanaConfig.PARTICLES.get().floatValue()));
    }

    public static void fx(ServerLevel l, ParticleOptions p, Vec3 pos, int n, double dx, double dy, double dz, double speed) {
        l.sendParticles(p, pos.x, pos.y, pos.z, count(n), dx, dy, dz, speed);
    }

    public static DustParticleOptions dust(SpellContext c, float size) {
        return School.dust(c.rgb(), size);
    }

    /** The wand's cosmetic trail particle (custom fx or the school default). */
    public static ParticleOptions trail(SpellContext c) {
        Fx f = c.style.fxType();
        return f.particle != null ? f.particle : dust(c, 1.0f);
    }

    /** Spawns a short-lived 3D cinematic effect model (see {@link dev.arcana.entity.FxKind}). */
    public static void fx3d(ServerLevel l, dev.arcana.entity.FxKind kind, Vec3 pos, float scale, int life, int profile) {
        dev.arcana.entity.SpellFxEntity.spawn(l, kind, pos, scale, life, profile);
    }

    public static void line(ServerLevel l, ParticleOptions p, Vec3 a, Vec3 b, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 dir = d.scale(1 / len);
        double s = step / Math.max(0.1, ArcanaConfig.PARTICLES.get());
        for (double t = 0; t <= len; t += s) {
            Vec3 q = a.add(dir.scale(t));
            l.sendParticles(p, q.x, q.y, q.z, 1, 0, 0, 0, 0);
        }
    }

    public static void ring(ServerLevel l, ParticleOptions p, Vec3 center, double radius, int points, double yOffset) {
        int n = count(points);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n;
            l.sendParticles(p, center.x + Math.cos(a) * radius, center.y + yOffset, center.z + Math.sin(a) * radius, 1, 0, 0, 0, 0);
        }
    }

    public static void snd(ServerLevel l, Vec3 pos, SoundEvent s, float vol, float pitch) {
        l.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    // ------------------------------------------------------------------ terrain helpers

    public static double groundY(ServerLevel l, double x, double y, double z) {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y) + 2, Mth.floor(z));
        for (int i = 0; i < 8; i++) {
            BlockState s = l.getBlockState(p.below());
            if (!s.isAir() && !s.getCollisionShape(l, p.below()).isEmpty()) return p.getY();
            p.move(0, -1, 0);
        }
        return y;
    }

    public static boolean canStand(ServerLevel l, Entity e, Vec3 feet) {
        AABB box = e.getBoundingBox().move(feet.subtract(e.position()));
        return l.noCollision(e, box) && !l.containsAnyLiquid(box);
    }

    public static Vec3 rotateY(Vec3 v, double rad) {
        double cos = Math.cos(rad), sin = Math.sin(rad);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    public static ServerPlayer owner(SpellContext c) {
        return c.player;
    }

    private Magic() {}
}
