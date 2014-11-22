package itkach.aard2;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;

public class DictionariesFragment extends BaseListFragment {


    private DictionaryListAdapter listAdapter;
    private AlertDialog           deleteConfirmationDialog = null;

    protected Icons getEmptyIcon() {
        return Icons.DICTIONARY;
    }

    protected CharSequence getEmptyText() {
        return Html.fromHtml(getString(R.string.main_empty_dictionaries));
    }

    @Override
    protected boolean supportsSelection() {
        return false;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Application app = (Application)getActivity().getApplication();
        listAdapter = new DictionaryListAdapter(app.dictionaries, getActivity());
        setListAdapter(listAdapter);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.dictionaries, menu);
    }

    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        MenuItem miFindDictionaries = menu.findItem(R.id.action_find_dictionaries);
        miFindDictionaries.setIcon(Icons.REFRESH.forActionBar());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final Application app = ((Application)getActivity().getApplication());
        if (item.getItemId() == R.id.action_find_dictionaries) {
            final ProgressDialog p = new ProgressDialog(getActivity());
            p.setIndeterminate(true);
            p.setTitle(getString(R.string.dictionaries_please_wait));
            p.setMessage(getString(R.string.dictionaries_scanning_device));
            app.findDictionaries(new DictionaryDiscoveryCallback() {
                @Override
                public void onDiscoveryFinished() {
                    p.dismiss();
                }
            });
            p.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (deleteConfirmationDialog != null) {
            deleteConfirmationDialog.dismiss();
        }
    }
}
