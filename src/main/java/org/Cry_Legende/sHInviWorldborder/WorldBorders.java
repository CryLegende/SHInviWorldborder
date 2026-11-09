package org.Cry_Legende.sHInviWorldborder;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bordures de monde invisibles. Le vrai réglage de chaque monde est gardé dans borderdata.yml
 * et le worldborder natif est poussé à la taille maximale pour que le client ne l'affiche plus.
 * Un changement fait avec /worldborder (ou par un plugin) devient le nouveau réglage invisible.
 */
final class WorldBorders {

    /** Taille maximale d'un worldborder vanilla : le mur est hors du monde, donc jamais affiché. */
    private static final double HIDDEN_SIZE = 5.9999968E7;
    /** Au-delà de cette taille, le monde est considéré sans bordure. */
    private static final double NO_LIMIT = 5.9E7;

    /** Réglage réel d'un worldborder, tel qu'enregistré dans borderdata.yml. */
    record Data(double centerX, double centerZ, double size, int warningDistance, int warningTime,
                double damageBuffer, double damageAmount) {

        Data withSize(double newSize) {
            return new Data(centerX, centerZ, newSize, warningDistance, warningTime, damageBuffer, damageAmount);
        }

        Data withCenter(double x, double z) {
            return new Data(x, z, size, warningDistance, warningTime, damageBuffer, damageAmount);
        }

        /** Le mur de la bordure, ou null si la taille revient à ne pas avoir de bordure. */
        Area area() {
            if (size >= NO_LIMIT) {
                return null;
            }
            double half = size / 2.0;
            return new Area(centerX - half, centerZ - half, centerX + half, centerZ + half);
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final Map<UUID, Data> borders = new ConcurrentHashMap<>();
    private final Map<UUID, Area> areas = new ConcurrentHashMap<>();
    /** Vrai pendant que le plugin modifie lui-même un worldborder natif (ses propres évènements sont ignorés). */
    private boolean selfChange;

    WorldBorders(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "borderdata.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    /** Le mur de la bordure du monde, ou null s'il n'en a pas. */
    Area area(World world) {
        return areas.get(world.getUID());
    }

    boolean isSelfChange() {
        return selfChange;
    }

    boolean isRegistered(World world) {
        return borders.containsKey(world.getUID());
    }

    /** Charge (ou relève sur le worldborder natif) le réglage du monde, puis masque le natif. */
    void register(World world) {
        if (borders.containsKey(world.getUID())) {
            return;
        }
        Data data = load(world.getName());
        if (data == null) {
            WorldBorder wb = world.getWorldBorder();
            data = new Data(wb.getCenter().getX(), wb.getCenter().getZ(), wb.getSize(), wb.getWarningDistance(),
                    wb.getWarningTimeTicks() / 20, wb.getDamageBuffer(), wb.getDamageAmount());
            save(world.getName(), data);
        }
        put(world, data);
        hide(world, data);
    }

    /** Remet le vrai réglage sur le worldborder natif et oublie le monde. */
    void unregister(World world) {
        Data data = borders.remove(world.getUID());
        areas.remove(world.getUID());
        if (data != null) {
            restore(world, data);
        }
    }

    void unregisterAll(Iterable<World> worlds) {
        for (World world : worlds) {
            unregister(world);
        }
    }

    /** Nouvelle taille demandée sur le worldborder natif : elle devient la taille de la bordure invisible. */
    void changeSize(World world, double size) {
        Data data = borders.get(world.getUID());
        if (data == null) {
            return;
        }
        update(world, data.withSize(Math.max(1.0, size)));
    }

    /** Nouveau centre demandé sur le worldborder natif : il devient le centre de la bordure invisible. */
    void changeCenter(World world, double x, double z) {
        Data data = borders.get(world.getUID());
        if (data == null) {
            return;
        }
        update(world, data.withCenter(x, z));
    }

    private void update(World world, Data data) {
        put(world, data);
        save(world.getName(), data);
        plugin.getLogger().info("Bordure invisible de " + world.getName() + " : " + data.size() + " blocs, centre "
                + data.centerX() + ", " + data.centerZ() + ".");
    }

    private void put(World world, Data data) {
        borders.put(world.getUID(), data);
        Area area = data.area();
        if (area == null) {
            areas.remove(world.getUID());
        } else {
            areas.put(world.getUID(), area);
        }
    }

    private void hide(World world, Data data) {
        WorldBorder wb = world.getWorldBorder();
        selfChange = true;
        try {
            Location center = wb.getCenter();
            if (center.getX() != data.centerX() || center.getZ() != data.centerZ()) {
                wb.setCenter(data.centerX(), data.centerZ());
            }
            if (wb.getSize() != HIDDEN_SIZE) {
                wb.setSize(HIDDEN_SIZE);
            }
            if (wb.getWarningDistance() != 0) {
                wb.setWarningDistance(0);
            }
            if (wb.getWarningTimeTicks() != 0) {
                wb.setWarningTimeTicks(0);
            }
        } finally {
            selfChange = false;
        }
    }

    private void restore(World world, Data data) {
        WorldBorder wb = world.getWorldBorder();
        selfChange = true;
        try {
            wb.setCenter(data.centerX(), data.centerZ());
            wb.setSize(Math.max(1.0, Math.min(data.size(), HIDDEN_SIZE)));
            wb.setWarningDistance(data.warningDistance());
            wb.setWarningTimeTicks(data.warningTime() * 20);
            wb.setDamageBuffer(data.damageBuffer());
            wb.setDamageAmount(data.damageAmount());
        } finally {
            selfChange = false;
        }
    }

    private Data load(String world) {
        if (!yaml.isConfigurationSection(world)) {
            return null;
        }
        return new Data(
                yaml.getDouble(world + ".center.x"),
                yaml.getDouble(world + ".center.z"),
                yaml.getDouble(world + ".size"),
                yaml.getInt(world + ".warningDistance"),
                yaml.getInt(world + ".warningTime"),
                yaml.getDouble(world + ".damageBuffer"),
                yaml.getDouble(world + ".damageAmount"));
    }

    private void save(String world, Data data) {
        yaml.set(world + ".center.x", data.centerX());
        yaml.set(world + ".center.y", 0.0);
        yaml.set(world + ".center.z", data.centerZ());
        yaml.set(world + ".center.world", world);
        yaml.set(world + ".size", data.size());
        yaml.set(world + ".warningDistance", data.warningDistance());
        yaml.set(world + ".warningTime", data.warningTime());
        yaml.set(world + ".damageBuffer", data.damageBuffer());
        yaml.set(world + ".damageAmount", data.damageAmount());
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossible d'enregistrer borderdata.yml : " + e.getMessage());
        }
    }
}
