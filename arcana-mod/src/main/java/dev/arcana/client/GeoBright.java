package dev.arcana.client;

import net.minecraft.client.renderer.LightTexture;
import software.bernie.geckolib.cache.object.GeoBone;

/** Bones named {@code glow*} or {@code tint*} are rendered fullbright, so crystals and magic read as luminous. */
public final class GeoBright {
    public static boolean isBright(GeoBone bone) {
        String n = bone.getName();
        return n.startsWith("glow") || n.startsWith("tint");
    }

    public static int light(GeoBone bone, int light) {
        return isBright(bone) ? LightTexture.FULL_BRIGHT : light;
    }

    private GeoBright() {}
}
