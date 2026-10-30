package dev.arcana.magic;

import dev.arcana.ArcanaConfig;
import dev.arcana.item.Rune;
import dev.arcana.item.WandItem;
import dev.arcana.item.WandStyle;
import dev.arcana.registry.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Everything a spell needs to know about one cast. Spell implementations only read from this. */
public final class SpellContext {
    public final ServerPlayer player;
    public final ServerLevel level;
    public final ItemStack wand;
    public final Spell spell;
    public final School school;
    public final int stage;
    public final float charge;
    public final WandStyle style;
    public final int mastery;
    /** Final outgoing-damage multiplier (mastery, stage, handle, runes, config). */
    public final float damage;
    /** Area multiplier (stage, wide rune). */
    public final float radius;

    private SpellContext(ServerPlayer player, ItemStack wand, Spell spell, float charge) {
        this.player = player;
        this.level = player.serverLevel();
        this.wand = wand;
        this.spell = spell;
        this.school = spell.isMimic() ? spell.mimicked() : spell.school;
        this.stage = WandItem.stageOf(wand);
        this.charge = Math.max(0f, Math.min(1f, charge));
        this.style = WandItem.styleOf(wand);
        this.mastery = player.getData(ModAttachments.MAGIC).level(school);
        float d = 1f + 0.05f * mastery + 0.2f * stage;
        d *= (float) style.handleType().damage;
        d *= style.runeProduct(r -> r.damage);
        d *= ArcanaConfig.DAMAGE.get().floatValue();
        d *= ArcanaConfig.SPELLS.get(spell).damage().get().floatValue();
        this.damage = d;
        this.radius = (1f + 0.12f * stage) * style.runeProduct(r -> r.radius);
    }

    public static SpellContext of(ServerPlayer player, ItemStack wand, Spell spell, float charge) {
        return new SpellContext(player, wand, spell, charge);
    }

    public boolean valid() {
        return !player.isRemoved() && player.isAlive();
    }

    public int scale(int dormant, int awakened, int ascended) {
        return stage <= 0 ? dormant : stage == 1 ? awakened : ascended;
    }

    public int rgb() {
        return WandItem.tintOf(wand, school);
    }

    public double leech() {
        return style.rune1().leech + style.rune2().leech;
    }

    public Rune rune(int i) {
        return i == 0 ? style.rune1() : style.rune2();
    }
}
