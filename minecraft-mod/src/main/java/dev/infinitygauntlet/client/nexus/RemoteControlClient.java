package dev.infinitygauntlet.client.nexus;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.client.Keybinds;
import dev.infinitygauntlet.network.ControlInputPayload;
import dev.infinitygauntlet.network.PossessionActionPayload;
import dev.infinitygauntlet.network.PossessionStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The operator's side of remote possession. While at the controls the player's own body stays put, facing the Nexus
 * screen: WASD/jump/sprint are captured and zeroed for the player, mouse movement is redirected from the player's
 * head to the creature's view, and attack/use become the creature's natural attack and special action. Everything is
 * forwarded to the server every tick; the server decides what actually happens.
 */
@EventBusSubscriber(modid = InfinityGauntlet.MOD_ID, value = Dist.CLIENT)
public final class RemoteControlClient {
    public static final float ANCHOR_PITCH = dev.infinitygauntlet.nexus.NexusBlockEntity.CONSOLE_PITCH;

    private static boolean controlling;
    private static BlockPos nexus = BlockPos.ZERO;
    private static int activeEntity = -1;
    private static float yaw, pitch, anchorYaw;
    private static float forward, strafe;
    private static boolean jump, sprint, wasShift;
    private static int pendingFlags;

    public static boolean isControlling() {
        return controlling;
    }

    public static int activeEntity() {
        return activeEntity;
    }

    public static BlockPos nexus() {
        return nexus;
    }

    public static float yaw() {
        return yaw;
    }

    public static float pitch() {
        return pitch;
    }

    public static void state(PossessionStatePayload p) {
        boolean entering = p.controlling() && (!controlling || p.activeEntity() != activeEntity);
        controlling = p.controlling();
        nexus = p.nexus();
        activeEntity = p.activeEntity();
        anchorYaw = p.anchorYaw();
        if (entering) {
            yaw = p.yaw();
            pitch = p.pitch();
            wasShift = true; // ignore a sneak still held from walking up to the console
        }
        if (!controlling) {
            forward = strafe = 0;
            jump = sprint = false;
        }
    }

    /** Runs after the mouse has turned the player this frame: move that rotation onto the creature instead. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onFrame(RenderFrameEvent.Pre event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!controlling || player == null) {
            return;
        }
        float dYaw = Mth.wrapDegrees(player.getYRot() - anchorYaw);
        float dPitch = player.getXRot() - ANCHOR_PITCH;
        yaw = Mth.wrapDegrees(yaw + dYaw);
        pitch = Mth.clamp(pitch + dPitch, -90, 90);
        player.setYRot(anchorYaw);
        player.yRotO = anchorYaw;
        player.setXRot(ANCHOR_PITCH);
        player.xRotO = ANCHOR_PITCH;
        player.setYHeadRot(anchorYaw);
        player.yHeadRotO = anchorYaw;
    }

    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!controlling) {
            return;
        }
        Input in = event.getInput();
        forward = in.forwardImpulse;
        strafe = in.leftImpulse;
        jump = in.jumping;
        sprint = Minecraft.getInstance().options.keySprint.isDown();
        if (in.shiftKeyDown && !wasShift) {
            PacketDistributor.sendToServer(new PossessionActionPayload(PossessionActionPayload.LEAVE));
        }
        wasShift = in.shiftKeyDown;
        in.forwardImpulse = 0;
        in.leftImpulse = 0;
        in.up = in.down = in.left = in.right = false;
        in.jumping = false;
        in.shiftKeyDown = false;
    }

    @SubscribeEvent
    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        if (!controlling) {
            return;
        }
        if (event.isAttack()) {
            pendingFlags |= ControlInputPayload.ATTACK;
        } else if (event.isUseItem()) {
            pendingFlags |= ControlInputPayload.SPECIAL;
        }
        event.setSwingHand(false);
        event.setCanceled(true);
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        while (Keybinds.EMERGENCY_DISCONNECT.consumeClick()) {
            if (controlling) {
                PacketDistributor.sendToServer(new PossessionActionPayload(PossessionActionPayload.DISCONNECT));
            }
        }
        send(Keybinds.REMOTE_BLINK, PossessionActionPayload.BLINK);
        send(Keybinds.NEURAL_OVERRIDE, PossessionActionPayload.OVERRIDE);
        send(Keybinds.PORTAL_EXTRACTION, PossessionActionPayload.EXTRACT);
        send(Keybinds.SWITCH_FEED, PossessionActionPayload.SWITCH);
        if (!controlling) {
            return;
        }
        int flags = pendingFlags | (jump ? ControlInputPayload.JUMP : 0) | (sprint ? ControlInputPayload.SPRINT : 0);
        PacketDistributor.sendToServer(new ControlInputPayload(forward, strafe, yaw, pitch, flags));
        pendingFlags = 0;
    }

    private static void send(net.minecraft.client.KeyMapping key, int action) {
        while (key.consumeClick()) {
            if (controlling) {
                PacketDistributor.sendToServer(new PossessionActionPayload(action));
            }
        }
    }

    @SubscribeEvent
    static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        controlling = false;
        activeEntity = -1;
    }

    private RemoteControlClient() {
    }
}
