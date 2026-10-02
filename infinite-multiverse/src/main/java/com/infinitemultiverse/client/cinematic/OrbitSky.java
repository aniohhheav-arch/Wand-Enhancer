package com.infinitemultiverse.client.cinematic;

import static com.infinitemultiverse.client.cinematic.FxDraw.argb;

import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** In Orbit, the planet hangs below you: an ocean sphere with continents, drifting clouds, an atmosphere glow and a sun. */
final class OrbitSky {
    private OrbitSky() {
    }

    static void render(FxDraw d, Vec3 cam, double time) {
        double radius = 260;
        Vec3 earth = new Vec3(cam.x, 70 - radius, cam.z);
        d.ink();
        d.sphere(earth, radius, 0x0B2E6A, 1f, 1f, 24, 48);
        RandomSource r = RandomSource.create(1234);
        for (int i = 0; i < 22; i++) {
            double th = r.nextDouble() * Math.PI * 2, phi = 0.15 + r.nextDouble() * 1.2;
            Vec3 n = new Vec3(Math.cos(th) * Math.sin(phi), Math.cos(phi), Math.sin(th) * Math.sin(phi)).normalize();
            int land = i % 4 == 0 ? 0x8A7A4A : 0x2E6A2E;
            d.disc(earth.add(n.scale(radius + 0.5)), n, 0, 18 + r.nextDouble() * 34, argb(land, 1f), argb(land, 0.9f), 14);
        }
        d.glow();
        double drift = time * 0.002;
        for (int i = 0; i < 26; i++) {
            double th = r.nextDouble() * Math.PI * 2 + drift, phi = 0.1 + r.nextDouble() * 1.3;
            Vec3 n = new Vec3(Math.cos(th) * Math.sin(phi), Math.cos(phi), Math.sin(th) * Math.sin(phi)).normalize();
            d.disc(earth.add(n.scale(radius + 2)), n, 0, 10 + r.nextDouble() * 24, argb(0xFFFFFF, 0.35f), argb(0xFFFFFF, 0), 12);
        }
        d.sphere(earth, radius * 1.03, 0x6AB0FF, 0f, 0.7f, 24, 48);
        Vec3 sun = cam.add(new Vec3(0.6, 0.35, -0.7).normalize().scale(300));
        d.flare(sun, 30, 0xFFF4D0, 1f);
        d.flare(sun, 80, 0xFFC060, 0.25f);
    }
}
