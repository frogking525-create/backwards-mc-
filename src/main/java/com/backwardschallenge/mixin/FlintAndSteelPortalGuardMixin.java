package com.backwardschallenge.mixin;

import com.backwardschallenge.PortalGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Reacts AFTER flint and steel has done its normal thing (ignite fire / ignite a portal frame).
 * If a nether portal exists near the click as a result and the dragon isn't dead yet, it gets
 * blown up.
 *
 * MAPPING CHECKPOINT: targets "useOn" (Mojang's official name for the method Yarn called
 * "useOnBlock"). If this doesn't match, check Item#useOn / FlintAndSteelItem in your IDE.
 */
@Mixin(FlintAndSteelItem.class)
public class FlintAndSteelPortalGuardMixin {

    @Inject(method = "useOn", at = @At("TAIL"))
    private void backwardschallenge$checkForEarlyPortal(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world instanceof ServerLevel serverWorld) {
            PortalGuard.punishIfEarlyPortal(serverWorld, context.getClickedPos());
        }
    }
}
