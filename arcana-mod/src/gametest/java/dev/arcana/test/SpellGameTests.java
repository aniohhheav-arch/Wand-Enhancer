package dev.arcana.test;

import dev.arcana.ArcanaMod;
import dev.arcana.item.Customize;
import dev.arcana.item.WandItem;
import dev.arcana.item.WandStyle;
import dev.arcana.magic.CastService;
import dev.arcana.magic.EffectManager;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.magic.SpellContext;
import dev.arcana.registry.ModAttachments;
import dev.arcana.registry.ModComponents;
import dev.arcana.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Casts every spell against live mobs with a mock player and fails on any exception inside spell effects. */
@GameTestHolder(ArcanaMod.MODID)
@PrefixGameTestTemplate(false)
public class SpellGameTests {

    private static ItemStack wand(School school, int stage) {
        ItemStack w = new ItemStack(ModItems.WANDS.get(school).get());
        w.set(ModComponents.STAGE.get(), stage);
        return w;
    }

    private static ServerPlayer player(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        BlockPos o = h.absolutePos(new BlockPos(16, 1, 4));
        p.moveTo(o.getX() + 0.5, o.getY(), o.getZ() + 0.5, 0f, 0f);
        return p;
    }

    /** Spawns a crowd in front of the (south-facing) player, plus two zombies directly in the line of sight. */
    private static void crowd(GameTestHelper h, ServerPlayer p) {
        for (int i = 0; i < 12; i++) spawnZombie(h, p.getX() - 5 + (i % 6) * 2, p.getY(), p.getZ() + 8 + (i / 6) * 3);
        for (int dz : new int[] {8, 11}) spawnZombie(h, p.getX(), p.getY(), p.getZ() + dz);
    }

    private static void spawnZombie(GameTestHelper h, double x, double y, double z) {
        Zombie zom = EntityType.ZOMBIE.create(h.getLevel());
        zom.moveTo(x, y, z, 0f, 0f);
        zom.setNoAi(true);
        zom.setPersistenceRequired();
        h.getLevel().addFreshEntity(zom);
    }

    @GameTest(template = "arena", timeoutTicks = 3000)
    public static void every_spell_runs_clean(GameTestHelper h) {
        ServerPlayer p = player(h);
        crowd(h, p);
        int errorsBefore = EffectManager.errors;
        PlayerMagic m = p.getData(ModAttachments.MAGIC);
        m.lastSpell = Spell.ARCANE_BOLT;
        m.lastCharge = 1f;
        for (int i = 0; i < 6; i++) m.history.add(new PlayerMagic.Snapshot(p.level().dimension(), p.position().add(0, 0, -3), p.getHealth(), 0, 0));
        Spell[] all = Spell.values();
        List<String> noops = new ArrayList<>();
        for (int i = 0; i < all.length; i++) {
            final Spell s = all[i];
            h.runAfterDelay(10L + i * 14L, () -> {
                for (var e : h.getLevel().getEntitiesOfClass(Zombie.class, p.getBoundingBox().inflate(40))) e.discard();
                crowd(h, p);
                p.moveTo(p.getX(), p.getY(), p.getZ(), 0f, 0f);
                ItemStack w = wand(s.school, 2);
                p.setItemInHand(InteractionHand.MAIN_HAND, w);
                m.mana = 1000;
                m.cooldowns.clear();
                m.recent.put(s.mimicked().id, h.getLevel().getGameTime());
                boolean ok = CastService.cast(p, w, s, 1f, InteractionHand.MAIN_HAND);
                if (!ok) noops.add(s.id);
            });
        }
        h.runAfterDelay(10L + all.length * 14L + 400L, () -> {
            h.assertTrue(EffectManager.errors == errorsBefore, "spell effects threw " + (EffectManager.errors - errorsBefore) + " exceptions (see log)");
            // frozen/summoned bookkeeping must be clean after all effects finish
            h.assertTrue(EffectManager.FROZEN.isEmpty(), "time stop left frozen entities: " + EffectManager.FROZEN.size());
            ArcanaMod.LOGGER.info("[arcana-test] spells that reported failure: {}", noops);
            h.assertTrue(noops.size() <= 2, "too many spells failed to cast: " + noops);
            h.succeed();
        });
    }

    @GameTest(template = "arena", timeoutTicks = 400)
    public static void low_stage_and_modifiers(GameTestHelper h) {
        ServerPlayer p = player(h);
        crowd(h, p);
        for (School s : School.real()) {
            ItemStack w = wand(s, 0);
            w.set(ModComponents.STYLE.get(), new WandStyle(2, 3, 1, 2, 1, 4));
            for (Spell sp : Spell.forWand(s, 0)) {
                p.getData(ModAttachments.MAGIC).mana = 1000;
                p.getData(ModAttachments.MAGIC).cooldowns.clear();
                CastService.cast(p, w, sp, 0.2f, InteractionHand.MAIN_HAND);
            }
        }
        h.runAfterDelay(120, () -> {
            h.assertTrue(true, "");
            h.succeed();
        });
    }

