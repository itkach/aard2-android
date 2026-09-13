package itkach.aard2;

import android.app.ActionBar;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import android.util.Log;
import android.util.Patterns;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.ImageButton;
import android.widget.SearchView;
import android.widget.Toast;
import android.widget.Toolbar;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Pattern;

import itkach.slob.Slob;

public class MainActivity extends FragmentActivity {

    private static final String TAG = MainActivity.class.getSimpleName();

    // The five sections, by position. Fragments are added once to R.id.content
    // and shown/hidden as the bottom nav selection changes, so each keeps its
    // state. Tags let us re-find them across recreation.
    private static final String[] SECTION_TAGS = {
            "section_lookup", "section_bookmarks", "section_history",
            "section_dictionaries", "section_settings"
    };
    private static final int LOOKUP = 0;
    private static final int DICTIONARIES = 3;
    private static final String STATE_SELECTED = "selectedSection";
    // Persisted so the last section is restored no matter how the app was last
    // torn down. Instance state (STATE_SELECTED) covers an OS kill while the task
    // is kept, but not a recents swipe-dismiss (Android discards a dismissed
    // task's saved state) or the task ageing out - so this pref seeds the initial
    // section whenever there is no saved instance state, giving "come back to the
    // tab I left" consistently, which is what a user expects regardless of the
    // framework's task-vs-process distinction.
    private static final String PREF_LAST_SECTION = "lastSection";

    private BottomNavigationView bottomNav;
    private SearchView searchView;
    private View btnRandomArticle;
    private Timer lookupTimer;
    private String[] titles;
    private int selectedPosition = LOOKUP;

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

        // Lookup (position 0) shows no title: its search box needs the Toolbar's
        // full width, and a "Lookup" title beside a search box is redundant.
        // Every other section shows its own name.
        titles = new String[] {
                "",
                getString(R.string.subtitle_bookmark),
                getString(R.string.subtitle_history),
                getString(R.string.subtitle_dictionaries),
                getString(R.string.subtitle_settings),
        };

        final AppBarLayout appBar = (AppBarLayout) findViewById(R.id.appbar);
        // Status bar backdrop + system icon appearance follow the DEVICE's dark
        // mode (not this app's preference) - see applyStatusBarAppearance. It
        // must run on the AppBarLayout itself, whose fitsSystemWindows reserves
        // the status bar space (see the layout). The bottom navigation bar inset
        // is applied by BottomNavigationView itself.
        app.applyStatusBarAppearance(this, appBar);

