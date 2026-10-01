package com.infinitemultiverse.client.vfx;

import net.minecraft.world.phys.Vec3;

/**
 * Choreography for the Multiverse Core abilities. Speeds are derived from particle friction so rings and spirals
 * actually reach the gameplay radius they represent: with per-tick friction {@code f} over {@code L} ticks a particle
 * travels {@code v * (1 - f^L) / (1 - f)}.
 */
final class CoreEffects {
    private static final int WHITE = 0xFFFFFF;
    private static final int VIOLET = 0x9B5CFF;
    private static final int CYAN = 0x5CE1FF;
    private static final int SKY = 0xBDEBFF;
    private static final int BLUE = 0x3A7BFF;
    private static final int ORANGE = 0xFF8A3D;
    private static final int GOLD = 0xFFC94D;
    private static final int DEEP_RED = 0xB0201A;
    private static final int AEGIS = 0x4DE8FF;
    private static final int AEGIS_DEEP = 0x2058FF;
    private static final int SLATE = 0x6B7A8F;
    private static final int AMBER = 0xFFB020;
    private static final int PALE_GOLD = 0xFFF1C2;

    private CoreEffects() {
    }

    private static double reach(double friction, int lifetime) {
        return (1.0 - Math.pow(friction, lifetime)) / (1.0 - friction);
    }

    /** origin = chest at departure, vector = displacement to arrival. */
    static void phaseStep(VfxSpawner s, Vec3 origin, Vec3 delta, float scale) {
        Vec3 end = origin.add(delta);
        int trail = s.count(48);
        for (int i = 0; i < trail; i++) {
            double t = (i + s.rand()) / trail;
            Vec3 pos = origin.add(delta.scale(t)).add(s.jitter(0.22));
            s.mote(pos, s.randomUnit().scale(0.02), VIOLET, CYAN, 0.09f + s.rand() * 0.08f, 10 + (int) (s.rand() * 12), 0.85f);
        }
        int burst = s.count(26);
        for (int i = 0; i < burst; i++) {
            Vec3 dir = s.randomUnit();
            s.mote(origin.add(dir.scale(0.2)), dir.scale(0.18), WHITE, VIOLET, 0.14f, 12 + (int) (s.rand() * 6), 0.82f);
        }
        for (int i = 0; i < burst; i++) {
            Vec3 dir = s.randomUnit();
            s.mote(end.add(dir.scale(1.3)), dir.scale(-0.11), CYAN, WHITE, 0.12f, 12, 0.9f);
        }
        int sparks = s.count(10);
        for (int i = 0; i < sparks; i++) {
            s.spark(end.add(s.jitter(0.45)), s.randomUnit().scale(0.04), WHITE, CYAN, 0.2f, 10, 0.8f);
        }
    }

    /** origin = feet at launch, vector = launch velocity. */
    static void kineticLeap(VfxSpawner s, Vec3 origin, Vec3 velocity, float scale) {
        int ring = s.count(32);
        for (int i = 0; i < ring; i++) {
            double angle = Math.PI * 2.0 * i / ring;
            Vec3 dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            s.mote(origin.add(dir.scale(0.35)), dir.scale(0.28).add(0.0, 0.02, 0.0), WHITE, SKY, 0.13f, 14, 0.82f);
        }
        Vec3 behind = velocity.lengthSqr() > 1.0E-6 ? velocity.normalize().scale(-0.08) : Vec3.ZERO;
        int streak = s.count(18);
        for (int i = 0; i < streak; i++) {
            Vec3 pos = origin.add(0.0, s.rand() * 1.6, 0.0).add(s.jitter(0.3));
            s.mote(pos, behind.add(s.randomUnit().scale(0.02)), SKY, BLUE, 0.09f, 16, 0.9f);
        }
        int sparks = s.count(8);
        for (int i = 0; i < sparks; i++) {
            s.spark(origin.add(s.jitter(0.4)).add(0.0, 0.1, 0.0), new Vec3(0.0, 0.08 + s.rand() * 0.1, 0.0), WHITE, SKY, 0.16f, 9, 0.85f);
        }
    }

    /** origin = ground centre, scale = gameplay radius. */
    static void shockwave(VfxSpawner s, Vec3 center, Vec3 unused, float radius) {
        float friction = 0.9f;
        int lifetime = 16;
        double reach = reach(friction, lifetime);
        emitRing(s, center.add(0.0, 0.1, 0.0), s.count(72), radius / reach, ORANGE, DEEP_RED, 0.22f, lifetime, friction);
        emitRing(s, center.add(0.0, 0.55, 0.0), s.count(48), radius * 0.7 / reach, GOLD, ORANGE, 0.16f, lifetime, friction);

        int flash = s.count(14);
        for (int i = 0; i < flash; i++) {
            s.spark(center.add(0.0, 0.6, 0.0).add(s.jitter(0.3)), s.randomUnit().scale(0.15), WHITE, ORANGE, 0.35f, 8, 0.75f);
        }
        int column = s.count(20);
        for (int i = 0; i < column; i++) {
            Vec3 offset = s.jitter(0.6).multiply(1.0, 0.0, 1.0);
            s.mote(center.add(offset), new Vec3(0.0, 0.15 + s.rand() * 0.25, 0.0), GOLD, DEEP_RED, 0.12f, 18, 0.88f);
        }
    }

