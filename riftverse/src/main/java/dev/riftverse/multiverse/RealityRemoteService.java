package dev.riftverse.multiverse;

import dev.riftverse.RiftverseConfig;
import dev.riftverse.multiverse.event.EventManager;
import dev.riftverse.multiverse.event.EventType;
import dev.riftverse.network.Payloads;
import dev.riftverse.network.UniverseEntry;
import dev.riftverse.player.PlayerMultiverseData;
import dev.riftverse.registry.RvAttachments;
import dev.riftverse.registry.RvItems;
import dev.riftverse.universe.Archetype;
import dev.riftverse.universe.PromptInterpreter;
import dev.riftverse.universe.UniverseId;
import dev.riftverse.universe.UniverseRegistry;
import dev.riftverse.universe.UniverseSpec;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Server side of the Reality Remote. Every request from the GUI is re-validated here: the player must be holding a
 * Remote, the target must be a universe they have charted (or stand in), the action must be within their cosmic rank
 * (creative players and operators are exempt) and the item cooldown must have elapsed.
 */
public final class RealityRemoteService {
    private RealityRemoteService() {}

    public static CosmicRank required(int action) {
        return switch (action) {
            case Payloads.RemoteAction.SCAN, Payloads.RemoteAction.STABILIZE, Payloads.RemoteAction.VISIT, Payloads.RemoteAction.PREVIEW,
                 Payloads.RemoteAction.STOP -> CosmicRank.WANDERER;
            case Payloads.RemoteAction.MODIFY, Payloads.RemoteAction.EVENT -> CosmicRank.VOYAGER;
            case Payloads.RemoteAction.ARCHIVE, Payloads.RemoteAction.RESTORE, Payloads.RemoteAction.CREATE -> CosmicRank.CARTOGRAPHER;
            default -> CosmicRank.ARCHITECT;
        };
    }

    private static boolean privileged(ServerPlayer p) {
        return p.isCreative() || p.hasPermissions(2);
    }

    private static boolean holding(ServerPlayer p) {
        return p.getMainHandItem().is(RvItems.REALITY_REMOTE.get()) || p.getOffhandItem().is(RvItems.REALITY_REMOTE.get());
    }

    public static void open(ServerPlayer player) {
        MinecraftServer server = player.server;
        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        Set<Long> ids = new LinkedHashSet<>();
        UniverseId here = RealityOps.universeOf(player);
        if (here != null) ids.add(here.pack());
        List<Long> discovered = data.discovered();
        for (int i = discovered.size() - 1; i >= 0; i--) ids.add(discovered.get(i));
        if (privileged(player)) for (UniverseSpec s : UniverseRegistry.get(server).manifestedBy(player.getUUID())) ids.add(s.id.pack());
        List<UniverseEntry> entries = new ArrayList<>();
        List<Integer> statuses = new ArrayList<>();
        List<Integer> stability = new ArrayList<>();
        RealityState state = RealityState.get(server);
        for (long id : ids) {
            UniverseId uid = UniverseId.unpack(id);
            UniverseSpec s = UniverseRegistry.specFor(uid);
            entries.add(UniverseEntry.of(s, uid.isPrime() ? UniverseEntry.PRIME : UniverseEntry.DISCOVERED));
            UniverseProfile p = state.profile(uid);
            statuses.add(p.status.ordinal());
            stability.add(Math.round(p.stability));
            if (entries.size() >= 256) break;
        }
        PacketDistributor.sendToPlayer(player, new Payloads.OpenRemote(data.rank().ordinal(), data.research, here == null ? Payloads.OpenRemote.NONE : here.pack(),
                entries, statuses, stability));
    }

    private static void say(ServerPlayer p, RealityOps.Outcome o) {
        for (String line : o.message().split("\n")) p.sendSystemMessage(Component.literal(line).withColor(o.ok() ? 0xB0E8FF : 0xFF8A9A));
    }

