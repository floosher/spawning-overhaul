package com.spawningoverhaul.mixin;

import com.spawningoverhaul.config.SpawningConfig;
import com.spawningoverhaul.spawn.SpawnContext;
import com.spawningoverhaul.spawn.SpawnMultiplierCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnPlacements.class)
public class SpawnHelperMixin {

    /**
     * Inject into the spawn rules check to modify spawn decisions based on immersive spawning rules.
     * This intercepts spawn attempts and applies environmental multipliers.
     */
    @Inject(method = "checkSpawnRules", at = @At("HEAD"), cancellable = true)
    private static void onCheckSpawnRules(
            EntityType<?> entityType,
            ServerLevelAccessor levelAccessor,
            MobSpawnType spawnType,
            BlockPos pos,
            RandomSource random,
            CallbackInfoReturnable<Boolean> cir
    ) {
        // Fast filter for non-natural spawn types
        if (spawnType == MobSpawnType.SPAWNER || spawnType == MobSpawnType.TRIAL_SPAWNER
                || spawnType == MobSpawnType.SPAWN_EGG || spawnType == MobSpawnType.COMMAND
                || spawnType == MobSpawnType.BREEDING || spawnType == MobSpawnType.BUCKET
                || spawnType == MobSpawnType.CONVERSION || spawnType == MobSpawnType.DISPENSER) {
            return;
        }

        ServerLevel level = levelAccessor.getLevel();

        SpawningConfig config = SpawningConfig.HANDLER().instance();

        // Check if immersive spawning is enabled
        if (!config.enableImmersiveSpawning) {
            return; // Let vanilla handle it
        }

        // Dimension whitelist: bail out if the current dimension isn't opted in
        if (!config.isDimensionEnabled(level.dimension())) {
            return;
        }

        // Create spawn context for this attempt
        SpawnContext context = new SpawnContext(level, pos, entityType, spawnType);

        // Calculate spawn multiplier based on environment and mob rules
        double multiplier = SpawnMultiplierCalculator.calculateMultiplier(context);

        // Make probabilistic spawn decision
        boolean shouldAllow = SpawnMultiplierCalculator.shouldAllowSpawn(multiplier, random);

        // If spawn is denied, cancel the spawn
        if (!shouldAllow) {
            cir.setReturnValue(false);
        }
    }
}
