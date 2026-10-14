package com.stickyfingers.ability;

import com.stickyfingers.data.StandData;
import com.stickyfingers.entity.ZipperEntity;
import com.stickyfingers.entity.ZipperKind;
import com.stickyfingers.network.ScreenFx;
import com.stickyfingers.registry.ModEffects;
import com.stickyfingers.registry.ModParticles;
import com.stickyfingers.registry.ModSounds;
import com.stickyfingers.world.ZipperNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.Vec3;

/**
 * Right-click interaction with zippers. Unzipped mobs can be climbed into: you hide inside them
 * (invisible, untargetable) and ride along until you sneak to unzip yourself back out.
 */
public final class Interactions {
    private static final double REACH = 8.0;

    public static void interact(ServerPlayer p, int entityId) {
        if (!StandData.get(p).hasStand() || p.isSpectator()) return;
        Entity e = p.level().getEntity(entityId);
        if (e == null || e.distanceTo(p) > REACH + 2) return;

        if (e instanceof ZipperEntity z && z.isOwnedBy(p)) {
            switch (z.kind()) {
                case MOB -> { if (z.attachedEntity() instanceof LivingEntity le) enterMob(p, le, z); }
                case STORAGE -> openStorage(p);
                case TRAP, CHAIN, FIELD, BODY -> {}
                default -> {
                    ZipperEntity dest = z.isOpen() ? ZipperNetwork.pickDestination(z, p) : null;
                    if (dest != null) ZipperNetwork.transport(p, z, dest);
                    else if (z.isOpen()) z.close(); else z.open();
                }
            }
            return;
        }
        if (e instanceof LivingEntity le) {
            for (ZipperEntity z : ZipperNetwork.ownedBy(p.getUUID())) {
                if (z.kind() == ZipperKind.MOB && z.attachedId() == le.getId() && z.isOpen()) {
                    enterMob(p, le, z);
                    return;
                }
            }
        }
    }

    static void openStorage(ServerPlayer p) {
        var container = StandData.get(p).storage();
        Fx.sound(p.serverLevel(), p.position(), ModSounds.STORAGE_OPEN.get(), 1f, 1f);
        p.openMenu(new SimpleMenuProvider((id, inv, pl) -> ChestMenu.threeRows(id, inv, container),
                Component.translatable("container.stickyfingers.zipper_storage")));
    }

    static void enterMob(ServerPlayer p, LivingEntity host, ZipperEntity z) {
        if (host == p || p.isPassenger()) return;
        ServerLevel level = p.serverLevel();
        Fx.screen(p, ScreenFx.ZIP_TRANSITION, 1f, 16);
        Fx.sound(level, host.position(), ModSounds.ZIPPER_OPEN.get(), 1f, 0.8f);
        Fx.particles(level, ModParticles.VOID_MOTE.get(), host.position().add(0, host.getBbHeight() / 2, 0), 24, 0.4, 0.04);
        StandManager.dismiss(p);
        if (!p.startRiding(host, true)) return;
        host.removeEffect(ModEffects.UNZIPPED);
        p.addEffect(new MobEffectInstance(ModEffects.IN_ZIPPER, 20 * 60 * 10, 0, false, false, true));
        p.setInvisible(true);
        z.closeAndDiscard();
        p.getPersistentData().putBoolean("sf_inside", true);
        p.displayClientMessage(Component.translatable("message.stickyfingers.inside", host.getDisplayName()).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Called when the hidden player dismounts (sneak) or the host dies. */
    public static void exitMob(ServerPlayer p, Entity host) {
        if (!p.getPersistentData().getBoolean("sf_inside")) return;
        p.getPersistentData().remove("sf_inside");
        p.removeEffect(ModEffects.IN_ZIPPER);
        p.setInvisible(false);
        ServerLevel level = p.serverLevel();
        Vec3 side = host.getLookAngle().multiply(1, 0, 1).normalize();
        Vec3 out = Abilities.safeSpot(level, host.position().add(new Vec3(-side.z, 0, side.x).scale(host.getBbWidth() + 0.6)), host.position());
        level.getServer().execute(() -> {
            p.teleportTo(level, out.x, out.y, out.z, p.getYRot(), p.getXRot());
            StandManager.summon(p);
        });
        ZipperEntity seam = ZipperEntity.create(level, p, ZipperKind.ESCAPE, host.position().add(0, host.getBbHeight() * 0.55, 0),
                p.position().subtract(host.position()).multiply(1, 0, 1), 90f, Math.max(1f, host.getBbHeight() * 0.7f), 0.6f).life(16);
        level.addFreshEntity(seam);
        seam.open();
        Fx.screen(p, ScreenFx.ZIP_TRANSITION, 1f, 14);
        Fx.sound(level, host.position(), ModSounds.ZIPPER_CLOSE.get(), 1f, 1.1f);
        Fx.particles(level, ModParticles.ZIPPER_SPARK.get(), host.position().add(0, 1, 0), 14, 0.4, 0.1);
    }

    private Interactions() {}
}
