package dev.infinitygauntlet.nexus;

import dev.infinitygauntlet.registry.ModBlockEntities;
import dev.infinitygauntlet.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Synced state of a Consciousness Nexus. The server-side {@link PossessionManager} owns the actual control logic;
 * this block entity mirrors what clients need to draw the station: links, stability, the screen state machine and
 * ability cooldowns.
 */
public class NexusBlockEntity extends BlockEntity implements GeoBlockEntity {
    public static final byte STANDBY = 0;
    public static final byte CONNECTING = 1;
    public static final byte LIVE = 2;
    public static final byte DISCONNECTING = 3;
    public static final int CONNECT_TICKS = 30;
    public static final int DISCONNECT_TICKS = 44;

    /** Loaded nexuses on the client, for feeds and link visuals. */
    public static final Set<NexusBlockEntity> CLIENT_LOADED = ConcurrentHashMap.newKeySet();

    public record Link(int entityId, long started, float stability) {
    }

    private static final RawAnimation STANDBY_ANIM = RawAnimation.begin().thenLoop("animation.consciousness_nexus.standby");
    private static final RawAnimation ACTIVE_ANIM = RawAnimation.begin().thenLoop("animation.consciousness_nexus.active");
    private static final RawAnimation UNSTABLE_ANIM = RawAnimation.begin().thenLoop("animation.consciousness_nexus.unstable");
    private static final RawAnimation POWER_ON = RawAnimation.begin().thenPlay("animation.consciousness_nexus.power_on")
            .thenLoop("animation.consciousness_nexus.active");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID owner;
    private String ownerName = "";
    private byte state = STANDBY;
    private long stateStart;
    private List<Link> links = new ArrayList<>();
    private int active;
    private int controllerId = -1;
    private int fadingEntityId = -1;
    private long blinkReady, overrideReady, extractReady, overrideUntil;
    private String lastSignal = "";

