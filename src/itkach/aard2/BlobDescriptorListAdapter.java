package itkach.aard2;

import android.content.Context;
import android.database.DataSetObserver;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.TextView;

import java.text.DateFormat;

import itkach.slob.Slob;

public class BlobDescriptorListAdapter extends BaseAdapter {

    BlobDescriptorList      list;
    DateFormat              dateFormat;
    private DataSetObserver observer;
    private boolean         selectionMode;

    public BlobDescriptorListAdapter(BlobDescriptorList list) {
        this.list = list;
        this.dateFormat = DateFormat.getDateTimeInstance();
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
        this.list.registerDataSetObserver(observer);
    }

    @Override
    public int getCount() {
        synchronized (list) {
            return list == null ? 0 : list.size();
        }
    }

    @Override
    public Object getItem(int position) {
        synchronized (list) {
            return list.get(position);
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public void setSelectionMode(boolean selectionMode) {
        this.selectionMode = selectionMode;
        notifyDataSetChanged();
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        BlobDescriptor item = list.get(position);
        Slob slob = list.resolveOwner(item);
        CharSequence timestamp = DateUtils.getRelativeDateTimeString(
                parent.getContext(), item.createdAt,
                DateUtils.MINUTE_IN_MILLIS, DateUtils.WEEK_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_ALL);
        View view;
        if (convertView != null) {
            view = convertView;
        } else {
            LayoutInflater inflater = (LayoutInflater) parent.getContext()
                    .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.blob_descriptor_list_item, parent,
                    false);
        }
        TextView titleView = (TextView) view
                .findViewById(R.id.blob_descriptor_key);
        titleView.setText(item.key);
        TextView sourceView = (TextView) view
                .findViewById(R.id.blob_descriptor_source);
        sourceView.setText(slob == null ? "???" : slob.getTags().get("label"));
        TextView timestampView = (TextView) view
                .findViewById(R.id.blob_descriptor_timestamp);
        timestampView.setText(timestamp);
        CheckBox cb = (CheckBox) view
                .findViewById(R.id.blob_descriptor_checkbox);
        cb.setVisibility(isSelectionMode() ? View.VISIBLE : View.GONE);
        return view;
    }

}
