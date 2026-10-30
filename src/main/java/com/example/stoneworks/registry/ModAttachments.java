package com.example.stoneworks.registry;

import com.example.stoneworks.Stoneworks;
import com.example.stoneworks.data.ResearchProgress;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Stoneworks.MOD_ID);

        public static final DeferredHolder<AttachmentType<?>, AttachmentType<ResearchProgress>> RESEARCH = TYPES.register(
            "research",
            () -> AttachmentType.builder(() -> new ResearchProgress(0))
                    .serialize(ResearchProgress.CODEC)
                    .copyOnDeath()
                    .build()
    );

    private ModAttachments() {}
}