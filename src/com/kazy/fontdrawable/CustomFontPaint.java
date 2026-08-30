// Vendored from https://github.com/k4zy/FontDrawable (Apache License 2.0,
// see LICENSE.txt in this directory) after its JitPack build became
// permanently unbuildable: it depends on JCenter/bintray, shut down in 2022,
// and the upstream repo is archived.
package com.kazy.fontdrawable;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;

public class CustomFontPaint extends Paint {

    @SuppressWarnings("unused")
    private CustomFontPaint() {
        super();
    }

    @SuppressWarnings("unused")
    private CustomFontPaint(int flags) {
        super(flags);
    }

    public CustomFontPaint(Context context, String fontPathFromAssetsDir) {
        this(Typeface.createFromAsset(context.getAssets(), fontPathFromAssetsDir));
    }

    public CustomFontPaint(Typeface typeface) {
        super(ANTI_ALIAS_FLAG);
        setTypeface(typeface);
        setStyle(Style.FILL);
        setTextAlign(Align.CENTER);
        setUnderlineText(false);
        setAntiAlias(true);
    }
}
