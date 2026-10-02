package dev.riftverse.client.cinematic;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.riftverse.client.render.Fullscreen;
import dev.riftverse.client.render.RvShaders;
import dev.riftverse.network.Payloads;
import dev.riftverse.registry.RvSounds;
import dev.riftverse.util.ColorUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Genesis Protocol as the player sees it: absolute void and silence, a single spark, the primordial expansion, the
 * birth of galaxies, a planet forming layer by layer, the first life, and a gateway that opens onto the real new
 * universe (the server has already placed the player there behind the opaque sequence). Stage cues trigger their own
 * sounds over a synthesised soundtrack. Hold Jump to skip.
 */
public final class GenesisCinematic {
    private static final float[] CUES = {0.07f, 0.17f, 0.32f, 0.54f, 0.62f, 0.80f, 0.88f};

    @Nullable
    private static Payloads.Genesis current;
    private static int tick;
    private static int skipHold;
    private static int cuePlayed;
    private static boolean skipSent;

    private GenesisCinematic() {}

    public static void play(Payloads.Genesis g) {
        current = g;
        tick = 0;
        skipHold = 0;
        cuePlayed = 0;
        skipSent = false;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().stop();
        CinematicDirector.reset();
    }

    public static void finish() {
        if (current == null) return;
        current = null;
        Minecraft mc = Minecraft.getInstance();
        mc.getSoundManager().play(SimpleSoundInstance.forUI(RvSounds.UNIVERSE_ARRIVE.get(), 1f, 1f));
    }

    public static boolean active() {
        return current != null;
    }

    private static float progress(float partial) {
        return current == null ? 0f : Mth.clamp((tick + partial) / current.duration(), 0f, 1f);
    }

    private static void cue(SoundEvent s, float pitch, float vol) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, vol));
    }

    public static void tick() {
        Payloads.Genesis g = current;
        if (g == null) return;
        Minecraft mc = Minecraft.getInstance();
        tick++;
        float p = progress(0f);
        while (cuePlayed < CUES.length && p >= CUES[cuePlayed]) {
            switch (cuePlayed) {
                case 0 -> {
                    cue(RvSounds.GENESIS_IGNITE.get(), 1f, 0.8f);
                    cue(RvSounds.GENESIS_THEME.get(), 1f, 0.9f);
                }
                case 1 -> {
                    cue(RvSounds.GENESIS_BANG.get(), 1f, 1f);
                    CinematicDirector.shake(1.2f, 40, 0f, 0xFFFFFF);
                }
                case 2 -> cue(RvSounds.WORMHOLE_TRAVEL.get(), 0.6f, 0.5f);
                case 3 -> cue(RvSounds.GENESIS_FORM.get(), 1f, 0.9f);
                case 4 -> cue(RvSounds.GENESIS_FORM.get(), 1.3f, 0.7f);
                case 5 -> cue(RvSounds.GENESIS_LIFE.get(), 1f, 0.9f);
                case 6 -> cue(RvSounds.GENESIS_GATE.get(), 1f, 1f);
                default -> {}
            }
            cuePlayed++;
        }
        if (mc.options.keyJump.isDown()) {
            if (++skipHold >= 30 && !skipSent) {
                skipSent = true;
                PacketDistributor.sendToServer(new Payloads.GenesisSkip(0));
            }
        } else {
            skipHold = 0;
        }
        // safety: never stay in the cinematic longer than intended even if the server goes quiet
        if (tick > g.duration() + 200) current = null;
    }

    public static boolean locksInput() {
        return current != null;
    }

    public static void render(GuiGraphics g, float partial) {
        Payloads.Genesis gen = current;
        if (gen == null) return;
        Minecraft mc = Minecraft.getInstance();
        int w = g.guiWidth();
        int h = g.guiHeight();
        float p = progress(partial);
        ShaderInstance sh = RvShaders.genesis;
        if (sh != null) {
            g.flush();
            int a = gen.colorA();
            int b = gen.colorB();
            sh.safeGetUniform("ColorA").set(ColorUtil.r(a), ColorUtil.g(a), ColorUtil.b(a));
            sh.safeGetUniform("ColorB").set(ColorUtil.r(b), ColorUtil.g(b), ColorUtil.b(b));
            float aspect = mc.getWindow().getWidth() / (float) Math.max(1, mc.getWindow().getHeight());
            sh.safeGetUniform("GenParams").set(aspect, p, 1f, gen.seed());
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            Fullscreen.draw(sh);
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
        } else {
            g.fill(0, 0, w, h, 0xFF000000);
        }
        // captions for each act
        String caption = p < 0.07f ? "" : p < 0.17f ? "In the beginning, there was nothing." : p < 0.32f ? "Then, everything."
                : p < 0.54f ? "Space unfolds. Light gathers into galaxies." : p < 0.80f ? "A world takes shape." : p < 0.88f ? "Life stirs."
                : "";
        float ca = captionAlpha(p);
        if (!caption.isEmpty() && ca > 0.03f) {
            int alpha = (int) (ca * 220);
            int cw = mc.font.width(caption);
            g.drawString(mc.font, caption, (w - cw) / 2, (int) (h * 0.82f), (alpha << 24) | 0xE8E4F8, false);
        }
        if (p > 0.9f) {
            float ta = Mth.clamp((p - 0.9f) / 0.04f, 0f, 1f) * (1f - Mth.clamp((p - 0.985f) / 0.015f, 0f, 1f));
            int alpha = (int) (ta * 255);
            if (alpha > 8) {
                g.pose().pushPose();
                g.pose().translate(w / 2f, h * 0.36f, 0);
                g.pose().scale(2.6f, 2.6f, 1f);
                String t = "A NEW UNIVERSE IS BORN";
                g.drawString(mc.font, t, -mc.font.width(t) / 2, 0, (alpha << 24) | (gen.colorA() & 0xFFFFFF), true);
                g.pose().popPose();
                String sub = gen.name() + "  •  " + gen.designation() + "  •  Stability 100% — Stable";
                g.drawString(mc.font, sub, (w - mc.font.width(sub)) / 2, (int) (h * 0.36f + 30), (alpha << 24) | 0xFFFFFF, true);
            }
        }
        String hint = skipHold > 0 ? "Skipping... " + (skipHold * 100 / 30) + "%" : "Hold [Jump] to skip";
        g.drawString(mc.font, hint, w - mc.font.width(hint) - 8, h - 14, 0x60FFFFFF, false);
    }

    private static float captionAlpha(float p) {
        for (float[] r : new float[][] {{0.07f, 0.17f}, {0.17f, 0.32f}, {0.32f, 0.54f}, {0.54f, 0.80f}, {0.80f, 0.88f}}) {
            if (p >= r[0] && p < r[1]) {
                float k = (p - r[0]) / (r[1] - r[0]);
                return Mth.clamp(Math.min(k * 6f, (1f - k) * 6f), 0f, 1f);
            }
        }
        return 0f;
    }
}
