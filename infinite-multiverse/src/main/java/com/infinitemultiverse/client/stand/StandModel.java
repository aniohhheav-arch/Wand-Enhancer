package com.infinitemultiverse.client.stand;

import com.infinitemultiverse.abilities.stand.StandAbilities;
import com.infinitemultiverse.stand.StandEntity;
import com.infinitemultiverse.stand.StandType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

/** Resolves geometry, texture and animations from the Stand's synced type, so one renderer serves every Stand. */
@SuppressWarnings("deprecation")
public final class StandModel extends GeoModel<StandEntity> {
    private static StandType type(StandEntity stand) {
        StandType type = stand.standType();
        return type != null ? type : StandAbilities.STAR_PLATINUM.get();
    }

    @Override
    public ResourceLocation getModelResource(StandEntity stand) {
        return type(stand).model();
    }

    @Override
    public ResourceLocation getTextureResource(StandEntity stand) {
        return type(stand).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(StandEntity stand) {
        return type(stand).animations();
    }

    @Override
    public void setCustomAnimations(StandEntity stand, long instanceId, AnimationState<StandEntity> state) {
        super.setCustomAnimations(stand, instanceId, state);
        GeoBone head = getAnimationProcessor().getBone("head");
        if (head != null) {
            head.setRotX(head.getRotX() - stand.lookPitch(state.getPartialTick()) * Mth.DEG_TO_RAD);
        }
    }
}
