package itkach.aard2;

import itkach.slob.Slob;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Timer;
import java.util.TimerTask;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SearchView;
import android.widget.TextView;

import itkach.fdrawable.IconicFontDrawable;

public class LookupFragment extends Fragment {

    // ListView lookupResultView;

    private Timer       timer;
    private ProgressBar progressSpinner;
    private SearchView  searchView;
    private String      initialQuery = "";
    private String      currentQuery = "";
    private ListView    lookupResultView;
    private TextView        emptyView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        if (savedInstanceState != null) {
            initialQuery = savedInstanceState.getString("lookupQuery", "");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_lookup,
                container, false);
        Application app = (Application) getActivity().getApplication();

        lookupResultView = (ListView) rootView
                .findViewById(R.id.lookupResultView);

        lookupResultView.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                    int position, long id) {
                Log.i("--", "Item clicked: " + position);
                Intent intent = new Intent(getActivity(),
                        ArticleCollectionActivity.class);
                intent.putExtra("position", position);
                startActivity(intent);
            }
        });

        lookupResultView.setAdapter(app.lastResult);

        progressSpinner = (ProgressBar) rootView
                .findViewById(R.id.progressSpinner);

        IconicFontDrawable iconSearch = app.getIcon(0xf002);
        iconSearch.setIconColor(Color.LTGRAY);

        emptyView = (TextView)rootView.findViewById(R.id.emptyLookupView);
        emptyView.setBackground(iconSearch);
//        final Typeface fontAwesome = Typeface.createFromAsset(getActivity().getAssets(), "fonts/fontawesome-webfont.ttf" );
//        emptyView.setTypeface(fontAwesome);

        return rootView;
    }

    ListView getListView() {
        View rootView = getView();
        return (ListView) rootView.findViewById(R.id.lookupResultView);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.lookup, menu);
        MenuItem miFilter = menu.findItem(R.id.action_lookup);
        View filterActionView = miFilter.getActionView();

        timer = new Timer();

        searchView = (SearchView) filterActionView.findViewById(R.id.fldLookup);
        searchView.setQueryHint(miFilter.getTitle());
        searchView.setIconified(false);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {

            TimerTask scheduledLookup = null;

            @Override
            public boolean onQueryTextSubmit(String query) {
                Log.i("SUBMIT", query);
                onQueryTextChange(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                Log.i("CHANGE", "New text: " + newText);
                TimerTask doLookup = new TimerTask() {
                    @Override
                    public void run() {
                        final String query = searchView.getQuery().toString();
                        if (currentQuery.equals(query)) {
                            return;
                        }
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                setBusy(true);
                            }
                        });
                        final Application app = (Application) getActivity()
                                .getApplication();
                        final Iterator<Slob.Blob> result;
                        if (query == null || query.equals("")) {
                            result = new ArrayList<Slob.Blob>().iterator();
                        } else {
                            result = app.find(query);
                        }
                        getActivity().runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                app.setLookupResult(query, result);
                                currentQuery = query;
                                setBusy(false);
                            }
                        });
                        scheduledLookup = null;
                    }
                };
                if (scheduledLookup != null) {
                    scheduledLookup.cancel();
                }
                scheduledLookup = doLookup;
                timer.schedule(doLookup, 600);
                return true;
            }
        });

        searchView.setOnCloseListener(new SearchView.OnCloseListener() {

            @Override
            public boolean onClose() {
                return true;
            }
        });

        searchView.setSubmitButtonEnabled(false);
        searchView.setQuery(initialQuery, !initialQuery.equals(currentQuery));
        setBusy(false);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuItem miFilter = menu.findItem(R.id.action_lookup);
        View filterActionView = miFilter.getActionView();
        searchView = (SearchView) filterActionView.findViewById(R.id.fldLookup);
        searchView.setQuery(currentQuery, false);
        final Application app = (Application) getActivity()
                .getApplication();
        if (app.lastResult.getCount() > 0) {
            searchView.clearFocus();
        }
        super.onPrepareOptionsMenu(menu);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString("lookupQuery", searchView.getQuery().toString());
    }

    private void setBusy(boolean busy) {
        boolean empty = lookupResultView.getCount() == 0;
        lookupResultView.setVisibility(!busy && !empty ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(!busy && empty ? View.VISIBLE : View.GONE);
        if (searchView.getQuery() != null && !searchView.getQuery().toString().equals("")) {
            emptyView.setText("Nothing found");
        }
        else {
            emptyView.setText("");
        }
        progressSpinner.setVisibility(busy ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onDestroy() {
        timer.cancel();
        super.onDestroy();
    }

}
