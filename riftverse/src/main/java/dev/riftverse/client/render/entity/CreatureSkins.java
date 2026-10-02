package dev.riftverse.client.render.entity;

import dev.riftverse.Riftverse;
import dev.riftverse.entity.creature.CrystalSpiderEntity;
import dev.riftverse.entity.creature.VoidCultistEntity;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.BatRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.IronGolemRenderer;
import net.minecraft.client.renderer.entity.SpiderRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.IronGolem;

/** Renderers for creatures that wear Riftverse skins over vanilla body plans. */
public final class CreatureSkins {
    private CreatureSkins() {}

    public static final class VoidCultist extends HumanoidMobRenderer<VoidCultistEntity, PlayerModel<VoidCultistEntity>> {
        private static final ResourceLocation TEX = Riftverse.id("textures/entity/void_cultist.png");

        public VoidCultist(EntityRendererProvider.Context ctx) {
            super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5f);
        }

        @Override
        public ResourceLocation getTextureLocation(VoidCultistEntity entity) {
            return TEX;
        }
    }

    public static final class CrystalSpider extends SpiderRenderer<CrystalSpiderEntity> {
        private static final ResourceLocation TEX = Riftverse.id("textures/entity/crystal_spider.png");

        public CrystalSpider(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(CrystalSpiderEntity entity) {
            return TEX;
        }
    }

    public static final class StarMoth extends BatRenderer {
        private static final ResourceLocation TEX = Riftverse.id("textures/entity/star_moth.png");

        public StarMoth(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(Bat entity) {
            return TEX;
        }
    }

    public static final class LunarGolem extends IronGolemRenderer {
        private static final ResourceLocation TEX = Riftverse.id("textures/entity/lunar_golem.png");

        public LunarGolem(EntityRendererProvider.Context ctx) {
            super(ctx);
        }

        @Override
        public ResourceLocation getTextureLocation(IronGolem entity) {
            return TEX;
        }
    }
}
