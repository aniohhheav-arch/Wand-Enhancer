package dev.mysticarts.client;

import dev.mysticarts.MaConfig;
import dev.mysticarts.MysticArts;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.PowerData;
import dev.mysticarts.power.PowerManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/** Client-side view of an ability: icon, lock state and cost, for the radial menu and the HUD. */
public final class AbilityInfo {
    private AbilityInfo() {}

    public static ResourceLocation icon(Ability a) {
        return MysticArts.id("textures/gui/ability/" + a.id + ".png");
    }

    /** Translation key explaining why the ability cannot be used at all (ignoring cooldown/energy), or null. */
    @Nullable
    public static String locked(Ability a) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return null;
        if (a.cosmic()) {
            int stones = InfinityGauntletItem.heldStones(p);
            if (InfinityGauntletItem.held(p).isEmpty()) return "message.mysticarts.need_gauntlet";
            if ((stones & a.stoneMask) != a.stoneMask) return "message.mysticarts.need_stone";
            return null;
        }
        boolean tomes;
        try {
            tomes = MaConfig.REQUIRE_TOMES.get() && !p.isCreative();
        } catch (IllegalStateException e) {
            tomes = !p.isCreative();
        }
        int tiers = ClientPower.tiers();
        int id = p.getId();
        return switch (a.req) {
            case INITIATE -> tomes && (tiers & PowerData.TIER_INITIATE) == 0 ? "message.mysticarts.need_initiate" : null;
            case ADEPT -> tomes && (tiers & PowerData.TIER_ADEPT) == 0 ? "message.mysticarts.need_adept" : null;
            case RING -> CasterStates.hasArtifact(id, Artifact.RING.ordinal()) ? null : "message.mysticarts.need_ring";
            case EYE -> CasterStates.hasArtifact(id, Artifact.AMULET.ordinal()) ? null : "message.mysticarts.need_eye";
            case MIRROR -> ClientPower.has(Payloads.PowerSync.F_MIRROR) ? null : "message.mysticarts.need_mirror";
            default -> null;
        };
    }

    public static float cost(Ability a) {
        LocalPlayer p = Minecraft.getInstance().player;
        try {
            return p == null ? a.cost : PowerManager.cost(p, PowerManager.data(p), a);
        } catch (IllegalStateException e) {
            return a.cost;
        }
    }

    /** Whether the ability could be cast right now (energy, cooldown, ultimate charge). */
    public static boolean ready(Ability a) {
        if (locked(a) != null) return false;
        if (ClientPower.has(Payloads.PowerSync.F_UNLIMITED)) return true;
        if (ClientPower.cooldown(a) > 0) return false;
        if (a.ultimate() && ClientPower.ultimate() < PowerManager.ULTIMATE_MAX) return false;
        float energy = a.cosmic() ? ClientPower.cosmic() : ClientPower.mystic();
        return energy >= a.cost;
    }
}
