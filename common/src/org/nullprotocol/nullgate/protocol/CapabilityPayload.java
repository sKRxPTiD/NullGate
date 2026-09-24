package org.nullprotocol.nullgate.protocol;

/** Closed, typed capability parameters. No command, path, URI, or arbitrary JSON is accepted. */
public final class CapabilityPayload {
    public enum Kind { NONE, SYSTEM_THEME_SEED }
    public enum ThemeStyle {
        TONAL_SPOT, SPRITZ, VIBRANT, EXPRESSIVE, RAINBOW,
        FRUIT_SALAD, CONTENT, MONOCHROMATIC, FIDELITY
    }

    public final Kind kind;
    public final int seedArgb;
    public final ThemeStyle themeStyle;

    private CapabilityPayload(Kind kind, int seedArgb, ThemeStyle themeStyle) {
        this.kind = kind; this.seedArgb = seedArgb; this.themeStyle = themeStyle;
    }

    public static CapabilityPayload none() {
        return new CapabilityPayload(Kind.NONE, 0, ThemeStyle.TONAL_SPOT);
    }

    public static CapabilityPayload systemThemeSeed(int seedArgb, ThemeStyle style) {
        if ((seedArgb >>> 24) != 0xff) throw new IllegalArgumentException("theme seed must be opaque ARGB");
        if (style == null) throw new NullPointerException("theme style");
        return new CapabilityPayload(Kind.SYSTEM_THEME_SEED, seedArgb, style);
    }
}