    private static void emitRing(VfxSpawner s, Vec3 center, int count, double speed, int c0, int c1, float size, int lifetime, float friction) {
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * (i + s.rand() * 0.3) / count;
            Vec3 dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            s.mote(center.add(dir.scale(0.4)), dir.scale(speed), c0, c1, size, lifetime, friction);
        }
    }

    /** origin = body centre, scale = shell radius. Sent periodically while the field is up. */
    static void aegisPulse(VfxSpawner s, Vec3 center, Vec3 unused, float radius) {
        int count = s.count(20);
        for (int i = 0; i < count; i++) {
            double theta = s.rand() * Math.PI * 2.0;
            double polar = Math.acos(2.0 * s.rand() - 1.0);
            Vec3 dir = new Vec3(Math.sin(polar) * Math.cos(theta), Math.cos(polar) * 1.3, Math.sin(polar) * Math.sin(theta));
            Vec3 tangent = new Vec3(-Math.sin(theta), 0.0, Math.cos(theta)).scale(0.04);
            s.mote(center.add(dir.scale(radius)), tangent, AEGIS, AEGIS_DEEP, 0.07f + s.rand() * 0.04f, 12, 0.95f);
        }
    }

    /** origin = point on the shell, vector = outward direction toward the attacker. */
    static void aegisImpact(VfxSpawner s, Vec3 point, Vec3 direction, float scale) {
        int sparks = s.count(16);
        for (int i = 0; i < sparks; i++) {
            Vec3 velocity = direction.add(s.randomUnit().scale(0.6)).normalize().scale(0.2);
            s.spark(point, velocity, WHITE, AEGIS, 0.18f, 9, 0.78f);
        }
        int glow = s.count(10);
        for (int i = 0; i < glow; i++) {
            s.mote(point.add(s.jitter(0.25)), s.randomUnit().scale(0.02), AEGIS, AEGIS_DEEP, 0.2f, 10, 0.9f);
        }
    }

    static void aegisCollapse(VfxSpawner s, Vec3 center, Vec3 unused, float radius) {
        int count = s.count(36);
        for (int i = 0; i < count; i++) {
            Vec3 dir = s.randomUnit().multiply(1.0, 1.3, 1.0);
            s.mote(center.add(dir.scale(radius)), dir.scale(-0.06).add(0.0, -0.02, 0.0), AEGIS, SLATE, 0.11f, 18, 0.9f);
        }
    }

    /** origin = ground centre, scale = gameplay radius. Inward amber spiral around a slowly turning clock dial. */
    static void temporalDrag(VfxSpawner s, Vec3 center, Vec3 unused, float radius) {
        float friction = 0.92f;
        int lifetime = 24;
        double reach = reach(friction, lifetime);
        int spiral = s.count(70);
        for (int i = 0; i < spiral; i++) {
            double theta = s.rand() * Math.PI * 2.0;
            double r = radius * (0.6 + 0.4 * s.rand());
            Vec3 pos = center.add(Math.cos(theta) * r, 0.2 + s.rand() * 1.6, Math.sin(theta) * r);
            double speed = r * 0.7 / reach;
            Vec3 inward = new Vec3(-Math.cos(theta), 0.0, -Math.sin(theta)).scale(speed);
            Vec3 tangent = new Vec3(-Math.sin(theta), 0.0, Math.cos(theta)).scale(speed * 0.8);
            s.mote(pos, inward.add(tangent), AMBER, PALE_GOLD, 0.12f, lifetime, friction);
        }
        double dialRadius = radius * 0.5;
        int dial = s.count(48);
        for (int i = 0; i < dial; i++) {
            double theta = Math.PI * 2.0 * i / dial;
            Vec3 pos = center.add(Math.cos(theta) * dialRadius, 0.05, Math.sin(theta) * dialRadius);
            Vec3 tangent = new Vec3(-Math.sin(theta), 0.0, Math.cos(theta)).scale(0.015);
            s.mote(pos, tangent, GOLD, AMBER, 0.08f, 30, 0.98f);
        }
        int marks = s.count(12);
        for (int i = 0; i < marks; i++) {
            double theta = Math.PI * 2.0 * i / marks;
            Vec3 pos = center.add(Math.cos(theta) * dialRadius, 0.1, Math.sin(theta) * dialRadius);
            s.spark(pos, Vec3.ZERO, WHITE, GOLD, 0.16f, 26, 1.0f);
        }
    }
}
