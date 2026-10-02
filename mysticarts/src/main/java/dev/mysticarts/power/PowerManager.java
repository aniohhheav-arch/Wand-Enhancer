package dev.mysticarts.power;

import dev.mysticarts.MaConfig;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.item.Artifact;
import dev.mysticarts.item.InfinityGauntletItem;
import dev.mysticarts.item.Vestments;
import dev.mysticarts.network.Payloads;
import dev.mysticarts.registry.MaAttachments;
import dev.mysticarts.registry.MaItems;
import dev.mysticarts.registry.MaSounds;
import dev.mysticarts.world.MirrorDimension;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Server-authoritative cast pipeline: validates requirements, energy, cooldowns and ultimate charge, runs the spell,
 * charges its cost and keeps the client in sync.
 */
public final class PowerManager {
    public static final float ULTIMATE_MAX = 100f;
    public static final int MAX_CHARGE_TICKS = 50;

    private PowerManager() {}

    public static PowerData data(Player player) {
        return player.getData(MaAttachments.POWER);
    }

    public static boolean unlimited(Player player) {
        return player.getAbilities().instabuild && MaConfig.CREATIVE_UNLIMITED.get();
    }

    public static boolean hasArtifact(Player player, Artifact slot) {
        return !data(player).artifacts[slot.ordinal()].isEmpty();
    }

    public static boolean hasArtifact(PowerData data, Artifact slot) {
        return !data.artifacts[slot.ordinal()].isEmpty();
    }

    // ============================================================================================ requirements

    /** Null when the ability may be cast right now, otherwise the reason (a translation key). */
    @Nullable
    public static String blocker(ServerPlayer player, PowerData data, Ability a) {
        boolean unlimited = unlimited(player);
        if (a.source == Source.MYSTIC) {
            boolean tomes = MaConfig.REQUIRE_TOMES.get() && !player.isCreative();
            switch (a.req) {
                case INITIATE -> {
                    if (tomes && (data.tiers & PowerData.TIER_INITIATE) == 0) return "message.mysticarts.need_initiate";
                }
                case ADEPT -> {
                    if (tomes && (data.tiers & PowerData.TIER_ADEPT) == 0) return "message.mysticarts.need_adept";
                }
                case RING -> {
                    if (!hasArtifact(data, Artifact.RING)) return "message.mysticarts.need_ring";
                }
                case EYE -> {
                    if (!hasArtifact(data, Artifact.AMULET)) return "message.mysticarts.need_eye";
                }
                case MIRROR -> {
                    if (!MirrorDimension.isMirror(player.level())) return "message.mysticarts.need_mirror";
                }
                default -> {}
            }
            if (data.astralTicks > 0 && a != Ability.ASTRAL_PROJECTION) return "message.mysticarts.astral_busy";
        } else {
            ItemStack gauntlet = InfinityGauntletItem.held(player);
            if (gauntlet.isEmpty()) return "message.mysticarts.need_gauntlet";
            int stones = InfinityGauntletItem.stones(gauntlet);
            if ((stones & a.stoneMask) != a.stoneMask) return a.source == Source.COMBO ? "message.mysticarts.need_combo" : "message.mysticarts.need_stone";
        }
        if (unlimited) return null;
        if (data.cooldown(a) > 0) return "message.mysticarts.cooldown";
        if (a.ultimate() && data.ultimate < ULTIMATE_MAX) return "message.mysticarts.need_ultimate";
        if (energy(data, a) < cost(player, data, a)) return "message.mysticarts.no_energy";
        return null;
    }

    public static float energy(PowerData data, Ability a) {
        return a.cosmic() ? data.cosmic : data.mystic;
    }

    public static float cost(Player player, PowerData data, Ability a) {
        float c = a.cost * MaConfig.COST_MULTIPLIER.get().floatValue();
        if (!a.cosmic()) {
            Vestments v = Vestments.worn(player);
            if (v == Vestments.ANCIENT_ONE) c *= 0.8f;
            if (hasArtifact(data, Artifact.BRACERS)) c *= 0.9f;
        }
        return c;
    }

