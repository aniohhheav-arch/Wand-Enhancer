package dev.mysticarts.power.service;

import dev.mysticarts.MaConfig;
import dev.mysticarts.network.Payloads;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import dev.mysticarts.MysticArts;

/**
 * Server-authoritative store of status marks, one table per dimension. Marks tick down every server tick and apply
 * their behaviour; the table is mirrored to every client in the dimension so time-frozen entities also stop ticking
 * client-side and status visuals can be drawn.
 */
public final class EntityMarks {
    public static final class Mark {
        public int ticks;
        public int param;
        @Nullable public Vec3 anchor;
        public int owner = -1;

        Mark(int ticks, int param) {
            this.ticks = ticks;
            this.param = param;
        }
    }

    private static final class Table {
        final Map<Integer, EnumMap<MarkKind, Mark>> marks = new HashMap<>();
        boolean dirty;
        int sinceSync;
    }

    private static final Map<ResourceKey<Level>, Table> TABLES = new HashMap<>();
    private static final net.minecraft.resources.ResourceLocation FREEZE_SPEED = MysticArts.id("time_freeze");

    private EntityMarks() {}

    private static Table table(Level level) {
        return TABLES.computeIfAbsent(level.dimension(), k -> new Table());
    }

    public static void clearAll() {
        TABLES.clear();
    }

    public static Mark add(Entity entity, MarkKind kind, int ticks, int param) {
        Table t = table(entity.level());
        EnumMap<MarkKind, Mark> map = t.marks.computeIfAbsent(entity.getId(), k -> new EnumMap<>(MarkKind.class));
        Mark m = map.get(kind);
        if (m == null) {
            m = new Mark(ticks, param);
            map.put(kind, m);
        } else {
            m.ticks = Math.max(m.ticks, ticks);
            m.param = param;
        }
        t.dirty = true;
        return m;
    }

    public static void remove(Entity entity, MarkKind kind) {
        Table t = table(entity.level());
        EnumMap<MarkKind, Mark> map = t.marks.get(entity.getId());
        if (map != null && map.remove(kind) != null) {
            t.dirty = true;
            if (kind == MarkKind.FROZEN && entity instanceof Player p) unfreezePlayer(p);
        }
    }

    @Nullable
    public static Mark get(Entity entity, MarkKind kind) {
        Table t = TABLES.get(entity.level().dimension());
        if (t == null) return null;
        EnumMap<MarkKind, Mark> map = t.marks.get(entity.getId());
        return map == null ? null : map.get(kind);
    }

    public static boolean has(Entity entity, MarkKind kind) {
        return get(entity, kind) != null;
    }

    /** Whether the entity should skip this tick (time stop / time slow). Used on the server tick event. */
    public static boolean suppressTick(Entity entity) {
        Table t = TABLES.get(entity.level().dimension());
        if (t == null) return false;
        EnumMap<MarkKind, Mark> map = t.marks.get(entity.getId());
        if (map == null) return false;
        if (map.containsKey(MarkKind.FROZEN)) return true;
        Mark slow = map.get(MarkKind.SLOWED);
        return slow != null && slow.param > 1 && entity.level().getGameTime() % slow.param != 0;
    }

    public static List<Entity> marked(ServerLevel level, MarkKind kind) {
        List<Entity> out = new ArrayList<>();
        Table t = TABLES.get(level.dimension());
        if (t == null) return out;
        for (Map.Entry<Integer, EnumMap<MarkKind, Mark>> e : t.marks.entrySet()) {
            if (e.getValue().containsKey(kind)) {
                Entity ent = level.getEntity(e.getKey());
                if (ent != null) out.add(ent);
            }
        }
        return out;
    }

