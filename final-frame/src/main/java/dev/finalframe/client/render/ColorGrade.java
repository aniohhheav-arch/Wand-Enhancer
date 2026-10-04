package dev.finalframe.client.render;

import com.mojang.logging.LogUtils;
import dev.finalframe.FFConfig;
import dev.finalframe.FinalFrame;
import dev.finalframe.client.choreo.Grade;
import dev.finalframe.mixin.client.PostChainAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Owns the {@code finalframe:western} post effect: loads it when a grade is needed, feeds its uniforms
 * every frame and removes it again — without disturbing a vanilla effect (spectator shaders).
 */
public final class ColorGrade {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation EFFECT = FinalFrame.id("shaders/post/western.json");
    private static boolean loaded;
    private static boolean failed;
    private static float time;

    private ColorGrade() {
    }

    /** True when the shader is drawing the grade; otherwise the HUD draws a fallback vignette. */
    public static boolean isShaderActive() {
        return loaded;
    }

    public static void update(Grade grade) {
        Minecraft mc = Minecraft.getInstance();
        GameRenderer renderer = mc.gameRenderer;
        boolean want = !grade.isNeutral() && FFConfig.get(FFConfig.COLOR_GRADING) && !failed;
        PostChain current = renderer.currentEffect();
        boolean ours = current != null && current.getName().equals(EFFECT.toString());
        if (loaded && !ours) {
            loaded = false; // something else (a spectator shader, a resource reload) replaced or dropped it
        }
        if (want && !loaded) {
            if (current != null && !ours) {
                return; // never stomp a vanilla/other-mod effect
            }
            renderer.loadEffect(EFFECT);
            current = renderer.currentEffect();
            loaded = current != null && current.getName().equals(EFFECT.toString());
            if (!loaded) {
                failed = true;
                LOGGER.warn("Final Frame color grade unavailable; using HUD fallback");
                return;
            }
        } else if (!want && loaded) {
            renderer.shutdownEffect();
            loaded = false;
            return;
        }
        if (loaded && current != null) {
            time += 0.37f;
            for (PostPass pass : ((PostChainAccessor) current).finalframe$passes()) {
                var effect = pass.getEffect();
                effect.safeGetUniform("Saturation").set(grade.saturation());
                effect.safeGetUniform("Warmth").set(grade.warmth());
                effect.safeGetUniform("Contrast").set(grade.contrast());
                effect.safeGetUniform("Vignette").set(grade.vignette());
                effect.safeGetUniform("Flash").set(grade.flash());
                effect.safeGetUniform("Grain").set(grade.grain());
            }
        }
    }

    /** Resource reloads rebuild shaders; allow a retry afterwards. */
    public static void onReload() {
        failed = false;
        loaded = false;
    }
}
