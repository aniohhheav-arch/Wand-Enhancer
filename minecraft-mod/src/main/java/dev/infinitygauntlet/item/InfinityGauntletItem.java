package dev.infinitygauntlet.item;

import dev.infinitygauntlet.ability.Ability;
import dev.infinitygauntlet.ability.AbilityCaster;
import dev.infinitygauntlet.client.render.GauntletItemRenderer;
import dev.infinitygauntlet.component.GauntletState;
import dev.infinitygauntlet.registry.ModDataComponents;
import dev.infinitygauntlet.registry.ModItems;
import dev.infinitygauntlet.registry.ModSounds;
import dev.infinitygauntlet.registry.ModTriggers;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * The Infinity Gauntlet. Stones are installed bundle-style (right-click a stone onto the gauntlet in an inventory),
 * abilities are cast with use, and all state lives in the {@link GauntletState} data component.
 */
public class InfinityGauntletItem extends Item implements GeoItem {
    public static final String MAIN_CONTROLLER = "main";
    public static final String STONE_CONTROLLER = "stones";

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.infinity_gauntlet.idle");
    private static final RawAnimation INSERT = RawAnimation.begin().thenPlay("animation.infinity_gauntlet.insert");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.infinity_gauntlet.cast");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenPlay("animation.infinity_gauntlet.charge");
    private static final RawAnimation SNAP = RawAnimation.begin().thenPlay("animation.infinity_gauntlet.snap");
    private static final RawAnimation OVERLOAD = RawAnimation.begin().thenPlay("animation.infinity_gauntlet.overload");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public InfinityGauntletItem(Properties properties) {
        super(properties);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static ItemStack fullGauntlet() {
        ItemStack stack = new ItemStack(ModItems.INFINITY_GAUNTLET.get());
        GauntletState state = GauntletState.EMPTY;
        for (InfinityStone stone : InfinityStone.values()) {
            state = state.withStone(stone);
        }
        stack.set(ModDataComponents.GAUNTLET_STATE, state);
        return stack;
    }

    public static GauntletState state(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.GAUNTLET_STATE, GauntletState.EMPTY);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand == InteractionHand.MAIN_HAND && player.isShiftKeyDown() && player.getOffhandItem().getItem() instanceof InfinityStoneItem) {
            if (!level.isClientSide) {
                installFromOffhand(player);
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            AbilityCaster.tryCast(serverPlayer, hand, stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // ---------------------------------------------------------------- stone installation (bundle-style)

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack gauntlet, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        GauntletState state = state(gauntlet);
        if (other.getItem() instanceof InfinityStoneItem stoneItem) {
            if (state.has(stoneItem.stone())) {
                return false;
            }
            gauntlet.set(ModDataComponents.GAUNTLET_STATE, state.withStone(stoneItem.stone()));
            other.shrink(1);
            onStoneInstalled(player, gauntlet, stoneItem.stone());
            return true;
        }
        if (other.isEmpty() && !state.stones().isEmpty()) {
            InfinityStone removed = state.has(state.selected()) ? state.selected() : state.stones().getLast();
            gauntlet.set(ModDataComponents.GAUNTLET_STATE, state.withoutStone(removed));
            access.set(new ItemStack(ModItems.stone(removed)));
            player.playSound(ModSounds.GAUNTLET_SELECT.get(), 0.8f, 0.6f);
            return true;
        }
        return false;
    }

    /** Off-hand installation: holding the gauntlet in the main hand and a stone in the off hand, sneak + use. */
    public static boolean installFromOffhand(Player player) {
        ItemStack gauntlet = player.getMainHandItem();
        ItemStack stone = player.getOffhandItem();
        if (!(gauntlet.getItem() instanceof InfinityGauntletItem) || !(stone.getItem() instanceof InfinityStoneItem stoneItem)) {
            return false;
        }
        GauntletState state = state(gauntlet);
        if (state.has(stoneItem.stone())) {
            return false;
        }
        gauntlet.set(ModDataComponents.GAUNTLET_STATE, state.withStone(stoneItem.stone()));
        stone.shrink(1);
        onStoneInstalled(player, gauntlet, stoneItem.stone());
        return true;
    }

    private static void onStoneInstalled(Player player, ItemStack gauntlet, InfinityStone stone) {
        player.level().playSound(null, player.blockPosition(), ModSounds.STONE_INSERT.get(), SoundSource.PLAYERS, 1.0f, 1.0f);
        if (player instanceof ServerPlayer serverPlayer) {
            ModTriggers.STONE_INSTALLED.get().trigger(serverPlayer, stone.getSerializedName());
            if (state(gauntlet).isComplete()) {
                ModTriggers.STONE_INSTALLED.get().trigger(serverPlayer, "all");
            }
            if (gauntlet.getItem() instanceof InfinityGauntletItem item && canAnimate(serverPlayer)) {
                long id = GeoItem.getOrAssignId(gauntlet, serverPlayer.serverLevel());
                item.triggerAnim(serverPlayer, id, MAIN_CONTROLLER, "insert");
                item.triggerAnim(serverPlayer, id, STONE_CONTROLLER, "activate_" + stone.getSerializedName());
            }
        }
    }

    // ---------------------------------------------------------------- animation

    /** GeckoLib syncs triggered animations over its own channel; skip players whose connection lacks it (fake/test players). */
    private static final net.minecraft.resources.ResourceLocation GECKOLIB_TRIGGER = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
            "geckolib", "singleton_anim_trigger");

    private static boolean canAnimate(ServerPlayer player) {
        return player.connection != null && player.connection.hasChannel(GECKOLIB_TRIGGER);
    }

    public void playCastAnimation(ServerPlayer player, ItemStack stack, InfinityStone stone) {
        if (!canAnimate(player)) {
            return;
        }
        long id = GeoItem.getOrAssignId(stack, player.serverLevel());
        triggerAnim(player, id, MAIN_CONTROLLER, "cast");
        triggerAnim(player, id, STONE_CONTROLLER, "activate_" + stone.getSerializedName());
    }

    public void playAnimation(ServerPlayer player, ItemStack stack, String animation) {
        if (!canAnimate(player)) {
            return;
        }
        triggerAnim(player, GeoItem.getOrAssignId(stack, (ServerLevel) player.level()), MAIN_CONTROLLER, animation);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<InfinityGauntletItem> main = new AnimationController<>(this, MAIN_CONTROLLER, 3, state -> state.setAndContinue(IDLE))
                .triggerableAnim("insert", INSERT)
                .triggerableAnim("cast", CAST)
                .triggerableAnim("charge", CHARGE)
                .triggerableAnim("snap", SNAP)
                .triggerableAnim("overload", OVERLOAD);
        AnimationController<InfinityGauntletItem> stones = new AnimationController<>(this, STONE_CONTROLLER, 0, state -> PlayState.STOP);
        for (InfinityStone stone : InfinityStone.values()) {
            stones.triggerableAnim("activate_" + stone.getSerializedName(),
                    RawAnimation.begin().thenPlay("animation.infinity_gauntlet.activate_" + stone.getSerializedName()));
        }
        controllers.add(main, stones);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GauntletItemRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new GauntletItemRenderer();
                }
                return renderer;
            }
        });
    }

    // ---------------------------------------------------------------- tooltip

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        GauntletState state = state(stack);
        if (state.stones().isEmpty()) {
            tooltip.add(Component.translatable("tooltip.infinitygauntlet.gauntlet.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.infinitygauntlet.gauntlet.stones", state.stones().size()).withStyle(ChatFormatting.GRAY));
            for (InfinityStone stone : state.stones()) {
                tooltip.add(Component.literal(" ◆ ").withStyle(stone.format()).append(stone.displayName()));
            }
            Ability selected = AbilityCaster.selectedAbility(state);
            if (selected != null) {
                tooltip.add(Component.translatable("tooltip.infinitygauntlet.gauntlet.selected", selected.displayName().copy().withStyle(selected.stone().format()))
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        tooltip.add(Component.translatable("tooltip.infinitygauntlet.gauntlet.hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }
}
