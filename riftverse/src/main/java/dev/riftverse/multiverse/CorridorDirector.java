package dev.riftverse.multiverse;

import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.util.Hash;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Vex;

/**
 * Life inside the Infinite Corridor: zone announcements, time anomalies (stretches where time misbehaves, including
 * true loops that put you back where you started), rare corridor events, and an ambient score that changes with the
 * zone and grows deeper and stranger the farther you walk from the start.
 */
public final class CorridorDirector {
    private static final Map<UUID, Integer> LAST_ZONE = new HashMap<>();
    private static final Map<UUID, Integer> LOOP_FROM = new HashMap<>();

    private CorridorDirector() {}

    public static boolean inCorridor(ServerPlayer p) {
        return p.level().dimension() == RvWorldgen.EXPANSE && InfiniteCorridor.isCorridor(UniverseId.ofBlock(p.getBlockX(), p.getBlockZ()))
                && Math.abs(p.getZ()) < InfiniteCorridor.HALF_WIDTH + 8 && p.getY() > InfiniteCorridor.FLOOR - 12;
    }

    /** Time anomaly stretches: one 32-block segment in ten misbehaves. 0 none, 1 slow, 2 fast, 3 loop, 4 freeze-frame. */
    public static int anomaly(int x) {
        long h = Hash.of(0x7173L, Math.floorDiv(x, InfiniteCorridor.DOOR_SPACING));
        if (Hash.unit(h) > 0.1f) return 0;
        return 1 + (int) Long.remainderUnsigned(Hash.mix(h), 4);
    }

