package dev.infinitygauntlet.breach;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Geometry of the floating control panel's button plane, shared by the renderer, the client hit test and the
 * server. The plane faces direction {@code faceYaw} (toward the operator) and leans back by {@link #TILT_DEG}.
 * Coordinates: u to the operator's right, v up along the tilted face, in blocks from the plane centre.
 */
public final class PanelLayout {
    public static final double TILT_DEG = 38;
    public static final double CELL_W = 0.36, CELL_H = 0.19, GAP = 0.035;
    public static final double HEADER = 0.13;

    public final Vec3 center;
    public final Vec3 normal;
    public final Vec3 right;
    public final Vec3 up;

    public PanelLayout(Vec3 slabCenter, float faceYaw) {
        Vec3 n = Vec3.directionFromRotation(0, faceYaw);
        Vec3 r = new Vec3(n.z, 0, -n.x);
        double t = Math.toRadians(TILT_DEG);
        this.up = new Vec3(0, Math.cos(t), 0).subtract(n.scale(Math.sin(t)));
        this.normal = n.scale(Math.cos(t)).add(0, Math.sin(t), 0);
        this.right = r;
        this.center = slabCenter.add(this.normal.scale(0.06));
    }

    public static double gridWidth() {
        return PanelControl.COLUMNS * CELL_W + (PanelControl.COLUMNS - 1) * GAP;
    }

    public static double gridHeight() {
        return 4 * CELL_H + 3 * GAP + HEADER;
    }

    /** {u0, v0, u1, v1} of a button in plane coordinates. */
    public static double[] rect(PanelControl c) {
        double left = -gridWidth() / 2 + c.column * (CELL_W + GAP);
        double top = gridHeight() / 2 - HEADER - c.row * (CELL_H + GAP);
        double width = c.span() * CELL_W + (c.span() - 1) * GAP;
        return new double[]{left, top - CELL_H, left + width, top};
    }

    public Vec3 toWorld(double u, double v) {
        return center.add(right.scale(u)).add(up.scale(v));
    }

    /** The button the ray from {@code eye} along {@code dir} points at, within {@code reach}. */
    @Nullable
    public PanelControl hit(Vec3 eye, Vec3 dir, double reach) {
        double denom = dir.dot(normal);
        if (denom > -1.0E-4) {
            return null; // looking at the back or edge-on
        }
        double t = center.subtract(eye).dot(normal) / denom;
        if (t < 0 || t > reach) {
            return null;
        }
        Vec3 local = eye.add(dir.scale(t)).subtract(center);
        double u = local.dot(right), v = local.dot(up);
        for (PanelControl c : PanelControl.values()) {
            double[] r = rect(c);
            if (u >= r[0] && u <= r[2] && v >= r[1] && v <= r[3]) {
                return c;
            }
        }
        return null;
    }
}
