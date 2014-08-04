package itkach.aard2;

import static java.lang.String.format;

import java.util.List;
import java.util.Locale;

import android.content.Context;
import android.content.res.Resources;
import android.database.DataSetObserver;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

public class DictionaryListAdapter extends BaseAdapter {

    private final SlobDescriptorList data;
    private final DataSetObserver observer;
    private boolean              selectionMode;

    DictionaryListAdapter(SlobDescriptorList data) {
        this.data = data;
        this.observer = new DataSetObserver() {
            @Override
            public void onChanged() {
                notifyDataSetChanged();
            }

            @Override
            public void onInvalidated() {
                notifyDataSetInvalidated();
            }
        };
        this.data.registerDataSetObserver(observer);

    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        SlobDescriptor desc = (SlobDescriptor) getItem(position);
        String label = desc.tags.get("label");
        String path = desc.path;
        int blobCount = desc.blobCount;
        boolean available = this.data.resolve(desc) != null;
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.dictionary_list_item, parent,
                    false);
        }
        TextView titleView = (TextView) view
                .findViewById(R.id.dictionary_label);
        titleView.setEnabled(available);
        titleView.setText(label);
        TextView pathView = (TextView) view.findViewById(R.id.dictionary_path);
        pathView.setText(path);
        pathView.setEnabled(available);
        TextView blobCountView = (TextView) view
                .findViewById(R.id.dictionary_blob_count);
        blobCountView.setEnabled(available);
        blobCountView.setVisibility(desc.error == null ? View.VISIBLE : View.GONE);
        Resources r = parent.getResources();
        blobCountView.setText(format(Locale.getDefault(),
                r.getString(R.string.dict_item_count), blobCount));
        CheckBox cb = (CheckBox) view.findViewById(R.id.dictionary_checkbox);
        cb.setVisibility(isSelectionMode() ? View.VISIBLE : View.GONE);
        TextView errorView = (TextView) view
                .findViewById(R.id.dictionary_error);
        errorView.setVisibility(desc.error == null ? View.GONE : View.VISIBLE);
        errorView.setText(desc.error);
        return view;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public Object getItem(int position) {
        return data.get(position);
    }

    @Override
    public int getCount() {
        return data.size();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
    }
}
