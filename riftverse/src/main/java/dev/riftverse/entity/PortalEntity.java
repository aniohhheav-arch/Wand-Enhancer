package dev.riftverse.entity;

import dev.riftverse.registry.RvEntities;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One end of a portal-gun portal pair. Anything that passes through the membrane is moved to the partner portal with its
 * momentum re-oriented, so you can fling yourself across the world.
 */
public class PortalEntity extends Entity {
    private static final EntityDataAccessor<Integer> SLOT = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACE = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> UP = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PARTNER = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);

    public static final int COLOR_A = 0x2FC8FF;
    public static final int COLOR_B = 0xFF8A1F;
    public static final float HALF_W = 0.62f;
    public static final float HALF_H = 1.05f;

    private static final Map<UUID, UUID[]> BY_OWNER = new HashMap<>();

    @Nullable
    private UUID owner;
    @Nullable
    private UUID partnerId;

    public PortalEntity(EntityType<? extends PortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(SLOT, 0);
        builder.define(FACE, Direction.NORTH.ordinal());
        builder.define(UP, Direction.UP.ordinal());
        builder.define(PARTNER, -1);
    }

    public int slot() {
        return entityData.get(SLOT);
    }

    public int color() {
        return slot() == 0 ? COLOR_A : COLOR_B;
    }

    public Direction face() {
        return Direction.from3DDataValue(entityData.get(FACE));
    }

    public Direction upDir() {
        return Direction.from3DDataValue(entityData.get(UP));
    }

    public Vec3 normal() {
        return Vec3.atLowerCornerOf(face().getNormal());
    }

    public Vec3 up() {
        return Vec3.atLowerCornerOf(upDir().getNormal());
    }

    public Vec3 right() {
        return up().cross(normal());
    }

    @Nullable
    public PortalEntity partner() {
        int id = entityData.get(PARTNER);
        if (id < 0) return null;
        Entity e = level().getEntity(id);
        return e instanceof PortalEntity p && p.isAlive() ? p : null;
    }

    /** Opens a portal on a surface, replacing the owner's previous portal of the same colour and linking the pair. */
    public static PortalEntity place(ServerLevel level, Player owner, int slot, Vec3 center, Direction face, Direction up) {
        UUID[] pair = BY_OWNER.computeIfAbsent(owner.getUUID(), k -> new UUID[2]);
        if (pair[slot] != null && level.getEntity(pair[slot]) instanceof PortalEntity old) old.close(false);
        PortalEntity portal = new PortalEntity(RvEntities.PORTAL.get(), level);
        portal.owner = owner.getUUID();
        portal.entityData.set(SLOT, slot);
        portal.entityData.set(FACE, face.ordinal());
        portal.entityData.set(UP, up.ordinal());
        portal.moveTo(center.x, center.y, center.z, 0, 0);
        level.addFreshEntity(portal);
        pair[slot] = portal.getUUID();
        UUID other = pair[1 - slot];
        if (other != null && level.getEntity(other) instanceof PortalEntity partner) {
            portal.link(partner);
        }
        level.playSound(null, center.x, center.y, center.z, RvSounds.PORTAL_OPEN.get(), SoundSource.PLAYERS, 1.0f, slot == 0 ? 1.15f : 0.9f);
        level.sendParticles(RvParticles.RING.get().with(portal.color(), 1.8f, 14), center.x, center.y, center.z, 1, 0, 0, 0, 0);
        return portal;
    }

    private void link(PortalEntity other) {
        this.partnerId = other.getUUID();
        other.partnerId = this.getUUID();
        this.entityData.set(PARTNER, other.getId());
        other.entityData.set(PARTNER, this.getId());
    }

    public void close(boolean effects) {
        if (effects && level() instanceof ServerLevel server) {
            server.sendParticles(RvParticles.SPARK.get().with(color(), 0.5f, 14), getX(), getY(), getZ(), 20, 0.3, 0.5, 0.3, 0.1);
        }
        PortalEntity p = partner();
        if (p != null) p.entityData.set(PARTNER, -1);
        discard();
    }

    public static void closeAll(ServerLevel level, UUID owner) {
        UUID[] pair = BY_OWNER.remove(owner);
        if (pair == null) return;
        for (UUID id : pair) if (id != null && level.getEntity(id) instanceof PortalEntity p) p.close(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (random.nextInt(2) == 0) {
                double a = random.nextDouble() * Math.PI * 2;
                Vec3 off = up().scale(Math.sin(a) * HALF_H).add(right().scale(Math.cos(a) * HALF_W));
                Vec3 p = position().add(off);
                Vec3 v = normal().scale(0.02 + random.nextDouble() * 0.03);
                level().addParticle(RvParticles.SPARK.get().with(color(), 0.18f, 18), p.x, p.y, p.z, v.x, v.y, v.z);
            }
            return;
        }
        if (owner != null) {
            UUID[] pair = BY_OWNER.computeIfAbsent(owner, k -> new UUID[2]);
            if (pair[slot()] == null) pair[slot()] = getUUID();
        }
        PortalEntity partner = partner();
        if (partner == null && partnerId != null && level() instanceof ServerLevel server && server.getEntity(partnerId) instanceof PortalEntity p) {
            link(p);
            partner = p;
        }
        if (partner == null) return;
        AABB zone = new AABB(position(), position()).inflate(1.6);
        List<Entity> list = level().getEntities(this, zone, e -> !(e instanceof PortalEntity) && !(e instanceof BlackHoleEntity) && !e.isPassenger() && !e.isSpectator());
        for (Entity e : list) tryTeleport(e, partner);
    }

    private void tryTeleport(Entity e, PortalEntity partner) {
        long now = level().getGameTime();
        CompoundTag pd = e.getPersistentData();
        if (now - pd.getLong("riftverse:portal_cd") < 8) return;
        Vec3 n = normal();
        Vec3 c = e.getBoundingBox().getCenter();
        Vec3 rel = c.subtract(position());
        double dn = rel.dot(n);
        double du = rel.dot(up());
        double dr = rel.dot(right());
        double reach = Math.max(0.35, e.getBbWidth() * 0.6);
        if (dn > reach || dn < -0.5) return;
        if (Math.abs(du) > HALF_H || Math.abs(dr) > HALF_W) return;
        Vec3 v = e.getDeltaMovement();
        if (v.dot(n) > 0.05) return;

        Vec3 nb = partner.normal();
        Vec3 ub = partner.up();
        Vec3 rb = partner.right();
        Vec3 out = transform(v, n, up(), right(), nb, ub, rb);
        double minExit = e instanceof Player ? 0.32 : 0.2;
        if (out.dot(nb) < minExit) out = out.add(nb.scale(minExit - out.dot(nb)));

        double h = e.getBbHeight();
        Vec3 exitCenter = partner.position().add(nb.scale(Math.max(0.6, e.getBbWidth() * 0.5 + 0.25))).add(ub.scale(Mth.clamp(du, -0.3, 0.3))).add(rb.scale(-Mth.clamp(dr, -0.2, 0.2)));
        Vec3 feet;
        if (partner.face() == Direction.UP) feet = partner.position().add(0, 0.05, 0);
        else if (partner.face() == Direction.DOWN) feet = partner.position().add(0, -h - 0.05, 0);
        else feet = exitCenter.add(0, -h * 0.5, 0);

        Vec3 look = transform(e.getLookAngle(), n, up(), right(), nb, ub, rb);
        float yaw = (float) (Mth.atan2(-look.x, look.z) * Mth.RAD_TO_DEG);
        float pitch = (float) (-Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        if (Math.abs(look.y) > 0.98) yaw = e.getYRot();

        pd.putLong("riftverse:portal_cd", now);
        if (e instanceof ServerPlayer player) {
            player.connection.teleport(feet.x, feet.y, feet.z, yaw, Mth.clamp(pitch, -89f, 89f));
            player.setDeltaMovement(out);
            player.hurtMarked = true;
            player.resetFallDistance();
        } else {
            e.teleportTo(feet.x, feet.y, feet.z);
            e.setYRot(yaw);
            e.setDeltaMovement(out);
            e.hurtMarked = true;
            e.resetFallDistance();
        }
        ServerLevel server = (ServerLevel) level();
        server.playSound(null, partner.getX(), partner.getY(), partner.getZ(), RvSounds.PORTAL_ENTER.get(), SoundSource.PLAYERS, 0.6f, 1.4f);
        server.sendParticles(RvParticles.SPARK.get().with(partner.color(), 0.4f, 12), partner.getX(), partner.getY(), partner.getZ(), 14, 0.3, 0.5, 0.3, 0.15);
    }

    /** Maps a vector entering portal A to the corresponding vector leaving portal B. */
    private static Vec3 transform(Vec3 v, Vec3 na, Vec3 ua, Vec3 ra, Vec3 nb, Vec3 ub, Vec3 rb) {
        double a = v.dot(na);
        double b = v.dot(ua);
        double c = v.dot(ra);
        return nb.scale(-a).add(ub.scale(b)).add(rb.scale(-c));
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 160 * 160;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(SLOT, tag.getInt("slot"));
        entityData.set(FACE, tag.getInt("face"));
        entityData.set(UP, tag.contains("up") ? tag.getInt("up") : Direction.UP.ordinal());
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.hasUUID("partner")) partnerId = tag.getUUID("partner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("slot", slot());
        tag.putInt("face", entityData.get(FACE));
        tag.putInt("up", entityData.get(UP));
        if (owner != null) tag.putUUID("owner", owner);
        if (partnerId != null) tag.putUUID("partner", partnerId);
    }
}
