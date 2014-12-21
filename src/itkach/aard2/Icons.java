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
    SETTINGS(0xf013),
    REFRESH(0xf021),
    FILTER(0xf0b0),
    SORT_DESC(0xf161),
    SORT_ASC(0xf160),
    CLOCK(0xf017),
    LIST(0xf03a),
    TRASH(0xf1f8),
    LICENSE(0xf19c),
    EXTERNAL_LINK(0xf08e),
    FILE_ARCHIVE(0xf1c6),
    ERROR(0xf071),
    COPYRIGHT(0xf1f9),
    SELECT_ALL(0xf046),
    ADD(0xf067),
    ANGLE_UP(0xf106),
    ANGLE_DOWN(0xf107),
    STAR(0xf005),
    STAR_O(0xf006),
    FOLDER(0xf07b),
    LEVEL_UP(0xf148)
    ;

    private static TypefaceManager typefaceManager;
    private static DisplayMetrics displayMetrics;
    private static int iconColor;
    private static int listIconColor;
    private static int textColor;

    private final int codePoint;

    private Icons(int codePoint) {
        this.codePoint = codePoint;
    }

    static void init(AssetManager assetManager, Resources r) {
        typefaceManager = new TypefaceManager(assetManager);
        displayMetrics = r.getDisplayMetrics();
        iconColor = r.getColor(android.R.color.secondary_text_dark);
        listIconColor = r.getColor(android.R.color.holo_blue_dark);
        textColor = r.getColor(android.R.color.primary_text_light);
    }

    Drawable forActionBar() {
        return create(21, iconColor);
    }

    Drawable forEmptyView() {
        return create(42, Color.LTGRAY);
    }

    Drawable forTab() {
        return create(19, 0xff888888);
    }

    Drawable forList() {
        return create(21, listIconColor);
    }

    Drawable forListSmall() {
        return create(18, listIconColor);
    }

    Drawable forText() {
        return forText(textColor);
    }

    Drawable forText(int color) {
        return create(14, color);
    }

    private Drawable create(int intrinsicSize, int color) {
        Typeface font = typefaceManager.get("fontawesome-4.2.0.ttf");
        IconicFontDrawable drawable = new IconicFontDrawable(new Icon(font, codePoint));
        drawable.setIntrinsicHeight(Math.round(intrinsicSize*displayMetrics.density));
        drawable.setIntrinsicWidth(Math.round(intrinsicSize*displayMetrics.density));
        drawable.setIconColor(color);
        return drawable;
    }

}
