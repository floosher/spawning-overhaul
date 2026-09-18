package com.spawningoverhaul.spawn;

import com.spawningoverhaul.config.SpawningConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Performs environmental checks for spawn location detection.
 * Optimized for performance with cylinder scans and zero-allocation queries.
 */
public class LocationDetector {

    private static final ThreadLocal<BlockPos.MutableBlockPos> MUTABLE_POS =
            ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    /**
     * Check if the position can see the sky (is outside).
     *
     * @param level The level/world
     * @param pos The position to check
     * @return true if position can see sky
     */
    public static boolean isOutside(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos mutablePos = MUTABLE_POS.get();
        mutablePos.set(pos.getX(), pos.getY() + 1, pos.getZ());
        return level.canSeeSky(mutablePos);
    }

    /**
     * Check if the position is in a cave.
     * Definition: Y < 60 AND cannot see sky
     *
     * @param level The level/world
     * @param pos The position to check
     * @return true if in a cave
     */
    public static boolean isInCave(Level level, BlockPos pos) {
        return pos.getY() < 60 && !level.canSeeSky(pos);
    }

    /**
     * Calculate cave depth multiplier based on Y coordinate.
     * Formula: (60 - Y) / 31 + 1, clamped to max of 5.0
     * Y=60 → 1.0x, Y=0 → ~2.9x, Y=-64 → 5.0x
     *
     * @param pos The position to check
     * @return Cave depth multiplier (1.0 to 5.0)
     */
    public static double getCaveDepth(BlockPos pos) {
        int y = pos.getY();
        if (y >= 60) {
            return 1.0;
        }
        double depth = (60.0 - y) / 31.0 + 1.0;
        return Math.min(depth, 5.0);
    }

    /**
     * Calculate forest density by counting log blocks in a cylindrical radius.
     * Returns a value from 0.0 (no forest) to 1.0 (maximum density).
     * Uses cylinder scan (not sphere) for better performance.
     * Scans Y ±3 blocks only to limit vertical range.
     * Reuses ThreadLocal MutableBlockPos to eliminate heap allocations.
     *
     * @param level The level/world
     * @param pos The center position
     * @return Forest density from 0.0 to 1.0
     */
    public static double getForestDensity(Level level, BlockPos pos) {
        SpawningConfig config = SpawningConfig.HANDLER().instance();
        int radius = config.denseForestScanRadius;
        int maxLogs = config.denseForestLogThreshold;

        if (radius <= 0 || maxLogs <= 0) {
            return 0.0;
        }

        int logCount = 0;
        int radiusSq = radius * radius;
        int posX = pos.getX();
        int posY = pos.getY();
        int posZ = pos.getZ();

        BlockPos.MutableBlockPos checkPos = MUTABLE_POS.get();

        // Cylinder scan: iterate through X and Z in radius, limited Y range
        for (int dx = -radius; dx <= radius; dx++) {
            int dxSq = dx * dx;
            for (int dz = -radius; dz <= radius; dz++) {
                // Check if within circular radius (not square)
                if (dxSq + dz * dz > radiusSq) {
                    continue;
                }

                // Scan Y ±3 blocks only
                for (int dy = -3; dy <= 3; dy++) {
                    checkPos.set(posX + dx, posY + dy, posZ + dz);
                    BlockState state = level.getBlockState(checkPos);

                    // Check if block is a log (uses vanilla log tag)
                    if (state.is(BlockTags.LOGS)) {
                        logCount++;

                        // Early exit if maximum reached
                        if (logCount >= maxLogs) {
                            return 1.0;
                        }
                    }
                }
            }
        }

        // Return density as a ratio (0.0 to 1.0)
        return Math.min(1.0, (double) logCount / maxLogs);
    }

    /**
     * Check if the position is in a dangerous structure (not a village).
     * Delegates to StructureCache for performance.
     *
     * @param level The level/world
     * @param pos The position to check
     * @return true if in a dangerous structure
     */
    public static boolean isInDangerousStructure(Level level, BlockPos pos) {
        return StructureCache.isInDangerousStructure(level, pos);
    }
}
