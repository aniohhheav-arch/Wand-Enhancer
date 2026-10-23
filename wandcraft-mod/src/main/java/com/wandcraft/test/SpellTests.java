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
    @GameTest(timeoutTicks = 3000, template = "empty", templateNamespace = WandCraft.ID)
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
                for (var z : zombies) { z.setHealth(z.getMaxHealth()); z.removeAllEffects(); z.clearFire(); }
                m.cooldownEnd.clear();
                p.moveTo(spawn.x, spawn.y, spawn.z, 0f, 0f);
                if (s.id.equals("dispel")) {
                    var foreign = new com.wandcraft.entity.SpellZone(h.getLevel(), Spells.get("rune_mine"), null, zombies.get(4).position(), 2f, 200);
                    h.getLevel().addFreshEntity(foreign);
                }
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
}
