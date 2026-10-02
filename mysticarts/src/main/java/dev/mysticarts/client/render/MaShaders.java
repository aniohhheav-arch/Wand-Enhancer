package dev.mysticarts.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import dev.mysticarts.MysticArts;
import java.io.IOException;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.jetbrains.annotations.Nullable;

/** Every Mystic Arts core shader. Instances are replaced on resource reload. */
public final class MaShaders {
    @Nullable public static ShaderInstance energy;
    @Nullable public static ShaderInstance rune;
    @Nullable public static ShaderInstance screenfx;
    @Nullable public static ShaderInstance warp;

    private static final long START = System.nanoTime();

    private MaShaders() {}

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MysticArts.id("energy"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> energy = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MysticArts.id("rune"), DefaultVertexFormat.POSITION_TEX_COLOR), s -> rune = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MysticArts.id("screenfx"), DefaultVertexFormat.POSITION), s -> screenfx = s);
            event.registerShader(new ShaderInstance(event.getResourceProvider(), MysticArts.id("warp"), DefaultVertexFormat.POSITION), s -> warp = s);
        } catch (IOException e) {
            throw new RuntimeException("Mystic Arts failed to load its shaders", e);
        }
    }

    /** Seconds since the client started, wrapped to keep float precision. */
    public static float time() {
        return (float) (((System.nanoTime() - START) / 1.0e9) % 3600.0);
    }

    public static void updateTime() {
        float t = time();
        for (ShaderInstance s : new ShaderInstance[] {energy, rune, screenfx, warp}) {
            if (s != null) s.safeGetUniform("RvTime").set(t);
        }
    }
}
