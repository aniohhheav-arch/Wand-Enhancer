package com.stickyfingers.entity;

import com.stickyfingers.config.SFConfig;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModEntities;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import com.stickyfingers.world.SeparationManager;
import com.stickyfingers.world.ZipperNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.*;

/**
 * A single zipper seam. Purely a visual + logic anchor: it renders the animated teeth/slider/interior,
 * owns any temporary block separation it caused, and acts as a portal endpoint when linked.
 * Zippers are never saved with the world; every world change they make is restored by {@link SeparationManager}.
 */
public class ZipperEntity extends Entity {
    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> NORMAL = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> ANGLE = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> WIDTH = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> OPEN = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> ZIPPER_ID = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> ATTACHED = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> ANCHOR = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Integer> SEED = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LINK_COUNT = SynchedEntityData.defineId(ZipperEntity.class, EntityDataSerializers.INT);

    // client animation
    private float openProgress;
    private float prevOpenProgress;
    private float appear;
    private float prevAppear;

    // server state
    private final List<Integer> links = new ArrayList<>();
    private List<BlockPos> openArea = List.of();
    private int separationTicks;
    private int life = -1;
    private int closingTimer = -1;
    private int effectDuration = 60;
    private boolean registered;

    public ZipperEntity(EntityType<? extends ZipperEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static ZipperEntity create(ServerLevel level, ServerPlayer owner, ZipperKind kind, Vec3 pos, Vec3 normal,
                                      float angleDeg, float length, float width) {
        ZipperEntity z = new ZipperEntity(ModEntities.ZIPPER.get(), level);
        z.setPos(pos.x, pos.y, pos.z);
        z.entityData.set(KIND, kind.ordinal());
        Vec3 n = normal.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : normal.normalize();
        z.entityData.set(NORMAL, new Vector3f((float) n.x, (float) n.y, (float) n.z));
        z.entityData.set(ANGLE, angleDeg);
        z.entityData.set(LENGTH, length);
        z.entityData.set(WIDTH, width);
        z.entityData.set(OWNER, Optional.of(owner.getUUID()));
        z.entityData.set(SEED, level.random.nextInt());
        z.entityData.set(ZIPPER_ID, ZipperNetwork.nextId());
        return z;
    }

    public static Vec3 normalOf(Direction d) {
        return new Vec3(d.getStepX(), d.getStepY(), d.getStepZ());
    }

    // ---------------------------------------------------------------- data

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder b) {
        b.define(KIND, 0);
        b.define(NORMAL, new Vector3f(0, 1, 0));
        b.define(ANGLE, 0f);
        b.define(LENGTH, 1.5f);
        b.define(WIDTH, 0.8f);
        b.define(OPEN, false);
        b.define(ZIPPER_ID, -1);
        b.define(OWNER, Optional.empty());
        b.define(ATTACHED, -1);
        b.define(ANCHOR, BlockPos.ZERO);
        b.define(SEED, 0);
        b.define(LINK_COUNT, 0);
    }

    public ZipperKind kind() { return ZipperKind.byId(entityData.get(KIND)); }
    public Vec3 normal() { Vector3f v = entityData.get(NORMAL); return new Vec3(v.x, v.y, v.z); }
    public void setNormal(Vec3 n) { Vec3 m = n.normalize(); entityData.set(NORMAL, new Vector3f((float) m.x, (float) m.y, (float) m.z)); }
    public float angle() { return entityData.get(ANGLE); }
    public float length() { return entityData.get(LENGTH); }
    public float width() { return entityData.get(WIDTH); }
    public boolean isOpen() { return entityData.get(OPEN); }
    public int zipperId() { return entityData.get(ZIPPER_ID); }
    @Nullable public UUID ownerId() { return entityData.get(OWNER).orElse(null); }
    public int attachedId() { return entityData.get(ATTACHED); }
    public BlockPos anchor() { return entityData.get(ANCHOR); }
    public int seed() { return entityData.get(SEED); }
    public int linkCount() { return entityData.get(LINK_COUNT); }
    public List<Integer> links() { return Collections.unmodifiableList(links); }
    public int life() { return life; }

    public ZipperEntity attachTo(Entity target) { entityData.set(ATTACHED, target.getId()); return this; }
    public ZipperEntity anchorAt(BlockPos pos) { entityData.set(ANCHOR, pos); return this; }
    public ZipperEntity life(int ticks) { this.life = ticks; return this; }
    public ZipperEntity effectDuration(int ticks) { this.effectDuration = ticks; return this; }
    /** Blocks that get separated while this zipper is open. */
    public ZipperEntity opensArea(List<BlockPos> area, int ticks) { this.openArea = area; this.separationTicks = ticks; return this; }

    public boolean isOwnedBy(Entity e) { return e.getUUID().equals(ownerId()); }

    public void addLink(int id) {
        if (id != zipperId() && !links.contains(id)) { links.add(id); entityData.set(LINK_COUNT, links.size()); }
    }

    public void removeLink(int id) {
        links.remove((Integer) id);
        entityData.set(LINK_COUNT, links.size());
    }

    // ---------------------------------------------------------------- open/close

