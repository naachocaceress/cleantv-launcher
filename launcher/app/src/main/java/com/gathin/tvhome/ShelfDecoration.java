package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The plate of the TILES theme: a frosted, rounded panel behind the first row, which always holds the pinned apps (the
 * dock of this theme). It is drawn by the grid, under its children, and stays inside the grid's own bounds (anything
 * outside them would be clipped square). When fewer apps are pinned than the plate has room for, the last one is given
 * the empty cells too and is pushed back to its own cell here, so the pinned apps sit at the left of the plate.
 */
final class ShelfDecoration extends RecyclerView.ItemDecoration {

    private final AppAdapter adapter;
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    ShelfDecoration(AppAdapter adapter, float density) {
        this.adapter = adapter;
        this.density = density;
        edge.setColor(0x40FFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density * 1.2f);
    }

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent,
                               @NonNull RecyclerView.State state) {
        int n = adapter.shelfCount();
        if (n == 0 || n >= AppAdapter.TILES_COLUMNS) return;
        if (parent.getChildAdapterPosition(view) != n - 1) return;
        float inner = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        outRect.right = Math.round((AppAdapter.TILES_COLUMNS - n) * inner / AppAdapter.TILES_COLUMNS);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        int n = adapter.shelfCount();
        if (n == 0) return;
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos >= 0 && pos < n) {
                top = Math.min(top, child.getTop());
                bottom = Math.max(bottom, child.getBottom());
            }
        }
        if (top > bottom) return;   // the plate is scrolled out of sight
        rect.set(2 * density, top, parent.getWidth() - 2 * density, bottom);
        // Lighter at the top, like light falling on glass.
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x52FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP));
        float r = 26 * density;
        c.drawRoundRect(rect, r, r, fill);
        c.drawRoundRect(rect, r, r, edge);
    }
}