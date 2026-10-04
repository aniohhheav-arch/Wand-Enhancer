package dev.finalframe.client.camera;

import dev.finalframe.client.session.ClientSession;
import dev.finalframe.finisher.Ease;
import java.util.ArrayList;
import java.util.List;

/**
 * An edit decision list: shots that start at a tick and either cut in hard or blend in from the
 * previous shot over a number of ticks. Each shot is a function of time, so shots can orbit, track
 * and push in while they are live.
 */
public final class ShotList {
    @FunctionalInterface
    public interface Shot {
        CameraPose pose(ClientSession session, float t, CameraPose gameplay);
    }

    private record Entry(float start, float blend, Shot shot) {
    }

    private final List<Entry> entries = new ArrayList<>();

    /** Hard cut at {@code start}. */
    public ShotList cut(float start, Shot shot) {
        return blend(start, 0, shot);
    }

    /** Blends from the previous shot into this one over {@code blendTicks}. */
    public ShotList blend(float start, float blendTicks, Shot shot) {
        entries.add(new Entry(start, blendTicks, shot));
        return this;
    }

    public CameraPose evaluate(ClientSession session, float t, CameraPose gameplay) {
        int idx = 0;
        for (int i = 0; i < entries.size(); i++) {
            if (t >= entries.get(i).start) {
                idx = i;
            }
        }
        Entry e = entries.get(idx);
        CameraPose pose = e.shot.pose(session, t, gameplay);
        if (idx > 0 && e.blend > 0 && t < e.start + e.blend) {
            CameraPose prev = entries.get(idx - 1).shot.pose(session, t, gameplay);
            return CameraPose.blend(prev, pose, Ease.inOutCubic((t - e.start) / e.blend));
        }
        return pose;
    }
}
