package com.backwardschallenge;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.ResourceLocation;

/**
 * Persistent per-player flags for the challenge, using Fabric API's Data Attachment system
 * instead of vanilla entity tags. This is deliberately NOT built on vanilla Entity/Player
 * internals (which keep changing name between Minecraft versions) - Fabric's Attachment API
 * is documented and stable, and persists across restarts via the given Codec.
 */
public final class ModAttachments {

    private ModAttachments() {
    }

    public static final AttachmentType<Boolean> INITIALIZED = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "initialized"),
            builder -> builder.initializer(() -> Boolean.FALSE).persistent(Codec.BOOL));

    public static final AttachmentType<Boolean> AWAITING_TRUE_CREDITS = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "awaiting_credits"),
            builder -> builder.initializer(() -> Boolean.FALSE).persistent(Codec.BOOL));

    public static final AttachmentType<Boolean> TRUE_ENDING_DONE = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "true_ending_done"),
            builder -> builder.initializer(() -> Boolean.FALSE).persistent(Codec.BOOL));
}
