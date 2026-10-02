package dev.riftverse.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.riftverse.Riftverse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/** Every Riftverse core shader. Instances are replaced on resource reload. */
public final class RvShaders {
    @Nullable public static ShaderInstance sky;
    @Nullable public static ShaderInstance blackhole;
    @Nullable public static ShaderInstance rift;
    @Nullable public static ShaderInstance wormhole;
    @Nullable public static ShaderInstance genesis;
    @Nullable public static ShaderInstance screenfx;
    @Nullable public static ShaderInstance cosmos;
    @Nullable public static ShaderInstance energy;

    private static final long START = System.nanoTime();

    private RvShaders() {}

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("sky"), DefaultVertexFormat.POSITION), s -> sky = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("blackhole"), DefaultVertexFormat.POSITION), s -> blackhole = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("rift"), DefaultVertexFormat.POSITION), s -> rift = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("wormhole"), DefaultVertexFormat.POSITION), s -> wormhole = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("genesis"), DefaultVertexFormat.POSITION), s -> genesis = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("screenfx"), DefaultVertexFormat.POSITION), s -> screenfx = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("cosmos"), DefaultVertexFormat.POSITION), s -> cosmos = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Riftverse.id("energy"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> energy = s);
        } catch (IOException e) {
            throw new RuntimeException("Riftverse failed to load its shaders", e);
        }
    }

    /** Seconds since the client started, wrapped to keep float precision. */
    public static float time() {
        return (float) (((System.nanoTime() - START) / 1.0e9) % 3600.0);
    }

    /** Pushes the shared clock into every shader once per frame. */
    public static void updateTime() {
        float t = time();
        List<ShaderInstance> all = new ArrayList<>(7);
        all.add(sky);
        all.add(blackhole);
        all.add(rift);
        all.add(wormhole);
        all.add(genesis);
        all.add(screenfx);
        all.add(cosmos);
        all.add(energy);
        for (ShaderInstance s : all) {
            if (s != null) s.safeGetUniform("RvTime").set(t);
        }
    }
}
