package com.infinitemultiverse.core.registry;

import com.infinitemultiverse.InfiniteMultiverse;
import com.infinitemultiverse.core.data.PlayerMultiverseData;
import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, InfiniteMultiverse.MOD_ID);

    public static final Supplier<AttachmentType<PlayerMultiverseData>> PLAYER_DATA = ATTACHMENT_TYPES.register("player_data",
            () -> AttachmentType.builder(() -> new PlayerMultiverseData())
                    .serialize(PlayerMultiverseData.CODEC)
                    .copyOnDeath()
                    .build());

    private ModAttachments() {
    }
}