    public void open() {
        if (isOpen() || !(level() instanceof ServerLevel sl)) return;
        entityData.set(OPEN, true);
        closingTimer = -1;
        ServerPlayer owner = ownerPlayer();
        if (!openArea.isEmpty()) {
            SeparationManager.get(sl).separate(sl, owner, openArea, getUUID(), separationTicks);
        }
        sl.playSound(null, getX(), getY(), getZ(), ModSounds.ZIPPER_OPEN.get(), SoundSource.PLAYERS, 1.0f, 0.9f + random.nextFloat() * 0.2f);
        burst(sl, 10);
    }

    public void close() {
        if (!(level() instanceof ServerLevel sl)) return;
        boolean wasOpen = isOpen();
        entityData.set(OPEN, false);
        SeparationManager.get(sl).restoreOwner(sl, getUUID());
        if (wasOpen) sl.playSound(null, getX(), getY(), getZ(), ModSounds.ZIPPER_CLOSE.get(), SoundSource.PLAYERS, 1.0f, 0.9f + random.nextFloat() * 0.2f);
        Entity target = attachedEntity();
        if (target instanceof LivingEntity le && (kind() == ZipperKind.MOB || kind() == ZipperKind.CHAIN)) {
            le.removeEffect(ModEffects.UNZIPPED);
            le.removeEffect(ModEffects.ZIPPER_BOUND);
        }
        if (!kind().reusable || life == 0) closingTimer = 14;
        burst(sl, 6);
    }

    /** Close then remove. */
    public void closeAndDiscard() {
        close();
        closingTimer = 14;
    }

    private void burst(ServerLevel sl, int n) {
        Vec3 d = direction();
        for (int i = 0; i < n; i++) {
            double t = (random.nextDouble() - 0.5) * length();
            sl.sendParticles(ModParticles.ZIPPER_SPARK.get(), getX() + d.x * t, getY() + d.y * t, getZ() + d.z * t, 1, 0.05, 0.05, 0.05, 0.05);
        }
        sl.sendParticles(ModParticles.ZIPPER_TOOTH.get(), getX(), getY(), getZ(), n / 3, 0.2, 0.2, 0.2, 0.08);
    }

    // ---------------------------------------------------------------- geometry

    /** Unit vector along the zipper line. */
    public Vec3 direction() {
        return directionOf(normal(), angle());
    }

    public static Vec3 directionOf(Vec3 n, float angleDeg) {
        Vec3 u = tangent(n);
        Vec3 v = n.cross(u).normalize();
        double a = Math.toRadians(angleDeg);
        return u.scale(Math.cos(a)).add(v.scale(Math.sin(a))).normalize();
    }

    /** Stable in-plane "horizontal" axis for any normal. */
    public static Vec3 tangent(Vec3 n) {
        Vec3 up = Math.abs(n.y) > 0.9 ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
        return up.cross(n).normalize();
    }

    // ---------------------------------------------------------------- tick

    @Override
    public void tick() {
        super.tick();
        prevOpenProgress = openProgress;
        prevAppear = appear;
        appear = Math.min(1f, appear + 0.12f);
        float target = isOpen() ? 1f : 0f;
        openProgress += Mth.clamp(target - openProgress, -0.1f, 0.1f);

        Entity attached = attachedEntity();
        if (attached != null && (kind() == ZipperKind.MOB || kind() == ZipperKind.BODY)) {
            setPos(attached.getX(), attached.getY(0.55), attached.getZ());
        }

        if (level().isClientSide) {
            clientParticles();
            return;
        }
        ServerLevel sl = (ServerLevel) level();
        if (!registered) { ZipperNetwork.register(this); registered = true; }

        if (closingTimer >= 0 && --closingTimer <= 0) { discard(); return; }
        if (life > 0 && --life == 0) { closeAndDiscard(); return; }
        if (tickCount % 20 == 0) {
            ServerPlayer owner = ownerPlayer();
            if (owner == null || !owner.isAlive() && kind() != ZipperKind.TRAP) { closeAndDiscard(); return; }
        }
        if (kind() == ZipperKind.BODY && closingTimer < 0 && !(attached instanceof LivingEntity body && body.hasEffect(ModEffects.ZIPPER_BODY))) {
            closeAndDiscard();
            return;
        }
        if ((kind() == ZipperKind.MOB || kind() == ZipperKind.CHAIN || kind() == ZipperKind.BODY) && (attached == null || !attached.isAlive())) {
            closeAndDiscard();
            return;
        }

        switch (kind()) {
            case MOB, CHAIN -> tickRestraint(attached);
            case TRAP -> tickTrap(sl);
            default -> {}
        }
        if (isOpen() && kind().portal && !links.isEmpty() && closingTimer < 0) tickPortal(sl);
    }

