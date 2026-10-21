package org.Cry_Legende.sHInviWorldborder;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class SHInviWorldborder extends JavaPlugin {

    // Stocke les paramètres originels (persistés) du worldborder
    private final Map<World, BorderSettings> persistentBorders = new HashMap<>();
    // Fichier de persistance (borderdata.yml)
    private File borderDataFile;
    private FileConfiguration borderDataConfig;

    // Option debug
    private boolean debug;

    // Rayon pour afficher les particules (seulement pour les joueurs proches)
    private final double particleDisplayRadius = 16.0;
    // Tolérance en blocs pour la détection (pour éviter les faux hors limites)
    private final double TOLERANCE = 0.2;

    @Override
    public void onEnable() {
        getLogger().info("SHInviWorldborder activé.");
        saveDefaultConfig();
        debug = getConfig().getBoolean("debug", false);

        initBorderDataFile();

        // Pour chaque monde, charge ou initialise les paramètres originels (persistés)
        for (World world : Bukkit.getWorlds()) {
            BorderSettings bs = loadBorderSettingsForWorld(world);
            if (bs == null) {
                WorldBorder wb = world.getWorldBorder();
                bs = new BorderSettings(
                    wb.getCenter(),
                    wb.getSize(),
                    wb.getWarningDistance(),
                    wb.getWarningTime(),
                    wb.getDamageBuffer(),
                    wb.getDamageAmount()
                );
                saveBorderSettingsForWorld(world, bs);
            }
            persistentBorders.put(world, bs);
            if (debug) {
                getLogger().info("[DEBUG] Monde " + world.getName() + " - Border persistée: taille="
                    + bs.getSize() + ", centre=" + bs.getCenter());
            }
            // Masque l'affichage du worldborder natif
            applyOverride(world, bs);
        }

        // Réappliquer l'override visuel chaque seconde
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (World world : Bukkit.getWorlds()) {
                BorderSettings bs = persistentBorders.get(world);
                if (bs != null) {
                    applyOverride(world, bs);
                }
            }
        }, 20L, 20L);

        // Tâche planifiée pour afficher les particules près du worldborder (exécute toutes les 5 ticks, soit 0,25 sec)
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            // Cache la liste des joueurs en ligne pour éviter de répéter getOnlinePlayers() dans la boucle
            for (Player player : Bukkit.getOnlinePlayers()) {
                World world = player.getWorld();
                BorderSettings bs = persistentBorders.get(world);
                if (bs == null) continue;

                // Calcul unique du worldborder pour le monde courant
                double half = bs.getSize() / 2.0;
                double centerX = bs.getCenter().getX();
                double centerZ = bs.getCenter().getZ();
                double xMin = centerX - half;
                double xMax = centerX + half;
                double zMin = centerZ - half;
                double zMax = centerZ + half;

                double px = player.getLocation().getX();
                double pz = player.getLocation().getZ();
                // Utilisation de l'eyeLocation pour une meilleure visibilité (optionnel)
                double py = player.getEyeLocation().getY();

                double globalRange = particleDisplayRadius; // reste 16 blocs
                double spacing = 2.0;  // espacement fixe de 2 blocs entre particules

                // Pour chacun des bords, on calcule la distance et on adapte la grille :
                // Seuils adaptés :
                //   - ≤ 4 blocs → grille 4×4
                //   - ≤ 8 blocs → grille 3×3
                //   - sinon (mais <= globalRange) → grille 2×2

                // --- Bord supérieur (z = zMin) ---
                double distTop = Math.abs(pz - zMin);
                if (distTop <= globalRange) {
                    int gridSize = (distTop <= 8) ? 4 : (distTop <= 12 ? 3 : 2);
                    // Centre horizontal le long du bord : clamp la position X dans [xMin, xMax]
                    double xCenter = Math.max(xMin, Math.min(px, xMax));
                    double yCenter = py;
                    for (int i = 0; i < gridSize; i++) {
                        for (int j = 0; j < gridSize; j++) {
                            double offsetX = (i - (gridSize - 1) / 2.0) * spacing;
                            double offsetY = (j - (gridSize - 1) / 2.0) * spacing;
                            Location loc = new Location(world, xCenter + offsetX, yCenter + offsetY, zMin);
                            sendRedstoneParticle(player, loc);
                        }
                    }
                }

                // --- Bord inférieur (z = zMax) ---
                double distBottom = Math.abs(pz - zMax);
                if (distBottom <= globalRange) {
                    int gridSize = (distBottom <= 8) ? 4 : (distBottom <= 12 ? 3 : 2);
                    double xCenter = Math.max(xMin, Math.min(px, xMax));
                    double yCenter = py;
                    for (int i = 0; i < gridSize; i++) {
                        for (int j = 0; j < gridSize; j++) {
                            double offsetX = (i - (gridSize - 1) / 2.0) * spacing;
                            double offsetY = (j - (gridSize - 1) / 2.0) * spacing;
                            Location loc = new Location(world, xCenter + offsetX, yCenter + offsetY, zMax);
                            sendRedstoneParticle(player, loc);
                        }
                    }
                }

                // --- Bord gauche (x = xMin) ---
                double distLeft = Math.abs(px - xMin);
                if (distLeft <= globalRange) {
                    int gridSize = (distLeft <= 8) ? 4 : (distLeft <= 12 ? 3 : 2);
                    // Axe horizontal le long du mur = axe Z
                    double zCenter = Math.max(zMin, Math.min(pz, zMax));
                    double yCenter = py;
                    for (int i = 0; i < gridSize; i++) {
                        for (int j = 0; j < gridSize; j++) {
                            double offsetZ = (i - (gridSize - 1) / 2.0) * spacing;
                            double offsetY = (j - (gridSize - 1) / 2.0) * spacing;
                            Location loc = new Location(world, xMin, yCenter + offsetY, zCenter + offsetZ);
                            sendRedstoneParticle(player, loc);
                        }
                    }
                }

                // --- Bord droit (x = xMax) ---
                double distRight = Math.abs(px - xMax);
                if (distRight <= globalRange) {
                    int gridSize = (distRight <= 8) ? 4 : (distRight <= 12 ? 3 : 2);
                    double zCenter = Math.max(zMin, Math.min(pz, zMax));
                    double yCenter = py;
                    for (int i = 0; i < gridSize; i++) {
                        for (int j = 0; j < gridSize; j++) {
                            double offsetZ = (i - (gridSize - 1) / 2.0) * spacing;
                            double offsetY = (j - (gridSize - 1) / 2.0) * spacing;
                            Location loc = new Location(world, xMax, yCenter + offsetY, zCenter + offsetZ);
                            sendRedstoneParticle(player, loc);
                        }
                    }
                }
            }
        }, 0L, 5L);


        // Enregistrement du listener de mouvement
        getServer().getPluginManager().registerEvents(new BorderMoveListener(), this);
    }

    @Override
    public void onDisable() {
        getLogger().info("SHInviWorldborder désactivé. Restauration des paramètres originels du worldborder.");
        // À la désactivation, restaure les vraies valeurs pour chaque monde
        for (World world : persistentBorders.keySet()) {
            BorderSettings bs = persistentBorders.get(world);
            if (bs != null) {
                WorldBorder wb = world.getWorldBorder();
                wb.setCenter(bs.getCenter());
                // 1.21+ refuse une taille au-delà du maximum vanilla (5.9999968E7)
                wb.setSize(Math.max(1.0, Math.min(bs.getSize(), 5.9999968E7)));
                wb.setWarningDistance(bs.getWarningDistance());
                wb.setWarningTime(bs.getWarningTime());
                wb.setDamageBuffer(bs.getDamageBuffer());
                wb.setDamageAmount(bs.getDamageAmount());
            }
        }
    }

    // Applique l'override sur le worldborder natif en fixant sa taille à 1.0E7
    private void applyOverride(World world, BorderSettings bs) {
        WorldBorder wb = world.getWorldBorder();
        wb.setCenter(bs.getCenter());
        wb.setSize(1.0E7);
        wb.setWarningDistance(0);
        wb.setWarningTime(0);
    }

    // --- Persistance (borderdata.yml) ---

    private void initBorderDataFile() {
        borderDataFile = new File(getDataFolder(), "borderdata.yml");
        if (!borderDataFile.exists()) {
            getDataFolder().mkdirs();
            try {
                borderDataFile.createNewFile();
            } catch (IOException e) {
                getLogger().severe("Impossible de créer le fichier borderdata.yml");
            }
        }
        borderDataConfig = YamlConfiguration.loadConfiguration(borderDataFile);
    }

    private BorderSettings loadBorderSettingsForWorld(World world) {
        if (borderDataConfig.contains(world.getName())) {
            double x = borderDataConfig.getDouble(world.getName() + ".center.x");
            double y = borderDataConfig.getDouble(world.getName() + ".center.y");
            double z = borderDataConfig.getDouble(world.getName() + ".center.z");
            Location center = new Location(world, x, y, z);
            double size = borderDataConfig.getDouble(world.getName() + ".size");
            int warningDistance = borderDataConfig.getInt(world.getName() + ".warningDistance");
            int warningTime = borderDataConfig.getInt(world.getName() + ".warningTime");
            double damageBuffer = borderDataConfig.getDouble(world.getName() + ".damageBuffer");
            double damageAmount = borderDataConfig.getDouble(world.getName() + ".damageAmount");
            return new BorderSettings(center, size, warningDistance, warningTime, damageBuffer, damageAmount);
        }
        return null;
    }

    private void saveBorderSettingsForWorld(World world, BorderSettings bs) {
        String path = world.getName();
        borderDataConfig.set(path + ".center.x", bs.getCenter().getX());
        borderDataConfig.set(path + ".center.y", bs.getCenter().getY());
        borderDataConfig.set(path + ".center.z", bs.getCenter().getZ());
        borderDataConfig.set(path + ".center.world", bs.getCenter().getWorld().getName());
        borderDataConfig.set(path + ".size", bs.getSize());
        borderDataConfig.set(path + ".warningDistance", bs.getWarningDistance());
        borderDataConfig.set(path + ".warningTime", bs.getWarningTime());
        borderDataConfig.set(path + ".damageBuffer", bs.getDamageBuffer());
        borderDataConfig.set(path + ".damageAmount", bs.getDamageAmount());
        try {
            borderDataConfig.save(borderDataFile);
            if (debug)
                getLogger().info("[DEBUG] Données persistantes sauvegardées pour le monde " + world.getName());
        } catch (IOException e) {
            getLogger().severe("Impossible de sauvegarder les données du worldborder pour le monde " + world.getName());
        }
    }

    // --- Affichage des particules via l'API Bukkit/Spigot ---

    private void sendRedstoneParticle(Player player, Location loc) {
        player.spawnParticle(Particle.REDSTONE, loc, 1, 0, 0, 0,
            new Particle.DustOptions(Color.RED, 1.0F));
        if (debug) {
            getLogger().info("[DEBUG] Particule rouge affichée à " + loc + " pour " + player.getName());
        }
    }

    // --- Listener sur les déplacements ---

    public class BorderMoveListener implements Listener {
        /**
         * Vérifie si la position 'loc' est considérée inside la bordure,
         * avec une tolérance afin d'éviter les faux hors limite.
         */
        private boolean isInside(Location loc, BorderSettings bs) {
            double halfSize = bs.getSize() / 2.0;
            double deltaX = Math.abs(loc.getX() - bs.getCenter().getX());
            double deltaZ = Math.abs(loc.getZ() - bs.getCenter().getZ());
            return deltaX <= halfSize + TOLERANCE && deltaZ <= halfSize + TOLERANCE;
        }

        @EventHandler
        public void onPlayerMove(PlayerMoveEvent event) {
            // Traiter uniquement les déplacements inter-blocs
            if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
                return;
            }
            Player player = event.getPlayer();
            World world = player.getWorld();
            BorderSettings bs = persistentBorders.get(world);
            if (bs == null) return;

            boolean fromInside = isInside(event.getFrom(), bs);
            boolean toInside = isInside(event.getTo(), bs);

            if (debug) {
                getLogger().info("[DEBUG] " + player.getName() + " de " + event.getFrom() + " vers " + event.getTo() +
                    " | fromInside=" + fromInside + ", toInside=" + toInside);
            }

            // Si le joueur tente de sortir de la zone autorisée
            if (fromInside && !toInside) {
                // Annuler le déplacement pour le bloquer, sans modifier sa position
                event.setCancelled(true);
                if (debug)
                    getLogger().info("[DEBUG] " + player.getName() + " bloqué à la limite.");
            }
            // Si le joueur se retrouve toujours hors après déplacement
            else if (!isInside(player.getLocation(), bs)) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(SHInviWorldborder.this, () -> {
                    Location spawn = world.getSpawnLocation();
                    if (!isInside(spawn, bs)) {
                        if (debug)
                            getLogger().info("[DEBUG] Le spawn n'est pas dans la zone autorisée, utilisation du centre.");
                        spawn = bs.getCenter();
                    }
                    player.teleport(spawn);
                    String rawMessage = getConfig().getString("outOfBorderMessage", "&cVous êtes en dehors de la limite, retour au spawn !");
                    String coloredMessage = ChatColor.translateAlternateColorCodes('&', rawMessage);
                    player.sendMessage(coloredMessage);
                    if (debug)
                        getLogger().info("[DEBUG] " + player.getName() + " téléporté à " + spawn);
                });
            }
        }

    }

    // --- Classe de stockage des paramètres du worldborder ---

    public static class BorderSettings {
        private final Location center;
        private final double size;
        private final int warningDistance;
        private final int warningTime;
        private final double damageBuffer;
        private final double damageAmount;

        public BorderSettings(Location center, double size, int warningDistance, int warningTime, double damageBuffer, double damageAmount) {
            this.center = center;
            this.size = size;
            this.warningDistance = warningDistance;
            this.warningTime = warningTime;
            this.damageBuffer = damageBuffer;
            this.damageAmount = damageAmount;
        }

        public Location getCenter() {
            return center;
        }

        public double getSize() {
            return size;
        }

        public int getWarningDistance() {
            return warningDistance;
        }

        public int getWarningTime() {
            return warningTime;
        }

        public double getDamageBuffer() {
            return damageBuffer;
        }

        public double getDamageAmount() {
            return damageAmount;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BorderSettings)) return false;
            BorderSettings that = (BorderSettings) o;
            return Double.compare(that.size, size) == 0 &&
                warningDistance == that.warningDistance &&
                warningTime == that.warningTime &&
                Double.compare(that.damageBuffer, damageBuffer) == 0 &&
                Double.compare(that.damageAmount, damageAmount) == 0 &&
                Objects.equals(center, that.center);
        }

        @Override
        public int hashCode() {
            return Objects.hash(center, size, warningDistance, warningTime, damageBuffer, damageAmount);
        }
    }
}
