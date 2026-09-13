package com.civilization.sim.settlement;

import com.civilization.sim.geom.SimPos;
import com.civilization.sim.person.Household;
import com.civilization.sim.person.Person;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the beds are, and whose each one is.
 *
 * <p>One table, read by the two halves that have to agree about it — exactly the
 * arrangement {@link BuildingSizes} exists for, and for the same reason. The
 * placer lays a home's beds from this list, and the simulation asks this list
 * where a settler's bed is so it can send them to it. Neither measures the
 * other and neither scans the world: a body walks to a block it was told about
 * before the chunk was loaded.
 *
 * <p>Positions are <strong>unturned</strong>, in the same local frame every
 * drawing method in the placer works in: {@code dx} across, {@code dz} back,
 * about the building's origin, with the door in the {@code +z} wall. A building
 * is turned to face its street after it is drawn, so {@link #turned} applies the
 * same quarter turns the placer applies to the blocks — and applies them to the
 * head offset too, because a bed that swings round without its head swinging
 * with it is two blocks of furniture pointing at each other.
 *
 * <p>Every home holds exactly as many beds as the catalog says it has room for
 * heads. That is asserted rather than assumed; see {@code BedsTest}.
 */
public final class Beds {

    private Beds() {
    }

    /**
     * The course furniture stands on, one above the building's own base.
     *
     * <p>The floor is laid at the base and everything indoors sits on top of it,
     * which is what {@code base.offset(dx, 1, dz)} says in every drawing method
     * in the placer. A bed is furniture, so a bed is here.
     */
    public static final int FLOOR_COURSE = 1;

    /**
     * One bed: where its foot lies, and which way its head lies from there.
     *
     * <p>The foot is the anchor because the foot is where a sleeper stands to
     * get in, and because Minecraft says a bed's {@code FACING} points from foot
     * to head — so the head offset <em>is</em> the facing, said in a form the
     * simulation can hold without knowing what a {@code Direction} is.
     *
     * @param dx     the foot, across, from the building's origin
     * @param dz     the foot, back, from the building's origin
     * @param headDx which way the head lies, across: -1, 0 or 1
     * @param headDz which way the head lies, back: -1, 0 or 1
     */
    public record Slot(int dx, int dz, int headDx, int headDz) {

        public Slot {
            if (Math.abs(headDx) + Math.abs(headDz) != 1) {
                throw new IllegalArgumentException(
                        "a bed's head lies one block along one axis, not " + headDx
                                + "," + headDz);
            }
        }

        /** Where the head half lies, across. */
        public int headX() {
            return dx + headDx;
        }

        /** Where the head half lies, back. */
        public int headZ() {
            return dz + headDz;
        }
    }

    /** A bed whose head lies toward {@code -z}: against the cold wall. */
    private static Slot northward(int dx, int dz) {
        return new Slot(dx, dz, 0, -1);
    }

    /** A bed whose head lies toward {@code +z}: against the door wall. */
    private static Slot southward(int dx, int dz) {
        return new Slot(dx, dz, 0, 1);
    }

    /** A bed whose head lies toward {@code +x}. */
    private static Slot eastward(int dx, int dz) {
        return new Slot(dx, dz, 1, 0);
    }

    private static final Map<String, List<Slot>> DRAWN = drawn();

    /**
     * What each home lays down, head by head.
     *
     * <p>Read against {@link BuildingSizes} while reading these. Every cell here
     * is indoors — inside the wall ring, clear of the lantern the shape hangs at
     * the middle of its floor and clear of the post that names the building —
     * and every head half is laid against a wall wherever the room allows it,
     * because a bed with its pillow out in the room reads as furniture somebody
     * dropped rather than as somewhere a person sleeps.
     */
    private static Map<String, List<Slot>> drawn() {
        Map<String, List<Slot>> table = new LinkedHashMap<>();

        // Seven by seven, so five by five indoors, with the cottage post at
        // (0,-1) and a barrel at (-1,1). Two under the cold wall either side of
        // the post, and the third down the east side: three is what the catalog
        // says a cottage holds and a five-by-five room has no fourth wall to put
        // one against.
        table.put("cottage", List.of(
                northward(-1, -1),
                northward(1, -1),
                eastward(1, 1)));

        // Nine by nine, seven indoors, house post at (-1,-1). Four in a rank
        // along the cold wall with a bay between each, which is the same row a
        // longhouse has and the reason both read as dormitories from the door.
        table.put("house", List.of(
                northward(-3, -2),
                northward(-1, -2),
                northward(1, -2),
                northward(3, -2)));

        // Thirteen by nine: six down the cold wall, a bay apiece, three
        // households sharing one roof. These are the cells the wool bedrolls
        // used to occupy, so a longhouse standing in somebody's world gets beds
        // exactly where it had bedrolls.
        table.put("longhouse", List.of(
                northward(-5, -2),
                northward(-3, -2),
                northward(-1, -2),
                northward(1, -2),
                northward(3, -2),
                northward(5, -2)));

        // The L. Three up the wing and three along the range, so the household
        // is split between the two arms the way the building is. The wing beds
        // keep to {@code dx <= 0}, which is the half of the shape the notch does
        // not bite out of.
        table.put("croft", List.of(
                northward(-5, -3),
                northward(-3, -3),
                northward(-1, -3),
                southward(1, 3),
                southward(3, 3),
                southward(5, 3)));

        // Nine by seven, seven by five indoors, bunkhouse post at (0,-1). Six
        // shoulder to shoulder along the north wall with the post standing in
        // the middle of the rank. No bays: this is the one room the whole
        // founding party sleeps in, and a founding party does not have space to
        // spare.
        table.put("bunkhouse", List.of(
                northward(-3, -1),
                northward(-2, -1),
                northward(-1, -1),
                northward(1, -1),
                northward(2, -1),
                northward(3, -1)));

        // The orc hut. Seven across with the corners cut, so the room inside is
        // a five-wide cross rather than a square: dz = -2 and dz = 2 are only
        // three cells wide, and dx = 2 is only three deep. Two under the back
        // wall either side of the hut post at (0,-2), and the third down the east
        // side where the widest part of the round room is — three is what the
        // catalog says a hut holds, and a round room has no fourth straight
        // stretch of wall to lay one against.
        table.put("hut", List.of(
                northward(-1, -1),
                northward(1, -1),
                eastward(1, 1)));

        // The great hut: thirteen across, six beds, and the king in the first of
        // them. Four in a rank along the back with a bay apiece, exactly as a
        // house and a longhouse rank theirs, and two more down the door wall
        // either side of the fire — the two halves of a warband's chief's hall,
        // his own household at the back and his hearth-guard by the door.
        //
        // Kept to |dx| <= 3 on the back rank and |dz| <= 4 throughout, which is
        // well inside the octagon: the corners this shape cuts are at
        // |dx| + |dz| > 9.
        table.put("great_hut", List.of(
                northward(-3, -3),
                northward(-1, -3),
                northward(1, -3),
                northward(3, -3),
                southward(-3, 3),
                southward(3, 3)));

        // The goblin hovel. Five by five, so three by three indoors -- the
        // smallest room in the mod, and the reason every bed here runs north
        // rather than one of them running east as a cottage's third does: a
        // three-wide room has one spare cell after two beds, not a second wall.
        // The hovel post stands at (0,-1) and the lantern at the origin, which
        // leaves seven cells; three beds take six of them and a barrel the last.
        table.put("hovel", List.of(
                northward(-1, 0),
                northward(1, 0),
                eastward(0, 1)));

        // The tent. The same pad and half the beds, which is what a tent is: two
        // bedrolls under hide, with the pole at (0,-1) and room to sit up by the
        // door. Two rather than three because the third cell a hovel uses is
        // where a tent's guy ropes come down.
        table.put("tent", List.of(
                northward(-1, 0),
                northward(1, 0)));

        // The chieftain's hut: nine across, four beds, and the chief in the first
        // of them by {@code GoblinCamp}'s rule rather than by anything here. Four
        // in a rank along the back wall with a bay apiece, exactly as a house
        // ranks its four -- the chief, the shaman, and two of whoever is in
        // favour this week.
        table.put("chieftain_hut", List.of(
                northward(-3, -2),
                northward(-1, -2),
                northward(1, -2),
                northward(3, -2)));

        return Map.copyOf(table);
    }

    /** The building's own name, however it is addressed. */
    private static String pathOf(String blueprintId) {
        return blueprintId == null ? "" : BuildingRole.bareName(blueprintId);
    }

    /**
     * Every bed this kind of building lays, unturned, or an empty list.
     *
     * <p>Empty rather than null for anything that is not a home, because "how
     * many beds does a granary have" has an answer and the answer is none.
     */
    public static List<Slot> layoutOf(String blueprintId) {
        return DRAWN.getOrDefault(pathOf(blueprintId), List.of());
    }

    /** How many heads this kind of building has a bed for. */
    public static int countIn(String blueprintId) {
        return layoutOf(blueprintId).size();
    }

    /**
     * How many heads <em>this</em> building has a bed for.
     *
     * <p>The table is what a drawn building holds. An authored one holds the beds
     * that were actually found in its plan, which is the whole point of checking
     * the file: a cottage the validator passed has three, and a cottage somebody
     * placed without checking may have two — and a town that went on believing
     * the table would send a third settler to stand in the dark.
     */
    public static int countIn(Building building) {
        if (building == null) {
            return 0;
        }
        return building.isAuthored()
                ? building.authored().bedCount()
                : countIn(building.blueprintId());
    }

    /** Whether anybody sleeps in this kind of building at all. */
    public static boolean isHome(String blueprintId) {
        return !layoutOf(blueprintId).isEmpty();
    }

    /**
     * The same slot, on the building after it has been turned to face its street.
     *
     * <p>Must match {@code BlueprintPlacer.rotate} exactly, which sends
     * {@code (x, z)} to {@code (-z, x)} on a clockwise quarter. It is applied to
     * the head offset as well as to the foot, which is the whole of why this is
     * a function rather than two lines at the call site.
     */
    public static Slot turned(Slot slot, int facing) {
        return switch (Math.floorMod(facing, 4)) {
            case 1 -> new Slot(-slot.dz(), slot.dx(), -slot.headDz(), slot.headDx());
            case 2 -> new Slot(-slot.dx(), -slot.dz(), -slot.headDx(), -slot.headDz());
            case 3 -> new Slot(slot.dz(), -slot.dx(), slot.headDz(), -slot.headDx());
            default -> slot;
        };
    }

    /** Every bed this building holds, turned the way the building stands. */
    public static List<Slot> bedsOf(String blueprintId, int facing) {
        List<Slot> turned = new ArrayList<>();
        for (Slot slot : layoutOf(blueprintId)) {
            turned.add(turned(slot, facing));
        }
        return List.copyOf(turned);
    }

    /**
     * Where the foot half of one bed stands in the world, or null if this
     * building has no such bed.
     *
     * <p>Read from the file where there is one. An authored building's beds were
     * found in its plan when it was raised and written on the record; the table
     * below knows only where the <em>drawn</em> version of this kind puts them,
     * which for somebody else's cottage is an answer about a different cottage.
     */
    public static SimPos footOf(Building building, int index) {
        if (building == null) {
            return null;
        }
        if (building.isAuthored()) {
            return offset(building, building.authored().bedFeet(), index);
        }
        return footOf(building.blueprintId(), building.origin(), building.facing(), index);
    }

    /** Where the head half of that same bed stands. */
    public static SimPos headOf(Building building, int index) {
        if (building == null) {
            return null;
        }
        if (building.isAuthored()) {
            return offset(building, building.authored().bedHeads(), index);
        }
        return headOf(building.blueprintId(), building.origin(), building.facing(), index);
    }

    /**
     * One of an authored building's recorded cells, in the world.
     *
     * <p>Already turned: an authored plan is turned before it is laid, so what
     * was found in it is in the frame the building actually stands in. Turning it
     * again here is the mistake this method exists to not make.
     */
    private static SimPos offset(Building building, List<SimPos> cells, int index) {
        if (index < 0 || index >= cells.size()) {
            return null;
        }
        SimPos origin = building.origin();
        SimPos cell = cells.get(index);
        return new SimPos(origin.x() + cell.x(), origin.y() + cell.y(), origin.z() + cell.z());
    }

    /**
     * The same, for a kind of building rather than a particular one.
     *
     * <p>Reads the table and nothing else, which is what the placer's drawing
     * methods and their tests want: what does a cottage look like, rather than
     * what does this cottage hold.
     */
    public static SimPos footOf(String blueprintId, SimPos origin, int facing, int index) {
        List<Slot> slots = layoutOf(blueprintId);
        if (origin == null || index < 0 || index >= slots.size()) {
            return null;
        }
        Slot slot = turned(slots.get(index), facing);
        return new SimPos(origin.x() + slot.dx(), origin.y() + FLOOR_COURSE,
                origin.z() + slot.dz());
    }

    /** Where the head half of that same bed stands. */
    public static SimPos headOf(String blueprintId, SimPos origin, int facing, int index) {
        List<Slot> slots = layoutOf(blueprintId);
        if (origin == null || index < 0 || index >= slots.size()) {
            return null;
        }
        Slot slot = turned(slots.get(index), facing);
        return new SimPos(origin.x() + slot.headX(), origin.y() + FLOOR_COURSE,
                origin.z() + slot.headZ());
    }

    /** That settler's household home, or null if they are not housed. */
    private static SimPos homeOf(Settlement settlement, Person person) {
        for (Household household : settlement.households()) {
            if (household.isHoused() && household.members().contains(person.id())) {
                return household.home();
            }
        }
        return null;
    }

    /**
     * Everybody who sleeps under this roof, in the one order they will always be
     * in.
     *
     * <p>Sorted by identity rather than by whatever order the households happen
     * to be listed in. The identity of a person is written down and the order of
     * a list is not, so this is the same answer on the next load, in the next
     * session, and on a server that restored its households in a different
     * order — which is what lets the assignment below be recomputed rather than
     * stored. Nothing about beds goes into the save.
     *
     * <p>A roof may hold more than one household: a longhouse is three families
     * under one, and they are ranked together because they share the beds.
     */
    private static List<Person> sleepersIn(Settlement settlement, SimPos home) {
        List<Person> under = new ArrayList<>();
        for (Household household : settlement.households()) {
            if (!home.equals(household.home())) {
                continue;
            }
            for (Person.Id memberId : household.members()) {
                Person member = settlement.resident(memberId);
                if (member != null) {
                    under.add(member);
                }
            }
        }
        under.sort(Comparator.comparing(person -> person.id().value()));
        return under;
    }

    /**
     * Which bed in their own home is this settler's, or -1 if none is.
     *
     * <p>Minus one covers both ways of having no bed: living nowhere, and living
     * somewhere more crowded than it has beds for. A town whose housing has been
     * outgrown puts the extra heads on the floor rather than two to a mattress.
     */
    public static int indexOf(Settlement settlement, Person person) {
        SimPos home = homeOf(settlement, person);
        return home == null ? -1 : indexIn(settlement, home, person);
    }

    /** The same, once the caller has already found the roof. */
    private static int indexIn(Settlement settlement, SimPos home, Person person) {
        Building standing = settlement.buildingAt(home);
        if (standing == null) {
            return -1;
        }
        int beds = countIn(standing);
        int rank = 0;
        for (Person other : sleepersIn(settlement, home)) {
            if (other.id().equals(person.id())) {
                return rank < beds ? rank : -1;
            }
            rank++;
        }
        return -1;
    }

    /**
     * Where this settler's own bed is, or null if they have none.
     *
     * <p>The building is found through {@link Settlement#buildingAt}, not by
     * matching the household's address exactly: a family housed before their
     * cottage was drawn holds an estimated height, and the bed is in the
     * building rather than at the address.
     */
    public static SimPos bedFor(Settlement settlement, Person person) {
        SimPos home = homeOf(settlement, person);
        if (home == null) {
            return null;
        }
        Building standing = settlement.buildingAt(home);
        if (standing == null) {
            return null;
        }
        return footOf(standing, indexIn(settlement, home, person));
    }
}
