package com.cnethuka.kegare.mixin;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** An attachment's serializer, so Postgres storage covers every saved attachment with no code per attachment (DESIGN §3.33). */
@Mixin(AttachmentType.class)
public interface AttachmentTypeAccessor {
    @Accessor(value = "serializer", remap = false)
    IAttachmentSerializer<?, ?> kegare$serializer();
}
