package itkach.aard2;

import android.app.ActionBar;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.activity.OnBackPressedCallback;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import android.animation.ValueAnimator;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.DecelerateInterpolator;
import android.widget.BaseAdapter;
import android.widget.Toast;
import android.widget.Toolbar;

import java.util.Iterator;
import java.util.List;

import itkach.slob.Slob;
import itkach.slob.Slob.Blob;

public class ArticleCollectionActivity extends FragmentActivity {

    private static final String TAG = ArticleCollectionActivity.class.getSimpleName();

    // ToolbarActionBar (the wrapper Activity.setActionBar(Toolbar) installs)
    // renders a primary ActionMode's contextual bar as a separate view
    // instead of replacing our Toolbar's own content - because our Toolbar
    // lives inside AppBarLayout rather than the standard decor slot
    // ToolbarActionBar expects, the two end up stacked rather than one
    // swapping for the other. Hiding the Toolbar for the duration achieves
    // the intended "replace, not overlap" look with no fragile assumptions
    // about ToolbarActionBar's internals. Only TYPE_PRIMARY (find-in-page)
    // needs this - TYPE_FLOATING is the text-selection popup a long-press in
    // the WebView triggers, which is a small overlay near the selection, not
    // something that replaces the Toolbar.
    @Override
    public void onActionModeStarted(android.view.ActionMode mode) {
        super.onActionModeStarted(mode);
        if (mode.getType() == android.view.ActionMode.TYPE_PRIMARY) {
            getToolbar().setVisibility(View.GONE);
        }
    }

    @Override
    public void onActionModeFinished(android.view.ActionMode mode) {
        super.onActionModeFinished(mode);
        if (mode.getType() == android.view.ActionMode.TYPE_PRIMARY) {
            getToolbar().setVisibility(View.VISIBLE);
        }
    }

    ArticleCollectionPagerAdapter articleCollectionPagerAdapter;
    ViewPager viewPager;

    // Scroll-away header (toolbar + always-visible title bar) driven by the
    // current article's native scroll position - see the scroll listener and
    // onArticleScroll(). No CoordinatorLayout/AppBarLayout/nested-scrolling: the
    // WebView scrolls and flings natively (chromium), and we just slide the
    // header to match, which is what makes it feel like Chrome/Firefox.
    private View header;
    private Toolbar toolbarView;
    private ArticleTitleStrip titleBar;
    private View statusBarScrim;
    private int statusBarInset = 0;
    private int navBarInset = 0;
    // How far the header can slide up = the toolbar's own height (so the title
    // bar below it stays fully visible, coming to rest just below the status
    // bar). Set once the toolbar is measured.
    private int headerCollapseRange = 0;
    // Top padding applied to every article WebView so its content starts below
    // the header; equals the header's full height (status bar + toolbar +
    // title bar). Content scrolls up behind the header (clipToPadding=false).
    private int contentTopInset = 0;
    private boolean fingerDown = false;
    private boolean snapping = false;
    private ValueAnimator snapAnimator;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable snapRunnable = this::snapHeaderIfPartial;
    // Every article WebView currently alive, weakly held so destroyed pages
    // don't leak; used to (re-)apply the content top/bottom padding.
    private final java.util.Set<ArticleWebView> liveWebViews =
            java.util.Collections.newSetFromMap(new java.util.WeakHashMap<ArticleWebView, Boolean>());

    // A single listener shared by every article WebView; it only drives the
    // header for whichever page is currently front-and-center.
    private final ArticleWebView.OnArticleScrollListener articleScrollListener =
            new ArticleWebView.OnArticleScrollListener() {
                @Override
                public void onArticleScrollChanged(ArticleWebView view, int scrollY) {
                    if (view != currentWebView()) {
                        return;
                    }
                    onArticleScroll(scrollY);
                }

                @Override
                public void onArticleTouchDown(ArticleWebView view) {
                    fingerDown = true;
                    mainHandler.removeCallbacks(snapRunnable);
                    cancelSnapAnimation();
                }

                @Override
                public void onArticleTouchUp(ArticleWebView view) {
                    fingerDown = false;
                    scheduleSnap();
                }
            };


