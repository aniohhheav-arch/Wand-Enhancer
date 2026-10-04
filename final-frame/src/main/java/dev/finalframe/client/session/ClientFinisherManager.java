package dev.finalframe.client.session;

import com.mojang.logging.LogUtils;
import dev.finalframe.client.choreo.Choreographies;
import dev.finalframe.client.choreo.Choreography;
import dev.finalframe.finisher.FinisherDefinition;
import dev.finalframe.finisher.FinisherRegistry;
import dev.finalframe.network.FFNetwork;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Tracks every finisher this client is replaying and advances them in step with the game tick. */
public final class ClientFinisherManager implements FFNetwork.ClientSink {
    public static final ClientFinisherManager INSTANCE = new ClientFinisherManager();
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int END_GRACE = 6;

    private final Map<Integer, ClientSession> sessions = new LinkedHashMap<>();

    private ClientFinisherManager() {
    }

    @Override
    public void start(FFNetwork.Start payload) {
        FinisherDefinition def = FinisherRegistry.get(payload.snapshot().finisherId());
        Choreography choreo = Choreographies.get(payload.snapshot().finisherId());
        if (def == null || choreo == null) {
            LOGGER.warn("Unknown finisher {}", payload.snapshot().finisherId());
            return;
        }
        sessions.put(payload.snapshot().sessionId(), new ClientSession(payload.snapshot(), def, choreo, payload.elapsed()));
    }

    @Override
    public void sync(FFNetwork.Sync payload) {
        ClientSession s = sessions.get(payload.sessionId());
        if (s != null && !s.isAborting() && Math.abs(s.tick - payload.tick()) > 2) {
            s.tick = payload.tick();
        }
    }

    @Override
    public void end(FFNetwork.End payload) {
        ClientSession s = sessions.get(payload.sessionId());
        if (s == null) {
            return;
        }
        s.serverEnded = true;
        if (payload.aborted() && !s.isAborting()) {
            s.abortTick = s.tick;
        }
    }

    /** Called once per client game tick (not while paused or tick-frozen). */
    public void tick() {
        for (ClientSession s : new ArrayList<>(sessions.values())) {
            s.tick++;
            s.shake().tick();
            if (!s.isAborting() && s.tick <= s.definition().timeline().length()) {
                s.choreography().onTick(s, s.tick);
            }
            int length = s.definition().timeline().length();
            boolean done = s.isAborting() ? s.tick - s.abortTick > ClientSession.ABORT_BLEND : s.tick > length + END_GRACE;
            if (done) {
                sessions.remove(s.snapshot().sessionId());
            }
        }
    }

    public void clear() {
        sessions.clear();
    }

    public Collection<ClientSession> sessions() {
        return sessions.values();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    /** The finisher the local player is performing, if any (including its abort hand-back). */
    @Nullable
    public ClientSession local() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return null;
        }
        for (ClientSession s : sessions.values()) {
            if (s.snapshot().performerId() == mc.player.getId()) {
                return s;
            }
        }
        return null;
    }

    /** True while the local player must not move, look around, attack or switch items. */
    public boolean localLocked() {
        ClientSession s = local();
        return s != null && !s.isAborting() && s.tick < s.definition().timeline().length();
    }

    @Nullable
    public ClientSession asPerformer(Entity entity) {
        for (ClientSession s : sessions.values()) {
            if (s.isPerformer(entity)) {
                return s;
            }
        }
        return null;
    }

    @Nullable
    public ClientSession asTarget(Entity entity) {
        for (ClientSession s : sessions.values()) {
            if (s.isTarget(entity)) {
                return s;
            }
        }
        return null;
    }
}
