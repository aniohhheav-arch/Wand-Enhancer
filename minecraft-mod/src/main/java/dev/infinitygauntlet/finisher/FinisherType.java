package dev.infinitygauntlet.finisher;

import dev.infinitygauntlet.stone.InfinityStone;

/** The signature finisher of each stone. The duration is shared by server and client timelines. */
public enum FinisherType {
    COSMIC_PUNCH(InfinityStone.POWER, CosmicPunchTimeline.DURATION),
    DIMENSIONAL_ERASURE(InfinityStone.SPACE, Timelines.Erasure.DURATION),
    PSYCHIC_OVERLOAD(InfinityStone.MIND, Timelines.Psychic.DURATION),
    REALITY_REWRITE(InfinityStone.REALITY, Timelines.Rewrite.DURATION),
    TEMPORAL_COLLAPSE(InfinityStone.TIME, Timelines.Collapse.DURATION),
    SOUL_EXTRACTION(InfinityStone.SOUL, Timelines.Extraction.DURATION);

    public final InfinityStone stone;
    public final int duration;

    FinisherType(InfinityStone stone, int duration) {
        this.stone = stone;
        this.duration = duration;
    }
}
