package dev.riftverse.client;

import dev.riftverse.registry.RvParticles;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.universe.UniverseSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.client.event.SelectMusicEvent;
import org.jetbrains.annotations.Nullable;

/** Weather, airborne particles and music unique to each reality. */
public final class UniverseAmbience {
    private UniverseAmbience() {}

    public static void onUniverseChanged() {
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        UniverseSpec s = ClientUniverseState.spec();
        if (level == null || player == null || s == null || mc.isPaused()) return;
        RandomSource r = level.random;
        int amount = switch (s.weather) {
            case STORM -> 26;
            case RAIN, DATA_RAIN -> 18;
            case SNOW, ASH, PETALS -> 10;
            case SPORES, STARDUST, EMBERS -> 7;
            case CLEAR -> ClientUniverseState.isNexus() ? 4 : 0;
        };
        for (int i = 0; i < amount; i++) {
            double x = player.getX() + (r.nextDouble() - 0.5) * 36;
            double z = player.getZ() + (r.nextDouble() - 0.5) * 36;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
            double top = player.getY() + 14 + r.nextDouble() * 8;
            if (ground > top) continue;
            double y = Math.max(ground + 0.2, player.getY() - 6 + r.nextDouble() * 20);
            switch (s.weather) {
                case RAIN, STORM -> level.addParticle(RvParticles.STREAK.get().with(0x9FB8E0, 0.35f, 30), x, top, z, (r.nextDouble() - 0.5) * 0.05, -1.4, 0);
                case SNOW -> level.addParticle(ParticleTypes.SNOWFLAKE, x, y + 6, z, (r.nextDouble() - 0.5) * 0.05, -0.08, (r.nextDouble() - 0.5) * 0.05);
                case ASH -> level.addParticle(ParticleTypes.WHITE_ASH, x, y, z, 0, -0.02, 0);
                case SPORES -> level.addParticle(RvParticles.MOTE.get().with(r.nextBoolean() ? 0x7CFFB2 : 0xB26BFF, 0.12f, 120), x, y, z,
                        (r.nextDouble() - 0.5) * 0.01, 0.006, (r.nextDouble() - 0.5) * 0.01);
                case EMBERS -> level.addParticle(RvParticles.SPARK.get().with(r.nextBoolean() ? 0xFF6A1F : 0xFFC14D, 0.18f, 70), x, y - 4, z,
                        (r.nextDouble() - 0.5) * 0.02, 0.05 + r.nextDouble() * 0.05, (r.nextDouble() - 0.5) * 0.02);
                case DATA_RAIN -> level.addParticle(RvParticles.GLITCH.get().with(r.nextInt(6) == 0 ? 0xFF0055 : 0x39FF14, 0.22f, 40), x, top, z, 0, -0.55, 0);
                case STARDUST -> level.addParticle(RvParticles.SPARK.get().with(r.nextBoolean() ? s.nebulaA : 0xFFFFFF, 0.12f, 90), x, y, z,
                        (r.nextDouble() - 0.5) * 0.01, -0.004, (r.nextDouble() - 0.5) * 0.01);
                case PETALS -> level.addParticle(ParticleTypes.CHERRY_LEAVES, x, y + 4, z, 0, 0, 0);
                case CLEAR -> level.addParticle(RvParticles.MOTE.get().with(0xFFC14D, 0.15f, 120), x, y, z, 0, 0.01, 0);
            }
        }
        dev.riftverse.client.render.SkyRenderer.UniverseFlash.tick(s, r);
        if (s.stormIntensity > 0.5f && dev.riftverse.client.render.SkyRenderer.UniverseFlash.current() > 0.7f && r.nextInt(3) == 0) {
            level.playLocalSound(player.blockPosition().offset(r.nextInt(40) - 20, 20, r.nextInt(40) - 20),
                    net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.WEATHER, 2.0f, 0.7f + r.nextFloat() * 0.3f, false);
        }
    }

    @Nullable
    private static Holder<SoundEvent> track(UniverseSpec s) {
        if (ClientUniverseState.isNexus()) return RvSounds.MUSIC_NEXUS;
        return switch (s.music) {
            case COSMIC -> RvSounds.MUSIC_COSMIC;
            case NEON -> RvSounds.MUSIC_NEON;
            case DREAM -> RvSounds.MUSIC_DREAM;
            case VOID -> RvSounds.MUSIC_VOID;
            case NEXUS -> RvSounds.MUSIC_NEXUS;
            case OCEAN -> RvSounds.MUSIC_OCEAN;
            case ANCIENT -> RvSounds.MUSIC_ANCIENT;
        };
    }

    public static void selectMusic(SelectMusicEvent event) {
        UniverseSpec s = ClientUniverseState.spec();
        if (s == null) return;
        Holder<SoundEvent> t = track(s);
        if (t != null) event.setMusic(new Music(t, 600, 2400, false));
    }
}
