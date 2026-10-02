package dev.mysticarts.power.spells;

import dev.mysticarts.entity.FieldKind;
import dev.mysticarts.entity.MysticCloneEntity;
import dev.mysticarts.entity.ShockwaveEntity;
import dev.mysticarts.entity.SpellFieldEntity;
import dev.mysticarts.fx.Fx;
import dev.mysticarts.fx.FxKind;
import dev.mysticarts.power.Ability;
import dev.mysticarts.power.Aim;
import dev.mysticarts.power.Cast;
import dev.mysticarts.power.Poses;
import dev.mysticarts.power.Spells;
import dev.mysticarts.power.Sustained;
import dev.mysticarts.power.service.EntityMarks;
import dev.mysticarts.power.service.MarkKind;
import dev.mysticarts.registry.MaEntities;
import dev.mysticarts.registry.MaSounds;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** The Mind Stone: thought made force. */
public final class MindSpells {
    public static final int GOLD = 0xFFC81E;

    private MindSpells() {}

    public static void register() {
        Spells.register(Ability.MIND_TELEKINESIS, c -> MysticSpells.telekinesis(c, Sustained.HOLD_MIND, 32));
        Spells.register(Ability.MIND_CONTROL, MindSpells::control);
        Spells.register(Ability.MIND_PACIFY, MindSpells::pacify);
        Spells.register(Ability.MIND_ILLUSION, MindSpells::illusion);
        Spells.register(Ability.MIND_BEAM, c -> {
            Sustained.startBeam(c.player(), c.data(), c.ability(), 70);
            Fx.sound(c.player(), MaSounds.BEAM_MYSTIC.get(), 1f, 1.2f);
            return true;
        });
        Spells.register(Ability.MIND_PULSE, MindSpells::pulse);
        Spells.register(Ability.MIND_DETECT, c -> MysticSpells.detect(c, 64, true));
        Spells.register(Ability.MIND_REMOTE, MindSpells::remote);
        Spells.register(Ability.MIND_ENHANCE, MindSpells::enhance);
        Spells.register(Ability.MIND_COMMAND, MindSpells::command);
        Spells.register(Ability.MIND_ULTIMATE, MindSpells::ultimate);
    }

    private static boolean control(Cast c) {
        LivingEntity t = Aim.entity(c.player(), 28, e -> e instanceof Mob && Aim.controllable(e));
        if (!(t instanceof Mob mob)) return false;
        EntityMarks.add(mob, MarkKind.CONTROLLED, 900, c.player().getId());
        EntityMarks.remove(mob, MarkKind.PACIFIED);
        mob.setTarget(null);
        mob.setPersistenceRequired();
        Fx.send(c.level(), FxKind.ARC, c.eye(), mob.getEyePosition().subtract(c.eye()), GOLD, 0.4f, 0, c.player().getId());
        Fx.burst(c.level(), mob.getEyePosition(), GOLD, 0.8f, 1f);
        Fx.sound(c.level(), mob.position(), MaSounds.STONE_MIND.get(), 1f, 1.4f);
        c.player().displayClientMessage(Component.translatable("message.mysticarts.controlled", mob.getDisplayName()).withColor(GOLD), true);
        return true;
    }