    /** Cooldown length for HUD display; falls back to the base value if the config is not loaded on this side. */
    public static int cooldownTicksClient(Ability a) {
        try {
            return cooldownTicks(a);
        } catch (IllegalStateException e) {
            return a.cooldown;
        }
    }

    public static int cooldownTicks(Ability a) {
        return Math.max(0, Math.round(a.cooldown * MaConfig.COOLDOWN_MULTIPLIER.get().floatValue()));
    }

    // ============================================================================================ casting

    public static void handleSelect(ServerPlayer player, int id) {
        Ability a = Ability.byId(id);
        PowerData data = data(player);
        data.select(a);
        player.level().playSound(null, player.blockPosition(), MaSounds.UI_SELECT.get(), net.minecraft.sounds.SoundSource.PLAYERS, 0.4f, 1.2f);
        sync(player, data);
    }

    public static void handleCast(ServerPlayer player, int id, int phase) {
        if (player.isSpectator() && data(player).astralTicks <= 0) return;
        Ability a = Ability.byId(id);
        PowerData data = data(player);
        if (phase == Payloads.Cast.RELEASE) {
            if (data.charging >= 0 && data.chargeAbility == a.ordinal()) releaseCharge(player, data);
            return;
        }
        // Recasting a held grab throws it; recasting a toggle switches it off. Neither needs energy.
        if (Sustained.recast(player, data, a)) return;
        String blocked = blocker(player, data, a);
        if (blocked != null) {
            deny(player, blocked, a);
            return;
        }
        if (a.has(Ability.Flag.CHARGE)) {
            data.charging = 0;
            data.chargeAbility = a.ordinal();
            data.pose(Poses.CHARGE, MAX_CHARGE_TICKS + 5);
            Fx.sound(player, MaSounds.BLAST_CHARGE.get(), 0.8f, 1f);
            data.dirty = true;
            return;
        }
        execute(player, data, a, 1f);
    }

    private static void releaseCharge(ServerPlayer player, PowerData data) {
        Ability a = Ability.byId(data.chargeAbility);
        float charge = Math.min(1f, data.charging / (float) MAX_CHARGE_TICKS);
        data.charging = -1;
        data.chargeAbility = -1;
        data.dirty = true;
        if (blocker(player, data, a) == null) execute(player, data, a, charge);
    }

    /** Runs a spell and pays for it when it succeeds. */
    public static boolean execute(ServerPlayer player, PowerData data, Ability a, float charge) {
        Spell spell = Spells.get(a);
        if (spell == null) return false;
        ServerLevel level = player.serverLevel();
        boolean ok = spell.cast(new Cast(player, level, data, a, charge));
        if (!ok) {
            Fx.sound(player, MaSounds.ENERGY_EMPTY.get(), 0.5f, 1.3f);
            return false;
        }
        pay(player, data, a, charge);
        if (data.castPoseTicks <= 0) data.pose(Poses.forAbility(a), 12);
        SoundEvent voice = a.cosmic() ? stoneSound(a.source) : MaSounds.CAST_MYSTIC.get();
        Fx.sound(player, voice, 0.7f, 1f);
        return true;
    }

    public static void pay(ServerPlayer player, PowerData data, Ability a, float charge) {
        if (!unlimited(player)) {
            float c = cost(player, data, a) * (a.has(Ability.Flag.CHARGE) ? 0.5f + charge * 0.5f : 1f);
            if (a.cosmic()) data.cosmic = Math.max(0, data.cosmic - c);
            else data.mystic = Math.max(0, data.mystic - c);
            data.cooldowns[a.ordinal()] = cooldownTicks(a);
            if (a.ultimate()) data.ultimate = 0;
        }
        if (!a.ultimate()) gainUltimate(data, a.cost * 0.12f);
        data.dirty = true;
    }

