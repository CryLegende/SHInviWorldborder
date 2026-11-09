package org.Cry_Legende.sHInviWorldborder;

import com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI;
import com.bgsoftware.superiorskyblock.api.handlers.GridManager;
import com.bgsoftware.superiorskyblock.api.island.Island;
import com.bgsoftware.superiorskyblock.api.wrappers.BlockPosition;
import com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bordures des îles SuperiorSkyblock2 : le mur suit la zone protégée de l'île (centre ± taille),
 * c'est-à-dire la zone où les membres peuvent construire.
 */
final class IslandBorders {

    /** Durée, en ticks, pendant laquelle la zone d'île d'un joueur immobile est réutilisée. */
    private static final int CACHE_TICKS = 20;

    /** Zone d'île calculée pour un joueur à une position de bloc donnée. */
    private record Cached(UUID world, int blockX, int blockZ, int tick, Area edge) {
    }

    private final GridManager grid = SuperiorSkyblockAPI.getGrid();
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    /**
     * Le mur de l'île où se trouve « at » (bord de la zone protégée), ou null hors d'une île.
     * Recalculé quand le joueur change de bloc, et au plus tard chaque seconde (agrandissement d'île).
     */
    Area edge(Player player, Location at) {
        World world = at.getWorld();
        if (!grid.isIslandsWorld(world)) {
            return null;
        }
        int blockX = at.getBlockX();
        int blockZ = at.getBlockZ();
        int now = Bukkit.getCurrentTick();
        Cached cached = cache.get(player.getUniqueId());
        if (cached != null && cached.blockX == blockX && cached.blockZ == blockZ
                && now - cached.tick < CACHE_TICKS && cached.world.equals(world.getUID())) {
            return cached.edge;
        }
        Area edge = lookup(at);
        cache.put(player.getUniqueId(), new Cached(world.getUID(), blockX, blockZ, now, edge));
        return edge;
    }

    /** Joueurs que la bordure d'île ne retient pas : mode bypass de SuperiorSkyblock2. */
    boolean bypass(Player player) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        return superiorPlayer != null && superiorPlayer.hasBypassModeEnabled();
    }

    /** Ramène le joueur sur l'île où il se trouve (ou la sienne), sinon au spawn de SuperiorSkyblock2. */
    void sendBack(Player player, Location at) {
        SuperiorPlayer superiorPlayer = SuperiorSkyblockAPI.getPlayer(player);
        Island island = grid.getIslandAt(at);
        if (island == null || island.isSpawn()) {
            island = superiorPlayer.getIsland();
        }
        superiorPlayer.teleport(island != null ? island : grid.getSpawnIsland());
    }

    void forget(UUID player) {
        cache.remove(player);
    }

    private Area lookup(Location at) {
        Island island = grid.getIslandAt(at);
        if (island == null || island.isSpawn()) {
            return null;
        }
        // Zone protégée en blocs : [centre - taille, centre + taille], soit [min, max + 1) en coordonnées continues
        BlockPosition center = island.getCenterPosition();
        int size = island.getIslandSize();
        return new Area(center.getX() - size, center.getZ() - size, center.getX() + size + 1, center.getZ() + size + 1);
    }
}
