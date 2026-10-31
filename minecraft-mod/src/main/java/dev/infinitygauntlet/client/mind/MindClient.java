package dev.infinitygauntlet.client.mind;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.Keybinds;
import dev.infinitygauntlet.client.particle.Particles;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.mind.LevitationFieldEntity;
import dev.infinitygauntlet.network.MindStatePayload;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Client side of the Mind Stone's personal states. Astral Projection gets a golden vignette and a HUD saying how to
 * return. Mind's Eye reveals every living creature within range as a gold silhouette through walls (a client-only
 * glowing outline in a client-only gold team), labels each with its distance, and places markers around the
 * crosshair pointing toward each one.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class MindClient {
    private static final String TEAM = "infinitygauntlet_minds_eye";
    private static final double EYE_RANGE = 48;
    private static int astralTicks;
    private static int astralTotal;
    private static int eyeTicks;
    private static int eyeTotal;
    /** Revealed entities and whether they were already glowing before. */
    private static final Map<Entity, Boolean> REVEALED = new HashMap<>();
    private static final List<String> TEAMED = new ArrayList<>();

    public static void state(MindStatePayload p) {
        if (p.state() == MindStatePayload.ASTRAL) {
            astralTicks = astralTotal = p.ticks();
        } else if (p.state() == MindStatePayload.MINDS_EYE) {
            eyeTicks = eyeTotal = p.ticks();
            if (p.ticks() == 0) {
                unreveal();
            }
        }
    }

    public static boolean astral() {
        return astralTicks > 0;
    }

    public static boolean mindsEye() {
        return eyeTicks > 0;
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            astralTicks = eyeTicks = 0;
            REVEALED.clear();
            TEAMED.clear();
            return;
        }
        if (mc.isPaused()) {
            return;
        }
        if (astralTicks > 0) {
            astralTicks--;
            if (Particles.chance(0.5f)) {
                ThreadLocalRandom r = ThreadLocalRandom.current();
                Particles.mote(mc.player.getEyePosition().add(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()), new Vec3(0, 0.01, 0),
                        InfinityStone.MIND.color(), 0.05f, 30);
            }
        }
        if (eyeTicks > 0) {
            eyeTicks--;
            reveal(mc);
            if (eyeTicks == 0) {
                unreveal();
            }
        }
    }

    private static void reveal(Minecraft mc) {
        Scoreboard sb = mc.level.getScoreboard();
        PlayerTeam team = sb.getPlayerTeam(TEAM);
        if (team == null) {
            team = sb.addPlayerTeam(TEAM);
            team.setColor(ChatFormatting.GOLD);
        }
        for (Entity e : mc.level.getEntitiesOfClass(LivingEntity.class, mc.player.getBoundingBox().inflate(EYE_RANGE), e -> e != mc.player && e.isAlive())) {
            if (!REVEALED.containsKey(e)) {
                REVEALED.put(e, e.isCurrentlyGlowing());
                String name = e.getScoreboardName();
                if (sb.getPlayersTeam(name) == null && sb.addPlayerToTeam(name, team)) {
                    TEAMED.add(name);
                }
            }
            e.setSharedFlag(6, true); // re-applied every tick: server syncs can reset it
        }
        REVEALED.keySet().removeIf(e -> !e.isAlive());
    }

    private static void unreveal() {
        Minecraft mc = Minecraft.getInstance();
        REVEALED.forEach((e, wasGlowing) -> {
            if (!wasGlowing) {
                e.setSharedFlag(6, false);
            }
        });
        REVEALED.clear();
        if (mc.level != null) {
            Scoreboard sb = mc.level.getScoreboard();
            PlayerTeam team = sb.getPlayerTeam(TEAM);
            if (team != null) {
                for (String name : TEAMED) {
                    if (sb.getPlayersTeam(name) == team) {
                        sb.removePlayerFromTeam(name, team);
                    }
                }
            }
        }
        TEAMED.clear();
    }

    /** Distance labels over every revealed creature, readable through walls. */
    public static void renderWorld(PoseStack pose, MultiBufferSource.BufferSource buffers, Camera camera, float pt) {
        if (eyeTicks <= 0 || REVEALED.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        Vec3 cam = camera.getPosition();
        for (Entity e : REVEALED.keySet()) {
            Vec3 at = e.getPosition(pt).add(0, e.getBbHeight() + 0.45, 0);
            double dist = at.distanceTo(cam);
            pose.pushPose();
            pose.translate(at.x - cam.x, at.y - cam.y, at.z - cam.z);
            pose.mulPose(camera.rotation());
            float s = 0.025f * (float) Math.max(1, dist / 8);
            pose.scale(s, -s, s);
            Component label = Component.literal(e.getDisplayName().getString() + "  " + (int) Math.round(e.distanceTo(mc.player)) + "m");
            Matrix4f m = pose.last().pose();
            float x = -font.width(label) / 2f;
            font.drawInBatch(label, x, 0, 0xFFFFD84A, false, m, buffers, Font.DisplayMode.SEE_THROUGH, 0x60201400, LightTexture.FULL_BRIGHT);
            pose.popPose();
        }
        buffers.endBatch();
    }

    /** HUD: astral vignette and instructions; Mind's Eye direction markers around the crosshair. */
    public static void renderHud(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        int w = g.guiWidth(), h = g.guiHeight();
        if (astralTicks > 0) {
            float pulse = 0.55f + 0.1f * Mth.sin((mc.player.tickCount + delta.getGameTimeDeltaPartialTick(true)) * 0.15f);
            RenderSystem.enableBlend();
            g.setColor(1f, 0.82f, 0.25f, pulse);
            g.blit(ModRenderTypes.VIGNETTE, 0, 0, w, h, 0, 0, 256, 256, 256, 256);
            g.setColor(1, 1, 1, 1);
            g.fill(0, 0, w, h, 0x18FFD22A);
            RenderSystem.disableBlend();
            Component line = Component.translatable("hud.infinitygauntlet.astral", Keybinds.EMERGENCY_DISCONNECT.getTranslatedKeyMessage(),
                    (astralTicks + 19) / 20);
            g.drawCenteredString(mc.font, line, w / 2, h - 70, 0xFFFFE680);
        }
        if (eyeTicks > 0 && !REVEALED.isEmpty()) {
            float fade = Math.min(1f, eyeTicks / 20f) * Math.min(1f, (eyeTotal - eyeTicks) / 6f + 0.2f);
            int cx = w / 2, cy = h / 2;
            Vec3 me = mc.player.position();
            float yaw = mc.player.getYRot() * Mth.DEG_TO_RAD;
            for (Entity e : REVEALED.keySet()) {
                Vec3 d = e.position().subtract(me);
                double dist = Math.sqrt(d.x * d.x + d.z * d.z);
                // relative bearing: 0 straight ahead, positive to the right
                double bearing = Math.atan2(d.x, d.z) + yaw;
                double ang = -bearing;
                int r = 34 + (int) Math.min(26, dist * 0.6);
                int x = cx + (int) (Math.sin(ang) * r);
                int y = cy - (int) (Math.cos(ang) * r);
                int alpha = (int) (fade * (dist < 16 ? 230 : 150)) << 24;
                int size = dist < 16 ? 2 : 1;
                g.fill(x - size, y - size, x + size + 1, y + size + 1, alpha | 0xFFD22A);
            }
            g.drawCenteredString(mc.font, Component.translatable("hud.infinitygauntlet.minds_eye", REVEALED.size()), cx, h - 70, (int) (fade * 255) << 24 | 0xFFE680);
        }
    }

    public static void fieldTick(LevitationFieldEntity field) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        float rad = field.visualRadius();
        for (int i = 0; i < Particles.scaled(3); i++) {
            double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * rad;
            Vec3 at = field.position().add(Math.cos(a) * d, 0.1, Math.sin(a) * d);
            Particles.mote(at, new Vec3(0, 0.05 + r.nextDouble() * 0.05, 0), i % 2 == 0 ? InfinityStone.MIND.color() : InfinityStone.MIND.highlight(), 0.07f, 50);
        }
    }

    public static void clear() {
        astralTicks = 0;
        eyeTicks = 0;
        REVEALED.clear();
        TEAMED.clear();
    }

    private MindClient() {
    }
}
