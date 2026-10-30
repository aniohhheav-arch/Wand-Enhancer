package dev.infinitygauntlet.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.infinitygauntlet.stone.InfinityStone;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Immutable gauntlet contents stored as an item data component: installed stones, the selected stone and the
 * selected ability index for each stone (indexed by stone ordinal).
 */
public record GauntletState(List<InfinityStone> stones, InfinityStone selected, List<Integer> abilityIndices) {
    public static final GauntletState EMPTY = new GauntletState(List.of(), InfinityStone.SPACE, defaultIndices());

    public static final Codec<GauntletState> CODEC = RecordCodecBuilder.create(i -> i.group(
            InfinityStone.CODEC.listOf().fieldOf("stones").forGetter(GauntletState::stones),
            InfinityStone.CODEC.fieldOf("selected").forGetter(GauntletState::selected),
            Codec.INT.listOf().fieldOf("ability_indices").forGetter(GauntletState::abilityIndices)
    ).apply(i, GauntletState::new));

    public static final StreamCodec<ByteBuf, GauntletState> STREAM_CODEC = StreamCodec.composite(
            InfinityStone.STREAM_CODEC.apply(ByteBufCodecs.list(6)), GauntletState::stones,
            InfinityStone.STREAM_CODEC, GauntletState::selected,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(6)), GauntletState::abilityIndices,
            GauntletState::new);

    public GauntletState {
        stones = List.copyOf(stones);
        List<Integer> fixed = new ArrayList<>(abilityIndices);
        while (fixed.size() < InfinityStone.values().length) {
            fixed.add(0);
        }
        abilityIndices = List.copyOf(fixed.subList(0, InfinityStone.values().length));
    }

    private static List<Integer> defaultIndices() {
        return Collections.nCopies(InfinityStone.values().length, 0);
    }

    public boolean has(InfinityStone stone) {
        return stones.contains(stone);
    }

    public boolean isComplete() {
        return stones.size() == InfinityStone.values().length;
    }

    public Set<InfinityStone> stoneSet() {
        return stones.isEmpty() ? EnumSet.noneOf(InfinityStone.class) : EnumSet.copyOf(stones);
    }

    public int abilityIndex(InfinityStone stone) {
        return abilityIndices.get(stone.ordinal());
    }

    public GauntletState withStone(InfinityStone stone) {
        if (has(stone)) {
            return this;
        }
        List<InfinityStone> list = new ArrayList<>(stones);
        list.add(stone);
        list.sort(null);
        return new GauntletState(list, stones.isEmpty() ? stone : selected, abilityIndices);
    }

    public GauntletState withoutStone(InfinityStone stone) {
        List<InfinityStone> list = new ArrayList<>(stones);
        list.remove(stone);
        InfinityStone sel = selected == stone && !list.isEmpty() ? list.getFirst() : selected;
        return new GauntletState(list, sel, abilityIndices);
    }

    public GauntletState withSelection(InfinityStone stone, int abilityIndex) {
        List<Integer> idx = new ArrayList<>(abilityIndices);
        idx.set(stone.ordinal(), abilityIndex);
        return new GauntletState(stones, stone, idx);
    }
}
