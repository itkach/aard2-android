package itkach.aard2;

import android.app.ActionBar;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;
import android.util.Log;
import android.util.Patterns;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.SearchView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import itkach.slob.Slob;

public class MainActivity extends FragmentActivity {

    private static final String TAG = MainActivity.class.getSimpleName();
    private AppSectionsPagerAdapter appSectionsPagerAdapter;
    private ViewPager2 viewPager;
    private SearchView searchView;
    private View btnRandomArticle;
    private Timer lookupTimer;
    private String[] titles;

    private Pattern[] NO_PASTE_PATTERNS = new Pattern[]{
            Patterns.WEB_URL,
            Patterns.EMAIL_ADDRESS,
            Patterns.PHONE
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Application app = (Application)getApplication();
        app.installTheme(this);
        setContentView(R.layout.activity_main);

        appSectionsPagerAdapter = new AppSectionsPagerAdapter(this);

        // ViewPager2 + FragmentStateAdapter doesn't toggle each page fragment's
        // options-menu visibility the way FragmentPagerAdapter's setPrimaryItem
        // did, and menu dispatch ignores lifecycle state - so every offscreen
        // (STARTED) tab would otherwise contribute its menu items at once.
        // Re-assert "only the current tab's menu is visible" whenever any tab
        // fragment reaches STARTED (offscreen instantiation) and on every page
        // change (below).
        getSupportFragmentManager().registerFragmentLifecycleCallbacks(
                new FragmentManager.FragmentLifecycleCallbacks() {
                    @Override
                    public void onFragmentStarted(@NonNull FragmentManager fm, @NonNull Fragment f) {
                        updateMenuVisibility(viewPager.getCurrentItem());
                    }
                }, false);

        Toolbar toolbar = getToolbar();
        setActionBar(toolbar);
        final ActionBar actionBar = getActionBar();
        actionBar.setDisplayShowHomeEnabled(false);
        actionBar.setDisplayHomeAsUpEnabled(false);

        lookupTimer = new Timer();
        searchView = toolbar.findViewById(R.id.fldLookup);
        searchView.setSubmitButtonEnabled(false);
        searchView.setOnCloseListener(() -> true);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            TimerTask scheduledLookup = null;

            @Override
            public boolean onQueryTextSubmit(String query) {
                onQueryTextChange(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                TimerTask doLookup = new TimerTask() {
                    @Override
                    public void run() {
                        final String query = searchView.getQuery().toString();
                        if (app.getLookupQuery().equals(query)) {
                            return;
                        }
                        runOnUiThread(() -> app.lookup(query));
                        scheduledLookup = null;
                    }
                };
                final String query = searchView.getQuery().toString();
                if (!app.getLookupQuery().equals(query)) {
                    if (scheduledLookup != null) {
                        scheduledLookup.cancel();
                    }
                    scheduledLookup = doLookup;
                    lookupTimer.schedule(doLookup, 600);
                }
                return true;
            }
        });

