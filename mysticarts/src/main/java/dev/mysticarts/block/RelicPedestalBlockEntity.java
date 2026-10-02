package dev.mysticarts.block;

import dev.mysticarts.item.StoneItem;
import dev.mysticarts.power.Source;
import dev.mysticarts.registry.MaBlockEntities;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class RelicPedestalBlockEntity extends BlockEntity {
    private ItemStack relic = ItemStack.EMPTY;
    /** Set by worldgen: taking the relic awakens the shrine's guardians. */
    private boolean guarded;

    public RelicPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(MaBlockEntities.RELIC_PEDESTAL.get(), pos, state);
    }

    public ItemStack relic() {
        return relic;
    }

    public void setRelic(ItemStack stack, boolean guarded) {
        this.relic = stack;
        this.guarded = guarded;
        sync();
    }

    public void take(Player player) {
        ItemStack stack = relic;
        relic = ItemStack.EMPTY;
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        if (guarded && level instanceof ServerLevel server) awaken(server, stack, player);
        guarded = false;
        sync();
    }

    private void awaken(ServerLevel server, ItemStack stack, Player player) {
        Source stone = stack.getItem() instanceof StoneItem s ? s.stone() : Source.MYSTIC;
        int color = stone.color;
        BlockPos p = worldPosition.above();
        server.sendParticles(MaParticles.RING.get().with(color, 6f, 30), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 1, 0, 0, 0, 0);
        server.sendParticles(MaParticles.SPARK.get().with(color, 0.8f, 40), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 80, 0.5, 0.5, 0.5, 0.4);
        server.playSound(null, p, MaSounds.ULTIMATE_RELEASE.get(), SoundSource.HOSTILE, 1.5f, 1.4f);
        player.displayClientMessage(Component.translatable("message.mysticarts.guardians_awaken").withColor(color), true);
        EntityType<? extends Mob> guardian = switch (stone) {
            case POWER -> EntityType.BLAZE;
            case SPACE -> EntityType.ENDERMAN;
            case SOUL -> EntityType.WITHER_SKELETON;
            case TIME -> EntityType.STRAY;
            default -> EntityType.VINDICATOR;
        };
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3;
            BlockPos at = BlockPos.containing(p.getX() + Math.cos(a) * 4, p.getY(), p.getZ() + Math.sin(a) * 4);
            Mob mob = guardian.spawn(server, at, MobSpawnType.EVENT);
            if (mob != null) {
                mob.setTarget(player);
                mob.setGlowingTag(true);
                mob.setPersistenceRequired();
            }
        }
        if (stone != Source.SPACE) {
            Mob evoker = EntityType.EVOKER.spawn(server, p.offset(0, 0, 3), MobSpawnType.EVENT);
            if (evoker != null) evoker.setTarget(player);
        }
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RelicPedestalBlockEntity be) {
        if (!level.isClientSide || be.relic.isEmpty() || level.random.nextInt(3) != 0) return;
        int color = be.relic.getItem() instanceof StoneItem s ? s.stone().color : 0xFFD27A;
        double a = level.random.nextDouble() * Math.PI * 2;
        level.addParticle(MaParticles.MOTE.get().with(color, 0.4f, 40), pos.getX() + 0.5 + Math.cos(a) * 0.5, pos.getY() + 1.3,
                pos.getZ() + 0.5 + Math.sin(a) * 0.5, 0, 0.02, 0);
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!relic.isEmpty()) tag.put("relic", relic.save(registries));
        tag.putBoolean("guarded", guarded);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        relic = tag.contains("relic") ? ItemStack.parse(registries, tag.getCompound("relic")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        guarded = tag.getBoolean("guarded");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        if (!relic.isEmpty()) t.put("relic", relic.save(registries));
        return t;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
