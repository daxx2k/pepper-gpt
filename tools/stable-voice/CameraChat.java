package com.softbankrobotics.pepper.pepperGPT;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import java.util.ArrayDeque;

public final class CameraChat {
    private static final ArrayDeque<Preview> PREVIEWS = new ArrayDeque<>();

    private static final class Preview {
        final Object row;
        final Bitmap image;
        Preview(Object row, Bitmap image) { this.row = row; this.image = image; }
    }

    static void put(Object row, Bitmap image) {
        forget(row);
        PREVIEWS.addLast(new Preview(row, image));
        while (PREVIEWS.size() > 6) PREVIEWS.removeFirst();
    }

    static void forget(Object row) {
        java.util.Iterator<Preview> it = PREVIEWS.iterator();
        while (it.hasNext()) if (it.next().row == row) it.remove();
    }

    public static void reset(ImageView view) {
        if (view.getDrawable() instanceof FramedImage) view.setImageDrawable(null);
    }

    public static void bind(ImageView view, Object row) {
        for (Preview preview : PREVIEWS) {
            if (preview.row != row) continue;
            float density = view.getResources().getDisplayMetrics().density;
            ViewGroup.LayoutParams size = view.getLayoutParams();
            size.width = Math.round(246 * density);
            size.height = Math.round(172 * density);
            view.setLayoutParams(size);
            view.setImageDrawable(new FramedImage(preview.image, density));
            view.setVisibility(View.VISIBLE);
            view.setOnClickListener(null);
            return;
        }
    }

    private static final class FramedImage extends Drawable {
        private final Bitmap image;
        private final float density;
        private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF area = new RectF();
        private final Matrix matrix = new Matrix();
        private final BitmapShader shader;

        FramedImage(Bitmap image, float density) {
            this.image = image;
            this.density = density;
            shader = new BitmapShader(image, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            imagePaint.setShader(shader);
            stroke.setColor(0xff75b6a9);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.5f * density);
        }

        @Override protected void onBoundsChange(Rect bounds) {
            super.onBoundsChange(bounds);
            area.set(bounds);
            area.inset(density, density);
            float scale = Math.max(area.width() / image.getWidth(), area.height() / image.getHeight());
            matrix.setScale(scale, scale);
            matrix.postTranslate(area.centerX() - image.getWidth() * scale / 2,
                    area.centerY() - image.getHeight() * scale / 2);
            shader.setLocalMatrix(matrix);
        }

        @Override public void draw(Canvas canvas) {
            float radius = 12 * density;
            canvas.drawRoundRect(area, radius, radius, imagePaint);
            canvas.drawRoundRect(area, radius, radius, stroke);
        }

        @Override public void setAlpha(int alpha) { imagePaint.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { imagePaint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
