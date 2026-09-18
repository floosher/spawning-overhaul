package com.spawningoverhaul.spawn;

import com.spawningoverhaul.config.SpawningConfig;
import com.spawningoverhaul.spawn.rules.MobRuleRegistry;
import net.minecraft.util.RandomSource;

/**
 * Calculates the final spawn rate multiplier based on environmental factors and mob rules.
 * Multipliers are multiplicative and stack together.
 */
public class SpawnMultiplierCalculator {

    /**
     * Calculate the final spawn multiplier for the given context.
     * Uses progressive scaling based on cave depth and forest density.
     *
     * @param context The spawn context
     * @return The final spawn multiplier
     */
    public static double calculateMultiplier(SpawnContext context) {
        SpawningConfig config = SpawningConfig.HANDLER().instance();

        // Start with configurable base multiplier
        double multiplier = config.baseMultiplier;

        // Calculate cave contribution (scales from min to max based on depth)
        double caveMultiplier = 0.0;
        if (context.isInCave()) {
            // getCaveDepth returns 1.0 at Y=60 and 5.0 at Y=-64
            double depth = context.getCaveDepth();
            double depthRatio = (depth - 1.0) / 4.0; // Normalize to 0.0-1.0 (since depth is 1.0-5.0)
            caveMultiplier = config.minCaveMultiplier + (config.maxCaveMultiplier - config.minCaveMultiplier) * depthRatio;
        }

        // Calculate forest contribution (scales from min to max based on density)
        // Optimization: skip scanning blocks for forest if cave multiplier is already >= max possible forest multiplier
        double forestMultiplier = 0.0;
        if (caveMultiplier < config.maxForestMultiplier) {
            double forestDensity = context.getForestDensity();
            if (forestDensity > 0.0) {
                // forestDensity is already 0.0-1.0
                forestMultiplier = config.minForestMultiplier + (config.maxForestMultiplier - config.minForestMultiplier) * forestDensity;
            }
        }

        // Use the highest multiplier (base, cave, or forest)
        multiplier = Math.max(multiplier, Math.max(caveMultiplier, forestMultiplier));

        // Dangerous structure: Additional multiplier on top
        if (config.enableStructureModifications && context.isInDangerousStructure()) {
            multiplier *= config.dangerousStructureMultiplier;
        }

        // Mob-specific rules (can override or modify)
        double mobMultiplier = MobRuleRegistry.getMultiplier(context);
        multiplier *= mobMultiplier;

        return multiplier;
    }

    /**
     * Convert multiplier to a boolean spawn decision using normalized probability.
     *
     * Dynamically calculates reference maximum from config values to normalize multipliers:
     * - The highest configured multiplier equals 100% acceptance
     * - Lower multipliers scale proportionally
     * - Higher multipliers = higher acceptance = more spawns
     *
     * Examples with dynamic max of 5.0 (from maxCaveMultiplier):
     * - 0.2x → 4% acceptance (very few spawns)
     * - 1.0x → 20% acceptance (reduced spawns)
     * - 5.0x → 100% acceptance (maximum spawns)
     *
     * @param multiplier The spawn multiplier from environment calculation
     * @param random The random source for probabilistic decisions
     * @return true if spawn should be allowed based on probability
     */
    public static boolean shouldAllowSpawn(double multiplier, RandomSource random) {
        // Hard block for zero/negative multipliers
        if (multiplier <= 0.0) {
            return false;
        }

        // Get config to find reference maximum
        SpawningConfig config = SpawningConfig.HANDLER().instance();

        // Find the highest configured multiplier to use as reference
        double referenceMax = Math.max(
            config.maxCaveMultiplier,
            Math.max(
                config.maxForestMultiplier,
                config.dangerousStructureMultiplier
            )
        );

        // Ensure reference is at least 1.0 to avoid division issues
        if (referenceMax < 1.0) {
            referenceMax = 1.0;
        }

        // Calculate acceptance probability: multiplier / referenceMax
        // Capped at 1.0 (100%) for multipliers >= referenceMax
        double acceptanceProbability = Math.min(1.0, multiplier / referenceMax);

        // Make probabilistic decision
        return random.nextDouble() < acceptanceProbability;
    }
}
