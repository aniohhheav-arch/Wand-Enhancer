package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.AbilityManager;
import com.infinitemultiverse.core.ability.Beams;
import com.infinitemultiverse.core.ability.Summons;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Cursed Spirit Manipulation: absorb weakened creatures into a stored collection, release them as allies, or pour them all into Maximum Uzumaki. */
public final class CursedSpirits {
    private static final String TAG = "infinitemultiverse_spirits";
    private static final int CAPACITY = 12;
    private static final int COLOR = 0x6A3FA0;

    private CursedSpirits() {
    }

    private static ListTag stored(ServerPlayer player) {
        if (!player.getPersistentData().contains(TAG, Tag.TAG_LIST)) {
            player.getPersistentData().put(TAG, new ListTag());
        }
        return player.getPersistentData().getList(TAG, Tag.TAG_STRING);
    }

    public static final class Absorb extends PowerAbility {
        public Absorb() {
            super(MultiverseSystem.CURSED_TECHNIQUES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            ListTag spirits = stored(player);
            if (spirits.size() >= CAPACITY) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.spirits_full", CAPACITY));
                return false;
            }
            Beams.Result trace = Beams.trace(player, 8, 0.8, 1);
            if (trace.hits().isEmpty() || !(trace.hits().get(0) instanceof Mob mob)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_target"));
                return false;
            }
            if (mob.getHealth() > Math.max(8f, mob.getMaxHealth() * 0.35f)) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.spirit_too_strong"));
                return false;
            }
            spirits.add(StringTag.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString()));
            player.getPersistentData().put(TAG, spirits);
            Cinematics.scene(ctx.level(), SceneIds.SPIRIT_ABSORB, mob.getBoundingBox().getCenter(), player.getEyePosition().subtract(mob.getBoundingBox().getCenter()), COLOR, 16, player, 1f);
            MultiverseVfx.sound(ctx.level(), mob.position(), ModSounds.STAND_DISMISS, 1f, 0.6f);
            mob.discard();
            player.displayClientMessage(Component.translatable("message.infinitemultiverse.spirit_absorbed", mob.getType().getDescription(), spirits.size(), CAPACITY)
                    .withStyle(ChatFormatting.DARK_PURPLE), true);
            return true;
        }
    }

    public static final class Release extends PowerAbility {
        public Release() {
            super(MultiverseSystem.CURSED_TECHNIQUES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            ListTag spirits = stored(player);
            if (spirits.isEmpty()) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.no_spirits"));
                return false;
            }
            ResourceLocation id = ResourceLocation.tryParse(spirits.getString(spirits.size() - 1));
            EntityType<?> type = id == null ? null : BuiltInRegistries.ENTITY_TYPE.get(id);
            spirits.remove(spirits.size() - 1);
            player.getPersistentData().put(TAG, spirits);
            if (type == null) {
                return false;
            }
            Vec3 at = player.position().add(player.getLookAngle().multiply(2, 0, 2));
            if (type.create(ctx.level()) instanceof Mob probe) {
                probe.discard();
                @SuppressWarnings("unchecked")
                EntityType<Mob> mobType = (EntityType<Mob>) type;
                Mob ally = Summons.spawnAlly(player, mobType, at, 900, COLOR);
                if (ally != null) {
                    ally.setCustomName(Component.translatable("entity.infinitemultiverse.cursed_spirit", type.getDescription()).withStyle(ChatFormatting.DARK_PURPLE));
                }
            }
            Cinematics.scene(ctx.level(), SceneIds.SPIRIT_RELEASE, at.add(0, 1.2, 0), player.getLookAngle(), COLOR, 24, player, 1f);
            return true;
        }
    }

    public static final class Uzumaki extends PowerAbility {
        public Uzumaki() {
            super(MultiverseSystem.CURSED_TECHNIQUES);
        }

        @Override
        public boolean activate(AbilityContext ctx) {
            ServerPlayer player = ctx.player();
            ListTag spirits = stored(player);
            if (spirits.size() < 3) {
                AbilityManager.deny(player, Component.translatable("message.infinitemultiverse.need_spirits", 3));
                return false;
            }
            float damage = 5f * spirits.size() * mastery(player, system());
            player.getPersistentData().put(TAG, new ListTag());
            Cinematics.scene(ctx.level(), SceneIds.UZUMAKI, player.getEyePosition(), player.getLookAngle().scale(40), COLOR, 50, player, 40f);
            player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE, 30, 3, false, false));
            com.infinitemultiverse.stand.StandScheduler.later(22, () -> {
                if (!player.isAlive()) {
                    return;
                }
                Beams.Result trace = Beams.trace(player, 40, 2.0, 12);
                for (LivingEntity target : trace.hits()) {
                    target.invulnerableTime = 0;
                    target.hurt(player.damageSources().indirectMagic(player, player), damage);
                }
            });
            MultiverseVfx.shout(ctx.level(), player.position(), Component.translatable("message.infinitemultiverse.shout.uzumaki")
                    .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD), 32);
            MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.SHOCKWAVE, 1.4f, 0.6f);
            return true;
        }
    }
}
