package com.stickyfingers.registry;

import com.stickyfingers.StickyFingersMod;
import com.stickyfingers.data.StandData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StickyFingersMod.MODID);

    public static final Supplier<AttachmentType<StandData>> STAND = REGISTER.register("stand",
            () -> AttachmentType.serializable(StandData::new).copyOnDeath().build());

    private ModAttachments() {}
}
