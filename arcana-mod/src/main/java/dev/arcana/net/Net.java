package dev.arcana.net;

import dev.arcana.ArcanaConfig;
import dev.arcana.ArcanaMod;
import dev.arcana.client.ClientPayloadHandler;
import dev.arcana.item.Customize;
import dev.arcana.item.WandItem;
import dev.arcana.magic.CastService;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.School;
import dev.arcana.magic.Spell;
import dev.arcana.magic.SpellSlot;
import dev.arcana.registry.ModComponents;
import java.util.Arrays;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** All network payloads. Clients only ever *request*; the server validates and applies everything. */
@EventBusSubscriber(modid = ArcanaMod.MODID)
public final class Net {
    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> mk(String id) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ArcanaMod.MODID, id));
    }

    // ---- client -> server
    public record CastSlot(int slot) implements CustomPacketPayload {
        public static final Type<CastSlot> TYPE = mk("cast_slot");
        public static final StreamCodec<RegistryFriendlyByteBuf, CastSlot> CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT, CastSlot::slot, CastSlot::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SelectSpell(String id) implements CustomPacketPayload {
        public static final Type<SelectSpell> TYPE = mk("select_spell");
        public static final StreamCodec<RegistryFriendlyByteBuf, SelectSpell> CODEC = StreamCodec.composite(ByteBufCodecs.STRING_UTF8, SelectSpell::id, SelectSpell::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record CustomizeReq(int field, int value) implements CustomPacketPayload {
        public static final Type<CustomizeReq> TYPE = mk("customize");
        public static final StreamCodec<RegistryFriendlyByteBuf, CustomizeReq> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CustomizeReq::field, ByteBufCodecs.VAR_INT, CustomizeReq::value, CustomizeReq::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record EvolveReq() implements CustomPacketPayload {
        public static final Type<EvolveReq> TYPE = mk("evolve");
        public static final StreamCodec<RegistryFriendlyByteBuf, EvolveReq> CODEC = StreamCodec.unit(new EvolveReq());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    // ---- server -> client
    /** Full magic snapshot: mana, max mana, per-school xp (8), per-spell cooldown ticks left, recently-cast bitmask. */
    public record Sync(float mana, float max, int[] xp, int[] cooldowns, int recentMask) implements CustomPacketPayload {
        public static final Type<Sync> TYPE = mk("sync");
        public static final StreamCodec<RegistryFriendlyByteBuf, Sync> CODEC = StreamCodec.of(
            (buf, s) -> {
                buf.writeFloat(s.mana);
                buf.writeFloat(s.max);
                buf.writeVarIntArray(s.xp);
                buf.writeVarIntArray(s.cooldowns);
                buf.writeVarInt(s.recentMask);
            },
            buf -> new Sync(buf.readFloat(), buf.readFloat(), buf.readVarIntArray(), buf.readVarIntArray(), buf.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cinematic screen effect. kind 0 = colour flash + shake, 1 = time-stop desaturation. */
    public record Cinematic(int rgb, int ticks, int kind) implements CustomPacketPayload {
        public static final Type<Cinematic> TYPE = mk("cinematic");
        public static final StreamCodec<RegistryFriendlyByteBuf, Cinematic> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Cinematic::rgb, ByteBufCodecs.VAR_INT, Cinematic::ticks, ByteBufCodecs.VAR_INT, Cinematic::kind, Cinematic::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        r.playToServer(CastSlot.TYPE, CastSlot.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ItemStack wand = CastService.heldWand(sp);
            if (wand.isEmpty()) return;
            Spell s = Spell.of(WandItem.schoolOf(wand), SpellSlot.byOrdinal(p.slot()));
            if (s != null) CastService.cast(sp, wand, s, s.chargeTicks > 0 ? 0.6f : 1f, net.minecraft.world.InteractionHand.MAIN_HAND);
        });
        r.playToServer(SelectSpell.TYPE, SelectSpell.CODEC, (p, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer sp)) return;
            ItemStack wand = CastService.heldWand(sp);
            Spell s = Spell.byId(p.id());
            if (wand.isEmpty() || s == null || s.school != WandItem.schoolOf(wand)) return;
            if (s.school != School.NONE && s.minStage > WandItem.stageOf(wand)) return;
            wand.set(ModComponents.SELECTED.get(), s.id);
        });
        r.playToServer(CustomizeReq.TYPE, CustomizeReq.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Customize.apply(sp, p.field(), p.value());
        });
        r.playToServer(EvolveReq.TYPE, EvolveReq.CODEC, (p, ctx) -> {
            if (ctx.player() instanceof ServerPlayer sp) Customize.evolve(sp);
        });
        r.playToClient(Sync.TYPE, Sync.CODEC, (p, ctx) -> ClientPayloadHandler.sync(p));
        r.playToClient(Cinematic.TYPE, Cinematic.CODEC, (p, ctx) -> ClientPayloadHandler.cinematic(p));
    }

    // ---- helpers used by the server
    public static void sync(ServerPlayer p, boolean force) {
        PlayerMagic m = CastService.magic(p);
        long now = p.level().getGameTime();
        School[] real = School.real();
        int[] xp = new int[real.length];
        for (int i = 0; i < real.length; i++) xp[i] = m.xp(real[i]);
        Spell[] spells = Spell.values();
        int[] cd = new int[spells.length];
        for (int i = 0; i < spells.length; i++) cd[i] = (int) m.cooldownLeft(spells[i], now);
        int mask = 0;
        for (int i = 0; i < real.length; i++) {
            Long t = m.recent.get(real[i].id);
            if (t != null && now - t <= ArcanaConfig.MIMIC_WINDOW.get()) mask |= 1 << i;
        }
        float max = CastService.maxMana(p);
        // quantise so tiny regen steps don't spam packets: send when mana moves by >=1, anything else changed, or forced
        int[] key = new int[xp.length + cd.length + 3];
        System.arraycopy(xp, 0, key, 0, xp.length);
        for (int i = 0; i < cd.length; i++) key[xp.length + i] = cd[i] > 0 ? 1 : 0;
        key[key.length - 3] = Math.round(m.mana);
        key[key.length - 2] = Math.round(max);
        key[key.length - 1] = mask;
        if (!force && Arrays.equals(key, m.lastSent)) return;
        m.lastSent = key;
        send(p, new Sync(m.mana, max, xp, cd, mask));
    }

    /** Fake or mocked players (other mods, test harnesses) can have connections that reject clientbound payloads. */
    private static void send(ServerPlayer p, CustomPacketPayload payload) {
        if (p.connection == null) return;
        try {
            PacketDistributor.sendToPlayer(p, payload);
        } catch (UnsupportedOperationException ignored) {
            // connection cannot receive clientbound payloads
        }
    }

    public static void cinematic(ServerPlayer caster, int rgb, int ticks, int kind) {
        Cinematic c = new Cinematic(rgb, ticks, kind);
        cinematicNear((net.minecraft.server.level.ServerLevel) caster.level(), caster.position(), c.rgb(), c.ticks(), c.kind());
    }

    public static void cinematicNear(net.minecraft.server.level.ServerLevel level, net.minecraft.world.phys.Vec3 at, int rgb, int ticks, int kind) {
        Cinematic c = new Cinematic(rgb, ticks, kind);
        for (ServerPlayer p : level.players()) if (p.position().distanceToSqr(at) <= 64 * 64) send(p, c);
    }

    private Net() {}

}
