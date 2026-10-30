package dev.infinitygauntlet.client;

import dev.infinitygauntlet.ability.Abilities;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.item.InfinityGauntletItem;
import dev.infinitygauntlet.network.SelectAbilityPayload;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/** Client helpers for reading and changing the selection of the gauntlet the local player holds. */
public final class HeldGauntlet {
    public static ItemStack find(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof InfinityGauntletItem) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public static void cycleAbility(int delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = find(player);
        if (stack.isEmpty()) {
            return;
        }
        GauntletState state = InfinityGauntletItem.state(stack);
        InfinityStone stone = state.selected();
        int count = Abilities.available(stone, state.stoneSet()).size();
        if (!state.has(stone) || count == 0) {
            return;
        }
        select(stone, Math.floorMod(state.abilityIndex(stone) + delta, count));
    }

    public static void cycleStone() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        ItemStack stack = find(player);
        GauntletState state = InfinityGauntletItem.state(stack);
        List<InfinityStone> stones = state.stones();
        if (stones.isEmpty()) {
            return;
        }
        int idx = stones.indexOf(state.selected());
        InfinityStone next = stones.get(Math.floorMod(idx + 1, stones.size()));
        select(next, state.abilityIndex(next));
    }

    public static void select(InfinityStone stone, int ability) {
        PacketDistributor.sendToServer(new SelectAbilityPayload(stone.ordinal(), ability));
        Player player = Minecraft.getInstance().player;
        if (player != null) {
            player.playSound(ModSounds.GAUNTLET_SELECT.get(), 0.5f, 1.0f + stone.ordinal() * 0.08f);
        }
    }

    private HeldGauntlet() {
    }
}
