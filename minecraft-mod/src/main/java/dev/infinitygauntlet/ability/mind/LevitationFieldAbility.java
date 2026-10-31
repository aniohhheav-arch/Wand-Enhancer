package dev.infinitygauntlet.ability.mind;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityContext;
import dev.infinitygauntlet.ability.CastResult;
import dev.infinitygauntlet.mind.LevitationFieldEntity;
import dev.infinitygauntlet.mind.MindManager;
import dev.infinitygauntlet.power.PowerFx;
import dev.infinitygauntlet.power.PowerPose;
import dev.infinitygauntlet.registry.ModEntities;
import dev.infinitygauntlet.stone.InfinityStone;
import dev.infinitygauntlet.vfx.Vfx;
import dev.infinitygauntlet.vfx.VfxType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Levitation Field (see {@link LevitationFieldEntity}); recast to release everything at once. */
public final class LevitationFieldAbility extends Ability {
    public LevitationFieldAbility() {
        super("levitation_field", InfinityStone.MIND, 300);
    }

    @Nullable
    private static LevitationFieldEntity field(AbilityContext ctx) {
        for (LevitationFieldEntity f : ctx.level().getEntitiesOfClass(LevitationFieldEntity.class, new AABB(ctx.player().blockPosition()).inflate(64))) {
            if (f.owner() == ctx.player()) {
                return f;
            }
        }
        return null;
    }

    @Override
    public boolean bypassesCooldown(AbilityContext ctx) {
        return field(ctx) != null;
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LevitationFieldEntity existing = field(ctx);
        if (existing != null) {
            existing.release();
            PowerFx.pose(ctx.player(), PowerPose.PUSH, 10);
            PowerFx.anim(ctx, "mind_push");
            return CastResult.utility(Component.translatable("message.infinitygauntlet.levitation.released"));
        }
        Vec3 at = ctx.groundTarget(32, 10);
        LevitationFieldEntity field = new LevitationFieldEntity(ModEntities.LEVITATION_FIELD.get(), ctx.level());
        field.setup(ctx.player(), 400, 7f);
        field.moveTo(at.x, at.y, at.z);
        field.sampleGround(ctx.level());
        ctx.level().addFreshEntity(field);
        PowerFx.pose(ctx.player(), PowerPose.RAISE, 20);
        PowerFx.anim(ctx, "mind_focus");
        Vfx.send(ctx.level(), VfxType.GROUND_GLYPH, at.add(0, 0.05, 0), Vec3.ZERO, MindManager.GOLD, MindManager.PALE, 7f, 400);
        Vfx.send(ctx.level(), VfxType.NEURAL_BEAM, ctx.handPos(), at.add(0, 3, 0), MindManager.GOLD, MindManager.PALE, 0.15f, 10);
        return CastResult.SUCCESS;
    }
}
