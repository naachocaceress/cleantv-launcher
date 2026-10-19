package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The tvOS shelf: a frosted, rounded panel drawn behind the first row of tiles. It is drawn by the grid, under its
 * children, so it scrolls away with the row.
 */
final class ShelfDecoration extends RecyclerView.ItemDecoration {

    private final int columns;
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    ShelfDecoration(int columns, float density) {
        this.columns = columns;
        this.density = density;
        fill.setColor(0x38FFFFFF);          // white at ~22%: reads as frosted glass over a photo
        edge.setColor(0x33FFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (parent.getChildAdapterPosition(child) < columns) {   // the first row
                top = Math.min(top, child.getTop());
                bottom = Math.max(bottom, child.getBottom());
            }
        }
        if (top > bottom) return;   // the first row is not on screen
        float inset = 4 * density;
        rect.set(parent.getPaddingLeft() - 14 * density, top + inset,
                parent.getWidth() - parent.getPaddingRight() + 14 * density, bottom - inset);
        float r = 26 * density;
        c.drawRoundRect(rect, r, r, fill);
        c.drawRoundRect(rect, r, r, edge);
    }
}
