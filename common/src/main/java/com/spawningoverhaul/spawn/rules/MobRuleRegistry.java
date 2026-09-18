package com.spawningoverhaul.spawn.rules;

import com.spawningoverhaul.config.SpawningConfig;
import com.spawningoverhaul.spawn.SpawnContext;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.Map;

/**
 * Registry for mob-specific spawn rules.
 * Allows custom logic for individual mob types.
 */
public class MobRuleRegistry {

    private static final Map<EntityType<?>, MobSpawnRule> RULES = new HashMap<>();

    /**
     * Register a custom spawn rule for a specific mob type.
     *
     * @param entityType The mob entity type
     * @param rule The spawn rule
     */
    public static void register(EntityType<?> entityType, MobSpawnRule rule) {
        RULES.put(entityType, rule);
    }

    /**
     * Get the spawn multiplier for a specific mob type based on context.
     *
     * @param context The spawn context
     * @return The spawn multiplier (1.0 if no rule registered)
     */
    public static double getMultiplier(SpawnContext context) {
        double multiplier = 1.0;
        MobSpawnRule rule = RULES.get(context.getEntityType());
        if (rule != null) {
            multiplier *= rule.getSpawnMultiplier(context);
        }

        SpawningConfig config = SpawningConfig.HANDLER().instance();
        if (config.mobSpecificMultipliers != null && !config.mobSpecificMultipliers.isEmpty()) {
            ResourceLocation entityKey = BuiltInRegistries.ENTITY_TYPE.getKey(context.getEntityType());
            if (entityKey != null) {
                Double customMultiplier = config.mobSpecificMultipliers.get(entityKey.toString());
                if (customMultiplier != null) {
                    multiplier *= customMultiplier;
                }
            }
        }

        return multiplier;
    }

    /**
     * Register default mob-specific rules.
     * Called during mod initialization.
     */
    public static void registerDefaults() {
        // Spider Rule: ONLY spawn in caves if enabled
        register(EntityType.SPIDER, context -> {
            SpawningConfig config = SpawningConfig.HANDLER().instance();
            if (config.spiderOnlyInCave) {
                return context.isInCave() ? 1.0 : 0.0;
            }
            return 1.0;
        });

        // Cave Spider Rule: Same as spider
        register(EntityType.CAVE_SPIDER, context -> {
            SpawningConfig config = SpawningConfig.HANDLER().instance();
            if (config.spiderOnlyInCave) {
                return context.isInCave() ? 1.0 : 0.0;
            }
            return 1.0;
        });

        // Creeper Rule: Block near dangerous structures if enabled
        register(EntityType.CREEPER, context -> {
            SpawningConfig config = SpawningConfig.HANDLER().instance();
            if (config.disableCreeperNearStructure && context.isInDangerousStructure()) {
                return 0.0;
            }
            return 1.0;
        });
    }
}
