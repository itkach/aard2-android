package itkach.aard2;

import android.app.ActionBar;
import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerTitleStrip;
import androidx.viewpager.widget.ViewPager;

import java.util.Iterator;
import java.util.List;

import itkach.slob.Slob;
import itkach.slob.Slob.Blob;

public class ArticleCollectionActivity extends AppCompatActivity
        implements  View.OnSystemUiVisibilityChangeListener,
                    SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = ArticleCollectionActivity.class.getSimpleName();

    static final String PREF = "articleCollection";
    static final String PREF_FULLSCREEN = "fullscreen";

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

    ToBlob blobToBlob = item -> (Blob)item;


    private boolean onDestroyCalled = false;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        final Application app = (Application)getApplication();
        app.installTheme(this);
        getActionBar().hide();
        setContentView(R.layout.activity_article_collection_loading);
        app.push(this);
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setSubtitle("...");
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

                findViewById(R.id.pager_title_strip).setVisibility(
                        articleCollectionPagerAdapter.getCount() == 1 ? ViewGroup.GONE : ViewGroup.VISIBLE);

                viewPager = findViewById(R.id.pager);
                viewPager.setAdapter(articleCollectionPagerAdapter);
                viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener(){

                    @Override
                    public void onPageScrollStateChanged(int arg0) {}

                    @Override
                    public void onPageScrolled(int arg0, float arg1, int arg2) {}

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

                PagerTitleStrip titleStrip = findViewById(R.id.pager_title_strip);
                titleStrip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);
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
    }

    private ArticleCollectionPagerAdapter createFromLastResult(Application app) {
        return new ArticleCollectionPagerAdapter(
                app, app.lastResult, blobToBlob, getSupportFragmentManager());
    }

    private ArticleCollectionPagerAdapter createFromBookmarks(final Application app) {
        return new ArticleCollectionPagerAdapter(
                app, new BlobDescriptorListAdapter(app.bookmarks), item ->
                app.bookmarks.resolve((BlobDescriptor)item), getSupportFragmentManager());
    }

    private ArticleCollectionPagerAdapter createFromHistory(final Application app) {
        return new ArticleCollectionPagerAdapter(
                app, new BlobDescriptorListAdapter(app.history), item ->
                app.history.resolve((BlobDescriptor)item), getSupportFragmentManager());
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
        CharSequence pageTitle = articleCollectionPagerAdapter.getPageTitle(position);
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
        actionBar.setSubtitle(pageTitle);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(PREF_FULLSCREEN)) {
            applyFullScreenPref();
        }
    }

    private void applyFullScreenPref() {
        if (getFullScreenPref()) {
            fullScreen();
        }
        else {
            unFullScreen();
        }
    }

    SharedPreferences prefs() {
        return getSharedPreferences(PREF, Activity.MODE_PRIVATE);
    }

    boolean getFullScreenPref() {
        return prefs().getBoolean(PREF_FULLSCREEN, false);
    }

    private void setFullScreenPref(boolean value) {
        SharedPreferences.Editor editor = prefs().edit();
        editor.putBoolean(PREF_FULLSCREEN, value);
        editor.apply();
    }

    private void fullScreen() {
        Log.d(TAG, "[F] fullscreen");
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE
        );
        getActionBar().hide();
    }

    private void unFullScreen() {
        Log.d(TAG, "[F] unfullscreen");
        getWindow().getDecorView().setSystemUiVisibility(0);
        getActionBar().show();
    }

    void toggleFullScreen() {
        setFullScreenPref(!getFullScreenPref());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "[F] Resume");
        applyFullScreenPref();
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(this);
        prefs().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "[F] Pause");
        View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(null);
        prefs().unregisterOnSharedPreferenceChangeListener(this);
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
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
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onSystemUiVisibilityChange(int visibility) {
        if (isFinishing()) {
            return;
        }
        final View decorView = getWindow().getDecorView();
        int uiOptions = decorView.getSystemUiVisibility();
        boolean isHideNavigation =
                ((uiOptions | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION) == uiOptions);
         if (!isHideNavigation) {
            setFullScreenPref(false);
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
        ArticleFragment af = articleCollectionPagerAdapter.getPrimaryItem();
        if (af != null) {
            ArticleWebView webView = af.getWebView();
            if (webView != null) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    if (webView.canGoBack()) {
                        webView.goBack();
                        return true;
                    }
                }

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


    interface ToBlob {
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
