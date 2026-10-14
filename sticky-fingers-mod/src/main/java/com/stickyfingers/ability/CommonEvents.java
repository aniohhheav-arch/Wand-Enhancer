package com.stickyfingers.ability;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.config.SFConfig;
import com.stickyfingers.data.StandData;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import com.stickyfingers.world.SeparationManager;
import com.stickyfingers.world.ZipperNetwork;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Collection;

@EventBusSubscriber(modid = StickyFingersMod.MODID)
public final class CommonEvents {
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer sp) StandManager.tick(sp);
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel sl && sl.getGameTime() % 5 == 0) SeparationManager.get(sl).tick(sl);
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            StandData.get(sp).setSummoned(false);
            StandManager.sync(sp);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            StandManager.cleanup(sp);
            ZipperNetwork.ownedBy(sp.getUUID()).forEach(z -> z.closeAndDiscard());
        }
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            boolean wasSummoned = StandData.get(sp).isSummoned();
            StandManager.dismiss(sp);
            if (wasSummoned) StandManager.summon(sp);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            StandManager.cleanup(sp);
            StandData.get(sp).setSummoned(false);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        for (ServerLevel level : event.getServer().getAllLevels()) SeparationManager.get(level).restoreAll(level);
        ZipperNetwork.clear();
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        LivingEntity e = event.getEntity();
        if (e.hasEffect(ModEffects.IN_ZIPPER)) {
            event.setCanceled(true);
            return;
        }
        if (e.hasEffect(ModEffects.ZIPPER_BODY) && !event.getSource().is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)
                && e.getRandom().nextDouble() < SFConfig.DODGE_CHANCE.get() && e.level() instanceof ServerLevel sl) {
            event.setCanceled(true);
            // the body unzips around the blow
            Vec3 from = event.getSource().getSourcePosition();
            Vec3 away = from == null ? e.getLookAngle().reverse() : e.position().subtract(from).multiply(1, 0, 1).normalize();
            Vec3 side = new Vec3(-away.z, 0, away.x).scale(e.getRandom().nextBoolean() ? 0.6 : -0.6);
            e.push(side.x, 0.05, side.z);
            e.hurtMarked = true;
            Fx.sound(sl, e.position(), ModSounds.DODGE.get(), 1f, 1f + e.getRandom().nextFloat() * 0.2f);
            Fx.particles(sl, ModParticles.SEAM_GLOW.get(), e.position().add(0, e.getBbHeight() * 0.5, 0), 10, 0.2, 0.0);
            Fx.particles(sl, ModParticles.AFTERIMAGE.get(), e.position().add(0, e.getBbHeight() * 0.5, 0), 4, 0.2, 0.0);
        }
    }

    @SubscribeEvent
    public static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity t = event.getNewAboutToBeSetTarget();
        if (t != null && t.hasEffect(ModEffects.IN_ZIPPER)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && StandData.get(sp).isHidden()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMount(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.isDismounting() && event.getEntityMounting() instanceof ServerPlayer sp && event.getEntityBeingMounted() != null) {
            Interactions.exitMob(sp, event.getEntityBeingMounted());
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("stickyfingers")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("grant").then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                    players.forEach(p -> StandData.get(p).setHasStand(true));
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.stickyfingers.grant", players.size()), true);
                    return players.size();
                })))
                .then(Commands.literal("revoke").then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                    players.forEach(p -> { StandManager.dismiss(p); StandData.get(p).setHasStand(false); });
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.stickyfingers.revoke", players.size()), true);
                    return players.size();
                })))
                .then(Commands.literal("energy").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("value", FloatArgumentType.floatArg(0)).executes(ctx -> {
                            float v = FloatArgumentType.getFloat(ctx, "value");
                            Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                            players.forEach(p -> StandData.get(p).setEnergy(v));
                            return players.size();
                        }))))
                .then(Commands.literal("resetcooldowns").then(Commands.argument("targets", EntityArgument.players()).executes(ctx -> {
                    Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
                    players.forEach(p -> { for (AbilityType t : AbilityType.VALUES) StandData.get(p).setCooldownEnd(t, 0); });
                    return players.size();
                })))
                .then(Commands.literal("cast").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("ability", com.mojang.brigadier.arguments.StringArgumentType.word())
                                .suggests((ctx, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(
                                        java.util.Arrays.stream(AbilityType.VALUES).map(AbilityType::key), b))
                                .executes(ctx -> cast(ctx, false))
                                .then(Commands.literal("alt").executes(ctx -> cast(ctx, true))))))
                .then(Commands.literal("restoreworld").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    SeparationManager.get(level).restoreAll(level);
                    ctx.getSource().sendSuccess(() -> Component.translatable("commands.stickyfingers.restored"), true);
                    return 1;
                })));
    }

    /** Forces an ability activation for the target players (admin / map-making / testing). */
    private static int cast(com.mojang.brigadier.context.CommandContext<net.minecraft.commands.CommandSourceStack> ctx, boolean alt)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String key = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "ability");
        AbilityType type = java.util.Arrays.stream(AbilityType.VALUES).filter(t -> t.key().equals(key)).findFirst().orElse(null);
        if (type == null) {
            ctx.getSource().sendFailure(Component.literal("Unknown ability " + key));
            return 0;
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(ctx, "targets");
        players.forEach(p -> AbilityExecutor.tryActivate(p, type.ordinal(), alt));
        return players.size();
    }

    private CommonEvents() {}
}
