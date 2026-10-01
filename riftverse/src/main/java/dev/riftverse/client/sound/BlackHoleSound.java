package dev.riftverse.client.sound;

import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.registry.RvSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;

/** The black hole's endless sub-bass drone, louder and lower the closer you are. */
public class BlackHoleSound extends AbstractTickableSoundInstance {
    private final BlackHoleEntity hole;

    public BlackHoleSound(BlackHoleEntity hole) {
        super(RvSounds.BLACK_HOLE_AMBIENT.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.hole = hole;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.attenuation = Attenuation.NONE;
        this.x = hole.getX();
        this.y = hole.getY();
        this.z = hole.getZ();
    }

    @Override
    public void tick() {
        if (hole.isRemoved()) {
            stop();
            return;
        }
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        double d = player.position().distanceTo(hole.position());
        float reach = hole.influenceRadius() * 2.5f;
        float k = (float) Math.max(0.0, 1.0 - d / reach);
        this.volume = Math.max(0.001f, k * k * 1.6f) * Math.min(1f, hole.horizonRadius() / 2f + 0.3f);
        this.pitch = 0.55f + 0.35f * k;
        this.x = hole.getX();
        this.y = hole.getY();
        this.z = hole.getZ();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
