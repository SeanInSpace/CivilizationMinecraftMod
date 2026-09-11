package com.kingdoms.sim.quest;

import com.kingdoms.sim.settlement.Settlement;
import com.kingdoms.sim.settlement.TownStores;

/**
 * What the notice on the board actually says.
 *
 * <p>Kept apart from {@link QuestPlanner} because they answer different
 * questions. The planner decides that the town is short of stone and will pay
 * forty coin for thirty-two of it; this decides that the notice reads
 * <em>"The works have stopped for want of stone"</em> rather than
 * <em>"DELIVER stone 32"</em>. A board written by the first of those is a
 * spreadsheet with a frame round it.
 *
 * <p>Two rules hold everything here together. Every line is in the town's own
 * voice — "we", "our", the thing they are actually worried about — and no line
 * ever carries a number the row beside it is already showing. The amount, the
 * progress and the reward are columns; the words are why anybody is being
 * asked.
 */
final class QuestVoice {

    private QuestVoice() {
    }

    /** The heading on the notice: four or five words, said like a person. */
    static String title(QuestKind kind, String target, Settlement town) {
        return switch (kind) {
            case DELIVER -> switch (target) {
                case TownStores.FOOD -> town.isStarving()
                        ? "The granary is bare" : "Bread for the winter";
                case TownStores.WOOD -> "Timber for the yard";
                case TownStores.STONE -> "Stone for the works";
                case TownStores.IRON -> "Iron for the forge";
                case TownStores.SAPLINGS -> "Something to replant";
                default -> "Goods for the storehouse";
            };
            case SLAY -> "Thin them out";
            case CLEAR -> "Take the place back";
            case VISIT -> visitTitle(target);
            case UNKNOWN -> "A notice nobody here can read";
        };
    }

    private static String visitTitle(String target) {
        return switch (target) {
            case "mine" -> "The mine is cut out";
            case "stand" -> "The wood is gone";
            case "door" -> "No way to the door";
            default -> "Come and see for yourself";
        };
    }

    /** The sentence under it: what is wrong, and what would fix it. */
    static String detail(QuestKind kind, String target, Settlement town) {
        return switch (kind) {
            case DELIVER -> deliverDetail(target, town);
            case SLAY -> "There is too much abroad after dark for the watch alone. "
                    + "Put some of it down inside our bounds.";
            case CLEAR -> "Something has moved into the wreck and our people will not "
                    + "work near it. Clear them out and we will go back.";
            case VISIT -> visitDetail(target);
            case UNKNOWN -> "The hand is not one of ours.";
        };
    }

    private static String deliverDetail(String target, Settlement town) {
        return switch (target) {
            case TownStores.FOOD -> town.isStarving()
                    ? "We are eating the seed corn. Anything you can carry to the "
                            + "storehouse keeps somebody alive."
                    : "The larder will not see us through. Bring bread to the "
                            + "storehouse and we will not forget it.";
            case TownStores.WOOD -> "Every job on the books is waiting on logs. "
                    + "Leave them at the storehouse.";
            case TownStores.STONE -> "The walls want facing and the quarry is slow. "
                    + "Cobble at the storehouse, and thank you.";
            case TownStores.IRON -> "The smith has a rack of handles and nothing to "
                    + "put on them. Iron at the storehouse.";
            case TownStores.SAPLINGS -> "We have felled faster than we planted. "
                    + "Saplings at the storehouse, before the ground goes to grass.";
            default -> "Bring it to the storehouse and we will take it gladly.";
        };
    }

    private static String visitDetail(String target) {
        return switch (target) {
            case "mine" -> "The seam is finished and nobody wants to say so. "
                    + "Walk down and look at it, and we will believe you.";
            case "stand" -> "Our cutting ground is bare to the dirt. "
                    + "Go and stand in it before we send anybody else out there.";
            case "door" -> "There is a building nobody has laid a path to. "
                    + "Find it, and we will know where to send the road crew.";
            default -> "Go out to the mark and have a look. "
                    + "We would rather a stranger's eyes than our own.";
        };
    }
}
