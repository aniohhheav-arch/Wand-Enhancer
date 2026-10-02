package dev.riftverse.fusion;

import dev.riftverse.item.ItemData;
import dev.riftverse.multiverse.RealityOps;
import dev.riftverse.multiverse.UniverseDna;
import dev.riftverse.registry.RvParticles;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseSpec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** A Universe Sample: use an empty one inside a universe to extract its full DNA; filled samples feed the Fusion Engine. */
public class UniverseSampleItem extends Item {
    public UniverseSampleItem(Properties p) {
        super(p);
    }

    @Nullable
    public static UniverseSpec spec(ItemStack stack) {
        CompoundTag t = ItemData.read(stack);
        return t.contains("dna_spec") ? UniverseSpec.load(t.getCompound("dna_spec")) : null;
    }

    public static void fill(ItemStack stack, UniverseSpec spec) {
        ItemData.edit(stack, t -> {
            t.put("dna_spec", spec.save());
            t.putString("dna_name", spec.name);
            t.putString("dna_code", UniverseDna.encode(spec));
            t.putString("dna_type", spec.archetype.displayName);
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        if (spec(stack) != null) {
            sp.displayClientMessage(Component.literal("This sample is already full. Combine it at a Fusion Engine.").withColor(0xB0A0FF), true);
            return InteractionResultHolder.fail(stack);
        }
        UniverseId id = RealityOps.universeOf(sp);
        if (id == null || dev.riftverse.multiverse.InfiniteCorridor.isCorridor(id)) {
            sp.displayClientMessage(Component.literal("Samples can only be drawn from inside a universe of the Expanse.").withColor(0xFF6070), true);
            return InteractionResultHolder.fail(stack);
        }
        UniverseSpec spec = RealityOps.spec(sp.server, id);
        ItemStack filled = stack.split(1);
        fill(filled, spec);
        if (!sp.getInventory().add(filled)) sp.drop(filled, false);
        sp.serverLevel().sendParticles(RvParticles.RING.get().with(spec.accent, 2f, 16), sp.getX(), sp.getY() + 1, sp.getZ(), 1, 0, 0, 0, 0);
        sp.serverLevel().sendParticles(RvParticles.INFALL.get().with(spec.accent, 1f, 20), sp.getX(), sp.getY() + 1, sp.getZ(), 40, 2, 1, 2, 0.1);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BOTTLE_FILL_DRAGONBREATH, SoundSource.PLAYERS, 1f, 0.8f);
        sp.displayClientMessage(Component.literal("Extracted the DNA of " + spec.name + ".").withColor(spec.accent), true);
        RealityOps.research(sp, 10, "universe sampled");
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ItemData.read(stack).contains("dna_spec");
    }

    @Override
    public Component getName(ItemStack stack) {
        CompoundTag t = ItemData.read(stack);
        return t.contains("dna_name") ? Component.literal("Universe Sample: " + t.getString("dna_name")).withColor(0xB0A0FF) : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = ItemData.read(stack);
        if (!t.contains("dna_spec")) {
            tip.add(Component.literal("Use inside a universe to extract its DNA.").withStyle(ChatFormatting.GRAY));
            return;
        }
        tip.add(Component.literal(t.getString("dna_type")).withStyle(ChatFormatting.LIGHT_PURPLE));
        tip.add(Component.literal(t.getString("dna_code")).withStyle(ChatFormatting.DARK_GRAY));
    }
}
