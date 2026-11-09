package org.Cry_Legende.sHInviWorldborder;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Affiche, à chaque joueur proche d'une bordure, un nuage de particules sur le mur.
 * Un mur = un seul paquet : le client répartit lui-même les particules autour du point visé.
 */
final class ParticleTask implements Runnable {

    private final SHInviWorldborder plugin;
    /** Position réutilisée d'un joueur à l'autre (la tâche tourne sur le thread principal). */
    private final Location scratch = new Location(null, 0, 0, 0);

    ParticleTask(SHInviWorldborder plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        Settings settings = plugin.settings();
        Settings.Particles worldParticles = settings.worlds().enabled() ? settings.worlds().particles() : null;
        Settings.Particles islandParticles = settings.islands().enabled() && plugin.islands() != null
                ? settings.islands().particles() : null;
        if (worldParticles == null && islandParticles == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR) {
                continue;
            }
            Location at = player.getLocation(scratch);
            double eyeY = at.getY() + player.getEyeHeight();
            if (worldParticles != null) {
                Area wall = plugin.worlds().area(at.getWorld());
                if (wall != null) {
                    draw(player, wall, at.getX(), eyeY, at.getZ(), worldParticles);
                }
            }
            if (islandParticles != null) {
                Area wall = plugin.islands().edge(player, at);
                if (wall != null) {
                    draw(player, wall, at.getX(), eyeY, at.getZ(), islandParticles);
                }
            }
        }
    }

    /** Dessine les murs du rectangle proches du joueur, face à lui et sans dépasser leurs extrémités. */
    private static void draw(Player player, Area wall, double x, double y, double z, Settings.Particles particles) {
        double alongZ = wall.clampZ(z);
        double alongX = wall.clampX(x);
        double offZ = Math.abs(z - alongZ);
        double offX = Math.abs(x - alongX);
        wallX(player, wall.minX(), y, alongZ, Math.hypot(x - wall.minX(), offZ), particles);
        wallX(player, wall.maxX(), y, alongZ, Math.hypot(x - wall.maxX(), offZ), particles);
        wallZ(player, alongX, y, wall.minZ(), Math.hypot(z - wall.minZ(), offX), particles);
        wallZ(player, alongX, y, wall.maxZ(), Math.hypot(z - wall.maxZ(), offX), particles);
    }

    /** Mur à x constant : les particules s'étalent le long de z et en hauteur. */
    private static void wallX(Player player, double x, double y, double z, double distance, Settings.Particles p) {
        int count = distance <= p.distance() ? p.countAt(distance) : 0;
        if (count > 0) {
            player.spawnParticle(Particle.DUST, x, y, z, count, 0.0, p.spreadY(), p.spread(), 0.0, p.dust());
        }
    }

    /** Mur à z constant : les particules s'étalent le long de x et en hauteur. */
    private static void wallZ(Player player, double x, double y, double z, double distance, Settings.Particles p) {
        int count = distance <= p.distance() ? p.countAt(distance) : 0;
        if (count > 0) {
            player.spawnParticle(Particle.DUST, x, y, z, count, p.spread(), p.spreadY(), 0.0, 0.0, p.dust());
        }
    }
}
