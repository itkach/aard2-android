package itkach.aard2;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public class MainActivity extends FragmentActivity implements
        ActionBar.TabListener {

    private AppSectionsPagerAdapter mAppSectionsPagerAdapter;
    private ViewPager               mViewPager;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mAppSectionsPagerAdapter = new AppSectionsPagerAdapter(
                getSupportFragmentManager(), new String[] {
                        getString(R.string.icon_search),
                        getString(R.string.icon_star),
                        getString(R.string.icon_history),
                        getString(R.string.icon_dictionary), });

        final ActionBar actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(false);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        mViewPager = (ViewPager) findViewById(R.id.pager);
        mViewPager.setOffscreenPageLimit(mAppSectionsPagerAdapter.getCount());
        mViewPager.setAdapter(mAppSectionsPagerAdapter);

        final String[] subtitles = new String[] {
                getString(R.string.subtitle_lookup),
                getString(R.string.subtitle_bookmark),
                getString(R.string.subtitle_history),
                getString(R.string.subtitle_dictionaries), };

        mViewPager
                .setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                    @Override
                    public void onPageSelected(int position) {
                        actionBar.setSelectedNavigationItem(position);
                        actionBar.setSubtitle(subtitles[position]);
                    }
                });

        Drawable[] tabIcons = new Drawable[4];
        Application app = (Application)getApplication();

        tabIcons[0] = Icons.SEARCH.forTab();
        tabIcons[1] = Icons.BOOKMARK.forTab();
        tabIcons[2] = Icons.HISTORY.forTab();
        tabIcons[3] = Icons.DICTIONARY.forTab();
        // For each of the sections in the app, add a tab to the action bar.
        for (int i = 0; i < mAppSectionsPagerAdapter.getCount(); i++) {
            Tab tab = actionBar.newTab();
            tab.setTabListener(this);
            tab.setIcon(tabIcons[i]);
            actionBar.addTab(tab);
        }

        Intent intent = getIntent();
        String query = intent.getStringExtra(SearchManager.QUERY);
        if (query != null && !query.equals("")) {
            mViewPager.setCurrentItem(0);
            LookupFragment lookupFragment = (LookupFragment)mAppSectionsPagerAdapter.getItem(0);
            lookupFragment.setQuery(query);
        }
        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        } else {
            Log.d("onCreate", "saved sate is null!!!");
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
        ((BaseListFragment)mAppSectionsPagerAdapter.getItem(tab.getPosition())).finishActionMode();
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
        Icons getEmptyIcon() {
            return Icons.BOOKMARK;
        }

        @Override
        String getEmptyText() {
            return "No bookmarks";
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
        Icons getEmptyIcon() {
            return Icons.HISTORY;
        }

        @Override
        String getEmptyText() {
            return "No history";
        }
    }

    public static class AppSectionsPagerAdapter extends FragmentPagerAdapter {

        private String[]                   titles;
        private Fragment[]                 fragments;
        private LookupFragment             tabLookup;
        private BlobDescriptorListFragment tabBookmarks;
        private BlobDescriptorListFragment tabHistory;
        private DictionariesFragment       tabDictionaries;

        public AppSectionsPagerAdapter(FragmentManager fm, String[] titles) {
            super(fm);
            this.titles = titles;
            tabLookup = new LookupFragment();
            tabBookmarks = new BookmarksFragment();

            tabHistory = new HistoryFragment();

            tabDictionaries = new DictionariesFragment();
            fragments = new Fragment[] { tabLookup, tabBookmarks, tabHistory,
                    tabDictionaries };
        }

        @Override
        public Fragment getItem(int i) {
            return fragments[i];
        }

        @Override
        public int getCount() {
            return titles.length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return titles[position];
        }
    }
}