    public static void tick(ServerLevel level) {
        Table t = TABLES.get(level.dimension());
        if (t == null) return;
        Iterator<Map.Entry<Integer, EnumMap<MarkKind, Mark>>> it = t.marks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, EnumMap<MarkKind, Mark>> e = it.next();
            Entity entity = level.getEntity(e.getKey());
            if (entity == null || !entity.isAlive()) {
                it.remove();
                t.dirty = true;
                continue;
            }
            Iterator<Map.Entry<MarkKind, Mark>> mi = e.getValue().entrySet().iterator();
            while (mi.hasNext()) {
                Map.Entry<MarkKind, Mark> me = mi.next();
                Mark m = me.getValue();
                apply(level, entity, me.getKey(), m);
                if (--m.ticks <= 0) {
                    mi.remove();
                    t.dirty = true;
                    if (me.getKey() == MarkKind.FROZEN && entity instanceof Player p) unfreezePlayer(p);
                    if (me.getKey() == MarkKind.LIFTED) entity.setNoGravity(false);
                }
            }
            if (e.getValue().isEmpty()) it.remove();
        }
        t.sinceSync++;
        if ((t.dirty && t.sinceSync >= 2) || (t.sinceSync >= 40 && !t.marks.isEmpty())) {
            sync(level, t);
        }
    }

    private static void sync(ServerLevel level, Table t) {
        List<Payloads.Marks.Entry> entries = new ArrayList<>();
        for (Map.Entry<Integer, EnumMap<MarkKind, Mark>> e : t.marks.entrySet()) {
            for (Map.Entry<MarkKind, Mark> me : e.getValue().entrySet()) {
                entries.add(new Payloads.Marks.Entry(e.getKey(), me.getKey().ordinal(), me.getValue().ticks, me.getValue().param));
            }
        }
        PacketDistributor.sendToPlayersInDimension(level, new Payloads.Marks(entries));
        t.dirty = false;
        t.sinceSync = 0;
    }

    private static void apply(ServerLevel level, Entity entity, MarkKind kind, Mark m) {
        switch (kind) {
            case FROZEN -> {
                if (entity instanceof Player p) freezePlayer(p);
                else if (entity instanceof LivingEntity living && living.invulnerableTime > 0) living.invulnerableTime--;
            }
            case BOUND, PRISON -> {
                if (m.anchor == null) m.anchor = entity.position();
                entity.setDeltaMovement(Vec3.ZERO);
                if (entity.position().distanceToSqr(m.anchor) > 0.01) entity.teleportTo(m.anchor.x, m.anchor.y, m.anchor.z);
                if (entity instanceof Mob mob) mob.getNavigation().stop();
            }
            case TETHERED -> {
                if (m.anchor == null) m.anchor = entity.position();
                Vec3 off = entity.position().subtract(m.anchor);
                double d = off.length();
                if (d > 3) entity.setDeltaMovement(entity.getDeltaMovement().add(off.scale(-0.25 * (d - 3) / d)));
                entity.hurtMarked = true;
            }
            case LIFTED -> {
                if (m.anchor == null) m.anchor = entity.position().add(0, 3, 0);
                entity.setNoGravity(true);
                Vec3 to = m.anchor.subtract(entity.position());
                entity.setDeltaMovement(entity.getDeltaMovement().scale(0.6).add(to.scale(0.15)));
                entity.hurtMarked = true;
                entity.fallDistance = 0;
            }
            case SEPARATED -> {
                if (entity instanceof Mob mob) {
                    mob.getNavigation().stop();
                    mob.setTarget(null);
                    entity.setDeltaMovement(entity.getDeltaMovement().multiply(0, 1, 0));
                }
            }
            case PACIFIED -> {
                if (entity instanceof Mob mob && mob.getTarget() instanceof Player) mob.setTarget(null);
            }
            case CONTROLLED -> controlledTick(level, entity, m);
            default -> {}
        }
    }

    private static void controlledTick(ServerLevel level, Entity entity, Mark m) {
        if (!(entity instanceof Mob mob)) return;
        Entity controller = level.getEntity(m.param);
        LivingEntity target = mob.getTarget();
        if (target != null && (target == controller || target instanceof Player || has(target, MarkKind.CONTROLLED))) {
            mob.setTarget(null);
            target = null;
        }
        if (m.anchor != null && target == null) {
            mob.getNavigation().moveTo(m.anchor.x, m.anchor.y, m.anchor.z, 1.2);
            if (mob.position().distanceToSqr(m.anchor) < 2) m.anchor = null;
            return;
        }
        if (target == null && level.getGameTime() % 10 == 0) {
            AABB box = mob.getBoundingBox().inflate(16);
            LivingEntity best = null;
            double bestD = Double.MAX_VALUE;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, x -> x instanceof Enemy && x != mob && x.isAlive() && !has(x, MarkKind.CONTROLLED))) {
                double d = e.distanceToSqr(mob);
                if (d < bestD) {
                    bestD = d;
                    best = e;
                }
            }
            if (best != null) mob.setTarget(best);
            else if (controller != null && mob.distanceToSqr(controller) > 36) mob.getNavigation().moveTo(controller, 1.1);
        }
    }

    private static void freezePlayer(Player p) {
        if (!MaConfig.FREEZE_PLAYERS.get()) return;
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null && !speed.hasModifier(FREEZE_SPEED)) {
            speed.addTransientModifier(new AttributeModifier(FREEZE_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null && !jump.hasModifier(FREEZE_SPEED)) {
            jump.addTransientModifier(new AttributeModifier(FREEZE_SPEED, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
        p.setDeltaMovement(Vec3.ZERO);
        p.hurtMarked = true;
    }

    private static void unfreezePlayer(Player p) {
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(FREEZE_SPEED);
        AttributeInstance jump = p.getAttribute(Attributes.JUMP_STRENGTH);
        if (jump != null) jump.removeModifier(FREEZE_SPEED);
    }
}
