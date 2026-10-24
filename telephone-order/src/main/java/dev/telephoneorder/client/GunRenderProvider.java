package dev.telephoneorder.client;

import com.geckolib.animatable.client.GeoRenderProvider;
import com.geckolib.renderer.GeoItemRenderer;
import dev.telephoneorder.weapon.GunItem;

import java.util.function.Consumer;

/** One GeckoLib item renderer per gun (client only). */
public final class GunRenderProvider {
    private GunRenderProvider() { }

    public static void provide(GunItem item, Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private GunRenderer renderer;

            @Override
            public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new GunRenderer(item);
                return renderer;
            }
        });
    }
}
