package com.backwardschallenge;

import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.resources.Identifier;

/**
 * Persistent flags for the challenge, using Fabric API's Data Attachment system instead of
 * vanilla entity/level internals (which keep changing name between Minecraft versions).
 *
 * Confirmed against Fabric's own current documentation (docs.fabricmc.net/develop/data-attachments,
 * dated for this exact Minecraft generation): the ID class is Identifier, and reads use
 * getAttachedOrElse(type, default) / writes use setAttached(type, value).
 *
 * MAPPING CHECKPOINT: the package below (net.minecraft.resources.Identifier) is a reasonable
 * guess - the docs example didn't show a full import. If it doesn't compile, this is a single,
 * easy fix: search your IDE for the "Identifier" class and use whatever package it reports.
 */
public final class ModAttachments {

    private ModAttachments() {
    }

    public static final AttachmentType<Boolean> INITIALIZED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "initialized"),
            builder -> builder.initializer(() -> Boolean.FALSE).persistent(Codec.BOOL));

    public static final AttachmentType<Boolean> AWAITING_TRUE_CREDITS = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "awaiting_credits"),
            builder -> builder.initializer(() -> Boolean.FALSE).persistent(Codec.BOOL));

    public static final AttachmentType<Boolean> TRUE_ENDING_DONE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "true_ending_done"),
            builder -> builder.initializer(() -> Boolean.FALSE).persistent(Codec.BOOL));

    // Attached to the End ServerLevel itself (attachments work on ServerLevel too, per the
    // same docs), set true the moment any EnderDragon entity dies anywhere. This sidesteps
    // needing to reference the internal dragon-fight class at all.
    public static final AttachmentType<Boolean> DRAGON_DEFEATED = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(ModConstants.MOD_ID, "dragon_defeated"),
            builder -> builder.initializer(() -> Boolean.FALSE).persistent(Codec.BOOL));
}
