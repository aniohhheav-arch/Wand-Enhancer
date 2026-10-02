package dev.riftverse.item;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Places a vehicle entity on the clicked block, facing away from the player. Sneak-punch it to pick it back up. */
public class VehicleItem extends Item {
    private final Supplier<? extends EntityType<?>> type;

    public VehicleItem(Properties properties, Supplier<? extends EntityType<?>> type) {
        super(properties);
        this.type = type;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!(ctx.getLevel() instanceof ServerLevel level)) return InteractionResult.SUCCESS;
        BlockPos at = ctx.getClickedPos().relative(ctx.getClickedFace());
        Entity e = type.get().create(level);
        if (e == null) return InteractionResult.FAIL;
        float yaw = ctx.getPlayer() != null ? ctx.getPlayer().getYRot() : 0f;
        e.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0);
        if (!level.noCollision(e)) return InteractionResult.FAIL;
        level.addFreshEntity(e);
        if (ctx.getPlayer() == null || !ctx.getPlayer().isCreative()) ctx.getItemInHand().shrink(1);
        return InteractionResult.CONSUME;
    }
}
