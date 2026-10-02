package dev.riftverse.temporal;

import dev.riftverse.multiverse.CinematicType;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.Scheduler;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Time travel and its police. Every player carries a temporal coordinate (a year and a timeline). Jumping through time
 * builds paradox; too much paradox, or climbing too high into temporal airspace, summons the TSA — the Time and Space
 * Authority — who warn, then pursue, then drag the offender back down to the ground.
 */
public final class TemporalManager {
    public static final int PRESENT = 2026;
    private static final String TAG = "riftverse_temporal";
    public static final String AGENT_TAG = "riftverse_tsa";

    public enum Timeline { PRIME, PAST, FUTURE, ALTERNATE, FRACTURED }

    private static boolean tsaEnabled = true;
    public static int altitudeLimit = 300;
    private static final Map<UUID, Integer> WARNINGS = new HashMap<>();

    private TemporalManager() {}

    // ------------------------------------------------------------------ coordinates

    private static CompoundTag data(ServerPlayer p) {
        CompoundTag root = p.getPersistentData();
        if (!root.contains(TAG)) {
            CompoundTag t = new CompoundTag();
            t.putInt("year", PRESENT);
            t.putString("line", Timeline.PRIME.name());
            t.putInt("paradox", 0);
            root.put(TAG, t);
        }
        return root.getCompound(TAG);
    }

    public static int year(ServerPlayer p) {
        return data(p).getInt("year");
    }

    public static Timeline timeline(ServerPlayer p) {
        try {
            return Timeline.valueOf(data(p).getString("line"));
        } catch (IllegalArgumentException e) {
            return Timeline.PRIME;
        }
    }

    public static int paradox(ServerPlayer p) {
        return data(p).getInt("paradox");
    }

    public static void setYear(ServerPlayer p, int year) {
        data(p).putInt("year", year);
        data(p).putString("line", (year < PRESENT ? Timeline.PAST : year > PRESENT ? Timeline.FUTURE : Timeline.PRIME).name());
    }

    public static void setParadox(ServerPlayer p, int v) {
        data(p).putInt("paradox", Math.max(0, Math.min(100, v)));
    }

    public static String coordinate(ServerPlayer p) {
        return "T-" + timeline(p).name() + " / " + formatYear(year(p)) + " / X" + p.getBlockX() + " Y" + p.getBlockY() + " Z" + p.getBlockZ();
    }

    public static String formatYear(int y) {
        return y < 0 ? (-y) + " BCE" : y + " CE";
    }

    // ------------------------------------------------------------------ travel

