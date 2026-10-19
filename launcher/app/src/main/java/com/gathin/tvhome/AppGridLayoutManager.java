package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Grid layout without scroll-to-focus. When a child takes focus, RecyclerView asks its LayoutManager to scroll it into
 * the padded area; in the DOCK theme the top padding is huge (it pushes the dock row to the bottom of the screen) and
 * in ANDROID / APPLE the grid hangs from the bottom, so that logic fights with MainActivity's own scrolling and the
 * grid drifts a few pixels with every move. MainActivity scrolls on each focus change, at once, so this one stays out.
 */
final class AppGridLayoutManager extends GridLayoutManager {

    AppGridLayoutManager(Context context, int spanCount) {
        super(context, spanCount);
    }

    /**
     * Always lay out a screen's worth of rows beyond the visible area. The row below the focused one then exists when
     * the user presses Down, so focus can move to it (and be scrolled to) without waiting for a layout pass.
     */
    @Override
    protected void calculateExtraLayoutSpace(@NonNull RecyclerView.State state, @NonNull int[] extraLayoutSpace) {
        int extra = getHeight();
        extraLayoutSpace[0] = extra;
        extraLayoutSpace[1] = extra;
    }

    @Override
    public boolean requestChildRectangleOnScreen(@NonNull RecyclerView parent, @NonNull View child, @NonNull Rect rect,
                                                 boolean immediate, boolean focusedChildVisible) {
        return false;
    }
}
