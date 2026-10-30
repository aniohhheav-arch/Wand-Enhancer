package dev.infinitygauntlet.network;

import dev.infinitygauntlet.InfinityGauntlet;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Server → chamber visitor, ten times a second: where the target looks from, and the creatures around it. */
public record ViewPayload(int specimenId, Vec3 eye, float yaw, float pitch, List<Creature> creatures) implements CustomPacketPayload {
    /** {@code type} is the entity type's registry id; {@code name} is only used for players. */
    public record Creature(int id, int type, Vec3 pos, float body, float head, float pitch, float speed, boolean baby, float scale, String name) {
    }

    public static final Type<ViewPayload> TYPE = new Type<>(InfinityGauntlet.id("view"));
    public static final StreamCodec<ByteBuf, ViewPayload> CODEC = new StreamCodec<>() {
        @Override
        public ViewPayload decode(ByteBuf buf) {
            int id = ByteBufCodecs.VAR_INT.decode(buf);
            Vec3 eye = ModCodecs.VEC3.decode(buf);
            float yaw = buf.readFloat(), pitch = buf.readFloat();
            int n = ByteBufCodecs.VAR_INT.decode(buf);
            List<Creature> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                list.add(new Creature(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ModCodecs.VEC3.decode(buf), buf.readFloat(),
                        buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readBoolean(), buf.readFloat(), ByteBufCodecs.STRING_UTF8.decode(buf)));
            }
            return new ViewPayload(id, eye, yaw, pitch, list);
        }

        @Override
        public void encode(ByteBuf buf, ViewPayload p) {
            ByteBufCodecs.VAR_INT.encode(buf, p.specimenId);
            ModCodecs.VEC3.encode(buf, p.eye);
            buf.writeFloat(p.yaw);
            buf.writeFloat(p.pitch);
            ByteBufCodecs.VAR_INT.encode(buf, p.creatures.size());
            for (Creature c : p.creatures) {
                ByteBufCodecs.VAR_INT.encode(buf, c.id);
                ByteBufCodecs.VAR_INT.encode(buf, c.type);
                ModCodecs.VEC3.encode(buf, c.pos);
                buf.writeFloat(c.body);
                buf.writeFloat(c.head);
                buf.writeFloat(c.pitch);
                buf.writeFloat(c.speed);
                buf.writeBoolean(c.baby);
                buf.writeFloat(c.scale);
                ByteBufCodecs.STRING_UTF8.encode(buf, c.name);
            }
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
