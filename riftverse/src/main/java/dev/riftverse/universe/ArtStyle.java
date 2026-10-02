package dev.riftverse.universe;

/**
 * Visual media a universe can be rendered in. The ids match the art styles of the screen-effect shader; a universe may
 * combine two (a primary and an overlay style).
 */
public enum ArtStyle {
    NONE("None"), SKETCH("Hand-Drawn"), NOIR("Black & White"), PSYCHEDELIC("Psychedelic"), COMIC("Comic Book"), PIXEL("Pixel Art"),
    PAINTED("Painted"), PAPER("Paper"), NEON("Neon Cyber"), DREAM("Dream"), GLITCH("Glitch"), BLUEPRINT("Blueprint"), CLAY("Claymation"),
    WIREFRAME("Wireframe"), INK("Ink"), GLASS("Glass"), WATERCOLOR("Watercolor"), NEGATIVE("Negative"), SILHOUETTE("Silhouette"),
    VHS("VHS"), ASTRAL("Astral"), ORIGAMI("Origami"), XRAY("X-Ray"), FRACTAL("Fractal"), COMIC_PANEL("Comic Panels");

    public final String displayName;

    ArtStyle(String displayName) {
        this.displayName = displayName;
    }

    public static ArtStyle byId(int i) {
        ArtStyle[] v = values();
        return i < 0 || i >= v.length ? NONE : v[i];
    }
}
