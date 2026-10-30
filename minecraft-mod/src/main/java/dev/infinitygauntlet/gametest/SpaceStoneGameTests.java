package dev.infinitygauntlet.gametest;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.entity.PortalNetwork;
import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.entity.SpatialCompressionEntity;
import dev.infinitygauntlet.entity.SpatialPrisonEntity;
import dev.infinitygauntlet.entity.SpatialRiftEntity;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.util.TeleportUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-side behaviour tests for the Space Stone: run with {@code ./gradlew runGameTestServer}.
 * Each test uses the 16×10×28 {@code arena} template (stone floor, open air).
 */
@GameTestHolder(InfinityGauntlet.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpaceStoneGameTests {
    private static Ability ability(String name) {
        return Abilities.byId(InfinityGauntlet.id(name));
    }

    @GameTest(template = "arena")
    public static void gauntletStateInstallAndSelect(GameTestHelper helper) {
        GauntletState state = GauntletState.EMPTY.withStone(InfinityStone.TIME).withStone(InfinityStone.SPACE);
        helper.assertTrue(state.stones().getFirst() == InfinityStone.SPACE, "stones are kept in socket order");
        helper.assertTrue(state.selected() == InfinityStone.TIME, "the first installed stone becomes selected");
        state = state.withSelection(InfinityStone.SPACE, 4).withoutStone(InfinityStone.SPACE);
        helper.assertTrue(state.selected() == InfinityStone.TIME, "removing the selected stone falls back to an installed one");
        helper.assertTrue(InfinityGauntletItem.state(InfinityGauntletItem.fullGauntlet()).isComplete(), "full gauntlet has all six stones");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void safeSpotNeverInsideBlocks(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 1, 8));
        Vec3 insideFloor = helper.absoluteVec(new Vec3(8.5, 0.2, 8.5));
        Vec3 safe = TeleportUtil.findSafeSpot(helper.getLevel(), zombie, insideFloor, 3);
        helper.assertTrue(safe != null, "a safe spot exists above the floor");
        helper.assertTrue(TeleportUtil.fits(helper.getLevel(), zombie, safe), "the safe spot does not collide");
        helper.assertTrue(safe.y >= helper.absoluteVec(new Vec3(0, 1, 0)).y, "the safe spot is above the floor");
        helper.succeed();
    }

    @GameTest(template = "arena")
    public static void blinkMovesForwardWithoutClipping(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 1, 3.5));
        player.moveTo(start.x, start.y, start.z, 0f, 0f); // yaw 0 = facing +Z
        ItemStack gauntlet = InfinityGauntletItem.fullGauntlet();
        player.setItemInHand(InteractionHand.MAIN_HAND, gauntlet);
        boolean cast = AbilityCaster.cast(player, InteractionHand.MAIN_HAND, gauntlet, ability("blink"), true);
        helper.assertTrue(cast, "blink succeeds in open air");
        helper.assertTrue(player.getZ() - start.z > 5, "blink moved the player forward (moved " + (player.getZ() - start.z) + ")");
        helper.assertTrue(TeleportUtil.fits(helper.getLevel(), player, player.position()), "player is not inside blocks");
        helper.succeed();
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void portalPairTransportsMobs(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Vec3 a = helper.absoluteVec(new Vec3(8.5, 2.6, 5.5));
        Vec3 b = helper.absoluteVec(new Vec3(8.5, 2.6, 22.5));
        RupturePortalEntity pa = RupturePortalEntity.create(level, a, 1.6f, new java.util.UUID(1, 1), 0, RupturePortalEntity.STYLE_PORTAL, false);
        RupturePortalEntity pb = RupturePortalEntity.create(level, b, 1.6f, new java.util.UUID(1, 1), 0, RupturePortalEntity.STYLE_PORTAL, false);
        PortalNetwork.get(level.getServer()).link(pa.getUUID(), pb.getUUID());
        helper.runAfterDelay(RupturePortalEntity.OPEN_TICKS + 5, () -> {
            helper.assertTrue(pa.isOpen() && pa.isLinked(), "portal opened and linked (phase " + pa.phase() + ", linked " + pa.isLinked()
                    + ", ticks " + pa.tickCount + ", removed " + pa.isRemoved() + ", entry " + PortalNetwork.get(level.getServer()).get(pa.getUUID()) + ")");
            Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 5));
            zombie.setPos(a.x, a.y - 1.0, a.z);
            helper.succeedWhen(() -> {
                helper.assertTrue(zombie.position().distanceTo(b) < 4.5, "zombie emerged at the partner rupture");
                PortalNetwork.get(level.getServer()).close(level.getServer(), pa.getUUID());
            });
        });
    }

    @GameTest(template = "arena", timeoutTicks = 200)
    public static void closingOnePortalCollapsesItsPair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RupturePortalEntity pa = RupturePortalEntity.create(level, helper.absoluteVec(new Vec3(4.5, 3, 5.5)), 1.2f, new java.util.UUID(2, 2), 0,
                RupturePortalEntity.STYLE_PORTAL, false);
        RupturePortalEntity pb = RupturePortalEntity.create(level, helper.absoluteVec(new Vec3(11.5, 3, 20.5)), 1.2f, new java.util.UUID(2, 2), 0,
                RupturePortalEntity.STYLE_PORTAL, false);
        PortalNetwork network = PortalNetwork.get(level.getServer());
        network.link(pa.getUUID(), pb.getUUID());
        helper.runAfterDelay(10, () -> network.close(level.getServer(), pa.getUUID()));
        helper.succeedWhen(() -> {
            helper.assertTrue(pa.isRemoved() && pb.isRemoved(), "both ruptures collapsed and were removed");
            helper.assertTrue(network.get(pa.getUUID()) == null && network.get(pb.getUUID()) == null, "network entries removed");
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120)
    public static void prisonSuspendsTarget(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(8, 1, 12));
        Vec3 center = zombie.position().add(0, zombie.getBbHeight() / 2 + 1.2, 0);
        SpatialPrisonEntity prison = new SpatialPrisonEntity(ModEntities.SPATIAL_PRISON.get(), helper.getLevel());
        prison.setup(null, 80, 1.5f);
        prison.setTarget(zombie);
        prison.moveTo(center.x, center.y, center.z);
        helper.getLevel().addFreshEntity(prison);
        double floor = helper.absoluteVec(new Vec3(0, 1, 0)).y;
        helper.runAfterDelay(40, () -> {
            helper.assertTrue(zombie.getY() > floor + 1.0, "the target is held in the air (y offset " + (zombie.getY() - floor) + ")");
            helper.assertTrue(zombie.position().distanceTo(center.subtract(0, zombie.getBbHeight() / 2, 0)) < 0.5, "the target stays centred");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 140)
    public static void riftSwallowsAndLaunchesUpward(GameTestHelper helper) {
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(8, 1, 12));
        Vec3 riftPos = helper.absoluteVec(new Vec3(8.5, 1.05, 13.5));
        SpatialRiftEntity rift = new SpatialRiftEntity(ModEntities.SPATIAL_RIFT.get(), helper.getLevel());
        rift.setup(null, 110, 3.2f);
        rift.moveTo(riftPos.x, riftPos.y, riftPos.z);
        helper.getLevel().addFreshEntity(rift);
        helper.succeedWhen(() -> helper.assertTrue(zombie.getY() > riftPos.y + 5, "the zombie reappeared high above the rift"));
    }

    @GameTest(template = "arena", timeoutTicks = 100)
    public static void compressionPullsLooseItems(GameTestHelper helper) {
        Vec3 center = helper.absoluteVec(new Vec3(8.5, 3.5, 14.5));
        SpatialCompressionEntity sphere = new SpatialCompressionEntity(ModEntities.SPATIAL_COMPRESSION.get(), helper.getLevel());
        sphere.setup(null, 80, 2.6f);
        sphere.moveTo(center.x, center.y, center.z);
        helper.getLevel().addFreshEntity(sphere);
        Vec3 itemPos = center.add(6, -2.4, 0);
        ItemEntity item = new ItemEntity(helper.getLevel(), itemPos.x, itemPos.y, itemPos.z, new ItemStack(Items.DIAMOND));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        double startDist = item.position().distanceTo(center);
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(item.position().distanceTo(center) < startDist - 2, "the item was dragged toward the sphere");
            helper.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 120, skyAccess = true)
    public static void cosmicGatewayOpensInTheSky(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 start = helper.absoluteVec(new Vec3(8.5, 1, 2.5));
        player.moveTo(start.x, start.y, start.z, 0f, 20f); // looking slightly down the arena
        ItemStack gauntlet = InfinityGauntletItem.fullGauntlet();
        player.setItemInHand(InteractionHand.MAIN_HAND, gauntlet);
        boolean cast = AbilityCaster.cast(player, InteractionHand.MAIN_HAND, gauntlet, ability("cosmic_gateway"), true);
        helper.assertTrue(cast, "gateway cast succeeds under open sky");
        helper.runAfterDelay(50, () -> {
            java.util.List<RupturePortalEntity> portals = helper.getLevel().getEntitiesOfClass(RupturePortalEntity.class,
                    player.getBoundingBox().inflate(64), p -> {
                        PortalNetwork.Entry e = PortalNetwork.get(helper.getLevel().getServer()).get(p.getUUID());
                        return e != null && e.owner().equals(player.getUUID());
                    });
            long gateways = portals.stream().filter(p -> p.style() == RupturePortalEntity.STYLE_GATEWAY).count();
            helper.assertTrue(gateways == 1, "one sky gateway exists (portals found: " + portals.size() + ")");
            RupturePortalEntity gateway = portals.stream().filter(p -> p.style() == RupturePortalEntity.STYLE_GATEWAY).findFirst().orElseThrow();
            helper.assertTrue(gateway.getY() > start.y + 10, "the gateway hangs high above the ground");
            // close only this test's ruptures: other tests' portals can be within range on the shared test grid
            PortalNetwork network = PortalNetwork.get(helper.getLevel().getServer());
            network.ownedBy(player.getUUID()).forEach(e -> network.close(helper.getLevel().getServer(), e.id()));
            helper.succeed();
        });
    }
}
