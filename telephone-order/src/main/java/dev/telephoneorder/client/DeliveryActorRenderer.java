package dev.telephoneorder.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.model.DefaultedEntityGeoModel;
import com.geckolib.model.GeoModel;
import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.entity.DeliveryActor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

public class DeliveryActorRenderer extends GeoEntityRenderer<DeliveryActor, EntityRenderState> {
    static final DataTicket<String> KIND = DataTicket.create("telephoneorder_kind", String.class);
    static final DataTicket<Float> SCALE = DataTicket.create("telephoneorder_scale", Float.class);
    static final DataTicket<Float> YAW = DataTicket.create("telephoneorder_yaw", Float.class);

    public DeliveryActorRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new Model());
        withRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void captureDefaultRenderState(DeliveryActor e, Void v, EntityRenderState s, float partial) {
        super.captureDefaultRenderState(e, v, s, partial);
        s.addGeckolibData(KIND, e.kind());
        s.addGeckolibData(SCALE, e.shownScale(partial));
        s.addGeckolibData(YAW, e.modelYaw());
    }

    @Override
    public void scaleModelForRender(RenderPassInfo<EntityRenderState> info, float w, float h) {
        float s = Math.max(0.001f, info.getOrDefaultGeckolibData(SCALE, 1f));
        super.scaleModelForRender(info, w * s, h * s);
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<EntityRenderState> info) {
        super.adjustRenderPose(info);
        float yaw = info.getOrDefaultGeckolibData(YAW, 0f);
        if (yaw != 0f) info.poseStack().mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(yaw)));
    }

    /** One geo/texture/animation set per actor kind, picked from the render state. */
    static final class Model extends GeoModel<DeliveryActor> {
        private final Map<String, DefaultedEntityGeoModel<DeliveryActor>> byKind = new HashMap<>();

        private DefaultedEntityGeoModel<DeliveryActor> of(String kind) {
            return byKind.computeIfAbsent(kind, k -> new DefaultedEntityGeoModel<>(ModItems.id("delivery_" + k)));
        }

        @Override public Identifier getModelResource(GeoRenderState s) { return of(s.getOrDefaultGeckolibData(KIND, "crate")).getModelResource(s); }
        @Override public Identifier getTextureResource(GeoRenderState s) { return of(s.getOrDefaultGeckolibData(KIND, "crate")).getTextureResource(s); }
        @Override public Identifier getAnimationResource(DeliveryActor a) { return of(a.kind()).getAnimationResource(a); }
    }
}
