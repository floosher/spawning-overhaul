package com.spawningoverhaul.spawn;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;

/**
 * Immutable snapshot of a spawn attempt with lazy-evaluated environment detection.
 * Uses primitive bitflags and sentinel values to avoid boxing and heap allocations.
 */
public class SpawnContext {
    private static final byte FLAG_NIGHT_COMPUTED = 1;
    private static final byte FLAG_IS_NIGHT = 2;
    private static final byte FLAG_OUTSIDE_COMPUTED = 4;
    private static final byte FLAG_IS_OUTSIDE = 8;
    private static final byte FLAG_CAVE_COMPUTED = 16;
    private static final byte FLAG_IS_CAVE = 32;
    private static final byte FLAG_STRUCTURE_COMPUTED = 64;
    private static final byte FLAG_IS_STRUCTURE = (byte) 128;

    private final Level level;
    private final BlockPos spawnPos;
    private final EntityType<?> entityType;
    private final MobSpawnType spawnType;

    private byte flags = 0;
    private double forestDensity = -1.0;
    private double caveDepth = -1.0;

    public SpawnContext(Level level, BlockPos spawnPos, EntityType<?> entityType, MobSpawnType spawnType) {
        this.level = level;
        this.spawnPos = spawnPos;
        this.entityType = entityType;
        this.spawnType = spawnType;
    }

    /**
     * Pre-populate all environment checks in one pass.
     * Useful when you know you'll need all the data.
     */
    public void detectEnvironment() {
        isNight();
        isOutside();
        getForestDensity();
        isInCave();
        getCaveDepth();
        isInDangerousStructure();
    }

    public Level getLevel() {
        return level;
    }

    public BlockPos getSpawnPos() {
        return spawnPos;
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    public MobSpawnType getSpawnType() {
        return spawnType;
    }

    public boolean isNight() {
        if ((flags & FLAG_NIGHT_COMPUTED) == 0) {
            boolean night = !level.isDay();
            flags |= FLAG_NIGHT_COMPUTED;
            if (night) {
                flags |= FLAG_IS_NIGHT;
            }
        }
        return (flags & FLAG_IS_NIGHT) != 0;
    }

    public boolean isOutside() {
        if ((flags & FLAG_OUTSIDE_COMPUTED) == 0) {
            boolean outside = LocationDetector.isOutside(level, spawnPos);
            flags |= FLAG_OUTSIDE_COMPUTED;
            if (outside) {
                flags |= FLAG_IS_OUTSIDE;
            }
        }
        return (flags & FLAG_IS_OUTSIDE) != 0;
    }

    public double getForestDensity() {
        if (forestDensity < 0.0) {
            if (isInCave() && spawnPos.getY() < 50) {
                // Underground in cave, forest density is 0
                forestDensity = 0.0;
            } else {
                forestDensity = LocationDetector.getForestDensity(level, spawnPos);
            }
        }
        return forestDensity;
    }

    public boolean isInCave() {
        if ((flags & FLAG_CAVE_COMPUTED) == 0) {
            boolean inCave = LocationDetector.isInCave(level, spawnPos);
            flags |= FLAG_CAVE_COMPUTED;
            if (inCave) {
                flags |= FLAG_IS_CAVE;
            }
        }
        return (flags & FLAG_IS_CAVE) != 0;
    }

    public double getCaveDepth() {
        if (caveDepth < 0.0) {
            caveDepth = LocationDetector.getCaveDepth(spawnPos);
        }
        return caveDepth;
    }

    public boolean isInDangerousStructure() {
        if ((flags & FLAG_STRUCTURE_COMPUTED) == 0) {
            boolean inStructure = LocationDetector.isInDangerousStructure(level, spawnPos);
            flags |= FLAG_STRUCTURE_COMPUTED;
            if (inStructure) {
                flags |= FLAG_IS_STRUCTURE;
            }
        }
        return (flags & FLAG_IS_STRUCTURE) != 0;
    }
}
