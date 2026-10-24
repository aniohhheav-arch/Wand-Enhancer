package dev.telephoneorder.client;

import dev.telephoneorder.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Client-only transient state: camera effects, hit markers, lock-on, notifications, last phone snapshot. */
public final class ClientState {
    private ClientState() {}

    public static final Random RNG = new Random();

    // recoil is applied over a few ticks so it reads as a kick + recovery
    public static float recoilPitchLeft, recoilYawLeft, recoverPitch;
    public static float shake, shakeTicksLeft, shakeTotal;
    public static float lastShakeX, lastShakeY;
    public static int flashRgb, flashTicks, flashTotal;
    public static int hitMarkerTicks, hitMarkerKind;
    public static int lockEntity = -1, lockProgress; public static boolean lockLocked;
    public static boolean aiming;
    public static float aimBlend;               // 0..1 smoothed ADS
    public static float swayX, swayY;           // last applied scope sway (to undo)

    public static CompoundTag phone;            // latest PhoneState
    public static int phoneModel;
    public static long phoneStateStamp;

    public record Toast(String text, int color, long born) {}
    public static final List<Toast> TOASTS = new ArrayList<>();

    public static void toast(String text, int color) {
        TOASTS.add(new Toast(text, color, System.currentTimeMillis()));
        if (TOASTS.size() > 4) TOASTS.remove(0);
    }

    public static void addRecoil(float pitch, float yaw) {
        ModConfig c = ModConfig.get();
        float m = c.reduceCameraMotion ? 0.15f : c.recoilVisual;
        recoilPitchLeft += pitch * m;
        recoilYawLeft += yaw * m;
        recoverPitch += pitch * m * 0.6f;
    }

    public static void addShake(float strength, int ticks) {
        ModConfig c = ModConfig.get();
        if (!c.screenShake || c.reduceCameraMotion) return;
        shake = Math.max(shake, strength * c.shakeIntensity);
        shakeTicksLeft = Math.max(shakeTicksLeft, ticks);
        shakeTotal = Math.max(shakeTotal, ticks);
    }

    public static void flash(int rgb, int ticks) {
        flashRgb = rgb; flashTicks = ticks; flashTotal = ticks;
    }

    /** called every client tick (after the world tick) */
    public static void tick(Minecraft mc) {
        if (mc.player == null) return;
        ModConfig cfg = ModConfig.get();
        // --- recoil: apply a fraction per tick, then recover
        float stepP = recoilPitchLeft * 0.55f, stepY = recoilYawLeft * 0.55f;
        recoilPitchLeft -= stepP; recoilYawLeft -= stepY;
        float rec = Math.min(Math.abs(recoverPitch), 0.25f) * Math.signum(recoverPitch);
        recoverPitch -= rec;
        mc.player.setXRot(net.minecraft.util.Mth.clamp(mc.player.getXRot() - stepP + rec * 0.0f, -90f, 90f));
        mc.player.setYRot(mc.player.getYRot() + stepY);
        // --- screen shake (zero-mean: undo the previous offset before applying the next)
        float undoX = lastShakeX, undoY = lastShakeY;
        lastShakeX = 0; lastShakeY = 0;
        if (shakeTicksLeft > 0 && cfg.screenShake && !cfg.reduceCameraMotion) {
            float k = shakeTicksLeft / Math.max(1f, shakeTotal);
            float amp = shake * k * 1.4f;
            lastShakeX = (RNG.nextFloat() - 0.5f) * amp;
            lastShakeY = (RNG.nextFloat() - 0.5f) * amp;
            shakeTicksLeft--;
            if (shakeTicksLeft <= 0) { shake = 0; shakeTotal = 0; }
        }
        // --- scope sway (breathing) while aiming a scoped weapon, damped when crouching
        float undoSX = swayX, undoSY = swayY;
        swayX = 0; swayY = 0;
        if (aimBlend > 0.6f && !cfg.reduceCameraMotion && ScopeInfo.scoped(mc.player.getMainHandItem())) {
            double t = mc.player.tickCount * 0.09;
            float damp = mc.player.isShiftKeyDown() ? 0.25f : 1f;
            swayX = (float) (Math.sin(t * 1.3) * 0.22 + Math.sin(t * 0.37) * 0.15) * damp;
            swayY = (float) (Math.cos(t * 0.9) * 0.17) * damp;
        }
        float dx = lastShakeX - undoX + swayX - undoSX, dy = lastShakeY - undoY + swayY - undoSY;
        if (dx != 0 || dy != 0) {
            mc.player.setYRot(mc.player.getYRot() + dx);
            mc.player.setXRot(net.minecraft.util.Mth.clamp(mc.player.getXRot() + dy, -90f, 90f));
        }
        if (flashTicks > 0) flashTicks--;
        if (hitMarkerTicks > 0) hitMarkerTicks--;
        // smoothed ADS blend
        float target = aiming && mc.player.getMainHandItem().getItem() instanceof dev.telephoneorder.weapon.GunItem ? 1f : 0f;
        aimBlend += (target - aimBlend) * 0.35f;
        if (Math.abs(aimBlend - target) < 0.01f) aimBlend = target;
        TOASTS.removeIf(t -> System.currentTimeMillis() - t.born() > 5000);
    }
}
