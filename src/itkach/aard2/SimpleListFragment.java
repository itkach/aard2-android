package itkach.aard2;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;

import androidx.fragment.app.Fragment;

/**
 * Minimal replacement for android.support.v4.app.ListFragment, which has no
 * AndroidX equivalent (Google dropped it rather than porting it). Provides
 * just the subset of its API this codebase actually used: a fragment-owned
 * ListView, getListView()/setListAdapter(), an overridable onListItemClick()
 * callback matching ListFragment's own contract, and setListShown() for
 * toggling between the list and a loading spinner.
 */
public class SimpleListFragment extends Fragment {

    private ListView listView;
    private ProgressBar progressView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_list_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        listView = (ListView) view.findViewById(R.id.list);
        progressView = (ProgressBar) view.findViewById(R.id.progress);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
                onListItemClick(listView, v, position, id);
            }
        });
    }

    public ListView getListView() {
        return listView;
    }

    public void setListAdapter(ListAdapter adapter) {
        listView.setAdapter(adapter);
    }

    public void onListItemClick(ListView l, View v, int position, long id) {
    }

    public void setListShown(boolean shown) {
        listView.setVisibility(shown ? View.VISIBLE : View.GONE);
        progressView.setVisibility(shown ? View.GONE : View.VISIBLE);
    }
}
