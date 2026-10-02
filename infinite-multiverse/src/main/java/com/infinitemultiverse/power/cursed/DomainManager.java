package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.ability.AbilityTargeting;
import com.infinitemultiverse.core.ability.Summons;
import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.core.world.TemporaryBlocks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Domain Expansion engine. Opening: battle cry, audio build-up and an expanding sphere, then the barrier is raised
 * top-down over ~0.6 s and the floor is re-skinned. While open: a boss bar counts down, the screen is tinted, and the
 * domain's sure-hit rule runs on everything inside. Closing restores every block it touched (also after a crash,
 * via TemporaryBlocks). One domain per user; domains never overlap.
 */
public final class DomainManager {
    private static final int BUILD_TICKS = 12;

    private static final class Domain {
        final UUID owner;
        final DomainType type;
        final ServerLevel level;
        final Vec3 center;
        final double radius;
        final int duration;
        final String group;
        final ServerBossEvent bar;
        final List<BlockPos> shellOrder;
        int age;

        Domain(ServerPlayer owner, DomainType type, double radius, int duration) {
            this.owner = owner.getUUID();
            this.type = type;
            this.level = owner.serverLevel();
            this.center = owner.position();
            this.radius = radius;
            this.duration = duration;
            this.group = "domain:" + owner.getUUID();
            this.bar = new ServerBossEvent(Component.translatable("domain.infinitemultiverse." + type.id()), type.barColor(), BossEvent.BossBarOverlay.NOTCHED_10);
            this.shellOrder = shellPositions(center, radius);
        }
    }

    private static final Map<UUID, Domain> ACTIVE = new HashMap<>();

    private DomainManager() {
    }

    public static boolean hasDomain(ServerPlayer player) {
        return ACTIVE.containsKey(player.getUUID());
    }

    /** True if {@code at} is inside someone else's domain (domains cannot overlap). */
    public static boolean overlapsAnother(ServerPlayer player, double radius) {
        for (Domain domain : ACTIVE.values()) {
            if (domain.level == player.level() && domain.center.distanceTo(player.position()) < domain.radius + radius) {
                return true;
            }
        }
        return false;
    }

    private static final java.util.Set<UUID> RESERVED = new java.util.HashSet<>();

    /** Held while a domain's opening cutscene plays, so it cannot be cast twice. */
    public static void reserve(ServerPlayer player) {
        RESERVED.add(player.getUUID());
    }

    public static void unreserve(ServerPlayer player) {
        RESERVED.remove(player.getUUID());
    }

    public static boolean isReserved(ServerPlayer player) {
        return RESERVED.contains(player.getUUID());
    }

    /** Limitless mastery V (Domain Amplification) widens Unlimited Void by 50%. */
    public static double radiusFor(ServerPlayer owner, DomainType type) {
        double radius = MultiverseConfig.SERVER.domainRadius.get() * type.radiusScale();
        if (type == DomainType.UNLIMITED_VOID && com.infinitemultiverse.power.mastery.Mastery.level(owner, com.infinitemultiverse.core.MultiverseSystem.CURSED_TECHNIQUES) >= 5) {
            radius *= 1.5;
        }
        return radius;
    }