        btnRandomArticle = toolbar.findViewById(R.id.btnRandomArticle);
        ((ImageButton) btnRandomArticle).setImageDrawable(IconMaker.actionBar(this, IconMaker.IC_RANDOM));
        btnRandomArticle.setOnClickListener(v -> {
            Slob.Blob blob = app.random();
            if (blob == null) {
                Toast.makeText(this,
                        R.string.article_collection_nothing_found,
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(this, ArticleCollectionActivity.class);
            intent.setData(Uri.parse(app.getUrl(blob)));
            startActivity(intent);
        });

        final AppBarLayout appBar = (AppBarLayout) findViewById(R.id.appbar);
        viewPager = (ViewPager2) findViewById(R.id.pager);
        // Keep all tabs instantiated (they were, under the old pager) so their
        // state survives switching between them and getFragment() can reach any
        // of them.
        viewPager.setOffscreenPageLimit(appSectionsPagerAdapter.getItemCount() - 1);
        viewPager.setAdapter(appSectionsPagerAdapter);
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateMenuVisibility(position);
                // Drives the title and Lookup search box/dice via
                // onPrepareOptionsMenu - see there.
                invalidateOptionsMenu();
            }
        });

        // With edge-to-edge enforced (mandatory as of API 36), content draws
        // behind the system bars unless we handle it ourselves. The status
        // bar's own backdrop and system icon appearance are handled by
        // applyStatusBarAppearance() (see there for why that has to follow
        // the device's own dark mode, not this app's own light/dark
        // preference) - it needs the AppBarLayout itself, not a listener
        // set directly on it: AppBarLayout installs its own
        // OnApplyWindowInsetsListener on itself in its constructor to
        // capture insets for its statusBarForeground draw path, and
        // calling ViewCompat.setOnApplyWindowInsetsListener on the same
        // view again (as this used to) replaces that listener outright,
        // silently breaking the capture. No manual top padding is needed
        // anywhere for the status bar inset itself: with
        // android:fitsSystemWindows="true" on the AppBarLayout (see the
        // layout file) and none on its Toolbar child, AppBarLayout reserves
        // that space in its own measurement automatically - confirmed
        // empirically after an earlier version of this method ALSO padded
        // the AppBarLayout manually by the same inset, which doubled it
        // (230px instead of 115px on a device where the status bar is
        // 115px tall) since AppBarLayout was already reserving it on its
        // own. The navigation bar inset still pads the ViewPager's bottom.
        // Left/right are deliberately ignored: in landscape,
        // navigationBars() reports a left inset for the back-gesture swipe
        // zone (not a visible bar), and the display cutout reports a
        // similar side inset - reserving visible padding for either would
        // look wrong, since the app bar's own background already extends
        // full-bleed regardless of both.
        app.applyStatusBarAppearance(this, appBar);
        ViewCompat.setOnApplyWindowInsetsListener(viewPager, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars());
            v.setPadding(0, 0, 0, bars.bottom);
            return windowInsets;
        });

        // Position 0 (Lookup) gets no title: its search box is always shown
        // expanded and needs the Toolbar's full width, and a "Lookup" title
        // next to a search box would be redundant anyway. Every other tab's
        // title is just its own name, replacing the app-wide "Aard 2" title
        // (which used to appear alongside a now-removed logo/random-article
        // button) - a title that's specific to the visible screen leaves
        // much more room for the search box than app name + tab name both
        // did together, which is what caused both to get ellipsized on a
        // typical phone width.
        titles = new String[] {
                "",
                getString(R.string.subtitle_bookmark),
                getString(R.string.subtitle_history),
                getString(R.string.subtitle_dictionaries),
                getString(R.string.subtitle_settings),
        };

        TabLayout tabLayout = (TabLayout) findViewById(R.id.tabs);

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                // Title, and showing/hiding the Lookup search box + dice
                // button, both happen in onPrepareOptionsMenu instead of
                // here - see that method for why.
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                Fragment frag = getFragment(tab.getPosition());
                if (frag instanceof BaseListFragment) {
                    ((BaseListFragment)frag).finishActionMode();
                }
                if (tab.getPosition() == 0) {
                    View v = getCurrentFocus();
                    if (v != null){
                        InputMethodManager mgr = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
                        mgr.hideSoftInputFromWindow(v.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
                    }
                }
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        final Drawable[] tabIcons = new Drawable[5];
        tabIcons[0] = IconMaker.tab(this, IconMaker.IC_SEARCH);
        tabIcons[1] = IconMaker.tab(this, IconMaker.IC_BOOKMARK);
        tabIcons[2] = IconMaker.tab(this, IconMaker.IC_HISTORY);
        tabIcons[3] = IconMaker.tab(this, IconMaker.IC_DICTIONARY);
        tabIcons[4] = IconMaker.tab(this, IconMaker.IC_SETTINGS);
        // TabLayoutMediator is ViewPager2's replacement for
        // TabLayout.setupWithViewPager: it keeps tabs and pages in sync and
        // configures each tab (here, just its icon) on demand.
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setIcon(tabIcons[position])).attach();

        // Don't call onRestoreInstanceState here: the framework calls it after
        // onStart, and ViewPager2's FragmentStateAdapter can only restore its
        // state once on a still-'fresh' adapter - restoring it early here (as we
        // did under the old ViewPager) makes that framework restore throw
        // "Expected the adapter to be 'fresh'". The current tab is restored by
        // that framework call (and by ViewPager2's own saved state) instead.
        if (savedInstanceState == null && app.dictionaries.size() == 0) {
            viewPager.setCurrentItem(3, false);
        }

    }

    private Fragment getFragment(int position) {
        // FragmentStateAdapter tags its fragments "f" + itemId, and our itemId
        // is the position (default), so this resolves the live fragment for a
        // tab without the adapter holding references to them.
        return getSupportFragmentManager().findFragmentByTag("f" + position);
    }

    // Only the current tab's options menu should contribute to the Toolbar.
    // ViewPager2 doesn't manage this, so we drive Fragment.setMenuVisibility
    // ourselves; setMenuVisibility invalidates the options menu on change.
    private void updateMenuVisibility(int selected) {
        for (int i = 0; i < appSectionsPagerAdapter.getItemCount(); i++) {
            Fragment f = getFragment(i);
            if (f != null) {
                f.setMenuVisibility(i == selected);
            }
        }
    }

    Toolbar getToolbar() {
        return (Toolbar) findViewById(R.id.toolbar);
    }

    // The title AND showing/hiding the Lookup search box + dice button are
    // both driven from here rather than TabLayout's onTabSelected.
    // onTabSelected fires synchronously the instant a tab crosses the
    // selection threshold, but the OTHER tabs' toolbar icons are
    // options-menu items, which the framework rebuilds asynchronously
    // (posted, not synchronous) whenever the ViewPager's primary fragment
    // changes. Splitting these two - title from onTabSelected, icons from
    // here - was tried and made things worse: the title would flip
    // immediately while the icons lagged behind it, visibly out of sync with
    // each other on top of the original mismatch with the other tabs' menu
    // icons. Driving both from this single synchronous callback (the same
    // one the framework itself uses to rebuild those other icons) keeps
    // everything changing together, even though that means all of it now
    // lags slightly behind the swipe gesture rather than the title tracking
    // it instantly.
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        int position = viewPager.getCurrentItem();
        boolean isLookup = position == 0;
        getActionBar().setTitle(titles[position]);
        searchView.setVisibility(isLookup ? View.VISIBLE : View.GONE);
        btnRandomArticle.setVisibility(isLookup ? View.VISIBLE : View.GONE);
        if (isLookup) {
            revealLookupTab();
        }
        return result;
    }

    // Runs whenever the Lookup tab becomes the visible one - either via
    // onPrepareOptionsMenu above, or (see onWindowFocusChanged) when the
    // window regains focus with clipboard text waiting to auto-paste.
    // Mirrors what used to run in LookupFragment.onPrepareOptionsMenu, back
    // when the search box itself was a menu-hosted action view.
    private void revealLookupTab() {
        final Application app = (Application) getApplication();
        if (app.autoPaste()) {
            CharSequence clipboard = Clipboard.take(this);
            if (clipboard != null) {
                app.lookup(clipboard.toString(), false);
            }
        }
        searchView.setQuery(app.getLookupQuery(), true);
        if (app.lastResult.getItemCount() > 0) {
            searchView.clearFocus();
        }
    }

    // See ArticleCollectionActivity's identical fix: the multi-select delete
    // CAB (BaseListFragment's ListView.setMultiChoiceModeListener) renders as
    // a separate floating bar instead of replacing the Toolbar's content,
    // since our Toolbar isn't the standard decor action-bar slot
    // ToolbarActionBar expects. Hiding the Toolbar for the duration achieves
    // the intended "replace, not overlap" look. Only TYPE_PRIMARY (the CAB)
    // needs this - TYPE_FLOATING is the text-selection popup a long-press in
    // the Lookup SearchView's EditText triggers, which is a small overlay
    // near the selection, not something that replaces the Toolbar.
    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        // INVISIBLE, not GONE: with windowActionModeOverlay the ActionMode bar
        // is drawn over this Toolbar's spot, so we only need to stop the
        // Toolbar painting through - keeping its layout space avoids the reflow
        // that GONE (and the ActionMode's enter/exit animation) would cause.
        if (mode.getType() == ActionMode.TYPE_PRIMARY) {
            getToolbar().setVisibility(View.INVISIBLE);
        }
    }

    @Override
    public void onActionModeFinished(ActionMode mode) {
        super.onActionModeFinished(mode);
        if (mode.getType() == ActionMode.TYPE_PRIMARY) {
            getToolbar().setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        int currentSection = savedInstanceState.getInt("currentSection");
        viewPager.setCurrentItem(currentSection, false);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("currentSection", viewPager.getCurrentItem());
    }

    @Override
    protected void onDestroy() {
        lookupTimer.cancel();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        //Looks like shown soft input sometimes causes a system ui visibility
        //change event that breaks article activity launched from here out of full screen mode.
        //Hiding it appears to reduce that.
        InputMethodManager inputMethodManager = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        try {
            inputMethodManager.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        catch (Exception e) {
            Log.w(TAG, "Hiding soft input failed", e);
        }
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        int currentItem = viewPager.getCurrentItem();
        Fragment frag = getFragment(currentItem);
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
        IconMaker.Glyph getEmptyIcon() {
            return IconMaker.IC_BOOKMARK;
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
        IconMaker.Glyph getEmptyIcon() {
            return IconMaker.IC_HISTORY;
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

    public static class AppSectionsPagerAdapter extends FragmentStateAdapter {

        public AppSectionsPagerAdapter(FragmentActivity fa) {
            super(fa);
        }

        // FragmentStateAdapter creates pages on demand (and destroys distant
        // ones) rather than the old adapter's up-front array; reach a live
        // page via MainActivity.getFragment(position), not by holding refs.
        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0: return new LookupFragment();
                case 1: return new BookmarksFragment();
                case 2: return new HistoryFragment();
                case 3: return new DictionariesFragment();
                case 4: return new SettingsFragment();
                default:
                    throw new IllegalArgumentException("Unexpected tab position " + position);
            }
        }

        @Override
        public int getItemCount() {
            return 5;
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
            revealLookupTab();
        }
    }

    private boolean useVolumeForNav() {
        Application app = (Application)getApplication();
        return app.useVolumeForNav();
    }

    private boolean autoPaste() {
        Application app = (Application)getApplication();
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
            }
            else {
                viewPager.setCurrentItem(appSectionsPagerAdapter.getItemCount() - 1);
            }
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false;
            }
            int current = viewPager.getCurrentItem();
            if (current < appSectionsPagerAdapter.getItemCount() - 1) {
                viewPager.setCurrentItem(current + 1);
            }
            else {
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
