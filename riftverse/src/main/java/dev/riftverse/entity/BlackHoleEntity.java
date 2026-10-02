package dev.riftverse.entity;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.client.ClientHooksProxy;
import dev.riftverse.registry.RvItems;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.transit.Destination;
import dev.riftverse.transit.TransitKind;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.transit.UniverseTravel;
import dev.riftverse.util.Hash;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * A gravitational singularity. Bends light (client shader), drags everything nearby into an accretion spiral, tears
 * loose terrain, and swallows travellers into a wormhole that spits them out in another universe.
 */
public class BlackHoleEntity extends Entity {
    private static final EntityDataAccessor<Float> HORIZON = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> NATURAL = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> NEXUS_CORE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CAPTURES = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STYLE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);

    public static final int STYLE_CLASSIC = 0;
    public static final int STYLE_NEXUS = 1;
    public static final int STYLE_GRAVITY = 2;
    public static final int STYLE_WARDEN = 3;

    @Nullable
    private UUID ownerId;
    private boolean soundStarted;

    public BlackHoleEntity(EntityType<? extends BlackHoleEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(HORIZON, 3.0f);
        builder.define(NATURAL, false);
        builder.define(NEXUS_CORE, false);
        builder.define(CAPTURES, true);
        builder.define(LIFETIME, -1);
        builder.define(STYLE, STYLE_CLASSIC);
    }

    public float horizonRadius() {
        return entityData.get(HORIZON);
    }

    public void setHorizonRadius(float r) {
        entityData.set(HORIZON, Math.max(0.3f, r));
    }

    public void setNatural(boolean natural) {
        entityData.set(NATURAL, natural);
    }

    public boolean isNexusCore() {
        return entityData.get(NEXUS_CORE);
    }

    public void setNexusCore(boolean core) {
        entityData.set(NEXUS_CORE, core);
        if (core) entityData.set(STYLE, STYLE_NEXUS);
    }

    public void setCaptures(boolean captures) {
        entityData.set(CAPTURES, captures);
    }

    /** Cosmetic black holes (End Protocol previews) pull, hurt and break nothing. Not saved: they never outlive their sequence. */
    private boolean harmless;

    public void setHarmless(boolean harmless) {
        this.harmless = harmless;
    }

    public boolean captures() {
        return entityData.get(CAPTURES);
    }

    public void setLifetime(int ticks) {
        entityData.set(LIFETIME, ticks);
    }

    public int lifetime() {
        return entityData.get(LIFETIME);
    }

    public int style() {
        return entityData.get(STYLE);
    }

    public void setStyle(int style) {
        entityData.set(STYLE, style);
    }

    public void setOwner(@Nullable Entity owner) {
        this.ownerId = owner == null ? null : owner.getUUID();
    }

    /** Radius within which gravity is felt. */
    public float influenceRadius() {
        return horizonRadius() * (isNexusCore() ? 6f : 14f);
    }

    /** Radius at which a traveller loses control and the cinematic begins. */
    public float captureRadius() {
        return horizonRadius() * (isNexusCore() ? 2.5f : 5.5f);
    }

    /** Opening animation 0..1 so spawned singularities bloom into existence. */
    public float bloom(float partial) {
        return Math.min(1f, (tickCount + partial) / 40f);
    }

    /** Orientation of the accretion disk, stable for this entity. */
    public Vector3f diskNormal() {
        long h = Hash.of(getUUID().getMostSignificantBits(), getUUID().getLeastSignificantBits());
        float tilt = 0.18f + Hash.unit(h) * 0.42f;
        float azimuth = Hash.unit(Hash.mix(h)) * (float) Math.PI * 2f;
        if (isNexusCore()) tilt = 0.08f;
        return new Vector3f((float) (Math.sin(tilt) * Math.cos(azimuth)), (float) Math.cos(tilt), (float) (Math.sin(tilt) * Math.sin(azimuth)));
    }

    public Vec3 center() {
        return position();
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            if (!soundStarted) {
                soundStarted = true;
                ClientHooksProxy.startBlackHoleSound(this);
            }
            ClientHooksProxy.blackHoleClientTick(this);
            return;
        }
        int life = lifetime();
        if (life > 0) {
            setLifetime(life - 1);
            if (life - 1 == 0) {
                collapse();
                return;
            }
        }
        ServerLevel level = (ServerLevel) level();
        if (dev.riftverse.wormhole.WormholeManager.isMouth(this)) {
            dev.riftverse.wormhole.WormholeManager.mouthTick(this);
            return;
        }
        if (harmless) return;
        float horizon = horizonRadius() * bloom(0);
        Vec3 c = center();
        float influence = influenceRadius();
        List<Entity> nearby = level.getEntities(this, new AABB(c, c).inflate(influence), e -> !(e instanceof BlackHoleEntity) && !e.isSpectator());
        for (Entity e : nearby) {
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > influence || d < 1e-3) continue;
            if (e instanceof ServerPlayer player) {
                if (captures() && d < captureRadius() && !TransitManager.inTransit(player) && !player.isCreative() || (captures() && d < captureRadius() * 0.6 && !TransitManager.inTransit(player))) {
                    Destination dest = Destination.random();
                    int a = style() == STYLE_NEXUS ? 0xFFD27A : 0xFF8A3A;
                    TransitManager.begin(player, TransitKind.BLACK_HOLE, dest, c, a, 0x7A5CFF, true);
                } else if (!captures() && d < horizon * 1.6 && tickCount % 10 == 0) {
                    player.hurt(damage(), 3.0f);
                }
                continue;
            }
            if (d < horizon * 1.15) {
                consume(level, e);
                continue;
            }
            Vec3 pull = pullAt(to, d, horizon);
            e.setDeltaMovement(e.getDeltaMovement().scale(0.94).add(pull));
            e.hurtMarked = true;
            e.fallDistance = 0;
            if (e instanceof LivingEntity living && d < horizon * 2.2 && tickCount % 10 == 0) living.hurt(damage(), 4.0f);
        }
        if (canRipBlocks(level) && tickCount % 2 == 0) ripBlocks(level, c, horizon);
        if (tickCount % 80 == 0) level.playSound(null, c.x, c.y, c.z, RvSounds.BLACK_HOLE_AMBIENT.get(), SoundSource.AMBIENT, 2.5f, 0.8f + random.nextFloat() * 0.1f);
    }

    /** Gravitational acceleration toward the centre plus a swirl around the disk axis. */
    public Vec3 pullAt(Vec3 toCenter, double dist, float horizon) {
        double strength = Math.min(0.55, 0.9 * (horizon * horizon) / Math.max(dist * dist, horizon * horizon * 0.8));
        if (isNexusCore()) strength *= 0.25;
        Vec3 dir = toCenter.scale(1.0 / dist);
        Vector3f n = diskNormal();
        Vec3 axis = new Vec3(n.x(), n.y(), n.z());
        Vec3 swirl = dir.cross(axis);
        if (swirl.lengthSqr() > 1e-6) swirl = swirl.normalize();
        return dir.scale(strength).add(swirl.scale(strength * 0.45));
    }

    private DamageSource damage() {
        Entity owner = ownerId == null ? null : ((ServerLevel) level()).getEntity(ownerId);
        return owner != null ? damageSources().indirectMagic(this, owner) : damageSources().magic();
    }

    private boolean canRipBlocks(ServerLevel level) {
        if (isNexusCore() || style() == STYLE_GRAVITY) return false;
        if (!RiftverseConfig.BLACK_HOLES_BREAK_BLOCKS.get()) return false;
        if (UniverseTravel.isRiftverseDimension(level)) return true;
        return RiftverseConfig.BLACK_HOLES_BREAK_BLOCKS_IN_OVERWORLD.get();
    }

    private void ripBlocks(ServerLevel level, Vec3 c, float horizon) {
        int attempts = horizon > 2 ? 3 : 1;
        for (int i = 0; i < attempts; i++) {
            Vec3 dir = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
            if (dir.lengthSqr() < 1e-4) continue;
            Vec3 at = c.add(dir.normalize().scale(horizon * (1.4 + random.nextDouble() * 3.2)));
            BlockPos p = BlockPos.containing(at);
            BlockState s = level.getBlockState(p);
            if (s.isAir() || s.hasBlockEntity() || !s.getFluidState().isEmpty()) continue;
            float hardness = s.getDestroySpeed(level, p);
            if (hardness < 0 || hardness > 50) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(level, p, s);
            fb.dropItem = false;
            fb.setNoGravity(true);
            fb.time = 1;
            fb.setDeltaMovement(c.subtract(Vec3.atCenterOf(p)).normalize().scale(0.25));
        }
    }

    private void consume(ServerLevel level, Entity e) {
        Vec3 c = center();
        level.sendParticles(RvParticles.SPARK.get().with(0xFFB060, 0.6f, 16), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 12, 0.2, 0.2, 0.2, 0.2);
        if ((e instanceof ItemEntity || e instanceof FallingBlockEntity) && random.nextFloat() < 0.08f) {
            Vector3f n = diskNormal();
            double side = random.nextBoolean() ? 1 : -1;
            ItemEntity frag = new ItemEntity(level, c.x + n.x() * horizonRadius() * 2.2 * side, c.y + n.y() * horizonRadius() * 2.2 * side,
                    c.z + n.z() * horizonRadius() * 2.2 * side, new ItemStack(RvItems.SINGULARITY_FRAGMENT.get()));
            frag.setDeltaMovement(n.x() * 1.2 * side, n.y() * 1.2 * side, n.z() * 1.2 * side);
            frag.setPickUpDelay(20);
            frag.setNoGravity(false);
            level.addFreshEntity(frag);
            level.sendParticles(RvParticles.RING.get().with(0xB0A0FF, 2.0f, 14), frag.getX(), frag.getY(), frag.getZ(), 1, 0, 0, 0, 0);
        }
        if (e instanceof LivingEntity living && !(living instanceof net.minecraft.world.entity.player.Player) && living.getMaxHealth() <= 120f
                && dev.riftverse.multiverse.MigrationManager.swallow(living, 0xFF8A3A)) {
            return;
        }
        if (e instanceof LivingEntity living) {
            if (living.getMaxHealth() > 120f) {
                if (tickCount % 10 == 0) living.hurt(damage(), 12f);
                return;
            }
            living.hurt(damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            if (living.isAlive()) living.discard();
        } else {
            e.discard();
        }
    }

    /** End of life for transient singularities: an inward crush followed by a shockwave. */
    public void collapse() {
        if (!(level() instanceof ServerLevel level)) return;
        Vec3 c = center();
        float r = horizonRadius();
        level.sendParticles(RvParticles.RING.get().with(0xFFFFFF, r * 4f, 20), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.RING.get().with(0x9A7CFF, r * 7f, 28), c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(RvParticles.SPARK.get().with(0xFFD0A0, 0.9f, 30), c.x, c.y, c.z, 120, 0.4, 0.4, 0.4, 0.9);
        level.playSound(null, c.x, c.y, c.z, RvSounds.BLACK_HOLE_COLLAPSE.get(), SoundSource.HOSTILE, 3.0f, 1.0f);
        for (Entity e : level.getEntities(this, new AABB(c, c).inflate(r * 6), e -> e instanceof LivingEntity)) {
            Vec3 out = e.position().subtract(c);
            double d = Math.max(0.5, out.length());
            e.setDeltaMovement(e.getDeltaMovement().add(out.normalize().scale(2.2 / d * r)).add(0, 0.5, 0));
            e.hurtMarked = true;
            if (!(e instanceof Player p && p.getUUID().equals(ownerId))) e.hurt(damage(), 10f * r / Math.max(1f, (float) d * 0.5f));
        }
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(c) < 64 * 64) {
                float k = (float) (1.0 - Math.sqrt(p.distanceToSqr(c)) / 64.0);
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p, new dev.riftverse.network.Payloads.Shake(1.2f * k, 25, 0.6f * k, 0xD0C0FF));
            }
        }
        discard();
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distSq) {
        return distSq < 600 * 600;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        setHorizonRadius(tag.getFloat("horizon"));
        setNatural(tag.getBoolean("natural"));
        entityData.set(NEXUS_CORE, tag.getBoolean("nexusCore"));
        setCaptures(!tag.contains("captures") || tag.getBoolean("captures"));
        setLifetime(tag.contains("lifetime") ? tag.getInt("lifetime") : -1);
        setStyle(tag.getInt("style"));
        if (tag.hasUUID("owner")) ownerId = tag.getUUID("owner");
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putFloat("horizon", horizonRadius());
        tag.putBoolean("natural", entityData.get(NATURAL));
        tag.putBoolean("nexusCore", isNexusCore());
        tag.putBoolean("captures", captures());
        tag.putInt("lifetime", lifetime());
        tag.putInt("style", style());
        if (ownerId != null) tag.putUUID("owner", ownerId);
    }
}
