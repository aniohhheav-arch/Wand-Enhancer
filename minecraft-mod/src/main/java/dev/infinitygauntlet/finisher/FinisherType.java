package dev.infinitygauntlet.finisher;

import dev.infinitygauntlet.stone.InfinityStone;

/** The signature finisher of each stone. The duration is shared by server and client timelines. */
public enum FinisherType {
    COSMIC_PUNCH(InfinityStone.POWER, CosmicPunchTimeline.DURATION);

    public final InfinityStone stone;
    public final int duration;

    FinisherType(InfinityStone stone, int duration) {
        this.stone = stone;
        this.duration = duration;
    }
}
