package dev.infinitygauntlet.mind;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.PlayerPowerData;
import dev.infinitygauntlet.ability.TargetRules;
import dev.infinitygauntlet.config.ServerConfig;
import dev.infinitygauntlet.network.MindStatePayload;
import dev.infinitygauntlet.network.ModNetwork;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Server state of the Mind Stone: telekinetic grips and throws, mind-controlled creatures, dazed creatures and
 * astral projections. Everything here is ticked once per server tick and cleaned up when its owner leaves.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID)
public final class MindManager {
    public static final int GOLD = InfinityStone.MIND.color();
    public static final int PALE = InfinityStone.MIND.highlight();

    // ------------------------------------------------------------------ telekinesis

    private static final class Grip {
        final ServerPlayer caster;
        final Entity target;
        double distance;
        int age;

        Grip(ServerPlayer caster, Entity target, double distance) {
            this.caster = caster;
            this.target = target;
            this.distance = distance;
        }
    }

    private record Thrown(ServerPlayer by, Entity entity, int[] age) {
    }

    /** A thrown block: tracked separately because a falling block places itself the tick it lands. */
    private static final class ThrownBlock {
        final ServerPlayer by;
        final FallingBlockEntity block;
        Vec3 last;
        int age;

        ThrownBlock(ServerPlayer by, FallingBlockEntity block) {
            this.by = by;
            this.block = block;
            this.last = block.position();
        }
    }

    private static final List<ThrownBlock> THROWN_BLOCKS = new ArrayList<>();

    private static final Map<UUID, Grip> GRIPS = new HashMap<>();
    private static final List<Thrown> THROWN = new ArrayList<>();
    public static final int GRIP_MAX_TICKS = 300;

    public static boolean holding(ServerPlayer player) {
        return GRIPS.containsKey(player.getUUID());
    }

