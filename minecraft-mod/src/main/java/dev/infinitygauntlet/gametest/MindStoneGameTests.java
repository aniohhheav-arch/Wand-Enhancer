package dev.infinitygauntlet.gametest;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.mind.LevitationFieldEntity;
import dev.infinitygauntlet.mind.MentalShieldEntity;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.registry.ModAttachments;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Server-side behaviour tests for the Mind Stone; each test runs in its own batch. */
@GameTestHolder(InfinityGauntlet.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MindStoneGameTests {
    private static ServerPlayer caster(GameTestHelper helper, float pitch) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 2, 3.5));
        player.moveTo(start.x, start.y, start.z, 0f, pitch);
        player.setYHeadRot(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, InfinityGauntletItem.fullGauntlet());
        return player;
    }

    private static boolean cast(ServerPlayer player, String name) {
        return AbilityCaster.cast(player, InteractionHand.MAIN_HAND, player.getMainHandItem(), Abilities.byId(InfinityGauntlet.id(name)), true);
    }

    @GameTest(template = "arena", batch = "mind_list")
    public static void mindStoneHasNineAbilitiesAndItsFinisher(GameTestHelper helper) {
        helper.assertTrue(Abilities.forStone(InfinityStone.MIND).size() == 10, "nine Mind abilities plus Psychic Overload");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "mind_telekinesis")
    public static void telekinesisHoldsThenThrows(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 8));
        double floor = zombie.getY();
        helper.assertTrue(cast(player, "telekinesis"), "telekinesis seizes the zombie");
        helper.assertTrue(MindManager.holding(player), "it is held");
        helper.runAfterDelay(15, () -> {
            helper.assertTrue(zombie.getY() > floor + 0.5, "the held zombie floats at eye level");
            helper.assertTrue(cast(player, "telekinesis"), "a second cast throws it");
            helper.assertTrue(!MindManager.holding(player), "it is no longer held");
        });
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(zombie.getZ() > helper.absoluteVec(new Vec3(0, 0, 14)).z || zombie.getHealth() < zombie.getMaxHealth(),
                    "the thrown zombie flew away or crashed");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "mind_telekinesis_block")
    public static void telekinesisTearsUpABlockAndItExplodes(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        BlockPos wallPos = new BlockPos(8, 3, 7);
        helper.setBlock(wallPos, net.minecraft.world.level.block.Blocks.STONE_BRICKS);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 16));
        // aim at the block
        Vec3 eye = player.getEyePosition();
        Vec3 to = helper.absoluteVec(new Vec3(8.5, 3.5, 7.5)).subtract(eye);
        player.setXRot((float) -Math.toDegrees(Math.atan2(to.y, Math.sqrt(to.x * to.x + to.z * to.z))));
        helper.assertTrue(cast(player, "telekinesis"), "telekinesis takes hold of the block");
        helper.assertTrue(MindManager.holding(player), "the block is held");
        helper.runAfterDelay(5, () -> helper.assertBlockNotPresent(net.minecraft.world.level.block.Blocks.STONE_BRICKS, wallPos));
        helper.runAfterDelay(20, () -> {
            player.setXRot(0f);
            helper.assertTrue(cast(player, "telekinesis"), "a second cast throws the block");
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(!zombie.isAlive() || zombie.getHealth() < zombie.getMaxHealth(), "the block exploded on impact near the zombie");
            helper.assertBlockNotPresent(net.minecraft.world.level.block.Blocks.STONE_BRICKS, wallPos);
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200, batch = "mind_control")
    public static void mindControlTurnsAMonsterOnItsKind(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie zombie = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 7));
        Zombie other = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 14));
        helper.assertTrue(cast(player, "mind_control"), "mind control seizes the zombie");
        helper.assertTrue(MindManager.isControlled(zombie), "the zombie is controlled");
        zombie.setTarget(player);
        helper.assertTrue(zombie.getTarget() != player, "a controlled creature refuses to target its controller");
        helper.succeedWhen(() -> helper.assertTrue(zombie.getTarget() == other, "the controlled zombie hunts the other monster"));
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "mind_pulse")
    public static void psychicPulseShovesAndDazes(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie zombie = helper.spawn(EntityType.HUSK, new BlockPos(8, 2, 6));
        Vec3 before = zombie.position();
        helper.assertTrue(cast(player, "psychic_pulse"), "psychic pulse fires");
        helper.assertTrue(MindManager.dazed(zombie), "the zombie is dazed");
        zombie.setTarget(player);
        helper.assertTrue(zombie.getTarget() == null, "a dazed creature cannot take a target");
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(zombie.position().distanceTo(player.position()) > before.distanceTo(player.position()), "it was thrown away");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 100, batch = "mind_overload")
    public static void mentalOverloadBurstsAfterADelay(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 5f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 10));
        helper.assertTrue(cast(player, "mental_overload"), "the target is marked");
        helper.runAfterDelay(20, () -> helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "nothing happens while the mark charges"));
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(!zombie.isAlive() || zombie.getHealth() < zombie.getMaxHealth(), "the overload burst hurt it");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "mind_levitation")
    public static void levitationFieldLiftsThenReleases(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 20f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 9));
        double floor = zombie.getY();
        LevitationFieldEntity field = new LevitationFieldEntity(ModEntities.LEVITATION_FIELD.get(), helper.getLevel());
        field.setup(player, 400, 6f);
        Vec3 at = helper.absoluteVec(new Vec3(8.5, 2, 9.5));
        field.moveTo(at.x, at.y, at.z);
        helper.getLevel().addFreshEntity(field);
        helper.runAfterDelay(50, () -> {
            helper.assertTrue(zombie.getY() > floor + 1, "the field lifted the zombie");
            helper.assertTrue(cast(player, "levitation_field"), "recasting releases the field");
            helper.assertTrue(!field.isAlive(), "the field is gone");
        });
        helper.runAfterDelay(90, () -> {
            helper.assertTrue(zombie.getY() < floor + 0.5, "the zombie fell back down");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "mind_shield")
    public static void mentalShieldTurnsArrowsBack(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        helper.assertTrue(cast(player, "mental_shield"), "the shield forms");
        Skeleton archer = helper.spawnWithNoFreeWill(EntityType.SKELETON, new BlockPos(8, 2, 16));
        Arrow arrow = new Arrow(EntityType.ARROW, helper.getLevel());
        arrow.setOwner(archer);
        Vec3 from = player.position().add(0, 1, 7);
        arrow.moveTo(from.x, from.y, from.z);
        arrow.setDeltaMovement(0, 0, -1.6);
        helper.getLevel().addFreshEntity(arrow);
        helper.runAfterDelay(12, () -> {
            helper.assertTrue(arrow.getDeltaMovement().z > 0 || arrow.isRemoved(), "the arrow was turned back (vz " + arrow.getDeltaMovement().z + ")");
            helper.assertTrue(arrow.getOwner() == player || arrow.isRemoved(), "the deflected arrow now belongs to the caster");
            helper.assertTrue(player.getHealth() == player.getMaxHealth(), "the caster was not hit");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 80, batch = "mind_astral")
    public static void astralProjectionLeavesAndReturns(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        player.setGameMode(GameType.SURVIVAL);
        Vec3 home = player.position();
        helper.assertTrue(cast(player, "astral_projection"), "the projection leaves the body");
        // the GameTest mock player hard-codes isSpectator() to false, so check the game mode itself
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR, "the projection is intangible");
        helper.assertTrue(player.getData(ModAttachments.POWER_DATA).astralBody != null, "the body's location is saved for crash recovery");
        helper.assertTrue(!helper.getLevel().getEntitiesOfClass(dev.infinitygauntlet.mind.AstralBodyEntity.class, new AABB(player.blockPosition()).inflate(4)).isEmpty(),
                "the body stays behind");
        player.teleportTo(home.x + 10, home.y + 3, home.z + 6);
        MindManager.returnToBody(player);
        helper.assertTrue(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL, "the original game mode is restored");
        helper.assertTrue(player.position().distanceTo(home) < 1.5, "the player is back in the body");
        helper.assertTrue(player.getData(ModAttachments.POWER_DATA).astralBody == null, "the recovery record is cleared");
        long now = helper.getLevel().getGameTime();
        helper.assertTrue(player.getData(ModAttachments.POWER_DATA).cooldownEnd(InfinityGauntlet.id("astral_projection")) > now + 100,
                "the cooldown starts on return, not on departure");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "mind_blast")
    public static void neuralBlastHitsTheCrosshair(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 5f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 12));
        helper.assertTrue(cast(player, "neural_blast"), "neural blast charges");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(zombie.getHealth() < zombie.getMaxHealth(), "the beam struck the zombie");
            helper.succeed();
        });
    }
}
