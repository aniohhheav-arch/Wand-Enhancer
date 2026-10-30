package dev.arcana.item;

import dev.arcana.magic.CastService;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.registry.ModComponents;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.function.Consumer;

/** A wand of one magic school. Right-click casts the selected spell; hold to charge spells that support it. */
public class WandItem extends Item implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.arcana.wand.idle");
    private static final RawAnimation CHARGE = RawAnimation.begin().thenLoop("animation.arcana.wand.charge");
    private static final RawAnimation CAST = RawAnimation.begin().thenPlay("animation.arcana.wand.cast");

    public final School school;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WandItem(Properties props, School school) {
        super(props);
        this.school = school;
        GeoItem.registerSyncedAnimatable(this);
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 3, state -> {
            LivingEntity holder = state.getData(DataTickets.ENTITY) instanceof LivingEntity le ? le : null;
            boolean charging = holder != null && holder.isUsingItem() && holder.getUseItem().getItem() == this;
            return state.setAndContinue(charging ? CHARGE : IDLE);
        }).triggerableAnim("cast", CAST));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private dev.arcana.client.WandRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                if (renderer == null) renderer = new dev.arcana.client.WandRenderer();
                return renderer;
            }
        });
    }

    /** Plays the cast flourish on every client tracking {@code player}. */
    public void playCast(ServerPlayer player, ItemStack stack) {
        if (player.connection == null) return;
        try {
            triggerAnim(player, GeoItem.getOrAssignId(stack, player.serverLevel()), "main", "cast");
        } catch (UnsupportedOperationException ignored) {
            // fake/mocked connections cannot receive clientbound payloads
        }
    }

    // ---------------------------------------------------------------- component helpers

    public static int stageOf(ItemStack s) {
        if (s.getItem() instanceof WandItem w && w.school == School.NONE) return 2;
        return Mth.clamp(s.getOrDefault(ModComponents.STAGE.get(), 0), 0, 2);
    }

    public static WandStyle styleOf(ItemStack s) {
        return s.getOrDefault(ModComponents.STYLE.get(), WandStyle.DEFAULT);
    }

    public static School schoolOf(ItemStack s) {
        return s.getItem() instanceof WandItem w ? w.school : School.NONE;
    }

    public static Spell selectedOf(ItemStack s) {
        School school = schoolOf(s);
        Spell sel = Spell.byId(s.get(ModComponents.SELECTED.get()));
        if (sel != null && sel.school == school && (school == School.NONE || sel.minStage <= stageOf(s))) return sel;
        return Spell.defaultFor(school);
    }

    /** RGB tint of the crystal: dye colour if customised, otherwise the school colour (or the selected spell's school for the Forbidden Wand). */
    public static int tintOf(ItemStack s, School fallback) {
        int c = styleOf(s).color();
        if (c >= 0 && c < 16) return DyeColor.byId(c).getTextureDiffuseColor() & 0xFFFFFF;
        School sch = schoolOf(s);
        if (sch == School.NONE) {
            Spell sel = selectedOf(s);
            return sel.isMimic() ? sel.mimicked().color : School.NONE.color;
        }
        return fallback.color;
    }

    // ---------------------------------------------------------------- item behaviour

    @Override
    public boolean isFoil(ItemStack stack) {
        return stageOf(stack) >= 2;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, net.minecraft.core.BlockPos pos, Player player) {
        return !player.isCreative();
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        Spell spell = selectedOf(stack);
        if (level.isClientSide) {
            return spell.chargeTicks > 0 ? InteractionResultHolder.consume(stack) : InteractionResultHolder.success(stack);
        }
        ServerPlayer sp = (ServerPlayer) player;
        if (!CastService.canStart(sp, stack, spell)) return InteractionResultHolder.fail(stack);
        if (spell.chargeTicks > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        CastService.cast(sp, stack, spell, 1f, hand);
        return InteractionResultHolder.success(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remaining) {
        if (level.isClientSide || !(living instanceof ServerPlayer sp)) return;
        Spell spell = selectedOf(stack);
        int used = getUseDuration(stack, living) - remaining;
        if (spell.chargeTicks <= 0) return;
        CastService.chargeTick(sp, stack, spell, Math.min(1f, used / (float) spell.chargeTicks), used);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int remaining) {
        if (level.isClientSide || !(living instanceof ServerPlayer sp)) return;
        Spell spell = selectedOf(stack);
        int used = getUseDuration(stack, living) - remaining;
        float charge = spell.chargeTicks <= 0 ? 1f : Math.min(1f, used / (float) spell.chargeTicks);
        InteractionHand hand = sp.getUsedItemHand();
        CastService.cast(sp, stack, spell, Math.max(0.15f, charge), hand);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        int stage = stageOf(stack);
        if (school != School.NONE) {
            tip.add(Component.translatable("stage.arcana." + stage).withStyle(stage == 2 ? ChatFormatting.LIGHT_PURPLE : stage == 1 ? ChatFormatting.AQUA : ChatFormatting.GRAY));
        } else {
            tip.add(Component.translatable("tooltip.arcana.forbidden").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        }
        tip.add(Component.translatable("tooltip.arcana.selected", selectedOf(stack).displayName()).withStyle(ChatFormatting.GOLD));
        WandStyle st = styleOf(stack);
        if (st.handle() != 0) tip.add(Component.translatable("tooltip.arcana.handle", Component.translatable("handle.arcana." + st.handleType().id)).withStyle(ChatFormatting.DARK_GRAY));
        if (st.core() != 0) tip.add(Component.translatable("tooltip.arcana.core", Component.translatable("core.arcana." + st.coreType().id)).withStyle(ChatFormatting.DARK_GRAY));
        if (st.mod1() != 0) tip.add(Component.translatable("tooltip.arcana.rune", Component.translatable("rune.arcana." + st.rune1().id)).withStyle(ChatFormatting.DARK_GRAY));
        if (st.mod2() != 0) tip.add(Component.translatable("tooltip.arcana.rune", Component.translatable("rune.arcana." + st.rune2().id)).withStyle(ChatFormatting.DARK_GRAY));
        tip.add(Component.translatable("tooltip.arcana.menu_hint").withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
    }
}
