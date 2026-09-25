package com.mvx.agriculture;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Keeps screen content clear of the status and navigation bars.
 *
 * From targetSdk 35 onwards Android draws every app edge-to-edge and no longer
 * offers an opt-out, so each screen has to inset its own content. Padding the
 * root view (rather than the content frame) leaves the root's background drawable
 * filling the whole window while its children stay inside the system bars.
 */
final class InsetsSupport {

    private InsetsSupport() {
    }

    /** Call right after setContentView(). Pads the whole layout. */
    static void applyTo(Activity activity) {
        View content = activity.findViewById(android.R.id.content);
        if (!(content instanceof ViewGroup)) {
            return;
        }
        ViewGroup contentFrame = (ViewGroup) content;
        if (contentFrame.getChildCount() == 0) {
            return;
        }
        applyToView(contentFrame.getChildAt(0));
    }

    /**
     * Pads one named view instead of the whole layout.
     *
     * Use this on screens with full-bleed artwork: the photo keeps running under
     * the system bars while the text and buttons stay clear of them.
     */
    static void applyTo(Activity activity, int viewId) {
        View target = activity.findViewById(viewId);
        if (target != null) {
            applyToView(target);
        }
    }

    private static void applyToView(final View root) {

        // Remember the padding the layout asked for; insets are added on top of it.
        final int left = root.getPaddingLeft();
        final int top = root.getPaddingTop();
        final int right = root.getPaddingRight();
        final int bottom = root.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            // The keyboard is taller than the navigation bar it covers, so take
            // whichever is larger: without this the IME hides the lower fields.
            int imeBottom = windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(left + bars.left,
                    top + bars.top,
                    right + bars.right,
                    bottom + Math.max(bars.bottom, imeBottom));
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }
}