    /**
     * Tears a block out of the world into a floating block that can be held and thrown. Refused for unbreakable
     * blocks, blocks with contents (chests, furnaces...) and where the player may not build.
     */
    @Nullable
    public static FallingBlockEntity liftBlock(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.serverLevel();
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity() || state.getDestroySpeed(level, pos) < 0
                || !level.mayInteract(player, pos) || !player.mayBuild()) {
            return null;
        }
        FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
        block.setNoGravity(true);
        block.time = 1;
        block.dropItem = false;
        block.setDeltaMovement(0, 0.3, 0); // off the ground at once, or it would settle straight back into place
        block.hurtMarked = true;
        Vfx.send(level, VfxType.GEOMETRY_BURST, Vec3.atCenterOf(pos), Vec3.ZERO, GOLD, PALE, 1.2f, 12);
        level.levelEvent(2001, pos, Block.getId(state)); // the crumble of it tearing loose
        return block;
    }

    public static void grab(ServerPlayer player, Entity target) {
        GRIPS.put(player.getUUID(), new Grip(player, target, Math.max(2.5, Math.min(12, player.getEyePosition().distanceTo(target.position())))));
        if (target instanceof Mob mob) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        Vfx.sound(player.serverLevel(), target.position(), ModSounds.MIND_GRAB, 1.5f, 1f);
        Vfx.send(player.serverLevel(), VfxType.GEOMETRY_BURST, target.getBoundingBox().getCenter(), Vec3.ZERO, GOLD, PALE, 1.4f, 14);
    }

    /** Lets go: a hurl along the caster's look, or with {@code gentle} a slow, harmless set-down. */
    public static void release(ServerPlayer player, boolean gentle) {
        Grip g = GRIPS.remove(player.getUUID());
        if (g == null || !g.target.isAlive()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        // thrown at whatever is under the crosshair, from wherever the held thing actually is
        Vec3 eye = player.getEyePosition();
        var hit = level.clip(new net.minecraft.world.level.ClipContext(eye, eye.add(player.getLookAngle().scale(64)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        Vec3 aim = hit.getLocation();
        Vec3 throwDir = aim.subtract(g.target.getBoundingBox().getCenter());
        throwDir = throwDir.lengthSqr() < 1.0E-4 ? player.getLookAngle() : throwDir.normalize();
        if (g.target instanceof FallingBlockEntity block) {
            block.setNoGravity(!gentle); // thrown blocks fly true for a moment before dropping
            if (!gentle) {
                block.setDeltaMovement(throwDir.scale(2.2));
                block.hurtMarked = true;
                THROWN_BLOCKS.add(new ThrownBlock(player, block));
                Vfx.sound(level, block.position(), ModSounds.MIND_THROW, 2f, 0.8f);
                Vfx.send(level, VfxType.ENTITY_TRAIL, block.position(), Vec3.ZERO, GOLD, PALE, 0.9f, 30, block.getId());
                return;
            }
            block.setDeltaMovement(Vec3.ZERO); // set down: it settles back into the world as a block
            block.hurtMarked = true;
            return;
        }
        if (gentle) {
            g.target.setDeltaMovement(player.getLookAngle().scale(0.1).add(0, -0.05, 0));
            if (g.target instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 60, 0));
            }
            Vfx.sound(level, g.target.position(), ModSounds.MIND_SHIELD_HIT, 1f, 0.8f);
        } else {
            g.target.setDeltaMovement(throwDir.scale(2.4).add(0, 0.1, 0));
            THROWN.add(new Thrown(player, g.target, new int[]{0}));
            Vfx.sound(level, g.target.position(), ModSounds.MIND_THROW, 2f, 1f);
            Vfx.send(level, VfxType.ENTITY_TRAIL, g.target.position(), Vec3.ZERO, GOLD, PALE, 0.8f, 20, g.target.getId());
        }
        g.target.hurtMarked = true;
    }

    private static void tickGrips() {
        Iterator<Grip> it = GRIPS.values().iterator();
        while (it.hasNext()) {
            Grip g = it.next();
            ServerPlayer p = g.caster;
            if (!p.isAlive() || p.hasDisconnected() || !g.target.isAlive() || g.target.level() != p.level() || ++g.age > GRIP_MAX_TICKS
                    || g.target.distanceToSqr(p) > 32 * 32) {
                it.remove();
                if (g.target.isAlive()) {
                    g.target.setDeltaMovement(Vec3.ZERO);
                    g.target.setNoGravity(false);
                    g.target.hurtMarked = true;
                }
                continue;
            }
            Vec3 point = p.getEyePosition().add(p.getLookAngle().scale(g.distance));
            Vec3 centre = g.target.getBoundingBox().getCenter();
            Vec3 pull = point.subtract(centre).scale(0.35);
            if (pull.length() > 1.4) {
                pull = pull.normalize().scale(1.4);
            }
            g.target.setDeltaMovement(pull);
            g.target.fallDistance = 0;
            g.target.hurtMarked = true;
            if (g.target instanceof Mob mob) {
                mob.setTarget(null);
                mob.getNavigation().stop();
            }
            if (g.target instanceof FallingBlockEntity block) {
                block.time = 1; // a held block never times out
            }
            if (g.age % 4 == 1) {
                Vfx.send(p.serverLevel(), VfxType.NEURAL_LINK, centre, new Vec3(p.getId(), 0, 0), GOLD, PALE, 1f, 6, g.target.getId());
            }
        }
        Iterator<ThrownBlock> tb = THROWN_BLOCKS.iterator();
        while (tb.hasNext()) {
            ThrownBlock t = tb.next();
            FallingBlockEntity b = t.block;
            t.age++;
            if (t.age == 12) {
                b.setNoGravity(false);
            }
            if (!(b.level() instanceof ServerLevel level)) {
                tb.remove();
                continue;
            }
            boolean landed = b.isRemoved(); // it already placed itself where it struck
            boolean struck = !landed && (b.horizontalCollision || b.onGround() || !level.getEntitiesOfClass(LivingEntity.class,
                    b.getBoundingBox().inflate(0.3), o -> o != t.by && TargetRules.canTarget(t.by, o)).isEmpty());
            if (landed || struck || t.age > 100) {
                tb.remove();
                Vec3 at = landed ? t.last : b.position();
                if (landed) {
                    BlockPos placed = BlockPos.containing(at);
                    if (level.getBlockState(placed).is(b.getBlockState().getBlock())) {
                        level.removeBlock(placed, false);
                    }
                } else {
                    b.discard();
                }
                blockImpact(t.by, level, at.add(0, 0.5, 0), b.getBlockState());
                continue;
            }
            t.last = b.position();
        }
        Iterator<Thrown> th = THROWN.iterator();
        while (th.hasNext()) {
            Thrown t = th.next();
            Entity e = t.entity();
            int age = ++t.age()[0];
            if (!e.isAlive() || age > 60) {
                th.remove();
                continue;
            }
            if (age < 3 || !(e.level() instanceof ServerLevel level)) {
                continue;
            }
            List<LivingEntity> struck = level.getEntitiesOfClass(LivingEntity.class, e.getBoundingBox().inflate(0.4),
                    o -> o != e && o != t.by() && TargetRules.canTarget(t.by(), o));
            if (e.horizontalCollision || e.verticalCollision && e.getDeltaMovement().y < -0.05 || !struck.isEmpty()) {
                th.remove();
                float damage = damage(10f);
                if (e instanceof LivingEntity living) {
                    living.hurt(level.damageSources().indirectMagic(t.by(), t.by()), damage);
                }
                for (LivingEntity o : struck) {
                    o.hurt(level.damageSources().indirectMagic(t.by(), t.by()), damage * 0.8f);
                    o.setDeltaMovement(e.getDeltaMovement().scale(0.6).add(0, 0.3, 0));
                    o.hurtMarked = true;
                }
                Vec3 c = e.getBoundingBox().getCenter();
                Vfx.send(level, VfxType.GEOMETRY_BURST, c, Vec3.ZERO, GOLD, PALE, 2.2f, 16);
                Vfx.send(level, VfxType.SHOCKWAVE, c, e.getDeltaMovement().lengthSqr() > 0 ? e.getDeltaMovement().normalize() : new Vec3(0, 1, 0), GOLD, PALE, 3f, 10);
                Vfx.sound(level, c, ModSounds.MIND_BURST, 1.5f, 1.3f);
                Vfx.shake(level, c, 16, 0.4f);
            }
        }
    }

    /** A thrown block bursts on impact: a real explosion (terrain only if the server allows it) and golden geometry. */
    private static void blockImpact(ServerPlayer by, ServerLevel level, Vec3 at, BlockState state) {
        boolean terrain = ServerConfig.POWER_TERRAIN_DAMAGE.get();
        level.explode(by, level.damageSources().explosion(by, by), null, at.x, at.y, at.z, 2.6f, false,
                terrain ? Level.ExplosionInteraction.TNT : Level.ExplosionInteraction.NONE);
        level.levelEvent(2001, BlockPos.containing(at), Block.getId(state));
        Vfx.send(level, VfxType.POWER_EXPLOSION, at, Vec3.ZERO, 0xFFB01E, 0xFFF4C0, 2.6f, 24);
        Vfx.send(level, VfxType.SPHERE_PULSE, at, Vec3.ZERO, GOLD, 0xFFFFFF, 4f, 10);
        Vfx.send(level, VfxType.GEOMETRY_BURST, at, Vec3.ZERO, GOLD, PALE, 3.5f, 20);
        Vfx.send(level, VfxType.SHOCKWAVE, at, new Vec3(0, 1, 0), GOLD, PALE, 6f, 14);
        Vfx.send(level, VfxType.DEBRIS, at, Vec3.ZERO, Block.getId(state), 10, 0.9f, 60);
        Vfx.sound(level, at, ModSounds.MIND_BURST, 2f, 0.9f);
        Vfx.camera(level, at, 24, 0.6f, 0, 0, 0xFFF0C0, 0.15f, 4);
    }

    public static float damage(float base) {
        return base * ServerConfig.MIND_DAMAGE_MULTIPLIER.get().floatValue();
    }

    // ------------------------------------------------------------------ mind control

    private static final class Control {
        final Mob mob;
        final UUID controller;
        long end;
        @Nullable
        LivingEntity order;

        Control(Mob mob, UUID controller, long end) {
            this.mob = mob;
            this.controller = controller;
            this.end = end;
        }
    }

    private static final Map<Mob, Control> CONTROLLED = new HashMap<>();

    public static int controlledBy(ServerPlayer player) {
        int n = 0;
        for (Control c : CONTROLLED.values()) {
            if (c.controller.equals(player.getUUID())) {
                n++;
            }
        }
        return n;
    }

    public static boolean isControlled(Entity e) {
        return e instanceof Mob mob && CONTROLLED.containsKey(mob);
    }

    @Nullable
    public static UUID controllerOf(Entity e) {
        Control c = e instanceof Mob mob ? CONTROLLED.get(mob) : null;
        return c == null ? null : c.controller;
    }

    public static void control(ServerPlayer player, Mob mob) {
        int ticks = ServerConfig.MIND_CONTROL_SECONDS.get() * 20;
        CONTROLLED.put(mob, new Control(mob, player.getUUID(), player.serverLevel().getGameTime() + ticks));
        mob.setTarget(null);
        mob.setPersistenceRequired();
        ServerLevel level = player.serverLevel();
        Vfx.send(level, VfxType.MIND_AURA, mob.position(), Vec3.ZERO, GOLD, PALE, 1f, ticks, mob.getId());
        Vfx.send(level, VfxType.NEURAL_LINK, mob.getBoundingBox().getCenter(), new Vec3(player.getId(), 0, 0), GOLD, PALE, 1.4f, 16, mob.getId());
        Vfx.send(level, VfxType.GEOMETRY_BURST, mob.getEyePosition(), Vec3.ZERO, GOLD, PALE, 1.6f, 18);
        Vfx.sound(level, mob.position(), ModSounds.MIND_CONTROL, 2f, 1f);
    }

    /** Points every creature {@code player} controls at {@code target}. */
    public static int command(ServerPlayer player, LivingEntity target) {
        int n = 0;
        for (Control c : CONTROLLED.values()) {
            if (c.controller.equals(player.getUUID()) && c.mob != target) {
                c.order = target;
                c.mob.setTarget(target);
                Vfx.send(player.serverLevel(), VfxType.NEURAL_LINK, target.getBoundingBox().getCenter(), new Vec3(c.mob.getId(), 0, 0), GOLD, PALE, 1f, 10,
                        target.getId());
                n++;
            }
        }
        if (n > 0) {
            Vfx.send(player.serverLevel(), VfxType.MIND_MARK, target.position(), Vec3.ZERO, GOLD, PALE, 0.7f, 20, target.getId());
            Vfx.sound(player.serverLevel(), target.position(), ModSounds.MIND_MARK, 1.2f, 1.6f);
        }
        return n;
    }

    private static void endControl(Control c) {
        if (c.mob.isAlive() && c.mob.level() instanceof ServerLevel level) {
            c.mob.setTarget(null);
            Vfx.send(level, VfxType.GEOMETRY_BURST, c.mob.getEyePosition(), Vec3.ZERO, GOLD, PALE, 1f, 10);
            Vfx.sound(level, c.mob.position(), ModSounds.MIND_SHIELD_HIT, 1f, 0.6f);
        }
    }

    private static boolean alliedTo(Control c, Entity other) {
        if (other == null) {
            return false;
        }
        if (other.getUUID().equals(c.controller)) {
            return true;
        }
        if (other instanceof Mob m) {
            Control oc = CONTROLLED.get(m);
            if (oc != null && oc.controller.equals(c.controller)) {
                return true;
            }
        }
        ServerPlayer owner = c.mob.level().getServer() == null ? null : c.mob.level().getServer().getPlayerList().getPlayer(c.controller);
        return owner != null && TargetRules.isAlly(owner, other);
    }

    private static void tickControl(MinecraftServer server) {
        Iterator<Control> it = CONTROLLED.values().iterator();
        while (it.hasNext()) {
            Control c = it.next();
            Mob mob = c.mob;
            if (!mob.isAlive() || mob.level().getGameTime() >= c.end) {
                it.remove();
                endControl(c);
                continue;
            }
            ServerPlayer owner = server.getPlayerList().getPlayer(c.controller);
            if (owner == null) {
                it.remove();
                endControl(c);
                continue;
            }
            if (mob.tickCount % 10 != 0) {
                continue;
            }
            LivingEntity target = mob.getTarget();
            if (c.order != null && c.order.isAlive()) {
                target = c.order;
            } else if (target == null || !target.isAlive() || alliedTo(c, target)) {
                target = null;
                double best = 16 * 16;
                for (LivingEntity e : mob.level().getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(16),
                        e -> e instanceof Enemy && e != mob && e.isAlive() && !alliedTo(c, e))) {
                    double dsq = e.distanceToSqr(mob);
                    if (dsq < best) {
                        best = dsq;
                        target = e;
                    }
                }
            }
            if (target != null) {
                mob.setTarget(target);
            } else {
                mob.setTarget(null);
                if (mob.distanceToSqr(owner) > 6 * 6 && mob.level() == owner.level()) {
                    mob.getNavigation().moveTo(owner, 1.2);
                }
            }
        }
    }

    // ------------------------------------------------------------------ dazed creatures

    private static final Map<Mob, Long> DAZED = new HashMap<>();

    public static void daze(Mob mob, int ticks) {
        DAZED.put(mob, mob.level().getGameTime() + ticks);
        mob.setTarget(null);
        if (mob.level() instanceof ServerLevel level) {
            Vfx.send(level, VfxType.DAZE, mob.position(), Vec3.ZERO, GOLD, PALE, 1f, ticks, mob.getId());
        }
    }

    public static boolean dazed(Entity e) {
        return e instanceof Mob mob && DAZED.containsKey(mob);
    }

    private static void tickDazed() {
        Iterator<Map.Entry<Mob, Long>> it = DAZED.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Mob, Long> en = it.next();
            Mob mob = en.getKey();
            if (!mob.isAlive() || mob.level().getGameTime() >= en.getValue()) {
                it.remove();
                continue;
            }
            mob.setTarget(null);
            if (mob.tickCount % 20 == 0) {
                RandomSource r = mob.getRandom();
                mob.getNavigation().moveTo(mob.getX() + r.nextGaussian() * 4, mob.getY(), mob.getZ() + r.nextGaussian() * 4, 0.6);
            }
        }
    }

    @SubscribeEvent
    static void onTarget(LivingChangeTargetEvent event) {
        LivingEntity e = event.getEntity();
        LivingEntity next = event.getNewAboutToBeSetTarget();
        if (next == null || !(e instanceof Mob mob)) {
            return;
        }
        if (DAZED.containsKey(mob)) {
            event.setCanceled(true);
            return;
        }
        Control c = CONTROLLED.get(mob);
        if (c != null && alliedTo(c, next)) {
            event.setCanceled(true); // a controlled creature never turns on its controller or their side
        }
    }

    // ------------------------------------------------------------------ astral projection

    private static final class Astral {
        final ServerPlayer player;
        final AstralBodyEntity body;
        final long end;
        final int age0;
        int age;

        Astral(ServerPlayer player, AstralBodyEntity body, long end) {
            this.player = player;
            this.body = body;
            this.end = end;
            this.age0 = 0;
        }
    }

    private static final Map<UUID, Astral> ASTRAL = new HashMap<>();

    public static boolean projecting(ServerPlayer player) {
        return ASTRAL.containsKey(player.getUUID());
    }

    public static void project(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        int ticks = ServerConfig.ASTRAL_SECONDS.get() * 20;
        AstralBodyEntity body = new AstralBodyEntity(ModEntities.ASTRAL_BODY.get(), level);
        body.setup(player, ticks + 200, 0.6f);
        body.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        body.pose(player.yBodyRot, player.getYHeadRot());
        level.addFreshEntity(body);
        PlayerPowerData data = player.getData(ModAttachments.POWER_DATA);
        data.astralBody = GlobalPos.of(level.dimension(), player.blockPosition());
        data.astralMode = player.gameMode.getGameModeForPlayer().getName();
        ASTRAL.put(player.getUUID(), new Astral(player, body, level.getGameTime() + ticks));
        player.setGameMode(GameType.SPECTATOR);
        player.setDeltaMovement(player.getLookAngle().scale(0.4).add(0, 0.3, 0));
        player.hurtMarked = true;
        ModNetwork.sendTo(player, new MindStatePayload(MindStatePayload.ASTRAL, ticks));
        Vfx.send(level, VfxType.ASTRAL_GHOST, player.position(), Vec3.ZERO, GOLD, PALE, 1f, ticks, player.getId());
        Vfx.send(level, VfxType.GEOMETRY_BURST, player.getEyePosition(), Vec3.ZERO, GOLD, PALE, 2.4f, 20);
        Vfx.sound(level, player.position(), ModSounds.MIND_ASTRAL_OUT, 2f, 1f);
        Vfx.transition(player, 20, InfinityStone.MIND);
    }

    /** Ends the projection: the consciousness snaps back into the body. */
    public static void returnToBody(ServerPlayer player) {
        Astral a = ASTRAL.remove(player.getUUID());
        if (a == null) {
            return;
        }
        restore(player, a.body.isAlive() ? a.body.position() : null, a.body.isAlive() ? a.body.getYRot() : player.getYRot());
        a.body.discard();
    }

    private static void restore(ServerPlayer player, @Nullable Vec3 at, float yaw) {
        PlayerPowerData data = player.getData(ModAttachments.POWER_DATA);
        GameType mode = GameType.byName(data.astralMode == null ? "survival" : data.astralMode, GameType.SURVIVAL);
        ServerLevel level = player.serverLevel();
        if (at == null && data.astralBody != null) {
            ServerLevel bodyLevel = player.server.getLevel(data.astralBody.dimension());
            if (bodyLevel != null) {
                level = bodyLevel;
                at = Vec3.atBottomCenterOf(data.astralBody.pos());
            }
        }
        if (at != null) {
            TeleportUtil.teleport(player, level, at, yaw, player.getXRot(), Vec3.ZERO);
        }
        player.setGameMode(mode);
        data.astralBody = null;
        data.astralMode = null;
        var ability = dev.infinitygauntlet.ability.Abilities.byId(InfinityGauntlet.id("astral_projection"));
        if (ability != null && mode != GameType.CREATIVE) {
            data.setCooldownEnd(ability.id(), player.serverLevel().getGameTime() + ServerConfig.cooldown(ability));
            dev.infinitygauntlet.ability.AbilityCaster.syncCooldowns(player);
        }
        ModNetwork.sendTo(player, new MindStatePayload(MindStatePayload.ASTRAL, 0));
        Vfx.sound(player.serverLevel(), player.position(), ModSounds.MIND_ASTRAL_IN, 2f, 1f);
        Vfx.send(player.serverLevel(), VfxType.GEOMETRY_BURST, player.getEyePosition(), Vec3.ZERO, GOLD, PALE, 1.6f, 16);
        Vfx.transition(player, 16, InfinityStone.MIND);
    }

    /** The body was struck: the shock throws the consciousness back into it, and it takes the hit. */
    static void bodyStruck(AstralBodyEntity body, net.minecraft.world.damagesource.DamageSource source, float amount) {
        ServerPlayer owner = body.owner();
        if (owner == null || !projecting(owner)) {
            return;
        }
        returnToBody(owner);
        owner.hurt(source, amount);
    }

    private static void tickAstral() {
        Iterator<Astral> it = ASTRAL.values().iterator();
        while (it.hasNext()) {
            Astral a = it.next();
            ServerPlayer p = a.player;
            a.age++;
            boolean expired = p.serverLevel().getGameTime() >= a.end;
            boolean home = a.age > 40 && p.level() == a.body.level() && p.distanceToSqr(a.body) < 1.6 * 1.6;
            if (!p.isAlive() || p.hasDisconnected() || expired || home || !a.body.isAlive()) {
                it.remove();
                if (!p.hasDisconnected()) {
                    restore(p, a.body.isAlive() ? a.body.position() : null, a.body.getYRot());
                }
                a.body.discard();
                continue;
            }
            double range = ServerConfig.ASTRAL_RANGE.get();
            if (p.level() != a.body.level()) {
                TeleportUtil.teleport(p, (ServerLevel) a.body.level(), a.body.position().add(0, 2, 0), p.getYRot(), p.getXRot(), Vec3.ZERO);
            } else if (p.distanceToSqr(a.body) > range * range) {
                // the silver cord pulls the projection back within reach of the body
                Vec3 back = a.body.position().add(p.position().subtract(a.body.position()).normalize().scale(range - 1));
                p.teleportTo(back.x, back.y, back.z);
            }
            if (a.age % 100 == 0) {
                Vfx.send(p.serverLevel(), VfxType.ASTRAL_GHOST, p.position(), Vec3.ZERO, GOLD, PALE, 1f, (int) (a.end - p.serverLevel().getGameTime()), p.getId());
            }
        }
    }

    @SubscribeEvent
    static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Astral a = ASTRAL.remove(p.getUUID());
            if (a != null) {
                restore(p, a.body.position(), a.body.getYRot()); // saved back in the body, never left in spectator
                a.body.discard();
            }
            GRIPS.remove(p.getUUID());
        }
    }

    @SubscribeEvent
    static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        // a crash or restart mid-projection: put the player back in their body
        if (event.getEntity() instanceof ServerPlayer p && p.getData(ModAttachments.POWER_DATA).astralBody != null) {
            restore(p, null, p.getYRot());
        }
    }

    // ------------------------------------------------------------------

    @SubscribeEvent
    static void onTick(ServerTickEvent.Post event) {
        tickGrips();
        tickControl(event.getServer());
        tickDazed();
        tickAstral();
    }

    @SubscribeEvent
    static void onStopped(ServerStoppedEvent event) {
        GRIPS.clear();
        THROWN.clear();
        THROWN_BLOCKS.clear();
        CONTROLLED.clear();
        DAZED.clear();
        ASTRAL.clear();
    }

    /** Living creatures within {@code radius} that {@code caster} may affect. */
    public static List<LivingEntity> targets(ServerPlayer caster, Vec3 c, double radius) {
        return caster.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                e -> e.isAlive() && TargetRules.canTarget(caster, e) && e.getBoundingBox().getCenter().distanceToSqr(c) <= radius * radius);
    }

    static ResourceKey<Level> dimension(Entity e) {
        return e.level().dimension();
    }

    private MindManager() {
    }
}
