package dev.telephoneorder.weapon;

import dev.telephoneorder.Fx;
import dev.telephoneorder.ModConfig;
import dev.telephoneorder.net.Payloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative hitscan and damage application. */
public final class Ballistics {
    private Ballistics() {}

    public record EntityHit(Entity entity, Vec3 pos, double dist) {}
    public record Trace(Vec3 start, Vec3 end, List<EntityHit> hits, BlockHitResult block) {}

    /** Targets marked by The Black Ledger: uuid -> expiry game time. */
    public static final Map<UUID, Long> MARKS = new ConcurrentHashMap<>();

    public static Vec3 spreadDir(Vec3 dir, double degrees, java.util.Random r) {
        if (degrees <= 0.001) return dir;
        Vec3 up = Math.abs(dir.y) > 0.98 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = dir.cross(up).normalize();
        Vec3 realUp = right.cross(dir).normalize();
        double ang = r.nextDouble() * Math.PI * 2;
        double mag = Math.tan(Math.toRadians(degrees)) * Math.sqrt(r.nextDouble());
        return dir.add(right.scale(Math.cos(ang) * mag)).add(realUp.scale(Math.sin(ang) * mag)).normalize();
    }

    private static boolean thin(ServerLevel l, BlockPos pos) {
        BlockState st = l.getBlockState(pos);
        return st.getDestroySpeed(l, pos) <= 1.6f || st.is(BlockTags.LEAVES) || st.is(BlockTags.IMPERMEABLE) || st.is(BlockTags.WOOL);
    }

    public static boolean targetable(Entity shooter, Entity e) {
        if (e == shooter || !e.isAlive() || e.isSpectator() || !e.isPickable() || e instanceof ItemEntity) return false;
        if (shooter != null && e.getRootVehicle() == shooter.getRootVehicle()) return false;
        if (e instanceof dev.telephoneorder.entity.GunProjectile) return false;
        if (e instanceof Player tp && shooter instanceof ServerPlayer sp && (!sp.canHarmPlayer(tp) || !ModConfig.get().allowFriendlyDamage)) return false;
        return true;
    }

    public static Trace trace(ServerLevel lvl, Entity shooter, Vec3 from, Vec3 dir, double range, int pierceEntities, int pierceBlocks, boolean phase) {
        Vec3 end = from.add(dir.scale(range));
        Vec3 stop = end;
        BlockHitResult hitBlock = null;
        if (!phase) {
            Vec3 segStart = from;
            for (int i = 0; i <= pierceBlocks; i++) {
                BlockHitResult r = lvl.clip(new ClipContext(segStart, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter != null ? net.minecraft.world.phys.shapes.CollisionContext.of(shooter) : net.minecraft.world.phys.shapes.CollisionContext.empty()));
                if (r.getType() == HitResult.Type.MISS) { hitBlock = null; stop = end; break; }
                hitBlock = r;
                stop = r.getLocation();
                if (i < pierceBlocks && thin(lvl, r.getBlockPos())) {
                    segStart = r.getLocation().add(dir.scale(0.6));
                    if (segStart.distanceToSqr(end) < 0.5) { stop = end; hitBlock = null; break; }
                    continue;
                }
                break;
            }
        }
        List<EntityHit> hits = new ArrayList<>();
        AABB box = new AABB(from, stop).inflate(1.0);
        for (Entity e : lvl.getEntities(shooter, box, x -> targetable(shooter, x))) {
            AABB bb = e.getBoundingBox().inflate(Math.max(0.0, e.getPickRadius()));
            Optional<Vec3> o = bb.clip(from, stop);
            if (o.isPresent()) hits.add(new EntityHit(e, o.get(), o.get().distanceTo(from)));
            else if (bb.contains(from)) hits.add(new EntityHit(e, from, 0));
        }
        hits.sort(Comparator.comparingDouble(EntityHit::dist));
        if (hits.size() > pierceEntities + 1) hits = new ArrayList<>(hits.subList(0, pierceEntities + 1));
        // if we pierce entities, the beam goes on; if not, it stops at the first one
        if (!hits.isEmpty() && pierceEntities == 0 && !phase) stop = hits.get(0).pos();
        return new Trace(from, stop, hits, hitBlock);
    }

    public static boolean isHead(Entity e, Vec3 hit) {
        if (!(e instanceof LivingEntity)) return false;
        double h = e.getBbHeight();
        return h >= 0.9 && hit.y >= e.getY() + h * 0.80;
    }

    public static boolean isLegs(Entity e, Vec3 hit) {
        return e instanceof LivingEntity && e.getBbHeight() >= 1.2 && hit.y < e.getY() + e.getBbHeight() * 0.3;
    }

    /**
     * Applies damage to a target. {@code ap} is the fraction that ignores armor. Returns true if damage was dealt.
     */
    public static boolean damage(ServerLevel lvl, ServerPlayer shooter, Entity target, float amount, float ap, DamageKind kind, Vec3 dir, boolean head, boolean silentMarker) {
        if (amount <= 0 || !target.isAlive()) return false;
        amount *= ModConfig.get().damageMultiplier;
        if (shooter != null && target instanceof LivingEntity) {
            Long exp = MARKS.get(target.getUUID());
            if (exp != null) {
                if (exp > lvl.getGameTime()) amount *= 1.5f; else MARKS.remove(target.getUUID());
            }
        }
        boolean hurt;
        if (ap > 0.01f && target instanceof LivingEntity) {
            float apPart = amount * Math.min(1f, ap);
            hurt = target.hurtServer(lvl, DamageKind.ARMOR_PIERCING.source(lvl, shooter, shooter), apPart);
            if (amount - apPart > 0.01f) hurt |= target.hurtServer(lvl, kind.source(lvl, shooter, shooter), amount - apPart);
        } else {
            hurt = target.hurtServer(lvl, kind.source(lvl, shooter, shooter), amount);
        }
        if (hurt) {
            if (dir != null && target instanceof LivingEntity && kind != DamageKind.EMP) {
                target.push(dir.x * 0.07, 0.01, dir.z * 0.07);
            }
            if (shooter != null && !silentMarker) {
                boolean dead = target instanceof LivingEntity le && (le.isDeadOrDying());
                ServerPlayNetworking.send(shooter, new Payloads.HitMarker(dead ? 2 : (head ? 1 : 0)));
            }
        }
        return hurt;
    }

    public static float falloff(double dist, double range, double startFrac, double minMul) {
        double s = range * startFrac;
        if (dist <= s) return 1f;
        double t = Mth.clamp((dist - s) / Math.max(0.001, range - s), 0, 1);
        return (float) (1.0 - (1.0 - minMul) * t);
    }

    public static void impulse(Entity e, Vec3 v) {
        e.push(v.x, v.y, v.z);
        if (e instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
    }

    /** Alerts hostile mobs to a gunshot. */
    public static void alertMobs(ServerLevel lvl, ServerPlayer shooter, double radius) {
        if (radius < 3) return;
        for (net.minecraft.world.entity.Mob m : lvl.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, shooter.getBoundingBox().inflate(radius),
                x -> x instanceof net.minecraft.world.entity.monster.Enemy && x.getTarget() == null)) {
            if (shooter.getRandom().nextFloat() < 0.35f) m.setTarget(shooter);
        }
    }

    public static void soundAt(ServerLevel l, Vec3 at, String name, float vol, float pitch) {
        Fx.sound(l, at, name, SoundSource.PLAYERS, vol, pitch);
    }
}
