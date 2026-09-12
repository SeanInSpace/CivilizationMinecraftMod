package com.civilization.neoforge.trade;

import com.civilization.neoforge.CivilizationItems;
import com.civilization.sim.economy.Valuation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The money: registered, drawn, named, and named in only one place.
 *
 * <p>Three ways a currency item is broken without anything failing to compile.
 * It can be missing from the registry; it can be in the registry with no item
 * definition, which is a magenta cube in a hotbar where a player's whole purse
 * should be; and it can have no translation, which is
 * {@code item.civilization.coin} written across the creative tab and every
 * message that says the money's name out loud.
 *
 * <p>The fourth is the one only this mod has, and the reason for the last test
 * below. The name is not settled — see {@code docs/CURRENCY.md} — and the whole
 * point of {@link Currency} is that renaming it is a change to a language file
 * and nothing else. That is only true for as long as nobody writes the word into
 * a message by hand, and a hand-written "coin" in one refusal string is
 * invisible until the day somebody renames the money and one sentence in the
 * game keeps saying the old word.
 */
class CurrencyTest {

    @Test
    @DisplayName("the money is a registered item in the mod's own namespace")
    void theMoneyIsARegisteredItemInTheModsOwnNamespace() {
        Identifier id = Identifier.fromNamespaceAndPath("civilization", Currency.ID);
        assertTrue(BuiltInRegistries.ITEM.getOptional(id).isPresent(),
                id + " is not in the item registry");
        assertSame(CivilizationItems.COIN.get(), Currency.item(),
                "Currency.item() is not the item that was registered");
        assertEquals("civilization:" + Currency.ID,
                BuiltInRegistries.ITEM.getKey(Currency.item()).toString());
    }

    // Nothing here asserts the stack size, though a sworn friend's delivery pays
    // a hundred and twenty and one-to-a-slot would be two rows of a player's
    // inventory for a single quest. It cannot be asked: getDefaultMaxStackSize
    // reads DataComponents.MAX_STACK_SIZE, and this harness never binds an
    // item's components -- see SettlerEggTest, which documents the same wall.
    // The properties say stacksTo(64) and a playtest is what confirms it.

    @Test
    @DisplayName("the money has a model, a picture and a name a player can read")
    void theMoneyHasAModelAPictureAndAName() throws IOException {
        String definition = resource("/assets/civilization/items/" + Currency.ID + ".json");
        assertTrue(definition.contains("civilization:item/" + Currency.ID),
                "the item definition points somewhere else: " + definition);
        String model = resource("/assets/civilization/models/item/" + Currency.ID + ".json");
        assertTrue(model.contains("minecraft:item/generated"),
                "the money is not a flat item model: " + model);
        assertTrue(model.contains("civilization:item/" + Currency.ID),
                "the model has no layer0 of its own: " + model);
        // Drawn by neoforge/tools/coin_art.py, which is committed beside it.
        assertEquals(16, iconWidth("/assets/civilization/textures/item/" + Currency.ID + ".png"),
                "the money is not a 16x16 icon");
    }

    /**
     * The lang key exists, and {@link Currency#name()} is what reads it.
     *
     * <p>The description id is derived by vanilla from the registry key at
     * construction, so this asserts the two rename points actually meet: the id
     * on one side, the key in {@code en_us.json} on the other, and nothing in
     * between for them to disagree through.
     */
    @Test
    @DisplayName("the one lang key is the one the money's name is read from")
    void theOneLangKeyIsTheOneTheNameIsReadFrom() throws IOException {
        String key = "item.civilization." + Currency.ID;
        assertEquals(key, Currency.item().getDescriptionId(),
                "the item's description id is not the key the lang file spells");
        String lang = resource("/assets/civilization/lang/en_us.json");
        assertTrue(lang.contains("\"" + key + "\""),
                key + " has no translation; every message naming the money "
                        + "would print the raw key");
    }

    /**
     * Money is not a find, so nobody hauls it in to sell it back.
     *
     * <p>{@code Valuation} prices what a person came across — a dropped sword, a
     * diamond out of a cellar — and a settler who could carry the town's own
     * money to the market and be paid for it would do nothing else. It falls out
     * of the default rather than a rule, which is exactly why it is worth a test:
     * one entry added to that table by hand would make the town buy its own
     * purse.
     */
    @Test
    @DisplayName("the money is worth nothing at the market, being the market's own medium")
    void theMoneyIsWorthNothingAtTheMarket() {
        assertEquals(Valuation.WORTHLESS,
                Valuation.priceOf("civilization:" + Currency.ID));
    }