    public NexusBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CONSCIOUSNESS_NEXUS.get(), pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, NexusBlockEntity nexus) {
        if (level.isClientSide) {
            return;
        }
        long age = level.getGameTime() - nexus.stateStart;
        if (nexus.state == CONNECTING && age >= CONNECT_TICKS) {
            nexus.setState(LIVE);
        } else if (nexus.state == DISCONNECTING && age >= DISCONNECT_TICKS) {
            nexus.fadingEntityId = -1;
            nexus.setState(nexus.links.isEmpty() ? STANDBY : LIVE);
        }
        if (nexus.state != STANDBY && level.getGameTime() % 80 == 0) {
            level.playSound(null, pos, ModSounds.NEXUS_HUM.get(), SoundSource.BLOCKS, 0.6f, 1f);
        }
    }

    // ------------------------------------------------------------------ state changes (server)

    public void bindOwner(ServerPlayer player) {
        owner = player.getUUID();
        ownerName = player.getGameProfile().getName();
        player.getData(dev.infinitygauntlet.registry.ModAttachments.POWER_DATA).nexus =
                net.minecraft.core.GlobalPos.of(player.level().dimension(), worldPosition);
        player.displayClientMessage(Component.translatable("message.infinitygauntlet.nexus.bound"), true);
        sync();
    }

    void setState(byte newState) {
        state = newState;
        stateStart = level == null ? 0 : level.getGameTime();
        sync();
    }

    void setLinks(List<Link> newLinks, int newActive) {
        links = new ArrayList<>(newLinks);
        active = links.isEmpty() ? 0 : Math.floorMod(newActive, links.size());
        sync();
    }

    void setController(int entityId) {
        controllerId = entityId;
        sync();
    }

    void beginDisconnect(int entityId, String signal) {
        fadingEntityId = entityId;
        lastSignal = signal;
        setState(DISCONNECTING);
    }

    void setCooldowns(long blink, long override, long extract, long overrideActiveUntil) {
        blinkReady = blink;
        overrideReady = override;
        extractReady = extract;
        overrideUntil = overrideActiveUntil;
        sync();
    }

    public void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------ accessors

    @Nullable
    public UUID owner() {
        return owner;
    }

    public String ownerName() {
        return ownerName;
    }

    public byte state() {
        return state;
    }

    /** Ticks since the current screen state began (client-safe). */
    public float stateAge(float pt) {
        return level == null ? 0 : level.getGameTime() - stateStart + pt;
    }

    public List<Link> links() {
        return Collections.unmodifiableList(links);
    }

    public int activeIndex() {
        return active;
    }

    @Nullable
    public Link activeLink() {
        return links.isEmpty() ? null : links.get(Math.min(active, links.size() - 1));
    }

    public int controllerId() {
        return controllerId;
    }

    public int fadingEntityId() {
        return fadingEntityId;
    }

    public String lastSignal() {
        return lastSignal;
    }

    public long blinkReady() {
        return blinkReady;
    }

    public long overrideReady() {
        return overrideReady;
    }

    public long extractReady() {
        return extractReady;
    }

    public long overrideUntil() {
        return overrideUntil;
    }

    public Direction facing() {
        return getBlockState().getValue(ConsciousnessNexusBlock.FACING);
    }

    /** World position of the holographic screen's centre. */
    public Vec3 screenCenter() {
        Direction f = facing();
        return Vec3.atBottomCenterOf(worldPosition).add(0, 54 / 16.0, 0).add(f.getStepX() * -6 / 16.0, 0, f.getStepZ() * -6 / 16.0);
    }

    /** Where the operator stands, and the yaw that faces the screen. */
    public Vec3 consolePosition() {
        Direction f = facing();
        return Vec3.atBottomCenterOf(worldPosition).add(f.getStepX() * 2.6, 0, f.getStepZ() * 2.6);
    }

    /** Operator view pitch at the console: looking up at the screen. */
    public static final float CONSOLE_PITCH = -27f;

    public float consoleYaw() {
        return facing().getOpposite().toYRot();
    }

    @Nullable
    public Entity linkedEntity(int index) {
        if (level == null || index < 0 || index >= links.size()) {
            return null;
        }
        return level.getEntity(links.get(index).entityId());
    }

    // ------------------------------------------------------------------ persistence + sync

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (owner != null) {
            tag.putUUID("owner", owner);
            tag.putString("ownerName", ownerName);
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
        ownerName = tag.getString("ownerName");
        if (tag.contains("state")) {
            readSync(tag);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        tag.putByte("state", state);
        tag.putLong("stateStart", stateStart);
        ListTag list = new ListTag();
        for (Link l : links) {
            CompoundTag lt = new CompoundTag();
            lt.putInt("id", l.entityId());
            lt.putLong("started", l.started());
            lt.putFloat("stability", l.stability());
            list.add(lt);
        }
        tag.put("links", list);
        tag.putInt("active", active);
        tag.putInt("controller", controllerId);
        tag.putInt("fading", fadingEntityId);
        tag.putString("signal", lastSignal);
        tag.putLongArray("cooldowns", new long[]{blinkReady, overrideReady, extractReady, overrideUntil});
        return tag;
    }

    private void readSync(CompoundTag tag) {
        state = tag.getByte("state");
        stateStart = tag.getLong("stateStart");
        List<Link> list = new ArrayList<>();
        for (Tag t : tag.getList("links", Tag.TAG_COMPOUND)) {
            CompoundTag lt = (CompoundTag) t;
            list.add(new Link(lt.getInt("id"), lt.getLong("started"), lt.getFloat("stability")));
        }
        links = list;
        active = tag.getInt("active");
        controllerId = tag.getInt("controller");
        fadingEntityId = tag.getInt("fading");
        lastSignal = tag.getString("signal");
        long[] cd = tag.getLongArray("cooldowns");
        if (cd.length == 4) {
            blinkReady = cd[0];
            overrideReady = cd[1];
            extractReady = cd[2];
            overrideUntil = cd[3];
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && level.isClientSide) {
            CLIENT_LOADED.add(this);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_LOADED.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CLIENT_LOADED.remove(this);
    }

    // ------------------------------------------------------------------ GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 8, s -> {
            if (state == STANDBY) {
                return s.setAndContinue(STANDBY_ANIM);
            }
            if (state == CONNECTING) {
                return s.setAndContinue(POWER_ON);
            }
            Link link = activeLink();
            if (state == DISCONNECTING || link != null && link.stability() < 0.3f) {
                return s.setAndContinue(UNSTABLE_ANIM);
            }
            return s.setAndContinue(ACTIVE_ANIM);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
