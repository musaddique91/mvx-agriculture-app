package com.mvx.agriculture.ui;

import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import com.mvx.agriculture.R;
import com.mvx.agriculture.theme.NeumorphDrawable;
import com.mvx.agriculture.theme.SoftTheme;

/** Fills one included feature tile: accent, icon, title, blurb and tap target. */
final class Tiles {

    private Tiles() {
    }

    /** Coloured puck in the default theme; moulded and monochrome in soft UI. */
    static void applyBadge(ImageView icon, int accentRes, int accentBgRes) {
        if (SoftTheme.isActive(icon.getContext())) {
            float size = icon.getResources().getDisplayMetrics().density * 24f;
            icon.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            icon.setBackground(new NeumorphDrawable(
                    ContextCompat.getColor(icon.getContext(), R.color.soft_surface),
                    ContextCompat.getColor(icon.getContext(), R.color.soft_light),
                    ContextCompat.getColor(icon.getContext(), R.color.soft_dark),
                    size, icon.getResources().getDisplayMetrics().density * 3f,
                    NeumorphDrawable.Style.RAISED));
            icon.setBackgroundTintList(null);
            icon.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(icon.getContext(), R.color.soft_on_surface_variant)));
            return;
        }
        icon.setImageTintList(ColorStateList.valueOf(
                ContextCompat.getColor(icon.getContext(), accentRes)));
        icon.setBackgroundTintList(ColorStateList.valueOf(
                ContextCompat.getColor(icon.getContext(), accentBgRes)));
    }

    static void bind(View tile, int iconRes, int titleRes, int subtitleRes,
                     int accentRes, int accentBgRes, View.OnClickListener onClick) {
        ImageView icon = tile.findViewById(R.id.featureIcon);
        icon.setImageResource(iconRes);
        // Soft UI is one material throughout: a flat colour puck would read as a
        // sticker on the surface, so the badge is moulded and the icon muted.
        applyBadge(icon, accentRes, accentBgRes);

        ((TextView) tile.findViewById(R.id.featureTitle)).setText(titleRes);
        ((TextView) tile.findViewById(R.id.featureSubtitle)).setText(subtitleRes);
        tile.setOnClickListener(onClick);
    }
}
