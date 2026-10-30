package dev.infinitygauntlet.client.sound;

import dev.infinitygauntlet.entity.RupturePortalEntity;
import dev.infinitygauntlet.registry.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** Looping low rumble that follows a rupture and swells/fades with its opening and collapse. */
public class PortalAmbientSound extends AbstractTickableSoundInstance {
    private final RupturePortalEntity portal;
    private final Runnable onStop;

    public PortalAmbientSound(RupturePortalEntity portal, Runnable onStop) {
        super(ModSounds.SPACE_PORTAL_AMBIENT.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.portal = portal;
        this.onStop = onStop;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = Attenuation.LINEAR;
        this.x = portal.getX();
        this.y = portal.getY();
        this.z = portal.getZ();
    }

    @Override
    public void tick() {
        if (portal.isRemoved()) {
            stop();
            onStop.run();
            return;
        }
        x = portal.getX();
        y = portal.getY();
        z = portal.getZ();
        float strength = portal.openProgress(0) * (1 - portal.closeProgress(0));
        volume = Math.max(0.01f, strength * Math.min(3f, 0.6f + portal.radius() * 0.35f));
        pitch = portal.radius() > 3 ? 0.7f : 1f;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
