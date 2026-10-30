package dev.infinitygauntlet.gametest;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.network.ControlInputPayload;
import dev.infinitygauntlet.network.PossessionActionPayload;
import dev.infinitygauntlet.nexus.ConsciousnessNexusBlock;
import dev.infinitygauntlet.nexus.NexusBlockEntity;
import dev.infinitygauntlet.nexus.PossessionManager;
import dev.infinitygauntlet.nexus.RemoteMoveControl;
import dev.infinitygauntlet.registry.ModBlocks;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/** Server-side tests of the Consciousness Nexus possession system. */
@GameTestHolder(InfinityGauntlet.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NexusGameTests {
    private static final BlockPos NEXUS = new BlockPos(8, 1, 3);

    private static NexusBlockEntity placeNexus(GameTestHelper helper, ServerPlayer player) {
        helper.setBlock(NEXUS, ModBlocks.CONSCIOUSNESS_NEXUS.get().defaultBlockState().setValue(ConsciousnessNexusBlock.FACING, Direction.SOUTH));
        NexusBlockEntity nexus = helper.getBlockEntity(NEXUS);
        nexus.bindOwner(player);
        return nexus;
    }

    private static void drive(GameTestHelper helper, ServerPlayer player, int fromTick, int ticks, float forward, float yaw, int flags) {
        for (int i = 0; i < ticks; i++) {
            helper.runAfterDelay(fromTick + i, () -> PossessionManager.input(player, new ControlInputPayload(forward, 0, yaw, 0, flags)));
        }
    }

    @GameTest(template = "arena")
    public static void combinationAppearsOnlyWithBothStones(GameTestHelper helper) {
        GauntletState spaceOnly = GauntletState.EMPTY.withStone(InfinityStone.SPACE);
        GauntletState both = spaceOnly.withStone(InfinityStone.MIND);
        Ability combo = Abilities.byId(InfinityGauntlet.id("remote_consciousness"));
        helper.assertTrue(!Abilities.available(InfinityStone.SPACE, spaceOnly.stoneSet()).contains(combo), "hidden with Space alone");
        helper.assertTrue(Abilities.available(InfinityStone.SPACE, both.stoneSet()).contains(combo), "listed under Space with Mind + Space");
        List<Ability> mind = Abilities.available(InfinityStone.MIND, both.stoneSet());
        helper.assertTrue(mind.contains(combo) && mind.stream().allMatch(Ability::isCombination), "Mind lists only its combinations for now");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void operatorDrivesALinkedCreature(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NexusBlockEntity nexus = placeNexus(helper, player);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 12));
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(PossessionManager.link(player, pig).success(), "the pig links to the Nexus");
            helper.assertTrue(pig.moveControl instanceof RemoteMoveControl, "its movement is taken over");
            PossessionManager.toggleControl(player, nexus);
            helper.assertTrue(PossessionManager.isControlling(player), "the operator is at the controls");
            helper.assertTrue(player.position().distanceTo(nexus.consolePosition()) < 1.5, "the operator stands at the console");
        });
        Vec3[] start = new Vec3[1];
        helper.runAfterDelay(4, () -> start[0] = pig.position());
        drive(helper, player, 5, 40, 1f, 0f, 0); // yaw 0 = south (+z)
        helper.runAfterDelay(50, () -> {
            double moved = pig.getZ() - start[0].z;
            helper.assertTrue(moved > 3, "the pig walked where the operator steered it (" + moved + ")");
            helper.assertTrue(Math.abs(pig.getYRot()) < 1, "the pig faces the operator's view");
            PossessionManager.action(player, PossessionActionPayload.DISCONNECT);
            helper.assertTrue(!PossessionManager.isPossessed(pig), "emergency disconnect releases the pig");
            helper.assertTrue(!(pig.moveControl instanceof RemoteMoveControl), "its own movement control is restored");
            helper.assertTrue(nexus.state() == NexusBlockEntity.DISCONNECTING, "the screen plays the disconnection sequence");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void possessedZombieAttacksWhatItLooksAt(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NexusBlockEntity nexus = placeNexus(helper, player);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 1, 12));
        Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(8, 1, 14));
        helper.runAfterDelay(2, () -> {
            PossessionManager.link(player, zombie);
            PossessionManager.toggleControl(player, nexus);
        });
        drive(helper, player, 4, 30, 0f, 0f, ControlInputPayload.ATTACK);
        helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < pig.getMaxHealth(), "the zombie's melee hit the pig"));
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void remoteBlinkAndPortalExtraction(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        NexusBlockEntity nexus = placeNexus(helper, player);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 12));
        Vec3[] before = new Vec3[1];
        helper.runAfterDelay(2, () -> {
            PossessionManager.link(player, pig);
            PossessionManager.toggleControl(player, nexus);
            PossessionManager.input(player, new ControlInputPayload(0, 0, 0f, 0f, 0));
            before[0] = pig.position();
            PossessionManager.action(player, PossessionActionPayload.BLINK);
        });
        helper.runAfterDelay(4, () -> helper.assertTrue(pig.getZ() - before[0].z > 5, "Remote Blink moved the pig forward"));
        helper.runAfterDelay(6, () -> PossessionManager.action(player, PossessionActionPayload.EXTRACT));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(pig.position().distanceTo(Vec3.atBottomCenterOf(nexus.getBlockPos())) < 5, "extraction brought the pig to the Nexus");
            helper.assertTrue(!PossessionManager.isPossessed(pig), "extraction ends the link");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60)
    public static void linkBreaksOutOfRange(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        placeNexus(helper, player);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(8, 1, 12));
        helper.runAfterDelay(2, () -> helper.assertTrue(PossessionManager.link(player, pig).success(), "linked"));
        helper.runAfterDelay(4, () -> pig.teleportTo(pig.getX(), pig.getY(), pig.getZ() + 200));
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!PossessionManager.isPossessed(pig), "the link broke beyond the possession range");
            helper.assertTrue(!(pig.moveControl instanceof RemoteMoveControl), "the pig's own control is restored");
            helper.succeed();
        });
    }
}
