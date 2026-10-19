package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

/**
 * The plate of the TILES theme: a frosted, rounded panel drawn behind the row that has the focus. It is drawn by the
 * grid, under its children, and stays inside the grid's own bounds (anything outside them would be clipped square).
 */
final class ShelfDecoration extends RecyclerView.ItemDecoration {

    private final int columns;
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private int focusedRow = 0;

    ShelfDecoration(int columns, float density) {
        this.columns = columns;
        this.density = density;
        edge.setColor(0x40FFFFFF);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(density * 1.2f);
    }

    /** The row (0 = first) the plate sits behind. */
    void setFocusedRow(int row) {
        focusedRow = Math.max(0, row);
    }

    @Override
    public void onDraw(@NonNull Canvas c, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        float top = Float.MAX_VALUE, bottom = -Float.MAX_VALUE;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            int pos = parent.getChildAdapterPosition(child);
            if (pos >= 0 && pos / columns == focusedRow) {
                top = Math.min(top, child.getTop());
                bottom = Math.max(bottom, child.getBottom());
            }
        }
        if (top > bottom) return;   // that row is not on screen
        rect.set(2 * density, top + 6 * density, parent.getWidth() - 2 * density, bottom - 4 * density);
        // Lighter at the top, like light falling on glass.
        fill.setShader(new LinearGradient(0, rect.top, 0, rect.bottom, 0x52FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP));
        float r = 30 * density;
        c.drawRoundRect(rect, r, r, fill);
        c.drawRoundRect(rect, r, r, edge);
    }
}