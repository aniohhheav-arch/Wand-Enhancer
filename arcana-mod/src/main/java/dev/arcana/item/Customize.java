package dev.arcana.item;

import dev.arcana.ArcanaConfig;
import dev.arcana.magic.CastService;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.School;
import dev.arcana.net.Net;
import dev.arcana.registry.ModAttachments;
import dev.arcana.registry.ModComponents;
import dev.arcana.registry.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Server-side wand customisation and evolution. Item costs are validated and consumed here, never on the client. */
public final class Customize {
    public static final int HANDLE = 0, COLOR = 1, CORE = 2, FX = 3, RUNE1 = 4, RUNE2 = 5;

    /** The item that must be consumed to select {@code value} for {@code field}; null means free. */
    public static Item costOf(int field, int value) {
        return switch (field) {
            case HANDLE -> value == 0 ? null : BuiltInRegistries.ITEM.get(Handle.byIndex(value).item);
            case COLOR -> value < 0 || value > 15 ? null : DyeItem.byColor(DyeColor.byId(value));
            case CORE -> value == 0 ? null : BuiltInRegistries.ITEM.get(Core.byIndex(value).item);
            case RUNE1, RUNE2 -> value == 0 ? null : BuiltInRegistries.ITEM.get(Rune.byIndex(value).item);
            default -> null;
        };
    }

    public static int optionCount(int field) {
        return switch (field) {
            case HANDLE -> Handle.values().length;
            case COLOR -> 17; // -1 (school) + 16 dyes
            case CORE -> Core.values().length;
            case FX -> Fx.values().length;
            default -> Rune.values().length;
        };
    }

    public static void apply(ServerPlayer p, int field, int value) {
        ItemStack wand = CastService.heldWand(p);
        if (wand.isEmpty() || field < 0 || field >= WandStyle.FIELDS) return;
        if (value < (field == COLOR ? -1 : 0) || value >= optionCount(field) - (field == COLOR ? 1 : 0)) return;
        WandStyle style = WandItem.styleOf(wand);
        int old = style.get(field);
        if (old == value) return;
        if ((field == RUNE1 && value != 0 && value == style.mod2()) || (field == RUNE2 && value != 0 && value == style.mod1())) {
            p.displayClientMessage(Component.translatable("customize.arcana.duplicate_rune"), true);
            return;
        }
        Item need = costOf(field, value);
        if (need != null && !p.isCreative() && !consume(p, need, 1)) {
            p.displayClientMessage(Component.translatable("customize.arcana.missing", need.getDescription()), true);
            return;
        }
        Item refund = costOf(field, old);
        if (refund != null && !p.isCreative()) p.getInventory().placeItemBackInInventory(new ItemStack(refund));
        wand.set(ModComponents.STYLE.get(), style.with(field, value));
        p.level().playSound(null, p.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, net.minecraft.sounds.SoundSource.PLAYERS, 1f, 1.3f);
    }

    public static void evolve(ServerPlayer p) {
        ItemStack wand = CastService.heldWand(p);
        if (wand.isEmpty()) return;
        School school = WandItem.schoolOf(wand);
        int stage = WandItem.stageOf(wand);
        if (school == School.NONE || stage >= 2) return;
        PlayerMagic m = ModAttachmentsAccess.magic(p);
        int needMastery = stage == 0 ? ArcanaConfig.AWAKEN_MASTERY.get() : ArcanaConfig.ASCEND_MASTERY.get();
        int needCrystals = stage == 0 ? ArcanaConfig.AWAKEN_CRYSTALS.get() : ArcanaConfig.ASCEND_CRYSTALS.get();
        Item rare = stage == 0 ? ModItems.AWAKENING_SHARD.get() : ModItems.ASCENSION_CORE.get();
        Item crystal = ModItems.CRYSTALS.get(school).get();
        if (m.level(school) < needMastery) {
            p.displayClientMessage(Component.translatable("evolve.arcana.fail.mastery", needMastery), true);
            return;
        }
        if (!p.isCreative() && (count(p, rare) < 1 || count(p, crystal) < needCrystals)) {
            p.displayClientMessage(Component.translatable("evolve.arcana.fail.items"), true);
            return;
        }
        if (!p.isCreative()) {
            consume(p, rare, 1);
            consume(p, crystal, needCrystals);
        }
        wand.set(ModComponents.STAGE.get(), stage + 1);
        var level = p.serverLevel();
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1.2, p.getZ(), 60, 0.5, 0.8, 0.5, 0.4);
        level.sendParticles(School.dust(school.color, 2f), p.getX(), p.getY() + 1.2, p.getZ(), 40, 0.6, 0.9, 0.6, 0);
        level.playSound(null, p.blockPosition(), SoundEvents.TOTEM_USE, net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 1.2f);
        Net.cinematic(p, school.color, 30, 0, -1);
        p.displayClientMessage(Component.translatable("evolve.arcana.success." + (stage + 1)), true);
    }

    public static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (ItemStack s : p.getInventory().items) if (s.is(item)) n += s.getCount();
        for (ItemStack s : p.getInventory().offhand) if (s.is(item)) n += s.getCount();
        return n;
    }

    public static boolean consume(ServerPlayer p, Item item, int amount) {
        if (count(p, item) < amount) return false;
        int left = amount;
        for (var list : new java.util.List[] {p.getInventory().items, p.getInventory().offhand}) {
            for (Object o : list) {
                ItemStack s = (ItemStack) o;
                if (s.is(item) && left > 0) {
                    int t = Math.min(left, s.getCount());
                    s.shrink(t);
                    left -= t;
                }
            }
        }
        return true;
    }

    /** Small indirection so the item package doesn't depend on attachment wiring details. */
    private static final class ModAttachmentsAccess {
        static PlayerMagic magic(ServerPlayer p) {
            return p.getData(ModAttachments.MAGIC);
        }
    }

    private Customize() {}
}
