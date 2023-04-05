package itkach.aard2.article;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.NavUtils;
import androidx.core.app.TaskStackBuilder;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager.widget.PagerTitleStrip;
import androidx.viewpager.widget.ViewPager;

import java.util.Objects;

import itkach.aard2.BuildConfig;
import itkach.aard2.MainActivity;
import itkach.aard2.R;
import itkach.aard2.SlobHelper;
import itkach.aard2.prefs.AppPrefs;
import itkach.aard2.prefs.ArticleCollectionPrefs;
import itkach.aard2.slob.SlobTags;
import itkach.aard2.utils.ThreadUtils;
import itkach.aard2.utils.Utils;
import itkach.aard2.widget.ArticleWebView;
import itkach.slob.Slob;

public class ArticleCollectionActivity extends AppCompatActivity
        implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String ACTION_BOOKMARKS = BuildConfig.APPLICATION_ID + ".action.BOOKMARKS";
    public static final String ACTION_HISTORY = BuildConfig.APPLICATION_ID + ".action.HISTORY";

    private static final String TAG = ArticleCollectionActivity.class.getSimpleName();

    private ArticleCollectionPagerAdapter pagerAdapter;
    private ViewPager viewPager;
    private ArticleCollectionViewModel viewModel;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        Utils.updateNightMode();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_article_collection_loading);
        setSupportActionBar(findViewById(R.id.toolbar));
        viewModel = new ViewModelProvider(this).get(ArticleCollectionViewModel.class);

        final ActionBar actionBar = requireActionBar();
        actionBar.hide();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setSubtitle("...");

        final Intent intent = getIntent();
        final int position = intent.getIntExtra("position", 0);

        viewModel.getBlobListLiveData().observe(this, blobListWrapper -> {
            if (blobListWrapper == null) {
                // Adapter is never null, empty or less than |position|
                return;
            }

            setContentView(R.layout.activity_article_collection);
            setSupportActionBar(findViewById(R.id.toolbar));

            PagerTitleStrip titleStrip = findViewById(R.id.pager_title_strip);
            titleStrip.setVisibility(blobListWrapper.size() == 1 ? ViewGroup.GONE : ViewGroup.VISIBLE);
            titleStrip.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 10);

            viewPager = findViewById(R.id.pager);
            pagerAdapter = new ArticleCollectionPagerAdapter(blobListWrapper, getSupportFragmentManager());
            viewPager.setAdapter(pagerAdapter);
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
                        ArticleFragment fragment = pagerAdapter.getItem(position);
                        fragment.applyTextZoomPref();
                    });

                }
            });
            viewPager.setCurrentItem(position);
            updateTitle(position);
            pagerAdapter.registerDataSetObserver(new DataSetObserver() {
                @Override
                public void onChanged() {
                    if (pagerAdapter.getCount() == 0) {
                        finish();
                    }
                }
            });
        });
        viewModel.getFailureMessageLiveData().observe(this, message -> {
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
        // Load adapter
        viewModel.loadBlobList(intent);
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

    @NonNull
    public ActionBar requireActionBar() {
        return Objects.requireNonNull(getSupportActionBar());
    }

    private void updateTitle(int position) {
        Log.d("updateTitle", position + " count: " + pagerAdapter.getCount());
        Slob.Blob blob = pagerAdapter.get(position);
        CharSequence pageTitle = pagerAdapter.getPageTitle(position);
        Log.d("updateTitle", String.valueOf(blob));
        ActionBar actionBar = requireActionBar();
        if (blob != null) {
            String dictLabel = blob.owner.getTags().get(SlobTags.TAG_LABEL);
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
            requireActionBar().hide();
        } else {
            WindowInsetsControllerCompat windowInsetsController = WindowCompat.getInsetsController(getWindow(),
                    getWindow().getDecorView());
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars());
            requireActionBar().show();
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
        if (viewPager != null) {
            viewPager.setAdapter(null);
        }
        if (pagerAdapter != null) {
            pagerAdapter.destroy();
        }
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
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
    public boolean onKeyUp(int keyCode, @NonNull KeyEvent event) {
        if (event.isCanceled()) {
            return true;
        }
        if (pagerAdapter == null) {
            return false;
        }
        ArticleFragment af = pagerAdapter.getPrimaryItem();
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
                        if (current < pagerAdapter.getCount() - 1) {
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
    public boolean onKeyDown(int keyCode, @NonNull KeyEvent event) {
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
        ArticleFragment af = pagerAdapter.getPrimaryItem();
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

    public static class ArticleCollectionPagerAdapter extends FragmentStatePagerAdapter {
        private final BlobListWrapper blobListWrapper;
        private final DataSetObserver observer = new DataSetObserver() {
            @Override
            public void onChanged() {
                ThreadUtils.postOnMainThread(() -> notifyDataSetChanged());
            }
        };

        private ArticleFragment primaryItem;

        public ArticleCollectionPagerAdapter(@NonNull BlobListWrapper blobListWrapper, @NonNull FragmentManager fm) {
            super(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT);
            this.blobListWrapper = blobListWrapper;
            this.blobListWrapper.registerDataSetObserver(observer);
        }

        public void destroy() {
            blobListWrapper.unregisterDataSetObserver(observer);
        }

        @Override
        public void setPrimaryItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            super.setPrimaryItem(container, position, object);
            this.primaryItem = (ArticleFragment) object;
        }

        public ArticleFragment getPrimaryItem() {
            return this.primaryItem;
        }

        @Override
        @NonNull
        public ArticleFragment getItem(int i) {
            ArticleFragment fragment = new ArticleFragment();

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
            return blobListWrapper.size();
        }

        public Slob.Blob get(int position) {
            return blobListWrapper.get(position);
        }

        @Override
        public CharSequence getPageTitle(int position) {
            CharSequence label = blobListWrapper.getLabel(position);
            return label != null ? label : "???";
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
