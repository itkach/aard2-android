package itkach.aard2;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;

import java.util.Objects;

import itkach.slob.Slob;

public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener,
        ViewPager.OnPageChangeListener {
    private static final String TAG = MainActivity.class.getSimpleName();
    private AppSectionsPagerAdapter appSectionsPagerAdapter;
    private ViewPager viewPager;
    private BottomNavigationView bottomNavigationView;
    private int oldPosition = -1;

    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Application app = (Application) getApplication();
        app.installTheme(this);
        setContentView(R.layout.activity_main);
        setSupportActionBar(findViewById(R.id.toolbar));

        appSectionsPagerAdapter = new AppSectionsPagerAdapter(getSupportFragmentManager());

        final ActionBar actionBar = Objects.requireNonNull(getSupportActionBar());
        actionBar.setHomeButtonEnabled(true);

        viewPager = findViewById(R.id.pager);
        viewPager.setOffscreenPageLimit(appSectionsPagerAdapter.getCount());
        viewPager.setAdapter(appSectionsPagerAdapter);
        viewPager.setOnPageChangeListener(this);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnItemSelectedListener(this);

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        } else if (app.dictionaries.size() == 0) {
            viewPager.setCurrentItem(3);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int currentSection = savedInstanceState.getInt("currentSection");
        viewPager.setCurrentItem(currentSection);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentSection", viewPager.getCurrentItem());
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.action_lookup) {
            viewPager.setCurrentItem(0);
        } else if (itemId == R.id.action_bookmarks) {
            viewPager.setCurrentItem(1);
        } else if (itemId == R.id.action_history) {
            viewPager.setCurrentItem(2);
        } else if (itemId == R.id.action_dictionaries) {
            viewPager.setCurrentItem(3);
        } else if (itemId == R.id.action_settings) {
            viewPager.setCurrentItem(4);
        } else return false;
        return true;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
    }

    @Override
    public void onPageSelected(int position) {
        if (oldPosition >= 0) {
            bottomNavigationView.getMenu().getItem(oldPosition).setChecked(false);
        } else {
            bottomNavigationView.getMenu().getItem(0).setChecked(false);
        }

        bottomNavigationView.getMenu().getItem(position).setChecked(true);
        oldPosition = position;

        // Hide Ime
        if (oldPosition >= 0) {
            Fragment frag = appSectionsPagerAdapter.getItem(oldPosition);
            if (frag instanceof BaseListFragment) {
                ((BaseListFragment) frag).finishActionMode();
            }
        }
        if (oldPosition == 0) {
            View v = this.getCurrentFocus();
            if (v != null) {
                InputMethodManager mgr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                mgr.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
    }

    @Override
    public void onPageScrollStateChanged(int state) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Application app = (Application) getApplication();
            Slob.Blob blob = app.random();
            if (blob == null) {
                Toast.makeText(this, R.string.article_collection_nothing_found, Toast.LENGTH_SHORT).show();
                return true;
            }
            Intent intent = new Intent(this,
                    ArticleCollectionActivity.class);
            intent.setData(Uri.parse(app.getUrl(blob)));
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        //Looks like shown soft input sometimes causes a system ui visibility
        //change event that breaks article activity launched from here out of full screen mode.
        //Hiding it appears to reduce that.
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        try {
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        } catch (Exception e) {
            Log.w(TAG, "Hiding soft input failed", e);
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        int currentItem = viewPager.getCurrentItem();
        Fragment frag = appSectionsPagerAdapter.getItem(currentItem);
        Log.d(TAG, "current tab: " + currentItem);
        if (frag instanceof BlobDescriptorListFragment) {
            BlobDescriptorListFragment bdFrag = (BlobDescriptorListFragment) frag;
            if (bdFrag.isFilterExpanded()) {
                Log.d(TAG, "Filter is expanded");
                bdFrag.collapseFilter();
                return;
            }
        }
        super.onBackPressed();
    }

    public static final class BookmarksFragment extends BlobDescriptorListFragment {
        @Override
        String getItemClickAction() {
            return "showBookmarks";
        }

        @Override
        BlobDescriptorList getDescriptorList() {
            Application app = (Application) requireActivity().getApplication();
            return app.bookmarks;
        }

        @Override
        int getEmptyIcon() {
            return R.drawable.ic_bookmarks;
        }

        @Override
        String getEmptyText() {
            return getString(R.string.main_empty_bookmarks);
        }

        @Override
        int getDeleteConfirmationItemCountResId() {
            return R.plurals.confirm_delete_bookmark_count;
        }

        @Override
        String getPreferencesNS() {
            return "bookmarks";
        }
    }

    public static class HistoryFragment extends BlobDescriptorListFragment {
        @Override
        String getItemClickAction() {
            return "showHistory";
        }

        @Override
        BlobDescriptorList getDescriptorList() {
            Application app = (Application) requireActivity().getApplication();
            return app.history;
        }

        @Override
        int getEmptyIcon() {
            return R.drawable.ic_history;
        }

        @Override
        String getEmptyText() {
            return getString(R.string.main_empty_history);
        }

        @Override
        int getDeleteConfirmationItemCountResId() {
            return R.plurals.confirm_delete_history_count;
        }

        @Override
        String getPreferencesNS() {
            return "history";
        }

    }

    public static class AppSectionsPagerAdapter extends FragmentPagerAdapter {
        private final Fragment[] fragments;
        LookupFragment tabLookup;
        BlobDescriptorListFragment tabBookmarks;
        BlobDescriptorListFragment tabHistory;
        DictionariesFragment tabDictionaries;
        SettingsFragment tabSettings;

        public AppSectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            tabLookup = new LookupFragment();
            tabBookmarks = new BookmarksFragment();
            tabHistory = new HistoryFragment();
            tabDictionaries = new DictionariesFragment();
            tabSettings = new SettingsFragment();
            fragments = new Fragment[]{tabLookup, tabBookmarks, tabHistory, tabDictionaries, tabSettings};
        }

        @NonNull
        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return fragments.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return "";
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!autoPaste()) {
            Log.d(TAG, "Auto-paste is off");
            return;
        }
        if (!hasFocus) {
            Log.d(TAG, "has no focus");
            return;
        }
        CharSequence text = Clipboard.peek(this);
        if (text != null) {
            viewPager.setCurrentItem(0);
            invalidateOptionsMenu();
        }
    }

    private boolean useVolumeForNav() {
        Application app = (Application) getApplication();
        return app.useVolumeForNav();
    }

    private boolean autoPaste() {
        Application app = (Application) getApplication();
        return app.autoPaste();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {

        if (event.isCanceled()) {
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            if (!useVolumeForNav()) {
                return false;
            }
            int current = viewPager.getCurrentItem();
            if (current > 0) {
                viewPager.setCurrentItem(current - 1);
            } else {
                viewPager.setCurrentItem(appSectionsPagerAdapter.getCount() - 1);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false;
            }
            int current = viewPager.getCurrentItem();
            if (current < appSectionsPagerAdapter.getCount() - 1) {
                viewPager.setCurrentItem(current + 1);
            } else {
                viewPager.setCurrentItem(0);
            }
            return true;
        }

        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false;
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

}
