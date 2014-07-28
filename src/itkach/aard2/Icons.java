package itkach.aard2;

import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;

import itkach.fdrawable.Icon;
import itkach.fdrawable.IconicFontDrawable;
import itkach.fdrawable.TypefaceManager;

public enum Icons {
    SEARCH(0xf002),
    BOOKMARK(0xf02e),
    BOOKMARK_O(0xf097),
    HISTORY(0xf1da),
    DICTIONARY(0xf02d),
    REFRESH(0xf021),
    FILTER(0xf0b0),
    ARROW_DOWN(0xf01a),
    ARROW_UP(0xf01b),
    CLOCK(0xf017),
    LIST(0xf03a)
    ;

    private static TypefaceManager typefaceManager;
    private static DisplayMetrics displayMetrics;
    private static int iconColor;

    private final int codePoint;

    private Icons(int codePoint) {
        this.codePoint = codePoint;
    }

    static void init(AssetManager assetManager, Resources r) {
        typefaceManager = new TypefaceManager(assetManager);
        displayMetrics = r.getDisplayMetrics();
        iconColor = r.getColor(android.R.color.secondary_text_dark);
    }

    public Drawable forActionBar() {
        return create(21, iconColor);
    }

    public Drawable forEmptyView() {
        return create(42, Color.LTGRAY);
    }

    public Drawable forTab() {
        return create(19, Color.DKGRAY);
    }

    private IconicFontDrawable create(int intrinsicSize, int color) {
        Typeface font = typefaceManager.get("fontawesome-4.1.0.ttf");
        IconicFontDrawable drawable = new IconicFontDrawable(new Icon(font, codePoint));
        drawable.setIntrinsicHeight(Math.round(intrinsicSize*displayMetrics.density));
        drawable.setIntrinsicWidth(Math.round(intrinsicSize*displayMetrics.density));
        drawable.setIconColor(color);
        return drawable;
    }
}
