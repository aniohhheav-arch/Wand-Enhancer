package dev.mysticarts.power;

import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.registry.MaSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Sneak-casting a bookmark ability stores a location or state instead of casting it. Free of cost and cooldown. */
public final class Anchors {
    private Anchors() {}

    public static boolean set(ServerPlayer player, PowerData data, Ability a) {
        if (PowerManager.blocker(player, data, a) instanceof String reason && !reason.equals("message.mysticarts.cooldown")
                && !reason.equals("message.mysticarts.no_energy") && !reason.equals("message.mysticarts.need_ultimate")) {
            return false;
        }
        PowerData.Anchor here = new PowerData.Anchor(player.level().dimension(), player.position(), player.getYRot(), player.getXRot());
        String key;
        switch (a) {
            case SLING_PORTAL -> {
                data.slingAnchor = here;
                key = "message.mysticarts.anchor_sling";
            }
            case SPACE_PORTAL, SPACE_ULTIMATE -> {
                data.spaceAnchor = here;
                key = "message.mysticarts.anchor_space";
            }
            case REALITY_RESTORE -> {
                data.realityAnchor = here;
                key = "message.mysticarts.anchor_reality";
            }
            case TIME_STONE_SAVESTATE -> {
                CompoundTag t = new CompoundTag();
                t.putString("dim", here.dimension().location().toString());
                t.putDouble("x", here.pos().x);
                t.putDouble("y", here.pos().y);
                t.putDouble("z", here.pos().z);
                t.putFloat("yaw", here.yaw());
                t.putFloat("pitch", here.pitch());
                t.putFloat("health", player.getHealth());
                t.putInt("food", player.getFoodData().getFoodLevel());
                t.putFloat("saturation", player.getFoodData().getSaturationLevel());
                t.putInt("air", player.getAirSupply());
                t.putInt("fire", player.getRemainingFireTicks());
                data.savedState = t;
                key = "message.mysticarts.state_saved";
            }
            default -> {
                return false;
            }
        }
        data.dirty = true;
        player.displayClientMessage(Component.translatable(key).withColor(a.color()), true);
        Fx.send(player.serverLevel(), FxKind.CAST_CIRCLE, player.position().add(0, 0.05, 0), new Vec3(0, 1, 0), a.color(), 1.4f, 30, player.getId());
        Fx.sound(player, MaSounds.UI_SELECT.get(), 0.8f, 0.7f);
        return true;
    }
}
