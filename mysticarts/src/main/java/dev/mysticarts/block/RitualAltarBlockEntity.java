package dev.mysticarts.block;

import dev.mysticarts.registry.MaBlockEntities;
import dev.mysticarts.registry.MaParticles;
import dev.mysticarts.registry.MaSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

public class RitualAltarBlockEntity extends BlockEntity {
    public static final int DURATION = 80;

    /** Ticks into the current ritual, or -1 when idle. Synced for the renderer. */
    public int progress = -1;
    public int color = 0xFF9A2E;
    private ItemStack pending = ItemStack.EMPTY;

    public RitualAltarBlockEntity(BlockPos pos, BlockState state) {
        super(MaBlockEntities.RITUAL_ALTAR.get(), pos, state);
    }

    private AABB area() {
        return new AABB(worldPosition).inflate(1.5, 0, 1.5).expandTowards(0, 2, 0);
    }

    public Component invoke(Player player) {
        if (progress >= 0) return Component.translatable("message.mysticarts.ritual_busy");
        List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, area(), ItemEntity::isAlive);
        List<ItemStack> pool = new ArrayList<>();
        for (ItemEntity e : drops) pool.add(e.getItem());
        RitualRecipes.Recipe recipe = RitualRecipes.match(pool);
        if (recipe == null) return Component.translatable("message.mysticarts.ritual_none");
        for (RitualRecipes.Ingredient in : recipe.inputs()) {
            int need = in.count();
            for (ItemEntity e : drops) {
                if (need <= 0) break;
                ItemStack s = e.getItem();
                if (!s.is(in.item().get())) continue;
                int take = Math.min(need, s.getCount());
                s.shrink(take);
                need -= take;
                if (s.isEmpty()) e.discard();
                else e.setItem(s);
            }
        }
        pending = recipe.output();
        color = recipe.color();
        progress = 0;
        level.playSound(null, worldPosition, MaSounds.CAST_MYSTIC.get(), SoundSource.BLOCKS, 1f, 0.8f);
        sync();
        return Component.translatable("message.mysticarts.ritual_begin", recipe.output().getHoverName());
    }

    public static void tick(Level level, BlockPos pos, BlockState state, RitualAltarBlockEntity be) {
        if (be.progress < 0) return;
        be.progress++;
        if (level.isClientSide) {
            float t = be.progress / (float) DURATION;
            for (int i = 0; i < 3; i++) {
                double a = level.random.nextDouble() * Math.PI * 2, r = 2.5 - t * 1.5;
                level.addParticle(MaParticles.INFALL.get().with(be.color, 0.35f, 30), pos.getX() + 0.5 + Math.cos(a) * r,
                        pos.getY() + 1 + level.random.nextDouble() * 1.5, pos.getZ() + 0.5 + Math.sin(a) * r, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5);
            }
            return;
        }
        if (be.progress >= DURATION) {
            ServerLevel server = (ServerLevel) level;
            ItemEntity out = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, be.pending.copy());
            out.setDeltaMovement(0, 0.15, 0);
            out.setGlowingTag(true);
            level.addFreshEntity(out);
            server.sendParticles(MaParticles.RING.get().with(be.color, 2.5f, 20), pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 1, 0, 0, 0, 0);
            server.sendParticles(MaParticles.SPARK.get().with(be.color, 0.6f, 25), pos.getX() + 0.5, pos.getY() + 1.6, pos.getZ() + 0.5, 40, 0.3, 0.3, 0.3, 0.25);
            level.playSound(null, pos, MaSounds.RITUAL_COMPLETE.get(), SoundSource.BLOCKS, 1.2f, 1f);
            be.pending = ItemStack.EMPTY;
            be.progress = -1;
            be.sync();
        }
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("progress", progress);
        tag.putInt("color", color);
        if (!pending.isEmpty()) tag.put("pending", pending.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.contains("progress") ? tag.getInt("progress") : -1;
        color = tag.getInt("color");
        pending = tag.contains("pending") ? ItemStack.parse(registries, tag.getCompound("pending")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag t = new CompoundTag();
        t.putInt("progress", progress);
        t.putInt("color", color);
        return t;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
