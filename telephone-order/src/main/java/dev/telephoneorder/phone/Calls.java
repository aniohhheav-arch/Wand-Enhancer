package dev.telephoneorder.phone;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Branching phone-call scripts. A node shows lines and offers choices; choosing runs an action then moves to {@code next}.
 * Actions (semicolon separated): unlock:KEY pay:N credits:N goto:TAB hangup ring:SCRIPT flag:NAME event:NAME
 */
public final class Calls {
    public record Choice(String label, String next, String action) {}
    public record Node(String speaker, String style, List<String> lines, List<Choice> choices) {}

    public static final Map<String, Node> NODES = new HashMap<>();

    private Calls() {}

    private static Choice c(String label, String next) { return new Choice(label, next, ""); }
    private static Choice c(String label, String next, String action) { return new Choice(label, next, action); }

    private static void n(String id, String speaker, String style, List<String> lines, Choice... choices) {
        NODES.put(id, new Node(speaker, style, List.copyOf(lines), List.of(choices)));
    }

    static {
        // ---------------------------------------------------------------- customer service
        n("welcome", "T.O. AUTOMATED ASSISTANT", "normal", List.of(
                "Thank you for calling TELEPHONE ORDER, a subsidiary of itself.",
                "Your call is important to us. Your payment is more important.",
                "Please select an option."),
                c("Browse the catalogue", "welcome", "goto:catalog"), c("Track my deliveries", "welcome", "goto:orders"),
                c("Hear today's special", "special"), c("Hang up", "bye", "hangup"));
        n("special", "T.O. AUTOMATED ASSISTANT", "normal", List.of(
                "Today's special: every weapon is, technically, a special.",
                "Please note: Telephone Order is not liable for deliveries that land on you, near you, or in a general sense because of you.",
                "A friendly reminder that some numbers are not listed in any directory. Some of them answer."),
                c("Back", "welcome"), c("Hang up", "bye", "hangup"));
        n("bye", "T.O. AUTOMATED ASSISTANT", "normal", List.of("Goodbye. Please hold while we invoice your goodbye."));

        // ---------------------------------------------------------------- 555-0113 prototype lab
        n("proto_denied", "PROTOTYPE LAB", "normal", List.of(
                "CLEARANCE CHECK ........ FAILED",
                "Your account has not spent enough with us to be trusted with sharp ideas.",
                "Come back with at least 1000 credits of loyalty."),
                c("Hang up", "bye", "hangup"));
        n("proto", "PROTOTYPE LAB", "normal", List.of(
                "CLEARANCE CHECK ........ PASSED",
                "Welcome, valued test subject. We have a prototype that nobody has finished testing.",
                "It is unstable. We mean that technically and emotionally."),
                c("Read the liability waiver", "waiver"), c("Accept the liability waiver", "proto_ok", "unlock:proto_lab"), c("Hang up", "bye", "hangup"));
        n("waiver", "PROTOTYPE LAB", "normal", List.of(
                "WAIVER 14(b): The user agrees to be hit by their own weapon.",
                "WAIVER 14(c): The user agrees that 14(b) is a feature.",
                "WAIVER 14(d): [REDACTED]"),
                c("Accept the liability waiver", "proto_ok", "unlock:proto_lab"), c("Hang up", "bye", "hangup"));
        n("proto_ok", "PROTOTYPE LAB", "normal", List.of(
                "Signed. The Prototype X-13 has been added to your CLASSIFIED listings.",
                "Please do not test it near anything you love."),
                c("Open the catalogue", "bye", "goto:catalog"), c("Hang up", "bye", "hangup"));

        // ---------------------------------------------------------------- dimension lines
        n("line_overworld", "SURFACE DIVISION", "normal", List.of(
                "SOLAR UPLINK ........ ONLINE",
                "Daylight confirmed. The Surface Division is authorised to release the Daybreak Lance.",
                "It hits harder in sunlight. We recommend sunlight."),
                c("Open the catalogue", "bye", "unlock:line_overworld;goto:catalog"), c("Hang up", "bye", "unlock:line_overworld;hangup"));
        n("line_overworld_dark", "SURFACE DIVISION", "normal", List.of(
                "SOLAR UPLINK ........ OFFLINE",
                "It is dark where you are. The array does not work in the dark, and neither do we.",
                "Call back in daylight, from the open sky."),
                c("Hang up", "bye", "hangup"));
        n("line_nether", "BRIMSTONE DESK", "normal", List.of(
                "You have reached the Brimstone Desk. Your call is being routed through something very hot.",
                "Ember Judgment is yours for the asking. It sets fire to things. Mostly the things you shoot.",
                "Do not pay with any currency that can be burned."),
                c("Open the catalogue", "bye", "unlock:line_nether;goto:catalog"), c("Hang up", "bye", "unlock:line_nether;hangup"));
        n("line_end", "THE QUIET OFFICE", "ominous", List.of(
                "...",
                "you are very far from home",
                "we kept a rifle for someone like you. It goes through walls. It goes through most things.",
                "the Void Whisper is waiting"),
                c("Open the catalogue", "bye", "unlock:line_end;goto:catalog"), c("Hang up", "bye", "unlock:line_end;hangup"));
        n("wrong_dim", "AUTOMATED ASSISTANT", "normal", List.of(
                "The number you have dialled is valid, but it only connects from a very specific place.",
                "Try another dimension."),
                c("Hang up", "bye", "hangup"));

        // ---------------------------------------------------------------- archive (clue number)
        n("archive", "THE ARCHIVE", "ominous", List.of(
                "ACCESS GRANTED. You found every slip. Nobody finds every slip.",
                "The Black Ledger marks whatever it hits. Marked things bleed more.",
                "It has been added to your CLASSIFIED listings. Please sign the ledger."),
                c("Open the catalogue", "bye", "unlock:archive;goto:catalog"), c("Hang up", "bye", "unlock:archive;hangup"));

        // ---------------------------------------------------------------- the disconnected number + operator
        n("disconnected", "SYSTEM", "corrupt", List.of(
                "The number you have dialled has been disconnected.",
                "The number you have dialled has been d1sc0nnect3d.",
                "The number you have dialled has been",
                "...are you still there?"),
                c("Hang up", "bye", "hangup;event:eerie"));
        n("operator", "THE OPERATOR", "ominous", List.of(
                "Do not hang up.",
                "You dialled a dead line. Dead lines are my favourite. They are quiet. Nobody is on them. Except me.",
                "I am the Operator. I take orders that nobody else is permitted to take."),
                c("Who are you?", "operator_who"), c("What do you sell?", "operator_offer"), c("(hang up)", "operator_bye", "hangup"));
        n("operator_who", "THE OPERATOR", "ominous", List.of(
                "I was the first employee. Then I was the only employee. Then the company grew around me.",
                "I answer the numbers that do not exist. It is a lonely job. It has excellent benefits."),
                c("What do you sell?", "operator_offer"), c("(hang up)", "operator_bye", "hangup"));
        n("operator_offer", "THE OPERATOR", "ominous", List.of(
                "I can send you something that has not happened yet.",
                "It costs four hundred credits. I do not take refunds, because I do not take them back."),
                c("Pay 400 credits", "operator_pay", "pay:400"), c("What is the catch?", "operator_catch"), c("(hang up)", "operator_bye", "hangup"));
        n("operator_catch", "THE OPERATOR", "ominous", List.of(
                "There is always an echo.",
                "Everything you do with it, you do twice. Mostly the second one is the problem."),
                c("Pay 400 credits", "operator_pay", "pay:400"), c("(hang up)", "operator_bye", "hangup"));
        n("operator_pay", "THE OPERATOR", "ominous", List.of(
                "It is done. Check the CLASSIFIED listings. The Paradox Revolver is yours to order.",
                "It will arrive twice. Do not be surprised."),
                c("Open the catalogue", "operator_bye", "unlock:operator;goto:catalog"), c("(hang up)", "operator_bye", "unlock:operator;hangup"));
        n("operator_broke", "THE OPERATOR", "ominous", List.of(
                "You cannot afford the impossible. Come back when you can.",
                "I will be here. I am always here. That is the problem."),
                c("(hang up)", "operator_bye", "hangup"));
        n("operator_bye", "THE OPERATOR", "ominous", List.of("...", "I will call again."));

        // ---------------------------------------------------------------- order events
        n("event_ringback", "THE OPERATOR", "ominous", List.of(
                "Your order has been noticed.",
                "Someone upstairs wants to know why you wanted that. I told them it was a hobby.",
                "Accept this small credit as a courtesy. It is not a bribe. It is a bribe."),
                c("Take 25 credits", "event_ok", "credits:25"), c("Refuse", "event_ok"));
        n("event_ok", "THE OPERATOR", "ominous", List.of("Good. Enjoy your delivery."));
        n("event_survey", "T.O. FEEDBACK", "normal", List.of(
                "Quick survey: how likely are you to recommend Telephone Order to a person you do not like?",
                "Your honest feedback will be ignored with great care."),
                c("Very likely", "event_survey_done", "credits:15"), c("Unlikely", "event_survey_done", "credits:10"));
        n("event_survey_done", "T.O. FEEDBACK", "normal", List.of("Thank you. Credits have been added as a thank-you. Please do not spend them all in one place."));

        // ---------------------------------------------------------------- misc numbers
        n("jenny", "JENNY'S ANSWERING MACHINE", "normal", List.of("Hi, you've reached Jenny. I'm not here right now.", "Please leave an order at the beep.", "*beep*"),
                c("Hang up", "bye", "hangup"));
        n("luggage", "T.O. AUTOMATED ASSISTANT", "normal", List.of("That is the combination to somebody's luggage.", "We have credited the owner's account on their behalf: 10 credits."),
                c("Hang up", "bye", "hangup;credits:10;flag:luggage"));
        n("emergency", "T.O. AUTOMATED ASSISTANT", "normal", List.of("This is not that kind of corporation.", "However, a courier has been dispatched with a sympathy card."),
                c("Hang up", "bye", "hangup"));
        n("e404", "ERROR", "corrupt", List.of("ERR 404: LINE NOT FOUND", "ERR 404: LINE NOT FOUND", "ERR 404: THIS ISN'T A REAL NUMBER (but you knew that)"),
                c("Hang up", "bye", "hangup"));
        n("operator_hint", "RECEPTIONIST", "normal", List.of("The Operator? He doesn't take calls. He makes them.", "If you ever hear a phone ring that nobody is calling, answer it."),
                c("Hang up", "bye", "hangup"));
        n("clue_hint", "RECEPTIONIST", "normal", List.of("The Archive is a seven-digit number we lost.", "Seven torn slips, seven digits, scattered in the dark places where nobody looks twice. Chests. Old ones.", "Piece them together and dial."),
                c("Hang up", "bye", "hangup"));
        n("wrong", "A STRANGER", "normal", List.of("Hello? ... Who is this? ... I think you have the wrong number.", "*click*"), c("Hang up", "bye", "hangup"));
        n("busy", "SYSTEM", "normal", List.of("The number you have dialled is not in service.", "Please check the number and try again."), c("Hang up", "bye", "hangup"));
    }

    public static final String ARCHIVE_NUMBER = "4815162";
    public static final int[] ARCHIVE_DIGITS = {4, 8, 1, 5, 1, 6, 2};

    public static List<String> flavourForDimension(String dim) {
        List<String> l = new ArrayList<>();
        switch (dim) {
            case "nether" -> l.add("Line quality: SCORCHED. Area code 666.");
            case "end" -> l.add("Line quality: ... Area code 000. Nobody should be calling from here.");
            case "overworld" -> l.add("Line quality: acceptable. Area code 555.");
            default -> l.add("Line quality: unknown region. Area code ???.");
        }
        return l;
    }
}
