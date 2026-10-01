package com.infinitemultiverse.client.vfx;

import com.infinitemultiverse.core.config.MultiverseConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;

/** Converts an effect's "high quality" particle counts into what this client is willing to render. */
public record VfxBudget(float multiplier, int cap) {
    public static VfxBudget current() {
        float multiplier = MultiverseConfig.CLIENT.vfxQuality.get().particleMultiplier();
        ParticleStatus vanilla = Minecraft.getInstance().options.particles().get();
        if (vanilla == ParticleStatus.DECREASED) {
            multiplier *= 0.5f;
        } else if (vanilla == ParticleStatus.MINIMAL) {
            multiplier *= 0.15f;
        }
        return new VfxBudget(multiplier, MultiverseConfig.CLIENT.maxParticlesPerEffect.get());
    }

    public int count(int highQualityCount) {
        return Math.max(1, Math.min(cap, Math.round(highQualityCount * multiplier)));
    }
}