    /** Sends a player to a year (and optionally another timeline), with the time-travel cinematic. */
    public static void travel(ServerPlayer p, int targetYear, Timeline line) {
        int from = year(p);
        int jump = Math.abs(targetYear - from);
        Timeline target = line != null ? line : targetYear < PRESENT ? Timeline.PAST : targetYear > PRESENT ? Timeline.FUTURE : Timeline.PRIME;
        RealityOps.cinematic(p, CinematicType.TIME_TRAVEL, 0, p.getEyePosition(), 0x7DF9FF, 0xFFC24D, "TEMPORAL DISPLACEMENT",
                formatYear(from) + "  →  " + formatYear(targetYear));
        ServerLevel level = p.serverLevel();
        level.playSound(null, p.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.PLAYERS, 1.5f, 1.6f);
        for (int i = 0; i < 5; i++) {
            Scheduler.later(i * 18, () -> level.sendParticles(RvParticles.RING.get().with(0x7DF9FF, 3f, 14), p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0));
        }
        Scheduler.later(100, () -> {
            if (p.isRemoved()) return;
            CompoundTag d = data(p);
            d.putInt("year", targetYear);
            d.putString("line", target.name());
            setParadox(p, paradox(p) + Math.min(40, 2 + jump / 50) + (target == Timeline.ALTERNATE || target == Timeline.FRACTURED ? 10 : 0));
            applyEra(p, targetYear);
            p.serverLevel().sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 1.2f, 20), p.getX(), p.getY() + 1, p.getZ(), 80, 1, 1.5, 1, 0.4);
            RealityOps.cinematic(p, CinematicType.ANNOUNCE, 70, p.getEyePosition(), 0x7DF9FF, 0xFFFFFF, formatYear(targetYear), "Timeline " + target.name() + " • Paradox " + paradox(p) + "%");
            if (paradox(p) >= 60) dispatch(p, "PARADOX THRESHOLD EXCEEDED");
        });
    }

    /** The era shows up in the world around the traveller: the sky's hour and the weather follow the century. */
    private static void applyEra(ServerPlayer p, int year) {
        ServerLevel level = p.serverLevel();
        long hour = Math.floorMod(year * 997L, 24000L);
        level.setDayTime(level.getDayTime() - level.getDayTime() % 24000L + hour);
        if (year < 0) level.setWeatherParameters(6000, 0, false, false);
        else if (year > 3000) level.setWeatherParameters(0, 6000, true, true);
    }

    // ------------------------------------------------------------------ the TSA

    public static boolean tsaEnabled() {
        return tsaEnabled;
    }

    public static void setTsaEnabled(boolean on) {
        tsaEnabled = on;
    }

    /** Called once a second per player: temporal airspace, paradox decay, agent despawning. */
    public static void playerSecond(ServerPlayer p) {
        if (p.tickCount % 200 == 0 && paradox(p) > 0) setParadox(p, paradox(p) - 1);
        if (!tsaEnabled || p.isCreative() || p.isSpectator()) return;
        if (p.getY() > altitudeLimit) {
            int w = WARNINGS.merge(p.getUUID(), 1, Integer::sum);
            if (w == 1 || w == 3) {
                p.displayClientMessage(Component.literal("⚠ TSA: You are entering restricted temporal airspace. Descend immediately.").withColor(0xFF4050), true);
                p.serverLevel().playSound(null, p.blockPosition(), RvSounds.SINGULARITY_IMPLODE.get(), SoundSource.HOSTILE, 0.8f, 2f);
            }
            if (w >= 5) {
                WARNINGS.remove(p.getUUID());
                arrest(p, "TEMPORAL AIRSPACE VIOLATION");
            }
        } else {
            WARNINGS.remove(p.getUUID());
        }
    }

    /** Halts the offender, pulls them back to the ground and leaves agents to keep watch. */
    public static void arrest(ServerPlayer p, String charge) {
        RealityOps.cinematic(p, CinematicType.TSA_ARREST, 0, p.getEyePosition(), 0xFF2030, 0x2050FF, "TIME AND SPACE AUTHORITY", charge);
        p.setDeltaMovement(0, 0, 0);
        p.hurtMarked = true;
        if (p.getAbilities().flying) {
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
        }
        p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 2));
        Scheduler.later(30, () -> {
            if (p.isRemoved()) return;
            ServerLevel level = p.serverLevel();
            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p.blockPosition());
            p.teleportTo(ground.getX() + 0.5, ground.getY() + 0.1, ground.getZ() + 0.5);
            level.sendParticles(RvParticles.RING.get().with(0xFF2030, 4f, 20), p.getX(), p.getY() + 0.1, p.getZ(), 1, 0, 0, 0, 0);
            spawnAgents(p, 2);
            setParadox(p, paradox(p) / 2);
        });
    }

    /** TSA pursuit: agents materialise around a paradox offender. */
    public static void dispatch(ServerPlayer p, String charge) {
        p.displayClientMessage(Component.literal("⚠ TSA: " + charge + ". Agents dispatched to " + coordinate(p) + ".").withColor(0xFF4050), false);
        RealityOps.cinematic(p, CinematicType.TSA_ARREST, 60, p.getEyePosition(), 0xFF2030, 0x2050FF, "TSA PURSUIT", charge);
        spawnAgents(p, 2 + paradox(p) / 30);
    }

    public static List<Mob> spawnAgents(ServerPlayer target, int count) {
        List<Mob> out = new ArrayList<>();
        ServerLevel level = target.serverLevel();
        for (int i = 0; i < count; i++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            BlockPos at = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    BlockPos.containing(target.getX() + Math.cos(a) * 5, target.getY(), target.getZ() + Math.sin(a) * 5));
            dev.riftverse.entity.creature.TsaAgentEntity agent = dev.riftverse.registry.RvEntities.TSA_AGENT.get().create(level);
            if (agent == null) continue;
            agent.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, (float) Math.toDegrees(-a), 0);
            agent.setRank(i == 0 && paradox(target) >= 80 ? 1 : 0);
            agent.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new ItemStack(Items.BREEZE_ROD));
            agent.setDropChance(net.minecraft.world.entity.EquipmentSlot.MAINHAND, 0f);
            agent.setCustomName(Component.literal(agent.rank() > 0 ? "TSA Temporal Enforcer" : "TSA Agent").withColor(agent.rank() > 0 ? 0xFF4050 : 0x60A0FF));
            agent.addTag(AGENT_TAG);
            agent.setWarrant(20 * 90);
            agent.setTarget(target);
            level.addFreshEntity(agent);
            level.sendParticles(RvParticles.RING.get().with(0x60A0FF, 2f, 16), agent.getX(), agent.getY() + 1, agent.getZ(), 1, 0, 0, 0, 0);
            level.sendParticles(RvParticles.STREAK.get().with(0xFFFFFF, 1f, 14), agent.getX(), agent.getY() + 1, agent.getZ(), 30, 0.3, 1, 0.3, 0.2);
            out.add(agent);
        }
        return out;
    }

    public static int recallAgents(net.minecraft.server.MinecraftServer server) {
        int n = 0;
        for (ServerLevel l : server.getAllLevels()) {
            for (var e : l.getAllEntities()) {
                if (e.getTags().contains(AGENT_TAG)) {
                    e.discard();
                    n++;
                }
            }
        }
        return n;
    }
}