    public static void open(ServerPlayer owner, DomainType type) {
        double radius = radiusFor(owner, type);
        int duration = MultiverseConfig.SERVER.domainDuration.get();
        if (type == DomainType.UNLIMITED_VOID && com.infinitemultiverse.power.mastery.Mastery.level(owner, com.infinitemultiverse.core.MultiverseSystem.CURSED_TECHNIQUES) >= 5) {
            duration = duration * 3 / 2;
        }
        Domain domain = new Domain(owner, type, radius, duration);
        ACTIVE.put(owner.getUUID(), domain);

        MultiverseVfx.shout(domain.level, domain.center, Component.translatable("message.infinitemultiverse.shout.domain",
                Component.translatable("domain.infinitemultiverse." + type.id())).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), radius * 2.5);
        MultiverseVfx.sound(domain.level, domain.center, ModSounds.TIME_STOP, 1.6f, 0.5f);
        com.infinitemultiverse.core.cinematic.Cinematics.scene(domain.level, switch (type) {
            case UNLIMITED_VOID -> com.infinitemultiverse.core.cinematic.SceneIds.UNLIMITED_VOID;
            case MALEVOLENT_SHRINE -> com.infinitemultiverse.core.cinematic.SceneIds.MALEVOLENT_SHRINE;
            case CHIMERA_SHADOW_GARDEN -> com.infinitemultiverse.core.cinematic.SceneIds.CHIMERA_GARDEN;
        }, domain.center, owner.getLookAngle(), type.color(), duration + BUILD_TICKS, owner, (float) radius);
        MultiverseVfx.tint(domain.level, domain.center, radius * 1.3, type.color(), 0.32f, duration + BUILD_TICKS);
    }

    public static void close(ServerPlayer owner) {
        Domain domain = ACTIVE.remove(owner.getUUID());
        if (domain != null) {
            collapse(domain);
        }
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        Iterator<Domain> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Domain domain = it.next();
            ServerPlayer owner = server.getPlayerList().getPlayer(domain.owner);
            domain.age++;
            if (owner == null || !owner.isAlive() || owner.level() != domain.level || domain.age > domain.duration + BUILD_TICKS) {
                it.remove();
                collapse(domain);
                continue;
            }
            if (domain.age <= BUILD_TICKS) {
                build(domain);
                continue;
            }
            int remaining = domain.duration + BUILD_TICKS - domain.age;
            domain.bar.setProgress(Math.max(0f, remaining / (float) domain.duration));
            if (domain.age % 10 == 0) {
                updateBarPlayers(domain);
            }
            applyRule(domain, owner);
        }
    }

    public static void closeAll() {
        for (Domain domain : new ArrayList<>(ACTIVE.values())) {
            collapse(domain);
        }
        ACTIVE.clear();
    }

    // ---- building ----

    private static List<BlockPos> shellPositions(Vec3 center, double radius) {
        List<BlockPos> shell = new ArrayList<>();
        int r = (int) Math.ceil(radius) + 1;
        BlockPos origin = BlockPos.containing(center);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (Math.abs(distance - radius) < 0.55) {
                        shell.add(origin.offset(dx, dy, dz));
                    }
                }
            }
        }
        shell.sort(Comparator.comparingInt((BlockPos pos) -> pos.getY()).reversed());
        return shell;
    }

    private static void build(Domain domain) {
        int ttl = domain.duration + BUILD_TICKS + 40;
        if (domain.type.shell() != null) {
            BlockState shell = domain.type.shell().defaultBlockState();
            int perTick = (int) Math.ceil(domain.shellOrder.size() / (double) BUILD_TICKS);
            int from = (domain.age - 1) * perTick;
            for (int i = from; i < Math.min(domain.shellOrder.size(), from + perTick); i++) {
                TemporaryBlocks.place(domain.level, domain.shellOrder.get(i), shell, ttl, domain.group, false);
            }
        }
        if (domain.age == BUILD_TICKS) {
            reskinFloor(domain, ttl);
            MultiverseVfx.sound(domain.level, domain.center, ModSounds.TIME_RESUME, 1.4f, 0.6f);
        }
    }

    private static void reskinFloor(Domain domain, int ttl) {
        BlockPos origin = BlockPos.containing(domain.center);
        int r = (int) Math.floor(domain.radius) - 1;
        BlockState floor = domain.type.floor().defaultBlockState();
        BlockState accent = domain.type.accent().defaultBlockState();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx * dx + dz * dz > r * r) {
                    continue;
                }
                for (int dy = 1; dy >= -5; dy--) {
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = domain.level.getBlockState(pos);
                    if (!state.isAir() && state.isCollisionShapeFullBlock(domain.level, pos) && domain.level.getBlockState(pos.above()).isAir()) {
                        boolean isAccent = (Math.abs(dx * 7 + dz * 13)) % 11 == 0;
                        TemporaryBlocks.place(domain.level, pos, isAccent ? accent : floor, ttl, domain.group, true);
                        break;
                    }
                }
            }
        }
    }

    private static void collapse(Domain domain) {
        ResourceLocation interior = switch (domain.type) {
            case UNLIMITED_VOID -> com.infinitemultiverse.core.cinematic.SceneIds.UNLIMITED_VOID;
            case MALEVOLENT_SHRINE -> com.infinitemultiverse.core.cinematic.SceneIds.MALEVOLENT_SHRINE;
            case CHIMERA_SHADOW_GARDEN -> com.infinitemultiverse.core.cinematic.SceneIds.CHIMERA_GARDEN;
        };
        ServerPlayer ownerNow = domain.level.getServer().getPlayerList().getPlayer(domain.owner);
        if (ownerNow != null) {
            com.infinitemultiverse.core.cinematic.Cinematics.stop(domain.level, interior, domain.center, ownerNow);
        }
        TemporaryBlocks.revertGroup(domain.level, domain.group);
        domain.bar.removeAllPlayers();
        MultiverseVfx.fx(domain.level, VfxIds.DOMAIN_CLOSE, domain.center.add(0, 1, 0), new Vec3(domain.radius, 0, 0), domain.type.color());
        MultiverseVfx.tint(domain.level, domain.center, domain.radius * 1.3, domain.type.color(), 0f, 0);
        MultiverseVfx.sound(domain.level, domain.center, ModSounds.STAND_DISMISS, 1.4f, 0.5f);
    }

    private static void updateBarPlayers(Domain domain) {
        double reach = domain.radius * 1.3;
        for (ServerPlayer player : domain.level.players()) {
            boolean near = player.position().distanceTo(domain.center) <= reach;
            if (near) {
                domain.bar.addPlayer(player);
            } else {
                domain.bar.removePlayer(player);
            }
        }
    }

    // ---- sure-hit rules ----

    private static void applyRule(Domain domain, ServerPlayer owner) {
        List<LivingEntity> inside = domain.level.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(domain.radius * 2),
                e -> e.position().distanceTo(domain.center) < domain.radius && AbilityTargeting.isHostileTarget(owner, e));
        switch (domain.type) {
            case UNLIMITED_VOID -> {
                // Infinite information: everything inside is paralysed while the user moves freely.
                if (domain.age % 10 == 0) {
                    for (LivingEntity target : inside) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 6, false, false));
                        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 25, 4, false, false));
                        target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 25, 4, false, false));
                        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 40, 0, false, false));
                        if (target instanceof Mob mob) {
                            mob.setTarget(null);
                            mob.getNavigation().stop();
                        }
                    }
                    owner.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 25, 1, false, false));
                }
                if (domain.age % 6 == 0) {
                    MultiverseVfx.fx(domain.level, VfxIds.FROST, domain.center.add(0, domain.radius * 0.5, 0), new Vec3(domain.radius * 0.8, 0, 0), 0xBFD4FF);
                }
            }
            case MALEVOLENT_SHRINE -> {
                // Dismantle and Cleave rain on everything inside.
                if (domain.age % 10 == 0) {
                    for (LivingEntity target : inside) {
                        target.invulnerableTime = 0;
                        target.hurt(owner.damageSources().indirectMagic(owner, owner), 3f + target.getMaxHealth() * 0.02f);
                        MultiverseVfx.fx(domain.level, VfxIds.SLASH, target.getBoundingBox().getCenter(), Vec3.ZERO, 0xFF3040);
                    }
                    if (!inside.isEmpty()) {
                        MultiverseVfx.sound(domain.level, domain.center, ModSounds.STAND_SWING, 0.8f, 0.7f);
                    }
                }
            }
            case CHIMERA_SHADOW_GARDEN -> {
                // The shadows belong to the user: speed and stealth inside, shikigami keep emerging, enemies sink.
                if (domain.age % 10 == 0) {
                    owner.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 25, 1, false, false));
                    owner.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 25, 0, false, false));
                    for (LivingEntity target : inside) {
                        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 25, 2, false, false));
                    }
                }
                if (domain.age % 80 == 0 && !inside.isEmpty()) {
                    long dogs = domain.level.getEntitiesOfClass(Wolf.class, owner.getBoundingBox().inflate(domain.radius),
                            w -> Summons.isAllyOf(w, owner)).size();
                    if (dogs < 4) {
                        Wolf dog = Summons.spawnAlly(owner, EntityType.WOLF, domain.center.add(owner.getRandom().nextGaussian() * 3, 0.5, owner.getRandom().nextGaussian() * 3),
                                domain.duration - domain.age + 40, 0x14141F);
                        if (dog != null) {
                            dog.setCustomName(Component.translatable("entity.infinitemultiverse.divine_dog").withStyle(ChatFormatting.DARK_GRAY));
                        }
                    }
                }
            }
        }
    }
}
