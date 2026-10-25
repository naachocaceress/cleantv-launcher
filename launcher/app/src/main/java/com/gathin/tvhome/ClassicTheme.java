package com.gathin.tvhome;

import java.util.Locale;

/**
 * The two home screen layouts of this fork, switched from the settings panel (the third theme, Tiles, is upstream's own
 * home and lives in MainActivity). They have neutral names of their own; the layouts are inspired by well-known TV home
 * screens but are not copies of any of them.
 * <ul>
 *   <li>DOCK: a pinned dock of magnified icons at the bottom, the rest of the apps as banner cards below it.</li>
 *   <li>CIRCLES: one row of round icons hanging from the bottom, with the focused app's name as a big title.</li>
 * </ul>
 * In CIRCLES there is no dock: pinned apps simply come first.
 */
enum ClassicTheme {
    DOCK, CIRCLES;

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Reads a saved key. The first versions called the themes "android" and "apple"; they are mapped to the new ones. */
    static ClassicTheme from(String key) {
        if ("android".equals(key)) return CIRCLES;
        for (ClassicTheme t : values()) if (t.key().equals(key)) return t;
        return DOCK;
    }
}
