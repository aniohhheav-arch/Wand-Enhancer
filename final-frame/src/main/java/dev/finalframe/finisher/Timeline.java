package dev.finalframe.finisher;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Tick-based phase layout of a finisher. Phases are contiguous: each runs from its start tick to the
 * next phase's start; the last runs to {@link #length()}.
 */
public final class Timeline {
    private final List<FinisherPhase> order;
    private final Map<FinisherPhase, Integer> starts;
    private final int length;

    private Timeline(List<FinisherPhase> order, Map<FinisherPhase, Integer> starts, int length) {
        this.order = order;
        this.starts = starts;
        this.length = length;
    }

    public static Builder builder() {
        return new Builder();
    }

    public int length() {
        return length;
    }

    public int start(FinisherPhase phase) {
        Integer s = starts.get(phase);
        if (s == null) {
            throw new IllegalArgumentException("Phase not in timeline: " + phase);
        }
        return s;
    }

    public int end(FinisherPhase phase) {
        int i = order.indexOf(phase);
        return i + 1 < order.size() ? starts.get(order.get(i + 1)) : length;
    }

    public FinisherPhase phaseAt(float tick) {
        if (tick < 0) {
            return FinisherPhase.IDLE;
        }
        FinisherPhase current = FinisherPhase.IDLE;
        for (FinisherPhase p : order) {
            if (tick >= starts.get(p)) {
                current = p;
            }
        }
        return tick >= length ? FinisherPhase.IDLE : current;
    }

    /** 0..1 progress through {@code phase} at {@code tick}, clamped. */
    public float progress(FinisherPhase phase, float tick) {
        int s = start(phase);
        int e = end(phase);
        return e <= s ? 1f : Math.max(0f, Math.min(1f, (tick - s) / (float) (e - s)));
    }

    public static final class Builder {
        private final List<FinisherPhase> order = new ArrayList<>();
        private final Map<FinisherPhase, Integer> starts = new EnumMap<>(FinisherPhase.class);

        public Builder phase(FinisherPhase phase, int startTick) {
            if (!order.isEmpty() && startTick < starts.get(order.get(order.size() - 1))) {
                throw new IllegalArgumentException("Phases must be added in time order");
            }
            order.add(phase);
            starts.put(phase, startTick);
            return this;
        }

        public Timeline end(int length) {
            return new Timeline(List.copyOf(order), Map.copyOf(starts), length);
        }
    }
}
