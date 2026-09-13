package com.backwardschallenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Shared by the flint-and-steel and fire-charge mixins: after vanilla has (possibly) generated
 * a nether portal, check whether the dragon has been killed yet. If not, blow the frame up
 * instead of letting it stand.
 *
 * Deliberately does NOT reference the internal dragon-fight class (EnderDragonFight /
 * EndDragonFight) at all - that class's exact name/package could not be confirmed against
 * real 26.2 source. Instead, "dragon defeated" is tracked via our own Fabric Attachment flag,
 * set by a death-event listener on the actual EnderDragon entity (see BackwardsChallengeMod).
 */
public final class PortalGuard {

    private PortalGuard() {
    }

    public static void punishIfEarlyPortal(ServerLevel world, BlockPos ignitionPos) {
        MinecraftServer server = world.getServer();
        if (server == null) {
            return;
        }

        ServerLevel endWorld = server.getLevel(Level.END);
        boolean dragonDefeated = endWorld != null
                && Boolean.TRUE.equals(endWorld.getAttachedOrElse(ModAttachments.DRAGON_DEFEATED, Boolean.FALSE));
        if (dragonDefeated) {
            return; // portal is allowed to exist, nothing to do
        }

        int r = ModConstants.PORTAL_SCAN_RADIUS;
        boolean foundPortal = false;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = ignitionPos.offset(dx, dy, dz);
                    if (world.getBlockState(pos).is(Blocks.NETHER_PORTAL)) {
                        world.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                        foundPortal = true;
                    }
                }
            }
        }

        if (foundPortal) {
            BackwardsChallengeMod.LOGGER.info(
                    "[BackwardsChallenge] Portal lit before the dragon died near {}, detonating it.", ignitionPos);
            // MAPPING CHECKPOINT: explosion API signatures have changed repeatedly. If
            // Level#explode doesn't match this signature, look up the current one on Level in
            // your IDE - any "make an explosion at this position with this power" call works.
            world.explode(null, ignitionPos.getX() + 0.5, ignitionPos.getY() + 0.5, ignitionPos.getZ() + 0.5,
                    3.0F, false, Level.ExplosionInteraction.BLOCK);
        }
    }
}
