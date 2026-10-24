package dev.telephoneorder.block;

import dev.telephoneorder.Catalog;
import dev.telephoneorder.Catalog.GunDef;
import dev.telephoneorder.Fx;
import dev.telephoneorder.ModBlocks;
import dev.telephoneorder.ModConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.Comparator;
import java.util.List;

public class SentryBlockEntity extends BlockEntity {
    private int ammo, life, cooldown;
    private static final GunDef DEF = Catalog.GUN_BY_ID.get("sentinel_kit");

    public SentryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.SENTRY_BE, pos, state);
        this.ammo = (int) DEF.p("turret_ammo", 200);
        this.life = (int) DEF.p("life", 2400);
    }

    public int ammo() { return ammo; }
    public int life() { return life; }
    public void addAmmo(int n) { ammo = Math.min(600, ammo + n); life = Math.max(life, 1200); setChanged(); }

    @Override
    protected void saveAdditional(ValueOutput out) {
        super.saveAdditional(out);
        out.putInt("ammo", ammo);
        out.putInt("life", life);
    }

    @Override
    protected void loadAdditional(ValueInput in) {
        super.loadAdditional(in);
        ammo = in.getIntOr("ammo", 0);
        life = in.getIntOr("life", 0);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SentryBlockEntity be) {
        if (!(level instanceof ServerLevel l)) return;
        if (be.life <= 0 || be.ammo <= 0) {
            if (be.life <= 0 && l.getGameTime() % 40 == 0) { Fx.p(l, ParticleTypes.SMOKE, Vec3.atCenterOf(pos).add(0, 0.5, 0), 2, 0.1, 0.1, 0.1, 0.01); }
            if (be.life > 0) be.life--;
            return;
        }
        be.life--;
        if (be.cooldown > 0) { be.cooldown--; return; }
        double range = DEF.p("turret_range", 18);
        Vec3 from = Vec3.atCenterOf(pos).add(0, 0.25, 0);
        List<Mob> mobs = l.getEntitiesOfClass(Mob.class, new AABB(pos).inflate(range), m -> m instanceof Enemy && m.isAlive());
        mobs.sort(Comparator.comparingDouble(m -> m.distanceToSqr(from)));
        for (Mob m : mobs) {
            Vec3 to = m.getBoundingBox().getCenter();
            var r = l.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            if (r.getType() != HitResult.Type.MISS && r.getLocation().distanceTo(from) < to.distanceTo(from) - 0.5) continue;
            Vec3 d = to.subtract(from);
            Direction face = Direction.getApproximateNearest(d.x, 0, d.z);
            if (state.getValue(SentryBlock.FACING) != face) l.setBlock(pos, state.setValue(SentryBlock.FACING, face), 3);
            Vec3 dir = d.normalize();
            float dmg = (float) (DEF.p("turret_dmg", 3.5) * ModConfig.get().damageMultiplier);
            m.hurtServer(l, dev.telephoneorder.weapon.DamageKind.TURRET.source(l, null, null), dmg);
            m.setTarget(null);
            Fx.line(l, Fx.dust(0xFF5030, 0.5f), from.add(dir.scale(0.8)), to, 2.0);
            Fx.p(l, ParticleTypes.CRIT, to, 2, 0.1, 0.1, 0.1, 0.1);
            Fx.p(l, ParticleTypes.FLAME, from.add(dir.scale(0.9)), 1, 0.02, 0.02, 0.02, 0.01);
            Fx.sound(l, from, "fire_turret", SoundSource.BLOCKS, 1.0f, 0.95f + l.getRandom().nextFloat() * 0.1f);
            be.ammo--;
            be.cooldown = (int) DEF.p("turret_delay", 4);
            return;
        }
    }
}
