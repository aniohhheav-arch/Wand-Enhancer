package dev.arcana.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Customisation indices of a wand: handle, crystal dye (-1 = school colour), core, particle effect and two rune slots. */
public record WandStyle(int handle, int color, int core, int fx, int mod1, int mod2) {
    public static final WandStyle DEFAULT = new WandStyle(0, -1, 0, 0, 0, 0);

    public static final Codec<WandStyle> CODEC = RecordCodecBuilder.create(i -> i.group(
        Codec.INT.fieldOf("handle").forGetter(WandStyle::handle),
        Codec.INT.fieldOf("color").forGetter(WandStyle::color),
        Codec.INT.fieldOf("core").forGetter(WandStyle::core),
        Codec.INT.fieldOf("fx").forGetter(WandStyle::fx),
        Codec.INT.fieldOf("mod1").forGetter(WandStyle::mod1),
        Codec.INT.fieldOf("mod2").forGetter(WandStyle::mod2)
    ).apply(i, WandStyle::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, WandStyle> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, WandStyle::handle,
        ByteBufCodecs.VAR_INT, WandStyle::color,
        ByteBufCodecs.VAR_INT, WandStyle::core,
        ByteBufCodecs.VAR_INT, WandStyle::fx,
        ByteBufCodecs.VAR_INT, WandStyle::mod1,
        ByteBufCodecs.VAR_INT, WandStyle::mod2,
        WandStyle::new);

    public WandStyle with(int field, int value) {
        return switch (field) {
            case 0 -> new WandStyle(value, color, core, fx, mod1, mod2);
            case 1 -> new WandStyle(handle, value, core, fx, mod1, mod2);
            case 2 -> new WandStyle(handle, color, value, fx, mod1, mod2);
            case 3 -> new WandStyle(handle, color, core, value, mod1, mod2);
            case 4 -> new WandStyle(handle, color, core, fx, value, mod2);
            case 5 -> new WandStyle(handle, color, core, fx, mod1, value);
            default -> this;
        };
    }

    public int get(int field) {
        return switch (field) {
            case 0 -> handle;
            case 1 -> color;
            case 2 -> core;
            case 3 -> fx;
            case 4 -> mod1;
            case 5 -> mod2;
            default -> 0;
        };
    }

    public static final int FIELDS = 6;

    public Handle handleType() { return Handle.byIndex(handle); }
    public Core coreType() { return Core.byIndex(core); }
    public Fx fxType() { return Fx.byIndex(fx); }
    public Rune rune1() { return Rune.byIndex(mod1); }
    public Rune rune2() { return Rune.byIndex(mod2); }

    public float runeProduct(java.util.function.ToDoubleFunction<Rune> f) {
        return (float) (f.applyAsDouble(rune1()) * f.applyAsDouble(rune2()));
    }
}
