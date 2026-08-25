package com.kingdoms.sim.settlement;

import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which farmer works which field.
 *
 * <p>Nobody decided this before. Every farmer was sent to whichever farm was
 * nearest to where they happened to be standing, and a farmer standing on a
 * farm is, necessarily, nearest to that one — so the first field anybody
 * reached became the only field they ever worked. A town with several farms
 * staffed one of them.
 *
 * <p>What that looks like in a log: a field frozen at the same planting for as
 * long as anybody watches. One measured run held {@code 72 farmland, 26
 * planted} across sixteen sweeps and seven minutes, in a fully watched town of
 * forty-eight with thirty-five people embodied, while other fields in the same
 * town rose and fell. Nothing was eating those crops — nothing was planting
 * them, because no farmer had any reason to walk over there, and the worker
 * gives up on any field more than thirty-two blocks off.
 *
 * <p>The rule here is the dullest one that fixes it: sort the fields, sort the
 * farmers, and deal them out. It wants three properties and has them all.
 *
 * <ul>
 *   <li><strong>Stable.</strong> The same farmer keeps the same field across
 *       steps, so nobody walks back and forth between two of them getting
 *       nothing done.</li>
 *   <li><strong>Total.</strong> Every field is dealt to somebody before anybody
 *       gets a second, so no field is left standing empty while two farmers
 *       share one.</li>
 *   <li><strong>Deterministic.</strong> Both orderings are by id, so a reload
 *       does not reshuffle the whole workforce.</li>
 * </ul>
 */
public final class FieldRoster {

    private FieldRoster() {
    }

    /**
     * The field this person is responsible for, or null if the town has none.
     *
     * <p>Returns null for anybody who is not a farmer: the roster is about who
     * works which field, and a smith does not appear on it.
     */
    public static Building fieldFor(Settlement settlement, Person person) {
        if (person == null || settlement.laboursAs(person, Profession.FARMER) == false) {
            return null;
        }
        List<Building> fields = fields(settlement);
        if (fields.isEmpty()) {
            return null;
        }
        int place = placeInRoster(settlement, person);
        if (place < 0) {
            return fields.getFirst();
        }
        return fields.get(place % fields.size());
    }

    /**
     * Every crop field the town has, in a fixed order.
     *
     * <p>By role rather than by name: {@code contains("farm")} also matches the
     * animal farm, which is a pen full of livestock and not a thing anybody
     * plants wheat in.
     */
    public static List<Building> fields(Settlement settlement) {
        List<Building> fields = new ArrayList<>();
        for (Building building : settlement.buildings()) {
            if (building.role() == BuildingRole.CROP_FARM && building.isMaterialized()) {
                fields.add(building);
            }
        }
        fields.sort(Comparator.comparingInt((Building b) -> b.origin().x())
                .thenComparingInt(b -> b.origin().z()));
        return fields;
    }

    /**
     * Where this farmer comes in the town's list of farmers.
     *
     * <p>Ordered by id, which is arbitrary but fixed — the point is only that
     * two farmers never believe they are the same one, and that the answer
     * survives a reload.
     */
    private static int placeInRoster(Settlement settlement, Person person) {
        List<Person> farmers = new ArrayList<>();
        for (Person resident : settlement.residents()) {
            if (settlement.laboursAs(resident, Profession.FARMER)) {
                farmers.add(resident);
            }
        }
        farmers.sort(Comparator.comparing(p -> p.id().value()));
        for (int i = 0; i < farmers.size(); i++) {
            if (farmers.get(i).id().equals(person.id())) {
                return i;
            }
        }
        return -1;
    }
}
