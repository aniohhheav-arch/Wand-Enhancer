package dev.riftverse.event;

import dev.riftverse.Riftverse;
import dev.riftverse.multiverse.EndProtocols;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.RealityRewriter;
import dev.riftverse.multiverse.RealityState;
import dev.riftverse.multiverse.Scheduler;
import dev.riftverse.multiverse.event.EventManager;
import dev.riftverse.network.UniverseSync;
import dev.riftverse.player.ArmorAbilities;
import dev.riftverse.registry.RvWorldgen;
import dev.riftverse.transit.TransitManager;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import dev.riftverse.world.TerrainSampler;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingBreatheEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CommonEvents {
    private static final ResourceLocation GRAVITY_ID = Riftverse.id("universe_gravity");

    private CommonEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener((ServerStartedEvent e) -> {
            UniverseRegistry.onServerStarted(e.getServer());
            RealityState.onServerStarted(e.getServer());
            dev.riftverse.creator.CreatorAuthority.load(e.getServer());
        });
        bus.addListener((ServerStoppingEvent e) -> {
            // end sequences while the levels still exist so no rift, boss or half-run protocol is orphaned
            if (!EndProtocols.runs().isEmpty()) EndProtocols.stop(e.getServer(), null, null);
            EventManager.shutdown();
            RealityRewriter.cancelAll();
        });
        bus.addListener((ServerStoppedEvent e) -> {
            UniverseRegistry.onServerStopped();
            RealityState.onServerStopped();
            Scheduler.clear();
            EndProtocols.clear();
            dev.riftverse.multiverse.GenesisManager.clear();
            dev.riftverse.wormhole.WormholeManager.clearAll();
            dev.riftverse.temporal.FireTrails.clear();
            RealityOps.clear();
            TransitManager.clear();
            TerrainSampler.clearCache();
        });
        bus.addListener((ServerTickEvent.Post e) -> {
            TransitManager.tick(e.getServer());
            Scheduler.tick(e.getServer());
            RealityRewriter.tick(e.getServer());
            EventManager.tick(e.getServer());
            EndProtocols.tick(e.getServer());
            dev.riftverse.multiverse.GenesisManager.tick(e.getServer());
            dev.riftverse.wormhole.WormholeManager.tick(e.getServer());
            dev.riftverse.temporal.FireTrails.tick();
        });
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) UniverseSync.send(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            TransitManager.onLogout(e.getEntity());
            UniverseSync.forget(e.getEntity().getUUID());
            dev.riftverse.creator.CreatorAuthority.endSession(e.getEntity().getUUID());
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                UniverseSync.send(sp);
                applyGravity(sp);
            }
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (!(e.getEntity() instanceof ServerPlayer sp)) return;
            if (RealityState.isSealed(sp.level().dimension())) {
                // the respawn point lies in an ended world: wake up in the Nexus instead
                ServerLevel nexus = sp.server.getLevel(RvWorldgen.NEXUS);
                if (nexus != null) {
                    net.minecraft.core.BlockPos a = dev.riftverse.world.NexusLayout.ARRIVAL;
                    nexus.getChunk(a.getX() >> 4, a.getZ() >> 4);
                    sp.teleportTo(nexus, a.getX() + 0.5, a.getY(), a.getZ() + 0.5, 180f, 0f);
                }
            }
            UniverseSync.send(sp);
        });
        bus.addListener((net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent e) -> {
            if (RealityState.isSealed(e.getDimension())) {
                e.setCanceled(true);
                if (e.getEntity() instanceof ServerPlayer sp) {
                    sp.displayClientMessage(net.minecraft.network.chat.Component.literal("That world has ended. The way is closed forever.").withColor(0xFF8A9A), true);
                }
            }
        });
        bus.addListener(CommonEvents::onPlayerTick);
        bus.addListener(CommonEvents::onIncomingDamage);
        bus.addListener(CommonEvents::onFall);
        bus.addListener(CommonEvents::onBreathe);
        bus.addListener(CommonEvents::onJoin);
        bus.addListener((net.neoforged.neoforge.event.entity.living.LivingDropsEvent e) -> {
            LivingEntity dead = e.getEntity();
            if (!(e.getSource().getEntity() instanceof Player) || !(dead.level() instanceof ServerLevel level)) return;
            UniverseSpec spec = specOf(dead);
            if (spec == null) return;
            boolean boss = dead.getMaxHealth() > 120f;
            float chance = boss ? 1f : (dead instanceof net.minecraft.world.entity.monster.Enemy ? 0.04f : 0.015f);
            if (level.random.nextFloat() >= chance) return;
            var stack = dev.riftverse.item.relic.Relics.forge(spec, level.random.nextBoolean(), level.random);
            e.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(level, dead.getX(), dead.getY() + 0.5, dead.getZ(), stack));
        });
        bus.addListener(CommonEvents::onLevelTick);
        bus.addListener((PlayerEvent.NameFormat e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && dev.riftverse.creator.CreatorAuthority.aura(sp.getUUID())) {
                e.setDisplayname(net.minecraft.network.chat.Component.literal("✦ Reality Architect ").withColor(0xC080FF).append(e.getDisplayname()));
            }
        });
    }

    public static UniverseSpec specOf(LivingEntity entity) {
        if (entity.level().dimension() != RvWorldgen.EXPANSE) return null;
        return UniverseRegistry.specAt(entity.getBlockX(), entity.getBlockZ());
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        ArmorAbilities.tick(player);
        if (player.tickCount % 4 == 0 && dev.riftverse.creator.CreatorAuthority.aura(player.getUUID())) {
            player.serverLevel().sendParticles(dev.riftverse.registry.RvParticles.STREAK.get().with(player.tickCount % 8 == 0 ? 0xFFFFFF : 0xA040FF, 0.7f, 18),
                    player.getX(), player.getY() + 1, player.getZ(), 2, 0.4, 0.8, 0.4, 0.02);
        }
        if (player.tickCount % 20 != 0) return;
        UniverseSync.check(player);
        if (player.level().dimension() == RvWorldgen.EXPANSE) dev.riftverse.wormhole.WormholeManager.naturalSecond(player);
        RealityOps.playerSecond(player);
        dev.riftverse.temporal.TemporalManager.playerSecond(player);
        dev.riftverse.multiverse.CorridorDirector.playerSecond(player);
        applyGravity(player);
        UniverseEffects.playerSecond(player, specOf(player));
    }

    public static void applyGravity(LivingEntity entity) {
        AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
        if (gravity == null) return;
        UniverseSpec spec = specOf(entity);
        float g = spec == null ? 1f : spec.gravity;
        if (Math.abs(g - 1f) < 0.01f) {
            if (gravity.getModifier(GRAVITY_ID) != null) gravity.removeModifier(GRAVITY_ID);
            return;
        }
        AttributeModifier current = gravity.getModifier(GRAVITY_ID);
        double amount = g - 1.0;
        if (current == null || Math.abs(current.amount() - amount) > 1e-4) {
            gravity.addOrUpdateTransientModifier(new AttributeModifier(GRAVITY_ID, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
        }
    }

    private static void onIncomingDamage(LivingIncomingDamageEvent e) {
        if (e.getEntity() instanceof Player p && TransitManager.isProtected(p)) e.setCanceled(true);
        if (e.getEntity() instanceof ServerPlayer sp && dev.riftverse.multiverse.GenesisManager.inGenesis(sp)) e.setCanceled(true);
    }

    private static void onFall(LivingFallEvent e) {
        UniverseSpec spec = specOf(e.getEntity());
        if (spec == null) return;
        float mult = spec.dreamlike ? 0f : Math.min(1f, spec.gravity * spec.gravity);
        e.setDamageMultiplier(e.getDamageMultiplier() * mult);
    }

    private static void onBreathe(LivingBreatheEvent e) {
        if (!(e.getEntity() instanceof Player player)) return;
        UniverseSpec spec = specOf(player);
        if (spec == null || !spec.vacuum) return;
        if (ArmorAbilities.hasVoyagerHelmet(player) || player.isCreative() || player.isSpectator()) return;
        e.setCanBreathe(false);
        e.setConsumeAirAmount(Math.max(e.getConsumeAirAmount(), 2));
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        if (e.getEntity() instanceof net.minecraft.world.entity.PathfinderMob mob && !(mob instanceof dev.riftverse.entity.boss.CosmicDeityEntity)
                && mob.getMaxHealth() <= 120f) {
            mob.goalSelector.addGoal(8, new dev.riftverse.entity.ai.RiftSeekGoal(mob));
        }
        if (e.getEntity() instanceof LivingEntity living && !(living instanceof Player) && e.getLevel().dimension() == RvWorldgen.EXPANSE) {
            applyGravity(living);
        }
    }

    private static void onLevelTick(LevelTickEvent.Post e) {
        Level level = e.getLevel();
        if (level.isClientSide || level.dimension() != RvWorldgen.EXPANSE) return;
        UniverseEffects.levelTick((ServerLevel) level);
    }
}
