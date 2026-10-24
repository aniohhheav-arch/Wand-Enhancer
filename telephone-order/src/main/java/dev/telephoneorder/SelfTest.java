package dev.telephoneorder;

import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.delivery.DeliveryDirector;
import dev.telephoneorder.net.Payloads;
import dev.telephoneorder.phone.Account;
import dev.telephoneorder.phone.Listing;
import dev.telephoneorder.phone.PhoneService;
import dev.telephoneorder.weapon.*;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.commands.arguments.EntityAnchorArgument;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless integration test, only active with -Dtelephoneorder.selftest=true. It drives a fake player through every weapon,
 * the telephone, the workbench and all five delivery sequences on a real dedicated server and prints PASS/FAIL lines.
 */
final class SelfTest {
    private SelfTest() {}

    private static final List<String> FAILS = new ArrayList<>();
    private static int checks;
    private static MinecraftServer server;
    private static ServerLevel level;
    private static FakePlayer fake;
    private static IronGolem golem;
    private static int cursor = 0;

    static void init() {
        if (!Boolean.getBoolean("telephoneorder.selftest")) return;
        ServerLifecycleEvents.SERVER_STARTED.register(s -> { server = s; Sched.later(80, SelfTest::begin); });
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) { FAILS.add(what); TelephoneOrder.LOGGER.error("SELFTEST FAIL: {}", what); }
    }

    private static void log(String s) { TelephoneOrder.LOGGER.info("SELFTEST {}", s); }

    private static void begin() {
        level = server.overworld();
        ModConfig.get().deliveryTimeMultiplier = 0.05f;
        fake = FakePlayer.get(level);
        BlockPos base = new BlockPos(0, 0, 0);
        for (int cx = -4; cx <= 4; cx++) for (int cz = -4; cz <= 4; cz++) level.setChunkForced(cx, cz, true);
        for (int x = -48; x <= 48; x++) for (int z = -48; z <= 48; z++) level.setBlock(new BlockPos(x, 63, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
        int y = 64;
        log("test platform built, standing y=" + y);
        fake.setPos(0.5, y, 0.5);
        golem = new IronGolem(EntityTypes.IRON_GOLEM, level);
        golem.setPos(0.5, y, 12.5);
        golem.setNoAi(true);
        golem.setPersistenceRequired();
        level.addFreshEntity(golem);
        Account.of(fake);
        Account.add(fake, 200000);
        { int slot = 9; for (var e : ModItems.AMMO.values()) fake.getInventory().setItem(slot++, new ItemStack(e, 64)); }
        gunStep();
    }

    private static void aim() {
        fake.lookAt(EntityAnchorArgument.Anchor.EYES, golem.getBoundingBox().getCenter());
    }

    private static void gunStep() {
        if (cursor >= Catalog.GUNS.size()) { workbenchTest(); return; }
        GunDef def = Catalog.GUNS.get(cursor++);
        GunItem item = ModItems.GUNS.get(def.id());
        ItemStack stack = GunData.newStack(def, item);
        fake.setItemSlot(EquipmentSlot.MAINHAND, stack);
        golem.setHealth(golem.getMaxHealth());
        aim();
        float hp0 = golem.getHealth();
        int ammo0 = GunData.ammo(stack);
        GunLogic.PS ps = GunLogic.ps(fake);
        ps.nextFire = 0;
        try {
            if (def.beh().equals("turret")) { runTurretTest(def, stack); return; }
            var res = GunLogic.onUse(fake, stack, item, InteractionHand.MAIN_HAND);
            String hold = GunLogic.holdType(def);
            int ticks = hold == null ? 6 : (hold.equals("charge") ? (int) def.p("charge", 40) + 4 : hold.equals("smart") ? 30 : hold.equals("rail") ? (int) def.p("charge", 14) + 4 : 40);
            int[] t = {0};
            Runnable[] loop = new Runnable[1];
            loop[0] = () -> {
                try {
                    aim();
                    GunLogic.tickInventory(fake, stack, item, true);
                    if (hold != null) {
                        if (fake.isUsingItem()) GunLogic.onUseTick(fake, stack, item, 0);
                    } else if (t[0] % Math.max(2, def.delay()) == 0 && t[0] > 0) {
                        ps.nextFire = 0;
                        GunLogic.onUse(fake, stack, item, InteractionHand.MAIN_HAND);
                    }
                } catch (Throwable ex) { check(false, def.id() + " threw " + ex); ex.printStackTrace(); }
                if (++t[0] < ticks) Sched.later(1, loop[0]);
                else finishGun(def, item, stack, hp0, ammo0, hold);
            };
            Sched.later(1, loop[0]);
        } catch (Throwable ex) {
            check(false, def.id() + " threw " + ex);
            ex.printStackTrace();
            Sched.later(1, SelfTest::gunStep);
        }
    }

    private static void finishGun(GunDef def, GunItem item, ItemStack stack, float hp0, int ammo0, String hold) {
        try {
            if (hold != null) { GunLogic.onRelease(fake, stack, item); fake.stopUsingItem(); }
        } catch (Throwable ex) { check(false, def.id() + " release threw " + ex); ex.printStackTrace(); }
        // let projectiles / fields settle
        Sched.later(45, () -> {
            float dmg = hp0 - golem.getHealth();
            int ammo1 = GunData.ammo(stack);
            boolean offensive = !def.beh().equals("shield") && !def.beh().equals("gravity") && !def.beh().equals("cryo");
            boolean spent = ammo1 < ammo0;
            check(spent, def.id() + " did not consume ammo");
            if (offensive) check(dmg > 0.01f || def.beh().equals("smart") || def.beh().equals("unstable"), def.id() + " dealt no damage (" + dmg + ")");
            log(String.format("%-18s beh=%-11s ammo %d->%d  damage dealt %.1f  golem@%.1f,%.1f,%.1f hp=%.0f alive=%s fake@%.1f,%.1f,%.1f yaw=%.0f pitch=%.0f", def.id(), def.beh(), ammo0, ammo1, dmg,
                    golem.getX(), golem.getY(), golem.getZ(), golem.getHealth(), golem.isAlive(), fake.getX(), fake.getY(), fake.getZ(), fake.getYRot(), fake.getXRot()));
            // reload check
            GunData.edit(stack, t -> t.putInt("ammo", 0));
            GunLogic.startReload(fake, stack, def);
            boolean reloading = GunData.reloading(stack);
            check(reloading, def.id() + " reload did not start");
            int dur = (int) (def.reload() * (def.perRound() ? def.mag() : 1)) + 8;
            int[] t = {0};
            Runnable[] loop = new Runnable[1];
            loop[0] = () -> {
                try { GunLogic.tickInventory(fake, stack, item, true); } catch (Throwable ex) { check(false, def.id() + " reload tick threw " + ex); }
                if (++t[0] < dur && GunData.reloading(stack)) Sched.later(1, loop[0]);
                else {
                    check(GunData.ammo(stack) == GunStats.of(def, stack).mag || def.perRound() && GunData.ammo(stack) > 0, def.id() + " reload ended with ammo=" + GunData.ammo(stack));
                    Sched.later(2, SelfTest::gunStep);
                }
            };
            Sched.later(1, loop[0]);
        });
    }

    private static void runTurretTest(GunDef def, ItemStack stack) {
        BlockPos p = new BlockPos(5, 64, 5);
        level.setBlock(p, ModBlocks.SENTRY.defaultBlockState(), 3);
        check(level.getBlockEntity(p) instanceof dev.telephoneorder.block.SentryBlockEntity, "sentry block entity missing");
        log("turret placed ok");
        level.removeBlock(p, false);
        Sched.later(2, SelfTest::gunStep);
    }

    // ------------------------------------------------------------------------------------------------ workbench
    private static void workbenchTest() {
        try {
            GunDef def = Catalog.GUN_BY_ID.get("chameleon_mod");
            ItemStack stack = GunData.newStack(def, ModItems.GUNS.get(def.id()));
            fake.setItemSlot(EquipmentSlot.MAINHAND, stack);
            { int sl = 22; for (var a : Catalog.ATTS) { if (sl < 36) fake.getInventory().setItem(sl++, new ItemStack(ModItems.ATTACHMENTS.get(a.id()), 2)); } }
            int fitted = 0;
            for (var a : Catalog.ATTS) {
                if (!Catalog.fits(a, def)) continue;
                ServerSetup.workbench(fake, new Payloads.WorkbenchAction("attach", a.slot(), a.id()));
                check(GunData.attachment(stack, a.slot()).equals(a.id()), "attach failed " + a.id());
                fitted++;
            }
            GunStats st = GunStats.of(def, stack);
            log("workbench: fitted " + fitted + " attachments, mag=" + st.mag + " spread x" + st.spread + " recoil x" + st.recoil);
            check(fitted >= 7, "too few attachments fit chameleon");
            // incompatible attach must be refused
            ServerSetup.workbench(fake, new Payloads.WorkbenchAction("attach", "optic", "scope_8x"));
            ItemStack gp = GunData.newStack(Catalog.GUN_BY_ID.get("vesper_p9"), ModItems.GUNS.get("vesper_p9"));
            fake.setItemSlot(EquipmentSlot.MAINHAND, gp);
            ServerSetup.workbench(fake, new Payloads.WorkbenchAction("attach", "optic", "scope_8x"));
            check(GunData.attachment(gp, "optic").isEmpty(), "8x scope wrongly fitted to a pistol");
            ServerSetup.workbench(fake, new Payloads.WorkbenchAction("attach", "muzzle", "suppressor"));
            check(GunData.attachment(gp, "muzzle").equals("suppressor"), "suppressor not fitted to pistol");
            ServerSetup.workbench(fake, new Payloads.WorkbenchAction("detach", "muzzle", ""));
            check(GunData.attachment(gp, "muzzle").isEmpty(), "detach failed");
        } catch (Throwable ex) { check(false, "workbench threw " + ex); ex.printStackTrace(); }
        Sched.later(5, SelfTest::phoneTest);
    }

    // ------------------------------------------------------------------------------------------------ phone
    private static void phoneTest() {
        try {
            int y = 64;
            BlockPos pos = new BlockPos(3, y, 0);
            level.setBlock(pos, ModBlocks.TELEPHONE_ROTARY.defaultBlockState(), 3);
            fake.setPos(2.5, y, 1.5);
            ((dev.telephoneorder.block.TelephoneBlock) ModBlocks.TELEPHONE_ROTARY).getClass();
            PhoneService.interact(fake, level, pos, (dev.telephoneorder.block.TelephoneBlock) ModBlocks.TELEPHONE_ROTARY);
            check(level.getBlockState(pos).getValue(dev.telephoneorder.block.TelephoneBlock.PHONE).offHook(), "phone did not go off hook");
            Account.of(fake).putInt("spent", 0);
            PhoneService.handle(fake, "dial", "555-0113");
            check(!Account.has(fake, "proto_lab"), "prototype lab should deny low-clearance");
            Account.of(fake).putInt("spent", 1500);
            PhoneService.handle(fake, "dial", "555-0113");
            PhoneService.handle(fake, "choice", "1");
            check(Account.has(fake, "proto_lab"), "prototype lab unlock failed");
            PhoneService.handle(fake, "dial", "555-0666");
            check(!Account.has(fake, "line_nether"), "nether line must not work in the overworld");
            PhoneService.handle(fake, "dial", "555-0101");
            check(Account.has(fake, "line_overworld"), "overworld line failed in daylight");
            PhoneService.handle(fake, "dial", "481-5162");
            check(Account.has(fake, "archive"), "archive number failed");
            PhoneService.handle(fake, "dial", "000-0000");
            PhoneService.handle(fake, "choice", "0");   // hang up -> eerie
            Sched.later(5, () -> {
                // operator flow after the eerie event
                Account.of(fake).putInt("credits", 5000);
                PhoneService.interact(fake, level, pos, (dev.telephoneorder.block.TelephoneBlock) ModBlocks.TELEPHONE_ROTARY);
                PhoneService.handle(fake, "dial", "555-0100");
                PhoneService.handle(fake, "deposit", "minecraft:copper_ingot");
                // order one of everything type
                int before = Account.of(fake).getListOrEmpty("orders").size();
                PhoneService.handle(fake, "order", "vesper_p9");
                PhoneService.handle(fake, "order", "red_dot");
                PhoneService.handle(fake, "order", "ammo_9mm");
                check(Account.of(fake).getListOrEmpty("orders").size() == before + 3, "orders were not placed");
                PhoneService.handle(fake, "order", "void_whisper");
                check(Account.of(fake).getListOrEmpty("orders").size() == before + 3, "locked secret order should be refused");
                PhoneService.handle(fake, "hangup", "");
                log("phone: orders placed, waiting for scripted deliveries");
                Sched.later(10, SelfTest::deliveryTest);
            });
        } catch (Throwable ex) { check(false, "phone threw " + ex); ex.printStackTrace(); Sched.later(5, SelfTest::deliveryTest); }
    }

    // ------------------------------------------------------------------------------------------------ deliveries
    private static final String[][] DELIVERIES = {{"crate", "ammo_9mm"}, {"drone", "anvil_br7"}, {"pod", "stormcaller_rl"}, {"teleport", "sunder_pr"}, {"case", "meridian_rail"}};
    private static int dcur;

    private static void deliveryTest() {
        if (dcur >= DELIVERIES.length) { finish(); return; }
        String[] d = DELIVERIES[dcur++];
        Listing li = Listing.BY_ID.get(d[1]);
        int before = countItems();
        log("delivery sequence: " + d[0] + " -> " + li.name());
        boolean[] done = {false};
        try {
            DeliveryDirector.start(fake, li, d[0], () -> done[0] = true, 900 + dcur);
        } catch (Throwable ex) { check(false, "delivery " + d[0] + " threw " + ex); ex.printStackTrace(); }
        Sched.later(420, () -> {
            check(done[0], "delivery " + d[0] + " never released its items");
            int after = countItems();
            check(after > before, "delivery " + d[0] + " produced no item entities");
            log("delivery " + d[0] + " produced " + (after - before) + " item entities");
            Sched.later(2, SelfTest::deliveryTest);
        });
    }

    private static int countItems() {
        return level.getEntitiesOfClass(ItemEntity.class, new AABB(-80, -64, -80, 80, 320, 80)).size();
    }

    private static void finish() {
        log("checks run: " + checks + ", failures: " + FAILS.size());
        for (String f : FAILS) log("FAILED: " + f);
        TelephoneOrder.LOGGER.info(FAILS.isEmpty() ? "SELFTEST RESULT: PASS" : "SELFTEST RESULT: FAIL");
        server.halt(false);
    }
}
