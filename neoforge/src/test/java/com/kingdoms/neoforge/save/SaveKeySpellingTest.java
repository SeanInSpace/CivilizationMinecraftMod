package com.kingdoms.neoforge.save;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kingdoms.sim.geom.SimPos;
import com.kingdoms.sim.kingdom.Kingdom;
import com.kingdoms.sim.kingdom.Standing;
import com.kingdoms.sim.person.HaulTask;
import com.kingdoms.sim.person.Household;
import com.kingdoms.sim.person.Person;
import com.kingdoms.sim.person.Profession;
import com.kingdoms.sim.settlement.BuildTask;
import com.kingdoms.sim.settlement.Building;
import com.kingdoms.sim.settlement.Footprint;
import com.kingdoms.sim.settlement.PathNetwork;
import com.kingdoms.sim.settlement.Perimeter;
import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;
import com.kingdoms.sim.settlement.WorkArea;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every name a save file carries is spelled American.
 *
 * <p>The spelling sweep converted the whole tree and then had to leave four keys
 * behind — {@code centre} three times and the value {@code armour} — because
 * renaming a save key was a codec migration at the time and nobody was willing to
 * pay for one. The migration debt is gone: old worlds do not load, so a key is
 * free to be spelled correctly. This is what keeps it that way.
 *
 * <p>It works by writing one deliberately crowded kingdom, walking the JSON, and
 * looking at every field name in it. That is the whole point of doing it this way
 * rather than grepping the codec source: a key added to a sub-record three levels
 * down, or a resource id that reaches the file as a map key without ever being
 * written as a {@code fieldOf}, is caught the same as one typed into
 * {@link KingdomsCodecs} by hand.
 *
 * <p>If this fails, do not add the word to the list below. Rename the key.
 */
class SaveKeySpellingTest {

    /**
     * British forms that could plausibly reach a save key, from the appendix of
     * {@code docs/american-spelling-audit.md}. Matched as substrings and case
     * insensitively, because a key may be {@code sound_census} today and
     * {@code soundCensus} in whatever is added next.
     */
    private static final List<String> BRITISH = List.of(
            "centre", "armour", "colour", "behaviour", "honour", "favour", "harbour",
            "neighbour", "labour", "rumour", "endeavour", "defence", "offence",
            "pretence", "licence", "practise", "kerb", "catalogue", "dialogue",
            "programme", "storey", "levelled", "levelling", "travelled", "cancelled",
            "modelling", "labelled", "fuelled", "recognise", "organise", "realise",
            "analyse", "apologise", "summarise", "specialise", "metre", "litre",
            "mould", "plough", "tyre", "grey", "aluminium", "jewellery", "sceptic",
            "cheque", "draught", "gaol");

    /**
     * Names the crowded sample is expected to have reached, so that a scan which
     * quietly walked nothing cannot pass. Two are the point of the exercise:
     * {@code center} on a settlement and {@code armor} in a store.
     */
    private static final List<String> EXPECTED = List.of(
            "center", "armor", "charter", "layout", "holdings", "treasury", "defense",
            "perimeter", "retired", "staked_on", "pulled", "works", "build_queue",
            "buildings", "next_plot", "plot", "footprint", "condition", "ledgers",
            "stand", "seam", "ripe", "paths", "segments", "width", "lumber_area",
            "mine_area", "haul", "pockets", "households", "events", "seeded",
            "seeded_roads_owed", "dig_done", "laid_through", "streets_laid_for");

    @Test
    void noSaveKeyIsSpelledBritish() {
        TreeSet<String> keys = new TreeSet<>();
        collectKeys(encodedSample(), keys);
        collectKeys(encodedLedger(), keys);
        collectKeys(encodedRoot(), keys);

        for (String key : keys) {
            String lowered = key.toLowerCase(java.util.Locale.ROOT);
            for (String british : BRITISH) {
                assertTrue(!lowered.contains(british),
                        "the save key \"" + key + "\" is spelled British (\"" + british
                                + "\"). Rename the key — nothing migrates, so it costs"
                                + " nothing but this line.");
            }
        }
    }

    @Test
    void theSampleActuallyReachesTheKeysWorthChecking() {
        TreeSet<String> keys = new TreeSet<>();
        collectKeys(encodedSample(), keys);
        collectKeys(encodedLedger(), keys);
        collectKeys(encodedRoot(), keys);

        for (String expected : EXPECTED) {
            assertTrue(keys.contains(expected),
                    "the sample never wrote \"" + expected + "\", so the spelling scan"
                            + " above is not looking at as much as it claims to be."
                            + " Written: " + keys);
        }
        assertEquals(1, encodedSample().getAsJsonObject()
                .getAsJsonArray("settlements").size());
    }

