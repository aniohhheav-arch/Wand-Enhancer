package com.tardismod.block;

import com.tardismod.Interior;
import com.tardismod.Travel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/** One console station. 0 = destination dimension, 1 = random coordinates, 2 = flight lever, 3 = status readout. */
public class ConsoleBlock extends Block {
    public static final IntegerProperty STATION = IntegerProperty.create("station", 0, 3);

    public ConsoleBlock(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(STATION, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(STATION); }

    private static void msg(Player p, Component c, boolean overlay) { if (p instanceof net.minecraft.server.level.ServerPlayer sp) sp.sendSystemMessage(c, overlay); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide() || !(level instanceof ServerLevel sl)) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(Interior.DOOR_POS) instanceof TardisBlockEntity door)) { msg(player, Component.literal("The console has no door link."), false); return InteractionResult.SUCCESS; }
        switch (state.getValue(STATION)) {
            case 0 -> { Travel.cycleDimension(door); msg(player, Component.literal("Destination: " + Travel.describe(door)), true); sl.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1f, 1.2f); }
            case 1 -> { Travel.randomise(door, level.getRandom()); msg(player, Component.literal("Destination: " + Travel.describe(door)), true); sl.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 1f, 0.8f); }
            case 2 -> { sl.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1f, 0.7f); msg(player, Component.literal(Travel.fly(sl, door)), true); }
            default -> msg(player, Component.literal("Destination: " + Travel.describe(door) + " | TARDIS now at " + (door.returnDimension().isEmpty() ? "unknown" : door.returnDimension() + " " + door.returnPos().toShortString())), false);
        }
        return InteractionResult.SUCCESS;
    }
}
