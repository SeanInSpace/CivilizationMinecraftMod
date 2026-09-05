package com.kingdoms.sim.settlement;

import java.util.Locale;

/**
 * How far along the founding progression a settlement is.
 *
 * <p>The verdict that forced this: no realistic settlement starts by building a
 * government. The old catalog put the town hall at priority 100 above
 * everything, so four settlers' first act was civic architecture while they
 * slept in the open. A settlement now advances through stages — camp, then
 * food, then safety, then permanence — and the hall is the capstone of the last
 * one, built once there is a town worth governing. The full design is
 * {@code FOUNDING.md}.
 *
 * <p>Each stage carries its own build program and its own staffing rules (see
 * {@link StagePlanner}), and advancement is decided by conditions rather than
 * day counts — a stage that advances on a timer advances over a party that is
 * failing.
 */
public enum SettlementStage {

    /** Arrival. The claim is staked and the packs are grounded. */
    CAMP,

    /** Shelter and food: bunkhouse, hearth, the first farm. */
    HOMESTEAD,

    /** The perimeter goes up, the first sentry walks it, stores get a roof. */
    FORTIFIED,

    /** Families move into cottages, workshops open, the market trades. */
    VILLAGE,

    /** The hall at last — and everything the old catalog always wanted. */
    TOWN;

    /** The stage after this one, or this one at the end of the road. */
    public SettlementStage next() {
        int i = ordinal();
        SettlementStage[] all = values();
        return i + 1 < all.length ? all[i + 1] : this;
    }

    public boolean atLeast(SettlementStage other) {
        return ordinal() >= other.ordinal();
    }

    public boolean before(SettlementStage other) {
        return ordinal() < other.ordinal();
    }

    /** "fortified", for logs and reports. */
    public String pretty() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a saved stage, tolerating anything an older save might hold. */
    public static SettlementStage parse(String saved, SettlementStage fallback) {
        try {
            return valueOf(saved.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return fallback;
        }
    }
}
