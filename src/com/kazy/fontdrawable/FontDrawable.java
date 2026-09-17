// Vendored from https://github.com/k4zy/FontDrawable (Apache License 2.0,
// see LICENSE.txt in this directory) after its JitPack build became
// permanently unbuildable: it depends on JCenter/bintray, shut down in 2022,
// and the upstream repo is archived.
package com.kazy.fontdrawable;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import androidx.annotation.ColorInt;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.applyDimension;

public class FontDrawable extends Drawable {

    final private char fontCode;

    final private Paint paint;

    final private int size;

    final private int padding;

    private FontDrawable(Builder builder) {
        this.fontCode = builder.fontCode;
        this.paint = builder.paint;
        this.paint.setColor(builder.color);
        this.size = builder.size;
        this.padding = builder.padding;
        // A positive stroke width renders the glyph's outline instead of filling
        // it - the way to get an outline icon from a font that only ships the
        // filled ("solid") glyph. Rounded join/cap keep the corners clean.
        if (builder.strokeWidthPx > 0f) {
            this.paint.setStyle(Paint.Style.STROKE);
            this.paint.setStrokeWidth(builder.strokeWidthPx);
            this.paint.setStrokeJoin(Paint.Join.ROUND);
            this.paint.setStrokeCap(Paint.Cap.ROUND);
        }
    }

    protected FontDrawable(char fontCode, Paint paint, int size, int padding) {
        this.fontCode = fontCode;
        this.paint = paint;
        this.size = size;
        this.padding = padding;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawPath(createTextPath(), paint);
    }

    private Path createTextPath() {
        final Path path = createTextPathBase();
        RectF textBounds = createTextBounds(path);
        applyPadding(path, textBounds, createPaddingBounds());
        path.close();
        applyOffset(path, textBounds);
        return path;
    }

    private Path createTextPathBase() {
        Path path = new Path();
        Rect viewBounds = getBounds();
        float textSize = (float) viewBounds.height();
        paint.setTextSize(textSize);
        paint.getTextPath(String.valueOf(fontCode), 0, 1, 0, viewBounds.height(), path);
        return path;
    }

    private RectF createTextBounds(Path path) {
        RectF textBounds = new RectF();
        path.computeBounds(textBounds, true);
        return textBounds;
    }

    private void applyPadding(Path path, RectF textBounds, Rect paddingBounds) {
        final Rect viewBounds = getBounds();
        float deltaWidth = ((float) paddingBounds.width() / textBounds.width());
        float deltaHeight = ((float) paddingBounds.height() / textBounds.height());
        float attenuate = (deltaWidth < deltaHeight) ? deltaWidth : deltaHeight;
        float textSize = paint.getTextSize();
        textSize *= attenuate;
        paint.setTextSize(textSize);
        paint.getTextPath(String.valueOf(fontCode), 0, 1, 0, viewBounds.height(), path);
        path.computeBounds(textBounds, true);
    }

    private void applyOffset(Path path, RectF textBounds) {
        Rect viewBounds = getBounds();
        float startX = viewBounds.centerX() - (textBounds.width() / 2);
        float offsetX = startX - textBounds.left;
        float startY = viewBounds.centerY() - (textBounds.height() / 2);
        float offsetY = startY - (textBounds.top);
        path.offset(offsetX, offsetY);
    }

    private Rect createPaddingBounds() {
        Rect viewBounds = getBounds();
        return new Rect(viewBounds.left + padding, viewBounds.top + padding, viewBounds.right - padding, viewBounds.bottom - padding);
    }

    public Bitmap toBitmap() {
        final Bitmap bitmap = Bitmap.createBitmap(this.getIntrinsicWidth(), this.getIntrinsicHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        this.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        this.draw(canvas);
        return bitmap;
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public static class Builder {

        final private Context context;

        final private char fontCode;

        final private Paint paint;

        private int size = 100;

        private int padding = 0;

        private int color = Color.BLACK;

        private float strokeWidthPx = 0f;

        public Builder(Context context, char fontCode, String customFontPath) {
            this(context, fontCode, Typeface.createFromAsset(context.getAssets(), customFontPath));
        }

        public Builder(Context context, char fontCode, Typeface typeface) {
            this(context, fontCode, new CustomFontPaint(typeface));
        }

        public Builder(Context context, char fontCode, CustomFontPaint paint) {
            this.context = context;
            this.fontCode = fontCode;
            this.paint = new Paint(paint);
        }

        public Builder setSizePx(int size) {
            this.size = size;
            return this;
        }

        public Builder setSizeDp(int dp) {
            int size = convertDpToPx(context, dp);
            setSizePx(size);
            return this;
        }

        public Builder setPaddingPx(int padding) {
            this.padding = padding;
            return this;
        }

        public Builder setPaddingDp(int dp) {
            int padding = convertDpToPx(context, dp);
            setPaddingPx(padding);
            return this;
        }

        public Builder setColor(@ColorInt int color) {
            this.color = color;
            return this;
        }

        // Draw the glyph as an outline of this stroke width rather than filled.
        public Builder setStrokeWidthDp(float dp) {
            this.strokeWidthPx = applyDimension(COMPLEX_UNIT_DIP, dp,
                    context.getResources().getDisplayMetrics());
            return this;
        }

        public FontDrawable build() {
            return new FontDrawable(this);
        }

        private int convertDpToPx(Context context, float dp) {
            return (int) applyDimension(COMPLEX_UNIT_DIP, dp, context.getResources().getDisplayMetrics());
        }
    }
}
