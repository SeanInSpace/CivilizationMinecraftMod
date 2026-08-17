package com.kingdoms.sim.world;

import com.kingdoms.sim.geom.SimPos;

import java.util.List;

/**
 * Deterministic settlement names. The same spot always founds a town of the same
 * name — no randomness, like everything else in the sim.
 */
public final class SettlementNames {

    private static final List<String> PREFIXES = List.of(
            "Oak", "High", "Stone", "Green", "Wolf", "Ash", "Fair", "Mill",
            "Raven", "Winter", "Elder", "Bright", "Thorn", "Amber", "Cold", "Iron");

    private static final List<String> SUFFIXES = List.of(
            "stead", "field", "brook", "haven", "watch", "ford", "dale", "gate",
            "fall", "march", "holm", "wick", "bury", "moor", "crest", "hollow");

    private SettlementNames() {
    }

    public static String forPosition(SimPos pos) {
        long h = mix(pos.x() * 341873128712L + pos.z() * 132897987541L);
        String prefix = PREFIXES.get((int) Math.floorMod(h, PREFIXES.size()));
        String suffix = SUFFIXES.get((int) Math.floorMod(h >>> 16, SUFFIXES.size()));
        return prefix + suffix;
    }

    private static long mix(long x) {
        x ^= x >>> 27;
        x *= 0xBF58476D1CE4E5B9L;
        x ^= x >>> 31;
        return x;
    }
}
