package itkach.aard2;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.SparseArray;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.fragment.app.FragmentActivity;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import androidx.viewpager.widget.ViewPager.OnPageChangeListener;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
        // INVISIBLE, not GONE: with windowActionModeOverlay the find-in-page
        // bar (started via the host Activity - see SearchableWebView) overlays
        // this Toolbar's spot, so keeping its layout space avoids a reflow as
        // the bar animates in and out. Same as MainActivity's multi-select CAB.
        if (mode.getType() == android.view.ActionMode.TYPE_PRIMARY) {
            getToolbar().setVisibility(View.INVISIBLE);
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

    // The article menu (bookmark/find/zoom/style/remote) lives on the Activity
    // now that pages are raw ArticleWebViews rather than ArticleFragments; it
    // acts on the current page's WebView.
    private MenuItem miBookmark;
    private Drawable icBookmark;
    private Drawable icBookmarkO;


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
        samsungTransition(R.anim.article_open_enter, R.anim.article_open_exit);
        // Registered via OnBackPressedDispatcher rather than intercepted in
        // onKeyUp(KEYCODE_BACK): ComponentActivity's own back dispatch runs
        // ahead of onKeyUp regardless of what that returns, so an onKeyUp
        // override can't actually veto the default back/finish behavior -
        // this is the only layer that can.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                ArticleWebView webView = articleCollectionPagerAdapter == null ? null
                        : articleCollectionPagerAdapter.getPrimaryWebView();
                if (webView != null && webView.canGoBack()) {
                    webView.goBack();
                    return;
                }
                setEnabled(false);
                getOnBackPressedDispatcher().onBackPressed();
                setEnabled(true);
            }
        });
        final Application app = (Application)getApplication();
        app.installTheme(this);
        // One content view for the whole lifetime: the pager plus an overlaid
        // spinner (see the layout). The spinner shows while the lookup resolves
        // in the background; onPostExecute just flips visibility to the pager.
        // No setContentView swap means nothing flashes through the open transition.
        setContentView(R.layout.activity_article_collection);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setActionBar(toolbar);
        // Paint the status bar with the AppBarLayout's neutral scrim from the
        // first frame, so it never shows the toolbar colour up there.
        ((Application) getApplication()).applyStatusBarAppearance(
                this, (AppBarLayout) findViewById(R.id.appbar));
        app.push(this);
        final ActionBar actionBar = getActionBar();
        actionBar.setTitle("...");
        setupUpNavigation(toolbar);
        // Debounced, so it doesn't even appear on fast lookups.
        ((ContentLoadingProgressBar) findViewById(R.id.loading_progress)).show();
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

                // The content view, toolbar and status bar are already set up in
                // onCreate; just populate the (still-hidden) pager and reveal it.
                View titleStrip = findViewById(R.id.pager_title_strip);
                // The swipe-title strip is only meaningful when there's more
                // than one article to page between; hide it for a single result.
                titleStrip.setVisibility(
                        articleCollectionPagerAdapter.getCount() == 1 ? View.GONE : View.VISIBLE);

                viewPager = (ViewPager) findViewById(R.id.pager);
                applyContentInsets();
                viewPager.setAdapter(articleCollectionPagerAdapter);
                viewPager.addOnPageChangeListener(new OnPageChangeListener(){

                    @Override
                    public void onPageScrollStateChanged(int arg0) {}

                    @Override
                    public void onPageScrolled(int position, float offset, int offsetPixels) {}

                    @Override
                    public void onPageSelected(final int position) {
                        updateTitle(position);
                        ArticleWebView webView = articleCollectionPagerAdapter.getWebView(position);
                        if (webView != null) {
                            webView.applyTextZoomPref();
                            webView.applyStylePref();
                        }
                        // Refresh the bookmark state for the newly current page.
                        invalidateOptionsMenu();
                    }});
                viewPager.setCurrentItem(position);

                updateTitle(position);
                invalidateOptionsMenu();
                articleCollectionPagerAdapter.registerDataSetObserver(new DataSetObserver() {
                    @Override
                    public void onChanged() {
                        if (articleCollectionPagerAdapter.getCount() == 0) {
                            finish();
                        }
                    }
                });

                // Content ready: hide the spinner and reveal the pager in place.
                ((ContentLoadingProgressBar) findViewById(R.id.loading_progress)).hide();
                viewPager.setVisibility(View.VISIBLE);
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
                app, data, hasFragment ? new ToBlobWithFragment(bd.fragment) : blobToBlob);
    };

    private ArticleCollectionPagerAdapter createFromLastResult(Application app) {
        return new ArticleCollectionPagerAdapter(
                app, app.lastResult, blobToBlob);
    }

    private ArticleCollectionPagerAdapter createFromBookmarks(final Application app) {
        return new ArticleCollectionPagerAdapter(
                app, new BlobDescriptorListAdapter(app.bookmarks), new ToBlob() {
            @Override
            public Blob convert(Object item) {
                return app.bookmarks.resolve((BlobDescriptor)item);
            }
        });
    }

    private ArticleCollectionPagerAdapter createFromHistory(final Application app) {
        return new ArticleCollectionPagerAdapter(
                app, new BlobDescriptorListAdapter(app.history), new ToBlob() {
            @Override
            public Blob convert(Object item) {
                return app.history.resolve((BlobDescriptor)item);
            }
        });
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
                app, data, blobToBlob);
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
            if (app.recordHistory()) {
                app.history.add(app.getUrl(blob));
            }
        }
        else {
            actionBar.setTitle("???");
        }
    }


    // Lets the article's "Find in page" ActionMode host in this Toolbar
    // (Toolbar.startActionMode() swaps its own content for the CAB) instead of
    // the WebView's default target, which - since we don't use the framework
    // decor action bar - shows as a floating bar overlapping the Toolbar rather
    // than replacing it.
    Toolbar getToolbar() {
        return (Toolbar) findViewById(R.id.toolbar);
    }

    // With edge-to-edge enforced (mandatory as of API 36), content draws
    // behind the system bars unless we handle it ourselves. The status bar's
    // backdrop and system icon appearance are handled by
    // applyStatusBarAppearance() (which follows the DEVICE's own dark mode,
    // not this app's light/dark preference - see there): it paints the
    // AppBarLayout's own statusBarForeground and, for non-edge-to-edge devices
    // where that never draws, the legacy window status bar color. No manual
    // top padding is applied for the status bar inset - AppBarLayout reserves
    // that space itself via android:fitsSystemWindows (see the layout);
    // padding it here too would double the inset. The navigation bar inset
    // pads the pager's bottom so an article's last line clears it. Left/right
    // insets are deliberately ignored: in landscape, navigationBars() reports
    // a left inset for the back-gesture swipe zone (not a visible bar) and the
    // display cutout a similar side inset - reserving visible padding for
    // either would look wrong since the app bar background extends full-bleed.
    private void applyContentInsets() {
        final AppBarLayout appBar = (AppBarLayout) findViewById(R.id.appbar);
        if (appBar == null || viewPager == null) {
            return;
        }
        ((Application) getApplication()).applyStatusBarAppearance(this, appBar);
        ViewCompat.setOnApplyWindowInsetsListener(viewPager, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return windowInsets;
        });
    }

    @Override
    public void finish() {
        super.finish();
        samsungTransition(R.anim.article_close_enter, R.anim.article_close_exit);
    }

    // Samsung One UI animates the status bar inset during its default activity
    // transition, and the fitsSystemWindows content follows it - sliding up under
    // the status bar and bouncing back on every open/close. overridePendingTransition
    // replaces One UI's transition entirely, so substituting a plain horizontal
    // slide (res/anim/article_*) gives the same slide as other devices with no
    // inset animation to bounce. Samsung only; everywhere else keeps the genuine
    // platform transition (which also masks the brief loading-screen swap). The
    // theme's windowAnimationStyle can't do this - installTheme's setTheme
    // re-applies the default; only overridePendingTransition overrides the
    // actual transition.
    @SuppressWarnings("deprecation")  // overridePendingTransition: replacement is API 34+, minSdk is 23
    private void samsungTransition(int enterAnim, int exitAnim) {
        if ("samsung".equalsIgnoreCase(Build.MANUFACTURER)) {
            overridePendingTransition(enterAnim, exitAnim);
        }
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.article, menu);
        miBookmark = menu.findItem(R.id.action_bookmark_article);
        if (icBookmark == null) {
            Context themed = getActionBar().getThemedContext();
            icBookmark = IconMaker.actionBar(themed, IconMaker.IC_BOOKMARK);
            icBookmarkO = IconMaker.actionBar(themed, IconMaker.IC_BOOKMARK_O);
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // The article menu only makes sense once the collection is loaded (real
        // content view up) - during the loading screen there's nothing to act
        // on, so hide it all. Gating on the adapter, not on a materialized
        // WebView: the current page's WebView is instantiated a layout pass
        // after setCurrentItem, so it can still be null here (notably for the
        // initial position 0, which fires no page-change to re-prepare).
        boolean ready = viewPager != null && articleCollectionPagerAdapter != null;
        for (int i = 0; i < menu.size(); i++) {
            menu.getItem(i).setVisible(ready);
        }
        if (ready && miBookmark != null) {
            String url = currentUrl();
            if (url == null) {
                miBookmark.setVisible(false);
            } else {
                try {
                    displayBookmarked(((Application) getApplication()).isBookmarked(url));
                } catch (Exception e) {
                    miBookmark.setVisible(false);
                }
            }
        }
        return super.onPrepareOptionsMenu(menu);
    }

    private void displayBookmarked(boolean value) {
        if (miBookmark == null) {
            return;
        }
        miBookmark.setChecked(value);
        miBookmark.setIcon(value ? icBookmark : icBookmarkO);
    }

    // The current page's WebView / url, or null while the loading screen is up.
    private ArticleWebView currentWebView() {
        if (viewPager == null || articleCollectionPagerAdapter == null) {
            return null;
        }
        return articleCollectionPagerAdapter.getWebView(viewPager.getCurrentItem());
    }

    private String currentUrl() {
        if (viewPager == null || articleCollectionPagerAdapter == null) {
            return null;
        }
        Slob.Blob blob = articleCollectionPagerAdapter.get(viewPager.getCurrentItem());
        return blob == null ? null : ((Application) getApplication()).getUrl(blob);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            navigateUp();
            return true;
        }
        final ArticleWebView webView = currentWebView();
        if (webView == null) {
            return super.onOptionsItemSelected(item);
        }
        if (itemId == R.id.action_find_in_page) {
            webView.showFindDialog(null, true);
            return true;
        }
        if (itemId == R.id.action_bookmark_article) {
            String url = currentUrl();
            if (url != null) {
                Application app = (Application) getApplication();
                if (item.isChecked()) {
                    app.removeBookmark(url);
                    displayBookmarked(false);
                } else {
                    app.addBookmark(url);
                    displayBookmarked(true);
                }
            }
            return true;
        }
        if (itemId == R.id.action_zoom_in) {
            webView.textZoomIn();
            return true;
        }
        if (itemId == R.id.action_zoom_out) {
            webView.textZoomOut();
            return true;
        }
        if (itemId == R.id.action_zoom_reset) {
            webView.resetTextZoom();
            return true;
        }
        if (itemId == R.id.action_load_remote_content) {
            webView.forceLoadRemoteContent = true;
            webView.reload();
            return true;
        }
        if (itemId == R.id.action_select_style) {
            final String[] styleTitles = webView.getAvailableStyles();
            new AlertDialog.Builder(this)
                    .setTitle(R.string.select_style)
                    .setItems(styleTitles, (dialog, which) -> {
                        webView.saveStylePref(styleTitles[which]);
                        webView.applyStylePref();
                    })
                    .create()
                    .show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Pick up any zoom/style preference changed elsewhere (e.g. Settings)
        // on the visible page; other cached pages get it on page-select.
        ArticleWebView webView = currentWebView();
        if (webView != null) {
            webView.applyTextZoomPref();
            webView.applyStylePref();
        }
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
        ArticleWebView webView = articleCollectionPagerAdapter.getPrimaryWebView();
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
        ArticleWebView webView = articleCollectionPagerAdapter.getPrimaryWebView();
        if (webView != null) {
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

    // A plain (non-deprecated) PagerAdapter paging raw ArticleWebViews rather
    // than the deprecated FragmentStatePagerAdapter over ArticleFragments. Pages
    // are instantiated/destroyed on demand as they scroll into/out of view (the
    // old adapter did the same), so a long collection still loads its articles
    // lazily. PagerTitleStrip works unchanged via getPageTitle.
    public static class ArticleCollectionPagerAdapter extends PagerAdapter {

        private Application app;
        private RecyclerView.AdapterDataObserver observer;
        private RecyclerView.Adapter<?> data;
        private BlobSource source;
        private ToBlob toBlob;
        private int count;

        // The instantiated page views by position, so the Activity can reach the
        // current page's ArticleWebView (menu, back/volume nav, zoom/style).
        private final SparseArray<View> pages = new SparseArray<>();
        private int primaryPosition = -1;

        public ArticleCollectionPagerAdapter(Application app, RecyclerView.Adapter<?> data, ToBlob toBlob) {
            this.app = app;
            this.data = data;
            this.source = (BlobSource) data;
            this.count = source.getBlobCount();
            this.observer = new RecyclerView.AdapterDataObserver(){
                @Override
                public void onChanged() {
                    count = source.getBlobCount();
                    notifyDataSetChanged();
                }

                @Override
                public void onItemRangeInserted(int positionStart, int itemCount) {
                    count = source.getBlobCount();
                    notifyDataSetChanged();
                }

                @Override
                public void onItemRangeRemoved(int positionStart, int itemCount) {
                    count = source.getBlobCount();
                    notifyDataSetChanged();
                }
            };
            data.registerAdapterDataObserver(observer);
            this.toBlob = toBlob;
        }

        void destroy() {
            data.unregisterAdapterDataObserver(observer);
            data = null;
            app = null;
        }

        @Override
        public int getCount() {
            return count;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
            LayoutInflater inflater = LayoutInflater.from(container.getContext());
            Slob.Blob blob = get(position);
            View pageView;
            if (blob == null) {
                pageView = inflater.inflate(R.layout.empty_view, container, false);
                ((TextView) pageView.findViewById(R.id.empty_text)).setText("");
                ((ImageView) pageView.findViewById(R.id.empty_icon)).setImageDrawable(
                        IconMaker.emptyView(container.getContext(), IconMaker.IC_BAN));
            } else {
                pageView = inflater.inflate(R.layout.article_view, container, false);
                final ContentLoadingProgressBar progressBar =
                        pageView.findViewById(R.id.webViewPogress);
                ArticleWebView webView = pageView.findViewById(R.id.webView);
                webView.setWebChromeClient(new WebChromeClient() {
                    @Override
                    public void onProgressChanged(WebView view, int newProgress) {
                        progressBar.setProgress(newProgress);
                        // show()/hide() (not setVisibility) debounce briefly, so a
                        // quick load never flashes the bar on and off.
                        if (newProgress >= progressBar.getMax()) {
                            progressBar.hide();
                        } else {
                            progressBar.show();
                        }
                    }
                });
                webView.loadUrl(app.getUrl(blob));
            }
            container.addView(pageView);
            pages.put(position, pageView);
            return pageView;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            View pageView = (View) object;
            ArticleWebView webView = pageView.findViewById(R.id.webView);
            if (webView != null) {
                webView.destroy();
            }
            container.removeView(pageView);
            if (pages.get(position) == pageView) {
                pages.remove(position);
            }
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            primaryPosition = position;
        }

        ArticleWebView getWebView(int position) {
            View pageView = pages.get(position);
            return pageView == null ? null : (ArticleWebView) pageView.findViewById(R.id.webView);
        }

        ArticleWebView getPrimaryWebView() {
            return getWebView(primaryPosition);
        }

        Slob.Blob get(int position) {
            return toBlob.convert(source.getBlobItem(position));
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position < source.getBlobCount()) {
                Object item = source.getBlobItem(position);
                if (item instanceof BlobDescriptor) {
                    return ((BlobDescriptor) item).key;
                }
                if (item instanceof Slob.Blob) {
                    return ((Blob) item).key;
                }
            }
            return "???";
        }

        //this is needed so that the page is properly updated
        //if underlying data changes (such as on unbookmark)
        //https://code.google.com/p/android/issues/detail?id=19001
        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }
    }
}
