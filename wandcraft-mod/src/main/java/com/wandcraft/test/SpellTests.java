package com.wandcraft.test;

import com.wandcraft.WandCraft;
import com.wandcraft.magic.CastMode;
import com.wandcraft.magic.Casting;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.magic.util.TempBlocks;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Smoke-tests every spell against a field of enemies. Only runs when the gametest namespace is enabled. */
@GameTestHolder(WandCraft.ID)
@PrefixGameTestTemplate(false)
public final class SpellTests {
    @GameTest(timeoutTicks = 9000, template = "empty", templateNamespace = WandCraft.ID)
    public static void everySpellCasts(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 spawn = h.absoluteVec(new Vec3(11.5, 2, 3.5));
        p.moveTo(spawn.x, spawn.y, spawn.z, 0f, 0f);
        PlayerMagic m = Magic.of(p);
        for (Spell s : Spells.all()) m.uses.merge(s.id, 100, Math::max);
        m.uses.put("arcane_bolt", 100000);
        List<net.minecraft.world.entity.Mob> zombies = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            var z = EntityType.ZOMBIE.create(h.getLevel());
            z.setNoAi(true);
            z.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(90);
            z.setHealth(90);
            Vec3 zp = h.absoluteVec(new Vec3(8.5 + (i % 3) * 3, 1, 10.5 + (i / 3) * 2 + (i == 4 ? -3 : 0)));
            z.moveTo(zp.x, spawn.y, zp.z, 180f, 0f);
            h.getLevel().addFreshEntity(z);
            zombies.add(z);
        }
        List<String> fired = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int i = 0;
        for (Spell s : Spells.all()) {
            final int idx = i++;
            h.runAfterDelay(idx * 50L + 5, () -> {
                m.mana = m.maxMana();
                p.setHealth(p.getMaxHealth());
                for (var z : zombies) { z.setHealth(z.getMaxHealth()); z.removeAllEffects(); z.clearFire(); }
                m.cooldownEnd.clear();
                p.moveTo(spawn.x, spawn.y, spawn.z, 0f, 0f);
                if (s.id.equals("cleansing_light")) p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 200));
                if (s.id.equals("dispel")) {
                    var foreign = new com.wandcraft.entity.SpellZone(h.getLevel(), Spells.get("rune_mine"), null, zombies.get(4).position(), 2f, 200);
                    h.getLevel().addFreshEntity(foreign);
                }
                if (s.id.equals("rewind")) { com.wandcraft.magic.TimeEvents.seed(p, 80); p.moveTo(spawn.x + 4, spawn.y, spawn.z, 0f, 0f); }
                String why = Casting.blocker(p, s);
                if (why != null) System.out.println("WANDCRAFT blocker " + s.id + ": " + why);
                boolean ok;
                if (s.mode == CastMode.CHANNEL) {
                    ok = Casting.startChannel(p, s);
                    for (int t = 0; t < 40 && ok; t++) Casting.tickChannel(p, s);
                    Casting.endChannel(p);
                } else {
                    ok = Casting.cast(p, s, 1f);
                }
                (ok ? fired : skipped).add(s.id);
                if (m.mana < 0) throw new AssertionError("negative mana after " + s.id);
            });
        }
        h.runAfterDelay(i * 50L + 200, () -> {
            System.out.println("WANDCRAFT fired=" + fired.size() + " " + fired);
            System.out.println("WANDCRAFT skipped=" + skipped.size() + " " + skipped);
            if (!skipped.isEmpty()) throw new AssertionError("spells that did not fire: " + skipped);
            if (zombies.stream().noneMatch(z -> z.getHealth() < z.getMaxHealth()) && zombies.stream().allMatch(z -> z.isAlive()))
                throw new AssertionError("no spell damaged any zombie");
            h.succeed();
        });
    }

    /** The cooldown toggle must change real casting behaviour, for every spell, without touching mana. */
    @GameTest(timeoutTicks = 200, template = "empty", templateNamespace = WandCraft.ID)
    public static void cooldownToggle(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 spawn = h.absoluteVec(new Vec3(11.5, 2, 3.5));
        p.moveTo(spawn.x, spawn.y, spawn.z, 0f, 0f);
        PlayerMagic m = Magic.of(p);
        m.uses.put("arcane_bolt", 100000);
        m.mana = m.maxMana();
        Spell s = Spells.get("arcane_bolt");
        var settings = com.wandcraft.magic.WandSettings.get(h.getLevel().getServer());
        settings.setCooldownsDisabled(false);

        if (!Casting.cast(p, s, 1f)) throw new AssertionError("first cast failed");
        if (Casting.blocker(p, s) == null) throw new AssertionError("expected a cooldown after casting");

        settings.setCooldownsDisabled(true);
        float before = m.mana;
        if (Casting.blocker(p, s) != null) throw new AssertionError("world cooldown toggle ignored: " + Casting.blocker(p, s));
        if (!Casting.cast(p, s, 1f)) throw new AssertionError("could not recast with cooldowns off");
        if (m.mana >= before) throw new AssertionError("mana cost must still apply");
        settings.setCooldownsDisabled(false);
        if (Casting.blocker(p, s) != null && !Casting.blocker(p, s).contains("recharging")) throw new AssertionError("unexpected blocker");

        // Personal override
        m.cooldownEnd.put("arcane_bolt", h.getLevel().getGameTime() + 100);
        if (Casting.blocker(p, s) == null) throw new AssertionError("expected cooldown restored");
        m.cooldownsOff = true;
        if (Casting.blocker(p, s) != null) throw new AssertionError("personal toggle ignored");
        m.cooldownsOff = false;

        // Mana still gates casting when cooldowns are off.
        settings.setCooldownsDisabled(true);
        m.mana = 0;
        if (Casting.blocker(p, s) == null) throw new AssertionError("no mana must still block");
        settings.setCooldownsDisabled(false);
        h.succeed();
    }

    /** Full Dark Clone possession: the clone hunts, the mob is removed once without drops and ash is left behind. */
    @GameTest(timeoutTicks = 900, template = "empty", templateNamespace = WandCraft.ID)
    public static void darkClonePossession(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 spawn = h.absoluteVec(new Vec3(11.5, 2, 3.5));
        p.moveTo(spawn.x, spawn.y, spawn.z, 0f, 0f);
        PlayerMagic m = Magic.of(p);
        m.uses.put("dark_clone", 100000);
        m.mana = m.maxMana();
        Spell s = Spells.get("dark_clone");

        // Protected targets must not be eligible: a boss-like blacklisted mob alone means the cast is refused.
        var golem = EntityType.IRON_GOLEM.create(h.getLevel());
        Vec3 gp = h.absoluteVec(new Vec3(11.5, 2, 8.5));
        golem.moveTo(gp.x, gp.y, gp.z, 0f, 0f);
        golem.setNoAi(true);
        h.getLevel().addFreshEntity(golem);
        p.setHealth(p.getMaxHealth());
        if (Casting.cast(p, s, 1f)) throw new AssertionError("a blacklisted mob must not be possessable");
        golem.discard();

        var z = EntityType.ZOMBIE.create(h.getLevel());
        Vec3 zp = h.absoluteVec(new Vec3(11.5, 2, 9.5));
        z.moveTo(zp.x, zp.y, zp.z, 180f, 0f);
        z.setNoAi(true);
        h.getLevel().addFreshEntity(z);
        p.setHealth(p.getMaxHealth());
        m.mana = m.maxMana();
        if (!Casting.cast(p, s, 1f)) throw new AssertionError("dark clone failed to cast: " + Casting.blocker(p, s));
        m.cooldownEnd.clear();
        m.mana = m.maxMana();
        p.setHealth(p.getMaxHealth());
        if (Casting.cast(p, s, 1f)) throw new AssertionError("only one hunting clone per caster");

        h.succeedWhen(() -> {
            h.assertTrue(!z.isAlive(), "target still alive");
            var piles = h.getLevel().getEntitiesOfClass(com.wandcraft.entity.AshPile.class, new net.minecraft.world.phys.AABB(zp, zp).inflate(4));
            h.assertTrue(!piles.isEmpty(), "no ash pile");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(zp, zp).inflate(8)).isEmpty(), "possession must not drop loot");
            h.assertTrue(h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new net.minecraft.world.phys.AABB(zp, zp).inflate(8)).isEmpty(), "possession must not drop xp");
            h.assertTrue(h.getLevel().getEntitiesOfClass(com.wandcraft.entity.DarkClone.class, new net.minecraft.world.phys.AABB(zp, zp).inflate(30)).isEmpty(), "clone should be gone");
            h.assertTrue(Math.abs(piles.get(0).getY() - zp.y) < 0.5, "ash not on ground");
        });
    }

    /** Releasing a channelled shield must remove its 3D panel. */
    @GameTest(timeoutTicks = 120, template = "empty", templateNamespace = WandCraft.ID)
    public static void shieldPropVanishes(GameTestHelper h) {
        ServerPlayer p = h.makeMockServerPlayerInLevel();
        p.setGameMode(GameType.SURVIVAL);
        Vec3 spawn = h.absoluteVec(new Vec3(11.5, 2, 3.5));
        p.moveTo(spawn.x, spawn.y, spawn.z, 0f, 0f);
        PlayerMagic m = Magic.of(p);
        m.mana = m.maxMana();
        Spell s = Spells.get("magic_shield");
        if (!Casting.startChannel(p, s)) throw new AssertionError("shield did not start");
        for (int t = 0; t < 12; t++) h.runAfterDelay(t * 2L + 1, () -> Casting.tickChannel(p, s));
        var box = new net.minecraft.world.phys.AABB(spawn, spawn).inflate(8);
        h.runAfterDelay(26, () -> {
            if (h.getLevel().getEntitiesOfClass(com.wandcraft.entity.SpellProp.class, box).isEmpty()) throw new AssertionError("expected a shield panel while channelling");
            Casting.endChannel(p);
        });
        h.runAfterDelay(60, () -> {
            h.assertTrue(h.getLevel().getEntitiesOfClass(com.wandcraft.entity.SpellProp.class, box).isEmpty(), "shield panel must vanish after release");
            h.succeed();
        });
    }
}
