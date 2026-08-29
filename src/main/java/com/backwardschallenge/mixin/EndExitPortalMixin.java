package com.backwardschallenge.mixin;

import com.backwardschallenge.BackwardsChallengeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.InsideBlockEffectApplier;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link EndPortalBlock} backs BOTH the stronghold entry portal (Overworld -> End) and the
 * bedrock exit fountain (End -> Overworld). We only ever intercept the End -> * direction.
 *
 * MAPPING CHECKPOINT: this targets "entityInside" with a trailing InsideBlockEffectApplier
 * parameter, matching a fairly recent vanilla refactor of Block#onEntityCollision. If your
 * 26.2 build doesn't have that 5th parameter (or names the method differently), open
 * EndPortalBlock in your IDE / mcsrc.dev, check the actual method name+signature, and update
 * both the @Inject target string and this method's parameter list to match - the body logic
 * underneath does not need to change.
 */
@Mixin(EndPortalBlock.class)
public class EndExitPortalMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void backwardschallenge$redirectExitFountain(BlockState state, Level world, BlockPos pos, Entity entity,
                                                           InsideBlockEffectApplier effectApplier, CallbackInfo ci) {
        if (!(world instanceof ServerLevel serverWorld)) {
            return;
        }
        if (serverWorld.dimension() != Level.END) {
            // Stronghold entry portal (or any other End-portal-block use outside the End) -
            // let vanilla handle it exactly as normal.
            return;
        }
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        if (entity.getVehicle() != null || !entity.getPassengers().isEmpty()) {
            return;
        }

        MinecraftServer server = serverWorld.getServer();
        ServerLevel netherWorld = server.getLevel(Level.NETHER);
        if (netherWorld == null) {
            BackwardsChallengeMod.LOGGER.warn("[BackwardsChallenge] Nether world unavailable, letting vanilla exit-portal logic run.");
            return;
        }

        ci.cancel();

        BackwardsChallengeMod.sendPlayerToNetherLanding(player, netherWorld);
        BackwardsChallengeMod.markAwaitingTrueCredits(player);
    }
}
