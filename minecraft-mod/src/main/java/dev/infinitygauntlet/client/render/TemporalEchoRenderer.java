package dev.infinitygauntlet.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.infinitygauntlet.client.time.Afterimages;
import dev.infinitygauntlet.client.time.ClientHistory;
import dev.infinitygauntlet.client.vfx.ModRenderTypes;
import dev.infinitygauntlet.client.vfx.VfxDraw;
import dev.infinitygauntlet.entity.TemporalEchoEntity;
import dev.infinitygauntlet.stone.InfinityStone;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/** Renders the echo as a green ghost of its caster's own model, trailing fainter copies of its recent poses. */
public class TemporalEchoRenderer extends EntityRenderer<TemporalEchoEntity> {
    public TemporalEchoRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0;
    }

    @Override
    public void render(TemporalEchoEntity echo, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity ownerEntity = echo.ownerEntity();
        LivingEntity model = ownerEntity instanceof LivingEntity living ? living : echo;
        if (model == echo) {
            return;
        }
        float p = echo.progress(pt);
        float alpha = VfxDraw.smooth(p / 0.1f) * (1 - VfxDraw.smooth((p - 0.85f) / 0.15f));
        float body = Mth.rotLerp(pt, echo.yBodyRotO, echo.yBodyRot);
        float head = Mth.rotLerp(pt, echo.yHeadRotO, echo.yHeadRot);
        Afterimages.draw(pose, buffers, model, 0, 0, 0, body, head, echo.getXRot(), echo.walkAnimation.position(pt), echo.walkAnimation.speed(pt),
                echo.tickCount + pt, 0.75f * alpha, InfinityStone.TIME.color(), 1f);
        ClientHistory.keepAlive(20);
        double ox = Mth.lerp(pt, echo.xo, echo.getX()), oy = Mth.lerp(pt, echo.yo, echo.getY()), oz = Mth.lerp(pt, echo.zo, echo.getZ());
        for (int k = 1; k <= 3; k++) {
            ClientHistory.Frame f = ClientHistory.get(echo.getId(), k * 3);
            if (f != null && echo.position().distanceToSqr(f.x(), f.y(), f.z()) > 0.04) {
                Afterimages.draw(pose, buffers, model, f.x() - ox, f.y() - oy, f.z() - oz, f.bodyRot(), f.headRot(), f.xRot(), f.limbPos(),
                        f.limbSpeed(), f.age(), 0.3f * alpha * (1 - k / 4f), InfinityStone.TIME.color(), 1f);
            }
        }
    }

    @Override
    public ResourceLocation getTextureLocation(TemporalEchoEntity entity) {
        return ModRenderTypes.WHITE;
    }
}
