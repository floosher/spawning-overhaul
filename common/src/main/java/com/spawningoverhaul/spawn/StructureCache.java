package com.spawningoverhaul.spawn;

import com.spawningoverhaul.config.SpawningConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.Set;

/**
 * High-performance structure detection using chunk-level structure references.
 * Directly queries active chunk structure references without full registry scans.
 */
public class StructureCache {

    // Hardcoded dangerous structures (increase spawns)
    private static final Set<String> DANGEROUS_STRUCTURES = Set.of(
            "minecraft:stronghold",
            "minecraft:fortress",
            "minecraft:monument",
            "minecraft:mansion",
            "minecraft:mineshaft",
            "minecraft:mineshaft_mesa",
            "minecraft:pillager_outpost",
            "minecraft:ancient_city",
            "minecraft:trial_chambers",
            "minecraft:bastion_remnant",
            "minecraft:end_city",
            "minecraft:swamp_hut",
            "minecraft:dungeon"
    );

    // Hardcoded safe structures (no spawn modification)
    private static final Set<String> SAFE_STRUCTURES = Set.of(
            "minecraft:village"
    );

    /**
     * Check if a position is in a dangerous structure.
     * Fast O(1) query on chunk structure references.
     *
     * @param level The level/world
     * @param pos The position to check
     * @return true if in a dangerous structure
     */
    public static boolean isInDangerousStructure(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        SpawningConfig config = SpawningConfig.HANDLER().instance();
        if (!config.enableStructureModifications) {
            return false;
        }

        return checkStructureAt(serverLevel, pos, config);
    }

    /**
     * Perform structure lookup at a position.
     * Queries only structures referenced in the chunk to avoid iterating the global registry.
     *
     * @param level The server level
     * @param pos The position to check
     * @param config The spawning configuration
     * @return true if dangerous structure present
     */
    private static boolean checkStructureAt(ServerLevel level, BlockPos pos, SpawningConfig config) {
        var structureManager = level.structureManager();
        var structures = structureManager.getAllStructuresAt(pos);
        if (structures.isEmpty()) {
            return false;
        }

        var structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);

        for (Structure structure : structures.keySet()) {
            ResourceLocation structureId = structureRegistry.getKey(structure);
            if (structureId == null) {
                continue;
            }
            String structureIdString = structureId.toString();

            // Check if it's a safe structure
            if (SAFE_STRUCTURES.contains(structureIdString)
                    || structureIdString.startsWith("minecraft:village")
                    || config.additionalSafeStructures.contains(structureIdString)) {
                continue;
            }

            // Check if it's a dangerous structure
            if (DANGEROUS_STRUCTURES.contains(structureIdString)
                    || config.additionalDangerousStructures.contains(structureIdString)) {
                if (structureManager.getStructureWithPieceAt(pos, structure).isValid()) {
                    return true;
                }
            }
        }

        return false;
    }
}
