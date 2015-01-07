package itkach.aard2;

import android.os.Bundle;
import android.support.v4.app.ListFragment;
import android.text.method.LinkMovementMethod;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.shamanland.fonticon.FontIconDrawable;

public abstract class BaseListFragment extends ListFragment {

    protected View emptyView;
    ActionMode actionMode;

    abstract int getEmptyIcon();

    abstract CharSequence getEmptyText();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        emptyView = inflater.inflate(R.layout.empty_view, container, false);
        TextView emptyText = ((TextView)emptyView.findViewById(R.id.empty_text));
        emptyText.setMovementMethod(LinkMovementMethod.getInstance());
        emptyText.setText(getEmptyText());
        ImageView emptyIcon = (ImageView)(emptyView.findViewById(R.id.empty_icon));
        emptyIcon.setImageDrawable(FontIconDrawable.inflate(getActivity(), getEmptyIcon()));
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    protected void setSelectionMode(boolean selectionMode){};

    protected int getSelectionMenuId(){return 0;};

    protected boolean onSelectionActionItemClicked(final ActionMode mode, MenuItem item){
        return false;
    };

    protected boolean supportsSelection() {
        return true;
    }

    void finishActionMode() {
        if (actionMode != null) {
            actionMode.finish();
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        final ListView listView = getListView();
        listView.setEmptyView(emptyView);
        ((ViewGroup) listView.getParent()).addView(emptyView, 0);

        if (supportsSelection()) {
            listView.setItemsCanFocus(false);
            listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
            listView.setMultiChoiceModeListener(new AbsListView.MultiChoiceModeListener() {

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    actionMode = mode;
                    MenuInflater inflater = mode.getMenuInflater();
                    inflater.inflate(getSelectionMenuId(), menu);
                    MenuItem miDelete = menu.findItem(R.id.blob_descriptor_delete);
                    if (miDelete != null) {
                        miDelete.setIcon(FontIconDrawable.inflate(getActivity(), R.xml.ic_actionbar_trash));
                    }
                    MenuItem miSelectAll = menu.findItem(R.id.blob_descriptor_select_all);
                    if (miSelectAll != null) {
                        miSelectAll.setIcon(FontIconDrawable.inflate(getActivity(), R.xml.ic_actionbar_select_all));
                    }
                    setSelectionMode(true);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    return false;
                }

                @Override
                public boolean onActionItemClicked(final ActionMode mode, MenuItem item) {
                    return onSelectionActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    setSelectionMode(false);
                    actionMode = null;
                }

                @Override
                public void onItemCheckedStateChanged(ActionMode mode,
                                                      int position, long id, boolean checked) {
                }
            });

        }
    }

}
