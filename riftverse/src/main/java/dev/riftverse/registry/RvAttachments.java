package dev.riftverse.registry;

import dev.riftverse.Riftverse;
import dev.riftverse.player.PlayerMultiverseData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class RvAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Riftverse.MODID);

    public static final Supplier<AttachmentType<PlayerMultiverseData>> MULTIVERSE = ATTACHMENTS.register("multiverse",
            () -> AttachmentType.serializable(PlayerMultiverseData::new).copyOnDeath().build());

    private RvAttachments() {}
}