    private static boolean pacify(Cast c) {
        int n = 0;
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, c.player().getBoundingBox().inflate(14), m -> Aim.controllable(m) && !EntityMarks.has(m, MarkKind.CONTROLLED))) {
            EntityMarks.add(m, MarkKind.PACIFIED, 600, 0);
            if (m.getTarget() instanceof Player) m.setTarget(null);
            n++;
        }
        SpellFieldEntity.spawn(c.level(), c.player(), c.player().position().add(0, 1, 0), FieldKind.PSYCHIC_FIELD, 14f, 40, GOLD);
        Fx.sound(c.player(), MaSounds.STONE_MIND.get(), 1.2f, 0.8f);
        c.player().displayClientMessage(Component.translatable("message.mysticarts.pacified", n).withColor(GOLD), true);
        return true;
    }

    private static boolean illusion(Cast c) {
        ServerPlayer p = c.player();
        MysticCloneEntity decoy = MaEntities.MYSTIC_CLONE.get().create(c.level());
        if (decoy == null) return false;
        Vec3 at = Aim.safeLookSpot(c.level(), p, 16).orElse(p.position().add(Aim.horizontal(c.look()).scale(3)));
        decoy.moveTo(at.x, at.y, at.z, p.getYRot() + 180, 0);
        decoy.bind(p, 300);
        decoy.mode(MysticCloneEntity.DECOY);
        c.level().addFreshEntity(decoy);
        for (Mob m : c.level().getEntitiesOfClass(Mob.class, decoy.getBoundingBox().inflate(20), m -> Aim.hostile(m) || m.getTarget() == p)) {
            m.setTarget(decoy);
        }
        Fx.burst(c.level(), at.add(0, 1, 0), GOLD, 1.2f, 1.5f);
        Fx.sound(c.level(), at, MaSounds.CLONE.get(), 1f, 1.3f);
        return true;
    }

    private static boolean pulse(Cast c) {
        ServerPlayer p = c.player();
        for (LivingEntity e : Aim.around(p, p.position(), 9, e -> !MysticSpells.pvpBlocked(c, e))) {
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            if (e instanceof Mob m) m.setTarget(null);
        }
        ShockwaveEntity.spawn(c.level(), p, p.position().add(0, 1, 0), ShockwaveEntity.SPHERE, GOLD, 9f, 10, c.damage(5f), 1.6f, 0.4f);
        Fx.screen(c.level(), p.position(), 12, 0.5f, 0.2f, GOLD);
        Fx.sound(p, MaSounds.PUSH.get(), 1.2f, 1.4f);
        c.data().pose(Poses.RAISE, 10);
        return true;
    }

    /** Operates a distant block as if the caster had touched it: levers, buttons, doors, bells, note blocks... */
    private static boolean remote(Cast c) {
        BlockHitResult hit = Aim.block(c.player(), 48);
        if (hit.getType() != HitResult.Type.BLOCK) return false;
        BlockState s = c.level().getBlockState(hit.getBlockPos());
        if (!c.level().mayInteract(c.player(), hit.getBlockPos())) return false;
        InteractionResult r = s.useWithoutItem(c.level(), c.player(), hit);
        if (!r.consumesAction()) return false;
        Fx.send(c.level(), FxKind.ARC, c.eye(), hit.getLocation().subtract(c.eye()), GOLD, 0.25f, 0, c.player().getId());
        Fx.burst(c.level(), hit.getLocation(), GOLD, 0.5f, 0.6f);
        return true;
    }

    private static boolean enhance(Cast c) {
        ServerPlayer p = c.player();
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 1));
        p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 600, 1));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 0));
        p.addEffect(new MobEffectInstance(MobEffects.JUMP, 600, 0));
        p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 600, 0));
        p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0));
        Fx.send(c.level(), FxKind.CHARGE_UP, p.position().add(0, 1, 0), Vec3.ZERO, GOLD, 1f, 30, p.getId());
        Fx.sound(p, MaSounds.STONE_MIND.get(), 1.2f, 1.2f);
        return true;
    }

    private static boolean command(Cast c) {
        ServerPlayer p = c.player();
        List<Entity> minions = EntityMarks.marked(c.level(), MarkKind.CONTROLLED);
        minions.removeIf(e -> {
            EntityMarks.Mark m = EntityMarks.get(e, MarkKind.CONTROLLED);
            return m == null || m.param != p.getId() || !(e instanceof Mob);
        });
        if (minions.isEmpty()) {
            p.displayClientMessage(Component.translatable("message.mysticarts.no_minions"), true);
            return false;
        }
        LivingEntity target = Aim.entity(p, 48, e -> !minions.contains(e));
        Vec3 point = Aim.point(p, 48);
        for (Entity e : minions) {
            Mob m = (Mob) e;
            EntityMarks.Mark mark = EntityMarks.get(m, MarkKind.CONTROLLED);
            if (target != null) {
                m.setTarget(target);
                if (mark != null) mark.anchor = null;
            } else if (mark != null) {
                m.setTarget(null);
                mark.anchor = point;
            }
        }
        Vec3 mark = target != null ? target.position().add(0, target.getBbHeight() + 0.5, 0) : point;
        Fx.send(c.level(), FxKind.RING, mark, new Vec3(0, 1, 0), GOLD, 1f, 14, -1);
        Fx.sound(p, MaSounds.UI_SELECT.get(), 1f, 1.5f);
        return true;
    }

    private static boolean ultimate(Cast c) {
        ServerPlayer p = c.player();
        SpellFieldEntity f = SpellFieldEntity.spawn(c.level(), p, p.position().add(0, 1, 0), FieldKind.MIND_LIFT, 16f, 100, GOLD);
        f.damage = c.damage(12f);
        Fx.send(c.level(), FxKind.CHARGE_UP, p.position().add(0, 1, 0), Vec3.ZERO, GOLD, 2f, 90, p.getId());
        Fx.sound(p, MaSounds.ULTIMATE_CHARGE.get(), 1.5f, 1.2f);
        Fx.sound(p, MaSounds.STONE_MIND.get(), 1.5f, 0.6f);
        c.data().pose(Poses.RAISE, 90);
        return true;
    }
}
