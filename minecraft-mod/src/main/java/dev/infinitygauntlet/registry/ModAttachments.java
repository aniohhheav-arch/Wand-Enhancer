package dev.infinitygauntlet.registry;

import dev.infinitygauntlet.InfinityGauntlet;
import dev.infinitygauntlet.ability.PlayerPowerData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, InfinityGauntlet.MOD_ID);

    public static final Supplier<AttachmentType<PlayerPowerData>> POWER_DATA = REGISTER.register("power_data",
            () -> AttachmentType.builder(PlayerPowerData::new).serialize(PlayerPowerData.CODEC).copyOnDeath().build());

    private ModAttachments() {
    }
}
