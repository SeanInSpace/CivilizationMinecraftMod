package com.kingdoms.sim.settlement;

/**
 * How a crew divides between the places it works.
 *
 * <p>Unwatched production is worked out in aggregate — count the lumberjacks,
 * multiply by a rate — which was fine while its output went into one town-wide
 * figure. Once goods started landing where they were made it stopped being
 * fine: all the timber was credited to whichever camp happened to be listed
 * first, so a town with two camps piled everything at one of them and left the
 * other's shelves empty for good.
 */
public final class Workforce {

    private Workforce() {
    }

    /**
     * How many of a crew work the given site.
     *
     * <p>Evenly, with the remainder going to the earliest sites — five
     * lumberjacks across two camps is three and two, not two and two with one
     * of them idle. Every worker is always counted somewhere, which matters
     * because the total is what the rate was priced against.
     *
     * @param crew  how many are working at all
     * @param site  which site, counting from zero
     * @param sites how many sites there are; treated as one if fewer
     */
    public static int shareOf(int crew, int site, int sites) {
        int places = Math.max(1, sites);
        if (crew <= 0 || site < 0 || site >= places) {
            return 0;
        }
        return crew / places + (site < crew % places ? 1 : 0);
    }
}
