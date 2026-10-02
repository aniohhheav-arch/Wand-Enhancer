package com.infinitemultiverse.client.vfx;

import net.minecraft.world.phys.Vec3;

/** Generic power effects. Colour arrives in {@code scale}; any extra parameter rides in {@code vector}. */
final class PowerEffects {
    private static final int WHITE = 0xFFFFFF;

    private PowerEffects() {
    }

    private static int color(float scale) {
        return scale >= 1f ? ((int) scale) & 0xFFFFFF : 0x7FD6FF;
    }

    private static int darker(int c) {
        return ((c >> 17 & 0x7F) << 16) | ((c >> 9 & 0x7F) << 8) | (c >> 1 & 0x7F);
    }

    private static Vec3 perpendicular(Vec3 dir) {
        Vec3 up = Math.abs(dir.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        return dir.cross(up).normalize();
    }

    /** origin = start, vector = end - start. */
    static void beam(VfxSpawner s, Vec3 start, Vec3 delta, float scale) {
        int c = color(scale);
        double length = delta.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 dir = delta.normalize();
        int count = s.count((int) Math.min(120, length * 6));
        for (int i = 0; i < count; i++) {
            Vec3 pos = start.add(delta.scale(s.rand())).add(s.jitter(0.06));
            s.mote(pos, dir.scale(0.02), WHITE, c, 0.12f, 8 + (int) (s.rand() * 4), 0.9f);
        }
        burst(s, start.add(delta), Vec3.ZERO, scale);
    }

    static void beamHeavy(VfxSpawner s, Vec3 start, Vec3 delta, float scale) {
        int c = color(scale);
        double length = delta.length();
        if (length < 1.0E-3) {
            return;
        }
        Vec3 dir = delta.normalize();
        Vec3 a = perpendicular(dir);
        Vec3 b = dir.cross(a).normalize();
        int count = s.count((int) Math.min(260, length * 10));
        for (int i = 0; i < count; i++) {
            double t = s.rand();
            double angle = s.rand() * Math.PI * 2;
            double r = 0.2 + s.rand() * 0.9;
            Vec3 pos = start.add(delta.scale(t)).add(a.scale(Math.cos(angle) * r)).add(b.scale(Math.sin(angle) * r));
            s.mote(pos, dir.scale(0.05), WHITE, c, 0.25f, 12 + (int) (s.rand() * 6), 0.9f);
        }
        for (int i = 0; i < s.count(30); i++) {
            s.spark(start.add(delta.scale(s.rand())), s.randomUnit().scale(0.08), WHITE, c, 0.3f, 8, 0.85f);
        }
    }

    /** origin = point; several crossing slash lines. */
    static void slash(VfxSpawner s, Vec3 at, Vec3 unused, float scale) {
        int c = color(scale);
        for (int k = 0; k < 3; k++) {
            Vec3 dir = s.randomUnit();
            int count = s.count(14);
            for (int i = 0; i < count; i++) {
                double t = (i / (double) count - 0.5) * 2.4;
                s.spark(at.add(dir.scale(t)), Vec3.ZERO, WHITE, c, 0.13f, 6, 1f);
            }
        }
    }

    /** origin = centre, vector.x = radius; a swirling orb. */
    static void orb(VfxSpawner s, Vec3 at, Vec3 param, float scale) {
        int c = color(scale);
        double radius = Math.max(0.5, param.x);
        int count = s.count(40);
        for (int i = 0; i < count; i++) {
            Vec3 dir = s.randomUnit();
            Vec3 tangent = dir.cross(new Vec3(0, 1, 0)).scale(0.12);
            s.mote(at.add(dir.scale(radius)), dir.scale(-0.1).add(tangent), c, WHITE, 0.16f, 10, 0.88f);
        }
        s.mote(at, Vec3.ZERO, WHITE, c, (float) (0.5 + radius * 0.3), 6, 1f);
    }

    static void burst(VfxSpawner s, Vec3 at, Vec3 unused, float scale) {
        int c = color(scale);
        for (int i = 0; i < s.count(22); i++) {
            s.spark(at, s.randomUnit().scale(0.2), WHITE, c, 0.2f, 9, 0.8f);
        }
        for (int i = 0; i < s.count(12); i++) {
            s.mote(at.add(s.jitter(0.3)), s.randomUnit().scale(0.05), c, darker(c), 0.2f, 12, 0.88f);
        }
    }

    /** origin = start, vector = end - start; jagged lightning. */
    static void bolt(VfxSpawner s, Vec3 start, Vec3 delta, float scale) {
        int c = color(scale);
        double length = delta.length();
        if (length < 1.0E-3) {
            return;
        }
        int segments = Math.max(4, (int) (length * 1.5));
        Vec3 previous = start;
        for (int i = 1; i <= segments; i++) {
            Vec3 next = start.add(delta.scale(i / (double) segments));
            if (i < segments) {
                next = next.add(s.jitter(0.35));
            }
            Vec3 seg = next.subtract(previous);
            int dots = s.count(6);
            for (int k = 0; k < dots; k++) {
                s.spark(previous.add(seg.scale(k / (double) dots)), Vec3.ZERO, WHITE, c, 0.12f, 5, 1f);
            }
            previous = next;
        }
    }

    /** origin = feet; a rising aura column. */
    static void aura(VfxSpawner s, Vec3 feet, Vec3 unused, float scale) {
        int c = color(scale);
        for (int i = 0; i < s.count(16); i++) {
            double angle = s.rand() * Math.PI * 2;
            Vec3 pos = feet.add(Math.cos(angle) * 0.55, s.rand() * 1.9, Math.sin(angle) * 0.55);
            s.mote(pos, new Vec3(0, 0.05 + s.rand() * 0.04, 0), c, darker(c), 0.12f, 14, 0.92f);
        }
    }

    /** origin = centre, vector = facing; a glowing circular mandala. */
    static void mandala(VfxSpawner s, Vec3 at, Vec3 facing, float scale) {
        int c = color(scale);
        Vec3 normal = facing.lengthSqr() > 1.0E-6 ? facing.normalize() : new Vec3(0, 0, 1);
        Vec3 a = perpendicular(normal);
        Vec3 b = normal.cross(a).normalize();
        for (double r : new double[]{0.6, 1.1, 1.5}) {
            int count = s.count((int) (24 * r));
            for (int i = 0; i < count; i++) {
                double angle = Math.PI * 2 * i / count;
                s.mote(at.add(a.scale(Math.cos(angle) * r)).add(b.scale(Math.sin(angle) * r)), Vec3.ZERO, WHITE, c, 0.08f, 10, 1f);
            }
        }
        for (int k = 0; k < 6; k++) {
            double angle = Math.PI * k / 3;
            Vec3 spoke = a.scale(Math.cos(angle)).add(b.scale(Math.sin(angle)));
            for (int i = 0; i < s.count(6); i++) {
                s.spark(at.add(spoke.scale(0.3 + i * 0.2)), Vec3.ZERO, WHITE, c, 0.1f, 10, 1f);
            }
        }
    }

    /** origin = centre, vector = normal; a sparking portal ring of radius 1.4. */
    static void portalRing(VfxSpawner s, Vec3 at, Vec3 normalIn, float scale) {
        int c = color(scale);
        Vec3 normal = normalIn.lengthSqr() > 1.0E-6 ? normalIn.normalize() : new Vec3(0, 0, 1);
        Vec3 a = perpendicular(normal);
        Vec3 b = normal.cross(a).normalize();
        int count = s.count(40);
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * (i + s.rand()) / count;
            Vec3 radial = a.scale(Math.cos(angle)).add(b.scale(Math.sin(angle) * 1.5));
            Vec3 tangent = a.scale(-Math.sin(angle)).add(b.scale(Math.cos(angle)));
            s.spark(at.add(radial.scale(1.2)), tangent.scale(0.12).add(radial.scale(0.03)), 0xFFE6A0, c, 0.12f, 8, 0.9f);
        }
    }

