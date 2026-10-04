package dev.finalframe.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.finalframe.client.choreo.WeaponState;
import dev.finalframe.client.render.EntityPoses;
import dev.finalframe.finisher.Ease;
import dev.finalframe.sheriff.SheriffsLastWordItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Everyday gunplay animation for the local player: the draw twirl, recoil kick, hammer fall and
 * re-cock, cylinder indexing and the reload spin.
 */
public final class RevolverAnimator {
    private static int clientTicks;
    private static float lastShot = -100;
    private static float reloadStart = -100;
    private static float lastEquip = 0;

    private RevolverAnimator() {
    }

    public static void tick() {
        clientTicks++;
    }

    private static float now() {
        return clientTicks + Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
    }

    public static void onUse(Player player, boolean fired) {
        if (player != Minecraft.getInstance().player) {
            return;
        }
        if (fired) {
            lastShot = now();
        } else {
            reloadStart = now();
        }
    }

    /** Cylinder and hammer for a revolver that is not being choreographed. */
    public static WeaponState idleState(ItemStack stack, ItemDisplayContext context) {
        int rounds = stack.getItem() instanceof SheriffsLastWordItem ? SheriffsLastWordItem.rounds(stack) : SheriffsLastWordItem.CAPACITY;
        float cylinder = (SheriffsLastWordItem.CAPACITY - rounds) * 60f;
        float hammer = 1f;
        if (isLocalView(context)) {
            float sinceShot = now() - lastShot;
            if (sinceShot >= 0 && sinceShot < 7) {
                cylinder -= 60f * (1 - Ease.outCubic(Ease.range(sinceShot, 2, 5)));
                hammer = sinceShot < 2 ? 0 : Ease.outBack(Ease.range(sinceShot, 3, 6));
            }
            float sinceReload = now() - reloadStart;
            if (sinceReload >= 0 && sinceReload < SheriffsLastWordItem.RELOAD_TICKS) {
                cylinder += 720f * Ease.inOutCubic(Ease.range(sinceReload, 18, SheriffsLastWordItem.RELOAD_TICKS - 2));
            }
        }
        return new WeaponState(WeaponState.Placement.HAND, 0, 0, 0, 0, cylinder, hammer);
    }

    private static boolean isLocalView(ItemDisplayContext context) {
        if (context.firstPerson()) {
            return true;
        }
        Entity holder = EntityPoses.current();
        return holder != null && holder == Minecraft.getInstance().player;
    }

    /** First-person arm transform replacing vanilla's, adding the draw twirl, recoil and reload tilt. */
    public static boolean applyHandTransform(PoseStack ps, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick,
                                             float equipProgress, float swingProgress) {
        int side = arm == HumanoidArm.RIGHT ? 1 : -1;
        ps.translate(side * 0.56f, -0.52f + equipProgress * -0.6f, -0.72f);

        if (equipProgress > 0.001f || lastEquip > 0.001f) {
            float twirl = Ease.inOutSine(equipProgress) * 360f;
            ps.translate(0, 0.08f, 0.05f);
            ps.mulPose(Axis.XP.rotationDegrees(-twirl * side));
            ps.translate(0, -0.08f, -0.05f);
        }
        lastEquip = equipProgress;

        float sinceShot = now() - lastShot;
        if (sinceShot >= 0 && sinceShot < 8) {
            float kick = sinceShot < 1 ? Ease.outCubic(sinceShot) : 1 - Ease.inOutSine(Ease.range(sinceShot, 1, 8));
            ps.translate(0, 0.04f * kick, 0.14f * kick);
            ps.mulPose(Axis.XP.rotationDegrees(22f * kick));
        }
        float sinceReload = now() - reloadStart;
        if (sinceReload >= 0 && sinceReload < SheriffsLastWordItem.RELOAD_TICKS) {
            float tilt = Ease.bump(sinceReload, 0, SheriffsLastWordItem.RELOAD_TICKS);
            ps.translate(-0.12f * side * tilt, 0.06f * tilt, 0);
            ps.mulPose(Axis.ZP.rotationDegrees(38f * side * tilt));
            ps.mulPose(Axis.XP.rotationDegrees(12f * tilt));
        }
        return true;
    }
}
