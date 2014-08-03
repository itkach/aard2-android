package itkach.aard2;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import static android.text.method.MovementMethod.*;

public class DictionariesFragment extends BaseListFragment {


    protected Icons getEmptyIcon() {
        return Icons.DICTIONARY;
    }

    protected CharSequence getEmptyText() {
        return Html.fromHtml("Get dictionaries at <a href='http://aarddict.org'>http://aarddict.org</a>");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final Application app = (Application)getActivity().getApplication();
        final DictionaryListAdapter listAdapter = new DictionaryListAdapter(app.dictionaries);
        final ListView listView = getListView();

        listView.setItemsCanFocus(false);
        listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
        listView.setMultiChoiceModeListener(new MultiChoiceModeListener() {

            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                MenuInflater inflater = mode.getMenuInflater();
                inflater.inflate(R.menu.dictionary_selection, menu);
                listAdapter.setSelectionMode(true);
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                // TODO Auto-generated method stub
                return false;
            }

            @Override
            public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
                ListView listView = getListView();
                switch (item.getItemId()) {
                    case R.id.dictionary_forget:
                        SparseBooleanArray checkedItems = listView.getCheckedItemPositions();
                        new AlertDialog.Builder(getActivity())
                                .setIcon(android.R.drawable.ic_dialog_alert)
                                .setTitle("")
                                .setMessage(String.format("Are you sure you want to forget %d dictionaries?", checkedItems.size()))
                                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        forgetSelectedItems();
                                        mode.finish();
                                    }
                                })
                                .setNegativeButton("No", null)
                                .show();
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
            public void onDestroyActionMode(ActionMode mode) {
                listAdapter.setSelectionMode(false);
            }

            @Override
            public void onItemCheckedStateChanged(ActionMode mode,
                                                  int position, long id, boolean checked) {
            }

        });

        setListAdapter(listAdapter);
    }


    private void forgetSelectedItems() {
        SparseBooleanArray checkedItems = getListView().getCheckedItemPositions();
        final Application app = (Application)getActivity().getApplication();
        app.dictionaries.beginUpdate();
        for (int i = checkedItems.size() - 1; i > -1; --i) {
            int position = checkedItems.keyAt(i);
            app.dictionaries.remove(position);
        }
        app.dictionaries.endUpdate(checkedItems.size() > 0);
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
            p.setTitle("Please wait");
            p.setMessage("Scanning device for dictionaries...");
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


}
