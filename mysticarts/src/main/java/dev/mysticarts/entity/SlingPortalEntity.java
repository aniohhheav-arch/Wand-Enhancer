package dev.mysticarts.entity;

import dev.mysticarts.fx.Fx;
import dev.mysticarts.power.Aim;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A circular sling-ring portal. Its orientation comes from the entity's yaw/pitch (pitch -90 = a floor portal facing up).
 * Anything that crosses the disc is sent to the stored exit, in this or another dimension, with its momentum turned to
 * leave the exit face. Redirect portals instead turn projectiles back on their shooters.
 */
public class SlingPortalEntity extends Entity {
    public static final int KIND_SLING = 0;
    public static final int KIND_SPACE = 1;
    public static final int KIND_TRAP = 2;
    public static final int KIND_REDIRECT = 3;
    public static final int KIND_RIFT = 4;

    private static final EntityDataAccessor<Integer> KIND = SynchedEntityData.defineId(SlingPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> COLOR = SynchedEntityData.defineId(SlingPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> RADIUS = SynchedEntityData.defineId(SlingPortalEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(SlingPortalEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(SlingPortalEntity.class, EntityDataSerializers.INT);
    /** Colour glimpsed through the portal: the sky/biome tint of the destination. */
    private static final EntityDataAccessor<Integer> VIEW = SynchedEntityData.defineId(SlingPortalEntity.class, EntityDataSerializers.INT);

    public static final int OPEN_TICKS = 16;
    public static final int CLOSE_TICKS = 12;

    @Nullable private UUID owner;
    @Nullable private UUID partner;
    @Nullable private ResourceKey<Level> exitDim;
    @Nullable private Vec3 exitPos;
    private float exitYaw;
    private float exitPitch;
    private boolean closing;

    public SlingPortalEntity(EntityType<? extends SlingPortalEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(KIND, KIND_SLING);
        builder.define(COLOR, 0xFF9A2E);
        builder.define(RADIUS, 1.4f);
        builder.define(LIFETIME, 600);
        builder.define(AGE, 0);
        builder.define(VIEW, 0x88B8FF);
    }

    // ============================================================================================ creation

    /** Opens a portal at {@code center} facing along yaw/pitch. Exit is set separately with {@link #exit}. */
    public static SlingPortalEntity open(ServerLevel level, @Nullable Entity owner, int kind, Vec3 center, float yaw, float pitch, float radius, int color, int lifetime) {
        SlingPortalEntity p = new SlingPortalEntity(MaEntities.SLING_PORTAL.get(), level);
        p.entityData.set(KIND, kind);
        p.entityData.set(COLOR, color);
        p.entityData.set(RADIUS, radius);
        p.entityData.set(LIFETIME, lifetime);
        p.owner = owner == null ? null : owner.getUUID();
        p.moveTo(center.x, center.y, center.z, yaw, pitch);
        p.setYRot(yaw);
        p.setXRot(pitch);
        level.addFreshEntity(p);
        Fx.sound(level, center, MaSounds.PORTAL_OPEN.get(), 1.2f, kind == KIND_SPACE ? 1.3f : 1f);
        return p;
    }

    public SlingPortalEntity exit(ServerLevel level, Vec3 pos, float yaw, float pitch) {
        this.exitDim = level.dimension();
        this.exitPos = pos;
        this.exitYaw = yaw;
        this.exitPitch = pitch;
        this.entityData.set(VIEW, viewColor(level, pos));
        return this;
    }

    /** Opens a linked two-way pair. Returns {a, b}. */
    public static SlingPortalEntity[] pair(ServerLevel levelA, Entity owner, int kind, Vec3 a, float yawA, ServerLevel levelB, Vec3 b, float yawB,
                                           float radius, int color, int lifetime) {
        SlingPortalEntity pa = open(levelA, owner, kind, a, yawA, 0, radius, color, lifetime);
        SlingPortalEntity pb = open(levelB, owner, kind, b, yawB, 0, radius, color, lifetime);
        pa.partner = pb.getUUID();
        pb.partner = pa.getUUID();
        pa.exit(levelB, b, yawB, 0);
        pb.exit(levelA, a, yawA, 0);
        return new SlingPortalEntity[] {pa, pb};
    }

    private static int viewColor(ServerLevel level, Vec3 pos) {
        var biome = level.getBiome(net.minecraft.core.BlockPos.containing(pos)).value();
        if (level.dimension() == Level.NETHER) return 0xC0402A;
        if (level.dimension() == Level.END) return 0x2A1E40;
        return biome.getSkyColor();
    }

    // ============================================================================================ accessors

    public int kind() {
        return entityData.get(KIND);
    }

    public int color() {
        return entityData.get(COLOR);
    }

    public float radius() {
        return entityData.get(RADIUS);
    }

    public int viewColor() {
        return entityData.get(VIEW);
    }

    public int lifetime() {
        return entityData.get(LIFETIME);
    }

    public int age() {
        return entityData.get(AGE);
    }

    @Nullable
    public UUID owner() {
        return owner;
    }

    public Vec3 normal() {
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    public Vec3 up() {
        Vec3 n = normal();
        Vec3 ref = Math.abs(n.y) > 0.9 ? Vec3.directionFromRotation(0, getYRot()) : new Vec3(0, 1, 0);
        Vec3 right = ref.cross(n).normalize();
        return n.cross(right).normalize();
    }

    public Vec3 right() {
        return up().cross(normal()).normalize();
    }

    /** 0..1 opening / closing envelope for renderers. */
    public float openness(float partial) {
        float age = age() + partial;
        float open = Mth.clamp(age / OPEN_TICKS, 0f, 1f);
        float close = Mth.clamp((lifetime() - age) / CLOSE_TICKS, 0f, 1f);
        float t = Math.min(open, close);
        return t * t * (3f - 2f * t);
    }

    public void close() {
        if (closing) return;
        closing = true;
        int age = age();
        entityData.set(LIFETIME, Math.min(lifetime(), age + CLOSE_TICKS));
        if (level() instanceof ServerLevel server) Fx.sound(server, position(), MaSounds.PORTAL_CLOSE.get(), 1f, 1f);
        if (partner != null && level().getServer() != null && exitDim != null) {
            ServerLevel other = level().getServer().getLevel(exitDim);
            if (other != null && other.getEntity(partner) instanceof SlingPortalEntity p) p.close();
        }
    }

    // ============================================================================================ ticking

    @Override
    public void tick() {
        super.tick();
        int age = age() + 1;
        if (!level().isClientSide) entityData.set(AGE, age);
        float open = openness(0);
        if (level().isClientSide) {
            clientParticles(open);
            return;
        }
        if (age >= lifetime()) {
            discard();
            return;
        }
        if (age == lifetime() - CLOSE_TICKS) Fx.sound((ServerLevel) level(), position(), MaSounds.PORTAL_CLOSE.get(), 1f, 1f);
        if (open < 0.6f) return;
        float r = radius() * open;
        List<Entity> list = level().getEntities(this, new AABB(position(), position()).inflate(r + 1.5),
                e -> !(e instanceof SlingPortalEntity) && !(e instanceof SpellFieldEntity) && !e.isPassenger() && !e.isSpectator());
        for (Entity e : list) tryCross(e, r);
    }

    private void clientParticles(float open) {
        if (open <= 0.05f) return;
        Vec3 n = normal(), u = up(), rt = right();
        float r = radius() * open;
        int count = kind() == KIND_RIFT ? 6 : 3;
        for (int i = 0; i < count; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            Vec3 rim = position().add(u.scale(Math.sin(a) * r)).add(rt.scale(Math.cos(a) * r));
            // sparks fly off tangentially, like a grinder
            Vec3 tangent = u.scale(Math.cos(a)).subtract(rt.scale(Math.sin(a)));
            Vec3 v = tangent.scale(0.12 + random.nextDouble() * 0.12).add(n.scale((random.nextDouble() - 0.5) * 0.06)).add(0, -0.02, 0);
            level().addParticle(MaParticles.SPARK.get().with(color(), 0.22f, 16), rim.x, rim.y, rim.z, v.x, v.y, v.z);
        }
    }

    private void tryCross(Entity e, float r) {
        if (exitPos == null || exitDim == null) {
            if (kind() == KIND_REDIRECT && e instanceof Projectile p) redirect(p);
            return;
        }
        if (kind() == KIND_REDIRECT) {
            if (e instanceof Projectile p) redirect(p);
            return;
        }
        long now = level().getGameTime();
        CompoundTag pd = e.getPersistentData();
        if (now - pd.getLong("mysticarts:portal_cd") < 15 && pd.getLong("mysticarts:portal_cd") <= now) return;
        Vec3 n = normal();
        Vec3 c = e.getBoundingBox().getCenter();
        Vec3 rel = c.subtract(position());
        double dn = rel.dot(n);
        double reach = Math.max(0.4, e.getBbWidth() * 0.6) + e.getDeltaMovement().length();
        if (Math.abs(dn) > reach) return;
        Vec3 inPlane = rel.subtract(n.scale(dn));
        if (inPlane.length() > r * 0.92) return;
        // floor portals swallow anything standing over them; wall portals need the entity moving into the disc
        boolean floor = Math.abs(n.y) > 0.9;
        Vec3 v = e.getDeltaMovement();
        if (!floor && v.lengthSqr() < 1e-4 && !(e instanceof Player)) return;

        ServerLevel target = level().getServer().getLevel(exitDim);
        if (target == null) return;
        Vec3 exitN = Vec3.directionFromRotation(exitPitch, exitYaw);
        double speed = Math.max(v.length(), e instanceof Projectile ? 0.5 : 0.25);
        Vec3 out = exitN.scale(speed);
        Vec3 feet;
        if (Math.abs(exitN.y) > 0.9) {
            feet = exitN.y > 0 ? exitPos.add(0, 0.1, 0) : exitPos.add(0, -e.getBbHeight() - 0.1, 0);
        } else {
            feet = exitPos.add(exitN.scale(Math.max(0.7, e.getBbWidth() * 0.5 + 0.35))).subtract(0, e.getBbHeight() * 0.5, 0);
            if (!(e instanceof Projectile)) {
                var safe = Aim.safeSpot(target, e, feet, 2);
                if (safe.isPresent()) feet = safe.get();
            }
        }
        float yaw = Math.abs(exitN.y) > 0.9 ? e.getYRot() : exitYaw;
        pd.putLong("mysticarts:portal_cd", now);
        Entity moved;
        if (e instanceof ServerPlayer player && target == level()) {
            player.connection.teleport(feet.x, feet.y, feet.z, yaw, player.getXRot());
            moved = player;
        } else {
            moved = Aim.teleport(e, target, feet, yaw, e.getXRot());
        }
        if (moved == null) return;
        moved.getPersistentData().putLong("mysticarts:portal_cd", target.getGameTime());
        moved.setDeltaMovement(out);
        moved.hurtMarked = true;
        moved.resetFallDistance();
        Fx.sound(target, feet, MaSounds.PORTAL_TRAVEL.get(), 0.8f, 1.2f);
        target.sendParticles(MaParticles.SPARK.get().with(color(), 0.4f, 14), feet.x, feet.y + 1, feet.z, 16, 0.3, 0.5, 0.3, 0.2);
        if (kind() == KIND_TRAP && !(moved instanceof Player)) close();
    }

    private void redirect(Projectile p) {
        Vec3 n = normal();
        Vec3 rel = p.position().subtract(position());
        if (Math.abs(rel.dot(n)) > 1.2 || rel.subtract(n.scale(rel.dot(n))).length() > radius()) return;
        if (p.getDeltaMovement().dot(n) >= 0) return;
        Entity shooter = p.getOwner();
        Vec3 back = shooter != null ? shooter.getEyePosition().subtract(p.position()).normalize() : n;
        p.setDeltaMovement(back.scale(Math.max(1.0, p.getDeltaMovement().length() * 1.4)));
        if (owner != null && level() instanceof ServerLevel s && s.getEntity(owner) != null) p.setOwner(s.getEntity(owner));
        p.hurtMarked = true;
        p.setPos(p.position().add(n.scale(0.8)));
        Fx.sound((ServerLevel) level(), p.position(), MaSounds.DEFLECT.get(), 0.8f, 1.3f);
    }

    // ============================================================================================ misc

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
        return distSq < 128 * 128;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        entityData.set(KIND, tag.getInt("kind"));
        entityData.set(COLOR, tag.getInt("color"));
        entityData.set(RADIUS, tag.getFloat("radius"));
        entityData.set(LIFETIME, tag.getInt("lifetime"));
        entityData.set(AGE, tag.getInt("age"));
        entityData.set(VIEW, tag.getInt("view"));
        if (tag.hasUUID("owner")) owner = tag.getUUID("owner");
        if (tag.hasUUID("partner")) partner = tag.getUUID("partner");
        if (tag.contains("exitDim")) {
            ResourceLocation id = ResourceLocation.tryParse(tag.getString("exitDim"));
            if (id != null) exitDim = ResourceKey.create(Registries.DIMENSION, id);
            exitPos = new Vec3(tag.getDouble("ex"), tag.getDouble("ey"), tag.getDouble("ez"));
            exitYaw = tag.getFloat("eyaw");
            exitPitch = tag.getFloat("epitch");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("kind", kind());
        tag.putInt("color", color());
        tag.putFloat("radius", radius());
        tag.putInt("lifetime", lifetime());
        tag.putInt("age", age());
        tag.putInt("view", viewColor());
        if (owner != null) tag.putUUID("owner", owner);
        if (partner != null) tag.putUUID("partner", partner);
        if (exitDim != null && exitPos != null) {
            tag.putString("exitDim", exitDim.location().toString());
            tag.putDouble("ex", exitPos.x);
            tag.putDouble("ey", exitPos.y);
            tag.putDouble("ez", exitPos.z);
            tag.putFloat("eyaw", exitYaw);
            tag.putFloat("epitch", exitPitch);
        }
    }
}
