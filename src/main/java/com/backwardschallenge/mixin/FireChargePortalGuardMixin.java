package com.backwardschallenge.mixin;

import com.backwardschallenge.PortalGuard;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Same guard as {@link FlintAndSteelPortalGuardMixin}, but for fire charges, which can also
 * light a nether portal frame.
 */
@Mixin(FireChargeItem.class)
public class FireChargePortalGuardMixin {

    @Inject(method = "useOn", at = @At("TAIL"))
    private void backwardschallenge$checkForEarlyPortal(UseOnContext context, CallbackInfoReturnable<InteractionResult> cir) {
        Level world = context.getLevel();
        if (world instanceof ServerLevel serverWorld) {
            PortalGuard.punishIfEarlyPortal(serverWorld, context.getClickedPos());
        }
    }
}
