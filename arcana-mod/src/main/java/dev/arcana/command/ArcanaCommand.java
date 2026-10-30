package dev.arcana.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.arcana.ArcanaMod;
import dev.arcana.item.WandItem;
import dev.arcana.magic.CastService;
import dev.arcana.magic.Mastery;
import dev.arcana.magic.PlayerMagic;
import dev.arcana.magic.School;
import dev.arcana.net.Net;
import dev.arcana.registry.ModComponents;
import java.util.Collection;
import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Operator tooling (permission level 2):
 * <pre>
 * /arcana mastery &lt;players&gt; &lt;school|all&gt; set|add &lt;level&gt;      /arcana mastery &lt;players&gt; &lt;school|all&gt; setxp|addxp &lt;xp&gt;
 * /arcana mastery &lt;players&gt; reset
 * /arcana mana &lt;players&gt; fill | set &lt;amount&gt;      /arcana cooldowns &lt;players&gt; reset
 * /arcana stage &lt;players&gt; &lt;0-2&gt;                    /arcana info &lt;player&gt;
 * </pre>
 */
@EventBusSubscriber(modid = ArcanaMod.MODID)
public final class ArcanaCommand {
    private static final SimpleCommandExceptionType BAD_SCHOOL = new SimpleCommandExceptionType(Component.literal("Unknown school (use void, verdant, glacier, crimson, solar, storm, chronos, arcane or all)"));
    private static final SimpleCommandExceptionType NO_WAND = new SimpleCommandExceptionType(Component.literal("Target is not holding a wand that can change stage"));

    @SubscribeEvent
    public static void register(RegisterCommandsEvent e) {
        register(e.getDispatcher());
    }

    static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("arcana").requires(s -> s.hasPermission(2))
            .then(Commands.literal("mastery").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.literal("reset").executes(c -> mastery(c, "all", (xp, v) -> 0, 0)))
                .then(Commands.argument("school", StringArgumentType.word())
                    .suggests((c, b) -> SharedSuggestionProvider.suggest(schoolNames(), b))
                    .then(op("set", 10, (xp, v) -> Mastery.xpFor(v), true))
                    .then(op("add", 10, (xp, v) -> Mastery.xpFor(Math.min(Mastery.MAX_LEVEL, Mastery.level(xp) + v)), true))
                    .then(op("setxp", 100000, (xp, v) -> v, false))
                    .then(op("addxp", 100000, (xp, v) -> xp + v, false)))))
            .then(Commands.literal("mana").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.literal("fill").executes(c -> mana(c, -1)))
                .then(Commands.literal("set").then(Commands.argument("amount", IntegerArgumentType.integer(0, 100000)).executes(c -> mana(c, IntegerArgumentType.getInteger(c, "amount")))))))
            .then(Commands.literal("cooldowns").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.literal("reset").executes(ArcanaCommand::cooldowns))))
            .then(Commands.literal("stage").then(Commands.argument("targets", EntityArgument.players())
                .then(Commands.argument("stage", IntegerArgumentType.integer(0, 2)).executes(ArcanaCommand::stage))))
            .then(Commands.literal("info").then(Commands.argument("target", EntityArgument.player()).executes(ArcanaCommand::info))));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> op(String name, int max, IntBinaryOperator f, boolean isLevel) {
        return Commands.literal(name).then(Commands.argument("value", IntegerArgumentType.integer(0, max))
            .executes(c -> mastery(c, StringArgumentType.getString(c, "school"), f, IntegerArgumentType.getInteger(c, "value"))));
    }

    private static List<String> schoolNames() {
        java.util.ArrayList<String> l = new java.util.ArrayList<>(List.of("all"));
        for (School s : School.real()) l.add(s.id);
        return l;
    }

    private static int mastery(CommandContext<CommandSourceStack> c, String school, IntBinaryOperator f, int value) throws CommandSyntaxException {
        List<School> schools;
        if (school.equals("all")) schools = List.of(School.real());
        else {
            School s = School.byId(school);
            if (s == School.NONE) throw BAD_SCHOOL.create();
            schools = List.of(s);
        }
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : targets) {
            PlayerMagic m = CastService.magic(p);
            for (School s : schools) m.mastery.put(s.id, Math.max(0, f.applyAsInt(m.xp(s), value)));
            Net.sync(p, true);
        }
        c.getSource().sendSuccess(() -> Component.literal("Updated mastery (" + school + ") for " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static int mana(CommandContext<CommandSourceStack> c, int amount) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : targets) {
            PlayerMagic m = CastService.magic(p);
            m.mana = amount < 0 ? CastService.maxMana(p) : Math.min(amount, CastService.maxMana(p));
            Net.sync(p, true);
        }
        c.getSource().sendSuccess(() -> Component.literal("Set mana for " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static int cooldowns(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : targets) {
            CastService.magic(p).cooldowns.clear();
            Net.sync(p, true);
        }
        c.getSource().sendSuccess(() -> Component.literal("Reset cooldowns for " + targets.size() + " player(s)"), true);
        return targets.size();
    }

    private static int stage(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> targets = EntityArgument.getPlayers(c, "targets");
        int stage = IntegerArgumentType.getInteger(c, "stage");
        int changed = 0;
        for (ServerPlayer p : targets) {
            ItemStack w = CastService.heldWand(p);
            if (w.isEmpty() || WandItem.schoolOf(w) == School.NONE) continue;
            w.set(ModComponents.STAGE.get(), stage);
            changed++;
        }
        if (changed == 0) throw NO_WAND.create();
        int n = changed;
        c.getSource().sendSuccess(() -> Component.literal("Set held wand stage to " + stage + " for " + n + " player(s)"), true);
        return changed;
    }

    private static int info(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = EntityArgument.getPlayer(c, "target");
        PlayerMagic m = CastService.magic(p);
        StringBuilder sb = new StringBuilder(p.getName().getString()).append(String.format(": mana %.0f/%.0f", m.mana, CastService.maxMana(p)));
        for (School s : School.real()) sb.append("\n  ").append(s.id).append(": level ").append(m.level(s)).append(" (").append(m.xp(s)).append(" xp)");
        c.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return 1;
    }

    private ArcanaCommand() {}
}
