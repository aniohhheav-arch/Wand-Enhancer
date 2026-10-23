package com.wandcraft;

import com.wandcraft.entity.SpellProjectile;
import com.wandcraft.entity.AshPile;
import com.wandcraft.entity.DarkClone;
import com.wandcraft.entity.DebrisEntity;
import com.wandcraft.entity.ShadowClone;
import com.wandcraft.entity.SpellBeam;
import com.wandcraft.entity.SpellProp;
import com.wandcraft.entity.SpellZone;
import com.wandcraft.item.WandItem;
import com.wandcraft.magic.Magic;
import com.wandcraft.magic.PlayerMagic;
import com.wandcraft.magic.Spell;
import com.wandcraft.magic.Spells;
import com.wandcraft.net.SelectSpellPayload;
import com.wandcraft.net.SyncMagicPayload;
import java.util.function.Supplier;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;

@Mod(WandCraft.ID)
public class WandCraft {
    public static final String ID = "wandcraft";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    private static BlockBehaviour.Properties iceProps() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS).mapColor(MapColor.ICE).friction(0.98f)
                .sound(SoundType.GLASS).noLootTable().noOcclusion();
    }

    /** Translucent conjured ice that never melts into water; used by Ice Bridge and Glacial Prison. */
    public static final DeferredBlock<Block> ICE_CONSTRUCT = BLOCKS.register("ice_construct", () -> new TransparentBlock(iceProps()));
    /** Cracked variant shown shortly before the ice expires. */
    public static final DeferredBlock<Block> ICE_CONSTRUCT_MELTING = BLOCKS.register("ice_construct_melting", () -> new TransparentBlock(iceProps()));
    public static final DeferredHolder<Item, WandItem> WAND = ITEMS.register("wand", () -> new WandItem(new Item.Properties()));
    public static final Supplier<EntityType<SpellProjectile>> SPELL_PROJECTILE = ENTITIES.register("spell_projectile",
            () -> EntityType.Builder.<SpellProjectile>of(SpellProjectile::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f).clientTrackingRange(8).updateInterval(1).noSave().build("spell_projectile"));
    public static final Supplier<EntityType<SpellZone>> SPELL_ZONE = ENTITIES.register("spell_zone",
            () -> EntityType.Builder.<SpellZone>of(SpellZone::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(20).noSave().fireImmune().build("spell_zone"));
    public static final Supplier<EntityType<SpellBeam>> SPELL_BEAM = ENTITIES.register("spell_beam",
            () -> EntityType.Builder.<SpellBeam>of(SpellBeam::new, MobCategory.MISC)
                    .sized(0.2f, 0.2f).clientTrackingRange(10).updateInterval(1).noSave().fireImmune().build("spell_beam"));
    public static final Supplier<EntityType<DebrisEntity>> DEBRIS = ENTITIES.register("debris",
            () -> EntityType.Builder.<DebrisEntity>of(DebrisEntity::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(8).updateInterval(1).noSave().build("debris"));
    public static final Supplier<EntityType<ShadowClone>> SHADOW_CLONE = ENTITIES.register("shadow_clone",
            () -> EntityType.Builder.<ShadowClone>of(ShadowClone::new, MobCategory.MISC)
                    .sized(0.6f, 1.8f).clientTrackingRange(8).noSave().build("shadow_clone"));
    public static final Supplier<EntityType<DarkClone>> DARK_CLONE = ENTITIES.register("dark_clone",
            () -> EntityType.Builder.<DarkClone>of(DarkClone::new, MobCategory.MISC)
                    .sized(0.7f, 2.2f).clientTrackingRange(10).fireImmune().noSave().build("dark_clone"));
    public static final Supplier<EntityType<AshPile>> ASH_PILE = ENTITIES.register("ash_pile",
            () -> EntityType.Builder.<AshPile>of(AshPile::new, MobCategory.MISC)
                    .sized(1.2f, 0.3f).clientTrackingRange(8).updateInterval(40).fireImmune().build("ash_pile"));
    public static final Supplier<EntityType<SpellProp>> SPELL_PROP = ENTITIES.register("spell_prop",
            () -> EntityType.Builder.<SpellProp>of(SpellProp::new, MobCategory.MISC)
                    .sized(0.5f, 0.5f).clientTrackingRange(10).updateInterval(2).noSave().fireImmune().build("spell_prop"));
    public static final Supplier<AttachmentType<PlayerMagic>> MAGIC = ATTACHMENTS.register("magic",
            () -> AttachmentType.serializable(PlayerMagic::new).copyOnDeath().build());
    public static final Supplier<CreativeModeTab> TAB = TABS.register("wandcraft", () -> CreativeModeTab.builder()
            .title(Component.literal("Wandcraft"))
            .icon(() -> WAND.get().getDefaultInstance())
            .displayItems((params, out) -> out.accept(WAND.get()))
            .build());

    public WandCraft(IEventBus bus, ModContainer container) {
        Spells.init();
        ModParticles.PARTICLES.register(bus);
        ModSounds.SOUNDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        ATTACHMENTS.register(bus);
        TABS.register(bus);
        bus.addListener(this::registerPayloads);
        bus.addListener((net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent e) -> {
            e.put(SHADOW_CLONE.get(), ShadowClone.attributes().build());
            e.put(DARK_CLONE.get(), DarkClone.attributes().build());
        });
        container.registerConfig(ModConfig.Type.COMMON, Config.COMMON);
        container.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(MagicEvents.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.wandcraft.magic.DefenseEvents.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.wandcraft.magic.DarkEvents.class);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(com.wandcraft.magic.Possession.class);
    }

    private void registerPayloads(RegisterPayloadHandlersEvent e) {
        PayloadRegistrar r = e.registrar("1");
        r.playToServer(SelectSpellPayload.TYPE, SelectSpellPayload.CODEC, (payload, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer p)) return;
            Spell s = Spells.get(payload.spellId());
            PlayerMagic m = Magic.of(p);
            if (s == null || p.isUsingItem() || !Magic.isUnlocked(p, s)) {
                Magic.sync(p);
                return;
            }
            m.selected = s.id;
            m.dirty = true;
        });
        r.playToClient(com.wandcraft.net.PossessionPayload.TYPE, com.wandcraft.net.PossessionPayload.CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> com.wandcraft.magic.Possession.clientSet(payload.entityId(), payload.elapsed(), ctx.player().level().getGameTime())));
        r.playToClient(SyncMagicPayload.TYPE, SyncMagicPayload.CODEC, (payload, ctx) ->
                ctx.enqueueWork(() -> Magic.of(ctx.player()).deserializeNBT(ctx.player().registryAccess(), payload.data())));
    }
}
