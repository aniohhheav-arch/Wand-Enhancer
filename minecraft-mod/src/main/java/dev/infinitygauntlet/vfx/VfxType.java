package dev.infinitygauntlet.vfx;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Client-side effect primitives of the cinematic VFX framework. Field meanings are documented per constant. */
public enum VfxType {
    /** Expanding ring. a = centre, b = ring normal, scale = final radius. */
    SHOCKWAVE,
    /** Expanding translucent sphere shell. a = centre, scale = final radius. */
    SPHERE_PULSE,
    /** Sphere that collapses to a point then flashes. a = centre, scale = start radius. */
    IMPLOSION,
    /** Thick textured energy beam. a = start, b = end, scale = width. */
    BEAM,
    /** Jagged lightning arc. a = start, b = end, scale = jaggedness. */
    ARC,
    /** Short-lived dimensional rupture that opens, flares and collapses. a = centre, scale = radius. */
    RUPTURE_BURST,
    /** Rotating rune circle on the ground. a = centre, scale = radius. */
    GROUND_GLYPH,
    /** Pulsing energy connection between two points. a, b = endpoints. */
    LINK,
    /** Entity dissolving into energy. a = feet position, b = (width, height, 0). */
    DISSOLVE,
    /** Refraction-like lens shell. a = centre, scale = radius. */
    DISTORTION,
    /** Spherical burst of sparks and smoke. a = centre, scale = radius. */
    SPARK_BURST,
    /** Rotating clock dial on the ground (Time Stone glyph). a = centre, scale = radius. */
    CLOCK_GLYPH,
    /** Trailing afterimages of a living entity. seed = entity id, duration = ticks to keep trailing. */
    AFTERIMAGES,
    /** Emerald motes hanging motionless in the air. a = centre, scale = radius. */
    SUSPENDED_FIELD,
    /** Vertical stack of counter-rotating clock rings. a = centre, scale = radius. */
    CLOCK_RINGS,
    /** Glowing fissures splitting radially through the ground. a = centre, scale = radius. */
    GROUND_CRACKS,
    /** Power beam: a core with two energy strands coiling around it. a = start, b = end, scale = width. */
    HELIX_BEAM,
    /** Purple fireball with a churning shell, smoke and a ground ring. a = centre, scale = radius. */
    POWER_EXPLOSION,
    /** Energy streaming into a point while a core swells. a = centre, scale = gathering radius. */
    CHARGE,
    /** Glowing ribbon trailing behind an entity. seed = entity id, scale = width. */
    ENTITY_TRAIL,
    /** Lock-on reticle that closes in on an entity. seed = entity id, a = fallback position, scale = size. */
    LOCK_RETICLE,
    /** Real block chunks blown out of the ground. a = centre, color = block state id, color2 = count, scale = launch power. */
    DEBRIS,
    /** Crystal shards bursting outward. a = centre, scale = radius. */
    SHARD_NOVA,
    /** Impact frame of a punch: white star, speed lines and an inverted flash. a = centre, b = blow direction, scale = size. */
    IMPACT_STAR,
    /** Rising, mushrooming smoke column. a = base, scale = height. */
    SMOKE_COLUMN;

    public static final StreamCodec<ByteBuf, VfxType> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], VfxType::ordinal);
}
