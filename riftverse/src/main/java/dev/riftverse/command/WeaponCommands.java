package dev.riftverse.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.riftverse.creator.CreatorAuthority;
import dev.riftverse.creator.RuptureService;
import dev.riftverse.item.ItemData;
import dev.riftverse.registry.RvItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /multiverse weapon ... and /multiverse reality rupture ... . Invisible to anyone who is neither the creator nor a
 * level-4 operator; every powerful action additionally needs an authenticated session. The secret itself is never
 * accepted as a command argument (it would land in logs and chat history) — it is typed into the masked interface.
 */
final class WeaponCommands {
    private WeaponCommands() {}

    private static boolean eligible(CommandSourceStack s) {
        return s.hasPermission(2);
    }

    private static int msg(CommandSourceStack s, String text, boolean ok) {
        s.sendSystemMessage(Component.literal(text).withColor(ok ? 0xC080FF : 0xFF5070));
        return ok ? 1 : 0;
    }

    private static ServerPlayer session(CommandSourceStack s) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return s.getPlayerOrException();
    }

    static LiteralArgumentBuilder<CommandSourceStack> weapon() {
        return Commands.literal("weapon").requires(WeaponCommands::eligible)
                .then(Commands.literal("give").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    p.getInventory().placeItemBackInInventory(RuptureService.forge(c.getSource().getServer(), p.getUUID()));
                    return msg(c.getSource(), "THE REALITY RUPTURE is yours.", true);
                }).then(Commands.argument("player", EntityArgument.player()).executes(c -> {
                    ServerPlayer t = EntityArgument.getPlayer(c, "player");
                    t.getInventory().placeItemBackInInventory(RuptureService.forge(c.getSource().getServer(), t.getUUID()));
                    return msg(c.getSource(), "Gave the Rupture to " + t.getGameProfile().getName() + ".", true);
                })))
                .then(Commands.literal("discover").executes(c -> {
                    RuptureService.materialize(c.getSource().getPlayerOrException());
                    return 1;
                }))
                .then(Commands.literal("aura").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    CreatorAuthority.toggleAura(c.getSource().getServer(), p.getUUID());
                    p.refreshDisplayName();
                    return msg(c.getSource(), "Aura " + (CreatorAuthority.aura(p.getUUID()) ? "on" : "off") + ".", true);
                }))
                .then(Commands.literal("inspect").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayer();
                    String held = "";
                    if (p != null) {
                        ItemStack s = p.getMainHandItem();
                        if (s.is(RvItems.REALITY_RUPTURE.get())) {
                            long serial = ItemData.read(s).getLong("rupture_serial");
                            held = " Held copy: serial " + Long.toHexString(serial) + (CreatorAuthority.validSerial(serial) ? " (registered)" : " (UNREGISTERED)") + ".";
                        }
                    }
                    return msg(c.getSource(), "Credential " + (CreatorAuthority.credentialSet() ? "configured" : "NOT configured")
                            + ", creators " + CreatorAuthority.creatorCount() + ", granted " + CreatorAuthority.grantedCount()
                            + ", issued copies " + CreatorAuthority.serialCount() + ", restorable snapshots " + RuptureService.snapshotCount()
                            + ", ultimate mode " + RuptureService.mode().name().toLowerCase() + "." + (p != null && CreatorAuthority.inSession(p) ? " Session open." : "") + held, true);
                }))
                .then(Commands.literal("test").executes(c -> {
                    ServerPlayer p = session(c.getSource());
                    RuptureService.tear(p);
                    return msg(c.getSource(), "Test tear opened.", true);
                }))
                .then(Commands.literal("restore").executes(c -> {
                    session(c.getSource());
                    int n = RuptureService.restoreLatest();
                    return msg(c.getSource(), n > 0 ? "Restored " + n + " blocks." : "Nothing to restore.", n > 0);
                }));
    }

    static LiteralArgumentBuilder<CommandSourceStack> rupture() {
        return Commands.literal("reality").then(Commands.literal("rupture").requires(WeaponCommands::eligible)
                .then(Commands.literal("preview").executes(c -> {
                    RuptureService.preview(session(c.getSource()));
                    return 1;
                }))
                .then(Commands.literal("activate").executes(c -> {
                    RuptureService.finalRupture(session(c.getSource()), RuptureService.mode());
                    return 1;
                })
                        .then(Commands.literal("visual").executes(c -> {
                            RuptureService.finalRupture(session(c.getSource()), RuptureService.Mode.VISUAL);
                            return 1;
                        }))
                        .then(Commands.literal("area").executes(c -> {
                            RuptureService.finalRupture(session(c.getSource()), RuptureService.Mode.AREA);
                            return 1;
                        }))
                        .then(Commands.literal("universe").executes(c -> {
                            RuptureService.finalRupture(session(c.getSource()), RuptureService.Mode.UNIVERSE);
                            return 1;
                        }))));
    }
}
