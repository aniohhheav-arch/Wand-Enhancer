package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import dev.riftverse.entity.BlackHoleEntity;
import dev.riftverse.entity.EnergyBoltEntity;
import dev.riftverse.entity.GravityWellEntity;
import dev.riftverse.entity.PortalBoltEntity;
import dev.riftverse.entity.PortalEntity;
import dev.riftverse.entity.SingularityGrenadeEntity;
import dev.riftverse.entity.boss.AbyssalLeviathanEntity;
import dev.riftverse.entity.boss.RiftWardenEntity;
import dev.riftverse.entity.creature.AstralJellyEntity;
import dev.riftverse.entity.creature.CrystalSentinelEntity;
import dev.riftverse.entity.creature.GlitchlingEntity;
import dev.riftverse.entity.creature.LumenStriderEntity;
import dev.riftverse.entity.creature.NeonDroneEntity;
import dev.riftverse.entity.creature.RiftWraithEntity;
import dev.riftverse.entity.creature.SkyWhaleEntity;
import dev.riftverse.entity.creature.VoidStalkerEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RvEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, Riftverse.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<BlackHoleEntity>> BLACK_HOLE = ENTITIES.register("black_hole",
            () -> EntityType.Builder.<BlackHoleEntity>of(BlackHoleEntity::new, MobCategory.MISC).sized(1.0F, 1.0F)
                    .clientTrackingRange(32).updateInterval(2).fireImmune().build("black_hole"));
    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.RealityTearEntity>> REALITY_TEAR = ENTITIES.register("reality_tear",
            () -> EntityType.Builder.<dev.riftverse.entity.RealityTearEntity>of(dev.riftverse.entity.RealityTearEntity::new, MobCategory.MISC).sized(1.0F, 3.0F)
                    .clientTrackingRange(16).updateInterval(2).fireImmune().noSave().build("reality_tear"));
    public static final DeferredHolder<EntityType<?>, EntityType<PortalEntity>> PORTAL = ENTITIES.register("portal",
            () -> EntityType.Builder.<PortalEntity>of(PortalEntity::new, MobCategory.MISC).sized(0.5F, 0.5F)
                    .clientTrackingRange(10).updateInterval(20).fireImmune().build("portal"));
    public static final DeferredHolder<EntityType<?>, EntityType<PortalBoltEntity>> PORTAL_BOLT = ENTITIES.register("portal_bolt",
            () -> EntityType.Builder.<PortalBoltEntity>of(PortalBoltEntity::new, MobCategory.MISC).sized(0.3F, 0.3F)
                    .clientTrackingRange(8).updateInterval(1).build("portal_bolt"));
    public static final DeferredHolder<EntityType<?>, EntityType<SingularityGrenadeEntity>> SINGULARITY_GRENADE = ENTITIES.register("singularity_grenade",
            () -> EntityType.Builder.<SingularityGrenadeEntity>of(SingularityGrenadeEntity::new, MobCategory.MISC).sized(0.3F, 0.3F)
                    .clientTrackingRange(8).updateInterval(1).build("singularity_grenade"));
    public static final DeferredHolder<EntityType<?>, EntityType<GravityWellEntity>> GRAVITY_WELL = ENTITIES.register("gravity_well",
            () -> EntityType.Builder.<GravityWellEntity>of(GravityWellEntity::new, MobCategory.MISC).sized(0.6F, 0.6F)
                    .clientTrackingRange(10).updateInterval(1).fireImmune().build("gravity_well"));
    public static final DeferredHolder<EntityType<?>, EntityType<EnergyBoltEntity>> ENERGY_BOLT = ENTITIES.register("energy_bolt",
            () -> EntityType.Builder.<EnergyBoltEntity>of(EnergyBoltEntity::new, MobCategory.MISC).sized(0.35F, 0.35F)
                    .clientTrackingRange(8).updateInterval(1).fireImmune().build("energy_bolt"));

    public static final DeferredHolder<EntityType<?>, EntityType<AstralJellyEntity>> ASTRAL_JELLY = ENTITIES.register("astral_jelly",
            () -> EntityType.Builder.<AstralJellyEntity>of(AstralJellyEntity::new, MobCategory.AMBIENT).sized(1.4F, 1.8F).clientTrackingRange(10).fireImmune().build("astral_jelly"));
    public static final DeferredHolder<EntityType<?>, EntityType<SkyWhaleEntity>> SKY_WHALE = ENTITIES.register("sky_whale",
            () -> EntityType.Builder.<SkyWhaleEntity>of(SkyWhaleEntity::new, MobCategory.CREATURE).sized(5.0F, 3.0F).clientTrackingRange(16).build("sky_whale"));
    public static final DeferredHolder<EntityType<?>, EntityType<LumenStriderEntity>> LUMEN_STRIDER = ENTITIES.register("lumen_strider",
            () -> EntityType.Builder.<LumenStriderEntity>of(LumenStriderEntity::new, MobCategory.CREATURE).sized(1.3F, 3.4F).clientTrackingRange(10).build("lumen_strider"));
    public static final DeferredHolder<EntityType<?>, EntityType<NeonDroneEntity>> NEON_DRONE = ENTITIES.register("neon_drone",
            () -> EntityType.Builder.<NeonDroneEntity>of(NeonDroneEntity::new, MobCategory.MONSTER).sized(0.9F, 0.9F).clientTrackingRange(10).fireImmune().build("neon_drone"));
    public static final DeferredHolder<EntityType<?>, EntityType<GlitchlingEntity>> GLITCHLING = ENTITIES.register("glitchling",
            () -> EntityType.Builder.<GlitchlingEntity>of(GlitchlingEntity::new, MobCategory.MONSTER).sized(0.8F, 1.7F).clientTrackingRange(10).build("glitchling"));
    public static final DeferredHolder<EntityType<?>, EntityType<VoidStalkerEntity>> VOID_STALKER = ENTITIES.register("void_stalker",
            () -> EntityType.Builder.<VoidStalkerEntity>of(VoidStalkerEntity::new, MobCategory.MONSTER).sized(0.8F, 2.6F).clientTrackingRange(10).build("void_stalker"));
    public static final DeferredHolder<EntityType<?>, EntityType<CrystalSentinelEntity>> CRYSTAL_SENTINEL = ENTITIES.register("crystal_sentinel",
            () -> EntityType.Builder.<CrystalSentinelEntity>of(CrystalSentinelEntity::new, MobCategory.MONSTER).sized(1.6F, 3.4F).clientTrackingRange(10).build("crystal_sentinel"));
    public static final DeferredHolder<EntityType<?>, EntityType<RiftWraithEntity>> RIFT_WRAITH = ENTITIES.register("rift_wraith",
            () -> EntityType.Builder.<RiftWraithEntity>of(RiftWraithEntity::new, MobCategory.MONSTER).sized(0.8F, 2.2F).clientTrackingRange(10).fireImmune().build("rift_wraith"));
    public static final DeferredHolder<EntityType<?>, EntityType<RiftWardenEntity>> RIFT_WARDEN = ENTITIES.register("rift_warden",
            () -> EntityType.Builder.<RiftWardenEntity>of(RiftWardenEntity::new, MobCategory.MONSTER).sized(2.2F, 5.2F).clientTrackingRange(16).fireImmune().build("rift_warden"));
    public static final DeferredHolder<EntityType<?>, EntityType<AbyssalLeviathanEntity>> ABYSSAL_LEVIATHAN = ENTITIES.register("abyssal_leviathan",
            () -> EntityType.Builder.<AbyssalLeviathanEntity>of(AbyssalLeviathanEntity::new, MobCategory.MONSTER).sized(4.0F, 3.5F).clientTrackingRange(20).fireImmune().build("abyssal_leviathan"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.boss.CosmicDeityEntity>> COSMIC_DEITY = ENTITIES.register("cosmic_deity",
            () -> EntityType.Builder.<dev.riftverse.entity.boss.CosmicDeityEntity>of(dev.riftverse.entity.boss.CosmicDeityEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.8F).clientTrackingRange(32).fireImmune().build("cosmic_deity"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.creature.VoidCultistEntity>> VOID_CULTIST = ENTITIES.register("void_cultist",
            () -> EntityType.Builder.<dev.riftverse.entity.creature.VoidCultistEntity>of(dev.riftverse.entity.creature.VoidCultistEntity::new, MobCategory.MONSTER).sized(0.6F, 1.95F).clientTrackingRange(10).build("void_cultist"));
    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.creature.CrystalSpiderEntity>> CRYSTAL_SPIDER = ENTITIES.register("crystal_spider",
            () -> EntityType.Builder.<dev.riftverse.entity.creature.CrystalSpiderEntity>of(dev.riftverse.entity.creature.CrystalSpiderEntity::new, MobCategory.MONSTER).sized(1.4F, 0.9F).clientTrackingRange(10).build("crystal_spider"));
    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.creature.StarMothEntity>> STAR_MOTH = ENTITIES.register("star_moth",
            () -> EntityType.Builder.<dev.riftverse.entity.creature.StarMothEntity>of(dev.riftverse.entity.creature.StarMothEntity::new, MobCategory.AMBIENT).sized(0.5F, 0.9F).clientTrackingRange(10).build("star_moth"));
    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.creature.LunarGolemEntity>> LUNAR_GOLEM = ENTITIES.register("lunar_golem",
            () -> EntityType.Builder.<dev.riftverse.entity.creature.LunarGolemEntity>of(dev.riftverse.entity.creature.LunarGolemEntity::new, MobCategory.MISC).sized(1.4F, 2.7F).clientTrackingRange(10).build("lunar_golem"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.creature.DenizenEntity>> DENIZEN = ENTITIES.register("denizen",
            () -> EntityType.Builder.<dev.riftverse.entity.creature.DenizenEntity>of(dev.riftverse.entity.creature.DenizenEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("denizen"));

    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.creature.TsaAgentEntity>> TSA_AGENT = ENTITIES.register("tsa_agent",
            () -> EntityType.Builder.<dev.riftverse.entity.creature.TsaAgentEntity>of(dev.riftverse.entity.creature.TsaAgentEntity::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).fireImmune().build("tsa_agent"));
    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.vehicle.DeLoreanEntity>> DELOREAN = ENTITIES.register("delorean",
            () -> EntityType.Builder.<dev.riftverse.entity.vehicle.DeLoreanEntity>of(dev.riftverse.entity.vehicle.DeLoreanEntity::new, MobCategory.MISC)
                    .sized(2.0F, 1.3F).clientTrackingRange(10).updateInterval(1).fireImmune().build("delorean"));
    public static final DeferredHolder<EntityType<?>, EntityType<dev.riftverse.entity.vehicle.TardisEntity>> TARDIS = ENTITIES.register("tardis",
            () -> EntityType.Builder.<dev.riftverse.entity.vehicle.TardisEntity>of(dev.riftverse.entity.vehicle.TardisEntity::new, MobCategory.MISC)
                    .sized(1.4F, 2.8F).clientTrackingRange(10).fireImmune().build("tardis"));

    private RvEntities() {}

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(ASTRAL_JELLY.get(), AstralJellyEntity.createAttributes().build());
        event.put(SKY_WHALE.get(), SkyWhaleEntity.createAttributes().build());
        event.put(LUMEN_STRIDER.get(), LumenStriderEntity.createAttributes().build());
        event.put(NEON_DRONE.get(), NeonDroneEntity.createAttributes().build());
        event.put(GLITCHLING.get(), GlitchlingEntity.createAttributes().build());
        event.put(VOID_STALKER.get(), VoidStalkerEntity.createAttributes().build());
        event.put(CRYSTAL_SENTINEL.get(), CrystalSentinelEntity.createAttributes().build());
        event.put(RIFT_WRAITH.get(), RiftWraithEntity.createAttributes().build());
        event.put(RIFT_WARDEN.get(), RiftWardenEntity.createAttributes().build());
        event.put(ABYSSAL_LEVIATHAN.get(), AbyssalLeviathanEntity.createAttributes().build());
        event.put(VOID_CULTIST.get(), dev.riftverse.entity.creature.VoidCultistEntity.createAttributes().build());
        event.put(CRYSTAL_SPIDER.get(), dev.riftverse.entity.creature.CrystalSpiderEntity.createAttributes().build());
        event.put(STAR_MOTH.get(), dev.riftverse.entity.creature.StarMothEntity.createAttributes().build());
        event.put(LUNAR_GOLEM.get(), dev.riftverse.entity.creature.LunarGolemEntity.createAttributes().build());
        event.put(DENIZEN.get(), dev.riftverse.entity.creature.DenizenEntity.createAttributes().build());
        event.put(TSA_AGENT.get(), dev.riftverse.entity.creature.TsaAgentEntity.createAttributes().build());
        event.put(COSMIC_DEITY.get(), dev.riftverse.entity.boss.CosmicDeityEntity.createAttributes().build());
    }
}
