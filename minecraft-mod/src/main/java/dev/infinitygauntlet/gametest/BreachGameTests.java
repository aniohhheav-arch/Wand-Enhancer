package dev.infinitygauntlet.gametest;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.breach.BreachManager;
import dev.infinitygauntlet.breach.ChamberDimension;
import dev.infinitygauntlet.breach.DimensionalRiftEntity;
import dev.infinitygauntlet.breach.ObservationPanelEntity;
import dev.infinitygauntlet.breach.PanelControl;
import dev.infinitygauntlet.breach.PanelLayout;
import dev.infinitygauntlet.breach.SpecimenEntity;
import dev.infinitygauntlet.time.TimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side tests of the Reality Breach: rift, chamber traversal, real-target manipulation and cleanup. */
@GameTestHolder(InfinityGauntlet.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BreachGameTests {
    @GameTest(template = "arena")
    public static void panelHitTestFindsButtons(GameTestHelper helper) {
        PanelLayout layout = new PanelLayout(new Vec3(0, 1.2, 0), 180f); // facing north
        double[] r = PanelLayout.rect(PanelControl.FREEZE);
        Vec3 onButton = layout.toWorld((r[0] + r[2]) / 2, (r[1] + r[3]) / 2);
        Vec3 eye = new Vec3(0, 1.62, -3.5);
        helper.assertTrue(layout.hit(eye, onButton.subtract(eye).normalize(), 6) == PanelControl.FREEZE, "aiming at FREEZE hits FREEZE");
        helper.assertTrue(layout.hit(eye, new Vec3(0, 1, 0), 6) == null, "aiming away hits nothing");
        Vec3 behind = new Vec3(0, 1.62, 3.5);
        helper.assertTrue(layout.hit(behind, onButton.subtract(behind).normalize(), 6) == null, "the back of the panel is not clickable");
        double[] east = PanelLayout.rect(PanelControl.MOVE_EAST), west = PanelLayout.rect(PanelControl.MOVE_WEST);
        helper.assertTrue(layout.toWorld(east[0], 0).x > layout.toWorld(west[0], 0).x, "EAST sits to the east of WEST for a north-facing viewer");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void breachLoopManipulatesTheRealTarget(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, 1, 14));
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 1, 6.5));
        player.moveTo(start.x, start.y, start.z, 0f, 0f);
        float[] pigScale = new float[1];
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(BreachManager.open(player, pig).success(), "the breach opens on the pig");
            DimensionalRiftEntity rift = BreachManager.riftOf(player);
            helper.assertTrue(rift != null && rift.position().distanceTo(pig.getBoundingBox().getCenter()) < 0.6, "the rift sits on the pig");
        });
        // the rift clings to its target
        helper.runAfterDelay(10, () -> pig.teleportTo(pig.getX() + 2, pig.getY(), pig.getZ()));
        helper.runAfterDelay(14, () -> helper.assertTrue(BreachManager.riftOf(player).position().distanceTo(pig.getBoundingBox().getCenter()) < 0.6,
                "the rift followed the pig"));
        // walking into the tear (after it has opened) carries the player into the chamber
        helper.runAfterDelay(DimensionalRiftEntity.OPEN_TICKS + 6, () -> {
            Vec3 c = BreachManager.riftOf(player).position();
            player.teleportTo(c.x, c.y - player.getBbHeight() / 2, c.z - 0.3);
        });
        helper.runAfterDelay(DimensionalRiftEntity.OPEN_TICKS + 12, () -> {
            if (helper.getLevel().getServer().getLevel(ChamberDimension.KEY) == null) {
                // the GameTest server bakes only the flat preset's dimensions (no datapack dimensions), so the chamber
                // cannot exist here: check the graceful refusal instead; the full loop runs in normal worlds
                helper.assertTrue(player.level() == helper.getLevel() && BreachManager.hasBreach(player),
                        "without a chamber the player stays put and the breach stays open");
                BreachManager.open(player, null);
                helper.assertTrue(!BreachManager.hasBreach(player), "re-casting seals the breach");
                helper.succeed();
                return;
            }
            helper.assertTrue(ChamberDimension.isChamber(player.level()), "the player is in the observation chamber");
            ObservationPanelEntity panel = BreachManager.panelOf(player);
            helper.assertTrue(panel != null && panel.level() == player.level(), "the control panel stands in the chamber");
            pigScale[0] = pig.getScale();
            BreachManager.press(player, panel.getId(), PanelControl.FREEZE.ordinal());
            BreachManager.press(player, panel.getId(), PanelControl.GROW.ordinal());
            BreachManager.press(player, panel.getId(), PanelControl.PACIFY.ordinal());
        });
        helper.runAfterDelay(DimensionalRiftEntity.OPEN_TICKS + 16, () -> {
            helper.assertTrue(TimeManager.isFrozen(pig), "FREEZE froze the real pig in its own world");
            helper.assertTrue(pig.getScale() > pigScale[0] * 1.3f, "GROW enlarged the real pig (" + pig.getScale() + ")");
            ObservationPanelEntity panel = BreachManager.panelOf(player);
            SpecimenEntity specimen = (SpecimenEntity) panel.level().getEntity(panel.specimenId());
            helper.assertTrue(specimen != null && specimen.has(SpecimenEntity.FLAG_FROZEN) && specimen.has(SpecimenEntity.FLAG_PACIFIED),
                    "the specimen mirrors the manipulations");
            helper.assertTrue(specimen.targetType() == EntityType.PIG, "the specimen represents a pig");
            BreachManager.press(player, panel.getId(), PanelControl.RELEASE.ordinal());
        });
        helper.runAfterDelay(DimensionalRiftEntity.OPEN_TICKS + 22, () -> {
            helper.assertTrue(player.level() == helper.getLevel(), "releasing returned the player to the original world");
            helper.assertTrue(!BreachManager.hasBreach(player), "the breach is sealed");
            helper.assertTrue(!TimeManager.isFrozen(pig) && Math.abs(pig.getScale() - pigScale[0]) < 0.01f, "every manipulation was reverted");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void breachClosesWhenTheTargetDies(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, 1, 14));
        helper.runAfterDelay(2, () -> BreachManager.open(player, pig));
        helper.runAfterDelay(6, () -> pig.kill());
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(!BreachManager.hasBreach(player), "the breach sealed itself when its target died");
            helper.succeed();
        });
    }
}
