package itkach.aard2.article;

import android.app.Activity;
import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.viewpager.widget.PagerTitleStrip;
import androidx.viewpager.widget.ViewPager;

import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

import itkach.aard2.Application;
import itkach.aard2.BlobDescriptor;
import itkach.aard2.BlobDescriptorListAdapter;
import itkach.aard2.MainActivity;
import itkach.aard2.R;
import itkach.aard2.SlobHelper;
import itkach.aard2.lookup.LookupResult;
import itkach.aard2.prefs.AppPrefs;
import itkach.aard2.prefs.ArticleCollectionPrefs;
import itkach.aard2.utils.ThreadUtils;
import itkach.aard2.utils.Utils;
import itkach.aard2.widget.ArticleWebView;
import itkach.slob.Slob;
import itkach.slob.Slob.Blob;

public class ArticleCollectionActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = ArticleCollectionActivity.class.getSimpleName();

    private AbsArticleCollectionPagerAdapter articleCollectionPagerAdapter;
    private ViewPager viewPager;

    private static class ToBlobWithFragment implements ToBlob {

        private final String fragment;

        ToBlobWithFragment(String fragment) {
            this.fragment = fragment;
        }

        @Override
        public Blob convert(Object item) {
            Blob b = (Blob) item;
            return new Blob(b.owner, b.id, b.key, this.fragment);
        }
    }

    private final ToBlob blobToBlob = item -> (Blob) item;

    private boolean onDestroyCalled = false;

    public void onCreate(Bundle savedInstanceState) {
        Utils.updateNightMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_collection_loading);
        setSupportActionBar(findViewById(R.id.toolbar));
        final Application app = (Application) getApplication();
        app.push(this);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setSubtitle("...");
        }
        final Intent intent = getIntent();
        final int position = intent.getIntExtra("position", 0);

        AsyncTask<Void, Void, AbsArticleCollectionPagerAdapter> createAdapterTask = new AsyncTask<Void, Void, AbsArticleCollectionPagerAdapter>() {

            Exception exception;

            @Override
            protected AbsArticleCollectionPagerAdapter doInBackground(Void... params) {
                AbsArticleCollectionPagerAdapter result = null;
                Uri articleUrl = intent.getData();
                try {
                    if (articleUrl != null) {
                        result = createFromUri(app, articleUrl);
                    } else {
                        String action = intent.getAction();
                        if (action == null) {
                            result = createFromLastResult(app);
                        } else if (action.equals("showBookmarks")) {
                            result = createFromBookmarks();
                        } else if (action.equals("showHistory")) {
                            result = createFromHistory();
                        } else {
                            result = createFromIntent(app, intent);
                        }
                    }
                } catch (Exception e) {
                    this.exception = e;
                }
                return result;
            }

            @Override
            protected void onPostExecute(AbsArticleCollectionPagerAdapter adapter) {
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
                    } else {
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
                setSupportActionBar(findViewById(R.id.toolbar));

                findViewById(R.id.pager_title_strip).setVisibility(
                        articleCollectionPagerAdapter.getCount() == 1 ? ViewGroup.GONE : ViewGroup.VISIBLE);

                viewPager = findViewById(R.id.pager);
                viewPager.setAdapter(articleCollectionPagerAdapter);
                viewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {

                    @Override
                    public void onPageScrollStateChanged(int arg0) {
                    }

                    @Override
                    public void onPageScrolled(int arg0, float arg1, int arg2) {
                    }

                    @Override
                    public void onPageSelected(final int position) {
                        updateTitle(position);
                        runOnUiThread(() -> {
                            ArticleFragment fragment = (ArticleFragment) articleCollectionPagerAdapter.getItem(position);
                            fragment.applyTextZoomPref();
                        });

                    }
                });
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

    @Override
    public void onBackPressed() {
        if (ArticleCollectionPrefs.isFullscreen()) {
            // Exit fullscreen
            toggleFullScreen();
            return;
        }
        super.onBackPressed();
    }

    private ArticleCollectionLookupPagerAdapter createFromUri(Application app, Uri articleUrl) {
        String host = articleUrl.getHost();
        if (!(host.equals("localhost") || host.matches("127.\\d{1,3}.\\d{1,3}.\\d{1,3}"))) {
            return createFromIntent(app, getIntent());
        }
        BlobDescriptor bd = BlobDescriptor.fromUri(articleUrl);
        if (bd == null) {
            return null;
        }
        Iterator<Slob.Blob> result = SlobHelper.getInstance().find(bd.key, bd.slobId);
        LookupResult lookupResult = new LookupResult(20, 1);
        lookupResult.setResult(result);
        boolean hasFragment = !TextUtils.isEmpty(bd.fragment);
        return new ArticleCollectionLookupPagerAdapter(lookupResult,
                hasFragment ? new ToBlobWithFragment(bd.fragment) : blobToBlob, getSupportFragmentManager());
    }

    private ArticleCollectionLookupPagerAdapter createFromLastResult(Application app) {
        return new ArticleCollectionLookupPagerAdapter(SlobHelper.getInstance().lastLookupResult, blobToBlob,
                getSupportFragmentManager());
    }

    private ArticleCollectionPagerAdapter createFromBookmarks() {
        SlobHelper slobHelper = SlobHelper.getInstance();
        return new ArticleCollectionPagerAdapter(
                new BlobDescriptorListAdapter(slobHelper.bookmarks), item ->
                slobHelper.bookmarks.resolve((BlobDescriptor) item), getSupportFragmentManager());
    }

    private ArticleCollectionPagerAdapter createFromHistory() {
        SlobHelper slobHelper = SlobHelper.getInstance();
        return new ArticleCollectionPagerAdapter(
                new BlobDescriptorListAdapter(slobHelper.history), item ->
                slobHelper.history.resolve((BlobDescriptor) item), getSupportFragmentManager());
    }

    private ArticleCollectionLookupPagerAdapter createFromIntent(Application app, Intent intent) {
        String lookupKey = intent.getStringExtra(Intent.EXTRA_TEXT);
        if (intent.getAction().equals(Intent.ACTION_PROCESS_TEXT)) {
            lookupKey = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT).toString();
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
            String slobUri = Utils.wikipediaToSlobUri(uri);
            Log.d(TAG, String.format("Converted URI %s to slob URI %s", uri, slobUri));
            if (slobUri != null) {
                Slob slob = SlobHelper.getInstance().findSlob(slobUri);
                if (slob != null) {
                    preferredSlobId = slob.getId().toString();
                    Log.d(TAG, String.format("Found slob %s for slob URI %s", preferredSlobId, slobUri));
                }
            }
        }
        LookupResult lookupResult = new LookupResult(20, 1);
        if (lookupKey == null || lookupKey.length() == 0) {
            String msg = getString(R.string.article_collection_nothing_to_lookup);
            throw new RuntimeException(msg);
        } else {
            Iterator<Blob> result = stemLookup(lookupKey, preferredSlobId);
            lookupResult.setResult(result);
        }
        return new ArticleCollectionLookupPagerAdapter(lookupResult, blobToBlob, getSupportFragmentManager());
    }

    private Iterator<Blob> stemLookup(String lookupKey) {
        return this.stemLookup(lookupKey, null);
    }

    private Iterator<Blob> stemLookup(String lookupKey, String preferredSlobId) {
        Slob.PeekableIterator<Blob> result;
        final int length = lookupKey.length();
        String currentLookupKey = lookupKey;
        int currentLength = currentLookupKey.length();
        do {
            result = SlobHelper.getInstance().find(currentLookupKey, preferredSlobId, true);
            if (result.hasNext()) {
                Blob b = result.peek();
                if (b.key.length() - length > 3) {
                    //we don't like this result
                } else {
                    break;
                }
            }
            currentLookupKey = currentLookupKey.substring(0, currentLength - 1);
            currentLength = currentLookupKey.length();
        } while (length - currentLength < 5 && currentLength > 0);
        return result;
    }

    private void updateTitle(int position) {
        Log.d("updateTitle", "" + position + " count: " + articleCollectionPagerAdapter.getCount());
        Slob.Blob blob = articleCollectionPagerAdapter.get(position);
        CharSequence pageTitle = articleCollectionPagerAdapter.getPageTitle(position);
        Log.d("updateTitle", "" + blob);
        ActionBar actionBar = getSupportActionBar();
        if (blob != null) {
            String dictLabel = blob.owner.getTags().get("label");
            actionBar.setTitle(dictLabel);
            SlobHelper slobHelper = SlobHelper.getInstance();
            slobHelper.history.add(slobHelper.getUrl(blob));
        } else {
            actionBar.setTitle("???");
        }
        actionBar.setSubtitle(pageTitle);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(ArticleCollectionPrefs.PREF_FULLSCREEN)) {
            applyFullScreenPref();
        }
    }

    private void applyFullScreenPref() {
        if (ArticleCollectionPrefs.isFullscreen()) {
            WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                    getWindow().getDecorView());
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars());
            windowInsetsController.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            getSupportActionBar().hide();
        } else {
            WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                    getWindow().getDecorView());
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
            getSupportActionBar().show();
        }
    }

    SharedPreferences prefs() {
        return getSharedPreferences("articleCollection", Activity.MODE_PRIVATE);
    }

    void toggleFullScreen() {
        ArticleCollectionPrefs.setFullscreen(!ArticleCollectionPrefs.isFullscreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "[F] Resume");
        applyFullScreenPref();
        prefs().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "[F] Pause");
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
        Application app = (Application) getApplication();
        app.pop(this);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
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
                    if (!AppPrefs.useVolumeKeysForNavigation()) {
                        return false;
                    }
                    boolean scrolled = webView.pageUp(false);
                    if (!scrolled) {
                        int current = viewPager.getCurrentItem();
                        if (current > 0) {
                            viewPager.setCurrentItem(current - 1);
                        } else {
                            finish();
                        }
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                    if (!AppPrefs.useVolumeKeysForNavigation()) {
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
            if (!AppPrefs.useVolumeKeysForNavigation()) {
                return false;
            }
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (!AppPrefs.useVolumeKeysForNavigation()) {
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


    public abstract static class AbsArticleCollectionPagerAdapter extends FragmentStatePagerAdapter {
        public AbsArticleCollectionPagerAdapter(@NonNull @NotNull FragmentManager fm) {
            super(fm);
        }

        public AbsArticleCollectionPagerAdapter(@NonNull @NotNull FragmentManager fm, int behavior) {
            super(fm, behavior);
        }

        public abstract void destroy();

        public abstract Slob.Blob get(int position);

        public abstract ArticleFragment getPrimaryItem();
    }

    public static class ArticleCollectionPagerAdapter extends AbsArticleCollectionPagerAdapter {
        private final DataSetObserver observer;
        private BaseAdapter data;
        private final ToBlob toBlob;
        private int count;
        private ArticleFragment primaryItem;

        public ArticleCollectionPagerAdapter(BaseAdapter data, ToBlob toBlob, FragmentManager fm) {
            super(fm);
            this.data = data;
            this.count = data.getCount();
            this.observer = new DataSetObserver() {
                @Override
                public void onChanged() {
                    count = ArticleCollectionPagerAdapter.this.data.getCount();
                    notifyDataSetChanged();
                }
            };
            data.registerDataSetObserver(observer);
            this.toBlob = toBlob;
        }

        @Override
        public void destroy() {
            data.unregisterDataSetObserver(observer);
            data = null;
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            this.primaryItem = (ArticleFragment) object;
        }

        @Override
        public ArticleFragment getPrimaryItem() {
            return this.primaryItem;
        }

        @Override
        @NonNull
        public Fragment getItem(int i) {
            Fragment fragment = new ArticleFragment();

            Slob.Blob blob = get(i);
            if (blob != null) {
                String articleUrl = SlobHelper.getInstance().getUrl(blob);
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

        @Override
        public Slob.Blob get(int position) {
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
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }
    }

    public static class ArticleCollectionLookupPagerAdapter extends AbsArticleCollectionPagerAdapter {
        private List<Slob.Blob> list;

        private final LookupResult lookupResult;
        private final DataSetObserver observer = new DataSetObserver() {
            @Override
            public void onChanged() {
                ThreadUtils.postOnMainThread(() -> {
                    list = lookupResult.getList();
                    notifyDataSetChanged();
                });
            }
        };

        private final ToBlob toBlob;
        private ArticleFragment primaryItem;

        public ArticleCollectionLookupPagerAdapter(@NonNull LookupResult lookupResult, @NonNull ToBlob toBlob,
                                                   @NonNull FragmentManager fm) {
            super(fm);
            this.lookupResult = lookupResult;
            this.list = lookupResult.getList();
            this.toBlob = toBlob;
            this.lookupResult.registerDataSetObserver(observer);
        }

        @Override
        public void destroy() {
            lookupResult.unregisterDataSetObserver(observer);
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            this.primaryItem = (ArticleFragment) object;
        }

        @Override
        public ArticleFragment getPrimaryItem() {
            return this.primaryItem;
        }

        @Override
        @NonNull
        public Fragment getItem(int i) {
            Fragment fragment = new ArticleFragment();

            Slob.Blob blob = get(i);
            if (blob != null) {
                String articleUrl = SlobHelper.getInstance().getUrl(blob);
                Bundle args = new Bundle();
                args.putString(ArticleFragment.ARG_URL, articleUrl);
                fragment.setArguments(args);
            }
            return fragment;
        }

        @Override
        public int getCount() {
            return list != null ? list.size() : 0;
        }

        @Override
        public Slob.Blob get(int position) {
            return toBlob.convert(list.get(position));
        }

        @Override
        public CharSequence getPageTitle(int position) {
            if (position < list.size()) {
                Blob item = list.get(position);
                if (item != null) {
                    return item.key;
                }
            }
            return "???";
        }

        //this is needed so that fragment is properly updated
        //if underlying data changes (such as on unbookmark)
        //https://code.google.com/p/android/issues/detail?id=19001
        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }
    }
}
