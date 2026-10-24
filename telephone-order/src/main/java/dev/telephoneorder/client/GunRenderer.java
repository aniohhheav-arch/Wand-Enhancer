package dev.telephoneorder.client;

import com.geckolib.constant.dataticket.DataTicket;
import com.geckolib.renderer.GeoItemRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import com.geckolib.renderer.base.RenderPassInfo;
import com.geckolib.renderer.layer.builtin.AutoGlowingGeoLayer;
import dev.telephoneorder.Catalog;
import dev.telephoneorder.ModItems;
import dev.telephoneorder.weapon.GunItem;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.core.component.DataComponents;

import java.util.List;

/** Renders a gun's GeckoLib model: finish texture, glow mask, fitted attachments, live orientation tweaks. */
public class GunRenderer extends GeoItemRenderer<GunItem> {
    static final DataTicket<String> FINISH = DataTicket.create("telephoneorder_finish", String.class);
    static final DataTicket<String> VIEW = DataTicket.create("telephoneorder_view", String.class);
    static final DataTicket<List> FLAGS = DataTicket.create("telephoneorder_flags", List.class);

    public GunRenderer(GunItem item) {
        super(new GunModel(item));
        withRenderLayer(new AutoGlowingGeoLayer<>(this));
    }

    @Override
    public void captureDefaultRenderState(GunItem item, RenderData data, GeoRenderState state, float partialTick) {
        super.captureDefaultRenderState(item, data, state, partialTick);
        ItemStack stack = data.itemStack();
        CustomModelData cmd = stack.get(DataComponents.CUSTOM_MODEL_DATA);
        state.addGeckolibData(FINISH, cmd != null && !cmd.strings().isEmpty() ? cmd.strings().get(0) : "");
        state.addGeckolibData(FLAGS, cmd != null ? cmd.flags() : List.of());
        state.addGeckolibData(VIEW, GunOrient.view(data.renderPerspective()));
    }

    @Override
    public void preRenderPass(RenderPassInfo<GeoRenderState> info, SubmitNodeCollector collector) {
        super.preRenderPass(info, collector);
        List<?> flags = info.getOrDefaultGeckolibData(FLAGS, List.of());
        info.addBoneUpdater((pass, bones) -> {
            for (int i = 0; i < Catalog.ATTS.size(); i++) {
                boolean on = i < flags.size() && Boolean.TRUE.equals(flags.get(i));
                String id = Catalog.ATTS.get(i).id();
                if (!on) bones.ifPresent("att_" + id, b -> b.skipRender(true).skipChildrenRender(true));
                else if (id.equals("drum_mag")) bones.ifPresent("mag_base", b -> b.skipRender(true));
            }
        });
    }

    @Override
    public void adjustRenderPose(RenderPassInfo<GeoRenderState> info) {
        super.adjustRenderPose(info);
        String v = info.getOrDefaultGeckolibData(VIEW, "other");
        var ps = info.poseStack();
        ps.translate(GunOrient.get(v, "x") / 16f, GunOrient.get(v, "y") / 16f, GunOrient.get(v, "z") / 16f);
        ps.mulPose(new org.joml.Matrix4f().rotationY((float) Math.toRadians(GunOrient.get(v, "yaw"))));
        ps.mulPose(new org.joml.Matrix4f().rotationX((float) Math.toRadians(GunOrient.get(v, "pitch"))));
        ps.mulPose(new org.joml.Matrix4f().rotationZ((float) Math.toRadians(GunOrient.get(v, "roll"))));
        float s = GunOrient.get(v, "scale");
        ps.scale(s, s, s);
    }

    static final class GunModel extends com.geckolib.model.DefaultedItemGeoModel<GunItem> {
        private final String gunId;

        GunModel(GunItem item) {
            super(ModItems.id(item.def.id()));
            this.gunId = item.def.id();
        }

        @Override
        public void addAdditionalStateData(GunItem animatable, Object relatedObject, GeoRenderState state) {
            super.addAdditionalStateData(animatable, relatedObject, state);
        }

        @Override
        public Identifier getTextureResource(GeoRenderState state) {
            String fin = state.getOrDefaultGeckolibData(FINISH, "");
            if (fin == null || fin.isEmpty()) return super.getTextureResource(state);
            return ModItems.id("textures/item/" + gunId + "_" + fin + ".png");
        }
    }
}
