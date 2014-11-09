package itkach.aard2;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
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
    protected void setSelectionMode(boolean selectionMode) {
        listAdapter.setSelectionMode(selectionMode);
    }

    @Override
    protected int getSelectionMenuId() {
        return R.menu.dictionary_selection;
    }

    @Override
    protected boolean onSelectionActionItemClicked(final ActionMode mode, MenuItem item) {
        ListView listView = getListView();
        switch (item.getItemId()) {
            case R.id.dictionary_forget:
                int checkedCount = getListView().getCheckedItemCount();
                String countStr = getResources().getQuantityString(R.plurals.selected_dict_count,
                        checkedCount, checkedCount);
                String message = getString(R.string.dictionaries_confirm_forget, countStr);
                deleteConfirmationDialog = new AlertDialog.Builder(getActivity())
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setTitle("")
                        .setMessage(message)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                forgetSelectedItems();
                                mode.finish();
                            }
                        })
                        .setNegativeButton(android.R.string.no, null)
                        .create();
                deleteConfirmationDialog.setOnDismissListener(new DialogInterface.OnDismissListener(){
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        deleteConfirmationDialog = null;
                    }
                });
                deleteConfirmationDialog.show();
                return true;
            case R.id.dictionary_select_all:
                int itemCount = listView.getCount();
                for (int i = itemCount - 1; i > -1; --i) {
                    listView.setItemChecked(i, true);
                }
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Application app = (Application)getActivity().getApplication();
        listAdapter = new DictionaryListAdapter(app.dictionaries);
        setListAdapter(listAdapter);
    }


    private void forgetSelectedItems() {
        SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
        int checkedCount = getListView().getCheckedItemCount();
        Application app = (Application)getActivity().getApplication();
        app.dictionaries.beginUpdate();
        for (int i = checkedItems.size() - 1; i > -1; --i) {
            int position = checkedItems.keyAt(i);
            boolean checked = checkedItems.get(position);
            if (checked) {
                app.dictionaries.remove(position);
            }
        }
        app.dictionaries.endUpdate(checkedCount > 0);
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
    public void onListItemClick(ListView l, View v, int position, long id) {
        SlobDescriptor desc = (SlobDescriptor)l.getAdapter().getItem(position);
        String source = desc.tags.get("source");
        if (source != null) {
            Uri uri = Uri.parse(source);
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(browserIntent);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (deleteConfirmationDialog != null) {
            deleteConfirmationDialog.dismiss();
        }
    }
}
