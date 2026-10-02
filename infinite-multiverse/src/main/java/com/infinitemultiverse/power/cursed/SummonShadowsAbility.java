package com.infinitemultiverse.power.cursed;

import com.infinitemultiverse.core.cinematic.Cinematics;
import com.infinitemultiverse.core.cinematic.SceneIds;
import com.infinitemultiverse.core.MultiverseSystem;
import com.infinitemultiverse.core.ability.AbilityContext;
import com.infinitemultiverse.core.ability.Summons;
import com.infinitemultiverse.core.registry.ModSounds;
import com.infinitemultiverse.core.vfx.MultiverseVfx;
import com.infinitemultiverse.core.vfx.VfxIds;
import com.infinitemultiverse.power.PowerAbility;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.phys.Vec3;

/** Ten Shadows: Divine Dogs. Two shadow hounds rise from your shadow and hunt the nearest hostile for 40 seconds. */
public final class SummonShadowsAbility extends PowerAbility {
    private static final int LIFETIME = 800;
    private static final int COLOR = 0x1A1A2E;

    public SummonShadowsAbility() {
        super(MultiverseSystem.CURSED_TECHNIQUES);
    }

    @Override
    public boolean activate(AbilityContext ctx) {
        ServerPlayer player = ctx.player();
        Vec3 right = new Vec3(-Math.cos(Math.toRadians(player.getYRot())), 0, -Math.sin(Math.toRadians(player.getYRot())));
        for (int side : new int[]{-1, 1}) {
            Vec3 at = player.position().add(right.scale(side * 1.5));
            Wolf dog = Summons.spawnAlly(player, EntityType.WOLF, at, LIFETIME, COLOR);
            if (dog != null) {
                dog.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, LIFETIME, 1, false, false));
                dog.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, LIFETIME, 1, false, false));
                dog.setCustomName(Component.translatable("entity.infinitemultiverse.divine_dog").withStyle(side < 0 ? ChatFormatting.WHITE : ChatFormatting.DARK_GRAY));
                Cinematics.scene(ctx.level(), SceneIds.SHADOW_SUMMON, at, Vec3.ZERO, COLOR, 30, player, 1.4f);
            }
        }
        MultiverseVfx.sound(ctx.level(), player.position(), ModSounds.STAND_SUMMON, 1.0f, 0.6f);
        MultiverseVfx.shout(ctx.level(), player.position(), Component.translatable("message.infinitemultiverse.shout.divine_dogs")
                .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.BOLD), 24);
        return true;
    }
}
