package com.mvx.agriculture.theme;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The soft-UI surface: one flat colour lit from the top-left.
 *
 * Android elevation casts a single shadow, which cannot express this look — it
 * needs a light highlight up-left and a dark shadow down-right at the same time.
 * Both are drawn here with blurred shadow layers so edges stay soft rather than
 * reading as offset rectangles.
 */
public class NeumorphDrawable extends Drawable {

    /** Raised sits above the surface; pressed is carved into it, for inputs. */
    public enum Style { RAISED, PRESSED }

    private final Paint surfacePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint darkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lightPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final int surfaceColor;
    private final int darkShadow;
    private final int lightShadow;
    private final float cornerRadius;
    private final float offset;
    private final float blur;
    private final Style style;

    private final RectF bounds = new RectF();

    public NeumorphDrawable(int surfaceColor, int lightShadow, int darkShadow,
                            float cornerRadius, float elevation, Style style) {
        this.surfaceColor = surfaceColor;
        this.lightShadow = lightShadow;
        this.darkShadow = darkShadow;
        this.cornerRadius = cornerRadius;
        this.style = style;
        this.offset = elevation;
        this.blur = elevation * 1.6f;

        surfacePaint.setColor(surfaceColor);
        surfacePaint.setStyle(Paint.Style.FILL);

        // Shadow layers need software rendering; the owning view disables hardware
        // acceleration for itself in SoftTheme.
        darkPaint.setColor(surfaceColor);
        darkPaint.setShadowLayer(blur, offset, offset, darkShadow);

        lightPaint.setColor(surfaceColor);
        lightPaint.setShadowLayer(blur, -offset, -offset, lightShadow);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        bounds.set(getBounds());
        if (bounds.isEmpty()) {
            return;
        }

        if (style == Style.RAISED) {
            // Inset so the blur has room to fall inside the view's own bounds.
            bounds.inset(offset + blur / 2f, offset + blur / 2f);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, darkPaint);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, lightPaint);
            canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, surfacePaint);
            return;
        }

        // Pressed: fill first, then clip to the shape and let the same two shadows
        // fall inwards, which reads as a groove cut into the surface.
        canvas.drawRoundRect(bounds, cornerRadius, cornerRadius, surfacePaint);
        int saved = canvas.save();
        android.graphics.Path clip = new android.graphics.Path();
        clip.addRoundRect(bounds, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW);
        canvas.clipPath(clip);

        RectF outer = new RectF(bounds);
        outer.inset(-offset * 2f, -offset * 2f);
        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setStyle(Paint.Style.STROKE);
        inner.setStrokeWidth(offset * 2f);

        inner.setColor(Color.TRANSPARENT);
        inner.setShadowLayer(blur, offset, offset, darkShadow);
        canvas.drawRoundRect(outer, cornerRadius, cornerRadius, inner);

        inner.setShadowLayer(blur, -offset, -offset, lightShadow);
        canvas.drawRoundRect(outer, cornerRadius, cornerRadius, inner);

        canvas.restoreToCount(saved);
    }

    @Override
    public void setAlpha(int alpha) {
        surfacePaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        surfacePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    /** Padding the content needs so it clears the raised edge. */
    public int inset() {
        return Math.round(offset + blur / 2f);
    }

    @Override
    public boolean getPadding(@NonNull Rect padding) {
        int i = style == Style.RAISED ? inset() : Math.round(offset);
        padding.set(i, i, i, i);
        return true;
    }
}
