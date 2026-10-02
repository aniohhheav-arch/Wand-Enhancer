package dev.mysticarts.client;

import dev.mysticarts.network.Payloads;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/** What every visible caster is doing, plus smoothed animation values derived from it. */
public final class CasterStates {
    public static final class Anim {
        public float shield, shieldO;
        public float charge, chargeO;
        public float flight, flightO;
        public float astral, astralO;
        public int poseTicks;
        public int pose;
        public float poseBlend, poseBlendO;
        public long poseStart;

        public float shield(float partial) {
            return Mth.lerp(partial, shieldO, shield);
        }

        public float charge(float partial) {
            return Mth.lerp(partial, chargeO, charge);
        }

        public float flight(float partial) {
            return Mth.lerp(partial, flightO, flight);
        }

        public float astral(float partial) {
            return Mth.lerp(partial, astralO, astral);
        }

        public float poseBlend(float partial) {
            return Mth.lerp(partial, poseBlendO, poseBlend);
        }
    }

    private static final Map<Integer, Payloads.CasterState> STATES = new HashMap<>();
    private static final Map<Integer, Anim> ANIMS = new HashMap<>();

    private CasterStates() {}

    public static void update(Payloads.CasterState s) {
        STATES.put(s.entity(), s);
        Anim a = ANIMS.computeIfAbsent(s.entity(), k -> new Anim());
        if (s.poseTicks() > 0 && (s.pose() != a.pose || a.poseTicks <= 0)) a.poseStart = ClientPower.clientTick();
        a.pose = s.pose();
        a.poseTicks = s.poseTicks();
    }

    @Nullable
    public static Payloads.CasterState get(int entity) {
        return STATES.get(entity);
    }

    public static Anim anim(int entity) {
        return ANIMS.computeIfAbsent(entity, k -> new Anim());
    }

    public static Map<Integer, Payloads.CasterState> all() {
        return STATES;
    }

    public static boolean hasArtifact(int entity, int ordinal) {
        Payloads.CasterState s = STATES.get(entity);
        return s != null && (s.artifacts() & (1 << ordinal)) != 0;
    }

    public static boolean flag(int entity, int flag) {
        Payloads.CasterState s = STATES.get(entity);
        return s != null && (s.flags() & flag) != 0;
    }

    public static void tick() {
        for (Map.Entry<Integer, Anim> e : ANIMS.entrySet()) {
            Anim a = e.getValue();
            Payloads.CasterState s = STATES.get(e.getKey());
            a.shieldO = a.shield;
            a.chargeO = a.charge;
            a.flightO = a.flight;
            a.astralO = a.astral;
            a.poseBlendO = a.poseBlend;
            boolean shield = s != null && s.shieldMode() == 1;
            a.shield = Mth.clamp(a.shield + (shield ? 0.18f : -0.15f), 0f, 1f);
            boolean charging = s != null && (s.flags() & Payloads.CasterState.F_CHARGING) != 0;
            a.charge = Mth.clamp(a.charge + (charging ? 0.03f : -0.2f), 0f, 1f);
            boolean flying = s != null && (s.flags() & Payloads.CasterState.F_FLYING) != 0;
            a.flight = Mth.clamp(a.flight + (flying ? 0.1f : -0.08f), 0f, 1f);
            boolean astral = s != null && (s.flags() & Payloads.CasterState.F_ASTRAL_FORM) != 0;
            a.astral = Mth.clamp(a.astral + (astral ? 0.15f : -0.1f), 0f, 1f);
            if (a.poseTicks > 0) a.poseTicks--;
            a.poseBlend = Mth.clamp(a.poseBlend + (a.poseTicks > 0 && a.pose != 0 ? 0.35f : -0.2f), 0f, 1f);
        }
    }

    public static void clear() {
        STATES.clear();
        ANIMS.clear();
    }
}