    private void tickRestraint(@Nullable Entity attached) {
        if (!(attached instanceof LivingEntity le) || !isOpen()) return;
        var effect = kind() == ZipperKind.MOB ? ModEffects.UNZIPPED : ModEffects.ZIPPER_BOUND;
        if (!le.hasEffect(effect)) le.addEffect(new MobEffectInstance(effect, Math.max(effectDuration, 20), 0, false, true));
        if (kind() == ZipperKind.CHAIN && tickCount % 4 == 0) {
            // pull gently toward the anchor so the chain stays taut
            Vec3 a = Vec3.atCenterOf(anchor());
            Vec3 toAnchor = a.subtract(le.position());
            if (toAnchor.length() > 2.5) le.push(toAnchor.x * 0.02, 0, toAnchor.z * 0.02);
        }
    }

    private void tickTrap(ServerLevel sl) {
        if (isOpen()) return;
        double r = SFConfig.TRAP_RADIUS.get();
        UUID owner = ownerId();
        List<LivingEntity> hits = sl.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(r, 1.0, r), e ->
                e.isAlive() && !e.getUUID().equals(owner) && !(e instanceof DecoyEntity) && !e.isSpectator()
                        && (!(e instanceof Player) || SFConfig.TRAP_AFFECTS_PLAYERS.get())
                        && e.distanceToSqr(position()) <= (r + e.getBbWidth()) * (r + e.getBbWidth()) + 1.0);
        if (hits.isEmpty()) return;
        LivingEntity victim = hits.getFirst();
        entityData.set(OPEN, true);
        entityData.set(ATTACHED, victim.getId());
        victim.teleportTo(getX(), victim.getY(), getZ());
        victim.addEffect(new MobEffectInstance(ModEffects.ZIPPER_BOUND, effectDuration, 0, false, true));
        victim.addEffect(new MobEffectInstance(ModEffects.UNZIPPED, effectDuration, 0, false, true));
        sl.playSound(null, getX(), getY(), getZ(), ModSounds.ZIPPER_SNAP.get(), SoundSource.PLAYERS, 1.2f, 1.0f);
        sl.sendParticles(ModParticles.PUNCH_IMPACT.get(), getX(), getY() + 0.1, getZ(), 1, 0, 0, 0, 0);
        sl.sendParticles(ModParticles.ZIPPER_TOOTH.get(), getX(), getY() + 0.2, getZ(), 14, 0.5, 0.2, 0.5, 0.15);
        ServerPlayer ownerPlayer = ownerPlayer();
        if (ownerPlayer != null) {
            ownerPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.stickyfingers.trap_triggered", victim.getDisplayName()), true);
        }
        life = effectDuration + 10;
    }

    private void tickPortal(ServerLevel sl) {
        double reach = Math.max(0.6, Math.max(width(), length()) * 0.5);
        AABB box = new AABB(position(), position()).inflate(reach * 0.8, Math.max(reach, 1.0), reach * 0.8);
        if (kind() == ZipperKind.FLOOR || kind() == ZipperKind.AERIAL) box = box.expandTowards(0, 1.5, 0);
        long now = sl.getGameTime();
        int cd = SFConfig.PORTAL_COOLDOWN.get();
        for (Entity e : sl.getEntitiesOfClass(Entity.class, box, en -> en instanceof LivingEntity && en.isAlive() && !en.isSpectator())) {
            if (now - e.getPersistentData().getLong("sf_portal") < cd) continue;
            // Only the owner may freely travel; other entities only when it is the owner's door network
            if (!isOwnedBy(e) && !(e instanceof LivingEntity && kind() == ZipperKind.TRAVEL)) continue;
            ZipperEntity dest = ZipperNetwork.pickDestination(this, e);
            if (dest == null) continue;
            e.getPersistentData().putLong("sf_portal", now);
            ZipperNetwork.transport(e, this, dest);
        }
    }

    private void clientParticles() {
        if (openProgress <= 0.3f || random.nextInt(3) != 0) return;
        Vec3 d = direction();
        double t = (random.nextDouble() - 0.5) * length();
        Vec3 n = normal();
        level().addParticle(com.stickyfingers.registry.ModParticles.VOID_MOTE.get(),
                getX() + d.x * t + n.x * 0.1, getY() + d.y * t + n.y * 0.1, getZ() + d.z * t + n.z * 0.1,
                n.x * 0.02, n.y * 0.02 + 0.01, n.z * 0.02);
    }

    // ---------------------------------------------------------------- helpers

    @Nullable
    public Entity attachedEntity() {
        int id = attachedId();
        return id < 0 ? null : level().getEntity(id);
    }

    @Nullable
    public ServerPlayer ownerPlayer() {
        UUID id = ownerId();
        if (id == null || level().getServer() == null) return null;
        return level().getServer().getPlayerList().getPlayer(id);
    }

    public float getOpenProgress(float partial) { return Mth.lerp(partial, prevOpenProgress, openProgress); }
    public float getAppear(float partial) { return Mth.lerp(partial, prevAppear, appear); }

    @Override
    public void remove(RemovalReason reason) {
        if (level() instanceof ServerLevel sl) {
            SeparationManager.get(sl).restoreOwner(sl, getUUID());
            ZipperNetwork.unregister(this);
        }
        super.remove(reason);
    }

    @Override
    public boolean shouldBeSaved() { return false; }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean shouldRenderAtSqrDistance(double dist) { return dist < 128 * 128; }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}
}