    static void frost(VfxSpawner s, Vec3 at, Vec3 param, float scale) {
        int c = color(scale);
        double radius = Math.max(1.0, param.x);
        for (int i = 0; i < s.count(50); i++) {
            Vec3 pos = at.add(s.jitter(radius).multiply(1, 0.4, 1));
            s.spark(pos, new Vec3(0, -0.01, 0), WHITE, c, 0.12f, 20, 0.95f);
        }
    }

    /** origin = centre, vector.x = radius: expanding sphere shell. */
    static void domainOpen(VfxSpawner s, Vec3 at, Vec3 param, float scale) {
        int c = color(scale);
        double radius = Math.max(2.0, param.x);
        float friction = 0.9f;
        int lifetime = 16;
        double reach = (1.0 - Math.pow(friction, lifetime)) / (1.0 - friction);
        for (int i = 0; i < s.count(220); i++) {
            Vec3 dir = s.randomUnit();
            s.mote(at, dir.scale(radius / reach), WHITE, c, 0.3f, lifetime, friction);
        }
    }

    static void domainClose(VfxSpawner s, Vec3 at, Vec3 param, float scale) {
        int c = color(scale);
        double radius = Math.max(2.0, param.x);
        for (int i = 0; i < s.count(160); i++) {
            Vec3 dir = s.randomUnit();
            s.mote(at.add(dir.scale(radius)), dir.scale(-0.08), c, darker(c), 0.25f, 14, 0.92f);
        }
    }
}
