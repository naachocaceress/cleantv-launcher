package com.gathin.tvhome;

import java.util.Locale;

/**
 * The three home screen layouts, switched from the settings panel. They have neutral names of their own; the
 * layouts are inspired by well-known TV home screens but are not copies of any of them.
 * <ul>
 *   <li>DOCK: a pinned dock of magnified icons at the bottom, the rest of the apps as banner cards below it.</li>
 *   <li>CIRCLES: one row of round icons hanging from the bottom, with the focused app's name as a big title.</li>
 *   <li>TILES: wide rounded tiles on a translucent plate, the focused one grows and shows its name.</li>
 * </ul>
 * In CIRCLES and TILES there is no dock: pinned apps simply come first.
 */
enum Theme {
    DOCK, CIRCLES, TILES;

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Reads a saved key. The first versions called the themes "android" and "apple"; they are mapped to the new ones. */
    static Theme from(String key) {
        if ("android".equals(key)) return CIRCLES;
        if ("apple".equals(key)) return TILES;
        for (Theme t : values()) if (t.key().equals(key)) return t;
        return DOCK;
    }
}