package com.myriad.game;

import com.myriad.Myriad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;

/** The bundled court is placed once in its own dimension, before the first arrival. */
public final class TrainingGroundMap {
    private static final Identifier MAP = Identifier.fromNamespaceAndPath(Myriad.MODID, "training_ground");
    public static final ResourceKey<Level> KEY = ResourceKey.create(Registries.DIMENSION, MAP);
    public static final BlockPos SPAWN = new BlockPos(0, 65, 0);
    private static final BlockPos ORIGIN = new BlockPos(-36, 64, -8);
    private static final BlockPos COMPLETED = SPAWN.below(2);

    private TrainingGroundMap() { }

    public static boolean prepare(ServerLevel level) {
        if (level == null || !level.dimension().equals(KEY)) return false;
        if (level.getBlockState(COMPLETED).is(Blocks.LODESTONE)) return true;
        var template = level.getServer().getStructureTemplateManager().get(MAP);
        if (template.isEmpty()) {
            Myriad.LOGGER.error("Missing training ground structure {}", MAP);
            return false;
        }
        var settings = new StructurePlaceSettings().setIgnoreEntities(true).setKnownShape(true);
        if (!template.get().placeInWorld(level, ORIGIN, ORIGIN, settings, level.getRandom(), Block.UPDATE_CLIENTS)) {
            Myriad.LOGGER.error("Could not place training ground structure {}", MAP);
            return false;
        }
        level.setBlockAndUpdate(COMPLETED, Blocks.LODESTONE.defaultBlockState());
        return true;
    }
}
