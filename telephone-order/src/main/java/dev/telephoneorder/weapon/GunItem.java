package dev.telephoneorder.weapon;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.AttDef;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.ModBlocks;
import dev.telephoneorder.block.SentryBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** One class for every gun; behaviour comes from the catalog definition ({@link GunDef}) and {@link GunLogic}. */
public class GunItem extends Item implements com.geckolib.animatable.GeoItem {
    public final GunDef def;
    private final com.geckolib.animatable.instance.AnimatableInstanceCache geoCache = com.geckolib.util.GeckoLibUtil.createInstanceCache(this);

    public GunItem(Item.Properties props, GunDef def) {
        super(init(props, def));
        this.def = def;
        com.geckolib.animatable.GeoItem.registerSyncedAnimatable(this);
    }

    // ---- GeckoLib ----
    @Override
    public void registerControllers(com.geckolib.animatable.manager.AnimatableManager.ControllerRegistrar controllers) {
        String pre = "animation." + def.id() + ".";
        controllers.add(new com.geckolib.animation.AnimationController<GunItem>("main", 2,
                test -> test.setAndContinue(com.geckolib.animation.RawAnimation.begin().thenLoop(pre + "idle")))
                .triggerableAnim("fire", com.geckolib.animation.RawAnimation.begin().thenPlay(pre + "fire").thenLoop(pre + "idle"))
                .triggerableAnim("reload", com.geckolib.animation.RawAnimation.begin().thenPlay(pre + "reload").thenLoop(pre + "idle")));
    }

    @Override
    public com.geckolib.animatable.instance.AnimatableInstanceCache getAnimatableInstanceCache() { return geoCache; }

    @Override
    public void createGeoRenderer(Consumer<com.geckolib.animatable.client.GeoRenderProvider> consumer) {
        dev.telephoneorder.client.GunRenderProvider.provide(this, consumer);
    }

    /** Plays "fire" or "reload" on the held model for the owning player and everybody tracking them. */
    public void playAnim(ServerPlayer p, ItemStack stack, String anim) {
        if (p.level() instanceof ServerLevel sl) triggerAnim(p, com.geckolib.animatable.GeoItem.getOrAssignId(stack, sl), "main", anim);
    }

    private static Item.Properties init(Item.Properties p, GunDef def) {
        List<Boolean> flags = new ArrayList<>();
        for (int i = 0; i < Catalog.ATTS.size(); i++) flags.add(false);
        return p.component(DataComponents.CUSTOM_DATA, CustomData.of(GunData.fresh(def)))
                .component(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(List.of(0f), flags, List.of(""), List.of()));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel && player instanceof ServerPlayer sp) return GunLogic.onUse(sp, stack, this, hand);
        String hold = GunLogic.holdType(def);
        if (hold != null || GunLogic.currentMode(stack, def).equals("auto")) player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        if (!def.beh().equals("turret")) return InteractionResult.PASS;
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
        if (ctx.getClickedFace() == net.minecraft.core.Direction.DOWN) return InteractionResult.FAIL;
        BlockState here = level.getBlockState(pos);
        if (!here.canBeReplaced() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), net.minecraft.core.Direction.UP)) return InteractionResult.FAIL;
        if (!level.isClientSide()) {
            level.setBlock(pos, ModBlocks.SENTRY.defaultBlockState().setValue(SentryBlock.FACING, ctx.getHorizontalDirection()), 3);
            dev.telephoneorder.Fx.sound((ServerLevel) level, net.minecraft.world.phys.Vec3.atCenterOf(pos), "turret_deploy", SoundSource.BLOCKS, 1.0f, 1f);
            if (ctx.getPlayer() != null && !ctx.getPlayer().getAbilities().instabuild) ctx.getItemInHand().shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level instanceof ServerLevel && entity instanceof ServerPlayer sp) GunLogic.onUseTick(sp, stack, this, remaining);
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
        if (level instanceof ServerLevel && entity instanceof ServerPlayer sp) GunLogic.onRelease(sp, stack, this);
        return false;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, EquipmentSlot slot) {
        if (owner instanceof ServerPlayer sp) GunLogic.tickInventory(sp, stack, this, slot == EquipmentSlot.MAINHAND);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) { return 72000; }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) { return ItemUseAnimation.NONE; }

    @Override
    public boolean allowComponentsUpdateAnimation(Player player, InteractionHand hand, ItemStack oldStack, ItemStack newStack) { return false; }

    @Override
    public boolean allowContinuingBlockBreaking(Player player, ItemStack oldStack, ItemStack newStack) { return true; }

    @Override
    public boolean isBarVisible(ItemStack stack) { return !def.beh().equals("turret"); }

    @Override
    public int getBarWidth(ItemStack stack) {
        CustomData d = stack.get(DataComponents.CUSTOM_DATA);
        int ammo = d == null ? 0 : d.copyTag().getIntOr("ammo", 0);
        return Math.max(0, Math.min(13, Math.round(13f * ammo / Math.max(1, GunStats.of(def, stack).mag))));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        float heat = GunData.heat(stack);
        if (def.usesHeat()) {
            if (GunData.overheated(stack) || heat > 85) return 0xFF3030;
            if (heat > 55) return 0xFFA030;
        }
        return def.energy() ? 0x50E6FF : 0xE8C860;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext ctx, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
        GunStats st = GunStats.of(def, stack);
        out.accept(Component.literal(def.cat().toUpperCase() + "  •  " + Catalog.TIERS.get(def.tier()).toUpperCase()).withStyle(ChatFormatting.DARK_AQUA));
        out.accept(Component.literal(def.desc()).withStyle(ChatFormatting.GRAY));
        String dmg = def.pellets() > 1 ? String.format("%.1f x%d", def.dmg() * st.dmg, def.pellets()) : String.format("%.1f", def.dmg() * st.dmg);
        out.accept(stat("Damage", dmg));
        if (def.delay() > 0) out.accept(stat("Fire rate", Math.round(1200f / Math.max(1f, def.delay() * st.delay)) + " RPM"));
        out.accept(stat("Range", Math.round(def.range() * st.range) + " m"));
        out.accept(stat("Magazine", GunData.ammo(stack) + " / " + st.mag + "  (" + Catalog.AMMO_BY_ID.get(def.ammo()).name() + ")"));
        out.accept(stat("Reload", String.format("%.1fs%s", def.reload() * st.reload / 20f, def.perRound() ? " per round" : "")));
        out.accept(stat("Modes", String.join(" / ", def.modes()).toUpperCase()));
        if (def.usesHeat()) out.accept(stat("Heat", Math.round(GunData.heat(stack)) + "%"));
        String mod = GunData.ammoMod(stack);
        if (!mod.isEmpty()) out.accept(Component.literal("Loaded: " + mod.toUpperCase() + " rounds").withStyle(ChatFormatting.GOLD));
        List<AttDef> atts = GunData.attachments(stack);
        if (!atts.isEmpty()) {
            out.accept(Component.literal("Attachments:").withStyle(ChatFormatting.DARK_GRAY));
            for (AttDef a : atts) out.accept(Component.literal("  + " + a.name()).withStyle(ChatFormatting.GREEN));
        }
        if (!def.slots().isEmpty()) out.accept(Component.literal("Slots: " + String.join(", ", def.slots())).withStyle(ChatFormatting.DARK_GRAY));
        out.accept(Component.literal("Hold RIGHT CLICK to fire • R reload • B fire mode • ALT aim").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    private static Component stat(String k, String v) {
        return Component.literal(k + ": ").withStyle(ChatFormatting.GRAY).append(Component.literal(v).withStyle(ChatFormatting.WHITE));
    }
}
