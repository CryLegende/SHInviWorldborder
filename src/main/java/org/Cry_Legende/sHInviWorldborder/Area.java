package org.Cry_Legende.sHInviWorldborder;

/**
 * Rectangle horizontal en coordonnées continues : x dans [minX, maxX], z dans [minZ, maxZ].
 */
record Area(double minX, double minZ, double maxX, double maxZ) {

    /** Agrandit le rectangle de « amount » blocs de chaque côté (rétrécit si négatif). */
    Area expand(double amount) {
        return new Area(minX - amount, minZ - amount, maxX + amount, maxZ + amount);
    }

    boolean contains(double x, double z) {
        return contains(x, z, 0.0);
    }

    /** Vrai si (x, z) est dans le rectangle, avec une tolérance de « tolerance » blocs. */
    boolean contains(double x, double z, double tolerance) {
        return x >= minX - tolerance && x <= maxX + tolerance && z >= minZ - tolerance && z <= maxZ + tolerance;
    }

    double clampX(double x) {
        return Math.max(minX, Math.min(maxX, x));
    }

    double clampZ(double z) {
        return Math.max(minZ, Math.min(maxZ, z));
    }

    /** Intersection de deux zones ; null est une zone sans limite. */
    static Area intersect(Area a, Area b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new Area(Math.max(a.minX, b.minX), Math.max(a.minZ, b.minZ),
                Math.min(a.maxX, b.maxX), Math.min(a.maxZ, b.maxZ));
    }
}
