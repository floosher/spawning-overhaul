package com.spawningoverhaul.event;

import com.spawningoverhaul.SpawningOverhaulCommon;
import com.spawningoverhaul.config.SpawningConfig;
import com.spawningoverhaul.spawn.SpawnContext;
import com.spawningoverhaul.spawn.SpawnMultiplierCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobSpawnEvent;

/**
 * NeoForge event handler for immersive spawning mechanics.
 * Intercepts mob spawn position checks to apply environment-based spawn rules.
 */
public class SpawnEventHandler {

    /**
     * Handles mob spawn position checks and applies immersive spawning rules.
     * Fired after SpawnPlacements.checkSpawnRules has been evaluated.
     */
    @SubscribeEvent
    public void onMobSpawnPositionCheck(MobSpawnEvent.PositionCheck event) {
        // Get config
        SpawningConfig config = SpawningConfig.HANDLER().instance();

        // Check if immersive spawning is enabled
        if (!config.enableImmersiveSpawning) {
            return; // Let vanilla handle it
        }

        // Fast filter for non-natural spawn types (spawners, breeding, spawn eggs, commands, etc.)
        MobSpawnType spawnType = event.getSpawnType();
        if (spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.TRIAL_SPAWNER
                || spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND
                || spawnType == MobSpawnType.BREEDING || spawnType == MobSpawnType.BUCKET
                || spawnType == MobSpawnType.CONVERSION || spawnType == MobSpawnType.DISPENSER) {
            return;
        }

        // Verify we're on the server side and have a ServerLevel
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        // Dimension whitelist: bail out if the current dimension isn't opted in
        if (!config.isDimensionEnabled(serverLevel.dimension())) {
            return;
        }

        // Get spawn information from event
        var mob = event.getEntity();

        var entityType = mob.getType();
        // Create BlockPos correctly using floor coordinates
        var spawnPos = BlockPos.containing(event.getX(), event.getY(), event.getZ());

        // Create spawn context for this attempt
        SpawnContext context = new SpawnContext(serverLevel, spawnPos, entityType, spawnType);

        // Calculate spawn multiplier based on environment and mob rules
        double multiplier = SpawnMultiplierCalculator.calculateMultiplier(context);

        // Make probabilistic spawn decision
        boolean shouldAllow = SpawnMultiplierCalculator.shouldAllowSpawn(multiplier, serverLevel.getRandom());

        // If spawn is denied, set result to FAIL
        if (!shouldAllow) {
            event.setResult(MobSpawnEvent.PositionCheck.Result.FAIL);

            // Debug logging
            if (config.debugmode) {
                if (SpawningOverhaulCommon.getLogger() != null) {
                    SpawningOverhaulCommon.getLogger().info(
                            "Denied spawn of {} at {} (multiplier: {}, outside: {}, cave: {}, forest: {})",
                            entityType.getDescription().getString(),
                            spawnPos,
                            String.format("%.2f", multiplier),
                            context.isOutside(),
                            context.isInCave(),
                            String.format("%.0f%%", context.getForestDensity() * 100.0)
                    );
                }
            }
        }
        // If allowed, leave result as DEFAULT to let vanilla checks proceed
    }
}
