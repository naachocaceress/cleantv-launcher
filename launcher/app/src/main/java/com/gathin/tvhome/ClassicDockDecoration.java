package com.gathin.tvhome;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Spaces the dock row. The row is 2 * {@code slots} equal units wide and each dock icon takes 2 of them, centred
 * (ClassicHome gives the first and last icon the empty units on their side). Those two items are wider than an
 * icon cell, so this pushes their icon back into the cell it belongs to. The icons float on the photo.
 */
public class ClassicDockDecoration extends RecyclerView.ItemDecoration {

    private final ClassicAdapter adapter;
    private final int slots;
    private final float density;

    ClassicDockDecoration(ClassicAdapter adapter, int slots, float density) {
        this.adapter = adapter;
        this.slots = slots;
        this.density = density;
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int pos = parent.getChildAdapterPosition(view);
        int n = adapter.dockCount();
        if (pos < 0 || pos >= n) return;
        outRect.top = (int) (6 * density);
        outRect.bottom = (int) (12 * density);
        float unit = (parent.getMeasuredWidth() - parent.getPaddingLeft() - parent.getPaddingRight()) / (2f * slots);
        int pad = ClassicHome.dockLead(n);
        if (pos == 0) outRect.left = Math.round(pad * unit);
        if (pos == n - 1) outRect.right = Math.round(pad * unit);
    }
}
