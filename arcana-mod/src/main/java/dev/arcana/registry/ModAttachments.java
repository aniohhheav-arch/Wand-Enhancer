package dev.arcana.registry;

import dev.arcana.ArcanaMod;
import dev.arcana.magic.PlayerMagic;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ArcanaMod.MODID);

    public static final Supplier<AttachmentType<PlayerMagic>> MAGIC = ATTACHMENTS.register("magic",
        () -> AttachmentType.builder(PlayerMagic::new).serialize(PlayerMagic.CODEC.codec()).copyOnDeath().build());

    private ModAttachments() {}
}
