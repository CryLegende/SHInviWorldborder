package org.Cry_Legende.sHInviWorldborder;

import io.papermc.paper.event.world.border.WorldBorderBoundsChangeEvent;
import io.papermc.paper.event.world.border.WorldBorderCenterChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Retient les joueurs dans les bordures : mur glissant pour les déplacements, perles et chorus
 * refusés au-delà, retour sur l'île ou au spawn pour un joueur qui se retrouve déjà dehors.
 */
final class BorderListener implements Listener {

    /**
     * Écart, en blocs, au-delà duquel un joueur est considéré comme déjà dehors. Le serveur ne signale
     * pas les pas de moins de 1/16 de bloc, un joueur contre le mur peut donc le dépasser de si peu.
     */
    private static final double OUTSIDE_TOLERANCE = 0.25;

    private final SHInviWorldborder plugin;
    /** Joueurs dont le retour ou la descente de monture est déjà programmé pour le tick suivant. */
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();

    BorderListener(SHInviWorldborder plugin) {
        this.plugin = plugin;
    }

    // LOW : décidé avant les autres plugins, qui reçoivent le déplacement déjà annulé
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getX() == to.getX() && from.getZ() == to.getZ()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || from.getWorld() != to.getWorld()) {
            return;
        }
        double halfWidth = player.getWidth() / 2.0;
        Area islandBox = plugin.islandBox(player, from, halfWidth);
        Area worldBox = plugin.worldBox(from.getWorld(), halfWidth);
        if (islandBox == null && worldBox == null) {
            return;
        }
        // Les droits ne sont vérifiés que lorsqu'un mur est franchi
        if (islandBox != null && !islandBox.contains(to.getX(), to.getZ()) && plugin.ignoresIslandBorder(player)) {
            islandBox = null;
        }
        if (worldBox != null && !worldBox.contains(to.getX(), to.getZ()) && player.hasPermission(SHInviWorldborder.BYPASS)) {
            worldBox = null;
        }
        Area box = Area.intersect(worldBox, islandBox);
        if (box == null || box.contains(to.getX(), to.getZ())) {
            return;
        }
        if (!box.contains(from.getX(), from.getZ(), OUTSIDE_TOLERANCE)) {
            event.setCancelled(true);
            boolean outsideIsland = islandBox != null && !islandBox.contains(from.getX(), from.getZ(), OUTSIDE_TOLERANCE);
            sendBack(player, from.clone(), outsideIsland);
            return;
        }
        if (player.isInsideVehicle()) {
            // Monture ou bateau piloté : le véhicule a déjà avancé, le joueur en descend contre le mur
            dismount(player, box);
            return;
        }
        // Quand le déplacement est annulé, le serveur replace le joueur sur l'objet « from » lui-même :
        // on le pose contre le mur, à la hauteur et au glissement demandés, pour qu'il longe la bordure.
        from.set(box.clampX(to.getX()), to.getY(), box.clampZ(to.getZ()));
        from.setYaw(to.getYaw());
        from.setPitch(to.getPitch());
        event.setCancelled(true);
    }

    // Perle et fruit de chorus (CONSUMABLE_EFFECT)
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        switch (event.getCause()) {
            case ENDER_PEARL, CONSUMABLE_EFFECT -> {
            }
            default -> {
                return;
            }
        }
        Player player = event.getPlayer();
        Location to = event.getTo();
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        double halfWidth = player.getWidth() / 2.0;
        Area islandBox = plugin.islandBox(player, to, halfWidth);
        Area worldBox = plugin.worldBox(to.getWorld(), halfWidth);
        boolean blocked = islandBox != null && !islandBox.contains(to.getX(), to.getZ()) && !plugin.ignoresIslandBorder(player)
                || worldBox != null && !worldBox.contains(to.getX(), to.getZ()) && !player.hasPermission(SHInviWorldborder.BYPASS);
        if (blocked) {
            event.setCancelled(true);
            plugin.messages().send(player, "teleport-blocked");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pending.remove(uuid);
        plugin.forget(uuid);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.registerWorld(event.getWorld());
    }

    // Le monde déchargé retrouve son vrai worldborder natif avant d'être sauvegardé
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldUnload(WorldUnloadEvent event) {
        plugin.worlds().unregister(event.getWorld());
    }

    // /worldborder set (ou un plugin) : la nouvelle taille devient celle de la bordure invisible
    @EventHandler(ignoreCancelled = true)
    public void onBorderSize(WorldBorderBoundsChangeEvent event) {
        if (isRealBorder(event.getWorld(), event.getWorldBorder().getWorld())) {
            event.setCancelled(true);
            plugin.worlds().changeSize(event.getWorld(), event.getNewSize());
        }
    }

    // /worldborder center (ou un plugin) : le nouveau centre devient celui de la bordure invisible
    @EventHandler(ignoreCancelled = true)
    public void onBorderCenter(WorldBorderCenterChangeEvent event) {
        if (isRealBorder(event.getWorld(), event.getWorldBorder().getWorld())) {
            event.setCancelled(true);
            plugin.worlds().changeCenter(event.getWorld(), event.getNewCenter().getX(), event.getNewCenter().getZ());
        }
    }

    /** Vrai pour un changement du worldborder d'un monde suivi, hors changements faits par le plugin. */
    private boolean isRealBorder(World world, World borderWorld) {
        return borderWorld != null && plugin.settings().worlds().enabled() && !plugin.worlds().isSelfChange()
                && plugin.worlds().isRegistered(world);
    }

    private void sendBack(Player player, Location at, boolean island) {
        if (!pending.add(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (!player.isOnline()) {
                return;
            }
            if (island && plugin.islands() != null) {
                plugin.islands().sendBack(player, at);
                plugin.messages().send(player, "island-outside");
                return;
            }
            World world = at.getWorld();
            Location spawn = world.getSpawnLocation();
            Area area = plugin.worlds().area(world);
            if (area != null && !area.contains(spawn.getX(), spawn.getZ())) {
                spawn = new Location(world, (area.minX() + area.maxX()) / 2.0, spawn.getY(), (area.minZ() + area.maxZ()) / 2.0);
            }
            player.teleport(spawn);
            plugin.messages().send(player, "world-outside");
        });
    }

    private void dismount(Player player, Area box) {
        if (!pending.add(player.getUniqueId())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            Entity vehicle = player.getVehicle();
            if (!player.isOnline() || vehicle == null) {
                return;
            }
            player.leaveVehicle();
            vehicle.teleport(clamp(vehicle.getLocation(), box.expand(player.getWidth() / 2.0 - vehicle.getWidth() / 2.0)));
            player.teleport(clamp(player.getLocation(), box));
        });
    }

    private static Location clamp(Location location, Area box) {
        location.setX(box.clampX(location.getX()));
        location.setZ(box.clampZ(location.getZ()));
        return location;
    }
}
