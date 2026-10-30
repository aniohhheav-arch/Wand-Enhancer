package dev.infinitygauntlet.gametest;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.entity.power.GravityCoreEntity;
import dev.infinitygauntlet.entity.power.PowerOrbEntity;
import dev.infinitygauntlet.entity.power.SeveredHeadEntity;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.power.PowerOverload;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Server-side behaviour tests for the Power Stone (see {@link SpaceStoneGameTests} for how to run them). Each test runs
 * in its own batch: these abilities reach well past one arena and would hit the creatures of neighbouring tests.
 */
@GameTestHolder(InfinityGauntlet.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PowerStoneGameTests {
    private static Ability ability(String name) {
        return Abilities.byId(InfinityGauntlet.id(name));
    }

    /**
     * A gauntlet-wielding player at the south end of the arena looking north (+Z), {@code pitch} degrees down. The
     * arena floor is at relative y=1, so everything stands at y=2.
     */
    private static ServerPlayer caster(GameTestHelper helper, float pitch) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 2, 3.5));
        player.moveTo(start.x, start.y, start.z, 0f, pitch);
        player.setYHeadRot(0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, InfinityGauntletItem.fullGauntlet());
        return player;
    }

    private static boolean cast(ServerPlayer player, String name) {
        ItemStack gauntlet = player.getMainHandItem();
        return AbilityCaster.cast(player, InteractionHand.MAIN_HAND, gauntlet, ability(name), true);
    }

    /** Husks, not zombies: they do not burn in daylight, so any damage comes from the ability. */
    private static boolean hurt(Zombie zombie) {
        return !zombie.isAlive() || zombie.getHealth() < zombie.getMaxHealth();
    }

    @GameTest(template = "arena", batch = "power_powerStoneHasNineDistinctAbilities")
    public static void powerStoneHasNineDistinctAbilities(GameTestHelper helper) {
        List<Ability> list = Abilities.forStone(InfinityStone.POWER);
        helper.assertTrue(list.size() == 9, "the Power Stone has nine abilities (has " + list.size() + ")");
        Set<String> ids = new HashSet<>();
        for (Ability a : list) {
            ids.add(a.id().getPath());
        }
        helper.assertTrue(ids.size() == 9, "every Power Stone ability is distinct");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "power_powerBeamBurnsWhatItSweeps")
    public static void powerBeamBurnsWhatItSweeps(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 12));
        helper.assertTrue(cast(player, "power_beam"), "power beam casts");
        helper.runAfterDelay(15, () -> helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "nothing is hit while the beam charges"));
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(hurt(zombie), "the beam damaged the zombie in its path");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "power_cosmicPunchExecutesAWoundedTarget")
    public static void cosmicPunchExecutesAWoundedTarget(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 10f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 5));
        zombie.setHealth(5f);
        helper.assertTrue(cast(player, "cosmic_punch"), "cosmic punch finds the zombie");
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(!zombie.isAlive(), "the execution killed the wounded zombie");
            List<SeveredHeadEntity> heads = helper.getLevel().getEntitiesOfClass(SeveredHeadEntity.class, new AABB(zombie.blockPosition()).inflate(16));
            helper.assertTrue(!heads.isEmpty(), "the finisher knocked the zombie's head off");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "power_cosmicPunchNeedsATarget")
    public static void cosmicPunchNeedsATarget(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        helper.assertTrue(!cast(player, "cosmic_punch"), "cosmic punch fails with nothing in sight");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "power_groundbreakerRingsHitAndLaunch")
    public static void groundbreakerRingsHitAndLaunch(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie near = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 6));
        Zombie far = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 14));
        helper.assertTrue(cast(player, "groundbreaker"), "groundbreaker casts");
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(hurt(near), "the inner ring hit the near zombie");
            helper.assertTrue(hurt(far), "an outer ring reached the far zombie");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "power_energyNovaHurlsEnemiesAway")
    public static void energyNovaHurlsEnemiesAway(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 8));
        Vec3 before = zombie.position();
        helper.assertTrue(cast(player, "energy_nova"), "energy nova casts");
        helper.runAfterDelay(55, () -> {
            helper.assertTrue(hurt(zombie), "the nova damaged the zombie");
            helper.assertTrue(!zombie.isAlive() || zombie.position().distanceTo(player.position()) > before.distanceTo(player.position()),
                    "the nova threw the zombie away from the caster");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "power_cosmicArtilleryOrbsRiseAndStrike")
    public static void cosmicArtilleryOrbsRiseAndStrike(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 14));
        helper.assertTrue(cast(player, "cosmic_artillery"), "cosmic artillery casts");
        helper.runAfterDelay(20, () -> {
            List<PowerOrbEntity> orbs = helper.getLevel().getEntitiesOfClass(PowerOrbEntity.class, new AABB(player.blockPosition()).inflate(12));
            helper.assertTrue(orbs.size() == 6, "six orbs rose (" + orbs.size() + ")");
            helper.assertTrue(orbs.stream().allMatch(o -> o.getY() > player.getY() + 3), "the orbs hang above the caster");
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(hurt(zombie), "a meteor struck the locked-on zombie");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, batch = "power_gravityImpactSuspendsThenCrushes")
    public static void gravityImpactSuspendsThenCrushes(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 14));
        double floor = zombie.getY();
        GravityCoreEntity core = new GravityCoreEntity(ModEntities.GRAVITY_CORE.get(), helper.getLevel());
        core.setup(player, GravityCoreEntity.DESCEND + GravityCoreEntity.AFTER, 3f);
        core.place(helper.absoluteVec(new Vec3(8.5, 2, 14.5)), 12, 7f);
        helper.getLevel().addFreshEntity(core);
        helper.runAfterDelay(35, () -> helper.assertTrue(zombie.getY() > floor + 1, "the singularity lifted the zombie off the ground"));
        helper.runAfterDelay(GravityCoreEntity.DESCEND + 6, () -> {
            helper.assertTrue(hurt(zombie), "touchdown crushed the zombie");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 160, batch = "power_cosmicBarrageOrbitsThenFires")
    public static void cosmicBarrageOrbitsThenFires(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        helper.assertTrue(cast(player, "cosmic_barrage"), "the first cast summons the barrage");
        List<PowerOrbEntity> orbs = helper.getLevel().getEntitiesOfClass(PowerOrbEntity.class, new AABB(player.blockPosition()).inflate(8),
                PowerOrbEntity::orbiting);
        helper.assertTrue(orbs.size() == 5, "five orbs orbit the caster (" + orbs.size() + ")");
        helper.assertTrue(ability("cosmic_barrage").bypassesCooldown(new dev.infinitygauntlet.ability.AbilityContext(player, helper.getLevel(),
                player.getMainHandItem(), InteractionHand.MAIN_HAND)), "orbiting orbs can be fired while recharging");
        helper.assertTrue(cast(player, "cosmic_barrage"), "a second cast fires one orb");
        player.setShiftKeyDown(true);
        helper.runAfterDelay(2, () -> helper.assertTrue(cast(player, "cosmic_barrage"), "a sneak-cast fires the rest"));
        helper.runAfterDelay(30, () -> {
            List<PowerOrbEntity> left = helper.getLevel().getEntitiesOfClass(PowerOrbEntity.class, new AABB(player.blockPosition()).inflate(8),
                    PowerOrbEntity::orbiting);
            helper.assertTrue(left.isEmpty(), "every orb has been fired");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200, batch = "power_planetaryForceChargesThenDevastates")
    public static void planetaryForceChargesThenDevastates(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 5.6f);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 20));
        helper.assertTrue(cast(player, "planetary_force"), "planetary force casts");
        helper.runAfterDelay(40, () -> helper.assertTrue(zombie.getHealth() == zombie.getMaxHealth(), "nothing is hit during the charge"));
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(hurt(zombie), "the planetary impact devastated the zombie");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 60, batch = "power_overloadedFistsHitHarder")
    public static void overloadedFistsHitHarder(GameTestHelper helper) {
        ServerPlayer player = caster(helper, 0f);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.HUSK, new BlockPos(8, 2, 5));
        PowerOverload.start(player, 100);
        helper.assertTrue(PowerOverload.active(player), "the overload is active");
        player.attack(zombie);
        helper.assertTrue(zombie.getHealth() <= zombie.getMaxHealth() - 7, "an overloaded bare-handed blow deals bonus damage (health "
                + zombie.getHealth() + ")");
        helper.succeed();
    }
}