    public static void playerSecond(ServerPlayer p) {
        if (!inCorridor(p)) {
            LAST_ZONE.remove(p.getUUID());
            return;
        }
        ServerLevel l = p.serverLevel();
        RandomSource r = p.getRandom();
        int x = p.getBlockX();
        int zone = InfiniteCorridor.zone(x);
        Integer last = LAST_ZONE.put(p.getUUID(), zone);
        if (last == null || last != zone) {
            int depth = Math.abs(Math.floorDiv(x, InfiniteCorridor.ZONE_LENGTH));
            RealityOps.cinematic(p, CinematicType.ANNOUNCE, 60, p.getEyePosition(), 0xB0A0FF, 0xFFFFFF, InfiniteCorridor.ZONES[zone], "depth " + depth);
        }
        // time anomalies
        switch (anomaly(x)) {
            case 1 -> {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 2, true, false));
                l.sendParticles(RvParticles.RING.get().with(0x7DF9FF, 3f, 20), p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
            }
            case 2 -> {
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 30, 3, true, false));
                l.sendParticles(RvParticles.STREAK.get().with(0xFFB040, 1f, 10), p.getX(), p.getY() + 1, p.getZ(), 10, 1, 1, 1, 0.3);
            }
            case 3 -> {
                // the loop: reach the far end of this segment and you are walking into its start again
                int segStart = Math.floorDiv(x, InfiniteCorridor.DOOR_SPACING) * InfiniteCorridor.DOOR_SPACING;
                Integer from = LOOP_FROM.get(p.getUUID());
                if (from == null || from != segStart) {
                    LOOP_FROM.put(p.getUUID(), segStart);
                    p.displayClientMessage(Component.literal("Haven't you been here before?").withColor(0xFFB040), true);
                } else if (x - segStart >= InfiniteCorridor.DOOR_SPACING - 3 && r.nextInt(3) != 0) {
                    p.teleportTo(segStart + 2.5, p.getY(), p.getZ());
                    RealityOps.cinematic(p, CinematicType.FLASH, 0, p.getEyePosition(), 0xFFB040, 0xFFB040, "", "");
                } else if (x - segStart <= 2 && r.nextInt(3) != 0) {
                    p.teleportTo(segStart + InfiniteCorridor.DOOR_SPACING - 3.5, p.getY(), p.getZ());
                }
            }
            case 4 -> {
                if (r.nextInt(6) == 0) {
                    p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 9, true, false));
                    p.displayClientMessage(Component.literal("Time skips a beat.").withColor(0x7DF9FF), true);
                }
            }
            default -> LOOP_FROM.remove(p.getUUID());
        }
        ambience(p, l, zone, x, r);
        if (r.nextInt(240) == 0) corridorEvent(p, l, r);
    }

    /** The corridor's score: each zone has its own voice, and the farther from the start the lower and stranger it gets. */
    private static void ambience(ServerPlayer p, ServerLevel l, int zone, int x, RandomSource r) {
        if (p.tickCount % 80 != 0) return;
        float depth = Math.min(1f, Math.abs(x) / 8000f);
        float pitch = 1.0f - 0.5f * depth + (r.nextFloat() - 0.5f) * 0.1f;
        Holder<SoundEvent> s = switch (zone) {
            case 1 -> SoundEvents.AMBIENT_CAVE;
            case 2 -> Holder.direct(SoundEvents.AMBIENT_UNDERWATER_LOOP_ADDITIONS);
            case 3 -> SoundEvents.AMBIENT_CRIMSON_FOREST_ADDITIONS;
            case 4 -> SoundEvents.AMBIENT_SOUL_SAND_VALLEY_ADDITIONS;
            case 5 -> SoundEvents.AMBIENT_BASALT_DELTAS_ADDITIONS;
            case 6 -> Holder.direct(SoundEvents.AMETHYST_BLOCK_CHIME);
            case 9 -> Holder.direct(SoundEvents.COPPER_BULB_TURN_ON);
            case 10 -> SoundEvents.NOTE_BLOCK_BIT;
            case 12 -> Holder.direct(SoundEvents.SCULK_SHRIEKER_SHRIEK);
            case 14 -> SoundEvents.NOTE_BLOCK_CHIME;
            default -> SoundEvents.AMBIENT_WARPED_FOREST_MOOD;
        };
        p.playNotifySound(s.value(), SoundSource.AMBIENT, 0.6f + 0.4f * depth, pitch);
        if (depth > 0.3f && r.nextInt(3) == 0) p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 0.4f * depth, 0.5f);
    }

    private static void corridorEvent(ServerPlayer p, ServerLevel l, RandomSource r) {
        switch (r.nextInt(5)) {
            case 0 -> {
                p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
                p.displayClientMessage(Component.literal("The lights go out.").withColor(0x808080), true);
            }
            case 1 -> {
                Vex walker = EntityType.VEX.create(l);
                if (walker != null) {
                    walker.moveTo(p.getX() + (r.nextBoolean() ? 24 : -24), p.getY() + 1, 0);
                    walker.setCustomName(Component.literal("The Walker").withColor(0x404050));
                    walker.setLimitedLife(20 * 20);
                    walker.setTarget(p);
                    l.addFreshEntity(walker);
                }
                p.displayClientMessage(Component.literal("Footsteps. Not yours.").withColor(0x9090A0), true);
            }
            case 2 -> {
                for (int i = 0; i < 8; i++) p.playNotifySound(SoundEvents.WOODEN_DOOR_CLOSE, SoundSource.AMBIENT, 1f, 0.5f + i * 0.08f);
                p.displayClientMessage(Component.literal("Somewhere, every door slams at once.").withColor(0xB0A0FF), true);
            }
            case 3 -> {
                l.sendParticles(RvParticles.GLITCH.get().with(0xB0A0FF, 1f, 20), p.getX(), p.getY() + 1, p.getZ(), 80, 6, 2, 3, 0.1);
                p.displayClientMessage(Component.literal("The corridor rearranges itself.").withColor(0xB0A0FF), true);
                p.teleportTo(p.getX() + (r.nextBoolean() ? 1 : -1) * (64 + r.nextInt(256)), p.getY(), p.getZ());
            }
            default -> p.displayClientMessage(Component.literal(new String[] {"...keep walking...", "...it never ends...", "...turn around...",
                    "...the doors remember you...", "...behind the cracked wall..."}[r.nextInt(5)]).withColor(0x6A6080), true);
        }
    }
}