        bottomNav = (BottomNavigationView) findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            selectSection(positionForItemId(item.getItemId()));
            return true;
        });

        // The History section is hidden entirely when history recording is off.
        bottomNav.getMenu().findItem(R.id.nav_history).setVisible(app.recordHistory());

        // The activity is windowSoftInputMode=adjustNothing (see the manifest):
        // when the keyboard opens for a lookup it simply draws over the bottom of
        // the window, leaving the nav bar pinned at the bottom (covered, not
        // floating above the keyboard) and the results list at full height. All
        // text inputs (the Lookup box, the list filter) live in the top Toolbar,
        // so nothing that needs to stay visible is ever covered.

        int initial;
        if (savedInstanceState != null) {
            initial = savedInstanceState.getInt(STATE_SELECTED, LOOKUP);
        } else if (app.dictionaries.size() == 0) {
            initial = DICTIONARIES;
        } else {
            initial = getPreferences(MODE_PRIVATE).getInt(PREF_LAST_SECTION, LOOKUP);
        }
        // Don't restore into the History section if history is (now) off.
        if (!app.recordHistory() && initial == positionForItemId(R.id.nav_history)) {
            initial = LOOKUP;
        }
        setupSections(initial);
        updateNavIcons();
        // Move the bar's own selection to match (fires the listener, which is a
        // no-op for the already-current section).
        bottomNav.setSelectedItemId(itemIdForPosition(initial));
    }

    // The bottom nav's FontDrawable icons don't respond to itemIconTint, so
    // selection is shown by re-colouring them here: the current destination in
    // colorPrimary, the rest muted.
    private void updateNavIcons() {
        Menu m = bottomNav.getMenu();
        m.findItem(R.id.nav_lookup).setIcon(IconMaker.tab(this, IconMaker.IC_SEARCH, selectedPosition == 0));
        m.findItem(R.id.nav_bookmarks).setIcon(IconMaker.tab(this, IconMaker.IC_BOOKMARK, selectedPosition == 1));
        m.findItem(R.id.nav_history).setIcon(IconMaker.tab(this, IconMaker.IC_HISTORY, selectedPosition == 2));
        m.findItem(R.id.nav_dictionaries).setIcon(IconMaker.tab(this, IconMaker.IC_DICTIONARY, selectedPosition == 3));
        m.findItem(R.id.nav_settings).setIcon(IconMaker.tab(this, IconMaker.IC_SETTINGS, selectedPosition == 4));
    }

    // Show or hide the History section's bottom-nav entry. Called at startup
    // from the record-history preference and when Settings toggles it. Nav
    // positions are keyed by item id, not contiguous index, so hiding one entry
    // leaves the others' positions untouched.
    void setHistoryVisible(boolean visible) {
        bottomNav.getMenu().findItem(R.id.nav_history).setVisible(visible);
        if (!visible && selectedPosition == positionForItemId(R.id.nav_history)) {
            bottomNav.setSelectedItemId(itemIdForPosition(LOOKUP));
        }
    }

    // Add all five fragments (once) and show the initial one, hide the rest.
    private void setupSections(int initial) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction();
        for (int i = 0; i < SECTION_TAGS.length; i++) {
            Fragment f = fm.findFragmentByTag(SECTION_TAGS[i]);
            if (f == null) {
                f = createFragment(i);
                tx.add(R.id.content, f, SECTION_TAGS[i]);
            }
            if (i == initial) {
                tx.show(f);
            } else {
                tx.hide(f);
            }
        }
        tx.commitNow();
        selectedPosition = initial;
    }

    private void selectSection(int position) {
        if (position == selectedPosition) {
            return;
        }
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = getFragment(selectedPosition);
        Fragment next = getFragment(position);
        FragmentTransaction tx = fm.beginTransaction();
        if (current != null) {
            tx.hide(current);
        }
        if (next != null) {
            tx.show(next);
        }
        tx.commit();

        // Leaving the previous section: close its selection CAB, and drop the
        // soft keyboard if we're leaving Lookup.
        if (current instanceof BaseListFragment) {
            ((BaseListFragment) current).finishActionMode();
        }
        if (selectedPosition == LOOKUP) {
            View focus = getCurrentFocus();
            if (focus != null) {
                InputMethodManager mgr = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                mgr.hideSoftInputFromWindow(focus.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
            }
        }
        selectedPosition = position;
        // Remember the section so the next launch reopens it (see PREF_LAST_SECTION).
        getPreferences(MODE_PRIVATE).edit().putInt(PREF_LAST_SECTION, position).apply();
        updateNavIcons();
        // Refresh the title and the Lookup-only search box / dice button.
        invalidateOptionsMenu();
    }

    private Fragment createFragment(int position) {
        switch (position) {
            case 0: return new LookupFragment();
            case 1: return new BookmarksFragment();
            case 2: return new HistoryFragment();
            case 3: return new DictionariesFragment();
            case 4: return new SettingsFragment();
            default:
                throw new IllegalArgumentException("Unexpected section " + position);
        }
    }

    private Fragment getFragment(int position) {
        return getSupportFragmentManager().findFragmentByTag(SECTION_TAGS[position]);
    }

    private int positionForItemId(int itemId) {
        if (itemId == R.id.nav_bookmarks) return 1;
        if (itemId == R.id.nav_history) return 2;
        if (itemId == R.id.nav_dictionaries) return 3;
        if (itemId == R.id.nav_settings) return 4;
        return LOOKUP;
    }

    private int itemIdForPosition(int position) {
        switch (position) {
            case 1: return R.id.nav_bookmarks;
            case 2: return R.id.nav_history;
            case 3: return R.id.nav_dictionaries;
            case 4: return R.id.nav_settings;
            default: return R.id.nav_lookup;
        }
    }

    Toolbar getToolbar() {
        return (Toolbar) findViewById(R.id.toolbar);
    }

    // Drives the title and the Lookup search box + dice button off the selected
    // section. Hidden fragments don't contribute menu items, so the other
    // sections' own menu items (filter/sort, add) are handled entirely by their
    // fragments; only this activity-level chrome needs the selection here.
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean result = super.onPrepareOptionsMenu(menu);
        boolean isLookup = selectedPosition == LOOKUP;
        getActionBar().setTitle(titles[selectedPosition]);
        searchView.setVisibility(isLookup ? View.VISIBLE : View.GONE);
        btnRandomArticle.setVisibility(isLookup ? View.VISIBLE : View.GONE);
        if (isLookup) {
            revealLookup();
        }
        return result;
    }

    // Runs whenever Lookup becomes the visible section - either via
    // onPrepareOptionsMenu above, or (see onWindowFocusChanged) when the window
    // regains focus with clipboard text waiting to auto-paste.
    private void revealLookup() {
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
    // CAB renders as a separate floating bar instead of replacing the Toolbar's
    // content, since our Toolbar isn't the standard decor action-bar slot
    // ToolbarActionBar expects. Hiding the Toolbar for the duration achieves the
    // intended "replace, not overlap" look. Only TYPE_PRIMARY (the CAB) needs
    // this - TYPE_FLOATING is the text-selection popup a long-press in the
    // Lookup SearchView's EditText triggers, a small overlay near the selection.
    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        // INVISIBLE, not GONE: with windowActionModeOverlay the ActionMode bar
        // is drawn over this Toolbar's spot, so we only need to stop the Toolbar
        // painting through - keeping its layout space avoids the reflow GONE (and
        // the ActionMode's enter/exit animation) would cause.
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
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(STATE_SELECTED, selectedPosition);
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
            bottomNav.setSelectedItemId(R.id.nav_lookup);
            revealLookup();
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
            int next = selectedPosition > 0
                    ? selectedPosition - 1 : SECTION_TAGS.length - 1;
            bottomNav.setSelectedItemId(itemIdForPosition(next));
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (!useVolumeForNav()) {
                return false;
            }
            int next = selectedPosition < SECTION_TAGS.length - 1
                    ? selectedPosition + 1 : 0;
            bottomNav.setSelectedItemId(itemIdForPosition(next));
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
