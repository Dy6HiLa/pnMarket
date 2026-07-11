package ru.privatenull.compat;

import org.bukkit.Material;

public final class MaterialCompat {
    private MaterialCompat() {
    }

    public static Material first(String... names) {
        for (String name : names) {
            Material material = Material.matchMaterial(name);
            if (material != null) return material;
        }
        return Material.STONE;
    }
}
