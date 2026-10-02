package dev.mysticarts.registry;

import dev.mysticarts.MysticArts;
import dev.mysticarts.power.PowerData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class MaAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MysticArts.MODID);

    public static final Supplier<AttachmentType<PowerData>> POWER = ATTACHMENTS.register("power",
            () -> AttachmentType.serializable(PowerData::new).copyOnDeath().build());

    private MaAttachments() {}
}
