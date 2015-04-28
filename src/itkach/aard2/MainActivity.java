package itkach.aard2;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.FragmentTransaction;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import com.shamanland.fonticon.FontIconDrawable;

public class MainActivity extends FragmentActivity implements
        ActionBar.TabListener {

    private static final String TAG = MainActivity.class.getSimpleName();
    private AppSectionsPagerAdapter appSectionsPagerAdapter;
    private ViewPager viewPager;
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Application app = (Application)getApplication();
        app.installTheme(this);
        setContentView(R.layout.activity_main);

        appSectionsPagerAdapter = new AppSectionsPagerAdapter(
                getSupportFragmentManager());

        final ActionBar actionBar = getActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

        viewPager = (ViewPager) findViewById(R.id.pager);
        viewPager.setOffscreenPageLimit(appSectionsPagerAdapter.getCount());
        viewPager.setAdapter(appSectionsPagerAdapter);

        final String[] subtitles = new String[] {
                getString(R.string.subtitle_lookup),
                getString(R.string.subtitle_bookmark),
                getString(R.string.subtitle_history),
                getString(R.string.subtitle_dictionaries),
                getString(R.string.subtitle_settings),
        };

        viewPager
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
        for (int i = 0; i < appSectionsPagerAdapter.getCount(); i++) {
            Tab tab = actionBar.newTab();
            tab.setTabListener(this);
            tab.setIcon(tabIcons[i]);
            actionBar.addTab(tab);
        }

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        } else {
            if (app.dictionaries.size() == 0) {
                viewPager.setCurrentItem(3);
                DictionariesFragment df = (DictionariesFragment) appSectionsPagerAdapter.getItem(3);
                df.findDictionaries();
            }
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int currentSection = savedInstanceState.getInt("currentSection");
        viewPager.setCurrentItem(currentSection);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentSection", viewPager.getCurrentItem());
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab,
            FragmentTransaction fragmentTransaction) {
        Fragment frag = appSectionsPagerAdapter.getItem(tab.getPosition());
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
        viewPager.setCurrentItem(tab.getPosition());
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

    @Override
    public void onBackPressed() {
        int currentItem = viewPager.getCurrentItem();
        Fragment frag = appSectionsPagerAdapter.getItem(currentItem);
        Log.d(TAG, "current tab: " + currentItem);
        if (frag instanceof BlobDescriptorListFragment) {
            BlobDescriptorListFragment bdFrag = (BlobDescriptorListFragment)frag;
            if (bdFrag.isFilterExpanded()) {
                Log.d(TAG, "Filter is expanded");
                bdFrag.collapseFilter();
                return;
            }
        }
        super.onBackPressed();
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
        private Fragment[]         fragments;
        LookupFragment             tabLookup;
        BlobDescriptorListFragment tabBookmarks;
        BlobDescriptorListFragment tabHistory;
        DictionariesFragment       tabDictionaries;
        SettingsFragment           tabSettings;

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

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        ClipData clipData = cm.getPrimaryClip();
        if (clipData == null) {
            return;
        }
        int count = clipData.getItemCount();
        for (int i = 0; i < count; i++) {
            ClipData.Item item = clipData.getItemAt(i);
            CharSequence text = item.getText();
            if (text != null && text.length() > 0) {
                viewPager.setCurrentItem(0);
                cm.setPrimaryClip(ClipData.newPlainText(null, ""));
                appSectionsPagerAdapter.tabLookup.setQuery(text.toString());
                break;
            }
        }
    }
}
