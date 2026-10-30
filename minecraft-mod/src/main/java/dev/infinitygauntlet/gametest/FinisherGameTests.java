package dev.infinitygauntlet.gametest;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.finisher.FinisherManager;
import dev.infinitygauntlet.finisher.FinisherType;
import dev.infinitygauntlet.finisher.Timelines;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.time.TimeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The five stationary signature finishers: each must hold its target alive (and shielded) until its own finishing
 * beat, finish it exactly then, and release the caster at the end. Each runs in its own batch.
 */
@GameTestHolder(InfinityGauntlet.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FinisherGameTests {
    private static ServerPlayer caster(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 2, 3.5));
        player.moveTo(start.x, start.y, start.z, 0f, 15f);
        player.setItemInHand(InteractionHand.MAIN_HAND, InfinityGauntletItem.fullGauntlet());
        return player;
    }

    private static void runFinisher(GameTestHelper helper, String ability, int killAt, int duration) {
        ServerPlayer player = caster(helper);
        Zombie strong = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 8));
        helper.assertTrue(!AbilityCaster.cast(player, InteractionHand.MAIN_HAND, player.getMainHandItem(),
                Abilities.byId(InfinityGauntlet.id(ability)), true), ability + " refuses a healthy target");
        strong.setHealth(4f);
        helper.assertTrue(AbilityCaster.cast(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), Abilities.byId(InfinityGauntlet.id(ability)), true),
                ability + " starts on a weakened target");
        helper.assertTrue(FinisherManager.busy(player), ability + " runs as a finisher");
        helper.runAfterDelay(killAt - 3, () -> {
            strong.hurt(helper.getLevel().damageSources().generic(), 100f);
            helper.assertTrue(strong.isAlive(), ability + ": the target survives until the finishing beat");
        });
        helper.runAfterDelay(killAt + 3, () -> helper.assertTrue(!strong.isAlive(), ability + ": the finishing beat kills the target"));
        helper.runAfterDelay(duration + 3, () -> {
            helper.assertTrue(!FinisherManager.busy(player), ability + ": the caster is released");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "finisher_erasure")
    public static void dimensionalErasure(GameTestHelper helper) {
        runFinisher(helper, "dimensional_erasure", Timelines.Erasure.ERASE, Timelines.Erasure.DURATION);
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "finisher_psychic")
    public static void psychicOverload(GameTestHelper helper) {
        runFinisher(helper, "psychic_overload", Timelines.Psychic.PULSE, Timelines.Psychic.DURATION);
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "finisher_rewrite")
    public static void realityRewrite(GameTestHelper helper) {
        runFinisher(helper, "reality_rewrite", Timelines.Rewrite.SHATTER, Timelines.Rewrite.DURATION);
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "finisher_soul")
    public static void soulExtraction(GameTestHelper helper) {
        runFinisher(helper, "soul_extraction", Timelines.Extraction.CONSUMED, Timelines.Extraction.DURATION);
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "finisher_collapse")
    public static void temporalCollapseStopsTheScene(GameTestHelper helper) {
        Zombie bystander = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(3, 2, 10));
        runFinisher(helper, "temporal_collapse", Timelines.Collapse.RELEASE, Timelines.Collapse.DURATION);
        helper.runAfterDelay(Timelines.Collapse.FREEZE + 10, () -> helper.assertTrue(TimeManager.isFrozen(bystander), "the whole scene is stopped"));
        helper.runAfterDelay(Timelines.Collapse.RELEASE + 6, () -> helper.assertTrue(!TimeManager.isFrozen(bystander), "time resumes for the bystander"));
    }

    @GameTest(template = "arena", batch = "finisher_registry")
    public static void everyStoneHasASignatureFinisher(GameTestHelper helper) {
        for (FinisherType type : FinisherType.values()) {
            helper.assertTrue(type.duration > 60, type + " plays out over several seconds");
        }
        for (InfinityStone stone : InfinityStone.values()) {
            boolean has = false;
            for (FinisherType type : FinisherType.values()) {
                has |= type.stone == stone;
            }
            helper.assertTrue(has, stone + " has a signature finisher");
            helper.assertTrue(!Abilities.forStone(stone).isEmpty(), stone + " has at least one castable ability");
        }
        helper.succeed();
    }
}
