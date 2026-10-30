package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Server → client: one combat presentation event on a living entity (see {@code CombatFx}).
 *
 * @param kind      {@code CombatFx.KIND_*}: material hit burst, pose reaction or death style
 * @param style     the reaction / death style ordinal; for deaths bit 8 marks a severed head
 * @param dir       direction of the blow (unit-ish)
 * @param intensity 0..n strength of the blow
 * @param ticks     how long a reaction lasts
 */
public record CombatPayload(int entityId, int kind, int style, Vec3 dir, float intensity, int ticks) implements CustomPacketPayload {
    public static final Type<CombatPayload> TYPE = new Type<>(InfinityGauntlet.id("combat"));
    public static final StreamCodec<ByteBuf, CombatPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CombatPayload::entityId,
            ByteBufCodecs.VAR_INT, CombatPayload::kind,
            ByteBufCodecs.VAR_INT, CombatPayload::style,
            ModCodecs.VEC3, CombatPayload::dir,
            ByteBufCodecs.FLOAT, CombatPayload::intensity,
            ByteBufCodecs.VAR_INT, CombatPayload::ticks,
            CombatPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
