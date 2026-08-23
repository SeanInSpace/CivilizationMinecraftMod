package com.kingdoms.sim.settlement;

import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.person.Foods;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;

/**
 * What a charter actually creates.
 *
 * <p>Lives here rather than in the item because a founding party is a fact
 * about the simulation, not about right-clicking. It was written out longhand
 * inside {@code FoundingCharterItem}, which meant the one path a player can
 * actually take was also the one path no test could reach — and meant
 * {@code /civ found}, which every playtest and every scripted run goes through,
 * quietly created something else entirely: a settlement with a kit and nobody
 * to spend it. Towns founded that way sat at population zero forever, and the
 * scripts had to follow with {@code /civ populate} to paper over it.
 *
 * <p>Both now come through here, so a headless run founds the same party a
 * player's charter does.
 */
public final class Founding {

    /**
     * The claim a brand-new settlement lays around its centre.
     *
     * <p>The same for a charter, a console command and a daughter colony —
     * three places that had each written 64 down separately.
     */
    public static final int INITIAL_CLAIM = 64;

    private Founding() {
    }

    /**
     * A settlement as a charter makes one: a camp, a party, and rations.
     *
     * <p>Pioneers, all of them — generalists who build, farm and haul as the
     * camp needs, with professions crystallizing as the stages demand them. A
     * party split into half builders and half idlers is how one ended up with
     * idlers it could not turn into farmers.
     *
     * <p>The food is carried by the settlers rather than banked, because until
     * the first house stands there is no larder to fetch from: what they have
     * on them is what they live on. The timber and stone come from
     * {@link TownStores#founding}, laid on open ground until somebody raises a
     * store to put it in.
     */
    public static Settlement party(SimPos site, String name) {
        Settlement settlement = new Settlement(Settlement.Id.random(), name, site, INITIAL_CLAIM);
        // Fresh foundings live the ladder. Only loaded saves default to TOWN.
        settlement.setStage(SettlementStage.CAMP);
        for (int i = 0; i < TownStores.FOUNDING_SETTLERS; i++) {
            Person settler = new Person(Person.Id.random(), "Settler " + (i + 1),
                    Profession.PIONEER, site);
            settler.inventory().add(Foods.PROVISION, TownStores.FOUNDING_PROVISIONS_EACH);
            settlement.addResident(settler);
        }
        return settlement;
    }
}
