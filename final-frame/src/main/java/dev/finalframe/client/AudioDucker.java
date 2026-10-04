package dev.finalframe.client;

import dev.finalframe.FFConfig;
import dev.finalframe.FinalFrame;
import dev.finalframe.client.session.ClientFinisherManager;
import dev.finalframe.client.session.ClientSession;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import org.jetbrains.annotations.Nullable;

/** After the final shot the world goes quiet: new non-Final-Frame sounds start ducked. */
public final class AudioDucker {
    private static final float MIN_GAIN = 0.15f;

    private AudioDucker() {
    }

    public static void onPlaySound(PlaySoundEvent event) {
        SoundInstance sound = event.getSound();
        if (sound == null || sound instanceof TickableSoundInstance || !FFConfig.get(FFConfig.DUCK_WORLD_AUDIO)) {
            return;
        }
        if (FinalFrame.MOD_ID.equals(sound.getLocation().getNamespace()) || sound.getSource() == SoundSource.MASTER) {
            return;
        }
        ClientSession s = ClientFinisherManager.INSTANCE.local();
        if (s == null) {
            return;
        }
        float pt = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        float duck = s.choreography().grade(s, s.time(pt)).duck();
        if (duck > 0.01f) {
            event.setSound(new Ducked(sound, 1f - (1f - MIN_GAIN) * duck));
        }
    }

    private record Ducked(SoundInstance inner, float gain) implements SoundInstance {
        @Override
        public ResourceLocation getLocation() {
            return inner.getLocation();
        }

        @Override
        @Nullable
        public WeighedSoundEvents resolve(SoundManager manager) {
            return inner.resolve(manager);
        }

        @Override
        public Sound getSound() {
            return inner.getSound();
        }

        @Override
        public SoundSource getSource() {
            return inner.getSource();
        }

        @Override
        public boolean isLooping() {
            return inner.isLooping();
        }

        @Override
        public boolean isRelative() {
            return inner.isRelative();
        }

        @Override
        public int getDelay() {
            return inner.getDelay();
        }

        @Override
        public float getVolume() {
            return inner.getVolume() * gain;
        }

        @Override
        public float getPitch() {
            return inner.getPitch();
        }

        @Override
        public double getX() {
            return inner.getX();
        }

        @Override
        public double getY() {
            return inner.getY();
        }

        @Override
        public double getZ() {
            return inner.getZ();
        }

        @Override
        public Attenuation getAttenuation() {
            return inner.getAttenuation();
        }

        @Override
        public boolean canStartSilent() {
            return inner.canStartSilent();
        }

        @Override
        public boolean canPlaySound() {
            return inner.canPlaySound();
        }
    }
}