    public static void gainUltimate(PowerData data, float amount) {
        data.ultimate = Math.min(ULTIMATE_MAX, data.ultimate + amount * MaConfig.ULTIMATE_GAIN.get().floatValue());
        data.dirty = true;
    }

    private static void deny(ServerPlayer player, String key, Ability a) {
        Component name = Component.translatable(a.translationKey());
        Component msg = key.equals("message.mysticarts.cooldown")
                ? Component.translatable(key, name, String.format("%.1f", data(player).cooldown(a) / 20f))
                : Component.translatable(key, name);
        player.displayClientMessage(msg.copy().withColor(0xFF6A5A), true);
        Fx.sound(player, MaSounds.ENERGY_EMPTY.get(), 0.6f, 1f);
    }

    public static SoundEvent stoneSound(Source s) {
        return switch (s) {
            case SPACE -> MaSounds.STONE_SPACE.get();
            case MIND -> MaSounds.STONE_MIND.get();
            case REALITY -> MaSounds.STONE_REALITY.get();
            case POWER -> MaSounds.STONE_POWER.get();
            case TIME -> MaSounds.STONE_TIME.get();
            case SOUL -> MaSounds.STONE_SOUL.get();
            default -> MaSounds.CAST_COSMIC.get();
        };
    }

    // ============================================================================================ ticking

    public static void tick(ServerPlayer player) {
        PowerData data = data(player);
        boolean unlimited = unlimited(player);
        Vestments vest = Vestments.worn(player);
        float maxMystic = 100f + (vest != null ? vest.bonusEnergy : 0f) + (hasArtifact(data, Artifact.AMULET) ? 20f : 0f);
        float maxCosmic = 200f;
        if (maxMystic != data.maxMystic || maxCosmic != data.maxCosmic) {
            data.maxMystic = maxMystic;
            data.maxCosmic = maxCosmic;
            data.dirty = true;
        }
        boolean gauntlet = !InfinityGauntletItem.held(player).isEmpty();

        for (int i = 0; i < data.cooldowns.length; i++) {
            if (data.cooldowns[i] > 0 && --data.cooldowns[i] == 0 && Ability.values()[i].ultimate()) {
                Fx.sound(player, MaSounds.COOLDOWN_READY.get(), 0.6f, 1f);
            }
        }

        float mysticRegen = MaConfig.MYSTIC_REGEN.get().floatValue() / 20f * (vest != null ? vest.regen : 1f) * (hasArtifact(data, Artifact.BRACERS) ? 1.25f : 1f);
        float cosmicRegen = MaConfig.COSMIC_REGEN.get().floatValue() / 20f * (gauntlet ? 1f : 0.35f);
        if (data.shieldMode == 0 && data.beamTicks <= 0) data.mystic = Math.min(maxMystic, data.mystic + mysticRegen);
        if (data.beamTicks <= 0) data.cosmic = Math.min(maxCosmic, data.cosmic + cosmicRegen);
        if (gauntlet && player.tickCount % 20 == 0) gainUltimate(data, 0.5f);
        if (unlimited) {
            data.mystic = maxMystic;
            data.cosmic = maxCosmic;
            data.ultimate = ULTIMATE_MAX;
        }

        if (data.charging >= 0) {
            data.charging++;
            if (data.charging > MAX_CHARGE_TICKS + 40) releaseCharge(player, data);
        }
        if (data.castPoseTicks > 0 && --data.castPoseTicks == 0) {
            data.castPose = Poses.NONE;
            data.casterDirty = true;
        }

        Sustained.tick(player, data);
        Cloak.tick(player, data);

        data.idleSync++;
        if (data.dirty || data.idleSync >= 20) sync(player, data);
        if (data.casterDirty) broadcastCaster(player, data);
    }

    // ============================================================================================ sync

