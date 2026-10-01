package dev.riftverse.event;

import dev.riftverse.Riftverse;
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
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class CommonEvents {
    private static final ResourceLocation GRAVITY_ID = Riftverse.id("universe_gravity");

    private CommonEvents() {}

    public static void register(IEventBus bus) {
        bus.addListener((ServerStartedEvent e) -> UniverseRegistry.onServerStarted(e.getServer()));
        bus.addListener((ServerStoppedEvent e) -> {
            UniverseRegistry.onServerStopped();
            TransitManager.clear();
            TerrainSampler.clearCache();
        });
        bus.addListener((ServerTickEvent.Post e) -> TransitManager.tick(e.getServer()));
        bus.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) UniverseSync.send(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            TransitManager.onLogout(e.getEntity());
            UniverseSync.forget(e.getEntity().getUUID());
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) {
                UniverseSync.send(sp);
                applyGravity(sp);
            }
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) UniverseSync.send(sp);
        });
        bus.addListener(CommonEvents::onPlayerTick);
        bus.addListener(CommonEvents::onIncomingDamage);
        bus.addListener(CommonEvents::onFall);
        bus.addListener(CommonEvents::onBreathe);
        bus.addListener(CommonEvents::onJoin);
        bus.addListener(CommonEvents::onLevelTick);
    }

    public static UniverseSpec specOf(LivingEntity entity) {
        if (entity.level().dimension() != RvWorldgen.EXPANSE) return null;
        return UniverseRegistry.specAt(entity.getBlockX(), entity.getBlockZ());
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        ArmorAbilities.tick(player);
        if (player.tickCount % 20 != 0) return;
        UniverseSync.check(player);
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
