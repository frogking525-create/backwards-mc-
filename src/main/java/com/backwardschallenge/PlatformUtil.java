package com.backwardschallenge;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;

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
     * Builds a flat, solid obsidian platform (with the air above it cleared) centered on
     * {@code center}, at {@code center.getY()}. Returns the block players should be teleported
     * to stand on (one block above the platform surface).
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
        ChunkPos chunkPos = new ChunkPos(center);
        world.setChunkForced(chunkPos.x, chunkPos.z, false);
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
