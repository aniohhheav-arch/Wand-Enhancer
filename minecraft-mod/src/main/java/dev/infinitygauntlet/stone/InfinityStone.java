package dev.infinitygauntlet.stone;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/** The six stones, in gauntlet socket order. Colors are the stone's primary VFX palette (0xRRGGBB). */
public enum InfinityStone implements StringRepresentable {
    SPACE("space", 0x2A6EFF, 0x9FE8FF, ChatFormatting.BLUE),
    MIND("mind", 0xFFD22A, 0xFFF4B0, ChatFormatting.YELLOW),
    REALITY("reality", 0xDC1428, 0xFF7A6A, ChatFormatting.RED),
    POWER("power", 0xA032FF, 0xE6B0FF, ChatFormatting.DARK_PURPLE),
    TIME("time", 0x1EDC5A, 0xB4FFC8, ChatFormatting.GREEN),
    SOUL("soul", 0xFF8214, 0xFFD29A, ChatFormatting.GOLD);

    public static final Codec<InfinityStone> CODEC = StringRepresentable.fromEnum(InfinityStone::values);
    public static final StreamCodec<ByteBuf, InfinityStone> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], InfinityStone::ordinal);

    private final String name;
    private final int color;
    private final int highlight;
    private final ChatFormatting format;

    InfinityStone(String name, int color, int highlight, ChatFormatting format) {
        this.name = name;
        this.color = color;
        this.highlight = highlight;
        this.format = format;
    }

    public int color() {
        return color;
    }

    public int highlight() {
        return highlight;
    }

    public ChatFormatting format() {
        return format;
    }

    public Component displayName() {
        return Component.translatable("stone.infinitygauntlet." + name).withStyle(format);
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
