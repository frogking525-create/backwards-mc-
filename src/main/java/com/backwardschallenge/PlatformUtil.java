package com.backwardschallenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

import java.util.Random;

/**
 * Terrain scanning + platform construction helpers.
 */
public final class PlatformUtil {

    private PlatformUtil() {
    }

    /**
     * Spiral-searches outward on the XZ plane (within the given Y band) for the nearest
     * naturally generated End island, force-loading chunks as it goes. Returns the surface
     * position of the first end_stone column found, or null if nothing turned up in range.
     */
    public static BlockPos findNearestEndIsland(ServerLevel world, BlockPos center, int radius, int yMin, int yMax) {
        int cx = center.getX();
        int cz = center.getZ();

        for (int r = 0; r <= radius; r += 8) {
            for (int dx = -r; dx <= r; dx += 8) {
                for (int dz = -r; dz <= r; dz += 8) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = cx + dx;
                    int z = cz + dz;

                    world.getChunk(x >> 4, z >> 4);

                    for (int y = yMax; y >= yMin; y--) {
                        BlockPos pos = new BlockPos(x, y, z);
                        if (world.getBlockState(pos).is(Blocks.END_STONE)) {
                            return pos;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Builds a rounded, domed end-stone island centered on {@code center}, with its top surface
     * at {@code center.getY()}. Thickest in the middle (several blocks deep) and tapering to a
     * single block at the edges, with a jagged (not perfectly circular) coastline so it reads as
     * a natural outer-End island rather than a platform. Returns the block players should be
     * teleported to stand on (one block above the surface).
     */
    public static BlockPos buildEndIsland(ServerLevel world, BlockPos center, int radius) {
        Random random = new Random(center.getX() * 341873128712L + center.getZ() * 132897987541L);
        int maxThickness = Math.max(3, radius / 2 + 1);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                double dist = Math.sqrt((double) (dx * dx) + (double) (dz * dz));
                if (dist > radius) {
                    continue; // circular footprint, not a square one
                }
                double edgeFactor = dist / radius; // 0 at the center, 1 at the rim

                // Jagged coastline: the further out we are, the more likely this column is
                // skipped entirely, so the outline isn't a perfect circle.
                if (edgeFactor > 0.7) {
                    double skipChance = (edgeFactor - 0.7) / 0.3 * 0.65;
                    if (random.nextDouble() < skipChance) {
                        continue;
                    }
                }

                // Dome-shaped cross-section: thick in the middle, tapering to a thin edge.
                int thickness = Math.max(1, (int) Math.round(maxThickness * (1 - edgeFactor * edgeFactor)));

                BlockPos top = center.offset(dx, 0, dz);
                for (int dy = 0; dy < thickness; dy++) {
                    world.setBlock(top.below(dy), Blocks.END_STONE.defaultBlockState(), 3);
                }
                for (int dy = 1; dy <= 4; dy++) {
                    world.setBlock(top.above(dy), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        // FIX: this was passing `false`, which is a no-op - it never actually force-loads
        // anything. That let this chunk get unloaded again before the teleporting player's
        // client had finished receiving it, which is exactly what was leaving players stuck
        // on the "Loading terrain..." screen after being sent here. Forcing it `true`
        // guarantees the chunk is fully sent to the client before/while the teleport happens.
        world.setChunkForced(chunkX, chunkZ, true);
        return center.above();
    }

    /**
     * Builds a flat, solid obsidian platform (with the air above it cleared) centered on
     * {@code center}, at {@code center.getY()}. Returns the block players should be teleported
     * to stand on (one block above the platform surface). Used for the Nether landing pad, which
     * is meant to read as an obviously artificial safe zone rather than natural terrain.
     */
    public static BlockPos buildFlatPlatform(ServerLevel world, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos floor = center.offset(dx, 0, dz);
                world.setBlock(floor, Blocks.OBSIDIAN.defaultBlockState(), 3);
                for (int dy = 1; dy <= 4; dy++) {
                    world.setBlock(floor.above(dy), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        // Avoids the ChunkPos record entirely (its x/z accessor names aren't confirmed) -
        // plain bit-shift is all a chunk coordinate ever is.
        int chunkX = center.getX() >> 4;
        int chunkZ = center.getZ() >> 4;
        // FIX: same no-op-`false` bug as buildEndIsland() above - force it `true` so the
        // Nether landing chunk can't be unloaded out from under the teleporting player.
        world.setChunkForced(chunkX, chunkZ, true);
        return center.above();
    }

    /**
     * Finds a safe Y in the Nether below the given XZ by scanning downward for the first solid,
     * non-liquid block. The mod always paves a guaranteed-safe obsidian pad on top of whatever
     * it finds (or the fallback Y) rather than trusting raw terrain alone.
     */
    public static int findSafeNetherY(ServerLevel world, int x, int z, int yMin, int yMax, int fallback) {
        world.getChunk(x >> 4, z >> 4);
        for (int y = yMax; y >= yMin; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            boolean solidBelow = !world.getBlockState(pos).isAir()
                    && world.getFluidState(pos).isEmpty();
            boolean clearAbove = world.getBlockState(pos.above()).isAir()
                    && world.getBlockState(pos.above(2)).isAir();
            if (solidBelow && clearAbove) {
                return y + 1;
            }
        }
        return fallback;
    }
}

