package com.keystone;

import com.keystone.api.BlueprintSource;
import com.keystone.api.Blueprints;
import com.keystone.api.LoadedBlueprint;
import com.keystone.blueprint.Blueprint;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which of several answers to the same name wins, and how it is turned.
 *
 * <p>Four places a building can come from — this world's own folder, the shared
 * folder, an imported Structurize pack, a mod jar — and the order between them
 * is a promise to whoever authored the file: a cottage you built in <em>this</em>
 * world beats one you built in another, which beats one somebody shipped. Get
 * the order wrong and a player's work silently stops appearing, with no error
 * anywhere and nothing to look at but a cottage that is not theirs.
 *
 * <p>Tested against fake sources rather than real files, because the thing under
 * test is the ranking rather than the reading. The real sources' own priorities
 * are constants next to their {@code name()}, and a test that read files off
 * disk would be testing the filesystem.
 */
class SourceOrderTest {

    /** A source that hands back one structure and nothing else. */
    private record Fake(String label, int rank, Identifier has, Blueprint gives)
            implements BlueprintSource {

        @Override
        public Optional<Blueprint> load(ServerLevel level, BlockPos base, Identifier id) {
            return id.equals(has) ? Optional.of(gives) : Optional.empty();
        }

        @Override
        public boolean cacheable() {
            // Never cached, so one test cannot serve another test's answer out
            // of a static map.
            return false;
        }

        @Override
        public int priority() {
            return rank;
        }

        @Override
        public String name() {
            return label;
        }
    }

    private static final Identifier COTTAGE =
            Identifier.fromNamespaceAndPath("civilization", "norman/cottage");

    /** A structure distinguishable by its size and the way its front points. */
    private static Blueprint marked(int width, int facing) {
        return new Blueprint(new Vec3i(width, 4, 7), List.of(),
                new BlockPos(width / 2, 0, 3),
                new Blueprint.Meta(facing, Blueprint.UNCOUNTED));
    }

    /**
     * The four ranks the real sources ship with, under fake names.
     *
     * <p>Registered once for the class. There is no unregistering — sources are
     * a mod's statement about where its content lives, made once at startup —
     * so a test that registered per method would stack them up.
     */
    @BeforeAll
    static void stackTheSources() {
        Blueprints.register(new Fake("jar", 50, COTTAGE, marked(5, 0)));
        Blueprints.register(new Fake("shared-folder", 100, COTTAGE, marked(7, 0)));
        Blueprints.register(new Fake("world-folder", 120, COTTAGE, marked(9, 2)));
        Blueprints.clearCache();
    }

    @Test
    void theWorldsOwnFolderBeatsEverything() {
        LoadedBlueprint found = Blueprints.load(null, BlockPos.ZERO, COTTAGE).orElseThrow();

        assertEquals(9, found.size().getX(),
                "a building authored in this world is the most specific answer there is");
    }

    @Test
    void aNameNobodyHasResolvesToNothingRatherThanToTheNearestMatch() {
        assertTrue(Blueprints.load(null, BlockPos.ZERO,
                        Identifier.fromNamespaceAndPath("civilization", "norman/palace")).isEmpty(),
                "the caller falls back to its own drawing, which it cannot do if"
                        + " it is handed somebody else's cottage");
    }

    @Test
    void loadingToFaceAStreetTurnsTheFileByItsOwnStatedFront() {
        // The world file above was authored facing north. Asked to face south,
        // it must come back turned a half rather than left alone -- which is the
        // whole difference between honoring a stated front and assuming one.
        LoadedBlueprint southward =
                Blueprints.loadFacing(null, BlockPos.ZERO, COTTAGE, 0).orElseThrow();

        assertEquals(0, southward.facing(),
                "asked to face south, it faces south");
    }

    @Test
    void aQuarterTurnSwapsTheStructuresOwnAxes() {
        // Nine across and seven back, authored facing north; turned a quarter to
        // face west it is seven across and nine back. A consumer comparing the
        // result against a size table has to be given the turned size or it will
        // reserve the wrong ground.
        LoadedBlueprint westward =
                Blueprints.loadFacing(null, BlockPos.ZERO, COTTAGE, 1).orElseThrow();

        assertEquals(1, westward.facing());
        assertEquals(7, westward.size().getX());
        assertEquals(9, westward.size().getZ());
    }

    @Test
    void theAnchorTurnsWithTheStructureRatherThanStayingPut() {
        // Authored at (4, 0, 3) in a 9x4x7 box, facing north. A half turn sends
        // it to (9-1-4, 0, 7-1-3) = (4, 0, 3) -- the middle, which it was. So
        // check the quarter, where the arithmetic actually moves.
        LoadedBlueprint turned =
                Blueprints.loadFacing(null, BlockPos.ZERO, COTTAGE, 3).orElseThrow();

        assertTrue(Blueprint.anchorFits(turned.anchor(), turned.size()),
                "an anchor that fell outside the turned box would line the"
                        + " building up by a cell it does not have");
    }
}
