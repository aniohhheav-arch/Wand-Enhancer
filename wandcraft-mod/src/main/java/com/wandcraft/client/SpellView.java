package com.wandcraft.client;

import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Rank;
import com.wandcraft.magic.Spell;
import com.wandcraft.net.SelectSpellPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Read-only client helpers over the synced magic state. */
final class SpellView {
    private SpellView() {}

    static Player player() { return Minecraft.getInstance().player; }

    static PlayerMagic magic() { return Magic.of(player()); }

    static boolean unlocked(Spell s) { return Magic.isUnlocked(player(), s); }

    static Rank rank(Spell s) { return magic().rank(s.id); }

    static int cost(Spell s) { return Magic.cost(player(), s); }

    static float cooldownFraction(Spell s) {
        long end = magic().cooldownEnd.getOrDefault(s.id, 0L);
        long now = player().level().getGameTime();
        if (end <= now) return 0;
        float total = Math.max(2f, s.cooldown * rank(s).cooldownMul);
        return Math.min(1f, (end - now) / total);
    }

    /** Progress toward the next rank, 1 when Master. */
    static float rankProgress(Spell s) {
        int uses = magic().uses.getOrDefault(s.id, 0);
        Rank r = Rank.forUses(uses);
        if (r == Rank.MASTER) return 1f;
        Rank next = r == Rank.NOVICE ? Rank.ADEPT : Rank.MASTER;
        return (uses - r.uses) / (float) (next.uses - r.uses);
    }

    static boolean hasEvolution(Spell s) {
        for (Spell o : com.wandcraft.magic.Spells.all()) if (s.id.equals(o.evolvesFrom)) return true;
        return false;
    }

    static void select(Spell s) {
        if (!unlocked(s)) return;
        magic().selected = s.id;
        PacketDistributor.sendToServer(new SelectSpellPayload(s.id));
    }
}