    class ToBlobWithFragment implements ToBlob {

        private final String fragment;

        ToBlobWithFragment(String fragment){
            this.fragment = fragment;
        }

        @Override
        public Blob convert(Object item) {
            Blob b = (Blob)item;
            return new Blob(b.owner, b.id, b.key, this.fragment);
        }
    }

    ToBlob blobToBlob = new ToBlob(){

        @Override
        public Blob convert(Object item) {
            return (Blob)item;
        }

    };


    private boolean onDestroyCalled = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Registered via OnBackPressedDispatcher rather than intercepted in
        // onKeyUp(KEYCODE_BACK): ComponentActivity's own back dispatch runs
        // ahead of onKeyUp regardless of what that returns, so an onKeyUp
        // override can't actually veto the default back/finish behavior -
        // this is the only layer that can.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ArticleFragment af = articleCollectionPagerAdapter == null ? null
                        : articleCollectionPagerAdapter.getPrimaryItem();
                ArticleWebView webView = af == null ? null : af.getWebView();
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        requestWindowFeature(Window.FEATURE_PROGRESS);
        final Application app = (Application)getApplication();
        app.installTheme(this);
        setContentView(R.layout.activity_article_collection_loading);
        Toolbar loadingToolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(loadingToolbar);
        applyStatusBarInset(loadingToolbar);
        app.push(this);
        final ActionBar actionBar = getActionBar();
        actionBar.setTitle("...");
        setupUpNavigation(loadingToolbar);
        final Intent intent = getIntent();
        final int position = intent.getIntExtra("position", 0);

        AsyncTask<Void, Void, ArticleCollectionPagerAdapter> createAdapterTask = new AsyncTask<Void, Void, ArticleCollectionPagerAdapter>(){

            Exception exception;

            @Override
            protected ArticleCollectionPagerAdapter doInBackground(Void ... params) {
                ArticleCollectionPagerAdapter result = null;
                Uri articleUrl = intent.getData();
                try {
                    if (articleUrl != null) {
                        result = createFromUri(app, articleUrl);
                    } else {
                        String action = intent.getAction();
                        if (action == null) {
                            result = createFromLastResult(app);
                        } else if (action.equals("showBookmarks")) {
                            result = createFromBookmarks(app);
                        } else if (action.equals("showHistory")) {
                            result = createFromHistory(app);
                        } else {
                            result = createFromIntent(app, intent);
                        }
                    }
                }
                catch (Exception e) {
                    this.exception = e;
                }
                return result;
            }

            @Override
            protected void onPostExecute(ArticleCollectionPagerAdapter adapter) {
                if (isFinishing() || onDestroyCalled) {
                    return;
                }
                if (this.exception != null) {
                    Toast.makeText(ArticleCollectionActivity.this,
                            this.exception.getLocalizedMessage(),
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                articleCollectionPagerAdapter = adapter;
                if (articleCollectionPagerAdapter == null || articleCollectionPagerAdapter.getCount() == 0) {
                    int messageId;
                    if (articleCollectionPagerAdapter == null) {
                        messageId = R.string.article_collection_invalid_link;
                    }
                    else {
                        messageId = R.string.article_collection_nothing_found;
                    }
                    Toast.makeText(ArticleCollectionActivity.this, messageId,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                if (position > articleCollectionPagerAdapter.getCount() - 1) {
                    Toast.makeText(ArticleCollectionActivity.this, R.string.article_collection_selected_not_available,
                            Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }

                setContentView(R.layout.activity_article_collection);
                Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
                setActionBar(toolbar);
                setupUpNavigation(toolbar);

                header = findViewById(R.id.header);
                toolbarView = toolbar;
                titleBar = (ArticleTitleStrip) findViewById(R.id.article_title_bar);
                statusBarScrim = findViewById(R.id.status_bar_scrim);
                // The title bar is only meaningful when there's more than one
                // article to swipe between; hidden for a single result (which
                // also shrinks the header, and thus the content top inset,
                // accordingly - measured after layout below).
                titleBar.setVisibility(
                        articleCollectionPagerAdapter.getCount() == 1 ? View.GONE : View.VISIBLE);

                viewPager = (ViewPager) findViewById(R.id.pager);
                applyContentInsets();
                viewPager.setAdapter(articleCollectionPagerAdapter);
                titleBar.setPager(viewPager);
                viewPager.setOnPageChangeListener(new OnPageChangeListener(){

                    @Override
                    public void onPageScrollStateChanged(int arg0) {}

                    @Override
                    public void onPageScrolled(int position, float offset, int offsetPixels) {
                        if (titleBar != null) {
                            titleBar.onPageScrolled(position, offset);
                        }
                    }

                    @Override
                    public void onPageSelected(final int position) {
                        updateTitle(position);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ArticleFragment fragment =(ArticleFragment) articleCollectionPagerAdapter.getItem(position);
                                fragment.applyTextZoomPref();
                                syncCurrentWebView(position);
                            }
                        });

                    }});
                viewPager.setCurrentItem(position);

                // The header's full height (status bar + toolbar + title bar)
                // isn't known until it's laid out; once it is, use it as the
                // content top inset (padding every article WebView so its
                // content starts just below the header) and remember the
                // toolbar's height as the collapse range.
                header.getViewTreeObserver().addOnGlobalLayoutListener(
                        new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                headerCollapseRange = toolbarView.getHeight();
                                int newInset = header.getBottom();
                                if (newInset > 0 && newInset != contentTopInset) {
                                    contentTopInset = newInset;
                                    applyContentInsetToWebViews();
                                }
                            }
                        });

                updateTitle(position);
                syncCurrentWebView(position);
                articleCollectionPagerAdapter.registerDataSetObserver(new DataSetObserver() {
                    @Override
                    public void onChanged() {
                        if (articleCollectionPagerAdapter.getCount() == 0) {
                            finish();
                        } else if (titleBar != null && viewPager != null) {
                            titleBar.update(viewPager.getCurrentItem(), articleCollectionPagerAdapter);
                        }
                    }
                });
            }
        };

