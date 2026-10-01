package com.infinitemultiverse.client.vfx;

import net.minecraft.world.phys.Vec3;

/** Choreography for Stands and time stop. Summon/dismiss/awaken carry the Stand's RGB colour in {@code scale}. */
final class StandEffects {
    private static final int WHITE = 0xFFFFFF;
    private static final int VIOLET = 0xC77DFF;
    private static final int DEEP_VIOLET = 0x5B2FB0;
    private static final int GOLD = 0xFFD86B;
    private static final int ICE = 0xBFD4FF;
    private static final int TIME_BLUE = 0x5C8DFF;

    private StandEffects() {
    }

    private static int colorOf(float scale) {
        return scale >= 1f ? ((int) scale) & 0xFFFFFF : VIOLET;
    }

    static void summon(VfxSpawner s, Vec3 base, Vec3 unused, float scale) {
        int color = colorOf(scale);
        int count = s.count(60);
        for (int i = 0; i < count; i++) {
            double angle = s.rand() * Math.PI * 2.0;
            double height = s.rand() * 2.4;
            double r = 0.9 - height * 0.15;
            Vec3 pos = base.add(Math.cos(angle) * r, height, Math.sin(angle) * r);
            Vec3 velocity = new Vec3(-Math.sin(angle) * 0.08, 0.04 + s.rand() * 0.05, Math.cos(angle) * 0.08);
            s.mote(pos, velocity, WHITE, color, 0.12f + s.rand() * 0.06f, 16 + (int) (s.rand() * 8), 0.9f);
        }
        int sparks = s.count(12);
        for (int i = 0; i < sparks; i++) {
            s.spark(base.add(0.0, 1.2, 0.0).add(s.jitter(0.6)), s.randomUnit().scale(0.06), WHITE, color, 0.22f, 12, 0.85f);
        }
    }

    static void dismiss(VfxSpawner s, Vec3 base, Vec3 unused, float scale) {
        int color = colorOf(scale);
        int count = s.count(40);
        for (int i = 0; i < count; i++) {
            Vec3 pos = base.add(s.jitter(0.5).add(0.0, 1.1 + s.rand() * 0.9, 0.0));
            s.mote(pos, new Vec3(0.0, 0.03 + s.rand() * 0.04, 0.0).add(s.jitter(0.02)), color, DEEP_VIOLET, 0.1f, 18, 0.92f);
        }
    }

