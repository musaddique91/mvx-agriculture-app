package com.mvx.agriculture.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;

import androidx.core.content.ContextCompat;

import com.mvx.agriculture.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputLayout;

/**
 * Re-skins a view tree into soft UI.
 *
 * Material's card, button and text-field backgrounds are baked into their own
 * drawables, so a theme alone cannot express the dual-shadow look. This walks the
 * tree once after inflation and swaps those backgrounds for
 * {@link NeumorphDrawable}, leaving every other attribute — colours, spacing,
 * behaviour — to the theme where it belongs.
 */
public final class SoftTheme {

    private SoftTheme() {
    }

    public static boolean isActive(Context context) {
        return AppTheme.current(context) == AppTheme.SOFT;
    }

    /** Applies the skin if the soft theme is selected; otherwise does nothing. */
    public static void applyIfActive(View root) {
        if (root == null || !isActive(root.getContext())) {
            return;
        }
        skin(root);
    }

    private static void skin(View view) {
        Context context = view.getContext();
        int surface = ContextCompat.getColor(context, R.color.soft_surface);
        int light = ContextCompat.getColor(context, R.color.soft_light);
        int dark = ContextCompat.getColor(context, R.color.soft_dark);

        if (view instanceof MaterialCardView) {
            MaterialCardView card = (MaterialCardView) view;
            card.setCardBackgroundColor(Color.TRANSPARENT);
            card.setStrokeWidth(0);
            card.setCardElevation(0f);
            setNeumorph(card, surface, light, dark, dp(context, 24), dp(context, 6),
                    NeumorphDrawable.Style.RAISED);

        } else if (view instanceof TextInputLayout) {
            TextInputLayout field = (TextInputLayout) view;
            // Inputs are carved in, so the Material box must get out of the way.
            field.setBoxStrokeWidth(0);
            field.setBoxStrokeWidthFocused(0);
            field.setBoxBackgroundColor(Color.TRANSPARENT);
            setNeumorph(field, surface, light, dark, dp(context, 18), dp(context, 4),
                    NeumorphDrawable.Style.PRESSED);

        } else if (view instanceof MaterialButton) {
            MaterialButton button = (MaterialButton) view;
            // Text buttons stay flat; only solid ones are moulded.
            if (button.getBackgroundTintList() != null) {
                button.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                button.setStrokeWidth(0);
                button.setElevation(0f);
                setNeumorph(button, surface, light, dark, dp(context, 22), dp(context, 5),
                        NeumorphDrawable.Style.RAISED);
            }

        } else if (view instanceof EditText && !(view.getParent() instanceof TextInputLayout)) {
            setNeumorph(view, surface, light, dark, dp(context, 18), dp(context, 4),
                    NeumorphDrawable.Style.PRESSED);
        }

        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                skin(group.getChildAt(i));
            }
        }
    }

    private static void setNeumorph(View view, int surface, int light, int dark,
                                    float radius, float elevation,
                                    NeumorphDrawable.Style style) {
        // Blurred shadow layers are ignored by the hardware renderer.
        view.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        NeumorphDrawable drawable =
                new NeumorphDrawable(surface, light, dark, radius, elevation, style);

        int left = view.getPaddingLeft();
        int top = view.getPaddingTop();
        int right = view.getPaddingRight();
        int bottom = view.getPaddingBottom();

        view.setBackground(drawable);
        // setBackground resets padding from the drawable; keep what the layout asked
        // for and add room for the moulded edge.
        int extra = drawable.inset();
        view.setPadding(left + extra, top + extra, right + extra, bottom + extra);
    }

    private static float dp(Context context, int value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics());
    }
}
