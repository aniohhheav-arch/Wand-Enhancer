package dev.arcana.entity;

/** The 3D cinematic effect models (assets/arcana/geo/entity/fx_*.geo.json). Ordinal is synced to clients. */
public enum FxKind {
    BLACK_HOLE("black_hole"), SUN("sun"), ICE_SPIRE("ice_spire"), CRIMSON_SPEARS("crimson_spears"), STORM_CLOUD("storm_cloud"),
    CLOCK("clock"), SIGIL("sigil"), WORLD_TREE("world_tree"), RIFT("rift");

    public final String id;

    FxKind(String id) {
        this.id = id;
    }

    public static FxKind byOrdinal(int i) {
        FxKind[] v = values();
        return v[Math.floorMod(i, v.length)];
    }
}
