package com.softbankrobotics.pepper.pepperGPT;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;

/** Recipe thumbnails use half the original dimensions; recycled views restore other images. */
public final class ChatImageStyle {
    private ChatImageStyle() { }

    private static final ViewOutlineProvider ROUNDED = new ViewOutlineProvider() {
        @Override public void getOutline(View view, Outline outline) {
            float radius = 16f * view.getResources().getDisplayMetrics().density;
            outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        }
    };

    public static void apply(View image, String activityType) {
        boolean recipe = "recipe".equals(activityType);
        int size = Math.round((recipe ? 250f : 500f) * image.getResources().getDisplayMetrics().density);
        ViewGroup.LayoutParams layout = image.getLayoutParams();
        if (layout.width != size || layout.height != size) {
            layout.width = size;
            layout.height = size;
            image.setLayoutParams(layout);
        }
        image.setOutlineProvider(recipe ? ROUNDED : ViewOutlineProvider.BACKGROUND);
        image.setClipToOutline(recipe);
        image.invalidateOutline();
    }
}
