package dev.telephoneorder.client;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

/** /toorient show | reset | &lt;view&gt; &lt;yaw|pitch|roll|x|y|z|scale&gt; &lt;value&gt; — live-tune how guns are held / shown. */
final class GunOrientCommand {
    private GunOrientCommand() { }

    static void register(CommandDispatcher<FabricClientCommandSource> d) {
        LiteralArgumentBuilder<FabricClientCommandSource> root = ClientCommands.literal("toorient");
        root.then(ClientCommands.literal("show").executes(c -> { c.getSource().sendFeedback(Component.literal(GunOrient.describe())); return 1; }));
        root.then(ClientCommands.literal("reset").executes(c -> { GunOrient.reset(); c.getSource().sendFeedback(Component.literal("Gun orientation reset.")); return 1; }));
        RequiredArgumentBuilder<FabricClientCommandSource, String> view = ClientCommands.argument("view", StringArgumentType.word())
                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Arrays.asList(GunOrient.VIEWS), b));
        RequiredArgumentBuilder<FabricClientCommandSource, String> field = ClientCommands.argument("field", StringArgumentType.word())
                .suggests((c, b) -> net.minecraft.commands.SharedSuggestionProvider.suggest(Arrays.asList(GunOrient.FIELDS), b));
        field.then(ClientCommands.argument("value", FloatArgumentType.floatArg(-720, 720)).executes(c -> {
            String v = StringArgumentType.getString(c, "view"), f = StringArgumentType.getString(c, "field");
            if (!Arrays.asList(GunOrient.VIEWS).contains(v) || !Arrays.asList(GunOrient.FIELDS).contains(f)) {
                c.getSource().sendFeedback(Component.literal("views: " + String.join(", ", GunOrient.VIEWS) + "  fields: " + String.join(", ", GunOrient.FIELDS)));
                return 0;
            }
            GunOrient.set(v, f, FloatArgumentType.getFloat(c, "value"));
            c.getSource().sendFeedback(Component.literal(v + "." + f + " = " + GunOrient.get(v, f)));
            return 1;
        }));
        root.then(view.then(field));
        d.register(root);
    }
}