    public static void sync(ServerPlayer player, PowerData data) {
        int flags = 0;
        if (data.shieldMode == 1) flags |= Payloads.PowerSync.F_SHIELD;
        if (data.astralTicks > 0) flags |= Payloads.PowerSync.F_ASTRAL;
        if (data.absorbTicks > 0) flags |= Payloads.PowerSync.F_ABSORB;
        if (data.kineticTicks > 0) flags |= Payloads.PowerSync.F_KINETIC;
        if (data.superchargeHits > 0) flags |= Payloads.PowerSync.F_SUPERCHARGE;
        if (data.deflectTicks > 0) flags |= Payloads.PowerSync.F_DEFLECT;
        if (data.astralFormTicks > 0) flags |= Payloads.PowerSync.F_ASTRAL_FORM;
        if (MirrorDimension.isMirror(player.level())) flags |= Payloads.PowerSync.F_MIRROR;
        if (unlimited(player)) flags |= Payloads.PowerSync.F_UNLIMITED;
        if (data.cloakFlight) flags |= Payloads.PowerSync.F_CLOAK_FLIGHT;
        if (data.slingAnchor != null) flags |= Payloads.PowerSync.F_SLING_ANCHOR;
        if (data.spaceAnchor != null) flags |= Payloads.PowerSync.F_SPACE_ANCHOR;
        if (data.realityAnchor != null) flags |= Payloads.PowerSync.F_REALITY_ANCHOR;
        if (data.savedState != null) flags |= Payloads.PowerSync.F_SAVED_STATE;
        PacketDistributor.sendToPlayer(player, new Payloads.PowerSync(data.mystic, data.cosmic, data.ultimate, data.maxMystic, data.maxCosmic,
                data.tiers, data.souls, data.activeSource.ordinal(), data.selected.clone(), data.cooldowns.clone(), flags,
                data.charging, data.windupAbility, data.windupTicks));
        data.dirty = false;
        data.idleSync = 0;
    }

    public static Payloads.CasterState casterState(ServerPlayer player, PowerData data) {
        int artifacts = 0;
        for (Artifact a : Artifact.values()) if (hasArtifact(data, a)) artifacts |= 1 << a.ordinal();
        int flags = 0;
        if (player.getAbilities().flying && hasArtifact(data, Artifact.CLOAK)) flags |= Payloads.CasterState.F_FLYING;
        if (data.astralTicks > 0) flags |= Payloads.CasterState.F_ASTRAL;
        if (data.astralFormTicks > 0) flags |= Payloads.CasterState.F_ASTRAL_FORM;
        if (data.charging >= 0 || data.windupAbility >= 0) flags |= Payloads.CasterState.F_CHARGING;
        if (data.absorbTicks > 0) flags |= Payloads.CasterState.F_ABSORB;
        if (data.kineticTicks > 0) flags |= Payloads.CasterState.F_KINETIC;
        if (data.deflectTicks > 0) flags |= Payloads.CasterState.F_DEFLECT;
        int beam = data.beamTicks > 0 ? data.beamAbility : -1;
        int pose = data.castPose;
        int poseTicks = data.castPoseTicks;
        if (data.windupAbility >= 0) {
            pose = data.windupAbility == Ability.THE_SNAP.ordinal() ? Poses.SNAP : Poses.RAISE;
            poseTicks = data.windupTicks + 2;
        }
        return new Payloads.CasterState(player.getId(), data.shieldMode, data.heldEntity, data.holdKind, beam, pose, poseTicks, artifacts, flags);
    }

    public static void broadcastCaster(ServerPlayer player, PowerData data) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, casterState(player, data));
        data.casterDirty = false;
    }

    /** Whether the stack is the cloak (used by renderers on the logical client too). */
    public static boolean isCloak(ItemStack stack) {
        return stack.is(MaItems.CLOAK_OF_LEVITATION.get());
    }
}