        createAdapterTask.execute();

    }

    private ArticleCollectionPagerAdapter createFromUri(Application app, Uri articleUrl) {
        String host = articleUrl.getHost();
        if (!(host.equals("localhost") || host.matches("127.\\d{1,3}.\\d{1,3}.\\d{1,3}"))) {
            return createFromIntent(app, getIntent());
        }
        BlobDescriptor bd = BlobDescriptor.fromUri(articleUrl);
        if (bd == null) {
            return null;
        }
        Iterator<Slob.Blob> result = app.find(bd.key, bd.slobId);
        BlobListAdapter data = new BlobListAdapter(this, 20, 1);
        data.setData(result);
        boolean hasFragment = !Util.isBlank(bd.fragment);
        return new ArticleCollectionPagerAdapter(
                app, data, hasFragment ? new ToBlobWithFragment(bd.fragment) : blobToBlob, getSupportFragmentManager());
    };

    private ArticleCollectionPagerAdapter createFromLastResult(Application app) {
        return new ArticleCollectionPagerAdapter(
                app, app.lastResult, blobToBlob, getSupportFragmentManager());
    }

    private ArticleCollectionPagerAdapter createFromBookmarks(final Application app) {
        return new ArticleCollectionPagerAdapter(
                app, new BlobDescriptorListAdapter(app.bookmarks), new ToBlob() {
            @Override
            public Blob convert(Object item) {
                return app.bookmarks.resolve((BlobDescriptor)item);
            }
        }, getSupportFragmentManager());
    }

    private ArticleCollectionPagerAdapter createFromHistory(final Application app) {
        return new ArticleCollectionPagerAdapter(
                app, new BlobDescriptorListAdapter(app.history), new ToBlob() {
            @Override
            public Blob convert(Object item) {
                return app.history.resolve((BlobDescriptor)item);
            }
        }, getSupportFragmentManager());
    }

    private ArticleCollectionPagerAdapter createFromIntent(Application app, Intent intent) {
        String lookupKey = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (intent.getAction().equals(Intent.ACTION_PROCESS_TEXT)) {
            lookupKey = getIntent()
                    .getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString();
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra(SearchManager.QUERY);
        }
        if (lookupKey == null) {
            lookupKey = intent.getStringExtra("EXTRA_QUERY");
        }
        String preferredSlobId = null;
        if (lookupKey == null) {
            Uri uri = intent.getData();
            List<String> segments = uri.getPathSegments();
            int length = segments.size();
            if (length > 0) {
                lookupKey = segments.get(length - 1);
            }
            String slobUri = Util.wikipediaToSlobUri(uri);
            Log.d(TAG, String.format("Converted URI %s to slob URI %s", uri, slobUri));
            if (slobUri != null) {
                Slob slob = app.findSlob(slobUri);
                if (slob != null) {
                    preferredSlobId = slob.getId().toString();
                    Log.d(TAG, String.format("Found slob %s for slob URI %s", preferredSlobId, slobUri));
                }
            }
        }
        BlobListAdapter data = new BlobListAdapter(this, 20, 1);
        if (lookupKey == null || lookupKey.length() == 0) {
            String msg = getString(R.string.article_collection_nothing_to_lookup);
            throw new RuntimeException(msg);
        }
        else {
            Iterator<Blob> result = stemLookup(app, lookupKey, preferredSlobId);
            data.setData(result);
        }
        return new ArticleCollectionPagerAdapter(
                app, data, blobToBlob, getSupportFragmentManager());
    }

    private Iterator<Blob> stemLookup(Application app, String lookupKey) {
        return this.stemLookup(app, lookupKey, null);
    }

    private Iterator<Blob> stemLookup(Application app, String lookupKey, String preferredSlobId) {
        Slob.PeekableIterator<Blob> result;
        final int length = lookupKey.length();
        String currentLookupKey = lookupKey;
        int currentLength = currentLookupKey.length();
        do {
            result = app.find(currentLookupKey, preferredSlobId, true);
            if (result.hasNext()) {
                Blob b = result.peek();
                if (b.key.length() - length > 3) {
                    //we don't like this result
                }
                else {
                    break;
                }
            }
            currentLookupKey = currentLookupKey.substring(0, currentLength - 1);
            currentLength = currentLookupKey.length();
        } while (length - currentLength < 5 && currentLength > 0);
        return result;
    }

    private void updateTitle(int position) {
        Log.d("updateTitle", ""+position + " count: " + articleCollectionPagerAdapter.getCount());
        Slob.Blob blob = articleCollectionPagerAdapter.get(position);
        Log.d("updateTitle", ""+blob);
        ActionBar actionBar = getActionBar();
        if (blob != null) {
            String dictLabel = blob.owner.getTags().get("label");
            actionBar.setTitle(dictLabel);
            Application app = (Application)getApplication();
            app.history.add(app.getUrl(blob));
        }
        else {
            actionBar.setTitle("???");
        }
        if (titleBar != null) {
            titleBar.update(position, articleCollectionPagerAdapter);
        }
    }


    // Lets ArticleFragment host its "Find in page" ActionMode in this
    // Toolbar (Toolbar.startActionMode() swaps its own content for the CAB)
    // instead of the WebView's default target, which - since we don't use
    // the framework decor action bar - shows as a floating bar overlapping
    // the Toolbar rather than replacing it.
    Toolbar getToolbar() {
        return (Toolbar) findViewById(R.id.toolbar);
    }

    // Padding for the loading screen's standalone Toolbar, which - unlike
    // the real content's AppBarLayout - has no wrap_content container of its
    // own to grow into, so it needs its own top-only status bar inset.
    private void applyStatusBarInset(View toolbar) {
        ViewCompat.setOnApplyWindowInsetsListener(toolbar, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars());
            v.setPadding(0, bars.top, 0, 0);
            return windowInsets;
        });
    }

    // With edge-to-edge enforced (mandatory as of API 36), content draws
    // behind the system bars unless we handle it ourselves. The header
    // (toolbar + title bar overlay) is offset below the status bar by
    // setting its topMargin to the status bar inset; the status_bar_scrim
    // View is sized to that inset and painted the device-dark-aware backdrop
    // color (drawn on top of the header, so the toolbar slides up behind it).
    // Each article WebView gets a top padding equal to the header's full
    // height so its content starts below the header, and a bottom padding
    // equal to the navigation bar inset; clipToPadding is off so content
    // scrolls up behind the header. Left/right insets are deliberately
    // ignored: in landscape, navigationBars() reports a left inset for the
    // back-gesture swipe zone (not a visible bar) and the display cutout a
    // similar side inset - reserving visible padding for either would look
    // wrong since the header background already extends full-bleed.
    private void applyContentInsets() {
        final View root = findViewById(R.id.article_collection_root);
        if (root == null || header == null || statusBarScrim == null || viewPager == null) {
            return;
        }
        int scrimColor = ((Application) getApplication()).applyStatusBarAppearance(this);
        statusBarScrim.setBackgroundColor(scrimColor);
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            statusBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            navBarInset = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;
            ViewGroup.MarginLayoutParams headerParams =
                    (ViewGroup.MarginLayoutParams) header.getLayoutParams();
            headerParams.topMargin = statusBarInset;
            header.setLayoutParams(headerParams);
            ViewGroup.LayoutParams scrimParams = statusBarScrim.getLayoutParams();
            scrimParams.height = statusBarInset;
            statusBarScrim.setLayoutParams(scrimParams);
            return windowInsets;
        });
    }

    // Pads every currently-live article WebView (the current page and its
    // offscreen neighbours) so content clears the header at the top and the
    // navigation bar at the bottom, and lets content scroll up behind the
    // header. Called once the header height is known and whenever it changes.
    // Re-pads every live WebView (tracked as they're created) rather than
    // fetching them via the adapter: FragmentStatePagerAdapter.getItem()
    // creates a brand-new fragment each call (whose WebView is null), so the
    // padding never reached the WebViews actually on screen.
    private void applyContentInsetToWebViews() {
        if (contentTopInset <= 0) {
            return;
        }
        for (ArticleWebView wv : liveWebViews) {
            padArticleWebView(wv);
        }
    }

    private void padArticleWebView(ArticleWebView wv) {
        wv.setClipToPadding(false);
        wv.setPadding(0, contentTopInset, 0, navBarInset);
    }

    // Called by ArticleFragment as each WebView is created, so it starts out
    // padded and reporting its scroll to us even before it becomes current.
    // Kept in a weak set so it can be re-padded if the header height changes
    // after the WebView already exists (e.g. the header lays out a frame after
    // the first page's WebView is created).
    void configureArticleWebView(ArticleWebView wv) {
        wv.setOnArticleScrollListener(articleScrollListener);
        liveWebViews.add(wv);
        if (contentTopInset > 0) {
            padArticleWebView(wv);
        }
    }

    // The WebView of whichever page is currently front-and-center (the pager's
    // primary item), or null if it isn't laid out yet. Derived live rather
    // than cached, because the initial page's fragment/WebView often doesn't
    // exist yet at the moment we'd want to cache it.
    private ArticleWebView currentWebView() {
        if (articleCollectionPagerAdapter == null) {
            return null;
        }
        ArticleFragment f = articleCollectionPagerAdapter.getPrimaryItem();
        return f == null ? null : f.getWebView();
    }

    // Re-syncs the header to whichever page is now current (each page scrolls
    // independently; a freshly-opened page is at the top with the toolbar
    // shown). Called on page change and once the initial page has loaded.
    private void syncCurrentWebView(int position) {
        cancelSnapAnimation();
        mainHandler.removeCallbacks(snapRunnable);
        ArticleWebView wv = currentWebView();
        if (wv != null) {
            padArticleWebView(wv);
            onArticleScroll(wv.getScrollY());
        } else if (header != null) {
            header.setTranslationY(0);
        }
    }

    // Slides the header to mirror the current article's scroll: it hides by at
    // most the toolbar's height, so the toolbar disappears behind the status
    // bar scrim while the title bar comes to rest just below it, still fully
    // visible. This is a pure function of scrollY (single source of truth), so
    // toolbar and content can never drift out of unison, and a native fling
    // keeps calling this as it decelerates - the whole reason it feels like
    // Chrome/Firefox.
    private void onArticleScroll(int scrollY) {
        if (header == null) {
            return;
        }
        int offset = Math.min(Math.max(scrollY, 0), headerCollapseRange);
        header.setTranslationY(-offset);
        if (!fingerDown && !snapping) {
            scheduleSnap();
        }
    }

    // After the finger lifts and any fling settles, if the header is left
    // partway collapsed, animate it (by scrolling the WebView) to the nearest
    // rest state - fully shown if more than half the toolbar is visible, else
    // fully hidden - so it never rests half-collapsed.
    private void scheduleSnap() {
        mainHandler.removeCallbacks(snapRunnable);
        mainHandler.postDelayed(snapRunnable, 90);
    }

    private void snapHeaderIfPartial() {
        final ArticleWebView wv = currentWebView();
        if (fingerDown || snapping || wv == null || headerCollapseRange <= 0) {
            return;
        }
        int scrollY = wv.getScrollY();
        if (scrollY <= 0 || scrollY >= headerCollapseRange) {
            return; // fully shown or fully hidden already - nothing to snap
        }
        int target = scrollY < headerCollapseRange / 2 ? 0 : headerCollapseRange;
        snapping = true;
        snapAnimator = ValueAnimator.ofInt(scrollY, target);
        snapAnimator.setDuration(150);
        snapAnimator.setInterpolator(new DecelerateInterpolator());
        snapAnimator.addUpdateListener(a -> wv.scrollTo(0, (int) a.getAnimatedValue()));
        snapAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                snapping = false;
            }
        });
        snapAnimator.start();
    }

    private void cancelSnapAnimation() {
        if (snapAnimator != null) {
            snapAnimator.cancel();
            snapAnimator = null;
        }
        snapping = false;
    }

    @Override
    protected void onDestroy() {
        onDestroyCalled = true;
        if (viewPager != null) {
            viewPager.setAdapter(null);
        }
        if (articleCollectionPagerAdapter != null) {
            articleCollectionPagerAdapter.destroy();
        }
        Application app = (Application)getApplication();
        app.pop(this);
        super.onDestroy();
    }

    // ToolbarActionBar's setDisplayHomeAsUpEnabled() doesn't reliably draw a
    // chevron on a Toolbar hosted in AppBarLayout instead of the standard
    // decor slot it expects (same root cause as the ActionMode-hiding fix
    // above), so the up affordance is wired directly on the Toolbar instead
    // of through the ActionBar bridge. android:homeAsUpIndicator is still
    // resolved from the theme rather than a hardcoded drawable.
    private void setupUpNavigation(Toolbar toolbar) {
        // Resolved via the Toolbar's own context (not the Activity's), so
        // this picks up ThemeOverlay.Aard2.Toolbar's colorControlNormal -
        // the drawable's built-in tint otherwise follows the Activity's
        // ambient (non-overlaid) theme instead of the toolbar's.
        TypedArray a = toolbar.getContext().obtainStyledAttributes(new int[]{android.R.attr.homeAsUpIndicator});
        toolbar.setNavigationIcon(a.getDrawable(0));
        a.recycle();
        toolbar.setNavigationOnClickListener(v -> navigateUp());
    }

    private void navigateUp() {
        Intent upIntent = Intent.makeMainActivity(new ComponentName(this, MainActivity.class));
        if (NavUtils.shouldUpRecreateTask(this, upIntent)) {
            TaskStackBuilder.create(this)
                    .addNextIntent(upIntent).startActivities();
            finish();
        } else {
            // This activity is part of the application's task, so simply
            // navigate up to the hierarchical parent activity.
            upIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(upIntent);
            finish();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            navigateUp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean useVolumeForNav() {
        Application app = (Application)getApplication();
        return app.useVolumeForNav();
    }


    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (event.isCanceled()) {
            return true;
        }
        if (articleCollectionPagerAdapter == null) {
            return false;
        }
        ArticleFragment af = articleCollectionPagerAdapter.getPrimaryItem();
        if (af != null) {
            ArticleWebView webView = af.getWebView();
            if (webView != null) {
                if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                    if (!useVolumeForNav()) {
                        return false;
                    }
                    boolean scrolled = webView.pageUp(false);
                    if (!scrolled) {
                        int current = viewPager.getCurrentItem();
                        if (current > 0) {
                            viewPager.setCurrentItem(current - 1);
                        }
                        else {
                            finish();
                        }
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (!useVolumeForNav()) {
                        return false;
                    }
                    boolean scrolled = webView.pageDown(false);
                    if (!scrolled) {
                        int current = viewPager.getCurrentItem();
                        if (current < articleCollectionPagerAdapter.getCount() - 1) {
                            viewPager.setCurrentItem(current + 1);
                        }
                    }
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false;
            }
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (!useVolumeForNav()) {
            return false;
        }
        ArticleFragment af = articleCollectionPagerAdapter.getPrimaryItem();
        if (af != null) {
            ArticleWebView webView = af.getWebView();

            if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                webView.pageUp(true);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                webView.pageDown(true);
                return true;
            }
        }
        return super.onKeyLongPress(keyCode, event);
    }


    static interface ToBlob {
        Slob.Blob convert(Object item);
    }

    public static class ArticleCollectionPagerAdapter extends FragmentStatePagerAdapter {

        private Application app;
        private DataSetObserver observer;
        private BaseAdapter data;
        private ToBlob toBlob;
        private int count;
        private ArticleFragment primaryItem;

        public ArticleCollectionPagerAdapter(Application app, BaseAdapter data, ToBlob toBlob, FragmentManager fm) {
            super(fm);
            this.app = app;
            this.data = data;
            this.count = data.getCount();
            this.observer = new DataSetObserver(){
                @Override
                public void onChanged() {
                    count = ArticleCollectionPagerAdapter.this.data.getCount();
                    notifyDataSetChanged();
                }
            };
            data.registerDataSetObserver(observer);
            this.toBlob = toBlob;
        }

        void destroy() {
            data.unregisterDataSetObserver(observer);
            data = null;
            app = null;
        }

        @Override
        public void setPrimaryItem(ViewGroup container, int position, Object object) {
            super.setPrimaryItem(container, position, object);
            this.primaryItem = (ArticleFragment)object;
        }

        ArticleFragment getPrimaryItem() {
            return this.primaryItem;
        }

        @Override
        public Fragment getItem(int i) {
            Fragment fragment = new ArticleFragment();

            Slob.Blob blob = get(i);
            if (blob != null) {
                String articleUrl = app.getUrl(blob);
                Bundle args = new Bundle();
                args.putString(ArticleFragment.ARG_URL, articleUrl);
                fragment.setArguments(args);
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return count;
        }

        Slob.Blob get(int position) {
            return toBlob.convert(data.getItem(position));
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position < data.getCount()) {
                Object item = data.getItem(position);
                if (item instanceof BlobDescriptor) {
                    return ((BlobDescriptor) item).key;
                }
                if (item instanceof Slob.Blob) {
                    return ((Blob) item).key;
                }
            }
            return "???";
        }

        //this is needed so that fragment is properly updated
        //if underlying data changes (such as on unbookmark)
        //https://code.google.com/p/android/issues/detail?id=19001
        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }
}
