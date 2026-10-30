package dev.infinitygauntlet.gametest;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.entity.ChronoBarrierEntity;
import dev.infinitygauntlet.entity.TemporalLoopEntity;
import dev.infinitygauntlet.entity.TimeFieldEntity;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.time.RewindController;
import dev.infinitygauntlet.time.TimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side behaviour tests for the Time Stone's tick dilation and abilities. */
@GameTestHolder(InfinityGauntlet.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStoneGameTests {
    @GameTest(template = "arena", timeoutTicks = 100)
    public static void frozenArrowHangsInTheAir(GameTestHelper helper) {
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 4, 4.5));
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setPos(start);
        arrow.setDeltaMovement(0, 0, 1.5);
        helper.getLevel().addFreshEntity(arrow);
        TimeManager.apply(arrow, 0f, 40);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(arrow.position().distanceTo(start) < 0.01, "a frozen arrow does not move (moved " + arrow.position().distanceTo(start) + ")");
            helper.assertTrue(arrow.getDeltaMovement().z > 1.4, "a frozen arrow keeps its momentum");
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(arrow.isRemoved() || arrow.position().distanceTo(start) > 3, "the arrow flies on once time resumes");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void slowedArrowTravelsAtTheScaledRate(GameTestHelper helper) {
        Vec3 a0 = helper.absoluteVec(new Vec3(4.5, 6, 2.5));
        Vec3 b0 = helper.absoluteVec(new Vec3(11.5, 6, 2.5));
        Arrow normal = new Arrow(EntityType.ARROW, helper.getLevel());
        Arrow slowed = new Arrow(EntityType.ARROW, helper.getLevel());
        for (Arrow a : new Arrow[]{normal, slowed}) {
            a.setNoGravity(true);
            a.setDeltaMovement(0, 0, 0.5);
        }
        normal.setPos(a0);
        slowed.setPos(b0);
        helper.getLevel().addFreshEntity(normal);
        helper.getLevel().addFreshEntity(slowed);
        TimeManager.apply(slowed, 0.25f, 80);
        helper.runAfterDelay(20, () -> {
            double dn = normal.position().distanceTo(a0);
            double ds = slowed.position().distanceTo(b0);
            helper.assertTrue(dn > 5, "the normal arrow flew (" + dn + ")");
            helper.assertTrue(Math.abs(ds / dn - 0.25) < 0.08, "the slowed arrow covered a quarter of the distance (" + ds + " vs " + dn + ")");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void slowFieldOnlyAffectsEntitiesInside(GameTestHelper helper) {
        Zombie inside = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 8));
        Zombie outside = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 24));
        Vec3 c = helper.absoluteVec(new Vec3(8.5, 1, 8.5));
        TimeFieldEntity field = new TimeFieldEntity(ModEntities.TIME_FIELD.get(), helper.getLevel());
        field.setup(null, 60, 5f);
        field.configure(TimeFieldEntity.SHAPE_DISC, 0.25f);
        field.moveTo(c.x, c.y, c.z);
        helper.getLevel().addFreshEntity(field);
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(TimeManager.scale(inside) == 0.25f, "the zombie inside the field is slowed");
            helper.assertTrue(TimeManager.scale(outside) == 1f, "the zombie outside the field runs normally");
        });
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(TimeManager.scale(inside) == 1f, "time returns to normal once the field expires");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void rewindReturnsAnEntityAlongItsPath(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().add(InfinityGauntletItem.fullGauntlet());
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 4));
        Vec3 start = zombie.position();
        for (int i = 1; i <= 40; i++) {
            int step = i;
            helper.runAfterDelay(i, () -> zombie.teleportTo(start.x, start.y, start.z + step * 0.4));
        }
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(zombie.position().distanceTo(start) > 12, "the zombie moved away first");
            int ticks = RewindController.start(zombie, 200);
            helper.assertTrue(ticks > 10, "the zombie had recorded history (" + ticks + " ticks)");
        });
        helper.runAfterDelay(80, () -> {
            // horizontal only: the zombie spawns a block above the floor and falls again once the rewind ends
            double horizontal = Math.hypot(zombie.getX() - start.x, zombie.getZ() - start.z);
            helper.assertTrue(horizontal < 1.0, "rewind brought the zombie back to its start (off by " + horizontal + ")");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160)
    public static void loopSnapsEntitiesBack(GameTestHelper helper) {
        Vec3 c = helper.absoluteVec(new Vec3(8.5, 1.03, 8.5));
        TemporalLoopEntity loop = new TemporalLoopEntity(ModEntities.TEMPORAL_LOOP.get(), helper.getLevel());
        loop.setup(null, 140, 3.5f);
        loop.moveTo(c.x, c.y, c.z);
        helper.getLevel().addFreshEntity(loop);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 8));
        Vec3 entered = zombie.position();
        helper.runAfterDelay(10, () -> zombie.teleportTo(entered.x, entered.y, entered.z + 6));
        helper.succeedWhen(() -> helper.assertTrue(zombie.position().distanceTo(entered) < 0.6, "the loop snapped the zombie back"));
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void barrierSuspendsAndReflectsArrows(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 p = helper.absoluteVec(new Vec3(8.5, 1, 4.5));
        player.moveTo(p.x, p.y, p.z, 0f, 0f);
        ItemStack gauntlet = InfinityGauntletItem.fullGauntlet();
        player.setItemInHand(InteractionHand.MAIN_HAND, gauntlet);
        AbilityCaster.cast(player, InteractionHand.MAIN_HAND, gauntlet, Abilities.byId(InfinityGauntlet.id("chronological_barrier")), true);
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setNoGravity(true);
        arrow.setPos(p.add(0, 1, 14));
        arrow.setDeltaMovement(0, 0, -1.2);
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(TimeManager.isFrozen(arrow), "the arrow is suspended in front of the barrier");
            helper.assertTrue(arrow.getZ() > p.z + 1, "the arrow never reached the caster");
            ChronoBarrierEntity barrier = helper.getLevel().getEntitiesOfClass(ChronoBarrierEntity.class, player.getBoundingBox().inflate(6))
                    .getFirst();
            barrier.release();
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(arrow.getDeltaMovement().z > 0.5, "the released arrow flies back the way it came");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, skyAccess = true)
    public static void timeFractureOpensAndPulses(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 p = helper.absoluteVec(new Vec3(8.5, 1, 2.5));
        player.moveTo(p.x, p.y, p.z, 0f, -5f);
        ItemStack gauntlet = InfinityGauntletItem.fullGauntlet();
        player.setItemInHand(InteractionHand.MAIN_HAND, gauntlet);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 20));
        boolean cast = AbilityCaster.cast(player, InteractionHand.MAIN_HAND, gauntlet, Abilities.byId(InfinityGauntlet.id("time_fracture")), true);
        helper.assertTrue(cast, "the fracture cast succeeds");
        helper.runAfterDelay(30, () -> helper.assertTrue(!helper.getLevel().getEntitiesOfClass(dev.infinitygauntlet.entity.TimeFractureEntity.class,
                player.getBoundingBox().inflate(48)).isEmpty(), "a fracture exists after the charge-up"));
        helper.runAfterDelay(66, () -> {
            helper.assertTrue(TimeManager.scale(zombie) < 0.5f, "the fracture's pulse slowed the nearby zombie");
            helper.succeed();
        });
    }
}
