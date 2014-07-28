package itkach.aard2;

import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.Typeface;
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

    private final int codePoint;

    private Icons(int codePoint) {
        this.codePoint = codePoint;
    }

    static void init(AssetManager assetManager, DisplayMetrics dm) {
        typefaceManager = new TypefaceManager(assetManager);
        displayMetrics = dm;
    }

    public IconicFontDrawable create() {
        return create(21, Color.WHITE);
    }

    public IconicFontDrawable create(int intrinsicSize, int color) {
        Typeface font = typefaceManager.get("fontawesome-4.1.0.ttf");
        IconicFontDrawable drawable = new IconicFontDrawable(new Icon(font, codePoint));
        drawable.setIntrinsicHeight(Math.round(intrinsicSize*displayMetrics.density));
        drawable.setIntrinsicWidth(Math.round(intrinsicSize*displayMetrics.density));
        drawable.setIconColor(color);
        return drawable;
    }
}
