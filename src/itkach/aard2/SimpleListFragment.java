package itkach.aard2;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * Base for the app's list screens: hosts a RecyclerView plus a centered
 * progress spinner, with setListShown() to toggle between them. This is the
 * app's own thin glue over RecyclerView (there is no AndroidX ListFragment),
 * not a re-implementation of a list widget.
 */
public class SimpleListFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.simple_list_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = (RecyclerView) view.findViewById(R.id.list);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        progressView = (ProgressBar) view.findViewById(R.id.progress);
    }

    public RecyclerView getRecyclerView() {
        return recyclerView;
    }

    public void setListAdapter(RecyclerView.Adapter<? extends RecyclerView.ViewHolder> adapter) {
        recyclerView.setAdapter(adapter);
    }

    public void setListShown(boolean shown) {
        recyclerView.setVisibility(shown ? View.VISIBLE : View.GONE);
        setProgressVisible(!shown);
    }

    protected void setProgressVisible(boolean visible) {
        progressView.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
}
