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
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import android.util.Log;
import android.view.KeyEvent;
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

                View titleStrip = findViewById(R.id.pager_title_strip);
                // The swipe-title strip is only meaningful when there's more
                // than one article to page between; hide it for a single result.
                titleStrip.setVisibility(
                        articleCollectionPagerAdapter.getCount() == 1 ? View.GONE : View.VISIBLE);

                viewPager = (ViewPager) findViewById(R.id.pager);
                applyContentInsets();
                viewPager.setAdapter(articleCollectionPagerAdapter);
                viewPager.setOnPageChangeListener(new OnPageChangeListener(){

                    @Override
                    public void onPageScrollStateChanged(int arg0) {}

                    @Override
                    public void onPageScrolled(int position, float offset, int offsetPixels) {}

                    @Override
                    public void onPageSelected(final int position) {
                        updateTitle(position);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                ArticleFragment fragment =(ArticleFragment) articleCollectionPagerAdapter.getItem(position);
                                fragment.applyTextZoomPref();
                            }
                        });

                    }});
                viewPager.setCurrentItem(position);

                updateTitle(position);
                articleCollectionPagerAdapter.registerDataSetObserver(new DataSetObserver() {
                    @Override
                    public void onChanged() {
                        if (articleCollectionPagerAdapter.getCount() == 0) {
                            finish();
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
        private RecyclerView.AdapterDataObserver observer;
        private RecyclerView.Adapter<?> data;
        private BlobSource source;
        private ToBlob toBlob;
        private int count;
        private ArticleFragment primaryItem;

        public ArticleCollectionPagerAdapter(Application app, RecyclerView.Adapter<?> data, ToBlob toBlob, FragmentManager fm) {
            super(fm);
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

        //this is needed so that fragment is properly updated
        //if underlying data changes (such as on unbookmark)
        //https://code.google.com/p/android/issues/detail?id=19001
        @Override
        public int getItemPosition(Object object) {
            return POSITION_NONE;
        }
    }
}
