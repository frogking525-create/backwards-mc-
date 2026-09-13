package com.backwardschallenge.mixin;

import com.backwardschallenge.BackwardsChallengeMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EndPortalBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@link EndPortalBlock} backs BOTH the stronghold entry portal (Overworld -> End) and the
 * bedrock exit fountain (End -> Overworld). We only ever intercept the End -> * direction.
 *
 * MAPPING CHECKPOINT: targets "entityInside" with the classic 4-arg signature
 * (BlockState, Level, BlockPos, Entity). Some recent vanilla versions added a trailing
 * InsideBlockEffectApplier parameter to this method; 26.2 did not appear to need it based on
 * the last compile (that guess produced a "cannot find symbol" on the type, not a signature
 * mismatch on the method itself). If this mixin fails to apply at runtime with a "cannot find
 * method" style error, open EndPortalBlock in your IDE and check the exact parameter list.
 */
@Mixin(EndPortalBlock.class)
public class EndExitPortalMixin {

    @Inject(method = "entityInside", at = @At("HEAD"), cancellable = true)
    private void backwardschallenge$redirectExitFountain(BlockState state, Level world, BlockPos pos, Entity entity, CallbackInfo ci) {
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
