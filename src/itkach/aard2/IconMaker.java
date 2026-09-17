package itkach.aard2;

import android.content.Context;
import androidx.core.content.ContextCompat;
import android.util.TypedValue;

import com.kazy.fontdrawable.FontDrawable;


class IconMaker {

    // Phosphor ships each weight (regular = outline, fill = filled, ...) as a
    // separate font file, all sharing one codepoint per icon - so a Glyph
    // carries which file it comes from, not just the character. Everything uses
    // the outline (regular) weight; only the filled bookmark that marks a
    // bookmarked article uses fill. Each asset is a subset built with fonttools
    // (pyftsubset) holding just the glyphs used below, not Phosphor's full
    // ~2000-icon set (that's why phosphor-regular.ttf is ~7KB and
    // phosphor-fill.ttf under 1KB) - regenerate with ./mk-phosphor-subset, which
    // documents the name-to-codepoint mapping. Codepoints are in Phosphor's
    // private-use range (U+E000+), written as unicode char escapes since the
    // glyphs don't render as source text.
    private static final String FONT_REGULAR = "phosphor-regular.ttf";
    private static final String FONT_FILL = "phosphor-fill.ttf";

    static final class Glyph {
        final char code;
        final String font;
        private Glyph(char code, String font) {
            this.code = code;
            this.font = font;
        }
    }

    private static Glyph regular(char code) {
        return new Glyph(code, FONT_REGULAR);
    }

    private static Glyph fill(char code) {
        return new Glyph(code, FONT_FILL);
    }

    static final Glyph IC_SEARCH = regular('\ue30c');        // magnifying-glass
    static final Glyph IC_BOOKMARK = fill('\ue0ea');         // bookmark-simple (filled)
    static final Glyph IC_BOOKMARK_O = regular('\ue0ea');    // bookmark-simple (outline)
    static final Glyph IC_HISTORY = regular('\ue1a0');       // clock-counter-clockwise
    static final Glyph IC_DICTIONARY = regular('\ue0e2');    // book
    static final Glyph IC_SETTINGS = regular('\ue270');      // gear
    static final Glyph IC_FILTER = regular('\ue266');        // funnel
    static final Glyph IC_SORT_DESC = regular('\ue446');     // sort-descending
    static final Glyph IC_SORT_ASC = regular('\ue444');      // sort-ascending
    static final Glyph IC_CLOCK = regular('\ue19a');         // clock
    static final Glyph IC_SORT_NAME = regular('\ue6ee');     // text-aa (sort by title)
    static final Glyph IC_TRASH = regular('\ue4a6');         // trash
    static final Glyph IC_LICENSE = regular('\ue0b4');       // bank
    static final Glyph IC_EXTERNAL_LINK = regular('\ue5de'); // arrow-square-out
    static final Glyph IC_FILE = regular('\ue230');          // file
    static final Glyph IC_ERROR = regular('\ue4e0');         // warning
    static final Glyph IC_COPYRIGHT = regular('\ue54a');     // copyright
    static final Glyph IC_CHECK_SQUARE = regular('\ue186');  // check-square (checked box)
    static final Glyph IC_SQUARE = regular('\ue45e');        // square (empty box)
    static final Glyph IC_ADD = regular('\ue3d4');           // plus
    static final Glyph IC_ANGLE_UP = regular('\ue13c');      // caret-up
    static final Glyph IC_ANGLE_DOWN = regular('\ue136');    // caret-down
    static final Glyph IC_BAN = regular('\ue3de');           // prohibit
    static final Glyph IC_RANDOM = regular('\ue1ee');        // dice-five
    static final Glyph IC_DRAG_HANDLE = regular('\ueae2');   // dots-six-vertical (drag-to-reorder handle)
    static final Glyph IC_COMPRESS = regular('\ue1ce');      // corners-in (exit full screen)
    static final Glyph IC_EXPAND = regular('\ue1d0');        // corners-out (enter full screen)
    static final Glyph IC_FOLDER_OPEN = regular('\ue256');   // folder-open (open dictionary file)
    static final Glyph IC_CLOSE = regular('\ue4f6');         // x (close/remove dictionary)

    static FontDrawable make(Context context, Glyph g, int sizeDp, int color) {
        FontDrawable drawable = new FontDrawable.Builder(context, g.code, g.font)
                .setSizeDp(sizeDp)
                .setColor(color)
                .build();
        return drawable;
    }

    // Resolves a theme attribute (framework or library) to an actual color,
    // so icon color follows the active theme (light/dark, or whatever brand
    // color it's set to) instead of a color resource baked in ahead of time.
    static int resolveThemeColor(Context context, int attrId, int fallbackColor) {
        TypedValue typedValue = new TypedValue();
        boolean wasResolved = context.getTheme().resolveAttribute(attrId, typedValue, true);
        if (wasResolved) {
            return ContextCompat.getColor(context, typedValue.resourceId);
        }
        return fallbackColor;
    }