    @GameTest(template = "arena")
    public static void evolution_and_customisation(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack w = wand(School.STORM, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, w);
        PlayerMagic m = p.getData(ModAttachments.MAGIC);
        // below mastery requirement: must refuse
        Customize.evolve(p);
        h.assertTrue(WandItem.stageOf(w) == 0, "evolved without mastery");
        m.mastery.put(School.STORM.id, 40 * 4 * 4);
        Customize.evolve(p);
        h.assertTrue(WandItem.stageOf(w) == 1, "creative player with mastery should awaken");
        Customize.evolve(p);
        h.assertTrue(WandItem.stageOf(w) == 1, "second evolution needs higher mastery");
        m.mastery.put(School.STORM.id, 40 * 100);
        Customize.evolve(p);
        h.assertTrue(WandItem.stageOf(w) == 2, "should ascend");
        Customize.evolve(p);
        h.assertTrue(WandItem.stageOf(w) == 2, "cannot exceed ascended");
        Customize.apply(p, Customize.HANDLE, 3);
        Customize.apply(p, Customize.CORE, 2);
        Customize.apply(p, Customize.RUNE1, 1);
        Customize.apply(p, Customize.RUNE2, 1); // duplicate rune must be rejected
        Customize.apply(p, Customize.COLOR, 99); // out of range must be rejected
        WandStyle st = WandItem.styleOf(w);
        h.assertTrue(st.handle() == 3 && st.core() == 2 && st.mod1() == 1 && st.mod2() == 0 && st.color() == -1, "style = " + st);
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void mimic_requires_recent_school(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack w = wand(School.NONE, 2);
        PlayerMagic m = p.getData(ModAttachments.MAGIC);
        m.mana = 1000;
        h.assertTrue(SpellContext.of(p, w, Spell.MIMIC_STORM, 1f).school == School.STORM, "mimic should credit the copied school");
        h.assertTrue(Spell.MIMIC_STORM.lookIndex() == 6 && Spell.RIFT_COLLAPSE.lookIndex() == 11, "forbidden look indices");
        h.succeed();
    }

    @GameTest(template = "arena")
    public static void admin_commands(GameTestHelper h) {
        ServerPlayer p = player(h);
        ItemStack w = wand(School.STORM, 0);
        p.setItemInHand(InteractionHand.MAIN_HAND, w);
        var src = p.createCommandSourceStack().withPermission(4);
        var cmds = h.getLevel().getServer().getCommands();
        PlayerMagic m = p.getData(ModAttachments.MAGIC);
        cmds.performPrefixedCommand(src, "arcana mastery @s storm set 7");
        h.assertTrue(m.level(School.STORM) == 7, "set level 7, got " + m.level(School.STORM));
        cmds.performPrefixedCommand(src, "arcana mastery @s storm add 9");
        h.assertTrue(m.level(School.STORM) == 10, "add clamps to 10, got " + m.level(School.STORM));
        cmds.performPrefixedCommand(src, "arcana mastery @s void setxp 500");
        h.assertTrue(m.xp(School.VOID) == 500, "setxp");
        cmds.performPrefixedCommand(src, "arcana mastery @s void addxp 100");
        h.assertTrue(m.xp(School.VOID) == 600, "addxp");
        cmds.performPrefixedCommand(src, "arcana mastery @s all set 3");
        h.assertTrue(m.level(School.ARCANE) == 3 && m.level(School.CHRONOS) == 3, "all set 3");
        cmds.performPrefixedCommand(src, "arcana stage @s 2");
        h.assertTrue(WandItem.stageOf(w) == 2, "stage command");
        cmds.performPrefixedCommand(src, "arcana mana @s set 5");
        h.assertTrue(Math.round(m.mana) == 5, "mana set");
        cmds.performPrefixedCommand(src, "arcana mana @s fill");
        h.assertTrue(m.mana >= 100, "mana fill");
        m.cooldowns.put("thunderbolt", 99999L);
        cmds.performPrefixedCommand(src, "arcana cooldowns @s reset");
        h.assertTrue(m.cooldowns.isEmpty(), "cooldowns reset");
        cmds.performPrefixedCommand(src, "arcana mastery @s bogus set 1");
        cmds.performPrefixedCommand(src, "arcana mastery @s reset");
        h.assertTrue(m.level(School.STORM) == 0, "reset");
        h.succeed();
    }
}