    static void awaken(VfxSpawner s, Vec3 base, Vec3 unused, float scale) {
        int color = colorOf(scale);
        int count = s.count(90);
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double angle = t * Math.PI * 8.0;
            Vec3 pos = base.add(Math.cos(angle) * 1.1, t * 2.6, Math.sin(angle) * 1.1);
            s.mote(pos, new Vec3(-Math.cos(angle) * 0.03, 0.02, -Math.sin(angle) * 0.03), GOLD, color, 0.13f, 26, 0.94f);
        }
        int burst = s.count(24);
        for (int i = 0; i < burst; i++) {
            s.spark(base.add(0.0, 1.0, 0.0), s.randomUnit().scale(0.22), WHITE, GOLD, 0.25f, 12, 0.82f);
        }
    }

    /** origin = impact point, vector = punch direction. */
    static void punch(VfxSpawner s, Vec3 point, Vec3 direction, float scale) {
        Vec3 dir = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : Vec3.ZERO;
        int sparks = s.count(Math.max(3, Math.round(8 * scale)));
        for (int i = 0; i < sparks; i++) {
            Vec3 velocity = dir.add(s.randomUnit().scale(0.8)).normalize().scale(0.18);
            s.spark(point, velocity, WHITE, VIOLET, 0.15f * Math.max(0.6f, scale), 6, 0.75f);
        }
        s.mote(point, Vec3.ZERO, WHITE, VIOLET, 0.35f * Math.max(0.6f, scale), 5, 1.0f);
    }

    static void heavy(VfxSpawner s, Vec3 point, Vec3 direction, float scale) {
        Vec3 dir = direction.lengthSqr() > 1.0E-6 ? direction.normalize() : new Vec3(0.0, 1.0, 0.0);
        int burst = s.count(36);
        for (int i = 0; i < burst; i++) {
            Vec3 velocity = dir.scale(0.25).add(s.randomUnit().scale(0.22));
            s.spark(point, velocity, WHITE, GOLD, 0.24f, 9, 0.8f);
        }
        int ring = s.count(28);
        Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
        Vec3 a = dir.cross(up).normalize();
        Vec3 b = dir.cross(a).normalize();
        for (int i = 0; i < ring; i++) {
            double angle = Math.PI * 2.0 * i / ring;
            Vec3 radial = a.scale(Math.cos(angle)).add(b.scale(Math.sin(angle)));
            s.mote(point.add(radial.scale(0.3)), radial.scale(0.2), VIOLET, DEEP_VIOLET, 0.16f, 10, 0.82f);
        }
        s.mote(point, Vec3.ZERO, WHITE, GOLD, 0.9f, 6, 1.0f);
    }

    /** Expanding cold shell that sweeps out to the frozen radius. */
    static void timeStop(VfxSpawner s, Vec3 center, Vec3 unused, float radius) {
        float friction = 0.9f;
        int lifetime = 18;
        double reach = (1.0 - Math.pow(friction, lifetime)) / (1.0 - friction);
        double speed = radius / reach;
        int count = s.count(160);
        for (int i = 0; i < count; i++) {
            Vec3 dir = s.randomUnit();
            s.mote(center.add(dir.scale(0.5)), dir.scale(speed), WHITE, TIME_BLUE, 0.3f, lifetime, friction);
        }
        int flash = s.count(16);
        for (int i = 0; i < flash; i++) {
            s.spark(center.add(s.jitter(0.6)), s.randomUnit().scale(0.05), WHITE, ICE, 0.35f, 14, 0.9f);
        }
    }

    /** The shell rushes back in and bursts as time resumes. */
    static void timeResume(VfxSpawner s, Vec3 center, Vec3 unused, float radius) {
        float friction = 0.9f;
        int lifetime = 14;
        double reach = (1.0 - Math.pow(friction, lifetime)) / (1.0 - friction);
        double distance = Math.min(radius, 10.0);
        double speed = distance / reach;
        int count = s.count(110);
        for (int i = 0; i < count; i++) {
            Vec3 dir = s.randomUnit();
            s.mote(center.add(dir.scale(distance)), dir.scale(-speed), TIME_BLUE, GOLD, 0.22f, lifetime, friction);
        }
        int burst = s.count(24);
        for (int i = 0; i < burst; i++) {
            s.spark(center, s.randomUnit().scale(0.3), WHITE, GOLD, 0.3f, 10, 0.8f);
        }
    }

    /** Pink ring that tightens around the bomb. */
    static void bombMark(VfxSpawner s, Vec3 at, Vec3 unused, float scale) {
        int color = colorOf(scale);
        int count = s.count(30);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            Vec3 dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            s.mote(at.add(dir.scale(1.2)), dir.scale(-0.07), WHITE, color, 0.12f, 14, 0.9f);
        }
        s.spark(at, Vec3.ZERO, WHITE, color, 0.5f, 10, 1f);
    }

    /** Spiral that winds backwards into the point, for rewinds. */
    static void rewind(VfxSpawner s, Vec3 at, Vec3 unused, float scale) {
        int color = colorOf(scale);
        int count = s.count(60);
        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double angle = -t * Math.PI * 6.0;
            double r = 1.4 * (1.0 - t) + 0.2;
            Vec3 pos = at.add(Math.cos(angle) * r, (t - 0.5) * 2.0, Math.sin(angle) * r);
            s.mote(pos, new Vec3(Math.sin(angle) * 0.05, 0.0, -Math.cos(angle) * 0.05), color, WHITE, 0.12f, 18, 0.9f);
        }
    }

    /** Expanding green ring of life energy. scale = radius. */
    static void healPulse(VfxSpawner s, Vec3 at, Vec3 unused, float radius) {
        float friction = 0.88f;
        int lifetime = 14;
        double reach = (1.0 - Math.pow(friction, lifetime)) / (1.0 - friction);
        int count = s.count(40);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            Vec3 dir = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            s.mote(at, dir.scale(radius / reach).add(0.0, 0.02, 0.0), 0xC8FF9A, 0x3FA34D, 0.14f, lifetime, friction);
        }
        int rise = s.count(10);
        for (int i = 0; i < rise; i++) {
            s.spark(at.add(s.jitter(radius * 0.5).multiply(1, 0, 1)), new Vec3(0, 0.06, 0), WHITE, 0x8BE36A, 0.15f, 16, 0.95f);
        }
    }

    /** A red ghost silhouette where a creature is about to be. vector points back to its present position, scale = height. */
    static void epitaph(VfxSpawner s, Vec3 future, Vec3 toPresent, float height) {
        int count = s.count(24);
        for (int i = 0; i < count; i++) {
            Vec3 pos = future.add(s.jitter(0.25).multiply(1, 0, 1)).add(0.0, s.rand() * Math.max(0.5f, height), 0.0);
            s.mote(pos, new Vec3(0, 0.005, 0), 0xFF4D6A, 0x7C1222, 0.1f, 40, 0.98f);
        }
        int trail = s.count(10);
        for (int i = 0; i < trail; i++) {
            double t = i / (double) trail;
            s.mote(future.add(toPresent.scale(t)).add(0, 0.2, 0), Vec3.ZERO, 0xE0284A, 0x400810, 0.06f, 30, 1f);
        }
    }

    /** Red shell that collapses and shatters: time skipped. */
    static void timeErase(VfxSpawner s, Vec3 at, Vec3 unused, float scale) {
        int color = colorOf(scale);
        int count = s.count(70);
        for (int i = 0; i < count; i++) {
            Vec3 dir = s.randomUnit();
            s.mote(at.add(dir.scale(2.4)), dir.scale(-0.16), WHITE, color, 0.16f, 12, 0.85f);
        }
        int shards = s.count(20);
        for (int i = 0; i < shards; i++) {
            s.spark(at, s.randomUnit().scale(0.25), color, 0x400810, 0.22f, 10, 0.8f);
        }
    }

    /** Fading afterimage trail. vector = movement, scale = colour. */
    static void afterimage(VfxSpawner s, Vec3 feet, Vec3 movement, float scale) {
        int color = colorOf(scale);
        int count = s.count(14);
        for (int i = 0; i < count; i++) {
            Vec3 pos = feet.add(s.jitter(0.3).multiply(1, 0, 1)).add(0.0, s.rand() * 1.8, 0.0);
            s.mote(pos, movement.scale(-0.15), WHITE, color, 0.14f, 10, 0.85f);
        }
    }
}
