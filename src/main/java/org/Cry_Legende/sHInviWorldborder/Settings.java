package org.Cry_Legende.sHInviWorldborder;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.logging.Logger;

/**
 * Réglages lus dans config.yml.
 */
record Settings(int intervalTicks, Border worlds, Border islands) {

    /** Réglages d'un type de bordure (monde ou île). */
    record Border(boolean enabled, double margin, Particles particles) {
    }

    /** Mur de particules affiché près d'une bordure ; null quand il est désactivé. */
    record Particles(Particle.DustOptions dust, double distance,
                     int min, int max, double spread, double spreadY) {

        /** Nombre de particules envoyées pour un mur à « distance » blocs du joueur. */
        int countAt(double distance) {
            double closeness = 1.0 - Math.min(1.0, distance / this.distance);
            return (int) Math.round(min + (max - min) * closeness);
        }
    }

    static Settings load(FileConfiguration config, Logger logger) {
        int interval = Math.max(1, config.getInt("interval-ticks", 4));
        return new Settings(interval,
                border(config.getConfigurationSection("world-borders"), 0.0, logger),
                border(config.getConfigurationSection("islands"), 1.0, logger));
    }

    private static Border border(ConfigurationSection section, double defaultMargin, Logger logger) {
        if (section == null) {
            return new Border(false, defaultMargin, null);
        }
        ConfigurationSection p = section.getConfigurationSection("particles");
        Particles particles = null;
        if (p != null && p.getBoolean("enabled", true)) {
            int min = Math.max(0, p.getInt("min", 2));
            particles = new Particles(
                    new Particle.DustOptions(color(p.getString("color", "#FF3030"), logger), (float) p.getDouble("size", 0.9)),
                    Math.max(1.0, p.getDouble("distance", 8.0)),
                    min,
                    Math.max(min, p.getInt("max", 12)),
                    Math.max(0.0, p.getDouble("spread", 1.5)),
                    Math.max(0.0, p.getDouble("spread-y", 1.0)));
        }
        return new Border(section.getBoolean("enabled", true), Math.max(0.0, section.getDouble("margin", defaultMargin)), particles);
    }

    private static Color color(String hex, Logger logger) {
        try {
            return Color.fromRGB(Integer.parseInt(hex.replace("#", ""), 16));
        } catch (IllegalArgumentException e) {
            logger.warning("Couleur invalide « " + hex + " », rouge utilisé.");
            return Color.RED;
        }
    }
}
