package dev.arcana.magic;

import dev.arcana.ArcanaConfig;
import dev.arcana.ArcanaMod;
import dev.arcana.net.Net;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = ArcanaMod.MODID)
public final class ServerEvents {
    @SubscribeEvent
    public static void levelTick(LevelTickEvent.Post e) {
        if (e.getLevel() instanceof ServerLevel l) EffectManager.tick(l);
    }

    @SubscribeEvent
    public static void playerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p)) return;
        PlayerMagic m = CastService.magic(p);
        float max = CastService.maxMana(p);
        if (m.mana < max) m.mana = Math.min(max, m.mana + ArcanaConfig.MANA_REGEN.get().floatValue());
        else if (m.mana > max) m.mana = max;
        long t = p.level().getGameTime();
        if (t % 5 == 0) {
            m.history.addLast(new PlayerMagic.Snapshot(p.level().dimension(), p.position(), p.getHealth(), p.getYRot(), p.getXRot()));
            while (m.history.size() > 20) m.history.removeFirst();
        }
        if (t % 4 == 0) Net.sync(p, false);
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Net.sync(p, true);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            CastService.magic(p).history.clear();
            Net.sync(p, true);
        }
    }

    /** Safety net: entities left frozen or summoned by an effect that no longer runs (crash / restart) are repaired. */
    @SubscribeEvent
    public static void join(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        var ent = e.getEntity();
        var tag = ent.getPersistentData();
        if (tag.getBoolean("arcana_frozen") && !EffectManager.FROZEN.contains(ent.getUUID())) {
            tag.remove("arcana_frozen");
            if (ent instanceof Mob mob) mob.setNoAi(false);
            ent.setNoGravity(false);
        }
        if (tag.getBoolean("arcana_summon") && !EffectManager.SUMMONS.contains(ent.getUUID())) ent.discard();
        if (ent instanceof Projectile && tag.getBoolean("arcana_frozen") && !EffectManager.FROZEN.contains(ent.getUUID())) ent.setNoGravity(false);
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent e) {
        EffectManager.shutdown();
    }

    private ServerEvents() {}
}