    // Bottom-navigation icon. The selected destination is drawn in the brand
    // colour (colorPrimary), the rest in the muted textColorSecondary (the
    // framework's own "de-emphasized icon" role, which follows the theme and,
    // on Android 12+, the system's per-wallpaper dynamic color). The colour is
    // chosen here rather than via itemIconTint because the FontDrawable this
    // produces doesn't honour a tint list. Size follows the bar's itemIconSize,
    // so the glyph size passed to make() is just its intrinsic bound.
    static FontDrawable tab(Context context, Glyph g, boolean selected) {
        int attr = selected
                ? androidx.appcompat.R.attr.colorPrimary
                : android.R.attr.textColorSecondary;
        int fallback = selected ? 0xff0099cc : 0xff888888;
        return make(context, g, 24, resolveThemeColor(context, attr, fallback));
    }

    // Content-area accent icons - tied to the theme's brand color (colorPrimary)
    // so they stay consistent with the rest of the UI instead of a separate
    // hardcoded accent.
    static FontDrawable list(Context context, Glyph g) {
        int color = resolveThemeColor(context, androidx.appcompat.R.attr.colorPrimary, 0xff0099cc);
        return make(context, g, 26, color);
    }

    // Secondary row actions (a dictionary row's forget/trash) - deliberately
    // smaller and in the muted secondary text color so they recede behind the
    // dictionary name and its on/off toggle rather than competing with them the
    // way full-size colorPrimary list() icons do.
    static FontDrawable rowAction(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorSecondary, 0xff888888);
        return make(context, g, 18, color);
    }

    // The expand/collapse chevron is a passive disclosure affordance, not an
    // action - smaller still and in the faint hint colour so it recedes further
    // than the row's actual actions (trash) instead of reading as a button.
    static FontDrawable chevron(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorHint, 0xffaaaaaa);
        return make(context, g, 13, color);
    }

    // These icons are drawn directly in a Toolbar (bookmark toggle, etc.), so
    // they need to contrast with colorPrimary the same way the Toolbar's own
    // title text does - resolving @android:color/secondary_text_dark
    // unconditionally (as this used to) ignored the active theme entirely and
    // read as a washed-out, disabled-looking grey once that stopped
    // coincidentally matching.
    static FontDrawable actionBar(Context context, Glyph g) {
        // 20dp (not the framework action icon's nominal 24dp): the glyphs carry
        // little built-in padding, so they read larger at equal size - 20dp
        // brings them into line with the framework overflow "⋮" they sit next to.
        return actionBar(context, g, 20);
    }

    // Same colorOnPrimary tint as the standard app-bar icon, at a caller-chosen
    // size - the find bar's up/down carets use a slightly smaller glyph so they
    // don't read as heavy next to the find field.
    static FontDrawable actionBar(Context context, Glyph g, int sizeDp) {
        int color = resolveThemeColor(context, com.google.android.material.R.attr.colorOnPrimary, 0xff000000);
        return make(context, g, sizeDp, color);
    }

    // Contextual-action-bar (multi-select) icons. The CAB now carries the same
    // colorPrimary background as the Toolbar (see Widget.Aard2.ActionMode), so its
    // icons take the Toolbar's colorOnPrimary tint too - matching the CAB's own
    // title and close-button colour on the blue bar.
    static FontDrawable actionMode(Context context, Glyph g) {
        int color = resolveThemeColor(context, com.google.android.material.R.attr.colorOnPrimary, 0xff000000);
        // Same 20dp as the main Toolbar's actionBar() icons so the CAB matches
        // the regular toolbar rather than looking oversized next to it.
        return make(context, g, 20, color);
    }

    static FontDrawable text(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorSecondary, 0xff888888);
        return make(context, g, 16, color);
    }

    // colorError is Material's own dynamic-color-aware error role, replacing
    // a fixed android.R.color.holo_red_dark this used to resolve to
    // regardless of theme.
    static FontDrawable errorText(Context context, Glyph g) {
        int color = resolveThemeColor(context, androidx.appcompat.R.attr.colorError, 0xffcc0000);
        return make(context, g, 16, color);
    }

    // Placeholder icon for an empty list (e.g. "no bookmarks yet") -
    // textColorHint is the framework's own role for exactly this kind of
    // de-emphasized, content-absent state.
    static FontDrawable emptyView(Context context, Glyph g) {
        int color = resolveThemeColor(context, android.R.attr.textColorHint, 0xffcccccc);
        return make(context, g, 52, color);
    }

}
