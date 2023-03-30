package itkach.aard2.lookup;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;

import java.util.Timer;
import java.util.TimerTask;

import itkach.aard2.Application;
import itkach.aard2.BaseListFragment;
import itkach.aard2.BlobListAdapter;
import itkach.aard2.R;
import itkach.aard2.SlobHelper;
import itkach.aard2.article.ArticleCollectionActivity;
import itkach.aard2.prefs.AppPrefs;
import itkach.aard2.utils.ClipboardUtils;

public class LookupFragment extends BaseListFragment implements LookupListener, SearchView.OnQueryTextListener {
    private final static String TAG = LookupFragment.class.getSimpleName();

    private Timer timer;
    private SearchView searchView;
    private Application app;
    private TimerTask scheduledLookup = null;
    private BlobListAdapter listAdapter;

    @Override
    protected int getEmptyIcon() {
        return R.drawable.ic_search;
    }

    @Override
    protected CharSequence getEmptyText() {
        return "";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        app = (Application) requireActivity().getApplication();
        app.addLookupListener(this);
    }

    @Override
    protected boolean supportsSelection() {
        return false;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setBusy(false);
        ListView listView = getListView();
        listView.setOnItemClickListener((parent, view1, position, id) -> {
            Log.i("--", "Item clicked: " + position);
            Intent intent = new Intent(getActivity(), ArticleCollectionActivity.class);
            intent.putExtra("position", position);
            startActivity(intent);
        });
        listAdapter = new BlobListAdapter(SlobHelper.getInstance().lastLookupResult);
        getListView().setAdapter(listAdapter);
    }


    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        timer = new Timer();
        inflater.inflate(R.menu.lookup, menu);
        MenuItem lookupMenu = menu.findItem(R.id.action_lookup);
        View filterActionView = lookupMenu.getActionView();
        searchView = filterActionView.findViewById(R.id.search);
        searchView.setQueryHint(lookupMenu.getTitle());
        searchView.setIconified(false);
        searchView.setOnQueryTextListener(this);
        searchView.setOnCloseListener(() -> true);
        searchView.setSubmitButtonEnabled(false);
    }

    @Override
    public void onPrepareOptionsMenu(@NonNull Menu menu) {
        super.onPrepareOptionsMenu(menu);
        if (AppPrefs.autoPasteInLookup()) {
            CharSequence clipboard = ClipboardUtils.take(requireContext());
            if (clipboard != null) {
                app.lookup(clipboard.toString(), false);
            }
        }
        CharSequence query = AppPrefs.getLastQuery();
        searchView.setQuery(query, true);
        if (listAdapter != null && listAdapter.getCount() > 0) {
            searchView.clearFocus();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (searchView != null) {
            String query = searchView.getQuery().toString();
            outState.putString("lookupQuery", query);
        }
    }

    private void setBusy(boolean busy) {
        setListShown(!busy);
        if (!busy) {
            TextView emptyText = emptyView.findViewById(R.id.empty_text);
            String msg = "";
            String query = AppPrefs.getLastQuery();
            if (!query.isEmpty()) {
                msg = getString(R.string.lookup_nothing_found);
            }
            emptyText.setText(msg);
        }
    }

    @Override
    public void onDestroy() {
        if (timer != null) {
            timer.cancel();
        }
        listAdapter = null;
        Application app = (Application) requireActivity().getApplication();
        app.removeLookupListener(this);
        super.onDestroy();
    }

    @Override
    public void onLookupStarted(String query) {
        setBusy(true);
    }

    @Override
    public void onLookupFinished(String query) {
        setBusy(false);
    }

    @Override
    public void onLookupCanceled(String query) {
        setBusy(false);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        Log.d(TAG, "new query submit: " + query);
        TimerTask doLookup = new TimerTask() {
            @Override
            public void run() {
                requireActivity().runOnUiThread(() -> app.lookup(query));
                scheduledLookup = null;
            }
        };
        scheduledLookup = doLookup;
        timer.schedule(doLookup, 600);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        Log.d(TAG, "new query text: " + newText);
        TimerTask doLookup = new TimerTask() {
            @Override
            public void run() {
                final String query = searchView.getQuery().toString();
                if (AppPrefs.getLastQuery().equals(query)) {
                    return;
                }
                requireActivity().runOnUiThread(() -> app.lookup(query));
                scheduledLookup = null;
            }
        };
        final String query = searchView.getQuery().toString();
        if (!AppPrefs.getLastQuery().equals(query)) {
            if (scheduledLookup != null) {
                scheduledLookup.cancel();
            }
            scheduledLookup = doLookup;
            timer.schedule(doLookup, 600);
        }
        return true;
    }
}
