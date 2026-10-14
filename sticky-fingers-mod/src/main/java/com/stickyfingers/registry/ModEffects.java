package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.effect.ZipperEffect;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEffects {
    public static final DeferredRegister<MobEffect> REGISTER = DeferredRegister.create(Registries.MOB_EFFECT, StickyFingersMod.MODID);

    /** Target has been unzipped: frozen in place and visually separated. */
    public static final DeferredHolder<MobEffect, MobEffect> UNZIPPED = REGISTER.register("unzipped", () ->
            new ZipperEffect(MobEffectCategory.HARMFUL, 0x5B3FD6, true)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, StickyFingersMod.id("unzipped_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, StickyFingersMod.id("unzipped_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.ATTACK_DAMAGE, StickyFingersMod.id("unzipped_attack"), -0.5, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** Target is stitched to the surrounding terrain by zipper chains. */
    public static final DeferredHolder<MobEffect, MobEffect> ZIPPER_BOUND = REGISTER.register("zipper_bound", () ->
            new ZipperEffect(MobEffectCategory.HARMFUL, 0xE8A93B, true)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, StickyFingersMod.id("bound_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.JUMP_STRENGTH, StickyFingersMod.id("bound_jump"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.KNOCKBACK_RESISTANCE, StickyFingersMod.id("bound_kb"), 1.0, AttributeModifier.Operation.ADD_VALUE));
    /** The bearer is hidden inside zipper space. */
    public static final DeferredHolder<MobEffect, MobEffect> IN_ZIPPER = REGISTER.register("in_zipper", () ->
            new ZipperEffect(MobEffectCategory.BENEFICIAL, 0x1B1033, false)
                    .addAttributeModifier(Attributes.MOVEMENT_SPEED, StickyFingersMod.id("hidden_speed"), -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    /** The bearer's body is split by zippers: shrunk hitbox and dodges. */
    public static final DeferredHolder<MobEffect, MobEffect> ZIPPER_BODY = REGISTER.register("zipper_body", () ->
            new ZipperEffect(MobEffectCategory.BENEFICIAL, 0x7FD8FF, false)
                    .addAttributeModifier(Attributes.SCALE, StickyFingersMod.id("body_scale"), -0.45, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)
                    .addAttributeModifier(Attributes.STEP_HEIGHT, StickyFingersMod.id("body_step"), 0.5, AttributeModifier.Operation.ADD_VALUE));

    private ModEffects() {}
}