    public static void handle(ServerPlayer player, Payloads.RemoteAction a) {
        MinecraftServer server = player.server;
        if (!holding(player)) return;
        ItemStack remote = RvItems.REALITY_REMOTE.get().getDefaultInstance();
        PlayerMultiverseData data = player.getData(RvAttachments.MULTIVERSE.get());
        CosmicRank need = required(a.action());
        boolean gate = RiftverseConfig.get(RiftverseConfig.REMOTE_REQUIRES_RANK, true) && !privileged(player);
        if (gate && data.rank().ordinal() < need.ordinal()) {
            say(player, RealityOps.Outcome.fail("The Remote refuses: this function requires cosmic rank " + need.title + " (you are " + data.rank().title + ")."));
            return;
        }
        if (player.getCooldowns().isOnCooldown(remote.getItem()) && a.action() != Payloads.RemoteAction.SCAN && a.action() != Payloads.RemoteAction.STOP) {
            say(player, RealityOps.Outcome.fail("The Remote is still recharging."));
            return;
        }
        UniverseId target = a.universe() == Payloads.OpenRemote.NONE ? null : UniverseId.unpack(a.universe());
        if (target != null && !privileged(player) && !data.discovered().contains(target.pack()) && !target.equals(RealityOps.universeOf(player))) {
            say(player, RealityOps.Outcome.fail("You have not charted that universe."));
            return;
        }
        RealityOps.Outcome out = switch (a.action()) {
            case Payloads.RemoteAction.SCAN -> target == null ? RealityOps.Outcome.fail("Select a universe.") : RealityOps.scan(player, target);
            case Payloads.RemoteAction.STABILIZE -> target == null ? RealityOps.Outcome.fail("Select a universe.") : RealityOps.stabilize(server, target);
            case Payloads.RemoteAction.VISIT -> target == null ? RealityOps.Outcome.fail("Select a universe.") : RealityOps.visit(player, target);
            case Payloads.RemoteAction.MODIFY -> {
                if (target == null) yield RealityOps.Outcome.fail("Select a universe.");
                String[] parts = a.text().trim().split("\\s+", 2);
                yield parts.length < 2 ? RealityOps.Outcome.fail("Type a trait and value, e.g. gravity 0.4") : RealityOps.modify(server, target, parts[0], parts[1]);
            }
            case Payloads.RemoteAction.ARCHIVE -> target == null ? RealityOps.Outcome.fail("Select a universe.") : guardPrime(player, target, () -> RealityOps.archive(server, target));
            case Payloads.RemoteAction.RESTORE -> target == null ? RealityOps.Outcome.fail("Select a universe.") : RealityOps.restore(server, target, player);
            case Payloads.RemoteAction.REBUILD -> {
                if (target == null) yield RealityOps.Outcome.fail("Select a universe.");
                boolean mutate = a.option() == -2;
                Archetype into = a.option() >= 0 ? Archetype.byId(a.option()) : null;
                yield guardPrime(player, target, () -> RealityOps.rebuild(server, target, mutate, into, player));
            }
            case Payloads.RemoteAction.CREATE -> create(player, a.text());
            case Payloads.RemoteAction.EVENT -> EventManager.start(EventType.values()[Math.floorMod(a.option(), EventType.values().length)], player.serverLevel(),
                    player.position().add(player.getLookAngle().scale(12)), player, false);
            case Payloads.RemoteAction.ERASE -> target == null ? RealityOps.Outcome.fail("Select a universe.") : guardPrime(player, target, () -> RealityOps.erase(server, target, player));
            case Payloads.RemoteAction.PROTOCOL -> {
                if (target == null) yield RealityOps.Outcome.fail("Select a universe.");
                EndProtocol p = EndProtocol.byOrdinal(a.option());
                yield guardPrime(player, target, () -> EndProtocols.execute(server, target, p, player, a.flag()));
            }
            case Payloads.RemoteAction.PREVIEW -> EndProtocols.preview(player, EndProtocol.byOrdinal(a.option()));
            case Payloads.RemoteAction.STOP -> {
                if (target != null && privileged(player)) yield EndProtocols.stop(server, target, null);
                yield EndProtocols.stop(server, null, player.getUUID());
            }
            default -> RealityOps.Outcome.fail("Unknown Remote function.");
        };
        say(player, out);
        if (out.ok()) {
            int cd = switch (a.action()) {
                case Payloads.RemoteAction.PROTOCOL, Payloads.RemoteAction.ERASE, Payloads.RemoteAction.REBUILD -> 600;
                case Payloads.RemoteAction.EVENT, Payloads.RemoteAction.CREATE, Payloads.RemoteAction.RESTORE, Payloads.RemoteAction.ARCHIVE -> 200;
                case Payloads.RemoteAction.SCAN -> 0;
                default -> 40;
            };
            if (cd > 0 && !player.isCreative()) player.getCooldowns().addCooldown(remote.getItem(), cd);
        }
    }

    private static RealityOps.Outcome guardPrime(ServerPlayer player, UniverseId target, java.util.function.Supplier<RealityOps.Outcome> op) {
        if (target.isPrime() && RiftverseConfig.get(RiftverseConfig.PROTECT_PRIME_UNIVERSES, true) && !player.hasPermissions(2)) {
            return RealityOps.Outcome.fail("Prime realities are protected from the Remote (server config protectPrimeUniverses).");
        }
        return op.get();
    }

    private static RealityOps.Outcome create(ServerPlayer player, @Nullable String text) {
        String prompt = text == null ? "" : text.trim();
        if (prompt.isEmpty()) return RealityOps.Outcome.fail("Describe the universe to create (or paste dna:RV-...).");
        UniverseRegistry reg = UniverseRegistry.get(player.server);
        int max = RiftverseConfig.get(RiftverseConfig.MAX_PROMPT_UNIVERSES_PER_PLAYER, 64);
        if (!privileged(player) && reg.manifestCount(player.getUUID()) >= max) return RealityOps.Outcome.fail("You have manifested the maximum of " + max + " universes.");
        UniverseSpec spec;
        if (prompt.toLowerCase(java.util.Locale.ROOT).startsWith("dna:")) {
            String code = prompt.substring(4).trim();
            if (UniverseDna.decode(code, new UniverseId(0, -1)) == null) return RealityOps.Outcome.fail("Invalid Universe DNA.");
            spec = reg.manifestCustom(id -> {
                UniverseSpec s = UniverseDna.decode(code, id);
                s.prompt = "DNA " + code;
                return s;
            }, player.getUUID());
        } else {
            PromptInterpreter.Result r = reg.manifest(prompt, player.getUUID());
            spec = r.spec();
        }
        player.getData(RvAttachments.MULTIVERSE.get()).discover(spec.id.pack());
        RealityOps.research(player, 20, "universe architected");
        return RealityOps.Outcome.ok("Architected " + spec.name + " [" + spec.id.designation() + "]. It is now in your Remote's target list; select it and Visit.");
    }
}
