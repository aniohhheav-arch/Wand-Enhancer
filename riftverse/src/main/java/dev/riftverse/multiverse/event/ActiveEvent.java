package dev.riftverse.multiverse.event;

import dev.riftverse.universe.UniverseId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** One running event instance and the world objects it owns. */
public final class ActiveEvent {
    public final int id;
    public final EventType type;
    public final ServerLevel level;
    public Vec3 center;
    /** Where the announcement cinematic should look (the boss, the singularity...). */
    public Vec3 focus;
    @Nullable
    public final UUID instigator;
    public final boolean natural;
    @Nullable
    public final UniverseId universe;
    public int age;
    public int duration;
    public boolean finished;
    public final CompoundTag data = new CompoundTag();
    /** Entities spawned by this event (bosses, invaders, visitors). */
    public final List<UUID> entities = new ArrayList<>();
    /** Temporary rifts opened by this event. */
    public final List<BlockPos> rifts = new ArrayList<>();

    ActiveEvent(int id, EventType type, ServerLevel level, Vec3 center, @Nullable UUID instigator, boolean natural, @Nullable UniverseId universe) {
        this.id = id;
        this.type = type;
        this.level = level;
        this.center = center;
        this.focus = center;
        this.instigator = instigator;
        this.natural = natural;
        this.universe = universe;
        this.duration = type.durationTicks;
    }

    public int remaining() {
        return Math.max(0, duration - age);
    }

    public String describe() {
        return "#" + id + " " + type.id + " @ " + (int) center.x + ", " + (int) center.y + ", " + (int) center.z + " in " + level.dimension().location()
                + " — " + (remaining() / 20) + "s left" + (natural ? " (natural)" : "");
    }
}
