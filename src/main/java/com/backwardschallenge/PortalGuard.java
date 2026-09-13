package com.backwardschallenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * Shared by the flint-and-steel and fire-charge mixins: after vanilla has (possibly) generated
 * a nether portal, decide whether it's allowed to exist. Two rules, checked in order:
 *
 * 1. In the End, a nether portal is NEVER allowed, dragon dead or not - the only intended way
 *    out of the End is the exit fountain (see EndExitPortalMixin). This is deliberate: a
 *    hand-built nether portal would otherwise let players skip that redirect entirely.
 * 2. Everywhere else, a nether portal is only punished before the dragon has been killed.
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
        // NEW: the End is never a valid place for a nether portal in this challenge, no matter
        // whether the dragon is dead yet - the only intended way out of the End is the exit
        // fountain (see EndExitPortalMixin). This check is unconditional and skips the
        // dragon-defeated lookup entirely for the End.
        if (world.dimension() == Level.END) {
            detonatePortalsNear(world, ignitionPos);
            return;
        }

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

        detonatePortalsNear(world, ignitionPos);
    }

    private static void detonatePortalsNear(ServerLevel world, BlockPos ignitionPos) {
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
                    "[BackwardsChallenge] Portal lit at {} where it isn't allowed - detonating it.", ignitionPos);
            // MAPPING CHECKPOINT: explosion API signatures have changed repeatedly. If
            // Level#explode doesn't match this signature, look up the current one on Level in
            // your IDE - any "make an explosion at this position with this power" call works.
            world.explode(null, ignitionPos.getX() + 0.5, ignitionPos.getY() + 0.5, ignitionPos.getZ() + 0.5,
                    3.0F, false, Level.ExplosionInteraction.BLOCK);
        }
    }
}