    /** Every field name anywhere in the tree, keys of maps included. */
    private static void collectKeys(JsonElement element, TreeSet<String> into) {
        if (element instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                into.add(entry.getKey());
                collectKeys(entry.getValue(), into);
            }
        } else if (element instanceof JsonArray array) {
            array.forEach(child -> collectKeys(child, into));
        }
    }

    private static JsonElement encodedSample() {
        return KingdomsCodecs.KINGDOM.encodeStart(JsonOps.INSTANCE, crowdedKingdom())
                .result().orElseThrow();
    }

    private static JsonElement encodedLedger() {
        SiteLedger ledger = new SiteLedger();
        ledger.accept(3, -7, new SimPos(1898, 71, -3315));
        ledger.reject(-2, 5);
        return SiteLedger.CODEC.encodeStart(JsonOps.INSTANCE, ledger).result().orElseThrow();
    }

    private static JsonElement encodedRoot() {
        return KingdomsSavedData.CODEC.encodeStart(JsonOps.INSTANCE, new KingdomsSavedData())
                .result().orElseThrow();
    }

    /**
     * A kingdom with one of everything the codecs can write: a walled town that
     * has moved its wall, a road network part laid, a resident mid-errand with
     * full pockets, a household with a home, a queued repair, a store holding
     * goods, and a working claim outside the wall.
     */
    private static Kingdom crowdedKingdom() {
        SimPos center = new SimPos(512, 72, -512);
        Settlement town = new Settlement(Settlement.Id.random(), "Stonebridge", center, 256);

        town.setStock(TownStores.FOOD, 120);
        town.setStock(TownStores.WOOD, 64);
        town.setStock(TownStores.STONE, 32);
        town.setStock(TownStores.IRON, 8);
        town.setStock(TownStores.TOOLS, 4);
        town.setStock(TownStores.WEAPONS, 3);
        town.setStock(TownStores.ARMOR, 2);
        town.setStock(TownStores.GRAIN, 16);
        town.setStock(TownStores.SAPLINGS, 9);
        town.setStock(TownStores.EARTH, 40);
        town.setTreasury(77);
        town.tallies().record("built", 4);
        town.setFedStreak(12);

        Person miner = new Person(Person.Id.random(), "Alden", Profession.MINER,
                new SimPos(520, 72, -500));
        miner.setHunger(3);
        miner.inventory().restore("minecraft:stone", 12);
        miner.setHasTool(true);
        miner.setCarry(TownStores.STONE, 6);
        miner.pockets().restore(Map.of(TownStores.STONE, 6));
        miner.setHaul(new HaulTask(TownStores.ARMOR, HaulTask.Store.GRANARY,
                new SimPos(512, 72, -512), HaulTask.Store.GRANARY,
                new SimPos(500, 72, -520), 2));
        town.addResident(miner);

        Household household = new Household(Household.Id.random(), "Aldenson");
        household.addMember(miner.id());
        household.setHome(new SimPos(520, 72, -500));
        household.addGrowthProgress(5);
        household.setPantry(7);
        town.addHousehold(household);

        Building smithy = new Building("kingdoms:smithy", new SimPos(524, 72, -508), 30, true);
        smithy.setSurveyed(true);
        smithy.setFootprint(new Footprint(72, 7, 5, 4));
        smithy.setFacing(2);
        smithy.setDamage(3);
        smithy.setSoundCensus(180);
        smithy.stores().restore(Map.of(TownStores.ARMOR, 2, TownStores.WEAPONS, 1));
        town.addBuilding(smithy);

        Building camp = new Building("kingdoms:lumber_camp", new SimPos(480, 72, -540), 40, true);
        camp.setSeeded(true);
        camp.setStandThousandths(31_500);
        camp.setGrowingThousandths(4_000);
        town.addBuilding(camp);

        BuildTask repair = new BuildTask("kingdoms:cottage", new SimPos(528, 72, -516), 40);
        repair.setUpgradeOf(new SimPos(528, 72, -516));
        repair.setRepair(true);
        repair.setDigDone(6);
        repair.setSitePrepared(true);
        town.enqueueUrgent(repair);

        town.setPerimeter(new Perimeter(box(center, 60), List.of(new SimPos(572, 72, -512)),
                40, List.of(new Perimeter.Retired(box(center, 30), 240)), 900L));
        town.setPerimeterClosed(true);

        PathNetwork roads = new PathNetwork(
                List.of(new PathNetwork.Segment(center, new SimPos(524, 72, -508), 3)),
                List.of(new SimPos(524, 72, -508)));
        roads.restoreOpened(List.of(0));
        roads.setStreetsLaidFor(2);
        roads.restoreStreets(List.of(0), List.of(1));
        roads.setLaidThrough(1);
        town.setPaths(roads);

        town.setLumberArea(new WorkArea(new SimPos(480, 72, -540), 48));
        town.setMineArea(new WorkArea(new SimPos(560, 60, -560), 32));
        town.logEvent(12L, "the wall was staked wider");

        Kingdom kingdom = new Kingdom(Kingdom.Id.random(), "Stonemarch", town.cultureId());
        kingdom.restoreSettlement(town);
        kingdom.setStandingWith(Kingdom.Id.random(), Standing.NEUTRAL);
        return kingdom;
    }

    private static List<SimPos> box(SimPos center, int half) {
        return List.of(new SimPos(center.x() - half, 72, center.z() - half),
                new SimPos(center.x() + half, 72, center.z() - half),
                new SimPos(center.x() + half, 72, center.z() + half),
                new SimPos(center.x() - half, 72, center.z() + half));
    }
}
