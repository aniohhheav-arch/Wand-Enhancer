package com.infinitemultiverse.client.vfx;

import com.infinitemultiverse.core.config.MultiverseConfig;
import com.infinitemultiverse.core.network.VfxPayload;
import com.infinitemultiverse.core.vfx.VfxIds;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

/** Client-side effect registry: maps effect ids from {@link VfxPayload} to particle choreography. */
public final class ClientVfx {
    @FunctionalInterface
    public interface Effect {
        void play(VfxSpawner spawner, Vec3 origin, Vec3 vector, float scale);
    }

    private static final Map<ResourceLocation, Effect> EFFECTS = Map.ofEntries(
            Map.entry(VfxIds.PHASE_STEP, CoreEffects::phaseStep),
            Map.entry(VfxIds.KINETIC_LEAP, CoreEffects::kineticLeap),
            Map.entry(VfxIds.SHOCKWAVE, CoreEffects::shockwave),
            Map.entry(VfxIds.AEGIS_PULSE, CoreEffects::aegisPulse),
            Map.entry(VfxIds.AEGIS_IMPACT, CoreEffects::aegisImpact),
            Map.entry(VfxIds.AEGIS_COLLAPSE, CoreEffects::aegisCollapse),
            Map.entry(VfxIds.TEMPORAL_DRAG, CoreEffects::temporalDrag),
            Map.entry(VfxIds.STAND_SUMMON, StandEffects::summon),
            Map.entry(VfxIds.STAND_DISMISS, StandEffects::dismiss),
            Map.entry(VfxIds.STAND_AWAKEN, StandEffects::awaken),
            Map.entry(VfxIds.STAND_PUNCH, StandEffects::punch),
            Map.entry(VfxIds.STAND_HEAVY, StandEffects::heavy),
            Map.entry(VfxIds.TIME_STOP, StandEffects::timeStop),
            Map.entry(VfxIds.TIME_RESUME, StandEffects::timeResume));

    private ClientVfx() {
    }

    public static void play(VfxPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        Effect effect = EFFECTS.get(payload.effect());
        if (level == null || effect == null) {
            return;
        }
        double maxDistance = MultiverseConfig.CLIENT.vfxMaxDistance.get();
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        if (camera.distanceToSqr(payload.origin()) > maxDistance * maxDistance) {
            return;
        }
        VfxSpawner spawner = new VfxSpawner(minecraft.particleEngine, level.random, VfxBudget.current());
        effect.play(spawner, payload.origin(), payload.vector(), payload.scale());
    }
}