    /**
     * <strong>The rename stays cheap.</strong>
     *
     * <p>{@link Currency#ID} is the only place in {@code :neoforge} Java that
     * spells the registry path, and every message that says the money's name
     * builds from {@link Currency#name()}. This walks the source tree to keep it
     * that way.
     *
     * <p>It looks for the path as a <em>quoted literal</em> rather than the bare
     * word, because the bare word is not available to look for: this codebase
     * has called the treasury's abstract unit "coin" since long before there was
     * an item, and {@code Reward.coin()}, {@code "Treasury 40 coin"} and the
     * {@code "coin"} codec field all say it about the integer rather than about
     * the item. Those are not the name of a thing anybody can hold and they do
     * not move when it is renamed. What would break a rename is a literal that
     * <em>is</em> the id — {@code "coin"} handed to a registry lookup, an
     * {@code Identifier}, or a resource path — and that is what this refuses.
     */
    @Test
    @DisplayName("nothing but Currency.ID spells the money's registry path")
    void nothingButCurrencyIdSpellsTheRegistryPath() throws IOException, URISyntaxException {
        String exact = "\"" + Currency.ID + "\"";
        String qualified = "\"civilization:" + Currency.ID + "\"";
        String asset = "\"civilization:item/" + Currency.ID + "\"";
        List<String> offenders = new ArrayList<>();
        for (Path source : javaSources()) {
            String text = Files.readString(source, StandardCharsets.UTF_8);
            String name = source.getFileName().toString();
            if (name.equals("Currency.java")) {
                continue;       // rename point 1, which is the point
            }
            // CivilizationCodecs names a codec field "coin" for the treasury
            // integer in a saved reward. That is a save key for a number, not
            // the item's id, and renaming the money must not silently rewrite
            // it -- every old save would lose its unclaimed rewards.
            if (name.equals("CivilizationCodecs.java")) {
                text = text.replace(exact, "");
            }
            if (text.contains(exact) || text.contains(qualified) || text.contains(asset)) {
                offenders.add(source.getFileName().toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "the money's registry path is written out in " + offenders
                        + "; it belongs in Currency.ID alone, so that renaming it "
                        + "stays the change docs/CURRENCY.md says it is");
    }

    /**
     * The old medium is gone from the boundary entirely.
     *
     * <p>Money used to be the vanilla emerald, and the replacement is only done
     * if no counter still reaches for one. A single missed {@code Items.EMERALD}
     * is a stall that pays in coin and charges in emeralds, or a storehouse that
     * silently ignores the money a player is holding — neither of which fails a
     * compile and both of which need a running game to notice.
     *
     * <p>Emeralds themselves are untouched: they are a gem a town will still buy
     * off you, and they are still what a Founding Charter is crafted from, which
     * is why this looks for the <em>item constant</em> in Java rather than for
     * the word.
     */
    @Test
    @DisplayName("no counter anywhere still reaches for a vanilla emerald")
    void noCounterStillReachesForAVanillaEmerald() throws IOException, URISyntaxException {
        List<String> offenders = new ArrayList<>();
        for (Path source : javaSources()) {
            if (Files.readString(source, StandardCharsets.UTF_8).contains("Items.EMERALD")) {
                offenders.add(source.getFileName().toString());
            }
        }
        assertTrue(offenders.isEmpty(),
                "emeralds are no longer the money, but " + offenders + " still handles them "
                        + "as it; every such site belongs on Currency");
    }

    /** Every {@code .java} file the mod ships, from the source tree on disk. */
    private static List<Path> javaSources() throws URISyntaxException, IOException {
        // Anchored on a shipped resource and walked back up to the module root,
        // because a test has no working directory it can rely on and FML's
        // classloader hands back nothing at all for the classpath root itself.
        URL anchor = CurrencyTest.class.getResource("/assets/civilization/lang/en_us.json");
        assertNotNull(anchor, "the mod's own lang file is not on the test classpath");
        assertEquals("file", anchor.getProtocol(),
                "resources are packaged rather than on disk; this test walks a directory");
        Path here = Path.of(anchor.toURI());
        Path module = here;
        while (module != null && !Files.isDirectory(module.resolve("src/main/java"))) {
            module = module.getParent();
        }
        assertNotNull(module, "could not find the :neoforge module root from " + here);
        List<Path> sources;
        try (Stream<Path> tree = Files.walk(module.resolve("src/main/java"))) {
            sources = tree.filter(path -> path.toString().endsWith(".java")).toList();
        } catch (UncheckedIOException failed) {
            throw failed.getCause();
        }
        // A walk that finds nothing passes every check made of it, which is the
        // one way these tests could quietly stop being tests.
        assertTrue(sources.size() > 50,
                "only " + sources.size() + " source files found under " + module
                        + "; the walk is looking in the wrong place");
        return sources;
    }

    private static String resource(String classpath) throws IOException {
        try (InputStream in = CurrencyTest.class.getResourceAsStream(classpath)) {
            assertNotNull(in, "no such resource on the test classpath: " + classpath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static int iconWidth(String classpath) throws IOException {
        try (InputStream in = CurrencyTest.class.getResourceAsStream(classpath)) {
            assertNotNull(in, "no such resource on the test classpath: " + classpath);
            DataInputStream data = new DataInputStream(in);
            data.readFully(new byte[8]);        // signature
            data.readInt();                     // IHDR length
            assertEquals(0x49484452, data.readInt(), classpath + " does not start with IHDR");
            return data.readInt();
        }
    }
}
