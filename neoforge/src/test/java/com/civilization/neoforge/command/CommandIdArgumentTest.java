package com.civilization.neoforge.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The commands that take an id can be given one.
 *
 * <p>{@code /civ culture civilization:orc/warhost} did not run. It did not fail
 * either — it never reached the command at all, because Brigadier stopped
 * reading at the colon and answered "Expected whitespace to end one argument"
 * with its cursor sitting in the middle of the id. An unquoted Brigadier string
 * is {@code [0-9A-Za-z_.+-]} and nothing else: no colon, no slash. Both
 * {@code string()} and {@code word()} read one of those, so both cut every id
 * this mod has in half, and the only way to type one was to know to quote it.
 *
 * <p>Which is a fault a person finds by typing a command in a world and nothing
 * else finds at all. Nothing checks an argument type at compile time, the
 * suggestion list cheerfully offers ids the argument cannot then accept, and a
 * test that calls the command's own method — as every other test of these
 * commands does — skips the parser entirely and passes.
 *
 * <p>So the parser is what is asserted on here, with the types the commands are
 * really registered with rather than a copy of them: the tree is built, the
 * argument node is found in it, and the id goes through whatever is standing
 * there.
 */
class CommandIdArgumentTest {

    /** A real culture id, colon and slash and all. */
    private static final String CULTURE = "civilization:orc/warhost";

    /** A real blueprint name, as {@code /civ blueprint list} prints it. */
    private static final String BLUEPRINT = "civilization:norman/house";

    @Test
    void theCultureCommandParsesACultureId() {
        CommandDispatcher<Object> mirror = new CommandDispatcher<>();
        mirror.register(LiteralArgumentBuilder.<Object>literal("civ")
                .then(LiteralArgumentBuilder.<Object>literal("culture")
                        .then(RequiredArgumentBuilder.argument("id", typeOf("culture", "id"))
                                .executes(ctx -> 1))));

        ParseResults<Object> parsed = mirror.parse("civ culture " + CULTURE, new Object());
        assertTrue(parsed.getExceptions().isEmpty(),
                "/civ culture " + CULTURE + " still does not parse: "
                        + parsed.getExceptions());
        assertEquals("", parsed.getReader().getRemaining(),
                "the parser stopped short of the end of the id");
        assertEquals(CULTURE, parsed.getContext().build(CULTURE)
                        .getArgument("id", String.class),
                "the command was handed something other than what was typed");
    }

    @Test
    void theCultureStillSuggestsTheIdsItAccepts() {
        // Swapping an argument type is the easiest possible way to drop the
        // suggestions that hung off the old one.
        assertNotNull(argumentNode(List.of("culture"), "id").getCustomSuggestions(),
                "/civ culture no longer suggests the cultures");
    }

    @Test
    void everyBlueprintVerbTakesANamespacedName() {
        for (String verb : List.of("scan", "check", "place")) {
            assertEquals(BLUEPRINT, readWhole(typeOf(List.of("blueprint", verb), "name"),
                            BLUEPRINT).toString(),
                    "/civ blueprint " + verb + " cannot be given a namespaced name");
        }
    }

    /**
     * {@code scan} stops at the end of the name, because it has corners after it.
     *
     * <p>This is why the three blueprint verbs are not all the same type. A
     * greedy argument would take "orc/great_hut 10 64 10 …" as the name and
     * leave the scan with nothing to scan.
     */
    @Test
    void scanStopsAtTheEndOfTheName() throws CommandSyntaxException {
        StringReader reader = new StringReader("orc/great_hut 10 64 10 20 70 20");
        Object name = typeOf(List.of("blueprint", "scan"), "name").parse(reader);
        assertEquals("minecraft:orc/great_hut", name.toString());
        assertEquals(" 10 64 10 20 70 20", reader.getRemaining(),
                "scan's name argument ate the corners after it");
    }

    /**
     * Why a bare {@code string()} could not do this, stated rather than assumed.
     *
     * <p>Both halves matter. {@code word()} looks like the obvious answer for an
     * id — one token, no spaces — and it is the same reader underneath, so it
     * stops on exactly the same character.
     */
    @Test
    void anUnquotedBrigadierStringStopsAtTheColon() throws CommandSyntaxException {
        StringReader quotable = new StringReader(CULTURE);
        assertEquals("civilization", StringArgumentType.string().parse(quotable));
        assertEquals(":orc/warhost", quotable.getRemaining(),
                "string() now reads past a colon; the fix may no longer be needed");

        StringReader word = new StringReader(CULTURE);
        assertEquals("civilization", StringArgumentType.word().parse(word));
        assertEquals(":orc/warhost", word.getRemaining(),
                "word() now reads past a colon; the fix may no longer be needed");
    }

    // --- the real tree ---------------------------------------------------------

    /** The argument node registered under {@code /civ <path...> <argument>}. */
    private static ArgumentCommandNode<CommandSourceStack, ?> argumentNode(
            List<String> path, String argument) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        CivilizationCommand.register(dispatcher);
        CommandNode<CommandSourceStack> at = dispatcher.getRoot().getChild("civ");
        assertNotNull(at, "/civ did not register");
        for (String step : path) {
            CommandNode<CommandSourceStack> next = at.getChild(step);
            assertNotNull(next, "/civ " + String.join(" ", path) + " has no " + step);
            at = next;
        }
        CommandNode<CommandSourceStack> node = at.getChild(argument);
        assertNotNull(node, "/civ " + String.join(" ", path) + " has no <" + argument + ">");
        return assertInstanceOf(ArgumentCommandNode.class, node);
    }

    @SuppressWarnings("unchecked")
    private static ArgumentType<Object> typeOf(List<String> path, String argument) {
        return (ArgumentType<Object>) argumentNode(path, argument).getType();
    }

    private static ArgumentType<Object> typeOf(String subcommand, String argument) {
        return typeOf(List.of(subcommand), argument);
    }

    /** Parses the whole of the input, or fails saying what was left over. */
    private static Object readWhole(ArgumentType<?> type, String input) {
        StringReader reader = new StringReader(input);
        Object value;
        try {
            value = type.parse(reader);
        } catch (CommandSyntaxException ex) {
            throw new AssertionError(input + " does not parse: " + ex.getMessage(), ex);
        }
        assertFalse(reader.canRead(),
                "the parser stopped at \"" + reader.getRemaining() + "\"");
        return value;
    }
}
