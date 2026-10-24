package dev.telephoneorder;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.telephoneorder.phone.Account;
import dev.telephoneorder.weapon.ClueSlipItem;
import dev.telephoneorder.weapon.GunData;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

/**
 * Admin / testing commands (permission level 2):
 * <pre>
 * /telephoneorder credits &lt;players&gt; add|set &lt;n&gt;     /telephoneorder clearance &lt;players&gt; &lt;0-3&gt;
 * /telephoneorder unlock|lock &lt;players&gt; &lt;line|all&gt;   /telephoneorder give &lt;players&gt; &lt;gun|attachment|ammo|all&gt; [count]
 * /telephoneorder all &lt;players&gt;  (every line, max clearance, 100000 credits, every weapon)   /telephoneorder reset &lt;players&gt;
 * </pre>
 * Alias: /to
 */
final class ToCommand {
    static final List<String> LINES = List.of("proto_lab", "operator", "line_overworld", "line_nether", "line_end", "archive");

    private ToCommand() {}

    static void init() {
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, sel) -> {
            var root = build("telephoneorder");
            var node = dispatcher.register(root);
            dispatcher.register(Commands.literal("to").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).redirect(node));
        });
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build(String name) {
        return Commands.literal(name).requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("credits").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.literal("add").then(Commands.argument("n", IntegerArgumentType.integer()).executes(c -> {
                            int n = IntegerArgumentType.getInteger(c, "n");
                            return each(c, p -> { Account.add(p, n); msg(c, p, "credits now " + Account.credits(p)); });
                        })))
                        .then(Commands.literal("set").then(Commands.argument("n", IntegerArgumentType.integer(0)).executes(c -> {
                            int n = IntegerArgumentType.getInteger(c, "n");
                            return each(c, p -> { Account.setCredits(p, n); msg(c, p, "credits set to " + n); });
                        })))))
                .then(Commands.literal("clearance").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("level", IntegerArgumentType.integer(0, 3)).executes(c -> {
                            int lvl = IntegerArgumentType.getInteger(c, "level");
                            return each(c, p -> { Account.setSpent(p, Catalog.TIER_THRESHOLDS[lvl]); msg(c, p, "clearance level " + lvl); });
                        }))))
                .then(Commands.literal("unlock").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("line", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(LINES.stream(), Stream.of("all")), b)).executes(c -> {
                            String line = StringArgumentType.getString(c, "line");
                            return each(c, p -> {
                                if (line.equals("all")) LINES.forEach(k -> Account.unlock(p, k)); else Account.unlock(p, line);
                                msg(c, p, "unlocked " + line);
                            });
                        }))))
                .then(Commands.literal("lock").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("line", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(LINES.stream(), Stream.of("all")), b)).executes(c -> {
                            String line = StringArgumentType.getString(c, "line");
                            return each(c, p -> { if (line.equals("all")) LINES.forEach(k -> Account.lock(p, k)); else Account.lock(p, line); msg(c, p, "locked " + line); });
                        }))))
                .then(Commands.literal("give").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("what", StringArgumentType.word()).suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(Stream.of("all", "guns", "attachments", "ammo", "slips", "strikes"),
                                Stream.concat(ModItems.GUNS.keySet().stream(), Stream.concat(ModItems.ATTACHMENTS.keySet().stream(), ModItems.AMMO.keySet().stream()))), b))
                                .executes(c -> give(c, 1))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 64)).executes(c -> give(c, IntegerArgumentType.getInteger(c, "count")))))))
                .then(Commands.literal("all").then(Commands.argument("targets", EntityArgument.players()).executes(c -> each(c, p -> {
                    LINES.forEach(k -> Account.unlock(p, k));
                    Account.setSpent(p, Math.max(Account.spent(p), Catalog.TIER_THRESHOLDS[Catalog.TIER_THRESHOLDS.length - 1]));
                    Account.setCredits(p, Math.max(Account.credits(p), 100000));
                    for (int i = 0; i < 7; i++) Account.addClue(p, i);
                    giveWhat(p, "guns", 1);
                    msg(c, p, "everything unlocked: all lines, max clearance, 100000 credits, every weapon");
                }))))
                .then(Commands.literal("reset").then(Commands.argument("targets", EntityArgument.players()).executes(c -> each(c, p -> {
                    var t = Account.of(p);
                    for (String k : new String[]{"unlocks", "orders", "history"}) t.remove(k);
                    t.putInt("clues", 0); t.putInt("spent", 0); t.putInt("credits", ModConfig.get().startingCredits);
                    msg(c, p, "account reset");
                }))));
    }

    private interface Act { void run(ServerPlayer p); }

    private static int each(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, Act a) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> ps = EntityArgument.getPlayers(c, "targets");
        for (ServerPlayer p : ps) a.run(p);
        return ps.size();
    }

    private static void msg(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, ServerPlayer p, String s) {
        c.getSource().sendSuccess(() -> Component.literal("[Telephone Order] " + p.getGameProfile().name() + ": " + s), true);
    }

    private static int give(com.mojang.brigadier.context.CommandContext<CommandSourceStack> c, int count) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        String what = StringArgumentType.getString(c, "what");
        return each(c, p -> {
            boolean ok = giveWhat(p, what, count);
            if (ok) msg(c, p, "gave " + what); else c.getSource().sendFailure(Component.literal("Unknown item: " + what));
        });
    }

    private static void hand(ServerPlayer p, ItemStack s) {
        if (!p.getInventory().add(s)) p.level().addFreshEntity(new ItemEntity(p.level(), p.getX(), p.getY() + 0.5, p.getZ(), s));
    }

    static boolean giveWhat(ServerPlayer p, String what, int count) {
        switch (what) {
            case "all" -> { giveWhat(p, "guns", 1); giveWhat(p, "attachments", 1); giveWhat(p, "ammo", 1); giveWhat(p, "strikes", 1); return true; }
            case "guns" -> { ModItems.GUNS.values().forEach(g -> hand(p, GunData.newStack(g.def, g))); return true; }
            case "attachments" -> { ModItems.ATTACHMENTS.values().forEach(i -> hand(p, new ItemStack(i, 2))); return true; }
            case "ammo" -> { ModItems.AMMO.values().forEach(i -> hand(p, new ItemStack(i, 16))); return true; }
            case "strikes" -> { ModItems.ALL.forEach((k, i) -> { if (k.startsWith("strike_")) hand(p, new ItemStack(i)); }); return true; }
            case "slips" -> { for (int i = 0; i < 7; i++) hand(p, ClueSlipItem.make(ModItems.CLUE_SLIP, i)); return true; }
            default -> {
                var g = ModItems.GUNS.get(what);
                if (g != null) { hand(p, GunData.newStack(g.def, g)); return true; }
                Item it = ModItems.ATTACHMENTS.get(what);
                if (it == null) it = ModItems.AMMO.get(what);
                if (it == null) it = ModItems.ALL.get(what);
                if (it == null) return false;
                hand(p, new ItemStack(it, count));
                return true;
            }
        }
    }
}
