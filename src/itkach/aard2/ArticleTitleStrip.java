package itkach.aard2;

import android.content.Context;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

/**
 * Reproduces the faded prev | current | next swipe-title strip that used to be
 * a PagerTitleStrip child of the ViewPager. It now lives in the collapsing
 * header (next to the toolbar) so it can slide with the toolbar while the
 * article WebView scrolls natively - a PagerTitleStrip couldn't, because it
 * pins itself to the top of the ViewPager whose content now scrolls
 * independently. Behaviour mirrors PagerTitleStrip: the current title is
 * centered and fully opaque, the neighbours sit dimmed at the left/right edges,
 * and during a swipe all three slide across and cross-fade. Driven by the
 * host's ViewPager.OnPageChangeListener via update()/onPageScrolled().
 */
public class ArticleTitleStrip extends ViewGroup {

    private static final float SIDE_ALPHA = 0.35f;
    private static final int TEXT_SIZE_DIP = 10;

    private final TextView prevText;
    private final TextView currText;
    private final TextView nextText;

    private ViewPager pager;
    private int lastKnownPosition = -1;
    private float lastKnownOffset = -1;

    public ArticleTitleStrip(Context context) {
        this(context, null);
    }

    public ArticleTitleStrip(Context context, AttributeSet attrs) {
        super(context, attrs);
        int textColor = IconMaker.resolveThemeColor(
                context, android.R.attr.textColorPrimary, Color.BLACK);
        prevText = addText(textColor);
        currText = addText(textColor);
        nextText = addText(textColor);
    }

    private TextView addText(int color) {
        TextView tv = new TextView(getContext());
        tv.setSingleLine(true);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, TEXT_SIZE_DIP);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        addView(tv);
        return tv;
    }

    // Binds this strip to the pager whose current page it reflects.
    void setPager(ViewPager pager) {
        this.pager = pager;
        update(pager.getCurrentItem(), pager.getAdapter());
    }

    // Refreshes the three titles for the given current position (call on page
    // select and whenever the adapter's data changes).
    void update(int position, PagerAdapter adapter) {
        if (adapter == null) {
            return;
        }
        int count = adapter.getCount();
        prevText.setText(position > 0 ? adapter.getPageTitle(position - 1) : null);
        currText.setText(position >= 0 && position < count ? adapter.getPageTitle(position) : null);
        nextText.setText(position + 1 < count ? adapter.getPageTitle(position + 1) : null);
        requestLayout();
        // Re-run positioning at rest for the new titles.
        lastKnownPosition = -1;
        onPageScrolled(position, 0f);
    }

    // Slides and cross-fades the three titles to follow an in-progress swipe.
    void onPageScrolled(int position, float offset) {
        lastKnownPosition = position;
        lastKnownOffset = offset;
        layoutTexts(getWidth(), position, offset);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int childWidthSpec = MeasureSpec.makeMeasureSpec(
                Math.max(0, width - getPaddingLeft() - getPaddingRight()),
                MeasureSpec.AT_MOST);
        int childHeightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        int textHeight = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            child.measure(childWidthSpec, childHeightSpec);
            textHeight = Math.max(textHeight, child.getMeasuredHeight());
        }
        int height = textHeight + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(
                resolveSize(width, widthMeasureSpec),
                resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        layoutTexts(r - l, lastKnownPosition < 0 ? (pager != null ? pager.getCurrentItem() : 0) : lastKnownPosition,
                lastKnownOffset < 0 ? 0f : lastKnownOffset);
    }

    // Positions the three titles: current centered, prev/next dimmed at the
    // edges, everything shifted by the swipe offset so the strip tracks the
    // finger. Mirrors PagerTitleStrip's own placement closely enough to read
    // as the same widget.
    private void layoutTexts(int width, int position, float offset) {
        if (width <= 0) {
            return;
        }
        int top = getPaddingTop();
        int contentWidth = Math.max(0, width - getPaddingLeft() - getPaddingRight());

        // Measure fresh here at the real strip width. Relying on the widths
        // left over from onMeasure was unreliable: an intermediate measure
        // pass at a much smaller width left each TextView's cached measured
        // width tiny (~45px), so titles got laid out narrow and ellipsized to
        // "te..." with the whole strip empty around them.
        int wSpec = MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.AT_MOST);
        int hSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);
        currText.measure(wSpec, hSpec);
        prevText.measure(wSpec, hSpec);
        nextText.measure(wSpec, hSpec);

        int currW = currText.getMeasuredWidth();
        int prevW = prevText.getMeasuredWidth();
        int nextW = nextText.getMeasuredWidth();

        // Current title centered at offset 0, sliding left as the swipe
        // progresses toward the next page (offset 0 -> 1).
        int currCenter = getPaddingLeft() + contentWidth / 2 - (int) (offset * contentWidth);
        int currLeft = currCenter - currW / 2;
        currText.layout(currLeft, top, currLeft + currW, top + currText.getMeasuredHeight());

        // Previous title tucked against the left edge, its right end trailing
        // the current title.
        int prevRight = currLeft - dp(16);
        int prevLeft = Math.min(getPaddingLeft(), prevRight - prevW);
        prevText.layout(prevLeft, top, prevLeft + prevW, top + prevText.getMeasuredHeight());

        // Next title tucked against the right edge, its left end leading the
        // current title.
        int nextLeft = currLeft + currW + dp(16);
        int rightEdge = width - getPaddingRight();
        nextLeft = Math.max(nextLeft, rightEdge - nextW);
        nextText.layout(nextLeft, top, nextLeft + nextW, top + nextText.getMeasuredHeight());

        // Cross-fade: current fades out as the swipe advances, the incoming
        // next fades in; neighbours are otherwise dimmed.
        currText.setAlpha(1f - offset * (1f - SIDE_ALPHA));
        prevText.setAlpha(SIDE_ALPHA);
        nextText.setAlpha(SIDE_ALPHA + offset * (1f - SIDE_ALPHA));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
