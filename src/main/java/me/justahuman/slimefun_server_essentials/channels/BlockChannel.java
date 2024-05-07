package me.justahuman.slimefun_server_essentials.channels;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunBlockBreakEvent;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunBlockPlaceEvent;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.ChunkPosition;
import me.mrCookieSlime.CSCoreLibPlugin.Configuration.Config;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class BlockChannel extends AbstractChannel {
    private static final Map<ChunkPosition, Set<BlockPosition>> BLOCK_CACHE = new HashMap<>();

    @Override
    public String getChannel() {
        return "slimefun_server_essentials:block";
    }

    @Override
    public void onMessageReceived(@Nonnull Player player, @Nonnull byte[] message) {
        final World world = player.getWorld();

        final ByteArrayDataInput packet = ByteStreams.newDataInput(message);
        final int chunkX = packet.readInt();
        final int chunkZ = packet.readInt();
        final ChunkPosition chunkPosition = new ChunkPosition(world, chunkX, chunkZ);

        if (BLOCK_CACHE.containsKey(chunkPosition)) {
            final Set<BlockPosition> blockPositions = BLOCK_CACHE.get(chunkPosition);
            blockPositions.removeIf(blockPosition -> !StorageCacheUtils.hasBlock(blockPosition.toLocation()));

            for (BlockPosition blockPosition : blockPositions) {
                sendSlimefunBlock(player, blockPosition, StorageCacheUtils.getBlock(blockPosition.toLocation()).getSfId());
            }

            BLOCK_CACHE.put(chunkPosition, blockPositions);
            return;
        }
        var allBlockData = Slimefun.getDatabaseManager().getBlockDataController().getChunkData(chunkPosition.getChunk()).getAllBlockData();
        final Set<BlockPosition> blockPositions = new HashSet<>();
        for (var blockData : allBlockData) {
            final Location location = blockData.getLocation();
            final BlockPosition blockPosition = new BlockPosition(location);
            blockPositions.add(blockPosition);
            sendSlimefunBlock(player, blockPosition, blockData.getSfId());
        }

        BLOCK_CACHE.put(chunkPosition, blockPositions);
    }

    // Slimefun Block Place Event
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onSlimefunBlockPlace(SlimefunBlockPlaceEvent event) {
        final Block block = event.getBlockPlaced();
        final BlockPosition blockPosition = new BlockPosition(block);
        final ChunkPosition chunkPosition = new ChunkPosition(blockPosition.getChunk());
        final Set<BlockPosition> blockPositions = BLOCK_CACHE.getOrDefault(chunkPosition, new HashSet<>());

        blockPositions.add(blockPosition);
        BLOCK_CACHE.put(chunkPosition, blockPositions);

        final String id = event.getSlimefunItem().getId();
        for (UUID uuid : players) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && block.getWorld() == player.getWorld() && player.getLocation().distanceSquared(block.getLocation()) <= Math.pow(player.getClientViewDistance() * 16D, 2)) {
                sendSlimefunBlock(player, blockPosition, id);
            }
        }
    }

    // Slimefun Block Break Event
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    private void onSlimefunBlockBreak(SlimefunBlockBreakEvent event) {
        final Block block = event.getBlockBroken();
        final BlockPosition blockPosition = new BlockPosition(block);
        final ChunkPosition chunkPosition = new ChunkPosition(blockPosition.getChunk());
        final Set<BlockPosition> blockPositions = BLOCK_CACHE.getOrDefault(chunkPosition, new HashSet<>());

        blockPositions.remove(blockPosition);
        BLOCK_CACHE.put(chunkPosition, blockPositions);

        for (UUID uuid : players) {
            final Player player = Bukkit.getPlayer(uuid);
            if (player != null && block.getWorld() == player.getWorld() && player.getLocation().distanceSquared(block.getLocation()) <= Math.pow(player.getClientViewDistance() * 16D, 2)) {
                sendSlimefunBlock(player, blockPosition, " ");
            }
        }
    }

    @ParametersAreNonnullByDefault
    public void sendSlimefunBlock(Player player, BlockPosition blockPosition, String id) {
        final ByteArrayDataOutput packet = ByteStreams.newDataOutput();
        packet.writeInt(blockPosition.getX());
        packet.writeInt(blockPosition.getY());
        packet.writeInt(blockPosition.getZ());
        packet.writeUTF(id);
        sendMessage(player, packet);
    }
}
