package com.softbankrobotics.pepper.pepperGPT;

/** Solid surfaces and readable foregrounds for the existing application themes. */
final class ThemePalette {
    private static final ThemePalette GLASS = new ThemePalette(
            0xff2e3440, 0xff3b4655, 0xffeceff4, 0xffc4d3df, 0xff9ed9e5, 0xff485b6d, 0xff586b7d, true);
    private static final ThemePalette DARK = new ThemePalette(
            0xff121212, 0xff222222, 0xfff6f6f6, 0xffc6cbd0, 0xffb3d29e, 0xff303d33, 0xff474747, true);
    private static final ThemePalette SOFT = new ThemePalette(
            0xfff0f4f8, 0xfff9fbfd, 0xff193245, 0xff526b7b, 0xff146b64, 0xffe8f5f2, 0xffd6e0ea, false);

    final int background, surface, ink, muted, accent, tint, line;
    final boolean darkSurface;

    private ThemePalette(int background, int surface, int ink, int muted, int accent, int tint, int line,
            boolean darkSurface) {
        this.background = background;
        this.surface = surface;
        this.ink = ink;
        this.muted = muted;
        this.accent = accent;
        this.tint = tint;
        this.line = line;
        this.darkSurface = darkSurface;
    }

    static ThemePalette named(String name) {
        if ("dark".equals(name)) return DARK;
        if ("soft".equals(name)) return SOFT;
        return GLASS;
    }
}
