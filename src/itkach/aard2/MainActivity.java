package itkach.aard2;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.shamanland.fonticon.FontIconDrawable;

public class MainActivity extends FragmentActivity implements
        ActionBar.TabListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private AppSectionsPagerAdapter mAppSectionsPagerAdapter;
    private ViewPager               mViewPager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAppSectionsPagerAdapter = new AppSectionsPagerAdapter(
                getSupportFragmentManager());

        final ActionBar actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(mAppSectionsPagerAdapter.getCount());
        mViewPager.setAdapter(mAppSectionsPagerAdapter);

        final String[] subtitles = new String[] {
                getString(R.string.subtitle_lookup),
                getString(R.string.subtitle_bookmark),
                getString(R.string.subtitle_history),
                getString(R.string.subtitle_dictionaries),
                getString(R.string.subtitle_settings),
        };

        mViewPager
                .setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        actionBar.setSelectedNavigationItem(position);
                        actionBar.setSubtitle(subtitles[position]);
                    }
                });

        Drawable[] tabIcons = new Drawable[5];
        tabIcons[0] = FontIconDrawable.inflate(this, R.xml.ic_tab_search);
        tabIcons[1] = FontIconDrawable.inflate(this, R.xml.ic_tab_bookmark);
        tabIcons[2] = FontIconDrawable.inflate(this, R.xml.ic_tab_history);
        tabIcons[3] = FontIconDrawable.inflate(this, R.xml.ic_tab_dictionary);
        tabIcons[4] = FontIconDrawable.inflate(this, R.xml.ic_tab_settings);
        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mAppSectionsPagerAdapter.getCount(); i++) {
            Tab tab = actionBar.newTab();
            tab.setTabListener(this);
            tab.setIcon(tabIcons[i]);
            actionBar.addTab(tab);
        }

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        } else {
            final Application app = (Application)getApplication();
            if (app.dictionaries.size() == 0) {
                mViewPager.setCurrentItem(3);
                DictionariesFragment df = (DictionariesFragment)mAppSectionsPagerAdapter.getItem(3);
                df.findDictionaries();
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int currentSection = savedInstanceState.getInt("currentSection");
        mViewPager.setCurrentItem(currentSection);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentSection", mViewPager.getCurrentItem());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab,
            FragmentTransaction fragmentTransaction) {
        Fragment frag = mAppSectionsPagerAdapter.getItem(tab.getPosition());
        if (frag instanceof BaseListFragment) {
            ((BaseListFragment)frag).finishActionMode();
        }
        if (tab.getPosition() == 0) {
            View v = this.getCurrentFocus();
            if (v != null){
                InputMethodManager mgr = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                mgr.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab,
            FragmentTransaction fragmentTransaction) {
        mViewPager.setCurrentItem(tab.getPosition());
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab,
            FragmentTransaction fragmentTransaction) {
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Intent intent = new Intent(this,
                        ArticleCollectionActivity.class);
                intent.setAction("showRandom");
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public static final class BookmarksFragment extends
            BlobDescriptorListFragment {
        @Override
        String getItemClickAction() {
            return "showBookmarks";
        }

        @Override
        BlobDescriptorList getDescriptorList() {
            Application app = (Application) getActivity().getApplication();
            return app.bookmarks;
        }

        @Override
        int getEmptyIcon() {
            return R.xml.ic_empty_view_bookmark;
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
            Application app = (Application) getActivity()
                    .getApplication();
            return app.history;
        }

        @Override
        int getEmptyIcon() {
            return R.xml.ic_empty_view_history;
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
        private Fragment[]                 fragments;
        private LookupFragment             tabLookup;
        private BlobDescriptorListFragment tabBookmarks;
        private BlobDescriptorListFragment tabHistory;
        private DictionariesFragment       tabDictionaries;
        private SettingsFragment           tabSettings;

        public AppSectionsPagerAdapter(FragmentManager fm) {
            super(fm);
            tabLookup = new LookupFragment();
            tabBookmarks = new BookmarksFragment();
            tabHistory = new HistoryFragment();
            tabDictionaries = new DictionariesFragment();
            tabSettings = new SettingsFragment();
            fragments = new Fragment[] { tabLookup, tabBookmarks, tabHistory,
                    tabDictionaries, tabSettings };
        }

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
}
