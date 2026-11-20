package com.worive.client.colour;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.BlockDoublePlant;
import net.minecraft.block.BlockLadder;
import net.minecraft.block.BlockTorch;
import net.minecraft.block.BlockWeb;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.entity.Entity;
import net.minecraft.init.Blocks;
import net.minecraft.profiler.Profiler;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.WorldProviderSurface;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.WorldType;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.storage.SaveHandlerMP;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class BlockShapes {

    private static final Logger LOG = LogManager.getLogger("ColorPicker");

    private ShapeWorld shapeWorld;

    boolean fillsCube(Block block, int metadata) {
        // These blocks can report a full selection box for thin models on inventory metadata.
        if (block instanceof BlockWeb || block instanceof BlockDoublePlant
            || block instanceof BlockLadder
            || block instanceof BlockTorch) {
            return false;
        }
        float minX = (float) block.getBlockBoundsMinX();
        float minY = (float) block.getBlockBoundsMinY();
        float minZ = (float) block.getBlockBoundsMinZ();
        float maxX = (float) block.getBlockBoundsMaxX();
        float maxY = (float) block.getBlockBoundsMaxY();
        float maxZ = (float) block.getBlockBoundsMaxZ();
        try {
            if (shapeWorld == null) {
                shapeWorld = new ShapeWorld();
            }
            shapeWorld.block = block;
            shapeWorld.metadata = metadata;
            block.setBlockBoundsForItemRender();
            block.setBlockBoundsBasedOnState(shapeWorld, 0, ShapeWorld.Y, 0);
            if (!fillsCube(block.getSelectedBoundingBoxFromPool(shapeWorld, 0, ShapeWorld.Y, 0))) {
                return false;
            }
            // Multipart blocks such as stairs can have a full outline with gaps inside it.
            // The outline still counts for blocks such as soul sand, which has a shorter collision box.
            List<AxisAlignedBB> boxes = new ArrayList<>();
            block.addCollisionBoxesToList(shapeWorld, 0, ShapeWorld.Y, 0, ShapeWorld.CUBE.copy(), boxes, null);
            return boxes.size() < 2 || boxes.stream()
                .anyMatch(BlockShapes::fillsCube);
        } catch (RuntimeException needsWorldState) {
            LOG.debug(
                "Could not determine the shape of {}:{}",
                Block.blockRegistry.getNameForObject(block),
                metadata,
                needsWorldState);
            return false;
        } finally {
            block.setBlockBounds(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }

    private static boolean fillsCube(AxisAlignedBB bounds) {
        return bounds != null && bounds.minX == 0
            && bounds.minY == ShapeWorld.Y
            && bounds.minZ == 0
            && bounds.maxX == 1
            && bounds.maxY == ShapeWorld.Y + 1
            && bounds.maxZ == 1;
    }

    static boolean hasCollision(Block block) {
        try {
            AxisAlignedBB bounds = block.getCollisionBoundingBoxFromPool(null, 0, 0, 0);
            return bounds != null && bounds.maxX > bounds.minX
                && bounds.maxY > bounds.minY
                && bounds.maxZ > bounds.minZ;
        } catch (RuntimeException needsWorld) {
            return block.getMaterial()
                .blocksMovement();
        }
    }

    // The shape hooks can read metadata and neighbors even when the picker is outside a loaded world.
    private static final class ShapeWorld extends World {

        private static final int Y = 64;
        private static final AxisAlignedBB CUBE = AxisAlignedBB.getBoundingBox(0, Y, 0, 1, Y + 1, 1);

        private Block block;
        private int metadata;

        ShapeWorld() {
            super(
                new SaveHandlerMP(),
                "Block Shapes",
                new WorldProviderSurface(),
                new WorldSettings(0, WorldSettings.GameType.CREATIVE, false, false, WorldType.FLAT),
                new Profiler());
            isRemote = true;
            provider.registerWorld(this);
            chunkProvider = createChunkProvider();
        }

        @Override
        public Block getBlock(int x, int y, int z) {
            return x == 0 && y == Y && z == 0 ? block : Blocks.air;
        }

        @Override
        public int getBlockMetadata(int x, int y, int z) {
            return x == 0 && y == Y && z == 0 ? metadata : 0;
        }

        @Override
        protected IChunkProvider createChunkProvider() {
            return new ChunkProviderClient(this);
        }

        @Override
        protected int func_152379_p() {
            return 0;
        }

        @Override
        public Entity getEntityByID(int id) {
            return null;
        }
    }
}
