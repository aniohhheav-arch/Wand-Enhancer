package com.infinitemultiverse.cosmic.portal;

import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * One end of a linked portal pair. Anything that moves into it — players, mobs, items, projectiles — comes out of the
 * partner with its momentum turned to match the exit (a fall into a floor portal flings you out of a wall portal).
 * Loop prevention: a short per-entity cooldown and a cap on transits per second.
 */
public final class PortalEntity extends Entity {
    public static final int BLUE = 0;
    public static final int ORANGE = 1;
    public static final double HALF_W = 0.5;
    public static final double HALF_H = 1.0;
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> FACING = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> UP = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LINKED = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(PortalEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final String LAST = "infinitemultiverse_portal_last";
    private static final String BURST = "infinitemultiverse_portal_burst";

    public PortalEntity(EntityType<? extends PortalEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(COLOR, BLUE);
        builder.define(FACING, Direction.NORTH.get3DDataValue());
        builder.define(UP, Direction.UP.get3DDataValue());
        builder.define(LINKED, false);
        builder.define(OWNER, Optional.empty());
    }

    public void setup(UUID owner, int color, Direction facing, Direction up) {
        entityData.set(OWNER, Optional.of(owner));
        entityData.set(COLOR, color);
        entityData.set(FACING, facing.get3DDataValue());
        entityData.set(UP, up.get3DDataValue());
        setBoundingBox(makeBoundingBox());
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public Direction facing() {
        return Direction.from3DDataValue(entityData.get(FACING));
    }

    public Direction up() {
        return Direction.from3DDataValue(entityData.get(UP));
    }

    public boolean linked() {
        return entityData.get(LINKED);
    }

    @Nullable
    public UUID owner() {
        return entityData.get(OWNER).orElse(null);
    }

    public static Vec3 vec(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    /** Right vector of a portal frame (facing × up). */
    public static Vec3 right(Direction facing, Direction up) {
        return vec(up).cross(vec(facing));
    }

    @Override
    protected AABB makeBoundingBox() {
        if (entityData == null) {
            return super.makeBoundingBox();
        }
        Vec3 c = position();
        Vec3 u = vec(up()).scale(HALF_H), r = right(facing(), up()).scale(HALF_W), n = vec(facing()).scale(0.08);
        Vec3 a = c.add(u).add(r).add(n), b = c.subtract(u).subtract(r).subtract(n);
        return new AABB(a, b);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        setBoundingBox(makeBoundingBox());
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        if (entityData != null) {
            setBoundingBox(makeBoundingBox());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        UUID owner = owner();
        PortalNetwork net = PortalNetwork.get(level.getServer());
        PortalNetwork.End self = owner == null ? null : net.get(owner, color());
        if (self == null || !self.entity().equals(getUUID())) {
            discard();
            return;
        }
        PortalNetwork.End partner = net.partner(owner, color());
        entityData.set(LINKED, partner != null);
        if (partner == null) {
            return;
        }
        ServerLevel targetLevel = level.getServer().getLevel(partner.dimension());
        if (targetLevel == null) {
            return;
        }
        Vec3 n = vec(facing());
        AABB zone = getBoundingBox().inflate(0.35).expandTowards(n.scale(0.6));
        for (Entity e : level.getEntities(this, zone, e -> e.isAlive() && !(e instanceof PortalEntity) && !(e instanceof ArmorStand) && !e.isPassenger())) {
            Vec3 center = e.getBoundingBox().getCenter();
            Vec3 rel = center.subtract(position());
            double depth = rel.dot(n);
            boolean approaching = e.getDeltaMovement().dot(n) < 0.02 || e instanceof Player;
            if (depth > Math.max(0.6, e.getBbWidth() * 0.6 + 0.2) || !approaching) {
                continue;
            }
            if (Math.abs(rel.dot(vec(up()))) > HALF_H + 0.2 || Math.abs(rel.dot(right(facing(), up()))) > HALF_W + 0.25) {
                continue;
            }
            if (!readyToTransit(e, level.getGameTime())) {
                continue;
            }
            transit(e, rel, targetLevel, partner);
        }
    }

    private static boolean readyToTransit(Entity e, long now) {
        CompoundTag data = e.getPersistentData();
        if (now - data.getLong(LAST) < 8) {
            return false;
        }
        // At most 6 transits in any second: an endless floor-to-ceiling loop gets dropped out after a moment.
        long windowStart = data.getLong(BURST) >> 8;
        int count = (int) (data.getLong(BURST) & 0xFF);
        if (now - windowStart > 20) {
            windowStart = now;
            count = 0;
        }
        if (count >= 6) {
            return false;
        }
        data.putLong(BURST, (windowStart << 8) | (count + 1));
        data.putLong(LAST, now);
        return true;
    }

    private void transit(Entity e, Vec3 rel, ServerLevel targetLevel, PortalNetwork.End exit) {
        Vec3 n1 = vec(facing()), u1 = vec(up()), r1 = right(facing(), up());
        Vec3 n2 = vec(exit.facing()), u2 = vec(exit.up()), r2 = right(exit.facing(), exit.up());
        Vec3 v = e.getDeltaMovement();
        Vec3 out = mapThrough(v, n1, u1, r1, n2, u2, r2);
        double forward = out.dot(n2);
        if (forward < 0.3) {
            out = out.add(n2.scale(0.3 - forward));
        }
        Vec3 offset = u2.scale(Mth.clamp(rel.dot(u1), -HALF_H + 0.2, HALF_H - 0.2)).add(r2.scale(-Mth.clamp(rel.dot(r1), -0.3, 0.3)));
        Vec3 center = exit.center().add(offset).add(n2.scale(e.getBbWidth() * 0.5 + 0.25));
        Vec3 feet = center.subtract(0, e.getBbHeight() * 0.5, 0);
        if (n2.y > 0.5) {
            feet = exit.center().add(n2.scale(0.1));
        }
        Vec3 look = mapThrough(e.getLookAngle(), n1, u1, r1, n2, u2, r2);
        float yaw = (float) (Mth.atan2(look.z, look.x) * Mth.RAD_TO_DEG) - 90f;
        float pitch = (float) -(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * Mth.RAD_TO_DEG);
        if (Math.abs(n2.y) > 0.5) {
            yaw = e.getYRot();
        }
        ServerLevel from = (ServerLevel) level();
        Cinematics.scene(from, SceneIds.PORTAL_TRANSIT, position(), n1, color() == BLUE ? 0x3DA0FF : 0xFF8A1A, 10, null, 1f);
        e.teleportTo(targetLevel, feet.x, feet.y, feet.z, Set.<RelativeMovement>of(), yaw, Mth.clamp(pitch, -90, 90));
        if (e.isRemoved()) {
            // Non-player entities are re-created when changing dimension; they keep their position but not momentum.
            return;
        }
        Entity finalEntity = e;
        finalEntity.setDeltaMovement(out);
        finalEntity.hurtMarked = true;
        finalEntity.fallDistance = 0;
        if (finalEntity instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
        Cinematics.scene(targetLevel, SceneIds.PORTAL_TRANSIT, exit.center(), n2, color() == BLUE ? 0xFF8A1A : 0x3DA0FF, 10, null, 1f);
        MultiverseVfx.sound(targetLevel, exit.center(), ModSounds.PHASE_STEP_ARRIVE, 0.6f, 1.4f);
    }

    /** Express v in the entry frame (going into the portal), re-emit it in the exit frame (coming out). Mirror the side axis. */
    static Vec3 mapThrough(Vec3 v, Vec3 n1, Vec3 u1, Vec3 r1, Vec3 n2, Vec3 u2, Vec3 r2) {
        double in = -v.dot(n1), up = v.dot(u1), side = v.dot(r1);
        return n2.scale(in).add(u2.scale(up)).add(r2.scale(-side));
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        if (tag.hasUUID("Owner")) {
            entityData.set(OWNER, Optional.of(tag.getUUID("Owner")));
        }
        entityData.set(COLOR, tag.getInt("Color"));
        entityData.set(FACING, tag.getInt("Facing"));
        entityData.set(UP, tag.getInt("Up"));
        setBoundingBox(makeBoundingBox());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        UUID owner = owner();
        if (owner != null) {
            tag.putUUID("Owner", owner);
        }
        tag.putInt("Color", color());
        tag.putInt("Facing", entityData.get(FACING));
        tag.putInt("Up", entityData.get(UP));
    }
}
