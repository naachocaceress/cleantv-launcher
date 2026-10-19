package com.gathin.tvhome;

import java.util.Locale;

/**
 * The three home screen layouts, switched from the config sidebar.
 * <ul>
 *   <li>DOCK: a pinned dock of magnified icons at the bottom, the rest of the apps as banner cards below it.</li>
 *   <li>ANDROID: Google TV style, every app as a wide banner card with a white focus ring.</li>
 *   <li>APPLE: tvOS style, every app as a large rounded icon with its name underneath when focused.</li>
 * </ul>
 * In ANDROID and APPLE there is no dock: pinned apps simply come first.
 */
enum Theme {
    DOCK, ANDROID, APPLE;

    String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    static Theme from(String key) {
        for (Theme t : values()) if (t.key().equals(key)) return t;
        return DOCK;
    }
}
