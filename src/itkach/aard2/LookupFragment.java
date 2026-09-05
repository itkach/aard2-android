package itkach.aard2;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

public class LookupFragment extends BaseListFragment implements LookupListener {

    private Application app;

    private final static String TAG = LookupFragment.class.getSimpleName();

    @Override
    IconMaker.Glyph getEmptyIcon() {
        return IconMaker.IC_SEARCH;
    }

    @Override
    CharSequence getEmptyText() {
        return "";
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        app = (Application) getActivity().getApplication();
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
        listView.setOnItemClickListener(new OnItemClickListener() {
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
        final Application app = (Application) getActivity().getApplication();
        getListView().setAdapter(app.lastResult);
    }

    private void setBusy(boolean busy) {
        setListShown(!busy);
        if (!busy) {
            TextView emptyText = ((TextView)emptyView.findViewById(R.id.empty_text));
            String msg = "";
            String query = app.getLookupQuery();
            if (query != null && !query.toString().equals("")) {
                msg = getString(R.string.lookup_nothing_found);
            }
            emptyText.setText(msg);
        }
    }

    @Override
    public void onDestroy() {
        Application app = (Application) getActivity().getApplication();
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

}
